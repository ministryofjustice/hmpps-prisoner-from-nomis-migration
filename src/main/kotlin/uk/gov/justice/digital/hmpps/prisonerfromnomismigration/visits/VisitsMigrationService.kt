package uk.gov.justice.digital.hmpps.prisonerfromnomismigration.visits

import com.microsoft.applicationinsights.TelemetryClient
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.data.MigrationContext
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.data.generateBatchId
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.service.AuditService
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.service.AuditType
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.service.MigrationHistoryService
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.service.MigrationQueueService
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.service.NomisApiService
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.service.NomisCodeDescription
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.service.NomisVisit
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.service.SynchronisationType.VISITS
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.service.VisitId
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.service.asStringOrBlank
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.visits.VisitMessages.CANCEL_MIGRATE_VISITS
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.visits.VisitMessages.MIGRATE_VISIT
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.visits.VisitMessages.MIGRATE_VISITS
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.visits.VisitMessages.MIGRATE_VISITS_BY_PAGE
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.visits.VisitMessages.MIGRATE_VISITS_STATUS_CHECK
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.visits.VisitMessages.RETRY_VISIT_MAPPING
import java.time.Duration
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime

@Service
class VisitsMigrationService(
  private val queueService: MigrationQueueService,
  private val nomisApiService: NomisApiService,
  private val visitMappingService: VisitMappingService,
  private val visitsService: VisitsService,
  private val migrationHistoryService: MigrationHistoryService,
  private val telemetryClient: TelemetryClient,
  private val auditService: AuditService,
  @Value("\${visits.page.size:1000}") private val pageSize: Long
) {
  private companion object {
    val log: Logger = LoggerFactory.getLogger(this::class.java)
  }

  suspend fun migrateVisits(migrationFilter: VisitsMigrationFilter): MigrationContext<VisitsMigrationFilter> {
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
      type = VISITS,
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
      migrationHistoryService.recordMigrationStarted(
        migrationId = it.migrationId,
        migrationType = VISITS,
        estimatedRecordCount = it.estimatedCount,
        filter = it.body
      )
      auditService.sendAuditEvent(
        AuditType.MIGRATION_STARTED.name,
        mapOf("migrationType" to VISITS.name, "migrationId" to it.migrationId, "filter" to it.body)
      )
    }
  }

  suspend fun divideVisitsByPage(context: MigrationContext<VisitsMigrationFilter>) {
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
    queueService.sendMessage(
      MIGRATE_VISITS_STATUS_CHECK,
      MigrationContext(
        context = context,
        body = VisitMigrationStatusCheck()
      )
    )
  }

  suspend fun migrateVisitsForPage(context: MigrationContext<VisitsPage>) =
    nomisApiService.getVisits(
      prisonIds = context.body.filter.prisonIds,
      visitTypes = context.body.filter.visitTypes,
      fromDateTime = context.body.filter.fromDateTime,
      toDateTime = context.body.filter.toDateTime,
      ignoreMissingRoom = context.body.filter.ignoreMissingRoom,
      pageNumber = context.body.pageNumber,
      pageSize = context.body.pageSize
    ).takeUnless {
      migrationHistoryService.isCancelling(context.migrationId)
    }?.content?.map {
      MigrationContext(
        context = context,
        body = it
      )
    }?.forEach { queueService.sendMessage(MIGRATE_VISIT, it) }

  suspend fun migrateVisit(context: MigrationContext<VisitId>) =
    visitMappingService.findNomisVisitMapping(context.body.visitId)
      ?.run {
        log.info("Will not migrate visit since it is migrated already, NOMIS id is ${context.body.visitId}, VSIP id is ${this.vsipId} as part migration ${this.label ?: "NONE"} (${this.mappingType})")
      }
      ?: run {
        val nomisVisit = nomisApiService.getVisit(context.body.visitId)
        determineRoomMapping(nomisVisit)
          ?.run {
            visitsService.createVisit(mapNomisVisit(nomisVisit, this))
              .also {
                createNomisVisitMapping(
                  nomisVisitId = nomisVisit.visitId,
                  vsipVisitId = it,
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
                    "vsipVisitId" to it,
                    "startDateTime" to nomisVisit.startDateTime.asStringOrBlank(),
                    "room" to this.room
                  ),
                  null
                )
              }
          } ?: run { handleNoRoomMappingFound(context.migrationId, nomisVisit) }
      }

  private suspend fun determineRoomMapping(
    nomisVisit: NomisVisit
  ): DateAwareRoomMapping? = if (isFutureVisit(nomisVisit) && !isErroneousFutureVisit(nomisVisit)) {
    nomisVisit.agencyInternalLocation?.let {
      visitMappingService.findRoomMapping(
        agencyInternalLocationCode = nomisVisit.agencyInternalLocation.description,
        prisonId = nomisVisit.prisonId
      )?.let {
        DateAwareRoomMapping(
          it.vsipId,
          restriction = if (it.isOpen) VisitRestriction.OPEN else VisitRestriction.CLOSED
        )
      }
    }
  } else
    DateAwareRoomMapping(
      room = nomisVisit.agencyInternalLocation?.let { nomisVisit.agencyInternalLocation.description } ?: "UNKNOWN",
      restriction = VisitRestriction.UNKNOWN
    )

  private fun isFutureVisit(nomisVisit: NomisVisit) =
    nomisVisit.startDateTime.toLocalDate() >= LocalDate.now()

  private fun isErroneousFutureVisit(nomisVisit: NomisVisit) =
    nomisVisit.startDateTime.toLocalDate() > LocalDate.now().plusYears(1)

  suspend fun migrateVisitsStatusCheck(context: MigrationContext<VisitMigrationStatusCheck>) {
    /*
       when checking if there are messages to process, it is always an estimation due to SQS, therefore once
       we think there are no messages we check several times in row reducing probability of false positives significantly
    */
    if (queueService.isItProbableThatThereAreStillMessagesToBeProcessed(context.type)) {
      queueService.sendMessage(
        MIGRATE_VISITS_STATUS_CHECK,
        MigrationContext(
          context = context,
          body = VisitMigrationStatusCheck()
        ),
        delaySeconds = 10
      )
    } else {
      if (context.body.hasCheckedAReasonableNumberOfTimes()) {
        telemetryClient.trackEvent(
          "nomis-migration-visits-completed",
          mapOf<String, String>(
            "migrationId" to context.migrationId,
            "estimatedCount" to context.estimatedCount.toString(),
            "durationMinutes" to context.durationMinutes().toString()
          ),
          null
        )
        migrationHistoryService.recordMigrationCompleted(
          migrationId = context.migrationId,
          recordsFailed = queueService.countMessagesThatHaveFailed(context.type),
          recordsMigrated = visitMappingService.getMigrationCount(context.migrationId),
        )
      } else {
        queueService.sendMessage(
          MIGRATE_VISITS_STATUS_CHECK,
          MigrationContext(
            context = context,
            body = context.body.increment()
          ),
          delaySeconds = 1
        )
      }
    }
  }

  suspend fun cancelMigrateVisitsStatusCheck(context: MigrationContext<VisitMigrationStatusCheck>) {
    /*
       when checking if there are messages to process, it is always an estimation due to SQS, therefore once
       we think there are no messages we check several times in row reducing probability of false positives significantly
    */
    if (queueService.isItProbableThatThereAreStillMessagesToBeProcessed(context.type)) {
      queueService.purgeAllMessages(context.type)
      queueService.sendMessage(
        CANCEL_MIGRATE_VISITS,
        MigrationContext(
          context = context,
          body = VisitMigrationStatusCheck()
        ),
        delaySeconds = 10
      )
    } else {
      if (context.body.hasCheckedAReasonableNumberOfTimes()) {
        telemetryClient.trackEvent(
          "nomis-migration-visits-cancelled",
          mapOf<String, String>(
            "migrationId" to context.migrationId,
            "estimatedCount" to context.estimatedCount.toString(),
            "durationMinutes" to context.durationMinutes().toString()
          ),
          null
        )
        migrationHistoryService.recordMigrationCancelled(
          migrationId = context.migrationId,
          recordsFailed = queueService.countMessagesThatHaveFailed(context.type),
          recordsMigrated = visitMappingService.getMigrationCount(context.migrationId),
        )
      } else {
        queueService.purgeAllMessages(context.type)
        queueService.sendMessage(
          CANCEL_MIGRATE_VISITS,
          MigrationContext(
            context = context,
            body = context.body.increment()
          ),
          delaySeconds = 1
        )
      }
    }
  }

  private suspend fun createNomisVisitMapping(
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

  private suspend fun handleNoRoomMappingFound(migrationId: String, nomisVisit: NomisVisit) {
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

  suspend fun retryCreateVisitMapping(context: MigrationContext<VisitMapping>) {
    visitMappingService.createNomisVisitMapping(
      nomisVisitId = context.body.nomisVisitId,
      vsipVisitId = context.body.vsipVisitId,
      migrationId = context.migrationId
    )
  }

  suspend fun findRoomUsageByFilter(filter: VisitsMigrationFilter): List<VisitRoomUsageResponse> {
    val roomUsage = nomisApiService.getRoomUsage(filter)
    return roomUsage.map { usage ->
      usage.copy(
        vsipRoom = visitMappingService.findRoomMapping(
          usage.agencyInternalLocationDescription,
          usage.prisonId
        )?.vsipId
      )
    }.sortedWith(compareBy(VisitRoomUsageResponse::prisonId, VisitRoomUsageResponse::agencyInternalLocationDescription))
  }

  suspend fun cancel(migrationId: String) {
    val migration = migrationHistoryService.get(migrationId)
    telemetryClient.trackEvent(
      "nomis-migration-visit-cancel-requested",
      mapOf<String, String>(
        "migrationId" to migration.migrationId,
      ),
      null
    )
    migrationHistoryService.recordMigrationCancelledRequested(migrationId)
    auditService.sendAuditEvent(
      AuditType.MIGRATION_CANCEL_REQUESTED.name,
      mapOf("migrationType" to VISITS.name, "migrationId" to migration.migrationId)
    )
    queueService.purgeAllMessagesNowAndAgainInTheNearFuture(
      MigrationContext(
        context = MigrationContext(
          type = VISITS,
          migrationId, migration.estimatedRecordCount, Unit
        ),
        body = VisitMigrationStatusCheck()
      ),
      message = CANCEL_MIGRATE_VISITS
    )
  }

  private fun mapNomisVisit(nomisVisit: NomisVisit, dateAwareRoomMapping: DateAwareRoomMapping): CreateVsipVisit {
    val visitNotesSet = mutableSetOf<VsipVisitNote>()
    val futureVisit = isFutureVisit(nomisVisit)
    nomisVisit.commentText?.apply {
      if (futureVisit) visitNotesSet.add(
        VsipVisitNote(
          VsipVisitNoteType.VISIT_COMMENT,
          this
        )
      )
    }
    nomisVisit.visitorConcernText?.apply {
      if (futureVisit) visitNotesSet.add(
        VsipVisitNote(
          VsipVisitNoteType.VISITOR_CONCERN,
          this
        )
      )
    }

    val startDateTime = applyPrisonSpecificVisitStartTimeMapping(nomisVisit)
    val endDateTime = applyPrisonSpecificVisitEndTimeMapping(nomisVisit)

    return CreateVsipVisit(
      prisonId = nomisVisit.prisonId,
      prisonerId = nomisVisit.offenderNo,
      startTimestamp = startDateTime,
      endTimestamp = endDateTime,
      visitType = nomisVisit.visitType.toVisitType(),
      visitStatus = getVsipVisitStatus(nomisVisit),
      outcomeStatus = getVsipOutcome(nomisVisit),
      visitRoom = dateAwareRoomMapping.room,
      contactList = nomisVisit.visitors.map {
        VsipVisitor(
          nomisPersonId = it.personId,
        )
      },
      legacyData = nomisVisit.leadVisitor?.let { VsipLegacyData(it.personId) },
      visitContact = nomisVisit.leadVisitor?.let {
        VsipLegacyContactOnVisit(
          name = it.fullName, telephone = it.telephones.firstOrNull()
        )
      },
      visitNotes = visitNotesSet,
      visitors = nomisVisit.visitors.map { v -> VsipVisitor(v.personId) }.toSet(),
      visitRestriction = dateAwareRoomMapping.restriction,
      createDateTime = nomisVisit.whenCreated,
      modifyDateTime = nomisVisit.whenUpdated
    )
  }

  private fun applyPrisonSpecificVisitStartTimeMapping(nomisVisit: NomisVisit) =
    if (isFutureVisit(nomisVisit) && nomisVisit.prisonId == "HEI") {
      if (nomisVisit.startDateTime.toLocalTime().isBefore(LocalTime.NOON)) {
        nomisVisit.startDateTime.toLocalDate().atTime(LocalTime.of(9, 0))
      } else {
        nomisVisit.startDateTime.toLocalDate().atTime(LocalTime.of(13, 45))
      }
    } else nomisVisit.startDateTime

  private fun applyPrisonSpecificVisitEndTimeMapping(nomisVisit: NomisVisit) =
    if (isFutureVisit(nomisVisit) && nomisVisit.prisonId == "HEI") {
      if (nomisVisit.startDateTime.toLocalTime().isBefore(LocalTime.NOON)) {
        nomisVisit.endDateTime.toLocalDate().atTime(LocalTime.of(10, 0))
      } else {
        nomisVisit.endDateTime.toLocalDate().atTime(LocalTime.of(14, 45))
      }
    } else nomisVisit.endDateTime
}

private fun <T> MigrationContext<T>.durationMinutes(): Long =
  Duration.between(LocalDateTime.parse(this.migrationId), LocalDateTime.now()).toMinutes()

data class VisitsPage(val filter: VisitsMigrationFilter, val pageNumber: Long, val pageSize: Long)

data class DateAwareRoomMapping(val room: String?, val restriction: VisitRestriction)

data class VisitMigrationStatusCheck(val checkCount: Int = 0) {
  fun hasCheckedAReasonableNumberOfTimes() = checkCount > 9
  fun increment() = this.copy(checkCount = checkCount + 1)
}

private fun NomisCodeDescription.toVisitType() = when (this.code) {
  "SCON" -> "SOCIAL"
  "OFFI" -> "OFFICIAL"
  else -> throw IllegalArgumentException("Unknown visit type ${this.code}")
}

class NoRoomMappingFoundException(val prisonId: String, val agencyInternalLocationDescription: String) :
  RuntimeException("No room mapping found for prisonId $prisonId and agencyInternalLocationDescription $agencyInternalLocationDescription")

data class VisitMapping(
  val nomisVisitId: Long,
  val vsipVisitId: String,
)
