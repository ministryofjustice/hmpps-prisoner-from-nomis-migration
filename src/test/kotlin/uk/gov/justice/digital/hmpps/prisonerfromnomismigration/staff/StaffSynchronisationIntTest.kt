package uk.gov.justice.digital.hmpps.prisonerfromnomismigration.staff

import com.github.tomakehurst.wiremock.client.WireMock.deleteRequestedFor
import com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor
import com.github.tomakehurst.wiremock.client.WireMock.putRequestedFor
import com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo
import org.assertj.core.api.Assertions.assertThat
import org.awaitility.kotlin.await
import org.awaitility.kotlin.untilAsserted
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.mockito.Mockito.eq
import org.mockito.kotlin.check
import org.mockito.kotlin.isNull
import org.mockito.kotlin.verify
import org.springframework.beans.factory.annotation.Autowired
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.integration.sendMessage
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.staff.StaffDpsApiExtension.Companion.dpsStaffServer
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.staff.StaffDpsApiMockServer.Companion.verifyUserMigrationRequest

class StaffSynchronisationIntTest(
  @Autowired private val nomisApiMock: StaffNomisApiMockServer,
) : StaffIntegrationTestBase() {

  private val dpsApiMock = dpsStaffServer

  @Nested
  inner class StaffMember {
    val nomisStaffId = 1234L

    @Nested
    @DisplayName("STAFF_MEMBERS-INSERTED")
    inner class StaffMemberCreated {
      @Nested
      inner class WhenCreatedInDps {
        @BeforeEach
        fun setUp() {
          staffOffenderEventsQueue.sendMessage(
            staffEvent(
              eventType = "STAFF_MEMBERS-INSERTED",
              staffId = nomisStaffId,
              auditModuleName = "DPS_SYNCHRONISATION",
            ),
          ).also { waitForAnyProcessingToComplete() }
        }

        @Test
        fun `will track telemetry`() {
          verify(telemetryClient).trackEvent(
            eq("staff-synchronisation-created-skipped"),
            check {
              assertThat(it["nomisStaffId"]).isEqualTo(nomisStaffId.toString())
            },
            isNull(),
          )
        }
      }

      @Nested
      inner class WhenCreatedInNomis {

        @Nested
        inner class HappyPath {

          @BeforeEach
          fun setUp() {
            nomisApiMock.stubGetStaffDetails()
            dpsApiMock.stubSyncStaff()

            staffOffenderEventsQueue.sendMessage(
              staffEvent(
                eventType = "STAFF_MEMBERS-INSERTED",
                staffId = nomisStaffId,
              ),
            ).also { waitForAnyProcessingToComplete() }
          }

          @Test
          fun `will retrieve the staff details from NOMIS`() {
            nomisApiMock.verify(getRequestedFor(urlPathEqualTo("/staff/$nomisStaffId")))
          }

          @Test
          fun `will create the staff in DPS`() {
            dpsApiMock.verify(putRequestedFor(urlPathEqualTo("/prison-users/staff")))
            verifyUserMigrationRequest()
          }

          @Test
          fun `will track telemetry`() {
            verify(telemetryClient).trackEvent(
              eq("staff-synchronisation-created-success"),
              check {
                assertThat(it["nomisStaffId"]).isEqualTo(nomisStaffId.toString())
              },
              isNull(),
            )
          }
        }
      }
    }

    @Nested
    @DisplayName("STAFF_MEMBERS-UPDATED")
    inner class StaffMemberUpdated {
      @Nested
      inner class WhenUpdatedInDps {
        @BeforeEach
        fun setUp() {
          staffOffenderEventsQueue.sendMessage(
            staffEvent(
              eventType = "STAFF_MEMBERS-UPDATED",
              staffId = nomisStaffId,
              auditModuleName = "DPS_SYNCHRONISATION",
            ),
          ).also { waitForAnyProcessingToComplete() }
        }

        @Test
        fun `will track telemetry`() {
          verify(telemetryClient).trackEvent(
            eq("staff-synchronisation-updated-skipped"),
            check {
              assertThat(it["nomisStaffId"]).isEqualTo(nomisStaffId.toString())
            },
            isNull(),
          )
        }
      }

      @Nested
      inner class WhenUpdatedInNomis {
        @Nested
        inner class HappyPath {

          @BeforeEach
          fun setUp() {
            nomisApiMock.stubGetStaffDetails()
            dpsApiMock.stubSyncStaff()

            staffOffenderEventsQueue.sendMessage(
              staffEvent(
                eventType = "STAFF_MEMBERS-UPDATED",
                staffId = nomisStaffId,
              ),
            ).also { waitForAnyProcessingToComplete() }
          }

          @Test
          fun `will retrieve the staff details from NOMIS`() {
            nomisApiMock.verify(getRequestedFor(urlPathEqualTo("/staff/$nomisStaffId")))
          }

          @Test
          fun `will update the staff in DPS`() {
            dpsApiMock.verify(putRequestedFor(urlPathEqualTo("/prison-users/staff")))
            verifyUserMigrationRequest()
          }

          @Test
          fun `will track telemetry`() {
            verify(telemetryClient).trackEvent(
              eq("staff-synchronisation-updated-success"),
              check {
                assertThat(it["nomisStaffId"]).isEqualTo(nomisStaffId.toString())
              },
              isNull(),
            )
          }
        }
      }
    }

    @Nested
    @DisplayName("STAFF_MEMBERS-DELETED")
    inner class StaffMemberDeleted {
      @Nested
      inner class WhenDeletedInDps {
        @BeforeEach
        fun setUp() {
          staffOffenderEventsQueue.sendMessage(
            staffEvent(
              eventType = "STAFF_MEMBERS-DELETED",
              staffId = nomisStaffId,
              auditModuleName = "DPS_SYNCHRONISATION",
            ),
          ).also { waitForAnyProcessingToComplete() }
        }

        @Test
        fun `will track telemetry`() {
          verify(telemetryClient).trackEvent(
            eq("staff-synchronisation-deleted-skipped"),
            check {
              assertThat(it["nomisStaffId"]).isEqualTo(nomisStaffId.toString())
            },
            isNull(),
          )
        }
      }

      @Nested
      inner class WhenDeletedInNomis {

        @BeforeEach
        fun setUp() {
          nomisApiMock.stubGetStaffDetails()
          dpsApiMock.stubDeleteStaff()

          staffOffenderEventsQueue.sendMessage(
            staffEvent(
              eventType = "STAFF_MEMBERS-DELETED",
              staffId = nomisStaffId,
            ),
          ).also { waitForAnyProcessingToComplete() }
        }

        @Nested
        inner class HappyPath {

          @Test
          fun `will delete Staff in DPS`() {
            await untilAsserted {
              dpsStaffServer.verify(
                1,
                deleteRequestedFor(urlPathEqualTo("/prison-users/staff/$nomisStaffId")),
              )
            }
          }

          @Test
          fun `will track telemetry`() {
            verify(telemetryClient).trackEvent(
              eq("staff-synchronisation-deleted-success"),
              check {
                assertThat(it["nomisStaffId"]).isEqualTo(nomisStaffId.toString())
              },
              isNull(),
            )
          }
        }
      }
    }
  }

  @Nested
  inner class StaffUserAccounts {
    val nomisStaffId = 1234L
    val username = "FRED_GEN"
    val caseloadId = "ASI"

    @Nested
    @DisplayName("STAFF_USER_ACCOUNTS-INSERTED")
    inner class StaffUserAccountCreated {
      @Nested
      inner class WhenCreatedInDps {
        @BeforeEach
        fun setUp() {
          staffOffenderEventsQueue.sendMessage(
            staffUserAccountEvent(
              eventType = "STAFF_USER_ACCOUNTS-INSERTED",
              staffId = nomisStaffId,
              username = username,
              auditModuleName = "DPS_SYNCHRONISATION",
            ),
          ).also { waitForAnyProcessingToComplete() }
        }

        @Test
        fun `will track telemetry`() {
          verify(telemetryClient).trackEvent(
            eq("staff-useraccount-synchronisation-created-skipped"),
            check {
              assertThat(it["nomisStaffId"]).isEqualTo(nomisStaffId.toString())
            },
            isNull(),
          )
        }
      }

      @Nested
      inner class WhenCreatedInNomis {

        @BeforeEach
        fun setUp() {
          nomisApiMock.stubGetStaffDetails()
          dpsApiMock.stubSyncStaff()
          staffOffenderEventsQueue.sendMessage(
            staffUserAccountEvent(
              eventType = "STAFF_USER_ACCOUNTS-INSERTED",
              staffId = nomisStaffId,
              username = username,
            ),
          ).also { waitForAnyProcessingToComplete() }
        }

        @Nested
        inner class HappyPath {

          @Test
          fun `will retrieve the staff details from NOMIS`() {
            nomisApiMock.verify(getRequestedFor(urlPathEqualTo("/staff/$nomisStaffId")))
          }

          @Test
          fun `will update the staff in DPS`() {
            dpsApiMock.verify(putRequestedFor(urlPathEqualTo("/prison-users/staff")))
            verifyUserMigrationRequest()
          }

          @Test
          fun `will track telemetry`() {
            verify(telemetryClient).trackEvent(
              eq("staff-useraccount-synchronisation-created-success"),
              check {
                assertThat(it["nomisStaffId"]).isEqualTo(nomisStaffId.toString())
                assertThat(it["username"]).isEqualTo(username)
              },
              isNull(),
            )
          }
        }
      }
    }

    @Nested
    @DisplayName("STAFF_USER_ACCOUNTS-UPDATED")
    inner class StaffUserAccountUpdated {
      @Nested
      inner class WhenUpdatedInDps {
        @BeforeEach
        fun setUp() {
          staffOffenderEventsQueue.sendMessage(
            staffUserAccountEvent(
              eventType = "STAFF_USER_ACCOUNTS-UPDATED",
              staffId = nomisStaffId,
              username = username,
              auditModuleName = "DPS_SYNCHRONISATION",
            ),
          ).also { waitForAnyProcessingToComplete() }
        }

        @Test
        fun `will track telemetry`() {
          verify(telemetryClient).trackEvent(
            eq("staff-useraccount-synchronisation-updated-skipped"),
            check {
              assertThat(it["nomisStaffId"]).isEqualTo(nomisStaffId.toString())
              assertThat(it["username"]).isEqualTo(username)
            },
            isNull(),
          )
        }
      }

      @Nested
      inner class WhenUpdatedInNomis {

        @BeforeEach
        fun setUp() {
          nomisApiMock.stubGetStaffDetails()
          dpsApiMock.stubSyncStaff()
          staffOffenderEventsQueue.sendMessage(
            staffUserAccountEvent(
              eventType = "STAFF_USER_ACCOUNTS-UPDATED",
              staffId = nomisStaffId,
              username = username,
            ),
          ).also { waitForAnyProcessingToComplete() }
        }

        @Nested
        inner class HappyPath {

          @Test
          fun `will retrieve the staff details from NOMIS`() {
            nomisApiMock.verify(getRequestedFor(urlPathEqualTo("/staff/$nomisStaffId")))
          }

          @Test
          fun `will update the staff in DPS`() {
            dpsApiMock.verify(putRequestedFor(urlPathEqualTo("/prison-users/staff")))
            verifyUserMigrationRequest()
          }

          @Test
          fun `will track telemetry`() {
            verify(telemetryClient).trackEvent(
              eq("staff-useraccount-synchronisation-updated-success"),
              check {
                assertThat(it["nomisStaffId"]).isEqualTo(nomisStaffId.toString())
                assertThat(it["username"]).isEqualTo(username)
              },
              isNull(),
            )
          }
        }
      }
    }

    @Nested
    @DisplayName("STAFF_USER_ACCOUNTS-DELETED")
    inner class StaffUserAccountDeleted {
      @Nested
      inner class WhenDeletedInDps {
        @BeforeEach
        fun setUp() {
          staffOffenderEventsQueue.sendMessage(
            staffUserAccountEvent(
              eventType = "STAFF_USER_ACCOUNTS-DELETED",
              staffId = nomisStaffId,
              username = username,
              auditModuleName = "DPS_SYNCHRONISATION",
            ),
          ).also { waitForAnyProcessingToComplete() }
        }

        @Test
        fun `will track telemetry`() {
          verify(telemetryClient).trackEvent(
            eq("staff-useraccount-synchronisation-deleted-skipped"),
            check {
              assertThat(it["nomisStaffId"]).isEqualTo(nomisStaffId.toString())
              assertThat(it["username"]).isEqualTo(username)
            },
            isNull(),
          )
        }
      }

      @Nested
      inner class WhenDeletedInNomis {

        @BeforeEach
        fun setUp() {
          nomisApiMock.stubGetStaffDetails()
          dpsApiMock.stubSyncStaff()
          staffOffenderEventsQueue.sendMessage(
            staffUserAccountEvent(
              eventType = "STAFF_USER_ACCOUNTS-DELETED",
              staffId = nomisStaffId,
              username = username,
            ),
          ).also { waitForAnyProcessingToComplete() }
        }

        @Nested
        inner class HappyPath {

          @Test
          fun `will retrieve the staff details from NOMIS`() {
            nomisApiMock.verify(getRequestedFor(urlPathEqualTo("/staff/$nomisStaffId")))
          }

          @Test
          fun `will update the staff in DPS`() {
            dpsApiMock.verify(putRequestedFor(urlPathEqualTo("/prison-users/staff")))
            verifyUserMigrationRequest()
          }

          @Test
          fun `will track telemetry`() {
            verify(telemetryClient).trackEvent(
              eq("staff-useraccount-synchronisation-deleted-success"),
              check {
                assertThat(it["nomisStaffId"]).isEqualTo(nomisStaffId.toString())
                assertThat(it["username"]).isEqualTo(username)
              },
              isNull(),
            )
          }
        }
      }
    }
  }

  @Nested
  inner class InternetAddressesStaff {
    val nomisStaffId = 1234L
    val nomisInternetAddressStaffId = 5678L
    val username = "FRED_GEN"
    val caseloadId = "ASI"

    @Nested
    @DisplayName("INTERNET_ADDRESSES_STAFF-INSERTED")
    inner class InternetAddressesStaffCreated {
      @Nested
      inner class WhenCreatedInDps {
        @BeforeEach
        fun setUp() {
          staffOffenderEventsQueue.sendMessage(
            internetAddressesStaffEvent(
              eventType = "INTERNET_ADDRESSES_STAFF-INSERTED",
              staffId = nomisStaffId,
              internetAddressId = nomisInternetAddressStaffId,
              auditModuleName = "DPS_SYNCHRONISATION",
            ),
          ).also { waitForAnyProcessingToComplete() }
        }

        @Test
        fun `will track telemetry`() {
          verify(telemetryClient).trackEvent(
            eq("staffinternetaddresses-synchronisation-created-notimplemented"),
            check {
              assertThat(it["nomisStaffId"]).isEqualTo(nomisStaffId.toString())
            },
            isNull(),
          )
        }
      }

      @Nested
      inner class WhenCreatedInNomis {

        @BeforeEach
        fun setUp() {
          staffOffenderEventsQueue.sendMessage(
            internetAddressesStaffEvent(
              eventType = "INTERNET_ADDRESSES_STAFF-INSERTED",
              staffId = nomisStaffId,
              internetAddressId = nomisInternetAddressStaffId,
            ),
          ).also { waitForAnyProcessingToComplete() }
        }

        @Nested
        inner class HappyPath {

          @Test
          fun `will track telemetry`() {
            verify(telemetryClient).trackEvent(
              eq("staffinternetaddresses-synchronisation-created-notimplemented"),
              check {
                assertThat(it["nomisStaffId"]).isEqualTo(nomisStaffId.toString())
                assertThat(it["internetAddressId"]).isEqualTo(nomisInternetAddressStaffId.toString())
              },
              isNull(),
            )
          }
        }
      }
    }

    @Nested
    @DisplayName("INTERNET_ADDRESSES_STAFF-UPDATED")
    inner class InternetAddressesStaffUpdated {
      @Nested
      inner class WhenUpdatedInDps {
        @BeforeEach
        fun setUp() {
          staffOffenderEventsQueue.sendMessage(
            internetAddressesStaffEvent(
              eventType = "INTERNET_ADDRESSES_STAFF-UPDATED",
              staffId = nomisStaffId,
              internetAddressId = nomisInternetAddressStaffId,
              auditModuleName = "DPS_SYNCHRONISATION",
            ),
          ).also { waitForAnyProcessingToComplete() }
        }

        @Test
        fun `will track telemetry`() {
          verify(telemetryClient).trackEvent(
            eq("staffinternetaddresses-synchronisation-updated-notimplemented"),
            check {
              assertThat(it["nomisStaffId"]).isEqualTo(nomisStaffId.toString())
              assertThat(it["internetAddressId"]).isEqualTo(nomisInternetAddressStaffId.toString())
            },
            isNull(),
          )
        }
      }

      @Nested
      inner class WhenUpdatedInNomis {

        @BeforeEach
        fun setUp() {
          staffOffenderEventsQueue.sendMessage(
            internetAddressesStaffEvent(
              eventType = "INTERNET_ADDRESSES_STAFF-UPDATED",
              staffId = nomisStaffId,
              internetAddressId = nomisInternetAddressStaffId,
            ),
          ).also { waitForAnyProcessingToComplete() }
        }

        @Nested
        inner class HappyPath {

          @Test
          fun `will track telemetry`() {
            verify(telemetryClient).trackEvent(
              eq("staffinternetaddresses-synchronisation-updated-notimplemented"),
              check {
                assertThat(it["nomisStaffId"]).isEqualTo(nomisStaffId.toString())
                assertThat(it["internetAddressId"]).isEqualTo(nomisInternetAddressStaffId.toString())
              },
              isNull(),
            )
          }
        }
      }
    }

    @Nested
    @DisplayName("INTERNET_ADDRESSES_STAFF-DELETED")
    inner class InternetAddressesStaffDeleted {
      @Nested
      inner class WhenDeletedInDps {
        @BeforeEach
        fun setUp() {
          staffOffenderEventsQueue.sendMessage(
            internetAddressesStaffEvent(
              eventType = "INTERNET_ADDRESSES_STAFF-DELETED",
              staffId = nomisStaffId,
              internetAddressId = nomisInternetAddressStaffId,
              auditModuleName = "DPS_SYNCHRONISATION",
            ),
          ).also { waitForAnyProcessingToComplete() }
        }

        @Test
        fun `will track telemetry`() {
          verify(telemetryClient).trackEvent(
            eq("staffinternetaddresses-synchronisation-deleted-notimplemented"),
            check {
              assertThat(it["nomisStaffId"]).isEqualTo(nomisStaffId.toString())
              assertThat(it["internetAddressId"]).isEqualTo(nomisInternetAddressStaffId.toString())
            },
            isNull(),
          )
        }
      }

      @Nested
      inner class WhenDeletedInNomis {

        @BeforeEach
        fun setUp() {
          staffOffenderEventsQueue.sendMessage(
            internetAddressesStaffEvent(
              eventType = "INTERNET_ADDRESSES_STAFF-DELETED",
              staffId = nomisStaffId,
              internetAddressId = nomisInternetAddressStaffId,
            ),
          ).also { waitForAnyProcessingToComplete() }
        }

        @Nested
        inner class HappyPath {

          @Test
          fun `will track telemetry`() {
            verify(telemetryClient).trackEvent(
              eq("staffinternetaddresses-synchronisation-deleted-notimplemented"),
              check {
                assertThat(it["nomisStaffId"]).isEqualTo(nomisStaffId.toString())
                assertThat(it["internetAddressId"]).isEqualTo(nomisInternetAddressStaffId.toString())
              },
              isNull(),
            )
          }
        }
      }
    }
  }

  @Nested
  inner class UserAccessibleCaseloads {
    val username = "FRED_GEN"
    val caseloadId = "ASI"

    @Nested
    @DisplayName("USER_ACCESSIBLE_CASELOADS-INSERTED")
    inner class UserAccessibleCaseloadCreated {
      @Nested
      inner class WhenCreatedInDps {
        @BeforeEach
        fun setUp() {
          staffOffenderEventsQueue.sendMessage(
            userAccessibleCaseloadEvent(
              eventType = "USER_ACCESSIBLE_CASELOADS-INSERTED",
              username = username,
              caseloadId = caseloadId,
              auditModuleName = "DPS_SYNCHRONISATION",
            ),
          ).also { waitForAnyProcessingToComplete() }
        }

        @Test
        fun `will track telemetry`() {
          verify(telemetryClient).trackEvent(
            eq("useraccessiblecaseloads-synchronisation-created-notimplemented"),
            check {
              assertThat(it["username"]).isEqualTo(username)
              assertThat(it["caseloadId"]).isEqualTo(caseloadId)
            },
            isNull(),
          )
        }
      }

      @Nested
      inner class WhenCreatedInNomis {

        @BeforeEach
        fun setUp() {
          staffOffenderEventsQueue.sendMessage(
            userAccessibleCaseloadEvent(
              eventType = "USER_ACCESSIBLE_CASELOADS-INSERTED",
              username = username,
              caseloadId = caseloadId,
            ),
          ).also { waitForAnyProcessingToComplete() }
        }

        @Nested
        inner class HappyPath {

          @Test
          fun `will track telemetry`() {
            verify(telemetryClient).trackEvent(
              eq("useraccessiblecaseloads-synchronisation-created-notimplemented"),
              check {
                assertThat(it["username"]).isEqualTo(username)
                assertThat(it["caseloadId"]).isEqualTo(caseloadId)
              },
              isNull(),
            )
          }
        }
      }
    }

    @Nested
    @DisplayName("USER_ACCESSIBLE_CASELOADS-DELETED")
    inner class UserAccessibleCaseloadDeleted {
      @Nested
      inner class WhenDeletedInDps {
        @BeforeEach
        fun setUp() {
          staffOffenderEventsQueue.sendMessage(
            userAccessibleCaseloadEvent(
              eventType = "USER_ACCESSIBLE_CASELOADS-DELETED",
              username = username,
              caseloadId = caseloadId,
              auditModuleName = "DPS_SYNCHRONISATION",
            ),
          ).also { waitForAnyProcessingToComplete() }
        }

        @Test
        fun `will track telemetry`() {
          verify(telemetryClient).trackEvent(
            eq("useraccessiblecaseloads-synchronisation-deleted-notimplemented"),
            check {
              assertThat(it["username"]).isEqualTo(username)
              assertThat(it["caseloadId"]).isEqualTo(caseloadId)
            },
            isNull(),
          )
        }
      }

      @Nested
      inner class WhenDeletedInNomis {

        @BeforeEach
        fun setUp() {
          staffOffenderEventsQueue.sendMessage(
            userAccessibleCaseloadEvent(
              eventType = "USER_ACCESSIBLE_CASELOADS-DELETED",
              username = username,
              caseloadId = caseloadId,
            ),
          ).also { waitForAnyProcessingToComplete() }
        }

        @Nested
        inner class HappyPath {

          @Test
          fun `will track telemetry`() {
            verify(telemetryClient).trackEvent(
              eq("useraccessiblecaseloads-synchronisation-deleted-notimplemented"),
              check {
                assertThat(it["username"]).isEqualTo(username)
                assertThat(it["caseloadId"]).isEqualTo(caseloadId)
              },
              isNull(),
            )
          }
        }
      }
    }
  }

  @Nested
  inner class UserCaseloadRoles {
    val username = "FRED_GEN"
    val caseloadId = "ASI"
    val roleCode = "ROLE_ADD_USER"

    @Nested
    @DisplayName("USER_CASELOAD_ROLES-INSERTED")
    inner class UserCaseloadRoleCreated {
      @Nested
      inner class WhenCreatedInDps {
        @BeforeEach
        fun setUp() {
          staffOffenderEventsQueue.sendMessage(
            userCaseloadRoleEvent(
              eventType = "USER_CASELOAD_ROLES-INSERTED",
              username = username,
              caseloadId = caseloadId,
              roleCode = roleCode,
              auditModuleName = "DPS_SYNCHRONISATION",
            ),
          ).also { waitForAnyProcessingToComplete() }
        }

        @Test
        fun `will track telemetry`() {
          verify(telemetryClient).trackEvent(
            eq("usercaseloadroles-synchronisation-created-notimplemented"),
            check {
              assertThat(it["username"]).isEqualTo(username)
              assertThat(it["caseloadId"]).isEqualTo(caseloadId)
              assertThat(it["roleCode"]).isEqualTo(roleCode)
            },
            isNull(),
          )
        }
      }

      @Nested
      inner class WhenCreatedInNomis {

        @BeforeEach
        fun setUp() {
          staffOffenderEventsQueue.sendMessage(
            userCaseloadRoleEvent(
              eventType = "USER_CASELOAD_ROLES-INSERTED",
              username = username,
              caseloadId = caseloadId,
              roleCode = roleCode,
            ),
          ).also { waitForAnyProcessingToComplete() }
        }

        @Nested
        inner class HappyPath {

          @Test
          fun `will track telemetry`() {
            verify(telemetryClient).trackEvent(
              eq("usercaseloadroles-synchronisation-created-notimplemented"),
              check {
                assertThat(it["username"]).isEqualTo(username)
                assertThat(it["caseloadId"]).isEqualTo(caseloadId)
                assertThat(it["roleCode"]).isEqualTo(roleCode)
              },
              isNull(),
            )
          }
        }
      }
    }

    @Nested
    @DisplayName("USER_CASELOAD_ROLES-DELETED")
    inner class UserCaseloadRoleDeleted {
      @Nested
      inner class WhenDeletedInDps {
        @BeforeEach
        fun setUp() {
          staffOffenderEventsQueue.sendMessage(
            userCaseloadRoleEvent(
              eventType = "USER_CASELOAD_ROLES-DELETED",
              username = username,
              caseloadId = caseloadId,
              roleCode = roleCode,
              auditModuleName = "DPS_SYNCHRONISATION",
            ),
          ).also { waitForAnyProcessingToComplete() }
        }

        @Test
        fun `will track telemetry`() {
          verify(telemetryClient).trackEvent(
            eq("usercaseloadroles-synchronisation-deleted-notimplemented"),
            check {
              assertThat(it["username"]).isEqualTo(username)
              assertThat(it["caseloadId"]).isEqualTo(caseloadId)
              assertThat(it["roleCode"]).isEqualTo(roleCode)
            },
            isNull(),
          )
        }
      }

      @Nested
      inner class WhenDeletedInNomis {

        @BeforeEach
        fun setUp() {
          staffOffenderEventsQueue.sendMessage(
            userCaseloadRoleEvent(
              eventType = "USER_CASELOAD_ROLES-DELETED",
              username = username,
              caseloadId = caseloadId,
              roleCode = roleCode,
            ),
          ).also { waitForAnyProcessingToComplete() }
        }

        @Nested
        inner class HappyPath {

          @Test
          fun `will track telemetry`() {
            verify(telemetryClient).trackEvent(
              eq("usercaseloadroles-synchronisation-deleted-notimplemented"),
              check {
                assertThat(it["username"]).isEqualTo(username)
                assertThat(it["caseloadId"]).isEqualTo(caseloadId)
                assertThat(it["roleCode"]).isEqualTo(roleCode)
              },
              isNull(),
            )
          }
        }
      }
    }
  }
}

