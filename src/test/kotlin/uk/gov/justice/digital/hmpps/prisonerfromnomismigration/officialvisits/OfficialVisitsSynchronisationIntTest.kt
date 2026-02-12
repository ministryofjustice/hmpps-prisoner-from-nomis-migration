package uk.gov.justice.digital.hmpps.prisonerfromnomismigration.officialvisits

import com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor
import com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor
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
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.integration.SqsIntegrationTestBase
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.integration.sendMessage
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomismappings.model.DuplicateErrorContentObject
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomismappings.model.DuplicateMappingErrorResponse
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomismappings.model.LocationMappingDto
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomismappings.model.OfficialVisitMappingDto
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomismappings.model.OfficialVisitorMappingDto
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomismappings.model.VisitSlotMappingDto
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomisprisoner.model.CodeDescription
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomisprisoner.model.ContactRelationship
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomisprisoner.model.NomisAudit
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomisprisoner.model.VisitOrder
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.officialvisits.OfficialVisitsDpsApiExtension.Companion.dpsOfficialVisitsServer
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.officialvisits.OfficialVisitsDpsApiExtension.Companion.getRequestBody
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.officialvisits.OfficialVisitsDpsApiMockServer.Companion.syncOfficialVisit
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.officialvisits.OfficialVisitsDpsApiMockServer.Companion.syncOfficialVisitor
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.officialvisits.OfficialVisitsNomisApiMockServer.Companion.officialVisitResponse
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.officialvisits.OfficialVisitsNomisApiMockServer.Companion.officialVisitor
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.officialvisits.model.AttendanceType
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.officialvisits.model.RelationshipType
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.officialvisits.model.SearchLevelType
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.officialvisits.model.SyncCreateOfficialVisitRequest
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.officialvisits.model.SyncCreateOfficialVisitorRequest
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.officialvisits.model.VisitStatusType
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.officialvisits.model.VisitType
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.wiremock.withRequestBodyJsonPath
import java.time.LocalDate
import java.time.LocalDateTime
import java.util.*

class OfficialVisitsSynchronisationIntTest : SqsIntegrationTestBase() {
  val offenderNo = "A1234KT"
  val bookingId = 1234L
  val prisonId = "MDI"
  val nomisVisitId = 65432L
  val nomisVisitorId = 76544L
  val nomisPersonId = 8987756L

  @Autowired
  private lateinit var nomisApiMock: OfficialVisitsNomisApiMockServer

  private val dpsApiMock = dpsOfficialVisitsServer

  @Autowired
  private lateinit var mappingApiMock: OfficialVisitsMappingApiMockServer

  @Autowired
  private lateinit var visitSlotsMappingApiMock: VisitSlotsMappingApiMockServer

