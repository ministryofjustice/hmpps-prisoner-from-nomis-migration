package uk.gov.justice.digital.hmpps.prisonerfromnomismigration.integration

import com.microsoft.applicationinsights.TelemetryClient
import org.awaitility.Awaitility
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
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.service.ACTIVITIES_QUEUE_ID
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.service.ADJUDICATIONS_QUEUE_ID
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.service.ALLOCATIONS_QUEUE_ID
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.service.APPOINTMENTS_QUEUE_ID
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.service.SENTENCING_ADJUSTMENTS_QUEUE_ID
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.service.VISITS_QUEUE_ID
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.wiremock.ActivitiesApiExtension
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.wiremock.AdjudicationsApiExtension
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.wiremock.HmppsAuthApiExtension
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.wiremock.MappingApiExtension
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.wiremock.NomisApiExtension
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.wiremock.NonAssociationsApiExtension
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.wiremock.SentencingApiExtension
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.wiremock.VisitsApiExtension
import uk.gov.justice.hmpps.sqs.HmppsQueue
import uk.gov.justice.hmpps.sqs.HmppsQueueService
import uk.gov.justice.hmpps.sqs.countAllMessagesOnQueue
import java.util.concurrent.TimeUnit

@ExtendWith(
  NomisApiExtension::class,
  HmppsAuthApiExtension::class,
  VisitsApiExtension::class,
  MappingApiExtension::class,
  SentencingApiExtension::class,
  AdjudicationsApiExtension::class,
  ActivitiesApiExtension::class,
  NonAssociationsApiExtension::class,
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
  internal val activitiesMigrationQueue by lazy { hmppsQueueService.findByQueueId(ACTIVITIES_QUEUE_ID) as HmppsQueue }
  internal val allocationsMigrationQueue by lazy { hmppsQueueService.findByQueueId(ALLOCATIONS_QUEUE_ID) as HmppsQueue }
  internal val adjudicationsMigrationQueue by lazy { hmppsQueueService.findByQueueId(ADJUDICATIONS_QUEUE_ID) as HmppsQueue }
  // internal val nonAssociationsMigrationQueue by lazy { hmppsQueueService.findByQueueId(NON_ASSOCIATIONS_QUEUE_ID) as HmppsQueue }

  internal val awsSqsVisitsMigrationClient by lazy { visitsMigrationQueue.sqsClient }
  internal val awsSqsVisitsMigrationDlqClient by lazy { visitsMigrationQueue.sqsDlqClient }
  internal val awsSqsSentencingMigrationClient by lazy { sentencingMigrationQueue.sqsClient }
  internal val awsSqsSentencingMigrationDlqClient by lazy { sentencingMigrationQueue.sqsDlqClient }

  // internal val awsSqsNonAssociationsMigrationClient by lazy { nonAssociationsMigrationQueue.sqsClient }
  // internal val awsSqsSNonAssociationsMigrationDlqClient by lazy { nonAssociationsMigrationQueue.sqsDlqClient }
  internal val awsSqsAppointmentsMigrationClient by lazy { appointmentsMigrationQueue.sqsClient }
  internal val awsSqsAppointmentsMigrationDlqClient by lazy { appointmentsMigrationQueue.sqsDlqClient }
  internal val awsSqsActivitiesMigrationClient by lazy { activitiesMigrationQueue.sqsClient }
  internal val awsSqsActivitiesMigrationDlqClient by lazy { activitiesMigrationQueue.sqsDlqClient }
  internal val awsSqsAllocationsMigrationClient by lazy { allocationsMigrationQueue.sqsClient }
  internal val awsSqsAllocationsMigrationDlqClient by lazy { allocationsMigrationQueue.sqsDlqClient }
  internal val awsSqsAdjudicationsMigrationDlqClient by lazy { adjudicationsMigrationQueue.sqsDlqClient }
  internal val visitsMigrationQueueUrl by lazy { visitsMigrationQueue.queueUrl }
  internal val visitsMigrationDlqUrl by lazy { visitsMigrationQueue.dlqUrl }
  internal val sentencingMigrationUrl by lazy { sentencingMigrationQueue.queueUrl }
  internal val sentencingMigrationDlqUrl by lazy { sentencingMigrationQueue.dlqUrl }

  // internal val nonAssociationsMigrationUrl by lazy { nonAssociationsMigrationQueue.queueUrl }
  // internal val nonAssociationsMigrationDlqUrl by lazy { nonAssociationsMigrationQueue.dlqUrl }
  internal val appointmentsMigrationUrl by lazy { appointmentsMigrationQueue.queueUrl }
  internal val appointmentsMigrationDlqUrl by lazy { appointmentsMigrationQueue.dlqUrl }
  internal val activitiesMigrationUrl by lazy { activitiesMigrationQueue.queueUrl }
  internal val activitiesMigrationDlqUrl by lazy { activitiesMigrationQueue.dlqUrl }
  internal val allocationsMigrationUrl by lazy { allocationsMigrationQueue.queueUrl }
  internal val allocationsMigrationDlqUrl by lazy { allocationsMigrationQueue.dlqUrl }
  internal val adjudicationsMigrationDlqUrl by lazy { adjudicationsMigrationQueue.dlqUrl }

  internal val visitsOffenderEventsQueue by lazy { hmppsQueueService.findByQueueId("eventvisits") as HmppsQueue }
  internal val visitsQueueOffenderEventsUrl by lazy { visitsOffenderEventsQueue.queueUrl }
  internal val awsSqsVisitsOffenderEventsClient by lazy { visitsOffenderEventsQueue.sqsClient }
  internal val sentencingOffenderEventsQueue by lazy { hmppsQueueService.findByQueueId("eventsentencing") as HmppsQueue }
  internal val sentencingQueueOffenderEventsUrl by lazy { sentencingOffenderEventsQueue.queueUrl }
  internal val sentencingQueueOffenderEventsDlqUrl by lazy { sentencingOffenderEventsQueue.dlqUrl }
  internal val awsSqsSentencingOffenderEventsClient by lazy { sentencingOffenderEventsQueue.sqsClient }
  internal val awsSqsSentencingOffenderEventsDlqClient by lazy { sentencingOffenderEventsQueue.sqsDlqClient }

  internal val nonAssociationsOffenderEventsClient by lazy { nonAssociationsOffenderEventsQueue.sqsClient }
  internal val nonAssociationsOffenderEventsQueue by lazy { hmppsQueueService.findByQueueId("eventnonassociations") as HmppsQueue }
  internal val nonAssociationsQueueOffenderEventsUrl by lazy { nonAssociationsOffenderEventsQueue.queueUrl }

  private val allQueues by lazy {
    listOf(
      visitsMigrationQueue,
      sentencingMigrationQueue,
      visitsOffenderEventsQueue,
      nonAssociationsOffenderEventsQueue,
      sentencingOffenderEventsQueue,
      adjudicationsMigrationQueue,
      activitiesMigrationQueue,
      appointmentsMigrationQueue,
      sentencingMigrationQueue,
    )
  }

  @Autowired
  protected lateinit var jwtAuthHelper: JwtAuthHelper

  @SpyBean
  protected lateinit var telemetryClient: TelemetryClient

  @BeforeEach
  fun setUp() {
    Awaitility.setDefaultPollDelay(1, TimeUnit.MILLISECONDS)
    Awaitility.setDefaultPollInterval(10, TimeUnit.MILLISECONDS)
    reset(telemetryClient)
    allQueues.forEach { it.purgeAndWait() }
    Awaitility.setDefaultPollInterval(50, TimeUnit.MILLISECONDS)
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
