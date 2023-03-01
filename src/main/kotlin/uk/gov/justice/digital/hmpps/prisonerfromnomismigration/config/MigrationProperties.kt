package uk.gov.justice.digital.hmpps.prisonerfromnomismigration.config

import kotlinx.coroutines.future.await
import kotlinx.coroutines.runBlocking
import org.springframework.boot.actuate.info.Info.Builder
import org.springframework.boot.actuate.info.InfoContributor
import software.amazon.awssdk.services.sqs.model.GetQueueAttributesRequest
import software.amazon.awssdk.services.sqs.model.QueueAttributeName
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.integration.history.MigrationMapping
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.service.SynchronisationType
import uk.gov.justice.hmpps.sqs.HmppsQueue
import uk.gov.justice.hmpps.sqs.HmppsQueueService

abstract class MigrationProperties(
  private val hmppsQueueService: HmppsQueueService,
  private val mappingService: MigrationMapping,
  private val migrationType: SynchronisationType
) : InfoContributor {

  internal val queue by lazy { hmppsQueueService.findByQueueId(migrationType.queueId) as HmppsQueue }

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

    val migrationProperties = mappingService.findLatestMigration()?.let {
      val details = mappingService.getMigrationDetails(it.migrationId)
      mapOf<String, Any?>(
        "id" to it.migrationId,
        "records migrated" to details.count,
        "started" to details.startedDateTime
      )
    } ?: mapOf()

    builder.withDetail(
      "last ${migrationType.name} migration",
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
