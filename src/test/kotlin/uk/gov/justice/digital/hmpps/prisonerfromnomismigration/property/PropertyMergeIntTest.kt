package uk.gov.justice.digital.hmpps.prisonerfromnomismigration.property

import com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor
import com.github.tomakehurst.wiremock.client.WireMock.putRequestedFor
import com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo
import org.assertj.core.api.Assertions.assertThat
import org.awaitility.kotlin.await
import org.awaitility.kotlin.untilAsserted
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.mockito.ArgumentMatchers.eq
import org.mockito.kotlin.check
import org.mockito.kotlin.isNull
import org.mockito.kotlin.verify
import org.springframework.beans.factory.annotation.Autowired
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.helper.bookingMovedDomainEvent
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.helper.generateUUID
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.integration.sendMessage
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomismappings.model.PropertyContainerMappingDto
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.property.PropertyApiExtension.Companion.propertyDpsApi
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.wiremock.withRequestBodyJsonPath

class PropertyMergeIntTest(

  @Autowired private val propertyMappingApiMockServer: PropertyMappingApiMockServer,
  @Autowired private val propertyNomisApiMockServer: PropertyNomisApiMockServer,
) : PropertyIntegrationTestBase() {

  @Nested
  inner class BookingMoved {
    private val uuid1 = generateUUID(1)
    private val uuid2 = generateUUID(2)

    @Test
    fun `will correct DPS property`() {
      propertyMappingApiMockServer.stubGetMappingsByBookingId(
        listOf(
          PropertyContainerMappingDto(
            dpsPropertyContainerId = uuid1,
            nomisPropertyContainerId = 1,
            bookingId = 1,
            mappingType = PropertyContainerMappingDto.MappingType.MIGRATED,
          ),
          PropertyContainerMappingDto(
            dpsPropertyContainerId = uuid2,
            nomisPropertyContainerId = 2,
            bookingId = 1,
            mappingType = PropertyContainerMappingDto.MappingType.MIGRATED,
          ),
        ),
      )
      propertyDpsApi.stubMove()

      awsSqsPropertyEventClient.sendMessage(
        propertyEventQueueUrl,
        bookingMovedDomainEvent(
          bookingId = 12,
          movedToNomsNumber = "A1234BB",
          movedFromNomsNumber = "A1234AA",
        ),
      )
      // ensure the process has finished by checking the telemetry
      await untilAsserted {
        verify(telemetryClient).trackEvent(
          eq("property-booking-moved-success"),
          check {
            assertThat(it["bookingId"]).isEqualTo("12")
            assertThat(it["movedToNomsNumber"]).isEqualTo("A1234BB")
            assertThat(it["movedFromNomsNumber"]).isEqualTo("A1234AA")
            assertThat(it["count"]).isEqualTo("2")
          },
          isNull(),
        )
      }

      propertyMappingApiMockServer.verify(
        getRequestedFor(urlPathEqualTo("/mapping/property/booking-id/12")),
      )
      propertyDpsApi.verify(
        putRequestedFor(urlPathEqualTo("/sync/property-containers/move/from/A1234AA/to/A1234BB"))
          .withRequestBodyJsonPath("$.[0]", uuid1)
          .withRequestBodyJsonPath("$.[1]", uuid2),
      )
    }

    @Test
    fun `will do nothing when no ids`() {
      propertyMappingApiMockServer.stubGetMappingsByBookingId(emptyList())

      awsSqsPropertyEventClient.sendMessage(
        propertyEventQueueUrl,
        bookingMovedDomainEvent(
          bookingId = 12,
          movedToNomsNumber = "A1234BB",
          movedFromNomsNumber = "A1234AA",
        ),
      )
      // ensure the process has finished by checking the telemetry
      await untilAsserted {
        verify(telemetryClient).trackEvent(
          eq("property-booking-moved-success"),
          check {
            assertThat(it["bookingId"]).isEqualTo("12")
            assertThat(it["movedToNomsNumber"]).isEqualTo("A1234BB")
            assertThat(it["movedFromNomsNumber"]).isEqualTo("A1234AA")
            assertThat(it["count"]).isEqualTo("0")
          },
          isNull(),
        )
      }

      propertyDpsApi.verify(
        0,
        putRequestedFor(urlPathEqualTo("/sync/property-containers/move/from/A1234AA/to/A1234BB")),
      )
    }
  }
}
