package uk.gov.justice.digital.hmpps.prisonerfromnomismigration.prisonperson.physicalattributes

import com.github.tomakehurst.wiremock.client.WireMock.absent
import com.github.tomakehurst.wiremock.client.WireMock.equalTo
import com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor
import com.github.tomakehurst.wiremock.client.WireMock.matchingJsonPath
import com.github.tomakehurst.wiremock.client.WireMock.putRequestedFor
import com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo
import com.github.tomakehurst.wiremock.matching.StringValuePattern
import kotlinx.coroutines.test.runTest
import org.assertj.core.api.Assertions.assertThat
import org.awaitility.kotlin.await
import org.awaitility.kotlin.untilAsserted
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.mockito.kotlin.check
import org.mockito.kotlin.eq
import org.mockito.kotlin.isNull
import org.mockito.kotlin.verify
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.http.HttpStatus.BAD_REQUEST
import org.springframework.http.HttpStatus.NOT_FOUND
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.integration.SqsIntegrationTestBase
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.integration.sendMessage
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomissync.model.BookingPhysicalAttributesResponse
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomissync.model.PhysicalAttributesResponse
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomissync.model.PrisonerPhysicalAttributesResponse
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.prisonperson.model.PhysicalAttributesSyncResponse
import uk.gov.justice.hmpps.sqs.countAllMessagesOnQueue
import java.time.LocalDateTime
import java.time.ZoneId

class PhysicalAttributesSyncIntTest : SqsIntegrationTestBase() {

  @Autowired
  private lateinit var nomisApi: PhysicalAttributesNomisApiMockServer

  @Autowired
  private lateinit var dpsApi: PhysicalAttributesDpsApiMockServer

  private val now = LocalDateTime.now()
  private val yesterday = now.minusDays(1)
  private val offenderNo = "A1234AA"

