package uk.gov.justice.digital.hmpps.prisonerfromnomismigration.csip

import com.github.tomakehurst.wiremock.client.WireMock.deleteRequestedFor
import com.github.tomakehurst.wiremock.client.WireMock.equalTo
import com.github.tomakehurst.wiremock.client.WireMock.matchingJsonPath
import com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor
import com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.context.annotation.Import
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.csip.CSIPApiExtension.Companion.csipApi
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.helper.SpringAPIServiceTest

@SpringAPIServiceTest
@Import(CSIPService::class, CSIPConfiguration::class)
internal class CSIPServiceTest {

  @Autowired
  private lateinit var csipService: CSIPService

  @Nested
  @DisplayName("POST /migrate/csip-report")
  inner class CreateCSIPForMigration {
    private val nomisCSIPId = 1234L

    @BeforeEach
    internal fun setUp() {
      csipApi.stubCSIPMigrate()

      runBlocking {
        csipService.migrateCSIP(
          CSIPMigrateRequest(
            nomisCSIPId = nomisCSIPId,
            concernDescription = "Fighting on Prisoner Cell Block H",
          ),
        )
      }
    }

    @Test
    fun `should call api with OAuth2 token`() {
      csipApi.verify(
        postRequestedFor(urlEqualTo("/migrate/csip-report"))
          .withHeader("Authorization", equalTo("Bearer ABCDE")),
      )
    }

    @Test
    fun `will pass data to the api`() {
      csipApi.verify(
        postRequestedFor(urlEqualTo("/migrate/csip-report"))
          .withRequestBody(matchingJsonPath("nomisCSIPId", equalTo("$nomisCSIPId")))
          .withRequestBody(matchingJsonPath("concernDescription", equalTo("Fighting on Prisoner Cell Block H"))),
      )
    }
  }

  @Nested
  @DisplayName("POST /csip")
  inner class CreateCSIP {
    private val nomisCSIPId = 1234L

    @BeforeEach
    internal fun setUp() {
      csipApi.stubCSIPInsert()

      runBlocking {
        csipService.createCSIP(
          CSIPSyncRequest(
            nomisCSIPId = nomisCSIPId,
            concernDescription = "Fighting on Prisoner Cell Block H",
          ),
        )
      }
    }

    @Test
    fun `should call api with OAuth2 token`() {
      csipApi.verify(
        postRequestedFor(urlEqualTo("/csip"))
          .withHeader("Authorization", equalTo("Bearer ABCDE")),
      )
    }

    @Test
    fun `will pass data to the api`() {
      csipApi.verify(
        postRequestedFor(urlEqualTo("/csip"))
          .withRequestBody(matchingJsonPath("nomisCSIPId", equalTo("$nomisCSIPId")))
          .withRequestBody(matchingJsonPath("concernDescription", equalTo("Fighting on Prisoner Cell Block H"))),
      )
    }
  }

  @Nested
  @DisplayName("DELETE /csip/{dpsCSIPId}")
  inner class DeleteCSIP {
    private val dpsCSIPId = "a1b2c3d4-e5f6-1234-5678-90a1b2c3d4e5"

    @Nested
    inner class CSIPExists {
      @BeforeEach
      internal fun setUp() {
        csipApi.stubCSIPDelete()
        runBlocking {
          csipService.deleteCSIP(dpsCSIPId = dpsCSIPId)
        }
      }

      @Test
      fun `should call api with OAuth2 token`() {
        csipApi.verify(
          deleteRequestedFor(urlEqualTo("/csip/$dpsCSIPId"))
            .withHeader("Authorization", equalTo("Bearer ABCDE")),
        )
      }
    }

    @Nested
    inner class CSIPAlreadyDeleted {
      @BeforeEach
      internal fun setUp() {
        csipApi.stubCSIPDeleteNotFound()
      }

      @Test
      fun `should ignore 404 error`() {
        runBlocking {
          csipService.deleteCSIP(dpsCSIPId = dpsCSIPId)
        }
      }
    }
  }
}
