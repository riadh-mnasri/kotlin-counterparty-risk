package io.github.riadhmnasri.counterpartyrisk.cva

import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatIllegalArgumentException
import org.assertj.core.data.Offset
import org.junit.jupiter.api.Test
import java.math.BigDecimal

class PiecewiseCreditCurveTest {
    // 1y: 2% cumulative PD (S=0.98), 3y: 8% cumulative PD (S=0.92)
    private val curve =
        PiecewiseCreditCurve(
            listOf(
                CreditCurvePoint(BigDecimal.ONE, BigDecimal("0.02")),
                CreditCurvePoint(BigDecimal("3"), BigDecimal("0.08")),
            ),
        )

    @Test
    fun `survival probability matches exactly at a given tenor`() {
        // Given / When / Then
        assertThat(curve.survivalProbability(BigDecimal.ONE).toDouble()).isCloseTo(0.98, Offset.offset(0.0001))
        assertThat(curve.survivalProbability(BigDecimal("3")).toDouble()).isCloseTo(0.92, Offset.offset(0.0001))
    }

    @Test
    fun `survival probability between two given tenors follows the segment's implied hazard rate`() {
        // Given / When
        val survivalAt2Years = curve.survivalProbability(BigDecimal("2"))

        // Then: computed from the hazard rate that exactly reproduces S(1)=0.98 and S(3)=0.92
        assertThat(survivalAt2Years.toDouble()).isCloseTo(0.9495261976, Offset.offset(0.0001))
    }

    @Test
    fun `survival probability beyond the last tenor extrapolates flat using the last segment's hazard rate`() {
        // Given / When
        val survivalAt5Years = curve.survivalProbability(BigDecimal("5"))

        // Then: same hazard rate as the [1y, 3y] segment, continued past 3y
        assertThat(survivalAt5Years.toDouble()).isCloseTo(0.8636734694, Offset.offset(0.0001))
    }

    @Test
    fun `cumulative probability of default is the complement of survival`() {
        // Given / When
        val cumulativePd = curve.cumulativeProbabilityOfDefault(BigDecimal.ONE)

        // Then
        assertThat(cumulativePd.toDouble()).isCloseTo(0.02, Offset.offset(0.0001))
    }

    @Test
    fun `at least one point is required`() {
        // Given / When / Then
        assertThatIllegalArgumentException().isThrownBy { PiecewiseCreditCurve(emptyList()) }
    }

    @Test
    fun `tenors must be strictly increasing, points can be given out of order`() {
        // Given: the same two points as the main curve, given in reverse order
        val reordered =
            PiecewiseCreditCurve(
                listOf(
                    CreditCurvePoint(BigDecimal("3"), BigDecimal("0.08")),
                    CreditCurvePoint(BigDecimal.ONE, BigDecimal("0.02")),
                ),
            )

        // When / Then
        val survivalAt2Years = reordered.survivalProbability(BigDecimal("2")).toDouble()
        assertThat(survivalAt2Years).isCloseTo(0.9495261976, Offset.offset(0.0001))
    }

    @Test
    fun `duplicate tenors are rejected`() {
        // Given / When / Then
        assertThatIllegalArgumentException().isThrownBy {
            PiecewiseCreditCurve(
                listOf(
                    CreditCurvePoint(BigDecimal.ONE, BigDecimal("0.02")),
                    CreditCurvePoint(BigDecimal.ONE, BigDecimal("0.03")),
                ),
            )
        }
    }

    @Test
    fun `a non-positive tenor is rejected`() {
        // Given / When / Then
        assertThatIllegalArgumentException().isThrownBy {
            PiecewiseCreditCurve(listOf(CreditCurvePoint(BigDecimal.ZERO, BigDecimal("0.02"))))
        }
    }

    @Test
    fun `cumulative PD must not decrease from one tenor to the next`() {
        // Given / When / Then
        assertThatIllegalArgumentException().isThrownBy {
            PiecewiseCreditCurve(
                listOf(
                    CreditCurvePoint(BigDecimal.ONE, BigDecimal("0.08")),
                    CreditCurvePoint(BigDecimal("3"), BigDecimal("0.02")),
                ),
            )
        }
    }
}
