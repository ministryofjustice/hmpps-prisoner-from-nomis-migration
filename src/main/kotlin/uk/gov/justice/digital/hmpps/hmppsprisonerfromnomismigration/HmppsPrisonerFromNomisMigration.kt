package uk.gov.justice.digital.hmpps.hmppsprisonerfromnomismigration

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication

@SpringBootApplication()
class HmppsPrisonerFromNomisMigration

fun main(args: Array<String>) {
  runApplication<HmppsPrisonerFromNomisMigration>(*args)
}
