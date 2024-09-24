package uk.gov.justice.digital.hmpps.prisonerfromnomismigration.prisonperson.profiledetails

import com.github.tomakehurst.wiremock.client.WireMock.absent
import com.github.tomakehurst.wiremock.client.WireMock.equalTo
import com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor
import com.github.tomakehurst.wiremock.client.WireMock.matchingJsonPath
import com.github.tomakehurst.wiremock.client.WireMock.putRequestedFor
import com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo
import com.github.tomakehurst.wiremock.matching.StringValuePattern
import kotlinx.coroutines.test.runTest
import org.assertj.core.api.Assertions.assertThat
import org.awaitility.kotlin.await
import org.awaitility.kotlin.untilAsserted
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.mockito.kotlin.check
import org.mockito.kotlin.eq
import org.mockito.kotlin.isNull
import org.mockito.kotlin.verify
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.http.HttpStatus.INTERNAL_SERVER_ERROR
import org.springframework.http.HttpStatus.NOT_FOUND
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.integration.SqsIntegrationTestBase
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.integration.sendMessage
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomissync.model.BookingProfileDetailsResponse
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomissync.model.PrisonerProfileDetailsResponse
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomissync.model.ProfileDetailsResponse
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.prisonperson.model.ProfileDetailsPhysicalAttributesSyncResponse
import uk.gov.justice.hmpps.sqs.countAllMessagesOnQueue

class ProfileDetailsPhysicalAttributesSyncIntTest : SqsIntegrationTestBase() {

  @Autowired
  private lateinit var nomisApi: ProfileDetailsNomisApiMockServer

  @Autowired
  private lateinit var dpsApi: ProfileDetailsPhysicalAttributesDpsApiMockServer

