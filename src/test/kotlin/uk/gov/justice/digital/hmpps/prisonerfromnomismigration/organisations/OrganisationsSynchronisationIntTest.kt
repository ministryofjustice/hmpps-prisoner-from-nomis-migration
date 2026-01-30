package uk.gov.justice.digital.hmpps.prisonerfromnomismigration.organisations

import com.github.tomakehurst.wiremock.client.WireMock.deleteRequestedFor
import com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor
import com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor
import com.github.tomakehurst.wiremock.client.WireMock.putRequestedFor
import com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo
import com.github.tomakehurst.wiremock.client.WireMock.urlPathMatching
import org.assertj.core.api.Assertions.assertThat
import org.awaitility.kotlin.await
import org.awaitility.kotlin.untilAsserted
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.mockito.Mockito.eq
import org.mockito.kotlin.any
import org.mockito.kotlin.atLeastOnce
import org.mockito.kotlin.check
import org.mockito.kotlin.isNull
import org.mockito.kotlin.verify
import org.springframework.beans.factory.annotation.Autowired
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.integration.SqsIntegrationTestBase
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.integration.hasMessagesOnDLQQueue
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.integration.sendMessage
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomismappings.model.DuplicateErrorContentObject
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomismappings.model.DuplicateMappingErrorResponse
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomismappings.model.OrganisationsMappingDto
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomismappings.model.OrganisationsMappingDto.MappingType.MIGRATED
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomismappings.model.OrganisationsMappingDto.MappingType.NOMIS_CREATED
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomisprisoner.model.CodeDescription
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomisprisoner.model.CorporateAddress
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomisprisoner.model.CorporateOrganisationType
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomisprisoner.model.CorporatePhoneNumber
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomisprisoner.model.NomisAudit
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.organisations.OrganisationsDpsApiExtension.Companion.dpsOrganisationsServer
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.organisations.OrganisationsDpsApiMockServer.Companion.syncAddressResponse
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.organisations.OrganisationsDpsApiMockServer.Companion.syncCreateAddressPhoneResponse
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.organisations.OrganisationsDpsApiMockServer.Companion.syncEmailResponse
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.organisations.OrganisationsDpsApiMockServer.Companion.syncOrganisationResponse
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.organisations.OrganisationsDpsApiMockServer.Companion.syncPhoneResponse
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.organisations.OrganisationsDpsApiMockServer.Companion.syncWebResponse
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.organisations.model.SyncCreateAddressPhoneRequest
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.organisations.model.SyncCreateAddressRequest
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.organisations.model.SyncCreateEmailRequest
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.organisations.model.SyncCreateOrganisationRequest
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.organisations.model.SyncCreatePhoneRequest
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.organisations.model.SyncCreateWebRequest
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.organisations.model.SyncUpdateAddressPhoneRequest
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.organisations.model.SyncUpdateAddressRequest
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.organisations.model.SyncUpdateEmailRequest
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.organisations.model.SyncUpdateOrganisationRequest
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.organisations.model.SyncUpdatePhoneRequest
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.organisations.model.SyncUpdateTypesRequest
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.organisations.model.SyncUpdateWebRequest
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.wiremock.MappingApiExtension.Companion.mappingApi
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.wiremock.withRequestBodyJsonPath
import java.time.LocalDate
import java.time.LocalDateTime

class OrganisationsSynchronisationIntTest : SqsIntegrationTestBase() {
  @Autowired
  private lateinit var nomisApiMock: OrganisationsNomisApiMockServer

  private val dpsApiMock = dpsOrganisationsServer

  @Autowired
  private lateinit var mappingApiMock: OrganisationsMappingApiMockServer

  @Nested
  @DisplayName("CORPORATE-INSERTED")
  inner class CorporateInserted {
    private val corporateAndOrganisationId = 123456L

    @Nested
    inner class WhenCreatedInDpsByOrganisationSyncService {
      @BeforeEach
      fun setUp() {
        organisationsOffenderEventsQueue.sendMessage(
          corporateEvent(
            eventType = "CORPORATE-INSERTED",
            corporateId = corporateAndOrganisationId,
            auditModuleName = "DPS_SYNCHRONISATION_ORGANISATION",
          ),
        ).also { waitForAnyProcessingToComplete() }
      }

      @Test
      fun `will not create organisation in DPS`() {
        dpsApiMock.verify(0, postRequestedFor(urlPathEqualTo("/sync/organisation")))
      }

      @Test
      fun `will track telemetry`() {
        verify(telemetryClient).trackEvent(
          eq("organisations-corporate-synchronisation-created-skipped"),
          check {
            assertThat(it["nomisCorporateId"]).isEqualTo(corporateAndOrganisationId.toString())
          },
          isNull(),
        )
      }
    }

    @Nested
    inner class WhenCreatedInDpsByAnotherSynService {
      @BeforeEach
      fun setUp() {
        mappingApiMock.stubGetByNomisCorporateIdOrNull(nomisCorporateId = corporateAndOrganisationId, mapping = null)
        nomisApiMock.stubGetCorporateOrganisation(corporateId = corporateAndOrganisationId)
        dpsApiMock.stubCreateOrganisation(syncOrganisationResponse().copy(organisationId = corporateAndOrganisationId))
        mappingApiMock.stubCreateCorporateMapping()
        organisationsOffenderEventsQueue.sendMessage(
          corporateEvent(
            eventType = "CORPORATE-INSERTED",
            corporateId = corporateAndOrganisationId,
            auditModuleName = "DPS_SYNCHRONISATION",
          ),
        ).also { waitForAnyProcessingToComplete() }
      }

      @Test
      fun `will create organisation in DPS`() {
        dpsApiMock.verify(postRequestedFor(urlPathEqualTo("/sync/organisation")))
      }

      @Test
      fun `will track telemetry`() {
        verify(telemetryClient).trackEvent(
          eq("organisations-corporate-synchronisation-created-success"),
          any(),
          isNull(),
        )
      }
    }

    @Nested
    inner class WhenCreatedInNomis {
      @BeforeEach
      fun setUp() {
        mappingApiMock.stubGetByNomisCorporateIdOrNull(nomisCorporateId = corporateAndOrganisationId, mapping = null)
        nomisApiMock.stubGetCorporateOrganisation(
          corporate = corporateOrganisation().copy(
            id = corporateAndOrganisationId,
            name = "Mesh Solicitors Ltd",
            caseload = CodeDescription("LEI", "Leeds"),
            active = true,
            programmeNumber = "1",
            vatNumber = "ABS1234",
            comment = "Good people",
            audit = NomisAudit(
              createUsername = "J.SPEAK",
              createDatetime = LocalDateTime.parse("2024-09-01T13:31"),
            ),
          ),
        )
        dpsApiMock.stubCreateOrganisation(syncOrganisationResponse().copy(organisationId = corporateAndOrganisationId))
        mappingApiMock.stubCreateCorporateMapping()
        organisationsOffenderEventsQueue.sendMessage(
          corporateEvent(
            eventType = "CORPORATE-INSERTED",
            corporateId = corporateAndOrganisationId,
          ),
        ).also { waitForAnyProcessingToComplete() }
      }

      @Test
      fun `will check if mapping already exists for organisation`() {
        mappingApi.verify(getRequestedFor(urlPathEqualTo("/mapping/corporate/organisation/nomis-corporate-id/$corporateAndOrganisationId")))
      }

      @Test
      fun `will retrieve the organisation details from NOMIS`() {
        nomisApiMock.verify(getRequestedFor(urlPathEqualTo("/corporates/$corporateAndOrganisationId")))
      }

      @Test
      fun `will create the organisation in DPS from the organisation`() {
        dpsApiMock.verify(postRequestedFor(urlPathEqualTo("/sync/organisation")))
        val request: SyncCreateOrganisationRequest = OrganisationsDpsApiExtension.getRequestBody(
          postRequestedFor(urlPathEqualTo("/sync/organisation")),
        )
        with(request) {
          assertThat(organisationId).isEqualTo(corporateAndOrganisationId)
          assertThat(organisationName).isEqualTo("Mesh Solicitors Ltd")
          assertThat(caseloadId).isEqualTo("LEI")
          assertThat(programmeNumber).isEqualTo("1")
          assertThat(vatNumber).isEqualTo("ABS1234")
          assertThat(active).isTrue()
          assertThat(comments).isEqualTo("Good people")
          assertThat(deactivatedDate).isNull()
          assertThat(createdBy).isEqualTo("J.SPEAK")
          assertThat(createdTime).isEqualTo("2024-09-01T13:31")
        }
      }

      @Test
      fun `will create a mapping between the DPS and NOMIS record`() {
        mappingApiMock.verify(
          postRequestedFor(urlPathEqualTo("/mapping/corporate/organisation"))
            .withRequestBodyJsonPath("mappingType", "NOMIS_CREATED")
            .withRequestBodyJsonPath("dpsId", "$corporateAndOrganisationId")
            .withRequestBodyJsonPath("nomisId", "$corporateAndOrganisationId"),
        )
      }

      @Test
      fun `will track telemetry`() {
        verify(telemetryClient).trackEvent(
          eq("organisations-corporate-synchronisation-created-success"),
          check {
            assertThat(it["nomisCorporateId"]).isEqualTo("$corporateAndOrganisationId")
            assertThat(it["dpsOrganisationId"]).isEqualTo("$corporateAndOrganisationId")
          },
          isNull(),
        )
      }
    }

    @Nested
    inner class WhenAlreadyCreated {
      @BeforeEach
      fun setUp() {
        mappingApiMock.stubGetByNomisCorporateIdOrNull(nomisCorporateId = corporateAndOrganisationId, mapping = OrganisationsMappingDto(dpsId = "$corporateAndOrganisationId", nomisId = corporateAndOrganisationId, mappingType = NOMIS_CREATED))
        organisationsOffenderEventsQueue.sendMessage(
          corporateEvent(
            eventType = "CORPORATE-INSERTED",
            corporateId = corporateAndOrganisationId,
          ),
        ).also { waitForAnyProcessingToComplete() }
      }

      @Test
      fun `will not create organisation in DPS`() {
        dpsApiMock.verify(0, postRequestedFor(urlPathEqualTo("/sync/organisation")))
      }

      @Test
      fun `will track telemetry`() {
        verify(telemetryClient).trackEvent(
          eq("organisations-corporate-synchronisation-created-ignored"),
          check {
            assertThat(it["nomisCorporateId"]).isEqualTo(corporateAndOrganisationId.toString())
            assertThat(it["dpsOrganisationId"]).isEqualTo(corporateAndOrganisationId.toString())
          },
          isNull(),
        )
      }
    }

    @Nested
    inner class WhenDuplicateMapping {
      @BeforeEach
      fun setUp() {
        mappingApiMock.stubGetByNomisCorporateIdOrNull(nomisCorporateId = corporateAndOrganisationId, mapping = null)
        nomisApiMock.stubGetCorporateOrganisation(
          corporate = corporateOrganisation(corporateAndOrganisationId),
        )
        dpsApiMock.stubCreateOrganisation(syncOrganisationResponse().copy(organisationId = corporateAndOrganisationId))
        mappingApiMock.stubCreateCorporateMapping(
          error = DuplicateMappingErrorResponse(
            moreInfo = DuplicateErrorContentObject(
              duplicate = OrganisationsMappingDto(
                dpsId = corporateAndOrganisationId.toString(),
                nomisId = corporateAndOrganisationId,
                mappingType = NOMIS_CREATED,
              ),
              existing = OrganisationsMappingDto(
                dpsId = "9999",
                nomisId = corporateAndOrganisationId,
                mappingType = NOMIS_CREATED,
              ),
            ),
            errorCode = 1409,
            status = DuplicateMappingErrorResponse.Status._409_CONFLICT,
            userMessage = "Duplicate mapping",
          ),
        )

        organisationsOffenderEventsQueue.sendMessage(
          corporateEvent(
            eventType = "CORPORATE-INSERTED",
            corporateId = corporateAndOrganisationId,
          ),
        ).also { waitForAnyProcessingToComplete("from-nomis-sync-organisations-duplicate") }
      }

      @Test
      fun `will create the organisation in DPS once`() {
        dpsApiMock.verify(1, postRequestedFor(urlPathEqualTo("/sync/organisation")))
      }

      @Test
      fun `will attempt to create a mapping between the DPS and NOMIS record once`() {
        mappingApiMock.verify(
          1,
          postRequestedFor(urlPathEqualTo("/mapping/corporate/organisation"))
            .withRequestBodyJsonPath("mappingType", "NOMIS_CREATED")
            .withRequestBodyJsonPath("dpsId", "$corporateAndOrganisationId")
            .withRequestBodyJsonPath("nomisId", "$corporateAndOrganisationId"),
        )
      }

      @Test
      fun `will track telemetry for both overall success and duplicate`() {
        verify(telemetryClient).trackEvent(
          eq("organisations-corporate-synchronisation-created-success"),
          check {
            assertThat(it["nomisCorporateId"]).isEqualTo("$corporateAndOrganisationId")
            assertThat(it["dpsOrganisationId"]).isEqualTo("$corporateAndOrganisationId")
          },
          isNull(),
        )
        verify(telemetryClient).trackEvent(
          eq("from-nomis-sync-organisations-duplicate"),
          check {
            assertThat(it["existingNomisId"]).isEqualTo("$corporateAndOrganisationId")
            assertThat(it["existingDpsId"]).isEqualTo("9999")
            assertThat(it["duplicateNomisId"]).isEqualTo("$corporateAndOrganisationId")
            assertThat(it["duplicateDpsId"]).isEqualTo("$corporateAndOrganisationId")
            assertThat(it["type"]).isEqualTo("CORPORATE")
          },
          isNull(),
        )
      }
    }

    @Nested
    inner class MappingCreateFails {
      @BeforeEach
      fun setUp() {
        mappingApiMock.stubGetByNomisCorporateIdOrNull(nomisCorporateId = corporateAndOrganisationId, mapping = null)
        nomisApiMock.stubGetCorporateOrganisation(
          corporate = corporateOrganisation(corporateAndOrganisationId),
        )
        dpsApiMock.stubCreateOrganisation(syncOrganisationResponse().copy(organisationId = corporateAndOrganisationId))
        mappingApiMock.stubCreateCorporateMappingFailureFollowedBySuccess()
        organisationsOffenderEventsQueue.sendMessage(
          corporateEvent(
            eventType = "CORPORATE-INSERTED",
            corporateId = corporateAndOrganisationId,
          ),
        ).also { waitForAnyProcessingToComplete("organisations-corporate-mapping-synchronisation-created") }
      }

      @Test
      fun `will create the organisation in DPS once`() {
        dpsApiMock.verify(1, postRequestedFor(urlPathEqualTo("/sync/organisation")))
      }

      @Test
      fun `will create a mapping between the DPS and NOMIS record`() {
        mappingApiMock.verify(
          2,
          postRequestedFor(urlPathEqualTo("/mapping/corporate/organisation"))
            .withRequestBodyJsonPath("mappingType", "NOMIS_CREATED")
            .withRequestBodyJsonPath("dpsId", "$corporateAndOrganisationId")
            .withRequestBodyJsonPath("nomisId", "$corporateAndOrganisationId"),
        )
      }

      @Test
      fun `will track telemetry`() {
        verify(telemetryClient).trackEvent(
          eq("organisations-corporate-synchronisation-created-success"),
          check {
            assertThat(it["nomisCorporateId"]).isEqualTo("$corporateAndOrganisationId")
            assertThat(it["dpsOrganisationId"]).isEqualTo("$corporateAndOrganisationId")
          },
          isNull(),
        )
      }
    }
  }

  @Nested
  @DisplayName("CORPORATE-UPDATED")
  inner class CorporateUpdated {
    private val nomisCorporateId = 123456L
    private val dpsOrganisationId = 123456L

    @Nested
    inner class WhenUpdatedInDpsByOrganisationSyncService {
      @BeforeEach
      fun setUp() {
        organisationsOffenderEventsQueue.sendMessage(
          corporateEvent(
            eventType = "CORPORATE-UPDATED",
            corporateId = nomisCorporateId,
            auditModuleName = "DPS_SYNCHRONISATION_ORGANISATION",
          ),
        ).also { waitForAnyProcessingToComplete() }
      }

      @Test
      fun `will not update organisation in DPS`() {
        dpsApiMock.verify(0, putRequestedFor(urlPathMatching("/sync/organisation/.*")))
      }

      @Test
      fun `will track telemetry`() {
        verify(telemetryClient).trackEvent(
          eq("organisations-corporate-synchronisation-updated-skipped"),
          check {
            assertThat(it["nomisCorporateId"]).isEqualTo(nomisCorporateId.toString())
          },
          isNull(),
        )
      }
    }

    @Nested
    inner class WhenUpdatedInDpsByAnotherSyncService {
      @BeforeEach
      fun setUp() {
        mappingApiMock.stubGetByNomisCorporateId(
          nomisCorporateId = nomisCorporateId,
          mapping = OrganisationsMappingDto(dpsId = dpsOrganisationId.toString(), nomisId = nomisCorporateId, mappingType = MIGRATED),
        )
        nomisApiMock.stubGetCorporateOrganisation(
          corporateId = nomisCorporateId,
          corporate = corporateOrganisation().copy(
            id = nomisCorporateId,
            audit = NomisAudit(
              createUsername = "J.SPEAK",
              createDatetime = LocalDateTime.parse("2024-09-01T13:31"),
              modifyUserId = "T.SMITH",
              modifyDatetime = LocalDateTime.parse("2024-10-01T13:31"),
            ),
          ),
        )
        dpsApiMock.stubUpdateOrganisation(organisationId = dpsOrganisationId)

        organisationsOffenderEventsQueue.sendMessage(
          corporateEvent(
            eventType = "CORPORATE-UPDATED",
            corporateId = nomisCorporateId,
            auditModuleName = "DPS_SYNCHRONISATION",
          ),
        ).also { waitForAnyProcessingToComplete() }
      }

      @Test
      fun `will update organisation in DPS`() {
        dpsApiMock.verify(putRequestedFor(urlPathMatching("/sync/organisation/.*")))
      }

      @Test
      fun `will track telemetry`() {
        verify(telemetryClient).trackEvent(
          eq("organisations-corporate-synchronisation-updated-success"),
          any(),
          isNull(),
        )
      }
    }

    @Nested
    inner class WhenUpdatedInNomis {

      @BeforeEach
      fun setUp() {
        mappingApiMock.stubGetByNomisCorporateId(
          nomisCorporateId = nomisCorporateId,
          mapping = OrganisationsMappingDto(dpsId = dpsOrganisationId.toString(), nomisId = nomisCorporateId, mappingType = MIGRATED),
        )
        nomisApiMock.stubGetCorporateOrganisation(
          corporateId = nomisCorporateId,
          corporate = corporateOrganisation().copy(
            id = nomisCorporateId,
            name = "Mesh Solicitors Ltd",
            caseload = CodeDescription("LEI", "Leeds"),
            active = false,
            expiryDate = LocalDate.parse("2020-01-01"),
            programmeNumber = "1",
            vatNumber = "ABS1234",
            comment = "Good people",
            audit = NomisAudit(
              createUsername = "J.SPEAK",
              createDatetime = LocalDateTime.parse("2024-09-01T13:31"),
              modifyUserId = "T.SMITH",
              modifyDatetime = LocalDateTime.parse("2024-10-01T13:31"),
            ),
          ),
        )
        dpsApiMock.stubUpdateOrganisation(organisationId = dpsOrganisationId)

        organisationsOffenderEventsQueue.sendMessage(
          corporateEvent(
            eventType = "CORPORATE-UPDATED",
            corporateId = nomisCorporateId,
          ),
        ).also { waitForAnyProcessingToComplete() }
      }

      @Test
      fun `will track telemetry`() {
        verify(telemetryClient).trackEvent(
          eq("organisations-corporate-synchronisation-updated-success"),
          check {
            assertThat(it["nomisCorporateId"]).isEqualTo("$nomisCorporateId")
            assertThat(it["dpsOrganisationId"]).isEqualTo("$dpsOrganisationId")
          },
          isNull(),
        )
      }

      @Test
      fun `will update the organisation in DPS from the NOMIS corporate organisation`() {
        dpsApiMock.verify(putRequestedFor(urlPathEqualTo("/sync/organisation/$dpsOrganisationId")))
        val request: SyncUpdateOrganisationRequest = OrganisationsDpsApiExtension.getRequestBody(putRequestedFor(urlPathEqualTo("/sync/organisation/$dpsOrganisationId")))
        with(request) {
          assertThat(organisationName).isEqualTo("Mesh Solicitors Ltd")
          assertThat(caseloadId).isEqualTo("LEI")
          assertThat(programmeNumber).isEqualTo("1")
          assertThat(vatNumber).isEqualTo("ABS1234")
          assertThat(active).isFalse()
          assertThat(comments).isEqualTo("Good people")
          assertThat(deactivatedDate).isEqualTo(LocalDate.parse("2020-01-01"))
          assertThat(updatedBy).isEqualTo("T.SMITH")
          assertThat(updatedTime).isEqualTo("2024-10-01T13:31")
        }
      }
    }
  }

