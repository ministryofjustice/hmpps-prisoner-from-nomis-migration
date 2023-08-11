package uk.gov.justice.digital.hmpps.prisonerfromnomismigration.adjudications

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.adjudications.model.MigrateDamage.DamageType
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.adjudications.model.MigrateEvidence
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomissync.model.AdjudicationCharge
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomissync.model.AdjudicationChargeResponse
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomissync.model.AdjudicationIncident
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomissync.model.AdjudicationOffence
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomissync.model.CodeDescription
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomissync.model.Evidence
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomissync.model.InternalLocation
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomissync.model.Investigation
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomissync.model.Prisoner
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomissync.model.Repair
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomissync.model.Staff
import java.time.LocalDate
import java.time.LocalTime

class AdjudicationTransformationTest {
  @Test
  fun `will copy core identifiers`() {
    val nomisAdjudication =
      nomisAdjudicationCharge(adjudicationNumber = 654321, chargeSequence = 2, adjudicationIncidentId = 45453)
    val dpsAdjudication = nomisAdjudication.toAdjudication()

    assertThat(dpsAdjudication.oicIncidentId).isEqualTo(654321)
    assertThat(dpsAdjudication.offenceSequence).isEqualTo(2)
    assertThat(dpsAdjudication.agencyIncidentId).isEqualTo(45453)
  }

  @Test
  fun `will copy prisoner details`() {
    val nomisAdjudication = nomisAdjudicationCharge(
      offenderNo = "A1234AA",
      bookingId = 543231,
      genderCode = "F",
      currentPrison = CodeDescription("WWI", "Wandsworth (HMP)"),
    )
    val dpsAdjudication = nomisAdjudication.toAdjudication()

    assertThat(dpsAdjudication.prisoner.prisonerNumber).isEqualTo("A1234AA")
    assertThat(dpsAdjudication.bookingId).isEqualTo(543231)
    assertThat(dpsAdjudication.prisoner.gender).isEqualTo("F")
    assertThat(dpsAdjudication.prisoner.currentAgencyId).isEqualTo("WWI")

    assertThat(nomisAdjudicationCharge(currentPrison = null).toAdjudication().prisoner.currentAgencyId).isNull()
  }

  @Test
  fun `will copy the incident location`() {
    val nomisAdjudication = nomisAdjudicationCharge(prisonId = "MDI", internalLocationId = 1234567)
    val dpsAdjudication = nomisAdjudication.toAdjudication()

    assertThat(dpsAdjudication.agencyId).isEqualTo("MDI")
    assertThat(dpsAdjudication.locationId).isEqualTo(1234567)
  }

  @Test
  fun `will copy incident dates`() {
    val nomisAdjudication =
      nomisAdjudicationCharge(
        incidentDate = LocalDate.parse("2020-12-25"),
        incidentTime = LocalTime.parse("12:34"),
        reportedDate = LocalDate.parse("2020-12-26"),
        reportedTime = LocalTime.parse("09:10"),
      )
    val dpsAdjudication = nomisAdjudication.toAdjudication()

    assertThat(dpsAdjudication.reportedDateTime).isEqualTo("2020-12-26T09:10:00")
    assertThat(dpsAdjudication.incidentDateTime).isEqualTo("2020-12-25T12:34:00")
  }

  @Test
  fun `will copy the statement`() {
    val nomisAdjudication =
      nomisAdjudicationCharge(statementDetails = "Governor approximately 09:10 on the 23.07.2023 whilst completing enhanced AFC’s on cell K-02-25 dually occupied by Offender Bobby A9999DV and Simon A8888EK, whilst completing a rub down search of offender Marke A6543DV a small piece of paper was removed from his pocket along with a vape pen with paper on it was taken and placed in evidence bag M16824213. When these items were tested on the Rapiscan these tested positive for Spice 9. BWVC 655432 was active throughout, for this reason, I am placing Offender Bob on report.")
    val dpsAdjudication = nomisAdjudication.toAdjudication()

    assertThat(dpsAdjudication.statement).isEqualTo("Governor approximately 09:10 on the 23.07.2023 whilst completing enhanced AFC’s on cell K-02-25 dually occupied by Offender Bobby A9999DV and Simon A8888EK, whilst completing a rub down search of offender Marke A6543DV a small piece of paper was removed from his pocket along with a vape pen with paper on it was taken and placed in evidence bag M16824213. When these items were tested on the Rapiscan these tested positive for Spice 9. BWVC 655432 was active throughout, for this reason, I am placing Offender Bob on report.")
  }

