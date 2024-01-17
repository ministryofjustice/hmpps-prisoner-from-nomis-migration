package uk.gov.justice.digital.hmpps.prisonerfromnomismigration.adjudications

import com.github.tomakehurst.wiremock.client.WireMock.equalTo
import com.github.tomakehurst.wiremock.client.WireMock.matchingJsonPath
import com.github.tomakehurst.wiremock.matching.RequestPatternBuilder
import com.github.tomakehurst.wiremock.matching.StringValuePattern
import kotlinx.coroutines.runBlocking
import org.assertj.core.api.Assertions.assertThat
import org.awaitility.kotlin.atMost
import org.awaitility.kotlin.await
import org.awaitility.kotlin.matches
import org.awaitility.kotlin.untilAsserted
import org.awaitility.kotlin.untilCallTo
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.check
import org.mockito.kotlin.eq
import org.mockito.kotlin.isNull
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.http.ReactiveHttpOutputMessage
import org.springframework.test.web.reactive.server.returnResult
import org.springframework.web.reactive.function.BodyInserter
import org.springframework.web.reactive.function.BodyInserters
import software.amazon.awssdk.services.sqs.model.SendMessageRequest
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.appointments.AppointmentsMigrationFilter
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.data.MigrationContext
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.integration.SqsIntegrationTestBase
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.persistence.repository.MigrationHistory
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.persistence.repository.MigrationHistoryRepository
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.service.MigrationStatus
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.service.MigrationType
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.wiremock.AdjudicationsApiExtension.Companion.adjudicationsApi
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.wiremock.MappingApiExtension.Companion.ADJUDICATIONS_GET_MAPPING_URL
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.wiremock.MappingApiExtension.Companion.mappingApi
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.wiremock.NomisApiExtension.Companion.ADJUDICATIONS_ID_URL
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.wiremock.NomisApiExtension.Companion.nomisApi
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.wiremock.adjudicationsIdsPagedResponse
import java.time.Duration
import java.time.LocalDateTime

class AdjudicationsMigrationIntTest : SqsIntegrationTestBase() {

  @Autowired
  private lateinit var migrationHistoryRepository: MigrationHistoryRepository

