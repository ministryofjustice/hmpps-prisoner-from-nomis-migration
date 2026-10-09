package uk.gov.justice.digital.hmpps.prisonerfromnomismigration.movements.personlocation

import kotlinx.coroutines.runBlocking
import org.assertj.core.api.Assertions.assertThat
import org.awaitility.kotlin.atMost
import org.awaitility.kotlin.await
import org.awaitility.kotlin.untilAsserted
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.eq
import org.mockito.kotlin.isNull
import org.mockito.kotlin.reset
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.verifyBlocking
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.http.MediaType
import org.springframework.test.web.reactive.server.returnResult
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.data.MigrationContext
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.helper.MigrationResult
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomisprisoner.model.PrisonNumberAndRootOffenderId
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.persistence.repository.MigrationHistoryRepository
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.wiremock.MappingApiExtension
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.wiremock.NomisApiExtension
import java.time.Duration

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class PersonLocationMigrationIntTest(
  @Autowired private val migrationHistoryRepository: MigrationHistoryRepository,
) : PersonLocationIntegrationTestBase() {

  private val nomisApi = NomisApiExtension.nomisApi

  override fun resetTelemetryClient() {}

  internal fun setupMigrationTest() = runBlocking {
    migrationHistoryRepository.deleteAll()

    NomisApiExtension.resetAndDisableResetBeforeEach()
    MappingApiExtension.resetAndDisableResetBeforeEach()
    PersonLocationDpsApiExtension.resetAndDisableResetBeforeEach()

    tearDownTelemetryClient()
    reset(personLocationMigrationService)
  }

  @AfterAll
  fun tearDownTelemetryClient() = reset(telemetryClient)

  private fun stubMigrationDependencies(entities: Int, pageSize: Long) {
    nomisApi.stubGetPrisonerIds(entities.toLong(), 1, "A0000KT")
    nomisApi.stubGetAllPrisonersIdRangesAndInRange(pageSize = pageSize, totalElements = entities.toLong())
  }

  private fun migratedPrisonerNumbers(count: Int): List<String> = argumentCaptor<MigrationContext<PrisonNumberAndRootOffenderId>>().run {
    verifyBlocking(personLocationMigrationService, times(count)) { migrateNomisEntity(capture()) }
    allValues.map { it.body.prisonNumber }
  }

  @Nested
  @TestInstance(TestInstance.Lifecycle.PER_CLASS)
  inner class FullMigration {
    @BeforeAll
    fun setUp() {
      setupMigrationTest()

      stubMigrationDependencies(entities = 3, pageSize = 2)
      performMigration()
    }

    @Test
    fun `should migrate all prisoners`() {
      assertThat(migratedPrisonerNumbers(3)).containsExactlyInAnyOrder("A0000KT", "A0001KT", "A0002KT")
    }
  }

  @Nested
  @TestInstance(TestInstance.Lifecycle.PER_CLASS)
  inner class MigrateSinglePrisoner {
    @BeforeAll
    fun setUp() {
      setupMigrationTest()

      stubMigrationDependencies(entities = 100, pageSize = 10)
      performMigration("A1234BC")
    }

    @Test
    fun `should migrate only the requested prisoner`() {
      assertThat(migratedPrisonerNumbers(1)).containsExactly("A1234BC")
    }
  }

  @Nested
  inner class Security {
    @Test
    fun `access forbidden when no role`() {
      webTestClient.post().uri("/migrate/person-location")
        .headers(setAuthorisation(roles = listOf()))
        .contentType(MediaType.APPLICATION_JSON)
        .bodyValue(PersonLocationMigrationFilter())
        .exchange()
        .expectStatus().isForbidden
    }

    @Test
    fun `access forbidden with wrong role`() {
      webTestClient.post().uri("/migrate/person-location")
        .headers(setAuthorisation(roles = listOf("ROLE_BANANAS")))
        .contentType(MediaType.APPLICATION_JSON)
        .bodyValue(PersonLocationMigrationFilter())
        .exchange()
        .expectStatus().isForbidden
    }

    @Test
    fun `access unauthorised with no auth token`() {
      webTestClient.post().uri("/migrate/person-location")
        .contentType(MediaType.APPLICATION_JSON)
        .bodyValue(PersonLocationMigrationFilter())
        .exchange()
        .expectStatus().isUnauthorized
    }
  }

  private fun performMigration(prisonerNumber: String? = null): String = webTestClient.post()
    .uri("/migrate/person-location")
    .headers(setAuthorisation(roles = listOf("ROLE_PRISONER_FROM_NOMIS__MIGRATION__RW")))
    .contentType(MediaType.APPLICATION_JSON)
    .bodyValue(PersonLocationMigrationFilter(prisonerNumber = prisonerNumber))
    .exchange()
    .expectStatus().isAccepted
    .returnResult<MigrationResult>().responseBody.blockFirst()!!
    .migrationId
    .also {
      waitUntilCompleted()
    }

  private fun waitUntilCompleted() = await atMost Duration.ofSeconds(60) untilAsserted {
    verify(telemetryClient).trackEvent(
      eq("person-location-migration-completed"),
      any(),
      isNull(),
    )
  }
}
