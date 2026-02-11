package uk.gov.justice.digital.hmpps.prisonerfromnomismigration.officialvisits

import com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor
import com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor
import com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.mockito.Mockito.eq
import org.mockito.kotlin.check
import org.mockito.kotlin.isNull
import org.mockito.kotlin.verify
import org.springframework.beans.factory.annotation.Autowired
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.integration.SqsIntegrationTestBase
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.integration.sendMessage
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomismappings.model.DuplicateErrorContentObject
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomismappings.model.DuplicateMappingErrorResponse
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomismappings.model.LocationMappingDto
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomismappings.model.OfficialVisitMappingDto
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomismappings.model.VisitSlotMappingDto
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.officialvisits.OfficialVisitsDpsApiExtension.Companion.dpsOfficialVisitsServer
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.officialvisits.OfficialVisitsDpsApiExtension.Companion.getRequestBody
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.officialvisits.OfficialVisitsDpsApiMockServer.Companion.syncOfficialVisit
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.officialvisits.OfficialVisitsNomisApiMockServer.Companion.officialVisitResponse
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.officialvisits.model.SyncCreateOfficialVisitRequest
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.wiremock.withRequestBodyJsonPath
import java.util.*

class OfficialVisitsSynchronisationIntTest : SqsIntegrationTestBase() {
  val offenderNo = "A1234KT"
  val bookingId = 1234L
  val prisonId = "MDI"
  val nomisVisitId = 65432L
  val nomisVisitorId = 76544L
  val nomisPersonId = 8987756L

  @Autowired
  private lateinit var nomisApiMock: OfficialVisitsNomisApiMockServer

  private val dpsApiMock = dpsOfficialVisitsServer

  @Autowired
  private lateinit var mappingApiMock: OfficialVisitsMappingApiMockServer

  @Autowired
  private lateinit var visitSlotsMappingApiMock: VisitSlotsMappingApiMockServer

