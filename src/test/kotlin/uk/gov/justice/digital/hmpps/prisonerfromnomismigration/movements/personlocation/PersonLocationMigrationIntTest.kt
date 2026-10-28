package uk.gov.justice.digital.hmpps.prisonerfromnomismigration.movements.personlocation

import com.github.tomakehurst.wiremock.client.WireMock.equalToJson
import com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor
import com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor
import com.github.tomakehurst.wiremock.client.WireMock.putRequestedFor
import com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo
import com.github.tomakehurst.wiremock.client.WireMock.urlPathMatching
import kotlinx.coroutines.runBlocking
import org.assertj.core.api.Assertions.assertThat
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
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.http.HttpStatus.BAD_REQUEST
import org.springframework.http.HttpStatus.INTERNAL_SERVER_ERROR
import org.springframework.http.HttpStatus.NOT_FOUND
import org.springframework.http.MediaType
import org.springframework.test.web.reactive.server.returnResult
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.courtsentencing.CourtSentencingMappingApiMockServer
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.helper.MigrationResult
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.movements.court.CourtSchedulerMappingApiMockServer
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.movements.personlocation.PersonLocationDpsApiExtension.Companion.dpsPersonLocationServer
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.movements.personlocation.PersonLocationDpsApiMockServer.Companion.resyncResponse
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.movements.taps.TapMappingApiMockServer
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.movements.transfer.TransferScheduleMappingApiMockServer
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomismappings.model.PersonLocationBookingMappingDto
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomismappings.model.PersonLocationMovementMappingDto
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomismappings.model.PersonLocationsMappingDto
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomisprisoner.model.CodeDescription
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomisprisoner.model.MovementLocation
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomisprisoner.model.MovementLocation.Type.COURT
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomisprisoner.model.MovementLocation.Type.HOSPITAL
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomisprisoner.model.MovementLocation.Type.PRISON
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomisprisoner.model.NomisAudit
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomisprisoner.model.OffenderPersonLocationsResponse
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomisprisoner.model.PersonLocationMovement
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomisprisoner.model.PersonLocationMovement.DirectionCode.IN
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomisprisoner.model.PersonLocationMovement.DirectionCode.OUT
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomisprisoner.model.PersonLocationMovement.MovementType.ADM
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomisprisoner.model.PersonLocationMovement.MovementType.CRT
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomisprisoner.model.PersonLocationMovement.MovementType.REL
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomisprisoner.model.PersonLocationMovement.MovementType.TAP
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomisprisoner.model.PersonLocationMovement.MovementType.TRN
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.persistence.repository.MigrationHistoryRepository
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.personlocation.model.CodedReason
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.personlocation.model.Court
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.personlocation.model.Hospital
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.personlocation.model.Prison
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.personlocation.model.ResyncExternalMovementsRequest
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.personlocation.model.ResyncResponse
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.personlocation.model.SimpleLocation
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.personlocation.model.SyncCustodialSeries.Status.OPEN
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.personlocation.model.SyncExternalJourney
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.personlocation.model.SyncExternalJourney.Type.ADMISSION
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.personlocation.model.SyncExternalJourney.Type.TRANSFER
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.personlocation.model.SyncExternalMovement.Type.ARRIVAL
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.personlocation.model.SyncExternalMovement.Type.DEPARTURE
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.wiremock.MappingApiExtension
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.wiremock.NomisApiExtension
import java.time.Duration
import java.time.LocalDateTime
import java.util.UUID

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class PersonLocationMigrationIntTest(
  @Autowired private val migrationHistoryRepository: MigrationHistoryRepository,
  @Autowired private val personLocationNomisApi: PersonLocationNomisApiMockServer,
  @Autowired private val mappingApi: PersonLocationMappingApiMockServer,
  @Autowired private val tapMappingApi: TapMappingApiMockServer,
  @Autowired private val courtSchedulerMappingApi: CourtSchedulerMappingApiMockServer,
  @Autowired private val courtSentencingMappingApi: CourtSentencingMappingApiMockServer,
  @Autowired private val transferScheduleMappingApi: TransferScheduleMappingApiMockServer,
) : PersonLocationIntegrationTestBase() {

  private val nomisApi = NomisApiExtension.nomisApi
  private val dpsApi = dpsPersonLocationServer

  private val dpsCustodialSeriesId = UUID.randomUUID()
  private val dpsMovementId1 = UUID.randomUUID()
  private val dpsMovementId2 = UUID.randomUUID()
  private val dpsTransferScheduleId = UUID.randomUUID()
  private lateinit var migrationId: String

  // Because of how the ID ranges stubs work, we start with the unintuitive rootOffenderId=0 and prisonerNumber="A0000KT"
  private val prisonerNumber = "A0000KT"

  override fun resetTelemetryClient() {}

  internal fun setupMigrationTest() = runBlocking {
    migrationHistoryRepository.deleteAll()

    NomisApiExtension.resetAndDisableResetBeforeEach()
    MappingApiExtension.resetAndDisableResetBeforeEach()
    PersonLocationDpsApiExtension.resetAndDisableResetBeforeEach()

    tearDownTelemetryClient()
  }

  @AfterAll
  fun tearDownTelemetryClient() = reset(telemetryClient)

  private fun stubMigrationDependencies(entities: Int, pageSize: Long) {
    nomisApi.stubGetPrisonerIds(entities.toLong(), 1, prisonerNumber)
    nomisApi.stubGetAllPrisonersIdRangesAndInRange(pageSize = pageSize, totalElements = entities.toLong())
    ((0 until entities).map { index -> "A%04dKT".format(index) } + "A1234BC")
      .forEach { prisonerNumber ->
        personLocationNomisApi.stubGetOffenderPersonLocations(prisonerNumber)
        dpsApi.stubResyncPrisoner(
          personIdentifier = prisonerNumber,
          response = resyncResponse(
            dpsCustodialSeriesId = dpsCustodialSeriesId,
            movements = listOf(1 to dpsMovementId1, 2 to dpsMovementId2),
          ),
        )
        transferScheduleMappingApi.stubGetTransferSchedulerPrisonerMappingIds(
          prisonerNumber = prisonerNumber,
          nomisEventId = 54321L,
          dpsTransferScheduleId = dpsTransferScheduleId,
        )
      }
    mappingApi.stubGetPersonLocationMappings()
    mappingApi.stubCreatePersonLocationMappings()
  }

  @Nested
  @TestInstance(TestInstance.Lifecycle.PER_CLASS)
  inner class MigrateEntity {
    @BeforeAll
    fun setUp() {
      setupMigrationTest()

      stubMigrationDependencies(entities = 1, pageSize = 1)
      migrationId = performMigration()
    }

    @Test
    fun `should get NOMIS person locations`() {
      personLocationNomisApi.verify(getRequestedFor(urlPathEqualTo("/movements/$prisonerNumber/person-locations")))
    }

    @Test
    fun `should get existing mappings for the NOMIS bookings`() {
      mappingApi.verify(
        1,
        postRequestedFor(urlPathEqualTo("/mapping/person-location/nomis-booking-ids/get-list"))
          .withRequestBody(equalToJson("[12345]")),
      )
    }

    @Test
    fun `should only get schedule mappings for scheduled movement types`() {
      transferScheduleMappingApi.verify(1, getRequestedFor(urlPathEqualTo("/mapping/transfer-scheduler/$prisonerNumber/ids")))
      tapMappingApi.verify(0, getRequestedFor(urlPathEqualTo("/mapping/taps/$prisonerNumber/ids")))
      courtSchedulerMappingApi.verify(0, getRequestedFor(urlPathEqualTo("/mapping/court-scheduler/$prisonerNumber/ids")))
    }

    @Test
    fun `should populate DPS custodial series`() {
      PersonLocationDpsApiMockServer.getRequestBody<ResyncExternalMovementsRequest>(
        putRequestedFor(urlPathEqualTo("/resync/external-movements/$prisonerNumber")),
      ).apply {
        assertThat(custodialSeries).hasSize(1)
        with(custodialSeries[0]) {
          assertThat(created.at).isEqualTo(LocalDateTime.parse("2024-01-01T10:00:00"))
          assertThat(created.by).isEqualTo("SYS")
          assertThat(modified).isNull()
        }
        with(custodialSeries[0].custodialSeries) {
          assertThat(dpsId).isNull()
          assertThat(legacyBookingId).isEqualTo(12345L)
          assertThat(legacyBookingReference).isEqualTo("B12345")
          assertThat(status).isEqualTo(OPEN)
          assertThat(isActive).isTrue
          assertThat(openedAt).isEqualTo(LocalDateTime.parse("2024-01-01T10:00:00"))
          assertThat(closedAt).isNull()
          assertThat(notes).isNull()
        }
      }
    }

    @Test
    fun `should populate DPS movement with locations`() {
      PersonLocationDpsApiMockServer.getRequestBody<ResyncExternalMovementsRequest>(
        putRequestedFor(urlPathEqualTo("/resync/external-movements/$prisonerNumber")),
      ).apply {
        with(custodialSeries[0].movements[0]) {
          assertThat(created.at).isEqualTo(LocalDateTime.parse("2024-01-01T10:00:00"))
          assertThat(created.by).isEqualTo("SOME_USER")
          assertThat(modified!!.at).isEqualTo(LocalDateTime.parse("2024-01-01T11:00:00"))
          assertThat(modified.by).isEqualTo("SYS")
        }
        with(custodialSeries[0].movements[0].movement) {
          assertThat(dpsId).isNull()
          assertThat(dpsCustodySeriesId).isNull()
          assertThat(legacyBookingId).isEqualTo(12345L)
          assertThat(legacySequenceNumber).isEqualTo(1)
          assertThat(type).isEqualTo(ARRIVAL)
          assertThat(occurredAt).isEqualTo(LocalDateTime.parse("2024-01-01T10:00:00"))
          assertThat(reason).isEqualTo(CodedReason(description = "Unconvicted Remand", reasonType = "CodedReason", code = "N"))
          assertThat(from).isEqualTo(Court(description = "Leeds Court", locationType = "Court", code = "LEEDCC"))
          assertThat(to).isEqualTo(Prison(description = "Leeds (HMP)", locationType = "Prison", code = "LEI"))
          assertThat(journey).isEqualTo(SyncExternalJourney(type = ADMISSION, scheduleReference = null))
          assertThat(notes).isNull()
        }
      }
    }

    @Test
    fun `should populate DPS movement with schedule reference`() {
      PersonLocationDpsApiMockServer.getRequestBody<ResyncExternalMovementsRequest>(
        putRequestedFor(urlPathEqualTo("/resync/external-movements/$prisonerNumber")),
      ).apply {
        with(custodialSeries[0].movements[1].movement) {
          assertThat(legacySequenceNumber).isEqualTo(2)
          assertThat(type).isEqualTo(DEPARTURE)
          assertThat(occurredAt).isEqualTo(LocalDateTime.parse("2024-01-11T10:00:00"))
          assertThat(reason).isEqualTo(CodedReason(description = "Normal Transfer", reasonType = "CodedReason", code = "NOTR"))
          assertThat(from).isEqualTo(Prison(description = "Leeds (HMP)", locationType = "Prison", code = "LEI"))
          assertThat(to!!.description).isEqualTo("Unknown destination")
          assertThat(to.locationType).isEqualTo("SimpleLocation")
          assertThat(journey).isEqualTo(SyncExternalJourney(type = TRANSFER, scheduleReference = "urn:transfer-scheduler:transfer:$dpsTransferScheduleId"))
          assertThat(notes).isEqualTo("Transfer comment")
        }
      }
    }

    @Test
    fun `should create mappings`() {
      PersonLocationMappingApiMockServer.getRequestBody<PersonLocationsMappingDto>(
        putRequestedFor(urlPathEqualTo("/mapping/person-location/migrate")),
      ).apply {
        assertThat(offenderNo).isEqualTo(prisonerNumber)
        assertThat(migrationId).isEqualTo(this@PersonLocationMigrationIntTest.migrationId)
        assertThat(bookings).hasSize(1)
        with(bookings[0]) {
          assertThat(bookingId).isEqualTo(12345L)
          assertThat(dpsCustodialSeriesId).isEqualTo(this@PersonLocationMigrationIntTest.dpsCustodialSeriesId)
          assertThat(movements).containsExactly(
            PersonLocationMovementMappingDto(nomisMovementSeq = 1, dpsExternalMovementId = dpsMovementId1),
            PersonLocationMovementMappingDto(nomisMovementSeq = 2, dpsExternalMovementId = dpsMovementId2),
          )
        }
      }
    }

    @Test
    fun `will publish telemetry`() {
      verify(telemetryClient).trackEvent(
        eq("person-location-migration-entity-migrated"),
        check {
          assertThat(it["offenderNo"]).isEqualTo(prisonerNumber)
          assertThat(it["migrationId"]).isEqualTo(migrationId)
        },
        isNull(),
      )
    }
  }

  @Nested
  @TestInstance(TestInstance.Lifecycle.PER_CLASS)
  inner class MigrateEntityScheduleReferencesAndMissingLocations {
    private val dpsTapOccurrenceId = UUID.randomUUID()
    private val dpsCourtAppearanceId = UUID.randomUUID()
    private val dpsSentencingCourtAppearanceId = UUID.randomUUID()

    @BeforeAll
    fun setUp() {
      setupMigrationTest()

      stubMigrationDependencies(entities = 1, pageSize = 1)
      personLocationNomisApi.stubGetOffenderPersonLocations(
        offenderNo = "A1234BC",
        response = offenderPersonLocationsResponse().let { response ->
          response.copy(
            bookings = listOf(
              response.bookings[0].copy(
                movements = listOf(
                  movement(bookingId = 12345L, sequence = 1, movementType = ADM, directionCode = IN, from = null),
                  movement(bookingId = 12345L, sequence = 2, movementType = TAP, scheduleId = 101),
                  movement(bookingId = 12345L, sequence = 3, movementType = CRT, scheduleId = 102),
                  movement(bookingId = 12345L, sequence = 4, movementType = TRN, scheduleId = 103),
                  movement(bookingId = 12345L, sequence = 5, movementType = REL),
                  movement(bookingId = 12345L, sequence = 6, movementType = CRT, scheduleId = 104),
                  movement(bookingId = 12345L, sequence = 7, movementType = REL, from = MovementLocation(type = PRISON, description = "Unknown", code = "UNKNWN")),
                  movement(bookingId = 12345L, sequence = 8, movementType = REL, from = MovementLocation(type = COURT, description = "Unknown", code = "UNKNCT")),
                  movement(bookingId = 12345L, sequence = 9, movementType = REL, from = MovementLocation(type = HOSPITAL, description = "Northern General Hospital", code = "NGENHO")),
                ),
              ),
            ),
          )
        },
      )
      courtSentencingMappingApi.stubGetAllCourtAppearanceByNomisIds(
        nomisCourtAppearanceIds = listOf(104, 102),
        dpsCourtAppearanceIds = listOf(dpsSentencingCourtAppearanceId.toString(), UUID.randomUUID().toString()),
      )
      tapMappingApi.stubGetTapPrisonerMappingIds(prisonerNumber = "A1234BC", nomisScheduleOutEventId = 101, dpsOccurrenceId = dpsTapOccurrenceId)
      courtSchedulerMappingApi.stubGetCourtSchedulerPrisonerMappingIds(prisonerNumber = "A1234BC", nomisEventId = 102, dpsCourtAppearanceId = dpsCourtAppearanceId)
      transferScheduleMappingApi.stubGetTransferSchedulerPrisonerMappingIds(prisonerNumber = "A1234BC", dpsTransferScheduleId = null)

      performMigration("A1234BC")
    }

    private fun movement(
      bookingId: Long,
      sequence: Int,
      movementType: PersonLocationMovement.MovementType,
      directionCode: PersonLocationMovement.DirectionCode = OUT,
      scheduleId: Long? = null,
      from: MovementLocation? = MovementLocation(type = PRISON, description = "Leeds (HMP)", code = "LEI"),
    ) = PersonLocationMovement(
      bookingId = bookingId,
      sequence = sequence,
      movementType = movementType,
      directionCode = directionCode,
      movementTime = LocalDateTime.parse("2024-01-01T10:00:00").plusDays(sequence.toLong()),
      movementReason = CodeDescription(code = "X", description = "Some reason"),
      audit = NomisAudit(createDatetime = LocalDateTime.parse("2024-01-01T10:00:00"), createUsername = "SOME_USER"),
      from = from,
      scheduleId = scheduleId,
    )

    private fun dpsMovements() = PersonLocationDpsApiMockServer.getRequestBody<ResyncExternalMovementsRequest>(
      putRequestedFor(urlPathEqualTo("/resync/external-movements/A1234BC")),
    ).custodialSeries[0].movements.map { it.movement }

    @Test
    fun `should default missing from location on inbound movements`() {
      assertThat(dpsMovements()[0].from.description).isEqualTo("Outside")
      assertThat(dpsMovements()[0].from.locationType).isEqualTo("SimpleLocation")
    }

    @Test
    fun `should populate schedule references from the DPS schedule mappings`() {
      assertThat(dpsMovements().map { it.journey.scheduleReference }).containsExactly(
        null,
        "urn:temporary-absence-scheduler:temporary-absence:$dpsTapOccurrenceId",
        "urn:court-appearance-scheduler:court-appearance:$dpsCourtAppearanceId",
        null,
        null,
        "urn:remand-and-sentencing:court-appearance:$dpsSentencingCourtAppearanceId",
        null,
        null,
        null,
      )
    }

    @Test
    fun `should send hospital locations as a secure hospital`() {
      assertThat(dpsMovements()[8].from).isEqualTo(
        Hospital(description = "Northern General Hospital", locationType = "Hospital", code = "NGENHO"),
      )
    }

    @Test
    fun `should send unknown prison and court locations as an UNKNOWN simple location`() {
      with(dpsMovements()[6].from as SimpleLocation) {
        assertThat(description).isEqualTo("UNKNOWN")
        assertThat(locationType).isEqualTo("SimpleLocation")
      }
      with(dpsMovements()[7].from as SimpleLocation) {
        assertThat(description).isEqualTo("UNKNOWN")
        assertThat(locationType).isEqualTo("SimpleLocation")
      }
    }

    @Test
    fun `should get each type of schedule mappings once for the prisoner`() {
      tapMappingApi.verify(1, getRequestedFor(urlPathEqualTo("/mapping/taps/A1234BC/ids")))
      courtSchedulerMappingApi.verify(1, getRequestedFor(urlPathEqualTo("/mapping/court-scheduler/A1234BC/ids")))
      transferScheduleMappingApi.verify(1, getRequestedFor(urlPathEqualTo("/mapping/transfer-scheduler/A1234BC/ids")))
      courtSentencingMappingApi.verify(1, postRequestedFor(urlPathEqualTo("/mapping/court-sentencing/court-appearances/nomis-court-appearance-ids/get-list")))
    }
  }

  @Nested
  @TestInstance(TestInstance.Lifecycle.PER_CLASS)
  inner class MigrateEntityWithExistingMappings {
    private val existingDpsCustodialSeriesId = UUID.randomUUID()
    private val existingDpsMovementId = UUID.randomUUID()

    @BeforeAll
    fun setUp() {
      setupMigrationTest()

      stubMigrationDependencies(entities = 1, pageSize = 1)
      // Only the first movement has a mapping
      mappingApi.stubGetPersonLocationMappings(
        response = listOf(
          PersonLocationBookingMappingDto(
            bookingId = 12345L,
            dpsCustodialSeriesId = existingDpsCustodialSeriesId,
            movements = listOf(PersonLocationMovementMappingDto(nomisMovementSeq = 1, dpsExternalMovementId = existingDpsMovementId)),
          ),
        ),
      )
      migrationId = performMigration()
    }

    @Test
    fun `should populate known DPS IDs`() {
      PersonLocationDpsApiMockServer.getRequestBody<ResyncExternalMovementsRequest>(
        putRequestedFor(urlPathEqualTo("/resync/external-movements/$prisonerNumber")),
      ).apply {
        assertThat(custodialSeries[0].custodialSeries.dpsId).isEqualTo(existingDpsCustodialSeriesId)
        with(custodialSeries[0].movements[0].movement) {
          assertThat(dpsId).isEqualTo(existingDpsMovementId)
          assertThat(dpsCustodySeriesId).isEqualTo(existingDpsCustodialSeriesId)
        }
        with(custodialSeries[0].movements[1].movement) {
          assertThat(dpsId).isNull()
          assertThat(dpsCustodySeriesId).isEqualTo(existingDpsCustodialSeriesId)
        }
      }
    }

    @Test
    fun `should create mappings from the DPS response`() {
      PersonLocationMappingApiMockServer.getRequestBody<PersonLocationsMappingDto>(
        putRequestedFor(urlPathEqualTo("/mapping/person-location/migrate")),
      ).apply {
        assertThat(bookings[0].dpsCustodialSeriesId).isEqualTo(dpsCustodialSeriesId)
        assertThat(bookings[0].movements.map { it.dpsExternalMovementId }).containsExactly(dpsMovementId1, dpsMovementId2)
      }
    }
  }

  @Nested
  @TestInstance(TestInstance.Lifecycle.PER_CLASS)
  inner class FullMigration {
    @BeforeAll
    fun setUp() {
      setupMigrationTest()

      stubMigrationDependencies(entities = 3, pageSize = 2)
      performMigration()
    }

    @Test
    fun `should call DPS resync API for all prisoners`() {
      dpsApi.verify(putRequestedFor(urlPathEqualTo("/resync/external-movements/A0000KT")))
      dpsApi.verify(putRequestedFor(urlPathEqualTo("/resync/external-movements/A0001KT")))
      dpsApi.verify(putRequestedFor(urlPathEqualTo("/resync/external-movements/A0002KT")))
    }

    @Test
    fun `should create mappings for all prisoners`() {
      mappingApi.verify(3, putRequestedFor(urlPathEqualTo("/mapping/person-location/migrate")))
    }

    @Test
    fun `will publish telemetry for each prisoner`() {
      verify(telemetryClient, times(3)).trackEvent(
        eq("person-location-migration-entity-migrated"),
        any(),
        isNull(),
      )
    }
  }

  @Nested
  @TestInstance(TestInstance.Lifecycle.PER_CLASS)
  inner class MigrateSinglePrisoner {
    @BeforeAll
    fun setUp() {
      setupMigrationTest()

      stubMigrationDependencies(entities = 100, pageSize = 10)
      performMigration("A1234BC")
    }

    @Test
    fun `should call DPS resync`() {
      dpsApi.verify(1, putRequestedFor(urlPathMatching("/resync/external-movements/.*")))
      dpsApi.verify(putRequestedFor(urlPathEqualTo("/resync/external-movements/A1234BC")))
    }

    @Test
    fun `should create mappings`() {
      mappingApi.verify(1, putRequestedFor(urlPathEqualTo("/mapping/person-location/migrate")))
    }

    @Test
    fun `will publish telemetry`() {
      verify(telemetryClient).trackEvent(
        eq("person-location-migration-entity-migrated"),
        check {
          assertThat(it["offenderNo"]).isEqualTo("A1234BC")
        },
        isNull(),
      )
    }
  }

  @Nested
  @TestInstance(TestInstance.Lifecycle.PER_CLASS)
  inner class MigrationFailure {
    @BeforeAll
    fun setUp() {
      setupMigrationTest()

      stubMigrationDependencies(entities = 1, pageSize = 1)
      // The call to DPS fails
      dpsApi.stubResyncPrisoner(personIdentifier = prisonerNumber, status = BAD_REQUEST)
      migrationId = performMigration()
    }

    @Test
    fun `should call DPS resync API`() {
      dpsApi.verify(putRequestedFor(urlPathEqualTo("/resync/external-movements/$prisonerNumber")))
    }

    @Test
    fun `should NOT create mappings`() {
      mappingApi.verify(0, putRequestedFor(urlPathEqualTo("/mapping/person-location/migrate")))
    }

    @Test
    fun `will publish fail telemetry`() {
      verify(telemetryClient).trackEvent(
        eq("person-location-migration-entity-failed"),
        check {
          assertThat(it["offenderNo"]).isEqualTo(prisonerNumber)
          assertThat(it["migrationId"]).isEqualTo(migrationId)
          assertThat(it["reason"]).contains("400 Bad Request")
        },
        isNull(),
      )
    }
  }

  @Nested
  @TestInstance(TestInstance.Lifecycle.PER_CLASS)
  inner class MigrationFailureMissingFromLocation {
    @BeforeAll
    fun setUp() {
      setupMigrationTest()

      stubMigrationDependencies(entities = 1, pageSize = 1)
      // An outbound movement has no from location
      personLocationNomisApi.stubGetOffenderPersonLocations(
        offenderNo = prisonerNumber,
        response = offenderPersonLocationsResponse().let { response ->
          response.copy(
            bookings = listOf(
              response.bookings[0].copy(
                movements = listOf(response.bookings[0].movements[1].copy(from = null)),
              ),
            ),
          )
        },
      )
      migrationId = performMigration()
    }

    @Test
    fun `should NOT call DPS resync API`() {
      dpsApi.verify(0, putRequestedFor(urlPathEqualTo("/resync/external-movements/$prisonerNumber")))
    }

    @Test
    fun `should NOT create mappings`() {
      mappingApi.verify(0, putRequestedFor(urlPathEqualTo("/mapping/person-location/migrate")))
    }

    @Test
    fun `will publish fail telemetry`() {
      verify(telemetryClient).trackEvent(
        eq("person-location-migration-entity-failed"),
        check {
          assertThat(it["offenderNo"]).isEqualTo(prisonerNumber)
          assertThat(it["migrationId"]).isEqualTo(migrationId)
          assertThat(it["reason"]).isEqualTo("Outbound movement 12345/2 has no from location")
        },
        isNull(),
      )
    }
  }

  @Nested
  @TestInstance(TestInstance.Lifecycle.PER_CLASS)
  inner class MigrationFailureGetExistingMappings {
    @BeforeAll
    fun setUp() {
      setupMigrationTest()

      stubMigrationDependencies(entities = 1, pageSize = 1)
      // The call to get existing mappings fails
      mappingApi.stubGetPersonLocationMappings(status = INTERNAL_SERVER_ERROR)
      migrationId = performMigration()
    }

    @Test
    fun `should NOT call DPS resync API`() {
      dpsApi.verify(0, putRequestedFor(urlPathEqualTo("/resync/external-movements/$prisonerNumber")))
    }

    @Test
    fun `should NOT create mappings`() {
      mappingApi.verify(0, putRequestedFor(urlPathEqualTo("/mapping/person-location/migrate")))
    }

    @Test
    fun `will publish fail telemetry`() {
      verify(telemetryClient).trackEvent(
        eq("person-location-migration-entity-failed"),
        check {
          assertThat(it["offenderNo"]).isEqualTo(prisonerNumber)
          assertThat(it["migrationId"]).isEqualTo(migrationId)
          assertThat(it["reason"]).contains("500 Internal Server Error")
        },
        isNull(),
      )
    }
  }

  @Nested
  @TestInstance(TestInstance.Lifecycle.PER_CLASS)
  inner class MappingErrorRecovery {
    @BeforeAll
    fun setUp() {
      setupMigrationTest()

      stubMigrationDependencies(entities = 1, pageSize = 1)
      // The call to create mappings fails then succeeds on a retry
      mappingApi.stubCreatePersonLocationMappingsFailureFollowedBySuccess()
      migrationId = performMigration()
      await atMost Duration.ofSeconds(10) untilAsserted {
        mappingApi.verify(2, putRequestedFor(urlPathEqualTo("/mapping/person-location/migrate")))
        verify(telemetryClient).trackEvent(
          eq("person-location-migration-entity-migrated"),
          any(),
          isNull(),
        )
      }
    }

    @Test
    fun `should call DPS resync API once`() {
      dpsApi.verify(1, putRequestedFor(urlPathEqualTo("/resync/external-movements/$prisonerNumber")))
    }

    @Test
    fun `should attempt to create mappings twice`() {
      mappingApi.verify(2, putRequestedFor(urlPathEqualTo("/mapping/person-location/migrate")))
    }

    @Test
    fun `will publish telemetry`() {
      verify(telemetryClient).trackEvent(
        eq("person-location-migration-entity-migrated"),
        check {
          assertThat(it["offenderNo"]).isEqualTo(prisonerNumber)
          assertThat(it["migrationId"]).isEqualTo(migrationId)
        },
        isNull(),
      )
    }
  }

  @Nested
  @TestInstance(TestInstance.Lifecycle.PER_CLASS)
  inner class IgnorePrisonerWithNoMovements {
    @BeforeAll
    fun setUp() {
      setupMigrationTest()

      stubMigrationDependencies(entities = 1, pageSize = 1)
      // The prisoner has no person locations
      personLocationNomisApi.stubGetOffenderPersonLocations(
        offenderNo = prisonerNumber,
        response = OffenderPersonLocationsResponse(bookings = listOf()),
      )
      migrationId = performMigration()
    }

    @Test
    fun `should NOT get existing mappings`() {
      mappingApi.verify(0, postRequestedFor(urlPathEqualTo("/mapping/person-location/nomis-booking-ids/get-list")))
    }

    @Test
    fun `should NOT call DPS resync API`() {
      dpsApi.verify(0, putRequestedFor(urlPathEqualTo("/resync/external-movements/$prisonerNumber")))
    }

    @Test
    fun `should NOT create mappings`() {
      mappingApi.verify(0, putRequestedFor(urlPathEqualTo("/mapping/person-location/migrate")))
    }

    @Test
    fun `should publish ignore telemetry`() {
      verify(telemetryClient).trackEvent(
        eq("person-location-migration-entity-ignored"),
        check {
          assertThat(it["offenderNo"]).isEqualTo(prisonerNumber)
          assertThat(it["migrationId"]).isEqualTo(migrationId)
          assertThat(it["reason"]).isEqualTo("The offender has no person locations")
        },
        isNull(),
      )
    }
  }

  @Nested
  @TestInstance(TestInstance.Lifecycle.PER_CLASS)
  inner class IgnorePrisonerNotFound {
    @BeforeAll
    fun setUp() {
      setupMigrationTest()

      stubMigrationDependencies(entities = 1, pageSize = 1)
      personLocationNomisApi.stubGetOffenderPersonLocations(offenderNo = prisonerNumber, status = NOT_FOUND)
      migrationId = performMigration()
    }

    @Test
    fun `should NOT call DPS resync API`() {
      dpsApi.verify(0, putRequestedFor(urlPathEqualTo("/resync/external-movements/$prisonerNumber")))
    }

    @Test
    fun `should publish ignore telemetry`() {
      verify(telemetryClient).trackEvent(
        eq("person-location-migration-entity-ignored"),
        check {
          assertThat(it["offenderNo"]).isEqualTo(prisonerNumber)
          assertThat(it["reason"]).isEqualTo("The offender has no person locations")
        },
        isNull(),
      )
    }
  }

  @Nested
  @TestInstance(TestInstance.Lifecycle.PER_CLASS)
  inner class Resync {

    @Nested
    @TestInstance(TestInstance.Lifecycle.PER_CLASS)
    inner class HappyPath {
      @BeforeAll
      fun setUp() {
        setupMigrationTest()

        stubMigrationDependencies(entities = 1, pageSize = 1)
        mappingApi.stubGetPersonLocationMappings(
          response = listOf(
            PersonLocationBookingMappingDto(
              bookingId = 12345L,
              dpsCustodialSeriesId = dpsCustodialSeriesId,
              movements = listOf(
                PersonLocationMovementMappingDto(nomisMovementSeq = 1, dpsExternalMovementId = dpsMovementId1),
                PersonLocationMovementMappingDto(nomisMovementSeq = 2, dpsExternalMovementId = dpsMovementId2),
              ),
            ),
          ),
        )
        repairPrisonerOk(prisonerNumber)
      }

      @Test
      fun `should send existing DPS IDs`() {
        PersonLocationDpsApiMockServer.getRequestBody<ResyncExternalMovementsRequest>(
          putRequestedFor(urlPathEqualTo("/resync/external-movements/$prisonerNumber")),
        ).apply {
          assertThat(custodialSeries[0].custodialSeries.dpsId).isEqualTo(dpsCustodialSeriesId)
          assertThat(custodialSeries[0].movements.map { it.movement.dpsId }).containsExactly(dpsMovementId1, dpsMovementId2)
          assertThat(custodialSeries[0].movements.map { it.movement.dpsCustodySeriesId }).containsOnly(dpsCustodialSeriesId)
        }
      }

      @Test
      fun `should call DPS resync API`() {
        PersonLocationDpsApiMockServer.getRequestBody<ResyncExternalMovementsRequest>(
          putRequestedFor(urlPathEqualTo("/resync/external-movements/$prisonerNumber")),
        ).apply {
          assertThat(custodialSeries).hasSize(1)
          assertThat(custodialSeries[0].custodialSeries.legacyBookingId).isEqualTo(12345L)
          assertThat(custodialSeries[0].movements.map { it.movement.legacySequenceNumber }).containsExactly(1, 2)
          assertThat(custodialSeries[0].movements[1].movement.journey)
            .isEqualTo(SyncExternalJourney(type = TRANSFER, scheduleReference = "urn:transfer-scheduler:transfer:$dpsTransferScheduleId"))
        }
      }

      @Test
      fun `should create mappings`() {
        PersonLocationMappingApiMockServer.getRequestBody<PersonLocationsMappingDto>(
          putRequestedFor(urlPathEqualTo("/mapping/person-location/migrate")),
        ).apply {
          assertThat(offenderNo).isEqualTo(prisonerNumber)
          assertThat(migrationId).isNotEmpty
          assertThat(bookings).hasSize(1)
          with(bookings[0]) {
            assertThat(bookingId).isEqualTo(12345L)
            assertThat(dpsCustodialSeriesId).isEqualTo(this@PersonLocationMigrationIntTest.dpsCustodialSeriesId)
            assertThat(movements).containsExactly(
              PersonLocationMovementMappingDto(nomisMovementSeq = 1, dpsExternalMovementId = dpsMovementId1),
              PersonLocationMovementMappingDto(nomisMovementSeq = 2, dpsExternalMovementId = dpsMovementId2),
            )
          }
        }
      }

      @Test
      fun `should publish telemetry`() {
        verify(telemetryClient).trackEvent(
          eq("person-location-migration-entity-repair-requested"),
          check {
            assertThat(it["offenderNo"]).isEqualTo(prisonerNumber)
          },
          isNull(),
        )
        verify(telemetryClient).trackEvent(
          eq("person-location-migration-entity-migrated"),
          check {
            assertThat(it["offenderNo"]).isEqualTo(prisonerNumber)
          },
          isNull(),
        )
      }
    }

    @Nested
    @TestInstance(TestInstance.Lifecycle.PER_CLASS)
    inner class PrisonerWithNoMovements {
      @BeforeAll
      fun setUp() {
        setupMigrationTest()

        stubMigrationDependencies(entities = 1, pageSize = 1)
        personLocationNomisApi.stubGetOffenderPersonLocations(
          offenderNo = prisonerNumber,
          response = OffenderPersonLocationsResponse(bookings = listOf()),
        )
        dpsApi.stubResyncPrisoner(personIdentifier = prisonerNumber, response = ResyncResponse(custodialSeries = listOf()))
        repairPrisonerOk(prisonerNumber)
      }

      @Test
      fun `should NOT get existing mappings`() {
        mappingApi.verify(0, postRequestedFor(urlPathEqualTo("/mapping/person-location/nomis-booking-ids/get-list")))
      }

      @Test
      fun `should call DPS resync API with no custodial series`() {
        PersonLocationDpsApiMockServer.getRequestBody<ResyncExternalMovementsRequest>(
          putRequestedFor(urlPathEqualTo("/resync/external-movements/$prisonerNumber")),
        ).apply {
          assertThat(custodialSeries).isEmpty()
        }
      }

      @Test
      fun `should update mappings`() {
        PersonLocationMappingApiMockServer.getRequestBody<PersonLocationsMappingDto>(
          putRequestedFor(urlPathEqualTo("/mapping/person-location/migrate")),
        ).apply {
          assertThat(offenderNo).isEqualTo(prisonerNumber)
          assertThat(bookings).isEmpty()
        }
      }

      @Test
      fun `should publish telemetry`() {
        verify(telemetryClient).trackEvent(
          eq("person-location-migration-entity-repair-requested"),
          check {
            assertThat(it["offenderNo"]).isEqualTo(prisonerNumber)
          },
          isNull(),
        )
        verify(telemetryClient).trackEvent(
          eq("person-location-migration-entity-migrated"),
          check {
            assertThat(it["offenderNo"]).isEqualTo(prisonerNumber)
          },
          isNull(),
        )
      }
    }

    @Nested
    @TestInstance(TestInstance.Lifecycle.PER_CLASS)
    inner class PrisonerNotFound {
      @BeforeAll
      fun setUp() {
        setupMigrationTest()

        stubMigrationDependencies(entities = 1, pageSize = 1)
        personLocationNomisApi.stubGetOffenderPersonLocations(offenderNo = prisonerNumber, status = NOT_FOUND)
        dpsApi.stubResyncPrisoner(personIdentifier = prisonerNumber, response = ResyncResponse(custodialSeries = listOf()))
        repairPrisonerOk(prisonerNumber)
      }

      @Test
      fun `should NOT get existing mappings`() {
        mappingApi.verify(0, postRequestedFor(urlPathEqualTo("/mapping/person-location/nomis-booking-ids/get-list")))
      }

      @Test
      fun `should call DPS resync API with no custodial series`() {
        PersonLocationDpsApiMockServer.getRequestBody<ResyncExternalMovementsRequest>(
          putRequestedFor(urlPathEqualTo("/resync/external-movements/$prisonerNumber")),
        ).apply {
          assertThat(custodialSeries).isEmpty()
        }
      }

      @Test
      fun `should update mappings`() {
        PersonLocationMappingApiMockServer.getRequestBody<PersonLocationsMappingDto>(
          putRequestedFor(urlPathEqualTo("/mapping/person-location/migrate")),
        ).apply {
          assertThat(offenderNo).isEqualTo(prisonerNumber)
          assertThat(bookings).isEmpty()
        }
      }

      @Test
      fun `should publish telemetry`() {
        verify(telemetryClient).trackEvent(
          eq("person-location-migration-entity-migrated"),
          check {
            assertThat(it["offenderNo"]).isEqualTo(prisonerNumber)
          },
          isNull(),
        )
      }
    }

    @Nested
    @TestInstance(TestInstance.Lifecycle.PER_CLASS)
    inner class ResyncFailure {
      @BeforeAll
      fun setUp() {
        setupMigrationTest()

        stubMigrationDependencies(entities = 1, pageSize = 1)
        dpsApi.stubResyncPrisoner(personIdentifier = prisonerNumber, status = BAD_REQUEST)
        repairPrisoner(prisonerNumber).expectStatus().is5xxServerError
      }

      @Test
      fun `should NOT create mappings`() {
        mappingApi.verify(0, putRequestedFor(urlPathEqualTo("/mapping/person-location/migrate")))
      }

      @Test
      fun `will publish fail telemetry`() {
        verify(telemetryClient).trackEvent(
          eq("person-location-migration-entity-failed"),
          check {
            assertThat(it["offenderNo"]).isEqualTo(prisonerNumber)
            assertThat(it["reason"]).contains("400 Bad Request")
          },
          isNull(),
        )
      }
    }

    @Nested
    @TestInstance(TestInstance.Lifecycle.PER_CLASS)
    inner class MappingErrorRecovery {
      @BeforeAll
      fun setUp() {
        setupMigrationTest()

        stubMigrationDependencies(entities = 1, pageSize = 1)
        // The call to create mappings fails then succeeds on a retry
        mappingApi.stubCreatePersonLocationMappingsFailureFollowedBySuccess()
        repairPrisonerOk(prisonerNumber)
        await atMost Duration.ofSeconds(10) untilAsserted {
          mappingApi.verify(2, putRequestedFor(urlPathEqualTo("/mapping/person-location/migrate")))
          verify(telemetryClient).trackEvent(
            eq("person-location-migration-entity-migrated"),
            any(),
            isNull(),
          )
        }
      }

      @Test
      fun `should call DPS resync API once`() {
        dpsApi.verify(1, putRequestedFor(urlPathEqualTo("/resync/external-movements/$prisonerNumber")))
      }

      @Test
      fun `should attempt to create mappings twice`() {
        mappingApi.verify(2, putRequestedFor(urlPathEqualTo("/mapping/person-location/migrate")))
      }

      @Test
      fun `will publish telemetry`() {
        verify(telemetryClient).trackEvent(
          eq("person-location-migration-entity-repair-requested"),
          check {
            assertThat(it["offenderNo"]).isEqualTo(prisonerNumber)
          },
          isNull(),
        )
        verify(telemetryClient).trackEvent(
          eq("person-location-migration-entity-migrated"),
          check {
            assertThat(it["offenderNo"]).isEqualTo(prisonerNumber)
          },
          isNull(),
        )
      }
    }

    @Nested
    @TestInstance(TestInstance.Lifecycle.PER_CLASS)
    inner class Security {
      @Test
      fun `access allowed with repair movements role`() {
        setupMigrationTest()
        stubMigrationDependencies(entities = 1, pageSize = 1)

        repairPrisoner(prisonerNumber, role = "ROLE_PRISONER_FROM_NOMIS__REPAIR_MOVEMENTS__RW").expectStatus().isOk
      }

      @Test
      fun `access forbidden when no role`() {
        webTestClient.put().uri("/migrate/person-location/repair/$prisonerNumber")
          .headers(setAuthorisation(roles = listOf()))
          .exchange()
          .expectStatus().isForbidden
      }

      @Test
      fun `access forbidden with wrong role`() {
        webTestClient.put().uri("/migrate/person-location/repair/$prisonerNumber")
          .headers(setAuthorisation(roles = listOf("ROLE_BANANAS")))
          .exchange()
          .expectStatus().isForbidden
      }

      @Test
      fun `access unauthorised with no auth token`() {
        webTestClient.put().uri("/migrate/person-location/repair/$prisonerNumber")
          .exchange()
          .expectStatus().isUnauthorized
      }
    }

    private fun repairPrisoner(prisonerNumber: String, role: String = "ROLE_PRISONER_FROM_NOMIS__MIGRATION__RW") = webTestClient.put()
      .uri("/migrate/person-location/repair/$prisonerNumber")
      .headers(setAuthorisation(roles = listOf(role)))
      .exchange()

    private fun repairPrisonerOk(prisonerNumber: String) = repairPrisoner(prisonerNumber).expectStatus().isOk
  }

  @Nested
  inner class Security {
    @Test
    fun `access forbidden when no role`() {
      webTestClient.post().uri("/migrate/person-location")
        .headers(setAuthorisation(roles = listOf()))
        .contentType(MediaType.APPLICATION_JSON)
        .bodyValue(PersonLocationMigrationFilter())
        .exchange()
        .expectStatus().isForbidden
    }

    @Test
    fun `access forbidden with wrong role`() {
      webTestClient.post().uri("/migrate/person-location")
        .headers(setAuthorisation(roles = listOf("ROLE_BANANAS")))
        .contentType(MediaType.APPLICATION_JSON)
        .bodyValue(PersonLocationMigrationFilter())
        .exchange()
        .expectStatus().isForbidden
    }

    @Test
    fun `access unauthorised with no auth token`() {
      webTestClient.post().uri("/migrate/person-location")
        .contentType(MediaType.APPLICATION_JSON)
        .bodyValue(PersonLocationMigrationFilter())
        .exchange()
        .expectStatus().isUnauthorized
    }
  }

  private fun performMigration(prisonerNumber: String? = null): String = webTestClient.post()
    .uri("/migrate/person-location")
    .headers(setAuthorisation(roles = listOf("ROLE_PRISONER_FROM_NOMIS__MIGRATION__RW")))
    .contentType(MediaType.APPLICATION_JSON)
    .bodyValue(PersonLocationMigrationFilter(prisonerNumber = prisonerNumber))
    .exchange()
    .expectStatus().isAccepted
    .returnResult<MigrationResult>().responseBody.blockFirst()!!
    .migrationId
    .also {
      waitUntilCompleted()
    }

  private fun waitUntilCompleted() = await atMost Duration.ofSeconds(10) untilAsserted {
    verify(telemetryClient).trackEvent(
      eq("person-location-migration-completed"),
      any(),
      isNull(),
    )
  }
}
