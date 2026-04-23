package uk.gov.justice.digital.hmpps.prisonerfromnomismigration.movements.taps

import com.github.tomakehurst.wiremock.client.WireMock.absent
import com.github.tomakehurst.wiremock.client.WireMock.deleteRequestedFor
import com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor
import com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor
import com.github.tomakehurst.wiremock.client.WireMock.putRequestedFor
import com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo
import com.github.tomakehurst.wiremock.client.WireMock.urlPathMatching
import org.assertj.core.api.Assertions.assertThat
import org.awaitility.kotlin.await
import org.awaitility.kotlin.untilAsserted
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.mockito.kotlin.check
import org.mockito.kotlin.eq
import org.mockito.kotlin.isNull
import org.mockito.kotlin.verify
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.http.HttpStatus.NOT_FOUND
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.integration.SqsIntegrationTestBase
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.integration.sendMessage
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.movements.ExternalMovementsMappingApiMockServer
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.movements.model.SyncResponse
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.movements.model.SyncWriteTapMovement
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.movements.taps.TapDpsApiExtension.Companion.dpsExtMovementsServer
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomismappings.model.DuplicateErrorContentObject
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomismappings.model.DuplicateMappingErrorResponse
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomismappings.model.ExternalMovementSyncMappingDto
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.wiremock.withRequestBodyJsonPath
import uk.gov.justice.hmpps.sqs.countAllMessagesOnQueue
import java.util.*