  @Nested
  @DisplayName("OFFENDER_PHYSICAL_DETAILS-CHANGED for physical attributes")
  inner class PhysicalAttributesChanged {
    @Nested
    inner class HappyPath {
      @Test
      fun `should sync new profile detail physical attributes`() = runTest {
        nomisApi.stubGetProfileDetails(
          "A1234AA",
          nomisResponse(
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
        dpsApi.stubSyncProfileDetailsPhysicalAttributes(dpsResponse())

        sendProfileDetailsChangedEvent(prisonerNumber = "A1234AA", bookingId = 12345, profileType = "SHOESIZE")

        nomisApi.verify(getRequestedFor(urlPathEqualTo("/prisoners/A1234AA/profile-details")))
        dpsApi.verify(
          dpsUpdates = mapOf(
            "shoeSize.value" to equalTo("8.5"),
            "appliesFrom" to equalTo("2024-09-03T12:34:56+01:00[Europe/London]"),
            "appliesTo" to absent(),
            "latestBooking" to equalTo("true"),
            "shoeSize.lastModifiedAt" to equalTo("2024-09-04T12:34:56+01:00[Europe/London]"),
            "shoeSize.lastModifiedBy" to equalTo("A_USER"),
            "hair" to absent(),
            "facialHair" to absent(),
            "face" to absent(),
            "build" to absent(),
            "leftEyeColour" to absent(),
            "leftRightColour" to absent(),
          ),
        )
        verifyTelemetry(
          telemetryType = "updated",
          offenderNo = "A1234AA",
          bookingId = 12345,
          requestedProfileType = "SHOESIZE",
        )
      }

      @Test
      fun `should sync multiple profile types regardless of which is requested`() = runTest {
        nomisApi.stubGetProfileDetails(
          "A1234AA",
          nomisResponse(
            bookings = listOf(
              booking(
                profileDetails = listOf(
                  profileDetails(
                    type = "SHOESIZE",
                    code = "8.5",
                    modifiedDateTime = "2024-09-05T12:34:56",
                    modifiedBy = "A_USER",
                  ),
                  profileDetails(
                    type = "BUILD",
                    code = "MEDIUM",
                    modifiedDateTime = "2024-09-05T11:34:56",
                    modifiedBy = "ANOTHER_USER",
                  ),
                ),
              ),
            ),
          ),
        )
        dpsApi.stubSyncProfileDetailsPhysicalAttributes(dpsResponse())

        sendProfileDetailsChangedEvent(prisonerNumber = "A1234AA", bookingId = 12345, profileType = "SHOESIZE")

        nomisApi.verify(getRequestedFor(urlPathEqualTo("/prisoners/A1234AA/profile-details")))
        dpsApi.verify(
          dpsUpdates = mapOf(
            "shoeSize.value" to equalTo("8.5"),
            "shoeSize.lastModifiedAt" to equalTo("2024-09-05T12:34:56+01:00[Europe/London]"),
            "shoeSize.lastModifiedBy" to equalTo("A_USER"),
            "build.value" to equalTo("MEDIUM"),
            "build.lastModifiedAt" to equalTo("2024-09-05T11:34:56+01:00[Europe/London]"),
            "build.lastModifiedBy" to equalTo("ANOTHER_USER"),
            "hair" to absent(),
          ),
        )
        verifyTelemetry(
          telemetryType = "updated",
          offenderNo = "A1234AA",
          bookingId = 12345,
          requestedProfileType = "SHOESIZE",
        )
      }

      @Test
      fun `should sync a null value`() = runTest {
        nomisApi.stubGetProfileDetails(
          "A1234AA",
          nomisResponse(
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
        dpsApi.stubSyncProfileDetailsPhysicalAttributes(dpsResponse())

        sendProfileDetailsChangedEvent(prisonerNumber = "A1234AA", bookingId = 12345, profileType = "SHOESIZE")

        nomisApi.verify(getRequestedFor(urlPathEqualTo("/prisoners/A1234AA/profile-details")))
        dpsApi.verify(
          dpsUpdates = mapOf(
            "shoeSize.value" to absent(),
            "shoeSize.lastModifiedAt" to equalTo("2024-09-05T12:34:56+01:00[Europe/London]"),
            "shoeSize.lastModifiedBy" to equalTo("ANOTHER_USER"),
          ),
        )
        verifyTelemetry()
      }

      @Test
      fun `should ignore a null value that has just been created`() = runTest {
        nomisApi.stubGetProfileDetails(
          "A1234AA",
          nomisResponse(
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
        dpsApi.verify(type = "ignored")
        verifyTelemetry(telemetryType = "ignored", ignoreReason = "New profile details are empty")
      }

      @Test
      fun `should ignore an update created by the synchronisation service`() = runTest {
        nomisApi.stubGetProfileDetails(
          "A1234AA",
          nomisResponse(
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
        dpsApi.verify(type = "ignored")
        verifyTelemetry(
          telemetryType = "ignored",
          ignoreReason = "Profile details were created by DPS_SYNCHRONISATION",
        )
      }

      @Test
      fun `should sync a historical booking`() = runTest {
        nomisApi.stubGetProfileDetails(
          "A1234AA",
          nomisResponse(
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
        dpsApi.stubSyncProfileDetailsPhysicalAttributes(dpsResponse())

        sendProfileDetailsChangedEvent(prisonerNumber = "A1234AA", bookingId = 12345, profileType = "SHOESIZE")

        nomisApi.verify(getRequestedFor(urlPathEqualTo("/prisoners/A1234AA/profile-details")))
        dpsApi.verify(
          dpsUpdates = mapOf(
            "shoeSize.value" to equalTo("9.5"),
            "appliesFrom" to equalTo("2024-09-02T12:34:56+01:00[Europe/London]"),
            "appliesTo" to equalTo("2024-09-03T12:34:56+01:00[Europe/London]"),
            "latestBooking" to equalTo("false"),
          ),
        )
        verifyTelemetry()
      }

      @Test
      fun `should sync a historical booking which is the latest`() = runTest {
        nomisApi.stubGetProfileDetails(
          "A1234AA",
          nomisResponse(
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
        dpsApi.stubSyncProfileDetailsPhysicalAttributes(dpsResponse())

        sendProfileDetailsChangedEvent(prisonerNumber = "A1234AA", bookingId = 12345, profileType = "SHOESIZE")

        nomisApi.verify(getRequestedFor(urlPathEqualTo("/prisoners/A1234AA/profile-details")))
        dpsApi.verify(
          dpsUpdates = mapOf(
            "shoeSize.value" to equalTo("8.5"),
            "appliesFrom" to equalTo("2024-09-02T12:34:56+01:00[Europe/London]"),
            "appliesTo" to equalTo("2024-09-03T12:34:56+01:00[Europe/London]"),
            "latestBooking" to equalTo("true"),
          ),
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
        dpsApi.verify(type = "ignored")
        verifyTelemetry(telemetryType = "error", errorReason = "404 Not Found from GET http://localhost:8081/prisoners/A1234AA/profile-details")
      }

      @Test
      fun `should put message on DLQ if call to DPS fails`() = runTest {
        nomisApi.stubGetProfileDetails("A1234AA", nomisResponse())
        dpsApi.stubSyncProfileDetailsPhysicalAttributes(INTERNAL_SERVER_ERROR)

        sendProfileDetailsChangedEvent(prisonerNumber = "A1234AA", bookingId = 12345, profileType = "SHOESIZE")
          .also { waitForDlqMessage() }

        nomisApi.verify(getRequestedFor(urlPathEqualTo("/prisoners/A1234AA/profile-details")))
        dpsApi.verify()
        verifyTelemetry(telemetryType = "error", errorReason = "500 Internal Server Error from PUT http://localhost:8095/sync/prisoners/A1234AA/profile-details-physical-attributes")
      }

      @Test
      fun `should put message on DLQ if booking doesn't exist`() = runTest {
        nomisApi.stubGetProfileDetails(
          "A1234AA",
          nomisResponse(
            bookings = listOf(
              booking(bookingId = 1),
            ),
          ),
        )

        sendProfileDetailsChangedEvent(prisonerNumber = "A1234AA", bookingId = 12345, profileType = "SHOESIZE")
          .also { waitForDlqMessage() }

        nomisApi.verify(getRequestedFor(urlPathEqualTo("/prisoners/A1234AA/profile-details")))
        dpsApi.verify(type = "ignored")
        verifyTelemetry(telemetryType = "error", errorReason = "Booking with requested bookingId not found")
      }

      @Test
      fun `should put message on DLQ if booking doesn't have requested profile type`() = runTest {
        nomisApi.stubGetProfileDetails(
          "A1234AA",
          nomisResponse(
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
        dpsApi.verify(type = "ignored")
        verifyTelemetry(
          telemetryType = "error",
          requestedProfileType = "BUILD",
          errorReason = "Profile details for requested profileType not found",
        )
      }
    }

    @Nested
    inner class Events {
      @Test
      fun `should sync profile types we are interested in`() = runTest {
        nomisApi.stubGetProfileDetails("A1234AA", nomisResponse())
        dpsApi.stubSyncProfileDetailsPhysicalAttributes(dpsResponse())

        sendProfileDetailsChangedEvent(prisonerNumber = "A1234AA", bookingId = 12345, profileType = "SHOESIZE")

        nomisApi.verify(getRequestedFor(urlPathEqualTo("/prisoners/A1234AA/profile-details")))
        dpsApi.verify()
        verifyTelemetry()
      }

      @Test
      fun `should do nothing for profile types we're not interested in`() = runTest {
        sendProfileDetailsChangedEvent(prisonerNumber = "A1234AA", bookingId = 12345, profileType = "RELF")

        nomisApi.verify(0, getRequestedFor(urlPathEqualTo("/prisoners/A1234AA/profile-details")))
        dpsApi.verify(type = "ignored")
        verify(telemetryClient).trackEvent(
          eq("profile-details-synchronisation-ignored"),
          check {
            assertThat(it["offenderNo"]).isEqualTo("A1234AA")
            assertThat(it["bookingId"]).isEqualTo("12345")
            assertThat(it["requestedProfileType"]).isEqualTo("RELF")
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

  private fun nomisResponse(
    offenderNo: String = "A1234AA",
    bookings: List<BookingProfileDetailsResponse> = listOf(booking()),
  ) = PrisonerProfileDetailsResponse(
    offenderNo = offenderNo,
    bookings = bookings,
  )

  private fun dpsResponse(ids: List<Long> = listOf(321)) = ProfileDetailsPhysicalAttributesSyncResponse(ids)

  private fun ProfileDetailsPhysicalAttributesDpsApiMockServer.verify(
    type: String = "updated",
    dpsUpdates: Map<String, StringValuePattern> = mapOf(),
  ) {
    // For updates verify we sent the correct details to the DPS API
    if (type == "updated") {
      verify(
        putRequestedFor(urlPathEqualTo("/sync/prisoners/A1234AA/profile-details-physical-attributes"))
          .apply {
            dpsUpdates.forEach { (jsonPath, pattern) ->
              withRequestBody(matchingJsonPath(jsonPath, pattern))
            }
          },
      )
    } else {
      // If not updated we shouldn't call the DPS API
      verify(0, putRequestedFor(urlPathEqualTo("/sync/prisoners/A1234AA/profile-details-physical-attributes")))
    }
  }

  private fun verifyTelemetry(
    offenderNo: String = "A1234AA",
    bookingId: Long = 12345,
    requestedProfileType: String = "SHOESIZE",
    telemetryType: String = "updated",
    ignoreReason: String? = null,
    errorReason: String? = null,
  ) =
    verify(telemetryClient).trackEvent(
      eq("profile-details-physical-attributes-synchronisation-$telemetryType"),
      check {
        assertThat(it["offenderNo"]).isEqualTo(offenderNo)
        assertThat(it["bookingId"]).isEqualTo("$bookingId")
        assertThat(it["requestedProfileType"]).isEqualTo(requestedProfileType)
        ignoreReason?.run { assertThat(it["reason"]).isEqualTo(this) }
        errorReason?.run { assertThat(it["error"]).isEqualTo(this) }
        if (ignoreReason == null && errorReason == null) {
          assertThat(it["physicalAttributesHistoryId"]).isEqualTo("[321]")
        }
      },
      isNull(),
    )
}
