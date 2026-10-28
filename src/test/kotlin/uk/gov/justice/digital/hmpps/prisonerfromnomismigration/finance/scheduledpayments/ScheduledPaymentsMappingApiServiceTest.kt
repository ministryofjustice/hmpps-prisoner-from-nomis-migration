package uk.gov.justice.digital.hmpps.prisonerfromnomismigration.finance.scheduledpayments

import com.github.tomakehurst.wiremock.client.WireMock.anyUrl
import com.github.tomakehurst.wiremock.client.WireMock.equalTo
import com.github.tomakehurst.wiremock.client.WireMock.equalToJson
import com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor
import com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor
import com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo
import com.github.tomakehurst.wiremock.client.WireMock.urlPathMatching
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.extension.ExtendWith
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.context.annotation.Import
import org.springframework.core.ParameterizedTypeReference
import org.springframework.http.HttpStatus
import org.springframework.web.reactive.function.client.WebClientResponseException
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.helper.SpringAPIServiceTest
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.integration.history.DuplicateErrorContent
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.integration.history.DuplicateErrorResponse
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomismappings.model.ScheduledPaymentMappingDto
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomismappings.model.ScheduledPaymentMappingDto.MappingType.MIGRATED
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomismappings.model.ScheduledPaymentMappingDto.MappingType.NOMIS_CREATED
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.wiremock.MappingApiExtension
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.wiremock.MappingApiExtension.Companion.mappingApi

@ExtendWith(MappingApiExtension::class)
@SpringAPIServiceTest
@Import(ScheduledPaymentsMappingService::class, ScheduledPaymentsMappingApiMockServer::class)
class ScheduledPaymentsMappingApiServiceTest {
  @Autowired
  private lateinit var apiService: ScheduledPaymentsMappingService

  @Autowired
  private lateinit var mockServer: ScheduledPaymentsMappingApiMockServer

  @Nested
  inner class GetByNomisIdOrNull {
    @Test
    internal fun `will pass oauth2 token to service`() = runTest {
      mockServer.stubGetScheduledPaymentByNomisIdOrNull(nomisScheduledPaymentId = 12345L)

      apiService.getByNomisIdOrNull(nomisId = 12345L)

      mockServer.verify(
        getRequestedFor(anyUrl()).withHeader("Authorization", equalTo("Bearer ABCDE")),
      )
    }

    @Test
    internal fun `will pass NOMIS id to service`() = runTest {
      mockServer.stubGetScheduledPaymentByNomisIdOrNull()

      apiService.getByNomisIdOrNull(nomisId = 12345L)

      mockServer.verify(
        getRequestedFor(urlPathEqualTo("/mapping/scheduled-payments/nomis-id/12345")),
      )
    }

    @Test
    fun `will return dpsId when mapping exists`() = runTest {
      mockServer.stubGetScheduledPaymentByNomisIdOrNull(
        nomisScheduledPaymentId = 12345L,
        mapping = ScheduledPaymentMappingDto(
          dpsId = "A1234BC",
          nomisScheduledPaymentId = 12345L,
          mappingType = MIGRATED,
        ),
      )

      val mapping = apiService.getByNomisIdOrNull(nomisId = 12345L)!!

      assertThat(mapping.dpsId).isEqualTo("A1234BC")
    }

    @Test
    fun `will return null if mapping does not exist`() = runTest {
      mockServer.stubGetScheduledPaymentByNomisIdOrNull(
        nomisScheduledPaymentId = 12345L,
        mapping = null,
      )

      assertThat(apiService.getByNomisIdOrNull(nomisId = 12345L)).isNull()
    }

    @Test
    fun `will propagate errors other than not found`() = runTest {
      mockServer.stubGetByNomisIdFailure()

      assertThrows<WebClientResponseException.InternalServerError> {
        apiService.getByNomisIdOrNull(nomisId = 12345L)
      }
    }
  }