  @Nested
  @DisplayName("POST /migrate/adjudications")
  inner class MigrationAdjudications {
    @BeforeEach
    internal fun setUp() {
      webTestClient.delete().uri("/history")
        .headers(setAuthorisation(roles = listOf("ROLE_MIGRATION_ADMIN")))
        .header("Content-Type", "application/json")
        .exchange()
        .expectStatus().is2xxSuccessful
    }

    @Test
    internal fun `must have valid token to start migration`() {
      webTestClient.post().uri("/migrate/adjudications")
        .header("Content-Type", "application/json")
        .body(someMigrationFilter())
        .exchange()
        .expectStatus().isUnauthorized
    }

    @Test
    internal fun `must have correct role to start migration`() {
      webTestClient.post().uri("/migrate/adjudications")
        .headers(setAuthorisation(roles = listOf("ROLE_MIGRATE_BANANAS")))
        .header("Content-Type", "application/json")
        .body(someMigrationFilter())
        .exchange()
        .expectStatus().isForbidden
    }

    @Test
    internal fun `will start processing pages of adjudications`() {
      nomisApi.stubGetInitialCount(ADJUDICATIONS_ID_URL, 21) { adjudicationsIdsPagedResponse(it) }
      nomisApi.stubMultipleGetAdjudicationIdCounts(totalElements = 21, pageSize = 10)
      nomisApi.stubMultipleGetAdjudications(1..21)
      mappingApi.stubAllMappingsNotFound(ADJUDICATIONS_GET_MAPPING_URL)
      mappingApi.stubMappingCreate("/mapping/adjudications/all")

      adjudicationsApi.stubCreateAdjudicationForMigration()
      mappingApi.stubAdjudicationMappingByMigrationId(count = 21)

      webTestClient.post().uri("/migrate/adjudications")
        .headers(setAuthorisation(roles = listOf("ROLE_MIGRATE_ADJUDICATIONS")))
        .header("Content-Type", "application/json")
        .body(
          BodyInserters.fromValue(
            """
            {
              "fromDate": "2020-01-01",
              "toDate": "2020-01-02"
            }
            """.trimIndent(),
          ),
        )
        .exchange()
        .expectStatus().isAccepted

      await atMost Duration.ofSeconds(60) untilAsserted {
        verify(telemetryClient).trackEvent(
          eq("adjudications-migration-completed"),
          any(),
          isNull(),
        )
      }

      // check filter matches what is passed in
      nomisApi.verifyGetIdsCount(
        url = "/adjudications/charges/ids",
        fromDate = "2020-01-01",
        toDate = "2020-01-02",
      )

      await untilAsserted {
        assertThat(adjudicationsApi.createAdjudicationCount()).isEqualTo(21)
      }
      assertThat(mappingApi.createMappingCount("/mapping/adjudications/all")).isEqualTo(21)
    }

    @Test
    internal fun `a migrated adjudication will create a mapping and a transformed adjudication in DPS`() {
      val adjudicationNumber = 12345L
      val chargeSequence = 1
      val bookingId = 6543L
      val offenderNo = "A1234BC"
      val chargeNumber = "12345-1"
      val nomisHearingId = 2012708L
      val sanctionSequence1 = 10
      val sanctionSequence2 = 11
      val adjudicationPageResponse = adjudicationsIdsPagedResponse(
        adjudicationNumber = adjudicationNumber,
        chargeSequence = chargeSequence,
        offenderNo = offenderNo,
      )
      nomisApi.stubGetInitialCount(ADJUDICATIONS_ID_URL, 1) { adjudicationPageResponse }
      nomisApi.stubMultipleGetAdjudicationIdCounts(totalElements = 1, pageSize = 10) { adjudicationPageResponse }
      nomisApi.stubGetAdjudication(adjudicationNumber = adjudicationNumber, chargeSequence = chargeSequence) {
        // language=json
        """
{
    "adjudicationSequence": 1,
    "offenderNo": "$offenderNo",
    "bookingId": $bookingId,
    "adjudicationNumber": $adjudicationNumber,
    "hasMultipleCharges": false,
    "gender": {
        "code": "M",
        "description": "Male"
    },
    "currentPrison": {
        "code": "BXI",
        "description": "BRIXTON (HMP)"
    },
    "partyAddedDate": "2023-08-07",
    "incident": {
        "adjudicationIncidentId": 1503234,
        "reportingStaff": {
            "username": "AMARKE_GEN",
            "staffId": 485887,
            "firstName": "ANDY",
            "lastName": "MARKE",
            "createdByUsername": "AMARKE_GEN"
        },
        "incidentDate": "2023-08-07",
        "incidentTime": "11:23:00",
        "reportedDate": "2023-08-07",
        "reportedTime": "11:20:00",
        "createdByUsername": "AMARKE_GEN",
        "createdDateTime": "2023-08-07T11:24:10.501959",
        "internalLocation": {
            "locationId": 172315,
            "code": "W/SHOP13",
            "description": "WWI-EDUC-W/SHOP13"
        },
        "incidentType": {
            "code": "GOV",
            "description": "Governor's Report"
        },
        "details": "some incident",
        "prison": {
            "code": "WWI",
            "description": "WANDSWORTH (HMP)"
        },
        "prisonerWitnesses": [],
        "prisonerVictims": [],
        "otherPrisonersInvolved": [],
        "reportingOfficers": [],
        "staffWitnesses": [],
        "staffVictims": [],
        "otherStaffInvolved": [],
        "repairs": [
            {
                "type": {
                    "code": "ELEC",
                    "description": "Electrical"
                },
                "createdByUsername": "AMARKE_GEN"
            },
            {
                "type": {
                    "code": "PLUM",
                    "description": "Plumbing"
                },
                "comment": "plum",
                "createdByUsername": "AMARKE_GEN"
            }
        ]
    },
    "charge": {
        "offence": {
            "code": "51:2D",
            "description": "Detains any person against his will - detention against will of staff (not prison offr)",
            "type": {
                "code": "51",
                "description": "Prison Rule 51"
            }
        },
        "offenceId": "1525933/2",
        "chargeSequence": $chargeSequence
    },
    "investigations": [
        {
            "investigator": {
                "username": "KQG94Y",
                "staffId": 67362,
                "firstName": "EKSINRN",
                "lastName": "AALYLE",
                "createdByUsername": "AMARKE_GEN"
            },
            "comment": "comment one",
            "dateAssigned": "2023-08-07",
            "evidence": [
                {
                    "type": {
                        "code": "EVI_BAG",
                        "description": "Evidence Bag"
                    },
                    "date": "2023-08-07",
                    "detail": "evidence bag",
                    "createdByUsername": "AMARKE_GEN"
                },
                {
                    "type": {
                        "code": "OTHER",
                        "description": "Other"
                    },
                    "date": "2023-08-07",
                    "detail": "other stuff",
                    "createdByUsername": "AMARKE_GEN"
                }
            ]
        },
        {
            "investigator": {
                "username": "HQZ33B",
                "staffId": 67839,
                "firstName": "DIKBLISNG",
                "lastName": "ABBOY",
                "createdByUsername": "AMARKE_GEN"
            },
            "comment": "another comment",
            "dateAssigned": "2023-08-07",
            "evidence": [
                {
                    "type": {
                        "code": "BEHAV",
                        "description": "Behaviour Report"
                    },
                    "date": "2023-08-07",
                    "detail": "report",
                    "createdByUsername": "AMARKE_GEN"
                }
            ]
        }
    ],
    "hearings": [
        {
            "hearingId": $nomisHearingId,
            "type": {
                "code": "GOV_ADULT",
                "description": "Governor's Hearing Adult"
            },
            "hearingDate": "2023-08-07",
            "hearingTime": "16:55:00",
            "internalLocation": {
                "locationId": 176776,
                "code": "ADJR",
                "description": "WWI-RES-CSU-ADJR"
            },
            "eventStatus": {
                "code": "EXP",
                "description": "Expired"
            },
            "hearingResults": [
                {
                    "pleaFindingType": {
                        "code": "GUILTY",
                        "description": "Guilty"
                    },
                    "findingType": {
                        "code": "PROVED",
                        "description": "Charge Proved"
                    },
                    "charge": {
                        "offence": {
                            "code": "51:2D",
                            "description": "Detains any person against his will - detention against will of staff (not prison offr)",
                            "type": {
                                "code": "51",
                                "description": "Prison Rule 51"
                            }
                        },
                        "offenceId": "1525933/2",
                        "chargeSequence": 2
                    },
                    "offence": {
                        "code": "51:2D",
                        "description": "Detains any person against his will - detention against will of staff (not prison offr)",
                        "type": {
                            "code": "51",
                            "description": "Prison Rule 51"
                        }
                    },
                    "resultAwards": [
                        {
                            "sequence": $sanctionSequence1,
                            "sanctionType": {
                                "code": "FORFEIT",
                                "description": "Forfeiture of Privileges"
                            },
                            "sanctionStatus": {
                                "code": "IMMEDIATE",
                                "description": "Immediate"
                            },
                            "effectiveDate": "2023-08-15",
                            "statusDate": "2023-08-15",
                            "sanctionDays": 3,
                            "createdByUsername": "Q124UT",
                            "createdDateTime": "2023-08-07T16:56:17.018049",
                            "consecutiveAward": {
                                "sequence": 2,
                                "sanctionType": {
                                    "code": "ADA",
                                    "description": "Additional Days Added"
                                },
                                "createdByUsername": "Q124UT",
                                "createdDateTime": "2023-08-07T16:56:17.018049",
                                "sanctionStatus": {
                                    "code": "IMMEDIATE",
                                    "description": "Immediate"
                                },
                                "effectiveDate": "2023-08-07",
                                "statusDate": "2023-08-07",
                                "sanctionDays": 2,
                                "sanctionMonths": 1,
                                "chargeSequence": 1
                            },
                            "chargeSequence": 2
                        },
                        {
                            "sequence": $sanctionSequence2,
                            "sanctionType": {
                                "code": "STOP_PCT",
                                "description": "Stoppage of Earnings (%)"
                            },
                            "sanctionStatus": {
                                "code": "IMMEDIATE",
                                "description": "Immediate"
                            },
                            "effectiveDate": "2023-08-15",
                            "statusDate": "2023-08-15",
                            "sanctionMonths": 2,
                            "createdByUsername": "Q124UT",
                            "createdDateTime": "2023-08-07T16:56:17.018049",
                            "compensationAmount": 120.12,
                            "chargeSequence": 2
                        }
                    ],
                    "createdDateTime": "2023-08-15T08:58:08.015285",
                    "createdByUsername": "AMARKE_GEN"
                }
            ],
            "createdDateTime": "2023-08-07T16:56:17.018049",
            "createdByUsername": "AMARKE_GEN",
            "notifications": [
                  {
                    "deliveryDate": "2023-09-07",
                    "deliveryTime": "11:24:00",
                    "comment": "Added",
                    "notifiedStaff": {
                        "username": "AMARKE_GEN",
                        "staffId": 87654,
                        "firstName": "BREAD",
                        "lastName": "MEAT"
                    }
                  }
            ]
        }
    ]
}    
  """
      }
      mappingApi.stubAllMappingsNotFound(ADJUDICATIONS_GET_MAPPING_URL)
      adjudicationsApi.stubCreateAdjudicationForMigration(
        adjudicationNumber = adjudicationNumber,
        chargeSequence = chargeSequence,
        chargeNumber = chargeNumber,
        bookingId = bookingId,
        hearingIds = listOf(nomisHearingId to 9876),
        punishmentIds = listOf(sanctionSequence1 to 1234, sanctionSequence2 to 1235),
      )
      mappingApi.stubMappingCreate("/mapping/adjudications/all")
      mappingApi.stubAdjudicationMappingByMigrationId(count = 1)

      webTestClient.post().uri("/migrate/adjudications")
        .headers(setAuthorisation(roles = listOf("ROLE_MIGRATE_ADJUDICATIONS")))
        .header("Content-Type", "application/json")
        .body(
          BodyInserters.fromValue(
            """
            {
              "fromDate": "2020-01-01",
              "toDate": "2020-01-02"
            }
            """.trimIndent(),
          ),
        )
        .exchange()
        .expectStatus().isAccepted

      await atMost Duration.ofSeconds(60) untilAsserted {
        verify(telemetryClient).trackEvent(
          eq("adjudications-migration-completed"),
          any(),
          isNull(),
        )
      }

      adjudicationsApi.verifyCreatedAdjudicationForMigration {
        bodyWithJson("$.oicIncidentId", equalTo("$adjudicationNumber"))
        bodyWithJson("$.offenceSequence", equalTo("$chargeSequence"))
        bodyWithJson("$.bookingId", equalTo("$bookingId"))
        bodyWithJson("$.hearings.length()", equalTo("1"))
        bodyWithJson("$.punishments.length()", equalTo("2"))
        bodyWithJson("$.evidence.length()", equalTo("3"))
        bodyWithJson("$.damages.length()", equalTo("2"))
      }

      mappingApi.verifyCreateMappingAdjudication {
        bodyWithJson("adjudicationId.adjudicationNumber", equalTo("$adjudicationNumber"))
        bodyWithJson("adjudicationId.chargeSequence", equalTo("$chargeSequence"))
        bodyWithJson("adjudicationId.chargeNumber", equalTo(chargeNumber))
        bodyWithJson("hearings[0].nomisHearingId", equalTo("$nomisHearingId"))
        bodyWithJson("hearings[0].dpsHearingId", equalTo("9876"))
        bodyWithJson("punishments[0].nomisBookingId", equalTo("$bookingId"))
        bodyWithJson("punishments[0].nomisSanctionSequence", equalTo("$sanctionSequence1"))
        bodyWithJson("punishments[0].dpsPunishmentId", equalTo("1234"))
        bodyWithJson("punishments[1].nomisBookingId", equalTo("$bookingId"))
        bodyWithJson("punishments[1].nomisSanctionSequence", equalTo("$sanctionSequence2"))
        bodyWithJson("punishments[1].dpsPunishmentId", equalTo("1235"))
      }

      verify(telemetryClient).trackEvent(
        eq("adjudications-migration-entity-migrated"),
        check {
          assertThat(it["migrationId"]).isNotNull()
          assertThat(it["offenderNo"]).isEqualTo(offenderNo)
          assertThat(it["adjudicationNumber"]).isEqualTo(adjudicationNumber.toString())
          assertThat(it["chargeSequence"]).isEqualTo(chargeSequence.toString())
          assertThat(it["chargeNumber"]).isEqualTo(chargeNumber)
        },
        isNull(),
      )
    }

    @Test
    fun `will add analytical events for starting, ending and each migrated record`() {
      nomisApi.stubGetInitialCount("/adjudications/charges/ids", 3) { adjudicationsIdsPagedResponse(it) }
      nomisApi.stubMultipleGetAdjudicationIdCounts(totalElements = 3, pageSize = 10)
      nomisApi.stubMultipleGetAdjudications(1..3)
      adjudicationsApi.stubCreateAdjudicationForMigration(12345)
      mappingApi.stubAllMappingsNotFound(ADJUDICATIONS_GET_MAPPING_URL)
      mappingApi.stubMappingCreate("/mapping/adjudications/all")

      // stub 10 migrated records and 1 fake a failure
      mappingApi.stubAdjudicationMappingByMigrationId(count = 2)
      awsSqsAdjudicationsMigrationDlqClient!!.sendMessage(
        SendMessageRequest.builder().queueUrl(adjudicationsMigrationDlqUrl)
          .messageBody("""{ "message": "some error" }""").build(),
      ).get()

      webTestClient.post().uri("/migrate/adjudications")
        .headers(setAuthorisation(roles = listOf("ROLE_MIGRATE_ADJUDICATIONS")))
        .header("Content-Type", "application/json")
        .body(
          BodyInserters.fromValue(
            """
            {
            }
            """.trimIndent(),
          ),
        )
        .exchange()
        .expectStatus().isAccepted

      await atMost Duration.ofSeconds(60) untilAsserted {
        verify(telemetryClient).trackEvent(
          eq("adjudications-migration-completed"),
          any(),
          isNull(),
        )
      }

      verify(telemetryClient).trackEvent(eq("adjudications-migration-started"), any(), isNull())
      verify(telemetryClient, times(3)).trackEvent(eq("adjudications-migration-entity-migrated"), any(), isNull())

      await atMost Duration.ofSeconds(20) untilAsserted {
        webTestClient.get().uri("/migrate/adjudications/history")
          .headers(setAuthorisation(roles = listOf("ROLE_MIGRATE_ADJUDICATIONS")))
          .header("Content-Type", "application/json")
          .exchange()
          .expectStatus().isOk
          .expectBody()
          .jsonPath("$.size()").isEqualTo(1)
          .jsonPath("$[0].migrationId").isNotEmpty
          .jsonPath("$[0].whenStarted").isNotEmpty
          .jsonPath("$[0].estimatedRecordCount").isEqualTo(3)
          .jsonPath("$[0].migrationType").isEqualTo("ADJUDICATIONS")
          .jsonPath("$[0].recordsMigrated").isEqualTo(2)
          .jsonPath("$[0].recordsFailed").isEqualTo(1)
          .jsonPath("$[0].whenEnded").isNotEmpty
          .jsonPath("$[0].status").isEqualTo("COMPLETED")
      }
    }

    @Test
    fun `will retry to create a mapping, and only the mapping, if it fails first time`() {
      nomisApi.stubGetInitialCount("/adjudications/charges/ids", 3) {
        adjudicationsIdsPagedResponse(
          totalElements = it,
          ids = listOf(654321),
        )
      }
      nomisApi.stubGetSingleAdjudicationId(adjudicationNumber = 654321)
      nomisApi.stubGetAdjudication(adjudicationNumber = 654321, chargeSequence = 1)
      mappingApi.stubAllMappingsNotFound(ADJUDICATIONS_GET_MAPPING_URL)
      mappingApi.stubAdjudicationMappingByMigrationId()
      adjudicationsApi.stubCreateAdjudicationForMigration(654321L)
      mappingApi.stubMappingCreateFailureFollowedBySuccess(url = "/mapping/adjudications/all")

      webTestClient.post().uri("/migrate/adjudications")
        .headers(setAuthorisation(roles = listOf("ROLE_MIGRATE_ADJUDICATIONS")))
        .header("Content-Type", "application/json")
        .body(
          BodyInserters.fromValue(
            """
            {
              "fromDate": "2020-01-01",
              "toDate": "2020-01-02"
            }
            """.trimIndent(),
          ),
        )
        .exchange()
        .expectStatus().isAccepted

      // wait for all mappings to be created before verifying
      await untilCallTo { mappingApi.createMappingCount("/mapping/adjudications/all") } matches { it == 2 }

      // check that one adjudication is created
      assertThat(adjudicationsApi.createAdjudicationCount()).isEqualTo(1)

      // should retry to create mapping twice
      mappingApi.verifyCreateMappingAdjudication(
        adjudicationNumber = 654321,
        chargeSequence = 1,
        chargeNumber = "654321-1",
        times = 2,
      )
    }
  }

