package uk.gov.justice.digital.hmpps.prisonerfromnomismigration.activities

import com.github.tomakehurst.wiremock.client.WireMock
import com.github.tomakehurst.wiremock.client.WireMock.aResponse
import com.github.tomakehurst.wiremock.client.WireMock.equalTo
import com.github.tomakehurst.wiremock.client.WireMock.get
import com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor
import com.github.tomakehurst.wiremock.client.WireMock.matchingJsonPath
import com.github.tomakehurst.wiremock.client.WireMock.post
import com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor
import com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo
import com.github.tomakehurst.wiremock.client.WireMock.urlPathMatching
import kotlinx.coroutines.runBlocking
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.context.annotation.Import
import org.springframework.core.ParameterizedTypeReference
import org.springframework.http.HttpStatus
import org.springframework.web.reactive.function.client.WebClientResponseException.InternalServerError
import org.springframework.web.reactive.function.client.WebClientResponseException.NotFound
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.helper.SpringAPIServiceTest
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.integration.history.DuplicateErrorResponse
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomismappings.model.AllocationMigrationMappingDto
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.wiremock.MappingApiExtension
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.wiremock.MappingApiExtension.Companion.mappingApi

@SpringAPIServiceTest
@Import(AllocationsMappingService::class, ActivitiesConfiguration::class)
class AllocationsMappingServiceTest {

  @Autowired
  private lateinit var allocationsMappingService: AllocationsMappingService

  @Nested
  inner class CreateNomisMapping {
    @Test
    fun `should provide oath2 token`() {
      mappingApi.stubMappingCreate(MappingApiExtension.ALLOCATIONS_CREATE_MAPPING_URL)

      runBlocking {
        allocationsMappingService.createMapping(
          AllocationMigrationMappingDto(
            nomisAllocationId = 1234L,
            activityAllocationId = 2345L,
            activityScheduleId = 3456L,
            label = "some-migration-id",
          ),
          object : ParameterizedTypeReference<DuplicateErrorResponse<AllocationMigrationMappingDto>>() {},
        )
      }

      mappingApi.verify(
        postRequestedFor(
          urlPathEqualTo("/mapping/allocations/migration"),
        ).withHeader("Authorization", equalTo("Bearer ABCDE")),
      )
    }

    @Test
    fun `should create mapping`() {
      mappingApi.stubMappingCreate(MappingApiExtension.ALLOCATIONS_CREATE_MAPPING_URL)

      runBlocking {
        allocationsMappingService.createMapping(
          AllocationMigrationMappingDto(
            nomisAllocationId = 1234L,
            activityAllocationId = 2345L,
            activityScheduleId = 3456L,
            label = "some-migration-id",
          ),
          object : ParameterizedTypeReference<DuplicateErrorResponse<AllocationMigrationMappingDto>>() {},
        )
      }

      mappingApi.verify(
        postRequestedFor(urlPathEqualTo("/mapping/allocations/migration"))
          .withRequestBody(matchingJsonPath("nomisAllocationId", equalTo("1234")))
          .withRequestBody(matchingJsonPath("activityAllocationId", equalTo("2345")))
          .withRequestBody(matchingJsonPath("activityScheduleId", equalTo("3456")))
          .withRequestBody(matchingJsonPath("label", equalTo("some-migration-id"))),
      )
    }

    @Test
    fun `should throw exception for any error`() {
      mappingApi.stubFor(
        post(urlPathMatching("/mapping/allocations/migration")).willReturn(
          aResponse()
            .withHeader("Content-Type", "application/json")
            .withStatus(HttpStatus.INTERNAL_SERVER_ERROR.value())
            .withBody("""{"message":"Tea"}"""),
        ),
      )

      assertThatThrownBy {
        runBlocking {
          allocationsMappingService.createMapping(
            AllocationMigrationMappingDto(
              nomisAllocationId = 1234L,
              activityAllocationId = 2345L,
              activityScheduleId = 3456L,
              label = "some-migration-id",
            ),
            object : ParameterizedTypeReference<DuplicateErrorResponse<AllocationMigrationMappingDto>>() {},
          )
        }
      }.isInstanceOf(InternalServerError::class.java)
    }
  }

