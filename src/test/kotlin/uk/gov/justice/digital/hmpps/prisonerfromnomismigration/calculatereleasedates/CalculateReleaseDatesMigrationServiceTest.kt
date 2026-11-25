package uk.gov.justice.digital.hmpps.prisonerfromnomismigration.calculatereleasedates

import kotlinx.coroutines.test.runTest
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.mockito.kotlin.mock
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.json.JsonTest
import org.springframework.web.reactive.function.client.WebClient
import tools.jackson.databind.json.JsonMapper
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomisprisoner.model.PrisonerId
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.service.MigrationType

@JsonTest
internal class CalculateReleaseDatesMigrationServiceTest(@Autowired private val jsonMapper: JsonMapper) {
  private val service = CalculateReleaseDatesMigrationService(
    webClient = mock<WebClient>(),
    jsonMapper = jsonMapper,
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
  inner class GetIds {
    @Test
    fun `returns no ids until the migration is implemented`() = runTest {
      assertThat(service.getIds()).isEmpty()
    }
  }

  @Nested
  inner class ParseContextNomisId {
    @Test
    fun `parses the prisoner id from the message body`() {
      val message = service.parseContextNomisId(
        """{"type":"MIGRATE_ENTITY","context":{"type":"CALCULATE_RELEASE_DATES","migrationId":"2020-05-23T11:30:00","estimatedCount":1,"body":{"offenderNo":"A1234KT"}}}""",
      )

      assertThat(message.context.body).isEqualTo(PrisonerId("A1234KT"))
      assertThat(message.context.type).isEqualTo(MigrationType.CALCULATE_RELEASE_DATES)
    }
  }
}
