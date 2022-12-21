@file:Suppress("UNCHECKED_CAST")

package uk.gov.justice.digital.hmpps.prisonerfromnomismigration.visits

import kotlinx.coroutines.runBlocking
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import org.springframework.boot.actuate.info.Info.Builder
import software.amazon.awssdk.services.sqs.SqsAsyncClient
import software.amazon.awssdk.services.sqs.model.GetQueueAttributesRequest
import software.amazon.awssdk.services.sqs.model.GetQueueAttributesResponse
import software.amazon.awssdk.services.sqs.model.GetQueueUrlRequest
import software.amazon.awssdk.services.sqs.model.GetQueueUrlResponse
import software.amazon.awssdk.services.sqs.model.QueueAttributeName
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.data.LatestMigration
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.data.MigratedItem
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.data.MigrationDetails
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.service.VISITS_QUEUE_ID
import uk.gov.justice.hmpps.sqs.HmppsQueue
import uk.gov.justice.hmpps.sqs.HmppsQueueService
import java.time.LocalDateTime
import java.util.concurrent.CompletableFuture

internal class VisitMigrationPropertiesTest {
  private val hmppsQueueService: HmppsQueueService = mock()
  private val visitMappingService: VisitMappingService = mock()
  private val sqsClient: SqsAsyncClient = mock()
  private val migrationQueue = HmppsQueue(VISITS_QUEUE_ID, sqsClient, "queue", sqsClient, "dlq")

  private val visitMigrationProperties = VisitMigrationProperties(hmppsQueueService, visitMappingService)
  private lateinit var details: Map<String, Any>

  private fun build() =
    Builder()
      .apply { visitMigrationProperties.contribute(this) }
      .let {
        it.build().details["last visits migration"] as Map<String, Any>
      }

  @Nested
  @DisplayName("before first migration")
  inner class BeforeFirstMigration {
    @BeforeEach
    internal fun setUp(): Unit = runBlocking {
      mockEmptyQueues()
      whenever(visitMappingService.findLatestMigration()).thenReturn(null)
      details = build()
    }

    @Test
    internal fun `will have a migration section`() {
      assertThat(details).isNotNull
    }

    @Test
    internal fun `show zero for all counts`() {
      assertThat(details["records waiting processing"]).isEqualTo("0")
      assertThat(details["records currently being processed"]).isEqualTo("0")
      assertThat(details["records that have failed"]).isEqualTo("0")
    }

    @Test
    internal fun `will not show migration Id or totals`() {
      assertThat(details["id"]).isNull()
      assertThat(details["records migrated"]).isNull()
      assertThat(details["started"]).isNull()
    }
  }

  @Nested
  @DisplayName("during a migration")
  inner class DuringAMigration {

    @BeforeEach
    internal fun setUp(): Unit = runBlocking {
      mockQueuesWith(messagesOnQueueCount = 20_000, messagesInFlightCount = 16, messagesOnDLQCount = 3)
      whenever(visitMappingService.findLatestMigration()).thenReturn(LatestMigration(migrationId = "2020-01-01T12:00:00"))
      whenever(visitMappingService.getMigrationDetails("2020-01-01T12:00:00")).thenReturn(
        MigrationDetails(
          count = 12_001,
          content = listOf(MigratedItem(whenCreated = LocalDateTime.parse("2020-01-01T12:10:29"))),
        )
      )
      details = build()
    }

    @Test
    internal fun `will have a migration section`() {
      assertThat(details).isNotNull
    }

    @Test
    internal fun `show counts from message queues`() {
      assertThat(details["records waiting processing"]).isEqualTo("20000")
      assertThat(details["records currently being processed"]).isEqualTo("16")
      assertThat(details["records that have failed"]).isEqualTo("3")
    }

    @Test
    internal fun `will show migration Id, counts and date`() {
      assertThat(details["id"]).isEqualTo("2020-01-01T12:00:00")
      assertThat(details["records migrated"]).isEqualTo(12_001L)
      assertThat(details["started"]).isEqualTo(LocalDateTime.parse("2020-01-01T12:10:29"))
    }
  }

