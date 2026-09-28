package uk.gov.justice.digital.hmpps.prisonerfromnomismigration.finance

import java.math.BigDecimal
import java.math.RoundingMode

object MoneySupport {
  private const val MONEY_SCALE = 2
  private const val PENCE_IN_POUND = 100L

  fun poundsToPence(amountInPounds: BigDecimal): Long = toMoneyScale(amountInPounds).movePointRight(MONEY_SCALE).toLong()

  fun penceToPounds(penceValue: Long): BigDecimal = toMoneyScale(
    BigDecimal.valueOf(penceValue).divide(BigDecimal.valueOf(PENCE_IN_POUND)),
  )

  fun toMoneyScale(amount: BigDecimal): BigDecimal = amount.setScale(MONEY_SCALE, RoundingMode.HALF_UP)
}
