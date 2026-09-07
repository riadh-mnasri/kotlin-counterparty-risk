package io.github.riadhmnasri.counterpartyrisk.simulation

import io.github.riadhmnasri.counterpartyrisk.model.Currency
import io.github.riadhmnasri.counterpartyrisk.model.Money
import io.github.riadhmnasri.counterpartyrisk.model.Rate
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatIllegalArgumentException
import org.assertj.core.data.Offset
import org.junit.jupiter.api.Test
import java.math.BigDecimal

class CollateralRemarginingSimulatorTest {
    private val usd = Currency("USD")
    private val initialExposure = Money(BigDecimal("100000"), usd)

    @Test
    fun `a zero threshold and MTA keeps net exposure at exactly zero at every step`() {
        // Given: collateral is continuously adjusted to exactly match exposure
        val agreement = CollateralAgreement(threshold = Money.zero(usd), minimumTransferAmount = Money.zero(usd))
        val volatility = Rate.ofPercentage(30.0)

        // When
        val profile = simulateExposureProfileWithCollateral(initialExposure, volatility, BigDecimal("2"), agreement)

        // Then
        assertThat(profile.points).allSatisfy { point ->
            assertThat(point.epe.amount).isCloseTo(BigDecimal.ZERO, Offset.offset(BigDecimal("0.01")))
            assertThat(point.pfe.amount).isCloseTo(BigDecimal.ZERO, Offset.offset(BigDecimal("0.01")))
        }
    }

    @Test
    fun `a zero volatility keeps the collateralized net exposure at zero, since nothing ever moves`() {
        // Given: exposure never changes, so the initial full collateralization never needs adjusting
        val agreement = CollateralAgreement(threshold = Money(BigDecimal("5000"), usd))
        val zeroVolatility = Rate.ofDecimal(BigDecimal.ZERO)

        // When
        val profile = simulateExposureProfileWithCollateral(initialExposure, zeroVolatility, BigDecimal("2"), agreement)

        // Then
        assertThat(profile.points).allSatisfy { point ->
            assertThat(point.epe.amount).isCloseTo(BigDecimal.ZERO, Offset.offset(BigDecimal("0.01")))
        }
    }

    @Test
    fun `with a zero MTA, net exposure after margining never exceeds the threshold`() {
        // Given: any breach of the threshold is called immediately, regardless of size
        val threshold = BigDecimal("2000")
        val agreement = CollateralAgreement(threshold = Money(threshold, usd), minimumTransferAmount = Money.zero(usd))
        val volatility = Rate.ofPercentage(40.0)

        // When
        val profile = simulateExposureProfileWithCollateral(initialExposure, volatility, BigDecimal("3"), agreement)

        // Then: PFE (a high quantile) stays within the threshold, up to rounding
        assertThat(profile.points).allSatisfy { point ->
            assertThat(point.pfe.amount).isLessThanOrEqualTo(threshold.add(BigDecimal("0.01")))
        }
    }

    @Test
    fun `margining never leaves the collateralized exposure worse than the uncollateralized one`() {
        // Given: two independent Random instances seeded identically, so the
        // simulated exposure path itself is the same on both sides
        val agreement = CollateralAgreement(threshold = Money(BigDecimal("5000"), usd))
        val volatility = Rate.ofPercentage(35.0)
        val seed = 123L

        // When
        val withoutCollateral =
            simulateExposureProfile(
                initialExposure,
                volatility,
                BigDecimal("2"),
                SimulationAssumptions(pathCount = 500, random = java.util.Random(seed)),
            )
        val withCollateral =
            simulateExposureProfileWithCollateral(
                initialExposure,
                volatility,
                BigDecimal("2"),
                agreement,
                SimulationAssumptions(pathCount = 500, random = java.util.Random(seed)),
            )

        // Then
        withoutCollateral.points.zip(withCollateral.points).forEach { (uncollateralized, collateralized) ->
            assertThat(collateralized.epe.amount).isLessThanOrEqualTo(uncollateralized.epe.amount)
        }
    }

    @Test
    fun `a negative threshold is rejected`() {
        // Given / When / Then
        assertThatIllegalArgumentException().isThrownBy {
            CollateralAgreement(threshold = Money(BigDecimal("-1"), usd))
        }
    }

    @Test
    fun `a negative minimum transfer amount is rejected`() {
        // Given / When / Then
        assertThatIllegalArgumentException().isThrownBy {
            CollateralAgreement(threshold = Money.zero(usd), minimumTransferAmount = Money(BigDecimal("-1"), usd))
        }
    }

    @Test
    fun `mismatched currencies between threshold and minimum transfer amount are rejected`() {
        // Given
        val eur = Currency("EUR")

        // When / Then
        assertThatIllegalArgumentException().isThrownBy {
            CollateralAgreement(threshold = Money.zero(usd), minimumTransferAmount = Money.zero(eur))
        }
    }
}
