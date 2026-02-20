package uk.gov.justice.digital.hmpps.prisonerfromnomismigration.csra

import com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor
import com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo
import kotlinx.coroutines.runBlocking
import org.assertj.core.api.Assertions.assertThat
import org.awaitility.kotlin.atMost
import org.awaitility.kotlin.await
import org.awaitility.kotlin.matches
import org.awaitility.kotlin.untilAsserted
import org.awaitility.kotlin.untilCallTo
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.atLeast
import org.mockito.kotlin.check
import org.mockito.kotlin.eq
import org.mockito.kotlin.isNull
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.http.MediaType
import org.springframework.test.web.reactive.server.returnResult
import software.amazon.awssdk.services.sqs.SqsAsyncClient
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.csra.CsraApiExtension.Companion.csraApi
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.csra.CsraApiMockServer.Companion.WIREMOCK_PORT
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.helper.MigrationResult
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.integration.SqsIntegrationTestBase
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.persistence.repository.MigrationHistoryRepository
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.wiremock.NomisApiExtension.Companion.nomisApi
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.wiremock.withRequestBodyJsonPath
import uk.gov.justice.hmpps.sqs.countMessagesOnQueue
import java.time.Duration

private const val OFFENDER_NUMBER1 = "A0001KT"
private const val OFFENDER_NUMBER2 = "A0002KT"

class CsraMigrationIntTest : SqsIntegrationTestBase() {

  private val csraQueueMigrationUrl by lazy { csraMigrationQueue.queueUrl }
  private val csraQueueMigrationDlqUrl by lazy { csraMigrationQueue.dlqUrl as String }
  private val awsSqsCsraMigrationClient by lazy { csraMigrationQueue.sqsClient }
  private val awsSqsCsraMigrationDlqClient by lazy { csraMigrationQueue.sqsDlqClient as SqsAsyncClient }

  @Autowired
  private lateinit var csrasNomisApiMockServer: CsraNomisApiMockServer

  @Autowired
  private lateinit var csrasMappingApiMockServer: CsraMappingApiMockServer

  @Autowired
  private lateinit var migrationHistoryRepository: MigrationHistoryRepository

  @BeforeEach
  internal fun deleteHistoryRecords() {
    runBlocking {
      migrationHistoryRepository.deleteAll()
    }
  }

