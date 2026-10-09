package uk.gov.justice.digital.hmpps.prisonerfromnomismigration.movements.personlocation

import org.junit.jupiter.api.extension.ExtendWith
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.integration.SqsIntegrationTestBase

@ExtendWith(
  PersonLocationDpsApiExtension::class,
)
abstract class PersonLocationIntegrationTestBase : SqsIntegrationTestBase() {

  @MockitoSpyBean
  protected lateinit var personLocationMigrationService: PersonLocationMigrationService
}
