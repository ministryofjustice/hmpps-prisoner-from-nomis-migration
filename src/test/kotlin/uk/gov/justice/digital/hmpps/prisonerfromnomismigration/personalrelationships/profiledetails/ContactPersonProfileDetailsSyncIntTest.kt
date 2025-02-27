package uk.gov.justice.digital.hmpps.prisonerfromnomismigration.personalrelationships.profiledetails

import com.github.tomakehurst.wiremock.client.WireMock.absent
import com.github.tomakehurst.wiremock.client.WireMock.equalTo
import com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor
import com.github.tomakehurst.wiremock.client.WireMock.matchingJsonPath
import com.github.tomakehurst.wiremock.client.WireMock.putRequestedFor
import com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo
import com.github.tomakehurst.wiremock.client.WireMock.urlPathMatching
import com.github.tomakehurst.wiremock.matching.StringValuePattern
import kotlinx.coroutines.test.runTest
import org.assertj.core.api.Assertions.assertThat
import org.awaitility.kotlin.await
import org.awaitility.kotlin.untilAsserted
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertDoesNotThrow
import org.mockito.ArgumentMatchers.anyMap
import org.mockito.ArgumentMatchers.anyString
import org.mockito.kotlin.any
import org.mockito.kotlin.check
import org.mockito.kotlin.eq
import org.mockito.kotlin.isNull
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.http.HttpStatus.INTERNAL_SERVER_ERROR
import org.springframework.http.HttpStatus.NOT_FOUND
import org.springframework.http.MediaType
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.integration.SqsIntegrationTestBase
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.integration.sendMessage
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomissync.model.BookingProfileDetailsResponse
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomissync.model.PrisonerProfileDetailsResponse
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomissync.model.ProfileDetailsResponse
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.personalrelationships.ContactPersonMigrationFilter
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.personalrelationships.ProfileDetailsChangedEvent
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.personalrelationships.model.SyncPrisonerDomesticStatusResponse
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.personalrelationships.model.SyncPrisonerNumberOfChildrenResponse
import uk.gov.justice.hmpps.sqs.countAllMessagesOnQueue
import java.time.LocalDateTime
import kotlin.collections.component1
import kotlin.collections.component2