  @Nested
  inner class FindNomisMapping {
    @Test
    fun `should provide oath2 token`() {
      mappingApi.stubFor(
        get(urlPathMatching("/mapping/allocations/migration/nomis-allocation-id/.*")).willReturn(
          aResponse()
            .withHeader("Content-Type", "application/json")
            .withStatus(HttpStatus.OK.value())
            .withBody(
              """
            {
              "nomisAllocationId": 1234,
              "activityAllocationId": "2345",
              "activityScheduleId": "3456",
              "label": "2020-01-01T00:00:00"
            }
              """.trimIndent(),
            ),
        ),
      )

      runBlocking {
        allocationsMappingService.findNomisMapping(1234)
      }

      mappingApi.verify(
        getRequestedFor(
          urlPathEqualTo("/mapping/allocations/migration/nomis-allocation-id/1234"),
        ).withHeader("Authorization", equalTo("Bearer ABCDE")),
      )
    }

    @Test
    fun `should return null when not found`() {
      mappingApi.stubFor(
        get(urlPathMatching("/mapping/allocations/migration/nomis-allocation-id/.*")).willReturn(
          aResponse()
            .withHeader("Content-Type", "application/json")
            .withStatus(HttpStatus.NOT_FOUND.value())
            .withBody("""{"message":"Not found"}"""),
        ),
      )

      assertThat(
        runBlocking { allocationsMappingService.findNomisMapping(1234) },
      ).isNull()
    }

    @Test
    fun `should return the mapping when found`(): Unit = runBlocking {
      mappingApi.stubFor(
        get(urlPathMatching("/mapping/allocations/migration/nomis-allocation-id/.*")).willReturn(
          aResponse()
            .withHeader("Content-Type", "application/json")
            .withStatus(HttpStatus.OK.value())
            .withBody(
              """
            {
              "nomisAllocationId": 1234,
              "activityAllocationId": "2345",
              "activityScheduleId": "3456",
              "label": "2020-01-01T00:00:00"
            }
              """.trimIndent(),
            ),
        ),
      )
      val mapping = allocationsMappingService.findNomisMapping(1234)
      assertThat(mapping).isNotNull
      assertThat(mapping!!.nomisAllocationId).isEqualTo(1234)
      assertThat(mapping.activityAllocationId).isEqualTo(2345)
      assertThat(mapping.activityScheduleId).isEqualTo(3456)
      assertThat(mapping.label).isEqualTo("2020-01-01T00:00:00")
    }

    @Test
    fun `should throw exception for any other error`() {
      mappingApi.stubFor(
        get(urlPathMatching("/mapping/allocations/migration/nomis-allocation-id/.*")).willReturn(
          aResponse()
            .withHeader("Content-Type", "application/json")
            .withStatus(HttpStatus.INTERNAL_SERVER_ERROR.value())
            .withBody("""{"message":"Tea"}"""),
        ),
      )

      assertThatThrownBy {
        runBlocking {
          allocationsMappingService.findNomisMapping(1234)
        }
      }.isInstanceOf(InternalServerError::class.java)
    }
  }

  @Nested
  inner class FindLatestMigration {
    @BeforeEach
    internal fun setUp() {
      mappingApi.stubLatestMigration("2020-01-01T10:00:00")
    }

    @Test
    internal fun `should supply authentication token`(): Unit = runBlocking {
      allocationsMappingService.findLatestMigration()

      mappingApi.verify(
        getRequestedFor(
          urlPathEqualTo("/mapping/allocations/migration/migrated/latest"),
        )
          .withHeader("Authorization", equalTo("Bearer ABCDE")),
      )
    }

    @Test
    internal fun `should return null when not found`(): Unit = runBlocking {
      mappingApi.stubFor(
        get(urlPathEqualTo("/mapping/allocations/migration/migrated/latest")).willReturn(
          aResponse()
            .withHeader("Content-Type", "application/json")
            .withStatus(HttpStatus.NOT_FOUND.value())
            .withBody("""{"message":"Not found"}"""),
        ),
      )

      assertThat(allocationsMappingService.findLatestMigration()).isNull()
    }

    @Test
    internal fun `should return the mapping when found`(): Unit = runBlocking {
      mappingApi.stubFor(
        get(WireMock.urlEqualTo("/mapping/allocations/migration/migrated/latest")).willReturn(
          aResponse()
            .withHeader("Content-Type", "application/json")
            .withBody(
              """
                {
                  "nomisAllocationId": 1234,
                  "activityAllocationId": "2345",
                  "activityScheduleId": "3456",
                  "label": "2022-02-16T14:20:15",
                  "whenCreated": "2022-02-16T16:21:15.589091"
                }
              """,
            ),
        ),
      )

      val mapping = allocationsMappingService.findLatestMigration()
      assertThat(mapping).isNotNull
      assertThat(mapping?.migrationId).isEqualTo("2022-02-16T14:20:15")
    }

    @Test
    internal fun `should throw exception for any other error`() {
      mappingApi.stubFor(
        get(urlPathMatching("/mapping/allocations/migration/migrated/latest")).willReturn(
          aResponse()
            .withHeader("Content-Type", "application/json")
            .withStatus(HttpStatus.INTERNAL_SERVER_ERROR.value())
            .withBody("""{"message":"Tea"}"""),
        ),
      )

      assertThatThrownBy {
        runBlocking {
          allocationsMappingService.findLatestMigration()
        }
      }.isInstanceOf(InternalServerError::class.java)
    }
  }