  @Nested
  @DisplayName("OFFENDER_OFFICIAL_VISIT-INSERTED")
  inner class OfficialVisitCreated {
    @Nested
    inner class WhenCreatedInDps {
      @BeforeEach
      fun setUp() {
        officialVisitsOffenderEventsQueue.sendMessage(
          officialVisitEvent(
            eventType = "OFFENDER_OFFICIAL_VISIT-INSERTED",
            offenderNo = offenderNo,
            visitId = nomisVisitId,
            agencyLocationId = prisonId,
            bookingId = bookingId,
            auditModuleName = "DPS_SYNCHRONISATION_OFFICIAL_VISITS",
          ),
        ).also { waitForAnyProcessingToComplete() }
      }

      @Test
      fun `will track telemetry`() {
        verify(telemetryClient).trackEvent(
          eq("officialvisits-visit-synchronisation-created-skipped"),
          check {
            assertThat(it["offenderNo"]).isEqualTo(offenderNo)
            assertThat(it["nomisVisitId"]).isEqualTo(nomisVisitId.toString())
            assertThat(it["bookingId"]).isEqualTo(bookingId.toString())
            assertThat(it["prisonId"]).isEqualTo(prisonId)
          },
          isNull(),
        )
      }
    }

    @Nested
    inner class WhenCreatedInNomis {

      @Nested
      inner class AlreadyExists {
        val dpsOfficialVisitId = 123L

        @BeforeEach
        fun setUp() {
          mappingApiMock.stubGetByVisitNomisIdOrNull(
            nomisVisitId = nomisVisitId,
            mapping = OfficialVisitMappingDto(
              dpsId = dpsOfficialVisitId.toString(),
              nomisId = nomisVisitId,
              mappingType = OfficialVisitMappingDto.MappingType.NOMIS_CREATED,
            ),
          )
          officialVisitsOffenderEventsQueue.sendMessage(
            officialVisitEvent(
              eventType = "OFFENDER_OFFICIAL_VISIT-INSERTED",
              offenderNo = offenderNo,
              visitId = nomisVisitId,
              agencyLocationId = prisonId,
              bookingId = bookingId,
            ),
          ).also { waitForAnyProcessingToComplete() }
        }

        @Test
        fun `will track telemetry`() {
          verify(telemetryClient).trackEvent(
            eq("officialvisits-visit-synchronisation-created-ignored"),
            check {
              assertThat(it["offenderNo"]).isEqualTo(offenderNo)
              assertThat(it["nomisVisitId"]).isEqualTo(nomisVisitId.toString())
              assertThat(it["bookingId"]).isEqualTo(bookingId.toString())
              assertThat(it["prisonId"]).isEqualTo(prisonId)
              assertThat(it["dpsOfficialVisitId"]).isEqualTo(dpsOfficialVisitId.toString())
            },
            isNull(),
          )
        }
      }

      @Nested
      inner class HappyPath {
        val dpsOfficialVisitId = 8549934L
        val dpsVisitSlotId = 123L
        val nomisVisitSlotId = 8484L
        val dpsLocationId: UUID = UUID.randomUUID()
        val nomisLocationId = 765L

        @BeforeEach
        fun setUp() {
          mappingApiMock.stubGetByVisitNomisIdOrNull(
            nomisVisitId = nomisVisitId,
            mapping = null,
          )

          nomisApiMock.stubGetOfficialVisit(
            visitId = nomisVisitId,
            response = officialVisitResponse().copy(
              internalLocationId = nomisLocationId,
              visitId = nomisVisitId,
              visitSlotId = nomisVisitSlotId,
              startDateTime = LocalDateTime.parse("2020-01-01T10:00"),
              endDateTime = LocalDateTime.parse("2020-01-01T11:10"),
              offenderNo = "A1234KT",
              bookingId = 1234,
              currentTerm = true,
              prisonId = "MDI",
              commentText = "First visit",
              visitorConcernText = "Big concerns",
              overrideBanStaffUsername = "T.SMITH",
              visitOrder = VisitOrder(654321),
              prisonerSearchType = CodeDescription(code = "PAT", description = "Pat Down Search"),
              visitStatus = CodeDescription(code = "SCH", description = "Scheduled"),
              visitOutcome = null,
              prisonerAttendanceOutcome = CodeDescription(code = "ATT", description = "Attended"),
              cancellationReason = null,
              audit = NomisAudit(
                createDatetime = LocalDateTime.parse("2020-01-01T10:10:10"),
                createUsername = "J.JOHN",
              ),
              visitors = listOf(
                officialVisitor(),
              ),
            ),
          )

          mappingApiMock.stubGetInternalLocationByNomisId(
            nomisLocationId = nomisLocationId,
            mapping = LocationMappingDto(
              dpsLocationId = dpsLocationId.toString(),
              nomisLocationId = nomisLocationId,
              mappingType = LocationMappingDto.MappingType.LOCATION_CREATED,
            ),
          )

          visitSlotsMappingApiMock.stubGetVisitSlotByNomisIdOrNull(
            nomisId = nomisVisitSlotId,
            mapping = VisitSlotMappingDto(
              dpsId = dpsVisitSlotId.toString(),
              nomisId = nomisVisitSlotId,
              mappingType = VisitSlotMappingDto.MappingType.NOMIS_CREATED,
            ),
          )

          dpsApiMock.stubCreateVisit(response = syncOfficialVisit().copy(officialVisitId = dpsOfficialVisitId))
          mappingApiMock.stubCreateVisitMapping()

          officialVisitsOffenderEventsQueue.sendMessage(
            officialVisitEvent(
              eventType = "OFFENDER_OFFICIAL_VISIT-INSERTED",
              offenderNo = offenderNo,
              visitId = nomisVisitId,
              agencyLocationId = prisonId,
              bookingId = bookingId,
            ),
          ).also { waitForAnyProcessingToComplete() }
        }

        @Test
        fun `will track telemetry`() {
          verify(telemetryClient).trackEvent(
            eq("officialvisits-visit-synchronisation-created-success"),
            check {
              assertThat(it["offenderNo"]).isEqualTo(offenderNo)
              assertThat(it["nomisVisitId"]).isEqualTo(nomisVisitId.toString())
              assertThat(it["bookingId"]).isEqualTo(bookingId.toString())
              assertThat(it["prisonId"]).isEqualTo(prisonId)
              assertThat(it["dpsOfficialVisitId"]).isEqualTo(dpsOfficialVisitId.toString())
            },
            isNull(),
          )
        }

        @Test
        fun `will check if visit has already been created`() {
          mappingApiMock.verify(getRequestedFor(urlPathEqualTo("/mapping/official-visits/visit/nomis-id/$nomisVisitId")))
        }

        @Test
        fun `will retrieve the NOMIS visit details`() {
          nomisApiMock.verify(getRequestedFor(urlPathEqualTo("/official-visits/$nomisVisitId")))
        }

        @Test
        fun `will retrieve the DPS location id for the visit slot room location`() {
          mappingApiMock.verify(getRequestedFor(urlPathEqualTo("/mapping/locations/nomis/$nomisLocationId")))
        }

        @Test
        fun `will retrieve the DPS visit slot id for the NOMIS visit slot`() {
          mappingApiMock.verify(getRequestedFor(urlPathEqualTo("/mapping/visit-slots/visit-slot/nomis-id/$nomisVisitSlotId")))
        }

        @Test
        fun `will create the official visit in DPS`() {
          val request: SyncCreateOfficialVisitRequest = getRequestBody(postRequestedFor(urlPathEqualTo("/sync/official-visit")))
          with(request) {
            assertThat(offenderVisitId).isEqualTo(nomisVisitId)
            assertThat(dpsLocationId).isEqualTo(dpsLocationId)
            assertThat(prisonVisitSlotId).isEqualTo(dpsVisitSlotId)
            assertThat(prisonCode).isEqualTo("MDI")
            assertThat(visitDate).isEqualTo(LocalDate.parse("2020-01-01"))
            assertThat(startTime).isEqualTo("10:00")
            assertThat(endTime).isEqualTo("11:10")
            assertThat(commentText).isEqualTo("First visit")
            assertThat(visitorConcernText).isEqualTo("Big concerns")
            assertThat(overrideBanStaffUsername).isEqualTo("T.SMITH")
            assertThat(searchTypeCode).isEqualTo(SearchLevelType.PAT)
            assertThat(visitOrderNumber).isEqualTo(654321)
            assertThat(prisonerNumber).isEqualTo("A1234KT")
            assertThat(offenderBookId).isEqualTo(1234L)
            assertThat(currentTerm).isTrue
            assertThat(visitStatusCode).isEqualTo(VisitStatusType.SCHEDULED)
            assertThat(visitTypeCode).isEqualTo(VisitType.UNKNOWN)
            assertThat(visitCompletionCode).isNull()
            assertThat(createUsername).isEqualTo("J.JOHN")
            assertThat(createDateTime).isEqualTo(LocalDateTime.parse("2020-01-01T10:10:10"))
          }
        }

        @Test
        fun `will create mapping`() {
          mappingApiMock.verify(
            postRequestedFor(urlPathEqualTo("/mapping/official-visits/visit"))
              .withRequestBodyJsonPath("dpsId", dpsOfficialVisitId.toString())
              .withRequestBodyJsonPath("nomisId", nomisVisitId)
              .withRequestBodyJsonPath("mappingType", "NOMIS_CREATED"),
          )
        }
      }

      @Nested
      inner class MappingFailures {
        val dpsOfficialVisitId = 8549934L
        val dpsVisitSlotId = 123L
        val nomisVisitSlotId = 8484L
        val dpsLocationId: UUID = UUID.randomUUID()
        val nomisLocationId = 765L

        @BeforeEach
        fun setUp() {
          mappingApiMock.stubGetByVisitNomisIdOrNull(
            nomisVisitId = nomisVisitId,
            mapping = null,
          )

          nomisApiMock.stubGetOfficialVisit(
            visitId = nomisVisitId,
            response = officialVisitResponse().copy(
              internalLocationId = nomisLocationId,
              visitSlotId = nomisVisitSlotId,
            ),
          )

          mappingApiMock.stubGetInternalLocationByNomisId(
            nomisLocationId = nomisLocationId,
            mapping = LocationMappingDto(
              dpsLocationId = dpsLocationId.toString(),
              nomisLocationId = nomisLocationId,
              mappingType = LocationMappingDto.MappingType.LOCATION_CREATED,
            ),
          )

          visitSlotsMappingApiMock.stubGetVisitSlotByNomisIdOrNull(
            nomisId = nomisVisitSlotId,
            mapping = VisitSlotMappingDto(
              dpsId = dpsVisitSlotId.toString(),
              nomisId = nomisVisitSlotId,
              mappingType = VisitSlotMappingDto.MappingType.NOMIS_CREATED,
            ),
          )

          dpsApiMock.stubCreateVisit(response = syncOfficialVisit().copy(officialVisitId = dpsOfficialVisitId))
        }

        @Nested
        inner class FailureAndRecovery {
          @BeforeEach
          fun setUp() {
            mappingApiMock.stubCreateVisitMappingFailureFollowedBySuccess()

            officialVisitsOffenderEventsQueue.sendMessage(
              officialVisitEvent(
                eventType = "OFFENDER_OFFICIAL_VISIT-INSERTED",
                offenderNo = offenderNo,
                visitId = nomisVisitId,
                agencyLocationId = prisonId,
                bookingId = bookingId,
              ),
            ).also { waitForAnyProcessingToComplete("officialvisits-visit-mapping-synchronisation-created") }
          }

          @Test
          fun `will create the official visit in DPS only once`() {
            dpsApiMock.verify(1, postRequestedFor(urlPathEqualTo("/sync/official-visit")))
          }

          @Test
          fun `will eventually create mapping after a retry`() {
            mappingApiMock.verify(2, postRequestedFor(urlPathEqualTo("/mapping/official-visits/visit")))
          }

          @Test
          fun `will track telemetry`() {
            verify(telemetryClient).trackEvent(
              eq("officialvisits-visit-synchronisation-created-success"),
              check {
                assertThat(it["offenderNo"]).isEqualTo(offenderNo)
                assertThat(it["nomisVisitId"]).isEqualTo(nomisVisitId.toString())
                assertThat(it["bookingId"]).isEqualTo(bookingId.toString())
                assertThat(it["prisonId"]).isEqualTo(prisonId)
                assertThat(it["dpsOfficialVisitId"]).isEqualTo(dpsOfficialVisitId.toString())
              },
              isNull(),
            )

            verify(telemetryClient).trackEvent(
              eq("officialvisits-visit-mapping-synchronisation-created"),
              check {
                assertThat(it["offenderNo"]).isEqualTo(offenderNo)
                assertThat(it["nomisVisitId"]).isEqualTo(nomisVisitId.toString())
                assertThat(it["bookingId"]).isEqualTo(bookingId.toString())
                assertThat(it["prisonId"]).isEqualTo(prisonId)
                assertThat(it["dpsOfficialVisitId"]).isEqualTo(dpsOfficialVisitId.toString())
              },
              isNull(),
            )
          }
        }

        @Nested
        inner class DuplicateDetected {
          @BeforeEach
          fun setUp() {
            mappingApiMock.stubCreateVisitMapping(
              error = DuplicateMappingErrorResponse(
                moreInfo = DuplicateErrorContentObject(
                  duplicate = OfficialVisitMappingDto(
                    dpsId = dpsOfficialVisitId.toString(),
                    nomisId = nomisVisitId,
                    mappingType = OfficialVisitMappingDto.MappingType.NOMIS_CREATED,
                  ),
                  existing = OfficialVisitMappingDto(
                    dpsId = "98765",
                    nomisId = nomisVisitId,
                    mappingType = OfficialVisitMappingDto.MappingType.NOMIS_CREATED,
                  ),
                ),
                errorCode = 1409,
                status = DuplicateMappingErrorResponse.Status._409_CONFLICT,
                userMessage = "Duplicate mapping",
              ),
            )

            officialVisitsOffenderEventsQueue.sendMessage(
              officialVisitEvent(
                eventType = "OFFENDER_OFFICIAL_VISIT-INSERTED",
                offenderNo = offenderNo,
                visitId = nomisVisitId,
                agencyLocationId = prisonId,
                bookingId = bookingId,
              ),
            ).also { waitForAnyProcessingToComplete("from-nomis-sync-officialvisits-duplicate") }
          }

          @Test
          fun `will create the official visit in DPS only once`() {
            dpsApiMock.verify(1, postRequestedFor(urlPathEqualTo("/sync/official-visit")))
          }

          @Test
          fun `will not bother retrying mapping`() {
            mappingApiMock.verify(1, postRequestedFor(urlPathEqualTo("/mapping/official-visits/visit")))
          }

          @Test
          fun `will track telemetry`() {
            verify(telemetryClient).trackEvent(
              eq("officialvisits-visit-synchronisation-created-success"),
              check {
                assertThat(it["offenderNo"]).isEqualTo(offenderNo)
                assertThat(it["nomisVisitId"]).isEqualTo(nomisVisitId.toString())
                assertThat(it["bookingId"]).isEqualTo(bookingId.toString())
                assertThat(it["prisonId"]).isEqualTo(prisonId)
                assertThat(it["dpsOfficialVisitId"]).isEqualTo(dpsOfficialVisitId.toString())
              },
              isNull(),
            )

            verify(telemetryClient).trackEvent(
              eq("from-nomis-sync-officialvisits-duplicate"),
              check {
                assertThat(it["type"]).isEqualTo("OFFICIALVISIT")
                assertThat(it["existingNomisId"]).isEqualTo(nomisVisitId.toString())
                assertThat(it["existingDpsId"]).isEqualTo("98765")
                assertThat(it["duplicateNomisId"]).isEqualTo(nomisVisitId.toString())
                assertThat(it["duplicateDpsId"]).isEqualTo(dpsOfficialVisitId.toString())
              },
              isNull(),
            )
          }
        }
      }
    }
  }

