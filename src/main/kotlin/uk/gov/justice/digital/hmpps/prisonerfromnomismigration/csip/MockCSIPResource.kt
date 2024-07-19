package uk.gov.justice.digital.hmpps.prisonerfromnomismigration.csip

import com.fasterxml.jackson.annotation.JsonProperty
import io.swagger.v3.oas.annotations.Operation
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.PatchMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RestController
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.csip.model.ContributoryFactor
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.csip.model.CreateContributoryFactorRequest
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.csip.model.CreateCsipRecordRequest
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.csip.model.CreateReferralRequest
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.csip.model.CreateSaferCustodyScreeningOutcomeRequest
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.csip.model.CsipRecord
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.csip.model.ReferenceData
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.csip.model.Referral
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.csip.model.SaferCustodyScreeningOutcome
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.csip.model.UpdateContributoryFactorRequest
import java.time.LocalDate
import java.time.LocalDateTime
import java.util.UUID

/**
 * This represents the possible interface for the CSIP API service.
 * This can be deleted once the real service is available.
 */
@RestController
class MockCSIPResource {
  private companion object {
    val log: Logger = LoggerFactory.getLogger(this::class.java)

    fun dpsCsip() = CsipRecord(
      recordUuid = UUID.randomUUID(),
      prisonNumber = "1234",
      createdAt = LocalDateTime.parse("2024-03-29T11:32:15"),
      createdBy = "JIM_SMITH",
      createdByDisplayName = "Jim Smith",
      referral =
      Referral(
        incidentDate = LocalDate.parse("2024-03-27"),
        incidentType = ReferenceData(
          code = "incidentTypeCode",
          createdAt = LocalDateTime.parse("2024-03-29T11:32:16"),
          createdBy = "JIM_SMITH",
        ),
        incidentLocation = ReferenceData(
          code = "incidentLocationCode",
          createdAt = LocalDateTime.parse("2024-03-29T11:32:16"),
          createdBy = "JIM_SMITH",
        ),
        referredBy = "Jim Smith",
        refererArea = ReferenceData(
          code = "areaCode",
          createdAt = LocalDateTime.parse("2024-03-29T11:32:16"),
          createdBy = "JIM_SMITH",
        ),
        incidentInvolvement = ReferenceData(
          code = "involvementCode",
          createdAt = LocalDateTime.parse("2024-03-29T11:32:16"),
          createdBy = "JIM_SMITH",
        ),
        descriptionOfConcern = "Needs guidance",
        knownReasons = "Fighting",
        contributoryFactors = listOf(),
        incidentTime = null,
        referralSummary = null,
        isProactiveReferral = null,
        isStaffAssaulted = null,
        assaultedStaffName = null,
        otherInformation = null,
        isSaferCustodyTeamInformed = null,
        isReferralComplete = null,
        investigation = null,
        saferCustodyScreeningOutcome = null,
        decisionAndActions = null,
      ),
      prisonCodeWhenRecorded = null,
      logCode = null,
      lastModifiedAt = null,
      lastModifiedBy = null,
      lastModifiedByDisplayName = null,
      plan = null,
    )

    fun dpsSaferCustodyScreening() =
      SaferCustodyScreeningOutcome(
        outcome = ReferenceData(
          code = "CUR",
          description	= "Progress to CSIP",
          listSequence = 1,
          createdAt = LocalDateTime.parse("2024-03-29T11:32:16"),
          createdBy = "FRED_ADM",
        ),
        recordedBy = "FRED_ADM",
        recordedByDisplayName = "FRED_ADM",
        date = LocalDate.parse("2024-04-08"),
        reasonForDecision = "There is a reason for the decision - it goes here",
      )

    fun dpsCsipFactor() =
      ContributoryFactor(
        factorUuid = UUID.randomUUID(),
        factorType = ReferenceData(
          code = "BUL",
          description = "Bullying",
          createdAt = LocalDateTime.parse("2024-03-29T11:32:16"),
          createdBy = "JIM_ADM",
        ),
        createdAt = LocalDateTime.parse("2024-03-29T11:32:16"),
        createdBy = "JIM_ADM",
        createdByDisplayName = "Jim Admin",
        comment = "Offender causes trouble",
      )
  }

  @PreAuthorize("hasRole('ROLE_MIGRATE_CSIP')")
  @PostMapping("/migrate/prisoners/{prisonNumber}/csip-records")
  @Operation(hidden = true)
  suspend fun migrateCSIPReports(
    csipRequest: MigrateCSIP,
    @PathVariable
    prisonNumber: String,
  ): CsipRecord {
    log.info("Created csip for migrate offender $prisonNumber")
    return dpsCsip()
  }

  @PreAuthorize("hasRole('ROLE_NOMIS_CSIP')")
  @PostMapping("/prisoners/{prisonNumber}/csip-records")
  @Operation(hidden = true)
  suspend fun createCSIP(
    csipRequest: CreateCsipRecordRequest,
    @PathVariable
    prisonNumber: String,
  ): CsipRecord {
    log.info("Created csip for sync offender $prisonNumber")
    return dpsCsip()
  }

  @PreAuthorize("hasRole('ROLE_NOMIS_CSIP')")
  @DeleteMapping("/csip-records/{recordUuid}")
  @Operation(hidden = true)
  suspend fun deleteCSIP(
    @PathVariable
    recordUuid: String,
  ): CsipRecord {
    log.info("Deleted csip for sync with id $recordUuid ")
    return return dpsCsip()
  }

  @PreAuthorize("hasRole('ROLE_NOMIS_CSIP')")
  @PostMapping("/csip-records/{recordUuid}/referral/safer-custody-screening")
  @Operation(hidden = true)
  suspend fun createCSIPSCS(
    csipRequest: CreateSaferCustodyScreeningOutcomeRequest,
    @PathVariable
    recordUuid: String,
  ): SaferCustodyScreeningOutcome {
    log.info("Created csip scs for sync report $recordUuid")
    return dpsSaferCustodyScreening()
  }

  @PreAuthorize("hasRole('ROLE_NOMIS_CSIP')")
  @PostMapping("/csip-records/{recordUuid}/referral/contributory-factors")
  @Operation(hidden = true)
  suspend fun createCSIPFactor(
    csipRequest: CreateContributoryFactorRequest,
    @PathVariable
    recordUuid: String,
  ): ContributoryFactor {
    log.info("Created csip factor for sync for report $recordUuid")
    return dpsCsipFactor()
  }

  @PreAuthorize("hasRole('ROLE_NOMIS_CSIP')")
  @PatchMapping("/csip-records/referral/contributory-factors/{contributoryFactorUuid}")
  @Operation(hidden = true)
  suspend fun updateCSIPFactor(
    csipRequest: UpdateContributoryFactorRequest,
    @PathVariable
    contributoryFactorUuid: String,
  ): ContributoryFactor {
    log.info("Updated csip factor for sync for factor $contributoryFactorUuid")
    return dpsCsipFactor()
  }

  @PreAuthorize("hasRole('ROLE_NOMIS_CSIP')")
  @DeleteMapping("/csip-records/referral/contributory-factors/{contributoryFactorUuid}")
  @Operation(hidden = true)
  suspend fun deleteCSIPFactor(
    @PathVariable
    contributoryFactorUuid: String,
  ) {
    log.info("Deleted csip factor for sync for factor $contributoryFactorUuid")
  }

  data class MigrateCSIP(
    /* User entered identifier for the CSIP record. Defaults to the prison code. */
    @field:JsonProperty("logNumber")
    val logNumber: kotlin.String,

    @field:JsonProperty("referral")
    val referral: CreateReferralRequest,
  )
}
