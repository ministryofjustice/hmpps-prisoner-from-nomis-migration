package uk.gov.justice.digital.hmpps.prisonerfromnomismigration.coreperson

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomisprisoner.model.CoreOffender

class CorePersonMigrationTest {
  @Nested
  inner class MiddleName {
    @Test
    fun `will correctly transform middle names`() {
      val aliasToCopy = CoreOffender(
        offenderId = 10000L,
        firstName = "firstName",
        lastName = "lastName",
        workingName = false,
        identifiers = emptyList(),
      )
      val middleNameAliases = listOf(
        aliasToCopy,
        aliasToCopy.copy(middleName1 = "not_null1", offenderId = 10001L),
        aliasToCopy.copy(middleName2 = "not_null2", offenderId = 10002L),
        aliasToCopy.copy(middleName1 = "not_empty1", middleName2 = "", offenderId = 10003L),
        aliasToCopy.copy(middleName1 = "", middleName2 = "not_empty2", offenderId = 10004L),
        aliasToCopy.copy(middleName1 = "", middleName2 = "", offenderId = 10005L),
      )
      val migrated = middleNameAliases.toMigrateAliasesAndIdentifiersRequest()
      assertThat(migrated.aliases[0].middleNames).isEqualTo(null)
      assertThat(migrated.aliases[1].middleNames).isEqualTo("not_null1")
      assertThat(migrated.aliases[2].middleNames).isEqualTo("not_null2")
      assertThat(migrated.aliases[3].middleNames).isEqualTo("not_empty1")
      assertThat(migrated.aliases[4].middleNames).isEqualTo("not_empty2")
      assertThat(migrated.aliases[5].middleNames).isEqualTo(null)
    }
  }
}