  @Nested
  @DisplayName("after a migration")
  inner class AfterAMigration {

    @BeforeEach
    internal fun setUp(): Unit = runBlocking {
      mockEmptyQueues()
      whenever(visitMappingService.findLatestMigration()).thenReturn(LatestMigration(migrationId = "2020-01-01T12:00:00"))
      whenever(visitMappingService.getMigrationDetails("2020-01-01T12:00:00")).thenReturn(
        MigrationDetails(
          count = 12_001,
          content = listOf(MigratedItem(whenCreated = LocalDateTime.parse("2020-01-01T12:10:29"))),
        )
      )
      details = build()
    }

    @Test
    internal fun `will have a migration section`() {
      assertThat(details).isNotNull
    }

    @Test
    internal fun `show zero for all counts`() {
      assertThat(details["records waiting processing"]).isEqualTo("0")
      assertThat(details["records currently being processed"]).isEqualTo("0")
      assertThat(details["records that have failed"]).isEqualTo("0")
    }

    @Test
    internal fun `will show migration Id, counts and date`() {
      assertThat(details["id"]).isEqualTo("2020-01-01T12:00:00")
      assertThat(details["records migrated"]).isEqualTo(12_001L)
      assertThat(details["started"]).isEqualTo(LocalDateTime.parse("2020-01-01T12:10:29"))
    }
  }

  private fun mockQueuesWith(messagesOnQueueCount: Long, messagesInFlightCount: Long, messagesOnDLQCount: Long) {
    whenever(hmppsQueueService.findByQueueId(VISITS_QUEUE_ID)).thenReturn(migrationQueue)
    whenever(sqsClient.getQueueUrl(GetQueueUrlRequest.builder().queueName("queue").build())).thenReturn(someGetQueueUrlResult())
    whenever(sqsClient.getQueueAttributes(someGetQueueAttributesRequest())).thenReturn(
      someGetQueueAttributesResult(
        messagesInFlightCount = messagesInFlightCount,
        messagesOnQueueCount = messagesOnQueueCount
      )
    )
    whenever(sqsClient.getQueueUrl(GetQueueUrlRequest.builder().queueName("dlq").build())).thenReturn(someGetQueueUrlResultForDLQ())
    whenever(sqsClient.getQueueAttributes(someGetQueueAttributesRequestForDLQ())).thenReturn(
      someGetQueueAttributesResultForDLQ(messagesOnDLQCount)
    )
  }

  private fun mockEmptyQueues() {
    mockQueuesWith(0, 0, 0)
  }

  private fun someGetQueueUrlResult() =
    CompletableFuture.completedFuture(GetQueueUrlResponse.builder().queueUrl("queueUrl").build())

  private fun someGetQueueUrlResultForDLQ() =
    CompletableFuture.completedFuture(GetQueueUrlResponse.builder().queueUrl("dlqUrl").build())

  private fun someGetQueueAttributesResult(messagesOnQueueCount: Long, messagesInFlightCount: Long) =
    CompletableFuture.completedFuture(
      GetQueueAttributesResponse.builder()
        .attributes(
          mapOf(
            QueueAttributeName.APPROXIMATE_NUMBER_OF_MESSAGES to "$messagesOnQueueCount",
            QueueAttributeName.APPROXIMATE_NUMBER_OF_MESSAGES_NOT_VISIBLE to "$messagesInFlightCount",
            QueueAttributeName.REDRIVE_POLICY to "any redrive policy"
          )
        )
        .build()
    )

  private fun someGetQueueAttributesResultForDLQ(messagesOnDLQCount: Long) = CompletableFuture.completedFuture(
    GetQueueAttributesResponse.builder()
      .attributes(mapOf(QueueAttributeName.APPROXIMATE_NUMBER_OF_MESSAGES to messagesOnDLQCount.toString()))
      .build()
  )

  private fun someGetQueueAttributesRequest() =
    GetQueueAttributesRequest.builder().queueUrl("queueUrl").attributeNames(QueueAttributeName.ALL).build()

  private fun someGetQueueAttributesRequestForDLQ() =
    GetQueueAttributesRequest.builder().queueUrl("dlqUrl").attributeNames(QueueAttributeName.ALL).build()
}
