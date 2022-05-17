package uk.gov.justice.digital.hmpps.prisonerfromnomismigration.visits

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.service.NomisCodeDescription
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.service.NomisVisit
import java.time.LocalDateTime

internal class VisitMapperTest {

  @Test
  internal fun `will return a valid outcome mapping `() {
  }

  @Test
  internal fun `will map correct outcome for visits `() {
    assertThat(getVsipOutcome(createNomisVisit(NomisVisitStatus.CANC.name, NomisCancellationOutcome.ADMIN.name))).isEqualTo(VsipOutcome.ADMINISTRATIVE_CANCELLATION)
    assertThat(getVsipOutcome(createNomisVisit(NomisVisitStatus.CANC.name, NomisCancellationOutcome.HMP.name))).isEqualTo(VsipOutcome.ESTABLISHMENT_CANCELLED)
    assertThat(getVsipOutcome(createNomisVisit(NomisVisitStatus.CANC.name, NomisCancellationOutcome.NO_ID.name))).isEqualTo(VsipOutcome.VISITOR_FAILED_SECURITY_CHECKS)
    assertThat(getVsipOutcome(createNomisVisit(NomisVisitStatus.CANC.name, NomisCancellationOutcome.NSHOW.name))).isEqualTo(VsipOutcome.VISITOR_DID_NOT_ARRIVE)
    assertThat(getVsipOutcome(createNomisVisit(NomisVisitStatus.CANC.name, NomisCancellationOutcome.OFFCANC.name))).isEqualTo(VsipOutcome.PRISONER_CANCELLED)
    assertThat(getVsipOutcome(createNomisVisit(NomisVisitStatus.CANC.name, NomisCancellationOutcome.REFUSED.name))).isEqualTo(VsipOutcome.PRISONER_REFUSED_TO_ATTEND)
    assertThat(getVsipOutcome(createNomisVisit(NomisVisitStatus.CANC.name, NomisCancellationOutcome.VISCANC.name))).isEqualTo(VsipOutcome.VISITOR_CANCELLED)
    assertThat(getVsipOutcome(createNomisVisit(NomisVisitStatus.CANC.name, NomisCancellationOutcome.VO_CANCEL.name))).isEqualTo(VsipOutcome.VISIT_ORDER_CANCELLED)
    assertThat(getVsipOutcome(createNomisVisit(NomisVisitStatus.CANC.name, NomisCancellationOutcome.NO_VO.name))).isEqualTo(VsipOutcome.NO_VISITING_ORDER)
    assertThat(getVsipOutcome(createNomisVisit(NomisVisitStatus.CANC.name, NomisCancellationOutcome.BATCH_CANC.name))).isEqualTo(VsipOutcome.BATCH_CANCELLATION)
    assertThat(getVsipOutcome(createNomisVisit(NomisVisitStatus.EXP.name, null))).isEqualTo(null)
    assertThat(getVsipOutcome(createNomisVisit(NomisVisitStatus.HMPOP.name, null))).isEqualTo(VsipOutcome.TERMINATED_BY_STAFF)
    assertThat(getVsipOutcome(createNomisVisit(NomisVisitStatus.NORM.name, null))).isEqualTo(VsipOutcome.COMPLETED_NORMALLY)
    assertThat(getVsipOutcome(createNomisVisit(NomisVisitStatus.SCH.name, null))).isEqualTo(null)
    assertThat(getVsipOutcome(createNomisVisit(NomisVisitStatus.OFFEND.name, null))).isEqualTo(VsipOutcome.PRISONER_COMPLETED_EARLY)
    assertThat(getVsipOutcome(createNomisVisit(NomisVisitStatus.VDE.name, null))).isEqualTo(VsipOutcome.VISITOR_DECLINED_ENTRY)
    assertThat(getVsipOutcome(createNomisVisit(NomisVisitStatus.VISITOR.name, null))).isEqualTo(VsipOutcome.VISITOR_COMPLETED_EARLY)
  }

  @Test
  internal fun `outcome mapped if present for non cancelled status visits`() {
    assertThat(getVsipOutcome(createNomisVisit(NomisVisitStatus.EXP.name, NomisCancellationOutcome.ADMIN.name))).isEqualTo(VsipOutcome.ADMINISTRATIVE_CANCELLATION)
  }

  @Test
  internal fun `will map correct status for visits `() {
    assertThat(getVsipVisitStatus(createNomisVisit(NomisVisitStatus.CANC.name, null))).isEqualTo(VsipStatus.CANCELLED)
    assertThat(getVsipVisitStatus(createNomisVisit(NomisVisitStatus.EXP.name, null))).isEqualTo(VsipStatus.BOOKED)
    assertThat(getVsipVisitStatus(createNomisVisit(NomisVisitStatus.HMPOP.name, null))).isEqualTo(VsipStatus.BOOKED)
    assertThat(getVsipVisitStatus(createNomisVisit(NomisVisitStatus.NORM.name, null))).isEqualTo(VsipStatus.BOOKED)
    assertThat(getVsipVisitStatus(createNomisVisit(NomisVisitStatus.SCH.name, null))).isEqualTo(VsipStatus.BOOKED)
    assertThat(getVsipVisitStatus(createNomisVisit(NomisVisitStatus.OFFEND.name, null))).isEqualTo(VsipStatus.BOOKED)
    assertThat(getVsipVisitStatus(createNomisVisit(NomisVisitStatus.VDE.name, null))).isEqualTo(VsipStatus.BOOKED)
    assertThat(getVsipVisitStatus(createNomisVisit(NomisVisitStatus.VISITOR.name, null))).isEqualTo(VsipStatus.BOOKED)
  }

  @Test
  internal fun `invalid status mapped to vsip BOOKED`() {
    assertThat(getVsipVisitStatus(createNomisVisit("chair", null))).isEqualTo(VsipStatus.BOOKED)
  }

  @Test
  internal fun `invalid outcome rejected`() {
    val thrown = assertThrows(java.lang.IllegalArgumentException::class.java) {
      getVsipOutcome(createNomisVisit(NomisVisitStatus.OFFEND.name, "Not a valid outcome"))
    }
    assertThat(thrown.message).isEqualTo("No enum constant uk.gov.justice.digital.hmpps.prisonerfromnomismigration.visits.NomisCancellationOutcome.Not a valid outcome")
  }

  fun createNomisVisit(nomisStatusCode: String, nomisOutcomeCode: String?): NomisVisit {
    return NomisVisit(
      visitId = 1L,
      offenderNo = "123",
      startDateTime = LocalDateTime.now(),
      endDateTime = LocalDateTime.now(),
      prisonId = "LEI",
      visitors = listOf(),
      visitType = NomisCodeDescription("SCON", "Social"),
      visitStatus = NomisCodeDescription(nomisStatusCode, nomisStatusCode),
      visitOutcome = nomisOutcomeCode?.let { NomisCodeDescription(nomisOutcomeCode, nomisOutcomeCode) },
      agencyInternalLocation = NomisCodeDescription("LEI", "desc")
    )
  }
}
