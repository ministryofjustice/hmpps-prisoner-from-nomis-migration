package uk.gov.justice.digital.hmpps.prisonerfromnomismigration.integration.incentives

import org.assertj.core.api.Assertions
import org.awaitility.kotlin.await
import org.awaitility.kotlin.matches
import org.awaitility.kotlin.untilAsserted
import org.awaitility.kotlin.untilCallTo
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.mockito.internal.verification.Times
import org.mockito.kotlin.any
import org.mockito.kotlin.check
import org.mockito.kotlin.eq
import org.mockito.kotlin.isNull
import org.mockito.kotlin.verify
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.helper.validIepCreatedMessage
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.helper.validIepCreatedMessageWithNomisIds
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.helper.validIepDeletedMessage
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.helper.validSynchroniseCurrentIncentiveMessage
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.integration.SqsIntegrationTestBase
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.integration.sendMessage
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.wiremock.IncentivesApiExtension.Companion.incentivesApi
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.wiremock.MappingApiExtension.Companion.mappingApi
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.wiremock.NomisApiExtension.Companion.nomisApi

class IncentivesFromNomisIntTest : SqsIntegrationTestBase() {

  @Nested
  @DisplayName("synchronise create incentive")
  inner class SynchroniseCreateIncentive {
    @Test
    fun `will synchronise an incentive after a NOMIS incentive is created`() {

      val message = validIepCreatedMessage()

      nomisApi.stubGetIncentive(bookingId = 1234, incentiveSequence = 1)
      mappingApi.stubNomisIncentiveMappingNotFound(nomisBookingId = 1234, nomisIncentiveSequence = 1)
      mappingApi.stubIncentiveMappingCreate()
      incentivesApi.stubCreateSynchroniseIncentive()

      awsSqsIncentivesOffenderEventsClient.sendMessage(incentivesQueueOffenderEventsUrl, message)

      await untilAsserted { mappingApi.verifyCreateIncentiveMapping() }

      await untilAsserted {
        verify(telemetryClient).trackEvent(
          eq("incentive-created-synchronisation"),
          eq(
            mapOf(
              "bookingId" to "1234",
              "incentiveSequence" to "1",
              "incentiveId" to "654321",
            )
          ),
          isNull()
        )
      }
    }

    @Test
    fun `will retry to create a mapping if call fails`() {

      val message = validIepCreatedMessage()

      nomisApi.stubGetIncentive(bookingId = 1234, incentiveSequence = 1)
      mappingApi.stubNomisIncentiveMappingNotFound(nomisBookingId = 1234, nomisIncentiveSequence = 1)
      mappingApi.stubIncentiveMappingCreateFailureFollowedBySuccess()
      incentivesApi.stubCreateSynchroniseIncentive()

      awsSqsIncentivesOffenderEventsClient.sendMessage(incentivesQueueOffenderEventsUrl, message)

      // wait for all mappings to be created before verifying
      await untilCallTo { mappingApi.createIncentiveMappingCount() } matches { it == 2 }

      // check that one incentive is created
      Assertions.assertThat(incentivesApi.createIncentiveSynchronisationCount()).isEqualTo(1)

      // should retry to create mapping twice
      mappingApi.verifyCreateMappingIncentiveIds(arrayOf(654321), times = 2)
    }

    @Test
    fun `will delay the synchronisation of a non-current incentive`() {

      val messageUpdate = validIepCreatedMessageWithNomisIds(1234, 2)
      nomisApi.stubGetIncentive(bookingId = 1234, incentiveSequence = 2, currentIep = false)
      nomisApi.stubGetCurrentIncentive(bookingId = 1234, incentiveSequence = 2)
      mappingApi.stubIncentiveMappingByNomisIds(nomisBookingId = 1234, nomisIncentiveSequence = 2, incentiveId = 4)
      incentivesApi.stubUpdateSynchroniseIncentive()

      awsSqsIncentivesOffenderEventsClient.sendMessage(incentivesQueueOffenderEventsUrl, messageUpdate)

      await untilAsserted {
        verify(telemetryClient).trackEvent(
          eq("SYNCHRONISE_CURRENT_INCENTIVE"),
          any(),
          isNull()
        )
      }
    }

    @Test
    fun `will handle a synchronise current incentive message`() {

      val message = validSynchroniseCurrentIncentiveMessage()
      nomisApi.stubGetIncentive(bookingId = 1234, incentiveSequence = 2, currentIep = false)
      nomisApi.stubGetCurrentIncentive(bookingId = 1234, incentiveSequence = 2)
      mappingApi.stubIncentiveMappingByNomisIds(nomisBookingId = 1234, nomisIncentiveSequence = 2, incentiveId = 4)
      incentivesApi.stubUpdateSynchroniseIncentive()

      awsSqsIncentivesMigrationClient.sendMessage(incentivesMigrationUrl, message)

      await untilAsserted { incentivesApi.verifyUpdateSynchroniseIncentive(1) }

      await untilAsserted {
        verify(telemetryClient, Times(1)).trackEvent(
          eq("incentive-updated-synchronisation"),
          any(),
          isNull()
        )
      }
    }
  }

