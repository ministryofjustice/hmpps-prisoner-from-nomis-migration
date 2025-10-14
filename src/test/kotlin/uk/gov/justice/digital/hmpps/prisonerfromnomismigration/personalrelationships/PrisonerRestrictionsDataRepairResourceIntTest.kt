package uk.gov.justice.digital.hmpps.prisonerfromnomismigration.personalrelationships

import com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor
import com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor
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
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomisprisoner.model.CodeDescription
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomisprisoner.model.ContactRestrictionEnteredStaff
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomisprisoner.model.PrisonerWithRestrictions
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.personalrelationships.ContactPersonDpsApiExtension.Companion.getRequestBody
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.personalrelationships.ContactPersonDpsApiMockServer.Companion.changedRestrictionsResponse
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.personalrelationships.model.ResetPrisonerRestrictionsRequest
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.wiremock.withRequestBodyJsonPath
import java.time.LocalDate
import java.time.LocalDateTime

class PrisonerRestrictionsDataRepairResourceIntTest : SqsIntegrationTestBase() {
  @Autowired
  private lateinit var nomisApiMock: ContactPersonNomisApiMockServer

  private val dpsApiMock = ContactPersonDpsApiExtension.dpsContactPersonServer

  @Autowired
  private lateinit var mappingApiMock: PrisonerRestrictionMappingApiMockServer

  @DisplayName("POST /prisoners/{offenderNo}/restrictions/resynchronise")
  @Nested
  inner class RepairPrisonerRestrictions {
    val offenderNo = "A1234KT"

    @Nested
    inner class Security {
      @Test
      fun `access forbidden when no role`() {
        webTestClient.post().uri("/prisoners/$offenderNo/restrictions/resynchronise")
          .headers(setAuthorisation(roles = listOf()))
          .exchange()
          .expectStatus().isForbidden
      }

      @Test
      fun `access forbidden with wrong role`() {
        webTestClient.post().uri("/prisoners/$offenderNo/restrictions/resynchronise")
          .headers(setAuthorisation(roles = listOf("ROLE_BANANAS")))
          .exchange()
          .expectStatus().isForbidden
      }

      @Test
      fun `access unauthorised with no auth token`() {
        webTestClient.post().uri("/prisoners/$offenderNo/restrictions/resynchronise")
          .exchange()
          .expectStatus().isUnauthorized
      }
    }

    @Nested
    inner class HappyPath {
      val offenderNo = "A1234KT"

      @BeforeEach
      fun setUp() {
        nomisApiMock.stubGetPrisonerRestrictions(
          offenderNo,
          PrisonerWithRestrictions(
            restrictions = listOf(
              nomisPrisonerRestriction().copy(
                id = 101,
                bookingSequence = 1,
                offenderNo = offenderNo,
                type = CodeDescription("BAN", "Banned"),
                effectiveDate = LocalDate.parse("2021-10-15"),
                enteredStaff = ContactRestrictionEnteredStaff(staffId = 123, username = "H.HARRY"),
                authorisedStaff = ContactRestrictionEnteredStaff(staffId = 456, username = "U.BELLY"),
                audit = nomisAudit().copy(createDatetime = LocalDateTime.parse("2021-10-15T12:00:00")),
                comment = "Banned for life",
                expiryDate = LocalDate.parse("2031-10-15"),
              ),
              nomisPrisonerRestriction().copy(id = 102, bookingSequence = 2),
            ),
          ),
        )
        dpsApiMock.stubResetPrisonerRestrictions(response = changedRestrictionsResponse().copy(createdRestrictions = listOf(1010, 1011)))
        mappingApiMock.stubReplacePrisonerRestrictions(offenderNo)

        webTestClient.post().uri("/prisoners/$offenderNo/restrictions/resynchronise")
          .headers(setAuthorisation(roles = listOf("PRISONER_FROM_NOMIS__UPDATE__RW")))
          .exchange()
          .expectStatus().isOk
      }

      @Test
      fun `will retrieve prisoner restrictions for prisoner`() {
        nomisApiMock.verify(getRequestedFor(urlPathEqualTo("/prisoners/$offenderNo/restrictions")))
      }

      @Test
      fun `will reset the restrictions in DPS`() {
        val request: ResetPrisonerRestrictionsRequest = getRequestBody(postRequestedFor(urlPathEqualTo("/prisoner-restrictions/reset")))
        with(request) {
          assertThat(prisonerNumber).isEqualTo(offenderNo)
          assertThat(restrictions[0].createdBy).isEqualTo("H.HARRY")
          assertThat(restrictions[0].createdTime).isEqualTo(LocalDateTime.parse("2021-10-15T12:00:00"))
          assertThat(restrictions[0].effectiveDate).isEqualTo(LocalDate.parse("2021-10-15"))
          assertThat(restrictions[0].expiryDate).isEqualTo(LocalDate.parse("2031-10-15"))
          assertThat(restrictions[0].authorisedUsername).isEqualTo("U.BELLY")
          assertThat(restrictions[0].commentText).isEqualTo("Banned for life")
          assertThat(restrictions[0].currentTerm).isTrue
          assertThat(restrictions[0].restrictionType).isEqualTo("BAN")
          assertThat(restrictions[1].currentTerm).isFalse
        }
      }

      @Test
      fun `will replace mappings for prisoner number`() {
        mappingApiMock.verify(
          postRequestedFor(urlPathEqualTo("/mapping/contact-person/replace/prisoner-restrictions/$offenderNo"))
            .withRequestBodyJsonPath("mappings[0].dpsId", "1010")
            .withRequestBodyJsonPath("mappings[0].nomisId", 101)
            .withRequestBodyJsonPath("mappings[1].dpsId", "1011")
            .withRequestBodyJsonPath("mappings[1].nomisId", 102)
            .withRequestBodyJsonPath("mappingType", "NOMIS_CREATED"),
        )
      }

      @Test
      fun `will track telemetry for the resynchronise`() {
        verify(telemetryClient).trackEvent(
          eq("from-nomis-synch-contactperson-restrictions-resynchronisation-repair"),
          check {
            assertThat(it["offenderNo"]).isEqualTo(offenderNo)
          },
          isNull(),
        )
      }
    }
  }
}