  @Nested
  @DisplayName("GET /migrate/adjudications/history")
  inner class GetAll {
    @BeforeEach
    fun createHistoryRecords() {
      runBlocking {
        migrationHistoryRepository.deleteAll()
        migrationHistoryRepository.save(
          MigrationHistory(
            migrationId = "2020-01-01T00:00:00",
            whenStarted = LocalDateTime.parse("2020-01-01T00:00:00"),
            whenEnded = LocalDateTime.parse("2020-01-01T01:00:00"),
            status = MigrationStatus.COMPLETED,
            estimatedRecordCount = 123_567,
            filter = "",
            recordsMigrated = 123_560,
            recordsFailed = 7,
            migrationType = MigrationType.ADJUDICATIONS,
          ),
        )
        migrationHistoryRepository.save(
          MigrationHistory(
            migrationId = "2020-01-02T00:00:00",
            whenStarted = LocalDateTime.parse("2020-01-02T00:00:00"),
            whenEnded = LocalDateTime.parse("2020-01-02T01:00:00"),
            status = MigrationStatus.COMPLETED,
            estimatedRecordCount = 123_567,
            filter = "",
            recordsMigrated = 123_567,
            recordsFailed = 0,
            migrationType = MigrationType.ADJUDICATIONS,
          ),
        )
        migrationHistoryRepository.save(
          MigrationHistory(
            migrationId = "2020-01-02T02:00:00",
            whenStarted = LocalDateTime.parse("2020-01-02T02:00:00"),
            whenEnded = LocalDateTime.parse("2020-01-02T03:00:00"),
            status = MigrationStatus.COMPLETED,
            estimatedRecordCount = 123_567,
            filter = "",
            recordsMigrated = 123_567,
            recordsFailed = 0,
            migrationType = MigrationType.ADJUDICATIONS,
          ),
        )
        migrationHistoryRepository.save(
          MigrationHistory(
            migrationId = "2020-01-03T02:00:00",
            whenStarted = LocalDateTime.parse("2020-01-03T02:00:00"),
            whenEnded = LocalDateTime.parse("2020-01-03T03:00:00"),
            status = MigrationStatus.COMPLETED,
            estimatedRecordCount = 123_567,
            filter = "",
            recordsMigrated = 123_560,
            recordsFailed = 7,
            migrationType = MigrationType.ADJUDICATIONS,
          ),
        )
      }
    }

    @AfterEach
    fun deleteHistoryRecords() {
      runBlocking {
        migrationHistoryRepository.deleteAll()
      }
    }

    @Test
    fun `must have valid token to get history`() {
      webTestClient.get().uri("/migrate/adjudications/history")
        .header("Content-Type", "application/json")
        .exchange()
        .expectStatus().isUnauthorized
    }

    @Test
    fun `must have correct role to get history`() {
      webTestClient.get().uri("/migrate/adjudications/history")
        .headers(setAuthorisation(roles = listOf("ROLE_MIGRATE_BANANAS")))
        .header("Content-Type", "application/json")
        .exchange()
        .expectStatus().isForbidden
    }

    @Test
    fun `can read all records with no filter`() {
      webTestClient.get().uri("/migrate/adjudications/history")
        .headers(setAuthorisation(roles = listOf("ROLE_MIGRATE_ADJUDICATIONS")))
        .header("Content-Type", "application/json")
        .exchange()
        .expectStatus().isOk
        .expectBody()
        .jsonPath("$.size()").isEqualTo(4)
        .jsonPath("$[0].migrationId").isEqualTo("2020-01-03T02:00:00")
        .jsonPath("$[1].migrationId").isEqualTo("2020-01-02T02:00:00")
        .jsonPath("$[2].migrationId").isEqualTo("2020-01-02T00:00:00")
        .jsonPath("$[3].migrationId").isEqualTo("2020-01-01T00:00:00")
    }

    @Test
    fun `can filter so only records after a date are returned`() {
      webTestClient.get().uri {
        it.path("/migrate/adjudications/history")
          .queryParam("fromDateTime", "2020-01-02T02:00:00")
          .build()
      }
        .headers(setAuthorisation(roles = listOf("ROLE_MIGRATE_ADJUDICATIONS")))
        .header("Content-Type", "application/json")
        .exchange()
        .expectStatus().isOk
        .expectBody()
        .jsonPath("$.size()").isEqualTo(2)
        .jsonPath("$[0].migrationId").isEqualTo("2020-01-03T02:00:00")
        .jsonPath("$[1].migrationId").isEqualTo("2020-01-02T02:00:00")
    }

    @Test
    fun `can filter so only records before a date are returned`() {
      webTestClient.get().uri {
        it.path("/migrate/adjudications/history")
          .queryParam("toDateTime", "2020-01-02T00:00:00")
          .build()
      }
        .headers(setAuthorisation(roles = listOf("ROLE_MIGRATE_ADJUDICATIONS")))
        .header("Content-Type", "application/json")
        .exchange()
        .expectStatus().isOk
        .expectBody()
        .jsonPath("$.size()").isEqualTo(2)
        .jsonPath("$[0].migrationId").isEqualTo("2020-01-02T00:00:00")
        .jsonPath("$[1].migrationId").isEqualTo("2020-01-01T00:00:00")
    }

    @Test
    fun `can filter so only records between dates are returned`() {
      webTestClient.get().uri {
        it.path("/migrate/adjudications/history")
          .queryParam("fromDateTime", "2020-01-03T01:59:59")
          .queryParam("toDateTime", "2020-01-03T02:00:01")
          .build()
      }
        .headers(setAuthorisation(roles = listOf("ROLE_MIGRATE_ADJUDICATIONS")))
        .header("Content-Type", "application/json")
        .exchange()
        .expectStatus().isOk
        .expectBody()
        .jsonPath("$.size()").isEqualTo(1)
        .jsonPath("$[0].migrationId").isEqualTo("2020-01-03T02:00:00")
    }

    @Test
    fun `can filter so only records with failed records are returned`() {
      webTestClient.get().uri {
        it.path("/migrate/adjudications/history")
          .queryParam("includeOnlyFailures", "true")
          .build()
      }
        .headers(setAuthorisation(roles = listOf("ROLE_MIGRATE_ADJUDICATIONS")))
        .header("Content-Type", "application/json")
        .exchange()
        .expectStatus().isOk
        .expectBody()
        .jsonPath("$.size()").isEqualTo(2)
        .jsonPath("$[0].migrationId").isEqualTo("2020-01-03T02:00:00")
        .jsonPath("$[1].migrationId").isEqualTo("2020-01-01T00:00:00")
    }
  }

