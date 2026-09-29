package uk.gov.justice.digital.hmpps.prisonerfromnomismigration.coreperson

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
import org.mockito.ArgumentMatchers
import org.mockito.kotlin.check
import org.mockito.kotlin.eq
import org.mockito.kotlin.isNull
import org.mockito.kotlin.verify
import org.springframework.beans.factory.annotation.Autowired
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.coreperson.CorePersonCprApiExtension.Companion.getRequestBody
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.coreperson.CorePersonCprApiMockServer.Companion.syncCorePersonEmailResponse
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.coreperson.model.PrisonAddress
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.coreperson.model.PrisonContact
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.integration.sendMessage
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomismappings.model.CorePersonAddressMappingDto
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomismappings.model.CorePersonEmailAddressMappingDto
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomismappings.model.DuplicateErrorContentObject
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomismappings.model.DuplicateMappingErrorResponse
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomisprisoner.model.OffenderAddress
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.wiremock.MappingApiExtension.Companion.mappingApi
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.wiremock.NomisApiExtension.Companion.nomisApi
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.wiremock.withRequestBodyJsonPath
import java.time.LocalDateTime

class CorePersonSynchronisationAddressContactIntTest(
  @Autowired private val nomisApiMock: CorePersonNomisApiMockServer,
  @Autowired private val mappingApiMock: CorePersonMappingApiMockServer,
) : CorePersonIntegrationTestBase() {

  @Nested
  @DisplayName("ADDRESSES_OFFENDER-INSERTED")
  inner class OffenderAddressAdded {
    private val ownerId = 1234L
    private val addressId = 3456L
    private val cprAddressId = "cpr-address-id"
    private val prisonNumber = "A1234BC"

    @Nested
    inner class WhenCreatedInCpr {
      @BeforeEach
      fun setUp() {
        sendAddressEvent("ADDRESSES_OFFENDER-INSERTED", auditModuleName = "DPS_SYNCHRONISATION")
          .also { waitForAnyProcessingToComplete("coreperson-address-synchronisation-created-skipped") }
      }

      @Test
      fun `will not create the address in CPR`() {
        corePersonCprApiMockServer.verify(0, postRequestedFor(urlPathEqualTo("/syscon-sync/person/$prisonNumber/address")))
      }

      @Test
      fun `will track telemetry`() {
        verify(telemetryClient).trackEvent(
          eq("coreperson-address-synchronisation-created-skipped"),
          check {
            assertThat(it["prisonNumber"]).isEqualTo(prisonNumber)
            assertThat(it["nomisOffenderId"]).isEqualTo(ownerId.toString())
            assertThat(it["nomisAddressId"]).isEqualTo(addressId.toString())
          },
          isNull(),
        )
      }
    }

    @Nested
    inner class WhenCreatedInNomis {
      @BeforeEach
      fun setUp() {
        mappingApiMock.stubGetByNomisAddressIdOrNull(addressId, null)
        nomisApiMock.stubGetOffenderAddress(ownerId, addressId)
        corePersonCprApiMockServer.stubSyncCreateAddress(
          prisonNumber = prisonNumber,
          addressId = addressId,
          cprAddressId = cprAddressId,
        )
        mappingApiMock.stubCreateAddressMapping()
        sendAddressEvent("ADDRESSES_OFFENDER-INSERTED")
          .also { waitForAnyProcessingToComplete("coreperson-address-synchronisation-created-success") }
      }

      @Test
      fun `will check if mapping already exists`() {
        mappingApi.verify(getRequestedFor(urlPathEqualTo("/mapping/core-person/address/nomis-address-id/$addressId")))
      }

      @Test
      fun `will retrieve the address from NOMIS`() {
        nomisApi.verify(getRequestedFor(urlPathEqualTo("/core-person/$ownerId/address/$addressId")))
      }

      @Test
      fun `will create the address in CPR`() {
        val addressRequestPattern = postRequestedFor(urlPathEqualTo("/syscon-sync/person/$prisonNumber/address"))
        corePersonCprApiMockServer.verify(addressRequestPattern)
        val request: PrisonAddress = getRequestBody(addressRequestPattern)
        assertThat(request.nomisAddressId).isEqualTo(addressId)
      }

      @Test
      fun `will create the address mapping`() {
        mappingApi.verify(
          postRequestedFor(urlPathEqualTo("/mapping/core-person/address"))
            .withRequestBodyJsonPath("mappingType", "NOMIS_CREATED")
            .withRequestBodyJsonPath("cprId", cprAddressId)
            .withRequestBodyJsonPath("nomisId", "$addressId")
            .withRequestBodyJsonPath("nomisPrisonNumber", prisonNumber),
        )
      }

      @Test
      fun `will track telemetry`() {
        verify(telemetryClient).trackEvent(
          eq("coreperson-address-synchronisation-created-success"),
          check {
            assertThat(it["prisonNumber"]).isEqualTo(prisonNumber)
            assertThat(it["nomisOffenderId"]).isEqualTo(ownerId.toString())
            assertThat(it["nomisAddressId"]).isEqualTo(addressId.toString())
            assertThat(it["cprAddressId"]).isEqualTo(cprAddressId)
          },
          isNull(),
        )
      }
    }

    @Nested
    inner class WhenAlreadyCreated {
      @BeforeEach
      fun setUp() {
        mappingApiMock.stubGetByNomisAddressIdOrNull(
          addressId,
          CorePersonAddressMappingDto(
            cprId = cprAddressId,
            nomisId = addressId,
            nomisPrisonNumber = prisonNumber,
            mappingType = CorePersonAddressMappingDto.MappingType.NOMIS_CREATED,
          ),
        )
        sendAddressEvent("ADDRESSES_OFFENDER-INSERTED")
          .also { waitForAnyProcessingToComplete("coreperson-address-synchronisation-created-ignored") }
      }

      @Test
      fun `will not create the address in CPR`() {
        corePersonCprApiMockServer.verify(0, postRequestedFor(urlPathEqualTo("/syscon-sync/person/$prisonNumber/address")))
      }

      @Test
      fun `will track telemetry`() {
        verify(telemetryClient).trackEvent(
          eq("coreperson-address-synchronisation-created-ignored"),
          check {
            assertThat(it["prisonNumber"]).isEqualTo(prisonNumber)
            assertThat(it["nomisOffenderId"]).isEqualTo(ownerId.toString())
            assertThat(it["nomisAddressId"]).isEqualTo(addressId.toString())
            assertThat(it["cprAddressId"]).isEqualTo(cprAddressId)
          },
          isNull(),
        )
      }
    }

    @Nested
    inner class WhenDuplicateMapping {
      @BeforeEach
      fun setUp() {
        mappingApiMock.stubGetByNomisAddressIdOrNull(addressId, null)
        nomisApiMock.stubGetOffenderAddress(ownerId, addressId)
        corePersonCprApiMockServer.stubSyncCreateAddress(
          prisonNumber = prisonNumber,
          addressId = addressId,
          cprAddressId = cprAddressId,
        )
        mappingApiMock.stubCreateAddressMapping(
          error = DuplicateMappingErrorResponse(
            moreInfo = DuplicateErrorContentObject(
              duplicate = CorePersonAddressMappingDto(
                cprId = cprAddressId,
                nomisId = addressId,
                nomisPrisonNumber = prisonNumber,
                mappingType = CorePersonAddressMappingDto.MappingType.NOMIS_CREATED,
              ),
              existing = CorePersonAddressMappingDto(
                cprId = "existing-cpr-address-id",
                nomisId = addressId,
                nomisPrisonNumber = prisonNumber,
                mappingType = CorePersonAddressMappingDto.MappingType.NOMIS_CREATED,
              ),
            ),
            errorCode = 1409,
            status = DuplicateMappingErrorResponse.Status._409_CONFLICT,
            userMessage = "Duplicate mapping",
          ),
        )
        sendAddressEvent("ADDRESSES_OFFENDER-INSERTED")
          .also { waitForAnyProcessingToComplete("coreperson-address-mapping-synchronisation-duplicate") }
      }

      @Test
      fun `will create the address in CPR once`() {
        corePersonCprApiMockServer.verify(1, postRequestedFor(urlPathEqualTo("/syscon-sync/person/$prisonNumber/address")))
      }

      @Test
      fun `will attempt to create the mapping once`() {
        mappingApi.verify(
          1,
          postRequestedFor(urlPathEqualTo("/mapping/core-person/address"))
            .withRequestBodyJsonPath("mappingType", "NOMIS_CREATED")
            .withRequestBodyJsonPath("cprId", cprAddressId)
            .withRequestBodyJsonPath("nomisId", "$addressId"),
        )
      }

      @Test
      fun `will track telemetry for both overall success and duplicate mapping`() {
        verify(telemetryClient).trackEvent(
          eq("coreperson-address-synchronisation-created-success"),
          check {
            assertThat(it["prisonNumber"]).isEqualTo(prisonNumber)
            assertThat(it["nomisOffenderId"]).isEqualTo(ownerId.toString())
            assertThat(it["nomisAddressId"]).isEqualTo(addressId.toString())
            assertThat(it["cprAddressId"]).isEqualTo(cprAddressId)
          },
          isNull(),
        )
        verify(telemetryClient).trackEvent(
          eq("coreperson-address-mapping-synchronisation-duplicate"),
          check {
            assertThat(it["nomisPrisonNumber"]).isEqualTo(prisonNumber)
            assertThat(it["existingNomisAddressId"]).isEqualTo(addressId.toString())
            assertThat(it["existingCprAddressId"]).isEqualTo("existing-cpr-address-id")
            assertThat(it["duplicateNomisAddressId"]).isEqualTo(addressId.toString())
            assertThat(it["duplicateCprAddressId"]).isEqualTo(cprAddressId)
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
        mappingApiMock.stubGetByNomisAddressIdOrNull(addressId, null)
        nomisApiMock.stubGetOffenderAddress(ownerId, addressId)
        corePersonCprApiMockServer.stubSyncCreateAddress(
          prisonNumber = prisonNumber,
          addressId = addressId,
          cprAddressId = cprAddressId,
        )
        mappingApiMock.stubCreateAddressMappingFollowedBySuccess()
        sendAddressEvent("ADDRESSES_OFFENDER-INSERTED")
          .also { waitForAnyProcessingToComplete("coreperson-address-mapping-synchronisation-created") }
      }

      @Test
      fun `will create the address in CPR once`() {
        corePersonCprApiMockServer.verify(1, postRequestedFor(urlPathEqualTo("/syscon-sync/person/$prisonNumber/address")))
      }

      @Test
      fun `will create the mapping between the CPR and NOMIS records twice`() {
        mappingApi.verify(
          2,
          postRequestedFor(urlPathEqualTo("/mapping/core-person/address"))
            .withRequestBodyJsonPath("mappingType", "NOMIS_CREATED")
            .withRequestBodyJsonPath("cprId", cprAddressId)
            .withRequestBodyJsonPath("nomisId", "$addressId"),
        )
      }

      @Test
      fun `will track telemetry`() {
        verify(telemetryClient).trackEvent(
          eq("coreperson-address-mapping-synchronisation-created"),
          check {
            assertThat(it["prisonNumber"]).isEqualTo(prisonNumber)
            assertThat(it["nomisOffenderId"]).isEqualTo(ownerId.toString())
            assertThat(it["nomisAddressId"]).isEqualTo(addressId.toString())
            assertThat(it["cprAddressId"]).isEqualTo(cprAddressId)
          },
          isNull(),
        )
      }
    }
  }

  @Nested
  @DisplayName("ADDRESSES_OFFENDER-UPDATED")
  inner class OffenderAddressUpdated {
    private val ownerId = 1234L
    private val addressId = 3456L
    private val prisonNumber = "A1234BC"
    private val cprAddressId = "cpr-address-id"

    @Nested
    inner class WhenUpdatedInCpr {
      @BeforeEach
      fun setUp() {
        sendAddressEvent("ADDRESSES_OFFENDER-UPDATED", ownerId, addressId, auditModuleName = "DPS_SYNCHRONISATION")
          .also { waitForAnyProcessingToComplete("coreperson-address-synchronisation-updated-skipped") }
      }

      @Test
      fun `will not update the address in CPR`() {
        corePersonCprApiMockServer.verify(
          0,
          putRequestedFor(urlPathEqualTo("/syscon-sync/person/$prisonNumber/address/$cprAddressId")),
        )
      }

      @Test
      fun `will track telemetry`() {
        verify(telemetryClient).trackEvent(
          eq("coreperson-address-synchronisation-updated-skipped"),
          check {
            assertThat(it["prisonNumber"]).isEqualTo(prisonNumber)
            assertThat(it["nomisOffenderId"]).isEqualTo(ownerId.toString())
            assertThat(it["nomisAddressId"]).isEqualTo(addressId.toString())
          },
          isNull(),
        )
      }
    }

    @Nested
    inner class WhenUpdatedInNomis {
      @BeforeEach
      fun setUp() {
        mappingApiMock.stubGetByNomisAddressIdOrNull(
          addressId,
          CorePersonAddressMappingDto(
            cprId = cprAddressId,
            nomisId = addressId,
            nomisPrisonNumber = prisonNumber,
            mappingType = CorePersonAddressMappingDto.MappingType.NOMIS_CREATED,
          ),
        )
        nomisApiMock.stubGetOffenderAddress(
          offenderId = ownerId,
          addressId = addressId,
          address = nomisAddress(addressId).copy(
            lastUpdatedByUsername = "T.SWIFT",
            lastUpdatedDateTime = LocalDateTime.parse("2024-10-01T13:31"),
          ),
        )
        corePersonCprApiMockServer.stubSyncUpdateAddress(prisonNumber, cprAddressId)
        sendAddressEvent("ADDRESSES_OFFENDER-UPDATED", ownerId, addressId)
          .also { waitForAnyProcessingToComplete("coreperson-address-synchronisation-updated-success") }
      }

      @Test
      fun `will retrieve the address from NOMIS and update CPR`() {
        nomisApi.verify(getRequestedFor(urlPathEqualTo("/core-person/$ownerId/address/$addressId")))
        val updateRequestPattern = putRequestedFor(urlPathEqualTo("/syscon-sync/person/$prisonNumber/address/$cprAddressId"))
        corePersonCprApiMockServer.verify(updateRequestPattern)
        val request: PrisonAddress = getRequestBody(updateRequestPattern)
        assertThat(request.nomisAddressId).isEqualTo(addressId)
        assertThat(request.modifyUserId).isEqualTo("T.SWIFT")
        assertThat(request.modifyDateTime).isEqualTo(LocalDateTime.parse("2024-10-01T13:31"))
      }

      @Test
      fun `will track telemetry`() {
        verify(telemetryClient).trackEvent(
          eq("coreperson-address-synchronisation-updated-success"),
          check {
            assertThat(it["prisonNumber"]).isEqualTo(prisonNumber)
            assertThat(it["nomisOffenderId"]).isEqualTo(ownerId.toString())
            assertThat(it["nomisAddressId"]).isEqualTo(addressId.toString())
            assertThat(it["cprAddressId"]).isEqualTo(cprAddressId)
          },
          isNull(),
        )
      }
    }
  }

  @Nested
  @DisplayName("ADDRESSES_OFFENDER-DELETED")
  inner class OffenderAddressDeleted {
    private val ownerId = 1234L
    private val addressId = 3456L
    private val prisonNumber = "A1234BC"
    private val cprAddressId = "cpr-address-id"

    @Nested
    inner class WhenMappingExists {
      @BeforeEach
      fun setUp() {
        mappingApiMock.stubGetByNomisAddressIdOrNull(
          addressId,
          CorePersonAddressMappingDto(
            cprId = cprAddressId,
            nomisId = addressId,
            nomisPrisonNumber = prisonNumber,
            mappingType = CorePersonAddressMappingDto.MappingType.NOMIS_CREATED,
          ),
        )
        mappingApiMock.stubDeleteByNomisAddressId(addressId)
        corePersonCprApiMockServer.stubSyncDeleteAddress(prisonNumber, cprAddressId)
        sendAddressEvent("ADDRESSES_OFFENDER-DELETED", ownerId, addressId)
          .also { waitForAnyProcessingToComplete("coreperson-address-synchronisation-deleted-success") }
      }

      @Test
      fun `will delete the address in CPR`() {
        corePersonCprApiMockServer.verify(
          deleteRequestedFor(urlPathEqualTo("/syscon-sync/person/$prisonNumber/address/$cprAddressId")),
        )
      }

      @Test
      fun `will delete the address mapping`() {
        mappingApi.verify(
          deleteRequestedFor(urlPathEqualTo("/mapping/core-person/address/nomis-address-id/$addressId")),
        )
      }

      @Test
      fun `will track telemetry`() {
        verify(telemetryClient).trackEvent(
          eq("coreperson-address-synchronisation-deleted-success"),
          check {
            assertThat(it["prisonNumber"]).isEqualTo(prisonNumber)
            assertThat(it["nomisOffenderId"]).isEqualTo(ownerId.toString())
            assertThat(it["nomisAddressId"]).isEqualTo(addressId.toString())
            assertThat(it["cprAddressId"]).isEqualTo(cprAddressId)
          },
          isNull(),
        )
      }
    }

    @Nested
    inner class WhenMappingDoesNotExist {
      @BeforeEach
      fun setUp() {
        mappingApiMock.stubGetByNomisAddressIdOrNull(addressId, null)
        sendAddressEvent("ADDRESSES_OFFENDER-DELETED", ownerId, addressId)
          .also { waitForAnyProcessingToComplete("coreperson-address-synchronisation-deleted-ignored") }
      }

      @Test
      fun `will not delete the address in CPR`() {
        corePersonCprApiMockServer.verify(
          0,
          deleteRequestedFor(urlPathEqualTo("/syscon-sync/person/$prisonNumber/address/$cprAddressId")),
        )
      }

      @Test
      fun `will track telemetry`() {
        verify(telemetryClient).trackEvent(
          eq("coreperson-address-synchronisation-deleted-ignored"),
          check {
            assertThat(it["prisonNumber"]).isEqualTo(prisonNumber)
            assertThat(it["nomisOffenderId"]).isEqualTo(ownerId.toString())
            assertThat(it["nomisAddressId"]).isEqualTo(addressId.toString())
          },
          isNull(),
        )
      }
    }
  }

  @Nested
  @DisplayName("OFFENDER_EMAIL-INSERTED")
  inner class PersonEmailAdded {
    private val nomisInternetAddressId = 3456L
    private val nomisOffenderId = 123456L
    private val cprContactEmailId = "cpr-email-id"

    @Nested
    inner class WhenCreatedInCpr {
      @BeforeEach
      fun setUp() {
        sendEmailEvent("OFFENDER_EMAIL-INSERTED", auditModuleName = "DPS_SYNCHRONISATION")
          .also { waitForAnyProcessingToComplete("coreperson-email-synchronisation-created-skipped") }
      }

      @Test
      fun `will not create email in CPR`() {
        corePersonCprApiMockServer.verify(0, postRequestedFor(urlPathEqualTo("/syscon-sync/person/A1234BC/contact")))
      }

      @Test
      fun `will track telemetry`() {
        verify(telemetryClient).trackEvent(
          eq("coreperson-email-synchronisation-created-skipped"),
          check {
            assertThat(it["nomisOffenderId"]).isEqualTo(nomisOffenderId.toString())
            assertThat(it["cprContactId"]).isEqualTo(nomisOffenderId.toString())
            assertThat(it["nomisInternetAddressId"]).isEqualTo(nomisInternetAddressId.toString())
          },
          isNull(),
        )
      }
    }

    @Nested
    inner class WhenCreatedInNomis {
      @BeforeEach
      fun setUp() {
        mappingApiMock.stubGetByNomisEmailIdOrNull(nomisInternetAddressId, null)
        nomisApiMock.stubGetOffenderEmail(nomisOffenderId, nomisInternetAddressId)
        corePersonCprApiMockServer.stubSyncCreateEmail(
          response = syncCorePersonEmailResponse(nomisInternetAddressId, cprContactEmailId),
        )
        mappingApiMock.stubCreateEmailMapping()
        sendEmailEvent("OFFENDER_EMAIL-INSERTED")
          .also { waitForAnyProcessingToComplete("coreperson-email-synchronisation-created-success") }
      }

      @Test
      fun `will check if mapping already exists`() {
        mappingApi.verify(getRequestedFor(urlPathEqualTo("/mapping/core-person/email/nomis-email-address-id/$nomisInternetAddressId")))
      }

      @Test
      fun `will retrieve the email from NOMIS`() {
        nomisApi.verify(getRequestedFor(urlPathEqualTo("/core-person/$nomisOffenderId/email/$nomisInternetAddressId")))
      }

      @Test
      fun `will create the email in CPR`() {
        corePersonCprApiMockServer.verify(postRequestedFor(urlPathEqualTo("/syscon-sync/person/A1234BC/contact")))
        val request: PrisonContact = getRequestBody(postRequestedFor(urlPathEqualTo("/syscon-sync/person/A1234BC/contact")))
        with(request) {
          assertThat(nomisContactId).isEqualTo(nomisInternetAddressId)
          assertThat(type).isEqualTo(PrisonContact.Type.EMAIL)
          assertThat(value).isEqualTo("test@example.com")
          assertThat(createUserId).isEqualTo("SYSTEM")
          assertThat(createDateTime).isEqualTo(LocalDateTime.parse("2001-03-03T00:00"))
        }
      }

      @Test
      fun `will create the email mapping`() {
        mappingApi.verify(
          postRequestedFor(urlPathEqualTo("/mapping/core-person/email"))
            .withRequestBodyJsonPath("mappingType", "NOMIS_CREATED")
            .withRequestBodyJsonPath("cprId", cprContactEmailId)
            .withRequestBodyJsonPath("nomisId", "$nomisInternetAddressId"),
        )
      }

      @Test
      fun `will track telemetry`() {
        verify(telemetryClient).trackEvent(
          eq("coreperson-email-synchronisation-created-success"),
          check {
            assertThat(it["nomisOffenderId"]).isEqualTo(nomisOffenderId.toString())
            assertThat(it["cprContactId"]).isEqualTo(nomisOffenderId.toString())
            assertThat(it["cprContactEmailId"]).isEqualTo(cprContactEmailId)
          },
          isNull(),
        )
      }
    }

    @Nested
    inner class WhenAlreadyCreated {
      @BeforeEach
      fun setUp() {
        mappingApiMock.stubGetByNomisEmailIdOrNull(
          nomisInternetAddressId,
          CorePersonEmailAddressMappingDto(
            cprId = cprContactEmailId,
            nomisId = nomisInternetAddressId,
            nomisPrisonNumber = "A1234BC",
            mappingType = CorePersonEmailAddressMappingDto.MappingType.NOMIS_CREATED,
          ),
        )
        sendEmailEvent("OFFENDER_EMAIL-INSERTED")
          .also { waitForAnyProcessingToComplete("coreperson-email-synchronisation-created-ignored") }
      }

      @Test
      fun `will not create email in CPR`() {
        corePersonCprApiMockServer.verify(0, postRequestedFor(urlPathEqualTo("/syscon-sync/person/A1234BC/contact")))
      }

      @Test
      fun `will track telemetry`() {
        verify(telemetryClient).trackEvent(
          ArgumentMatchers.eq("coreperson-email-synchronisation-created-ignored"),
          check {
            assertThat(it["nomisOffenderId"]).isEqualTo(nomisOffenderId.toString())
            assertThat(it["cprContactId"]).isEqualTo(nomisOffenderId.toString())
            assertThat(it["nomisInternetAddressId"]).isEqualTo(nomisInternetAddressId.toString())
            assertThat(it["cprContactEmailId"]).isEqualTo(cprContactEmailId)
          },
          isNull(),
        )
      }
    }

    @Nested
    inner class WhenDuplicateMapping {
      @BeforeEach
      fun setUp() {
        mappingApiMock.stubGetByNomisEmailIdOrNull(nomisInternetAddressId, null)
        nomisApiMock.stubGetOffenderEmail(nomisOffenderId, nomisInternetAddressId)
        corePersonCprApiMockServer.stubSyncCreateEmail(
          response = syncCorePersonEmailResponse(nomisInternetAddressId, cprContactEmailId),
        )
        mappingApiMock.stubCreateEmailMapping(
          error = DuplicateMappingErrorResponse(
            moreInfo = DuplicateErrorContentObject(
              duplicate = CorePersonEmailAddressMappingDto(
                cprId = cprContactEmailId,
                nomisId = nomisInternetAddressId,
                nomisPrisonNumber = "A1234BC",
                mappingType = CorePersonEmailAddressMappingDto.MappingType.NOMIS_CREATED,
              ),
              existing = CorePersonEmailAddressMappingDto(
                cprId = "existing-cpr-email-id",
                nomisId = nomisInternetAddressId,
                nomisPrisonNumber = "A1234BC",
                mappingType = CorePersonEmailAddressMappingDto.MappingType.NOMIS_CREATED,
              ),
            ),
            errorCode = 1409,
            status = DuplicateMappingErrorResponse.Status._409_CONFLICT,
            userMessage = "Duplicate mapping",
          ),
        )
        sendEmailEvent("OFFENDER_EMAIL-INSERTED")
          .also { waitForAnyProcessingToComplete("coreperson-email-mapping-synchronisation-duplicate") }
      }

      @Test
      fun `will create the email in CPR once`() {
        corePersonCprApiMockServer.verify(1, postRequestedFor(urlPathEqualTo("/syscon-sync/person/A1234BC/contact")))
      }

      @Test
      fun `will attempt to create the mapping once`() {
        mappingApi.verify(
          1,
          postRequestedFor(urlPathEqualTo("/mapping/core-person/email"))
            .withRequestBodyJsonPath("mappingType", "NOMIS_CREATED")
            .withRequestBodyJsonPath("cprId", cprContactEmailId)
            .withRequestBodyJsonPath("nomisId", "$nomisInternetAddressId"),
        )
      }

      @Test
      fun `will track telemetry for both overall success and duplicate mapping`() {
        verify(telemetryClient).trackEvent(
          eq("coreperson-email-synchronisation-created-success"),
          check {
            assertThat(it["nomisOffenderId"]).isEqualTo(nomisOffenderId.toString())
            assertThat(it["cprContactId"]).isEqualTo(nomisOffenderId.toString())
            assertThat(it["nomisInternetAddressId"]).isEqualTo(nomisInternetAddressId.toString())
            assertThat(it["cprContactEmailId"]).isEqualTo(cprContactEmailId)
          },
          isNull(),
        )
        verify(telemetryClient).trackEvent(
          eq("coreperson-email-mapping-synchronisation-duplicate"),
          check {
            assertThat(it["nomisPrisonNumber"]).isEqualTo("A1234BC")
            assertThat(it["existingNomisInternetAddressId"]).isEqualTo(nomisInternetAddressId.toString())
            assertThat(it["existingCprContactId"]).isEqualTo("existing-cpr-email-id")
            assertThat(it["duplicateNomisInternetAddressId"]).isEqualTo(nomisInternetAddressId.toString())
            assertThat(it["duplicateCprContactId"]).isEqualTo(cprContactEmailId)
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
        mappingApiMock.stubGetByNomisEmailIdOrNull(nomisInternetAddressId, null)
        nomisApiMock.stubGetOffenderEmail(nomisOffenderId, nomisInternetAddressId)
        corePersonCprApiMockServer.stubSyncCreateEmail(
          response = syncCorePersonEmailResponse(nomisInternetAddressId, cprContactEmailId),
        )
        mappingApiMock.stubCreateEmailMappingFollowedBySuccess()
        sendEmailEvent("OFFENDER_EMAIL-INSERTED")
          .also { waitForAnyProcessingToComplete("coreperson-email-mapping-synchronisation-created") }
      }

      @Test
      fun `will create the email in CPR once`() {
        corePersonCprApiMockServer.verify(1, postRequestedFor(urlPathEqualTo("/syscon-sync/person/A1234BC/contact")))
      }

      @Test
      fun `will create the mapping between the CPR and NOMIS records twice`() {
        mappingApi.verify(
          2,
          postRequestedFor(urlPathEqualTo("/mapping/core-person/email"))
            .withRequestBodyJsonPath("mappingType", "NOMIS_CREATED")
            .withRequestBodyJsonPath("cprId", cprContactEmailId)
            .withRequestBodyJsonPath("nomisId", "$nomisInternetAddressId"),
        )
      }

      @Test
      fun `will track telemetry`() {
        verify(telemetryClient).trackEvent(
          eq("coreperson-email-mapping-synchronisation-created"),
          check {
            assertThat(it["nomisOffenderId"]).isEqualTo(nomisOffenderId.toString())
            assertThat(it["cprContactId"]).isEqualTo(nomisOffenderId.toString())
            assertThat(it["nomisInternetAddressId"]).isEqualTo(nomisInternetAddressId.toString())
            assertThat(it["cprContactEmailId"]).isEqualTo(cprContactEmailId)
          },
          isNull(),
        )
      }
    }

    private fun sendEmailEvent(eventType: String, auditModuleName: String = "NOMIS") = awsSqsCorePersonOffenderEventsClient.sendMessage(
      corePersonQueueOffenderEventsUrl,
      offenderEmailEvent(eventType, nomisOffenderId, nomisInternetAddressId, auditModuleName),
    )
  }

  @Nested
  @DisplayName("OFFENDER_EMAIL-UPDATED")
  inner class PersonEmailUpdated {
    private val nomisInternetAddressId = 3456L
    private val nomisOffenderId = 123456L
    private val cprContactEmailId = "cpr-email-id"

    @Nested
    inner class WhenUpdatedInCpr {
      @BeforeEach
      fun setUp() {
        sendEmailEvent("OFFENDER_EMAIL-UPDATED", auditModuleName = "DPS_SYNCHRONISATION")
          .also { waitForAnyProcessingToComplete("coreperson-email-synchronisation-updated-skipped") }
      }

      @Test
      fun `will not update email in CPR`() {
        corePersonCprApiMockServer.verify(0, putRequestedFor(urlPathEqualTo("/syscon-sync/person/A1234BC/contact/$cprContactEmailId")))
      }

      @Test
      fun `will track telemetry`() {
        verify(telemetryClient).trackEvent(
          eq("coreperson-email-synchronisation-updated-skipped"),
          check {
            assertThat(it["nomisOffenderId"]).isEqualTo(nomisOffenderId.toString())
            assertThat(it["cprContactId"]).isEqualTo(nomisOffenderId.toString())
            assertThat(it["nomisInternetAddressId"]).isEqualTo(nomisInternetAddressId.toString())
          },
          isNull(),
        )
      }
    }

    @Nested
    inner class WhenUpdatedInNomis {
      @BeforeEach
      fun setUp() {
        mappingApiMock.stubGetByNomisEmailIdOrNull(
          nomisInternetAddressId,
          CorePersonEmailAddressMappingDto(
            cprId = cprContactEmailId,
            nomisId = nomisInternetAddressId,
            nomisPrisonNumber = "A1234BC",
            mappingType = CorePersonEmailAddressMappingDto.MappingType.NOMIS_CREATED,
          ),
        )
        nomisApiMock.stubGetOffenderEmail(
          offenderId = nomisOffenderId,
          emailAddressId = nomisInternetAddressId,
          emailAddress = offenderEmailAddress(nomisInternetAddressId).copy(
            lastUpdatedByUsername = "T.SWIFT",
            lastUpdatedDateTime = LocalDateTime.parse("2024-10-01T13:31"),
          ),
        )
        corePersonCprApiMockServer.stubSyncUpdateEmail("A1234BC", cprContactEmailId)
        sendEmailEvent("OFFENDER_EMAIL-UPDATED")
          .also { waitForAnyProcessingToComplete("coreperson-email-synchronisation-updated-success") }
      }

      @Test
      fun `will update the email in CPR`() {
        corePersonCprApiMockServer.verify(putRequestedFor(urlPathEqualTo("/syscon-sync/person/A1234BC/contact/$cprContactEmailId")))
        val request: PrisonContact = getRequestBody(putRequestedFor(urlPathEqualTo("/syscon-sync/person/A1234BC/contact/$cprContactEmailId")))
        with(request) {
          assertThat(nomisContactId).isEqualTo(nomisInternetAddressId)
          assertThat(type).isEqualTo(PrisonContact.Type.EMAIL)
          assertThat(value).isEqualTo("test@example.com")
          assertThat(modifyUserId).isEqualTo("T.SWIFT")
          assertThat(modifyDateTime).isEqualTo(LocalDateTime.parse("2024-10-01T13:31"))
        }
      }

      @Test
      fun `will track telemetry`() {
        verify(telemetryClient).trackEvent(
          eq("coreperson-email-synchronisation-updated-success"),
          check {
            assertThat(it["nomisOffenderId"]).isEqualTo(nomisOffenderId.toString())
            assertThat(it["cprContactId"]).isEqualTo(nomisOffenderId.toString())
            assertThat(it["nomisInternetAddressId"]).isEqualTo(nomisInternetAddressId.toString())
            assertThat(it["cprContactEmailId"]).isEqualTo(cprContactEmailId)
          },
          isNull(),
        )
      }
    }

    private fun sendEmailEvent(eventType: String, auditModuleName: String = "NOMIS") = awsSqsCorePersonOffenderEventsClient.sendMessage(
      corePersonQueueOffenderEventsUrl,
      offenderEmailEvent(eventType, nomisOffenderId, nomisInternetAddressId, auditModuleName),
    )
  }

  @Nested
  @DisplayName("OFFENDER_EMAIL-DELETED")
  inner class PersonEmailDeleted {
    private val nomisInternetAddressId = 3456L
    private val nomisOffenderId = 123456L
    private val cprContactEmailId = "cpr-email-id"

    @Nested
    inner class WhenMappingExists {
      @BeforeEach
      fun setUp() {
        mappingApiMock.stubGetByNomisEmailIdOrNull(
          nomisInternetAddressId,
          CorePersonEmailAddressMappingDto(
            cprId = cprContactEmailId,
            nomisId = nomisInternetAddressId,
            nomisPrisonNumber = "A1234BC",
            mappingType = CorePersonEmailAddressMappingDto.MappingType.NOMIS_CREATED,
          ),
        )
        corePersonCprApiMockServer.stubSyncDeleteEmail("A1234BC", cprContactEmailId)
        mappingApiMock.stubDeleteByNomisEmailId(nomisInternetAddressId)
        sendEmailEvent("OFFENDER_EMAIL-DELETED")
          .also { waitForAnyProcessingToComplete("coreperson-email-synchronisation-deleted-success") }
      }

      @Test
      fun `will delete the email in CPR`() {
        corePersonCprApiMockServer.verify(deleteRequestedFor(urlPathEqualTo("/syscon-sync/person/A1234BC/contact/$cprContactEmailId")))
      }

      @Test
      fun `will delete the email mapping`() {
        mappingApi.verify(deleteRequestedFor(urlPathEqualTo("/mapping/core-person/email/nomis-email-address-id/$nomisInternetAddressId")))
      }

      @Test
      fun `will track telemetry`() {
        verify(telemetryClient).trackEvent(
          eq("coreperson-email-synchronisation-deleted-success"),
          check {
            assertThat(it["nomisOffenderId"]).isEqualTo(nomisOffenderId.toString())
            assertThat(it["cprContactId"]).isEqualTo(nomisOffenderId.toString())
            assertThat(it["nomisInternetAddressId"]).isEqualTo(nomisInternetAddressId.toString())
            assertThat(it["cprContactEmailId"]).isEqualTo(cprContactEmailId)
          },
          isNull(),
        )
      }
    }

    @Nested
    inner class WhenMappingDoesNotExist {
      @BeforeEach
      fun setUp() {
        mappingApiMock.stubGetByNomisEmailIdOrNull(nomisInternetAddressId, null)
        sendEmailEvent("OFFENDER_EMAIL-DELETED")
          .also { waitForAnyProcessingToComplete("coreperson-email-synchronisation-deleted-ignored") }
      }

      @Test
      fun `will not delete an email in CPR`() {
        corePersonCprApiMockServer.verify(0, deleteRequestedFor(urlPathEqualTo("/syscon-sync/person/A1234BC/contact/$cprContactEmailId")))
      }

      @Test
      fun `will track telemetry`() {
        verify(telemetryClient).trackEvent(
          eq("coreperson-email-synchronisation-deleted-ignored"),
          check {
            assertThat(it["nomisOffenderId"]).isEqualTo(nomisOffenderId.toString())
            assertThat(it["cprContactId"]).isEqualTo(nomisOffenderId.toString())
            assertThat(it["nomisInternetAddressId"]).isEqualTo(nomisInternetAddressId.toString())
          },
          isNull(),
        )
      }
    }

    private fun sendEmailEvent(eventType: String, auditModuleName: String = "NOMIS") = awsSqsCorePersonOffenderEventsClient.sendMessage(
      corePersonQueueOffenderEventsUrl,
      offenderEmailEvent(eventType, nomisOffenderId, nomisInternetAddressId, auditModuleName),
    )
  }

  private fun sendAddressEvent(
    eventType: String,
    ownerId: Long = 1234L,
    addressId: Long = 3456L,
    auditModuleName: String = "NOMIS",
  ) = awsSqsCorePersonOffenderEventsClient.sendMessage(
    corePersonQueueOffenderEventsUrl,
    offenderAddressEvent(eventType, "A1234BC", ownerId, addressId, auditModuleName),
  )
}

private fun offenderEmailEvent(
  eventType: String,
  offenderId: Long,
  internetAddressId: Long,
  auditModuleName: String,
) = """
  {
    "MessageId": "ae06c49e-1f41-4b9f-b2f2-dcca610d02cd",
    "Type": "Notification",
    "Message": "{\"eventType\":\"$eventType\",\"offenderIdDisplay\":\"A1234BC\",\"offenderId\":$offenderId,\"internetAddressId\":$internetAddressId,\"auditModuleName\":\"$auditModuleName\"}",
    "MessageAttributes": {
      "eventType": {"Type": "String", "Value": "$eventType"}
    }
  }
""".trimIndent()

private fun offenderAddressEvent(
  eventType: String,
  offenderIdDisplay: String,
  ownerId: Long,
  addressId: Long,
  auditModuleName: String,
) = """
  {
    "MessageId": "ae06c49e-1f41-4b9f-b2f2-dcca610d02cd",
    "Type": "Notification",
    "Message": "{\"eventType\":\"$eventType\",\"offenderIdDisplay\":\"$offenderIdDisplay\",\"ownerId\":$ownerId,\"addressId\":$addressId,\"auditModuleName\":\"$auditModuleName\"}",
    "MessageAttributes": {
      "eventType": {"Type": "String", "Value": "$eventType"}
    }
  }
""".trimIndent()

private fun nomisAddress(addressId: Long) = OffenderAddress(
  addressId = addressId,
  primaryAddress = true,
  mailAddress = true,
  createdDateTime = LocalDateTime.parse("2001-03-03T00:00:00"),
  createdByUsername = "SYSTEM",
  lastUpdatedDateTime = null,
  lastUpdatedByUsername = null,
)
