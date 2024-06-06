package uk.gov.justice.digital.hmpps.prisonerfromnomismigration.alerts

import com.github.tomakehurst.wiremock.client.WireMock.anyUrl
import com.github.tomakehurst.wiremock.client.WireMock.deleteRequestedFor
import com.github.tomakehurst.wiremock.client.WireMock.equalTo
import com.github.tomakehurst.wiremock.client.WireMock.exactly
import com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor
import com.github.tomakehurst.wiremock.client.WireMock.matchingJsonPath
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
import org.mockito.Mockito.atLeastOnce
import org.mockito.Mockito.eq
import org.mockito.Mockito.times
import org.mockito.kotlin.any
import org.mockito.kotlin.check
import org.mockito.kotlin.isNull
import org.mockito.kotlin.verify
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.http.HttpStatus.INTERNAL_SERVER_ERROR
import org.springframework.http.HttpStatus.NOT_FOUND
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.alerts.AlertsDpsApiExtension.Companion.dpsAlertsServer
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.alerts.AlertsDpsApiMockServer.Companion.dpsAlert
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.alerts.AlertsDpsApiMockServer.Companion.mergedAlert
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.helper.mergeDomainEvent
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.integration.SqsIntegrationTestBase
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.integration.sendMessage
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomismappings.model.AlertMappingDto
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomismappings.model.AlertMappingDto.MappingType.MIGRATED
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomismappings.model.AlertMappingDto.MappingType.NOMIS_CREATED
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomismappings.model.DuplicateErrorContentObject
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomismappings.model.DuplicateMappingErrorResponse
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomissync.model.AlertResponse
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomissync.model.CodeDescription
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomissync.model.NomisAudit
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.wiremock.withRequestBodyJsonPath
import uk.gov.justice.hmpps.sqs.countAllMessagesOnQueue
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.AbstractMap.SimpleEntry
import java.util.UUID

private const val BOOKING_ID = 1234L
private const val ALERT_SEQUENCE = 1L
private const val OFFENDER_ID_DISPLAY = "A3864DZ"

class AlertsSynchronisationIntTest : SqsIntegrationTestBase() {
  @Autowired
  private lateinit var alertsNomisApiMockServer: AlertsNomisApiMockServer

  @Autowired
  private lateinit var alertsMappingApiMockServer: AlertsMappingApiMockServer

