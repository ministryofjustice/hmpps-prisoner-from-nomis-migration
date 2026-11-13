package uk.gov.justice.digital.hmpps.prisonerfromnomismigration.coreperson

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
import org.mockito.kotlin.check
import org.mockito.kotlin.eq
import org.mockito.kotlin.isNull
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.http.HttpStatus
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.coreperson.model.NomisIdentifierId
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.coreperson.model.PrisonAlias
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.coreperson.model.PrisonIdentifier
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.coreperson.model.PrisonIdentifiersRequest
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.coreperson.model.SysconIdentifierMapping
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.integration.countAllMessagesOnDLQQueue
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.integration.sendMessage
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomismappings.model.DuplicateErrorContentObject
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomismappings.model.DuplicateMappingErrorResponse
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomismappings.model.OffenderAliasMappingDto
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomismappings.model.OffenderIdentifierMappingDto
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomisprisoner.model.CodeDescription
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomisprisoner.model.Identifier
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.wiremock.MappingApiExtension
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.wiremock.MappingApiExtension.Companion.mappingApi
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.wiremock.NomisApiExtension.Companion.nomisApi
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.wiremock.withRequestBodyJsonPath
import java.time.LocalDate
import java.time.LocalDateTime

class CorePersonSynchronisationAliasIdentifierIntTest(
  @Autowired private val nomisApiMock: CorePersonNomisApiMockServer,
  @Autowired private val mappingApiMock: CorePersonAliasIdentifierMappingApiMockServer,
) : CorePersonIntegrationTestBase() {

  @Nested
  @DisplayName("OFFENDER_IDENTIFIER-UPDATED")
  inner class OffenderIdentifierUpdated {
    private val prisonNumber = "A1234BC"
    private val offenderId = 123456L
    private val identifierSequence = 2L
    private val cprAliasId = "cpr-alias-id"
    private val replaceIdentifiersUrl = "/syscon-sync/person/$prisonNumber/alias/$cprAliasId/identifiers"
    private val replaceMappingsUrl = "/mapping/core-person/identifier/nomis-offender-id/$offenderId/replace"

    private val nomisIdentifiers = listOf(
      nomisIdentifier(sequence = 1, type = "PNC", value = "20/0071818T"),
      nomisIdentifier(sequence = 2, type = "CRO", value = "123456/20X"),
    )

    @Nested
    inner class WhenUpdatedInDps {
      @BeforeEach
      fun setUp() {
        sendIdentifierEvent(auditModuleName = "DPS_SYNCHRONISATION_CORE_PERSON")
          .also { waitForAnyProcessingToComplete("coreperson-identifier-synchronisation-updated-skipped") }
      }

      @Test
      fun `will not retrieve the identifiers from NOMIS`() {
        nomisApi.verify(0, getRequestedFor(urlPathEqualTo("/core-person/$offenderId/identifiers")))
      }

      @Test
      fun `will not replace the identifiers in CPR`() {
        corePersonCprApiMockServer.verify(0, putRequestedFor(urlPathEqualTo(replaceIdentifiersUrl)))
      }

      @Test
      fun `will not replace the identifier mappings`() {
        mappingApi.verify(0, postRequestedFor(urlPathEqualTo(replaceMappingsUrl)))
      }

      @Test
      fun `will track telemetry`() {
        verify(telemetryClient).trackEvent(
          eq("coreperson-identifier-synchronisation-updated-skipped"),
          check {
            assertThat(it["nomisPrisonNumber"]).isEqualTo(prisonNumber)
            assertThat(it["nomisOffenderId"]).isEqualTo(offenderId.toString())
            assertThat(it["nomisIdentifierSequence"]).isEqualTo(identifierSequence.toString())
            assertThat(it).doesNotContainKey("cprAliasId")
          },
          isNull(),
        )
      }
    }

    @Nested
    inner class WhenUpdatedInNomis {
      @BeforeEach
      fun setUp() {
        mappingApiMock.stubGetByNomisAliasIdOrNull(offenderId, aliasMapping())
        nomisApiMock.stubGetOffenderIdentifiers(offenderId, nomisIdentifiers)
        mappingApiMock.stubGetIdentifierMappingsByNomisOffenderId(offenderId, emptyList())
        corePersonCprApiMockServer.stubSyncReplaceIdentifiers(
          prisonNumber = prisonNumber,
          cprAliasId = cprAliasId,
          identifierMappings = listOf(
            SysconIdentifierMapping(NomisIdentifierId(offenderId, 1), "cpr-identifier-1"),
            SysconIdentifierMapping(NomisIdentifierId(offenderId, 2), "cpr-identifier-2"),
          ),
        )
        mappingApiMock.stubReplaceIdentifierMappings(offenderId)
        sendIdentifierEvent()
          .also { waitForAnyProcessingToComplete("coreperson-identifier-synchronisation-updated-success") }
      }

      @Test
      fun `will look up the alias mapping`() {
        mappingApi.verify(getRequestedFor(urlPathEqualTo("/mapping/core-person/alias/nomis-offender-id/$offenderId")))
      }

      @Test
      fun `will retrieve all the identifiers for the offender from NOMIS`() {
        nomisApi.verify(getRequestedFor(urlPathEqualTo("/core-person/$offenderId/identifiers")))
      }

      @Test
      fun `will retrieve the existing identifier mappings for the offender`() {
        mappingApi.verify(getRequestedFor(urlPathEqualTo("/mapping/core-person/identifier/nomis-offender-id/$offenderId")))
      }

      @Test
      fun `will replace all the identifiers in CPR`() {
        val request: PrisonIdentifiersRequest = CorePersonCprApiExtension.getRequestBody(putRequestedFor(urlPathEqualTo(replaceIdentifiersUrl)))
        assertThat(request.identifiers).hasSize(2)
        with(request.identifiers[0]) {
          assertThat(nomisIdentifierId).isEqualTo(NomisIdentifierId(offenderId, 1))
          assertThat(cprIdentifierId).isNull()
          assertThat(type).isEqualTo(PrisonIdentifier.Type.PNC)
          assertThat(value).isEqualTo("20/0071818T")
          assertThat(verified).isTrue()
          assertThat(comment).isEqualTo("Met Police")
          assertThat(issuedDate).isEqualTo(LocalDate.parse("2020-01-01"))
          assertThat(createDateTime).isEqualTo(LocalDateTime.parse("2020-01-01T10:00:00"))
          assertThat(createUserId).isEqualTo("SYSTEM")
          assertThat(modifyDateTime).isEqualTo(LocalDateTime.parse("2024-10-01T13:31:00"))
          assertThat(modifyUserId).isEqualTo("T.SWIFT")
        }
        with(request.identifiers[1]) {
          assertThat(nomisIdentifierId).isEqualTo(NomisIdentifierId(offenderId, 2))
          assertThat(cprIdentifierId).isNull()
          assertThat(type).isEqualTo(PrisonIdentifier.Type.CRO)
          assertThat(value).isEqualTo("123456/20X")
        }
      }

      @Test
      fun `will replace the identifier mappings with those returned from CPR`() {
        val request: List<OffenderIdentifierMappingDto> = MappingApiExtension.getRequestBody(postRequestedFor(urlPathEqualTo(replaceMappingsUrl)))
        assertThat(request).containsExactly(
          identifierMapping(sequence = 1, cprId = "cpr-identifier-1", mappingType = OffenderIdentifierMappingDto.MappingType.NOMIS_CREATED),
          identifierMapping(sequence = 2, cprId = "cpr-identifier-2", mappingType = OffenderIdentifierMappingDto.MappingType.NOMIS_CREATED),
        )
      }

      @Test
      fun `will track telemetry`() {
        verifyTelemetry("coreperson-identifier-synchronisation-updated-success")
      }
    }

    @Nested
    inner class WhenExistingIdentifierMappings {
      @BeforeEach
      fun setUp() {
        mappingApiMock.stubGetByNomisAliasIdOrNull(offenderId, aliasMapping())
        nomisApiMock.stubGetOffenderIdentifiers(offenderId, nomisIdentifiers)
        mappingApiMock.stubGetIdentifierMappingsByNomisOffenderId(
          offenderId,
          listOf(
            identifierMapping(sequence = 1, cprId = "cpr-identifier-1"),
            identifierMapping(sequence = 3, cprId = "cpr-identifier-3"),
          ),
        )
        corePersonCprApiMockServer.stubSyncReplaceIdentifiers(
          prisonNumber = prisonNumber,
          cprAliasId = cprAliasId,
          identifierMappings = listOf(
            SysconIdentifierMapping(NomisIdentifierId(offenderId, 1), "cpr-identifier-1"),
            SysconIdentifierMapping(NomisIdentifierId(offenderId, 2), "cpr-identifier-2"),
          ),
        )
        mappingApiMock.stubReplaceIdentifierMappings(offenderId)
        sendIdentifierEvent()
          .also { waitForAnyProcessingToComplete("coreperson-identifier-synchronisation-updated-success") }
      }

      @Test
      fun `will pass the existing CPR identifier ids to CPR`() {
        val request: PrisonIdentifiersRequest = CorePersonCprApiExtension.getRequestBody(putRequestedFor(urlPathEqualTo(replaceIdentifiersUrl)))
        assertThat(request.identifiers.map { it.nomisIdentifierId.nomisSequence to it.cprIdentifierId }).containsExactly(
          1 to "cpr-identifier-1",
          2 to null,
        )
      }

      @Test
      fun `will replace the identifier mappings, removing the ones no longer in NOMIS`() {
        val request: List<OffenderIdentifierMappingDto> = MappingApiExtension.getRequestBody(postRequestedFor(urlPathEqualTo(replaceMappingsUrl)))
        assertThat(request.map { it.nomisIdentifierSequence to it.cprId }).containsExactly(
          1 to "cpr-identifier-1",
          2 to "cpr-identifier-2",
        )
      }

      @Test
      fun `will track telemetry`() {
        verifyTelemetry("coreperson-identifier-synchronisation-updated-success")
      }
    }

    @Nested
    inner class WhenNoIdentifiersLeftInNomis {
      @BeforeEach
      fun setUp() {
        mappingApiMock.stubGetByNomisAliasIdOrNull(offenderId, aliasMapping())
        nomisApiMock.stubGetOffenderIdentifiers(offenderId, emptyList())
        mappingApiMock.stubGetIdentifierMappingsByNomisOffenderId(
          offenderId,
          listOf(identifierMapping(sequence = 1, cprId = "cpr-identifier-1")),
        )
        corePersonCprApiMockServer.stubSyncReplaceIdentifiers(prisonNumber = prisonNumber, cprAliasId = cprAliasId)
        mappingApiMock.stubReplaceIdentifierMappings(offenderId)
        sendIdentifierEvent()
          .also { waitForAnyProcessingToComplete("coreperson-identifier-synchronisation-updated-success") }
      }

      @Test
      fun `will replace the identifiers in CPR with an empty list`() {
        val request: PrisonIdentifiersRequest = CorePersonCprApiExtension.getRequestBody(putRequestedFor(urlPathEqualTo(replaceIdentifiersUrl)))
        assertThat(request.identifiers).isEmpty()
      }

      @Test
      fun `will replace the identifier mappings with an empty list`() {
        val request: List<OffenderIdentifierMappingDto> = MappingApiExtension.getRequestBody(postRequestedFor(urlPathEqualTo(replaceMappingsUrl)))
        assertThat(request).isEmpty()
      }
    }

    @Nested
    inner class WhenAliasMappingDoesNotExist {
      @BeforeEach
      fun setUp() {
        mappingApiMock.stubGetByNomisAliasIdOrNull(offenderId, null)
        sendIdentifierEvent()
          .also { waitForAnyProcessingToComplete("coreperson-identifier-synchronisation-updated-error", times = 2) }
      }

      @Test
      fun `will retry and send the event to the DLQ without replacing the identifiers`() {
        mappingApi.verify(2, getRequestedFor(urlPathEqualTo("/mapping/core-person/alias/nomis-offender-id/$offenderId")))
        nomisApi.verify(0, getRequestedFor(urlPathEqualTo("/core-person/$offenderId/identifiers")))
        corePersonCprApiMockServer.verify(0, putRequestedFor(urlPathEqualTo(replaceIdentifiersUrl)))
        mappingApi.verify(0, postRequestedFor(urlPathEqualTo(replaceMappingsUrl)))
        await untilAsserted {
          assertThat(corePersonOffenderEventsQueue.countAllMessagesOnDLQQueue()).isEqualTo(1)
        }
      }

      @Test
      fun `will track the error for each attempt`() {
        verify(telemetryClient, times(2)).trackEvent(
          eq("coreperson-identifier-synchronisation-updated-error"),
          check {
            assertThat(it["nomisPrisonNumber"]).isEqualTo(prisonNumber)
            assertThat(it["nomisOffenderId"]).isEqualTo(offenderId.toString())
            assertThat(it["nomisIdentifierSequence"]).isEqualTo(identifierSequence.toString())
            assertThat(it).doesNotContainKey("cprAliasId")
            assertThat(it["error"]).isNotNull()
          },
          isNull(),
        )
      }
    }

    @Nested
    inner class WhenIdentifierTypeNotMapped {
      @BeforeEach
      fun setUp() {
        mappingApiMock.stubGetByNomisAliasIdOrNull(offenderId, aliasMapping())
        nomisApiMock.stubGetOffenderIdentifiers(
          offenderId,
          listOf(nomisIdentifier(sequence = 1, type = "PASS", value = "123456789")),
        )
        mappingApiMock.stubGetIdentifierMappingsByNomisOffenderId(offenderId, emptyList())
        sendIdentifierEvent()
          .also { waitForAnyProcessingToComplete("coreperson-identifier-synchronisation-updated-error", times = 2) }
      }

      @Test
      fun `will retry and send the event to the DLQ without replacing the identifiers`() {
        corePersonCprApiMockServer.verify(0, putRequestedFor(urlPathEqualTo(replaceIdentifiersUrl)))
        mappingApi.verify(0, postRequestedFor(urlPathEqualTo(replaceMappingsUrl)))
        await untilAsserted {
          assertThat(corePersonOffenderEventsQueue.countAllMessagesOnDLQQueue()).isEqualTo(1)
        }
      }

      @Test
      fun `will track the error for each attempt`() {
        verifyTelemetry("coreperson-identifier-synchronisation-updated-error", times = 2) {
          assertThat(it["error"]).contains("PASS")
        }
      }
    }

    @Nested
    inner class WhenCprFails {
      @BeforeEach
      fun setUp() {
        mappingApiMock.stubGetByNomisAliasIdOrNull(offenderId, aliasMapping())
        nomisApiMock.stubGetOffenderIdentifiers(offenderId, nomisIdentifiers)
        mappingApiMock.stubGetIdentifierMappingsByNomisOffenderId(offenderId, emptyList())
        corePersonCprApiMockServer.stubSyncReplaceIdentifiers(
          prisonNumber = prisonNumber,
          cprAliasId = cprAliasId,
          status = HttpStatus.INTERNAL_SERVER_ERROR,
        )
        sendIdentifierEvent()
          .also { waitForAnyProcessingToComplete("coreperson-identifier-synchronisation-updated-error", times = 2) }
      }

      @Test
      fun `will retry and send the event to the DLQ without replacing the mappings`() {
        corePersonCprApiMockServer.verify(2, putRequestedFor(urlPathEqualTo(replaceIdentifiersUrl)))
        mappingApi.verify(0, postRequestedFor(urlPathEqualTo(replaceMappingsUrl)))
        await untilAsserted {
          assertThat(corePersonOffenderEventsQueue.countAllMessagesOnDLQQueue()).isEqualTo(1)
        }
      }

      @Test
      fun `will track the error for each attempt`() {
        verifyTelemetry("coreperson-identifier-synchronisation-updated-error", times = 2) {
          assertThat(it["error"]).isNotNull()
        }
      }
    }

    @Nested
    inner class WhenMappingReplaceFails {
      @BeforeEach
      fun setUp() {
        mappingApiMock.stubGetByNomisAliasIdOrNull(offenderId, aliasMapping())
        nomisApiMock.stubGetOffenderIdentifiers(offenderId, nomisIdentifiers)
        mappingApiMock.stubGetIdentifierMappingsByNomisOffenderId(offenderId, emptyList())
        corePersonCprApiMockServer.stubSyncReplaceIdentifiers(
          prisonNumber = prisonNumber,
          cprAliasId = cprAliasId,
          identifierMappings = listOf(
            SysconIdentifierMapping(NomisIdentifierId(offenderId, 1), "cpr-identifier-1"),
            SysconIdentifierMapping(NomisIdentifierId(offenderId, 2), "cpr-identifier-2"),
          ),
        )
        mappingApiMock.stubReplaceIdentifierMappings(offenderId, status = HttpStatus.INTERNAL_SERVER_ERROR)
        sendIdentifierEvent()
          .also { waitForAnyProcessingToComplete("coreperson-identifier-synchronisation-updated-error", times = 2) }
      }

      @Test
      fun `will replace the identifiers in CPR on each attempt`() {
        corePersonCprApiMockServer.verify(2, putRequestedFor(urlPathEqualTo(replaceIdentifiersUrl)))
      }

      @Test
      fun `will attempt to replace the mappings on each attempt and then send the event to the DLQ`() {
        mappingApi.verify(2, postRequestedFor(urlPathEqualTo(replaceMappingsUrl)))
        await untilAsserted {
          assertThat(corePersonOffenderEventsQueue.countAllMessagesOnDLQQueue()).isEqualTo(1)
        }
      }

      @Test
      fun `will track the error for each attempt`() {
        verifyTelemetry("coreperson-identifier-synchronisation-updated-error", times = 2) {
          assertThat(it["error"]).isNotNull()
        }
      }
    }

    private fun verifyTelemetry(eventName: String, times: Int = 1, extraChecks: (Map<String, String>) -> Unit = {}) {
      verify(telemetryClient, times(times)).trackEvent(
        eq(eventName),
        check {
          assertThat(it["nomisPrisonNumber"]).isEqualTo(prisonNumber)
          assertThat(it["nomisOffenderId"]).isEqualTo(offenderId.toString())
          assertThat(it["nomisIdentifierSequence"]).isEqualTo(identifierSequence.toString())
          assertThat(it["cprAliasId"]).isEqualTo(cprAliasId)
          extraChecks(it)
        },
        isNull(),
      )
    }

    private fun aliasMapping() = OffenderAliasMappingDto(
      cprId = cprAliasId,
      nomisOffenderId = offenderId,
      nomisPrisonNumber = prisonNumber,
      mappingType = OffenderAliasMappingDto.MappingType.MIGRATED,
    )

    private fun identifierMapping(
      sequence: Int,
      cprId: String,
      mappingType: OffenderIdentifierMappingDto.MappingType = OffenderIdentifierMappingDto.MappingType.MIGRATED,
    ) = OffenderIdentifierMappingDto(
      cprId = cprId,
      nomisOffenderId = offenderId,
      nomisIdentifierSequence = sequence,
      nomisPrisonNumber = prisonNumber,
      mappingType = mappingType,
    )

    private fun nomisIdentifier(sequence: Long, type: String, value: String) = Identifier(
      offenderId = offenderId,
      sequence = sequence,
      type = CodeDescription(type, type),
      identifier = value,
      issuedAuthority = "Met Police",
      issuedDate = LocalDate.parse("2020-01-01"),
      verified = true,
      createdDateTime = LocalDateTime.parse("2020-01-01T10:00:00"),
      createdByUsername = "SYSTEM",
      lastUpdatedDateTime = LocalDateTime.parse("2024-10-01T13:31:00"),
      lastUpdatedByUsername = "T.SWIFT",
    )

    private fun sendIdentifierEvent(auditModuleName: String = "NOMIS") = awsSqsCorePersonOffenderEventsClient.sendMessage(
      corePersonQueueOffenderEventsUrl,
      offenderIdentifierEvent("OFFENDER_IDENTIFIER-UPDATED", prisonNumber, offenderId, identifierSequence, auditModuleName),
    )
  }

  @Nested
  @DisplayName("OFFENDER-INSERTED")
  inner class OffenderInserted {
    private val prisonNumber = "A1234BC"
    private val offenderId = 123456L
    private val cprAliasId = "cpr-alias-id"
    private val createAliasUrl = "/syscon-sync/person/$prisonNumber/alias"
    private val aliasMappingUrl = "/mapping/core-person/alias"

    @Nested
    inner class WhenCreatedInDps {
      @BeforeEach
      fun setUp() {
        sendOffenderEvent("OFFENDER-INSERTED", prisonNumber, offenderId, auditModuleName = "DPS_SYNCHRONISATION_CORE_PERSON")
          .also { waitForAnyProcessingToComplete("coreperson-alias-synchronisation-created-skipped") }
      }

      @Test
      fun `will not retrieve the alias from NOMIS`() {
        nomisApi.verify(0, getRequestedFor(urlPathEqualTo("/core-person/$offenderId/alias")))
      }

      @Test
      fun `will not create the alias in CPR`() {
        corePersonCprApiMockServer.verify(0, postRequestedFor(urlPathEqualTo(createAliasUrl)))
      }

      @Test
      fun `will not create a mapping`() {
        mappingApi.verify(0, postRequestedFor(urlPathEqualTo(aliasMappingUrl)))
      }

      @Test
      fun `will track telemetry`() {
        verifyAliasTelemetry("coreperson-alias-synchronisation-created-skipped", prisonNumber, offenderId, cprAliasId = null)
      }
    }

    @Nested
    inner class WhenCreatedInNomis {
      @BeforeEach
      fun setUp() {
        mappingApiMock.stubGetByNomisAliasIdOrNull(offenderId, null)
        nomisApiMock.stubGetOffenderAlias(offenderId)
        corePersonCprApiMockServer.stubSyncCreateAlias(prisonNumber, offenderId, cprAliasId)
        mappingApiMock.stubCreateAliasMapping()
        sendOffenderEvent("OFFENDER-INSERTED", prisonNumber, offenderId)
          .also { waitForAnyProcessingToComplete("coreperson-alias-synchronisation-created-success") }
      }

      @Test
      fun `will check if the alias mapping already exists`() {
        mappingApi.verify(getRequestedFor(urlPathEqualTo("/mapping/core-person/alias/nomis-offender-id/$offenderId")))
      }

      @Test
      fun `will retrieve the alias from NOMIS`() {
        nomisApi.verify(getRequestedFor(urlPathEqualTo("/core-person/$offenderId/alias")))
      }

      @Test
      fun `will create the alias in CPR without any identifiers`() {
        val request: PrisonAlias = CorePersonCprApiExtension.getRequestBody(postRequestedFor(urlPathEqualTo(createAliasUrl)))
        with(request) {
          assertThat(nomisOffenderId).isEqualTo(offenderId)
          assertThat(cprAliasId).isNull()
          assertThat(isPrimary).isTrue()
          assertThat(titleCode).isEqualTo(PrisonAlias.TitleCode.MR)
          assertThat(firstName).isEqualTo("JOHN")
          assertThat(middleNames).isEqualTo("FRED JAMES")
          assertThat(lastName).isEqualTo("SMITH")
          assertThat(dateOfBirth).isEqualTo(LocalDate.parse("1980-01-01"))
          assertThat(sexCode).isEqualTo(PrisonAlias.SexCode.M)
          assertThat(birthPlace).isEqualTo("LONDON")
          assertThat(createDateTime).isEqualTo(LocalDateTime.parse("2020-01-01T10:00:00"))
          assertThat(createUserId).isEqualTo("SYSTEM")
          assertThat(identifiers).isEmpty()
        }
      }

      @Test
      fun `will create the mapping between the CPR and NOMIS alias`() {
        mappingApi.verify(
          postRequestedFor(urlPathEqualTo(aliasMappingUrl))
            .withRequestBodyJsonPath("mappingType", "NOMIS_CREATED")
            .withRequestBodyJsonPath("cprId", cprAliasId)
            .withRequestBodyJsonPath("nomisOffenderId", "$offenderId")
            .withRequestBodyJsonPath("nomisPrisonNumber", prisonNumber),
        )
      }

      @Test
      fun `will track telemetry`() {
        verifyAliasTelemetry("coreperson-alias-synchronisation-created-success", prisonNumber, offenderId, cprAliasId)
      }
    }

    @Nested
    inner class WhenMappingAlreadyExists {
      @BeforeEach
      fun setUp() {
        mappingApiMock.stubGetByNomisAliasIdOrNull(offenderId, aliasMapping(prisonNumber, offenderId, cprAliasId))
        sendOffenderEvent("OFFENDER-INSERTED", prisonNumber, offenderId)
          .also { waitForAnyProcessingToComplete("coreperson-alias-synchronisation-created-ignored") }
      }

      @Test
      fun `will not retrieve the alias from NOMIS`() {
        nomisApi.verify(0, getRequestedFor(urlPathEqualTo("/core-person/$offenderId/alias")))
      }

      @Test
      fun `will not create the alias in CPR`() {
        corePersonCprApiMockServer.verify(0, postRequestedFor(urlPathEqualTo(createAliasUrl)))
      }

      @Test
      fun `will not create a mapping`() {
        mappingApi.verify(0, postRequestedFor(urlPathEqualTo(aliasMappingUrl)))
      }

      @Test
      fun `will track telemetry`() {
        verifyAliasTelemetry("coreperson-alias-synchronisation-created-ignored", prisonNumber, offenderId, cprAliasId)
      }
    }

    @Nested
    inner class WhenCprFails {
      @BeforeEach
      fun setUp() {
        mappingApiMock.stubGetByNomisAliasIdOrNull(offenderId, null)
        nomisApiMock.stubGetOffenderAlias(offenderId)
        corePersonCprApiMockServer.stubSyncCreateAlias(prisonNumber, status = HttpStatus.INTERNAL_SERVER_ERROR)
        sendOffenderEvent("OFFENDER-INSERTED", prisonNumber, offenderId)
          .also { waitForAnyProcessingToComplete("coreperson-alias-synchronisation-created-error", times = 2) }
      }

      @Test
      fun `will retry and send the event to the DLQ without creating a mapping`() {
        corePersonCprApiMockServer.verify(2, postRequestedFor(urlPathEqualTo(createAliasUrl)))
        mappingApi.verify(0, postRequestedFor(urlPathEqualTo(aliasMappingUrl)))
        await untilAsserted {
          assertThat(corePersonOffenderEventsQueue.countAllMessagesOnDLQQueue()).isEqualTo(1)
        }
      }

      @Test
      fun `will track the error for each attempt`() {
        verify(telemetryClient, times(2)).trackEvent(
          eq("coreperson-alias-synchronisation-created-error"),
          check {
            assertThat(it["nomisPrisonNumber"]).isEqualTo(prisonNumber)
            assertThat(it["nomisOffenderId"]).isEqualTo(offenderId.toString())
            assertThat(it["error"]).isNotNull()
          },
          isNull(),
        )
      }
    }

    @Nested
    inner class WhenDuplicateMapping {
      @BeforeEach
      fun setUp() {
        mappingApiMock.stubGetByNomisAliasIdOrNull(offenderId, null)
        nomisApiMock.stubGetOffenderAlias(offenderId)
        corePersonCprApiMockServer.stubSyncCreateAlias(prisonNumber, offenderId, cprAliasId)
        mappingApiMock.stubCreateAliasMapping(
          error = DuplicateMappingErrorResponse(
            moreInfo = DuplicateErrorContentObject(
              duplicate = aliasMapping(prisonNumber, offenderId, cprAliasId, OffenderAliasMappingDto.MappingType.NOMIS_CREATED),
              existing = aliasMapping(prisonNumber, offenderId, "existing-cpr-alias-id", OffenderAliasMappingDto.MappingType.NOMIS_CREATED),
            ),
            errorCode = 1409,
            status = DuplicateMappingErrorResponse.Status._409_CONFLICT,
            userMessage = "Duplicate mapping",
          ),
        )
        sendOffenderEvent("OFFENDER-INSERTED", prisonNumber, offenderId)
          .also { waitForAnyProcessingToComplete("coreperson-alias-mapping-synchronisation-duplicate") }
      }

      @Test
      fun `will create the alias in CPR once`() {
        corePersonCprApiMockServer.verify(1, postRequestedFor(urlPathEqualTo(createAliasUrl)))
      }

      @Test
      fun `will attempt to create the mapping once`() {
        mappingApi.verify(1, postRequestedFor(urlPathEqualTo(aliasMappingUrl)))
      }

      @Test
      fun `will track duplicate telemetry`() {
        verify(telemetryClient).trackEvent(
          eq("coreperson-alias-mapping-synchronisation-duplicate"),
          check {
            assertThat(it["nomisPrisonNumber"]).isEqualTo(prisonNumber)
            assertThat(it["existingNomisOffenderId"]).isEqualTo(offenderId.toString())
            assertThat(it["existingCprAliasId"]).isEqualTo("existing-cpr-alias-id")
            assertThat(it["duplicateNomisOffenderId"]).isEqualTo(offenderId.toString())
            assertThat(it["duplicateCprAliasId"]).isEqualTo(cprAliasId)
            assertThat(it["type"]).isEqualTo("ALIAS")
          },
          isNull(),
        )
      }
    }

    @Nested
    inner class WhenMappingCreateFailsOnce {
      @BeforeEach
      fun setUp() {
        mappingApiMock.stubGetByNomisAliasIdOrNull(offenderId, null)
        nomisApiMock.stubGetOffenderAlias(offenderId)
        corePersonCprApiMockServer.stubSyncCreateAlias(prisonNumber, offenderId, cprAliasId)
        mappingApiMock.stubCreateAliasMappingFollowedBySuccess()
        sendOffenderEvent("OFFENDER-INSERTED", prisonNumber, offenderId)
          .also { waitForAnyProcessingToComplete("coreperson-alias-mapping-synchronisation-created") }
      }

      @Test
      fun `will create the alias in CPR once`() {
        corePersonCprApiMockServer.verify(1, postRequestedFor(urlPathEqualTo(createAliasUrl)))
      }

      @Test
      fun `will create the mapping between the CPR and NOMIS records twice`() {
        mappingApi.verify(
          2,
          postRequestedFor(urlPathEqualTo(aliasMappingUrl))
            .withRequestBodyJsonPath("mappingType", "NOMIS_CREATED")
            .withRequestBodyJsonPath("cprId", cprAliasId)
            .withRequestBodyJsonPath("nomisOffenderId", "$offenderId"),
        )
      }

      @Test
      fun `will track telemetry`() {
        verifyAliasTelemetry("coreperson-alias-synchronisation-created-success", prisonNumber, offenderId, cprAliasId)
        verifyAliasTelemetry("coreperson-alias-mapping-synchronisation-created", prisonNumber, offenderId, cprAliasId)
      }
    }
  }

  @Nested
  @DisplayName("OFFENDER-UPDATED")
  inner class OffenderUpdated {
    private val prisonNumber = "A1234BC"
    private val offenderId = 123456L
    private val cprAliasId = "cpr-alias-id"
    private val updateAliasUrl = "/syscon-sync/person/$prisonNumber/alias/$cprAliasId"

    @Nested
    inner class WhenUpdatedInDps {
      @BeforeEach
      fun setUp() {
        sendOffenderEvent("OFFENDER-UPDATED", prisonNumber, offenderId, auditModuleName = "DPS_SYNCHRONISATION_CORE_PERSON")
          .also { waitForAnyProcessingToComplete("coreperson-alias-synchronisation-updated-skipped") }
      }

      @Test
      fun `will not retrieve the alias from NOMIS`() {
        nomisApi.verify(0, getRequestedFor(urlPathEqualTo("/core-person/$offenderId/alias")))
      }

      @Test
      fun `will not update the alias in CPR`() {
        corePersonCprApiMockServer.verify(0, putRequestedFor(urlPathEqualTo(updateAliasUrl)))
      }

      @Test
      fun `will track telemetry`() {
        verifyAliasTelemetry("coreperson-alias-synchronisation-updated-skipped", prisonNumber, offenderId, cprAliasId = null)
      }
    }

    @Nested
    inner class WhenUpdatedInNomis {
      @BeforeEach
      fun setUp() {
        mappingApiMock.stubGetByNomisAliasIdOrNull(offenderId, aliasMapping(prisonNumber, offenderId, cprAliasId))
        nomisApiMock.stubGetOffenderAlias(offenderId)
        corePersonCprApiMockServer.stubSyncUpdateAlias(prisonNumber, cprAliasId)
        sendOffenderEvent("OFFENDER-UPDATED", prisonNumber, offenderId)
          .also { waitForAnyProcessingToComplete("coreperson-alias-synchronisation-updated-success") }
      }

      @Test
      fun `will look up the alias mapping`() {
        mappingApi.verify(getRequestedFor(urlPathEqualTo("/mapping/core-person/alias/nomis-offender-id/$offenderId")))
      }

      @Test
      fun `will retrieve the alias from NOMIS`() {
        nomisApi.verify(getRequestedFor(urlPathEqualTo("/core-person/$offenderId/alias")))
      }

      @Test
      fun `will update the alias in CPR without any identifiers`() {
        val request: PrisonAlias = CorePersonCprApiExtension.getRequestBody(putRequestedFor(urlPathEqualTo(updateAliasUrl)))
        with(request) {
          assertThat(nomisOffenderId).isEqualTo(offenderId)
          assertThat(cprAliasId).isEqualTo(this@OffenderUpdated.cprAliasId)
          assertThat(firstName).isEqualTo("JOHN")
          assertThat(lastName).isEqualTo("SMITH")
          assertThat(identifiers).isEmpty()
        }
      }

      @Test
      fun `will not touch any mappings`() {
        mappingApi.verify(0, postRequestedFor(urlPathMatching("/mapping/core-person/.*")))
        mappingApi.verify(0, deleteRequestedFor(urlPathMatching("/mapping/core-person/.*")))
      }

      @Test
      fun `will track telemetry`() {
        verifyAliasTelemetry("coreperson-alias-synchronisation-updated-success", prisonNumber, offenderId, cprAliasId)
      }
    }

    @Nested
    inner class WhenMappingDoesNotExist {
      @BeforeEach
      fun setUp() {
        mappingApiMock.stubGetByNomisAliasIdOrNull(offenderId, null)
        sendOffenderEvent("OFFENDER-UPDATED", prisonNumber, offenderId)
          .also { waitForAnyProcessingToComplete("coreperson-alias-synchronisation-updated-error", times = 2) }
      }

      @Test
      fun `will retry and send the event to the DLQ without updating CPR`() {
        nomisApi.verify(0, getRequestedFor(urlPathEqualTo("/core-person/$offenderId/alias")))
        corePersonCprApiMockServer.verify(0, putRequestedFor(urlPathEqualTo(updateAliasUrl)))
        await untilAsserted {
          assertThat(corePersonOffenderEventsQueue.countAllMessagesOnDLQQueue()).isEqualTo(1)
        }
      }

      @Test
      fun `will track the error for each attempt`() {
        verify(telemetryClient, times(2)).trackEvent(
          eq("coreperson-alias-synchronisation-updated-error"),
          check {
            assertThat(it["nomisPrisonNumber"]).isEqualTo(prisonNumber)
            assertThat(it["nomisOffenderId"]).isEqualTo(offenderId.toString())
            assertThat(it).doesNotContainKey("cprAliasId")
            assertThat(it["error"]).isNotNull()
          },
          isNull(),
        )
      }
    }

    @Nested
    inner class WhenCprFails {
      @BeforeEach
      fun setUp() {
        mappingApiMock.stubGetByNomisAliasIdOrNull(offenderId, aliasMapping(prisonNumber, offenderId, cprAliasId))
        nomisApiMock.stubGetOffenderAlias(offenderId)
        corePersonCprApiMockServer.stubSyncUpdateAlias(prisonNumber, cprAliasId, status = HttpStatus.INTERNAL_SERVER_ERROR)
        sendOffenderEvent("OFFENDER-UPDATED", prisonNumber, offenderId)
          .also { waitForAnyProcessingToComplete("coreperson-alias-synchronisation-updated-error", times = 2) }
      }

      @Test
      fun `will retry and send the event to the DLQ`() {
        corePersonCprApiMockServer.verify(2, putRequestedFor(urlPathEqualTo(updateAliasUrl)))
        await untilAsserted {
          assertThat(corePersonOffenderEventsQueue.countAllMessagesOnDLQQueue()).isEqualTo(1)
        }
      }

      @Test
      fun `will track the error for each attempt`() {
        verifyAliasTelemetry("coreperson-alias-synchronisation-updated-error", prisonNumber, offenderId, cprAliasId, times = 2) {
          assertThat(it["error"]).isNotNull()
        }
      }
    }
  }

  @Nested
  @DisplayName("OFFENDER-DELETED")
  inner class OffenderDeleted {
    private val prisonNumber = "A1234BC"
    private val offenderId = 123456L
    private val cprAliasId = "cpr-alias-id"
    private val deleteAliasUrl = "/syscon-sync/person/$prisonNumber/alias/$cprAliasId"
    private val aliasMappingUrl = "/mapping/core-person/alias/nomis-offender-id/$offenderId"

    @Nested
    inner class WhenDeletedInDps {
      @BeforeEach
      fun setUp() {
        sendOffenderEvent("OFFENDER-DELETED", prisonNumber, offenderId, auditModuleName = "DPS_SYNCHRONISATION_CORE_PERSON")
          .also { waitForAnyProcessingToComplete("coreperson-alias-synchronisation-deleted-skipped") }
      }

      @Test
      fun `will not delete the alias in CPR`() {
        corePersonCprApiMockServer.verify(0, deleteRequestedFor(urlPathEqualTo(deleteAliasUrl)))
      }

      @Test
      fun `will not delete any mappings`() {
        mappingApi.verify(0, deleteRequestedFor(urlPathEqualTo(aliasMappingUrl)))
      }

      @Test
      fun `will track telemetry`() {
        verifyAliasTelemetry("coreperson-alias-synchronisation-deleted-skipped", prisonNumber, offenderId, cprAliasId = null)
      }
    }

    @Nested
    inner class WhenDeletedInNomis {
      @BeforeEach
      fun setUp() {
        mappingApiMock.stubGetByNomisAliasIdOrNull(offenderId, aliasMapping(prisonNumber, offenderId, cprAliasId))
        corePersonCprApiMockServer.stubSyncDeleteAlias(prisonNumber, cprAliasId)
        mappingApiMock.stubDeleteByNomisAliasId(offenderId)
        sendOffenderEvent("OFFENDER-DELETED", prisonNumber, offenderId)
          .also { waitForAnyProcessingToComplete("coreperson-alias-synchronisation-deleted-success") }
      }

      @Test
      fun `will look up the alias mapping`() {
        mappingApi.verify(getRequestedFor(urlPathEqualTo(aliasMappingUrl)))
      }

      @Test
      fun `will delete the alias in CPR`() {
        corePersonCprApiMockServer.verify(deleteRequestedFor(urlPathEqualTo(deleteAliasUrl)))
      }

      @Test
      fun `will delete the alias mapping`() {
        mappingApi.verify(deleteRequestedFor(urlPathEqualTo(aliasMappingUrl)))
      }

      @Test
      fun `will track telemetry`() {
        verifyAliasTelemetry("coreperson-alias-synchronisation-deleted-success", prisonNumber, offenderId, cprAliasId)
      }
    }

    @Nested
    inner class WhenMappingDoesNotExist {
      @BeforeEach
      fun setUp() {
        mappingApiMock.stubGetByNomisAliasIdOrNull(offenderId, null)
        sendOffenderEvent("OFFENDER-DELETED", prisonNumber, offenderId)
          .also { waitForAnyProcessingToComplete("coreperson-alias-synchronisation-deleted-ignored") }
      }

      @Test
      fun `will not delete the alias in CPR`() {
        corePersonCprApiMockServer.verify(0, deleteRequestedFor(urlPathEqualTo(deleteAliasUrl)))
      }

      @Test
      fun `will not delete any mappings`() {
        mappingApi.verify(0, deleteRequestedFor(urlPathEqualTo(aliasMappingUrl)))
      }

      @Test
      fun `will track telemetry`() {
        verifyAliasTelemetry("coreperson-alias-synchronisation-deleted-ignored", prisonNumber, offenderId, cprAliasId = null)
      }
    }

    @Nested
    inner class WhenCprFails {
      @BeforeEach
      fun setUp() {
        mappingApiMock.stubGetByNomisAliasIdOrNull(offenderId, aliasMapping(prisonNumber, offenderId, cprAliasId))
        corePersonCprApiMockServer.stubSyncDeleteAlias(prisonNumber, cprAliasId, status = HttpStatus.INTERNAL_SERVER_ERROR)
        sendOffenderEvent("OFFENDER-DELETED", prisonNumber, offenderId)
          .also { waitForAnyProcessingToComplete("coreperson-alias-synchronisation-deleted-error", times = 2) }
      }

      @Test
      fun `will retry and send the event to the DLQ without deleting any mappings`() {
        corePersonCprApiMockServer.verify(2, deleteRequestedFor(urlPathEqualTo(deleteAliasUrl)))
        mappingApi.verify(0, deleteRequestedFor(urlPathEqualTo(aliasMappingUrl)))
        await untilAsserted {
          assertThat(corePersonOffenderEventsQueue.countAllMessagesOnDLQQueue()).isEqualTo(1)
        }
      }

      @Test
      fun `will track the error for each attempt`() {
        verifyAliasTelemetry("coreperson-alias-synchronisation-deleted-error", prisonNumber, offenderId, cprAliasId, times = 2) {
          assertThat(it["error"]).isNotNull()
        }
      }
    }
  }

  private fun verifyAliasTelemetry(
    eventName: String,
    prisonNumber: String,
    offenderId: Long,
    cprAliasId: String?,
    times: Int = 1,
    extraChecks: (Map<String, String>) -> Unit = {},
  ) {
    verify(telemetryClient, times(times)).trackEvent(
      eq(eventName),
      check {
        assertThat(it["nomisPrisonNumber"]).isEqualTo(prisonNumber)
        assertThat(it["nomisOffenderId"]).isEqualTo(offenderId.toString())
        if (cprAliasId == null) {
          assertThat(it).doesNotContainKey("cprAliasId")
        } else {
          assertThat(it["cprAliasId"]).isEqualTo(cprAliasId)
        }
        extraChecks(it)
      },
      isNull(),
    )
  }

  private fun aliasMapping(
    prisonNumber: String,
    offenderId: Long,
    cprAliasId: String,
    mappingType: OffenderAliasMappingDto.MappingType = OffenderAliasMappingDto.MappingType.MIGRATED,
  ) = OffenderAliasMappingDto(
    cprId = cprAliasId,
    nomisOffenderId = offenderId,
    nomisPrisonNumber = prisonNumber,
    mappingType = mappingType,
  )

  private fun sendOffenderEvent(
    eventType: String,
    prisonNumber: String,
    offenderId: Long,
    auditModuleName: String = "NOMIS",
  ) = awsSqsCorePersonOffenderEventsClient.sendMessage(
    corePersonQueueOffenderEventsUrl,
    offenderEvent(eventType, prisonNumber, offenderId, auditModuleName),
  )
}

