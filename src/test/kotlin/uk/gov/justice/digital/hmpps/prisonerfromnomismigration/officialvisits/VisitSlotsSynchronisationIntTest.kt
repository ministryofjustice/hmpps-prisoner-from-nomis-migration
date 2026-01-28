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
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomismappings.model.VisitTimeSlotMappingDto
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomisprisoner.api.VisitsConfigurationResourceApi
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomisprisoner.model.NomisAudit
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomisprisoner.model.VisitTimeSlotResponse
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.officialvisits.OfficialVisitsDpsApiExtension.Companion.dpsOfficialVisitsServer
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.officialvisits.OfficialVisitsDpsApiExtension.Companion.getRequestBody
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.officialvisits.OfficialVisitsDpsApiMockServer.Companion.syncTimeSlot
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.officialvisits.VisitSlotsNomisApiMockServer.Companion.visitTimeSlotResponse
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.officialvisits.model.DayType
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.officialvisits.model.SyncCreateTimeSlotRequest
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.wiremock.withRequestBodyJsonPath
import java.time.LocalDate
import java.time.LocalDateTime

class VisitSlotsSynchronisationIntTest : SqsIntegrationTestBase() {
  @Autowired
  private lateinit var nomisApiMock: VisitSlotsNomisApiMockServer

  private val dpsApiMock = dpsOfficialVisitsServer

  @Autowired
  private lateinit var mappingApiMock: VisitSlotsMappingApiMockServer

  val nomisTimeslotSequence = 2
  val nomisWeekDay = "MON"
  val prisonId = "MDI"
  val nomisAgencyVisitSlotId = 12345L

