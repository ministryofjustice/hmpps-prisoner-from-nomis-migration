package uk.gov.justice.digital.hmpps.prisonerfromnomismigration.prisonperson

import com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor
import com.github.tomakehurst.wiremock.client.WireMock.putRequestedFor
import com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.mockito.kotlin.check
import org.mockito.kotlin.eq
import org.mockito.kotlin.isNull
import org.mockito.kotlin.verify
import org.springframework.beans.factory.annotation.Autowired
import software.amazon.awssdk.services.sqs.model.SendMessageResponse
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.helper.bookingMovedDomainEvent
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.integration.SqsIntegrationTestBase
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.integration.sendMessage
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomissync.model.BookingPhysicalAttributesResponse
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomissync.model.BookingProfileDetailsResponse
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomissync.model.PhysicalAttributesResponse
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomissync.model.PrisonerPhysicalAttributesResponse
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomissync.model.PrisonerProfileDetailsResponse
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomissync.model.ProfileDetailsResponse
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.prisonperson.PrisonPersonNomisSyncApiExtension.Companion.nomisSyncApi
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.prisonperson.model.PhysicalAttributesSyncResponse
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.prisonperson.model.ProfileDetailsPhysicalAttributesSyncResponse
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.prisonperson.physicalattributes.PhysicalAttributesDpsApiMockServer
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.prisonperson.physicalattributes.PhysicalAttributesNomisApiMockServer
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.prisonperson.profiledetails.ProfileDetailsNomisApiMockServer
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.prisonperson.profiledetails.ProfileDetailsPhysicalAttributesDpsApiMockServer
import java.time.LocalDateTime

/*
 * TODO SDIT-2200 Need more tests for:
 *  - profile details none re-entrered in NOMIS
 *  - profile details some re-entered in NOMIS
 *  - profile details all re-entered in NOMIS
 *  - any API call fails
 *
 */
class PrisonPersonMoveBookingIntTest : SqsIntegrationTestBase() {

  @Autowired
  private lateinit var physicalAttributesNomisApi: PhysicalAttributesNomisApiMockServer

  @Autowired
  private lateinit var physicalAttributesDpsApi: PhysicalAttributesDpsApiMockServer

  @Autowired
  private lateinit var profileDetailsNomisApi: ProfileDetailsNomisApiMockServer

  @Autowired
  private lateinit var profileDetailsDpsApi: ProfileDetailsPhysicalAttributesDpsApiMockServer

