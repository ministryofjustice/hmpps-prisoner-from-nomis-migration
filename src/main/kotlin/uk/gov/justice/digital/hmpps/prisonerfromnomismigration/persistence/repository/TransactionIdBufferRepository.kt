package uk.gov.justice.digital.hmpps.prisonerfromnomismigration.persistence.repository

import org.springframework.data.r2dbc.repository.Modifying
import org.springframework.data.r2dbc.repository.Query
import org.springframework.data.repository.kotlin.CoroutineCrudRepository
import org.springframework.stereotype.Repository

@Repository
interface TransactionIdBufferRepository : CoroutineCrudRepository<TransactionIdBuffer, Long> {
  @Modifying
  @Query("insert into TRANSACTION_ID_BUFFER values (:id) ON conflict do nothing")
  fun addId(transactionId: Long): Int
}
