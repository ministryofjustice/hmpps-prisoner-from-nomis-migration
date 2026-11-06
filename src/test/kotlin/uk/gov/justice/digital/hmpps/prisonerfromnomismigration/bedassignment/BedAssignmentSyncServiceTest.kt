package uk.gov.justice.digital.hmpps.prisonerfromnomismigration.bedassignment

import com.microsoft.applicationinsights.TelemetryClient
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.argThat
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.eq
import org.mockito.kotlin.inOrder
import org.mockito.kotlin.isNull
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.bedassignment.model.BedAssignmentNomisMapping
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.bedassignment.model.BedAssignmentSyncResponse
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.integration.history.CreateMappingResult
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.integration.history.DuplicateErrorContent
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.integration.history.DuplicateErrorResponse
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.listeners.SynchronisationMessageType.RETRY_SYNCHRONISATION_MAPPING
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomismappings.model.BedAssignmentMappingDto
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.service.InternalMessage
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.service.SynchronisationQueueService
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.service.SynchronisationType
import java.time.LocalDateTime
import java.util.UUID

class BedAssignmentSyncServiceTest {
  private val historyService = mock<TranslatorService>()
  private val dpsApiService = mock<BedAssignmentDpsApiService>()
  private val mappingApiService = mock<BedAssignmentMappingApiService>()
  private val telemetryClient = mock<TelemetryClient>()
  private val queueService = mock<SynchronisationQueueService>()
  private val service = BedAssignmentSyncService(
    historyService,
    dpsApiService,
    mappingApiService,
    telemetryClient,
    queueService,
  )
  private val event = BedAssignmentEvent(
    eventType = "BED_ASSIGNMENT_HISTORY-INSERTED",
    eventDatetime = LocalDateTime.parse("2026-11-04T12:00:00"),
    offenderIdDisplay = "A1234AA",
    bookingId = 123L,
    bedAssignmentSeq = 1,
    auditModuleName = "OIDSTUFF",
  )
  private val dpsId = UUID.fromString("11111111-1111-4111-8111-111111111111")
  private val deletedDpsId = "22222222-2222-4222-8222-222222222222"
  private val mapping = BedAssignmentMappingDto(
    dpsBedAssignmentId = dpsId.toString(),
    nomisBookingId = 123,
    nomisSequence = 1,
    mappingType = BedAssignmentMappingDto.MappingType.NOMIS_CREATED,
  )

  private suspend fun stubSyncResponse() {
    whenever(historyService.getHistory(event.offenderIdDisplay)).thenReturn(emptyList())
    whenever(dpsApiService.sync(event.offenderIdDisplay, emptyList())).thenReturn(
      BedAssignmentSyncResponse(
        created = listOf(BedAssignmentNomisMapping(dpsId, 123, 1)),
        updated = emptyList(),
        deleted = listOf(BedAssignmentNomisMapping(UUID.fromString(deletedDpsId), 123, 2)),
      ),
    )
  }

  @Nested
  inner class MappingRecovery {
    @Test
    fun `queues the original response when a mapping lookup fails`() = runTest {
      stubSyncResponse()
      whenever(mappingApiService.getMappingByNomisIdOrNull(123, 1)).thenThrow(IllegalStateException("mapping lookup failed"))
      val retry = argumentCaptor<BedAssignmentMappingRetry>()

      service.create(event)

      verify(queueService).sendMessage(
        eq(RETRY_SYNCHRONISATION_MAPPING.name),
        eq(SynchronisationType.BED_ASSIGNMENTS),
        retry.capture(),
        any(),
        any(),
      )
      assertThat(retry.firstValue.mappings).containsExactly(mapping)
      assertThat(retry.firstValue.deletedDpsIds).containsExactly(deletedDpsId)
      verify(dpsApiService).sync(any(), any())
      verify(telemetryClient).trackEvent(
        eq("bedassignment-synchronisation-created-success"),
        argThat { this["mapping"] == "initial-failure" && this["original-error"] == "mapping lookup failed" },
        isNull(),
      )
    }

    @Test
    fun `does not acknowledge a failed retry publication`() = runTest {
      stubSyncResponse()
      whenever(mappingApiService.deleteMappingByDpsId(deletedDpsId)).thenThrow(IllegalStateException("mapping delete failed"))
      whenever(
        queueService.sendMessage(
          any(),
          any(),
          any(),
          any(),
          any(),
        ),
      ).thenThrow(IllegalStateException("queue publication failed"))

      assertThatThrownBy { runBlocking { service.create(event) } }
        .isInstanceOf(IllegalStateException::class.java)
        .hasMessage("queue publication failed")

      verify(telemetryClient, never()).trackEvent(eq("bedassignment-synchronisation-created-success"), any(), anyOrNull())
      verify(telemetryClient).trackEvent(
        eq("bedassignment-synchronisation-created-error"),
        argThat { this["error"] == "queue publication failed" },
        isNull(),
      )
    }

    @Test
    fun `does not enqueue mapping work when the coroutine is cancelled`() = runTest {
      stubSyncResponse()
      whenever(mappingApiService.deleteMappingByDpsId(deletedDpsId)).thenThrow(CancellationException("cancelled"))

      assertThatThrownBy { runBlocking { service.create(event) } }.isInstanceOf(CancellationException::class.java)

      verify(queueService, never()).sendMessage(any(), any(), any(), any(), any())
    }
  }

