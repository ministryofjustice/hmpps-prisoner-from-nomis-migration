@file:OptIn(ExperimentalCoroutinesApi::class)

package uk.gov.justice.digital.hmpps.prisonerfromnomismigration.adjudications

import com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor
import com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.context.annotation.Import
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.adjudications.model.HearingMapping
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.helper.SpringAPIServiceTest
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomissync.model.AdjudicationCharge
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomissync.model.AdjudicationChargeResponse
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomissync.model.AdjudicationIncident
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomissync.model.AdjudicationOffence
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomissync.model.CodeDescription
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomissync.model.Hearing
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomissync.model.InternalLocation
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomissync.model.Staff
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.wiremock.AdjudicationsApiExtension.Companion.adjudicationsApi
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.wiremock.MappingApiExtension.Companion.mappingApi
import java.time.LocalDate
import java.time.LocalDateTime

@SpringAPIServiceTest
@Import(
  AdjudicationMappingCreator::class,
  AdjudicationsConfiguration::class,
  AdjudicationsService::class,
  AdjudicationsMappingService::class,
)
class AdjudicationMappingCreatorTest {
  @Autowired
  private lateinit var adjudicationMappingCreator: AdjudicationMappingCreator

  @Nested
  inner class WhenMappingAlreadyExists {
    @BeforeEach
    fun setUp() {
      mappingApi.stubFindNomisMapping(adjudicationNumber = 12345, chargeSequence = 1)
    }

    @Test
    fun `will return null when mapping already created`() = runTest {
      val mapping = adjudicationMappingCreator.createMigrationMappingResponse(
        nomisAdjudication(
          adjudicationNumber = 12345,
          chargeSequence = 1,
        ),
      )
      assertThat(mapping).isNull()
    }
  }

