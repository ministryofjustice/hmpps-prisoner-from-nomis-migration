package uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nonassociations

import com.github.tomakehurst.wiremock.client.WireMock.exactly
import com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor
import com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor
import com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo
import org.assertj.core.api.Assertions.assertThat
import org.awaitility.kotlin.await
import org.awaitility.kotlin.untilAsserted
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.mockito.Mockito
import org.mockito.kotlin.check
import org.mockito.kotlin.isNull
import org.mockito.kotlin.verify
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.integration.SqsIntegrationTestBase
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.integration.sendMessage
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.wiremock.NomisApiExtension.Companion.nomisApi
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.wiremock.NonAssociationsApiExtension.Companion.nonAssociationsApi

private const val OFFENDER_NUMBER = "G4803UT"
private const val NS_OFFENDER_NUMBER = "A4803BG"
private const val nomisApiUrl = "/non-associations/offender/$OFFENDER_NUMBER/ns-offender/$NS_OFFENDER_NUMBER"

class NonAssociationsSynchronisationIntTest : SqsIntegrationTestBase() {

  @Nested
  @DisplayName("NON_ASSOCIATION_DETAIL-UPSERTED")
  inner class NonAssociationDetailUpserted {
    @Nested
    @DisplayName("When there is a new Non-association")
    inner class WhenNewNonAssocation {

      @Nested
      inner class WhenCreateByDPS {
        @BeforeEach
        fun setUp() {
          nonAssociationsOffenderEventsClient.sendMessage(
            nonAssociationsQueueOffenderEventsUrl,
            nonAssociationEvent(
              eventType = "NON_ASSOCIATION_DETAIL-UPSERTED",
              auditModuleName = "DPS_SYNCHRONISATION",

              offenderIdDisplay = OFFENDER_NUMBER,
              nsOffenderIdDisplay = NS_OFFENDER_NUMBER,
            ),
          )
        }

        @Test
        fun `the event is ignored`() {
          await untilAsserted {
            verify(telemetryClient).trackEvent(
              Mockito.eq("non-association-synchronisation-skipped"),
              check {
                assertThat(it["offenderNo"]).isEqualTo(OFFENDER_NUMBER)
                assertThat(it["nsOffenderNo"]).isEqualTo(NS_OFFENDER_NUMBER)
                assertThat(it["nonAssociationsId"]).isNull()
              },
              isNull(),
            )
          }
          nomisApi.verify(exactly(0), getRequestedFor(urlPathEqualTo(nomisApiUrl)))
          nonAssociationsApi.verify(exactly(0), postRequestedFor(urlPathEqualTo("/sync")))
        }
      }

      @Nested
      inner class WhenCreateByNomis {
        @BeforeEach
        fun setUp() {
          nomisApi.stubGetNonAssociation(offenderNo = OFFENDER_NUMBER, nsOffenderNo = NS_OFFENDER_NUMBER)
          nonAssociationsApi.stubCreateNonAssociationForSynchronisation(firstOffenderNo = OFFENDER_NUMBER, secondOffenderNo = NS_OFFENDER_NUMBER)

          nonAssociationsOffenderEventsClient.sendMessage(
            nonAssociationsQueueOffenderEventsUrl,
            nonAssociationEvent(
              eventType = "NON_ASSOCIATION_DETAIL-UPSERTED",
              auditModuleName = "OIDSENAD",
              offenderIdDisplay = OFFENDER_NUMBER,
              nsOffenderIdDisplay = NS_OFFENDER_NUMBER,
            ),
          )
        }

        @Test
        fun `will retrieve details about the non-association from NOMIS`() {
          await untilAsserted {
            nomisApi.verify(getRequestedFor(urlPathEqualTo(nomisApiUrl)))
          }
        }

        @Test
        fun `will create the non-association in the non-associations service`() {
          await untilAsserted {
            nonAssociationsApi.verify(postRequestedFor(urlPathEqualTo("/sync")))
          }
        }

        @Test
        fun `will create telemetry tracking the create`() {
          await untilAsserted {
            verify(telemetryClient).trackEvent(
              Mockito.eq("non-association-created-synchronisation-success"),
              check {
                assertThat(it["offenderNo"]).isEqualTo(OFFENDER_NUMBER)
                assertThat(it["nsOffenderNo"]).isEqualTo(NS_OFFENDER_NUMBER)
              },
              isNull(),
            )
          }
        }
      }
    }
  }
}

fun nonAssociationEvent(
  eventType: String,
  offenderIdDisplay: String = OFFENDER_NUMBER,
  nsOffenderIdDisplay: String = NS_OFFENDER_NUMBER,
  auditModuleName: String = "OIDSENAD",
) = """{
    "Type" : "Notification",
    "MessageId" : "be8e7273-0446-5590-8c7f-2f24e966322e",
    "TopicArn" : "arn:aws:sns:eu-west-2:754256621582:cloud-platform-Digital-Prison-Services-f221e27fcfcf78f6ab4f4c3cc165eee7",
    "Message" : "{\"eventType\":\"NON_ASSOCIATION_DETAIL-UPSERTED\",\"eventDatetime\":\"2023-08-17T10:39:44\",\"bookingId\":1201882,\"offenderIdDisplay\":\"$offenderIdDisplay\",\"nomisEventType\":\"OFF_NA_DETAILS_ASSOC-UPDATED\",\"nsOffenderIdDisplay\":\"$nsOffenderIdDisplay\",\"nsBookingId\":1202145,\"reasonCode\":\"NOT_REL\",\"nsType\":\"CELL\",\"typeSeq\":1,\"effectiveDate\":\"2023-08-17\",\"expiryDate\":\"2026-12-20\",\"authorisedBy\":\"Manager\",\"comment\":\"Test comment\",\"auditModuleName\":\"$auditModuleName\"}",
    "Timestamp" : "2023-08-17T09:39:44.790Z",
    "SignatureVersion" : "1",
    "Signature" : "ppyNS9XAEwLaSdtXpVxZ+pYKT7g4uZLGGXUaquKKwtgpkcRCoTwG2Vcjbgh4HpqF0zNXTIQJHAckXBqXMXW6CeJuKcvndqOXO7yw+qzbL4iOkPecUkl4pJFWT0PJ4q6gptDOWf/nKP+Wd/ggozuGa27NJ5eEEGn/YbxnqH98h9C0pUjVPhaukoSp0fP6+2L8eyuFEPGgefT+reKZZ2E9VjUStaNNsNjdjVfkjrkHrVQwpey8PbucOQYLEwyo/WV6ho+gqjQYpM+WjghDWvGn6UNbnJKTQGxy3shInPsY2kfyCJAyUoOU0CJ6ALHKnlN7OMr1lbvmHMARgKNY6ELJoA==",
    "SigningCertURL" : "https://sns.eu-west-2.amazonaws.com/SimpleNotificationService-01d088a6f77103d0fe307c0069e40ed6.pem",
    "UnsubscribeURL" : "https://sns.eu-west-2.amazonaws.com/?Action=Unsubscribe&SubscriptionArn=arn:aws:sns:eu-west-2:754256621582:cloud-platform-Digital-Prison-Services-f221e27fcfcf78f6ab4f4c3cc165eee7:bc997c88-9318-4ec0-b424-3f4579c9be6d",
    "MessageAttributes" : {
      "publishedAt" : {"Type":"String","Value":"2023-08-17T10:39:44.744191611+01:00"},
      "eventType" : {"Type": "String","Value": "$eventType"}
   }
}
""".trimIndent()