  @Nested
  @DisplayName("POST /migrate/csras")
  inner class MigrateDpsCsra {
    @Nested
    inner class Security {
      @Test
      fun `access forbidden when no role`() {
        webTestClient.post().uri("/migrate/csras")
          .headers(setAuthorisation(roles = listOf()))
          .contentType(MediaType.APPLICATION_JSON)
          .bodyValue(PrisonerMigrationFilter())
          .exchange()
          .expectStatus().isForbidden
      }

      @Test
      fun `access forbidden with wrong role`() {
        webTestClient.post().uri("/migrate/csras")
          .headers(setAuthorisation(roles = listOf("ROLE_BANANAS")))
          .contentType(MediaType.APPLICATION_JSON)
          .bodyValue(PrisonerMigrationFilter())
          .exchange()
          .expectStatus().isForbidden
      }

      @Test
      fun `access unauthorised with no auth token`() {
        webTestClient.post().uri("/migrate/csras")
          .contentType(MediaType.APPLICATION_JSON)
          .bodyValue(PrisonerMigrationFilter())
          .exchange()
          .expectStatus().isUnauthorized
      }
    }

    @Nested
    inner class HappyPath {
      private lateinit var migrationResult: MigrationResult

      @BeforeEach
      fun setUp() {
        nomisApi.stubGetPrisonerIds(
          totalElements = 2,
          pageSize = 10,
          firstOffenderNo = OFFENDER_NUMBER1,
        )
        csrasNomisApiMockServer.stubGetCsraForPrisoner(
          offenderNo = OFFENDER_NUMBER1,
          bookingId = 1,
          currentCsraStart = 1,
          currentCsraCount = 1,
        )
        csrasNomisApiMockServer.stubGetCsraForPrisoner(
          offenderNo = OFFENDER_NUMBER2,
          bookingId = 2,
          currentCsraStart = 2,
          currentCsraCount = 1,
        )
        csraApi.stubMigrateCsras(
          OFFENDER_NUMBER1,
          listOf(
            MigrationResult(
              dpsCsraId = "00000000-0000-0000-0000-000000000001",
              nomisBookingId = 1,
              nomisSequence = 1,
            ),
          ),
        )
        csraApi.stubMigrateCsras(
          OFFENDER_NUMBER2,
          listOf(
            MigrationResult(
              dpsCsraId = "00000000-0000-0000-0000-000000000002",
              nomisBookingId = 2,
              nomisSequence = 2,
            ),
          ),
        )
        // csrasMappingApiMockServer.stubGetMappings(listOf())
        csrasMappingApiMockServer.stubPostMapping(OFFENDER_NUMBER1)
        csrasMappingApiMockServer.stubPostMapping(OFFENDER_NUMBER2)
        // csrasMappingApiMockServer.stubMigrationCount(recordsMigrated = 2)
        migrationResult = performMigration()
      }

      @Test
      fun `will migrate all prisoner's csras`() {
        verify(telemetryClient, times(2)).trackEvent(
          eq("csras-migration-entity-migrated"),
          any(),
          isNull(),
        )
        verify(telemetryClient).trackEvent(
          eq("csras-migration-entity-migrated"),
          check { assertThat(it).containsEntry("offenderNo", "A0001KT") },
          isNull(),
        )
        verify(telemetryClient).trackEvent(
          eq("csras-migration-entity-migrated"),
          check { assertThat(it).containsEntry("offenderNo", "A0002KT") },
          isNull(),
        )
      }

      @Test
      fun `will POST all csras to DPS for each prisoner`() {
        csraApi.verify(
          postRequestedFor(urlPathEqualTo("/csras/migrate/${OFFENDER_NUMBER1}"))
            .withRequestBodyJsonPath("$[0].bookingId", 1)
            .withRequestBodyJsonPath("$[0].sequenceNumber", 1)
            .withRequestBodyJsonPath("$[0].assessmentDate", "2021-02-03")
            .withRequestBodyJsonPath("$[0].assessmentType", "CSR")
            .withRequestBodyJsonPath("$[0].calculatedLevel", "STANDARD")
            .withRequestBodyJsonPath("$[0].score", "1001")
            .withRequestBodyJsonPath("$[0].status", "A")
            .withRequestBodyJsonPath("$[0].staffId", "1001")
            .withRequestBodyJsonPath("$[0].committeeCode", "GOV")
            .withRequestBodyJsonPath("$[0].nextReviewDate", "2021-02-03")
            .withRequestBodyJsonPath("$[0].comment", "comment")
            .withRequestBodyJsonPath("$[0].placementPrisonId", "placementAgencyId")
            .withRequestBodyJsonPath("$[0].createdDateTime", "2024-11-03T04:05:06")
            .withRequestBodyJsonPath("$[0].createdBy", "me")
            .withRequestBodyJsonPath("$[0].reviewLevel", "LOW")
            .withRequestBodyJsonPath("$[0].approvedLevel", "MED")
            .withRequestBodyJsonPath("$[0].evaluationDate", "2021-02-03")
            .withRequestBodyJsonPath("$[0].evaluationResultCode", "APP")
            .withRequestBodyJsonPath("$[0].reviewCommitteeCode", "SECSTATE")
            .withRequestBodyJsonPath("$[0].reviewCommitteeComment", "reviewCommitteeComment")
            .withRequestBodyJsonPath("$[0].reviewPlacementPrisonId", "reviewPlacementAgencyId")
            .withRequestBodyJsonPath("$[0].reviewComment", "reviewComment")
            .withRequestBodyJsonPath("$[0].reviewDetails[0].code", "CODE1")
            .withRequestBodyJsonPath("$[0].reviewDetails[0].description", "section description")
            .withRequestBodyJsonPath("$[0].reviewDetails[0].questions[0].code", "CODE2")
            .withRequestBodyJsonPath("$[0].reviewDetails[0].questions[0].description", "question description")
            .withRequestBodyJsonPath("$[0].reviewDetails[0].questions[0].responses[0].code", "CODE3")
            .withRequestBodyJsonPath("$[0].reviewDetails[0].questions[0].responses[0].answer", "answer")
            .withRequestBodyJsonPath("$[0].reviewDetails[0].questions[0].responses[0].comment", "response comment"),
        )
        csraApi.verify(
          postRequestedFor(urlPathEqualTo("/csras/migrate/${OFFENDER_NUMBER2}"))
            .withRequestBodyJsonPath("$[0].bookingId", 2)
            .withRequestBodyJsonPath("$[0].sequenceNumber", 2)
            .withRequestBodyJsonPath("$[0].assessmentDate", "2021-02-03")
            .withRequestBodyJsonPath("$[0].assessmentType", "CSR")
            .withRequestBodyJsonPath("$[0].calculatedLevel", "STANDARD")
            .withRequestBodyJsonPath("$[0].score", 1001)
            .withRequestBodyJsonPath("$[0].status", "A")
            .withRequestBodyJsonPath("$[0].staffId", 1001)
            .withRequestBodyJsonPath("$[0].committeeCode", "GOV")
            .withRequestBodyJsonPath("$[0].nextReviewDate", "2021-02-03")
            .withRequestBodyJsonPath("$[0].comment", "comment")
            .withRequestBodyJsonPath("$[0].placementPrisonId", "placementAgencyId")
            .withRequestBodyJsonPath("$[0].createdDateTime", "2024-11-03T04:05:06")
            .withRequestBodyJsonPath("$[0].createdBy", "me")
            .withRequestBodyJsonPath("$[0].reviewLevel", "LOW")
            .withRequestBodyJsonPath("$[0].approvedLevel", "MED")
            .withRequestBodyJsonPath("$[0].evaluationDate", "2021-02-03")
            .withRequestBodyJsonPath("$[0].evaluationResultCode", "APP")
            .withRequestBodyJsonPath("$[0].reviewCommitteeCode", "SECSTATE")
            .withRequestBodyJsonPath("$[0].reviewCommitteeComment", "reviewCommitteeComment")
            .withRequestBodyJsonPath("$[0].reviewPlacementPrisonId", "reviewPlacementAgencyId")
            .withRequestBodyJsonPath("$[0].reviewComment", "reviewComment")
            .withRequestBodyJsonPath("$[0].reviewDetails[0].code", "CODE1")
            .withRequestBodyJsonPath("$[0].reviewDetails[0].description", "section description")
            .withRequestBodyJsonPath("$[0].reviewDetails[0].questions[0].code", "CODE2")
            .withRequestBodyJsonPath("$[0].reviewDetails[0].questions[0].description", "question description")
            .withRequestBodyJsonPath("$[0].reviewDetails[0].questions[0].responses[0].code", "CODE3")
            .withRequestBodyJsonPath("$[0].reviewDetails[0].questions[0].responses[0].answer", "answer")
            .withRequestBodyJsonPath("$[0].reviewDetails[0].questions[0].responses[0].comment", "response comment"),
        )
      }

      @Test
      fun `will POST mappings for csras created for each prisoner`() {
        csrasMappingApiMockServer.verify(postRequestedFor(urlPathEqualTo("/mapping/csras/${OFFENDER_NUMBER1}/all")))
        csrasMappingApiMockServer.verify(postRequestedFor(urlPathEqualTo("/mapping/csras/${OFFENDER_NUMBER2}/all")))
      }

      @Test
      @Disabled("Count not implemented yet")
      fun `will record the number of prisoners migrated`() {
        // csrasMappingApiMockServer.stubGetCount(2)

        webTestClient.get().uri("/migrate/csras/history/${migrationResult.migrationId}")
          .headers(setAuthorisation(roles = listOf("ROLE_PRISONER_FROM_NOMIS__MIGRATION__RW")))
          .header("Content-Type", "application/json")
          .exchange()
          .expectStatus().isOk
          .expectBody()
          .jsonPath("$.migrationId").isEqualTo(migrationResult.migrationId)
          .jsonPath("$.status").isEqualTo("COMPLETED")
          .jsonPath("$.recordsMigrated").isEqualTo("2")
      }
    }

    @Nested
    inner class ErrorRecovery {
      @BeforeEach
      fun setUp() {
        nomisApi.stubGetPrisonerIds(
          totalElements = 1,
          pageSize = 10,
          firstOffenderNo = OFFENDER_NUMBER1,
        )
        csrasNomisApiMockServer.stubGetCsraForPrisoner(
          offenderNo = OFFENDER_NUMBER1,
          currentCsraCount = 1,
        )
        csraApi.stubMigrateCsras(
          OFFENDER_NUMBER1,
          listOf(
            MigrationResult(
              dpsCsraId = "00000000-0000-0000-0000-000000000001",
              nomisBookingId = 1,
              nomisSequence = 1,
            ),
          ),
        )
        csrasMappingApiMockServer.stubPostMappingFailureFollowedBySuccess(OFFENDER_NUMBER1)
        performMigration()
      }

      @Test
      fun `will POST the csras to DPS only once`() {
        csraApi.verify(
          1,
          postRequestedFor(urlPathEqualTo("/csras/migrate/${OFFENDER_NUMBER1}")),
        )
      }

      @Test
      fun `will POST mappings for csras twice due to the single error`() {
        csrasMappingApiMockServer.verify(
          2,
          postRequestedFor(urlPathEqualTo("/mapping/csras/${OFFENDER_NUMBER1}/all")),
        )
      }
    }

    @Nested
    inner class ErrorDpsFailure {
      @BeforeEach
      fun setUp() {
        nomisApi.stubGetPrisonerIds(
          totalElements = 1,
          pageSize = 10,
          firstOffenderNo = OFFENDER_NUMBER1,
        )
        csrasNomisApiMockServer.stubGetCsraForPrisoner(
          offenderNo = OFFENDER_NUMBER1,
          currentCsraCount = 1,
        )
        csraApi.stubMigrateCsras(OFFENDER_NUMBER1, 400)
        performMigration()
      }

      @Test
      fun `will POST the csras to DPS twice as per dlqMaxReceiveCount, but not mappings`() {
        await untilAsserted {
          csraApi.verify(
            2,
            postRequestedFor(urlPathEqualTo("/csras/migrate/${OFFENDER_NUMBER1}")),
          )
          csrasMappingApiMockServer.verify(
            0,
            postRequestedFor(urlPathEqualTo("/mapping/csras/${OFFENDER_NUMBER1}/all")),
          )
        }
      }

      @Test
      fun `failure telemetry is posted`() {
        verify(telemetryClient, atLeast(2)).trackEvent(
          eq("csras-migration-entity-failed"),
          check {
            assertThat(it).containsEntry(
              "offenderNo",
              OFFENDER_NUMBER1,
            )
            assertThat(it).containsEntry(
              "error",
              "400 Bad Request from POST http://localhost:$WIREMOCK_PORT/csras/migrate/$OFFENDER_NUMBER1",
            )
          },
          isNull(),
        )
      }

      @Test
      fun `message ends up on the dead letter queue`() {
        await untilCallTo {
          awsSqsCsraMigrationDlqClient.countMessagesOnQueue(csraQueueMigrationDlqUrl).get()
        } matches { it == 1 }
      }
    }
  }

  private fun performMigration(body: PrisonerMigrationFilter = PrisonerMigrationFilter()): MigrationResult = webTestClient.post()
    .uri("/migrate/csras")
    .headers(setAuthorisation(roles = listOf("ROLE_PRISONER_FROM_NOMIS__MIGRATION__RW")))
    .contentType(MediaType.APPLICATION_JSON)
    .bodyValue(body)
    .exchange()
    .expectStatus().isAccepted.returnResult<MigrationResult>().responseBody.blockFirst()!!
    .also {
      waitUntilCompleted()
    }

  private fun waitUntilCompleted() = await atMost Duration.ofSeconds(60) untilAsserted {
    verify(telemetryClient).trackEvent(
      eq("csras-migration-completed"),
      any(),
      isNull(),
    )
  }
}
