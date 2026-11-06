package uk.gov.justice.digital.hmpps.prisonerfromnomismigration.bedassignment

import com.microsoft.applicationinsights.TelemetryClient
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.coVerifyOrder
import io.mockk.just
import io.mockk.mockk
import io.mockk.runs
import io.mockk.slot
import io.mockk.verify
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
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
  private val historyService = mockk<TranslatorService>()
  private val dpsApiService = mockk<BedAssignmentDpsApiService>()
  private val mappingApiService = mockk<BedAssignmentMappingApiService>()
  private val telemetryClient = mockk<TelemetryClient>(relaxed = true)
  private val queueService = mockk<SynchronisationQueueService>()
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

  private fun stubSyncResponse() {
    coEvery { historyService.getHistory(event.offenderIdDisplay) } returns emptyList()
    coEvery { dpsApiService.sync(event.offenderIdDisplay, emptyList()) } returns BedAssignmentSyncResponse(
      created = listOf(BedAssignmentNomisMapping(dpsId, 123, 1)),
      updated = emptyList(),
      deleted = listOf(BedAssignmentNomisMapping(UUID.fromString(deletedDpsId), 123, 2)),
    )
  }

  @Nested
  inner class MappingRecovery {
    @Test
    fun `queues the original response when a mapping lookup fails`() = runTest {
      stubSyncResponse()
      coEvery { mappingApiService.deleteMappingByDpsId(deletedDpsId) } just runs
      coEvery {
        mappingApiService.getMappingByNomisIdOrNull(
          123,
          1,
        )
      } throws IllegalStateException("mapping lookup failed")
      val retry = slot<BedAssignmentMappingRetry>()
      coEvery {
        queueService.sendMessage(
          RETRY_SYNCHRONISATION_MAPPING.name,
          SynchronisationType.BED_ASSIGNMENTS,
          capture(retry),
          any(),
          any(),
        )
      } just runs

      service.create(event)

      assertThat(retry.captured.mappings).containsExactly(mapping)
      assertThat(retry.captured.deletedDpsIds).containsExactly(deletedDpsId)
      coVerify(exactly = 1) { dpsApiService.sync(any(), any()) }
      verify {
        telemetryClient.trackEvent(
          "bedassignment-synchronisation-created-success",
          match { it["mapping"] == "initial-failure" && it["original-error"] == "mapping lookup failed" },
          null,
        )
      }
    }

    @Test
    fun `does not acknowledge a failed retry publication`() = runTest {
      stubSyncResponse()
      coEvery { mappingApiService.deleteMappingByDpsId(deletedDpsId) } throws IllegalStateException("mapping delete failed")
      coEvery {
        queueService.sendMessage(
          any(),
          any(),
          any(),
          any(),
          any(),
        )
      } throws IllegalStateException("queue publication failed")

      assertThatThrownBy { runBlocking { service.create(event) } }
        .isInstanceOf(IllegalStateException::class.java)
        .hasMessage("queue publication failed")

      verify(exactly = 0) { telemetryClient.trackEvent("bedassignment-synchronisation-created-success", any(), any()) }
      verify {
        telemetryClient.trackEvent(
          "bedassignment-synchronisation-created-error",
          match { it["error"] == "queue publication failed" },
          null,
        )
      }
    }

    @Test
    fun `does not enqueue mapping work when the coroutine is cancelled`() = runTest {
      stubSyncResponse()
      coEvery { mappingApiService.deleteMappingByDpsId(deletedDpsId) } throws CancellationException("cancelled")

      assertThatThrownBy { runBlocking { service.create(event) } }.isInstanceOf(CancellationException::class.java)

      coVerify(exactly = 0) { queueService.sendMessage(any(), any(), any(), any(), any()) }
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
      coEvery { mappingApiService.deleteMappingByDpsId(deletedDpsId) } just runs
      coEvery { mappingApiService.getMappingByNomisIdOrNull(123, 1) } returns null
      coEvery { mappingApiService.createMapping(mapping, any()) } returns CreateMappingResult()

      service.retryCreateMapping(retry)

      coVerifyOrder {
        mappingApiService.deleteMappingByDpsId(deletedDpsId)
        mappingApiService.getMappingByNomisIdOrNull(123, 1)
        mappingApiService.createMapping(mapping, any())
      }
      coVerify(exactly = 0) { dpsApiService.sync(any(), any()) }
      coVerify(exactly = 0) { historyService.getHistory(any()) }
      verify { telemetryClient.trackEvent("bedassignment-mapping-created-success", retry.telemetryAttributes, null) }
    }

    @Test
    fun `leaves a matching mapping unchanged on retry after partial progress`() = runTest {
      coEvery { mappingApiService.deleteMappingByDpsId(deletedDpsId) } just runs
      coEvery {
        mappingApiService.getMappingByNomisIdOrNull(
          123,
          1,
        )
      } returns mapping.copy(mappingType = BedAssignmentMappingDto.MappingType.MIGRATED)

      service.retryCreateMapping(retry)

      coVerify(exactly = 0) { mappingApiService.createMapping(any(), any()) }
    }

    @Test
    fun `accepts an identical mapping created concurrently`() = runTest {
      coEvery { mappingApiService.deleteMappingByDpsId(deletedDpsId) } just runs
      coEvery { mappingApiService.getMappingByNomisIdOrNull(123, 1) } returns null
      coEvery { mappingApiService.createMapping(mapping, any()) } returns CreateMappingResult(
        DuplicateErrorResponse(DuplicateErrorContent(mapping, mapping)),
      )

      service.retryCreateMapping(retry)

      verify { telemetryClient.trackEvent("bedassignment-from-nomis-sync-duplicate", any(), null) }
      verify { telemetryClient.trackEvent("bedassignment-mapping-created-success", retry.telemetryAttributes, null) }
    }

    @Test
    fun `reports and rejects a conflicting lookup mapping`() = runTest {
      coEvery { mappingApiService.deleteMappingByDpsId(deletedDpsId) } just runs
      coEvery {
        mappingApiService.getMappingByNomisIdOrNull(
          123,
          1,
        )
      } returns mapping.copy(dpsBedAssignmentId = deletedDpsId)

      assertThatThrownBy { runBlocking { service.retryCreateMapping(retry) } }
        .isInstanceOf(IllegalStateException::class.java)
        .hasMessageContaining("Conflicting bed-assignment mapping")

      coVerify(exactly = 0) { mappingApiService.createMapping(any(), any()) }
      verify {
        telemetryClient.trackEvent(
          "bedassignment-from-nomis-sync-duplicate",
          match { it["existingDpsBedAssignmentId"] == deletedDpsId && it["duplicateDpsBedAssignmentId"] == dpsId.toString() },
          null,
        )
      }
      verify { telemetryClient.trackEvent("bedassignment-mapping-created-failure", any(), null) }
    }

    @Test
    fun `reports and rejects a conflict from mapping creation`() = runTest {
      coEvery { mappingApiService.deleteMappingByDpsId(deletedDpsId) } just runs
      coEvery { mappingApiService.getMappingByNomisIdOrNull(123, 1) } returns null
      coEvery { mappingApiService.createMapping(mapping, any()) } returns CreateMappingResult(
        DuplicateErrorResponse(DuplicateErrorContent(mapping, mapping.copy(dpsBedAssignmentId = deletedDpsId))),
      )

      assertThatThrownBy { runBlocking { service.retryCreateMapping(retry) } }
        .isInstanceOf(IllegalStateException::class.java)
        .hasMessageContaining("Conflicting bed-assignment mapping")

      verify { telemetryClient.trackEvent("bedassignment-from-nomis-sync-duplicate", any(), null) }
      verify(exactly = 0) { telemetryClient.trackEvent("bedassignment-mapping-created-success", any(), null) }
    }

    @Test
    fun `propagates a persistent delete failure to the queue retry`() = runTest {
      coEvery { mappingApiService.deleteMappingByDpsId(deletedDpsId) } throws IllegalStateException("mapping delete failed")

      assertThatThrownBy { runBlocking { service.retryCreateMapping(retry) } }.hasMessage("mapping delete failed")

      verify {
        telemetryClient.trackEvent(
          "bedassignment-mapping-created-failure",
          retry.telemetryAttributes + ("error" to "mapping delete failed"),
          null,
        )
      }
      coVerify(exactly = 0) { mappingApiService.getMappingByNomisIdOrNull(any(), any()) }
    }

    @Test
    fun `propagates cancellation without reporting retry success or failure`() = runTest {
      coEvery { mappingApiService.deleteMappingByDpsId(deletedDpsId) } throws CancellationException("cancelled")

      assertThatThrownBy { runBlocking { service.retryCreateMapping(retry) } }.isInstanceOf(CancellationException::class.java)

      verify(exactly = 0) { telemetryClient.trackEvent("bedassignment-mapping-created-success", any(), any()) }
      verify(exactly = 0) { telemetryClient.trackEvent("bedassignment-mapping-created-failure", any(), any()) }
    }

    @Test
    fun `empty retry has no downstream effects`() = runTest {
      service.retryCreateMapping(InternalMessage(BedAssignmentMappingRetry(emptyList(), emptyList()), emptyMap()))

      coVerify(exactly = 0) { mappingApiService.deleteMappingByDpsId(any()) }
      coVerify(exactly = 0) { mappingApiService.getMappingByNomisIdOrNull(any(), any()) }
      coVerify(exactly = 0) { mappingApiService.createMapping(any(), any()) }
    }
  }
}
