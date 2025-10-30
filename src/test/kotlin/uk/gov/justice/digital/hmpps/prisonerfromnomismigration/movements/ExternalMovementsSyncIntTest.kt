package uk.gov.justice.digital.hmpps.prisonerfromnomismigration.movements

import com.github.tomakehurst.wiremock.client.WireMock.absent
import com.github.tomakehurst.wiremock.client.WireMock.containing
import com.github.tomakehurst.wiremock.client.WireMock.deleteRequestedFor
import com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor
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
import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.kotlin.check
import org.mockito.kotlin.eq
import org.mockito.kotlin.isNull
import org.mockito.kotlin.verify
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.system.CapturedOutput
import org.springframework.boot.test.system.OutputCaptureExtension
import org.springframework.http.HttpStatus.NOT_FOUND
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.integration.SqsIntegrationTestBase
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.integration.sendMessage
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.movements.ExternalMovementsDpsApiExtension.Companion.dpsExtMovementsServer
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.movements.model.SyncResponse
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomismappings.model.DuplicateErrorContentObject
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomismappings.model.DuplicateMappingErrorResponse
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomismappings.model.ExternalMovementSyncMappingDto
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomismappings.model.ScheduledMovementSyncMappingDto
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomismappings.model.TemporaryAbsenceApplicationSyncMappingDto
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomismappings.model.TemporaryAbsenceOutsideMovementSyncMappingDto
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.wiremock.withRequestBodyJsonPath
import uk.gov.justice.hmpps.sqs.countAllMessagesOnQueue
import java.time.LocalDate
import java.time.LocalDateTime
import java.util.*

