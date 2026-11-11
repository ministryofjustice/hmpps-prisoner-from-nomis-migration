package uk.gov.justice.digital.hmpps.prisonerfromnomismigration.coreperson

import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.coreperson.model.PrisonAlias
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomisprisoner.model.CodeDescription

class CorePersonAliasIdentifierConversionTest {
  private val alias = corePerson().offenders!!.first()

  @Nested
  inner class Title {
    @ParameterizedTest
    @CsvSource(
      "BR,BR",
      "LADY,LDY",
      "SIR,SIR",
      "FR,FR",
      "REV,REV",
      "LORD,LRD",
      "DR,DR",
      "MRS,MRS",
      "MS,MS",
      "MISS,MISS",
      "MR,MR",
    )
    fun `will map NOMIS title to CPR title`(nomisCode: String, cprCode: String) {
      val prisonAlias = alias.copy(title = CodeDescription(nomisCode, nomisCode)).toPrisonAlias()

      assertThat(prisonAlias.titleCode?.value).isEqualTo(cprCode)
    }

    @Test
    fun `will leave title null when not set`() {
      assertThat(alias.copy(title = null).toPrisonAlias().titleCode).isNull()
    }

    @Test
    fun `will fail for an unknown title`() {
      assertThatThrownBy { alias.copy(title = CodeDescription("KING", "King")).toPrisonAlias() }
        .isInstanceOf(IllegalArgumentException::class.java)
        .hasMessageContaining("title code 'KING'")
    }
  }

  @Nested
  inner class Sex {
    @ParameterizedTest
    @CsvSource("F,F", "M,M", "NK,N", "NS,NS")
    fun `will map NOMIS sex to CPR sex`(nomisCode: String, cprCode: String) {
      assertThat(alias.copy(sex = CodeDescription(nomisCode, nomisCode)).toPrisonAlias().sexCode?.value).isEqualTo(cprCode)
    }

    @Test
    fun `will fail for an unknown sex`() {
      assertThatThrownBy { alias.copy(sex = CodeDescription("X", "X")).toPrisonAlias() }
        .isInstanceOf(IllegalArgumentException::class.java)
        .hasMessageContaining("sex code 'X'")
    }
  }

  @Nested
  inner class Ethnicity {
    @ParameterizedTest
    @CsvSource(
      "A1,A1", "A2,A2", "A3,A3", "A4,A4", "A9,A9", "B1,B1", "B2,B2", "B9,B9", "M1,M1", "M2,M2", "M3,M3", "M9,M9",
      "MERGE,MERGE", "NS,NS", "O1,O1", "O2,O2", "O9,O9", "W1,W1", "W10,W5", "W2,W2", "W3,W3", "W8,W8", "W9,W9",
    )
    fun `will map NOMIS ethnicity to CPR ethnicity`(nomisCode: String, cprCode: String) {
      assertThat(alias.copy(ethnicity = CodeDescription(nomisCode, nomisCode)).toPrisonAlias().ethnicity?.value).isEqualTo(cprCode)
    }

    @Test
    fun `will fail for an unknown ethnicity`() {
      assertThatThrownBy { alias.copy(ethnicity = CodeDescription("XX", "XX")).toPrisonAlias() }
        .isInstanceOf(IllegalArgumentException::class.java)
        .hasMessageContaining("ethnicity code 'XX'")
    }
  }

  @Nested
  inner class Suffix {
    @ParameterizedTest
    @CsvSource("JR,Junior", "SR,Senior", "I,I", "IV,IV", "X,X")
    fun `will map NOMIS suffix to CPR suffix`(nomisCode: String, cprCode: String) {
      assertThat(alias.copy(suffix = CodeDescription(nomisCode, nomisCode)).toPrisonAlias().suffix?.value).isEqualTo(cprCode)
    }
  }

  @Nested
  inner class BirthCountry {
    @ParameterizedTest
    @CsvSource("IOM,IMN", "ROM,ROU", "GBR,GBR")
    fun `will map NOMIS country to CPR country`(nomisCode: String, cprCode: String) {
      assertThat(alias.copy(birthCountry = CodeDescription(nomisCode, nomisCode)).toPrisonAlias().birthCountry).isEqualTo(PrisonAlias.BirthCountry.valueOf(cprCode))
    }
  }

  @Nested
  inner class IdentifierType {
    @ParameterizedTest
    @CsvSource("NINO,NATIONAL_INSURANCE_NUMBER", "DL,DRIVER_LICENSE_NUMBER", "PNC,PNC", "CRO,CRO", "NOMS,NOMS", "SPNC,SPNC", "NHS,NHS")
    fun `will map NOMIS identifier type to CPR identifier type`(nomisCode: String, cprCode: String) {
      val identifier = alias.identifiers.first().copy(type = CodeDescription(nomisCode, nomisCode))

      assertThat(identifier.toPrisonIdentifier().type.value).isEqualTo(cprCode)
    }

    @Test
    fun `will fail for an unmapped identifier type`() {
      val identifier = alias.identifiers.first().copy(type = CodeDescription("STAFF", "Staff Pass"))

      assertThatThrownBy { identifier.toPrisonIdentifier() }
        .isInstanceOf(IllegalArgumentException::class.java)
        .hasMessageContaining("identifier type code 'STAFF'")
    }
  }
}