private fun offenderEvent(
  eventType: String,
  prisonNumber: String,
  offenderId: Long,
  auditModuleName: String,
) = """
  {
    "MessageId": "ae06c49e-1f41-4b9f-b2f2-dcca610d02cd",
    "Type": "Notification",
    "Message": "{\"eventType\":\"$eventType\",\"offenderIdDisplay\":\"$prisonNumber\",\"offenderId\":$offenderId,\"auditModuleName\":\"$auditModuleName\"}",
    "MessageAttributes": {
      "eventType": {"Type": "String", "Value": "$eventType"}
    }
  }
""".trimIndent()

private fun offenderIdentifierEvent(
  eventType: String,
  prisonNumber: String,
  offenderId: Long,
  offenderIdSeq: Long,
  auditModuleName: String,
) = """
  {
    "MessageId": "ae06c49e-1f41-4b9f-b2f2-dcca610d02cd",
    "Type": "Notification",
    "Message": "{\"eventType\":\"$eventType\",\"offenderIdDisplay\":\"$prisonNumber\",\"offenderId\":$offenderId,\"offenderIdSeq\":$offenderIdSeq,\"auditModuleName\":\"$auditModuleName\"}",
    "MessageAttributes": {
      "eventType": {"Type": "String", "Value": "$eventType"}
    }
  }
""".trimIndent()
