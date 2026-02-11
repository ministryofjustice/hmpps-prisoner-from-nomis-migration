package uk.gov.justice.digital.hmpps.prisonerfromnomismigration.officialvisits

import com.github.tomakehurst.wiremock.WireMockServer
import com.github.tomakehurst.wiremock.client.WireMock.aResponse
import com.github.tomakehurst.wiremock.client.WireMock.delete
import com.github.tomakehurst.wiremock.client.WireMock.get
import com.github.tomakehurst.wiremock.client.WireMock.post
import com.github.tomakehurst.wiremock.client.WireMock.put
import com.github.tomakehurst.wiremock.matching.RequestPatternBuilder
import org.junit.jupiter.api.extension.AfterAllCallback
import org.junit.jupiter.api.extension.BeforeAllCallback
import org.junit.jupiter.api.extension.BeforeEachCallback
import org.junit.jupiter.api.extension.ExtensionContext
import org.springframework.test.context.junit.jupiter.SpringExtension
import tools.jackson.databind.json.JsonMapper
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.officialvisits.OfficialVisitsDpsApiExtension.Companion.jsonMapper
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.officialvisits.model.DayType
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.officialvisits.model.IdPair
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.officialvisits.model.MigrateVisitConfigRequest
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.officialvisits.model.MigrateVisitConfigResponse
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.officialvisits.model.MigrateVisitRequest
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.officialvisits.model.MigrateVisitResponse
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.officialvisits.model.MigrateVisitSlot
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.officialvisits.model.MigrateVisitor
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.officialvisits.model.SyncCreateOfficialVisitRequest
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.officialvisits.model.SyncCreateOfficialVisitorRequest
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.officialvisits.model.SyncCreateTimeSlotRequest
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.officialvisits.model.SyncCreateVisitSlotRequest
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.officialvisits.model.SyncOfficialVisit
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.officialvisits.model.SyncOfficialVisitor
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.officialvisits.model.SyncTimeSlot
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.officialvisits.model.SyncUpdateTimeSlotRequest
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.officialvisits.model.SyncUpdateVisitSlotRequest
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.officialvisits.model.SyncVisitSlot
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.officialvisits.model.VisitStatusType
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.officialvisits.model.VisitType
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.wiremock.getRequestBodies
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.wiremock.getRequestBody
import java.time.LocalDate
import java.time.LocalDateTime
import java.util.*

class OfficialVisitsDpsApiExtension :
  BeforeAllCallback,
  AfterAllCallback,
  BeforeEachCallback {
  companion object {
    @JvmField
    val dpsOfficialVisitsServer = OfficialVisitsDpsApiMockServer()
    lateinit var jsonMapper: JsonMapper

    inline fun <reified T> getRequestBody(pattern: RequestPatternBuilder): T = dpsOfficialVisitsServer.getRequestBody(pattern, jsonMapper)
    inline fun <reified T> getRequestBodies(pattern: RequestPatternBuilder): List<T> = dpsOfficialVisitsServer.getRequestBodies(pattern, jsonMapper)
  }

  override fun beforeAll(context: ExtensionContext) {
    dpsOfficialVisitsServer.start()
    jsonMapper = (SpringExtension.getApplicationContext(context).getBean("jacksonJsonMapper") as JsonMapper)
  }

  override fun beforeEach(context: ExtensionContext) {
    dpsOfficialVisitsServer.resetAll()
  }

  override fun afterAll(context: ExtensionContext) {
    dpsOfficialVisitsServer.stop()
  }
}