  @Nested
  @DisplayName("OFFENDER_OFFICIAL_VISIT-UPDATED")
  inner class OfficialVisitUpdated {

    @Nested
    inner class WhenUpdatedInNomis {
      @BeforeEach
      fun setUp() {
        officialVisitsOffenderEventsQueue.sendMessage(
          officialVisitEvent(
            eventType = "OFFENDER_OFFICIAL_VISIT-UPDATED",
            offenderNo = offenderNo,
            visitId = nomisVisitId,
            agencyLocationId = prisonId,
            bookingId = bookingId,
          ),
        ).also { waitForAnyProcessingToComplete() }
      }

      @Test
      fun `will track telemetry`() {
        verify(telemetryClient).trackEvent(
          eq("officialvisits-visit-synchronisation-updated-success"),
          check {
            assertThat(it["offenderNo"]).isEqualTo(offenderNo)
            assertThat(it["nomisVisitId"]).isEqualTo(nomisVisitId.toString())
            assertThat(it["bookingId"]).isEqualTo(bookingId.toString())
            assertThat(it["prisonId"]).isEqualTo(prisonId)
          },
          isNull(),
        )
      }
    }
  }

  @Nested
  @DisplayName("OFFENDER_OFFICIAL_VISIT-DELETED")
  inner class OfficialVisitDeleted {

    @Nested
    inner class WhenDeletedInNomis {
      @BeforeEach
      fun setUp() {
        officialVisitsOffenderEventsQueue.sendMessage(
          officialVisitEvent(
            eventType = "OFFENDER_OFFICIAL_VISIT-DELETED",
            offenderNo = offenderNo,
            visitId = nomisVisitId,
            agencyLocationId = prisonId,
            bookingId = bookingId,
          ),
        ).also { waitForAnyProcessingToComplete() }
      }

      @Test
      fun `will track telemetry`() {
        verify(telemetryClient).trackEvent(
          eq("officialvisits-visit-synchronisation-deleted-success"),
          check {
            assertThat(it["offenderNo"]).isEqualTo(offenderNo)
            assertThat(it["nomisVisitId"]).isEqualTo(nomisVisitId.toString())
            assertThat(it["bookingId"]).isEqualTo(bookingId.toString())
            assertThat(it["prisonId"]).isEqualTo(prisonId)
          },
          isNull(),
        )
      }
    }
  }