  @Nested
  @DisplayName("ALERT-INSERTED")
  inner class AlertInserted {

    @Nested
    @DisplayName("When alert was created in DPS")
    inner class DPSCreated {
      private val bookingId = 12345L
      private val alertSequence = 3L

      @BeforeEach
      fun setUp() {
        alertsNomisApiMockServer.stubGetAlert(
          bookingId = bookingId,
          alertSequence = alertSequence,
          alert = alert(bookingId = bookingId, alertSequence = alertSequence).copy(
            audit = alert().audit.copy(
              auditModuleName = "DPS_SYNCHRONISATION",
            ),
          ),
        )
        awsSqsAlertOffenderEventsClient.sendMessage(
          alertsQueueOffenderEventsUrl,
          alertEvent(
            eventType = "ALERT-INSERTED",
            bookingId = bookingId,
            alertSequence = alertSequence,
            offenderNo = "A3864DZ",
          ),
        )
      }

      @Test
      fun `the event is ignored`() {
        await untilAsserted {
          verify(telemetryClient).trackEvent(
            eq("alert-synchronisation-created-skipped"),
            check {
              assertThat(it["offenderNo"]).isEqualTo("A3864DZ")
              assertThat(it["bookingId"]).isEqualTo(bookingId.toString())
              assertThat(it["alertSequence"]).isEqualTo(alertSequence.toString())
            },
            isNull(),
          )
        }

        // will not bother getting mapping
        alertsMappingApiMockServer.verify(
          count = 0,
          getRequestedFor(urlPathMatching("/mapping/alerts/nomis-booking-id/\\d+/nomis-alert-sequence/\\d+")),
        )
        // will not create an alert in DPS
        dpsAlertsServer.verify(0, postRequestedFor(anyUrl()))
      }
    }

    @Nested
    @DisplayName("When alert was created in NOMIS")
    inner class NomisCreated {
      private val bookingId = 12345L
      private val alertSequence = 3L
      private val offenderNo = "A3864DZ"

      @BeforeEach
      fun setUp() {
        alertsNomisApiMockServer.stubGetAlert(
          bookingId = bookingId,
          alertSequence = alertSequence,
          alert = alert(bookingId = bookingId, alertSequence = alertSequence).copy(
            alertCode = CodeDescription("XNR", "Not For Release"),
            type = CodeDescription("X", "Security"),
            audit = alert().audit.copy(
              auditModuleName = "OCDALERT",
            ),
          ),
        )
      }

      @Nested
      @DisplayName("When mapping does not exist yet")
      inner class NoMapping {
        private val dpsAlertId = "a04f7a8d-61aa-400c-9395-f4dc62f36ab0"

        @BeforeEach
        fun setUp() {
          alertsMappingApiMockServer.stubGetByNomisId(status = NOT_FOUND)
          dpsAlertsServer.stubPostAlert(dpsAlert().copy(alertUuid = UUID.fromString(dpsAlertId)))
          alertsMappingApiMockServer.stubPostMapping()
          awsSqsAlertOffenderEventsClient.sendMessage(
            alertsQueueOffenderEventsUrl,
            alertEvent(
              eventType = "ALERT-INSERTED",
              bookingId = bookingId,
              alertSequence = alertSequence,
              offenderNo = offenderNo,
            ),
          )
        }

        @Test
        fun `will create alert in DPS`() {
          await untilAsserted {
            dpsAlertsServer.verify(
              postRequestedFor(urlPathEqualTo("/alerts"))
                .withRequestBody(matchingJsonPath("alertCode", equalTo("XNR"))),
            )
          }
        }

        @Test
        fun `will create mapping between DPS and NOMIS ids`() {
          await untilAsserted {
            alertsMappingApiMockServer.verify(
              postRequestedFor(urlPathEqualTo("/mapping/alerts"))
                .withRequestBody(matchingJsonPath("dpsAlertId", equalTo(dpsAlertId)))
                .withRequestBody(matchingJsonPath("nomisBookingId", equalTo(bookingId.toString())))
                .withRequestBody(matchingJsonPath("offenderNo", equalTo(offenderNo)))
                .withRequestBody(matchingJsonPath("nomisAlertSequence", equalTo(alertSequence.toString())))
                .withRequestBody(matchingJsonPath("mappingType", equalTo("NOMIS_CREATED"))),
            )
          }
        }

        @Test
        fun `will track a telemetry event for success`() {
          await untilAsserted {
            verify(telemetryClient).trackEvent(
              eq("alert-synchronisation-created-success"),
              check {
                assertThat(it["offenderNo"]).isEqualTo("A3864DZ")
                assertThat(it["bookingId"]).isEqualTo(bookingId.toString())
                assertThat(it["alertSequence"]).isEqualTo(alertSequence.toString())
                assertThat(it["dpsAlertId"]).isEqualTo(dpsAlertId)
                assertThat(it).doesNotContain(SimpleEntry("mapping", "initial-failure"))
              },
              isNull(),
            )
          }
        }
      }

      @Nested
      @DisplayName("When mapping already existS")
      inner class MappingExists {
        private val dpsAlertId = "a04f7a8d-61aa-400c-9395-f4dc62f36ab0"

        @BeforeEach
        fun setUp() {
          alertsMappingApiMockServer.stubGetByNomisId(
            bookingId = bookingId,
            alertSequence = alertSequence,
            AlertMappingDto(
              nomisBookingId = bookingId,
              nomisAlertSequence = alertSequence,
              dpsAlertId = dpsAlertId,
              offenderNo = "A1234KT",
              mappingType = MIGRATED,
            ),
          )
          awsSqsAlertOffenderEventsClient.sendMessage(
            alertsQueueOffenderEventsUrl,
            alertEvent(
              eventType = "ALERT-INSERTED",
              bookingId = bookingId,
              alertSequence = alertSequence,
              offenderNo = offenderNo,
            ),
          )
        }

        @Test
        fun `the event is ignored`() {
          await untilAsserted {
            verify(telemetryClient).trackEvent(
              eq("alert-synchronisation-created-ignored"),
              check {
                assertThat(it["offenderNo"]).isEqualTo("A3864DZ")
                assertThat(it["bookingId"]).isEqualTo(bookingId.toString())
                assertThat(it["alertSequence"]).isEqualTo(alertSequence.toString())
                assertThat(it["dpsAlertId"]).isEqualTo(dpsAlertId)
              },
              isNull(),
            )
          }
          // will not create an alert in DPS
          dpsAlertsServer.verify(0, postRequestedFor(anyUrl()))
        }
      }

      @Nested
      @DisplayName("When mapping POST fails")
      inner class MappingFail {
        private val dpsAlertId = "a04f7a8d-61aa-400c-9395-f4dc62f36ab0"

        @BeforeEach
        fun setUp() {
          alertsMappingApiMockServer.stubGetByNomisId(status = NOT_FOUND)
          dpsAlertsServer.stubPostAlert(dpsAlert().copy(alertUuid = UUID.fromString(dpsAlertId)))
        }

        @Nested
        @DisplayName("Fails once")
        inner class FailsOnce {
          @BeforeEach
          fun setUp() {
            alertsMappingApiMockServer.stubPostMappingFailureFollowedBySuccess()
            awsSqsAlertOffenderEventsClient.sendMessage(
              alertsQueueOffenderEventsUrl,
              alertEvent(
                eventType = "ALERT-INSERTED",
                bookingId = bookingId,
                alertSequence = alertSequence,
                offenderNo = offenderNo,
              ),
            )
          }

          @Test
          fun `will create alert in DPS`() {
            await untilAsserted {
              dpsAlertsServer.verify(
                postRequestedFor(urlPathEqualTo("/alerts"))
                  .withRequestBody(matchingJsonPath("alertCode", equalTo("XNR"))),
              )
            }
          }

          @Test
          fun `will attempt to create mapping two times and succeed`() {
            await untilAsserted {
              alertsMappingApiMockServer.verify(
                exactly(2),
                postRequestedFor(urlPathEqualTo("/mapping/alerts"))
                  .withRequestBody(matchingJsonPath("dpsAlertId", equalTo(dpsAlertId)))
                  .withRequestBody(matchingJsonPath("nomisBookingId", equalTo(bookingId.toString())))
                  .withRequestBody(matchingJsonPath("nomisAlertSequence", equalTo(alertSequence.toString())))
                  .withRequestBody(matchingJsonPath("mappingType", equalTo("NOMIS_CREATED"))),
              )
            }

            assertThat(
              awsSqsAlertsOffenderEventDlqClient.countAllMessagesOnQueue(alertsQueueOffenderEventsDlqUrl).get(),
            ).isEqualTo(0)
          }

          @Test
          fun `will track a telemetry event for partial success`() {
            await untilAsserted {
              verify(telemetryClient).trackEvent(
                eq("alert-synchronisation-created-success"),
                check {
                  assertThat(it["offenderNo"]).isEqualTo("A3864DZ")
                  assertThat(it["bookingId"]).isEqualTo(bookingId.toString())
                  assertThat(it["alertSequence"]).isEqualTo(alertSequence.toString())
                  assertThat(it["dpsAlertId"]).isEqualTo(dpsAlertId)
                  assertThat(it["mapping"]).isEqualTo("initial-failure")
                },
                isNull(),
              )
            }

            await untilAsserted {
              verify(telemetryClient).trackEvent(
                eq("alert-mapping-created-synchronisation-success"),
                check {
                  assertThat(it["offenderNo"]).isEqualTo("A3864DZ")
                  assertThat(it["bookingId"]).isEqualTo(bookingId.toString())
                  assertThat(it["alertSequence"]).isEqualTo(alertSequence.toString())
                },
                isNull(),
              )
            }
          }
        }

        @Nested
        @DisplayName("Fails constantly")
        inner class FailsConstantly {
          @BeforeEach
          fun setUp() {
            alertsMappingApiMockServer.stubPostMapping(status = INTERNAL_SERVER_ERROR)
            awsSqsAlertOffenderEventsClient.sendMessage(
              alertsQueueOffenderEventsUrl,
              alertEvent(
                eventType = "ALERT-INSERTED",
                bookingId = bookingId,
                alertSequence = alertSequence,
                offenderNo = offenderNo,
              ),
            )
            await untilCallTo {
              awsSqsAlertsOffenderEventDlqClient.countAllMessagesOnQueue(alertsQueueOffenderEventsDlqUrl).get()
            } matches { it == 1 }
          }

          @Test
          fun `will create alert in DPS`() {
            await untilAsserted {
              dpsAlertsServer.verify(
                1,
                postRequestedFor(urlPathEqualTo("/alerts"))
                  .withRequestBody(matchingJsonPath("alertCode", equalTo("XNR"))),
              )
            }
          }

          @Test
          fun `will attempt to create mapping several times and keep failing`() {
            alertsMappingApiMockServer.verify(
              exactly(3),
              postRequestedFor(urlPathEqualTo("/mapping/alerts")),
            )
          }

          @Test
          fun `will track a telemetry event for success`() {
            await untilAsserted {
              verify(telemetryClient).trackEvent(
                eq("alert-synchronisation-created-success"),
                check {
                  assertThat(it["offenderNo"]).isEqualTo("A3864DZ")
                  assertThat(it["bookingId"]).isEqualTo(bookingId.toString())
                  assertThat(it["alertSequence"]).isEqualTo(alertSequence.toString())
                  assertThat(it["dpsAlertId"]).isEqualTo(dpsAlertId)
                  assertThat(it["mapping"]).isEqualTo("initial-failure")
                },
                isNull(),
              )
            }
          }
        }
      }
    }

    @Nested
    @DisplayName("When alert was created in NOMIS but on previous booking")
    inner class NomisCreatedOnPreviousBooking {
      private val bookingId = 12345L
      private val alertSequence = 3L
      private val offenderNo = "A3864DZ"

      @BeforeEach
      fun setUp() {
        alertsMappingApiMockServer.stubGetByNomisId(status = NOT_FOUND)
        alertsNomisApiMockServer.stubGetAlert(
          bookingId = bookingId,
          alertSequence = alertSequence,
          alert = alert(bookingId = bookingId, alertSequence = alertSequence).copy(
            bookingSequence = 2,
            alertCode = CodeDescription("XNR", "Not For Release"),
            type = CodeDescription("X", "Security"),
            audit = alert().audit.copy(
              auditModuleName = "OCDALERT",
            ),
          ),
        )
        awsSqsAlertOffenderEventsClient.sendMessage(
          alertsQueueOffenderEventsUrl,
          alertEvent(
            eventType = "ALERT-INSERTED",
            bookingId = bookingId,
            alertSequence = alertSequence,
            offenderNo = offenderNo,
          ),
        ).also { waitForAnyProcessingToComplete() }
      }

      @Nested
      inner class WhenIrrelevantAlert {
        @Test
        fun `will not create alert in DPS`() {
          dpsAlertsServer.verify(
            0,
            postRequestedFor(urlPathEqualTo("/alerts")),
          )
        }

        @Test
        fun `will track a telemetry event for ignore`() {
          await untilAsserted {
            verify(telemetryClient).trackEvent(
              eq("alert-synchronisation-created-ignored-previous-booking"),
              check {
                assertThat(it["offenderNo"]).isEqualTo("A3864DZ")
                assertThat(it["bookingId"]).isEqualTo(bookingId.toString())
                assertThat(it["alertSequence"]).isEqualTo(alertSequence.toString())
              },
              isNull(),
            )
          }
        }
      }
    }

    @Nested
    @DisplayName("When alert was created in NOMIS due to a new booking")
    inner class NewBookingCreatedInNomis {
      private val bookingId = 12345L
      private val previousBookingId = 5000L
      private val alertSequence = 3L
      private val offenderNo = "A3864DZ"

      @BeforeEach
      fun setUp() {
        alertsNomisApiMockServer.stubGetAlert(
          bookingId = bookingId,
          alertSequence = alertSequence,
          alert = alert(bookingId = bookingId, alertSequence = alertSequence).copy(
            alertCode = CodeDescription("XNR", "Not For Release"),
            type = CodeDescription("X", "Security"),
            audit = alert().audit.copy(
              auditModuleName = "OIDADMIS",
              auditAdditionalInfo = "OMKCOPY.COPY_BOOKING_DATA",
            ),
          ),
        )
      }

      @Nested
      inner class HappyPath {
        private val dpsAlertId = "a04f7a8d-61aa-400c-9395-f4dc62f36ab0"

        @BeforeEach
        fun setUp() {
          alertsNomisApiMockServer.stubGetPreviousBooking(offenderNo, bookingId, previousBookingId)
          alertsMappingApiMockServer.stubGetByNomisId(bookingId = bookingId, alertSequence = alertSequence)
          alertsMappingApiMockServer.stubUpdateByNomisId(previousBookingId = previousBookingId, alertSequence = alertSequence, newBookingId = bookingId, dpsAlertId = dpsAlertId)
          awsSqsAlertOffenderEventsClient.sendMessage(
            alertsQueueOffenderEventsUrl,
            alertEvent(
              eventType = "ALERT-INSERTED",
              bookingId = bookingId,
              alertSequence = alertSequence,
              offenderNo = offenderNo,
            ),
          )
        }

        @Test
        fun `will retrieve the alert from NOMIS`() {
          await untilAsserted {
            alertsNomisApiMockServer.verify(
              getRequestedFor(urlPathEqualTo("/prisoners/booking-id/$bookingId/alerts/$alertSequence")),
            )
          }
        }

        @Test
        fun `will retrieve the previous booking id`() {
          await untilAsserted {
            alertsNomisApiMockServer.verify(
              getRequestedFor(urlPathEqualTo("/prisoners/$offenderNo/bookings/$bookingId/previous")),
            )
          }
        }

        @Test
        fun `will update the mapping`() {
          await untilAsserted {
            alertsMappingApiMockServer.verify(
              putRequestedFor(urlPathEqualTo("/mapping/alerts/nomis-booking-id/$previousBookingId/nomis-alert-sequence/$alertSequence"))
                .withRequestBody(matchingJsonPath("bookingId", equalTo("$bookingId"))),
            )
          }
        }

        @Test
        fun `will track a telemetry event for success`() {
          await untilAsserted {
            verify(telemetryClient).trackEvent(
              eq("alert-synchronisation-booking-transfer-success"),
              check {
                assertThat(it["offenderNo"]).isEqualTo(offenderNo)
                assertThat(it["bookingId"]).isEqualTo(bookingId.toString())
                assertThat(it["previousBookingId"]).isEqualTo(previousBookingId.toString())
                assertThat(it["alertSequence"]).isEqualTo(alertSequence.toString())
                assertThat(it["dpsAlertId"]).isEqualTo(dpsAlertId)
              },
              isNull(),
            )
          }
        }
      }

      @Nested
      inner class WhenNotFoundForMappingUpdate {
        @BeforeEach
        fun setUp() {
          alertsNomisApiMockServer.stubGetPreviousBooking(offenderNo, bookingId, previousBookingId)
          alertsMappingApiMockServer.stubGetByNomisId(bookingId = bookingId, alertSequence = alertSequence)
          alertsMappingApiMockServer.stubUpdateByNomisId(status = NOT_FOUND)
          awsSqsAlertOffenderEventsClient.sendMessage(
            alertsQueueOffenderEventsUrl,
            alertEvent(
              eventType = "ALERT-INSERTED",
              bookingId = bookingId,
              alertSequence = alertSequence,
              offenderNo = offenderNo,
            ),
          )
        }

        @Test
        fun `will retrieve the alert from NOMIS`() {
          await untilAsserted {
            alertsNomisApiMockServer.verify(
              2,
              getRequestedFor(urlPathEqualTo("/prisoners/booking-id/$bookingId/alerts/$alertSequence")),
            )
          }
        }

        @Test
        fun `will retrieve the previous booking id`() {
          await untilAsserted {
            alertsNomisApiMockServer.verify(
              2,
              getRequestedFor(urlPathEqualTo("/prisoners/$offenderNo/bookings/$bookingId/previous")),
            )
          }
        }

        @Test
        fun `will attempt update the mapping`() {
          await untilAsserted {
            alertsMappingApiMockServer.verify(
              2,
              putRequestedFor(urlPathEqualTo("/mapping/alerts/nomis-booking-id/$previousBookingId/nomis-alert-sequence/$alertSequence"))
                .withRequestBody(matchingJsonPath("bookingId", equalTo("$bookingId"))),
            )
          }
        }

        @Test
        fun `will track a telemetry event for failure`() {
          await untilAsserted {
            verify(telemetryClient, times(2)).trackEvent(
              eq("alert-synchronisation-booking-transfer-failed"),
              check {
                assertThat(it["offenderNo"]).isEqualTo(offenderNo)
                assertThat(it["bookingId"]).isEqualTo(bookingId.toString())
                assertThat(it["previousBookingId"]).isEqualTo(previousBookingId.toString())
                assertThat(it["alertSequence"]).isEqualTo(alertSequence.toString())
              },
              isNull(),
            )
          }
        }

        @Test
        fun `the event is placed on dead letter queue`() {
          await untilAsserted {
            assertThat(
              awsSqsAlertsOffenderEventDlqClient.countAllMessagesOnQueue(alertsQueueOffenderEventsDlqUrl).get(),
            ).isEqualTo(1)
          }
        }
      }
    }
  }

