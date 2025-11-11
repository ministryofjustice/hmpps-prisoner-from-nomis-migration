package uk.gov.justice.digital.hmpps.prisonerfromnomismigration.helpers

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

internal class EventAuditedTest {

  data class TestEvent(
    override val auditModuleName: String?,
  ) : EventAudited

  @Nested
  inner class OriginatesInDps {

    @Test
    fun `fails when auditModuleName is null`() {
      val event = TestEvent(null)
      assertThat(event.originatesInDps).isFalse()
    }

    @Test
    fun `fails when auditModuleName is empty`() {
      val event = TestEvent("")
      assertThat(event.originatesInDps).isFalse()
    }

    @Test
    fun `passes when auditModuleName has suffix but starts with DPS_SYNCHRONISATION`() {
      val event = TestEvent("DPS_SYNCHRONISATION_ABC")
      assertThat(event.originatesInDps).isTrue()
    }

    @Test
    fun `fails when auditModuleName has prefix`() {
      val event = TestEvent("XYZ_DPS_SYNCHRONISATION")
      assertThat(event.originatesInDps).isFalse()
    }

    @Test
    fun `fails when auditModuleName is RANDOM`() {
      val event = TestEvent("RANDOM")
      assertThat(event.originatesInDps).isFalse()
    }

    @Test
    fun `passes when auditModuleName when is DPS_SYNCHRONISATION`() {
      val event = TestEvent("DPS_SYNCHRONISATION")
      assertThat(event.originatesInDps).isTrue()
    }
  }

  @Nested
  inner class OriginatesInDpsOrHasMissingAudit {

    @Test
    fun `passes when auditModuleName is null`() {
      val event = TestEvent(null)
      assertThat(event.originatesInDpsOrHasMissingAudit).isTrue()
    }

    @Test
    fun `passes when auditModuleName is empty`() {
      val event = TestEvent("")
      assertThat(event.originatesInDpsOrHasMissingAudit).isTrue()
    }

    @Test
    fun `passes when auditModuleName starts with DPS_SYNCHRONISATION`() {
      val event = TestEvent("DPS_SYNCHRONISATION_ABC")
      assertThat(event.originatesInDpsOrHasMissingAudit).isTrue()
    }

    @Test
    fun `fails when auditModuleName has different prefix`() {
      val event = TestEvent("XYZ_DPS_SYNCHRONISATION")
      assertThat(event.originatesInDpsOrHasMissingAudit).isFalse()
    }

    @Test
    fun `fails when auditModuleName is RANDOM`() {
      val event = TestEvent("RANDOM")
      assertThat(event.originatesInDpsOrHasMissingAudit).isFalse()
    }

    @Test
    fun `passes when auditModuleName when is DPS_SYNCHRONISATION`() {
      val event = TestEvent("DPS_SYNCHRONISATION")
      assertThat(event.originatesInDpsOrHasMissingAudit).isTrue()
    }
  }
}
