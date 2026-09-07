package io.github.riadhmnasri.counterpartyrisk.cva

import java.math.BigDecimal
import kotlin.math.exp
import kotlin.math.ln

/** One point of a [PiecewiseCreditCurve]: the cumulative probability of default observed at [tenorYears]. */
data class CreditCurvePoint(
    val tenorYears: BigDecimal,
    val cumulativeProbabilityOfDefault: BigDecimal,
)

/**
 * A default probability term structure built from several observed
 * points, instead of [CreditCurve]'s single 1-year PD. Between two
 * consecutive points, survival follows the single **piecewise-constant
 * hazard rate** that exactly reproduces both endpoints — the standard
 * "bootstrapped" way real credit curves are built from market quotes —
 * which keeps cumulative PD exactly monotonic with no separate check
 * needed. Beyond the last given point, the curve extrapolates flat using
 * that last segment's hazard rate, the same simple choice [CreditCurve]
 * makes for its own single point.
 *
 * [points] can be given in any order but must have strictly increasing,
 * positive tenors and non-decreasing cumulative PD.
 */
class PiecewiseCreditCurve(points: List<CreditCurvePoint>) {
    private val segments: List<Segment>

    init {
        require(points.isNotEmpty()) { "PiecewiseCreditCurve requires at least one point" }
        val sortedPoints = points.sortedBy { it.tenorYears }
        require(sortedPoints.all { it.tenorYears > BigDecimal.ZERO }) { "Every tenor must be positive" }
        require(sortedPoints.zipWithNext().all { (a, b) -> a.tenorYears < b.tenorYears }) {
            "Tenors must be strictly increasing, got duplicates in $sortedPoints"
        }
        val cumulativePdNonDecreasing =
            sortedPoints.zipWithNext().all { (a, b) ->
                a.cumulativeProbabilityOfDefault <= b.cumulativeProbabilityOfDefault
            }
        require(cumulativePdNonDecreasing) {
            "Cumulative probability of default must not decrease with tenor, got $sortedPoints"
        }

        var previousTenor = 0.0
        var previousSurvival = 1.0
        segments =
            sortedPoints.map { point ->
                val tenor = point.tenorYears.toDouble()
                val survival = 1.0 - point.cumulativeProbabilityOfDefault.toDouble()
                val hazard = -ln(survival / previousSurvival) / (tenor - previousTenor)
                val segment =
                    Segment(
                        startTenor = previousTenor,
                        endTenor = tenor,
                        startSurvival = previousSurvival,
                        hazard = hazard,
                    )
                previousTenor = tenor
                previousSurvival = survival
                segment
            }
    }

    fun survivalProbability(years: BigDecimal): BigDecimal {
        val t = years.toDouble()
        val segment = segments.firstOrNull { t <= it.endTenor } ?: segments.last()
        return BigDecimal.valueOf(segment.startSurvival * exp(-segment.hazard * (t - segment.startTenor)))
    }

    fun cumulativeProbabilityOfDefault(years: BigDecimal): BigDecimal =
        BigDecimal.ONE.subtract(survivalProbability(years))

    private data class Segment(
        val startTenor: Double,
        val endTenor: Double,
        val startSurvival: Double,
        val hazard: Double,
    )
}
