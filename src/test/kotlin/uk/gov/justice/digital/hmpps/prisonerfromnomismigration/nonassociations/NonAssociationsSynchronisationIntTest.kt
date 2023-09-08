package uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nonassociations

import com.github.tomakehurst.wiremock.client.WireMock.anyRequestedFor
import com.github.tomakehurst.wiremock.client.WireMock.anyUrl
import com.github.tomakehurst.wiremock.client.WireMock.deleteRequestedFor
import com.github.tomakehurst.wiremock.client.WireMock.equalTo
import com.github.tomakehurst.wiremock.client.WireMock.exactly
import com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor
import com.github.tomakehurst.wiremock.client.WireMock.matchingJsonPath
import com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor
import com.github.tomakehurst.wiremock.client.WireMock.putRequestedFor
import com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo
import com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo
import org.assertj.core.api.Assertions.assertThat
import org.awaitility.kotlin.await
import org.awaitility.kotlin.matches
import org.awaitility.kotlin.untilAsserted
import org.awaitility.kotlin.untilCallTo
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.mockito.Mockito.eq
import org.mockito.internal.verification.Times
import org.mockito.kotlin.any
import org.mockito.kotlin.check
import org.mockito.kotlin.isNull
import org.mockito.kotlin.verify
import software.amazon.awssdk.services.sqs.SqsAsyncClient
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.integration.SqsIntegrationTestBase
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.integration.sendMessage
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.wiremock.MappingApiExtension.Companion.NON_ASSOCIATIONS_CREATE_MAPPING_URL
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.wiremock.MappingApiExtension.Companion.mappingApi
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.wiremock.NomisApiExtension.Companion.nomisApi
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.wiremock.NonAssociationsApiExtension.Companion.nonAssociationsApi
import uk.gov.justice.hmpps.sqs.countAllMessagesOnQueue

private const val NON_ASSOCIATION_ID = 4321L
private const val OFFENDER_A = "A1234BC"
private const val OFFENDER_B = "D5678EF"
private const val TYPE_SEQUENCE = 1
private const val nomisApiUrl = "/non-associations/offender/$OFFENDER_A/ns-offender/$OFFENDER_B?typeSequence=$TYPE_SEQUENCE"
private const val nomisMappingApiUrl = "/mapping/non-associations/first-offender-no/$OFFENDER_A/second-offender-no/$OFFENDER_B/type-sequence/$TYPE_SEQUENCE"

class NonAssociationsSynchronisationIntTest : SqsIntegrationTestBase() {

