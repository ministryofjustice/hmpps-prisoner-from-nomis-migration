package uk.gov.justice.digital.hmpps.prisonerfromnomismigration.service

import com.fasterxml.jackson.annotation.JsonProperty
import com.fasterxml.jackson.databind.JsonNode
import kotlinx.coroutines.reactive.awaitSingle
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.core.ParameterizedTypeReference
import org.springframework.data.domain.PageImpl
import org.springframework.data.domain.PageRequest
import org.springframework.stereotype.Service
import org.springframework.util.LinkedMultiValueMap
import org.springframework.web.reactive.function.BodyInserters
import org.springframework.web.reactive.function.client.WebClient
import org.springframework.web.reactive.function.client.WebClientResponseException
import org.springframework.web.reactive.function.client.awaitBodilessEntity
import org.springframework.web.reactive.function.client.awaitBody
import reactor.core.publisher.Mono
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.activities.model.AppointmentMigrateRequest
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.config.BadRequestException
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.csip.CSIPMigrateRequest
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.incidents.model.NomisIncidentReport
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.incidents.model.NomisIncidentStatus
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.incidents.model.UpsertNomisIncident
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomissync.model.AdjudicationChargeIdResponse
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomissync.model.AdjudicationChargeResponse
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomissync.model.CSIPIdResponse
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomissync.model.CSIPResponse
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomissync.model.CodeDescription
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomissync.model.EndActivitiesRequest
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomissync.model.ErrorResponse
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomissync.model.FindActiveActivityIdsResponse
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomissync.model.FindActiveAllocationIdsResponse
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomissync.model.GetActivityResponse
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomissync.model.GetAllocationResponse
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomissync.model.History
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomissync.model.HistoryQuestion
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomissync.model.HistoryResponse
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomissync.model.IncidentIdResponse
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomissync.model.IncidentResponse
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomissync.model.LocationIdResponse
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomissync.model.LocationResponse
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomissync.model.Offender
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomissync.model.OffenderParty
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomissync.model.Question
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomissync.model.Requirement
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomissync.model.Response
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomissync.model.Staff
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomissync.model.StaffParty
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.sentencing.adjustments.model.LegacyAdjustment
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.sentencing.adjustments.model.LegacyAdjustment.AdjustmentType
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.visits.VisitRoomUsageResponse
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.visits.VisitsMigrationFilter
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.incidents.model.CodeDescription as IncidentsApiCodeDescription
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.incidents.model.History as IncidentsApiHistory
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.incidents.model.HistoryQuestion as IncidentsApiHistoryQuestion
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.incidents.model.HistoryResponse as IncidentsApiHistoryResponse
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.incidents.model.Offender as IncidentsApiOffender
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.incidents.model.OffenderParty as IncidentsApiOffenderParty
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.incidents.model.Question as IncidentsApiQuestion
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.incidents.model.Requirement as IncidentsApiRequirement
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.incidents.model.Response as IncidentsApiResponse
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.incidents.model.Staff as IncidentsApiStaff
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.incidents.model.StaffParty as IncidentsApiStaffParty

@Service
class NomisApiService(@Qualifier("nomisApiWebClient") private val webClient: WebClient) {
  suspend fun getVisits(
    prisonIds: List<String>,
    visitTypes: List<String>,
    fromDateTime: LocalDateTime?,
    toDateTime: LocalDateTime?,
    ignoreMissingRoom: Boolean,
    pageNumber: Long,
    pageSize: Long,
  ): PageImpl<VisitId> =
    webClient.get()
      .uri {
        it.path("/visits/ids")
          .queryParam("prisonIds", prisonIds)
          .queryParam("visitTypes", visitTypes)
          .queryParam("fromDateTime", fromDateTime)
          .queryParam("toDateTime", toDateTime)
          .queryParam("ignoreMissingRoom", ignoreMissingRoom)
          .queryParam("page", pageNumber)
          .queryParam("size", pageSize)
          .build()
      }
      .retrieve()
      .bodyToMono(typeReference<RestResponsePage<VisitId>>())
      .awaitSingle()

  suspend fun getVisit(
    nomisVisitId: Long,
  ): NomisVisit =
    webClient.get()
      .uri("/visits/{nomisVisitId}", nomisVisitId)
      .retrieve()
      .bodyToMono(NomisVisit::class.java)
      .awaitSingle()!!