  @Nested
  @DisplayName("CORPORATE-DELETED")
  inner class CorporateDeleted {
    private val nomisCorporateId = 123456L
    private val dpsOrganisationId = 123456L

    @Nested
    inner class WhenMappingExists {

      @BeforeEach
      fun setUp() {
        mappingApiMock.stubGetByNomisCorporateIdOrNull(
          nomisCorporateId = nomisCorporateId,
          mapping = OrganisationsMappingDto(dpsId = dpsOrganisationId.toString(), nomisId = nomisCorporateId, mappingType = MIGRATED),
        )
        dpsApiMock.stubDeleteOrganisation(organisationId = dpsOrganisationId)
        mappingApiMock.stubDeleteByNomisCorporateId(nomisCorporateId)

        organisationsOffenderEventsQueue.sendMessage(
          corporateEvent(
            eventType = "CORPORATE-DELETED",
            corporateId = nomisCorporateId,
            auditModuleName = "DPS_SYNCHRONISATION",
          ),
        ).also { waitForAnyProcessingToComplete() }
      }

      @Test
      fun `will track telemetry`() {
        verify(telemetryClient).trackEvent(
          eq("organisations-corporate-synchronisation-deleted-success"),
          check {
            assertThat(it["nomisCorporateId"]).isEqualTo(nomisCorporateId.toString())
            assertThat(it["dpsOrganisationId"]).isEqualTo(dpsOrganisationId.toString())
          },
          isNull(),
        )
      }

      @Test
      fun `will delete the organisation from DPS`() {
        dpsApiMock.verify(deleteRequestedFor(urlPathEqualTo("/sync/organisation/$dpsOrganisationId")))
      }

      @Test
      fun `will delete the corporate mapping`() {
        mappingApi.verify(deleteRequestedFor(urlPathEqualTo("/mapping/corporate/organisation/nomis-corporate-id/$nomisCorporateId")))
      }
    }

    @Nested
    inner class WhenMappingDoesNotExist {
      @BeforeEach
      fun setUp() {
        mappingApiMock.stubGetByNomisCorporateIdOrNull(
          nomisCorporateId = nomisCorporateId,
          mapping = null,
        )

        organisationsOffenderEventsQueue.sendMessage(
          corporateEvent(
            eventType = "CORPORATE-DELETED",
            corporateId = nomisCorporateId,
            auditModuleName = "DPS_SYNCHRONISATION",
          ),
        ).also { waitForAnyProcessingToComplete() }
      }

      @Test
      fun `will track telemetry for delete ignored`() {
        verify(telemetryClient).trackEvent(
          eq("organisations-corporate-synchronisation-deleted-ignored"),
          check {
            assertThat(it["nomisCorporateId"]).isEqualTo(nomisCorporateId.toString())
          },
          isNull(),
        )
      }
    }
  }

  @Nested
  @DisplayName("ADDRESSES_CORPORATE-INSERTED")
  inner class CorporateAddressInserted {
    private val corporateAndOrganisationId = 123456L
    private val nomisAddressId = 34567L
    private val dpsOrganisationAddressId = 76543L

    @Nested
    inner class WhenCreatedInDpsByOrganisationSyncService {
      @BeforeEach
      fun setUp() {
        organisationsOffenderEventsQueue.sendMessage(
          corporateAddressEvent(
            eventType = "ADDRESSES_CORPORATE-INSERTED",
            corporateId = corporateAndOrganisationId,
            addressId = nomisAddressId,
            auditModuleName = "DPS_SYNCHRONISATION_ORGANISATION",
          ),
        ).also { waitForAnyProcessingToComplete() }
      }

      @Test
      fun `will not create address in DPS`() {
        dpsApiMock.verify(0, postRequestedFor(urlPathEqualTo("/sync/organisation-address")))
      }

      @Test
      fun `will track telemetry`() {
        verify(telemetryClient).trackEvent(
          eq("organisations-address-synchronisation-created-skipped"),
          check {
            assertThat(it["nomisCorporateId"]).isEqualTo("$corporateAndOrganisationId")
            assertThat(it["dpsOrganisationId"]).isEqualTo("$corporateAndOrganisationId")
            assertThat(it["nomisAddressId"]).isEqualTo("$nomisAddressId")
          },
          isNull(),
        )
      }
    }

    @Nested
    inner class WhenCreatedInDpsByAnotherSyncService {
      @BeforeEach
      fun setUp() {
        mappingApiMock.stubGetByNomisAddressIdOrNull(nomisAddressId = nomisAddressId, mapping = null)
        nomisApiMock.stubGetCorporateOrganisation(
          corporate = corporateOrganisation().withAddress(
            CorporateAddress(
              id = nomisAddressId,
              phoneNumbers = emptyList(),
              validatedPAF = false,
              primaryAddress = true,
              mailAddress = true,
              noFixedAddress = false,
              type = CodeDescription("HOME", "Home Address"),
              startDate = LocalDate.parse("2021-01-01"),
              audit = NomisAudit(
                createUsername = "J.SPEAK",
                createDatetime = LocalDateTime.parse("2024-09-01T13:31"),
              ),
              isServices = true,
            ),
          ),
        )
        dpsApiMock.stubCreateOrganisationAddress(syncAddressResponse().copy(organisationAddressId = dpsOrganisationAddressId))
        mappingApiMock.stubCreateAddressMapping()

        organisationsOffenderEventsQueue.sendMessage(
          corporateAddressEvent(
            eventType = "ADDRESSES_CORPORATE-INSERTED",
            corporateId = corporateAndOrganisationId,
            addressId = nomisAddressId,
            auditModuleName = "DPS_SYNCHRONISATION",
          ),
        ).also { waitForAnyProcessingToComplete() }
      }

      @Test
      fun `will create address in DPS`() {
        dpsApiMock.verify(postRequestedFor(urlPathEqualTo("/sync/organisation-address")))
      }

      @Test
      fun `will track telemetry`() {
        verify(telemetryClient).trackEvent(
          eq("organisations-address-synchronisation-created-success"),
          any(),
          isNull(),
        )
      }
    }

    @Nested
    inner class WhenCreatedInNomis {
      @BeforeEach
      fun setUp() {
        mappingApiMock.stubGetByNomisAddressIdOrNull(nomisAddressId = nomisAddressId, mapping = null)
        nomisApiMock.stubGetCorporateOrganisation(
          corporate = corporateOrganisation().withAddress(
            CorporateAddress(
              id = nomisAddressId,
              phoneNumbers = emptyList(),
              comment = "nice area",
              validatedPAF = false,
              primaryAddress = true,
              mailAddress = true,
              noFixedAddress = false,
              type = CodeDescription("HOME", "Home Address"),
              flat = "Flat 1",
              premise = "Brown Court",
              locality = "Broomhill",
              street = "Broomhill Street",
              postcode = "S1 6GG",
              city = CodeDescription("12345", "Sheffield"),
              county = CodeDescription("S.YORKSHIRE", "South Yorkshire"),
              country = CodeDescription("GBR", "United Kingdom"),
              startDate = LocalDate.parse("2021-01-01"),
              endDate = LocalDate.parse("2025-01-01"),
              isServices = true,
              contactPersonName = "Bob Brown",
              businessHours = "10am to 10pm Monday to Friday",
              audit = NomisAudit(
                createUsername = "J.SPEAK",
                createDatetime = LocalDateTime.parse("2024-09-01T13:31"),
                modifyUserId = "T.SMITH",
                modifyDatetime = LocalDateTime.parse("2024-10-01T13:31"),
              ),
            ),
          ),
        )
        dpsApiMock.stubCreateOrganisationAddress(syncAddressResponse().copy(organisationAddressId = dpsOrganisationAddressId))
        mappingApiMock.stubCreateAddressMapping()
        organisationsOffenderEventsQueue.sendMessage(
          corporateAddressEvent(
            eventType = "ADDRESSES_CORPORATE-INSERTED",
            addressId = nomisAddressId,
            corporateId = corporateAndOrganisationId,
          ),
        ).also { waitForAnyProcessingToComplete() }
      }

      @Test
      fun `will check if mapping already exists for address`() {
        mappingApi.verify(getRequestedFor(urlPathEqualTo("/mapping/corporate/address/nomis-address-id/$nomisAddressId")))
      }

      @Test
      fun `will retrieve the organisation details from NOMIS`() {
        nomisApiMock.verify(getRequestedFor(urlPathEqualTo("/corporates/$corporateAndOrganisationId")))
      }

      @Test
      fun `will create the organisation address in DPS from the organisation`() {
        dpsApiMock.verify(postRequestedFor(urlPathEqualTo("/sync/organisation-address")))
        val request: SyncCreateAddressRequest = OrganisationsDpsApiExtension.getRequestBody(
          postRequestedFor(urlPathEqualTo("/sync/organisation-address")),
        )
        with(request) {
          assertThat(organisationId).isEqualTo(corporateAndOrganisationId)
          assertThat(addressType).isEqualTo("HOME")
          assertThat(primaryAddress).isTrue()
          assertThat(flat).isEqualTo("Flat 1")
          assertThat(property).isEqualTo("Brown Court")
          assertThat(street).isEqualTo("Broomhill Street")
          assertThat(area).isEqualTo("Broomhill")
          assertThat(cityCode).isEqualTo("12345")
          assertThat(countyCode).isEqualTo("S.YORKSHIRE")
          assertThat(countryCode).isEqualTo("GBR")
          assertThat(postcode).isEqualTo("S1 6GG")
          assertThat(mailAddress).isTrue()
          assertThat(startDate).isEqualTo(LocalDate.parse("2021-01-01"))
          assertThat(endDate).isEqualTo(LocalDate.parse("2025-01-01"))
          assertThat(noFixedAddress).isFalse()
          assertThat(comments).isEqualTo("nice area")
          assertThat(serviceAddress).isTrue()
          assertThat(businessHours).isEqualTo("10am to 10pm Monday to Friday")
          assertThat(contactPersonName).isEqualTo("Bob Brown")
          assertThat(createdBy).isEqualTo("J.SPEAK")
          assertThat(createdTime).isEqualTo("2024-09-01T13:31")
        }
      }

      @Test
      fun `will create a mapping between the DPS and NOMIS record`() {
        mappingApiMock.verify(
          postRequestedFor(urlPathEqualTo("/mapping/corporate/address"))
            .withRequestBodyJsonPath("mappingType", "NOMIS_CREATED")
            .withRequestBodyJsonPath("dpsId", "$dpsOrganisationAddressId")
            .withRequestBodyJsonPath("nomisId", "$nomisAddressId"),
        )
      }

      @Test
      fun `will track telemetry`() {
        verify(telemetryClient).trackEvent(
          eq("organisations-address-synchronisation-created-success"),
          check {
            assertThat(it["nomisCorporateId"]).isEqualTo("$corporateAndOrganisationId")
            assertThat(it["dpsOrganisationId"]).isEqualTo("$corporateAndOrganisationId")
            assertThat(it["nomisAddressId"]).isEqualTo("$nomisAddressId")
            assertThat(it["dpsOrganisationAddressId"]).isEqualTo("$dpsOrganisationAddressId")
          },
          isNull(),
        )
      }
    }

    @Nested
    inner class WhenAlreadyCreated {
      @BeforeEach
      fun setUp() {
        mappingApiMock.stubGetByNomisAddressIdOrNull(nomisAddressId = nomisAddressId, mapping = OrganisationsMappingDto(dpsId = "$dpsOrganisationAddressId", nomisId = nomisAddressId, mappingType = NOMIS_CREATED))
        organisationsOffenderEventsQueue.sendMessage(
          corporateAddressEvent(
            eventType = "ADDRESSES_CORPORATE-INSERTED",
            addressId = nomisAddressId,
            corporateId = corporateAndOrganisationId,
          ),
        ).also { waitForAnyProcessingToComplete() }
      }

      @Test
      fun `will not create organisation in DPS`() {
        dpsApiMock.verify(0, postRequestedFor(urlPathEqualTo("/sync/organisation-address")))
      }

      @Test
      fun `will track telemetry`() {
        verify(telemetryClient).trackEvent(
          eq("organisations-address-synchronisation-created-ignored"),
          check {
            assertThat(it["nomisCorporateId"]).isEqualTo("$corporateAndOrganisationId")
            assertThat(it["dpsOrganisationId"]).isEqualTo("$corporateAndOrganisationId")
            assertThat(it["nomisAddressId"]).isEqualTo("$nomisAddressId")
            assertThat(it["dpsOrganisationAddressId"]).isEqualTo("$dpsOrganisationAddressId")
          },
          isNull(),
        )
      }
    }

    @Nested
    inner class WhenDuplicateMapping {
      @BeforeEach
      fun setUp() {
        mappingApiMock.stubGetByNomisAddressIdOrNull(nomisAddressId = nomisAddressId, mapping = null)
        nomisApiMock.stubGetCorporateOrganisation(
          corporate = corporateOrganisation(corporateAndOrganisationId).withAddress(corporateAddress().copy(id = nomisAddressId)),
        )

        dpsApiMock.stubCreateOrganisationAddress(syncAddressResponse().copy(organisationAddressId = dpsOrganisationAddressId))
        mappingApiMock.stubCreateAddressMapping(
          error = DuplicateMappingErrorResponse(
            moreInfo = DuplicateErrorContentObject(
              duplicate = OrganisationsMappingDto(
                dpsId = "$dpsOrganisationAddressId",
                nomisId = nomisAddressId,
                mappingType = NOMIS_CREATED,
              ),
              existing = OrganisationsMappingDto(
                dpsId = "9999",
                nomisId = nomisAddressId,
                mappingType = NOMIS_CREATED,
              ),
            ),
            errorCode = 1409,
            status = DuplicateMappingErrorResponse.Status._409_CONFLICT,
            userMessage = "Duplicate mapping",
          ),
        )

        organisationsOffenderEventsQueue.sendMessage(
          corporateAddressEvent(
            eventType = "ADDRESSES_CORPORATE-INSERTED",
            addressId = nomisAddressId,
            corporateId = corporateAndOrganisationId,
          ),
        ).also { waitForAnyProcessingToComplete("from-nomis-sync-organisations-duplicate") }
      }

      @Test
      fun `will create the organisation address in DPS once`() {
        dpsApiMock.verify(1, postRequestedFor(urlPathEqualTo("/sync/organisation-address")))
      }

      @Test
      fun `will attempt to create a mapping between the DPS and NOMIS record once`() {
        mappingApiMock.verify(
          1,
          postRequestedFor(urlPathEqualTo("/mapping/corporate/address"))
            .withRequestBodyJsonPath("mappingType", "NOMIS_CREATED")
            .withRequestBodyJsonPath("dpsId", "$dpsOrganisationAddressId")
            .withRequestBodyJsonPath("nomisId", "$nomisAddressId"),
        )
      }

      @Test
      fun `will track telemetry for both overall success and duplicate`() {
        verify(telemetryClient).trackEvent(
          eq("organisations-address-synchronisation-created-success"),
          check {
            assertThat(it["nomisCorporateId"]).isEqualTo("$corporateAndOrganisationId")
            assertThat(it["dpsOrganisationId"]).isEqualTo("$corporateAndOrganisationId")
          },
          isNull(),
        )
        verify(telemetryClient).trackEvent(
          eq("from-nomis-sync-organisations-duplicate"),
          check {
            assertThat(it["existingNomisId"]).isEqualTo("$nomisAddressId")
            assertThat(it["existingDpsId"]).isEqualTo("9999")
            assertThat(it["duplicateNomisId"]).isEqualTo("$nomisAddressId")
            assertThat(it["duplicateDpsId"]).isEqualTo("$dpsOrganisationAddressId")
            assertThat(it["type"]).isEqualTo("ADDRESS")
          },
          isNull(),
        )
      }
    }

    @Nested
    inner class MappingCreateFails {
      @BeforeEach
      fun setUp() {
        mappingApiMock.stubGetByNomisCorporateIdOrNull(nomisCorporateId = corporateAndOrganisationId, mapping = null)
        nomisApiMock.stubGetCorporateOrganisation(
          corporate = corporateOrganisation(corporateAndOrganisationId).withAddress(corporateAddress().copy(id = nomisAddressId)),
        )
        dpsApiMock.stubCreateOrganisationAddress(syncAddressResponse().copy(organisationAddressId = dpsOrganisationAddressId))
        mappingApiMock.stubCreateAddressMappingFailureFollowedBySuccess()
        organisationsOffenderEventsQueue.sendMessage(
          corporateAddressEvent(
            eventType = "ADDRESSES_CORPORATE-INSERTED",
            addressId = nomisAddressId,
            corporateId = corporateAndOrganisationId,
          ),
        ).also { waitForAnyProcessingToComplete("organisations-address-mapping-synchronisation-created") }
      }

      @Test
      fun `will create the organisation in DPS once`() {
        dpsApiMock.verify(1, postRequestedFor(urlPathEqualTo("/sync/organisation-address")))
      }

      @Test
      fun `will create a mapping between the DPS and NOMIS record`() {
        mappingApiMock.verify(
          2,
          postRequestedFor(urlPathEqualTo("/mapping/corporate/address"))
            .withRequestBodyJsonPath("mappingType", "NOMIS_CREATED")
            .withRequestBodyJsonPath("dpsId", "$dpsOrganisationAddressId")
            .withRequestBodyJsonPath("nomisId", "$nomisAddressId"),
        )
      }

      @Test
      fun `will track telemetry`() {
        verify(telemetryClient).trackEvent(
          eq("organisations-address-synchronisation-created-success"),
          check {
            assertThat(it["nomisCorporateId"]).isEqualTo("$corporateAndOrganisationId")
            assertThat(it["dpsOrganisationId"]).isEqualTo("$corporateAndOrganisationId")
            assertThat(it["nomisAddressId"]).isEqualTo("$nomisAddressId")
            assertThat(it["dpsOrganisationAddressId"]).isEqualTo("$dpsOrganisationAddressId")
          },
          isNull(),
        )
      }
    }
  }