  @Nested
  inner class DamageRepairs {
    @Test
    fun `will copy multiple repairs`() {
      val nomisAdjudication = nomisAdjudicationCharge(
        repairs = listOf(
          Repair(
            type = CodeDescription(code = "PLUM", description = "Plumbing"),
            comment = "Broken toilet",
            createdByUsername = "A.BEANS",
          ),
          Repair(
            type = CodeDescription(code = "PLUM", description = "Plumbing"),
            comment = "Broken sink",
            createdByUsername = "B.STUFF",
          ),
        ),
      )
      val dpsAdjudication = nomisAdjudication.toAdjudication()

      assertThat(dpsAdjudication.damages).hasSize(2)
    }

    @Test
    fun `will copy details`() {
      val nomisAdjudication = nomisAdjudicationCharge(
        repairs = listOf(
          Repair(
            type = CodeDescription(code = "PLUM", description = "Plumbing"),
            comment = "Broken toilet",
            createdByUsername = "A.BEANS",
          ),
        ),
      )
      val dpsAdjudication = nomisAdjudication.toAdjudication()

      assertThat(dpsAdjudication.damages).hasSize(1)
      assertThat(dpsAdjudication.damages[0].details).isEqualTo("Broken toilet")
      assertThat(dpsAdjudication.damages[0].createdBy).isEqualTo("A.BEANS")
    }

    @Test
    fun `damage type is mapped`() {
      val nomisAdjudication = nomisAdjudicationCharge(
        repairs = listOf(
          Repair(
            type = CodeDescription(code = "ELEC", description = "Electrical"),
            createdByUsername = "A.BEANS",
          ),
          Repair(type = CodeDescription(code = "PLUM", description = "Plumbing"), createdByUsername = "A.BEANS"),
          Repair(type = CodeDescription(code = "DECO", description = "Re-Decoration"), createdByUsername = "A.BEANS"),
          Repair(type = CodeDescription(code = "FABR", description = "Fabric"), createdByUsername = "A.BEANS"),
          Repair(type = CodeDescription(code = "CLEA", description = "Cleaning"), createdByUsername = "A.BEANS"),
          Repair(type = CodeDescription(code = "LOCK", description = "Lock"), createdByUsername = "A.BEANS"),
        ),
      )
      val dpsAdjudication = nomisAdjudication.toAdjudication()

      assertThat(dpsAdjudication.damages).hasSize(6)
      assertThat(dpsAdjudication.damages[0].damageType).isEqualTo(DamageType.ELECTRICAL_REPAIR)
      assertThat(dpsAdjudication.damages[1].damageType).isEqualTo(DamageType.PLUMBING_REPAIR)
      assertThat(dpsAdjudication.damages[2].damageType).isEqualTo(DamageType.REDECORATION)
      assertThat(dpsAdjudication.damages[3].damageType).isEqualTo(DamageType.FURNITURE_OR_FABRIC_REPAIR)
      assertThat(dpsAdjudication.damages[4].damageType).isEqualTo(DamageType.CLEANING)
      assertThat(dpsAdjudication.damages[5].damageType).isEqualTo(DamageType.LOCK_REPAIR)
    }
  }