  @Nested
  @DisplayName("OFFENDER_OFFICIAL_VISIT-INSERTED")
  inner class OfficialVisitCreated {
    @Nested
    inner class WhenCreatedInDps {
      @BeforeEach
      fun setUp() {
        officialVisitsOffenderEventsQueue.sendMessage(
          officialVisitEvent(
            eventType = "OFFENDER_OFFICIAL_VISIT-INSERTED",
            offenderNo = offenderNo,
            visitId = nomisVisitId,
            agencyLocationId = prisonId,
            bookingId = bookingId,
            auditModuleName = "DPS_SYNCHRONISATION_OFFICIAL_VISITS",
          ),
        ).also { waitForAnyProcessingToComplete() }
      }

      @Test
      fun `will track telemetry`() {
        verify(telemetryClient).trackEvent(
          eq("officialvisits-visit-synchronisation-created-skipped"),
          check {
            assertThat(it["offenderNo"]).isEqualTo(offenderNo)
            assertThat(it["nomisVisitId"]).isEqualTo(nomisVisitId.toString())
            assertThat(it["bookingId"]).isEqualTo(bookingId.toString())
            assertThat(it["prisonId"]).isEqualTo(prisonId)
          },
          isNull(),
        )
      }
    }

    @Nested
    inner class WhenCreatedInNomis {

      @Nested
      inner class AlreadyExists {
        val dpsOfficialVisitId = 123L

        @BeforeEach
        fun setUp() {
          mappingApiMock.stubGetByVisitNomisIdsOrNull(
            nomisVisitId = nomisVisitId,
            mapping = OfficialVisitMappingDto(
              dpsId = dpsOfficialVisitId.toString(),
              nomisId = nomisVisitId,
              mappingType = OfficialVisitMappingDto.MappingType.NOMIS_CREATED,
            ),
          )
          officialVisitsOffenderEventsQueue.sendMessage(
            officialVisitEvent(
              eventType = "OFFENDER_OFFICIAL_VISIT-INSERTED",
              offenderNo = offenderNo,
              visitId = nomisVisitId,
              agencyLocationId = prisonId,
              bookingId = bookingId,
            ),
          ).also { waitForAnyProcessingToComplete() }
        }

        @Test
        fun `will track telemetry`() {
          verify(telemetryClient).trackEvent(
            eq("officialvisits-visit-synchronisation-created-ignored"),
            check {
              assertThat(it["offenderNo"]).isEqualTo(offenderNo)
              assertThat(it["nomisVisitId"]).isEqualTo(nomisVisitId.toString())
              assertThat(it["bookingId"]).isEqualTo(bookingId.toString())
              assertThat(it["prisonId"]).isEqualTo(prisonId)
              assertThat(it["dpsOfficialVisitId"]).isEqualTo(dpsOfficialVisitId.toString())
            },
            isNull(),
          )
        }
      }

      @Nested
      inner class HappyPath {
        val dpsOfficialVisitId = 8549934L
        val dpsVisitSlotId = 123L
        val nomisVisitSlotId = 8484L
        val dpsLocationId: UUID = UUID.randomUUID()
        val nomisLocationId = 765L

        @BeforeEach
        fun setUp() {
          mappingApiMock.stubGetByVisitNomisIdsOrNull(
            nomisVisitId = nomisVisitId,
            mapping = null,
          )

          nomisApiMock.stubGetOfficialVisit(
            visitId = nomisVisitId,
            response = officialVisitResponse().copy(
              internalLocationId = nomisLocationId,
              visitSlotId = nomisVisitSlotId,
            ),
          )

          mappingApiMock.stubGetInternalLocationByNomisId(
            nomisLocationId = nomisLocationId,
            mapping = LocationMappingDto(
              dpsLocationId = dpsLocationId.toString(),
              nomisLocationId = nomisLocationId,
              mappingType = LocationMappingDto.MappingType.LOCATION_CREATED,
            ),
          )

          visitSlotsMappingApiMock.stubGetVisitSlotByNomisIdOrNull(
            nomisId = nomisVisitSlotId,
            mapping = VisitSlotMappingDto(
              dpsId = dpsVisitSlotId.toString(),
              nomisId = nomisVisitSlotId,
              mappingType = VisitSlotMappingDto.MappingType.NOMIS_CREATED,
            ),
          )

          dpsApiMock.stubCreateVisit(response = syncOfficialVisit().copy(officialVisitId = dpsOfficialVisitId))
          mappingApiMock.stubCreateVisitMapping()

          officialVisitsOffenderEventsQueue.sendMessage(
            officialVisitEvent(
              eventType = "OFFENDER_OFFICIAL_VISIT-INSERTED",
              offenderNo = offenderNo,
              visitId = nomisVisitId,
              agencyLocationId = prisonId,
              bookingId = bookingId,
            ),
          ).also { waitForAnyProcessingToComplete() }
        }

        @Test
        fun `will track telemetry`() {
          verify(telemetryClient).trackEvent(
            eq("officialvisits-visit-synchronisation-created-success"),
            check {
              assertThat(it["offenderNo"]).isEqualTo(offenderNo)
              assertThat(it["nomisVisitId"]).isEqualTo(nomisVisitId.toString())
              assertThat(it["bookingId"]).isEqualTo(bookingId.toString())
              assertThat(it["prisonId"]).isEqualTo(prisonId)
              assertThat(it["dpsOfficialVisitId"]).isEqualTo(dpsOfficialVisitId.toString())
            },
            isNull(),
          )
        }

        @Test
        fun `will check if visit has already been created`() {
          mappingApiMock.verify(getRequestedFor(urlPathEqualTo("/mapping/official-visits/visit/nomis-id/$nomisVisitId")))
        }

        @Test
        fun `will retrieve the NOMIS visit details`() {
          nomisApiMock.verify(getRequestedFor(urlPathEqualTo("/official-visits/$nomisVisitId")))
        }

        @Test
        fun `will retrieve the DPS location id for the visit slot room location`() {
          mappingApiMock.verify(getRequestedFor(urlPathEqualTo("/mapping/locations/nomis/$nomisLocationId")))
        }

        @Test
        fun `will retrieve the DPS visit slot id for the NOMIS visit slot`() {
          mappingApiMock.verify(getRequestedFor(urlPathEqualTo("/mapping/visit-slots/visit-slot/nomis-id/$nomisVisitSlotId")))
        }

        @Test
        fun `will create the official visit in DPS`() {
          val request: SyncCreateOfficialVisitRequest = getRequestBody(postRequestedFor(urlPathEqualTo("/sync/official-visit")))
          with(request) {
            assertThat(this.dpsLocationId).isEqualTo(dpsLocationId)
          }
        }

        @Test
        fun `will create mapping`() {
          mappingApiMock.verify(
            postRequestedFor(urlPathEqualTo("/mapping/official-visits/visit"))
              .withRequestBodyJsonPath("dpsId", dpsOfficialVisitId.toString())
              .withRequestBodyJsonPath("nomisId", nomisVisitId)
              .withRequestBodyJsonPath("mappingType", "NOMIS_CREATED"),
          )
        }
      }

      @Nested
      inner class MappingFailures {
        val dpsOfficialVisitId = 8549934L
        val dpsVisitSlotId = 123L
        val nomisVisitSlotId = 8484L
        val dpsLocationId: UUID = UUID.randomUUID()
        val nomisLocationId = 765L

        @BeforeEach
        fun setUp() {
          mappingApiMock.stubGetByVisitNomisIdsOrNull(
            nomisVisitId = nomisVisitId,
            mapping = null,
          )

          nomisApiMock.stubGetOfficialVisit(
            visitId = nomisVisitId,
            response = officialVisitResponse().copy(
              internalLocationId = nomisLocationId,
              visitSlotId = nomisVisitSlotId,
            ),
          )

          mappingApiMock.stubGetInternalLocationByNomisId(
            nomisLocationId = nomisLocationId,
            mapping = LocationMappingDto(
              dpsLocationId = dpsLocationId.toString(),
              nomisLocationId = nomisLocationId,
              mappingType = LocationMappingDto.MappingType.LOCATION_CREATED,
            ),
          )

          visitSlotsMappingApiMock.stubGetVisitSlotByNomisIdOrNull(
            nomisId = nomisVisitSlotId,
            mapping = VisitSlotMappingDto(
              dpsId = dpsVisitSlotId.toString(),
              nomisId = nomisVisitSlotId,
              mappingType = VisitSlotMappingDto.MappingType.NOMIS_CREATED,
            ),
          )

          dpsApiMock.stubCreateVisit(response = syncOfficialVisit().copy(officialVisitId = dpsOfficialVisitId))
        }

        @Nested
        inner class FailureAndRecovery {
          @BeforeEach
          fun setUp() {
            mappingApiMock.stubCreateVisitMappingFailureFollowedBySuccess()

            officialVisitsOffenderEventsQueue.sendMessage(
              officialVisitEvent(
                eventType = "OFFENDER_OFFICIAL_VISIT-INSERTED",
                offenderNo = offenderNo,
                visitId = nomisVisitId,
                agencyLocationId = prisonId,
                bookingId = bookingId,
              ),
            ).also { waitForAnyProcessingToComplete("officialvisits-visit-mapping-synchronisation-created") }
          }

          @Test
          fun `will create the official visit in DPS only once`() {
            dpsApiMock.verify(1, postRequestedFor(urlPathEqualTo("/sync/official-visit")))
          }

          @Test
          fun `will eventually create mapping after a retry`() {
            mappingApiMock.verify(2, postRequestedFor(urlPathEqualTo("/mapping/official-visits/visit")))
          }

          @Test
          fun `will track telemetry`() {
            verify(telemetryClient).trackEvent(
              eq("officialvisits-visit-synchronisation-created-success"),
              check {
                assertThat(it["offenderNo"]).isEqualTo(offenderNo)
                assertThat(it["nomisVisitId"]).isEqualTo(nomisVisitId.toString())
                assertThat(it["bookingId"]).isEqualTo(bookingId.toString())
                assertThat(it["prisonId"]).isEqualTo(prisonId)
                assertThat(it["dpsOfficialVisitId"]).isEqualTo(dpsOfficialVisitId.toString())
              },
              isNull(),
            )

            verify(telemetryClient).trackEvent(
              eq("officialvisits-visit-mapping-synchronisation-created"),
              check {
                assertThat(it["offenderNo"]).isEqualTo(offenderNo)
                assertThat(it["nomisVisitId"]).isEqualTo(nomisVisitId.toString())
                assertThat(it["bookingId"]).isEqualTo(bookingId.toString())
                assertThat(it["prisonId"]).isEqualTo(prisonId)
                assertThat(it["dpsOfficialVisitId"]).isEqualTo(dpsOfficialVisitId.toString())
              },
              isNull(),
            )
          }
        }

        @Nested
        inner class DuplicateDetected {
          @BeforeEach
          fun setUp() {
            mappingApiMock.stubCreateVisitMapping(
              error = DuplicateMappingErrorResponse(
                moreInfo = DuplicateErrorContentObject(
                  duplicate = OfficialVisitMappingDto(
                    dpsId = dpsOfficialVisitId.toString(),
                    nomisId = nomisVisitId,
                    mappingType = OfficialVisitMappingDto.MappingType.NOMIS_CREATED,
                  ),
                  existing = OfficialVisitMappingDto(
                    dpsId = "98765",
                    nomisId = nomisVisitId,
                    mappingType = OfficialVisitMappingDto.MappingType.NOMIS_CREATED,
                  ),
                ),
                errorCode = 1409,
                status = DuplicateMappingErrorResponse.Status._409_CONFLICT,
                userMessage = "Duplicate mapping",
              ),
            )

            officialVisitsOffenderEventsQueue.sendMessage(
              officialVisitEvent(
                eventType = "OFFENDER_OFFICIAL_VISIT-INSERTED",
                offenderNo = offenderNo,
                visitId = nomisVisitId,
                agencyLocationId = prisonId,
                bookingId = bookingId,
              ),
            ).also { waitForAnyProcessingToComplete("from-nomis-sync-officialvisits-duplicate") }
          }

          @Test
          fun `will create the official visit in DPS only once`() {
            dpsApiMock.verify(1, postRequestedFor(urlPathEqualTo("/sync/official-visit")))
          }

          @Test
          fun `will not bother retrying mapping`() {
            mappingApiMock.verify(1, postRequestedFor(urlPathEqualTo("/mapping/official-visits/visit")))
          }

          @Test
          fun `will track telemetry`() {
            verify(telemetryClient).trackEvent(
              eq("officialvisits-visit-synchronisation-created-success"),
              check {
                assertThat(it["offenderNo"]).isEqualTo(offenderNo)
                assertThat(it["nomisVisitId"]).isEqualTo(nomisVisitId.toString())
                assertThat(it["bookingId"]).isEqualTo(bookingId.toString())
                assertThat(it["prisonId"]).isEqualTo(prisonId)
                assertThat(it["dpsOfficialVisitId"]).isEqualTo(dpsOfficialVisitId.toString())
              },
              isNull(),
            )

            verify(telemetryClient).trackEvent(
              eq("from-nomis-sync-officialvisits-duplicate"),
              check {
                assertThat(it["type"]).isEqualTo("OFFICIALVISIT")
                assertThat(it["existingNomisId"]).isEqualTo(nomisVisitId.toString())
                assertThat(it["existingDpsId"]).isEqualTo("98765")
                assertThat(it["duplicateNomisId"]).isEqualTo(nomisVisitId.toString())
                assertThat(it["duplicateDpsId"]).isEqualTo(dpsOfficialVisitId.toString())
              },
              isNull(),
            )
          }
        }
      }
    }
  }

