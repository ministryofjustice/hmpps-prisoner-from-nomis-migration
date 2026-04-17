package uk.gov.justice.digital.hmpps.prisonerfromnomismigration.movements.taps

import com.github.tomakehurst.wiremock.client.WireMock.deleteRequestedFor
import com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor
import com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor
import com.github.tomakehurst.wiremock.client.WireMock.putRequestedFor
import com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo
import com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo
import com.github.tomakehurst.wiremock.client.WireMock.urlPathMatching
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.within
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
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.movements.ExternalMovementsDpsApiExtension.Companion.dpsExtMovementsServer
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.movements.ExternalMovementsDpsApiMockServer
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.movements.ExternalMovementsMappingApiMockServer
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.movements.ExternalMovementsNomisApiMockServer
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.movements.ExternalMovementsNomisApiMockServer.Companion.temporaryAbsenceApplicationResponse
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.movements.model.SyncResponse
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.movements.model.SyncWriteTapAuthorisation
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomismappings.model.DuplicateErrorContentObject
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomismappings.model.DuplicateMappingErrorResponse
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomismappings.model.TemporaryAbsenceApplicationSyncMappingDto
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.wiremock.withRequestBodyJsonPath
import java.time.Duration
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.util.UUID

class TapApplicationIntTest(
  @Autowired private val nomisApi: ExternalMovementsNomisApiMockServer,
  @Autowired private val mappingApi: ExternalMovementsMappingApiMockServer,
) : SqsIntegrationTestBase() {

  private val dpsApi = dpsExtMovementsServer

  private val now = LocalDateTime.now()
  private val today = now.toLocalDate()
  private val tomorrow = now.plusDays(1)

  @Nested
  @DisplayName("MOVEMENT_APPLICATION-INSERTED")
  inner class TapApplicationCreated {
    private var dpsId: UUID = UUID.randomUUID()

    @Nested
    inner class HappyPath {
      @BeforeEach
      fun setUp() {
        mappingApi.stubGetTemporaryAbsenceApplicationMapping(111, NOT_FOUND)
        mappingApi.stubCreateTemporaryAbsenceApplicationMapping()
        nomisApi.stubGetTemporaryAbsenceApplication(applicationId = 111)
        dpsApi.stubSyncTapAuthorisation(response = SyncResponse(dpsId))

        sendMessage(tapApplicationEvent("MOVEMENT_APPLICATION-INSERTED"))
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
        ExternalMovementsDpsApiMockServer.getRequestBody<SyncWriteTapAuthorisation>(
          putRequestedFor(urlEqualTo("/sync/temporary-absence-authorisations/A1234BC")),
        ).apply {
          assertThat(id).isNull()
          assertThat(prisonCode).isEqualTo("LEI")
          assertThat(statusCode).isEqualTo("APPROVED")
          assertThat(absenceTypeCode).isEqualTo("RR")
          assertThat(absenceSubTypeCode).isEqualTo("SPL")
          assertThat(absenceReasonCode).isEqualTo("C5")
          assertThat(accompaniedByCode).isEqualTo("P")
          assertThat(transportCode).isEqualTo("VAN")
          assertThat(repeat).isEqualTo(false)
          assertThat(start).isEqualTo("$today")
          assertThat(end).isEqualTo("${tomorrow.toLocalDate()}")
          assertThat(comments).isEqualTo("application comment")
          assertThat(legacyId).isEqualTo(111)
          assertThat(LocalTime.parse(startTime)).isCloseTo(now.minusDays(1).toLocalTime(), within(Duration.ofMinutes(5)))
          assertThat(LocalTime.parse(endTime)).isCloseTo(now.toLocalTime(), within(Duration.ofMinutes(5)))
          assertThat(location!!.uprn).isNull()
          assertThat(location.address).isEqualTo("some full address")
          assertThat(location.description).isEqualTo("some address description")
          assertThat(location.postcode).isEqualTo("S1 1AA")
        }
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
            assertThat(it["dpsAuthorisationId"]).isEqualTo(dpsId.toString())
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

        sendMessage(tapApplicationEvent("MOVEMENT_APPLICATION-INSERTED", "DPS_SYNCHRONISATION"))
          .also { waitForAnyProcessingToComplete() }
      }

      @Test
      fun `should NOT create DPS application`() {
        dpsApi.verify(0, putRequestedFor(urlPathEqualTo("/sync/temporary-absence-authorisations/A1234BC")))
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

        sendMessage(tapApplicationEvent("MOVEMENT_APPLICATION-INSERTED"))
          .also { waitForAnyProcessingToComplete() }
      }

      @Test
      fun `should NOT create DPS application`() {
        dpsApi.verify(0, putRequestedFor(urlPathEqualTo("/sync/temporary-absence-authorisations/A1234BC")))
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
        dpsApi.stubSyncTapAuthorisationError()

        sendMessage(tapApplicationEvent("MOVEMENT_APPLICATION-INSERTED"))
          .also { waitForAnyProcessingToComplete("temporary-absence-sync-application-inserted-error") }
      }

      @Test
      fun `should attempt to create DPS application`() {
        dpsApi.verify(putRequestedFor(urlPathEqualTo("/sync/temporary-absence-authorisations/A1234BC")))
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
        dpsApi.stubSyncTapAuthorisation(response = SyncResponse(dpsId))
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

        sendMessage(tapApplicationEvent("MOVEMENT_APPLICATION-INSERTED"))
          .also { waitForAnyProcessingToComplete("temporary-absence-sync-application-inserted-duplicate") }
      }

      @Test
      fun `should create DPS application only once`() {
        dpsApi.verify(putRequestedFor(urlPathEqualTo("/sync/temporary-absence-authorisations/A1234BC")))
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
            assertThat(it["dpsAuthorisationId"]).isEqualTo(dpsId.toString())
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
        dpsApi.stubSyncTapAuthorisation(response = SyncResponse(dpsId))
        mappingApi.stubCreateTemporaryAbsenceApplicationMappingFailureFollowedBySuccess()

        sendMessage(tapApplicationEvent("MOVEMENT_APPLICATION-INSERTED"))
          .also { waitForAnyProcessingToComplete("temporary-absence-sync-application-mapping-retry-created") }
      }

      @Test
      fun `should create DPS application`() {
        dpsApi.verify(putRequestedFor(urlPathEqualTo("/sync/temporary-absence-authorisations/A1234BC")))
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
            assertThat(it["dpsAuthorisationId"]).isEqualTo(dpsId.toString())
          },
          isNull(),
        )
      }

      @Test
      fun `should publish mapping created telemetry`() {
        verify(telemetryClient).trackEvent(
          eq("temporary-absence-sync-application-mapping-retry-created"),
          check {
            assertThat(it["offenderNo"]).isEqualTo("A1234BC")
            assertThat(it["bookingId"]).isEqualTo("12345")
            assertThat(it["nomisApplicationId"]).isEqualTo("111")
            assertThat(it["dpsAuthorisationId"]).isEqualTo(dpsId.toString())
          },
          isNull(),
        )
      }
    }
  }

  @Nested
  @DisplayName("MOVEMENT_APPLICATION-UPDATED")
  inner class TapApplicationUpdated {
    private var dpsId: UUID = UUID.randomUUID()

    @Nested
    inner class HappyPath {
      @BeforeEach
      fun setUp() {
        mappingApi.stubGetTemporaryAbsenceApplicationMapping(111, dpsId)
        nomisApi.stubGetTemporaryAbsenceApplication(applicationId = 111)
        dpsApi.stubSyncTapAuthorisation(response = SyncResponse(dpsId))

        sendMessage(tapApplicationEvent("MOVEMENT_APPLICATION-UPDATED"))
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
          putRequestedFor(urlPathEqualTo("/sync/temporary-absence-authorisations/A1234BC"))
            .withRequestBodyJsonPath("id", equalTo = dpsId)
            .withRequestBodyJsonPath("prisonCode", equalTo = "LEI")
            .withRequestBodyJsonPath("statusCode", equalTo = "APPROVED")
            .withRequestBodyJsonPath("absenceTypeCode", equalTo = "RR")
            .withRequestBodyJsonPath("absenceSubTypeCode", equalTo = "SPL")
            .withRequestBodyJsonPath("absenceReasonCode", equalTo = "C5")
            .withRequestBodyJsonPath("accompaniedByCode", equalTo = "P")
            .withRequestBodyJsonPath("repeat", equalTo = false)
            .withRequestBodyJsonPath("start", equalTo = "$today")
            .withRequestBodyJsonPath("end", equalTo = "${tomorrow.toLocalDate()}")
            .withRequestBodyJsonPath("comments", equalTo = "application comment")
            .withRequestBodyJsonPath("legacyId", equalTo = 111),
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
            assertThat(it["dpsAuthorisationId"]).isEqualTo(dpsId.toString())
          },
          isNull(),
        )
      }
    }

    @Nested
    inner class WhenUpdatedInDps {
      @BeforeEach
      fun setUp() {
        sendMessage(tapApplicationEvent("MOVEMENT_APPLICATION-UPDATED", "DPS_SYNCHRONISATION"))
          .also { waitForAnyProcessingToComplete() }
      }

      @Test
      fun `should NOT create DPS application`() {
        dpsApi.verify(0, putRequestedFor(urlPathEqualTo("/sync/temporary-absence-authorisations/A1234BC")))
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
        dpsApi.stubSyncTapAuthorisationError()

        sendMessage(tapApplicationEvent("MOVEMENT_APPLICATION-UPDATED"))
          .also { waitForAnyProcessingToComplete("temporary-absence-sync-application-updated-error") }
      }

      @Test
      fun `should try to update DPS application`() {
        dpsApi.verify(putRequestedFor(urlPathEqualTo("/sync/temporary-absence-authorisations/A1234BC")))
      }

      @Test
      fun `should create error telemetry`() {
        verify(telemetryClient).trackEvent(
          eq("temporary-absence-sync-application-updated-error"),
          check {
            assertThat(it["offenderNo"]).isEqualTo("A1234BC")
            assertThat(it["bookingId"]).isEqualTo("12345")
            assertThat(it["nomisApplicationId"]).isEqualTo("111")
            assertThat(it["dpsAuthorisationId"]).isEqualTo(dpsId.toString())
          },
          isNull(),
        )
      }
    }

    @Nested
    inner class HappyPathOldBookingFutureApplication {
      @BeforeEach
      fun setUp() {
        mappingApi.stubGetTemporaryAbsenceApplicationMapping(111, dpsId)
        nomisApi.stubGetTemporaryAbsenceApplication(
          applicationId = 111,
          response = temporaryAbsenceApplicationResponse(
            activeBooking = true,
            latestBooking = false,
            status = "APP-SCH",
            fromDate = LocalDate.now(),
            toDate = LocalDate.now().plusDays(1),
          ),
        )
        dpsApi.stubSyncTapAuthorisation(response = SyncResponse(dpsId))

        sendMessage(tapApplicationEvent("MOVEMENT_APPLICATION-UPDATED"))
          .also { waitForAnyProcessingToComplete() }
      }

      @Test
      fun `should set DPS application status to expired`() {
        dpsApi.verify(
          putRequestedFor(urlPathEqualTo("/sync/temporary-absence-authorisations/A1234BC"))
            .withRequestBodyJsonPath("statusCode", equalTo = "EXPIRED"),
        )
      }
    }
  }

  @Nested
  @DisplayName("MOVEMENT_APPLICATION-DELETED")
  inner class TapApplicationDeleted {
    private val dpsAuthorisationId = UUID.randomUUID()

    @Nested
    inner class HappyPath {
      @BeforeEach
      fun setUp() {
        mappingApi.stubGetTemporaryAbsenceApplicationMapping(111, dpsAuthorisationId)
        nomisApi.stubGetTemporaryAbsenceApplication(applicationId = 111)
        mappingApi.stubDeleteTemporaryAbsenceApplicationMapping(nomisApplicationId = 111)
        dpsApi.stubDeleteTapAuthorisation(dpsAuthorisationId)

        sendMessage(tapApplicationEvent("MOVEMENT_APPLICATION-DELETED"))
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
      fun `should delete DPS application`() {
        dpsApi.verify(deleteRequestedFor(urlPathEqualTo("/sync/temporary-absence-authorisations/$dpsAuthorisationId")))
      }

      @Test
      fun `should create success telemetry`() {
        verify(telemetryClient).trackEvent(
          eq("temporary-absence-sync-application-deleted-success"),
          check {
            assertThat(it["offenderNo"]).isEqualTo("A1234BC")
            assertThat(it["bookingId"]).isEqualTo("12345")
            assertThat(it["nomisApplicationId"]).isEqualTo("111")
            assertThat(it["dpsAuthorisationId"]).isEqualTo("$dpsAuthorisationId")
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

        sendMessage(tapApplicationEvent("MOVEMENT_APPLICATION-DELETED"))
          .also { waitForAnyProcessingToComplete() }
      }

      @Test
      fun `should NOT delete DPS application`() {
        dpsApi.verify(0, deleteRequestedFor(urlPathMatching("/sync/temporary-absence-authorisations/.*")))
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
          },
          isNull(),
        )
      }
    }

    @Nested
    inner class WhenDpsDeleteFails {
      @BeforeEach
      fun setUp() {
        mappingApi.stubGetTemporaryAbsenceApplicationMapping(111, dpsAuthorisationId)
        dpsApi.stubDeleteTapAuthorisationError(dpsAuthorisationId)

        sendMessage(tapApplicationEvent("MOVEMENT_APPLICATION-DELETED"))
          .also { waitForAnyProcessingToComplete("temporary-absence-sync-application-deleted-error") }
      }

      @Test
      fun `should try to delete DPS application`() {
        dpsApi.verify(deleteRequestedFor(urlPathEqualTo("/sync/temporary-absence-authorisations/$dpsAuthorisationId")))
      }

      @Test
      fun `should create error telemetry`() {
        verify(telemetryClient).trackEvent(
          eq("temporary-absence-sync-application-deleted-error"),
          check {
            assertThat(it["offenderNo"]).isEqualTo("A1234BC")
            assertThat(it["bookingId"]).isEqualTo("12345")
            assertThat(it["nomisApplicationId"]).isEqualTo("111")
            assertThat(it["dpsAuthorisationId"]).isEqualTo("$dpsAuthorisationId")
          },
          isNull(),
        )
      }

      @Test
      fun `should not delete mapping`() {
        mappingApi.verify(
          count = 0,
          deleteRequestedFor(urlPathEqualTo("/mapping/temporary-absence/application/nomis-application-id/111")),
        )
      }
    }
  }

  fun sendMessage(event: String) = awsSqsExternalMovementsOffenderEventsClient.sendMessage(
    externalMovementsQueueOffenderEventsUrl,
    event,
  )

  private fun tapApplicationEvent(eventType: String, auditModuleName: String = "OIUSCINQ") = // language=JSON
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
}