  @Nested
  inner class InvestigationEvidence {
    @Test
    fun `will copy multiple evidence from multiple investigations`() {
      val nomisAdjudication = nomisAdjudicationCharge(
        investigations = listOf(
          Investigation(
            investigator = Staff(username = "J.SMITH", staffId = 1, firstName = "John", lastName = "Smith"),
            dateAssigned = LocalDate.parse("2020-12-25"),
            comment = "some comment",
            evidence = listOf(
              Evidence(
                type = CodeDescription(code = "BEHAV", description = "Behaviour Report"),
                date = LocalDate.parse("2020-12-25"),
                detail = "report detail",
                createdByUsername = "A.BEANS",
              ),
              Evidence(
                type = CodeDescription(code = "WITNESS", description = "Witness Statement"),
                date = LocalDate.parse("2020-12-26"),
                detail = "witness statement",
                createdByUsername = "A.BEANS",
              ),
            ),
          ),

          Investigation(
            investigator = Staff(username = "D.ABBOY", staffId = 67839, firstName = "DIKBLISNG", lastName = "ABBOY"),
            dateAssigned = LocalDate.parse("2023-08-07"),
            comment = "another comment",
            evidence = listOf(
              Evidence(
                type = CodeDescription(code = "BEHAV", description = "Behaviour Report"),
                date = LocalDate.parse("2021-12-25"),
                detail = "another behave report",
                createdByUsername = "A.BEANS",
              ),
            ),
          ),
        ),
      )
      val dpsAdjudication = nomisAdjudication.toAdjudication()

      assertThat(dpsAdjudication.evidence).hasSize(3)
    }

    @Test
    fun `will copy evidence details`() {
      val nomisAdjudication = nomisAdjudicationCharge(
        investigations = listOf(
          Investigation(
            investigator = Staff(username = "J.SMITH", staffId = 1, firstName = "John", lastName = "Smith"),
            dateAssigned = LocalDate.parse("2020-12-25"),
            comment = "some comment",
            evidence = listOf(
              Evidence(
                type = CodeDescription(code = "BEHAV", description = "Behaviour Report"),
                date = LocalDate.parse("2020-12-25"),
                detail = "report detail",
                createdByUsername = "A.BEANS",
              ),
            ),
          ),
        ),
      )

      val dpsAdjudication = nomisAdjudication.toAdjudication()

      assertThat(dpsAdjudication.evidence).hasSize(1)
      assertThat(dpsAdjudication.evidence[0].details).isEqualTo("report detail")
      assertThat(dpsAdjudication.evidence[0].reporter).isEqualTo("A.BEANS")
    }

    @Test
    fun `will map evidence type`() {
      val nomisAdjudication = nomisAdjudicationCharge(
        investigations = listOf(
          Investigation(
            investigator = Staff(username = "J.SMITH", staffId = 1, firstName = "John", lastName = "Smith"),
            dateAssigned = LocalDate.parse("2020-12-25"),
            comment = "some comment",
            evidence = listOf(
              Evidence(
                type = CodeDescription(code = "BEHAV", description = "Behaviour Report"),
                date = LocalDate.now(),
                detail = "detail",
                createdByUsername = "A.BEANS",
              ),
              Evidence(
                type = CodeDescription(code = "WITNESS", description = "Witness Statement"),
                date = LocalDate.now(),
                detail = "detail",
                createdByUsername = "A.BEANS",
              ),
              Evidence(
                type = CodeDescription(code = "VICTIM", description = "Victim Statement"),
                date = LocalDate.now(),
                detail = "detail",
                createdByUsername = "A.BEANS",
              ),
              Evidence(
                type = CodeDescription(code = "WEAP", description = "Weapon"),
                date = LocalDate.now(),
                detail = "detail",
                createdByUsername = "A.BEANS",
              ),
              Evidence(
                type = CodeDescription(code = "PHOTO", description = "Photographic Evidence"),
                date = LocalDate.now(),
                detail = "detail",
                createdByUsername = "A.BEANS",
              ),
              Evidence(
                type = CodeDescription(code = "DRUGTEST", description = "Drug Test Report"),
                date = LocalDate.now(),
                detail = "detail",
                createdByUsername = "A.BEANS",
              ),
              Evidence(
                type = CodeDescription(code = "EVI_BAG", description = "Evidence Bag"),
                date = LocalDate.now(),
                detail = "detail",
                createdByUsername = "A.BEANS",
              ),
              Evidence(
                type = CodeDescription(code = "OTHER", description = "Other"),
                date = LocalDate.now(),
                detail = "detail",
                createdByUsername = "A.BEANS",
              ),
            ),
          ),
        ),
      )
      val dpsAdjudication = nomisAdjudication.toAdjudication()

      assertThat(dpsAdjudication.evidence).hasSize(8)
      assertThat(dpsAdjudication.evidence[0].evidenceCode).isEqualTo(MigrateEvidence.EvidenceCode.CCTV) // TODO
      assertThat(dpsAdjudication.evidence[1].evidenceCode).isEqualTo(MigrateEvidence.EvidenceCode.CCTV) // TODO
      assertThat(dpsAdjudication.evidence[2].evidenceCode).isEqualTo(MigrateEvidence.EvidenceCode.CCTV) // TODO
      assertThat(dpsAdjudication.evidence[3].evidenceCode).isEqualTo(MigrateEvidence.EvidenceCode.CCTV) // TODO
      assertThat(dpsAdjudication.evidence[4].evidenceCode).isEqualTo(MigrateEvidence.EvidenceCode.PHOTO)
      assertThat(dpsAdjudication.evidence[5].evidenceCode).isEqualTo(MigrateEvidence.EvidenceCode.CCTV) // TODO
      assertThat(dpsAdjudication.evidence[6].evidenceCode).isEqualTo(MigrateEvidence.EvidenceCode.BAGGED_AND_TAGGED)
      assertThat(dpsAdjudication.evidence[7].evidenceCode).isEqualTo(MigrateEvidence.EvidenceCode.CCTV) // TODO
    }
  }

