package uk.gov.justice.digital.hmpps.prisonerfromnomismigration.activities

import com.github.tomakehurst.wiremock.client.WireMock
import com.github.tomakehurst.wiremock.client.WireMock.aResponse
import com.github.tomakehurst.wiremock.client.WireMock.anyUrl
import com.github.tomakehurst.wiremock.client.WireMock.equalTo
import com.github.tomakehurst.wiremock.client.WireMock.get
import com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor
import com.github.tomakehurst.wiremock.client.WireMock.matchingJsonPath
import com.github.tomakehurst.wiremock.client.WireMock.post
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
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.context.annotation.Import
import org.springframework.core.ParameterizedTypeReference
import org.springframework.http.HttpStatus
import org.springframework.http.HttpStatus.INTERNAL_SERVER_ERROR
import org.springframework.http.HttpStatus.NOT_FOUND
import org.springframework.web.reactive.function.client.WebClientResponseException.InternalServerError
import org.springframework.web.reactive.function.client.WebClientResponseException.NotFound
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.helper.SpringAPIServiceTest
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.integration.history.DuplicateErrorResponse
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomismappings.model.ActivityMigrationMappingDto
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.wiremock.MappingApiExtension
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.wiremock.MappingApiExtension.Companion.mappingApi
import java.util.UUID

@SpringAPIServiceTest
@Import(ActivitiesMappingService::class, ActivitiesConfiguration::class)
class ActivitiesMappingServiceTest {

  @Autowired
  private lateinit var activitiesMappingService: ActivitiesMappingService

