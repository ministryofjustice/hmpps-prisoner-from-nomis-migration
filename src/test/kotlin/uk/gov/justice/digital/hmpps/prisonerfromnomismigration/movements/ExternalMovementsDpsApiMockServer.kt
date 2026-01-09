package uk.gov.justice.digital.hmpps.prisonerfromnomismigration.movements

import com.fasterxml.jackson.databind.ObjectMapper
import com.github.tomakehurst.wiremock.WireMockServer
import com.github.tomakehurst.wiremock.client.WireMock.aResponse
import com.github.tomakehurst.wiremock.client.WireMock.delete
import com.github.tomakehurst.wiremock.client.WireMock.get
import com.github.tomakehurst.wiremock.client.WireMock.put
import com.github.tomakehurst.wiremock.matching.RequestPatternBuilder
import org.junit.jupiter.api.extension.AfterAllCallback
import org.junit.jupiter.api.extension.BeforeAllCallback
import org.junit.jupiter.api.extension.BeforeEachCallback
import org.junit.jupiter.api.extension.ExtensionContext
import org.springframework.stereotype.Component
import org.springframework.test.context.junit.jupiter.SpringExtension
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.movements.ExternalMovementsDpsApiExtension.Companion.dpsExtMovementsServer
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.movements.model.Location
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.movements.model.MigrateTapAuthorisation
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.movements.model.MigrateTapMovement
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.movements.model.MigrateTapOccurrence
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.movements.model.MigrateTapRequest
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.movements.model.MigrateTapResponse
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.movements.model.MigratedAuthorisation
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.movements.model.MigratedMovement
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.movements.model.MigratedOccurrence
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.movements.model.SyncAtAndBy
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.movements.model.SyncAtAndByWithPrison
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.movements.model.SyncResponse
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.movements.model.SyncWriteTapAuthorisation
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.movements.model.SyncWriteTapMovement
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.movements.model.SyncWriteTapOccurrence
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomismappings.model.ErrorResponse
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.organisations.OrganisationsDpsApiExtension.Companion.objectMapper
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.wiremock.getRequestBodies
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.wiremock.getRequestBody
import java.time.LocalDateTime
import java.util.*

class ExternalMovementsDpsApiExtension :
  BeforeAllCallback,
  AfterAllCallback,
  BeforeEachCallback {
  companion object {
    @JvmField
    val dpsExtMovementsServer = ExternalMovementsDpsApiMockServer()
    lateinit var objectMapper: ObjectMapper
  }

  override fun beforeAll(context: ExtensionContext) {
    dpsExtMovementsServer.start()
    objectMapper = (SpringExtension.getApplicationContext(context).getBean("jackson2ObjectMapper") as ObjectMapper)
  }

  override fun beforeEach(context: ExtensionContext) {
    dpsExtMovementsServer.resetAll()
  }

  override fun afterAll(context: ExtensionContext) {
    dpsExtMovementsServer.stop()
  }
}