  @Nested
  @DisplayName("ADDRESSES_CORPORATE-UPDATED")
  inner class CorporateAddressUpdated {
    private val corporateAndOrganisationId = 123456L
    private val nomisAddressId = 34567L
    private val dpsOrganisationAddressId = 76543L

    @Nested
    inner class WhenUpdatedInDpsByOrganisationSyncService {
      @BeforeEach
      fun setUp() {
        organisationsOffenderEventsQueue.sendMessage(
          corporateAddressEvent(
            eventType = "ADDRESSES_CORPORATE-UPDATED",
            corporateId = corporateAndOrganisationId,
            addressId = nomisAddressId,
            auditModuleName = "DPS_SYNCHRONISATION_ORGANISATION",
          ),
        ).also { waitForAnyProcessingToComplete() }
      }

      @Test
      fun `will not update the address in DPS`() {
        dpsApiMock.verify(0, putRequestedFor(urlPathMatching("/sync/organisation-address/.*")))
      }

      @Test
      fun `will track telemetry`() {
        verify(telemetryClient).trackEvent(
          eq("organisations-address-synchronisation-updated-skipped"),
          check {
            assertThat(it["nomisCorporateId"]).isEqualTo("$corporateAndOrganisationId")
            assertThat(it["dpsOrganisationId"]).isEqualTo("$corporateAndOrganisationId")
            assertThat(it["nomisAddressId"]).isEqualTo("$nomisAddressId")
          },
          isNull(),
        )
      }
    }

    @Nested
    inner class WhenUpdatedInDpsByAnotherSyncService {

      @BeforeEach
      fun setUp() {
        mappingApiMock.stubGetByNomisAddressId(
          nomisAddressId = nomisAddressId,
          mapping = OrganisationsMappingDto(dpsId = dpsOrganisationAddressId.toString(), nomisId = nomisAddressId, mappingType = MIGRATED),
        )
        nomisApiMock.stubGetCorporateOrganisation(
          corporateId = corporateAndOrganisationId,
          corporate = corporateOrganisation().withAddress(
            CorporateAddress(
              id = nomisAddressId,
              phoneNumbers = emptyList(),
              validatedPAF = false,
              primaryAddress = true,
              mailAddress = true,
              noFixedAddress = false,
              type = CodeDescription("HOME", "Home Address"),
              startDate = LocalDate.parse("2021-01-01"),
              endDate = LocalDate.parse("2025-01-01"),
              isServices = true,
              audit = NomisAudit(
                createUsername = "J.SPEAK",
                createDatetime = LocalDateTime.parse("2024-09-01T13:31"),
                modifyUserId = "T.SMITH",
                modifyDatetime = LocalDateTime.parse("2024-10-01T13:31"),
              ),
            ),
          ),
        )
        dpsApiMock.stubUpdateOrganisationAddress(organisationAddressId = dpsOrganisationAddressId)

        organisationsOffenderEventsQueue.sendMessage(
          corporateAddressEvent(
            eventType = "ADDRESSES_CORPORATE-UPDATED",
            corporateId = corporateAndOrganisationId,
            addressId = nomisAddressId,
            auditModuleName = "DPS_SYNCHRONISATION",
          ),
        ).also { waitForAnyProcessingToComplete() }
      }

      @Test
      fun `will track telemetry`() {
        verify(telemetryClient).trackEvent(
          eq("organisations-address-synchronisation-updated-success"),
          check {
            assertThat(it["nomisCorporateId"]).isEqualTo("$corporateAndOrganisationId")
            assertThat(it["dpsOrganisationId"]).isEqualTo("$corporateAndOrganisationId")
            assertThat(it["nomisAddressId"]).isEqualTo("$nomisAddressId")
            assertThat(it["dpsOrganisationAddressId"]).isEqualTo("$dpsOrganisationAddressId")
          },
          isNull(),
        )
      }

      @Test
      fun `will update the address in DPS from the NOMIS address`() {
        dpsApiMock.verify(putRequestedFor(urlPathEqualTo("/sync/organisation-address/$dpsOrganisationAddressId")))
      }
    }

    @Nested
    inner class WhenUpdatedInNomis {

      @BeforeEach
      fun setUp() {
        mappingApiMock.stubGetByNomisAddressId(
          nomisAddressId = nomisAddressId,
          mapping = OrganisationsMappingDto(dpsId = dpsOrganisationAddressId.toString(), nomisId = nomisAddressId, mappingType = MIGRATED),
        )
        nomisApiMock.stubGetCorporateOrganisation(
          corporateId = corporateAndOrganisationId,
          corporate = corporateOrganisation().withAddress(
            CorporateAddress(
              id = nomisAddressId,
              phoneNumbers = emptyList(),
              comment = "nice area",
              validatedPAF = false,
              primaryAddress = true,
              mailAddress = true,
              noFixedAddress = false,
              type = CodeDescription("HOME", "Home Address"),
              flat = "Flat 1",
              premise = "Brown Court",
              locality = "Broomhill",
              street = "Broomhill Street",
              postcode = "S1 6GG",
              city = CodeDescription("12345", "Sheffield"),
              county = CodeDescription("S.YORKSHIRE", "South Yorkshire"),
              country = CodeDescription("GBR", "United Kingdom"),
              startDate = LocalDate.parse("2021-01-01"),
              endDate = LocalDate.parse("2025-01-01"),
              isServices = true,
              contactPersonName = "Bob Brown",
              businessHours = "10am to 10pm Monday to Friday",
              audit = NomisAudit(
                createUsername = "J.SPEAK",
                createDatetime = LocalDateTime.parse("2024-09-01T13:31"),
                modifyUserId = "T.SMITH",
                modifyDatetime = LocalDateTime.parse("2024-10-01T13:31"),
              ),
            ),
          ),
        )
        dpsApiMock.stubUpdateOrganisationAddress(organisationAddressId = dpsOrganisationAddressId)

        organisationsOffenderEventsQueue.sendMessage(
          corporateAddressEvent(
            eventType = "ADDRESSES_CORPORATE-UPDATED",
            corporateId = corporateAndOrganisationId,
            addressId = nomisAddressId,
          ),
        ).also { waitForAnyProcessingToComplete() }
      }

      @Test
      fun `will track telemetry`() {
        verify(telemetryClient).trackEvent(
          eq("organisations-address-synchronisation-updated-success"),
          check {
            assertThat(it["nomisCorporateId"]).isEqualTo("$corporateAndOrganisationId")
            assertThat(it["dpsOrganisationId"]).isEqualTo("$corporateAndOrganisationId")
            assertThat(it["nomisAddressId"]).isEqualTo("$nomisAddressId")
            assertThat(it["dpsOrganisationAddressId"]).isEqualTo("$dpsOrganisationAddressId")
          },
          isNull(),
        )
      }

      @Test
      fun `will update the address in DPS from the NOMIS address`() {
        dpsApiMock.verify(putRequestedFor(urlPathEqualTo("/sync/organisation-address/$dpsOrganisationAddressId")))
        val request: SyncUpdateAddressRequest = OrganisationsDpsApiExtension.getRequestBody(putRequestedFor(urlPathEqualTo("/sync/organisation-address/$dpsOrganisationAddressId")))
        with(request) {
          assertThat(addressType).isEqualTo("HOME")
          assertThat(primaryAddress).isTrue()
          assertThat(flat).isEqualTo("Flat 1")
          assertThat(property).isEqualTo("Brown Court")
          assertThat(street).isEqualTo("Broomhill Street")
          assertThat(area).isEqualTo("Broomhill")
          assertThat(cityCode).isEqualTo("12345")
          assertThat(countyCode).isEqualTo("S.YORKSHIRE")
          assertThat(countryCode).isEqualTo("GBR")
          assertThat(postcode).isEqualTo("S1 6GG")
          assertThat(mailAddress).isTrue()
          assertThat(startDate).isEqualTo(LocalDate.parse("2021-01-01"))
          assertThat(endDate).isEqualTo(LocalDate.parse("2025-01-01"))
          assertThat(noFixedAddress).isFalse()
          assertThat(comments).isEqualTo("nice area")
          assertThat(serviceAddress).isTrue()
          assertThat(businessHours).isEqualTo("10am to 10pm Monday to Friday")
          assertThat(contactPersonName).isEqualTo("Bob Brown")
          assertThat(updatedBy).isEqualTo("T.SMITH")
          assertThat(updatedTime).isEqualTo("2024-10-01T13:31")
        }
      }
    }
  }

  @Nested
  @DisplayName("ADDRESSES_CORPORATE-DELETED")
  inner class CorporateAddressDeleted {
    private val corporateAndOrganisationId = 123456L
    private val nomisAddressId = 34567L
    private val dpsOrganisationAddressId = 76543L

    @Nested
    inner class WhenMappingExists {

      @BeforeEach
      fun setUp() {
        mappingApiMock.stubGetByNomisAddressIdOrNull(
          nomisAddressId = nomisAddressId,
          mapping = OrganisationsMappingDto(dpsId = dpsOrganisationAddressId.toString(), nomisId = nomisAddressId, mappingType = MIGRATED),
        )
        dpsApiMock.stubDeleteOrganisationAddress(organisationAddressId = dpsOrganisationAddressId)
        mappingApiMock.stubDeleteByNomisAddressId(nomisAddressId)

        organisationsOffenderEventsQueue.sendMessage(
          corporateAddressEvent(
            eventType = "ADDRESSES_CORPORATE-DELETED",
            corporateId = corporateAndOrganisationId,
            addressId = nomisAddressId,
            auditModuleName = "DPS_SYNCHRONISATION",
          ),
        ).also { waitForAnyProcessingToComplete() }
      }

      @Test
      fun `will track telemetry`() {
        verify(telemetryClient).trackEvent(
          eq("organisations-address-synchronisation-deleted-success"),
          check {
            assertThat(it["nomisCorporateId"]).isEqualTo("$corporateAndOrganisationId")
            assertThat(it["dpsOrganisationId"]).isEqualTo("$corporateAndOrganisationId")
            assertThat(it["nomisAddressId"]).isEqualTo("$nomisAddressId")
            assertThat(it["dpsOrganisationAddressId"]).isEqualTo("$dpsOrganisationAddressId")
          },
          isNull(),
        )
      }

      @Test
      fun `will delete the organisation address from DPS`() {
        dpsApiMock.verify(deleteRequestedFor(urlPathEqualTo("/sync/organisation-address/$dpsOrganisationAddressId")))
      }

      @Test
      fun `will delete the address  mapping`() {
        mappingApi.verify(deleteRequestedFor(urlPathEqualTo("/mapping/corporate/address/nomis-address-id/$nomisAddressId")))
      }
    }

    @Nested
    inner class WhenMappingDoesNotExist {
      @BeforeEach
      fun setUp() {
        mappingApiMock.stubGetByNomisAddressIdOrNull(
          nomisAddressId = nomisAddressId,
          mapping = null,
        )

        organisationsOffenderEventsQueue.sendMessage(
          corporateAddressEvent(
            eventType = "ADDRESSES_CORPORATE-DELETED",
            corporateId = corporateAndOrganisationId,
            addressId = nomisAddressId,
            auditModuleName = "DPS_SYNCHRONISATION",
          ),
        ).also { waitForAnyProcessingToComplete() }
      }

      @Test
      fun `will track telemetry for delete ignored`() {
        verify(telemetryClient).trackEvent(
          eq("organisations-address-synchronisation-deleted-ignored"),
          check {
            assertThat(it["nomisCorporateId"]).isEqualTo("$corporateAndOrganisationId")
            assertThat(it["dpsOrganisationId"]).isEqualTo("$corporateAndOrganisationId")
            assertThat(it["nomisAddressId"]).isEqualTo("$nomisAddressId")
          },
          isNull(),
        )
      }
    }
  }

  @Nested
  @DisplayName("PHONES_CORPORATE-INSERTED (global)")
  inner class CorporatePhoneInserted {
    private val corporateAndOrganisationId = 123456L
    private val nomisPhoneId = 34567L
    private val dpsOrganisationPhoneId = 76543L

    @Nested
    inner class WhenCreatedInDpsByOrganisationSyncService {
      @BeforeEach
      fun setUp() {
        organisationsOffenderEventsQueue.sendMessage(
          corporatePhoneEvent(
            eventType = "PHONES_CORPORATE-INSERTED",
            corporateId = corporateAndOrganisationId,
            phoneId = nomisPhoneId,
            auditModuleName = "DPS_SYNCHRONISATION_ORGANISATION",
          ),
        ).also { waitForAnyProcessingToComplete() }
      }

      @Test
      fun `will not create phone in DPS`() {
        dpsApiMock.verify(0, postRequestedFor(urlPathEqualTo("/sync/organisation-phone")))
      }

      @Test
      fun `will track telemetry`() {
        verify(telemetryClient).trackEvent(
          eq("organisations-phone-synchronisation-created-skipped"),
          check {
            assertThat(it["nomisCorporateId"]).isEqualTo("$corporateAndOrganisationId")
            assertThat(it["dpsOrganisationId"]).isEqualTo("$corporateAndOrganisationId")
            assertThat(it["nomisPhoneId"]).isEqualTo("$nomisPhoneId")
          },
          isNull(),
        )
      }
    }

    @Nested
    inner class WhenCreatedInNomis {
      @BeforeEach
      fun setUp() {
        mappingApiMock.stubGetByNomisPhoneIdOrNull(nomisPhoneId = nomisPhoneId, mapping = null)
        nomisApiMock.stubGetCorporateOrganisation(
          corporate = corporateOrganisation().withPhone(
            CorporatePhoneNumber(
              id = nomisPhoneId,
              number = "0114 555 5555",
              extension = "ext 123",
              type = CodeDescription("HOME", "Home Phone"),
              audit = NomisAudit(
                createUsername = "J.SPEAK",
                createDatetime = LocalDateTime.parse("2024-09-01T13:31"),
                modifyUserId = "T.SMITH",
                modifyDatetime = LocalDateTime.parse("2024-10-01T13:31"),
              ),
            ),
          ),
        )
        dpsApiMock.stubCreateOrganisationPhone(syncPhoneResponse().copy(organisationPhoneId = dpsOrganisationPhoneId))
        mappingApiMock.stubCreatePhoneMapping()
        organisationsOffenderEventsQueue.sendMessage(
          corporatePhoneEvent(
            eventType = "PHONES_CORPORATE-INSERTED",
            phoneId = nomisPhoneId,
            corporateId = corporateAndOrganisationId,
          ),
        ).also { waitForAnyProcessingToComplete() }
      }

      @Test
      fun `will check if mapping already exists for phone`() {
        mappingApi.verify(getRequestedFor(urlPathEqualTo("/mapping/corporate/phone/nomis-phone-id/$nomisPhoneId")))
      }

      @Test
      fun `will retrieve the organisation details from NOMIS`() {
        nomisApiMock.verify(getRequestedFor(urlPathEqualTo("/corporates/$corporateAndOrganisationId")))
      }

      @Test
      fun `will create the organisation phone in DPS from the organisation`() {
        dpsApiMock.verify(postRequestedFor(urlPathEqualTo("/sync/organisation-phone")))
        val request: SyncCreatePhoneRequest = OrganisationsDpsApiExtension.getRequestBody(
          postRequestedFor(urlPathEqualTo("/sync/organisation-phone")),
        )
        with(request) {
          assertThat(organisationId).isEqualTo(corporateAndOrganisationId)
          assertThat(phoneType).isEqualTo("HOME")
          assertThat(phoneNumber).isEqualTo("0114 555 5555")
          assertThat(extNumber).isEqualTo("ext 123")
          assertThat(createdBy).isEqualTo("J.SPEAK")
          assertThat(createdTime).isEqualTo("2024-09-01T13:31")
        }
      }

      @Test
      fun `will create a mapping between the DPS and NOMIS record`() {
        mappingApiMock.verify(
          postRequestedFor(urlPathEqualTo("/mapping/corporate/phone"))
            .withRequestBodyJsonPath("mappingType", "NOMIS_CREATED")
            .withRequestBodyJsonPath("dpsId", "$dpsOrganisationPhoneId")
            .withRequestBodyJsonPath("nomisId", "$nomisPhoneId"),
        )
      }

      @Test
      fun `will track telemetry`() {
        verify(telemetryClient).trackEvent(
          eq("organisations-phone-synchronisation-created-success"),
          check {
            assertThat(it["nomisCorporateId"]).isEqualTo("$corporateAndOrganisationId")
            assertThat(it["dpsOrganisationId"]).isEqualTo("$corporateAndOrganisationId")
            assertThat(it["nomisPhoneId"]).isEqualTo("$nomisPhoneId")
            assertThat(it["dpsOrganisationPhoneId"]).isEqualTo("$dpsOrganisationPhoneId")
          },
          isNull(),
        )
      }
    }

    @Nested
    inner class WhenAlreadyCreated {
      @BeforeEach
      fun setUp() {
        mappingApiMock.stubGetByNomisPhoneIdOrNull(nomisPhoneId = nomisPhoneId, mapping = OrganisationsMappingDto(dpsId = "$dpsOrganisationPhoneId", nomisId = nomisPhoneId, mappingType = NOMIS_CREATED))
        organisationsOffenderEventsQueue.sendMessage(
          corporatePhoneEvent(
            eventType = "PHONES_CORPORATE-INSERTED",
            phoneId = nomisPhoneId,
            corporateId = corporateAndOrganisationId,
          ),
        ).also { waitForAnyProcessingToComplete() }
      }

      @Test
      fun `will not create organisation in DPS`() {
        dpsApiMock.verify(0, postRequestedFor(urlPathEqualTo("/sync/organisation-phone")))
      }

      @Test
      fun `will track telemetry`() {
        verify(telemetryClient).trackEvent(
          eq("organisations-phone-synchronisation-created-ignored"),
          check {
            assertThat(it["nomisCorporateId"]).isEqualTo("$corporateAndOrganisationId")
            assertThat(it["dpsOrganisationId"]).isEqualTo("$corporateAndOrganisationId")
            assertThat(it["nomisPhoneId"]).isEqualTo("$nomisPhoneId")
            assertThat(it["dpsOrganisationPhoneId"]).isEqualTo("$dpsOrganisationPhoneId")
          },
          isNull(),
        )
      }
    }

    @Nested
    inner class WhenDuplicateMapping {
      @BeforeEach
      fun setUp() {
        mappingApiMock.stubGetByNomisPhoneIdOrNull(nomisPhoneId = nomisPhoneId, mapping = null)
        nomisApiMock.stubGetCorporateOrganisation(
          corporate = corporateOrganisation(corporateAndOrganisationId).withPhone(corporatePhone().copy(id = nomisPhoneId)),
        )

        dpsApiMock.stubCreateOrganisationPhone(syncPhoneResponse().copy(organisationPhoneId = dpsOrganisationPhoneId))
        mappingApiMock.stubCreatePhoneMapping(
          error = DuplicateMappingErrorResponse(
            moreInfo = DuplicateErrorContentObject(
              duplicate = OrganisationsMappingDto(
                dpsId = "$dpsOrganisationPhoneId",
                nomisId = nomisPhoneId,
                mappingType = NOMIS_CREATED,
              ),
              existing = OrganisationsMappingDto(
                dpsId = "9999",
                nomisId = nomisPhoneId,
                mappingType = NOMIS_CREATED,
              ),
            ),
            errorCode = 1409,
            status = DuplicateMappingErrorResponse.Status._409_CONFLICT,
            userMessage = "Duplicate mapping",
          ),
        )

        organisationsOffenderEventsQueue.sendMessage(
          corporatePhoneEvent(
            eventType = "PHONES_CORPORATE-INSERTED",
            phoneId = nomisPhoneId,
            corporateId = corporateAndOrganisationId,
          ),
        ).also { waitForAnyProcessingToComplete("from-nomis-sync-organisations-duplicate") }
      }

      @Test
      fun `will create the organisation phone in DPS once`() {
        dpsApiMock.verify(1, postRequestedFor(urlPathEqualTo("/sync/organisation-phone")))
      }

      @Test
      fun `will attempt to create a mapping between the DPS and NOMIS record once`() {
        mappingApiMock.verify(
          1,
          postRequestedFor(urlPathEqualTo("/mapping/corporate/phone"))
            .withRequestBodyJsonPath("mappingType", "NOMIS_CREATED")
            .withRequestBodyJsonPath("dpsId", "$dpsOrganisationPhoneId")
            .withRequestBodyJsonPath("nomisId", "$nomisPhoneId"),
        )
      }

      @Test
      fun `will track telemetry for both overall success and duplicate`() {
        verify(telemetryClient).trackEvent(
          eq("organisations-phone-synchronisation-created-success"),
          check {
            assertThat(it["nomisCorporateId"]).isEqualTo("$corporateAndOrganisationId")
            assertThat(it["dpsOrganisationId"]).isEqualTo("$corporateAndOrganisationId")
          },
          isNull(),
        )
        verify(telemetryClient).trackEvent(
          eq("from-nomis-sync-organisations-duplicate"),
          check {
            assertThat(it["existingNomisId"]).isEqualTo("$nomisPhoneId")
            assertThat(it["existingDpsId"]).isEqualTo("9999")
            assertThat(it["duplicateNomisId"]).isEqualTo("$nomisPhoneId")
            assertThat(it["duplicateDpsId"]).isEqualTo("$dpsOrganisationPhoneId")
            assertThat(it["type"]).isEqualTo("PHONE")
          },
          isNull(),
        )
      }
    }

    @Nested
    inner class MappingCreateFails {
      @BeforeEach
      fun setUp() {
        mappingApiMock.stubGetByNomisCorporateIdOrNull(nomisCorporateId = corporateAndOrganisationId, mapping = null)
        nomisApiMock.stubGetCorporateOrganisation(
          corporate = corporateOrganisation(corporateAndOrganisationId).withPhone(corporatePhone().copy(id = nomisPhoneId)),
        )
        dpsApiMock.stubCreateOrganisationPhone(syncPhoneResponse().copy(organisationPhoneId = dpsOrganisationPhoneId))
        mappingApiMock.stubCreatePhoneMappingFailureFollowedBySuccess()
        organisationsOffenderEventsQueue.sendMessage(
          corporatePhoneEvent(
            eventType = "PHONES_CORPORATE-INSERTED",
            phoneId = nomisPhoneId,
            corporateId = corporateAndOrganisationId,
          ),
        ).also { waitForAnyProcessingToComplete("organisations-phone-mapping-synchronisation-created") }
      }

      @Test
      fun `will create the organisation in DPS once`() {
        dpsApiMock.verify(1, postRequestedFor(urlPathEqualTo("/sync/organisation-phone")))
      }

      @Test
      fun `will create a mapping between the DPS and NOMIS record`() {
        mappingApiMock.verify(
          2,
          postRequestedFor(urlPathEqualTo("/mapping/corporate/phone"))
            .withRequestBodyJsonPath("mappingType", "NOMIS_CREATED")
            .withRequestBodyJsonPath("dpsId", "$dpsOrganisationPhoneId")
            .withRequestBodyJsonPath("nomisId", "$nomisPhoneId"),
        )
      }

      @Test
      fun `will track telemetry`() {
        verify(telemetryClient).trackEvent(
          eq("organisations-phone-synchronisation-created-success"),
          check {
            assertThat(it["nomisCorporateId"]).isEqualTo("$corporateAndOrganisationId")
            assertThat(it["dpsOrganisationId"]).isEqualTo("$corporateAndOrganisationId")
            assertThat(it["nomisPhoneId"]).isEqualTo("$nomisPhoneId")
            assertThat(it["dpsOrganisationPhoneId"]).isEqualTo("$dpsOrganisationPhoneId")
          },
          isNull(),
        )
      }
    }
  }