  @Nested
  @DisplayName("OFFENDER_OFFICIAL_VISIT-UPDATED")
  inner class OfficialVisitUpdated {

    @Nested
    inner class WhenUpdatedInNomis {
      @BeforeEach
      fun setUp() {
        officialVisitsOffenderEventsQueue.sendMessage(
          officialVisitEvent(
            eventType = "OFFENDER_OFFICIAL_VISIT-UPDATED",
            offenderNo = offenderNo,
            visitId = nomisVisitId,
            agencyLocationId = prisonId,
            bookingId = bookingId,
          ),
        ).also { waitForAnyProcessingToComplete() }
      }

      @Test
      fun `will track telemetry`() {
        verify(telemetryClient).trackEvent(
          eq("officialvisits-visit-synchronisation-updated-success"),
          check {
            assertThat(it["offenderNo"]).isEqualTo(offenderNo)
            assertThat(it["nomisVisitId"]).isEqualTo(nomisVisitId.toString())
            assertThat(it["bookingId"]).isEqualTo(bookingId.toString())
            assertThat(it["prisonId"]).isEqualTo(prisonId)
          },
          isNull(),
        )
      }
    }
  }

  @Nested
  @DisplayName("OFFENDER_OFFICIAL_VISIT-DELETED")
  inner class OfficialVisitDeleted {

    @Nested
    inner class WhenDeletedInNomis {
      @BeforeEach
      fun setUp() {
        officialVisitsOffenderEventsQueue.sendMessage(
          officialVisitEvent(
            eventType = "OFFENDER_OFFICIAL_VISIT-DELETED",
            offenderNo = offenderNo,
            visitId = nomisVisitId,
            agencyLocationId = prisonId,
            bookingId = bookingId,
          ),
        ).also { waitForAnyProcessingToComplete() }
      }

      @Test
      fun `will track telemetry`() {
        verify(telemetryClient).trackEvent(
          eq("officialvisits-visit-synchronisation-deleted-success"),
          check {
            assertThat(it["offenderNo"]).isEqualTo(offenderNo)
            assertThat(it["nomisVisitId"]).isEqualTo(nomisVisitId.toString())
            assertThat(it["bookingId"]).isEqualTo(bookingId.toString())
            assertThat(it["prisonId"]).isEqualTo(prisonId)
          },
          isNull(),
        )
      }
    }
  }