  @Nested
  @DisplayName("GET /migrate/adjudications/history/{migrationId}")
  inner class GetMigration {
    @BeforeEach
    fun createHistoryRecords() {
      runBlocking {
        migrationHistoryRepository.deleteAll()
        migrationHistoryRepository.save(
          MigrationHistory(
            migrationId = "2020-01-01T00:00:00",
            whenStarted = LocalDateTime.parse("2020-01-01T00:00:00"),
            whenEnded = LocalDateTime.parse("2020-01-01T01:00:00"),
            status = MigrationStatus.COMPLETED,
            estimatedRecordCount = 123_567,
            filter = "",
            recordsMigrated = 123_560,
            recordsFailed = 7,
            migrationType = MigrationType.ADJUDICATIONS,
          ),
        )
      }
    }

    @AfterEach
    fun deleteHistoryRecords() {
      runBlocking {
        migrationHistoryRepository.deleteAll()
      }
    }

    @Test
    fun `must have valid token to get history`() {
      webTestClient.get().uri("/migrate/adjudications/history/2020-01-01T00:00:00")
        .header("Content-Type", "application/json")
        .exchange()
        .expectStatus().isUnauthorized
    }

    @Test
    fun `must have correct role to get history`() {
      webTestClient.get().uri("/migrate/adjudications/history/2020-01-01T00:00:00")
        .headers(setAuthorisation(roles = listOf("ROLE_MIGRATE_BANANAS")))
        .header("Content-Type", "application/json")
        .exchange()
        .expectStatus().isForbidden
    }

    @Test
    fun `can read record`() {
      webTestClient.get().uri("/migrate/adjudications/history/2020-01-01T00:00:00")
        .headers(setAuthorisation(roles = listOf("ROLE_MIGRATE_ADJUDICATIONS")))
        .header("Content-Type", "application/json")
        .exchange()
        .expectStatus().isOk
        .expectBody()
        .jsonPath("$.migrationId").isEqualTo("2020-01-01T00:00:00")
        .jsonPath("$.status").isEqualTo("COMPLETED")
    }
  }