  suspend fun getRoomUsage(
    filter: VisitsMigrationFilter,
  ): List<VisitRoomUsageResponse> =
    webClient.get()
      .uri {
        it.path("/visits/rooms/usage-count")
          .queryParam("prisonIds", filter.prisonIds)
          .queryParam("visitTypes", filter.visitTypes)
          .queryParam("fromDateTime", filter.fromDateTime)
          .queryParam("toDateTime", filter.toDateTime)
          .build()
      }
      .retrieve()
      .bodyToMono(typeReference<List<VisitRoomUsageResponse>>())
      .awaitSingle()

  suspend fun getSentencingAdjustmentIds(
    fromDate: LocalDate?,
    toDate: LocalDate?,
    pageNumber: Long,
    pageSize: Long,
  ): PageImpl<NomisAdjustmentId> =
    webClient.get()
      .uri {
        it.path("/adjustments/ids")
          .queryParam("fromDate", fromDate)
          .queryParam("toDate", toDate)
          .queryParam("page", pageNumber)
          .queryParam("size", pageSize)
          .build()
      }
      .retrieve()
      .bodyToMono(typeReference<RestResponsePage<NomisAdjustmentId>>())
      .awaitSingle()

  suspend fun getSentenceAdjustment(
    nomisSentenceAdjustmentId: Long,
  ): NomisAdjustment =
    webClient.get()
      .uri("/sentence-adjustments/{nomisSentenceAdjustmentId}", nomisSentenceAdjustmentId)
      .retrieve()
      .bodyToMono(NomisAdjustment::class.java)
      .awaitSingle()

  suspend fun getKeyDateAdjustment(
    nomisKeyDateAdjustmentId: Long,
  ): NomisAdjustment =
    webClient.get()
      .uri("/key-date-adjustments/{nomisKeyDateAdjustmentId}", nomisKeyDateAdjustmentId)
      .retrieve()
      .bodyToMono(NomisAdjustment::class.java)
      .awaitSingle()

  suspend fun getAppointment(nomisEventId: Long): AppointmentResponse =
    webClient.get()
      .uri("/appointments/{nomisEventId}", nomisEventId)
      .retrieve()
      .bodyToMono(AppointmentResponse::class.java)
      .awaitSingle()

  suspend fun getAppointmentIds(
    prisonIds: List<String>,
    fromDate: LocalDate?,
    toDate: LocalDate?,
    pageNumber: Long,
    pageSize: Long,
  ): PageImpl<AppointmentIdResponse> =
    webClient.get()
      .uri {
        it.path("/appointments/ids")
          .queryParam("prisonIds", prisonIds)
          .queryParam("fromDate", fromDate)
          .queryParam("toDate", toDate)
          .queryParam("page", pageNumber)
          .queryParam("size", pageSize)
          .build()
      }
      .retrieve()
      .bodyToMono(typeReference<RestResponsePage<AppointmentIdResponse>>())
      .awaitSingle()

  suspend fun getAdjudicationIds(
    prisonIds: List<String>,
    fromDate: LocalDate?,
    toDate: LocalDate?,
    pageNumber: Long,
    pageSize: Long,
  ): PageImpl<AdjudicationChargeIdResponse> =
    webClient.get()
      .uri {
        it.path("/adjudications/charges/ids")
          .queryParam("prisonIds", prisonIds)
          .queryParam("fromDate", fromDate)
          .queryParam("toDate", toDate)
          .queryParam("page", pageNumber)
          .queryParam("size", pageSize)
          .build()
      }
      .retrieve()
      .bodyToMono(typeReference<RestResponsePage<AdjudicationChargeIdResponse>>())
      .awaitSingle()

  suspend fun getAdjudicationCharge(adjudicationNumber: Long, chargeSequence: Int): AdjudicationChargeResponse =
    webClient.get()
      .uri(
        "/adjudications/adjudication-number/{adjudicationNumber}/charge-sequence/{chargeSequence}",
        adjudicationNumber,
        chargeSequence,
      )
      .retrieve()
      .awaitBody()

  suspend fun getActivity(courseActivityId: Long): GetActivityResponse =
    webClient.get()
      .uri("/activities/{courseActivityId}", courseActivityId)
      .retrieve()
      .bodyToMono(GetActivityResponse::class.java)
      .awaitSingle()

