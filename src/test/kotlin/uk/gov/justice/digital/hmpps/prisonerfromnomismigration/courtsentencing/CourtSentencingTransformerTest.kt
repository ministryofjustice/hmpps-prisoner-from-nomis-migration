package uk.gov.justice.digital.hmpps.prisonerfromnomismigration.courtsentencing

import com.fasterxml.jackson.databind.json.JsonMapper
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import kotlinx.coroutines.test.runTest
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.helpers.log
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomisprisoner.model.CaseIdentifierResponse
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomisprisoner.model.CodeDescription
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomisprisoner.model.CourtCaseResponse
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomisprisoner.model.CourtEventChargeResponse
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomisprisoner.model.CourtEventResponse
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomisprisoner.model.CourtOrderResponse
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomisprisoner.model.OffenceResponse
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomisprisoner.model.OffenceResultCodeResponse
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomisprisoner.model.OffenderChargeResponse
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomisprisoner.model.SentenceResponse
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomisprisoner.model.SentenceTermResponse
import java.time.LocalDate
import java.time.LocalDateTime

private const val NOMIS_COURT_CASE_ID = 3L
private const val SENTENCING_HEARING_DATE = "2016-10-13T10:00:00"
private const val NON_SENTENCING_APPEARANCE = 317323934L
private const val SENTENCING_APPEARANCE = 318155094L
private const val SENTENCED_CRIME_OFFENCE_CODE = "SX03007"
private const val SENTENCED_CRIME_OFFENCE_CODE_2 = "SX03008"
private const val NON_SENTENCED_CRIME_OFFENCE_CODE = "SX03005"
private const val BOOKING_ID = 1139832L
private const val OFFENDER_NO = "G2577UD"

class CourtSentencingTransformerTest {

