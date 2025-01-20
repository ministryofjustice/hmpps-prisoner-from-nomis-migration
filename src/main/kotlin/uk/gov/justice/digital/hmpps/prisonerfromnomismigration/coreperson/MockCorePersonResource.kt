package uk.gov.justice.digital.hmpps.prisonerfromnomismigration.coreperson

import io.swagger.v3.oas.annotations.Operation
import jakarta.validation.Valid
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RestController
import java.time.LocalDate

/**
 * This represents the possible interface for the CorePerson api service.
 * This can be deleted once the real service is available.
 */
@RestController
class MockCorePersonResource {
  private companion object {
    val log: Logger = LoggerFactory.getLogger(this::class.java)
  }

  @PreAuthorize("hasRole('ROLE_MIGRATE_CORE_PERSON')")
  @PostMapping("/syscon-sync")
  @Operation(hidden = true)
  suspend fun migrateCorePerson(
    @RequestBody @Valid
    corePersonRequest: MigrateCorePersonRequest,
  ): MigrateCorePersonResponse {
    log.info("Created core person for migration with nomis prison number ${corePersonRequest.nomisPrisonNumber} ")
    return MigrateCorePersonResponse(
      cprId = "CPR-${corePersonRequest.nomisPrisonNumber}",
      nomisPrisonNumber = corePersonRequest.nomisPrisonNumber,
    )
  }
}

data class IdPair(
  val nomisId: Long,
  val cprId: String,
)

data class MigrateCorePersonRequest(
  val nomisPrisonNumber: String,
  val activeFlag: Boolean,
  val inOutStatus: String? = null,
  val offenders: List<MockCprOffender>,
  val addresses: List<MockCprAddress> = emptyList(),
  val phoneNumbers: List<MockCprPhoneNumber> = emptyList(),
  val emailAddresses: List<MockCprEmailAddress> = emptyList(),
  val religions: List<MockCprBelief> = emptyList(),
)

data class MigrateCorePersonResponse(
  val cprId: String,
  val nomisPrisonNumber: String,
  val addressIds: List<IdPair> = emptyList(),
  val phoneIds: List<IdPair> = emptyList(),
  val emailAddressIds: List<IdPair> = emptyList(),
)
data class MockCprOffender(
  val nomisOffenderId: Long,
  val title: String? = null,
  val firstName: String,
  val middleName1: String? = null,
  val middleName2: String? = null,
  val lastName: String,
  val dateOfBirth: LocalDate? = null,
  val birthPlace: String? = null,
  val birthCountry: String? = null,
  val race: String? = null,
  // The offender record associated with the current booking */
  val workingName: Boolean,
  val sex: String? = null,
)
data class MockCprAddress(
  val nomisAddressId: Long,
  val isPrimary: Boolean,
  val usages: List<MockCprAddressUsage> = emptyList(),
  val flat: String? = null,
  val premise: String? = null,
  val street: String? = null,
  val locality: String? = null,
  val town: String? = null,
  val postcode: String? = null,
  val county: String? = null,
  val country: String? = null,
  val noFixedAddress: Boolean = false,
  val startDate: LocalDate? = null,
  val endDate: LocalDate? = null,
  val comment: String? = null,
  val mail: Boolean? = false,
)
data class MockCprAddressUsage(
  val usage: String,
  val active: Boolean,
)
data class MockCprPhoneNumber(
  val nomisPhoneId: Long,
  val phoneNumber: String? = null,
  val phoneType: String? = null,
  val phoneExtension: String? = null,
)
data class MockCprEmailAddress(
  val nomisEmailAddressId: Long,
  val emailAddress: String? = null,
)
data class MockCprBelief(
  val nomisBeliefId: Long,
  val religion: String,
  val startDate: LocalDate,
  val endDate: LocalDate? = null,
  val changeReason: Boolean? = null,
  val comment: String? = null,
  val createdByDisplayName: String? = null,
  val updatedDisplayName: String? = null,
)