  @Nested
  @DisplayName("prison-offender-events.prisoner.booking.moved")
  inner class BookingMoved {
    private val bookingId = 123L
    private val toOffenderNo = "A1234AA"
    private val fromOffenderNo = "B1234BB"

    @Nested
    inner class HappyPath {
      @BeforeEach
      fun stubMissingDetails() {
        // Assume we get nothing back from NOMIS so tests can override the stubs they're interested in
        physicalAttributesNomisApi.stubGetPhysicalAttributes(fromOffenderNo, PrisonerPhysicalAttributesResponse(fromOffenderNo, listOf()))
        physicalAttributesNomisApi.stubGetPhysicalAttributes(toOffenderNo, PrisonerPhysicalAttributesResponse(toOffenderNo, listOf()))
        profileDetailsNomisApi.stubGetProfileDetails(fromOffenderNo, PrisonerProfileDetailsResponse(fromOffenderNo, listOf()))
        profileDetailsNomisApi.stubGetProfileDetails(toOffenderNo, PrisonerProfileDetailsResponse(fromOffenderNo, listOf()))
      }

      @AfterEach
      fun verifyNomisApiCalls() {
        // Whatever happens we should be calling NOMIS to get the details
        physicalAttributesNomisApi.verify(
          getRequestedFor(urlPathEqualTo("/prisoners/$fromOffenderNo/physical-attributes")),
        )
        physicalAttributesNomisApi.verify(
          getRequestedFor(urlPathEqualTo("/prisoners/$toOffenderNo/physical-attributes")),
        )
        profileDetailsNomisApi.verify(
          getRequestedFor(urlPathEqualTo("/prisoners/$fromOffenderNo/profile-details")),
        )
        physicalAttributesNomisApi.verify(
          getRequestedFor(urlPathEqualTo("/prisoners/$toOffenderNo/profile-details")),
        )
      }

      @Test
      fun `offender physical attributes not re-entered in NOMIS`() {
        // the moved booking has height/weight as copied from the booking of the from offender
        physicalAttributesNomisApi.stubGetPhysicalAttributes(fromOffenderNo, nomisPhysicalAttributesFromOffender(fromOffenderNo))
        physicalAttributesNomisApi.stubGetPhysicalAttributes(toOffenderNo, nomisPhysicalAttributesToOffenderNotRemeasured(toOffenderNo, bookingId))
        physicalAttributesDpsApi.stubSyncPhysicalAttributes(syncPhysicalAttributesResponse())
        nomisSyncApi.stubSyncPhysicalAttributes(toOffenderNo)

        // process event
        sendBookingMovedEvent().also {
          waitForAnyProcessingToComplete("prisonperson-booking-moved")
        }

        // the from offender is sync'd from NOMIS to DPS
        physicalAttributesDpsApi.verify(
          putRequestedFor(urlPathEqualTo("/sync/prisoners/$fromOffenderNo/physical-attributes")),
        )

        // the to offender IS NOT sync'd from NOMIS to DPS
        physicalAttributesDpsApi.verify(
          count = 0,
          putRequestedFor(urlPathEqualTo("/sync/prisoners/$toOffenderNo/physical-attributes")),
        )

        // the to offender is also sync'd back from DPS to NOMIS
        nomisSyncApi.verify(
          putRequestedFor(urlPathEqualTo("/prisonperson/$toOffenderNo/physical-attributes")),
        )

        // telemetry
        verify(telemetryClient).trackEvent(
          eq("prisonperson-booking-moved"),
          check {
            assertThat(it).containsExactlyInAnyOrderEntriesOf(
              mapOf(
                "bookingId" to bookingId.toString(),
                "toOffenderNo" to toOffenderNo,
                "fromOffenderNo" to fromOffenderNo,
                "syncFromOffenderDps_HEIGHT" to "true",
                "syncFromOffenderDps_WEIGHT" to "true",
                "syncToOffenderNomis" to "true",
              ),
            )
          },
          isNull(),
        )
      }

      @Test
      fun `offender physical attributes re-entered in NOMIS`() {
        // the moved booking has height/weight re-entered
        physicalAttributesNomisApi.stubGetPhysicalAttributes(fromOffenderNo, nomisPhysicalAttributesFromOffender(fromOffenderNo))
        physicalAttributesNomisApi.stubGetPhysicalAttributes(toOffenderNo, nomisPhysicalAttributesToOffenderRemeasured(toOffenderNo, bookingId))
        physicalAttributesDpsApi.stubSyncPhysicalAttributes(syncPhysicalAttributesResponse())
        nomisSyncApi.stubSyncPhysicalAttributes(toOffenderNo)

        // process event
        sendBookingMovedEvent().also {
          waitForAnyProcessingToComplete("prisonperson-booking-moved")
        }

        // the from offender is sync'd from NOMIS to DPS
        physicalAttributesDpsApi.verify(
          putRequestedFor(urlPathEqualTo("/sync/prisoners/$fromOffenderNo/physical-attributes")),
        )

        // the to offender IS sync'd from NOMIS to DPS
        physicalAttributesDpsApi.verify(
          count = 1,
          putRequestedFor(urlPathEqualTo("/sync/prisoners/$toOffenderNo/physical-attributes")),
        )

        // the to offender is also sync'd back from DPS to NOMIS
        nomisSyncApi.verify(
          putRequestedFor(urlPathEqualTo("/prisonperson/$toOffenderNo/physical-attributes")),
        )

        // telemetry
        verify(telemetryClient).trackEvent(
          eq("prisonperson-booking-moved"),
          check {
            assertThat(it).containsExactlyInAnyOrderEntriesOf(
              mapOf(
                "bookingId" to bookingId.toString(),
                "toOffenderNo" to toOffenderNo,
                "fromOffenderNo" to fromOffenderNo,
                "syncFromOffenderDps_HEIGHT" to "true",
                "syncFromOffenderDps_WEIGHT" to "true",
                "syncToOffenderDps_HEIGHT" to "true",
                "syncToOffenderDps_WEIGHT" to "true",
                "syncToOffenderNomis" to "true",
              ),
            )
          },
          isNull(),
        )
      }

      @Test
      fun `offender profile details not re-entered in NOMIS`() {
        // the moved booking has BUILD as copied from the booking of the from offender
        profileDetailsNomisApi.stubGetProfileDetails(fromOffenderNo, nomisProfileDetailsFromOffender(fromOffenderNo))
        profileDetailsNomisApi.stubGetProfileDetails(toOffenderNo, nomisProfileDetailsToOffenderNotReentered(toOffenderNo, bookingId))
        profileDetailsDpsApi.stubSyncProfileDetailsPhysicalAttributes(syncProfileDetailsResponse())
        nomisSyncApi.stubSyncPhysicalAttributes(toOffenderNo)

        // process event
        sendBookingMovedEvent().also {
          waitForAnyProcessingToComplete("prisonperson-booking-moved")
        }

        // the from offender is sync'd from NOMIS to DPS
        physicalAttributesDpsApi.verify(
          putRequestedFor(urlPathEqualTo("/sync/prisoners/$fromOffenderNo/profile-details-physical-attributes")),
        )

        // the to offender IS NOT sync'd from NOMIS to DPS
        physicalAttributesDpsApi.verify(
          count = 0,
          putRequestedFor(urlPathEqualTo("/sync/prisoners/$toOffenderNo/profile-details-physical-attributes")),
        )

        // the to offender is also sync'd back from DPS to NOMIS
        nomisSyncApi.verify(
          putRequestedFor(urlPathEqualTo("/prisonperson/$toOffenderNo/physical-attributes")),
        )

        // telemetry
        verify(telemetryClient).trackEvent(
          eq("prisonperson-booking-moved"),
          check {
            assertThat(it).containsExactlyInAnyOrderEntriesOf(
              mapOf(
                "bookingId" to bookingId.toString(),
                "toOffenderNo" to toOffenderNo,
                "fromOffenderNo" to fromOffenderNo,
                "syncFromOffenderDps_BUILD" to "true",
                "syncToOffenderNomis" to "true",
              ),
            )
          },
          isNull(),
        )
      }

      @Test
      fun `offender profile details re-entered in NOMIS`() {
        // the moved booking has BUILD re-entered in NOMIS
        profileDetailsNomisApi.stubGetProfileDetails(fromOffenderNo, nomisProfileDetailsFromOffender(fromOffenderNo))
        profileDetailsNomisApi.stubGetProfileDetails(toOffenderNo, nomisProfileDetailsToOffenderReentered(toOffenderNo, bookingId))
        profileDetailsDpsApi.stubSyncProfileDetailsPhysicalAttributes(syncProfileDetailsResponse())
        nomisSyncApi.stubSyncPhysicalAttributes(toOffenderNo)

        // process event
        sendBookingMovedEvent().also {
          waitForAnyProcessingToComplete("prisonperson-booking-moved")
        }

        // the from offender is sync'd from NOMIS to DPS
        physicalAttributesDpsApi.verify(
          putRequestedFor(urlPathEqualTo("/sync/prisoners/$fromOffenderNo/profile-details-physical-attributes")),
        )

        // the to offender IS sync'd from NOMIS to DPS
        physicalAttributesDpsApi.verify(
          count = 1,
          putRequestedFor(urlPathEqualTo("/sync/prisoners/$toOffenderNo/profile-details-physical-attributes")),
        )

        // the to offender is also sync'd back from DPS to NOMIS
        nomisSyncApi.verify(
          putRequestedFor(urlPathEqualTo("/prisonperson/$toOffenderNo/physical-attributes")),
        )

        // telemetry
        verify(telemetryClient).trackEvent(
          eq("prisonperson-booking-moved"),
          check {
            assertThat(it).containsExactlyInAnyOrderEntriesOf(
              mapOf(
                "bookingId" to bookingId.toString(),
                "toOffenderNo" to toOffenderNo,
                "fromOffenderNo" to fromOffenderNo,
                "syncFromOffenderDps_BUILD" to "true",
                "syncToOffenderDps_BUILD" to "true",
                "syncToOffenderNomis" to "true",
              ),
            )
          },
          isNull(),
        )
      }
    }

    private fun sendBookingMovedEvent(): SendMessageResponse? =
      awsSqsSentencingOffenderEventsClient.sendMessage(
        prisonPersonQueueOffenderEventsUrl,
        bookingMovedDomainEvent(
          eventType = "prison-offender-events.prisoner.booking.moved",
          bookingId = bookingId,
          movedFromNomsNumber = fromOffenderNo,
          movedToNomsNumber = toOffenderNo,
        ),
      )

    private fun syncPhysicalAttributesResponse(ids: List<Long> = listOf(321)) = PhysicalAttributesSyncResponse(ids)
    private fun syncProfileDetailsResponse(ids: List<Long> = listOf(321)) = ProfileDetailsPhysicalAttributesSyncResponse(ids)
  }
}

