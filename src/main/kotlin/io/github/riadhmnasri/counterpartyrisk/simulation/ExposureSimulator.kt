package io.github.riadhmnasri.counterpartyrisk.simulation

import io.github.riadhmnasri.counterpartyrisk.model.Currency
import io.github.riadhmnasri.counterpartyrisk.model.Money
import io.github.riadhmnasri.counterpartyrisk.model.Rate
import java.math.BigDecimal
import java.math.MathContext
import java.util.Random
import kotlin.math.ceil
import kotlin.math.exp
import kotlin.math.sqrt

private const val DEFAULT_TIME_STEPS_PER_YEAR = 12
private const val DEFAULT_PATH_COUNT = 2000
private const val DEFAULT_CONFIDENCE_PERCENTAGE = 95.0
private const val DEFAULT_SEED = 42L
private const val DEFAULT_ALPHA = "1.4"
private const val GBM_ITO_CORRECTION = 0.5
private const val ZERO_EXPOSURE_FLOOR = 0.0

/**
 * The assumptions [simulateExposureProfile] runs its Monte Carlo
 * simulation with, bundled into one object to keep the function's
 * parameter count down (same reasoning as `CvaAssumptions` in the `cva`
 * package).
 *
 * [random] defaults to a fixed seed, so results are reproducible across
 * calls unless the caller passes their own `Random` (a fresh, non-seeded
 * one for genuine randomness, or a different fixed seed for a different
 * but still reproducible draw).
 */
data class SimulationAssumptions(
    val timeStepsPerYear: Int = DEFAULT_TIME_STEPS_PER_YEAR,
    val pathCount: Int = DEFAULT_PATH_COUNT,
    val confidence: Rate = Rate.ofPercentage(DEFAULT_CONFIDENCE_PERCENTAGE),
    val random: Random = Random(DEFAULT_SEED),
) {
    init {
        require(timeStepsPerYear > 0) { "timeStepsPerYear must be positive, got $timeStepsPerYear" }
        require(pathCount > 0) { "pathCount must be positive, got $pathCount" }
    }
}

/** One point of a simulated exposure profile, at [timeYears] from today. */
data class ExposureProfilePoint(
    val timeYears: BigDecimal,
    /** The mean of `max(exposure, 0)` across simulated paths at this time. */
    val epe: Money,
    /** The [ExposureProfile.confidence]-level quantile of `max(exposure, 0)` across simulated paths at this time. */
    val pfe: Money,
)

/**
 * The result of [simulateExposureProfile]: an exposure profile over
 * time, plus the [confidence] level [ExposureProfilePoint.pfe] was
 * computed at.
 */
data class ExposureProfile(
    val points: List<ExposureProfilePoint>,
    val confidence: Rate,
    val currency: Currency,
) {
    /**
     * A Basel-style non-decreasing EPE envelope: the average, over the
     * whole simulated horizon, of the running maximum of EPE seen so far.
     *
     * This is a simplification of the exact regulatory "Effective EPE"
     * definition (an average over the first year specifically); since
     * these short-dated SFT horizons are often already under a year,
     * averaging over the whole simulated horizon is used instead.
     */
    fun effectiveExpectedPositiveExposure(): Money {
        var runningMax = BigDecimal.ZERO
        val runningMaxima =
            points.drop(1).map { point ->
                runningMax = runningMax.max(point.epe.amount)
                runningMax
            }
        val total = runningMaxima.fold(BigDecimal.ZERO) { sum, value -> sum.add(value) }
        return Money(total.divide(BigDecimal(runningMaxima.size), MathContext.DECIMAL64), currency)
    }

    /**
     * An IMM-style exposure at default: `alpha x effectiveExpectedPositiveExposure()`.
     * [alpha] defaults to 1.4, Basel's standard supervisory multiplier.
     */
    fun immExposureAtDefault(alpha: Rate = Rate.ofDecimal(BigDecimal(DEFAULT_ALPHA))): Money =
        alpha.applyTo(effectiveExpectedPositiveExposure())
}

