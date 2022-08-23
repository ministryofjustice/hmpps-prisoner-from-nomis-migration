package uk.gov.justice.digital.hmpps.prisonerfromnomismigration.incentives

import com.amazonaws.services.sqs.model.GetQueueAttributesRequest
import com.amazonaws.services.sqs.model.GetQueueAttributesResult
import com.amazonaws.services.sqs.model.QueueAttributeName.All
import com.amazonaws.services.sqs.model.QueueAttributeName.ApproximateNumberOfMessages
import com.amazonaws.services.sqs.model.QueueAttributeName.ApproximateNumberOfMessagesNotVisible
import org.springframework.boot.actuate.info.Info.Builder
import org.springframework.boot.actuate.info.InfoContributor
import org.springframework.stereotype.Component
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.service.INCENTIVES_QUEUE_ID
import uk.gov.justice.hmpps.sqs.HmppsQueue
import uk.gov.justice.hmpps.sqs.HmppsQueueService

@Component
class IncentiveMigrationProperties(
  private var hmppsQueueService: HmppsQueueService,
) : InfoContributor {

  internal val queue by lazy { hmppsQueueService.findByQueueId(INCENTIVES_QUEUE_ID) as HmppsQueue }

  override fun contribute(builder: Builder) {
    val queueProperties = queue.getQueueAttributes().map {
      mapOf<String, Any?>(
        "records waiting processing" to it.attributes[ApproximateNumberOfMessages.toString()],
        "records currently being processed" to it.attributes[ApproximateNumberOfMessagesNotVisible.toString()]
      )
    }.getOrElse { mapOf() }

    val failureQueueProperties = queue.getDlqAttributes().map {
      mapOf<String, Any?>(
        "records that have failed" to it.attributes[ApproximateNumberOfMessages.toString()],
      )
    }.getOrElse { mapOf() }

    // TODO: we need the migrationId, current count and start time
    // we can get 2 of these from history service, but current count still needs
    // to be thought out
    val migrationProperties = mapOf<String, Any>()

    builder.withDetail(
      "last incentives migration",
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