  @Nested
  inner class RetryingMappings {
    private val retry = InternalMessage(
      BedAssignmentMappingRetry(listOf(mapping), listOf(deletedDpsId)),
      mapOf("offenderNo" to event.offenderIdDisplay),
    )

    @Test
    fun `retries original delete IDs before missing mappings without calling DPS or NOMIS`() = runTest {
      whenever(mappingApiService.getMappingByNomisIdOrNull(123, 1)).thenReturn(null)
      whenever(mappingApiService.createMapping(eq(mapping), any())).thenReturn(CreateMappingResult())

      service.retryCreateMapping(retry)

      inOrder(mappingApiService) {
        verify(mappingApiService).deleteMappingByDpsId(deletedDpsId)
        verify(mappingApiService).getMappingByNomisIdOrNull(123, 1)
        verify(mappingApiService).createMapping(eq(mapping), any())
      }
      verify(dpsApiService, never()).sync(any(), any())
      verify(historyService, never()).getHistory(any())
      verify(telemetryClient).trackEvent("bedassignment-mapping-created-success", retry.telemetryAttributes, null)
    }

    @Test
    fun `leaves a matching mapping unchanged on retry after partial progress`() = runTest {
      whenever(mappingApiService.getMappingByNomisIdOrNull(123, 1)).thenReturn(
        mapping.copy(mappingType = BedAssignmentMappingDto.MappingType.MIGRATED),
      )

      service.retryCreateMapping(retry)

      verify(mappingApiService, never()).createMapping(any(), any())
    }

    @Test
    fun `accepts an identical mapping created concurrently`() = runTest {
      whenever(mappingApiService.getMappingByNomisIdOrNull(123, 1)).thenReturn(null)
      whenever(mappingApiService.createMapping(eq(mapping), any())).thenReturn(
        CreateMappingResult(DuplicateErrorResponse(DuplicateErrorContent(mapping, mapping))),
      )

      service.retryCreateMapping(retry)

      verify(telemetryClient).trackEvent(eq("bedassignment-from-nomis-sync-duplicate"), any(), isNull())
      verify(telemetryClient).trackEvent("bedassignment-mapping-created-success", retry.telemetryAttributes, null)
    }

    @Test
    fun `reports and rejects a conflicting lookup mapping`() = runTest {
      whenever(mappingApiService.getMappingByNomisIdOrNull(123, 1)).thenReturn(mapping.copy(dpsBedAssignmentId = deletedDpsId))

      assertThatThrownBy { runBlocking { service.retryCreateMapping(retry) } }
        .isInstanceOf(IllegalStateException::class.java)
        .hasMessageContaining("Conflicting bed-assignment mapping")

      verify(mappingApiService, never()).createMapping(any(), any())
      verify(telemetryClient).trackEvent(
        eq("bedassignment-from-nomis-sync-duplicate"),
        argThat { this["existingDpsBedAssignmentId"] == deletedDpsId && this["duplicateDpsBedAssignmentId"] == dpsId.toString() },
        isNull(),
      )
      verify(telemetryClient).trackEvent(eq("bedassignment-mapping-created-failure"), any(), isNull())
    }

    @Test
    fun `reports and rejects a conflict from mapping creation`() = runTest {
      whenever(mappingApiService.getMappingByNomisIdOrNull(123, 1)).thenReturn(null)
      whenever(mappingApiService.createMapping(eq(mapping), any())).thenReturn(
        CreateMappingResult(DuplicateErrorResponse(DuplicateErrorContent(mapping, mapping.copy(dpsBedAssignmentId = deletedDpsId)))),
      )

      assertThatThrownBy { runBlocking { service.retryCreateMapping(retry) } }
        .isInstanceOf(IllegalStateException::class.java)
        .hasMessageContaining("Conflicting bed-assignment mapping")

      verify(telemetryClient).trackEvent(eq("bedassignment-from-nomis-sync-duplicate"), any(), isNull())
      verify(telemetryClient, never()).trackEvent(eq("bedassignment-mapping-created-success"), any(), isNull())
    }

    @Test
    fun `propagates a persistent delete failure to the queue retry`() = runTest {
      whenever(mappingApiService.deleteMappingByDpsId(deletedDpsId)).thenThrow(IllegalStateException("mapping delete failed"))

      assertThatThrownBy { runBlocking { service.retryCreateMapping(retry) } }.hasMessage("mapping delete failed")

      verify(telemetryClient).trackEvent(
        "bedassignment-mapping-created-failure",
        retry.telemetryAttributes + ("error" to "mapping delete failed"),
        null,
      )
      verify(mappingApiService, never()).getMappingByNomisIdOrNull(any(), any())
    }

    @Test
    fun `propagates cancellation without reporting retry success or failure`() = runTest {
      whenever(mappingApiService.deleteMappingByDpsId(deletedDpsId)).thenThrow(CancellationException("cancelled"))

      assertThatThrownBy { runBlocking { service.retryCreateMapping(retry) } }.isInstanceOf(CancellationException::class.java)

      verify(telemetryClient, never()).trackEvent(eq("bedassignment-mapping-created-success"), any(), anyOrNull())
      verify(telemetryClient, never()).trackEvent(eq("bedassignment-mapping-created-failure"), any(), anyOrNull())
    }

    @Test
    fun `empty retry has no downstream effects`() = runTest {
      service.retryCreateMapping(InternalMessage(BedAssignmentMappingRetry(emptyList(), emptyList()), emptyMap()))

      verify(mappingApiService, never()).deleteMappingByDpsId(any())
      verify(mappingApiService, never()).getMappingByNomisIdOrNull(any(), any())
      verify(mappingApiService, never()).createMapping(any(), any())
    }
  }
}