/**
 * Monte Carlo simulation of a netting set's future exposure, from
 * [initialExposure] over [horizonYears], modeling the net exposure as a
 * single, driftless geometric Brownian motion with annualized
 * [volatility] — a standard, simplified proxy for how the market-value
 * gap between exposure and collateral might evolve over time:
 *
 * `S(t+dt) = S(t) x exp(-0.5 x sigma^2 x dt + sigma x sqrt(dt) x Z)`, Z ~ N(0, 1)
 *
 * This keeps every simulated path positive (an exact log-normal
 * simulation step, not an Euler discretization). It is a teaching-grade
 * simplification, not a real risk-factor model: it does not simulate
 * collateral re-margining, netting set composition changes over time, or
 * multiple correlated risk factors.
 *
 * See [SimulationAssumptions] for the path count, time granularity,
 * confidence level and random source.
 */
fun simulateExposureProfile(
    initialExposure: Money,
    volatility: Rate,
    horizonYears: BigDecimal,
    assumptions: SimulationAssumptions = SimulationAssumptions(),
): ExposureProfile {
    require(horizonYears > BigDecimal.ZERO) { "horizonYears must be positive, got $horizonYears" }
    val timeSteps = TimeSteps.of(horizonYears, assumptions.timeStepsPerYear)
    val sigma = volatility.asDecimal.toDouble()
    val paths = simulateGbmPaths(initialExposure.amount.toDouble(), sigma, timeSteps, assumptions)

    val flooredAtZero =
        Array(assumptions.pathCount) { path ->
            DoubleArray(timeSteps.stepCount + 1) { step -> maxOf(paths[path][step], ZERO_EXPOSURE_FLOOR) }
        }
    return aggregateProfile(flooredAtZero, timeSteps, assumptions.confidence, initialExposure.currency)
}

/** How the simulated horizon is discretized: [stepCount] equal steps of [dt] years each. */
internal data class TimeSteps(val dt: Double, val stepCount: Int) {
    companion object {
        fun of(
            horizonYears: BigDecimal,
            timeStepsPerYear: Int,
        ): TimeSteps {
            val stepCount = ceil(horizonYears.toDouble() * timeStepsPerYear).toInt().coerceAtLeast(1)
            return TimeSteps(dt = horizonYears.toDouble() / stepCount, stepCount = stepCount)
        }
    }
}

/**
 * Simulates [SimulationAssumptions.pathCount] independent driftless GBM
 * paths for a single risk factor starting at [s0] with annualized
 * [sigma], using the exact log-normal step (see [simulateExposureProfile]'s
 * KDoc for the formula). Shared by every simulation entry point in this
 * file; what each does with the resulting paths (float them at zero
 * directly, or run them through a collateral re-margining rule first)
 * differs.
 */
internal fun simulateGbmPaths(
    s0: Double,
    sigma: Double,
    timeSteps: TimeSteps,
    assumptions: SimulationAssumptions,
): Array<DoubleArray> =
    Array(assumptions.pathCount) {
        DoubleArray(timeSteps.stepCount + 1).also { values ->
            values[0] = s0
            for (step in 1..timeSteps.stepCount) {
                val z = assumptions.random.nextGaussian()
                val drift = -GBM_ITO_CORRECTION * sigma * sigma * timeSteps.dt
                val diffusion = sigma * sqrt(timeSteps.dt) * z
                values[step] = values[step - 1] * exp(drift + diffusion)
            }
        }
    }

/**
 * Turns per-path, per-step values (already floored at zero, i.e. ready
 * to treat as "net exposure") into an [ExposureProfile]: EPE and PFE at
 * each time step, across all paths.
 */
internal fun aggregateProfile(
    netExposureByPathAndStep: Array<DoubleArray>,
    timeSteps: TimeSteps,
    confidence: Rate,
    currency: Currency,
): ExposureProfile {
    val pathCount = netExposureByPathAndStep.size
    val points =
        (0..timeSteps.stepCount).map { step ->
            val valuesAtStep = (0 until pathCount).map { path -> netExposureByPathAndStep[path][step] }
            val pfeAtStep = quantile(valuesAtStep, confidence.asDecimal.toDouble())
            ExposureProfilePoint(
                timeYears = BigDecimal.valueOf(step * timeSteps.dt),
                epe = Money(BigDecimal.valueOf(valuesAtStep.average()), currency),
                pfe = Money(BigDecimal.valueOf(pfeAtStep), currency),
            )
        }

    return ExposureProfile(points, confidence, currency)
}

private fun quantile(
    values: List<Double>,
    level: Double,
): Double {
    val sorted = values.sorted()
    val index = Math.round(level * (sorted.size - 1)).toInt().coerceIn(0, sorted.size - 1)
    return sorted[index]
}
