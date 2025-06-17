package uk.gov.justice.digital.hmpps.prisonerfromnomismigration.visitbalances

import com.github.tomakehurst.wiremock.client.WireMock.anyUrl
import com.github.tomakehurst.wiremock.client.WireMock.equalTo
import com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor
import com.github.tomakehurst.wiremock.client.WireMock.matchingJsonPath
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
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.visit.balance.model.VisitAllocationPrisonerMigrationDto
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.visit.balance.model.VisitAllocationPrisonerSyncDto
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.visitbalances.VisitBalanceDpsApiExtension.Companion.dpsVisitBalanceServer
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.wiremock.NomisApiExtension.Companion.nomisApi

class VisitBalanceSynchronisationIntTest : SqsIntegrationTestBase() {
  @Autowired
  private lateinit var nomisVisitBalanceApiMock: VisitBalanceNomisApiMockServer

  private val dpsApiMock = dpsVisitBalanceServer

  @Autowired
  private lateinit var mappingApiMock: VisitBalanceMappingApiMockServer

  @Nested
  @DisplayName("OFFENDER_VISIT_BALANCE_ADJS-INSERTED")
  inner class VisitBalanceAdjustmentInserted {
    private val visitBalanceAdjId = 123456L
    private val nomisPrisonNumber = "A1234BC"

    @Nested
    inner class WhenMissingAudit {
      @Test
      fun `the event is ignored if DPS Allocation`() {
        nomisApi.stubCheckServicePrisonForPrisoner()
        visitBalanceOffenderEventsQueue.sendMessage(
          visitBalanceAdjustmentEvent(
            eventType = "OFFENDER_VISIT_BALANCE_ADJS-INSERTED",
            visitBalanceAdjId = visitBalanceAdjId,
            auditModuleName = "",
          ),
        ).also { waitForAnyProcessingToComplete() }

        nomisApi.verify(getRequestedFor(urlPathEqualTo("/service-prisons/VISIT_ALLOCATION/prisoner/A1234BC")))

        dpsApiMock.verify(0, postRequestedFor(urlPathEqualTo("/visits/allocation/prisoner/sync")))
        verify(telemetryClient).trackEvent(
          eq("visitbalance-adjustment-synchronisation-created-skipped"),
          check {
            assertThat(it["nomisVisitBalanceAdjustmentId"]).isEqualTo(visitBalanceAdjId.toString())
            assertThat(it["dpsId"]).isEqualTo(nomisPrisonNumber)
          },
          isNull(),
        )
      }

      @Test
      fun `the event is not ignored if Nomis Allocation`() {
        nomisApi.stubCheckServicePrisonForPrisonerNotFound()
        nomisVisitBalanceApiMock.stubGetVisitBalanceAdjustment(nomisVisitBalanceAdjustmentId = visitBalanceAdjId)
        mappingApiMock.stubGetVisitBalanceAdjustmentByNomisId(mapping = null)
        dpsApiMock.stubSyncVisitBalanceAdjustment()
        mappingApiMock.stubCreateVisitBalanceAdjustmentMapping()

        visitBalanceOffenderEventsQueue.sendMessage(
          visitBalanceAdjustmentEvent(
            eventType = "OFFENDER_VISIT_BALANCE_ADJS-INSERTED",
            visitBalanceAdjId = visitBalanceAdjId,
            auditModuleName = "",
          ),
        ).also { waitForAnyProcessingToComplete() }

        nomisApi.verify(getRequestedFor(urlPathEqualTo("/service-prisons/VISIT_ALLOCATION/prisoner/A1234BC")))
        dpsApiMock.verify(postRequestedFor(urlPathEqualTo("/visits/allocation/prisoner/sync")))
        verify(telemetryClient).trackEvent(
          eq("visitbalance-adjustment-synchronisation-created-success"),
          check {
            assertThat(it["nomisVisitBalanceAdjustmentId"]).isEqualTo(visitBalanceAdjId.toString())
            assertThat(it["dpsId"]).isEqualTo(nomisPrisonNumber)
          },
          isNull(),
        )
      }
    }

    @Nested
    inner class WhenCreatedInDps {
      @Nested
      inner class WhenDpsVisitAllocation {
        @BeforeEach
        fun setUp() {
          nomisApi.stubCheckServicePrisonForPrisoner()
          visitBalanceOffenderEventsQueue.sendMessage(
            visitBalanceAdjustmentEvent(
              eventType = "OFFENDER_VISIT_BALANCE_ADJS-INSERTED",
              visitBalanceAdjId = visitBalanceAdjId,
              auditModuleName = "DPS_SYNCHRONISATION",
            ),
          ).also { waitForAnyProcessingToComplete() }
        }

        @Test
        fun `will not make a call to Nomis`() {
          nomisVisitBalanceApiMock.verify(
            0,
            getRequestedFor(urlPathEqualTo("/visit-balances/visit-balance-adjustment/$visitBalanceAdjId")),
          )
        }

        @Test
        fun `will not update in DPS`() {
          dpsApiMock.verify(0, postRequestedFor(urlPathEqualTo("/visits/allocation/prisoner/sync")))
        }

        @Test
        fun `will not call the mapping service`() {
          mappingApiMock.verify(0, getRequestedFor(anyUrl()))
          mappingApiMock.verify(0, postRequestedFor(anyUrl()))
        }

        @Test
        fun `will track telemetry`() {
          verify(telemetryClient).trackEvent(
            eq("visitbalance-adjustment-synchronisation-created-skipped"),
            check {
              assertThat(it["nomisVisitBalanceAdjustmentId"]).isEqualTo(visitBalanceAdjId.toString())
              assertThat(it["dpsId"]).isEqualTo(nomisPrisonNumber)
            },
            isNull(),
          )
        }
      }

      @Nested
      inner class WhenNomisVisitAllocation {
        @BeforeEach
        fun setUp() {
          nomisApi.stubCheckServicePrisonForPrisonerNotFound()
        }

        @Nested
        inner class HappyPath {
          @BeforeEach
          fun setUp() {
            nomisVisitBalanceApiMock.stubGetVisitBalanceAdjustment(nomisVisitBalanceAdjustmentId = visitBalanceAdjId)
            mappingApiMock.stubGetVisitBalanceAdjustmentByNomisId(mapping = null)
            dpsApiMock.stubSyncVisitBalanceAdjustment()
            mappingApiMock.stubCreateVisitBalanceAdjustmentMapping()

            visitBalanceOffenderEventsQueue.sendMessage(
              visitBalanceAdjustmentEvent(
                eventType = "OFFENDER_VISIT_BALANCE_ADJS-INSERTED",
                visitBalanceAdjId = visitBalanceAdjId,
              ),
            ).also { waitForAnyProcessingToComplete() }
          }

          @Test
          fun `will retrieve the adjustment details from NOMIS`() {
            nomisVisitBalanceApiMock.verify(getRequestedFor(urlPathEqualTo("/visit-balances/visit-balance-adjustment/$visitBalanceAdjId")))
          }

          @Test
          fun `will create the adjustment in DPS`() {
            dpsApiMock.verify(postRequestedFor(urlPathEqualTo("/visits/allocation/prisoner/sync")))
            val request: VisitAllocationPrisonerSyncDto = VisitBalanceDpsApiExtension.getRequestBody(
              postRequestedFor(urlPathEqualTo("/visits/allocation/prisoner/sync")),
            )
            with(request) {
              assertThat(prisonerId).isEqualTo(nomisPrisonNumber)
              assertThat(oldVoBalance).isEqualTo(12)
              assertThat(changeToVoBalance).isEqualTo(2)
              assertThat(oldPvoBalance).isEqualTo(4)
              assertThat(changeToPvoBalance).isEqualTo(1)
              assertThat(comment).isEqualTo("Some comment")
              assertThat(adjustmentReasonCode.value).isEqualTo("IEP")
              assertThat(changeLogSource.value).isEqualTo("STAFF")
              assertThat(createdDate).isEqualTo("2025-01-01")
            }
          }

          @Test
          fun `will track telemetry`() {
            verify(telemetryClient).trackEvent(
              eq("visitbalance-adjustment-synchronisation-created-success"),
              check {
                assertThat(it["nomisVisitBalanceAdjustmentId"]).isEqualTo(visitBalanceAdjId.toString())
                assertThat(it["dpsId"]).isEqualTo(nomisPrisonNumber)
                assertThat(it["visitOrderChange"]).isEqualTo("2")
                assertThat(it["previousVisitOrderCount"]).isEqualTo("12")
                assertThat(it["privilegedVisitOrderChange"]).isEqualTo("1")
                assertThat(it["previousPrivilegedVisitOrderCount"]).isEqualTo("4")
              },
              isNull(),
            )
          }
        }

        @Nested
        inner class HappyPathPassingNullEntries {
          @BeforeEach
          fun setUp() {
            nomisVisitBalanceApiMock.stubGetVisitBalanceAdjustment(
              nomisVisitBalanceAdjustmentId = visitBalanceAdjId,
              visitBalanceAdjustment().copy(
                previousVisitOrderCount = null,
                visitOrderChange = null,
                previousPrivilegedVisitOrderCount = null,
                privilegedVisitOrderChange = null,
                comment = null,
              ),
            )
            mappingApiMock.stubGetVisitBalanceAdjustmentByNomisId(mapping = null)
            dpsApiMock.stubSyncVisitBalanceAdjustment()
            mappingApiMock.stubCreateVisitBalanceAdjustmentMapping()

            visitBalanceOffenderEventsQueue.sendMessage(
              visitBalanceAdjustmentEvent(
                eventType = "OFFENDER_VISIT_BALANCE_ADJS-INSERTED",
                visitBalanceAdjId = visitBalanceAdjId,
              ),
            ).also { waitForAnyProcessingToComplete() }
          }

          @Test
          fun `will retrieve the adjustment details from NOMIS`() {
            nomisVisitBalanceApiMock.verify(getRequestedFor(urlPathEqualTo("/visit-balances/visit-balance-adjustment/$visitBalanceAdjId")))
          }

          @Test
          fun `will create the adjustment in DPS`() {
            dpsApiMock.verify(postRequestedFor(urlPathEqualTo("/visits/allocation/prisoner/sync")))
            val request: VisitAllocationPrisonerSyncDto = VisitBalanceDpsApiExtension.getRequestBody(
              postRequestedFor(urlPathEqualTo("/visits/allocation/prisoner/sync")),
            )
            with(request) {
              assertThat(prisonerId).isEqualTo(nomisPrisonNumber)
              assertThat(oldVoBalance).isNull()
              assertThat(changeToVoBalance).isNull()
              assertThat(oldPvoBalance).isNull()
              assertThat(changeToPvoBalance).isNull()
              assertThat(comment).isNull()
              assertThat(adjustmentReasonCode.value).isEqualTo("IEP")
              assertThat(changeLogSource.value).isEqualTo("STAFF")
              assertThat(createdDate).isEqualTo("2025-01-01")
            }
          }

          @Test
          fun `will track telemetry`() {
            verify(telemetryClient).trackEvent(
              eq("visitbalance-adjustment-synchronisation-created-success"),
              check {
                assertThat(it["nomisVisitBalanceAdjustmentId"]).isEqualTo(visitBalanceAdjId.toString())
                assertThat(it["dpsId"]).isEqualTo(nomisPrisonNumber)
              },
              isNull(),
            )
          }
        }

        @Nested
        inner class WhenAlreadyCreated {
          @BeforeEach
          fun setUp() {
            nomisVisitBalanceApiMock.stubGetVisitBalanceAdjustment(nomisVisitBalanceAdjustmentId = visitBalanceAdjId)
            mappingApiMock.stubGetVisitBalanceAdjustmentByNomisId(nomisVisitBalanceAdjustmentId = visitBalanceAdjId)

            visitBalanceOffenderEventsQueue.sendMessage(
              visitBalanceAdjustmentEvent(
                eventType = "OFFENDER_VISIT_BALANCE_ADJS-INSERTED",
                visitBalanceAdjId = visitBalanceAdjId,
              ),
            ).also { waitForAnyProcessingToComplete() }
          }

          @Test
          fun `will not update in DPS`() {
            dpsApiMock.verify(0, postRequestedFor(urlPathEqualTo("/visits/allocation/prisoner/sync")))
          }

          @Test
          fun `will track telemetry`() {
            verify(telemetryClient).trackEvent(
              eq("visitbalance-adjustment-synchronisation-created-ignored"),
              org.mockito.kotlin.check {
                assertThat(it["nomisVisitBalanceAdjustmentId"]).isEqualTo(visitBalanceAdjId.toString())
                assertThat(it["dpsId"]).isEqualTo(nomisPrisonNumber)
              },
              isNull(),
            )
          }
        }

        @Nested
        inner class HappyPathForIepEntitlement {
          @BeforeEach
          fun setUp() {
            nomisVisitBalanceApiMock.stubGetVisitBalanceAdjustment(
              nomisVisitBalanceAdjustmentId = visitBalanceAdjId,
              visitBalanceAdjustment = visitBalanceAdjustment().copy(comment = "Initial IEP entitlement"),
            )
            nomisVisitBalanceApiMock.stubGetVisitBalanceDetail(nomisVisitBalanceId = 1215724, prisonNumber = nomisPrisonNumber)
            dpsApiMock.stubMigrateVisitBalance()

            visitBalanceOffenderEventsQueue.sendMessage(
              visitBalanceAdjustmentEvent(
                eventType = "OFFENDER_VISIT_BALANCE_ADJS-INSERTED",
                visitBalanceAdjId = visitBalanceAdjId,
              ),
            ).also { waitForAnyProcessingToComplete() }
          }

          @Test
          fun `will retrieve the adjustment details from NOMIS`() {
            nomisVisitBalanceApiMock.verify(getRequestedFor(urlPathEqualTo("/visit-balances/visit-balance-adjustment/$visitBalanceAdjId")))
          }

          @Test
          fun `will retrieve the balance details from NOMIS`() {
            nomisVisitBalanceApiMock.verify(getRequestedFor(urlPathEqualTo("/visit-balances/1215724")))
          }

          @Test
          fun `will create the balance in DPS`() {
            dpsApiMock.verify(postRequestedFor(urlPathEqualTo("/visits/allocation/prisoner/migrate")))
            val request: VisitAllocationPrisonerMigrationDto = VisitBalanceDpsApiExtension.getRequestBody(
              postRequestedFor(urlPathEqualTo("/visits/allocation/prisoner/migrate")),
            )
            with(request) {
              assertThat(prisonerId).isEqualTo(nomisPrisonNumber)
              assertThat(voBalance).isEqualTo(3)
              assertThat(pvoBalance).isEqualTo(2)
            }
          }

          @Test
          fun `will track telemetry`() {
            verify(telemetryClient).trackEvent(
              eq("visitbalance-adjustment-synchronisation-balance-success"),
              check {
                assertThat(it["visitBalanceAdjustmentId"]).isEqualTo(visitBalanceAdjId.toString())
                assertThat(it["nomisPrisonNumber"]).isEqualTo(nomisPrisonNumber)
              },
              isNull(),
            )
          }
        }
      }
      // TODO  duplicate mapping tests
    }

    @Nested
    inner class WhenCreatedInNomis {

      @Nested
      inner class WhenDpsVisitAllocation {
        @BeforeEach
        fun setUp() {
          nomisApi.stubCheckServicePrisonForPrisoner()
        }

        @Nested
        inner class HappyPath {
          @BeforeEach
          fun setUp() {
            nomisVisitBalanceApiMock.stubGetVisitBalanceAdjustment(nomisVisitBalanceAdjustmentId = visitBalanceAdjId)
            mappingApiMock.stubGetVisitBalanceAdjustmentByNomisId(mapping = null)
            dpsApiMock.stubSyncVisitBalanceAdjustment()
            mappingApiMock.stubCreateVisitBalanceAdjustmentMapping()

            visitBalanceOffenderEventsQueue.sendMessage(
              visitBalanceAdjustmentEvent(
                eventType = "OFFENDER_VISIT_BALANCE_ADJS-INSERTED",
                visitBalanceAdjId = visitBalanceAdjId,
              ),
            ).also { waitForAnyProcessingToComplete() }
          }

          @Test
          fun `will retrieve the adjustment details from NOMIS`() {
            nomisVisitBalanceApiMock.verify(getRequestedFor(urlPathEqualTo("/visit-balances/visit-balance-adjustment/$visitBalanceAdjId")))
          }

          @Test
          fun `will create the adjustment in DPS`() {
            dpsApiMock.verify(postRequestedFor(urlPathEqualTo("/visits/allocation/prisoner/sync")))
            val request: VisitAllocationPrisonerSyncDto = VisitBalanceDpsApiExtension.getRequestBody(
              postRequestedFor(urlPathEqualTo("/visits/allocation/prisoner/sync")),
            )
            with(request) {
              assertThat(prisonerId).isEqualTo(nomisPrisonNumber)
              assertThat(oldVoBalance).isEqualTo(12)
              assertThat(changeToVoBalance).isEqualTo(2)
              assertThat(oldPvoBalance).isEqualTo(4)
              assertThat(changeToPvoBalance).isEqualTo(1)
              assertThat(comment).isEqualTo("Some comment")
              assertThat(adjustmentReasonCode.value).isEqualTo("IEP")
              assertThat(changeLogSource.value).isEqualTo("STAFF")
              assertThat(createdDate).isEqualTo("2025-01-01")
            }
          }

          @Test
          fun `will track telemetry`() {
            verify(telemetryClient).trackEvent(
              eq("visitbalance-adjustment-synchronisation-created-success"),
              check {
                assertThat(it["nomisVisitBalanceAdjustmentId"]).isEqualTo(visitBalanceAdjId.toString())
                assertThat(it["dpsId"]).isEqualTo(nomisPrisonNumber)
              },
              isNull(),
            )
          }
        }

        @Nested
        inner class HappyPathPassingNullEntries {
          @BeforeEach
          fun setUp() {
            nomisVisitBalanceApiMock.stubGetVisitBalanceAdjustment(
              nomisVisitBalanceAdjustmentId = visitBalanceAdjId,
              visitBalanceAdjustment().copy(
                previousVisitOrderCount = null,
                visitOrderChange = null,
                previousPrivilegedVisitOrderCount = null,
                privilegedVisitOrderChange = null,
                comment = null,
              ),
            )
            mappingApiMock.stubGetVisitBalanceAdjustmentByNomisId(mapping = null)
            dpsApiMock.stubSyncVisitBalanceAdjustment()
            mappingApiMock.stubCreateVisitBalanceAdjustmentMapping()

            visitBalanceOffenderEventsQueue.sendMessage(
              visitBalanceAdjustmentEvent(
                eventType = "OFFENDER_VISIT_BALANCE_ADJS-INSERTED",
                visitBalanceAdjId = visitBalanceAdjId,
              ),
            ).also { waitForAnyProcessingToComplete() }
          }

          @Test
          fun `will retrieve the adjustment details from NOMIS`() {
            nomisVisitBalanceApiMock.verify(getRequestedFor(urlPathEqualTo("/visit-balances/visit-balance-adjustment/$visitBalanceAdjId")))
          }

          @Test
          fun `will create the adjustment in DPS`() {
            dpsApiMock.verify(postRequestedFor(urlPathEqualTo("/visits/allocation/prisoner/sync")))
            val request: VisitAllocationPrisonerSyncDto = VisitBalanceDpsApiExtension.getRequestBody(
              postRequestedFor(urlPathEqualTo("/visits/allocation/prisoner/sync")),
            )
            with(request) {
              assertThat(prisonerId).isEqualTo(nomisPrisonNumber)
              assertThat(oldVoBalance).isNull()
              assertThat(changeToVoBalance).isNull()
              assertThat(oldPvoBalance).isNull()
              assertThat(changeToPvoBalance).isNull()
              assertThat(comment).isNull()
              assertThat(adjustmentReasonCode.value).isEqualTo("IEP")
              assertThat(changeLogSource.value).isEqualTo("STAFF")
              assertThat(createdDate).isEqualTo("2025-01-01")
            }
          }

          @Test
          fun `will track telemetry`() {
            verify(telemetryClient).trackEvent(
              eq("visitbalance-adjustment-synchronisation-created-success"),
              check {
                assertThat(it["nomisVisitBalanceAdjustmentId"]).isEqualTo(visitBalanceAdjId.toString())
                assertThat(it["dpsId"]).isEqualTo(nomisPrisonNumber)
              },
              isNull(),
            )
          }
        }
      }

      @Nested
      inner class WhenNomisVisitAllocation {
        @BeforeEach
        fun setUp() {
          nomisApi.stubCheckServicePrisonForPrisonerNotFound()
          mappingApiMock.stubGetVisitBalanceAdjustmentByNomisId(mapping = null)
          dpsApiMock.stubSyncVisitBalanceAdjustment()
          mappingApiMock.stubCreateVisitBalanceAdjustmentMapping()
        }

        @Nested
        inner class HappyPath {
          @BeforeEach
          fun setUp() {
            nomisVisitBalanceApiMock.stubGetVisitBalanceAdjustment(nomisVisitBalanceAdjustmentId = visitBalanceAdjId)

            visitBalanceOffenderEventsQueue.sendMessage(
              visitBalanceAdjustmentEvent(
                eventType = "OFFENDER_VISIT_BALANCE_ADJS-INSERTED",
                visitBalanceAdjId = visitBalanceAdjId,
              ),
            ).also { waitForAnyProcessingToComplete() }
          }

          @Test
          fun `will retrieve the adjustment details from NOMIS`() {
            nomisVisitBalanceApiMock.verify(getRequestedFor(urlPathEqualTo("/visit-balances/visit-balance-adjustment/$visitBalanceAdjId")))
          }

          @Test
          fun `will call the mapping api to determine if the mapping exists`() {
            mappingApiMock.verify(getRequestedFor(urlPathEqualTo("/mapping/visit-balance-adjustment/nomis-id/$visitBalanceAdjId")))
          }

          @Test
          fun `will create the adjustment in DPS`() {
            dpsApiMock.verify(postRequestedFor(urlPathEqualTo("/visits/allocation/prisoner/sync")))
            val request: VisitAllocationPrisonerSyncDto = VisitBalanceDpsApiExtension.getRequestBody(
              postRequestedFor(urlPathEqualTo("/visits/allocation/prisoner/sync")),
            )
            with(request) {
              assertThat(prisonerId).isEqualTo(nomisPrisonNumber)
              assertThat(oldVoBalance).isEqualTo(12)
              assertThat(changeToVoBalance).isEqualTo(2)
              assertThat(oldPvoBalance).isEqualTo(4)
              assertThat(changeToPvoBalance).isEqualTo(1)
              assertThat(comment).isEqualTo("Some comment")
              assertThat(adjustmentReasonCode.value).isEqualTo("IEP")
              assertThat(changeLogSource.value).isEqualTo("STAFF")
              assertThat(createdDate).isEqualTo("2025-01-01")
            }
          }

          @Test
          fun `will save the adjustment in the mapping table`() {
            mappingApiMock.verify(
              postRequestedFor(urlPathEqualTo("/mapping/visit-balance-adjustment"))
                .withRequestBody(matchingJsonPath("nomisVisitBalanceAdjustmentId", equalTo("$visitBalanceAdjId")))
                .withRequestBody(matchingJsonPath("dpsId", equalTo(nomisPrisonNumber)))
                .withRequestBody(matchingJsonPath("mappingType", equalTo("NOMIS_CREATED"))),
            )
          }

          @Test
          fun `will track telemetry`() {
            verify(telemetryClient).trackEvent(
              eq("visitbalance-adjustment-synchronisation-created-success"),
              check {
                assertThat(it["nomisVisitBalanceAdjustmentId"]).isEqualTo(visitBalanceAdjId.toString())
                assertThat(it["dpsId"]).isEqualTo(nomisPrisonNumber)
              },
              isNull(),
            )
          }
        }

        @Nested
        inner class HappyPathPassingNullEntries {
          @BeforeEach
          fun setUp() {
            nomisVisitBalanceApiMock.stubGetVisitBalanceAdjustment(
              nomisVisitBalanceAdjustmentId = visitBalanceAdjId,
              visitBalanceAdjustment().copy(
                previousVisitOrderCount = null,
                visitOrderChange = null,
                previousPrivilegedVisitOrderCount = null,
                privilegedVisitOrderChange = null,
                comment = null,
              ),
            )

            visitBalanceOffenderEventsQueue.sendMessage(
              visitBalanceAdjustmentEvent(
                eventType = "OFFENDER_VISIT_BALANCE_ADJS-INSERTED",
                visitBalanceAdjId = visitBalanceAdjId,
              ),
            ).also { waitForAnyProcessingToComplete() }
          }

          @Test
          fun `will retrieve the adjustment details from NOMIS`() {
            nomisVisitBalanceApiMock.verify(getRequestedFor(urlPathEqualTo("/visit-balances/visit-balance-adjustment/$visitBalanceAdjId")))
          }

          @Test
          fun `will create the adjustment in DPS`() {
            dpsApiMock.verify(postRequestedFor(urlPathEqualTo("/visits/allocation/prisoner/sync")))
            val request: VisitAllocationPrisonerSyncDto = VisitBalanceDpsApiExtension.getRequestBody(
              postRequestedFor(urlPathEqualTo("/visits/allocation/prisoner/sync")),
            )
            with(request) {
              assertThat(prisonerId).isEqualTo(nomisPrisonNumber)
              assertThat(oldVoBalance).isNull()
              assertThat(changeToVoBalance).isNull()
              assertThat(oldPvoBalance).isNull()
              assertThat(changeToPvoBalance).isNull()
              assertThat(comment).isNull()
              assertThat(adjustmentReasonCode.value).isEqualTo("IEP")
              assertThat(changeLogSource.value).isEqualTo("STAFF")
              assertThat(createdDate).isEqualTo("2025-01-01")
            }
          }

          @Test
          fun `will track telemetry`() {
            verify(telemetryClient).trackEvent(
              eq("visitbalance-adjustment-synchronisation-created-success"),
              check {
                assertThat(it["nomisVisitBalanceAdjustmentId"]).isEqualTo(visitBalanceAdjId.toString())
                assertThat(it["dpsId"]).isEqualTo(nomisPrisonNumber)
              },
              isNull(),
            )
          }
        }
      }
    }
  }

