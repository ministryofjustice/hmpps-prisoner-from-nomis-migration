package uk.gov.justice.digital.hmpps.prisonerfromnomismigration.activities

import java.time.LocalDate

data class ActivitiesMigrationRequest(val courseActivityId: Long, val activityStartDate: LocalDate, val hasScheduleRules: Boolean)
