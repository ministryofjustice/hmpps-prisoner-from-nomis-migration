package uk.gov.justice.digital.hmpps.prisonerfromnomismigration.visits

import com.microsoft.applicationinsights.TelemetryClient
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.data.MigrationContext
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.data.generateBatchId
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.service.Messages.MIGRATE_VISIT
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.service.Messages.MIGRATE_VISITS
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.service.Messages.MIGRATE_VISITS_BY_PAGE
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.service.Messages.RETRY_VISIT_MAPPING
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.service.MigrationQueueService
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.service.NomisApiService
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.service.NomisCodeDescription
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.service.NomisVisit
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.service.VisitId
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

@Service
class VisitsMigrationService(
  private val queueService: MigrationQueueService,
  private val nomisApiService: NomisApiService,
  private val visitMappingService: VisitMappingService,
  private val visitsService: VisitsService,
  private val telemetryClient: TelemetryClient,
  @Value("\${visits.page.size:1000}") private val pageSize: Long
) {
  private companion object {
    val log: Logger = LoggerFactory.getLogger(this::class.java)
  }

  fun migrateVisits(migrationFilter: VisitsMigrationFilter): MigrationContext<VisitsMigrationFilter> {
    val visitCount = nomisApiService.getVisits(
      prisonIds = migrationFilter.prisonIds,
      visitTypes = migrationFilter.visitTypes,
      fromDateTime = migrationFilter.fromDateTime,
      toDateTime = migrationFilter.toDateTime,
      ignoreMissingRoom = migrationFilter.ignoreMissingRoom,
      pageNumber = 0,
      pageSize = 1,
    ).totalElements

    return MigrationContext(
      migrationId = generateBatchId(),
      body = migrationFilter,
      estimatedCount = visitCount
    ).apply {
      queueService.sendMessage(MIGRATE_VISITS, this)
    }.also {
      telemetryClient.trackEvent(
        "nomis-migration-visits-started",
        mapOf<String, String>(
          "migrationId" to it.migrationId,
          "estimatedCount" to it.estimatedCount.toString(),
          "prisonIds" to it.body.prisonIds.joinToString(),
          "visitTypes" to it.body.visitTypes.joinToString(),
          "fromDateTime" to it.body.fromDateTime.asStringOrBlank(),
          "toDateTime" to it.body.toDateTime.asStringOrBlank(),
          "ignoreMissingRoom" to it.body.ignoreMissingRoom.toString()
        ),
        null
      )
    }
  }

  fun divideVisitsByPage(context: MigrationContext<VisitsMigrationFilter>) {
    (1..context.estimatedCount step pageSize).asSequence()
      .map {
        MigrationContext(
          context = context,
          body = VisitsPage(filter = context.body, pageNumber = it / pageSize, pageSize = pageSize)
        )
      }
      .forEach {
        queueService.sendMessage(MIGRATE_VISITS_BY_PAGE, it)
      }
  }

  fun migrateVisitsForPage(context: MigrationContext<VisitsPage>) = nomisApiService.getVisits(
    prisonIds = context.body.filter.prisonIds,
    visitTypes = context.body.filter.visitTypes,
    fromDateTime = context.body.filter.fromDateTime,
    toDateTime = context.body.filter.toDateTime,
    ignoreMissingRoom = context.body.filter.ignoreMissingRoom,
    pageNumber = context.body.pageNumber,
    pageSize = context.body.pageSize
  ).content.map {
    MigrationContext(
      context = context,
      body = it
    )
  }.forEach { queueService.sendMessage(MIGRATE_VISIT, it) }

  fun migrateVisit(context: MigrationContext<VisitId>) =
    visitMappingService.findNomisVisitMapping(context.body.visitId)
      ?.run {
        log.info("Will not migrate visit since it is migrated already, NOMIS id is ${context.body.visitId}, VSIP id is ${this.vsipId} as part migration ${this.label ?: "NONE"} (${this.mappingType})")
      }
      ?: run {
        val nomisVisit = nomisApiService.getVisit(context.body.visitId)
        nomisVisit.agencyInternalLocation?.let {
          visitMappingService.findRoomMapping(
            agencyInternalLocationCode = nomisVisit.agencyInternalLocation.description,
            prisonId = nomisVisit.prisonId
          )
        }?.run {
          visitsService.createVisit(mapNomisVisit(nomisVisit, this))
            .also {
              createNomisVisitMapping(
                nomisVisitId = nomisVisit.visitId,
                vsipVisitId = it.visitId,
                context = context
              )
            }.also {
              telemetryClient.trackEvent(
                "nomis-migration-visit-migrated",
                mapOf(
                  "migrationId" to context.migrationId,
                  "prisonId" to nomisVisit.prisonId,
                  "offenderNo" to nomisVisit.offenderNo,
                  "visitId" to nomisVisit.visitId.toString(),
                  "vsipVisitId" to it.visitId,
                  "startDateTime" to nomisVisit.startDateTime.asStringOrBlank(),
                  "room" to this.vsipId
                ),
                null
              )
            }
        } ?: run { handleNoRoomMappingFound(context.migrationId, nomisVisit) }
      }

  private fun createNomisVisitMapping(
    nomisVisitId: Long,
    vsipVisitId: String,
    context: MigrationContext<*>
  ) = try {
    visitMappingService.createNomisVisitMapping(
      nomisVisitId = nomisVisitId,
      vsipVisitId = vsipVisitId,
      migrationId = context.migrationId
    )
  } catch (e: Exception) {
    log.error("Failed to create mapping for visit $nomisVisitId, VSIP id $vsipVisitId", e)
    queueService.sendMessage(
      RETRY_VISIT_MAPPING,
      MigrationContext(
        context = context,
        body = VisitMapping(nomisVisitId = nomisVisitId, vsipVisitId = vsipVisitId)
      )
    )
  }

  private fun handleNoRoomMappingFound(migrationId: String, nomisVisit: NomisVisit) {
    telemetryClient.trackEvent(
      "nomis-migration-visit-no-room-mapping",
      mapOf<String, String>(
        "migrationId" to migrationId,
        "prisonId" to nomisVisit.prisonId,
        "offenderNo" to nomisVisit.offenderNo,
        "visitId" to nomisVisit.visitId.toString(),
        "startDateTime" to nomisVisit.startDateTime.asStringOrBlank(),
        "agencyInternalLocation" to nomisVisit.agencyInternalLocation?.description.orEmpty()
      ),
      null
    )

    throw NoRoomMappingFoundException(
      prisonId = nomisVisit.prisonId,
      agencyInternalLocationDescription = nomisVisit.agencyInternalLocation?.description
        ?: "NO NOMIS ROOM MAPPING FOUND"
    )
  }

  fun retryCreateVisitMapping(context: MigrationContext<VisitMapping>) {
    visitMappingService.createNomisVisitMapping(
      nomisVisitId = context.body.nomisVisitId,
      vsipVisitId = context.body.vsipVisitId,
      migrationId = context.migrationId
    )
  }
}