@Component
class ExternalMovementsDpsApiMockServer : WireMockServer(WIREMOCK_PORT) {
  companion object {
    private const val WIREMOCK_PORT = 8103
    private val today = LocalDateTime.now()
    private val tomorrow = today.plusDays(1)
    private val yesterday = today.minusDays(1)

    @Suppress("unused")
    inline fun <reified T> getRequestBody(pattern: RequestPatternBuilder): T = dpsExtMovementsServer.getRequestBody(pattern, ExternalMovementsDpsApiExtension.Companion.objectMapper)
    inline fun <reified T> getRequestBodies(pattern: RequestPatternBuilder): List<T> = dpsExtMovementsServer.getRequestBodies(pattern, ExternalMovementsDpsApiExtension.Companion.objectMapper)

    fun syncTapAuthorisation() = SyncWriteTapAuthorisation(
      prisonCode = "LEI",
      statusCode = "APPROVED",
      absenceTypeCode = "RDR",
      absenceSubTypeCode = "RR",
      absenceReasonCode = "C5",
      accompaniedByCode = "U",
      transportCode = "TNR",
      repeat = false,
      start = today.toLocalDate(),
      end = tomorrow.toLocalDate(),
      comments = "Authorisation comment",
      created = SyncAtAndBy(today, "AAA11A"),
      updated = SyncAtAndBy(today, "AAA11A"),
      legacyId = 1234,
    )

    fun syncTapOccurrence() = SyncWriteTapOccurrence(
      start = today,
      end = tomorrow,
      location = Location(
        description = "Boots",
        address = "High Street, Sheffield",
        postcode = "S1 1AA",
        uprn = 987L,
      ),
      absenceTypeCode = "RDR",
      absenceSubTypeCode = "RR",
      absenceReasonCode = "C5",
      accompaniedByCode = "P",
      transportCode = "VAN",
      comments = "Occurrence comment",
      created = SyncAtAndBy(today, "AAA11A"),
      updated = SyncAtAndBy(today, "AAA11A"),
      isCancelled = false,
      legacyId = 1234,
    )

    fun syncTapMovement(occurrenceId: UUID? = null) = SyncWriteTapMovement(
      occurrenceId = occurrenceId,
      occurredAt = today,
      direction = SyncWriteTapMovement.Direction.OUT,
      absenceReasonCode = "C5",
      location = Location(
        description = "Boots",
        address = "High Street, Sheffield",
        postcode = "S1 1AA",
        uprn = 987L,
      ),
      accompaniedByCode = "P",
      accompaniedByComments = "accompanied notes",
      comments = "movement notes",
      created = SyncAtAndByWithPrison(today, "AAA11A", "LEI"),
      updated = SyncAtAndBy(today, "AAA11A"),
      legacyId = "12345_6",
    )

    fun syncResponse() = SyncResponse(UUID.randomUUID())

    fun migratePrisonerTaps() = MigrateTapRequest(
      temporaryAbsences = listOf(
        MigrateTapAuthorisation(
          prisonCode = "LEI",
          statusCode = "APPROVED",
          absenceReasonCode = "C5",
          accompaniedByCode = "P",
          transportCode = "VAN",
          repeat = false,
          start = yesterday.toLocalDate(),
          end = today.toLocalDate(),
          created = SyncAtAndBy(yesterday, "AAA11A"),
          legacyId = 1234,
          absenceTypeCode = "RDR",
          absenceSubTypeCode = "RR",
          comments = "Authorisation comment",
          updated = SyncAtAndBy(today, "AAA11A"),
          occurrences = listOf(
            MigrateTapOccurrence(
              isCancelled = false,
              start = yesterday,
              end = today,
              location = Location(
                description = "Boots",
                address = "High Street, Sheffield",
                postcode = "S1 1AA",
                uprn = 987L,
              ),
              absenceReasonCode = "C6",
              accompaniedByCode = "R",
              transportCode = "TAXI",
              created = SyncAtAndBy(yesterday, "AAA11A"),
              legacyId = 5678,
              absenceTypeCode = "ROR",
              absenceSubTypeCode = "RRR",
              contactInformation = "contact info",
              comments = "Occurrence comment",
              updated = SyncAtAndBy(today, "AAA11A"),
              movements = listOf(
                MigrateTapMovement(
                  occurredAt = yesterday,
                  direction = MigrateTapMovement.Direction.OUT,
                  absenceReasonCode = "C7",
                  location = Location(
                    description = "Allied",
                    address = "Big Street, Sheffield",
                    postcode = "S1 1BB",
                    uprn = 876L,
                  ),
                  accompaniedByCode = "S",
                  created = SyncAtAndByWithPrison(yesterday, "AAA11A", "LEI"),
                  legacyId = "12345_2",
                  accompaniedByComments = "accompanied notes",
                  comments = "movement notes",
                  updated = SyncAtAndBy(today, "AAA11A"),
                ),
                MigrateTapMovement(
                  occurredAt = today,
                  direction = MigrateTapMovement.Direction.IN,
                  absenceReasonCode = "C8",
                  location = Location(
                    description = "Allied",
                    address = "Big Street, Sheffield",
                    postcode = "S1 1BB",
                    uprn = 876L,
                  ),
                  accompaniedByCode = "T",
                  created = SyncAtAndByWithPrison(today, "AAA11A", "LEI"),
                  legacyId = "12345_3",
                  accompaniedByComments = "accompanied IN notes",
                  comments = "movement IN notes",
                  updated = null,
                ),
              ),
            ),
          ),
        ),
      ),
      unscheduledMovements = listOf(
        MigrateTapMovement(
          occurredAt = yesterday,
          direction = MigrateTapMovement.Direction.OUT,
          absenceReasonCode = "C7",
          location = Location(
            description = "Allied",
            address = "Big Street, Sheffield",
            postcode = "S1 1BB",
            uprn = 876L,
          ),
          accompaniedByCode = "S",
          created = SyncAtAndByWithPrison(yesterday, "AAA11A", "LEI"),
          legacyId = "12345_4",
          accompaniedByComments = "accompanied notes",
          comments = "movement notes",
          updated = SyncAtAndBy(today, "AAA11A"),
        ),
        MigrateTapMovement(
          occurredAt = today,
          direction = MigrateTapMovement.Direction.IN,
          absenceReasonCode = "C8",
          location = Location(
            description = "Allied",
            address = "Big Street, Sheffield",
            postcode = "S1 1BB",
            uprn = 876L,
          ),
          accompaniedByCode = "T",
          created = SyncAtAndByWithPrison(today, "AAA11A", "LEI"),
          legacyId = "12345_5",
          accompaniedByComments = "accompanied IN notes",
          comments = "movement IN notes",
          updated = null,
        ),
      ),
    )

    fun migrateResponse(
      dpsAuthorisationId: UUID = UUID.randomUUID(),
      dpsOccurrenceId: UUID = UUID.randomUUID(),
      dpsScheduledMovementOutId: UUID = UUID.randomUUID(),
      dpsScheduledMovementInId: UUID = UUID.randomUUID(),
      dpsUnscheduledMovementOutId: UUID = UUID.randomUUID(),
      dpsUnscheduledMovementInId: UUID = UUID.randomUUID(),
    ) = MigrateTapResponse(
      temporaryAbsences = listOf(
        MigratedAuthorisation(
          legacyId = 1,
          id = dpsAuthorisationId,
          occurrences = listOf(
            MigratedOccurrence(
              legacyId = 1,
              id = dpsOccurrenceId,
              movements = listOf(
                MigratedMovement(
                  legacyId = "12345_3",
                  id = dpsScheduledMovementOutId,
                ),
                MigratedMovement(
                  legacyId = "12345_4",
                  id = dpsScheduledMovementInId,
                ),
              ),
            ),
          ),
        ),
      ),
      unscheduledMovements = listOf(
        MigratedMovement(
          legacyId = "12345_1",
          id = dpsUnscheduledMovementOutId,
        ),
        MigratedMovement(
          legacyId = "12345_2",
          id = dpsUnscheduledMovementInId,
        ),
      ),
    )
  }

