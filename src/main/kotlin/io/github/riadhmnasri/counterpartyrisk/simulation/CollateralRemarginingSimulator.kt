package io.github.riadhmnasri.counterpartyrisk.simulation

import io.github.riadhmnasri.counterpartyrisk.model.Money
import io.github.riadhmnasri.counterpartyrisk.model.Rate
import java.math.BigDecimal

private const val ZERO_EXPOSURE_FLOOR = 0.0

/**
 * A simplified, symmetric CSA-style collateral agreement: the same
 * [threshold] and [minimumTransferAmount] apply both to margin calls
 * (exposure grows past collateral held) and returns (collateral held
 * grows past exposure) — real agreements often set these independently
 * per direction, kept symmetric here for simplicity.
 *
 * [marginingStepsPerYear] defaults to `null`, meaning margining happens
 * at every step of whatever simulation it's used with (the original
 * behavior). Set it to margin less often than the simulation's own time
 * granularity (e.g. `marginingStepsPerYear = 1` for yearly margining on
 * a monthly-stepped simulation) — [SimulationAssumptions.timeStepsPerYear]
 * must then be evenly divisible by it, checked when the simulation runs
 * rather than here, since this type doesn't know what simulation it'll
 * be used with.
 */
data class CollateralAgreement(
    val threshold: Money,
    val minimumTransferAmount: Money = Money.zero(threshold.currency),
    val marginingStepsPerYear: Int? = null,
) {
    init {
        require(threshold.amount >= BigDecimal.ZERO) { "Collateral agreement threshold cannot be negative" }
        require(minimumTransferAmount.amount >= BigDecimal.ZERO) { "Minimum transfer amount cannot be negative" }
        require(threshold.currency == minimumTransferAmount.currency) {
            "Threshold currency (${threshold.currency}) must match minimum transfer amount currency " +
                "(${minimumTransferAmount.currency})"
        }
        require(marginingStepsPerYear == null || marginingStepsPerYear > 0) {
            "marginingStepsPerYear must be positive, got $marginingStepsPerYear"
        }
    }
}

/**
 * A variant of [simulateExposureProfile] that also simulates collateral
 * re-margining under [collateralAgreement], instead of reporting raw,
 * uncollateralized exposure.
 *
 * Exposure is simulated exactly as in [simulateExposureProfile] (a
 * single driftless GBM per path). Collateral held starts fully covering
 * [initialExposure] (a fresh agreement at inception) and is then a
 * **stateful running balance per path**, not an independent random path:
 * at each simulated time step, if `exposure - collateralHeld` exceeds
 * [CollateralAgreement.threshold], a call for the excess is added to
 * collateral held, but only if the call size clears
 * [CollateralAgreement.minimumTransferAmount] (otherwise it is skipped
 * this step, and may only be called later once a larger gap clears the
 * MTA). Symmetrically, if collateral held exceeds exposure by more than
 * the threshold, the excess is returned, same MTA friction. EPE and PFE
 * are then computed from the resulting post-margining net exposure
 * (`max(exposure - collateralHeld, 0)`).
 *
 * Not modeled: a margining frequency different from the simulation's own
 * time step, haircuts or FX on the collateral posted, or any correlation
 * between the exposure risk factor and collateral value (collateral held
 * is a deterministic function of the single exposure path via the
 * margining rule, not a second stochastic risk factor).
 */
fun simulateExposureProfileWithCollateral(
    initialExposure: Money,
    volatility: Rate,
    horizonYears: BigDecimal,
    collateralAgreement: CollateralAgreement,
    assumptions: SimulationAssumptions = SimulationAssumptions(),
): ExposureProfile {
    require(horizonYears > BigDecimal.ZERO) { "horizonYears must be positive, got $horizonYears" }
    require(initialExposure.currency == collateralAgreement.threshold.currency) {
        "Exposure currency (${initialExposure.currency}) must match the collateral agreement's currency " +
            "(${collateralAgreement.threshold.currency})"
    }

    val timeSteps = TimeSteps.of(horizonYears, assumptions.timeStepsPerYear)
    val marginEveryNSteps = marginEveryNSteps(collateralAgreement, assumptions.timeStepsPerYear)
    val s0 = initialExposure.amount.toDouble()
    val exposurePaths = simulateGbmPaths(s0, volatility.asDecimal.toDouble(), timeSteps, assumptions)
    val threshold = collateralAgreement.threshold.amount.toDouble()
    val minimumTransferAmount = collateralAgreement.minimumTransferAmount.amount.toDouble()

    val netExposureAfterMargin =
        Array(assumptions.pathCount) { path ->
            DoubleArray(timeSteps.stepCount + 1).also { netExposure ->
                var collateralHeld = s0
                netExposure[0] = ZERO_EXPOSURE_FLOOR
                for (step in 1..timeSteps.stepCount) {
                    val exposure = exposurePaths[path][step]
                    if (step % marginEveryNSteps == 0) {
                        val gap = exposure - collateralHeld
                        val callAmount = gap - threshold
                        val returnAmount = -threshold - gap
                        when {
                            gap > threshold && callAmount >= minimumTransferAmount -> collateralHeld += callAmount
                            gap < -threshold && returnAmount >= minimumTransferAmount -> collateralHeld -= returnAmount
                        }
                    }
                    netExposure[step] = maxOf(exposure - collateralHeld, ZERO_EXPOSURE_FLOOR)
                }
            }
        }

    return aggregateProfile(netExposureAfterMargin, timeSteps, assumptions.confidence, initialExposure.currency)
}

/**
 * How many simulated time steps separate two margining events: 1 when
 * [CollateralAgreement.marginingStepsPerYear] is `null` (margin every
 * step). Requires [timeStepsPerYear] to be evenly divisible by
 * [CollateralAgreement.marginingStepsPerYear], which also naturally
 * rejects a margining frequency higher than the simulation's own
 * granularity (the division would leave a remainder).
 */
private fun marginEveryNSteps(
    collateralAgreement: CollateralAgreement,
    timeStepsPerYear: Int,
): Int {
    val marginingStepsPerYear = collateralAgreement.marginingStepsPerYear ?: return 1
    require(timeStepsPerYear % marginingStepsPerYear == 0) {
        "timeStepsPerYear ($timeStepsPerYear) must be evenly divisible by " +
            "marginingStepsPerYear ($marginingStepsPerYear)"
    }
    return timeStepsPerYear / marginingStepsPerYear
}