  @Nested
  inner class SentenceTransformation {
    @Test
    internal fun `will transform sentences correctly`() = runTest {
      courtCaseResponse.toMigrationDpsCourtCase().apply {
        assertThat(this.caseId).isEqualTo(NOMIS_COURT_CASE_ID)
        val nonSentencingAppearance = this.appearances[0]
        val firstAppearanceChargeNotYetSentenced =
          nonSentencingAppearance.charges.find { it.offenceCode == SENTENCED_CRIME_OFFENCE_CODE }!!
        val sentencingAppearance = this.appearances[1]
        val secondAppearanceSentencedCharge =
          sentencingAppearance.charges.find { it.offenceCode == SENTENCED_CRIME_OFFENCE_CODE }!!
        // confirm outcome of Guilty charge for first appearance
        assertThat(firstAppearanceChargeNotYetSentenced.legacyData.nomisOutcomeCode).isEqualTo("G")
        assertThat(firstAppearanceChargeNotYetSentenced.sentence?.sentenceId?.sequence).isNull()
        assertThat(secondAppearanceSentencedCharge.sentence).isNotNull
        // confirm outcome of Imprisonment charge for first appearance
        assertThat(secondAppearanceSentencedCharge.legacyData.nomisOutcomeCode).isEqualTo("1002")
        log.info(
          JsonMapper.builder()
            .addModule(JavaTimeModule())
            .build().writeValueAsString(this),
        )
      }
    }

    // @Test
    // internal fun `local running test using real case data`() = runTest {
    //   val json = this::class.java.getResource("/inputRecon.json")!!.readText()
    //
    //   val objectMapper = ObjectMapper()
    //     .registerModule(JavaTimeModule())
    //     .registerKotlinModule()
    //     .configure(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS, false)
    //     .configure(SerializationFeature.INDENT_OUTPUT, true)
    //     .setSerializationInclusion(JsonInclude.Include.NON_NULL)
    //   val courtCaseResponse2: CourtCaseResponse = objectMapper.readValue(json)
    //   courtCaseResponse2.toMigrationDpsCourtCase().apply {
    //     assertThat(this.caseId).isEqualTo(NOMIS_COURT_CASE_ID)
    //   }
    // }

    val courtCaseResponse = CourtCaseResponse(
      id = NOMIS_COURT_CASE_ID,
      offenderNo = OFFENDER_NO,
      bookingId = BOOKING_ID,
      primaryCaseInfoNumber = "T20160410",
      caseSequence = 1,
      caseStatus = CodeDescription("A", "Active"),
      legalCaseType = CodeDescription("A", "Adult"),
      beginDate = LocalDate.parse("2016-10-07"),
      courtId = "CNTRCC",
      statusUpdateReason = "A",
      createdDateTime = LocalDateTime.parse("2016-10-10T12:17:31.054058"),
      createdByUsername = "GQL99B",
      courtEvents = listOf(
        CourtEventResponse(
          id = NON_SENTENCING_APPEARANCE,
          caseId = 1478875,
          offenderNo = OFFENDER_NO,
          eventDateTime = LocalDateTime.parse("2016-10-07T10:00:00"),
          courtEventType = CodeDescription("CRT", "Court Appearance"),
          eventStatus = CodeDescription("COMP", "Completed"),
          directionCode = CodeDescription("OUT", "Out"),
          courtId = "CNTRCC",
          outcomeReasonCode = OffenceResultCodeResponse(
            chargeStatus = "A",
            code = "G",
            description = "Guilty",
            dispositionCode = "F",
            conviction = true,
          ),
          orderRequestedFlag = false,
          holdFlag = false,
          nextEventRequestFlag = true,
          createdDateTime = LocalDateTime.parse("2016-10-10T12:17:42.987789"),
          createdByUsername = "GQL99B",
          courtEventCharges = listOf(
            CourtEventChargeResponse(
              eventId = NON_SENTENCING_APPEARANCE,
              offenderCharge = OffenderChargeResponse(
                id = 3688291,
                offence = OffenceResponse(
                  offenceCode = SENTENCED_CRIME_OFFENCE_CODE,
                  statuteCode = "SX03",
                  description = "Assault on a person",
                ),
                offenceDate = LocalDate.parse("2016-03-17"),
                chargeStatus = CodeDescription("A", "Active"),
                resultCode1 = OffenceResultCodeResponse(
                  chargeStatus = "A",
                  code = "1002",
                  description = "Imprisonment",
                  dispositionCode = "F",
                  conviction = true,
                ),
                mostSeriousFlag = true,
                lidsOffenceNumber = 1,
                createdByUsername = "msmith",
              ),
              resultCode1 = OffenceResultCodeResponse(
                chargeStatus = "A",
                code = "G",
                description = "Guilty",
                dispositionCode = "F",
                conviction = true,
              ),
              mostSeriousFlag = true,
              createdByUsername = "msmith",
            ),
            CourtEventChargeResponse(
              eventId = NON_SENTENCING_APPEARANCE,
              offenderCharge = OffenderChargeResponse(
                id = 3688292,
                offence = OffenceResponse(
                  offenceCode = NON_SENTENCED_CRIME_OFFENCE_CODE,
                  statuteCode = "SX03",
                  description = "Second crime",
                ),
                offenceDate = LocalDate.parse("2016-03-04"),
                chargeStatus = CodeDescription("I", "Inactive"),
                resultCode1 = OffenceResultCodeResponse(
                  chargeStatus = "I",
                  code = "2008",
                  description = "Lie on File",
                  dispositionCode = "I",
                  conviction = false,
                ),
                mostSeriousFlag = false,
                lidsOffenceNumber = 2,
                createdByUsername = "msmith",
              ),
              resultCode1 = OffenceResultCodeResponse(
                chargeStatus = "A",
                code = "G",
                description = "Guilty",
                dispositionCode = "F",
                conviction = true,
              ),
              mostSeriousFlag = false,
              createdByUsername = "msmith",
            ),
          ),
          courtOrders = listOf(
            CourtOrderResponse(
              id = 1343548,
              eventId = NON_SENTENCING_APPEARANCE,
              courtDate = LocalDate.parse("2016-10-07"),
              issuingCourt = "CNTRCC",
              orderType = "AUTO",
              orderStatus = "A",
              nonReportFlag = false,
              sentencePurposes = emptyList(),
            ),
          ),
        ),
        CourtEventResponse(
          id = SENTENCING_APPEARANCE,
          caseId = 1478875,
          offenderNo = OFFENDER_NO,
          eventDateTime = LocalDateTime.parse(SENTENCING_HEARING_DATE),
          courtEventType = CodeDescription("CRT", "Court Appearance"),
          eventStatus = CodeDescription("COMP", "Completed"),
          directionCode = CodeDescription("OUT", "Out"),
          courtId = "CNTRCC",
          outcomeReasonCode = OffenceResultCodeResponse(
            chargeStatus = "A",
            code = "1002",
            description = "Imprisonment",
            dispositionCode = "F",
            conviction = true,
          ),
          orderRequestedFlag = false,
          holdFlag = false,
          nextEventRequestFlag = false,
          createdDateTime = LocalDateTime.parse("2016-10-17T11:24:43.904851"),
          createdByUsername = "GQL99B",
          courtEventCharges = listOf(
            CourtEventChargeResponse(
              eventId = SENTENCING_APPEARANCE,
              offenderCharge = OffenderChargeResponse(
                id = 3688291,
                offence = OffenceResponse(
                  offenceCode = SENTENCED_CRIME_OFFENCE_CODE,
                  statuteCode = "SX03",
                  description = "Assault on a person",
                ),
                offenceDate = LocalDate.parse("2016-03-17"),
                chargeStatus = CodeDescription("A", "Active"),
                resultCode1 = OffenceResultCodeResponse(
                  chargeStatus = "A",
                  code = "1002",
                  description = "Imprisonment",
                  dispositionCode = "F",
                  conviction = true,
                ),
                mostSeriousFlag = true,
                lidsOffenceNumber = 1,
                createdByUsername = "msmith",
              ),
              resultCode1 = OffenceResultCodeResponse(
                chargeStatus = "A",
                code = "1002",
                description = "Imprisonment",
                dispositionCode = "F",
                conviction = true,
              ),
              mostSeriousFlag = true,
              createdByUsername = "msmith",
            ),
            CourtEventChargeResponse(
              eventId = SENTENCING_APPEARANCE,
              offenderCharge = OffenderChargeResponse(
                id = 3688293,
                offence = OffenceResponse(
                  offenceCode = SENTENCED_CRIME_OFFENCE_CODE_2,
                  statuteCode = "SX03",
                  description = "Assault on a person 2",
                ),
                offenceDate = LocalDate.parse("2016-03-17"),
                chargeStatus = CodeDescription("A", "Active"),
                resultCode1 = OffenceResultCodeResponse(
                  chargeStatus = "A",
                  code = "1002",
                  description = "Imprisonment",
                  dispositionCode = "F",
                  conviction = true,
                ),
                mostSeriousFlag = true,
                lidsOffenceNumber = 1,
                createdByUsername = "msmith",
              ),
              resultCode1 = OffenceResultCodeResponse(
                chargeStatus = "A",
                code = "1002",
                description = "Imprisonment",
                dispositionCode = "F",
                conviction = true,
              ),
              mostSeriousFlag = true,
              createdByUsername = "msmith",
            ),
            CourtEventChargeResponse(
              eventId = SENTENCING_APPEARANCE,
              offenderCharge = OffenderChargeResponse(
                id = 3688292,
                offence = OffenceResponse(
                  offenceCode = NON_SENTENCED_CRIME_OFFENCE_CODE,
                  statuteCode = "SX03",
                  description = "Second crime",
                ),
                offenceDate = LocalDate.parse("2016-03-04"),
                chargeStatus = CodeDescription("I", "Inactive"),
                resultCode1 = OffenceResultCodeResponse(
                  chargeStatus = "I",
                  code = "2008",
                  description = "Lie on File",
                  dispositionCode = "I",
                  conviction = false,
                ),
                mostSeriousFlag = false,
                lidsOffenceNumber = 2,
                createdByUsername = "msmith",
              ),
              resultCode1 = OffenceResultCodeResponse(
                chargeStatus = "I",
                code = "2008",
                description = "Lie on File",
                dispositionCode = "I",
                conviction = false,
              ),
              mostSeriousFlag = false,
              createdByUsername = "msmith",
            ),
          ),
          courtOrders = listOf(
            CourtOrderResponse(
              id = 1346801,
              eventId = SENTENCING_APPEARANCE,
              courtDate = LocalDate.parse("2016-10-13"),
              issuingCourt = "CNTRCC",
              orderType = "AUTO",
              orderStatus = "A",
              nonReportFlag = false,
              sentencePurposes = emptyList(),
            ),
          ),
        ),
      ),
      offenderCharges = listOf(
        OffenderChargeResponse(
          id = 3688291,
          offence = OffenceResponse(
            offenceCode = SENTENCED_CRIME_OFFENCE_CODE,
            statuteCode = "SX03",
            description = "Assault on a person",
          ),
          offenceDate = LocalDate.parse("2016-03-17"),
          chargeStatus = CodeDescription("A", "Active"),
          resultCode1 = OffenceResultCodeResponse(
            chargeStatus = "A",
            code = "1002",
            description = "Imprisonment",
            dispositionCode = "F",
            conviction = true,
          ),
          mostSeriousFlag = true,
          lidsOffenceNumber = 1,
          createdByUsername = "msmith",
        ),
        OffenderChargeResponse(
          id = 3688293,
          offence = OffenceResponse(
            offenceCode = SENTENCED_CRIME_OFFENCE_CODE_2,
            statuteCode = "SX03",
            description = "Assault on a person 2",
          ),
          offenceDate = LocalDate.parse("2016-03-17"),
          chargeStatus = CodeDescription("A", "Active"),
          resultCode1 = OffenceResultCodeResponse(
            chargeStatus = "A",
            code = "1002",
            description = "Imprisonment",
            dispositionCode = "F",
            conviction = true,
          ),
          mostSeriousFlag = true,
          lidsOffenceNumber = 1,
          createdByUsername = "msmith",
        ),
        OffenderChargeResponse(
          id = 3688292,
          offence = OffenceResponse(
            offenceCode = NON_SENTENCED_CRIME_OFFENCE_CODE,
            statuteCode = "SX03",
            description = "Second crime",
          ),
          offenceDate = LocalDate.parse("2016-03-04"),
          chargeStatus = CodeDescription("I", "Inactive"),
          resultCode1 = OffenceResultCodeResponse(
            chargeStatus = "I",
            code = "2008",
            description = "Lie on File",
            dispositionCode = "I",
            conviction = false,
          ),
          mostSeriousFlag = false,
          lidsOffenceNumber = 2,
          createdByUsername = "msmith",
        ),
      ),
      caseInfoNumbers = listOf(
        CaseIdentifierResponse(
          type = "CASE/INFO#",
          reference = "T20160410",
          createDateTime = LocalDateTime.parse("2016-10-10T12:17:31.076339"),
          auditModuleName = "OCDCCASE",
        ),
      ),
      sentences = listOf(
        SentenceResponse(
          bookingId = BOOKING_ID,
          sentenceSeq = 1,
          status = "A",
          calculationType = CodeDescription("ADIMP", "CJA03 Standard Determinate Sentence"),
          category = CodeDescription("2003", "2003 Act"),
          startDate = LocalDate.parse("2016-10-31"),
          courtOrder = CourtOrderResponse(
            id = 1346801,
            eventId = SENTENCING_APPEARANCE,
            courtDate = LocalDate.parse("2016-10-13"),
            issuingCourt = "CNTRCC",
            orderType = "AUTO",
            orderStatus = "A",
            nonReportFlag = false,
            sentencePurposes = emptyList(),
          ),
          endDate = LocalDate.parse("2020-06-05"),
          caseId = 1478875,
          crdCalculatedDate = LocalDate.parse("2018-04-24"),
          ledCalculatedDate = LocalDate.parse("2020-01-24"),
          sedCalculatedDate = LocalDate.parse("2020-01-26"),
          sentenceLevel = "IND",
          statusUpdateReason = "A",
          dischargeDate = LocalDate.parse("2020-05-21"),
          lineSequence = 1,
          hdcExclusionFlag = true,
          hdcExclusionReason = "PIMMS1",
          createdDateTime = LocalDateTime.parse("2016-10-17T11:26:09.077458"),
          createdByUsername = "GQL99B",
          sentenceTerms = listOf(
            SentenceTermResponse(
              termSequence = 1,
              sentenceTermType = CodeDescription("IMP", "Imprisonment"),
              months = 42,
              startDate = LocalDate.parse("2016-11-08"),
              endDate = LocalDate.parse("2020-04-28"),
              lifeSentenceFlag = false,
              prisonId = "OUT",
              createdByUsername = "msmith",
            ),
          ),
          offenderCharges = listOf(
            OffenderChargeResponse(
              id = 3688291,
              offence = OffenceResponse(
                offenceCode = SENTENCED_CRIME_OFFENCE_CODE,
                statuteCode = "SX03",
                description = "Assault on a person",
              ),
              offenceDate = LocalDate.parse("2016-03-17"),
              chargeStatus = CodeDescription("A", "Active"),
              resultCode1 = OffenceResultCodeResponse(
                chargeStatus = "A",
                code = "1002",
                description = "Imprisonment",
                dispositionCode = "F",
                conviction = true,
              ),
              mostSeriousFlag = true,
              lidsOffenceNumber = 1,
              createdByUsername = "msmith",
            ),
            OffenderChargeResponse(
              id = 3688293,
              offence = OffenceResponse(
                offenceCode = SENTENCED_CRIME_OFFENCE_CODE_2,
                statuteCode = "SX03",
                description = "Assault on a person 2",
              ),
              offenceDate = LocalDate.parse("2016-03-17"),
              chargeStatus = CodeDescription("A", "Active"),
              resultCode1 = OffenceResultCodeResponse(
                chargeStatus = "A",
                code = "1002",
                description = "Imprisonment",
                dispositionCode = "F",
                conviction = true,
              ),
              mostSeriousFlag = true,
              lidsOffenceNumber = 1,
              createdByUsername = "msmith",
            ),
          ),
          prisonId = "BAI",
          missingCourtOffenderChargeIds = emptyList(),
        ),
      ),
      sourceCombinedCaseIds = listOf(),
    )
  }
}