  @Nested
  @DisplayName("NON_ASSOCIATION_DETAIL-UPSERTED")
  inner class NonAssociationDetailUpserted {

    @Nested
    inner class WhenCreateByDPS {
      @BeforeEach
      fun setUp() {
        awsSqsNonAssociationsOffenderEventsClient.sendMessage(
          nonAssociationsQueueOffenderEventsUrl,
          nonAssociationEvent(auditModuleName = "DPS_SYNCHRONISATION"),
        )
      }

      @Test
      fun `the event is ignored`() {
        await untilAsserted {
          verify(telemetryClient).trackEvent(
            eq("non-association-synchronisation-skipped"),
            check {
              assertThat(it["firstOffenderNo"]).isEqualTo(OFFENDER_A)
              assertThat(it["secondOffenderNo"]).isEqualTo(OFFENDER_B)
              assertThat(it["typeSequence"]).isEqualTo("$TYPE_SEQUENCE")
              assertThat(it["nonAssociationId"]).isNull()
            },
            isNull(),
          )
        }
        nomisApi.verify(exactly(0), getRequestedFor(anyUrl()))
        mappingApi.verify(exactly(0), getRequestedFor(anyUrl()))
        nonAssociationsApi.verify(exactly(0), anyRequestedFor(anyUrl()))
      }
    }

    @Nested
    @DisplayName("When there is a new Non-association")
    inner class WhenNewNonAssociation {

      @Nested
      inner class WhenCreateByNomisSuccess {
        @BeforeEach
        fun setUp() {
          nomisApi.stubGetNonAssociation(offenderNo = OFFENDER_A, nsOffenderNo = OFFENDER_B, typeSequence = TYPE_SEQUENCE)
          mappingApi.stubGetNonAssociationNotFound()
          nonAssociationsApi.stubUpsertNonAssociationForSynchronisation(firstOffenderNo = OFFENDER_A, secondOffenderNo = OFFENDER_B)
          mappingApi.stubMappingCreate(NON_ASSOCIATIONS_CREATE_MAPPING_URL)

          awsSqsNonAssociationsOffenderEventsClient.sendMessage(
            nonAssociationsQueueOffenderEventsUrl,
            nonAssociationEvent(),
          )
        }

        @Test
        fun `will retrieve details about the non-association from NOMIS`() {
          await untilAsserted {
            nomisApi.verify(getRequestedFor(urlEqualTo(nomisApiUrl)))
          }
        }

        @Test
        fun `will retrieve mapping to check if this is a new non-association`() {
          await untilAsserted {
            mappingApi.verify(getRequestedFor(urlPathEqualTo(nomisMappingApiUrl)))
          }
        }

        @Test
        fun `will create the non-association in the non-associations service`() {
          await untilAsserted {
            nonAssociationsApi.verify(putRequestedFor(urlPathEqualTo("/sync/upsert")))
          }
        }

        @Test
        fun `will create a mapping between the two records`() {
          await untilAsserted {
            mappingApi.verify(
              postRequestedFor(urlPathEqualTo("/mapping/non-associations"))
                .withRequestBody(matchingJsonPath("nonAssociationId", equalTo("$NON_ASSOCIATION_ID")))
                .withRequestBody(matchingJsonPath("firstOffenderNo", equalTo(OFFENDER_A)))
                .withRequestBody(matchingJsonPath("secondOffenderNo", equalTo(OFFENDER_B)))
                .withRequestBody(matchingJsonPath("nomisTypeSequence", equalTo("$TYPE_SEQUENCE"))),
            )
          }
        }

        @Test
        fun `will create telemetry tracking the create`() {
          await untilAsserted {
            verify(telemetryClient).trackEvent(
              eq("non-association-created-synchronisation-success"),
              check {
                assertThat(it["nonAssociationId"]).isEqualTo("$NON_ASSOCIATION_ID")
                assertThat(it["firstOffenderNo"]).isEqualTo(OFFENDER_A)
                assertThat(it["secondOffenderNo"]).isEqualTo(OFFENDER_B)
                assertThat(it["typeSequence"]).isEqualTo("$TYPE_SEQUENCE")
              },
              isNull(),
            )
          }
        }
      }

      @Nested
      inner class WhenCreateByNomisWithMinimalDataSuccess {
        @BeforeEach
        fun setUp() {
          nomisApi.stubGetNonAssociationWithMinimalData(offenderNo = OFFENDER_A, nsOffenderNo = OFFENDER_B, typeSequence = TYPE_SEQUENCE)
          mappingApi.stubGetNonAssociationNotFound()
          nonAssociationsApi.stubUpsertNonAssociationForSynchronisation(firstOffenderNo = OFFENDER_A, secondOffenderNo = OFFENDER_B)
          mappingApi.stubMappingCreate(NON_ASSOCIATIONS_CREATE_MAPPING_URL)

          awsSqsNonAssociationsOffenderEventsClient.sendMessage(
            nonAssociationsQueueOffenderEventsUrl,
            nonAssociationEvent(),
          )
        }

        @Test
        fun `will retrieve details about the non-association from NOMIS`() {
          await untilAsserted {
            nomisApi.verify(getRequestedFor(urlEqualTo(nomisApiUrl)))
          }
        }

        @Test
        fun `will retrieve mapping to check if this is a new non-association`() {
          await untilAsserted {
            mappingApi.verify(getRequestedFor(urlPathEqualTo(nomisMappingApiUrl)))
          }
        }

        @Test
        fun `will create the non-association in the non-associations service`() {
          await untilAsserted {
            nonAssociationsApi.verify(putRequestedFor(urlPathEqualTo("/sync/upsert")))
          }
        }

        @Test
        fun `will create a mapping between the two records`() {
          await untilAsserted {
            mappingApi.verify(
              postRequestedFor(urlPathEqualTo("/mapping/non-associations"))
                .withRequestBody(matchingJsonPath("nonAssociationId", equalTo("$NON_ASSOCIATION_ID")))
                .withRequestBody(matchingJsonPath("firstOffenderNo", equalTo(OFFENDER_A)))
                .withRequestBody(matchingJsonPath("secondOffenderNo", equalTo(OFFENDER_B)))
                .withRequestBody(matchingJsonPath("nomisTypeSequence", equalTo("$TYPE_SEQUENCE"))),
            )
          }
        }

        @Test
        fun `will create telemetry tracking the create`() {
          await untilAsserted {
            verify(telemetryClient).trackEvent(
              eq("non-association-created-synchronisation-success"),
              check {
                assertThat(it["nonAssociationId"]).isEqualTo("$NON_ASSOCIATION_ID")
                assertThat(it["firstOffenderNo"]).isEqualTo(OFFENDER_A)
                assertThat(it["secondOffenderNo"]).isEqualTo(OFFENDER_B)
                assertThat(it["typeSequence"]).isEqualTo("$TYPE_SEQUENCE")
              },
              isNull(),
            )
          }
        }
      }

      @Nested
      inner class WhenOffenderOrderingInNonAssociationNotAlphabetical {
        @BeforeEach
        fun setUp() {
          awsSqsNonAssociationsOffenderEventsClient.sendMessage(
            nonAssociationsQueueOffenderEventsUrl,
            nonAssociationEvent(
              offenderIdDisplay = OFFENDER_B,
              nsOffenderIdDisplay = OFFENDER_A,
            ),
          )
          await untilAsserted {
            verify(telemetryClient, Times(1)).trackEvent(any(), any(), isNull())
          }
        }

        @Test
        fun `will not retrieve details about the non-association from NOMIS`() {
          nomisApi.verify(exactly(0), getRequestedFor((anyUrl())))
        }

        @Test
        fun `will not attempt to get mapping data`() {
          mappingApi.verify(exactly(0), getRequestedFor(anyUrl()))
        }

        @Test
        fun `will not create the non-association in the non-associations service`() {
          nonAssociationsApi.verify(exactly(0), anyRequestedFor(anyUrl()))
        }

        @Test
        fun `will not create telemetry tracking success`() {
          verify(telemetryClient, Times(0)).trackEvent(
            eq("non-association-created-synchronisation-success"),
            any(),
            isNull(),
          )
        }

        @Test
        fun `will create telemetry tracking the non-primary ignore`() {
          verify(telemetryClient).trackEvent(
            eq("non-association-synchronisation-non-primary-skipped"),
            check {
              assertThat(it["firstOffenderNo"]).isEqualTo(OFFENDER_B)
              assertThat(it["secondOffenderNo"]).isEqualTo(OFFENDER_A)
              assertThat(it["typeSequence"]).isEqualTo("$TYPE_SEQUENCE")
            },
            isNull(),
          )
        }
      }

      @Nested
      inner class WhenTwoRelatedNonAssociationsReceived {
        @BeforeEach
        fun setUp() {
          nomisApi.stubGetNonAssociation(offenderNo = OFFENDER_A, nsOffenderNo = OFFENDER_B, typeSequence = TYPE_SEQUENCE)
          mappingApi.stubGetNonAssociationNotFound()
          mappingApi.stubGetNonAssociationNotFound(firstOffenderNo = OFFENDER_B, secondOffenderNo = OFFENDER_A)
          nonAssociationsApi.stubUpsertNonAssociationForSynchronisation(firstOffenderNo = OFFENDER_A, secondOffenderNo = OFFENDER_B)
          mappingApi.stubMappingCreate(NON_ASSOCIATIONS_CREATE_MAPPING_URL)

          awsSqsNonAssociationsOffenderEventsClient.sendMessage(
            nonAssociationsQueueOffenderEventsUrl,
            nonAssociationEvent(),
          )
          awsSqsNonAssociationsOffenderEventsClient.sendMessage(
            nonAssociationsQueueOffenderEventsUrl,
            nonAssociationEvent(
              offenderIdDisplay = OFFENDER_B,
              nsOffenderIdDisplay = OFFENDER_A,
            ),
          )
          awsSqsNonAssociationsOffenderEventsClient.waitForMessageCountOnQueue(nonAssociationsQueueOffenderEventsUrl, 0)
        }

        @Test
        fun `will retrieve details about one non-association from NOMIS`() {
          await untilAsserted {
            nomisApi.verify(exactly(1), getRequestedFor(urlEqualTo(nomisApiUrl)))
          }
        }

        @Test
        fun `will create a mapping between the two records`() {
          await untilAsserted {
            mappingApi.verify(
              postRequestedFor(urlPathEqualTo("/mapping/non-associations"))
                .withRequestBody(matchingJsonPath("nonAssociationId", equalTo("$NON_ASSOCIATION_ID")))
                .withRequestBody(matchingJsonPath("firstOffenderNo", equalTo(OFFENDER_A)))
                .withRequestBody(matchingJsonPath("secondOffenderNo", equalTo(OFFENDER_B)))
                .withRequestBody(matchingJsonPath("nomisTypeSequence", equalTo("$TYPE_SEQUENCE"))),
            )
          }
        }

        @Test
        fun `will create one non-association in the non-associations service`() {
          await untilAsserted {
            nonAssociationsApi.verify(exactly(1), putRequestedFor(urlPathEqualTo("/sync/upsert")))
          }
        }

        @Test
        fun `will create one telemetry tracking the create`() {
          await untilAsserted {
            verify(telemetryClient).trackEvent(
              eq("non-association-created-synchronisation-success"),
              check {
                assertThat(it["nonAssociationId"]).isEqualTo("$NON_ASSOCIATION_ID")
                assertThat(it["firstOffenderNo"]).isEqualTo(OFFENDER_A)
                assertThat(it["secondOffenderNo"]).isEqualTo(OFFENDER_B)
                assertThat(it["typeSequence"]).isEqualTo("$TYPE_SEQUENCE")
              },
              isNull(),
            )
          }
          verify(telemetryClient, Times(0)).trackEvent(
            eq("non-association-created-synchronisation-success"),
            check {
              assertThat(it["nonAssociationId"]).isEqualTo("$NON_ASSOCIATION_ID")
              assertThat(it["firstOffenderNo"]).isEqualTo(OFFENDER_B)
              assertThat(it["secondOffenderNo"]).isEqualTo(OFFENDER_A)
              assertThat(it["typeSequence"]).isEqualTo("$TYPE_SEQUENCE")
            },
            isNull(),
          )
        }

        @Test
        fun `will create telemetry tracking the non-primary ignore`() {
          await untilAsserted {
            verify(telemetryClient).trackEvent(
              eq("non-association-synchronisation-non-primary-skipped"),
              check {
                assertThat(it["firstOffenderNo"]).isEqualTo(OFFENDER_B)
                assertThat(it["secondOffenderNo"]).isEqualTo(OFFENDER_A)
                assertThat(it["typeSequence"]).isEqualTo(TYPE_SEQUENCE.toString())
              },
              isNull(),
            )
          }
        }
      }

      @Nested
      inner class WhenNomisHasNoNonAssociation {
        @BeforeEach
        fun setUp() {
          nomisApi.stubGetNonAssociationNotFound(offenderNo = OFFENDER_A, nsOffenderNo = OFFENDER_B, typeSequence = TYPE_SEQUENCE)

          awsSqsNonAssociationsOffenderEventsClient.sendMessage(
            nonAssociationsQueueOffenderEventsUrl,
            nonAssociationEvent(),
          )
          awsSqsNonAssociationsOffenderEventDlqClient.waitForMessageCountOnQueue(nonAssociationsQueueOffenderEventsDlqUrl, 1)
        }

        @Test
        fun `will not create the non-association in the non-associations service`() {
          nonAssociationsApi.verify(exactly(0), anyRequestedFor(anyUrl()))
        }

        @Test
        fun `will not attempt to get mapping data`() {
          mappingApi.verify(exactly(0), getRequestedFor(anyUrl()))
        }

        @Test
        fun `will not create telemetry tracking`() {
          verify(telemetryClient, Times(0)).trackEvent(any(), any(), isNull())
        }
      }

      @Nested
      inner class WhenUpdateByNomisSuccess {
        @BeforeEach
        fun setUp() {
          nomisApi.stubGetNonAssociation(offenderNo = OFFENDER_A, nsOffenderNo = OFFENDER_B, typeSequence = TYPE_SEQUENCE)
          mappingApi.stubGetNonAssociation(firstOffenderNo = OFFENDER_A, secondOffenderNo = OFFENDER_B)
          nonAssociationsApi.stubUpsertNonAssociationForSynchronisation(firstOffenderNo = OFFENDER_A, secondOffenderNo = OFFENDER_B)

          awsSqsNonAssociationsOffenderEventsClient.sendMessage(
            nonAssociationsQueueOffenderEventsUrl,
            nonAssociationEvent(),
          )
        }

        @Test
        fun `will retrieve details about the non-association from NOMIS`() {
          await untilAsserted {
            nomisApi.verify(getRequestedFor(urlEqualTo(nomisApiUrl)))
          }
        }

        @Test
        fun `will retrieve mapping to check if this is a new non-association`() {
          await untilAsserted {
            mappingApi.verify(getRequestedFor(urlPathEqualTo(nomisMappingApiUrl)))
          }
        }

        @Test
        fun `will send the update to the non-association in the non-associations service`() {
          await untilAsserted {
            nonAssociationsApi.verify(putRequestedFor(urlPathEqualTo("/sync/upsert")))
          }
        }

        @Test
        fun `will not add a new mapping between the two records`() {
          await untilAsserted {
            verify(telemetryClient, Times(1)).trackEvent(any(), any(), isNull())
            mappingApi.verify(exactly(0), postRequestedFor(anyUrl()))
          }
        }

        @Test
        fun `will create telemetry tracking the create`() {
          await untilAsserted {
            verify(telemetryClient).trackEvent(
              eq("non-association-updated-synchronisation-success"),
              check {
                assertThat(it["nonAssociationId"]).isEqualTo("$NON_ASSOCIATION_ID")
                assertThat(it["firstOffenderNo"]).isEqualTo(OFFENDER_A)
                assertThat(it["secondOffenderNo"]).isEqualTo(OFFENDER_B)
                assertThat(it["typeSequence"]).isEqualTo("$TYPE_SEQUENCE")
              },
              isNull(),
            )
          }
        }
      }

      @Nested
      inner class WhenDuplicateMapping {

        private val duplicationNonAssociationId = 1234L

        @Test
        internal fun `it will not retry after a 409 (duplicate non-association written to Non-association API)`() {
          nomisApi.stubGetNonAssociation(offenderNo = OFFENDER_A, nsOffenderNo = OFFENDER_B, typeSequence = TYPE_SEQUENCE)
          mappingApi.stubGetNonAssociationNotFound()
          mappingApi.stubGetNonAssociationNotFound(firstOffenderNo = OFFENDER_B, secondOffenderNo = OFFENDER_A)
          nonAssociationsApi.stubUpsertNonAssociationForSynchronisation(nonAssociationId = duplicationNonAssociationId, firstOffenderNo = OFFENDER_A, secondOffenderNo = OFFENDER_B)
          mappingApi.stubNonAssociationMappingCreateConflict(
            nonAssociationId = NON_ASSOCIATION_ID,
            duplicateNonAssociationId = duplicationNonAssociationId,
          )

          awsSqsNonAssociationsOffenderEventsClient.sendMessage(
            nonAssociationsQueueOffenderEventsUrl,
            nonAssociationEvent(),
          )

          // wait for all mappings to be created before verifying
          await untilCallTo { mappingApi.createMappingCount(NON_ASSOCIATIONS_CREATE_MAPPING_URL) } matches { it == 1 }

          // check that one non-association is created
          assertThat(nonAssociationsApi.createNonAssociationSynchronisationCount()).isEqualTo(1)

          // doesn't retry
          mappingApi.verifyCreateMappingNonAssociation(arrayOf(duplicationNonAssociationId), times = 1)

          verify(telemetryClient).trackEvent(
            eq("from-nomis-synch-non-association-duplicate"),
            check {
              assertThat(it["duplicateNonAssociationId"]).isEqualTo("$duplicationNonAssociationId")
              assertThat(it["duplicateFirstOffenderNo"]).isEqualTo("A1234BC")
              assertThat(it["duplicateSecondOffenderNo"]).isEqualTo("D5678EF")
              assertThat(it["duplicateNomisTypeSequence"]).isEqualTo("2")
              assertThat(it["existingNonAssociationId"]).isEqualTo("$NON_ASSOCIATION_ID")
              assertThat(it["existingFirstOffenderNo"]).isEqualTo("A1234BC")
              assertThat(it["existingSecondOffenderNo"]).isEqualTo("D5678EF")
              assertThat(it["existingNomisTypeSequence"]).isEqualTo("2")
              assertThat(it["migrationId"]).isNull()
            },
            isNull(),
          )
        }
      }
    }
  }