  @Nested
  @DisplayName("OFFENDER_OFFICIAL_VISIT_VISITORS-INSERTED")
  inner class OfficialVisitVisitorCreated {

    @Nested
    inner class WhenCreatedInNomis {
      @BeforeEach
      fun setUp() {
        officialVisitsOffenderEventsQueue.sendMessage(
          officialVisitVisitorEvent(
            eventType = "OFFENDER_OFFICIAL_VISIT_VISITORS-INSERTED",
            offenderNo = offenderNo,
            visitId = nomisVisitId,
            bookingId = bookingId,
            visitVisitorId = nomisVisitorId,
            personId = nomisPersonId,
          ),
        ).also { waitForAnyProcessingToComplete() }
      }

      @Test
      fun `will track telemetry`() {
        verify(telemetryClient).trackEvent(
          eq("officialvisits-visitor-synchronisation-created-success"),
          check {
            assertThat(it["offenderNo"]).isEqualTo(offenderNo)
            assertThat(it["nomisVisitId"]).isEqualTo(nomisVisitId.toString())
            assertThat(it["bookingId"]).isEqualTo(bookingId.toString())
            assertThat(it["nomisVisitorId"]).isEqualTo(nomisVisitorId.toString())
            assertThat(it["nomisPersonId"]).isEqualTo(nomisPersonId.toString())
          },
          isNull(),
        )
      }
    }

    @Nested
    inner class WhenPrisonerVisitorCreatedInNomis {
      @BeforeEach
      fun setUp() {
        officialVisitsOffenderEventsQueue.sendMessage(
          officialVisitVisitorEvent(
            eventType = "OFFENDER_OFFICIAL_VISIT_VISITORS-INSERTED",
            offenderNo = offenderNo,
            visitId = nomisVisitId,
            bookingId = bookingId,
            visitVisitorId = nomisVisitorId,
          ),
        ).also { waitForAnyProcessingToComplete() }
      }

      @Test
      fun `will track telemetry`() {
        verify(telemetryClient).trackEvent(
          eq("officialvisits-visitor-synchronisation-created-ignored"),
          check {
            assertThat(it["offenderNo"]).isEqualTo(offenderNo)
            assertThat(it["nomisVisitId"]).isEqualTo(nomisVisitId.toString())
            assertThat(it["bookingId"]).isEqualTo(bookingId.toString())
            assertThat(it["nomisVisitorId"]).isEqualTo(nomisVisitorId.toString())
          },
          isNull(),
        )
      }
    }
  }