  @Nested
  @DisplayName("PHONES_CORPORATE-UPDATED (global)")
  inner class CorporatePhoneUpdated {
    private val corporateAndOrganisationId = 123456L
    private val nomisPhoneId = 34567L
    private val dpsOrganisationPhoneId = 76543L

    @Nested
    inner class WhenUpdatedInDpsByOrganisationSyncService {
      @BeforeEach
      fun setUp() {
        organisationsOffenderEventsQueue.sendMessage(
          corporatePhoneEvent(
            eventType = "PHONES_CORPORATE-UPDATED",
            corporateId = corporateAndOrganisationId,
            phoneId = nomisPhoneId,
            auditModuleName = "DPS_SYNCHRONISATION_ORGANISATION",
          ),
        ).also { waitForAnyProcessingToComplete() }
      }

      @Test
      fun `will not update the phone in DPS`() {
        dpsApiMock.verify(0, putRequestedFor(urlPathMatching("/sync/organisation-phone/.*")))
      }

      @Test
      fun `will track telemetry`() {
        verify(telemetryClient).trackEvent(
          eq("organisations-phone-synchronisation-updated-skipped"),
          check {
            assertThat(it["nomisCorporateId"]).isEqualTo("$corporateAndOrganisationId")
            assertThat(it["dpsOrganisationId"]).isEqualTo("$corporateAndOrganisationId")
            assertThat(it["nomisPhoneId"]).isEqualTo("$nomisPhoneId")
          },
          isNull(),
        )
      }
    }

    @Nested
    inner class WhenUpdatedInNomis {

      @BeforeEach
      fun setUp() {
        mappingApiMock.stubGetByNomisPhoneId(
          nomisPhoneId = nomisPhoneId,
          mapping = OrganisationsMappingDto(dpsId = dpsOrganisationPhoneId.toString(), nomisId = nomisPhoneId, mappingType = MIGRATED),
        )
        nomisApiMock.stubGetCorporateOrganisation(
          corporateId = corporateAndOrganisationId,
          corporate = corporateOrganisation().withPhone(
            CorporatePhoneNumber(
              id = nomisPhoneId,
              number = "0114 555 5555",
              extension = "ext 123",
              type = CodeDescription("HOME", "Home Phone"),
              audit = NomisAudit(
                createUsername = "J.SPEAK",
                createDatetime = LocalDateTime.parse("2024-09-01T13:31"),
                modifyUserId = "T.SMITH",
                modifyDatetime = LocalDateTime.parse("2024-10-01T13:31"),
              ),
            ),
          ),
        )
        dpsApiMock.stubUpdateOrganisationPhone(organisationPhoneId = dpsOrganisationPhoneId)

        organisationsOffenderEventsQueue.sendMessage(
          corporatePhoneEvent(
            eventType = "PHONES_CORPORATE-UPDATED",
            corporateId = corporateAndOrganisationId,
            phoneId = nomisPhoneId,
          ),
        ).also { waitForAnyProcessingToComplete() }
      }

      @Test
      fun `will track telemetry`() {
        verify(telemetryClient).trackEvent(
          eq("organisations-phone-synchronisation-updated-success"),
          check {
            assertThat(it["nomisCorporateId"]).isEqualTo("$corporateAndOrganisationId")
            assertThat(it["dpsOrganisationId"]).isEqualTo("$corporateAndOrganisationId")
            assertThat(it["nomisPhoneId"]).isEqualTo("$nomisPhoneId")
            assertThat(it["dpsOrganisationPhoneId"]).isEqualTo("$dpsOrganisationPhoneId")
          },
          isNull(),
        )
      }

      @Test
      fun `will update the phone in DPS from the NOMIS phone`() {
        dpsApiMock.verify(putRequestedFor(urlPathEqualTo("/sync/organisation-phone/$dpsOrganisationPhoneId")))
        val request: SyncUpdatePhoneRequest = OrganisationsDpsApiExtension.getRequestBody(putRequestedFor(urlPathEqualTo("/sync/organisation-phone/$dpsOrganisationPhoneId")))
        with(request) {
          assertThat(phoneType).isEqualTo("HOME")
          assertThat(extNumber).isEqualTo("ext 123")
          assertThat(phoneNumber).isEqualTo("0114 555 5555")
          assertThat(updatedBy).isEqualTo("T.SMITH")
          assertThat(updatedTime).isEqualTo("2024-10-01T13:31")
        }
      }
    }
  }

  @Nested
  @DisplayName("PHONES_CORPORATE-DELETED (global)")
  inner class CorporatePhoneDeleted {
    private val corporateAndOrganisationId = 123456L
    private val nomisPhoneId = 34567L
    private val dpsOrganisationPhoneId = 76543L

    @Nested
    inner class WhenMappingExists {

      @BeforeEach
      fun setUp() {
        mappingApiMock.stubGetByNomisPhoneIdOrNull(
          nomisPhoneId = nomisPhoneId,
          mapping = OrganisationsMappingDto(dpsId = dpsOrganisationPhoneId.toString(), nomisId = nomisPhoneId, mappingType = MIGRATED),
        )
        dpsApiMock.stubDeleteOrganisationPhone(organisationPhoneId = dpsOrganisationPhoneId)
        mappingApiMock.stubDeleteByNomisPhoneId(nomisPhoneId)

        organisationsOffenderEventsQueue.sendMessage(
          corporatePhoneEvent(
            eventType = "PHONES_CORPORATE-DELETED",
            corporateId = corporateAndOrganisationId,
            phoneId = nomisPhoneId,
            auditModuleName = "DPS_SYNCHRONISATION_ORGANISATION",
          ),
        ).also { waitForAnyProcessingToComplete() }
      }

      @Test
      fun `will track telemetry`() {
        verify(telemetryClient).trackEvent(
          eq("organisations-phone-synchronisation-deleted-success"),
          check {
            assertThat(it["nomisCorporateId"]).isEqualTo("$corporateAndOrganisationId")
            assertThat(it["dpsOrganisationId"]).isEqualTo("$corporateAndOrganisationId")
            assertThat(it["nomisPhoneId"]).isEqualTo("$nomisPhoneId")
            assertThat(it["dpsOrganisationPhoneId"]).isEqualTo("$dpsOrganisationPhoneId")
          },
          isNull(),
        )
      }

      @Test
      fun `will delete the organisation phone from DPS`() {
        dpsApiMock.verify(deleteRequestedFor(urlPathEqualTo("/sync/organisation-phone/$dpsOrganisationPhoneId")))
      }

      @Test
      fun `will delete the phone mapping`() {
        mappingApi.verify(deleteRequestedFor(urlPathEqualTo("/mapping/corporate/phone/nomis-phone-id/$nomisPhoneId")))
      }
    }

    @Nested
    inner class WhenMappingDoesNotExist {
      @BeforeEach
      fun setUp() {
        mappingApiMock.stubGetByNomisPhoneIdOrNull(
          nomisPhoneId = nomisPhoneId,
          mapping = null,
        )

        organisationsOffenderEventsQueue.sendMessage(
          corporatePhoneEvent(
            eventType = "PHONES_CORPORATE-DELETED",
            corporateId = corporateAndOrganisationId,
            phoneId = nomisPhoneId,
            auditModuleName = "DPS_SYNCHRONISATION",
          ),
        ).also { waitForAnyProcessingToComplete() }
      }

      @Test
      fun `will track telemetry for delete ignored`() {
        verify(telemetryClient).trackEvent(
          eq("organisations-phone-synchronisation-deleted-ignored"),
          check {
            assertThat(it["nomisCorporateId"]).isEqualTo("$corporateAndOrganisationId")
            assertThat(it["dpsOrganisationId"]).isEqualTo("$corporateAndOrganisationId")
            assertThat(it["nomisPhoneId"]).isEqualTo("$nomisPhoneId")
          },
          isNull(),
        )
      }
    }
  }

  @Nested
  @DisplayName("PHONES_CORPORATE-INSERTED (address)")
  inner class CorporateAddressPhoneInserted {
    private val corporateAndOrganisationId = 123456L
    private val nomisPhoneId = 34567L
    private val nomisAddressId = 6789L
    private val dpsOrganisationAddressId = 9876L
    private val dpsOrganisationAddressPhoneId = 76543L

    @Nested
    inner class WhenCreatedInDpsByOrganisationSyncService {
      @BeforeEach
      fun setUp() {
        organisationsOffenderEventsQueue.sendMessage(
          corporateAddressPhoneEvent(
            eventType = "PHONES_CORPORATE-INSERTED",
            corporateId = corporateAndOrganisationId,
            phoneId = nomisPhoneId,
            addressId = nomisAddressId,
            auditModuleName = "DPS_SYNCHRONISATION_ORGANISATION",
          ),
        ).also { waitForAnyProcessingToComplete() }
      }

      @Test
      fun `will not create phone in DPS`() {
        dpsApiMock.verify(0, postRequestedFor(urlPathEqualTo("/sync/organisation-address-phone")))
      }

      @Test
      fun `will track telemetry`() {
        verify(telemetryClient).trackEvent(
          eq("organisations-address-phone-synchronisation-created-skipped"),
          check {
            assertThat(it["nomisCorporateId"]).isEqualTo("$corporateAndOrganisationId")
            assertThat(it["dpsOrganisationId"]).isEqualTo("$corporateAndOrganisationId")
            assertThat(it["nomisPhoneId"]).isEqualTo("$nomisPhoneId")
          },
          isNull(),
        )
      }
    }

    @Nested
    inner class WhenCreatedInNomis {
      @BeforeEach
      fun setUp() {
        mappingApiMock.stubGetByNomisPhoneIdOrNull(nomisPhoneId = nomisPhoneId, mapping = null)
        mappingApiMock.stubGetByNomisAddressId(
          nomisAddressId = nomisAddressId,
          mapping = OrganisationsMappingDto(
            dpsId = "$dpsOrganisationAddressId",
            nomisId = nomisAddressId,
            mappingType = MIGRATED,
          ),
        )
        nomisApiMock.stubGetCorporateOrganisation(
          corporate = corporateOrganisation().withAddress(
            corporateAddress().withPhone(
              CorporatePhoneNumber(
                id = nomisPhoneId,
                number = "0114 555 5555",
                extension = "ext 123",
                type = CodeDescription("HOME", "Home Phone"),
                audit = NomisAudit(
                  createUsername = "J.SPEAK",
                  createDatetime = LocalDateTime.parse("2024-09-01T13:31"),
                  modifyUserId = "T.SMITH",
                  modifyDatetime = LocalDateTime.parse("2024-10-01T13:31"),
                ),
              ),
            ).copy(id = nomisAddressId),
          ),
        )
        dpsApiMock.stubCreateOrganisationAddressPhone(syncCreateAddressPhoneResponse().copy(organisationAddressPhoneId = dpsOrganisationAddressPhoneId))
        mappingApiMock.stubCreateAddressPhoneMapping()
        organisationsOffenderEventsQueue.sendMessage(
          corporateAddressPhoneEvent(
            eventType = "PHONES_CORPORATE-INSERTED",
            phoneId = nomisPhoneId,
            addressId = nomisAddressId,
            corporateId = corporateAndOrganisationId,
          ),
        ).also { waitForAnyProcessingToComplete() }
      }

      @Test
      fun `will check if mapping already exists for phone`() {
        mappingApi.verify(getRequestedFor(urlPathEqualTo("/mapping/corporate/address-phone/nomis-phone-id/$nomisPhoneId")))
      }

      @Test
      fun `will retrieve the organisation details from NOMIS`() {
        nomisApiMock.verify(getRequestedFor(urlPathEqualTo("/corporates/$corporateAndOrganisationId")))
      }

      @Test
      fun `will create the organisation phone in DPS from the organisation`() {
        dpsApiMock.verify(postRequestedFor(urlPathEqualTo("/sync/organisation-address-phone")))
        val request: SyncCreateAddressPhoneRequest = OrganisationsDpsApiExtension.getRequestBody(
          postRequestedFor(urlPathEqualTo("/sync/organisation-address-phone")),
        )
        with(request) {
          assertThat(organisationAddressId).isEqualTo(dpsOrganisationAddressId)
          assertThat(phoneType).isEqualTo("HOME")
          assertThat(phoneNumber).isEqualTo("0114 555 5555")
          assertThat(extNumber).isEqualTo("ext 123")
          assertThat(createdBy).isEqualTo("J.SPEAK")
          assertThat(createdTime).isEqualTo("2024-09-01T13:31")
        }
      }

      @Test
      fun `will create a mapping between the DPS and NOMIS record`() {
        mappingApiMock.verify(
          postRequestedFor(urlPathEqualTo("/mapping/corporate/address-phone"))
            .withRequestBodyJsonPath("mappingType", "NOMIS_CREATED")
            .withRequestBodyJsonPath("dpsId", "$dpsOrganisationAddressPhoneId")
            .withRequestBodyJsonPath("nomisId", "$nomisPhoneId"),
        )
      }

      @Test
      fun `will track telemetry`() {
        verify(telemetryClient).trackEvent(
          eq("organisations-address-phone-synchronisation-created-success"),
          check {
            assertThat(it["nomisCorporateId"]).isEqualTo("$corporateAndOrganisationId")
            assertThat(it["dpsOrganisationId"]).isEqualTo("$corporateAndOrganisationId")
            assertThat(it["nomisPhoneId"]).isEqualTo("$nomisPhoneId")
            assertThat(it["nomisAddressId"]).isEqualTo("$nomisAddressId")
            assertThat(it["dpsOrganisationAddressPhoneId"]).isEqualTo("$dpsOrganisationAddressPhoneId")
            assertThat(it["dpsOrganisationAddressId"]).isEqualTo("$dpsOrganisationAddressId")
          },
          isNull(),
        )
      }
    }

    @Nested
    inner class WhenAlreadyCreated {
      @BeforeEach
      fun setUp() {
        mappingApiMock.stubGetByNomisAddressPhoneIdOrNull(nomisPhoneId = nomisPhoneId, mapping = OrganisationsMappingDto(dpsId = "$dpsOrganisationAddressPhoneId", nomisId = nomisPhoneId, mappingType = NOMIS_CREATED))
        organisationsOffenderEventsQueue.sendMessage(
          corporateAddressPhoneEvent(
            eventType = "PHONES_CORPORATE-INSERTED",
            phoneId = nomisPhoneId,
            addressId = nomisAddressId,
            corporateId = corporateAndOrganisationId,
          ),
        ).also { waitForAnyProcessingToComplete() }
      }

      @Test
      fun `will not create organisation in DPS`() {
        dpsApiMock.verify(0, postRequestedFor(urlPathEqualTo("/sync/organisation-address-phone")))
      }

      @Test
      fun `will track telemetry`() {
        verify(telemetryClient).trackEvent(
          eq("organisations-address-phone-synchronisation-created-ignored"),
          check {
            assertThat(it["nomisCorporateId"]).isEqualTo("$corporateAndOrganisationId")
            assertThat(it["dpsOrganisationId"]).isEqualTo("$corporateAndOrganisationId")
            assertThat(it["nomisPhoneId"]).isEqualTo("$nomisPhoneId")
            assertThat(it["dpsOrganisationAddressPhoneId"]).isEqualTo("$dpsOrganisationAddressPhoneId")
          },
          isNull(),
        )
      }
    }

    @Nested
    inner class WhenDuplicateMapping {
      @BeforeEach
      fun setUp() {
        mappingApiMock.stubGetByNomisPhoneIdOrNull(nomisPhoneId = nomisPhoneId, mapping = null)
        mappingApiMock.stubGetByNomisAddressId(
          nomisAddressId = nomisAddressId,
          mapping = OrganisationsMappingDto(
            dpsId = "$dpsOrganisationAddressId",
            nomisId = nomisAddressId,
            mappingType = MIGRATED,
          ),
        )
        nomisApiMock.stubGetCorporateOrganisation(
          corporate = corporateOrganisation().withAddress(
            corporateAddress().withPhone(
              corporatePhone().copy(id = nomisPhoneId),
            ).copy(id = nomisAddressId),
          ),
        )
        dpsApiMock.stubCreateOrganisationAddressPhone(syncCreateAddressPhoneResponse().copy(organisationAddressPhoneId = dpsOrganisationAddressPhoneId))
        mappingApiMock.stubCreateAddressPhoneMapping(
          error = DuplicateMappingErrorResponse(
            moreInfo = DuplicateErrorContentObject(
              duplicate = OrganisationsMappingDto(
                dpsId = "$dpsOrganisationAddressPhoneId",
                nomisId = nomisPhoneId,
                mappingType = NOMIS_CREATED,
              ),
              existing = OrganisationsMappingDto(
                dpsId = "9999",
                nomisId = nomisPhoneId,
                mappingType = NOMIS_CREATED,
              ),
            ),
            errorCode = 1409,
            status = DuplicateMappingErrorResponse.Status._409_CONFLICT,
            userMessage = "Duplicate mapping",
          ),
        )

        organisationsOffenderEventsQueue.sendMessage(
          corporateAddressPhoneEvent(
            eventType = "PHONES_CORPORATE-INSERTED",
            phoneId = nomisPhoneId,
            addressId = nomisAddressId,
            corporateId = corporateAndOrganisationId,
          ),
        ).also { waitForAnyProcessingToComplete("from-nomis-sync-organisations-duplicate") }
      }

      @Test
      fun `will create the organisation phone in DPS once`() {
        dpsApiMock.verify(1, postRequestedFor(urlPathEqualTo("/sync/organisation-address-phone")))
      }

      @Test
      fun `will attempt to create a mapping between the DPS and NOMIS record once`() {
        mappingApiMock.verify(
          1,
          postRequestedFor(urlPathEqualTo("/mapping/corporate/address-phone"))
            .withRequestBodyJsonPath("mappingType", "NOMIS_CREATED")
            .withRequestBodyJsonPath("dpsId", "$dpsOrganisationAddressPhoneId")
            .withRequestBodyJsonPath("nomisId", "$nomisPhoneId"),
        )
      }

      @Test
      fun `will track telemetry for both overall success and duplicate`() {
        verify(telemetryClient).trackEvent(
          eq("organisations-address-phone-synchronisation-created-success"),
          check {
            assertThat(it["nomisCorporateId"]).isEqualTo("$corporateAndOrganisationId")
            assertThat(it["dpsOrganisationId"]).isEqualTo("$corporateAndOrganisationId")
          },
          isNull(),
        )
        verify(telemetryClient).trackEvent(
          eq("from-nomis-sync-organisations-duplicate"),
          check {
            assertThat(it["existingNomisId"]).isEqualTo("$nomisPhoneId")
            assertThat(it["existingDpsId"]).isEqualTo("9999")
            assertThat(it["duplicateNomisId"]).isEqualTo("$nomisPhoneId")
            assertThat(it["duplicateDpsId"]).isEqualTo("$dpsOrganisationAddressPhoneId")
            assertThat(it["type"]).isEqualTo("ADDRESS_PHONE")
          },
          isNull(),
        )
      }
    }

    @Nested
    inner class MappingCreateFails {
      @BeforeEach
      fun setUp() {
        mappingApiMock.stubGetByNomisCorporateIdOrNull(nomisCorporateId = corporateAndOrganisationId, mapping = null)
        mappingApiMock.stubGetByNomisAddressId(
          nomisAddressId = nomisAddressId,
          mapping = OrganisationsMappingDto(
            dpsId = "$dpsOrganisationAddressId",
            nomisId = nomisAddressId,
            mappingType = MIGRATED,
          ),
        )
        nomisApiMock.stubGetCorporateOrganisation(
          corporate = corporateOrganisation().withAddress(
            corporateAddress().withPhone(
              corporatePhone().copy(id = nomisPhoneId),
            ).copy(id = nomisAddressId),
          ),
        )
        dpsApiMock.stubCreateOrganisationAddressPhone(syncCreateAddressPhoneResponse().copy(organisationAddressPhoneId = dpsOrganisationAddressPhoneId))
        mappingApiMock.stubCreateAddressPhoneMappingFailureFollowedBySuccess()
        organisationsOffenderEventsQueue.sendMessage(
          corporateAddressPhoneEvent(
            eventType = "PHONES_CORPORATE-INSERTED",
            phoneId = nomisPhoneId,
            addressId = nomisAddressId,
            corporateId = corporateAndOrganisationId,
          ),
        ).also { waitForAnyProcessingToComplete("organisations-address_phone-mapping-synchronisation-created") }
      }

      @Test
      fun `will create the organisation in DPS once`() {
        dpsApiMock.verify(1, postRequestedFor(urlPathEqualTo("/sync/organisation-address-phone")))
      }

      @Test
      fun `will create a mapping between the DPS and NOMIS record`() {
        mappingApiMock.verify(
          2,
          postRequestedFor(urlPathEqualTo("/mapping/corporate/address-phone"))
            .withRequestBodyJsonPath("mappingType", "NOMIS_CREATED")
            .withRequestBodyJsonPath("dpsId", "$dpsOrganisationAddressPhoneId")
            .withRequestBodyJsonPath("nomisId", "$nomisPhoneId"),
        )
      }

      @Test
      fun `will track telemetry`() {
        verify(telemetryClient).trackEvent(
          eq("organisations-address-phone-synchronisation-created-success"),
          check {
            assertThat(it["nomisCorporateId"]).isEqualTo("$corporateAndOrganisationId")
            assertThat(it["dpsOrganisationId"]).isEqualTo("$corporateAndOrganisationId")
            assertThat(it["nomisPhoneId"]).isEqualTo("$nomisPhoneId")
            assertThat(it["dpsOrganisationAddressPhoneId"]).isEqualTo("$dpsOrganisationAddressPhoneId")
            assertThat(it["nomisAddressId"]).isEqualTo("$nomisAddressId")
            assertThat(it["dpsOrganisationAddressId"]).isEqualTo("$dpsOrganisationAddressId")
          },
          isNull(),
        )
      }
    }
  }