  @Nested
  @DisplayName("AGENCY_VISIT_TIMES-INSERTED")
  inner class AgencyVisitTimeCreated {

    @Nested
    inner class WhenCreatedInDps {
      @BeforeEach
      fun setUp() {
        officialVisitsOffenderEventsQueue.sendMessage(
          agencyVisitTimeEvent(
            eventType = "AGENCY_VISIT_TIMES-INSERTED",
            agencyLocationId = prisonId,
            timeslotSequence = nomisTimeslotSequence,
            weekDay = nomisWeekDay,
            auditModuleName = "DPS_SYNCHRONISATION_OFFICIAL_VISITS",
          ),
        ).also { waitForAnyProcessingToComplete() }
      }

      @Test
      fun `will track telemetry`() {
        verify(telemetryClient).trackEvent(
          eq("officialvisits-timeslot-synchronisation-created-skipped"),
          check {
            assertThat(it["prisonId"]).isEqualTo(prisonId)
            assertThat(it["nomisWeekDay"]).isEqualTo(nomisWeekDay)
            assertThat(it["nomisTimeslotSequence"]).isEqualTo(nomisTimeslotSequence.toString())
          },
          isNull(),
        )
      }
    }

    @Nested
    inner class WhenCreatedInNomis {
      @Nested
      inner class AlreadyExists {
        val dpsPrisonTimeSlotId = 123L

        @BeforeEach
        fun setUp() {
          mappingApiMock.stubGetTimeSlotByNomisIdsOrNull(
            nomisPrisonId = prisonId,
            nomisDayOfWeek = nomisWeekDay,
            nomisSlotSequence = nomisTimeslotSequence,
            mapping = VisitTimeSlotMappingDto(
              dpsId = dpsPrisonTimeSlotId.toString(),
              nomisPrisonId = prisonId,
              nomisDayOfWeek = nomisWeekDay,
              nomisSlotSequence = nomisTimeslotSequence,
              mappingType = VisitTimeSlotMappingDto.MappingType.NOMIS_CREATED,
            ),
          )
          officialVisitsOffenderEventsQueue.sendMessage(
            agencyVisitTimeEvent(
              eventType = "AGENCY_VISIT_TIMES-INSERTED",
              agencyLocationId = prisonId,
              timeslotSequence = nomisTimeslotSequence,
              weekDay = nomisWeekDay,
            ),
          ).also { waitForAnyProcessingToComplete() }
        }

        @Test
        fun `will track telemetry`() {
          verify(telemetryClient).trackEvent(
            eq("officialvisits-timeslot-synchronisation-created-ignored"),
            check {
              assertThat(it["prisonId"]).isEqualTo(prisonId)
              assertThat(it["nomisWeekDay"]).isEqualTo(nomisWeekDay)
              assertThat(it["nomisTimeslotSequence"]).isEqualTo(nomisTimeslotSequence.toString())
              assertThat(it["dpsPrisonTimeSlotId"]).isEqualTo(dpsPrisonTimeSlotId.toString())
            },
            isNull(),
          )
        }
      }

      @Nested
      inner class HappyPath {
        val dpsPrisonTimeSlotId = 123L

        @BeforeEach
        fun setUp() {
          mappingApiMock.stubGetTimeSlotByNomisIdsOrNull(
            nomisPrisonId = prisonId,
            nomisDayOfWeek = nomisWeekDay,
            nomisSlotSequence = nomisTimeslotSequence,
            mapping = null,
          )
          nomisApiMock.stubGetVisitTimeSlot(
            prisonId = prisonId,
            dayOfWeek = VisitsConfigurationResourceApi.DayOfWeekGetVisitTimeSlot.MON,
            timeSlotSequence = nomisTimeslotSequence,
            response = visitTimeSlotResponse().copy(
              timeSlotSequence = nomisTimeslotSequence,
              prisonId = prisonId,
              dayOfWeek = VisitTimeSlotResponse.DayOfWeek.MON,
              startTime = "14:00",
              endTime = "15:00",
              effectiveDate = LocalDate.parse("2021-01-01"),
              expiryDate = LocalDate.parse("2031-01-01"),
              audit = NomisAudit(
                createDatetime = LocalDateTime.parse("2021-01-01T10:30"),
                createUsername = "T.SMITH",
              ),
            ),
          )
          dpsApiMock.stubCreateTimeSlot(syncTimeSlot().copy(prisonTimeSlotId = dpsPrisonTimeSlotId))
          mappingApiMock.stubCreateTimeSlotMapping()

          officialVisitsOffenderEventsQueue.sendMessage(
            agencyVisitTimeEvent(
              eventType = "AGENCY_VISIT_TIMES-INSERTED",
              agencyLocationId = prisonId,
              timeslotSequence = nomisTimeslotSequence,
              weekDay = nomisWeekDay,
            ),
          ).also { waitForAnyProcessingToComplete() }
        }

        @Test
        fun `will retrieve the NOMIS time slot details`() {
          nomisApiMock.verify(getRequestedFor(urlPathEqualTo("/visits/configuration/time-slots/prison-id/$prisonId/day-of-week/$nomisWeekDay/time-slot-sequence/$nomisTimeslotSequence")))
        }

        @Test
        fun `will create time slot in DPS`() {
          val request: SyncCreateTimeSlotRequest = getRequestBody(postRequestedFor(urlPathEqualTo("/sync/time-slot")))
          with(request) {
            assertThat(this.prisonCode).isEqualTo(prisonId)
            assertThat(this.dayCode).isEqualTo(DayType.MON)
            assertThat(this.startTime).isEqualTo("14:00")
            assertThat(this.endTime).isEqualTo("15:00")
            assertThat(this.effectiveDate).isEqualTo(LocalDate.parse("2021-01-01"))
            assertThat(this.expiryDate).isEqualTo(LocalDate.parse("2031-01-01"))
            assertThat(this.createdTime).isEqualTo(LocalDateTime.parse("2021-01-01T10:30"))
            assertThat(this.createdBy).isEqualTo("T.SMITH")
          }
        }

        @Test
        fun `will create mapping`() {
          mappingApiMock.verify(
            postRequestedFor(urlPathEqualTo("/mapping/visit-slots/time-slots"))
              .withRequestBodyJsonPath("dpsId", dpsPrisonTimeSlotId.toString())
              .withRequestBodyJsonPath("nomisPrisonId", prisonId)
              .withRequestBodyJsonPath("nomisDayOfWeek", nomisWeekDay)
              .withRequestBodyJsonPath("nomisSlotSequence", nomisTimeslotSequence)
              .withRequestBodyJsonPath("mappingType", "NOMIS_CREATED"),
          )
        }

        @Test
        fun `will track telemetry`() {
          verify(telemetryClient).trackEvent(
            eq("officialvisits-timeslot-synchronisation-created-success"),
            check {
              assertThat(it["prisonId"]).isEqualTo(prisonId)
              assertThat(it["nomisWeekDay"]).isEqualTo(nomisWeekDay)
              assertThat(it["nomisTimeslotSequence"]).isEqualTo(nomisTimeslotSequence.toString())
              assertThat(it["dpsPrisonTimeSlotId"]).isEqualTo(dpsPrisonTimeSlotId.toString())
            },
            isNull(),
          )
        }
      }

      @Nested
      inner class MappingFailures {
        val dpsPrisonTimeSlotId = 123L

        @BeforeEach
        fun setUp() {
          mappingApiMock.stubGetTimeSlotByNomisIdsOrNull(
            nomisPrisonId = prisonId,
            nomisDayOfWeek = nomisWeekDay,
            nomisSlotSequence = nomisTimeslotSequence,
            mapping = null,
          )
          nomisApiMock.stubGetVisitTimeSlot(
            prisonId = prisonId,
            dayOfWeek = VisitsConfigurationResourceApi.DayOfWeekGetVisitTimeSlot.MON,
            timeSlotSequence = nomisTimeslotSequence,
            response = visitTimeSlotResponse(),
          )
          dpsApiMock.stubCreateTimeSlot(syncTimeSlot().copy(prisonTimeSlotId = dpsPrisonTimeSlotId))
        }

        @Nested
        inner class FailureAndRecovery {
          @BeforeEach
          fun setUp() {
            mappingApiMock.stubCreateTimeSlotMappingFailureFollowedBySuccess()

            officialVisitsOffenderEventsQueue.sendMessage(
              agencyVisitTimeEvent(
                eventType = "AGENCY_VISIT_TIMES-INSERTED",
                agencyLocationId = prisonId,
                timeslotSequence = nomisTimeslotSequence,
                weekDay = nomisWeekDay,
              ),
            ).also { waitForAnyProcessingToComplete("officialvisits-timeslot-mapping-synchronisation-created") }
          }

          @Test
          fun `will create time slot once in DPS`() {
            dpsApiMock.verify(1, postRequestedFor(urlPathEqualTo("/sync/time-slot")))
          }

          @Test
          fun `will eventually create mapping after a retry`() {
            mappingApiMock.verify(2, postRequestedFor(urlPathEqualTo("/mapping/visit-slots/time-slots")))
          }

          @Test
          fun `will track telemetry`() {
            verify(telemetryClient).trackEvent(
              eq("officialvisits-timeslot-synchronisation-created-success"),
              check {
                assertThat(it["prisonId"]).isEqualTo(prisonId)
                assertThat(it["nomisWeekDay"]).isEqualTo(nomisWeekDay)
                assertThat(it["nomisTimeslotSequence"]).isEqualTo(nomisTimeslotSequence.toString())
              },
              isNull(),
            )

            verify(telemetryClient).trackEvent(
              eq("officialvisits-timeslot-mapping-synchronisation-created"),
              check {
                assertThat(it["prisonId"]).isEqualTo(prisonId)
                assertThat(it["nomisWeekDay"]).isEqualTo(nomisWeekDay)
                assertThat(it["nomisTimeslotSequence"]).isEqualTo(nomisTimeslotSequence.toString())
              },
              isNull(),
            )
          }
        }

        @Nested
        inner class DuplicateDetected {
          @BeforeEach
          fun setUp() {
            mappingApiMock.stubCreateTimeSlotMapping(
              error = DuplicateMappingErrorResponse(
                moreInfo = DuplicateErrorContentObject(
                  duplicate = VisitTimeSlotMappingDto(
                    dpsId = dpsPrisonTimeSlotId.toString(),
                    nomisPrisonId = prisonId,
                    nomisDayOfWeek = nomisWeekDay,
                    nomisSlotSequence = nomisTimeslotSequence,
                    mappingType = VisitTimeSlotMappingDto.MappingType.NOMIS_CREATED,
                  ),
                  existing = VisitTimeSlotMappingDto(
                    dpsId = "98765",
                    nomisPrisonId = prisonId,
                    nomisDayOfWeek = nomisWeekDay,
                    nomisSlotSequence = nomisTimeslotSequence,
                    mappingType = VisitTimeSlotMappingDto.MappingType.NOMIS_CREATED,
                  ),
                ),
                errorCode = 1409,
                status = DuplicateMappingErrorResponse.Status._409_CONFLICT,
                userMessage = "Duplicate mapping",
              ),
            )

            officialVisitsOffenderEventsQueue.sendMessage(
              agencyVisitTimeEvent(
                eventType = "AGENCY_VISIT_TIMES-INSERTED",
                agencyLocationId = prisonId,
                timeslotSequence = nomisTimeslotSequence,
                weekDay = nomisWeekDay,
              ),
            ).also { waitForAnyProcessingToComplete("from-nomis-sync-officialvisits-duplicate") }
          }

          @Test
          fun `will create time slot once in DPS`() {
            dpsApiMock.verify(1, postRequestedFor(urlPathEqualTo("/sync/time-slot")))
          }

          @Test
          fun `will not bother retrying mapping`() {
            mappingApiMock.verify(1, postRequestedFor(urlPathEqualTo("/mapping/visit-slots/time-slots")))
          }

          @Test
          fun `will track telemetry`() {
            verify(telemetryClient).trackEvent(
              eq("officialvisits-timeslot-synchronisation-created-success"),
              check {
                assertThat(it["prisonId"]).isEqualTo(prisonId)
                assertThat(it["nomisWeekDay"]).isEqualTo(nomisWeekDay)
                assertThat(it["nomisTimeslotSequence"]).isEqualTo(nomisTimeslotSequence.toString())
              },
              isNull(),
            )

            verify(telemetryClient).trackEvent(
              eq("from-nomis-sync-officialvisits-duplicate"),
              check {
                assertThat(it["duplicateNomisPrisonId"]).isEqualTo(prisonId)
                assertThat(it["duplicateNomisDayOfWeek"]).isEqualTo(nomisWeekDay)
                assertThat(it["duplicateNomisSlotSequence"]).isEqualTo(nomisTimeslotSequence.toString())
              },
              isNull(),
            )
          }
        }
      }
    }
  }

