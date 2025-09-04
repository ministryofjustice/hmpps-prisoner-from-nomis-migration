package uk.gov.justice.digital.hmpps.prisonerfromnomismigration.persistence.repository

import org.springframework.data.r2dbc.repository.Modifying
import org.springframework.data.r2dbc.repository.Query
import org.springframework.data.repository.kotlin.CoroutineCrudRepository
import org.springframework.stereotype.Repository
import org.springframework.transaction.annotation.Transactional

@Repository
@Transactional
interface TransactionIdBufferRepository : CoroutineCrudRepository<TransactionIdBuffer, Long> {
  @Modifying
  @Query("INSERT INTO TRANSACTION_ID_BUFFER VALUES (:id) ON CONFLICT ON CONSTRAINT TRANSACTION_ID_BUFFER_ID_PKEY DO NOTHING")
  suspend fun addId(transactionId: Long): Int
}