  @Nested
  inner class GetByNomisId {
    @Test
    internal fun `will pass oauth2 token to service`() = runTest {
      mockServer.stubGetByNomisId(nomisScheduledPaymentId = 12345L)

      apiService.getByNomisId(nomisId = 12345L)

      mockServer.verify(
        getRequestedFor(anyUrl()).withHeader("Authorization", equalTo("Bearer ABCDE")),
      )
    }

    @Test
    internal fun `will pass NOMIS id to service`() = runTest {
      mockServer.stubGetByNomisId(nomisScheduledPaymentId = 12345L)

      apiService.getByNomisId(nomisId = 12345L)

      mockServer.verify(
        getRequestedFor(urlPathEqualTo("/mapping/scheduled-payments/nomis-id/12345")),
      )
    }

    @Test
    fun `will return dpsId`() = runTest {
      mockServer.stubGetByNomisId(
        nomisScheduledPaymentId = 12345L,
        mapping = ScheduledPaymentMappingDto(
          dpsId = "A1234BC",
          nomisScheduledPaymentId = 12345L,
          mappingType = MIGRATED,
        ),
      )

      val mapping = apiService.getByNomisId(nomisId = 12345L)

      assertThat(mapping.dpsId).isEqualTo("A1234BC")
    }

    @Test
    fun `will throw when mapping does not exist`() = runTest {
      mockServer.stubGetByNomisId(nomisScheduledPaymentId = 12345L, mapping = null)

      assertThrows<WebClientResponseException.NotFound> {
        apiService.getByNomisId(nomisId = 12345L)
      }
    }

    @Test
    fun `will propagate server errors`() = runTest {
      mockServer.stubGetByNomisIdFailure()

      assertThrows<WebClientResponseException.InternalServerError> {
        apiService.getByNomisId(nomisId = 12345L)
      }
    }
  }

  @Nested
  inner class GetMigrationCount {
    @BeforeEach
    internal fun setUp() {
      mockServer.stubGetMigrationCount(count = 54_766)
    }

    @Test
    internal fun `will supply authentication token`(): Unit = runBlocking {
      apiService.getPagedModelMigrationCount("2020-01-01T10:00:00")

      mappingApi.verify(
        getRequestedFor(
          urlPathMatching("/mapping/scheduled-payments/migration-id/.*"),
        )
          .withHeader("Authorization", equalTo("Bearer ABCDE")),
      )
    }

    @Test
    internal fun `will return zero when not found`(): Unit = runBlocking {
      mockServer.stubGetMigrationCountFailure(HttpStatus.NOT_FOUND)

      assertThat(apiService.getPagedModelMigrationCount("2020-01-01T10:00:00")).isEqualTo(0)
    }

    @Test
    internal fun `will return the mapping count when found`(): Unit = runBlocking {
      mockServer.stubGetMigrationCount(
        migrationId = "2020-01-01T11:10:00",
        count = 54_766,
      )
      assertThat(apiService.getPagedModelMigrationCount("2020-01-01T11:10:00")).isEqualTo(54_766)
    }

    @Test
    internal fun `will throw exception for any other error`() {
      mockServer.stubGetMigrationCountFailure()

      assertThatThrownBy {
        runBlocking {
          apiService.getPagedModelMigrationCount("2020-01-01T10:00:00")
        }
      }.isInstanceOf(WebClientResponseException.InternalServerError::class.java)
    }
  }

