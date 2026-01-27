package uk.gov.justice.digital.hmpps.prisonerfromnomismigration.courtsentencing

import com.github.tomakehurst.wiremock.client.WireMock
import com.github.tomakehurst.wiremock.client.WireMock.anyRequestedFor
import com.github.tomakehurst.wiremock.client.WireMock.anyUrl
import com.github.tomakehurst.wiremock.client.WireMock.deleteRequestedFor
import com.github.tomakehurst.wiremock.client.WireMock.equalTo
import com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor
import com.github.tomakehurst.wiremock.client.WireMock.matchingJsonPath
import com.github.tomakehurst.wiremock.client.WireMock.not
import com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor
import com.github.tomakehurst.wiremock.client.WireMock.putRequestedFor
import com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo
import com.github.tomakehurst.wiremock.client.WireMock.urlPathMatching
import org.assertj.core.api.Assertions.assertThat
import org.awaitility.kotlin.await
import org.awaitility.kotlin.matches
import org.awaitility.kotlin.untilAsserted
import org.awaitility.kotlin.untilCallTo
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.mockito.Mockito
import org.mockito.Mockito.eq
import org.mockito.kotlin.check
import org.mockito.kotlin.isNull
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.http.HttpStatus
import org.springframework.http.HttpStatus.NOT_FOUND
import tools.jackson.databind.json.JsonMapper
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.courtsentencing.CourtSentencingDpsApiExtension.Companion.dpsCourtSentencingServer
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.courtsentencing.model.LegacyCreateSentence
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.courtsentencing.model.MergeCreateChargeResponse
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.courtsentencing.model.MergeCreateCourtAppearanceResponse
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.courtsentencing.model.MergeCreateCourtCaseResponse
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.courtsentencing.model.MergeCreateCourtCasesResponse
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.courtsentencing.model.MergeCreatePeriodLengthResponse
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.courtsentencing.model.MergeCreateSentenceResponse
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.courtsentencing.model.MergeSentenceId
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.courtsentencing.model.NomisPeriodLengthId
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.helper.mergeDomainEvent
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.integration.SqsIntegrationTestBase
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.integration.countAllMessagesOnDLQQueue
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.integration.hasMessagesOnDLQQueue
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.integration.sendMessage
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.listeners.SQSMessage
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomismappings.model.CourtAppearanceMappingDto
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomismappings.model.CourtCaseBatchMappingDto
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomismappings.model.CourtCaseMappingDto
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomismappings.model.CourtChargeMappingDto
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomismappings.model.SentenceMappingDto
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomisprisoner.model.CaseIdentifierResponse
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomisprisoner.model.CodeDescription
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomisprisoner.model.OffenceResponse
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomisprisoner.model.RecallCustodyDate
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.wiremock.withRequestBodyJsonPath
import uk.gov.justice.hmpps.sqs.countAllMessagesOnQueue
import java.math.BigDecimal
import java.time.LocalDate
import java.time.LocalDateTime
import java.util.AbstractMap.SimpleEntry
import java.util.UUID

private const val NOMIS_COURT_CASE_ID = 1234L
private const val NOMIS_COURT_APPEARANCE_ID = 5555L
private const val NOMIS_CASE_IDENTIFIER = "GH123"
private const val NOMIS_CASE_IDENTIFIER_TYPE = "CASE/INFO#"
private const val DPS_COURT_CASE_ID = "cc1"
private const val DPS_COURT_APPEARANCE_ID = "6f35a357-f458-40b9-b824-de729ffeb459"
private const val EXISTING_DPS_COURT_CASE_ID = "cc2"
private const val EXISTING_DPS_COURT_APPEARANCE_ID = "9d99a357-f458-40b9-b824-de729ffeb459"
private const val EXISTING_DPS_CHARGE_ID = "88d8a357-f458-40b9-b824-de729ffeb459"
private const val OFFENDER_ID_DISPLAY = "A3864DZ"
private const val NOMIS_BOOKING_ID = 12344321L
private const val NOMIS_OFFENDER_CHARGE_ID = 7777L
private const val DPS_CHARGE_ID = "5b35a357-f458-40b9-b824-de729ffeb488"

class CourtSentencingSynchronisationIntTest : SqsIntegrationTestBase() {
  @Autowired
  private lateinit var courtSentencingNomisApiMockServer: CourtSentencingNomisApiMockServer

  @Autowired
  private lateinit var courtSentencingMappingApiMockServer: CourtSentencingMappingApiMockServer

  @Autowired
  private lateinit var jsonMapper: JsonMapper

  private fun Any.toJson(): String = jsonMapper.writeValueAsString(this)

  @Nested
  @DisplayName("OFFENDER_CASES-INSERTED")
  inner class CourtCaseInserted {

    @Nested
    @DisplayName("When court sentencing was created in DPS")
    inner class DPSCreated {

      @BeforeEach
      fun setUp() {
        courtSentencingNomisApiMockServer.stubGetCourtCase(
          courtCaseId = NOMIS_COURT_CASE_ID,
          bookingId = NOMIS_BOOKING_ID,
          offenderNo = OFFENDER_ID_DISPLAY,
        )
        courtSentencingOffenderEventsQueue.sendMessage(
          courtCaseEvent(
            eventType = "OFFENDER_CASES-INSERTED",
            auditModule = "DPS_SYNCHRONISATION",
          ),
        ).also {
          waitForAnyProcessingToComplete()
        }
      }

      @Test
      fun `the event is ignored`() {
        await untilAsserted {
          verify(telemetryClient).trackEvent(
            eq("court-case-synchronisation-created-skipped"),
            check {
              assertThat(it["offenderNo"]).isEqualTo("A3864DZ")
              assertThat(it["nomisBookingId"]).isEqualTo(NOMIS_BOOKING_ID.toString())
              assertThat(it["nomisCourtCaseId"]).isEqualTo(NOMIS_COURT_CASE_ID.toString())
            },
            isNull(),
          )
        }

        courtSentencingMappingApiMockServer.verify(
          0,
          getRequestedFor(urlPathMatching("/mapping/court-sentencing/court-cases/nomis-court-case-id/\\d+")),
        )
        // will not create a court case in DPS
        dpsCourtSentencingServer.verify(0, postRequestedFor(anyUrl()))
      }
    }

    @Nested
    @DisplayName("When court case was created in NOMIS")
    inner class NomisCreated {
      @BeforeEach
      fun setUp() {
        courtSentencingNomisApiMockServer.stubGetCourtCase(
          bookingId = NOMIS_BOOKING_ID,
          courtCaseId = NOMIS_COURT_CASE_ID,
        )
      }

      @Nested
      @DisplayName("When mapping does not exist yet")
      inner class NoMapping {
        @BeforeEach
        fun setUp() {
          courtSentencingMappingApiMockServer.stubGetByNomisId(status = NOT_FOUND)
          dpsCourtSentencingServer.stubPostCourtCaseForCreate(courtCaseId = DPS_COURT_CASE_ID)
          courtSentencingMappingApiMockServer.stubPostMapping()
          courtSentencingOffenderEventsQueue.sendMessage(
            courtCaseEvent(
              eventType = "OFFENDER_CASES-INSERTED",
            ),
          ).also {
            waitForAnyProcessingToComplete()
          }
        }

        @Test
        fun `will create a court case in DPS`() {
          dpsCourtSentencingServer.verify(
            postRequestedFor(urlPathEqualTo("/legacy/court-case"))
              .withRequestBody(matchingJsonPath("prisonerId", equalTo(OFFENDER_ID_DISPLAY)))
              .withRequestBody(matchingJsonPath("bookingId", equalTo(NOMIS_BOOKING_ID.toString()))),
          )
        }

        @Test
        fun `will create mapping between DPS and NOMIS ids`() {
          courtSentencingMappingApiMockServer.verify(
            postRequestedFor(urlPathEqualTo("/mapping/court-sentencing/court-cases"))
              .withRequestBody(matchingJsonPath("dpsCourtCaseId", equalTo(DPS_COURT_CASE_ID)))
              .withRequestBody(matchingJsonPath("nomisCourtCaseId", equalTo(NOMIS_COURT_CASE_ID.toString())))
              .withRequestBody(matchingJsonPath("mappingType", equalTo("NOMIS_CREATED"))),
          )
        }

        @Test
        fun `will retrieve the court case from nomis`() {
          courtSentencingNomisApiMockServer.verify(
            getRequestedFor(urlPathEqualTo("/prisoners/$OFFENDER_ID_DISPLAY/sentencing/court-cases/$NOMIS_COURT_CASE_ID")),
          )
        }

        @Test
        fun `will track a telemetry event for success`() {
          verify(telemetryClient).trackEvent(
            eq("court-case-synchronisation-created-success"),
            check {
              assertThat(it["offenderNo"]).isEqualTo(OFFENDER_ID_DISPLAY)
              assertThat(it["nomisBookingId"]).isEqualTo(NOMIS_BOOKING_ID.toString())
              assertThat(it["nomisCourtCaseId"]).isEqualTo(NOMIS_COURT_CASE_ID.toString())
              assertThat(it["dpsCourtCaseId"]).isEqualTo(DPS_COURT_CASE_ID)
              assertThat(it).doesNotContain(SimpleEntry("mapping", "initial-failure"))
            },
            isNull(),
          )
        }
      }

      @Nested
      @DisplayName("When dps returns an error on create")
      inner class DPSCreateFails {
        @BeforeEach
        fun setUp() {
          courtSentencingMappingApiMockServer.stubGetByNomisId(status = NOT_FOUND)
          dpsCourtSentencingServer.stubPostCourtCaseForCreate(courtCaseId = DPS_COURT_CASE_ID)
          dpsCourtSentencingServer.stubPostCourtCaseForCreateError()
          courtSentencingOffenderEventsQueue.sendMessage(
            courtCaseEvent(
              eventType = "OFFENDER_CASES-INSERTED",
            ),
          ).also {
            waitForAnyProcessingToComplete()
          }
        }

        @Test
        fun `DPS failure is tracked`() {
          verify(telemetryClient).trackEvent(
            eq("court-case-synchronisation-created-error"),
            check {
              assertThat(it["offenderNo"]).isEqualTo(OFFENDER_ID_DISPLAY)
              assertThat(it["nomisBookingId"]).isEqualTo(NOMIS_BOOKING_ID.toString())
              assertThat(it["nomisCourtCaseId"]).isEqualTo(NOMIS_COURT_CASE_ID.toString())
            },
            isNull(),
          )

          // will not create a mapping
          courtSentencingMappingApiMockServer.verify(0, postRequestedFor(anyUrl()))
        }
      }

      @Nested
      @DisplayName("When mapping already exists")
      inner class MappingExists {
        @BeforeEach
        fun setUp() {
          courtSentencingMappingApiMockServer.stubGetByNomisId(
            nomisCourtCaseId = NOMIS_COURT_CASE_ID,
            dpsCourtCaseId = DPS_COURT_CASE_ID,
            mapping = CourtCaseMappingDto(
              nomisCourtCaseId = NOMIS_COURT_CASE_ID,
              dpsCourtCaseId = DPS_COURT_CASE_ID,
            ),
          )
          courtSentencingOffenderEventsQueue.sendMessage(
            courtCaseEvent(
              eventType = "OFFENDER_CASES-INSERTED",
              bookingId = NOMIS_BOOKING_ID,
              caseId = NOMIS_COURT_CASE_ID,
              offenderNo = OFFENDER_ID_DISPLAY,
            ),
          ).also {
            waitForAnyProcessingToComplete()
          }
        }

        @Test
        fun `the event is ignored`() {
          verify(telemetryClient).trackEvent(
            eq("court-case-synchronisation-created-ignored"),
            check {
              assertThat(it["offenderNo"]).isEqualTo("A3864DZ")
              assertThat(it["nomisBookingId"]).isEqualTo(NOMIS_BOOKING_ID.toString())
              assertThat(it["nomisCourtCaseId"]).isEqualTo(NOMIS_COURT_CASE_ID.toString())
              assertThat(it["dpsCourtCaseId"]).isEqualTo(DPS_COURT_CASE_ID)
            },
            isNull(),
          )
          // will not create a court case in DPS
          dpsCourtSentencingServer.verify(0, postRequestedFor(anyUrl()))
        }
      }

      @Nested
      @DisplayName("When mapping POST fails")
      inner class MappingFail {
        @BeforeEach
        fun setUp() {
          courtSentencingMappingApiMockServer.stubGetByNomisId(status = NOT_FOUND)
          dpsCourtSentencingServer.stubPostCourtCaseForCreate(courtCaseId = DPS_COURT_CASE_ID)
        }

        @Nested
        @DisplayName("Fails once")
        inner class FailsOnce {
          @BeforeEach
          fun setUp() {
            courtSentencingMappingApiMockServer.stubPostMappingFailureFollowedBySuccess()
            courtSentencingOffenderEventsQueue.sendMessage(
              courtCaseEvent(
                eventType = "OFFENDER_CASES-INSERTED",
              ),
            ).also {
              waitForAnyProcessingToComplete("court-case-mapping-created-synchronisation-success")
            }
          }

          @Test
          fun `will create a court case in DPS`() {
            dpsCourtSentencingServer.verify(
              postRequestedFor(urlPathEqualTo("/legacy/court-case")),
            )
          }

          @Test
          fun `will attempt to create mapping two times and succeed`() {
            courtSentencingMappingApiMockServer.verify(
              WireMock.exactly(2),
              postRequestedFor(urlPathEqualTo("/mapping/court-sentencing/court-cases"))
                .withRequestBody(matchingJsonPath("dpsCourtCaseId", equalTo(DPS_COURT_CASE_ID)))
                .withRequestBody(matchingJsonPath("nomisCourtCaseId", equalTo(NOMIS_COURT_CASE_ID.toString())))
                .withRequestBody(matchingJsonPath("mappingType", equalTo("NOMIS_CREATED"))),
            )

            assertThat(
              courtSentencingOffenderEventsQueue.countAllMessagesOnDLQQueue(),
            ).isEqualTo(0)
          }

          @Test
          fun `will track a telemetry event for partial success`() {
            verify(telemetryClient).trackEvent(
              eq("court-case-synchronisation-created-success"),
              check {
                assertThat(it["offenderNo"]).isEqualTo(OFFENDER_ID_DISPLAY)
                assertThat(it["nomisBookingId"]).isEqualTo(NOMIS_BOOKING_ID.toString())
                assertThat(it["nomisCourtCaseId"]).isEqualTo(NOMIS_COURT_CASE_ID.toString())
                assertThat(it["dpsCourtCaseId"]).isEqualTo(DPS_COURT_CASE_ID)
                assertThat(it["mapping"]).isEqualTo("initial-failure")
              },
              isNull(),
            )

            verify(telemetryClient).trackEvent(
              eq("court-case-mapping-created-synchronisation-success"),
              check {
                assertThat(it["offenderNo"]).isEqualTo("A3864DZ")
                assertThat(it["nomisBookingId"]).isEqualTo(NOMIS_BOOKING_ID.toString())
                assertThat(it["nomisCourtCaseId"]).isEqualTo(NOMIS_COURT_CASE_ID.toString())
                assertThat(it["dpsCourtCaseId"]).isEqualTo(DPS_COURT_CASE_ID)
              },
              isNull(),
            )
          }
        }

        @Nested
        @DisplayName("Fails constantly")
        inner class FailsConstantly {
          @BeforeEach
          fun setUp() {
            courtSentencingMappingApiMockServer.stubPostMapping(status = HttpStatus.INTERNAL_SERVER_ERROR)
            courtSentencingOffenderEventsQueue.sendMessage(
              courtCaseEvent(
                eventType = "OFFENDER_CASES-INSERTED",
              ),
            )
            await untilCallTo {
              courtSentencingOffenderEventsQueue.countAllMessagesOnDLQQueue()
            } matches { it == 1 }
          }

          @Test
          fun `will create a court case in DPS`() {
            dpsCourtSentencingServer.verify(
              1,
              postRequestedFor(urlPathEqualTo("/legacy/court-case")),
            )
          }

          @Test
          fun `will attempt to create mapping several times and keep failing`() {
            courtSentencingMappingApiMockServer.verify(
              WireMock.exactly(3),
              postRequestedFor(urlPathEqualTo("/mapping/court-sentencing/court-cases")),
            )
          }

          @Test
          fun `will track a telemetry event for success`() {
            verify(telemetryClient).trackEvent(
              eq("court-case-synchronisation-created-success"),
              check {
                assertThat(it["offenderNo"]).isEqualTo("A3864DZ")
                assertThat(it["nomisBookingId"]).isEqualTo(NOMIS_BOOKING_ID.toString())
                assertThat(it["nomisCourtCaseId"]).isEqualTo(NOMIS_COURT_CASE_ID.toString())
                assertThat(it["dpsCourtCaseId"]).isEqualTo(DPS_COURT_CASE_ID)
                assertThat(it["mapping"]).isEqualTo("initial-failure")
              },
              isNull(),
            )
          }
        }
      }
    }

    @Nested
    @DisplayName("duplicate mapping - two messages received at the same time")
    inner class WhenDuplicate {

      @Test
      internal fun `it will not retry after a 409 (duplicate court case written to Sentencing API)`() {
        // in the case of multiple events received at the same time - mapping doesn't exist
        courtSentencingMappingApiMockServer.stubGetByNomisId(status = NOT_FOUND)

        courtSentencingNomisApiMockServer.stubGetCourtCase(
          bookingId = NOMIS_BOOKING_ID,
          courtCaseId = NOMIS_COURT_CASE_ID,
        )
        dpsCourtSentencingServer.stubPostCourtCaseForCreate(courtCaseId = DPS_COURT_CASE_ID)

        courtSentencingMappingApiMockServer.stubCourtCaseMappingCreateConflict(
          existingDpsCourtCaseId = EXISTING_DPS_COURT_CASE_ID,
          duplicateDpsCourtCaseId = DPS_COURT_CASE_ID,
          nomisCourtCaseId = NOMIS_COURT_CASE_ID,
        )

        courtSentencingOffenderEventsQueue.sendMessage(
          courtCaseEvent(
            eventType = "OFFENDER_CASES-INSERTED",
            caseId = NOMIS_COURT_CASE_ID,
            bookingId = NOMIS_BOOKING_ID,
            offenderNo = OFFENDER_ID_DISPLAY,
          ),
        ).also { waitForAnyProcessingToComplete("from-nomis-sync-court-case-duplicate") }

        // doesn't retry
        dpsCourtSentencingServer.verify(
          1,
          postRequestedFor(urlPathEqualTo("/legacy/court-case")),
        )

        verify(telemetryClient).trackEvent(
          org.mockito.kotlin.eq("from-nomis-sync-court-case-duplicate"),
          check {
            assertThat(it["migrationId"]).isNull()
            assertThat(it["existingDpsCourtCaseId"]).isEqualTo(EXISTING_DPS_COURT_CASE_ID)
            assertThat(it["duplicateDpsCourtCaseId"]).isEqualTo(DPS_COURT_CASE_ID)
            assertThat(it["existingNomisCourtCaseId"]).isEqualTo(NOMIS_COURT_CASE_ID.toString())
            assertThat(it["duplicateNomisCourtCaseId"]).isEqualTo(NOMIS_COURT_CASE_ID.toString())
          },
          isNull(),
        )
      }
    }
  }

  @Nested
  @DisplayName("OFFENDER_CASES-DELETED")
  inner class CourtCaseDeleted {

    @Nested
    @DisplayName("When court case was deleted in NOMIS")
    inner class NomisDeleted {

      @Nested
      @DisplayName("When mapping does not exist")
      inner class NoMapping {
        @BeforeEach
        fun setUp() {
          courtSentencingMappingApiMockServer.stubGetByNomisId(status = NOT_FOUND)
          courtSentencingOffenderEventsQueue.sendMessage(
            courtCaseEvent(
              eventType = "OFFENDER_CASES-DELETED",
            ),
          ).also { waitForAnyProcessingToComplete() }
        }

        @Test
        fun `the event is ignored`() {
          verify(telemetryClient).trackEvent(
            eq("court-case-synchronisation-deleted-ignored"),
            check {
              assertThat(it["offenderNo"]).isEqualTo("A3864DZ")
              assertThat(it["nomisBookingId"]).isEqualTo(NOMIS_BOOKING_ID.toString())
              assertThat(it["nomisCourtCaseId"]).isEqualTo(NOMIS_COURT_CASE_ID.toString())
            },
            isNull(),
          )
          // will not delete a court case in DPS
          dpsCourtSentencingServer.verify(0, deleteRequestedFor(anyUrl()))
        }
      }

      @Nested
      @DisplayName("When mapping already exists")
      inner class MappingExists {
        @BeforeEach
        fun setUp() {
          courtSentencingMappingApiMockServer.stubGetByNomisId(
            nomisCourtCaseId = NOMIS_COURT_CASE_ID,
            dpsCourtCaseId = DPS_COURT_CASE_ID,
            mapping = CourtCaseMappingDto(
              nomisCourtCaseId = NOMIS_COURT_CASE_ID,
              dpsCourtCaseId = DPS_COURT_CASE_ID,
            ),
          )
          courtSentencingMappingApiMockServer.stubDeleteCourtCaseMapping(dpsCourtCaseId = DPS_COURT_CASE_ID)
          dpsCourtSentencingServer.stubDeleteCourtCase(DPS_COURT_CASE_ID)
          courtSentencingOffenderEventsQueue.sendMessage(
            courtCaseEvent(
              eventType = "OFFENDER_CASES-DELETED",
              bookingId = NOMIS_BOOKING_ID,
              caseId = NOMIS_COURT_CASE_ID,
              offenderNo = OFFENDER_ID_DISPLAY,
            ),
          ).also {
            waitForAnyProcessingToComplete()
          }
        }

        @Test
        fun `will delete a court case in DPS`() {
          dpsCourtSentencingServer.verify(
            1,
            deleteRequestedFor(urlPathEqualTo("/legacy/court-case/$DPS_COURT_CASE_ID")),
            // TODO DPS to implement this endpoint
          )
        }

        @Test
        fun `will delete mapping between DPS and NOMIS ids`() {
          courtSentencingMappingApiMockServer.verify(
            1,
            deleteRequestedFor(urlPathEqualTo("/mapping/court-sentencing/court-cases/dps-court-case-id/$DPS_COURT_CASE_ID")),
          )
        }

        @Test
        fun `will track a telemetry event for success`() {
          verify(telemetryClient).trackEvent(
            eq("court-case-synchronisation-deleted-success"),
            check {
              assertThat(it["offenderNo"]).isEqualTo(OFFENDER_ID_DISPLAY)
              assertThat(it["nomisBookingId"]).isEqualTo(NOMIS_BOOKING_ID.toString())
              assertThat(it["nomisCourtCaseId"]).isEqualTo(NOMIS_COURT_CASE_ID.toString())
              assertThat(it["dpsCourtCaseId"]).isEqualTo(DPS_COURT_CASE_ID)
            },
            isNull(),
          )
        }
      }
    }
  }

  @Nested
  @DisplayName("OFFENDER_CASES-UPDATED")
  inner class CourtCaseUpdated {
    @Nested
    @DisplayName("When court case was updated in DPS")
    inner class DPSUpdated {
      @BeforeEach
      fun setUp() {
        courtSentencingOffenderEventsQueue.sendMessage(
          courtCaseEvent(
            eventType = "OFFENDER_CASES-UPDATED",
            auditModule = "DPS_SYNCHRONISATION",
          ),
        ).also { waitForAnyProcessingToComplete() }
      }

      @Test
      fun `the event is ignored`() {
        verify(telemetryClient).trackEvent(
          eq("court-case-synchronisation-updated-skipped"),
          check {
            assertThat(it["offenderNo"]).isEqualTo(OFFENDER_ID_DISPLAY)
            assertThat(it["nomisBookingId"]).isEqualTo(NOMIS_BOOKING_ID.toString())
            assertThat(it["nomisCourtCaseId"]).isEqualTo(NOMIS_COURT_CASE_ID.toString())
          },
          isNull(),
        )

        // will not bother getting the court case or the mapping
        courtSentencingNomisApiMockServer.verify(
          0,
          getRequestedFor(urlPathMatching("/prisoners/\\d+/sentencing/court-cases/\\d+")),
        )

        courtSentencingMappingApiMockServer.verify(
          0,
          getRequestedFor(urlPathMatching("/mapping/court-sentencing/court-cases/nomis-court-case-id/\\d+")),
        )
        // will not create a court case in DPS
        dpsCourtSentencingServer.verify(0, putRequestedFor(anyUrl()))
      }
    }

    @Nested
    @DisplayName("When court case was updated in NOMIS")
    inner class NomisUpdated {

      @BeforeEach
      fun setUp() {
        courtSentencingNomisApiMockServer.stubGetCourtCase(
          courtCaseId = NOMIS_COURT_CASE_ID,
          bookingId = NOMIS_BOOKING_ID,
          offenderNo = OFFENDER_ID_DISPLAY,
        )
      }

      @Nested
      @DisplayName("When mapping doesn't exist")
      inner class MappingDoesNotExist {
        @BeforeEach
        fun setUp() {
          courtSentencingMappingApiMockServer.stubGetByNomisId(status = NOT_FOUND)
          courtSentencingOffenderEventsQueue.sendMessage(
            courtCaseEvent(
              eventType = "OFFENDER_CASES-UPDATED",
            ),
          ).also { waitForAnyProcessingToComplete("court-case-synchronisation-updated-failed", 2) }
        }

        @Test
        fun `telemetry added to track the failure`() {
          verify(telemetryClient, times(2)).trackEvent(
            eq("court-case-synchronisation-updated-failed"),
            check {
              assertThat(it["offenderNo"]).isEqualTo(OFFENDER_ID_DISPLAY)
              assertThat(it["nomisBookingId"]).isEqualTo(NOMIS_BOOKING_ID.toString())
              assertThat(it["nomisCourtCaseId"]).isEqualTo(NOMIS_COURT_CASE_ID.toString())
            },
            isNull(),
          )
        }

        @Test
        fun `the event is placed on dead letter queue`() {
          await untilAsserted {
            assertThat(
              courtSentencingOffenderEventsQueue.countAllMessagesOnDLQQueue(),
            ).isEqualTo(1)
          }
        }
      }

      @Nested
      @DisplayName("When mapping exists")
      inner class MappingExists {
        @BeforeEach
        fun setUp() {
          courtSentencingMappingApiMockServer.stubGetByNomisId(
            nomisCourtCaseId = NOMIS_COURT_CASE_ID,
            dpsCourtCaseId = DPS_COURT_CASE_ID,
          )

          dpsCourtSentencingServer.stubPutCourtCaseForUpdate(courtCaseId = DPS_COURT_CASE_ID)
          courtSentencingOffenderEventsQueue.sendMessage(
            courtCaseEvent(
              eventType = "OFFENDER_CASES-UPDATED",
            ),
          ).also {
            waitForAnyProcessingToComplete()
          }
        }

        @Test
        fun `will update DPS with the changes`() {
          dpsCourtSentencingServer.verify(
            1,
            putRequestedFor(urlPathEqualTo("/legacy/court-case/$DPS_COURT_CASE_ID"))
              .withRequestBody(matchingJsonPath("bookingId", equalTo(NOMIS_BOOKING_ID.toString()))),
          )
        }

        @Test
        fun `will track a telemetry event for success`() {
          verify(telemetryClient).trackEvent(
            eq("court-case-synchronisation-updated-success"),
            check {
              assertThat(it["offenderNo"]).isEqualTo(OFFENDER_ID_DISPLAY)
              assertThat(it["nomisBookingId"]).isEqualTo(NOMIS_BOOKING_ID.toString())
              assertThat(it["nomisCourtCaseId"]).isEqualTo(NOMIS_COURT_CASE_ID.toString())
              assertThat(it["dpsCourtCaseId"]).isEqualTo(DPS_COURT_CASE_ID)
            },
            isNull(),
          )
        }
      }
    }
  }