  @Nested
  @DisplayName("ALERT-UPDATED")
  inner class AlertUpdated {
    @Nested
    @DisplayName("When alert was updated in DPS")
    inner class DPSUpdated {
      private val bookingId = 12345L
      private val alertSequence = 3L

      @BeforeEach
      fun setUp() {
        alertsNomisApiMockServer.stubGetAlert(
          bookingId = bookingId,
          alertSequence = alertSequence,
          alert = alert(bookingId = bookingId, alertSequence = alertSequence).copy(
            audit = alert().audit.copy(
              auditModuleName = "DPS_SYNCHRONISATION",
            ),
          ),
        )
        awsSqsAlertOffenderEventsClient.sendMessage(
          alertsQueueOffenderEventsUrl,
          alertEvent(
            eventType = "ALERT-UPDATED",
            bookingId = bookingId,
            alertSequence = alertSequence,
            offenderNo = "A3864DZ",
          ),
        )
      }

      @Test
      fun `the event is ignored`() {
        await untilAsserted {
          verify(telemetryClient).trackEvent(
            eq("alert-synchronisation-updated-skipped"),
            check {
              assertThat(it["offenderNo"]).isEqualTo("A3864DZ")
              assertThat(it["bookingId"]).isEqualTo(bookingId.toString())
              assertThat(it["alertSequence"]).isEqualTo(alertSequence.toString())
            },
            isNull(),
          )
        }

        // will not bother getting mapping
        alertsMappingApiMockServer.verify(
          count = 0,
          getRequestedFor(urlPathMatching("/mapping/alerts/nomis-booking-id/\\d+/nomis-alert-sequence/\\d+")),
        )
        // will not update the alert in DPS
        dpsAlertsServer.verify(
          0,
          putRequestedFor(anyUrl()),
        )
      }
    }

    @Nested
    @DisplayName("When alert was update in NOMIS")
    inner class NomisUpdated {
      private val bookingId = 12345L
      private val alertSequence = 3L
      private val offenderNo = "A3864DZ"
      private val alertExpiryDate = "2023-08-12"

      @BeforeEach
      fun setUp() {
        alertsNomisApiMockServer.stubGetAlert(
          bookingId = bookingId,
          alertSequence = alertSequence,
          alert = alert(bookingId = bookingId, alertSequence = alertSequence).copy(
            alertCode = CodeDescription("XNR", "Not For Release"),
            type = CodeDescription("X", "Security"),
            audit = alert().audit.copy(
              auditModuleName = "OCDALERT",
            ),
            expiryDate = LocalDate.parse(alertExpiryDate),
          ),
        )
      }

      @Nested
      @DisplayName("When mapping doesn't exist")
      inner class MappingDoesNotExist {
        @BeforeEach
        fun setUp() {
          alertsMappingApiMockServer.stubGetByNomisId(status = NOT_FOUND)
          awsSqsAlertOffenderEventsClient.sendMessage(
            alertsQueueOffenderEventsUrl,
            alertEvent(
              eventType = "ALERT-UPDATED",
              bookingId = bookingId,
              alertSequence = alertSequence,
              offenderNo = offenderNo,
            ),
          )
        }

        @Test
        fun `telemetry added to track the failure`() {
          await untilAsserted {
            verify(telemetryClient, atLeastOnce()).trackEvent(
              eq("alert-synchronisation-updated-failed"),
              check {
                assertThat(it["offenderNo"]).isEqualTo("A3864DZ")
                assertThat(it["bookingId"]).isEqualTo(bookingId.toString())
                assertThat(it["alertSequence"]).isEqualTo(alertSequence.toString())
              },
              isNull(),
            )
          }
        }

        @Test
        fun `the event is placed on dead letter queue`() {
          await untilAsserted {
            assertThat(
              awsSqsAlertsOffenderEventDlqClient.countAllMessagesOnQueue(alertsQueueOffenderEventsDlqUrl).get(),
            ).isEqualTo(1)
          }
        }
      }

      @Nested
      @DisplayName("When mapping doesn't exist and alert is not relevant")
      inner class MappingDoesNotExistAndShouldNotExist {
        @BeforeEach
        fun setUp() {
          alertsMappingApiMockServer.stubGetByNomisId(status = NOT_FOUND)
          alertsNomisApiMockServer.stubGetAlert(
            bookingId = bookingId,
            alertSequence = alertSequence,
            alert = alert(bookingId = bookingId, alertSequence = alertSequence).copy(
              bookingSequence = 2,
              alertCode = CodeDescription("XNR", "Not For Release"),
              type = CodeDescription("X", "Security"),
              audit = alert().audit.copy(
                auditModuleName = "OCDALERT",
              ),
              expiryDate = LocalDate.parse(alertExpiryDate),
            ),
          )

          awsSqsAlertOffenderEventsClient.sendMessage(
            alertsQueueOffenderEventsUrl,
            alertEvent(
              eventType = "ALERT-UPDATED",
              bookingId = bookingId,
              alertSequence = alertSequence,
              offenderNo = offenderNo,
            ),
          )
        }

        @Test
        fun `telemetry added to track the update being ignored`() {
          await untilAsserted {
            verify(telemetryClient, atLeastOnce()).trackEvent(
              eq("alert-synchronisation-updated-ignored-previous-booking"),
              check {
                assertThat(it["offenderNo"]).isEqualTo("A3864DZ")
                assertThat(it["bookingId"]).isEqualTo(bookingId.toString())
                assertThat(it["alertSequence"]).isEqualTo(alertSequence.toString())
              },
              isNull(),
            )
          }
        }

        @Test
        fun `the event not is placed on dead letter queue`() {
          await untilAsserted {
            assertThat(
              awsSqsAlertsOffenderEventDlqClient.countAllMessagesOnQueue(alertsQueueOffenderEventsDlqUrl).get(),
            ).isEqualTo(0)
          }
        }
      }

      @Nested
      @DisplayName("When mapping does exist")
      inner class MappingExists {
        private val dpsAlertId = "a04f7a8d-61aa-400c-9395-f4dc62f36ab0"

        @BeforeEach
        fun setUp() {
          alertsMappingApiMockServer.stubGetByNomisId(
            bookingId = bookingId,
            alertSequence = alertSequence,
            AlertMappingDto(
              nomisBookingId = bookingId,
              nomisAlertSequence = alertSequence,
              dpsAlertId = dpsAlertId,
              offenderNo = "A1234KT",
              mappingType = MIGRATED,
            ),
          )
          dpsAlertsServer.stubPutAlert()
          awsSqsAlertOffenderEventsClient.sendMessage(
            alertsQueueOffenderEventsUrl,
            alertEvent(
              eventType = "ALERT-UPDATED",
              bookingId = bookingId,
              alertSequence = alertSequence,
              offenderNo = offenderNo,
            ),
          )
        }

        @Test
        fun `will update DPS with the changes`() {
          await untilAsserted {
            dpsAlertsServer.verify(
              1,
              putRequestedFor(urlPathEqualTo("/alerts/$dpsAlertId"))
                .withRequestBody(matchingJsonPath("activeTo", equalTo(alertExpiryDate))),
            )
          }
        }

        @Test
        fun `will track a telemetry event for success`() {
          await untilAsserted {
            verify(telemetryClient).trackEvent(
              eq("alert-synchronisation-updated-success"),
              check {
                assertThat(it["offenderNo"]).isEqualTo("A3864DZ")
                assertThat(it["bookingId"]).isEqualTo(bookingId.toString())
                assertThat(it["alertSequence"]).isEqualTo(alertSequence.toString())
                assertThat(it["dpsAlertId"]).isEqualTo(dpsAlertId)
              },
              isNull(),
            )
          }
        }
      }
    }
  }