fun nomisPhysicalAttributesFromOffender(
  offenderNo: String,
  bookingStartTime: LocalDateTime = LocalDateTime.now().minusDays(1),
) =
  PrisonerPhysicalAttributesResponse(
    offenderNo = offenderNo,
    bookings = listOf(
      // The from offender has a single booking - the new booking has been copied to the to offender
      BookingPhysicalAttributesResponse(
        bookingId = 2345L,
        startDateTime = "${bookingStartTime.minusDays(8)}",
        endDateTime = "${bookingStartTime.minusDays(6)}",
        latestBooking = true,
        physicalAttributes = listOf(
          PhysicalAttributesResponse(
            attributeSequence = 1,
            heightCentimetres = 180,
            weightKilograms = 80,
            createdBy = "A_USER",
            createDateTime = "${bookingStartTime.minusDays(7)}",
            modifiedBy = "ANOTHER_USER",
            modifiedDateTime = null,
            auditModuleName = "MODULE",
          ),
        ),
      ),
    ),
  )

fun nomisPhysicalAttributesToOffenderNotRemeasured(
  offenderNo: String,
  bookingId: Long,
  bookingStartTime: LocalDateTime = LocalDateTime.now().minusDays(1),
) =
  PrisonerPhysicalAttributesResponse(
    offenderNo = offenderNo,
    bookings = listOf(
      // The to offender's old booking
      BookingPhysicalAttributesResponse(
        bookingId = 3456L,
        startDateTime = "${bookingStartTime.minusDays(8)}",
        endDateTime = "${bookingStartTime.minusDays(6)}",
        latestBooking = false,
        physicalAttributes = listOf(
          PhysicalAttributesResponse(
            attributeSequence = 1,
            heightCentimetres = 170,
            weightKilograms = 70,
            createdBy = "A_USER",
            createDateTime = "${bookingStartTime.minusDays(7)}",
            modifiedBy = "ANOTHER_USER",
            modifiedDateTime = null,
            auditModuleName = "MODULE",
          ),
        ),
      ),
      // The to offender's new booking has height and weight copied from the from offender
      BookingPhysicalAttributesResponse(
        bookingId = bookingId,
        startDateTime = "$bookingStartTime}",
        endDateTime = null,
        latestBooking = true,
        physicalAttributes = listOf(
          PhysicalAttributesResponse(
            attributeSequence = 1,
            heightCentimetres = 180,
            weightKilograms = 80,
            createdBy = "A_USER",
            createDateTime = "${bookingStartTime.minusDays(7)}",
            modifiedBy = null,
            modifiedDateTime = null,
            auditModuleName = "MODULE",
          ),
        ),
      ),
    ),
  )