class TapMovementIntTest(
  @Autowired private val nomisApi: TapNomisApiMockServer,
  @Autowired private val mappingApi: ExternalMovementsMappingApiMockServer,
) : SqsIntegrationTestBase() {

  private val dpsApi = dpsExtMovementsServer

  @Nested
  @DisplayName("EXTERNAL_MOVEMENT-CHANGED (inserted, scheduled)")
  inner class TemporaryAbsenceScheduledExternalMovementCreated {
    private val dpsMovementId = UUID.randomUUID()
    private val dpsOccurrenceId = UUID.randomUUID()

    @Nested
    @DisplayName("Happy path - scheduled outbound movement")
    inner class HappyPathOutbound {
      @BeforeEach
      fun setUp() {
        mappingApi.stubGetExternalMovementMapping(12345, 154, NOT_FOUND)
        nomisApi.stubGetTapMovementOut(movementSeq = 154, tapApplicationId = 111, tapScheduleOutId = 45678)
        mappingApi.stubGetTapApplicationMapping(111)
        mappingApi.stubGetTapScheduleMapping(45678, dpsOccurrenceId)
        mappingApi.stubCreateExternalMovementMapping()
        dpsApi.stubSyncTapMovement(response = SyncResponse(dpsMovementId))

        sendMessage(tapMovementEvent(inserted = true, direction = "OUT"))
          .also { waitForAnyProcessingToComplete() }
      }

      @Test
      fun `should check mapping`() {
        mappingApi.verify(getRequestedFor(urlPathEqualTo("/mapping/temporary-absence/external-movement/nomis-movement-id/12345/154")))
      }

      @Test
      fun `should check application mapping`() {
        mappingApi.verify(getRequestedFor(urlPathEqualTo("/mapping/taps/application/nomis-id/111")))
      }

      @Test
      fun `should check scheduled movement mapping`() {
        mappingApi.verify(getRequestedFor(urlPathEqualTo("/mapping/taps/schedule/nomis-id/45678")))
      }

      @Test
      fun `should get NOMIS external movement`() {
        nomisApi.verify(getRequestedFor(urlPathEqualTo("/movements/A1234BC/taps/movement/out/12345/154")))
      }

      @Test
      fun `should create DPS external movement`() {
        TapDpsApiMockServer.getRequestBody<SyncWriteTapMovement>(
          putRequestedFor(urlPathEqualTo("/sync/temporary-absence-movements/A1234BC")),
        ).apply {
          assertThat(id).isNull()
          assertThat(occurrenceId).isEqualTo(dpsOccurrenceId)
          assertThat(direction).isEqualTo(SyncWriteTapMovement.Direction.OUT)
          assertThat(absenceReasonCode).isEqualTo("C6")
          assertThat(location.description).isEqualTo("Some description")
          assertThat(location.address).isEqualTo("full address")
          assertThat(location.postcode).isEqualTo("S1 1AB")
          assertThat(location.uprn).isNull()
          assertThat(accompaniedByCode).isEqualTo("P")
          assertThat(accompaniedByComments).isEqualTo("Absence escort text")
          assertThat(comments).isEqualTo("Absence comment text")
          assertThat(created.by).isEqualTo("USER")
          assertThat(prisonCode).isEqualTo("LEI")
          assertThat(legacyId).isEqualTo("12345_154")
        }
      }

      @Test
      fun `should create mapping`() {
        mappingApi.verify(
          postRequestedFor(urlPathEqualTo("/mapping/temporary-absence/external-movement"))
            .withRequestBodyJsonPath("prisonerNumber", "A1234BC")
            .withRequestBodyJsonPath("bookingId", 12345)
            .withRequestBodyJsonPath("nomisMovementSeq", 154)
            .withRequestBodyJsonPath("nomisAddressId", 321)
            .withRequestBodyJsonPath("nomisAddressOwnerClass", "OFF")
            .withRequestBodyJsonPath("dpsMovementId", "$dpsMovementId")
            .withRequestBodyJsonPath("dpsAddressText", "full address")
            .withRequestBodyJsonPath("dpsUprn", absent())
            .withRequestBodyJsonPath("mappingType", "NOMIS_CREATED"),
        )
      }

      @Test
      fun `should create success telemetry`() {
        verify(telemetryClient).trackEvent(
          eq("temporary-absence-sync-external-movement-inserted-success"),
          check {
            assertThat(it["offenderNo"]).isEqualTo("A1234BC")
            assertThat(it["bookingId"]).isEqualTo("12345")
            assertThat(it["movementSeq"]).isEqualTo("154")
            assertThat(it["nomisScheduledEventId"]).isEqualTo("45678")
            assertThat(it["nomisApplicationId"]).isEqualTo("111")
            assertThat(it["directionCode"]).isEqualTo("OUT")
            assertThat(it["dpsMovementId"]).isEqualTo("$dpsMovementId")
            assertThat(it["nomisAddressId"]).isEqualTo("321")
            assertThat(it["nomisAddressOwnerClass"]).isEqualTo("OFF")
          },
          isNull(),
        )
      }
    }

    @Nested
    @DisplayName("Happy path - scheduled inbound movement")
    inner class HappyPathInbound {
      @BeforeEach
      fun setUp() {
        mappingApi.stubGetExternalMovementMapping(12345, 154, NOT_FOUND)
        nomisApi.stubGetTapMovementIn(movementSeq = 154, tapApplicationId = 111, tapScheduleMovementInId = 45678, tapScheduleMovementOutId = 23456)
        mappingApi.stubGetTapApplicationMapping(111)
        mappingApi.stubGetTapScheduleMapping(23456)
        mappingApi.stubCreateExternalMovementMapping()
        dpsApi.stubSyncTapMovement(response = SyncResponse(dpsMovementId))

        sendMessage(tapMovementEvent(inserted = true, direction = "IN"))
          .also { waitForAnyProcessingToComplete() }
      }

      @Test
      fun `should get NOMIS external movement`() {
        nomisApi.verify(getRequestedFor(urlPathEqualTo("/movements/A1234BC/taps/movement/in/12345/154")))
      }

      @Test
      fun `should check application mapping`() {
        mappingApi.verify(getRequestedFor(urlPathEqualTo("/mapping/taps/application/nomis-id/111")))
      }

      @Test
      fun `should check scheduled movement mapping`() {
        mappingApi.verify(getRequestedFor(urlPathEqualTo("/mapping/taps/schedule/nomis-id/23456")))
      }

      @Test
      fun `should create DPS external movement`() {
        dpsApi.verify(
          putRequestedFor(urlPathEqualTo("/sync/temporary-absence-movements/A1234BC"))
            .withRequestBodyJsonPath("id", absent())
            .withRequestBodyJsonPath("legacyId", "12345_154")
            .withRequestBodyJsonPath("direction", "IN")
            .withRequestBodyJsonPath("location.postcode", "S1 1AB")
            .withRequestBodyJsonPath("location.address", "full address")
            .withRequestBodyJsonPath("location.uprn", absent()),
        )
      }

      @Test
      fun `should create mapping`() {
        mappingApi.verify(
          postRequestedFor(urlPathEqualTo("/mapping/temporary-absence/external-movement"))
            .withRequestBodyJsonPath("prisonerNumber", "A1234BC")
            .withRequestBodyJsonPath("bookingId", 12345)
            .withRequestBodyJsonPath("nomisMovementSeq", 154)
            .withRequestBodyJsonPath("nomisAddressId", 321)
            .withRequestBodyJsonPath("nomisAddressOwnerClass", "OFF")
            .withRequestBodyJsonPath("dpsMovementId", "$dpsMovementId")
            .withRequestBodyJsonPath("dpsAddressText", "full address")
            .withRequestBodyJsonPath("dpsUprn", absent())
            .withRequestBodyJsonPath("mappingType", "NOMIS_CREATED"),
        )
      }

      @Test
      fun `should create success telemetry`() {
        verify(telemetryClient).trackEvent(
          eq("temporary-absence-sync-external-movement-inserted-success"),
          check {
            assertThat(it["offenderNo"]).isEqualTo("A1234BC")
            assertThat(it["bookingId"]).isEqualTo("12345")
            assertThat(it["movementSeq"]).isEqualTo("154")
            assertThat(it["nomisScheduledParentEventId"]).isEqualTo("23456")
            assertThat(it["nomisScheduledEventId"]).isEqualTo("45678")
            assertThat(it["nomisApplicationId"]).isEqualTo("111")
            assertThat(it["directionCode"]).isEqualTo("IN")
            assertThat(it["dpsMovementId"]).isEqualTo("$dpsMovementId")
            assertThat(it["nomisAddressId"]).isEqualTo("321")
            assertThat(it["nomisAddressOwnerClass"]).isEqualTo("OFF")
          },
          isNull(),
        )
      }
    }

    @Nested
    inner class WhenCreatedInDps {
      @BeforeEach
      fun setUp() {
        sendMessage(tapMovementEvent(inserted = true, auditModuleName = "DPS_SYNCHRONISATION"))
          .also { waitForAnyProcessingToComplete() }
      }

      @Test
      fun `should NOT create DPS scheduled movement`() {
        dpsApi.verify(
          0,
          putRequestedFor(urlPathEqualTo("/sync/temporary-absence-movements/A1234BC")),
        )
      }

      @Test
      fun `should NOT create mapping`() {
        mappingApi.verify(
          count = 0,
          getRequestedFor(urlPathEqualTo("/mapping/temporary-absence/external-movement/nomis-movement-id/12345/154")),
        )
      }

      @Test
      fun `should create telemetry`() {
        verify(telemetryClient).trackEvent(
          eq("temporary-absence-sync-external-movement-inserted-skipped"),
          check {
            assertThat(it["offenderNo"]).isEqualTo("A1234BC")
            assertThat(it["bookingId"]).isEqualTo("12345")
            assertThat(it["movementSeq"]).isEqualTo("154")
          },
          isNull(),
        )
      }
    }

    @Nested
    inner class WhenAlreadyCreated {
      private val dpsId = UUID.randomUUID()

      @BeforeEach
      fun setUp() {
        mappingApi.stubGetExternalMovementMapping(12345, 154, dpsId)

        sendMessage(tapMovementEvent(inserted = true))
          .also { waitForAnyProcessingToComplete() }
      }

      @Test
      fun `should NOT create DPS scheduled movement`() {
        dpsApi.verify(
          0,
          putRequestedFor(urlPathEqualTo("/sync/temporary-absence-movements/A1234BC")),
        )
      }

      @Test
      fun `should NOT create mapping`() {
        mappingApi.verify(
          count = 0,
          postRequestedFor(urlPathEqualTo("/mapping/temporary-absence/external-movement")),
        )
      }

      @Test
      fun `should create telemetry`() {
        verify(telemetryClient).trackEvent(
          eq("temporary-absence-sync-external-movement-inserted-ignored"),
          check {
            assertThat(it["offenderNo"]).isEqualTo("A1234BC")
            assertThat(it["bookingId"]).isEqualTo("12345")
            assertThat(it["movementSeq"]).isEqualTo("154")
            assertThat(it["directionCode"]).isEqualTo("OUT")
            assertThat(it["dpsMovementId"]).isEqualTo("$dpsId")
          },
          isNull(),
        )
      }
    }

    @Nested
    @DisplayName("Application mapping not created yet - scheduled outbound movement")
    inner class WhenApplicationMappingNotCreatedYetScheduledOutboundMovement {
      @BeforeEach
      fun setUp() {
        mappingApi.stubGetExternalMovementMapping(12345, 154, NOT_FOUND)
        nomisApi.stubGetTapMovementOut(movementSeq = 154, tapApplicationId = 111, tapScheduleOutId = 45678)
        mappingApi.stubGetTapApplicationMapping(111, NOT_FOUND)
        mappingApi.stubGetTapScheduleMapping(45678)

        sendMessage(tapMovementEvent(inserted = true, direction = "OUT"))
          .also { waitForAnyProcessingToComplete("temporary-absence-sync-external-movement-inserted-awaiting-parent") }
      }

      @Test
      fun `should check mapping`() {
        mappingApi.verify(getRequestedFor(urlPathEqualTo("/mapping/temporary-absence/external-movement/nomis-movement-id/12345/154")))
      }

      @Test
      fun `should check application mapping`() {
        mappingApi.verify(getRequestedFor(urlPathEqualTo("/mapping/taps/application/nomis-id/111")))
      }

      @Test
      fun `should create error telemetry`() {
        verify(telemetryClient).trackEvent(
          eq("temporary-absence-sync-external-movement-inserted-awaiting-parent"),
          check {
            assertThat(it["nomisApplicationId"]).isEqualTo("111")
            assertThat(it["error"]).isEqualTo("Expected parent entity not found, retrying")
          },
          isNull(),
        )
      }

      // Note we only send to the DLQ to assert the message is rejected in tests - in real life we'll retry 3 times as per redrive policy (1 of which should succeed after the parent is created)
      @Test
      fun `should send message to DLQ`() {
        await untilAsserted {
          assertThat(awsSqsExternalMovementsOffenderEventsDlqClient.countAllMessagesOnQueue(externalMovementsQueueOffenderEventsDlqUrl).get())
            .isEqualTo(1)
        }
      }
    }

    @Nested
    @DisplayName("Schedule mapping not created yet - scheduled outbound movement")
    inner class WhenScheduleMappingNotCreatedYetScheduledOutboundMovement {
      @BeforeEach
      fun setUp() {
        mappingApi.stubGetExternalMovementMapping(12345, 154, NOT_FOUND)
        nomisApi.stubGetTapMovementOut(movementSeq = 154, tapApplicationId = 111, tapScheduleOutId = 45678)
        mappingApi.stubGetTapApplicationMapping(111)
        mappingApi.stubGetTapScheduleMapping(45678, NOT_FOUND)

        sendMessage(tapMovementEvent(inserted = true, direction = "OUT"))
          .also { waitForAnyProcessingToComplete("temporary-absence-sync-external-movement-inserted-awaiting-parent") }
      }

      @Test
      fun `should check mapping`() {
        mappingApi.verify(getRequestedFor(urlPathEqualTo("/mapping/temporary-absence/external-movement/nomis-movement-id/12345/154")))
      }

      @Test
      fun `should check schedule mapping`() {
        mappingApi.verify(getRequestedFor(urlPathEqualTo("/mapping/taps/schedule/nomis-id/45678")))
      }

      @Test
      fun `should create error telemetry`() {
        verify(telemetryClient).trackEvent(
          eq("temporary-absence-sync-external-movement-inserted-awaiting-parent"),
          check {
            assertThat(it["nomisScheduledEventId"]).isEqualTo("45678")
            assertThat(it["error"]).isEqualTo("Expected parent entity not found, retrying")
          },
          isNull(),
        )
      }

      // Note we only send to the DLQ to assert the message is rejected in tests - in real life we'll retry 3 times as per redrive policy (1 of which should succeed after the parent is created)
      @Test
      fun `should send message to DLQ`() {
        await untilAsserted {
          assertThat(awsSqsExternalMovementsOffenderEventsDlqClient.countAllMessagesOnQueue(externalMovementsQueueOffenderEventsDlqUrl).get())
            .isEqualTo(1)
        }
      }
    }

    @Nested
    @DisplayName("Application mapping not created yet - scheduled inbound movement")
    inner class ApplicationMappingNotCreatedYetScheduledInboundMovement {
      @BeforeEach
      fun setUp() {
        mappingApi.stubGetExternalMovementMapping(12345, 154, NOT_FOUND)
        nomisApi.stubGetTapMovementIn(movementSeq = 154, tapApplicationId = 111, tapScheduleMovementInId = 45678)
        mappingApi.stubGetTapApplicationMapping(111, NOT_FOUND)
        mappingApi.stubGetTapScheduleMapping(45678)

        sendMessage(tapMovementEvent(inserted = true, direction = "IN"))
          .also { waitForAnyProcessingToComplete("temporary-absence-sync-external-movement-inserted-awaiting-parent") }
      }

      @Test
      fun `should check mapping`() {
        mappingApi.verify(getRequestedFor(urlPathEqualTo("/mapping/temporary-absence/external-movement/nomis-movement-id/12345/154")))
      }

      @Test
      fun `should check application mapping`() {
        mappingApi.verify(getRequestedFor(urlPathEqualTo("/mapping/taps/application/nomis-id/111")))
      }

      @Test
      fun `should create error telemetry`() {
        verify(telemetryClient).trackEvent(
          eq("temporary-absence-sync-external-movement-inserted-awaiting-parent"),
          check {
            assertThat(it["nomisApplicationId"]).isEqualTo("111")
            assertThat(it["error"]).isEqualTo("Expected parent entity not found, retrying")
          },
          isNull(),
        )
      }

      // Note we only send to the DLQ to assert the message is rejected in tests - in real life we'll retry 3 times as per redrive policy (1 of which should succeed after the parent is created)
      @Test
      fun `should send message to DLQ`() {
        await untilAsserted {
          assertThat(awsSqsExternalMovementsOffenderEventsDlqClient.countAllMessagesOnQueue(externalMovementsQueueOffenderEventsDlqUrl).get())
            .isEqualTo(1)
        }
      }
    }

    @Nested
    @DisplayName("Schedule mapping not created yet - scheduled inbound movement")
    inner class ScheduleMappingNotCreatedYetScheduledInboundMovement {
      @BeforeEach
      fun setUp() {
        mappingApi.stubGetExternalMovementMapping(12345, 154, NOT_FOUND)
        nomisApi.stubGetTapMovementIn(movementSeq = 154, tapApplicationId = 111, tapScheduleMovementInId = 45678, tapScheduleMovementOutId = 23456)
        mappingApi.stubGetTapApplicationMapping(111)
        mappingApi.stubGetTapScheduleMapping(23456, NOT_FOUND)

        sendMessage(tapMovementEvent(inserted = true, direction = "IN"))
          .also { waitForAnyProcessingToComplete("temporary-absence-sync-external-movement-inserted-awaiting-parent") }
      }

      @Test
      fun `should check mapping`() {
        mappingApi.verify(getRequestedFor(urlPathEqualTo("/mapping/temporary-absence/external-movement/nomis-movement-id/12345/154")))
      }

      @Test
      fun `should check schedule mapping`() {
        mappingApi.verify(getRequestedFor(urlPathEqualTo("/mapping/taps/schedule/nomis-id/23456")))
      }

      @Test
      fun `should create error telemetry`() {
        verify(telemetryClient).trackEvent(
          eq("temporary-absence-sync-external-movement-inserted-awaiting-parent"),
          check {
            assertThat(it["nomisScheduledParentEventId"]).isEqualTo("23456")
            assertThat(it["nomisScheduledEventId"]).isEqualTo("45678")
            assertThat(it["error"]).isEqualTo("Expected parent entity not found, retrying")
          },
          isNull(),
        )
      }

      // Note we only send to the DLQ to assert the message is rejected in tests - in real life we'll retry 3 times as per redrive policy (1 of which should succeed after the parent is created)
      @Test
      fun `should send message to DLQ`() {
        await untilAsserted {
          assertThat(awsSqsExternalMovementsOffenderEventsDlqClient.countAllMessagesOnQueue(externalMovementsQueueOffenderEventsDlqUrl).get())
            .isEqualTo(1)
        }
      }
    }
  }

  @Nested
  @DisplayName("EXTERNAL_MOVEMENT-CHANGED (inserted, unscheduled)")
  inner class TemporaryAbsenceUnscheduledExternalMovementCreated {
    private val dpsMovementId = UUID.randomUUID()

    @Nested
    @DisplayName("Happy path - unscheduled outbound movement")
    inner class HappyPathUnscheduledOutboundMovement {
      @BeforeEach
      fun setUp() {
        mappingApi.stubGetExternalMovementMapping(12345, 154, NOT_FOUND)
        nomisApi.stubGetTapMovementOut(movementSeq = 154, tapApplicationId = null, tapScheduleOutId = null, city = "Sheffield")
        mappingApi.stubCreateExternalMovementMapping()
        dpsApi.stubSyncTapMovement(response = SyncResponse(dpsMovementId))

        sendMessage(tapMovementEvent(inserted = true, direction = "OUT"))
          .also { waitForAnyProcessingToComplete() }
      }

      @Test
      fun `should check mapping`() {
        mappingApi.verify(getRequestedFor(urlPathEqualTo("/mapping/temporary-absence/external-movement/nomis-movement-id/12345/154")))
      }

      @Test
      fun `should NOT check application mapping`() {
        mappingApi.verify(
          count = 0,
          getRequestedFor(urlPathMatching("/mapping/taps/application/nomis-id/.*")),
        )
      }

      @Test
      fun `should NOT check scheduled movement mapping`() {
        mappingApi.verify(
          count = 0,
          getRequestedFor(urlPathEqualTo("/mapping/taps/schedule/nomis-id/.*")),
        )
      }

      @Test
      fun `should create DPS external movement`() {
        dpsApi.verify(
          putRequestedFor(urlPathEqualTo("/sync/temporary-absence-movements/A1234BC"))
            .withRequestBodyJsonPath("id", absent())
            .withRequestBodyJsonPath("legacyId", "12345_154")
            .withRequestBodyJsonPath("direction", "OUT")
            .withRequestBodyJsonPath("location.description", absent())
            .withRequestBodyJsonPath("location.postcode", absent())
            .withRequestBodyJsonPath("location.address", "Sheffield")
            .withRequestBodyJsonPath("location.uprn", absent()),
        )
      }

      @Test
      fun `should create mapping`() {
        mappingApi.verify(
          postRequestedFor(urlPathEqualTo("/mapping/temporary-absence/external-movement"))
            .withRequestBodyJsonPath("prisonerNumber", "A1234BC")
            .withRequestBodyJsonPath("bookingId", 12345)
            .withRequestBodyJsonPath("nomisMovementSeq", 154)
            .withRequestBodyJsonPath("dpsMovementId", "$dpsMovementId")
            .withRequestBodyJsonPath("nomisAddressId", absent())
            .withRequestBodyJsonPath("nomisAddressOwnerClass", absent())
            .withRequestBodyJsonPath("dpsUprn", absent())
            .withRequestBodyJsonPath("dpsAddressText", "Sheffield")
            .withRequestBodyJsonPath("mappingType", "NOMIS_CREATED"),
        )
      }

      @Test
      fun `should create success telemetry`() {
        verify(telemetryClient).trackEvent(
          eq("temporary-absence-sync-external-movement-inserted-success"),
          check {
            assertThat(it["offenderNo"]).isEqualTo("A1234BC")
            assertThat(it["bookingId"]).isEqualTo("12345")
            assertThat(it["movementSeq"]).isEqualTo("154")
            assertThat(it["nomisScheduledEventId"]).isNull()
            assertThat(it["nomisApplicationId"]).isNull()
            assertThat(it["directionCode"]).isEqualTo("OUT")
            assertThat(it["dpsMovementId"]).isEqualTo("$dpsMovementId")
          },
          isNull(),
        )
      }
    }

    @Nested
    @DisplayName("Happy path - unscheduled inbound movement")
    inner class HappyPathInbound {
      @BeforeEach
      fun setUp() {
        mappingApi.stubGetExternalMovementMapping(12345, 154, NOT_FOUND)
        nomisApi.stubGetTapMovementIn(movementSeq = 154, tapApplicationId = null, tapScheduleMovementInId = null, tapScheduleMovementOutId = null, city = "Sheffield")
        mappingApi.stubCreateExternalMovementMapping()
        dpsApi.stubSyncTapMovement(response = SyncResponse(dpsMovementId))

        sendMessage(tapMovementEvent(inserted = true, direction = "IN"))
          .also { waitForAnyProcessingToComplete() }
      }

      @Test
      fun `should check mapping`() {
        mappingApi.verify(getRequestedFor(urlPathEqualTo("/mapping/temporary-absence/external-movement/nomis-movement-id/12345/154")))
      }

      @Test
      fun `should NOT check application mapping`() {
        mappingApi.verify(
          count = 0,
          getRequestedFor(urlPathMatching("/mapping/taps/application/nomis-id/.*")),
        )
      }

      @Test
      fun `should NOT check scheduled movement mapping`() {
        mappingApi.verify(
          count = 0,
          getRequestedFor(urlPathEqualTo("/mapping/taps/schedule/nomis-id/.*")),
        )
      }

      @Test
      fun `should create DPS external movement`() {
        dpsApi.verify(
          putRequestedFor(urlPathEqualTo("/sync/temporary-absence-movements/A1234BC"))
            .withRequestBodyJsonPath("id", absent())
            .withRequestBodyJsonPath("legacyId", "12345_154")
            .withRequestBodyJsonPath("direction", "IN")
            .withRequestBodyJsonPath("location.description", absent())
            .withRequestBodyJsonPath("location.postcode", absent())
            .withRequestBodyJsonPath("location.uprn", absent())
            .withRequestBodyJsonPath("location.address", "Sheffield"),
        )
      }

      @Test
      fun `should create mapping`() {
        mappingApi.verify(
          postRequestedFor(urlPathEqualTo("/mapping/temporary-absence/external-movement"))
            .withRequestBodyJsonPath("prisonerNumber", "A1234BC")
            .withRequestBodyJsonPath("bookingId", 12345)
            .withRequestBodyJsonPath("nomisMovementSeq", 154)
            .withRequestBodyJsonPath("dpsMovementId", "$dpsMovementId")
            .withRequestBodyJsonPath("nomisAddressId", absent())
            .withRequestBodyJsonPath("nomisAddressOwnerClass", absent())
            .withRequestBodyJsonPath("dpsUprn", absent())
            .withRequestBodyJsonPath("dpsAddressText", "Sheffield")
            .withRequestBodyJsonPath("mappingType", "NOMIS_CREATED"),
        )
      }

      @Test
      fun `should create success telemetry`() {
        verify(telemetryClient).trackEvent(
          eq("temporary-absence-sync-external-movement-inserted-success"),
          check {
            assertThat(it["offenderNo"]).isEqualTo("A1234BC")
            assertThat(it["bookingId"]).isEqualTo("12345")
            assertThat(it["movementSeq"]).isEqualTo("154")
            assertThat(it["nomisScheduledParentEventId"]).isNull()
            assertThat(it["nomisScheduledEventId"]).isNull()
            assertThat(it["nomisApplicationId"]).isNull()
            assertThat(it["directionCode"]).isEqualTo("IN")
            assertThat(it["dpsMovementId"]).isEqualTo("$dpsMovementId")
          },
          isNull(),
        )
      }
    }

    @Nested
    inner class WhenDuplicateMapping {
      @BeforeEach
      fun setUp() {
        mappingApi.stubGetExternalMovementMapping(12345, 154, NOT_FOUND)
        nomisApi.stubGetTapMovementOut(movementSeq = 154, tapApplicationId = 111, tapScheduleOutId = 45678)
        mappingApi.stubGetTapApplicationMapping(nomisApplicationId = 111)
        mappingApi.stubGetTapScheduleMapping(nomisEventId = 45678)
        dpsApi.stubSyncTapMovement(response = SyncResponse(dpsMovementId))
        mappingApi.stubCreateExternalMovementMappingConflict(
          error = DuplicateMappingErrorResponse(
            moreInfo = DuplicateErrorContentObject(
              existing = ExternalMovementSyncMappingDto(
                prisonerNumber = "A1234BC",
                bookingId = 12345,
                nomisMovementSeq = 444,
                dpsMovementId = dpsMovementId,
                mappingType = ExternalMovementSyncMappingDto.MappingType.NOMIS_CREATED,
                "",
                0,
                "",
              ),
              duplicate = ExternalMovementSyncMappingDto(
                prisonerNumber = "A1234BC",
                bookingId = 12345,
                nomisMovementSeq = 154,
                dpsMovementId = dpsMovementId,
                mappingType = ExternalMovementSyncMappingDto.MappingType.NOMIS_CREATED,
                "",
                0,
                "",
              ),
            ),
            errorCode = 1409,
            status = DuplicateMappingErrorResponse.Status._409_CONFLICT,
            userMessage = "Duplicate mapping",
          ),
        )

        sendMessage(tapMovementEvent(inserted = true))
          .also { waitForAnyProcessingToComplete("temporary-absence-sync-external-movement-inserted-duplicate") }
      }

      @Test
      fun `should create DPS external movement`() {
        dpsApi.verify(
          putRequestedFor(urlPathEqualTo("/sync/temporary-absence-movements/A1234BC"))
            .withRequestBodyJsonPath("id", absent())
            .withRequestBodyJsonPath("legacyId", "12345_154")
            .withRequestBodyJsonPath("direction", "OUT")
            .withRequestBodyJsonPath("location.postcode", "S1 1AB")
            .withRequestBodyJsonPath("location.address", "full address"),
        )
      }

      @Test
      fun `should try to create mapping`() {
        mappingApi.verify(
          postRequestedFor(urlPathEqualTo("/mapping/temporary-absence/external-movement"))
            .withRequestBodyJsonPath("prisonerNumber", "A1234BC")
            .withRequestBodyJsonPath("bookingId", 12345)
            .withRequestBodyJsonPath("nomisMovementSeq", 154)
            .withRequestBodyJsonPath("dpsMovementId", "$dpsMovementId")
            .withRequestBodyJsonPath("mappingType", "NOMIS_CREATED"),
        )
      }

      @Test
      fun `should create success telemetry and duplicate telemetry`() {
        verify(telemetryClient).trackEvent(
          eq("temporary-absence-sync-external-movement-inserted-success"),
          check {
            assertThat(it["offenderNo"]).isEqualTo("A1234BC")
            assertThat(it["bookingId"]).isEqualTo("12345")
            assertThat(it["movementSeq"]).isEqualTo("154")
            assertThat(it["nomisScheduledEventId"]).isEqualTo("45678")
            assertThat(it["nomisApplicationId"]).isEqualTo("111")
            assertThat(it["directionCode"]).isEqualTo("OUT")
            assertThat(it["dpsMovementId"]).isEqualTo("$dpsMovementId")
          },
          isNull(),
        )
        verify(telemetryClient).trackEvent(
          eq("temporary-absence-sync-external-movement-inserted-duplicate"),
          check {
            assertThat(it["duplicateOffenderNo"]).isEqualTo("A1234BC")
            assertThat(it["existingOffenderNo"]).isEqualTo("A1234BC")
            assertThat(it["duplicateBookingId"]).isEqualTo("12345")
            assertThat(it["existingBookingId"]).isEqualTo("12345")
            assertThat(it["duplicateMovementSeq"]).isEqualTo("154")
            assertThat(it["existingMovementSeq"]).isEqualTo("444")
            assertThat(it["duplicateDpsMovementId"]).isEqualTo("$dpsMovementId")
            assertThat(it["existingDpsMovementId"]).isEqualTo("$dpsMovementId")
          },
          isNull(),
        )
      }
    }

    @Nested
    inner class WhenMappingCreateFailsOnce {

      @BeforeEach
      fun setUp() {
        mappingApi.stubGetExternalMovementMapping(12345, 154, NOT_FOUND)
        nomisApi.stubGetTapMovementOut(movementSeq = 154, tapApplicationId = 111, tapScheduleOutId = 45678)
        mappingApi.stubGetTapApplicationMapping(nomisApplicationId = 111)
        mappingApi.stubGetTapScheduleMapping(nomisEventId = 45678)
        mappingApi.stubCreateExternalMovementMappingFailureFollowedBySuccess()
        dpsApi.stubSyncTapMovement(response = SyncResponse(dpsMovementId))

        sendMessage(tapMovementEvent(inserted = true))
          .also { waitForAnyProcessingToComplete("temporary-absence-sync-external-movement-mapping-retry-created") }
      }

      @Test
      fun `should create DPS external movement`() {
        dpsApi.verify(
          putRequestedFor(urlPathEqualTo("/sync/temporary-absence-movements/A1234BC"))
            .withRequestBodyJsonPath("id", absent())
            .withRequestBodyJsonPath("legacyId", "12345_154")
            .withRequestBodyJsonPath("direction", "OUT")
            .withRequestBodyJsonPath("location.postcode", "S1 1AB")
            .withRequestBodyJsonPath("location.address", "full address"),
        )
      }

      @Test
      fun `should create mapping on 2nd call`() {
        mappingApi.verify(
          count = 2,
          postRequestedFor(urlPathEqualTo("/mapping/temporary-absence/external-movement"))
            .withRequestBodyJsonPath("prisonerNumber", "A1234BC")
            .withRequestBodyJsonPath("bookingId", 12345)
            .withRequestBodyJsonPath("nomisMovementSeq", 154)
            .withRequestBodyJsonPath("dpsMovementId", "$dpsMovementId")
            .withRequestBodyJsonPath("mappingType", "NOMIS_CREATED"),
        )
      }

      @Test
      fun `should publish success telemetry`() {
        verify(telemetryClient).trackEvent(
          eq("temporary-absence-sync-external-movement-inserted-success"),
          check {
            assertThat(it["offenderNo"]).isEqualTo("A1234BC")
            assertThat(it["bookingId"]).isEqualTo("12345")
            assertThat(it["movementSeq"]).isEqualTo("154")
            assertThat(it["nomisScheduledEventId"]).isEqualTo("45678")
            assertThat(it["nomisApplicationId"]).isEqualTo("111")
            assertThat(it["directionCode"]).isEqualTo("OUT")
            assertThat(it["dpsMovementId"]).isEqualTo("$dpsMovementId")
          },
          isNull(),
        )
      }

      @Test
      fun `should publish mapping created telemetry`() {
        verify(telemetryClient).trackEvent(
          eq("temporary-absence-sync-external-movement-mapping-retry-created"),
          check {
            assertThat(it["offenderNo"]).isEqualTo("A1234BC")
            assertThat(it["bookingId"]).isEqualTo("12345")
            assertThat(it["movementSeq"]).isEqualTo("154")
            assertThat(it["nomisScheduledEventId"]).isEqualTo("45678")
            assertThat(it["nomisApplicationId"]).isEqualTo("111")
            assertThat(it["dpsMovementId"]).isEqualTo("$dpsMovementId")
          },
          isNull(),
        )
      }
    }
  }

  @Nested
  @DisplayName("EXTERNAL_MOVEMENT-CHANGED (updated)")
  inner class TemporaryAbsenceExternalMovementUpdated {
    private val dpsMovementId = UUID.randomUUID()
    private val dpsOccurrenceId = UUID.randomUUID()

    @Nested
    inner class HappyPathOutboundMovement {
      @BeforeEach
      fun setUp() {
        mappingApi.stubGetExternalMovementMapping(12345, 154, dpsMovementId)
        nomisApi.stubGetTapMovementOut(movementSeq = 154, tapScheduleOutId = 45678)
        mappingApi.stubGetTapApplicationMapping(111)
        mappingApi.stubGetTapScheduleMapping(45678, dpsOccurrenceId)
        dpsApi.stubSyncTapMovement(response = SyncResponse(dpsMovementId))

        sendMessage(tapMovementEvent(direction = "OUT"))
          .also { waitForAnyProcessingToComplete() }
      }

      @Test
      fun `should get mapping`() {
        mappingApi.verify(getRequestedFor(urlPathEqualTo("/mapping/temporary-absence/external-movement/nomis-movement-id/12345/154")))
      }

      @Test
      fun `should get NOMIS external movement`() {
        nomisApi.verify(getRequestedFor(urlPathEqualTo("/movements/A1234BC/taps/movement/out/12345/154")))
      }

      @Test
      fun `should update DPS external movement`() {
        dpsApi.verify(
          putRequestedFor(urlPathEqualTo("/sync/temporary-absence-movements/A1234BC"))
            .withRequestBodyJsonPath("id", dpsMovementId)
            .withRequestBodyJsonPath("occurrenceId", dpsOccurrenceId)
            .withRequestBodyJsonPath("legacyId", "12345_154")
            .withRequestBodyJsonPath("direction", "OUT")
            .withRequestBodyJsonPath("location.postcode", "S1 1AB")
            .withRequestBodyJsonPath("location.address", "full address")
            .withRequestBodyJsonPath("location.description", "some description")
            .withRequestBodyJsonPath("location.uprn", absent()),
        )
      }

      @Test
      fun `should create success telemetry`() {
        verify(telemetryClient).trackEvent(
          eq("temporary-absence-sync-external-movement-updated-success"),
          check {
            assertThat(it["offenderNo"]).isEqualTo("A1234BC")
            assertThat(it["bookingId"]).isEqualTo("12345")
            assertThat(it["movementSeq"]).isEqualTo("154")
            assertThat(it["directionCode"]).isEqualTo("OUT")
            assertThat(it["nomisScheduledEventId"]).isEqualTo("45678")
            assertThat(it["nomisApplicationId"]).isEqualTo("111")
            assertThat(it["dpsMovementId"]).isEqualTo("$dpsMovementId")
            assertThat(it["nomisAddressId"]).isEqualTo("321")
            assertThat(it["nomisAddressOwnerClass"]).isEqualTo("OFF")
          },
          isNull(),
        )
      }
    }

    @Nested
    inner class WhenAddressChangedForScheduledOutbound {
      val newAddress = "new address"
      val newAddressId = 123L

      @BeforeEach
      fun setUp() {
        mappingApi.stubGetExternalMovementMapping(12345, 154, dpsMovementId)
        nomisApi.stubGetTapMovementOut(movementSeq = 154, tapScheduleOutId = 45678, address = newAddress, addressId = newAddressId)
        mappingApi.stubGetTapApplicationMapping(111)
        mappingApi.stubGetTapScheduleMapping(45678, dpsOccurrenceId)
        dpsApi.stubSyncTapMovement(response = SyncResponse(dpsMovementId))
        mappingApi.stubUpdateExternalMovementMapping()

        sendMessage(tapMovementEvent(direction = "OUT"))
          .also { waitForAnyProcessingToComplete() }
      }

      @Test
      fun `should update DPS external movement`() {
        dpsApi.verify(
          putRequestedFor(urlPathEqualTo("/sync/temporary-absence-movements/A1234BC"))
            .withRequestBodyJsonPath("id", dpsMovementId)
            .withRequestBodyJsonPath("occurrenceId", dpsOccurrenceId)
            .withRequestBodyJsonPath("legacyId", "12345_154")
            .withRequestBodyJsonPath("direction", "OUT")
            .withRequestBodyJsonPath("location.postcode", "S1 1AB")
            .withRequestBodyJsonPath("location.address", "new address"),
        )
      }

      @Test
      fun `should update mapping`() {
        mappingApi.verify(
          putRequestedFor(urlPathEqualTo("/mapping/temporary-absence/external-movement"))
            .withRequestBodyJsonPath("dpsMovementId", dpsMovementId)
            .withRequestBodyJsonPath("dpsAddressText", newAddress)
            .withRequestBodyJsonPath("nomisAddressId", newAddressId),
        )
      }

      @Test
      fun `should create success telemetry`() {
        verify(telemetryClient).trackEvent(
          eq("temporary-absence-sync-external-movement-updated-success"),
          check {
            assertThat(it["offenderNo"]).isEqualTo("A1234BC")
            assertThat(it["bookingId"]).isEqualTo("12345")
            assertThat(it["movementSeq"]).isEqualTo("154")
            assertThat(it["directionCode"]).isEqualTo("OUT")
            assertThat(it["nomisScheduledEventId"]).isEqualTo("45678")
            assertThat(it["nomisApplicationId"]).isEqualTo("111")
            assertThat(it["nomisAddressId"]).isEqualTo("$newAddressId")
            assertThat(it["dpsMovementId"]).isEqualTo("$dpsMovementId")
          },
          isNull(),
        )
      }
    }

    @Nested
    inner class WhenMappingUpdateFailsOnce {
      val newAddress = "new address"
      val newAddressId = 123L

      @BeforeEach
      fun setUp() {
        mappingApi.stubGetExternalMovementMapping(12345, 154, dpsMovementId)
        nomisApi.stubGetTapMovementOut(movementSeq = 154, tapScheduleOutId = 45678, address = newAddress, addressId = newAddressId)
        mappingApi.stubGetTapApplicationMapping(111)
        mappingApi.stubGetTapScheduleMapping(45678, dpsOccurrenceId)
        dpsApi.stubSyncTapMovement(response = SyncResponse(dpsMovementId))
        mappingApi.stubUpdateExternalMovementMappingFailureFollowedBySuccess()

        sendMessage(tapMovementEvent(direction = "OUT"))
          .also { waitForAnyProcessingToComplete("temporary-absence-sync-external-movement-mapping-updated") }
      }

      @Test
      fun `should update DPS external movement`() {
        dpsApi.verify(
          putRequestedFor(urlPathEqualTo("/sync/temporary-absence-movements/A1234BC"))
            .withRequestBodyJsonPath("id", dpsMovementId)
            .withRequestBodyJsonPath("occurrenceId", dpsOccurrenceId)
            .withRequestBodyJsonPath("location.address", "new address"),
        )
      }

      @Test
      fun `should update mapping`() {
        mappingApi.verify(
          count = 2,
          putRequestedFor(urlPathEqualTo("/mapping/temporary-absence/external-movement"))
            .withRequestBodyJsonPath("dpsMovementId", dpsMovementId)
            .withRequestBodyJsonPath("dpsAddressText", newAddress)
            .withRequestBodyJsonPath("nomisAddressId", newAddressId),
        )
      }

      @Test
      fun `should create success telemetry`() {
        verify(telemetryClient).trackEvent(
          eq("temporary-absence-sync-external-movement-updated-success"),
          check {
            assertThat(it["offenderNo"]).isEqualTo("A1234BC")
            assertThat(it["bookingId"]).isEqualTo("12345")
            assertThat(it["movementSeq"]).isEqualTo("154")
            assertThat(it["directionCode"]).isEqualTo("OUT")
            assertThat(it["nomisScheduledEventId"]).isEqualTo("45678")
            assertThat(it["nomisApplicationId"]).isEqualTo("111")
            assertThat(it["nomisAddressId"]).isEqualTo("123")
            assertThat(it["dpsMovementId"]).isEqualTo("$dpsMovementId")
          },
          isNull(),
        )
      }
    }

    @Nested
    @DisplayName("Happy path outbound movement - unscheduled")
    inner class HappyPathUnscheduledOutboundMovement {
      @BeforeEach
      fun setUp() {
        mappingApi.stubGetExternalMovementMapping(12345, 154, dpsMovementId, "Sheffield")
        nomisApi.stubGetTapMovementOut(movementSeq = 154, tapApplicationId = null, tapScheduleOutId = null, city = "Sheffield")
        mappingApi.stubGetTapApplicationMapping(111, NOT_FOUND)
        mappingApi.stubGetTapScheduleMapping(45678, NOT_FOUND)
        dpsApi.stubSyncTapMovement(response = SyncResponse(dpsMovementId))

        sendMessage(tapMovementEvent(direction = "OUT"))
          .also { waitForAnyProcessingToComplete() }
      }

      @Test
      fun `should get mapping`() {
        mappingApi.verify(getRequestedFor(urlPathEqualTo("/mapping/temporary-absence/external-movement/nomis-movement-id/12345/154")))
      }

      @Test
      fun `should get NOMIS external movement`() {
        nomisApi.verify(getRequestedFor(urlPathEqualTo("/movements/A1234BC/taps/movement/out/12345/154")))
      }

      @Test
      fun `should update DPS external movement`() {
        dpsApi.verify(
          putRequestedFor(urlPathEqualTo("/sync/temporary-absence-movements/A1234BC"))
            .withRequestBodyJsonPath("id", dpsMovementId)
            .withRequestBodyJsonPath("occurrenceId", absent())
            .withRequestBodyJsonPath("legacyId", "12345_154")
            .withRequestBodyJsonPath("direction", "OUT")
            .withRequestBodyJsonPath("location.postcode", absent())
            .withRequestBodyJsonPath("location.address", "Sheffield")
            .withRequestBodyJsonPath("location.description", absent())
            .withRequestBodyJsonPath("location.uprn", absent()),
        )
      }

      @Test
      fun `should create success telemetry`() {
        verify(telemetryClient).trackEvent(
          eq("temporary-absence-sync-external-movement-updated-success"),
          check {
            assertThat(it["offenderNo"]).isEqualTo("A1234BC")
            assertThat(it["bookingId"]).isEqualTo("12345")
            assertThat(it["movementSeq"]).isEqualTo("154")
            assertThat(it["directionCode"]).isEqualTo("OUT")
            assertThat(it["nomisScheduledEventId"]).isNull()
            assertThat(it["nomisApplicationId"]).isNull()
            assertThat(it["dpsMovementId"]).isEqualTo("$dpsMovementId")
          },
          isNull(),
        )
      }
    }

    @Nested
    inner class HappyPathInboundMovement {
      @BeforeEach
      fun setUp() {
        mappingApi.stubGetExternalMovementMapping(12345, 154, dpsMovementId)
        nomisApi.stubGetTapMovementIn(movementSeq = 154, tapScheduleMovementInId = 45678, tapScheduleMovementOutId = 23456)
        mappingApi.stubGetTapApplicationMapping(111)
        mappingApi.stubGetTapScheduleMapping(23456, dpsOccurrenceId)
        dpsApi.stubSyncTapMovement(response = SyncResponse(dpsMovementId))

        sendMessage(tapMovementEvent(direction = "IN"))
          .also { waitForAnyProcessingToComplete() }
      }

      @Test
      fun `should get mapping`() {
        mappingApi.verify(getRequestedFor(urlPathEqualTo("/mapping/temporary-absence/external-movement/nomis-movement-id/12345/154")))
      }

      @Test
      fun `should get NOMIS external movement`() {
        nomisApi.verify(getRequestedFor(urlPathEqualTo("/movements/A1234BC/taps/movement/in/12345/154")))
      }

      @Test
      fun `should update DPS external movement`() {
        dpsApi.verify(
          putRequestedFor(urlPathEqualTo("/sync/temporary-absence-movements/A1234BC"))
            .withRequestBodyJsonPath("id", dpsMovementId)
            .withRequestBodyJsonPath("occurrenceId", dpsOccurrenceId)
            .withRequestBodyJsonPath("legacyId", "12345_154")
            .withRequestBodyJsonPath("direction", "IN")
            .withRequestBodyJsonPath("location.postcode", "S1 1AB")
            .withRequestBodyJsonPath("location.address", "full address")
            .withRequestBodyJsonPath("location.description ", "some description"),
        )
      }

      @Test
      fun `should create success telemetry`() {
        verify(telemetryClient).trackEvent(
          eq("temporary-absence-sync-external-movement-updated-success"),
          check {
            assertThat(it["offenderNo"]).isEqualTo("A1234BC")
            assertThat(it["bookingId"]).isEqualTo("12345")
            assertThat(it["movementSeq"]).isEqualTo("154")
            assertThat(it["directionCode"]).isEqualTo("IN")
            assertThat(it["nomisScheduledParentEventId"]).isEqualTo("23456")
            assertThat(it["nomisScheduledEventId"]).isEqualTo("45678")
            assertThat(it["nomisApplicationId"]).isEqualTo("111")
            assertThat(it["dpsMovementId"]).isEqualTo("$dpsMovementId")
          },
          isNull(),
        )
      }
    }

    @Nested
    inner class WhenAddressChangedForScheduledInboundMovement {
      val newAddress = "new address"
      val newAddressId = 123L

      @BeforeEach
      fun setUp() {
        mappingApi.stubGetExternalMovementMapping(12345, 154, dpsMovementId)
        nomisApi.stubGetTapMovementIn(movementSeq = 154, tapScheduleMovementInId = 45678, tapScheduleMovementOutId = 23456, address = newAddress, addressId = newAddressId)
        mappingApi.stubGetTapApplicationMapping(111)
        mappingApi.stubGetTapScheduleMapping(23456, dpsOccurrenceId)
        dpsApi.stubSyncTapMovement(response = SyncResponse(dpsMovementId))
        mappingApi.stubUpdateExternalMovementMapping()

        sendMessage(tapMovementEvent(direction = "IN"))
          .also { waitForAnyProcessingToComplete() }
      }

      @Test
      fun `should update DPS external movement`() {
        dpsApi.verify(
          putRequestedFor(urlPathEqualTo("/sync/temporary-absence-movements/A1234BC"))
            .withRequestBodyJsonPath("id", dpsMovementId)
            .withRequestBodyJsonPath("occurrenceId", dpsOccurrenceId)
            .withRequestBodyJsonPath("legacyId", "12345_154")
            .withRequestBodyJsonPath("direction", "IN")
            .withRequestBodyJsonPath("location.postcode", "S1 1AB")
            .withRequestBodyJsonPath("location.address", "new address")
            .withRequestBodyJsonPath("location.description", "some description"),
        )
      }

      @Test
      fun `should update mapping`() {
        mappingApi.verify(
          putRequestedFor(urlPathEqualTo("/mapping/temporary-absence/external-movement"))
            .withRequestBodyJsonPath("dpsMovementId", dpsMovementId)
            .withRequestBodyJsonPath("dpsAddressText", newAddress)
            .withRequestBodyJsonPath("nomisAddressId", newAddressId),
        )
      }

      @Test
      fun `should create success telemetry`() {
        verify(telemetryClient).trackEvent(
          eq("temporary-absence-sync-external-movement-updated-success"),
          check {
            assertThat(it["offenderNo"]).isEqualTo("A1234BC")
            assertThat(it["bookingId"]).isEqualTo("12345")
            assertThat(it["movementSeq"]).isEqualTo("154")
            assertThat(it["directionCode"]).isEqualTo("IN")
            assertThat(it["nomisScheduledParentEventId"]).isEqualTo("23456")
            assertThat(it["nomisScheduledEventId"]).isEqualTo("45678")
            assertThat(it["nomisApplicationId"]).isEqualTo("111")
            assertThat(it["nomisAddressId"]).isEqualTo("$newAddressId")
            assertThat(it["dpsMovementId"]).isEqualTo("$dpsMovementId")
          },
          isNull(),
        )
      }
    }

    @Nested
    @DisplayName("Happy path inbound movement - unscheduled")
    inner class HappyPathUnscheduledInboundMovement {
      @BeforeEach
      fun setUp() {
        mappingApi.stubGetExternalMovementMapping(12345, 154, dpsMovementId, "Sheffield")
        nomisApi.stubGetTapMovementIn(movementSeq = 154, tapApplicationId = null, tapScheduleMovementInId = null, tapScheduleMovementOutId = null, city = "Sheffield")
        mappingApi.stubGetTapApplicationMapping(111, NOT_FOUND)
        mappingApi.stubGetTapScheduleMapping(45678, NOT_FOUND)
        dpsApi.stubSyncTapMovement(response = SyncResponse(dpsMovementId))

        sendMessage(tapMovementEvent(direction = "IN"))
          .also { waitForAnyProcessingToComplete() }
      }

      @Test
      fun `should get mapping`() {
        mappingApi.verify(getRequestedFor(urlPathEqualTo("/mapping/temporary-absence/external-movement/nomis-movement-id/12345/154")))
      }

      @Test
      fun `should get NOMIS external movement`() {
        nomisApi.verify(getRequestedFor(urlPathEqualTo("/movements/A1234BC/taps/movement/in/12345/154")))
      }

      @Test
      fun `should update DPS external movement`() {
        dpsApi.verify(
          putRequestedFor(urlPathEqualTo("/sync/temporary-absence-movements/A1234BC"))
            .withRequestBodyJsonPath("id", dpsMovementId)
            .withRequestBodyJsonPath("occurrenceId", absent())
            .withRequestBodyJsonPath("legacyId", "12345_154")
            .withRequestBodyJsonPath("direction", "IN")
            .withRequestBodyJsonPath("location.postcode", absent())
            .withRequestBodyJsonPath("location.address", "Sheffield")
            .withRequestBodyJsonPath("location.description", absent())
            .withRequestBodyJsonPath("location.uprn", absent()),
        )
      }

      @Test
      fun `should create success telemetry`() {
        verify(telemetryClient).trackEvent(
          eq("temporary-absence-sync-external-movement-updated-success"),
          check {
            assertThat(it["offenderNo"]).isEqualTo("A1234BC")
            assertThat(it["bookingId"]).isEqualTo("12345")
            assertThat(it["movementSeq"]).isEqualTo("154")
            assertThat(it["directionCode"]).isEqualTo("IN")
            assertThat(it["nomisScheduledParentEventId"]).isNull()
            assertThat(it["nomisScheduledEventId"]).isNull()
            assertThat(it["nomisApplicationId"]).isNull()
            assertThat(it["dpsMovementId"]).isEqualTo("$dpsMovementId")
          },
          isNull(),
        )
      }
    }

    @Nested
    @DisplayName("Happy path inbound movement - unscheduled - mapping changed")
    inner class WhenUnscheduledMovementMappingChanged {
      val newCity = "Leeds"

      @BeforeEach
      fun setUp() {
        mappingApi.stubGetExternalMovementMapping(12345, 154, dpsMovementId, "Sheffield")
        nomisApi.stubGetTapMovementIn(movementSeq = 154, tapApplicationId = null, tapScheduleMovementInId = null, tapScheduleMovementOutId = null, city = newCity)
        mappingApi.stubGetTapApplicationMapping(111, NOT_FOUND)
        mappingApi.stubGetTapScheduleMapping(45678, NOT_FOUND)
        dpsApi.stubSyncTapMovement(response = SyncResponse(dpsMovementId))
        mappingApi.stubUpdateExternalMovementMapping()

        sendMessage(tapMovementEvent(direction = "IN"))
          .also { waitForAnyProcessingToComplete() }
      }

      @Test
      fun `should update DPS external movement`() {
        dpsApi.verify(
          putRequestedFor(urlPathEqualTo("/sync/temporary-absence-movements/A1234BC"))
            .withRequestBodyJsonPath("id", dpsMovementId)
            .withRequestBodyJsonPath("occurrenceId", absent())
            .withRequestBodyJsonPath("legacyId", "12345_154")
            .withRequestBodyJsonPath("direction", "IN")
            .withRequestBodyJsonPath("location.postcode", absent())
            .withRequestBodyJsonPath("location.address", newCity)
            .withRequestBodyJsonPath("location.description", absent())
            .withRequestBodyJsonPath("location.uprn", absent()),
        )
      }

      @Test
      fun `should update mapping`() {
        mappingApi.verify(
          putRequestedFor(urlPathEqualTo("/mapping/temporary-absence/external-movement"))
            .withRequestBodyJsonPath("dpsMovementId", dpsMovementId)
            .withRequestBodyJsonPath("dpsOccurrenceId", absent())
            .withRequestBodyJsonPath("dpsAddressText", newCity),
        )
      }

      @Test
      fun `should create success telemetry`() {
        verify(telemetryClient).trackEvent(
          eq("temporary-absence-sync-external-movement-updated-success"),
          check {
            assertThat(it["offenderNo"]).isEqualTo("A1234BC")
            assertThat(it["bookingId"]).isEqualTo("12345")
            assertThat(it["movementSeq"]).isEqualTo("154")
            assertThat(it["directionCode"]).isEqualTo("IN")
            assertThat(it["nomisScheduledParentEventId"]).isNull()
            assertThat(it["nomisScheduledEventId"]).isNull()
            assertThat(it["nomisApplicationId"]).isNull()
            assertThat(it["dpsMovementId"]).isEqualTo("$dpsMovementId")
          },
          isNull(),
        )
      }
    }

    @Nested
    @DisplayName("Inbound movement - unscheduled - mapping update works on a retry")
    inner class WhenUnscheduledMovementMappingFailsOnce {
      val newCity = "Leeds"

      @BeforeEach
      fun setUp() {
        mappingApi.stubGetExternalMovementMapping(12345, 154, dpsMovementId, "Sheffield")
        nomisApi.stubGetTapMovementIn(movementSeq = 154, tapApplicationId = null, tapScheduleMovementInId = null, tapScheduleMovementOutId = null, city = newCity)
        mappingApi.stubGetTapApplicationMapping(111, NOT_FOUND)
        mappingApi.stubGetTapScheduleMapping(45678, NOT_FOUND)
        dpsApi.stubSyncTapMovement(response = SyncResponse(dpsMovementId))
        mappingApi.stubUpdateExternalMovementMappingFailureFollowedBySuccess()

        sendMessage(tapMovementEvent(direction = "IN"))
          .also { waitForAnyProcessingToComplete("temporary-absence-sync-external-movement-mapping-updated") }
      }

      @Test
      fun `should update DPS external movement`() {
        dpsApi.verify(
          putRequestedFor(urlPathEqualTo("/sync/temporary-absence-movements/A1234BC"))
            .withRequestBodyJsonPath("id", dpsMovementId)
            .withRequestBodyJsonPath("occurrenceId", absent())
            .withRequestBodyJsonPath("legacyId", "12345_154")
            .withRequestBodyJsonPath("direction", "IN")
            .withRequestBodyJsonPath("location.postcode", absent())
            .withRequestBodyJsonPath("location.address", newCity)
            .withRequestBodyJsonPath("location.description", absent())
            .withRequestBodyJsonPath("location.uprn", absent()),
        )
      }

      @Test
      fun `should update mapping`() {
        mappingApi.verify(
          count = 2,
          putRequestedFor(urlPathEqualTo("/mapping/temporary-absence/external-movement"))
            .withRequestBodyJsonPath("dpsMovementId", dpsMovementId)
            .withRequestBodyJsonPath("dpsOccurrenceId", absent())
            .withRequestBodyJsonPath("dpsAddressText", newCity),
        )
      }

      @Test
      fun `should create success telemetry`() {
        verify(telemetryClient).trackEvent(
          eq("temporary-absence-sync-external-movement-updated-success"),
          check {
            assertThat(it["offenderNo"]).isEqualTo("A1234BC")
            assertThat(it["bookingId"]).isEqualTo("12345")
            assertThat(it["movementSeq"]).isEqualTo("154")
            assertThat(it["directionCode"]).isEqualTo("IN")
            assertThat(it["nomisScheduledParentEventId"]).isNull()
            assertThat(it["nomisScheduledEventId"]).isNull()
            assertThat(it["nomisApplicationId"]).isNull()
            assertThat(it["dpsMovementId"]).isEqualTo("$dpsMovementId")
          },
          isNull(),
        )
      }
    }

    @Nested
    inner class WhenUpdatedInDps {
      @BeforeEach
      fun setUp() {
        sendMessage(tapMovementEvent(auditModuleName = "DPS_SYNCHRONISATION"))
          .also { waitForAnyProcessingToComplete() }
      }

      @Test
      fun `should NOT update DPS external movement`() {
        dpsApi.verify(
          0,
          putRequestedFor(urlPathEqualTo("/sync/temporary-absence-movements/A1234BC")),
        )
      }

      @Test
      fun `should NOT get mapping`() {
        mappingApi.verify(
          count = 0,
          getRequestedFor(urlPathEqualTo("/mapping/temporary-absence/external-movement/nomis-movement-id/12345/154")),
        )
      }

      @Test
      fun `should create telemetry`() {
        verify(telemetryClient).trackEvent(
          eq("temporary-absence-sync-external-movement-updated-skipped"),
          check {
            assertThat(it["offenderNo"]).isEqualTo("A1234BC")
            assertThat(it["bookingId"]).isEqualTo("12345")
            assertThat(it["movementSeq"]).isEqualTo("154")
            assertThat(it["directionCode"]).isEqualTo("OUT")
          },
          isNull(),
        )
      }
    }
  }

  @Nested
  @DisplayName("EXTERNAL_MOVEMENT-CHANGED (deleted)")
  inner class TemporaryAbsenceExternalMovementDeleted {
    private val dpsMovementId = UUID.randomUUID()

    @Nested
    inner class HappyPathOutboundMovement {
      @BeforeEach
      fun setUp() {
        mappingApi.stubGetExternalMovementMapping(12345, 154, dpsMovementId)
        mappingApi.stubDeleteExternalMovementMapping(12345, 154)
        dpsApi.stubDeleteTapMovement(dpsMovementId)

        sendMessage(tapMovementEvent(deleted = true, direction = "OUT"))
          .also { waitForAnyProcessingToComplete() }
      }

      @Test
      fun `should get mapping`() {
        mappingApi.verify(getRequestedFor(urlPathEqualTo("/mapping/temporary-absence/external-movement/nomis-movement-id/12345/154")))
      }

      @Test
      fun `should delete mapping`() {
        mappingApi.verify(deleteRequestedFor(urlPathEqualTo("/mapping/temporary-absence/external-movement/nomis-movement-id/12345/154")))
      }

      @Test
      fun `should delete DPS external movement`() {
        dpsApi.verify(deleteRequestedFor(urlPathEqualTo("/sync/temporary-absence-movements/$dpsMovementId")))
      }

      @Test
      fun `should create success telemetry`() {
        verify(telemetryClient).trackEvent(
          eq("temporary-absence-sync-external-movement-deleted-success"),
          check {
            assertThat(it["offenderNo"]).isEqualTo("A1234BC")
            assertThat(it["bookingId"]).isEqualTo("12345")
            assertThat(it["movementSeq"]).isEqualTo("154")
            assertThat(it["directionCode"]).isEqualTo("OUT")
            assertThat(it["dpsMovementId"]).isEqualTo("$dpsMovementId")
          },
          isNull(),
        )
      }
    }

    @Nested
    inner class HappyPathInboundMovement {
      @BeforeEach
      fun setUp() {
        mappingApi.stubGetExternalMovementMapping(12345, 154, dpsMovementId)
        mappingApi.stubDeleteExternalMovementMapping(12345, 154)
        dpsApi.stubDeleteTapMovement(dpsMovementId)

        sendMessage(tapMovementEvent(deleted = true, direction = "IN"))
          .also { waitForAnyProcessingToComplete() }
      }

      @Test
      fun `should get mapping`() {
        mappingApi.verify(getRequestedFor(urlPathEqualTo("/mapping/temporary-absence/external-movement/nomis-movement-id/12345/154")))
      }

      @Test
      fun `should delete mapping`() {
        mappingApi.verify(deleteRequestedFor(urlPathEqualTo("/mapping/temporary-absence/external-movement/nomis-movement-id/12345/154")))
      }

      @Test
      fun `should delete DPS external movement`() {
        dpsApi.verify(deleteRequestedFor(urlPathEqualTo("/sync/temporary-absence-movements/$dpsMovementId")))
      }

      @Test
      fun `should create success telemetry`() {
        verify(telemetryClient).trackEvent(
          eq("temporary-absence-sync-external-movement-deleted-success"),
          check {
            assertThat(it["offenderNo"]).isEqualTo("A1234BC")
            assertThat(it["bookingId"]).isEqualTo("12345")
            assertThat(it["movementSeq"]).isEqualTo("154")
            assertThat(it["directionCode"]).isEqualTo("IN")
            assertThat(it["dpsMovementId"]).isEqualTo("$dpsMovementId")
          },
          isNull(),
        )
      }
    }

    @Nested
    inner class WhenMappingDoesNotExist {
      @BeforeEach
      fun setUp() {
        mappingApi.stubGetExternalMovementMapping(12345, 154, NOT_FOUND)

        sendMessage(tapMovementEvent(deleted = true))
          .also { waitForAnyProcessingToComplete() }
      }

      @Test
      fun `should NOT delete DPS external movement`() {
        dpsApi.verify(
          0,
          deleteRequestedFor(urlPathMatching("/sync/temporary-absence-movement/.*")),
        )
      }

      @Test
      fun `should get mapping`() {
        mappingApi.verify(
          getRequestedFor(urlPathEqualTo("/mapping/temporary-absence/external-movement/nomis-movement-id/12345/154")),
        )
      }

      @Test
      fun `should create telemetry`() {
        verify(telemetryClient).trackEvent(
          eq("temporary-absence-sync-external-movement-deleted-ignored"),
          check {
            assertThat(it["offenderNo"]).isEqualTo("A1234BC")
            assertThat(it["bookingId"]).isEqualTo("12345")
            assertThat(it["movementSeq"]).isEqualTo("154")
            assertThat(it["directionCode"]).isEqualTo("OUT")
          },
          isNull(),
        )
      }
    }
  }

  private fun sendMessage(event: String) = awsSqsExternalMovementsOffenderEventsClient.sendMessage(
    externalMovementsQueueOffenderEventsUrl,
    event,
  )

  private fun tapMovementEvent(
    auditModuleName: String = "OCUCANTR",
    movementType: String = "TAP",
    direction: String = "OUT",
    inserted: Boolean = false,
    deleted: Boolean = false,
  ) = // language=JSON
    """{
         "Type" : "Notification",
         "MessageId" : "83354f3f-45cb-5e8e-9266-2e0fa1e91dcc",
         "TopicArn" : "arn:aws:sns:eu-west-2:754256621582:cloud-platform-Digital-Prison-Services-160f3055cc4e04c4105ee85f2ed1fccb",
         "Message" : "{\"eventType\":\"EXTERNAL_MOVEMENT-CHANGED\",\"eventDatetime\":\"2025-09-02T13:24:01\",\"bookingId\":12345,\"offenderIdDisplay\":\"A1234BC\",\"nomisEventType\":\"EXTERNAL_MOVEMENT-CHANGED\",\"movementSeq\":154,\"movementDateTime\":\"2025-09-02T13:23:00\",\"movementType\":\"$movementType\",\"movementReasonCode\":\"OPA\",\"directionCode\":\"$direction\",\"fromAgencyLocationId\":\"NWI\",\"recordInserted\":$inserted,\"recordDeleted\":$deleted,\"auditModuleName\":\"$auditModuleName\"}",
         "Timestamp" : "2025-09-02T12:24:02.004Z",
         "SignatureVersion" : "1",
         "Signature" : "HDyAhgG0o4XV4eJjuLODqeyBfZfsUxLcqVyiwQQIvegES5QnWmfgKwzb+D3az1QgiJBaknq/NIR+C/71O0AFFTSRN3RFOQyLrPZBeynGIyBNzGgeJjPGrZrSBqYegtJKJPDQEQLNepk2Jgqjiu3NgKT0gq5z5mU7G45wqkC81F3/DJUAHb98BmLbWK/cibnaHrvgXW493IbWPLXQENzJ9rDJKekz6sdY6+qHcOg57xdho/Xlb6VFo28/9qoVqA+A2MUBlHBRI1BSK0QVu8duri5DHjE0I2/UG7emlt9vZ6KtxyXz/ZmFVC/nY2OD0OgFJvP7DaAJbgMo/rbGe1JlYQ==",
         "SigningCertURL" : "https://sns.eu-west-2.amazonaws.com/SimpleNotificationService-6209c161c6221fdf56ec1eb5c821d112.pem",
         "UnsubscribeURL" : "https://sns.eu-west-2.amazonaws.com/?Action=Unsubscribe&SubscriptionArn=arn:aws:sns:eu-west-2:754256621582:cloud-platform-Digital-Prison-Services-160f3055cc4e04c4105ee85f2ed1fccb:217952f4-706e-4451-84d7-e349633331e0",
         "MessageAttributes" : {
           "code" : {"Type":"String","Value":"$movementType-$direction"},
           "publishedAt" : {"Type":"String","Value":"2025-09-02T13:24:02.000596721+01:00"},
           "traceparent" : {"Type":"String","Value":"00-b525e7a9b05de2c11a64ff93a0ef292b-f8f18e48747b11fd-01"},
           "eventType" : {"Type":"String","Value":"EXTERNAL_MOVEMENT-CHANGED"}
         }
        }
    """.trimMargin()
}
