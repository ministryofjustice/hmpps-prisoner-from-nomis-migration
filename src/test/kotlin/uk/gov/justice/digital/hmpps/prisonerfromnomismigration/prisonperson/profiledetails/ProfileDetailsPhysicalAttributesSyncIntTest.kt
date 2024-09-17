package uk.gov.justice.digital.hmpps.prisonerfromnomismigration.prisonperson.profiledetails

import com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor
import com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo
import kotlinx.coroutines.test.runTest
import org.assertj.core.api.Assertions.assertThat
import org.awaitility.kotlin.await
import org.awaitility.kotlin.untilAsserted
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.check
import org.mockito.kotlin.doThrow
import org.mockito.kotlin.eq
import org.mockito.kotlin.isNull
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.mock.mockito.SpyBean
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpStatus.NOT_FOUND
import org.springframework.web.reactive.function.client.WebClientResponseException
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.integration.SqsIntegrationTestBase
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.integration.sendMessage
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomissync.model.BookingProfileDetailsResponse
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomissync.model.PrisonerProfileDetailsResponse
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomissync.model.ProfileDetailsResponse
import uk.gov.justice.hmpps.sqs.countAllMessagesOnQueue
import java.time.LocalDateTime

class ProfileDetailsPhysicalAttributesSyncIntTest : SqsIntegrationTestBase() {

  @Autowired
  private lateinit var nomisApi: ProfileDetailsNomisApiMockServer

  @SpyBean
  private lateinit var dpsApi: ProfileDetailPhysicalAttributesDpsApiService