  @Nested
  @DisplayName("courtsentencing.resync.case")
  inner class CaseResynchronisation {

    @Nested
    @DisplayName("When resynchronisation of case required")
    inner class ResyncMessageReceived {

      @BeforeEach
      fun setUp() {
        courtSentencingNomisApiMockServer.stubGetCourtCase(
          courtCaseId = NOMIS_COURT_CASE_ID,
          bookingId = NOMIS_BOOKING_ID,
          offenderNo = OFFENDER_ID_DISPLAY,
        )

        dpsCourtSentencingServer.stubPutCourtCaseForUpdate(courtCaseId = DPS_COURT_CASE_ID)

        courtSentencingOffenderEventsQueue.sendMessage(
          SQSMessage(
            Type = "courtsentencing.resync.case",
            Message = OffenderCaseResynchronisationEvent(
              offenderNo = OFFENDER_ID_DISPLAY,
              caseId = NOMIS_COURT_CASE_ID,
              dpsCaseUuid = DPS_COURT_CASE_ID,
            ).toJson(),
          ).toJson(),
        ).also { waitForAnyProcessingToComplete() }
      }

      @Test
      fun `will update DPS with the changes`() {
        dpsCourtSentencingServer.verify(
          1,
          putRequestedFor(urlPathEqualTo("/legacy/court-case/$DPS_COURT_CASE_ID"))
            .withRequestBody(matchingJsonPath("bookingId", equalTo(NOMIS_BOOKING_ID.toString()))),
        )
      }

      @Test
      fun `will track a telemetry event for success`() {
        verify(telemetryClient).trackEvent(
          eq("court-case-resynchronisation-success"),
          check {
            assertThat(it["offenderNo"]).isEqualTo(OFFENDER_ID_DISPLAY)
            assertThat(it["nomisBookingId"]).isEqualTo(NOMIS_BOOKING_ID.toString())
            assertThat(it["nomisCaseId"]).isEqualTo(NOMIS_COURT_CASE_ID.toString())
            assertThat(it["dpsCaseId"]).isEqualTo(DPS_COURT_CASE_ID)
          },
          isNull(),
        )
      }
    }
  }

  @Nested
  @DisplayName("courtsentencing.resync.case.booking")
  inner class CaseBookingResynchronisation {

    @Nested
    @DisplayName("When resynchronisation of cases for a cloned booking required")
    inner class ResyncMessageReceived {
      @Nested
      inner class HappyPath {
        val nomisCourtCaseUpdatedId = 727272L
        val dpsCourtCaseUpdatedId = "e1f86fba-6584-46c3-9533-8f636347e141"

        val nomisSentenceSequence = 7
        val dpsSentenceUpdateId = "fe31eba3-3bc2-4092-badd-4eddc0faa1eb"
        val dpsCourtAppearanceId = "9164b690-a3c3-486a-b413-07084d869cbf"

        @BeforeEach
        fun setUp() {
          // create in DPS of court case from previous booking
          courtSentencingNomisApiMockServer.stubGetCourtCases(offenderNo = OFFENDER_ID_DISPLAY)
          dpsCourtSentencingServer.stubCreateCourtCaseCloneBooking(response = dpsBookingCloneCreateResponseWithTwoAppearancesAndTwoCharges())
          courtSentencingMappingApiMockServer.stubReplaceOrCreateMappings()

          // update to DPS due to booking id change on case changing
          courtSentencingMappingApiMockServer.stubGetByNomisId(nomisCourtCaseId = nomisCourtCaseUpdatedId, dpsCourtCaseId = dpsCourtCaseUpdatedId)
          courtSentencingNomisApiMockServer.stubGetCourtCase(courtCaseId = nomisCourtCaseUpdatedId, offenderNo = OFFENDER_ID_DISPLAY, bookingId = NOMIS_BOOKING_ID)
          dpsCourtSentencingServer.stubPutCourtCaseForUpdate(courtCaseId = dpsCourtCaseUpdatedId)

          // update to DPS due to booking id change on sentence changing
          courtSentencingMappingApiMockServer.stubGetSentenceByNomisId(nomisSentenceSequence = nomisSentenceSequence, nomisBookingId = NOMIS_BOOKING_ID, dpsSentenceId = dpsSentenceUpdateId)
          courtSentencingNomisApiMockServer.stubGetSentence(offenderNo = OFFENDER_ID_DISPLAY, caseId = nomisCourtCaseUpdatedId, bookingId = NOMIS_BOOKING_ID, sentenceSequence = nomisSentenceSequence, response = sentenceResponse(bookingId = NOMIS_BOOKING_ID, sentenceSequence = nomisSentenceSequence, eventId = NOMIS_COURT_APPEARANCE_ID))
          courtSentencingMappingApiMockServer.stubGetCourtAppearanceByNomisId(nomisCourtAppearanceId = NOMIS_COURT_APPEARANCE_ID, dpsCourtAppearanceId = dpsCourtAppearanceId)
          dpsCourtSentencingServer.stubPutSentenceForUpdate(sentenceId = dpsSentenceUpdateId)

          courtSentencingOffenderEventsQueue.sendMessage(
            SQSMessage(
              Type = "courtsentencing.resync.case.booking",
              Message = OffenderCaseBookingResynchronisationEvent(
                offenderNo = OFFENDER_ID_DISPLAY,
                caseIds = listOf(NOMIS_COURT_CASE_ID),
                fromBookingId = 54321,
                toBookingId = NOMIS_BOOKING_ID,
                casesMoved = listOf(
                  CaseBookingChanged(
                    caseId = nomisCourtCaseUpdatedId,
                    sentences = listOf(
                      SentenceBookingChanged(
                        sentenceSequence = nomisSentenceSequence,
                      ),
                    ),
                  ),
                ),
              ).toJson(),
            ).toJson(),
          ).also { waitForAnyProcessingToComplete("court-case-booking-resynchronisation-success") }
        }

        @Test
        fun `will update court case on latest booking in DPS`() {
          dpsCourtSentencingServer.verify(
            1,
            putRequestedFor(urlPathEqualTo("/legacy/court-case/$dpsCourtCaseUpdatedId"))
              .withRequestBody(matchingJsonPath("bookingId", equalTo(NOMIS_BOOKING_ID.toString()))),
          )
        }

        @Test
        fun `will recreate court case on previous booking in DPS`() {
          dpsCourtSentencingServer.verify(
            1,
            postRequestedFor(urlPathEqualTo("/legacy/court-case/booking")),
          )
        }

        @Test
        fun `will recreate court case mappings on previous booking`() {
          courtSentencingMappingApiMockServer.verify(
            1,
            putRequestedFor(urlPathEqualTo("/mapping/court-sentencing/court-cases/replace")),
          )
        }

        @Test
        fun `will track a telemetry event for success`() {
          verify(telemetryClient).trackEvent(
            eq("court-case-booking-resynchronisation-success"),
            check {
              assertThat(it["offenderNo"]).isEqualTo(OFFENDER_ID_DISPLAY)
              assertThat(it["nomisCaseIds"]).isEqualTo("$NOMIS_COURT_CASE_ID")
              assertThat(it["toBookingId"]).isEqualTo("$NOMIS_BOOKING_ID")
              assertThat(it["fromBookingId"]).isEqualTo("54321")
            },
            isNull(),
          )
        }
      }

      @Nested
      inner class MappingFailsOnce {

        @BeforeEach
        fun setUp() {
          courtSentencingNomisApiMockServer.stubGetCourtCases(offenderNo = OFFENDER_ID_DISPLAY)
          dpsCourtSentencingServer.stubCreateCourtCaseCloneBooking(response = dpsBookingCloneCreateResponseWithTwoAppearancesAndTwoCharges())
          courtSentencingMappingApiMockServer.stubReplaceOrCreateMappingsFailureFollowedBySuccess()

          courtSentencingOffenderEventsQueue.sendMessage(
            SQSMessage(
              Type = "courtsentencing.resync.case.booking",
              Message = OffenderCaseBookingResynchronisationEvent(
                offenderNo = OFFENDER_ID_DISPLAY,
                caseIds = listOf(NOMIS_COURT_CASE_ID),
                toBookingId = NOMIS_BOOKING_ID,
              ).toJson(),
            ).toJson(),
          ).also { waitForAnyProcessingToComplete("from-nomis-synch-court-case-booking-clone-mapping-retry-success") }
        }

        @Test
        fun `will track a telemetry event for success`() {
          verify(telemetryClient).trackEvent(
            eq("court-case-booking-resynchronisation-success"),
            check {
              assertThat(it["offenderNo"]).isEqualTo(OFFENDER_ID_DISPLAY)
              assertThat(it["nomisCaseIds"]).isEqualTo("$NOMIS_COURT_CASE_ID")
            },
            isNull(),
          )
        }

        @Test
        fun `will track a telemetry event for mapping retry success`() {
          verify(telemetryClient).trackEvent(
            eq("from-nomis-synch-court-case-booking-clone-mapping-retry-success"),
            check {
              assertThat(it["offenderNo"]).isEqualTo(OFFENDER_ID_DISPLAY)
            },
            isNull(),
          )
        }
      }
    }
  }

  @Nested
  @DisplayName("OFFENDER_CASES-LINKED")
  inner class CourtCaseLinked {
    val combinedCaseId = 5432L
    val dpsTargetId = "1b0a031e-dc57-4273-bb9f-c45ea3f68584"

    @Nested
    @DisplayName("When court case was linked in DPS")
    inner class DPSUpdated {
      @BeforeEach
      fun setUp() {
        courtSentencingOffenderEventsQueue.sendMessage(
          courtCaseLinkingEvent(
            eventType = "OFFENDER_CASES-LINKED",
            auditModule = "DPS_SYNCHRONISATION",
            combinedCaseId = combinedCaseId,
          ),
        ).also { waitForAnyProcessingToComplete() }
      }

      @Test
      fun `the event is ignored`() {
        verify(telemetryClient).trackEvent(
          eq("court-case-synchronisation-link-skipped"),
          check {
            assertThat(it["offenderNo"]).isEqualTo(OFFENDER_ID_DISPLAY)
            assertThat(it["nomisBookingId"]).isEqualTo(NOMIS_BOOKING_ID.toString())
            assertThat(it["nomisCourtCaseId"]).isEqualTo(NOMIS_COURT_CASE_ID.toString())
            assertThat(it["nomisCombinedCourtCaseId"]).isEqualTo(combinedCaseId.toString())
          },
          isNull(),
        )

        // will not bother getting the court case or the mapping
        courtSentencingNomisApiMockServer.verify(
          0,
          getRequestedFor(urlPathMatching("/prisoners/\\d+/sentencing/court-cases/\\d+")),
        )

        courtSentencingMappingApiMockServer.verify(
          0,
          getRequestedFor(urlPathMatching("/mapping/court-sentencing/court-cases/nomis-court-case-id/\\d+")),
        )
        // will not create a court case in DPS
        dpsCourtSentencingServer.verify(0, putRequestedFor(anyUrl()))
      }
    }

    @Nested
    @DisplayName("When court case was linked in NOMIS")
    inner class NomisUpdated {

      @BeforeEach
      fun setUp() {
        courtSentencingNomisApiMockServer.stubGetCourtCase(
          courtCaseId = NOMIS_COURT_CASE_ID,
          bookingId = NOMIS_BOOKING_ID,
          offenderNo = OFFENDER_ID_DISPLAY,
        )
      }

      @Nested
      @DisplayName("When mapping doesn't exist")
      inner class MappingDoesNotExist {
        @BeforeEach
        fun setUp() {
          courtSentencingMappingApiMockServer.stubGetByNomisId(status = NOT_FOUND)
          courtSentencingOffenderEventsQueue.sendMessage(
            courtCaseLinkingEvent(
              eventType = "OFFENDER_CASES-LINKED",
              combinedCaseId = combinedCaseId,
            ),
          ).also { waitForAnyProcessingToComplete("court-case-synchronisation-link-error", 2) }
        }

        @Test
        fun `telemetry added to track the failure`() {
          verify(telemetryClient, times(2)).trackEvent(
            eq("court-case-synchronisation-link-error"),
            check {
              assertThat(it["offenderNo"]).isEqualTo(OFFENDER_ID_DISPLAY)
              assertThat(it["nomisBookingId"]).isEqualTo(NOMIS_BOOKING_ID.toString())
              assertThat(it["nomisCourtCaseId"]).isEqualTo(NOMIS_COURT_CASE_ID.toString())
              assertThat(it["nomisCombinedCourtCaseId"]).isEqualTo(combinedCaseId.toString())
            },
            isNull(),
          )
        }

        @Test
        fun `the event is placed on dead letter queue`() {
          await untilAsserted {
            assertThat(
              courtSentencingOffenderEventsQueue.countAllMessagesOnDLQQueue(),
            ).isEqualTo(1)
          }
        }
      }

      @Nested
      @DisplayName("When mapping exists")
      inner class MappingExists {
        @BeforeEach
        fun setUp() {
          courtSentencingMappingApiMockServer.stubGetByNomisId(
            nomisCourtCaseId = NOMIS_COURT_CASE_ID,
            dpsCourtCaseId = DPS_COURT_CASE_ID,
          )
          courtSentencingMappingApiMockServer.stubGetByNomisId(
            nomisCourtCaseId = combinedCaseId,
            dpsCourtCaseId = dpsTargetId,
          )

          dpsCourtSentencingServer.stubLinkCase(sourceCourtCaseId = DPS_COURT_CASE_ID, targetCourtCaseId = dpsTargetId)
          courtSentencingOffenderEventsQueue.sendMessage(
            courtCaseLinkingEvent(
              eventType = "OFFENDER_CASES-LINKED",
              combinedCaseId = combinedCaseId,
            ),
          ).also {
            waitForAnyProcessingToComplete()
          }
        }

        @Test
        fun `will update DPS with the changes`() {
          dpsCourtSentencingServer.verify(putRequestedFor(urlPathEqualTo("/legacy/court-case/$DPS_COURT_CASE_ID/link/$dpsTargetId")))
        }

        @Test
        fun `will track a telemetry event for success`() {
          await untilAsserted {
            verify(telemetryClient).trackEvent(
              eq("court-case-synchronisation-link-success"),
              check {
                assertThat(it["offenderNo"]).isEqualTo(OFFENDER_ID_DISPLAY)
                assertThat(it["nomisBookingId"]).isEqualTo(NOMIS_BOOKING_ID.toString())
                assertThat(it["nomisCourtCaseId"]).isEqualTo(NOMIS_COURT_CASE_ID.toString())
                assertThat(it["nomisCombinedCourtCaseId"]).isEqualTo(combinedCaseId.toString())
                assertThat(it["dpsSourceCourtCaseId"]).isEqualTo(DPS_COURT_CASE_ID)
                assertThat(it["dpsTargetCourtCaseId"]).isEqualTo(dpsTargetId)
              },
              isNull(),
            )
          }
        }
      }
    }
  }

  @Nested
  @DisplayName("OFFENDER_CASES-UNLINKED")
  inner class CourtCaseUnlinked {
    val combinedCaseId = 5432L
    val dpsTargetId = "1b0a031e-dc57-4273-bb9f-c45ea3f68584"

    @Nested
    @DisplayName("When court case was unlinked in DPS")
    inner class DPSUpdated {
      @BeforeEach
      fun setUp() {
        courtSentencingOffenderEventsQueue.sendMessage(
          courtCaseLinkingEvent(
            eventType = "OFFENDER_CASES-UNLINKED",
            auditModule = "DPS_SYNCHRONISATION",
            combinedCaseId = combinedCaseId,
          ),
        ).also { waitForAnyProcessingToComplete() }
      }

      @Test
      fun `the event is ignored`() {
        verify(telemetryClient).trackEvent(
          eq("court-case-synchronisation-unlink-skipped"),
          check {
            assertThat(it["offenderNo"]).isEqualTo(OFFENDER_ID_DISPLAY)
            assertThat(it["nomisBookingId"]).isEqualTo(NOMIS_BOOKING_ID.toString())
            assertThat(it["nomisCourtCaseId"]).isEqualTo(NOMIS_COURT_CASE_ID.toString())
            assertThat(it["nomisCombinedCourtCaseId"]).isEqualTo(combinedCaseId.toString())
          },
          isNull(),
        )

        // will not bother getting the court case or the mapping
        courtSentencingNomisApiMockServer.verify(
          0,
          getRequestedFor(urlPathMatching("/prisoners/\\d+/sentencing/court-cases/\\d+")),
        )

        courtSentencingMappingApiMockServer.verify(
          0,
          getRequestedFor(urlPathMatching("/mapping/court-sentencing/court-cases/nomis-court-case-id/\\d+")),
        )
        // will not create a court case in DPS
        dpsCourtSentencingServer.verify(0, putRequestedFor(anyUrl()))
      }
    }

    @Nested
    @DisplayName("When court case was unlinked in NOMIS")
    inner class NomisUpdated {

      @BeforeEach
      fun setUp() {
        courtSentencingNomisApiMockServer.stubGetCourtCase(
          courtCaseId = NOMIS_COURT_CASE_ID,
          bookingId = NOMIS_BOOKING_ID,
          offenderNo = OFFENDER_ID_DISPLAY,
        )
      }

      @Nested
      @DisplayName("When mapping doesn't exist")
      inner class MappingDoesNotExist {
        @BeforeEach
        fun setUp() {
          courtSentencingMappingApiMockServer.stubGetByNomisId(status = NOT_FOUND)
          courtSentencingOffenderEventsQueue.sendMessage(
            courtCaseLinkingEvent(
              eventType = "OFFENDER_CASES-UNLINKED",
              combinedCaseId = combinedCaseId,
            ),
          ).also { waitForAnyProcessingToComplete("court-case-synchronisation-unlink-error", 2) }
        }

        @Test
        fun `telemetry added to track the failure`() {
          verify(telemetryClient, times(2)).trackEvent(
            eq("court-case-synchronisation-unlink-error"),
            check {
              assertThat(it["offenderNo"]).isEqualTo(OFFENDER_ID_DISPLAY)
              assertThat(it["nomisBookingId"]).isEqualTo(NOMIS_BOOKING_ID.toString())
              assertThat(it["nomisCourtCaseId"]).isEqualTo(NOMIS_COURT_CASE_ID.toString())
              assertThat(it["nomisCombinedCourtCaseId"]).isEqualTo(combinedCaseId.toString())
            },
            isNull(),
          )
        }

        @Test
        fun `the event is placed on dead letter queue`() {
          await untilAsserted {
            assertThat(
              courtSentencingOffenderEventsQueue.countAllMessagesOnDLQQueue(),
            ).isEqualTo(1)
          }
        }
      }

      @Nested
      @DisplayName("When mapping exists")
      inner class MappingExists {
        @BeforeEach
        fun setUp() {
          courtSentencingMappingApiMockServer.stubGetByNomisId(
            nomisCourtCaseId = NOMIS_COURT_CASE_ID,
            dpsCourtCaseId = DPS_COURT_CASE_ID,
          )

          courtSentencingMappingApiMockServer.stubGetByNomisId(
            nomisCourtCaseId = combinedCaseId,
            dpsCourtCaseId = dpsTargetId,
          )

          dpsCourtSentencingServer.stubUnlinkCase(sourceCourtCaseId = DPS_COURT_CASE_ID, targetCourtCaseId = dpsTargetId)
          courtSentencingOffenderEventsQueue.sendMessage(
            courtCaseLinkingEvent(
              eventType = "OFFENDER_CASES-UNLINKED",
              combinedCaseId = combinedCaseId,
            ),
          ).also {
            waitForAnyProcessingToComplete()
          }
        }

        @Test
        fun `will update DPS with the changes`() {
          dpsCourtSentencingServer.verify(putRequestedFor(urlPathEqualTo("/legacy/court-case/$DPS_COURT_CASE_ID/unlink/$dpsTargetId")))
        }

        @Test
        fun `will track a telemetry event for success`() {
          verify(telemetryClient).trackEvent(
            eq("court-case-synchronisation-unlink-success"),
            check {
              assertThat(it["offenderNo"]).isEqualTo(OFFENDER_ID_DISPLAY)
              assertThat(it["nomisBookingId"]).isEqualTo(NOMIS_BOOKING_ID.toString())
              assertThat(it["nomisCourtCaseId"]).isEqualTo(NOMIS_COURT_CASE_ID.toString())
              assertThat(it["nomisCombinedCourtCaseId"]).isEqualTo(combinedCaseId.toString())
              assertThat(it["dpsSourceCourtCaseId"]).isEqualTo(DPS_COURT_CASE_ID)
              assertThat(it["dpsTargetCourtCaseId"]).isEqualTo(dpsTargetId)
            },
            isNull(),
          )
        }
      }
    }
  }

