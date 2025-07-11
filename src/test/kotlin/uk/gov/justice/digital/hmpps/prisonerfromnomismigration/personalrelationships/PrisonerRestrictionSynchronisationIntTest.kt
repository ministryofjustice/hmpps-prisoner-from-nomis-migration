package uk.gov.justice.digital.hmpps.prisonerfromnomismigration.personalrelationships

import com.github.tomakehurst.wiremock.client.WireMock.deleteRequestedFor
import com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor
import com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor
import com.github.tomakehurst.wiremock.client.WireMock.putRequestedFor
import com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.mockito.Mockito.eq
import org.mockito.kotlin.check
import org.mockito.kotlin.isNull
import org.mockito.kotlin.verify
import org.springframework.beans.factory.annotation.Autowired
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.helper.bookingMovedDomainEvent
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.helper.mergeDomainEvent
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.helper.prisonerReceivedDomainEvent
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.integration.SqsIntegrationTestBase
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.integration.sendMessage
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomismappings.model.PrisonerRestrictionMappingDto
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomisprisoner.model.CodeDescription
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomisprisoner.model.ContactRestrictionEnteredStaff
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.personalrelationships.ContactPersonDpsApiExtension.Companion.dpsContactPersonServer
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.personalrelationships.ContactPersonDpsApiExtension.Companion.getRequestBody
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.personalrelationships.ContactPersonDpsApiMockServer.Companion.dpsPrisonerRestriction
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.personalrelationships.model.SyncCreatePrisonerRestrictionRequest
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.personalrelationships.model.SyncUpdatePrisonerRestrictionRequest
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.wiremock.MappingApiExtension.Companion.mappingApi
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.wiremock.withRequestBodyJsonPath
import java.time.LocalDate
import java.time.LocalDateTime

class PrisonerRestrictionSynchronisationIntTest : SqsIntegrationTestBase() {
  @Autowired
  private lateinit var nomisApiMock: ContactPersonNomisApiMockServer

  private val dpsApiMock = dpsContactPersonServer

  @Autowired
  private lateinit var mappingApiMock: PrisonerRestrictionMappingApiMockServer

