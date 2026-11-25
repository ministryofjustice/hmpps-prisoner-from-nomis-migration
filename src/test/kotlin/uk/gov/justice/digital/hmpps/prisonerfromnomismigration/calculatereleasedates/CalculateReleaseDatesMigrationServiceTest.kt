package uk.gov.justice.digital.hmpps.prisonerfromnomismigration.calculatereleasedates

import kotlinx.coroutines.test.runTest
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.verifyNoInteractions
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.json.JsonTest
import org.springframework.web.reactive.function.client.WebClient
import tools.jackson.databind.json.JsonMapper
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomisprisoner.model.PrisonNumberAndRootOffenderId
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.service.MigrationType
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.service.NomisApiService

@JsonTest
internal class CalculateReleaseDatesMigrationServiceTest(@Autowired private val jsonMapper: JsonMapper) {
  private val nomisApi = mock<NomisApiService>()
  private val calculateReleaseDatesNomisApi = mock<CalculateReleaseDatesNomisApiService>()
  private val dpsApi = mock<CalculateReleaseDatesApiService>()
  private val service = CalculateReleaseDatesMigrationService(
    mappingApiWebClient = mock<WebClient>(),
    nomisApi = nomisApi,
    calculateReleaseDatesNomisApi = calculateReleaseDatesNomisApi,
    dpsApi = dpsApi,
    jsonMapper = jsonMapper,
    pageSize = 10,
    completeCheckDelaySeconds = 10,
    completeCheckRetrySeconds = 1,
    completeCheckCount = 9,
    completeCheckScheduledRetrySeconds = 10,
  )

  @Nested
  inner class Queue {
    @Test
    fun `is registered against the calculate release dates queue`() {
      assertThat(MigrationType.CALCULATE_RELEASE_DATES.queueId)
        .isEqualTo("migrationcalculatereleasedates")
    }
  }

  @Nested
  inner class SinglePrisonerFilter {
    private val filter = CalculateReleaseDatesMigrationFilter(prisonerNumber = "A1234KT")

    @Test
    fun `counts a single prisoner without calling NOMIS`() = runTest {
      assertThat(service.getTotalNumberOfIds(filter)).isEqualTo(1)
      verifyNoInteractions(nomisApi)
    }

    @Test
    fun `uses the supplied prisoner number without calling NOMIS`() = runTest {
      val ids = service.getPageOfIdsFromIdRange(null, null, filter)

      assertThat(ids).containsExactly(PrisonNumberAndRootOffenderId(0, "A1234KT"))
      assertThat(service.getRangeOfIds(filter, 10)).hasSize(1)
      verifyNoInteractions(nomisApi)
    }
  }

  @Test
  fun `has no mapping to count`() = runTest {
    assertThat(service.getMigrationCount("2020-05-23T11:30:00")).isZero
  }

  @Nested
  inner class ParseContextNomisId {
    @Test
    fun `parses the prisoner id from the message body`() {
      val message = service.parseContextNomisId(
        """{"type":"MIGRATE_ENTITY","context":{"type":"CALCULATE_RELEASE_DATES","migrationId":"2020-05-23T11:30:00","estimatedCount":1,"body":{"rootOffenderId":123,"prisonNumber":"A1234KT"}}}""",
      )

      assertThat(message.context.body).isEqualTo(PrisonNumberAndRootOffenderId(123, "A1234KT"))
      assertThat(message.context.type).isEqualTo(MigrationType.CALCULATE_RELEASE_DATES)
    }
  }
}
