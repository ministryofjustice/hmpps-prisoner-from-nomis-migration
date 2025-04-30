package uk.gov.justice.digital.hmpps.prisonerfromnomismigration.courtsentencing

import com.fasterxml.jackson.databind.json.JsonMapper
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.fasterxml.jackson.module.kotlin.kotlinModule
import com.fasterxml.jackson.module.kotlin.readValue
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
      courtCaseResponse.toMigrationDpsCourtCase(null).apply {
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

    @Test
    internal fun `will transform sentences again correctly`() = runTest {
      val ddd: CourtCaseResponse = JsonMapper.builder()
        .addModule(JavaTimeModule()).addModule(kotlinModule())
        .build().readValue<CourtCaseResponse>(courtCaseFromJson)
      ddd.toMigrationDpsCourtCase(null).apply {
        log.info(
          JsonMapper.builder()
            .addModule(JavaTimeModule())
            .build().writeValueAsString(this),
        )
      }
    }

    val courtCaseFromJson = """
    {
    "id": 660258,
    "offenderNo": "A7163CK",
    "bookingId": 576330,
    "primaryCaseInfoNumber": "STD LR",
    "caseSequence": 1,
    "caseStatus": {
      "code": "A",
      "description": "Active"
    },
    "legalCaseType": {
      "code": "A",
      "description": "Adult"
    },
    "beginDate": "2011-08-03",
    "courtId": "COACD",
    "lidsCaseId": 389,
    "lidsCombinedCaseId": 0,
    "lidsCaseNumber": 1,
    "createdDateTime": "2012-02-05T05:04:26.458656",
    "createdByUsername": "XTAG",
    "courtEvents": [
      {
        "id": 130245322,
        "caseId": 660258,
        "offenderNo": "A7163CK",
        "eventDateTime": "2012-08-02T10:00:00",
        "courtEventType": {
          "code": "AP",
          "description": "Discharge to Court on Appeal"
        },
        "eventStatus": {
          "code": "COMP",
          "description": "Completed"
        },
        "directionCode": {
          "code": "OUT",
          "description": "Out"
        },
        "courtId": "COACD",
        "outcomeReasonCode": {
          "chargeStatus": "A",
          "code": "3045",
          "description": "Appeal Dismissed",
          "dispositionCode": "F",
          "conviction": true
        },
        "commentText": "T20117152",
        "orderRequestedFlag": false,
        "holdFlag": false,
        "nextEventRequestFlag": false,
        "createdDateTime": "2012-08-22T12:18:40.316758",
        "createdByUsername": "HQO58C",
        "courtEventCharges": [
          {
            "eventId": 130245322,
            "offenderCharge": {
              "id": 1453012,
              "offence": {
                "offenceCode": "PK78001-008NA",
                "statuteCode": "ZZ",
                "description": "INDECENT PHOTOGRAPHS OF CHILDREN"
              },
              "offenceDate": "2010-10-31",
              "chargeStatus": {
                "code": "A",
                "description": "Active"
              },
              "resultCode1": {
                "chargeStatus": "A",
                "code": "1501",
                "description": "Recall to Prison",
                "dispositionCode": "F",
                "conviction": true
              },
              "mostSeriousFlag": false,
              "lidsOffenceNumber": 23
            },
            "offenceDate": "2010-10-31",
            "resultCode1": {
              "chargeStatus": "A",
              "code": "3045",
              "description": "Appeal Dismissed",
              "dispositionCode": "F",
              "conviction": true
            },
            "mostSeriousFlag": false
          },
          {
            "eventId": 130245322,
            "offenderCharge": {
              "id": 1453013,
              "offence": {
                "offenceCode": "SX03013-014N",
                "statuteCode": "ZZ",
                "description": "RAPE OF A CHILD U13 (SOA 2003 s5)"
              },
              "offenceDate": "2006-10-17",
              "chargeStatus": {
                "code": "A",
                "description": "Active"
              },
              "resultCode1": {
                "chargeStatus": "A",
                "code": "1501",
                "description": "Recall to Prison",
                "dispositionCode": "F",
                "conviction": true
              },
              "mostSeriousFlag": false,
              "lidsOffenceNumber": 7
            },
            "offenceDate": "2006-10-17",
            "resultCode1": {
              "chargeStatus": "A",
              "code": "3045",
              "description": "Appeal Dismissed",
              "dispositionCode": "F",
              "conviction": true
            },
            "mostSeriousFlag": false
          },
          {
            "eventId": 130245322,
            "offenderCharge": {
              "id": 1453014,
              "offence": {
                "offenceCode": "SX56070-072N",
                "statuteCode": "ZZ",
                "description": "RAPE ON CHILD (SOA 1956 s69)"
              },
              "offenceDate": "2001-10-17",
              "chargeStatus": {
                "code": "A",
                "description": "Active"
              },
              "resultCode1": {
                "chargeStatus": "A",
                "code": "1501",
                "description": "Recall to Prison",
                "dispositionCode": "F",
                "conviction": true
              },
              "mostSeriousFlag": false,
              "lidsOffenceNumber": 1
            },
            "offenceDate": "2001-10-17",
            "resultCode1": {
              "chargeStatus": "A",
              "code": "3045",
              "description": "Appeal Dismissed",
              "dispositionCode": "F",
              "conviction": true
            },
            "mostSeriousFlag": false
          },
          {
            "eventId": 130245322,
            "offenderCharge": {
              "id": 1453015,
              "offence": {
                "offenceCode": "SX56070-072N",
                "statuteCode": "ZZ",
                "description": "RAPE ON CHILD (SOA 1956 s69)"
              },
              "offenceDate": "2004-04-30",
              "chargeStatus": {
                "code": "A",
                "description": "Active"
              },
              "resultCode1": {
                "chargeStatus": "A",
                "code": "1501",
                "description": "Recall to Prison",
                "dispositionCode": "F",
                "conviction": true
              },
              "mostSeriousFlag": false,
              "lidsOffenceNumber": 4
            },
            "offenceDate": "2004-04-30",
            "resultCode1": {
              "chargeStatus": "A",
              "code": "3045",
              "description": "Appeal Dismissed",
              "dispositionCode": "F",
              "conviction": true
            },
            "mostSeriousFlag": false
          },
          {
            "eventId": 130245322,
            "offenderCharge": {
              "id": 1453016,
              "offence": {
                "offenceCode": "PK78001-008NA",
                "statuteCode": "ZZ",
                "description": "INDECENT PHOTOGRAPHS OF CHILDREN"
              },
              "offenceDate": "2010-03-28",
              "chargeStatus": {
                "code": "A",
                "description": "Active"
              },
              "resultCode1": {
                "chargeStatus": "A",
                "code": "1501",
                "description": "Recall to Prison",
                "dispositionCode": "F",
                "conviction": true
              },
              "mostSeriousFlag": false,
              "lidsOffenceNumber": 15
            },
            "offenceDate": "2010-03-28",
            "resultCode1": {
              "chargeStatus": "A",
              "code": "3045",
              "description": "Appeal Dismissed",
              "dispositionCode": "F",
              "conviction": true
            },
            "mostSeriousFlag": false
          },
          {
            "eventId": 130245322,
            "offenderCharge": {
              "id": 1453017,
              "offence": {
                "offenceCode": "PK78001-008NA",
                "statuteCode": "ZZ",
                "description": "INDECENT PHOTOGRAPHS OF CHILDREN"
              },
              "offenceDate": "2011-06-30",
              "chargeStatus": {
                "code": "A",
                "description": "Active"
              },
              "resultCode1": {
                "chargeStatus": "A",
                "code": "1501",
                "description": "Recall to Prison",
                "dispositionCode": "F",
                "conviction": true
              },
              "mostSeriousFlag": false,
              "lidsOffenceNumber": 27
            },
            "offenceDate": "2011-06-30",
            "resultCode1": {
              "chargeStatus": "A",
              "code": "3045",
              "description": "Appeal Dismissed",
              "dispositionCode": "F",
              "conviction": true
            },
            "mostSeriousFlag": false
          },
          {
            "eventId": 130245322,
            "offenderCharge": {
              "id": 1453018,
              "offence": {
                "offenceCode": "SX03013-014N",
                "statuteCode": "ZZ",
                "description": "RAPE OF A CHILD U13 (SOA 2003 s5)"
              },
              "offenceDate": "2005-10-17",
              "chargeStatus": {
                "code": "A",
                "description": "Active"
              },
              "resultCode1": {
                "chargeStatus": "A",
                "code": "1501",
                "description": "Recall to Prison",
                "dispositionCode": "F",
                "conviction": true
              },
              "mostSeriousFlag": false,
              "lidsOffenceNumber": 6
            },
            "offenceDate": "2005-10-17",
            "resultCode1": {
              "chargeStatus": "A",
              "code": "3045",
              "description": "Appeal Dismissed",
              "dispositionCode": "F",
              "conviction": true
            },
            "mostSeriousFlag": false
          },
          {
            "eventId": 130245322,
            "offenderCharge": {
              "id": 1453019,
              "offence": {
                "offenceCode": "PK78001-008NA",
                "statuteCode": "ZZ",
                "description": "INDECENT PHOTOGRAPHS OF CHILDREN"
              },
              "offenceDate": "2011-03-27",
              "chargeStatus": {
                "code": "A",
                "description": "Active"
              },
              "resultCode1": {
                "chargeStatus": "A",
                "code": "1501",
                "description": "Recall to Prison",
                "dispositionCode": "F",
                "conviction": true
              },
              "mostSeriousFlag": false,
              "lidsOffenceNumber": 24
            },
            "offenceDate": "2011-03-27",
            "resultCode1": {
              "chargeStatus": "A",
              "code": "3045",
              "description": "Appeal Dismissed",
              "dispositionCode": "F",
              "conviction": true
            },
            "mostSeriousFlag": false
          },
          {
            "eventId": 130245322,
            "offenderCharge": {
              "id": 1453020,
              "offence": {
                "offenceCode": "SX56025-029N",
                "statuteCode": "ZZ",
                "description": "INDECENT ASSAULT ON A CHILD"
              },
              "offenceDate": "2003-10-17",
              "chargeStatus": {
                "code": "A",
                "description": "Active"
              },
              "resultCode1": {
                "chargeStatus": "A",
                "code": "1501",
                "description": "Recall to Prison",
                "dispositionCode": "F",
                "conviction": true
              },
              "mostSeriousFlag": false,
              "lidsOffenceNumber": 10
            },
            "offenceDate": "2003-10-17",
            "resultCode1": {
              "chargeStatus": "A",
              "code": "3045",
              "description": "Appeal Dismissed",
              "dispositionCode": "F",
              "conviction": true
            },
            "mostSeriousFlag": false
          },
          {
            "eventId": 130245322,
            "offenderCharge": {
              "id": 1453021,
              "offence": {
                "offenceCode": "PK78001-008NA",
                "statuteCode": "ZZ",
                "description": "INDECENT PHOTOGRAPHS OF CHILDREN"
              },
              "offenceDate": "2011-05-01",
              "chargeStatus": {
                "code": "A",
                "description": "Active"
              },
              "resultCode1": {
                "chargeStatus": "A",
                "code": "1501",
                "description": "Recall to Prison",
                "dispositionCode": "F",
                "conviction": true
              },
              "mostSeriousFlag": false,
              "lidsOffenceNumber": 21
            },
            "offenceDate": "2011-05-01",
            "resultCode1": {
              "chargeStatus": "A",
              "code": "3045",
              "description": "Appeal Dismissed",
              "dispositionCode": "F",
              "conviction": true
            },
            "mostSeriousFlag": false
          },
          {
            "eventId": 130245322,
            "offenderCharge": {
              "id": 1453022,
              "offence": {
                "offenceCode": "SX03015-016N",
                "statuteCode": "ZZ",
                "description": "ASSAULT OF A CHILD U13 BY PENETRATION (SOA 2003 s6"
              },
              "offenceDate": "2006-10-17",
              "chargeStatus": {
                "code": "A",
                "description": "Active"
              },
              "resultCode1": {
                "chargeStatus": "A",
                "code": "1501",
                "description": "Recall to Prison",
                "dispositionCode": "F",
                "conviction": true
              },
              "mostSeriousFlag": false,
              "lidsOffenceNumber": 14
            },
            "offenceDate": "2006-10-17",
            "resultCode1": {
              "chargeStatus": "A",
              "code": "3045",
              "description": "Appeal Dismissed",
              "dispositionCode": "F",
              "conviction": true
            },
            "mostSeriousFlag": false
          },
          {
            "eventId": 130245322,
            "offenderCharge": {
              "id": 1453023,
              "offence": {
                "offenceCode": "PK78001-008NA",
                "statuteCode": "ZZ",
                "description": "INDECENT PHOTOGRAPHS OF CHILDREN"
              },
              "offenceDate": "2010-10-31",
              "chargeStatus": {
                "code": "A",
                "description": "Active"
              },
              "resultCode1": {
                "chargeStatus": "A",
                "code": "1501",
                "description": "Recall to Prison",
                "dispositionCode": "F",
                "conviction": true
              },
              "mostSeriousFlag": false,
              "lidsOffenceNumber": 17
            },
            "offenceDate": "2010-10-31",
            "resultCode1": {
              "chargeStatus": "A",
              "code": "3045",
              "description": "Appeal Dismissed",
              "dispositionCode": "F",
              "conviction": true
            },
            "mostSeriousFlag": false
          },
          {
            "eventId": 130245322,
            "offenderCharge": {
              "id": 1453024,
              "offence": {
                "offenceCode": "PK78001-008NA",
                "statuteCode": "ZZ",
                "description": "INDECENT PHOTOGRAPHS OF CHILDREN"
              },
              "offenceDate": "2011-05-23",
              "chargeStatus": {
                "code": "A",
                "description": "Active"
              },
              "resultCode1": {
                "chargeStatus": "A",
                "code": "1501",
                "description": "Recall to Prison",
                "dispositionCode": "F",
                "conviction": true
              },
              "mostSeriousFlag": false,
              "lidsOffenceNumber": 25
            },
            "offenceDate": "2011-05-23",
            "resultCode1": {
              "chargeStatus": "A",
              "code": "3045",
              "description": "Appeal Dismissed",
              "dispositionCode": "F",
              "conviction": true
            },
            "mostSeriousFlag": false
          },
          {
            "eventId": 130245322,
            "offenderCharge": {
              "id": 1453025,
              "offence": {
                "offenceCode": "SX03013-014N",
                "statuteCode": "ZZ",
                "description": "RAPE OF A CHILD U13 (SOA 2003 s5)"
              },
              "offenceDate": "2004-10-17",
              "chargeStatus": {
                "code": "A",
                "description": "Active"
              },
              "resultCode1": {
                "chargeStatus": "A",
                "code": "1501",
                "description": "Recall to Prison",
                "dispositionCode": "F",
                "conviction": true
              },
              "mostSeriousFlag": false,
              "lidsOffenceNumber": 5
            },
            "offenceDate": "2004-10-17",
            "resultCode1": {
              "chargeStatus": "A",
              "code": "3045",
              "description": "Appeal Dismissed",
              "dispositionCode": "F",
              "conviction": true
            },
            "mostSeriousFlag": false
          },
          {
            "eventId": 130245322,
            "offenderCharge": {
              "id": 1453026,
              "offence": {
                "offenceCode": "SX56025-029N",
                "statuteCode": "ZZ",
                "description": "INDECENT ASSAULT ON A CHILD"
              },
              "offenceDate": "2004-04-30",
              "chargeStatus": {
                "code": "A",
                "description": "Active"
              },
              "resultCode1": {
                "chargeStatus": "A",
                "code": "1501",
                "description": "Recall to Prison",
                "dispositionCode": "F",
                "conviction": true
              },
              "mostSeriousFlag": false,
              "lidsOffenceNumber": 11
            },
            "offenceDate": "2004-04-30",
            "resultCode1": {
              "chargeStatus": "A",
              "code": "3045",
              "description": "Appeal Dismissed",
              "dispositionCode": "F",
              "conviction": true
            },
            "mostSeriousFlag": false
          },
          {
            "eventId": 130245322,
            "offenderCharge": {
              "id": 1453027,
              "offence": {
                "offenceCode": "SX03015-016N",
                "statuteCode": "ZZ",
                "description": "ASSAULT OF A CHILD U13 BY PENETRATION (SOA 2003 s6"
              },
              "offenceDate": "2005-10-17",
              "chargeStatus": {
                "code": "A",
                "description": "Active"
              },
              "resultCode1": {
                "chargeStatus": "A",
                "code": "1501",
                "description": "Recall to Prison",
                "dispositionCode": "F",
                "conviction": true
              },
              "mostSeriousFlag": false,
              "lidsOffenceNumber": 12
            },
            "offenceDate": "2005-10-17",
            "resultCode1": {
              "chargeStatus": "A",
              "code": "3045",
              "description": "Appeal Dismissed",
              "dispositionCode": "F",
              "conviction": true
            },
            "mostSeriousFlag": false
          },
          {
            "eventId": 130245322,
            "offenderCharge": {
              "id": 1453028,
              "offence": {
                "offenceCode": "SX03015-016N",
                "statuteCode": "ZZ",
                "description": "ASSAULT OF A CHILD U13 BY PENETRATION (SOA 2003 s6"
              },
              "offenceDate": "2005-10-17",
              "chargeStatus": {
                "code": "A",
                "description": "Active"
              },
              "resultCode1": {
                "chargeStatus": "A",
                "code": "1501",
                "description": "Recall to Prison",
                "dispositionCode": "F",
                "conviction": true
              },
              "mostSeriousFlag": false,
              "lidsOffenceNumber": 13
            },
            "offenceDate": "2005-10-17",
            "resultCode1": {
              "chargeStatus": "A",
              "code": "3045",
              "description": "Appeal Dismissed",
              "dispositionCode": "F",
              "conviction": true
            },
            "mostSeriousFlag": false
          },
          {
            "eventId": 130245322,
            "offenderCharge": {
              "id": 1453029,
              "offence": {
                "offenceCode": "SX56070-072N",
                "statuteCode": "ZZ",
                "description": "RAPE ON CHILD (SOA 1956 s69)"
              },
              "offenceDate": "2002-10-17",
              "chargeStatus": {
                "code": "A",
                "description": "Active"
              },
              "resultCode1": {
                "chargeStatus": "A",
                "code": "1501",
                "description": "Recall to Prison",
                "dispositionCode": "F",
                "conviction": true
              },
              "mostSeriousFlag": false,
              "lidsOffenceNumber": 2
            },
            "offenceDate": "2002-10-17",
            "resultCode1": {
              "chargeStatus": "A",
              "code": "3045",
              "description": "Appeal Dismissed",
              "dispositionCode": "F",
              "conviction": true
            },
            "mostSeriousFlag": false
          },
          {
            "eventId": 130245322,
            "offenderCharge": {
              "id": 1453030,
              "offence": {
                "offenceCode": "PK78001-008NA",
                "statuteCode": "ZZ",
                "description": "INDECENT PHOTOGRAPHS OF CHILDREN"
              },
              "offenceDate": "2011-05-01",
              "chargeStatus": {
                "code": "A",
                "description": "Active"
              },
              "resultCode1": {
                "chargeStatus": "A",
                "code": "1501",
                "description": "Recall to Prison",
                "dispositionCode": "F",
                "conviction": true
              },
              "mostSeriousFlag": false,
              "lidsOffenceNumber": 22
            },
            "offenceDate": "2011-05-01",
            "resultCode1": {
              "chargeStatus": "A",
              "code": "3045",
              "description": "Appeal Dismissed",
              "dispositionCode": "F",
              "conviction": true
            },
            "mostSeriousFlag": false
          },
          {
            "eventId": 130245322,
            "offenderCharge": {
              "id": 1453031,
              "offence": {
                "offenceCode": "PK78001-008NA",
                "statuteCode": "ZZ",
                "description": "INDECENT PHOTOGRAPHS OF CHILDREN"
              },
              "offenceDate": "2010-10-31",
              "chargeStatus": {
                "code": "A",
                "description": "Active"
              },
              "resultCode1": {
                "chargeStatus": "A",
                "code": "1501",
                "description": "Recall to Prison",
                "dispositionCode": "F",
                "conviction": true
              },
              "mostSeriousFlag": false,
              "lidsOffenceNumber": 20
            },
            "offenceDate": "2010-10-31",
            "resultCode1": {
              "chargeStatus": "A",
              "code": "3045",
              "description": "Appeal Dismissed",
              "dispositionCode": "F",
              "conviction": true
            },
            "mostSeriousFlag": false
          },
          {
            "eventId": 130245322,
            "offenderCharge": {
              "id": 1453032,
              "offence": {
                "offenceCode": "PK78001-008NA",
                "statuteCode": "ZZ",
                "description": "INDECENT PHOTOGRAPHS OF CHILDREN"
              },
              "offenceDate": "2010-03-27",
              "chargeStatus": {
                "code": "A",
                "description": "Active"
              },
              "resultCode1": {
                "chargeStatus": "A",
                "code": "1501",
                "description": "Recall to Prison",
                "dispositionCode": "F",
                "conviction": true
              },
              "mostSeriousFlag": false,
              "lidsOffenceNumber": 16
            },
            "offenceDate": "2010-03-27",
            "resultCode1": {
              "chargeStatus": "A",
              "code": "3045",
              "description": "Appeal Dismissed",
              "dispositionCode": "F",
              "conviction": true
            },
            "mostSeriousFlag": false
          },
          {
            "eventId": 130245322,
            "offenderCharge": {
              "id": 1453033,
              "offence": {
                "offenceCode": "PK78001-008NA",
                "statuteCode": "ZZ",
                "description": "INDECENT PHOTOGRAPHS OF CHILDREN"
              },
              "offenceDate": "2010-10-31",
              "chargeStatus": {
                "code": "A",
                "description": "Active"
              },
              "resultCode1": {
                "chargeStatus": "A",
                "code": "1501",
                "description": "Recall to Prison",
                "dispositionCode": "F",
                "conviction": true
              },
              "mostSeriousFlag": false,
              "lidsOffenceNumber": 19
            },
            "offenceDate": "2010-10-31",
            "resultCode1": {
              "chargeStatus": "A",
              "code": "3045",
              "description": "Appeal Dismissed",
              "dispositionCode": "F",
              "conviction": true
            },
            "mostSeriousFlag": false
          },
          {
            "eventId": 130245322,
            "offenderCharge": {
              "id": 1453034,
              "offence": {
                "offenceCode": "PK78001-008NA",
                "statuteCode": "ZZ",
                "description": "INDECENT PHOTOGRAPHS OF CHILDREN"
              },
              "offenceDate": "2010-05-28",
              "chargeStatus": {
                "code": "A",
                "description": "Active"
              },
              "resultCode1": {
                "chargeStatus": "A",
                "code": "1501",
                "description": "Recall to Prison",
                "dispositionCode": "F",
                "conviction": true
              },
              "mostSeriousFlag": false,
              "lidsOffenceNumber": 26
            },
            "offenceDate": "2010-05-28",
            "resultCode1": {
              "chargeStatus": "A",
              "code": "3045",
              "description": "Appeal Dismissed",
              "dispositionCode": "F",
              "conviction": true
            },
            "mostSeriousFlag": false
          },
          {
            "eventId": 130245322,
            "offenderCharge": {
              "id": 1453035,
              "offence": {
                "offenceCode": "PK78001-008NA",
                "statuteCode": "ZZ",
                "description": "INDECENT PHOTOGRAPHS OF CHILDREN"
              },
              "offenceDate": "2010-10-31",
              "chargeStatus": {
                "code": "A",
                "description": "Active"
              },
              "resultCode1": {
                "chargeStatus": "A",
                "code": "1501",
                "description": "Recall to Prison",
                "dispositionCode": "F",
                "conviction": true
              },
              "mostSeriousFlag": false,
              "lidsOffenceNumber": 18
            },
            "offenceDate": "2010-10-31",
            "resultCode1": {
              "chargeStatus": "A",
              "code": "3045",
              "description": "Appeal Dismissed",
              "dispositionCode": "F",
              "conviction": true
            },
            "mostSeriousFlag": false
          },
          {
            "eventId": 130245322,
            "offenderCharge": {
              "id": 1453036,
              "offence": {
                "offenceCode": "SX56070-072N",
                "statuteCode": "ZZ",
                "description": "RAPE ON CHILD (SOA 1956 s69)"
              },
              "offenceDate": "2003-10-17",
              "chargeStatus": {
                "code": "A",
                "description": "Active"
              },
              "resultCode1": {
                "chargeStatus": "A",
                "code": "1501",
                "description": "Recall to Prison",
                "dispositionCode": "F",
                "conviction": true
              },
              "mostSeriousFlag": false,
              "lidsOffenceNumber": 3
            },
            "offenceDate": "2003-10-17",
            "resultCode1": {
              "chargeStatus": "A",
              "code": "3045",
              "description": "Appeal Dismissed",
              "dispositionCode": "F",
              "conviction": true
            },
            "mostSeriousFlag": false
          },
          {
            "eventId": 130245322,
            "offenderCharge": {
              "id": 1453037,
              "offence": {
                "offenceCode": "SX56025-029N",
                "statuteCode": "ZZ",
                "description": "INDECENT ASSAULT ON A CHILD"
              },
              "offenceDate": "2001-10-17",
              "chargeStatus": {
                "code": "A",
                "description": "Active"
              },
              "resultCode1": {
                "chargeStatus": "A",
                "code": "1501",
                "description": "Recall to Prison",
                "dispositionCode": "F",
                "conviction": true
              },
              "mostSeriousFlag": false,
              "lidsOffenceNumber": 8
            },
            "offenceDate": "2001-10-17",
            "resultCode1": {
              "chargeStatus": "A",
              "code": "3045",
              "description": "Appeal Dismissed",
              "dispositionCode": "F",
              "conviction": true
            },
            "mostSeriousFlag": false
          },
          {
            "eventId": 130245322,
            "offenderCharge": {
              "id": 1453038,
              "offence": {
                "offenceCode": "PK78001-008NA",
                "statuteCode": "ZZ",
                "description": "INDECENT PHOTOGRAPHS OF CHILDREN"
              },
              "offenceDate": "2011-06-22",
              "chargeStatus": {
                "code": "A",
                "description": "Active"
              },
              "resultCode1": {
                "chargeStatus": "A",
                "code": "1501",
                "description": "Recall to Prison",
                "dispositionCode": "F",
                "conviction": true
              },
              "mostSeriousFlag": false,
              "lidsOffenceNumber": 28
            },
            "offenceDate": "2011-06-22",
            "resultCode1": {
              "chargeStatus": "A",
              "code": "3045",
              "description": "Appeal Dismissed",
              "dispositionCode": "F",
              "conviction": true
            },
            "mostSeriousFlag": false
          },
          {
            "eventId": 130245322,
            "offenderCharge": {
              "id": 1453039,
              "offence": {
                "offenceCode": "SX56025-029N",
                "statuteCode": "ZZ",
                "description": "INDECENT ASSAULT ON A CHILD"
              },
              "offenceDate": "2002-10-17",
              "chargeStatus": {
                "code": "A",
                "description": "Active"
              },
              "resultCode1": {
                "chargeStatus": "A",
                "code": "1501",
                "description": "Recall to Prison",
                "dispositionCode": "F",
                "conviction": true
              },
              "mostSeriousFlag": false,
              "lidsOffenceNumber": 9
            },
            "offenceDate": "2002-10-17",
            "resultCode1": {
              "chargeStatus": "A",
              "code": "3045",
              "description": "Appeal Dismissed",
              "dispositionCode": "F",
              "conviction": true
            },
            "mostSeriousFlag": false
          },
          {
            "eventId": 130245322,
            "offenderCharge": {
              "id": 1583786,
              "offence": {
                "offenceCode": "SX03002",
                "statuteCode": "SX03",
                "description": "Rape a woman 16 years of age or over - SOA 2003"
              },
              "offenceDate": "2001-10-17",
              "offenceEndDate": "2002-10-17",
              "chargeStatus": {
                "code": "A",
                "description": "Active"
              },
              "resultCode1": {
                "chargeStatus": "A",
                "code": "1501",
                "description": "Recall to Prison",
                "dispositionCode": "F",
                "conviction": true
              },
              "mostSeriousFlag": false,
              "lidsOffenceNumber": 29
            },
            "offenceDate": "2001-10-17",
            "offenceEndDate": "2002-10-17",
            "resultCode1": {
              "chargeStatus": "A",
              "code": "3045",
              "description": "Appeal Dismissed",
              "dispositionCode": "F",
              "conviction": true
            },
            "mostSeriousFlag": false
          },
          {
            "eventId": 130245322,
            "offenderCharge": {
              "id": 1583787,
              "offence": {
                "offenceCode": "SX03002",
                "statuteCode": "SX03",
                "description": "Rape a woman 16 years of age or over - SOA 2003"
              },
              "offenceDate": "2002-10-17",
              "offenceEndDate": "2003-10-17",
              "chargeStatus": {
                "code": "A",
                "description": "Active"
              },
              "resultCode1": {
                "chargeStatus": "A",
                "code": "1501",
                "description": "Recall to Prison",
                "dispositionCode": "F",
                "conviction": true
              },
              "mostSeriousFlag": false,
              "lidsOffenceNumber": 30
            },
            "offenceDate": "2002-10-17",
            "offenceEndDate": "2003-10-17",
            "resultCode1": {
              "chargeStatus": "A",
              "code": "3045",
              "description": "Appeal Dismissed",
              "dispositionCode": "F",
              "conviction": true
            },
            "mostSeriousFlag": false
          },
          {
            "eventId": 130245322,
            "offenderCharge": {
              "id": 1583788,
              "offence": {
                "offenceCode": "SX03002",
                "statuteCode": "SX03",
                "description": "Rape a woman 16 years of age or over - SOA 2003"
              },
              "offenceDate": "2003-10-17",
              "offenceEndDate": "2004-04-30",
              "chargeStatus": {
                "code": "A",
                "description": "Active"
              },
              "resultCode1": {
                "chargeStatus": "A",
                "code": "1501",
                "description": "Recall to Prison",
                "dispositionCode": "F",
                "conviction": true
              },
              "mostSeriousFlag": false,
              "lidsOffenceNumber": 31
            },
            "offenceDate": "2003-10-17",
            "offenceEndDate": "2004-04-30",
            "resultCode1": {
              "chargeStatus": "A",
              "code": "3045",
              "description": "Appeal Dismissed",
              "dispositionCode": "F",
              "conviction": true
            },
            "mostSeriousFlag": false
          },
          {
            "eventId": 130245322,
            "offenderCharge": {
              "id": 1583789,
              "offence": {
                "offenceCode": "SX03013",
                "statuteCode": "SX03",
                "description": "Rape a girl under 13"
              },
              "offenceDate": "2004-05-01",
              "offenceEndDate": "2004-10-17",
              "chargeStatus": {
                "code": "A",
                "description": "Active"
              },
              "resultCode1": {
                "chargeStatus": "A",
                "code": "1501",
                "description": "Recall to Prison",
                "dispositionCode": "F",
                "conviction": true
              },
              "mostSeriousFlag": false,
              "lidsOffenceNumber": 32
            },
            "offenceDate": "2004-05-01",
            "offenceEndDate": "2004-10-17",
            "resultCode1": {
              "chargeStatus": "A",
              "code": "3045",
              "description": "Appeal Dismissed",
              "dispositionCode": "F",
              "conviction": true
            },
            "mostSeriousFlag": false
          },
          {
            "eventId": 130245322,
            "offenderCharge": {
              "id": 1583790,
              "offence": {
                "offenceCode": "SX03013",
                "statuteCode": "SX03",
                "description": "Rape a girl under 13"
              },
              "offenceDate": "2004-10-17",
              "offenceEndDate": "2005-10-17",
              "chargeStatus": {
                "code": "A",
                "description": "Active"
              },
              "resultCode1": {
                "chargeStatus": "A",
                "code": "1501",
                "description": "Recall to Prison",
                "dispositionCode": "F",
                "conviction": true
              },
              "mostSeriousFlag": false,
              "lidsOffenceNumber": 33
            },
            "offenceDate": "2004-10-17",
            "offenceEndDate": "2005-10-17",
            "resultCode1": {
              "chargeStatus": "A",
              "code": "3045",
              "description": "Appeal Dismissed",
              "dispositionCode": "F",
              "conviction": true
            },
            "mostSeriousFlag": false
          },
          {
            "eventId": 130245322,
            "offenderCharge": {
              "id": 1583791,
              "offence": {
                "offenceCode": "SX03013",
                "statuteCode": "SX03",
                "description": "Rape a girl under 13"
              },
              "offenceDate": "2005-10-17",
              "offenceEndDate": "2006-10-17",
              "chargeStatus": {
                "code": "A",
                "description": "Active"
              },
              "resultCode1": {
                "chargeStatus": "A",
                "code": "1501",
                "description": "Recall to Prison",
                "dispositionCode": "F",
                "conviction": true
              },
              "mostSeriousFlag": false,
              "lidsOffenceNumber": 34
            },
            "offenceDate": "2005-10-17",
            "offenceEndDate": "2006-10-17",
            "resultCode1": {
              "chargeStatus": "A",
              "code": "3045",
              "description": "Appeal Dismissed",
              "dispositionCode": "F",
              "conviction": true
            },
            "mostSeriousFlag": false
          },
          {
            "eventId": 130245322,
            "offenderCharge": {
              "id": 1583792,
              "offence": {
                "offenceCode": "SX56025-029N",
                "statuteCode": "ZZ",
                "description": "INDECENT ASSAULT ON A CHILD"
              },
              "offenceDate": "2000-10-17",
              "offenceEndDate": "2001-10-17",
              "chargeStatus": {
                "code": "A",
                "description": "Active"
              },
              "resultCode1": {
                "chargeStatus": "A",
                "code": "1501",
                "description": "Recall to Prison",
                "dispositionCode": "F",
                "conviction": true
              },
              "mostSeriousFlag": false,
              "lidsOffenceNumber": 35
            },
            "offenceDate": "2000-10-17",
            "offenceEndDate": "2001-10-17",
            "resultCode1": {
              "chargeStatus": "A",
              "code": "3045",
              "description": "Appeal Dismissed",
              "dispositionCode": "F",
              "conviction": true
            },
            "mostSeriousFlag": false
          },
          {
            "eventId": 130245322,
            "offenderCharge": {
              "id": 1583793,
              "offence": {
                "offenceCode": "SX56025-029N",
                "statuteCode": "ZZ",
                "description": "INDECENT ASSAULT ON A CHILD"
              },
              "offenceDate": "2001-10-17",
              "offenceEndDate": "2002-10-17",
              "chargeStatus": {
                "code": "A",
                "description": "Active"
              },
              "resultCode1": {
                "chargeStatus": "A",
                "code": "1501",
                "description": "Recall to Prison",
                "dispositionCode": "F",
                "conviction": true
              },
              "mostSeriousFlag": false,
              "lidsOffenceNumber": 36
            },
            "offenceDate": "2001-10-17",
            "offenceEndDate": "2002-10-17",
            "resultCode1": {
              "chargeStatus": "A",
              "code": "3045",
              "description": "Appeal Dismissed",
              "dispositionCode": "F",
              "conviction": true
            },
            "mostSeriousFlag": false
          },
          {
            "eventId": 130245322,
            "offenderCharge": {
              "id": 1583794,
              "offence": {
                "offenceCode": "SX56025-029N",
                "statuteCode": "ZZ",
                "description": "INDECENT ASSAULT ON A CHILD"
              },
              "offenceDate": "2002-10-17",
              "offenceEndDate": "2003-10-17",
              "chargeStatus": {
                "code": "A",
                "description": "Active"
              },
              "resultCode1": {
                "chargeStatus": "A",
                "code": "1501",
                "description": "Recall to Prison",
                "dispositionCode": "F",
                "conviction": true
              },
              "mostSeriousFlag": false,
              "lidsOffenceNumber": 37
            },
            "offenceDate": "2002-10-17",
            "offenceEndDate": "2003-10-17",
            "resultCode1": {
              "chargeStatus": "A",
              "code": "3045",
              "description": "Appeal Dismissed",
              "dispositionCode": "F",
              "conviction": true
            },
            "mostSeriousFlag": false
          },
          {
            "eventId": 130245322,
            "offenderCharge": {
              "id": 1583795,
              "offence": {
                "offenceCode": "SX56025-029N",
                "statuteCode": "ZZ",
                "description": "INDECENT ASSAULT ON A CHILD"
              },
              "offenceDate": "2003-10-17",
              "offenceEndDate": "2004-04-30",
              "chargeStatus": {
                "code": "A",
                "description": "Active"
              },
              "resultCode1": {
                "chargeStatus": "A",
                "code": "1501",
                "description": "Recall to Prison",
                "dispositionCode": "F",
                "conviction": true
              },
              "mostSeriousFlag": false,
              "lidsOffenceNumber": 38
            },
            "offenceDate": "2003-10-17",
            "offenceEndDate": "2004-04-30",
            "resultCode1": {
              "chargeStatus": "A",
              "code": "3045",
              "description": "Appeal Dismissed",
              "dispositionCode": "F",
              "conviction": true
            },
            "mostSeriousFlag": false
          },
          {
            "eventId": 130245322,
            "offenderCharge": {
              "id": 1583796,
              "offence": {
                "offenceCode": "SX03015",
                "statuteCode": "SX03",
                "description": "Assault a girl under 13 by penetration with a part of your body / a thing - SOA 2003"
              },
              "offenceDate": "2004-05-01",
              "offenceEndDate": "2004-10-17",
              "chargeStatus": {
                "code": "A",
                "description": "Active"
              },
              "resultCode1": {
                "chargeStatus": "A",
                "code": "1501",
                "description": "Recall to Prison",
                "dispositionCode": "F",
                "conviction": true
              },
              "mostSeriousFlag": false,
              "lidsOffenceNumber": 39
            },
            "offenceDate": "2004-05-01",
            "offenceEndDate": "2004-10-17",
            "resultCode1": {
              "chargeStatus": "A",
              "code": "3045",
              "description": "Appeal Dismissed",
              "dispositionCode": "F",
              "conviction": true
            },
            "mostSeriousFlag": false
          },
          {
            "eventId": 130245322,
            "offenderCharge": {
              "id": 1583797,
              "offence": {
                "offenceCode": "SX03015",
                "statuteCode": "SX03",
                "description": "Assault a girl under 13 by penetration with a part of your body / a thing - SOA 2003"
              },
              "offenceDate": "2004-10-17",
              "offenceEndDate": "2005-10-17",
              "chargeStatus": {
                "code": "A",
                "description": "Active"
              },
              "resultCode1": {
                "chargeStatus": "A",
                "code": "1501",
                "description": "Recall to Prison",
                "dispositionCode": "F",
                "conviction": true
              },
              "mostSeriousFlag": false,
              "lidsOffenceNumber": 40
            },
            "offenceDate": "2004-10-17",
            "offenceEndDate": "2005-10-17",
            "resultCode1": {
              "chargeStatus": "A",
              "code": "3045",
              "description": "Appeal Dismissed",
              "dispositionCode": "F",
              "conviction": true
            },
            "mostSeriousFlag": false
          },
          {
            "eventId": 130245322,
            "offenderCharge": {
              "id": 1583798,
              "offence": {
                "offenceCode": "SX03015",
                "statuteCode": "SX03",
                "description": "Assault a girl under 13 by penetration with a part of your body / a thing - SOA 2003"
              },
              "offenceDate": "2005-10-17",
              "offenceEndDate": "2006-10-17",
              "chargeStatus": {
                "code": "A",
                "description": "Active"
              },
              "resultCode1": {
                "chargeStatus": "A",
                "code": "1501",
                "description": "Recall to Prison",
                "dispositionCode": "F",
                "conviction": true
              },
              "mostSeriousFlag": false,
              "lidsOffenceNumber": 41
            },
            "offenceDate": "2005-10-17",
            "offenceEndDate": "2006-10-17",
            "resultCode1": {
              "chargeStatus": "A",
              "code": "3045",
              "description": "Appeal Dismissed",
              "dispositionCode": "F",
              "conviction": true
            },
            "mostSeriousFlag": false
          },
          {
            "eventId": 130245322,
            "offenderCharge": {
              "id": 1583799,
              "offence": {
                "offenceCode": "PK78001",
                "statuteCode": "PK78",
                "description": "Take an indecent photograph / pseudo-photograph of a child"
              },
              "offenceDate": "2010-03-28",
              "chargeStatus": {
                "code": "A",
                "description": "Active"
              },
              "resultCode1": {
                "chargeStatus": "A",
                "code": "1501",
                "description": "Recall to Prison",
                "dispositionCode": "F",
                "conviction": true
              },
              "mostSeriousFlag": false,
              "lidsOffenceNumber": 42
            },
            "offenceDate": "2010-03-28",
            "resultCode1": {
              "chargeStatus": "A",
              "code": "3045",
              "description": "Appeal Dismissed",
              "dispositionCode": "F",
              "conviction": true
            },
            "mostSeriousFlag": false
          },
          {
            "eventId": 130245322,
            "offenderCharge": {
              "id": 1583800,
              "offence": {
                "offenceCode": "PK78001",
                "statuteCode": "PK78",
                "description": "Take an indecent photograph / pseudo-photograph of a child"
              },
              "offenceDate": "2010-03-27",
              "chargeStatus": {
                "code": "A",
                "description": "Active"
              },
              "resultCode1": {
                "chargeStatus": "A",
                "code": "1501",
                "description": "Recall to Prison",
                "dispositionCode": "F",
                "conviction": true
              },
              "mostSeriousFlag": false,
              "lidsOffenceNumber": 43
            },
            "offenceDate": "2010-03-27",
            "resultCode1": {
              "chargeStatus": "A",
              "code": "3045",
              "description": "Appeal Dismissed",
              "dispositionCode": "F",
              "conviction": true
            },
            "mostSeriousFlag": false
          },
          {
            "eventId": 130245322,
            "offenderCharge": {
              "id": 1583801,
              "offence": {
                "offenceCode": "PK78001",
                "statuteCode": "PK78",
                "description": "Take an indecent photograph / pseudo-photograph of a child"
              },
              "offenceDate": "2010-10-31",
              "chargeStatus": {
                "code": "A",
                "description": "Active"
              },
              "resultCode1": {
                "chargeStatus": "A",
                "code": "1501",
                "description": "Recall to Prison",
                "dispositionCode": "F",
                "conviction": true
              },
              "mostSeriousFlag": false,
              "lidsOffenceNumber": 44
            },
            "offenceDate": "2010-10-31",
            "resultCode1": {
              "chargeStatus": "A",
              "code": "3045",
              "description": "Appeal Dismissed",
              "dispositionCode": "F",
              "conviction": true
            },
            "mostSeriousFlag": false
          },
          {
            "eventId": 130245322,
            "offenderCharge": {
              "id": 1583802,
              "offence": {
                "offenceCode": "PK78001",
                "statuteCode": "PK78",
                "description": "Take an indecent photograph / pseudo-photograph of a child"
              },
              "offenceDate": "2010-10-31",
              "chargeStatus": {
                "code": "A",
                "description": "Active"
              },
              "resultCode1": {
                "chargeStatus": "A",
                "code": "1501",
                "description": "Recall to Prison",
                "dispositionCode": "F",
                "conviction": true
              },
              "mostSeriousFlag": false,
              "lidsOffenceNumber": 45
            },
            "offenceDate": "2010-10-31",
            "resultCode1": {
              "chargeStatus": "A",
              "code": "3045",
              "description": "Appeal Dismissed",
              "dispositionCode": "F",
              "conviction": true
            },
            "mostSeriousFlag": false
          },
          {
            "eventId": 130245322,
            "offenderCharge": {
              "id": 1583803,
              "offence": {
                "offenceCode": "PK78001",
                "statuteCode": "PK78",
                "description": "Take an indecent photograph / pseudo-photograph of a child"
              },
              "offenceDate": "2010-10-31",
              "chargeStatus": {
                "code": "A",
                "description": "Active"
              },
              "resultCode1": {
                "chargeStatus": "A",
                "code": "1501",
                "description": "Recall to Prison",
                "dispositionCode": "F",
                "conviction": true
              },
              "mostSeriousFlag": false,
              "lidsOffenceNumber": 46
            },
            "offenceDate": "2010-10-31",
            "resultCode1": {
              "chargeStatus": "A",
              "code": "3045",
              "description": "Appeal Dismissed",
              "dispositionCode": "F",
              "conviction": true
            },
            "mostSeriousFlag": false
          },
          {
            "eventId": 130245322,
            "offenderCharge": {
              "id": 1583804,
              "offence": {
                "offenceCode": "PK78001",
                "statuteCode": "PK78",
                "description": "Take an indecent photograph / pseudo-photograph of a child"
              },
              "offenceDate": "2010-10-31",
              "chargeStatus": {
                "code": "A",
                "description": "Active"
              },
              "resultCode1": {
                "chargeStatus": "A",
                "code": "1501",
                "description": "Recall to Prison",
                "dispositionCode": "F",
                "conviction": true
              },
              "mostSeriousFlag": false,
              "lidsOffenceNumber": 47
            },
            "offenceDate": "2010-10-31",
            "resultCode1": {
              "chargeStatus": "A",
              "code": "3045",
              "description": "Appeal Dismissed",
              "dispositionCode": "F",
              "conviction": true
            },
            "mostSeriousFlag": false
          },
          {
            "eventId": 130245322,
            "offenderCharge": {
              "id": 1583805,
              "offence": {
                "offenceCode": "PK78001",
                "statuteCode": "PK78",
                "description": "Take an indecent photograph / pseudo-photograph of a child"
              },
              "offenceDate": "2011-01-18",
              "chargeStatus": {
                "code": "A",
                "description": "Active"
              },
              "resultCode1": {
                "chargeStatus": "A",
                "code": "1501",
                "description": "Recall to Prison",
                "dispositionCode": "F",
                "conviction": true
              },
              "mostSeriousFlag": false,
              "lidsOffenceNumber": 48
            },
            "offenceDate": "2011-01-18",
            "resultCode1": {
              "chargeStatus": "A",
              "code": "3045",
              "description": "Appeal Dismissed",
              "dispositionCode": "F",
              "conviction": true
            },
            "mostSeriousFlag": false
          },
          {
            "eventId": 130245322,
            "offenderCharge": {
              "id": 1583806,
              "offence": {
                "offenceCode": "PK78001",
                "statuteCode": "PK78",
                "description": "Take an indecent photograph / pseudo-photograph of a child"
              },
              "offenceDate": "2011-05-01",
              "chargeStatus": {
                "code": "A",
                "description": "Active"
              },
              "resultCode1": {
                "chargeStatus": "A",
                "code": "1501",
                "description": "Recall to Prison",
                "dispositionCode": "F",
                "conviction": true
              },
              "mostSeriousFlag": false,
              "lidsOffenceNumber": 49
            },
            "offenceDate": "2011-05-01",
            "resultCode1": {
              "chargeStatus": "A",
              "code": "3045",
              "description": "Appeal Dismissed",
              "dispositionCode": "F",
              "conviction": true
            },
            "mostSeriousFlag": false
          },
          {
            "eventId": 130245322,
            "offenderCharge": {
              "id": 1583807,
              "offence": {
                "offenceCode": "PK78001",
                "statuteCode": "PK78",
                "description": "Take an indecent photograph / pseudo-photograph of a child"
              },
              "offenceDate": "2010-10-31",
              "chargeStatus": {
                "code": "A",
                "description": "Active"
              },
              "resultCode1": {
                "chargeStatus": "A",
                "code": "1501",
                "description": "Recall to Prison",
                "dispositionCode": "F",
                "conviction": true
              },
              "mostSeriousFlag": false,
              "lidsOffenceNumber": 50
            },
            "offenceDate": "2010-10-31",
            "resultCode1": {
              "chargeStatus": "A",
              "code": "3045",
              "description": "Appeal Dismissed",
              "dispositionCode": "F",
              "conviction": true
            },
            "mostSeriousFlag": false
          },
          {
            "eventId": 130245322,
            "offenderCharge": {
              "id": 1583808,
              "offence": {
                "offenceCode": "PK78001",
                "statuteCode": "PK78",
                "description": "Take an indecent photograph / pseudo-photograph of a child"
              },
              "offenceDate": "2011-03-27",
              "chargeStatus": {
                "code": "A",
                "description": "Active"
              },
              "resultCode1": {
                "chargeStatus": "A",
                "code": "1501",
                "description": "Recall to Prison",
                "dispositionCode": "F",
                "conviction": true
              },
              "mostSeriousFlag": false,
              "lidsOffenceNumber": 51
            },
            "offenceDate": "2011-03-27",
            "resultCode1": {
              "chargeStatus": "A",
              "code": "3045",
              "description": "Appeal Dismissed",
              "dispositionCode": "F",
              "conviction": true
            },
            "mostSeriousFlag": false
          },
          {
            "eventId": 130245322,
            "offenderCharge": {
              "id": 1583809,
              "offence": {
                "offenceCode": "PK78001",
                "statuteCode": "PK78",
                "description": "Take an indecent photograph / pseudo-photograph of a child"
              },
              "offenceDate": "2011-05-23",
              "chargeStatus": {
                "code": "A",
                "description": "Active"
              },
              "resultCode1": {
                "chargeStatus": "A",
                "code": "1501",
                "description": "Recall to Prison",
                "dispositionCode": "F",
                "conviction": true
              },
              "mostSeriousFlag": false,
              "lidsOffenceNumber": 52
            },
            "offenceDate": "2011-05-23",
            "resultCode1": {
              "chargeStatus": "A",
              "code": "3045",
              "description": "Appeal Dismissed",
              "dispositionCode": "F",
              "conviction": true
            },
            "mostSeriousFlag": false
          },
          {
            "eventId": 130245322,
            "offenderCharge": {
              "id": 1583810,
              "offence": {
                "offenceCode": "PK78001",
                "statuteCode": "PK78",
                "description": "Take an indecent photograph / pseudo-photograph of a child"
              },
              "offenceDate": "2010-05-28",
              "chargeStatus": {
                "code": "A",
                "description": "Active"
              },
              "resultCode1": {
                "chargeStatus": "A",
                "code": "1501",
                "description": "Recall to Prison",
                "dispositionCode": "F",
                "conviction": true
              },
              "mostSeriousFlag": false,
              "lidsOffenceNumber": 53
            },
            "offenceDate": "2010-05-28",
            "resultCode1": {
              "chargeStatus": "A",
              "code": "3045",
              "description": "Appeal Dismissed",
              "dispositionCode": "F",
              "conviction": true
            },
            "mostSeriousFlag": false
          },
          {
            "eventId": 130245322,
            "offenderCharge": {
              "id": 1583811,
              "offence": {
                "offenceCode": "PK78001",
                "statuteCode": "PK78",
                "description": "Take an indecent photograph / pseudo-photograph of a child"
              },
              "offenceDate": "2006-01-01",
              "offenceEndDate": "2011-06-30",
              "chargeStatus": {
                "code": "A",
                "description": "Active"
              },
              "resultCode1": {
                "chargeStatus": "A",
                "code": "1501",
                "description": "Recall to Prison",
                "dispositionCode": "F",
                "conviction": true
              },
              "mostSeriousFlag": false,
              "lidsOffenceNumber": 54
            },
            "offenceDate": "2006-01-01",
            "offenceEndDate": "2011-06-30",
            "resultCode1": {
              "chargeStatus": "A",
              "code": "3045",
              "description": "Appeal Dismissed",
              "dispositionCode": "F",
              "conviction": true
            },
            "mostSeriousFlag": false
          }
        ],
        "courtOrders": [
          {
            "id": 635195,
            "eventId": 130245322,
            "courtDate": "2012-08-02",
            "issuingCourt": "COACD",
            "orderType": "AUTO",
            "orderStatus": "A",
            "nonReportFlag": false,
            "sentencePurposes": []
          }
        ]
      },
      {
        "id": 110006913,
        "caseId": 660258,
        "offenderNo": "A7163CK",
        "eventDateTime": "2012-03-23T10:03:00",
        "courtEventType": {
          "code": "CA",
          "description": "Court Appearance"
        },
        "eventStatus": {
          "code": "COMP",
          "description": "Completed"
        },
        "directionCode": {
          "code": "OUT",
          "description": "Out"
        },
        "courtId": "PBORCC",
        "outcomeReasonCode": {
          "chargeStatus": "A",
          "code": "1002",
          "description": "Imprisonment",
          "dispositionCode": "F",
          "conviction": true
        },
        "orderRequestedFlag": false,
        "holdFlag": false,
        "nextEventRequestFlag": false,
        "createdDateTime": "2012-03-22T16:26:46.240265",
        "createdByUsername": "UQJ83O",
        "courtEventCharges": [
          {
            "eventId": 110006913,
            "offenderCharge": {
              "id": 1583786,
              "offence": {
                "offenceCode": "SX03002",
                "statuteCode": "SX03",
                "description": "Rape a woman 16 years of age or over - SOA 2003"
              },
              "offenceDate": "2001-10-17",
              "offenceEndDate": "2002-10-17",
              "chargeStatus": {
                "code": "A",
                "description": "Active"
              },
              "resultCode1": {
                "chargeStatus": "A",
                "code": "1501",
                "description": "Recall to Prison",
                "dispositionCode": "F",
                "conviction": true
              },
              "mostSeriousFlag": false,
              "lidsOffenceNumber": 29
            },
            "offenceDate": "2001-10-17",
            "offenceEndDate": "2002-10-17",
            "resultCode1": {
              "chargeStatus": "A",
              "code": "1002",
              "description": "Imprisonment",
              "dispositionCode": "F",
              "conviction": true
            },
            "mostSeriousFlag": false
          },
          {
            "eventId": 110006913,
            "offenderCharge": {
              "id": 1583787,
              "offence": {
                "offenceCode": "SX03002",
                "statuteCode": "SX03",
                "description": "Rape a woman 16 years of age or over - SOA 2003"
              },
              "offenceDate": "2002-10-17",
              "offenceEndDate": "2003-10-17",
              "chargeStatus": {
                "code": "A",
                "description": "Active"
              },
              "resultCode1": {
                "chargeStatus": "A",
                "code": "1501",
                "description": "Recall to Prison",
                "dispositionCode": "F",
                "conviction": true
              },
              "mostSeriousFlag": false,
              "lidsOffenceNumber": 30
            },
            "offenceDate": "2002-10-17",
            "offenceEndDate": "2003-10-17",
            "resultCode1": {
              "chargeStatus": "A",
              "code": "1002",
              "description": "Imprisonment",
              "dispositionCode": "F",
              "conviction": true
            },
            "mostSeriousFlag": false
          },
          {
            "eventId": 110006913,
            "offenderCharge": {
              "id": 1583788,
              "offence": {
                "offenceCode": "SX03002",
                "statuteCode": "SX03",
                "description": "Rape a woman 16 years of age or over - SOA 2003"
              },
              "offenceDate": "2003-10-17",
              "offenceEndDate": "2004-04-30",
              "chargeStatus": {
                "code": "A",
                "description": "Active"
              },
              "resultCode1": {
                "chargeStatus": "A",
                "code": "1501",
                "description": "Recall to Prison",
                "dispositionCode": "F",
                "conviction": true
              },
              "mostSeriousFlag": false,
              "lidsOffenceNumber": 31
            },
            "offenceDate": "2003-10-17",
            "offenceEndDate": "2004-04-30",
            "resultCode1": {
              "chargeStatus": "A",
              "code": "1002",
              "description": "Imprisonment",
              "dispositionCode": "F",
              "conviction": true
            },
            "mostSeriousFlag": false
          },
          {
            "eventId": 110006913,
            "offenderCharge": {
              "id": 1583789,
              "offence": {
                "offenceCode": "SX03013",
                "statuteCode": "SX03",
                "description": "Rape a girl under 13"
              },
              "offenceDate": "2004-05-01",
              "offenceEndDate": "2004-10-17",
              "chargeStatus": {
                "code": "A",
                "description": "Active"
              },
              "resultCode1": {
                "chargeStatus": "A",
                "code": "1501",
                "description": "Recall to Prison",
                "dispositionCode": "F",
                "conviction": true
              },
              "mostSeriousFlag": false,
              "lidsOffenceNumber": 32
            },
            "offenceDate": "2004-05-01",
            "offenceEndDate": "2004-10-17",
            "resultCode1": {
              "chargeStatus": "A",
              "code": "1002",
              "description": "Imprisonment",
              "dispositionCode": "F",
              "conviction": true
            },
            "mostSeriousFlag": false
          },
          {
            "eventId": 110006913,
            "offenderCharge": {
              "id": 1583790,
              "offence": {
                "offenceCode": "SX03013",
                "statuteCode": "SX03",
                "description": "Rape a girl under 13"
              },
              "offenceDate": "2004-10-17",
              "offenceEndDate": "2005-10-17",
              "chargeStatus": {
                "code": "A",
                "description": "Active"
              },
              "resultCode1": {
                "chargeStatus": "A",
                "code": "1501",
                "description": "Recall to Prison",
                "dispositionCode": "F",
                "conviction": true
              },
              "mostSeriousFlag": false,
              "lidsOffenceNumber": 33
            },
            "offenceDate": "2004-10-17",
            "offenceEndDate": "2005-10-17",
            "resultCode1": {
              "chargeStatus": "A",
              "code": "1002",
              "description": "Imprisonment",
              "dispositionCode": "F",
              "conviction": true
            },
            "mostSeriousFlag": false
          },
          {
            "eventId": 110006913,
            "offenderCharge": {
              "id": 1583791,
              "offence": {
                "offenceCode": "SX03013",
                "statuteCode": "SX03",
                "description": "Rape a girl under 13"
              },
              "offenceDate": "2005-10-17",
              "offenceEndDate": "2006-10-17",
              "chargeStatus": {
                "code": "A",
                "description": "Active"
              },
              "resultCode1": {
                "chargeStatus": "A",
                "code": "1501",
                "description": "Recall to Prison",
                "dispositionCode": "F",
                "conviction": true
              },
              "mostSeriousFlag": false,
              "lidsOffenceNumber": 34
            },
            "offenceDate": "2005-10-17",
            "offenceEndDate": "2006-10-17",
            "resultCode1": {
              "chargeStatus": "A",
              "code": "1002",
              "description": "Imprisonment",
              "dispositionCode": "F",
              "conviction": true
            },
            "mostSeriousFlag": false
          },
          {
            "eventId": 110006913,
            "offenderCharge": {
              "id": 1583792,
              "offence": {
                "offenceCode": "SX56025-029N",
                "statuteCode": "ZZ",
                "description": "INDECENT ASSAULT ON A CHILD"
              },
              "offenceDate": "2000-10-17",
              "offenceEndDate": "2001-10-17",
              "chargeStatus": {
                "code": "A",
                "description": "Active"
              },
              "resultCode1": {
                "chargeStatus": "A",
                "code": "1501",
                "description": "Recall to Prison",
                "dispositionCode": "F",
                "conviction": true
              },
              "mostSeriousFlag": false,
              "lidsOffenceNumber": 35
            },
            "offenceDate": "2000-10-17",
            "offenceEndDate": "2001-10-17",
            "resultCode1": {
              "chargeStatus": "A",
              "code": "1002",
              "description": "Imprisonment",
              "dispositionCode": "F",
              "conviction": true
            },
            "mostSeriousFlag": false
          },
          {
            "eventId": 110006913,
            "offenderCharge": {
              "id": 1583793,
              "offence": {
                "offenceCode": "SX56025-029N",
                "statuteCode": "ZZ",
                "description": "INDECENT ASSAULT ON A CHILD"
              },
              "offenceDate": "2001-10-17",
              "offenceEndDate": "2002-10-17",
              "chargeStatus": {
                "code": "A",
                "description": "Active"
              },
              "resultCode1": {
                "chargeStatus": "A",
                "code": "1501",
                "description": "Recall to Prison",
                "dispositionCode": "F",
                "conviction": true
              },
              "mostSeriousFlag": false,
              "lidsOffenceNumber": 36
            },
            "offenceDate": "2001-10-17",
            "offenceEndDate": "2002-10-17",
            "resultCode1": {
              "chargeStatus": "A",
              "code": "1002",
              "description": "Imprisonment",
              "dispositionCode": "F",
              "conviction": true
            },
            "mostSeriousFlag": false
          },
          {
            "eventId": 110006913,
            "offenderCharge": {
              "id": 1583794,
              "offence": {
                "offenceCode": "SX56025-029N",
                "statuteCode": "ZZ",
                "description": "INDECENT ASSAULT ON A CHILD"
              },
              "offenceDate": "2002-10-17",
              "offenceEndDate": "2003-10-17",
              "chargeStatus": {
                "code": "A",
                "description": "Active"
              },
              "resultCode1": {
                "chargeStatus": "A",
                "code": "1501",
                "description": "Recall to Prison",
                "dispositionCode": "F",
                "conviction": true
              },
              "mostSeriousFlag": false,
              "lidsOffenceNumber": 37
            },
            "offenceDate": "2002-10-17",
            "offenceEndDate": "2003-10-17",
            "resultCode1": {
              "chargeStatus": "A",
              "code": "1002",
              "description": "Imprisonment",
              "dispositionCode": "F",
              "conviction": true
            },
            "mostSeriousFlag": false
          },
          {
            "eventId": 110006913,
            "offenderCharge": {
              "id": 1583795,
              "offence": {
                "offenceCode": "SX56025-029N",
                "statuteCode": "ZZ",
                "description": "INDECENT ASSAULT ON A CHILD"
              },
              "offenceDate": "2003-10-17",
              "offenceEndDate": "2004-04-30",
              "chargeStatus": {
                "code": "A",
                "description": "Active"
              },
              "resultCode1": {
                "chargeStatus": "A",
                "code": "1501",
                "description": "Recall to Prison",
                "dispositionCode": "F",
                "conviction": true
              },
              "mostSeriousFlag": false,
              "lidsOffenceNumber": 38
            },
            "offenceDate": "2003-10-17",
            "offenceEndDate": "2004-04-30",
            "resultCode1": {
              "chargeStatus": "A",
              "code": "1002",
              "description": "Imprisonment",
              "dispositionCode": "F",
              "conviction": true
            },
            "mostSeriousFlag": false
          },
          {
            "eventId": 110006913,
            "offenderCharge": {
              "id": 1583796,
              "offence": {
                "offenceCode": "SX03015",
                "statuteCode": "SX03",
                "description": "Assault a girl under 13 by penetration with a part of your body / a thing - SOA 2003"
              },
              "offenceDate": "2004-05-01",
              "offenceEndDate": "2004-10-17",
              "chargeStatus": {
                "code": "A",
                "description": "Active"
              },
              "resultCode1": {
                "chargeStatus": "A",
                "code": "1501",
                "description": "Recall to Prison",
                "dispositionCode": "F",
                "conviction": true
              },
              "mostSeriousFlag": false,
              "lidsOffenceNumber": 39
            },
            "offenceDate": "2004-05-01",
            "offenceEndDate": "2004-10-17",
            "resultCode1": {
              "chargeStatus": "A",
              "code": "1002",
              "description": "Imprisonment",
              "dispositionCode": "F",
              "conviction": true
            },
            "mostSeriousFlag": false
          },
          {
            "eventId": 110006913,
            "offenderCharge": {
              "id": 1583797,
              "offence": {
                "offenceCode": "SX03015",
                "statuteCode": "SX03",
                "description": "Assault a girl under 13 by penetration with a part of your body / a thing - SOA 2003"
              },
              "offenceDate": "2004-10-17",
              "offenceEndDate": "2005-10-17",
              "chargeStatus": {
                "code": "A",
                "description": "Active"
              },
              "resultCode1": {
                "chargeStatus": "A",
                "code": "1501",
                "description": "Recall to Prison",
                "dispositionCode": "F",
                "conviction": true
              },
              "mostSeriousFlag": false,
              "lidsOffenceNumber": 40
            },
            "offenceDate": "2004-10-17",
            "offenceEndDate": "2005-10-17",
            "resultCode1": {
              "chargeStatus": "A",
              "code": "1002",
              "description": "Imprisonment",
              "dispositionCode": "F",
              "conviction": true
            },
            "mostSeriousFlag": false
          },
          {
            "eventId": 110006913,
            "offenderCharge": {
              "id": 1583798,
              "offence": {
                "offenceCode": "SX03015",
                "statuteCode": "SX03",
                "description": "Assault a girl under 13 by penetration with a part of your body / a thing - SOA 2003"
              },
              "offenceDate": "2005-10-17",
              "offenceEndDate": "2006-10-17",
              "chargeStatus": {
                "code": "A",
                "description": "Active"
              },
              "resultCode1": {
                "chargeStatus": "A",
                "code": "1501",
                "description": "Recall to Prison",
                "dispositionCode": "F",
                "conviction": true
              },
              "mostSeriousFlag": false,
              "lidsOffenceNumber": 41
            },
            "offenceDate": "2005-10-17",
            "offenceEndDate": "2006-10-17",
            "resultCode1": {
              "chargeStatus": "A",
              "code": "1002",
              "description": "Imprisonment",
              "dispositionCode": "F",
              "conviction": true
            },
            "mostSeriousFlag": false
          },
          {
            "eventId": 110006913,
            "offenderCharge": {
              "id": 1583799,
              "offence": {
                "offenceCode": "PK78001",
                "statuteCode": "PK78",
                "description": "Take an indecent photograph / pseudo-photograph of a child"
              },
              "offenceDate": "2010-03-28",
              "chargeStatus": {
                "code": "A",
                "description": "Active"
              },
              "resultCode1": {
                "chargeStatus": "A",
                "code": "1501",
                "description": "Recall to Prison",
                "dispositionCode": "F",
                "conviction": true
              },
              "mostSeriousFlag": false,
              "lidsOffenceNumber": 42
            },
            "offenceDate": "2010-03-28",
            "resultCode1": {
              "chargeStatus": "A",
              "code": "1002",
              "description": "Imprisonment",
              "dispositionCode": "F",
              "conviction": true
            },
            "mostSeriousFlag": false
          },
          {
            "eventId": 110006913,
            "offenderCharge": {
              "id": 1583800,
              "offence": {
                "offenceCode": "PK78001",
                "statuteCode": "PK78",
                "description": "Take an indecent photograph / pseudo-photograph of a child"
              },
              "offenceDate": "2010-03-27",
              "chargeStatus": {
                "code": "A",
                "description": "Active"
              },
              "resultCode1": {
                "chargeStatus": "A",
                "code": "1501",
                "description": "Recall to Prison",
                "dispositionCode": "F",
                "conviction": true
              },
              "mostSeriousFlag": false,
              "lidsOffenceNumber": 43
            },
            "offenceDate": "2010-03-27",
            "resultCode1": {
              "chargeStatus": "A",
              "code": "1002",
              "description": "Imprisonment",
              "dispositionCode": "F",
              "conviction": true
            },
            "mostSeriousFlag": false
          },
          {
            "eventId": 110006913,
            "offenderCharge": {
              "id": 1583801,
              "offence": {
                "offenceCode": "PK78001",
                "statuteCode": "PK78",
                "description": "Take an indecent photograph / pseudo-photograph of a child"
              },
              "offenceDate": "2010-10-31",
              "chargeStatus": {
                "code": "A",
                "description": "Active"
              },
              "resultCode1": {
                "chargeStatus": "A",
                "code": "1501",
                "description": "Recall to Prison",
                "dispositionCode": "F",
                "conviction": true
              },
              "mostSeriousFlag": false,
              "lidsOffenceNumber": 44
            },
            "offenceDate": "2010-10-31",
            "resultCode1": {
              "chargeStatus": "A",
              "code": "1002",
              "description": "Imprisonment",
              "dispositionCode": "F",
              "conviction": true
            },
            "mostSeriousFlag": false
          },
          {
            "eventId": 110006913,
            "offenderCharge": {
              "id": 1583802,
              "offence": {
                "offenceCode": "PK78001",
                "statuteCode": "PK78",
                "description": "Take an indecent photograph / pseudo-photograph of a child"
              },
              "offenceDate": "2010-10-31",
              "chargeStatus": {
                "code": "A",
                "description": "Active"
              },
              "resultCode1": {
                "chargeStatus": "A",
                "code": "1501",
                "description": "Recall to Prison",
                "dispositionCode": "F",
                "conviction": true
              },
              "mostSeriousFlag": false,
              "lidsOffenceNumber": 45
            },
            "offenceDate": "2010-10-31",
            "resultCode1": {
              "chargeStatus": "A",
              "code": "1002",
              "description": "Imprisonment",
              "dispositionCode": "F",
              "conviction": true
            },
            "mostSeriousFlag": false
          },
          {
            "eventId": 110006913,
            "offenderCharge": {
              "id": 1583803,
              "offence": {
                "offenceCode": "PK78001",
                "statuteCode": "PK78",
                "description": "Take an indecent photograph / pseudo-photograph of a child"
              },
              "offenceDate": "2010-10-31",
              "chargeStatus": {
                "code": "A",
                "description": "Active"
              },
              "resultCode1": {
                "chargeStatus": "A",
                "code": "1501",
                "description": "Recall to Prison",
                "dispositionCode": "F",
                "conviction": true
              },
              "mostSeriousFlag": false,
              "lidsOffenceNumber": 46
            },
            "offenceDate": "2010-10-31",
            "resultCode1": {
              "chargeStatus": "A",
              "code": "1002",
              "description": "Imprisonment",
              "dispositionCode": "F",
              "conviction": true
            },
            "mostSeriousFlag": false
          },
          {
            "eventId": 110006913,
            "offenderCharge": {
              "id": 1583804,
              "offence": {
                "offenceCode": "PK78001",
                "statuteCode": "PK78",
                "description": "Take an indecent photograph / pseudo-photograph of a child"
              },
              "offenceDate": "2010-10-31",
              "chargeStatus": {
                "code": "A",
                "description": "Active"
              },
              "resultCode1": {
                "chargeStatus": "A",
                "code": "1501",
                "description": "Recall to Prison",
                "dispositionCode": "F",
                "conviction": true
              },
              "mostSeriousFlag": false,
              "lidsOffenceNumber": 47
            },
            "offenceDate": "2010-10-31",
            "resultCode1": {
              "chargeStatus": "A",
              "code": "1002",
              "description": "Imprisonment",
              "dispositionCode": "F",
              "conviction": true
            },
            "mostSeriousFlag": false
          },
          {
            "eventId": 110006913,
            "offenderCharge": {
              "id": 1583805,
              "offence": {
                "offenceCode": "PK78001",
                "statuteCode": "PK78",
                "description": "Take an indecent photograph / pseudo-photograph of a child"
              },
              "offenceDate": "2011-01-18",
              "chargeStatus": {
                "code": "A",
                "description": "Active"
              },
              "resultCode1": {
                "chargeStatus": "A",
                "code": "1501",
                "description": "Recall to Prison",
                "dispositionCode": "F",
                "conviction": true
              },
              "mostSeriousFlag": false,
              "lidsOffenceNumber": 48
            },
            "offenceDate": "2011-01-18",
            "resultCode1": {
              "chargeStatus": "A",
              "code": "1002",
              "description": "Imprisonment",
              "dispositionCode": "F",
              "conviction": true
            },
            "mostSeriousFlag": false
          },
          {
            "eventId": 110006913,
            "offenderCharge": {
              "id": 1583806,
              "offence": {
                "offenceCode": "PK78001",
                "statuteCode": "PK78",
                "description": "Take an indecent photograph / pseudo-photograph of a child"
              },
              "offenceDate": "2011-05-01",
              "chargeStatus": {
                "code": "A",
                "description": "Active"
              },
              "resultCode1": {
                "chargeStatus": "A",
                "code": "1501",
                "description": "Recall to Prison",
                "dispositionCode": "F",
                "conviction": true
              },
              "mostSeriousFlag": false,
              "lidsOffenceNumber": 49
            },
            "offenceDate": "2011-05-01",
            "resultCode1": {
              "chargeStatus": "A",
              "code": "1002",
              "description": "Imprisonment",
              "dispositionCode": "F",
              "conviction": true
            },
            "mostSeriousFlag": false
          },
          {
            "eventId": 110006913,
            "offenderCharge": {
              "id": 1583807,
              "offence": {
                "offenceCode": "PK78001",
                "statuteCode": "PK78",
                "description": "Take an indecent photograph / pseudo-photograph of a child"
              },
              "offenceDate": "2010-10-31",
              "chargeStatus": {
                "code": "A",
                "description": "Active"
              },
              "resultCode1": {
                "chargeStatus": "A",
                "code": "1501",
                "description": "Recall to Prison",
                "dispositionCode": "F",
                "conviction": true
              },
              "mostSeriousFlag": false,
              "lidsOffenceNumber": 50
            },
            "offenceDate": "2010-10-31",
            "resultCode1": {
              "chargeStatus": "A",
              "code": "1002",
              "description": "Imprisonment",
              "dispositionCode": "F",
              "conviction": true
            },
            "mostSeriousFlag": false
          },
          {
            "eventId": 110006913,
            "offenderCharge": {
              "id": 1583808,
              "offence": {
                "offenceCode": "PK78001",
                "statuteCode": "PK78",
                "description": "Take an indecent photograph / pseudo-photograph of a child"
              },
              "offenceDate": "2011-03-27",
              "chargeStatus": {
                "code": "A",
                "description": "Active"
              },
              "resultCode1": {
                "chargeStatus": "A",
                "code": "1501",
                "description": "Recall to Prison",
                "dispositionCode": "F",
                "conviction": true
              },
              "mostSeriousFlag": false,
              "lidsOffenceNumber": 51
            },
            "offenceDate": "2011-03-27",
            "resultCode1": {
              "chargeStatus": "A",
              "code": "1002",
              "description": "Imprisonment",
              "dispositionCode": "F",
              "conviction": true
            },
            "mostSeriousFlag": false
          },
          {
            "eventId": 110006913,
            "offenderCharge": {
              "id": 1583809,
              "offence": {
                "offenceCode": "PK78001",
                "statuteCode": "PK78",
                "description": "Take an indecent photograph / pseudo-photograph of a child"
              },
              "offenceDate": "2011-05-23",
              "chargeStatus": {
                "code": "A",
                "description": "Active"
              },
              "resultCode1": {
                "chargeStatus": "A",
                "code": "1501",
                "description": "Recall to Prison",
                "dispositionCode": "F",
                "conviction": true
              },
              "mostSeriousFlag": false,
              "lidsOffenceNumber": 52
            },
            "offenceDate": "2011-05-23",
            "resultCode1": {
              "chargeStatus": "A",
              "code": "1002",
              "description": "Imprisonment",
              "dispositionCode": "F",
              "conviction": true
            },
            "mostSeriousFlag": false
          },
          {
            "eventId": 110006913,
            "offenderCharge": {
              "id": 1583810,
              "offence": {
                "offenceCode": "PK78001",
                "statuteCode": "PK78",
                "description": "Take an indecent photograph / pseudo-photograph of a child"
              },
              "offenceDate": "2010-05-28",
              "chargeStatus": {
                "code": "A",
                "description": "Active"
              },
              "resultCode1": {
                "chargeStatus": "A",
                "code": "1501",
                "description": "Recall to Prison",
                "dispositionCode": "F",
                "conviction": true
              },
              "mostSeriousFlag": false,
              "lidsOffenceNumber": 53
            },
            "offenceDate": "2010-05-28",
            "resultCode1": {
              "chargeStatus": "A",
              "code": "1002",
              "description": "Imprisonment",
              "dispositionCode": "F",
              "conviction": true
            },
            "mostSeriousFlag": false
          },
          {
            "eventId": 110006913,
            "offenderCharge": {
              "id": 1583811,
              "offence": {
                "offenceCode": "PK78001",
                "statuteCode": "PK78",
                "description": "Take an indecent photograph / pseudo-photograph of a child"
              },
              "offenceDate": "2006-01-01",
              "offenceEndDate": "2011-06-30",
              "chargeStatus": {
                "code": "A",
                "description": "Active"
              },
              "resultCode1": {
                "chargeStatus": "A",
                "code": "1501",
                "description": "Recall to Prison",
                "dispositionCode": "F",
                "conviction": true
              },
              "mostSeriousFlag": false,
              "lidsOffenceNumber": 54
            },
            "offenceDate": "2006-01-01",
            "offenceEndDate": "2011-06-30",
            "resultCode1": {
              "chargeStatus": "A",
              "code": "1002",
              "description": "Imprisonment",
              "dispositionCode": "F",
              "conviction": true
            },
            "mostSeriousFlag": false
          }
        ],
        "courtOrders": [
          {
            "id": 556349,
            "eventId": 110006913,
            "courtDate": "2012-03-23",
            "issuingCourt": "PBORCC",
            "orderType": "AUTO",
            "orderStatus": "A",
            "nonReportFlag": false,
            "sentencePurposes": []
          }
        ]
      },
      {
        "id": 103580606,
        "caseId": 660258,
        "offenderNo": "A7163CK",
        "eventDateTime": "2011-08-03T10:02:00",
        "courtEventType": {
          "code": "CA",
          "description": "Court Appearance"
        },
        "eventStatus": {
          "code": "SCH",
          "description": "Scheduled (Approved)"
        },
        "courtId": "PBORMC",
        "commentText": "COMMITTL. ",
        "holdFlag": false,
        "nextEventRequestFlag": false,
        "createdDateTime": "2012-02-05T05:04:26.479559",
        "createdByUsername": "XTAG",
        "courtEventCharges": [
          {
            "eventId": 103580606,
            "offenderCharge": {
              "id": 1453012,
              "offence": {
                "offenceCode": "PK78001-008NA",
                "statuteCode": "ZZ",
                "description": "INDECENT PHOTOGRAPHS OF CHILDREN"
              },
              "offenceDate": "2010-10-31",
              "chargeStatus": {
                "code": "A",
                "description": "Active"
              },
              "resultCode1": {
                "chargeStatus": "A",
                "code": "1501",
                "description": "Recall to Prison",
                "dispositionCode": "F",
                "conviction": true
              },
              "mostSeriousFlag": false,
              "lidsOffenceNumber": 23
            },
            "offenceDate": "2010-10-31",
            "resultCode1": {
              "chargeStatus": "A",
              "code": "4506",
              "description": "Adjournment",
              "dispositionCode": "I",
              "conviction": false
            },
            "mostSeriousFlag": false
          },
          {
            "eventId": 103580606,
            "offenderCharge": {
              "id": 1453013,
              "offence": {
                "offenceCode": "SX03013-014N",
                "statuteCode": "ZZ",
                "description": "RAPE OF A CHILD U13 (SOA 2003 s5)"
              },
              "offenceDate": "2006-10-17",
              "chargeStatus": {
                "code": "A",
                "description": "Active"
              },
              "resultCode1": {
                "chargeStatus": "A",
                "code": "1501",
                "description": "Recall to Prison",
                "dispositionCode": "F",
                "conviction": true
              },
              "mostSeriousFlag": false,
              "lidsOffenceNumber": 7
            },
            "offenceDate": "2006-10-17",
            "resultCode1": {
              "chargeStatus": "A",
              "code": "4506",
              "description": "Adjournment",
              "dispositionCode": "I",
              "conviction": false
            },
            "mostSeriousFlag": false
          },
          {
            "eventId": 103580606,
            "offenderCharge": {
              "id": 1453014,
              "offence": {
                "offenceCode": "SX56070-072N",
                "statuteCode": "ZZ",
                "description": "RAPE ON CHILD (SOA 1956 s69)"
              },
              "offenceDate": "2001-10-17",
              "chargeStatus": {
                "code": "A",
                "description": "Active"
              },
              "resultCode1": {
                "chargeStatus": "A",
                "code": "1501",
                "description": "Recall to Prison",
                "dispositionCode": "F",
                "conviction": true
              },
              "mostSeriousFlag": false,
              "lidsOffenceNumber": 1
            },
            "offenceDate": "2001-10-17",
            "resultCode1": {
              "chargeStatus": "A",
              "code": "4506",
              "description": "Adjournment",
              "dispositionCode": "I",
              "conviction": false
            },
            "mostSeriousFlag": false
          },
          {
            "eventId": 103580606,
            "offenderCharge": {
              "id": 1453015,
              "offence": {
                "offenceCode": "SX56070-072N",
                "statuteCode": "ZZ",
                "description": "RAPE ON CHILD (SOA 1956 s69)"
              },
              "offenceDate": "2004-04-30",
              "chargeStatus": {
                "code": "A",
                "description": "Active"
              },
              "resultCode1": {
                "chargeStatus": "A",
                "code": "1501",
                "description": "Recall to Prison",
                "dispositionCode": "F",
                "conviction": true
              },
              "mostSeriousFlag": false,
              "lidsOffenceNumber": 4
            },
            "offenceDate": "2004-04-30",
            "resultCode1": {
              "chargeStatus": "A",
              "code": "4506",
              "description": "Adjournment",
              "dispositionCode": "I",
              "conviction": false
            },
            "mostSeriousFlag": false
          },
          {
            "eventId": 103580606,
            "offenderCharge": {
              "id": 1453016,
              "offence": {
                "offenceCode": "PK78001-008NA",
                "statuteCode": "ZZ",
                "description": "INDECENT PHOTOGRAPHS OF CHILDREN"
              },
              "offenceDate": "2010-03-28",
              "chargeStatus": {
                "code": "A",
                "description": "Active"
              },
              "resultCode1": {
                "chargeStatus": "A",
                "code": "1501",
                "description": "Recall to Prison",
                "dispositionCode": "F",
                "conviction": true
              },
              "mostSeriousFlag": false,
              "lidsOffenceNumber": 15
            },
            "offenceDate": "2010-03-28",
            "resultCode1": {
              "chargeStatus": "A",
              "code": "4506",
              "description": "Adjournment",
              "dispositionCode": "I",
              "conviction": false
            },
            "mostSeriousFlag": false
          },
          {
            "eventId": 103580606,
            "offenderCharge": {
              "id": 1453017,
              "offence": {
                "offenceCode": "PK78001-008NA",
                "statuteCode": "ZZ",
                "description": "INDECENT PHOTOGRAPHS OF CHILDREN"
              },
              "offenceDate": "2011-06-30",
              "chargeStatus": {
                "code": "A",
                "description": "Active"
              },
              "resultCode1": {
                "chargeStatus": "A",
                "code": "1501",
                "description": "Recall to Prison",
                "dispositionCode": "F",
                "conviction": true
              },
              "mostSeriousFlag": false,
              "lidsOffenceNumber": 27
            },
            "offenceDate": "2011-06-30",
            "resultCode1": {
              "chargeStatus": "A",
              "code": "4506",
              "description": "Adjournment",
              "dispositionCode": "I",
              "conviction": false
            },
            "mostSeriousFlag": false
          },
          {
            "eventId": 103580606,
            "offenderCharge": {
              "id": 1453018,
              "offence": {
                "offenceCode": "SX03013-014N",
                "statuteCode": "ZZ",
                "description": "RAPE OF A CHILD U13 (SOA 2003 s5)"
              },
              "offenceDate": "2005-10-17",
              "chargeStatus": {
                "code": "A",
                "description": "Active"
              },
              "resultCode1": {
                "chargeStatus": "A",
                "code": "1501",
                "description": "Recall to Prison",
                "dispositionCode": "F",
                "conviction": true
              },
              "mostSeriousFlag": false,
              "lidsOffenceNumber": 6
            },
            "offenceDate": "2005-10-17",
            "resultCode1": {
              "chargeStatus": "A",
              "code": "4506",
              "description": "Adjournment",
              "dispositionCode": "I",
              "conviction": false
            },
            "mostSeriousFlag": false
          },
          {
            "eventId": 103580606,
            "offenderCharge": {
              "id": 1453019,
              "offence": {
                "offenceCode": "PK78001-008NA",
                "statuteCode": "ZZ",
                "description": "INDECENT PHOTOGRAPHS OF CHILDREN"
              },
              "offenceDate": "2011-03-27",
              "chargeStatus": {
                "code": "A",
                "description": "Active"
              },
              "resultCode1": {
                "chargeStatus": "A",
                "code": "1501",
                "description": "Recall to Prison",
                "dispositionCode": "F",
                "conviction": true
              },
              "mostSeriousFlag": false,
              "lidsOffenceNumber": 24
            },
            "offenceDate": "2011-03-27",
            "resultCode1": {
              "chargeStatus": "A",
              "code": "4506",
              "description": "Adjournment",
              "dispositionCode": "I",
              "conviction": false
            },
            "mostSeriousFlag": false
          },
          {
            "eventId": 103580606,
            "offenderCharge": {
              "id": 1453020,
              "offence": {
                "offenceCode": "SX56025-029N",
                "statuteCode": "ZZ",
                "description": "INDECENT ASSAULT ON A CHILD"
              },
              "offenceDate": "2003-10-17",
              "chargeStatus": {
                "code": "A",
                "description": "Active"
              },
              "resultCode1": {
                "chargeStatus": "A",
                "code": "1501",
                "description": "Recall to Prison",
                "dispositionCode": "F",
                "conviction": true
              },
              "mostSeriousFlag": false,
              "lidsOffenceNumber": 10
            },
            "offenceDate": "2003-10-17",
            "resultCode1": {
              "chargeStatus": "A",
              "code": "4506",
              "description": "Adjournment",
              "dispositionCode": "I",
              "conviction": false
            },
            "mostSeriousFlag": false
          },
          {
            "eventId": 103580606,
            "offenderCharge": {
              "id": 1453021,
              "offence": {
                "offenceCode": "PK78001-008NA",
                "statuteCode": "ZZ",
                "description": "INDECENT PHOTOGRAPHS OF CHILDREN"
              },
              "offenceDate": "2011-05-01",
              "chargeStatus": {
                "code": "A",
                "description": "Active"
              },
              "resultCode1": {
                "chargeStatus": "A",
                "code": "1501",
                "description": "Recall to Prison",
                "dispositionCode": "F",
                "conviction": true
              },
              "mostSeriousFlag": false,
              "lidsOffenceNumber": 21
            },
            "offenceDate": "2011-05-01",
            "resultCode1": {
              "chargeStatus": "A",
              "code": "4506",
              "description": "Adjournment",
              "dispositionCode": "I",
              "conviction": false
            },
            "mostSeriousFlag": false
          },
          {
            "eventId": 103580606,
            "offenderCharge": {
              "id": 1453022,
              "offence": {
                "offenceCode": "SX03015-016N",
                "statuteCode": "ZZ",
                "description": "ASSAULT OF A CHILD U13 BY PENETRATION (SOA 2003 s6"
              },
              "offenceDate": "2006-10-17",
              "chargeStatus": {
                "code": "A",
                "description": "Active"
              },
              "resultCode1": {
                "chargeStatus": "A",
                "code": "1501",
                "description": "Recall to Prison",
                "dispositionCode": "F",
                "conviction": true
              },
              "mostSeriousFlag": false,
              "lidsOffenceNumber": 14
            },
            "offenceDate": "2006-10-17",
            "resultCode1": {
              "chargeStatus": "A",
              "code": "4506",
              "description": "Adjournment",
              "dispositionCode": "I",
              "conviction": false
            },
            "mostSeriousFlag": false
          },
          {
            "eventId": 103580606,
            "offenderCharge": {
              "id": 1453023,
              "offence": {
                "offenceCode": "PK78001-008NA",
                "statuteCode": "ZZ",
                "description": "INDECENT PHOTOGRAPHS OF CHILDREN"
              },
              "offenceDate": "2010-10-31",
              "chargeStatus": {
                "code": "A",
                "description": "Active"
              },
              "resultCode1": {
                "chargeStatus": "A",
                "code": "1501",
                "description": "Recall to Prison",
                "dispositionCode": "F",
                "conviction": true
              },
              "mostSeriousFlag": false,
              "lidsOffenceNumber": 17
            },
            "offenceDate": "2010-10-31",
            "resultCode1": {
              "chargeStatus": "A",
              "code": "4506",
              "description": "Adjournment",
              "dispositionCode": "I",
              "conviction": false
            },
            "mostSeriousFlag": false
          },
          {
            "eventId": 103580606,
            "offenderCharge": {
              "id": 1453024,
              "offence": {
                "offenceCode": "PK78001-008NA",
                "statuteCode": "ZZ",
                "description": "INDECENT PHOTOGRAPHS OF CHILDREN"
              },
              "offenceDate": "2011-05-23",
              "chargeStatus": {
                "code": "A",
                "description": "Active"
              },
              "resultCode1": {
                "chargeStatus": "A",
                "code": "1501",
                "description": "Recall to Prison",
                "dispositionCode": "F",
                "conviction": true
              },
              "mostSeriousFlag": false,
              "lidsOffenceNumber": 25
            },
            "offenceDate": "2011-05-23",
            "resultCode1": {
              "chargeStatus": "A",
              "code": "4506",
              "description": "Adjournment",
              "dispositionCode": "I",
              "conviction": false
            },
            "mostSeriousFlag": false
          },
          {
            "eventId": 103580606,
            "offenderCharge": {
              "id": 1453025,
              "offence": {
                "offenceCode": "SX03013-014N",
                "statuteCode": "ZZ",
                "description": "RAPE OF A CHILD U13 (SOA 2003 s5)"
              },
              "offenceDate": "2004-10-17",
              "chargeStatus": {
                "code": "A",
                "description": "Active"
              },
              "resultCode1": {
                "chargeStatus": "A",
                "code": "1501",
                "description": "Recall to Prison",
                "dispositionCode": "F",
                "conviction": true
              },
              "mostSeriousFlag": false,
              "lidsOffenceNumber": 5
            },
            "offenceDate": "2004-10-17",
            "resultCode1": {
              "chargeStatus": "A",
              "code": "4506",
              "description": "Adjournment",
              "dispositionCode": "I",
              "conviction": false
            },
            "mostSeriousFlag": false
          },
          {
            "eventId": 103580606,
            "offenderCharge": {
              "id": 1453026,
              "offence": {
                "offenceCode": "SX56025-029N",
                "statuteCode": "ZZ",
                "description": "INDECENT ASSAULT ON A CHILD"
              },
              "offenceDate": "2004-04-30",
              "chargeStatus": {
                "code": "A",
                "description": "Active"
              },
              "resultCode1": {
                "chargeStatus": "A",
                "code": "1501",
                "description": "Recall to Prison",
                "dispositionCode": "F",
                "conviction": true
              },
              "mostSeriousFlag": false,
              "lidsOffenceNumber": 11
            },
            "offenceDate": "2004-04-30",
            "resultCode1": {
              "chargeStatus": "A",
              "code": "4506",
              "description": "Adjournment",
              "dispositionCode": "I",
              "conviction": false
            },
            "mostSeriousFlag": false
          },
          {
            "eventId": 103580606,
            "offenderCharge": {
              "id": 1453027,
              "offence": {
                "offenceCode": "SX03015-016N",
                "statuteCode": "ZZ",
                "description": "ASSAULT OF A CHILD U13 BY PENETRATION (SOA 2003 s6"
              },
              "offenceDate": "2005-10-17",
              "chargeStatus": {
                "code": "A",
                "description": "Active"
              },
              "resultCode1": {
                "chargeStatus": "A",
                "code": "1501",
                "description": "Recall to Prison",
                "dispositionCode": "F",
                "conviction": true
              },
              "mostSeriousFlag": false,
              "lidsOffenceNumber": 12
            },
            "offenceDate": "2005-10-17",
            "resultCode1": {
              "chargeStatus": "A",
              "code": "4506",
              "description": "Adjournment",
              "dispositionCode": "I",
              "conviction": false
            },
            "mostSeriousFlag": false
          },
          {
            "eventId": 103580606,
            "offenderCharge": {
              "id": 1453028,
              "offence": {
                "offenceCode": "SX03015-016N",
                "statuteCode": "ZZ",
                "description": "ASSAULT OF A CHILD U13 BY PENETRATION (SOA 2003 s6"
              },
              "offenceDate": "2005-10-17",
              "chargeStatus": {
                "code": "A",
                "description": "Active"
              },
              "resultCode1": {
                "chargeStatus": "A",
                "code": "1501",
                "description": "Recall to Prison",
                "dispositionCode": "F",
                "conviction": true
              },
              "mostSeriousFlag": false,
              "lidsOffenceNumber": 13
            },
            "offenceDate": "2005-10-17",
            "resultCode1": {
              "chargeStatus": "A",
              "code": "4506",
              "description": "Adjournment",
              "dispositionCode": "I",
              "conviction": false
            },
            "mostSeriousFlag": false
          },
          {
            "eventId": 103580606,
            "offenderCharge": {
              "id": 1453029,
              "offence": {
                "offenceCode": "SX56070-072N",
                "statuteCode": "ZZ",
                "description": "RAPE ON CHILD (SOA 1956 s69)"
              },
              "offenceDate": "2002-10-17",
              "chargeStatus": {
                "code": "A",
                "description": "Active"
              },
              "resultCode1": {
                "chargeStatus": "A",
                "code": "1501",
                "description": "Recall to Prison",
                "dispositionCode": "F",
                "conviction": true
              },
              "mostSeriousFlag": false,
              "lidsOffenceNumber": 2
            },
            "offenceDate": "2002-10-17",
            "resultCode1": {
              "chargeStatus": "A",
              "code": "4506",
              "description": "Adjournment",
              "dispositionCode": "I",
              "conviction": false
            },
            "mostSeriousFlag": false
          },
          {
            "eventId": 103580606,
            "offenderCharge": {
              "id": 1453030,
              "offence": {
                "offenceCode": "PK78001-008NA",
                "statuteCode": "ZZ",
                "description": "INDECENT PHOTOGRAPHS OF CHILDREN"
              },
              "offenceDate": "2011-05-01",
              "chargeStatus": {
                "code": "A",
                "description": "Active"
              },
              "resultCode1": {
                "chargeStatus": "A",
                "code": "1501",
                "description": "Recall to Prison",
                "dispositionCode": "F",
                "conviction": true
              },
              "mostSeriousFlag": false,
              "lidsOffenceNumber": 22
            },
            "offenceDate": "2011-05-01",
            "resultCode1": {
              "chargeStatus": "A",
              "code": "4506",
              "description": "Adjournment",
              "dispositionCode": "I",
              "conviction": false
            },
            "mostSeriousFlag": false
          },
          {
            "eventId": 103580606,
            "offenderCharge": {
              "id": 1453031,
              "offence": {
                "offenceCode": "PK78001-008NA",
                "statuteCode": "ZZ",
                "description": "INDECENT PHOTOGRAPHS OF CHILDREN"
              },
              "offenceDate": "2010-10-31",
              "chargeStatus": {
                "code": "A",
                "description": "Active"
              },
              "resultCode1": {
                "chargeStatus": "A",
                "code": "1501",
                "description": "Recall to Prison",
                "dispositionCode": "F",
                "conviction": true
              },
              "mostSeriousFlag": false,
              "lidsOffenceNumber": 20
            },
            "offenceDate": "2010-10-31",
            "resultCode1": {
              "chargeStatus": "A",
              "code": "4506",
              "description": "Adjournment",
              "dispositionCode": "I",
              "conviction": false
            },
            "mostSeriousFlag": false
          },
          {
            "eventId": 103580606,
            "offenderCharge": {
              "id": 1453032,
              "offence": {
                "offenceCode": "PK78001-008NA",
                "statuteCode": "ZZ",
                "description": "INDECENT PHOTOGRAPHS OF CHILDREN"
              },
              "offenceDate": "2010-03-27",
              "chargeStatus": {
                "code": "A",
                "description": "Active"
              },
              "resultCode1": {
                "chargeStatus": "A",
                "code": "1501",
                "description": "Recall to Prison",
                "dispositionCode": "F",
                "conviction": true
              },
              "mostSeriousFlag": false,
              "lidsOffenceNumber": 16
            },
            "offenceDate": "2010-03-27",
            "resultCode1": {
              "chargeStatus": "A",
              "code": "4506",
              "description": "Adjournment",
              "dispositionCode": "I",
              "conviction": false
            },
            "mostSeriousFlag": false
          },
          {
            "eventId": 103580606,
            "offenderCharge": {
              "id": 1453033,
              "offence": {
                "offenceCode": "PK78001-008NA",
                "statuteCode": "ZZ",
                "description": "INDECENT PHOTOGRAPHS OF CHILDREN"
              },
              "offenceDate": "2010-10-31",
              "chargeStatus": {
                "code": "A",
                "description": "Active"
              },
              "resultCode1": {
                "chargeStatus": "A",
                "code": "1501",
                "description": "Recall to Prison",
                "dispositionCode": "F",
                "conviction": true
              },
              "mostSeriousFlag": false,
              "lidsOffenceNumber": 19
            },
            "offenceDate": "2010-10-31",
            "resultCode1": {
              "chargeStatus": "A",
              "code": "4506",
              "description": "Adjournment",
              "dispositionCode": "I",
              "conviction": false
            },
            "mostSeriousFlag": false
          },
          {
            "eventId": 103580606,
            "offenderCharge": {
              "id": 1453034,
              "offence": {
                "offenceCode": "PK78001-008NA",
                "statuteCode": "ZZ",
                "description": "INDECENT PHOTOGRAPHS OF CHILDREN"
              },
              "offenceDate": "2010-05-28",
              "chargeStatus": {
                "code": "A",
                "description": "Active"
              },
              "resultCode1": {
                "chargeStatus": "A",
                "code": "1501",
                "description": "Recall to Prison",
                "dispositionCode": "F",
                "conviction": true
              },
              "mostSeriousFlag": false,
              "lidsOffenceNumber": 26
            },
            "offenceDate": "2010-03-28",
            "resultCode1": {
              "chargeStatus": "A",
              "code": "4506",
              "description": "Adjournment",
              "dispositionCode": "I",
              "conviction": false
            },
            "mostSeriousFlag": false
          },
          {
            "eventId": 103580606,
            "offenderCharge": {
              "id": 1453035,
              "offence": {
                "offenceCode": "PK78001-008NA",
                "statuteCode": "ZZ",
                "description": "INDECENT PHOTOGRAPHS OF CHILDREN"
              },
              "offenceDate": "2010-10-31",
              "chargeStatus": {
                "code": "A",
                "description": "Active"
              },
              "resultCode1": {
                "chargeStatus": "A",
                "code": "1501",
                "description": "Recall to Prison",
                "dispositionCode": "F",
                "conviction": true
              },
              "mostSeriousFlag": false,
              "lidsOffenceNumber": 18
            },
            "offenceDate": "2010-10-31",
            "resultCode1": {
              "chargeStatus": "A",
              "code": "4506",
              "description": "Adjournment",
              "dispositionCode": "I",
              "conviction": false
            },
            "mostSeriousFlag": false
          },
          {
            "eventId": 103580606,
            "offenderCharge": {
              "id": 1453036,
              "offence": {
                "offenceCode": "SX56070-072N",
                "statuteCode": "ZZ",
                "description": "RAPE ON CHILD (SOA 1956 s69)"
              },
              "offenceDate": "2003-10-17",
              "chargeStatus": {
                "code": "A",
                "description": "Active"
              },
              "resultCode1": {
                "chargeStatus": "A",
                "code": "1501",
                "description": "Recall to Prison",
                "dispositionCode": "F",
                "conviction": true
              },
              "mostSeriousFlag": false,
              "lidsOffenceNumber": 3
            },
            "offenceDate": "2003-10-17",
            "resultCode1": {
              "chargeStatus": "A",
              "code": "4506",
              "description": "Adjournment",
              "dispositionCode": "I",
              "conviction": false
            },
            "mostSeriousFlag": false
          },
          {
            "eventId": 103580606,
            "offenderCharge": {
              "id": 1453037,
              "offence": {
                "offenceCode": "SX56025-029N",
                "statuteCode": "ZZ",
                "description": "INDECENT ASSAULT ON A CHILD"
              },
              "offenceDate": "2001-10-17",
              "chargeStatus": {
                "code": "A",
                "description": "Active"
              },
              "resultCode1": {
                "chargeStatus": "A",
                "code": "1501",
                "description": "Recall to Prison",
                "dispositionCode": "F",
                "conviction": true
              },
              "mostSeriousFlag": false,
              "lidsOffenceNumber": 8
            },
            "offenceDate": "2001-10-17",
            "resultCode1": {
              "chargeStatus": "A",
              "code": "4506",
              "description": "Adjournment",
              "dispositionCode": "I",
              "conviction": false
            },
            "mostSeriousFlag": false
          },
          {
            "eventId": 103580606,
            "offenderCharge": {
              "id": 1453038,
              "offence": {
                "offenceCode": "PK78001-008NA",
                "statuteCode": "ZZ",
                "description": "INDECENT PHOTOGRAPHS OF CHILDREN"
              },
              "offenceDate": "2011-06-22",
              "chargeStatus": {
                "code": "A",
                "description": "Active"
              },
              "resultCode1": {
                "chargeStatus": "A",
                "code": "1501",
                "description": "Recall to Prison",
                "dispositionCode": "F",
                "conviction": true
              },
              "mostSeriousFlag": false,
              "lidsOffenceNumber": 28
            },
            "offenceDate": "2011-06-22",
            "resultCode1": {
              "chargeStatus": "A",
              "code": "4506",
              "description": "Adjournment",
              "dispositionCode": "I",
              "conviction": false
            },
            "mostSeriousFlag": false
          },
          {
            "eventId": 103580606,
            "offenderCharge": {
              "id": 1453039,
              "offence": {
                "offenceCode": "SX56025-029N",
                "statuteCode": "ZZ",
                "description": "INDECENT ASSAULT ON A CHILD"
              },
              "offenceDate": "2002-10-17",
              "chargeStatus": {
                "code": "A",
                "description": "Active"
              },
              "resultCode1": {
                "chargeStatus": "A",
                "code": "1501",
                "description": "Recall to Prison",
                "dispositionCode": "F",
                "conviction": true
              },
              "mostSeriousFlag": false,
              "lidsOffenceNumber": 9
            },
            "offenceDate": "2002-10-17",
            "resultCode1": {
              "chargeStatus": "A",
              "code": "4506",
              "description": "Adjournment",
              "dispositionCode": "I",
              "conviction": false
            },
            "mostSeriousFlag": false
          }
        ],
        "courtOrders": []
      },
      {
        "id": 103580607,
        "caseId": 660258,
        "offenderNo": "A7163CK",
        "eventDateTime": "2012-01-27T10:03:00",
        "courtEventType": {
          "code": "CA",
          "description": "Court Appearance"
        },
        "eventStatus": {
          "code": "COMP",
          "description": "Completed"
        },
        "courtId": "PBORCC",
        "holdFlag": false,
        "nextEventRequestFlag": true,
        "nextEventDateTime": "2012-03-23T00:00:00",
        "createdDateTime": "2012-02-05T05:04:26.48557",
        "createdByUsername": "XTAG",
        "courtEventCharges": [
          {
            "eventId": 103580607,
            "offenderCharge": {
              "id": 1453012,
              "offence": {
                "offenceCode": "PK78001-008NA",
                "statuteCode": "ZZ",
                "description": "INDECENT PHOTOGRAPHS OF CHILDREN"
              },
              "offenceDate": "2010-10-31",
              "chargeStatus": {
                "code": "A",
                "description": "Active"
              },
              "resultCode1": {
                "chargeStatus": "A",
                "code": "1501",
                "description": "Recall to Prison",
                "dispositionCode": "F",
                "conviction": true
              },
              "mostSeriousFlag": false,
              "lidsOffenceNumber": 23
            },
            "offenceDate": "2010-10-31",
            "resultCode1": {
              "chargeStatus": "A",
              "code": "4506",
              "description": "Adjournment",
              "dispositionCode": "I",
              "conviction": false
            },
            "mostSeriousFlag": false
          },
          {
            "eventId": 103580607,
            "offenderCharge": {
              "id": 1453013,
              "offence": {
                "offenceCode": "SX03013-014N",
                "statuteCode": "ZZ",
                "description": "RAPE OF A CHILD U13 (SOA 2003 s5)"
              },
              "offenceDate": "2006-10-17",
              "chargeStatus": {
                "code": "A",
                "description": "Active"
              },
              "resultCode1": {
                "chargeStatus": "A",
                "code": "1501",
                "description": "Recall to Prison",
                "dispositionCode": "F",
                "conviction": true
              },
              "mostSeriousFlag": false,
              "lidsOffenceNumber": 7
            },
            "offenceDate": "2006-10-17",
            "resultCode1": {
              "chargeStatus": "A",
              "code": "4506",
              "description": "Adjournment",
              "dispositionCode": "I",
              "conviction": false
            },
            "mostSeriousFlag": false
          },
          {
            "eventId": 103580607,
            "offenderCharge": {
              "id": 1453014,
              "offence": {
                "offenceCode": "SX56070-072N",
                "statuteCode": "ZZ",
                "description": "RAPE ON CHILD (SOA 1956 s69)"
              },
              "offenceDate": "2001-10-17",
              "chargeStatus": {
                "code": "A",
                "description": "Active"
              },
              "resultCode1": {
                "chargeStatus": "A",
                "code": "1501",
                "description": "Recall to Prison",
                "dispositionCode": "F",
                "conviction": true
              },
              "mostSeriousFlag": false,
              "lidsOffenceNumber": 1
            },
            "offenceDate": "2001-10-17",
            "resultCode1": {
              "chargeStatus": "A",
              "code": "4506",
              "description": "Adjournment",
              "dispositionCode": "I",
              "conviction": false
            },
            "mostSeriousFlag": false
          },
          {
            "eventId": 103580607,
            "offenderCharge": {
              "id": 1453015,
              "offence": {
                "offenceCode": "SX56070-072N",
                "statuteCode": "ZZ",
                "description": "RAPE ON CHILD (SOA 1956 s69)"
              },
              "offenceDate": "2004-04-30",
              "chargeStatus": {
                "code": "A",
                "description": "Active"
              },
              "resultCode1": {
                "chargeStatus": "A",
                "code": "1501",
                "description": "Recall to Prison",
                "dispositionCode": "F",
                "conviction": true
              },
              "mostSeriousFlag": false,
              "lidsOffenceNumber": 4
            },
            "offenceDate": "2004-04-30",
            "resultCode1": {
              "chargeStatus": "A",
              "code": "4506",
              "description": "Adjournment",
              "dispositionCode": "I",
              "conviction": false
            },
            "mostSeriousFlag": false
          },
          {
            "eventId": 103580607,
            "offenderCharge": {
              "id": 1453016,
              "offence": {
                "offenceCode": "PK78001-008NA",
                "statuteCode": "ZZ",
                "description": "INDECENT PHOTOGRAPHS OF CHILDREN"
              },
              "offenceDate": "2010-03-28",
              "chargeStatus": {
                "code": "A",
                "description": "Active"
              },
              "resultCode1": {
                "chargeStatus": "A",
                "code": "1501",
                "description": "Recall to Prison",
                "dispositionCode": "F",
                "conviction": true
              },
              "mostSeriousFlag": false,
              "lidsOffenceNumber": 15
            },
            "offenceDate": "2010-03-28",
            "resultCode1": {
              "chargeStatus": "A",
              "code": "4506",
              "description": "Adjournment",
              "dispositionCode": "I",
              "conviction": false
            },
            "mostSeriousFlag": false
          },
          {
            "eventId": 103580607,
            "offenderCharge": {
              "id": 1453017,
              "offence": {
                "offenceCode": "PK78001-008NA",
                "statuteCode": "ZZ",
                "description": "INDECENT PHOTOGRAPHS OF CHILDREN"
              },
              "offenceDate": "2011-06-30",
              "chargeStatus": {
                "code": "A",
                "description": "Active"
              },
              "resultCode1": {
                "chargeStatus": "A",
                "code": "1501",
                "description": "Recall to Prison",
                "dispositionCode": "F",
                "conviction": true
              },
              "mostSeriousFlag": false,
              "lidsOffenceNumber": 27
            },
            "offenceDate": "2011-06-30",
            "resultCode1": {
              "chargeStatus": "A",
              "code": "4506",
              "description": "Adjournment",
              "dispositionCode": "I",
              "conviction": false
            },
            "mostSeriousFlag": false
          },
          {
            "eventId": 103580607,
            "offenderCharge": {
              "id": 1453018,
              "offence": {
                "offenceCode": "SX03013-014N",
                "statuteCode": "ZZ",
                "description": "RAPE OF A CHILD U13 (SOA 2003 s5)"
              },
              "offenceDate": "2005-10-17",
              "chargeStatus": {
                "code": "A",
                "description": "Active"
              },
              "resultCode1": {
                "chargeStatus": "A",
                "code": "1501",
                "description": "Recall to Prison",
                "dispositionCode": "F",
                "conviction": true
              },
              "mostSeriousFlag": false,
              "lidsOffenceNumber": 6
            },
            "offenceDate": "2005-10-17",
            "resultCode1": {
              "chargeStatus": "A",
              "code": "4506",
              "description": "Adjournment",
              "dispositionCode": "I",
              "conviction": false
            },
            "mostSeriousFlag": false
          },
          {
            "eventId": 103580607,
            "offenderCharge": {
              "id": 1453019,
              "offence": {
                "offenceCode": "PK78001-008NA",
                "statuteCode": "ZZ",
                "description": "INDECENT PHOTOGRAPHS OF CHILDREN"
              },
              "offenceDate": "2011-03-27",
              "chargeStatus": {
                "code": "A",
                "description": "Active"
              },
              "resultCode1": {
                "chargeStatus": "A",
                "code": "1501",
                "description": "Recall to Prison",
                "dispositionCode": "F",
                "conviction": true
              },
              "mostSeriousFlag": false,
              "lidsOffenceNumber": 24
            },
            "offenceDate": "2011-03-27",
            "resultCode1": {
              "chargeStatus": "A",
              "code": "4506",
              "description": "Adjournment",
              "dispositionCode": "I",
              "conviction": false
            },
            "mostSeriousFlag": false
          },
          {
            "eventId": 103580607,
            "offenderCharge": {
              "id": 1453020,
              "offence": {
                "offenceCode": "SX56025-029N",
                "statuteCode": "ZZ",
                "description": "INDECENT ASSAULT ON A CHILD"
              },
              "offenceDate": "2003-10-17",
              "chargeStatus": {
                "code": "A",
                "description": "Active"
              },
              "resultCode1": {
                "chargeStatus": "A",
                "code": "1501",
                "description": "Recall to Prison",
                "dispositionCode": "F",
                "conviction": true
              },
              "mostSeriousFlag": false,
              "lidsOffenceNumber": 10
            },
            "offenceDate": "2003-10-17",
            "resultCode1": {
              "chargeStatus": "A",
              "code": "4506",
              "description": "Adjournment",
              "dispositionCode": "I",
              "conviction": false
            },
            "mostSeriousFlag": false
          },
          {
            "eventId": 103580607,
            "offenderCharge": {
              "id": 1453021,
              "offence": {
                "offenceCode": "PK78001-008NA",
                "statuteCode": "ZZ",
                "description": "INDECENT PHOTOGRAPHS OF CHILDREN"
              },
              "offenceDate": "2011-05-01",
              "chargeStatus": {
                "code": "A",
                "description": "Active"
              },
              "resultCode1": {
                "chargeStatus": "A",
                "code": "1501",
                "description": "Recall to Prison",
                "dispositionCode": "F",
                "conviction": true
              },
              "mostSeriousFlag": false,
              "lidsOffenceNumber": 21
            },
            "offenceDate": "2011-05-01",
            "resultCode1": {
              "chargeStatus": "A",
              "code": "4506",
              "description": "Adjournment",
              "dispositionCode": "I",
              "conviction": false
            },
            "mostSeriousFlag": false
          },
          {
            "eventId": 103580607,
            "offenderCharge": {
              "id": 1453022,
              "offence": {
                "offenceCode": "SX03015-016N",
                "statuteCode": "ZZ",
                "description": "ASSAULT OF A CHILD U13 BY PENETRATION (SOA 2003 s6"
              },
              "offenceDate": "2006-10-17",
              "chargeStatus": {
                "code": "A",
                "description": "Active"
              },
              "resultCode1": {
                "chargeStatus": "A",
                "code": "1501",
                "description": "Recall to Prison",
                "dispositionCode": "F",
                "conviction": true
              },
              "mostSeriousFlag": false,
              "lidsOffenceNumber": 14
            },
            "offenceDate": "2006-10-17",
            "resultCode1": {
              "chargeStatus": "A",
              "code": "4506",
              "description": "Adjournment",
              "dispositionCode": "I",
              "conviction": false
            },
            "mostSeriousFlag": false
          },
          {
            "eventId": 103580607,
            "offenderCharge": {
              "id": 1453023,
              "offence": {
                "offenceCode": "PK78001-008NA",
                "statuteCode": "ZZ",
                "description": "INDECENT PHOTOGRAPHS OF CHILDREN"
              },
              "offenceDate": "2010-10-31",
              "chargeStatus": {
                "code": "A",
                "description": "Active"
              },
              "resultCode1": {
                "chargeStatus": "A",
                "code": "1501",
                "description": "Recall to Prison",
                "dispositionCode": "F",
                "conviction": true
              },
              "mostSeriousFlag": false,
              "lidsOffenceNumber": 17
            },
            "offenceDate": "2010-10-31",
            "resultCode1": {
              "chargeStatus": "A",
              "code": "4506",
              "description": "Adjournment",
              "dispositionCode": "I",
              "conviction": false
            },
            "mostSeriousFlag": false
          },
          {
            "eventId": 103580607,
            "offenderCharge": {
              "id": 1453024,
              "offence": {
                "offenceCode": "PK78001-008NA",
                "statuteCode": "ZZ",
                "description": "INDECENT PHOTOGRAPHS OF CHILDREN"
              },
              "offenceDate": "2011-05-23",
              "chargeStatus": {
                "code": "A",
                "description": "Active"
              },
              "resultCode1": {
                "chargeStatus": "A",
                "code": "1501",
                "description": "Recall to Prison",
                "dispositionCode": "F",
                "conviction": true
              },
              "mostSeriousFlag": false,
              "lidsOffenceNumber": 25
            },
            "offenceDate": "2011-05-23",
            "resultCode1": {
              "chargeStatus": "A",
              "code": "4506",
              "description": "Adjournment",
              "dispositionCode": "I",
              "conviction": false
            },
            "mostSeriousFlag": false
          },
          {
            "eventId": 103580607,
            "offenderCharge": {
              "id": 1453025,
              "offence": {
                "offenceCode": "SX03013-014N",
                "statuteCode": "ZZ",
                "description": "RAPE OF A CHILD U13 (SOA 2003 s5)"
              },
              "offenceDate": "2004-10-17",
              "chargeStatus": {
                "code": "A",
                "description": "Active"
              },
              "resultCode1": {
                "chargeStatus": "A",
                "code": "1501",
                "description": "Recall to Prison",
                "dispositionCode": "F",
                "conviction": true
              },
              "mostSeriousFlag": false,
              "lidsOffenceNumber": 5
            },
            "offenceDate": "2004-10-17",
            "resultCode1": {
              "chargeStatus": "A",
              "code": "4506",
              "description": "Adjournment",
              "dispositionCode": "I",
              "conviction": false
            },
            "mostSeriousFlag": false
          },
          {
            "eventId": 103580607,
            "offenderCharge": {
              "id": 1453026,
              "offence": {
                "offenceCode": "SX56025-029N",
                "statuteCode": "ZZ",
                "description": "INDECENT ASSAULT ON A CHILD"
              },
              "offenceDate": "2004-04-30",
              "chargeStatus": {
                "code": "A",
                "description": "Active"
              },
              "resultCode1": {
                "chargeStatus": "A",
                "code": "1501",
                "description": "Recall to Prison",
                "dispositionCode": "F",
                "conviction": true
              },
              "mostSeriousFlag": false,
              "lidsOffenceNumber": 11
            },
            "offenceDate": "2004-04-30",
            "resultCode1": {
              "chargeStatus": "A",
              "code": "4506",
              "description": "Adjournment",
              "dispositionCode": "I",
              "conviction": false
            },
            "mostSeriousFlag": false
          },
          {
            "eventId": 103580607,
            "offenderCharge": {
              "id": 1453027,
              "offence": {
                "offenceCode": "SX03015-016N",
                "statuteCode": "ZZ",
                "description": "ASSAULT OF A CHILD U13 BY PENETRATION (SOA 2003 s6"
              },
              "offenceDate": "2005-10-17",
              "chargeStatus": {
                "code": "A",
                "description": "Active"
              },
              "resultCode1": {
                "chargeStatus": "A",
                "code": "1501",
                "description": "Recall to Prison",
                "dispositionCode": "F",
                "conviction": true
              },
              "mostSeriousFlag": false,
              "lidsOffenceNumber": 12
            },
            "offenceDate": "2005-10-17",
            "resultCode1": {
              "chargeStatus": "A",
              "code": "4506",
              "description": "Adjournment",
              "dispositionCode": "I",
              "conviction": false
            },
            "mostSeriousFlag": false
          },
          {
            "eventId": 103580607,
            "offenderCharge": {
              "id": 1453028,
              "offence": {
                "offenceCode": "SX03015-016N",
                "statuteCode": "ZZ",
                "description": "ASSAULT OF A CHILD U13 BY PENETRATION (SOA 2003 s6"
              },
              "offenceDate": "2005-10-17",
              "chargeStatus": {
                "code": "A",
                "description": "Active"
              },
              "resultCode1": {
                "chargeStatus": "A",
                "code": "1501",
                "description": "Recall to Prison",
                "dispositionCode": "F",
                "conviction": true
              },
              "mostSeriousFlag": false,
              "lidsOffenceNumber": 13
            },
            "offenceDate": "2005-10-17",
            "resultCode1": {
              "chargeStatus": "A",
              "code": "4506",
              "description": "Adjournment",
              "dispositionCode": "I",
              "conviction": false
            },
            "mostSeriousFlag": false
          },
          {
            "eventId": 103580607,
            "offenderCharge": {
              "id": 1453029,
              "offence": {
                "offenceCode": "SX56070-072N",
                "statuteCode": "ZZ",
                "description": "RAPE ON CHILD (SOA 1956 s69)"
              },
              "offenceDate": "2002-10-17",
              "chargeStatus": {
                "code": "A",
                "description": "Active"
              },
              "resultCode1": {
                "chargeStatus": "A",
                "code": "1501",
                "description": "Recall to Prison",
                "dispositionCode": "F",
                "conviction": true
              },
              "mostSeriousFlag": false,
              "lidsOffenceNumber": 2
            },
            "offenceDate": "2002-10-17",
            "resultCode1": {
              "chargeStatus": "A",
              "code": "4506",
              "description": "Adjournment",
              "dispositionCode": "I",
              "conviction": false
            },
            "mostSeriousFlag": false
          },
          {
            "eventId": 103580607,
            "offenderCharge": {
              "id": 1453030,
              "offence": {
                "offenceCode": "PK78001-008NA",
                "statuteCode": "ZZ",
                "description": "INDECENT PHOTOGRAPHS OF CHILDREN"
              },
              "offenceDate": "2011-05-01",
              "chargeStatus": {
                "code": "A",
                "description": "Active"
              },
              "resultCode1": {
                "chargeStatus": "A",
                "code": "1501",
                "description": "Recall to Prison",
                "dispositionCode": "F",
                "conviction": true
              },
              "mostSeriousFlag": false,
              "lidsOffenceNumber": 22
            },
            "offenceDate": "2011-05-01",
            "resultCode1": {
              "chargeStatus": "A",
              "code": "4506",
              "description": "Adjournment",
              "dispositionCode": "I",
              "conviction": false
            },
            "mostSeriousFlag": false
          },
          {
            "eventId": 103580607,
            "offenderCharge": {
              "id": 1453031,
              "offence": {
                "offenceCode": "PK78001-008NA",
                "statuteCode": "ZZ",
                "description": "INDECENT PHOTOGRAPHS OF CHILDREN"
              },
              "offenceDate": "2010-10-31",
              "chargeStatus": {
                "code": "A",
                "description": "Active"
              },
              "resultCode1": {
                "chargeStatus": "A",
                "code": "1501",
                "description": "Recall to Prison",
                "dispositionCode": "F",
                "conviction": true
              },
              "mostSeriousFlag": false,
              "lidsOffenceNumber": 20
            },
            "offenceDate": "2010-10-31",
            "resultCode1": {
              "chargeStatus": "A",
              "code": "4506",
              "description": "Adjournment",
              "dispositionCode": "I",
              "conviction": false
            },
            "mostSeriousFlag": false
          },
          {
            "eventId": 103580607,
            "offenderCharge": {
              "id": 1453032,
              "offence": {
                "offenceCode": "PK78001-008NA",
                "statuteCode": "ZZ",
                "description": "INDECENT PHOTOGRAPHS OF CHILDREN"
              },
              "offenceDate": "2010-03-27",
              "chargeStatus": {
                "code": "A",
                "description": "Active"
              },
              "resultCode1": {
                "chargeStatus": "A",
                "code": "1501",
                "description": "Recall to Prison",
                "dispositionCode": "F",
                "conviction": true
              },
              "mostSeriousFlag": false,
              "lidsOffenceNumber": 16
            },
            "offenceDate": "2010-03-27",
            "resultCode1": {
              "chargeStatus": "A",
              "code": "4506",
              "description": "Adjournment",
              "dispositionCode": "I",
              "conviction": false
            },
            "mostSeriousFlag": false
          },
          {
            "eventId": 103580607,
            "offenderCharge": {
              "id": 1453033,
              "offence": {
                "offenceCode": "PK78001-008NA",
                "statuteCode": "ZZ",
                "description": "INDECENT PHOTOGRAPHS OF CHILDREN"
              },
              "offenceDate": "2010-10-31",
              "chargeStatus": {
                "code": "A",
                "description": "Active"
              },
              "resultCode1": {
                "chargeStatus": "A",
                "code": "1501",
                "description": "Recall to Prison",
                "dispositionCode": "F",
                "conviction": true
              },
              "mostSeriousFlag": false,
              "lidsOffenceNumber": 19
            },
            "offenceDate": "2010-10-31",
            "resultCode1": {
              "chargeStatus": "A",
              "code": "4506",
              "description": "Adjournment",
              "dispositionCode": "I",
              "conviction": false
            },
            "mostSeriousFlag": false
          },
          {
            "eventId": 103580607,
            "offenderCharge": {
              "id": 1453034,
              "offence": {
                "offenceCode": "PK78001-008NA",
                "statuteCode": "ZZ",
                "description": "INDECENT PHOTOGRAPHS OF CHILDREN"
              },
              "offenceDate": "2010-05-28",
              "chargeStatus": {
                "code": "A",
                "description": "Active"
              },
              "resultCode1": {
                "chargeStatus": "A",
                "code": "1501",
                "description": "Recall to Prison",
                "dispositionCode": "F",
                "conviction": true
              },
              "mostSeriousFlag": false,
              "lidsOffenceNumber": 26
            },
            "offenceDate": "2010-03-28",
            "resultCode1": {
              "chargeStatus": "A",
              "code": "4506",
              "description": "Adjournment",
              "dispositionCode": "I",
              "conviction": false
            },
            "mostSeriousFlag": false
          },
          {
            "eventId": 103580607,
            "offenderCharge": {
              "id": 1453035,
              "offence": {
                "offenceCode": "PK78001-008NA",
                "statuteCode": "ZZ",
                "description": "INDECENT PHOTOGRAPHS OF CHILDREN"
              },
              "offenceDate": "2010-10-31",
              "chargeStatus": {
                "code": "A",
                "description": "Active"
              },
              "resultCode1": {
                "chargeStatus": "A",
                "code": "1501",
                "description": "Recall to Prison",
                "dispositionCode": "F",
                "conviction": true
              },
              "mostSeriousFlag": false,
              "lidsOffenceNumber": 18
            },
            "offenceDate": "2010-10-31",
            "resultCode1": {
              "chargeStatus": "A",
              "code": "4506",
              "description": "Adjournment",
              "dispositionCode": "I",
              "conviction": false
            },
            "mostSeriousFlag": false
          },
          {
            "eventId": 103580607,
            "offenderCharge": {
              "id": 1453036,
              "offence": {
                "offenceCode": "SX56070-072N",
                "statuteCode": "ZZ",
                "description": "RAPE ON CHILD (SOA 1956 s69)"
              },
              "offenceDate": "2003-10-17",
              "chargeStatus": {
                "code": "A",
                "description": "Active"
              },
              "resultCode1": {
                "chargeStatus": "A",
                "code": "1501",
                "description": "Recall to Prison",
                "dispositionCode": "F",
                "conviction": true
              },
              "mostSeriousFlag": false,
              "lidsOffenceNumber": 3
            },
            "offenceDate": "2003-10-17",
            "resultCode1": {
              "chargeStatus": "A",
              "code": "4506",
              "description": "Adjournment",
              "dispositionCode": "I",
              "conviction": false
            },
            "mostSeriousFlag": false
          },
          {
            "eventId": 103580607,
            "offenderCharge": {
              "id": 1453037,
              "offence": {
                "offenceCode": "SX56025-029N",
                "statuteCode": "ZZ",
                "description": "INDECENT ASSAULT ON A CHILD"
              },
              "offenceDate": "2001-10-17",
              "chargeStatus": {
                "code": "A",
                "description": "Active"
              },
              "resultCode1": {
                "chargeStatus": "A",
                "code": "1501",
                "description": "Recall to Prison",
                "dispositionCode": "F",
                "conviction": true
              },
              "mostSeriousFlag": false,
              "lidsOffenceNumber": 8
            },
            "offenceDate": "2001-10-17",
            "resultCode1": {
              "chargeStatus": "A",
              "code": "4506",
              "description": "Adjournment",
              "dispositionCode": "I",
              "conviction": false
            },
            "mostSeriousFlag": false
          },
          {
            "eventId": 103580607,
            "offenderCharge": {
              "id": 1453038,
              "offence": {
                "offenceCode": "PK78001-008NA",
                "statuteCode": "ZZ",
                "description": "INDECENT PHOTOGRAPHS OF CHILDREN"
              },
              "offenceDate": "2011-06-22",
              "chargeStatus": {
                "code": "A",
                "description": "Active"
              },
              "resultCode1": {
                "chargeStatus": "A",
                "code": "1501",
                "description": "Recall to Prison",
                "dispositionCode": "F",
                "conviction": true
              },
              "mostSeriousFlag": false,
              "lidsOffenceNumber": 28
            },
            "offenceDate": "2011-06-22",
            "resultCode1": {
              "chargeStatus": "A",
              "code": "4506",
              "description": "Adjournment",
              "dispositionCode": "I",
              "conviction": false
            },
            "mostSeriousFlag": false
          },
          {
            "eventId": 103580607,
            "offenderCharge": {
              "id": 1453039,
              "offence": {
                "offenceCode": "SX56025-029N",
                "statuteCode": "ZZ",
                "description": "INDECENT ASSAULT ON A CHILD"
              },
              "offenceDate": "2002-10-17",
              "chargeStatus": {
                "code": "A",
                "description": "Active"
              },
              "resultCode1": {
                "chargeStatus": "A",
                "code": "1501",
                "description": "Recall to Prison",
                "dispositionCode": "F",
                "conviction": true
              },
              "mostSeriousFlag": false,
              "lidsOffenceNumber": 9
            },
            "offenceDate": "2002-10-17",
            "resultCode1": {
              "chargeStatus": "A",
              "code": "4506",
              "description": "Adjournment",
              "dispositionCode": "I",
              "conviction": false
            },
            "mostSeriousFlag": false
          }
        ],
        "courtOrders": []
      },
      {
        "id": 103580608,
        "caseId": 660258,
        "offenderNo": "A7163CK",
        "eventDateTime": "2012-01-27T10:01:00",
        "courtEventType": {
          "code": "CA",
          "description": "Court Appearance"
        },
        "eventStatus": {
          "code": "SCH",
          "description": "Scheduled (Approved)"
        },
        "courtId": "PBORCC",
        "commentText": "CONVICTD. ",
        "holdFlag": false,
        "nextEventRequestFlag": false,
        "createdDateTime": "2012-02-05T05:04:26.490837",
        "createdByUsername": "XTAG",
        "courtEventCharges": [
          {
            "eventId": 103580608,
            "offenderCharge": {
              "id": 1453012,
              "offence": {
                "offenceCode": "PK78001-008NA",
                "statuteCode": "ZZ",
                "description": "INDECENT PHOTOGRAPHS OF CHILDREN"
              },
              "offenceDate": "2010-10-31",
              "chargeStatus": {
                "code": "A",
                "description": "Active"
              },
              "resultCode1": {
                "chargeStatus": "A",
                "code": "1501",
                "description": "Recall to Prison",
                "dispositionCode": "F",
                "conviction": true
              },
              "mostSeriousFlag": false,
              "lidsOffenceNumber": 23
            },
            "offenceDate": "2010-10-31",
            "resultCode1": {
              "chargeStatus": "A",
              "code": "4506",
              "description": "Adjournment",
              "dispositionCode": "I",
              "conviction": false
            },
            "mostSeriousFlag": false
          },
          {
            "eventId": 103580608,
            "offenderCharge": {
              "id": 1453013,
              "offence": {
                "offenceCode": "SX03013-014N",
                "statuteCode": "ZZ",
                "description": "RAPE OF A CHILD U13 (SOA 2003 s5)"
              },
              "offenceDate": "2006-10-17",
              "chargeStatus": {
                "code": "A",
                "description": "Active"
              },
              "resultCode1": {
                "chargeStatus": "A",
                "code": "1501",
                "description": "Recall to Prison",
                "dispositionCode": "F",
                "conviction": true
              },
              "mostSeriousFlag": false,
              "lidsOffenceNumber": 7
            },
            "offenceDate": "2006-10-17",
            "resultCode1": {
              "chargeStatus": "A",
              "code": "4506",
              "description": "Adjournment",
              "dispositionCode": "I",
              "conviction": false
            },
            "mostSeriousFlag": false
          },
          {
            "eventId": 103580608,
            "offenderCharge": {
              "id": 1453014,
              "offence": {
                "offenceCode": "SX56070-072N",
                "statuteCode": "ZZ",
                "description": "RAPE ON CHILD (SOA 1956 s69)"
              },
              "offenceDate": "2001-10-17",
              "chargeStatus": {
                "code": "A",
                "description": "Active"
              },
              "resultCode1": {
                "chargeStatus": "A",
                "code": "1501",
                "description": "Recall to Prison",
                "dispositionCode": "F",
                "conviction": true
              },
              "mostSeriousFlag": false,
              "lidsOffenceNumber": 1
            },
            "offenceDate": "2001-10-17",
            "resultCode1": {
              "chargeStatus": "A",
              "code": "4506",
              "description": "Adjournment",
              "dispositionCode": "I",
              "conviction": false
            },
            "mostSeriousFlag": false
          },
          {
            "eventId": 103580608,
            "offenderCharge": {
              "id": 1453015,
              "offence": {
                "offenceCode": "SX56070-072N",
                "statuteCode": "ZZ",
                "description": "RAPE ON CHILD (SOA 1956 s69)"
              },
              "offenceDate": "2004-04-30",
              "chargeStatus": {
                "code": "A",
                "description": "Active"
              },
              "resultCode1": {
                "chargeStatus": "A",
                "code": "1501",
                "description": "Recall to Prison",
                "dispositionCode": "F",
                "conviction": true
              },
              "mostSeriousFlag": false,
              "lidsOffenceNumber": 4
            },
            "offenceDate": "2004-04-30",
            "resultCode1": {
              "chargeStatus": "A",
              "code": "4506",
              "description": "Adjournment",
              "dispositionCode": "I",
              "conviction": false
            },
            "mostSeriousFlag": false
          },
          {
            "eventId": 103580608,
            "offenderCharge": {
              "id": 1453016,
              "offence": {
                "offenceCode": "PK78001-008NA",
                "statuteCode": "ZZ",
                "description": "INDECENT PHOTOGRAPHS OF CHILDREN"
              },
              "offenceDate": "2010-03-28",
              "chargeStatus": {
                "code": "A",
                "description": "Active"
              },
              "resultCode1": {
                "chargeStatus": "A",
                "code": "1501",
                "description": "Recall to Prison",
                "dispositionCode": "F",
                "conviction": true
              },
              "mostSeriousFlag": false,
              "lidsOffenceNumber": 15
            },
            "offenceDate": "2010-03-28",
            "resultCode1": {
              "chargeStatus": "A",
              "code": "4506",
              "description": "Adjournment",
              "dispositionCode": "I",
              "conviction": false
            },
            "mostSeriousFlag": false
          },
          {
            "eventId": 103580608,
            "offenderCharge": {
              "id": 1453017,
              "offence": {
                "offenceCode": "PK78001-008NA",
                "statuteCode": "ZZ",
                "description": "INDECENT PHOTOGRAPHS OF CHILDREN"
              },
              "offenceDate": "2011-06-30",
              "chargeStatus": {
                "code": "A",
                "description": "Active"
              },
              "resultCode1": {
                "chargeStatus": "A",
                "code": "1501",
                "description": "Recall to Prison",
                "dispositionCode": "F",
                "conviction": true
              },
              "mostSeriousFlag": false,
              "lidsOffenceNumber": 27
            },
            "offenceDate": "2011-06-30",
            "resultCode1": {
              "chargeStatus": "A",
              "code": "4506",
              "description": "Adjournment",
              "dispositionCode": "I",
              "conviction": false
            },
            "mostSeriousFlag": false
          },
          {
            "eventId": 103580608,
            "offenderCharge": {
              "id": 1453018,
              "offence": {
                "offenceCode": "SX03013-014N",
                "statuteCode": "ZZ",
                "description": "RAPE OF A CHILD U13 (SOA 2003 s5)"
              },
              "offenceDate": "2005-10-17",
              "chargeStatus": {
                "code": "A",
                "description": "Active"
              },
              "resultCode1": {
                "chargeStatus": "A",
                "code": "1501",
                "description": "Recall to Prison",
                "dispositionCode": "F",
                "conviction": true
              },
              "mostSeriousFlag": false,
              "lidsOffenceNumber": 6
            },
            "offenceDate": "2005-10-17",
            "resultCode1": {
              "chargeStatus": "A",
              "code": "4506",
              "description": "Adjournment",
              "dispositionCode": "I",
              "conviction": false
            },
            "mostSeriousFlag": false
          },
          {
            "eventId": 103580608,
            "offenderCharge": {
              "id": 1453019,
              "offence": {
                "offenceCode": "PK78001-008NA",
                "statuteCode": "ZZ",
                "description": "INDECENT PHOTOGRAPHS OF CHILDREN"
              },
              "offenceDate": "2011-03-27",
              "chargeStatus": {
                "code": "A",
                "description": "Active"
              },
              "resultCode1": {
                "chargeStatus": "A",
                "code": "1501",
                "description": "Recall to Prison",
                "dispositionCode": "F",
                "conviction": true
              },
              "mostSeriousFlag": false,
              "lidsOffenceNumber": 24
            },
            "offenceDate": "2011-03-27",
            "resultCode1": {
              "chargeStatus": "A",
              "code": "4506",
              "description": "Adjournment",
              "dispositionCode": "I",
              "conviction": false
            },
            "mostSeriousFlag": false
          },
          {
            "eventId": 103580608,
            "offenderCharge": {
              "id": 1453020,
              "offence": {
                "offenceCode": "SX56025-029N",
                "statuteCode": "ZZ",
                "description": "INDECENT ASSAULT ON A CHILD"
              },
              "offenceDate": "2003-10-17",
              "chargeStatus": {
                "code": "A",
                "description": "Active"
              },
              "resultCode1": {
                "chargeStatus": "A",
                "code": "1501",
                "description": "Recall to Prison",
                "dispositionCode": "F",
                "conviction": true
              },
              "mostSeriousFlag": false,
              "lidsOffenceNumber": 10
            },
            "offenceDate": "2003-10-17",
            "resultCode1": {
              "chargeStatus": "A",
              "code": "4506",
              "description": "Adjournment",
              "dispositionCode": "I",
              "conviction": false
            },
            "mostSeriousFlag": false
          },
          {
            "eventId": 103580608,
            "offenderCharge": {
              "id": 1453021,
              "offence": {
                "offenceCode": "PK78001-008NA",
                "statuteCode": "ZZ",
                "description": "INDECENT PHOTOGRAPHS OF CHILDREN"
              },
              "offenceDate": "2011-05-01",
              "chargeStatus": {
                "code": "A",
                "description": "Active"
              },
              "resultCode1": {
                "chargeStatus": "A",
                "code": "1501",
                "description": "Recall to Prison",
                "dispositionCode": "F",
                "conviction": true
              },
              "mostSeriousFlag": false,
              "lidsOffenceNumber": 21
            },
            "offenceDate": "2011-05-01",
            "resultCode1": {
              "chargeStatus": "A",
              "code": "4506",
              "description": "Adjournment",
              "dispositionCode": "I",
              "conviction": false
            },
            "mostSeriousFlag": false
          },
          {
            "eventId": 103580608,
            "offenderCharge": {
              "id": 1453022,
              "offence": {
                "offenceCode": "SX03015-016N",
                "statuteCode": "ZZ",
                "description": "ASSAULT OF A CHILD U13 BY PENETRATION (SOA 2003 s6"
              },
              "offenceDate": "2006-10-17",
              "chargeStatus": {
                "code": "A",
                "description": "Active"
              },
              "resultCode1": {
                "chargeStatus": "A",
                "code": "1501",
                "description": "Recall to Prison",
                "dispositionCode": "F",
                "conviction": true
              },
              "mostSeriousFlag": false,
              "lidsOffenceNumber": 14
            },
            "offenceDate": "2006-10-17",
            "resultCode1": {
              "chargeStatus": "A",
              "code": "4506",
              "description": "Adjournment",
              "dispositionCode": "I",
              "conviction": false
            },
            "mostSeriousFlag": false
          },
          {
            "eventId": 103580608,
            "offenderCharge": {
              "id": 1453023,
              "offence": {
                "offenceCode": "PK78001-008NA",
                "statuteCode": "ZZ",
                "description": "INDECENT PHOTOGRAPHS OF CHILDREN"
              },
              "offenceDate": "2010-10-31",
              "chargeStatus": {
                "code": "A",
                "description": "Active"
              },
              "resultCode1": {
                "chargeStatus": "A",
                "code": "1501",
                "description": "Recall to Prison",
                "dispositionCode": "F",
                "conviction": true
              },
              "mostSeriousFlag": false,
              "lidsOffenceNumber": 17
            },
            "offenceDate": "2010-10-31",
            "resultCode1": {
              "chargeStatus": "A",
              "code": "4506",
              "description": "Adjournment",
              "dispositionCode": "I",
              "conviction": false
            },
            "mostSeriousFlag": false
          },
          {
            "eventId": 103580608,
            "offenderCharge": {
              "id": 1453024,
              "offence": {
                "offenceCode": "PK78001-008NA",
                "statuteCode": "ZZ",
                "description": "INDECENT PHOTOGRAPHS OF CHILDREN"
              },
              "offenceDate": "2011-05-23",
              "chargeStatus": {
                "code": "A",
                "description": "Active"
              },
              "resultCode1": {
                "chargeStatus": "A",
                "code": "1501",
                "description": "Recall to Prison",
                "dispositionCode": "F",
                "conviction": true
              },
              "mostSeriousFlag": false,
              "lidsOffenceNumber": 25
            },
            "offenceDate": "2011-05-23",
            "resultCode1": {
              "chargeStatus": "A",
              "code": "4506",
              "description": "Adjournment",
              "dispositionCode": "I",
              "conviction": false
            },
            "mostSeriousFlag": false
          },
          {
            "eventId": 103580608,
            "offenderCharge": {
              "id": 1453025,
              "offence": {
                "offenceCode": "SX03013-014N",
                "statuteCode": "ZZ",
                "description": "RAPE OF A CHILD U13 (SOA 2003 s5)"
              },
              "offenceDate": "2004-10-17",
              "chargeStatus": {
                "code": "A",
                "description": "Active"
              },
              "resultCode1": {
                "chargeStatus": "A",
                "code": "1501",
                "description": "Recall to Prison",
                "dispositionCode": "F",
                "conviction": true
              },
              "mostSeriousFlag": false,
              "lidsOffenceNumber": 5
            },
            "offenceDate": "2004-10-17",
            "resultCode1": {
              "chargeStatus": "A",
              "code": "4506",
              "description": "Adjournment",
              "dispositionCode": "I",
              "conviction": false
            },
            "mostSeriousFlag": false
          },
          {
            "eventId": 103580608,
            "offenderCharge": {
              "id": 1453026,
              "offence": {
                "offenceCode": "SX56025-029N",
                "statuteCode": "ZZ",
                "description": "INDECENT ASSAULT ON A CHILD"
              },
              "offenceDate": "2004-04-30",
              "chargeStatus": {
                "code": "A",
                "description": "Active"
              },
              "resultCode1": {
                "chargeStatus": "A",
                "code": "1501",
                "description": "Recall to Prison",
                "dispositionCode": "F",
                "conviction": true
              },
              "mostSeriousFlag": false,
              "lidsOffenceNumber": 11
            },
            "offenceDate": "2004-04-30",
            "resultCode1": {
              "chargeStatus": "A",
              "code": "4506",
              "description": "Adjournment",
              "dispositionCode": "I",
              "conviction": false
            },
            "mostSeriousFlag": false
          },
          {
            "eventId": 103580608,
            "offenderCharge": {
              "id": 1453027,
              "offence": {
                "offenceCode": "SX03015-016N",
                "statuteCode": "ZZ",
                "description": "ASSAULT OF A CHILD U13 BY PENETRATION (SOA 2003 s6"
              },
              "offenceDate": "2005-10-17",
              "chargeStatus": {
                "code": "A",
                "description": "Active"
              },
              "resultCode1": {
                "chargeStatus": "A",
                "code": "1501",
                "description": "Recall to Prison",
                "dispositionCode": "F",
                "conviction": true
              },
              "mostSeriousFlag": false,
              "lidsOffenceNumber": 12
            },
            "offenceDate": "2005-10-17",
            "resultCode1": {
              "chargeStatus": "A",
              "code": "4506",
              "description": "Adjournment",
              "dispositionCode": "I",
              "conviction": false
            },
            "mostSeriousFlag": false
          },
          {
            "eventId": 103580608,
            "offenderCharge": {
              "id": 1453028,
              "offence": {
                "offenceCode": "SX03015-016N",
                "statuteCode": "ZZ",
                "description": "ASSAULT OF A CHILD U13 BY PENETRATION (SOA 2003 s6"
              },
              "offenceDate": "2005-10-17",
              "chargeStatus": {
                "code": "A",
                "description": "Active"
              },
              "resultCode1": {
                "chargeStatus": "A",
                "code": "1501",
                "description": "Recall to Prison",
                "dispositionCode": "F",
                "conviction": true
              },
              "mostSeriousFlag": false,
              "lidsOffenceNumber": 13
            },
            "offenceDate": "2005-10-17",
            "resultCode1": {
              "chargeStatus": "A",
              "code": "4506",
              "description": "Adjournment",
              "dispositionCode": "I",
              "conviction": false
            },
            "mostSeriousFlag": false
          },
          {
            "eventId": 103580608,
            "offenderCharge": {
              "id": 1453029,
              "offence": {
                "offenceCode": "SX56070-072N",
                "statuteCode": "ZZ",
                "description": "RAPE ON CHILD (SOA 1956 s69)"
              },
              "offenceDate": "2002-10-17",
              "chargeStatus": {
                "code": "A",
                "description": "Active"
              },
              "resultCode1": {
                "chargeStatus": "A",
                "code": "1501",
                "description": "Recall to Prison",
                "dispositionCode": "F",
                "conviction": true
              },
              "mostSeriousFlag": false,
              "lidsOffenceNumber": 2
            },
            "offenceDate": "2002-10-17",
            "resultCode1": {
              "chargeStatus": "A",
              "code": "4506",
              "description": "Adjournment",
              "dispositionCode": "I",
              "conviction": false
            },
            "mostSeriousFlag": false
          },
          {
            "eventId": 103580608,
            "offenderCharge": {
              "id": 1453030,
              "offence": {
                "offenceCode": "PK78001-008NA",
                "statuteCode": "ZZ",
                "description": "INDECENT PHOTOGRAPHS OF CHILDREN"
              },
              "offenceDate": "2011-05-01",
              "chargeStatus": {
                "code": "A",
                "description": "Active"
              },
              "resultCode1": {
                "chargeStatus": "A",
                "code": "1501",
                "description": "Recall to Prison",
                "dispositionCode": "F",
                "conviction": true
              },
              "mostSeriousFlag": false,
              "lidsOffenceNumber": 22
            },
            "offenceDate": "2011-05-01",
            "resultCode1": {
              "chargeStatus": "A",
              "code": "4506",
              "description": "Adjournment",
              "dispositionCode": "I",
              "conviction": false
            },
            "mostSeriousFlag": false
          },
          {
            "eventId": 103580608,
            "offenderCharge": {
              "id": 1453031,
              "offence": {
                "offenceCode": "PK78001-008NA",
                "statuteCode": "ZZ",
                "description": "INDECENT PHOTOGRAPHS OF CHILDREN"
              },
              "offenceDate": "2010-10-31",
              "chargeStatus": {
                "code": "A",
                "description": "Active"
              },
              "resultCode1": {
                "chargeStatus": "A",
                "code": "1501",
                "description": "Recall to Prison",
                "dispositionCode": "F",
                "conviction": true
              },
              "mostSeriousFlag": false,
              "lidsOffenceNumber": 20
            },
            "offenceDate": "2010-10-31",
            "resultCode1": {
              "chargeStatus": "A",
              "code": "4506",
              "description": "Adjournment",
              "dispositionCode": "I",
              "conviction": false
            },
            "mostSeriousFlag": false
          },
          {
            "eventId": 103580608,
            "offenderCharge": {
              "id": 1453032,
              "offence": {
                "offenceCode": "PK78001-008NA",
                "statuteCode": "ZZ",
                "description": "INDECENT PHOTOGRAPHS OF CHILDREN"
              },
              "offenceDate": "2010-03-27",
              "chargeStatus": {
                "code": "A",
                "description": "Active"
              },
              "resultCode1": {
                "chargeStatus": "A",
                "code": "1501",
                "description": "Recall to Prison",
                "dispositionCode": "F",
                "conviction": true
              },
              "mostSeriousFlag": false,
              "lidsOffenceNumber": 16
            },
            "offenceDate": "2010-03-27",
            "resultCode1": {
              "chargeStatus": "A",
              "code": "4506",
              "description": "Adjournment",
              "dispositionCode": "I",
              "conviction": false
            },
            "mostSeriousFlag": false
          },
          {
            "eventId": 103580608,
            "offenderCharge": {
              "id": 1453033,
              "offence": {
                "offenceCode": "PK78001-008NA",
                "statuteCode": "ZZ",
                "description": "INDECENT PHOTOGRAPHS OF CHILDREN"
              },
              "offenceDate": "2010-10-31",
              "chargeStatus": {
                "code": "A",
                "description": "Active"
              },
              "resultCode1": {
                "chargeStatus": "A",
                "code": "1501",
                "description": "Recall to Prison",
                "dispositionCode": "F",
                "conviction": true
              },
              "mostSeriousFlag": false,
              "lidsOffenceNumber": 19
            },
            "offenceDate": "2010-10-31",
            "resultCode1": {
              "chargeStatus": "A",
              "code": "4506",
              "description": "Adjournment",
              "dispositionCode": "I",
              "conviction": false
            },
            "mostSeriousFlag": false
          },
          {
            "eventId": 103580608,
            "offenderCharge": {
              "id": 1453034,
              "offence": {
                "offenceCode": "PK78001-008NA",
                "statuteCode": "ZZ",
                "description": "INDECENT PHOTOGRAPHS OF CHILDREN"
              },
              "offenceDate": "2010-05-28",
              "chargeStatus": {
                "code": "A",
                "description": "Active"
              },
              "resultCode1": {
                "chargeStatus": "A",
                "code": "1501",
                "description": "Recall to Prison",
                "dispositionCode": "F",
                "conviction": true
              },
              "mostSeriousFlag": false,
              "lidsOffenceNumber": 26
            },
            "offenceDate": "2010-03-28",
            "resultCode1": {
              "chargeStatus": "A",
              "code": "4506",
              "description": "Adjournment",
              "dispositionCode": "I",
              "conviction": false
            },
            "mostSeriousFlag": false
          },
          {
            "eventId": 103580608,
            "offenderCharge": {
              "id": 1453035,
              "offence": {
                "offenceCode": "PK78001-008NA",
                "statuteCode": "ZZ",
                "description": "INDECENT PHOTOGRAPHS OF CHILDREN"
              },
              "offenceDate": "2010-10-31",
              "chargeStatus": {
                "code": "A",
                "description": "Active"
              },
              "resultCode1": {
                "chargeStatus": "A",
                "code": "1501",
                "description": "Recall to Prison",
                "dispositionCode": "F",
                "conviction": true
              },
              "mostSeriousFlag": false,
              "lidsOffenceNumber": 18
            },
            "offenceDate": "2010-10-31",
            "resultCode1": {
              "chargeStatus": "A",
              "code": "4506",
              "description": "Adjournment",
              "dispositionCode": "I",
              "conviction": false
            },
            "mostSeriousFlag": false
          },
          {
            "eventId": 103580608,
            "offenderCharge": {
              "id": 1453036,
              "offence": {
                "offenceCode": "SX56070-072N",
                "statuteCode": "ZZ",
                "description": "RAPE ON CHILD (SOA 1956 s69)"
              },
              "offenceDate": "2003-10-17",
              "chargeStatus": {
                "code": "A",
                "description": "Active"
              },
              "resultCode1": {
                "chargeStatus": "A",
                "code": "1501",
                "description": "Recall to Prison",
                "dispositionCode": "F",
                "conviction": true
              },
              "mostSeriousFlag": false,
              "lidsOffenceNumber": 3
            },
            "offenceDate": "2003-10-17",
            "resultCode1": {
              "chargeStatus": "A",
              "code": "4506",
              "description": "Adjournment",
              "dispositionCode": "I",
              "conviction": false
            },
            "mostSeriousFlag": false
          },
          {
            "eventId": 103580608,
            "offenderCharge": {
              "id": 1453037,
              "offence": {
                "offenceCode": "SX56025-029N",
                "statuteCode": "ZZ",
                "description": "INDECENT ASSAULT ON A CHILD"
              },
              "offenceDate": "2001-10-17",
              "chargeStatus": {
                "code": "A",
                "description": "Active"
              },
              "resultCode1": {
                "chargeStatus": "A",
                "code": "1501",
                "description": "Recall to Prison",
                "dispositionCode": "F",
                "conviction": true
              },
              "mostSeriousFlag": false,
              "lidsOffenceNumber": 8
            },
            "offenceDate": "2001-10-17",
            "resultCode1": {
              "chargeStatus": "A",
              "code": "4506",
              "description": "Adjournment",
              "dispositionCode": "I",
              "conviction": false
            },
            "mostSeriousFlag": false
          },
          {
            "eventId": 103580608,
            "offenderCharge": {
              "id": 1453038,
              "offence": {
                "offenceCode": "PK78001-008NA",
                "statuteCode": "ZZ",
                "description": "INDECENT PHOTOGRAPHS OF CHILDREN"
              },
              "offenceDate": "2011-06-22",
              "chargeStatus": {
                "code": "A",
                "description": "Active"
              },
              "resultCode1": {
                "chargeStatus": "A",
                "code": "1501",
                "description": "Recall to Prison",
                "dispositionCode": "F",
                "conviction": true
              },
              "mostSeriousFlag": false,
              "lidsOffenceNumber": 28
            },
            "offenceDate": "2011-06-22",
            "resultCode1": {
              "chargeStatus": "A",
              "code": "4506",
              "description": "Adjournment",
              "dispositionCode": "I",
              "conviction": false
            },
            "mostSeriousFlag": false
          },
          {
            "eventId": 103580608,
            "offenderCharge": {
              "id": 1453039,
              "offence": {
                "offenceCode": "SX56025-029N",
                "statuteCode": "ZZ",
                "description": "INDECENT ASSAULT ON A CHILD"
              },
              "offenceDate": "2002-10-17",
              "chargeStatus": {
                "code": "A",
                "description": "Active"
              },
              "resultCode1": {
                "chargeStatus": "A",
                "code": "1501",
                "description": "Recall to Prison",
                "dispositionCode": "F",
                "conviction": true
              },
              "mostSeriousFlag": false,
              "lidsOffenceNumber": 9
            },
            "offenceDate": "2002-10-17",
            "resultCode1": {
              "chargeStatus": "A",
              "code": "4506",
              "description": "Adjournment",
              "dispositionCode": "I",
              "conviction": false
            },
            "mostSeriousFlag": false
          }
        ],
        "courtOrders": []
      },
      {
        "id": 103580609,
        "caseId": 660258,
        "offenderNo": "A7163CK",
        "eventDateTime": "2011-08-03T10:00:00",
        "courtEventType": {
          "code": "CA",
          "description": "Court Appearance"
        },
        "eventStatus": {
          "code": "SCH",
          "description": "Scheduled (Approved)"
        },
        "courtId": "PBORMC",
        "commentText": "REMANDED. ",
        "holdFlag": false,
        "nextEventRequestFlag": false,
        "createdDateTime": "2012-02-05T05:04:26.504523",
        "createdByUsername": "XTAG",
        "courtEventCharges": [
          {
            "eventId": 103580609,
            "offenderCharge": {
              "id": 1453012,
              "offence": {
                "offenceCode": "PK78001-008NA",
                "statuteCode": "ZZ",
                "description": "INDECENT PHOTOGRAPHS OF CHILDREN"
              },
              "offenceDate": "2010-10-31",
              "chargeStatus": {
                "code": "A",
                "description": "Active"
              },
              "resultCode1": {
                "chargeStatus": "A",
                "code": "1501",
                "description": "Recall to Prison",
                "dispositionCode": "F",
                "conviction": true
              },
              "mostSeriousFlag": false,
              "lidsOffenceNumber": 23
            },
            "offenceDate": "2010-10-31",
            "resultCode1": {
              "chargeStatus": "A",
              "code": "4506",
              "description": "Adjournment",
              "dispositionCode": "I",
              "conviction": false
            },
            "mostSeriousFlag": false
          },
          {
            "eventId": 103580609,
            "offenderCharge": {
              "id": 1453013,
              "offence": {
                "offenceCode": "SX03013-014N",
                "statuteCode": "ZZ",
                "description": "RAPE OF A CHILD U13 (SOA 2003 s5)"
              },
              "offenceDate": "2006-10-17",
              "chargeStatus": {
                "code": "A",
                "description": "Active"
              },
              "resultCode1": {
                "chargeStatus": "A",
                "code": "1501",
                "description": "Recall to Prison",
                "dispositionCode": "F",
                "conviction": true
              },
              "mostSeriousFlag": false,
              "lidsOffenceNumber": 7
            },
            "offenceDate": "2006-10-17",
            "resultCode1": {
              "chargeStatus": "A",
              "code": "4506",
              "description": "Adjournment",
              "dispositionCode": "I",
              "conviction": false
            },
            "mostSeriousFlag": false
          },
          {
            "eventId": 103580609,
            "offenderCharge": {
              "id": 1453014,
              "offence": {
                "offenceCode": "SX56070-072N",
                "statuteCode": "ZZ",
                "description": "RAPE ON CHILD (SOA 1956 s69)"
              },
              "offenceDate": "2001-10-17",
              "chargeStatus": {
                "code": "A",
                "description": "Active"
              },
              "resultCode1": {
                "chargeStatus": "A",
                "code": "1501",
                "description": "Recall to Prison",
                "dispositionCode": "F",
                "conviction": true
              },
              "mostSeriousFlag": false,
              "lidsOffenceNumber": 1
            },
            "offenceDate": "2001-10-17",
            "resultCode1": {
              "chargeStatus": "A",
              "code": "4506",
              "description": "Adjournment",
              "dispositionCode": "I",
              "conviction": false
            },
            "mostSeriousFlag": false
          },
          {
            "eventId": 103580609,
            "offenderCharge": {
              "id": 1453015,
              "offence": {
                "offenceCode": "SX56070-072N",
                "statuteCode": "ZZ",
                "description": "RAPE ON CHILD (SOA 1956 s69)"
              },
              "offenceDate": "2004-04-30",
              "chargeStatus": {
                "code": "A",
                "description": "Active"
              },
              "resultCode1": {
                "chargeStatus": "A",
                "code": "1501",
                "description": "Recall to Prison",
                "dispositionCode": "F",
                "conviction": true
              },
              "mostSeriousFlag": false,
              "lidsOffenceNumber": 4
            },
            "offenceDate": "2004-04-30",
            "resultCode1": {
              "chargeStatus": "A",
              "code": "4506",
              "description": "Adjournment",
              "dispositionCode": "I",
              "conviction": false
            },
            "mostSeriousFlag": false
          },
          {
            "eventId": 103580609,
            "offenderCharge": {
              "id": 1453016,
              "offence": {
                "offenceCode": "PK78001-008NA",
                "statuteCode": "ZZ",
                "description": "INDECENT PHOTOGRAPHS OF CHILDREN"
              },
              "offenceDate": "2010-03-28",
              "chargeStatus": {
                "code": "A",
                "description": "Active"
              },
              "resultCode1": {
                "chargeStatus": "A",
                "code": "1501",
                "description": "Recall to Prison",
                "dispositionCode": "F",
                "conviction": true
              },
              "mostSeriousFlag": false,
              "lidsOffenceNumber": 15
            },
            "offenceDate": "2010-03-28",
            "resultCode1": {
              "chargeStatus": "A",
              "code": "4506",
              "description": "Adjournment",
              "dispositionCode": "I",
              "conviction": false
            },
            "mostSeriousFlag": false
          },
          {
            "eventId": 103580609,
            "offenderCharge": {
              "id": 1453017,
              "offence": {
                "offenceCode": "PK78001-008NA",
                "statuteCode": "ZZ",
                "description": "INDECENT PHOTOGRAPHS OF CHILDREN"
              },
              "offenceDate": "2011-06-30",
              "chargeStatus": {
                "code": "A",
                "description": "Active"
              },
              "resultCode1": {
                "chargeStatus": "A",
                "code": "1501",
                "description": "Recall to Prison",
                "dispositionCode": "F",
                "conviction": true
              },
              "mostSeriousFlag": false,
              "lidsOffenceNumber": 27
            },
            "offenceDate": "2011-06-30",
            "resultCode1": {
              "chargeStatus": "A",
              "code": "4506",
              "description": "Adjournment",
              "dispositionCode": "I",
              "conviction": false
            },
            "mostSeriousFlag": false
          },
          {
            "eventId": 103580609,
            "offenderCharge": {
              "id": 1453018,
              "offence": {
                "offenceCode": "SX03013-014N",
                "statuteCode": "ZZ",
                "description": "RAPE OF A CHILD U13 (SOA 2003 s5)"
              },
              "offenceDate": "2005-10-17",
              "chargeStatus": {
                "code": "A",
                "description": "Active"
              },
              "resultCode1": {
                "chargeStatus": "A",
                "code": "1501",
                "description": "Recall to Prison",
                "dispositionCode": "F",
                "conviction": true
              },
              "mostSeriousFlag": false,
              "lidsOffenceNumber": 6
            },
            "offenceDate": "2005-10-17",
            "resultCode1": {
              "chargeStatus": "A",
              "code": "4506",
              "description": "Adjournment",
              "dispositionCode": "I",
              "conviction": false
            },
            "mostSeriousFlag": false
          },
          {
            "eventId": 103580609,
            "offenderCharge": {
              "id": 1453019,
              "offence": {
                "offenceCode": "PK78001-008NA",
                "statuteCode": "ZZ",
                "description": "INDECENT PHOTOGRAPHS OF CHILDREN"
              },
              "offenceDate": "2011-03-27",
              "chargeStatus": {
                "code": "A",
                "description": "Active"
              },
              "resultCode1": {
                "chargeStatus": "A",
                "code": "1501",
                "description": "Recall to Prison",
                "dispositionCode": "F",
                "conviction": true
              },
              "mostSeriousFlag": false,
              "lidsOffenceNumber": 24
            },
            "offenceDate": "2011-03-27",
            "resultCode1": {
              "chargeStatus": "A",
              "code": "4506",
              "description": "Adjournment",
              "dispositionCode": "I",
              "conviction": false
            },
            "mostSeriousFlag": false
          },
          {
            "eventId": 103580609,
            "offenderCharge": {
              "id": 1453020,
              "offence": {
                "offenceCode": "SX56025-029N",
                "statuteCode": "ZZ",
                "description": "INDECENT ASSAULT ON A CHILD"
              },
              "offenceDate": "2003-10-17",
              "chargeStatus": {
                "code": "A",
                "description": "Active"
              },
              "resultCode1": {
                "chargeStatus": "A",
                "code": "1501",
                "description": "Recall to Prison",
                "dispositionCode": "F",
                "conviction": true
              },
              "mostSeriousFlag": false,
              "lidsOffenceNumber": 10
            },
            "offenceDate": "2003-10-17",
            "resultCode1": {
              "chargeStatus": "A",
              "code": "4506",
              "description": "Adjournment",
              "dispositionCode": "I",
              "conviction": false
            },
            "mostSeriousFlag": false
          },
          {
            "eventId": 103580609,
            "offenderCharge": {
              "id": 1453021,
              "offence": {
                "offenceCode": "PK78001-008NA",
                "statuteCode": "ZZ",
                "description": "INDECENT PHOTOGRAPHS OF CHILDREN"
              },
              "offenceDate": "2011-05-01",
              "chargeStatus": {
                "code": "A",
                "description": "Active"
              },
              "resultCode1": {
                "chargeStatus": "A",
                "code": "1501",
                "description": "Recall to Prison",
                "dispositionCode": "F",
                "conviction": true
              },
              "mostSeriousFlag": false,
              "lidsOffenceNumber": 21
            },
            "offenceDate": "2011-05-01",
            "resultCode1": {
              "chargeStatus": "A",
              "code": "4506",
              "description": "Adjournment",
              "dispositionCode": "I",
              "conviction": false
            },
            "mostSeriousFlag": false
          },
          {
            "eventId": 103580609,
            "offenderCharge": {
              "id": 1453022,
              "offence": {
                "offenceCode": "SX03015-016N",
                "statuteCode": "ZZ",
                "description": "ASSAULT OF A CHILD U13 BY PENETRATION (SOA 2003 s6"
              },
              "offenceDate": "2006-10-17",
              "chargeStatus": {
                "code": "A",
                "description": "Active"
              },
              "resultCode1": {
                "chargeStatus": "A",
                "code": "1501",
                "description": "Recall to Prison",
                "dispositionCode": "F",
                "conviction": true
              },
              "mostSeriousFlag": false,
              "lidsOffenceNumber": 14
            },
            "offenceDate": "2006-10-17",
            "resultCode1": {
              "chargeStatus": "A",
              "code": "4506",
              "description": "Adjournment",
              "dispositionCode": "I",
              "conviction": false
            },
            "mostSeriousFlag": false
          },
          {
            "eventId": 103580609,
            "offenderCharge": {
              "id": 1453023,
              "offence": {
                "offenceCode": "PK78001-008NA",
                "statuteCode": "ZZ",
                "description": "INDECENT PHOTOGRAPHS OF CHILDREN"
              },
              "offenceDate": "2010-10-31",
              "chargeStatus": {
                "code": "A",
                "description": "Active"
              },
              "resultCode1": {
                "chargeStatus": "A",
                "code": "1501",
                "description": "Recall to Prison",
                "dispositionCode": "F",
                "conviction": true
              },
              "mostSeriousFlag": false,
              "lidsOffenceNumber": 17
            },
            "offenceDate": "2010-10-31",
            "resultCode1": {
              "chargeStatus": "A",
              "code": "4506",
              "description": "Adjournment",
              "dispositionCode": "I",
              "conviction": false
            },
            "mostSeriousFlag": false
          },
          {
            "eventId": 103580609,
            "offenderCharge": {
              "id": 1453024,
              "offence": {
                "offenceCode": "PK78001-008NA",
                "statuteCode": "ZZ",
                "description": "INDECENT PHOTOGRAPHS OF CHILDREN"
              },
              "offenceDate": "2011-05-23",
              "chargeStatus": {
                "code": "A",
                "description": "Active"
              },
              "resultCode1": {
                "chargeStatus": "A",
                "code": "1501",
                "description": "Recall to Prison",
                "dispositionCode": "F",
                "conviction": true
              },
              "mostSeriousFlag": false,
              "lidsOffenceNumber": 25
            },
            "offenceDate": "2011-05-23",
            "resultCode1": {
              "chargeStatus": "A",
              "code": "4506",
              "description": "Adjournment",
              "dispositionCode": "I",
              "conviction": false
            },
            "mostSeriousFlag": false
          },
          {
            "eventId": 103580609,
            "offenderCharge": {
              "id": 1453025,
              "offence": {
                "offenceCode": "SX03013-014N",
                "statuteCode": "ZZ",
                "description": "RAPE OF A CHILD U13 (SOA 2003 s5)"
              },
              "offenceDate": "2004-10-17",
              "chargeStatus": {
                "code": "A",
                "description": "Active"
              },
              "resultCode1": {
                "chargeStatus": "A",
                "code": "1501",
                "description": "Recall to Prison",
                "dispositionCode": "F",
                "conviction": true
              },
              "mostSeriousFlag": false,
              "lidsOffenceNumber": 5
            },
            "offenceDate": "2004-10-17",
            "resultCode1": {
              "chargeStatus": "A",
              "code": "4506",
              "description": "Adjournment",
              "dispositionCode": "I",
              "conviction": false
            },
            "mostSeriousFlag": false
          },
          {
            "eventId": 103580609,
            "offenderCharge": {
              "id": 1453026,
              "offence": {
                "offenceCode": "SX56025-029N",
                "statuteCode": "ZZ",
                "description": "INDECENT ASSAULT ON A CHILD"
              },
              "offenceDate": "2004-04-30",
              "chargeStatus": {
                "code": "A",
                "description": "Active"
              },
              "resultCode1": {
                "chargeStatus": "A",
                "code": "1501",
                "description": "Recall to Prison",
                "dispositionCode": "F",
                "conviction": true
              },
              "mostSeriousFlag": false,
              "lidsOffenceNumber": 11
            },
            "offenceDate": "2004-04-30",
            "resultCode1": {
              "chargeStatus": "A",
              "code": "4506",
              "description": "Adjournment",
              "dispositionCode": "I",
              "conviction": false
            },
            "mostSeriousFlag": false
          },
          {
            "eventId": 103580609,
            "offenderCharge": {
              "id": 1453027,
              "offence": {
                "offenceCode": "SX03015-016N",
                "statuteCode": "ZZ",
                "description": "ASSAULT OF A CHILD U13 BY PENETRATION (SOA 2003 s6"
              },
              "offenceDate": "2005-10-17",
              "chargeStatus": {
                "code": "A",
                "description": "Active"
              },
              "resultCode1": {
                "chargeStatus": "A",
                "code": "1501",
                "description": "Recall to Prison",
                "dispositionCode": "F",
                "conviction": true
              },
              "mostSeriousFlag": false,
              "lidsOffenceNumber": 12
            },
            "offenceDate": "2005-10-17",
            "resultCode1": {
              "chargeStatus": "A",
              "code": "4506",
              "description": "Adjournment",
              "dispositionCode": "I",
              "conviction": false
            },
            "mostSeriousFlag": false
          },
          {
            "eventId": 103580609,
            "offenderCharge": {
              "id": 1453028,
              "offence": {
                "offenceCode": "SX03015-016N",
                "statuteCode": "ZZ",
                "description": "ASSAULT OF A CHILD U13 BY PENETRATION (SOA 2003 s6"
              },
              "offenceDate": "2005-10-17",
              "chargeStatus": {
                "code": "A",
                "description": "Active"
              },
              "resultCode1": {
                "chargeStatus": "A",
                "code": "1501",
                "description": "Recall to Prison",
                "dispositionCode": "F",
                "conviction": true
              },
              "mostSeriousFlag": false,
              "lidsOffenceNumber": 13
            },
            "offenceDate": "2005-10-17",
            "resultCode1": {
              "chargeStatus": "A",
              "code": "4506",
              "description": "Adjournment",
              "dispositionCode": "I",
              "conviction": false
            },
            "mostSeriousFlag": false
          },
          {
            "eventId": 103580609,
            "offenderCharge": {
              "id": 1453029,
              "offence": {
                "offenceCode": "SX56070-072N",
                "statuteCode": "ZZ",
                "description": "RAPE ON CHILD (SOA 1956 s69)"
              },
              "offenceDate": "2002-10-17",
              "chargeStatus": {
                "code": "A",
                "description": "Active"
              },
              "resultCode1": {
                "chargeStatus": "A",
                "code": "1501",
                "description": "Recall to Prison",
                "dispositionCode": "F",
                "conviction": true
              },
              "mostSeriousFlag": false,
              "lidsOffenceNumber": 2
            },
            "offenceDate": "2002-10-17",
            "resultCode1": {
              "chargeStatus": "A",
              "code": "4506",
              "description": "Adjournment",
              "dispositionCode": "I",
              "conviction": false
            },
            "mostSeriousFlag": false
          },
          {
            "eventId": 103580609,
            "offenderCharge": {
              "id": 1453030,
              "offence": {
                "offenceCode": "PK78001-008NA",
                "statuteCode": "ZZ",
                "description": "INDECENT PHOTOGRAPHS OF CHILDREN"
              },
              "offenceDate": "2011-05-01",
              "chargeStatus": {
                "code": "A",
                "description": "Active"
              },
              "resultCode1": {
                "chargeStatus": "A",
                "code": "1501",
                "description": "Recall to Prison",
                "dispositionCode": "F",
                "conviction": true
              },
              "mostSeriousFlag": false,
              "lidsOffenceNumber": 22
            },
            "offenceDate": "2011-05-01",
            "resultCode1": {
              "chargeStatus": "A",
              "code": "4506",
              "description": "Adjournment",
              "dispositionCode": "I",
              "conviction": false
            },
            "mostSeriousFlag": false
          },
          {
            "eventId": 103580609,
            "offenderCharge": {
              "id": 1453031,
              "offence": {
                "offenceCode": "PK78001-008NA",
                "statuteCode": "ZZ",
                "description": "INDECENT PHOTOGRAPHS OF CHILDREN"
              },
              "offenceDate": "2010-10-31",
              "chargeStatus": {
                "code": "A",
                "description": "Active"
              },
              "resultCode1": {
                "chargeStatus": "A",
                "code": "1501",
                "description": "Recall to Prison",
                "dispositionCode": "F",
                "conviction": true
              },
              "mostSeriousFlag": false,
              "lidsOffenceNumber": 20
            },
            "offenceDate": "2010-10-31",
            "resultCode1": {
              "chargeStatus": "A",
              "code": "4506",
              "description": "Adjournment",
              "dispositionCode": "I",
              "conviction": false
            },
            "mostSeriousFlag": false
          },
          {
            "eventId": 103580609,
            "offenderCharge": {
              "id": 1453032,
              "offence": {
                "offenceCode": "PK78001-008NA",
                "statuteCode": "ZZ",
                "description": "INDECENT PHOTOGRAPHS OF CHILDREN"
              },
              "offenceDate": "2010-03-27",
              "chargeStatus": {
                "code": "A",
                "description": "Active"
              },
              "resultCode1": {
                "chargeStatus": "A",
                "code": "1501",
                "description": "Recall to Prison",
                "dispositionCode": "F",
                "conviction": true
              },
              "mostSeriousFlag": false,
              "lidsOffenceNumber": 16
            },
            "offenceDate": "2010-03-27",
            "resultCode1": {
              "chargeStatus": "A",
              "code": "4506",
              "description": "Adjournment",
              "dispositionCode": "I",
              "conviction": false
            },
            "mostSeriousFlag": false
          },
          {
            "eventId": 103580609,
            "offenderCharge": {
              "id": 1453033,
              "offence": {
                "offenceCode": "PK78001-008NA",
                "statuteCode": "ZZ",
                "description": "INDECENT PHOTOGRAPHS OF CHILDREN"
              },
              "offenceDate": "2010-10-31",
              "chargeStatus": {
                "code": "A",
                "description": "Active"
              },
              "resultCode1": {
                "chargeStatus": "A",
                "code": "1501",
                "description": "Recall to Prison",
                "dispositionCode": "F",
                "conviction": true
              },
              "mostSeriousFlag": false,
              "lidsOffenceNumber": 19
            },
            "offenceDate": "2010-10-31",
            "resultCode1": {
              "chargeStatus": "A",
              "code": "4506",
              "description": "Adjournment",
              "dispositionCode": "I",
              "conviction": false
            },
            "mostSeriousFlag": false
          },
          {
            "eventId": 103580609,
            "offenderCharge": {
              "id": 1453034,
              "offence": {
                "offenceCode": "PK78001-008NA",
                "statuteCode": "ZZ",
                "description": "INDECENT PHOTOGRAPHS OF CHILDREN"
              },
              "offenceDate": "2010-05-28",
              "chargeStatus": {
                "code": "A",
                "description": "Active"
              },
              "resultCode1": {
                "chargeStatus": "A",
                "code": "1501",
                "description": "Recall to Prison",
                "dispositionCode": "F",
                "conviction": true
              },
              "mostSeriousFlag": false,
              "lidsOffenceNumber": 26
            },
            "offenceDate": "2010-03-28",
            "resultCode1": {
              "chargeStatus": "A",
              "code": "4506",
              "description": "Adjournment",
              "dispositionCode": "I",
              "conviction": false
            },
            "mostSeriousFlag": false
          },
          {
            "eventId": 103580609,
            "offenderCharge": {
              "id": 1453035,
              "offence": {
                "offenceCode": "PK78001-008NA",
                "statuteCode": "ZZ",
                "description": "INDECENT PHOTOGRAPHS OF CHILDREN"
              },
              "offenceDate": "2010-10-31",
              "chargeStatus": {
                "code": "A",
                "description": "Active"
              },
              "resultCode1": {
                "chargeStatus": "A",
                "code": "1501",
                "description": "Recall to Prison",
                "dispositionCode": "F",
                "conviction": true
              },
              "mostSeriousFlag": false,
              "lidsOffenceNumber": 18
            },
            "offenceDate": "2010-10-31",
            "resultCode1": {
              "chargeStatus": "A",
              "code": "4506",
              "description": "Adjournment",
              "dispositionCode": "I",
              "conviction": false
            },
            "mostSeriousFlag": false
          },
          {
            "eventId": 103580609,
            "offenderCharge": {
              "id": 1453036,
              "offence": {
                "offenceCode": "SX56070-072N",
                "statuteCode": "ZZ",
                "description": "RAPE ON CHILD (SOA 1956 s69)"
              },
              "offenceDate": "2003-10-17",
              "chargeStatus": {
                "code": "A",
                "description": "Active"
              },
              "resultCode1": {
                "chargeStatus": "A",
                "code": "1501",
                "description": "Recall to Prison",
                "dispositionCode": "F",
                "conviction": true
              },
              "mostSeriousFlag": false,
              "lidsOffenceNumber": 3
            },
            "offenceDate": "2003-10-17",
            "resultCode1": {
              "chargeStatus": "A",
              "code": "4506",
              "description": "Adjournment",
              "dispositionCode": "I",
              "conviction": false
            },
            "mostSeriousFlag": false
          },
          {
            "eventId": 103580609,
            "offenderCharge": {
              "id": 1453037,
              "offence": {
                "offenceCode": "SX56025-029N",
                "statuteCode": "ZZ",
                "description": "INDECENT ASSAULT ON A CHILD"
              },
              "offenceDate": "2001-10-17",
              "chargeStatus": {
                "code": "A",
                "description": "Active"
              },
              "resultCode1": {
                "chargeStatus": "A",
                "code": "1501",
                "description": "Recall to Prison",
                "dispositionCode": "F",
                "conviction": true
              },
              "mostSeriousFlag": false,
              "lidsOffenceNumber": 8
            },
            "offenceDate": "2001-10-17",
            "resultCode1": {
              "chargeStatus": "A",
              "code": "4506",
              "description": "Adjournment",
              "dispositionCode": "I",
              "conviction": false
            },
            "mostSeriousFlag": false
          },
          {
            "eventId": 103580609,
            "offenderCharge": {
              "id": 1453038,
              "offence": {
                "offenceCode": "PK78001-008NA",
                "statuteCode": "ZZ",
                "description": "INDECENT PHOTOGRAPHS OF CHILDREN"
              },
              "offenceDate": "2011-06-22",
              "chargeStatus": {
                "code": "A",
                "description": "Active"
              },
              "resultCode1": {
                "chargeStatus": "A",
                "code": "1501",
                "description": "Recall to Prison",
                "dispositionCode": "F",
                "conviction": true
              },
              "mostSeriousFlag": false,
              "lidsOffenceNumber": 28
            },
            "offenceDate": "2011-06-22",
            "resultCode1": {
              "chargeStatus": "A",
              "code": "4506",
              "description": "Adjournment",
              "dispositionCode": "I",
              "conviction": false
            },
            "mostSeriousFlag": false
          },
          {
            "eventId": 103580609,
            "offenderCharge": {
              "id": 1453039,
              "offence": {
                "offenceCode": "SX56025-029N",
                "statuteCode": "ZZ",
                "description": "INDECENT ASSAULT ON A CHILD"
              },
              "offenceDate": "2002-10-17",
              "chargeStatus": {
                "code": "A",
                "description": "Active"
              },
              "resultCode1": {
                "chargeStatus": "A",
                "code": "1501",
                "description": "Recall to Prison",
                "dispositionCode": "F",
                "conviction": true
              },
              "mostSeriousFlag": false,
              "lidsOffenceNumber": 9
            },
            "offenceDate": "2002-10-17",
            "resultCode1": {
              "chargeStatus": "A",
              "code": "4506",
              "description": "Adjournment",
              "dispositionCode": "I",
              "conviction": false
            },
            "mostSeriousFlag": false
          }
        ],
        "courtOrders": []
      },
      {
        "id": 668964883,
        "caseId": 660258,
        "offenderNo": "A7163CK",
        "eventDateTime": "2024-10-31T10:00:00",
        "courtEventType": {
          "code": "BREACH",
          "description": "Breach hearing"
        },
        "eventStatus": {
          "code": "COMP",
          "description": "Completed"
        },
        "directionCode": {
          "code": "OUT",
          "description": "Out"
        },
        "courtId": "COACD",
        "outcomeReasonCode": {
          "chargeStatus": "A",
          "code": "1501",
          "description": "Recall to Prison",
          "dispositionCode": "F",
          "conviction": true
        },
        "orderRequestedFlag": false,
        "holdFlag": false,
        "nextEventRequestFlag": false,
        "createdDateTime": "2025-01-27T08:04:36.402987",
        "createdByUsername": "EQY54N",
        "courtEventCharges": [
          {
            "eventId": 668964883,
            "offenderCharge": {
              "id": 1453012,
              "offence": {
                "offenceCode": "PK78001-008NA",
                "statuteCode": "ZZ",
                "description": "INDECENT PHOTOGRAPHS OF CHILDREN"
              },
              "offenceDate": "2010-10-31",
              "chargeStatus": {
                "code": "A",
                "description": "Active"
              },
              "resultCode1": {
                "chargeStatus": "A",
                "code": "1501",
                "description": "Recall to Prison",
                "dispositionCode": "F",
                "conviction": true
              },
              "mostSeriousFlag": false,
              "lidsOffenceNumber": 23
            },
            "offenceDate": "2010-10-31",
            "resultCode1": {
              "chargeStatus": "A",
              "code": "1501",
              "description": "Recall to Prison",
              "dispositionCode": "F",
              "conviction": true
            },
            "mostSeriousFlag": false
          },
          {
            "eventId": 668964883,
            "offenderCharge": {
              "id": 1453013,
              "offence": {
                "offenceCode": "SX03013-014N",
                "statuteCode": "ZZ",
                "description": "RAPE OF A CHILD U13 (SOA 2003 s5)"
              },
              "offenceDate": "2006-10-17",
              "chargeStatus": {
                "code": "A",
                "description": "Active"
              },
              "resultCode1": {
                "chargeStatus": "A",
                "code": "1501",
                "description": "Recall to Prison",
                "dispositionCode": "F",
                "conviction": true
              },
              "mostSeriousFlag": false,
              "lidsOffenceNumber": 7
            },
            "offenceDate": "2006-10-17",
            "resultCode1": {
              "chargeStatus": "A",
              "code": "1501",
              "description": "Recall to Prison",
              "dispositionCode": "F",
              "conviction": true
            },
            "mostSeriousFlag": false
          },
          {
            "eventId": 668964883,
            "offenderCharge": {
              "id": 1453014,
              "offence": {
                "offenceCode": "SX56070-072N",
                "statuteCode": "ZZ",
                "description": "RAPE ON CHILD (SOA 1956 s69)"
              },
              "offenceDate": "2001-10-17",
              "chargeStatus": {
                "code": "A",
                "description": "Active"
              },
              "resultCode1": {
                "chargeStatus": "A",
                "code": "1501",
                "description": "Recall to Prison",
                "dispositionCode": "F",
                "conviction": true
              },
              "mostSeriousFlag": false,
              "lidsOffenceNumber": 1
            },
            "offenceDate": "2001-10-17",
            "resultCode1": {
              "chargeStatus": "A",
              "code": "1501",
              "description": "Recall to Prison",
              "dispositionCode": "F",
              "conviction": true
            },
            "mostSeriousFlag": false
          },
          {
            "eventId": 668964883,
            "offenderCharge": {
              "id": 1453015,
              "offence": {
                "offenceCode": "SX56070-072N",
                "statuteCode": "ZZ",
                "description": "RAPE ON CHILD (SOA 1956 s69)"
              },
              "offenceDate": "2004-04-30",
              "chargeStatus": {
                "code": "A",
                "description": "Active"
              },
              "resultCode1": {
                "chargeStatus": "A",
                "code": "1501",
                "description": "Recall to Prison",
                "dispositionCode": "F",
                "conviction": true
              },
              "mostSeriousFlag": false,
              "lidsOffenceNumber": 4
            },
            "offenceDate": "2004-04-30",
            "resultCode1": {
              "chargeStatus": "A",
              "code": "1501",
              "description": "Recall to Prison",
              "dispositionCode": "F",
              "conviction": true
            },
            "mostSeriousFlag": false
          },
          {
            "eventId": 668964883,
            "offenderCharge": {
              "id": 1453016,
              "offence": {
                "offenceCode": "PK78001-008NA",
                "statuteCode": "ZZ",
                "description": "INDECENT PHOTOGRAPHS OF CHILDREN"
              },
              "offenceDate": "2010-03-28",
              "chargeStatus": {
                "code": "A",
                "description": "Active"
              },
              "resultCode1": {
                "chargeStatus": "A",
                "code": "1501",
                "description": "Recall to Prison",
                "dispositionCode": "F",
                "conviction": true
              },
              "mostSeriousFlag": false,
              "lidsOffenceNumber": 15
            },
            "offenceDate": "2010-03-28",
            "resultCode1": {
              "chargeStatus": "A",
              "code": "1501",
              "description": "Recall to Prison",
              "dispositionCode": "F",
              "conviction": true
            },
            "mostSeriousFlag": false
          },
          {
            "eventId": 668964883,
            "offenderCharge": {
              "id": 1453017,
              "offence": {
                "offenceCode": "PK78001-008NA",
                "statuteCode": "ZZ",
                "description": "INDECENT PHOTOGRAPHS OF CHILDREN"
              },
              "offenceDate": "2011-06-30",
              "chargeStatus": {
                "code": "A",
                "description": "Active"
              },
              "resultCode1": {
                "chargeStatus": "A",
                "code": "1501",
                "description": "Recall to Prison",
                "dispositionCode": "F",
                "conviction": true
              },
              "mostSeriousFlag": false,
              "lidsOffenceNumber": 27
            },
            "offenceDate": "2011-06-30",
            "resultCode1": {
              "chargeStatus": "A",
              "code": "1501",
              "description": "Recall to Prison",
              "dispositionCode": "F",
              "conviction": true
            },
            "mostSeriousFlag": false
          },
          {
            "eventId": 668964883,
            "offenderCharge": {
              "id": 1453018,
              "offence": {
                "offenceCode": "SX03013-014N",
                "statuteCode": "ZZ",
                "description": "RAPE OF A CHILD U13 (SOA 2003 s5)"
              },
              "offenceDate": "2005-10-17",
              "chargeStatus": {
                "code": "A",
                "description": "Active"
              },
              "resultCode1": {
                "chargeStatus": "A",
                "code": "1501",
                "description": "Recall to Prison",
                "dispositionCode": "F",
                "conviction": true
              },
              "mostSeriousFlag": false,
              "lidsOffenceNumber": 6
            },
            "offenceDate": "2005-10-17",
            "resultCode1": {
              "chargeStatus": "A",
              "code": "1501",
              "description": "Recall to Prison",
              "dispositionCode": "F",
              "conviction": true
            },
            "mostSeriousFlag": false
          },
          {
            "eventId": 668964883,
            "offenderCharge": {
              "id": 1453019,
              "offence": {
                "offenceCode": "PK78001-008NA",
                "statuteCode": "ZZ",
                "description": "INDECENT PHOTOGRAPHS OF CHILDREN"
              },
              "offenceDate": "2011-03-27",
              "chargeStatus": {
                "code": "A",
                "description": "Active"
              },
              "resultCode1": {
                "chargeStatus": "A",
                "code": "1501",
                "description": "Recall to Prison",
                "dispositionCode": "F",
                "conviction": true
              },
              "mostSeriousFlag": false,
              "lidsOffenceNumber": 24
            },
            "offenceDate": "2011-03-27",
            "resultCode1": {
              "chargeStatus": "A",
              "code": "1501",
              "description": "Recall to Prison",
              "dispositionCode": "F",
              "conviction": true
            },
            "mostSeriousFlag": false
          },
          {
            "eventId": 668964883,
            "offenderCharge": {
              "id": 1453020,
              "offence": {
                "offenceCode": "SX56025-029N",
                "statuteCode": "ZZ",
                "description": "INDECENT ASSAULT ON A CHILD"
              },
              "offenceDate": "2003-10-17",
              "chargeStatus": {
                "code": "A",
                "description": "Active"
              },
              "resultCode1": {
                "chargeStatus": "A",
                "code": "1501",
                "description": "Recall to Prison",
                "dispositionCode": "F",
                "conviction": true
              },
              "mostSeriousFlag": false,
              "lidsOffenceNumber": 10
            },
            "offenceDate": "2003-10-17",
            "resultCode1": {
              "chargeStatus": "A",
              "code": "1501",
              "description": "Recall to Prison",
              "dispositionCode": "F",
              "conviction": true
            },
            "mostSeriousFlag": false
          },
          {
            "eventId": 668964883,
            "offenderCharge": {
              "id": 1453021,
              "offence": {
                "offenceCode": "PK78001-008NA",
                "statuteCode": "ZZ",
                "description": "INDECENT PHOTOGRAPHS OF CHILDREN"
              },
              "offenceDate": "2011-05-01",
              "chargeStatus": {
                "code": "A",
                "description": "Active"
              },
              "resultCode1": {
                "chargeStatus": "A",
                "code": "1501",
                "description": "Recall to Prison",
                "dispositionCode": "F",
                "conviction": true
              },
              "mostSeriousFlag": false,
              "lidsOffenceNumber": 21
            },
            "offenceDate": "2011-05-01",
            "resultCode1": {
              "chargeStatus": "A",
              "code": "1501",
              "description": "Recall to Prison",
              "dispositionCode": "F",
              "conviction": true
            },
            "mostSeriousFlag": false
          },
          {
            "eventId": 668964883,
            "offenderCharge": {
              "id": 1453022,
              "offence": {
                "offenceCode": "SX03015-016N",
                "statuteCode": "ZZ",
                "description": "ASSAULT OF A CHILD U13 BY PENETRATION (SOA 2003 s6"
              },
              "offenceDate": "2006-10-17",
              "chargeStatus": {
                "code": "A",
                "description": "Active"
              },
              "resultCode1": {
                "chargeStatus": "A",
                "code": "1501",
                "description": "Recall to Prison",
                "dispositionCode": "F",
                "conviction": true
              },
              "mostSeriousFlag": false,
              "lidsOffenceNumber": 14
            },
            "offenceDate": "2006-10-17",
            "resultCode1": {
              "chargeStatus": "A",
              "code": "1501",
              "description": "Recall to Prison",
              "dispositionCode": "F",
              "conviction": true
            },
            "mostSeriousFlag": false
          },
          {
            "eventId": 668964883,
            "offenderCharge": {
              "id": 1453023,
              "offence": {
                "offenceCode": "PK78001-008NA",
                "statuteCode": "ZZ",
                "description": "INDECENT PHOTOGRAPHS OF CHILDREN"
              },
              "offenceDate": "2010-10-31",
              "chargeStatus": {
                "code": "A",
                "description": "Active"
              },
              "resultCode1": {
                "chargeStatus": "A",
                "code": "1501",
                "description": "Recall to Prison",
                "dispositionCode": "F",
                "conviction": true
              },
              "mostSeriousFlag": false,
              "lidsOffenceNumber": 17
            },
            "offenceDate": "2010-10-31",
            "resultCode1": {
              "chargeStatus": "A",
              "code": "1501",
              "description": "Recall to Prison",
              "dispositionCode": "F",
              "conviction": true
            },
            "mostSeriousFlag": false
          },
          {
            "eventId": 668964883,
            "offenderCharge": {
              "id": 1453024,
              "offence": {
                "offenceCode": "PK78001-008NA",
                "statuteCode": "ZZ",
                "description": "INDECENT PHOTOGRAPHS OF CHILDREN"
              },
              "offenceDate": "2011-05-23",
              "chargeStatus": {
                "code": "A",
                "description": "Active"
              },
              "resultCode1": {
                "chargeStatus": "A",
                "code": "1501",
                "description": "Recall to Prison",
                "dispositionCode": "F",
                "conviction": true
              },
              "mostSeriousFlag": false,
              "lidsOffenceNumber": 25
            },
            "offenceDate": "2011-05-23",
            "resultCode1": {
              "chargeStatus": "A",
              "code": "1501",
              "description": "Recall to Prison",
              "dispositionCode": "F",
              "conviction": true
            },
            "mostSeriousFlag": false
          },
          {
            "eventId": 668964883,
            "offenderCharge": {
              "id": 1453025,
              "offence": {
                "offenceCode": "SX03013-014N",
                "statuteCode": "ZZ",
                "description": "RAPE OF A CHILD U13 (SOA 2003 s5)"
              },
              "offenceDate": "2004-10-17",
              "chargeStatus": {
                "code": "A",
                "description": "Active"
              },
              "resultCode1": {
                "chargeStatus": "A",
                "code": "1501",
                "description": "Recall to Prison",
                "dispositionCode": "F",
                "conviction": true
              },
              "mostSeriousFlag": false,
              "lidsOffenceNumber": 5
            },
            "offenceDate": "2004-10-17",
            "resultCode1": {
              "chargeStatus": "A",
              "code": "1501",
              "description": "Recall to Prison",
              "dispositionCode": "F",
              "conviction": true
            },
            "mostSeriousFlag": false
          },
          {
            "eventId": 668964883,
            "offenderCharge": {
              "id": 1453026,
              "offence": {
                "offenceCode": "SX56025-029N",
                "statuteCode": "ZZ",
                "description": "INDECENT ASSAULT ON A CHILD"
              },
              "offenceDate": "2004-04-30",
              "chargeStatus": {
                "code": "A",
                "description": "Active"
              },
              "resultCode1": {
                "chargeStatus": "A",
                "code": "1501",
                "description": "Recall to Prison",
                "dispositionCode": "F",
                "conviction": true
              },
              "mostSeriousFlag": false,
              "lidsOffenceNumber": 11
            },
            "offenceDate": "2004-04-30",
            "resultCode1": {
              "chargeStatus": "A",
              "code": "1501",
              "description": "Recall to Prison",
              "dispositionCode": "F",
              "conviction": true
            },
            "mostSeriousFlag": false
          },
          {
            "eventId": 668964883,
            "offenderCharge": {
              "id": 1453027,
              "offence": {
                "offenceCode": "SX03015-016N",
                "statuteCode": "ZZ",
                "description": "ASSAULT OF A CHILD U13 BY PENETRATION (SOA 2003 s6"
              },
              "offenceDate": "2005-10-17",
              "chargeStatus": {
                "code": "A",
                "description": "Active"
              },
              "resultCode1": {
                "chargeStatus": "A",
                "code": "1501",
                "description": "Recall to Prison",
                "dispositionCode": "F",
                "conviction": true
              },
              "mostSeriousFlag": false,
              "lidsOffenceNumber": 12
            },
            "offenceDate": "2005-10-17",
            "resultCode1": {
              "chargeStatus": "A",
              "code": "1501",
              "description": "Recall to Prison",
              "dispositionCode": "F",
              "conviction": true
            },
            "mostSeriousFlag": false
          },
          {
            "eventId": 668964883,
            "offenderCharge": {
              "id": 1453028,
              "offence": {
                "offenceCode": "SX03015-016N",
                "statuteCode": "ZZ",
                "description": "ASSAULT OF A CHILD U13 BY PENETRATION (SOA 2003 s6"
              },
              "offenceDate": "2005-10-17",
              "chargeStatus": {
                "code": "A",
                "description": "Active"
              },
              "resultCode1": {
                "chargeStatus": "A",
                "code": "1501",
                "description": "Recall to Prison",
                "dispositionCode": "F",
                "conviction": true
              },
              "mostSeriousFlag": false,
              "lidsOffenceNumber": 13
            },
            "offenceDate": "2005-10-17",
            "resultCode1": {
              "chargeStatus": "A",
              "code": "1501",
              "description": "Recall to Prison",
              "dispositionCode": "F",
              "conviction": true
            },
            "mostSeriousFlag": false
          },
          {
            "eventId": 668964883,
            "offenderCharge": {
              "id": 1453029,
              "offence": {
                "offenceCode": "SX56070-072N",
                "statuteCode": "ZZ",
                "description": "RAPE ON CHILD (SOA 1956 s69)"
              },
              "offenceDate": "2002-10-17",
              "chargeStatus": {
                "code": "A",
                "description": "Active"
              },
              "resultCode1": {
                "chargeStatus": "A",
                "code": "1501",
                "description": "Recall to Prison",
                "dispositionCode": "F",
                "conviction": true
              },
              "mostSeriousFlag": false,
              "lidsOffenceNumber": 2
            },
            "offenceDate": "2002-10-17",
            "resultCode1": {
              "chargeStatus": "A",
              "code": "1501",
              "description": "Recall to Prison",
              "dispositionCode": "F",
              "conviction": true
            },
            "mostSeriousFlag": false
          },
          {
            "eventId": 668964883,
            "offenderCharge": {
              "id": 1453030,
              "offence": {
                "offenceCode": "PK78001-008NA",
                "statuteCode": "ZZ",
                "description": "INDECENT PHOTOGRAPHS OF CHILDREN"
              },
              "offenceDate": "2011-05-01",
              "chargeStatus": {
                "code": "A",
                "description": "Active"
              },
              "resultCode1": {
                "chargeStatus": "A",
                "code": "1501",
                "description": "Recall to Prison",
                "dispositionCode": "F",
                "conviction": true
              },
              "mostSeriousFlag": false,
              "lidsOffenceNumber": 22
            },
            "offenceDate": "2011-05-01",
            "resultCode1": {
              "chargeStatus": "A",
              "code": "1501",
              "description": "Recall to Prison",
              "dispositionCode": "F",
              "conviction": true
            },
            "mostSeriousFlag": false
          },
          {
            "eventId": 668964883,
            "offenderCharge": {
              "id": 1453031,
              "offence": {
                "offenceCode": "PK78001-008NA",
                "statuteCode": "ZZ",
                "description": "INDECENT PHOTOGRAPHS OF CHILDREN"
              },
              "offenceDate": "2010-10-31",
              "chargeStatus": {
                "code": "A",
                "description": "Active"
              },
              "resultCode1": {
                "chargeStatus": "A",
                "code": "1501",
                "description": "Recall to Prison",
                "dispositionCode": "F",
                "conviction": true
              },
              "mostSeriousFlag": false,
              "lidsOffenceNumber": 20
            },
            "offenceDate": "2010-10-31",
            "resultCode1": {
              "chargeStatus": "A",
              "code": "1501",
              "description": "Recall to Prison",
              "dispositionCode": "F",
              "conviction": true
            },
            "mostSeriousFlag": false
          },
          {
            "eventId": 668964883,
            "offenderCharge": {
              "id": 1453032,
              "offence": {
                "offenceCode": "PK78001-008NA",
                "statuteCode": "ZZ",
                "description": "INDECENT PHOTOGRAPHS OF CHILDREN"
              },
              "offenceDate": "2010-03-27",
              "chargeStatus": {
                "code": "A",
                "description": "Active"
              },
              "resultCode1": {
                "chargeStatus": "A",
                "code": "1501",
                "description": "Recall to Prison",
                "dispositionCode": "F",
                "conviction": true
              },
              "mostSeriousFlag": false,
              "lidsOffenceNumber": 16
            },
            "offenceDate": "2010-03-27",
            "resultCode1": {
              "chargeStatus": "A",
              "code": "1501",
              "description": "Recall to Prison",
              "dispositionCode": "F",
              "conviction": true
            },
            "mostSeriousFlag": false
          },
          {
            "eventId": 668964883,
            "offenderCharge": {
              "id": 1453033,
              "offence": {
                "offenceCode": "PK78001-008NA",
                "statuteCode": "ZZ",
                "description": "INDECENT PHOTOGRAPHS OF CHILDREN"
              },
              "offenceDate": "2010-10-31",
              "chargeStatus": {
                "code": "A",
                "description": "Active"
              },
              "resultCode1": {
                "chargeStatus": "A",
                "code": "1501",
                "description": "Recall to Prison",
                "dispositionCode": "F",
                "conviction": true
              },
              "mostSeriousFlag": false,
              "lidsOffenceNumber": 19
            },
            "offenceDate": "2010-10-31",
            "resultCode1": {
              "chargeStatus": "A",
              "code": "1501",
              "description": "Recall to Prison",
              "dispositionCode": "F",
              "conviction": true
            },
            "mostSeriousFlag": false
          },
          {
            "eventId": 668964883,
            "offenderCharge": {
              "id": 1453034,
              "offence": {
                "offenceCode": "PK78001-008NA",
                "statuteCode": "ZZ",
                "description": "INDECENT PHOTOGRAPHS OF CHILDREN"
              },
              "offenceDate": "2010-05-28",
              "chargeStatus": {
                "code": "A",
                "description": "Active"
              },
              "resultCode1": {
                "chargeStatus": "A",
                "code": "1501",
                "description": "Recall to Prison",
                "dispositionCode": "F",
                "conviction": true
              },
              "mostSeriousFlag": false,
              "lidsOffenceNumber": 26
            },
            "offenceDate": "2010-05-28",
            "resultCode1": {
              "chargeStatus": "A",
              "code": "1501",
              "description": "Recall to Prison",
              "dispositionCode": "F",
              "conviction": true
            },
            "mostSeriousFlag": false
          },
          {
            "eventId": 668964883,
            "offenderCharge": {
              "id": 1453035,
              "offence": {
                "offenceCode": "PK78001-008NA",
                "statuteCode": "ZZ",
                "description": "INDECENT PHOTOGRAPHS OF CHILDREN"
              },
              "offenceDate": "2010-10-31",
              "chargeStatus": {
                "code": "A",
                "description": "Active"
              },
              "resultCode1": {
                "chargeStatus": "A",
                "code": "1501",
                "description": "Recall to Prison",
                "dispositionCode": "F",
                "conviction": true
              },
              "mostSeriousFlag": false,
              "lidsOffenceNumber": 18
            },
            "offenceDate": "2010-10-31",
            "resultCode1": {
              "chargeStatus": "A",
              "code": "1501",
              "description": "Recall to Prison",
              "dispositionCode": "F",
              "conviction": true
            },
            "mostSeriousFlag": false
          },
          {
            "eventId": 668964883,
            "offenderCharge": {
              "id": 1453036,
              "offence": {
                "offenceCode": "SX56070-072N",
                "statuteCode": "ZZ",
                "description": "RAPE ON CHILD (SOA 1956 s69)"
              },
              "offenceDate": "2003-10-17",
              "chargeStatus": {
                "code": "A",
                "description": "Active"
              },
              "resultCode1": {
                "chargeStatus": "A",
                "code": "1501",
                "description": "Recall to Prison",
                "dispositionCode": "F",
                "conviction": true
              },
              "mostSeriousFlag": false,
              "lidsOffenceNumber": 3
            },
            "offenceDate": "2003-10-17",
            "resultCode1": {
              "chargeStatus": "A",
              "code": "1501",
              "description": "Recall to Prison",
              "dispositionCode": "F",
              "conviction": true
            },
            "mostSeriousFlag": false
          },
          {
            "eventId": 668964883,
            "offenderCharge": {
              "id": 1453037,
              "offence": {
                "offenceCode": "SX56025-029N",
                "statuteCode": "ZZ",
                "description": "INDECENT ASSAULT ON A CHILD"
              },
              "offenceDate": "2001-10-17",
              "chargeStatus": {
                "code": "A",
                "description": "Active"
              },
              "resultCode1": {
                "chargeStatus": "A",
                "code": "1501",
                "description": "Recall to Prison",
                "dispositionCode": "F",
                "conviction": true
              },
              "mostSeriousFlag": false,
              "lidsOffenceNumber": 8
            },
            "offenceDate": "2001-10-17",
            "resultCode1": {
              "chargeStatus": "A",
              "code": "1501",
              "description": "Recall to Prison",
              "dispositionCode": "F",
              "conviction": true
            },
            "mostSeriousFlag": false
          },
          {
            "eventId": 668964883,
            "offenderCharge": {
              "id": 1453038,
              "offence": {
                "offenceCode": "PK78001-008NA",
                "statuteCode": "ZZ",
                "description": "INDECENT PHOTOGRAPHS OF CHILDREN"
              },
              "offenceDate": "2011-06-22",
              "chargeStatus": {
                "code": "A",
                "description": "Active"
              },
              "resultCode1": {
                "chargeStatus": "A",
                "code": "1501",
                "description": "Recall to Prison",
                "dispositionCode": "F",
                "conviction": true
              },
              "mostSeriousFlag": false,
              "lidsOffenceNumber": 28
            },
            "offenceDate": "2011-06-22",
            "resultCode1": {
              "chargeStatus": "A",
              "code": "1501",
              "description": "Recall to Prison",
              "dispositionCode": "F",
              "conviction": true
            },
            "mostSeriousFlag": false
          },
          {
            "eventId": 668964883,
            "offenderCharge": {
              "id": 1453039,
              "offence": {
                "offenceCode": "SX56025-029N",
                "statuteCode": "ZZ",
                "description": "INDECENT ASSAULT ON A CHILD"
              },
              "offenceDate": "2002-10-17",
              "chargeStatus": {
                "code": "A",
                "description": "Active"
              },
              "resultCode1": {
                "chargeStatus": "A",
                "code": "1501",
                "description": "Recall to Prison",
                "dispositionCode": "F",
                "conviction": true
              },
              "mostSeriousFlag": false,
              "lidsOffenceNumber": 9
            },
            "offenceDate": "2002-10-17",
            "resultCode1": {
              "chargeStatus": "A",
              "code": "1501",
              "description": "Recall to Prison",
              "dispositionCode": "F",
              "conviction": true
            },
            "mostSeriousFlag": false
          },
          {
            "eventId": 668964883,
            "offenderCharge": {
              "id": 1583786,
              "offence": {
                "offenceCode": "SX03002",
                "statuteCode": "SX03",
                "description": "Rape a woman 16 years of age or over - SOA 2003"
              },
              "offenceDate": "2001-10-17",
              "offenceEndDate": "2002-10-17",
              "chargeStatus": {
                "code": "A",
                "description": "Active"
              },
              "resultCode1": {
                "chargeStatus": "A",
                "code": "1501",
                "description": "Recall to Prison",
                "dispositionCode": "F",
                "conviction": true
              },
              "mostSeriousFlag": false,
              "lidsOffenceNumber": 29
            },
            "offenceDate": "2001-10-17",
            "offenceEndDate": "2002-10-17",
            "resultCode1": {
              "chargeStatus": "A",
              "code": "1501",
              "description": "Recall to Prison",
              "dispositionCode": "F",
              "conviction": true
            },
            "mostSeriousFlag": false
          },
          {
            "eventId": 668964883,
            "offenderCharge": {
              "id": 1583787,
              "offence": {
                "offenceCode": "SX03002",
                "statuteCode": "SX03",
                "description": "Rape a woman 16 years of age or over - SOA 2003"
              },
              "offenceDate": "2002-10-17",
              "offenceEndDate": "2003-10-17",
              "chargeStatus": {
                "code": "A",
                "description": "Active"
              },
              "resultCode1": {
                "chargeStatus": "A",
                "code": "1501",
                "description": "Recall to Prison",
                "dispositionCode": "F",
                "conviction": true
              },
              "mostSeriousFlag": false,
              "lidsOffenceNumber": 30
            },
            "offenceDate": "2002-10-17",
            "offenceEndDate": "2003-10-17",
            "resultCode1": {
              "chargeStatus": "A",
              "code": "1501",
              "description": "Recall to Prison",
              "dispositionCode": "F",
              "conviction": true
            },
            "mostSeriousFlag": false
          },
          {
            "eventId": 668964883,
            "offenderCharge": {
              "id": 1583788,
              "offence": {
                "offenceCode": "SX03002",
                "statuteCode": "SX03",
                "description": "Rape a woman 16 years of age or over - SOA 2003"
              },
              "offenceDate": "2003-10-17",
              "offenceEndDate": "2004-04-30",
              "chargeStatus": {
                "code": "A",
                "description": "Active"
              },
              "resultCode1": {
                "chargeStatus": "A",
                "code": "1501",
                "description": "Recall to Prison",
                "dispositionCode": "F",
                "conviction": true
              },
              "mostSeriousFlag": false,
              "lidsOffenceNumber": 31
            },
            "offenceDate": "2003-10-17",
            "offenceEndDate": "2004-04-30",
            "resultCode1": {
              "chargeStatus": "A",
              "code": "1501",
              "description": "Recall to Prison",
              "dispositionCode": "F",
              "conviction": true
            },
            "mostSeriousFlag": false
          },
          {
            "eventId": 668964883,
            "offenderCharge": {
              "id": 1583789,
              "offence": {
                "offenceCode": "SX03013",
                "statuteCode": "SX03",
                "description": "Rape a girl under 13"
              },
              "offenceDate": "2004-05-01",
              "offenceEndDate": "2004-10-17",
              "chargeStatus": {
                "code": "A",
                "description": "Active"
              },
              "resultCode1": {
                "chargeStatus": "A",
                "code": "1501",
                "description": "Recall to Prison",
                "dispositionCode": "F",
                "conviction": true
              },
              "mostSeriousFlag": false,
              "lidsOffenceNumber": 32
            },
            "offenceDate": "2004-05-01",
            "offenceEndDate": "2004-10-17",
            "resultCode1": {
              "chargeStatus": "A",
              "code": "1501",
              "description": "Recall to Prison",
              "dispositionCode": "F",
              "conviction": true
            },
            "mostSeriousFlag": false
          },
          {
            "eventId": 668964883,
            "offenderCharge": {
              "id": 1583790,
              "offence": {
                "offenceCode": "SX03013",
                "statuteCode": "SX03",
                "description": "Rape a girl under 13"
              },
              "offenceDate": "2004-10-17",
              "offenceEndDate": "2005-10-17",
              "chargeStatus": {
                "code": "A",
                "description": "Active"
              },
              "resultCode1": {
                "chargeStatus": "A",
                "code": "1501",
                "description": "Recall to Prison",
                "dispositionCode": "F",
                "conviction": true
              },
              "mostSeriousFlag": false,
              "lidsOffenceNumber": 33
            },
            "offenceDate": "2004-10-17",
            "offenceEndDate": "2005-10-17",
            "resultCode1": {
              "chargeStatus": "A",
              "code": "1501",
              "description": "Recall to Prison",
              "dispositionCode": "F",
              "conviction": true
            },
            "mostSeriousFlag": false
          },
          {
            "eventId": 668964883,
            "offenderCharge": {
              "id": 1583791,
              "offence": {
                "offenceCode": "SX03013",
                "statuteCode": "SX03",
                "description": "Rape a girl under 13"
              },
              "offenceDate": "2005-10-17",
              "offenceEndDate": "2006-10-17",
              "chargeStatus": {
                "code": "A",
                "description": "Active"
              },
              "resultCode1": {
                "chargeStatus": "A",
                "code": "1501",
                "description": "Recall to Prison",
                "dispositionCode": "F",
                "conviction": true
              },
              "mostSeriousFlag": false,
              "lidsOffenceNumber": 34
            },
            "offenceDate": "2005-10-17",
            "offenceEndDate": "2006-10-17",
            "resultCode1": {
              "chargeStatus": "A",
              "code": "1501",
              "description": "Recall to Prison",
              "dispositionCode": "F",
              "conviction": true
            },
            "mostSeriousFlag": false
          },
          {
            "eventId": 668964883,
            "offenderCharge": {
              "id": 1583792,
              "offence": {
                "offenceCode": "SX56025-029N",
                "statuteCode": "ZZ",
                "description": "INDECENT ASSAULT ON A CHILD"
              },
              "offenceDate": "2000-10-17",
              "offenceEndDate": "2001-10-17",
              "chargeStatus": {
                "code": "A",
                "description": "Active"
              },
              "resultCode1": {
                "chargeStatus": "A",
                "code": "1501",
                "description": "Recall to Prison",
                "dispositionCode": "F",
                "conviction": true
              },
              "mostSeriousFlag": false,
              "lidsOffenceNumber": 35
            },
            "offenceDate": "2000-10-17",
            "offenceEndDate": "2001-10-17",
            "resultCode1": {
              "chargeStatus": "A",
              "code": "1501",
              "description": "Recall to Prison",
              "dispositionCode": "F",
              "conviction": true
            },
            "mostSeriousFlag": false
          },
          {
            "eventId": 668964883,
            "offenderCharge": {
              "id": 1583793,
              "offence": {
                "offenceCode": "SX56025-029N",
                "statuteCode": "ZZ",
                "description": "INDECENT ASSAULT ON A CHILD"
              },
              "offenceDate": "2001-10-17",
              "offenceEndDate": "2002-10-17",
              "chargeStatus": {
                "code": "A",
                "description": "Active"
              },
              "resultCode1": {
                "chargeStatus": "A",
                "code": "1501",
                "description": "Recall to Prison",
                "dispositionCode": "F",
                "conviction": true
              },
              "mostSeriousFlag": false,
              "lidsOffenceNumber": 36
            },
            "offenceDate": "2001-10-17",
            "offenceEndDate": "2002-10-17",
            "resultCode1": {
              "chargeStatus": "A",
              "code": "1501",
              "description": "Recall to Prison",
              "dispositionCode": "F",
              "conviction": true
            },
            "mostSeriousFlag": false
          },
          {
            "eventId": 668964883,
            "offenderCharge": {
              "id": 1583794,
              "offence": {
                "offenceCode": "SX56025-029N",
                "statuteCode": "ZZ",
                "description": "INDECENT ASSAULT ON A CHILD"
              },
              "offenceDate": "2002-10-17",
              "offenceEndDate": "2003-10-17",
              "chargeStatus": {
                "code": "A",
                "description": "Active"
              },
              "resultCode1": {
                "chargeStatus": "A",
                "code": "1501",
                "description": "Recall to Prison",
                "dispositionCode": "F",
                "conviction": true
              },
              "mostSeriousFlag": false,
              "lidsOffenceNumber": 37
            },
            "offenceDate": "2002-10-17",
            "offenceEndDate": "2003-10-17",
            "resultCode1": {
              "chargeStatus": "A",
              "code": "1501",
              "description": "Recall to Prison",
              "dispositionCode": "F",
              "conviction": true
            },
            "mostSeriousFlag": false
          },
          {
            "eventId": 668964883,
            "offenderCharge": {
              "id": 1583795,
              "offence": {
                "offenceCode": "SX56025-029N",
                "statuteCode": "ZZ",
                "description": "INDECENT ASSAULT ON A CHILD"
              },
              "offenceDate": "2003-10-17",
              "offenceEndDate": "2004-04-30",
              "chargeStatus": {
                "code": "A",
                "description": "Active"
              },
              "resultCode1": {
                "chargeStatus": "A",
                "code": "1501",
                "description": "Recall to Prison",
                "dispositionCode": "F",
                "conviction": true
              },
              "mostSeriousFlag": false,
              "lidsOffenceNumber": 38
            },
            "offenceDate": "2003-10-17",
            "offenceEndDate": "2004-04-30",
            "resultCode1": {
              "chargeStatus": "A",
              "code": "1501",
              "description": "Recall to Prison",
              "dispositionCode": "F",
              "conviction": true
            },
            "mostSeriousFlag": false
          },
          {
            "eventId": 668964883,
            "offenderCharge": {
              "id": 1583796,
              "offence": {
                "offenceCode": "SX03015",
                "statuteCode": "SX03",
                "description": "Assault a girl under 13 by penetration with a part of your body / a thing - SOA 2003"
              },
              "offenceDate": "2004-05-01",
              "offenceEndDate": "2004-10-17",
              "chargeStatus": {
                "code": "A",
                "description": "Active"
              },
              "resultCode1": {
                "chargeStatus": "A",
                "code": "1501",
                "description": "Recall to Prison",
                "dispositionCode": "F",
                "conviction": true
              },
              "mostSeriousFlag": false,
              "lidsOffenceNumber": 39
            },
            "offenceDate": "2004-05-01",
            "offenceEndDate": "2004-10-17",
            "resultCode1": {
              "chargeStatus": "A",
              "code": "1501",
              "description": "Recall to Prison",
              "dispositionCode": "F",
              "conviction": true
            },
            "mostSeriousFlag": false
          },
          {
            "eventId": 668964883,
            "offenderCharge": {
              "id": 1583797,
              "offence": {
                "offenceCode": "SX03015",
                "statuteCode": "SX03",
                "description": "Assault a girl under 13 by penetration with a part of your body / a thing - SOA 2003"
              },
              "offenceDate": "2004-10-17",
              "offenceEndDate": "2005-10-17",
              "chargeStatus": {
                "code": "A",
                "description": "Active"
              },
              "resultCode1": {
                "chargeStatus": "A",
                "code": "1501",
                "description": "Recall to Prison",
                "dispositionCode": "F",
                "conviction": true
              },
              "mostSeriousFlag": false,
              "lidsOffenceNumber": 40
            },
            "offenceDate": "2004-10-17",
            "offenceEndDate": "2005-10-17",
            "resultCode1": {
              "chargeStatus": "A",
              "code": "1501",
              "description": "Recall to Prison",
              "dispositionCode": "F",
              "conviction": true
            },
            "mostSeriousFlag": false
          },
          {
            "eventId": 668964883,
            "offenderCharge": {
              "id": 1583798,
              "offence": {
                "offenceCode": "SX03015",
                "statuteCode": "SX03",
                "description": "Assault a girl under 13 by penetration with a part of your body / a thing - SOA 2003"
              },
              "offenceDate": "2005-10-17",
              "offenceEndDate": "2006-10-17",
              "chargeStatus": {
                "code": "A",
                "description": "Active"
              },
              "resultCode1": {
                "chargeStatus": "A",
                "code": "1501",
                "description": "Recall to Prison",
                "dispositionCode": "F",
                "conviction": true
              },
              "mostSeriousFlag": false,
              "lidsOffenceNumber": 41
            },
            "offenceDate": "2005-10-17",
            "offenceEndDate": "2006-10-17",
            "resultCode1": {
              "chargeStatus": "A",
              "code": "1501",
              "description": "Recall to Prison",
              "dispositionCode": "F",
              "conviction": true
            },
            "mostSeriousFlag": false
          },
          {
            "eventId": 668964883,
            "offenderCharge": {
              "id": 1583799,
              "offence": {
                "offenceCode": "PK78001",
                "statuteCode": "PK78",
                "description": "Take an indecent photograph / pseudo-photograph of a child"
              },
              "offenceDate": "2010-03-28",
              "chargeStatus": {
                "code": "A",
                "description": "Active"
              },
              "resultCode1": {
                "chargeStatus": "A",
                "code": "1501",
                "description": "Recall to Prison",
                "dispositionCode": "F",
                "conviction": true
              },
              "mostSeriousFlag": false,
              "lidsOffenceNumber": 42
            },
            "offenceDate": "2010-03-28",
            "resultCode1": {
              "chargeStatus": "A",
              "code": "1501",
              "description": "Recall to Prison",
              "dispositionCode": "F",
              "conviction": true
            },
            "mostSeriousFlag": false
          },
          {
            "eventId": 668964883,
            "offenderCharge": {
              "id": 1583800,
              "offence": {
                "offenceCode": "PK78001",
                "statuteCode": "PK78",
                "description": "Take an indecent photograph / pseudo-photograph of a child"
              },
              "offenceDate": "2010-03-27",
              "chargeStatus": {
                "code": "A",
                "description": "Active"
              },
              "resultCode1": {
                "chargeStatus": "A",
                "code": "1501",
                "description": "Recall to Prison",
                "dispositionCode": "F",
                "conviction": true
              },
              "mostSeriousFlag": false,
              "lidsOffenceNumber": 43
            },
            "offenceDate": "2010-03-27",
            "resultCode1": {
              "chargeStatus": "A",
              "code": "1501",
              "description": "Recall to Prison",
              "dispositionCode": "F",
              "conviction": true
            },
            "mostSeriousFlag": false
          },
          {
            "eventId": 668964883,
            "offenderCharge": {
              "id": 1583801,
              "offence": {
                "offenceCode": "PK78001",
                "statuteCode": "PK78",
                "description": "Take an indecent photograph / pseudo-photograph of a child"
              },
              "offenceDate": "2010-10-31",
              "chargeStatus": {
                "code": "A",
                "description": "Active"
              },
              "resultCode1": {
                "chargeStatus": "A",
                "code": "1501",
                "description": "Recall to Prison",
                "dispositionCode": "F",
                "conviction": true
              },
              "mostSeriousFlag": false,
              "lidsOffenceNumber": 44
            },
            "offenceDate": "2010-10-31",
            "resultCode1": {
              "chargeStatus": "A",
              "code": "1501",
              "description": "Recall to Prison",
              "dispositionCode": "F",
              "conviction": true
            },
            "mostSeriousFlag": false
          },
          {
            "eventId": 668964883,
            "offenderCharge": {
              "id": 1583802,
              "offence": {
                "offenceCode": "PK78001",
                "statuteCode": "PK78",
                "description": "Take an indecent photograph / pseudo-photograph of a child"
              },
              "offenceDate": "2010-10-31",
              "chargeStatus": {
                "code": "A",
                "description": "Active"
              },
              "resultCode1": {
                "chargeStatus": "A",
                "code": "1501",
                "description": "Recall to Prison",
                "dispositionCode": "F",
                "conviction": true
              },
              "mostSeriousFlag": false,
              "lidsOffenceNumber": 45
            },
            "offenceDate": "2010-10-31",
            "resultCode1": {
              "chargeStatus": "A",
              "code": "1501",
              "description": "Recall to Prison",
              "dispositionCode": "F",
              "conviction": true
            },
            "mostSeriousFlag": false
          },
          {
            "eventId": 668964883,
            "offenderCharge": {
              "id": 1583803,
              "offence": {
                "offenceCode": "PK78001",
                "statuteCode": "PK78",
                "description": "Take an indecent photograph / pseudo-photograph of a child"
              },
              "offenceDate": "2010-10-31",
              "chargeStatus": {
                "code": "A",
                "description": "Active"
              },
              "resultCode1": {
                "chargeStatus": "A",
                "code": "1501",
                "description": "Recall to Prison",
                "dispositionCode": "F",
                "conviction": true
              },
              "mostSeriousFlag": false,
              "lidsOffenceNumber": 46
            },
            "offenceDate": "2010-10-31",
            "resultCode1": {
              "chargeStatus": "A",
              "code": "1501",
              "description": "Recall to Prison",
              "dispositionCode": "F",
              "conviction": true
            },
            "mostSeriousFlag": false
          },
          {
            "eventId": 668964883,
            "offenderCharge": {
              "id": 1583804,
              "offence": {
                "offenceCode": "PK78001",
                "statuteCode": "PK78",
                "description": "Take an indecent photograph / pseudo-photograph of a child"
              },
              "offenceDate": "2010-10-31",
              "chargeStatus": {
                "code": "A",
                "description": "Active"
              },
              "resultCode1": {
                "chargeStatus": "A",
                "code": "1501",
                "description": "Recall to Prison",
                "dispositionCode": "F",
                "conviction": true
              },
              "mostSeriousFlag": false,
              "lidsOffenceNumber": 47
            },
            "offenceDate": "2010-10-31",
            "resultCode1": {
              "chargeStatus": "A",
              "code": "1501",
              "description": "Recall to Prison",
              "dispositionCode": "F",
              "conviction": true
            },
            "mostSeriousFlag": false
          },
          {
            "eventId": 668964883,
            "offenderCharge": {
              "id": 1583805,
              "offence": {
                "offenceCode": "PK78001",
                "statuteCode": "PK78",
                "description": "Take an indecent photograph / pseudo-photograph of a child"
              },
              "offenceDate": "2011-01-18",
              "chargeStatus": {
                "code": "A",
                "description": "Active"
              },
              "resultCode1": {
                "chargeStatus": "A",
                "code": "1501",
                "description": "Recall to Prison",
                "dispositionCode": "F",
                "conviction": true
              },
              "mostSeriousFlag": false,
              "lidsOffenceNumber": 48
            },
            "offenceDate": "2011-01-18",
            "resultCode1": {
              "chargeStatus": "A",
              "code": "1501",
              "description": "Recall to Prison",
              "dispositionCode": "F",
              "conviction": true
            },
            "mostSeriousFlag": false
          },
          {
            "eventId": 668964883,
            "offenderCharge": {
              "id": 1583806,
              "offence": {
                "offenceCode": "PK78001",
                "statuteCode": "PK78",
                "description": "Take an indecent photograph / pseudo-photograph of a child"
              },
              "offenceDate": "2011-05-01",
              "chargeStatus": {
                "code": "A",
                "description": "Active"
              },
              "resultCode1": {
                "chargeStatus": "A",
                "code": "1501",
                "description": "Recall to Prison",
                "dispositionCode": "F",
                "conviction": true
              },
              "mostSeriousFlag": false,
              "lidsOffenceNumber": 49
            },
            "offenceDate": "2011-05-01",
            "resultCode1": {
              "chargeStatus": "A",
              "code": "1501",
              "description": "Recall to Prison",
              "dispositionCode": "F",
              "conviction": true
            },
            "mostSeriousFlag": false
          },
          {
            "eventId": 668964883,
            "offenderCharge": {
              "id": 1583807,
              "offence": {
                "offenceCode": "PK78001",
                "statuteCode": "PK78",
                "description": "Take an indecent photograph / pseudo-photograph of a child"
              },
              "offenceDate": "2010-10-31",
              "chargeStatus": {
                "code": "A",
                "description": "Active"
              },
              "resultCode1": {
                "chargeStatus": "A",
                "code": "1501",
                "description": "Recall to Prison",
                "dispositionCode": "F",
                "conviction": true
              },
              "mostSeriousFlag": false,
              "lidsOffenceNumber": 50
            },
            "offenceDate": "2010-10-31",
            "resultCode1": {
              "chargeStatus": "A",
              "code": "1501",
              "description": "Recall to Prison",
              "dispositionCode": "F",
              "conviction": true
            },
            "mostSeriousFlag": false
          },
          {
            "eventId": 668964883,
            "offenderCharge": {
              "id": 1583808,
              "offence": {
                "offenceCode": "PK78001",
                "statuteCode": "PK78",
                "description": "Take an indecent photograph / pseudo-photograph of a child"
              },
              "offenceDate": "2011-03-27",
              "chargeStatus": {
                "code": "A",
                "description": "Active"
              },
              "resultCode1": {
                "chargeStatus": "A",
                "code": "1501",
                "description": "Recall to Prison",
                "dispositionCode": "F",
                "conviction": true
              },
              "mostSeriousFlag": false,
              "lidsOffenceNumber": 51
            },
            "offenceDate": "2011-03-27",
            "resultCode1": {
              "chargeStatus": "A",
              "code": "1501",
              "description": "Recall to Prison",
              "dispositionCode": "F",
              "conviction": true
            },
            "mostSeriousFlag": false
          },
          {
            "eventId": 668964883,
            "offenderCharge": {
              "id": 1583809,
              "offence": {
                "offenceCode": "PK78001",
                "statuteCode": "PK78",
                "description": "Take an indecent photograph / pseudo-photograph of a child"
              },
              "offenceDate": "2011-05-23",
              "chargeStatus": {
                "code": "A",
                "description": "Active"
              },
              "resultCode1": {
                "chargeStatus": "A",
                "code": "1501",
                "description": "Recall to Prison",
                "dispositionCode": "F",
                "conviction": true
              },
              "mostSeriousFlag": false,
              "lidsOffenceNumber": 52
            },
            "offenceDate": "2011-05-23",
            "resultCode1": {
              "chargeStatus": "A",
              "code": "1501",
              "description": "Recall to Prison",
              "dispositionCode": "F",
              "conviction": true
            },
            "mostSeriousFlag": false
          },
          {
            "eventId": 668964883,
            "offenderCharge": {
              "id": 1583810,
              "offence": {
                "offenceCode": "PK78001",
                "statuteCode": "PK78",
                "description": "Take an indecent photograph / pseudo-photograph of a child"
              },
              "offenceDate": "2010-05-28",
              "chargeStatus": {
                "code": "A",
                "description": "Active"
              },
              "resultCode1": {
                "chargeStatus": "A",
                "code": "1501",
                "description": "Recall to Prison",
                "dispositionCode": "F",
                "conviction": true
              },
              "mostSeriousFlag": false,
              "lidsOffenceNumber": 53
            },
            "offenceDate": "2010-05-28",
            "resultCode1": {
              "chargeStatus": "A",
              "code": "1501",
              "description": "Recall to Prison",
              "dispositionCode": "F",
              "conviction": true
            },
            "mostSeriousFlag": false
          },
          {
            "eventId": 668964883,
            "offenderCharge": {
              "id": 1583811,
              "offence": {
                "offenceCode": "PK78001",
                "statuteCode": "PK78",
                "description": "Take an indecent photograph / pseudo-photograph of a child"
              },
              "offenceDate": "2006-01-01",
              "offenceEndDate": "2011-06-30",
              "chargeStatus": {
                "code": "A",
                "description": "Active"
              },
              "resultCode1": {
                "chargeStatus": "A",
                "code": "1501",
                "description": "Recall to Prison",
                "dispositionCode": "F",
                "conviction": true
              },
              "mostSeriousFlag": false,
              "lidsOffenceNumber": 54
            },
            "offenceDate": "2006-01-01",
            "offenceEndDate": "2011-06-30",
            "resultCode1": {
              "chargeStatus": "A",
              "code": "1501",
              "description": "Recall to Prison",
              "dispositionCode": "F",
              "conviction": true
            },
            "mostSeriousFlag": false
          }
        ],
        "courtOrders": [
          {
            "id": 3535564,
            "eventId": 668964883,
            "courtDate": "2024-10-31",
            "issuingCourt": "COACD",
            "orderType": "AUTO",
            "orderStatus": "A",
            "nonReportFlag": false,
            "sentencePurposes": []
          }
        ]
      }
    ],
    "offenderCharges": [
      {
        "id": 1453012,
        "offence": {
          "offenceCode": "PK78001-008NA",
          "statuteCode": "ZZ",
          "description": "INDECENT PHOTOGRAPHS OF CHILDREN"
        },
        "offenceDate": "2010-10-31",
        "chargeStatus": {
          "code": "A",
          "description": "Active"
        },
        "resultCode1": {
          "chargeStatus": "A",
          "code": "1501",
          "description": "Recall to Prison",
          "dispositionCode": "F",
          "conviction": true
        },
        "mostSeriousFlag": false,
        "lidsOffenceNumber": 23
      },
      {
        "id": 1453013,
        "offence": {
          "offenceCode": "SX03013-014N",
          "statuteCode": "ZZ",
          "description": "RAPE OF A CHILD U13 (SOA 2003 s5)"
        },
        "offenceDate": "2006-10-17",
        "chargeStatus": {
          "code": "A",
          "description": "Active"
        },
        "resultCode1": {
          "chargeStatus": "A",
          "code": "1501",
          "description": "Recall to Prison",
          "dispositionCode": "F",
          "conviction": true
        },
        "mostSeriousFlag": false,
        "lidsOffenceNumber": 7
      },
      {
        "id": 1453014,
        "offence": {
          "offenceCode": "SX56070-072N",
          "statuteCode": "ZZ",
          "description": "RAPE ON CHILD (SOA 1956 s69)"
        },
        "offenceDate": "2001-10-17",
        "chargeStatus": {
          "code": "A",
          "description": "Active"
        },
        "resultCode1": {
          "chargeStatus": "A",
          "code": "1501",
          "description": "Recall to Prison",
          "dispositionCode": "F",
          "conviction": true
        },
        "mostSeriousFlag": false,
        "lidsOffenceNumber": 1
      },
      {
        "id": 1453015,
        "offence": {
          "offenceCode": "SX56070-072N",
          "statuteCode": "ZZ",
          "description": "RAPE ON CHILD (SOA 1956 s69)"
        },
        "offenceDate": "2004-04-30",
        "chargeStatus": {
          "code": "A",
          "description": "Active"
        },
        "resultCode1": {
          "chargeStatus": "A",
          "code": "1501",
          "description": "Recall to Prison",
          "dispositionCode": "F",
          "conviction": true
        },
        "mostSeriousFlag": false,
        "lidsOffenceNumber": 4
      },
      {
        "id": 1453016,
        "offence": {
          "offenceCode": "PK78001-008NA",
          "statuteCode": "ZZ",
          "description": "INDECENT PHOTOGRAPHS OF CHILDREN"
        },
        "offenceDate": "2010-03-28",
        "chargeStatus": {
          "code": "A",
          "description": "Active"
        },
        "resultCode1": {
          "chargeStatus": "A",
          "code": "1501",
          "description": "Recall to Prison",
          "dispositionCode": "F",
          "conviction": true
        },
        "mostSeriousFlag": false,
        "lidsOffenceNumber": 15
      },
      {
        "id": 1453017,
        "offence": {
          "offenceCode": "PK78001-008NA",
          "statuteCode": "ZZ",
          "description": "INDECENT PHOTOGRAPHS OF CHILDREN"
        },
        "offenceDate": "2011-06-30",
        "chargeStatus": {
          "code": "A",
          "description": "Active"
        },
        "resultCode1": {
          "chargeStatus": "A",
          "code": "1501",
          "description": "Recall to Prison",
          "dispositionCode": "F",
          "conviction": true
        },
        "mostSeriousFlag": false,
        "lidsOffenceNumber": 27
      },
      {
        "id": 1453018,
        "offence": {
          "offenceCode": "SX03013-014N",
          "statuteCode": "ZZ",
          "description": "RAPE OF A CHILD U13 (SOA 2003 s5)"
        },
        "offenceDate": "2005-10-17",
        "chargeStatus": {
          "code": "A",
          "description": "Active"
        },
        "resultCode1": {
          "chargeStatus": "A",
          "code": "1501",
          "description": "Recall to Prison",
          "dispositionCode": "F",
          "conviction": true
        },
        "mostSeriousFlag": false,
        "lidsOffenceNumber": 6
      },
      {
        "id": 1453019,
        "offence": {
          "offenceCode": "PK78001-008NA",
          "statuteCode": "ZZ",
          "description": "INDECENT PHOTOGRAPHS OF CHILDREN"
        },
        "offenceDate": "2011-03-27",
        "chargeStatus": {
          "code": "A",
          "description": "Active"
        },
        "resultCode1": {
          "chargeStatus": "A",
          "code": "1501",
          "description": "Recall to Prison",
          "dispositionCode": "F",
          "conviction": true
        },
        "mostSeriousFlag": false,
        "lidsOffenceNumber": 24
      },
      {
        "id": 1453020,
        "offence": {
          "offenceCode": "SX56025-029N",
          "statuteCode": "ZZ",
          "description": "INDECENT ASSAULT ON A CHILD"
        },
        "offenceDate": "2003-10-17",
        "chargeStatus": {
          "code": "A",
          "description": "Active"
        },
        "resultCode1": {
          "chargeStatus": "A",
          "code": "1501",
          "description": "Recall to Prison",
          "dispositionCode": "F",
          "conviction": true
        },
        "mostSeriousFlag": false,
        "lidsOffenceNumber": 10
      },
      {
        "id": 1453021,
        "offence": {
          "offenceCode": "PK78001-008NA",
          "statuteCode": "ZZ",
          "description": "INDECENT PHOTOGRAPHS OF CHILDREN"
        },
        "offenceDate": "2011-05-01",
        "chargeStatus": {
          "code": "A",
          "description": "Active"
        },
        "resultCode1": {
          "chargeStatus": "A",
          "code": "1501",
          "description": "Recall to Prison",
          "dispositionCode": "F",
          "conviction": true
        },
        "mostSeriousFlag": false,
        "lidsOffenceNumber": 21
      },
      {
        "id": 1453022,
        "offence": {
          "offenceCode": "SX03015-016N",
          "statuteCode": "ZZ",
          "description": "ASSAULT OF A CHILD U13 BY PENETRATION (SOA 2003 s6"
        },
        "offenceDate": "2006-10-17",
        "chargeStatus": {
          "code": "A",
          "description": "Active"
        },
        "resultCode1": {
          "chargeStatus": "A",
          "code": "1501",
          "description": "Recall to Prison",
          "dispositionCode": "F",
          "conviction": true
        },
        "mostSeriousFlag": false,
        "lidsOffenceNumber": 14
      },
      {
        "id": 1453023,
        "offence": {
          "offenceCode": "PK78001-008NA",
          "statuteCode": "ZZ",
          "description": "INDECENT PHOTOGRAPHS OF CHILDREN"
        },
        "offenceDate": "2010-10-31",
        "chargeStatus": {
          "code": "A",
          "description": "Active"
        },
        "resultCode1": {
          "chargeStatus": "A",
          "code": "1501",
          "description": "Recall to Prison",
          "dispositionCode": "F",
          "conviction": true
        },
        "mostSeriousFlag": false,
        "lidsOffenceNumber": 17
      },
      {
        "id": 1453024,
        "offence": {
          "offenceCode": "PK78001-008NA",
          "statuteCode": "ZZ",
          "description": "INDECENT PHOTOGRAPHS OF CHILDREN"
        },
        "offenceDate": "2011-05-23",
        "chargeStatus": {
          "code": "A",
          "description": "Active"
        },
        "resultCode1": {
          "chargeStatus": "A",
          "code": "1501",
          "description": "Recall to Prison",
          "dispositionCode": "F",
          "conviction": true
        },
        "mostSeriousFlag": false,
        "lidsOffenceNumber": 25
      },
      {
        "id": 1453025,
        "offence": {
          "offenceCode": "SX03013-014N",
          "statuteCode": "ZZ",
          "description": "RAPE OF A CHILD U13 (SOA 2003 s5)"
        },
        "offenceDate": "2004-10-17",
        "chargeStatus": {
          "code": "A",
          "description": "Active"
        },
        "resultCode1": {
          "chargeStatus": "A",
          "code": "1501",
          "description": "Recall to Prison",
          "dispositionCode": "F",
          "conviction": true
        },
        "mostSeriousFlag": false,
        "lidsOffenceNumber": 5
      },
      {
        "id": 1453026,
        "offence": {
          "offenceCode": "SX56025-029N",
          "statuteCode": "ZZ",
          "description": "INDECENT ASSAULT ON A CHILD"
        },
        "offenceDate": "2004-04-30",
        "chargeStatus": {
          "code": "A",
          "description": "Active"
        },
        "resultCode1": {
          "chargeStatus": "A",
          "code": "1501",
          "description": "Recall to Prison",
          "dispositionCode": "F",
          "conviction": true
        },
        "mostSeriousFlag": false,
        "lidsOffenceNumber": 11
      },
      {
        "id": 1453027,
        "offence": {
          "offenceCode": "SX03015-016N",
          "statuteCode": "ZZ",
          "description": "ASSAULT OF A CHILD U13 BY PENETRATION (SOA 2003 s6"
        },
        "offenceDate": "2005-10-17",
        "chargeStatus": {
          "code": "A",
          "description": "Active"
        },
        "resultCode1": {
          "chargeStatus": "A",
          "code": "1501",
          "description": "Recall to Prison",
          "dispositionCode": "F",
          "conviction": true
        },
        "mostSeriousFlag": false,
        "lidsOffenceNumber": 12
      },
      {
        "id": 1453028,
        "offence": {
          "offenceCode": "SX03015-016N",
          "statuteCode": "ZZ",
          "description": "ASSAULT OF A CHILD U13 BY PENETRATION (SOA 2003 s6"
        },
        "offenceDate": "2005-10-17",
        "chargeStatus": {
          "code": "A",
          "description": "Active"
        },
        "resultCode1": {
          "chargeStatus": "A",
          "code": "1501",
          "description": "Recall to Prison",
          "dispositionCode": "F",
          "conviction": true
        },
        "mostSeriousFlag": false,
        "lidsOffenceNumber": 13
      },
      {
        "id": 1453029,
        "offence": {
          "offenceCode": "SX56070-072N",
          "statuteCode": "ZZ",
          "description": "RAPE ON CHILD (SOA 1956 s69)"
        },
        "offenceDate": "2002-10-17",
        "chargeStatus": {
          "code": "A",
          "description": "Active"
        },
        "resultCode1": {
          "chargeStatus": "A",
          "code": "1501",
          "description": "Recall to Prison",
          "dispositionCode": "F",
          "conviction": true
        },
        "mostSeriousFlag": false,
        "lidsOffenceNumber": 2
      },
      {
        "id": 1453030,
        "offence": {
          "offenceCode": "PK78001-008NA",
          "statuteCode": "ZZ",
          "description": "INDECENT PHOTOGRAPHS OF CHILDREN"
        },
        "offenceDate": "2011-05-01",
        "chargeStatus": {
          "code": "A",
          "description": "Active"
        },
        "resultCode1": {
          "chargeStatus": "A",
          "code": "1501",
          "description": "Recall to Prison",
          "dispositionCode": "F",
          "conviction": true
        },
        "mostSeriousFlag": false,
        "lidsOffenceNumber": 22
      },
      {
        "id": 1453031,
        "offence": {
          "offenceCode": "PK78001-008NA",
          "statuteCode": "ZZ",
          "description": "INDECENT PHOTOGRAPHS OF CHILDREN"
        },
        "offenceDate": "2010-10-31",
        "chargeStatus": {
          "code": "A",
          "description": "Active"
        },
        "resultCode1": {
          "chargeStatus": "A",
          "code": "1501",
          "description": "Recall to Prison",
          "dispositionCode": "F",
          "conviction": true
        },
        "mostSeriousFlag": false,
        "lidsOffenceNumber": 20
      },
      {
        "id": 1453032,
        "offence": {
          "offenceCode": "PK78001-008NA",
          "statuteCode": "ZZ",
          "description": "INDECENT PHOTOGRAPHS OF CHILDREN"
        },
        "offenceDate": "2010-03-27",
        "chargeStatus": {
          "code": "A",
          "description": "Active"
        },
        "resultCode1": {
          "chargeStatus": "A",
          "code": "1501",
          "description": "Recall to Prison",
          "dispositionCode": "F",
          "conviction": true
        },
        "mostSeriousFlag": false,
        "lidsOffenceNumber": 16
      },
      {
        "id": 1453033,
        "offence": {
          "offenceCode": "PK78001-008NA",
          "statuteCode": "ZZ",
          "description": "INDECENT PHOTOGRAPHS OF CHILDREN"
        },
        "offenceDate": "2010-10-31",
        "chargeStatus": {
          "code": "A",
          "description": "Active"
        },
        "resultCode1": {
          "chargeStatus": "A",
          "code": "1501",
          "description": "Recall to Prison",
          "dispositionCode": "F",
          "conviction": true
        },
        "mostSeriousFlag": false,
        "lidsOffenceNumber": 19
      },
      {
        "id": 1453034,
        "offence": {
          "offenceCode": "PK78001-008NA",
          "statuteCode": "ZZ",
          "description": "INDECENT PHOTOGRAPHS OF CHILDREN"
        },
        "offenceDate": "2010-05-28",
        "chargeStatus": {
          "code": "A",
          "description": "Active"
        },
        "resultCode1": {
          "chargeStatus": "A",
          "code": "1501",
          "description": "Recall to Prison",
          "dispositionCode": "F",
          "conviction": true
        },
        "mostSeriousFlag": false,
        "lidsOffenceNumber": 26
      },
      {
        "id": 1453035,
        "offence": {
          "offenceCode": "PK78001-008NA",
          "statuteCode": "ZZ",
          "description": "INDECENT PHOTOGRAPHS OF CHILDREN"
        },
        "offenceDate": "2010-10-31",
        "chargeStatus": {
          "code": "A",
          "description": "Active"
        },
        "resultCode1": {
          "chargeStatus": "A",
          "code": "1501",
          "description": "Recall to Prison",
          "dispositionCode": "F",
          "conviction": true
        },
        "mostSeriousFlag": false,
        "lidsOffenceNumber": 18
      },
      {
        "id": 1453036,
        "offence": {
          "offenceCode": "SX56070-072N",
          "statuteCode": "ZZ",
          "description": "RAPE ON CHILD (SOA 1956 s69)"
        },
        "offenceDate": "2003-10-17",
        "chargeStatus": {
          "code": "A",
          "description": "Active"
        },
        "resultCode1": {
          "chargeStatus": "A",
          "code": "1501",
          "description": "Recall to Prison",
          "dispositionCode": "F",
          "conviction": true
        },
        "mostSeriousFlag": false,
        "lidsOffenceNumber": 3
      },
      {
        "id": 1453037,
        "offence": {
          "offenceCode": "SX56025-029N",
          "statuteCode": "ZZ",
          "description": "INDECENT ASSAULT ON A CHILD"
        },
        "offenceDate": "2001-10-17",
        "chargeStatus": {
          "code": "A",
          "description": "Active"
        },
        "resultCode1": {
          "chargeStatus": "A",
          "code": "1501",
          "description": "Recall to Prison",
          "dispositionCode": "F",
          "conviction": true
        },
        "mostSeriousFlag": false,
        "lidsOffenceNumber": 8
      },
      {
        "id": 1453038,
        "offence": {
          "offenceCode": "PK78001-008NA",
          "statuteCode": "ZZ",
          "description": "INDECENT PHOTOGRAPHS OF CHILDREN"
        },
        "offenceDate": "2011-06-22",
        "chargeStatus": {
          "code": "A",
          "description": "Active"
        },
        "resultCode1": {
          "chargeStatus": "A",
          "code": "1501",
          "description": "Recall to Prison",
          "dispositionCode": "F",
          "conviction": true
        },
        "mostSeriousFlag": false,
        "lidsOffenceNumber": 28
      },
      {
        "id": 1453039,
        "offence": {
          "offenceCode": "SX56025-029N",
          "statuteCode": "ZZ",
          "description": "INDECENT ASSAULT ON A CHILD"
        },
        "offenceDate": "2002-10-17",
        "chargeStatus": {
          "code": "A",
          "description": "Active"
        },
        "resultCode1": {
          "chargeStatus": "A",
          "code": "1501",
          "description": "Recall to Prison",
          "dispositionCode": "F",
          "conviction": true
        },
        "mostSeriousFlag": false,
        "lidsOffenceNumber": 9
      },
      {
        "id": 1583786,
        "offence": {
          "offenceCode": "SX03002",
          "statuteCode": "SX03",
          "description": "Rape a woman 16 years of age or over - SOA 2003"
        },
        "offenceDate": "2001-10-17",
        "offenceEndDate": "2002-10-17",
        "chargeStatus": {
          "code": "A",
          "description": "Active"
        },
        "resultCode1": {
          "chargeStatus": "A",
          "code": "1501",
          "description": "Recall to Prison",
          "dispositionCode": "F",
          "conviction": true
        },
        "mostSeriousFlag": false,
        "lidsOffenceNumber": 29
      },
      {
        "id": 1583787,
        "offence": {
          "offenceCode": "SX03002",
          "statuteCode": "SX03",
          "description": "Rape a woman 16 years of age or over - SOA 2003"
        },
        "offenceDate": "2002-10-17",
        "offenceEndDate": "2003-10-17",
        "chargeStatus": {
          "code": "A",
          "description": "Active"
        },
        "resultCode1": {
          "chargeStatus": "A",
          "code": "1501",
          "description": "Recall to Prison",
          "dispositionCode": "F",
          "conviction": true
        },
        "mostSeriousFlag": false,
        "lidsOffenceNumber": 30
      },
      {
        "id": 1583788,
        "offence": {
          "offenceCode": "SX03002",
          "statuteCode": "SX03",
          "description": "Rape a woman 16 years of age or over - SOA 2003"
        },
        "offenceDate": "2003-10-17",
        "offenceEndDate": "2004-04-30",
        "chargeStatus": {
          "code": "A",
          "description": "Active"
        },
        "resultCode1": {
          "chargeStatus": "A",
          "code": "1501",
          "description": "Recall to Prison",
          "dispositionCode": "F",
          "conviction": true
        },
        "mostSeriousFlag": false,
        "lidsOffenceNumber": 31
      },
      {
        "id": 1583789,
        "offence": {
          "offenceCode": "SX03013",
          "statuteCode": "SX03",
          "description": "Rape a girl under 13"
        },
        "offenceDate": "2004-05-01",
        "offenceEndDate": "2004-10-17",
        "chargeStatus": {
          "code": "A",
          "description": "Active"
        },
        "resultCode1": {
          "chargeStatus": "A",
          "code": "1501",
          "description": "Recall to Prison",
          "dispositionCode": "F",
          "conviction": true
        },
        "mostSeriousFlag": false,
        "lidsOffenceNumber": 32
      },
      {
        "id": 1583790,
        "offence": {
          "offenceCode": "SX03013",
          "statuteCode": "SX03",
          "description": "Rape a girl under 13"
        },
        "offenceDate": "2004-10-17",
        "offenceEndDate": "2005-10-17",
        "chargeStatus": {
          "code": "A",
          "description": "Active"
        },
        "resultCode1": {
          "chargeStatus": "A",
          "code": "1501",
          "description": "Recall to Prison",
          "dispositionCode": "F",
          "conviction": true
        },
        "mostSeriousFlag": false,
        "lidsOffenceNumber": 33
      },
      {
        "id": 1583791,
        "offence": {
          "offenceCode": "SX03013",
          "statuteCode": "SX03",
          "description": "Rape a girl under 13"
        },
        "offenceDate": "2005-10-17",
        "offenceEndDate": "2006-10-17",
        "chargeStatus": {
          "code": "A",
          "description": "Active"
        },
        "resultCode1": {
          "chargeStatus": "A",
          "code": "1501",
          "description": "Recall to Prison",
          "dispositionCode": "F",
          "conviction": true
        },
        "mostSeriousFlag": false,
        "lidsOffenceNumber": 34
      },
      {
        "id": 1583792,
        "offence": {
          "offenceCode": "SX56025-029N",
          "statuteCode": "ZZ",
          "description": "INDECENT ASSAULT ON A CHILD"
        },
        "offenceDate": "2000-10-17",
        "offenceEndDate": "2001-10-17",
        "chargeStatus": {
          "code": "A",
          "description": "Active"
        },
        "resultCode1": {
          "chargeStatus": "A",
          "code": "1501",
          "description": "Recall to Prison",
          "dispositionCode": "F",
          "conviction": true
        },
        "mostSeriousFlag": false,
        "lidsOffenceNumber": 35
      },
      {
        "id": 1583793,
        "offence": {
          "offenceCode": "SX56025-029N",
          "statuteCode": "ZZ",
          "description": "INDECENT ASSAULT ON A CHILD"
        },
        "offenceDate": "2001-10-17",
        "offenceEndDate": "2002-10-17",
        "chargeStatus": {
          "code": "A",
          "description": "Active"
        },
        "resultCode1": {
          "chargeStatus": "A",
          "code": "1501",
          "description": "Recall to Prison",
          "dispositionCode": "F",
          "conviction": true
        },
        "mostSeriousFlag": false,
        "lidsOffenceNumber": 36
      },
      {
        "id": 1583794,
        "offence": {
          "offenceCode": "SX56025-029N",
          "statuteCode": "ZZ",
          "description": "INDECENT ASSAULT ON A CHILD"
        },
        "offenceDate": "2002-10-17",
        "offenceEndDate": "2003-10-17",
        "chargeStatus": {
          "code": "A",
          "description": "Active"
        },
        "resultCode1": {
          "chargeStatus": "A",
          "code": "1501",
          "description": "Recall to Prison",
          "dispositionCode": "F",
          "conviction": true
        },
        "mostSeriousFlag": false,
        "lidsOffenceNumber": 37
      },
      {
        "id": 1583795,
        "offence": {
          "offenceCode": "SX56025-029N",
          "statuteCode": "ZZ",
          "description": "INDECENT ASSAULT ON A CHILD"
        },
        "offenceDate": "2003-10-17",
        "offenceEndDate": "2004-04-30",
        "chargeStatus": {
          "code": "A",
          "description": "Active"
        },
        "resultCode1": {
          "chargeStatus": "A",
          "code": "1501",
          "description": "Recall to Prison",
          "dispositionCode": "F",
          "conviction": true
        },
        "mostSeriousFlag": false,
        "lidsOffenceNumber": 38
      },
      {
        "id": 1583796,
        "offence": {
          "offenceCode": "SX03015",
          "statuteCode": "SX03",
          "description": "Assault a girl under 13 by penetration with a part of your body / a thing - SOA 2003"
        },
        "offenceDate": "2004-05-01",
        "offenceEndDate": "2004-10-17",
        "chargeStatus": {
          "code": "A",
          "description": "Active"
        },
        "resultCode1": {
          "chargeStatus": "A",
          "code": "1501",
          "description": "Recall to Prison",
          "dispositionCode": "F",
          "conviction": true
        },
        "mostSeriousFlag": false,
        "lidsOffenceNumber": 39
      },
      {
        "id": 1583797,
        "offence": {
          "offenceCode": "SX03015",
          "statuteCode": "SX03",
          "description": "Assault a girl under 13 by penetration with a part of your body / a thing - SOA 2003"
        },
        "offenceDate": "2004-10-17",
        "offenceEndDate": "2005-10-17",
        "chargeStatus": {
          "code": "A",
          "description": "Active"
        },
        "resultCode1": {
          "chargeStatus": "A",
          "code": "1501",
          "description": "Recall to Prison",
          "dispositionCode": "F",
          "conviction": true
        },
        "mostSeriousFlag": false,
        "lidsOffenceNumber": 40
      },
      {
        "id": 1583798,
        "offence": {
          "offenceCode": "SX03015",
          "statuteCode": "SX03",
          "description": "Assault a girl under 13 by penetration with a part of your body / a thing - SOA 2003"
        },
        "offenceDate": "2005-10-17",
        "offenceEndDate": "2006-10-17",
        "chargeStatus": {
          "code": "A",
          "description": "Active"
        },
        "resultCode1": {
          "chargeStatus": "A",
          "code": "1501",
          "description": "Recall to Prison",
          "dispositionCode": "F",
          "conviction": true
        },
        "mostSeriousFlag": false,
        "lidsOffenceNumber": 41
      },
      {
        "id": 1583799,
        "offence": {
          "offenceCode": "PK78001",
          "statuteCode": "PK78",
          "description": "Take an indecent photograph / pseudo-photograph of a child"
        },
        "offenceDate": "2010-03-28",
        "chargeStatus": {
          "code": "A",
          "description": "Active"
        },
        "resultCode1": {
          "chargeStatus": "A",
          "code": "1501",
          "description": "Recall to Prison",
          "dispositionCode": "F",
          "conviction": true
        },
        "mostSeriousFlag": false,
        "lidsOffenceNumber": 42
      },
      {
        "id": 1583800,
        "offence": {
          "offenceCode": "PK78001",
          "statuteCode": "PK78",
          "description": "Take an indecent photograph / pseudo-photograph of a child"
        },
        "offenceDate": "2010-03-27",
        "chargeStatus": {
          "code": "A",
          "description": "Active"
        },
        "resultCode1": {
          "chargeStatus": "A",
          "code": "1501",
          "description": "Recall to Prison",
          "dispositionCode": "F",
          "conviction": true
        },
        "mostSeriousFlag": false,
        "lidsOffenceNumber": 43
      },
      {
        "id": 1583801,
        "offence": {
          "offenceCode": "PK78001",
          "statuteCode": "PK78",
          "description": "Take an indecent photograph / pseudo-photograph of a child"
        },
        "offenceDate": "2010-10-31",
        "chargeStatus": {
          "code": "A",
          "description": "Active"
        },
        "resultCode1": {
          "chargeStatus": "A",
          "code": "1501",
          "description": "Recall to Prison",
          "dispositionCode": "F",
          "conviction": true
        },
        "mostSeriousFlag": false,
        "lidsOffenceNumber": 44
      },
      {
        "id": 1583802,
        "offence": {
          "offenceCode": "PK78001",
          "statuteCode": "PK78",
          "description": "Take an indecent photograph / pseudo-photograph of a child"
        },
        "offenceDate": "2010-10-31",
        "chargeStatus": {
          "code": "A",
          "description": "Active"
        },
        "resultCode1": {
          "chargeStatus": "A",
          "code": "1501",
          "description": "Recall to Prison",
          "dispositionCode": "F",
          "conviction": true
        },
        "mostSeriousFlag": false,
        "lidsOffenceNumber": 45
      },
      {
        "id": 1583803,
        "offence": {
          "offenceCode": "PK78001",
          "statuteCode": "PK78",
          "description": "Take an indecent photograph / pseudo-photograph of a child"
        },
        "offenceDate": "2010-10-31",
        "chargeStatus": {
          "code": "A",
          "description": "Active"
        },
        "resultCode1": {
          "chargeStatus": "A",
          "code": "1501",
          "description": "Recall to Prison",
          "dispositionCode": "F",
          "conviction": true
        },
        "mostSeriousFlag": false,
        "lidsOffenceNumber": 46
      },
      {
        "id": 1583804,
        "offence": {
          "offenceCode": "PK78001",
          "statuteCode": "PK78",
          "description": "Take an indecent photograph / pseudo-photograph of a child"
        },
        "offenceDate": "2010-10-31",
        "chargeStatus": {
          "code": "A",
          "description": "Active"
        },
        "resultCode1": {
          "chargeStatus": "A",
          "code": "1501",
          "description": "Recall to Prison",
          "dispositionCode": "F",
          "conviction": true
        },
        "mostSeriousFlag": false,
        "lidsOffenceNumber": 47
      },
      {
        "id": 1583805,
        "offence": {
          "offenceCode": "PK78001",
          "statuteCode": "PK78",
          "description": "Take an indecent photograph / pseudo-photograph of a child"
        },
        "offenceDate": "2011-01-18",
        "chargeStatus": {
          "code": "A",
          "description": "Active"
        },
        "resultCode1": {
          "chargeStatus": "A",
          "code": "1501",
          "description": "Recall to Prison",
          "dispositionCode": "F",
          "conviction": true
        },
        "mostSeriousFlag": false,
        "lidsOffenceNumber": 48
      },
      {
        "id": 1583806,
        "offence": {
          "offenceCode": "PK78001",
          "statuteCode": "PK78",
          "description": "Take an indecent photograph / pseudo-photograph of a child"
        },
        "offenceDate": "2011-05-01",
        "chargeStatus": {
          "code": "A",
          "description": "Active"
        },
        "resultCode1": {
          "chargeStatus": "A",
          "code": "1501",
          "description": "Recall to Prison",
          "dispositionCode": "F",
          "conviction": true
        },
        "mostSeriousFlag": false,
        "lidsOffenceNumber": 49
      },
      {
        "id": 1583807,
        "offence": {
          "offenceCode": "PK78001",
          "statuteCode": "PK78",
          "description": "Take an indecent photograph / pseudo-photograph of a child"
        },
        "offenceDate": "2010-10-31",
        "chargeStatus": {
          "code": "A",
          "description": "Active"
        },
        "resultCode1": {
          "chargeStatus": "A",
          "code": "1501",
          "description": "Recall to Prison",
          "dispositionCode": "F",
          "conviction": true
        },
        "mostSeriousFlag": false,
        "lidsOffenceNumber": 50
      },
      {
        "id": 1583808,
        "offence": {
          "offenceCode": "PK78001",
          "statuteCode": "PK78",
          "description": "Take an indecent photograph / pseudo-photograph of a child"
        },
        "offenceDate": "2011-03-27",
        "chargeStatus": {
          "code": "A",
          "description": "Active"
        },
        "resultCode1": {
          "chargeStatus": "A",
          "code": "1501",
          "description": "Recall to Prison",
          "dispositionCode": "F",
          "conviction": true
        },
        "mostSeriousFlag": false,
        "lidsOffenceNumber": 51
      },
      {
        "id": 1583809,
        "offence": {
          "offenceCode": "PK78001",
          "statuteCode": "PK78",
          "description": "Take an indecent photograph / pseudo-photograph of a child"
        },
        "offenceDate": "2011-05-23",
        "chargeStatus": {
          "code": "A",
          "description": "Active"
        },
        "resultCode1": {
          "chargeStatus": "A",
          "code": "1501",
          "description": "Recall to Prison",
          "dispositionCode": "F",
          "conviction": true
        },
        "mostSeriousFlag": false,
        "lidsOffenceNumber": 52
      },
      {
        "id": 1583810,
        "offence": {
          "offenceCode": "PK78001",
          "statuteCode": "PK78",
          "description": "Take an indecent photograph / pseudo-photograph of a child"
        },
        "offenceDate": "2010-05-28",
        "chargeStatus": {
          "code": "A",
          "description": "Active"
        },
        "resultCode1": {
          "chargeStatus": "A",
          "code": "1501",
          "description": "Recall to Prison",
          "dispositionCode": "F",
          "conviction": true
        },
        "mostSeriousFlag": false,
        "lidsOffenceNumber": 53
      },
      {
        "id": 1583811,
        "offence": {
          "offenceCode": "PK78001",
          "statuteCode": "PK78",
          "description": "Take an indecent photograph / pseudo-photograph of a child"
        },
        "offenceDate": "2006-01-01",
        "offenceEndDate": "2011-06-30",
        "chargeStatus": {
          "code": "A",
          "description": "Active"
        },
        "resultCode1": {
          "chargeStatus": "A",
          "code": "1501",
          "description": "Recall to Prison",
          "dispositionCode": "F",
          "conviction": true
        },
        "mostSeriousFlag": false,
        "lidsOffenceNumber": 54
      }
    ],
    "caseInfoNumbers": [
      {
        "type": "CASE/INFO#",
        "reference": "T20117152",
        "createDateTime": "2012-02-21T11:10:00.029523",
        "auditModuleName": "OCDCCASE"
      },
      {
        "type": "CASE/INFO#",
        "reference": "STD LR",
        "createDateTime": "2025-01-27T08:04:02.835476",
        "auditModuleName": "OCDCCASE"
      }
    ],
    "sentences": [
      {
        "bookingId": 576330,
        "sentenceSeq": 9,
        "status": "A",
        "calculationType": {
          "code": "LR_ORA",
          "description": "ORA Licence Recall"
        },
        "category": {
          "code": "2003",
          "description": "2003 Act"
        },
        "startDate": "2012-03-23",
        "courtOrder": {
          "id": 556349,
          "eventId": 110006913,
          "courtDate": "2012-03-23",
          "issuingCourt": "PBORCC",
          "orderType": "AUTO",
          "orderStatus": "A",
          "nonReportFlag": false,
          "sentencePurposes": []
        },
        "endDate": "2013-03-22",
        "caseId": 660258,
        "crdCalculatedDate": "2012-09-21",
        "sedCalculatedDate": "2013-03-22",
        "prrdCalculatedDate": "2013-03-22",
        "tusedCalculatedDate": "2013-09-21",
        "aggAdjustDays": 0,
        "sentenceLevel": "IND",
        "extendedDays": 0,
        "statusUpdateReason": "A",
        "dischargeDate": "2013-03-22",
        "lineSequence": 1,
        "hdcExclusionFlag": true,
        "hdcExclusionReason": "PIMMS1",
        "createdDateTime": "2025-01-31T10:24:06.99671",
        "createdByUsername": "EQY54N",
        "sentenceTerms": [
          {
            "termSequence": 1,
            "sentenceTermType": {
              "code": "IMP",
              "description": "Imprisonment"
            },
            "years": 1,
            "startDate": "2012-03-23",
            "endDate": "2013-03-22",
            "lifeSentenceFlag": false
          }
        ],
        "offenderCharges": [
          {
            "id": 1583801,
            "offence": {
              "offenceCode": "PK78001",
              "statuteCode": "PK78",
              "description": "Take an indecent photograph / pseudo-photograph of a child"
            },
            "offenceDate": "2010-10-31",
            "chargeStatus": {
              "code": "A",
              "description": "Active"
            },
            "resultCode1": {
              "chargeStatus": "A",
              "code": "1501",
              "description": "Recall to Prison",
              "dispositionCode": "F",
              "conviction": true
            },
            "mostSeriousFlag": false,
            "lidsOffenceNumber": 44
          },
          {
            "id": 1583802,
            "offence": {
              "offenceCode": "PK78001",
              "statuteCode": "PK78",
              "description": "Take an indecent photograph / pseudo-photograph of a child"
            },
            "offenceDate": "2010-10-31",
            "chargeStatus": {
              "code": "A",
              "description": "Active"
            },
            "resultCode1": {
              "chargeStatus": "A",
              "code": "1501",
              "description": "Recall to Prison",
              "dispositionCode": "F",
              "conviction": true
            },
            "mostSeriousFlag": false,
            "lidsOffenceNumber": 45
          },
          {
            "id": 1583803,
            "offence": {
              "offenceCode": "PK78001",
              "statuteCode": "PK78",
              "description": "Take an indecent photograph / pseudo-photograph of a child"
            },
            "offenceDate": "2010-10-31",
            "chargeStatus": {
              "code": "A",
              "description": "Active"
            },
            "resultCode1": {
              "chargeStatus": "A",
              "code": "1501",
              "description": "Recall to Prison",
              "dispositionCode": "F",
              "conviction": true
            },
            "mostSeriousFlag": false,
            "lidsOffenceNumber": 46
          },
          {
            "id": 1583804,
            "offence": {
              "offenceCode": "PK78001",
              "statuteCode": "PK78",
              "description": "Take an indecent photograph / pseudo-photograph of a child"
            },
            "offenceDate": "2010-10-31",
            "chargeStatus": {
              "code": "A",
              "description": "Active"
            },
            "resultCode1": {
              "chargeStatus": "A",
              "code": "1501",
              "description": "Recall to Prison",
              "dispositionCode": "F",
              "conviction": true
            },
            "mostSeriousFlag": false,
            "lidsOffenceNumber": 47
          },
          {
            "id": 1583805,
            "offence": {
              "offenceCode": "PK78001",
              "statuteCode": "PK78",
              "description": "Take an indecent photograph / pseudo-photograph of a child"
            },
            "offenceDate": "2011-01-18",
            "chargeStatus": {
              "code": "A",
              "description": "Active"
            },
            "resultCode1": {
              "chargeStatus": "A",
              "code": "1501",
              "description": "Recall to Prison",
              "dispositionCode": "F",
              "conviction": true
            },
            "mostSeriousFlag": false,
            "lidsOffenceNumber": 48
          },
          {
            "id": 1583806,
            "offence": {
              "offenceCode": "PK78001",
              "statuteCode": "PK78",
              "description": "Take an indecent photograph / pseudo-photograph of a child"
            },
            "offenceDate": "2011-05-01",
            "chargeStatus": {
              "code": "A",
              "description": "Active"
            },
            "resultCode1": {
              "chargeStatus": "A",
              "code": "1501",
              "description": "Recall to Prison",
              "dispositionCode": "F",
              "conviction": true
            },
            "mostSeriousFlag": false,
            "lidsOffenceNumber": 49
          },
          {
            "id": 1583807,
            "offence": {
              "offenceCode": "PK78001",
              "statuteCode": "PK78",
              "description": "Take an indecent photograph / pseudo-photograph of a child"
            },
            "offenceDate": "2010-10-31",
            "chargeStatus": {
              "code": "A",
              "description": "Active"
            },
            "resultCode1": {
              "chargeStatus": "A",
              "code": "1501",
              "description": "Recall to Prison",
              "dispositionCode": "F",
              "conviction": true
            },
            "mostSeriousFlag": false,
            "lidsOffenceNumber": 50
          },
          {
            "id": 1583808,
            "offence": {
              "offenceCode": "PK78001",
              "statuteCode": "PK78",
              "description": "Take an indecent photograph / pseudo-photograph of a child"
            },
            "offenceDate": "2011-03-27",
            "chargeStatus": {
              "code": "A",
              "description": "Active"
            },
            "resultCode1": {
              "chargeStatus": "A",
              "code": "1501",
              "description": "Recall to Prison",
              "dispositionCode": "F",
              "conviction": true
            },
            "mostSeriousFlag": false,
            "lidsOffenceNumber": 51
          },
          {
            "id": 1583809,
            "offence": {
              "offenceCode": "PK78001",
              "statuteCode": "PK78",
              "description": "Take an indecent photograph / pseudo-photograph of a child"
            },
            "offenceDate": "2011-05-23",
            "chargeStatus": {
              "code": "A",
              "description": "Active"
            },
            "resultCode1": {
              "chargeStatus": "A",
              "code": "1501",
              "description": "Recall to Prison",
              "dispositionCode": "F",
              "conviction": true
            },
            "mostSeriousFlag": false,
            "lidsOffenceNumber": 52
          },
          {
            "id": 1583810,
            "offence": {
              "offenceCode": "PK78001",
              "statuteCode": "PK78",
              "description": "Take an indecent photograph / pseudo-photograph of a child"
            },
            "offenceDate": "2010-05-28",
            "chargeStatus": {
              "code": "A",
              "description": "Active"
            },
            "resultCode1": {
              "chargeStatus": "A",
              "code": "1501",
              "description": "Recall to Prison",
              "dispositionCode": "F",
              "conviction": true
            },
            "mostSeriousFlag": false,
            "lidsOffenceNumber": 53
          },
          {
            "id": 1583811,
            "offence": {
              "offenceCode": "PK78001",
              "statuteCode": "PK78",
              "description": "Take an indecent photograph / pseudo-photograph of a child"
            },
            "offenceDate": "2006-01-01",
            "offenceEndDate": "2011-06-30",
            "chargeStatus": {
              "code": "A",
              "description": "Active"
            },
            "resultCode1": {
              "chargeStatus": "A",
              "code": "1501",
              "description": "Recall to Prison",
              "dispositionCode": "F",
              "conviction": true
            },
            "mostSeriousFlag": false,
            "lidsOffenceNumber": 54
          }
        ],
        "prisonId": "FWI"
      },
      {
        "bookingId": 576330,
        "sentenceSeq": 10,
        "status": "A",
        "calculationType": {
          "code": "LR",
          "description": "Licence Recall"
        },
        "category": {
          "code": "2003",
          "description": "2003 Act"
        },
        "startDate": "2012-03-23",
        "courtOrder": {
          "id": 556349,
          "eventId": 110006913,
          "courtDate": "2012-03-23",
          "issuingCourt": "PBORCC",
          "orderType": "AUTO",
          "orderStatus": "A",
          "nonReportFlag": false,
          "sentencePurposes": []
        },
        "endDate": "2017-03-22",
        "caseId": 660258,
        "crdCalculatedDate": "2014-09-21",
        "sedCalculatedDate": "2017-03-22",
        "prrdCalculatedDate": "2017-03-22",
        "aggAdjustDays": 0,
        "sentenceLevel": "IND",
        "extendedDays": 0,
        "statusUpdateReason": "A",
        "dischargeDate": "2017-03-22",
        "lineSequence": 2,
        "hdcExclusionFlag": true,
        "hdcExclusionReason": "PIMMS1",
        "createdDateTime": "2025-01-31T10:26:44.691427",
        "createdByUsername": "EQY54N",
        "sentenceTerms": [
          {
            "termSequence": 1,
            "sentenceTermType": {
              "code": "IMP",
              "description": "Imprisonment"
            },
            "years": 5,
            "startDate": "2012-03-23",
            "endDate": "2017-03-22",
            "lifeSentenceFlag": false
          }
        ],
        "offenderCharges": [
          {
            "id": 1453012,
            "offence": {
              "offenceCode": "PK78001-008NA",
              "statuteCode": "ZZ",
              "description": "INDECENT PHOTOGRAPHS OF CHILDREN"
            },
            "offenceDate": "2010-10-31",
            "chargeStatus": {
              "code": "A",
              "description": "Active"
            },
            "resultCode1": {
              "chargeStatus": "A",
              "code": "1501",
              "description": "Recall to Prison",
              "dispositionCode": "F",
              "conviction": true
            },
            "mostSeriousFlag": false,
            "lidsOffenceNumber": 23
          },
          {
            "id": 1453016,
            "offence": {
              "offenceCode": "PK78001-008NA",
              "statuteCode": "ZZ",
              "description": "INDECENT PHOTOGRAPHS OF CHILDREN"
            },
            "offenceDate": "2010-03-28",
            "chargeStatus": {
              "code": "A",
              "description": "Active"
            },
            "resultCode1": {
              "chargeStatus": "A",
              "code": "1501",
              "description": "Recall to Prison",
              "dispositionCode": "F",
              "conviction": true
            },
            "mostSeriousFlag": false,
            "lidsOffenceNumber": 15
          },
          {
            "id": 1453017,
            "offence": {
              "offenceCode": "PK78001-008NA",
              "statuteCode": "ZZ",
              "description": "INDECENT PHOTOGRAPHS OF CHILDREN"
            },
            "offenceDate": "2011-06-30",
            "chargeStatus": {
              "code": "A",
              "description": "Active"
            },
            "resultCode1": {
              "chargeStatus": "A",
              "code": "1501",
              "description": "Recall to Prison",
              "dispositionCode": "F",
              "conviction": true
            },
            "mostSeriousFlag": false,
            "lidsOffenceNumber": 27
          },
          {
            "id": 1453019,
            "offence": {
              "offenceCode": "PK78001-008NA",
              "statuteCode": "ZZ",
              "description": "INDECENT PHOTOGRAPHS OF CHILDREN"
            },
            "offenceDate": "2011-03-27",
            "chargeStatus": {
              "code": "A",
              "description": "Active"
            },
            "resultCode1": {
              "chargeStatus": "A",
              "code": "1501",
              "description": "Recall to Prison",
              "dispositionCode": "F",
              "conviction": true
            },
            "mostSeriousFlag": false,
            "lidsOffenceNumber": 24
          },
          {
            "id": 1453020,
            "offence": {
              "offenceCode": "SX56025-029N",
              "statuteCode": "ZZ",
              "description": "INDECENT ASSAULT ON A CHILD"
            },
            "offenceDate": "2003-10-17",
            "chargeStatus": {
              "code": "A",
              "description": "Active"
            },
            "resultCode1": {
              "chargeStatus": "A",
              "code": "1501",
              "description": "Recall to Prison",
              "dispositionCode": "F",
              "conviction": true
            },
            "mostSeriousFlag": false,
            "lidsOffenceNumber": 10
          },
          {
            "id": 1453021,
            "offence": {
              "offenceCode": "PK78001-008NA",
              "statuteCode": "ZZ",
              "description": "INDECENT PHOTOGRAPHS OF CHILDREN"
            },
            "offenceDate": "2011-05-01",
            "chargeStatus": {
              "code": "A",
              "description": "Active"
            },
            "resultCode1": {
              "chargeStatus": "A",
              "code": "1501",
              "description": "Recall to Prison",
              "dispositionCode": "F",
              "conviction": true
            },
            "mostSeriousFlag": false,
            "lidsOffenceNumber": 21
          },
          {
            "id": 1453023,
            "offence": {
              "offenceCode": "PK78001-008NA",
              "statuteCode": "ZZ",
              "description": "INDECENT PHOTOGRAPHS OF CHILDREN"
            },
            "offenceDate": "2010-10-31",
            "chargeStatus": {
              "code": "A",
              "description": "Active"
            },
            "resultCode1": {
              "chargeStatus": "A",
              "code": "1501",
              "description": "Recall to Prison",
              "dispositionCode": "F",
              "conviction": true
            },
            "mostSeriousFlag": false,
            "lidsOffenceNumber": 17
          },
          {
            "id": 1453024,
            "offence": {
              "offenceCode": "PK78001-008NA",
              "statuteCode": "ZZ",
              "description": "INDECENT PHOTOGRAPHS OF CHILDREN"
            },
            "offenceDate": "2011-05-23",
            "chargeStatus": {
              "code": "A",
              "description": "Active"
            },
            "resultCode1": {
              "chargeStatus": "A",
              "code": "1501",
              "description": "Recall to Prison",
              "dispositionCode": "F",
              "conviction": true
            },
            "mostSeriousFlag": false,
            "lidsOffenceNumber": 25
          },
          {
            "id": 1453030,
            "offence": {
              "offenceCode": "PK78001-008NA",
              "statuteCode": "ZZ",
              "description": "INDECENT PHOTOGRAPHS OF CHILDREN"
            },
            "offenceDate": "2011-05-01",
            "chargeStatus": {
              "code": "A",
              "description": "Active"
            },
            "resultCode1": {
              "chargeStatus": "A",
              "code": "1501",
              "description": "Recall to Prison",
              "dispositionCode": "F",
              "conviction": true
            },
            "mostSeriousFlag": false,
            "lidsOffenceNumber": 22
          },
          {
            "id": 1453031,
            "offence": {
              "offenceCode": "PK78001-008NA",
              "statuteCode": "ZZ",
              "description": "INDECENT PHOTOGRAPHS OF CHILDREN"
            },
            "offenceDate": "2010-10-31",
            "chargeStatus": {
              "code": "A",
              "description": "Active"
            },
            "resultCode1": {
              "chargeStatus": "A",
              "code": "1501",
              "description": "Recall to Prison",
              "dispositionCode": "F",
              "conviction": true
            },
            "mostSeriousFlag": false,
            "lidsOffenceNumber": 20
          },
          {
            "id": 1453032,
            "offence": {
              "offenceCode": "PK78001-008NA",
              "statuteCode": "ZZ",
              "description": "INDECENT PHOTOGRAPHS OF CHILDREN"
            },
            "offenceDate": "2010-03-27",
            "chargeStatus": {
              "code": "A",
              "description": "Active"
            },
            "resultCode1": {
              "chargeStatus": "A",
              "code": "1501",
              "description": "Recall to Prison",
              "dispositionCode": "F",
              "conviction": true
            },
            "mostSeriousFlag": false,
            "lidsOffenceNumber": 16
          },
          {
            "id": 1453033,
            "offence": {
              "offenceCode": "PK78001-008NA",
              "statuteCode": "ZZ",
              "description": "INDECENT PHOTOGRAPHS OF CHILDREN"
            },
            "offenceDate": "2010-10-31",
            "chargeStatus": {
              "code": "A",
              "description": "Active"
            },
            "resultCode1": {
              "chargeStatus": "A",
              "code": "1501",
              "description": "Recall to Prison",
              "dispositionCode": "F",
              "conviction": true
            },
            "mostSeriousFlag": false,
            "lidsOffenceNumber": 19
          },
          {
            "id": 1453034,
            "offence": {
              "offenceCode": "PK78001-008NA",
              "statuteCode": "ZZ",
              "description": "INDECENT PHOTOGRAPHS OF CHILDREN"
            },
            "offenceDate": "2010-05-28",
            "chargeStatus": {
              "code": "A",
              "description": "Active"
            },
            "resultCode1": {
              "chargeStatus": "A",
              "code": "1501",
              "description": "Recall to Prison",
              "dispositionCode": "F",
              "conviction": true
            },
            "mostSeriousFlag": false,
            "lidsOffenceNumber": 26
          },
          {
            "id": 1453035,
            "offence": {
              "offenceCode": "PK78001-008NA",
              "statuteCode": "ZZ",
              "description": "INDECENT PHOTOGRAPHS OF CHILDREN"
            },
            "offenceDate": "2010-10-31",
            "chargeStatus": {
              "code": "A",
              "description": "Active"
            },
            "resultCode1": {
              "chargeStatus": "A",
              "code": "1501",
              "description": "Recall to Prison",
              "dispositionCode": "F",
              "conviction": true
            },
            "mostSeriousFlag": false,
            "lidsOffenceNumber": 18
          },
          {
            "id": 1453038,
            "offence": {
              "offenceCode": "PK78001-008NA",
              "statuteCode": "ZZ",
              "description": "INDECENT PHOTOGRAPHS OF CHILDREN"
            },
            "offenceDate": "2011-06-22",
            "chargeStatus": {
              "code": "A",
              "description": "Active"
            },
            "resultCode1": {
              "chargeStatus": "A",
              "code": "1501",
              "description": "Recall to Prison",
              "dispositionCode": "F",
              "conviction": true
            },
            "mostSeriousFlag": false,
            "lidsOffenceNumber": 28
          },
          {
            "id": 1583799,
            "offence": {
              "offenceCode": "PK78001",
              "statuteCode": "PK78",
              "description": "Take an indecent photograph / pseudo-photograph of a child"
            },
            "offenceDate": "2010-03-28",
            "chargeStatus": {
              "code": "A",
              "description": "Active"
            },
            "resultCode1": {
              "chargeStatus": "A",
              "code": "1501",
              "description": "Recall to Prison",
              "dispositionCode": "F",
              "conviction": true
            },
            "mostSeriousFlag": false,
            "lidsOffenceNumber": 42
          },
          {
            "id": 1583800,
            "offence": {
              "offenceCode": "PK78001",
              "statuteCode": "PK78",
              "description": "Take an indecent photograph / pseudo-photograph of a child"
            },
            "offenceDate": "2010-03-27",
            "chargeStatus": {
              "code": "A",
              "description": "Active"
            },
            "resultCode1": {
              "chargeStatus": "A",
              "code": "1501",
              "description": "Recall to Prison",
              "dispositionCode": "F",
              "conviction": true
            },
            "mostSeriousFlag": false,
            "lidsOffenceNumber": 43
          }
        ],
        "prisonId": "FWI"
      },
      {
        "bookingId": 576330,
        "sentenceSeq": 11,
        "status": "A",
        "calculationType": {
          "code": "LR",
          "description": "Licence Recall"
        },
        "category": {
          "code": "1991",
          "description": "1991 Act"
        },
        "startDate": "2012-09-22",
        "courtOrder": {
          "id": 556349,
          "eventId": 110006913,
          "courtDate": "2012-03-23",
          "issuingCourt": "PBORCC",
          "orderType": "AUTO",
          "orderStatus": "A",
          "nonReportFlag": false,
          "sentencePurposes": []
        },
        "consecSequence": 9,
        "endDate": "2031-09-21",
        "caseId": 660258,
        "pedCalculatedDate": "2022-03-23",
        "npdCalculatedDate": "2025-05-22",
        "ledCalculatedDate": "2026-12-22",
        "sedCalculatedDate": "2031-09-21",
        "aggAdjustDays": 0,
        "sentenceLevel": "IND",
        "extendedDays": 0,
        "statusUpdateReason": "A",
        "dischargeDate": "2031-09-21",
        "lineSequence": 3,
        "hdcExclusionFlag": true,
        "hdcExclusionReason": "PIMMS1",
        "createdDateTime": "2025-01-31T10:28:10.230215",
        "createdByUsername": "EQY54N",
        "sentenceTerms": [
          {
            "termSequence": 1,
            "sentenceTermType": {
              "code": "IMP",
              "description": "Imprisonment"
            },
            "years": 19,
            "startDate": "2012-09-22",
            "endDate": "2031-09-21",
            "lifeSentenceFlag": false
          }
        ],
        "offenderCharges": [
          {
            "id": 1453013,
            "offence": {
              "offenceCode": "SX03013-014N",
              "statuteCode": "ZZ",
              "description": "RAPE OF A CHILD U13 (SOA 2003 s5)"
            },
            "offenceDate": "2006-10-17",
            "chargeStatus": {
              "code": "A",
              "description": "Active"
            },
            "resultCode1": {
              "chargeStatus": "A",
              "code": "1501",
              "description": "Recall to Prison",
              "dispositionCode": "F",
              "conviction": true
            },
            "mostSeriousFlag": false,
            "lidsOffenceNumber": 7
          },
          {
            "id": 1453014,
            "offence": {
              "offenceCode": "SX56070-072N",
              "statuteCode": "ZZ",
              "description": "RAPE ON CHILD (SOA 1956 s69)"
            },
            "offenceDate": "2001-10-17",
            "chargeStatus": {
              "code": "A",
              "description": "Active"
            },
            "resultCode1": {
              "chargeStatus": "A",
              "code": "1501",
              "description": "Recall to Prison",
              "dispositionCode": "F",
              "conviction": true
            },
            "mostSeriousFlag": false,
            "lidsOffenceNumber": 1
          },
          {
            "id": 1453015,
            "offence": {
              "offenceCode": "SX56070-072N",
              "statuteCode": "ZZ",
              "description": "RAPE ON CHILD (SOA 1956 s69)"
            },
            "offenceDate": "2004-04-30",
            "chargeStatus": {
              "code": "A",
              "description": "Active"
            },
            "resultCode1": {
              "chargeStatus": "A",
              "code": "1501",
              "description": "Recall to Prison",
              "dispositionCode": "F",
              "conviction": true
            },
            "mostSeriousFlag": false,
            "lidsOffenceNumber": 4
          },
          {
            "id": 1453018,
            "offence": {
              "offenceCode": "SX03013-014N",
              "statuteCode": "ZZ",
              "description": "RAPE OF A CHILD U13 (SOA 2003 s5)"
            },
            "offenceDate": "2005-10-17",
            "chargeStatus": {
              "code": "A",
              "description": "Active"
            },
            "resultCode1": {
              "chargeStatus": "A",
              "code": "1501",
              "description": "Recall to Prison",
              "dispositionCode": "F",
              "conviction": true
            },
            "mostSeriousFlag": false,
            "lidsOffenceNumber": 6
          },
          {
            "id": 1453022,
            "offence": {
              "offenceCode": "SX03015-016N",
              "statuteCode": "ZZ",
              "description": "ASSAULT OF A CHILD U13 BY PENETRATION (SOA 2003 s6"
            },
            "offenceDate": "2006-10-17",
            "chargeStatus": {
              "code": "A",
              "description": "Active"
            },
            "resultCode1": {
              "chargeStatus": "A",
              "code": "1501",
              "description": "Recall to Prison",
              "dispositionCode": "F",
              "conviction": true
            },
            "mostSeriousFlag": false,
            "lidsOffenceNumber": 14
          },
          {
            "id": 1453025,
            "offence": {
              "offenceCode": "SX03013-014N",
              "statuteCode": "ZZ",
              "description": "RAPE OF A CHILD U13 (SOA 2003 s5)"
            },
            "offenceDate": "2004-10-17",
            "chargeStatus": {
              "code": "A",
              "description": "Active"
            },
            "resultCode1": {
              "chargeStatus": "A",
              "code": "1501",
              "description": "Recall to Prison",
              "dispositionCode": "F",
              "conviction": true
            },
            "mostSeriousFlag": false,
            "lidsOffenceNumber": 5
          },
          {
            "id": 1453026,
            "offence": {
              "offenceCode": "SX56025-029N",
              "statuteCode": "ZZ",
              "description": "INDECENT ASSAULT ON A CHILD"
            },
            "offenceDate": "2004-04-30",
            "chargeStatus": {
              "code": "A",
              "description": "Active"
            },
            "resultCode1": {
              "chargeStatus": "A",
              "code": "1501",
              "description": "Recall to Prison",
              "dispositionCode": "F",
              "conviction": true
            },
            "mostSeriousFlag": false,
            "lidsOffenceNumber": 11
          },
          {
            "id": 1453027,
            "offence": {
              "offenceCode": "SX03015-016N",
              "statuteCode": "ZZ",
              "description": "ASSAULT OF A CHILD U13 BY PENETRATION (SOA 2003 s6"
            },
            "offenceDate": "2005-10-17",
            "chargeStatus": {
              "code": "A",
              "description": "Active"
            },
            "resultCode1": {
              "chargeStatus": "A",
              "code": "1501",
              "description": "Recall to Prison",
              "dispositionCode": "F",
              "conviction": true
            },
            "mostSeriousFlag": false,
            "lidsOffenceNumber": 12
          },
          {
            "id": 1453028,
            "offence": {
              "offenceCode": "SX03015-016N",
              "statuteCode": "ZZ",
              "description": "ASSAULT OF A CHILD U13 BY PENETRATION (SOA 2003 s6"
            },
            "offenceDate": "2005-10-17",
            "chargeStatus": {
              "code": "A",
              "description": "Active"
            },
            "resultCode1": {
              "chargeStatus": "A",
              "code": "1501",
              "description": "Recall to Prison",
              "dispositionCode": "F",
              "conviction": true
            },
            "mostSeriousFlag": false,
            "lidsOffenceNumber": 13
          },
          {
            "id": 1453029,
            "offence": {
              "offenceCode": "SX56070-072N",
              "statuteCode": "ZZ",
              "description": "RAPE ON CHILD (SOA 1956 s69)"
            },
            "offenceDate": "2002-10-17",
            "chargeStatus": {
              "code": "A",
              "description": "Active"
            },
            "resultCode1": {
              "chargeStatus": "A",
              "code": "1501",
              "description": "Recall to Prison",
              "dispositionCode": "F",
              "conviction": true
            },
            "mostSeriousFlag": false,
            "lidsOffenceNumber": 2
          },
          {
            "id": 1453036,
            "offence": {
              "offenceCode": "SX56070-072N",
              "statuteCode": "ZZ",
              "description": "RAPE ON CHILD (SOA 1956 s69)"
            },
            "offenceDate": "2003-10-17",
            "chargeStatus": {
              "code": "A",
              "description": "Active"
            },
            "resultCode1": {
              "chargeStatus": "A",
              "code": "1501",
              "description": "Recall to Prison",
              "dispositionCode": "F",
              "conviction": true
            },
            "mostSeriousFlag": false,
            "lidsOffenceNumber": 3
          },
          {
            "id": 1453037,
            "offence": {
              "offenceCode": "SX56025-029N",
              "statuteCode": "ZZ",
              "description": "INDECENT ASSAULT ON A CHILD"
            },
            "offenceDate": "2001-10-17",
            "chargeStatus": {
              "code": "A",
              "description": "Active"
            },
            "resultCode1": {
              "chargeStatus": "A",
              "code": "1501",
              "description": "Recall to Prison",
              "dispositionCode": "F",
              "conviction": true
            },
            "mostSeriousFlag": false,
            "lidsOffenceNumber": 8
          },
          {
            "id": 1453039,
            "offence": {
              "offenceCode": "SX56025-029N",
              "statuteCode": "ZZ",
              "description": "INDECENT ASSAULT ON A CHILD"
            },
            "offenceDate": "2002-10-17",
            "chargeStatus": {
              "code": "A",
              "description": "Active"
            },
            "resultCode1": {
              "chargeStatus": "A",
              "code": "1501",
              "description": "Recall to Prison",
              "dispositionCode": "F",
              "conviction": true
            },
            "mostSeriousFlag": false,
            "lidsOffenceNumber": 9
          },
          {
            "id": 1583786,
            "offence": {
              "offenceCode": "SX03002",
              "statuteCode": "SX03",
              "description": "Rape a woman 16 years of age or over - SOA 2003"
            },
            "offenceDate": "2001-10-17",
            "offenceEndDate": "2002-10-17",
            "chargeStatus": {
              "code": "A",
              "description": "Active"
            },
            "resultCode1": {
              "chargeStatus": "A",
              "code": "1501",
              "description": "Recall to Prison",
              "dispositionCode": "F",
              "conviction": true
            },
            "mostSeriousFlag": false,
            "lidsOffenceNumber": 29
          },
          {
            "id": 1583787,
            "offence": {
              "offenceCode": "SX03002",
              "statuteCode": "SX03",
              "description": "Rape a woman 16 years of age or over - SOA 2003"
            },
            "offenceDate": "2002-10-17",
            "offenceEndDate": "2003-10-17",
            "chargeStatus": {
              "code": "A",
              "description": "Active"
            },
            "resultCode1": {
              "chargeStatus": "A",
              "code": "1501",
              "description": "Recall to Prison",
              "dispositionCode": "F",
              "conviction": true
            },
            "mostSeriousFlag": false,
            "lidsOffenceNumber": 30
          },
          {
            "id": 1583788,
            "offence": {
              "offenceCode": "SX03002",
              "statuteCode": "SX03",
              "description": "Rape a woman 16 years of age or over - SOA 2003"
            },
            "offenceDate": "2003-10-17",
            "offenceEndDate": "2004-04-30",
            "chargeStatus": {
              "code": "A",
              "description": "Active"
            },
            "resultCode1": {
              "chargeStatus": "A",
              "code": "1501",
              "description": "Recall to Prison",
              "dispositionCode": "F",
              "conviction": true
            },
            "mostSeriousFlag": false,
            "lidsOffenceNumber": 31
          },
          {
            "id": 1583789,
            "offence": {
              "offenceCode": "SX03013",
              "statuteCode": "SX03",
              "description": "Rape a girl under 13"
            },
            "offenceDate": "2004-05-01",
            "offenceEndDate": "2004-10-17",
            "chargeStatus": {
              "code": "A",
              "description": "Active"
            },
            "resultCode1": {
              "chargeStatus": "A",
              "code": "1501",
              "description": "Recall to Prison",
              "dispositionCode": "F",
              "conviction": true
            },
            "mostSeriousFlag": false,
            "lidsOffenceNumber": 32
          },
          {
            "id": 1583790,
            "offence": {
              "offenceCode": "SX03013",
              "statuteCode": "SX03",
              "description": "Rape a girl under 13"
            },
            "offenceDate": "2004-10-17",
            "offenceEndDate": "2005-10-17",
            "chargeStatus": {
              "code": "A",
              "description": "Active"
            },
            "resultCode1": {
              "chargeStatus": "A",
              "code": "1501",
              "description": "Recall to Prison",
              "dispositionCode": "F",
              "conviction": true
            },
            "mostSeriousFlag": false,
            "lidsOffenceNumber": 33
          },
          {
            "id": 1583791,
            "offence": {
              "offenceCode": "SX03013",
              "statuteCode": "SX03",
              "description": "Rape a girl under 13"
            },
            "offenceDate": "2005-10-17",
            "offenceEndDate": "2006-10-17",
            "chargeStatus": {
              "code": "A",
              "description": "Active"
            },
            "resultCode1": {
              "chargeStatus": "A",
              "code": "1501",
              "description": "Recall to Prison",
              "dispositionCode": "F",
              "conviction": true
            },
            "mostSeriousFlag": false,
            "lidsOffenceNumber": 34
          },
          {
            "id": 1583792,
            "offence": {
              "offenceCode": "SX56025-029N",
              "statuteCode": "ZZ",
              "description": "INDECENT ASSAULT ON A CHILD"
            },
            "offenceDate": "2000-10-17",
            "offenceEndDate": "2001-10-17",
            "chargeStatus": {
              "code": "A",
              "description": "Active"
            },
            "resultCode1": {
              "chargeStatus": "A",
              "code": "1501",
              "description": "Recall to Prison",
              "dispositionCode": "F",
              "conviction": true
            },
            "mostSeriousFlag": false,
            "lidsOffenceNumber": 35
          },
          {
            "id": 1583793,
            "offence": {
              "offenceCode": "SX56025-029N",
              "statuteCode": "ZZ",
              "description": "INDECENT ASSAULT ON A CHILD"
            },
            "offenceDate": "2001-10-17",
            "offenceEndDate": "2002-10-17",
            "chargeStatus": {
              "code": "A",
              "description": "Active"
            },
            "resultCode1": {
              "chargeStatus": "A",
              "code": "1501",
              "description": "Recall to Prison",
              "dispositionCode": "F",
              "conviction": true
            },
            "mostSeriousFlag": false,
            "lidsOffenceNumber": 36
          },
          {
            "id": 1583794,
            "offence": {
              "offenceCode": "SX56025-029N",
              "statuteCode": "ZZ",
              "description": "INDECENT ASSAULT ON A CHILD"
            },
            "offenceDate": "2002-10-17",
            "offenceEndDate": "2003-10-17",
            "chargeStatus": {
              "code": "A",
              "description": "Active"
            },
            "resultCode1": {
              "chargeStatus": "A",
              "code": "1501",
              "description": "Recall to Prison",
              "dispositionCode": "F",
              "conviction": true
            },
            "mostSeriousFlag": false,
            "lidsOffenceNumber": 37
          },
          {
            "id": 1583795,
            "offence": {
              "offenceCode": "SX56025-029N",
              "statuteCode": "ZZ",
              "description": "INDECENT ASSAULT ON A CHILD"
            },
            "offenceDate": "2003-10-17",
            "offenceEndDate": "2004-04-30",
            "chargeStatus": {
              "code": "A",
              "description": "Active"
            },
            "resultCode1": {
              "chargeStatus": "A",
              "code": "1501",
              "description": "Recall to Prison",
              "dispositionCode": "F",
              "conviction": true
            },
            "mostSeriousFlag": false,
            "lidsOffenceNumber": 38
          },
          {
            "id": 1583796,
            "offence": {
              "offenceCode": "SX03015",
              "statuteCode": "SX03",
              "description": "Assault a girl under 13 by penetration with a part of your body / a thing - SOA 2003"
            },
            "offenceDate": "2004-05-01",
            "offenceEndDate": "2004-10-17",
            "chargeStatus": {
              "code": "A",
              "description": "Active"
            },
            "resultCode1": {
              "chargeStatus": "A",
              "code": "1501",
              "description": "Recall to Prison",
              "dispositionCode": "F",
              "conviction": true
            },
            "mostSeriousFlag": false,
            "lidsOffenceNumber": 39
          },
          {
            "id": 1583797,
            "offence": {
              "offenceCode": "SX03015",
              "statuteCode": "SX03",
              "description": "Assault a girl under 13 by penetration with a part of your body / a thing - SOA 2003"
            },
            "offenceDate": "2004-10-17",
            "offenceEndDate": "2005-10-17",
            "chargeStatus": {
              "code": "A",
              "description": "Active"
            },
            "resultCode1": {
              "chargeStatus": "A",
              "code": "1501",
              "description": "Recall to Prison",
              "dispositionCode": "F",
              "conviction": true
            },
            "mostSeriousFlag": false,
            "lidsOffenceNumber": 40
          },
          {
            "id": 1583798,
            "offence": {
              "offenceCode": "SX03015",
              "statuteCode": "SX03",
              "description": "Assault a girl under 13 by penetration with a part of your body / a thing - SOA 2003"
            },
            "offenceDate": "2005-10-17",
            "offenceEndDate": "2006-10-17",
            "chargeStatus": {
              "code": "A",
              "description": "Active"
            },
            "resultCode1": {
              "chargeStatus": "A",
              "code": "1501",
              "description": "Recall to Prison",
              "dispositionCode": "F",
              "conviction": true
            },
            "mostSeriousFlag": false,
            "lidsOffenceNumber": 41
          }
        ],
        "prisonId": "FWI"
      }
    ]
  }"""
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
      lidsCaseNumber = 1,
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
              ),
              resultCode1 = OffenceResultCodeResponse(
                chargeStatus = "A",
                code = "G",
                description = "Guilty",
                dispositionCode = "F",
                conviction = true,
              ),
              mostSeriousFlag = true,
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
              ),
              resultCode1 = OffenceResultCodeResponse(
                chargeStatus = "A",
                code = "G",
                description = "Guilty",
                dispositionCode = "F",
                conviction = true,
              ),
              mostSeriousFlag = false,
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
              ),
              resultCode1 = OffenceResultCodeResponse(
                chargeStatus = "A",
                code = "1002",
                description = "Imprisonment",
                dispositionCode = "F",
                conviction = true,
              ),
              mostSeriousFlag = true,
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
              ),
              resultCode1 = OffenceResultCodeResponse(
                chargeStatus = "A",
                code = "1002",
                description = "Imprisonment",
                dispositionCode = "F",
                conviction = true,
              ),
              mostSeriousFlag = true,
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
              ),
              resultCode1 = OffenceResultCodeResponse(
                chargeStatus = "I",
                code = "2008",
                description = "Lie on File",
                dispositionCode = "I",
                conviction = false,
              ),
              mostSeriousFlag = false,
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
            ),
          ),
          prisonId = "BAI",
        ),
      ),
    )
  }
}
