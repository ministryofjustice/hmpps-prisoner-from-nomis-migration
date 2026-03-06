package uk.gov.justice.digital.hmpps.prisonerfromnomismigration.coreperson

import com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor
import com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor
import com.github.tomakehurst.wiremock.client.WireMock.putRequestedFor
import com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo
import com.github.tomakehurst.wiremock.client.WireMock.urlPathMatching
import kotlinx.coroutines.test.runTest
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.mockito.kotlin.check
import org.mockito.kotlin.eq
import org.mockito.kotlin.isNull
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.springframework.beans.factory.annotation.Autowired
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.coreperson.religion.ReligionsMappingApiMockServer
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.integration.SqsIntegrationTestBase
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.integration.sendMessage
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.wiremock.withRequestBodyJsonPath

class CorePersonSynchronisationBeliefsIntTest(
  @Autowired private val nomisApi: CorePersonNomisApiMockServer,
) : SqsIntegrationTestBase() {

  @Autowired
  private lateinit var mappingApiMock: ReligionsMappingApiMockServer

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
            eq("coreperson-beliefs-synchronisation-created-skipped"),
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
          nomisApi.stubGetOffenderReligions(prisonNumber = "A1234AA", religions = multipleBeliefs())
          mappingApiMock.stubGetReligionByNomisIdOrNull(nomisId = 2, nomisPrisonNumber = "A1234AA", mapping = null)
          cprApi.stubSyncCreateOffenderBelief("A1234AA")
          mappingApiMock.stubCreateReligionMapping()
        }

        @Nested
        inner class HappyPathNoFailures {
          @Test
          fun `should sync new belief to CPR`() = runTest {
            sendBeliefsEvent(prisonerNumber = "A1234AA", beliefId = 2, eventType = "INSERTED")
              .also { waitForAnyProcessingToComplete() }

            verifyNomis(offenderNo = "A1234AA")
            verifyMappingCheck(nomisId = 2)
            cprApi.verify(
              postRequestedFor(urlPathEqualTo("/person/prison/A1234AA/religion"))
                .withRequestBodyJsonPath("nomisReligionId", 2)
                .withRequestBodyJsonPath("current", false)
                .withRequestBodyJsonPath("religionCode", "DRU")
                .withRequestBodyJsonPath("changeReasonKnown", true)
                .withRequestBodyJsonPath("comments", "No longer believes in Zoroastrianism")
                .withRequestBodyJsonPath("verified", true)
                .withRequestBodyJsonPath("startDate", "2016-08-02")
                .withRequestBodyJsonPath("modifyUserId", "KOFEADDY")
                .withRequestBodyJsonPath("modifyDateTime", "2016-08-01T10:55:00"),
            )
            verifyMappingSaved()

            verifyTelemetry(
              "coreperson-beliefs-synchronisation-created-success",
              offenderNo = "A1234AA",
              nomisId = 2,
            )
          }
        }

        @Nested
        inner class HappyPathWithMappingFailures {
          @Nested
          inner class MappingFailures {

            @BeforeEach
            fun setUp() {
              nomisApi.stubGetOffenderReligions(prisonNumber = "A1234AA", religions = multipleBeliefs())
              mappingApiMock.stubGetReligionByNomisIdOrNull(nomisId = 1, nomisPrisonNumber = "A1234AA", mapping = null)
              cprApi.stubSyncCreateOffenderBelief("A1234AA")
            }

            @Nested
            inner class FailureAndRecovery {
              @BeforeEach
              fun setUp() {
                mappingApiMock.stubCreateReligionMappingFailureFollowedBySuccess()

                sendBeliefsEvent(prisonerNumber = "A1234AA", beliefId = 1, eventType = "INSERTED")
                  .also { waitForAnyProcessingToComplete("coreperson-beliefs-synchronisation-mapping-created") }

                verifyNomis(offenderNo = "A1234AA")
                verifyMappingCheck(nomisId = 1)
                verifyCpr()
              }

              @Test
              fun `will eventually create mapping after a retry`() {
                verifyMappingSaved(2)
              }

              @Test
              fun `will track telemetry`() {
                verify(telemetryClient).trackEvent(
                  eq("coreperson-beliefs-synchronisation-created-success"),
                  check {
                    assertThat(it["prisonNumber"]).isEqualTo("A1234AA")
                    assertThat(it["nomisId"]).isEqualTo("1")
                  },
                  isNull(),
                )
              }
            }
          }
        }
      }

      @Nested
      inner class UnHappyPath {
        @Nested
        inner class AlreadyExists {

          @BeforeEach
          fun setUp() {
            mappingApiMock.stubGetReligionByNomisIdOrNull(nomisId = 1, nomisPrisonNumber = "A1234AA")
            sendBeliefsEvent(prisonerNumber = "A1234AA", beliefId = 1, eventType = "INSERTED")
              .also { waitForAnyProcessingToComplete() }
          }

          @Test
          fun `will not call Nomis`() {
            verifyNomis("A1234AA", 0)
          }

          @Test
          fun `will not create the religion in CPR`() {
            verifyCpr(0)
          }

          @Test
          fun `will not attempt to create a mapping`() {
            verifyMappingSaved(0)
          }

          @Test
          fun `will track telemetry`() {
            verify(telemetryClient).trackEvent(
              eq("coreperson-beliefs-synchronisation-created-ignored"),
              check {
                assertThat(it["prisonNumber"]).isEqualTo("A1234AA")
                assertThat(it["nomisId"]).isEqualTo("1")
                assertThat(it["cprId"]).isEqualTo("123456")
              },
              isNull(),
            )
          }
        }
      }
    }

    @Nested
    @DisplayName("OFFENDER_BELIEFS-UPDATED")
    inner class OfficialBeliefsUpdated {
      @Nested
      inner class WhenUpdatedInDps {
        @BeforeEach
        fun setUp() {
          sendBeliefsEvent(
            prisonerNumber = "A1234AA",
            beliefId = 1,
            eventType = "UPDATED",
            auditModuleName = "DPS_SYNCHRONISATION",
          )
            .also { waitForAnyProcessingToComplete() }
        }

        @Test
        fun `will track telemetry`() {
          verify(telemetryClient).trackEvent(
            eq("coreperson-beliefs-synchronisation-updated-skipped"),
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
          nomisApi.stubGetOffenderReligions(prisonNumber = "A1234AA", religions = multipleBeliefs())
          mappingApiMock.stubGetReligionByNomisIdOrNull(nomisId = 2, nomisPrisonNumber = "A1234AA")
          cprApi.stubSyncUpdateOffenderBelief("A1234AA", "123456")
          sendBeliefsEvent(prisonerNumber = "A1234AA", beliefId = 2, eventType = "UPDATED")
            .also { waitForAnyProcessingToComplete("coreperson-beliefs-synchronisation-updated-success") }
        }

        @Test
        fun `should sync belief to CPR`() = runTest {
          verifyNomis(offenderNo = "A1234AA")
          verifyMappingCheck(nomisId = 2)
          cprApi.verify(
            putRequestedFor(urlPathEqualTo("/person/prison/A1234AA/religion/123456"))
              .withRequestBodyJsonPath("nomisReligionId", 2)
              .withRequestBodyJsonPath("current", false)
              .withRequestBodyJsonPath("comments", "No longer believes in Zoroastrianism")
              .withRequestBodyJsonPath("verified", true)
              .withRequestBodyJsonPath("modifyUserId", "KOFEADDY")
              .withRequestBodyJsonPath("modifyDateTime", "2016-08-01T10:55:00"),
          )
          verifyMappingSaved(count = 0)
        }

        @Test
        fun `will track telemetry`() {
          verify(telemetryClient).trackEvent(
            eq("coreperson-beliefs-synchronisation-updated-success"),
            check {
              assertThat(it["prisonNumber"]).isEqualTo("A1234AA")
              assertThat(it["nomisId"]).isEqualTo("2")
              assertThat(it["cprId"]).isEqualTo("123456")
            },
            isNull(),
          )
        }
      }
    }
  }

  private fun verifyNomis(offenderNo: String = "A1234AA", count: Int = 1) {
    nomisApi.verify(count, getRequestedFor(urlPathMatching("/core-person/$offenderNo/religions")))
  }
  private fun verifyCpr(count: Int = 1) {
    cprApi.verify(count, postRequestedFor(urlPathMatching("/person/prison/A1234AA/religion")))
  }
  private fun verifyMappingCheck(nomisId: Long = 1) {
    mappingApiMock.verify(getRequestedFor(urlPathMatching("/mapping/core-person-religion/religion/nomis-id/$nomisId")))
  }
  private fun verifyMappingSaved(count: Int = 1) {
    mappingApiMock.verify(count, postRequestedFor(urlPathMatching("/mapping/core-person-religion/religion")))
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
