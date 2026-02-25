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
import org.springframework.boot.test.autoconfigure.json.AutoConfigureJson
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.webtestclient.autoconfigure.AutoConfigureWebTestClient
import org.springframework.http.HttpHeaders
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean
import org.springframework.test.web.reactive.server.WebTestClient
import software.amazon.awssdk.services.sqs.SqsAsyncClient
import software.amazon.awssdk.services.sqs.model.PurgeQueueRequest
import software.amazon.awssdk.services.sqs.model.SendMessageRequest
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.alerts.AlertsDpsApiExtension
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.casenotes.CaseNotesApiExtension
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.coreperson.CorePersonCprApiExtension
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.courtsentencing.CourtSentencingDpsApiExtension
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.csra.CsraApiExtension
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.finance.FinanceApiExtension
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.incidents.IncidentsApiExtension
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.integration.LocalStackContainer.setLocalStackProperties
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.movements.ExternalMovementsDpsApiExtension
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.movements.ExternalMovementsMigrationService
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.officialvisits.OfficialVisitsDpsApiExtension
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.organisations.OrganisationsDpsApiExtension
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.personalrelationships.ContactPersonDpsApiExtension
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.service.ACTIVITIES_QUEUE_ID
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.service.ALERTS_SYNC_QUEUE_ID
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.service.ALLOCATIONS_QUEUE_ID
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.service.APPOINTMENTS_QUEUE_ID
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.service.CASENOTES_SYNC_QUEUE_ID
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.service.CORE_PERSON_SYNC_QUEUE_ID
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.service.COURT_SENTENCING_QUEUE_ID
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.service.COURT_SENTENCING_SYNC_QUEUE_ID
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.service.CSRA_QUEUE_ID
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.service.CSRA_SYNC_QUEUE_ID
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.service.EXTERNALMOVEMENTS_SYNC_QUEUE_ID
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.service.FINANCE_SYNC_QUEUE_ID
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.service.INCIDENTS_QUEUE_ID
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.service.INCIDENTS_SYNC_QUEUE_ID
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.service.LOCATIONS_SYNC_QUEUE_ID
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.service.OFFICIAL_VISITS_SYNC_QUEUE_ID
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.service.ORGANISATIONS_SYNC_QUEUE_ID
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.service.PERSONALRELATIONSHIPS_DOMAIN_SYNC_QUEUE_ID
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.service.PERSONALRELATIONSHIPS_SYNC_QUEUE_ID
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.service.PERSONCONTACTS_DOMAIN_SYNC_QUEUE_ID
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.service.PRISONERRESTRICTIONS_DOMAIN_SYNC_QUEUE_ID
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.service.PRISONER_BALANCE_QUEUE_ID
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.service.SENTENCING_ADJUSTMENTS_SYNC_QUEUE_ID
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.service.VISITS_QUEUE_ID
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.service.VISITS_SYNC_QUEUE_ID
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.service.VISIT_BALANCE_SYNC_QUEUE_ID
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.visitbalances.VisitBalanceDpsApiExtension
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.wiremock.ActivitiesApiExtension
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.wiremock.HmppsAuthApiExtension
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.wiremock.LocationsApiExtension
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.wiremock.MappingApiExtension
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.wiremock.NomisApiExtension
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.wiremock.NomisSyncApiExtension
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
  CorePersonCprApiExtension::class,
  CsraApiExtension::class,
  IncidentsApiExtension::class,
  LocationsApiExtension::class,
  AlertsDpsApiExtension::class,
  CaseNotesApiExtension::class,
  FinanceApiExtension::class,
  CourtSentencingDpsApiExtension::class,
  ContactPersonDpsApiExtension::class,
  OrganisationsDpsApiExtension::class,
  VisitBalanceDpsApiExtension::class,
  NomisSyncApiExtension::class,
  ExternalMovementsDpsApiExtension::class,
  OfficialVisitsDpsApiExtension::class,
)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
@AutoConfigureWebTestClient
@AutoConfigureJson
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
  internal val prisonerBalanceMigrationQueue by lazy { hmppsQueueService.findByQueueId(PRISONER_BALANCE_QUEUE_ID) as HmppsQueue }
  internal val csraMigrationQueue by lazy { hmppsQueueService.findByQueueId(CSRA_QUEUE_ID) as HmppsQueue }

  internal val awsSqsVisitsMigrationDlqClient by lazy { visitsMigrationQueue.sqsDlqClient }

  internal val awsSqsAppointmentsMigrationDlqClient by lazy { appointmentsMigrationQueue.sqsDlqClient }
  internal val awsSqsActivitiesMigrationDlqClient by lazy { activitiesMigrationQueue.sqsDlqClient }
  internal val awsSqsAllocationsMigrationDlqClient by lazy { allocationsMigrationQueue.sqsDlqClient }
  internal val awsSqsIncidentsMigrationDlqClient by lazy { incidentsMigrationQueue.sqsDlqClient }
  internal val visitsMigrationDlqUrl by lazy { visitsMigrationQueue.dlqUrl }

  internal val appointmentsMigrationDlqUrl by lazy { appointmentsMigrationQueue.dlqUrl }
  internal val activitiesMigrationDlqUrl by lazy { activitiesMigrationQueue.dlqUrl }
  internal val allocationsMigrationDlqUrl by lazy { allocationsMigrationQueue.dlqUrl }
  internal val incidentsMigrationDlqUrl by lazy { incidentsMigrationQueue.dlqUrl }

  internal val visitsOffenderEventsQueue by lazy { hmppsQueueService.findByQueueId(VISITS_SYNC_QUEUE_ID) as HmppsQueue }
  internal val visitsQueueOffenderEventsUrl by lazy { visitsOffenderEventsQueue.queueUrl }
  internal val awsSqsVisitsOffenderEventsClient by lazy { visitsOffenderEventsQueue.sqsClient }
  internal val sentencingOffenderEventsQueue by lazy { hmppsQueueService.findByQueueId(SENTENCING_ADJUSTMENTS_SYNC_QUEUE_ID) as HmppsQueue }
  internal val sentencingQueueOffenderEventsUrl by lazy { sentencingOffenderEventsQueue.queueUrl }
  internal val sentencingQueueOffenderEventsDlqUrl by lazy { sentencingOffenderEventsQueue.dlqUrl }
  internal val awsSqsSentencingOffenderEventsClient by lazy { sentencingOffenderEventsQueue.sqsClient }
  internal val awsSqsSentencingOffenderEventsDlqClient by lazy { sentencingOffenderEventsQueue.sqsDlqClient }

  internal val incidentsOffenderEventsQueue by lazy { hmppsQueueService.findByQueueId(INCIDENTS_SYNC_QUEUE_ID) as HmppsQueue }
  internal val incidentsQueueOffenderEventsUrl by lazy { incidentsOffenderEventsQueue.queueUrl }
  internal val incidentsQueueOffenderEventsDlqUrl by lazy { incidentsOffenderEventsQueue.dlqUrl as String }
  internal val awsSqsIncidentsOffenderEventsClient by lazy { incidentsOffenderEventsQueue.sqsClient }
  internal val awsSqsIncidentsOffenderEventDlqClient by lazy { incidentsOffenderEventsQueue.sqsDlqClient as SqsAsyncClient }

  internal val locationsOffenderEventsQueue by lazy { hmppsQueueService.findByQueueId(LOCATIONS_SYNC_QUEUE_ID) as HmppsQueue }
  internal val locationsQueueOffenderEventsUrl by lazy { locationsOffenderEventsQueue.queueUrl }
  internal val locationsQueueOffenderEventsDlqUrl by lazy { locationsOffenderEventsQueue.dlqUrl as String }
  internal val awsSqsLocationsOffenderEventsClient by lazy { locationsOffenderEventsQueue.sqsClient }
  internal val awsSqsLocationsOffenderEventDlqClient by lazy { locationsOffenderEventsQueue.sqsDlqClient as SqsAsyncClient }

  internal val alertsOffenderEventsQueue by lazy { hmppsQueueService.findByQueueId(ALERTS_SYNC_QUEUE_ID) as HmppsQueue }
  internal val alertsQueueOffenderEventsUrl by lazy { alertsOffenderEventsQueue.queueUrl }
  internal val alertsQueueOffenderEventsDlqUrl by lazy { alertsOffenderEventsQueue.dlqUrl as String }
  internal val awsSqsAlertOffenderEventsClient by lazy { alertsOffenderEventsQueue.sqsClient }
  internal val awsSqsAlertsOffenderEventDlqClient by lazy { alertsOffenderEventsQueue.sqsDlqClient as SqsAsyncClient }

  internal val caseNotesOffenderEventsQueue by lazy { hmppsQueueService.findByQueueId(CASENOTES_SYNC_QUEUE_ID) as HmppsQueue }
  internal val caseNotesQueueOffenderEventsUrl by lazy { caseNotesOffenderEventsQueue.queueUrl }
  internal val caseNotesQueueOffenderEventsDlqUrl by lazy { caseNotesOffenderEventsQueue.dlqUrl as String }
  internal val awsSqsCaseNoteOffenderEventsClient by lazy { caseNotesOffenderEventsQueue.sqsClient }
  internal val awsSqsCaseNotesOffenderEventsDlqClient by lazy { caseNotesOffenderEventsQueue.sqsDlqClient as SqsAsyncClient }

  internal val financeOffenderEventsQueue by lazy { hmppsQueueService.findByQueueId(FINANCE_SYNC_QUEUE_ID) as HmppsQueue }
  internal val financeQueueOffenderEventsUrl by lazy { financeOffenderEventsQueue.queueUrl }
  internal val financeQueueOffenderEventsDlqUrl by lazy { financeOffenderEventsQueue.dlqUrl as String }
  internal val awsSqsFinanceOffenderEventsClient by lazy { financeOffenderEventsQueue.sqsClient }
  internal val awsSqsFinanceOffenderEventsDlqClient by lazy { financeOffenderEventsQueue.sqsDlqClient as SqsAsyncClient }

  internal val courtSentencingOffenderEventsQueue by lazy { hmppsQueueService.findByQueueId(COURT_SENTENCING_SYNC_QUEUE_ID) as HmppsQueue }
  internal val courtSentencingQueueOffenderEventsUrl by lazy { courtSentencingOffenderEventsQueue.queueUrl }
  internal val courtSentencingQueueOffenderEventsDlqUrl by lazy { courtSentencingOffenderEventsQueue.dlqUrl as String }
  internal val awsSqsCourtSentencingOffenderEventsClient by lazy { courtSentencingOffenderEventsQueue.sqsClient }
  internal val awsSqsCourtSentencingOffenderEventDlqClient by lazy { courtSentencingOffenderEventsQueue.sqsDlqClient as SqsAsyncClient }
  internal val courtSentencingMigrationDlqUrl by lazy { courtSentencingMigrationQueue.dlqUrl as String }
  internal val awsSqsCourtSentencingMigrationDlqClient by lazy { courtSentencingMigrationQueue.sqsDlqClient }

  internal val personalRelationshipsOffenderEventsQueue by lazy { hmppsQueueService.findByQueueId(PERSONALRELATIONSHIPS_SYNC_QUEUE_ID) as HmppsQueue }
  internal val awsSqsPersonalRelationshipsOffenderEventsClient by lazy { personalRelationshipsOffenderEventsQueue.sqsClient }
  internal val awsSqsPersonalRelationshipsOffenderEventsDlqClient by lazy { personalRelationshipsOffenderEventsQueue.sqsDlqClient as SqsAsyncClient }
  internal val personalRelationshipsQueueOffenderEventsUrl by lazy { personalRelationshipsOffenderEventsQueue.queueUrl }
  internal val personalRelationshipsQueueOffenderEventsDlqUrl by lazy { personalRelationshipsOffenderEventsQueue.dlqUrl as String }

  internal val organisationsOffenderEventsQueue by lazy { hmppsQueueService.findByQueueId(ORGANISATIONS_SYNC_QUEUE_ID) as HmppsQueue }
  internal val personalRelationshipsDomainEventsQueue by lazy { hmppsQueueService.findByQueueId(PERSONALRELATIONSHIPS_DOMAIN_SYNC_QUEUE_ID) as HmppsQueue }
  internal val personContactsDomainEventsQueue by lazy { hmppsQueueService.findByQueueId(PERSONCONTACTS_DOMAIN_SYNC_QUEUE_ID) as HmppsQueue }
  internal val prisonerRestrictionsDomainEventsQueue by lazy { hmppsQueueService.findByQueueId(PRISONERRESTRICTIONS_DOMAIN_SYNC_QUEUE_ID) as HmppsQueue }

  internal val visitBalanceOffenderEventsQueue by lazy { hmppsQueueService.findByQueueId(VISIT_BALANCE_SYNC_QUEUE_ID) as HmppsQueue }
  internal val awsSqsVisitBalanceOffenderEventDlqClient by lazy { visitBalanceOffenderEventsQueue.sqsDlqClient as SqsAsyncClient }
  internal val visitBalanceOffenderEventsDlqUrl by lazy { visitBalanceOffenderEventsQueue.dlqUrl as String }

  internal val externalMovementsOffenderEventsQueue by lazy { hmppsQueueService.findByQueueId(EXTERNALMOVEMENTS_SYNC_QUEUE_ID) as HmppsQueue }
  internal val awsSqsExternalMovementsOffenderEventsClient by lazy { externalMovementsOffenderEventsQueue.sqsClient }
  internal val awsSqsExternalMovementsOffenderEventsDlqClient by lazy { externalMovementsOffenderEventsQueue.sqsDlqClient as SqsAsyncClient }
  internal val externalMovementsQueueOffenderEventsUrl by lazy { externalMovementsOffenderEventsQueue.queueUrl }
  internal val externalMovementsQueueOffenderEventsDlqUrl by lazy { externalMovementsOffenderEventsQueue.dlqUrl as String }

  internal val officialVisitsOffenderEventsQueue by lazy { hmppsQueueService.findByQueueId(OFFICIAL_VISITS_SYNC_QUEUE_ID) as HmppsQueue }
  internal val csraOffenderEventsQueue by lazy { hmppsQueueService.findByQueueId(CSRA_SYNC_QUEUE_ID) as HmppsQueue }

  internal val corePersonOffenderEventsQueue by lazy { hmppsQueueService.findByQueueId(CORE_PERSON_SYNC_QUEUE_ID) as HmppsQueue }
  internal val awsSqsCorePersonOffenderEventsClient by lazy { corePersonOffenderEventsQueue.sqsClient }
  internal val awsSqsCorePersonOffenderEventsDlqClient by lazy { corePersonOffenderEventsQueue.sqsDlqClient as SqsAsyncClient }
  internal val corePersonQueueOffenderEventsUrl by lazy { corePersonOffenderEventsQueue.queueUrl }
  internal val corePersonQueueOffenderEventsDlqUrl by lazy { corePersonOffenderEventsQueue.dlqUrl as String }

  private val allQueues by lazy {
    listOf(
      incidentsOffenderEventsQueue,
      locationsOffenderEventsQueue,
      sentencingOffenderEventsQueue,
      visitsOffenderEventsQueue,
      activitiesMigrationQueue,
      appointmentsMigrationQueue,
      incidentsMigrationQueue,
      prisonerBalanceMigrationQueue,
      visitsMigrationQueue,
      csraMigrationQueue,
      alertsOffenderEventsQueue,
      caseNotesOffenderEventsQueue,
      financeOffenderEventsQueue,
      corePersonOffenderEventsQueue,
      courtSentencingOffenderEventsQueue,
      personalRelationshipsOffenderEventsQueue,
      personalRelationshipsDomainEventsQueue,
      personContactsDomainEventsQueue,
      organisationsOffenderEventsQueue,
      visitBalanceOffenderEventsQueue,
      externalMovementsOffenderEventsQueue,
      officialVisitsOffenderEventsQueue,
    )
  }

  @Autowired
  protected lateinit var jwtAuthHelper: JwtAuthorisationHelper

  @MockitoSpyBean
  protected lateinit var telemetryClient: TelemetryClient

  @MockitoSpyBean
  protected lateinit var externalMovementsMigrationService: ExternalMovementsMigrationService

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

internal fun SqsAsyncClient.sendMessage(queueOffenderEventsUrl: String, message: String) = sendMessage(SendMessageRequest.builder().queueUrl(queueOffenderEventsUrl).messageBody(message).build()).get()
internal fun HmppsQueue.sendMessage(message: String) = this.sqsClient.sendMessage(this.queueUrl, message = message)

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

fun HmppsQueue.countAllMessagesOnDLQQueue(): Int = this.sqsDlqClient!!.countAllMessagesOnQueue(dlqUrl!!).get()

fun HmppsQueue.hasMessagesOnDLQQueue(): Boolean = this.countAllMessagesOnDLQQueue() > 0