  @Nested
  @DisplayName("RESTRICTION-UPSERTED created")
  inner class PrisonerRestrictionCreated {
    private val nomisRestrictionId = 3456L
    private val dpsRestrictionId = 65432L
    private val offenderNo = "A1234KT"

    @Nested
    inner class WhenCreatedInDps {
      @BeforeEach
      fun setUp() {
        personalRelationshipsOffenderEventsQueue.sendMessage(
          prisonerRestrictionEvent(
            eventType = "RESTRICTION-UPSERTED",
            isUpdated = false,
            offenderNo = offenderNo,
            restrictionId = nomisRestrictionId,
            auditModuleName = "DPS_SYNCHRONISATION",
          ),
        ).also { waitForAnyProcessingToComplete() }
      }

      @Test
      fun `will track telemetry`() {
        verify(telemetryClient).trackEvent(
          eq("contactperson-prisoner-restriction-synchronisation-created-skipped"),
          check {
            assertThat(it["offenderNo"]).isEqualTo(offenderNo)
            assertThat(it["nomisRestrictionId"]).isEqualTo(nomisRestrictionId.toString())
          },
          isNull(),
        )
      }
    }

    @Nested
    inner class WhenCreatedInNomis {
      @BeforeEach
      fun setUp() {
        mappingApiMock.stubGetByNomisPrisonerRestrictionIdOrNull(nomisRestrictionId = nomisRestrictionId, mapping = null)
        nomisApiMock.stubGetPrisonerRestrictionById(
          restrictionId = nomisRestrictionId,
          nomisPrisonerRestriction().copy(
            id = nomisRestrictionId,
            offenderNo = offenderNo,
            bookingSequence = 1,
            type = CodeDescription("BAN", "Banned"),
            effectiveDate = LocalDate.parse("2021-10-15"),
            enteredStaff = ContactRestrictionEnteredStaff(staffId = 123, username = "H.HARRY"),
            authorisedStaff = ContactRestrictionEnteredStaff(staffId = 456, username = "U.BELLY"),
            audit = nomisAudit().copy(createDatetime = LocalDateTime.parse("2021-10-15T12:00:00")),
            comment = "Banned for life",
            expiryDate = LocalDate.parse("2031-10-15"),
          ),
        )
        dpsApiMock.stubCreatePrisonerRestriction(dpsPrisonerRestriction().copy(prisonerRestrictionId = dpsRestrictionId))
        mappingApiMock.stubCreateMapping()
        personalRelationshipsOffenderEventsQueue.sendMessage(
          prisonerRestrictionEvent(
            eventType = "RESTRICTION-UPSERTED",
            isUpdated = false,
            offenderNo = offenderNo,
            restrictionId = nomisRestrictionId,
            auditModuleName = "OCUMINN",
          ),
        ).also { waitForAnyProcessingToComplete() }
      }

      @Test
      fun `will check if mapping already exists for restriction`() {
        mappingApi.verify(getRequestedFor(urlPathEqualTo("/mapping/contact-person/prisoner-restriction/nomis-prisoner-restriction-id/$nomisRestrictionId")))
      }

      @Test
      fun `will retrieve the restrictions details from NOMIS`() {
        nomisApiMock.verify(getRequestedFor(urlPathEqualTo("/prisoners/restrictions/$nomisRestrictionId")))
      }

      @Test
      fun `will create restriction in DPS`() {
        val request: SyncCreatePrisonerRestrictionRequest = getRequestBody(postRequestedFor(urlPathEqualTo("/sync/prisoner-restriction")))
        with(request) {
          assertThat(prisonerNumber).isEqualTo(offenderNo)
          assertThat(createdBy).isEqualTo("H.HARRY")
          assertThat(createdTime).isEqualTo(LocalDateTime.parse("2021-10-15T12:00:00"))
          assertThat(effectiveDate).isEqualTo(LocalDate.parse("2021-10-15"))
          assertThat(expiryDate).isEqualTo(LocalDate.parse("2031-10-15"))
          assertThat(authorisedUsername).isEqualTo("U.BELLY")
          assertThat(commentText).isEqualTo("Banned for life")
          assertThat(currentTerm).isTrue
          assertThat(restrictionType).isEqualTo("BAN")
        }
      }

      @Test
      fun `will create mapping between DPS and NOMIS Ids`() {
        mappingApiMock.verify(
          postRequestedFor(urlPathEqualTo("/mapping/contact-person/prisoner-restriction"))
            .withRequestBodyJsonPath("dpsId", dpsRestrictionId.toString())
            .withRequestBodyJsonPath("nomisId", nomisRestrictionId.toString())
            .withRequestBodyJsonPath("offenderNo", offenderNo)
            .withRequestBodyJsonPath("mappingType", "NOMIS_CREATED"),
        )
      }

      @Test
      fun `will track telemetry`() {
        verify(telemetryClient).trackEvent(
          eq("contactperson-prisoner-restriction-synchronisation-created-success"),
          check {
            assertThat(it["offenderNo"]).isEqualTo(offenderNo)
            assertThat(it["nomisRestrictionId"]).isEqualTo(nomisRestrictionId.toString())
          },
          isNull(),
        )
      }
    }

    @Nested
    inner class WhenMappingFails {
      @BeforeEach
      fun setUp() {
        mappingApiMock.stubGetByNomisPrisonerRestrictionIdOrNull(nomisRestrictionId = nomisRestrictionId, mapping = null)
        nomisApiMock.stubGetPrisonerRestrictionById(
          restrictionId = nomisRestrictionId,
        )
        dpsApiMock.stubCreatePrisonerRestriction(dpsPrisonerRestriction().copy(prisonerRestrictionId = dpsRestrictionId))
        mappingApiMock.stubCreateMappingFailureFollowedBySuccess()
        personalRelationshipsOffenderEventsQueue.sendMessage(
          prisonerRestrictionEvent(
            eventType = "RESTRICTION-UPSERTED",
            isUpdated = false,
            offenderNo = offenderNo,
            restrictionId = nomisRestrictionId,
            auditModuleName = "OCUMINN",
          ),
        ).also { waitForAnyProcessingToComplete("contactperson-prisoner-restriction-mapping-synchronisation-created") }
      }

      @Test
      fun `will create restriction in DPS once`() {
        dpsApiMock.verify(1, postRequestedFor(urlPathEqualTo("/sync/prisoner-restriction")))
      }

      @Test
      fun `will create mapping between DPS and NOMIS Ids eventually`() {
        mappingApiMock.verify(
          2,
          postRequestedFor(urlPathEqualTo("/mapping/contact-person/prisoner-restriction"))
            .withRequestBodyJsonPath("dpsId", dpsRestrictionId.toString())
            .withRequestBodyJsonPath("nomisId", nomisRestrictionId.toString())
            .withRequestBodyJsonPath("offenderNo", offenderNo)
            .withRequestBodyJsonPath("mappingType", "NOMIS_CREATED"),
        )
      }

      @Test
      fun `will track telemetry`() {
        verify(telemetryClient).trackEvent(
          eq("contactperson-prisoner-restriction-synchronisation-created-success"),
          check {
            assertThat(it["offenderNo"]).isEqualTo(offenderNo)
            assertThat(it["nomisRestrictionId"]).isEqualTo(nomisRestrictionId.toString())
          },
          isNull(),
        )
        verify(telemetryClient).trackEvent(
          eq("contactperson-prisoner-restriction-mapping-synchronisation-created"),
          check {
            assertThat(it["offenderNo"]).isEqualTo(offenderNo)
            assertThat(it["nomisRestrictionId"]).isEqualTo(nomisRestrictionId.toString())
          },
          isNull(),
        )
      }
    }

    @Nested
    inner class WhenCreatedAlready {
      @BeforeEach
      fun setUp() {
        mappingApiMock.stubGetByNomisPrisonerRestrictionIdOrNull(
          nomisRestrictionId = nomisRestrictionId,
          dpsRestrictionId = dpsRestrictionId.toString(),
          PrisonerRestrictionMappingDto(
            dpsId = dpsRestrictionId.toString(),
            nomisId = nomisRestrictionId,
            offenderNo = offenderNo,
            mappingType = PrisonerRestrictionMappingDto.MappingType.NOMIS_CREATED,
          ),
        )
        personalRelationshipsOffenderEventsQueue.sendMessage(
          prisonerRestrictionEvent(
            eventType = "RESTRICTION-UPSERTED",
            isUpdated = false,
            offenderNo = offenderNo,
            restrictionId = nomisRestrictionId,
            auditModuleName = "OCUMINN",
          ),
        ).also { waitForAnyProcessingToComplete() }
      }

      @Test
      fun `will check if mapping already exists for restriction`() {
        mappingApi.verify(getRequestedFor(urlPathEqualTo("/mapping/contact-person/prisoner-restriction/nomis-prisoner-restriction-id/$nomisRestrictionId")))
      }

      @Test
      fun `will track telemetry`() {
        verify(telemetryClient).trackEvent(
          eq("contactperson-prisoner-restriction-synchronisation-created-ignored"),
          check {
            assertThat(it["offenderNo"]).isEqualTo(offenderNo)
            assertThat(it["nomisRestrictionId"]).isEqualTo(nomisRestrictionId.toString())
            assertThat(it["dpsRestrictionId"]).isEqualTo(dpsRestrictionId.toString())
          },
          isNull(),
        )
      }
    }
  }

