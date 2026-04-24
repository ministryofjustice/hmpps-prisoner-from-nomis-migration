package uk.gov.justice.digital.hmpps.prisonerfromnomismigration.officialvisits

import com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor
import com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.integration.SqsIntegrationTestBase
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomismappings.model.LocationMappingDto
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomismappings.model.VisitSlotMappingDto
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomismappings.model.VisitTimeSlotMappingDto
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomisprisoner.api.VisitsConfigurationResourceApi
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomisprisoner.model.NomisAudit
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomisprisoner.model.VisitInternalLocationResponse
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomisprisoner.model.VisitTimeSlotResponse
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.officialvisits.OfficialVisitsDpsApiExtension.Companion.dpsOfficialVisitsServer
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.officialvisits.OfficialVisitsDpsApiExtension.Companion.getRequestBody
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.officialvisits.OfficialVisitsDpsApiMockServer.Companion.syncTimeSlot
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.officialvisits.OfficialVisitsDpsApiMockServer.Companion.syncVisitSlot
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.officialvisits.VisitSlotsNomisApiMockServer.Companion.visitSlotResponse
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.officialvisits.VisitSlotsNomisApiMockServer.Companion.visitTimeSlotResponse
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.officialvisits.model.DayType
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.officialvisits.model.SyncCreateTimeSlotRequest
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.officialvisits.model.SyncCreateVisitSlotRequest
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.wiremock.withRequestBodyJsonPath
import java.time.LocalDate
import java.time.LocalDateTime
import java.util.*

