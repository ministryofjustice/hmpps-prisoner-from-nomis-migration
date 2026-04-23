package uk.gov.justice.digital.hmpps.prisonerfromnomismigration.movements.taps

import com.github.tomakehurst.wiremock.client.WireMock.absent
import com.github.tomakehurst.wiremock.client.WireMock.anyUrl
import com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor
import com.github.tomakehurst.wiremock.client.WireMock.putRequestedFor
import com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.mockito.kotlin.check
import org.mockito.kotlin.eq
import org.mockito.kotlin.isNull
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.springframework.beans.factory.annotation.Autowired
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.integration.SqsIntegrationTestBase
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.integration.sendMessage
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.movements.ExternalMovementsMappingApiMockServer
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.movements.model.SyncResponse
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.movements.tapScheduleMapping
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.movements.taps.TapDpsApiExtension.Companion.dpsExtMovementsServer
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomismappings.model.TapScheduleMappingDto
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.wiremock.withRequestBodyJsonPath
import java.util.*

class TapAddressIntTest(
  @Autowired private val nomisApi: TapNomisApiMockServer,
  @Autowired private val mappingApi: ExternalMovementsMappingApiMockServer,
) : SqsIntegrationTestBase() {

  private val dpsApi = dpsExtMovementsServer

  @Nested
  inner class AddressUpdated {
    private inner class TestData(val dpsAuthorisationId: UUID, val nomisApplicationId: Long, val mapping: TapScheduleMappingDto)
    private lateinit var scheduleMappings: List<TestData>

    @Nested
    inner class HappyPath {

      @Nested
      inner class OffenderAddress {

        @BeforeEach
        fun setUp() {
          createStubs("OFF")

          sendMessage(tapAddressUpdatedEvent(321, "OFFENDER"))
            .also { waitForAnyProcessingToComplete("temporary-absence-sync-address-updated-success") }
        }

        @Test
        fun `should get mappings`() {
          mappingApi.verify(getRequestedFor(urlPathEqualTo("/mapping/taps/schedule/nomis-address-id/321")))
        }

        @Test
        fun `should get NOMIS scheduled movements`() {
          nomisApi.verify(getRequestedFor(urlPathEqualTo("/movements/A1234AA/taps/schedule/out/1")))
          nomisApi.verify(getRequestedFor(urlPathEqualTo("/movements/B1234BB/taps/schedule/out/2")))
        }

        @Test
        fun `should update DPS scheduled movement`() {
          dpsApi.verify(
            putRequestedFor(urlPathEqualTo("/sync/temporary-absence-authorisations/${scheduleMappings[0].dpsAuthorisationId}/occurrences"))
              .withRequestBodyJsonPath("id", "${scheduleMappings[0].mapping.dpsOccurrenceId}")
              .withRequestBodyJsonPath("legacyId", "1"),
          )
          dpsApi.verify(
            putRequestedFor(urlPathEqualTo("/sync/temporary-absence-authorisations/${scheduleMappings[1].dpsAuthorisationId}/occurrences"))
              .withRequestBodyJsonPath("id", "${scheduleMappings[1].mapping.dpsOccurrenceId}")
              .withRequestBodyJsonPath("legacyId", "2"),
          )
        }

        @Test
        fun `should update mappings`() {
          mappingApi.verify(
            putRequestedFor(urlPathEqualTo("/mapping/taps/schedule"))
              .withRequestBodyJsonPath("dpsOccurrenceId", "${scheduleMappings[0].mapping.dpsOccurrenceId}")
              .withRequestBodyJsonPath("nomisEventId", "1")
              .withRequestBodyJsonPath("dpsAddressText", "to full address"),
          )

          mappingApi.verify(
            putRequestedFor(urlPathEqualTo("/mapping/taps/schedule"))
              .withRequestBodyJsonPath("dpsOccurrenceId", "${scheduleMappings[1].mapping.dpsOccurrenceId}")
              .withRequestBodyJsonPath("nomisEventId", "2")
              .withRequestBodyJsonPath("dpsAddressText", "to full address"),
          )
        }

        @Test
        fun `should create success telemetry for address update`() {
          verify(telemetryClient).trackEvent(
            eq("temporary-absence-sync-address-updated-success"),
            check {
              assertThat(it["nomisAddressId"]).isEqualTo("321")
              assertThat(it["nomisAddressOwnerClass"]).isEqualTo("OFF")
              assertThat(it["nomisEventIds"]).isEqualTo("[1, 2]")
              assertThat(it["dpsOccurrenceIds"]).isEqualTo("${scheduleMappings.map { it.mapping.dpsOccurrenceId }}")
            },
            isNull(),
          )
        }

        @Test
        fun `should create success telemetry for schedule updates`() {
          scheduleMappings.forEach { testData ->
            verify(telemetryClient).trackEvent(
              eq("temporary-absence-sync-scheduled-movement-updated-success"),
              check {
                assertThat(it["offenderNo"]).isEqualTo(testData.mapping.prisonerNumber)
                assertThat(it["nomisApplicationId"]).isEqualTo("${testData.nomisApplicationId}")
                assertThat(it["nomisEventId"]).isEqualTo("${testData.mapping.nomisEventId}")
                assertThat(it["directionCode"]).isEqualTo("OUT")
                assertThat(it["dpsOccurrenceId"]).isEqualTo("${testData.mapping.dpsOccurrenceId}")
                assertThat(it["nomisAddressId"]).isEqualTo("321")
                assertThat(it["nomisAddressOwnerClass"]).isEqualTo("OFF")
              },
              isNull(),
            )
          }
        }
      }

      @Nested
      inner class CorporateAddress {

        @BeforeEach
        fun setUp() {
          createStubs("CORP")

          sendMessage(tapAddressUpdatedEvent(321, "CORPORATE"))
            .also { waitForAnyProcessingToComplete("temporary-absence-sync-address-updated-success") }
        }

        @Test
        fun `should update DPS scheduled movement`() {
          dpsApi.verify(putRequestedFor(urlPathEqualTo("/sync/temporary-absence-authorisations/${scheduleMappings[0].dpsAuthorisationId}/occurrences")))
          dpsApi.verify(putRequestedFor(urlPathEqualTo("/sync/temporary-absence-authorisations/${scheduleMappings[1].dpsAuthorisationId}/occurrences")))
        }

        @Test
        fun `should update mappings`() {
          mappingApi.verify(putRequestedFor(urlPathEqualTo("/mapping/taps/schedule")))
          mappingApi.verify(putRequestedFor(urlPathEqualTo("/mapping/taps/schedule")))
        }

        @Test
        fun `should create success telemetry for address updates`() {
          verify(telemetryClient).trackEvent(
            eq("temporary-absence-sync-address-updated-success"),
            check {
              assertThat(it["nomisAddressOwnerClass"]).isEqualTo("CORP")
            },
            isNull(),
          )
        }

        @Test
        fun `should create success telemetry for schedule updates`() {
          scheduleMappings.forEach { testData ->
            verify(telemetryClient).trackEvent(
              eq("temporary-absence-sync-scheduled-movement-updated-success"),
              check {
                assertThat(it["dpsOccurrenceId"]).isEqualTo("${testData.mapping.dpsOccurrenceId}")
                assertThat(it["nomisAddressOwnerClass"]).isEqualTo("CORP")
              },
              isNull(),
            )
          }
        }
      }

      @Nested
      inner class AgencyAddress {

        @BeforeEach
        fun setUp() {
          createStubs("AGY")

          sendMessage(tapAddressUpdatedEvent(321, "AGENCY"))
            .also { waitForAnyProcessingToComplete("temporary-absence-sync-address-updated-success") }
        }

        @Test
        fun `should update DPS scheduled movement`() {
          dpsApi.verify(putRequestedFor(urlPathEqualTo("/sync/temporary-absence-authorisations/${scheduleMappings[0].dpsAuthorisationId}/occurrences")))
          dpsApi.verify(putRequestedFor(urlPathEqualTo("/sync/temporary-absence-authorisations/${scheduleMappings[1].dpsAuthorisationId}/occurrences")))
        }

        @Test
        fun `should update mappings`() {
          mappingApi.verify(putRequestedFor(urlPathEqualTo("/mapping/taps/schedule")))
          mappingApi.verify(putRequestedFor(urlPathEqualTo("/mapping/taps/schedule")))
        }

        @Test
        fun `should create success telemetry for address updates`() {
          verify(telemetryClient).trackEvent(
            eq("temporary-absence-sync-address-updated-success"),
            check {
              assertThat(it["nomisAddressOwnerClass"]).isEqualTo("AGY")
            },
            isNull(),
          )
        }

        @Test
        fun `should create success telemetry for schedule updates`() {
          scheduleMappings.forEach { testData ->
            verify(
              telemetryClient,
            ).trackEvent(
              eq("temporary-absence-sync-scheduled-movement-updated-success"),
              check {
                assertThat(it["dpsOccurrenceId"]).isEqualTo("${testData.mapping.dpsOccurrenceId}")
                assertThat(it["nomisAddressOwnerClass"]).isEqualTo("AGY")
              },
              isNull(),
            )
          }
        }
      }

      @Nested
      inner class NomisAddressUpdated {

        @BeforeEach
        fun setUp() {
          createStubs("OFF", toAddress = "updated address")

          sendMessage(tapAddressUpdatedEvent(321, "OFFENDER"))
            .also { waitForAnyProcessingToComplete("temporary-absence-sync-address-updated-success") }
        }

        @Test
        fun `should update DPS scheduled movement`() {
          dpsApi.verify(
            putRequestedFor(urlPathEqualTo("/sync/temporary-absence-authorisations/${scheduleMappings[0].dpsAuthorisationId}/occurrences"))
              .withRequestBodyJsonPath("id", "${scheduleMappings[0].mapping.dpsOccurrenceId}")
              .withRequestBodyJsonPath("legacyId", "1")
              .withRequestBodyJsonPath("location.uprn", absent())
              .withRequestBodyJsonPath("location.address", "updated address"),
          )
          dpsApi.verify(
            putRequestedFor(urlPathEqualTo("/sync/temporary-absence-authorisations/${scheduleMappings[1].dpsAuthorisationId}/occurrences"))
              .withRequestBodyJsonPath("id", "${scheduleMappings[1].mapping.dpsOccurrenceId}")
              .withRequestBodyJsonPath("legacyId", "2")
              .withRequestBodyJsonPath("location.uprn", absent())
              .withRequestBodyJsonPath("location.address", "updated address"),
          )
        }

        @Test
        fun `should update mappings`() {
          mappingApi.verify(
            putRequestedFor(urlPathEqualTo("/mapping/taps/schedule"))
              .withRequestBodyJsonPath("dpsOccurrenceId", "${scheduleMappings[0].mapping.dpsOccurrenceId}")
              .withRequestBodyJsonPath("nomisEventId", "1")
              .withRequestBodyJsonPath("dpsAddressText", "updated address")
              .withRequestBodyJsonPath("dpsUprn", absent()),
          )

          mappingApi.verify(
            putRequestedFor(urlPathEqualTo("/mapping/taps/schedule"))
              .withRequestBodyJsonPath("dpsOccurrenceId", "${scheduleMappings[1].mapping.dpsOccurrenceId}")
              .withRequestBodyJsonPath("nomisEventId", "2")
              .withRequestBodyJsonPath("dpsAddressText", "updated address")
              .withRequestBodyJsonPath("dpsUprn", absent()),
          )
        }

        @Test
        fun `should create success telemetry for schedule updates`() {
          scheduleMappings.forEach { testData ->
            verify(telemetryClient).trackEvent(
              eq("temporary-absence-sync-scheduled-movement-updated-success"),
              check {
                assertThat(it["offenderNo"]).isEqualTo(testData.mapping.prisonerNumber)
                assertThat(it["nomisApplicationId"]).isEqualTo("${testData.nomisApplicationId}")
                assertThat(it["nomisEventId"]).isEqualTo("${testData.mapping.nomisEventId}")
                assertThat(it["directionCode"]).isEqualTo("OUT")
                assertThat(it["dpsOccurrenceId"]).isEqualTo("${testData.mapping.dpsOccurrenceId}")
                assertThat(it["nomisAddressId"]).isEqualTo("321")
                assertThat(it["nomisAddressOwnerClass"]).isEqualTo("OFF")
              },
              isNull(),
            )
          }
        }
      }
    }

    @Nested
    inner class WhenScheduleCompleted {

      @BeforeEach
      fun setUp() {
        createStubs("OFF")
        scheduleMappings.forEach {
          nomisApi.stubGetTapScheduleOut(
            offenderNo = it.mapping.prisonerNumber,
            eventId = it.mapping.nomisEventId,
            applicationId = it.nomisApplicationId,
            addressOwnerClass = "OFF",
            eventStatus = "COMP",
          )
        }

        sendMessage(tapAddressUpdatedEvent(321, "OFFENDER"))
          .also { waitForAnyProcessingToComplete("temporary-absence-sync-address-updated-success") }
      }

      @Test
      fun `should NOT update DPS scheduled movement`() {
        dpsApi.verify(0, putRequestedFor(anyUrl()))
      }

      @Test
      fun `should NOT update mappings`() {
        mappingApi.verify(0, putRequestedFor(urlPathEqualTo("/mapping/taps/schedule")))
      }

      @Test
      fun `should create success telemetry for address update`() {
        verify(telemetryClient).trackEvent(
          eq("temporary-absence-sync-address-updated-success"),
          check {
            assertThat(it["nomisAddressId"]).isEqualTo("321")
            assertThat(it["nomisAddressOwnerClass"]).isEqualTo("OFF")
            assertThat(it["nomisEventIds"]).isEqualTo("[1, 2]")
            assertThat(it["dpsOccurrenceIds"]).isEqualTo("${scheduleMappings.map { it.mapping.dpsOccurrenceId }}")
          },
          isNull(),
        )
      }

      @Test
      fun `should included ignored on sync telemetry`() {
        verify(telemetryClient, times(2))
          .trackEvent(
            eq("temporary-absence-sync-scheduled-movement-updated-success"),
            check {
              assertThat(it["ignored"]).isEqualTo("true")
            },
            isNull(),
          )
      }
    }

    @Nested
    inner class WhenDpsUpdateFails {
      @BeforeEach
      fun setUp() {
        createStubs("OFF")
        dpsApi.stubSyncTapOccurrenceError(authorisationId = scheduleMappings[0].dpsAuthorisationId, status = 500)

        sendMessage(tapAddressUpdatedEvent(321, "OFFENDER"))
          .also { waitForAnyProcessingToComplete("temporary-absence-sync-address-updated-error") }
      }

      @Test
      fun `should try to update only first DPS scheduled movement`() {
        dpsApi.verify(
          putRequestedFor(urlPathEqualTo("/sync/temporary-absence-authorisations/${scheduleMappings[0].dpsAuthorisationId}/occurrences"))
            .withRequestBodyJsonPath("id", "${scheduleMappings[0].mapping.dpsOccurrenceId}"),
        )
        dpsApi.verify(
          0,
          putRequestedFor(urlPathEqualTo("/sync/temporary-absence-authorisations/${scheduleMappings[1].dpsAuthorisationId}/occurrences"))
            .withRequestBodyJsonPath("id", "${scheduleMappings[1].mapping.dpsOccurrenceId}"),
        )
      }

      @Test
      fun `should create error telemetry for sync`() {
        verify(telemetryClient).trackEvent(
          eq("temporary-absence-sync-scheduled-movement-updated-error"),
          check {
            assertThat(it["offenderNo"]).isEqualTo(scheduleMappings[0].mapping.prisonerNumber)
            assertThat(it["nomisApplicationId"]).isEqualTo("${scheduleMappings[0].nomisApplicationId}")
            assertThat(it["dpsAuthorisationId"]).isEqualTo("${scheduleMappings[0].dpsAuthorisationId}")
            assertThat(it["nomisEventId"]).isEqualTo("${scheduleMappings[0].mapping.nomisEventId}")
            assertThat(it["directionCode"]).isEqualTo("OUT")
            assertThat(it["error"]).isEqualTo("500 Internal Server Error from PUT http://localhost:8103/sync/temporary-absence-authorisations/${scheduleMappings[0].dpsAuthorisationId}/occurrences")
          },
          isNull(),
        )
      }

      @Test
      fun `should create error telemetry for address update`() {
        verify(telemetryClient).trackEvent(
          eq("temporary-absence-sync-address-updated-error"),
          check {
            assertThat(it["nomisAddressId"]).isEqualTo("321")
            assertThat(it["nomisAddressOwnerClass"]).isEqualTo("OFF")
            assertThat(it["nomisEventIds"]).isEqualTo("[1, 2]")
            assertThat(it["dpsOccurrenceIds"]).isEqualTo("${scheduleMappings.map { it.mapping.dpsOccurrenceId }}")
            assertThat(it["error"]).isEqualTo("500 Internal Server Error from PUT http://localhost:8103/sync/temporary-absence-authorisations/${scheduleMappings[0].dpsAuthorisationId}/occurrences")
          },
          isNull(),
        )
      }
    }

    @Nested
    inner class WhenMappingUpdateFailsOnce {
      @BeforeEach
      fun setUp() {
        createStubs("OFF", mappings = 1)
        mappingApi.stubUpdateTapScheduleMappingFailureFollowedBySuccess()

        sendMessage(tapAddressUpdatedEvent(321, "OFFENDER"))
          .also { waitForAnyProcessingToComplete("temporary-absence-sync-scheduled-movement-mapping-retry-updated") }
      }

      @Test
      fun `should update DPS scheduled movement`() {
        dpsApi.verify(
          putRequestedFor(urlPathEqualTo("/sync/temporary-absence-authorisations/${scheduleMappings[0].dpsAuthorisationId}/occurrences"))
            .withRequestBodyJsonPath("id", "${scheduleMappings[0].mapping.dpsOccurrenceId}")
            .withRequestBodyJsonPath("location.address", "to full address"),
        )
      }

      @Test
      fun `should update mapping`() {
        mappingApi.verify(
          2,
          putRequestedFor(urlPathEqualTo("/mapping/taps/schedule"))
            .withRequestBodyJsonPath("dpsOccurrenceId", "${scheduleMappings[0].mapping.dpsOccurrenceId}")
            .withRequestBodyJsonPath("nomisEventId", "1")
            .withRequestBodyJsonPath("dpsAddressText", "to full address"),
        )
      }

      @Test
      fun `should create success telemetry for address update`() {
        verify(telemetryClient).trackEvent(
          eq("temporary-absence-sync-address-updated-success"),
          check {
            assertThat(it["nomisAddressId"]).isEqualTo("321")
            assertThat(it["nomisAddressOwnerClass"]).isEqualTo("OFF")
            assertThat(it["nomisEventIds"]).isEqualTo("[1]")
            assertThat(it["dpsOccurrenceIds"]).isEqualTo("${scheduleMappings.map { it.mapping.dpsOccurrenceId }}")
          },
          isNull(),
        )
      }

      @Test
      fun `should create success telemetry for schedule updates`() {
        with(scheduleMappings[0]) {
          verify(telemetryClient).trackEvent(
            eq("temporary-absence-sync-scheduled-movement-updated-success"),
            check {
              assertThat(it["offenderNo"]).isEqualTo(mapping.prisonerNumber)
              assertThat(it["nomisApplicationId"]).isEqualTo("$nomisApplicationId")
              assertThat(it["nomisEventId"]).isEqualTo("${mapping.nomisEventId}")
              assertThat(it["directionCode"]).isEqualTo("OUT")
              assertThat(it["dpsOccurrenceId"]).isEqualTo("${mapping.dpsOccurrenceId}")
              assertThat(it["nomisAddressId"]).isEqualTo("321")
              assertThat(it["nomisAddressOwnerClass"]).isEqualTo("OFF")
            },
            isNull(),
          )
        }
      }
    }

    private fun createStubs(addressOwnerClass: String, mappings: Int = 2, toAddress: String = "to full address") {
      scheduleMappings = listOf(
        TestData(
          UUID.randomUUID(),
          111,
          tapScheduleMapping(1L, "A1234AA", UUID.randomUUID(), nomisAddressOwnerClass = addressOwnerClass),
        ),
        TestData(
          UUID.randomUUID(),
          222,
          tapScheduleMapping(2L, "B1234BB", UUID.randomUUID(), nomisAddressOwnerClass = addressOwnerClass),
        ),
      ).take(mappings)
      mappingApi.stubFindScheduledMovementsForAddressMappings(321, scheduleMappings.map { it.mapping })
      scheduleMappings.forEach {
        mappingApi.stubGetTapApplicationMapping(it.nomisApplicationId, it.dpsAuthorisationId)
        nomisApi.stubGetTapScheduleOut(
          offenderNo = it.mapping.prisonerNumber,
          eventId = it.mapping.nomisEventId,
          applicationId = it.nomisApplicationId,
          addressOwnerClass = addressOwnerClass,
          eventStatus = "SCH",
          toAddress = toAddress,
        )
        dpsApi.stubSyncTapOccurrence(
          authorisationId = it.dpsAuthorisationId,
          response = SyncResponse(it.mapping.dpsOccurrenceId),
        )
      }
      mappingApi.stubUpdateTapScheduleMapping()
    }
  }

  private fun sendMessage(event: String) = awsSqsExternalMovementsOffenderEventsClient.sendMessage(
    externalMovementsQueueOffenderEventsUrl,
    event,
  )

  private fun tapAddressUpdatedEvent(
    addressId: Long = 123L,
    addressType: String = "OFFENDER",
  ): String {
    val idField = when (addressType) {
      "OFFENDER" -> """\"offenderId\":348250"""
      "CORPORATE" -> """\"corporateId\":87663"""
      "AGENCY" -> """\"agencyCode\":\"HALWD\""""
      else -> throw IllegalStateException("unknown address type: $addressType")
    }
    // language=JSON
    return """{
          "Type" : "Notification",
          "MessageId" : "d6c0d1af-7f49-5f42-b1d0-b0d5f7c1983c",
          "TopicArn" : "arn:aws:sns:eu-west-2:754256621582:cloud-platform-Digital-Prison-Services-160f3055cc4e04c4105ee85f2ed1fccb",
          "Message" : "{\"eventType\":\"ADDRESSES_$addressType-UPDATED\",\"eventDatetime\":\"2025-10-28T09:00:56\",\"nomisEventType\":\"ADDRESSES_$addressType-UPDATED\",\"auditModuleName\":\"OUMAGENC\",$idField,\"addressId\":$addressId}",
          "Timestamp" : "2025-10-28T09:00:56.300Z",
          "SignatureVersion" : "1",
          "Signature" : "XrlCPnj/Vj137LXUb3nvveGcnkRF3OWJQhDi4czTRKMYVgRidZmJTiS3xPumWwsNYH2RwrRLP2Ghuqoyk3X8k1X+lRfb2Z2PksEsdS6EaqQG9Aqa+QAF6G6TqPJPHK8ghhLod9nY2bEZdKgBBxWXstw2M2u+NQgSHr6bWtKnRpMq7whIka7Dd8mIQ4op+0S5xe/glso+pPIr1cIp0mKtWfrNXNOFp/V4LBkJJwqr6P31honkRDiTZF6I3k52YxmIO0hRL2HF+J7Edw7wIZYpwbKb/kmzt+9HKeyAQmF3fLZYTm4SIkqu0TtKuGPxFaODmnm3WSadwkYHsfvesLWtbw==",
          "SigningCertURL" : "https://sns.eu-west-2.amazonaws.com/SimpleNotificationService-6209c161c6221fdf56ec1eb5c821d112.pem",
          "UnsubscribeURL" : "https://sns.eu-west-2.amazonaws.com/?Action=Unsubscribe&SubscriptionArn=arn:aws:sns:eu-west-2:754256621582:cloud-platform-Digital-Prison-Services-160f3055cc4e04c4105ee85f2ed1fccb:763eba96-d154-4a47-b9a1-22b957832b57",
          "MessageAttributes" : {
              "publishedAt" : {"Type":"String","Value":"2025-10-28T09:00:56.296568943Z"},
              "traceparent" : {"Type":"String","Value":"00-2231237d40643c5334f02ee002e61e86-47b44dc460337f0b-01"},
              "eventType" : {"Type":"String","Value":"ADDRESSES_$addressType-UPDATED"}
            }
          }
    """.trimMargin()
  }
}
