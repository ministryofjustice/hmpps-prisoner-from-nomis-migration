package uk.gov.justice.digital.hmpps.prisonerfromnomismigration.movements

import com.github.tomakehurst.wiremock.client.WireMock.deleteRequestedFor
import com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor
import com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor
import com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Disabled
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
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomismappings.model.DuplicateErrorContentObject
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomismappings.model.DuplicateMappingErrorResponse
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomismappings.model.TemporaryAbsenceApplicationSyncMappingDto
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomismappings.model.TemporaryAbsenceOutsideMovementSyncMappingDto
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.wiremock.withRequestBodyJsonPath
import java.util.*

class ExternalMovementsSyncIntTest(
  @Autowired private val nomisApi: ExternalMovementsNomisApiMockServer,
  @Autowired private val mappingApi: ExternalMovementsMappingApiMockServer,
) : SqsIntegrationTestBase() {

  @Nested
  @DisplayName("MOVEMENT_APPLICATION-INSERTED")
  inner class TemporaryAbsenceApplicationCreated {

    @Nested
    inner class HappyPath {
      @BeforeEach
      fun setUp() {
        mappingApi.stubGetTemporaryAbsenceApplicationMapping(111, NOT_FOUND)
        mappingApi.stubCreateTemporaryAbsenceApplicationMapping()
        nomisApi.stubGetTemporaryAbsenceApplication(applicationId = 111)
        // TODO stub DPS API

        sendMessage(externalMovementApplicationEvent("MOVEMENT_APPLICATION-INSERTED"))
          .also { waitForAnyProcessingToComplete() }
      }

      @Test
      fun `should check mapping`() {
        mappingApi.verify(getRequestedFor(urlPathEqualTo("/mapping/temporary-absence/application/nomis-application-id/111")))
      }

      @Test
      fun `should get NOMIS application`() {
        nomisApi.verify(getRequestedFor(urlPathEqualTo("/prisoners/A1234BC/temporary-absences/application/111")))
      }

      @Test
      @Disabled("Waiting for DPS API to become available")
      fun `should create DPS application`() {
        // TODO verify DPS endpoint called
      }

      @Test
      fun `should create mapping`() {
        mappingApi.verify(
          postRequestedFor(urlPathEqualTo("/mapping/temporary-absence/application"))
            .withRequestBodyJsonPath("prisonerNumber", "A1234BC")
            .withRequestBodyJsonPath("bookingId", 12345)
            .withRequestBodyJsonPath("nomisMovementApplicationId", 111)
            // TODO verify DPS id
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
        mappingApi.stubGetTemporaryAbsenceApplicationMapping(111)

        sendMessage(externalMovementApplicationEvent("MOVEMENT_APPLICATION-INSERTED", "DPS_SYNCHRONISATION"))
          .also { waitForAnyProcessingToComplete() }
      }

      @Test
      @Disabled("Waiting for DPS API to become available")
      fun `should NOT create DPS application`() {
        // TODO verify DPS endpoint not called
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
            // TODO assert DPS id is tracked
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
      @Disabled("Waiting for DPS API to become available")
      fun `should NOT create DPS application`() {
        // TODO verify DPS endpoint not called
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
            // TODO assert DPS id is tracked
          },
          isNull(),
        )
      }
    }

    @Nested
    @Disabled("Waiting for DPS API to become available")
    inner class WhenCreateFailsInDps {
      @BeforeEach
      fun setUp() {
        mappingApi.stubGetTemporaryAbsenceApplicationMapping(111, NOT_FOUND)
        mappingApi.stubCreateTemporaryAbsenceApplicationMapping()
        nomisApi.stubGetTemporaryAbsenceApplication(applicationId = 111)
        // TODO stub DPS API to reject create

        sendMessage(externalMovementApplicationEvent("MOVEMENT_APPLICATION-INSERTED"))
          .also { waitForAnyProcessingToComplete() }
      }

      @Test
      fun `should create DPS application`() {
        // TODO verify DPS endpoint called
      }

      @Test
      fun `should NOT create mapping`() {
        mappingApi.verify(
          count = 0,
          postRequestedFor(urlPathEqualTo("/mapping/temporary-absence/application"))
            .withRequestBodyJsonPath("prisonerNumber", "A1234BC")
            .withRequestBodyJsonPath("bookingId", 12345)
            .withRequestBodyJsonPath("nomisMovementApplicationId", 111)
            // TODO verify DPS id
            .withRequestBodyJsonPath("mappingType", "NOMIS_CREATED"),
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
        // TODO stub DPS API
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
                dpsMovementApplicationId = UUID.randomUUID(),
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
      @Disabled("Waiting for DPS API to become available")
      fun `should create DPS application only once`() {
        // TODO verify DPS endpoint called
      }

      @Test
      fun `should create mapping only once`() {
        mappingApi.verify(
          postRequestedFor(urlPathEqualTo("/mapping/temporary-absence/application"))
            .withRequestBodyJsonPath("prisonerNumber", "A1234BC")
            .withRequestBodyJsonPath("bookingId", 12345)
            .withRequestBodyJsonPath("nomisMovementApplicationId", 111)
            // TODO verify DPS id
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
            // TODO assert DPS id is tracked
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
            // TODO verify DPS ids
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
        // TODO stub DPS API
        mappingApi.stubCreateTemporaryAbsenceApplicationMappingFailureFollowedBySuccess()

        sendMessage(externalMovementApplicationEvent("MOVEMENT_APPLICATION-INSERTED"))
          .also { waitForAnyProcessingToComplete("temporary-absence-sync-application-mapping-created") }
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
          postRequestedFor(urlPathEqualTo("/mapping/temporary-absence/application"))
            .withRequestBodyJsonPath("prisonerNumber", "A1234BC")
            .withRequestBodyJsonPath("bookingId", 12345)
            .withRequestBodyJsonPath("nomisMovementApplicationId", 111)
            // TODO verify DPS id
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
            // TODO assert DPS id is tracked
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
            // TODO assert DPS id is tracked
          },
          isNull(),
        )
      }
    }
  }

  @Nested
  @DisplayName("MOVEMENT_APPLICATION-UPDATED")
  inner class TemporaryAbsenceApplicationUpdated {

    @Nested
    inner class HappyPath {
      @BeforeEach
      fun setUp() {
        mappingApi.stubGetTemporaryAbsenceApplicationMapping(111)
        nomisApi.stubGetTemporaryAbsenceApplication(applicationId = 111)
        // TODO stub DPS API

        sendMessage(externalMovementApplicationEvent("MOVEMENT_APPLICATION-UPDATED"))
          .also { waitForAnyProcessingToComplete() }
      }

      @Test
      fun `should get mapping`() {
        mappingApi.verify(getRequestedFor(urlPathEqualTo("/mapping/temporary-absence/application/nomis-application-id/111")))
      }

      @Test
      fun `should get NOMIS application`() {
        nomisApi.verify(getRequestedFor(urlPathEqualTo("/prisoners/A1234BC/temporary-absences/application/111")))
      }

      @Test
      @Disabled("Waiting for DPS API to become available")
      fun `should update DPS application`() {
        // TODO verify DPS endpoint called
      }

      @Test
      fun `should create success telemetry`() {
        verify(telemetryClient).trackEvent(
          eq("temporary-absence-sync-application-updated-success"),
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
    inner class WhenUpdatedInDps {
      @BeforeEach
      fun setUp() {
        sendMessage(externalMovementApplicationEvent("MOVEMENT_APPLICATION-UPDATED", "DPS_SYNCHRONISATION"))
          .also { waitForAnyProcessingToComplete() }
      }

      @Test
      @Disabled("Waiting for DPS API to become available")
      fun `should NOT create DPS application`() {
        // TODO verify DPS endpoint not called
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
        mappingApi.stubGetTemporaryAbsenceApplicationMapping(111)
        nomisApi.stubGetTemporaryAbsenceApplication(applicationId = 111)
        // TODO stub DPS API to reject update

        sendMessage(externalMovementApplicationEvent("MOVEMENT_APPLICATION-UPDATED"))
          .also { waitForAnyProcessingToComplete() }
      }

      @Test
      @Disabled("Waiting for DPS API to become available")
      fun `should try to update DPS application`() {
        // TODO verify DPS endpoint called
      }

      @Test
      fun `should create error telemetry`() {
        verify(telemetryClient).trackEvent(
          eq("temporary-absence-sync-application-updated-error"),
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
    @Disabled("Waiting for DPS API to become available")
    inner class WhenCreateFailsInDps {
      @BeforeEach
      fun setUp() {
        mappingApi.stubGetOutsideMovementMapping(41, NOT_FOUND)
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
}