  @Nested
  @DisplayName("OFFENDER_OFFICIAL_VISIT_VISITORS-INSERTED")
  inner class OfficialVisitVisitorCreated {

    @Nested
    inner class WhenCreatedInDps {
      @BeforeEach
      fun setUp() {
        officialVisitsOffenderEventsQueue.sendMessage(
          officialVisitVisitorEvent(
            eventType = "OFFENDER_OFFICIAL_VISIT_VISITORS-INSERTED",
            offenderNo = offenderNo,
            visitId = nomisVisitId,
            bookingId = bookingId,
            visitVisitorId = nomisVisitorId,
            personId = nomisPersonId,
            auditModuleName = "DPS_SYNCHRONISATION_OFFICIAL_VISITS",
          ),
        ).also { waitForAnyProcessingToComplete() }
      }

      @Test
      fun `will track telemetry`() {
        verify(telemetryClient).trackEvent(
          eq("officialvisits-visitor-synchronisation-created-skipped"),
          check {
            assertThat(it["offenderNo"]).isEqualTo(offenderNo)
            assertThat(it["nomisVisitId"]).isEqualTo(nomisVisitId.toString())
            assertThat(it["bookingId"]).isEqualTo(bookingId.toString())
            assertThat(it["nomisVisitorId"]).isEqualTo(nomisVisitorId.toString())
            assertThat(it["nomisPersonId"]).isEqualTo(nomisPersonId.toString())
          },
          isNull(),
        )
      }
    }

    @Nested
    inner class WhenCreatedInNomis {
      val dpsOfficialVisitorId = 173193L
      val dpsOfficialVisitId = 287283L

      @Nested
      inner class HappyPath {

        @BeforeEach
        fun setUp() {
          mappingApiMock.stubGetByVisitorNomisIdOrNull(
            nomisVisitorId = nomisVisitorId,
            mapping = null,
          )

          nomisApiMock.stubGetOfficialVisit(
            visitId = nomisVisitId,
            response = officialVisitResponse().copy(
              visitId = nomisVisitId,
              visitors = listOf(
                officialVisitor().copy(
                  id = nomisVisitorId,
                  personId = nomisPersonId,
                  firstName = "JOHN",
                  lastName = "SMITH",
                  dateOfBirth = LocalDate.parse("1965-07-19"),
                  leadVisitor = true,
                  assistedVisit = true,
                  commentText = "Requires access",
                  eventStatus = CodeDescription(code = "SCH", description = "Scheduled"),
                  visitorAttendanceOutcome = CodeDescription(code = "ATT", description = "Attended"),
                  cancellationReason = null,
                  relationships = listOf(
                    ContactRelationship(
                      relationshipType = CodeDescription(
                        code = "POL",
                        description = "Police",
                      ),
                      contactType = CodeDescription(
                        code = "O",
                        description = "Official",
                      ),
                    ),
                    ContactRelationship(
                      relationshipType = CodeDescription(
                        code = "DR",
                        description = "Doctor",
                      ),
                      contactType = CodeDescription(
                        code = "O",
                        description = "Official",
                      ),
                    ),
                  ),
                  audit = NomisAudit(
                    createDatetime = LocalDateTime.parse("2019-01-01T10:10:10"),
                    createUsername = "S.JOHN",
                    modifyDatetime = LocalDateTime.parse("2019-02-02T11:10:10"),
                    modifyUserId = "T.SMITH",
                  ),
                ),
              ),
            ),
          )

          mappingApiMock.stubGetByVisitNomisId(
            nomisVisitId = nomisVisitId,
            mapping = OfficialVisitMappingDto(
              dpsId = dpsOfficialVisitId.toString(),
              nomisId = nomisVisitId,
              mappingType = OfficialVisitMappingDto.MappingType.NOMIS_CREATED,
            ),
          )

          dpsApiMock.stubCreateVisitor(officialVisitId = dpsOfficialVisitId, response = syncOfficialVisitor().copy(officialVisitorId = dpsOfficialVisitorId))
          mappingApiMock.stubCreateVisitorMapping()

          officialVisitsOffenderEventsQueue.sendMessage(
            officialVisitVisitorEvent(
              eventType = "OFFENDER_OFFICIAL_VISIT_VISITORS-INSERTED",
              offenderNo = offenderNo,
              visitId = nomisVisitId,
              bookingId = bookingId,
              visitVisitorId = nomisVisitorId,
              personId = nomisPersonId,
            ),
          ).also { waitForAnyProcessingToComplete() }
        }

        @Test
        fun `will track telemetry`() {
          verify(telemetryClient).trackEvent(
            eq("officialvisits-visitor-synchronisation-created-success"),
            check {
              assertThat(it["offenderNo"]).isEqualTo(offenderNo)
              assertThat(it["nomisVisitId"]).isEqualTo(nomisVisitId.toString())
              assertThat(it["dpsOfficialVisitId"]).isEqualTo(dpsOfficialVisitId.toString())
              assertThat(it["bookingId"]).isEqualTo(bookingId.toString())
              assertThat(it["nomisVisitorId"]).isEqualTo(nomisVisitorId.toString())
              assertThat(it["dpsOfficialVisitorId"]).isEqualTo(dpsOfficialVisitorId.toString())
              assertThat(it["nomisPersonId"]).isEqualTo(nomisPersonId.toString())
            },
            isNull(),
          )
        }

        @Test
        fun `will check if visitor has already been created`() {
          mappingApiMock.verify(getRequestedFor(urlPathEqualTo("/mapping/official-visits/visitor/nomis-id/$nomisVisitorId")))
        }

        @Test
        fun `will retrieve the NOMIS visit details to get the visitor details`() {
          nomisApiMock.verify(getRequestedFor(urlPathEqualTo("/official-visits/$nomisVisitId")))
        }

        @Test
        fun `will retrieve the DPS visit id for the visit`() {
          mappingApiMock.verify(getRequestedFor(urlPathEqualTo("/mapping/official-visits/visit/nomis-id/$nomisVisitId")))
        }

        @Test
        fun `will create the official visitor in DPS`() {
          val request: SyncCreateOfficialVisitorRequest = getRequestBody(postRequestedFor(urlPathEqualTo("/sync/official-visit/$dpsOfficialVisitId/visitor")))
          with(request) {
            assertThat(offenderVisitVisitorId).isEqualTo(nomisVisitorId)
            assertThat(personId).isEqualTo(nomisPersonId)
            assertThat(createUsername).isEqualTo("S.JOHN")
            assertThat(createDateTime).isEqualTo(LocalDateTime.parse("2019-01-01T10:10:10"))
            assertThat(firstName).isEqualTo("JOHN")
            assertThat(lastName).isEqualTo("SMITH")
            assertThat(dateOfBirth).isEqualTo(LocalDate.parse("1965-07-19"))
            assertThat(groupLeaderFlag).isTrue
            assertThat(assistedVisitFlag).isTrue
            assertThat(commentText).isEqualTo("Requires access")
            assertThat(attendanceCode).isEqualTo(AttendanceType.ATTENDED)
            assertThat(relationshipToPrisoner).isEqualTo("POL")
            assertThat(relationshipTypeCode).isEqualTo(RelationshipType.OFFICIAL)
          }
        }

        @Test
        fun `will create mapping`() {
          mappingApiMock.verify(
            postRequestedFor(urlPathEqualTo("/mapping/official-visits/visitor"))
              .withRequestBodyJsonPath("dpsId", dpsOfficialVisitorId.toString())
              .withRequestBodyJsonPath("nomisId", nomisVisitorId)
              .withRequestBodyJsonPath("mappingType", "NOMIS_CREATED"),
          )
        }
      }

      @Nested
      inner class VisitMissingRecovery {

        @BeforeEach
        fun setUp() {
          mappingApiMock.stubGetByVisitorNomisIdOrNull(
            nomisVisitorId = nomisVisitorId,
            mapping = null,
          )

          nomisApiMock.stubGetOfficialVisit(
            visitId = nomisVisitId,
            response = officialVisitResponse().copy(
              visitId = nomisVisitId,
              visitors = listOf(
                officialVisitor().copy(
                  id = nomisVisitorId,
                  personId = nomisPersonId,
                ),
              ),
            ),
          )

          mappingApiMock.stubGetByVisitNomisIdOrNullNotFoundFollowedBySuccess(
            nomisVisitId = nomisVisitId,
            mapping = OfficialVisitMappingDto(
              dpsId = dpsOfficialVisitId.toString(),
              nomisId = nomisVisitId,
              mappingType = OfficialVisitMappingDto.MappingType.NOMIS_CREATED,
            ),
          )

          dpsApiMock.stubCreateVisitor(officialVisitId = dpsOfficialVisitId, response = syncOfficialVisitor().copy(officialVisitorId = dpsOfficialVisitorId))
          mappingApiMock.stubCreateVisitorMapping()

          officialVisitsOffenderEventsQueue.sendMessage(
            officialVisitVisitorEvent(
              eventType = "OFFENDER_OFFICIAL_VISIT_VISITORS-INSERTED",
              offenderNo = offenderNo,
              visitId = nomisVisitId,
              bookingId = bookingId,
              visitVisitorId = nomisVisitorId,
              personId = nomisPersonId,
            ),
          ).also { waitForAnyProcessingToComplete("officialvisits-visitor-synchronisation-created-success") }
        }

        @Test
        fun `will track telemetry`() {
          verify(telemetryClient).trackEvent(
            eq("officialvisits-visitor-synchronisation-created-awaiting-parent"),
            check {
              assertThat(it["offenderNo"]).isEqualTo(offenderNo)
              assertThat(it["nomisVisitId"]).isEqualTo(nomisVisitId.toString())
              assertThat(it["bookingId"]).isEqualTo(bookingId.toString())
              assertThat(it["nomisVisitorId"]).isEqualTo(nomisVisitorId.toString())
              assertThat(it["nomisPersonId"]).isEqualTo(nomisPersonId.toString())
            },
            isNull(),
          )

          verify(telemetryClient).trackEvent(
            eq("officialvisits-visitor-synchronisation-created-success"),
            check {
              assertThat(it["offenderNo"]).isEqualTo(offenderNo)
              assertThat(it["nomisVisitId"]).isEqualTo(nomisVisitId.toString())
              assertThat(it["dpsOfficialVisitId"]).isEqualTo(dpsOfficialVisitId.toString())
              assertThat(it["bookingId"]).isEqualTo(bookingId.toString())
              assertThat(it["nomisVisitorId"]).isEqualTo(nomisVisitorId.toString())
              assertThat(it["dpsOfficialVisitorId"]).isEqualTo(dpsOfficialVisitorId.toString())
              assertThat(it["nomisPersonId"]).isEqualTo(nomisPersonId.toString())
            },
            isNull(),
          )
        }

        @Test
        fun `will eventually retrieve the DPS visit id for the visit`() {
          mappingApiMock.verify(2, getRequestedFor(urlPathEqualTo("/mapping/official-visits/visit/nomis-id/$nomisVisitId")))
        }

        @Test
        fun `will create the official visitor in DPS once`() {
          dpsApiMock.verify(1, postRequestedFor(urlPathEqualTo("/sync/official-visit/$dpsOfficialVisitId/visitor")))
        }
      }

      @Nested
      inner class MappingFailures {
        @BeforeEach
        fun setUp() {
          mappingApiMock.stubGetByVisitorNomisIdOrNull(
            nomisVisitorId = nomisVisitorId,
            mapping = null,
          )

          nomisApiMock.stubGetOfficialVisit(
            visitId = nomisVisitId,
            response = officialVisitResponse().copy(
              visitId = nomisVisitId,
              visitors = listOf(
                officialVisitor().copy(
                  id = nomisVisitorId,
                  personId = nomisPersonId,
                ),
              ),
            ),
          )

          mappingApiMock.stubGetByVisitNomisId(
            nomisVisitId = nomisVisitId,
            mapping = OfficialVisitMappingDto(
              dpsId = dpsOfficialVisitId.toString(),
              nomisId = nomisVisitId,
              mappingType = OfficialVisitMappingDto.MappingType.NOMIS_CREATED,
            ),
          )

          dpsApiMock.stubCreateVisitor(officialVisitId = dpsOfficialVisitId, response = syncOfficialVisitor().copy(officialVisitorId = dpsOfficialVisitorId))
        }

        @Nested
        inner class FailureAndRecovery {
          @BeforeEach
          fun setUp() {
            mappingApiMock.stubCreateVisitorMappingFailureFollowedBySuccess()

            officialVisitsOffenderEventsQueue.sendMessage(
              officialVisitVisitorEvent(
                eventType = "OFFENDER_OFFICIAL_VISIT_VISITORS-INSERTED",
                offenderNo = offenderNo,
                visitId = nomisVisitId,
                bookingId = bookingId,
                visitVisitorId = nomisVisitorId,
                personId = nomisPersonId,
              ),
            ).also { waitForAnyProcessingToComplete("officialvisits-visitor-mapping-synchronisation-created") }
          }

          @Test
          fun `will create the official visitor in DPS only once`() {
            dpsApiMock.verify(1, postRequestedFor(urlPathEqualTo("/sync/official-visit/$dpsOfficialVisitId/visitor")))
          }

          @Test
          fun `will eventually create mapping after a retry`() {
            mappingApiMock.verify(2, postRequestedFor(urlPathEqualTo("/mapping/official-visits/visitor")))
          }

          @Test
          fun `will track telemetry`() {
            verify(telemetryClient).trackEvent(
              eq("officialvisits-visitor-synchronisation-created-success"),
              check {
                assertThat(it["offenderNo"]).isEqualTo(offenderNo)
                assertThat(it["nomisVisitId"]).isEqualTo(nomisVisitId.toString())
                assertThat(it["dpsOfficialVisitId"]).isEqualTo(dpsOfficialVisitId.toString())
                assertThat(it["bookingId"]).isEqualTo(bookingId.toString())
                assertThat(it["nomisVisitorId"]).isEqualTo(nomisVisitorId.toString())
                assertThat(it["dpsOfficialVisitorId"]).isEqualTo(dpsOfficialVisitorId.toString())
                assertThat(it["nomisPersonId"]).isEqualTo(nomisPersonId.toString())
              },
              isNull(),
            )

            verify(telemetryClient).trackEvent(
              eq("officialvisits-visitor-mapping-synchronisation-created"),
              check {
                assertThat(it["offenderNo"]).isEqualTo(offenderNo)
                assertThat(it["nomisVisitId"]).isEqualTo(nomisVisitId.toString())
                assertThat(it["dpsOfficialVisitId"]).isEqualTo(dpsOfficialVisitId.toString())
                assertThat(it["bookingId"]).isEqualTo(bookingId.toString())
                assertThat(it["nomisVisitorId"]).isEqualTo(nomisVisitorId.toString())
                assertThat(it["dpsOfficialVisitorId"]).isEqualTo(dpsOfficialVisitorId.toString())
                assertThat(it["nomisPersonId"]).isEqualTo(nomisPersonId.toString())
              },
              isNull(),
            )
          }
        }

        @Nested
        inner class DuplicateDetected {
          @BeforeEach
          fun setUp() {
            mappingApiMock.stubCreateVisitorMapping(
              error = DuplicateMappingErrorResponse(
                moreInfo = DuplicateErrorContentObject(
                  duplicate = OfficialVisitorMappingDto(
                    dpsId = dpsOfficialVisitorId.toString(),
                    nomisId = nomisVisitorId,
                    mappingType = OfficialVisitorMappingDto.MappingType.NOMIS_CREATED,
                  ),
                  existing = OfficialVisitorMappingDto(
                    dpsId = "98765",
                    nomisId = nomisVisitorId,
                    mappingType = OfficialVisitorMappingDto.MappingType.NOMIS_CREATED,
                  ),
                ),
                errorCode = 1409,
                status = DuplicateMappingErrorResponse.Status._409_CONFLICT,
                userMessage = "Duplicate mapping",
              ),
            )

            officialVisitsOffenderEventsQueue.sendMessage(
              officialVisitVisitorEvent(
                eventType = "OFFENDER_OFFICIAL_VISIT_VISITORS-INSERTED",
                offenderNo = offenderNo,
                visitId = nomisVisitId,
                bookingId = bookingId,
                visitVisitorId = nomisVisitorId,
                personId = nomisPersonId,
              ),
            ).also { waitForAnyProcessingToComplete("from-nomis-sync-officialvisits-duplicate") }
          }

          @Test
          fun `will create the official visitor in DPS only once`() {
            dpsApiMock.verify(1, postRequestedFor(urlPathEqualTo("/sync/official-visit/$dpsOfficialVisitId/visitor")))
          }

          @Test
          fun `will not bother retrying mapping`() {
            mappingApiMock.verify(1, postRequestedFor(urlPathEqualTo("/mapping/official-visits/visitor")))
          }

          @Test
          fun `will track telemetry`() {
            verify(telemetryClient).trackEvent(
              eq("officialvisits-visitor-synchronisation-created-success"),
              check {
                assertThat(it["offenderNo"]).isEqualTo(offenderNo)
                assertThat(it["nomisVisitId"]).isEqualTo(nomisVisitId.toString())
                assertThat(it["dpsOfficialVisitId"]).isEqualTo(dpsOfficialVisitId.toString())
                assertThat(it["bookingId"]).isEqualTo(bookingId.toString())
                assertThat(it["nomisVisitorId"]).isEqualTo(nomisVisitorId.toString())
                assertThat(it["dpsOfficialVisitorId"]).isEqualTo(dpsOfficialVisitorId.toString())
                assertThat(it["nomisPersonId"]).isEqualTo(nomisPersonId.toString())
              },
              isNull(),
            )

            verify(telemetryClient).trackEvent(
              eq("from-nomis-sync-officialvisits-duplicate"),
              check {
                assertThat(it["type"]).isEqualTo("OFFICIALVISITOR")
                assertThat(it["existingNomisId"]).isEqualTo(nomisVisitorId.toString())
                assertThat(it["existingDpsId"]).isEqualTo("98765")
                assertThat(it["duplicateNomisId"]).isEqualTo(nomisVisitorId.toString())
                assertThat(it["duplicateDpsId"]).isEqualTo(dpsOfficialVisitorId.toString())
              },
              isNull(),
            )
          }
        }
      }
    }

    @Nested
    inner class WhenPrisonerVisitorCreatedInNomis {
      @BeforeEach
      fun setUp() {
        officialVisitsOffenderEventsQueue.sendMessage(
          officialVisitVisitorEvent(
            eventType = "OFFENDER_OFFICIAL_VISIT_VISITORS-INSERTED",
            offenderNo = offenderNo,
            visitId = nomisVisitId,
            bookingId = bookingId,
            visitVisitorId = nomisVisitorId,
          ),
        ).also { waitForAnyProcessingToComplete() }
      }

      @Test
      fun `will track telemetry`() {
        verify(telemetryClient).trackEvent(
          eq("officialvisits-visitor-synchronisation-created-ignored"),
          check {
            assertThat(it["offenderNo"]).isEqualTo(offenderNo)
            assertThat(it["nomisVisitId"]).isEqualTo(nomisVisitId.toString())
            assertThat(it["bookingId"]).isEqualTo(bookingId.toString())
            assertThat(it["nomisVisitorId"]).isEqualTo(nomisVisitorId.toString())
          },
          isNull(),
        )
      }
    }
  }

