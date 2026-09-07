package io.github.riadhmnasri.counterpartyrisk.cva

import io.github.riadhmnasri.counterpartyrisk.creditrisk.LOSS_GIVEN_DEFAULT
import io.github.riadhmnasri.counterpartyrisk.entity.Counterparty
import io.github.riadhmnasri.counterpartyrisk.entity.NettingSet
import io.github.riadhmnasri.counterpartyrisk.model.Money
import io.github.riadhmnasri.counterpartyrisk.model.Rate
import java.math.BigDecimal
import java.math.MathContext
import kotlin.math.ceil

private const val DEFAULT_PERIODS_PER_YEAR = 4

/**
 * The assumptions [computeMultiPeriodCva] discretizes the horizon with.
 *
 * [discountRate] defaults to zero (no discounting). [periodsPerYear]
 * controls how finely the horizon is sliced (see [computeMultiPeriodCva]);
 * it does not change the undiscounted result. [lgd] defaults to the same
 * flat [LOSS_GIVEN_DEFAULT] as [computeCva], exposed here for consistency
 * with the `lgd` parameter on `computeExpectedLoss`.
 */
data class CvaAssumptions(
    val discountRate: Rate = Rate.ofDecimal(BigDecimal.ZERO),
    val periodsPerYear: Int = DEFAULT_PERIODS_PER_YEAR,
    val lgd: Rate = LOSS_GIVEN_DEFAULT,
) {
    init {
        require(periodsPerYear > 0) { "periodsPerYear must be positive, got $periodsPerYear" }
    }
}

/**
 * Computes a multi-period Credit Valuation Adjustment, discretizing the
 * horizon (0 to the netting set's longest remaining transaction tenor)
 * into equal periods sized at roughly `1 / periodsPerYear` years each:
 *
 * `CVA = sum over periods of EAD x marginalPD x LGD x discountFactor`
 *
 * where marginal PD for a period comes from a [CreditCurve] built from
 * the counterparty's 1-year PD (cumulative PD at the period's end minus
 * at its start), and `discountFactor = 1 / (1 + discountRate)^t` (simple
 * annual compounding, `t` in years at the period's end).
 *
 * This is a new, more realistic calculation path alongside [computeCva]
 * (a linear single-period approximation), not a replacement for it: both
 * remain simplified teaching tools, not a real multi-period CVA model
 * (which would use a proper exposure profile and a real discount curve).
 *
 * The number of periods is `ceil(maturityYears * periodsPerYear)`, so the
 * horizon always divides into equal-length periods; the effective period
 * length only differs from the nominal `1 / periodsPerYear` when the
 * horizon doesn't divide evenly into it. With `discountRate` at zero, the
 * result does not depend on `periodsPerYear`: the marginal PDs of a
 * telescoping sum always add up to the cumulative PD at maturity,
 * regardless of how finely the horizon is sliced.
 */
fun computeMultiPeriodCva(
    counterparty: Counterparty,
    nettingSet: NettingSet,
    ead: Money,
    assumptions: CvaAssumptions = CvaAssumptions(),
): Money {
    require(nettingSet.transactions.isNotEmpty()) {
        "Cannot compute a CVA horizon for a netting set with no transactions"
    }
    val (discountRate, periodsPerYear, lgd) = assumptions

    val maturityYears = nettingSet.transactions.maxOf { it.remainingTenorYears() }
    val curve = CreditCurve(counterparty.rating.probabilityOfDefault1y)
    val periodCount = ceil(maturityYears.toDouble() * periodsPerYear).toInt().coerceAtLeast(1)
    val periodLength = maturityYears.divide(BigDecimal(periodCount), MathContext.DECIMAL64)

    val cvaPerPeriod =
        (1..periodCount).map { period ->
            val periodStart = periodLength.multiply(BigDecimal(period - 1))
            val periodEnd = periodLength.multiply(BigDecimal(period))
            val marginalPd =
                curve.cumulativeProbabilityOfDefault(periodEnd)
                    .subtract(curve.cumulativeProbabilityOfDefault(periodStart))
            val discountFactor = discountFactorFor(periodEnd, discountRate)

            ead.multiply(marginalPd).multiply(lgd.asDecimal).multiply(discountFactor)
        }

    return Money.sum(cvaPerPeriod, ead.currency)
}

private fun discountFactorFor(
    years: BigDecimal,
    discountRate: Rate,
): BigDecimal {
    val onePlusRate = BigDecimal.ONE.add(discountRate.asDecimal).toDouble()
    return BigDecimal.valueOf(Math.pow(onePlusRate, -years.toDouble()))
}