  @Nested
  @DisplayName("RESTRICTION-UPSERTED updated")
  inner class PrisonerRestrictionUpdated {
    private val offenderRestrictionId = 3456L
    private val offenderNo = "A1234KT"

    @Nested
    inner class WhenUpdatedInDps {
      @BeforeEach
      fun setUp() {
        personalRelationshipsOffenderEventsQueue.sendMessage(
          prisonerRestrictionEvent(
            eventType = "RESTRICTION-UPSERTED",
            isUpdated = true,
            offenderNo = offenderNo,
            restrictionId = offenderRestrictionId,
            auditModuleName = "DPS_SYNCHRONISATION",
          ),
        ).also { waitForAnyProcessingToComplete() }
      }

      @Test
      fun `will track telemetry`() {
        verify(telemetryClient).trackEvent(
          eq("contactperson-prisoner-restriction-synchronisation-updated-skipped"),
          check {
            assertThat(it["offenderNo"]).isEqualTo(offenderNo)
            assertThat(it["nomisRestrictionId"]).isEqualTo(offenderRestrictionId.toString())
          },
          isNull(),
        )
      }
    }

    @Nested
    inner class WhenUpdatedInNomis {
      private val nomisRestrictionId = 3456L
      private val dpsRestrictionId = 65432L
      private val offenderNo = "A1234KT"

      @BeforeEach
      fun setUp() {
        mappingApiMock.stubGetByNomisPrisonerRestrictionIdOrNull(
          nomisRestrictionId = nomisRestrictionId,
          dpsRestrictionId = dpsRestrictionId.toString(),
          PrisonerRestrictionMappingDto(
            dpsId = dpsRestrictionId.toString(),
            nomisId = nomisRestrictionId,
            offenderNo = offenderNo,
            mappingType = PrisonerRestrictionMappingDto.MappingType.NOMIS_CREATED,
          ),
        )
        nomisApiMock.stubGetPrisonerRestrictionById(
          restrictionId = nomisRestrictionId,
          nomisPrisonerRestriction().copy(
            id = nomisRestrictionId,
            offenderNo = offenderNo,
            bookingSequence = 1,
            type = CodeDescription("BAN", "Banned"),
            effectiveDate = LocalDate.parse("2021-10-15"),
            enteredStaff = ContactRestrictionEnteredStaff(staffId = 123, username = "H.HARRY"),
            authorisedStaff = ContactRestrictionEnteredStaff(staffId = 456, username = "U.BELLY"),
            audit = nomisAudit().copy(modifyDatetime = LocalDateTime.parse("2021-10-15T12:00:00")),
            comment = "Banned for life",
            expiryDate = LocalDate.parse("2031-10-15"),
          ),
        )
        dpsApiMock.stubUpdatePrisonerRestriction(prisonerRestrictionId = dpsRestrictionId)

        personalRelationshipsOffenderEventsQueue.sendMessage(
          prisonerRestrictionEvent(
            eventType = "RESTRICTION-UPSERTED",
            isUpdated = true,
            offenderNo = offenderNo,
            restrictionId = offenderRestrictionId,
            auditModuleName = "OCUMINN",
          ),
        ).also { waitForAnyProcessingToComplete() }
      }

      @Test
      fun `will get the  mapping for the restriction`() {
        mappingApi.verify(getRequestedFor(urlPathEqualTo("/mapping/contact-person/prisoner-restriction/nomis-prisoner-restriction-id/$nomisRestrictionId")))
      }

      @Test
      fun `will retrieve the restrictions details from NOMIS`() {
        nomisApiMock.verify(getRequestedFor(urlPathEqualTo("/prisoners/restrictions/$nomisRestrictionId")))
      }

      @Test
      fun `will update restriction in DPS`() {
        val request: SyncUpdatePrisonerRestrictionRequest = getRequestBody(putRequestedFor(urlPathEqualTo("/sync/prisoner-restriction/$dpsRestrictionId")))
        with(request) {
          assertThat(prisonerNumber).isEqualTo(offenderNo)
          assertThat(updatedBy).isEqualTo("H.HARRY")
          assertThat(updatedTime).isEqualTo(LocalDateTime.parse("2021-10-15T12:00:00"))
          assertThat(effectiveDate).isEqualTo(LocalDate.parse("2021-10-15"))
          assertThat(expiryDate).isEqualTo(LocalDate.parse("2031-10-15"))
          assertThat(authorisedUsername).isEqualTo("U.BELLY")
          assertThat(commentText).isEqualTo("Banned for life")
          assertThat(currentTerm).isTrue
          assertThat(restrictionType).isEqualTo("BAN")
        }
      }

      @Test
      fun `will track telemetry`() {
        verify(telemetryClient).trackEvent(
          eq("contactperson-prisoner-restriction-synchronisation-updated-success"),
          check {
            assertThat(it["offenderNo"]).isEqualTo(offenderNo)
            assertThat(it["nomisRestrictionId"]).isEqualTo(offenderRestrictionId.toString())
          },
          isNull(),
        )
      }
    }
  }