  @Nested
  inner class CreateMapping {
    private val mapping = ScheduledPaymentMappingDto(
      mappingType = MIGRATED,
      label = "2020-01-01T10:00",
      dpsId = "de2bc56c-ea73-4f3c-8a37-5a46fdb2d79a",
      nomisScheduledPaymentId = 12345L,
    )

    @Test
    internal fun `will pass oauth2 token to migrate endpoint`() = runTest {
      mockServer.stubCreateMapping()

      apiService.createMapping(
        ScheduledPaymentMappingDto(
          mappingType = MIGRATED,
          label = "2020-01-01T10:00",
          dpsId = "A1234KT",
          nomisScheduledPaymentId = 12345L,
        ),
        object : ParameterizedTypeReference<DuplicateErrorResponse<ScheduledPaymentMappingDto>>() {},
      )

      mockServer.verify(
        postRequestedFor(urlPathEqualTo("/mapping/scheduled-payments")).withHeader(
          "Authorization",
          equalTo("Bearer ABCDE"),
        ),
      )
    }

    @Test
    fun `will return success when OK response`() = runTest {
      mockServer.stubCreateMapping()

      val result = apiService.createMapping(
        ScheduledPaymentMappingDto(
          mappingType = MIGRATED,
          label = "2020-01-01T10:00",
          dpsId = "A1234KT",
          nomisScheduledPaymentId = 12345L,
        ),
        object : ParameterizedTypeReference<DuplicateErrorResponse<ScheduledPaymentMappingDto>>() {},
      )

      assertThat(result.isError).isFalse()
      assertThat(result.errorResponse).isNull()
    }

    @Test
    fun `will send the scheduled payment mapping to the service`() = runTest {
      mockServer.stubCreateMapping()

      apiService.createMapping(
        mapping,
        object : ParameterizedTypeReference<DuplicateErrorResponse<ScheduledPaymentMappingDto>>() {},
      )

      mockServer.verify(
        postRequestedFor(urlPathEqualTo("/mapping/scheduled-payments"))
          .withRequestBody(
            equalToJson(
              """
                {
                  "nomisScheduledPaymentId": 12345,
                  "dpsId": "de2bc56c-ea73-4f3c-8a37-5a46fdb2d79a",
                  "mappingType": "MIGRATED",
                  "label": "2020-01-01T10:00",
                  "whenCreated": null
                }
              """.trimIndent(),
            ),
          ),
      )
    }

    @Test
    fun `will propagate server errors`() = runTest {
      mockServer.stubCreateMappingFailure()

      assertThrows<WebClientResponseException.InternalServerError> {
        apiService.createMapping(
          mapping,
          object : ParameterizedTypeReference<DuplicateErrorResponse<ScheduledPaymentMappingDto>>() {},
        )
      }
    }

    @Test
    fun `will propagate validation errors`() = runTest {
      mockServer.stubCreateMappingFailure(HttpStatus.BAD_REQUEST)

      assertThrows<WebClientResponseException.BadRequest> {
        apiService.createMapping(
          mapping,
          object : ParameterizedTypeReference<DuplicateErrorResponse<ScheduledPaymentMappingDto>>() {},
        )
      }
    }

    @Test
    fun `will succeed when a failed mapping is retried`() = runTest {
      mockServer.stubCreateMappingFailureFollowedBySuccess()

      assertThrows<WebClientResponseException.InternalServerError> {
        apiService.createMapping(
          mapping,
          object : ParameterizedTypeReference<DuplicateErrorResponse<ScheduledPaymentMappingDto>>() {},
        )
      }

      val result = apiService.createMapping(
        mapping,
        object : ParameterizedTypeReference<DuplicateErrorResponse<ScheduledPaymentMappingDto>>() {},
      )

      assertThat(result.isError).isFalse()
      mockServer.verify(2, postRequestedFor(urlPathEqualTo("/mapping/scheduled-payments")))
    }

    @Test
    fun `will return error when 409 conflict`() = runTest {
      val dpsId = "A4321BC"
      val nomisId = 12345L
      val existingDpsId = "existing-scheduled-payment-id"

      mockServer.stubCreateMapping(
        error = DuplicateErrorResponse(
          moreInfo = DuplicateErrorContent(
            duplicate = ScheduledPaymentMappingDto(
              dpsId = dpsId,
              nomisScheduledPaymentId = nomisId,
              mappingType = NOMIS_CREATED,
            ),
            existing = ScheduledPaymentMappingDto(
              dpsId = existingDpsId,
              nomisScheduledPaymentId = nomisId,
              mappingType = NOMIS_CREATED,
            ),
          ),
        ),
      )

      val result = apiService.createMapping(
        ScheduledPaymentMappingDto(
          mappingType = MIGRATED,
          label = "2020-01-01T10:00",
          dpsId = "A1234KT",
          nomisScheduledPaymentId = 12345L,
        ),
        object : ParameterizedTypeReference<DuplicateErrorResponse<ScheduledPaymentMappingDto>>() {},
      )

      assertThat(result.isError).isTrue()
      assertThat(result.errorResponse!!.moreInfo.duplicate.dpsId).isEqualTo(dpsId)
      assertThat(result.errorResponse.moreInfo.duplicate.nomisScheduledPaymentId).isEqualTo(nomisId)
      assertThat(result.errorResponse.moreInfo.existing.dpsId).isEqualTo(existingDpsId)
      assertThat(result.errorResponse.moreInfo.existing.nomisScheduledPaymentId).isEqualTo(nomisId)
    }
  }
}