// TODO - where does comment go?
private fun mapNomisVisit(nomisVisit: NomisVisit, room: RoomMapping): CreateVsipVisit = CreateVsipVisit(
  prisonId = nomisVisit.prisonId,
  prisonerId = nomisVisit.offenderNo,
  startTimestamp = nomisVisit.startDateTime,
  endTimestamp = nomisVisit.endDateTime,
  visitType = nomisVisit.visitType.toVisitType(),
  visitStatus = nomisVisit.visitStatus.toVisitStatus(),
  visitRoom = room.vsipId,
  contactList = nomisVisit.visitors.map {
    VsipVisitor(
      nomisPersonId = it.personId,
      leadVisitor = it.leadVisitor,
    )
  },

)

data class VisitsPage(val filter: VisitsMigrationFilter, val pageNumber: Long, val pageSize: Long)

private fun NomisCodeDescription.toVisitType() = when (this.code) {
  "SCON" -> "STANDARD_SOCIAL"
  "OFFI" -> "OFFICIAL"
  else -> throw IllegalArgumentException("Unknown visit type ${this.code}")
}

private fun NomisCodeDescription.toVisitStatus() = when (this.code) {
  // TODO -> What statuses are there?
  "CANC" -> "CANCELLED_BY_PRISON"
  else -> "BOOKED"
}

class NoRoomMappingFoundException(val prisonId: String, val agencyInternalLocationDescription: String) :
  RuntimeException("No room mapping found for prisonId $prisonId and agencyInternalLocationDescription $agencyInternalLocationDescription")

private fun LocalDateTime?.asStringOrBlank(): String = this?.format(DateTimeFormatter.ISO_DATE_TIME) ?: ""

data class VisitMapping(
  val nomisVisitId: Long,
  val vsipVisitId: String,
)