  @Nested
  @DisplayName("ALERT-DELETED")
  inner class AlertDeleted {
    @Nested
    @DisplayName("When alert was deleted in either NOMIS or DPS")
    inner class DeletedInEitherNOMISOrDPS {
      private val bookingId = 12345L
      private val alertSequence = 3L
      private val offenderNo = "A3864DZ"

      @Nested
      @DisplayName("When mapping doesn't exist")
      inner class MappingDoesNotExist {
        @BeforeEach
        fun setUp() {
          alertsMappingApiMockServer.stubGetByNomisId(status = NOT_FOUND)
          awsSqsAlertOffenderEventsClient.sendMessage(
            alertsQueueOffenderEventsUrl,
            alertEvent(
              eventType = "ALERT-DELETED",
              bookingId = bookingId,
              alertSequence = alertSequence,
              offenderNo = offenderNo,
            ),
          )
        }

        @Test
        fun `telemetry added to track that the delete was ignored`() {
          await untilAsserted {
            verify(telemetryClient, atLeastOnce()).trackEvent(
              eq("alert-synchronisation-deleted-ignored"),
              check {
                assertThat(it["offenderNo"]).isEqualTo("A3864DZ")
                assertThat(it["bookingId"]).isEqualTo(bookingId.toString())
                assertThat(it["alertSequence"]).isEqualTo(alertSequence.toString())
              },
              isNull(),
            )
          }
        }
      }

      @Nested
      @DisplayName("When mapping does exist")
      inner class MappingExists {
        private val dpsAlertId = "a04f7a8d-61aa-400c-9395-f4dc62f36ab0"

        @BeforeEach
        fun setUp() {
          alertsMappingApiMockServer.stubGetByNomisId(
            bookingId = bookingId,
            alertSequence = alertSequence,
            AlertMappingDto(
              nomisBookingId = bookingId,
              nomisAlertSequence = alertSequence,
              dpsAlertId = dpsAlertId,
              offenderNo = "A1234KT",
              mappingType = MIGRATED,
            ),
          )
          dpsAlertsServer.stubDeleteAlert()
          alertsMappingApiMockServer.stubDeleteMapping()
          awsSqsAlertOffenderEventsClient.sendMessage(
            alertsQueueOffenderEventsUrl,
            alertEvent(
              eventType = "ALERT-DELETED",
              bookingId = bookingId,
              alertSequence = alertSequence,
              offenderNo = offenderNo,
            ),
          )
        }

        @Test
        fun `will delete Alert in DPS`() {
          await untilAsserted {
            dpsAlertsServer.verify(
              1,
              deleteRequestedFor(urlPathEqualTo("/alerts/$dpsAlertId")),
            )
          }
        }

        @Test
        fun `will delete Alert mapping`() {
          await untilAsserted {
            alertsMappingApiMockServer.verify(
              1,
              deleteRequestedFor(urlPathEqualTo("/mapping/alerts/dps-alert-id/$dpsAlertId")),
            )
          }
        }

        @Test
        fun `will track a telemetry event for success`() {
          await untilAsserted {
            verify(telemetryClient).trackEvent(
              eq("alert-synchronisation-deleted-success"),
              check {
                assertThat(it["offenderNo"]).isEqualTo("A3864DZ")
                assertThat(it["bookingId"]).isEqualTo(bookingId.toString())
                assertThat(it["alertSequence"]).isEqualTo(alertSequence.toString())
                assertThat(it["dpsAlertId"]).isEqualTo(dpsAlertId)
              },
              isNull(),
            )
          }
        }
      }

      @Nested
      @DisplayName("When mapping fails to be deleted")
      inner class MappingDeleteFails {
        private val dpsAlertId = "a04f7a8d-61aa-400c-9395-f4dc62f36ab0"

        @BeforeEach
        fun setUp() {
          alertsMappingApiMockServer.stubGetByNomisId(
            bookingId = bookingId,
            alertSequence = alertSequence,
            AlertMappingDto(
              nomisBookingId = bookingId,
              nomisAlertSequence = alertSequence,
              dpsAlertId = dpsAlertId,
              offenderNo = "A1234KT",
              mappingType = MIGRATED,
            ),
          )
          dpsAlertsServer.stubDeleteAlert()
          alertsMappingApiMockServer.stubDeleteMapping(status = INTERNAL_SERVER_ERROR)
          awsSqsAlertOffenderEventsClient.sendMessage(
            alertsQueueOffenderEventsUrl,
            alertEvent(
              eventType = "ALERT-DELETED",
              bookingId = bookingId,
              alertSequence = alertSequence,
              offenderNo = offenderNo,
            ),
          )
        }

        @Test
        fun `will delete Alert in DPS`() {
          await untilAsserted {
            dpsAlertsServer.verify(
              1,
              deleteRequestedFor(urlPathEqualTo("/alerts/$dpsAlertId")),
            )
          }
        }

        @Test
        fun `will try to delete Alert mapping once and record failure`() {
          await untilAsserted {
            verify(telemetryClient).trackEvent(
              eq("alert-mapping-deleted-failed"),
              any(),
              isNull(),
            )

            alertsMappingApiMockServer.verify(
              1,
              deleteRequestedFor(urlPathEqualTo("/mapping/alerts/dps-alert-id/$dpsAlertId")),
            )
          }
        }

        @Test
        fun `will track a telemetry event for success`() {
          await untilAsserted {
            verify(telemetryClient).trackEvent(
              eq("alert-synchronisation-deleted-success"),
              check {
                assertThat(it["offenderNo"]).isEqualTo("A3864DZ")
                assertThat(it["bookingId"]).isEqualTo(bookingId.toString())
                assertThat(it["alertSequence"]).isEqualTo(alertSequence.toString())
                assertThat(it["dpsAlertId"]).isEqualTo(dpsAlertId)
              },
              isNull(),
            )
          }
        }
      }
    }
  }