  @Nested
  @DisplayName("synchronise update incentive")
  inner class SynchroniseUpdateIncentive {
    @Test
    fun `will synchronise an incentive after a nomis update to a current incentive`() {

      val message = validIepCreatedMessage()

      nomisApi.stubGetIncentive(bookingId = 1234, incentiveSequence = 1, currentIep = true)
      mappingApi.stubIncentiveMappingByNomisIds(nomisBookingId = 1234, nomisIncentiveSequence = 1)
      incentivesApi.stubUpdateSynchroniseIncentive()
      awsSqsIncentivesOffenderEventsClient.sendMessage(incentivesQueueOffenderEventsUrl, message)

      await untilAsserted { incentivesApi.verifyUpdateSynchroniseIncentive(1) }

      await untilAsserted {
        verify(telemetryClient, Times(1)).trackEvent(
          eq("incentive-updated-synchronisation"),
          eq(
            mapOf(
              "bookingId" to "1234",
              "incentiveSequence" to "1",
              "incentiveId" to "3",
              "currentIep" to "true"
            )
          ),
          isNull()
        )
      }
    }

    @Test
    fun `will synchronise an incentive after a nomis update to a non-current incentive`() {

      /* 1. update to non-current iep received
         2. non-current iep mapping retrieved
         3. non-current iep is updated in the incentives service
         4. current iep is retrieved from nomis
         5. mapping of current iep is found
         6. iep is updated in the incentives service
       */

      val message = validIepCreatedMessage()

      nomisApi.stubGetIncentive(bookingId = 1234, incentiveSequence = 1, currentIep = false)
      mappingApi.stubIncentiveMappingByNomisIds(nomisBookingId = 1234, nomisIncentiveSequence = 1)
      mappingApi.stubIncentiveMappingByNomisIds(nomisBookingId = 1234, nomisIncentiveSequence = 2)
      incentivesApi.stubUpdateSynchroniseIncentive()
      awsSqsIncentivesOffenderEventsClient.sendMessage(incentivesQueueOffenderEventsUrl, message)
      nomisApi.stubGetCurrentIncentive(bookingId = 1234, incentiveSequence = 2)

      await untilAsserted { incentivesApi.verifyUpdateSynchroniseIncentive(2) }

      await untilAsserted {
        verify(telemetryClient, Times(2)).trackEvent(
          eq("incentive-updated-synchronisation"),
          any(),
          isNull()
        )
      }
    }

    @Test
    fun `will synchronise and create incentive in the incentive service (if no mapping) after a nomis update to a non-current incentive`() {

      /* 1. update to non-current iep received
         2. non-current iep mapping retrieved
         3. non-current iep is updated in the incentives service
         4. current iep is retrieved from nomis
         5. no mapping is found
         6. Mapping is created
         7. iep is created in the incentives service
       */

      val message = validIepCreatedMessage()

      nomisApi.stubGetIncentive(bookingId = 1234, incentiveSequence = 1, currentIep = false)
      nomisApi.stubGetCurrentIncentive(bookingId = 1234, incentiveSequence = 2)
      mappingApi.stubIncentiveMappingByNomisIds(nomisBookingId = 1234, nomisIncentiveSequence = 1)
      mappingApi.stubNomisIncentiveMappingNotFound(nomisBookingId = 1234, nomisIncentiveSequence = 2)
      incentivesApi.stubUpdateSynchroniseIncentive()
      incentivesApi.stubCreateSynchroniseIncentive()
      mappingApi.stubIncentiveMappingCreate()
      awsSqsIncentivesOffenderEventsClient.sendMessage(incentivesQueueOffenderEventsUrl, message)

      await untilAsserted { incentivesApi.verifyCreateSynchroniseIncentive() }

      await untilAsserted {
        verify(telemetryClient, Times(1)).trackEvent(
          eq("incentive-updated-synchronisation"),
          any(),
          isNull()
        )
      }
      await untilAsserted {
        // created incentive for current incentive without a mapping
        verify(telemetryClient, Times(1)).trackEvent(
          eq("incentive-created-synchronisation"),
          any(),
          isNull()
        )
      }
    }
  }

