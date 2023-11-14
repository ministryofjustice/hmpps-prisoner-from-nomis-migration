package uk.gov.justice.digital.hmpps.prisonerfromnomismigration.adjudications

import kotlinx.coroutines.test.runTest
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.context.annotation.Import
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.helper.SpringAPIServiceTest
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomissync.model.AdjudicationCharge
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomissync.model.AdjudicationChargeResponse
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomissync.model.AdjudicationIncident
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomissync.model.AdjudicationOffence
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomissync.model.CodeDescription
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomissync.model.InternalLocation
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomissync.model.Staff
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.wiremock.MappingApiExtension.Companion.mappingApi
import java.time.LocalDate

@SpringAPIServiceTest
@Import(AdjudicationMappingCreator::class, AdjudicationsConfiguration::class, AdjudicationsService::class, AdjudicationsMappingService::class)
class AdjudicationMappingCreatorTest {
  @Autowired
  private lateinit var adjudicationMappingCreator: AdjudicationMappingCreator

  @Nested
  inner class WhenMappingAlreadyExists {
    @BeforeEach
    fun setUp() {
      mappingApi.stubFindNomisMapping(adjudicationNumber = 12345, chargeSequence = 1)
    }

    @Test
    fun `will return null when mapping already created`() = runTest {
      val mapping = adjudicationMappingCreator.createMigrationMappingResponse(
        nomisAdjudication(
          adjudicationNumber = 12345,
          chargeSequence = 1,
        ),
      )
      assertThat(mapping).isNull()
    }
  }

  private fun nomisAdjudication(adjudicationNumber: Long, chargeSequence: Int): AdjudicationChargeResponse {
    return AdjudicationChargeResponse(
      adjudicationSequence = chargeSequence,
      adjudicationNumber = adjudicationNumber,
      incident = AdjudicationIncident(
        adjudicationIncidentId = 98765L,
        reportingStaff = Staff(
          username = "LQY66C",
          staffId = 12643L,
          firstName = "SUZANNE",
          lastName = "O'BOYLE",
          createdByUsername = "PQY66C",
          dateAddedToIncident = LocalDate.now(),
        ),
        incidentDate = LocalDate.now(),
        incidentTime = "08:00:00",
        reportedDate = LocalDate.now(),
        reportedTime = "08:00:00",
        createdByUsername = "LQY66C",
        createdDateTime = "2014-02-21T08:03:11.145714",
        internalLocation = InternalLocation(
          locationId = 14447L,
          code = "CWING",
          description = "LEI-RES-CWING-CWING",
        ),
        incidentType = CodeDescription(
          code = "GOV",
          description = "Governor's Report",
        ),
        details = "Fight",
        prison = CodeDescription(
          code = "LEI",
          description = "LEEDS (HMP)",
        ),
        prisonerWitnesses = listOf(),
        prisonerVictims = listOf(),
        otherPrisonersInvolved = listOf(),
        reportingOfficers = listOf(),
        staffWitnesses = listOf(),
        staffVictims = listOf(),
        otherStaffInvolved = listOf(),
        repairs = listOf(),

      ),
      bookingId = 12345L,
      offenderNo = "A1234KL",
      gender = CodeDescription("F", "Female"),
      partyAddedDate = LocalDate.now(),
      charge = AdjudicationCharge(
        offence = AdjudicationOffence("51:22", description = "Disobeys any lawful order"),
        chargeSequence = chargeSequence,
      ),
      investigations = listOf(),
      hearings = listOf(),
    )
  }
}