  @Nested
  @DisplayName("OFFENDER_OFFICIAL_VISIT_VISITORS-UPDATED")
  inner class OfficialVisitVisitorUpdated {

    @Nested
    inner class WhenUpdatedInNomis {
      @BeforeEach
      fun setUp() {
        officialVisitsOffenderEventsQueue.sendMessage(
          officialVisitVisitorEvent(
            eventType = "OFFENDER_OFFICIAL_VISIT_VISITORS-UPDATED",
            offenderNo = offenderNo,
            visitId = nomisVisitId,
            bookingId = bookingId,
            visitVisitorId = nomisVisitorId,
            personId = nomisPersonId,
          ),
        ).also { waitForAnyProcessingToComplete() }
      }

      @Test
      fun `will track telemetry`() {
        verify(telemetryClient).trackEvent(
          eq("officialvisits-visitor-synchronisation-updated-success"),
          check {
            assertThat(it["offenderNo"]).isEqualTo(offenderNo)
            assertThat(it["nomisVisitId"]).isEqualTo(nomisVisitId.toString())
            assertThat(it["bookingId"]).isEqualTo(bookingId.toString())
            assertThat(it["nomisVisitorId"]).isEqualTo(nomisVisitorId.toString())
            assertThat(it["nomisPersonId"]).isEqualTo(nomisPersonId.toString())
          },
          isNull(),
        )
      }
    }

    @Nested
    inner class WhenPrisonerVisitorUpdatedInNomis {
      @BeforeEach
      fun setUp() {
        officialVisitsOffenderEventsQueue.sendMessage(
          officialVisitVisitorEvent(
            eventType = "OFFENDER_OFFICIAL_VISIT_VISITORS-UPDATED",
            offenderNo = offenderNo,
            visitId = nomisVisitId,
            bookingId = bookingId,
            visitVisitorId = nomisVisitorId,
          ),
        ).also { waitForAnyProcessingToComplete() }
      }

      @Test
      fun `will track telemetry`() {
        verify(telemetryClient).trackEvent(
          eq("officialvisits-visitor-synchronisation-updated-ignored"),
          check {
            assertThat(it["offenderNo"]).isEqualTo(offenderNo)
            assertThat(it["nomisVisitId"]).isEqualTo(nomisVisitId.toString())
            assertThat(it["bookingId"]).isEqualTo(bookingId.toString())
            assertThat(it["nomisVisitorId"]).isEqualTo(nomisVisitorId.toString())
          },
          isNull(),
        )
      }
    }
  }

