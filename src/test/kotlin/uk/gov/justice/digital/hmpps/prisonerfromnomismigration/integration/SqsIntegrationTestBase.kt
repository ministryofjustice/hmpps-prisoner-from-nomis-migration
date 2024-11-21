package uk.gov.justice.digital.hmpps.prisonerfromnomismigration.integration

import com.microsoft.applicationinsights.TelemetryClient
import org.awaitility.Awaitility
import org.awaitility.kotlin.await
import org.awaitility.kotlin.matches
import org.awaitility.kotlin.untilAsserted
import org.awaitility.kotlin.untilCallTo
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.kotlin.any
import org.mockito.kotlin.eq
import org.mockito.kotlin.isNull
import org.mockito.kotlin.reset
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
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
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.alerts.AlertsDpsApiExtension
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.casenotes.CaseNotesApiExtension
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.contactperson.ContactPersonDpsApiExtension
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.courtsentencing.CourtSentencingDpsApiExtension
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.csip.CSIPDpsApiExtension
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.incidents.IncidentsApiExtension
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.integration.LocalStackContainer.setLocalStackProperties
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.prisonperson.PrisonPersonDpsApiExtension
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.prisonperson.PrisonPersonNomisSyncApiExtension
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.service.ACTIVITIES_QUEUE_ID
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.service.ALLOCATIONS_QUEUE_ID
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.service.APPOINTMENTS_QUEUE_ID
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.service.COURT_SENTENCING_QUEUE_ID
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.service.CSIP_QUEUE_ID
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.service.INCIDENTS_QUEUE_ID
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.service.LOCATIONS_QUEUE_ID
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.service.VISITS_QUEUE_ID
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.wiremock.ActivitiesApiExtension
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.wiremock.HmppsAuthApiExtension
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.wiremock.LocationsApiExtension
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.wiremock.MappingApiExtension
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.wiremock.NomisApiExtension
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.wiremock.SentencingApiExtension
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.wiremock.VisitsApiExtension
import uk.gov.justice.hmpps.sqs.HmppsQueue
import uk.gov.justice.hmpps.sqs.HmppsQueueService
import uk.gov.justice.hmpps.sqs.countAllMessagesOnQueue
import uk.gov.justice.hmpps.test.kotlin.auth.JwtAuthorisationHelper
import java.util.concurrent.TimeUnit