  suspend fun getActivityIds(
    prisonId: String,
    excludeProgramCodes: List<String>,
    courseActivityId: Long? = null,
    pageNumber: Long,
    pageSize: Long,
  ): PageImpl<FindActiveActivityIdsResponse> =
    webClient.get()
      .uri {
        it.path("/activities/ids")
          .queryParam("prisonId", prisonId)
          .queryParams(LinkedMultiValueMap<String, String>().apply { addAll("excludeProgramCode", excludeProgramCodes) })
          .apply { courseActivityId?.run { queryParam("courseActivityId", courseActivityId) } }
          .queryParam("page", pageNumber)
          .queryParam("size", pageSize)
          .build()
      }
      .retrieve()
      .bodyToMono(typeReference<RestResponsePage<FindActiveActivityIdsResponse>>())
      .onErrorResume(WebClientResponseException.BadRequest::class.java) {
        val errorResponse = it.getResponseBodyAs(ErrorResponse::class.java) as ErrorResponse
        Mono.error(BadRequestException(errorResponse.userMessage ?: "Received a 400 calling /activities/ids"))
      }
      .awaitSingle()

  suspend fun endActivities(ids: List<Long>) =
    webClient.put()
      .uri("/activities/end")
      .body(BodyInserters.fromValue(EndActivitiesRequest(ids)))
      .retrieve()
      .awaitBodilessEntity()

  suspend fun getAllocation(allocationId: Long): GetAllocationResponse =
    webClient.get()
      .uri("/allocations/{allocationId}", allocationId)
      .retrieve()
      .bodyToMono(GetAllocationResponse::class.java)
      .awaitSingle()

  suspend fun getAllocationIds(
    prisonId: String,
    excludeProgramCodes: List<String>,
    courseActivityId: Long? = null,
    pageNumber: Long,
    pageSize: Long,
  ): PageImpl<FindActiveAllocationIdsResponse> =
    webClient.get()
      .uri {
        it.path("/allocations/ids")
          .queryParam("prisonId", prisonId)
          .queryParams(LinkedMultiValueMap<String, String>().apply { addAll("excludeProgramCode", excludeProgramCodes) })
          .apply { courseActivityId?.run { queryParam("courseActivityId", courseActivityId) } }
          .queryParam("page", pageNumber)
          .queryParam("size", pageSize)
          .build()
      }
      .retrieve()
      .bodyToMono(typeReference<RestResponsePage<FindActiveAllocationIdsResponse>>())
      .onErrorResume(WebClientResponseException.BadRequest::class.java) {
        val errorResponse = it.getResponseBodyAs(ErrorResponse::class.java) as ErrorResponse
        Mono.error(BadRequestException(errorResponse.userMessage ?: "Received a 400 calling /allocations/ids"))
      }
      .awaitSingle()

  // /////////////////////////////////////// Incidents

  suspend fun getIncident(incidentId: Long): IncidentResponse =
    webClient.get()
      .uri("/incidents/{incidentId}", incidentId)
      .retrieve()
      .bodyToMono(IncidentResponse::class.java)
      .awaitSingle()

  suspend fun getIncidentIds(
    fromDate: LocalDate?,
    toDate: LocalDate?,
    pageNumber: Long,
    pageSize: Long,
  ): PageImpl<IncidentIdResponse> =
    webClient.get()
      .uri {
        it.path("/incidents/ids")
          .queryParam("fromDate", fromDate)
          .queryParam("toDate", toDate)
          .queryParam("page", pageNumber)
          .queryParam("size", pageSize)
          .build()
      }
      .retrieve()
      .bodyToMono(typeReference<RestResponsePage<IncidentIdResponse>>())
      .awaitSingle()

  // /////////////////////////////////////// Locations

  suspend fun getLocation(locationId: Long): LocationResponse =
    webClient.get()
      .uri("/locations/{locationId}", locationId)
      .retrieve()
      .bodyToMono(LocationResponse::class.java)
      .awaitSingle()

  suspend fun getLocationIds(
    pageNumber: Long,
    pageSize: Long,
  ): PageImpl<LocationIdResponse> =
    webClient.get()
      .uri {
        it.path("/locations/ids")
          .queryParam("page", pageNumber)
          .queryParam("size", pageSize)
          .build()
      }
      .retrieve()
      .bodyToMono(typeReference<RestResponsePage<LocationIdResponse>>())
      .awaitSingle()

  // /////////////////////////////////////// CSIP

  suspend fun getCSIP(csipId: Long): CSIPResponse =
    webClient.get()
      .uri("/csip/{csipId}", csipId)
      .retrieve()
      .bodyToMono(CSIPResponse::class.java)
      .awaitSingle()