fun nomisPhysicalAttributesToOffenderRemeasured(
  offenderNo: String,
  bookingId: Long,
  bookingStartTime: LocalDateTime = LocalDateTime.now().minusDays(1),
) =
  PrisonerPhysicalAttributesResponse(
    offenderNo = offenderNo,
    bookings = listOf(
      // The to offender's old booking
      BookingPhysicalAttributesResponse(
        bookingId = 3456L,
        startDateTime = "${bookingStartTime.minusDays(8)}",
        endDateTime = "${bookingStartTime.minusDays(6)}",
        latestBooking = false,
        physicalAttributes = listOf(
          PhysicalAttributesResponse(
            attributeSequence = 1,
            heightCentimetres = 170,
            weightKilograms = 70,
            createdBy = "A_USER",
            createDateTime = "${bookingStartTime.minusDays(7)}",
            modifiedBy = "ANOTHER_USER",
            modifiedDateTime = null,
            auditModuleName = "MODULE",
          ),
        ),
      ),
      // The to offender's new booking has weight re-entered hence new weight and modified time after booking start time
      BookingPhysicalAttributesResponse(
        bookingId = bookingId,
        startDateTime = "$bookingStartTime",
        endDateTime = null,
        latestBooking = true,
        physicalAttributes = listOf(
          PhysicalAttributesResponse(
            attributeSequence = 1,
            heightCentimetres = 170,
            weightKilograms = 90,
            createdBy = "A_USER",
            createDateTime = "${bookingStartTime.minusDays(7)}",
            modifiedBy = "ANOTHER_USER",
            modifiedDateTime = "${bookingStartTime.plusDays(1)}",
            auditModuleName = "MODULE",
          ),
        ),
      ),
    ),
  )

