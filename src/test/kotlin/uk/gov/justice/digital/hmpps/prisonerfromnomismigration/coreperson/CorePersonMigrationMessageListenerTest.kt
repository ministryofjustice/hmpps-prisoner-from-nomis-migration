package uk.gov.justice.digital.hmpps.prisonerfromnomismigration.coreperson

import kotlinx.coroutines.runBlocking
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.EnumSource
import org.mockito.kotlin.any
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.json.JsonTest
import software.amazon.awssdk.services.sqs.model.Message
import tools.jackson.databind.json.JsonMapper
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.data.MigrationContext
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.listeners.MigrationMessageType
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomisprisoner.model.PrisonNumberAndRootOffenderId
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.service.MigrationMessage
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.service.MigrationType
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.service.MigrationType.CORE_PERSON_ADDRESS_CONTACT
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.service.MigrationType.CORE_PERSON_ALIAS_IDENTIFIER
import java.util.concurrent.ExecutionException

@JsonTest
internal class CorePersonMigrationMessageListenerTest(@Autowired private val jsonMapper: JsonMapper) {
  private val addressContactMigrationService: CorePersonAddressContactMigrationService = mock()
  private val aliasIdentifierMigrationService: CorePersonAliasIdentifierMigrationService = mock()
  private val rawMessage: Message = Message.builder().messageId("123").build()

  private val listener = CorePersonMigrationMessageListener(
    jsonMapper = jsonMapper,
    addressContactMigrationService = addressContactMigrationService,
    aliasIdentifierMigrationService = aliasIdentifierMigrationService,
  )

  @ParameterizedTest
  @EnumSource(value = MigrationType::class, names = ["CORE_PERSON_ADDRESS_CONTACT", "CORE_PERSON_ALIAS_IDENTIFIER"])
  fun `will route the message to the migration service for the context type`(migrationType: MigrationType) {
    val message = migrationMessage(migrationType)

    listener.onMigrationMessage(message, rawMessage).get()

    val (expected, other) = when (migrationType) {
      CORE_PERSON_ADDRESS_CONTACT -> addressContactMigrationService to aliasIdentifierMigrationService
      else -> aliasIdentifierMigrationService to addressContactMigrationService
    }
    runBlocking {
      verify(expected).migrateNomisEntity(eq(message))
      verify(other, never()).migrateNomisEntity(any<String>())
    }
  }

  @Test
  fun `will route each message type to the migration service`() {
    MigrationMessageType.entries.forEach {
      listener.onMigrationMessage(migrationMessage(CORE_PERSON_ALIAS_IDENTIFIER, it), rawMessage).get()
    }

    runBlocking {
      verify(aliasIdentifierMigrationService).divideEntitiesByPage(any<String>())
      verify(aliasIdentifierMigrationService).divideEntitiesByDivision(any<String>())
      verify(aliasIdentifierMigrationService).migrateEntitiesForPage(any<String>())
      verify(aliasIdentifierMigrationService).migrateNomisEntity(any<String>())
      verify(aliasIdentifierMigrationService).migrateStatusCheck(any<String>())
      verify(aliasIdentifierMigrationService).cancelMigrateStatusCheck(any<String>())
      verify(aliasIdentifierMigrationService).retryCreateMapping(any<String>())
      verify(addressContactMigrationService, never()).migrateNomisEntity(any<String>())
    }
  }

  @Test
  fun `will fail when the migration type is not a core person migration`() {
    val future = listener.onMigrationMessage(migrationMessage(MigrationType.VISITS), rawMessage)

    assertThat(future).isCompletedExceptionally
    runCatching { future.get() }.exceptionOrNull().also {
      assertThat(it).isInstanceOf(ExecutionException::class.java)
      assertThat(it!!.cause).isInstanceOf(IllegalStateException::class.java).hasMessageContaining("VISITS")
    }
  }

  private fun migrationMessage(
    migrationType: MigrationType,
    messageType: MigrationMessageType = MigrationMessageType.MIGRATE_ENTITY,
  ): String = jsonMapper.writeValueAsString(
    MigrationMessage(
      type = messageType,
      context = MigrationContext(
        type = migrationType,
        migrationId = "2020-01-01T10:00:00",
        estimatedCount = 1,
        body = PrisonNumberAndRootOffenderId(rootOffenderId = 1, prisonNumber = "A1234BC"),
      ),
    ),
  )
}
