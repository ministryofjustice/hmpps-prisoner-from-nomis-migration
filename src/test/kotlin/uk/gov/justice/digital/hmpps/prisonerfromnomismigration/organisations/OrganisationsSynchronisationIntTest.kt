package uk.gov.justice.digital.hmpps.prisonerfromnomismigration.organisations

import com.github.tomakehurst.wiremock.client.WireMock.deleteRequestedFor
import com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor
import com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor
import com.github.tomakehurst.wiremock.client.WireMock.putRequestedFor
import com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo
import com.github.tomakehurst.wiremock.client.WireMock.urlPathMatching
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.mockito.Mockito.eq
import org.mockito.kotlin.isNull
import org.mockito.kotlin.verify
import org.springframework.beans.factory.annotation.Autowired
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.integration.SqsIntegrationTestBase
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.integration.sendMessage
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomismappings.model.CorporateAddressMappingDto
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomismappings.model.CorporateMappingDto
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomismappings.model.CorporateMappingDto.MappingType.MIGRATED
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomismappings.model.CorporateMappingDto.MappingType.NOMIS_CREATED
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomismappings.model.DuplicateErrorContentObject
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomismappings.model.DuplicateMappingErrorResponse
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomissync.model.CodeDescription
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomissync.model.CorporateAddress
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomissync.model.NomisAudit
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.organisations.OrganisationsDpsApiExtension.Companion.dpsOrganisationsServer
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.organisations.OrganisationsDpsApiMockServer.Companion.syncCreateOrganisationAddressResponse
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.organisations.OrganisationsDpsApiMockServer.Companion.syncCreateOrganisationResponse
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.wiremock.MappingApiExtension.Companion.mappingApi
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.wiremock.withRequestBodyJsonPath
import java.time.LocalDate

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
    inner class WhenCreatedInDps {
      @BeforeEach
      fun setUp() {
        organisationsOffenderEventsQueue.sendMessage(
          corporateEvent(
            eventType = "CORPORATE-INSERTED",
            corporateId = corporateAndOrganisationId,
            auditModuleName = "DPS_SYNCHRONISATION",
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
          org.mockito.kotlin.check {
            assertThat(it["nomisCorporateId"]).isEqualTo(corporateAndOrganisationId.toString())
          },
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
          ),
        )
        dpsApiMock.stubCreateOrganisation(syncCreateOrganisationResponse().copy(organisationId = corporateAndOrganisationId))
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
          org.mockito.kotlin.check {
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
        mappingApiMock.stubGetByNomisCorporateIdOrNull(nomisCorporateId = corporateAndOrganisationId, mapping = CorporateMappingDto(dpsId = "$corporateAndOrganisationId", nomisId = corporateAndOrganisationId, mappingType = CorporateMappingDto.MappingType.NOMIS_CREATED))
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
          org.mockito.kotlin.check {
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
        dpsApiMock.stubCreateOrganisation(syncCreateOrganisationResponse().copy(organisationId = corporateAndOrganisationId))
        mappingApiMock.stubCreateCorporateMapping(
          error = DuplicateMappingErrorResponse(
            moreInfo = DuplicateErrorContentObject(
              duplicate = CorporateMappingDto(
                dpsId = corporateAndOrganisationId.toString(),
                nomisId = corporateAndOrganisationId,
                mappingType = NOMIS_CREATED,
              ),
              existing = CorporateMappingDto(
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
          org.mockito.kotlin.check {
            assertThat(it["nomisCorporateId"]).isEqualTo("$corporateAndOrganisationId")
            assertThat(it["dpsOrganisationId"]).isEqualTo("$corporateAndOrganisationId")
          },
          isNull(),
        )
        verify(telemetryClient).trackEvent(
          eq("from-nomis-sync-organisations-duplicate"),
          org.mockito.kotlin.check {
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
        dpsApiMock.stubCreateOrganisation(syncCreateOrganisationResponse().copy(organisationId = corporateAndOrganisationId))
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
          org.mockito.kotlin.check {
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
    inner class WhenUpdatedInDps {
      @BeforeEach
      fun setUp() {
        organisationsOffenderEventsQueue.sendMessage(
          corporateEvent(
            eventType = "CORPORATE-UPDATED",
            corporateId = nomisCorporateId,
            auditModuleName = "DPS_SYNCHRONISATION",
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
          org.mockito.kotlin.check {
            assertThat(it["nomisCorporateId"]).isEqualTo(nomisCorporateId.toString())
          },
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
          mapping = CorporateMappingDto(dpsId = dpsOrganisationId.toString(), nomisId = nomisCorporateId, mappingType = MIGRATED),
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
          org.mockito.kotlin.check {
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
          mapping = CorporateMappingDto(dpsId = dpsOrganisationId.toString(), nomisId = nomisCorporateId, mappingType = MIGRATED),
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
          org.mockito.kotlin.check {
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
          org.mockito.kotlin.check {
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
    inner class WhenCreatedInDps {
      @BeforeEach
      fun setUp() {
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
      fun `will not create address in DPS`() {
        dpsApiMock.verify(0, postRequestedFor(urlPathEqualTo("/sync/organisation-address")))
      }

      @Test
      fun `will track telemetry`() {
        verify(telemetryClient).trackEvent(
          eq("organisations-address-synchronisation-created-skipped"),
          org.mockito.kotlin.check {
            assertThat(it["nomisCorporateId"]).isEqualTo("$corporateAndOrganisationId")
            assertThat(it["dpsOrganisationId"]).isEqualTo("$corporateAndOrganisationId")
            assertThat(it["nomisAddressId"]).isEqualTo("$nomisAddressId")
          },
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
                createDatetime = "2024-09-01T13:31",
                modifyUserId = "T.SMITH",
                modifyDatetime = "2024-10-01T13:31",
              ),
            ),
          ),
        )
        dpsApiMock.stubCreateOrganisationAddress(syncCreateOrganisationAddressResponse().copy(organisationAddressId = dpsOrganisationAddressId))
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
        val request: SyncCreateOrganisationAddressRequest = OrganisationsDpsApiExtension.getRequestBody(
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
          assertThat(verified).isNull()
          assertThat(mailFlag).isTrue()
          assertThat(startDate).isEqualTo(LocalDate.parse("2021-01-01"))
          assertThat(endDate).isEqualTo(LocalDate.parse("2025-01-01"))
          assertThat(noFixedAddress).isFalse()
          assertThat(comments).isEqualTo("nice area")
          assertThat(servicesAddress).isTrue()
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
          org.mockito.kotlin.check {
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
        mappingApiMock.stubGetByNomisAddressIdOrNull(nomisAddressId = nomisAddressId, mapping = CorporateAddressMappingDto(dpsId = "$dpsOrganisationAddressId", nomisId = nomisAddressId, mappingType = CorporateAddressMappingDto.MappingType.NOMIS_CREATED))
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
          org.mockito.kotlin.check {
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

        dpsApiMock.stubCreateOrganisationAddress(syncCreateOrganisationAddressResponse().copy(organisationAddressId = dpsOrganisationAddressId))
        mappingApiMock.stubCreateAddressMapping(
          error = DuplicateMappingErrorResponse(
            moreInfo = DuplicateErrorContentObject(
              duplicate = CorporateAddressMappingDto(
                dpsId = "$dpsOrganisationAddressId",
                nomisId = nomisAddressId,
                mappingType = CorporateAddressMappingDto.MappingType.NOMIS_CREATED,
              ),
              existing = CorporateAddressMappingDto(
                dpsId = "9999",
                nomisId = nomisAddressId,
                mappingType = CorporateAddressMappingDto.MappingType.NOMIS_CREATED,
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
          org.mockito.kotlin.check {
            assertThat(it["nomisCorporateId"]).isEqualTo("$corporateAndOrganisationId")
            assertThat(it["dpsOrganisationId"]).isEqualTo("$corporateAndOrganisationId")
          },
          isNull(),
        )
        verify(telemetryClient).trackEvent(
          eq("from-nomis-sync-organisations-duplicate"),
          org.mockito.kotlin.check {
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
        dpsApiMock.stubCreateOrganisationAddress(syncCreateOrganisationAddressResponse().copy(organisationAddressId = dpsOrganisationAddressId))
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
          org.mockito.kotlin.check {
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
    inner class WhenUpdatedInDps {
      @BeforeEach
      fun setUp() {
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
      fun `will not update the address in DPS`() {
        dpsApiMock.verify(0, putRequestedFor(urlPathMatching("/sync/organisation-address/.*")))
      }

      @Test
      fun `will track telemetry`() {
        verify(telemetryClient).trackEvent(
          eq("organisations-address-synchronisation-updated-skipped"),
          org.mockito.kotlin.check {
            assertThat(it["nomisCorporateId"]).isEqualTo("$corporateAndOrganisationId")
            assertThat(it["dpsOrganisationId"]).isEqualTo("$corporateAndOrganisationId")
            assertThat(it["nomisAddressId"]).isEqualTo("$nomisAddressId")
          },
          isNull(),
        )
      }
    }

    @Nested
    inner class WhenUpdatedInNomis {

      @BeforeEach
      fun setUp() {
        mappingApiMock.stubGetByNomisAddressId(
          nomisAddressId = nomisAddressId,
          mapping = CorporateAddressMappingDto(dpsId = dpsOrganisationAddressId.toString(), nomisId = nomisAddressId, mappingType = CorporateAddressMappingDto.MappingType.MIGRATED),
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
                createDatetime = "2024-09-01T13:31",
                modifyUserId = "T.SMITH",
                modifyDatetime = "2024-10-01T13:31",
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
          org.mockito.kotlin.check {
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
        val request: SyncUpdateOrganisationAddressRequest = OrganisationsDpsApiExtension.getRequestBody(putRequestedFor(urlPathEqualTo("/sync/organisation-address/$dpsOrganisationAddressId")))
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
          assertThat(verified).isNull()
          assertThat(mailFlag).isTrue()
          assertThat(startDate).isEqualTo(LocalDate.parse("2021-01-01"))
          assertThat(endDate).isEqualTo(LocalDate.parse("2025-01-01"))
          assertThat(noFixedAddress).isFalse()
          assertThat(comments).isEqualTo("nice area")
          assertThat(servicesAddress).isTrue()
          assertThat(businessHours).isEqualTo("10am to 10pm Monday to Friday")
          assertThat(contactPersonName).isEqualTo("Bob Brown")
          assertThat(updatedBy).isEqualTo("T.SMITH")
          assertThat(updatedTime).isEqualTo("2024-10-01T13:31")
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
