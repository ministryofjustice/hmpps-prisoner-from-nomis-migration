package uk.gov.justice.digital.hmpps.prisonerfromnomismigration.agency

import com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor
import com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor
import com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.mockito.kotlin.check
import org.mockito.kotlin.eq
import org.mockito.kotlin.isNull
import org.mockito.kotlin.verify
import org.springframework.beans.factory.annotation.Autowired
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.agency.AgencyNomisApiMockServer.Companion.agencyResponse
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.agency.AgencyRegistersDpsApiExtension.Companion.agencyRegistersApi
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.agencyregisters.model.LegacyAgencyDto
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.integration.sendMessage

class AgencyRegistersSynchronisationIntTest : AgencyRegistersIntegrationTestBase() {
  private val agencyId = "SHEFCC"

  @Autowired
  private lateinit var nomisApiMock: AgencyNomisApiMockServer

  private val dpsApiMock = agencyRegistersApi

  @Nested
  @DisplayName("AGENCY_REGISTERS_UPDATED")
  inner class AgencyUpdated {
    @Nested
    inner class WhenUpdatedInDps {
      @BeforeEach
      fun setUp() {
        agencyRegistersOffenderEventsQueue.sendMessage(
          agencyRegisterEvent(
            eventType = "AGENCY_REGISTERS_UPDATED",
            agencyLocationId = agencyId,
            auditModuleName = "DPS_SYNCHRONISATION",
          ),
        ).also { waitForAnyProcessingToComplete() }
      }

      @Test
      fun `will track telemetry`() {
        verify(telemetryClient).trackEvent(
          eq("agency-synchronisation-updated-skipped"),
          check {
            assertThat(it["agencyId"]).isEqualTo(agencyId)
          },
          isNull(),
        )
      }

      @Test
      fun `will not call nomis or dps apis`() {
        nomisApiMock.verify(0, getRequestedFor(urlPathEqualTo("/agency/$agencyId")))
        dpsApiMock.verify(0, postRequestedFor(urlPathEqualTo("/legacy/sync/agency/id/$agencyId")))
      }
    }

    @Nested
    inner class WhenUpdatedInNomis {
      @BeforeEach
      fun setUp() {
        nomisApiMock.stubGetAgency(
          agencyId = agencyId,
          response = agencyResponse().copy(agencyId = agencyId),
        )
        dpsApiMock.stubSyncAgency(agencyId = agencyId)

        agencyRegistersOffenderEventsQueue.sendMessage(
          agencyRegisterEvent(
            eventType = "AGENCY_REGISTERS_UPDATED",
            agencyLocationId = agencyId,
          ),
        ).also { waitForAnyProcessingToComplete() }
      }

      @Test
      fun `will track telemetry`() {
        verify(telemetryClient).trackEvent(
          eq("agency-synchronisation-updated-success"),
          check {
            assertThat(it["agencyId"]).isEqualTo(agencyId)
          },
          isNull(),
        )
      }

      @Test
      fun `will retrieve agency details from nomis`() {
        nomisApiMock.verify(getRequestedFor(urlPathEqualTo("/agency/$agencyId")))
      }

      @Test
      fun `will sync the agency into DPS`() {
        dpsApiMock.verify(postRequestedFor(urlPathEqualTo("/legacy/sync/agency/id/$agencyId")))
      }

      @Test
      fun `will transform the agency data correctly`() {
        val request: LegacyAgencyDto =
          AgencyRegistersDpsApiExtension.getRequestBody(postRequestedFor(urlPathEqualTo("/legacy/sync/agency/id/$agencyId")))
        assertThat(request.name).isEqualTo("Sheffield Crown Court")
      }
    }
  }
}

fun agencyRegisterEvent(
  eventType: String,
  agencyLocationId: String,
  auditModuleName: String = "OCUAGY",
) = // language=JSON
  """{
    "MessageId": "ae06c49e-1f41-4b9f-b2f2-dcca610d02cd", "Type": "Notification", "Timestamp": "2019-10-21T14:01:18.500Z", 
    "Message": "{\"eventType\":\"$eventType\",\"eventDatetime\":\"2019-10-21T15:00:25.489964\",\"agencyLocationId\": \"$agencyLocationId\",\"auditModuleName\":\"$auditModuleName\",\"nomisEventType\":\"$eventType\" }",
    "TopicArn": "arn:aws:sns:eu-west-1:000000000000:offender_events", 
    "MessageAttributes": {
      "eventType": {"Type": "String", "Value": "$eventType"}, 
      "id": {"Type": "String", "Value": "8b07cbd9-0820-0a0f-c32f-a9429b618e0b"}, 
      "contentType": {"Type": "String", "Value": "text/plain;charset=UTF-8"}, 
      "timestamp": {"Type": "Number.java.lang.Long", "Value": "1571666478344"}
    }
}
  """.trimIndent()