fun staffEvent(
  eventType: String,
  staffId: Long,
  auditModuleName: String = "OUUUSERS",
) = // language=JSON
  """{
    "MessageId": "ae06c49e-1f41-4b9f-b2f2-dcca610d02cd", "Type": "Notification", "Timestamp": "2019-10-21T14:01:18.500Z", 
    "Message": "{\"eventType\":\"$eventType\",\"eventDatetime\":\"2019-10-21T15:00:25.489964\",\"staffId\": $staffId,\"auditModuleName\":\"$auditModuleName\",\"nomisEventType\":\"$eventType\" }",
    "TopicArn": "arn:aws:sns:eu-west-1:000000000000:offender_events", 
    "MessageAttributes": {
      "eventType": {"Type": "String", "Value": "$eventType"}, 
      "id": {"Type": "String", "Value": "8b07cbd9-0820-0a0f-c32f-a9429b618e0b"}, 
      "contentType": {"Type": "String", "Value": "text/plain;charset=UTF-8"}, 
      "timestamp": {"Type": "Number.java.lang.Long", "Value": "1571666478344"}
    }
}
  """.trimIndent()

fun staffUserAccountEvent(
  eventType: String,
  staffId: Long,
  username: String,
  auditModuleName: String = "OUUUSERS",
) = // language=JSON
  """{
    "MessageId": "ae06c49e-1f41-4b9f-b2f2-dcca610d02cd", "Type": "Notification", "Timestamp": "2019-10-21T14:01:18.500Z", 
    "Message": "{\"eventType\":\"$eventType\",\"eventDatetime\":\"2019-10-21T15:00:25.489964\",\"staffId\": $staffId,\"username\": \"$username\",\"auditModuleName\":\"$auditModuleName\",\"nomisEventType\":\"$eventType\" }",
    "TopicArn": "arn:aws:sns:eu-west-1:000000000000:offender_events", 
    "MessageAttributes": {
      "eventType": {"Type": "String", "Value": "$eventType"}, 
      "id": {"Type": "String", "Value": "8b07cbd9-0820-0a0f-c32f-a9429b618e0b"}, 
      "contentType": {"Type": "String", "Value": "text/plain;charset=UTF-8"}, 
      "timestamp": {"Type": "Number.java.lang.Long", "Value": "1571666478344"}
    }
}
  """.trimIndent()

