package uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nonassociations

import com.github.tomakehurst.wiremock.client.WireMock
import com.github.tomakehurst.wiremock.client.WireMock.aResponse
import com.github.tomakehurst.wiremock.client.WireMock.equalToJson
import com.github.tomakehurst.wiremock.client.WireMock.get
import com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor
import com.github.tomakehurst.wiremock.client.WireMock.post
import com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor
import com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo
import com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo
import com.github.tomakehurst.wiremock.client.WireMock.urlPathMatching
import kotlinx.coroutines.runBlocking
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.context.annotation.Import
import org.springframework.core.ParameterizedTypeReference
import org.springframework.http.HttpStatus
import org.springframework.web.reactive.function.client.WebClientResponseException
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.helper.SpringAPIServiceTest
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.integration.history.DuplicateErrorResponse
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomismappings.model.NonAssociationMappingDto
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomismappings.model.NonAssociationMappingDto.MappingType.MIGRATED
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.wiremock.MappingApiExtension.Companion.NON_ASSOCIATIONS_CREATE_MAPPING_URL
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.wiremock.MappingApiExtension.Companion.mappingApi

@SpringAPIServiceTest
@Import(NonAssociationsMappingService::class, NonAssociationsConfiguration::class)
internal class NonAssociationsMappingServiceTest {
  @Autowired
  private lateinit var nonAssociationsMappingService: NonAssociationsMappingService

  @Nested
  @DisplayName("findNonAssociationMapping")
  inner class FindNonAssociationMapping {

    @Test
    internal fun `will return null when not found`() {
      mappingApi.stubFor(
        get(urlPathMatching("/mapping/non-associations/firstOffenderNo/.*")).willReturn(
          aResponse()
            .withHeader("Content-Type", "application/json")
            .withStatus(HttpStatus.NOT_FOUND.value())
            .withBody("""{"message":"Not found"}"""),
        ),
      )

      assertThat(
        runBlocking {
          nonAssociationsMappingService.findNomisNonAssociationMapping(
            firstOffenderNo = "A1234BC",
            secondOffenderNo = "D5678EF",
            nomisTypeSequence = 2,
          )
        },
      ).isNull()
    }

    @Test
    internal fun `will return the mapping when found`(): Unit = runBlocking {
      mappingApi.stubFor(
        get(urlPathMatching("/mapping/non-associations/firstOffenderNo/.*")).willReturn(
          aResponse()
            .withHeader("Content-Type", "application/json")
            .withStatus(HttpStatus.OK.value())
            .withBody(
              """
              {
                  "nonAssociationId": 1234,
                  "firstOffenderNo": "A1234BC",                                       
                  "secondOffenderNo": "D5678EF",                   
                  "nomisTypeSequence": 2,                   
                  "label": "5678",
                  "mappingType": "MIGRATED",
                  "whenCreated": "2020-01-01T00:00:00"
              }
              """.trimIndent(),
            ),
        ),
      )

      val mapping = nonAssociationsMappingService.findNomisNonAssociationMapping(
        firstOffenderNo = "A1234BC",
        secondOffenderNo = "D5678EF",
        nomisTypeSequence = 2,
      )
      assertThat(mapping).isNotNull
      assertThat(mapping!!.nonAssociationId).isEqualTo(1234)
      assertThat(mapping.firstOffenderNo).isEqualTo("A1234BC")
      assertThat(mapping.secondOffenderNo).isEqualTo("D5678EF")
      assertThat(mapping.nomisTypeSequence).isEqualTo(2)
      assertThat(mapping.label).isEqualTo("5678")
      assertThat(mapping.mappingType).isEqualTo(MIGRATED)
      assertThat(mapping.whenCreated).isEqualTo("2020-01-01T00:00:00")
    }

    @Test
    internal fun `will throw exception for any other error`() {
      mappingApi.stubFor(
        get(urlPathMatching("/mapping/non-associations/firstOffenderNo/.*")).willReturn(
          aResponse()
            .withHeader("Content-Type", "application/json")
            .withStatus(HttpStatus.INTERNAL_SERVER_ERROR.value())
            .withBody("""{"message":"Tea"}"""),
        ),
      )

      assertThatThrownBy {
        runBlocking {
          nonAssociationsMappingService.findNomisNonAssociationMapping(
            firstOffenderNo = "A1234BC",
            secondOffenderNo = "D5678EF",
            nomisTypeSequence = 2,
          )
        }
      }.isInstanceOf(WebClientResponseException.InternalServerError::class.java)
    }
  }

