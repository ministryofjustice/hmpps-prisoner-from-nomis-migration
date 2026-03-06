package uk.gov.justice.digital.hmpps.prisonerfromnomismigration.coreperson

import com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor
import com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor
import com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo
import com.github.tomakehurst.wiremock.client.WireMock.urlPathMatching
import kotlinx.coroutines.test.runTest
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.mockito.ArgumentMatchers
import org.mockito.kotlin.check
import org.mockito.kotlin.eq
import org.mockito.kotlin.isNull
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.springframework.beans.factory.annotation.Autowired
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.coreperson.religion.ReligionsMappingApiMockServer
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

  @Autowired
  private lateinit var religionsMappingApiMock: ReligionsMappingApiMockServer

  private val cprApi = CorePersonCprApiExtension.cprCorePersonServer

  @Nested
  @DisplayName("OFFENDER_BELIEFS")
  inner class OffenderBeliefs {
    @Nested
    @DisplayName("OFFENDER_BELIEFS-INSERTED")
    inner class OfficialBeliefsCreated {

      @Nested
      inner class WhenCreatedInDps {
        @BeforeEach
        fun setUp() {
          sendBeliefsEvent(
            prisonerNumber = "A1234AA",
            beliefId = 1,
            eventType = "INSERTED",
            auditModuleName = "DPS_SYNCHRONISATION",
          )
            .also { waitForAnyProcessingToComplete() }
        }

        @Test
        fun `will track telemetry`() {
          verify(telemetryClient).trackEvent(
            ArgumentMatchers.eq("coreperson-beliefs-synchronisation-created-skipped"),
            check {
              assertThat(it["prisonNumber"]).isEqualTo("A1234AA")
              assertThat(it["nomisId"]).isEqualTo("1")
            },
            isNull(),
          )
        }
      }

      @Nested
      inner class HappyPath {

        @BeforeEach
        fun setup() {
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
          religionsMappingApiMock.stubGetReligionsByNomisPrisonNumber(nomisPrisonNumber = "A1234AA")
          cprApi.stubSyncCreateOffenderBelief("A1234AA")
          religionsMappingApiMock.stubCreateReligionMapping()
        }

        @Test
        fun `should sync new belief to CPR`() = runTest {
          sendBeliefsEvent(prisonerNumber = "A1234AA", beliefId = 1, eventType = "INSERTED")
            .also { waitForAnyProcessingToComplete("coreperson-beliefs-synchronisation-success") }

          verifyNomis(offenderNo = "A1234AA")
          verifyMappingExistsCheck(prisonNumber = "A1234AA")
          cprApi.verify(
            postRequestedFor(urlPathEqualTo("/person/prison/A1234AA/religion"))
              .withRequestBodyJsonPath("nomisReligionId", 1)
              .withRequestBodyJsonPath("current", true)
              .withRequestBodyJsonPath("religionCode", "DRU")
              .withRequestBodyJsonPath("changeReasonKnown", true)
              .withRequestBodyJsonPath("comments", "No longer believes in Zoroastrianism")
              .withRequestBodyJsonPath("verified", true)
              .withRequestBodyJsonPath("startDate", "2016-08-02")
              .withRequestBodyJsonPath("modifyUserId", "KOFEADDY")
              .withRequestBodyJsonPath("modifyDateTime", "2016-08-01T10:55:00"),
          )
          verifyMappingSaved(prisonNumber = "A1234AA")

          verifyTelemetry(
            "coreperson-beliefs-synchronisation-success",
            offenderNo = "A1234AA",
            nomisId = 1,
          )
        }
      }
    }

    @Nested
    @DisplayName("OFFENDER_BELIEFS-INSERTED")
    inner class OfficialBeliefsUpdated {
      // TODO
    }
  }

  private fun verifyNomis(
    offenderNo: String = "A1234AA",
  ) {
    nomisApi.verify(
      getRequestedFor(urlPathMatching("/core-person/$offenderNo/religions")),
    )
  }
  private fun verifyMappingExistsCheck(prisonNumber: String = "A1234AA") {
    religionsMappingApiMock.verify(getRequestedFor(urlPathMatching("/mapping/core-person-religion/religions/nomis-prison-number/$prisonNumber")))
  }
  private fun verifyMappingSaved(prisonNumber: String = "A1234AA") {
    religionsMappingApiMock.verify(postRequestedFor(urlPathMatching("/mapping/core-person-religion/religion")))
  }
  private fun verifyTelemetry(
    name: String,
    offenderNo: String = "A1234AA",
    nomisId: Long = 1,
    error: String? = null,
    count: Int = 1,
  ) = verify(telemetryClient, times(count)).trackEvent(
    eq(name),
    check {
      assertThat(it["prisonNumber"]).isEqualTo(offenderNo)
      assertThat(it["nomisId"]).isEqualTo(nomisId.toString())
      error?.run { assertThat(it["error"]).isEqualTo(error) }
    },
    isNull(),
  )

  private fun beliefsEvent(
    prisonerNumber: String = "A1234AA",
    beliefId: Long = 234,
    eventType: String,
    auditModuleName: String? = "OIDBELIF",
  ) = """
   {
      "Type" : "Notification",
      "MessageId" : "298a61d6-e078-51f2-9c60-3f348f8bde68",
      "TopicArn" : "arn:aws:sns:eu-west-2:754256621582:cloud-platform-Digital-Prison-Services-f221e27fcfcf78f6ab4f4c3cc165eee7",
      "Message" : "{\"eventType\":\"OFFENDER_BELIEFS-$eventType\",\"eventDatetime\":\"2024-06-11T16:30:59\",\"offenderIdDisplay\":\"$prisonerNumber\",\"bookingId\":12345,\"rootOffenderId\":123,\"offenderBeliefId\":$beliefId,\"auditModuleName\":\"$auditModuleName\"}",
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
    beliefId: Long,
    eventType: String,
    auditModuleName: String? = null,
  ) = awsSqsCorePersonOffenderEventsClient.sendMessage(
    corePersonQueueOffenderEventsUrl,
    beliefsEvent(prisonerNumber, beliefId, eventType, auditModuleName),
  )
}
