package uk.gov.justice.digital.hmpps.prisonerfromnomismigration.officialvisits

import com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor
import com.github.tomakehurst.wiremock.client.WireMock.putRequestedFor
import com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.mockito.Mockito.eq
import org.mockito.kotlin.check
import org.mockito.kotlin.isNull
import org.mockito.kotlin.verify
import org.springframework.beans.factory.annotation.Autowired
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.integration.SqsIntegrationTestBase
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomismappings.model.LocationMappingDto
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomismappings.model.OfficialVisitMappingDto
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomismappings.model.OfficialVisitorMappingDto
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomismappings.model.VisitSlotMappingDto
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomisprisoner.model.NomisAudit
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.officialvisits.OfficialVisitsDpsApiExtension.Companion.dpsOfficialVisitsServer
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.officialvisits.OfficialVisitsDpsApiExtension.Companion.getRequestBodies
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.officialvisits.OfficialVisitsDpsApiExtension.Companion.getRequestBody
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.officialvisits.OfficialVisitsDpsApiMockServer.Companion.syncOfficialVisit
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.officialvisits.OfficialVisitsDpsApiMockServer.Companion.syncOfficialVisitor
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.officialvisits.OfficialVisitsNomisApiMockServer.Companion.officialVisitResponse
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.officialvisits.OfficialVisitsNomisApiMockServer.Companion.officialVisitor
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.officialvisits.model.SyncCreateOfficialVisitRequest
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.officialvisits.model.SyncCreateOfficialVisitorRequest
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.officialvisits.model.SyncUpdateOfficialVisitRequest
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.wiremock.withRequestBodyJsonPath
import java.time.LocalDateTime
import java.util.*

