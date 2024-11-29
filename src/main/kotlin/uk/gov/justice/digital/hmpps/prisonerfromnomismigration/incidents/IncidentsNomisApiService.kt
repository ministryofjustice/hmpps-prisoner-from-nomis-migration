package uk.gov.justice.digital.hmpps.prisonerfromnomismigration.incidents

import kotlinx.coroutines.reactor.awaitSingle
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.data.domain.PageImpl
import org.springframework.stereotype.Service
import org.springframework.web.reactive.function.client.WebClient
import org.springframework.web.reactive.function.client.awaitBody
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.helpers.awaitBodyOrNullWhenNotFound
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.incidents.model.NomisCode
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.incidents.model.NomisHistory
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.incidents.model.NomisHistoryQuestion
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.incidents.model.NomisHistoryResponse
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.incidents.model.NomisOffender
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.incidents.model.NomisOffenderParty
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.incidents.model.NomisQuestion
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.incidents.model.NomisReport
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.incidents.model.NomisRequirement
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.incidents.model.NomisResponse
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.incidents.model.NomisStaff
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.incidents.model.NomisStaffParty
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.incidents.model.NomisStatus
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.incidents.model.NomisSyncRequest
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomissync.model.CodeDescription
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomissync.model.History
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomissync.model.HistoryQuestion
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomissync.model.HistoryResponse
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomissync.model.IncidentAgencyId
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomissync.model.IncidentIdResponse
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomissync.model.IncidentResponse
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomissync.model.IncidentsReconciliationResponse
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomissync.model.Offender
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomissync.model.OffenderParty
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomissync.model.Question
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomissync.model.Requirement
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomissync.model.Response
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomissync.model.Staff
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomissync.model.StaffParty
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.service.RestResponsePage
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.service.typeReference
import java.time.LocalDate

@Service
class IncidentsNomisApiService(@Qualifier("nomisApiWebClient") private val webClient: WebClient) {

  suspend fun getIncident(incidentId: Long): IncidentResponse =
    webClient.get()
      .uri("/incidents/{incidentId}", incidentId)
      .retrieve()
      .bodyToMono(IncidentResponse::class.java)
      .awaitSingle()

  suspend fun getIncidentOrNull(incidentId: Long): IncidentResponse? =
    webClient.get()
      .uri("/incidents/{incidentId}", incidentId)
      .retrieve()
      .awaitBodyOrNullWhenNotFound()

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

  suspend fun getAllAgencies(): List<IncidentAgencyId> =
    webClient.get()
      .uri("/incidents/reconciliation/agencies")
      .retrieve()
      .awaitBody()

  suspend fun getIncidentsReconciliation(agencyId: String): IncidentsReconciliationResponse =
    webClient.get()
      .uri("/incidents/reconciliation/agency/{agencyId}/counts", agencyId)
      .retrieve()
      .awaitBody()

  suspend fun getOpenIncidentIds(
    agencyId: String,
    pageNumber: Long,
    pageSize: Long,
  ): PageImpl<IncidentIdResponse> =
    webClient.get()
      .uri {
        it.path("/incidents/reconciliation/agency/{agencyId}/ids")
          .queryParam("page", pageNumber)
          .queryParam("size", pageSize)
          .build(agencyId)
      }
      .retrieve()
      .bodyToMono(typeReference<RestResponsePage<IncidentIdResponse>>())
      .awaitSingle()
}

fun IncidentResponse.toMigrateUpsertNomisIncident() =
  NomisSyncRequest(
    id = null,
    initialMigration = true,
    incidentReport = toNomisIncidentReport(),
  )

fun IncidentResponse.toNomisIncidentReport() =
  NomisReport(
    incidentId = incidentId,
    questionnaireId = questionnaireId,
    prison = agency.toUpsertCodeDescription(),
    status = NomisStatus(status.code, status.description),
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
    followUpDate = followUpDate,
    createdBy = createdBy,
    createDateTime = createDateTime,
    lastModifiedBy = lastModifiedBy,
    lastModifiedDateTime = lastModifiedDateTime,
  )

fun CodeDescription.toUpsertCodeDescription() =
  NomisCode(
    code = code,
    description = description,
  )

fun Staff.toUpsertStaff() =
  NomisStaff(
    username = username,
    staffId = staffId,
    firstName = firstName,
    lastName = lastName,
  )

fun StaffParty.toUpsertStaffParty() =
  NomisStaffParty(
    staff = staff.toUpsertStaff(),
    role = role.toUpsertCodeDescription(),
    comment = comment,
    createdBy = createdBy,
    createDateTime = createDateTime,
    lastModifiedBy = lastModifiedBy,
    lastModifiedDateTime = lastModifiedDateTime,
  )

fun OffenderParty.toUpsertOffenderParty() =
  NomisOffenderParty(
    offender = offender.toUpsertOffender(),
    role = role.toUpsertCodeDescription(),
    outcome = outcome?.toUpsertCodeDescription(),
    comment = comment,
    createdBy = createdBy,
    createDateTime = createDateTime,
    lastModifiedBy = lastModifiedBy,
    lastModifiedDateTime = lastModifiedDateTime,
  )

fun Offender.toUpsertOffender() =
  NomisOffender(
    offenderNo = offenderNo,
    firstName = firstName,
    lastName = lastName,
  )

fun Requirement.toUpsertRequirement() =
  NomisRequirement(
    date = date,
    staff = staff.toUpsertStaff(),
    prisonId = agencyId,
    comment = comment,
    createdBy = createdBy,
    createDateTime = createDateTime,
    lastModifiedBy = lastModifiedBy,
    lastModifiedDateTime = lastModifiedDateTime,
  )

fun Question.toUpsertQuestion() =
  NomisQuestion(
    questionId = questionId,
    sequence = sequence,
    question = question,
    answers = answers.map { it.toUpsertResponse() },
    createdBy = createdBy,
    createDateTime = createDateTime,
  )

fun Response.toUpsertResponse() =
  NomisResponse(
    sequence = sequence,
    recordingStaff = recordingStaff.toUpsertStaff(),
    questionResponseId = questionResponseId,
    answer = answer,
    comment = comment,
    responseDate = responseDate,
    createdBy = createdBy,
    createDateTime = createDateTime,
    lastModifiedBy = lastModifiedBy,
    lastModifiedDateTime = lastModifiedDateTime,
  )

fun History.toUpsertHistory() =
  NomisHistory(
    questionnaireId = questionnaireId,
    type = type,
    questions = questions.map { it.toUpsertHistoryQuestion() },
    incidentChangeDate = incidentChangeDate,
    incidentChangeStaff = incidentChangeStaff.toUpsertStaff(),
    description = description,
    createdBy = createdBy,
    createDateTime = createDateTime,
  )

fun HistoryQuestion.toUpsertHistoryQuestion() =
  NomisHistoryQuestion(
    questionId = questionId,
    sequence = sequence,
    question = question,
    answers = answers.map { it.toUpsertHistoryResponse() },
  )

fun HistoryResponse.toUpsertHistoryResponse() =
  NomisHistoryResponse(
    responseSequence = responseSequence,
    recordingStaff = recordingStaff.toUpsertStaff(),
    questionResponseId = questionResponseId,
    answer = answer,
    comment = comment,
  )