  @Nested
  @DisplayName("RESTRICTION-DELETED")
  inner class PrisonerRestrictionDeleted {
    private val nomisRestrictionId = 3456L
    private val dpsRestrictionId = 65432L
    private val offenderNo = "A1234KT"

    @Nested
    inner class WhenDeletedAlreadyInDps {
      @BeforeEach
      fun setUp() {
        mappingApiMock.stubGetByNomisPrisonerRestrictionIdOrNull(
          nomisRestrictionId = nomisRestrictionId,
          mapping = null,
        )

        personalRelationshipsOffenderEventsQueue.sendMessage(
          prisonerRestrictionEvent(
            eventType = "RESTRICTION-DELETED",
            isUpdated = true,
            offenderNo = offenderNo,
            restrictionId = nomisRestrictionId,
            auditModuleName = "DPS_SYNCHRONISATION",
          ),
        ).also { waitForAnyProcessingToComplete() }
      }

      @Test
      fun `will track telemetry`() {
        verify(telemetryClient).trackEvent(
          eq("contactperson-prisoner-restriction-synchronisation-deleted-ignored"),
          check {
            assertThat(it["offenderNo"]).isEqualTo(offenderNo)
            assertThat(it["nomisRestrictionId"]).isEqualTo(nomisRestrictionId.toString())
          },
          isNull(),
        )
      }
    }

    @Nested
    inner class WhenDeletedInNomisBeforeDPS {
      @BeforeEach
      fun setUp() {
        mappingApiMock.stubGetByNomisPrisonerRestrictionIdOrNull(
          nomisRestrictionId = nomisRestrictionId,
          dpsRestrictionId = dpsRestrictionId.toString(),
          PrisonerRestrictionMappingDto(
            dpsId = dpsRestrictionId.toString(),
            nomisId = nomisRestrictionId,
            offenderNo = offenderNo,
            mappingType = PrisonerRestrictionMappingDto.MappingType.NOMIS_CREATED,
          ),
        )
        dpsApiMock.stubDeletePrisonerRestriction(dpsRestrictionId)
        personalRelationshipsOffenderEventsQueue.sendMessage(
          prisonerRestrictionEvent(
            eventType = "RESTRICTION-DELETED",
            isUpdated = true,
            offenderNo = offenderNo,
            restrictionId = nomisRestrictionId,
            auditModuleName = "DPS_SYNCHRONISATION",
          ),
        ).also { waitForAnyProcessingToComplete() }
      }

      @Test
      fun `will get the  mapping for the restriction`() {
        mappingApi.verify(getRequestedFor(urlPathEqualTo("/mapping/contact-person/prisoner-restriction/nomis-prisoner-restriction-id/$nomisRestrictionId")))
      }

      @Test
      fun `will delete restriction from DPS`() {
        dpsApiMock.verify(deleteRequestedFor(urlPathEqualTo("/sync/prisoner-restriction/$dpsRestrictionId")))
      }

      @Test
      fun `will track telemetry`() {
        verify(telemetryClient).trackEvent(
          eq("contactperson-prisoner-restriction-synchronisation-deleted-success"),
          check {
            assertThat(it["offenderNo"]).isEqualTo(offenderNo)
            assertThat(it["nomisRestrictionId"]).isEqualTo(nomisRestrictionId.toString())
            assertThat(it["dpsRestrictionId"]).isEqualTo(dpsRestrictionId.toString())
          },
          isNull(),
        )
      }
    }
  }

