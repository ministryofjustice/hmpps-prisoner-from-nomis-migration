package uk.gov.justice.digital.hmpps.prisonerfromnomismigration.prisonperson.physicalattributes

import com.github.tomakehurst.wiremock.client.WireMock.anyUrl
import com.github.tomakehurst.wiremock.client.WireMock.equalTo
import com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor
import com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo
import kotlinx.coroutines.test.runTest
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.tuple
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.context.annotation.Import
import org.springframework.http.HttpStatus.INTERNAL_SERVER_ERROR
import org.springframework.http.HttpStatus.NOT_FOUND
import org.springframework.web.reactive.function.client.WebClientResponseException
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.helper.SpringAPIServiceTest
import java.time.LocalDate
import java.time.LocalDateTime

@SpringAPIServiceTest
@Import(PhysicalAttributesNomisApiService::class, PhysicalAttributesNomisApiMockServer::class)
class PhysicalAttributesNomisApiServiceTest {
  @Autowired
  private lateinit var apiService: PhysicalAttributesNomisApiService

  @Autowired
  private lateinit var nomisApi: PhysicalAttributesNomisApiMockServer

  @Nested
  inner class GetPhysicalAttributes {
    @Test
    internal fun `will pass oath2 token to service`() = runTest {
      nomisApi.stubGetPhysicalAttributes(offenderNo = "A1234AA")

      apiService.getPhysicalAttributes(offenderNo = "A1234AA")

      nomisApi.verify(
        getRequestedFor(anyUrl()).withHeader("Authorization", equalTo("Bearer ABCDE")),
      )
    }

    @Test
    internal fun `will pass NOMIS ids to service`() = runTest {
      nomisApi.stubGetPhysicalAttributes(offenderNo = "A1234AA")

      apiService.getPhysicalAttributes(offenderNo = "A1234AA")

      nomisApi.verify(
        getRequestedFor(urlPathEqualTo("/prisoners/A1234AA/physical-attributes")),
      )
    }

    @Test
    fun `will return physical attributes`() = runTest {
      nomisApi.stubGetPhysicalAttributes(offenderNo = "A1234AA")

      val physicalAttributesResponse = apiService.getPhysicalAttributes("A1234AA")

      with(physicalAttributesResponse) {
        assertThat(offenderNo).isEqualTo("A1234AA")
        assertThat(bookings)
          .extracting("bookingId", "startDateTime", "endDateTime", "latestBooking")
          .containsExactly(tuple(1L, "2024-02-03T12:34:56", "2024-10-21T12:34:56", true))
        assertThat(bookings[0].physicalAttributes)
          .extracting("attributeSequence", "heightCentimetres", "weightKilograms", "auditModuleName", "createdBy", "modifiedBy")
          .containsExactly(tuple(1L, 180, 80, "MODULE", "A_USER", "ANOTHER_USER"))
        assertThat(LocalDateTime.parse(bookings[0].physicalAttributes[0].createDateTime).toLocalDate()).isEqualTo(LocalDate.now())
        assertThat(LocalDateTime.parse(bookings[0].physicalAttributes[0].modifiedDateTime!!).toLocalDate()).isEqualTo(LocalDate.now())
      }
    }

    @Test
    fun `will throw error when bookings do not exist`() = runTest {
      nomisApi.stubGetPhysicalAttributes(NOT_FOUND)

      assertThrows<WebClientResponseException.NotFound> {
        apiService.getPhysicalAttributes("A1234AA")
      }
    }

    @Test
    fun `will throw error when API returns an error`() = runTest {
      nomisApi.stubGetPhysicalAttributes(INTERNAL_SERVER_ERROR)

      assertThrows<WebClientResponseException.InternalServerError> {
        apiService.getPhysicalAttributes("A1234AA")
      }
    }
  }
}