  @Nested
  @DisplayName("OFFENDER_OFFICIAL_VISIT_VISITORS-DELETED")
  inner class OfficialVisitVisitorDeleted {

    @Nested
    inner class WhenDeletedFromNomis {
      @BeforeEach
      fun setUp() {
        officialVisitsOffenderEventsQueue.sendMessage(
          officialVisitVisitorEvent(
            eventType = "OFFENDER_OFFICIAL_VISIT_VISITORS-DELETED",
            offenderNo = offenderNo,
            visitId = nomisVisitId,
            bookingId = bookingId,
            visitVisitorId = nomisVisitorId,
            personId = nomisPersonId,
          ),
        ).also { waitForAnyProcessingToComplete() }
      }

      @Test
      fun `will track telemetry`() {
        verify(telemetryClient).trackEvent(
          eq("officialvisits-visitor-synchronisation-deleted-success"),
          check {
            assertThat(it["offenderNo"]).isEqualTo(offenderNo)
            assertThat(it["nomisVisitId"]).isEqualTo(nomisVisitId.toString())
            assertThat(it["bookingId"]).isEqualTo(bookingId.toString())
            assertThat(it["nomisVisitorId"]).isEqualTo(nomisVisitorId.toString())
            assertThat(it["nomisPersonId"]).isEqualTo(nomisPersonId.toString())
          },
          isNull(),
        )
      }
    }

    @Nested
    inner class WhenPrisonerVisitorDeletedFromNomis {
      @BeforeEach
      fun setUp() {
        officialVisitsOffenderEventsQueue.sendMessage(
          officialVisitVisitorEvent(
            eventType = "OFFENDER_OFFICIAL_VISIT_VISITORS-DELETED",
            offenderNo = offenderNo,
            visitId = nomisVisitId,
            bookingId = bookingId,
            visitVisitorId = nomisVisitorId,
          ),
        ).also { waitForAnyProcessingToComplete() }
      }

      @Test
      fun `will track telemetry`() {
        verify(telemetryClient).trackEvent(
          eq("officialvisits-visitor-synchronisation-deleted-ignored"),
          check {
            assertThat(it["offenderNo"]).isEqualTo(offenderNo)
            assertThat(it["nomisVisitId"]).isEqualTo(nomisVisitId.toString())
            assertThat(it["bookingId"]).isEqualTo(bookingId.toString())
            assertThat(it["nomisVisitorId"]).isEqualTo(nomisVisitorId.toString())
          },
          isNull(),
        )
      }
    }
  }
}