  @Nested
  @DisplayName("prison-offender-events.prisoner.merged")
  inner class PrisonerMerged {
    val offenderNumberRetained = "A1234KT"
    val offenderNumberRemoved = "A1000KT"

    @Nested
    inner class HappyPath {
      @BeforeEach
      fun setUp() {
        prisonerRestrictionsDomainEventsQueue.sendMessage(
          mergeDomainEvent(
            bookingId = 1234,
            offenderNo = offenderNumberRetained,
            removedOffenderNo = offenderNumberRemoved,
          ),
        ).also { waitForAnyProcessingToComplete() }
      }

      @Test
      fun `will track telemetry for the merge`() {
        verify(telemetryClient).trackEvent(
          eq("from-nomis-synch-prisonerrestriction-merge"),
          check {
            assertThat(it["offenderNo"]).isEqualTo(offenderNumberRetained)
            assertThat(it["removedOffenderNo"]).isEqualTo(offenderNumberRemoved)
          },
          isNull(),
        )
      }
    }
  }

  @Nested
  @DisplayName("prisoner-offender-search.prisoner.received")
  inner class PrisonerReceived {
    val offenderNumber = "A1234KT"

    @Nested
    inner class HappyPath {

      @Nested
      inner class NewBooking {

        @BeforeEach
        fun setUp() {
          prisonerRestrictionsDomainEventsQueue.sendMessage(
            prisonerReceivedDomainEvent(
              offenderNo = offenderNumber,
              reason = "NEW_ADMISSION",
            ),
          ).also { waitForAnyProcessingToComplete() }
        }

        @Test
        fun `will track telemetry for the merge`() {
          verify(telemetryClient).trackEvent(
            eq("from-nomis-synch-prisonerrestriction-booking-changed"),
            check {
              assertThat(it["offenderNo"]).isEqualTo(offenderNumber)
            },
            isNull(),
          )
        }
      }

      @Nested
      inner class SwitchBooking {

        @BeforeEach
        fun setUp() {
          prisonerRestrictionsDomainEventsQueue.sendMessage(
            prisonerReceivedDomainEvent(
              offenderNo = offenderNumber,
              reason = "READMISSION_SWITCH_BOOKING",
            ),
          ).also { waitForAnyProcessingToComplete() }
        }

        @Test
        fun `will track telemetry for the switch`() {
          verify(telemetryClient).trackEvent(
            eq("from-nomis-synch-prisonerrestriction-booking-changed"),
            check {
              assertThat(it["offenderNo"]).isEqualTo(offenderNumber)
            },
            isNull(),
          )
        }
      }
    }
  }