  @Nested
  @DisplayName("OFFENDER_OFFICIAL_VISIT_VISITORS-UPDATED")
  inner class OfficialVisitVisitorUpdated {

    @Nested
    inner class WhenUpdatedInNomis {
      @BeforeEach
      fun setUp() {
        officialVisitsOffenderEventsQueue.sendMessage(
          officialVisitVisitorEvent(
            eventType = "OFFENDER_OFFICIAL_VISIT_VISITORS-UPDATED",
            offenderNo = offenderNo,
            visitId = nomisVisitId,
            bookingId = bookingId,
            visitVisitorId = nomisVisitorId,
            personId = nomisPersonId,
          ),
        ).also { waitForAnyProcessingToComplete() }
      }

      @Test
      fun `will track telemetry`() {
        verify(telemetryClient).trackEvent(
          eq("officialvisits-visitor-synchronisation-updated-success"),
          check {
            assertThat(it["offenderNo"]).isEqualTo(offenderNo)
            assertThat(it["nomisVisitId"]).isEqualTo(nomisVisitId.toString())
            assertThat(it["bookingId"]).isEqualTo(bookingId.toString())
            assertThat(it["nomisVisitorId"]).isEqualTo(nomisVisitorId.toString())
            assertThat(it["nomisPersonId"]).isEqualTo(nomisPersonId.toString())
          },
          isNull(),
        )
      }
    }

    @Nested
    inner class WhenPrisonerVisitorUpdatedInNomis {
      @BeforeEach
      fun setUp() {
        officialVisitsOffenderEventsQueue.sendMessage(
          officialVisitVisitorEvent(
            eventType = "OFFENDER_OFFICIAL_VISIT_VISITORS-UPDATED",
            offenderNo = offenderNo,
            visitId = nomisVisitId,
            bookingId = bookingId,
            visitVisitorId = nomisVisitorId,
          ),
        ).also { waitForAnyProcessingToComplete() }
      }

      @Test
      fun `will track telemetry`() {
        verify(telemetryClient).trackEvent(
          eq("officialvisits-visitor-synchronisation-updated-ignored"),
          check {
            assertThat(it["offenderNo"]).isEqualTo(offenderNo)
            assertThat(it["nomisVisitId"]).isEqualTo(nomisVisitId.toString())
            assertThat(it["bookingId"]).isEqualTo(bookingId.toString())
            assertThat(it["nomisVisitorId"]).isEqualTo(nomisVisitorId.toString())
          },
          isNull(),
        )
      }
    }
  }

