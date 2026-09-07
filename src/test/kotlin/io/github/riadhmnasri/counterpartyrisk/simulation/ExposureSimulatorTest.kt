package io.github.riadhmnasri.counterpartyrisk.simulation

import io.github.riadhmnasri.counterpartyrisk.model.Currency
import io.github.riadhmnasri.counterpartyrisk.model.Money
import io.github.riadhmnasri.counterpartyrisk.model.Rate
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatIllegalArgumentException
import org.assertj.core.data.Offset
import org.junit.jupiter.api.Test
import java.math.BigDecimal

class ExposureSimulatorTest {
    private val usd = Currency("USD")
    private val initialExposure = Money(BigDecimal("100000"), usd)

    @Test
    fun `at time zero, EPE and PFE both equal the initial exposure`() {
        // Given
        val volatility = Rate.ofPercentage(20.0)

        // When
        val profile = simulateExposureProfile(initialExposure, volatility, horizonYears = BigDecimal.ONE)

        // Then
        val firstPoint = profile.points.first()
        assertThat(firstPoint.timeYears).isEqualByComparingTo(BigDecimal.ZERO)
        assertThat(firstPoint.epe.amount).isCloseTo(initialExposure.amount, Offset.offset(BigDecimal("0.01")))
        assertThat(firstPoint.pfe.amount).isCloseTo(initialExposure.amount, Offset.offset(BigDecimal("0.01")))
    }

    @Test
    fun `zero volatility produces a flat profile at the initial exposure`() {
        // Given: no randomness can move the exposure when volatility is zero
        val zeroVolatility = Rate.ofDecimal(BigDecimal.ZERO)

        // When
        val profile = simulateExposureProfile(initialExposure, zeroVolatility, horizonYears = BigDecimal("2"))

        // Then
        assertThat(profile.points).allSatisfy { point ->
            assertThat(point.epe.amount).isCloseTo(initialExposure.amount, Offset.offset(BigDecimal("0.01")))
            assertThat(point.pfe.amount).isCloseTo(initialExposure.amount, Offset.offset(BigDecimal("0.01")))
        }
    }

    @Test
    fun `PFE at a high confidence level is never below EPE`() {
        // Given: a 95 percent confidence, well above the mean
        val volatility = Rate.ofPercentage(30.0)
        val assumptions = SimulationAssumptions(confidence = Rate.ofPercentage(95.0))

        // When
        val profile = simulateExposureProfile(initialExposure, volatility, horizonYears = BigDecimal("2"), assumptions)

        // Then
        assertThat(profile.points).allSatisfy { point ->
            assertThat(point.pfe.amount).isGreaterThanOrEqualTo(point.epe.amount)
        }
    }

    @Test
    fun `the same default seed produces the same result across separate calls`() {
        // Given
        val volatility = Rate.ofPercentage(20.0)

        // When: two independent calls, neither passing a custom Random
        val first = simulateExposureProfile(initialExposure, volatility, horizonYears = BigDecimal.ONE)
        val second = simulateExposureProfile(initialExposure, volatility, horizonYears = BigDecimal.ONE)

        // Then
        assertThat(first.points.map { it.epe.amount }).isEqualTo(second.points.map { it.epe.amount })
        assertThat(first.points.map { it.pfe.amount }).isEqualTo(second.points.map { it.pfe.amount })
    }

    @Test
    fun `the profile has one point per time step plus the starting point`() {
        // Given: a 1 year horizon at 12 steps per year
        val volatility = Rate.ofPercentage(20.0)
        val assumptions = SimulationAssumptions(timeStepsPerYear = 12)

        // When
        val profile = simulateExposureProfile(initialExposure, volatility, horizonYears = BigDecimal.ONE, assumptions)

        // Then
        assertThat(profile.points).hasSize(13)
        assertThat(profile.points.last().timeYears).isCloseTo(BigDecimal.ONE, Offset.offset(BigDecimal("0.0001")))
    }

    @Test
    fun `effective EPE for a flat zero-volatility profile equals the initial exposure`() {
        // Given
        val zeroVolatility = Rate.ofDecimal(BigDecimal.ZERO)
        val profile = simulateExposureProfile(initialExposure, zeroVolatility, horizonYears = BigDecimal("2"))

        // When
        val effectiveEpe = profile.effectiveExpectedPositiveExposure()

        // Then
        assertThat(effectiveEpe.amount).isCloseTo(initialExposure.amount, Offset.offset(BigDecimal("0.01")))
    }

    @Test
    fun `IMM exposure at default applies the alpha multiplier to effective EPE`() {
        // Given: a flat profile, so effective EPE is exactly the initial exposure
        val zeroVolatility = Rate.ofDecimal(BigDecimal.ZERO)
        val profile = simulateExposureProfile(initialExposure, zeroVolatility, horizonYears = BigDecimal("2"))

        // When
        val immExposure = profile.immExposureAtDefault(alpha = Rate.ofDecimal(BigDecimal("1.4")))

        // Then: 100,000 x 1.4 = 140,000
        assertThat(immExposure.amount).isCloseTo(BigDecimal("140000"), Offset.offset(BigDecimal("1")))
    }

    @Test
    fun `horizon must be positive`() {
        // Given / When / Then
        assertThatIllegalArgumentException().isThrownBy {
            simulateExposureProfile(initialExposure, Rate.ofPercentage(20.0), horizonYears = BigDecimal.ZERO)
        }
    }

    @Test
    fun `path count must be positive`() {
        // Given / When / Then
        assertThatIllegalArgumentException().isThrownBy { SimulationAssumptions(pathCount = 0) }
    }

    @Test
    fun `time steps per year must be positive`() {
        // Given / When / Then
        assertThatIllegalArgumentException().isThrownBy { SimulationAssumptions(timeStepsPerYear = 0) }
    }
}
