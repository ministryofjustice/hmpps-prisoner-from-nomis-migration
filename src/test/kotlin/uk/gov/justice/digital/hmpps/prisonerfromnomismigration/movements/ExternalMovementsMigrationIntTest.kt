package uk.gov.justice.digital.hmpps.prisonerfromnomismigration.movements

import com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor
import com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo
import kotlinx.coroutines.test.runTest
import org.awaitility.kotlin.atMost
import org.awaitility.kotlin.await
import org.awaitility.kotlin.untilAsserted
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.eq
import org.mockito.kotlin.isNull
import org.mockito.kotlin.verify
import org.springframework.beans.factory.annotation.Autowired
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.integration.SqsIntegrationTestBase
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.persistence.repository.MigrationHistoryRepository
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.wiremock.NomisApiExtension.Companion.nomisApi
import uk.gov.justice.hmpps.test.kotlin.auth.WithMockAuthUser
import java.time.Duration

// TODO SDIT-2846 remove this when starting migration via the endpoint
@WithMockAuthUser
class ExternalMovementsMigrationIntTest(
  @Autowired private val migrationService: ExternalMovementsMigrationService,
  @Autowired private val migrationHistoryRepository: MigrationHistoryRepository,
) : SqsIntegrationTestBase() {

  private lateinit var migrationId: String

  @BeforeEach
  fun deleteHistoryRecords() = runTest {
    migrationHistoryRepository.deleteAll()
  }

  private fun stubMigrationDependencies(entities: Int = 2) {
    nomisApi.stubGetPrisonerIds(totalElements = entities.toLong(), pageSize = 10, firstOffenderNo = "A0001KT")
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
  }

  // TODO SDIT-2846 call the start migration endpoint
  private suspend fun performMigration(): String = migrationService.startMigration(
    ExternalMovementsMigrationFilter(),
  )
    .migrationId
    .also { waitUntilCompleted() }

  private fun waitUntilCompleted() = await atMost Duration.ofSeconds(60) untilAsserted {
    verify(telemetryClient).trackEvent(
      eq("external-movements-migration-completed"),
      any(),
      isNull(),
    )
  }
}
