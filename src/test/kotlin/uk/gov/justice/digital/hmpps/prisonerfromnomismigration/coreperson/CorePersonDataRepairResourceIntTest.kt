package uk.gov.justice.digital.hmpps.prisonerfromnomismigration.coreperson

import com.github.tomakehurst.wiremock.client.WireMock.anyUrl
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
import org.mockito.kotlin.verifyNoInteractions
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.http.HttpStatus
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.coreperson.CorePersonCprApiExtension.Companion.cprCorePersonServer
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.coreperson.religion.ReligionsMappingApiMockServer
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.integration.SqsIntegrationTestBase
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.wiremock.withRequestBodyJsonPath
import java.time.LocalDate

class CorePersonDataRepairResourceIntTest : SqsIntegrationTestBase() {
  @Autowired
  private lateinit var nomisApiMockServer: CorePersonNomisApiMockServer

  @Autowired
  private lateinit var mappingApiMockServer: ReligionsMappingApiMockServer

  @DisplayName("POST /prisoners/{prisonNumber}/religion/repair")
  @Nested
  inner class RepairPrisonerReligions {
    val prisonNumber = "A1234KT"

    @Nested
    inner class Security {
      @Test
      fun `access forbidden when no role`() {
        webTestClient.post().uri("/prisoners/$prisonNumber/religion/repair")
          .headers(setAuthorisation(roles = listOf()))
          .exchange()
          .expectStatus().isForbidden
      }

      @Test
      fun `access forbidden with wrong role`() {
        webTestClient.post().uri("/prisoners/$prisonNumber/religion/repair")
          .headers(setAuthorisation(roles = listOf("ROLE_BANANAS")))
          .exchange()
          .expectStatus().isForbidden
      }

      @Test
      fun `access unauthorised with no auth token`() {
        webTestClient.post().uri("/prisoners/$prisonNumber/religion/repair")
          .exchange()
          .expectStatus().isUnauthorized
      }
    }

    @Nested
    inner class HappyPath {
      val prisonNumber = "A1234KT"

      @BeforeEach
      fun setUp() {
        nomisApiMockServer.stubGetOffenderReligions(prisonNumber)
        cprCorePersonServer.stubMigrateCorePersonReligion(prisonNumber)
        mappingApiMockServer.stubReplaceMappings()

        webTestClient.post().uri("/prisoners/$prisonNumber/religion/repair")
          .headers(setAuthorisation(roles = listOf("ROLE_PRISONER_FROM_NOMIS__UPDATE__RW")))
          .exchange()
          .expectStatus().isNoContent
      }

      @Test
      fun `will retrieve current religion for the prisoner`() {
        nomisApiMockServer.verify(getRequestedFor(urlPathEqualTo("/core-person/$prisonNumber/religions")))
      }

      @Test
      fun `will send religion to CPR`() {
        cprCorePersonServer.verify(
          postRequestedFor(urlPathEqualTo("/syscon-sync/religion/$prisonNumber"))
            .withRequestBodyJsonPath("religions[0].nomisReligionId", 2)
            .withRequestBodyJsonPath("religions[0].religionCode", "DRU")
            .withRequestBodyJsonPath("religions[0].startDate", LocalDate.parse("2016-08-02"))
            .withoutQueryParam("religions[0].endDate")
            .withRequestBodyJsonPath("religions[0].verified", true)
            .withRequestBodyJsonPath("religions[0].changeReasonKnown", true)
            .withRequestBodyJsonPath("religions[0].comments", "No longer believes in Zoroastrianism")
            .withRequestBodyJsonPath("religions[0].modifyUserId", "KOFEADDY")
            .withRequestBodyJsonPath("religions[0].modifyDateTime", "2016-08-01T10:55:00"),
        )
      }

      @Test
      fun `will replace mappings`() {
        mappingApiMockServer.verify(
          postRequestedFor(
            urlPathEqualTo("/mapping/core-person-religion/replace"),
          ),
        )
      }

      @Test
      fun `will track telemetry for the repair`() {
        verify(telemetryClient).trackEvent(
          eq("core-person-religion-resynchronisation-repair"),
          check {
            assertThat(it["prisonNumber"]).isEqualTo(prisonNumber)
          },
          isNull(),
        )
      }
    }

    @Nested
    inner class HappyPathNotFound {
      val prisonNumber = "A1234KT"

      @BeforeEach
      fun setUp() {
        nomisApiMockServer.stubGetOffenderReligions(prisonNumber, status = HttpStatus.NOT_FOUND)

        webTestClient.post().uri("/prisoners/$prisonNumber/religion/repair")
          .headers(setAuthorisation(roles = listOf("ROLE_PRISONER_FROM_NOMIS__UPDATE__RW")))
          .exchange()
          .expectStatus().isNotFound
      }

      @Test
      fun `will try to retrieve current religion for the prison`() {
        nomisApiMockServer.verify(getRequestedFor(urlPathEqualTo("/core-person/$prisonNumber/religions")))
      }

      @Test
      fun `will not send religion to CPR`() {
        cprCorePersonServer.verify(0, getRequestedFor(anyUrl()))
      }

      @Test
      fun `will not track telemetry for the repair`() {
        verifyNoInteractions(telemetryClient)
      }
    }
  }
}