  @Nested
  @DisplayName("OFFENDER_PHYSICAL_DETAILS-CHANGED for physical attributes")
  inner class PhysicalAttributesChanged {
    @Nested
    inner class HappyPath {
      @Test
      fun `should sync new profile detail physical attributes`() = runTest {
        nomisApi.stubGetProfileDetails(
          "A1234AA",
          response(
            offenderNo = "A1234AA",
            bookings = listOf(
              booking(
                bookingId = 12345,
                startDateTime = "2024-09-03T12:34:56",
                endDateTime = null,
                latestBooking = true,
                profileDetails = listOf(
                  profileDetails(
                    type = "SHOESIZE",
                    code = "8.5",
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

        sendProfileDetailsChangedEvent(prisonerNumber = "A1234AA", bookingId = 12345, profileType = "SHOESIZE")

        nomisApi.verify(getRequestedFor(urlPathEqualTo("/prisoners/A1234AA/profile-details")))
        verify(dpsApi).syncProfileDetailsPhysicalAttributes(
          check {
            assertThat(it.prisonerNumber).isEqualTo("A1234AA")
            assertThat(it.profileType).isEqualTo("SHOESIZE")
            assertThat(it.profileCode).isEqualTo("8.5")
            assertThat(it.appliesFrom).isEqualTo(LocalDateTime.parse("2024-09-03T12:34:56"))
            assertThat(it.appliesTo).isNull()
            assertThat(it.latestBooking).isTrue
            assertThat(it.createdAt).isEqualTo(LocalDateTime.parse("2024-09-04T12:34:56"))
            assertThat(it.createdBy).isEqualTo("A_USER")
          },
        )
        verifyTelemetry(
          telemetryType = "updated",
          offenderNo = "A1234AA",
          bookingId = 12345,
          profileType = "SHOESIZE",
        )
      }

      @Test
      fun `should sync a null value`() = runTest {
        nomisApi.stubGetProfileDetails(
          "A1234AA",
          response(
            bookings = listOf(
              booking(
                profileDetails = listOf(
                  profileDetails(
                    type = "SHOESIZE",
                    code = null,
                    modifiedDateTime = "2024-09-05T12:34:56",
                    modifiedBy = "ANOTHER_USER",
                  ),
                ),
              ),
            ),
          ),
        )

        sendProfileDetailsChangedEvent(prisonerNumber = "A1234AA", bookingId = 12345, profileType = "SHOESIZE")

        nomisApi.verify(getRequestedFor(urlPathEqualTo("/prisoners/A1234AA/profile-details")))
        verify(dpsApi).syncProfileDetailsPhysicalAttributes(
          check {
            assertThat(it.profileType).isEqualTo("SHOESIZE")
            assertThat(it.profileCode).isEqualTo(null)
            assertThat(it.createdAt).isEqualTo(LocalDateTime.parse("2024-09-05T12:34:56"))
            assertThat(it.createdBy).isEqualTo("ANOTHER_USER")
          },
        )
        verifyTelemetry()
      }

      @Test
      fun `should ignore a null value that has just been created`() = runTest {
        nomisApi.stubGetProfileDetails(
          "A1234AA",
          response(
            bookings = listOf(
              booking(
                profileDetails = listOf(
                  profileDetails(
                    type = "SHOESIZE",
                    code = null,
                    modifiedDateTime = null,
                    modifiedBy = null,
                  ),
                ),
              ),
            ),
          ),
        )

        sendProfileDetailsChangedEvent(prisonerNumber = "A1234AA", bookingId = 12345, profileType = "SHOESIZE")

        nomisApi.verify(getRequestedFor(urlPathEqualTo("/prisoners/A1234AA/profile-details")))
        verify(dpsApi, never()).syncProfileDetailsPhysicalAttributes(any())
        verifyTelemetry(telemetryType = "ignored", ignoreReason = "New profile details are empty")
      }

      @Test
      fun `should ignore an update created by the synchronisation service`() = runTest {
        nomisApi.stubGetProfileDetails(
          "A1234AA",
          response(
            bookings = listOf(
              booking(
                profileDetails = listOf(
                  profileDetails(auditModuleName = "DPS_SYNCHRONISATION"),
                ),
              ),
            ),
          ),
        )

        sendProfileDetailsChangedEvent(prisonerNumber = "A1234AA", bookingId = 12345, profileType = "SHOESIZE")

        nomisApi.verify(getRequestedFor(urlPathEqualTo("/prisoners/A1234AA/profile-details")))
        verify(dpsApi, never()).syncProfileDetailsPhysicalAttributes(any())
        verifyTelemetry(
          telemetryType = "ignored",
          ignoreReason = "Profile details were created by DPS_SYNCHRONISATION",
        )
      }

      @Test
      fun `should sync a historical booking`() = runTest {
        nomisApi.stubGetProfileDetails(
          "A1234AA",
          response(
            bookings = listOf(
              booking(
                bookingId = 1,
                startDateTime = "2024-09-02T12:34:56",
                latestBooking = true,
                profileDetails = listOf(
                  profileDetails(
                    type = "SHOESIZE",
                    code = "8.5",
                  ),
                ),
              ),
              booking(
                bookingId = 12345,
                startDateTime = "2024-09-02T12:34:56",
                endDateTime = "2024-09-03T12:34:56",
                latestBooking = false,
                profileDetails = listOf(
                  profileDetails(
                    type = "SHOESIZE",
                    code = "9.5",
                  ),
                ),
              ),
            ),
          ),
        )

        sendProfileDetailsChangedEvent(prisonerNumber = "A1234AA", bookingId = 12345, profileType = "SHOESIZE")

        nomisApi.verify(getRequestedFor(urlPathEqualTo("/prisoners/A1234AA/profile-details")))
        verify(dpsApi).syncProfileDetailsPhysicalAttributes(
          check {
            assertThat(it.profileType).isEqualTo("SHOESIZE")
            assertThat(it.profileCode).isEqualTo("9.5")
            assertThat(it.appliesFrom).isEqualTo(LocalDateTime.parse("2024-09-02T12:34:56"))
            assertThat(it.appliesTo).isEqualTo(LocalDateTime.parse("2024-09-03T12:34:56"))
            assertThat(it.latestBooking).isFalse()
          },
        )
        verifyTelemetry()
      }

      @Test
      fun `should sync a historical booking which is the latest`() = runTest {
        nomisApi.stubGetProfileDetails(
          "A1234AA",
          response(
            bookings = listOf(
              booking(
                bookingId = 12345,
                startDateTime = "2024-09-02T12:34:56",
                endDateTime = "2024-09-03T12:34:56",
                latestBooking = true,
                profileDetails = listOf(
                  profileDetails(
                    type = "SHOESIZE",
                    code = "8.5",
                  ),
                ),
              ),
            ),
          ),
        )

        sendProfileDetailsChangedEvent(prisonerNumber = "A1234AA", bookingId = 12345, profileType = "SHOESIZE")

        nomisApi.verify(getRequestedFor(urlPathEqualTo("/prisoners/A1234AA/profile-details")))
        verify(dpsApi).syncProfileDetailsPhysicalAttributes(
          check {
            assertThat(it.profileType).isEqualTo("SHOESIZE")
            assertThat(it.profileCode).isEqualTo("8.5")
            assertThat(it.appliesFrom).isEqualTo(LocalDateTime.parse("2024-09-02T12:34:56"))
            assertThat(it.appliesTo).isEqualTo(LocalDateTime.parse("2024-09-03T12:34:56"))
            assertThat(it.latestBooking).isTrue()
          },
        )
        verifyTelemetry()
      }
    }

    @Nested
    inner class Errors {
      @Test
      fun `should put message on DLQ if call to NOMIS fails`() = runTest {
        nomisApi.stubGetProfileDetails(NOT_FOUND)

        sendProfileDetailsChangedEvent(prisonerNumber = "A1234AA", bookingId = 12345, profileType = "SHOESIZE")
          .also { waitForDlqMessage() }

        nomisApi.verify(getRequestedFor(urlPathEqualTo("/prisoners/A1234AA/profile-details")))
        verify(dpsApi, never()).syncProfileDetailsPhysicalAttributes(any())
        verifyTelemetry(telemetryType = "error", errorReason = "404 Not Found from GET http://localhost:8081/prisoners/A1234AA/profile-details")
      }

      @Test
      fun `should put message on DLQ if call to DPS fails`() = runTest {
        nomisApi.stubGetProfileDetails("A1234AA", response())
        // TODO SDIT-2019 Change this to use Wiremock when the DPS API is available
        doThrow(WebClientResponseException.create(500, "Internal Server Error", HttpHeaders.EMPTY, ByteArray(0), null))
          .whenever(dpsApi).syncProfileDetailsPhysicalAttributes(any())

        sendProfileDetailsChangedEvent(prisonerNumber = "A1234AA", bookingId = 12345, profileType = "SHOESIZE")
          .also { waitForDlqMessage() }

        nomisApi.verify(getRequestedFor(urlPathEqualTo("/prisoners/A1234AA/profile-details")))
        verify(dpsApi).syncProfileDetailsPhysicalAttributes(any())
        verifyTelemetry(telemetryType = "error", errorReason = "500 Internal Server Error")
      }

      @Test
      fun `should put message on DLQ if booking doesn't exist`() = runTest {
        nomisApi.stubGetProfileDetails(
          "A1234AA",
          response(
            bookings = listOf(
              booking(bookingId = 1),
            ),
          ),
        )

        sendProfileDetailsChangedEvent(prisonerNumber = "A1234AA", bookingId = 12345, profileType = "SHOESIZE")
          .also { waitForDlqMessage() }

        nomisApi.verify(getRequestedFor(urlPathEqualTo("/prisoners/A1234AA/profile-details")))
        verify(dpsApi, never()).syncProfileDetailsPhysicalAttributes(any())
        verifyTelemetry(telemetryType = "error", errorReason = "Booking with requested bookingId not found")
      }

      @Test
      fun `should put message on DLQ if booking doesn't have requested profile type`() = runTest {
        nomisApi.stubGetProfileDetails(
          "A1234AA",
          response(
            bookings = listOf(
              booking(
                profileDetails = listOf(
                  profileDetails(
                    type = "SHOESIZE",
                    code = "8.5",
                  ),
                ),
              ),
            ),
          ),
        )

        sendProfileDetailsChangedEvent(prisonerNumber = "A1234AA", bookingId = 12345, profileType = "BUILD")
          .also { waitForDlqMessage() }

        nomisApi.verify(getRequestedFor(urlPathEqualTo("/prisoners/A1234AA/profile-details")))
        verify(dpsApi, never()).syncProfileDetailsPhysicalAttributes(any())
        verifyTelemetry(
          telemetryType = "error",
          profileType = "BUILD",
          errorReason = "Profile details for requested profileType not found",
        )
      }
    }

    @Nested
    inner class Events {
      @Test
      fun `should sync profile types we are interested in`() = runTest {
        nomisApi.stubGetProfileDetails("A1234AA", response())

        sendProfileDetailsChangedEvent(prisonerNumber = "A1234AA", bookingId = 12345, profileType = "SHOESIZE")

        nomisApi.verify(getRequestedFor(urlPathEqualTo("/prisoners/A1234AA/profile-details")))
        verify(dpsApi).syncProfileDetailsPhysicalAttributes(any())
        verifyTelemetry()
      }

      @Test
      fun `should do nothing for profile types we're not interested in`() = runTest {
        sendProfileDetailsChangedEvent(prisonerNumber = "A1234AA", bookingId = 12345, profileType = "RELF")

        nomisApi.verify(0, getRequestedFor(urlPathEqualTo("/prisoners/A1234AA/profile-details")))
        verify(dpsApi, never()).syncProfileDetailsPhysicalAttributes(any())
        verify(telemetryClient).trackEvent(
          eq("profile-details-synchronisation-ignored"),
          check {
            assertThat(it["offenderNo"]).isEqualTo("A1234AA")
            assertThat(it["bookingId"]).isEqualTo("12345")
            assertThat(it["profileType"]).isEqualTo("RELF")
            assertThat(it["reason"]).isEqualTo("Profile type not supported")
          },
          isNull(),
        )
      }
    }
  }

  private fun profileDetailsChangedEvent(
    prisonerNumber: String = "A1234AA",
    bookingId: Int = 1,
    profileType: String = "HAIR",
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
    bookingId: Int = 1,
    profileType: String = "HAIR",
  ) =
    awsSqsPrisonPersonOffenderEventsClient.sendMessage(
      prisonPersonQueueOffenderEventsUrl,
      profileDetailsChangedEvent(prisonerNumber, bookingId, profileType),
    ).also { waitForAnyProcessingToComplete() }

  private fun waitForDlqMessage() =
    await untilAsserted {
      assertThat(
        awsSqsPrisonPersonOffenderEventDlqClient.countAllMessagesOnQueue(prisonPersonQueueOffenderEventsDlqUrl).get(),
      ).isEqualTo(1)
    }
  private fun profileDetails(
    type: String = "SHOESIZE",
    code: String? = "8.5",
    createDateTime: String = "2024-09-04T12:34:56",
    createdBy: String = "A_USER",
    modifiedDateTime: String? = null,
    modifiedBy: String? = null,
    auditModuleName: String = "NOMIS",
  ) =
    ProfileDetailsResponse(
      type = type,
      code = code,
      createDateTime = createDateTime,
      createdBy = createdBy,
      modifiedDateTime = modifiedDateTime,
      modifiedBy = modifiedBy,
      auditModuleName = auditModuleName,
    )

  private fun booking(
    bookingId: Long = 12345,
    startDateTime: String = "2024-09-03T12:34:56",
    endDateTime: String? = null,
    latestBooking: Boolean = true,
    profileDetails: List<ProfileDetailsResponse> = listOf(profileDetails()),
  ) = BookingProfileDetailsResponse(
    bookingId = bookingId,
    startDateTime = startDateTime,
    endDateTime = endDateTime,
    latestBooking = latestBooking,
    profileDetails = profileDetails,
  )

  private fun response(
    offenderNo: String = "A1234AA",
    bookings: List<BookingProfileDetailsResponse> = listOf(booking()),
  ) = PrisonerProfileDetailsResponse(
    offenderNo = offenderNo,
    bookings = bookings,
  )

  private fun verifyTelemetry(
    offenderNo: String = "A1234AA",
    bookingId: Long = 12345,
    profileType: String = "SHOESIZE",
    telemetryType: String = "updated",
    ignoreReason: String? = null,
    errorReason: String? = null,
  ) =
    verify(telemetryClient).trackEvent(
      eq("profile-details-physical-attributes-synchronisation-$telemetryType"),
      check {
        assertThat(it["offenderNo"]).isEqualTo(offenderNo)
        assertThat(it["bookingId"]).isEqualTo("$bookingId")
        assertThat(it["profileType"]).isEqualTo(profileType)
        ignoreReason?.run { assertThat(it["reason"]).isEqualTo(this) }
        errorReason?.run { assertThat(it["error"]).isEqualTo(this) }
      },
      isNull(),
    )
}
