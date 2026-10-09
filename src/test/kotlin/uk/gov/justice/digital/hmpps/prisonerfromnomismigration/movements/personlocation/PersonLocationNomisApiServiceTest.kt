package uk.gov.justice.digital.hmpps.prisonerfromnomismigration.movements.personlocation

import com.github.tomakehurst.wiremock.client.WireMock.anyUrl
import com.github.tomakehurst.wiremock.client.WireMock.equalTo
import com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor
import com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo
import kotlinx.coroutines.test.runTest
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.extension.ExtendWith
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.context.annotation.Import
import org.springframework.http.HttpStatus.INTERNAL_SERVER_ERROR
import org.springframework.http.HttpStatus.NOT_FOUND
import org.springframework.web.reactive.function.client.WebClientResponseException
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.helper.SpringAPIServiceTest
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomisprisoner.model.MovementLocation
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomisprisoner.model.PersonLocationMovement
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.wiremock.NomisApiExtension

@ExtendWith(NomisApiExtension::class)
@SpringAPIServiceTest
@Import(PersonLocationNomisApiService::class, PersonLocationNomisApiMockServer::class)
class PersonLocationNomisApiServiceTest {
  @Autowired
  private lateinit var apiService: PersonLocationNomisApiService

  @Autowired
  private lateinit var nomisApiMockServer: PersonLocationNomisApiMockServer

  @Nested
  inner class GetOffenderPersonLocations {
    @Test
    internal fun `will pass oauth2 token to service`() = runTest {
      nomisApiMockServer.stubGetOffenderPersonLocations(offenderNo = "A1234BC")

      apiService.getOffenderPersonLocationsOrNull(offenderNo = "A1234BC")

      nomisApiMockServer.verify(
        getRequestedFor(anyUrl()).withHeader("Authorization", equalTo("Bearer ABCDE")),
      )
    }

    @Test
    internal fun `will pass offender number to service`() = runTest {
      nomisApiMockServer.stubGetOffenderPersonLocations(offenderNo = "A1234BC")

      apiService.getOffenderPersonLocationsOrNull(offenderNo = "A1234BC")

      nomisApiMockServer.verify(
        getRequestedFor(urlPathEqualTo("/movements/A1234BC/person-locations")),
      )
    }

    @Test
    fun `will return offender person locations`() = runTest {
      nomisApiMockServer.stubGetOffenderPersonLocations(offenderNo = "A1234BC")

      val result = apiService.getOffenderPersonLocationsOrNull(offenderNo = "A1234BC")!!

      assertThat(result.bookings).hasSize(1)
      with(result.bookings[0]) {
        assertThat(bookingId).isEqualTo(12345)
        assertThat(movements).hasSize(2)
        with(movements[0]) {
          assertThat(sequence).isEqualTo(1)
          assertThat(movementType).isEqualTo(PersonLocationMovement.MovementType.ADM)
          assertThat(from?.type).isEqualTo(MovementLocation.Type.COURT)
          assertThat(to?.code).isEqualTo("LEI")
          assertThat(scheduleId).isNull()
        }
        with(movements[1]) {
          assertThat(sequence).isEqualTo(2)
          assertThat(from).isNull()
          assertThat(to).isNull()
          assertThat(scheduleId).isEqualTo(54321L)
        }
      }
    }

    @Test
    fun `will return null when offender does not exist`() = runTest {
      nomisApiMockServer.stubGetOffenderPersonLocations(status = NOT_FOUND)

      assertThat(apiService.getOffenderPersonLocationsOrNull(offenderNo = "A1234BC")).isNull()
    }

    @Test
    fun `will throw error when API returns an error`() = runTest {
      nomisApiMockServer.stubGetOffenderPersonLocations(status = INTERNAL_SERVER_ERROR)

      assertThrows<WebClientResponseException.InternalServerError> {
        apiService.getOffenderPersonLocationsOrNull(offenderNo = "A1234BC")
      }
    }
  }
}
