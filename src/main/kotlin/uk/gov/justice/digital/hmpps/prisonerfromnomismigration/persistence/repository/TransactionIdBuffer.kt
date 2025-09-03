package uk.gov.justice.digital.hmpps.prisonerfromnomismigration.persistence.repository

import com.fasterxml.jackson.annotation.JsonIgnore
import org.springframework.beans.factory.annotation.Value
import org.springframework.data.annotation.Id
import org.springframework.data.annotation.Transient
import org.springframework.data.domain.Persistable

/**
 * This table is a cache which serves to limit unnecessary duplicate calls to the DPS transaction sync api
 */
data class TransactionIdBuffer(
  @Id
  val transactionId: Long,
  @Transient
  @Value("false")
  @JsonIgnore
  val new: Boolean = true,
) : Persistable<Long> {

  override fun equals(other: Any?): Boolean {
    if (this === other) return true
    if (other !is TransactionIdBuffer) return false

    if (transactionId != other.transactionId) return false

    return true
  }

  override fun hashCode(): Int = transactionId.hashCode()

  override fun isNew(): Boolean = new

  override fun getId(): Long = transactionId
}

// enum class TransactionProcessStatus { PROCESSING, STARTED, COMPLETED }