  @Nested
  @DisplayName("OFFENDER_CASE_IDENTIFIERS")
  inner class CaseIdentifiersUpdated {
    @Nested
    @DisplayName("When court case was updated in DPS")
    inner class DPSUpdated {
      @BeforeEach
      fun setUp() {
        courtSentencingOffenderEventsQueue.sendMessage(
          caseIdentifiersEvent(
            eventType = "OFFENDER_CASE_IDENTIFIERS-UPDATED",
            auditModule = "DPS_SYNCHRONISATION",
          ),
        ).also { waitForAnyProcessingToComplete() }
      }

      @Test
      fun `the event is ignored`() {
        verify(telemetryClient).trackEvent(
          eq("case-identifiers-synchronisation-skipped"),
          check {
            assertThat(it["nomisIdentifiersNo"]).isEqualTo(NOMIS_CASE_IDENTIFIER)
            assertThat(it["nomisIdentifiersType"]).isEqualTo(NOMIS_CASE_IDENTIFIER_TYPE)
            assertThat(it["nomisCourtCaseId"]).isEqualTo(NOMIS_COURT_CASE_ID.toString())
            assertThat(it["eventType"]).isEqualTo("OFFENDER_CASE_IDENTIFIERS-UPDATED")
          },
          isNull(),
        )

        // will not bother getting the court case or the mapping
        courtSentencingNomisApiMockServer.verify(
          0,
          getRequestedFor(urlPathMatching("/prisoners/\\d+/sentencing/court-cases/\\d+")),
        )

        courtSentencingMappingApiMockServer.verify(
          0,
          getRequestedFor(urlPathMatching("/mapping/court-sentencing/court-cases/nomis-court-case-id/\\d+")),
        )
        // will not attempt to refresh case references in DPS
        dpsCourtSentencingServer.verify(0, postRequestedFor(anyUrl()))
      }
    }

    @Nested
    @DisplayName("When case identifier was updated in NOMIS")
    inner class NomisUpdated {

      @BeforeEach
      fun setUp() {
        courtSentencingNomisApiMockServer.stubGetCourtCaseNoOffenderVersion(
          caseId = NOMIS_COURT_CASE_ID,
          bookingId = NOMIS_BOOKING_ID,
          offenderNo = OFFENDER_ID_DISPLAY,
          caseIndentifiers = listOf(
            CaseIdentifierResponse(
              reference = NOMIS_CASE_IDENTIFIER,
              createDateTime = LocalDateTime.now(),
              type = NOMIS_CASE_IDENTIFIER_TYPE,
            ),
            CaseIdentifierResponse(
              reference = "ref2",
              createDateTime = LocalDateTime.now().plusHours(1),
              type = NOMIS_CASE_IDENTIFIER_TYPE,
            ),
            CaseIdentifierResponse(
              reference = "ref2",
              createDateTime = LocalDateTime.now().plusHours(1),
              type = "NOT_OF_INTEREST_TO_DPS",
            ),
          ),
        )
      }

      @Nested
      @DisplayName("When mapping doesn't exist")
      inner class MappingDoesNotExist {
        @BeforeEach
        fun setUp() {
          courtSentencingMappingApiMockServer.stubGetByNomisId(status = NOT_FOUND)
          courtSentencingOffenderEventsQueue.sendMessage(
            caseIdentifiersEvent(
              eventType = "OFFENDER_CASE_IDENTIFIERS-UPDATED",
            ),
          ).also { waitForAnyProcessingToComplete("case-identifiers-synchronisation-failed", 2) }
        }

        @Test
        fun `telemetry added to track the failure`() {
          verify(telemetryClient, times(2)).trackEvent(
            eq("case-identifiers-synchronisation-failed"),
            check {
              assertThat(it["nomisIdentifiersNo"]).isEqualTo(NOMIS_CASE_IDENTIFIER)
              assertThat(it["nomisIdentifiersType"]).isEqualTo(NOMIS_CASE_IDENTIFIER_TYPE)
              assertThat(it["nomisCourtCaseId"]).isEqualTo(NOMIS_COURT_CASE_ID.toString())
              assertThat(it["eventType"]).isEqualTo("OFFENDER_CASE_IDENTIFIERS-UPDATED")
            },
            isNull(),
          )
        }

        @Test
        fun `the event is placed on dead letter queue`() {
          await untilAsserted {
            assertThat(
              awsSqsCourtSentencingOffenderEventDlqClient.countAllMessagesOnQueue(
                courtSentencingQueueOffenderEventsDlqUrl,
              ).get(),
            ).isEqualTo(1)
          }
        }
      }

      @Nested
      @DisplayName("When mapping doesn't exist")
      inner class MappingDoesNotExistForDeletedEvent {
        @BeforeEach
        fun setUp() {
          courtSentencingMappingApiMockServer.stubGetByNomisId(status = NOT_FOUND)
          courtSentencingOffenderEventsQueue.sendMessage(
            caseIdentifiersEvent(
              eventType = "OFFENDER_CASE_IDENTIFIERS-DELETED",
            ),
          ).also { waitForAnyProcessingToComplete() }
        }

        @Test
        fun `telemetry added to track the skipping of the event`() {
          verify(telemetryClient).trackEvent(
            eq("case-identifiers-synchronisation-skipped"),
            check {
              assertThat(it["nomisIdentifiersNo"]).isEqualTo(NOMIS_CASE_IDENTIFIER)
              assertThat(it["isDelete"]).isEqualTo("true")
              assertThat(it["nomisIdentifiersType"]).isEqualTo(NOMIS_CASE_IDENTIFIER_TYPE)
              assertThat(it["nomisCourtCaseId"]).isEqualTo(NOMIS_COURT_CASE_ID.toString())
              assertThat(it["eventType"]).isEqualTo("OFFENDER_CASE_IDENTIFIERS-DELETED")
            },
            isNull(),
          )
        }

        @Test
        fun `the event is NOT placed on dead letter queue`() {
          assertThat(
            courtSentencingOffenderEventsQueue.countAllMessagesOnDLQQueue(),
          ).isEqualTo(0)
        }
      }

      @Nested
      @DisplayName("When mapping exists")
      inner class MappingExists {
        @BeforeEach
        fun setUp() {
          courtSentencingMappingApiMockServer.stubGetByNomisId(
            nomisCourtCaseId = NOMIS_COURT_CASE_ID,
            dpsCourtCaseId = DPS_COURT_CASE_ID,
          )

          dpsCourtSentencingServer.stubPutCaseIdentifierRefresh(courtCaseId = DPS_COURT_CASE_ID)
          courtSentencingOffenderEventsQueue.sendMessage(
            caseIdentifiersEvent(
              eventType = "OFFENDER_CASE_IDENTIFIERS-UPDATED",
            ),
          ).also {
            waitForAnyProcessingToComplete()
          }
        }

        @Test
        fun `will update DPS with the changes`() {
          dpsCourtSentencingServer.verify(
            1,
            putRequestedFor(urlPathEqualTo("/legacy/court-case/$DPS_COURT_CASE_ID/case-references/refresh"))
              .withRequestBody(matchingJsonPath("caseReferences.size()", equalTo("2")))
              .withRequestBody(
                matchingJsonPath(
                  "caseReferences[0].offenderCaseReference",
                  equalTo(NOMIS_CASE_IDENTIFIER),
                ),
              )
              .withRequestBody(matchingJsonPath("caseReferences[1].offenderCaseReference", equalTo("ref2"))),
          )
        }

        @Test
        fun `will track a telemetry event for success`() {
          verify(telemetryClient).trackEvent(
            eq("case-identifiers-synchronisation-success"),
            check {
              assertThat(it["offenderNo"]).isEqualTo(OFFENDER_ID_DISPLAY)
              assertThat(it["nomisIdentifiersNo"]).isEqualTo(NOMIS_CASE_IDENTIFIER)
              assertThat(it["nomisIdentifiersType"]).isEqualTo(NOMIS_CASE_IDENTIFIER_TYPE)
              assertThat(it["nomisCourtCaseId"]).isEqualTo(NOMIS_COURT_CASE_ID.toString())
              assertThat(it["eventType"]).isEqualTo("OFFENDER_CASE_IDENTIFIERS-UPDATED")
              assertThat(it["dpsCourtCaseId"]).isEqualTo(DPS_COURT_CASE_ID)
            },
            isNull(),
          )
        }
      }

      @Nested
      @DisplayName("Identifier deleted")
      inner class HandlesDeletedEvent {
        @BeforeEach
        fun setUp() {
          courtSentencingMappingApiMockServer.stubGetByNomisId(
            nomisCourtCaseId = NOMIS_COURT_CASE_ID,
            dpsCourtCaseId = DPS_COURT_CASE_ID,
          )

          dpsCourtSentencingServer.stubPutCaseIdentifierRefresh(courtCaseId = DPS_COURT_CASE_ID)
          courtSentencingOffenderEventsQueue.sendMessage(
            caseIdentifiersEvent(
              eventType = "OFFENDER_CASE_IDENTIFIERS-DELETED",
            ),
          ).also {
            waitForAnyProcessingToComplete()
          }
        }

        @Test
        fun `will track a telemetry event for success`() {
          verify(telemetryClient).trackEvent(
            eq("case-identifiers-synchronisation-success"),
            check {
              assertThat(it["offenderNo"]).isEqualTo(OFFENDER_ID_DISPLAY)
              assertThat(it["nomisIdentifiersNo"]).isEqualTo(NOMIS_CASE_IDENTIFIER)
              assertThat(it["nomisIdentifiersType"]).isEqualTo(NOMIS_CASE_IDENTIFIER_TYPE)
              assertThat(it["nomisCourtCaseId"]).isEqualTo(NOMIS_COURT_CASE_ID.toString())
              assertThat(it["eventType"]).isEqualTo("OFFENDER_CASE_IDENTIFIERS-DELETED")
              assertThat(it["dpsCourtCaseId"]).isEqualTo(DPS_COURT_CASE_ID)
            },
            isNull(),
          )
        }
      }

      @Nested
      @DisplayName("When mapping exists")
      inner class HandlesInsertedEvent {
        @BeforeEach
        fun setUp() {
          courtSentencingMappingApiMockServer.stubGetByNomisId(
            nomisCourtCaseId = NOMIS_COURT_CASE_ID,
            dpsCourtCaseId = DPS_COURT_CASE_ID,
          )

          dpsCourtSentencingServer.stubPutCaseIdentifierRefresh(courtCaseId = DPS_COURT_CASE_ID)
          courtSentencingOffenderEventsQueue.sendMessage(
            caseIdentifiersEvent(
              eventType = "OFFENDER_CASE_IDENTIFIERS-INSERTED",
            ),
          ).also {
            waitForAnyProcessingToComplete()
          }
        }

        @Test
        fun `will track a telemetry event for success`() {
          verify(telemetryClient).trackEvent(
            eq("case-identifiers-synchronisation-success"),
            check {
              assertThat(it["offenderNo"]).isEqualTo(OFFENDER_ID_DISPLAY)
              assertThat(it["nomisIdentifiersNo"]).isEqualTo(NOMIS_CASE_IDENTIFIER)
              assertThat(it["nomisIdentifiersType"]).isEqualTo(NOMIS_CASE_IDENTIFIER_TYPE)
              assertThat(it["nomisCourtCaseId"]).isEqualTo(NOMIS_COURT_CASE_ID.toString())
              assertThat(it["eventType"]).isEqualTo("OFFENDER_CASE_IDENTIFIERS-INSERTED")
              assertThat(it["dpsCourtCaseId"]).isEqualTo(DPS_COURT_CASE_ID)
            },
            isNull(),
          )
        }
      }
    }
  }

  @Nested
  @DisplayName("COURT_EVENTS-INSERTED")
  inner class CourtAppearanceInserted {

    @Nested
    @DisplayName("When court appearance was created in DPS")
    inner class DPSCreated {

      @BeforeEach
      fun setUp() {
        courtSentencingNomisApiMockServer.stubGetCourtAppearance(
          courtCaseId = NOMIS_COURT_CASE_ID,
          offenderNo = OFFENDER_ID_DISPLAY,
          courtAppearanceId = NOMIS_COURT_APPEARANCE_ID,
        )
        courtSentencingOffenderEventsQueue.sendMessage(
          courtAppearanceEvent(
            eventType = "COURT_EVENTS-INSERTED",
            auditModule = "DPS_SYNCHRONISATION",
          ),
        ).also { waitForAnyProcessingToComplete() }
      }

      @Test
      fun `the event is ignored`() {
        verify(telemetryClient).trackEvent(
          eq("court-appearance-synchronisation-created-skipped"),
          check {
            assertThat(it["offenderNo"]).isEqualTo("A3864DZ")
            assertThat(it["nomisCourtAppearanceId"]).isEqualTo(NOMIS_COURT_APPEARANCE_ID.toString())
          },
          isNull(),
        )

        courtSentencingMappingApiMockServer.verify(
          0,
          getRequestedFor(urlPathMatching("/mapping/court-sentencing/court-appearances/nomis-court-appearance-id/\\d+")),
        )
        // will not create a court case in DPS
        dpsCourtSentencingServer.verify(0, postRequestedFor(anyUrl()))
      }
    }

    @Nested
    @DisplayName("When court appearance was created in NOMIS")
    inner class NomisCreated {
      @BeforeEach
      fun setUp() {
        courtSentencingNomisApiMockServer.stubGetCourtAppearance(
          courtCaseId = NOMIS_COURT_CASE_ID,
          offenderNo = OFFENDER_ID_DISPLAY,
          courtAppearanceId = NOMIS_COURT_APPEARANCE_ID,
          eventDateTime = LocalDateTime.parse("2020-01-02T09:00:00"),
          courtId = "MDI",
        )
      }

      @Nested
      @DisplayName("When mapping does not exist yet")
      inner class NoMapping {
        @BeforeEach
        fun setUp() {
          courtSentencingMappingApiMockServer.stubGetCourtAppearanceByNomisId(status = NOT_FOUND)
          courtSentencingMappingApiMockServer.stubGetByNomisId(
            nomisCourtCaseId = NOMIS_COURT_CASE_ID,
            dpsCourtCaseId = DPS_COURT_CASE_ID,
          )
          dpsCourtSentencingServer.stubPostCourtAppearanceForCreate(
            courtAppearanceId = UUID.fromString(
              DPS_COURT_APPEARANCE_ID,
            ),
            courtCaseId = DPS_COURT_CASE_ID,
          )
          courtSentencingMappingApiMockServer.stubPostCourtAppearanceMapping()
          courtSentencingOffenderEventsQueue.sendMessage(
            courtAppearanceEvent(
              eventType = "COURT_EVENTS-INSERTED",
            ),
          ).also { waitForAnyProcessingToComplete() }
        }

        @Test
        fun `will create a court appearance in DPS`() {
          dpsCourtSentencingServer.verify(
            postRequestedFor(urlPathEqualTo("/legacy/court-appearance"))
              .withRequestBody(matchingJsonPath("legacyData.nomisOutcomeCode", equalTo("4506")))
              .withRequestBody(matchingJsonPath("legacyData.outcomeConvictionFlag", equalTo("false")))
              .withRequestBody(matchingJsonPath("legacyData.outcomeDispositionCode", equalTo("I")))
              .withRequestBody(matchingJsonPath("legacyData.outcomeDescription", equalTo("Adjournment")))
              .withRequestBody(matchingJsonPath("legacyData.postedDate", not(WireMock.absent())))
              .withRequestBody(matchingJsonPath("legacyData.appearanceTime", equalTo("09:00")))
              .withRequestBody(matchingJsonPath("courtCode", equalTo("MDI")))
              .withRequestBody(matchingJsonPath("courtCaseUuid", equalTo(DPS_COURT_CASE_ID)))
              .withRequestBody(matchingJsonPath("appearanceDate", equalTo("2020-01-02")))
              .withRequestBody(
                matchingJsonPath(
                  "appearanceTypeUuid",
                  equalTo(COURT_APPEARANCE_DPS_APPEARANCE_TYPE_UUID),
                ),
              ),
          )
        }

        @Test
        fun `will create mapping between DPS and NOMIS ids`() {
          courtSentencingMappingApiMockServer.verify(
            postRequestedFor(urlPathEqualTo("/mapping/court-sentencing/court-appearances"))
              .withRequestBody(matchingJsonPath("dpsCourtAppearanceId", equalTo(DPS_COURT_APPEARANCE_ID)))
              .withRequestBody(
                matchingJsonPath(
                  "nomisCourtAppearanceId",
                  equalTo(NOMIS_COURT_APPEARANCE_ID.toString()),
                ),
              )
              .withRequestBody(matchingJsonPath("mappingType", equalTo("NOMIS_CREATED"))),
          )
        }

        @Test
        fun `will track a telemetry event for success`() {
          verify(telemetryClient).trackEvent(
            eq("court-appearance-synchronisation-created-success"),
            check {
              assertThat(it["offenderNo"]).isEqualTo(OFFENDER_ID_DISPLAY)
              assertThat(it["nomisBookingId"]).isEqualTo(NOMIS_BOOKING_ID.toString())
              assertThat(it["nomisCourtCaseId"]).isEqualTo(NOMIS_COURT_CASE_ID.toString())
              assertThat(it["nomisCourtAppearanceId"]).isEqualTo(NOMIS_COURT_APPEARANCE_ID.toString())
              assertThat(it["dpsCourtCaseId"]).isEqualTo(DPS_COURT_CASE_ID)
              assertThat(it["dpsCourtAppearanceId"]).isEqualTo(DPS_COURT_APPEARANCE_ID)
              assertThat(it).doesNotContain(SimpleEntry("mapping", "initial-failure"))
            },
            isNull(),
          )
        }
      }

      @Nested
      @DisplayName("When dps returns an error on create")
      inner class DPSCreateFails {
        @BeforeEach
        fun setUp() {
          courtSentencingMappingApiMockServer.stubGetCourtAppearanceByNomisId(status = NOT_FOUND)
          courtSentencingMappingApiMockServer.stubGetByNomisId(
            nomisCourtCaseId = NOMIS_COURT_CASE_ID,
            dpsCourtCaseId = DPS_COURT_CASE_ID,
          )
          dpsCourtSentencingServer.stubPostCourtAppearanceForCreateError()
          courtSentencingMappingApiMockServer.stubPostCourtAppearanceMapping()
          courtSentencingOffenderEventsQueue.sendMessage(
            courtAppearanceEvent(
              eventType = "COURT_EVENTS-INSERTED",
            ),
          ).also { waitForAnyProcessingToComplete() }
        }

        @Test
        fun `DPS failure is tracked`() {
          verify(telemetryClient).trackEvent(
            eq("court-appearance-synchronisation-created-error"),
            check {
              assertThat(it["offenderNo"]).isEqualTo(OFFENDER_ID_DISPLAY)
              assertThat(it["nomisBookingId"]).isEqualTo(NOMIS_BOOKING_ID.toString())
              assertThat(it["nomisCourtCaseId"]).isEqualTo(NOMIS_COURT_CASE_ID.toString())
              assertThat(it["nomisCourtAppearanceId"]).isEqualTo(NOMIS_COURT_APPEARANCE_ID.toString())
            },
            isNull(),
          )

          // will not create a mapping
          courtSentencingMappingApiMockServer.verify(0, postRequestedFor(anyUrl()))
        }
      }

      @Nested
      @DisplayName("Ignore appearances unrelated to a court case")
      inner class NoAssociatedCourtCase {
        @BeforeEach
        fun setUp() {
          courtSentencingNomisApiMockServer.stubGetCourtAppearance(
            courtCaseId = null,
            offenderNo = OFFENDER_ID_DISPLAY,
            courtAppearanceId = NOMIS_COURT_APPEARANCE_ID,
          )
          courtSentencingOffenderEventsQueue.sendMessage(
            courtAppearanceEvent(
              eventType = "COURT_EVENTS-INSERTED",
              courtCaseId = null,
            ),
          ).also { waitForAnyProcessingToComplete() }
        }

        @Test
        fun `will not retrieve the court appearance from nomis`() {
          courtSentencingNomisApiMockServer.verify(
            0,
            getRequestedFor(anyUrl()),
          )
        }

        @Test
        fun `will track a telemetry event for ignored`() {
          verify(telemetryClient).trackEvent(
            eq("court-appearance-synchronisation-created-ignored"),
            check {
              assertThat(it["offenderNo"]).isEqualTo(OFFENDER_ID_DISPLAY)
              assertThat(it["nomisBookingId"]).isEqualTo(NOMIS_BOOKING_ID.toString())
              assertThat(it["nomisCourtCaseId"]).isNull()
              assertThat(it["nomisCourtAppearanceId"]).isEqualTo(NOMIS_COURT_APPEARANCE_ID.toString())
              assertThat(it["reason"]).contains("appearance not associated with a court case")
              assertThat(it).doesNotContain(SimpleEntry("mapping", "initial-failure"))
            },
            isNull(),
          )
        }
      }

      @Nested
      @DisplayName("Ignore appearances without a court case mapping")
      inner class CourtCaseNotMapped {
        @BeforeEach
        fun setUp() {
          courtSentencingNomisApiMockServer.stubGetCourtAppearance(
            courtCaseId = NOMIS_COURT_CASE_ID,
            offenderNo = OFFENDER_ID_DISPLAY,
            courtAppearanceId = NOMIS_COURT_APPEARANCE_ID,
          )
          courtSentencingMappingApiMockServer.stubGetByNomisId(status = NOT_FOUND)
          courtSentencingOffenderEventsQueue.sendMessage(
            courtAppearanceEvent(
              eventType = "COURT_EVENTS-INSERTED",
            ),
          ).also { waitForAnyProcessingToComplete("court-appearance-synchronisation-created-failed", 2) }
        }

        @Test
        fun `will track a telemetry event for failed`() {
          verify(telemetryClient, times(2)).trackEvent(
            eq("court-appearance-synchronisation-created-failed"),
            check {
              assertThat(it["offenderNo"]).isEqualTo(OFFENDER_ID_DISPLAY)
              assertThat(it["nomisBookingId"]).isEqualTo(NOMIS_BOOKING_ID.toString())
              assertThat(it["nomisCourtCaseId"]).isEqualTo(NOMIS_COURT_CASE_ID.toString())
              assertThat(it["nomisCourtAppearanceId"]).isEqualTo(NOMIS_COURT_APPEARANCE_ID.toString())
              assertThat(it["reason"]).isEqualTo("associated court case is not mapped")
              assertThat(it).doesNotContain(SimpleEntry("mapping", "initial-failure"))
            },
            isNull(),
          )
        }
      }

      @Nested
      @DisplayName("When mapping already exists")
      inner class MappingExists {
        @BeforeEach
        fun setUp() {
          courtSentencingMappingApiMockServer.stubGetCourtAppearanceByNomisId(
            nomisCourtAppearanceId = NOMIS_COURT_APPEARANCE_ID,
            dpsCourtAppearanceId = DPS_COURT_APPEARANCE_ID,
          )
          courtSentencingMappingApiMockServer.stubGetByNomisId(
            nomisCourtCaseId = NOMIS_COURT_CASE_ID,
            dpsCourtCaseId = DPS_COURT_CASE_ID,
            mapping = CourtCaseMappingDto(
              nomisCourtCaseId = NOMIS_COURT_CASE_ID,
              dpsCourtCaseId = DPS_COURT_CASE_ID,
            ),
          )
          courtSentencingOffenderEventsQueue.sendMessage(
            courtAppearanceEvent(
              eventType = "COURT_EVENTS-INSERTED",
              bookingId = NOMIS_BOOKING_ID,
              courtAppearanceId = NOMIS_COURT_APPEARANCE_ID,
              offenderNo = OFFENDER_ID_DISPLAY,
            ),
          ).also { waitForAnyProcessingToComplete() }
        }

        @Test
        fun `the event is ignored`() {
          verify(telemetryClient).trackEvent(
            eq("court-appearance-synchronisation-created-ignored"),
            check {
              assertThat(it["offenderNo"]).isEqualTo("A3864DZ")
              assertThat(it["nomisBookingId"]).isEqualTo(NOMIS_BOOKING_ID.toString())
              assertThat(it["nomisCourtAppearanceId"]).isEqualTo(NOMIS_COURT_APPEARANCE_ID.toString())
              assertThat(it["dpsCourtAppearanceId"]).isEqualTo(DPS_COURT_APPEARANCE_ID)
            },
            isNull(),
          )
          // will not create a court case in DPS
          dpsCourtSentencingServer.verify(0, postRequestedFor(anyUrl()))
        }
      }

      @Nested
      @DisplayName("When mapping POST fails")
      inner class MappingFail {
        @BeforeEach
        fun setUp() {
          courtSentencingMappingApiMockServer.stubGetCourtAppearanceByNomisId(status = NOT_FOUND)
          courtSentencingMappingApiMockServer.stubGetByNomisId(
            nomisCourtCaseId = NOMIS_COURT_CASE_ID,
            dpsCourtCaseId = DPS_COURT_CASE_ID,
          )
          dpsCourtSentencingServer.stubPostCourtAppearanceForCreate(
            courtAppearanceId = UUID.fromString(
              DPS_COURT_APPEARANCE_ID,
            ),
          )
        }

        @Nested
        @DisplayName("Fails once")
        inner class FailsOnce {
          @BeforeEach
          fun setUp() {
            courtSentencingMappingApiMockServer.stubPostCourtAppearanceMappingFailureFollowedBySuccess()
            courtSentencingOffenderEventsQueue.sendMessage(
              courtAppearanceEvent(
                eventType = "COURT_EVENTS-INSERTED",
              ),
            ).also {
              waitForAnyProcessingToComplete("court-appearance-mapping-created-synchronisation-success")
            }
          }

          @Test
          fun `will create a court case in DPS`() {
            dpsCourtSentencingServer.verify(
              postRequestedFor(urlPathEqualTo("/legacy/court-appearance")),
            )
          }

          @Test
          fun `will attempt to create mapping two times and succeed`() {
            courtSentencingMappingApiMockServer.verify(
              WireMock.exactly(2),
              postRequestedFor(urlPathEqualTo("/mapping/court-sentencing/court-appearances"))
                .withRequestBody(matchingJsonPath("dpsCourtAppearanceId", equalTo(DPS_COURT_APPEARANCE_ID)))
                .withRequestBody(
                  matchingJsonPath(
                    "nomisCourtAppearanceId",
                    equalTo(NOMIS_COURT_APPEARANCE_ID.toString()),
                  ),
                )
                .withRequestBody(matchingJsonPath("mappingType", equalTo("NOMIS_CREATED"))),
            )

            assertThat(
              courtSentencingOffenderEventsQueue.hasMessagesOnDLQQueue(),
            ).isFalse
          }

          @Test
          fun `will track a telemetry event for partial success`() {
            verify(telemetryClient).trackEvent(
              eq("court-appearance-synchronisation-created-success"),
              check {
                assertThat(it["offenderNo"]).isEqualTo(OFFENDER_ID_DISPLAY)
                assertThat(it["nomisBookingId"]).isEqualTo(NOMIS_BOOKING_ID.toString())
                assertThat(it["nomisCourtCaseId"]).isEqualTo(NOMIS_COURT_CASE_ID.toString())
                assertThat(it["dpsCourtCaseId"]).isEqualTo(DPS_COURT_CASE_ID)
                assertThat(it["nomisCourtAppearanceId"]).isEqualTo(NOMIS_COURT_APPEARANCE_ID.toString())
                assertThat(it["dpsCourtAppearanceId"]).isEqualTo(DPS_COURT_APPEARANCE_ID)
                assertThat(it["mapping"]).isEqualTo("initial-failure")
              },
              isNull(),
            )

            verify(telemetryClient).trackEvent(
              eq("court-appearance-mapping-created-synchronisation-success"),
              check {
                assertThat(it["offenderNo"]).isEqualTo("A3864DZ")
                assertThat(it["nomisBookingId"]).isEqualTo(NOMIS_BOOKING_ID.toString())
                assertThat(it["nomisCourtAppearanceId"]).isEqualTo(NOMIS_COURT_APPEARANCE_ID.toString())
                assertThat(it["dpsCourtAppearanceId"]).isEqualTo(DPS_COURT_APPEARANCE_ID)
              },
              isNull(),
            )
          }
        }

        @Nested
        @DisplayName("Fails constantly")
        inner class FailsConstantly {
          @BeforeEach
          fun setUp() {
            courtSentencingMappingApiMockServer.stubPostCourtAppearanceMapping(status = HttpStatus.INTERNAL_SERVER_ERROR)
            courtSentencingOffenderEventsQueue.sendMessage(
              courtAppearanceEvent(
                eventType = "COURT_EVENTS-INSERTED",
              ),
            )
            await untilCallTo {
              courtSentencingOffenderEventsQueue.hasMessagesOnDLQQueue()
            } matches { it == true }
          }

          @Test
          fun `will create a court case in DPS`() {
            dpsCourtSentencingServer.verify(
              1,
              postRequestedFor(urlPathEqualTo("/legacy/court-appearance")),
            )
          }

          @Test
          fun `will attempt to create mapping several times and keep failing`() {
            courtSentencingMappingApiMockServer.verify(
              WireMock.exactly(3),
              postRequestedFor(urlPathEqualTo("/mapping/court-sentencing/court-appearances")),
            )
          }

          @Test
          fun `will track a telemetry event for success`() {
            verify(telemetryClient).trackEvent(
              eq("court-appearance-synchronisation-created-success"),
              check {
                assertThat(it["offenderNo"]).isEqualTo("A3864DZ")
                assertThat(it["nomisBookingId"]).isEqualTo(NOMIS_BOOKING_ID.toString())
                assertThat(it["nomisCourtCaseId"]).isEqualTo(NOMIS_COURT_CASE_ID.toString())
                assertThat(it["dpsCourtCaseId"]).isEqualTo(DPS_COURT_CASE_ID)
                assertThat(it["nomisCourtAppearanceId"]).isEqualTo(NOMIS_COURT_APPEARANCE_ID.toString())
                assertThat(it["dpsCourtAppearanceId"]).isEqualTo(DPS_COURT_APPEARANCE_ID)
                assertThat(it["mapping"]).isEqualTo("initial-failure")
              },
              isNull(),
            )
          }
        }
      }
    }

    @Nested
    @DisplayName("When court appearance was created in DPS for a recall")
    inner class DpsCreatedRecallBreachHearing {
      @Nested
      @DisplayName("When happy path")
      inner class HappyPath {
        val chargeIds =
          listOf((101L to "e94eea10-dfd1-43c2-b43c-56e8650f8ae7"), (102L to "e94eea10-dfd1-43c2-b43c-56e8650f8ae7"))

        @BeforeEach
        fun setUp() {
          courtSentencingNomisApiMockServer.stubGetCourtAppearance(
            courtCaseId = NOMIS_COURT_CASE_ID,
            offenderNo = OFFENDER_ID_DISPLAY,
            courtAppearanceId = NOMIS_COURT_APPEARANCE_ID,
            eventDateTime = LocalDateTime.parse("2020-01-02T09:00:00"),
            courtId = "MDI",
            courtEventCharges = listOf(
              courtEventChargeResponse(
                eventId = NOMIS_COURT_APPEARANCE_ID,
                offenderChargeId = chargeIds[0].first,
              ),
              courtEventChargeResponse(
                eventId = NOMIS_COURT_APPEARANCE_ID,
                offenderChargeId = chargeIds[1].first,
              ),
            ),
          )

          courtSentencingMappingApiMockServer.stubGetCourtAppearanceByNomisIdNotFoundFollowedBySuccess(
            nomisCourtAppearanceId = NOMIS_COURT_APPEARANCE_ID,
            dpsCourtAppearanceId = DPS_COURT_APPEARANCE_ID,
          )
          courtSentencingMappingApiMockServer.stubGetByNomisId(
            nomisCourtCaseId = NOMIS_COURT_CASE_ID,
            dpsCourtCaseId = DPS_COURT_CASE_ID,
          )
          dpsCourtSentencingServer.stubPostCourtAppearanceForCreate(
            courtAppearanceId = UUID.fromString(
              DPS_COURT_APPEARANCE_ID,
            ),
            courtCaseId = DPS_COURT_CASE_ID,
          )
          courtSentencingMappingApiMockServer.stubPostCourtAppearanceMapping()

          // court event charge mocking
          chargeIds.forEach {
            courtSentencingNomisApiMockServer.stubGetCourtEventCharge(
              offenderNo = OFFENDER_ID_DISPLAY,
              offenderChargeId = it.first,
              courtAppearanceId = NOMIS_COURT_APPEARANCE_ID,
            )

            dpsCourtSentencingServer.stubPostCourtChargeForCreate(
              courtChargeId = it.second,
              courtCaseId = DPS_COURT_CASE_ID,
              offenderNo = OFFENDER_ID_DISPLAY,
            )
          }
          courtSentencingMappingApiMockServer.stubGetCourtChargeByNomisId(status = NOT_FOUND)
          courtSentencingMappingApiMockServer.stubPostCourtChargeMapping()

          courtSentencingOffenderEventsQueue.sendMessage(

            courtAppearanceEvent(
              eventType = "COURT_EVENTS-INSERTED",
              auditModule = "DPS_SYNCHRONISATION",
              courtAppearanceId = NOMIS_COURT_APPEARANCE_ID,
              isBreachHearing = true,
            ),
          ).also { waitForAnyProcessingToComplete(name = "court-charge-synchronisation-created-success", times = 2) }
        }

        @Test
        fun `will create a court appearance in DPS`() {
          dpsCourtSentencingServer.verify(
            postRequestedFor(urlPathEqualTo("/legacy/court-appearance"))
              .withRequestBody(matchingJsonPath("legacyData.nomisOutcomeCode", equalTo("4506")))
              .withRequestBody(matchingJsonPath("legacyData.outcomeConvictionFlag", equalTo("false")))
              .withRequestBody(matchingJsonPath("legacyData.outcomeDispositionCode", equalTo("I")))
              .withRequestBody(matchingJsonPath("legacyData.outcomeDescription", equalTo("Adjournment")))
              .withRequestBody(matchingJsonPath("legacyData.postedDate", not(WireMock.absent())))
              .withRequestBody(matchingJsonPath("legacyData.appearanceTime", equalTo("09:00")))
              .withRequestBody(matchingJsonPath("courtCode", equalTo("MDI")))
              .withRequestBody(matchingJsonPath("courtCaseUuid", equalTo(DPS_COURT_CASE_ID)))
              .withRequestBody(matchingJsonPath("appearanceDate", equalTo("2020-01-02")))
              .withRequestBody(
                matchingJsonPath(
                  "appearanceTypeUuid",
                  equalTo(COURT_APPEARANCE_DPS_APPEARANCE_TYPE_UUID),
                ),
              ),
          )
        }

        @Test
        fun `will create a court charges and associate with an appearance in DPS`() {
          dpsCourtSentencingServer.verify(
            2,
            postRequestedFor(urlPathEqualTo("/legacy/charge"))
              .withRequestBody(matchingJsonPath("appearanceLifetimeUuid", equalTo(DPS_COURT_APPEARANCE_ID))),
          )
        }

        @Test
        fun `will create mapping between DPS and NOMIS court charge ids`() {
          chargeIds.forEach {
            courtSentencingMappingApiMockServer.verify(
              postRequestedFor(urlPathEqualTo("/mapping/court-sentencing/court-charges"))
                .withRequestBody(matchingJsonPath("dpsCourtChargeId", equalTo(it.second)))
                .withRequestBody(
                  matchingJsonPath(
                    "nomisCourtChargeId",
                    equalTo(it.first.toString()),
                  ),
                ),
            )
          }
        }

        @Test
        fun `will create mapping between DPS and NOMIS appearance ids`() {
          courtSentencingMappingApiMockServer.verify(
            postRequestedFor(urlPathEqualTo("/mapping/court-sentencing/court-appearances"))
              .withRequestBody(matchingJsonPath("dpsCourtAppearanceId", equalTo(DPS_COURT_APPEARANCE_ID)))
              .withRequestBody(
                matchingJsonPath(
                  "nomisCourtAppearanceId",
                  equalTo(NOMIS_COURT_APPEARANCE_ID.toString()),
                ),
              )
              .withRequestBody(matchingJsonPath("mappingType", equalTo("NOMIS_CREATED"))),
          )
        }

        @Test
        fun `will track a telemetry event for court appearance success`() {
          verify(telemetryClient).trackEvent(
            eq("court-appearance-synchronisation-created-success"),
            check {
              assertThat(it["offenderNo"]).isEqualTo(OFFENDER_ID_DISPLAY)
              assertThat(it["isBreachHearing"]).isEqualTo("true")
              assertThat(it["nomisBookingId"]).isEqualTo(NOMIS_BOOKING_ID.toString())
              assertThat(it["nomisCourtCaseId"]).isEqualTo(NOMIS_COURT_CASE_ID.toString())
              assertThat(it["nomisCourtAppearanceId"]).isEqualTo(NOMIS_COURT_APPEARANCE_ID.toString())
              assertThat(it["dpsCourtCaseId"]).isEqualTo(DPS_COURT_CASE_ID)
              assertThat(it["dpsCourtAppearanceId"]).isEqualTo(DPS_COURT_APPEARANCE_ID)
              assertThat(it).doesNotContain(SimpleEntry("mapping", "initial-failure"))
            },
            isNull(),
          )
        }

        @Test
        fun `will track a telemetry event for court charge events success`() {
          chargeIds.forEach { ids ->
            verify(telemetryClient).trackEvent(
              eq("court-charge-synchronisation-created-success"),
              check {
                assertThat(it["offenderNo"]).isEqualTo(OFFENDER_ID_DISPLAY)
                assertThat(it["nomisBookingId"]).isEqualTo(NOMIS_BOOKING_ID.toString())
                assertThat(it["nomisOffenderChargeId"]).isEqualTo(ids.first.toString())
                assertThat(it["nomisCourtAppearanceId"]).isEqualTo(NOMIS_COURT_APPEARANCE_ID.toString())
                assertThat(it["dpsChargeId"]).isEqualTo(ids.second)
                assertThat(it["dpsCourtAppearanceId"]).isEqualTo(DPS_COURT_APPEARANCE_ID)
                assertThat(it["existingDpsCharge"]).isEqualTo("false")
                assertThat(it).doesNotContain(SimpleEntry("mapping", "initial-failure"))
              },
              isNull(),
            )
          }
        }
      }
    }

    @Nested
    @DisplayName("duplicate mapping - two messages received at the same time")
    inner class WhenDuplicate {

      @Test
      internal fun `it will not retry after a 409 (duplicate court appearance written to Sentencing API)`() {
        // in the case of multiple events received at the same time - mapping doesn't exist
        courtSentencingMappingApiMockServer.stubGetCourtAppearanceByNomisId(status = NOT_FOUND)

        courtSentencingMappingApiMockServer.stubGetByNomisId(
          nomisCourtCaseId = NOMIS_COURT_CASE_ID,
          dpsCourtCaseId = DPS_COURT_CASE_ID,
        )

        courtSentencingNomisApiMockServer.stubGetCourtAppearance(
          offenderNo = OFFENDER_ID_DISPLAY,
          courtAppearanceId = NOMIS_COURT_APPEARANCE_ID,
          courtCaseId = NOMIS_COURT_CASE_ID,
        )
        dpsCourtSentencingServer.stubPostCourtAppearanceForCreate(
          courtAppearanceId = UUID.fromString(
            DPS_COURT_APPEARANCE_ID,
          ),
        )

        courtSentencingMappingApiMockServer.stubCourtAppearanceMappingCreateConflict(
          existingDpsCourtAppearanceId = EXISTING_DPS_COURT_APPEARANCE_ID,
          duplicateDpsCourtAppearanceId = DPS_COURT_APPEARANCE_ID,
          nomisCourtAppearanceId = NOMIS_COURT_APPEARANCE_ID,
        )

        courtSentencingOffenderEventsQueue.sendMessage(
          courtAppearanceEvent(
            eventType = "COURT_EVENTS-INSERTED",
            courtAppearanceId = NOMIS_COURT_APPEARANCE_ID,
            bookingId = NOMIS_BOOKING_ID,
            offenderNo = OFFENDER_ID_DISPLAY,
          ),
        ).also { waitForAnyProcessingToComplete("from-nomis-sync-court-appearance-duplicate") }

        // doesn't retry
        dpsCourtSentencingServer.verify(
          1,
          postRequestedFor(urlPathEqualTo("/legacy/court-appearance")),
        )

        verify(telemetryClient).trackEvent(
          org.mockito.kotlin.eq("from-nomis-sync-court-appearance-duplicate"),
          check {
            assertThat(it["migrationId"]).isNull()
            assertThat(it["existingDpsCourtAppearanceId"]).isEqualTo(EXISTING_DPS_COURT_APPEARANCE_ID)
            assertThat(it["duplicateDpsCourtAppearanceId"]).isEqualTo(DPS_COURT_APPEARANCE_ID)
            assertThat(it["existingNomisCourtAppearanceId"]).isEqualTo(NOMIS_COURT_APPEARANCE_ID.toString())
            assertThat(it["duplicateNomisCourtAppearanceId"]).isEqualTo(NOMIS_COURT_APPEARANCE_ID.toString())
          },
          isNull(),
        )
      }
    }
  }

