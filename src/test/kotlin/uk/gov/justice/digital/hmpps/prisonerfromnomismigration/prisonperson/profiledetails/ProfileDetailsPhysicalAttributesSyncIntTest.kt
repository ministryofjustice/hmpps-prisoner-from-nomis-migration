package uk.gov.justice.digital.hmpps.prisonerfromnomismigration.prisonperson.profiledetails

import com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor
import com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo
import kotlinx.coroutines.test.runTest
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.mockito.kotlin.check
import org.mockito.kotlin.eq
import org.mockito.kotlin.isNull
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.mock.mockito.SpyBean
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.integration.SqsIntegrationTestBase
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.integration.sendMessage

class ProfileDetailsPhysicalAttributesSyncIntTest : SqsIntegrationTestBase() {

  @Autowired
  private lateinit var nomisApi: ProfileDetailsNomisApiMockServer

  @SpyBean
  private lateinit var dpsApi: ProfileDetailPhysicalAttributesDpsApiService

  @Nested
  @DisplayName("OFFENDER_PHYSICAL_DETAILS-CHANGED")
  inner class PhysicalAttributesChanged {
    @Nested
    inner class Events {
      @Test
      fun `should sync profile types we are interested in`() = runTest {
        nomisApi.stubGetProfileDetails("A1234AA")

        sendProfileDetailsChangedEvent(prisonerNumber = "A1234AA", bookingId = 1, profileType = "BUILD")

        nomisApi.verify(getRequestedFor(urlPathEqualTo("/prisoners/A1234AA/profile-details")))
        verify(dpsApi).syncProfileDetailsPhysicalAttributes("A1234AA")
        verify(telemetryClient).trackEvent(
          eq("profile-details-physical-attributes-synchronisation-updated"),
          check {
            assertThat(it["offenderNo"]).isEqualTo("A1234AA")
            assertThat(it["bookingId"]).isEqualTo("1")
            assertThat(it["profileType"]).isEqualTo("BUILD")
          },
          isNull(),
        )
      }

      @Test
      fun `should do nothing for profile types we're not interested in`() = runTest {
        sendProfileDetailsChangedEvent(prisonerNumber = "A1234AA", bookingId = 1, profileType = "RELF")

        nomisApi.verify(0, getRequestedFor(urlPathEqualTo("/prisoners/A1234AA/profile-details")))
        verify(dpsApi, never()).syncProfileDetailsPhysicalAttributes("A1234AA")
        verify(telemetryClient).trackEvent(
          eq("profile-details-synchronisation-ignored"),
          check {
            assertThat(it["offenderNo"]).isEqualTo("A1234AA")
            assertThat(it["bookingId"]).isEqualTo("1")
            assertThat(it["profileType"]).isEqualTo("RELF")
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
}