  @Nested
  @DisplayName("AGENCY_VISIT_TIMES-UPDATED")
  inner class AgencyVisitTimeUpdated {

    @Nested
    inner class WhenUpdatedInNomis {
      @BeforeEach
      fun setUp() {
        officialVisitsOffenderEventsQueue.sendMessage(
          agencyVisitTimeEvent(
            eventType = "AGENCY_VISIT_TIMES-UPDATED",
            agencyLocationId = prisonId,
            timeslotSequence = nomisTimeslotSequence,
            weekDay = nomisWeekDay,
          ),
        ).also { waitForAnyProcessingToComplete() }
      }

      @Test
      fun `will track telemetry`() {
        verify(telemetryClient).trackEvent(
          eq("officialvisits-timeslot-synchronisation-updated-success"),
          check {
            assertThat(it["prisonId"]).isEqualTo(prisonId)
            assertThat(it["nomisWeekDay"]).isEqualTo(nomisWeekDay)
            assertThat(it["nomisTimeslotSequence"]).isEqualTo(nomisTimeslotSequence.toString())
          },
          isNull(),
        )
      }
    }
  }

  @Nested
  @DisplayName("AGENCY_VISIT_TIMES-DELETED")
  inner class AgencyVisitTimeDeleted {

    @Nested
    inner class WhenDeletedInNomis {
      @BeforeEach
      fun setUp() {
        officialVisitsOffenderEventsQueue.sendMessage(
          agencyVisitTimeEvent(
            eventType = "AGENCY_VISIT_TIMES-DELETED",
            agencyLocationId = prisonId,
            timeslotSequence = nomisTimeslotSequence,
            weekDay = nomisWeekDay,
          ),
        ).also { waitForAnyProcessingToComplete() }
      }

      @Test
      fun `will track telemetry`() {
        verify(telemetryClient).trackEvent(
          eq("officialvisits-timeslot-synchronisation-deleted-success"),
          check {
            assertThat(it["prisonId"]).isEqualTo(prisonId)
            assertThat(it["nomisWeekDay"]).isEqualTo(nomisWeekDay)
            assertThat(it["nomisTimeslotSequence"]).isEqualTo(nomisTimeslotSequence.toString())
          },
          isNull(),
        )
      }
    }
  }