  fun stubHealthPing(status: Int) {
    stubFor(
      get("/health/ping").willReturn(
        aResponse()
          .withHeader("Content-Type", "application/json")
          .withBody(if (status == 200) "pong" else "some error")
          .withStatus(status),
      ),
    )
  }

  fun stubSyncTapAuthorisation(personIdentifier: String = "A1234BC", response: SyncResponse = syncResponse()) {
    dpsExtMovementsServer.stubFor(
      put("/sync/temporary-absence-authorisations/$personIdentifier")
        .willReturn(
          aResponse()
            .withStatus(201)
            .withHeader("Content-Type", "application/json")
            .withBody(objectMapper.writeValueAsString(response)),
        ),
    )
  }

  fun stubSyncTapAuthorisationError(
    personIdentifier: String = "A1234BC",
    status: Int = 500,
    error: ErrorResponse = ErrorResponse(status = status),
  ) {
    dpsExtMovementsServer.stubFor(
      put("/sync/temporary-absence-authorisations/$personIdentifier")
        .willReturn(
          aResponse()
            .withStatus(status)
            .withHeader("Content-Type", "application/json")
            .withBody(objectMapper.writeValueAsString(error)),
        ),
    )
  }

  fun stubDeleteTapAuthorisation(authorisationId: UUID) {
    dpsExtMovementsServer.stubFor(
      delete("/sync/temporary-absence-authorisations/$authorisationId")
        .willReturn(
          aResponse()
            .withStatus(204),
        ),
    )
  }

  fun stubDeleteTapAuthorisationError(
    authorisationId: UUID,
    status: Int = 500,
    error: ErrorResponse = ErrorResponse(status = status),
  ) {
    dpsExtMovementsServer.stubFor(
      delete("/sync/temporary-absence-authorisations/$authorisationId")
        .willReturn(
          aResponse()
            .withStatus(status)
            .withHeader("Content-Type", "application/json")
            .withBody(objectMapper.writeValueAsString(error)),
        ),
    )
  }