  @Nested
  @DisplayName("OFFENDER_OFFICIAL_VISIT_VISITORS-DELETED")
  inner class OfficialVisitVisitorDeleted {

    @Nested
    inner class WhenDeletedFromNomis {
      @BeforeEach
      fun setUp() {
        officialVisitsOffenderEventsQueue.sendMessage(
          officialVisitVisitorEvent(
            eventType = "OFFENDER_OFFICIAL_VISIT_VISITORS-DELETED",
            offenderNo = offenderNo,
            visitId = nomisVisitId,
            bookingId = bookingId,
            visitVisitorId = nomisVisitorId,
            personId = nomisPersonId,
          ),
        ).also { waitForAnyProcessingToComplete() }
      }

      @Test
      fun `will track telemetry`() {
        verify(telemetryClient).trackEvent(
          eq("officialvisits-visitor-synchronisation-deleted-success"),
          check {
            assertThat(it["offenderNo"]).isEqualTo(offenderNo)
            assertThat(it["nomisVisitId"]).isEqualTo(nomisVisitId.toString())
            assertThat(it["bookingId"]).isEqualTo(bookingId.toString())
            assertThat(it["nomisVisitorId"]).isEqualTo(nomisVisitorId.toString())
            assertThat(it["nomisPersonId"]).isEqualTo(nomisPersonId.toString())
          },
          isNull(),
        )
      }
    }

    @Nested
    inner class WhenPrisonerVisitorDeletedFromNomis {
      @BeforeEach
      fun setUp() {
        officialVisitsOffenderEventsQueue.sendMessage(
          officialVisitVisitorEvent(
            eventType = "OFFENDER_OFFICIAL_VISIT_VISITORS-DELETED",
            offenderNo = offenderNo,
            visitId = nomisVisitId,
            bookingId = bookingId,
            visitVisitorId = nomisVisitorId,
          ),
        ).also { waitForAnyProcessingToComplete() }
      }

      @Test
      fun `will track telemetry`() {
        verify(telemetryClient).trackEvent(
          eq("officialvisits-visitor-synchronisation-deleted-ignored"),
          check {
            assertThat(it["offenderNo"]).isEqualTo(offenderNo)
            assertThat(it["nomisVisitId"]).isEqualTo(nomisVisitId.toString())
            assertThat(it["bookingId"]).isEqualTo(bookingId.toString())
            assertThat(it["nomisVisitorId"]).isEqualTo(nomisVisitorId.toString())
          },
          isNull(),
        )
      }
    }
  }
}