class OfficialVisitsDpsApiMockServer : WireMockServer(WIREMOCK_PORT) {
  companion object {
    private const val WIREMOCK_PORT = 8104
    fun migrateVisitSlot() = MigrateVisitSlot(
      agencyVisitSlotId = 123,
      dpsLocationId = UUID.randomUUID(),
      maxGroups = 99,
      maxAdults = 99,
    )
    fun migrateVisitConfigRequest() = MigrateVisitConfigRequest(
      prisonCode = "MDI",
      dayCode = "MON",
      timeSlotSeq = 1,
      startTime = "10:00",
      endTime = "11:00",
      effectiveDate = LocalDate.parse("2021-01-01"),
      visitSlots = listOf(migrateVisitSlot()),
    )

    fun migrateVisitConfigResponse(request: MigrateVisitConfigRequest = migrateVisitConfigRequest()) = MigrateVisitConfigResponse(
      prisonCode = request.prisonCode,
      dayCode = request.dayCode,
      timeSlotSeq = request.timeSlotSeq,
      dpsTimeSlotId = 678,
      visitSlots = request.visitSlots.map {
        IdPair(
          elementType = IdPair.ElementType.PRISON_VISIT_SLOT,
          nomisId = it.agencyVisitSlotId,
          dpsId = it.agencyVisitSlotId * 10,
        )
      },
    )

    fun syncCreateTimeSlotRequest() = SyncCreateTimeSlotRequest(
      prisonCode = "MDI",
      dayCode = DayType.MON,
      startTime = "10:00",
      endTime = "11:00",
      effectiveDate = LocalDate.parse("2021-01-01"),
      createdBy = "T.SMITH",
      createdTime = LocalDateTime.parse("2020-01-01T08:00"),
    )

    fun syncUpdateTimeSlotRequest() = SyncUpdateTimeSlotRequest(
      prisonCode = "MDI",
      dayCode = DayType.MON,
      startTime = "10:00",
      endTime = "11:00",
      effectiveDate = LocalDate.parse("2021-01-01"),
      updatedBy = "T.SMITH",
      updatedTime = LocalDateTime.parse("2020-01-01T08:00"),
    )

    fun syncTimeSlot() = SyncTimeSlot(
      prisonTimeSlotId = 1,
      prisonCode = "MDI",
      dayCode = DayType.MON,
      startTime = "10:00",
      endTime = "11:00",
      effectiveDate = LocalDate.parse("2025-01-01"),
      createdBy = "T.SMITH",
      createdTime = LocalDateTime.parse("2020-01-01T08:00"),
    )

    fun syncCreateVisitSlotRequest() = SyncCreateVisitSlotRequest(
      createdBy = "T.SMITH",
      createdTime = LocalDateTime.parse("2020-01-01T08:00"),
      prisonTimeSlotId = 1,
      dpsLocationId = UUID.randomUUID(),
      maxAdults = 10,
      maxGroups = 2,
    )

    fun syncUpdateVisitSlotRequest() = SyncUpdateVisitSlotRequest(
      updatedBy = "T.SMITH",
      updatedTime = LocalDateTime.parse("2020-01-01T08:00"),
      dpsLocationId = UUID.randomUUID(),
      maxAdults = 10,
      maxGroups = 2,
    )
    fun syncVisitSlot() = SyncVisitSlot(
      prisonTimeSlotId = 1,
      prisonCode = "MDI",
      createdBy = "T.SMITH",
      createdTime = LocalDateTime.parse("2020-01-01T08:00"),
      visitSlotId = 2,
      dpsLocationId = UUID.randomUUID(),
      maxAdults = 10,
      maxGroups = 2,
      updatedBy = "T.SMITH",
      updatedTime = LocalDateTime.parse("2020-01-01T08:00"),
    )

    fun migrateVisitRequest() = MigrateVisitRequest(
      offenderVisitId = 1,
      prisonVisitSlotId = 10,
      prisonCode = "MDI",
      offenderBookId = 20,
      prisonerNumber = "A1234KT",
      currentTerm = true,
      visitDate = LocalDate.parse("2020-01-01"),
      startTime = "10:00",
      endTime = "11:00",
      dpsLocationId = UUID.fromString("d0cc8fcd-22db-46a7-bdb3-ada7ac1828f5"),
      visitStatusCode = VisitStatusType.SCHEDULED,
      createDateTime = LocalDateTime.parse("2020-01-01T08:00"),
      createUsername = "T.SMITH",
      visitors = listOf(
        MigrateVisitor(
          offenderVisitVisitorId = 30,
          personId = 40,
          createDateTime = LocalDateTime.parse("2020-01-01T08:00"),
          createUsername = "T.SMITH",
        ),
      ),
    )

    fun migrateVisitResponse() = MigrateVisitResponse(
      visit = IdPair(nomisId = 1, dpsId = 10, elementType = IdPair.ElementType.OFFICIAL_VISIT),
      visitors = listOf(IdPair(nomisId = 30, dpsId = 300, elementType = IdPair.ElementType.OFFICIAL_VISITOR)),
      prisoner = IdPair(nomisId = 20, dpsId = 200, elementType = IdPair.ElementType.PRISONER_VISITED),
    )

    fun syncCreateOfficialVisitRequest() = SyncCreateOfficialVisitRequest(
      offenderVisitId = 1,
      prisonVisitSlotId = 10,
      prisonCode = "MDI",
      offenderBookId = 100,
      prisonerNumber = "A1234KT",
      currentTerm = true,
      visitDate = LocalDate.parse("2020-01-01"),
      startTime = "10:00",
      endTime = "11:00",
      dpsLocationId = UUID.randomUUID(),
      visitStatusCode = VisitStatusType.SCHEDULED,
      createDateTime = LocalDateTime.parse("2020-01-01T08:00"),
      createUsername = "T.SMITH",
    )

    fun syncOfficialVisit() = SyncOfficialVisit(
      officialVisitId = 1,
      visitDate = LocalDate.parse("2020-01-01"),
      startTime = "10:00",
      endTime = "11:00",
      prisonVisitSlotId = 10,
      dpsLocationId = UUID.randomUUID(),
      prisonCode = "MDI",
      prisonerNumber = "A1234KT",
      statusCode = VisitStatusType.SCHEDULED,
      visitType = VisitType.UNKNOWN,
      createdBy = "T.SMITH",
      createdTime = LocalDateTime.parse("2020-01-01T08:00"),
      visitors = emptyList(),
    )

    fun syncCreateOfficialVisitorRequest() = SyncCreateOfficialVisitorRequest(
      offenderVisitVisitorId = 1,
      personId = 100,
      createDateTime = LocalDateTime.parse("2020-01-01T08:00"),
      createUsername = "T.SMITH",
    )

    fun syncOfficialVisitor() = SyncOfficialVisitor(
      officialVisitorId = 1,
      createdBy = "T.SMITH",
      createdTime = LocalDateTime.parse("2020-01-01T08:00"),
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

  fun stubMigrateVisitConfiguration(response: MigrateVisitConfigResponse = migrateVisitConfigResponse()) {
    stubFor(
      post("/migrate/visit-configuration")
        .willReturn(
          aResponse()
            .withHeader("Content-Type", "application/json")
            .withStatus(201)
            .withBody(jsonMapper.writeValueAsString(response)),
        ),
    )
  }

  fun stubCreateTimeSlot(response: SyncTimeSlot = syncTimeSlot()) {
    stubFor(
      post("/sync/time-slot")
        .willReturn(
          aResponse()
            .withHeader("Content-Type", "application/json")
            .withStatus(201)
            .withBody(jsonMapper.writeValueAsString(response)),
        ),
    )
  }

  fun stubUpdateTimeSlot(prisonTimeSlotId: Long, response: SyncTimeSlot = syncTimeSlot()) {
    stubFor(
      put("/sync/time-slot/$prisonTimeSlotId")
        .willReturn(
          aResponse()
            .withHeader("Content-Type", "application/json")
            .withStatus(200)
            .withBody(jsonMapper.writeValueAsString(response)),
        ),
    )
  }
  fun stubDeleteTimeSlot(prisonTimeSlotId: Long) {
    stubFor(
      delete("/sync/time-slot/$prisonTimeSlotId")
        .willReturn(
          aResponse()
            .withHeader("Content-Type", "application/json")
            .withStatus(204),
        ),
    )
  }

  fun stubCreateVisitSlot(response: SyncVisitSlot = syncVisitSlot()) {
    stubFor(
      post("/sync/visit-slot")
        .willReturn(
          aResponse()
            .withHeader("Content-Type", "application/json")
            .withStatus(201)
            .withBody(jsonMapper.writeValueAsString(response)),
        ),
    )
  }

  fun stubUpdateVisitSlot(visitSlotId: Long, response: SyncVisitSlot = syncVisitSlot()) {
    stubFor(
      put("/sync/visit-slot/$visitSlotId")
        .willReturn(
          aResponse()
            .withHeader("Content-Type", "application/json")
            .withStatus(200)
            .withBody(jsonMapper.writeValueAsString(response)),
        ),
    )
  }
  fun stubDeleteVisitSlot(visitSlotId: Long) {
    stubFor(
      delete("/sync/visit-slot/$visitSlotId")
        .willReturn(
          aResponse()
            .withHeader("Content-Type", "application/json")
            .withStatus(204),
        ),
    )
  }

  fun stubMigrateVisit(response: MigrateVisitResponse = migrateVisitResponse()) {
    stubFor(
      post("/migrate/visit")
        .willReturn(
          aResponse()
            .withHeader("Content-Type", "application/json")
            .withStatus(201)
            .withBody(jsonMapper.writeValueAsString(response)),
        ),
    )
  }

  fun stubCreateVisit(response: SyncOfficialVisit = syncOfficialVisit()) {
    stubFor(
      post("/sync/official-visit")
        .willReturn(
          aResponse()
            .withHeader("Content-Type", "application/json")
            .withStatus(201)
            .withBody(jsonMapper.writeValueAsString(response)),
        ),
    )
  }

  fun stubDeleteVisit(officialVisitId: Long) {
    stubFor(
      delete("/sync/official-visit/id/$officialVisitId")
        .willReturn(
          aResponse()
            .withHeader("Content-Type", "application/json")
            .withStatus(204),
        ),
    )
  }
  fun stubCreateVisitor(officialVisitId: Long, response: SyncOfficialVisitor = syncOfficialVisitor()) {
    stubFor(
      post("/sync/official-visit/$officialVisitId/visitor")
        .willReturn(
          aResponse()
            .withHeader("Content-Type", "application/json")
            .withStatus(201)
            .withBody(jsonMapper.writeValueAsString(response)),
        ),
    )
  }
}