  @Nested
  @DisplayName("PHONES_CORPORATE-UPDATED (address)")
  inner class CorporateAddressPhoneUpdated {
    private val corporateAndOrganisationId = 123456L
    private val nomisPhoneId = 34567L
    private val nomisAddressId = 6789L
    private val dpsOrganisationAddressPhoneId = 76543L

    @Nested
    inner class WhenUpdatedInDpsByOrganisationSyncService {
      @BeforeEach
      fun setUp() {
        organisationsOffenderEventsQueue.sendMessage(
          corporateAddressPhoneEvent(
            eventType = "PHONES_CORPORATE-UPDATED",
            corporateId = corporateAndOrganisationId,
            phoneId = nomisPhoneId,
            addressId = nomisAddressId,
            auditModuleName = "DPS_SYNCHRONISATION_ORGANISATION",
          ),
        ).also { waitForAnyProcessingToComplete() }
      }

      @Test
      fun `will not update the phone in DPS`() {
        dpsApiMock.verify(0, putRequestedFor(urlPathMatching("/sync/organisation-address-phone/.*")))
      }

      @Test
      fun `will track telemetry`() {
        verify(telemetryClient).trackEvent(
          eq("organisations-address-phone-synchronisation-updated-skipped"),
          check {
            assertThat(it["nomisCorporateId"]).isEqualTo("$corporateAndOrganisationId")
            assertThat(it["dpsOrganisationId"]).isEqualTo("$corporateAndOrganisationId")
            assertThat(it["nomisPhoneId"]).isEqualTo("$nomisPhoneId")
          },
          isNull(),
        )
      }
    }

    @Nested
    inner class WhenUpdatedInNomis {

      @BeforeEach
      fun setUp() {
        mappingApiMock.stubGetByNomisAddressPhoneId(
          nomisPhoneId = nomisPhoneId,
          mapping = OrganisationsMappingDto(dpsId = dpsOrganisationAddressPhoneId.toString(), nomisId = nomisPhoneId, mappingType = MIGRATED),
        )
        nomisApiMock.stubGetCorporateOrganisation(
          corporate = corporateOrganisation().withAddress(
            corporateAddress().withPhone(
              CorporatePhoneNumber(
                id = nomisPhoneId,
                number = "0114 555 5555",
                extension = "ext 123",
                type = CodeDescription("HOME", "Home Phone"),
                audit = NomisAudit(
                  createUsername = "J.SPEAK",
                  createDatetime = LocalDateTime.parse("2024-09-01T13:31"),
                  modifyUserId = "T.SMITH",
                  modifyDatetime = LocalDateTime.parse("2024-10-01T13:31"),
                ),
              ),
            ).copy(id = nomisAddressId),
          ),
        )
        dpsApiMock.stubUpdateOrganisationAddressPhone(organisationAddressPhoneId = dpsOrganisationAddressPhoneId)

        organisationsOffenderEventsQueue.sendMessage(
          corporateAddressPhoneEvent(
            eventType = "PHONES_CORPORATE-UPDATED",
            corporateId = corporateAndOrganisationId,
            phoneId = nomisPhoneId,
            addressId = nomisAddressId,
          ),
        ).also { waitForAnyProcessingToComplete() }
      }

      @Test
      fun `will track telemetry`() {
        verify(telemetryClient).trackEvent(
          eq("organisations-address-phone-synchronisation-updated-success"),
          check {
            assertThat(it["nomisCorporateId"]).isEqualTo("$corporateAndOrganisationId")
            assertThat(it["dpsOrganisationId"]).isEqualTo("$corporateAndOrganisationId")
            assertThat(it["nomisPhoneId"]).isEqualTo("$nomisPhoneId")
            assertThat(it["nomisAddressId"]).isEqualTo("$nomisAddressId")
            assertThat(it["dpsOrganisationAddressPhoneId"]).isEqualTo("$dpsOrganisationAddressPhoneId")
          },
          isNull(),
        )
      }

      @Test
      fun `will update the phone in DPS from the NOMIS phone`() {
        dpsApiMock.verify(putRequestedFor(urlPathEqualTo("/sync/organisation-address-phone/$dpsOrganisationAddressPhoneId")))
        val request: SyncUpdateAddressPhoneRequest = OrganisationsDpsApiExtension.getRequestBody(putRequestedFor(urlPathEqualTo("/sync/organisation-address-phone/$dpsOrganisationAddressPhoneId")))
        with(request) {
          assertThat(phoneType).isEqualTo("HOME")
          assertThat(extNumber).isEqualTo("ext 123")
          assertThat(phoneNumber).isEqualTo("0114 555 5555")
          assertThat(updatedBy).isEqualTo("T.SMITH")
          assertThat(updatedTime).isEqualTo("2024-10-01T13:31")
        }
      }
    }
  }

  @Nested
  @DisplayName("PHONES_CORPORATE-DELETED (address)")
  inner class CorporateAddressPhoneDeleted {
    private val corporateAndOrganisationId = 123456L
    private val nomisPhoneId = 34567L
    private val nomisAddressId = 6789L
    private val dpsOrganisationAddressPhoneId = 76543L

    @Nested
    inner class WhenMappingExists {

      @BeforeEach
      fun setUp() {
        mappingApiMock.stubGetByNomisAddressPhoneIdOrNull(
          nomisPhoneId = nomisPhoneId,
          mapping = OrganisationsMappingDto(dpsId = dpsOrganisationAddressPhoneId.toString(), nomisId = nomisPhoneId, mappingType = MIGRATED),
        )
        dpsApiMock.stubDeleteOrganisationAddressPhone(organisationAddressPhoneId = dpsOrganisationAddressPhoneId)
        mappingApiMock.stubDeleteByNomisAddressPhoneId(nomisPhoneId)

        organisationsOffenderEventsQueue.sendMessage(
          corporateAddressPhoneEvent(
            eventType = "PHONES_CORPORATE-DELETED",
            corporateId = corporateAndOrganisationId,
            phoneId = nomisPhoneId,
            addressId = nomisAddressId,
            auditModuleName = "DPS_SYNCHRONISATION",
          ),
        ).also { waitForAnyProcessingToComplete() }
      }

      @Test
      fun `will track telemetry`() {
        verify(telemetryClient).trackEvent(
          eq("organisations-address-phone-synchronisation-deleted-success"),
          check {
            assertThat(it["nomisCorporateId"]).isEqualTo("$corporateAndOrganisationId")
            assertThat(it["dpsOrganisationId"]).isEqualTo("$corporateAndOrganisationId")
            assertThat(it["nomisAddressId"]).isEqualTo("$nomisAddressId")
            assertThat(it["nomisPhoneId"]).isEqualTo("$nomisPhoneId")
            assertThat(it["dpsOrganisationAddressPhoneId"]).isEqualTo("$dpsOrganisationAddressPhoneId")
          },
          isNull(),
        )
      }

      @Test
      fun `will delete the organisation address phone from DPS`() {
        dpsApiMock.verify(deleteRequestedFor(urlPathEqualTo("/sync/organisation-address-phone/$dpsOrganisationAddressPhoneId")))
      }

      @Test
      fun `will delete the address phone mapping`() {
        mappingApi.verify(deleteRequestedFor(urlPathEqualTo("/mapping/corporate/address-phone/nomis-phone-id/$nomisPhoneId")))
      }
    }

    @Nested
    inner class WhenMappingDoesNotExist {
      @BeforeEach
      fun setUp() {
        mappingApiMock.stubGetByNomisAddressPhoneIdOrNull(
          nomisPhoneId = nomisPhoneId,
          mapping = null,
        )

        organisationsOffenderEventsQueue.sendMessage(
          corporateAddressPhoneEvent(
            eventType = "PHONES_CORPORATE-DELETED",
            corporateId = corporateAndOrganisationId,
            phoneId = nomisPhoneId,
            addressId = nomisAddressId,
            auditModuleName = "DPS_SYNCHRONISATION",
          ),
        ).also { waitForAnyProcessingToComplete() }
      }

      @Test
      fun `will track telemetry for delete ignored`() {
        verify(telemetryClient).trackEvent(
          eq("organisations-address-phone-synchronisation-deleted-ignored"),
          check {
            assertThat(it["nomisCorporateId"]).isEqualTo("$corporateAndOrganisationId")
            assertThat(it["dpsOrganisationId"]).isEqualTo("$corporateAndOrganisationId")
            assertThat(it["nomisAddressId"]).isEqualTo("$nomisAddressId")
            assertThat(it["nomisPhoneId"]).isEqualTo("$nomisPhoneId")
          },
          isNull(),
        )
      }
    }
  }

  @Nested
  @DisplayName("INTERNET_ADDRESSES_CORPORATE-INSERTED (web address)")
  inner class CorporateInternetAddressWebInserted {
    private val corporateAndOrganisationId = 123456L
    private val nomisInternetAddressId = 34567L
    private val dpsOrganisationWebAddressId = 76543L

    @Nested
    inner class WhenCreatedInDpsByOrganisationSyncService {
      @BeforeEach
      fun setUp() {
        organisationsOffenderEventsQueue.sendMessage(
          corporateInternetAddressEvent(
            eventType = "INTERNET_ADDRESSES_CORPORATE-INSERTED",
            corporateId = corporateAndOrganisationId,
            internetAddressId = nomisInternetAddressId,
            auditModuleName = "DPS_SYNCHRONISATION_ORGANISATION",
          ),
        ).also { waitForAnyProcessingToComplete() }
      }

      @Test
      fun `will not create address in DPS`() {
        dpsApiMock.verify(0, postRequestedFor(urlPathEqualTo("/sync/organisation-web")))
      }

      @Test
      fun `will track telemetry`() {
        verify(telemetryClient).trackEvent(
          eq("organisations-internet-address-synchronisation-created-skipped"),
          check {
            assertThat(it["nomisCorporateId"]).isEqualTo("$corporateAndOrganisationId")
            assertThat(it["dpsOrganisationId"]).isEqualTo("$corporateAndOrganisationId")
            assertThat(it["nomisInternetAddressId"]).isEqualTo("$nomisInternetAddressId")
          },
          isNull(),
        )
      }
    }

    @Nested
    inner class WhenCreatedInNomis {
      @BeforeEach
      fun setUp() {
        mappingApiMock.stubGetByNomisWebIdOrNull(nomisWebId = nomisInternetAddressId, mapping = null)
        nomisApiMock.stubGetCorporateOrganisation(
          corporate = corporateOrganisation().withInternetAddress(
            corporateWebAddress().copy(
              id = nomisInternetAddressId,
              internetAddress = "www.test.com",
              audit = NomisAudit(
                createUsername = "J.SPEAK",
                createDatetime = LocalDateTime.parse("2024-09-01T13:31"),
                modifyUserId = "T.SMITH",
                modifyDatetime = LocalDateTime.parse("2024-10-01T13:31"),
              ),
            ),
          ),
        )
        dpsApiMock.stubCreateOrganisationWebAddress(syncWebResponse().copy(organisationWebAddressId = dpsOrganisationWebAddressId))
        mappingApiMock.stubCreateWebMapping()
        organisationsOffenderEventsQueue.sendMessage(
          corporateInternetAddressEvent(
            eventType = "INTERNET_ADDRESSES_CORPORATE-INSERTED",
            internetAddressId = nomisInternetAddressId,
            corporateId = corporateAndOrganisationId,
          ),
        ).also { waitForAnyProcessingToComplete() }
      }

      @Test
      fun `will check if mapping already exists for web address`() {
        mappingApi.verify(getRequestedFor(urlPathEqualTo("/mapping/corporate/web/nomis-internet-address-id/$nomisInternetAddressId")))
      }

      @Test
      fun `will retrieve the organisation details from NOMIS`() {
        nomisApiMock.verify(getRequestedFor(urlPathEqualTo("/corporates/$corporateAndOrganisationId")))
      }

      @Test
      fun `will create the organisation web address in DPS from the organisation`() {
        dpsApiMock.verify(postRequestedFor(urlPathEqualTo("/sync/organisation-web")))
        val request: SyncCreateWebRequest = OrganisationsDpsApiExtension.getRequestBody(
          postRequestedFor(urlPathEqualTo("/sync/organisation-web")),
        )
        with(request) {
          assertThat(organisationId).isEqualTo(corporateAndOrganisationId)
          assertThat(webAddress).isEqualTo("www.test.com")
          assertThat(createdBy).isEqualTo("J.SPEAK")
          assertThat(createdTime).isEqualTo("2024-09-01T13:31")
        }
      }

      @Test
      fun `will create a mapping between the DPS and NOMIS record`() {
        mappingApiMock.verify(
          postRequestedFor(urlPathEqualTo("/mapping/corporate/web"))
            .withRequestBodyJsonPath("mappingType", "NOMIS_CREATED")
            .withRequestBodyJsonPath("dpsId", "$dpsOrganisationWebAddressId")
            .withRequestBodyJsonPath("nomisId", "$nomisInternetAddressId"),
        )
      }

      @Test
      fun `will track telemetry`() {
        verify(telemetryClient).trackEvent(
          eq("organisations-web-address-synchronisation-created-success"),
          check {
            assertThat(it["nomisCorporateId"]).isEqualTo("$corporateAndOrganisationId")
            assertThat(it["dpsOrganisationId"]).isEqualTo("$corporateAndOrganisationId")
            assertThat(it["nomisInternetAddressId"]).isEqualTo("$nomisInternetAddressId")
            assertThat(it["dpsOrganisationWebAddressId"]).isEqualTo("$dpsOrganisationWebAddressId")
          },
          isNull(),
        )
      }
    }

    @Nested
    inner class WhenAlreadyCreated {
      @BeforeEach
      fun setUp() {
        nomisApiMock.stubGetCorporateOrganisation(
          corporate = corporateOrganisation().withInternetAddress(
            corporateWebAddress().copy(
              id = nomisInternetAddressId,
            ),
          ),
        )

        mappingApiMock.stubGetByNomisWebIdOrNull(nomisWebId = nomisInternetAddressId, mapping = OrganisationsMappingDto(dpsId = "$dpsOrganisationWebAddressId", nomisId = nomisInternetAddressId, mappingType = NOMIS_CREATED))
        organisationsOffenderEventsQueue.sendMessage(
          corporateInternetAddressEvent(
            eventType = "INTERNET_ADDRESSES_CORPORATE-INSERTED",
            internetAddressId = nomisInternetAddressId,
            corporateId = corporateAndOrganisationId,
          ),
        ).also { waitForAnyProcessingToComplete() }
      }

      @Test
      fun `will not create organisation in DPS`() {
        dpsApiMock.verify(0, postRequestedFor(urlPathEqualTo("/sync/organisation-web")))
      }

      @Test
      fun `will track telemetry`() {
        verify(telemetryClient).trackEvent(
          eq("organisations-web-address-synchronisation-created-ignored"),
          check {
            assertThat(it["nomisCorporateId"]).isEqualTo("$corporateAndOrganisationId")
            assertThat(it["dpsOrganisationId"]).isEqualTo("$corporateAndOrganisationId")
            assertThat(it["nomisInternetAddressId"]).isEqualTo("$nomisInternetAddressId")
            assertThat(it["dpsOrganisationWebAddressId"]).isEqualTo("$dpsOrganisationWebAddressId")
          },
          isNull(),
        )
      }
    }

    @Nested
    inner class WhenDuplicateMapping {
      @BeforeEach
      fun setUp() {
        nomisApiMock.stubGetCorporateOrganisation(
          corporate = corporateOrganisation().withInternetAddress(
            corporateWebAddress().copy(
              id = nomisInternetAddressId,
            ),
          ),
        )

        mappingApiMock.stubGetByNomisWebIdOrNull(nomisWebId = nomisInternetAddressId, mapping = null)

        dpsApiMock.stubCreateOrganisationWebAddress(syncWebResponse().copy(organisationWebAddressId = dpsOrganisationWebAddressId))
        mappingApiMock.stubCreateWebMapping(
          error = DuplicateMappingErrorResponse(
            moreInfo = DuplicateErrorContentObject(
              duplicate = OrganisationsMappingDto(
                dpsId = "$dpsOrganisationWebAddressId",
                nomisId = nomisInternetAddressId,
                mappingType = NOMIS_CREATED,
              ),
              existing = OrganisationsMappingDto(
                dpsId = "9999",
                nomisId = nomisInternetAddressId,
                mappingType = NOMIS_CREATED,
              ),
            ),
            errorCode = 1409,
            status = DuplicateMappingErrorResponse.Status._409_CONFLICT,
            userMessage = "Duplicate mapping",
          ),
        )

        organisationsOffenderEventsQueue.sendMessage(
          corporateInternetAddressEvent(
            eventType = "INTERNET_ADDRESSES_CORPORATE-INSERTED",
            internetAddressId = nomisInternetAddressId,
            corporateId = corporateAndOrganisationId,
          ),
        ).also { waitForAnyProcessingToComplete("from-nomis-sync-organisations-duplicate") }
      }

      @Test
      fun `will create the organisation web address in DPS once`() {
        dpsApiMock.verify(1, postRequestedFor(urlPathEqualTo("/sync/organisation-web")))
      }

      @Test
      fun `will attempt to create a mapping between the DPS and NOMIS record once`() {
        mappingApiMock.verify(
          1,
          postRequestedFor(urlPathEqualTo("/mapping/corporate/web"))
            .withRequestBodyJsonPath("mappingType", "NOMIS_CREATED")
            .withRequestBodyJsonPath("dpsId", "$dpsOrganisationWebAddressId")
            .withRequestBodyJsonPath("nomisId", "$nomisInternetAddressId"),
        )
      }

      @Test
      fun `will track telemetry for both overall success and duplicate`() {
        verify(telemetryClient).trackEvent(
          eq("organisations-web-address-synchronisation-created-success"),
          check {
            assertThat(it["nomisCorporateId"]).isEqualTo("$corporateAndOrganisationId")
            assertThat(it["dpsOrganisationId"]).isEqualTo("$corporateAndOrganisationId")
          },
          isNull(),
        )
        verify(telemetryClient).trackEvent(
          eq("from-nomis-sync-organisations-duplicate"),
          check {
            assertThat(it["existingNomisId"]).isEqualTo("$nomisInternetAddressId")
            assertThat(it["existingDpsId"]).isEqualTo("9999")
            assertThat(it["duplicateNomisId"]).isEqualTo("$nomisInternetAddressId")
            assertThat(it["duplicateDpsId"]).isEqualTo("$dpsOrganisationWebAddressId")
            assertThat(it["type"]).isEqualTo("WEB")
          },
          isNull(),
        )
      }
    }

    @Nested
    inner class MappingCreateFails {
      @BeforeEach
      fun setUp() {
        nomisApiMock.stubGetCorporateOrganisation(
          corporate = corporateOrganisation().withInternetAddress(
            corporateWebAddress().copy(
              id = nomisInternetAddressId,
            ),
          ),
        )

        mappingApiMock.stubGetByNomisWebIdOrNull(nomisWebId = nomisInternetAddressId, mapping = null)

        dpsApiMock.stubCreateOrganisationWebAddress(syncWebResponse().copy(organisationWebAddressId = dpsOrganisationWebAddressId))
        mappingApiMock.stubCreateWebMappingFailureFollowedBySuccess()
        organisationsOffenderEventsQueue.sendMessage(
          corporateInternetAddressEvent(
            eventType = "INTERNET_ADDRESSES_CORPORATE-INSERTED",
            internetAddressId = nomisInternetAddressId,
            corporateId = corporateAndOrganisationId,
          ),
        ).also { waitForAnyProcessingToComplete("organisations-web-mapping-synchronisation-created") }
      }

      @Test
      fun `will create the organisation in DPS once`() {
        dpsApiMock.verify(1, postRequestedFor(urlPathEqualTo("/sync/organisation-web")))
      }

      @Test
      fun `will create a mapping between the DPS and NOMIS record`() {
        mappingApiMock.verify(
          2,
          postRequestedFor(urlPathEqualTo("/mapping/corporate/web"))
            .withRequestBodyJsonPath("mappingType", "NOMIS_CREATED")
            .withRequestBodyJsonPath("dpsId", "$dpsOrganisationWebAddressId")
            .withRequestBodyJsonPath("nomisId", "$nomisInternetAddressId"),
        )
      }

      @Test
      fun `will track telemetry`() {
        verify(telemetryClient).trackEvent(
          eq("organisations-web-address-synchronisation-created-success"),
          check {
            assertThat(it["nomisCorporateId"]).isEqualTo("$corporateAndOrganisationId")
            assertThat(it["dpsOrganisationId"]).isEqualTo("$corporateAndOrganisationId")
            assertThat(it["nomisInternetAddressId"]).isEqualTo("$nomisInternetAddressId")
            assertThat(it["dpsOrganisationWebAddressId"]).isEqualTo("$dpsOrganisationWebAddressId")
          },
          isNull(),
        )
      }
    }
  }