  fun stubSyncTapOccurrence(authorisationId: UUID, response: SyncResponse = syncResponse()) {
    dpsExtMovementsServer.stubFor(
      put("/sync/temporary-absence-authorisations/$authorisationId/occurrences")
        .willReturn(
          aResponse()
            .withStatus(200)
            .withHeader("Content-Type", "application/json")
            .withBody(objectMapper.writeValueAsString(response)),
        ),
    )
  }

  fun stubSyncTapOccurrenceError(
    authorisationId: UUID,
    status: Int = 500,
    error: ErrorResponse = ErrorResponse(status = status),
  ) {
    dpsExtMovementsServer.stubFor(
      put("/sync/temporary-absence-authorisations/$authorisationId/occurrences")
        .willReturn(
          aResponse()
            .withStatus(status)
            .withHeader("Content-Type", "application/json")
            .withBody(objectMapper.writeValueAsString(error)),
        ),
    )
  }

  fun stubDeleteTapOccurrence(occurrenceId: UUID) {
    dpsExtMovementsServer.stubFor(
      delete("/sync/temporary-absence-occurrences/$occurrenceId")
        .willReturn(
          aResponse()
            .withStatus(204),
        ),
    )
  }

  fun stubDeleteTapOccurrenceError(
    occurrenceId: UUID,
    status: Int = 500,
    error: ErrorResponse = ErrorResponse(status = status),
  ) {
    dpsExtMovementsServer.stubFor(
      delete("/sync/temporary-absence-occurrences/$occurrenceId")
        .willReturn(
          aResponse()
            .withStatus(status)
            .withHeader("Content-Type", "application/json")
            .withBody(objectMapper.writeValueAsString(error)),
        ),
    )
  }

  fun stubSyncTapMovement(personIdentifier: String = "A1234BC", response: SyncResponse = syncResponse()) {
    dpsExtMovementsServer.stubFor(
      put("/sync/temporary-absence-movements/$personIdentifier")
        .willReturn(
          aResponse()
            .withStatus(200)
            .withHeader("Content-Type", "application/json")
            .withBody(objectMapper.writeValueAsString(response)),
        ),
    )
  }

  fun stubSyncTapMovementError(
    personIdentifier: String = "A1234BC",
    status: Int = 500,
    error: ErrorResponse = ErrorResponse(status = status),
  ) {
    dpsExtMovementsServer.stubFor(
      put("/sync/temporary-absence-movements/$personIdentifier")
        .willReturn(
          aResponse()
            .withStatus(status)
            .withHeader("Content-Type", "application/json")
            .withBody(objectMapper.writeValueAsString(error)),
        ),
    )
  }

  fun stubDeleteTapMovement(movementId: UUID) {
    dpsExtMovementsServer.stubFor(
      delete("/sync/temporary-absence-movements/$movementId")
        .willReturn(
          aResponse()
            .withStatus(204),
        ),
    )
  }

  fun stubDeleteTapMovementError(
    movementId: UUID,
    status: Int = 500,
    error: ErrorResponse = ErrorResponse(status = status),
  ) {
    dpsExtMovementsServer.stubFor(
      delete("/sync/temporary-absence-movements/$movementId")
        .willReturn(
          aResponse()
            .withStatus(status)
            .withHeader("Content-Type", "application/json")
            .withBody(objectMapper.writeValueAsString(error)),
        ),
    )
  }

  fun stubMigratePrisonerTaps(personIdentifier: String = "A1234BC", response: MigrateTapResponse = migrateResponse()) {
    dpsExtMovementsServer.stubFor(
      put("/migrate/temporary-absences/$personIdentifier")
        .willReturn(
          aResponse()
            .withStatus(200)
            .withHeader("Content-Type", "application/json")
            .withBody(objectMapper.writeValueAsString(response)),
        ),
    )
  }

  fun stubMigratePrisonerTapsError(
    personIdentifier: String = "A1234BC",
    status: Int = 500,
    error: ErrorResponse = ErrorResponse(status = status),
  ) {
    dpsExtMovementsServer.stubFor(
      put("/migrate/temporary-absences/$personIdentifier")
        .willReturn(
          aResponse()
            .withStatus(status)
            .withHeader("Content-Type", "application/json")
            .withBody(objectMapper.writeValueAsString(error)),
        ),
    )
  }
}