  @Nested
  @DisplayName("createNonAssociationMapping")
  inner class CreateNonAssociationMapping {
    @BeforeEach
    internal fun setUp() {
      mappingApi.stubFor(
        post(urlEqualTo(NON_ASSOCIATIONS_CREATE_MAPPING_URL)).willReturn(
          aResponse()
            .withHeader("Content-Type", "application/json")
            .withStatus(HttpStatus.CREATED.value()),
        ),
      )
    }

    @Test
    fun `should provide oath2 token`() {
      mappingApi.stubMappingCreate(NON_ASSOCIATIONS_CREATE_MAPPING_URL)

      runBlocking {
        nonAssociationsMappingService.createMapping(
          NonAssociationMappingDto(
            nonAssociationId = 1234,
            firstOffenderNo = "A1234BC",
            secondOffenderNo = "D5678EF",
            nomisTypeSequence = 2,
            label = "some-migration-id",
            mappingType = MIGRATED,
          ),
          object : ParameterizedTypeReference<DuplicateErrorResponse<NonAssociationMappingDto>>() {},
        )
      }

      mappingApi.verify(
        postRequestedFor(
          urlPathEqualTo("/mapping/non-associations"),
        ).withHeader("Authorization", WireMock.equalTo("Bearer ABCDE")),
      )
    }

    @Test
    internal fun `will pass all parameters visit id, migration Id and MIGRATED indicator to mapping service`(): Unit =
      runBlocking {
        nonAssociationsMappingService.createMapping(
          NonAssociationMappingDto(
            nonAssociationId = 1234,
            firstOffenderNo = "A1234BC",
            secondOffenderNo = "D5678EF",
            nomisTypeSequence = 2,
            mappingType = MIGRATED,
            label = "5678",
            whenCreated = "2020-01-01T00:00:00",
          ),
          errorJavaClass = object : ParameterizedTypeReference<DuplicateErrorResponse<NonAssociationMappingDto>>() {},
        )

        mappingApi.verify(
          postRequestedFor(urlEqualTo(NON_ASSOCIATIONS_CREATE_MAPPING_URL))
            .withRequestBody(
              equalToJson(
                """
                  {
                  "nonAssociationId": 1234,
                  "firstOffenderNo": "A1234BC",                                       
                  "secondOffenderNo": "D5678EF",                   
                  "nomisTypeSequence": 2,                   
                  "label": "5678",
                  "mappingType": "MIGRATED",
                  "whenCreated": "2020-01-01T00:00:00"
                  }
                """.trimIndent(),
              ),
            ),
        )
      }

    @Test
    fun `should throw exception for any error`() {
      mappingApi.stubFor(
        post(urlPathMatching(NON_ASSOCIATIONS_CREATE_MAPPING_URL)).willReturn(
          aResponse()
            .withHeader("Content-Type", "application/json")
            .withStatus(HttpStatus.INTERNAL_SERVER_ERROR.value())
            .withBody("""{"message":"Tea"}"""),
        ),
      )

      assertThatThrownBy {
        runBlocking {
          nonAssociationsMappingService.createMapping(
            NonAssociationMappingDto(
              nonAssociationId = 1234,
              firstOffenderNo = "A1234BC",
              secondOffenderNo = "D5678EF",
              nomisTypeSequence = 2,
              mappingType = MIGRATED,
              label = "5678",
              whenCreated = "2020-01-01T00:00:00",
            ),
            object : ParameterizedTypeReference<DuplicateErrorResponse<NonAssociationMappingDto>>() {},
          )
        }
      }.isInstanceOf(WebClientResponseException.InternalServerError::class.java)
    }
  }

  @Nested
  @DisplayName("findLatestMigration")
  inner class FindLatestMigration {
    @BeforeEach
    internal fun setUp() {
      mappingApi.stubLatestMigration("2020-01-01T10:00:00")
    }

    @Test
    internal fun `will supply authentication token`(): Unit = runBlocking {
      nonAssociationsMappingService.findLatestMigration()

      mappingApi.verify(
        getRequestedFor(
          urlPathEqualTo("/mapping/non-associations/migrated/latest"),
        )
          .withHeader("Authorization", WireMock.equalTo("Bearer ABCDE")),
      )
    }

    @Test
    internal fun `will return null when not found`(): Unit = runBlocking {
      mappingApi.stubFor(
        get(urlPathEqualTo("/mapping/non-associations/migrated/latest")).willReturn(
          aResponse()
            .withHeader("Content-Type", "application/json")
            .withStatus(HttpStatus.NOT_FOUND.value())
            .withBody("""{"message":"Not found"}"""),
        ),
      )

      assertThat(nonAssociationsMappingService.findLatestMigration()).isNull()
    }

    @Test
    internal fun `will return the mapping when found`(): Unit = runBlocking {
      mappingApi.stubFor(
        get(urlEqualTo("/mapping/non-associations/migrated/latest")).willReturn(
          aResponse()
            .withHeader("Content-Type", "application/json")
            .withBody(
              """
                {
                  "nonAssociationId": 14478380,
                  "firstOffenderNo": "A1234BC",                                       
                  "secondOffenderNo": "D5678EF",                   
                  "nomisTypeSequence": 1,                   
                  "label": "2022-02-16T14:20:15",
                  "mappingType": "MIGRATED",
                  "whenCreated": "2022-02-16T16:21:15.589091"
                }              
              """,
            ),
        ),
      )

      val mapping = nonAssociationsMappingService.findLatestMigration()
      assertThat(mapping).isNotNull
      assertThat(mapping?.migrationId).isEqualTo("2022-02-16T14:20:15")
    }

    @Test
    internal fun `will throw exception for any other error`() {
      mappingApi.stubFor(
        get(urlPathMatching("/mapping/non-associations/migrated/latest")).willReturn(
          aResponse()
            .withHeader("Content-Type", "application/json")
            .withStatus(HttpStatus.INTERNAL_SERVER_ERROR.value())
            .withBody("""{"message":"Tea"}"""),
        ),
      )

      assertThatThrownBy {
        runBlocking {
          nonAssociationsMappingService.findLatestMigration()
        }
      }.isInstanceOf(WebClientResponseException.InternalServerError::class.java)
    }
  }

