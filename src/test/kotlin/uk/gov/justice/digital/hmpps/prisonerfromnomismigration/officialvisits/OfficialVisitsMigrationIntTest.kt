package uk.gov.justice.digital.hmpps.prisonerfromnomismigration.officialvisits

import com.github.tomakehurst.wiremock.client.WireMock.equalTo
import com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor
import com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor
import com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo
import kotlinx.coroutines.runBlocking
import org.assertj.core.api.Assertions.assertThat
import org.awaitility.kotlin.atMost
import org.awaitility.kotlin.await
import org.awaitility.kotlin.untilAsserted
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
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
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomismappings.model.VisitSlotMappingDto
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomisprisoner.model.CodeDescription
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomisprisoner.model.ContactRelationship
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomisprisoner.model.NomisAudit
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomisprisoner.model.VisitIdResponse
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomisprisoner.model.VisitOrder
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.officialvisits.OfficialVisitsDpsApiExtension.Companion.getRequestBodies
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.officialvisits.OfficialVisitsDpsApiExtension.Companion.getRequestBody
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.officialvisits.OfficialVisitsDpsApiMockServer.Companion.migrateVisitResponse
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.officialvisits.OfficialVisitsNomisApiMockServer.Companion.officialVisitResponse
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.officialvisits.OfficialVisitsNomisApiMockServer.Companion.officialVisitor
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.officialvisits.model.AttendanceType
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.officialvisits.model.IdPair
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.officialvisits.model.MigrateVisitRequest
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.officialvisits.model.RelationshipType
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.officialvisits.model.SearchLevelType
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.officialvisits.model.VisitCompletionType
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.officialvisits.model.VisitStatusType
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.officialvisits.model.VisitType
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.persistence.repository.MigrationHistoryRepository
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.wiremock.MappingApiExtension
import java.time.Duration
import java.time.LocalDate
import java.time.LocalDateTime
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

  @Autowired
  private lateinit var visitSlotMappingApiMock: VisitSlotsMappingApiMockServer

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
          .bodyValue(OfficialVisitsMigrationFilter())
          .exchange()
          .expectStatus().isForbidden
      }

      @Test
      fun `access forbidden with wrong role`() {
        webTestClient.post().uri("/migrate/official-visits")
          .headers(setAuthorisation(roles = listOf("BANANAS")))
          .contentType(MediaType.APPLICATION_JSON)
          .bodyValue(OfficialVisitsMigrationFilter())
          .exchange()
          .expectStatus().isForbidden
      }

      @Test
      fun `access unauthorised with no auth token`() {
        webTestClient.post().uri("/migrate/official-visits")
          .contentType(MediaType.APPLICATION_JSON)
          .bodyValue(OfficialVisitsMigrationFilter())
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
        nomisApiMock.stubGetOfficialVisitIdsByLastId(
          visitId = 0,
          content = listOf(
            VisitIdResponse(
              visitId = 2,
            ),
          ),
        )
        nomisApiMock.stubGetOfficialVisitIdsByLastId(
          visitId = 2,
          content = emptyList(),
        )
        mappingApiMock.stubGetByVisitNomisIdsOrNull(
          nomisVisitId = 2,
          mapping = OfficialVisitMappingDto(
            dpsId = "10000",
            nomisId = 2,
            mappingType = OfficialVisitMappingDto.MappingType.MIGRATED,
            label = "2020-01-01T00:00:00",
          ),
        )
        mappingApiMock.stubGetMigrationCount(migrationId = ".*", count = 0)
        migrationResult = performMigration(
          OfficialVisitsMigrationFilter(
            prisonIds = listOf("BXI", "MDI"),
            fromDate = LocalDate.parse("2020-01-01"),
            toDate = LocalDate.parse("2023-01-01"),
          ),
        )
      }

      @Test
      fun `will pass filter to get all ids endpoint for initial count and first page`() {
        nomisApiMock.verify(
          getRequestedFor(urlPathEqualTo("/official-visits/ids"))
            .withQueryParam("page", equalTo("0"))
            .withQueryParam("size", equalTo("1"))
            .withQueryParam("prisonIds", equalTo("MDI"))
            .withQueryParam("prisonIds", equalTo("BXI"))
            .withQueryParam("fromDate", equalTo("2020-01-01"))
            .withQueryParam("toDate", equalTo("2023-01-01")),
        )
        nomisApiMock.verify(
          getRequestedFor(urlPathEqualTo("/official-visits/ids/all-from-id"))
            .withQueryParam("visitId", equalTo("0"))
            .withQueryParam("size", equalTo("10"))
            .withQueryParam("prisonIds", equalTo("MDI"))
            .withQueryParam("prisonIds", equalTo("BXI"))
            .withQueryParam("fromDate", equalTo("2020-01-01"))
            .withQueryParam("toDate", equalTo("2023-01-01")),
        )
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
      val nomisVisitSlotId = 3L
      val dpsVisitSlotId = 30L

      @BeforeEach
      fun setUp() {
        nomisApiMock.stubGetOfficialVisitIds(
          content = listOf(
            VisitIdResponse(
              visitId = nomisVisitId,
            ),
          ),
        )
        nomisApiMock.stubGetOfficialVisitIdsByLastId(
          content = listOf(
            VisitIdResponse(
              visitId = nomisVisitId,
            ),
          ),
        )
        nomisApiMock.stubGetOfficialVisitIdsByLastId(
          visitId = nomisVisitId,
          content = emptyList(),
        )

        mappingApiMock.stubGetByVisitNomisIdsOrNull(
          nomisVisitId = nomisVisitId,
          mapping = null,
        )
        nomisApiMock.stubGetOfficialVisit(
          visitId = nomisVisitId,
          response = officialVisitResponse().copy(internalLocationId = nomisLocationId, visitId = nomisVisitId, visitSlotId = nomisVisitSlotId),
        )
        mappingApiMock.stubGetInternalLocationByNomisId(
          nomisLocationId = nomisLocationId,
          mapping = LocationMappingDto(
            dpsLocationId = dpsLocationId.toString(),
            nomisLocationId = nomisLocationId,
            mappingType = LocationMappingDto.MappingType.LOCATION_CREATED,
          ),
        )
        visitSlotMappingApiMock.stubGetVisitSlotByNomisId(
          nomisId = nomisVisitSlotId,
          mapping = VisitSlotMappingDto(
            dpsId = dpsVisitSlotId.toString(),
            nomisId = nomisVisitSlotId,
            mappingType = VisitSlotMappingDto.MappingType.NOMIS_CREATED,
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
      fun `will retrieve DPS visit slot id`() {
        mappingApiMock.verify(getRequestedFor(urlPathEqualTo("/mapping/visit-slots/visit-slot/nomis-id/$nomisVisitSlotId")))
      }

      @Test
      fun `will migrate visit and visitors into DPS`() {
        dpsApiMock.verify(postRequestedFor(urlPathEqualTo("/migrate/visit")))
      }

      @Test
      fun `will create mappings for visit and visitors`() {
        val mappingRequests: List<OfficialVisitMigrationMappingDto> = MappingApiExtension.getRequestBodies(postRequestedFor(urlPathEqualTo("/mapping/official-visits")))

        assertThat(mappingRequests).hasSize(1)

        with(mappingRequests.first()) {
          assertThat(mappingType).isEqualTo(OfficialVisitMigrationMappingDto.MappingType.MIGRATED)
          assertThat(label).isEqualTo(migrationResult.migrationId)
          assertThat(nomisId).isEqualTo(nomisVisitId)
          assertThat(dpsId).isEqualTo(dpsVisitId)
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
    @TestInstance(TestInstance.Lifecycle.PER_CLASS)
    inner class HappyPathNomisToDPSMapping {
      private lateinit var migrationResult: MigrationResult
      val nomisLocationId = 12345L
      val dpsLocationId: UUID = UUID.fromString("e7c2a3cc-e5b2-48ff-9e8b-a5038355b36c")
      val nomisVisitId = 2L
      val dpsVisitId = "20"
      val dpsVisitorId = 45678L
      val nomisVisitorId = 876544L
      val nomisVisitSlotId = 3L
      val dpsVisitSlotId = 30L

      private lateinit var migrationRequest: MigrateVisitRequest

      @BeforeAll
      fun setUp() {
        dpsApiMock.resetAll()

        nomisApiMock.stubGetOfficialVisitIds(
          content = listOf(
            VisitIdResponse(
              visitId = nomisVisitId,
            ),
          ),
        )
        nomisApiMock.stubGetOfficialVisitIdsByLastId(
          content = listOf(
            VisitIdResponse(
              visitId = nomisVisitId,
            ),
          ),
        )
        nomisApiMock.stubGetOfficialVisitIdsByLastId(
          visitId = nomisVisitId,
          content = emptyList(),
        )
        mappingApiMock.stubGetByVisitNomisIdsOrNull(
          nomisVisitId = nomisVisitId,
          mapping = null,
        )
        nomisApiMock.stubGetOfficialVisit(
          visitId = nomisVisitId,
          response = officialVisitResponse().copy(
            internalLocationId = nomisLocationId,
            visitId = nomisVisitId,
            visitSlotId = nomisVisitSlotId,
            startDateTime = LocalDateTime.parse("2020-01-01T10:00"),
            endDateTime = LocalDateTime.parse("2020-01-01T11:10"),
            offenderNo = "A1234KT",
            bookingId = 1234,
            currentTerm = true,
            prisonId = "MDI",
            commentText = "First visit",
            visitorConcernText = "Big concerns",
            overrideBanStaffUsername = "T.SMITH",
            visitOrder = VisitOrder(654321),
            prisonerSearchType = CodeDescription(code = "PAT", description = "Pat Down Search"),
            visitStatus = CodeDescription(code = "VISITOR", description = "Visitor Completed Early"),
            visitOutcome = CodeDescription(code = "CANC", description = "Cancelled"),
            prisonerAttendanceOutcome = CodeDescription(code = "ATT", description = "Attended"),
            cancellationReason = null,
            audit = NomisAudit(
              createDatetime = LocalDateTime.parse("2020-01-01T10:10:10"),
              createUsername = "J.JOHN",
              modifyDatetime = LocalDateTime.parse("2020-02-02T11:10:10"),
              modifyUserId = "S.SMITH",
            ),
            visitors = listOf(
              officialVisitor().copy(
                id = nomisVisitorId,
                personId = 876,
                firstName = "JOHN",
                lastName = "SMITH",
                dateOfBirth = LocalDate.parse("1965-07-19"),
                leadVisitor = true,
                assistedVisit = true,
                commentText = "Requires access",
                eventStatus = CodeDescription(code = "COMP", description = "Completed"),
                visitorAttendanceOutcome = CodeDescription(code = "ATT", description = "Attended"),
                cancellationReason = CodeDescription(code = "OFFCANC", description = "Offender Cancelled"),
                relationships = listOf(
                  ContactRelationship(
                    relationshipType = CodeDescription(
                      code = "POL",
                      description = "Police",
                    ),
                    contactType = CodeDescription(
                      code = "O",
                      description = "Official",
                    ),
                  ),
                  ContactRelationship(
                    relationshipType = CodeDescription(
                      code = "DR",
                      description = "Doctor",
                    ),
                    contactType = CodeDescription(
                      code = "O",
                      description = "Official",
                    ),
                  ),
                ),
                audit = NomisAudit(
                  createDatetime = LocalDateTime.parse("2019-01-01T10:10:10"),
                  createUsername = "S.JOHN",
                  modifyDatetime = LocalDateTime.parse("2019-02-02T11:10:10"),
                  modifyUserId = "T.SMITH",
                ),
              ),
              officialVisitor(),
            ),
          ),
        )
        mappingApiMock.stubGetInternalLocationByNomisId(
          nomisLocationId = nomisLocationId,
          mapping = LocationMappingDto(
            dpsLocationId = dpsLocationId.toString(),
            nomisLocationId = nomisLocationId,
            mappingType = LocationMappingDto.MappingType.LOCATION_CREATED,
          ),
        )
        visitSlotMappingApiMock.stubGetVisitSlotByNomisId(
          nomisId = nomisVisitSlotId,
          mapping = VisitSlotMappingDto(
            dpsId = dpsVisitSlotId.toString(),
            nomisId = nomisVisitSlotId,
            mappingType = VisitSlotMappingDto.MappingType.NOMIS_CREATED,
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
        migrationRequest = getRequestBody(postRequestedFor(urlPathEqualTo("/migrate/visit")))
      }

      @Test
      fun `will map and transform visits ids`() {
        assertThat(migrationRequest.offenderVisitId).isEqualTo(nomisVisitId)
        assertThat(migrationRequest.dpsLocationId).isEqualTo(dpsLocationId)
        assertThat(migrationRequest.prisonVisitSlotId).isEqualTo(dpsVisitSlotId)
        assertThat(migrationRequest.prisonCode).isEqualTo("MDI")
      }

      @Test
      fun `will map and transform visit times`() {
        assertThat(migrationRequest.visitDate).isEqualTo(LocalDate.parse("2020-01-01"))
        assertThat(migrationRequest.startTime).isEqualTo("10:00")
        assertThat(migrationRequest.endTime).isEqualTo("11:10")
      }

      @Test
      fun `will map and transform visit attributes`() {
        assertThat(migrationRequest.commentText).isEqualTo("First visit")
        assertThat(migrationRequest.visitorConcernText).isEqualTo("Big concerns")
        assertThat(migrationRequest.overrideBanStaffUsername).isEqualTo("T.SMITH")
        assertThat(migrationRequest.searchTypeCode).isEqualTo(SearchLevelType.PAT)
        assertThat(migrationRequest.visitOrderNumber).isEqualTo(654321)
      }

      @Test
      fun `will map and transform prisoner details`() {
        assertThat(migrationRequest.prisonerNumber).isEqualTo("A1234KT")
        assertThat(migrationRequest.offenderBookId).isEqualTo(1234L)
        assertThat(migrationRequest.currentTerm).isTrue
      }

      @Test
      fun `will map and transform visit status`() {
        assertThat(migrationRequest.visitStatusCode).isEqualTo(VisitStatusType.COMPLETED)
        assertThat(migrationRequest.visitTypeCode).isEqualTo(VisitType.UNKNOWN)
        assertThat(migrationRequest.visitCompletionCode).isEqualTo(VisitCompletionType.VISITOR_EARLY)
      }

      @Test
      fun `will map and transform audit details`() {
        assertThat(migrationRequest.createUsername).isEqualTo("J.JOHN")
        assertThat(migrationRequest.createDateTime).isEqualTo(LocalDateTime.parse("2020-01-01T10:10:10"))
        assertThat(migrationRequest.modifyUsername).isEqualTo("S.SMITH")
        assertThat(migrationRequest.modifyDateTime).isEqualTo(LocalDateTime.parse("2020-02-02T11:10:10"))
      }

      @Test
      fun `will send all visitors`() {
        assertThat(migrationRequest.visitors).hasSize(2)
      }

      @Test
      fun `will map and transform core details about each visitor`() {
        with(migrationRequest.visitors[0]) {
          assertThat(offenderVisitVisitorId).isEqualTo(nomisVisitorId)
          assertThat(personId).isEqualTo(876)
          assertThat(firstName).isEqualTo("JOHN")
          assertThat(lastName).isEqualTo("SMITH")
          assertThat(dateOfBirth).isEqualTo(LocalDate.parse("1965-07-19"))
          assertThat(groupLeaderFlag).isTrue
          assertThat(assistedVisitFlag).isTrue
          assertThat(commentText).isEqualTo("Requires access")
        }
      }

      @Test
      fun `will map and transform audit details of the visitor being added to visit`() {
        with(migrationRequest.visitors[0]) {
          assertThat(createUsername).isEqualTo("S.JOHN")
          assertThat(createDateTime).isEqualTo(LocalDateTime.parse("2019-01-01T10:10:10"))
          assertThat(modifyUsername).isEqualTo("T.SMITH")
          assertThat(modifyDateTime).isEqualTo(LocalDateTime.parse("2019-02-02T11:10:10"))
        }
      }

      @Test
      fun `will map and transform status details about each visitor`() {
        with(migrationRequest.visitors[0]) {
          assertThat(attendanceCode).isEqualTo(AttendanceType.ATTENDED)
        }
      }

      @Test
      fun `will map and transform the most relevant relationship between the visitor and prisoner`() {
        with(migrationRequest.visitors[0]) {
          assertThat(relationshipToPrisoner).isEqualTo("POL")
          assertThat(relationshipTypeCode).isEqualTo(RelationshipType.OFFICIAL)
        }
      }
    }

    @Nested
    @TestInstance(TestInstance.Lifecycle.PER_CLASS)
    inner class HappyPathLargeNumberOfVisits {
      private lateinit var migrationResult: MigrationResult
      val nomisLocationId = 12345L
      val dpsLocationId: UUID = UUID.fromString("e7c2a3cc-e5b2-48ff-9e8b-a5038355b36c")
      val nomisVisitId = 2L
      val nomisVisitSlotId = 3L
      val dpsVisitSlotId = 30L

      private lateinit var migrationRequests: List<MigrateVisitRequest>

      @BeforeAll
      fun setUp() {
        dpsApiMock.resetAll()

        // estimated count
        nomisApiMock.stubGetOfficialVisitIds(
          pageNumber = 0,
          pageSize = 1,
          totalElements = 81,
          content = listOf(
            VisitIdResponse(
              visitId = 1,
            ),
          ),
        )
        nomisApiMock.stubGetOfficialVisitIds(
          pageNumber = 2,
          pageSize = 10,
          totalElements = 81,
          content = (21L..30L).map {
            VisitIdResponse(
              visitId = it,
            )
          },
        )
        nomisApiMock.stubGetOfficialVisitIds(
          pageNumber = 4,
          pageSize = 10,
          totalElements = 81,
          content = (41L..50L).map {
            VisitIdResponse(
              visitId = it,
            )
          },
        )
        nomisApiMock.stubGetOfficialVisitIds(
          pageNumber = 6,
          pageSize = 10,
          totalElements = 81,
          content = (61L..70L).map {
            VisitIdResponse(
              visitId = it,
            )
          },
        )
        nomisApiMock.stubGetOfficialVisitIds(
          pageNumber = 8,
          pageSize = 10,
          totalElements = 81,
          content = (81L..81L).map {
            VisitIdResponse(
              visitId = it,
            )
          },
        )

        // first page
        nomisApiMock.stubGetOfficialVisitIdsByLastId(
          visitId = 0,
          content = (1..10).map {
            VisitIdResponse(
              visitId = it.toLong(),
            )
          },
        )

        listOf(11 to 20, 21 to 30, 31 to 40, 41 to 50, 51 to 60, 61 to 70, 71 to 80, 81 to 81).forEach { (startId, endId) ->
          nomisApiMock.stubGetOfficialVisitIdsByLastId(
            visitId = startId.toLong() - 1,
            content = (startId..endId).map {
              VisitIdResponse(
                visitId = it.toLong(),
              )
            },
          )
        }

        // last page
        nomisApiMock.stubGetOfficialVisitIdsByLastId(
          visitId = 81,
          content = emptyList(),
        )

        (1L..81L).forEach {
          mappingApiMock.stubGetByVisitNomisIdsOrNull(
            nomisVisitId = it,
            mapping = null,
          )
        }
        (1L..81L).forEach {
          nomisApiMock.stubGetOfficialVisit(
            visitId = it,
            response = officialVisitResponse().copy(
              internalLocationId = nomisLocationId,
              visitId = it,
              visitSlotId = nomisVisitSlotId,
            ),
          )
        }
        mappingApiMock.stubGetInternalLocationByNomisId(
          nomisLocationId = nomisLocationId,
          mapping = LocationMappingDto(
            dpsLocationId = dpsLocationId.toString(),
            nomisLocationId = nomisLocationId,
            mappingType = LocationMappingDto.MappingType.LOCATION_CREATED,
          ),
        )
        visitSlotMappingApiMock.stubGetVisitSlotByNomisId(
          nomisId = nomisVisitSlotId,
          mapping = VisitSlotMappingDto(
            dpsId = dpsVisitSlotId.toString(),
            nomisId = nomisVisitSlotId,
            mappingType = VisitSlotMappingDto.MappingType.NOMIS_CREATED,
          ),
        )
        dpsApiMock.stubMigrateVisit()
        mappingApiMock.stubCreateMappingsForMigration()
        mappingApiMock.stubGetMigrationCount(migrationId = ".*", count = 81)
        // wait until all records have individually migrated since status check might finish just before some entities are still in flight due to the "big" numbers
        migrationResult = performMigration {
          verify(telemetryClient, times(81)).trackEvent(eq("officialvisits-migration-entity-migrated"), any(), isNull())
        }
        migrationRequests = getRequestBodies(postRequestedFor(urlPathEqualTo("/migrate/visit")))
      }

      @Test
      fun `will migrate 81 records exactly once`() {
        assertThat(migrationRequests).hasSize(81)
        (1L..81L).forEach { nomisVisitId -> assertThat(migrationRequests.firstOrNull { it.offenderVisitId == nomisVisitId }).isNotNull }
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
      val nomisVisitSlotId = 3L
      val dpsVisitSlotId = "30"

      @BeforeEach
      fun setUp() {
        nomisApiMock.stubGetOfficialVisitIds(
          content = listOf(
            VisitIdResponse(
              visitId = nomisVisitId,
            ),
          ),
        )
        nomisApiMock.stubGetOfficialVisitIdsByLastId(
          content = listOf(
            VisitIdResponse(
              visitId = nomisVisitId,
            ),
          ),
        )
        nomisApiMock.stubGetOfficialVisitIdsByLastId(
          visitId = nomisVisitId,
          content = emptyList(),
        )
        mappingApiMock.stubGetByVisitNomisIdsOrNull(
          nomisVisitId = nomisVisitId,
          mapping = null,
        )
        nomisApiMock.stubGetOfficialVisit(
          visitId = nomisVisitId,
          response = officialVisitResponse().copy(internalLocationId = nomisLocationId, visitId = nomisVisitId, visitSlotId = nomisVisitSlotId),
        )
        mappingApiMock.stubGetInternalLocationByNomisId(
          nomisLocationId = nomisLocationId,
          mapping = LocationMappingDto(
            dpsLocationId = dpsLocationId.toString(),
            nomisLocationId = nomisLocationId,
            mappingType = LocationMappingDto.MappingType.LOCATION_CREATED,
          ),
        )
        visitSlotMappingApiMock.stubGetVisitSlotByNomisId(
          nomisId = nomisVisitSlotId,
          mapping = VisitSlotMappingDto(
            dpsId = dpsVisitSlotId,
            nomisId = nomisVisitSlotId,
            mappingType = VisitSlotMappingDto.MappingType.NOMIS_CREATED,
          ),
        )
        dpsApiMock.stubMigrateVisit(
          response = migrateVisitResponse().copy(
            visit = IdPair(
              elementType = IdPair.ElementType.OFFICIAL_VISIT,
              nomisId = nomisVisitId,
              dpsId = dpsVisitId,
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
              dpsId = dpsVisitId,
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
      val nomisVisitSlotId = 3L
      val dpsVisitSlotId = "30"

      @BeforeEach
      fun setUp() {
        nomisApiMock.stubGetOfficialVisitIds(
          content = listOf(
            VisitIdResponse(
              visitId = nomisVisitId,
            ),
          ),
        )

        nomisApiMock.stubGetOfficialVisitIdsByLastId(
          content = listOf(
            VisitIdResponse(
              visitId = nomisVisitId,
            ),
          ),
        )
        nomisApiMock.stubGetOfficialVisitIdsByLastId(
          visitId = nomisVisitId,
          content = emptyList(),
        )

        mappingApiMock.stubGetByVisitNomisIdsOrNull(
          nomisVisitId = nomisVisitId,
          mapping = null,
        )
        nomisApiMock.stubGetOfficialVisit(
          visitId = nomisVisitId,
          response = officialVisitResponse().copy(internalLocationId = nomisLocationId, visitId = nomisVisitId, visitSlotId = nomisVisitSlotId),
        )
        mappingApiMock.stubGetInternalLocationByNomisId(
          nomisLocationId = nomisLocationId,
          mapping = LocationMappingDto(
            dpsLocationId = dpsLocationId.toString(),
            nomisLocationId = nomisLocationId,
            mappingType = LocationMappingDto.MappingType.LOCATION_CREATED,
          ),
        )
        visitSlotMappingApiMock.stubGetVisitSlotByNomisId(
          nomisId = nomisVisitSlotId,
          mapping = VisitSlotMappingDto(
            dpsId = dpsVisitSlotId,
            nomisId = nomisVisitSlotId,
            mappingType = VisitSlotMappingDto.MappingType.NOMIS_CREATED,
          ),
        )
        dpsApiMock.stubMigrateVisit(
          response = migrateVisitResponse().copy(
            visit = IdPair(
              elementType = IdPair.ElementType.OFFICIAL_VISIT,
              nomisId = nomisVisitId,
              dpsId = dpsVisitId,
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
              dpsId = dpsVisitId,
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

  private fun performMigration(
    body: OfficialVisitsMigrationFilter = OfficialVisitsMigrationFilter(),
    waitUntilVerify: () -> Unit = {
      verify(telemetryClient).trackEvent(
        eq("officialvisits-migration-completed"),
        any(),
        isNull(),
      )
    },
  ): MigrationResult = webTestClient.post().uri("/migrate/official-visits")
    .headers(setAuthorisation(roles = listOf("PRISONER_FROM_NOMIS__MIGRATION__RW")))
    .contentType(MediaType.APPLICATION_JSON)
    .bodyValue(body)
    .exchange()
    .expectStatus().isAccepted.returnResult<MigrationResult>().responseBody.blockFirst()!!
    .also {
      waitUntilCompleted(waitUntilVerify)
    }

  private fun waitUntilCompleted(waitUntilVerify: () -> Unit) = await atMost Duration.ofSeconds(60) untilAsserted {
    waitUntilVerify()
  }
}