fun nomisProfileDetailsFromOffender(
  offenderNo: String,
  bookingStartTime: LocalDateTime = LocalDateTime.now().minusDays(1),
) =
  PrisonerProfileDetailsResponse(
    offenderNo,
    listOf(
      BookingProfileDetailsResponse(
        bookingId = 2345L,
        startDateTime = "${bookingStartTime.minusDays(8)}",
        endDateTime = "${bookingStartTime.minusDays(6)}",
        latestBooking = true,
        profileDetails = listOf(
          ProfileDetailsResponse(
            type = "BUILD",
            code = "MEDIUM",
            createDateTime = "${bookingStartTime.minusDays(7)}",
            createdBy = "A_USER",
            modifiedDateTime = null,
            modifiedBy = null,
            auditModuleName = "NOMIS",
          ),
        ),
      ),
    ),
  )

fun nomisProfileDetailsToOffenderNotReentered(
  offenderNo: String,
  bookingId: Long,
  bookingStartTime: LocalDateTime = LocalDateTime.now().minusDays(1),
) =
  PrisonerProfileDetailsResponse(
    offenderNo = offenderNo,
    bookings = listOf(
      // The to offender's old booking
      BookingProfileDetailsResponse(
        bookingId = 3456L,
        startDateTime = "${bookingStartTime.minusDays(8)}",
        endDateTime = "${bookingStartTime.minusDays(6)}",
        latestBooking = false,
        profileDetails = listOf(
          ProfileDetailsResponse(
            type = "BUILD",
            code = "MEDIUM",
            createDateTime = "${bookingStartTime.minusDays(7)}",
            createdBy = "A_USER",
            modifiedDateTime = null,
            modifiedBy = null,
            auditModuleName = "NOMIS",
          ),
        ),
      ),
      // The to offender's new booking has build copied from the from offender
      BookingProfileDetailsResponse(
        bookingId = bookingId,
        startDateTime = "$bookingStartTime",
        endDateTime = null,
        latestBooking = true,
        profileDetails = listOf(
          ProfileDetailsResponse(
            type = "BUILD",
            code = "LARGE",
            createDateTime = "${bookingStartTime.minusDays(7)}",
            createdBy = "A_USER",
            modifiedDateTime = null,
            modifiedBy = null,
            auditModuleName = "NOMIS",
          ),
        ),
      ),
    ),
  )

fun nomisProfileDetailsToOffenderReentered(
  offenderNo: String,
  bookingId: Long,
  bookingStartTime: LocalDateTime = LocalDateTime.now().minusDays(1),
) =
  PrisonerProfileDetailsResponse(
    offenderNo = offenderNo,
    bookings = listOf(
      // The to offender's old booking
      BookingProfileDetailsResponse(
        bookingId = 3456L,
        startDateTime = "${bookingStartTime.minusDays(8)}",
        endDateTime = "${bookingStartTime.minusDays(6)}",
        latestBooking = false,
        profileDetails = listOf(
          ProfileDetailsResponse(
            type = "BUILD",
            code = "MEDIUM",
            createdBy = "A_USER",
            createDateTime = "${bookingStartTime.minusDays(7)}",
            modifiedBy = null,
            modifiedDateTime = null,
            auditModuleName = "NOMIS",
          ),
        ),
      ),
      // The to offender's new booking has build changed in NOMIS hence modified time after booking start time
      BookingProfileDetailsResponse(
        bookingId = bookingId,
        startDateTime = "$bookingStartTime",
        endDateTime = null,
        latestBooking = true,
        profileDetails = listOf(
          ProfileDetailsResponse(
            type = "BUILD",
            code = "LARGE",
            createdBy = "A_USER",
            createDateTime = "${bookingStartTime.minusDays(7)}",
            modifiedBy = "ANOTHER_USER",
            modifiedDateTime = "${bookingStartTime.plusDays(1)}",
            auditModuleName = "NOMIS",
          ),
        ),
      ),
    ),
  )
