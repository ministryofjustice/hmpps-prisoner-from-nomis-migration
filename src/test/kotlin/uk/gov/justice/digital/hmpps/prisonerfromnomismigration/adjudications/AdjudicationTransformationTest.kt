package uk.gov.justice.digital.hmpps.prisonerfromnomismigration.adjudications

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.MethodSource
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.adjudications.model.MigrateDamage.DamageType
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.adjudications.model.MigrateEvidence.EvidenceCode
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.adjudications.model.MigrateHearing
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.adjudications.model.MigrateHearing.OicHearingType.GOV
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.adjudications.model.MigrateHearing.OicHearingType.GOV_ADULT
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.adjudications.model.MigrateWitness
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomissync.model.AdjudicationCharge
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomissync.model.AdjudicationChargeResponse
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomissync.model.AdjudicationIncident
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomissync.model.AdjudicationOffence
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomissync.model.CodeDescription
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomissync.model.Evidence
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomissync.model.Hearing
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomissync.model.HearingNotification
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomissync.model.HearingResult
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomissync.model.HearingResultAward
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomissync.model.InternalLocation
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomissync.model.Investigation
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomissync.model.Prisoner
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomissync.model.Repair
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomissync.model.Staff
import java.math.BigDecimal
import java.time.LocalDate
import java.time.LocalTime
import java.util.stream.Stream

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
  fun `will user who created and reported the incident`() {
    val nomisAdjudication =
      nomisAdjudicationCharge(reportingStaffUsername = "F.LAST", createdByStaffUsername = "A.BEANS")
    val dpsAdjudication = nomisAdjudication.toAdjudication()

    assertThat(dpsAdjudication.reportingOfficer.username).isEqualTo("F.LAST")
    assertThat(dpsAdjudication.createdByUsername).isEqualTo("A.BEANS")
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
            cost = BigDecimal("12.34"),
          ),
        ),
      )
      val dpsAdjudication = nomisAdjudication.toAdjudication()

      assertThat(dpsAdjudication.damages).hasSize(1)
      assertThat(dpsAdjudication.damages[0].details).isEqualTo("Broken toilet")
      assertThat(dpsAdjudication.damages[0].createdBy).isEqualTo("A.BEANS")
      assertThat(dpsAdjudication.damages[0].repairCost).isEqualTo(BigDecimal("12.34"))
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
            investigator = Staff(
              username = "J.SMITH",
              staffId = 1,
              firstName = "John",
              lastName = "Smith",
              createdByUsername = "B.BATTS",
            ),
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
            investigator = Staff(
              username = "D.ABBOY",
              staffId = 67839,
              firstName = "DIKBLISNG",
              lastName = "ABBOY",
              createdByUsername = "B.BATTS",
            ),
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
            investigator = Staff(
              username = "J.SMITH",
              staffId = 1,
              firstName = "John",
              lastName = "Smith",
              createdByUsername = "B.BATTS",
            ),
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
      assertThat(dpsAdjudication.evidence[0].dateAdded).isEqualTo("2020-12-25")
    }

    @Test
    fun `will map evidence type`() {
      val nomisAdjudication = nomisAdjudicationCharge(
        investigations = listOf(
          Investigation(
            investigator = Staff(
              username = "J.SMITH",
              staffId = 1,
              firstName = "John",
              lastName = "Smith",
              createdByUsername = "B.BATTS",
            ),
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
      assertThat(dpsAdjudication.evidence[0].evidenceCode).isEqualTo(EvidenceCode.OTHER)
      assertThat(dpsAdjudication.evidence[1].evidenceCode).isEqualTo(EvidenceCode.OTHER)
      assertThat(dpsAdjudication.evidence[2].evidenceCode).isEqualTo(EvidenceCode.OTHER)
      assertThat(dpsAdjudication.evidence[3].evidenceCode).isEqualTo(EvidenceCode.OTHER)
      assertThat(dpsAdjudication.evidence[4].evidenceCode).isEqualTo(EvidenceCode.PHOTO)
      assertThat(dpsAdjudication.evidence[5].evidenceCode).isEqualTo(EvidenceCode.OTHER)
      assertThat(dpsAdjudication.evidence[6].evidenceCode).isEqualTo(EvidenceCode.BAGGED_AND_TAGGED)
      assertThat(dpsAdjudication.evidence[7].evidenceCode).isEqualTo(EvidenceCode.OTHER)
    }

    @Test
    fun `will copy charge evidence details from charge details`() {
      val nomisAdjudication = nomisAdjudicationCharge(
        investigations = emptyList(),
        chargeEvidence = "Broken cup",
        chargeReportDetail = "Smashed to pieces",
        reportedDate = LocalDate.parse("2020-12-26"),
        reportingStaffUsername = "A.CHARGEBEANS",
      )

      val dpsAdjudication = nomisAdjudication.toAdjudication()

      assertThat(dpsAdjudication.evidence).hasSize(1)
      assertThat(dpsAdjudication.evidence[0].details).isEqualTo("Broken cup - Smashed to pieces")
      assertThat(dpsAdjudication.evidence[0].reporter).isEqualTo("A.CHARGEBEANS")
      assertThat(dpsAdjudication.evidence[0].dateAdded).isEqualTo("2020-12-26")
      assertThat(dpsAdjudication.evidence[0].evidenceCode).isEqualTo(EvidenceCode.OTHER)
    }

    @Test
    fun `will copy partial charge evidence details from charge details with evidence`() {
      val nomisAdjudication = nomisAdjudicationCharge(
        investigations = emptyList(),
        chargeEvidence = "Broken cup",
        chargeReportDetail = "",
        reportedDate = LocalDate.parse("2020-12-26"),
        reportingStaffUsername = "A.CHARGEBEANS",
      )

      val dpsAdjudication = nomisAdjudication.toAdjudication()

      assertThat(dpsAdjudication.evidence).hasSize(1)
      assertThat(dpsAdjudication.evidence[0].details).isEqualTo("Broken cup")
      assertThat(dpsAdjudication.evidence[0].reporter).isEqualTo("A.CHARGEBEANS")
      assertThat(dpsAdjudication.evidence[0].dateAdded).isEqualTo("2020-12-26")
      assertThat(dpsAdjudication.evidence[0].evidenceCode).isEqualTo(EvidenceCode.OTHER)
    }

    @Test
    fun `will copy partial charge evidence details from charge details with report deatils`() {
      val nomisAdjudication = nomisAdjudicationCharge(
        investigations = emptyList(),
        chargeEvidence = "",
        chargeReportDetail = "Smashed to pieces",
        reportedDate = LocalDate.parse("2020-12-26"),
        reportingStaffUsername = "A.CHARGEBEANS",
      )

      val dpsAdjudication = nomisAdjudication.toAdjudication()

      assertThat(dpsAdjudication.evidence).hasSize(1)
      assertThat(dpsAdjudication.evidence[0].details).isEqualTo("Smashed to pieces")
      assertThat(dpsAdjudication.evidence[0].reporter).isEqualTo("A.CHARGEBEANS")
      assertThat(dpsAdjudication.evidence[0].dateAdded).isEqualTo("2020-12-26")
      assertThat(dpsAdjudication.evidence[0].evidenceCode).isEqualTo(EvidenceCode.OTHER)
    }
  }

  @Test
  fun `will copy offence code`() {
    val nomisAdjudication = nomisAdjudicationCharge(
      offenceCode = "51:1J",
      offenceDescription = "Commits any assault - assault on prison officer",
    )
    val dpsAdjudication = nomisAdjudication.toAdjudication()

    assertThat(dpsAdjudication.offence.offenceCode).isEqualTo("51:1J")
    assertThat(dpsAdjudication.offence.offenceDescription).isEqualTo("Commits any assault - assault on prison officer")
  }

  @Nested
  @DisplayName("Witnesses and other parties")
  inner class Witnesses {
    @Test
    fun `staff witnesses are copied`() {
      val nomisAdjudication = nomisAdjudicationCharge(
        createdByStaffUsername = "A.BEANS",
        staffWitness = listOf(
          Staff(
            username = "J.SMITH",
            staffId = 1,
            firstName = "JOHN",
            lastName = "SMITH",
            createdByUsername = "B.BATTS",
          ),
          Staff(
            username = "K.KOFI",
            staffId = 2,
            firstName = "KWEKU",
            lastName = "KOFI",
            createdByUsername = "J.TOMS",
          ),
        ),
      )
      val dpsAdjudication = nomisAdjudication.toAdjudication()

      assertThat(dpsAdjudication.witnesses).hasSize(2)
      assertThat(dpsAdjudication.witnesses[0].createdBy).isEqualTo("B.BATTS")
      assertThat(dpsAdjudication.witnesses[0].firstName).isEqualTo("JOHN")
      assertThat(dpsAdjudication.witnesses[0].lastName).isEqualTo("SMITH")
      assertThat(dpsAdjudication.witnesses[0].witnessType).isEqualTo(MigrateWitness.WitnessType.STAFF)

      assertThat(dpsAdjudication.witnesses[1].createdBy).isEqualTo("J.TOMS")
      assertThat(dpsAdjudication.witnesses[1].firstName).isEqualTo("KWEKU")
      assertThat(dpsAdjudication.witnesses[1].lastName).isEqualTo("KOFI")
      assertThat(dpsAdjudication.witnesses[1].witnessType).isEqualTo(MigrateWitness.WitnessType.STAFF)
    }

    @Test
    fun `prisoner witnesses are copied`() {
      val nomisAdjudication = nomisAdjudicationCharge(
        createdByStaffUsername = "A.BEANS",
        prisonerWitnesses = listOf(
          Prisoner(
            offenderNo = "A1234KK",
            firstName = "BOBBY",
            lastName = "BALLER",
            createdByUsername = "B.BATTS",
            dateAddedToIncident = LocalDate.parse("2020-12-25"),
          ),
          Prisoner(
            offenderNo = "A1234TT",
            firstName = "JANE",
            lastName = "MIKES",
            createdByUsername = "A.AMRK",
            dateAddedToIncident = LocalDate.parse("2020-12-26"),
            comment = "Saw everything",
          ),
        ),
      )
      val dpsAdjudication = nomisAdjudication.toAdjudication()

      assertThat(dpsAdjudication.witnesses).hasSize(2)

      assertThat(dpsAdjudication.witnesses[0].createdBy).isEqualTo("B.BATTS")
      assertThat(dpsAdjudication.witnesses[0].firstName).isEqualTo("BOBBY")
      assertThat(dpsAdjudication.witnesses[0].lastName).isEqualTo("BALLER")
      assertThat(dpsAdjudication.witnesses[0].witnessType).isEqualTo(MigrateWitness.WitnessType.OTHER_PERSON)
      assertThat(dpsAdjudication.witnesses[0].dateAdded).isEqualTo("2020-12-25")

      assertThat(dpsAdjudication.witnesses[1].createdBy).isEqualTo("A.AMRK")
      assertThat(dpsAdjudication.witnesses[1].firstName).isEqualTo("JANE")
      assertThat(dpsAdjudication.witnesses[1].lastName).isEqualTo("MIKES")
      assertThat(dpsAdjudication.witnesses[1].witnessType).isEqualTo(MigrateWitness.WitnessType.OTHER_PERSON)
      assertThat(dpsAdjudication.witnesses[1].dateAdded).isEqualTo("2020-12-26")
      assertThat(dpsAdjudication.witnesses[1].comment).isEqualTo("Saw everything")
    }

    @Test
    fun `staff victims are copied`() {
      val nomisAdjudication = nomisAdjudicationCharge(
        staffVictims = listOf(
          Staff(
            username = "J.SMITH",
            staffId = 1,
            firstName = "JOHN",
            lastName = "SMITH",
            createdByUsername = "B.BATTS",
          ),
          Staff(
            username = "K.KOFI",
            staffId = 2,
            firstName = "KWEKU",
            lastName = "KOFI",
            createdByUsername = "B.BATTS",
          ),
        ),
      )
      val dpsAdjudication = nomisAdjudication.toAdjudication()

      assertThat(dpsAdjudication.witnesses).hasSize(2)
      assertThat(dpsAdjudication.witnesses[0].createdBy).isEqualTo("B.BATTS")
      assertThat(dpsAdjudication.witnesses[0].firstName).isEqualTo("JOHN")
      assertThat(dpsAdjudication.witnesses[0].lastName).isEqualTo("SMITH")
      assertThat(dpsAdjudication.witnesses[0].witnessType).isEqualTo(MigrateWitness.WitnessType.VICTIM)

      assertThat(dpsAdjudication.witnesses[1].createdBy).isEqualTo("B.BATTS")
      assertThat(dpsAdjudication.witnesses[1].firstName).isEqualTo("KWEKU")
      assertThat(dpsAdjudication.witnesses[1].lastName).isEqualTo("KOFI")
      assertThat(dpsAdjudication.witnesses[1].witnessType).isEqualTo(MigrateWitness.WitnessType.VICTIM)
    }

    @Test
    fun `prisoner victims are copied`() {
      val nomisAdjudication = nomisAdjudicationCharge(
        prisonerVictims = listOf(
          Prisoner(
            offenderNo = "A1234KK",
            firstName = "BOBBY",
            lastName = "BALLER",
            createdByUsername = "B.BATTS",
            dateAddedToIncident = LocalDate.parse("2020-12-25"),
          ),
          Prisoner(
            offenderNo = "A1234TT",
            firstName = "JANE",
            lastName = "MIKES",
            createdByUsername = "B.BATTS",
            dateAddedToIncident = LocalDate.parse("2020-12-26"),
            comment = "Beaten up",
          ),
        ),
      )
      val dpsAdjudication = nomisAdjudication.toAdjudication()

      assertThat(dpsAdjudication.witnesses).hasSize(2)

      assertThat(dpsAdjudication.witnesses[0].createdBy).isEqualTo("B.BATTS")
      assertThat(dpsAdjudication.witnesses[0].firstName).isEqualTo("BOBBY")
      assertThat(dpsAdjudication.witnesses[0].lastName).isEqualTo("BALLER")
      assertThat(dpsAdjudication.witnesses[0].witnessType).isEqualTo(MigrateWitness.WitnessType.VICTIM)
      assertThat(dpsAdjudication.witnesses[0].dateAdded).isEqualTo("2020-12-25")

      assertThat(dpsAdjudication.witnesses[1].createdBy).isEqualTo("B.BATTS")
      assertThat(dpsAdjudication.witnesses[1].firstName).isEqualTo("JANE")
      assertThat(dpsAdjudication.witnesses[1].lastName).isEqualTo("MIKES")
      assertThat(dpsAdjudication.witnesses[1].witnessType).isEqualTo(MigrateWitness.WitnessType.VICTIM)
      assertThat(dpsAdjudication.witnesses[1].dateAdded).isEqualTo("2020-12-26")
      assertThat(dpsAdjudication.witnesses[1].comment).isEqualTo("Beaten up")
    }

    @Test
    fun `other prisoner suspects are copied`() {
      val nomisAdjudication = nomisAdjudicationCharge(
        otherPrisonerInvolved = listOf(
          Prisoner(
            offenderNo = "A1234KK",
            firstName = "BOBBY",
            lastName = "BALLER",
            createdByUsername = "B.BATTS",
            dateAddedToIncident = LocalDate.parse("2020-12-25"),
          ),
          Prisoner(
            offenderNo = "A1234TT",
            firstName = "JANE",
            lastName = "MIKES",
            createdByUsername = "B.BATTS",
            dateAddedToIncident = LocalDate.parse("2020-12-26"),
            comment = "She joined in",
          ),
        ),
      )
      val dpsAdjudication = nomisAdjudication.toAdjudication()

      assertThat(dpsAdjudication.witnesses).hasSize(2)

      assertThat(dpsAdjudication.witnesses[0].createdBy).isEqualTo("B.BATTS")
      assertThat(dpsAdjudication.witnesses[0].firstName).isEqualTo("BOBBY")
      assertThat(dpsAdjudication.witnesses[0].lastName).isEqualTo("BALLER")
      assertThat(dpsAdjudication.witnesses[0].witnessType).isEqualTo(MigrateWitness.WitnessType.PRISONER)
      assertThat(dpsAdjudication.witnesses[0].dateAdded).isEqualTo("2020-12-25")

      assertThat(dpsAdjudication.witnesses[1].createdBy).isEqualTo("B.BATTS")
      assertThat(dpsAdjudication.witnesses[1].firstName).isEqualTo("JANE")
      assertThat(dpsAdjudication.witnesses[1].lastName).isEqualTo("MIKES")
      assertThat(dpsAdjudication.witnesses[1].witnessType).isEqualTo(MigrateWitness.WitnessType.PRISONER)
      assertThat(dpsAdjudication.witnesses[1].dateAdded).isEqualTo("2020-12-26")
      assertThat(dpsAdjudication.witnesses[1].comment).isEqualTo("She joined in")
    }

    @Test
    fun `all other staff types copied`() {
      val nomisAdjudication = nomisAdjudicationCharge(
        reportingOfficers = listOf(
          Staff(
            username = "J.SMITH",
            staffId = 1,
            firstName = "JOHN",
            lastName = "SMITH",
            createdByUsername = "A.BEANS",
          ),
          Staff(
            username = "K.KOFI",
            staffId = 2,
            firstName = "KWEKU",
            lastName = "KOFI",
            createdByUsername = "A.BEANS",
          ),
        ),
        otherStaffInvolved = listOf(
          Staff(
            username = "J.BEEKS",
            staffId = 3,
            firstName = "JANE",
            lastName = "SEEKS",
            createdByUsername = "A.BEANS",
          ),
          Staff(
            username = "S.BIGHTS",
            staffId = 4,
            firstName = "SARAH",
            lastName = "BIGHTS",
            createdByUsername = "A.BEANS",
          ),
        ),
      )
      val dpsAdjudication = nomisAdjudication.toAdjudication()

      assertThat(dpsAdjudication.witnesses).hasSize(4)

      assertThat(dpsAdjudication.witnesses[0].createdBy).isEqualTo("A.BEANS")
      assertThat(dpsAdjudication.witnesses[0].firstName).isEqualTo("JOHN")
      assertThat(dpsAdjudication.witnesses[0].lastName).isEqualTo("SMITH")
      assertThat(dpsAdjudication.witnesses[0].witnessType).isEqualTo(MigrateWitness.WitnessType.OTHER_PERSON)

      assertThat(dpsAdjudication.witnesses[1].createdBy).isEqualTo("A.BEANS")
      assertThat(dpsAdjudication.witnesses[1].firstName).isEqualTo("KWEKU")
      assertThat(dpsAdjudication.witnesses[1].lastName).isEqualTo("KOFI")
      assertThat(dpsAdjudication.witnesses[1].witnessType).isEqualTo(MigrateWitness.WitnessType.OTHER_PERSON)

      assertThat(dpsAdjudication.witnesses[2].createdBy).isEqualTo("A.BEANS")
      assertThat(dpsAdjudication.witnesses[2].firstName).isEqualTo("JANE")
      assertThat(dpsAdjudication.witnesses[2].lastName).isEqualTo("SEEKS")
      assertThat(dpsAdjudication.witnesses[2].witnessType).isEqualTo(MigrateWitness.WitnessType.OTHER_PERSON)

      assertThat(dpsAdjudication.witnesses[3].createdBy).isEqualTo("A.BEANS")
      assertThat(dpsAdjudication.witnesses[3].firstName).isEqualTo("SARAH")
      assertThat(dpsAdjudication.witnesses[3].lastName).isEqualTo("BIGHTS")
      assertThat(dpsAdjudication.witnesses[3].witnessType).isEqualTo(MigrateWitness.WitnessType.OTHER_PERSON)
    }
  }

  @Nested
  inner class Hearings {
    @Test
    fun `will copy core hearing details`() {
      val nomisAdjudication = nomisAdjudicationCharge(
        hearings = listOf(
          Hearing(
            hearingId = 54321,
            hearingDate = LocalDate.parse("2021-01-01"),
            hearingTime = "12:00:00",
            type = CodeDescription(code = "GOV_ADULT", description = "Governor's Hearing Adult"),
            hearingResults = emptyList(),
            scheduleDate = LocalDate.parse("2020-12-31"),
            scheduleTime = "11:00:00",
            comment = "Some comment",
            hearingStaff = Staff(
              username = "A.JUDGE",
              staffId = 123,
              firstName = "A",
              lastName = "JUDGE",
              createdByUsername = "A.BEANS",
            ),
            internalLocation = InternalLocation(321, "A-1-1", "MDI-A-1-1"),
            eventStatus = CodeDescription(code = "SCH", description = "Scheduled"),
            createdByUsername = "A.BEANS",
            createdDateTime = "2020-12-31T10:00:00",
            notifications = emptyList(),
            representativeText = "JULIE BART",
          ),
        ),
      )
      val dpsAdjudication = nomisAdjudication.toAdjudication()

      assertThat(dpsAdjudication.hearings).containsExactly(
        MigrateHearing(
          oicHearingId = 54321,
          oicHearingType = GOV_ADULT,
          hearingDateTime = "2021-01-01T12:00:00",
          adjudicator = "A.JUDGE",
          commentText = "Some comment",
          locationId = 321,
          hearingResult = null,
          representative = "JULIE BART",
        ),
      )
    }

    @Test
    fun `will copy scheduled hearing details`() {
      val nomisAdjudication = nomisAdjudicationCharge(
        hearings = listOf(
          Hearing(
            hearingId = 54321,
            hearingDate = LocalDate.parse("2021-01-01"),
            hearingTime = "12:00:00",
            hearingResults = emptyList(),
            internalLocation = InternalLocation(321, "A-1-1", "MDI-A-1-1"),
            eventStatus = CodeDescription(code = "SCH", description = "Scheduled"),
            createdByUsername = "A.BEANS",
            createdDateTime = "2020-12-31T10:00:00",
            notifications = emptyList(),
          ),
        ),
      )
      val dpsAdjudication = nomisAdjudication.toAdjudication()

      assertThat(dpsAdjudication.hearings).containsExactly(
        MigrateHearing(
          oicHearingId = 54321,
          // TODO - we always have a NOMIS type so default to this until we have a decision
          oicHearingType = GOV,
          hearingDateTime = "2021-01-01T12:00:00",
          adjudicator = null,
          commentText = null,
          locationId = 321,
          hearingResult = null,
        ),
      )
    }

    @Nested
    inner class HearingResults {
      @Test
      fun `will copy results`() {
        val charge = nomisAdjudicationCharge().charge.copy(chargeSequence = 2)
        val nomisAdjudication = nomisAdjudicationCharge(
          chargeSequence = charge.chargeSequence,
          hearings = listOf(
            Hearing(
              hearingId = 54321,
              hearingDate = LocalDate.parse("2021-01-01"),
              hearingTime = "12:00:00",
              type = CodeDescription(code = "GOV_ADULT", description = "Governor's Hearing Adult"),
              hearingResults = listOf(
                HearingResult(
                  charge = charge,
                  offence = charge.offence,
                  resultAwards = emptyList(),
                  pleaFindingType = CodeDescription(code = "GUILTY", description = "Guilty"),
                  findingType = CodeDescription(code = "S", description = "Suspended"),
                  createdByUsername = "A.BEANS",
                  createdDateTime = "2020-12-31T10:00:00",
                ),
              ),
              hearingStaff = Staff(
                username = "A.JUDGE",
                staffId = 123,
                firstName = "A",
                lastName = "JUDGE",
                createdByUsername = "A.BEANS",
              ),
              internalLocation = InternalLocation(321, "A-1-1", "MDI-A-1-1"),
              eventStatus = CodeDescription(code = "SCH", description = "Scheduled"),
              createdByUsername = "A.BEANS",
              createdDateTime = "2020-12-30T10:00:00",
              notifications = emptyList(),
            ),
          ),
        )
        val dpsAdjudication = nomisAdjudication.toAdjudication()
        assertThat(dpsAdjudication.hearings).hasSize(1)
        assertThat(dpsAdjudication.hearings[0].hearingResult).isNotNull
        assertThat(dpsAdjudication.hearings[0].hearingResult?.finding).isEqualTo("S")
        assertThat(dpsAdjudication.hearings[0].hearingResult?.plea).isEqualTo("GUILTY")
        assertThat(dpsAdjudication.hearings[0].hearingResult?.createdBy).isEqualTo("A.BEANS")
        assertThat(dpsAdjudication.hearings[0].hearingResult?.createdDateTime).isEqualTo("2020-12-31T10:00:00")
      }

      @Test
      fun `result can be null when not present`() {
        val charge = nomisAdjudicationCharge().charge.copy(chargeSequence = 2)
        val nomisAdjudication = nomisAdjudicationCharge(
          chargeSequence = charge.chargeSequence,
          hearings = listOf(
            Hearing(
              hearingId = 54321,
              hearingDate = LocalDate.parse("2021-01-01"),
              hearingTime = "12:00:00",
              type = CodeDescription(code = "GOV_ADULT", description = "Governor's Hearing Adult"),
              hearingResults = listOf(),
              hearingStaff = Staff(
                username = "A.JUDGE",
                staffId = 123,
                firstName = "A",
                lastName = "JUDGE",
                createdByUsername = "A.BEANS",
              ),
              internalLocation = InternalLocation(321, "A-1-1", "MDI-A-1-1"),
              eventStatus = CodeDescription(code = "SCH", description = "Scheduled"),
              createdByUsername = "A.BEANS",
              createdDateTime = "2020-12-31T10:00:00",
              notifications = emptyList(),
            ),
          ),
        )
        val dpsAdjudication = nomisAdjudication.toAdjudication()
        assertThat(dpsAdjudication.hearings).hasSize(1)
        assertThat(dpsAdjudication.hearings[0].hearingResult).isNull()
      }
    }

    @Nested
    inner class HearingNotifications {
      @Test
      fun `will copy notifications`() {
        val charge = nomisAdjudicationCharge().charge.copy(chargeSequence = 2)
        val nomisAdjudication = nomisAdjudicationCharge(
          chargeSequence = charge.chargeSequence,
          hearings = listOf(
            Hearing(
              hearingId = 54321,
              hearingDate = LocalDate.parse("2021-01-01"),
              hearingTime = "12:00:00",
              type = CodeDescription(code = "GOV_ADULT", description = "Governor's Hearing Adult"),
              hearingStaff = Staff(
                username = "A.JUDGE",
                staffId = 123,
                firstName = "A",
                lastName = "JUDGE",
                createdByUsername = "A.BEANS",
              ),
              internalLocation = InternalLocation(321, "A-1-1", "MDI-A-1-1"),
              eventStatus = CodeDescription(code = "SCH", description = "Scheduled"),
              createdByUsername = "A.BEANS",
              createdDateTime = "2020-12-30T10:00:00",
              hearingResults = emptyList(),
              notifications = listOf(
                HearingNotification(
                  deliveryDate = LocalDate.parse("2020-12-31"),
                  deliveryTime = "11:00:00",
                  comment = "You have been notified",
                  notifiedStaff = Staff(
                    username = "A.NOTIFY",
                    staffId = 456,
                    firstName = "A",
                    lastName = "NOTIFY",
                    createdByUsername = "A.BEANS",
                  ),
                ),
                HearingNotification(
                  deliveryDate = LocalDate.parse("2021-01-01"),
                  deliveryTime = "10:30:00",
                  notifiedStaff = Staff(
                    username = "B.NOTIFY",
                    staffId = 457,
                    firstName = "B",
                    lastName = "NOTIFY",
                    createdByUsername = "A.BEANS",
                  ),
                ),
              ),
            ),
          ),
        )
        val dpsAdjudication = nomisAdjudication.toAdjudication()
        assertThat(dpsAdjudication.disIssued).hasSize(2)
        assertThat(dpsAdjudication.disIssued[0].issuingOfficer).isEqualTo("A.NOTIFY")
        assertThat(dpsAdjudication.disIssued[0].dateTimeOfIssue).isEqualTo("2020-12-31T11:00:00")
        assertThat(dpsAdjudication.disIssued[1].issuingOfficer).isEqualTo("B.NOTIFY")
        assertThat(dpsAdjudication.disIssued[1].dateTimeOfIssue).isEqualTo("2021-01-01T10:30:00")
      }

      @Test
      fun `result can be null when not present`() {
        val charge = nomisAdjudicationCharge().charge.copy(chargeSequence = 2)
        val nomisAdjudication = nomisAdjudicationCharge(
          chargeSequence = charge.chargeSequence,
          hearings = listOf(
            Hearing(
              hearingId = 54321,
              hearingDate = LocalDate.parse("2021-01-01"),
              hearingTime = "12:00:00",
              type = CodeDescription(code = "GOV_ADULT", description = "Governor's Hearing Adult"),
              hearingResults = listOf(),
              hearingStaff = Staff(
                username = "A.JUDGE",
                staffId = 123,
                firstName = "A",
                lastName = "JUDGE",
                createdByUsername = "A.BEANS",
              ),
              internalLocation = InternalLocation(321, "A-1-1", "MDI-A-1-1"),
              eventStatus = CodeDescription(code = "SCH", description = "Scheduled"),
              createdByUsername = "A.BEANS",
              createdDateTime = "2020-12-31T10:00:00",
              notifications = emptyList(),
            ),
          ),
        )
        val dpsAdjudication = nomisAdjudication.toAdjudication()
        assertThat(dpsAdjudication.hearings).hasSize(1)
        assertThat(dpsAdjudication.hearings[0].hearingResult).isNull()
      }
    }

    @Nested
    inner class Punishments {

      @Test
      fun `will copy award for the charge punishment`() {
        val charge = nomisAdjudicationCharge().charge.copy(chargeSequence = 2)
        val nomisAdjudication = nomisAdjudicationCharge(
          chargeSequence = charge.chargeSequence,
          hearings = listOf(
            Hearing(
              hearingId = 54321,
              hearingDate = LocalDate.parse("2021-01-01"),
              hearingTime = "12:00:00",
              type = CodeDescription(code = "GOV_ADULT", description = "Governor's Hearing Adult"),
              hearingResults = listOf(
                HearingResult(
                  charge = charge,
                  offence = charge.offence,
                  resultAwards = listOf(
                    HearingResultAward(
                      effectiveDate = LocalDate.parse("2021-01-01"),
                      sanctionType = CodeDescription(code = "CC", description = "Cellular Confinement"),
                      sanctionStatus = CodeDescription(code = "IMMEDIATE", description = "Immediate"),
                      comment = "Remain in cell",
                      statusDate = LocalDate.parse("2021-01-02"),
                      sanctionDays = 2,
                      sanctionMonths = null,
                      compensationAmount = BigDecimal.valueOf(23.67),
                      consecutiveAward = null,
                      sequence = 23,
                      chargeSequence = charge.chargeSequence,
                      adjudicationNumber = 7654321,
                      createdByUsername = "A.BEANS",
                      createdDateTime = "2021-01-01T10:00",
                    ),
                  ),
                  pleaFindingType = CodeDescription(code = "GUILTY", description = "Guilty"),
                  findingType = CodeDescription(code = "S", description = "Suspended"),
                  createdByUsername = "A.BEANS",
                  createdDateTime = "2020-12-31T10:00:00",
                ),
              ),
              hearingStaff = Staff(
                username = "A.JUDGE",
                staffId = 123,
                firstName = "A",
                lastName = "JUDGE",
                createdByUsername = "A.BEANS",
              ),
              internalLocation = InternalLocation(321, "A-1-1", "MDI-A-1-1"),
              eventStatus = CodeDescription(code = "SCH", description = "Scheduled"),
              createdByUsername = "A.BEANS",
              createdDateTime = "2020-12-31T10:00:00",
              notifications = emptyList(),
            ),
          ),
        )
        val dpsAdjudication = nomisAdjudication.toAdjudication()
        assertThat(dpsAdjudication.punishments).hasSize(1)
        assertThat(dpsAdjudication.punishments[0].comment).isEqualTo("Remain in cell")
        assertThat(dpsAdjudication.punishments[0].compensationAmount).isEqualTo(BigDecimal.valueOf(23.67))
        assertThat(dpsAdjudication.punishments[0].days).isEqualTo(2)
        assertThat(dpsAdjudication.punishments[0].effectiveDate).isEqualTo("2021-01-01")
        assertThat(dpsAdjudication.punishments[0].statusDate).isEqualTo("2021-01-02")
        assertThat(dpsAdjudication.punishments[0].consecutiveChargeNumber).isNull()
        assertThat(dpsAdjudication.punishments[0].sanctionCode).isEqualTo("CC")
        assertThat(dpsAdjudication.punishments[0].sanctionSeq).isEqualTo(23)
        assertThat(dpsAdjudication.punishments[0].sanctionStatus).isEqualTo("IMMEDIATE")
        assertThat(dpsAdjudication.punishments[0].createdBy).isEqualTo("A.BEANS")
        assertThat(dpsAdjudication.punishments[0].createdDateTime).isEqualTo("2021-01-01T10:00")
      }

      @Test
      fun `will calculate the consecutiveChargeNumber when consecutive punishment is present`() {
        val charge1 = nomisAdjudicationCharge().charge.copy(chargeSequence = 1)
        val charge2 = nomisAdjudicationCharge().charge.copy(chargeSequence = 2)
        val nomisAdjudication = nomisAdjudicationCharge(
          adjudicationNumber = 12345,
          chargeSequence = charge2.chargeSequence,
          hearings = listOf(
            Hearing(
              hearingId = 54321,
              hearingDate = LocalDate.parse("2021-01-01"),
              hearingTime = "12:00:00",
              type = CodeDescription(code = "GOV_ADULT", description = "Governor's Hearing Adult"),
              hearingResults = listOf(
                HearingResult(
                  charge = charge2,
                  offence = charge2.offence,
                  resultAwards = listOf(
                    HearingResultAward(
                      effectiveDate = LocalDate.parse("2021-01-01"),
                      sanctionType = CodeDescription(code = "CC", description = "Cellular Confinement"),
                      sanctionStatus = CodeDescription(code = "IMMEDIATE", description = "Immediate"),
                      comment = "Remain in cell",
                      statusDate = LocalDate.parse("2021-01-02"),
                      sanctionDays = 2,
                      sanctionMonths = null,
                      compensationAmount = null,
                      chargeSequence = charge2.chargeSequence,
                      consecutiveAward = HearingResultAward(
                        effectiveDate = LocalDate.parse("2021-01-01"),
                        sanctionType = CodeDescription(code = "CC", description = "Cellular Confinement"),
                        sanctionStatus = CodeDescription(code = "IMMEDIATE", description = "Immediate"),
                        comment = "Remain in cell",
                        statusDate = LocalDate.parse("2021-01-02"),
                        sanctionDays = 2,
                        sanctionMonths = null,
                        compensationAmount = null,
                        consecutiveAward = null,
                        sequence = 24,
                        chargeSequence = charge1.chargeSequence,
                        adjudicationNumber = 7654321,
                        createdByUsername = "A.BEANS",
                        createdDateTime = "2021-03-01T10:00:00",
                      ),
                      sequence = 23,
                      adjudicationNumber = 12345,
                      createdByUsername = "A.BEANS",
                      createdDateTime = "2021-03-01T10:00:00",
                    ),
                  ),
                  pleaFindingType = CodeDescription(code = "GUILTY", description = "Guilty"),
                  findingType = CodeDescription(code = "S", description = "Suspended"),
                  createdByUsername = "A.BEANS",
                  createdDateTime = "2020-12-31T10:00:00",
                ),
              ),
              hearingStaff = Staff(
                username = "A.JUDGE",
                staffId = 123,
                firstName = "A",
                lastName = "JUDGE",
                createdByUsername = "A.BEANS",
              ),
              internalLocation = InternalLocation(321, "A-1-1", "MDI-A-1-1"),
              eventStatus = CodeDescription(code = "SCH", description = "Scheduled"),
              createdByUsername = "A.BEANS",
              createdDateTime = "2020-12-31T10:00:00",
              notifications = emptyList(),
            ),
          ),
        )
        val dpsAdjudication = nomisAdjudication.toAdjudication()
        assertThat(dpsAdjudication.punishments).hasSize(1)
        assertThat(dpsAdjudication.punishments[0].comment).isEqualTo("Remain in cell")
        assertThat(dpsAdjudication.punishments[0].compensationAmount).isNull()
        assertThat(dpsAdjudication.punishments[0].days).isEqualTo(2)
        assertThat(dpsAdjudication.punishments[0].effectiveDate).isEqualTo("2021-01-01")
        assertThat(dpsAdjudication.punishments[0].sanctionCode).isEqualTo("CC")
        assertThat(dpsAdjudication.punishments[0].sanctionSeq).isEqualTo(23)
        assertThat(dpsAdjudication.punishments[0].sanctionStatus).isEqualTo("IMMEDIATE")
        assertThat(dpsAdjudication.punishments[0].createdBy).isEqualTo("A.BEANS")
        assertThat(dpsAdjudication.punishments[0].consecutiveChargeNumber).isEqualTo("7654321-1")
      }

      @ParameterizedTest
      @MethodSource("uk.gov.justice.digital.hmpps.prisonerfromnomismigration.adjudications.AdjudicationTransformationTest#getAwardDayMonthData")
      fun `days and months are added together`(
        days: Int?,
        months: Int?,
        effectiveDate: String,
        calculatedDays: Int?,
      ) {
        val charge = nomisAdjudicationCharge().charge.copy(chargeSequence = 2)
        val nomisAdjudication = nomisAdjudicationCharge(
          chargeSequence = charge.chargeSequence,
          hearings = listOf(
            Hearing(
              hearingId = 54321,
              hearingDate = LocalDate.parse("2021-01-01"),
              hearingTime = "12:00:00",
              type = CodeDescription(code = "GOV_ADULT", description = "Governor's Hearing Adult"),
              hearingResults = listOf(
                HearingResult(
                  charge = charge,
                  offence = charge.offence,
                  resultAwards = listOf(
                    HearingResultAward(
                      effectiveDate = LocalDate.parse(effectiveDate),
                      sanctionType = CodeDescription(code = "CC", description = "Cellular Confinement"),
                      sanctionStatus = CodeDescription(code = "IMMEDIATE", description = "Immediate"),
                      comment = "Remain in cell",
                      statusDate = LocalDate.parse("2021-01-02"),
                      sanctionDays = days,
                      sanctionMonths = months,
                      compensationAmount = null,
                      consecutiveAward = null,
                      sequence = 23,
                      chargeSequence = charge.chargeSequence,
                      adjudicationNumber = 12345,
                      createdByUsername = "A.BEANS",
                      createdDateTime = "2021-03-01T10:00:00",
                    ),
                  ),
                  pleaFindingType = CodeDescription(code = "GUILTY", description = "Guilty"),
                  findingType = CodeDescription(code = "S", description = "Suspended"),
                  createdByUsername = "A.BEANS",
                  createdDateTime = "2020-12-31T10:00:00",
                ),
              ),
              hearingStaff = Staff(
                username = "A.JUDGE",
                staffId = 123,
                firstName = "A",
                lastName = "JUDGE",
                createdByUsername = "A.BEANS",
              ),
              internalLocation = InternalLocation(321, "A-1-1", "MDI-A-1-1"),
              eventStatus = CodeDescription(code = "SCH", description = "Scheduled"),
              createdByUsername = "A.BEANS",
              createdDateTime = "2020-12-31T10:00:00",
              notifications = emptyList(),
            ),
          ),
        )
        val dpsAdjudication = nomisAdjudication.toAdjudication()
        assertThat(dpsAdjudication.punishments).hasSize(1)
        assertThat(dpsAdjudication.punishments[0].days).isEqualTo(calculatedDays)
      }
    }
  }

  companion object {
    @JvmStatic
    fun getAwardDayMonthData(): Stream<Arguments> {
      // days, months, effect date and expected days
      return Stream.of(
        Arguments.of(null, null, "2020-01-01", null),
        Arguments.of(1, null, "2020-01-01", 1),
        Arguments.of(null, 1, "2020-01-01", 31),
        // leap year
        Arguments.of(null, 1, "2020-02-01", 29),
        Arguments.of(null, 1, "2021-02-01", 28),
        Arguments.of(10, 1, "2020-02-01", 39),
        Arguments.of(null, 6, "2020-01-01", 182),
        Arguments.of(null, 24, "2020-01-01", 731),
        Arguments.of(null, 24, "2021-01-01", 730),
      )
    }
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
  prisonerWitnesses: List<Prisoner> = emptyList(),
  prisonerVictims: List<Prisoner> = emptyList(),
  otherPrisonerInvolved: List<Prisoner> = emptyList(),
  reportingOfficers: List<Staff> = emptyList(),
  staffWitness: List<Staff> = emptyList(),
  staffVictims: List<Staff> = emptyList(),
  otherStaffInvolved: List<Staff> = emptyList(),
  repairs: List<Repair> = emptyList(),
  investigations: List<Investigation> = emptyList(),
  statementDetails: String = "Fight",
  offenceCode: String = "51:12A",
  offenceDescription: String = "Commits any assault - assault on prison officer",
  offenceType: String = "51",
  genderCode: String = "F",
  currentPrison: CodeDescription? = CodeDescription(prisonId, "HMP Prison"),
  reportingStaffUsername: String = "F.LAST",
  createdByStaffUsername: String = "A.BEANS",
  hearings: List<Hearing> = emptyList(),
  chargeEvidence: String? = null,
  chargeReportDetail: String? = null,
): AdjudicationChargeResponse {
  return AdjudicationChargeResponse(
    adjudicationSequence = 10,
    offenderNo = offenderNo,
    bookingId = bookingId,
    partyAddedDate = LocalDate.now(),
    gender = CodeDescription(genderCode, "Female"),
    currentPrison = currentPrison,
    incident = AdjudicationIncident(
      adjudicationIncidentId = adjudicationIncidentId,
      reportingStaff = Staff(
        username = reportingStaffUsername,
        staffId = 1,
        firstName = "stafffirstname",
        lastName = "stafflastname",
        createdByUsername = "A.BEANS",
      ),
      incidentDate = incidentDate,
      incidentTime = incidentTime.toString(),
      reportedDate = reportedDate,
      reportedTime = reportedTime.toString(),
      createdByUsername = createdByStaffUsername,
      createdDateTime = "2023-04-12T10:00:00",
      internalLocation = InternalLocation(internalLocationId, "GYM", "GYM"),
      prison = CodeDescription(prisonId, "HMP Prison"),
      prisonerWitnesses = prisonerWitnesses,
      prisonerVictims = prisonerVictims,
      incidentType = CodeDescription("GOV", "Governor's Report"),
      otherPrisonersInvolved = otherPrisonerInvolved,
      reportingOfficers = reportingOfficers,
      staffWitnesses = staffWitness,
      staffVictims = staffVictims,
      otherStaffInvolved = otherStaffInvolved,
      repairs = repairs,
      details = statementDetails,
    ),
    charge = AdjudicationCharge(
      chargeSequence = chargeSequence,
      evidence = chargeEvidence,
      reportDetail = chargeReportDetail,
      offenceId = "$adjudicationNumber/$chargeSequence",
      offence = AdjudicationOffence(
        code = offenceCode,
        description = offenceDescription,
        type = CodeDescription(offenceType, "Prison Rule $offenceType"),
      ),
    ),
    investigations = investigations,
    hearings = hearings,
    adjudicationNumber = adjudicationNumber,
    comment = "comment",
  )
}
