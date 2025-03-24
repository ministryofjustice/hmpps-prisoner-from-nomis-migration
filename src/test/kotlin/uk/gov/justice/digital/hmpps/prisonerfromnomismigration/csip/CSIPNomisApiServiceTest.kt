package uk.gov.justice.digital.hmpps.prisonerfromnomismigration.csip

import com.github.tomakehurst.wiremock.client.WireMock.anyUrl
import com.github.tomakehurst.wiremock.client.WireMock.equalTo
import com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor
import com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo
import kotlinx.coroutines.test.runTest
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.context.annotation.Import
import org.springframework.http.HttpStatus
import org.springframework.web.reactive.function.client.WebClientResponseException
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.helper.SpringAPIServiceTest

@SpringAPIServiceTest
@Import(CSIPNomisApiService::class, CSIPConfiguration::class, CSIPNomisApiMockServer::class)
class CSIPNomisApiServiceTest {
  @Autowired
  private lateinit var nomisApiService: CSIPNomisApiService

  @Autowired
  private lateinit var csipNomisApiMockServer: CSIPNomisApiMockServer

  @DisplayName("GET /csip/{nomisCSIPId}")
  @Nested
  inner class GetCSIPReport {
    private val nomisCSIPId: Long = 1234

    @Test
    internal fun `will pass oath2 token to service`() = runTest {
      csipNomisApiMockServer.stubGetCSIP()

      nomisApiService.getCSIP(nomisCSIPId)

      csipNomisApiMockServer.verify(
        getRequestedFor(anyUrl()).withHeader("Authorization", equalTo("Bearer ABCDE")),
      )
    }

    @Test
    internal fun `will pass NOMIS id to service`() = runTest {
      csipNomisApiMockServer.stubGetCSIP()

      nomisApiService.getCSIP(nomisCSIPId)

      csipNomisApiMockServer.verify(getRequestedFor(urlPathEqualTo("/csip/$nomisCSIPId")))
    }

    @Test
    fun `will return csip data`() = runTest {
      csipNomisApiMockServer.stubGetCSIP()

      val csip = nomisApiService.getCSIP(nomisCSIPId)

      assertThat(csip.id).isEqualTo(nomisCSIPId)
      assertThat(csip.bookingId).isEqualTo(1214478)
      assertThat(csip.offender.offenderNo).isEqualTo("A1234BC")
      assertThat(csip.incidentDate).isEqualTo("2024-06-12")
      assertThat(csip.incidentTime).isEqualTo("10:32:12")
    }

    @Test
    fun `will throw error when csip does not exist`() = runTest {
      csipNomisApiMockServer.stubGetCSIP(HttpStatus.NOT_FOUND)

      assertThrows<WebClientResponseException.NotFound> {
        nomisApiService.getCSIP(nomisCSIPId)
      }
    }

    @Test
    fun `will throw error when API returns an error`() = runTest {
      csipNomisApiMockServer.stubGetCSIP(HttpStatus.INTERNAL_SERVER_ERROR)

      assertThrows<WebClientResponseException.InternalServerError> {
        nomisApiService.getCSIP(nomisCSIPId)
      }
    }
  }

  @DisplayName("GET /csip/factors/{nomisCSIPFactorId}")
  @Nested
  inner class GetCSIPFactor {
    private val nomisCSIPFactorId: Long = 12

    @Test
    internal fun `will pass oath2 token to service`() = runTest {
      csipNomisApiMockServer.stubGetCSIPFactor(nomisCSIPFactorId)

      nomisApiService.getCSIPFactor(nomisCSIPFactorId)

      csipNomisApiMockServer.verify(
        getRequestedFor(anyUrl()).withHeader("Authorization", equalTo("Bearer ABCDE")),
      )
    }

    @Test
    internal fun `will pass NOMIS id to service`() = runTest {
      csipNomisApiMockServer.stubGetCSIPFactor(nomisCSIPFactorId)

      nomisApiService.getCSIPFactor(nomisCSIPFactorId)

      csipNomisApiMockServer.verify(getRequestedFor(urlPathEqualTo("/csip/factors/$nomisCSIPFactorId")))
    }

    @Test
    fun `will return csip factor data`() = runTest {
      csipNomisApiMockServer.stubGetCSIPFactor(nomisCSIPFactorId)

      val csipFactor = nomisApiService.getCSIPFactor(nomisCSIPFactorId)

      assertThat(csipFactor.id).isEqualTo(nomisCSIPFactorId)
      assertThat(csipFactor.type.code).isEqualTo("BUL")
      assertThat(csipFactor.type.description).isEqualTo("Bullying")
      assertThat(csipFactor.comment).isEqualTo("Offender causes trouble")
      assertThat(csipFactor.createDateTime).isEqualTo("2024-04-01T10:00:00")
      assertThat(csipFactor.createdBy).isEqualTo("CFACTOR")
      assertThat(csipFactor.lastModifiedDateTime).isNull()
      assertThat(csipFactor.lastModifiedBy).isNull()
    }

    @Test
    fun `will throw error when csip factor does not exist`() = runTest {
      csipNomisApiMockServer.stubGetCSIPFactor(HttpStatus.NOT_FOUND)

      assertThrows<WebClientResponseException.NotFound> {
        nomisApiService.getCSIPFactor(nomisCSIPFactorId)
      }
    }

    @Test
    fun `will throw error when API returns an error`() = runTest {
      csipNomisApiMockServer.stubGetCSIPFactor(HttpStatus.INTERNAL_SERVER_ERROR)

      assertThrows<WebClientResponseException.InternalServerError> {
        nomisApiService.getCSIPFactor(nomisCSIPFactorId)
      }
    }
  }
}