  @Nested
  inner class WhenMappingAbsent {
    @BeforeEach
    fun setUp() {
      mappingApi.stubFindNomisMappingWithError(adjudicationNumber = 12345, chargeSequence = 1, statusCode = 404)
    }

    @Nested
    inner class WhenMigratedMigrateDPSAdjudicationNotFound {
      @BeforeEach
      fun setUp() {
        adjudicationsApi.stubChargeGetWithError("12345-1", 404)
      }

      @Test
      fun `will attempt to find charge using both DPS and the migrated format`() = runTest {
        adjudicationsApi.stubChargeGet("12345", hearings = "[]")

        adjudicationMappingCreator.createMigrationMappingResponse(
          nomisAdjudication(
            adjudicationNumber = 12345,
            chargeSequence = 1,
          ),
        )

        adjudicationsApi.verify(getRequestedFor(urlEqualTo("/reported-adjudications/12345-1/v2")))
        adjudicationsApi.verify(getRequestedFor(urlEqualTo("/reported-adjudications/12345/v2")))
      }

      @Test
      fun `will throw exception if adjudication not found in DPS`() {
        // this scenario really shouldn't;t happen since we are responding to a duplicate being created
        adjudicationsApi.stubChargeGetWithError("12345", status = 404)

        assertThrows<IllegalStateException> {
          runBlocking {
            adjudicationMappingCreator.createMigrationMappingResponse(
              nomisAdjudication(
                adjudicationNumber = 12345,
                chargeSequence = 1,
              ),
            )
          }
        }
      }
    }

    @Nested
    inner class WhenDPSHasNoHearings {
      @BeforeEach
      fun setUp() {
        adjudicationsApi.stubChargeGet("12345-1", hearings = "[]", punishments = "[]")
      }

      @Test
      fun `will only map adjudication number`() = runTest {
        val mapping = adjudicationMappingCreator.createMigrationMappingResponse(
          nomisAdjudication(
            adjudicationNumber = 12345,
            chargeSequence = 1,
            hearings = listOf(),
          ),
        )
        assertThat(mapping).isNotNull
        assertThat(mapping?.chargeNumberMapping?.chargeNumber).isEqualTo("12345-1")
        assertThat(mapping?.chargeNumberMapping?.oicIncidentId).isEqualTo(12345)
        assertThat(mapping?.chargeNumberMapping?.offenceSequence).isEqualTo(1)
        assertThat(mapping?.hearingMappings).isEmpty()
        assertThat(mapping?.punishmentMappings).isEmpty()
      }

      @Test
      fun `will throw exception when NOMIS has a hearing`() = runTest {
        assertThrows<IllegalStateException> {
          adjudicationMappingCreator.createMigrationMappingResponse(
            nomisAdjudication(
              adjudicationNumber = 12345,
              chargeSequence = 1,
              hearings = listOf(
                nomisHearing(11, LocalDateTime.parse("2023-08-23T14:25:00")),
              ),
            ),
          )
        }
      }
    }

    @Nested
    inner class WhenDPSHasASingleHearing {
      @BeforeEach
      fun setUp() {
        adjudicationsApi.stubChargeGet(
          chargeNumber = "12345-1",
          hearings = """
            [
              ${dpsHearing(1, LocalDateTime.parse("2023-08-23T14:25:00"))}
            ]""",
        )
      }

      @Test
      fun `will map adjudication number and hearing`() = runTest {
        val mapping = adjudicationMappingCreator.createMigrationMappingResponse(
          nomisAdjudication(
            adjudicationNumber = 12345,
            chargeSequence = 1,
            hearings = listOf(
              nomisHearing(11, LocalDateTime.parse("2023-08-23T14:25:00")),
            ),
          ),
        )
        assertThat(mapping).isNotNull
        assertThat(mapping?.chargeNumberMapping?.chargeNumber).isEqualTo("12345-1")
        assertThat(mapping?.chargeNumberMapping?.oicIncidentId).isEqualTo(12345)
        assertThat(mapping?.chargeNumberMapping?.offenceSequence).isEqualTo(1)
        assertThat(mapping?.hearingMappings).containsExactly(HearingMapping(hearingId = 1, oicHearingId = 11))
        assertThat(mapping?.punishmentMappings).isEmpty()
      }

      @Test
      fun `will throw exception when NOMIS has no hearings`() = runTest {
        assertThrows<IllegalStateException> {
          adjudicationMappingCreator.createMigrationMappingResponse(
            nomisAdjudication(
              adjudicationNumber = 12345,
              chargeSequence = 1,
              hearings = listOf(),
            ),
          )
        }
      }
    }

    @Nested
    inner class WhenDPSHasMultipleHearings {
      @BeforeEach
      fun setUp() {
        adjudicationsApi.stubChargeGet(
          chargeNumber = "12345-1",
          hearings = """
            [
              ${dpsHearing(2, LocalDateTime.parse("2023-08-23T14:25:00"))},
              ${dpsHearing(1, LocalDateTime.parse("2021-08-23T14:25:00"))},
              ${dpsHearing(4, LocalDateTime.parse("2023-09-23T15:25:00"))},
              ${dpsHearing(3, LocalDateTime.parse("2023-09-23T14:25:00"))}
            ]""",
          punishments = "[]",
        )
      }

      @Test
      fun `will map adjudication number and hearing`() = runTest {
        val mapping = adjudicationMappingCreator.createMigrationMappingResponse(
          nomisAdjudication(
            adjudicationNumber = 12345,
            chargeSequence = 1,
            hearings = listOf(
              nomisHearing(11, LocalDateTime.parse("2021-08-23T14:25:00")),
              nomisHearing(12, LocalDateTime.parse("2023-08-23T14:25:00")),
              nomisHearing(14, LocalDateTime.parse("2023-09-23T15:25:00")),
              nomisHearing(13, LocalDateTime.parse("2023-09-23T14:25:00")),
            ),
          ),
        )
        assertThat(mapping).isNotNull
        assertThat(mapping?.chargeNumberMapping?.chargeNumber).isEqualTo("12345-1")
        assertThat(mapping?.chargeNumberMapping?.oicIncidentId).isEqualTo(12345)
        assertThat(mapping?.chargeNumberMapping?.offenceSequence).isEqualTo(1)
        assertThat(mapping?.hearingMappings).containsExactly(
          HearingMapping(hearingId = 1, oicHearingId = 11),
          HearingMapping(hearingId = 2, oicHearingId = 12),
          HearingMapping(hearingId = 3, oicHearingId = 13),
          HearingMapping(hearingId = 4, oicHearingId = 14),
        )
      }

      @Test
      fun `will throw exception when NOMIS has a different number of hearings`() = runTest {
        assertThrows<IllegalStateException> {
          adjudicationMappingCreator.createMigrationMappingResponse(
            nomisAdjudication(
              adjudicationNumber = 12345,
              chargeSequence = 1,
              hearings = listOf(
                nomisHearing(12, LocalDateTime.parse("2023-08-23T14:25:00")),
                nomisHearing(14, LocalDateTime.parse("2023-09-23T15:25:00")),
                nomisHearing(13, LocalDateTime.parse("2023-09-23T14:25:00")),
              ),
            ),
          )
        }
      }
    }
  }

