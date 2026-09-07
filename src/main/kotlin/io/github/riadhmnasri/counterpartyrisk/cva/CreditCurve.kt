package io.github.riadhmnasri.counterpartyrisk.cva

import io.github.riadhmnasri.counterpartyrisk.model.Rate
import java.math.BigDecimal

/**
 * A simplified default probability term structure, built from a single
 * 1-year probability of default by assuming a constant (flat) hazard
 * rate: survival to time [years] is `(1 - oneYearProbabilityOfDefault)^years`.
 *
 * This is a standard, well-known technique for extrapolating a single
 * PD point into a curve, not a real market-implied or agency-published
 * multi-tenor credit curve (which would use a different PD at each
 * tenor). Deliberately uses `Double` internally for the fractional-year
 * power computation, since `BigDecimal` has no native fractional
 * exponent and any real CVA engine needs this kind of transcendental math
 * too; the result is converted straight back to `BigDecimal`.
 */
data class CreditCurve(val oneYearProbabilityOfDefault: Rate) {
    private val oneYearSurvivalProbability: Double = 1.0 - oneYearProbabilityOfDefault.asDecimal.toDouble()

    fun survivalProbability(years: BigDecimal): BigDecimal =
        BigDecimal.valueOf(Math.pow(oneYearSurvivalProbability, years.toDouble()))

    fun cumulativeProbabilityOfDefault(years: BigDecimal): BigDecimal =
        BigDecimal.ONE.subtract(survivalProbability(years))
}
