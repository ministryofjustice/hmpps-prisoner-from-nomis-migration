package uk.gov.justice.digital.hmpps.prisonerfromnomismigration.organisations

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
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomismappings.model.CorporateMappingDto
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomismappings.model.CorporateMappingDto.MappingType.MIGRATED
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomismappings.model.CorporateMappingDto.MappingType.NOMIS_CREATED
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomismappings.model.DuplicateErrorContentObject
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomismappings.model.DuplicateMappingErrorResponse
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomissync.model.CodeDescription
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.organisations.OrganisationsDpsApiExtension.Companion.dpsOrganisationsServer
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
        awsSqsOrganisationsOffenderEventsClient.sendMessage(
          organisationsQueueOffenderEventsUrl,
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
        awsSqsOrganisationsOffenderEventsClient.sendMessage(
          organisationsQueueOffenderEventsUrl,
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
        awsSqsOrganisationsOffenderEventsClient.sendMessage(
          organisationsQueueOffenderEventsUrl,
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

        awsSqsOrganisationsOffenderEventsClient.sendMessage(
          organisationsQueueOffenderEventsUrl,
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
            assertThat(it["existingNomisCorporateId"]).isEqualTo("$corporateAndOrganisationId")
            assertThat(it["existingDpsOrganisationId"]).isEqualTo("9999")
            assertThat(it["duplicateNomisCorporateId"]).isEqualTo("$corporateAndOrganisationId")
            assertThat(it["duplicateDpsOrganisationId"]).isEqualTo("$corporateAndOrganisationId")
            assertThat(it["type"]).isEqualTo("ORGANISATION")
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
        awsSqsOrganisationsOffenderEventsClient.sendMessage(
          organisationsQueueOffenderEventsUrl,
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
        awsSqsOrganisationsOffenderEventsClient.sendMessage(
          organisationsQueueOffenderEventsUrl,
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

        awsSqsOrganisationsOffenderEventsClient.sendMessage(
          organisationsQueueOffenderEventsUrl,
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
