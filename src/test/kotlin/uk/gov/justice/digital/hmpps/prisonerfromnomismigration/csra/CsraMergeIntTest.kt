package uk.gov.justice.digital.hmpps.prisonerfromnomismigration.csra

import com.github.tomakehurst.wiremock.client.WireMock.putRequestedFor
import com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo
import org.assertj.core.api.Assertions.assertThat
import org.awaitility.kotlin.await
import org.awaitility.kotlin.untilAsserted
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.mockito.ArgumentMatchers.eq
import org.mockito.kotlin.check
import org.mockito.kotlin.isNull
import org.mockito.kotlin.verify
import org.springframework.beans.factory.annotation.Autowired
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.csra.CsraApiExtension.Companion.csraApi
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.helper.bookingMovedDomainEvent
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.helper.generateUUID
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.integration.sendMessage
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomismappings.model.CsraMappingDto
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.wiremock.withRequestBodyJsonPath

class CsraMergeIntTest(
  @Autowired private val csraMappingApiMockServer: CsraMappingApiMockServer,
  @Autowired private val csraNomisApiMockServer: CsraNomisApiMockServer,
) : CsraIntegrationTestBase() {
  @Nested
  inner class BookingMoved {
    private val uuid1 = generateUUID(1)
    private val uuid2 = generateUUID(2)

    @BeforeEach
    fun setUp() {
      csraMappingApiMockServer.stubUpdateMappingsByBookingId(
        listOf(
          CsraMappingDto(
            dpsCsraId = uuid1,
            nomisSequence = 1,
            offenderNo = "A1234AA",
            nomisBookingId = 1,
            mappingType = CsraMappingDto.MappingType.MIGRATED,
          ),
          CsraMappingDto(
            dpsCsraId = uuid2,
            nomisSequence = 2,
            offenderNo = "A1234AA",
            nomisBookingId = 1,
            mappingType = CsraMappingDto.MappingType.MIGRATED,
          ),
        ),
      )
      csraApi.stubMove()

      awsSqsCsraEventClient.sendMessage(
        csraEventQueueUrl,
        bookingMovedDomainEvent(
          bookingId = 12,
          movedToNomsNumber = "A1234BB",
          movedFromNomsNumber = "A1234AA",
        ),
      )
    }

    @Test
    fun `will correct the mappings between the DPS and NOMIS csras`() {
      await untilAsserted {
        csraMappingApiMockServer.verify(
          putRequestedFor(urlPathEqualTo("/mapping/csras/move/booking-id/12/from/A1234AA/to/A1234BB")),
        )
      }
      // ensure the process has finished before the test ends by checking the telemetry
      await untilAsserted {
        verify(telemetryClient).trackEvent(
          eq("csras-booking-moved-success"),
          check {
            assertThat(it["bookingId"]).isEqualTo("12")
            assertThat(it["movedToNomsNumber"]).isEqualTo("A1234BB")
            assertThat(it["movedFromNomsNumber"]).isEqualTo("A1234AA")
            assertThat(it["count"]).isEqualTo("2")
          },
          isNull(),
        )
      }
    }

    @Test
    fun `will correct the DPS csras`() {
      await untilAsserted {
        csraApi.verify(
          putRequestedFor(urlPathEqualTo("/nomis-sync/move/from/A1234AA/to/A1234BB"))
            .withRequestBodyJsonPath("$[0]", uuid1)
            .withRequestBodyJsonPath("$[1]", uuid2),
        )
      }
      // ensure the process has finished before the test ends by checking the telemetry
      await untilAsserted {
        verify(telemetryClient).trackEvent(
          eq("csras-booking-moved-success"),
          check {
            assertThat(it["bookingId"]).isEqualTo("12")
            assertThat(it["movedToNomsNumber"]).isEqualTo("A1234BB")
            assertThat(it["movedFromNomsNumber"]).isEqualTo("A1234AA")
            assertThat(it["count"]).isEqualTo("2")
          },
          isNull(),
        )
      }
    }
  }
}
