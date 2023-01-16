@file:Suppress("UNCHECKED_CAST")

package uk.gov.justice.digital.hmpps.prisonerfromnomismigration.sentencing

import com.amazonaws.services.sqs.AmazonSQS
import com.amazonaws.services.sqs.model.GetQueueAttributesRequest
import com.amazonaws.services.sqs.model.GetQueueAttributesResult
import com.amazonaws.services.sqs.model.GetQueueUrlResult
import com.amazonaws.services.sqs.model.QueueAttributeName
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import org.springframework.boot.actuate.info.Info.Builder
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.data.LatestMigration
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.data.MigratedItem
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.data.MigrationDetails
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.service.SENTENCING_QUEUE_ID
import uk.gov.justice.hmpps.sqs.HmppsQueue
import uk.gov.justice.hmpps.sqs.HmppsQueueService
import java.time.LocalDateTime

internal class SentencingMigrationPropertiesTest {
  private var hmppsQueueService: HmppsQueueService = mock()
  private var sentencingMappingService: SentencingMappingService = mock()
  private val sqsClient: AmazonSQS = mock()
  private var migrationQueue = HmppsQueue(SENTENCING_QUEUE_ID, sqsClient, "queue", sqsClient, "dlq")

  private var sentencingMigrationProperties = SentencingMigrationProperties(hmppsQueueService, sentencingMappingService)
  private lateinit var details: Map<String, Any>

  private fun build() =
    Builder()
      .apply { sentencingMigrationProperties.contribute(this) }
      .let {
        it.build().details["last sentencing migration"] as Map<String, Any>
      }

  @Nested
  @DisplayName("before first migration")
  inner class BeforeFirstMigration {
    @BeforeEach
    internal fun setUp() {
      mockEmptyQueues()
      whenever(sentencingMappingService.findLatestMigration()).thenReturn(null)
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
    internal fun setUp() {
      mockQueuesWith(messagesOnQueueCount = 20_000, messagesInFlightCount = 16, messagesOnDLQCount = 3)
      whenever(sentencingMappingService.findLatestMigration()).thenReturn(LatestMigration(migrationId = "2020-01-01T12:00:00"))
      whenever(sentencingMappingService.getMigrationDetails("2020-01-01T12:00:00")).thenReturn(
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
    internal fun setUp() {
      mockEmptyQueues()
      whenever(sentencingMappingService.findLatestMigration()).thenReturn(LatestMigration(migrationId = "2020-01-01T12:00:00"))
      whenever(sentencingMappingService.getMigrationDetails("2020-01-01T12:00:00")).thenReturn(
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
    whenever(hmppsQueueService.findByQueueId(SENTENCING_QUEUE_ID)).thenReturn(migrationQueue)
    whenever(sqsClient.getQueueUrl("queue")).thenReturn(someGetQueueUrlResult())
    whenever(sqsClient.getQueueAttributes(someGetQueueAttributesRequest())).thenReturn(
      someGetQueueAttributesResult(
        messagesInFlightCount = messagesInFlightCount,
        messagesOnQueueCount = messagesOnQueueCount
      )
    )
    whenever(sqsClient.getQueueUrl("dlq")).thenReturn(someGetQueueUrlResultForDLQ())
    whenever(sqsClient.getQueueAttributes(someGetQueueAttributesRequestForDLQ())).thenReturn(
      someGetQueueAttributesResultForDLQ(messagesOnDLQCount)
    )
  }

  private fun mockEmptyQueues() {
    mockQueuesWith(0, 0, 0)
  }

  private fun someGetQueueUrlResult(): GetQueueUrlResult = GetQueueUrlResult().withQueueUrl("queueUrl")
  private fun someGetQueueUrlResultForDLQ(): GetQueueUrlResult = GetQueueUrlResult().withQueueUrl("dlqUrl")
  private fun someGetQueueAttributesResult(messagesOnQueueCount: Long, messagesInFlightCount: Long) =
    GetQueueAttributesResult().withAttributes(
      mapOf(
        "ApproximateNumberOfMessages" to "$messagesOnQueueCount",
        "ApproximateNumberOfMessagesNotVisible" to "$messagesInFlightCount",
        QueueAttributeName.RedrivePolicy.toString() to "any redrive policy"
      )
    )

  private fun someGetQueueAttributesResultForDLQ(messagesOnDLQCount: Long) = GetQueueAttributesResult().withAttributes(
    mapOf("ApproximateNumberOfMessages" to messagesOnDLQCount.toString())
  )

  private fun someGetQueueAttributesRequest() =
    GetQueueAttributesRequest("queueUrl").withAttributeNames(listOf(QueueAttributeName.All.toString()))

  private fun someGetQueueAttributesRequestForDLQ() =
    GetQueueAttributesRequest("dlqUrl").withAttributeNames(listOf(QueueAttributeName.All.toString()))
}
