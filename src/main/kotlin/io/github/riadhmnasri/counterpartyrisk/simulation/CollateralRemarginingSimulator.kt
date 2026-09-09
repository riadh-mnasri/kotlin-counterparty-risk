package io.github.riadhmnasri.counterpartyrisk.simulation

import io.github.riadhmnasri.counterpartyrisk.model.AssetClass
import io.github.riadhmnasri.counterpartyrisk.model.Currency
import io.github.riadhmnasri.counterpartyrisk.model.FxHaircutTable
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
 *
 * [collateralAssetClass] defaults to `null`, meaning collateral is
 * treated as cash-equivalent (no haircut, the original behavior). Set it
 * to apply that asset class's [AssetClass.haircut] to every margin call:
 * only `callAmount x (1 - haircut)` of *effective* value is actually
 * recognized, mirroring how [io.github.riadhmnasri.counterpartyrisk.exposure.computeExposure]
 * already treats a haircut as reducing collateral's effective coverage.
 * Returns are not haircut-adjusted (giving back already-discounted
 * effective value is symmetric regardless of asset class).
 *
 * [collateralRiskFactor] defaults to `null`, meaning collateral held only
 * ever changes via margin calls/returns (the original behavior, and the
 * exact same random draw sequence as before it existed). Set it to also
 * mark collateral held to market every simulated step, correlated with
 * the exposure path — see [CorrelatedCollateralRiskFactor].
 *
 * [collateralCurrency] defaults to `null`, meaning no FX mismatch (same
 * as it matching the exposure's own currency). Set it to a different
 * currency to also apply an FX haircut (from [fxHaircutTable]) to every
 * margin call, on top of any [collateralAssetClass] security haircut —
 * the two combine additively (`securityHaircut + fxHaircut`), the same
 * way [io.github.riadhmnasri.counterpartyrisk.exposure.computeExposure]
 * already sums both add-ons for [io.github.riadhmnasri.counterpartyrisk.entity.CollateralPosition].
 */
data class CollateralAgreement(
    val threshold: Money,
    val minimumTransferAmount: Money = Money.zero(threshold.currency),
    val marginingStepsPerYear: Int? = null,
    val collateralAssetClass: AssetClass? = null,
    val collateralRiskFactor: CorrelatedCollateralRiskFactor? = null,
    val collateralCurrency: Currency? = null,
    val fxHaircutTable: FxHaircutTable = FxHaircutTable.FLAT,
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
 * A second GBM risk factor for the *value* of collateral already posted,
 * correlated with the exposure risk factor via [correlationWithExposure]
 * (a plain `BigDecimal` rather than [Rate], since correlation ranges
 * from -1 to 1 and `Rate` disallows negative values). At each simulated
 * step, collateral held is marked to market by this factor's own return,
 * before that step's margining check — see
 * [simulateCorrelatedGbmPathPair] for how the correlated draws are
 * generated.
 *
 * Note that setting this at all draws twice as many random values per
 * step (one per correlated path) as leaving it `null`: even a
 * zero-volatility risk factor changes the exposure path's own random
 * draw sequence compared to not setting it, since the second draw is
 * still consumed. Two runs both setting it (whatever their correlation
 * or volatility) stay comparable against each other, seed for seed.
 */
data class CorrelatedCollateralRiskFactor(
    val volatility: Rate,
    val correlationWithExposure: BigDecimal,
) {
    init {
        require(correlationWithExposure in BigDecimal("-1")..BigDecimal.ONE) {
            "correlationWithExposure must be between -1 and 1, got $correlationWithExposure"
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
 * A margining frequency different from the simulation's own time step,
 * an [AssetClass] security haircut and an FX haircut on the collateral
 * posted (see [CollateralAgreement.collateralAssetClass] and
 * [CollateralAgreement.collateralCurrency]), and a second, correlated
 * risk factor for collateral value between margining events (see
 * [CollateralAgreement.collateralRiskFactor]) are all supported.
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
    val riskFactor = collateralAgreement.collateralRiskFactor
    val exposurePaths: Array<DoubleArray>
    val collateralValuePaths: Array<DoubleArray>?
    if (riskFactor == null) {
        exposurePaths = simulateGbmPaths(s0, volatility.asDecimal.toDouble(), timeSteps, assumptions)
        collateralValuePaths = null
    } else {
        val (e, c) =
            simulateCorrelatedGbmPathPair(
                GbmFactor(s0, volatility.asDecimal.toDouble()),
                GbmFactor(1.0, riskFactor.volatility.asDecimal.toDouble()),
                riskFactor.correlationWithExposure.toDouble(),
                timeSteps,
                assumptions,
            )
        exposurePaths = e
        collateralValuePaths = c
    }
    val threshold = collateralAgreement.threshold.amount.toDouble()
    val minimumTransferAmount = collateralAgreement.minimumTransferAmount.amount.toDouble()
    val securityHaircut = collateralAgreement.collateralAssetClass?.haircut?.asDecimal?.toDouble() ?: 0.0
    val fxHaircut = fxHaircut(collateralAgreement, initialExposure.currency)
    val collateralRecognitionRate = 1.0 - securityHaircut - fxHaircut

    val netExposureAfterMargin =
        Array(assumptions.pathCount) { path ->
            DoubleArray(timeSteps.stepCount + 1).also { netExposure ->
                var collateralHeld = s0
                netExposure[0] = ZERO_EXPOSURE_FLOOR
                for (step in 1..timeSteps.stepCount) {
                    val exposure = exposurePaths[path][step]
                    if (collateralValuePaths != null) {
                        collateralHeld *= collateralValuePaths[path][step] / collateralValuePaths[path][step - 1]
                    }
                    if (step % marginEveryNSteps == 0) {
                        val gap = exposure - collateralHeld
                        val callAmount = gap - threshold
                        val returnAmount = -threshold - gap
                        when {
                            gap > threshold && callAmount >= minimumTransferAmount ->
                                collateralHeld += callAmount * collateralRecognitionRate
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
 * The FX haircut rate to apply, or 0.0 if
 * [CollateralAgreement.collateralCurrency] is unset or matches [exposureCurrency].
 */
private fun fxHaircut(
    collateralAgreement: CollateralAgreement,
    exposureCurrency: Currency,
): Double {
    val collateralCurrency = collateralAgreement.collateralCurrency
    return if (collateralCurrency == null || collateralCurrency == exposureCurrency) {
        0.0
    } else {
        collateralAgreement.fxHaircutTable.rateFor(collateralCurrency, exposureCurrency).asDecimal.toDouble()
    }
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
