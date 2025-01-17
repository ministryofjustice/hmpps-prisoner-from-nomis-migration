package uk.gov.justice.digital.hmpps.prisonerfromnomismigration.coreperson

import io.swagger.v3.oas.annotations.Operation
import jakarta.validation.Valid
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RestController

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

data class MigrateCorePersonRequest(
  val nomisPrisonNumber: String,
  val firstName: String,
  val lastName: String,
  val addresses: List<MockCprAddress> = emptyList(),
  val phoneNumbers: List<MockCprPhoneNumber> = emptyList(),
  // TODO val emailAddresses: List<Email> = emptyList(),
)

data class MigrateCorePersonResponse(
  val cprId: String,
  val nomisPrisonNumber: String,
  val addressIds: List<IdPair> = emptyList(),
  val phoneIds: List<IdPair> = emptyList(),
  val emailAddressIds: List<IdPair> = emptyList(),
)

data class IdPair(
  val nomisId: Long,
  val cprId: String,
)

data class MockCprAddress(
  val nomisAddressId: Long,
  val isPrimary: Boolean,
  val type: String? = null,
  val flat: String? = null,
  val premise: String? = null,
  val street: String? = null,
  val locality: String? = null,
  val town: String? = null,
  val postcode: String? = null,
  val county: String? = null,
  val country: String? = null,
  val noFixedAddress: Boolean? = false,
  val startDate: java.time.LocalDate? = null,
  val endDate: java.time.LocalDate? = null,
  val comment: String? = null,
  val mail: Boolean? = false,
)

data class MockCprPhoneNumber(
  val nomisPhoneId: Long,
  val phoneNumber: String? = null,
  val phoneType: String? = null,
  val phoneExtension: String? = null,

)