  suspend fun getCSIPIds(
    fromDate: LocalDate?,
    toDate: LocalDate?,
    pageNumber: Long,
    pageSize: Long,
  ): PageImpl<CSIPIdResponse> =
    webClient.get()
      .uri {
        it.path("/csip/ids")
          .queryParam("fromDate", fromDate)
          .queryParam("toDate", toDate)
          .queryParam("page", pageNumber)
          .queryParam("size", pageSize)
          .build()
      }
      .retrieve()
      .bodyToMono(typeReference<RestResponsePage<CSIPIdResponse>>())
      .awaitSingle()
}

data class VisitId(
  val visitId: Long,
)

data class NomisAdjustmentId(
  val adjustmentId: Long,
  val adjustmentCategory: String,
)

data class NomisVisitor(
  val personId: Long,
)

data class NomisLeadVisitor(
  val personId: Long,
  val fullName: String,
  // latest modified first
  val telephones: List<String>,
)

data class NomisCodeDescription(val code: String, val description: String)

data class NomisVisit(
  val visitId: Long,
  val offenderNo: String,
  val startDateTime: LocalDateTime,
  val endDateTime: LocalDateTime,
  val prisonId: String,
  val visitors: List<NomisVisitor>,
  val visitType: NomisCodeDescription,
  val visitStatus: NomisCodeDescription,
  val visitOutcome: NomisCodeDescription? = null,
  val agencyInternalLocation: NomisCodeDescription? = null,
  val commentText: String? = null,
  val visitorConcernText: String? = null,
  val leadVisitor: NomisLeadVisitor? = null,
  val createUserId: String,
  val modifyUserId: String? = null,
  val whenCreated: LocalDateTime,
  val whenUpdated: LocalDateTime? = null,
)

data class NomisAdjustment(
  val id: Long,
  val bookingId: Long,
  val offenderNo: String,
  val sentenceSequence: Long? = null,
  val adjustmentType: NomisCodeDescription,
  val adjustmentDate: LocalDate?,
  val adjustmentFromDate: LocalDate?,
  val adjustmentToDate: LocalDate?,
  val adjustmentDays: Long,
  val comment: String?,
  val active: Boolean,
  val hiddenFromUsers: Boolean,
  val hasBeenReleased: Boolean,
) {
  fun toSentencingAdjustment(): LegacyAdjustment = LegacyAdjustment(
    bookingId = bookingId,
    sentenceSequence = sentenceSequence?.toInt(),
    adjustmentType = AdjustmentType.valueOf(adjustmentType.code),
    adjustmentDate = adjustmentDate,
    adjustmentFromDate = adjustmentFromDate,
    adjustmentDays = adjustmentDays.toInt(),
    comment = comment,
    active = active,
    offenderNo = offenderNo,
    bookingReleased = hasBeenReleased,
  )

  fun getAdjustmentCategory() = sentenceSequence?.let { "SENTENCE" } ?: "KEY_DATE"
}

private val simpleTimeFormat = DateTimeFormatter.ofPattern("HH:mm")

data class AppointmentResponse(
  val bookingId: Long,
  val offenderNo: String,
  // prison or toPrison is never null in existing nomis data for event_type = 'APP' (as at 11/5/2023)
  val prisonId: String,
  val internalLocation: Long? = null,
  val startDateTime: LocalDateTime? = null,
  val endDateTime: LocalDateTime? = null,
  val comment: String? = null,
  val subtype: String,
  val status: String,
  val createdDate: LocalDateTime,
  val createdBy: String,
  val modifiedDate: LocalDateTime? = null,
  val modifiedBy: String? = null,
) {
  fun toAppointment() = AppointmentMigrateRequest(
    bookingId = bookingId,
    prisonerNumber = offenderNo,
    prisonCode = prisonId,
    internalLocationId = internalLocation!!,
    // startDate never null in existing nomis data for event_type = 'APP' (as at 11/5/2023)
    startDate = startDateTime!!.toLocalDate(),
    startTime = startDateTime.toLocalTime().format(simpleTimeFormat),
    endTime = endDateTime?.toLocalTime()?.format(simpleTimeFormat),
    comment = comment,
    categoryCode = subtype,
    isCancelled = status == "CANC",
    created = createdDate.truncatedTo(ChronoUnit.SECONDS),
    createdBy = createdBy,
    updated = modifiedDate?.truncatedTo(ChronoUnit.SECONDS),
    updatedBy = modifiedBy,
  )
}

