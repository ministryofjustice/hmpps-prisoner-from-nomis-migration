package uk.gov.justice.digital.hmpps.prisonerfromnomismigration.coreperson

import com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor
import com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor
import com.github.tomakehurst.wiremock.client.WireMock.putRequestedFor
import com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo
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
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.coreperson.model.PrisonIdentifier
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.coreperson.model.PrisonIdentifiersRequest
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.coreperson.model.SysconIdentifierMapping
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.integration.countAllMessagesOnDLQQueue
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.integration.sendMessage
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomismappings.model.OffenderAliasMappingDto
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomismappings.model.OffenderIdentifierMappingDto
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomisprisoner.model.CodeDescription
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomisprisoner.model.Identifier
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.wiremock.MappingApiExtension
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.wiremock.MappingApiExtension.Companion.mappingApi
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.wiremock.NomisApiExtension.Companion.nomisApi
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
}

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