fun internetAddressesStaffEvent(
  eventType: String,
  staffId: Long,
  internetAddressId: Long,
  auditModuleName: String = "OUUUSERS",
) = // language=JSON
  """{
    "MessageId": "ae06c49e-1f41-4b9f-b2f2-dcca610d02cd", "Type": "Notification", "Timestamp": "2019-10-21T14:01:18.500Z", 
    "Message": "{\"eventType\":\"$eventType\",\"eventDatetime\":\"2019-10-21T15:00:25.489964\",\"staffId\": $staffId,\"internetAddressId\": $internetAddressId,\"auditModuleName\":\"$auditModuleName\",\"nomisEventType\":\"$eventType\" }",
    "TopicArn": "arn:aws:sns:eu-west-1:000000000000:offender_events", 
    "MessageAttributes": {
      "eventType": {"Type": "String", "Value": "$eventType"}, 
      "id": {"Type": "String", "Value": "8b07cbd9-0820-0a0f-c32f-a9429b618e0b"}, 
      "contentType": {"Type": "String", "Value": "text/plain;charset=UTF-8"}, 
      "timestamp": {"Type": "Number.java.lang.Long", "Value": "1571666478344"}
    }
}
  """.trimIndent()

fun userAccessibleCaseloadEvent(
  eventType: String,
  username: String,
  caseloadId: String,
  auditModuleName: String = "OUUUSERS",
) = // language=JSON
  """{
    "MessageId": "ae06c49e-1f41-4b9f-b2f2-dcca610d02cd", "Type": "Notification", "Timestamp": "2019-10-21T14:01:18.500Z", 
    "Message": "{\"eventType\":\"$eventType\",\"eventDatetime\":\"2019-10-21T15:00:25.489964\",\"username\": \"$username\",\"caseloadId\": \"$caseloadId\",\"auditModuleName\":\"$auditModuleName\",\"nomisEventType\":\"$eventType\" }",
    "TopicArn": "arn:aws:sns:eu-west-1:000000000000:offender_events", 
    "MessageAttributes": {
      "eventType": {"Type": "String", "Value": "$eventType"}, 
      "id": {"Type": "String", "Value": "8b07cbd9-0820-0a0f-c32f-a9429b618e0b"}, 
      "contentType": {"Type": "String", "Value": "text/plain;charset=UTF-8"}, 
      "timestamp": {"Type": "Number.java.lang.Long", "Value": "1571666478344"}
    }
}
  """.trimIndent()

