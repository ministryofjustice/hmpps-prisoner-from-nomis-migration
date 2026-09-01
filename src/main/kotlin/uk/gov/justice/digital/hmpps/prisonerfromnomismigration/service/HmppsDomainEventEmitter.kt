package uk.gov.justice.digital.hmpps.prisonerfromnomismigration.service

import com.microsoft.applicationinsights.TelemetryClient
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import tools.jackson.databind.json.JsonMapper
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.service.HmppsDomainEventEmitter.Companion.COURT_APPEARANCE_CLONE_EVENT_TYPE
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.service.HmppsDomainEventEmitter.CourtAppearanceCloneEvent
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.service.PersonReference.Companion.withNomsNumber
import uk.gov.justice.hmpps.sqs.HmppsQueueService
import uk.gov.justice.hmpps.sqs.HmppsTopic
import uk.gov.justice.hmpps.sqs.publish
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlin.reflect.KClass
import kotlin.reflect.full.memberProperties

@Service
class HmppsDomainEventEmitter(
  private val jsonMapper: JsonMapper,
  private val hmppsQueueService: HmppsQueueService,
  private val telemetryClient: TelemetryClient,
) {

  companion object {
    private val log: Logger = LoggerFactory.getLogger(this::class.java)
    const val COURT_APPEARANCE_CLONE_EVENT_TYPE = "nomis-sync.court-appearance.cloned"
  }

  private val domainEventsTopic by lazy { hmppsQueueService.findByTopicId("hmppseventtopic") as HmppsTopic }

  private fun <T : PrisonerAdditionalInformation> PrisonerDomainEvent<T>.publish() {
    val event = PrisonerDomainEvent(
      additionalInformation = this.additionalInformation,
      eventType = this.eventType,
      occurredAt = this.occurredAt,
      version = this.version,
      description = this.description,
    )

    runCatching {
      domainEventsTopic.publish(
        event.eventType,
        jsonMapper.writeValueAsString(event),
      )
      telemetryClient.trackEvent(event.eventType, event.asMap(), null)
    }.onFailure {
      log.error("Failed to publish event ${event.eventType} for prisoner ${event.additionalInformation.nomsNumber}", it)
    }
  }

  fun emitCourtAppearanceClone(offenderNo: String, previousBookingAppearanceId: String, currentBookingAppearanceId: String) {
    val courtAppearanceCloneEvent = CourtAppearanceCloneEvent(
      nomsNumber = offenderNo,
      previousBookingAppearanceId = previousBookingAppearanceId,
      currentBookingAppearanceId = currentBookingAppearanceId,
    )
    val event = CourtAppearanceCloneDomainEvent(courtAppearanceCloneEvent, Instant.now())
    event.publish()
  }

  data class CourtAppearanceCloneEvent(
    override val nomsNumber: String,
    val previousBookingAppearanceId: String,
    val currentBookingAppearanceId: String,
  ) : PrisonerAdditionalInformation
}

interface PrisonerAdditionalInformation {
  val nomsNumber: String
}

class CourtAppearanceCloneDomainEvent(additionalInformation: CourtAppearanceCloneEvent, occurredAt: Instant) :
  PrisonerDomainEvent<CourtAppearanceCloneEvent>(
    additionalInformation = additionalInformation,
    occurredAt = occurredAt,
    description = "A court appearance has been cloned in NOMIS",
    eventType = COURT_APPEARANCE_CLONE_EVENT_TYPE,
  )

open class PrisonerDomainEvent<T : PrisonerAdditionalInformation>(
  val additionalInformation: T,
  val occurredAt: String,
  val eventType: String,
  val version: Int,
  val description: String,
  @Suppress("unused")
  val personReference: PersonReference = withNomsNumber(additionalInformation.nomsNumber),
) {
  constructor(
    additionalInformation: T,
    occurredAt: Instant = Instant.now(),
    description: String,
    eventType: String,
  ) :
    this(
      additionalInformation = additionalInformation,
      occurredAt = DateTimeFormatter.ISO_OFFSET_DATE_TIME.withZone(ZoneId.of("Europe/London")).format(occurredAt),
      eventType = eventType,
      version = 1,
      description = description,
    )
}

data class PersonReference(val identifiers: List<Identifier> = listOf()) {
  companion object {
    private const val NOMS_NUMBER_TYPE = "NOMS"
    fun withNomsNumber(prisonNumber: String) = PersonReference(listOf(Identifier(NOMS_NUMBER_TYPE, prisonNumber)))
  }

  data class Identifier(val type: String, val value: String)
}

fun <T : PrisonerAdditionalInformation> PrisonerDomainEvent<T>.asMap(): Map<String, String> = mutableMapOf(
  "occurredAt" to occurredAt,
  "eventType" to eventType,
  "version" to version.toString(),
  "description" to description,
).also { it.putAll(additionalInformation.asMap()) }

fun <T : PrisonerAdditionalInformation> T.asMap(): Map<String, String> =
  @Suppress("UNCHECKED_CAST")
  (this::class as KClass<T>).memberProperties
    .filter { it.get(this) != null }
    .associate { prop ->
      "additionalInformation.${prop.name}" to prop.get(this).toString()
    }

data class DomainEvent(val eventType: String, val body: String)