  @Nested
  @DisplayName("INTERNET_ADDRESSES_CORPORATE-INSERTED (email address)")
  inner class CorporateInternetAddressEmailInserted {
    private val corporateAndOrganisationId = 123456L
    private val nomisInternetAddressId = 34567L
    private val dpsOrganisationEmailId = 76543L

    @Nested
    inner class WhenCreatedInDpsByOrganisationSyncService {
      @BeforeEach
      fun setUp() {
        organisationsOffenderEventsQueue.sendMessage(
          corporateInternetAddressEvent(
            eventType = "INTERNET_ADDRESSES_CORPORATE-INSERTED",
            corporateId = corporateAndOrganisationId,
            internetAddressId = nomisInternetAddressId,
            auditModuleName = "DPS_SYNCHRONISATION_ORGANISATION",
          ),
        ).also { waitForAnyProcessingToComplete() }
      }

      @Test
      fun `will not create address in DPS`() {
        dpsApiMock.verify(0, postRequestedFor(urlPathEqualTo("/sync/organisation-email")))
      }

      @Test
      fun `will track telemetry`() {
        verify(telemetryClient).trackEvent(
          eq("organisations-internet-address-synchronisation-created-skipped"),
          check {
            assertThat(it["nomisCorporateId"]).isEqualTo("$corporateAndOrganisationId")
            assertThat(it["dpsOrganisationId"]).isEqualTo("$corporateAndOrganisationId")
            assertThat(it["nomisInternetAddressId"]).isEqualTo("$nomisInternetAddressId")
          },
          isNull(),
        )
      }
    }

    @Nested
    inner class WhenCreatedInNomis {
      @BeforeEach
      fun setUp() {
        mappingApiMock.stubGetByNomisEmailIdOrNull(nomisEmailId = nomisInternetAddressId, mapping = null)
        nomisApiMock.stubGetCorporateOrganisation(
          corporate = corporateOrganisation().withInternetAddress(
            corporateEmail().copy(
              id = nomisInternetAddressId,
              internetAddress = "jane@test.com",
              audit = NomisAudit(
                createUsername = "J.SPEAK",
                createDatetime = LocalDateTime.parse("2024-09-01T13:31"),
                modifyUserId = "T.SMITH",
                modifyDatetime = LocalDateTime.parse("2024-10-01T13:31"),
              ),
            ),
          ),
        )
        dpsApiMock.stubCreateOrganisationEmail(syncEmailResponse().copy(organisationEmailId = dpsOrganisationEmailId))
        mappingApiMock.stubCreateEmailMapping()
        organisationsOffenderEventsQueue.sendMessage(
          corporateInternetAddressEvent(
            eventType = "INTERNET_ADDRESSES_CORPORATE-INSERTED",
            internetAddressId = nomisInternetAddressId,
            corporateId = corporateAndOrganisationId,
          ),
        ).also { waitForAnyProcessingToComplete() }
      }

      @Test
      fun `will check if mapping already exists for email address`() {
        mappingApi.verify(getRequestedFor(urlPathEqualTo("/mapping/corporate/email/nomis-internet-address-id/$nomisInternetAddressId")))
      }

      @Test
      fun `will retrieve the organisation details from NOMIS`() {
        nomisApiMock.verify(getRequestedFor(urlPathEqualTo("/corporates/$corporateAndOrganisationId")))
      }

      @Test
      fun `will create the organisation email address in DPS from the organisation`() {
        dpsApiMock.verify(postRequestedFor(urlPathEqualTo("/sync/organisation-email")))
        val request: SyncCreateEmailRequest = OrganisationsDpsApiExtension.getRequestBody(
          postRequestedFor(urlPathEqualTo("/sync/organisation-email")),
        )
        with(request) {
          assertThat(organisationId).isEqualTo(corporateAndOrganisationId)
          assertThat(emailAddress).isEqualTo("jane@test.com")
          assertThat(createdBy).isEqualTo("J.SPEAK")
          assertThat(createdTime).isEqualTo("2024-09-01T13:31")
        }
      }

      @Test
      fun `will create a mapping between the DPS and NOMIS record`() {
        mappingApiMock.verify(
          postRequestedFor(urlPathEqualTo("/mapping/corporate/email"))
            .withRequestBodyJsonPath("mappingType", "NOMIS_CREATED")
            .withRequestBodyJsonPath("dpsId", "$dpsOrganisationEmailId")
            .withRequestBodyJsonPath("nomisId", "$nomisInternetAddressId"),
        )
      }

      @Test
      fun `will track telemetry`() {
        verify(telemetryClient).trackEvent(
          eq("organisations-email-synchronisation-created-success"),
          check {
            assertThat(it["nomisCorporateId"]).isEqualTo("$corporateAndOrganisationId")
            assertThat(it["dpsOrganisationId"]).isEqualTo("$corporateAndOrganisationId")
            assertThat(it["nomisInternetAddressId"]).isEqualTo("$nomisInternetAddressId")
            assertThat(it["dpsOrganisationEmailId"]).isEqualTo("$dpsOrganisationEmailId")
          },
          isNull(),
        )
      }
    }

    @Nested
    inner class WhenAlreadyCreated {
      @BeforeEach
      fun setUp() {
        nomisApiMock.stubGetCorporateOrganisation(
          corporate = corporateOrganisation().withInternetAddress(
            corporateEmail().copy(
              id = nomisInternetAddressId,
            ),
          ),
        )

        mappingApiMock.stubGetByNomisEmailIdOrNull(nomisEmailId = nomisInternetAddressId, mapping = OrganisationsMappingDto(dpsId = "$dpsOrganisationEmailId", nomisId = nomisInternetAddressId, mappingType = NOMIS_CREATED))
        organisationsOffenderEventsQueue.sendMessage(
          corporateInternetAddressEvent(
            eventType = "INTERNET_ADDRESSES_CORPORATE-INSERTED",
            internetAddressId = nomisInternetAddressId,
            corporateId = corporateAndOrganisationId,
          ),
        ).also { waitForAnyProcessingToComplete() }
      }

      @Test
      fun `will not create organisation in DPS`() {
        dpsApiMock.verify(0, postRequestedFor(urlPathEqualTo("/sync/organisation-email")))
      }

      @Test
      fun `will track telemetry`() {
        verify(telemetryClient).trackEvent(
          eq("organisations-email-synchronisation-created-ignored"),
          check {
            assertThat(it["nomisCorporateId"]).isEqualTo("$corporateAndOrganisationId")
            assertThat(it["dpsOrganisationId"]).isEqualTo("$corporateAndOrganisationId")
            assertThat(it["nomisInternetAddressId"]).isEqualTo("$nomisInternetAddressId")
            assertThat(it["dpsOrganisationEmailId"]).isEqualTo("$dpsOrganisationEmailId")
          },
          isNull(),
        )
      }
    }

    @Nested
    inner class WhenDuplicateMapping {
      @BeforeEach
      fun setUp() {
        nomisApiMock.stubGetCorporateOrganisation(
          corporate = corporateOrganisation().withInternetAddress(
            corporateEmail().copy(
              id = nomisInternetAddressId,
            ),
          ),
        )

        mappingApiMock.stubGetByNomisEmailIdOrNull(nomisEmailId = nomisInternetAddressId, mapping = null)

        dpsApiMock.stubCreateOrganisationEmail(syncEmailResponse().copy(organisationEmailId = dpsOrganisationEmailId))
        mappingApiMock.stubCreateEmailMapping(
          error = DuplicateMappingErrorResponse(
            moreInfo = DuplicateErrorContentObject(
              duplicate = OrganisationsMappingDto(
                dpsId = "$dpsOrganisationEmailId",
                nomisId = nomisInternetAddressId,
                mappingType = NOMIS_CREATED,
              ),
              existing = OrganisationsMappingDto(
                dpsId = "9999",
                nomisId = nomisInternetAddressId,
                mappingType = NOMIS_CREATED,
              ),
            ),
            errorCode = 1409,
            status = DuplicateMappingErrorResponse.Status._409_CONFLICT,
            userMessage = "Duplicate mapping",
          ),
        )

        organisationsOffenderEventsQueue.sendMessage(
          corporateInternetAddressEvent(
            eventType = "INTERNET_ADDRESSES_CORPORATE-INSERTED",
            internetAddressId = nomisInternetAddressId,
            corporateId = corporateAndOrganisationId,
          ),
        ).also { waitForAnyProcessingToComplete("from-nomis-sync-organisations-duplicate") }
      }

      @Test
      fun `will create the organisation email address in DPS once`() {
        dpsApiMock.verify(1, postRequestedFor(urlPathEqualTo("/sync/organisation-email")))
      }

      @Test
      fun `will attempt to create a mapping between the DPS and NOMIS record once`() {
        mappingApiMock.verify(
          1,
          postRequestedFor(urlPathEqualTo("/mapping/corporate/email"))
            .withRequestBodyJsonPath("mappingType", "NOMIS_CREATED")
            .withRequestBodyJsonPath("dpsId", "$dpsOrganisationEmailId")
            .withRequestBodyJsonPath("nomisId", "$nomisInternetAddressId"),
        )
      }

      @Test
      fun `will track telemetry for both overall success and duplicate`() {
        verify(telemetryClient).trackEvent(
          eq("organisations-email-synchronisation-created-success"),
          check {
            assertThat(it["nomisCorporateId"]).isEqualTo("$corporateAndOrganisationId")
            assertThat(it["dpsOrganisationId"]).isEqualTo("$corporateAndOrganisationId")
          },
          isNull(),
        )
        verify(telemetryClient).trackEvent(
          eq("from-nomis-sync-organisations-duplicate"),
          check {
            assertThat(it["existingNomisId"]).isEqualTo("$nomisInternetAddressId")
            assertThat(it["existingDpsId"]).isEqualTo("9999")
            assertThat(it["duplicateNomisId"]).isEqualTo("$nomisInternetAddressId")
            assertThat(it["duplicateDpsId"]).isEqualTo("$dpsOrganisationEmailId")
            assertThat(it["type"]).isEqualTo("EMAIL")
          },
          isNull(),
        )
      }
    }

    @Nested
    inner class MappingCreateFails {
      @BeforeEach
      fun setUp() {
        nomisApiMock.stubGetCorporateOrganisation(
          corporate = corporateOrganisation().withInternetAddress(
            corporateEmail().copy(
              id = nomisInternetAddressId,
            ),
          ),
        )

        mappingApiMock.stubGetByNomisEmailIdOrNull(nomisEmailId = nomisInternetAddressId, mapping = null)

        dpsApiMock.stubCreateOrganisationEmail(syncEmailResponse().copy(organisationEmailId = dpsOrganisationEmailId))
        mappingApiMock.stubCreateEmailMappingFailureFollowedBySuccess()
        organisationsOffenderEventsQueue.sendMessage(
          corporateInternetAddressEvent(
            eventType = "INTERNET_ADDRESSES_CORPORATE-INSERTED",
            internetAddressId = nomisInternetAddressId,
            corporateId = corporateAndOrganisationId,
          ),
        ).also { waitForAnyProcessingToComplete("organisations-email-mapping-synchronisation-created") }
      }

      @Test
      fun `will create the organisation in DPS once`() {
        dpsApiMock.verify(1, postRequestedFor(urlPathEqualTo("/sync/organisation-email")))
      }

      @Test
      fun `will create a mapping between the DPS and NOMIS record`() {
        mappingApiMock.verify(
          2,
          postRequestedFor(urlPathEqualTo("/mapping/corporate/email"))
            .withRequestBodyJsonPath("mappingType", "NOMIS_CREATED")
            .withRequestBodyJsonPath("dpsId", "$dpsOrganisationEmailId")
            .withRequestBodyJsonPath("nomisId", "$nomisInternetAddressId"),
        )
      }

      @Test
      fun `will track telemetry`() {
        verify(telemetryClient).trackEvent(
          eq("organisations-email-synchronisation-created-success"),
          check {
            assertThat(it["nomisCorporateId"]).isEqualTo("$corporateAndOrganisationId")
            assertThat(it["dpsOrganisationId"]).isEqualTo("$corporateAndOrganisationId")
            assertThat(it["nomisInternetAddressId"]).isEqualTo("$nomisInternetAddressId")
            assertThat(it["dpsOrganisationEmailId"]).isEqualTo("$dpsOrganisationEmailId")
          },
          isNull(),
        )
      }
    }
  }