  @Nested
  inner class GetMigrationDetails {
    @BeforeEach
    internal fun setUp() {
      mappingApi.stubAllocationsMappingByMigrationId("2020-01-01T11:10:00")
    }

    @Test
    internal fun `should supply authentication token`(): Unit = runBlocking {
      allocationsMappingService.getMigrationDetails("2020-01-01T10:00:00")

      mappingApi.verify(
        getRequestedFor(
          urlPathMatching("/mapping/allocations/migration/migration-id/.*"),
        )
          .withHeader("Authorization", equalTo("Bearer ABCDE")),
      )
    }

    @Test
    internal fun `should throw error when not found`() {
      mappingApi.stubFor(
        get(urlPathMatching("/mapping/allocations/migration/migration-id/.*")).willReturn(
          aResponse()
            .withHeader("Content-Type", "application/json")
            .withStatus(HttpStatus.NOT_FOUND.value())
            .withBody("""{"message":"Not found"}"""),
        ),
      )

      assertThatThrownBy {
        runBlocking {
          allocationsMappingService.getMigrationDetails("2020-01-01T10:00:00")
        }
      }.isInstanceOf(NotFound::class.java)
    }

    @Test
    internal fun `should return the mapping when found`(): Unit = runBlocking {
      mappingApi.stubAllocationsMappingByMigrationId(
        whenCreated = "2020-01-01T11:10:00",
        count = 56_766,
      )

      val mapping = allocationsMappingService.getMigrationDetails("2020-01-01T10:00:00")
      assertThat(mapping).isNotNull
      assertThat(mapping.startedDateTime).isEqualTo("2020-01-01T11:10:00")
      assertThat(mapping.count).isEqualTo(56766)
    }

    @Test
    internal fun `should throw exception for any other error`() {
      mappingApi.stubFor(
        get(urlPathMatching("/mapping/allocations/migration/migration-id/.*")).willReturn(
          aResponse()
            .withHeader("Content-Type", "application/json")
            .withStatus(HttpStatus.INTERNAL_SERVER_ERROR.value())
            .withBody("""{"message":"Tea"}"""),
        ),
      )

      assertThatThrownBy {
        runBlocking {
          allocationsMappingService.getMigrationDetails("2020-01-01T10:00:00")
        }
      }.isInstanceOf(InternalServerError::class.java)
    }
  }

  @Nested
  inner class GetMigrationCount {

    @Test
    internal fun `should supply authentication token`(): Unit = runBlocking {
      allocationsMappingService.getMigrationCount("2020-01-01T10:00:00")

      mappingApi.verify(
        getRequestedFor(
          urlPathMatching("/mapping/allocations/migration/migration-id/.*"),
        )
          .withHeader("Authorization", equalTo("Bearer ABCDE")),
      )
    }

    @Test
    internal fun `should return zero when not found`(): Unit = runBlocking {
      mappingApi.stubFor(
        get(urlPathMatching("/mapping/alloations/migration/migration-id/.*")).willReturn(
          aResponse()
            .withHeader("Content-Type", "application/json")
            .withStatus(HttpStatus.NOT_FOUND.value())
            .withBody("""{"message":"Not found"}"""),
        ),
      )

      assertThat(allocationsMappingService.getMigrationCount("2020-01-01T10:00:00")).isEqualTo(0)
    }

    @Test
    internal fun `should return the mapping count when found`(): Unit = runBlocking {
      mappingApi.stubAllocationsMappingByMigrationId(
        whenCreated = "2020-01-01T11:10:00",
        count = 56_766,
      )

      assertThat(allocationsMappingService.getMigrationCount("2020-01-01T10:00:00")).isEqualTo(56_766)
    }

    @Test
    internal fun `should throw exception for any other error`() {
      mappingApi.stubFor(
        get(urlPathMatching("/mapping/allocations/migration/migration-id/.*")).willReturn(
          aResponse()
            .withHeader("Content-Type", "application/json")
            .withStatus(HttpStatus.INTERNAL_SERVER_ERROR.value())
            .withBody("""{"message":"Tea"}"""),
        ),
      )

      assertThatThrownBy {
        runBlocking {
          allocationsMappingService.getMigrationCount("2020-01-01T10:00:00")
        }
      }.isInstanceOf(InternalServerError::class.java)
    }
  }
}
