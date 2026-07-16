package uk.gov.justice.digital.hmpps.prisonerfromnomismigration.movements.court

import org.springframework.boot.context.properties.ConfigurationProperties

@ConfigurationProperties(prefix = "feature.court-scheduler")
class CourtSchedulerFeatureSwitches(
  val ignoreInsertAndUpdateSentencingEvents: Boolean = false,
  val ignoreDeletedSentencingEvents: Boolean = false,
)