  @Nested
  @DisplayName("COURT_EVENTS-UPDATED")
  inner class CourtAppearanceUpdated {
    @Nested
    @DisplayName("When court appearance was updated in DPS")
    inner class DPSUpdated {
      @BeforeEach
      fun setUp() {
        courtSentencingOffenderEventsQueue.sendMessage(
          courtAppearanceEvent(
            eventType = "COURT_EVENTS-UPDATED",
            auditModule = "DPS_SYNCHRONISATION",
          ),
        ).also { waitForAnyProcessingToComplete() }
      }

      @Test
      fun `the event is ignored`() {
        verify(telemetryClient).trackEvent(
          eq("court-appearance-synchronisation-updated-skipped"),
          check {
            assertThat(it["offenderNo"]).isEqualTo(OFFENDER_ID_DISPLAY)
            assertThat(it["nomisBookingId"]).isEqualTo(NOMIS_BOOKING_ID.toString())
            assertThat(it["nomisCourtAppearanceId"]).isEqualTo(NOMIS_COURT_APPEARANCE_ID.toString())
          },
          isNull(),
        )

        // will not bother getting the court appearance or the mapping
        courtSentencingNomisApiMockServer.verify(
          0,
          getRequestedFor(urlPathMatching("/prisoners/\\d+/sentencing/court-appearances/\\d+")),
        )

        courtSentencingMappingApiMockServer.verify(
          0,
          getRequestedFor(urlPathMatching("/mapping/court-sentencing/court-appearances/nomis-court-appearance-id/\\d+")),
        )
        // will not update a court appearance in DPS
        dpsCourtSentencingServer.verify(0, putRequestedFor(anyUrl()))
      }
    }

    @Nested
    @DisplayName("When court appearance was updated in NOMIS")
    inner class NomisUpdated {

      @BeforeEach
      fun setUp() {
        courtSentencingNomisApiMockServer.stubGetCourtAppearance(
          courtAppearanceId = NOMIS_COURT_APPEARANCE_ID,
          offenderNo = OFFENDER_ID_DISPLAY,
          courtCaseId = NOMIS_COURT_CASE_ID,
          eventDateTime = LocalDateTime.parse("2020-01-02T09:00:00"),
        )
      }

      @Nested
      @DisplayName("When mapping doesn't exist")
      inner class MappingDoesNotExist {
        @BeforeEach
        fun setUp() {
          courtSentencingMappingApiMockServer.stubGetCourtAppearanceByNomisId(status = NOT_FOUND)
          courtSentencingOffenderEventsQueue.sendMessage(
            courtAppearanceEvent(
              eventType = "COURT_EVENTS-UPDATED",
            ),
          ).also { waitForAnyProcessingToComplete("court-appearance-synchronisation-updated-failed", 2) }
        }

        @Test
        fun `telemetry added to track the failure`() {
          verify(telemetryClient, times(2)).trackEvent(
            eq("court-appearance-synchronisation-updated-failed"),
            check {
              assertThat(it["offenderNo"]).isEqualTo(OFFENDER_ID_DISPLAY)
              assertThat(it["nomisBookingId"]).isEqualTo(NOMIS_BOOKING_ID.toString())
              assertThat(it["nomisCourtAppearanceId"]).isEqualTo(NOMIS_COURT_APPEARANCE_ID.toString())
            },
            isNull(),
          )
        }

        @Test
        fun `the event is placed on dead letter queue`() {
          await untilAsserted {
            assertThat(
              courtSentencingOffenderEventsQueue.hasMessagesOnDLQQueue(),
            ).isTrue
          }
        }
      }

      @Nested
      @DisplayName("When mapping exists")
      inner class MappingExists {
        @BeforeEach
        fun setUp() {
          courtSentencingMappingApiMockServer.stubGetCourtAppearanceByNomisId(
            nomisCourtAppearanceId = NOMIS_COURT_APPEARANCE_ID,
            dpsCourtAppearanceId = DPS_COURT_APPEARANCE_ID,
          )

          courtSentencingMappingApiMockServer.stubGetByNomisId(
            nomisCourtCaseId = NOMIS_COURT_CASE_ID,
          )

          dpsCourtSentencingServer.stubPutCourtAppearanceForUpdate(
            courtAppearanceId = UUID.fromString(
              DPS_COURT_APPEARANCE_ID,
            ),
          )
          courtSentencingOffenderEventsQueue.sendMessage(
            courtAppearanceEvent(
              eventType = "COURT_EVENTS-UPDATED",
            ),
          ).also {
            waitForAnyProcessingToComplete()
          }
        }

        @Test
        fun `will update DPS with the changes`() {
          dpsCourtSentencingServer.verify(
            1,
            putRequestedFor(urlPathEqualTo("/legacy/court-appearance/$DPS_COURT_APPEARANCE_ID"))
              .withRequestBody(
                matchingJsonPath(
                  "appearanceTypeUuid",
                  equalTo(COURT_APPEARANCE_DPS_APPEARANCE_TYPE_UUID),
                ),
              )
              .withRequestBody(
                matchingJsonPath(
                  "legacyData.appearanceTime",
                  equalTo("09:00"),
                ),
              )
              .withRequestBody(
                matchingJsonPath(
                  "performedByUser",
                  equalTo("jbell"),
                ),
              ),
          )
        }

        @Test
        fun `will track a telemetry event for success`() {
          verify(telemetryClient).trackEvent(
            eq("court-appearance-synchronisation-updated-success"),
            check {
              assertThat(it["offenderNo"]).isEqualTo(OFFENDER_ID_DISPLAY)
              assertThat(it["nomisBookingId"]).isEqualTo(NOMIS_BOOKING_ID.toString())
              assertThat(it["nomisCourtAppearanceId"]).isEqualTo(NOMIS_COURT_APPEARANCE_ID.toString())
              assertThat(it["dpsCourtAppearanceId"]).isEqualTo(DPS_COURT_APPEARANCE_ID)
            },
            isNull(),
          )
        }
      }

      @Nested
      @DisplayName("Ignore appearances unrelated to a court case")
      inner class NoAssociatedCourtCase {
        @BeforeEach
        fun setUp() {
          courtSentencingOffenderEventsQueue.sendMessage(
            courtAppearanceEvent(
              eventType = "COURT_EVENTS-UPDATED",
              courtCaseId = null,
            ),
          ).also { waitForAnyProcessingToComplete() }
        }

        @Test
        fun `will track a telemetry event for ignored`() {
          verify(telemetryClient).trackEvent(
            eq("court-appearance-synchronisation-updated-ignored"),
            check {
              assertThat(it["offenderNo"]).isEqualTo(OFFENDER_ID_DISPLAY)
              assertThat(it["nomisBookingId"]).isEqualTo(NOMIS_BOOKING_ID.toString())
              assertThat(it["nomisCourtCaseId"]).isNull()
              assertThat(it["nomisCourtAppearanceId"]).isEqualTo(NOMIS_COURT_APPEARANCE_ID.toString())
              assertThat(it["reason"]).contains("appearance not associated with a court case")
              assertThat(it).doesNotContain(SimpleEntry("mapping", "initial-failure"))
            },
            isNull(),
          )
        }
      }

      @Nested
      @DisplayName("appearances without a court case mapping fail")
      inner class CourtCaseNotMapped {
        @BeforeEach
        fun setUp() {
          courtSentencingMappingApiMockServer.stubGetCourtAppearanceByNomisId(
            nomisCourtAppearanceId = NOMIS_COURT_APPEARANCE_ID,
            dpsCourtAppearanceId = DPS_COURT_APPEARANCE_ID,
          )
          courtSentencingNomisApiMockServer.stubGetCourtAppearance(
            courtCaseId = NOMIS_COURT_CASE_ID,
            offenderNo = OFFENDER_ID_DISPLAY,
            courtAppearanceId = NOMIS_COURT_APPEARANCE_ID,
          )
          courtSentencingMappingApiMockServer.stubGetByNomisId(status = NOT_FOUND)
          courtSentencingOffenderEventsQueue.sendMessage(
            courtAppearanceEvent(
              eventType = "COURT_EVENTS-UPDATED",
            ),
          ).also { waitForAnyProcessingToComplete("court-appearance-synchronisation-updated-failed", 2) }
        }

        @Test
        fun `will track a telemetry event for failed`() {
          verify(telemetryClient, times(2)).trackEvent(
            eq("court-appearance-synchronisation-updated-failed"),
            check {
              assertThat(it["offenderNo"]).isEqualTo(OFFENDER_ID_DISPLAY)
              assertThat(it["nomisBookingId"]).isEqualTo(NOMIS_BOOKING_ID.toString())
              assertThat(it["nomisCourtCaseId"]).isEqualTo(NOMIS_COURT_CASE_ID.toString())
              assertThat(it["nomisCourtAppearanceId"]).isEqualTo(NOMIS_COURT_APPEARANCE_ID.toString())
              assertThat(it["reason"]).isEqualTo("associated court case is not mapped")
              assertThat(it).doesNotContain(SimpleEntry("mapping", "initial-failure"))
            },
            isNull(),
          )
        }
      }
    }

    @Nested
    @DisplayName("When breach recall court appearance was updated in DPS")
    inner class DpsUpdatedRecallBreachHearing {

      @BeforeEach
      fun setUp() {
        courtSentencingNomisApiMockServer.stubGetCourtAppearance(
          courtAppearanceId = NOMIS_COURT_APPEARANCE_ID,
          offenderNo = OFFENDER_ID_DISPLAY,
          courtCaseId = NOMIS_COURT_CASE_ID,
          eventDateTime = LocalDateTime.parse("2020-01-02T09:00:00"),
        )
      }

      @Nested
      @DisplayName("When happy path")
      inner class HappyPath {
        @BeforeEach
        fun setUp() {
          courtSentencingMappingApiMockServer.stubGetCourtAppearanceByNomisId(
            nomisCourtAppearanceId = NOMIS_COURT_APPEARANCE_ID,
            dpsCourtAppearanceId = DPS_COURT_APPEARANCE_ID,
          )

          courtSentencingMappingApiMockServer.stubGetByNomisId(
            nomisCourtCaseId = NOMIS_COURT_CASE_ID,
          )

          dpsCourtSentencingServer.stubPutCourtAppearanceForUpdate(
            courtAppearanceId = UUID.fromString(
              DPS_COURT_APPEARANCE_ID,
            ),
          )
          courtSentencingOffenderEventsQueue.sendMessage(
            courtAppearanceEvent(
              eventType = "COURT_EVENTS-UPDATED",
              auditModule = "DPS_SYNCHRONISATION",
              isBreachHearing = true,
            ),
          ).also {
            waitForAnyProcessingToComplete()
          }
        }

        @Test
        fun `will update DPS with the changes`() {
          dpsCourtSentencingServer.verify(
            1,
            putRequestedFor(urlPathEqualTo("/legacy/court-appearance/$DPS_COURT_APPEARANCE_ID"))
              .withRequestBody(
                matchingJsonPath(
                  "appearanceTypeUuid",
                  equalTo(COURT_APPEARANCE_DPS_APPEARANCE_TYPE_UUID),
                ),
              )
              .withRequestBody(
                matchingJsonPath(
                  "legacyData.appearanceTime",
                  equalTo("09:00"),
                ),
              ),
          )
        }

        @Test
        fun `will track a telemetry event for success`() {
          verify(telemetryClient).trackEvent(
            eq("court-appearance-synchronisation-updated-success"),
            check {
              assertThat(it["offenderNo"]).isEqualTo(OFFENDER_ID_DISPLAY)
              assertThat(it["isBreachHearing"]).isEqualTo("true")
              assertThat(it["nomisBookingId"]).isEqualTo(NOMIS_BOOKING_ID.toString())
              assertThat(it["nomisCourtAppearanceId"]).isEqualTo(NOMIS_COURT_APPEARANCE_ID.toString())
              assertThat(it["dpsCourtAppearanceId"]).isEqualTo(DPS_COURT_APPEARANCE_ID)
            },
            isNull(),
          )
        }
      }
    }
  }

  @Nested
  @DisplayName("COURT_EVENTS-DELETED")
  inner class CourtAppearanceDeleted {

    @Nested
    @DisplayName("When court appearance was deleted in NOMIS or DPS")
    inner class NomisOrDPSDeleted {

      @Nested
      @DisplayName("When mapping does not exist")
      inner class NoMapping {
        @BeforeEach
        fun setUp() {
          courtSentencingMappingApiMockServer.stubGetByNomisId(status = NOT_FOUND)
          courtSentencingOffenderEventsQueue.sendMessage(
            courtAppearanceEvent(
              eventType = "COURT_EVENTS-DELETED",
            ),
          ).also { waitForAnyProcessingToComplete() }
        }

        @Test
        fun `the event is ignored`() {
          verify(telemetryClient).trackEvent(
            eq("court-appearance-synchronisation-deleted-ignored"),
            check {
              assertThat(it["offenderNo"]).isEqualTo("A3864DZ")
              assertThat(it["nomisBookingId"]).isEqualTo(NOMIS_BOOKING_ID.toString())
              assertThat(it["nomisCourtAppearanceId"]).isEqualTo(NOMIS_COURT_APPEARANCE_ID.toString())
            },
            isNull(),
          )
          // will not delete a court appearance in DPS
          dpsCourtSentencingServer.verify(0, deleteRequestedFor(anyUrl()))
        }
      }

      @Nested
      @DisplayName("When mapping already exists")
      inner class MappingExists {
        @BeforeEach
        fun setUp() {
          courtSentencingMappingApiMockServer.stubGetCourtAppearanceByNomisId(
            nomisCourtAppearanceId = NOMIS_COURT_APPEARANCE_ID,
            dpsCourtAppearanceId = DPS_COURT_APPEARANCE_ID,
            mapping = CourtAppearanceMappingDto(
              nomisCourtAppearanceId = NOMIS_COURT_APPEARANCE_ID,
              dpsCourtAppearanceId = DPS_COURT_APPEARANCE_ID,
            ),
          )
          courtSentencingMappingApiMockServer.stubDeleteCourtAppearanceMappingByDpsId(dpsCourtAppearanceId = DPS_COURT_APPEARANCE_ID)
          dpsCourtSentencingServer.stubDeleteCourtAppearance(DPS_COURT_APPEARANCE_ID)
          courtSentencingOffenderEventsQueue.sendMessage(
            courtAppearanceEvent(
              eventType = "COURT_EVENTS-DELETED",
              bookingId = NOMIS_BOOKING_ID,
              courtAppearanceId = NOMIS_COURT_APPEARANCE_ID,
              offenderNo = OFFENDER_ID_DISPLAY,
              auditModule = "DPS_SYNCHRONISATION",
            ),
          ).also {
            waitForAnyProcessingToComplete()
          }
        }

        @Test
        fun `will delete a court appearance in DPS`() {
          dpsCourtSentencingServer.verify(
            1,
            deleteRequestedFor(urlPathEqualTo("/legacy/court-appearance/$DPS_COURT_APPEARANCE_ID")),
          )
        }

        @Test
        fun `will delete mapping between DPS and NOMIS ids`() {
          courtSentencingMappingApiMockServer.verify(
            1,
            deleteRequestedFor(urlPathEqualTo("/mapping/court-sentencing/court-appearances/dps-court-appearance-id/$DPS_COURT_APPEARANCE_ID")),
          )
        }

        @Test
        fun `will track a telemetry event for success`() {
          verify(telemetryClient).trackEvent(
            eq("court-appearance-synchronisation-deleted-success"),
            check {
              assertThat(it["offenderNo"]).isEqualTo(OFFENDER_ID_DISPLAY)
              assertThat(it["nomisBookingId"]).isEqualTo(NOMIS_BOOKING_ID.toString())
              assertThat(it["nomisCourtAppearanceId"]).isEqualTo(NOMIS_COURT_APPEARANCE_ID.toString())
              assertThat(it["dpsCourtAppearanceId"]).isEqualTo(DPS_COURT_APPEARANCE_ID)
            },
            isNull(),
          )
        }
      }
    }
  }