  @Nested
  @DisplayName("prison-offender-events.prisoner.merged")
  inner class PrisonerMerge {
    @Nested
    inner class HappyPath {
      val bookingId = BOOKING_ID
      private val dpsAlertId1 = UUID.fromString("956d4326-b0c3-47ac-ab12-f0165109a6c5")
      private val dpsAlertId2 = UUID.fromString("f612a10f-4827-4022-be96-d882193dfabd")

      @BeforeEach
      fun setUp() {
        alertsNomisApiMockServer.stubGetAlertsByBookingId(bookingId, alertCount = 4)
        alertsMappingApiMockServer.stubGetByNomisId(bookingId, 1)
        alertsMappingApiMockServer.stubGetByNomisId(bookingId, 2)
        alertsMappingApiMockServer.stubGetByNomisId(bookingId, 3, status = NOT_FOUND)
        alertsMappingApiMockServer.stubGetByNomisId(bookingId, 4, status = NOT_FOUND)
        dpsAlertsServer.stubMergePrisonerAlerts(
          created = listOf(
            mergedAlert().copy(offenderBookId = bookingId, alertSeq = 3, alertUuid = dpsAlertId1),
            mergedAlert().copy(offenderBookId = bookingId, alertSeq = 4, alertUuid = dpsAlertId2),
          ),
        )
        alertsMappingApiMockServer.stubPostBatchMappings()
        awsSqsSentencingOffenderEventsClient.sendMessage(
          alertsQueueOffenderEventsUrl,
          mergeDomainEvent(
            bookingId = bookingId,
            offenderNo = "A1234KT",
            removedOffenderNo = "A1000KT",
          ),
        )
        waitForAnyProcessingToComplete()
      }

      @Test
      fun `will retrieve alerts for the bookings that has changed`() {
        alertsNomisApiMockServer.verify(getRequestedFor(urlPathEqualTo("/prisoners/booking-id/$bookingId/alerts")))
      }

      @Test
      fun `will attempt to  get mappings for all alerts`() {
        alertsMappingApiMockServer.verify(getRequestedFor(urlPathEqualTo("/mapping/alerts/nomis-booking-id/$bookingId/nomis-alert-sequence/1")))
        alertsMappingApiMockServer.verify(getRequestedFor(urlPathEqualTo("/mapping/alerts/nomis-booking-id/$bookingId/nomis-alert-sequence/2")))
        alertsMappingApiMockServer.verify(getRequestedFor(urlPathEqualTo("/mapping/alerts/nomis-booking-id/$bookingId/nomis-alert-sequence/3")))
        alertsMappingApiMockServer.verify(getRequestedFor(urlPathEqualTo("/mapping/alerts/nomis-booking-id/$bookingId/nomis-alert-sequence/4")))
      }

      @Test
      fun `will send missing alerts to DPS`() {
        dpsAlertsServer.verify(
          postRequestedFor(urlPathEqualTo("/merge-alerts"))
            .withRequestBodyJsonPath("prisonNumberMergeFrom", "A1000KT")
            .withRequestBodyJsonPath("prisonNumberMergeTo", "A1234KT")
            .withRequestBodyJsonPath("newAlerts[0].offenderBookId", "$bookingId")
            .withRequestBodyJsonPath("newAlerts[0].alertSeq", "3")
            .withRequestBodyJsonPath("newAlerts[1].offenderBookId", "$bookingId")
            .withRequestBodyJsonPath("newAlerts[1].alertSeq", "4"),
        )
      }

      @Test
      fun `will create a mapping between the DPS and NOMIS alerts`() {
        alertsMappingApiMockServer.verify(
          postRequestedFor(urlPathEqualTo("/mapping/alerts/batch"))
            .withRequestBodyJsonPath("$[0].nomisBookingId", "$bookingId")
            .withRequestBodyJsonPath("$[0].nomisAlertSequence", "3")
            .withRequestBodyJsonPath("$[0].dpsAlertId", "$dpsAlertId1")
            .withRequestBodyJsonPath("$[1].nomisBookingId", "$bookingId")
            .withRequestBodyJsonPath("$[1].nomisAlertSequence", "4")
            .withRequestBodyJsonPath("$[1].dpsAlertId", "$dpsAlertId2"),
        )
      }

      @Test
      fun `will track telemetry for the merge`() {
        verify(telemetryClient).trackEvent(
          eq("from-nomis-synch-alerts-merge"),
          check {
            assertThat(it["bookingId"]).isEqualTo(bookingId.toString())
            assertThat(it["offenderNo"]).isEqualTo("A1234KT")
            assertThat(it["removedOffenderNo"]).isEqualTo("A1000KT")
            assertThat(it["newAlertsCount"]).isEqualTo("2")
            assertThat(it["newAlerts"]).isEqualTo("3, 4")
          },
          isNull(),
        )
      }
    }

    @Nested
    inner class HappyPathWithFailure {
      val bookingId = BOOKING_ID
      private val dpsAlertId1 = UUID.fromString("956d4326-b0c3-47ac-ab12-f0165109a6c5")
      private val dpsAlertId2 = UUID.fromString("f612a10f-4827-4022-be96-d882193dfabd")

      @BeforeEach
      fun setUp() {
        alertsNomisApiMockServer.stubGetAlertsByBookingId(bookingId, alertCount = 4)
        alertsMappingApiMockServer.stubGetByNomisId(bookingId, 1)
        alertsMappingApiMockServer.stubGetByNomisId(bookingId, 2)
        alertsMappingApiMockServer.stubGetByNomisId(bookingId, 3, status = NOT_FOUND)
        alertsMappingApiMockServer.stubGetByNomisId(bookingId, 4, status = NOT_FOUND)
        dpsAlertsServer.stubMergePrisonerAlerts(
          created = listOf(
            mergedAlert().copy(offenderBookId = bookingId, alertSeq = 3, alertUuid = dpsAlertId1),
            mergedAlert().copy(offenderBookId = bookingId, alertSeq = 4, alertUuid = dpsAlertId2),
          ),
        )
        alertsMappingApiMockServer.stubPostBatchMappingsFailureFollowedBySuccess()
        awsSqsSentencingOffenderEventsClient.sendMessage(
          alertsQueueOffenderEventsUrl,
          mergeDomainEvent(
            bookingId = bookingId,
            offenderNo = "A1234KT",
            removedOffenderNo = "A1000KT",
          ),
        )
        waitForAnyProcessingToComplete("alert-mapping-created-merge-success")
      }

      @Test
      fun `will retrieve alerts for the bookings that has changed once`() {
        alertsNomisApiMockServer.verify(1, getRequestedFor(urlPathEqualTo("/prisoners/booking-id/$bookingId/alerts")))
      }

      @Test
      fun `will attempt to get mappings for all alerts once`() {
        alertsMappingApiMockServer.verify(1, getRequestedFor(urlPathEqualTo("/mapping/alerts/nomis-booking-id/$bookingId/nomis-alert-sequence/1")))
        alertsMappingApiMockServer.verify(1, getRequestedFor(urlPathEqualTo("/mapping/alerts/nomis-booking-id/$bookingId/nomis-alert-sequence/2")))
        alertsMappingApiMockServer.verify(1, getRequestedFor(urlPathEqualTo("/mapping/alerts/nomis-booking-id/$bookingId/nomis-alert-sequence/3")))
        alertsMappingApiMockServer.verify(1, getRequestedFor(urlPathEqualTo("/mapping/alerts/nomis-booking-id/$bookingId/nomis-alert-sequence/4")))
      }

      @Test
      fun `will send missing alerts to DPS once`() {
        dpsAlertsServer.verify(
          1,
          postRequestedFor(urlPathEqualTo("/merge-alerts"))
            .withRequestBodyJsonPath("prisonNumberMergeFrom", "A1000KT")
            .withRequestBodyJsonPath("prisonNumberMergeTo", "A1234KT")
            .withRequestBodyJsonPath("newAlerts[0].offenderBookId", "$bookingId")
            .withRequestBodyJsonPath("newAlerts[0].alertSeq", "3")
            .withRequestBodyJsonPath("newAlerts[1].offenderBookId", "$bookingId")
            .withRequestBodyJsonPath("newAlerts[1].alertSeq", "4"),
        )
      }

      @Test
      fun `will attempt create a mapping between the DPS and NOMIS alerts until it succeeds`() {
        alertsMappingApiMockServer.verify(
          2,
          postRequestedFor(urlPathEqualTo("/mapping/alerts/batch"))
            .withRequestBodyJsonPath("$[0].nomisBookingId", "$bookingId")
            .withRequestBodyJsonPath("$[0].nomisAlertSequence", "3")
            .withRequestBodyJsonPath("$[0].dpsAlertId", "$dpsAlertId1")
            .withRequestBodyJsonPath("$[1].nomisBookingId", "$bookingId")
            .withRequestBodyJsonPath("$[1].nomisAlertSequence", "4")
            .withRequestBodyJsonPath("$[1].dpsAlertId", "$dpsAlertId2"),
        )
      }

      @Test
      fun `will track telemetry for the merge and mapping success`() {
        verify(telemetryClient).trackEvent(
          eq("from-nomis-synch-alerts-merge"),
          check {
            assertThat(it["bookingId"]).isEqualTo(bookingId.toString())
            assertThat(it["offenderNo"]).isEqualTo("A1234KT")
            assertThat(it["removedOffenderNo"]).isEqualTo("A1000KT")
            assertThat(it["newAlertsCount"]).isEqualTo("2")
            assertThat(it["newAlerts"]).isEqualTo("3, 4")
            assertThat(it["mappingSuccess"]).isEqualTo("false")
          },
          isNull(),
        )
        verify(telemetryClient).trackEvent(
          eq("alert-mapping-created-merge-success"),
          check {
            assertThat(it["bookingId"]).isEqualTo(bookingId.toString())
            assertThat(it["offenderNo"]).isEqualTo("A1234KT")
            assertThat(it["removedOffenderNo"]).isEqualTo("A1000KT")
            assertThat(it["newAlertsCount"]).isEqualTo("2")
            assertThat(it["newAlerts"]).isEqualTo("3, 4")
          },
          isNull(),
        )
      }
    }

    @Nested
    inner class DuplicateMappingFailure {
      val bookingId = BOOKING_ID
      private val dpsAlertId = UUID.fromString("956d4326-b0c3-47ac-ab12-f0165109a6c5")
      private val existingAlertId = UUID.fromString("f612a10f-4827-4022-be96-d882193dfabd")

      @BeforeEach
      fun setUp() {
        alertsNomisApiMockServer.stubGetAlertsByBookingId(bookingId, alertCount = 1)
        alertsMappingApiMockServer.stubGetByNomisId(bookingId, 1, status = NOT_FOUND)
        dpsAlertsServer.stubMergePrisonerAlerts(
          created = listOf(
            mergedAlert().copy(offenderBookId = bookingId, alertSeq = 1, alertUuid = dpsAlertId),
          ),
        )
        alertsMappingApiMockServer.stubPostBatchMappings(
          error = DuplicateMappingErrorResponse(
            moreInfo = DuplicateErrorContentObject(
              duplicate = AlertMappingDto(
                dpsAlertId = dpsAlertId.toString(),
                nomisBookingId = bookingId,
                nomisAlertSequence = 1,
                offenderNo = "A1234KT",
                mappingType = NOMIS_CREATED,
              ),
              existing = AlertMappingDto(
                dpsAlertId = existingAlertId.toString(),
                nomisBookingId = bookingId,
                nomisAlertSequence = 1,
                offenderNo = "A1234KT",
                mappingType = NOMIS_CREATED,
              ),
            ),
            errorCode = 1409,
            status = DuplicateMappingErrorResponse.Status._409_CONFLICT,
            userMessage = "Duplicate mapping",
          ),
        )
        awsSqsSentencingOffenderEventsClient.sendMessage(
          alertsQueueOffenderEventsUrl,
          mergeDomainEvent(
            bookingId = bookingId,
            offenderNo = "A1234KT",
            removedOffenderNo = "A1000KT",
          ),
        )
        waitForAnyProcessingToComplete("from-nomis-sync-alert-duplicate")
      }

      @Test
      fun `will send missing alerts to DPS even though they are duplicated`() {
        dpsAlertsServer.verify(
          1,
          postRequestedFor(urlPathEqualTo("/merge-alerts"))
            .withRequestBodyJsonPath("prisonNumberMergeFrom", "A1000KT")
            .withRequestBodyJsonPath("prisonNumberMergeTo", "A1234KT")
            .withRequestBodyJsonPath("newAlerts[0].offenderBookId", "$bookingId")
            .withRequestBodyJsonPath("newAlerts[0].alertSeq", "1"),
        )
      }

      @Test
      fun `will not put message on DLQ`() {
        assertThat(
          awsSqsAlertsOffenderEventDlqClient.countAllMessagesOnQueue(alertsQueueOffenderEventsDlqUrl).get(),
        ).isEqualTo(0)
      }

      @Test
      fun `will track event with duplicate details`() {
        verify(telemetryClient).trackEvent(
          eq("from-nomis-sync-alert-duplicate"),
          check {
            assertThat(it["offenderNo"]).isEqualTo("A1234KT")
            assertThat(it["duplicateDpsAlertId"]).isEqualTo(dpsAlertId.toString())
            assertThat(it["duplicateNomisBookingId"]).isEqualTo("$bookingId")
            assertThat(it["duplicateNomisAlertSequence"]).isEqualTo("1")
            assertThat(it["existingDpsAlertId"]).isEqualTo(existingAlertId.toString())
            assertThat(it["existingNomisBookingId"]).isEqualTo("$bookingId")
            assertThat(it["existingNomisAlertSequence"]).isEqualTo("1")
          },
          isNull(),
        )
      }
    }
  }
}