class ContactPersonProfileDetailsSyncIntTest(
  @Autowired private val nomisApi: ContactPersonProfileDetailsNomisApiMockServer,
  @Autowired private val dpsApi: ContactPersonProfileDetailsDpsApiMockServer,
  @Autowired private val service: ContactPersonProfileDetailsSyncService,
) : SqsIntegrationTestBase() {

  @Nested
  @DisplayName("OFFENDER_PHYSICAL_DETAILS-CHANGED for contact person details")
  inner class ProfileDetailsChanged {
    @Nested
    inner class HappyPath {
      @Test
      fun `should sync new profile details for domestic status`() = runTest {
        nomisApi.stubGetProfileDetails(
          offenderNo = "A1234AA",
          bookingId = 12345,
          profileTypes = listOf("MARITAL"),
          response = nomisResponse(
            offenderNo = "A1234AA",
            bookings = listOf(
              booking(
                bookingId = 12345,
                latestBooking = true,
                profileDetails = listOf(
                  profileDetails(
                    type = "MARITAL",
                    code = "M",
                    createDateTime = "2024-09-04T12:34:56",
                    createdBy = "A_USER",
                    modifiedDateTime = null,
                    modifiedBy = null,
                    auditModuleName = "NOMIS",
                  ),
                ),
              ),
            ),
          ),
        )
        dpsApi.stubSyncDomesticStatus(response = dpsDomesticStatusResponse(dpsId = 321))

        sendProfileDetailsChangedEvent(prisonerNumber = "A1234AA", bookingId = 12345, profileType = "MARITAL")
          .also { waitForAnyProcessingToComplete() }

        nomisApi.verify(offenderNo = "A1234AA", bookingId = 12345, profileType = "MARITAL")
        dpsApi.verify(
          dpsUpdates = mapOf(
            "domesticStatusCode" to equalTo("M"),
            "createdTime" to equalTo("2024-09-04T12:34:56"),
            "createdBy" to equalTo("A_USER"),
          ),
        )
        verifyTelemetry(
          telemetryType = "success",
          profileType = "domestic-status",
          offenderNo = "A1234AA",
          bookingId = 12345,
        )
      }

      @Test
      fun `should sync new profile details for number of children`() = runTest {
        nomisApi.stubGetProfileDetails(
          offenderNo = "A1234AA",
          bookingId = 12345,
          profileTypes = listOf("CHILD"),
          response = nomisResponse(
            offenderNo = "A1234AA",
            bookings = listOf(
              booking(
                bookingId = 12345,
                latestBooking = true,
                profileDetails = listOf(
                  profileDetails(
                    type = "CHILD",
                    code = "2",
                    createDateTime = "2024-09-04T12:34:56",
                    createdBy = "A_USER",
                    modifiedDateTime = null,
                    modifiedBy = null,
                    auditModuleName = "NOMIS",
                  ),
                ),
              ),
            ),
          ),
        )
        dpsApi.stubSyncNumberOfChildren(response = dpsNumberOfChildrenResponse())

        sendProfileDetailsChangedEvent(prisonerNumber = "A1234AA", bookingId = 12345, profileType = "CHILD")
          .also { waitForAnyProcessingToComplete() }

        nomisApi.verify(offenderNo = "A1234AA", bookingId = 12345, profileType = "CHILD")
        dpsApi.verify(
          profileType = "number-of-children",
          dpsUpdates = mapOf(
            "numberOfChildren" to equalTo("2"),
            "createdTime" to equalTo("2024-09-04T12:34:56"),
            "createdBy" to equalTo("A_USER"),
          ),
        )
        verifyTelemetry(
          telemetryType = "success",
          profileType = "number-of-children",
          offenderNo = "A1234AA",
          bookingId = 12345,
        )
      }

      @Test
      fun `should sync a null value`() = runTest {
        nomisApi.stubGetProfileDetails(
          offenderNo = "A1234AA",
          response = nomisResponse(
            bookings = listOf(
              booking(
                profileDetails = listOf(
                  profileDetails(
                    type = "MARITAL",
                    code = null,
                    modifiedDateTime = "2024-09-05T12:34:56",
                    modifiedBy = "ANOTHER_USER",
                  ),
                ),
              ),
            ),
          ),
        )
        dpsApi.stubSyncDomesticStatus(response = dpsDomesticStatusResponse())

        sendProfileDetailsChangedEvent(prisonerNumber = "A1234AA", bookingId = 12345, profileType = "MARITAL")
          .also { waitForAnyProcessingToComplete() }

        nomisApi.verify()
        dpsApi.verify(
          dpsUpdates = mapOf(
            "domesticStatusCode" to absent(),
            "createdTime" to equalTo("2024-09-05T12:34:56"),
            "createdBy" to equalTo("ANOTHER_USER"),
          ),
        )
        verifyTelemetry()
      }

      @Test
      fun `should ignore a null value that has just been created`() = runTest {
        nomisApi.stubGetProfileDetails(
          response = nomisResponse(
            bookings = listOf(
              booking(
                profileDetails = listOf(
                  profileDetails(
                    type = "MARITAL",
                    code = null,
                    modifiedDateTime = null,
                    modifiedBy = null,
                  ),
                ),
              ),
            ),
          ),
        )
        dpsApi.stubSyncDomesticStatus(response = dpsDomesticStatusResponse())

        sendProfileDetailsChangedEvent(profileType = "MARITAL")
          .also { waitForAnyProcessingToComplete() }

        nomisApi.verify()
        dpsApi.verify(type = "ignored")
        verifyTelemetry(telemetryType = "ignored", ignoreReason = "New profile details are empty")
      }

      @Test
      fun `should ignore an update created by the synchronisation service`() = runTest {
        nomisApi.stubGetProfileDetails(
          response = nomisResponse(
            bookings = listOf(
              booking(
                profileDetails = listOf(
                  profileDetails(
                    auditModuleName = "DPS_SYNCHRONISATION",
                  ),
                ),
              ),
            ),
          ),
        )
        dpsApi.stubSyncDomesticStatus(response = dpsDomesticStatusResponse())

        sendProfileDetailsChangedEvent()
          .also { waitForAnyProcessingToComplete() }

        nomisApi.verify()
        dpsApi.verify(type = "ignored")
        verifyTelemetry(telemetryType = "ignored", ignoreReason = "Profile details were created by DPS_SYNCHRONISATION")
      }

      @Test
      fun `should ignore a historical booking`() = runTest {
        nomisApi.stubGetProfileDetails(
          response = nomisResponse(
            bookings = listOf(
              booking(
                latestBooking = false,
                profileDetails = listOf(
                  profileDetails(
                    code = "M",
                  ),
                ),
              ),
            ),
          ),
        )
        dpsApi.stubSyncDomesticStatus(response = dpsDomesticStatusResponse())

        sendProfileDetailsChangedEvent()
          .also { waitForAnyProcessingToComplete() }

        nomisApi.verify()
        dpsApi.verify(type = "ignored")
        verifyTelemetry(telemetryType = "ignored", ignoreReason = "Ignoring historical bookingId 12345")
      }

      @Test
      fun `should only sync the profile type requested`() = runTest {
        nomisApi.stubGetProfileDetails(
          response = nomisResponse(
            bookings = listOf(
              booking(
                profileDetails = listOf(
                  profileDetails(
                    type = "MARITAL",
                    code = "M",
                  ),
                ),
              ),
            ),
          ),
        )
        dpsApi.stubSyncDomesticStatus(response = dpsDomesticStatusResponse())
        dpsApi.stubSyncNumberOfChildren(response = dpsNumberOfChildrenResponse())

        // The event received is for a domestic-status update, not number-of-children
        sendProfileDetailsChangedEvent(profileType = "MARITAL")
          .also { waitForAnyProcessingToComplete() }

        // We updated domestic status and created telemetry
        nomisApi.verify(profileType = "MARITAL")
        dpsApi.verify(profileType = "domestic-status", type = "updated")
        verifyTelemetry(profileType = "domestic-status", telemetryType = "success")

        nomisApi.verify()
        dpsApi.verify(profileType = "number-of-children", type = "not updated")
        verify(telemetryClient, times(0)).trackEvent(
          eq("contact-person-number-of-children-synchronisation-success"),
          any(),
          isNull(),
        )
      }

      @Test
      fun `should ignore an unsupported profile type`() = runTest {
        assertDoesNotThrow {
          service.profileDetailsChanged(ProfileDetailsChangedEvent(profileType = "UNKNOWN", offenderIdDisplay = "A1234AA", bookingId = 12345))
        }

        dpsApi.verify(profileType = "domestic-status", type = "ignored")
        dpsApi.verify(profileType = "number-of-children", type = "ignored")
      }
    }

    @Nested
    inner class Errors {
      @Test
      fun `should handle failed NOMIS API call`() = runTest {
        nomisApi.stubGetProfileDetails(status = NOT_FOUND)

        sendProfileDetailsChangedEvent()
          .also { waitForDlqMessage() }

        nomisApi.verify()
        dpsApi.verify(type = "ignored")
        verifyTelemetry(telemetryType = "error", errorReason = "404 Not Found from GET http://localhost:8081/prisoners/A1234AA/profile-details")
      }

      @Test
      fun `should handle failed DPS API call`() = runTest {
        nomisApi.stubGetProfileDetails("A1234AA", response = nomisResponse(offenderNo = "A1234AA"))
        dpsApi.stubSyncDomesticStatus(status = INTERNAL_SERVER_ERROR)

        sendProfileDetailsChangedEvent().also { waitForDlqMessage() }

        nomisApi.verify(getRequestedFor(urlPathEqualTo("/prisoners/A1234AA/profile-details")))
        dpsApi.verify()
        verifyTelemetry(telemetryType = "error", errorReason = "500 Internal Server Error from PUT http://localhost:8097/sync/A1234AA/domestic-status")
      }
    }

    @Nested
    @DisplayName("PUT /sync/contactperson/profile-detail/{prisonerNumber}/{bookingId}/{profileType}")
    inner class SyncEndpoint {
      @BeforeEach
      fun setUp() {
        nomisApi.stubGetProfileDetails(
          offenderNo = "A1234AA",
          bookingId = 12345,
          profileTypes = listOf("MARITAL"),
          response = nomisResponse(
            offenderNo = "A1234AA",
            bookings = listOf(
              booking(
                bookingId = 12345,
                latestBooking = true,
                profileDetails = listOf(
                  profileDetails(
                    type = "MARITAL",
                    code = "M",
                    createDateTime = "2024-09-04T12:34:56",
                    createdBy = "A_USER",
                    modifiedDateTime = null,
                    modifiedBy = null,
                    auditModuleName = "NOMIS",
                  ),
                ),
              ),
            ),
          ),
        )
        dpsApi.stubSyncDomesticStatus(response = dpsDomesticStatusResponse(dpsId = 321))
      }

      @Test
      fun `access forbidden when no role`() {
        webTestClient.put().uri("/sync/contactperson/profile-details/A1234AA/12345/MARITAL")
          .headers(setAuthorisation(roles = listOf()))
          .contentType(MediaType.APPLICATION_JSON)
          .bodyValue(ContactPersonMigrationFilter())
          .exchange()
          .expectStatus().isForbidden
      }

      @Test
      fun `access forbidden with wrong role`() {
        webTestClient.put().uri("/sync/contactperson/profile-details/A1234AA/12345/MARITAL")
          .headers(setAuthorisation(roles = listOf("BANANAS")))
          .contentType(MediaType.APPLICATION_JSON)
          .bodyValue(ContactPersonMigrationFilter())
          .exchange()
          .expectStatus().isForbidden
      }

      @Test
      fun `access unauthorised with no auth token`() {
        webTestClient.put().uri("/sync/contactperson/profile-details/A1234AA/12345/MARITAL")
          .contentType(MediaType.APPLICATION_JSON)
          .bodyValue(ContactPersonMigrationFilter())
          .exchange()
          .expectStatus().isUnauthorized
      }

      @Test
      fun `should sync profile details`() = runTest {
        webTestClient.put().uri("/sync/contactperson/profile-details/A1234AA/12345/MARITAL")
          .headers(setAuthorisation(roles = listOf("ROLE_MIGRATE_CONTACTPERSON")))
          .exchange()
          .expectStatus().isOk

        nomisApi.verify()
        dpsApi.verify()
        verifyTelemetry()
      }

      @Test
      fun `should handle errors from NOMIS`() = runTest {
        nomisApi.stubGetProfileDetails(status = NOT_FOUND)

        webTestClient.put().uri("/sync/contactperson/profile-details/A1234AA/12345/MARITAL")
          .headers(setAuthorisation(roles = listOf("ROLE_MIGRATE_CONTACTPERSON")))
          .exchange()
          .expectStatus().isBadRequest
          .expectBody()
          .jsonPath("userMessage").value<String> {
            assertThat(it).contains("404 Not Found from GET http://localhost:8081/prisoners/A1234AA/profile-details")
          }

        nomisApi.verify()
        dpsApi.verify(type = "ignored")
        verifyTelemetry(telemetryType = "error", errorReason = "404 Not Found from GET http://localhost:8081/prisoners/A1234AA/profile-details")
      }

      @Test
      fun `should handle errors from DPS`() = runTest {
        dpsApi.stubSyncDomesticStatus(status = INTERNAL_SERVER_ERROR)

        webTestClient.put().uri("/sync/contactperson/profile-details/A1234AA/12345/MARITAL")
          .headers(setAuthorisation(roles = listOf("ROLE_MIGRATE_CONTACTPERSON")))
          .exchange()
          .expectStatus().isBadRequest
          .expectBody()
          .jsonPath("userMessage").value<String> {
            assertThat(it).contains("500 Internal Server Error from PUT http://localhost:8097/sync/A1234AA/domestic-status")
          }

        nomisApi.verify(getRequestedFor(urlPathEqualTo("/prisoners/A1234AA/profile-details")))
        dpsApi.verify()
        verifyTelemetry(telemetryType = "error", errorReason = "500 Internal Server Error from PUT http://localhost:8097/sync/A1234AA/domestic-status")
      }

      @Test
      fun `should handle invalid profile type`() = runTest {
        webTestClient.put().uri("/sync/contactperson/profile-details/A1234AA/12345/BUILD")
          .headers(setAuthorisation(roles = listOf("ROLE_MIGRATE_CONTACTPERSON")))
          .exchange()
          .expectStatus().isBadRequest

        nomisApi.verify(count = 0, getRequestedFor(urlPathEqualTo("/prisoners/A1234AA/profile-details")))
        dpsApi.verify(type = "ignored")
        verify(telemetryClient, times(0)).trackEvent(anyString(), anyMap(), isNull())
      }
    }

    private fun dpsDomesticStatusResponse(dpsId: Long = 321) = SyncPrisonerDomesticStatusResponse(
      id = dpsId,
      active = true,
      domesticStatusCode = "M",
      createdTime = LocalDateTime.now(),
      createdBy = "A_USER",
    )
    private fun dpsNumberOfChildrenResponse(id: Long = 321) = SyncPrisonerNumberOfChildrenResponse(id)

    private fun ContactPersonProfileDetailsNomisApiMockServer.verify(
      offenderNo: String = "A1234AA",
      bookingId: Long = 12345,
      profileType: String = "MARITAL",
    ) {
      verify(
        getRequestedFor(urlPathMatching("/prisoners/$offenderNo/profile-details"))
          .withQueryParam("profileTypes", equalTo(profileType))
          .withQueryParam("bookingId", equalTo("$bookingId")),
      )
    }

    private fun ContactPersonProfileDetailsDpsApiMockServer.verify(
      type: String = "updated",
      profileType: String = "domestic-status",
      dpsUpdates: Map<String, StringValuePattern> = mapOf(),
    ) {
      // For updates verify we sent the correct details to the DPS API
      if (type == "updated") {
        verify(
          putRequestedFor(urlPathEqualTo("/sync/A1234AA/$profileType"))
            .apply {
              dpsUpdates.forEach { (jsonPath, pattern) ->
                withRequestBody(matchingJsonPath(jsonPath, pattern))
              }
            },
        )
      } else {
        // If not updated we shouldn't call the DPS API
        verify(0, putRequestedFor(urlPathEqualTo("/sync/A1234AA/$profileType")))
      }
    }
  }

  private fun nomisResponse(
    offenderNo: String = "A1234AA",
    bookings: List<BookingProfileDetailsResponse> = listOf(booking()),
  ) = PrisonerProfileDetailsResponse(
    offenderNo = offenderNo,
    bookings = bookings,
  )

  private fun booking(
    bookingId: Long = 12345,
    startDateTime: String = "2024-09-03T12:34:56",
    latestBooking: Boolean = true,
    profileDetails: List<ProfileDetailsResponse> = listOf(profileDetails()),
  ) = BookingProfileDetailsResponse(
    bookingId = bookingId,
    startDateTime = startDateTime,
    latestBooking = latestBooking,
    profileDetails = profileDetails,
  )

  private fun profileDetails(
    type: String = "MARITAL",
    code: String? = "M",
    createDateTime: String = "2024-09-04T12:34:56",
    createdBy: String = "A_USER",
    modifiedDateTime: String? = null,
    modifiedBy: String? = null,
    auditModuleName: String = "NOMIS",
  ) = ProfileDetailsResponse(
    type = type,
    code = code,
    createDateTime = createDateTime,
    createdBy = createdBy,
    modifiedDateTime = modifiedDateTime,
    modifiedBy = modifiedBy,
    auditModuleName = auditModuleName,
  )

  private fun verifyTelemetry(
    profileType: String = "domestic-status",
    telemetryType: String = "success",
    offenderNo: String = "A1234AA",
    bookingId: Long = 12345,
    ignoreReason: String? = null,
    errorReason: String? = null,
  ) = verify(telemetryClient).trackEvent(
    eq("contact-person-$profileType-synchronisation-$telemetryType"),
    check {
      assertThat(it["offenderNo"]).isEqualTo(offenderNo)
      assertThat(it["bookingId"]).isEqualTo("$bookingId")
      ignoreReason?.run { assertThat(it["reason"]).isEqualTo(this) }
      errorReason?.run { assertThat(it["error"]).isEqualTo(this) }
      if (ignoreReason == null && errorReason == null) {
        assertThat(it["dpsId"]).isEqualTo("321")
      }
    },
    isNull(),
  )

  private fun profileDetailsChangedEvent(
    prisonerNumber: String = "A1234AA",
    bookingId: Int = 1,
    profileType: String,
  ) = """
   {
      "Type" : "Notification",
      "MessageId" : "298a61d6-e078-51f2-9c60-3f348f8bde68",
      "TopicArn" : "arn:aws:sns:eu-west-2:754256621582:cloud-platform-Digital-Prison-Services-f221e27fcfcf78f6ab4f4c3cc165eee7",
      "Message" : "{\"eventType\":\"OFFENDER_PHYSICAL_DETAILS-CHANGED\",\"eventDatetime\":\"2024-06-11T16:30:59\",\"offenderIdDisplay\":\"$prisonerNumber\",\"bookingId\":$bookingId,\"profileType\":\"$profileType\"}",
      "Timestamp" : "2024-06-11T15:30:59.048Z",
      "SignatureVersion" : "1",
      "Signature" : "kyuV8tDWmRoixtnyXauR/mzBdkO4yWXEFLZU6256JRIRfcGBNdn7+TPcRnM7afa6N6DwUs3TDKQ17U7W8hkB86r/J1PsfEpF8qOr8bZd4J/RDNAHJxmNnuTzy351ISDYjdxccREF57pLXtaMcu0Z6nJTTnv9pn7qOVasuxUIGANaD214P6iXkWvsFj0AgR1TVITHW5jMFTE+ln2PTLQ9N6dwx4/foIlFsQu7rWnx3hy9+x7gtInnDIaQSvI2gHQQI51TpQrES0YKjn5Tb25ANS8bZooK7knt9F+Hv3bejDyXWgR3fyC4SJbUvbVhfVI/aRhOv/qLwFGSOFKt6I0KAA==",
      "SigningCertURL" : "https://sns.eu-west-2.amazonaws.com/SimpleNotificationService-60eadc530605d63b8e62a523676ef735.pem",
      "UnsubscribeURL" : "https://sns.eu-west-2.amazonaws.com/?Action=Unsubscribe&SubscriptionArn=arn:aws:sns:eu-west-2:754256621582:cloud-platform-Digital-Prison-Services-f221e27fcfcf78f6ab4f4c3cc165eee7:e5c3f313-ccda-4a2f-9667-e2519fd01a19",
      "MessageAttributes" : {
        "publishedAt" : {"Type":"String","Value":"2024-06-11T16:30:59.023769832+01:00"},
        "eventType" : {"Type":"String","Value":"OFFENDER_PHYSICAL_DETAILS-CHANGED"}
      }
   }
  """.trimIndent()

  private fun sendProfileDetailsChangedEvent(
    prisonerNumber: String = "A1234AA",
    bookingId: Int = 12345,
    profileType: String = "MARITAL",
  ) = awsSqsPersonalRelationshipsOffenderEventsClient.sendMessage(
    personalRelationshipsQueueOffenderEventsUrl,
    profileDetailsChangedEvent(prisonerNumber, bookingId, profileType),
  )

  private fun waitForDlqMessage() = await untilAsserted {
    assertThat(
      awsSqsPersonalRelationshipsOffenderEventsDlqClient.countAllMessagesOnQueue(personalRelationshipsQueueOffenderEventsDlqUrl).get(),
    ).isEqualTo(1)
  }
}
