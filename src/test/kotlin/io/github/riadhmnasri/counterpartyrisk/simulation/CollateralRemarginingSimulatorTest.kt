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
    fun `a margining frequency equal to the simulation's own time steps behaves identically to the default`() {
        // Given: two independent Random instances seeded identically
        val threshold = Money(BigDecimal("3000"), usd)
        val volatility = Rate.ofPercentage(30.0)
        val seed = 77L
        val simulationTimeStepsPerYear = 12

        // When
        val defaultAssumptions =
            SimulationAssumptions(timeStepsPerYear = simulationTimeStepsPerYear, random = java.util.Random(seed))
        val defaultBehavior =
            simulateExposureProfileWithCollateral(
                initialExposure,
                volatility,
                BigDecimal("2"),
                CollateralAgreement(threshold = threshold),
                defaultAssumptions,
            )
        val explicitAssumptions =
            SimulationAssumptions(timeStepsPerYear = simulationTimeStepsPerYear, random = java.util.Random(seed))
        val explicitEveryStep =
            simulateExposureProfileWithCollateral(
                initialExposure,
                volatility,
                BigDecimal("2"),
                CollateralAgreement(threshold = threshold, marginingStepsPerYear = simulationTimeStepsPerYear),
                explicitAssumptions,
            )

        // Then
        defaultBehavior.points.zip(explicitEveryStep.points).forEach { (a, b) ->
            assertThat(a.epe.amount).isCloseTo(b.epe.amount, Offset.offset(BigDecimal("0.0001")))
        }
    }

    @Test
    fun `a coarser margining frequency lets net exposure exceed the threshold between margining events`() {
        // Given: yearly margining on a monthly-stepped simulation, with a zero
        // threshold and MTA so any margining event fully resets the gap to zero
        val agreement =
            CollateralAgreement(
                threshold = Money.zero(usd),
                minimumTransferAmount = Money.zero(usd),
                marginingStepsPerYear = 1,
            )
        val volatility = Rate.ofPercentage(40.0)
        val assumptions = SimulationAssumptions(timeStepsPerYear = 12)

        // When
        val profile =
            simulateExposureProfileWithCollateral(initialExposure, volatility, BigDecimal("1"), agreement, assumptions)

        // Then: step 0 (t=0) and step 12 (t=1y, a margining event) are exactly
        // zero, but at least one of the 11 in-between, non-margining steps
        // shows a nonzero net exposure since the gap was free to drift
        assertThat(profile.points.first().epe.amount).isCloseTo(BigDecimal.ZERO, Offset.offset(BigDecimal("0.01")))
        assertThat(profile.points.last().epe.amount).isCloseTo(BigDecimal.ZERO, Offset.offset(BigDecimal("0.01")))
        val inBetweenSteps = profile.points.subList(1, profile.points.size - 1)
        assertThat(inBetweenSteps).anySatisfy { point -> assertThat(point.epe.amount).isGreaterThan(BigDecimal.ZERO) }
    }

    @Test
    fun `a margining frequency that does not evenly divide the simulation's time steps is rejected`() {
        // Given: 12 time steps per year does not divide evenly by 5
        val agreement = CollateralAgreement(threshold = Money(BigDecimal("1000"), usd), marginingStepsPerYear = 5)
        val assumptions = SimulationAssumptions(timeStepsPerYear = 12)

        // When / Then
        assertThatIllegalArgumentException().isThrownBy {
            val volatility = Rate.ofPercentage(20.0)
            simulateExposureProfileWithCollateral(initialExposure, volatility, BigDecimal.ONE, agreement, assumptions)
        }
    }

    @Test
    fun `margining more often than the simulation's own time steps is rejected`() {
        // Given: 12 time steps per year cannot support 24 margining events per year
        val agreement = CollateralAgreement(threshold = Money(BigDecimal("1000"), usd), marginingStepsPerYear = 24)
        val assumptions = SimulationAssumptions(timeStepsPerYear = 12)

        // When / Then
        assertThatIllegalArgumentException().isThrownBy {
            val volatility = Rate.ofPercentage(20.0)
            simulateExposureProfileWithCollateral(initialExposure, volatility, BigDecimal.ONE, agreement, assumptions)
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
