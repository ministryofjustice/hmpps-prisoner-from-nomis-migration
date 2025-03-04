package uk.gov.justice.digital.hmpps.prisonerfromnomismigration.prisonperson

import com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor
import com.github.tomakehurst.wiremock.client.WireMock.putRequestedFor
import com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo
import org.assertj.core.api.Assertions.assertThat
import org.awaitility.kotlin.await
import org.awaitility.kotlin.untilAsserted
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
import org.springframework.http.HttpStatus
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
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.prisonperson.model.PhysicalAttributesSyncResponse
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.prisonperson.model.ProfileDetailsPhysicalAttributesSyncResponse
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.prisonperson.physicalattributes.PhysicalAttributesDpsApiMockServer
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.prisonperson.physicalattributes.PhysicalAttributesNomisApiMockServer
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.prisonperson.profiledetails.ProfileDetailsNomisApiMockServer
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.prisonperson.profiledetails.ProfileDetailsPhysicalAttributesDpsApiMockServer
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.wiremock.withRequestBodyJsonPath
import uk.gov.justice.hmpps.sqs.countAllMessagesOnQueue
import java.time.LocalDateTime

class PrisonPersonMoveBookingIntTest : SqsIntegrationTestBase() {

  @Autowired
  private lateinit var physicalAttributesNomisApi: PhysicalAttributesNomisApiMockServer

  @Autowired
  private lateinit var physicalAttributesDpsApi: PhysicalAttributesDpsApiMockServer

  @Autowired
  private lateinit var profileDetailsNomisApi: ProfileDetailsNomisApiMockServer

  @Autowired
  private lateinit var profileDetailsDpsApi: ProfileDetailsPhysicalAttributesDpsApiMockServer

  @Autowired
  private lateinit var nomisSyncApi: PrisonPersonNomisSyncApiMockServer