  @Nested
  @DisplayName("OFFENDER_PHYSICAL_ATTRIBUTES-CHANGED")
  inner class PhysicalAttributesChanged {
    @Nested
    inner class SingleBooking {

      @Test
      fun `should sync new physical attributes`() {
        nomisApi.stubGetPhysicalAttributes(
          offenderNo,
          aPrisonerPhysicalAttributesResponse(
            bookings = listOf(
              aBookingResponse(
                startDateTime = "$yesterday",
                endDateTime = null,
                physicalAttributes = listOf(
                  aPhysicalAttributesResponse(
                    heightCentimetres = 180,
                    weightKilograms = 80,
                    modifiedDateTime = "$now",
                    modifiedBy = "A_USER",
                  ),
                ),
              ),
            ),
          ),
        )
        dpsApi.stubSyncPhysicalAttributes(aResponse())

        sendPhysicalAttributesChangedEvent()

        verifyResults(
          dpsUpdates = mapOf(
            "height" to equalTo("180"),
            "weight" to equalTo("80"),
            "appliesFrom" to equalTo("${yesterday.toZoned()}"),
            "appliesTo" to absent(),
            "latestBooking" to equalTo("true"),
            "createdAt" to equalTo("${now.toZoned()}"),
            "createdBy" to equalTo("A_USER"),
          ),
        )
      }

      @Test
      fun `should ignore a new empty booking (when a new prisoner is created)`() {
        nomisApi.stubGetPhysicalAttributes(
          offenderNo,
          aPrisonerPhysicalAttributesResponse(
            bookings = listOf(
              aBookingResponse(
                endDateTime = null,
                physicalAttributes = listOf(
                  aPhysicalAttributesResponse(
                    heightCentimetres = null,
                    weightKilograms = null,
                    modifiedDateTime = null,
                  ),
                ),
              ),
            ),
          ),
        )

        sendPhysicalAttributesChangedEvent()

        verifyResults(type = "ignored", reason = "New physical attributes are empty")
      }

      @Test
      fun `should sync last created physical attributes`() {
        nomisApi.stubGetPhysicalAttributes(
          offenderNo,
          aPrisonerPhysicalAttributesResponse(
            bookings = listOf(
              aBookingResponse(
                endDateTime = null,
                physicalAttributes = listOf(
                  aPhysicalAttributesResponse(
                    attributeSequence = 1,
                    heightCentimetres = 180,
                    weightKilograms = 80,
                    createDateTime = "$yesterday",
                    modifiedDateTime = null,
                  ),
                  aPhysicalAttributesResponse(
                    attributeSequence = 2,
                    heightCentimetres = 170,
                    weightKilograms = 70,
                    createDateTime = "$now",
                    modifiedDateTime = null,
                  ),
                ),
              ),
            ),
          ),
        )
        dpsApi.stubSyncPhysicalAttributes(aResponse())

        sendPhysicalAttributesChangedEvent()

        verifyResults(
          attributeSequence = 2,
          dpsUpdates = mapOf(
            "height" to equalTo("170"),
            "weight" to equalTo("70"),
          ),
        )
      }

      @Test
      fun `should sync last modified physical attributes`() {
        nomisApi.stubGetPhysicalAttributes(
          offenderNo,
          aPrisonerPhysicalAttributesResponse(
            bookings = listOf(
              aBookingResponse(
                endDateTime = null,
                physicalAttributes = listOf(
                  aPhysicalAttributesResponse(
                    attributeSequence = 1,
                    heightCentimetres = 180,
                    weightKilograms = 80,
                    createDateTime = "${now.minusDays(3)}",
                    modifiedDateTime = "${now.minusDays(1)}",
                    modifiedBy = "MODIFY_USER",
                  ),
                  aPhysicalAttributesResponse(
                    attributeSequence = 2,
                    heightCentimetres = 170,
                    weightKilograms = 70,
                    createDateTime = "${now.minusDays(2)}",
                    modifiedDateTime = null,
                  ),
                ),
              ),
            ),
          ),
        )
        dpsApi.stubSyncPhysicalAttributes(aResponse())

        sendPhysicalAttributesChangedEvent()

        verifyResults(
          attributeSequence = 1,
          dpsUpdates = mapOf(
            "height" to equalTo("180"),
            "weight" to equalTo("80"),
            "createdAt" to equalTo("${now.minusDays(1).toZoned()}"),
            "createdBy" to equalTo("MODIFY_USER"),
          ),
        )
      }

      @Test
      fun `should ignore physical attributes updated by the synchronisation service`() {
        nomisApi.stubGetPhysicalAttributes(
          offenderNo,
          aPrisonerPhysicalAttributesResponse(
            bookings = listOf(
              aBookingResponse(
                endDateTime = null,
                physicalAttributes = listOf(
                  aPhysicalAttributesResponse(
                    heightCentimetres = 180,
                    weightKilograms = 80,
                    auditModuleName = "DPS_SYNCHRONISATION",
                  ),
                ),
              ),
            ),
          ),
        )

        sendPhysicalAttributesChangedEvent()

        verifyResults(type = "ignored", reason = "The physical attributes were created by DPS_SYNCHRONISATION")
      }
    }

    @Nested
    inner class MultipleBookings {
      @Test
      fun `should sync the latest booking`() {
        nomisApi.stubGetPhysicalAttributes(
          offenderNo,
          aPrisonerPhysicalAttributesResponse(
            bookings = listOf(
              aBookingResponse(
                bookingId = 12344,
                endDateTime = "$yesterday",
                latestBooking = false,
                physicalAttributes = listOf(
                  aPhysicalAttributesResponse(
                    heightCentimetres = 180,
                    weightKilograms = 80,
                  ),
                ),
              ),
              aBookingResponse(
                bookingId = 12345,
                startDateTime = "$now",
                endDateTime = null,
                latestBooking = true,
                physicalAttributes = listOf(
                  aPhysicalAttributesResponse(
                    heightCentimetres = 170,
                    weightKilograms = 70,
                  ),
                ),
              ),
            ),
          ),
        )
        dpsApi.stubSyncPhysicalAttributes(aResponse())

        sendPhysicalAttributesChangedEvent(bookingId = 12345)

        verifyResults(
          bookingId = 12345,
          dpsUpdates = mapOf(
            "height" to equalTo("170"),
            "weight" to equalTo("70"),
            "appliesFrom" to equalTo("${now.toZoned()}"),
            "appliesTo" to absent(),
            "latestBooking" to equalTo("true"),
          ),
        )
      }

      @Test
      fun `should sync a historical booking`() {
        nomisApi.stubGetPhysicalAttributes(
          offenderNo,
          aPrisonerPhysicalAttributesResponse(
            bookings = listOf(
              aBookingResponse(
                bookingId = 12344,
                startDateTime = "${now.minusDays(3)}",
                endDateTime = "${now.minusDays(2)}",
                latestBooking = false,
                physicalAttributes = listOf(
                  aPhysicalAttributesResponse(
                    heightCentimetres = 180,
                    weightKilograms = 80,
                  ),
                ),
              ),
              aBookingResponse(
                bookingId = 12345,
                startDateTime = "$now",
                endDateTime = null,
                latestBooking = true,
                physicalAttributes = listOf(
                  aPhysicalAttributesResponse(
                    heightCentimetres = 170,
                    weightKilograms = 70,
                  ),
                ),
              ),
            ),
          ),
        )
        dpsApi.stubSyncPhysicalAttributes(aResponse())

        sendPhysicalAttributesChangedEvent(bookingId = 12344)

        verifyResults(
          bookingId = 12344,
          dpsUpdates = mapOf(
            "height" to equalTo("180"),
            "weight" to equalTo("80"),
            "appliesFrom" to equalTo("${now.minusDays(3).toZoned()}"),
            "appliesTo" to equalTo("${now.minusDays(2).toZoned()}"),
            "latestBooking" to equalTo("false"),
          ),
        )
      }

      @Test
      fun `should sync a historical booking on and old attribute seq`() {
        nomisApi.stubGetPhysicalAttributes(
          offenderNo,
          aPrisonerPhysicalAttributesResponse(
            bookings = listOf(
              aBookingResponse(
                bookingId = 12344,
                startDateTime = "${now.minusDays(3)}",
                endDateTime = "${now.minusDays(2)}",
                latestBooking = false,
                physicalAttributes = listOf(
                  aPhysicalAttributesResponse(
                    attributeSequence = 1,
                    heightCentimetres = 180,
                    weightKilograms = 80,
                    modifiedDateTime = "$now",
                  ),
                  aPhysicalAttributesResponse(
                    attributeSequence = 2,
                    heightCentimetres = 190,
                    weightKilograms = 90,
                    modifiedDateTime = "$yesterday",
                  ),
                ),
              ),
              aBookingResponse(
                bookingId = 12345,
                startDateTime = "$now",
                endDateTime = null,
                latestBooking = true,
                physicalAttributes = listOf(
                  aPhysicalAttributesResponse(
                    heightCentimetres = 170,
                    weightKilograms = 70,
                  ),
                ),
              ),
            ),
          ),
        )
        dpsApi.stubSyncPhysicalAttributes(aResponse())

        sendPhysicalAttributesChangedEvent(bookingId = 12344)

        verifyResults(
          bookingId = 12344,
          attributeSequence = 1,
          dpsUpdates = mapOf(
            "height" to equalTo("180"),
            "weight" to equalTo("80"),
            "appliesFrom" to equalTo("${now.minusDays(3).toZoned()}"),
            "appliesTo" to equalTo("${now.minusDays(2).toZoned()}"),
            "latestBooking" to equalTo("false"),
          ),
        )
      }

      @Test
      fun `should sync a historical booking which is the latest booking`() {
        nomisApi.stubGetPhysicalAttributes(
          offenderNo,
          aPrisonerPhysicalAttributesResponse(
            bookings = listOf(
              aBookingResponse(
                bookingId = 12344,
                startDateTime = "${now.minusDays(3)}",
                endDateTime = "${now.minusDays(2)}",
                latestBooking = true,
                physicalAttributes = listOf(
                  aPhysicalAttributesResponse(
                    attributeSequence = 1,
                    heightCentimetres = 180,
                    weightKilograms = 80,
                    modifiedDateTime = "$now",
                  ),
                ),
              ),
            ),
          ),
        )
        dpsApi.stubSyncPhysicalAttributes(aResponse())

        sendPhysicalAttributesChangedEvent(bookingId = 12344)

        verifyResults(
          bookingId = 12344,
          attributeSequence = 1,
          dpsUpdates = mapOf(
            "height" to equalTo("180"),
            "weight" to equalTo("80"),
            "appliesFrom" to equalTo("${now.minusDays(3).toZoned()}"),
            "appliesTo" to equalTo("${now.minusDays(2).toZoned()}"),
            "latestBooking" to equalTo("true"),
          ),
        )
      }
    }

    @Nested
    inner class Errors {

      @Test
      fun `should put message on DLQ if NOMIS returns error`() = runTest {
        nomisApi.stubGetPhysicalAttributes(NOT_FOUND)

        sendPhysicalAttributesChangedEvent()

        await untilAsserted {
          assertThat(
            awsSqsPrisonPersonOffenderEventDlqClient.countAllMessagesOnQueue(prisonPersonQueueOffenderEventsDlqUrl).get(),
          ).isEqualTo(1)
        }
        nomisApi.verify(getRequestedFor(urlPathEqualTo("/prisoners/A1234AA/physical-attributes")))
        dpsApi.verify(0, putRequestedFor(urlPathEqualTo("/sync/prisoners/A1234AA/physical-attributes")))
        verify(telemetryClient).trackEvent(
          eq("physical-attributes-synchronisation-error"),
          check {
            assertThat(it["offenderNo"]).isEqualTo("A1234AA")
            assertThat(it["bookingId"]).isEqualTo("12345")
            assertThat(it["error"]).contains("Not Found")
          },
          isNull(),
        )
      }

      @Test
      fun `should put message on DLQ if DPS returns error`() = runTest {
        nomisApi.stubGetPhysicalAttributes(offenderNo, aPrisonerPhysicalAttributesResponse())
        dpsApi.stubSyncPhysicalAttributes(BAD_REQUEST)

        sendPhysicalAttributesChangedEvent()

        await untilAsserted {
          assertThat(
            awsSqsPrisonPersonOffenderEventDlqClient.countAllMessagesOnQueue(prisonPersonQueueOffenderEventsDlqUrl).get(),
          ).isEqualTo(1)
        }
        nomisApi.verify(getRequestedFor(urlPathEqualTo("/prisoners/A1234AA/physical-attributes")))
        dpsApi.verify(putRequestedFor(urlPathEqualTo("/sync/prisoners/A1234AA/physical-attributes")))
        verify(telemetryClient).trackEvent(
          eq("physical-attributes-synchronisation-error"),
          check {
            assertThat(it["offenderNo"]).isEqualTo("A1234AA")
            assertThat(it["bookingId"]).isEqualTo("12345")
            assertThat(it["attributeSequence"]).isEqualTo("1")
            assertThat(it["error"]).contains("Bad Request")
          },
          isNull(),
        )
      }

      @Test
      fun `should put message on DLQ if booking has no physical attributes`() = runTest {
        nomisApi.stubGetPhysicalAttributes(
          offenderNo,
          aPrisonerPhysicalAttributesResponse(bookings = listOf(aBookingResponse(bookingId = 12345))),
        )

        sendPhysicalAttributesChangedEvent(bookingId = 54321)

        await untilAsserted {
          assertThat(
            awsSqsPrisonPersonOffenderEventDlqClient.countAllMessagesOnQueue(prisonPersonQueueOffenderEventsDlqUrl).get(),
          ).isEqualTo(1)
        }
        nomisApi.verify(getRequestedFor(urlPathEqualTo("/prisoners/A1234AA/physical-attributes")))
        dpsApi.verify(0, putRequestedFor(urlPathEqualTo("/sync/prisoners/A1234AA/physical-attributes")))
        verify(telemetryClient).trackEvent(
          eq("physical-attributes-synchronisation-error"),
          check {
            assertThat(it["offenderNo"]).isEqualTo("A1234AA")
            assertThat(it["bookingId"]).isEqualTo("54321")
            assertThat(it["error"]).contains("Booking with physical attributes not found")
          },
          isNull(),
        )
      }
    }

    private fun physicalAttributesChangedEvent(bookingId: Int = 12345) = """
   {
      "Type" : "Notification",
      "MessageId" : "298a61d6-e078-51f2-9c60-3f348f8bde68",
      "TopicArn" : "arn:aws:sns:eu-west-2:754256621582:cloud-platform-Digital-Prison-Services-f221e27fcfcf78f6ab4f4c3cc165eee7",
      "Message" : "{\"eventType\":\"OFFENDER_PHYSICAL_ATTRIBUTES-CHANGED\",\"eventDatetime\":\"2024-06-11T16:30:59\",\"offenderIdDisplay\":\"A1234AA\",\"bookingId\":$bookingId}",
      "Timestamp" : "2024-06-11T15:30:59.048Z",
      "SignatureVersion" : "1",
      "Signature" : "kyuV8tDWmRoixtnyXauR/mzBdkO4yWXEFLZU6256JRIRfcGBNdn7+TPcRnM7afa6N6DwUs3TDKQ17U7W8hkB86r/J1PsfEpF8qOr8bZd4J/RDNAHJxmNnuTzy351ISDYjdxccREF57pLXtaMcu0Z6nJTTnv9pn7qOVasuxUIGANaD214P6iXkWvsFj0AgR1TVITHW5jMFTE+ln2PTLQ9N6dwx4/foIlFsQu7rWnx3hy9+x7gtInnDIaQSvI2gHQQI51TpQrES0YKjn5Tb25ANS8bZooK7knt9F+Hv3bejDyXWgR3fyC4SJbUvbVhfVI/aRhOv/qLwFGSOFKt6I0KAA==",
      "SigningCertURL" : "https://sns.eu-west-2.amazonaws.com/SimpleNotificationService-60eadc530605d63b8e62a523676ef735.pem",
      "UnsubscribeURL" : "https://sns.eu-west-2.amazonaws.com/?Action=Unsubscribe&SubscriptionArn=arn:aws:sns:eu-west-2:754256621582:cloud-platform-Digital-Prison-Services-f221e27fcfcf78f6ab4f4c3cc165eee7:e5c3f313-ccda-4a2f-9667-e2519fd01a19",
      "MessageAttributes" : {
        "publishedAt" : {"Type":"String","Value":"2024-06-11T16:30:59.023769832+01:00"},
        "eventType" : {"Type":"String","Value":"OFFENDER_PHYSICAL_ATTRIBUTES-CHANGED"}
      }
   }
    """.trimIndent()

    private fun aPhysicalAttributesResponse(
      attributeSequence: Int = 1,
      heightCentimetres: Int? = 180,
      weightKilograms: Int? = 80,
      createdBy: String = "A_USER",
      createDateTime: String = "$yesterday",
      modifiedBy: String? = "ANOTHER_USER",
      modifiedDateTime: String? = "$now",
      auditModuleName: String = "MODULE",
    ) =
      PhysicalAttributesResponse(
        attributeSequence = attributeSequence.toLong(),
        heightCentimetres = heightCentimetres,
        weightKilograms = weightKilograms,
        createdBy = createdBy,
        createDateTime = createDateTime,
        modifiedBy = modifiedBy,
        modifiedDateTime = modifiedDateTime,
        auditModuleName = auditModuleName,
      )

    private fun aBookingResponse(
      bookingId: Long = 12345,
      startDateTime: String = "2024-02-03T12:34:56",
      endDateTime: String? = "2024-10-21T12:34:56",
      latestBooking: Boolean = true,
      physicalAttributes: List<PhysicalAttributesResponse> = listOf(aPhysicalAttributesResponse()),
    ) =
      BookingPhysicalAttributesResponse(
        bookingId = bookingId,
        startDateTime = startDateTime,
        endDateTime = endDateTime,
        latestBooking = latestBooking,
        physicalAttributes = physicalAttributes,
      )

    private fun aPrisonerPhysicalAttributesResponse(
      offenderNo: String = "A1234AA",
      bookings: List<BookingPhysicalAttributesResponse> = listOf(aBookingResponse()),
    ) =
      PrisonerPhysicalAttributesResponse(
        offenderNo = offenderNo,
        bookings = bookings,
      )

    private fun aResponse(ids: List<Long> = listOf(321)) = PhysicalAttributesSyncResponse(ids)

    private fun LocalDateTime.toZoned() = atZone(ZoneId.of("Europe/London"))

    private fun sendPhysicalAttributesChangedEvent(bookingId: Int = 12345) =
      awsSqsPrisonPersonOffenderEventsClient.sendMessage(
        prisonPersonQueueOffenderEventsUrl,
        physicalAttributesChangedEvent(bookingId = bookingId),
      ).also { waitForAnyProcessingToComplete() }

    private fun verifyResults(
      type: String = "updated",
      bookingId: Int = 12345,
      attributeSequence: Int = 1,
      reason: String? = null,
      dpsUpdates: Map<String, StringValuePattern> = mapOf(),
    ) {
      // We should always call the NOMIS API
      nomisApi.verify(getRequestedFor(urlPathEqualTo("/prisoners/A1234AA/physical-attributes")))

      // For updates verify we sent the correct details to the DPS API
      if (type == "updated") {
        dpsApi.verify(
          putRequestedFor(urlPathEqualTo("/sync/prisoners/A1234AA/physical-attributes"))
            .apply {
              dpsUpdates.forEach { (jsonPath, pattern) ->
                withRequestBody(matchingJsonPath(jsonPath, pattern))
              }
            },
        )
      } else {
        // If not updated we shouldn't call the DPS API
        dpsApi.verify(0, putRequestedFor(urlPathEqualTo("/sync/prisoners/A1234AA/physical-attributes")))
      }

      verify(telemetryClient).trackEvent(
        eq("physical-attributes-synchronisation-$type"),
        check {
          assertThat(it["offenderNo"]).isEqualTo("A1234AA")
          assertThat(it["bookingId"]).isEqualTo("$bookingId")
          assertThat(it["attributeSequence"]).isEqualTo("$attributeSequence")
          // Verify the ignored reason if expected
          reason?.run { assertThat(it["reason"]).isEqualTo(this) }
          // For updates verify we track the DPS ID returned
          if (type == "updated") {
            assertThat(it["physicalAttributesHistoryId"]).isEqualTo("[321]")
          }
        },
        isNull(),
      )
    }
  }
}