  @Nested
  @DisplayName("AGENCY_VISIT_SLOTS-INSERTED")
  inner class AgencyVisitSlotCreated {

    @Nested
    inner class WhenCreatedInNomis {
      @BeforeEach
      fun setUp() {
        officialVisitsOffenderEventsQueue.sendMessage(
          agencyVisitSlotEvent(
            eventType = "AGENCY_VISIT_SLOTS-INSERTED",
            agencyVisitSlotId = nomisAgencyVisitSlotId,
            agencyLocationId = prisonId,
            timeslotSequence = nomisTimeslotSequence,
            weekDay = nomisWeekDay,
          ),
        ).also { waitForAnyProcessingToComplete() }
      }

      @Test
      fun `will track telemetry`() {
        verify(telemetryClient).trackEvent(
          eq("officialvisits-visitslot-synchronisation-created-success"),
          check {
            assertThat(it["prisonId"]).isEqualTo(prisonId)
            assertThat(it["nomisWeekDay"]).isEqualTo(nomisWeekDay)
            assertThat(it["nomisTimeslotSequence"]).isEqualTo(nomisTimeslotSequence.toString())
            assertThat(it["nomisAgencyVisitSlotId"]).isEqualTo(nomisAgencyVisitSlotId.toString())
          },
          isNull(),
        )
      }
    }
  }