  @Nested
  @DisplayName("synchronise delete incentive")
  inner class SynchroniseDeleteIncentive {

    @Test
    fun `will synchronise an incentive after nomis deletes the associated IEP`() {

      /* 1. Deletes any iep which may or may not have been current
         2. Deleted iep mapping retrieved
         3. current iep is retrieved from nomis
         4. mapping of current iep is found
         5. iep is updated in the incentives service
         6. Deleted iep is deleted in the incentives service and mapping service
       */

      val message = validIepDeletedMessage(bookingId = 1234, incentiveSequence = 1)

      mappingApi.stubIncentiveMappingByNomisIds(nomisBookingId = 1234, nomisIncentiveSequence = 1, incentiveId = 456789)
      nomisApi.stubGetCurrentIncentive(bookingId = 1234, incentiveSequence = 2)
      mappingApi.stubIncentiveMappingByNomisIds(nomisBookingId = 1234, nomisIncentiveSequence = 2, incentiveId = 987654)

      // set current incentive
      incentivesApi.stubUpdateSynchroniseIncentive(bookingId = 1234, incentivesId = 987654)
      // delete incentive
      incentivesApi.stubDeleteSynchroniseIncentive(bookingId = 1234, incentivesId = 456789)
      // delete mapping
      mappingApi.stubDeleteIncentiveMapping(incentiveId = 456789)

      awsSqsIncentivesOffenderEventsClient.sendMessage(incentivesQueueOffenderEventsUrl, message)

      await untilAsserted { incentivesApi.verifyDeleteSynchroniseIncentive() }

      incentivesApi.verifyDeleteSynchroniseIncentive(bookingId = 1234, incentivesId = 456789)
      incentivesApi.verifyUpdateSynchroniseIncentive(bookingId = 1234, incentivesId = 987654)

      await untilAsserted {
        mappingApi.verifyDeleteIncentiveMapping(incentiveId = 456789)
      }

      await untilAsserted {
        verify(telemetryClient).trackEvent(
          eq("incentive-delete-synchronisation"),
          check {
            it["bookingId"] == "1234" &&
              it["incentiveSequence"] == "1" &&
              it["incentiveId"] == "456789"
          },
          isNull()
        )
      }
    }

    @Test
    fun `will synchronise an incentive after nomis deletes the last associated IEP -  No current IEP left after deletion `() {

      /* 1. Deletes any iep which may or may not have been current
         2. Deleted iep mapping retrieved
         3. current iep returns a 404 from nomis (deleted IEP was the only one)
         4. Deleted iep is deleted in the incentives service and mapping service
       */

      val message = validIepDeletedMessage(bookingId = 1234, incentiveSequence = 1)

      mappingApi.stubIncentiveMappingByNomisIds(nomisBookingId = 1234, nomisIncentiveSequence = 1, incentiveId = 456789)
      nomisApi.stubGetCurrentIncentiveNotFound(bookingId = 1234)
      mappingApi.stubIncentiveMappingByNomisIds(nomisBookingId = 1234, nomisIncentiveSequence = 2, incentiveId = 987654)

      // delete incentive
      incentivesApi.stubDeleteSynchroniseIncentive(bookingId = 1234, incentivesId = 456789)
      // delete mapping
      mappingApi.stubDeleteIncentiveMapping(incentiveId = 456789)

      awsSqsIncentivesOffenderEventsClient.sendMessage(incentivesQueueOffenderEventsUrl, message)

      await untilAsserted { incentivesApi.verifyDeleteSynchroniseIncentive() }

      incentivesApi.verifyDeleteSynchroniseIncentive(bookingId = 1234, incentivesId = 456789)
      incentivesApi.verifyUpdateSynchroniseIncentive(times = 0)
      await untilAsserted {
        mappingApi.verifyDeleteIncentiveMapping(incentiveId = 456789)
      }

      await untilAsserted {
        verify(telemetryClient).trackEvent(
          eq("incentive-delete-synchronisation"),
          check {
            it["bookingId"] == "1234" &&
              it["incentiveSequence"] == "1" &&
              it["incentiveId"] == "456789"
          },
          isNull()
        )
      }
    }

    @Test
    fun `will not synchronise anything after a non-mapped nomis IEP is deleted`() {

      /* 1. Deletes any iep which may or may not have been current
         2. Deleted iep mapping retrieved
         3. No mapping found so do nothing
       */

      val message = validIepDeletedMessage(bookingId = 1234, incentiveSequence = 1)

      mappingApi.stubNomisIncentiveMappingNotFound(nomisBookingId = 1234, nomisIncentiveSequence = 1)
      awsSqsIncentivesOffenderEventsClient.sendMessage(incentivesQueueOffenderEventsUrl, message)

      await untilAsserted {
        verify(telemetryClient).trackEvent(
          eq("incentive-delete-synchronisation-ignored"),
          any(),
          isNull()
        )
      }

      verify(telemetryClient).trackEvent(
        eq("incentive-delete-synchronisation-ignored"),
        check { it["bookingId"] == "1234" && it["incentiveSequence"] == "1" },
        isNull()
      )
    }
  }
}