  @Nested
  @DisplayName("POST /migrate/adjudications/{migrationId}/terminate/")
  inner class TerminateMigrationAppointments {
    @BeforeEach
    fun setUp() {
      webTestClient.delete().uri("/history")
        .headers(setAuthorisation(roles = listOf("ROLE_MIGRATION_ADMIN")))
        .header("Content-Type", "application/json")
        .exchange()
        .expectStatus().is2xxSuccessful
    }

    @Test
    fun `must have valid token to terminate a migration`() {
      webTestClient.post().uri("/migrate/adjudications/{migrationId}/cancel/", "some id")
        .header("Content-Type", "application/json")
        .exchange()
        .expectStatus().isUnauthorized
    }

    @Test
    fun `must have correct role to terminate a migration`() {
      webTestClient.post().uri("/migrate/adjudications/{migrationId}/cancel", "some id")
        .headers(setAuthorisation(roles = listOf("ROLE_MIGRATE_BANANAS")))
        .header("Content-Type", "application/json")
        .exchange()
        .expectStatus().isForbidden
    }

    @Test
    fun `will return a not found if no running migration found`() {
      webTestClient.post().uri("/migrate/adjudications/{migrationId}/cancel", "some id")
        .headers(setAuthorisation(roles = listOf("ROLE_MIGRATE_ADJUDICATIONS")))
        .header("Content-Type", "application/json")
        .exchange()
        .expectStatus().isNotFound
    }

    @Test
    fun `will terminate a running migration`() {
      val count = 30L
      nomisApi.stubGetInitialCount("/adjudications/ids", 2) { adjudicationsIdsPagedResponse(it) }
      nomisApi.stubMultipleGetAdjudicationIdCounts(totalElements = count, pageSize = 10)
      mappingApi.stubAdjudicationMappingByMigrationId(count = count.toInt())

      val migrationId = webTestClient.post().uri("/migrate/adjudications")
        .headers(setAuthorisation(roles = listOf("ROLE_MIGRATE_ADJUDICATIONS")))
        .header("Content-Type", "application/json")
        .body(
          BodyInserters.fromValue(
            """
            {
              "fromDate": "2020-01-01",
              "toDate": "2020-01-02",
              "prisonIds": ["MDI"]
            }
            """.trimIndent(),
          ),
        )
        .exchange()
        .expectStatus().isAccepted
        .returnResult<MigrationContext<AppointmentsMigrationFilter>>()
        .responseBody.blockFirst()!!.migrationId

      webTestClient.post().uri("/migrate/adjudications/{migrationId}/cancel", migrationId)
        .headers(setAuthorisation(roles = listOf("ROLE_MIGRATE_ADJUDICATIONS")))
        .header("Content-Type", "application/json")
        .exchange()
        .expectStatus().isAccepted

      webTestClient.get().uri("/migrate/adjudications/history/{migrationId}", migrationId)
        .headers(setAuthorisation(roles = listOf("ROLE_MIGRATE_ADJUDICATIONS")))
        .header("Content-Type", "application/json")
        .exchange()
        .expectStatus().isOk
        .expectBody()
        .jsonPath("$.migrationId").isEqualTo(migrationId)
        .jsonPath("$.status").isEqualTo("CANCELLED_REQUESTED")

      await atMost Duration.ofSeconds(60) untilAsserted {
        webTestClient.get().uri("/migrate/adjudications/history/{migrationId}", migrationId)
          .headers(setAuthorisation(roles = listOf("ROLE_MIGRATE_ADJUDICATIONS")))
          .header("Content-Type", "application/json")
          .exchange()
          .expectStatus().isOk
          .expectBody()
          .jsonPath("$.migrationId").isEqualTo(migrationId)
          .jsonPath("$.status").isEqualTo("CANCELLED")
      }
    }
  }

