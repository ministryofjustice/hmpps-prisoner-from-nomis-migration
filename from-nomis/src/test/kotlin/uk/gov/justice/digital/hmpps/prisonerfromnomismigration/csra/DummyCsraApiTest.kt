package uk.gov.justice.digital.hmpps.prisonerfromnomismigration.csra

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.http.MediaType
import org.springframework.test.web.reactive.server.returnResult
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.integration.SqsIntegrationTestBase
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomisprisoner.model.CsraGetDto
import java.math.BigDecimal
import java.time.LocalDate
import java.time.LocalDateTime

class DummyCsraApiTest : SqsIntegrationTestBase() {
  @Test
  fun testApi() {
    val body: List<CsraReviewDto> = listOf(
      CsraReviewDto(
        bookingId = 101,
        sequenceNumber = 11,
        assessmentDate = LocalDate.now(),
        assessmentType = CsraGetDto.Type.CSR1,
        score = BigDecimal.valueOf(1001),
        status = CsraGetDto.Status.I,
        staffId = 10101,
        createdDateTime = LocalDateTime.now(),
        createdBy = "me",
        reviewDetails = listOf(),
      ),
    )
    val d = webTestClient.post()
      .uri("/csras/migrate/A1122AB")
      .headers(setAuthorisation(roles = listOf("ROLE_PRISONER_CSRA__SYNC__RW")))
      .contentType(MediaType.APPLICATION_JSON)
      .bodyValue(body)
      .exchange()
      .expectStatus().isCreated
      .returnResult<List<MigrationResult>>()
      .responseBody!!
      .blockFirst()!!
    println(d)
    assertThat(d.first().nomisBookingId).isEqualTo(101)
  }
}