  @Nested
  @DisplayName("COURT_EVENT_CHARGES-INSERTED")
  inner class CourtEventChargeInserted {

    @Nested
    @DisplayName("When court event charge was created in DPS")
    inner class DPSCreated {

      @BeforeEach
      fun setUp() {
        courtSentencingOffenderEventsQueue.sendMessage(
          courtEventChargeEvent(
            eventType = "COURT_EVENT_CHARGES-INSERTED",
            auditModule = "DPS_SYNCHRONISATION",
          ),
        ).also { waitForAnyProcessingToComplete() }
      }

      @Test
      fun `the event is ignored`() {
        verify(telemetryClient).trackEvent(
          eq("court-charge-synchronisation-created-skipped"),
          check {
            assertThat(it["offenderNo"]).isEqualTo("A3864DZ")
            assertThat(it["nomisCourtAppearanceId"]).isEqualTo(NOMIS_COURT_APPEARANCE_ID.toString())
            assertThat(it["nomisOffenderChargeId"]).isEqualTo(NOMIS_OFFENDER_CHARGE_ID.toString())
          },
          isNull(),
        )

        courtSentencingMappingApiMockServer.verify(
          0,
          getRequestedFor(urlPathMatching("/mapping/court-sentencing/court-appearances/nomis-court-appearance-id/\\d+")),
        )

        courtSentencingMappingApiMockServer.verify(
          0,
          getRequestedFor(urlPathMatching("/mapping/court-sentencing/court-charges/nomis-court-charge-id/\\d+")),
        )

        // will not call the DPS service
        dpsCourtSentencingServer.verify(0, anyRequestedFor(anyUrl()))
      }
    }

    @Nested
    @DisplayName("When court event charge was created in NOMIS")
    inner class NomisCreated {
      @BeforeEach
      fun setUp() {
        courtSentencingMappingApiMockServer.stubGetCourtAppearanceByNomisId(
          nomisCourtAppearanceId = NOMIS_COURT_APPEARANCE_ID,
          dpsCourtAppearanceId = DPS_COURT_APPEARANCE_ID,
        )
      }

      @Nested
      @DisplayName("When charge mapping does not exist yet")
      inner class NoMapping {
        @BeforeEach
        fun setUp() {
          courtSentencingNomisApiMockServer.stubGetCourtEventCharge(
            offenderNo = OFFENDER_ID_DISPLAY,
            offenderChargeId = NOMIS_OFFENDER_CHARGE_ID,
            courtAppearanceId = NOMIS_COURT_APPEARANCE_ID,
          )

          courtSentencingMappingApiMockServer.stubGetCourtChargeByNomisId(status = NOT_FOUND)

          dpsCourtSentencingServer.stubPostCourtChargeForCreate(
            courtChargeId = DPS_CHARGE_ID,
            courtCaseId = DPS_COURT_CASE_ID,
            offenderNo = OFFENDER_ID_DISPLAY,
          )
          courtSentencingMappingApiMockServer.stubPostCourtChargeMapping()
          courtSentencingOffenderEventsQueue.sendMessage(
            courtEventChargeEvent(
              eventType = "COURT_EVENT_CHARGES-INSERTED",
            ),
          ).also { waitForAnyProcessingToComplete() }
        }

        @Test
        fun `will create a court charge and associate with an appearance in DPS`() {
          dpsCourtSentencingServer.verify(
            postRequestedFor(urlPathEqualTo("/legacy/charge"))
              .withRequestBody(matchingJsonPath("appearanceLifetimeUuid", equalTo(DPS_COURT_APPEARANCE_ID)))
              .withRequestBody(matchingJsonPath("offenceCode", equalTo("RI64006")))
              .withRequestBody(matchingJsonPath("offenceStartDate", equalTo("2024-03-03")))
              .withRequestBody(matchingJsonPath("legacyData.nomisOutcomeCode", equalTo("1002")))
              .withRequestBody(matchingJsonPath("legacyData.outcomeDispositionCode", equalTo("F")))
              .withRequestBody(matchingJsonPath("legacyData.offenceDescription", equalTo("Offender description"))),
          )
        }

        @Test
        fun `will create mapping between DPS and NOMIS ids`() {
          courtSentencingMappingApiMockServer.verify(
            postRequestedFor(urlPathEqualTo("/mapping/court-sentencing/court-charges"))
              .withRequestBody(matchingJsonPath("dpsCourtChargeId", equalTo(DPS_CHARGE_ID)))
              .withRequestBody(
                matchingJsonPath(
                  "nomisCourtChargeId",
                  equalTo(NOMIS_OFFENDER_CHARGE_ID.toString()),
                ),
              )
              .withRequestBody(matchingJsonPath("mappingType", equalTo("NOMIS_CREATED"))),
          )
        }

        @Test
        fun `will track a telemetry event for success`() {
          verify(telemetryClient).trackEvent(
            eq("court-charge-synchronisation-created-success"),
            check {
              assertThat(it["offenderNo"]).isEqualTo(OFFENDER_ID_DISPLAY)
              assertThat(it["nomisBookingId"]).isEqualTo(NOMIS_BOOKING_ID.toString())
              assertThat(it["nomisOffenderChargeId"]).isEqualTo(NOMIS_OFFENDER_CHARGE_ID.toString())
              assertThat(it["nomisCourtAppearanceId"]).isEqualTo(NOMIS_COURT_APPEARANCE_ID.toString())
              assertThat(it["dpsChargeId"]).isEqualTo(DPS_CHARGE_ID)
              assertThat(it["dpsCourtAppearanceId"]).isEqualTo(DPS_COURT_APPEARANCE_ID)
              assertThat(it["existingDpsCharge"]).isEqualTo("false")
              assertThat(it["nomisOutcomeCode"]).isEqualTo("1002")
              assertThat(it).doesNotContain(SimpleEntry("mapping", "initial-failure"))
            },
            isNull(),
          )
        }
      }

      @Nested
      @DisplayName("When dps returns an error on create")
      inner class DPSCreateFailsWhenNoMapping {
        @BeforeEach
        fun setUp() {
          courtSentencingNomisApiMockServer.stubGetCourtEventCharge(
            offenderNo = OFFENDER_ID_DISPLAY,
            offenderChargeId = NOMIS_OFFENDER_CHARGE_ID,
            courtAppearanceId = NOMIS_COURT_APPEARANCE_ID,
          )
          courtSentencingMappingApiMockServer.stubGetCourtChargeByNomisId(status = NOT_FOUND)
          dpsCourtSentencingServer.stubPostCourtChargeForCreateError()
          courtSentencingOffenderEventsQueue.sendMessage(
            courtEventChargeEvent(
              eventType = "COURT_EVENT_CHARGES-INSERTED",
            ),
          ).also {
            waitForAnyProcessingToComplete()
          }
        }

        @Test
        fun `DPS failure is tracked`() {
          verify(telemetryClient).trackEvent(
            eq("court-charge-synchronisation-created-error"),
            check {
              assertThat(it["offenderNo"]).isEqualTo(OFFENDER_ID_DISPLAY)
              assertThat(it["nomisBookingId"]).isEqualTo(NOMIS_BOOKING_ID.toString())
              assertThat(it["nomisOffenderChargeId"]).isEqualTo(NOMIS_OFFENDER_CHARGE_ID.toString())
              assertThat(it["existingDpsCharge"]).isEqualTo("false")
            },
            isNull(),
          )

          // will not create a mapping
          courtSentencingMappingApiMockServer.verify(0, postRequestedFor(anyUrl()))
        }
      }

      @Nested
      @DisplayName("When court charge mapping already exists")
      inner class MappingExists {
        @BeforeEach
        fun setUp() {
          courtSentencingNomisApiMockServer.stubGetCourtEventCharge(
            offenderNo = OFFENDER_ID_DISPLAY,
            offenderChargeId = NOMIS_OFFENDER_CHARGE_ID,
            courtAppearanceId = NOMIS_COURT_APPEARANCE_ID,
          )

          courtSentencingMappingApiMockServer.stubGetCourtChargeByNomisId(
            nomisCourtChargeId = NOMIS_OFFENDER_CHARGE_ID,
            dpsCourtChargeId = DPS_CHARGE_ID,
          )
          dpsCourtSentencingServer.stubPutCourtChargeForAddExistingChargeToAppearance(
            courtChargeId = DPS_CHARGE_ID,
            courtAppearanceId = DPS_COURT_APPEARANCE_ID,
          )

          courtSentencingOffenderEventsQueue.sendMessage(
            courtEventChargeEvent(
              eventType = "COURT_EVENT_CHARGES-INSERTED",
            ),
          ).also { waitForAnyProcessingToComplete() }
        }

        @Test
        fun `the existing offender charge is added to the appearance on DPS rather than created`() {
          dpsCourtSentencingServer.verify(
            putRequestedFor(urlPathEqualTo("/legacy/court-appearance/${DPS_COURT_APPEARANCE_ID}/charge/$DPS_CHARGE_ID"))
              .withRequestBody(matchingJsonPath("offenceStartDate", equalTo("2024-03-03")))
              .withRequestBody(matchingJsonPath("legacyData.nomisOutcomeCode", equalTo("1002")))
              .withRequestBody(matchingJsonPath("legacyData.outcomeDispositionCode", equalTo("F"))),
          )
        }

        @Test
        fun `will not try to create a mapping `() {
          courtSentencingMappingApiMockServer.verify(
            0,
            postRequestedFor(anyUrl()),
          )
        }

        @Test
        fun `will track a telemetry event for success`() {
          verify(telemetryClient).trackEvent(
            eq("court-charge-synchronisation-created-success"),
            check {
              assertThat(it["offenderNo"]).isEqualTo(OFFENDER_ID_DISPLAY)
              assertThat(it["nomisBookingId"]).isEqualTo(NOMIS_BOOKING_ID.toString())
              assertThat(it["nomisOffenderChargeId"]).isEqualTo(NOMIS_OFFENDER_CHARGE_ID.toString())
              assertThat(it["nomisCourtAppearanceId"]).isEqualTo(NOMIS_COURT_APPEARANCE_ID.toString())
              assertThat(it["dpsChargeId"]).isEqualTo(DPS_CHARGE_ID)
              assertThat(it["dpsCourtAppearanceId"]).isEqualTo(DPS_COURT_APPEARANCE_ID)
              assertThat(it["nomisOutcomeCode"]).isEqualTo("1002")
              assertThat(it["existingDpsCharge"]).isEqualTo("true")
              assertThat(it).doesNotContain(SimpleEntry("mapping", "initial-failure"))
            },
            isNull(),
          )
        }
      }

      @Nested
      @DisplayName("When dps returns an error on create with mapping")
      inner class DPSCreateFailsWhenMappingExists {
        @BeforeEach
        fun setUp() {
          courtSentencingNomisApiMockServer.stubGetCourtEventCharge(
            offenderNo = OFFENDER_ID_DISPLAY,
            offenderChargeId = NOMIS_OFFENDER_CHARGE_ID,
            courtAppearanceId = NOMIS_COURT_APPEARANCE_ID,
          )

          courtSentencingMappingApiMockServer.stubGetCourtChargeByNomisId(
            nomisCourtChargeId = NOMIS_OFFENDER_CHARGE_ID,
            dpsCourtChargeId = DPS_CHARGE_ID,
          )
          dpsCourtSentencingServer.stubPutCourtChargeForAddExistingChargeToAppearanceError(
            courtChargeId = DPS_CHARGE_ID,
            courtAppearanceId = DPS_COURT_APPEARANCE_ID,
          )

          courtSentencingOffenderEventsQueue.sendMessage(
            courtEventChargeEvent(
              eventType = "COURT_EVENT_CHARGES-INSERTED",
            ),
          ).also { waitForAnyProcessingToComplete() }
        }

        @Test
        fun `DPS failure is tracked`() {
          verify(telemetryClient).trackEvent(
            eq("court-charge-synchronisation-created-error"),
            check {
              assertThat(it["offenderNo"]).isEqualTo(OFFENDER_ID_DISPLAY)
              assertThat(it["nomisBookingId"]).isEqualTo(NOMIS_BOOKING_ID.toString())
              assertThat(it["nomisOffenderChargeId"]).isEqualTo(NOMIS_OFFENDER_CHARGE_ID.toString())
              assertThat(it["dpsChargeId"]).isEqualTo(DPS_CHARGE_ID)
              assertThat(it["existingDpsCharge"]).isEqualTo("true")
            },
            isNull(),
          )

          // will not create a mapping
          courtSentencingMappingApiMockServer.verify(0, postRequestedFor(anyUrl()))
        }
      }

      @Nested
      @DisplayName("When mapping POST fails")
      inner class MappingFail {
        @BeforeEach
        fun setUp() {
          courtSentencingNomisApiMockServer.stubGetCourtEventCharge(
            offenderNo = OFFENDER_ID_DISPLAY,
            offenderChargeId = NOMIS_OFFENDER_CHARGE_ID,
            courtAppearanceId = NOMIS_COURT_APPEARANCE_ID,
          )

          courtSentencingMappingApiMockServer.stubGetCourtChargeByNomisId(status = NOT_FOUND)

          dpsCourtSentencingServer.stubPostCourtChargeForCreate(
            courtChargeId = DPS_CHARGE_ID,
            courtCaseId = DPS_COURT_CASE_ID,
            offenderNo = OFFENDER_ID_DISPLAY,
          )
        }

        @Nested
        @DisplayName("Fails once")
        inner class FailsOnce {
          @BeforeEach
          fun setUp() {
            courtSentencingMappingApiMockServer.stubPostCourtChargeMappingFailureFollowedBySuccess()
            courtSentencingOffenderEventsQueue.sendMessage(
              courtEventChargeEvent(
                eventType = "COURT_EVENT_CHARGES-INSERTED",
              ),
            ).also { waitForAnyProcessingToComplete("court-charge-mapping-created-synchronisation-success") }
          }

          @Test
          fun `will create a court case in DPS`() {
            dpsCourtSentencingServer.verify(
              postRequestedFor(urlPathEqualTo("/legacy/charge")),
            )
          }

          @Test
          fun `will attempt to create mapping two times and succeed`() {
            courtSentencingMappingApiMockServer.verify(
              WireMock.exactly(2),
              postRequestedFor(urlPathEqualTo("/mapping/court-sentencing/court-charges"))
                .withRequestBody(matchingJsonPath("dpsCourtChargeId", equalTo(DPS_CHARGE_ID)))
                .withRequestBody(
                  matchingJsonPath(
                    "nomisCourtChargeId",
                    equalTo(NOMIS_OFFENDER_CHARGE_ID.toString()),
                  ),
                )
                .withRequestBody(matchingJsonPath("mappingType", equalTo("NOMIS_CREATED"))),
            )

            assertThat(
              courtSentencingOffenderEventsQueue.hasMessagesOnDLQQueue(),
            ).isFalse
          }

          @Test
          fun `will track a telemetry event for partial success`() {
            verify(telemetryClient).trackEvent(
              eq("court-charge-synchronisation-created-success"),
              check {
                assertThat(it["offenderNo"]).isEqualTo(OFFENDER_ID_DISPLAY)
                assertThat(it["nomisBookingId"]).isEqualTo(NOMIS_BOOKING_ID.toString())
                assertThat(it["nomisOffenderChargeId"]).isEqualTo(NOMIS_OFFENDER_CHARGE_ID.toString())
                assertThat(it["nomisCourtAppearanceId"]).isEqualTo(NOMIS_COURT_APPEARANCE_ID.toString())
                assertThat(it["dpsChargeId"]).isEqualTo(DPS_CHARGE_ID)
                assertThat(it["dpsCourtAppearanceId"]).isEqualTo(DPS_COURT_APPEARANCE_ID)
                assertThat(it["mapping"]).isEqualTo("initial-failure")
              },
              isNull(),
            )

            verify(telemetryClient).trackEvent(
              eq("court-charge-mapping-created-synchronisation-success"),
              check {
                assertThat(it["offenderNo"]).isEqualTo("A3864DZ")
                assertThat(it["nomisBookingId"]).isEqualTo(NOMIS_BOOKING_ID.toString())
                assertThat(it["nomisOffenderChargeId"]).isEqualTo(NOMIS_OFFENDER_CHARGE_ID.toString())
                assertThat(it["dpsChargeId"]).isEqualTo(DPS_CHARGE_ID)
              },
              isNull(),
            )
          }
        }

        @Nested
        @DisplayName("Fails constantly")
        inner class FailsConstantly {
          @BeforeEach
          fun setUp() {
            courtSentencingMappingApiMockServer.stubPostCourtChargeMapping(status = HttpStatus.INTERNAL_SERVER_ERROR)
            courtSentencingOffenderEventsQueue.sendMessage(
              courtEventChargeEvent(
                eventType = "COURT_EVENT_CHARGES-INSERTED",
              ),
            )
            await untilCallTo {
              courtSentencingOffenderEventsQueue.hasMessagesOnDLQQueue()
            } matches { it == true }
          }

          @Test
          fun `will create and associate a charge in DPS`() {
            dpsCourtSentencingServer.verify(
              postRequestedFor(urlPathEqualTo("/legacy/charge")),
            )
          }

          @Test
          fun `will attempt to create mapping several times and keep failing`() {
            courtSentencingMappingApiMockServer.verify(
              WireMock.exactly(3),
              postRequestedFor(urlPathEqualTo("/mapping/court-sentencing/court-charges")),
            )
          }

          @Test
          fun `will track a telemetry event for success`() {
            verify(telemetryClient).trackEvent(
              eq("court-charge-synchronisation-created-success"),
              check {
                assertThat(it["offenderNo"]).isEqualTo("A3864DZ")
                assertThat(it["nomisBookingId"]).isEqualTo(NOMIS_BOOKING_ID.toString())
                assertThat(it["nomisOffenderChargeId"]).isEqualTo(NOMIS_OFFENDER_CHARGE_ID.toString())
                assertThat(it["nomisCourtAppearanceId"]).isEqualTo(NOMIS_COURT_APPEARANCE_ID.toString())
                assertThat(it["dpsChargeId"]).isEqualTo(DPS_CHARGE_ID)
                assertThat(it["dpsCourtAppearanceId"]).isEqualTo(DPS_COURT_APPEARANCE_ID)
                assertThat(it["mapping"]).isEqualTo("initial-failure")
              },
              isNull(),
            )
          }
        }
      }
    }

    @Nested
    @DisplayName("duplicate mapping - two messages received at the same time")
    inner class WhenDuplicate {

      @Test
      internal fun `it will not retry after a 409 (duplicate charge written to Sentencing API)`() {
        // in the case of multiple events received at the same time - mapping doesn't exist
        courtSentencingMappingApiMockServer.stubGetCourtChargeByNomisId(status = NOT_FOUND)

        courtSentencingNomisApiMockServer.stubGetCourtEventCharge(
          offenderNo = OFFENDER_ID_DISPLAY,
          offenderChargeId = NOMIS_OFFENDER_CHARGE_ID,
          courtAppearanceId = NOMIS_COURT_APPEARANCE_ID,
        )

        courtSentencingMappingApiMockServer.stubGetCourtAppearanceByNomisId(
          nomisCourtAppearanceId = NOMIS_COURT_APPEARANCE_ID,
          dpsCourtAppearanceId = DPS_COURT_APPEARANCE_ID,
        )

        dpsCourtSentencingServer.stubPostCourtChargeForCreate(
          courtChargeId = DPS_CHARGE_ID,
          courtCaseId = DPS_COURT_CASE_ID,
          offenderNo = OFFENDER_ID_DISPLAY,
        )

        courtSentencingMappingApiMockServer.stubCourtChargeMappingCreateConflict(
          existingDpsCourtChargeId = EXISTING_DPS_CHARGE_ID,
          duplicateDpsCourtChargeId = DPS_CHARGE_ID,
          nomisCourtChargeId = NOMIS_OFFENDER_CHARGE_ID,
        )

        courtSentencingOffenderEventsQueue.sendMessage(
          courtEventChargeEvent(
            eventType = "COURT_EVENT_CHARGES-INSERTED",
          ),
        ).also { waitForAnyProcessingToComplete("from-nomis-sync-charge-duplicate") }
        // doesn't retry
        dpsCourtSentencingServer.verify(
          1,
          postRequestedFor(urlPathEqualTo("/legacy/charge")),
        )

        verify(telemetryClient).trackEvent(
          org.mockito.kotlin.eq("from-nomis-sync-charge-duplicate"),
          check {
            assertThat(it["migrationId"]).isNull()
            assertThat(it["existingDpsCourtChargeId"]).isEqualTo(EXISTING_DPS_CHARGE_ID)
            assertThat(it["duplicateDpsCourtChargeId"]).isEqualTo(DPS_CHARGE_ID)
            assertThat(it["existingNomisCourtChargeId"]).isEqualTo(NOMIS_OFFENDER_CHARGE_ID.toString())
            assertThat(it["duplicateNomisCourtChargeId"]).isEqualTo(NOMIS_OFFENDER_CHARGE_ID.toString())
          },
          isNull(),
        )
      }
    }
  }

  @Nested
  @DisplayName("COURT_EVENT_CHARGES-DELETED")
  inner class CourtEventChargeDeleted {

    @Nested
    @DisplayName("When court charge was deleted in NOMIS")
    inner class NomisDeleted {

      @Nested
      @DisplayName("When charge mapping does not exist")
      inner class NoChargeMapping {
        @BeforeEach
        fun setUp() {
          courtSentencingMappingApiMockServer.stubGetCourtChargeByNomisId(status = NOT_FOUND)
          courtSentencingMappingApiMockServer.stubGetCourtAppearanceByNomisId(
            nomisCourtAppearanceId = NOMIS_COURT_APPEARANCE_ID,
            dpsCourtAppearanceId = DPS_COURT_APPEARANCE_ID,
            mapping = CourtAppearanceMappingDto(
              nomisCourtAppearanceId = NOMIS_COURT_APPEARANCE_ID,
              dpsCourtAppearanceId = DPS_COURT_APPEARANCE_ID,
            ),
          )
          courtSentencingOffenderEventsQueue.sendMessage(
            courtEventChargeEvent(
              eventType = "COURT_EVENT_CHARGES-DELETED",
            ),
          )
          waitForAnyProcessingToComplete()
        }

        @Test
        fun `the event is skipped`() {
          verify(telemetryClient).trackEvent(
            eq("court-charge-synchronisation-deleted-skipped"),
            check {
              assertThat(it["offenderNo"]).isEqualTo("A3864DZ")
              assertThat(it["nomisBookingId"]).isEqualTo(NOMIS_BOOKING_ID.toString())
              assertThat(it["nomisCourtAppearanceId"]).isEqualTo(NOMIS_COURT_APPEARANCE_ID.toString())
              assertThat(it["nomisOffenderChargeId"]).isEqualTo(NOMIS_OFFENDER_CHARGE_ID.toString())
              assertThat(it["reason"]).isEqualTo("charge is not mapped")
            },
            isNull(),
          )
          // will not delete a court charge in DPS
          dpsCourtSentencingServer.verify(0, postRequestedFor(anyUrl()))
        }
      }

      @Nested
      @DisplayName("When appearance mapping does not exist")
      inner class NoAppearanceMapping {
        @BeforeEach
        fun setUp() {
          courtSentencingMappingApiMockServer.stubGetCourtAppearanceByNomisId(status = NOT_FOUND)
          courtSentencingOffenderEventsQueue.sendMessage(
            courtEventChargeEvent(
              eventType = "COURT_EVENT_CHARGES-DELETED",
            ),
          )
          waitForAnyProcessingToComplete()
        }

        @Test
        fun `the skipped event is recorded `() {
          verify(telemetryClient).trackEvent(
            eq("court-charge-synchronisation-deleted-skipped"),
            check {
              assertThat(it["offenderNo"]).isEqualTo("A3864DZ")
              assertThat(it["nomisBookingId"]).isEqualTo(NOMIS_BOOKING_ID.toString())
              assertThat(it["nomisCourtAppearanceId"]).isEqualTo(NOMIS_COURT_APPEARANCE_ID.toString())
              assertThat(it["nomisOffenderChargeId"]).isEqualTo(NOMIS_OFFENDER_CHARGE_ID.toString())
              assertThat(it["reason"]).isEqualTo("court appearance is not mapped")
            },
            isNull(),
          )
          // will not delete a court charge in DPS
          dpsCourtSentencingServer.verify(0, deleteRequestedFor(anyUrl()))
        }
      }

      @Nested
      @DisplayName("When mappings exists")
      inner class MappingExists {
        @BeforeEach
        fun setUp() {
          courtSentencingMappingApiMockServer.stubGetCourtAppearanceByNomisId(
            nomisCourtAppearanceId = NOMIS_COURT_APPEARANCE_ID,
            dpsCourtAppearanceId = DPS_COURT_APPEARANCE_ID,
            mapping = CourtAppearanceMappingDto(
              nomisCourtAppearanceId = NOMIS_COURT_APPEARANCE_ID,
              dpsCourtAppearanceId = DPS_COURT_APPEARANCE_ID,
            ),
          )

          courtSentencingMappingApiMockServer.stubGetCourtChargeByNomisId(
            nomisCourtChargeId = NOMIS_OFFENDER_CHARGE_ID,
            dpsCourtChargeId = DPS_CHARGE_ID,
          )

          dpsCourtSentencingServer.stubRemoveCourtCharge(
            courtAppearanceId = DPS_COURT_APPEARANCE_ID,
            chargeId = DPS_CHARGE_ID,
          )

          courtSentencingMappingApiMockServer.stubDeleteCourtChargeMapping(NOMIS_OFFENDER_CHARGE_ID)

          courtSentencingNomisApiMockServer.stubGetOffenderCharge(status = NOT_FOUND)
          courtSentencingOffenderEventsQueue.sendMessage(
            courtEventChargeEvent(
              eventType = "COURT_EVENT_CHARGES-DELETED",
            ),
          ).also { waitForAnyProcessingToComplete("court-charge-synchronisation-deleted-success") }
        }

        @Test
        fun `will delete a court charge in DPS`() {
          dpsCourtSentencingServer.verify(
            1,
            deleteRequestedFor(urlPathEqualTo("/legacy/court-appearance/$DPS_COURT_APPEARANCE_ID/charge/$DPS_CHARGE_ID")),
          )
        }

        @Test
        fun `will remove the mapping if nomis charge has been deleted`() {
          courtSentencingMappingApiMockServer.verify(
            1,
            deleteRequestedFor(urlPathEqualTo("/mapping/court-sentencing/court-charges/nomis-court-charge-id/$NOMIS_OFFENDER_CHARGE_ID")),
          )
        }

        @Test
        fun `will track a telemetry event for success`() {
          verify(telemetryClient).trackEvent(
            eq("court-charge-synchronisation-deleted-success"),
            check {
              assertThat(it["offenderNo"]).isEqualTo(OFFENDER_ID_DISPLAY)
              assertThat(it["nomisBookingId"]).isEqualTo(NOMIS_BOOKING_ID.toString())
              assertThat(it["nomisCourtAppearanceId"]).isEqualTo(NOMIS_COURT_APPEARANCE_ID.toString())
              assertThat(it["dpsCourtAppearanceId"]).isEqualTo(DPS_COURT_APPEARANCE_ID)
              assertThat(it["dpsChargeId"]).isEqualTo(DPS_CHARGE_ID)
              assertThat(it["nomisOffenderChargeId"]).isEqualTo(NOMIS_OFFENDER_CHARGE_ID.toString())
            },
            isNull(),
          )
        }
      }

      @Nested
      @DisplayName("When mappings exists - DPS failure")
      inner class MappingExistsDpsFailure {
        @BeforeEach
        fun setUp() {
          courtSentencingMappingApiMockServer.stubGetCourtAppearanceByNomisId(
            nomisCourtAppearanceId = NOMIS_COURT_APPEARANCE_ID,
            dpsCourtAppearanceId = DPS_COURT_APPEARANCE_ID,
            mapping = CourtAppearanceMappingDto(
              nomisCourtAppearanceId = NOMIS_COURT_APPEARANCE_ID,
              dpsCourtAppearanceId = DPS_COURT_APPEARANCE_ID,
            ),
          )

          courtSentencingMappingApiMockServer.stubGetCourtChargeByNomisId(
            nomisCourtChargeId = NOMIS_OFFENDER_CHARGE_ID,
            dpsCourtChargeId = DPS_CHARGE_ID,
          )

          dpsCourtSentencingServer.stubRemoveCourtChargeError(
            courtAppearanceId = DPS_COURT_APPEARANCE_ID,
            chargeId = DPS_CHARGE_ID,
          )

          courtSentencingNomisApiMockServer.stubGetOffenderCharge(status = NOT_FOUND)
          courtSentencingOffenderEventsQueue.sendMessage(
            courtEventChargeEvent(
              eventType = "COURT_EVENT_CHARGES-DELETED",
            ),
          ).also { waitForAnyProcessingToComplete("court-charge-synchronisation-deleted-error") }
        }

        @Test
        fun `will track a telemetry event for error`() {
          verify(telemetryClient).trackEvent(
            eq("court-charge-synchronisation-deleted-error"),
            check {
              assertThat(it["offenderNo"]).isEqualTo(OFFENDER_ID_DISPLAY)
              assertThat(it["nomisBookingId"]).isEqualTo(NOMIS_BOOKING_ID.toString())
              assertThat(it["nomisCourtAppearanceId"]).isEqualTo(NOMIS_COURT_APPEARANCE_ID.toString())
              assertThat(it["dpsCourtAppearanceId"]).isEqualTo(DPS_COURT_APPEARANCE_ID)
              assertThat(it["dpsChargeId"]).isEqualTo(DPS_CHARGE_ID)
              assertThat(it["nomisOffenderChargeId"]).isEqualTo(NOMIS_OFFENDER_CHARGE_ID.toString())
            },
            isNull(),
          )
        }
      }

      @Nested
      @DisplayName("When mappings exists, nomis has not deleted the offender charge")
      inner class MappingExistsNomisHasNotDeletedCharge {
        @BeforeEach
        fun setUp() {
          courtSentencingMappingApiMockServer.stubGetCourtAppearanceByNomisId(
            nomisCourtAppearanceId = NOMIS_COURT_APPEARANCE_ID,
            dpsCourtAppearanceId = DPS_COURT_APPEARANCE_ID,
            mapping = CourtAppearanceMappingDto(
              nomisCourtAppearanceId = NOMIS_COURT_APPEARANCE_ID,
              dpsCourtAppearanceId = DPS_COURT_APPEARANCE_ID,
            ),
          )

          courtSentencingMappingApiMockServer.stubGetCourtChargeByNomisId(
            nomisCourtChargeId = NOMIS_OFFENDER_CHARGE_ID,
            dpsCourtChargeId = DPS_CHARGE_ID,
          )

          dpsCourtSentencingServer.stubRemoveCourtCharge(
            courtAppearanceId = DPS_COURT_APPEARANCE_ID,
            chargeId = DPS_CHARGE_ID,
          )

          courtSentencingNomisApiMockServer.stubGetOffenderCharge(
            offenderNo = OFFENDER_ID_DISPLAY,
            offenderChargeId = NOMIS_OFFENDER_CHARGE_ID,
          )

          courtSentencingOffenderEventsQueue.sendMessage(

            courtEventChargeEvent(
              eventType = "COURT_EVENT_CHARGES-DELETED",
            ),
          ).also { waitForAnyProcessingToComplete() }
        }

        @Test
        fun `will not delete the charge mapping if nomis charge has not been deleted`() {
          courtSentencingMappingApiMockServer.verify(
            0,
            deleteRequestedFor(urlPathEqualTo("/court-charges/nomis-court-charge-id/$NOMIS_OFFENDER_CHARGE_ID")),
          )
        }
      }
    }
  }