  @Test
  fun `will copy offence code`() {
    val nomisAdjudication = nomisAdjudicationCharge(offenceCode = "51:1J", offenceDescription = "Commits any assault - assault on prison officer")
    val dpsAdjudication = nomisAdjudication.toAdjudication()

    assertThat(dpsAdjudication.offence.offenceCode).isEqualTo("51:1J")
    assertThat(dpsAdjudication.offence.offenceDescription).isEqualTo("Commits any assault - assault on prison officer")
  }
}

private fun nomisAdjudicationCharge(
  adjudicationNumber: Long = 1234567,
  chargeSequence: Int = 1,
  offenderNo: String = "A1234AA",
  bookingId: Long = 543321,
  adjudicationIncidentId: Long = 8765432,
  incidentDate: LocalDate = LocalDate.now(),
  incidentTime: LocalTime = LocalTime.now(),
  reportedDate: LocalDate = LocalDate.now(),
  reportedTime: LocalTime = LocalTime.now(),
  internalLocationId: Long = 1234567,
  prisonId: String = "MDI",
  prisonerWitnessOffenderNumbers: List<String> = emptyList(),
  prisonerVictimOffenderNumbers: List<String> = emptyList(),
  otherPrisonerInvolvedOffenderNumbers: List<String> = emptyList(),
  reportingOfficersUsernames: List<String> = emptyList(),
  staffWitnessUsernames: List<String> = emptyList(),
  staffVictimsUsernames: List<String> = emptyList(),
  otherStaffInvolvedUsernames: List<String> = emptyList(),
  repairs: List<Repair> = emptyList(),
  investigations: List<Investigation> = emptyList(),
  statementDetails: String = "Fight",
  offenceCode: String = "51:12A",
  offenceDescription: String = "Commits any assault - assault on prison officer",
  offenceType: String = "51",
  genderCode: String = "F",
  currentPrison: CodeDescription? = CodeDescription(prisonId, "HMP Prison"),
): AdjudicationChargeResponse {
  return AdjudicationChargeResponse(
    adjudicationSequence = chargeSequence,
    offenderNo = offenderNo,
    bookingId = bookingId,
    partyAddedDate = LocalDate.now(),
    gender = CodeDescription(genderCode, "Female"),
    currentPrison = currentPrison,
    incident = AdjudicationIncident(
      adjudicationIncidentId = adjudicationIncidentId,
      reportingStaff = Staff(
        username = "F.LAST",
        staffId = 1,
        firstName = "stafffirstname",
        lastName = "stafflastname",
      ),
      incidentDate = incidentDate,
      incidentTime = incidentTime.toString(),
      reportedDate = reportedDate,
      reportedTime = reportedTime.toString(),
      createdByUsername = "A.BEANS",
      createdDateTime = "2023-04-12T10:00:00",
      internalLocation = InternalLocation(internalLocationId, "GYM", "GYM"),
      prison = CodeDescription(prisonId, "HMP Prison"),
      prisonerWitnesses = prisonerWitnessOffenderNumbers.map {
        Prisoner(
          it,
          lastName = "SURNAME",
          firstName = "FIRSTNAME",
        )
      },
      prisonerVictims = prisonerVictimOffenderNumbers.map {
        Prisoner(
          it,
          lastName = "SURNAME",
          firstName = "FIRSTNAME",
        )
      },
      incidentType = CodeDescription("GOV", "Governor's Report"),
      otherPrisonersInvolved = otherPrisonerInvolvedOffenderNumbers.map {
        Prisoner(
          it,
          lastName = "SURNAME",
          firstName = "FIRSTNAME",
        )
      },
      reportingOfficers = emptyList(), // TODO - need usernames
      staffWitnesses = emptyList(), // TODO - need usernames
      staffVictims = emptyList(), // TODO - need usernames
      otherStaffInvolved = emptyList(), // TODO - need usernames
      repairs = repairs,
      details = statementDetails,
    ),
    charge = AdjudicationCharge(
      chargeSequence = chargeSequence,
      evidence = "some evidence",
      reportDetail = "some report detail",
      offenceId = "$adjudicationNumber/$chargeSequence",
      offence = AdjudicationOffence(
        code = offenceCode,
        description = offenceDescription,
        type = CodeDescription(offenceType, "Prison Rule $offenceType"),
      ),
    ),
    investigations = investigations,
    hearings = emptyList(),
    adjudicationNumber = adjudicationNumber,
    comment = "comment",
  )
}
