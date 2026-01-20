package uk.gov.justice.digital.hmpps.prisonerfromnomismigration.resources

import org.awaitility.kotlin.await
import org.awaitility.kotlin.matches
import org.awaitility.kotlin.untilCallTo
import org.hamcrest.Matchers.containsInAnyOrder
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.http.MediaType
import software.amazon.awssdk.services.sqs.model.SendMessageRequest
import tools.jackson.databind.json.JsonMapper
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.integration.SqsIntegrationTestBase
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.listeners.EventType
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.listeners.MessageAttributes
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.listeners.SQSMessage
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.service.MigrationType.INCIDENTS
import uk.gov.justice.hmpps.sqs.countMessagesOnQueue

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class MigrateDeadLetterQueueIntTest : SqsIntegrationTestBase() {
  @Autowired
  private lateinit var jsonMapper: JsonMapper

  @Nested
  @DisplayName("GET /migrate/dead-letter-queue/{migrationType}")
  inner class GetDlqMessages {
    val defaultMessageAttributes = MessageAttributes(EventType("test.type", "String"))
    val defaultEvent = HmppsEvent("event-id", "test.type", "event-contents")
    fun testMessage(id: String) = SQSMessage(
      Type = "test.type",
      Message = jsonMapper.writeValueAsString(defaultEvent),
      MessageId = "message-$id",
      MessageAttributes = defaultMessageAttributes,
    )

    @Nested
    inner class Security {
      @Test
      fun `access forbidden when no role`() {
        webTestClient.get().uri("/migrate/dead-letter-queue/{migrationType}", INCIDENTS)
          .headers(setAuthorisation(roles = listOf()))
          .exchange()
          .expectStatus().isForbidden
      }

      @Test
      fun `access forbidden with wrong role`() {
        webTestClient.get().uri("/migrate/dead-letter-queue/{migrationType}", INCIDENTS)
          .headers(setAuthorisation(roles = listOf("BANANAS")))
          .exchange()
          .expectStatus().isForbidden
      }

      @Test
      fun `access unauthorised with no auth token`() {
        webTestClient.get().uri("/migrate/dead-letter-queue/{migrationType}", INCIDENTS)
          .header("Content-Type", "application/json")
          .exchange()
          .expectStatus().isUnauthorized
      }
    }

    @Test
    fun `should get all messages from the specified dlq`() {
      for (i in 1..3) {
        awsSqsIncidentsMigrationDlqClient!!.sendMessage(
          SendMessageRequest.builder().queueUrl(incidentsMigrationDlqUrl!!).messageBody(
            jsonMapper.writeValueAsString(testMessage("id-$i")),
          ).build(),
        )
      }
      await untilCallTo { awsSqsIncidentsMigrationDlqClient!!.countMessagesOnQueue(incidentsMigrationDlqUrl!!).get() } matches { it == 3 }

      webTestClient.get().uri("/migrate/dead-letter-queue/{migrationType}", INCIDENTS)
        .headers(setAuthorisation(roles = listOf("PRISONER_FROM_NOMIS__MIGRATION__RW")))
        .header("Content-Type", "application/json")
        .exchange()
        .expectStatus().isOk
        .expectBody()
        .jsonPath("messagesFoundCount").isEqualTo(3)
        .jsonPath("messagesReturnedCount").isEqualTo(3)
        .jsonPath("messages..body.MessageId").value(
          containsInAnyOrder(
            "message-id-1",
            "message-id-2",
            "message-id-3",
          ),
        )
    }
  }

  @Nested
  @DisplayName("GET /migrate/dead-letter-queue/{migrationType}/count")
  inner class GetDlqMessageCount {
    val defaultMessageAttributes = MessageAttributes(EventType("test.type", "String"))
    val defaultEvent = HmppsEvent("event-id", "test.type", "event-contents")
    fun testMessage(id: String) = SQSMessage(
      Type = "test.type",
      Message = jsonMapper.writeValueAsString(defaultEvent),
      MessageId = "message-$id",
      MessageAttributes = defaultMessageAttributes,
    )

    @Nested
    inner class Security {
      @Test
      fun `access forbidden when no role`() {
        webTestClient.get().uri("/migrate/dead-letter-queue/{migrationType}/count", INCIDENTS)
          .headers(setAuthorisation(roles = listOf()))
          .exchange()
          .expectStatus().isForbidden
      }

      @Test
      fun `access forbidden with wrong role`() {
        webTestClient.get().uri("/migrate/dead-letter-queue/{migrationType}/count", INCIDENTS)
          .headers(setAuthorisation(roles = listOf("BANANAS")))
          .exchange()
          .expectStatus().isForbidden
      }

      @Test
      fun `access unauthorised with no auth token`() {
        webTestClient.get().uri("/migrate/dead-letter-queue/{migrationType}/count", INCIDENTS)
          .header("Content-Type", "application/json")
          .exchange()
          .expectStatus().isUnauthorized
      }
    }

    @Test
    fun `should get count of messages from the specified dlq`() {
      for (i in 1..3) {
        awsSqsIncidentsMigrationDlqClient!!.sendMessage(SendMessageRequest.builder().queueUrl(incidentsMigrationDlqUrl!!).messageBody(jsonMapper.writeValueAsString(testMessage("id-$i"))).build())
      }
      await untilCallTo { awsSqsIncidentsMigrationDlqClient!!.countMessagesOnQueue(incidentsMigrationDlqUrl!!).get() } matches { it == 3 }

      webTestClient.get().uri("/migrate/dead-letter-queue/{migrationType}/count", INCIDENTS)
        .headers(setAuthorisation(roles = listOf("PRISONER_FROM_NOMIS__MIGRATION__RW")))
        .exchange()
        .expectStatus().isOk
        .expectBody().json("3")
    }
  }

  @Nested
  @DisplayName("DELETE /migrate/dead-letter-queue/{migrationType}")
  inner class DeleteDlqMessages {
    val defaultMessageAttributes = MessageAttributes(EventType("test.type", "String"))
    val defaultEvent = HmppsEvent("event-id", "test.type", "event-contents")
    fun testMessage(id: String) = SQSMessage(
      Type = "test.type",
      Message = jsonMapper.writeValueAsString(defaultEvent),
      MessageId = "message-$id",
      MessageAttributes = defaultMessageAttributes,
    )

    @Nested
    inner class Security {
      @Test
      fun `access forbidden when no role`() {
        webTestClient.delete().uri("/migrate/dead-letter-queue/{migrationType}", INCIDENTS)
          .headers(setAuthorisation(roles = listOf()))
          .exchange()
          .expectStatus().isForbidden
      }

      @Test
      fun `access forbidden with wrong role`() {
        webTestClient.delete().uri("/migrate/dead-letter-queue/{migrationType}", INCIDENTS)
          .headers(setAuthorisation(roles = listOf("BANANAS")))
          .exchange()
          .expectStatus().isForbidden
      }

      @Test
      fun `access unauthorised with no auth token`() {
        webTestClient.delete().uri("/migrate/dead-letter-queue/{migrationType}", INCIDENTS)
          .header("Content-Type", "application/json")
          .exchange()
          .expectStatus().isUnauthorized
      }
    }

    @Test
    fun `should delete messages from the dead letter queue`() {
      awsSqsIncidentsMigrationDlqClient!!.sendMessage(SendMessageRequest.builder().queueUrl(incidentsMigrationDlqUrl).messageBody(jsonMapper.writeValueAsString(HmppsEvent("id1", "test.type", "message1"))).build())
      awsSqsIncidentsMigrationDlqClient!!.sendMessage(SendMessageRequest.builder().queueUrl(incidentsMigrationDlqUrl).messageBody(jsonMapper.writeValueAsString(HmppsEvent("id2", "test.type", "message2"))).build())
      await untilCallTo { awsSqsIncidentsMigrationDlqClient!!.countMessagesOnQueue(incidentsMigrationDlqUrl!!).get() } matches { it == 2 }

      webTestClient.delete().uri("/migrate/dead-letter-queue/{migrationType}", INCIDENTS)
        .headers(setAuthorisation(roles = listOf("PRISONER_FROM_NOMIS__MIGRATION__RW")))
        .accept(MediaType.APPLICATION_JSON)
        .exchange()
        .expectStatus().isNoContent

      await untilCallTo { awsSqsIncidentsMigrationDlqClient!!.countMessagesOnQueue(incidentsMigrationDlqUrl!!).get() } matches { it == 0 }
    }
  }
}

data class HmppsEvent(val id: String, val type: String, val contents: String)
