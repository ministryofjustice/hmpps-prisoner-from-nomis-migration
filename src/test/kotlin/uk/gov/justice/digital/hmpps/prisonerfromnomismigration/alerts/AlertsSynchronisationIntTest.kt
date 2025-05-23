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
import org.mockito.kotlin.any
import org.mockito.kotlin.check
import org.mockito.kotlin.isNull
import org.mockito.kotlin.verify
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.http.HttpStatus.INTERNAL_SERVER_ERROR
import org.springframework.http.HttpStatus.NOT_FOUND
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.alerts.AlertsDpsApiExtension.Companion.dpsAlertsServer
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.alerts.AlertsDpsApiMockServer.Companion.dpsAlert
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.alerts.AlertsDpsApiMockServer.Companion.resyncedAlert
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.helper.bookingMovedDomainEvent
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.helper.mergeDomainEvent
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.helper.prisonerReceivedDomainEvent
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.integration.SqsIntegrationTestBase
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.integration.sendMessage
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomismappings.model.AlertMappingDto
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomismappings.model.AlertMappingDto.MappingType.MIGRATED
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomisprisoner.model.AlertResponse
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomisprisoner.model.CodeDescription
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomisprisoner.model.NomisAudit
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.wiremock.withRequestBodyJsonPath
import uk.gov.justice.hmpps.sqs.countAllMessagesOnQueue
import java.time.LocalDate
import java.time.LocalDateTime
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
          dpsAlertsServer.stubPostAlert(offenderNo, dpsAlert().copy(alertUuid = UUID.fromString(dpsAlertId)))
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
              postRequestedFor(urlPathEqualTo("/prisoners/$offenderNo/alerts"))
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
          dpsAlertsServer.stubPostAlert(offenderNo, dpsAlert().copy(alertUuid = UUID.fromString(dpsAlertId)))
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
                postRequestedFor(urlPathEqualTo("/prisoners/$offenderNo/alerts"))
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
                postRequestedFor(urlPathEqualTo("/prisoners/$offenderNo/alerts"))
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
        @BeforeEach
        fun setUp() {
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

        @Test
        fun `will retrieve the alert from NOMIS`() {
          await untilAsserted {
            alertsNomisApiMockServer.verify(
              getRequestedFor(urlPathEqualTo("/prisoners/booking-id/$bookingId/alerts/$alertSequence")),
            )
          }
        }

        @Test
        fun `will track a telemetry event for ignore`() {
          await untilAsserted {
            verify(telemetryClient).trackEvent(
              eq("alert-created-new-booking-ignored"),
              check {
                assertThat(it["offenderNo"]).isEqualTo(offenderNo)
                assertThat(it["bookingId"]).isEqualTo(bookingId.toString())
                assertThat(it["alertSequence"]).isEqualTo(alertSequence.toString())
              },
              isNull(),
            )
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
      val offenderNo = "A1234KT"
      private val dpsAlertId1 = UUID.fromString("956d4326-b0c3-47ac-ab12-f0165109a6c5")
      private val dpsAlertId2 = UUID.fromString("f612a10f-4827-4022-be96-d882193dfabd")

      @BeforeEach
      fun setUp() {
        alertsNomisApiMockServer.stubGetAlertsToResynchronise(offenderNo, bookingId = bookingId, currentAlertCount = 2)
        dpsAlertsServer.stubResynchroniseAlerts(
          offenderNo = offenderNo,
          response = listOf(
            resyncedAlert().copy(offenderBookId = bookingId, alertSeq = 1, alertUuid = dpsAlertId1),
            resyncedAlert().copy(offenderBookId = bookingId, alertSeq = 2, alertUuid = dpsAlertId2),
          ),
        )
        dpsAlertsServer.stubResynchroniseAlerts(
          offenderNo = "A1000KT",
          response = listOf(),
        )
        alertsMappingApiMockServer.stubReplaceMappingsForMerge(offenderNo)
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
        alertsNomisApiMockServer.verify(getRequestedFor(urlPathEqualTo("/prisoners/$offenderNo/alerts/to-migrate")))
      }

      @Test
      fun `will send all alerts to DPS`() {
        dpsAlertsServer.verify(
          postRequestedFor(urlPathEqualTo("/resync/$offenderNo/alerts"))
            .withRequestBodyJsonPath("[0].offenderBookId", "$bookingId")
            .withRequestBodyJsonPath("[0].alertSeq", "1")
            .withRequestBodyJsonPath("[1].offenderBookId", "$bookingId")
            .withRequestBodyJsonPath("[1].alertSeq", "2"),
        )
      }

      @Test
      fun `will remove all alerts from DPS for removed record`() {
        dpsAlertsServer.verify(
          postRequestedFor(urlPathEqualTo("/resync/A1000KT/alerts"))
            .withRequestBodyJsonPath("$.size()", "0"),
        )
      }

      @Test
      fun `will create a mapping between the DPS and NOMIS alerts`() {
        alertsMappingApiMockServer.verify(
          putRequestedFor(urlPathEqualTo("/mapping/alerts/$offenderNo/merge"))
            .withRequestBodyJsonPath("prisonerMapping.mappings[0].nomisBookingId", "$bookingId")
            .withRequestBodyJsonPath("prisonerMapping.mappings[0].nomisAlertSequence", "1")
            .withRequestBodyJsonPath("prisonerMapping.mappings[0].dpsAlertId", "$dpsAlertId1")
            .withRequestBodyJsonPath("prisonerMapping.mappings[1].nomisBookingId", "$bookingId")
            .withRequestBodyJsonPath("prisonerMapping.mappings[1].nomisAlertSequence", "2")
            .withRequestBodyJsonPath("prisonerMapping.mappings[1].dpsAlertId", "$dpsAlertId2")
            .withRequestBodyJsonPath("prisonerMapping.mappingType", "NOMIS_CREATED")
            .withRequestBodyJsonPath("removedOffenderNo", "A1000KT"),
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
            assertThat(it["alertsCount"]).isEqualTo("2")
            assertThat(it["alerts"]).isEqualTo("1, 2")
          },
          isNull(),
        )
      }
    }

    @Nested
    inner class HappyPathWithFailure {
      val bookingId = BOOKING_ID
      val offenderNo = "A1234KT"
      private val dpsAlertId1 = UUID.fromString("956d4326-b0c3-47ac-ab12-f0165109a6c5")
      private val dpsAlertId2 = UUID.fromString("f612a10f-4827-4022-be96-d882193dfabd")

      @BeforeEach
      fun setUp() {
        alertsNomisApiMockServer.stubGetAlertsToResynchronise(offenderNo, bookingId = bookingId, currentAlertCount = 2)
        dpsAlertsServer.stubResynchroniseAlerts(
          offenderNo = offenderNo,
          response = listOf(
            resyncedAlert().copy(offenderBookId = bookingId, alertSeq = 1, alertUuid = dpsAlertId1),
            resyncedAlert().copy(offenderBookId = bookingId, alertSeq = 2, alertUuid = dpsAlertId2),
          ),
        )
        dpsAlertsServer.stubResynchroniseAlerts(
          offenderNo = "A1000KT",
          response = listOf(),
        )
        alertsMappingApiMockServer.stubReplaceMappingsForMerge(offenderNo)
        alertsMappingApiMockServer.stubReplaceMappingsForMergeFollowedBySuccess(offenderNo)
        awsSqsSentencingOffenderEventsClient.sendMessage(
          alertsQueueOffenderEventsUrl,
          mergeDomainEvent(
            bookingId = bookingId,
            offenderNo = "A1234KT",
            removedOffenderNo = "A1000KT",
          ),
        )
        waitForAnyProcessingToComplete("alert-mapping-replace-success")
      }

      @Test
      fun `will retrieve alerts for the bookings that has changed`() {
        alertsNomisApiMockServer.verify(1, getRequestedFor(urlPathEqualTo("/prisoners/$offenderNo/alerts/to-migrate")))
      }

      @Test
      fun `will send all alerts to DPS once`() {
        dpsAlertsServer.verify(
          1,
          postRequestedFor(urlPathEqualTo("/resync/$offenderNo/alerts"))
            .withRequestBodyJsonPath("[0].offenderBookId", "$bookingId")
            .withRequestBodyJsonPath("[0].alertSeq", "1")
            .withRequestBodyJsonPath("[1].offenderBookId", "$bookingId")
            .withRequestBodyJsonPath("[1].alertSeq", "2"),
        )
      }

      @Test
      fun `will remove all alerts from DPS for removed record`() {
        dpsAlertsServer.verify(
          postRequestedFor(urlPathEqualTo("/resync/A1000KT/alerts"))
            .withRequestBodyJsonPath("$.size()", "0"),
        )
      }

      @Test
      fun `will try to create a mapping between the DPS and NOMIS alerts until it succeeds`() {
        alertsMappingApiMockServer.verify(
          2,
          putRequestedFor(urlPathEqualTo("/mapping/alerts/$offenderNo/merge"))
            .withRequestBodyJsonPath("prisonerMapping.mappings[0].nomisBookingId", "$bookingId")
            .withRequestBodyJsonPath("prisonerMapping.mappings[0].nomisAlertSequence", "1")
            .withRequestBodyJsonPath("prisonerMapping.mappings[0].dpsAlertId", "$dpsAlertId1")
            .withRequestBodyJsonPath("prisonerMapping.mappings[1].nomisBookingId", "$bookingId")
            .withRequestBodyJsonPath("prisonerMapping.mappings[1].nomisAlertSequence", "2")
            .withRequestBodyJsonPath("prisonerMapping.mappings[1].dpsAlertId", "$dpsAlertId2")
            .withRequestBodyJsonPath("prisonerMapping.mappingType", "NOMIS_CREATED")
            .withRequestBodyJsonPath("removedOffenderNo", "A1000KT"),
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
            assertThat(it["alertsCount"]).isEqualTo("2")
            assertThat(it["alerts"]).isEqualTo("1, 2")
          },
          isNull(),
        )
        verify(telemetryClient).trackEvent(
          eq("alert-mapping-replace-success"),
          check {
            assertThat(it["bookingId"]).isEqualTo(bookingId.toString())
            assertThat(it["offenderNo"]).isEqualTo("A1234KT")
            assertThat(it["removedOffenderNo"]).isEqualTo("A1000KT")
            assertThat(it["alertsCount"]).isEqualTo("2")
            assertThat(it["alerts"]).isEqualTo("1, 2")
          },
          isNull(),
        )
      }
    }
  }

  @Nested
  @DisplayName("prison-offender-events.prisoner.booking.moved")
  inner class BookingMoved {
    @Nested
    inner class HappyPath {
      val bookingId = BOOKING_ID
      private val movedToNomsNumber = "A1234KT"
      private val movedFromNomsNumber = "A1000KT"
      private val dpsAlertId1 = UUID.fromString("956d4326-b0c3-47ac-ab12-f0165109a6c5")
      private val dpsAlertId2 = UUID.fromString("f612a10f-4827-4022-be96-d882193dfabd")

      @BeforeEach
      fun setUp() {
        alertsNomisApiMockServer.stubGetAlertsToResynchronise(movedFromNomsNumber, bookingId = bookingId, currentAlertCount = 2)
        dpsAlertsServer.stubResynchroniseAlerts(
          offenderNo = movedFromNomsNumber,
          response = listOf(
            resyncedAlert().copy(offenderBookId = bookingId, alertSeq = 1, alertUuid = dpsAlertId1),
            resyncedAlert().copy(offenderBookId = bookingId, alertSeq = 2, alertUuid = dpsAlertId2),
          ),
        )
        alertsMappingApiMockServer.stubReplaceMappings(movedFromNomsNumber)
        alertsNomisApiMockServer.stubGetPrisonerDetails(offenderNo = movedToNomsNumber, prisonerDetails().copy(location = "MDI", active = true))
        awsSqsSentencingOffenderEventsClient.sendMessage(
          alertsQueueOffenderEventsUrl,
          bookingMovedDomainEvent(
            bookingId = bookingId,
            movedFromNomsNumber = movedFromNomsNumber,
            movedToNomsNumber = movedToNomsNumber,
          ),
        )
        waitForAnyProcessingToComplete("from-nomis-synch-alerts-booking-moved", "from-nomis-synch-alerts-booking-moved-ignored")
      }

      @Test
      fun `will retrieve alerts for the bookings that has changed`() {
        alertsNomisApiMockServer.verify(getRequestedFor(urlPathEqualTo("/prisoners/$movedFromNomsNumber/alerts/to-migrate")))
      }

      @Test
      fun `will send all alerts to DPS`() {
        dpsAlertsServer.verify(
          postRequestedFor(urlPathEqualTo("/resync/$movedFromNomsNumber/alerts"))
            .withRequestBodyJsonPath("[0].offenderBookId", "$bookingId")
            .withRequestBodyJsonPath("[0].alertSeq", "1")
            .withRequestBodyJsonPath("[1].offenderBookId", "$bookingId")
            .withRequestBodyJsonPath("[1].alertSeq", "2"),
        )
      }

      @Test
      fun `will create a mapping between the DPS and NOMIS alerts`() {
        alertsMappingApiMockServer.verify(
          putRequestedFor(urlPathEqualTo("/mapping/alerts/$movedFromNomsNumber/all"))
            .withRequestBodyJsonPath("mappings[0].nomisBookingId", "$bookingId")
            .withRequestBodyJsonPath("mappings[0].nomisAlertSequence", "1")
            .withRequestBodyJsonPath("mappings[0].dpsAlertId", "$dpsAlertId1")
            .withRequestBodyJsonPath("mappings[1].nomisBookingId", "$bookingId")
            .withRequestBodyJsonPath("mappings[1].nomisAlertSequence", "2")
            .withRequestBodyJsonPath("mappings[1].dpsAlertId", "$dpsAlertId2")
            .withRequestBodyJsonPath("mappingType", "NOMIS_CREATED"),
        )
      }

      @Test
      fun `will check the to prisoner is active`() {
        alertsNomisApiMockServer.verify(getRequestedFor(urlPathEqualTo("/prisoners/$movedToNomsNumber")))
      }

      @Test
      fun `will track telemetry for the booking move`() {
        verify(telemetryClient).trackEvent(
          eq("from-nomis-synch-alerts-booking-moved"),
          check {
            assertThat(it["bookingId"]).isEqualTo(bookingId.toString())
            assertThat(it["whichPrisoner"]).isEqualTo("FROM")
            assertThat(it["offenderNo"]).isEqualTo(movedFromNomsNumber)
            assertThat(it["alertsCount"]).isEqualTo("2")
            assertThat(it["alerts"]).isEqualTo("1, 2")
          },
          isNull(),
        )
      }

      @Test
      fun `will track no further active require for the to prisoner`() {
        verify(telemetryClient).trackEvent(
          eq("from-nomis-synch-alerts-booking-moved-ignored"),
          check {
            assertThat(it["bookingId"]).isEqualTo(bookingId.toString())
            assertThat(it["whichPrisoner"]).isEqualTo("TO")
            assertThat(it["offenderNo"]).isEqualTo(movedToNomsNumber)
          },
          isNull(),
        )
      }
    }

    @Nested
    inner class HappyPathWithInactiveBooking {
      private val movedBookingId = BOOKING_ID
      private val bookingIdForFromPrisoner = BOOKING_ID + 1
      private val bookingIdForToPrisoner = movedBookingId
      private val movedToNomsNumber = "A1234KT"
      private val movedFromNomsNumber = "A1000KT"
      private val dpsAlertId1 = UUID.fromString("956d4326-b0c3-47ac-ab12-f0165109a6c5")
      private val dpsAlertId2 = UUID.fromString("f612a10f-4827-4022-be96-d882193dfabd")
      private val dpsAlertId3 = UUID.fromString("ca1b4421-e463-42ac-b036-6df5aed308f7")

      @BeforeEach
      fun setUp() {
        alertsNomisApiMockServer.stubGetAlertsToResynchronise(movedFromNomsNumber, bookingId = bookingIdForFromPrisoner, currentAlertCount = 2)
        alertsNomisApiMockServer.stubGetAlertsToResynchronise(movedToNomsNumber, bookingId = bookingIdForToPrisoner, currentAlertCount = 1)
        dpsAlertsServer.stubResynchroniseAlerts(
          offenderNo = movedFromNomsNumber,
          response = listOf(
            resyncedAlert().copy(offenderBookId = bookingIdForFromPrisoner, alertSeq = 1, alertUuid = dpsAlertId1),
            resyncedAlert().copy(offenderBookId = bookingIdForFromPrisoner, alertSeq = 2, alertUuid = dpsAlertId2),
          ),
        )
        dpsAlertsServer.stubResynchroniseAlerts(
          offenderNo = movedToNomsNumber,
          response = listOf(
            resyncedAlert().copy(offenderBookId = bookingIdForToPrisoner, alertSeq = 1, alertUuid = dpsAlertId3),
          ),
        )
        alertsMappingApiMockServer.stubReplaceMappings(movedFromNomsNumber)
        alertsMappingApiMockServer.stubReplaceMappings(movedToNomsNumber)
        alertsNomisApiMockServer.stubGetPrisonerDetails(offenderNo = movedToNomsNumber, prisonerDetails().copy(location = "OUT", active = false))
        awsSqsSentencingOffenderEventsClient.sendMessage(
          alertsQueueOffenderEventsUrl,
          bookingMovedDomainEvent(
            bookingId = movedBookingId,
            movedFromNomsNumber = movedFromNomsNumber,
            movedToNomsNumber = movedToNomsNumber,
          ),
        )
        waitForAnyProcessingToComplete("from-nomis-synch-alerts-booking-moved", times = 2)
      }

      @Test
      fun `will retrieve alerts for both prisoners that has changed`() {
        alertsNomisApiMockServer.verify(getRequestedFor(urlPathEqualTo("/prisoners/$movedFromNomsNumber/alerts/to-migrate")))
        alertsNomisApiMockServer.verify(getRequestedFor(urlPathEqualTo("/prisoners/$movedToNomsNumber/alerts/to-migrate")))
      }

      @Test
      fun `will send all alerts to DPS for both prisoners`() {
        dpsAlertsServer.verify(
          postRequestedFor(urlPathEqualTo("/resync/$movedFromNomsNumber/alerts"))
            .withRequestBodyJsonPath("[0].offenderBookId", "$bookingIdForFromPrisoner")
            .withRequestBodyJsonPath("[0].alertSeq", "1")
            .withRequestBodyJsonPath("[1].offenderBookId", "$bookingIdForFromPrisoner")
            .withRequestBodyJsonPath("[1].alertSeq", "2"),
        )
        dpsAlertsServer.verify(
          postRequestedFor(urlPathEqualTo("/resync/$movedToNomsNumber/alerts"))
            .withRequestBodyJsonPath("[0].offenderBookId", "$bookingIdForToPrisoner")
            .withRequestBodyJsonPath("[0].alertSeq", "1"),
        )
      }

      @Test
      fun `will create a mapping between the DPS and NOMIS alerts for both prisoners`() {
        alertsMappingApiMockServer.verify(
          putRequestedFor(urlPathEqualTo("/mapping/alerts/$movedFromNomsNumber/all"))
            .withRequestBodyJsonPath("mappings[0].nomisBookingId", "$bookingIdForFromPrisoner")
            .withRequestBodyJsonPath("mappings[0].nomisAlertSequence", "1")
            .withRequestBodyJsonPath("mappings[0].dpsAlertId", "$dpsAlertId1")
            .withRequestBodyJsonPath("mappings[1].nomisBookingId", "$bookingIdForFromPrisoner")
            .withRequestBodyJsonPath("mappings[1].nomisAlertSequence", "2")
            .withRequestBodyJsonPath("mappings[1].dpsAlertId", "$dpsAlertId2")
            .withRequestBodyJsonPath("mappingType", "NOMIS_CREATED"),
        )
        alertsMappingApiMockServer.verify(
          putRequestedFor(urlPathEqualTo("/mapping/alerts/$movedToNomsNumber/all"))
            .withRequestBodyJsonPath("mappings[0].nomisBookingId", "$bookingIdForToPrisoner")
            .withRequestBodyJsonPath("mappings[0].nomisAlertSequence", "1")
            .withRequestBodyJsonPath("mappings[0].dpsAlertId", "$dpsAlertId3")
            .withRequestBodyJsonPath("mappingType", "NOMIS_CREATED"),
        )
      }

      @Test
      fun `will check the to prisoner is active`() {
        alertsNomisApiMockServer.verify(getRequestedFor(urlPathEqualTo("/prisoners/$movedToNomsNumber")))
      }

      @Test
      fun `will track telemetry for the booking move for both prisoners`() {
        verify(telemetryClient).trackEvent(
          eq("from-nomis-synch-alerts-booking-moved"),
          check {
            assertThat(it["bookingId"]).isEqualTo(movedBookingId.toString())
            assertThat(it["whichPrisoner"]).isEqualTo("FROM")
            assertThat(it["offenderNo"]).isEqualTo(movedFromNomsNumber)
            assertThat(it["alertsCount"]).isEqualTo("2")
            assertThat(it["alerts"]).isEqualTo("1, 2")
          },
          isNull(),
        )
        verify(telemetryClient).trackEvent(
          eq("from-nomis-synch-alerts-booking-moved"),
          check {
            assertThat(it["bookingId"]).isEqualTo(movedBookingId.toString())
            assertThat(it["whichPrisoner"]).isEqualTo("TO")
            assertThat(it["offenderNo"]).isEqualTo(movedToNomsNumber)
            assertThat(it["alertsCount"]).isEqualTo("1")
            assertThat(it["alerts"]).isEqualTo("1")
          },
          isNull(),
        )
      }
    }

    @Nested
    inner class HappyPathWithFailure {
      val bookingId = BOOKING_ID
      private val movedToNomsNumber = "A1234KT"
      private val movedFromNomsNumber = "A1000KT"
      private val dpsAlertId1 = UUID.fromString("956d4326-b0c3-47ac-ab12-f0165109a6c5")
      private val dpsAlertId2 = UUID.fromString("f612a10f-4827-4022-be96-d882193dfabd")

      @BeforeEach
      fun setUp() {
        alertsNomisApiMockServer.stubGetAlertsToResynchronise(movedFromNomsNumber, bookingId = bookingId, currentAlertCount = 2)
        dpsAlertsServer.stubResynchroniseAlerts(
          offenderNo = movedFromNomsNumber,
          response = listOf(
            resyncedAlert().copy(offenderBookId = bookingId, alertSeq = 1, alertUuid = dpsAlertId1),
            resyncedAlert().copy(offenderBookId = bookingId, alertSeq = 2, alertUuid = dpsAlertId2),
          ),
        )
        alertsMappingApiMockServer.stubReplaceMappingsFailureFollowedBySuccess(movedFromNomsNumber)
        awsSqsSentencingOffenderEventsClient.sendMessage(
          alertsQueueOffenderEventsUrl,
          bookingMovedDomainEvent(
            bookingId = bookingId,
            movedFromNomsNumber = movedFromNomsNumber,
            movedToNomsNumber = movedToNomsNumber,
          ),
        )
        waitForAnyProcessingToComplete("alert-mapping-replace-success")
      }

      @Test
      fun `will retrieve alerts for the bookings that has changed`() {
        alertsNomisApiMockServer.verify(1, getRequestedFor(urlPathEqualTo("/prisoners/$movedFromNomsNumber/alerts/to-migrate")))
      }

      @Test
      fun `will send all alerts to DPS once`() {
        dpsAlertsServer.verify(
          1,
          postRequestedFor(urlPathEqualTo("/resync/$movedFromNomsNumber/alerts"))
            .withRequestBodyJsonPath("[0].offenderBookId", "$bookingId")
            .withRequestBodyJsonPath("[0].alertSeq", "1")
            .withRequestBodyJsonPath("[1].offenderBookId", "$bookingId")
            .withRequestBodyJsonPath("[1].alertSeq", "2"),
        )
      }

      @Test
      fun `will try to create a mapping between the DPS and NOMIS alerts until it succeeds`() {
        alertsMappingApiMockServer.verify(
          2,
          putRequestedFor(urlPathEqualTo("/mapping/alerts/$movedFromNomsNumber/all"))
            .withRequestBodyJsonPath("mappings[0].nomisBookingId", "$bookingId")
            .withRequestBodyJsonPath("mappings[0].nomisAlertSequence", "1")
            .withRequestBodyJsonPath("mappings[0].dpsAlertId", "$dpsAlertId1")
            .withRequestBodyJsonPath("mappings[1].nomisBookingId", "$bookingId")
            .withRequestBodyJsonPath("mappings[1].nomisAlertSequence", "2")
            .withRequestBodyJsonPath("mappings[1].dpsAlertId", "$dpsAlertId2")
            .withRequestBodyJsonPath("mappingType", "NOMIS_CREATED"),
        )
      }

      @Test
      fun `will track telemetry for the merge and mapping success`() {
        verify(telemetryClient).trackEvent(
          eq("from-nomis-synch-alerts-booking-moved"),
          check {
            assertThat(it["bookingId"]).isEqualTo(bookingId.toString())
            assertThat(it["whichPrisoner"]).isEqualTo("FROM")
            assertThat(it["offenderNo"]).isEqualTo(movedFromNomsNumber)
            assertThat(it["alertsCount"]).isEqualTo("2")
            assertThat(it["alerts"]).isEqualTo("1, 2")
          },
          isNull(),
        )
        verify(telemetryClient).trackEvent(
          eq("alert-mapping-replace-success"),
          check {
            assertThat(it["bookingId"]).isEqualTo(bookingId.toString())
            assertThat(it["whichPrisoner"]).isEqualTo("FROM")
            assertThat(it["offenderNo"]).isEqualTo(movedFromNomsNumber)
            assertThat(it["alertsCount"]).isEqualTo("2")
            assertThat(it["alerts"]).isEqualTo("1, 2")
          },
          isNull(),
        )
      }
    }
  }

  @Nested
  @DisplayName("prisoner-offender-search.prisoner.received")
  inner class PrisonerReceived {
    @Nested
    inner class HappyPath {
      val offenderNo = OFFENDER_ID_DISPLAY
      val bookingId = BOOKING_ID
      private val dpsAlertId1 = UUID.fromString("956d4326-b0c3-47ac-ab12-f0165109a6c5")
      private val dpsAlertId2 = UUID.fromString("f612a10f-4827-4022-be96-d882193dfabd")

      @BeforeEach
      fun setUp() {
        alertsNomisApiMockServer.stubGetAlertsToResynchronise(offenderNo, bookingId = bookingId, currentAlertCount = 2)
        dpsAlertsServer.stubResynchroniseAlerts(
          offenderNo = offenderNo,
          response = listOf(
            resyncedAlert().copy(offenderBookId = bookingId, alertSeq = 1, alertUuid = dpsAlertId1),
            resyncedAlert().copy(offenderBookId = bookingId, alertSeq = 2, alertUuid = dpsAlertId2),
          ),
        )
        alertsMappingApiMockServer.stubReplaceMappings(offenderNo)
        awsSqsSentencingOffenderEventsClient.sendMessage(
          alertsQueueOffenderEventsUrl,
          prisonerReceivedDomainEvent(
            offenderNo = offenderNo,
          ),
        )
        waitForAnyProcessingToComplete("from-nomis-synch-alerts-resynchronise")
      }

      @Test
      fun `will retrieve current alerts for the prisoner`() {
        alertsNomisApiMockServer.verify(getRequestedFor(urlPathEqualTo("/prisoners/$offenderNo/alerts/to-migrate")))
      }

      @Test
      fun `will send all alerts to DPS`() {
        dpsAlertsServer.verify(
          postRequestedFor(urlPathEqualTo("/resync/$offenderNo/alerts"))
            .withRequestBodyJsonPath("[0].offenderBookId", "$bookingId")
            .withRequestBodyJsonPath("[0].alertSeq", "1")
            .withRequestBodyJsonPath("[1].offenderBookId", "$bookingId")
            .withRequestBodyJsonPath("[1].alertSeq", "2"),
        )
      }

      @Test
      fun `will replaces mapping between the DPS and NOMIS alerts`() {
        alertsMappingApiMockServer.verify(
          putRequestedFor(urlPathEqualTo("/mapping/alerts/$offenderNo/all"))
            .withRequestBodyJsonPath("mappingType", "NOMIS_CREATED")
            .withRequestBodyJsonPath("mappings[0].nomisBookingId", "$bookingId")
            .withRequestBodyJsonPath("mappings[0].nomisAlertSequence", "1")
            .withRequestBodyJsonPath("mappings[0].dpsAlertId", "$dpsAlertId1")
            .withRequestBodyJsonPath("mappings[1].nomisBookingId", "$bookingId")
            .withRequestBodyJsonPath("mappings[1].nomisAlertSequence", "2")
            .withRequestBodyJsonPath("mappings[1].dpsAlertId", "$dpsAlertId2"),
        )
      }

      @Test
      fun `will track telemetry for the resynchronise`() {
        verify(telemetryClient).trackEvent(
          eq("from-nomis-synch-alerts-resynchronise"),
          check {
            assertThat(it["offenderNo"]).isEqualTo(offenderNo)
            assertThat(it["alertsCount"]).isEqualTo("2")
            assertThat(it["alerts"]).isEqualTo("1, 2")
          },
          isNull(),
        )
      }
    }

    @Nested
    inner class HappyPathWithFailure {
      val offenderNo = OFFENDER_ID_DISPLAY
      val bookingId = BOOKING_ID
      private val dpsAlertId1 = UUID.fromString("956d4326-b0c3-47ac-ab12-f0165109a6c5")
      private val dpsAlertId2 = UUID.fromString("f612a10f-4827-4022-be96-d882193dfabd")

      @BeforeEach
      fun setUp() {
        alertsNomisApiMockServer.stubGetAlertsToResynchronise(offenderNo, bookingId = bookingId, currentAlertCount = 2)
        dpsAlertsServer.stubResynchroniseAlerts(
          offenderNo = offenderNo,
          response = listOf(
            resyncedAlert().copy(offenderBookId = bookingId, alertSeq = 1, alertUuid = dpsAlertId1),
            resyncedAlert().copy(offenderBookId = bookingId, alertSeq = 2, alertUuid = dpsAlertId2),
          ),
        )
        alertsMappingApiMockServer.stubReplaceMappingsFailureFollowedBySuccess(offenderNo)
        awsSqsSentencingOffenderEventsClient.sendMessage(
          alertsQueueOffenderEventsUrl,
          prisonerReceivedDomainEvent(
            offenderNo = offenderNo,
          ),
        )
        waitForAnyProcessingToComplete("alert-mapping-replace-success")
      }

      @Test
      fun `will retrieve current alerts for the prisoner once`() {
        alertsNomisApiMockServer.verify(1, getRequestedFor(urlPathEqualTo("/prisoners/$offenderNo/alerts/to-migrate")))
      }

      @Test
      fun `will send all alerts to DPS once`() {
        dpsAlertsServer.verify(
          1,
          postRequestedFor(urlPathEqualTo("/resync/$offenderNo/alerts"))
            .withRequestBodyJsonPath("[0].offenderBookId", "$bookingId")
            .withRequestBodyJsonPath("[0].alertSeq", "1")
            .withRequestBodyJsonPath("[1].offenderBookId", "$bookingId")
            .withRequestBodyJsonPath("[1].alertSeq", "2"),
        )
      }

      @Test
      fun `will attempt create a mapping between the DPS and NOMIS alerts until it succeeds`() {
        alertsMappingApiMockServer.verify(
          2,
          putRequestedFor(urlPathEqualTo("/mapping/alerts/$offenderNo/all"))
            .withRequestBodyJsonPath("mappingType", "NOMIS_CREATED")
            .withRequestBodyJsonPath("mappings[0].nomisBookingId", "$bookingId")
            .withRequestBodyJsonPath("mappings[0].nomisAlertSequence", "1")
            .withRequestBodyJsonPath("mappings[0].dpsAlertId", "$dpsAlertId1")
            .withRequestBodyJsonPath("mappings[1].nomisBookingId", "$bookingId")
            .withRequestBodyJsonPath("mappings[1].nomisAlertSequence", "2")
            .withRequestBodyJsonPath("mappings[1].dpsAlertId", "$dpsAlertId2"),
        )
      }

      @Test
      fun `will track telemetry for the resynchronisation and mapping success`() {
        verify(telemetryClient).trackEvent(
          eq("from-nomis-synch-alerts-resynchronise"),
          check {
            assertThat(it["offenderNo"]).isEqualTo(offenderNo)
            assertThat(it["alertsCount"]).isEqualTo("2")
            assertThat(it["alerts"]).isEqualTo("1, 2")
          },
          isNull(),
        )
        verify(telemetryClient).trackEvent(
          eq("alert-mapping-replace-success"),
          check {
            assertThat(it["offenderNo"]).isEqualTo(offenderNo)
          },
          isNull(),
        )
      }
    }

    @Nested
    inner class WhenNotReAdmission {
      val offenderNo = OFFENDER_ID_DISPLAY

      @BeforeEach
      fun setUp() {
        awsSqsSentencingOffenderEventsClient.sendMessage(
          alertsQueueOffenderEventsUrl,
          prisonerReceivedDomainEvent(
            offenderNo = offenderNo,
            reason = "TRANSFERRED",
          ),
        )
        waitForAnyProcessingToComplete("from-nomis-synch-alerts-resynchronise-ignored")
      }

      @Test
      fun `will not even retrieve current alerts for the prisoner`() {
        alertsNomisApiMockServer.verify(0, getRequestedFor(urlPathEqualTo("/prisoners/$offenderNo/alerts/to-migrate")))
      }

      @Test
      fun `will track telemetry for the resynchronisation ignore`() {
        verify(telemetryClient).trackEvent(
          eq("from-nomis-synch-alerts-resynchronise-ignored"),
          check {
            assertThat(it["offenderNo"]).isEqualTo(offenderNo)
            assertThat(it["receiveReason"]).isEqualTo("TRANSFERRED")
          },
          isNull(),
        )
      }
    }

    @Nested
    inner class WhenNewAdmission {
      val offenderNo = OFFENDER_ID_DISPLAY
      val bookingId = BOOKING_ID
      private val dpsAlertId1 = UUID.fromString("956d4326-b0c3-47ac-ab12-f0165109a6c5")
      private val dpsAlertId2 = UUID.fromString("f612a10f-4827-4022-be96-d882193dfabd")

      @BeforeEach
      fun setUp() {
        alertsNomisApiMockServer.stubGetAlertsToResynchronise(offenderNo, bookingId = bookingId, currentAlertCount = 2)
        dpsAlertsServer.stubResynchroniseAlerts(
          offenderNo = offenderNo,
          response = listOf(
            resyncedAlert().copy(offenderBookId = bookingId, alertSeq = 1, alertUuid = dpsAlertId1),
            resyncedAlert().copy(offenderBookId = bookingId, alertSeq = 2, alertUuid = dpsAlertId2),
          ),
        )
        alertsMappingApiMockServer.stubReplaceMappings(offenderNo)
        awsSqsSentencingOffenderEventsClient.sendMessage(
          alertsQueueOffenderEventsUrl,
          prisonerReceivedDomainEvent(
            offenderNo = offenderNo,
            reason = "NEW_ADMISSION",
          ),
        )
        waitForAnyProcessingToComplete("from-nomis-synch-alerts-resynchronise")
      }

      @Test
      fun `will send all alerts to DPS`() {
        dpsAlertsServer.verify(
          postRequestedFor(urlPathEqualTo("/resync/$offenderNo/alerts"))
            .withRequestBodyJsonPath("[0].offenderBookId", "$bookingId")
            .withRequestBodyJsonPath("[0].alertSeq", "1")
            .withRequestBodyJsonPath("[1].offenderBookId", "$bookingId")
            .withRequestBodyJsonPath("[1].alertSeq", "2"),
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
    createDatetime = LocalDateTime.now(),
    createUsername = "Q1251T",
  ),
)