  @Nested
  @DisplayName("OFFENDER_VISIT_BALANCE_ADJS-DELETED")
  inner class VisitBalanceAdjustmentDeleted {
    private val nomisVisitBalanceAdjId = 123456L
    private val nomisPrisonNumber = "A1234BC"

    @Nested
    inner class WhenDeletedInDps {
      @BeforeEach
      fun setUp() {
        visitBalanceOffenderEventsQueue.sendMessage(
          visitBalanceAdjustmentEvent(
            eventType = "OFFENDER_VISIT_BALANCE_ADJS-DELETED",
            visitBalanceAdjId = nomisVisitBalanceAdjId,
            auditModuleName = "DPS_SYNCHRONISATION",
          ),
        ).also { waitForAnyProcessingToComplete() }
      }

      @Test
      fun `will track telemetry`() {
        verify(telemetryClient).trackEvent(
          eq("visitbalance-adjustment-synchronisation-deleted-unexpected"),
          check {
            assertThat(it["nomisVisitBalanceAdjustmentId"]).isEqualTo(nomisVisitBalanceAdjId.toString())
            assertThat(it["dpsId"]).isEqualTo(nomisPrisonNumber)
          },
          isNull(),
        )
      }
    }

    @Nested
    inner class WhenDeletedInNomis {
      private val nomisVisitBalanceAdjId = 123456L
      private val nomisPrisonNumber = "A1234BC"

      @BeforeEach
      fun setUp() {
        visitBalanceOffenderEventsQueue.sendMessage(
          visitBalanceAdjustmentEvent(
            eventType = "OFFENDER_VISIT_BALANCE_ADJS-DELETED",
            visitBalanceAdjId = nomisVisitBalanceAdjId,
            auditModuleName = "NOMIS_SYNCHRONISATION",
          ),
        ).also { waitForAnyProcessingToComplete() }
      }

      @Test
      fun `will track telemetry`() {
        verify(telemetryClient).trackEvent(
          eq("visitbalance-adjustment-synchronisation-deleted-unexpected"),
          check {
            assertThat(it["nomisVisitBalanceAdjustmentId"]).isEqualTo(nomisVisitBalanceAdjId.toString())
            assertThat(it["dpsId"]).isEqualTo(nomisPrisonNumber)
          },
          isNull(),
        )
      }
    }
  }
}

