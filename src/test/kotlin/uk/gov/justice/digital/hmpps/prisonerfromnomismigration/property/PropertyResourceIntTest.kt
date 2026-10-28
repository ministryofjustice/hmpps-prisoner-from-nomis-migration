package uk.gov.justice.digital.hmpps.prisonerfromnomismigration.property

import com.github.tomakehurst.wiremock.client.WireMock.anyUrl
import com.github.tomakehurst.wiremock.client.WireMock.equalTo
import com.github.tomakehurst.wiremock.client.WireMock.get
import com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor
import com.github.tomakehurst.wiremock.client.WireMock.jsonResponse
import com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor
import com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo
import com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo
import kotlinx.coroutines.test.runTest
import org.assertj.core.api.Assertions.assertThat
import org.awaitility.kotlin.atMost
import org.awaitility.kotlin.await
import org.awaitility.kotlin.matches
import org.awaitility.kotlin.untilAsserted
import org.awaitility.kotlin.untilCallTo
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.atLeastOnce
import org.mockito.kotlin.check
import org.mockito.kotlin.eq
import org.mockito.kotlin.isNull
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.http.HttpStatus
import org.springframework.test.web.reactive.server.WebTestClient
import org.springframework.web.reactive.function.BodyInserters
import software.amazon.awssdk.services.sqs.model.SendMessageRequest
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.config.ErrorResponse
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.data.MigrationContext
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomismappings.model.PropertyContainerMappingDto
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.persistence.repository.MigrationHistoryRepository
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.property.PropertyApiExtension.Companion.propertyDpsApi
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.property.model.SyncPropertyContainerResponse
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.service.MigrationType
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.wiremock.MappingApiExtension.Companion.mappingApi
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.wiremock.NomisApiExtension.Companion.nomisApi
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.wiremock.pageContent
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.wiremock.withRequestBodyJsonPath
import uk.gov.justice.hmpps.sqs.countMessagesOnQueue
import java.time.Duration
import java.util.UUID

private const val DPS_ID = "e52d7268-6e10-41a8-a0b9-2319b32520d6"
private const val DPS_ID2 = "edcd118c-41ba-42ea-b5c4-404b453ad58b"
private const val DPS_LOCATION_ID = "edcd118c-41ba-42ea-b5c4-404b453a0000"
private const val NOMIS_ID = 1234L
private const val BOOKING_ID = 5678L
private const val OFFENDER_NO = "A1234AA"