fun officialVisitEvent(
  eventType: String,
  visitId: Long,
  offenderNo: String,
  agencyLocationId: String,
  bookingId: Long,
  auditModuleName: String = "OIDUVISI",
) = // language=JSON
  """{
    "MessageId": "ae06c49e-1f41-4b9f-b2f2-dcca610d02cd", "Type": "Notification", "Timestamp": "2019-10-21T14:01:18.500Z", 
    "Message": "{\"eventType\":\"$eventType\",\"eventDatetime\":\"2019-10-21T15:00:25.489964\",\"offenderIdDisplay\": \"$offenderNo\", \"bookingId\": $bookingId, \"visitId\": $visitId,\"auditModuleName\":\"$auditModuleName\",\"agencyLocationId\": \"$agencyLocationId\",\"nomisEventType\":\"$eventType\" }",
    "TopicArn": "arn:aws:sns:eu-west-1:000000000000:offender_events", 
    "MessageAttributes": {
      "eventType": {"Type": "String", "Value": "$eventType"}, 
      "id": {"Type": "String", "Value": "8b07cbd9-0820-0a0f-c32f-a9429b618e0b"}, 
      "contentType": {"Type": "String", "Value": "text/plain;charset=UTF-8"}, 
      "timestamp": {"Type": "Number.java.lang.Long", "Value": "1571666478344"}
    }
}
  """.trimIndent()

fun officialVisitVisitorEvent(
  eventType: String,
  visitVisitorId: Long,
  visitId: Long,
  offenderNo: String,
  bookingId: Long,
  auditModuleName: String = "OIDUVISI",
) = // language=JSON
  """{
    "MessageId": "ae06c49e-1f41-4b9f-b2f2-dcca610d02cd", "Type": "Notification", "Timestamp": "2019-10-21T14:01:18.500Z", 
    "Message": "{\"eventType\":\"$eventType\",\"eventDatetime\":\"2019-10-21T15:00:25.489964\",\"offenderIdDisplay\": \"$offenderNo\", \"bookingId\": $bookingId, \"visitId\": $visitId,\"auditModuleName\":\"$auditModuleName\",\"visitVisitorId\": $visitVisitorId,\"nomisEventType\":\"$eventType\" }",
    "TopicArn": "arn:aws:sns:eu-west-1:000000000000:offender_events", 
    "MessageAttributes": {
      "eventType": {"Type": "String", "Value": "$eventType"}, 
      "id": {"Type": "String", "Value": "8b07cbd9-0820-0a0f-c32f-a9429b618e0b"}, 
      "contentType": {"Type": "String", "Value": "text/plain;charset=UTF-8"}, 
      "timestamp": {"Type": "Number.java.lang.Long", "Value": "1571666478344"}
    }
}
  """.trimIndent()

fun officialVisitVisitorEvent(
  eventType: String,
  visitVisitorId: Long,
  visitId: Long,
  offenderNo: String,
  personId: Long,
  bookingId: Long,
  auditModuleName: String = "OIDUVISI",
) = // language=JSON
  """{
    "MessageId": "ae06c49e-1f41-4b9f-b2f2-dcca610d02cd", "Type": "Notification", "Timestamp": "2019-10-21T14:01:18.500Z", 
    "Message": "{\"eventType\":\"$eventType\",\"eventDatetime\":\"2019-10-21T15:00:25.489964\",\"offenderIdDisplay\": \"$offenderNo\", \"bookingId\": $bookingId, \"personId\": $personId, \"visitId\": $visitId,\"auditModuleName\":\"$auditModuleName\",\"visitVisitorId\": $visitVisitorId,\"nomisEventType\":\"$eventType\" }",
    "TopicArn": "arn:aws:sns:eu-west-1:000000000000:offender_events", 
    "MessageAttributes": {
      "eventType": {"Type": "String", "Value": "$eventType"}, 
      "id": {"Type": "String", "Value": "8b07cbd9-0820-0a0f-c32f-a9429b618e0b"}, 
      "contentType": {"Type": "String", "Value": "text/plain;charset=UTF-8"}, 
      "timestamp": {"Type": "Number.java.lang.Long", "Value": "1571666478344"}
    }
}
  """.trimIndent()
