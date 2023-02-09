package uk.gov.justice.digital.hmpps.prisonerfromnomismigration.incentives

import com.fasterxml.jackson.annotation.JsonInclude
import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.fasterxml.jackson.module.kotlin.registerKotlinModule
import io.mockk.Runs
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.junit.jupiter.MockitoExtension
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.helper.validIepCreatedMessage
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.listeners.EventFeatureSwitch

@ExtendWith(MockitoExtension::class)
internal class IncentivesPrisonOffenderEventListenerTest {
  private val incentivesSynchronisationService: IncentivesSynchronisationService = mockk()
  private val objectMapper: ObjectMapper = objectMapper()
  private val eventFeatureSwitch: EventFeatureSwitch = mockk()

  private val listener =
    IncentivesPrisonOffenderEventListener(
      incentivesSynchronisationService = incentivesSynchronisationService,
      eventFeatureSwitch = eventFeatureSwitch,
      objectMapper = objectMapper
    )

  @Nested
  inner class Incentives {
    @Nested
    inner class WhenEnabled {
      @BeforeEach
      internal fun setUp() {
        every {
          eventFeatureSwitch.isEnabled(any())
        } returns true
      }

      @Test
      internal fun `will call service with create incentive data`() {
        coEvery { incentivesSynchronisationService.synchroniseIncentive(any()) } just Runs

        listener.onMessage(
          message = validIepCreatedMessage()
        ).get()

        coVerify {
          incentivesSynchronisationService.synchroniseIncentive(
            any()
          )
        }
      }
    }

    @Nested
    inner class WhenDisabled {
      @BeforeEach
      internal fun setUp() {
        every {
          eventFeatureSwitch.isEnabled(any())
        } returns false
      }

      @Test
      internal fun `will not call service when disabled`() {

        listener.onMessage(
          message = validIepCreatedMessage()
        )

        coVerify(exactly = 0) {
          incentivesSynchronisationService.synchroniseIncentive(
            any()
          )
        }
      }
    }
  }
}

private fun objectMapper(): ObjectMapper {
  return ObjectMapper()
    .setSerializationInclusion(JsonInclude.Include.NON_NULL)
    .registerModule(JavaTimeModule())
    .registerKotlinModule()
    .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
}