  private fun nomisHearing(hearingId: Long, hearingDateTime: LocalDateTime) = Hearing(
    hearingId = hearingId,
    hearingResults = emptyList(),
    createdDateTime = "2021-03-01T10:00:00",
    createdByUsername = "JTEST_GEN",
    notifications = emptyList(),
    type = CodeDescription("INAD_ADULT", "Independent Adjudicator Hearing Adult"),
    hearingDate = hearingDateTime.toLocalDate(),
    hearingTime = hearingDateTime.toLocalTime().toString(),
    internalLocation = InternalLocation(
      locationId = 27187L,
      code = "MDI-1-1-1",
      description = "MDI-1-1-1",
    ),
  )

  private fun dpsHearing(hearingId: Long, hearingDateTime: LocalDateTime) =
    // language=JSON
    """
    {
                "id": $hearingId,
                "locationId": 27187,
                "dateTimeOfHearing": "$hearingDateTime",
                "oicHearingType": "GOV_ADULT",
                "agencyId": "MDI",
                "outcome": {
                      "id": 962,
                      "adjudicator": "JTEST_GEN",
                      "code": "COMPLETE",
                      "plea": "GUILTY"
                  }
            }
    """.trimIndent()

  private fun nomisAdjudication(
    adjudicationNumber: Long,
    chargeSequence: Int,
    hearings: List<Hearing> = emptyList(),
  ): AdjudicationChargeResponse {
    return AdjudicationChargeResponse(
      adjudicationSequence = chargeSequence,
      adjudicationNumber = adjudicationNumber,
      incident = AdjudicationIncident(
        adjudicationIncidentId = 98765L,
        reportingStaff = Staff(
          username = "LQY66C",
          staffId = 12643L,
          firstName = "SUZANNE",
          lastName = "O'BOYLE",
          createdByUsername = "PQY66C",
          dateAddedToIncident = LocalDate.now(),
        ),
        incidentDate = LocalDate.now(),
        incidentTime = "08:00:00",
        reportedDate = LocalDate.now(),
        reportedTime = "08:00:00",
        createdByUsername = "LQY66C",
        createdDateTime = "2014-02-21T08:03:11.145714",
        internalLocation = InternalLocation(
          locationId = 14447L,
          code = "CWING",
          description = "LEI-RES-CWING-CWING",
        ),
        incidentType = CodeDescription(
          code = "GOV",
          description = "Governor's Report",
        ),
        details = "Fight",
        prison = CodeDescription(
          code = "LEI",
          description = "LEEDS (HMP)",
        ),
        prisonerWitnesses = listOf(),
        prisonerVictims = listOf(),
        otherPrisonersInvolved = listOf(),
        reportingOfficers = listOf(),
        staffWitnesses = listOf(),
        staffVictims = listOf(),
        otherStaffInvolved = listOf(),
        repairs = listOf(),

      ),
      bookingId = 12345L,
      offenderNo = "A1234KL",
      gender = CodeDescription("F", "Female"),
      partyAddedDate = LocalDate.now(),
      charge = AdjudicationCharge(
        offence = AdjudicationOffence("51:22", description = "Disobeys any lawful order"),
        chargeSequence = chargeSequence,
      ),
      investigations = listOf(),
      hearings = hearings,
    )
  }
}