class PropertyResourceIntTest(
  @Autowired private val propertyNomisApiMockServer: PropertyNomisApiMockServer,
  @Autowired private val propertyMappingApiMockServer: PropertyMappingApiMockServer,
  @Autowired private val migrationHistoryRepository: MigrationHistoryRepository,
) : PropertyIntegrationTestBase() {

  @Nested
  @DisplayName("POST /migrate/property")
  inner class MigrationProperty {
    @BeforeEach
    fun setUp() = runTest {
      migrationHistoryRepository.deleteAll()
    }

    private fun WebTestClient.performMigration(body: String = "{ }") = post().uri("/migrate/property")
      .headers(setAuthorisation(roles = listOf("ROLE_PRISONER_FROM_NOMIS__MIGRATION__RW")))
      .header("Content-Type", "application/json")
      .body(BodyInserters.fromValue(body))
      .exchange()
      .expectStatus().isAccepted
      .expectBody(MigrationContext::class.java)
      .returnResult()
      .also {
        waitUntilCompleted(it.responseBody?.properties.toString())
      }

    private fun waitUntilCompleted(uuid: String) = await atMost Duration.ofSeconds(60) untilAsserted {
      verify(telemetryClient).trackEvent(
        eq("property-migration-completed"),
        check {
          assertThat(it["properties"]).contains(uuid)
        },
        isNull(),
      )
    }

    @Test
    fun `must have valid token to start migration`() {
      webTestClient.post().uri("/migrate/property")
        .header("Content-Type", "application/json")
        .body(BodyInserters.fromValue("{ }"))
        .exchange()
        .expectStatus().isUnauthorized
    }

    @Test
    fun `must have correct role to start migration`() {
      webTestClient.post().uri("/migrate/property")
        .headers(setAuthorisation(roles = listOf("ROLE_MIGRATE_BANANAS")))
        .header("Content-Type", "application/json")
        .body(BodyInserters.fromValue("{ }"))
        .exchange()
        .expectStatus().isForbidden
    }

    @Test
    fun `will start processing pages of property`() {
      nomisApi.stubGetInitialCount("property/ids", 14) { propertyIdsPagedResponse(it) }
      propertyNomisApiMockServer.stubMultipleGetPropertyIdCounts(totalElements = 14, pageSize = 10)
      propertyNomisApiMockServer.stubMultipleGetProperty(1..14)
      mappingApi.stubAllMappingsNotFound("/mapping/property/nomis-id")
      mappingApi.stubMappingCreate("/mapping/property")
      mappingApi.stubGetApiLocationNomis(123456, UUID.randomUUID().toString())

      propertyDpsApi.stubCreatePropertyForMigration(DPS_ID)
      propertyMappingApiMockServer.stubCountByMigrationId(count = 14)

      webTestClient.performMigration("""{ "prisonIds": ["MDI"] }""")

      // check filter matches what is passed in
      nomisApi.verify(
        getRequestedFor(urlPathEqualTo("/property-containers/ids"))
          .withQueryParam("prisonIds", equalTo("MDI")),
      )

      propertyDpsApi.verify(
        postRequestedFor(urlPathEqualTo("/sync/property-containers/migrate"))
          .withRequestBodyJsonPath("createDateTime", "2023-01-01T11:00:01.234567"),
      )

      await untilAsserted {
        assertThat(propertyDpsApi.createPropertyCount()).isEqualTo(14)
      }
    }

    @Test
    fun `will add analytical events for starting, ending and each migrated record`() {
      nomisApi.stubGetInitialCount("/property-containers/ids", 3) { propertyIdsPagedResponse(it) }
      propertyNomisApiMockServer.stubMultipleGetPropertyIdCounts(totalElements = 3, pageSize = 10)
      propertyNomisApiMockServer.stubMultipleGetProperty(1..3)
      propertyDpsApi.stubCreatePropertyForMigration(DPS_ID)
      mappingApi.stubAllMappingsNotFound("/mapping/property/nomis-id")
      mappingApi.stubMappingCreate("/mapping/property")
      mappingApi.stubGetApiLocationNomis(123456, UUID.randomUUID().toString())

      // stub 10 migrated records and 1 fake a failure
      propertyMappingApiMockServer.stubCountByMigrationId(count = 2)

      awsSqsPropertyMigrationDlqClient.sendMessage(
        SendMessageRequest.builder().queueUrl(propertyMigrationDlqUrl)
          .messageBody("""{ "message": "some error" }""").build(),
      ).get()

      webTestClient.performMigration()

      verify(telemetryClient).trackEvent(eq("property-migration-started"), any(), isNull())
      verify(telemetryClient, times(3)).trackEvent(eq("property-migration-entity-migrated"), any(), isNull())

      await atMost Duration.ofSeconds(20) untilAsserted {
        webTestClient.get().uri("/migrate/history/all/{migrationType}", MigrationType.PROPERTY)
          .headers(setAuthorisation(roles = listOf("ROLE_PRISONER_FROM_NOMIS__MIGRATION__RW")))
          .header("Content-Type", "application/json")
          .exchange()
          .expectStatus().isOk
          .expectBody()
          .jsonPath("$.size()").isEqualTo(1)
          .jsonPath("$[0].migrationId").isNotEmpty
          .jsonPath("$[0].whenStarted").isNotEmpty
          .jsonPath("$[0].status").isEqualTo("COMPLETED")
          .jsonPath("$[0].estimatedRecordCount").isEqualTo(3)
          .jsonPath("$[0].migrationType").isEqualTo("PROPERTY")
          .jsonPath("$[0].recordsMigrated").isEqualTo(2)
          .jsonPath("$[0].recordsFailed").isEqualTo(1)
          .jsonPath("$[0].whenEnded").isNotEmpty
      }
    }

    @Test
    fun `will post the correct data to DPS`() {
      nomisApi.stubGetInitialCount("/property-containers/ids", 1) { propertyIdsPagedResponse(it) }
      propertyNomisApiMockServer.stubMultipleGetPropertyIdCounts(totalElements = 1, pageSize = 10)
      propertyNomisApiMockServer.stubGetProperty(1)
      mappingApi.stubAllMappingsNotFound("/mapping/property/nomis-id")
      mappingApi.stubMappingCreate("/mapping/property")
      propertyMappingApiMockServer.stubCountByMigrationId(1)
      propertyDpsApi.stubCreatePropertyForMigration(DPS_ID)
      mappingApi.stubGetApiLocationNomis(123456, DPS_LOCATION_ID)

      webTestClient.performMigration("""{ "prisonIds": ["MDI"] }""")

      propertyDpsApi.verify(
        postRequestedFor(urlPathEqualTo("/sync/property-containers/migrate"))
          .withRequestBodyJsonPath("prisonerNumber", "A1234AA")
          .withRequestBodyJsonPath("internalLocationId", DPS_LOCATION_ID)
          .withRequestBodyJsonPath("prisonId", "SYI")
          .withRequestBodyJsonPath("containerCode", "Bulk")
          .withRequestBodyJsonPath("sealMark", "SEAL1234")
          .withRequestBodyJsonPath("active", "true")
          .withRequestBodyJsonPath("proposedDisposalDate", "2035-05-14")
          .withRequestBodyJsonPath("expiryDate", "2035-05-13")
          .withRequestBodyJsonPath("createDateTime", "2025-05-01T12:34:56")
          .withRequestBodyJsonPath("createUsername", "ME")
          .withRequestBodyJsonPath("modifyDateTime", "2025-05-02T12:34:56")
          .withRequestBodyJsonPath("modifyUsername", "SOMEONEELSE"),
      )
    }

    @Test
    fun `will retry to create a mapping, and only the mapping, if it fails first time`() {
      nomisApi.stubGetInitialCount("/property-containers/ids", 1) { propertyIdsPagedResponse(it) }
      propertyNomisApiMockServer.stubMultipleGetPropertyIdCounts(totalElements = 1, pageSize = 10)
      propertyNomisApiMockServer.stubMultipleGetProperty(1..1)
      mappingApi.stubAllMappingsNotFound("/mapping/property/nomis-id")
      propertyMappingApiMockServer.stubCountByMigrationId(1)
      propertyDpsApi.stubCreatePropertyForMigration(DPS_ID)
      mappingApi.stubMappingCreateFailureFollowedBySuccess("/mapping/property")
      mappingApi.stubGetApiLocationNomis(123456, UUID.randomUUID().toString())

      webTestClient.performMigration("""{ "prisonIds": ["MDI"] }""")

      // wait for all mappings to be created before verifying
      await untilCallTo { mappingApi.createMappingCount("/mapping/property") } matches { it == 2 }

      // check that one property is created
      assertThat(propertyDpsApi.createPropertyCount()).isEqualTo(1)

      // should retry to create mapping twice
      propertyMappingApiMockServer.verifyCreateMappingPropertyIds(arrayOf(DPS_ID), times = 2)
    }

    @Test
    fun `will generate failure telemetry`() {
      nomisApi.stubGetInitialCount("/property-containers/ids", 1) { propertyIdsPagedResponse(it) }
      propertyNomisApiMockServer.stubMultipleGetPropertyIdCounts(totalElements = 1, pageSize = 10)
      propertyNomisApiMockServer.stubMultipleGetProperty(1..1)
      mappingApi.stubAllMappingsNotFound("/mapping/property/nomis-id")
      propertyMappingApiMockServer.stubCountByMigrationId(1)
      propertyDpsApi.stubCreatePropertyForMigrationFailure()
      mappingApi.stubGetApiLocationNomis(123456, UUID.randomUUID().toString())

      webTestClient.performMigration("""{ "prisonIds": ["MDI"] }""")

      await untilAsserted {
        verify(telemetryClient, atLeastOnce()).trackEvent(
          eq(
            "property-migration-entity-migrate-failed",
          ),
          check {
            assertThat(it["nomisId"]).isEqualTo("1")
            assertThat(it["error"]).contains("500 Internal Server Error")
          },
          isNull(),
        )
      }
    }

    @Test
    fun `will end up on the DLQ if the location mapping transformation fails`() {
      nomisApi.stubGetInitialCount("/property-containers/ids", 1) { propertyIdsPagedResponse(it) }
      propertyNomisApiMockServer.stubMultipleGetPropertyIdCounts(totalElements = 1, pageSize = 10)
      propertyNomisApiMockServer.stubMultipleGetProperty(1..1)
      propertyDpsApi.stubCreatePropertyForMigration(DPS_ID)
      mappingApi.stubAllMappingsNotFound("/mapping/property/nomis-id")
      mappingApi.stubMappingCreate("/mapping/property")
      // Not found from location mapping request
      mappingApi.stubGetAnyLocationNotFound()
      propertyMappingApiMockServer.stubCountByMigrationId(count = 1)

      webTestClient.performMigration("""{ "prisonIds": ["MDI"] }""")

      // should end up on the DLQ
      await untilCallTo {
        awsSqsPropertyMigrationDlqClient.countMessagesOnQueue(propertyMigrationDlqUrl).get()
      } matches { it == 1 }

      // check that property is not created
      assertThat(propertyDpsApi.createPropertyCount()).isEqualTo(0)
    }

    @Test
    fun `it will not retry after a 409 (duplicate property written to DPS API) or mapping already exists`() {
      nomisApi.stubGetInitialCount("/property-containers/ids", 1) { propertyIdsPagedResponse(it) }
      propertyNomisApiMockServer.stubMultipleGetPropertyIdCounts(totalElements = 2, pageSize = 10)
      propertyNomisApiMockServer.stubMultipleGetProperty(1..2)
      mappingApi.stubAllMappingsNotFound("/mapping/property/nomis-id")
      propertyDpsApi.stubCreatePropertyForMigration(DPS_ID)
      propertyMappingApiMockServer.stubPropertyMappingCreateConflict(DPS_ID, DPS_ID2, 1)
      propertyMappingApiMockServer.stubNomisPropertyMappingFound(2)
      propertyMappingApiMockServer.stubCountByMigrationId(1)
      mappingApi.stubGetApiLocationNomis(123456, UUID.randomUUID().toString())

      webTestClient.performMigration("""{ "prisonIds": ["MDI"] }""")

      // wait for all mappings to be created before verifying
      await untilCallTo { mappingApi.createMappingCount("/mapping/property") } matches { it == 1 }

      // check that one property is created
      assertThat(propertyDpsApi.createPropertyCount()).isEqualTo(1)

      // doesn't retry
      propertyMappingApiMockServer.verifyCreateMappingPropertyIds(arrayOf(DPS_ID), times = 1)

      verify(telemetryClient).trackEvent(
        eq("property-nomis-migration-duplicate"),
        check {
          assertThat(it["migrationId"]).isNotNull
          assertThat(it["existingDpsId"]).isEqualTo(DPS_ID)
          assertThat(it["duplicateDpsId"]).isEqualTo(DPS_ID2)
          assertThat(it["existingNomisId"]).isEqualTo("1")
          assertThat(it["duplicateNomisId"]).isEqualTo("1")
        },
        isNull(),
      )
    }
  }

  @Nested
  @DisplayName("POST /property/{id}/repair")
  inner class Repair {
    private fun WebTestClient.repair(roles: List<String> = listOf("ROLE_PRISONER_FROM_NOMIS__UPDATE__RW")) = put()
      .uri("/property/$NOMIS_ID/repair")
      .headers(setAuthorisation(roles = roles))
      .exchange()

    @Nested
    inner class Security {
      @Test
      fun `access unauthorised with no auth token`() {
        webTestClient.put().uri("/property/$NOMIS_ID/repair")
          .exchange()
          .expectStatus().isUnauthorized
      }

      @Test
      fun `access forbidden with no role`() {
        webTestClient.repair(roles = listOf())
          .expectStatus().isForbidden
      }

      @Test
      fun `access forbidden with wrong role`() {
        webTestClient.repair(roles = listOf("ROLE_BANANAS"))
          .expectStatus().isForbidden
      }
    }

    @Nested
    inner class NomisPropertyNotFound {
      @BeforeEach
      fun setUp() {
        nomisApi.stubFor(
          get(urlEqualTo("/property-containers/$NOMIS_ID"))
            .willReturn(jsonResponse(ErrorResponse(status = 404), 404)),
        )
      }

      @Test
      fun `will return error and not send anything to DPS`() {
        webTestClient.repair().expectStatus().isNotFound
          .expectBody()
          .jsonPath("$.userMessage").isEqualTo("Not Found: property container with nomisId=$NOMIS_ID not found")

        propertyDpsApi.verify(0, postRequestedFor(anyUrl()))
      }
    }

    @Nested
    inner class MappingDoesNotExist {
      @BeforeEach
      fun setUp() {
        propertyNomisApiMockServer.stubGetProperty(
          propertyResponse = propertyContainerGetResponse(NOMIS_ID, BOOKING_ID, OFFENDER_NO),
        )
        propertyMappingApiMockServer.stubGetByNomisId(status = HttpStatus.NOT_FOUND)
        mappingApi.stubGetApiLocationNomis(123456, DPS_LOCATION_ID)
        propertyDpsApi.stubUpsert(
          SyncPropertyContainerResponse(
            dpsId = UUID.fromString(DPS_ID),
            nomisPropertyContainerId = NOMIS_ID,
            mappingType = SyncPropertyContainerResponse.MappingType.CREATED,
          ),
        )
      }

      @Nested
      inner class HappyPath {
        @BeforeEach
        fun setUp() {
          propertyMappingApiMockServer.stubPostMapping()
          webTestClient.repair().expectStatus().isOk
        }

        @Test
        fun `will create property in DPS`() {
          propertyDpsApi.verify(
            1,
            postRequestedFor(urlPathEqualTo("/sync/property-containers/upsert"))
              .withRequestBodyJsonPath("nomisPropertyContainerId", NOMIS_ID.toString())
              .withRequestBodyJsonPath("prisonerNumber", OFFENDER_NO)
              .withRequestBodyJsonPath("internalLocationId", DPS_LOCATION_ID),
          )
        }

        @Test
        fun `will create mapping between DPS and NOMIS ids`() {
          propertyMappingApiMockServer.verify(
            postRequestedFor(urlPathEqualTo("/mapping/property"))
              .withRequestBodyJsonPath("dpsPropertyContainerId", DPS_ID)
              .withRequestBodyJsonPath("nomisPropertyContainerId", NOMIS_ID.toString())
              .withRequestBodyJsonPath("bookingId", BOOKING_ID.toString())
              .withRequestBodyJsonPath("mappingType", "NOMIS_CREATED"),
          )
        }

        @Test
        fun `will track telemetry for the repair`() {
          verify(telemetryClient).trackEvent(
            eq("property-repair-success"),
            check {
              assertThat(it["nomisPropertyContainerId"]).isEqualTo(NOMIS_ID.toString())
              assertThat(it["bookingId"]).isEqualTo(BOOKING_ID.toString())
              assertThat(it["offenderNo"]).isEqualTo(OFFENDER_NO)
            },
            isNull(),
          )
        }
      }

      @Nested
      inner class MappingCreateFails {
        @BeforeEach
        fun setUp() {
          propertyMappingApiMockServer.stubPostMappingFailureFollowedBySuccess()
          webTestClient.repair().expectStatus().isOk
        }

        @Test
        fun `will retry creating the mapping without recreating the property`() {
          await untilAsserted {
            propertyMappingApiMockServer.verify(
              2,
              postRequestedFor(urlPathEqualTo("/mapping/property")),
            )
          }
          propertyDpsApi.verify(1, postRequestedFor(urlPathEqualTo("/sync/property-containers/upsert")))
        }
      }

      @Nested
      inner class DpsFails {
        @BeforeEach
        fun setUp() {
          propertyDpsApi.stubUpsertFailure()
        }

        @Test
        fun `will return error and track failure telemetry`() {
          webTestClient.repair().expectStatus().is5xxServerError

          verify(telemetryClient).trackEvent(
            eq("property-repair-error"),
            check {
              assertThat(it["nomisPropertyContainerId"]).isEqualTo(NOMIS_ID.toString())
            },
            isNull(),
          )
        }
      }
    }

    @Nested
    inner class MappingExists {
      @BeforeEach
      fun setUp() {
        propertyNomisApiMockServer.stubGetProperty(
          propertyResponse = propertyContainerGetResponse(NOMIS_ID, BOOKING_ID, OFFENDER_NO),
        )
        propertyMappingApiMockServer.stubGetByNomisId(
          PropertyContainerMappingDto(
            dpsPropertyContainerId = DPS_ID,
            nomisPropertyContainerId = NOMIS_ID,
            bookingId = BOOKING_ID,
            mappingType = PropertyContainerMappingDto.MappingType.MIGRATED,
          ),
        )
        mappingApi.stubGetApiLocationNomis(123456, DPS_LOCATION_ID)
      }

      @Nested
      inner class HappyPath {
        @BeforeEach
        fun setUp() {
          propertyDpsApi.stubUpsert(
            SyncPropertyContainerResponse(
              dpsId = UUID.fromString(DPS_ID),
              nomisPropertyContainerId = NOMIS_ID,
              mappingType = SyncPropertyContainerResponse.MappingType.UPDATED,
            ),
          )
          webTestClient.repair().expectStatus().isOk
        }

        @Test
        fun `will update property in DPS`() {
          propertyDpsApi.verify(
            1,
            postRequestedFor(urlPathEqualTo("/sync/property-containers/upsert"))
              .withRequestBodyJsonPath("dpsId", DPS_ID)
              .withRequestBodyJsonPath("nomisPropertyContainerId", NOMIS_ID.toString())
              .withRequestBodyJsonPath("prisonerNumber", OFFENDER_NO),
          )
        }

        @Test
        fun `will not create a mapping`() {
          propertyMappingApiMockServer.verify(0, postRequestedFor(urlPathEqualTo("/mapping/property")))
        }

        @Test
        fun `will track telemetry for the repair`() {
          verify(telemetryClient).trackEvent(
            eq("property-repair-success"),
            check {
              assertThat(it["nomisPropertyContainerId"]).isEqualTo(NOMIS_ID.toString())
              assertThat(it["dpsPropertyContainerId"]).isEqualTo(DPS_ID)
              assertThat(it["bookingId"]).isEqualTo(BOOKING_ID.toString())
              assertThat(it["offenderNo"]).isEqualTo(OFFENDER_NO)
            },
            isNull(),
          )
        }
      }

      @Nested
      inner class DpsFails {
        @BeforeEach
        fun setUp() {
          propertyDpsApi.stubUpsertFailure()
        }

        @Test
        fun `will return error and track failure telemetry`() {
          webTestClient.repair().expectStatus().is5xxServerError

          verify(telemetryClient).trackEvent(
            eq("property-repair-error"),
            check {
              assertThat(it["dpsPropertyContainerId"]).isEqualTo(DPS_ID)
            },
            isNull(),
          )
        }
      }
    }
  }
}

fun propertyIdsPagedResponse(
  totalElements: Long = 10,
  ids: List<Long> = (0L..10L).toList(),
  pageSize: Long = 10,
  pageNumber: Long = 0,
): String {
  val content = ids.map { """{ "containerId": $it }""" }.joinToString { it }
  return pageContent(content, pageSize, pageNumber, totalElements, ids.size)
}