  @Nested
  @DisplayName("COURT_EVENT_CHARGES-UPDATED")
  inner class CourtEventChargeUpdated {
    @Nested
    @DisplayName("When court charge was updated in DPS")
    inner class DPSUpdated {
      @BeforeEach
      fun setUp() {
        courtSentencingOffenderEventsQueue.sendMessage(
          courtEventChargeEvent(
            eventType = "COURT_EVENT_CHARGES-UPDATED",
            auditModule = "DPS_SYNCHRONISATION",
          ),
        ).also { waitForAnyProcessingToComplete() }
      }

      @Test
      fun `the event is ignored`() {
        verify(telemetryClient).trackEvent(
          eq("court-charge-synchronisation-updated-skipped"),
          check {
            assertThat(it["offenderNo"]).isEqualTo(OFFENDER_ID_DISPLAY)
            assertThat(it["nomisBookingId"]).isEqualTo(NOMIS_BOOKING_ID.toString())
            assertThat(it["nomisOffenderChargeId"]).isEqualTo(NOMIS_OFFENDER_CHARGE_ID.toString())
          },
          isNull(),
        )

        // will not bother getting the court charge or the mapping
        courtSentencingNomisApiMockServer.verify(
          0,
          getRequestedFor(urlPathMatching("/prisoners/\\d+/sentencing/offender-charges/\\d+")),
        )

        courtSentencingMappingApiMockServer.verify(
          0,
          getRequestedFor(urlPathMatching("/mapping/court-sentencing/court-charges/nomis-court-charge-id/\\d+")),
        )
        // will not update a court charge in DPS
        dpsCourtSentencingServer.verify(0, putRequestedFor(anyUrl()))
      }
    }

    @Nested
    @DisplayName("When offender charge was updated in NOMIS")
    inner class NomisUpdated {

      @BeforeEach
      fun setUp() {
        courtSentencingNomisApiMockServer.stubGetCourtEventCharge(
          offenderChargeId = NOMIS_OFFENDER_CHARGE_ID,
          courtAppearanceId = NOMIS_COURT_APPEARANCE_ID,
          offenderNo = OFFENDER_ID_DISPLAY,
        )
      }

      @Nested
      @DisplayName("When charge mapping doesn't exist")
      inner class MappingDoesNotExist {
        @BeforeEach
        fun setUp() {
          courtSentencingMappingApiMockServer.stubGetCourtAppearanceByNomisId(
            nomisCourtAppearanceId = NOMIS_COURT_APPEARANCE_ID,
            dpsCourtAppearanceId = DPS_COURT_APPEARANCE_ID,
          )
          courtSentencingMappingApiMockServer.stubGetCourtChargeByNomisId(status = NOT_FOUND)
          courtSentencingOffenderEventsQueue.sendMessage(
            courtEventChargeEvent(
              eventType = "COURT_EVENT_CHARGES-UPDATED",
            ),
          ).also { waitForAnyProcessingToComplete("court-charge-synchronisation-updated-failed", 2) }
        }

        @Test
        fun `telemetry added to track the failure`() {
          verify(telemetryClient, Mockito.atLeastOnce()).trackEvent(
            eq("court-charge-synchronisation-updated-failed"),
            check {
              assertThat(it["offenderNo"]).isEqualTo(OFFENDER_ID_DISPLAY)
              assertThat(it["nomisBookingId"]).isEqualTo(NOMIS_BOOKING_ID.toString())
              assertThat(it["reason"]).isEqualTo("charge is not mapped")
              assertThat(it["nomisOffenderChargeId"]).isEqualTo(NOMIS_OFFENDER_CHARGE_ID.toString())
            },
            isNull(),
          )
        }

        @Test
        fun `the event is placed on dead letter queue`() {
          await untilAsserted {
            assertThat(
              courtSentencingOffenderEventsQueue.hasMessagesOnDLQQueue(),
            ).isTrue
          }
        }
      }

      @Nested
      @DisplayName("When appearance mapping doesn't exist")
      inner class AppearanceMappingDoesNotExist {
        @BeforeEach
        fun setUp() {
          courtSentencingMappingApiMockServer.stubGetCourtChargeByNomisId(
            nomisCourtChargeId = NOMIS_OFFENDER_CHARGE_ID,
            dpsCourtChargeId = DPS_CHARGE_ID,
          )
          courtSentencingMappingApiMockServer.stubGetCourtAppearanceByNomisId(status = NOT_FOUND)
          courtSentencingOffenderEventsQueue.sendMessage(
            courtEventChargeEvent(
              eventType = "COURT_EVENT_CHARGES-UPDATED",
            ),
          ).also { waitForAnyProcessingToComplete("court-charge-synchronisation-updated-failed", 2) }
        }

        @Test
        fun `telemetry added to track the failure`() {
          verify(telemetryClient, Mockito.atLeastOnce()).trackEvent(
            eq("court-charge-synchronisation-updated-failed"),
            check {
              assertThat(it["offenderNo"]).isEqualTo(OFFENDER_ID_DISPLAY)
              assertThat(it["nomisBookingId"]).isEqualTo(NOMIS_BOOKING_ID.toString())
              assertThat(it["reason"]).isEqualTo("associated court appearance is not mapped")
              assertThat(it["nomisOffenderChargeId"]).isEqualTo(NOMIS_OFFENDER_CHARGE_ID.toString())
            },
            isNull(),
          )
        }

        @Test
        fun `the event is placed on dead letter queue`() {
          await untilAsserted {
            assertThat(
              courtSentencingOffenderEventsQueue.hasMessagesOnDLQQueue(),
            ).isTrue
          }
        }
      }

      @Nested
      @DisplayName("When mapping exists")
      inner class MappingExists {
        @BeforeEach
        fun setUp() {
          courtSentencingMappingApiMockServer.stubGetCourtChargeByNomisId(
            nomisCourtChargeId = NOMIS_OFFENDER_CHARGE_ID,
            dpsCourtChargeId = DPS_CHARGE_ID,
          )

          courtSentencingMappingApiMockServer.stubGetCourtAppearanceByNomisId(
            nomisCourtAppearanceId = NOMIS_COURT_APPEARANCE_ID,
            dpsCourtAppearanceId = DPS_COURT_APPEARANCE_ID,
          )

          dpsCourtSentencingServer.stubPutAppearanceChargeForUpdate(
            chargeId = DPS_CHARGE_ID,
            appearanceId = DPS_COURT_APPEARANCE_ID,
          )
          courtSentencingOffenderEventsQueue.sendMessage(
            courtEventChargeEvent(
              eventType = "COURT_EVENT_CHARGES-UPDATED",
            ),
          ).also { waitForAnyProcessingToComplete() }
        }

        @Test
        fun `will update DPS with the changes`() {
          dpsCourtSentencingServer.verify(
            1,
            putRequestedFor(urlPathEqualTo("/legacy/charge/$DPS_CHARGE_ID/appearance/$DPS_COURT_APPEARANCE_ID"))
              .withRequestBody(matchingJsonPath("legacyData.outcomeDispositionCode", equalTo("F")))
              .withRequestBody(matchingJsonPath("legacyData.outcomeDescription", equalTo("Imprisonment")))
              .withRequestBody(matchingJsonPath("offenceCode", equalTo("RI64006"))),
          )
        }

        @Test
        fun `will track a telemetry event for success`() {
          verify(telemetryClient).trackEvent(
            eq("court-charge-synchronisation-updated-success"),
            check {
              assertThat(it["offenderNo"]).isEqualTo(OFFENDER_ID_DISPLAY)
              assertThat(it["nomisBookingId"]).isEqualTo(NOMIS_BOOKING_ID.toString())
              assertThat(it["nomisOffenderChargeId"]).isEqualTo(NOMIS_OFFENDER_CHARGE_ID.toString())
              assertThat(it["dpsChargeId"]).isEqualTo(DPS_CHARGE_ID)
              assertThat(it["nomisOutcomeCode"]).isEqualTo("1002")
              assertThat(it["offenceCode"]).isEqualTo("RI64006")
            },
            isNull(),
          )
        }
      }

      @Nested
      @DisplayName("When error from dps updating court event charge")
      inner class FailureFromDps {
        @BeforeEach
        fun setUp() {
          courtSentencingMappingApiMockServer.stubGetCourtChargeByNomisId(
            nomisCourtChargeId = NOMIS_OFFENDER_CHARGE_ID,
            dpsCourtChargeId = DPS_CHARGE_ID,
          )

          courtSentencingMappingApiMockServer.stubGetCourtAppearanceByNomisId(
            nomisCourtAppearanceId = NOMIS_COURT_APPEARANCE_ID,
            dpsCourtAppearanceId = DPS_COURT_APPEARANCE_ID,
          )

          dpsCourtSentencingServer.stubPutAppearanceChargeForUpdateError(
            chargeId = DPS_CHARGE_ID,
            appearanceId = DPS_COURT_APPEARANCE_ID,
          )
          courtSentencingOffenderEventsQueue.sendMessage(
            courtEventChargeEvent(
              eventType = "COURT_EVENT_CHARGES-UPDATED",
            ),
          ).also { waitForAnyProcessingToComplete() }
        }

        @Test
        fun `will track a telemetry event for error`() {
          await untilAsserted {
            verify(telemetryClient).trackEvent(
              eq("court-charge-synchronisation-updated-error"),
              check {
                assertThat(it["offenderNo"]).isEqualTo(OFFENDER_ID_DISPLAY)
                assertThat(it["nomisBookingId"]).isEqualTo(NOMIS_BOOKING_ID.toString())
                assertThat(it["nomisOffenderChargeId"]).isEqualTo(NOMIS_OFFENDER_CHARGE_ID.toString())
                assertThat(it["dpsChargeId"]).isEqualTo(DPS_CHARGE_ID)
              },
              isNull(),
            )
          }
        }
      }
    }

    @Nested
    @DisplayName("When court event charge was updated in DPS")
    inner class DPSCreated {

      @BeforeEach
      fun setUp() {
        courtSentencingOffenderEventsQueue.sendMessage(
          courtEventChargeEvent(
            eventType = "COURT_EVENT_CHARGES-UPDATED",
            auditModule = "DPS_SYNCHRONISATION",
          ),
        ).also { waitForAnyProcessingToComplete() }
      }

      @Test
      fun `the event is ignored`() {
        verify(telemetryClient).trackEvent(
          eq("court-charge-synchronisation-updated-skipped"),
          check {
            assertThat(it["offenderNo"]).isEqualTo("A3864DZ")
            assertThat(it["nomisCourtAppearanceId"]).isEqualTo(NOMIS_COURT_APPEARANCE_ID.toString())
            assertThat(it["nomisOffenderChargeId"]).isEqualTo(NOMIS_OFFENDER_CHARGE_ID.toString())
          },
          isNull(),
        )

        courtSentencingMappingApiMockServer.verify(
          0,
          getRequestedFor(urlPathMatching("/mapping/court-sentencing/court-appearances/nomis-court-appearance-id/\\d+")),
        )

        courtSentencingMappingApiMockServer.verify(
          0,
          getRequestedFor(urlPathMatching("/mapping/court-sentencing/court-charges/nomis-court-charge-id/\\d+")),
        )

        // will not call the DPS service
        dpsCourtSentencingServer.verify(0, anyRequestedFor(anyUrl()))
      }
    }
  }

  @Nested
  @DisplayName("COURT_EVENT_CHARGES-LINKED")
  inner class CourtEventChargeLinked {
    val combinedCaseId = 65432L
    val caseId = 65430L

    @Nested
    @DisplayName("When court charge was linked in DPS")
    inner class DPSUpdated {
      @BeforeEach
      fun setUp() {
        courtSentencingOffenderEventsQueue.sendMessage(
          courtEventChargeLinkingEvent(
            eventType = "COURT_EVENT_CHARGES-LINKED",
            auditModule = "DPS_SYNCHRONISATION",
            combinedCaseId = combinedCaseId,
            caseId = caseId,
            eventId = NOMIS_COURT_APPEARANCE_ID,
            chargeId = NOMIS_OFFENDER_CHARGE_ID,
          ),
        ).also { waitForAnyProcessingToComplete() }
      }

      @Test
      fun `the event is ignored`() {
        verify(telemetryClient).trackEvent(
          eq("court-charge-synchronisation-link-skipped"),
          check {
            assertThat(it["offenderNo"]).isEqualTo(OFFENDER_ID_DISPLAY)
            assertThat(it["nomisBookingId"]).isEqualTo(NOMIS_BOOKING_ID.toString())
            assertThat(it["nomisOffenderChargeId"]).isEqualTo(NOMIS_OFFENDER_CHARGE_ID.toString())
            assertThat(it["nomisCombinedCourtCaseId"]).isEqualTo(combinedCaseId.toString())
            assertThat(it["nomisCourtCaseId"]).isEqualTo(caseId.toString())
          },
          isNull(),
        )
        // will not update a court charge in DPS
        dpsCourtSentencingServer.verify(0, putRequestedFor(anyUrl()))
      }
    }

    @Nested
    @DisplayName("When offender charge was linked to a linked case in NOMIS")
    inner class NomisUpdated {

      @Nested
      @DisplayName("When charge mapping doesn't exist")
      inner class MappingDoesNotExist {
        @BeforeEach
        fun setUp() {
          courtSentencingMappingApiMockServer.stubGetCourtAppearanceByNomisId(
            nomisCourtAppearanceId = NOMIS_COURT_APPEARANCE_ID,
            dpsCourtAppearanceId = DPS_COURT_APPEARANCE_ID,
          )
          courtSentencingMappingApiMockServer.stubGetCourtChargeByNomisId(status = NOT_FOUND)
          courtSentencingOffenderEventsQueue.sendMessage(
            courtEventChargeLinkingEvent(
              eventType = "COURT_EVENT_CHARGES-LINKED",
              combinedCaseId = combinedCaseId,
              caseId = caseId,
              eventId = NOMIS_COURT_APPEARANCE_ID,
              chargeId = NOMIS_OFFENDER_CHARGE_ID,
            ),
          ).also { waitForAnyProcessingToComplete("court-charge-synchronisation-link-error", 2) }
        }

        @Test
        fun `telemetry added to track the failure`() {
          verify(telemetryClient, Mockito.atLeastOnce()).trackEvent(
            eq("court-charge-synchronisation-link-error"),
            check {
              assertThat(it["offenderNo"]).isEqualTo(OFFENDER_ID_DISPLAY)
              assertThat(it["nomisBookingId"]).isEqualTo(NOMIS_BOOKING_ID.toString())
              assertThat(it["nomisCombinedCourtCaseId"]).isEqualTo(combinedCaseId.toString())
              assertThat(it["nomisCourtCaseId"]).isEqualTo(caseId.toString())
              assertThat(it["nomisOffenderChargeId"]).isEqualTo(NOMIS_OFFENDER_CHARGE_ID.toString())
              assertThat(it["nomisCourtAppearanceId"]).isEqualTo(NOMIS_COURT_APPEARANCE_ID.toString())
            },
            isNull(),
          )
        }

        @Test
        fun `the event is placed on dead letter queue`() {
          await untilAsserted {
            assertThat(
              awsSqsCourtSentencingOffenderEventDlqClient.countAllMessagesOnQueue(
                courtSentencingQueueOffenderEventsDlqUrl,
              ).get(),
            ).isEqualTo(1)
          }
        }
      }

      @Nested
      @DisplayName("When case mapping doesn't exist")
      inner class CaseMappingDoesNotExist {
        @BeforeEach
        fun setUp() {
          courtSentencingMappingApiMockServer.stubGetByNomisId(status = NOT_FOUND)
          courtSentencingOffenderEventsQueue.sendMessage(
            courtEventChargeLinkingEvent(
              eventType = "COURT_EVENT_CHARGES-LINKED",
              combinedCaseId = combinedCaseId,
              caseId = caseId,
              eventId = NOMIS_COURT_APPEARANCE_ID,
              chargeId = NOMIS_OFFENDER_CHARGE_ID,
            ),
          ).also { waitForAnyProcessingToComplete("court-charge-synchronisation-link-error", 2) }
        }

        @Test
        fun `telemetry added to track the failure`() {
          verify(telemetryClient, Mockito.atLeastOnce()).trackEvent(
            eq("court-charge-synchronisation-link-error"),
            check {
              assertThat(it["offenderNo"]).isEqualTo(OFFENDER_ID_DISPLAY)
              assertThat(it["nomisBookingId"]).isEqualTo(NOMIS_BOOKING_ID.toString())
              assertThat(it["nomisCombinedCourtCaseId"]).isEqualTo(combinedCaseId.toString())
              assertThat(it["nomisCourtCaseId"]).isEqualTo(caseId.toString())
              assertThat(it["nomisOffenderChargeId"]).isEqualTo(NOMIS_OFFENDER_CHARGE_ID.toString())
              assertThat(it["nomisCourtAppearanceId"]).isEqualTo(NOMIS_COURT_APPEARANCE_ID.toString())
            },
            isNull(),
          )
        }

        @Test
        fun `the event is placed on dead letter queue`() {
          await untilAsserted {
            assertThat(
              courtSentencingOffenderEventsQueue.countAllMessagesOnDLQQueue(),
            ).isEqualTo(1)
          }
        }
      }

      @Nested
      @DisplayName("When appearance mapping doesn't exist")
      inner class AppearanceMappingDoesNotExist {
        @BeforeEach
        fun setUp() {
          courtSentencingMappingApiMockServer.stubGetByNomisId(nomisCourtCaseId = caseId, dpsCourtCaseId = DPS_COURT_CASE_ID)
          courtSentencingMappingApiMockServer.stubGetCourtAppearanceByNomisId(NOT_FOUND)
          courtSentencingOffenderEventsQueue.sendMessage(
            courtEventChargeLinkingEvent(
              eventType = "COURT_EVENT_CHARGES-LINKED",
              combinedCaseId = combinedCaseId,
              caseId = caseId,
              eventId = NOMIS_COURT_APPEARANCE_ID,
              chargeId = NOMIS_OFFENDER_CHARGE_ID,
            ),
          ).also { waitForAnyProcessingToComplete("court-charge-synchronisation-link-error", 2) }
        }

        @Test
        fun `telemetry added to track the failure`() {
          verify(telemetryClient, Mockito.atLeastOnce()).trackEvent(
            eq("court-charge-synchronisation-link-error"),
            check {
              assertThat(it["offenderNo"]).isEqualTo(OFFENDER_ID_DISPLAY)
              assertThat(it["nomisBookingId"]).isEqualTo(NOMIS_BOOKING_ID.toString())
              assertThat(it["nomisCombinedCourtCaseId"]).isEqualTo(combinedCaseId.toString())
              assertThat(it["nomisCourtCaseId"]).isEqualTo(caseId.toString())
              assertThat(it["nomisOffenderChargeId"]).isEqualTo(NOMIS_OFFENDER_CHARGE_ID.toString())
              assertThat(it["nomisCourtAppearanceId"]).isEqualTo(NOMIS_COURT_APPEARANCE_ID.toString())
            },
            isNull(),
          )
        }

        @Test
        fun `the event is placed on dead letter queue`() {
          await untilAsserted {
            assertThat(
              courtSentencingOffenderEventsQueue.countAllMessagesOnDLQQueue(),
            ).isEqualTo(1)
          }
        }
      }

      @Nested
      @DisplayName("When charge mapping doesn't exist")
      inner class ChargeMappingDoesNotExist {
        @BeforeEach
        fun setUp() {
          courtSentencingMappingApiMockServer.stubGetByNomisId(nomisCourtCaseId = caseId, dpsCourtCaseId = DPS_COURT_CASE_ID)
          courtSentencingMappingApiMockServer.stubGetCourtAppearanceByNomisId(nomisCourtAppearanceId = NOMIS_COURT_APPEARANCE_ID, dpsCourtAppearanceId = DPS_COURT_APPEARANCE_ID)
          courtSentencingMappingApiMockServer.stubGetCourtChargeByNomisIdNotFound(nomisCourtChargeId = NOMIS_OFFENDER_CHARGE_ID)
          courtSentencingOffenderEventsQueue.sendMessage(
            courtEventChargeLinkingEvent(
              eventType = "COURT_EVENT_CHARGES-LINKED",
              combinedCaseId = combinedCaseId,
              caseId = caseId,
              eventId = NOMIS_COURT_APPEARANCE_ID,
              chargeId = NOMIS_OFFENDER_CHARGE_ID,
            ),
          ).also { waitForAnyProcessingToComplete("court-charge-synchronisation-link-error", 2) }
        }

        @Test
        fun `telemetry added to track the failure`() {
          verify(telemetryClient, Mockito.atLeastOnce()).trackEvent(
            eq("court-charge-synchronisation-link-error"),
            check {
              assertThat(it["offenderNo"]).isEqualTo(OFFENDER_ID_DISPLAY)
              assertThat(it["nomisBookingId"]).isEqualTo(NOMIS_BOOKING_ID.toString())
              assertThat(it["nomisCombinedCourtCaseId"]).isEqualTo(combinedCaseId.toString())
              assertThat(it["nomisCourtCaseId"]).isEqualTo(caseId.toString())
              assertThat(it["nomisOffenderChargeId"]).isEqualTo(NOMIS_OFFENDER_CHARGE_ID.toString())
              assertThat(it["nomisCourtAppearanceId"]).isEqualTo(NOMIS_COURT_APPEARANCE_ID.toString())
            },
            isNull(),
          )
        }

        @Test
        fun `the event is placed on dead letter queue`() {
          await untilAsserted {
            assertThat(
              courtSentencingOffenderEventsQueue.countAllMessagesOnDLQQueue(),
            ).isEqualTo(1)
          }
        }
      }

      @Nested
      @DisplayName("When all mapping exists")
      inner class MappingExists {
        @BeforeEach
        fun setUp() {
          courtSentencingMappingApiMockServer.stubGetByNomisId(nomisCourtCaseId = caseId, dpsCourtCaseId = DPS_COURT_CASE_ID)
          courtSentencingMappingApiMockServer.stubGetCourtAppearanceByNomisId(nomisCourtAppearanceId = NOMIS_COURT_APPEARANCE_ID, dpsCourtAppearanceId = DPS_COURT_APPEARANCE_ID)
          courtSentencingMappingApiMockServer.stubGetCourtChargeByNomisId(nomisCourtChargeId = NOMIS_OFFENDER_CHARGE_ID, dpsCourtChargeId = DPS_CHARGE_ID)
          dpsCourtSentencingServer.stubLinkChargeToCase(
            courtAppearanceId = DPS_COURT_APPEARANCE_ID,
            chargeId = DPS_CHARGE_ID,
          )
          courtSentencingOffenderEventsQueue.sendMessage(
            courtEventChargeLinkingEvent(
              eventType = "COURT_EVENT_CHARGES-LINKED",
              combinedCaseId = combinedCaseId,
              caseId = caseId,
              eventId = NOMIS_COURT_APPEARANCE_ID,
              chargeId = NOMIS_OFFENDER_CHARGE_ID,
              eventDatetime = "2025-01-01T10:00",
            ),
          ).also { waitForAnyProcessingToComplete() }
        }

        @Test
        fun `will update DPS with the changes`() {
          dpsCourtSentencingServer.verify(
            putRequestedFor(urlPathEqualTo("/legacy/court-appearance/$DPS_COURT_APPEARANCE_ID/charge/$DPS_CHARGE_ID/link"))
              .withRequestBody(matchingJsonPath("sourceCourtCaseUuid", equalTo(DPS_COURT_CASE_ID)))
              .withRequestBody(matchingJsonPath("linkedDate", equalTo("2025-01-01"))),
          )
        }

        @Test
        fun `will track a telemetry event for success`() {
          verify(telemetryClient).trackEvent(
            eq("court-charge-synchronisation-link-success"),
            check {
              assertThat(it["offenderNo"]).isEqualTo(OFFENDER_ID_DISPLAY)
              assertThat(it["nomisBookingId"]).isEqualTo(NOMIS_BOOKING_ID.toString())
              assertThat(it["nomisCombinedCourtCaseId"]).isEqualTo(combinedCaseId.toString())
              assertThat(it["nomisCourtCaseId"]).isEqualTo(caseId.toString())
              assertThat(it["nomisOffenderChargeId"]).isEqualTo(NOMIS_OFFENDER_CHARGE_ID.toString())
              assertThat(it["nomisCourtAppearanceId"]).isEqualTo(NOMIS_COURT_APPEARANCE_ID.toString())
              assertThat(it["dpsCourtAppearanceId"]).isEqualTo(DPS_COURT_APPEARANCE_ID)
              assertThat(it["dpsSourceCourtCaseId"]).isEqualTo(DPS_COURT_CASE_ID)
              assertThat(it["dpsCourtChargeId"]).isEqualTo(DPS_CHARGE_ID)
            },
            isNull(),
          )
        }
      }
    }
  }