fun officialVisitEvent(
  eventType: String,
  visitId: Long,
  offenderNo: String,
  agencyLocationId: String,
  bookingId: Long,
  auditModuleName: String = "OIDUVISI",
) = // language=JSON
  """{
    "MessageId": "ae06c49e-1f41-4b9f-b2f2-dcca610d02cd", "Type": "Notification", "Timestamp": "2019-10-21T14:01:18.500Z", 
    "Message": "{\"eventType\":\"$eventType\",\"eventDatetime\":\"2019-10-21T15:00:25.489964\",\"offenderIdDisplay\": \"$offenderNo\", \"bookingId\": $bookingId, \"visitId\": $visitId,\"auditModuleName\":\"$auditModuleName\",\"agencyLocationId\": \"$agencyLocationId\",\"nomisEventType\":\"$eventType\" }",
    "TopicArn": "arn:aws:sns:eu-west-1:000000000000:offender_events", 
    "MessageAttributes": {
      "eventType": {"Type": "String", "Value": "$eventType"}, 
      "id": {"Type": "String", "Value": "8b07cbd9-0820-0a0f-c32f-a9429b618e0b"}, 
      "contentType": {"Type": "String", "Value": "text/plain;charset=UTF-8"}, 
      "timestamp": {"Type": "Number.java.lang.Long", "Value": "1571666478344"}
    }
}
  """.trimIndent()

