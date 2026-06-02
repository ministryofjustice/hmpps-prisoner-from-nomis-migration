package uk.gov.justice.digital.hmpps.prisonerfromnomismigration.helpers

import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomisprisoner.model.PrisonerDetails

// A booking move that is active and moved should result in prisoner-offender-search.prisoner.received event, services that
// processes these events as well as a move booking event might want to ignore the move event
fun PrisonerDetails.shouldReceiveEventHaveBeenRaisedAfterBookingMove() = this.active