@ExtendWith(
  NomisApiExtension::class,
  HmppsAuthApiExtension::class,
  VisitsApiExtension::class,
  MappingApiExtension::class,
  SentencingApiExtension::class,
  ActivitiesApiExtension::class,
  IncidentsApiExtension::class,
  CSIPDpsApiExtension::class,
  LocationsApiExtension::class,
  AlertsDpsApiExtension::class,
  CaseNotesApiExtension::class,
  CourtSentencingDpsApiExtension::class,
  PrisonPersonDpsApiExtension::class,
  ContactPersonDpsApiExtension::class,
  PrisonPersonNomisSyncApiExtension::class,
)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class SqsIntegrationTestBase : TestBase() {
  @Autowired
  lateinit var webTestClient: WebTestClient

  @Autowired
  private lateinit var hmppsQueueService: HmppsQueueService

  internal val visitsMigrationQueue by lazy { hmppsQueueService.findByQueueId(VISITS_QUEUE_ID) as HmppsQueue }
  internal val courtSentencingMigrationQueue by lazy { hmppsQueueService.findByQueueId(COURT_SENTENCING_QUEUE_ID) as HmppsQueue }
  internal val appointmentsMigrationQueue by lazy { hmppsQueueService.findByQueueId(APPOINTMENTS_QUEUE_ID) as HmppsQueue }
  internal val activitiesMigrationQueue by lazy { hmppsQueueService.findByQueueId(ACTIVITIES_QUEUE_ID) as HmppsQueue }
  internal val allocationsMigrationQueue by lazy { hmppsQueueService.findByQueueId(ALLOCATIONS_QUEUE_ID) as HmppsQueue }
  internal val incidentsMigrationQueue by lazy { hmppsQueueService.findByQueueId(INCIDENTS_QUEUE_ID) as HmppsQueue }
  internal val csipMigrationQueue by lazy { hmppsQueueService.findByQueueId(CSIP_QUEUE_ID) as HmppsQueue }
  internal val locationsMigrationQueue by lazy { hmppsQueueService.findByQueueId(LOCATIONS_QUEUE_ID) as HmppsQueue }

  internal val awsSqsVisitsMigrationClient by lazy { visitsMigrationQueue.sqsClient }
  internal val awsSqsVisitsMigrationDlqClient by lazy { visitsMigrationQueue.sqsDlqClient }

  internal val awsSqsAppointmentsMigrationClient by lazy { appointmentsMigrationQueue.sqsClient }
  internal val awsSqsAppointmentsMigrationDlqClient by lazy { appointmentsMigrationQueue.sqsDlqClient }
  internal val awsSqsActivitiesMigrationClient by lazy { activitiesMigrationQueue.sqsClient }
  internal val awsSqsActivitiesMigrationDlqClient by lazy { activitiesMigrationQueue.sqsDlqClient }
  internal val awsSqsAllocationsMigrationClient by lazy { allocationsMigrationQueue.sqsClient }
  internal val awsSqsAllocationsMigrationDlqClient by lazy { allocationsMigrationQueue.sqsDlqClient }
  internal val awsSqsIncidentsMigrationDlqClient by lazy { incidentsMigrationQueue.sqsDlqClient }
  internal val awsSqsCSIPMigrationDlqClient by lazy { csipMigrationQueue.sqsDlqClient }
  internal val awsSqsLocationsMigrationDlqClient by lazy { locationsMigrationQueue.sqsDlqClient }
  internal val visitsMigrationQueueUrl by lazy { visitsMigrationQueue.queueUrl }
  internal val visitsMigrationDlqUrl by lazy { visitsMigrationQueue.dlqUrl }

  internal val appointmentsMigrationUrl by lazy { appointmentsMigrationQueue.queueUrl }
  internal val appointmentsMigrationDlqUrl by lazy { appointmentsMigrationQueue.dlqUrl }
  internal val activitiesMigrationUrl by lazy { activitiesMigrationQueue.queueUrl }
  internal val activitiesMigrationDlqUrl by lazy { activitiesMigrationQueue.dlqUrl }
  internal val allocationsMigrationUrl by lazy { allocationsMigrationQueue.queueUrl }
  internal val allocationsMigrationDlqUrl by lazy { allocationsMigrationQueue.dlqUrl }
  internal val incidentsMigrationDlqUrl by lazy { incidentsMigrationQueue.dlqUrl }
  internal val csipMigrationDlqUrl by lazy { csipMigrationQueue.dlqUrl }
  internal val locationsMigrationDlqUrl by lazy { locationsMigrationQueue.dlqUrl }

  internal val visitsOffenderEventsQueue by lazy { hmppsQueueService.findByQueueId("eventvisits") as HmppsQueue }
  internal val visitsQueueOffenderEventsUrl by lazy { visitsOffenderEventsQueue.queueUrl }
  internal val awsSqsVisitsOffenderEventsClient by lazy { visitsOffenderEventsQueue.sqsClient }
  internal val sentencingOffenderEventsQueue by lazy { hmppsQueueService.findByQueueId("eventsentencing") as HmppsQueue }
  internal val sentencingQueueOffenderEventsUrl by lazy { sentencingOffenderEventsQueue.queueUrl }
  internal val sentencingQueueOffenderEventsDlqUrl by lazy { sentencingOffenderEventsQueue.dlqUrl }
  internal val awsSqsSentencingOffenderEventsClient by lazy { sentencingOffenderEventsQueue.sqsClient }
  internal val awsSqsSentencingOffenderEventsDlqClient by lazy { sentencingOffenderEventsQueue.sqsDlqClient }

  internal val incidentsOffenderEventsQueue by lazy { hmppsQueueService.findByQueueId("eventincidents") as HmppsQueue }
  internal val incidentsQueueOffenderEventsUrl by lazy { incidentsOffenderEventsQueue.queueUrl }
  internal val incidentsQueueOffenderEventsDlqUrl by lazy { incidentsOffenderEventsQueue.dlqUrl as String }
  internal val awsSqsIncidentsOffenderEventsClient by lazy { incidentsOffenderEventsQueue.sqsClient }
  internal val awsSqsIncidentsOffenderEventDlqClient by lazy { incidentsOffenderEventsQueue.sqsDlqClient as SqsAsyncClient }

  internal val csipOffenderEventsQueue by lazy { hmppsQueueService.findByQueueId("eventcsip") as HmppsQueue }
  internal val csipQueueOffenderEventsUrl by lazy { csipOffenderEventsQueue.queueUrl }
  internal val csipQueueOffenderEventsDlqUrl by lazy { csipOffenderEventsQueue.dlqUrl as String }
  internal val awsSqsCSIPOffenderEventsClient by lazy { csipOffenderEventsQueue.sqsClient }
  internal val awsSqsCSIPOffenderEventDlqClient by lazy { csipOffenderEventsQueue.sqsDlqClient as SqsAsyncClient }

  internal val locationsOffenderEventsQueue by lazy { hmppsQueueService.findByQueueId("eventlocations") as HmppsQueue }
  internal val locationsQueueOffenderEventsUrl by lazy { locationsOffenderEventsQueue.queueUrl }
  internal val locationsQueueOffenderEventsDlqUrl by lazy { locationsOffenderEventsQueue.dlqUrl as String }
  internal val awsSqsLocationsOffenderEventsClient by lazy { locationsOffenderEventsQueue.sqsClient }
  internal val awsSqsLocationsOffenderEventDlqClient by lazy { locationsOffenderEventsQueue.sqsDlqClient as SqsAsyncClient }

  internal val alertsOffenderEventsQueue by lazy { hmppsQueueService.findByQueueId("eventalerts") as HmppsQueue }
  internal val alertsQueueOffenderEventsUrl by lazy { alertsOffenderEventsQueue.queueUrl }
  internal val alertsQueueOffenderEventsDlqUrl by lazy { alertsOffenderEventsQueue.dlqUrl as String }
  internal val awsSqsAlertOffenderEventsClient by lazy { alertsOffenderEventsQueue.sqsClient }
  internal val awsSqsAlertsOffenderEventDlqClient by lazy { alertsOffenderEventsQueue.sqsDlqClient as SqsAsyncClient }

  internal val caseNotesOffenderEventsQueue by lazy { hmppsQueueService.findByQueueId("eventcasenotes") as HmppsQueue }
  internal val caseNotesQueueOffenderEventsUrl by lazy { caseNotesOffenderEventsQueue.queueUrl }
  internal val caseNotesQueueOffenderEventsDlqUrl by lazy { caseNotesOffenderEventsQueue.dlqUrl as String }
  internal val awsSqsCaseNoteOffenderEventsClient by lazy { caseNotesOffenderEventsQueue.sqsClient }
  internal val awsSqsCaseNotesOffenderEventsDlqClient by lazy { caseNotesOffenderEventsQueue.sqsDlqClient as SqsAsyncClient }

  internal val courtSentencingOffenderEventsQueue by lazy { hmppsQueueService.findByQueueId("eventcourtsentencing") as HmppsQueue }
  internal val courtSentencingQueueOffenderEventsUrl by lazy { courtSentencingOffenderEventsQueue.queueUrl }
  internal val courtSentencingQueueOffenderEventsDlqUrl by lazy { courtSentencingOffenderEventsQueue.dlqUrl as String }
  internal val awsSqsCourtSentencingOffenderEventsClient by lazy { courtSentencingOffenderEventsQueue.sqsClient }
  internal val awsSqsCourtSentencingOffenderEventDlqClient by lazy { courtSentencingOffenderEventsQueue.sqsDlqClient as SqsAsyncClient }
  internal val courtSentencingMigrationUrl by lazy { courtSentencingMigrationQueue.queueUrl }
  internal val courtSentencingMigrationDlqUrl by lazy { courtSentencingMigrationQueue.dlqUrl as String }
  internal val awsSqsCourtSentencingMigrationClient by lazy { courtSentencingMigrationQueue.sqsClient }
  internal val awsSqsCourtSentencingMigrationDlqClient by lazy { courtSentencingMigrationQueue.sqsDlqClient }

  internal val prisonPersonOffenderEventsQueue by lazy { hmppsQueueService.findByQueueId("eventprisonperson") as HmppsQueue }
  internal val prisonPersonQueueOffenderEventsUrl by lazy { prisonPersonOffenderEventsQueue.queueUrl }
  internal val prisonPersonQueueOffenderEventsDlqUrl by lazy { prisonPersonOffenderEventsQueue.dlqUrl as String }
  internal val awsSqsPrisonPersonOffenderEventsClient by lazy { prisonPersonOffenderEventsQueue.sqsClient }
  internal val awsSqsPrisonPersonOffenderEventDlqClient by lazy { prisonPersonOffenderEventsQueue.sqsDlqClient as SqsAsyncClient }

  internal val caseNotesOffenderMigrationQueue by lazy { hmppsQueueService.findByQueueId("migrationcasenotes") as HmppsQueue }
  internal val caseNotesQueueOffenderMigrationUrl by lazy { caseNotesOffenderMigrationQueue.queueUrl }
  internal val caseNotesQueueOffenderMigrationDlqUrl by lazy { caseNotesOffenderMigrationQueue.dlqUrl as String }
  internal val awsSqsCaseNoteOffenderMigrationClient by lazy { caseNotesOffenderMigrationQueue.sqsClient }
  internal val awsSqsCaseNotesOffenderMigrationDlqClient by lazy { caseNotesOffenderMigrationQueue.sqsDlqClient as SqsAsyncClient }

  internal val prisonPersonMigrationQueue by lazy { hmppsQueueService.findByQueueId("migrationprisonperson") as HmppsQueue }
  internal val prisonPersonMigrationQueueUrl by lazy { prisonPersonMigrationQueue.queueUrl }
  internal val prisonPersonMigrationDlqUrl by lazy { prisonPersonMigrationQueue.dlqUrl as String }
  internal val prisonPersonMigrationQueueClient by lazy { prisonPersonMigrationQueue.sqsClient }
  internal val prisonPersonMigrationDlqClient by lazy { prisonPersonMigrationQueue.sqsDlqClient as SqsAsyncClient }

  internal val contactPersonOffenderEventsQueue by lazy { hmppsQueueService.findByQueueId("eventcontactperson") as HmppsQueue }
  internal val awsSqsContactPersonOffenderEventsClient by lazy { contactPersonOffenderEventsQueue.sqsClient }
  internal val contactPersonQueueOffenderEventsUrl by lazy { contactPersonOffenderEventsQueue.queueUrl }

  private val allQueues by lazy {
    listOf(
      incidentsOffenderEventsQueue,
      csipOffenderEventsQueue,
      locationsOffenderEventsQueue,
      sentencingOffenderEventsQueue,
      visitsOffenderEventsQueue,
      activitiesMigrationQueue,
      appointmentsMigrationQueue,
      incidentsMigrationQueue,
      csipMigrationQueue,
      locationsMigrationQueue,
      visitsMigrationQueue,
      alertsOffenderEventsQueue,
      caseNotesOffenderEventsQueue,
      courtSentencingOffenderEventsQueue,
      prisonPersonOffenderEventsQueue,
      prisonPersonMigrationQueue,
      contactPersonOffenderEventsQueue,
    )
  }

  @Autowired
  protected lateinit var jwtAuthHelper: JwtAuthorisationHelper

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
  ): (HttpHeaders) -> Unit = jwtAuthHelper.setAuthorisationHeader(username = user, roles = roles, scope = scopes)

  internal fun waitForAnyProcessingToComplete() {
    await untilAsserted { verify(telemetryClient).trackEvent(any(), any(), isNull()) }
  }

  internal fun waitForAnyProcessingToComplete(name: String, times: Int = 1) {
    await untilAsserted { verify(telemetryClient, times(times)).trackEvent(eq(name), any(), isNull()) }
  }

  internal fun waitForAnyProcessingToComplete(vararg names: String) {
    names.forEach {
      await untilAsserted { verify(telemetryClient, times(1)).trackEvent(eq(it), any(), isNull()) }
    }
  }

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
