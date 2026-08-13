package uk.gov.justice.digital.hmpps.prisonerfromnomismigration.movements.transfer

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.movements.transfer.TransferScheduleNomisApiMockServer.Companion.transferScheduleOutResponse
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.movements.transfer.TransferScheduleNomisApiMockServer.Companion.transferScheduleWaitlistResponse
import java.time.LocalDateTime

class TransferSchedulerSyncScheduleTest {

  @Nested
  inner class AuditFields {
    private val today = LocalDateTime.now()
    private val yesterday = today.minusDays(1)

    @Test
    fun `should select schedule created by if no waitlist`() {
      val nomisScheduleOut = transferScheduleOutResponse(
        createDateTime = today,
        createUsername = "CREATE_SCHEDULE_USER",
        waitlist = null,
      )

      with(nomisScheduleOut.toDpsRequest()) {
        assertThat(syncUser.username).isEqualTo("CREATE_SCHEDULE_USER")
        assertThat(occurredAt).isEqualTo(today)
      }
    }

    @Test
    fun `should select schedule updated if no waitlist`() {
      val nomisScheduleOut = transferScheduleOutResponse(
        createDateTime = yesterday,
        createUsername = "CREATE_SCHEDULE_USER",
        modifyDateTime = today,
        modifyUserId = "MODIFY_SCHEDULE_USER",
        waitlist = null,
      )

      with(nomisScheduleOut.toDpsRequest()) {
        assertThat(syncUser.username).isEqualTo("MODIFY_SCHEDULE_USER")
        assertThat(occurredAt).isEqualTo(today)
      }
    }

    @Test
    fun `should select schedule updated if after waitlist created`() {
      val nomisScheduleOut = transferScheduleOutResponse(
        createDateTime = yesterday,
        createUsername = "CREATE_SCHEDULE_USER",
        modifyDateTime = today,
        modifyUserId = "MODIFY_SCHEDULE_USER",
        waitlist = transferScheduleWaitlistResponse(
          createDateTime = yesterday.plusHours(1),
          createUsername = "CREATE_WAITLIST_USER",
        ),
      )

      with(nomisScheduleOut.toDpsRequest()) {
        assertThat(syncUser.username).isEqualTo("MODIFY_SCHEDULE_USER")
        assertThat(occurredAt).isEqualTo(today)
      }
    }

    @Test
    fun `should select waitlist created if after schedule created`() {
      val nomisScheduleOut = transferScheduleOutResponse(
        createDateTime = yesterday,
        createUsername = "CREATE_SCHEDULE_USER",
        waitlist = transferScheduleWaitlistResponse(
          createDateTime = today,
          createUsername = "CREATE_WAITLIST_USER",
        ),
      )

      with(nomisScheduleOut.toDpsRequest()) {
        assertThat(syncUser.username).isEqualTo("CREATE_WAITLIST_USER")
        assertThat(occurredAt).isEqualTo(today)
      }
    }

    @Test
    fun `should select waitlist created if after schedule updated`() {
      val nomisScheduleOut = transferScheduleOutResponse(
        createDateTime = yesterday,
        createUsername = "CREATE_SCHEDULE_USER",
        modifyDateTime = yesterday.plusHours(1),
        modifyUserId = "MODIFY_SCHEDULE_USER",
        waitlist = transferScheduleWaitlistResponse(
          createDateTime = today,
          createUsername = "CREATE_WAITLIST_USER",
        ),
      )

      with(nomisScheduleOut.toDpsRequest()) {
        assertThat(syncUser.username).isEqualTo("CREATE_WAITLIST_USER")
        assertThat(occurredAt).isEqualTo(today)
      }
    }

    @Test
    fun `should select waitlist updated if after schedule updated`() {
      val nomisScheduleOut = transferScheduleOutResponse(
        createDateTime = yesterday,
        createUsername = "CREATE_SCHEDULE_USER",
        modifyDateTime = yesterday.plusHours(1),
        modifyUserId = "MODIFY_SCHEDULE_USER",
        waitlist = transferScheduleWaitlistResponse(
          createDateTime = today.minusHours(1),
          createUsername = "CREATE_WAITLIST_USER",
          modifyDateTime = today,
          modifyUserId = "MODIFY_WAITLIST_USER",
        ),
      )

      with(nomisScheduleOut.toDpsRequest()) {
        assertThat(syncUser.username).isEqualTo("MODIFY_WAITLIST_USER")
        assertThat(occurredAt).isEqualTo(today)
      }
    }
  }
}
