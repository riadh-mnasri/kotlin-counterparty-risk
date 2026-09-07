package io.github.riadhmnasri.counterpartyrisk.cva

import io.github.riadhmnasri.counterpartyrisk.model.Rate
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.data.Offset
import org.junit.jupiter.api.Test
import java.math.BigDecimal

class CreditCurveTest {
    @Test
    fun `survival probability at time zero is always 1`() {
        // Given
        val curve = CreditCurve(Rate.ofPercentage(1.0))

        // When
        val survival = curve.survivalProbability(BigDecimal.ZERO)

        // Then
        assertThat(survival.toDouble()).isCloseTo(1.0, Offset.offset(0.0001))
    }

    @Test
    fun `survival probability at exactly one year equals 1 minus the 1-year PD`() {
        // Given: a 2 percent 1-year PD
        val curve = CreditCurve(Rate.ofPercentage(2.0))

        // When
        val survival = curve.survivalProbability(BigDecimal.ONE)

        // Then
        assertThat(survival.toDouble()).isCloseTo(0.98, Offset.offset(0.0001))
    }

    @Test
    fun `survival probability compounds down over multiple years under a flat hazard rate`() {
        // Given: a 2 percent 1-year PD
        val curve = CreditCurve(Rate.ofPercentage(2.0))

        // When
        val survivalAt2Years = curve.survivalProbability(BigDecimal("2"))

        // Then: (1 - 0.02)^2 = 0.9604
        assertThat(survivalAt2Years.toDouble()).isCloseTo(0.9604, Offset.offset(0.0001))
    }

    @Test
    fun `cumulative probability of default is the complement of survival`() {
        // Given
        val curve = CreditCurve(Rate.ofPercentage(2.0))

        // When
        val cumulativePd = curve.cumulativeProbabilityOfDefault(BigDecimal.ONE)

        // Then
        assertThat(cumulativePd.toDouble()).isCloseTo(0.02, Offset.offset(0.0001))
    }

    @Test
    fun `cumulative probability of default is monotonically increasing with time`() {
        // Given
        val curve = CreditCurve(Rate.ofPercentage(5.0))

        // When
        val pdAt3Months = curve.cumulativeProbabilityOfDefault(BigDecimal("0.25"))
        val pdAt6Months = curve.cumulativeProbabilityOfDefault(BigDecimal("0.5"))
        val pdAt1Year = curve.cumulativeProbabilityOfDefault(BigDecimal.ONE)

        // Then
        assertThat(pdAt3Months).isLessThan(pdAt6Months)
        assertThat(pdAt6Months).isLessThan(pdAt1Year)
    }
}