class VisitSlotsRepairResourceIntTest(
  @Autowired private val nomisApiMock: VisitSlotsNomisApiMockServer,
  @Autowired private val mappingApiMock: VisitSlotsMappingApiMockServer,
) : SqsIntegrationTestBase() {

  private val dpsApiMock = dpsOfficialVisitsServer
  val nomisTimeslotSequence = 2
  val nomisWeekDay = "MON"
  val prisonId = "MDI"
  val nomisAgencyVisitSlotId = 12345L
  val dpsPrisonTimeSlotId = 123L

  @DisplayName("POST /visits/configuration/time-slots/prison-id/{prisonId}/day-of-week/{dayOfWeek}/time-slot-sequence/{timeSlotSequence}")
  @Nested
  inner class CreateTimeSlot {

    @Nested
    inner class Security {
      @Test
      fun `access forbidden when no role`() {
        webTestClient.post().uri("/visits/configuration/time-slots/prison-id/$prisonId/day-of-week/$nomisWeekDay/time-slot-sequence/$nomisTimeslotSequence")
          .headers(setAuthorisation(roles = listOf()))
          .exchange()
          .expectStatus().isForbidden
      }

      @Test
      fun `access forbidden with wrong role`() {
        webTestClient.post().uri("/visits/configuration/time-slots/prison-id/$prisonId/day-of-week/$nomisWeekDay/time-slot-sequence/$nomisTimeslotSequence")
          .headers(setAuthorisation(roles = listOf("ROLE_BANANAS")))
          .exchange()
          .expectStatus().isForbidden
      }

      @Test
      fun `access unauthorised with no auth token`() {
        webTestClient.post().uri("/visits/configuration/time-slots/prison-id/$prisonId/day-of-week/$nomisWeekDay/time-slot-sequence/$nomisTimeslotSequence")
          .exchange()
          .expectStatus().isUnauthorized
      }
    }

    @Nested
    inner class Validation {
      @BeforeEach
      fun setUp() {
        mappingApiMock.stubGetTimeSlotByNomisIdsOrNull(
          nomisPrisonId = prisonId,
          nomisDayOfWeek = nomisWeekDay,
          nomisSlotSequence = nomisTimeslotSequence,
          mapping = VisitTimeSlotMappingDto(
            dpsId = dpsPrisonTimeSlotId.toString(),
            nomisPrisonId = prisonId,
            nomisDayOfWeek = nomisWeekDay,
            nomisSlotSequence = nomisTimeslotSequence,
            mappingType = VisitTimeSlotMappingDto.MappingType.NOMIS_CREATED,
          ),
        )
      }

      @Test
      fun `will throw BadRequest error`() {
        webTestClient.post().uri("/visits/configuration/time-slots/prison-id/$prisonId/day-of-week/$nomisWeekDay/time-slot-sequence/$nomisTimeslotSequence")
          .headers(setAuthorisation(roles = listOf("PRISONER_FROM_NOMIS__UPDATE__RW")))
          .exchange()
          .expectStatus().isBadRequest
      }
    }

    @Nested
    inner class HappyPath {
      @BeforeEach
      fun setUp() {
        mappingApiMock.stubGetTimeSlotByNomisIdsOrNull(
          nomisPrisonId = prisonId,
          nomisDayOfWeek = nomisWeekDay,
          nomisSlotSequence = nomisTimeslotSequence,
          mapping = null,
        )
        nomisApiMock.stubGetVisitTimeSlot(
          prisonId = prisonId,
          dayOfWeek = VisitsConfigurationResourceApi.DayOfWeekGetVisitTimeSlot.MON,
          timeSlotSequence = nomisTimeslotSequence,
          response = visitTimeSlotResponse().copy(
            timeSlotSequence = nomisTimeslotSequence,
            prisonId = prisonId,
            dayOfWeek = VisitTimeSlotResponse.DayOfWeek.MON,
            startTime = "14:00",
            endTime = "15:00",
            effectiveDate = LocalDate.parse("2021-01-01"),
            expiryDate = LocalDate.parse("2031-01-01"),
            audit = NomisAudit(
              createDatetime = LocalDateTime.parse("2021-01-01T10:30"),
              createUsername = "T.SMITH",
            ),
          ),
        )
        dpsApiMock.stubCreateTimeSlot(syncTimeSlot().copy(prisonTimeSlotId = dpsPrisonTimeSlotId))
        mappingApiMock.stubCreateTimeSlotMapping()
        webTestClient.post().uri("/visits/configuration/time-slots/prison-id/$prisonId/day-of-week/$nomisWeekDay/time-slot-sequence/$nomisTimeslotSequence")
          .headers(setAuthorisation(roles = listOf("PRISONER_FROM_NOMIS__UPDATE__RW")))
          .exchange()
          .expectStatus().isCreated
      }

      @Test
      fun `will create time slot in DPS`() {
        val request: SyncCreateTimeSlotRequest = getRequestBody(postRequestedFor(urlPathEqualTo("/sync/time-slot")))
        with(request) {
          assertThat(this.prisonCode).isEqualTo(prisonId)
          assertThat(this.dayCode).isEqualTo(DayType.MON)
          assertThat(this.startTime).isEqualTo("14:00")
          assertThat(this.endTime).isEqualTo("15:00")
          assertThat(this.effectiveDate).isEqualTo(LocalDate.parse("2021-01-01"))
          assertThat(this.expiryDate).isEqualTo(LocalDate.parse("2031-01-01"))
          assertThat(this.createdTime).isEqualTo(LocalDateTime.parse("2021-01-01T10:30"))
          assertThat(this.createdBy).isEqualTo("T.SMITH")
        }
      }

      @Test
      fun `will create mapping`() {
        mappingApiMock.verify(
          postRequestedFor(urlPathEqualTo("/mapping/visit-slots/time-slots"))
            .withRequestBodyJsonPath("dpsId", dpsPrisonTimeSlotId.toString())
            .withRequestBodyJsonPath("nomisPrisonId", prisonId)
            .withRequestBodyJsonPath("nomisDayOfWeek", nomisWeekDay)
            .withRequestBodyJsonPath("nomisSlotSequence", nomisTimeslotSequence)
            .withRequestBodyJsonPath("mappingType", "NOMIS_CREATED"),
        )
      }
    }
  }

  @DisplayName("POST /visits/configuration/time-slots/prison-id/{prisonId}/day-of-week/{dayOfWeek}/time-slot-sequence/{timeSlotSequence}/visit-slot-id/{visitSlotId}")
  @Nested
  inner class CreateVisitSlot {
    val dpsVisitSlotId = 123L

    @Nested
    inner class Security {
      @Test
      fun `access forbidden when no role`() {
        webTestClient.post().uri("/visits/configuration/time-slots/prison-id/$prisonId/day-of-week/$nomisWeekDay/time-slot-sequence/$nomisTimeslotSequence/visit-slot-id/$nomisAgencyVisitSlotId")
          .headers(setAuthorisation(roles = listOf()))
          .exchange()
          .expectStatus().isForbidden
      }

      @Test
      fun `access forbidden with wrong role`() {
        webTestClient.post().uri("/visits/configuration/time-slots/prison-id/$prisonId/day-of-week/$nomisWeekDay/time-slot-sequence/$nomisTimeslotSequence/visit-slot-id/$nomisAgencyVisitSlotId")
          .headers(setAuthorisation(roles = listOf("ROLE_BANANAS")))
          .exchange()
          .expectStatus().isForbidden
      }

      @Test
      fun `access unauthorised with no auth token`() {
        webTestClient.post().uri("/visits/configuration/time-slots/prison-id/$prisonId/day-of-week/$nomisWeekDay/time-slot-sequence/$nomisTimeslotSequence/visit-slot-id/$nomisAgencyVisitSlotId")
          .exchange()
          .expectStatus().isUnauthorized
      }
    }

    @Nested
    inner class Validation {
      @BeforeEach
      fun setUp() {
        mappingApiMock.stubGetVisitSlotByNomisIdOrNull(
          nomisId = nomisAgencyVisitSlotId,
          mapping = VisitSlotMappingDto(
            dpsId = dpsVisitSlotId.toString(),
            nomisId = nomisAgencyVisitSlotId,
            mappingType = VisitSlotMappingDto.MappingType.NOMIS_CREATED,
          ),
        )
      }

      @Test
      fun `will throw BadRequest error`() {
        webTestClient.post().uri("/visits/configuration/time-slots/prison-id/$prisonId/day-of-week/$nomisWeekDay/time-slot-sequence/$nomisTimeslotSequence/visit-slot-id/$nomisAgencyVisitSlotId")
          .headers(setAuthorisation(roles = listOf("PRISONER_FROM_NOMIS__UPDATE__RW")))
          .exchange()
          .expectStatus().isBadRequest
      }
    }

    @Nested
    inner class HappyPath {
      val dpsPrisonTimeSlotId = 43231L
      val dpsLocationId: UUID = UUID.randomUUID()
      val nomisLocationId = 765L

      @BeforeEach
      fun setUp() {
        mappingApiMock.stubGetVisitSlotByNomisIdOrNull(
          nomisId = nomisAgencyVisitSlotId,
          mapping = null,
        )

        nomisApiMock.stubGetVisitTimeSlot(
          prisonId = prisonId,
          dayOfWeek = VisitsConfigurationResourceApi.DayOfWeekGetVisitTimeSlot.MON,
          timeSlotSequence = nomisTimeslotSequence,
          response = visitTimeSlotResponse().copy(
            visitSlots = listOf(
              visitSlotResponse(),
              visitSlotResponse().copy(
                id = nomisAgencyVisitSlotId,
                maxGroups = 2,
                maxAdults = 20,
                internalLocation = VisitInternalLocationResponse(id = nomisLocationId, "MDI-VISIT-LEGAL-1"),
                audit = NomisAudit(
                  createDatetime = LocalDateTime.parse("2021-01-01T10:30"),
                  createUsername = "T.SMITH",
                ),
              ),
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

        mappingApiMock.stubGetTimeSlotByNomisIds(
          nomisPrisonId = prisonId,
          nomisDayOfWeek = nomisWeekDay,
          nomisSlotSequence = nomisTimeslotSequence,
          mapping = VisitTimeSlotMappingDto(
            dpsId = dpsPrisonTimeSlotId.toString(),
            nomisPrisonId = prisonId,
            nomisDayOfWeek = nomisWeekDay,
            nomisSlotSequence = nomisTimeslotSequence,
            mappingType = VisitTimeSlotMappingDto.MappingType.NOMIS_CREATED,
          ),
        )

        dpsApiMock.stubCreateVisitSlot(response = syncVisitSlot().copy(visitSlotId = dpsVisitSlotId))
        mappingApiMock.stubCreateVisitSlotMapping()
        webTestClient.post().uri("/visits/configuration/time-slots/prison-id/$prisonId/day-of-week/$nomisWeekDay/time-slot-sequence/$nomisTimeslotSequence/visit-slot-id/$nomisAgencyVisitSlotId")
          .headers(setAuthorisation(roles = listOf("PRISONER_FROM_NOMIS__UPDATE__RW")))
          .exchange()
          .expectStatus().isCreated
      }

      @Test
      fun `will create visit slot in DPS`() {
        val request: SyncCreateVisitSlotRequest = getRequestBody(postRequestedFor(urlPathEqualTo("/sync/visit-slot")))
        with(request) {
          assertThat(this.prisonTimeSlotId).isEqualTo(dpsPrisonTimeSlotId)
          assertThat(this.dpsLocationId).isEqualTo(dpsLocationId)
          assertThat(this.maxAdults).isEqualTo(20)
          assertThat(this.maxGroups).isEqualTo(2)
          assertThat(this.createdTime).isEqualTo(LocalDateTime.parse("2021-01-01T10:30"))
          assertThat(this.createdBy).isEqualTo("T.SMITH")
        }
      }

      @Test
      fun `will create mapping`() {
        mappingApiMock.verify(
          postRequestedFor(urlPathEqualTo("/mapping/visit-slots/visit-slot"))
            .withRequestBodyJsonPath("dpsId", dpsVisitSlotId.toString())
            .withRequestBodyJsonPath("nomisId", nomisAgencyVisitSlotId)
            .withRequestBodyJsonPath("mappingType", "NOMIS_CREATED"),
        )
      }
    }
  }
}