  @Nested
  @DisplayName("NON_ASSOCIATION_DETAIL-DELETED")
  inner class NonAssociationDetailDeleted {

    @Nested
    inner class WhenDeleteByDPS {
      @BeforeEach
      fun setUp() {
        awsSqsNonAssociationsOffenderEventsClient.sendMessage(
          nonAssociationsQueueOffenderEventsUrl,
          nonAssociationEvent(
            eventType = "NON_ASSOCIATION_DETAIL-DELETED",
            auditModuleName = "DPS_SYNCHRONISATION",
          ),
        )
      }

      @Test
      fun `the event is ignored`() {
        await untilAsserted {
          verify(telemetryClient).trackEvent(
            eq("non-association-delete-synchronisation-skipped"),
            check {
              assertThat(it["firstOffenderNo"]).isEqualTo(OFFENDER_A)
              assertThat(it["secondOffenderNo"]).isEqualTo(OFFENDER_B)
            },
            isNull(),
          )
        }
        nomisApi.verify(exactly(0), anyRequestedFor(anyUrl()))
        mappingApi.verify(exactly(0), getRequestedFor(anyUrl()))
        nonAssociationsApi.verify(exactly(0), anyRequestedFor(anyUrl()))
      }
    }

    @Nested
    @DisplayName("When there is a delete Non-association")
    inner class WhenDeleteByNomis {

      @Nested
      inner class WhenDeleteByNomisSuccess {
        @BeforeEach
        fun setUp() {
          mappingApi.stubGetNonAssociation(OFFENDER_A, OFFENDER_B, TYPE_SEQUENCE)
          nonAssociationsApi.stubDeleteNonAssociationForSynchronisation(NON_ASSOCIATION_ID)
          mappingApi.stubNonAssociationMappingDelete(NON_ASSOCIATION_ID)

          awsSqsNonAssociationsOffenderEventsClient.sendMessage(
            nonAssociationsQueueOffenderEventsUrl,
            nonAssociationEvent(
              eventType = "NON_ASSOCIATION_DETAIL-DELETED",
              auditModuleName = "OIUDNONA",
            ),
          )
        }

        @Test
        fun `will retrieve mapping`() {
          await untilAsserted {
            mappingApi.verify(getRequestedFor(urlPathEqualTo(nomisMappingApiUrl)))
          }
        }

        @Test
        fun `will delete the non-association in the non-associations service`() {
          await untilAsserted {
            nonAssociationsApi.verify(deleteRequestedFor(urlPathEqualTo("/sync/delete/$NON_ASSOCIATION_ID")))
          }
        }

        @Test
        fun `will delete mapping`() {
          await untilAsserted {
            mappingApi.verify(deleteRequestedFor(urlPathEqualTo("/mapping/non-associations/non-association-id/$NON_ASSOCIATION_ID")))
          }
        }

        @Test
        fun `will create telemetry tracking the delete`() {
          await untilAsserted {
            verify(telemetryClient).trackEvent(
              eq("non-association-delete-synchronisation-success"),
              check {
                assertThat(it["firstOffenderNo"]).isEqualTo(OFFENDER_A)
                assertThat(it["secondOffenderNo"]).isEqualTo(OFFENDER_B)
                assertThat(it["typeSequence"]).isEqualTo("$TYPE_SEQUENCE")
              },
              isNull(),
            )
          }
        }
      }

      @Nested
      inner class WhenDeleteByNomisWithNoMapping {
        @BeforeEach
        fun setUp() {
          mappingApi.stubGetNonAssociationNotFound()

          awsSqsNonAssociationsOffenderEventsClient.sendMessage(
            nonAssociationsQueueOffenderEventsUrl,
            nonAssociationEvent(
              eventType = "NON_ASSOCIATION_DETAIL-DELETED",
              auditModuleName = "OIUDNONA",
            ),
          )
          await untilAsserted {
            verify(telemetryClient, Times(1)).trackEvent(any(), any(), isNull())
          }
        }

        @Test
        fun `will attempt to retrieve mapping`() {
          mappingApi.verify(getRequestedFor(urlPathEqualTo(nomisMappingApiUrl)))
        }

        @Test
        fun `will not delete the non-association in the non-associations service`() {
          nonAssociationsApi.verify(exactly(0), deleteRequestedFor(anyUrl()))
        }

        @Test
        fun `will not attempt to delete a mapping`() {
          mappingApi.verify(exactly(0), deleteRequestedFor(anyUrl()))
        }

        @Test
        fun `will create telemetry tracking the ignored delete`() {
          verify(telemetryClient).trackEvent(
            eq("non-association-delete-synchronisation-ignored"),
            check {
              assertThat(it["firstOffenderNo"]).isEqualTo(OFFENDER_A)
              assertThat(it["secondOffenderNo"]).isEqualTo(OFFENDER_B)
              assertThat(it["typeSequence"]).isEqualTo("$TYPE_SEQUENCE")
            },
            isNull(),
          )
        }
      }

      @Nested
      inner class WhenOffenderOrderingInNonAssociationNotAlphabetical {
        @BeforeEach
        fun setUp() {
          awsSqsNonAssociationsOffenderEventsClient.sendMessage(
            nonAssociationsQueueOffenderEventsUrl,
            nonAssociationEvent(
              eventType = "NON_ASSOCIATION_DETAIL-DELETED",
              auditModuleName = "OIUDNONA",
              offenderIdDisplay = OFFENDER_B,
              nsOffenderIdDisplay = OFFENDER_A,
            ),
          )
          await untilAsserted {
            verify(telemetryClient, Times(1)).trackEvent(any(), any(), isNull())
          }
        }

        @Test
        fun `will not attempt to get mapping data`() {
          mappingApi.verify(exactly(0), getRequestedFor(anyUrl()))
        }

        @Test
        fun `will not call the non-associations service`() {
          nonAssociationsApi.verify(exactly(0), anyRequestedFor(anyUrl()))
        }

        @Test
        fun `will create telemetry tracking the non-primary ignore`() {
          verify(telemetryClient).trackEvent(
            eq("non-association-delete-synchronisation-non-primary-skipped"),
            check {
              assertThat(it["firstOffenderNo"]).isEqualTo(OFFENDER_B)
              assertThat(it["secondOffenderNo"]).isEqualTo(OFFENDER_A)
              assertThat(it["typeSequence"]).isEqualTo("$TYPE_SEQUENCE")
            },
            isNull(),
          )
        }
      }
    }
  }
}

