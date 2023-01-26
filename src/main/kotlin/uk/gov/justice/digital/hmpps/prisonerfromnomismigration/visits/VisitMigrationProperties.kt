package uk.gov.justice.digital.hmpps.prisonerfromnomismigration.visits

import kotlinx.coroutines.future.await
import kotlinx.coroutines.runBlocking
import org.springframework.boot.actuate.info.Info.Builder
import org.springframework.boot.actuate.info.InfoContributor
import org.springframework.stereotype.Component
import software.amazon.awssdk.services.sqs.model.GetQueueAttributesRequest
import software.amazon.awssdk.services.sqs.model.QueueAttributeName
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.service.VISITS_QUEUE_ID
import uk.gov.justice.hmpps.sqs.HmppsQueue
import uk.gov.justice.hmpps.sqs.HmppsQueueService

@Component
class VisitMigrationProperties(
  private val hmppsQueueService: HmppsQueueService,
  private val visitMappingService: VisitMappingService
) : InfoContributor {

  internal val queue by lazy { hmppsQueueService.findByQueueId(VISITS_QUEUE_ID) as HmppsQueue }

  override fun contribute(builder: Builder): Unit = runBlocking {
    val queueProperties = queue.getQueueAttributes().map {
      mapOf<String, Any?>(
        "records waiting processing" to it.attributes()[QueueAttributeName.APPROXIMATE_NUMBER_OF_MESSAGES],
        "records currently being processed" to it.attributes()[QueueAttributeName.APPROXIMATE_NUMBER_OF_MESSAGES_NOT_VISIBLE],
      )
    }.getOrElse { mapOf() }

    val failureQueueProperties = queue.getDlqAttributes().map {
      mapOf<String, Any?>(
        "records that have failed" to it.attributes()[QueueAttributeName.APPROXIMATE_NUMBER_OF_MESSAGES],
      )
    }.getOrElse { mapOf() }

    val migrationProperties = visitMappingService.findLatestMigration()?.let {
      val details = visitMappingService.getMigrationDetails(it.migrationId)
      mapOf<String, Any?>(
        "id" to it.migrationId,
        "records migrated" to details.count,
        "started" to details.startedDateTime
      )
    } ?: mapOf()

    builder.withDetail(
      "last visits migration",
      queueProperties + failureQueueProperties + migrationProperties
    )
  }

  private suspend fun HmppsQueue.getQueueAttributes() = runCatching {
    this.sqsClient.getQueueAttributes(
      GetQueueAttributesRequest.builder().queueUrl(this.queueUrl).attributeNames(QueueAttributeName.ALL).build()
    ).await()
  }

  private suspend fun HmppsQueue.getDlqAttributes() = runCatching {
    this.sqsDlqClient!!.getQueueAttributes(
      GetQueueAttributesRequest.builder().queueUrl(this.dlqUrl).attributeNames(QueueAttributeName.ALL).build()
    ).await()
  }
}
