package uk.gov.justice.digital.hmpps.prisonerfromnomismigration.movements

import com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor
import com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor
import com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo
import com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo
import kotlinx.coroutines.test.runTest
import org.assertj.core.api.Assertions.assertThat
import org.awaitility.kotlin.atMost
import org.awaitility.kotlin.await
import org.awaitility.kotlin.untilAsserted
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.check
import org.mockito.kotlin.eq
import org.mockito.kotlin.isNull
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.http.HttpStatus.NOT_FOUND
import org.springframework.http.MediaType
import org.springframework.test.web.reactive.server.returnResult
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.helper.MigrationResult
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.integration.SqsIntegrationTestBase
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomismappings.model.DuplicateErrorContentObject
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomismappings.model.DuplicateMappingErrorResponse
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomismappings.model.DuplicateMappingErrorResponse.Status._409_CONFLICT
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.persistence.repository.MigrationHistoryRepository
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.wiremock.NomisApiExtension.Companion.nomisApi
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.wiremock.withRequestBodyJsonPath
import java.time.Duration

class ExternalMovementsMigrationIntTest(
  @Autowired private val migrationHistoryRepository: MigrationHistoryRepository,
  @Autowired private val externalMovementsNomisApi: ExternalMovementsNomisApiMockServer,
  @Autowired private val mappingApi: ExternalMovementsMappingApiMockServer,
) : SqsIntegrationTestBase() {

  private lateinit var migrationId: String

  @BeforeEach
  fun deleteHistoryRecords() = runTest {
    migrationHistoryRepository.deleteAll()
  }

  @Nested
  inner class Security {
    @Test
    fun `access forbidden when no role`() {
      webTestClient.post().uri("/migrate/external-movements")
        .headers(setAuthorisation(roles = listOf()))
        .contentType(MediaType.APPLICATION_JSON)
        .bodyValue(ExternalMovementsMigrationFilter())
        .exchange()
        .expectStatus().isForbidden
    }

    @Test
    fun `access forbidden with wrong role`() {
      webTestClient.post().uri("/migrate/external-movements")
        .headers(setAuthorisation(roles = listOf("ROLE_BANANAS")))
        .contentType(MediaType.APPLICATION_JSON)
        .bodyValue(ExternalMovementsMigrationFilter())
        .exchange()
        .expectStatus().isForbidden
    }

    @Test
    fun `access unauthorised with no auth token`() {
      webTestClient.post().uri("/migrate/external-movements")
        .contentType(MediaType.APPLICATION_JSON)
        .bodyValue(ExternalMovementsMigrationFilter())
        .exchange()
        .expectStatus().isUnauthorized
    }
  }

  private fun stubMigrationDependencies(entities: Int = 2) {
    nomisApi.stubGetPrisonerIds(totalElements = entities.toLong(), pageSize = 10, firstOffenderNo = "A0001KT")
    mappingApi.stubCreateTemporaryAbsenceMapping()
    (1..entities)
      .map { index -> "A%04dKT".format(index) }
      .forEach { prisonerNumber ->
        mappingApi.stubGetTemporaryAbsenceMappings(prisonerNumber, NOT_FOUND)
        externalMovementsNomisApi.stubGetTemporaryAbsences(prisonerNumber)
      }
  }

  @Nested
  inner class HappyPath {
    @BeforeEach
    fun setUp() = runTest {
      stubMigrationDependencies()
      migrationId = performMigration()
    }

    @Test
    fun `will request all prisoner ids`() {
      nomisApi.verify(getRequestedFor(urlPathEqualTo("/prisoners/ids/all")))
    }

    @Test
    fun `will request temporary absences for each prisoner`() {
      externalMovementsNomisApi.verifyGetTemporaryAbsences(offenderNo = "A0001KT")
      externalMovementsNomisApi.verifyGetTemporaryAbsences(offenderNo = "A0002KT")
    }

    @Test
    fun `will check mappings`() {
      mappingApi.verify(
        getRequestedFor(urlEqualTo("/mapping/temporary-absences/nomis-prisoner-number/A0001KT")),
      )
      mappingApi.verify(
        getRequestedFor(urlEqualTo("/mapping/temporary-absences/nomis-prisoner-number/A0002KT")),
      )
    }

    @Test
    fun `will create mappings`() {
      mappingApi.verify(
        postRequestedFor(urlEqualTo("/mapping/temporary-absences"))
          .withRequestBodyJsonPath("prisonerNumber", "A0001KT")
          .withRequestBodyJsonPath("migrationId", migrationId),
      )
      mappingApi.verify(
        postRequestedFor(urlEqualTo("/mapping/temporary-absences"))
          .withRequestBodyJsonPath("prisonerNumber", "A0002KT")
          .withRequestBodyJsonPath("migrationId", migrationId),
      )
    }

    @Test
    fun `will publish telemetry`() {
      verify(telemetryClient).trackEvent(
        eq("temporary-absences-migration-entity-migrated"),
        check {
          assertThat(it["offenderNo"]).isEqualTo("A0001KT")
          assertThat(it["migrationId"]).isEqualTo(migrationId)
        },
        isNull(),
      )
      verify(telemetryClient).trackEvent(
        eq("temporary-absences-migration-entity-migrated"),
        check {
          assertThat(it["offenderNo"]).isEqualTo("A0002KT")
          assertThat(it["migrationId"]).isEqualTo(migrationId)
        },
        isNull(),
      )
    }
  }

  @Nested
  inner class MappingErrorRecovery {
    @BeforeEach
    fun setUp() = runTest {
      stubMigrationDependencies(1)
      mappingApi.stubCreateTemporaryAbsenceMappingFailureFollowedBySuccess()
      migrationId = performMigration()
    }

    @Test
    fun `will request temporary absences only once`() {
      externalMovementsNomisApi.verifyGetTemporaryAbsences(offenderNo = "A0001KT")
    }

    @Test
    fun `will create mappings twice before succeeding`() {
      mappingApi.verify(
        2,
        postRequestedFor(urlEqualTo("/mapping/temporary-absences"))
          .withRequestBodyJsonPath("prisonerNumber", "A0001KT")
          .withRequestBodyJsonPath("migrationId", migrationId),
      )
    }

    @Test
    fun `will publish telemetry once`() {
      verify(telemetryClient, times(1)).trackEvent(
        eq("temporary-absences-migration-entity-migrated"),
        check {
          assertThat(it["offenderNo"]).isEqualTo("A0001KT")
          assertThat(it["migrationId"]).isEqualTo(migrationId)
        },
        isNull(),
      )
    }
  }

  @Nested
  inner class DuplicateCreateMappingError {
    @BeforeEach
    fun setUp() = runTest {
      stubMigrationDependencies(1)
      mappingApi.stubCreateTemporaryAbsenceMapping(
        error = DuplicateMappingErrorResponse(
          moreInfo = DuplicateErrorContentObject(
            duplicate = temporaryAbsencePrisonerMappings(),
            existing = temporaryAbsencePrisonerMappings(),
          ),
          errorCode = 1409,
          status = _409_CONFLICT,
          userMessage = "Duplicate mapping",
        ),
      )

      migrationId = performMigration()
    }

    @Test
    fun `will request temporary absences`() {
      externalMovementsNomisApi.verifyGetTemporaryAbsences(offenderNo = "A0001KT")
    }

    @Test
    fun `will check mappings`() {
      mappingApi.verify(
        getRequestedFor(urlEqualTo("/mapping/temporary-absences/nomis-prisoner-number/A0001KT")),
      )
    }

    @Test
    fun `will attempt to create mappings`() {
      mappingApi.verify(
        postRequestedFor(urlEqualTo("/mapping/temporary-absences")),
      )
    }

    @Test
    fun `will publish telemetry`() {
      verify(telemetryClient, times(1)).trackEvent(
        eq("temporary-absences-migration-entity-duplicate"),
        check {
          assertThat(it["offenderNo"]).isEqualTo("A0001KT")
          assertThat(it["migrationId"]).isEqualTo(migrationId)
        },
        isNull(),
      )
    }
  }

  @Nested
  inner class AlreadyMigrated {
    @BeforeEach
    fun setUp() = runTest {
      stubMigrationDependencies(1)
      mappingApi.stubGetTemporaryAbsenceMappings("A0001KT")
      migrationId = performMigration()
    }

    @Test
    fun `will not request temporary absences`() {
      externalMovementsNomisApi.verifyGetTemporaryAbsences(count = 0, offenderNo = "A0001KT")
    }

    @Test
    fun `will check mappings`() {
      mappingApi.verify(
        getRequestedFor(urlEqualTo("/mapping/temporary-absences/nomis-prisoner-number/A0001KT")),
      )
    }

    @Test
    fun `will NOT create mappings`() {
      mappingApi.verify(
        0,
        postRequestedFor(urlEqualTo("/mapping/temporary-absences")),
      )
    }

    @Test
    fun `will publish telemetry`() {
      verify(telemetryClient, times(1)).trackEvent(
        eq("temporary-absences-migration-entity-ignored"),
        check {
          assertThat(it["offenderNo"]).isEqualTo("A0001KT")
          assertThat(it["migrationId"]).isEqualTo(migrationId)
          assertThat(it["reason"]).isEqualTo("Already migrated")
        },
        isNull(),
      )
    }
  }

  private fun performMigration(prisonerNumber: String? = null): String = webTestClient.post()
    .uri("/migrate/external-movements")
    .headers(setAuthorisation(roles = listOf("ROLE_MIGRATE_EXTERNAL_MOVEMENTS")))
    .contentType(MediaType.APPLICATION_JSON)
    .apply { prisonerNumber?.let { bodyValue("""{"prisonerNumber":"$prisonerNumber"}""") } ?: bodyValue("{}") }
    .exchange()
    .expectStatus().isAccepted
    .returnResult<MigrationResult>().responseBody.blockFirst()!!
    .migrationId
    .also {
      waitUntilCompleted()
    }

  private fun waitUntilCompleted() = await atMost Duration.ofSeconds(60) untilAsserted {
    verify(telemetryClient).trackEvent(
      eq("temporary-absences-migration-completed"),
      any(),
      isNull(),
    )
  }
}
