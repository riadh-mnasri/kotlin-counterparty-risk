package io.github.riadhmnasri.counterpartyrisk.model

import java.math.BigDecimal
import java.math.MathContext

private val PERCENTAGE_DIVISOR = BigDecimal(100)

/**
 * A non-negative rate (a probability, a haircut, an LGD...) stored
 * internally as a decimal fraction. `Rate.ofPercentage(4.0)` and
 * `Rate.ofDecimal(0.04)` both represent 4%.
 */
@ConsistentCopyVisibility
data class Rate private constructor(val asDecimal: BigDecimal) {
    companion object {
        fun ofDecimal(fraction: BigDecimal): Rate {
            require(fraction >= BigDecimal.ZERO) { "Rate cannot be negative, got $fraction" }
            return Rate(fraction.stripTrailingZeros())
        }

        fun ofDecimal(fraction: Double): Rate = ofDecimal(BigDecimal.valueOf(fraction))

        fun ofPercentage(percentage: BigDecimal): Rate =
            ofDecimal(percentage.divide(PERCENTAGE_DIVISOR, MathContext.DECIMAL64))

        fun ofPercentage(percentage: Double): Rate = ofPercentage(BigDecimal.valueOf(percentage))
    }

    fun applyTo(money: Money): Money = money.multiply(asDecimal)
}