fun officialVisitVisitorEvent(
  eventType: String,
  visitVisitorId: Long,
  visitId: Long,
  offenderNo: String,
  bookingId: Long,
  auditModuleName: String = "OIDUVISI",
) = // language=JSON
  """{
    "MessageId": "ae06c49e-1f41-4b9f-b2f2-dcca610d02cd", "Type": "Notification", "Timestamp": "2019-10-21T14:01:18.500Z", 
    "Message": "{\"eventType\":\"$eventType\",\"eventDatetime\":\"2019-10-21T15:00:25.489964\",\"offenderIdDisplay\": \"$offenderNo\", \"bookingId\": $bookingId, \"visitId\": $visitId,\"auditModuleName\":\"$auditModuleName\",\"visitVisitorId\": $visitVisitorId,\"nomisEventType\":\"$eventType\" }",
    "TopicArn": "arn:aws:sns:eu-west-1:000000000000:offender_events", 
    "MessageAttributes": {
      "eventType": {"Type": "String", "Value": "$eventType"}, 
      "id": {"Type": "String", "Value": "8b07cbd9-0820-0a0f-c32f-a9429b618e0b"}, 
      "contentType": {"Type": "String", "Value": "text/plain;charset=UTF-8"}, 
      "timestamp": {"Type": "Number.java.lang.Long", "Value": "1571666478344"}
    }
}
  """.trimIndent()

fun officialVisitVisitorEvent(
  eventType: String,
  visitVisitorId: Long,
  visitId: Long,
  offenderNo: String,
  personId: Long,
  bookingId: Long,
  auditModuleName: String = "OIDUVISI",
) = // language=JSON
  """{
    "MessageId": "ae06c49e-1f41-4b9f-b2f2-dcca610d02cd", "Type": "Notification", "Timestamp": "2019-10-21T14:01:18.500Z", 
    "Message": "{\"eventType\":\"$eventType\",\"eventDatetime\":\"2019-10-21T15:00:25.489964\",\"offenderIdDisplay\": \"$offenderNo\", \"bookingId\": $bookingId, \"personId\": $personId, \"visitId\": $visitId,\"auditModuleName\":\"$auditModuleName\",\"visitVisitorId\": $visitVisitorId,\"nomisEventType\":\"$eventType\" }",
    "TopicArn": "arn:aws:sns:eu-west-1:000000000000:offender_events", 
    "MessageAttributes": {
      "eventType": {"Type": "String", "Value": "$eventType"}, 
      "id": {"Type": "String", "Value": "8b07cbd9-0820-0a0f-c32f-a9429b618e0b"}, 
      "contentType": {"Type": "String", "Value": "text/plain;charset=UTF-8"}, 
      "timestamp": {"Type": "Number.java.lang.Long", "Value": "1571666478344"}
    }
}
  """.trimIndent()