  @Nested
  @DisplayName("AGENCY_VISIT_SLOTS-UPDATED")
  inner class AgencyVisitSlotUpdated {

    @Nested
    inner class WhenUpdatedInNomis {
      @BeforeEach
      fun setUp() {
        officialVisitsOffenderEventsQueue.sendMessage(
          agencyVisitSlotEvent(
            eventType = "AGENCY_VISIT_SLOTS-UPDATED",
            agencyVisitSlotId = nomisAgencyVisitSlotId,
            agencyLocationId = prisonId,
            timeslotSequence = nomisTimeslotSequence,
            weekDay = nomisWeekDay,
          ),
        ).also { waitForAnyProcessingToComplete() }
      }

      @Test
      fun `will track telemetry`() {
        verify(telemetryClient).trackEvent(
          eq("officialvisits-visitslot-synchronisation-updated-success"),
          check {
            assertThat(it["prisonId"]).isEqualTo(prisonId)
            assertThat(it["nomisWeekDay"]).isEqualTo(nomisWeekDay)
            assertThat(it["nomisTimeslotSequence"]).isEqualTo(nomisTimeslotSequence.toString())
            assertThat(it["nomisAgencyVisitSlotId"]).isEqualTo(nomisAgencyVisitSlotId.toString())
          },
          isNull(),
        )
      }
    }
  }