  @Nested
  @DisplayName("prison-offender-events.prisoner.booking.moved")
  inner class BookingMoved {
    val fromOffenderNumber = "A1234KT"
    val toOffenderNumber = "A1000KT"
    val bookingId = 98776L

    @Nested
    inner class HappyPath {

      @Nested
      inner class ToPrisonerActive {

        @BeforeEach
        fun setUp() {
          prisonerRestrictionsDomainEventsQueue.sendMessage(
            bookingMovedDomainEvent(
              bookingId = bookingId,
              movedFromNomsNumber = fromOffenderNumber,
              movedToNomsNumber = toOffenderNumber,
            ),
          ).also {
            waitForAnyProcessingToComplete()
          }
        }

        @Test
        fun `will track telemetry for the merge`() {
          verify(telemetryClient).trackEvent(
            eq("from-nomis-synch-prisonerrestriction-booking-moved"),
            check {
              assertThat(it["fromOffenderNo"]).isEqualTo(fromOffenderNumber)
              assertThat(it["toOffenderNo"]).isEqualTo(toOffenderNumber)
            },
            isNull(),
          )
        }
      }
    }
  }
}

fun prisonerRestrictionEvent(
  eventType: String,
  restrictionId: Long,
  offenderNo: String,
  isUpdated: Boolean,
  auditModuleName: String = "OMUVREST",
) = // language=JSON
  """{
    "MessageId": "ae06c49e-1f41-4b9f-b2f2-dcca610d02cd", "Type": "Notification", "Timestamp": "2019-10-21T14:01:18.500Z", 
    "Message": "{\"eventId\":\"5958295\",\"eventType\":\"$eventType\",\"eventDatetime\":\"2019-10-21T15:00:25.489964\",\"offenderIdDisplay\": \"$offenderNo\", \"isUpdated\": $isUpdated, \"offenderRestrictionId\": \"$restrictionId\",\"auditModuleName\":\"$auditModuleName\",\"restrictionType\": \"BAN\",\"effectiveDate\": \"2021-10-15\",\"expiryDate\": \"2022-01-13\",\"enteredById\": \"485887\",\"nomisEventType\":\"OFF_RESTRICTS-UPDATED\" }",
    "TopicArn": "arn:aws:sns:eu-west-1:000000000000:offender_events", 
    "MessageAttributes": {
      "eventType": {"Type": "String", "Value": "$eventType"}, 
      "id": {"Type": "String", "Value": "8b07cbd9-0820-0a0f-c32f-a9429b618e0b"}, 
      "contentType": {"Type": "String", "Value": "text/plain;charset=UTF-8"}, 
      "timestamp": {"Type": "Number.java.lang.Long", "Value": "1571666478344"}
    }
}
  """.trimIndent()
