package uk.gov.justice.digital.hmpps.prisonerfromnomismigration.visits

import com.amazonaws.services.sqs.model.GetQueueAttributesRequest
import com.amazonaws.services.sqs.model.GetQueueAttributesResult
import com.amazonaws.services.sqs.model.QueueAttributeName.All
import com.amazonaws.services.sqs.model.QueueAttributeName.ApproximateNumberOfMessages
import com.amazonaws.services.sqs.model.QueueAttributeName.ApproximateNumberOfMessagesNotVisible
import org.springframework.boot.actuate.info.Info.Builder
import org.springframework.boot.actuate.info.InfoContributor
import org.springframework.stereotype.Component
import uk.gov.justice.hmpps.sqs.HmppsQueue
import uk.gov.justice.hmpps.sqs.HmppsQueueService

@Component
class VisitMigrationProperties(
  private var hmppsQueueService: HmppsQueueService,
  private var visitMappingService: VisitMappingService
) : InfoContributor {

  internal val registersQueue by lazy { hmppsQueueService.findByQueueId("migration") as HmppsQueue }

  override fun contribute(builder: Builder) {
    val queueProperties = registersQueue.getQueueAttributes().map {
      mapOf<String, Any?>(
        "records waiting processing" to it.attributes[ApproximateNumberOfMessages.toString()],
        "records currently being processed" to it.attributes[ApproximateNumberOfMessagesNotVisible.toString()]
      )
    }.getOrElse { mapOf() }

    val failureQueueProperties = registersQueue.getDlqAttributes().map {
      mapOf<String, Any?>(
        "records that have failed" to it.attributes[ApproximateNumberOfMessages.toString()],
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

  private fun HmppsQueue.getQueueAttributes(): Result<GetQueueAttributesResult> {
    return runCatching {
      this.sqsClient.getQueueAttributes(
        GetQueueAttributesRequest(this.queueUrl).withAttributeNames(
          All
        )
      )
    }
  }

  private fun HmppsQueue.getDlqAttributes(): Result<GetQueueAttributesResult> =
    runCatching {
      this.sqsDlqClient!!.getQueueAttributes(GetQueueAttributesRequest(this.dlqUrl).withAttributeNames(All))
    }
}