  @Nested
  @DisplayName("OFFENDER_CHARGES-UPDATED")
  inner class OffenderChargeUpdated {
    @Nested
    @DisplayName("When offender charge was updated in DPS")
    inner class DPSUpdated {
      @BeforeEach
      fun setUp() {
        courtSentencingOffenderEventsQueue.sendMessage(
          courtEventChargeEvent(
            eventType = "OFFENDER_CHARGES-UPDATED",
            auditModule = "DPS_SYNCHRONISATION",
          ),
        ).also { waitForAnyProcessingToComplete() }
      }

      @Test
      fun `the event is ignored`() {
        verify(telemetryClient).trackEvent(
          eq("court-charge-synchronisation-updated-skipped"),
          check {
            assertThat(it["offenderNo"]).isEqualTo(OFFENDER_ID_DISPLAY)
            assertThat(it["nomisBookingId"]).isEqualTo(NOMIS_BOOKING_ID.toString())
            assertThat(it["nomisOffenderChargeId"]).isEqualTo(NOMIS_OFFENDER_CHARGE_ID.toString())
          },
          isNull(),
        )

        // will not bother getting the court charge or the mapping
        courtSentencingNomisApiMockServer.verify(
          0,
          getRequestedFor(urlPathMatching("/prisoners/\\d+/sentencing/offender-charges/\\d+")),
        )

        courtSentencingMappingApiMockServer.verify(
          0,
          getRequestedFor(urlPathMatching("/mapping/court-sentencing/court-charges/nomis-court-charge-id/\\d+")),
        )
        // will not update a court charge in DPS
        dpsCourtSentencingServer.verify(0, putRequestedFor(anyUrl()))
      }
    }

    @Nested
    @DisplayName("When offender charge was updated in NOMIS without an offence code change")
    inner class NomisUpdatedNoOffenceChange {
      @BeforeEach
      fun setUp() {
        courtSentencingOffenderEventsQueue.sendMessage(
          offenderChargeEvent(
            eventType = "OFFENDER_CHARGES-UPDATED",
            auditModule = "DPS_SYNCHRONISATION",
            offenceCodeChange = "false",
          ),
        ).also { waitForAnyProcessingToComplete() }
      }

      @Test
      fun `the event is ignored`() {
        verify(telemetryClient).trackEvent(
          eq("court-charge-synchronisation-updated-skipped"),
          check {
            assertThat(it["offenderNo"]).isEqualTo(OFFENDER_ID_DISPLAY)
            assertThat(it["reason"]).isEqualTo("OFFENDER_CHARGES-UPDATED change is not an offence code change")
            assertThat(it["nomisBookingId"]).isEqualTo(NOMIS_BOOKING_ID.toString())
            assertThat(it["nomisOffenderChargeId"]).isEqualTo(NOMIS_OFFENDER_CHARGE_ID.toString())
          },
          isNull(),
        )

        // will not bother getting the court charge or the mapping
        courtSentencingNomisApiMockServer.verify(
          0,
          getRequestedFor(anyUrl()),
        )

        courtSentencingMappingApiMockServer.verify(
          0,
          getRequestedFor(anyUrl()),
        )
        // will not update a court charge in DPS
        dpsCourtSentencingServer.verify(0, putRequestedFor(anyUrl()))
      }
    }

    @Nested
    @DisplayName("When offender charge was updated in NOMIS with an offence code change")
    inner class NomisUpdated {

      @BeforeEach
      fun setUp() {
        courtSentencingNomisApiMockServer.stubGetOffenderCharge(
          offenderChargeId = NOMIS_OFFENDER_CHARGE_ID,
          offenderNo = OFFENDER_ID_DISPLAY,
          offence = OffenceResponse(
            offenceCode = "TTT4006",
            statuteCode = "TI64",
            description = "Offender description",
          ),
        )
      }

      @Nested
      @DisplayName("When charge mapping doesn't exist")
      inner class MappingDoesNotExist {
        @BeforeEach
        fun setUp() {
          courtSentencingMappingApiMockServer.stubGetCourtAppearanceByNomisId(
            nomisCourtAppearanceId = NOMIS_COURT_APPEARANCE_ID,
            dpsCourtAppearanceId = DPS_COURT_APPEARANCE_ID,
          )
          courtSentencingMappingApiMockServer.stubGetCourtChargeByNomisId(status = NOT_FOUND)
          courtSentencingOffenderEventsQueue.sendMessage(
            offenderChargeEvent(
              eventType = "OFFENDER_CHARGES-UPDATED",
              offenceCodeChange = "true",
            ),
          ).also { waitForAnyProcessingToComplete("court-charge-synchronisation-updated-failed", 2) }
        }

        @Test
        fun `telemetry added to track the failure`() {
          verify(telemetryClient, Mockito.atLeastOnce()).trackEvent(
            eq("court-charge-synchronisation-updated-failed"),
            check {
              assertThat(it["offenderNo"]).isEqualTo(OFFENDER_ID_DISPLAY)
              assertThat(it["nomisBookingId"]).isEqualTo(NOMIS_BOOKING_ID.toString())
              assertThat(it["reason"]).isEqualTo("charge is not mapped")
              assertThat(it["offenceCode"]).isEqualTo("TTT4006")
              assertThat(it["nomisOffenderChargeId"]).isEqualTo(NOMIS_OFFENDER_CHARGE_ID.toString())
            },
            isNull(),
          )
        }

        @Test
        fun `the event is placed on dead letter queue`() {
          await untilAsserted {
            assertThat(
              courtSentencingOffenderEventsQueue.hasMessagesOnDLQQueue(),
            ).isTrue
          }
        }
      }

      @Nested
      @DisplayName("When mapping exists")
      inner class MappingExists {
        @BeforeEach
        fun setUp() {
          courtSentencingMappingApiMockServer.stubGetCourtChargeByNomisId(
            nomisCourtChargeId = NOMIS_OFFENDER_CHARGE_ID,
            dpsCourtChargeId = DPS_CHARGE_ID,
          )

          dpsCourtSentencingServer.stubPutChargeForUpdate(
            chargeId = UUID.fromString(DPS_CHARGE_ID),
          )
          courtSentencingOffenderEventsQueue.sendMessage(
            offenderChargeEvent(
              eventType = "OFFENDER_CHARGES-UPDATED",
              offenceCodeChange = "true",
            ),
          ).also { waitForAnyProcessingToComplete() }
        }

        @Test
        fun `will update DPS with the changes`() {
          dpsCourtSentencingServer.verify(
            1,
            putRequestedFor(urlPathEqualTo("/legacy/charge/$DPS_CHARGE_ID")).withRequestBody(
              matchingJsonPath(
                "offenceCode",
                equalTo("TTT4006"),
              ),
            ),
          )
        }

        @Test
        fun `will track a telemetry event for success`() {
          verify(telemetryClient).trackEvent(
            eq("court-charge-synchronisation-updated-success"),
            check {
              assertThat(it["offenderNo"]).isEqualTo(OFFENDER_ID_DISPLAY)
              assertThat(it["nomisBookingId"]).isEqualTo(NOMIS_BOOKING_ID.toString())
              assertThat(it["nomisOffenderChargeId"]).isEqualTo(NOMIS_OFFENDER_CHARGE_ID.toString())
              assertThat(it["dpsChargeId"]).isEqualTo(DPS_CHARGE_ID)
              assertThat(it["offenceCode"]).isEqualTo("TTT4006")
            },
            isNull(),
          )
        }
      }
    }
  }

  @Nested
  @DisplayName("prison-offender-events.prisoner.merged")
  inner class PrisonerMerged {
    val offenderNumberRetained = "A1234KT"
    val offenderNumberRemoved = "A1000KT"

    @Nested
    inner class HappyPathWhenNoCasesChangedByMerge {
      @BeforeEach
      fun setUp() {
        courtSentencingNomisApiMockServer.stubGetCourtCasesChangedByMerge(offenderNo = offenderNumberRetained, courtCasesCreated = emptyList(), courtCasesDeactivated = emptyList())
        dpsCourtSentencingServer.stubUpdateCourtCasePostMerge(
          mergeResponse = dpsMergeCreateResponse(
            courtCases = emptyList(),
            charges = emptyList(),
            courtAppearances = emptyList(),
            sentences = emptyList(),
            sentenceTerms = emptyList(),
          ),
          retainedOffender = offenderNumberRetained,
        )

        courtSentencingOffenderEventsQueue.sendMessage(
          mergeDomainEvent(
            bookingId = 1234,
            offenderNo = offenderNumberRetained,
            removedOffenderNo = offenderNumberRemoved,
          ),
        ).also { waitForAnyProcessingToComplete() }
      }

      @Test
      fun `will callback to NOMIS to find the cases changed by the merge`() {
        courtSentencingNomisApiMockServer.verify(
          getRequestedFor(urlPathEqualTo("/prisoners/$offenderNumberRetained/sentencing/court-cases/post-merge")),
        )
      }

      @Test
      fun `will call DPS merge endpoint even with no cases changed`() {
        dpsCourtSentencingServer.verify(1, postRequestedFor(anyUrl()))
      }

      @Test
      fun `will not call mapping service to synchronise any case mappings`() {
        courtSentencingMappingApiMockServer.verify(0, postRequestedFor(anyUrl()))
      }

      @Test
      fun `will track telemetry for the merge`() {
        verify(telemetryClient).trackEvent(
          eq("from-nomis-synch-court-case-merge"),
          check {
            assertThat(it["offenderNo"]).isEqualTo(offenderNumberRetained)
            assertThat(it["removedOffenderNo"]).isEqualTo(offenderNumberRemoved)
            assertThat(it["courtCasesCreatedCount"]).isEqualTo("0")
            assertThat(it["courtCasesDeactivatedCount"]).isEqualTo("0")
          },
          isNull(),
        )
      }
    }

    @Nested
    inner class HappyPathWhenCasesCreatedAndDeactivatedByMerge {
      val dpsCourtCaseIdFor10001 = "3051ba19-d125-4f5f-8feb-3cf26802f488"
      val dpsCourtCaseIdFor10002 = "ef38466a-1886-477f-8e72-2e059670f5f8"
      val dpsSentenceIdForSequence1 = "0087ee53-0529-423a-a14d-f340ede43922"
      val dpsSentenceIdForSequence2 = "7b70c22c-a05b-4c78-8da3-c0d01f99400b"
      val dpsSentenceIdForSequence3 = "84430291-a381-4c99-acdb-102299324f2a"
      val dpsSentenceIdForSequence4 = "c0ba2706-6a89-4e64-ae84-a434472ca2ad"
      val dpsSentenceTermIdForSequence1Term1 = "4487ee53-0529-423a-a14d-f340ede43922"
      val dpsSentenceTermIdForSequence1Term2 = "2b70c22c-a05b-4c78-8da3-c0d01f99400b"
      val dpsSentenceTermIdForSequence2Term1 = "23430291-a381-4c99-acdb-102299324f2a"
      val dpsCourtAppearanceFor401 = "b8beca56-f155-4d35-82af-a727d16fb171"
      val dpsCourtAppearanceFor402 = "5c615d4e-891c-44c5-b3eb-349e8dd13da5"
      val dpsChargeIdFor501 = "b8a59d39-9cea-4fcd-a55b-9b53f07b089f"
      val dpsChargeIdFor502 = "50998ebf-bbbe-4bb8-971e-764fab595f80"
      val dpsChargeIdFor503 = "a580cc27-22c5-4d7d-ac16-e637e781d984"
      val dpsChargeIdFor504 = "4f21fb87-45b2-4a38-9ad8-bf2b40291adc"

      @BeforeEach
      fun setUp() {
        courtSentencingNomisApiMockServer.stubGetCourtCasesChangedByMerge(
          offenderNo = offenderNumberRetained,
          courtCasesCreated = listOf(
            courtCaseResponse().copy(
              id = 20001,
              courtEvents = listOf(
                courtEventResponse(eventId = 402).copy(
                  courtEventCharges = listOf(
                    courtEventChargeResponse(eventId = 402, offenderChargeId = 503),
                    courtEventChargeResponse(eventId = 402, offenderChargeId = 504),
                  ),
                ),
              ),
              sentences = listOf(
                sentenceResponse(bookingId = 301, sentenceSequence = 1, eventId = NOMIS_COURT_APPEARANCE_ID),
                sentenceResponse(bookingId = 301, sentenceSequence = 2, eventId = NOMIS_COURT_APPEARANCE_ID),
              ),
            ),
            courtCaseResponse().copy(
              id = 20002,
              courtEvents = listOf(
                courtEventResponse(eventId = 401).copy(
                  courtEventCharges = listOf(
                    courtEventChargeResponse(eventId = 401, offenderChargeId = 501),
                    courtEventChargeResponse(eventId = 401, offenderChargeId = 502),
                  ),
                ),
              ),
              sentences = listOf(
                sentenceResponse(bookingId = 301, sentenceSequence = 3, eventId = NOMIS_COURT_APPEARANCE_ID),
                sentenceResponse(bookingId = 301, sentenceSequence = 4, eventId = NOMIS_COURT_APPEARANCE_ID),
              ),
            ),
          ),
          courtCasesDeactivated = listOf(
            courtCaseResponse().copy(
              id = 10001,
              caseStatus = CodeDescription("I", "Inactive"),
              courtEvents = listOf(
                courtEventResponse(eventId = 201).copy(
                  courtEventCharges = listOf(
                    courtEventChargeResponse(eventId = 201, offenderChargeId = 301),
                    courtEventChargeResponse(eventId = 201, offenderChargeId = 302),
                  ),
                ),
              ),
              sentences = listOf(
                sentenceResponse(bookingId = 201, sentenceSequence = 1, eventId = NOMIS_COURT_APPEARANCE_ID).copy(offenderCharges = listOf(offenderChargeResponse(301))),
                sentenceResponse(bookingId = 201, sentenceSequence = 2, eventId = NOMIS_COURT_APPEARANCE_ID).copy(offenderCharges = listOf(offenderChargeResponse(302))),
              ),
            ),
            courtCaseResponse().copy(
              id = 10002,
              courtEvents = listOf(
                courtEventResponse(eventId = 202).copy(
                  courtEventCharges = listOf(
                    courtEventChargeResponse(eventId = 202, offenderChargeId = 303),
                    courtEventChargeResponse(eventId = 202, offenderChargeId = 304),
                  ),
                ),
              ),
              sentences = listOf(
                sentenceResponse(bookingId = 201, sentenceSequence = 3, eventId = NOMIS_COURT_APPEARANCE_ID).copy(offenderCharges = listOf(offenderChargeResponse(303))),
                sentenceResponse(bookingId = 201, sentenceSequence = 4, eventId = NOMIS_COURT_APPEARANCE_ID).copy(offenderCharges = listOf(offenderChargeResponse(304))),
              ),
            ),
          ),
        )
        courtSentencingMappingApiMockServer.stubGetCasesByNomisIds(
          listOf(
            CourtCaseMappingDto(
              nomisCourtCaseId = 10001,
              dpsCourtCaseId = dpsCourtCaseIdFor10001,
            ),
            CourtCaseMappingDto(
              nomisCourtCaseId = 10002,
              dpsCourtCaseId = dpsCourtCaseIdFor10002,
            ),
          ),
        )
        courtSentencingMappingApiMockServer.stubGetSentencesByNomisIds(
          listOf(
            SentenceMappingDto(
              nomisBookingId = 201,
              nomisSentenceSequence = 1,
              dpsSentenceId = dpsSentenceIdForSequence1,
            ),
            SentenceMappingDto(
              nomisBookingId = 201,
              nomisSentenceSequence = 2,
              dpsSentenceId = dpsSentenceIdForSequence2,
            ),
            SentenceMappingDto(
              nomisBookingId = 201,
              nomisSentenceSequence = 3,
              dpsSentenceId = dpsSentenceIdForSequence3,
            ),
            SentenceMappingDto(
              nomisBookingId = 201,
              nomisSentenceSequence = 4,
              dpsSentenceId = dpsSentenceIdForSequence4,
            ),
          ),
        )

        courtSentencingMappingApiMockServer.stubGetSentenceTermByNomisId(
          nomisBookingId = 201,
          nomisSentenceSequence = 1,
          dpsTermId = dpsSentenceTermIdForSequence1Term1,
        )
        courtSentencingMappingApiMockServer.stubGetSentenceTermByNomisId(
          nomisBookingId = 201,
          nomisSentenceSequence = 1,
          dpsTermId = dpsSentenceTermIdForSequence1Term2,
        )
        courtSentencingMappingApiMockServer.stubGetSentenceTermByNomisId(
          nomisBookingId = 201,
          nomisSentenceSequence = 2,
          dpsTermId = dpsSentenceTermIdForSequence2Term1,
        )
        courtSentencingMappingApiMockServer.stubGetCourtChargeByNomisId(
          nomisCourtChargeId = 301,
        )
        courtSentencingMappingApiMockServer.stubGetCourtChargeByNomisId(
          nomisCourtChargeId = 302,
        )
        courtSentencingMappingApiMockServer.stubGetCourtChargeByNomisId(
          nomisCourtChargeId = 303,
        )
        courtSentencingMappingApiMockServer.stubGetCourtChargeByNomisId(
          nomisCourtChargeId = 304,
        )

        dpsCourtSentencingServer.stubUpdateCourtCasePostMerge(
          mergeResponse = dpsMergeCreateResponse(
            courtCases = listOf(
              dpsCourtCaseIdFor10001 to 10001,
              dpsCourtCaseIdFor10002 to 10002,
            ),
            charges = listOf(
              dpsChargeIdFor501 to 501,
              dpsChargeIdFor502 to 502,
              dpsChargeIdFor503 to 503,
              dpsChargeIdFor504 to 504,
            ),
            courtAppearances = listOf(
              dpsCourtAppearanceFor401 to 401,
              dpsCourtAppearanceFor402 to 402,
            ),
            sentences = listOf(
              dpsSentenceIdForSequence1 to MergeSentenceId(offenderBookingId = 301, sequence = 1),
              dpsSentenceIdForSequence2 to MergeSentenceId(offenderBookingId = 301, sequence = 2),
              dpsSentenceIdForSequence3 to MergeSentenceId(offenderBookingId = 301, sequence = 3),
              dpsSentenceIdForSequence4 to MergeSentenceId(offenderBookingId = 301, sequence = 4),
            ),
            sentenceTerms = listOf(
              dpsSentenceTermIdForSequence1Term1 to NomisPeriodLengthId(offenderBookingId = 301, sentenceSequence = 1, termSequence = 1),
              dpsSentenceTermIdForSequence1Term2 to NomisPeriodLengthId(offenderBookingId = 301, sentenceSequence = 1, termSequence = 2),
              dpsSentenceTermIdForSequence2Term1 to NomisPeriodLengthId(offenderBookingId = 301, sentenceSequence = 2, termSequence = 1),
            ),
          ),
          retainedOffender = offenderNumberRetained,
          // courtCasesDeactivatedIds = listOf(dpsCourtCaseIdFor10001, dpsCourtCaseIdFor10002),
          // sentencesDeactivatedIds = listOf(dpsSentenceIdForSequence1, dpsSentenceIdForSequence2, dpsSentenceIdForSequence3, dpsSentenceIdForSequence4),
        )
      }

      @Nested
      inner class Success {

        @BeforeEach
        fun setUp() {
          courtSentencingMappingApiMockServer.stubPostMigrationMapping()

          courtSentencingOffenderEventsQueue.sendMessage(
            mergeDomainEvent(
              bookingId = 1234,
              offenderNo = offenderNumberRetained,
              removedOffenderNo = offenderNumberRemoved,
            ),
          ).also { waitForAnyProcessingToComplete() }
        }

        @Test
        fun `will callback to NOMIS to find the cases changed by the merge`() {
          courtSentencingNomisApiMockServer.verify(
            getRequestedFor(urlPathEqualTo("/prisoners/$offenderNumberRetained/sentencing/court-cases/post-merge")),
          )
        }

        @Test
        fun `will call mapping service to get DPS ids for cases and sentences to updated`() {
          courtSentencingMappingApiMockServer.verify(postRequestedFor(urlPathEqualTo("/mapping/court-sentencing/court-cases/nomis-case-ids/get-list")))
          courtSentencingMappingApiMockServer.verify(postRequestedFor(urlPathEqualTo("/mapping/court-sentencing/sentences/nomis-sentence-ids/get-list")))
        }

        @Test
        fun `will call DPS to synchronise any cases`() {
          dpsCourtSentencingServer.verify(
            postRequestedFor(urlPathEqualTo("/legacy/court-case/merge/person/$offenderNumberRetained"))
              .withRequestBody(matchingJsonPath("casesDeactivated[0].active", equalTo("false"))),
          )
        }

        @Test
        fun `will call mapping service to synchronise any new case mappings`() {
          courtSentencingMappingApiMockServer.verify(postRequestedFor(urlPathEqualTo("/mapping/court-sentencing/prisoner/$offenderNumberRetained/court-cases")))
        }

        @Test
        fun `will post any new case mappings`() {
          val request: CourtCaseBatchMappingDto = CourtSentencingMappingApiMockServer.getRequestBody(postRequestedFor(urlPathEqualTo("/mapping/court-sentencing/prisoner/$offenderNumberRetained/court-cases")))
          with(request) {
            assertThat(courtCases).hasSize(2)
            assertThat(courtCases[0].nomisCourtCaseId).isEqualTo(10001)
            assertThat(courtCases[0].dpsCourtCaseId).isEqualTo(dpsCourtCaseIdFor10001)
            assertThat(courtCases[1].nomisCourtCaseId).isEqualTo(10002)
            assertThat(courtCases[1].dpsCourtCaseId).isEqualTo(dpsCourtCaseIdFor10002)

            assertThat(courtAppearances).hasSize(2)
            assertThat(courtAppearances[0].nomisCourtAppearanceId).isEqualTo(401)
            assertThat(courtAppearances[0].dpsCourtAppearanceId).isEqualTo(dpsCourtAppearanceFor401)
            assertThat(courtAppearances[1].nomisCourtAppearanceId).isEqualTo(402)
            assertThat(courtAppearances[1].dpsCourtAppearanceId).isEqualTo(dpsCourtAppearanceFor402)

            assertThat(courtCharges).hasSize(4)
            assertThat(courtCharges[0].nomisCourtChargeId).isEqualTo(501)
            assertThat(courtCharges[0].dpsCourtChargeId).isEqualTo(dpsChargeIdFor501)
            assertThat(courtCharges[1].nomisCourtChargeId).isEqualTo(502)
            assertThat(courtCharges[1].dpsCourtChargeId).isEqualTo(dpsChargeIdFor502)
            assertThat(courtCharges[2].nomisCourtChargeId).isEqualTo(503)
            assertThat(courtCharges[2].dpsCourtChargeId).isEqualTo(dpsChargeIdFor503)
            assertThat(courtCharges[3].nomisCourtChargeId).isEqualTo(504)
            assertThat(courtCharges[3].dpsCourtChargeId).isEqualTo(dpsChargeIdFor504)

            assertThat(sentences).hasSize(4)
            assertThat(sentences[0].nomisBookingId).isEqualTo(301)
            assertThat(sentences[0].nomisSentenceSequence).isEqualTo(1)
            assertThat(sentences[0].dpsSentenceId).isEqualTo(dpsSentenceIdForSequence1)
            assertThat(sentences[1].nomisBookingId).isEqualTo(301)
            assertThat(sentences[1].nomisSentenceSequence).isEqualTo(2)
            assertThat(sentences[1].dpsSentenceId).isEqualTo(dpsSentenceIdForSequence2)
            assertThat(sentences[2].nomisBookingId).isEqualTo(301)
            assertThat(sentences[2].nomisSentenceSequence).isEqualTo(3)
            assertThat(sentences[2].dpsSentenceId).isEqualTo(dpsSentenceIdForSequence3)
            assertThat(sentences[3].nomisBookingId).isEqualTo(301)
            assertThat(sentences[3].nomisSentenceSequence).isEqualTo(4)
            assertThat(sentences[3].dpsSentenceId).isEqualTo(dpsSentenceIdForSequence4)
          }
        }

        @Test
        fun `will track telemetry for the merge`() {
          verify(telemetryClient).trackEvent(
            eq("from-nomis-synch-court-case-merge"),
            check {
              assertThat(it["offenderNo"]).isEqualTo(offenderNumberRetained)
              assertThat(it["removedOffenderNo"]).isEqualTo(offenderNumberRemoved)
              assertThat(it["courtCasesCreatedCount"]).isEqualTo("2")
              assertThat(it["courtCasesDeactivatedCount"]).isEqualTo("2")
            },
            isNull(),
          )
        }
      }

      @Nested
      inner class EventualSuccess {

        @BeforeEach
        fun setUp() {
          courtSentencingMappingApiMockServer.stubPostMigrationMappingFailureFollowedBySuccess()

          courtSentencingOffenderEventsQueue.sendMessage(
            mergeDomainEvent(
              bookingId = 1234,
              offenderNo = offenderNumberRetained,
              removedOffenderNo = offenderNumberRemoved,
            ),
          ).also { waitForAnyProcessingToComplete("from-nomis-synch-court-case-merge-mapping-retry-success") }
        }

        @Test
        fun `will callback to NOMIS to find the cases changed by the merge once`() {
          courtSentencingNomisApiMockServer.verify(
            1,
            getRequestedFor(urlPathEqualTo("/prisoners/$offenderNumberRetained/sentencing/court-cases/post-merge")),
          )
        }

        @Test
        fun `will call DPS to synchronise any cases once`() {
          dpsCourtSentencingServer.verify(1, postRequestedFor(urlPathEqualTo("/legacy/court-case/merge/person/$offenderNumberRetained")))
        }

        @Test
        fun `will call mapping service to synchronise any new case mappings until it succeeds`() {
          courtSentencingMappingApiMockServer.verify(2, postRequestedFor(urlPathEqualTo("/mapping/court-sentencing/prisoner/$offenderNumberRetained/court-cases")))
        }

        @Test
        fun `will track telemetry for the merge`() {
          verify(telemetryClient).trackEvent(
            eq("from-nomis-synch-court-case-merge"),
            check {
              assertThat(it["offenderNo"]).isEqualTo(offenderNumberRetained)
              assertThat(it["removedOffenderNo"]).isEqualTo(offenderNumberRemoved)
              assertThat(it["courtCasesCreatedCount"]).isEqualTo("2")
              assertThat(it["courtCasesDeactivatedCount"]).isEqualTo("2")
            },
            isNull(),
          )
          verify(telemetryClient).trackEvent(
            eq("from-nomis-synch-court-case-merge-mapping-retry-success"),
            check {
              assertThat(it["offenderNo"]).isEqualTo(offenderNumberRetained)
              assertThat(it["removedOffenderNo"]).isEqualTo(offenderNumberRemoved)
              assertThat(it["courtCasesCreatedCount"]).isEqualTo("2")
              assertThat(it["courtCasesDeactivatedCount"]).isEqualTo("2")
            },
            isNull(),
          )
        }
      }
    }
  }