fun userCaseloadRoleEvent(
  eventType: String,
  username: String,
  caseloadId: String,
  roleCode: String,
  auditModuleName: String = "OUUUSERS",
) = // language=JSON
  """{
    "MessageId": "ae06c49e-1f41-4b9f-b2f2-dcca610d02cd", "Type": "Notification", "Timestamp": "2019-10-21T14:01:18.500Z", 
    "Message": "{\"eventType\":\"$eventType\",\"eventDatetime\":\"2019-10-21T15:00:25.489964\",\"username\": \"$username\",\"caseloadId\": \"$caseloadId\",\"roleCode\": \"$roleCode\",\"auditModuleName\":\"$auditModuleName\",\"nomisEventType\":\"$eventType\" }",
    "TopicArn": "arn:aws:sns:eu-west-1:000000000000:offender_events", 
    "MessageAttributes": {
      "eventType": {"Type": "String", "Value": "$eventType"}, 
      "id": {"Type": "String", "Value": "8b07cbd9-0820-0a0f-c32f-a9429b618e0b"}, 
      "contentType": {"Type": "String", "Value": "text/plain;charset=UTF-8"}, 
      "timestamp": {"Type": "Number.java.lang.Long", "Value": "1571666478344"}
    }
}
  """.trimIndent()
