package uk.gov.justice.digital.hmpps.prisonerfromnomismigration.coreperson

import com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor
import com.github.tomakehurst.wiremock.client.WireMock.notMatching
import com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor
import com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo
import com.github.tomakehurst.wiremock.client.WireMock.urlPathMatching
import kotlinx.coroutines.test.runTest
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import org.mockito.kotlin.check
import org.mockito.kotlin.eq
import org.mockito.kotlin.isNull
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.springframework.beans.factory.annotation.Autowired
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.integration.SqsIntegrationTestBase
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.integration.sendMessage
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomisprisoner.model.CodeDescription
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomisprisoner.model.NomisAudit
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomisprisoner.model.OffenderBelief
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.wiremock.withRequestBodyJsonPath
import java.time.LocalDate
import java.time.LocalDateTime

class CorePersonSynchronisationBeliefsIntTest(
  @Autowired private val nomisApi: CorePersonNomisApiMockServer,
) : SqsIntegrationTestBase() {

  private val cprApi = CorePersonCprApiExtension.cprCorePersonServer

  @Nested
  @DisplayName("OFFENDER_BELIEFS")
  inner class OffenderBeliefs {
    @Nested
    inner class HappyPath {
      @ParameterizedTest
      @ValueSource(strings = ["INSERTED", "UPDATED", "DELETED"])
      fun `should sync new belief to CPR`(eventType: String) = runTest {
        nomisApi.stubGetOffenderReligions(
          prisonNumber = "A1234AA",
          religions = listOf(
            OffenderBelief(
              beliefId = 1,
              belief = CodeDescription("DRU", "Druid"),
              startDate = LocalDate.parse("2016-08-02"),
              verified = true,
              audit = NomisAudit(
                createDatetime = LocalDateTime.parse("2016-08-01T10:55:00"),
                createUsername = "KOFEADDY",
                createDisplayName = "KOFE ADDY",
              ),
              changeReason = true,
              comments = "No longer believes in Zoroastrianism",
            ),
            OffenderBelief(
              beliefId = 2,
              belief = CodeDescription("ZOO", "Zoroastrianism"),
              startDate = LocalDate.parse("2015-08-02"),
              endDate = LocalDate.parse("2016-08-02"),
              verified = false,
              audit = NomisAudit(
                createDatetime = LocalDateTime.parse("2016-08-01T10:55:00"),
                createUsername = "KOFEADDY",
                createDisplayName = "KOFE ADDY",
              ),
              changeReason = false,
            ),
          ),
        )
        cprApi.stubSyncCreateOffenderBelief("A1234AA")

        sendBeliefsEvent(prisonerNumber = "A1234AA", eventType = eventType)
          .also { waitForAnyProcessingToComplete("coreperson-beliefs-synchronisation-success") }

        verifyNomis(
          offenderNo = "A1234AA",
        )
        cprApi.verify(
          postRequestedFor(urlPathEqualTo("/syscon-sync/religion/A1234AA"))
            .withRequestBodyJsonPath("religions.length()", 2)
            .withRequestBodyJsonPath("religions[0].current", true)
            .withRequestBodyJsonPath("religions[0].religionCode", "DRU")
            .withRequestBodyJsonPath("religions[0].changeReasonKnown", true)
            .withRequestBodyJsonPath("religions[0].comments", "No longer believes in Zoroastrianism")
            .withRequestBodyJsonPath("religions[0].verified", true)
            .withRequestBodyJsonPath("religions[0].startDate", "2016-08-02")
            .withRequestBodyJsonPath("religions[0].modifyUserId", "KOFEADDY")
            .withRequestBodyJsonPath("religions[0].modifyDateTime", "2016-08-01T10:55:00")
            .withRequestBodyJsonPath("religions[1].current", false)
            .withRequestBodyJsonPath("religions[1].religionCode", "ZOO")
            .withRequestBodyJsonPath("religions[1].changeReasonKnown", false)
            .withRequestBody(notMatching("religions[1].comments"))
            .withRequestBodyJsonPath("religions[1].verified", false)
            .withRequestBodyJsonPath("religions[1].startDate", "2015-08-02")
            .withRequestBodyJsonPath("religions[1].endDate", "2016-08-02")
            .withRequestBodyJsonPath("religions[1].modifyUserId", "KOFEADDY")
            .withRequestBodyJsonPath("religions[1].modifyDateTime", "2016-08-01T10:55:00"),
        )
        verifyTelemetry(
          "coreperson-beliefs-synchronisation-success",
          offenderNo = "A1234AA",
        )
      }
    }
  }

  private fun verifyNomis(
    offenderNo: String = "A1234AA",
  ) {
    nomisApi.verify(
      getRequestedFor(urlPathMatching("/core-person/$offenderNo/religions")),
    )
  }

  private fun verifyTelemetry(
    name: String,
    offenderNo: String = "A1234AA",
    error: String? = null,
    count: Int = 1,
  ) = verify(telemetryClient, times(count)).trackEvent(
    eq(name),
    check {
      assertThat(it["offenderNo"]).isEqualTo(offenderNo)
      error?.run { assertThat(it["error"]).isEqualTo(error) }
    },
    isNull(),
  )

  private fun beliefsEvent(
    prisonerNumber: String = "A1234AA",
    eventType: String,
  ) = """
   {
      "Type" : "Notification",
      "MessageId" : "298a61d6-e078-51f2-9c60-3f348f8bde68",
      "TopicArn" : "arn:aws:sns:eu-west-2:754256621582:cloud-platform-Digital-Prison-Services-f221e27fcfcf78f6ab4f4c3cc165eee7",
      "Message" : "{\"eventType\":\"OFFENDER_BELIEFS-$eventType\",\"eventDatetime\":\"2024-06-11T16:30:59\",\"offenderIdDisplay\":\"$prisonerNumber\",\"bookingId\":12345,\"rootOffenderId\":123,\"offenderBeliefId\":234,\"auditModuleName\":\"OIDBELIF\"}",
      "Timestamp" : "2024-06-11T15:30:59.048Z",
      "SignatureVersion" : "1",
      "Signature" : "kyuV8tDWmRoixtnyXauR/mzBdkO4yWXEFLZU6256JRIRfcGBNdn7+TPcRnM7afa6N6DwUs3TDKQ17U7W8hkB86r/J1PsfEpF8qOr8bZd4J/RDNAHJxmNnuTzy351ISDYjdxccREF57pLXtaMcu0Z6nJTTnv9pn7qOVasuxUIGANaD214P6iXkWvsFj0AgR1TVITHW5jMFTE+ln2PTLQ9N6dwx4/foIlFsQu7rWnx3hy9+x7gtInnDIaQSvI2gHQQI51TpQrES0YKjn5Tb25ANS8bZooK7knt9F+Hv3bejDyXWgR3fyC4SJbUvbVhfVI/aRhOv/qLwFGSOFKt6I0KAA==",
      "SigningCertURL" : "https://sns.eu-west-2.amazonaws.com/SimpleNotificationService-60eadc530605d63b8e62a523676ef735.pem",
      "UnsubscribeURL" : "https://sns.eu-west-2.amazonaws.com/?Action=Unsubscribe&SubscriptionArn=arn:aws:sns:eu-west-2:754256621582:cloud-platform-Digital-Prison-Services-f221e27fcfcf78f6ab4f4c3cc165eee7:e5c3f313-ccda-4a2f-9667-e2519fd01a19",
      "MessageAttributes" : {
        "publishedAt" : {"Type":"String","Value":"2024-06-11T16:30:59.023769832+01:00"},
        "eventType" : {"Type":"String","Value":"OFFENDER_BELIEFS-$eventType"}
      }
   }
  """.trimIndent()

  private fun sendBeliefsEvent(
    prisonerNumber: String,
    eventType: String,
  ) = awsSqsCorePersonOffenderEventsClient.sendMessage(
    corePersonQueueOffenderEventsUrl,
    beliefsEvent(prisonerNumber, eventType),
  )
}
