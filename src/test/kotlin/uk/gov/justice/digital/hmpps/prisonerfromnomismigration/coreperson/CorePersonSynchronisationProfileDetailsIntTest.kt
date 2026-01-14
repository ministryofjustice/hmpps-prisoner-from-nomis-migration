package uk.gov.justice.digital.hmpps.prisonerfromnomismigration.coreperson

import com.github.tomakehurst.wiremock.client.WireMock.equalTo
import com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor
import com.github.tomakehurst.wiremock.client.WireMock.havingExactly
import com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor
import com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo
import com.github.tomakehurst.wiremock.client.WireMock.urlPathMatching
import kotlinx.coroutines.test.runTest
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.mockito.kotlin.check
import org.mockito.kotlin.eq
import org.mockito.kotlin.isNull
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.integration.SqsIntegrationTestBase
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.integration.sendMessage
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomisprisoner.model.BookingProfileDetailsResponse
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomisprisoner.model.PrisonerProfileDetailsResponse
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomisprisoner.model.ProfileDetailsResponse
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.wiremock.ProfileDetailsNomisApiMockServer
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.wiremock.withRequestBodyJsonPath
import java.time.LocalDateTime

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class CorePersonSynchronisationProfileDetailsIntTest(
  @Autowired private val nomisApi: ProfileDetailsNomisApiMockServer,
) : SqsIntegrationTestBase() {

  private val cprApi = CorePersonCprApiExtension.cprCorePersonServer

  @Nested
  @DisplayName("OFFENDER_PHYSICAL_DETAILS-CHANGED for SEXO profile type")
  inner class SexualOrientation {
    @Nested
    inner class HappyPath {
      @Test
      fun `should sync new profile details to CPR`() = runTest {
        nomisApi.stubGetProfileDetails(
          offenderNo = "A1234AA",
          bookingId = null,
          profileTypes = listOf("SEXO"),
          response = nomisResponse(
            offenderNo = "A1234AA",
            bookings = listOf(
              booking(
                bookingId = 12345,
                sequence = 1,
                profileDetails = listOf(profileDetails(type = "SEXO", code = "M")),
              ),
            ),
          ),
        )
        cprApi.stubSyncCreateSexualOrientation("A1234AA")

        sendProfileDetailsChangedEvent(prisonerNumber = "A1234AA", bookingId = 12345, profileType = "SEXO")
          .also { waitForAnyProcessingToComplete("coreperson-profiledetails-synchronisation-success") }

        verifyNomis(
          offenderNo = "A1234AA",
          bookingId = null,
          profileType = "SEXO",
        )
        cprApi.verify(
          postRequestedFor(urlPathEqualTo("/syscon-sync/sexual-orientation/A1234AA"))
            .withRequestBodyJsonPath("sexualOrientationCode", "M")
            .withRequestBodyJsonPath("modifyUserId", "A_USER")
            .withRequestBodyJsonPath("modifyDateTime", "2024-09-04T12:34:56"),
        )
        verifyTelemetry(
          "coreperson-profiledetails-synchronisation-success",
          offenderNo = "A1234AA",
          bookingId = 12345,
          profileType = "SEXO",
        )
      }

      @Test
      fun `event is on old booking`() = runTest {
        nomisApi.stubGetProfileDetails(
          offenderNo = "A1234AA",
          bookingId = null,
          profileTypes = listOf("SEXO"),
          response = nomisResponse(
            offenderNo = "A1234AA",
            bookings = listOf(
              booking(
                bookingId = 12345,
                sequence = 1,
                profileDetails = listOf(profileDetails(type = "SEXO", code = "M")),
              ),
              booking(
                bookingId = 11111,
                sequence = 2,
                profileDetails = listOf(profileDetails(type = "SEXO", code = "F")),
              ),
            ),
          ),
        )
        cprApi.stubSyncCreateSexualOrientation("A1234AA")

        sendProfileDetailsChangedEvent(prisonerNumber = "A1234AA", bookingId = 11111, profileType = "SEXO")
          .also { waitForAnyProcessingToComplete("coreperson-profiledetails-synchronisation-ignored-booking") }

        verifyNomis(
          offenderNo = "A1234AA",
          bookingId = null,
          profileType = "SEXO",
        )
        cprApi.verify(
          0,
          postRequestedFor(urlPathEqualTo("/syscon-sync/sexual-orientation")),
        )
        verifyTelemetry(
          "coreperson-profiledetails-synchronisation-ignored-booking",
          offenderNo = "A1234AA",
          bookingId = 11111,
          profileType = "SEXO",
        )
      }

      @Test
      fun `event is for a duplicate profile value`() = runTest {
        nomisApi.stubGetProfileDetails(
          offenderNo = "A1234AA",
          bookingId = null,
          profileTypes = listOf("SEXO"),
          response = nomisResponse(
            offenderNo = "A1234AA",
            bookings = listOf(
              booking(
                bookingId = 12345,
                sequence = 1,
                profileDetails = listOf(profileDetails(type = "SEXO", code = "M")),
              ),
              booking(
                bookingId = 11111,
                sequence = 3,
                profileDetails = listOf(profileDetails(type = "SEXO", code = "OTHER")),
              ),
              booking(
                bookingId = 11223,
                sequence = 2,
                profileDetails = listOf(profileDetails(type = "SEXO", code = "M")),
              ),
            ),
          ),
        )
        cprApi.stubSyncCreateSexualOrientation("A1234AA")

        sendProfileDetailsChangedEvent(prisonerNumber = "A1234AA", bookingId = 12345, profileType = "SEXO")
          .also { waitForAnyProcessingToComplete("coreperson-profiledetails-synchronisation-ignored-duplicate") }

        verifyNomis(
          offenderNo = "A1234AA",
          bookingId = null,
          profileType = "SEXO",
        )
        cprApi.verify(
          0,
          postRequestedFor(urlPathEqualTo("/syscon-sync/sexual-orientation")),
        )
        verifyTelemetry(
          "coreperson-profiledetails-synchronisation-ignored-duplicate",
          offenderNo = "A1234AA",
          bookingId = 12345,
          profileType = "SEXO",
        )
      }
    }

    @Nested
    inner class ErrorScenarios {
      @Test
      fun `there is no current or latest booking`() = runTest {
        nomisApi.stubGetProfileDetails(
          offenderNo = "A1234AA",
          bookingId = null,
          profileTypes = listOf("SEXO"),
          response = nomisResponse(
            offenderNo = "A1234AA",
            bookings = listOf(
              booking(
                bookingId = 11111,
                sequence = 2,
                profileDetails = listOf(profileDetails(type = "SEXO", code = "M")),
              ),
            ),
          ),
        )
        cprApi.stubSyncCreateSexualOrientation("A1234AA")

        sendProfileDetailsChangedEvent(prisonerNumber = "A1234AA", bookingId = 12345, profileType = "SEXO")
          .also { waitForAnyProcessingToComplete("coreperson-profiledetails-synchronisation-error", 2) }

        cprApi.verify(
          0,
          postRequestedFor(urlPathEqualTo("/syscon-sync/sexual-orientation")),
        )
        verifyTelemetry(
          "coreperson-profiledetails-synchronisation-error",
          offenderNo = "A1234AA",
          bookingId = 12345,
          profileType = "SEXO",
          error = "Could not find latest booking",
          count = 2,
        )
      }
    }
  }

  @Nested
  @DisplayName("OFFENDER_PHYSICAL_DETAILS-CHANGED for DISABILITY profile type")
  inner class Disability {
    @Nested
    inner class HappyPath {
      @Test
      fun `should sync new profile details to CPR`() = runTest {
        nomisApi.stubGetProfileDetails(
          offenderNo = "A1234AA",
          bookingId = null,
          profileTypes = listOf("DISABILITY"),
          response = nomisResponse(
            offenderNo = "A1234AA",
            bookings = listOf(
              booking(
                bookingId = 12345,
                sequence = 1,
                profileDetails = listOf(profileDetails(type = "DISABILITY", code = "YES")),
              ),
            ),
          ),
        )
        cprApi.stubSyncCreateDisability("A1234AA")

        sendProfileDetailsChangedEvent(prisonerNumber = "A1234AA", bookingId = 12345, profileType = "DISABILITY")
          .also { waitForAnyProcessingToComplete("coreperson-profiledetails-synchronisation-success") }

        verifyNomis(
          offenderNo = "A1234AA",
          bookingId = null,
          profileType = "DISABILITY",
        )
        cprApi.verify(
          postRequestedFor(urlPathEqualTo("/syscon-sync/disability-status/A1234AA"))
            .withRequestBodyJsonPath("disability", true)
            .withRequestBodyJsonPath("modifyUserId", "A_USER")
            .withRequestBodyJsonPath("modifyDateTime", "2024-09-04T12:34:56"),
        )
        verifyTelemetry(
          "coreperson-profiledetails-synchronisation-success",
          offenderNo = "A1234AA",
          bookingId = 12345,
          profileType = "DISABILITY",
        )
      }
    }
  }

  @Nested
  @DisplayName("OFFENDER_PHYSICAL_DETAILS-CHANGED for IMM profile type")
  inner class Immigration {
    @Nested
    inner class HappyPath {
      @Test
      fun `should sync new profile details to CPR`() = runTest {
        nomisApi.stubGetProfileDetails(
          offenderNo = "A1234AA",
          bookingId = null,
          profileTypes = listOf("IMM"),
          response = nomisResponse(
            offenderNo = "A1234AA",
            bookings = listOf(
              booking(
                bookingId = 12345,
                sequence = 1,
                profileDetails = listOf(profileDetails(type = "IMM", code = "Y")),
              ),
            ),
          ),
        )
        cprApi.stubSyncCreateImmigration("A1234AA")

        sendProfileDetailsChangedEvent(prisonerNumber = "A1234AA", bookingId = 12345, profileType = "IMM")
          .also { waitForAnyProcessingToComplete("coreperson-profiledetails-synchronisation-success") }

        verifyNomis(
          offenderNo = "A1234AA",
          bookingId = null,
          profileType = "IMM",
        )
        cprApi.verify(
          postRequestedFor(urlPathEqualTo("/syscon-sync/immigration-status/A1234AA"))
            .withRequestBodyJsonPath("interestToImmigration", true)
            .withRequestBodyJsonPath("modifyUserId", "A_USER")
            .withRequestBodyJsonPath("modifyDateTime", "2024-09-04T12:34:56"),
        )
        verifyTelemetry(
          "coreperson-profiledetails-synchronisation-success",
          offenderNo = "A1234AA",
          bookingId = 12345,
          profileType = "IMM",
        )
      }
    }
  }

  @Nested
  @DisplayName("OFFENDER_PHYSICAL_DETAILS-CHANGED for Nationality profile types")
  inner class Nationality {
    @Nested
    inner class HappyPath {
      @Test
      fun `should sync new NAT details to CPR`() = runTest {
        nomisApi.stubGetProfileDetails(
          offenderNo = "A1234AA",
          bookingId = null,
          profileTypes = listOf("NAT", "NATIO"),
          response = nomisResponse(
            offenderNo = "A1234AA",
            bookings = listOf(
              booking(
                bookingId = 12345,
                sequence = 1,
                profileDetails = listOf(
                  profileDetails(type = "NAT", code = "BRI"),
                  profileDetails(type = "NATIO", code = null),
                ),
              ),
            ),
          ),
        )
        cprApi.stubSyncCreateNationality("A1234AA")

        sendProfileDetailsChangedEvent(prisonerNumber = "A1234AA", bookingId = 12345, profileType = "NAT")
          .also { waitForAnyProcessingToComplete("coreperson-profiledetails-synchronisation-success") }

        nomisApi.verify(
          getRequestedFor(urlPathMatching("/prisoners/A1234AA/profile-details"))
            .withQueryParam("profileTypes", havingExactly("NAT", "NATIO")),
        )
        cprApi.verify(
          postRequestedFor(urlPathEqualTo("/syscon-sync/nationality/A1234AA"))
            .withRequestBodyJsonPath("nationalityCode", "BRI")
            .withRequestBodyJsonPath("modifyUserId", "A_USER")
            .withRequestBodyJsonPath("modifyDateTime", "2024-09-04T12:34:56"),
        )
        verifyTelemetry(
          "coreperson-profiledetails-synchronisation-success",
          offenderNo = "A1234AA",
          bookingId = 12345,
          profileType = "NAT",
        )
      }

      @Test
      fun `should sync new NATIO details to CPR`() = runTest {
        nomisApi.stubGetProfileDetails(
          offenderNo = "A1234AA",
          bookingId = null,
          profileTypes = listOf("NAT", "NATIO"),
          response = nomisResponse(
            offenderNo = "A1234AA",
            bookings = listOf(
              booking(
                bookingId = 12345,
                sequence = 1,
                profileDetails = listOf(
                  profileDetails(type = "NAT", code = "BRI"),
                  profileDetails(type = "NATIO", code = "details"),
                ),
              ),
            ),
          ),
        )
        cprApi.stubSyncCreateNationality("A1234AA")

        sendProfileDetailsChangedEvent(prisonerNumber = "A1234AA", bookingId = 12345, profileType = "NATIO")
          .also { waitForAnyProcessingToComplete("coreperson-profiledetails-synchronisation-success") }

        nomisApi.verify(
          getRequestedFor(urlPathMatching("/prisoners/A1234AA/profile-details"))
            .withQueryParam("profileTypes", havingExactly("NAT", "NATIO")),
        )
        cprApi.verify(
          postRequestedFor(urlPathEqualTo("/syscon-sync/nationality/A1234AA"))
            .withRequestBodyJsonPath("nationalityCode", "BRI")
            .withRequestBodyJsonPath("modifyUserId", "A_USER")
            .withRequestBodyJsonPath("modifyDateTime", "2024-09-04T12:34:56")
            .withRequestBodyJsonPath("notes", "details"),
        )
        verifyTelemetry(
          "coreperson-profiledetails-synchronisation-success",
          offenderNo = "A1234AA",
          bookingId = 12345,
          profileType = "NATIO",
        )
      }
    }
  }

  private fun verifyNomis(
    offenderNo: String = "A1234AA",
    bookingId: Long? = 12345,
    profileType: String,
  ) {
    nomisApi.verify(
      getRequestedFor(urlPathMatching("/prisoners/$offenderNo/profile-details"))
        .withQueryParam("profileTypes", equalTo(profileType))
        .apply { bookingId?.run { withQueryParam("bookingId", equalTo("$bookingId")) } },
    )
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
    startDateTime: LocalDateTime = LocalDateTime.parse("2024-09-03T12:34:56"),
    sequence: Int = 1,
    profileDetails: List<ProfileDetailsResponse> = listOf(profileDetails()),
  ) = BookingProfileDetailsResponse(
    bookingId = bookingId,
    startDateTime = startDateTime,
    latestBooking = sequence == 1,
    sequence = sequence,
    profileDetails = profileDetails,
  )

  private fun profileDetails(
    type: String = "MARITAL",
    code: String? = "M",
    created: LocalDateTime = LocalDateTime.parse("2024-09-04T12:34:56"),
    createdBy: String = "A_USER",
    modifiedDateTime: LocalDateTime? = null,
    modifiedBy: String? = null,
    auditModuleName: String = "NOMIS",
  ) = ProfileDetailsResponse(
    type = type,
    code = code,
    createDateTime = created,
    createdBy = createdBy,
    modifiedDateTime = modifiedDateTime,
    modifiedBy = modifiedBy,
    auditModuleName = auditModuleName,
  )

  private fun verifyTelemetry(
    name: String,
    offenderNo: String = "A1234AA",
    bookingId: Long? = 12345,
    profileType: String? = null,
    error: String? = null,
    count: Int = 1,
  ) = verify(telemetryClient, times(count)).trackEvent(
    eq(name),
    check {
      assertThat(it["offenderNo"]).isEqualTo(offenderNo)
      bookingId?.run { assertThat(it["bookingId"]).isEqualTo("$bookingId") }
      profileType?.run { assertThat(it["profileType"]).isEqualTo(profileType) }
      error?.run { assertThat(it["error"]).isEqualTo(error) }
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
    prisonerNumber: String,
    bookingId: Int,
    profileType: String,
  ) = awsSqsCorePersonOffenderEventsClient.sendMessage(
    corePersonQueueOffenderEventsUrl,
    profileDetailsChangedEvent(prisonerNumber, bookingId, profileType),
  )
}