fun alertEvent(
  eventType: String,
  bookingId: Long = BOOKING_ID,
  alertSequence: Long = ALERT_SEQUENCE,
  offenderNo: String = OFFENDER_ID_DISPLAY,
) = """{
    "MessageId": "ae06c49e-1f41-4b9f-b2f2-dcca610d02cd", "Type": "Notification", "Timestamp": "2019-10-21T14:01:18.500Z", 
    "Message": "{\"eventId\":\"5958295\",\"eventType\":\"$eventType\",\"eventDatetime\":\"2019-10-21T15:00:25.489964\",\"bookingId\": \"$bookingId\",\"alertSeq\": \"$alertSequence\",\"offenderIdDisplay\": \"$offenderNo\",\"nomisEventType\":\"OFF_ALERT_UPDATE\",\"alertType\":\"L\",\"alertCode\":\"LCE\",\"alertDateTime\":\"2024-02-14T13:24:11\" }",
    "TopicArn": "arn:aws:sns:eu-west-1:000000000000:offender_events", 
    "MessageAttributes": {
      "eventType": {"Type": "String", "Value": "$eventType"}, 
      "id": {"Type": "String", "Value": "8b07cbd9-0820-0a0f-c32f-a9429b618e0b"}, 
      "contentType": {"Type": "String", "Value": "text/plain;charset=UTF-8"}, 
      "timestamp": {"Type": "Number.java.lang.Long", "Value": "1571666478344"}
    }
}
""".trimIndent()

private fun alert(bookingId: Long = 123456, alertSequence: Long = 3) = AlertResponse(
  bookingId = bookingId,
  alertSequence = alertSequence,
  bookingSequence = 1,
  alertCode = CodeDescription("XA", "TACT"),
  type = CodeDescription("X", "Security"),
  date = LocalDate.now(),
  isActive = true,
  isVerified = false,
  audit = NomisAudit(
    createDatetime = LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME),
    createUsername = "Q1251T",
  ),
)
