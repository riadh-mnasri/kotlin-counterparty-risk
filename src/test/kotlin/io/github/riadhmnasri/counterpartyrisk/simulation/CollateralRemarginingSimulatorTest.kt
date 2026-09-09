package io.github.riadhmnasri.counterpartyrisk.simulation

import io.github.riadhmnasri.counterpartyrisk.model.AssetClass
import io.github.riadhmnasri.counterpartyrisk.model.Currency
import io.github.riadhmnasri.counterpartyrisk.model.FxHaircutTable
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
    fun `a collateral asset class haircut leaves more residual net exposure than no haircut`() {
        // Given: two independent Random instances seeded identically, so the
        // simulated exposure path is the same on both sides; a nonzero
        // threshold and zero MTA so margin calls happen and are fully acted on
        val threshold = Money(BigDecimal("2000"), usd)
        val volatility = Rate.ofPercentage(40.0)
        val seed = 55L

        // When
        val withoutHaircut =
            simulateExposureProfileWithCollateral(
                initialExposure,
                volatility,
                BigDecimal("2"),
                CollateralAgreement(threshold = threshold, minimumTransferAmount = Money.zero(usd)),
                SimulationAssumptions(pathCount = 500, random = java.util.Random(seed)),
            )
        val withHaircut =
            simulateExposureProfileWithCollateral(
                initialExposure,
                volatility,
                BigDecimal("2"),
                CollateralAgreement(
                    threshold = threshold,
                    minimumTransferAmount = Money.zero(usd),
                    collateralAssetClass = AssetClass.EQUITY,
                ),
                SimulationAssumptions(pathCount = 500, random = java.util.Random(seed)),
            )

        // Then: the same posted calls cover less of the gap once haircut, so
        // net exposure with a haircut is never better (lower) than without
        withoutHaircut.points.zip(withHaircut.points).forEach { (noHaircut, haircut) ->
            assertThat(haircut.epe.amount).isGreaterThanOrEqualTo(noHaircut.epe.amount)
        }
    }

    @Test
    fun `no collateral asset class behaves identically to the previous cash-equivalent default`() {
        // Given: two independent Random instances seeded identically
        val agreement = CollateralAgreement(threshold = Money(BigDecimal("2000"), usd))
        val volatility = Rate.ofPercentage(30.0)
        val seed = 99L

        // When
        val implicitDefault =
            simulateExposureProfileWithCollateral(
                initialExposure,
                volatility,
                BigDecimal("2"),
                agreement.copy(collateralAssetClass = null),
                SimulationAssumptions(pathCount = 300, random = java.util.Random(seed)),
            )
        val explicitCash =
            simulateExposureProfileWithCollateral(
                initialExposure,
                volatility,
                BigDecimal("2"),
                agreement.copy(collateralAssetClass = AssetClass.CASH),
                SimulationAssumptions(pathCount = 300, random = java.util.Random(seed)),
            )

        // Then: cash's haircut is 0 percent, same as the implicit no-haircut default
        implicitDefault.points.zip(explicitCash.points).forEach { (a, b) ->
            assertThat(a.epe.amount).isCloseTo(b.epe.amount, Offset.offset(BigDecimal("0.0001")))
        }
    }

    @Test
    fun `perfect correlation and matching volatility keeps collateral exactly in lockstep with exposure`() {
        // Given: correlation 1.0 and the same volatility means collateral's
        // GBM step uses the exact same random draw as exposure's, so they
        // move by identical multiplicative factors at every step and the
        // gap between them stays zero, needing no margin calls at all
        val volatility = Rate.ofPercentage(40.0)
        val riskFactor = CorrelatedCollateralRiskFactor(volatility, BigDecimal.ONE)
        val agreement = CollateralAgreement(threshold = Money(BigDecimal("1"), usd), collateralRiskFactor = riskFactor)

        // When
        val profile = simulateExposureProfileWithCollateral(initialExposure, volatility, BigDecimal("2"), agreement)

        // Then
        assertThat(profile.points).allSatisfy { point ->
            assertThat(point.epe.amount).isCloseTo(BigDecimal.ZERO, Offset.offset(BigDecimal("0.01")))
        }
    }

    @Test
    fun `negative correlation leaves more residual net exposure than positive correlation`() {
        // Given: two independent Random instances seeded identically
        val volatility = Rate.ofPercentage(30.0)
        val threshold = Money(BigDecimal("2000"), usd)
        val seed = 321L

        // When
        val positivelyCorrelated =
            simulateExposureProfileWithCollateral(
                initialExposure,
                volatility,
                BigDecimal("2"),
                CollateralAgreement(
                    threshold = threshold,
                    minimumTransferAmount = Money.zero(usd),
                    collateralRiskFactor = CorrelatedCollateralRiskFactor(volatility, BigDecimal.ONE),
                ),
                SimulationAssumptions(pathCount = 500, random = java.util.Random(seed)),
            )
        val negativelyCorrelated =
            simulateExposureProfileWithCollateral(
                initialExposure,
                volatility,
                BigDecimal("2"),
                CollateralAgreement(
                    threshold = threshold,
                    minimumTransferAmount = Money.zero(usd),
                    collateralRiskFactor = CorrelatedCollateralRiskFactor(volatility, BigDecimal("-1")),
                ),
                SimulationAssumptions(pathCount = 500, random = java.util.Random(seed)),
            )

        // Then: collateral moving opposite to exposure is a worse hedge
        assertThat(negativelyCorrelated.effectiveExpectedPositiveExposure().amount)
            .isGreaterThan(positivelyCorrelated.effectiveExpectedPositiveExposure().amount)
    }

    @Test
    fun `a zero-volatility risk factor is unaffected by its correlation, since it never moves either way`() {
        // Given: two independent Random instances seeded identically. Both
        // configurations draw the same number of random values per step (a
        // correlated pair), so unlike comparing against no risk factor at
        // all, their exposure paths stay in sync draw for draw
        val agreement = CollateralAgreement(threshold = Money(BigDecimal("2000"), usd))
        val volatility = Rate.ofPercentage(30.0)
        val zeroVolatility = Rate.ofDecimal(BigDecimal.ZERO)
        val seed = 44L

        // When
        val positivelyCorrelatedButStill =
            simulateExposureProfileWithCollateral(
                initialExposure,
                volatility,
                BigDecimal("2"),
                agreement.copy(collateralRiskFactor = CorrelatedCollateralRiskFactor(zeroVolatility, BigDecimal.ONE)),
                SimulationAssumptions(pathCount = 300, random = java.util.Random(seed)),
            )
        val negativelyCorrelatedButStill =
            simulateExposureProfileWithCollateral(
                initialExposure,
                volatility,
                BigDecimal("2"),
                agreement.copy(collateralRiskFactor = CorrelatedCollateralRiskFactor(zeroVolatility, BigDecimal("-1"))),
                SimulationAssumptions(pathCount = 300, random = java.util.Random(seed)),
            )

        // Then: collateral value never moves regardless of correlation when its volatility is zero
        positivelyCorrelatedButStill.points.zip(negativelyCorrelatedButStill.points).forEach { (a, b) ->
            assertThat(a.epe.amount).isCloseTo(b.epe.amount, Offset.offset(BigDecimal("0.01")))
        }
    }

    @Test
    fun `correlation must be between -1 and 1`() {
        // Given / When / Then
        assertThatIllegalArgumentException().isThrownBy {
            CorrelatedCollateralRiskFactor(Rate.ofPercentage(20.0), BigDecimal("1.5"))
        }
    }

    @Test
    fun `a mismatched collateral currency leaves more residual net exposure than no FX mismatch`() {
        // Given: two independent Random instances seeded identically; a
        // nonzero threshold and zero MTA so margin calls happen and are
        // fully acted on
        val eur = Currency("EUR")
        val threshold = Money(BigDecimal("2000"), usd)
        val volatility = Rate.ofPercentage(40.0)
        val seed = 88L

        // When
        val noFxMismatch =
            simulateExposureProfileWithCollateral(
                initialExposure,
                volatility,
                BigDecimal("2"),
                CollateralAgreement(threshold = threshold, minimumTransferAmount = Money.zero(usd)),
                SimulationAssumptions(pathCount = 500, random = java.util.Random(seed)),
            )
        val withFxMismatch =
            simulateExposureProfileWithCollateral(
                initialExposure,
                volatility,
                BigDecimal("2"),
                CollateralAgreement(
                    threshold = threshold,
                    minimumTransferAmount = Money.zero(usd),
                    collateralCurrency = eur,
                ),
                SimulationAssumptions(pathCount = 500, random = java.util.Random(seed)),
            )

        // Then: the FX haircut means the same calls cover less of the gap
        noFxMismatch.points.zip(withFxMismatch.points).forEach { (noMismatch, mismatch) ->
            assertThat(mismatch.epe.amount).isGreaterThanOrEqualTo(noMismatch.epe.amount)
        }
    }

    @Test
    fun `a collateral currency matching the exposure currency has no FX haircut effect`() {
        // Given: two independent Random instances seeded identically
        val threshold = Money(BigDecimal("2000"), usd)
        val volatility = Rate.ofPercentage(30.0)
        val seed = 66L

        // When
        val implicitDefault =
            simulateExposureProfileWithCollateral(
                initialExposure,
                volatility,
                BigDecimal("2"),
                CollateralAgreement(threshold = threshold),
                SimulationAssumptions(pathCount = 300, random = java.util.Random(seed)),
            )
        val explicitSameCurrency =
            simulateExposureProfileWithCollateral(
                initialExposure,
                volatility,
                BigDecimal("2"),
                CollateralAgreement(threshold = threshold, collateralCurrency = usd),
                SimulationAssumptions(pathCount = 300, random = java.util.Random(seed)),
            )

        // Then
        implicitDefault.points.zip(explicitSameCurrency.points).forEach { (a, b) ->
            assertThat(a.epe.amount).isCloseTo(b.epe.amount, Offset.offset(BigDecimal("0.0001")))
        }
    }

    @Test
    fun `security and FX haircuts combine additively, same as computeExposure`() {
        // Given: two independent Random instances seeded identically. A
        // combined 15 percent (equity) + 8 percent (flat FX) haircut should
        // leave the same residual as a single custom 23 percent FX-only entry
        val eur = Currency("EUR")
        val threshold = Money(BigDecimal("2000"), usd)
        val volatility = Rate.ofPercentage(35.0)
        val seed = 13L

        // When
        val securityPlusFx =
            simulateExposureProfileWithCollateral(
                initialExposure,
                volatility,
                BigDecimal("2"),
                CollateralAgreement(
                    threshold = threshold,
                    minimumTransferAmount = Money.zero(usd),
                    collateralAssetClass = AssetClass.EQUITY,
                    collateralCurrency = eur,
                ),
                SimulationAssumptions(pathCount = 500, random = java.util.Random(seed)),
            )
        val combinedFlatRate =
            simulateExposureProfileWithCollateral(
                initialExposure,
                volatility,
                BigDecimal("2"),
                CollateralAgreement(
                    threshold = threshold,
                    minimumTransferAmount = Money.zero(usd),
                    collateralCurrency = eur,
                    fxHaircutTable = FxHaircutTable(fallback = Rate.ofPercentage(23.0)),
                ),
                SimulationAssumptions(pathCount = 500, random = java.util.Random(seed)),
            )

        // Then
        securityPlusFx.points.zip(combinedFlatRate.points).forEach { (a, b) ->
            assertThat(a.epe.amount).isCloseTo(b.epe.amount, Offset.offset(BigDecimal("0.01")))
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
