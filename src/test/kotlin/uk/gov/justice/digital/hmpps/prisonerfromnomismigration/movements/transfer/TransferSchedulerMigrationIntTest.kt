package uk.gov.justice.digital.hmpps.prisonerfromnomismigration.movements.transfer

import com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor
import com.github.tomakehurst.wiremock.client.WireMock.putRequestedFor
import com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.within
import org.awaitility.kotlin.atMost
import org.awaitility.kotlin.await
import org.awaitility.kotlin.untilAsserted
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.mockito.kotlin.any
import org.mockito.kotlin.check
import org.mockito.kotlin.eq
import org.mockito.kotlin.isNull
import org.mockito.kotlin.reset
import org.mockito.kotlin.verify
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.http.MediaType
import org.springframework.test.web.reactive.server.returnResult
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.data.MigrationContext
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.data.generateBatchId
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.helper.MigrationResult
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.movements.transfer.TransferScheduleDpsApiExtension.Companion.dpsTransferSchedulerServer
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.movements.transfer.TransferScheduleDpsApiMockServer.Companion.resyncResponse
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.movements.transfer.TransferScheduleNomisApiMockServer.Companion.offenderTransferMovementsResponse
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomismappings.model.TransferSchedulerPrisonerMappingIdsDto
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomismappings.model.TransferSchedulerPrisonerMappingsDto
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomisprisoner.model.PrisonNumberAndRootOffenderId
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.persistence.repository.MigrationHistoryRepository
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.service.MigrationType
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.transferschedule.model.ResyncTransfersRequest
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.wiremock.MappingApiExtension
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.wiremock.NomisApiExtension
import java.time.Duration
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.temporal.ChronoUnit
import java.util.*

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class TransferSchedulerMigrationIntTest(
  @Autowired private val transfersNomisApi: TransferScheduleNomisApiMockServer,
  @Autowired private val mappingApi: TransferScheduleMappingApiMockServer,
  @Autowired private val migrationService: TransferScheduleMigrationService,
  @Autowired private val migrationHistoryRepository: MigrationHistoryRepository,
) : TransferSchedulerIntegrationTestBase() {

  private val nomisApi = NomisApiExtension.nomisApi
  private val dpsApi = dpsTransferSchedulerServer

  private val dpsTransferId = UUID.randomUUID()
  private val dpsScheduledMovementId = UUID.randomUUID()
  private val dpsUnscheduledMovementId = UUID.randomUUID()
  private lateinit var migrationId: String

  // Because of how the ID ranges stubs work, we start with the unintuitive rootOffenderId=0 and prisonerNumber="A0000KT"
  private val prisonerNumber = "A0000KT"

  override fun resetTelemetryClient() {}

  internal fun setupMigrationTest() = runBlocking {
    migrationHistoryRepository.deleteAll()

    NomisApiExtension.resetAndDisableResetBeforeEach()
    MappingApiExtension.resetAndDisableResetBeforeEach()
    TransferScheduleDpsApiExtension.resetAndDisableResetBeforeEach()

    tearDownTelemetryClient()
  }

  @AfterAll
  fun tearDownTelemetryClient() = reset(telemetryClient)

  private fun stubMigrationDependencies(entities: Int = 2, resync: Boolean = false, pageSize: Long = 1) {
    nomisApi.stubGetPrisonerIds(entities.toLong(), 1, prisonerNumber)
    nomisApi.stubGetAllPrisonersIdRanges(pageSize = pageSize, totalElements = entities.toLong())
    (1..(entities / pageSize)).forEach { page ->
      val fromRootOffenderId = ((page - 1) * pageSize)
      val toRootOffenderId = (page * pageSize)
      nomisApi.stubGetAllPrisonersInRange(fromRootOffenderId, toRootOffenderId, prisonerNumber)
    }
    (0 until entities)
      .map { index -> "A%04dKT".format(index) }
      .forEach { prisonerNumber ->
        transfersNomisApi.stubGetOffenderTransferMovements(prisonerNumber, offenderTransferMovementsResponse(offenderNo = prisonerNumber))
        if (resync) {
          mappingApi.stubGetTransferSchedulerPrisonerMappingIds(prisonerNumber, 12345L, 1, dpsTransferId, 3, dpsScheduledMovementId, 4, dpsUnscheduledMovementId)
        } else {
          mappingApi.stubGetTransferSchedulerPrisonerMappingIds(prisonerNumber, idMappings = TransferSchedulerPrisonerMappingIdsDto(prisonerNumber, listOf(), listOf()))
        }
        transfersNomisApi.stubGetOffenderTransferMovements(prisonerNumber)
        dpsApi.stubResyncPrisonerTransfers(
          personIdentifier = prisonerNumber,
          response = resyncResponse(dpsTransferId, 1, dpsScheduledMovementId, 3, dpsUnscheduledMovementId, 4),
        )
        mappingApi.stubCreateTransferSchedulerPrisonerMappings()
      }
  }

  @Nested
  @TestInstance(TestInstance.Lifecycle.PER_CLASS)
  inner class MigrateEntity {
    @BeforeAll
    fun setUp() = runTest {
      setupMigrationTest()

      stubMigrationDependencies(entities = 1, resync = false)
      migrationId = performMigration()
    }

    @Test
    fun `should check for existing mappings`() {
      mappingApi.verify(getRequestedFor(urlPathEqualTo("/mapping/transfer-scheduler/$prisonerNumber/ids")))
    }

    @Test
    fun `should get NOMIS transfer movement details`() {
      transfersNomisApi.verify(getRequestedFor(urlPathEqualTo("/movements/$prisonerNumber/transfer")))
    }

    @Test
    fun `should call DPS resync API`() {
      dpsApi.verify(putRequestedFor(urlPathEqualTo("/resync/transfers/$prisonerNumber")))
    }

    @Test
    fun `will publish telemetry`() {
      verify(telemetryClient).trackEvent(
        eq("transfer-scheduler-migration-entity-migrated"),
        check {
          assertThat(it["offenderNo"]).isEqualTo(prisonerNumber)
          assertThat(it["migrationId"]).isEqualTo(migrationId)
        },
        isNull(),
      )
    }

    @Test
    fun `should populate DPS transfer`() {
      TransferScheduleDpsApiMockServer.getRequestBody<ResyncTransfersRequest>(
        putRequestedFor(urlPathEqualTo("/resync/transfers/$prisonerNumber")),
      ).apply {
        with(transfers[0]) {
          assertThat(created.at).isCloseTo(LocalDateTime.now(), within(5, ChronoUnit.MINUTES))
          assertThat(created.by).isEqualTo("SYS")
        }
        with(transfers[0].transfer) {
          assertThat(eventId).isEqualTo(1)
          assertThat(dpsId).isNull()
        }
        with(transfers[0].transfer.schedule!!) {
          assertThat(eventSubType).isEqualTo("TRN")
          assertThat(eventStatus).isEqualTo("SCH")
          assertThat(agyLocId).isEqualTo("BXI")
          assertThat(toAgyLocId).isEqualTo("LEI")
          assertThat(start).isCloseTo(LocalDateTime.now(), within(5, ChronoUnit.MINUTES))
          assertThat(commentText).isEqualTo("transfer schedule comment")
          assertThat(hiddenCommentText).isEqualTo("hidden transfer schedule comment")
          assertThat(outcomeReasonCode).isEqualTo("ADMI")
          assertThat(escortCode).isEqualTo("U")
        }
      }
    }

    @Test
    fun `should populate DPS transfer waitlist`() {
      TransferScheduleDpsApiMockServer.getRequestBody<ResyncTransfersRequest>(
        putRequestedFor(urlPathEqualTo("/resync/transfers/$prisonerNumber")),
      ).apply {
        with(transfers[0].transfer.waitlist!!) {
          assertThat(requestDate).isEqualTo(LocalDate.now().minusDays(1))
          assertThat(waitListStatus).isEqualTo("APPROVED")
          assertThat(statusDate).isEqualTo(LocalDate.now().minusDays(1))
          assertThat(transferPriority).isEqualTo("3")
          assertThat(approved).isTrue
          assertThat(approvedUsername).isEqualTo("A_USER")
          assertThat(outcomeReasonCode?.value).isEqualTo("TRANS")
          assertThat(commentText1).isEqualTo("some waitlist comment")
        }
      }
    }

    @Test
    fun `should populate DPS transfer movement`() {
      TransferScheduleDpsApiMockServer.getRequestBody<ResyncTransfersRequest>(
        putRequestedFor(urlPathEqualTo("/resync/transfers/$prisonerNumber")),
      ).apply {
        with(transfers[0].movement!!) {
          assertThat(created.at).isCloseTo(LocalDateTime.now().minusDays(1), within(5, ChronoUnit.MINUTES))
          assertThat(created.by).isEqualTo("SYS")
        }
        with(transfers[0].movement!!.movement) {
          assertThat(dpsId).isNull()
          assertThat(dpsTransferId).isNull()
          assertThat(offenderBookId).isEqualTo(12345L)
          assertThat(movementSeq).isEqualTo(3)
          assertThat(occurredAt).isCloseTo(LocalDateTime.now(), within(5, ChronoUnit.MINUTES))
          assertThat(movementReasonCode).isEqualTo("28")
          assertThat(escortCode).isEqualTo("PECS")
          assertThat(fromAgyLocId).isEqualTo("BXI")
          assertThat(toAgyLocId).isEqualTo("LEI")
          assertThat(active).isTrue
          assertThat(commentText).isEqualTo("some transfer movement comment")
        }
      }
    }

    @Test
    fun `should populate DPS unscheduled transfer movement`() {
      TransferScheduleDpsApiMockServer.getRequestBody<ResyncTransfersRequest>(
        putRequestedFor(urlPathEqualTo("/resync/transfers/$prisonerNumber")),
      ).apply {
        with(unscheduledMovements[0]) {
          assertThat(created.at).isCloseTo(LocalDateTime.now().minusDays(1), within(5, ChronoUnit.MINUTES))
          assertThat(created.by).isEqualTo("SYS")
        }
        with(unscheduledMovements[0].movement) {
          assertThat(dpsId).isNull()
          assertThat(dpsTransferId).isNull()
          assertThat(offenderBookId).isEqualTo(12345L)
          assertThat(movementSeq).isEqualTo(4)
          assertThat(occurredAt).isCloseTo(LocalDateTime.now(), within(5, ChronoUnit.MINUTES))
          assertThat(movementReasonCode).isEqualTo("28")
          assertThat(escortCode).isEqualTo("PECS")
          assertThat(fromAgyLocId).isEqualTo("BXI")
          assertThat(toAgyLocId).isEqualTo("LEI")
          assertThat(active).isTrue
          assertThat(commentText).isEqualTo("some transfer movement comment")
        }
      }
    }

    @Test
    fun `should update mappings`() {
      TransferScheduleMappingApiMockServer.getRequestBody<TransferSchedulerPrisonerMappingsDto>(
        putRequestedFor(urlPathEqualTo("/mapping/transfer-scheduler/migrate")),
      ).apply {
        assertThat(offenderNo).isEqualTo(prisonerNumber)
        assertThat(migrationId).isNotEmpty
        with(bookings[0]) {
          assertThat(bookingId).isEqualTo(12345L)
        }
        with(bookings[0].schedules[0]) {
          assertThat(nomisEventId).isEqualTo(1)
          assertThat(dpsTransferId).isEqualTo(dpsTransferId)
        }
        with(bookings[0].schedules[0].movement!!) {
          assertThat(nomisMovementSeq).isEqualTo(3)
          assertThat(dpsTransferMovementId).isEqualTo(dpsScheduledMovementId)
        }
        with(bookings[0].unscheduledMovements[0]) {
          assertThat(nomisMovementSeq).isEqualTo(4)
          assertThat(dpsTransferMovementId).isEqualTo(dpsUnscheduledMovementId)
        }
      }
    }
  }

  @Nested
  @TestInstance(TestInstance.Lifecycle.PER_CLASS)
  inner class Resync {
    @BeforeAll
    fun setUp() = runTest {
      setupMigrationTest()

      stubMigrationDependencies(entities = 1, resync = true)

      // TODO expand this to call the resync endpoint
      migrationService.migrateNomisEntity(
        MigrationContext(
          MigrationType.TRANSFER_MOVEMENTS,
          generateBatchId(),
          1,
          PrisonNumberAndRootOffenderId(1, prisonerNumber),
          mutableMapOf(),
        ),
      )
    }

    @Test
    fun `should populate NOMIS but not DPS IDs`() {
      TransferScheduleDpsApiMockServer.getRequestBody<ResyncTransfersRequest>(
        putRequestedFor(urlPathEqualTo("/resync/transfers/$prisonerNumber")),
      ).apply {
        with(transfers[0].transfer) {
          assertThat(eventId).isEqualTo(1)
          assertThat(dpsId).isEqualTo(dpsTransferId)
        }
        with(transfers[0].movement!!.movement) {
          assertThat(dpsId).isEqualTo(dpsScheduledMovementId)
          assertThat(dpsTransferId).isEqualTo(dpsTransferId)
          assertThat(offenderBookId).isEqualTo(12345L)
          assertThat(movementSeq).isEqualTo(3)
        }
        with(unscheduledMovements[0].movement) {
          assertThat(dpsId).isEqualTo(dpsUnscheduledMovementId)
          assertThat(dpsTransferId).isNull()
          assertThat(offenderBookId).isEqualTo(12345L)
          assertThat(movementSeq).isEqualTo(4)
        }
      }
    }

    @Test
    fun `should create mappings`() {
      TransferScheduleMappingApiMockServer.getRequestBody<TransferSchedulerPrisonerMappingsDto>(
        putRequestedFor(urlPathEqualTo("/mapping/transfer-scheduler/migrate")),
      ).apply {
        assertThat(offenderNo).isEqualTo(prisonerNumber)
        assertThat(migrationId).isNotEmpty
        with(bookings[0]) {
          assertThat(bookingId).isEqualTo(12345L)
        }
        with(bookings[0].schedules[0]) {
          assertThat(nomisEventId).isEqualTo(1)
          assertThat(dpsTransferId).isEqualTo(dpsTransferId)
        }
        with(bookings[0].schedules[0].movement!!) {
          assertThat(nomisMovementSeq).isEqualTo(3)
          assertThat(dpsTransferMovementId).isEqualTo(dpsScheduledMovementId)
        }
        with(bookings[0].unscheduledMovements[0]) {
          assertThat(nomisMovementSeq).isEqualTo(4)
          assertThat(dpsTransferMovementId).isEqualTo(dpsUnscheduledMovementId)
        }
      }
    }
  }

  private fun performMigration(prisonerNumber: String? = null): String = webTestClient.post()
    .uri("/migrate/transfer-scheduler")
    .headers(setAuthorisation(roles = listOf("ROLE_PRISONER_FROM_NOMIS__MIGRATION__RW")))
    .contentType(MediaType.APPLICATION_JSON)
    .apply { prisonerNumber?.let { bodyValue("""{"prisonerNumber":"$prisonerNumber"}""") } ?: bodyValue("{}") }
    .exchange()
    .expectStatus().isAccepted
    .returnResult<MigrationResult>().responseBody.blockFirst()!!
    .migrationId
    .also {
      waitUntilCompleted()
    }

  private fun waitUntilCompleted(name: String = "transfer-scheduler-migration-completed") = await atMost Duration.ofSeconds(60) untilAsserted {
    verify(telemetryClient).trackEvent(
      eq(name),
      any(),
      isNull(),
    )
  }
}