  @Nested
  @DisplayName("OFFENDER_FIXED_TERM_RECALLS-UPDATED")
  inner class FixedTermRecallUpdated {
    val bookingId = NOMIS_BOOKING_ID
    val offenderNo = OFFENDER_ID_DISPLAY

    @Nested
    inner class UpdatedInNomis {
      @BeforeEach
      fun setUp() {
        courtSentencingNomisApiMockServer.stubGetOffenderActiveRecallSentences(
          bookingId,
          listOf(
            sentenceResponse(
              bookingId = bookingId,
              sentenceSequence = 1,
              eventId = NOMIS_COURT_APPEARANCE_ID,
            ).copy(
              offenderCharges = listOf(
                offenderChargeResponse(
                  offenderChargeId = 101,
                ),
              ),
              recallCustodyDate = RecallCustodyDate(
                returnToCustodyDate = LocalDate.parse("2023-04-01"),
                recallLength = 14,
              ),
              status = "A",
              fineAmount = BigDecimal("10.00"),
              category = CodeDescription(code = "2020", description = "2020"),
              calculationType = CodeDescription(code = "FTR", description = "28 day Fix term recall"),
              lineSequence = 10,
              consecSequence = null,
            ),
            sentenceResponse(
              bookingId = bookingId,
              sentenceSequence = 2,
              eventId = NOMIS_COURT_APPEARANCE_ID,
            ).copy(
              offenderCharges = listOf(
                offenderChargeResponse(
                  offenderChargeId = 201,
                ),
              ),
              recallCustodyDate = RecallCustodyDate(
                returnToCustodyDate = LocalDate.parse("2023-04-01"),
                recallLength = 14,
              ),
              status = "A",
              fineAmount = null,
              category = CodeDescription(code = "2020", description = "2020"),
              calculationType = CodeDescription(code = "FTR", description = "28 day Fix term recall"),
              lineSequence = 11,
              consecSequence = 1,
            ),
          ),
        )
        courtSentencingMappingApiMockServer.stubGetSentencesByNomisIds(
          listOf(
            SentenceMappingDto(
              nomisBookingId = bookingId,
              nomisSentenceSequence = 1,
              dpsSentenceId = "612cf742-feea-4562-b01d-ce643146fcf1",
            ),
            SentenceMappingDto(
              nomisBookingId = bookingId,
              nomisSentenceSequence = 2,
              dpsSentenceId = "870f7e03-6bfc-46e6-9782-a91daab5eab4",
            ),
          ),
        )
        courtSentencingMappingApiMockServer.stubGetSentenceByNomisId(
          nomisBookingId = bookingId,
          nomisSentenceSequence = 1,
          dpsSentenceId = "612cf742-feea-4562-b01d-ce643146fcf1",
        )

        courtSentencingMappingApiMockServer.stubGetCourtAppearanceByNomisId(
          nomisCourtAppearanceId = NOMIS_COURT_APPEARANCE_ID,
          dpsCourtAppearanceId = DPS_COURT_APPEARANCE_ID,
        )

        courtSentencingMappingApiMockServer.stubGetCourtChargeByNomisId(
          nomisCourtChargeId = 101,
          mapping = CourtChargeMappingDto(
            nomisCourtChargeId = 101,
            dpsCourtChargeId = "6cd85595-85c0-459f-9a7d-8d686284875f",
          ),
        )
        courtSentencingMappingApiMockServer.stubGetCourtChargeByNomisId(
          nomisCourtChargeId = 201,
          mapping = CourtChargeMappingDto(
            nomisCourtChargeId = 201,
            dpsCourtChargeId = "f677dea5-7062-416c-a03b-3523642fe093",
          ),
        )
        dpsCourtSentencingServer.stubPutSentenceForUpdate(sentenceId = "612cf742-feea-4562-b01d-ce643146fcf1")
        dpsCourtSentencingServer.stubPutSentenceForUpdate(sentenceId = "870f7e03-6bfc-46e6-9782-a91daab5eab4")

        courtSentencingOffenderEventsQueue.sendMessage(
          offenderFixedTermRecalls(
            eventType = "OFFENDER_FIXED_TERM_RECALLS-UPDATED",
            auditModule = "OIUFTRDA",
            bookingId = bookingId,
            offenderNo = offenderNo,
          ),
        ).also { waitForAnyProcessingToComplete() }
      }

      @Test
      fun `will retrieve active recall sentences from NOMIS`() {
        courtSentencingNomisApiMockServer.verify(
          getRequestedFor(urlPathEqualTo("/prisoners/booking-id/$bookingId/sentences/recall")),
        )
      }

      @Test
      fun `will retrieve the DPS IDs from the mapping service`() {
        courtSentencingMappingApiMockServer.verify(
          postRequestedFor(urlPathEqualTo("/mapping/court-sentencing/sentences/nomis-sentence-ids/get-list"))
            .withRequestBodyJsonPath("[0].nomisBookingId", equalTo(bookingId.toString()))
            .withRequestBodyJsonPath("[0].nomisSentenceSequence", equalTo("1"))
            .withRequestBodyJsonPath("[1].nomisBookingId", equalTo(bookingId.toString()))
            .withRequestBodyJsonPath("[1].nomisSentenceSequence", equalTo("2")),
        )
      }

      @Test
      fun `will update DPS with the latest custody date`() {
        val request1: LegacyCreateSentence = CourtSentencingDpsApiMockServer.getRequestBody(putRequestedFor(urlPathEqualTo("/legacy/sentence/612cf742-feea-4562-b01d-ce643146fcf1")))
        with(request1) {
          assertThat(this.returnToCustodyDate).isEqualTo(LocalDate.parse("2023-04-01"))
        }

        val request2: LegacyCreateSentence = CourtSentencingDpsApiMockServer.getRequestBody(putRequestedFor(urlPathEqualTo("/legacy/sentence/870f7e03-6bfc-46e6-9782-a91daab5eab4")))
        with(request2) {
          assertThat(this.returnToCustodyDate).isEqualTo(LocalDate.parse("2023-04-01"))
        }
      }

      @Test
      fun `will also update DPS with all other NOMIS sentence details`() {
        val request1: LegacyCreateSentence = CourtSentencingDpsApiMockServer.getRequestBody(putRequestedFor(urlPathEqualTo("/legacy/sentence/612cf742-feea-4562-b01d-ce643146fcf1")))
        with(request1) {
          assertThat(this.chargeUuids).contains(UUID.fromString("6cd85595-85c0-459f-9a7d-8d686284875f"))
          assertThat(this.active).isTrue
          assertThat(this.legacyData.sentenceCategory).isEqualTo("2020")
          assertThat(this.legacyData.sentenceCalcType).isEqualTo("FTR")
          assertThat(this.legacyData.bookingId).isEqualTo(NOMIS_BOOKING_ID)
          assertThat(this.fine?.fineAmount).isEqualTo(BigDecimal("10.00"))
          assertThat(this.consecutiveToLifetimeUuid).isNull()
        }

        val request2: LegacyCreateSentence = CourtSentencingDpsApiMockServer.getRequestBody(putRequestedFor(urlPathEqualTo("/legacy/sentence/870f7e03-6bfc-46e6-9782-a91daab5eab4")))
        with(request2) {
          assertThat(this.chargeUuids).contains(UUID.fromString("f677dea5-7062-416c-a03b-3523642fe093"))
          assertThat(this.active).isTrue
          assertThat(this.legacyData.sentenceCategory).isEqualTo("2020")
          assertThat(this.legacyData.sentenceCalcType).isEqualTo("FTR")
          assertThat(this.fine).isNull()
          assertThat(this.consecutiveToLifetimeUuid).isEqualTo(UUID.fromString("612cf742-feea-4562-b01d-ce643146fcf1"))
        }
      }

      @Test
      fun `will track telemetry for success`() {
        verify(telemetryClient).trackEvent(
          eq("recall-custody-date-synchronisation-success"),
          check {
            assertThat(it["offenderNo"]).isEqualTo(offenderNo)
            assertThat(it["bookingId"]).isEqualTo("$bookingId")
            assertThat(it["changeType"]).isEqualTo("OFFENDER_FIXED_TERM_RECALLS-UPDATED")
          },
          isNull(),
        )
      }
    }

    @Nested
    inner class MissingMappingsFailure {
      @BeforeEach
      fun setUp() {
        courtSentencingNomisApiMockServer.stubGetOffenderActiveRecallSentences(
          bookingId,
          listOf(
            sentenceResponse(
              bookingId = bookingId,
              sentenceSequence = 1,
              eventId = NOMIS_COURT_APPEARANCE_ID,
            ),
            sentenceResponse(
              bookingId = bookingId,
              sentenceSequence = 2,
              eventId = NOMIS_COURT_APPEARANCE_ID,
            ),
          ),
        )

        courtSentencingMappingApiMockServer.stubGetSentencesByNomisIds(
          listOf(
            SentenceMappingDto(
              nomisBookingId = bookingId,
              nomisSentenceSequence = 2,
              dpsSentenceId = "612cf742-feea-4562-b01d-ce643146fcf1",
            ),
          ),
        )

        courtSentencingMappingApiMockServer.stubGetCourtAppearanceByNomisId(
          nomisCourtAppearanceId = NOMIS_COURT_APPEARANCE_ID,
          dpsCourtAppearanceId = DPS_COURT_APPEARANCE_ID,
        )

        courtSentencingMappingApiMockServer.stubGetSentencesByNomisIds(
          listOf(
            SentenceMappingDto(
              nomisBookingId = bookingId,
              nomisSentenceSequence = 2,
              dpsSentenceId = "612cf742-feea-4562-b01d-ce643146fcf1",
            ),
          ),
        )
        courtSentencingOffenderEventsQueue.sendMessage(
          offenderFixedTermRecalls(
            eventType = "OFFENDER_FIXED_TERM_RECALLS-UPDATED",
            auditModule = "OIUFTRDA",
            bookingId = bookingId,
            offenderNo = offenderNo,
          ),
        ).also { waitForAnyProcessingToComplete("recall-custody-date-synchronisation-error", 2) }
      }

      @Test
      fun `will retrieve active recall sentences from NOMIS`() {
        courtSentencingNomisApiMockServer.verify(
          getRequestedFor(urlPathEqualTo("/prisoners/booking-id/$bookingId/sentences/recall")),
        )
      }

      @Test
      fun `will retrieve the DPS IDs from the mapping service`() {
        courtSentencingMappingApiMockServer.verify(
          postRequestedFor(urlPathEqualTo("/mapping/court-sentencing/sentences/nomis-sentence-ids/get-list"))
            .withRequestBodyJsonPath("[0].nomisBookingId", equalTo(bookingId.toString()))
            .withRequestBodyJsonPath("[0].nomisSentenceSequence", equalTo("1"))
            .withRequestBodyJsonPath("[1].nomisBookingId", equalTo(bookingId.toString()))
            .withRequestBodyJsonPath("[1].nomisSentenceSequence", equalTo("2")),
        )
      }

      @Test
      fun `will track telemetry for failure`() {
        verify(telemetryClient, times(2)).trackEvent(
          eq("recall-custody-date-synchronisation-error"),
          check {
            assertThat(it["offenderNo"]).isEqualTo(offenderNo)
            assertThat(it["bookingId"]).isEqualTo("$bookingId")
            assertThat(it["changeType"]).isEqualTo("OFFENDER_FIXED_TERM_RECALLS-UPDATED")
          },
          isNull(),
        )
      }
    }

    @Nested
    inner class UpdatedInNomisNoActiveRecallSentences {
      @BeforeEach
      fun setUp() {
        courtSentencingNomisApiMockServer.stubGetOffenderActiveRecallSentences(
          bookingId,
          emptyList(),
        )
        courtSentencingOffenderEventsQueue.sendMessage(
          offenderFixedTermRecalls(
            eventType = "OFFENDER_FIXED_TERM_RECALLS-UPDATED",
            auditModule = "OIUFTRDA",
            bookingId = bookingId,
            offenderNo = offenderNo,
          ),
        ).also { waitForAnyProcessingToComplete() }
      }

      @Test
      fun `will retrieve active recall sentences from NOMIS`() {
        courtSentencingNomisApiMockServer.verify(
          getRequestedFor(urlPathEqualTo("/prisoners/booking-id/$bookingId/sentences/recall")),
        )
      }

      @Test
      fun `will track telemetry for success`() {
        verify(telemetryClient).trackEvent(
          eq("recall-custody-date-synchronisation-ignored"),
          check {
            assertThat(it["offenderNo"]).isEqualTo(offenderNo)
            assertThat(it["bookingId"]).isEqualTo("$bookingId")
            assertThat(it["reason"]).isEqualTo("No active recall sentences found for booking $bookingId")
          },
          isNull(),
        )
      }
    }

    @Nested
    inner class UpdatedInDps {
      @BeforeEach
      fun setUp() {
        courtSentencingOffenderEventsQueue.sendMessage(
          offenderFixedTermRecalls(
            eventType = "OFFENDER_FIXED_TERM_RECALLS-UPDATED",
            auditModule = "DPS_SYNCHRONISATION",
            bookingId = bookingId,
            offenderNo = offenderNo,
          ),
        ).also { waitForAnyProcessingToComplete() }
      }

      @Test
      fun `will track telemetry for skip`() {
        verify(telemetryClient).trackEvent(
          eq("recall-custody-date-synchronisation-skipped"),
          check {
            assertThat(it["offenderNo"]).isEqualTo(offenderNo)
            assertThat(it["bookingId"]).isEqualTo("$bookingId")
            assertThat(it["changeType"]).isEqualTo("OFFENDER_FIXED_TERM_RECALLS-UPDATED")
          },
          isNull(),
        )
      }
    }
  }
}

fun dpsMergeCreateResponse(
  courtCases: List<Pair<String, Long>>,
  charges: List<Pair<String, Long>>,
  courtAppearances: List<Pair<String, Long>>,
  sentences: List<Pair<String, MergeSentenceId>>,
  sentenceTerms: List<Pair<String, NomisPeriodLengthId>>,
): MergeCreateCourtCasesResponse = MergeCreateCourtCasesResponse(
  courtCases = courtCases.map { MergeCreateCourtCaseResponse(courtCaseUuid = it.first, caseId = it.second) },
  appearances = courtAppearances.map {
    MergeCreateCourtAppearanceResponse(
      appearanceUuid = UUID.fromString(it.first),
      eventId = it.second,
    )
  },
  charges = charges.map {
    MergeCreateChargeResponse(
      chargeUuid = UUID.fromString(it.first),
      chargeNOMISId = it.second,
    )
  },
  sentences = sentences.map {
    MergeCreateSentenceResponse(
      sentenceUuid = UUID.fromString(it.first),
      sentenceNOMISId = it.second,
    )
  },
  sentenceTerms = sentenceTerms.map {
    MergeCreatePeriodLengthResponse(
      periodLengthUuid = UUID.fromString(it.first),
      sentenceTermNOMISId = it.second,
    )
  },
)

fun courtCaseEvent(
  eventType: String,
  bookingId: Long = NOMIS_BOOKING_ID,
  caseId: Long = NOMIS_COURT_CASE_ID,
  offenderNo: String = OFFENDER_ID_DISPLAY,
  auditModule: String = "DPS",
) = """{
    "MessageId": "ae06c49e-1f41-4b9f-b2f2-dcca610d02cd", "Type": "Notification", "Timestamp": "2019-10-21T14:01:18.500Z", 
    "Message": "{\"eventId\":\"5958295\",\"eventType\":\"$eventType\",\"eventDatetime\":\"2019-10-21T15:00:25.489964\",\"bookingId\": \"$bookingId\",\"caseId\": \"$caseId\",\"offenderIdDisplay\": \"$offenderNo\",\"nomisEventType\":\"COURT_EVENT\",\"auditModuleName\":\"$auditModule\" }",
    "TopicArn": "arn:aws:sns:eu-west-1:000000000000:offender_events", 
    "MessageAttributes": {
      "eventType": {"Type": "String", "Value": "$eventType"}, 
      "id": {"Type": "String", "Value": "8b07cbd9-0820-0a0f-c32f-a9429b618e0b"}, 
      "contentType": {"Type": "String", "Value": "text/plain;charset=UTF-8"}, 
      "timestamp": {"Type": "Number.java.lang.Long", "Value": "1571666478344"}
    }
}
""".trimIndent()

fun courtCaseLinkingEvent(
  eventType: String,
  bookingId: Long = NOMIS_BOOKING_ID,
  caseId: Long = NOMIS_COURT_CASE_ID,
  combinedCaseId: Long = 4321L,
  offenderNo: String = OFFENDER_ID_DISPLAY,
  auditModule: String = "DPS",
) = """{
    "MessageId": "ae06c49e-1f41-4b9f-b2f2-dcca610d02cd", "Type": "Notification", "Timestamp": "2019-10-21T14:01:18.500Z", 
    "Message": "{\"eventId\":\"5958295\",\"eventType\":\"$eventType\",\"eventDatetime\":\"2019-10-21T15:00:25.489964\",\"bookingId\": \"$bookingId\",\"caseId\": \"$caseId\",\"combinedCaseId\": \"$combinedCaseId\",\"offenderIdDisplay\": \"$offenderNo\",\"nomisEventType\":\"COURT_EVENT\",\"auditModuleName\":\"$auditModule\" }",
    "TopicArn": "arn:aws:sns:eu-west-1:000000000000:offender_events", 
    "MessageAttributes": {
      "eventType": {"Type": "String", "Value": "$eventType"}, 
      "id": {"Type": "String", "Value": "8b07cbd9-0820-0a0f-c32f-a9429b618e0b"}, 
      "contentType": {"Type": "String", "Value": "text/plain;charset=UTF-8"}, 
      "timestamp": {"Type": "Number.java.lang.Long", "Value": "1571666478344"}
    }
}
""".trimIndent()

fun caseIdentifiersEvent(
  eventType: String,
  reference: String = NOMIS_CASE_IDENTIFIER,
  identifierType: String = NOMIS_CASE_IDENTIFIER_TYPE,
  caseId: Long = NOMIS_COURT_CASE_ID,
  offenderNo: String = OFFENDER_ID_DISPLAY,
  auditModule: String = "DPS",
) = """{
    "MessageId": "ae06c49e-1f41-4b9f-b2f2-dcca610d02cd", "Type": "Notification", "Timestamp": "2019-10-21T14:01:18.500Z", 
    "Message": "{\"eventId\":\"5958295\",\"eventType\":\"$eventType\",\"eventDatetime\":\"2019-10-21T15:00:25.489964\",\"identifierNo\": \"$reference\",\"identifierType\": \"$identifierType\",\"caseId\": \"$caseId\",\"offenderIdDisplay\": \"$offenderNo\",\"nomisEventType\":\"COURT_EVENT\",\"auditModuleName\":\"$auditModule\" }",
    "TopicArn": "arn:aws:sns:eu-west-1:000000000000:offender_events", 
    "MessageAttributes": {
      "eventType": {"Type": "String", "Value": "$eventType"}, 
      "id": {"Type": "String", "Value": "8b07cbd9-0820-0a0f-c32f-a9429b618e0b"}, 
      "contentType": {"Type": "String", "Value": "text/plain;charset=UTF-8"}, 
      "timestamp": {"Type": "Number.java.lang.Long", "Value": "1571666478344"}
    }
}
""".trimIndent()

fun courtAppearanceEvent(
  eventType: String,
  bookingId: Long = NOMIS_BOOKING_ID,
  courtAppearanceId: Long = NOMIS_COURT_APPEARANCE_ID,
  courtCaseId: Long? = NOMIS_COURT_CASE_ID,
  offenderNo: String = OFFENDER_ID_DISPLAY,
  auditModule: String = "DPS",
  isBreachHearing: Boolean = false,
) = """{
    "MessageId": "ae06c49e-1f41-4b9f-b2f2-dcca610d02cd", "Type": "Notification", "Timestamp": "2019-10-21T14:01:18.500Z", 
    "Message": "{\"eventId\":\"$courtAppearanceId\",${courtCaseId.let { """\"caseId\":\"$courtCaseId\",""" }}\"eventType\":\"$eventType\",\"eventDatetime\":\"2019-10-21T15:00:25.489964\",\"bookingId\": \"$bookingId\",\"offenderIdDisplay\": \"$offenderNo\",\"nomisEventType\":\"COURT_EVENT\",\"auditModuleName\":\"$auditModule\",\"isBreachHearing\": $isBreachHearing }",
    "TopicArn": "arn:aws:sns:eu-west-1:000000000000:offender_events", 
    "MessageAttributes": {
      "eventType": {"Type": "String", "Value": "$eventType"}, 
      "id": {"Type": "String", "Value": "8b07cbd9-0820-0a0f-c32f-a9429b618e0b"}, 
      "contentType": {"Type": "String", "Value": "text/plain;charset=UTF-8"}, 
      "timestamp": {"Type": "Number.java.lang.Long", "Value": "1571666478344"}
    }
}
""".trimIndent()

fun courtEventChargeEvent(
  eventType: String,
  bookingId: Long = NOMIS_BOOKING_ID,
  eventId: Long = NOMIS_COURT_APPEARANCE_ID,
  chargeId: Long = NOMIS_OFFENDER_CHARGE_ID,
  offenderNo: String = OFFENDER_ID_DISPLAY,
  auditModule: String = "NOMIS",
) = """{
    "MessageId": "ae06c49e-1f41-4b9f-b2f2-dcca610d02cd", "Type": "Notification", "Timestamp": "2019-10-21T14:01:18.500Z", 
    "Message": "{\"eventType\":\"$eventType\",\"eventDatetime\":\"2019-10-21T15:00:25.489964\",\"bookingId\": \"$bookingId\",\"eventId\": \"$eventId\",\"chargeId\": \"$chargeId\",\"offenceCodeChange\": true, \"offenderIdDisplay\": \"$offenderNo\",\"nomisEventType\":\"COURT_EVENT\",\"auditModuleName\":\"$auditModule\" }",
    "TopicArn": "arn:aws:sns:eu-west-1:000000000000:offender_events", 
    "MessageAttributes": {
      "eventType": {"Type": "String", "Value": "$eventType"}, 
      "id": {"Type": "String", "Value": "8b07cbd9-0820-0a0f-c32f-a9429b618e0b"}, 
      "contentType": {"Type": "String", "Value": "text/plain;charset=UTF-8"}, 
      "timestamp": {"Type": "Number.java.lang.Long", "Value": "1571666478344"}
    }
}
""".trimIndent()

fun courtEventChargeLinkingEvent(
  eventType: String,
  bookingId: Long = NOMIS_BOOKING_ID,
  eventId: Long = NOMIS_COURT_APPEARANCE_ID,
  chargeId: Long = NOMIS_OFFENDER_CHARGE_ID,
  combinedCaseId: Long = 4321L,
  caseId: Long = 4320L,
  offenderNo: String = OFFENDER_ID_DISPLAY,
  auditModule: String = "NOMIS",
  eventDatetime: String = "2019-10-21T15:00:25.489964",
) = """{
    "MessageId": "ae06c49e-1f41-4b9f-b2f2-dcca610d02cd", "Type": "Notification", "Timestamp": "2019-10-21T14:01:18.500Z", 
    "Message": "{\"eventType\":\"$eventType\",\"eventDatetime\":\"$eventDatetime\",\"bookingId\": \"$bookingId\",\"caseId\": \"$caseId\",\"combinedCaseId\": \"$combinedCaseId\",\"eventId\": \"$eventId\",\"chargeId\": \"$chargeId\",\"offenderIdDisplay\": \"$offenderNo\",\"nomisEventType\":\"COURT_EVENT\",\"auditModuleName\":\"$auditModule\" }",
    "TopicArn": "arn:aws:sns:eu-west-1:000000000000:offender_events", 
    "MessageAttributes": {
      "eventType": {"Type": "String", "Value": "$eventType"}, 
      "id": {"Type": "String", "Value": "8b07cbd9-0820-0a0f-c32f-a9429b618e0b"}, 
      "contentType": {"Type": "String", "Value": "text/plain;charset=UTF-8"}, 
      "timestamp": {"Type": "Number.java.lang.Long", "Value": "1571666478344"}
    }
}
""".trimIndent()

fun offenderChargeEvent(
  eventType: String,
  bookingId: Long = NOMIS_BOOKING_ID,
  chargeId: Long = NOMIS_OFFENDER_CHARGE_ID,
  offenderNo: String = OFFENDER_ID_DISPLAY,
  offenceCodeChange: String = "true",
  auditModule: String = "DPS",
) = """{
    "MessageId": "ae06c49e-1f41-4b9f-b2f2-dcca610d02cd", "Type": "Notification", "Timestamp": "2019-10-21T14:01:18.500Z", 
    "Message": "{\"eventId\":\"5958295\",\"eventType\":\"$eventType\",\"eventDatetime\":\"2019-10-21T15:00:25.489964\",\"bookingId\": \"$bookingId\",\"chargeId\": \"$chargeId\",\"offenceCodeChange\": \"$offenceCodeChange\",\"offenderIdDisplay\": \"$offenderNo\",\"nomisEventType\":\"COURT_EVENT\",\"auditModuleName\":\"$auditModule\" }",
    "TopicArn": "arn:aws:sns:eu-west-1:000000000000:offender_events", 
    "MessageAttributes": {
      "eventType": {"Type": "String", "Value": "$eventType"}, 
      "id": {"Type": "String", "Value": "8b07cbd9-0820-0a0f-c32f-a9429b618e0b"}, 
      "contentType": {"Type": "String", "Value": "text/plain;charset=UTF-8"}, 
      "timestamp": {"Type": "Number.java.lang.Long", "Value": "1571666478344"}
    }
}
""".trimIndent()

fun offenderFixedTermRecalls(
  eventType: String,
  bookingId: Long = NOMIS_BOOKING_ID,
  offenderNo: String = OFFENDER_ID_DISPLAY,
  auditModule: String = "DPS",
) = //language=JSON
  """{
    "MessageId": "ae06c49e-1f41-4b9f-b2f2-dcca610d02cd", "Type": "Notification", "Timestamp": "2019-10-21T14:01:18.500Z", 
    "Message": "{\"eventType\":\"$eventType\",\"eventDatetime\":\"2019-10-21T15:00:25.489964\",\"bookingId\": \"$bookingId\",\"offenderIdDisplay\": \"$offenderNo\",\"nomisEventType\":\"$eventType\",\"auditModuleName\":\"$auditModule\" }",
    "TopicArn": "arn:aws:sns:eu-west-1:000000000000:offender_events", 
    "MessageAttributes": {
      "eventType": {"Type": "String", "Value": "$eventType"}, 
      "id": {"Type": "String", "Value": "8b07cbd9-0820-0a0f-c32f-a9429b618e0b"}, 
      "contentType": {"Type": "String", "Value": "text/plain;charset=UTF-8"}, 
      "timestamp": {"Type": "Number.java.lang.Long", "Value": "1571666478344"}
    }
}
  """.trimIndent()
