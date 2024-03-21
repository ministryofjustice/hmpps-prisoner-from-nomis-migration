package uk.gov.justice.digital.hmpps.prisonerfromnomismigration.alerts

import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.within
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import java.time.LocalDateTime
import java.time.temporal.ChronoUnit

class AlertResponseTransformerTest {

  @Nested
  inner class ToDPSUpdateAlert {
    @Test
    fun `should create empty list for null comment`() {
      val comment = null
      val nomisAlert = alertResponse().copy(comment = comment)
      val dpsAlert = nomisAlert.toDPSUpdateAlert()

      assertThat(dpsAlert.comments).isEmpty()
      assertThat(dpsAlert.description).isNull()
    }

    @Test
    fun `should create empty list for blank comment`() {
      val comment = ""
      val nomisAlert = alertResponse().copy(comment = comment)
      val dpsAlert = nomisAlert.toDPSUpdateAlert()

      assertThat(dpsAlert.comments).isEmpty()
      assertThat(dpsAlert.description).isBlank()
    }

    @Test
    fun `should create empty list for comments with no comment separators`() {
      val comment = """
        This is a comment, but no separators.
        
        But has multiple lines.
      """.trimIndent()
      val nomisAlert = alertResponse().copy(comment = comment)
      val dpsAlert = nomisAlert.toDPSUpdateAlert()

      assertThat(dpsAlert.comments).isEmpty()
      assertThat(dpsAlert.description).isEqualTo(comment)
    }

    @Test
    fun `should divide multiple comments to separate comment elements and the description`() {
      val comment = """
        ** Offenders must not be made aware of the OCG flag status.  Do not Share with offender. **

        This person has been mapped as a member of an Organised Crime Group (OCG). If further information is required to assist in management or re-categorisation decisions, including OPT 2 applications please contact the Prison Intelligence Officer.

        [AMARKE_GEN on 21-03-2024 10:58:51] 
        Comment 1

        [AMARKE_ADM on 21-03-2024 10:59:01] 
        Comment 2

        [QRT123 on 21-03-2024 10:59:25] 
        A long multiple para comment.
        A long multiple para comment.

        A long multiple para comment.
        A long multiple para comment.
      """.trimIndent()

      val nomisAlert = alertResponse().copy(comment = comment)
      val dpsAlert = nomisAlert.toDPSUpdateAlert()

      assertThat(dpsAlert.description).isEqualTo(
        """
        ** Offenders must not be made aware of the OCG flag status.  Do not Share with offender. **

        This person has been mapped as a member of an Organised Crime Group (OCG). If further information is required to assist in management or re-categorisation decisions, including OPT 2 applications please contact the Prison Intelligence Officer.
        """.trimIndent(),
      )

      assertThat(dpsAlert.comments).hasSize(3)
      assertThat(dpsAlert.comments!![0].createdBy).isEqualTo("AMARKE_GEN")
      assertThat(dpsAlert.comments!![0].createdAt).isEqualTo(LocalDateTime.parse("2024-03-21T10:58:51"))
      assertThat(dpsAlert.comments!![0].comment).isEqualTo(
        """
        Comment 1
        """.trimIndent(),
      )
      assertThat(dpsAlert.comments!![1].createdBy).isEqualTo("AMARKE_ADM")
      assertThat(dpsAlert.comments!![1].createdAt).isEqualTo(LocalDateTime.parse("2024-03-21T10:59:01"))
      assertThat(dpsAlert.comments!![1].comment).isEqualTo(
        """
        Comment 2
        """.trimIndent(),
      )
      assertThat(dpsAlert.comments!![2].createdBy).isEqualTo("QRT123")
      assertThat(dpsAlert.comments!![2].createdAt).isEqualTo(LocalDateTime.parse("2024-03-21T10:59:25"))
      assertThat(dpsAlert.comments!![2].comment).isEqualTo(
        """
        A long multiple para comment.
        A long multiple para comment.

        A long multiple para comment.
        A long multiple para comment.
        """.trimIndent(),
      )
    }

    @Test
    fun `can cope lines that look a bit like separators`() {
      val comment = """
        Initial comment

        [added to bad list 21-03-2024 10:58:51] 
      """.trimIndent().trim()

      val nomisAlert = alertResponse().copy(comment = comment)
      val dpsAlert = nomisAlert.toDPSUpdateAlert()
      assertThat(dpsAlert.comments).isEmpty()
      assertThat(dpsAlert.description).isEqualTo(comment)
    }

    @Test
    fun `can cope separators that have bad dates that happen to match the correct format`() {
      val comment = """
        1st comment
        [MARKE on 99-99-9999 99:99:99] 
        2nd comment
      """.trimIndent().trim()

      val nomisAlert = alertResponse().copy(comment = comment)
      val dpsAlert = nomisAlert.toDPSUpdateAlert()
      assertThat(dpsAlert.comments).hasSize(1)
      assertThat(dpsAlert.comments!![0].createdAt).isCloseTo(LocalDateTime.now(), within(10, ChronoUnit.SECONDS))
      assertThat(dpsAlert.description).isEqualTo("1st comment")
    }

    @Test
    fun `will ignore separators that dates in the completely wrong format`() {
      val comment = """
        1st comment
        [MARKE on 2023-01-23 10:10:59] 
        2nd comment
      """.trimIndent().trim()

      val nomisAlert = alertResponse().copy(comment = comment)
      val dpsAlert = nomisAlert.toDPSUpdateAlert()
      assertThat(dpsAlert.comments).isEmpty()
      assertThat(dpsAlert.description).isEqualTo(comment)
    }
  }
}
