package uk.gov.justice.digital.hmpps.prisonerfromnomismigration.officialvisits

import kotlinx.coroutines.test.runTest
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.MethodSource
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomisprisoner.model.CodeDescription
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.officialvisits.OfficialVisitsNomisApiMockServer.Companion.officialVisitResponse
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.officialvisits.model.VisitCompletionType
import java.util.*
import java.util.stream.Stream

class OfficialVisitsMigrationServiceTest {
  @TestInstance(TestInstance.Lifecycle.PER_CLASS)
  @Nested
  inner class ToMigrateVisitRequest {

    @Test
    fun willMapStatusAndCancellationReasonToVisitCompletionCode() = runTest {
      val nomisVisit = officialVisitResponse().copy(visitStatus = CodeDescription(code = "CANC", description = "Cancelled"), cancellationReason = CodeDescription(code = "OFFCANC", description = "Offender Cancelled"))
      assertThat(
        nomisVisit.toMigrateVisitRequest(
          prisonVisitSlotLookup = { 1L },
          dpsLocationLookup = { UUID.randomUUID() },
        ).visitCompletionCode,
      ).isEqualTo(VisitCompletionType.PRISONER_CANCELLED)
    }

    fun bindings(): Stream<Arguments> = Stream.of(
      Arguments.of(CodeDescription(code = "CANC", description = "Cancelled"), CodeDescription(code = "NO_VO", description = "No Visiting Order"), VisitCompletionType.STAFF_CANCELLED),
      Arguments.of(CodeDescription(code = "CANC", description = "Cancelled"), CodeDescription(code = "REFUSED", description = "Offender Refused Visit"), VisitCompletionType.PRISONER_REFUSED),
      Arguments.of(CodeDescription(code = "CANC", description = "Cancelled"), CodeDescription(code = "OFFCANC", description = "Offender Cancelled"), VisitCompletionType.PRISONER_CANCELLED),
      Arguments.of(CodeDescription(code = "CANC", description = "Cancelled"), CodeDescription(code = "VO_CANCEL", description = "Visit Order Cancelled"), VisitCompletionType.STAFF_CANCELLED),
      Arguments.of(CodeDescription(code = "CANC", description = "Cancelled"), CodeDescription(code = "ADMIN_CANCEL", description = "ADMIN_CANCEL"), VisitCompletionType.STAFF_CANCELLED),
      Arguments.of(CodeDescription(code = "CANC", description = "Cancelled"), CodeDescription(code = "OFFCANC", description = "Offender Cancelled"), VisitCompletionType.PRISONER_CANCELLED),
      Arguments.of(CodeDescription(code = "CANC", description = "Cancelled"), null, null),
      Arguments.of(CodeDescription(code = "CANC", description = "Cancelled"), CodeDescription(code = "VISCANC", description = "Visitor Cancelled"), VisitCompletionType.VISITOR_CANCELLED),
      Arguments.of(CodeDescription(code = "CANC", description = "Cancelled"), CodeDescription(code = "NSHOW", description = "Visitor Did Not Arrive"), VisitCompletionType.VISITOR_NO_SHOW),
      Arguments.of(CodeDescription(code = "CANC", description = "Cancelled"), CodeDescription(code = "ADMIN", description = "Administrative Cancellation"), VisitCompletionType.STAFF_CANCELLED),
      Arguments.of(CodeDescription(code = "CANC", description = "Cancelled"), CodeDescription(code = "BATCH_CANC", description = "BATCH_CANC"), VisitCompletionType.STAFF_CANCELLED),
      Arguments.of(CodeDescription(code = "CANC", description = "Cancelled"), CodeDescription(code = "HMP", description = "Operational Reasons-All Visits Cancelled"), VisitCompletionType.STAFF_CANCELLED),
      Arguments.of(CodeDescription(code = "CANC", description = "Cancelled"), CodeDescription(code = "NO_ID", description = "No Identification - Refused Entry"), VisitCompletionType.VISITOR_DENIED),
      Arguments.of(CodeDescription(code = "EXP", description = "Expired"), null, null),
      Arguments.of(CodeDescription(code = "HMPOP", description = "Terminated By Staff"), null, VisitCompletionType.STAFF_EARLY),
      Arguments.of(CodeDescription(code = "NORM", description = "Normal Completion"), CodeDescription(code = "BATCH_CANC", description = "BATCH_CANC"), VisitCompletionType.STAFF_CANCELLED),
      Arguments.of(CodeDescription(code = "NORM", description = "Normal Completion"), null, null),
      Arguments.of(CodeDescription(code = "OFFEND", description = "Offender Completed Early"), null, VisitCompletionType.PRISONER_EARLY),
      Arguments.of(CodeDescription(code = "SCH", description = "Scheduled"), null, null),
      Arguments.of(CodeDescription(code = "VDE", description = "Visitor Declined Entry"), null, VisitCompletionType.VISITOR_DENIED),
      Arguments.of(CodeDescription(code = "VISITOR", description = "Visitor Completed Early"), null, VisitCompletionType.VISITOR_EARLY),
    )

    @ParameterizedTest
    @MethodSource("bindings")
    fun willMapStatusAndCancellationReasonToVisitCompletionCode(visitStatus: CodeDescription, cancellationReason: CodeDescription?, completionCode: VisitCompletionType?) = runTest {
      val nomisVisit = officialVisitResponse().copy(visitStatus = visitStatus, cancellationReason = cancellationReason)
      assertThat(
        nomisVisit.toMigrateVisitRequest(
          prisonVisitSlotLookup = { 1L },
          dpsLocationLookup = { UUID.randomUUID() },
        ).visitCompletionCode,
      ).isEqualTo(completionCode)
    }
  }
}
