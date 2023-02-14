package uk.gov.justice.digital.hmpps.prisonerfromnomismigration.integration

import org.junit.jupiter.api.extension.ExtendWith
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.http.HttpHeaders
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import org.springframework.test.web.reactive.server.WebTestClient
import software.amazon.awssdk.services.sqs.SqsAsyncClient
import software.amazon.awssdk.services.sqs.model.PurgeQueueRequest
import software.amazon.awssdk.services.sqs.model.SendMessageRequest
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.helper.JwtAuthHelper
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.integration.LocalStackContainer.setLocalStackProperties
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.service.INCENTIVES_QUEUE_ID
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.service.SENTENCING_QUEUE_ID
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.service.VISITS_QUEUE_ID
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.wiremock.HmppsAuthApiExtension
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.wiremock.IncentivesApiExtension
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.wiremock.MappingApiExtension
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.wiremock.NomisApiExtension
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.wiremock.SentencingApiExtension
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.wiremock.VisitsApiExtension
import uk.gov.justice.hmpps.sqs.HmppsQueue
import uk.gov.justice.hmpps.sqs.HmppsQueueService

@ExtendWith(
  NomisApiExtension::class,
  HmppsAuthApiExtension::class,
  VisitsApiExtension::class,
  MappingApiExtension::class,
  IncentivesApiExtension::class,
  SentencingApiExtension::class,
)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class SqsIntegrationTestBase : TestBase() {
  @Autowired
  lateinit var webTestClient: WebTestClient

  @Autowired
  private lateinit var hmppsQueueService: HmppsQueueService

  internal val visitsMigrationQueue by lazy { hmppsQueueService.findByQueueId(VISITS_QUEUE_ID) as HmppsQueue }
  internal val incentivesMigrationQueue by lazy { hmppsQueueService.findByQueueId(INCENTIVES_QUEUE_ID) as HmppsQueue }
  internal val sentencingMigrationQueue by lazy { hmppsQueueService.findByQueueId(SENTENCING_QUEUE_ID) as HmppsQueue }
  internal val offenderEventsQueue by lazy { hmppsQueueService.findByQueueId("event") as HmppsQueue }

  internal val awsSqsVisitsMigrationClient by lazy { visitsMigrationQueue.sqsClient }
  internal val awsSqsVisitsMigrationDlqClient by lazy { visitsMigrationQueue.sqsDlqClient }
  internal val awsSqsIncentivesMigrationClient by lazy { incentivesMigrationQueue.sqsClient }
  internal val awsSqsIncentivesMigrationDlqClient by lazy { incentivesMigrationQueue.sqsDlqClient }
  internal val awsSqsSentencingMigrationClient by lazy { sentencingMigrationQueue.sqsClient }
  internal val awsSqsSentencingMigrationDlqClient by lazy { sentencingMigrationQueue.sqsDlqClient }
  internal val visitsMigrationQueueUrl by lazy { visitsMigrationQueue.queueUrl }
  internal val visitsMigrationDlqUrl by lazy { visitsMigrationQueue.dlqUrl }
  internal val incentivesMigrationUrl by lazy { incentivesMigrationQueue.queueUrl }
  internal val incentivesMigrationDlqUrl by lazy { incentivesMigrationQueue.dlqUrl }
  internal val sentencingMigrationUrl by lazy { sentencingMigrationQueue.queueUrl }
  internal val sentencingMigrationDlqUrl by lazy { sentencingMigrationQueue.dlqUrl }

  internal val awsSqsOffenderEventsClient by lazy { offenderEventsQueue.sqsClient }
  internal val queueOffenderEventsUrl by lazy { offenderEventsQueue.queueUrl }

  internal val sentencingOffenderEventsQueue by lazy { hmppsQueueService.findByQueueId("eventsentencing") as HmppsQueue }
  internal val sentencingQueueOffenderEventsUrl by lazy { sentencingOffenderEventsQueue.queueUrl }
  internal val awsSqsSentencingOffenderEventsClient by lazy { sentencingOffenderEventsQueue.sqsClient }

  @Autowired
  protected lateinit var jwtAuthHelper: JwtAuthHelper

  internal fun setAuthorisation(
    user: String = "ADMIN",
    roles: List<String> = listOf(),
    scopes: List<String> = listOf()
  ): (HttpHeaders) -> Unit = jwtAuthHelper.setAuthorisation(user, roles, scopes)

  companion object {
    private val localStackContainer = LocalStackContainer.instance

    @JvmStatic
    @DynamicPropertySource
    fun testcontainers(registry: DynamicPropertyRegistry) {
      localStackContainer?.also { setLocalStackProperties(it, registry) }
    }
  }
}

internal fun SqsAsyncClient.sendMessage(queueOffenderEventsUrl: String, message: String) =
  sendMessage(SendMessageRequest.builder().queueUrl(queueOffenderEventsUrl).messageBody(message).build()).get()

internal fun String.purgeQueueRequest() = PurgeQueueRequest.builder().queueUrl(this).build()