  @Nested
  @DisplayName("INTERNET_ADDRESSES_CORPORATE-UPDATED")
  inner class CorporateInternetAddressUpdated {
    private val corporateAndOrganisationId = 123456L
    private val nomisInternetAddressId = 34567L

    @Nested
    inner class WhenUpdatedInDpsByOrganisationSyncService {
      @BeforeEach
      fun setUp() {
        organisationsOffenderEventsQueue.sendMessage(
          corporateInternetAddressEvent(
            eventType = "INTERNET_ADDRESSES_CORPORATE-UPDATED",
            corporateId = corporateAndOrganisationId,
            internetAddressId = nomisInternetAddressId,
            auditModuleName = "DPS_SYNCHRONISATION_ORGANISATION",
          ),
        ).also { waitForAnyProcessingToComplete() }
      }

      @Test
      fun `will not update either the web or email address in DPS`() {
        dpsApiMock.verify(0, putRequestedFor(urlPathMatching("/sync/organisation-web/.*")))
        dpsApiMock.verify(0, putRequestedFor(urlPathMatching("/sync/organisation-email/.*")))
      }

      @Test
      fun `will track telemetry`() {
        verify(telemetryClient).trackEvent(
          eq("organisations-internet-address-synchronisation-updated-skipped"),
          check {
            assertThat(it["nomisCorporateId"]).isEqualTo("$corporateAndOrganisationId")
            assertThat(it["dpsOrganisationId"]).isEqualTo("$corporateAndOrganisationId")
            assertThat(it["nomisInternetAddressId"]).isEqualTo("$nomisInternetAddressId")
          },
          isNull(),
        )
      }
    }

    @Nested
    inner class WhenUpdatedInNomisAndRemainsAWebAddress {
      private val dpsOrganisationWebAddressId = 76543L

      @BeforeEach
      fun setUp() {
        mappingApiMock.stubGetByNomisWebId(
          nomisWebId = nomisInternetAddressId,
          mapping = OrganisationsMappingDto(dpsId = dpsOrganisationWebAddressId.toString(), nomisId = nomisInternetAddressId, mappingType = MIGRATED),
        )
        nomisApiMock.stubGetCorporateOrganisation(
          corporateId = corporateAndOrganisationId,
          corporate = corporateOrganisation().withInternetAddress(
            corporateWebAddress().copy(
              id = nomisInternetAddressId,
              internetAddress = "www.test.com",
              audit = NomisAudit(
                createUsername = "J.SPEAK",
                createDatetime = LocalDateTime.parse("2024-09-01T13:31"),
                modifyUserId = "T.SMITH",
                modifyDatetime = LocalDateTime.parse("2024-10-01T13:31"),
              ),
            ),
          ),
        )
        dpsApiMock.stubUpdateOrganisationWebAddress(organisationWebAddressId = dpsOrganisationWebAddressId)

        organisationsOffenderEventsQueue.sendMessage(
          corporateInternetAddressEvent(
            eventType = "INTERNET_ADDRESSES_CORPORATE-UPDATED",
            corporateId = corporateAndOrganisationId,
            internetAddressId = nomisInternetAddressId,
          ),
        ).also { waitForAnyProcessingToComplete() }
      }

      @Test
      fun `will track telemetry`() {
        verify(telemetryClient).trackEvent(
          eq("organisations-web-address-synchronisation-updated-success"),
          check {
            assertThat(it["nomisCorporateId"]).isEqualTo("$corporateAndOrganisationId")
            assertThat(it["dpsOrganisationId"]).isEqualTo("$corporateAndOrganisationId")
            assertThat(it["nomisInternetAddressId"]).isEqualTo("$nomisInternetAddressId")
            assertThat(it["dpsOrganisationWebAddressId"]).isEqualTo("$dpsOrganisationWebAddressId")
          },
          isNull(),
        )
      }

      @Test
      fun `will update the web address in DPS from the NOMIS address`() {
        dpsApiMock.verify(putRequestedFor(urlPathEqualTo("/sync/organisation-web/$dpsOrganisationWebAddressId")))
        val request: SyncUpdateWebRequest = OrganisationsDpsApiExtension.getRequestBody(putRequestedFor(urlPathEqualTo("/sync/organisation-web/$dpsOrganisationWebAddressId")))
        with(request) {
          assertThat(webAddress).isEqualTo("www.test.com")
          assertThat(updatedBy).isEqualTo("T.SMITH")
          assertThat(updatedTime).isEqualTo("2024-10-01T13:31")
        }
      }
    }

    @Nested
    inner class WhenUpdatedInNomisAndRemainsAEmailAddress {
      private val dpsOrganisationEmailId = 76543L

      @BeforeEach
      fun setUp() {
        mappingApiMock.stubGetByNomisEmailId(
          nomisEmailId = nomisInternetAddressId,
          mapping = OrganisationsMappingDto(dpsId = dpsOrganisationEmailId.toString(), nomisId = nomisInternetAddressId, mappingType = MIGRATED),
        )
        nomisApiMock.stubGetCorporateOrganisation(
          corporateId = corporateAndOrganisationId,
          corporate = corporateOrganisation().withInternetAddress(
            corporateEmail().copy(
              id = nomisInternetAddressId,
              internetAddress = "jane@test.com",
              audit = NomisAudit(
                createUsername = "J.SPEAK",
                createDatetime = LocalDateTime.parse("2024-09-01T13:31"),
                modifyUserId = "T.SMITH",
                modifyDatetime = LocalDateTime.parse("2024-10-01T13:31"),
              ),
            ),
          ),
        )
        dpsApiMock.stubUpdateOrganisationEmail(organisationEmailId = dpsOrganisationEmailId)

        organisationsOffenderEventsQueue.sendMessage(
          corporateInternetAddressEvent(
            eventType = "INTERNET_ADDRESSES_CORPORATE-UPDATED",
            corporateId = corporateAndOrganisationId,
            internetAddressId = nomisInternetAddressId,
          ),
        ).also { waitForAnyProcessingToComplete() }
      }

      @Test
      fun `will track telemetry`() {
        verify(telemetryClient).trackEvent(
          eq("organisations-email-synchronisation-updated-success"),
          check {
            assertThat(it["nomisCorporateId"]).isEqualTo("$corporateAndOrganisationId")
            assertThat(it["dpsOrganisationId"]).isEqualTo("$corporateAndOrganisationId")
            assertThat(it["nomisInternetAddressId"]).isEqualTo("$nomisInternetAddressId")
            assertThat(it["dpsOrganisationEmailId"]).isEqualTo("$dpsOrganisationEmailId")
          },
          isNull(),
        )
      }

      @Test
      fun `will update the email address in DPS from the NOMIS email`() {
        dpsApiMock.verify(putRequestedFor(urlPathEqualTo("/sync/organisation-email/$dpsOrganisationEmailId")))
        val request: SyncUpdateEmailRequest = OrganisationsDpsApiExtension.getRequestBody(putRequestedFor(urlPathEqualTo("/sync/organisation-email/$dpsOrganisationEmailId")))
        with(request) {
          assertThat(emailAddress).isEqualTo("jane@test.com")
          assertThat(updatedBy).isEqualTo("T.SMITH")
          assertThat(updatedTime).isEqualTo("2024-10-01T13:31")
        }
      }
    }

    @Nested
    inner class WhenUpdatedInNomisAndSwitchesToEmailAddress {
      private val dpsOrganisationWebAddressId = 76543L
      private val dpsOrganisationEmailId = 888643L

      @BeforeEach
      fun setUp() {
        mappingApiMock.stubGetByNomisWebId(
          nomisWebId = nomisInternetAddressId,
          mapping = OrganisationsMappingDto(dpsId = dpsOrganisationWebAddressId.toString(), nomisId = nomisInternetAddressId, mappingType = MIGRATED),
        )
        nomisApiMock.stubGetCorporateOrganisation(
          corporateId = corporateAndOrganisationId,
          corporate = corporateOrganisation().withInternetAddress(
            corporateEmail().copy(
              id = nomisInternetAddressId,
              internetAddress = "jane@test.com",
              audit = NomisAudit(
                createUsername = "J.SPEAK",
                createDatetime = LocalDateTime.parse("2024-09-01T13:31"),
                modifyUserId = "T.SMITH",
                modifyDatetime = LocalDateTime.parse("2024-10-01T13:31"),
              ),
            ),
          ),
        )
        dpsApiMock.stubDeleteOrganisationWebAddress(organisationWebAddressId = dpsOrganisationWebAddressId)
        dpsApiMock.stubCreateOrganisationEmail(syncEmailResponse().copy(organisationEmailId = dpsOrganisationEmailId))
        mappingApiMock.stubDeleteByNomisWebId(nomisWebId = nomisInternetAddressId)
        mappingApiMock.stubCreateEmailMapping()
        organisationsOffenderEventsQueue.sendMessage(
          corporateInternetAddressEvent(
            eventType = "INTERNET_ADDRESSES_CORPORATE-UPDATED",
            corporateId = corporateAndOrganisationId,
            internetAddressId = nomisInternetAddressId,
          ),
        ).also { waitForAnyProcessingToComplete() }
      }

      @Test
      fun `will track telemetry`() {
        verify(telemetryClient).trackEvent(
          eq("organisations-web-address-synchronisation-updated-success"),
          check {
            assertThat(it["nomisCorporateId"]).isEqualTo("$corporateAndOrganisationId")
            assertThat(it["dpsOrganisationId"]).isEqualTo("$corporateAndOrganisationId")
            assertThat(it["nomisInternetAddressId"]).isEqualTo("$nomisInternetAddressId")
            assertThat(it["dpsOrganisationWebAddressId"]).isEqualTo("$dpsOrganisationWebAddressId")
            assertThat(it["dpsOrganisationEmailId"]).isEqualTo("$dpsOrganisationEmailId")
          },
          isNull(),
        )
      }

      @Test
      fun `will delete the web address in DPS since it is now an email address`() {
        dpsApiMock.verify(deleteRequestedFor(urlPathEqualTo("/sync/organisation-web/$dpsOrganisationWebAddressId")))
      }

      @Test
      fun `will delete the web mapping since it is now an email address`() {
        mappingApiMock.verify(deleteRequestedFor(urlPathEqualTo("/mapping/corporate/web/nomis-internet-address-id/$nomisInternetAddressId")))
      }

      @Test
      fun `will create the email address in DPS from the NOMIS email`() {
        dpsApiMock.verify(postRequestedFor(urlPathEqualTo("/sync/organisation-email")))
        val request: SyncCreateEmailRequest = OrganisationsDpsApiExtension.getRequestBody(postRequestedFor(urlPathEqualTo("/sync/organisation-email")))
        with(request) {
          assertThat(emailAddress).isEqualTo("jane@test.com")
          assertThat(createdBy).isEqualTo("T.SMITH")
          assertThat(createdTime).isEqualTo("2024-10-01T13:31")
        }
      }

      @Test
      fun `will create a mapping between the DPS and NOMIS record`() {
        mappingApiMock.verify(
          postRequestedFor(urlPathEqualTo("/mapping/corporate/email"))
            .withRequestBodyJsonPath("mappingType", "NOMIS_CREATED")
            .withRequestBodyJsonPath("dpsId", "$dpsOrganisationEmailId")
            .withRequestBodyJsonPath("nomisId", "$nomisInternetAddressId"),
        )
      }
    }

    @Nested
    inner class WhenUpdatedInNomisAndSwitchesToWebAddress {
      private val dpsOrganisationWebAddressId = 76543L
      private val dpsOrganisationEmailId = 888643L

      @BeforeEach
      fun setUp() {
        mappingApiMock.stubGetByNomisEmailId(
          nomisEmailId = nomisInternetAddressId,
          mapping = OrganisationsMappingDto(dpsId = dpsOrganisationEmailId.toString(), nomisId = nomisInternetAddressId, mappingType = MIGRATED),
        )
        nomisApiMock.stubGetCorporateOrganisation(
          corporateId = corporateAndOrganisationId,
          corporate = corporateOrganisation().withInternetAddress(
            corporateWebAddress().copy(
              id = nomisInternetAddressId,
              internetAddress = "www.test.com",
              audit = NomisAudit(
                createUsername = "J.SPEAK",
                createDatetime = LocalDateTime.parse("2024-09-01T13:31"),
                modifyUserId = "T.SMITH",
                modifyDatetime = LocalDateTime.parse("2024-10-01T13:31"),
              ),
            ),
          ),
        )
        dpsApiMock.stubDeleteOrganisationEmail(organisationEmailId = dpsOrganisationEmailId)
        dpsApiMock.stubCreateOrganisationWebAddress(syncWebResponse().copy(organisationWebAddressId = dpsOrganisationWebAddressId))
        mappingApiMock.stubDeleteByNomisEmailId(nomisEmailId = nomisInternetAddressId)
        mappingApiMock.stubCreateWebMapping()
        organisationsOffenderEventsQueue.sendMessage(
          corporateInternetAddressEvent(
            eventType = "INTERNET_ADDRESSES_CORPORATE-UPDATED",
            corporateId = corporateAndOrganisationId,
            internetAddressId = nomisInternetAddressId,
          ),
        ).also { waitForAnyProcessingToComplete() }
      }

      @Test
      fun `will track telemetry`() {
        verify(telemetryClient).trackEvent(
          eq("organisations-email-synchronisation-updated-success"),
          check {
            assertThat(it["nomisCorporateId"]).isEqualTo("$corporateAndOrganisationId")
            assertThat(it["dpsOrganisationId"]).isEqualTo("$corporateAndOrganisationId")
            assertThat(it["nomisInternetAddressId"]).isEqualTo("$nomisInternetAddressId")
            assertThat(it["dpsOrganisationWebAddressId"]).isEqualTo("$dpsOrganisationWebAddressId")
            assertThat(it["dpsOrganisationEmailId"]).isEqualTo("$dpsOrganisationEmailId")
          },
          isNull(),
        )
      }

      @Test
      fun `will delete the email address in DPS since it is now a web address`() {
        dpsApiMock.verify(deleteRequestedFor(urlPathEqualTo("/sync/organisation-email/$dpsOrganisationEmailId")))
      }

      @Test
      fun `will delete the email mapping since it is now a web address`() {
        mappingApiMock.verify(deleteRequestedFor(urlPathEqualTo("/mapping/corporate/email/nomis-internet-address-id/$nomisInternetAddressId")))
      }

      @Test
      fun `will create the web address in DPS from the NOMIS address`() {
        dpsApiMock.verify(postRequestedFor(urlPathEqualTo("/sync/organisation-web")))
        val request: SyncCreateWebRequest = OrganisationsDpsApiExtension.getRequestBody(postRequestedFor(urlPathEqualTo("/sync/organisation-web")))
        with(request) {
          assertThat(webAddress).isEqualTo("www.test.com")
          assertThat(createdBy).isEqualTo("T.SMITH")
          assertThat(createdTime).isEqualTo("2024-10-01T13:31")
        }
      }

      @Test
      fun `will create a mapping between the DPS and NOMIS record`() {
        mappingApiMock.verify(
          postRequestedFor(urlPathEqualTo("/mapping/corporate/web"))
            .withRequestBodyJsonPath("mappingType", "NOMIS_CREATED")
            .withRequestBodyJsonPath("dpsId", "$dpsOrganisationWebAddressId")
            .withRequestBodyJsonPath("nomisId", "$nomisInternetAddressId"),
        )
      }
    }

    @Nested
    inner class WhenNoMappingFound {
      @BeforeEach
      fun setUp() {
        mappingApiMock.stubGetByNomisEmailIdOrNull(
          nomisEmailId = nomisInternetAddressId,
          mapping = null,
        )
        mappingApiMock.stubGetByNomisWebIdOrNull(
          nomisWebId = nomisInternetAddressId,
          mapping = null,
        )
        nomisApiMock.stubGetCorporateOrganisation(
          corporateId = corporateAndOrganisationId,
          corporate = corporateOrganisation().withInternetAddress(
            corporateWebAddress().copy(
              id = nomisInternetAddressId,
            ),
          ),
        )
        organisationsOffenderEventsQueue.sendMessage(
          corporateInternetAddressEvent(
            eventType = "INTERNET_ADDRESSES_CORPORATE-UPDATED",
            corporateId = corporateAndOrganisationId,
            internetAddressId = nomisInternetAddressId,
          ),
        )
      }

      @Test
      fun `will track telemetry`() {
        await untilAsserted {
          verify(telemetryClient, atLeastOnce()).trackEvent(
            eq("organisations-internet-address-synchronisation-updated-error"),
            check {
              assertThat(it["nomisCorporateId"]).isEqualTo("$corporateAndOrganisationId")
              assertThat(it["dpsOrganisationId"]).isEqualTo("$corporateAndOrganisationId")
              assertThat(it["nomisInternetAddressId"]).isEqualTo("$nomisInternetAddressId")
            },
            isNull(),
          )
        }
      }

      @Test
      fun `event will be sent to dead letter queue`() {
        await untilAsserted {
          assertThat(organisationsOffenderEventsQueue.hasMessagesOnDLQQueue()).isTrue
        }
      }
    }
  }

  @Nested
  @DisplayName("INTERNET_ADDRESSES_CORPORATE-DELETED")
  inner class CorporateInternetAddressDeleted {
    private val corporateAndOrganisationId = 123456L
    private val nomisInternetAddressId = 34567L

    @Nested
    inner class WhenMappingExistsForWebAddress {
      private val dpsOrganisationWebAddressId = 76543L

      @BeforeEach
      fun setUp() {
        mappingApiMock.stubGetByNomisWebIdOrNull(
          nomisWebId = nomisInternetAddressId,
          mapping = OrganisationsMappingDto(dpsId = dpsOrganisationWebAddressId.toString(), nomisId = nomisInternetAddressId, mappingType = MIGRATED),
        )
        dpsApiMock.stubDeleteOrganisationWebAddress(organisationWebAddressId = dpsOrganisationWebAddressId)
        mappingApiMock.stubDeleteByNomisWebId(nomisInternetAddressId)

        organisationsOffenderEventsQueue.sendMessage(
          corporateInternetAddressEvent(
            eventType = "INTERNET_ADDRESSES_CORPORATE-DELETED",
            corporateId = corporateAndOrganisationId,
            internetAddressId = nomisInternetAddressId,
            auditModuleName = "DPS_SYNCHRONISATION",
          ),
        ).also { waitForAnyProcessingToComplete() }
      }

      @Test
      fun `will track telemetry`() {
        verify(telemetryClient).trackEvent(
          eq("organisations-web-address-synchronisation-deleted-success"),
          check {
            assertThat(it["nomisCorporateId"]).isEqualTo("$corporateAndOrganisationId")
            assertThat(it["dpsOrganisationId"]).isEqualTo("$corporateAndOrganisationId")
            assertThat(it["nomisInternetAddressId"]).isEqualTo("$nomisInternetAddressId")
            assertThat(it["dpsOrganisationWebAddressId"]).isEqualTo("$dpsOrganisationWebAddressId")
          },
          isNull(),
        )
      }

      @Test
      fun `will delete the organisation web address from DPS`() {
        dpsApiMock.verify(deleteRequestedFor(urlPathEqualTo("/sync/organisation-web/$dpsOrganisationWebAddressId")))
      }

      @Test
      fun `will delete the web mapping`() {
        mappingApi.verify(deleteRequestedFor(urlPathEqualTo("/mapping/corporate/web/nomis-internet-address-id/$nomisInternetAddressId")))
      }
    }

    @Nested
    inner class WhenMappingExistsForEmailAddress {
      private val dpsOrganisationEmailId = 76543L

      @BeforeEach
      fun setUp() {
        mappingApiMock.stubGetByNomisEmailIdOrNull(
          nomisEmailId = nomisInternetAddressId,
          mapping = OrganisationsMappingDto(dpsId = dpsOrganisationEmailId.toString(), nomisId = nomisInternetAddressId, mappingType = MIGRATED),
        )
        dpsApiMock.stubDeleteOrganisationEmail(organisationEmailId = dpsOrganisationEmailId)
        mappingApiMock.stubDeleteByNomisEmailId(nomisInternetAddressId)

        organisationsOffenderEventsQueue.sendMessage(
          corporateInternetAddressEvent(
            eventType = "INTERNET_ADDRESSES_CORPORATE-DELETED",
            corporateId = corporateAndOrganisationId,
            internetAddressId = nomisInternetAddressId,
            auditModuleName = "DPS_SYNCHRONISATION",
          ),
        ).also { waitForAnyProcessingToComplete() }
      }

      @Test
      fun `will track telemetry`() {
        verify(telemetryClient).trackEvent(
          eq("organisations-email-synchronisation-deleted-success"),
          check {
            assertThat(it["nomisCorporateId"]).isEqualTo("$corporateAndOrganisationId")
            assertThat(it["dpsOrganisationId"]).isEqualTo("$corporateAndOrganisationId")
            assertThat(it["nomisInternetAddressId"]).isEqualTo("$nomisInternetAddressId")
            assertThat(it["dpsOrganisationEmailId"]).isEqualTo("$dpsOrganisationEmailId")
          },
          isNull(),
        )
      }

      @Test
      fun `will delete the organisation web address from DPS`() {
        dpsApiMock.verify(deleteRequestedFor(urlPathEqualTo("/sync/organisation-email/$dpsOrganisationEmailId")))
      }

      @Test
      fun `will delete the web mapping`() {
        mappingApi.verify(deleteRequestedFor(urlPathEqualTo("/mapping/corporate/email/nomis-internet-address-id/$nomisInternetAddressId")))
      }
    }

    @Nested
    inner class WhenMappingDoesNotExist {
      @BeforeEach
      fun setUp() {
        mappingApiMock.stubGetByNomisWebIdOrNull(
          nomisWebId = nomisInternetAddressId,
          mapping = null,
        )
        mappingApiMock.stubGetByNomisEmailIdOrNull(
          nomisEmailId = nomisInternetAddressId,
          mapping = null,
        )

        organisationsOffenderEventsQueue.sendMessage(
          corporateInternetAddressEvent(
            eventType = "INTERNET_ADDRESSES_CORPORATE-DELETED",
            corporateId = corporateAndOrganisationId,
            internetAddressId = nomisInternetAddressId,
            auditModuleName = "DPS_SYNCHRONISATION",
          ),
        ).also { waitForAnyProcessingToComplete() }
      }

      @Test
      fun `will track telemetry for delete ignored`() {
        verify(telemetryClient).trackEvent(
          eq("organisations-internet-address-synchronisation-deleted-ignored"),
          check {
            assertThat(it["nomisCorporateId"]).isEqualTo("$corporateAndOrganisationId")
            assertThat(it["dpsOrganisationId"]).isEqualTo("$corporateAndOrganisationId")
            assertThat(it["nomisInternetAddressId"]).isEqualTo("$nomisInternetAddressId")
          },
          isNull(),
        )
      }
    }
  }

