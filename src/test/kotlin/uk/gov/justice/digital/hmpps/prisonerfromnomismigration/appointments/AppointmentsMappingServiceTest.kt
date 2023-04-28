package uk.gov.justice.digital.hmpps.prisonerfromnomismigration.appointments

import com.github.tomakehurst.wiremock.client.WireMock
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.context.annotation.Import
import org.springframework.core.ParameterizedTypeReference
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.activities.ActivitiesConfiguration
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.helper.SpringAPIServiceTest
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.integration.history.DuplicateErrorResponse
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.wiremock.MappingApiExtension

private const val APPOINTMENT_INSTANCE_ID = 1234567L

@SpringAPIServiceTest
@Import(AppointmentsMappingService::class, ActivitiesConfiguration::class)
class AppointmentsMappingServiceTest {
  @Autowired
  private lateinit var appointmentsMappingService: AppointmentsMappingService

  @Nested
  inner class CreateNomisMapping {
    @Test
    fun `will pass oath2 token to service`() {
      MappingApiExtension.mappingApi.stubAppointmentMappingCreate()

      runBlocking {
        appointmentsMappingService.createMapping(
          AppointmentMapping(
            appointmentInstanceId = 1234L,
            nomisEventId = 5678L,
            mappingType = "NOMIS_CREATED",
          ),
          object : ParameterizedTypeReference<DuplicateErrorResponse<AppointmentMapping>>() {},
        )
      }

      MappingApiExtension.mappingApi.verify(
        WireMock.postRequestedFor(
          WireMock.urlPathEqualTo("/mapping/appointments"),
        ).withHeader("Authorization", WireMock.equalTo("Bearer ABCDE")),
      )
    }

    @Test
    fun `will create mapping`() {
      MappingApiExtension.mappingApi.stubAppointmentMappingCreate()

      runBlocking {
        appointmentsMappingService.createMapping(
          AppointmentMapping(
            appointmentInstanceId = APPOINTMENT_INSTANCE_ID,
            nomisEventId = 5678L,
            mappingType = "MAPPING",
          ),
          object : ParameterizedTypeReference<DuplicateErrorResponse<AppointmentMapping>>() {},
        )
      }

      MappingApiExtension.mappingApi.verify(
        WireMock.postRequestedFor(WireMock.urlPathEqualTo("/mapping/appointments"))
          .withRequestBody(WireMock.matchingJsonPath("nomisEventId", WireMock.equalTo("5678")))
          .withRequestBody(WireMock.matchingJsonPath("appointmentInstanceId", WireMock.equalTo(APPOINTMENT_INSTANCE_ID.toString())))
          .withRequestBody(WireMock.matchingJsonPath("mappingType", WireMock.equalTo("MAPPING"))),
      )
    }
  }
}
