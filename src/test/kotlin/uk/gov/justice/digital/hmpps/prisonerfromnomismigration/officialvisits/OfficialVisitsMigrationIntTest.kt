package uk.gov.justice.digital.hmpps.prisonerfromnomismigration.officialvisits

import com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor
import com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor
import com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo
import kotlinx.coroutines.runBlocking
import org.assertj.core.api.Assertions.assertThat
import org.awaitility.kotlin.atMost
import org.awaitility.kotlin.await
import org.awaitility.kotlin.untilAsserted
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.check
import org.mockito.kotlin.eq
import org.mockito.kotlin.isNull
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.http.MediaType
import org.springframework.test.web.reactive.server.returnResult
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.helper.MigrationResult
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.integration.SqsIntegrationTestBase
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomismappings.model.DuplicateErrorContentObject
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomismappings.model.DuplicateMappingErrorResponse
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomismappings.model.DuplicateMappingErrorResponse.Status
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomismappings.model.LocationMappingDto
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomismappings.model.OfficialVisitMappingDto
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomismappings.model.OfficialVisitMigrationMappingDto
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomisprisoner.model.VisitIdResponse
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.officialvisits.OfficialVisitsDpsApiExtension.Companion.getRequestBody
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.officialvisits.OfficialVisitsDpsApiMockServer.Companion.migrateVisitResponse
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.officialvisits.OfficialVisitsNomisApiMockServer.Companion.officialVisitResponse
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.officialvisits.model.IdPair
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.officialvisits.model.MigrateVisitRequest
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.persistence.repository.MigrationHistoryRepository
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.personalrelationships.PrisonerRestrictionMigrationFilter
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.wiremock.MappingApiExtension
import java.time.Duration
import java.util.*

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class OfficialVisitsMigrationIntTest(
  @Autowired private val migrationHistoryRepository: MigrationHistoryRepository,
) : SqsIntegrationTestBase() {
  @Autowired
  private lateinit var nomisApiMock: OfficialVisitsNomisApiMockServer
  private val dpsApiMock = OfficialVisitsDpsApiExtension.dpsOfficialVisitsServer

  @Autowired
  private lateinit var mappingApiMock: OfficialVisitsMappingApiMockServer

  @Nested
  @DisplayName("POST /migrate/official-visits")
  inner class StartMigration {
    @BeforeEach
    internal fun deleteHistoryRecords() {
      runBlocking {
        migrationHistoryRepository.deleteAll()
      }
    }

    @Nested
    inner class Security {
      @Test
      fun `access forbidden when no role`() {
        webTestClient.post().uri("/migrate/official-visits")
          .headers(setAuthorisation(roles = listOf()))
          .contentType(MediaType.APPLICATION_JSON)
          .bodyValue(PrisonerRestrictionMigrationFilter())
          .exchange()
          .expectStatus().isForbidden
      }

      @Test
      fun `access forbidden with wrong role`() {
        webTestClient.post().uri("/migrate/official-visits")
          .headers(setAuthorisation(roles = listOf("BANANAS")))
          .contentType(MediaType.APPLICATION_JSON)
          .bodyValue(PrisonerRestrictionMigrationFilter())
          .exchange()
          .expectStatus().isForbidden
      }

      @Test
      fun `access unauthorised with no auth token`() {
        webTestClient.post().uri("/migrate/official-visits")
          .contentType(MediaType.APPLICATION_JSON)
          .bodyValue(PrisonerRestrictionMigrationFilter())
          .exchange()
          .expectStatus().isUnauthorized
      }
    }

    @Nested
    inner class EverythingAlreadyMigrated {
      private lateinit var migrationResult: MigrationResult

      @BeforeEach
      fun setUp() {
        nomisApiMock.stubGetOfficialVisitIds(
          content = listOf(
            VisitIdResponse(
              visitId = 2,
            ),
          ),
        )
        mappingApiMock.stubGetByNomisIdsOrNull(
          nomisVisitId = 2,
          mapping = OfficialVisitMappingDto(
            dpsId = "10000",
            nomisId = 2,
            mappingType = OfficialVisitMappingDto.MappingType.MIGRATED,
            label = "2020-01-01T00:00:00",
          ),
        )
        mappingApiMock.stubGetMigrationCount(migrationId = ".*", count = 0)
        migrationResult = performMigration()
      }

      @Test
      fun `will not bother retrieving any visit details`() {
        nomisApiMock.verify(0, getRequestedFor(urlPathEqualTo("/official-visits/2")))
      }

      @Test
      fun `will mark migration as complete`() {
        webTestClient.get().uri("/migrate/history/${migrationResult.migrationId}")
          .headers(setAuthorisation(roles = listOf("ROLE_PRISONER_FROM_NOMIS__MIGRATION__RW")))
          .header("Content-Type", "application/json")
          .exchange()
          .expectStatus().isOk
          .expectBody()
          .jsonPath("$.migrationId").isEqualTo(migrationResult.migrationId)
          .jsonPath("$.status").isEqualTo("COMPLETED")
      }
    }

    @Nested
    inner class HappyPath {
      private lateinit var migrationResult: MigrationResult
      val nomisLocationId = 12345L
      val dpsLocationId: UUID = UUID.fromString("e7c2a3cc-e5b2-48ff-9e8b-a5038355b36c")
      val nomisVisitId = 2L
      val dpsVisitId = "20"
      val dpsVisitorId = 45678L
      val nomisVisitorId = 876544L

      @BeforeEach
      fun setUp() {
        nomisApiMock.stubGetOfficialVisitIds(
          content = listOf(
            VisitIdResponse(
              visitId = nomisVisitId,
            ),
          ),
        )
        mappingApiMock.stubGetByNomisIdsOrNull(
          nomisVisitId = nomisVisitId,
          mapping = null,
        )
        nomisApiMock.stubGetOfficialVisit(
          visitId = nomisVisitId,
          response = officialVisitResponse().copy(internalLocationId = nomisLocationId, visitId = nomisVisitId),
        )
        mappingApiMock.stubGetInternalLocationByNomisId(
          nomisLocationId = nomisLocationId,
          mapping = LocationMappingDto(
            dpsLocationId = dpsLocationId.toString(),
            nomisLocationId = nomisLocationId,
            mappingType = LocationMappingDto.MappingType.LOCATION_CREATED,
          ),
        )
        dpsApiMock.stubMigrateVisit(
          response = migrateVisitResponse().copy(
            visit = IdPair(
              elementType = IdPair.ElementType.OFFICIAL_VISIT,
              nomisId = nomisVisitId,
              dpsId = dpsVisitId.toLong(),
            ),
            visitors = listOf(
              IdPair(elementType = IdPair.ElementType.OFFICIAL_VISITOR, nomisId = nomisVisitorId, dpsId = dpsVisitorId),
            ),
          ),
        )
        mappingApiMock.stubCreateMappingsForMigration()
        mappingApiMock.stubGetMigrationCount(migrationId = ".*", count = 1)
        migrationResult = performMigration()
      }

      @Test
      fun `will retrieve visit details`() {
        nomisApiMock.verify(getRequestedFor(urlPathEqualTo("/official-visits/$nomisVisitId")))
      }

      @Test
      fun `will retrieve DPS visit room location id`() {
        mappingApiMock.verify(getRequestedFor(urlPathEqualTo("/mapping/locations/nomis/$nomisLocationId")))
      }

      @Test
      fun `will transform and migrate visit and visitors into DPS`() {
        val migrationRequest: MigrateVisitRequest = getRequestBody(postRequestedFor(urlPathEqualTo("/migrate/visit")))

        assertThat(migrationRequest.offenderVisitId).isEqualTo(nomisVisitId)
        // TODO - flesh out asserts
      }

      @Test
      fun `will create mappings for visit and visitors`() {
        val mappingRequests: List<OfficialVisitMigrationMappingDto> = MappingApiExtension.getRequestBodies(postRequestedFor(urlPathEqualTo("/mapping/official-visits")))

        assertThat(mappingRequests).hasSize(1)

        with(mappingRequests.first()) {
          assertThat(mappingType).isEqualTo(OfficialVisitMigrationMappingDto.MappingType.MIGRATED)
          assertThat(label).isEqualTo(migrationResult.migrationId)
          assertThat(nomisId).isEqualTo(nomisVisitId)
          assertThat(dpsId).isEqualTo(dpsVisitId.toString())
          assertThat(visitors).hasSize(1)
          assertThat(visitors[0].dpsId).isEqualTo(dpsVisitorId.toString())
          assertThat(visitors[0].nomisId).isEqualTo(nomisVisitorId)
        }
      }

      @Test
      fun `will track telemetry for each visit migrated`() {
        verify(telemetryClient).trackEvent(
          eq("officialvisits-migration-entity-migrated"),
          check {
            assertThat(it["nomisId"]).isEqualTo(nomisVisitId.toString())
            assertThat(it["dpsId"]).isEqualTo(dpsVisitId)
          },
          isNull(),
        )
      }

      @Test
      fun `will record the number of visits migrated`() {
        webTestClient.get().uri("/migrate/history/${migrationResult.migrationId}")
          .headers(setAuthorisation(roles = listOf("ROLE_PRISONER_FROM_NOMIS__MIGRATION__RW")))
          .header("Content-Type", "application/json")
          .exchange()
          .expectStatus().isOk
          .expectBody()
          .jsonPath("$.migrationId").isEqualTo(migrationResult.migrationId)
          .jsonPath("$.status").isEqualTo("COMPLETED")
          .jsonPath("$.recordsMigrated").isEqualTo("1")
      }
    }

    @Nested
    inner class FailureWithRecoverPath {
      private lateinit var migrationResult: MigrationResult
      val nomisLocationId = 12345L
      val dpsLocationId: UUID = UUID.fromString("e7c2a3cc-e5b2-48ff-9e8b-a5038355b36c")
      val nomisVisitId = 1L
      val dpsVisitId = 54321L
      val dpsVisitorId = 45678L
      val nomisVisitorId = 876544L

      @BeforeEach
      fun setUp() {
        nomisApiMock.stubGetOfficialVisitIds(
          content = listOf(
            VisitIdResponse(
              visitId = nomisVisitId,
            ),
          ),
        )
        mappingApiMock.stubGetByNomisIdsOrNull(
          nomisVisitId = nomisVisitId,
          mapping = null,
        )
        nomisApiMock.stubGetOfficialVisit(
          visitId = nomisVisitId,
          response = officialVisitResponse().copy(internalLocationId = nomisLocationId, visitId = nomisVisitId),
        )
        mappingApiMock.stubGetInternalLocationByNomisId(
          nomisLocationId = nomisLocationId,
          mapping = LocationMappingDto(
            dpsLocationId = dpsLocationId.toString(),
            nomisLocationId = nomisLocationId,
            mappingType = LocationMappingDto.MappingType.LOCATION_CREATED,
          ),
        )
        dpsApiMock.stubMigrateVisit(
          response = migrateVisitResponse().copy(
            visit = IdPair(
              elementType = IdPair.ElementType.OFFICIAL_VISIT,
              nomisId = nomisVisitId,
              dpsId = dpsVisitId.toLong(),
            ),
            visitors = listOf(
              IdPair(elementType = IdPair.ElementType.OFFICIAL_VISITOR, nomisId = nomisVisitorId, dpsId = dpsVisitorId),
            ),
          ),
        )
        dpsApiMock.stubMigrateVisit(
          response = migrateVisitResponse().copy(
            visit = IdPair(
              elementType = IdPair.ElementType.OFFICIAL_VISIT,
              nomisId = nomisVisitId,
              dpsId = dpsVisitId.toLong(),
            ),
            visitors = listOf(
              IdPair(elementType = IdPair.ElementType.OFFICIAL_VISITOR, nomisId = nomisVisitorId, dpsId = dpsVisitorId),
            ),
          ),
        )
        mappingApiMock.stubCreateMappingsForMigrationFailureFollowedBySuccess()
        mappingApiMock.stubGetMigrationCount(migrationId = ".*", count = 1)
        migrationResult = performMigration()
      }

      @Test
      fun `will transform and migrate visit into DPS`() {
        val migrationRequest: MigrateVisitRequest = getRequestBody(postRequestedFor(urlPathEqualTo("/migrate/visit")))

        assertThat(migrationRequest.offenderVisitId).isEqualTo(nomisVisitId)
      }

      @Test
      fun `will eventually create mappings for visit and visitors`() {
        val mappingRequests: List<OfficialVisitMigrationMappingDto> = MappingApiExtension.getRequestBodies(postRequestedFor(urlPathEqualTo("/mapping/official-visits")))

        await untilAsserted {
          assertThat(mappingRequests).hasSize(2)
        }

        mappingRequests.forEach {
          assertThat(it.dpsId).isEqualTo(dpsVisitId.toString())
        }
      }

      @Test
      fun `will eventually track telemetry for each visit migrated`() {
        await untilAsserted {
          verify(telemetryClient).trackEvent(
            eq("officialvisits-migration-entity-migrated"),
            check {
              assertThat(it["nomisId"]).isEqualTo(nomisVisitId.toString())
              assertThat(it["dpsId"]).isEqualTo(dpsVisitId.toString())
            },
            isNull(),
          )
        }
      }

      @Test
      fun `will record the number of visits migrated`() {
        webTestClient.get().uri("/migrate/history/${migrationResult.migrationId}")
          .headers(setAuthorisation(roles = listOf("ROLE_PRISONER_FROM_NOMIS__MIGRATION__RW")))
          .header("Content-Type", "application/json")
          .exchange()
          .expectStatus().isOk
          .expectBody()
          .jsonPath("$.migrationId").isEqualTo(migrationResult.migrationId)
          .jsonPath("$.status").isEqualTo("COMPLETED")
          .jsonPath("$.recordsMigrated").isEqualTo("1")
      }
    }

    @Nested
    inner class FailureWithDuplicate {
      private lateinit var migrationResult: MigrationResult
      val nomisLocationId = 12345L
      val dpsLocationId: UUID = UUID.fromString("e7c2a3cc-e5b2-48ff-9e8b-a5038355b36c")
      val nomisVisitId = 2L
      val dpsVisitId = 54321L
      val dpsVisitorId = 45678L
      val nomisVisitorId = 876544L

      @BeforeEach
      fun setUp() {
        nomisApiMock.stubGetOfficialVisitIds(
          content = listOf(
            VisitIdResponse(
              visitId = nomisVisitId,
            ),
          ),
        )
        mappingApiMock.stubGetByNomisIdsOrNull(
          nomisVisitId = nomisVisitId,
          mapping = null,
        )
        nomisApiMock.stubGetOfficialVisit(
          visitId = nomisVisitId,
          response = officialVisitResponse().copy(internalLocationId = nomisLocationId, visitId = nomisVisitId),
        )
        mappingApiMock.stubGetInternalLocationByNomisId(
          nomisLocationId = nomisLocationId,
          mapping = LocationMappingDto(
            dpsLocationId = dpsLocationId.toString(),
            nomisLocationId = nomisLocationId,
            mappingType = LocationMappingDto.MappingType.LOCATION_CREATED,
          ),
        )
        dpsApiMock.stubMigrateVisit(
          response = migrateVisitResponse().copy(
            visit = IdPair(
              elementType = IdPair.ElementType.OFFICIAL_VISIT,
              nomisId = nomisVisitId,
              dpsId = dpsVisitId.toLong(),
            ),
            visitors = listOf(
              IdPair(elementType = IdPair.ElementType.OFFICIAL_VISITOR, nomisId = nomisVisitorId, dpsId = dpsVisitorId),
            ),
          ),
        )
        dpsApiMock.stubMigrateVisit(
          response = migrateVisitResponse().copy(
            visit = IdPair(
              elementType = IdPair.ElementType.OFFICIAL_VISIT,
              nomisId = nomisVisitId,
              dpsId = dpsVisitId.toLong(),
            ),
            visitors = listOf(
              IdPair(elementType = IdPair.ElementType.OFFICIAL_VISITOR, nomisId = nomisVisitorId, dpsId = dpsVisitorId),
            ),
          ),
        )
        mappingApiMock.stubCreateMappingsForMigration(
          DuplicateMappingErrorResponse(
            moreInfo = DuplicateErrorContentObject(
              duplicate = OfficialVisitMigrationMappingDto(
                dpsId = dpsVisitId.toString(),
                nomisId = nomisVisitId,
                visitors = emptyList(),
                mappingType = OfficialVisitMigrationMappingDto.MappingType.MIGRATED,
              ),
              existing = OfficialVisitMigrationMappingDto(
                dpsId = "9999",
                nomisId = nomisVisitId,
                visitors = emptyList(),
                mappingType = OfficialVisitMigrationMappingDto.MappingType.MIGRATED,
              ),
            ),
            status = Status._409_CONFLICT,
            errorCode = 1409,
            userMessage = "Duplicate",
          ),
        )
        mappingApiMock.stubGetMigrationCount(migrationId = ".*", count = 0)
        migrationResult = performMigration()
      }

      @Test
      fun `will transform and migrate visit and visitors into DPS`() {
        val migrationRequest: MigrateVisitRequest = getRequestBody(postRequestedFor(urlPathEqualTo("/migrate/visit")))

        assertThat(migrationRequest.offenderVisitId).isEqualTo(nomisVisitId)
      }

      @Test
      fun `will only try create mappings once`() {
        val mappingRequests: List<OfficialVisitMigrationMappingDto> = MappingApiExtension.getRequestBodies(postRequestedFor(urlPathEqualTo("/mapping/official-visits")))

        await untilAsserted {
          assertThat(mappingRequests).hasSize(1)
        }
      }

      @Test
      fun `will not track telemetry for any visit migrated`() {
        verify(telemetryClient, times(0)).trackEvent(
          eq("officialvisits-migration-entity-migrated"),
          any(),
          isNull(),
        )
      }

      @Test
      fun `will record the number of visits migrated`() {
        webTestClient.get().uri("/migrate/history/${migrationResult.migrationId}")
          .headers(setAuthorisation(roles = listOf("ROLE_PRISONER_FROM_NOMIS__MIGRATION__RW")))
          .header("Content-Type", "application/json")
          .exchange()
          .expectStatus().isOk
          .expectBody()
          .jsonPath("$.migrationId").isEqualTo(migrationResult.migrationId)
          .jsonPath("$.status").isEqualTo("COMPLETED")
      }
    }
  }

  private fun performMigration(body: PrisonerRestrictionMigrationFilter = PrisonerRestrictionMigrationFilter()): MigrationResult = webTestClient.post().uri("/migrate/official-visits")
    .headers(setAuthorisation(roles = listOf("PRISONER_FROM_NOMIS__MIGRATION__RW")))
    .contentType(MediaType.APPLICATION_JSON)
    .bodyValue(body)
    .exchange()
    .expectStatus().isAccepted.returnResult<MigrationResult>().responseBody.blockFirst()!!
    .also {
      waitUntilCompleted()
    }

  private fun waitUntilCompleted() = await atMost Duration.ofSeconds(60) untilAsserted {
    verify(telemetryClient).trackEvent(
      eq("officialvisits-migration-completed"),
      any(),
      isNull(),
    )
  }
}
