package uk.gov.justice.digital.hmpps.prisonerfromnomismigration.visits

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.kotlin.any
import org.mockito.kotlin.check
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.springframework.data.domain.PageImpl
import org.springframework.data.domain.Pageable
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.data.MigrationContext
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.service.Messages.MIGRATE_VISITS
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.service.MigrationQueueService
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.service.NomisApiService
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.service.VisitId
import java.time.LocalDateTime

@ExtendWith(MockitoExtension::class)
internal class VisitsMigrationServiceTest {
  private val nomisApiService: NomisApiService = mock()
  private val queueService: MigrationQueueService = mock()
  val service = VisitsMigrationService(nomisApiService = nomisApiService, queueService = queueService)

  @Nested
  @DisplayName("migrateVisits")
  inner class MigrateVisits {
    @BeforeEach
    internal fun setUp() {
      whenever(nomisApiService.getVisits(any(), any(), any(), any(), any(), any())).thenReturn(
        pages(1)
      )
    }

    @Test
    internal fun `will pass filter through to get total count along with a tiny page count`() {
      service.migrateVisits(
        VisitsMigrationFilter(
          prisonIds = listOf("LEI", "BXI"),
          visitTypes = listOf("SCON"),
          fromDateTime = LocalDateTime.parse("2020-01-01T00:00:00"),
          toDateTime = LocalDateTime.parse("2020-01-02T23:00:00"),
        )
      )

      verify(nomisApiService).getVisits(
        listOf("LEI", "BXI"),
        listOf("SCON"),
        LocalDateTime.parse("2020-01-01T00:00:00"),
        LocalDateTime.parse("2020-01-02T23:00:00"),
        0,
        1
      )
    }

    @Test
    internal fun `will pass visit count and filter to queue`() {
      whenever(nomisApiService.getVisits(any(), any(), any(), any(), any(), any())).thenReturn(
        pages(23)
      )
      service.migrateVisits(
        VisitsMigrationFilter(
          prisonIds = listOf("LEI"),
          visitTypes = listOf("SCON"),
          fromDateTime = LocalDateTime.parse("2020-01-01T00:00:00"),
          toDateTime = LocalDateTime.parse("2020-01-02T23:00:00"),
        )
      )

      verify(queueService).sendMessage(
        eq(MIGRATE_VISITS),
        check<MigrationContext<VisitsMigrationFilter>> {
          assertThat(it.estimatedCount).isEqualTo(23)
          assertThat(it.filter.prisonIds).containsExactly("LEI")
          assertThat(it.filter.visitTypes).containsExactly("SCON")
          assertThat(it.filter.fromDateTime).isEqualTo(LocalDateTime.parse("2020-01-01T00:00:00"))
          assertThat(it.filter.toDateTime).isEqualTo(LocalDateTime.parse("2020-01-02T23:00:00"))
        }
      )
    }
  }
}

fun pages(total: Long): PageImpl<VisitId> = PageImpl<VisitId>(
  (1..total).map { VisitId(it) },
  Pageable.ofSize(10),
  total
)