  @Nested
  @DisplayName("AGENCY_VISIT_SLOTS-DELETED")
  inner class AgencyVisitSlotDeleted {

    @Nested
    inner class WhenDeletedInNomis {
      @BeforeEach
      fun setUp() {
        officialVisitsOffenderEventsQueue.sendMessage(
          agencyVisitSlotEvent(
            eventType = "AGENCY_VISIT_SLOTS-DELETED",
            agencyVisitSlotId = nomisAgencyVisitSlotId,
            agencyLocationId = prisonId,
            timeslotSequence = nomisTimeslotSequence,
            weekDay = nomisWeekDay,
          ),
        ).also { waitForAnyProcessingToComplete() }
      }

      @Test
      fun `will track telemetry`() {
        verify(telemetryClient).trackEvent(
          eq("officialvisits-visitslot-synchronisation-deleted-success"),
          check {
            assertThat(it["prisonId"]).isEqualTo(prisonId)
            assertThat(it["nomisWeekDay"]).isEqualTo(nomisWeekDay)
            assertThat(it["nomisTimeslotSequence"]).isEqualTo(nomisTimeslotSequence.toString())
            assertThat(it["nomisAgencyVisitSlotId"]).isEqualTo(nomisAgencyVisitSlotId.toString())
          },
          isNull(),
        )
      }
    }
  }
}

fun agencyVisitSlotEvent(
  eventType: String,
  agencyVisitSlotId: Long,
  agencyLocationId: String,
  timeslotSequence: Int,
  weekDay: String,
  auditModuleName: String = "OIMVDTSL",
) = // language=JSON
  """{
    "MessageId": "ae06c49e-1f41-4b9f-b2f2-dcca610d02cd", "Type": "Notification", "Timestamp": "2019-10-21T14:01:18.500Z", 
    "Message": "{\"eventType\":\"$eventType\",\"eventDatetime\":\"2019-10-21T15:00:25.489964\", \"agencyVisitSlotId\": $agencyVisitSlotId,  \"timeslotSequence\": $timeslotSequence, \"weekDay\": \"$weekDay\",\"auditModuleName\":\"$auditModuleName\",\"agencyLocationId\": \"$agencyLocationId\",\"nomisEventType\":\"$eventType\" }",
    "TopicArn": "arn:aws:sns:eu-west-1:000000000000:offender_events", 
    "MessageAttributes": {
      "eventType": {"Type": "String", "Value": "$eventType"}, 
      "id": {"Type": "String", "Value": "8b07cbd9-0820-0a0f-c32f-a9429b618e0b"}, 
      "contentType": {"Type": "String", "Value": "text/plain;charset=UTF-8"}, 
      "timestamp": {"Type": "Number.java.lang.Long", "Value": "1571666478344"}
    }
}
  """.trimIndent()
fun agencyVisitTimeEvent(
  eventType: String,
  agencyLocationId: String,
  timeslotSequence: Int,
  weekDay: String,
  auditModuleName: String = "OIMVDTSL",
) = // language=JSON
  """{
    "MessageId": "ae06c49e-1f41-4b9f-b2f2-dcca610d02cd", "Type": "Notification", "Timestamp": "2019-10-21T14:01:18.500Z", 
    "Message": "{\"eventType\":\"$eventType\",\"eventDatetime\":\"2019-10-21T15:00:25.489964\", \"timeslotSequence\": $timeslotSequence, \"weekDay\": \"$weekDay\",\"auditModuleName\":\"$auditModuleName\",\"agencyLocationId\": \"$agencyLocationId\",\"nomisEventType\":\"$eventType\" }",
    "TopicArn": "arn:aws:sns:eu-west-1:000000000000:offender_events", 
    "MessageAttributes": {
      "eventType": {"Type": "String", "Value": "$eventType"}, 
      "id": {"Type": "String", "Value": "8b07cbd9-0820-0a0f-c32f-a9429b618e0b"}, 
      "contentType": {"Type": "String", "Value": "text/plain;charset=UTF-8"}, 
      "timestamp": {"Type": "Number.java.lang.Long", "Value": "1571666478344"}
    }
}
  """.trimIndent()