  @Nested
  @DisplayName("getMigrationDetails")
  inner class GetMigrationDetails {
    @BeforeEach
    internal fun setUp() {
      mappingApi.stubNonAssociationsMappingByMigrationId("2020-01-01T11:10:00")
    }

    @Test
    internal fun `will supply authentication token`(): Unit = runBlocking {
      nonAssociationsMappingService.getMigrationDetails("2020-01-01T10:00:00")

      mappingApi.verify(
        getRequestedFor(
          urlPathMatching("/mapping/non-associations/migration-id/.*"),
        )
          .withHeader("Authorization", WireMock.equalTo("Bearer ABCDE")),
      )
    }

    @Test
    internal fun `will throw error when not found`() {
      mappingApi.stubFor(
        get(urlPathMatching("/mapping/non-associations/migration-id/.*")).willReturn(
          aResponse()
            .withHeader("Content-Type", "application/json")
            .withStatus(HttpStatus.NOT_FOUND.value())
            .withBody("""{"message":"Not found"}"""),
        ),
      )

      assertThatThrownBy {
        runBlocking {
          nonAssociationsMappingService.getMigrationDetails("2020-01-01T10:00:00")
        }
      }.isInstanceOf(WebClientResponseException.NotFound::class.java)
    }

    @Test
    internal fun `will return the mapping when found`(): Unit = runBlocking {
      mappingApi.stubNonAssociationsMappingByMigrationId(
        whenCreated = "2020-01-01T11:10:00",
        count = 56_766,
      )

      val mapping = nonAssociationsMappingService.getMigrationDetails("2020-01-01T10:00:00")
      assertThat(mapping).isNotNull
      assertThat(mapping.startedDateTime).isEqualTo("2020-01-01T11:10:00")
      assertThat(mapping.count).isEqualTo(56766)
    }

    @Test
    internal fun `will throw exception for any other error`() {
      mappingApi.stubFor(
        get(urlPathMatching("/mapping/non-associations/migration-id/.*")).willReturn(
          aResponse()
            .withHeader("Content-Type", "application/json")
            .withStatus(HttpStatus.INTERNAL_SERVER_ERROR.value())
            .withBody("""{"message":"Tea"}"""),
        ),
      )

      assertThatThrownBy {
        runBlocking {
          nonAssociationsMappingService.getMigrationDetails("2020-01-01T10:00:00")
        }
      }.isInstanceOf(WebClientResponseException.InternalServerError::class.java)
    }
  }

  @Nested
  @DisplayName("getMigrationCount")
  inner class GetMigrationCount {
    @BeforeEach
    internal fun setUp() {
      mappingApi.stubNonAssociationsMappingByMigrationId(count = 56_766)
    }

    @Test
    internal fun `will supply authentication token`(): Unit = runBlocking {
      nonAssociationsMappingService.getMigrationCount("2020-01-01T10:00:00")

      mappingApi.verify(
        getRequestedFor(
          urlPathMatching("/mapping/non-associations/migration-id/.*"),
        )
          .withHeader("Authorization", WireMock.equalTo("Bearer ABCDE")),
      )
    }

    @Test
    internal fun `will return zero when not found`(): Unit = runBlocking {
      mappingApi.stubFor(
        get(urlPathMatching("/mapping/non-associations/migration-id/.*")).willReturn(
          aResponse()
            .withHeader("Content-Type", "application/json")
            .withStatus(HttpStatus.NOT_FOUND.value())
            .withBody("""{"message":"Not found"}"""),
        ),
      )

      assertThat(nonAssociationsMappingService.getMigrationCount("2020-01-01T10:00:00")).isEqualTo(0)
    }

    @Test
    internal fun `will return the mapping count when found`(): Unit = runBlocking {
      mappingApi.stubNonAssociationsMappingByMigrationId(
        whenCreated = "2020-01-01T11:10:00",
        count = 54_766,
      )

      assertThat(nonAssociationsMappingService.getMigrationCount("2020-01-01T10:00:00")).isEqualTo(54_766)
    }

    @Test
    internal fun `will throw exception for any other error`() {
      mappingApi.stubFor(
        get(urlPathMatching("/mapping/non-associations/migration-id/.*")).willReturn(
          aResponse()
            .withHeader("Content-Type", "application/json")
            .withStatus(HttpStatus.INTERNAL_SERVER_ERROR.value())
            .withBody("""{"message":"Tea"}"""),
        ),
      )

      assertThatThrownBy {
        runBlocking {
          nonAssociationsMappingService.getMigrationCount("2020-01-01T10:00:00")
        }
      }.isInstanceOf(WebClientResponseException.InternalServerError::class.java)
    }
  }
}