data class AppointmentIdResponse(
  val eventId: Long,
)

class RestResponsePage<T>(
  @JsonProperty("content") content: List<T>,
  @JsonProperty("number") number: Int,
  @JsonProperty("size") size: Int,
  @JsonProperty("totalElements") totalElements: Long,
  @Suppress("UNUSED_PARAMETER")
  @JsonProperty("pageable") pageable: JsonNode,
) : PageImpl<T>(content, PageRequest.of(number, size), totalElements)

inline fun <reified T> typeReference() = object : ParameterizedTypeReference<T>() {}

fun CSIPResponse.toMigrateRequest() =
  CSIPMigrateRequest(
    nomisCSIPId = id,
    referralSummary = "test",
  )

fun IncidentResponse.toMigrateUpsertNomisIncident() =
  UpsertNomisIncident(
    initialMigration = true,
    incidentReport = toNomisIncidentReport(),
  )

fun IncidentResponse.toNomisIncidentReport() =
  NomisIncidentReport(
    incidentId = incidentId,
    questionnaireId = questionnaireId,
    prison = prison.toUpsertCodeDescription(),
    status = NomisIncidentStatus(status.code, status.description),
    type = type,
    lockedResponse = lockedResponse,
    incidentDateTime = incidentDateTime,
    reportingStaff = reportingStaff.toUpsertStaff(),
    reportedDateTime = reportedDateTime,
    staffParties = staffParties.map { it.toUpsertStaffParty() },
    offenderParties = offenderParties.map { it.toUpsertOffenderParty() },
    requirements = requirements.map { it.toUpsertRequirement() },
    questions = questions.map { it.toUpsertQuestion() },
    history = history.map { it.toUpsertHistory() },
    title = title,
    description = description,
  )

fun CodeDescription.toUpsertCodeDescription() =
  IncidentsApiCodeDescription(
    code = code,
    description = description,
  )

fun Staff.toUpsertStaff() =
  IncidentsApiStaff(
    username = username,
    staffId = staffId,
    firstName = firstName,
    lastName = lastName,
  )

fun StaffParty.toUpsertStaffParty() =
  IncidentsApiStaffParty(
    staff = staff.toUpsertStaff(),
    role = role.toUpsertCodeDescription(),
    comment = comment,
  )

fun OffenderParty.toUpsertOffenderParty() =
  IncidentsApiOffenderParty(
    offender = offender.toUpsertOffender(),
    role = role.toUpsertCodeDescription(),
    outcome = outcome?.toUpsertCodeDescription(),
    comment = comment,
  )

fun Offender.toUpsertOffender() =
  IncidentsApiOffender(
    offenderNo = offenderNo,
    firstName = firstName,
    lastName = lastName,
  )

fun Requirement.toUpsertRequirement() =
  IncidentsApiRequirement(
    date = date,
    staff = staff.toUpsertStaff(),
    prisonId = prisonId,
    comment = comment,
  )

fun Question.toUpsertQuestion() =
  IncidentsApiQuestion(
    questionId = questionId,
    sequence = sequence,
    question = question,
    answers = answers.map { it.toUpsertResponse() },
  )

fun Response.toUpsertResponse() =
  IncidentsApiResponse(
    sequence = sequence,
    recordingStaff = recordingStaff.toUpsertStaff(),
    questionResponseId = questionResponseId,
    answer = answer,
    comment = comment,
  )

fun History.toUpsertHistory() =
  IncidentsApiHistory(
    questionnaireId = questionnaireId,
    type = type,
    questions = questions.map { it.toUpsertHistoryQuestion() },
    incidentChangeDate = incidentChangeDate,
    incidentChangeStaff = incidentChangeStaff.toUpsertStaff(),
    description = description,
  )

fun HistoryQuestion.toUpsertHistoryQuestion() =
  IncidentsApiHistoryQuestion(
    questionId = questionId,
    sequence = sequence,
    question = question,
    answers = answers.map { it.toUpsertHistoryResponse() },
  )

fun HistoryResponse.toUpsertHistoryResponse() =
  IncidentsApiHistoryResponse(
    responseSequence = responseSequence,
    recordingStaff = recordingStaff.toUpsertStaff(),
    questionResponseId = questionResponseId,
    answer = answer,
    comment = comment,
  )