class OfficialVisitsRepairResourceIntTest(
  @Autowired private val nomisApiMock: OfficialVisitsNomisApiMockServer,
  @Autowired private val visitSlotsMappingApiMock: VisitSlotsMappingApiMockServer,
  @Autowired private val mappingApiMock: OfficialVisitsMappingApiMockServer,
) : SqsIntegrationTestBase() {

  private val dpsApiMock = dpsOfficialVisitsServer

  @DisplayName("POST /prison/{prisonId}/prisoners/{offenderNo}/official-visits/{nomisVisitId}")
  @Nested
  inner class CreateOfficialVisitFromNomis {
    val offenderNo = "A1234KT"
    val prisonId = "MDI"
    val nomisVisitId = 65432L
    val nomisVisitorId = 76544L
    val dpsOfficialVisitId = 8549934L
    val nomisVisitSlotId = 8484L
    val dpsLocationId: UUID = UUID.randomUUID()
    val nomisLocationId = 765L
    val dpsVisitSlotId = 123L
    val dpsOfficialVisitorId = 173193L
    val dpsOfficialVisitorId2 = 273193L
    val nomisVisitorId2 = 73738L

    @Nested
    inner class Security {
      @Test
      fun `access forbidden when no role`() {
        webTestClient.post().uri("/prison/$prisonId/prisoners/$offenderNo/official-visits/$nomisVisitId")
          .headers(setAuthorisation(roles = listOf()))
          .exchange()
          .expectStatus().isForbidden
      }

      @Test
      fun `access forbidden with wrong role`() {
        webTestClient.post().uri("/prison/$prisonId/prisoners/$offenderNo/official-visits/$nomisVisitId")
          .headers(setAuthorisation(roles = listOf("ROLE_BANANAS")))
          .exchange()
          .expectStatus().isForbidden
      }

      @Test
      fun `access unauthorised with no auth token`() {
        webTestClient.post().uri("/prison/$prisonId/prisoners/$offenderNo/official-visits/$nomisVisitId")
          .exchange()
          .expectStatus().isUnauthorized
      }
    }

    @Nested
    inner class HappyPath {
      @BeforeEach
      fun setUp() {
        mappingApiMock.stubGetByVisitNomisIdOrNullNotFoundOnceFollowedBySuccessForever(
          nomisVisitId,
          OfficialVisitMappingDto(
            dpsId = dpsOfficialVisitId.toString(),
            nomisId = nomisVisitId,
            mappingType = OfficialVisitMappingDto.MappingType.NOMIS_CREATED,
          ),
        )
        nomisApiMock.stubGetOfficialVisit(
          visitId = nomisVisitId,
          response = officialVisitResponse().copy(
            internalLocationId = nomisLocationId,
            visitId = nomisVisitId,
            visitSlotId = nomisVisitSlotId,
            visitors = listOf(
              officialVisitor().copy(id = nomisVisitorId),
              officialVisitor().copy(id = nomisVisitorId2),
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

        visitSlotsMappingApiMock.stubGetVisitSlotByNomisIdOrNull(
          nomisId = nomisVisitSlotId,
          mapping = VisitSlotMappingDto(
            dpsId = dpsVisitSlotId.toString(),
            nomisId = nomisVisitSlotId,
            mappingType = VisitSlotMappingDto.MappingType.NOMIS_CREATED,
          ),
        )

        dpsApiMock.stubCreateVisit(
          response = syncOfficialVisit().copy(
            officialVisitId = dpsOfficialVisitId,
          ),
        )

        // called twice for each visitor
        dpsApiMock.stubCreateVisitors(
          officialVisitId = dpsOfficialVisitId,
          response1 = syncOfficialVisitor().copy(officialVisitorId = dpsOfficialVisitorId),
          response2 = syncOfficialVisitor().copy(officialVisitorId = dpsOfficialVisitorId2),
        )

        mappingApiMock.stubCreateVisitMapping()
        mappingApiMock.stubCreateVisitorMapping()

        webTestClient.post().uri("/prison/$prisonId/prisoners/$offenderNo/official-visits/$nomisVisitId")
          .headers(setAuthorisation(roles = listOf("PRISONER_FROM_NOMIS__UPDATE__RW")))
          .exchange()
          .expectStatus().isCreated
      }

      @Test
      fun `will create the official visit in DPS`() {
        val request: SyncCreateOfficialVisitRequest =
          OfficialVisitsDpsApiExtension.Companion.getRequestBody(postRequestedFor(urlPathEqualTo("/sync/official-visit")))
        with(request) {
          assertThat(offenderVisitId).isEqualTo(nomisVisitId)
          assertThat(dpsLocationId).isEqualTo(dpsLocationId)
          assertThat(prisonVisitSlotId).isEqualTo(dpsVisitSlotId)
        }
      }

      @Test
      fun `will create the official visitors in DPS`() {
        val request: List<SyncCreateOfficialVisitorRequest> = getRequestBodies(postRequestedFor(urlPathEqualTo("/sync/official-visit/$dpsOfficialVisitId/visitor")))
        assertThat(request).hasSize(2)
        with(request[0]) {
          assertThat(offenderVisitVisitorId).isEqualTo(nomisVisitorId)
        }
        with(request[1]) {
          assertThat(offenderVisitVisitorId).isEqualTo(nomisVisitorId2)
        }
      }

      @Test
      fun `will create mappings for visit and visitors`() {
        mappingApiMock.verify(
          postRequestedFor(urlPathEqualTo("/mapping/official-visits/visit"))
            .withRequestBodyJsonPath("dpsId", dpsOfficialVisitId.toString())
            .withRequestBodyJsonPath("nomisId", nomisVisitId)
            .withRequestBodyJsonPath("mappingType", "NOMIS_CREATED"),
        )

        mappingApiMock.verify(
          postRequestedFor(urlPathEqualTo("/mapping/official-visits/visitor"))
            .withRequestBodyJsonPath("dpsId", dpsOfficialVisitorId.toString())
            .withRequestBodyJsonPath("nomisId", nomisVisitorId)
            .withRequestBodyJsonPath("mappingType", "NOMIS_CREATED"),
        )

        mappingApiMock.verify(
          postRequestedFor(urlPathEqualTo("/mapping/official-visits/visitor"))
            .withRequestBodyJsonPath("dpsId", dpsOfficialVisitorId2.toString())
            .withRequestBodyJsonPath("nomisId", nomisVisitorId2)
            .withRequestBodyJsonPath("mappingType", "NOMIS_CREATED"),
        )
      }

      @Test
      fun `will track telemetry`() {
        verify(telemetryClient).trackEvent(
          eq("officialvisits-visit-create-repair-success"),
          check {
            assertThat(it["offenderNo"]).isEqualTo(offenderNo)
            assertThat(it["nomisVisitId"]).isEqualTo("$nomisVisitId")
            assertThat(it["nomisVisitorIds"]).isEqualTo("$nomisVisitorId, $nomisVisitorId2")
            assertThat(it["prisonId"]).isEqualTo(prisonId)
            assertThat(it["reason"]).isEqualTo("Visit created. Manual repair")
          },
          isNull(),
        )
      }
    }
  }

  @DisplayName("PUT /prison/{prisonId}/prisoners/{offenderNo}/official-visits/{nomisVisitId}")
  @Nested
  inner class UpdateOfficialVisitFromNomis {
    val offenderNo = "A1234KT"
    val prisonId = "MDI"
    val nomisVisitId = 65432L
    val nomisVisitorId = 76544L
    val dpsOfficialVisitId = 8549934L
    val nomisVisitSlotId = 8484L
    val dpsLocationId: UUID = UUID.randomUUID()
    val nomisLocationId = 765L
    val dpsVisitSlotId = 123L
    val dpsOfficialVisitorId = 173193L
    val dpsOfficialVisitorId2 = 273193L
    val nomisVisitorId2 = 73738L

    @Nested
    inner class Security {
      @Test
      fun `access forbidden when no role`() {
        webTestClient.put().uri("/prison/$prisonId/prisoners/$offenderNo/official-visits/$nomisVisitId")
          .headers(setAuthorisation(roles = listOf()))
          .exchange()
          .expectStatus().isForbidden
      }

      @Test
      fun `access forbidden with wrong role`() {
        webTestClient.put().uri("/prison/$prisonId/prisoners/$offenderNo/official-visits/$nomisVisitId")
          .headers(setAuthorisation(roles = listOf("ROLE_BANANAS")))
          .exchange()
          .expectStatus().isForbidden
      }

      @Test
      fun `access unauthorised with no auth token`() {
        webTestClient.put().uri("/prison/$prisonId/prisoners/$offenderNo/official-visits/$nomisVisitId")
          .exchange()
          .expectStatus().isUnauthorized
      }
    }

    @Nested
    inner class HappyPath {
      @BeforeEach
      fun setUp() {
        mappingApiMock.stubGetByVisitNomisIdOrNull(
          nomisVisitId,
          OfficialVisitMappingDto(
            dpsId = dpsOfficialVisitId.toString(),
            nomisId = nomisVisitId,
            mappingType = OfficialVisitMappingDto.MappingType.NOMIS_CREATED,
          ),
        )
        nomisApiMock.stubGetOfficialVisit(
          visitId = nomisVisitId,
          response = officialVisitResponse().copy(
            internalLocationId = nomisLocationId,
            visitId = nomisVisitId,
            visitSlotId = nomisVisitSlotId,
            audit = NomisAudit(
              createDatetime = LocalDateTime.parse("2020-01-01T10:10:10"),
              createUsername = "J.JOHN",
              modifyDatetime = LocalDateTime.parse("2020-02-01T10:10:10"),
              modifyUserId = "T.SMITH",
            ),
            visitors = listOf(
              officialVisitor().copy(
                id = nomisVisitorId,
                audit = NomisAudit(
                  createDatetime = LocalDateTime.parse("2019-01-01T10:10:10"),
                  createUsername = "S.JOHN",
                ),
              ),
              officialVisitor().copy(id = nomisVisitorId2),
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

        visitSlotsMappingApiMock.stubGetVisitSlotByNomisIdOrNull(
          nomisId = nomisVisitSlotId,
          mapping = VisitSlotMappingDto(
            dpsId = dpsVisitSlotId.toString(),
            nomisId = nomisVisitSlotId,
            mappingType = VisitSlotMappingDto.MappingType.NOMIS_CREATED,
          ),
        )

        dpsApiMock.stubUpdateVisit(
          officialVisitId = dpsOfficialVisitId,
        )

        mappingApiMock.stubGetByVisitorNomisIdOrNull(
          nomisVisitorId,
          OfficialVisitorMappingDto(
            dpsId = dpsOfficialVisitorId.toString(),
            nomisId = nomisVisitorId,
            mappingType = OfficialVisitorMappingDto.MappingType.NOMIS_CREATED,
          ),
        )
        mappingApiMock.stubGetByVisitorNomisIdOrNull(nomisVisitorId2, null)

        // will update one visitor that already exists
        dpsApiMock.stubUpdateVisitor(
          officialVisitId = dpsOfficialVisitId,
          officialVisitorId = dpsOfficialVisitorId,
        )

        // but create the other visitor that doesn't exist yet'
        dpsApiMock.stubCreateVisitor(
          officialVisitId = dpsOfficialVisitId,
          response = syncOfficialVisitor().copy(officialVisitorId = dpsOfficialVisitorId2),
        )

        mappingApiMock.stubCreateVisitorMapping()

        webTestClient.put().uri("/prison/$prisonId/prisoners/$offenderNo/official-visits/$nomisVisitId")
          .headers(setAuthorisation(roles = listOf("PRISONER_FROM_NOMIS__UPDATE__RW")))
          .exchange()
          .expectStatus().isOk
      }

      @Test
      fun `will update the official visit in DPS`() {
        val request: SyncUpdateOfficialVisitRequest = getRequestBody(putRequestedFor(urlPathEqualTo("/sync/official-visit/$dpsOfficialVisitId")))
        with(request) {
          assertThat(offenderVisitId).isEqualTo(nomisVisitId)
          assertThat(dpsLocationId).isEqualTo(dpsLocationId)
          assertThat(prisonVisitSlotId).isEqualTo(dpsVisitSlotId)
        }
      }

      @Test
      fun `will update the official visitor in DPS that already existed`() {
        dpsApiMock.verify(putRequestedFor(urlPathEqualTo("/sync/official-visit/$dpsOfficialVisitId/visitor/$dpsOfficialVisitorId")))
      }

      @Test
      fun `will create the official visitor in DPS that did not exist `() {
        val request: SyncCreateOfficialVisitorRequest = getRequestBody(postRequestedFor(urlPathEqualTo("/sync/official-visit/$dpsOfficialVisitId/visitor")))
        with(request) {
          assertThat(offenderVisitVisitorId).isEqualTo(nomisVisitorId2)
        }
      }

      @Test
      fun `will create mappings for visitor just created`() {
        mappingApiMock.verify(
          postRequestedFor(urlPathEqualTo("/mapping/official-visits/visitor"))
            .withRequestBodyJsonPath("dpsId", dpsOfficialVisitorId2.toString())
            .withRequestBodyJsonPath("nomisId", nomisVisitorId2)
            .withRequestBodyJsonPath("mappingType", "NOMIS_CREATED"),
        )
      }

      @Test
      fun `will track telemetry`() {
        verify(telemetryClient).trackEvent(
          eq("officialvisits-visit-update-repair-success"),
          check {
            assertThat(it["offenderNo"]).isEqualTo(offenderNo)
            assertThat(it["nomisVisitId"]).isEqualTo("$nomisVisitId")
            assertThat(it["nomisVisitorIds"]).isEqualTo("$nomisVisitorId, $nomisVisitorId2")
            assertThat(it["prisonId"]).isEqualTo(prisonId)
            assertThat(it["reason"]).isEqualTo("Visit updated. Manual repair")
          },
          isNull(),
        )
      }
    }
  }
}