  @Nested
  @DisplayName("GET /migrate/adjudications/active-migration")
  inner class GetActiveMigration {
    @BeforeEach
    internal fun createHistoryRecords() {
      runBlocking {
        migrationHistoryRepository.deleteAll()
        migrationHistoryRepository.save(
          MigrationHistory(
            migrationId = "2020-01-01T00:00:00",
            whenStarted = LocalDateTime.parse("2020-01-01T00:00:00"),
            whenEnded = LocalDateTime.parse("2020-01-01T01:00:00"),
            status = MigrationStatus.STARTED,
            estimatedRecordCount = 123_567,
            filter = "",
            recordsMigrated = 123_560,
            recordsFailed = 7,
            migrationType = MigrationType.ADJUDICATIONS,
          ),
        )
        migrationHistoryRepository.save(
          MigrationHistory(
            migrationId = "2019-01-01T00:00:00",
            whenStarted = LocalDateTime.parse("2019-01-01T00:00:00"),
            whenEnded = LocalDateTime.parse("2019-01-01T01:00:00"),
            status = MigrationStatus.COMPLETED,
            estimatedRecordCount = 123_567,
            filter = "",
            recordsMigrated = 123_567,
            recordsFailed = 0,
            migrationType = MigrationType.ADJUDICATIONS,
          ),
        )
      }
    }

    @AfterEach
    internal fun deleteHistoryRecords() {
      runBlocking {
        migrationHistoryRepository.deleteAll()
      }
    }

    @Test
    internal fun `must have valid token to get active migration data`() {
      webTestClient.get().uri("/migrate/adjudications/active-migration")
        .header("Content-Type", "application/json")
        .exchange()
        .expectStatus().isUnauthorized
    }

    @Test
    internal fun `must have correct role to get action migration data`() {
      webTestClient.get().uri("/migrate/adjudications/active-migration")
        .headers(setAuthorisation(roles = listOf("ROLE_MIGRATE_BANANAS")))
        .header("Content-Type", "application/json")
        .exchange()
        .expectStatus().isForbidden
    }

    @Test
    internal fun `will return dto with null contents if no migrations are found`() {
      deleteHistoryRecords()
      webTestClient.get().uri("/migrate/adjudications/active-migration")
        .headers(setAuthorisation(roles = listOf("ROLE_MIGRATE_ADJUDICATIONS")))
        .header("Content-Type", "application/json")
        .exchange()
        .expectStatus().isOk
        .expectBody()
        .jsonPath("$.migrationId").doesNotExist()
        .jsonPath("$.whenStarted").doesNotExist()
        .jsonPath("$.recordsMigrated").doesNotExist()
        .jsonPath("$.estimatedRecordCount").doesNotExist()
        .jsonPath("$.status").doesNotExist()
        .jsonPath("$.migrationType").doesNotExist()
    }

    @Test
    internal fun `can read active migration data`() {
      mappingApi.stubAdjudicationMappingByMigrationId(count = 123456)
      webTestClient.get().uri("/migrate/adjudications/active-migration")
        .headers(setAuthorisation(roles = listOf("ROLE_MIGRATE_ADJUDICATIONS")))
        .header("Content-Type", "application/json")
        .exchange()
        .expectStatus().isOk
        .expectBody()
        .jsonPath("$.migrationId").isEqualTo("2020-01-01T00:00:00")
        .jsonPath("$.whenStarted").isEqualTo("2020-01-01T00:00:00")
        .jsonPath("$.recordsMigrated").isEqualTo(123456)
        .jsonPath("$.toBeProcessedCount").isEqualTo(0)
        .jsonPath("$.beingProcessedCount").isEqualTo(0)
        .jsonPath("$.recordsFailed").isEqualTo(0)
        .jsonPath("$.estimatedRecordCount").isEqualTo(123567)
        .jsonPath("$.status").isEqualTo("STARTED")
        .jsonPath("$.migrationType").isEqualTo("ADJUDICATIONS")
    }
  }
}

private fun RequestPatternBuilder.bodyWithJson(jsonPath: String, value: StringValuePattern?) =
  withRequestBody(
    matchingJsonPath(jsonPath, value),
  )

fun someMigrationFilter(): BodyInserter<String, ReactiveHttpOutputMessage> = BodyInserters.fromValue(
  """
  {
  }
  """.trimIndent(),
)
