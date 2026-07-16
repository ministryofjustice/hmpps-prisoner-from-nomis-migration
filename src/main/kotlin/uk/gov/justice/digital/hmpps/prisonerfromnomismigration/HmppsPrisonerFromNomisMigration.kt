package uk.gov.justice.digital.hmpps.prisonerfromnomismigration

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.context.properties.ConfigurationPropertiesScan
import org.springframework.boot.runApplication

@SpringBootApplication
@ConfigurationPropertiesScan
class HmppsPrisonerFromNomisMigration

fun main(args: Array<String>) {
  runApplication<HmppsPrisonerFromNomisMigration>(*args)
}