fun SqsAsyncClient.waitForMessageCountOnQueue(queueUrl: String, messageCount: Int) =
  await untilCallTo {
    countAllMessagesOnQueue(queueUrl)
      .get()
  } matches { it == messageCount }

fun nonAssociationEvent(
  eventType: String = "NON_ASSOCIATION_DETAIL-UPSERTED",
  offenderIdDisplay: String = OFFENDER_A,
  nsOffenderIdDisplay: String = OFFENDER_B,
  auditModuleName: String = "OIDONONA",
) = """{
    "Type" : "Notification",
    "MessageId" : "be8e7273-0446-5590-8c7f-2f24e966322e",
    "TopicArn" : "arn:aws:sns:eu-west-2:754256621582:cloud-platform-Digital-Prison-Services-f221e27fcfcf78f6ab4f4c3cc165eee7",
    "Message" : "{\"eventType\":\"$eventType\",\"eventDatetime\":\"2023-08-17T10:39:44\",\"bookingId\":1201882,\"offenderIdDisplay\":\"$offenderIdDisplay\",\"nomisEventType\":\"OFF_NA_DETAILS_ASSOC-UPDATED\",\"nsOffenderIdDisplay\":\"$nsOffenderIdDisplay\",\"nsBookingId\":1202145,\"reasonCode\":\"NOT_REL\",\"nsType\":\"CELL\",\"typeSeq\":1,\"effectiveDate\":\"2023-08-17\",\"expiryDate\":\"2026-12-20\",\"authorisedBy\":\"Manager\",\"comment\":\"Test comment\",\"auditModuleName\":\"$auditModuleName\"}",
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
