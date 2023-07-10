package uk.gov.justice.digital.hmpps.prisonerfromnomismigration.integration

import com.microsoft.applicationinsights.TelemetryClient
import org.awaitility.kotlin.await
import org.awaitility.kotlin.matches
import org.awaitility.kotlin.untilCallTo
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.kotlin.reset
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.mock.mockito.SpyBean
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
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.service.APPOINTMENTS_QUEUE_ID
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.service.SENTENCING_ADJUSTMENTS_QUEUE_ID
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.service.VISITS_QUEUE_ID
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.wiremock.ActivitiesApiExtension
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.wiremock.AdjudicationsApiExtension
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.wiremock.HmppsAuthApiExtension
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.wiremock.MappingApiExtension
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.wiremock.NomisApiExtension
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.wiremock.SentencingApiExtension
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.wiremock.VisitsApiExtension
import uk.gov.justice.hmpps.sqs.HmppsQueue
import uk.gov.justice.hmpps.sqs.HmppsQueueService
import uk.gov.justice.hmpps.sqs.countAllMessagesOnQueue

@ExtendWith(
  NomisApiExtension::class,
  HmppsAuthApiExtension::class,
  VisitsApiExtension::class,
  MappingApiExtension::class,
  SentencingApiExtension::class,
  AdjudicationsApiExtension::class,
  ActivitiesApiExtension::class,
)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class SqsIntegrationTestBase : TestBase() {
  @Autowired
  lateinit var webTestClient: WebTestClient

  @Autowired
  private lateinit var hmppsQueueService: HmppsQueueService

  internal val visitsMigrationQueue by lazy { hmppsQueueService.findByQueueId(VISITS_QUEUE_ID) as HmppsQueue }
  internal val sentencingMigrationQueue by lazy { hmppsQueueService.findByQueueId(SENTENCING_ADJUSTMENTS_QUEUE_ID) as HmppsQueue }
  internal val appointmentsMigrationQueue by lazy { hmppsQueueService.findByQueueId(APPOINTMENTS_QUEUE_ID) as HmppsQueue }

  internal val awsSqsVisitsMigrationClient by lazy { visitsMigrationQueue.sqsClient }
  internal val awsSqsVisitsMigrationDlqClient by lazy { visitsMigrationQueue.sqsDlqClient }
  internal val awsSqsSentencingMigrationClient by lazy { sentencingMigrationQueue.sqsClient }
  internal val awsSqsSentencingMigrationDlqClient by lazy { sentencingMigrationQueue.sqsDlqClient }
  internal val awsSqsAppointmentsMigrationClient by lazy { appointmentsMigrationQueue.sqsClient }
  internal val awsSqsAppointmentsMigrationDlqClient by lazy { appointmentsMigrationQueue.sqsDlqClient }
  internal val visitsMigrationQueueUrl by lazy { visitsMigrationQueue.queueUrl }
  internal val visitsMigrationDlqUrl by lazy { visitsMigrationQueue.dlqUrl }
  internal val sentencingMigrationUrl by lazy { sentencingMigrationQueue.queueUrl }
  internal val sentencingMigrationDlqUrl by lazy { sentencingMigrationQueue.dlqUrl }
  internal val appointmentsMigrationUrl by lazy { appointmentsMigrationQueue.queueUrl }
  internal val appointmentsMigrationDlqUrl by lazy { appointmentsMigrationQueue.dlqUrl }

  internal val visitsOffenderEventsQueue by lazy { hmppsQueueService.findByQueueId("eventvisits") as HmppsQueue }
  internal val visitsQueueOffenderEventsUrl by lazy { visitsOffenderEventsQueue.queueUrl }
  internal val awsSqsVisitsOffenderEventsClient by lazy { visitsOffenderEventsQueue.sqsClient }
  internal val sentencingOffenderEventsQueue by lazy { hmppsQueueService.findByQueueId("eventsentencing") as HmppsQueue }
  internal val sentencingQueueOffenderEventsUrl by lazy { sentencingOffenderEventsQueue.queueUrl }
  internal val sentencingQueueOffenderEventsDlqUrl by lazy { sentencingOffenderEventsQueue.dlqUrl }
  internal val awsSqsSentencingOffenderEventsClient by lazy { sentencingOffenderEventsQueue.sqsClient }
  internal val awsSqsSentencingOffenderEventsDlqClient by lazy { sentencingOffenderEventsQueue.sqsDlqClient }

  private val allQueues by lazy {
    listOf(
      visitsMigrationQueue,
      sentencingMigrationQueue,
      visitsOffenderEventsQueue,
      sentencingOffenderEventsQueue,
    )
  }

  @Autowired
  protected lateinit var jwtAuthHelper: JwtAuthHelper

  @SpyBean
  protected lateinit var telemetryClient: TelemetryClient

  @BeforeEach
  fun setUp() {
    reset(telemetryClient)
    allQueues.forEach { it.purgeAndWait() }
  }

  internal fun setAuthorisation(
    user: String = "ADMIN",
    roles: List<String> = listOf(),
    scopes: List<String> = listOf(),
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
private fun SqsAsyncClient.purgeQueue(queueUrl: String?) = purgeQueue(queueUrl?.purgeQueueRequest())

private fun HmppsQueue.purgeAndWait() {
  sqsClient.purgeQueue(queueUrl).get().also {
    await untilCallTo { sqsClient.countAllMessagesOnQueue(queueUrl).get() } matches { it == 0 }
  }
  sqsDlqClient?.run {
    purgeQueue(dlqUrl).get().also {
      await untilCallTo { this.countAllMessagesOnQueue(dlqUrl!!).get() } matches { it == 0 }
    }
  }
}
