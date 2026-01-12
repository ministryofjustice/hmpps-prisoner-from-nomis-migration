package uk.gov.justice.digital.hmpps.prisonerfromnomismigration.incidents

import kotlinx.coroutines.reactor.awaitSingle
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.data.domain.PageImpl
import org.springframework.stereotype.Service
import org.springframework.web.reactive.function.client.WebClient
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
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.incidents.model.PairStringListDescriptionAddendum
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomisprisoner.model.CodeDescription
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomisprisoner.model.History
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomisprisoner.model.HistoryQuestion
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomisprisoner.model.HistoryResponse
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomisprisoner.model.IncidentIdResponse
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomisprisoner.model.IncidentResponse
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomisprisoner.model.Offender
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomisprisoner.model.OffenderParty
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomisprisoner.model.Question
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomisprisoner.model.Requirement
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomisprisoner.model.Response
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomisprisoner.model.Staff
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomisprisoner.model.StaffParty
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.service.RestResponsePage
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.service.typeReference
import java.time.LocalDate

@Service
class IncidentsNomisApiService(@Qualifier("nomisApiWebClient") private val webClient: WebClient) {

  suspend fun getIncident(incidentId: Long): IncidentResponse = webClient.get()
    .uri("/incidents/{incidentId}", incidentId)
    .retrieve()
    .bodyToMono(IncidentResponse::class.java)
    .awaitSingle()

  suspend fun getIncidentOrNull(incidentId: Long): IncidentResponse? = webClient.get()
    .uri("/incidents/{incidentId}", incidentId)
    .retrieve()
    .awaitBodyOrNullWhenNotFound()

  suspend fun getIncidentIds(
    fromDate: LocalDate?,
    toDate: LocalDate?,
    pageNumber: Long,
    pageSize: Long,
  ): PageImpl<IncidentIdResponse> = webClient.get()
    .uri {
      it.path("/incidents/ids")
        .apply {
          fromDate?.let { queryParam("fromDate", fromDate) }
          toDate?.let { queryParam("toDate", toDate) }
        }
        .queryParam("page", pageNumber)
        .queryParam("size", pageSize)
        .build()
    }
    .retrieve()
    .bodyToMono(typeReference<RestResponsePage<IncidentIdResponse>>())
    .awaitSingle()
}

fun IncidentResponse.toMigrateUpsertNomisIncident() = NomisSyncRequest(
  id = null,
  initialMigration = true,
  incidentReport = toNomisIncidentReport(),
)

fun IncidentResponse.toNomisIncidentReport() = NomisReport(
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
  descriptionParts = PairStringListDescriptionAddendum("first", listOf()),
)

fun CodeDescription.toUpsertCodeDescription() = NomisCode(
  code = code,
  description = description,
)

fun Staff.toUpsertStaff() = NomisStaff(
  username = username,
  staffId = staffId,
  firstName = firstName,
  lastName = lastName,
)

fun StaffParty.toUpsertStaffParty() = NomisStaffParty(
  staff = staff.toUpsertStaff(),
  sequence = sequence,
  role = role.toUpsertCodeDescription(),
  comment = comment,
  createdBy = createdBy,
  createDateTime = createDateTime,
  lastModifiedBy = lastModifiedBy,
  lastModifiedDateTime = lastModifiedDateTime,
)

fun OffenderParty.toUpsertOffenderParty() = NomisOffenderParty(
  offender = offender.toUpsertOffender(),
  sequence = sequence,
  role = role.toUpsertCodeDescription(),
  outcome = outcome?.toUpsertCodeDescription(),
  comment = comment,
  createdBy = createdBy,
  createDateTime = createDateTime,
  lastModifiedBy = lastModifiedBy,
  lastModifiedDateTime = lastModifiedDateTime,
)

fun Offender.toUpsertOffender() = NomisOffender(
  offenderNo = offenderNo,
  firstName = firstName,
  lastName = lastName,
)

fun Requirement.toUpsertRequirement() = NomisRequirement(
  recordedDate = recordedDate,
  staff = staff.toUpsertStaff(),
  prisonId = agencyId,
  comment = comment,
  sequence = sequence,
  createdBy = createdBy,
  createDateTime = createDateTime,
  lastModifiedBy = lastModifiedBy,
  lastModifiedDateTime = lastModifiedDateTime,
)

fun Question.toUpsertQuestion() = NomisQuestion(
  questionId = questionId,
  sequence = sequence,
  question = question,
  answers = answers.map { it.toUpsertResponse() },
  createdBy = createdBy,
  createDateTime = createDateTime,
)

fun Response.toUpsertResponse() = NomisResponse(
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

fun History.toUpsertHistory() = NomisHistory(
  questionnaireId = questionnaireId,
  type = type,
  questions = questions.map { it.toUpsertHistoryQuestion() },
  incidentChangeDateTime = incidentChangeDateTime,
  incidentChangeStaff = incidentChangeStaff.toUpsertStaff(),
  description = description,
  createdBy = createdBy,
  createDateTime = createDateTime,
)

fun HistoryQuestion.toUpsertHistoryQuestion() = NomisHistoryQuestion(
  questionId = questionId,
  sequence = sequence,
  question = question,
  answers = answers.map { it.toUpsertHistoryResponse() },
)

fun HistoryResponse.toUpsertHistoryResponse() = NomisHistoryResponse(
  responseSequence = responseSequence,
  recordingStaff = recordingStaff.toUpsertStaff(),
  questionResponseId = questionResponseId,
  answer = answer,
  comment = comment,
)