  @Nested
  inner class CreateNomisMapping {
    @Test
    fun `should provide oath2 token`() {
      mappingApi.stubMappingCreate(MappingApiExtension.ACTIVITIES_CREATE_MAPPING_URL)

      runBlocking {
        activitiesMappingService.createMapping(
          ActivityMigrationMappingDto(
            nomisCourseActivityId = 1234L,
            activityId = 2345L,
            activityId2 = null,
            label = "some-migration-id",
          ),
          object : ParameterizedTypeReference<DuplicateErrorResponse<ActivityMigrationMappingDto>>() {},
        )
      }

      mappingApi.verify(
        postRequestedFor(
          urlPathEqualTo("/mapping/activities/migration"),
        ).withHeader("Authorization", equalTo("Bearer ABCDE")),
      )
    }

    @Test
    fun `should create mapping`() {
      mappingApi.stubMappingCreate(MappingApiExtension.ACTIVITIES_CREATE_MAPPING_URL)

      runBlocking {
        activitiesMappingService.createMapping(
          ActivityMigrationMappingDto(
            nomisCourseActivityId = 1234L,
            activityId = 2345L,
            activityId2 = 3456L,
            label = "some-migration-id",
          ),
          object : ParameterizedTypeReference<DuplicateErrorResponse<ActivityMigrationMappingDto>>() {},
        )
      }

      mappingApi.verify(
        postRequestedFor(urlPathEqualTo("/mapping/activities/migration"))
          .withRequestBody(matchingJsonPath("nomisCourseActivityId", equalTo("1234")))
          .withRequestBody(matchingJsonPath("activityId", equalTo("2345")))
          .withRequestBody(matchingJsonPath("activityId2", equalTo("3456")))
          .withRequestBody(matchingJsonPath("label", equalTo("some-migration-id"))),
      )
    }

    @Test
    fun `should throw exception for any error`() {
      mappingApi.stubFor(
        post(urlPathMatching("/mapping/activities/migration")).willReturn(
          aResponse()
            .withHeader("Content-Type", "application/json")
            .withStatus(INTERNAL_SERVER_ERROR.value())
            .withBody("""{"message":"Tea"}"""),
        ),
      )

      assertThatThrownBy {
        runBlocking {
          activitiesMappingService.createMapping(
            ActivityMigrationMappingDto(
              nomisCourseActivityId = 1234L,
              activityId = 2345L,
              activityId2 = 3456L,
              label = "some-migration-id",
            ),
            object : ParameterizedTypeReference<DuplicateErrorResponse<ActivityMigrationMappingDto>>() {},
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
        get(urlPathMatching("/mapping/activities/migration/nomis-course-activity-id/.*")).willReturn(
          aResponse()
            .withHeader("Content-Type", "application/json")
            .withStatus(HttpStatus.OK.value())
            .withBody(
              """
            {
              "nomisCourseActivityId": 1234,
              "activityId": "2345",
              "activityId2": "3456",
              "label": "2020-01-01T00:00:00"
            }
              """.trimIndent(),
            ),
        ),
      )

      runBlocking {
        activitiesMappingService.findNomisMapping(1234)
      }

      mappingApi.verify(
        getRequestedFor(
          urlPathEqualTo("/mapping/activities/migration/nomis-course-activity-id/1234"),
        ).withHeader("Authorization", equalTo("Bearer ABCDE")),
      )
    }

    @Test
    fun `should return null when not found`() {
      mappingApi.stubFor(
        get(urlPathMatching("/mapping/activities/migration/nomis-course-activity-id/.*")).willReturn(
          aResponse()
            .withHeader("Content-Type", "application/json")
            .withStatus(NOT_FOUND.value())
            .withBody("""{"message":"Not found"}"""),
        ),
      )

      assertThat(
        runBlocking { activitiesMappingService.findNomisMapping(1234) },
      ).isNull()
    }

    @Test
    fun `should return the mapping when found`(): Unit = runBlocking {
      mappingApi.stubFor(
        get(urlPathMatching("/mapping/activities/migration/nomis-course-activity-id/.*")).willReturn(
          aResponse()
            .withHeader("Content-Type", "application/json")
            .withStatus(HttpStatus.OK.value())
            .withBody(
              """
            {
              "nomisCourseActivityId": 1234,
              "activityId": "2345",
              "activityId2": "3456",
              "label": "2020-01-01T00:00:00"
            }
              """.trimIndent(),
            ),
        ),
      )
      val mapping = activitiesMappingService.findNomisMapping(1234)
      assertThat(mapping).isNotNull
      assertThat(mapping!!.nomisCourseActivityId).isEqualTo(1234)
      assertThat(mapping.activityId).isEqualTo(2345)
      assertThat(mapping.activityId2).isEqualTo(3456)
      assertThat(mapping.label).isEqualTo("2020-01-01T00:00:00")
    }

    @Test
    fun `should throw exception for any other error`() {
      mappingApi.stubFor(
        get(urlPathMatching("/mapping/activities/migration/nomis-course-activity-id/.*")).willReturn(
          aResponse()
            .withHeader("Content-Type", "application/json")
            .withStatus(INTERNAL_SERVER_ERROR.value())
            .withBody("""{"message":"Tea"}"""),
        ),
      )

      assertThatThrownBy {
        runBlocking {
          activitiesMappingService.findNomisMapping(1234)
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
      activitiesMappingService.findLatestMigration()

      mappingApi.verify(
        getRequestedFor(
          urlPathEqualTo("/mapping/activities/migration/migrated/latest"),
        )
          .withHeader("Authorization", equalTo("Bearer ABCDE")),
      )
    }

    @Test
    internal fun `should return null when not found`(): Unit = runBlocking {
      mappingApi.stubFor(
        get(urlPathEqualTo("/mapping/activities/migration/migrated/latest")).willReturn(
          aResponse()
            .withHeader("Content-Type", "application/json")
            .withStatus(NOT_FOUND.value())
            .withBody("""{"message":"Not found"}"""),
        ),
      )

      assertThat(activitiesMappingService.findLatestMigration()).isNull()
    }

    @Test
    internal fun `should return the mapping when found`(): Unit = runBlocking {
      mappingApi.stubFor(
        get(WireMock.urlEqualTo("/mapping/activities/migration/migrated/latest")).willReturn(
          aResponse()
            .withHeader("Content-Type", "application/json")
            .withBody(
              """
                {
                  "nomisCourseActivityId": 1234,
                  "activityId": "2345",
                  "activityId2": "3456",
                  "label": "2022-02-16T14:20:15",
                  "whenCreated": "2022-02-16T16:21:15.589091"
                }
              """,
            ),
        ),
      )

      val mapping = activitiesMappingService.findLatestMigration()
      assertThat(mapping).isNotNull
      assertThat(mapping?.migrationId).isEqualTo("2022-02-16T14:20:15")
    }

    @Test
    internal fun `should throw exception for any other error`() {
      mappingApi.stubFor(
        get(urlPathMatching("/mapping/activities/migration/migrated/latest")).willReturn(
          aResponse()
            .withHeader("Content-Type", "application/json")
            .withStatus(INTERNAL_SERVER_ERROR.value())
            .withBody("""{"message":"Tea"}"""),
        ),
      )

      assertThatThrownBy {
        runBlocking {
          activitiesMappingService.findLatestMigration()
        }
      }.isInstanceOf(InternalServerError::class.java)
    }
  }

  @Nested
  inner class GetMigrationDetails {
    @BeforeEach
    internal fun setUp() {
      mappingApi.stubActivitiesMappingByMigrationId("2020-01-01T11:10:00")
    }

    @Test
    internal fun `should supply authentication token`(): Unit = runBlocking {
      activitiesMappingService.getMigrationDetails("2020-01-01T10:00:00")

      mappingApi.verify(
        getRequestedFor(
          urlPathMatching("/mapping/activities/migration/migration-id/.*"),
        )
          .withHeader("Authorization", equalTo("Bearer ABCDE")),
      )
    }

    @Test
    internal fun `should throw error when not found`() {
      mappingApi.stubFor(
        get(urlPathMatching("/mapping/activities/migration/migration-id/.*")).willReturn(
          aResponse()
            .withHeader("Content-Type", "application/json")
            .withStatus(NOT_FOUND.value())
            .withBody("""{"message":"Not found"}"""),
        ),
      )

      assertThatThrownBy {
        runBlocking {
          activitiesMappingService.getMigrationDetails("2020-01-01T10:00:00")
        }
      }.isInstanceOf(NotFound::class.java)
    }

    @Test
    internal fun `should return the mapping when found`(): Unit = runBlocking {
      mappingApi.stubActivitiesMappingByMigrationId(
        whenCreated = "2020-01-01T11:10:00",
        count = 7,
      )

      val mapping = activitiesMappingService.getMigrationDetails("2020-01-01T10:00:00")
      assertThat(mapping).isNotNull
      assertThat(mapping.startedDateTime).isEqualTo("2020-01-01T11:10:00")
      assertThat(mapping.count).isEqualTo(7)
    }

    @Test
    internal fun `should throw exception for any other error`() {
      mappingApi.stubFor(
        get(urlPathMatching("/mapping/activities/migration/migration-id/.*")).willReturn(
          aResponse()
            .withHeader("Content-Type", "application/json")
            .withStatus(INTERNAL_SERVER_ERROR.value())
            .withBody("""{"message":"Tea"}"""),
        ),
      )

      assertThatThrownBy {
        runBlocking {
          activitiesMappingService.getMigrationDetails("2020-01-01T10:00:00")
        }
      }.isInstanceOf(InternalServerError::class.java)
    }
  }

  @Nested
  inner class GetMigrationCount {
    @Test
    internal fun `should supply authentication token`(): Unit = runTest {
      mappingApi.stubActivityMappingCountByMigrationId()

      activitiesMappingService.getMigrationCount("2020-01-01T10:00:00")

      mappingApi.verify(
        getRequestedFor(
          urlPathMatching("/mapping/activities/migration-count/migration-id/.*"),
        )
          .withHeader("Authorization", equalTo("Bearer ABCDE")),
      )
    }

    @Test
    internal fun `should return zero when not found`(): Unit = runTest {
      mappingApi.stubActivityMappingCountByMigrationIdFails(NOT_FOUND.value())

      assertThat(activitiesMappingService.getMigrationCount("2020-01-01T10:00:00")).isEqualTo(0)
    }

    @Test
    internal fun `should return the mapping count when found`(): Unit = runTest {
      mappingApi.stubActivityMappingCountByMigrationId(count = 7)

      assertThat(activitiesMappingService.getMigrationCount("2020-01-01T10:00:00")).isEqualTo(7)
    }

    @Test
    internal fun `should throw exception for any other error`() = runTest {
      mappingApi.stubActivityMappingCountByMigrationIdFails(INTERNAL_SERVER_ERROR.value())

      assertThrows<InternalServerError> {
        activitiesMappingService.getMigrationCount("2020-01-01T10:00:00")
      }
    }
  }

  @Nested
  inner class GetDpsLocation {
    private val nomisLocationId = 12345L
    private val dpsLocationId = UUID.randomUUID().toString()

    @Test
    internal fun `will pass oath2 token`() = runTest {
      mappingApi.stubGetApiLocationNomis(nomisLocationId, dpsLocationId)

      activitiesMappingService.getDpsLocation(nomisLocationId)

      mappingApi.verify(
        getRequestedFor(anyUrl())
          .withHeader("Authorization", equalTo("Bearer ABCDE")),
      )
    }

    @Test
    fun `will call the locations endpoint`() = runTest {
      mappingApi.stubGetApiLocationNomis(nomisLocationId, dpsLocationId)

      activitiesMappingService.getDpsLocation(nomisLocationId)

      mappingApi.verify(
        getRequestedFor(urlPathEqualTo("/api/locations/nomis/$nomisLocationId")),
      )
    }

    @Test
    fun `will parse the response`() = runTest {
      mappingApi.stubGetApiLocationNomis(nomisLocationId, dpsLocationId)

      with(activitiesMappingService.getDpsLocation(nomisLocationId)) {
        assertThat(dpsLocationId).isEqualTo(dpsLocationId)
        assertThat(nomisLocationId).isEqualTo(nomisLocationId)
      }
    }

    @Test
    fun `will throw in error`() = runTest {
      mappingApi.stubGetApiLocationNomis(nomisLocationId, NOT_FOUND)

      assertThrows<NotFound> {
        activitiesMappingService.getDpsLocation(nomisLocationId)
      }
    }
  }

  @Nested
  inner class CountMappings {
    @Test
    internal fun `will pass oath2 token`() = runTest {
      mappingApi.stubActivityMappingCountByMigrationId()

      activitiesMappingService.getMigrationCount("some-migration-id")

      mappingApi.verify(
        getRequestedFor(anyUrl())
          .withHeader("Authorization", equalTo("Bearer ABCDE")),
      )
    }

    @Test
    internal fun `will send parameters`() = runTest {
      mappingApi.stubActivityMappingCountByMigrationId()

      activitiesMappingService.getMigrationCount("some-migration-id")

      mappingApi.verify(
        getRequestedFor(urlPathMatching("/mapping/activities/migration-count/migration-id/some-migration-id"))
          .withQueryParam("includeIgnored", equalTo("false")),

      )
    }

    @Test
    fun `will parse the response`() = runTest {
      mappingApi.stubActivityMappingCountByMigrationId(count = 5)

      with(activitiesMappingService.getMigrationCount("some-migration-id")) {
        assertThat(this).isEqualTo(5)
      }
    }

    @Test
    fun `will throw in error`() = runTest {
      mappingApi.stubActivityMappingCountByMigrationIdFails(INTERNAL_SERVER_ERROR.value())

      assertThrows<InternalServerError> {
        activitiesMappingService.getMigrationCount("some-migration-id")
      }
    }
  }
}