  @Nested
  @DisplayName("CORPORATE_TYPES-INSERTED")
  inner class CorporateTypeInserted {
    private val corporateAndOrganisationId = 123456L
    private val nomisCorporateType = "BSKILLS"

    @Nested
    inner class WhenInsertedInDpsByOrganisationSyncService {
      @BeforeEach
      fun setUp() {
        organisationsOffenderEventsQueue.sendMessage(
          corporateTypeEvent(
            eventType = "CORPORATE_TYPES-INSERTED",
            corporateId = corporateAndOrganisationId,
            corporateType = nomisCorporateType,
            auditModuleName = "DPS_SYNCHRONISATION_ORGANISATION",
          ),
        ).also { waitForAnyProcessingToComplete() }
      }

      @Test
      fun `will not update the types in DPS`() {
        dpsApiMock.verify(0, putRequestedFor(urlPathMatching("/sync/organisation-types/$corporateAndOrganisationId")))
      }

      @Test
      fun `will track telemetry`() {
        verify(telemetryClient).trackEvent(
          eq("organisations-type-synchronisation-inserted-skipped"),
          check {
            assertThat(it["nomisCorporateId"]).isEqualTo("$corporateAndOrganisationId")
            assertThat(it["dpsOrganisationId"]).isEqualTo("$corporateAndOrganisationId")
            assertThat(it["nomisCorporateType"]).isEqualTo(nomisCorporateType)
          },
          isNull(),
        )
      }
    }

    @Nested
    inner class WhenInsertedInNomis {

      @BeforeEach
      fun setUp() {
        nomisApiMock.stubGetCorporateOrganisation(
          corporateId = corporateAndOrganisationId,
          corporate = corporateOrganisation().copy(
            types = listOf(
              CorporateOrganisationType(
                type = CodeDescription("TEA", "Teacher"),
                audit = NomisAudit(
                  createUsername = "J.SPEAK",
                  createDatetime = LocalDateTime.parse("2024-09-01T13:31"),
                  modifyUserId = "T.SMITH",
                  modifyDatetime = LocalDateTime.parse("2024-10-01T13:31"),
                ),
              ),
              CorporateOrganisationType(
                type = CodeDescription("BSKILLS", "Basic Skills Provider"),
                audit = NomisAudit(
                  createUsername = "J.SPEAK",
                  createDatetime = LocalDateTime.parse("2024-10-01T13:31"),
                ),
              ),
            ),
          ),
        )
        dpsApiMock.stubUpdateOrganisationTypes(corporateAndOrganisationId)

        organisationsOffenderEventsQueue.sendMessage(
          corporateTypeEvent(
            eventType = "CORPORATE_TYPES-INSERTED",
            corporateId = corporateAndOrganisationId,
            corporateType = nomisCorporateType,
            auditModuleName = "OCUCORPT",
          ),
        ).also { waitForAnyProcessingToComplete() }
      }

      @Test
      fun `will track telemetry`() {
        verify(telemetryClient).trackEvent(
          eq("organisations-type-synchronisation-inserted-success"),
          check {
            assertThat(it["nomisCorporateId"]).isEqualTo("$corporateAndOrganisationId")
            assertThat(it["dpsOrganisationId"]).isEqualTo("$corporateAndOrganisationId")
            assertThat(it["nomisCorporateType"]).isEqualTo(nomisCorporateType)
          },
          isNull(),
        )
      }

      @Test
      fun `will update all the types in DPS from the NOMIS types`() {
        dpsApiMock.verify(putRequestedFor(urlPathEqualTo("/sync/organisation-types/$corporateAndOrganisationId")))
        val request: SyncUpdateTypesRequest = OrganisationsDpsApiExtension.getRequestBody(putRequestedFor(urlPathEqualTo("/sync/organisation-types/$corporateAndOrganisationId")))
        with(request) {
          assertThat(types).hasSize(2)
          assertThat(types[0].type).isEqualTo("TEA")
          assertThat(types[0].createdBy).isEqualTo("J.SPEAK")
          assertThat(types[0].createdTime).isEqualTo("2024-09-01T13:31")
          assertThat(types[0].updatedBy).isEqualTo("T.SMITH")
          assertThat(types[0].updatedTime).isEqualTo("2024-10-01T13:31")
          assertThat(types[1].type).isEqualTo("BSKILLS")
          assertThat(types[1].createdBy).isEqualTo("J.SPEAK")
          assertThat(types[1].createdTime).isEqualTo("2024-10-01T13:31")
          assertThat(types[1].updatedBy).isNull()
          assertThat(types[1].updatedTime).isNull()
        }
      }
    }
  }

  @Nested
  @DisplayName("CORPORATE_TYPES-UPDATED")
  inner class CorporateTypeUpdated {
    private val corporateAndOrganisationId = 123456L
    private val nomisCorporateType = "BSKILLS"

    @Nested
    inner class WhenUpdatedInDpsByOrganisationSyncService {
      @BeforeEach
      fun setUp() {
        organisationsOffenderEventsQueue.sendMessage(
          corporateTypeEvent(
            eventType = "CORPORATE_TYPES-UPDATED",
            corporateId = corporateAndOrganisationId,
            corporateType = nomisCorporateType,
            auditModuleName = "DPS_SYNCHRONISATION_ORGANISATION",
          ),
        ).also { waitForAnyProcessingToComplete() }
      }

      @Test
      fun `will not update the types in DPS`() {
        dpsApiMock.verify(0, putRequestedFor(urlPathMatching("/sync/organisation-types/$corporateAndOrganisationId")))
      }

      @Test
      fun `will track telemetry`() {
        verify(telemetryClient).trackEvent(
          eq("organisations-type-synchronisation-updated-skipped"),
          check {
            assertThat(it["nomisCorporateId"]).isEqualTo("$corporateAndOrganisationId")
            assertThat(it["dpsOrganisationId"]).isEqualTo("$corporateAndOrganisationId")
            assertThat(it["nomisCorporateType"]).isEqualTo(nomisCorporateType)
          },
          isNull(),
        )
      }
    }

    @Nested
    inner class WhenUpdatedInNomis {

      @BeforeEach
      fun setUp() {
        nomisApiMock.stubGetCorporateOrganisation(
          corporateId = corporateAndOrganisationId,
          corporate = corporateOrganisation().copy(
            types = listOf(
              CorporateOrganisationType(
                type = CodeDescription("TEA", "Teacher"),
                audit = NomisAudit(
                  createUsername = "J.SPEAK",
                  createDatetime = LocalDateTime.parse("2024-09-01T13:31"),
                  modifyUserId = "T.SMITH",
                  modifyDatetime = LocalDateTime.parse("2024-10-01T13:31"),
                ),
              ),
              CorporateOrganisationType(
                type = CodeDescription("BSKILLS", "Basic Skills Provider"),
                audit = NomisAudit(
                  createUsername = "J.SPEAK",
                  createDatetime = LocalDateTime.parse("2024-10-01T13:31"),
                  modifyUserId = "T.SMITH",
                  modifyDatetime = LocalDateTime.parse("2024-11-01T13:31"),
                ),
              ),
            ),
          ),
        )
        dpsApiMock.stubUpdateOrganisationTypes(corporateAndOrganisationId)

        organisationsOffenderEventsQueue.sendMessage(
          corporateTypeEvent(
            eventType = "CORPORATE_TYPES-UPDATED",
            corporateId = corporateAndOrganisationId,
            corporateType = nomisCorporateType,
            auditModuleName = "OCUCORPT",
          ),
        ).also { waitForAnyProcessingToComplete() }
      }

      @Test
      fun `will track telemetry`() {
        verify(telemetryClient).trackEvent(
          eq("organisations-type-synchronisation-updated-success"),
          check {
            assertThat(it["nomisCorporateId"]).isEqualTo("$corporateAndOrganisationId")
            assertThat(it["dpsOrganisationId"]).isEqualTo("$corporateAndOrganisationId")
            assertThat(it["nomisCorporateType"]).isEqualTo(nomisCorporateType)
          },
          isNull(),
        )
      }

      @Test
      fun `will update all the types in DPS from the NOMIS types`() {
        dpsApiMock.verify(putRequestedFor(urlPathEqualTo("/sync/organisation-types/$corporateAndOrganisationId")))
        val request: SyncUpdateTypesRequest = OrganisationsDpsApiExtension.getRequestBody(putRequestedFor(urlPathEqualTo("/sync/organisation-types/$corporateAndOrganisationId")))
        with(request) {
          assertThat(types).hasSize(2)
          assertThat(types[0].type).isEqualTo("TEA")
          assertThat(types[0].createdBy).isEqualTo("J.SPEAK")
          assertThat(types[0].createdTime).isEqualTo("2024-09-01T13:31")
          assertThat(types[0].updatedBy).isEqualTo("T.SMITH")
          assertThat(types[0].updatedTime).isEqualTo("2024-10-01T13:31")
          assertThat(types[1].type).isEqualTo("BSKILLS")
          assertThat(types[1].createdBy).isEqualTo("J.SPEAK")
          assertThat(types[1].createdTime).isEqualTo("2024-10-01T13:31")
          assertThat(types[1].updatedBy).isEqualTo("T.SMITH")
          assertThat(types[1].updatedTime).isEqualTo("2024-11-01T13:31")
        }
      }
    }
  }

  @Nested
  @DisplayName("CORPORATE_TYPES-DELETED")
  inner class CorporateTypeDeleted {
    private val corporateAndOrganisationId = 123456L
    private val nomisCorporateType = "BSKILLS"

    @Nested
    inner class WhenDeletedInDpsByOrganisationSyncService {
      @BeforeEach
      fun setUp() {
        organisationsOffenderEventsQueue.sendMessage(
          corporateTypeEvent(
            eventType = "CORPORATE_TYPES-DELETED",
            corporateId = corporateAndOrganisationId,
            corporateType = nomisCorporateType,
            auditModuleName = "DPS_SYNCHRONISATION_ORGANISATION",
          ),
        ).also { waitForAnyProcessingToComplete() }
      }

      @Test
      fun `will not update the types in DPS`() {
        dpsApiMock.verify(0, putRequestedFor(urlPathMatching("/sync/organisation-types/$corporateAndOrganisationId")))
      }

      @Test
      fun `will track telemetry`() {
        verify(telemetryClient).trackEvent(
          eq("organisations-type-synchronisation-deleted-skipped"),
          check {
            assertThat(it["nomisCorporateId"]).isEqualTo("$corporateAndOrganisationId")
            assertThat(it["dpsOrganisationId"]).isEqualTo("$corporateAndOrganisationId")
            assertThat(it["nomisCorporateType"]).isEqualTo(nomisCorporateType)
          },
          isNull(),
        )
      }
    }

    @Nested
    inner class WhenDeletedInNomis {

      @BeforeEach
      fun setUp() {
        nomisApiMock.stubGetCorporateOrganisation(
          corporateId = corporateAndOrganisationId,
          corporate = corporateOrganisation().copy(
            types = listOf(
              CorporateOrganisationType(
                type = CodeDescription("TEA", "Teacher"),
                audit = NomisAudit(
                  createUsername = "J.SPEAK",
                  createDatetime = LocalDateTime.parse("2024-09-01T13:31"),
                  modifyUserId = "T.SMITH",
                  modifyDatetime = LocalDateTime.parse("2024-10-01T13:31"),
                ),
              ),
            ),
          ),
        )
        dpsApiMock.stubUpdateOrganisationTypes(corporateAndOrganisationId)

        organisationsOffenderEventsQueue.sendMessage(
          corporateTypeEvent(
            eventType = "CORPORATE_TYPES-DELETED",
            corporateId = corporateAndOrganisationId,
            corporateType = nomisCorporateType,
            auditModuleName = "OCUCORPT",
          ),
        ).also { waitForAnyProcessingToComplete() }
      }

      @Test
      fun `will track telemetry`() {
        verify(telemetryClient).trackEvent(
          eq("organisations-type-synchronisation-deleted-success"),
          check {
            assertThat(it["nomisCorporateId"]).isEqualTo("$corporateAndOrganisationId")
            assertThat(it["dpsOrganisationId"]).isEqualTo("$corporateAndOrganisationId")
            assertThat(it["nomisCorporateType"]).isEqualTo(nomisCorporateType)
          },
          isNull(),
        )
      }

      @Test
      fun `will update all the types in DPS from the NOMIS types`() {
        dpsApiMock.verify(putRequestedFor(urlPathEqualTo("/sync/organisation-types/$corporateAndOrganisationId")))
        val request: SyncUpdateTypesRequest = OrganisationsDpsApiExtension.getRequestBody(putRequestedFor(urlPathEqualTo("/sync/organisation-types/$corporateAndOrganisationId")))
        with(request) {
          assertThat(types).hasSize(1)
          assertThat(types[0].type).isEqualTo("TEA")
          assertThat(types[0].createdBy).isEqualTo("J.SPEAK")
          assertThat(types[0].createdTime).isEqualTo("2024-09-01T13:31")
          assertThat(types[0].updatedBy).isEqualTo("T.SMITH")
          assertThat(types[0].updatedTime).isEqualTo("2024-10-01T13:31")
        }
      }
    }
  }
}

fun corporateEvent(
  eventType: String,
  corporateId: Long,
  auditModuleName: String = "OUMAGENC",
) = // language=JSON
  """{
    "MessageId": "ae06c49e-1f41-4b9f-b2f2-dcca610d02cd", "Type": "Notification", "Timestamp": "2019-10-21T14:01:18.500Z", 
    "Message": "{\"eventId\":\"5958295\",\"eventType\":\"$eventType\",\"eventDatetime\":\"2019-10-21T15:00:25.489964\",\"corporateId\": \"$corporateId\",\"auditModuleName\":\"$auditModuleName\",\"nomisEventType\":\"$eventType\" }",
    "TopicArn": "arn:aws:sns:eu-west-1:000000000000:offender_events", 
    "MessageAttributes": {
      "eventType": {"Type": "String", "Value": "$eventType"}, 
      "id": {"Type": "String", "Value": "8b07cbd9-0820-0a0f-c32f-a9429b618e0b"}, 
      "contentType": {"Type": "String", "Value": "text/plain;charset=UTF-8"}, 
      "timestamp": {"Type": "Number.java.lang.Long", "Value": "1571666478344"}
    }
}
  """.trimIndent()

fun corporateAddressEvent(
  eventType: String,
  corporateId: Long,
  addressId: Long,
  auditModuleName: String = "OCDOAPOP",
) = // language=JSON
  """{
    "MessageId": "ae06c49e-1f41-4b9f-b2f2-dcca610d02cd", "Type": "Notification", "Timestamp": "2019-10-21T14:01:18.500Z", 
    "Message": "{\"eventId\":\"5958295\",\"eventType\":\"$eventType\",\"eventDatetime\":\"2019-10-21T15:00:25.489964\",\"addressId\": \"$addressId\",\"corporateId\": \"$corporateId\",\"auditModuleName\":\"$auditModuleName\",\"nomisEventType\":\"$eventType\" }",
    "TopicArn": "arn:aws:sns:eu-west-1:000000000000:offender_events", 
    "MessageAttributes": {
      "eventType": {"Type": "String", "Value": "$eventType"}, 
      "id": {"Type": "String", "Value": "8b07cbd9-0820-0a0f-c32f-a9429b618e0b"}, 
      "contentType": {"Type": "String", "Value": "text/plain;charset=UTF-8"}, 
      "timestamp": {"Type": "Number.java.lang.Long", "Value": "1571666478344"}
    }
}
  """.trimIndent()

fun corporatePhoneEvent(
  eventType: String,
  corporateId: Long,
  phoneId: Long,
  auditModuleName: String = "OUMAGENC",
) = // language=JSON
  """{
    "MessageId": "ae06c49e-1f41-4b9f-b2f2-dcca610d02cd", "Type": "Notification", "Timestamp": "2019-10-21T14:01:18.500Z", 
    "Message": "{\"eventId\":\"5958295\",\"eventType\":\"$eventType\",\"eventDatetime\":\"2019-10-21T15:00:25.489964\",\"phoneId\": \"$phoneId\",\"isAddress\": \"false\",\"corporateId\": \"$corporateId\",\"auditModuleName\":\"$auditModuleName\",\"nomisEventType\":\"$eventType\" }",
    "TopicArn": "arn:aws:sns:eu-west-1:000000000000:offender_events", 
    "MessageAttributes": {
      "eventType": {"Type": "String", "Value": "$eventType"}, 
      "id": {"Type": "String", "Value": "8b07cbd9-0820-0a0f-c32f-a9429b618e0b"}, 
      "contentType": {"Type": "String", "Value": "text/plain;charset=UTF-8"}, 
      "timestamp": {"Type": "Number.java.lang.Long", "Value": "1571666478344"}
    }
}
  """.trimIndent()
fun corporateAddressPhoneEvent(
  eventType: String,
  corporateId: Long,
  phoneId: Long,
  addressId: Long,
  auditModuleName: String = "OUMAGENC",
) = // language=JSON
  """{
    "MessageId": "ae06c49e-1f41-4b9f-b2f2-dcca610d02cd", "Type": "Notification", "Timestamp": "2019-10-21T14:01:18.500Z", 
    "Message": "{\"eventId\":\"5958295\",\"eventType\":\"$eventType\",\"eventDatetime\":\"2019-10-21T15:00:25.489964\",\"phoneId\": \"$phoneId\",\"isAddress\": \"true\",\"addressId\": \"$addressId\",\"corporateId\": \"$corporateId\",\"auditModuleName\":\"$auditModuleName\",\"nomisEventType\":\"$eventType\" }",
    "TopicArn": "arn:aws:sns:eu-west-1:000000000000:offender_events", 
    "MessageAttributes": {
      "eventType": {"Type": "String", "Value": "$eventType"}, 
      "id": {"Type": "String", "Value": "8b07cbd9-0820-0a0f-c32f-a9429b618e0b"}, 
      "contentType": {"Type": "String", "Value": "text/plain;charset=UTF-8"}, 
      "timestamp": {"Type": "Number.java.lang.Long", "Value": "1571666478344"}
    }
}
  """.trimIndent()

fun corporateInternetAddressEvent(
  eventType: String,
  corporateId: Long,
  internetAddressId: Long,
  auditModuleName: String = "OUMAGENC",
) = // language=JSON
  """{
    "MessageId": "ae06c49e-1f41-4b9f-b2f2-dcca610d02cd", "Type": "Notification", "Timestamp": "2019-10-21T14:01:18.500Z", 
    "Message": "{\"eventId\":\"5958295\",\"eventType\":\"$eventType\",\"eventDatetime\":\"2019-10-21T15:00:25.489964\",\"internetAddressId\": \"$internetAddressId\",\"corporateId\": \"$corporateId\",\"auditModuleName\":\"$auditModuleName\",\"nomisEventType\":\"$eventType\" }",
    "TopicArn": "arn:aws:sns:eu-west-1:000000000000:offender_events", 
    "MessageAttributes": {
      "eventType": {"Type": "String", "Value": "$eventType"}, 
      "id": {"Type": "String", "Value": "8b07cbd9-0820-0a0f-c32f-a9429b618e0b"}, 
      "contentType": {"Type": "String", "Value": "text/plain;charset=UTF-8"}, 
      "timestamp": {"Type": "Number.java.lang.Long", "Value": "1571666478344"}
    }
}
  """.trimIndent()

fun corporateTypeEvent(
  eventType: String,
  corporateId: Long,
  corporateType: String,
  auditModuleName: String = "OCUCORPT",
) = // language=JSON
  """{
    "MessageId": "ae06c49e-1f41-4b9f-b2f2-dcca610d02cd", "Type": "Notification", "Timestamp": "2019-10-21T14:01:18.500Z", 
    "Message": "{\"eventId\":\"5958295\",\"eventType\":\"$eventType\",\"eventDatetime\":\"2019-10-21T15:00:25.489964\",\"corporateType\": \"$corporateType\",\"corporateId\": \"$corporateId\",\"auditModuleName\":\"$auditModuleName\",\"nomisEventType\":\"$eventType\" }",
    "TopicArn": "arn:aws:sns:eu-west-1:000000000000:offender_events", 
    "MessageAttributes": {
      "eventType": {"Type": "String", "Value": "$eventType"}, 
      "id": {"Type": "String", "Value": "8b07cbd9-0820-0a0f-c32f-a9429b618e0b"}, 
      "contentType": {"Type": "String", "Value": "text/plain;charset=UTF-8"}, 
      "timestamp": {"Type": "Number.java.lang.Long", "Value": "1571666478344"}
    }
}
  """.trimIndent()