fun visitBalanceAdjustmentEvent(
  eventType: String = "OFFENDER_VISIT_BALANCE_ADJS-INSERTED",
  visitBalanceAdjId: Long,
  auditModuleName: String = "OIDVIORD",
) = // language=JSON
  """{
    "MessageId": "ae06c49e-1f41-4b9f-b2f2-dcca610d02cd", "Type": "Notification", "Timestamp": "2019-10-21T14:01:18.500Z", 
    "Message": "{\"eventId\":\"5958295\",\"eventType\":\"$eventType\",\"eventDatetime\":\"2019-10-21T15:00:25.489964\",\"bookingId\":1215724,\"visitBalanceAdjustmentId\": \"$visitBalanceAdjId\",\"offenderIdDisplay\":\"A1234BC\",\"offenderId\":2581911,\"rootOffenderId\":2581911,\"auditModuleName\":\"$auditModuleName\",\"nomisEventType\":\"$eventType\" }",
    "TopicArn": "arn:aws:sns:eu-west-1:000000000000:offender_events", 
    "MessageAttributes": {
      "eventType": {"Type": "String", "Value": "$eventType"}, 
      "id": {"Type": "String", "Value": "8b07cbd9-0820-0a0f-c32f-a9429b618e0b"}, 
      "contentType": {"Type": "String", "Value": "text/plain;charset=UTF-8"}, 
      "timestamp": {"Type": "Number.java.lang.Long", "Value": "1571666478344"}
    }
}
  """.trimIndent()