@ExtendWith(OutputCaptureExtension::class)
class ExternalMovementsSyncIntTest(
  @Autowired private val nomisApi: ExternalMovementsNomisApiMockServer,
  @Autowired private val mappingApi: ExternalMovementsMappingApiMockServer,
) : SqsIntegrationTestBase() {

  private val dpsApi = dpsExtMovementsServer

  @Nested
  @DisplayName("MOVEMENT_APPLICATION-INSERTED")
  inner class TemporaryAbsenceApplicationCreated {
    private var dpsId: UUID = UUID.randomUUID()

    @Nested
    inner class HappyPath {
      @BeforeEach
      fun setUp() {
        mappingApi.stubGetTemporaryAbsenceApplicationMapping(111, NOT_FOUND)
        mappingApi.stubCreateTemporaryAbsenceApplicationMapping()
        nomisApi.stubGetTemporaryAbsenceApplication(applicationId = 111)
        dpsApi.stubSyncTapApplication(response = SyncResponse(dpsId))

        sendMessage(externalMovementApplicationEvent("MOVEMENT_APPLICATION-INSERTED"))
          .also { waitForAnyProcessingToComplete() }
      }

      @Test
      fun `should check mapping`() {
        mappingApi.verify(getRequestedFor(urlPathEqualTo("/mapping/temporary-absence/application/nomis-application-id/111")))
      }

      @Test
      fun `should get NOMIS application`() {
        nomisApi.verify(getRequestedFor(urlPathEqualTo("/movements/A1234BC/temporary-absences/application/111")))
      }

      @Test
      fun `should create DPS application`() {
        dpsApi.verify(
          putRequestedFor(urlPathEqualTo("/sync/temporary-absence-application/A1234BC"))
            .withRequestBodyJsonPath("id", absent())
            .withRequestBodyJsonPath("movementApplicationId", equalTo = 111)
            .withRequestBodyJsonPath("eventSubType", equalTo = "C5"),
        )
      }

      @Test
      fun `should create mapping`() {
        mappingApi.verify(
          postRequestedFor(urlPathEqualTo("/mapping/temporary-absence/application"))
            .withRequestBodyJsonPath("prisonerNumber", "A1234BC")
            .withRequestBodyJsonPath("bookingId", 12345)
            .withRequestBodyJsonPath("nomisMovementApplicationId", 111)
            .withRequestBodyJsonPath("dpsMovementApplicationId", dpsId)
            .withRequestBodyJsonPath("mappingType", "NOMIS_CREATED"),
        )
      }

      @Test
      fun `should create success telemetry`() {
        verify(telemetryClient).trackEvent(
          eq("temporary-absence-sync-application-inserted-success"),
          check {
            assertThat(it["offenderNo"]).isEqualTo("A1234BC")
            assertThat(it["bookingId"]).isEqualTo("12345")
            assertThat(it["nomisApplicationId"]).isEqualTo("111")
            assertThat(it["dpsApplicationId"]).isEqualTo(dpsId.toString())
          },
          isNull(),
        )
      }
    }

    @Nested
    inner class WhenCreatedInDps {
      @BeforeEach
      fun setUp() {
        mappingApi.stubGetTemporaryAbsenceApplicationMapping(111)

        sendMessage(externalMovementApplicationEvent("MOVEMENT_APPLICATION-INSERTED", "DPS_SYNCHRONISATION"))
          .also { waitForAnyProcessingToComplete() }
      }

      @Test
      fun `should NOT create DPS application`() {
        dpsApi.verify(0, putRequestedFor(urlPathEqualTo("/sync/temporary-absence-application/A1234BC")))
      }

      @Test
      fun `should NOT create mapping`() {
        mappingApi.verify(
          count = 0,
          postRequestedFor(urlPathEqualTo("/mapping/temporary-absence/application")),
        )
      }

      @Test
      fun `should create telemetry`() {
        verify(telemetryClient).trackEvent(
          eq("temporary-absence-sync-application-inserted-skipped"),
          check {
            assertThat(it["offenderNo"]).isEqualTo("A1234BC")
            assertThat(it["bookingId"]).isEqualTo("12345")
            assertThat(it["nomisApplicationId"]).isEqualTo("111")
          },
          isNull(),
        )
      }
    }

    @Nested
    inner class WhenAlreadyCreated {
      @BeforeEach
      fun setUp() {
        mappingApi.stubGetTemporaryAbsenceApplicationMapping(111)

        sendMessage(externalMovementApplicationEvent("MOVEMENT_APPLICATION-INSERTED"))
          .also { waitForAnyProcessingToComplete() }
      }

      @Test
      fun `should NOT create DPS application`() {
        dpsApi.verify(0, putRequestedFor(urlPathEqualTo("/sync/temporary-absence-application/A1234BC")))
      }

      @Test
      fun `should NOT create mapping`() {
        mappingApi.verify(
          count = 0,
          postRequestedFor(urlPathEqualTo("/mapping/temporary-absence/application")),
        )
      }

      @Test
      fun `should create telemetry`() {
        verify(telemetryClient).trackEvent(
          eq("temporary-absence-sync-application-inserted-ignored"),
          check {
            assertThat(it["offenderNo"]).isEqualTo("A1234BC")
            assertThat(it["bookingId"]).isEqualTo("12345")
            assertThat(it["nomisApplicationId"]).isEqualTo("111")
          },
          isNull(),
        )
      }
    }

    @Nested
    inner class WhenCreateFailsInDps {
      @BeforeEach
      fun setUp() {
        mappingApi.stubGetTemporaryAbsenceApplicationMapping(111, NOT_FOUND)
        mappingApi.stubCreateTemporaryAbsenceApplicationMapping()
        nomisApi.stubGetTemporaryAbsenceApplication(applicationId = 111)
        dpsApi.stubSyncTapApplicationError()

        sendMessage(externalMovementApplicationEvent("MOVEMENT_APPLICATION-INSERTED"))
          .also { waitForAnyProcessingToComplete("temporary-absence-sync-application-inserted-error") }
      }

      @Test
      fun `should attempt to create DPS application`() {
        dpsApi.verify(putRequestedFor(urlPathEqualTo("/sync/temporary-absence-application/A1234BC")))
      }

      @Test
      fun `should NOT create mapping`() {
        mappingApi.verify(
          count = 0,
          postRequestedFor(urlPathEqualTo("/mapping/temporary-absence/application")),
        )
      }

      @Test
      fun `should create error telemetry`() {
        verify(telemetryClient).trackEvent(
          eq("temporary-absence-sync-application-inserted-error"),
          check {
            assertThat(it["offenderNo"]).isEqualTo("A1234BC")
            assertThat(it["bookingId"]).isEqualTo("12345")
            assertThat(it["nomisApplicationId"]).isEqualTo("111")
          },
          isNull(),
        )
      }
    }

    @Nested
    inner class WhenDuplicateMapping {
      @BeforeEach
      fun setUp() {
        mappingApi.stubGetTemporaryAbsenceApplicationMapping(111, NOT_FOUND)
        nomisApi.stubGetTemporaryAbsenceApplication(applicationId = 111)
        dpsApi.stubSyncTapApplication(response = SyncResponse(dpsId))
        mappingApi.stubCreateTemporaryAbsenceApplicationMappingConflict(
          error = DuplicateMappingErrorResponse(
            moreInfo = DuplicateErrorContentObject(
              existing = TemporaryAbsenceApplicationSyncMappingDto(
                prisonerNumber = "A1234BC",
                bookingId = 12345L,
                nomisMovementApplicationId = 222L,
                dpsMovementApplicationId = UUID.randomUUID(),
                mappingType = TemporaryAbsenceApplicationSyncMappingDto.MappingType.NOMIS_CREATED,
              ),
              duplicate = TemporaryAbsenceApplicationSyncMappingDto(
                prisonerNumber = "A1234BC",
                bookingId = 12345L,
                nomisMovementApplicationId = 111L,
                dpsMovementApplicationId = dpsId,
                mappingType = TemporaryAbsenceApplicationSyncMappingDto.MappingType.NOMIS_CREATED,
              ),
            ),
            errorCode = 1409,
            status = DuplicateMappingErrorResponse.Status._409_CONFLICT,
            userMessage = "Duplicate mapping",
          ),
        )

        sendMessage(externalMovementApplicationEvent("MOVEMENT_APPLICATION-INSERTED"))
          .also { waitForAnyProcessingToComplete("temporary-absence-sync-application-inserted-duplicate") }
      }

      @Test
      fun `should create DPS application only once`() {
        dpsApi.verify(putRequestedFor(urlPathEqualTo("/sync/temporary-absence-application/A1234BC")))
      }

      @Test
      fun `should create mapping only once`() {
        mappingApi.verify(
          postRequestedFor(urlPathEqualTo("/mapping/temporary-absence/application"))
            .withRequestBodyJsonPath("prisonerNumber", "A1234BC")
            .withRequestBodyJsonPath("bookingId", 12345)
            .withRequestBodyJsonPath("nomisMovementApplicationId", 111)
            .withRequestBodyJsonPath("dpsMovementApplicationId", dpsId)
            .withRequestBodyJsonPath("mappingType", "NOMIS_CREATED"),
        )
      }

      @Test
      fun `should create success telemetry and duplicate telemetry`() {
        verify(telemetryClient).trackEvent(
          eq("temporary-absence-sync-application-inserted-success"),
          check {
            assertThat(it["offenderNo"]).isEqualTo("A1234BC")
            assertThat(it["bookingId"]).isEqualTo("12345")
            assertThat(it["nomisApplicationId"]).isEqualTo("111")
            assertThat(it["dpsApplicationId"]).isEqualTo(dpsId.toString())
          },
          isNull(),
        )
        verify(telemetryClient).trackEvent(
          eq("temporary-absence-sync-application-inserted-duplicate"),
          check {
            assertThat(it["duplicateOffenderNo"]).isEqualTo("A1234BC")
            assertThat(it["existingOffenderNo"]).isEqualTo("A1234BC")
            assertThat(it["duplicateBookingId"]).isEqualTo("12345")
            assertThat(it["existingBookingId"]).isEqualTo("12345")
            assertThat(it["duplicateNomisApplicationId"]).isEqualTo("111")
            assertThat(it["existingNomisApplicationId"]).isEqualTo("222")
            assertThat(it["duplicateDpsApplicationId"]).isNotNull
            assertThat(it["existingDpsApplicationId"]).isNotNull
          },
          isNull(),
        )
      }
    }

    @Nested
    inner class WhenMappingCreateFailsOnce {
      @BeforeEach
      fun setUp() {
        mappingApi.stubGetTemporaryAbsenceApplicationMapping(111, NOT_FOUND)
        nomisApi.stubGetTemporaryAbsenceApplication(applicationId = 111)
        dpsApi.stubSyncTapApplication(response = SyncResponse(dpsId))
        mappingApi.stubCreateTemporaryAbsenceApplicationMappingFailureFollowedBySuccess()

        sendMessage(externalMovementApplicationEvent("MOVEMENT_APPLICATION-INSERTED"))
          .also { waitForAnyProcessingToComplete("temporary-absence-sync-application-mapping-created") }
      }

      @Test
      fun `should create DPS application`() {
        dpsApi.verify(putRequestedFor(urlPathEqualTo("/sync/temporary-absence-application/A1234BC")))
      }

      @Test
      fun `should create mapping on 2nd call`() {
        mappingApi.verify(
          count = 2,
          postRequestedFor(urlPathEqualTo("/mapping/temporary-absence/application"))
            .withRequestBodyJsonPath("prisonerNumber", "A1234BC")
            .withRequestBodyJsonPath("bookingId", 12345)
            .withRequestBodyJsonPath("nomisMovementApplicationId", 111)
            .withRequestBodyJsonPath("dpsMovementApplicationId", dpsId)
            .withRequestBodyJsonPath("mappingType", "NOMIS_CREATED"),
        )
      }

      @Test
      fun `should publish success telemetry`() {
        verify(telemetryClient).trackEvent(
          eq("temporary-absence-sync-application-inserted-success"),
          check {
            assertThat(it["offenderNo"]).isEqualTo("A1234BC")
            assertThat(it["bookingId"]).isEqualTo("12345")
            assertThat(it["nomisApplicationId"]).isEqualTo("111")
            assertThat(it["dpsApplicationId"]).isEqualTo(dpsId.toString())
          },
          isNull(),
        )
      }

      @Test
      fun `should publish mapping created telemetry`() {
        verify(telemetryClient).trackEvent(
          eq("temporary-absence-sync-application-mapping-created"),
          check {
            assertThat(it["offenderNo"]).isEqualTo("A1234BC")
            assertThat(it["bookingId"]).isEqualTo("12345")
            assertThat(it["nomisApplicationId"]).isEqualTo("111")
            assertThat(it["dpsApplicationId"]).isEqualTo(dpsId.toString())
          },
          isNull(),
        )
      }
    }
  }

  @Nested
  @DisplayName("MOVEMENT_APPLICATION-UPDATED")
  inner class TemporaryAbsenceApplicationUpdated {
    private var dpsId: UUID = UUID.randomUUID()

    @Nested
    inner class HappyPath {
      @BeforeEach
      fun setUp() {
        mappingApi.stubGetTemporaryAbsenceApplicationMapping(111, dpsId)
        nomisApi.stubGetTemporaryAbsenceApplication(applicationId = 111)
        dpsApi.stubSyncTapApplication(response = SyncResponse(dpsId))

        sendMessage(externalMovementApplicationEvent("MOVEMENT_APPLICATION-UPDATED"))
          .also { waitForAnyProcessingToComplete() }
      }

      @Test
      fun `should get mapping`() {
        mappingApi.verify(getRequestedFor(urlPathEqualTo("/mapping/temporary-absence/application/nomis-application-id/111")))
      }

      @Test
      fun `should get NOMIS application`() {
        nomisApi.verify(getRequestedFor(urlPathEqualTo("/movements/A1234BC/temporary-absences/application/111")))
      }

      @Test
      fun `should update DPS application`() {
        dpsApi.verify(
          putRequestedFor(urlPathEqualTo("/sync/temporary-absence-application/A1234BC"))
            .withRequestBodyJsonPath("id", equalTo = dpsId)
            .withRequestBodyJsonPath("movementApplicationId", equalTo = 111)
            .withRequestBodyJsonPath("eventSubType", equalTo = "C5"),
        )
      }

      @Test
      fun `should create success telemetry`() {
        verify(telemetryClient).trackEvent(
          eq("temporary-absence-sync-application-updated-success"),
          check {
            assertThat(it["offenderNo"]).isEqualTo("A1234BC")
            assertThat(it["bookingId"]).isEqualTo("12345")
            assertThat(it["nomisApplicationId"]).isEqualTo("111")
            assertThat(it["dpsApplicationId"]).isEqualTo(dpsId.toString())
          },
          isNull(),
        )
      }
    }

    @Nested
    inner class WhenUpdatedInDps {
      @BeforeEach
      fun setUp() {
        sendMessage(externalMovementApplicationEvent("MOVEMENT_APPLICATION-UPDATED", "DPS_SYNCHRONISATION"))
          .also { waitForAnyProcessingToComplete() }
      }

      @Test
      fun `should NOT create DPS application`() {
        dpsApi.verify(0, putRequestedFor(urlPathEqualTo("/sync/temporary-absence-application/A1234BC")))
      }

      @Test
      fun `should NOT get mapping`() {
        mappingApi.verify(
          count = 0,
          getRequestedFor(urlPathEqualTo("/mapping/temporary-absence/application/nomis-application-id/111")),
        )
      }

      @Test
      fun `should create telemetry`() {
        verify(telemetryClient).trackEvent(
          eq("temporary-absence-sync-application-updated-skipped"),
          check {
            assertThat(it["offenderNo"]).isEqualTo("A1234BC")
            assertThat(it["bookingId"]).isEqualTo("12345")
            assertThat(it["nomisApplicationId"]).isEqualTo("111")
          },
          isNull(),
        )
      }
    }

    @Nested
    inner class WhenDpsUpdateFails {
      @BeforeEach
      fun setUp() {
        mappingApi.stubGetTemporaryAbsenceApplicationMapping(111, dpsId)
        nomisApi.stubGetTemporaryAbsenceApplication(applicationId = 111)
        dpsApi.stubSyncTapApplicationError()

        sendMessage(externalMovementApplicationEvent("MOVEMENT_APPLICATION-UPDATED"))
          .also { waitForAnyProcessingToComplete("temporary-absence-sync-application-updated-error") }
      }

      @Test
      fun `should try to update DPS application`() {
        dpsApi.verify(putRequestedFor(urlPathEqualTo("/sync/temporary-absence-application/A1234BC")))
      }

      @Test
      fun `should create error telemetry`() {
        verify(telemetryClient).trackEvent(
          eq("temporary-absence-sync-application-updated-error"),
          check {
            assertThat(it["offenderNo"]).isEqualTo("A1234BC")
            assertThat(it["bookingId"]).isEqualTo("12345")
            assertThat(it["nomisApplicationId"]).isEqualTo("111")
            assertThat(it["dpsApplicationId"]).isEqualTo(dpsId.toString())
          },
          isNull(),
        )
      }
    }
  }

  @Nested
  @DisplayName("MOVEMENT_APPLICATION-DELETED")
  inner class TemporaryAbsenceApplicationDeleted {

    @Nested
    inner class HappyPath {
      @BeforeEach
      fun setUp() {
        mappingApi.stubGetTemporaryAbsenceApplicationMapping(111)
        nomisApi.stubGetTemporaryAbsenceApplication(applicationId = 111)
        mappingApi.stubDeleteTemporaryAbsenceApplicationMapping(nomisApplicationId = 111)
        // TODO stub DPS API

        sendMessage(externalMovementApplicationEvent("MOVEMENT_APPLICATION-DELETED"))
          .also { waitForAnyProcessingToComplete() }
      }

      @Test
      fun `should get mapping`() {
        mappingApi.verify(getRequestedFor(urlPathEqualTo("/mapping/temporary-absence/application/nomis-application-id/111")))
      }

      @Test
      fun `should delete mapping`() {
        mappingApi.verify(deleteRequestedFor(urlPathEqualTo("/mapping/temporary-absence/application/nomis-application-id/111")))
      }

      @Test
      @Disabled("Waiting for DPS API to become available")
      fun `should delete DPS application`() {
        // TODO verify DPS endpoint called
      }

      @Test
      fun `should create success telemetry`() {
        verify(telemetryClient).trackEvent(
          eq("temporary-absence-sync-application-deleted-success"),
          check {
            assertThat(it["offenderNo"]).isEqualTo("A1234BC")
            assertThat(it["bookingId"]).isEqualTo("12345")
            assertThat(it["nomisApplicationId"]).isEqualTo("111")
            // TODO assert DPS id is tracked
          },
          isNull(),
        )
      }
    }

    @Nested
    inner class WhenMappingDoesNotExist {
      @BeforeEach
      fun setUp() {
        mappingApi.stubGetTemporaryAbsenceApplicationMapping(111, NOT_FOUND)

        sendMessage(externalMovementApplicationEvent("MOVEMENT_APPLICATION-DELETED"))
          .also { waitForAnyProcessingToComplete() }
      }

      @Test
      @Disabled("Waiting for DPS API to become available")
      fun `should NOT delete DPS application`() {
        // TODO verify DPS endpoint not called
      }

      @Test
      fun `should get mapping`() {
        mappingApi.verify(
          getRequestedFor(urlPathEqualTo("/mapping/temporary-absence/application/nomis-application-id/111")),
        )
      }

      @Test
      fun `should create telemetry`() {
        verify(telemetryClient).trackEvent(
          eq("temporary-absence-sync-application-deleted-ignored"),
          check {
            assertThat(it["offenderNo"]).isEqualTo("A1234BC")
            assertThat(it["bookingId"]).isEqualTo("12345")
            assertThat(it["nomisApplicationId"]).isEqualTo("111")
            // TODO assert DPS id is tracked
          },
          isNull(),
        )
      }
    }

    @Nested
    @Disabled("Waiting for DPS API to become available")
    inner class WhenDpsDeleteFails {
      @BeforeEach
      fun setUp() {
        mappingApi.stubGetTemporaryAbsenceApplicationMapping(111)
        // TODO stub DPS API to reject delete

        sendMessage(externalMovementApplicationEvent("MOVEMENT_APPLICATION-DELETED"))
          .also { waitForAnyProcessingToComplete() }
      }

      @Test
      @Disabled("Waiting for DPS API to become available")
      fun `should try to delete DPS application`() {
        // TODO verify DPS endpoint called
      }

      @Test
      fun `should create error telemetry`() {
        verify(telemetryClient).trackEvent(
          eq("temporary-absence-sync-application-deleted-error"),
          check {
            assertThat(it["offenderNo"]).isEqualTo("A1234BC")
            assertThat(it["bookingId"]).isEqualTo("12345")
            assertThat(it["nomisApplicationId"]).isEqualTo("111")
            // TODO assert DPS id is tracked
          },
          isNull(),
        )
      }
    }
  }

  @Nested
  @DisplayName("MOVEMENT_APPLICATION_MULTI-INSERTED")
  inner class TemporaryAbsenceOutsideMovementCreated {

    @Nested
    inner class HappyPath {
      @BeforeEach
      fun setUp() {
        mappingApi.stubGetOutsideMovementMapping(41, NOT_FOUND)
        mappingApi.stubGetTemporaryAbsenceApplicationMapping(111)
        mappingApi.stubCreateOutsideMovementMapping()
        nomisApi.stubGetTemporaryAbsenceApplicationOutsideMovement(offenderNo = "A1234BC", appMultiId = 41)
        // TODO stub DPS API

        sendMessage(externalMovementApplicationMultiEvent("MOVEMENT_APPLICATION_MULTI-INSERTED"))
          .also { waitForAnyProcessingToComplete() }
      }

      @Test
      fun `should check mapping`() {
        mappingApi.verify(getRequestedFor(urlPathEqualTo("/mapping/temporary-absence/outside-movement/nomis-application-multi-id/41")))
      }

      @Test
      fun `should check parent mapping`() {
        mappingApi.verify(getRequestedFor(urlPathEqualTo("/mapping/temporary-absence/application/nomis-application-id/111")))
      }

      @Test
      fun `should get NOMIS outside movement`() {
        nomisApi.verify(getRequestedFor(urlPathEqualTo("/movements/A1234BC/temporary-absences/outside-movement/41")))
      }

      @Test
      @Disabled("Waiting for DPS API to become available")
      fun `should create DPS outside movement`() {
        // TODO verify DPS endpoint called
      }

      @Test
      fun `should create mapping`() {
        mappingApi.verify(
          postRequestedFor(urlPathEqualTo("/mapping/temporary-absence/outside-movement"))
            .withRequestBodyJsonPath("prisonerNumber", "A1234BC")
            .withRequestBodyJsonPath("bookingId", 12345)
            .withRequestBodyJsonPath("nomisMovementApplicationMultiId", 41)
            // TODO verify DPS id
            .withRequestBodyJsonPath("mappingType", "NOMIS_CREATED"),
        )
      }

      @Test
      fun `should create success telemetry`() {
        verify(telemetryClient).trackEvent(
          eq("temporary-absence-sync-outside-movement-inserted-success"),
          check {
            assertThat(it["offenderNo"]).isEqualTo("A1234BC")
            assertThat(it["bookingId"]).isEqualTo("12345")
            assertThat(it["nomisApplicationMultiId"]).isEqualTo("41")
            assertThat(it["nomisApplicationId"]).isEqualTo("111")
            // TODO assert DPS id is tracked
          },
          isNull(),
        )
      }
    }

    @Nested
    inner class WhenCreatedInDps {
      @BeforeEach
      fun setUp() {
        mappingApi.stubGetOutsideMovementMapping(41)
        mappingApi.stubGetTemporaryAbsenceApplicationMapping(111)

        sendMessage(externalMovementApplicationMultiEvent("MOVEMENT_APPLICATION_MULTI-INSERTED", "DPS_SYNCHRONISATION"))
          .also { waitForAnyProcessingToComplete() }
      }

      @Test
      @Disabled("Waiting for DPS API to become available")
      fun `should NOT create DPS outside movement`() {
        // TODO verify DPS endpoint not called
      }

      @Test
      fun `should NOT create mapping`() {
        mappingApi.verify(
          count = 0,
          postRequestedFor(urlPathEqualTo("/mapping/temporary-absence/outside-movement")),
        )
      }

      @Test
      fun `should create telemetry`() {
        verify(telemetryClient).trackEvent(
          eq("temporary-absence-sync-outside-movement-inserted-skipped"),
          check {
            assertThat(it["offenderNo"]).isEqualTo("A1234BC")
            assertThat(it["bookingId"]).isEqualTo("12345")
            assertThat(it["nomisApplicationMultiId"]).isEqualTo("41")
            assertThat(it["nomisApplicationId"]).isEqualTo("111")
          },
          isNull(),
        )
      }
    }

    @Nested
    inner class WhenAlreadyCreated {
      @BeforeEach
      fun setUp() {
        mappingApi.stubGetOutsideMovementMapping(41)

        sendMessage(externalMovementApplicationMultiEvent("MOVEMENT_APPLICATION_MULTI-INSERTED"))
          .also { waitForAnyProcessingToComplete() }
      }

      @Test
      @Disabled("Waiting for DPS API to become available")
      fun `should NOT create DPS outside movement`() {
        // TODO verify DPS endpoint not called
      }

      @Test
      fun `should NOT create mapping`() {
        mappingApi.verify(
          count = 0,
          postRequestedFor(urlPathEqualTo("/mapping/temporary-absence/outside-movement")),
        )
      }

      @Test
      fun `should create telemetry`() {
        verify(telemetryClient).trackEvent(
          eq("temporary-absence-sync-outside-movement-inserted-ignored"),
          check {
            assertThat(it["offenderNo"]).isEqualTo("A1234BC")
            assertThat(it["bookingId"]).isEqualTo("12345")
            assertThat(it["nomisApplicationMultiId"]).isEqualTo("41")
            assertThat(it["nomisApplicationId"]).isEqualTo("111")
            // TODO assert DPS id is tracked
          },
          isNull(),
        )
      }
    }

    @Nested
    inner class WhenParentApplicationNotCreatedYet {
      @BeforeEach
      fun setUp() {
        mappingApi.stubGetOutsideMovementMapping(41, NOT_FOUND)
        mappingApi.stubGetTemporaryAbsenceApplicationMapping(111, NOT_FOUND)

        sendMessage(externalMovementApplicationMultiEvent("MOVEMENT_APPLICATION_MULTI-INSERTED"))
          .also { waitForAnyProcessingToComplete("temporary-absence-sync-outside-movement-inserted-error") }
      }

      @Test
      fun `should check mapping`() {
        mappingApi.verify(getRequestedFor(urlPathEqualTo("/mapping/temporary-absence/outside-movement/nomis-application-multi-id/41")))
      }

      @Test
      fun `should check parent mapping`() {
        mappingApi.verify(getRequestedFor(urlPathEqualTo("/mapping/temporary-absence/application/nomis-application-id/111")))
      }

      @Test
      fun `should create error telemetry`() {
        verify(telemetryClient).trackEvent(
          eq("temporary-absence-sync-outside-movement-inserted-error"),
          check {
            assertThat(it["offenderNo"]).isEqualTo("A1234BC")
            assertThat(it["bookingId"]).isEqualTo("12345")
            assertThat(it["nomisApplicationMultiId"]).isEqualTo("41")
            assertThat(it["nomisApplicationId"]).isEqualTo("111")
            assertThat(it["error"]).isEqualTo("Application 111 not created yet so children cannot be processed")
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
    @Disabled("Waiting for DPS API to become available")
    inner class WhenCreateFailsInDps {
      @BeforeEach
      fun setUp() {
        mappingApi.stubGetOutsideMovementMapping(41, NOT_FOUND)
        mappingApi.stubGetTemporaryAbsenceApplicationMapping(111)
        mappingApi.stubCreateOutsideMovementMapping()
        nomisApi.stubGetTemporaryAbsenceApplicationOutsideMovement(appMultiId = 41)
        // TODO stub DPS to reject create

        sendMessage(externalMovementApplicationMultiEvent("MOVEMENT_APPLICATION_MULTI-INSERTED"))
          .also { waitForAnyProcessingToComplete("temporary-absence-sync-outside-movement-inserted-error") }
      }

      @Test
      fun `should create DPS outside movement`() {
        // TODO verify DPS endpoint not called
      }

      @Test
      fun `should NOT create mapping`() {
        mappingApi.verify(
          count = 0,
          postRequestedFor(urlPathEqualTo("/mapping/temporary-absence/outside-movement"))
            .withRequestBodyJsonPath("prisonerNumber", "A1234BC")
            .withRequestBodyJsonPath("bookingId", 12345)
            .withRequestBodyJsonPath("nomisMovementApplicationMultiId", 41)
            // TODO verify DPS id
            .withRequestBodyJsonPath("mappingType", "NOMIS_CREATED"),
        )
      }

      @Test
      fun `should create error telemetry`() {
        verify(telemetryClient).trackEvent(
          eq("temporary-absence-sync-outside-movement-inserted-error"),
          check {
            assertThat(it["offenderNo"]).isEqualTo("A1234BC")
            assertThat(it["bookingId"]).isEqualTo("12345")
            assertThat(it["nomisApplicationMultiId"]).isEqualTo("41")
            assertThat(it["nomisApplicationId"]).isEqualTo("111")
          },
          isNull(),
        )
      }
    }

    @Nested
    inner class WhenDuplicateMapping {
      @BeforeEach
      fun setUp() {
        mappingApi.stubGetOutsideMovementMapping(41, NOT_FOUND)
        mappingApi.stubGetTemporaryAbsenceApplicationMapping(111)
        nomisApi.stubGetTemporaryAbsenceApplicationOutsideMovement(offenderNo = "A1234BC", appMultiId = 41)
        // TODO stub DPS API
        mappingApi.stubCreateOutsideMovementMappingConflict(
          error = DuplicateMappingErrorResponse(
            moreInfo = DuplicateErrorContentObject(
              existing = TemporaryAbsenceOutsideMovementSyncMappingDto(
                prisonerNumber = "A1234BC",
                bookingId = 12345L,
                nomisMovementApplicationMultiId = 222L,
                dpsOutsideMovementId = UUID.randomUUID(),
                mappingType = TemporaryAbsenceOutsideMovementSyncMappingDto.MappingType.NOMIS_CREATED,
              ),
              duplicate = TemporaryAbsenceOutsideMovementSyncMappingDto(
                prisonerNumber = "A1234BC",
                bookingId = 12345L,
                nomisMovementApplicationMultiId = 41L,
                dpsOutsideMovementId = UUID.randomUUID(),
                mappingType = TemporaryAbsenceOutsideMovementSyncMappingDto.MappingType.NOMIS_CREATED,
              ),
            ),
            errorCode = 1409,
            status = DuplicateMappingErrorResponse.Status._409_CONFLICT,
            userMessage = "Duplicate mapping",
          ),
        )

        sendMessage(externalMovementApplicationMultiEvent("MOVEMENT_APPLICATION_MULTI-INSERTED"))
          .also { waitForAnyProcessingToComplete("temporary-absence-sync-outside-movement-inserted-duplicate") }
      }

      @Test
      @Disabled("Waiting for DPS API to become available")
      fun `should create DPS outside movement only once`() {
        // TODO verify DPS endpoint called
      }

      @Test
      fun `should create mapping only once`() {
        mappingApi.verify(
          postRequestedFor(urlPathEqualTo("/mapping/temporary-absence/outside-movement"))
            .withRequestBodyJsonPath("prisonerNumber", "A1234BC")
            .withRequestBodyJsonPath("bookingId", 12345)
            .withRequestBodyJsonPath("nomisMovementApplicationMultiId", 41)
            // TODO verify DPS id
            .withRequestBodyJsonPath("mappingType", "NOMIS_CREATED"),
        )
      }

      @Test
      fun `should create success telemetry and duplicate telemetry`() {
        verify(telemetryClient).trackEvent(
          eq("temporary-absence-sync-outside-movement-inserted-success"),
          check {
            assertThat(it["offenderNo"]).isEqualTo("A1234BC")
            assertThat(it["bookingId"]).isEqualTo("12345")
            assertThat(it["nomisApplicationMultiId"]).isEqualTo("41")
            assertThat(it["nomisApplicationId"]).isEqualTo("111")
            // TODO assert DPS id is tracked
          },
          isNull(),
        )
        verify(telemetryClient).trackEvent(
          eq("temporary-absence-sync-outside-movement-inserted-duplicate"),
          check {
            assertThat(it["duplicateOffenderNo"]).isEqualTo("A1234BC")
            assertThat(it["existingOffenderNo"]).isEqualTo("A1234BC")
            assertThat(it["duplicateBookingId"]).isEqualTo("12345")
            assertThat(it["existingBookingId"]).isEqualTo("12345")
            assertThat(it["duplicateNomisApplicationMultiId"]).isEqualTo("41")
            assertThat(it["existingNomisApplicationMultiId"]).isEqualTo("222")
            // TODO verify DPS ids
            assertThat(it["duplicateDpsOutsideMovementId"]).isNotNull
            assertThat(it["existingDpsOutsideMovementId"]).isNotNull
          },
          isNull(),
        )
      }
    }

    @Nested
    inner class WhenMappingCreateFailsOnce {
      @BeforeEach
      fun setUp() {
        mappingApi.stubGetOutsideMovementMapping(41, NOT_FOUND)
        mappingApi.stubGetTemporaryAbsenceApplicationMapping(111)
        nomisApi.stubGetTemporaryAbsenceApplicationOutsideMovement(offenderNo = "A1234BC", appMultiId = 41)
        // TODO stub DPS API
        mappingApi.stubCreateOutsideMovementMappingFailureFollowedBySuccess()

        sendMessage(externalMovementApplicationMultiEvent("MOVEMENT_APPLICATION_MULTI-INSERTED"))
          .also { waitForAnyProcessingToComplete("temporary-absence-sync-outside-movement-mapping-created") }
      }

      @Test
      @Disabled("Waiting for DPS API to become available")
      fun `should create DPS application`() {
        // TODO verify DPS endpoint called
      }

      @Test
      fun `should create mapping on 2nd call`() {
        mappingApi.verify(
          count = 2,
          postRequestedFor(urlPathEqualTo("/mapping/temporary-absence/outside-movement"))
            .withRequestBodyJsonPath("prisonerNumber", "A1234BC")
            .withRequestBodyJsonPath("bookingId", 12345)
            .withRequestBodyJsonPath("nomisMovementApplicationMultiId", 41)
            // TODO verify DPS id
            .withRequestBodyJsonPath("mappingType", "NOMIS_CREATED"),
        )
      }

      @Test
      fun `should publish success telemetry`() {
        verify(telemetryClient).trackEvent(
          eq("temporary-absence-sync-outside-movement-inserted-success"),
          check {
            assertThat(it["offenderNo"]).isEqualTo("A1234BC")
            assertThat(it["bookingId"]).isEqualTo("12345")
            assertThat(it["nomisApplicationMultiId"]).isEqualTo("41")
            assertThat(it["nomisApplicationId"]).isEqualTo("111")
            // TODO assert DPS id is tracked
          },
          isNull(),
        )
      }

      @Test
      fun `should publish mapping created telemetry`() {
        verify(telemetryClient).trackEvent(
          eq("temporary-absence-sync-outside-movement-mapping-created"),
          check {
            assertThat(it["offenderNo"]).isEqualTo("A1234BC")
            assertThat(it["bookingId"]).isEqualTo("12345")
            assertThat(it["nomisApplicationMultiId"]).isEqualTo("41")
            assertThat(it["nomisApplicationId"]).isEqualTo("111")
            // TODO assert DPS id is tracked
          },
          isNull(),
        )
      }
    }
  }

  @Nested
  @DisplayName("MOVEMENT_APPLICATION_MULTI-UPDATED")
  inner class TemporaryAbsenceOutsideMovementUpdated {

    @Nested
    inner class HappyPath {
      @BeforeEach
      fun setUp() {
        mappingApi.stubGetOutsideMovementMapping(41)
        nomisApi.stubGetTemporaryAbsenceApplicationOutsideMovement(appMultiId = 41)
        // TODO stub DPS API

        sendMessage(externalMovementApplicationMultiEvent("MOVEMENT_APPLICATION_MULTI-UPDATED"))
          .also { waitForAnyProcessingToComplete() }
      }

      @Test
      fun `should get mapping`() {
        mappingApi.verify(getRequestedFor(urlPathEqualTo("/mapping/temporary-absence/outside-movement/nomis-application-multi-id/41")))
      }

      @Test
      fun `should get NOMIS outside movement`() {
        nomisApi.verify(getRequestedFor(urlPathEqualTo("/movements/A1234BC/temporary-absences/outside-movement/41")))
      }

      @Test
      @Disabled("Waiting for DPS API to become available")
      fun `should update DPS outside movement`() {
        // TODO verify DPS endpoint called
      }

      @Test
      fun `should create success telemetry`() {
        verify(telemetryClient).trackEvent(
          eq("temporary-absence-sync-outside-movement-updated-success"),
          check {
            assertThat(it["offenderNo"]).isEqualTo("A1234BC")
            assertThat(it["bookingId"]).isEqualTo("12345")
            assertThat(it["nomisApplicationMultiId"]).isEqualTo("41")
            assertThat(it["nomisApplicationId"]).isEqualTo("111")
            // TODO assert DPS id is tracked
          },
          isNull(),
        )
      }
    }

    @Nested
    inner class WhenUpdatedInDps {
      @BeforeEach
      fun setUp() {
        sendMessage(externalMovementApplicationMultiEvent("MOVEMENT_APPLICATION_MULTI-UPDATED", "DPS_SYNCHRONISATION"))
          .also { waitForAnyProcessingToComplete() }
      }

      @Test
      @Disabled("Waiting for DPS API to become available")
      fun `should NOT create DPS outside movement`() {
        // TODO verify DPS endpoint not called
      }

      @Test
      fun `should NOT get mapping`() {
        mappingApi.verify(
          count = 0,
          getRequestedFor(urlPathEqualTo("/mapping/temporary-absence/outside-movement/nomis-application-multi-id/41")),
        )
      }

      @Test
      fun `should create telemetry`() {
        verify(telemetryClient).trackEvent(
          eq("temporary-absence-sync-outside-movement-updated-skipped"),
          check {
            assertThat(it["offenderNo"]).isEqualTo("A1234BC")
            assertThat(it["bookingId"]).isEqualTo("12345")
            assertThat(it["nomisApplicationMultiId"]).isEqualTo("41")
            assertThat(it["nomisApplicationId"]).isEqualTo("111")
            // TODO assert DPS id is tracked
          },
          isNull(),
        )
      }
    }

    @Nested
    @Disabled("Waiting for DPS API to become available")
    inner class WhenDpsUpdateFails {
      @BeforeEach
      fun setUp() {
        mappingApi.stubGetOutsideMovementMapping(41)
        nomisApi.stubGetTemporaryAbsenceApplicationOutsideMovement(appMultiId = 41)
        // TODO stub DPS API to reject update

        sendMessage(externalMovementApplicationMultiEvent("MOVEMENT_APPLICATION_MULTI-UPDATED"))
          .also { waitForAnyProcessingToComplete() }
      }

      @Test
      @Disabled("Waiting for DPS API to become available")
      fun `should try to update DPS outside movement`() {
        // TODO verify DPS endpoint called
      }

      @Test
      fun `should create error telemetry`() {
        verify(telemetryClient).trackEvent(
          eq("temporary-absence-sync-outside-movement-updated-error"),
          check {
            assertThat(it["offenderNo"]).isEqualTo("A1234BC")
            assertThat(it["bookingId"]).isEqualTo("12345")
            assertThat(it["nomisApplicationMultiId"]).isEqualTo("41")
            assertThat(it["nomisApplicationId"]).isEqualTo("111")
            // TODO assert DPS id is tracked
          },
          isNull(),
        )
      }
    }
  }

  @Nested
  @DisplayName("MOVEMENT_APPLICATION_MULTI-DELETED")
  inner class TemporaryAbsenceOutsideMovementDeleted {

    @Nested
    inner class HappyPath {
      @BeforeEach
      fun setUp() {
        mappingApi.stubGetOutsideMovementMapping(41)
        mappingApi.stubDeleteOutsideMovementMapping(nomisApplicationMultiId = 41)
        // TODO stub DPS API

        sendMessage(externalMovementApplicationMultiEvent("MOVEMENT_APPLICATION_MULTI-DELETED"))
          .also { waitForAnyProcessingToComplete() }
      }

      @Test
      fun `should get mapping`() {
        mappingApi.verify(getRequestedFor(urlPathEqualTo("/mapping/temporary-absence/outside-movement/nomis-application-multi-id/41")))
      }

      @Test
      fun `should delete mapping`() {
        mappingApi.verify(deleteRequestedFor(urlPathEqualTo("/mapping/temporary-absence/outside-movement/nomis-application-multi-id/41")))
      }

      @Test
      @Disabled("Waiting for DPS API to become available")
      fun `should delete DPS outside movement`() {
        // TODO verify DPS endpoint called
      }

      @Test
      fun `should create success telemetry`() {
        verify(telemetryClient).trackEvent(
          eq("temporary-absence-sync-outside-movement-deleted-success"),
          check {
            assertThat(it["offenderNo"]).isEqualTo("A1234BC")
            assertThat(it["bookingId"]).isEqualTo("12345")
            assertThat(it["nomisApplicationMultiId"]).isEqualTo("41")
            assertThat(it["nomisApplicationId"]).isEqualTo("111")
            // TODO assert DPS id is tracked
          },
          isNull(),
        )
      }
    }

    @Nested
    inner class WhenMappingDoesNotExist {
      @BeforeEach
      fun setUp() {
        mappingApi.stubGetOutsideMovementMapping(41, NOT_FOUND)

        sendMessage(externalMovementApplicationMultiEvent("MOVEMENT_APPLICATION_MULTI-DELETED"))
          .also { waitForAnyProcessingToComplete() }
      }

      @Test
      @Disabled("Waiting for DPS API to become available")
      fun `should NOT delete DPS outside movement`() {
        // TODO verify DPS endpoint not called
      }

      @Test
      fun `should get mapping`() {
        mappingApi.verify(
          getRequestedFor(urlPathEqualTo("/mapping/temporary-absence/outside-movement/nomis-application-multi-id/41")),
        )
      }

      @Test
      fun `should create telemetry`() {
        verify(telemetryClient).trackEvent(
          eq("temporary-absence-sync-outside-movement-deleted-ignored"),
          check {
            assertThat(it["offenderNo"]).isEqualTo("A1234BC")
            assertThat(it["bookingId"]).isEqualTo("12345")
            assertThat(it["nomisApplicationMultiId"]).isEqualTo("41")
            assertThat(it["nomisApplicationId"]).isEqualTo("111")
            // TODO assert DPS id is tracked
          },
          isNull(),
        )
      }
    }

    @Nested
    @Disabled("Waiting for DPS API to become available")
    inner class WhenDpsDeleteFails {
      @BeforeEach
      fun setUp() {
        mappingApi.stubGetOutsideMovementMapping(41)
        // TODO stub DPS API to reject delete

        sendMessage(externalMovementApplicationMultiEvent("MOVEMENT_APPLICATION_MULTI-DELETED"))
          .also { waitForAnyProcessingToComplete() }
      }

      @Test
      @Disabled("Waiting for DPS API to become available")
      fun `should try to delete DPS outside movement`() {
        // TODO verify DPS endpoint called
      }

      @Test
      fun `should create error telemetry`() {
        verify(telemetryClient).trackEvent(
          eq("temporary-absence-sync-outside-movement-deleted-error"),
          check {
            assertThat(it["offenderNo"]).isEqualTo("A1234BC")
            assertThat(it["bookingId"]).isEqualTo("12345")
            assertThat(it["nomisApplicationMultiId"]).isEqualTo("41")
            assertThat(it["nomisApplicationId"]).isEqualTo("111")
            // TODO assert DPS id is tracked
          },
          isNull(),
        )
      }
    }
  }

  @Nested
  @DisplayName("SCHEDULED_EXT_MOVE-INSERTED")
  inner class TemporaryAbsenceScheduledMovementCreated {
    private val dpsApplicationId: UUID = UUID.randomUUID()
    private val dpsOccurrenceId: UUID = UUID.randomUUID()

    @Nested
    inner class HappyPathOutbound {
      @BeforeEach
      fun setUp() {
        mappingApi.stubGetScheduledMovementMapping(45678, NOT_FOUND)
        mappingApi.stubGetTemporaryAbsenceApplicationMapping(111, dpsApplicationId = dpsApplicationId)
        nomisApi.stubGetTemporaryAbsenceScheduledMovement(eventId = 45678)
        dpsApi.stubSyncScheduledTemporaryAbsence(parentId = dpsApplicationId, response = SyncResponse(dpsOccurrenceId))
        mappingApi.stubCreateScheduledMovementMapping()

        sendMessage(scheduledMovementEvent("SCHEDULED_EXT_MOVE-INSERTED"))
          .also { waitForAnyProcessingToComplete() }
      }

      @Test
      fun `should check mapping`() {
        mappingApi.verify(getRequestedFor(urlPathEqualTo("/mapping/temporary-absence/scheduled-movement/nomis-event-id/45678")))
      }

      @Test
      fun `should check parent mapping`() {
        mappingApi.verify(getRequestedFor(urlPathEqualTo("/mapping/temporary-absence/application/nomis-application-id/111")))
      }

      @Test
      fun `should get NOMIS scheduled movement`() {
        nomisApi.verify(getRequestedFor(urlPathEqualTo("/movements/A1234BC/temporary-absences/scheduled-temporary-absence/45678")))
      }

      @Test
      fun `should create DPS scheduled movement`() {
        dpsApi.verify(
          putRequestedFor(urlPathEqualTo("/sync/scheduled-temporary-absence/$dpsApplicationId"))
            .withRequestBodyJsonPath("id", absent())
            .withRequestBodyJsonPath("location.address", "to full address"),
        )
      }

      @Test
      fun `should create mapping`() {
        mappingApi.verify(
          postRequestedFor(urlPathEqualTo("/mapping/temporary-absence/scheduled-movement"))
            .withRequestBodyJsonPath("prisonerNumber", "A1234BC")
            .withRequestBodyJsonPath("bookingId", 12345)
            .withRequestBodyJsonPath("nomisEventId", 45678)
            .withRequestBodyJsonPath("dpsOccurrenceId", dpsOccurrenceId)
            .withRequestBodyJsonPath("mappingType", "NOMIS_CREATED")
            .withRequestBodyJsonPath("nomisAddressId", 321)
            .withRequestBodyJsonPath("nomisAddressOwnerClass", "OFF")
            .withRequestBodyJsonPath("dpsAddressText", "to full address")
            .withRequestBodyJsonPath("eventTime", containing("${LocalDate.now()}")),
        )
      }

      @Test
      fun `should create success telemetry`() {
        verify(telemetryClient).trackEvent(
          eq("temporary-absence-sync-scheduled-movement-inserted-success"),
          check {
            assertThat(it["offenderNo"]).isEqualTo("A1234BC")
            assertThat(it["bookingId"]).isEqualTo("12345")
            assertThat(it["nomisEventId"]).isEqualTo("45678")
            assertThat(it["nomisApplicationId"]).isEqualTo("111")
            assertThat(it["directionCode"]).isEqualTo("OUT")
            assertThat(it["dpsOccurrenceId"]).isEqualTo("$dpsOccurrenceId")
            assertThat(it["nomisAddressId"]).isEqualTo("321")
            assertThat(it["nomisAddressOwnerClass"]).isEqualTo("OFF")
          },
          isNull(),
        )
      }
    }

    @Nested
    inner class WhenNotTapMovement {
      @BeforeEach
      fun setUp(output: CapturedOutput) {
        sendMessage(scheduledMovementEvent("SCHEDULED_EXT_MOVE-INSERTED", nomisEventType = "TRN"))
          .also { await untilCallTo { output.out } matches { it!!.contains("Ignoring") } }
      }

      @Test
      fun `should NOT create DPS scheduled movement`() {
        dpsApi.verify(
          0,
          putRequestedFor(urlPathEqualTo("/sync/scheduled-temporary-absence/$dpsApplicationId")),
        )
      }

      @Test
      fun `should NOT create mapping`() {
        mappingApi.verify(
          count = 0,
          postRequestedFor(urlPathEqualTo("/mapping/temporary-absence/scheduled-movement")),
        )
      }
    }

    @Nested
    inner class WhenCreatedInDps {
      @BeforeEach
      fun setUp() {
        mappingApi.stubGetScheduledMovementMapping(45678)

        sendMessage(scheduledMovementEvent("SCHEDULED_EXT_MOVE-INSERTED", "DPS_SYNCHRONISATION"))
          .also { waitForAnyProcessingToComplete() }
      }

      @Test
      fun `should NOT create DPS scheduled movement`() {
        dpsApi.verify(
          0,
          putRequestedFor(urlPathEqualTo("/sync/scheduled-temporary-absence/$dpsApplicationId")),
        )
      }

      @Test
      fun `should NOT create mapping`() {
        mappingApi.verify(
          count = 0,
          postRequestedFor(urlPathEqualTo("/mapping/temporary-absence/scheduled-movement")),
        )
      }

      @Test
      fun `should create telemetry`() {
        verify(telemetryClient).trackEvent(
          eq("temporary-absence-sync-scheduled-movement-inserted-skipped"),
          check {
            assertThat(it["offenderNo"]).isEqualTo("A1234BC")
            assertThat(it["bookingId"]).isEqualTo("12345")
            assertThat(it["nomisEventId"]).isEqualTo("45678")
          },
          isNull(),
        )
      }
    }

    @Nested
    inner class WhenAlreadyCreated {
      @BeforeEach
      fun setUp() {
        mappingApi.stubGetScheduledMovementMapping(45678, dpsOccurrenceId)

        sendMessage(scheduledMovementEvent("SCHEDULED_EXT_MOVE-INSERTED"))
          .also { waitForAnyProcessingToComplete() }
      }

      @Test
      fun `should NOT create DPS scheduled movement`() {
        dpsApi.verify(
          0,
          putRequestedFor(urlPathEqualTo("/sync/scheduled-temporary-absence/$dpsApplicationId")),
        )
      }

      @Test
      fun `should NOT create mapping`() {
        mappingApi.verify(
          count = 0,
          postRequestedFor(urlPathEqualTo("/mapping/temporary-absence/scheduled-movement")),
        )
      }

      @Test
      fun `should create telemetry`() {
        verify(telemetryClient).trackEvent(
          eq("temporary-absence-sync-scheduled-movement-inserted-ignored"),
          check {
            assertThat(it["offenderNo"]).isEqualTo("A1234BC")
            assertThat(it["bookingId"]).isEqualTo("12345")
            assertThat(it["nomisEventId"]).isEqualTo("45678")
          },
          isNull(),
        )
      }
    }

    @Nested
    inner class WhenParentApplicationNotCreatedYet {
      @BeforeEach
      fun setUp() {
        mappingApi.stubGetScheduledMovementMapping(45678, NOT_FOUND)
        nomisApi.stubGetTemporaryAbsenceScheduledMovement(eventId = 45678)
        mappingApi.stubGetTemporaryAbsenceApplicationMapping(111, NOT_FOUND)

        sendMessage(scheduledMovementEvent("SCHEDULED_EXT_MOVE-INSERTED"))
          .also { waitForAnyProcessingToComplete("temporary-absence-sync-scheduled-movement-inserted-error") }
      }

      @Test
      fun `should check mapping`() {
        mappingApi.verify(getRequestedFor(urlPathEqualTo("/mapping/temporary-absence/scheduled-movement/nomis-event-id/45678")))
      }

      @Test
      fun `should check parent mapping`() {
        mappingApi.verify(getRequestedFor(urlPathEqualTo("/mapping/temporary-absence/application/nomis-application-id/111")))
      }

      @Test
      fun `should create error telemetry`() {
        verify(telemetryClient).trackEvent(
          eq("temporary-absence-sync-scheduled-movement-inserted-error"),
          check {
            assertThat(it["offenderNo"]).isEqualTo("A1234BC")
            assertThat(it["bookingId"]).isEqualTo("12345")
            assertThat(it["nomisEventId"]).isEqualTo("45678")
            assertThat(it["nomisApplicationId"]).isEqualTo("111")
            assertThat(it["error"]).isEqualTo("Application 111 not created yet so children cannot be processed")
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
    inner class WhenDuplicateMapping {
      private val eventTime = LocalDateTime.now()

      @BeforeEach
      fun setUp() {
        mappingApi.stubGetScheduledMovementMapping(45678, NOT_FOUND)
        mappingApi.stubGetTemporaryAbsenceApplicationMapping(111, dpsApplicationId)
        nomisApi.stubGetTemporaryAbsenceScheduledMovement(eventId = 45678)
        dpsApi.stubSyncScheduledTemporaryAbsence(parentId = dpsApplicationId, response = SyncResponse(dpsOccurrenceId))
        mappingApi.stubCreateScheduledMovementMappingConflict(
          error = DuplicateMappingErrorResponse(
            moreInfo = DuplicateErrorContentObject(
              existing = ScheduledMovementSyncMappingDto(
                prisonerNumber = "A1234BC",
                bookingId = 12345L,
                nomisEventId = 222L,
                dpsOccurrenceId = dpsOccurrenceId,
                mappingType = ScheduledMovementSyncMappingDto.MappingType.NOMIS_CREATED,
                nomisAddressId = 321,
                nomisAddressOwnerClass = "OFF",
                dpsAddressText = "to full address",
                eventTime = "$eventTime",
              ),
              duplicate = ScheduledMovementSyncMappingDto(
                prisonerNumber = "A1234BC",
                bookingId = 12345L,
                nomisEventId = 45678L,
                dpsOccurrenceId = dpsOccurrenceId,
                mappingType = ScheduledMovementSyncMappingDto.MappingType.NOMIS_CREATED,
                nomisAddressId = 321,
                nomisAddressOwnerClass = "OFF",
                dpsAddressText = "to full address",
                eventTime = "$eventTime",
              ),
            ),
            errorCode = 1409,
            status = DuplicateMappingErrorResponse.Status._409_CONFLICT,
            userMessage = "Duplicate mapping",
          ),
        )

        sendMessage(scheduledMovementEvent("SCHEDULED_EXT_MOVE-INSERTED"))
          .also { waitForAnyProcessingToComplete("temporary-absence-sync-scheduled-movement-inserted-duplicate") }
      }

      @Test
      fun `should create DPS scheduled movement only once`() {
        dpsApi.verify(putRequestedFor(urlPathEqualTo("/sync/scheduled-temporary-absence/$dpsApplicationId")))
      }

      @Test
      fun `should create mapping only once`() {
        mappingApi.verify(
          postRequestedFor(urlPathEqualTo("/mapping/temporary-absence/scheduled-movement"))
            .withRequestBodyJsonPath("prisonerNumber", "A1234BC")
            .withRequestBodyJsonPath("bookingId", 12345)
            .withRequestBodyJsonPath("nomisEventId", 45678)
            .withRequestBodyJsonPath("dpsOccurrenceId", dpsOccurrenceId)
            .withRequestBodyJsonPath("mappingType", "NOMIS_CREATED")
            .withRequestBodyJsonPath("nomisAddressId", 321)
            .withRequestBodyJsonPath("nomisAddressOwnerClass", "OFF")
            .withRequestBodyJsonPath("dpsAddressText", "to full address"),
        )
      }

      @Test
      fun `should create success telemetry and duplicate telemetry`() {
        verify(telemetryClient).trackEvent(
          eq("temporary-absence-sync-scheduled-movement-inserted-success"),
          check {
            assertThat(it["offenderNo"]).isEqualTo("A1234BC")
            assertThat(it["bookingId"]).isEqualTo("12345")
            assertThat(it["nomisEventId"]).isEqualTo("45678")
            assertThat(it["nomisApplicationId"]).isEqualTo("111")
            assertThat(it["dpsOccurrenceId"]).isEqualTo("$dpsOccurrenceId")
          },
          isNull(),
        )
        verify(telemetryClient).trackEvent(
          eq("temporary-absence-sync-scheduled-movement-inserted-duplicate"),
          check {
            assertThat(it["duplicateOffenderNo"]).isEqualTo("A1234BC")
            assertThat(it["existingOffenderNo"]).isEqualTo("A1234BC")
            assertThat(it["duplicateBookingId"]).isEqualTo("12345")
            assertThat(it["existingBookingId"]).isEqualTo("12345")
            assertThat(it["duplicateNomisEventId"]).isEqualTo("45678")
            assertThat(it["existingNomisEventId"]).isEqualTo("222")
            assertThat(it["duplicateDpsOccurrenceId"]).isEqualTo("$dpsOccurrenceId")
            assertThat(it["existingDpsOccurrenceId"]).isEqualTo("$dpsOccurrenceId")
          },
          isNull(),
        )
      }
    }

    @Nested
    inner class WhenMappingCreateFailsOnce {
      @BeforeEach
      fun setUp() {
        mappingApi.stubGetScheduledMovementMapping(45678, NOT_FOUND)
        mappingApi.stubGetTemporaryAbsenceApplicationMapping(111, dpsApplicationId = dpsApplicationId)
        nomisApi.stubGetTemporaryAbsenceScheduledMovement(eventId = 45678)
        dpsApi.stubSyncScheduledTemporaryAbsence(parentId = dpsApplicationId, response = SyncResponse(dpsOccurrenceId))
        mappingApi.stubCreateScheduledMovementMappingFailureFollowedBySuccess()

        sendMessage(scheduledMovementEvent("SCHEDULED_EXT_MOVE-INSERTED"))
          .also { waitForAnyProcessingToComplete("temporary-absence-sync-scheduled-movement-mapping-created") }
      }

      @Test
      fun `should create DPS scheduled movement`() {
        dpsApi.verify(putRequestedFor(urlPathEqualTo("/sync/scheduled-temporary-absence/$dpsApplicationId")))
      }

      @Test
      fun `should create mapping on 2nd call`() {
        mappingApi.verify(
          count = 2,
          postRequestedFor(urlPathEqualTo("/mapping/temporary-absence/scheduled-movement"))
            .withRequestBodyJsonPath("prisonerNumber", "A1234BC")
            .withRequestBodyJsonPath("bookingId", 12345)
            .withRequestBodyJsonPath("nomisEventId", 45678)
            .withRequestBodyJsonPath("dpsOccurrenceId", dpsOccurrenceId)
            .withRequestBodyJsonPath("mappingType", "NOMIS_CREATED"),
        )
      }

      @Test
      fun `should publish success telemetry`() {
        verify(telemetryClient).trackEvent(
          eq("temporary-absence-sync-scheduled-movement-inserted-success"),
          check {
            assertThat(it["offenderNo"]).isEqualTo("A1234BC")
            assertThat(it["bookingId"]).isEqualTo("12345")
            assertThat(it["nomisApplicationId"]).isEqualTo("111")
            assertThat(it["nomisEventId"]).isEqualTo("45678")
            assertThat(it["dpsOccurrenceId"]).isEqualTo("$dpsOccurrenceId")
          },
          isNull(),
        )
      }

      @Test
      fun `should publish mapping created telemetry`() {
        verify(telemetryClient).trackEvent(
          eq("temporary-absence-sync-scheduled-movement-mapping-created"),
          check {
            assertThat(it["offenderNo"]).isEqualTo("A1234BC")
            assertThat(it["bookingId"]).isEqualTo("12345")
            assertThat(it["nomisEventId"]).isEqualTo("45678")
            assertThat(it["dpsOccurrenceId"]).isEqualTo("$dpsOccurrenceId")
          },
          isNull(),
        )
      }
    }
  }

  @Nested
  @DisplayName("SCHEDULED_EXT_MOVE-UPDATED")
  inner class TemporaryAbsenceScheduledMovementUpdated {
    private val dpsApplicationId: UUID = UUID.randomUUID()
    private val dpsOccurrenceId: UUID = UUID.randomUUID()
    private val eventTime = LocalDateTime.now()

    @Nested
    inner class HappyPathOutbound {
      @BeforeEach
      fun setUp() {
        mappingApi.stubGetScheduledMovementMapping(45678, dpsOccurrenceId, eventTime)
        mappingApi.stubGetTemporaryAbsenceApplicationMapping(111, dpsApplicationId)
        nomisApi.stubGetTemporaryAbsenceScheduledMovement(eventId = 45678, eventTime = eventTime)
        dpsApi.stubSyncScheduledTemporaryAbsence(parentId = dpsApplicationId, response = SyncResponse(dpsOccurrenceId))
        mappingApi.stubUpdateScheduledMovementMapping()

        sendMessage(scheduledMovementEvent("SCHEDULED_EXT_MOVE-UPDATED"))
          .also { waitForAnyProcessingToComplete() }
      }

      @Test
      fun `should get mapping`() {
        mappingApi.verify(getRequestedFor(urlPathEqualTo("/mapping/temporary-absence/scheduled-movement/nomis-event-id/45678")))
      }

      @Test
      fun `should get NOMIS scheduled movement`() {
        nomisApi.verify(getRequestedFor(urlPathEqualTo("/movements/A1234BC/temporary-absences/scheduled-temporary-absence/45678")))
      }

      @Test
      fun `should update DPS scheduled movement`() {
        dpsApi.verify(
          putRequestedFor(urlPathEqualTo("/sync/scheduled-temporary-absence/$dpsApplicationId"))
            .withRequestBodyJsonPath("id", "$dpsOccurrenceId")
            .withRequestBodyJsonPath("location.address", "to full address"),
        )
      }

      @Test
      fun `should create success telemetry`() {
        verify(telemetryClient).trackEvent(
          eq("temporary-absence-sync-scheduled-movement-updated-success"),
          check {
            assertThat(it["offenderNo"]).isEqualTo("A1234BC")
            assertThat(it["bookingId"]).isEqualTo("12345")
            assertThat(it["nomisApplicationId"]).isEqualTo("111")
            assertThat(it["nomisEventId"]).isEqualTo("45678")
            assertThat(it["directionCode"]).isEqualTo("OUT")
            assertThat(it["dpsOccurrenceId"]).isEqualTo("$dpsOccurrenceId")
            assertThat(it["nomisAddressId"]).isEqualTo("321")
            assertThat(it["nomisAddressOwnerClass"]).isEqualTo("OFF")
          },
          isNull(),
        )
      }
    }

    @Nested
    inner class WhenMappingDetailsChange {
      val newEventTime = LocalDateTime.now().plusDays(1)

      @BeforeEach
      fun setUp() {
        mappingApi.stubGetScheduledMovementMapping(45678, dpsOccurrenceId, eventTime)
        mappingApi.stubGetTemporaryAbsenceApplicationMapping(111, dpsApplicationId)
        nomisApi.stubGetTemporaryAbsenceScheduledMovement(eventId = 45678, eventTime = newEventTime)
        mappingApi.stubUpdateScheduledMovementMapping()
        dpsApi.stubSyncScheduledTemporaryAbsence(parentId = dpsApplicationId, response = SyncResponse(dpsOccurrenceId))

        sendMessage(scheduledMovementEvent("SCHEDULED_EXT_MOVE-UPDATED"))
          .also { waitForAnyProcessingToComplete() }
      }

      @Test
      fun `should get mapping`() {
        mappingApi.verify(getRequestedFor(urlPathEqualTo("/mapping/temporary-absence/scheduled-movement/nomis-event-id/45678")))
      }

      @Test
      fun `should get NOMIS scheduled movement`() {
        nomisApi.verify(getRequestedFor(urlPathEqualTo("/movements/A1234BC/temporary-absences/scheduled-temporary-absence/45678")))
      }

      @Test
      fun `should update DPS scheduled movement`() {
        dpsApi.verify(
          putRequestedFor(urlPathEqualTo("/sync/scheduled-temporary-absence/$dpsApplicationId"))
            .withRequestBodyJsonPath("id", "$dpsOccurrenceId")
            .withRequestBodyJsonPath("location.address", "to full address"),
        )
      }

      @Test
      fun `should update mapping`() {
        mappingApi.verify(
          putRequestedFor(urlPathEqualTo("/mapping/temporary-absence/scheduled-movement"))
            .withRequestBodyJsonPath("dpsOccurrenceId", "$dpsOccurrenceId")
            .withRequestBodyJsonPath("nomisEventId", "45678")
            .withRequestBodyJsonPath("eventTime", "$newEventTime"),
        )
      }

      @Test
      fun `should create success telemetry`() {
        verify(telemetryClient).trackEvent(
          eq("temporary-absence-sync-scheduled-movement-updated-success"),
          check {
            assertThat(it["offenderNo"]).isEqualTo("A1234BC")
            assertThat(it["bookingId"]).isEqualTo("12345")
            assertThat(it["nomisApplicationId"]).isEqualTo("111")
            assertThat(it["nomisEventId"]).isEqualTo("45678")
            assertThat(it["directionCode"]).isEqualTo("OUT")
            assertThat(it["dpsOccurrenceId"]).isEqualTo("$dpsOccurrenceId")
            assertThat(it["nomisAddressId"]).isEqualTo("321")
            assertThat(it["nomisAddressOwnerClass"]).isEqualTo("OFF")
          },
          isNull(),
        )
      }
    }

    @Nested
    inner class WhenMappingUpdateFailsOnce {
      val newEventTime = LocalDateTime.now().plusDays(1)

      @BeforeEach
      fun setUp() {
        mappingApi.stubGetScheduledMovementMapping(45678, dpsOccurrenceId, eventTime)
        mappingApi.stubGetTemporaryAbsenceApplicationMapping(111, dpsApplicationId)
        nomisApi.stubGetTemporaryAbsenceScheduledMovement(eventId = 45678, eventTime = newEventTime)
        mappingApi.stubUpdateScheduledMovementMappingFailureFollowedBySuccess()
        dpsApi.stubSyncScheduledTemporaryAbsence(parentId = dpsApplicationId, response = SyncResponse(dpsOccurrenceId))

        sendMessage(scheduledMovementEvent("SCHEDULED_EXT_MOVE-UPDATED"))
          .also { waitForAnyProcessingToComplete("temporary-absence-sync-scheduled-movement-mapping-updated") }
      }

      @Test
      fun `should update DPS scheduled movement`() {
        dpsApi.verify(
          putRequestedFor(urlPathEqualTo("/sync/scheduled-temporary-absence/$dpsApplicationId"))
            .withRequestBodyJsonPath("id", "$dpsOccurrenceId")
            .withRequestBodyJsonPath("location.address", "to full address"),
        )
      }

      @Test
      fun `should update mapping on 2nd call`() {
        mappingApi.verify(
          count = 2,
          putRequestedFor(urlPathEqualTo("/mapping/temporary-absence/scheduled-movement"))
            .withRequestBodyJsonPath("nomisEventId", 45678)
            .withRequestBodyJsonPath("dpsOccurrenceId", dpsOccurrenceId)
            .withRequestBodyJsonPath("eventTime", "$newEventTime"),
        )
      }

      @Test
      fun `should create success telemetry`() {
        verify(telemetryClient).trackEvent(
          eq("temporary-absence-sync-scheduled-movement-updated-success"),
          check {
            assertThat(it["offenderNo"]).isEqualTo("A1234BC")
            assertThat(it["bookingId"]).isEqualTo("12345")
            assertThat(it["nomisApplicationId"]).isEqualTo("111")
            assertThat(it["nomisEventId"]).isEqualTo("45678")
            assertThat(it["directionCode"]).isEqualTo("OUT")
            assertThat(it["dpsOccurrenceId"]).isEqualTo("$dpsOccurrenceId")
            assertThat(it["nomisAddressId"]).isEqualTo("321")
            assertThat(it["nomisAddressOwnerClass"]).isEqualTo("OFF")
          },
          isNull(),
        )
      }
    }

    @Nested
    inner class WhenUpdatedInDps {
      @BeforeEach
      fun setUp() {
        sendMessage(scheduledMovementEvent("SCHEDULED_EXT_MOVE-UPDATED", "DPS_SYNCHRONISATION"))
          .also { waitForAnyProcessingToComplete() }
      }

      @Test
      fun `should NOT create DPS scheduled movement`() {
        dpsApi.verify(
          0,
          putRequestedFor(urlPathEqualTo("/sync/scheduled-temporary-absence/$dpsApplicationId")),
        )
      }

      @Test
      fun `should NOT get mapping`() {
        mappingApi.verify(
          count = 0,
          getRequestedFor(urlPathEqualTo("/mapping/temporary-absence/scheduled-movement/nomis-event-id/45678")),
        )
      }

      @Test
      fun `should create telemetry`() {
        verify(telemetryClient).trackEvent(
          eq("temporary-absence-sync-scheduled-movement-updated-skipped"),
          check {
            assertThat(it["offenderNo"]).isEqualTo("A1234BC")
            assertThat(it["bookingId"]).isEqualTo("12345")
            assertThat(it["nomisEventId"]).isEqualTo("45678")
            assertThat(it["directionCode"]).isEqualTo("OUT")
          },
          isNull(),
        )
      }
    }

    @Nested
    inner class WhenDpsUpdateFails {
      @BeforeEach
      fun setUp() {
        mappingApi.stubGetScheduledMovementMapping(45678, dpsOccurrenceId = dpsOccurrenceId)
        mappingApi.stubGetTemporaryAbsenceApplicationMapping(111, dpsApplicationId)
        nomisApi.stubGetTemporaryAbsenceScheduledMovement(eventId = 45678)
        dpsApi.stubSyncScheduledTemporaryAbsenceError(parentId = dpsApplicationId, status = 500)

        sendMessage(scheduledMovementEvent("SCHEDULED_EXT_MOVE-UPDATED"))
          .also { waitForAnyProcessingToComplete("temporary-absence-sync-scheduled-movement-updated-error") }
      }

      @Test
      fun `should try to update DPS scheduled movement`() {
        dpsApi.verify(
          putRequestedFor(urlPathEqualTo("/sync/scheduled-temporary-absence/$dpsApplicationId"))
            .withRequestBodyJsonPath("id", "$dpsOccurrenceId"),
        )
      }

      @Test
      fun `should create error telemetry`() {
        verify(telemetryClient).trackEvent(
          eq("temporary-absence-sync-scheduled-movement-updated-error"),
          check {
            assertThat(it["offenderNo"]).isEqualTo("A1234BC")
            assertThat(it["bookingId"]).isEqualTo("12345")
            assertThat(it["nomisEventId"]).isEqualTo("45678")
            assertThat(it["nomisApplicationId"]).isEqualTo("111")
            assertThat(it["dpsOccurrenceId"]).isEqualTo("$dpsOccurrenceId")
          },
          isNull(),
        )
      }
    }
  }

  @Nested
  @DisplayName("SCHEDULED_EXT_MOVE-DELETED")
  inner class TemporaryAbsenceScheduledMovementDeleted {

    @Nested
    inner class HappyPath {
      @BeforeEach
      fun setUp() {
        mappingApi.stubGetScheduledMovementMapping(45678)
        mappingApi.stubDeleteScheduledMovementMapping(nomisEventId = 45678)
        // TODO stub DPS API

        sendMessage(scheduledMovementEvent("SCHEDULED_EXT_MOVE-DELETED"))
          .also { waitForAnyProcessingToComplete() }
      }

      @Test
      fun `should get mapping`() {
        mappingApi.verify(getRequestedFor(urlPathEqualTo("/mapping/temporary-absence/scheduled-movement/nomis-event-id/45678")))
      }

      @Test
      fun `should delete mapping`() {
        mappingApi.verify(deleteRequestedFor(urlPathEqualTo("/mapping/temporary-absence/scheduled-movement/nomis-event-id/45678")))
      }

      @Test
      @Disabled("Waiting for DPS API to become available")
      fun `should delete DPS scheduled movement`() {
        // TODO verify DPS endpoint called
      }

      @Test
      fun `should create success telemetry`() {
        verify(telemetryClient).trackEvent(
          eq("temporary-absence-sync-scheduled-movement-deleted-success"),
          check {
            assertThat(it["offenderNo"]).isEqualTo("A1234BC")
            assertThat(it["bookingId"]).isEqualTo("12345")
            assertThat(it["nomisEventId"]).isEqualTo("45678")
            // TODO assert DPS id is tracked
          },
          isNull(),
        )
      }
    }

    @Nested
    inner class WhenMappingDoesNotExist {
      @BeforeEach
      fun setUp() {
        mappingApi.stubGetScheduledMovementMapping(45678, NOT_FOUND)

        sendMessage(scheduledMovementEvent("SCHEDULED_EXT_MOVE-DELETED"))
          .also { waitForAnyProcessingToComplete() }
      }

      @Test
      @Disabled("Waiting for DPS API to become available")
      fun `should NOT delete DPS scheduled movement`() {
        // TODO verify DPS endpoint not called
      }

      @Test
      fun `should get mapping`() {
        mappingApi.verify(
          getRequestedFor(urlPathEqualTo("/mapping/temporary-absence/scheduled-movement/nomis-event-id/45678")),
        )
      }

      @Test
      fun `should create telemetry`() {
        verify(telemetryClient).trackEvent(
          eq("temporary-absence-sync-scheduled-movement-deleted-ignored"),
          check {
            assertThat(it["offenderNo"]).isEqualTo("A1234BC")
            assertThat(it["bookingId"]).isEqualTo("12345")
            assertThat(it["nomisEventId"]).isEqualTo("45678")
            // TODO assert DPS id is tracked
          },
          isNull(),
        )
      }
    }

    @Nested
    @Disabled("Waiting for DPS API to become available")
    inner class WhenDpsDeleteFails {
      @BeforeEach
      fun setUp() {
        mappingApi.stubGetScheduledMovementMapping(45678)
        // TODO stub DPS API to reject delete

        sendMessage(scheduledMovementEvent("SCHEDULED_EXT_MOVE-DELETED"))
          .also { waitForAnyProcessingToComplete() }
      }

      @Test
      @Disabled("Waiting for DPS API to become available")
      fun `should try to delete DPS scheduled movement`() {
        // TODO verify DPS endpoint called
      }

      @Test
      fun `should create error telemetry`() {
        verify(telemetryClient).trackEvent(
          eq("temporary-absence-sync-scheduled-movement-deleted-error"),
          check {
            assertThat(it["offenderNo"]).isEqualTo("A1234BC")
            assertThat(it["bookingId"]).isEqualTo("12345")
            assertThat(it["nomisEventId"]).isEqualTo("45678")
            // TODO assert DPS id is tracked
          },
          isNull(),
        )
      }
    }
  }

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
        nomisApi.stubGetTemporaryAbsenceMovement(movementSeq = 154, movementApplicationId = 111, scheduledTemporaryAbsenceId = 45678)
        mappingApi.stubGetTemporaryAbsenceApplicationMapping(111)
        mappingApi.stubGetScheduledMovementMapping(45678, dpsOccurrenceId)
        mappingApi.stubCreateExternalMovementMapping()
        dpsApi.stubSyncTemporaryAbsenceMovement(response = SyncResponse(dpsMovementId))

        sendMessage(externalMovementEvent(inserted = true, direction = "OUT"))
          .also { waitForAnyProcessingToComplete() }
      }

      @Test
      fun `should check mapping`() {
        mappingApi.verify(getRequestedFor(urlPathEqualTo("/mapping/temporary-absence/external-movement/nomis-movement-id/12345/154")))
      }

      @Test
      fun `should check application mapping`() {
        mappingApi.verify(getRequestedFor(urlPathEqualTo("/mapping/temporary-absence/application/nomis-application-id/111")))
      }

      @Test
      fun `should check scheduled movement mapping`() {
        mappingApi.verify(getRequestedFor(urlPathEqualTo("/mapping/temporary-absence/scheduled-movement/nomis-event-id/45678")))
      }

      @Test
      fun `should get NOMIS external movement`() {
        nomisApi.verify(getRequestedFor(urlPathEqualTo("/movements/A1234BC/temporary-absences/temporary-absence/12345/154")))
      }

      @Test
      fun `should create DPS external movement`() {
        dpsApi.verify(
          putRequestedFor(urlPathEqualTo("/sync/temporary-absence-movement/A1234BC"))
            .withRequestBodyJsonPath("id", absent())
            .withRequestBodyJsonPath("occurrenceId", dpsOccurrenceId)
            .withRequestBodyJsonPath("legacyId", "12345_154")
            .withRequestBodyJsonPath("direction", "OUT")
            .withRequestBodyJsonPath("location.postcode", "S1 1AB")
            .withRequestBodyJsonPath("location.address", "full address"),
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
        nomisApi.stubGetTemporaryAbsenceReturnMovement(movementSeq = 154, movementApplicationId = 111, scheduledTemporaryAbsenceReturnId = 45678, scheduledTemporaryAbsenceId = 23456)
        mappingApi.stubGetTemporaryAbsenceApplicationMapping(111)
        mappingApi.stubGetScheduledMovementMapping(23456)
        mappingApi.stubCreateExternalMovementMapping()
        dpsApi.stubSyncTemporaryAbsenceMovement(response = SyncResponse(dpsMovementId))

        sendMessage(externalMovementEvent(inserted = true, direction = "IN"))
          .also { waitForAnyProcessingToComplete() }
      }

      @Test
      fun `should get NOMIS external movement`() {
        nomisApi.verify(getRequestedFor(urlPathEqualTo("/movements/A1234BC/temporary-absences/temporary-absence-return/12345/154")))
      }

      @Test
      fun `should check application mapping`() {
        mappingApi.verify(getRequestedFor(urlPathEqualTo("/mapping/temporary-absence/application/nomis-application-id/111")))
      }

      @Test
      fun `should check scheduled movement mapping`() {
        mappingApi.verify(getRequestedFor(urlPathEqualTo("/mapping/temporary-absence/scheduled-movement/nomis-event-id/23456")))
      }

      @Test
      fun `should create DPS external movement`() {
        dpsApi.verify(
          putRequestedFor(urlPathEqualTo("/sync/temporary-absence-movement/A1234BC"))
            .withRequestBodyJsonPath("id", absent())
            .withRequestBodyJsonPath("legacyId", "12345_154")
            .withRequestBodyJsonPath("direction", "IN")
            .withRequestBodyJsonPath("location.postcode", "S1 1AB")
            .withRequestBodyJsonPath("location.address", "full address"),
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
          },
          isNull(),
        )
      }
    }

    @Nested
    inner class WhenCreatedInDps {
      @BeforeEach
      fun setUp() {
        sendMessage(externalMovementEvent(inserted = true, auditModuleName = "DPS_SYNCHRONISATION"))
          .also { waitForAnyProcessingToComplete() }
      }

      @Test
      fun `should NOT create DPS scheduled movement`() {
        dpsApi.verify(
          0,
          putRequestedFor(urlPathEqualTo("/sync/temporary-absence-movement/A1234BC")),
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

        sendMessage(externalMovementEvent(inserted = true))
          .also { waitForAnyProcessingToComplete() }
      }

      @Test
      fun `should NOT create DPS scheduled movement`() {
        dpsApi.verify(
          0,
          putRequestedFor(urlPathEqualTo("/sync/temporary-absence-movement/A1234BC")),
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
        nomisApi.stubGetTemporaryAbsenceMovement(movementSeq = 154, movementApplicationId = 111, scheduledTemporaryAbsenceId = 45678)
        mappingApi.stubGetTemporaryAbsenceApplicationMapping(111, NOT_FOUND)
        mappingApi.stubGetScheduledMovementMapping(45678)

        sendMessage(externalMovementEvent(inserted = true, direction = "OUT"))
          .also { waitForAnyProcessingToComplete("temporary-absence-sync-external-movement-inserted-error") }
      }

      @Test
      fun `should check mapping`() {
        mappingApi.verify(getRequestedFor(urlPathEqualTo("/mapping/temporary-absence/external-movement/nomis-movement-id/12345/154")))
      }

      @Test
      fun `should check application mapping`() {
        mappingApi.verify(getRequestedFor(urlPathEqualTo("/mapping/temporary-absence/application/nomis-application-id/111")))
      }

      @Test
      fun `should create error telemetry`() {
        verify(telemetryClient).trackEvent(
          eq("temporary-absence-sync-external-movement-inserted-error"),
          check {
            assertThat(it["nomisApplicationId"]).isEqualTo("111")
            assertThat(it["error"]).isEqualTo("Application 111 not created yet so children cannot be processed")
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
        nomisApi.stubGetTemporaryAbsenceMovement(movementSeq = 154, movementApplicationId = 111, scheduledTemporaryAbsenceId = 45678)
        mappingApi.stubGetTemporaryAbsenceApplicationMapping(111)
        mappingApi.stubGetScheduledMovementMapping(45678, NOT_FOUND)

        sendMessage(externalMovementEvent(inserted = true, direction = "OUT"))
          .also { waitForAnyProcessingToComplete("temporary-absence-sync-external-movement-inserted-error") }
      }

      @Test
      fun `should check mapping`() {
        mappingApi.verify(getRequestedFor(urlPathEqualTo("/mapping/temporary-absence/external-movement/nomis-movement-id/12345/154")))
      }

      @Test
      fun `should check schedule mapping`() {
        mappingApi.verify(getRequestedFor(urlPathEqualTo("/mapping/temporary-absence/scheduled-movement/nomis-event-id/45678")))
      }

      @Test
      fun `should create error telemetry`() {
        verify(telemetryClient).trackEvent(
          eq("temporary-absence-sync-external-movement-inserted-error"),
          check {
            assertThat(it["nomisScheduledEventId"]).isEqualTo("45678")
            assertThat(it["error"]).isEqualTo("Scheduled event ID 45678 not created yet so children cannot be processed")
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
        nomisApi.stubGetTemporaryAbsenceReturnMovement(movementSeq = 154, movementApplicationId = 111, scheduledTemporaryAbsenceReturnId = 45678)
        mappingApi.stubGetTemporaryAbsenceApplicationMapping(111, NOT_FOUND)
        mappingApi.stubGetScheduledMovementMapping(45678)

        sendMessage(externalMovementEvent(inserted = true, direction = "IN"))
          .also { waitForAnyProcessingToComplete("temporary-absence-sync-external-movement-inserted-error") }
      }

      @Test
      fun `should check mapping`() {
        mappingApi.verify(getRequestedFor(urlPathEqualTo("/mapping/temporary-absence/external-movement/nomis-movement-id/12345/154")))
      }

      @Test
      fun `should check application mapping`() {
        mappingApi.verify(getRequestedFor(urlPathEqualTo("/mapping/temporary-absence/application/nomis-application-id/111")))
      }

      @Test
      fun `should create error telemetry`() {
        verify(telemetryClient).trackEvent(
          eq("temporary-absence-sync-external-movement-inserted-error"),
          check {
            assertThat(it["nomisApplicationId"]).isEqualTo("111")
            assertThat(it["error"]).isEqualTo("Application 111 not created yet so children cannot be processed")
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
        nomisApi.stubGetTemporaryAbsenceReturnMovement(movementSeq = 154, movementApplicationId = 111, scheduledTemporaryAbsenceReturnId = 45678, scheduledTemporaryAbsenceId = 23456)
        mappingApi.stubGetTemporaryAbsenceApplicationMapping(111)
        mappingApi.stubGetScheduledMovementMapping(23456, NOT_FOUND)

        sendMessage(externalMovementEvent(inserted = true, direction = "IN"))
          .also { waitForAnyProcessingToComplete("temporary-absence-sync-external-movement-inserted-error") }
      }

      @Test
      fun `should check mapping`() {
        mappingApi.verify(getRequestedFor(urlPathEqualTo("/mapping/temporary-absence/external-movement/nomis-movement-id/12345/154")))
      }

      @Test
      fun `should check schedule mapping`() {
        mappingApi.verify(getRequestedFor(urlPathEqualTo("/mapping/temporary-absence/scheduled-movement/nomis-event-id/23456")))
      }

      @Test
      fun `should create error telemetry`() {
        verify(telemetryClient).trackEvent(
          eq("temporary-absence-sync-external-movement-inserted-error"),
          check {
            assertThat(it["nomisScheduledParentEventId"]).isEqualTo("23456")
            assertThat(it["nomisScheduledEventId"]).isEqualTo("45678")
            assertThat(it["error"]).isEqualTo("Scheduled event ID 23456 not created yet so children cannot be processed")
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
    @DisplayName("EXTERNAL_MOVEMENT-CHANGED (inserted, unscheduled)")
    inner class TemporaryAbsenceUnscheduledExternalMovementCreated {

      @Nested
      @DisplayName("Happy path - unscheduled outbound movement")
      inner class HappyPathUnscheduledOutboundMovement {
        @BeforeEach
        fun setUp() {
          mappingApi.stubGetExternalMovementMapping(12345, 154, NOT_FOUND)
          nomisApi.stubGetTemporaryAbsenceMovement(movementSeq = 154, movementApplicationId = null, scheduledTemporaryAbsenceId = null)
          mappingApi.stubCreateExternalMovementMapping()
          dpsApi.stubSyncTemporaryAbsenceMovement(response = SyncResponse(dpsMovementId))

          sendMessage(externalMovementEvent(inserted = true, direction = "OUT"))
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
            getRequestedFor(urlPathMatching("/mapping/temporary-absence/application/nomis-application-id/.*")),
          )
        }

        @Test
        fun `should NOT check scheduled movement mapping`() {
          mappingApi.verify(
            count = 0,
            getRequestedFor(urlPathEqualTo("/mapping/temporary-absence/scheduled-movement/nomis-event-id/.*")),
          )
        }

        @Test
        fun `should create DPS external movement`() {
          dpsApi.verify(
            putRequestedFor(urlPathEqualTo("/sync/temporary-absence-movement/A1234BC"))
              .withRequestBodyJsonPath("id", absent())
              .withRequestBodyJsonPath("legacyId", "12345_154")
              .withRequestBodyJsonPath("direction", "OUT")
              .withRequestBodyJsonPath("location.postcode", "S1 1AB")
              .withRequestBodyJsonPath("location.address", "full address"),
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
          nomisApi.stubGetTemporaryAbsenceReturnMovement(movementSeq = 154, movementApplicationId = null, scheduledTemporaryAbsenceReturnId = null, scheduledTemporaryAbsenceId = null)
          mappingApi.stubCreateExternalMovementMapping()
          dpsApi.stubSyncTemporaryAbsenceMovement(response = SyncResponse(dpsMovementId))

          sendMessage(externalMovementEvent(inserted = true, direction = "IN"))
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
            getRequestedFor(urlPathMatching("/mapping/temporary-absence/application/nomis-application-id/.*")),
          )
        }

        @Test
        fun `should NOT check scheduled movement mapping`() {
          mappingApi.verify(
            count = 0,
            getRequestedFor(urlPathEqualTo("/mapping/temporary-absence/scheduled-movement/nomis-event-id/.*")),
          )
        }

        @Test
        fun `should create DPS external movement`() {
          dpsApi.verify(
            putRequestedFor(urlPathEqualTo("/sync/temporary-absence-movement/A1234BC"))
              .withRequestBodyJsonPath("id", absent())
              .withRequestBodyJsonPath("legacyId", "12345_154")
              .withRequestBodyJsonPath("direction", "IN")
              .withRequestBodyJsonPath("location.postcode", "S1 1AB")
              .withRequestBodyJsonPath("location.address", "full address"),
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
    }

    @Nested
    inner class WhenDuplicateMapping {
      @BeforeEach
      fun setUp() {
        mappingApi.stubGetExternalMovementMapping(12345, 154, NOT_FOUND)
        nomisApi.stubGetTemporaryAbsenceMovement(movementSeq = 154, movementApplicationId = 111, scheduledTemporaryAbsenceId = 45678)
        mappingApi.stubGetTemporaryAbsenceApplicationMapping(nomisApplicationId = 111)
        mappingApi.stubGetScheduledMovementMapping(nomisEventId = 45678)
        dpsApi.stubSyncTemporaryAbsenceMovement(response = SyncResponse(dpsMovementId))
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

        sendMessage(externalMovementEvent(inserted = true))
          .also { waitForAnyProcessingToComplete("temporary-absence-sync-external-movement-inserted-duplicate") }
      }

      @Test
      fun `should create DPS external movement`() {
        dpsApi.verify(
          putRequestedFor(urlPathEqualTo("/sync/temporary-absence-movement/A1234BC"))
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
        nomisApi.stubGetTemporaryAbsenceMovement(movementSeq = 154, movementApplicationId = 111, scheduledTemporaryAbsenceId = 45678)
        mappingApi.stubGetTemporaryAbsenceApplicationMapping(nomisApplicationId = 111)
        mappingApi.stubGetScheduledMovementMapping(nomisEventId = 45678)
        mappingApi.stubCreateExternalMovementMappingFailureFollowedBySuccess()
        dpsApi.stubSyncTemporaryAbsenceMovement(response = SyncResponse(dpsMovementId))

        sendMessage(externalMovementEvent(inserted = true))
          .also { waitForAnyProcessingToComplete("temporary-absence-sync-external-movement-mapping-created") }
      }

      @Test
      fun `should create DPS external movement`() {
        dpsApi.verify(
          putRequestedFor(urlPathEqualTo("/sync/temporary-absence-movement/A1234BC"))
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
          eq("temporary-absence-sync-external-movement-mapping-created"),
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
        nomisApi.stubGetTemporaryAbsenceMovement(movementSeq = 154, scheduledTemporaryAbsenceId = 45678)
        mappingApi.stubGetTemporaryAbsenceApplicationMapping(111)
        mappingApi.stubGetScheduledMovementMapping(45678, dpsOccurrenceId)
        dpsApi.stubSyncTemporaryAbsenceMovement(response = SyncResponse(dpsMovementId))

        sendMessage(externalMovementEvent(direction = "OUT"))
          .also { waitForAnyProcessingToComplete() }
      }

      @Test
      fun `should get mapping`() {
        mappingApi.verify(getRequestedFor(urlPathEqualTo("/mapping/temporary-absence/external-movement/nomis-movement-id/12345/154")))
      }

      @Test
      fun `should get NOMIS external movement`() {
        nomisApi.verify(getRequestedFor(urlPathEqualTo("/movements/A1234BC/temporary-absences/temporary-absence/12345/154")))
      }

      @Test
      fun `should update DPS external movement`() {
        dpsApi.verify(
          putRequestedFor(urlPathEqualTo("/sync/temporary-absence-movement/A1234BC"))
            .withRequestBodyJsonPath("id", dpsMovementId)
            .withRequestBodyJsonPath("occurrenceId", dpsOccurrenceId)
            .withRequestBodyJsonPath("legacyId", "12345_154")
            .withRequestBodyJsonPath("direction", "OUT")
            .withRequestBodyJsonPath("location.postcode", "S1 1AB")
            .withRequestBodyJsonPath("location.address", "full address"),
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
          },
          isNull(),
        )
      }
    }

    @Nested
    inner class WhenMappingChanged {
      val newAddress = "new address"

      @BeforeEach
      fun setUp() {
        mappingApi.stubGetExternalMovementMapping(12345, 154, dpsMovementId)
        nomisApi.stubGetTemporaryAbsenceMovement(movementSeq = 154, scheduledTemporaryAbsenceId = 45678, address = newAddress)
        mappingApi.stubGetTemporaryAbsenceApplicationMapping(111)
        mappingApi.stubGetScheduledMovementMapping(45678, dpsOccurrenceId)
        dpsApi.stubSyncTemporaryAbsenceMovement(response = SyncResponse(dpsMovementId))
        mappingApi.stubUpdateExternalMovementMapping()

        sendMessage(externalMovementEvent(direction = "OUT"))
          .also { waitForAnyProcessingToComplete() }
      }

      @Test
      fun `should update DPS external movement`() {
        dpsApi.verify(
          putRequestedFor(urlPathEqualTo("/sync/temporary-absence-movement/A1234BC"))
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
            .withRequestBodyJsonPath("dpsAddressText", newAddress),
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
          },
          isNull(),
        )
      }
    }

    @Nested
    inner class WhenMappingUpdateFailsOnce {
      val newAddress = "new address"

      @BeforeEach
      fun setUp() {
        mappingApi.stubGetExternalMovementMapping(12345, 154, dpsMovementId)
        nomisApi.stubGetTemporaryAbsenceMovement(movementSeq = 154, scheduledTemporaryAbsenceId = 45678, address = newAddress)
        mappingApi.stubGetTemporaryAbsenceApplicationMapping(111)
        mappingApi.stubGetScheduledMovementMapping(45678, dpsOccurrenceId)
        dpsApi.stubSyncTemporaryAbsenceMovement(response = SyncResponse(dpsMovementId))
        mappingApi.stubUpdateExternalMovementMappingFailureFollowedBySuccess()

        sendMessage(externalMovementEvent(direction = "OUT"))
          .also { waitForAnyProcessingToComplete("temporary-absence-sync-external-movement-mapping-updated") }
      }

      @Test
      fun `should update DPS external movement`() {
        dpsApi.verify(
          putRequestedFor(urlPathEqualTo("/sync/temporary-absence-movement/A1234BC"))
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
            .withRequestBodyJsonPath("dpsAddressText", newAddress),
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
        mappingApi.stubGetExternalMovementMapping(12345, 154, dpsMovementId)
        nomisApi.stubGetTemporaryAbsenceMovement(movementSeq = 154, movementApplicationId = null, scheduledTemporaryAbsenceId = null)
        mappingApi.stubGetTemporaryAbsenceApplicationMapping(111, NOT_FOUND)
        mappingApi.stubGetScheduledMovementMapping(45678, NOT_FOUND)
        dpsApi.stubSyncTemporaryAbsenceMovement(response = SyncResponse(dpsMovementId))

        sendMessage(externalMovementEvent(direction = "OUT"))
          .also { waitForAnyProcessingToComplete() }
      }

      @Test
      fun `should get mapping`() {
        mappingApi.verify(getRequestedFor(urlPathEqualTo("/mapping/temporary-absence/external-movement/nomis-movement-id/12345/154")))
      }

      @Test
      fun `should get NOMIS external movement`() {
        nomisApi.verify(getRequestedFor(urlPathEqualTo("/movements/A1234BC/temporary-absences/temporary-absence/12345/154")))
      }

      @Test
      fun `should update DPS external movement`() {
        dpsApi.verify(
          putRequestedFor(urlPathEqualTo("/sync/temporary-absence-movement/A1234BC"))
            .withRequestBodyJsonPath("id", dpsMovementId)
            .withRequestBodyJsonPath("occurrenceId", absent())
            .withRequestBodyJsonPath("legacyId", "12345_154")
            .withRequestBodyJsonPath("direction", "OUT")
            .withRequestBodyJsonPath("location.postcode", "S1 1AB")
            .withRequestBodyJsonPath("location.address", "full address"),
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
        nomisApi.stubGetTemporaryAbsenceReturnMovement(movementSeq = 154, scheduledTemporaryAbsenceReturnId = 45678, scheduledTemporaryAbsenceId = 23456)
        mappingApi.stubGetTemporaryAbsenceApplicationMapping(111)
        mappingApi.stubGetScheduledMovementMapping(23456, dpsOccurrenceId)
        dpsApi.stubSyncTemporaryAbsenceMovement(response = SyncResponse(dpsMovementId))

        sendMessage(externalMovementEvent(direction = "IN"))
          .also { waitForAnyProcessingToComplete() }
      }

      @Test
      fun `should get mapping`() {
        mappingApi.verify(getRequestedFor(urlPathEqualTo("/mapping/temporary-absence/external-movement/nomis-movement-id/12345/154")))
      }

      @Test
      fun `should get NOMIS external movement`() {
        nomisApi.verify(getRequestedFor(urlPathEqualTo("/movements/A1234BC/temporary-absences/temporary-absence-return/12345/154")))
      }

      @Test
      fun `should update DPS external movement`() {
        dpsApi.verify(
          putRequestedFor(urlPathEqualTo("/sync/temporary-absence-movement/A1234BC"))
            .withRequestBodyJsonPath("id", dpsMovementId)
            .withRequestBodyJsonPath("occurrenceId", dpsOccurrenceId)
            .withRequestBodyJsonPath("legacyId", "12345_154")
            .withRequestBodyJsonPath("direction", "IN")
            .withRequestBodyJsonPath("location.postcode", "S1 1AB")
            .withRequestBodyJsonPath("location.address", "full address"),
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
    @DisplayName("Happy path inbound movement - unscheduled")
    inner class HappyPathUnscheduledInboundMovement {
      @BeforeEach
      fun setUp() {
        mappingApi.stubGetExternalMovementMapping(12345, 154, dpsMovementId)
        nomisApi.stubGetTemporaryAbsenceReturnMovement(movementSeq = 154, movementApplicationId = null, scheduledTemporaryAbsenceReturnId = null, scheduledTemporaryAbsenceId = null)
        mappingApi.stubGetTemporaryAbsenceApplicationMapping(111, NOT_FOUND)
        mappingApi.stubGetScheduledMovementMapping(45678, NOT_FOUND)
        dpsApi.stubSyncTemporaryAbsenceMovement(response = SyncResponse(dpsMovementId))

        sendMessage(externalMovementEvent(direction = "IN"))
          .also { waitForAnyProcessingToComplete() }
      }

      @Test
      fun `should get mapping`() {
        mappingApi.verify(getRequestedFor(urlPathEqualTo("/mapping/temporary-absence/external-movement/nomis-movement-id/12345/154")))
      }

      @Test
      fun `should get NOMIS external movement`() {
        nomisApi.verify(getRequestedFor(urlPathEqualTo("/movements/A1234BC/temporary-absences/temporary-absence-return/12345/154")))
      }

      @Test
      fun `should update DPS external movement`() {
        dpsApi.verify(
          putRequestedFor(urlPathEqualTo("/sync/temporary-absence-movement/A1234BC"))
            .withRequestBodyJsonPath("id", dpsMovementId)
            .withRequestBodyJsonPath("occurrenceId", absent())
            .withRequestBodyJsonPath("legacyId", "12345_154")
            .withRequestBodyJsonPath("direction", "IN")
            .withRequestBodyJsonPath("location.postcode", "S1 1AB")
            .withRequestBodyJsonPath("location.address", "full address"),
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
      val newAddress = "new address"

      @BeforeEach
      fun setUp() {
        mappingApi.stubGetExternalMovementMapping(12345, 154, dpsMovementId)
        nomisApi.stubGetTemporaryAbsenceReturnMovement(movementSeq = 154, movementApplicationId = null, scheduledTemporaryAbsenceReturnId = null, scheduledTemporaryAbsenceId = null, address = newAddress)
        mappingApi.stubGetTemporaryAbsenceApplicationMapping(111, NOT_FOUND)
        mappingApi.stubGetScheduledMovementMapping(45678, NOT_FOUND)
        dpsApi.stubSyncTemporaryAbsenceMovement(response = SyncResponse(dpsMovementId))
        mappingApi.stubUpdateExternalMovementMapping()

        sendMessage(externalMovementEvent(direction = "IN"))
          .also { waitForAnyProcessingToComplete() }
      }

      @Test
      fun `should update DPS external movement`() {
        dpsApi.verify(
          putRequestedFor(urlPathEqualTo("/sync/temporary-absence-movement/A1234BC"))
            .withRequestBodyJsonPath("id", dpsMovementId)
            .withRequestBodyJsonPath("occurrenceId", absent())
            .withRequestBodyJsonPath("legacyId", "12345_154")
            .withRequestBodyJsonPath("direction", "IN")
            .withRequestBodyJsonPath("location.postcode", "S1 1AB")
            .withRequestBodyJsonPath("location.address", "new address"),
        )
      }

      @Test
      fun `should update mapping`() {
        mappingApi.verify(
          putRequestedFor(urlPathEqualTo("/mapping/temporary-absence/external-movement"))
            .withRequestBodyJsonPath("dpsMovementId", dpsMovementId)
            .withRequestBodyJsonPath("dpsOccurrenceId", absent())
            .withRequestBodyJsonPath("dpsAddressText", "new address"),
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
    inner class WhenUnscheduledMovementMappingFailsOnce {
      val newAddress = "new address"

      @BeforeEach
      fun setUp() {
        mappingApi.stubGetExternalMovementMapping(12345, 154, dpsMovementId)
        nomisApi.stubGetTemporaryAbsenceReturnMovement(movementSeq = 154, movementApplicationId = null, scheduledTemporaryAbsenceReturnId = null, scheduledTemporaryAbsenceId = null, address = newAddress)
        mappingApi.stubGetTemporaryAbsenceApplicationMapping(111, NOT_FOUND)
        mappingApi.stubGetScheduledMovementMapping(45678, NOT_FOUND)
        dpsApi.stubSyncTemporaryAbsenceMovement(response = SyncResponse(dpsMovementId))
        mappingApi.stubUpdateExternalMovementMappingFailureFollowedBySuccess()

        sendMessage(externalMovementEvent(direction = "IN"))
          .also { waitForAnyProcessingToComplete("temporary-absence-sync-external-movement-mapping-updated") }
      }

      @Test
      fun `should update DPS external movement`() {
        dpsApi.verify(
          putRequestedFor(urlPathEqualTo("/sync/temporary-absence-movement/A1234BC"))
            .withRequestBodyJsonPath("id", dpsMovementId)
            .withRequestBodyJsonPath("occurrenceId", absent())
            .withRequestBodyJsonPath("legacyId", "12345_154")
            .withRequestBodyJsonPath("direction", "IN")
            .withRequestBodyJsonPath("location.postcode", "S1 1AB")
            .withRequestBodyJsonPath("location.address", "new address"),
        )
      }

      @Test
      fun `should update mapping`() {
        mappingApi.verify(
          count = 2,
          putRequestedFor(urlPathEqualTo("/mapping/temporary-absence/external-movement"))
            .withRequestBodyJsonPath("dpsMovementId", dpsMovementId)
            .withRequestBodyJsonPath("dpsOccurrenceId", absent())
            .withRequestBodyJsonPath("dpsAddressText", "new address"),
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
        sendMessage(externalMovementEvent(auditModuleName = "DPS_SYNCHRONISATION"))
          .also { waitForAnyProcessingToComplete() }
      }

      @Test
      fun `should NOT update DPS external movement`() {
        dpsApi.verify(
          0,
          putRequestedFor(urlPathEqualTo("/sync/temporary-absence-movement/A1234BC")),
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

    @Nested
    inner class HappyPathOutboundMovement {
      @BeforeEach
      fun setUp() {
        mappingApi.stubGetExternalMovementMapping(12345, 154)
        mappingApi.stubDeleteExternalMovementMapping(12345, 154)
        // TODO stub DPS API

        sendMessage(externalMovementEvent(deleted = true, direction = "OUT"))
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
      @Disabled("Waiting for DPS API to become available")
      fun `should delete DPS external movement`() {
        // TODO verify DPS endpoint called
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
            // TODO assert DPS id is tracked
          },
          isNull(),
        )
      }
    }

    @Nested
    inner class HappyPathInboundMovement {
      @BeforeEach
      fun setUp() {
        mappingApi.stubGetExternalMovementMapping(12345, 154)
        mappingApi.stubDeleteExternalMovementMapping(12345, 154)
        // TODO stub DPS API

        sendMessage(externalMovementEvent(deleted = true, direction = "IN"))
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
      @Disabled("Waiting for DPS API to become available")
      fun `should delete DPS external movement`() {
        // TODO verify DPS endpoint called
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
            // TODO assert DPS id is tracked
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

        sendMessage(externalMovementEvent(deleted = true))
          .also { waitForAnyProcessingToComplete() }
      }

      @Test
      fun `should NOT delete DPS external movement`() {
        dpsApi.verify(
          0,
          putRequestedFor(urlPathEqualTo("/sync/temporary-absence-movement/A1234BC")),
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

  private fun externalMovementApplicationEvent(eventType: String, auditModuleName: String = "OIUSCINQ") = // language=JSON
    """{
         "Type" : "Notification",
         "MessageId" : "57126174-e2d7-518f-914e-0056a63363b0",
         "TopicArn" : "arn:aws:sns:eu-west-2:754256621582:cloud-platform-Digital-Prison-Services-f221e27fcfcf78f6ab4f4c3cc165eee7",
         "Message" : "{\"eventType\":\"$eventType\",\"eventDatetime\":\"2025-08-22T11:12:52\",\"nomisEventType\":\"$eventType\",\"bookingId\":12345,\"offenderIdDisplay\":\"A1234BC\",\"movementApplicationId\":111,\"auditModuleName\":\"$auditModuleName\"}",
         "Timestamp" : "2025-08-22T10:12:52.998Z",
         "SignatureVersion" : "1",
         "Signature" : "eePe/HtUdMyeFriH6GJe4FAJjYhQFjohJOu0+t8qULvpaw+qsGBfolKYa83fARpGDZJf9ceKd6kYGwF+OVeNViXluqPeUyoWbJ/lOjCs1tvlUuceCLy/7+eGGxkNASKJ1sWdwhO5J5I8WKUq5vfyYgL/Mygae6U71Bc0H9I2uVkw7tUYg0ZQBMSkA8HpuLLAN06qR5ahJnNDDxxoV07KY6E2dy8TheEo2Dhxq8hicl272LxWKMifM9VfR+D1i1eZNXDGsvvHmMCjumpxxYAJmrU+aqUzAU2KnhoZJTfeZT+RV+ZazjPLqX52zwA47ZFcqzCBnmrU6XwuHT4gKJcj1Q==",
         "SigningCertURL" : "https://sns.eu-west-2.amazonaws.com/SimpleNotificationService-6209c161c6221fdf56ec1eb5c821d112.pem",
         "UnsubscribeURL" : "https://sns.eu-west-2.amazonaws.com/?Action=Unsubscribe&SubscriptionArn=arn:aws:sns:eu-west-2:754256621582:cloud-platform-Digital-Prison-Services-f221e27fcfcf78f6ab4f4c3cc165eee7:3b68e1dd-c229-490f-bff9-05bd53595ddc",
         "MessageAttributes" : {
           "publishedAt" : {"Type":"String","Value":"2025-08-22T11:12:52.976312166+01:00"},
           "traceparent" : {"Type":"String","Value":"00-a0103c496069d331bd417cac78f4085c-0158c9f6485e8841-01"},
           "eventType" : {"Type":"String","Value":"$eventType"}
         }
       }
    """.trimMargin()

  private fun externalMovementApplicationMultiEvent(eventType: String, auditModuleName: String = "OCMOMSCH") = // language=JSON
    """{
         "Type" : "Notification",
         "MessageId" : "57126174-e2d7-518f-914e-0056a63363b0",
         "TopicArn" : "arn:aws:sns:eu-west-2:754256621582:cloud-platform-Digital-Prison-Services-f221e27fcfcf78f6ab4f4c3cc165eee7",
         "Message" : "{\"eventType\":\"$eventType\",\"eventDatetime\":\"2025-08-19T14:08:12\",\"nomisEventType\":\"$eventType\",\"bookingId\":12345,\"offenderIdDisplay\":\"A1234BC\",\"movementApplicationMultiId\":41,\"movementApplicationId\":111,\"auditModuleName\":\"$auditModuleName\"}",
         "Timestamp" : "2025-08-19T14:08:12.998Z",
         "SignatureVersion" : "1",
         "Signature" : "eePe/HtUdMyeFriH6GJe4FAJjYhQFjohJOu0+t8qULvpaw+qsGBfolKYa83fARpGDZJf9ceKd6kYGwF+OVeNViXluqPeUyoWbJ/lOjCs1tvlUuceCLy/7+eGGxkNASKJ1sWdwhO5J5I8WKUq5vfyYgL/Mygae6U71Bc0H9I2uVkw7tUYg0ZQBMSkA8HpuLLAN06qR5ahJnNDDxxoV07KY6E2dy8TheEo2Dhxq8hicl272LxWKMifM9VfR+D1i1eZNXDGsvvHmMCjumpxxYAJmrU+aqUzAU2KnhoZJTfeZT+RV+ZazjPLqX52zwA47ZFcqzCBnmrU6XwuHT4gKJcj1Q==",
         "SigningCertURL" : "https://sns.eu-west-2.amazonaws.com/SimpleNotificationService-6209c161c6221fdf56ec1eb5c821d112.pem",
         "UnsubscribeURL" : "https://sns.eu-west-2.amazonaws.com/?Action=Unsubscribe&SubscriptionArn=arn:aws:sns:eu-west-2:754256621582:cloud-platform-Digital-Prison-Services-f221e27fcfcf78f6ab4f4c3cc165eee7:3b68e1dd-c229-490f-bff9-05bd53595ddc",
         "MessageAttributes" : {
           "publishedAt" : {"Type":"String","Value":"2025-08-19T14:08:12.976312166+01:00"},
           "traceparent" : {"Type":"String","Value":"00-a0103c496069d331bd417cac78f4085c-0158c9f6485e8841-01"},
           "eventType" : {"Type":"String","Value":"$eventType"}
         }
       }
    """.trimMargin()

  private fun scheduledMovementEvent(eventType: String, auditModuleName: String = "OCUCANTR", nomisEventType: String = "TAP", direction: String = "OUT") = // language=JSON
    """{
         "Type" : "Notification",
         "MessageId" : "57126174-e2d7-518f-914e-0056a63363b0",
         "TopicArn" : "arn:aws:sns:eu-west-2:754256621582:cloud-platform-Digital-Prison-Services-f221e27fcfcf78f6ab4f4c3cc165eee7",
         "Message" : "{\"eventType\":\"$eventType\",\"eventDatetime\":\"2025-09-02T09:19:03\",\"nomisEventType\":\"$eventType\",\"bookingId\":12345,\"offenderIdDisplay\":\"A1234BC\",\"eventId\":45678,\"eventMovementType\":\"$nomisEventType\",\"auditModuleName\":\"$auditModuleName\",\"directionCode\":\"$direction\"}",
         "Timestamp" : "2025-09-02T09:19:03.998Z",
         "SignatureVersion" : "1",
         "Signature" : "eePe/HtUdMyeFriH6GJe4FAJjYhQFjohJOu0+t8qULvpaw+qsGBfolKYa83fARpGDZJf9ceKd6kYGwF+OVeNViXluqPeUyoWbJ/lOjCs1tvlUuceCLy/7+eGGxkNASKJ1sWdwhO5J5I8WKUq5vfyYgL/Mygae6U71Bc0H9I2uVkw7tUYg0ZQBMSkA8HpuLLAN06qR5ahJnNDDxxoV07KY6E2dy8TheEo2Dhxq8hicl272LxWKMifM9VfR+D1i1eZNXDGsvvHmMCjumpxxYAJmrU+aqUzAU2KnhoZJTfeZT+RV+ZazjPLqX52zwA47ZFcqzCBnmrU6XwuHT4gKJcj1Q==",
         "SigningCertURL" : "https://sns.eu-west-2.amazonaws.com/SimpleNotificationService-6209c161c6221fdf56ec1eb5c821d112.pem",
         "UnsubscribeURL" : "https://sns.eu-west-2.amazonaws.com/?Action=Unsubscribe&SubscriptionArn=arn:aws:sns:eu-west-2:754256621582:cloud-platform-Digital-Prison-Services-f221e27fcfcf78f6ab4f4c3cc165eee7:3b68e1dd-c229-490f-bff9-05bd53595ddc",
         "MessageAttributes" : {
           "publishedAt" : {"Type":"String","Value":"2025-09-02T09:19:03.976312166+01:00"},
           "traceparent" : {"Type":"String","Value":"00-a0103c496069d331bd417cac78f4085c-0158c9f6485e8841-01"},
           "eventType" : {"Type":"String","Value":"$eventType"}
         }
       }
    """.trimMargin()

  private fun externalMovementEvent(
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
