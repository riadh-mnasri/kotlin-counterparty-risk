package io.github.riadhmnasri.counterpartyrisk.simulation

import io.github.riadhmnasri.counterpartyrisk.entity.NettingSet
import io.github.riadhmnasri.counterpartyrisk.exposure.netContribution
import io.github.riadhmnasri.counterpartyrisk.model.FxHaircutTable
import io.github.riadhmnasri.counterpartyrisk.model.Rate
import java.math.BigDecimal

private const val UNIT_RISK_FACTOR_START = 1.0
private const val MATURITY_TOLERANCE_YEARS = 1e-9

/** What happens to a transaction once it reaches maturity during the simulated horizon. */
enum class RolloverAssumption {
    /**
     * Matured transactions leave the netting set and are not replaced:
     * the contractual, wind-down view. Rollover risk is then left to
     * [ExposureProfile.effectiveExpectedPositiveExposure], whose
     * non-decreasing envelope is designed to capture it.
     */
    RUN_OFF,

    /**
     * Every transaction is renewed at maturity on identical terms, so
     * the netting set's composition never changes over the horizon.
     */
    FULL_ROLLOVER,
}

/**
 * How [simulateNettingSetExposureProfile] treats the netting set itself,
 * bundled into one object to keep the function's parameter count down.
 *
 * [fxHaircutTable] is the same table [io.github.riadhmnasri.counterpartyrisk.exposure.computeExposure]
 * takes, so the simulated profile starts from the same `E*`.
 */
data class NettingSetSimulationOptions(
    val rollover: RolloverAssumption = RolloverAssumption.RUN_OFF,
    val fxHaircutTable: FxHaircutTable = FxHaircutTable.FLAT,
)

/**
 * Monte Carlo simulation of a [nettingSet]'s future exposure over
 * [horizonYears], taking into account that its transactions mature at
 * different dates (each one's [io.github.riadhmnasri.counterpartyrisk.entity.SftTransaction.remainingTenorYears]).
 *
 * Each transaction contributes its own un-floored share of `E*` (exposure
 * minus collateral plus haircut add-ons, the same terms `computeExposure`
 * sums). A single driftless GBM multiplier `M(t)` starting at 1, with
 * annualized [volatility], scales the sum over the transactions still
 * alive at `t`, and the result is floored at zero:
 *
 * `E(t) = max(0, M(t) x sum of contributions of live transactions)`
 *
 * A transaction is alive while `t < remainingTenorYears()`; what happens
 * after that depends on [NettingSetSimulationOptions.rollover]. At `t = 0`
 * the profile equals `computeExposure(nettingSet).eStar`.
 *
 * Under [RolloverAssumption.RUN_OFF], exposure usually steps down as
 * transactions mature, but can also step up: when an over-collateralized
 * transaction matures, the netting benefit it provided to the others goes
 * with it.
 *
 * Same teaching-grade simplifications as [simulateExposureProfile]: one
 * shared risk factor for the whole netting set, no drift, no collateral
 * re-margining, and no new trades beyond today's.
 */
fun simulateNettingSetExposureProfile(
    nettingSet: NettingSet,
    volatility: Rate,
    horizonYears: BigDecimal,
    options: NettingSetSimulationOptions = NettingSetSimulationOptions(),
    assumptions: SimulationAssumptions = SimulationAssumptions(),
): ExposureProfile {
    require(horizonYears > BigDecimal.ZERO) { "horizonYears must be positive, got $horizonYears" }
    val timeSteps = TimeSteps.of(horizonYears, assumptions.timeStepsPerYear)
    val liveNetAmountByStep = liveNetAmountByStep(nettingSet, options, timeSteps)
    val multipliers =
        simulateGbmPaths(UNIT_RISK_FACTOR_START, volatility.asDecimal.toDouble(), timeSteps, assumptions)

    val netExposure =
        Array(assumptions.pathCount) { path ->
            DoubleArray(timeSteps.stepCount + 1) { step ->
                maxOf(multipliers[path][step] * liveNetAmountByStep[step], 0.0)
            }
        }
    return aggregateProfile(netExposure, timeSteps, assumptions.confidence, nettingSet.reportingCurrency)
}

/**
 * The sum of the contributions of the transactions still alive at each
 * time step. Deterministic: maturities are known in advance, only the
 * risk factor scaling them is random.
 */
private fun liveNetAmountByStep(
    nettingSet: NettingSet,
    options: NettingSetSimulationOptions,
    timeSteps: TimeSteps,
): DoubleArray {
    val contributions =
        nettingSet.transactions.map { transaction ->
            transaction.remainingTenorYears().toDouble() to
                netContribution(transaction, options.fxHaircutTable).amount.toDouble()
        }

    return DoubleArray(timeSteps.stepCount + 1) { step ->
        val timeYears = step * timeSteps.dt
        contributions
            .filter { (maturityYears, _) ->
                options.rollover == RolloverAssumption.FULL_ROLLOVER ||
                    timeYears < maturityYears - MATURITY_TOLERANCE_YEARS
            }.sumOf { (_, contribution) -> contribution }
    }
}