  @Nested
  @DisplayName("prison-offender-events.prisoner.booking.moved")
  inner class BookingMoved {
    private val movedBookingId = 123L
    private val toOffenderNo = "A1234AA"
    private val fromOffenderNo = "B1234BB"

    @Nested
    inner class HappyPath {
      @BeforeEach
      fun stubMissingDetails() {
        // Assume we get nothing back from NOMIS so tests can override the stubs they're interested in
        stubGetPhysicalAttributesMissing(fromOffenderNo)
        stubGetPhysicalAttributesMissing(toOffenderNo)
        stubGetProfileDetailsMissing(fromOffenderNo)
        stubGetProfileDetailsMissing(toOffenderNo)

        // Stub a successful sync
        physicalAttributesDpsApi.stubSyncPhysicalAttributes(syncPhysicalAttributesResponse())
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
        stubGetPhysicalAttributes(fromOffenderNo, nomisPhysicalAttributesFromOffender(fromOffenderNo))
        stubGetPhysicalAttributes(toOffenderNo, nomisPhysicalAttributesToOffenderNotRemeasured(fromOffenderNo, movedBookingId))
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
                "bookingId" to movedBookingId.toString(),
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
        stubGetPhysicalAttributes(fromOffenderNo, nomisPhysicalAttributesFromOffender(fromOffenderNo))
        stubGetPhysicalAttributes(toOffenderNo, nomisPhysicalAttributesToOffenderRemeasured(toOffenderNo, movedBookingId))
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
                "bookingId" to movedBookingId.toString(),
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
      fun `offender physical attributes re-entered in DPS`() {
        // the moved booking has height/weight re-entered
        stubGetPhysicalAttributes(fromOffenderNo, nomisPhysicalAttributesFromOffender(fromOffenderNo, updatedInNomis = false))
        stubGetPhysicalAttributes(toOffenderNo, nomisPhysicalAttributesToOffenderRemeasured(toOffenderNo, movedBookingId, updatedInNomis = false))
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
                "bookingId" to movedBookingId.toString(),
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
        stubGetProfileDetails(fromOffenderNo, nomisProfileDetailsFromOffender(fromOffenderNo))
        stubGetProfileDetails(toOffenderNo, nomisProfileDetailsToOffenderNotReentered(toOffenderNo, movedBookingId))
        profileDetailsDpsApi.stubSyncProfileDetailsPhysicalAttributes(fromOffenderNo, syncProfileDetailsResponse())
        nomisSyncApi.stubSyncPhysicalAttributes(toOffenderNo)

        // process event
        sendBookingMovedEvent().also {
          waitForAnyProcessingToComplete("prisonperson-booking-moved")
        }

        // the from offender is sync'd from NOMIS to DPS
        physicalAttributesDpsApi.verify(
          putRequestedFor(urlPathEqualTo("/sync/prisoners/$fromOffenderNo/profile-details-physical-attributes"))
            .withRequestBodyJsonPath("build.value", "MEDIUM"),

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
                "bookingId" to movedBookingId.toString(),
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
        stubGetProfileDetails(fromOffenderNo, nomisProfileDetailsFromOffender(fromOffenderNo))
        stubGetProfileDetails(toOffenderNo, nomisProfileDetailsToOffenderReentered(toOffenderNo, movedBookingId))
        profileDetailsDpsApi.stubSyncProfileDetailsPhysicalAttributes(fromOffenderNo, syncProfileDetailsResponse())
        profileDetailsDpsApi.stubSyncProfileDetailsPhysicalAttributes(toOffenderNo, syncProfileDetailsResponse())
        nomisSyncApi.stubSyncPhysicalAttributes(toOffenderNo)

        // process event
        sendBookingMovedEvent().also {
          waitForAnyProcessingToComplete("prisonperson-booking-moved")
        }

        // the from offender is sync'd from NOMIS to DPS
        physicalAttributesDpsApi.verify(
          putRequestedFor(urlPathEqualTo("/sync/prisoners/$fromOffenderNo/profile-details-physical-attributes"))
            .withRequestBodyJsonPath("build.value", "MEDIUM"),
        )

        // the to offender IS sync'd from NOMIS to DPS
        physicalAttributesDpsApi.verify(
          count = 1,
          putRequestedFor(urlPathEqualTo("/sync/prisoners/$toOffenderNo/profile-details-physical-attributes"))
            .withRequestBodyJsonPath("build.value", "LARGE"),
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
                "bookingId" to movedBookingId.toString(),
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

      @Test
      fun `offender profile details re-entered in DPS`() {
        // the moved booking has BUILD re-entered in NOMIS
        stubGetProfileDetails(fromOffenderNo, nomisProfileDetailsFromOffender(fromOffenderNo, updatedInNomis = false))
        stubGetProfileDetails(toOffenderNo, nomisProfileDetailsToOffenderReentered(toOffenderNo, movedBookingId, updatedInNomis = false))
        profileDetailsDpsApi.stubSyncProfileDetailsPhysicalAttributes(fromOffenderNo, syncProfileDetailsResponse())
        profileDetailsDpsApi.stubSyncProfileDetailsPhysicalAttributes(toOffenderNo, syncProfileDetailsResponse())
        nomisSyncApi.stubSyncPhysicalAttributes(toOffenderNo)

        // process event
        sendBookingMovedEvent().also {
          waitForAnyProcessingToComplete("prisonperson-booking-moved")
        }

        // the from offender is sync'd from NOMIS to DPS
        physicalAttributesDpsApi.verify(
          putRequestedFor(urlPathEqualTo("/sync/prisoners/$fromOffenderNo/profile-details-physical-attributes"))
            .withRequestBodyJsonPath("build.value", "MEDIUM"),
        )

        // the to offender IS sync'd from NOMIS to DPS
        physicalAttributesDpsApi.verify(
          count = 1,
          putRequestedFor(urlPathEqualTo("/sync/prisoners/$toOffenderNo/profile-details-physical-attributes"))
            .withRequestBodyJsonPath("build.value", "LARGE"),
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
                "bookingId" to movedBookingId.toString(),
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

      @Test
      fun `mix of offender profile details re-entered and not re-entered`() {
        // the moved booking has BUILD and FACE re-entered in NOMIS but SHOESIZE is not
        stubGetProfileDetails(fromOffenderNo, nomisProfileDetailsFromOffenderSomeReentered(fromOffenderNo))
        stubGetProfileDetails(toOffenderNo, nomisProfileDetailsToOffenderSomeReentered(toOffenderNo, movedBookingId))
        profileDetailsDpsApi.stubSyncProfileDetailsPhysicalAttributes(fromOffenderNo, syncProfileDetailsResponse())
        profileDetailsDpsApi.stubSyncProfileDetailsPhysicalAttributes(toOffenderNo, syncProfileDetailsResponse())
        nomisSyncApi.stubSyncPhysicalAttributes(toOffenderNo)

        // process event
        sendBookingMovedEvent().also {
          waitForAnyProcessingToComplete("prisonperson-booking-moved")
        }

        // the from offender is sync'd from NOMIS to DPS for all 3 profile details
        physicalAttributesDpsApi.verify(
          count = 3,
          putRequestedFor(urlPathEqualTo("/sync/prisoners/$fromOffenderNo/profile-details-physical-attributes")),
        )

        // the to offender IS sync'd from NOMIS to DPS for BUILD and for FACE
        physicalAttributesDpsApi.verify(
          count = 2,
          putRequestedFor(urlPathEqualTo("/sync/prisoners/$toOffenderNo/profile-details-physical-attributes")),
        )
        // the to offender IS NOT sync'd from NOMIS to DPS for shoe size
        physicalAttributesDpsApi.verify(
          count = 0,
          putRequestedFor(urlPathEqualTo("/sync/prisoners/$toOffenderNo/profile-details-physical-attributes"))
            .withRequestBodyJsonPath("shoesize.value", "8.5"),
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
                "bookingId" to movedBookingId.toString(),
                "toOffenderNo" to toOffenderNo,
                "fromOffenderNo" to fromOffenderNo,
                "syncFromOffenderDps_BUILD" to "true",
                "syncToOffenderDps_BUILD" to "true",
                "syncFromOffenderDps_FACE" to "true",
                "syncToOffenderDps_FACE" to "true",
                // SHOESIZE is not re-entered in NOMIS so only sync "from offender" to DPS
                "syncFromOffenderDps_SHOESIZE" to "true",
                "syncToOffenderNomis" to "true",
              ),
            )
          },
          isNull(),
        )
      }
    }

    @Nested
    inner class Errors {
      @Test
      fun `should end up on DLQ if get from offender physical attributes fails`() {
        physicalAttributesNomisApi.stubGetPhysicalAttributes(fromOffenderNo, HttpStatus.BAD_GATEWAY)

        sendBookingMovedEvent().also {
          waitForDlqMessage()
        }

        verify(telemetryClient).trackEvent(
          eq("prisonperson-booking-moved-error"),
          check {
            assertThat(it).containsExactlyInAnyOrderEntriesOf(
              mapOf(
                "bookingId" to movedBookingId.toString(),
                "toOffenderNo" to toOffenderNo,
                "fromOffenderNo" to fromOffenderNo,
                "error" to "502 Bad Gateway from GET http://localhost:8081/prisoners/$fromOffenderNo/physical-attributes",
              ),
            )
          },
          isNull(),
        )
      }

      @Test
      fun `should end up on DLQ if get to offender physical attributes from NOMIS fails`() {
        stubGetPhysicalAttributesMissing(fromOffenderNo)
        physicalAttributesNomisApi.stubGetPhysicalAttributes(toOffenderNo, HttpStatus.BAD_GATEWAY)
        stubGetProfileDetailsMissing(fromOffenderNo)
        stubGetProfileDetailsMissing(toOffenderNo)

        sendBookingMovedEvent().also {
          waitForDlqMessage()
        }

        verify(telemetryClient).trackEvent(
          eq("prisonperson-booking-moved-error"),
          check {
            assertThat(it).containsExactlyInAnyOrderEntriesOf(
              mapOf(
                "bookingId" to movedBookingId.toString(),
                "toOffenderNo" to toOffenderNo,
                "fromOffenderNo" to fromOffenderNo,
                "error" to "502 Bad Gateway from GET http://localhost:8081/prisoners/$toOffenderNo/physical-attributes",
              ),
            )
          },
          isNull(),
        )
      }

      @Test
      fun `should end up on DLQ if get from offender profile details fails`() {
        stubGetPhysicalAttributesMissing(fromOffenderNo)
        stubGetPhysicalAttributesMissing(toOffenderNo)
        profileDetailsNomisApi.stubGetProfileDetails(fromOffenderNo, HttpStatus.BAD_GATEWAY)

        sendBookingMovedEvent().also {
          waitForDlqMessage()
        }

        verify(telemetryClient).trackEvent(
          eq("prisonperson-booking-moved-error"),
          check {
            assertThat(it).containsExactlyInAnyOrderEntriesOf(
              mapOf(
                "bookingId" to movedBookingId.toString(),
                "toOffenderNo" to toOffenderNo,
                "fromOffenderNo" to fromOffenderNo,
                "error" to "502 Bad Gateway from GET http://localhost:8081/prisoners/$fromOffenderNo/profile-details",
              ),
            )
          },
          isNull(),
        )
      }

      @Test
      fun `should end up on DLQ if get to offender profile details fails`() {
        stubGetPhysicalAttributesMissing(fromOffenderNo)
        stubGetPhysicalAttributesMissing(toOffenderNo)
        stubGetProfileDetailsMissing(fromOffenderNo)
        profileDetailsNomisApi.stubGetProfileDetails(toOffenderNo, HttpStatus.BAD_GATEWAY)

        sendBookingMovedEvent().also {
          waitForDlqMessage()
        }

        verify(telemetryClient).trackEvent(
          eq("prisonperson-booking-moved-error"),
          check {
            assertThat(it).containsExactlyInAnyOrderEntriesOf(
              mapOf(
                "bookingId" to movedBookingId.toString(),
                "toOffenderNo" to toOffenderNo,
                "fromOffenderNo" to fromOffenderNo,
                "error" to "502 Bad Gateway from GET http://localhost:8081/prisoners/$toOffenderNo/profile-details",
              ),
            )
          },
          isNull(),
        )
      }

      @Test
      fun `should end up on DLQ if sync from offender fails`() {
        stubGetPhysicalAttributesMissing(fromOffenderNo)
        stubGetPhysicalAttributesMissing(toOffenderNo)
        stubGetProfileDetails(fromOffenderNo, nomisProfileDetailsFromOffender(toOffenderNo))
        stubGetProfileDetails(toOffenderNo, nomisProfileDetailsToOffenderReentered(toOffenderNo, movedBookingId))
        profileDetailsDpsApi.stubSyncProfileDetailsPhysicalAttributes(fromOffenderNo, HttpStatus.BAD_GATEWAY)
        profileDetailsDpsApi.stubSyncProfileDetailsPhysicalAttributes(toOffenderNo, syncProfileDetailsResponse())

        sendBookingMovedEvent().also {
          waitForDlqMessage()
        }

        verify(telemetryClient).trackEvent(
          eq("prisonperson-booking-moved-error"),
          check {
            assertThat(it).containsExactlyInAnyOrderEntriesOf(
              mapOf(
                "bookingId" to movedBookingId.toString(),
                "toOffenderNo" to toOffenderNo,
                "fromOffenderNo" to fromOffenderNo,
                "error" to "502 Bad Gateway from PUT http://localhost:8095/sync/prisoners/B1234BB/profile-details-physical-attributes",
              ),
            )
          },
          isNull(),
        )
      }

      @Test
      fun `should end up on DLQ if sync to offender fails`() {
        stubGetPhysicalAttributesMissing(fromOffenderNo)
        stubGetPhysicalAttributesMissing(toOffenderNo)
        stubGetProfileDetailsMissing(fromOffenderNo)
        stubGetProfileDetails(toOffenderNo, nomisProfileDetailsToOffenderReentered(toOffenderNo, movedBookingId))
        profileDetailsDpsApi.stubSyncProfileDetailsPhysicalAttributes(fromOffenderNo, syncProfileDetailsResponse())
        profileDetailsDpsApi.stubSyncProfileDetailsPhysicalAttributes(toOffenderNo, HttpStatus.BAD_GATEWAY)

        sendBookingMovedEvent().also {
          waitForDlqMessage()
        }

        verify(telemetryClient).trackEvent(
          eq("prisonperson-booking-moved-error"),
          check {
            assertThat(it).containsExactlyInAnyOrderEntriesOf(
              mapOf(
                "bookingId" to movedBookingId.toString(),
                "toOffenderNo" to toOffenderNo,
                "fromOffenderNo" to fromOffenderNo,
                "error" to "502 Bad Gateway from PUT http://localhost:8095/sync/prisoners/A1234AA/profile-details-physical-attributes",
              ),
            )
          },
          isNull(),
        )
      }

      @Test
      fun `should NOT end up on DLQ if sync back to NOMIS fails`() {
        stubGetPhysicalAttributesMissing(fromOffenderNo)
        stubGetPhysicalAttributesMissing(toOffenderNo)
        stubGetProfileDetailsMissing(fromOffenderNo)
        stubGetProfileDetails(toOffenderNo, nomisProfileDetailsToOffenderReentered(toOffenderNo, movedBookingId))
        profileDetailsDpsApi.stubSyncProfileDetailsPhysicalAttributes(fromOffenderNo, syncProfileDetailsResponse())
        profileDetailsDpsApi.stubSyncProfileDetailsPhysicalAttributes(toOffenderNo, syncProfileDetailsResponse())
        nomisSyncApi.stubSyncPhysicalAttributes(toOffenderNo, HttpStatus.BAD_GATEWAY)

        sendBookingMovedEvent().also {
          waitForAnyProcessingToComplete("prisonperson-booking-moved-error")
        }

        verify(telemetryClient).trackEvent(
          eq("prisonperson-booking-moved-error"),
          check {
            assertThat(it).containsExactlyInAnyOrderEntriesOf(
              mapOf(
                "bookingId" to movedBookingId.toString(),
                "toOffenderNo" to toOffenderNo,
                "fromOffenderNo" to fromOffenderNo,
                "syncToOffenderDps_BUILD" to "true",
                "error" to "502 Bad Gateway from PUT http://localhost:8098/prisonperson/$toOffenderNo/physical-attributes",
              ),
            )
          },
          isNull(),
        )

        assertThat(
          awsSqsPrisonPersonOffenderEventDlqClient.countAllMessagesOnQueue(prisonPersonQueueOffenderEventsDlqUrl).get(),
        ).isEqualTo(0)
      }
    }

    private fun sendBookingMovedEvent(): SendMessageResponse? = awsSqsSentencingOffenderEventsClient.sendMessage(
      prisonPersonQueueOffenderEventsUrl,
      bookingMovedDomainEvent(
        eventType = "prison-offender-events.prisoner.booking.moved",
        bookingId = movedBookingId,
        movedFromNomsNumber = fromOffenderNo,
        movedToNomsNumber = toOffenderNo,
      ),
    )

    private fun stubGetPhysicalAttributesMissing(offenderNo: String) = physicalAttributesNomisApi.stubGetPhysicalAttributes(offenderNo, PrisonerPhysicalAttributesResponse(offenderNo, listOf()))
    private fun stubGetProfileDetailsMissing(offenderNo: String) = profileDetailsNomisApi.stubGetProfileDetails(offenderNo, PrisonerProfileDetailsResponse(offenderNo, listOf()))
    private fun stubGetPhysicalAttributes(offenderNo: String, prisonerPhysicalAttribute: PrisonerPhysicalAttributesResponse) = physicalAttributesNomisApi.stubGetPhysicalAttributes(offenderNo, prisonerPhysicalAttribute)
    private fun stubGetProfileDetails(offenderNo: String, prisonerProfileDetails: PrisonerProfileDetailsResponse) = profileDetailsNomisApi.stubGetProfileDetails(offenderNo, prisonerProfileDetails)
    private fun syncPhysicalAttributesResponse(ids: List<Long> = listOf(321)) = PhysicalAttributesSyncResponse(ids)
    private fun syncProfileDetailsResponse(ids: List<Long> = listOf(321)) = ProfileDetailsPhysicalAttributesSyncResponse(ids)

    private fun waitForDlqMessage() = await untilAsserted {
      assertThat(
        awsSqsPrisonPersonOffenderEventDlqClient.countAllMessagesOnQueue(prisonPersonQueueOffenderEventsDlqUrl).get(),
      ).isEqualTo(1)
    }
  }
}

fun nomisPhysicalAttributesFromOffender(
  offenderNo: String,
  updatedInNomis: Boolean = true,
  bookingStartTime: LocalDateTime = LocalDateTime.now().minusDays(1),
) = prisonerPhysicalAttributes(
  offenderNo = offenderNo,
  bookings = listOf(
    // The from offender has a single booking - the new booking has been copied to the to offender
    bookingPhysicalAttributes(
      bookingId = 2345L,
      activeBooking = false,
      latestBooking = true,
      physicalAttributes = listOf(
        physicalAttributes(
          height = 180,
          weight = 80,
          createDateTime = bookingStartTime.minusDays(7),
          modifiedDateTime = null,
          auditModuleName = if (updatedInNomis) "A_NOMIS_USER" else "DPS_SYNCHRONISATION",
        ),
      ),
    ),
  ),
)

fun nomisPhysicalAttributesToOffenderNotRemeasured(
  offenderNo: String,
  movedBookingId: Long,
  updatedInNomis: Boolean = true,
  bookingStartTime: LocalDateTime = LocalDateTime.now().minusDays(1),
) = PrisonerPhysicalAttributesResponse(
  offenderNo = offenderNo,
  bookings = listOf(
    // The to offender's old booking
    bookingPhysicalAttributes(
      bookingId = 3456L,
      activeBooking = false,
      latestBooking = false,
      physicalAttributes = listOf(
        physicalAttributes(
          height = 170,
          weight = 70,
          createDateTime = bookingStartTime.minusDays(7),
          modifiedDateTime = null,
          auditModuleName = if (updatedInNomis) "A_NOMIS_USER" else "DPS_SYNCHRONISATION",
        ),
      ),
    ),
    // The to offender's new booking has height and weight copied from the from offender
    bookingPhysicalAttributes(
      bookingId = movedBookingId,
      activeBooking = true,
      latestBooking = true,
      physicalAttributes = listOf(
        physicalAttributes(
          height = 180,
          weight = 80,
          createDateTime = bookingStartTime.minusDays(7),
          modifiedDateTime = null,
          auditModuleName = if (updatedInNomis) "A_NOMIS_USER" else "DPS_SYNCHRONISATION",
        ),
      ),
    ),
  ),
)

fun nomisPhysicalAttributesToOffenderRemeasured(
  offenderNo: String,
  bookingId: Long,
  updatedInNomis: Boolean = true,
  bookingStartTime: LocalDateTime = LocalDateTime.now().minusDays(1),
) = prisonerPhysicalAttributes(
  offenderNo = offenderNo,
  bookings = listOf(
    // The to offender's old booking
    bookingPhysicalAttributes(
      bookingId = 3456L,
      activeBooking = false,
      latestBooking = false,
      physicalAttributes = listOf(
        physicalAttributes(
          height = 170,
          weight = 70,
          createDateTime = bookingStartTime.minusDays(7),
          modifiedDateTime = null,
          auditModuleName = if (updatedInNomis) "A_NOMIS_USER" else "DPS_SYNCHRONISATION",
        ),
      ),
    ),
    // The to offender's new booking has weight re-entered hence new weight and modified time after booking start time
    bookingPhysicalAttributes(
      bookingId = bookingId,
      activeBooking = true,
      latestBooking = true,
      physicalAttributes = listOf(
        physicalAttributes(
          height = 170,
          weight = 90,
          createDateTime = bookingStartTime.minusDays(7),
          modifiedDateTime = bookingStartTime.plusDays(1),
          auditModuleName = if (updatedInNomis) "A_NOMIS_USER" else "DPS_SYNCHRONISATION",
        ),
      ),
    ),
  ),
)

fun physicalAttributes(
  height: Int,
  weight: Int,
  createDateTime: LocalDateTime,
  modifiedDateTime: LocalDateTime?,
  auditModuleName: String = "A_NOMIS_USER",
) = PhysicalAttributesResponse(
  attributeSequence = 1,
  heightCentimetres = height,
  weightKilograms = weight,
  createdBy = "A_USER",
  createDateTime = "$createDateTime",
  modifiedBy = modifiedDateTime?.let { "ANOTHER_USER" },
  modifiedDateTime = modifiedDateTime?.toString(),
  auditModuleName = auditModuleName,
)

fun bookingPhysicalAttributes(
  bookingId: Long,
  activeBooking: Boolean,
  latestBooking: Boolean,
  physicalAttributes: List<PhysicalAttributesResponse>,
) = BookingPhysicalAttributesResponse(
  bookingId = bookingId,
  startDateTime = if (activeBooking) "${LocalDateTime.now().minusDays(1)}" else "${LocalDateTime.now().minusDays(8)}",
  endDateTime = if (activeBooking) null else "${LocalDateTime.now().minusDays(6)}",
  latestBooking = latestBooking,
  physicalAttributes = physicalAttributes,
)

fun prisonerPhysicalAttributes(
  offenderNo: String,
  bookings: List<BookingPhysicalAttributesResponse>,
) = PrisonerPhysicalAttributesResponse(offenderNo, bookings)

fun nomisProfileDetailsFromOffender(
  offenderNo: String,
  updatedInNomis: Boolean = true,
  bookingStartTime: LocalDateTime = LocalDateTime.now().minusDays(1),
) = prisonerProfileDetails(
  offenderNo,
  listOf(
    bookingProfileDetails(
      bookingId = 2345L,
      activeBooking = false,
      latestBooking = true,
      profileDetails = listOf(
        profileDetails(
          type = "BUILD",
          code = "MEDIUM",
          createDateTime = bookingStartTime.minusDays(7),
          modifiedDateTime = null,
          auditModuleName = if (updatedInNomis) "A_NOMIS_USER" else "DPS_SYNCHRONISATION",
        ),
      ),
    ),
  ),
)

fun nomisProfileDetailsToOffenderNotReentered(
  offenderNo: String,
  bookingId: Long,
  bookingStartTime: LocalDateTime = LocalDateTime.now().minusDays(1),
) = prisonerProfileDetails(
  offenderNo = offenderNo,
  bookings = listOf(
    // The to offender's old booking
    bookingProfileDetails(
      bookingId = 3456L,
      activeBooking = false,
      latestBooking = false,
      profileDetails = listOf(
        profileDetails(
          type = "BUILD",
          code = "MEDIUM",
          createDateTime = bookingStartTime.minusDays(7),
          modifiedDateTime = null,
        ),
      ),
    ),
    // The to offender's new booking has build copied from the from offender
    bookingProfileDetails(
      bookingId = bookingId,
      activeBooking = true,
      latestBooking = true,
      profileDetails = listOf(
        profileDetails(
          type = "BUILD",
          code = "LARGE",
          createDateTime = bookingStartTime.minusDays(7),
          modifiedDateTime = null,
        ),
      ),
    ),
  ),
)

fun nomisProfileDetailsToOffenderReentered(
  offenderNo: String,
  bookingId: Long,
  updatedInNomis: Boolean = true,
  bookingStartTime: LocalDateTime = LocalDateTime.now().minusDays(1),
) = prisonerProfileDetails(
  offenderNo = offenderNo,
  bookings = listOf(
    // The to offender's old booking
    bookingProfileDetails(
      bookingId = 3456L,
      activeBooking = false,
      latestBooking = false,
      profileDetails = listOf(
        profileDetails(
          type = "BUILD",
          code = "MEDIUM",
          createDateTime = bookingStartTime.minusDays(7),
          modifiedDateTime = null,
          auditModuleName = if (updatedInNomis) "A_NOMIS_USER" else "DPS_SYNCHRONISATION",
        ),
      ),
    ),
    // The to offender's new booking has build changed hence modified time after booking start time
    bookingProfileDetails(
      bookingId = bookingId,
      activeBooking = true,
      latestBooking = true,
      profileDetails = listOf(
        profileDetails(
          type = "BUILD",
          code = "LARGE",
          createDateTime = bookingStartTime.minusDays(7),
          modifiedDateTime = bookingStartTime.plusDays(1),
          auditModuleName = if (updatedInNomis) "A_NOMIS_USER" else "DPS_SYNCHRONISATION",
        ),
      ),
    ),
  ),
)

fun nomisProfileDetailsFromOffenderSomeReentered(
  offenderNo: String,
  updatedInNomis: Boolean = true,
  bookingStartTime: LocalDateTime = LocalDateTime.now().minusDays(1),
) = prisonerProfileDetails(
  offenderNo,
  listOf(
    bookingProfileDetails(
      bookingId = 2345L,
      activeBooking = false,
      latestBooking = true,
      profileDetails = listOf(
        profileDetails(
          type = "BUILD",
          code = "MEDIUM",
          createDateTime = bookingStartTime.minusDays(7),
          modifiedDateTime = null,
          auditModuleName = if (updatedInNomis) "A_NOMIS_USER" else "DPS_SYNCHRONISATION",
        ),
        profileDetails(
          type = "FACE",
          code = "OVAL",
          createDateTime = bookingStartTime.minusDays(7),
          modifiedDateTime = null,
          auditModuleName = if (updatedInNomis) "A_NOMIS_USER" else "DPS_SYNCHRONISATION",
        ),
        profileDetails(
          type = "SHOESIZE",
          code = "8.5",
          createDateTime = bookingStartTime.minusDays(7),
          modifiedDateTime = null,
          auditModuleName = if (updatedInNomis) "A_NOMIS_USER" else "DPS_SYNCHRONISATION",
        ),
      ),
    ),
  ),
)

fun nomisProfileDetailsToOffenderSomeReentered(
  offenderNo: String,
  bookingId: Long,
  updatedInNomis: Boolean = true,
  bookingStartTime: LocalDateTime = LocalDateTime.now().minusDays(1),
) = prisonerProfileDetails(
  offenderNo = offenderNo,
  bookings = listOf(
    // The to offender's old booking
    bookingProfileDetails(
      bookingId = 3456L,
      activeBooking = false,
      latestBooking = false,
      profileDetails = listOf(
        profileDetails(
          type = "BUILD",
          code = "MEDIUM",
          createDateTime = bookingStartTime.minusDays(7),
          modifiedDateTime = null,
          auditModuleName = if (updatedInNomis) "A_NOMIS_USER" else "DPS_SYNCHRONISATION",
        ),
        profileDetails(
          type = "FACE",
          code = "OVAL",
          createDateTime = bookingStartTime.minusDays(7),
          modifiedDateTime = null,
          auditModuleName = if (updatedInNomis) "A_NOMIS_USER" else "DPS_SYNCHRONISATION",
        ),
        profileDetails(
          type = "SHOESIZE",
          code = "8.5",
          createDateTime = bookingStartTime.minusDays(7),
          modifiedDateTime = null,
          auditModuleName = if (updatedInNomis) "A_NOMIS_USER" else "DPS_SYNCHRONISATION",
        ),
      ),
    ),
    // The to offender's new booking has build changed in NOMIS but show size not
    bookingProfileDetails(
      bookingId = bookingId,
      activeBooking = true,
      latestBooking = true,
      profileDetails = listOf(
        profileDetails(
          type = "BUILD",
          code = "LARGE",
          createDateTime = bookingStartTime.minusDays(7),
          modifiedDateTime = bookingStartTime.plusDays(1),
          auditModuleName = if (updatedInNomis) "A_NOMIS_USER" else "DPS_SYNCHRONISATION",
        ),
        profileDetails(
          type = "FACE",
          code = "ROUND",
          createDateTime = bookingStartTime.minusDays(7),
          modifiedDateTime = bookingStartTime.plusDays(1),
          auditModuleName = if (updatedInNomis) "A_NOMIS_USER" else "DPS_SYNCHRONISATION",
        ),
        profileDetails(
          type = "SHOESIZE",
          code = "8.5",
          createDateTime = bookingStartTime.minusDays(7),
          modifiedDateTime = null,
        ),
      ),
    ),
  ),
)

private fun profileDetails(
  type: String,
  code: String,
  createDateTime: LocalDateTime,
  modifiedDateTime: LocalDateTime?,
  auditModuleName: String = "A_NOMIS_USER",
) = ProfileDetailsResponse(
  type = type,
  code = code,
  createDateTime = "$createDateTime",
  createdBy = "A_USER",
  modifiedDateTime = modifiedDateTime?.toString(),
  modifiedBy = modifiedDateTime?.let { "ANOTHER_USER" },
  auditModuleName = auditModuleName,
)

private fun bookingProfileDetails(
  bookingId: Long,
  activeBooking: Boolean,
  latestBooking: Boolean,
  profileDetails: List<ProfileDetailsResponse>,
) = BookingProfileDetailsResponse(
  bookingId = bookingId,
  startDateTime = if (activeBooking) "${LocalDateTime.now().minusDays(1)}" else "${LocalDateTime.now().minusDays(8)}",
  latestBooking = latestBooking,
  profileDetails = profileDetails,
)

private fun prisonerProfileDetails(
  offenderNo: String,
  bookings: List<BookingProfileDetailsResponse>,
) = PrisonerProfileDetailsResponse(offenderNo, bookings)
