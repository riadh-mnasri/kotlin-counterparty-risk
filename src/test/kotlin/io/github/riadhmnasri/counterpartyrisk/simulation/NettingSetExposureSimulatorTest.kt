package io.github.riadhmnasri.counterpartyrisk.simulation

import io.github.riadhmnasri.counterpartyrisk.entity.CollateralPosition
import io.github.riadhmnasri.counterpartyrisk.entity.NettingSet
import io.github.riadhmnasri.counterpartyrisk.entity.SftTransaction
import io.github.riadhmnasri.counterpartyrisk.entity.SftTransactionKind
import io.github.riadhmnasri.counterpartyrisk.exposure.computeExposure
import io.github.riadhmnasri.counterpartyrisk.model.AssetClass
import io.github.riadhmnasri.counterpartyrisk.model.Currency
import io.github.riadhmnasri.counterpartyrisk.model.Money
import io.github.riadhmnasri.counterpartyrisk.model.Rate
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatIllegalArgumentException
import org.assertj.core.data.Offset
import org.junit.jupiter.api.Test
import java.math.BigDecimal

class NettingSetExposureSimulatorTest {
    private val usd = Currency("USD")
    private val zeroVolatility = Rate.ofDecimal(BigDecimal.ZERO)
    private val monthly = SimulationAssumptions(timeStepsPerYear = 12)
    private val cent = Offset.offset(BigDecimal("0.01"))

    @Test
    fun `at time zero, the profile starts from the netting set's point-in-time E star`() {
        // Given: equity lent against cash, plus a cash repo, both with haircut add-ons in play
        val nettingSet =
            nettingSetOf(
                transaction(
                    "sft-1",
                    exposure = "1000000",
                    collateral = "950000",
                    tenorDays = 30,
                    lent = AssetClass.EQUITY,
                ),
                transaction("repo-1", exposure = "500000", collateral = "480000", tenorDays = 400),
            )

        // When
        val profile = simulateNettingSetExposureProfile(nettingSet, Rate.ofPercentage(20.0), BigDecimal.ONE)

        // Then
        val eStar = computeExposure(nettingSet).eStar.amount
        assertThat(profile.points.first().epe.amount).isCloseTo(eStar, cent)
        assertThat(profile.points.first().pfe.amount).isCloseTo(eStar, cent)
    }

    @Test
    fun `under run-off, exposure steps down once a transaction matures`() {
        // Given: 50,000 net maturing after 0.2 years (73 days), 30,000 net maturing after 2 years
        val nettingSet =
            nettingSetOf(
                transaction("short", exposure = "150000", collateral = "100000", tenorDays = 73),
                transaction("long", exposure = "130000", collateral = "100000", tenorDays = 730),
            )

        // When
        val profile =
            simulateNettingSetExposureProfile(nettingSet, zeroVolatility, BigDecimal.ONE, assumptions = monthly)

        // Then: months 0 to 2 still include the short transaction, month 3 onwards only the long one
        val epeByMonth = profile.points.map { it.epe.amount }
        assertThat(epeByMonth.take(3)).allSatisfy { assertThat(it).isCloseTo(BigDecimal("80000"), cent) }
        assertThat(epeByMonth.drop(3)).allSatisfy { assertThat(it).isCloseTo(BigDecimal("30000"), cent) }
    }

    @Test
    fun `under run-off, exposure steps up when an over-collateralized transaction matures`() {
        // Given: a short transaction over-collateralized by 20,000, offsetting a long one worth 50,000
        val nettingSet =
            nettingSetOf(
                transaction("over-collateralized", exposure = "80000", collateral = "100000", tenorDays = 73),
                transaction("long", exposure = "150000", collateral = "100000", tenorDays = 730),
            )

        // When
        val profile =
            simulateNettingSetExposureProfile(nettingSet, zeroVolatility, BigDecimal.ONE, assumptions = monthly)

        // Then: the netting benefit disappears with the transaction that provided it
        assertThat(profile.points.first().epe.amount).isCloseTo(BigDecimal("30000"), cent)
        assertThat(profile.points.last().epe.amount).isCloseTo(BigDecimal("50000"), cent)
    }

    @Test
    fun `under run-off, exposure drops to zero once every transaction has matured`() {
        // Given: a single transaction maturing after 6 months, simulated over a full year
        val nettingSet =
            nettingSetOf(transaction("repo-1", exposure = "150000", collateral = "100000", tenorDays = 183))

        // When
        val profile =
            simulateNettingSetExposureProfile(
                nettingSet,
                Rate.ofPercentage(30.0),
                BigDecimal.ONE,
                assumptions = monthly,
            )

        // Then
        assertThat(profile.points.drop(7)).allSatisfy { point ->
            assertThat(point.epe.amount).isEqualByComparingTo(BigDecimal.ZERO)
            assertThat(point.pfe.amount).isEqualByComparingTo(BigDecimal.ZERO)
        }
    }

    @Test
    fun `under full rollover, the composition never changes`() {
        // Given: the same two transactions as the run-off step-down case
        val nettingSet =
            nettingSetOf(
                transaction("short", exposure = "150000", collateral = "100000", tenorDays = 73),
                transaction("long", exposure = "130000", collateral = "100000", tenorDays = 730),
            )
        val options = NettingSetSimulationOptions(rollover = RolloverAssumption.FULL_ROLLOVER)

        // When
        val profile = simulateNettingSetExposureProfile(nettingSet, zeroVolatility, BigDecimal.ONE, options, monthly)

        // Then
        assertThat(profile.points).allSatisfy { point ->
            assertThat(point.epe.amount).isCloseTo(BigDecimal("80000"), cent)
        }
    }

    @Test
    fun `under full rollover, a single transaction reproduces simulateExposureProfile`() {
        // Given
        val nettingSet = nettingSetOf(transaction("repo-1", exposure = "150000", collateral = "100000", tenorDays = 30))
        val volatility = Rate.ofPercentage(25.0)
        val options = NettingSetSimulationOptions(rollover = RolloverAssumption.FULL_ROLLOVER)

        // When: both use the same default seed
        val fromNettingSet = simulateNettingSetExposureProfile(nettingSet, volatility, BigDecimal("2"), options)
        val fromSingleAmount =
            simulateExposureProfile(computeExposure(nettingSet).eStar, volatility, BigDecimal("2"))

        // Then
        assertThat(fromNettingSet.points.zip(fromSingleAmount.points)).allSatisfy { (actual, expected) ->
            assertThat(actual.epe.amount).isCloseTo(expected.epe.amount, cent)
            assertThat(actual.pfe.amount).isCloseTo(expected.pfe.amount, cent)
        }
    }

    @Test
    fun `when nothing matures within the horizon, run-off and full rollover agree`() {
        // Given: every transaction outlives the 1 year horizon
        val nettingSet =
            nettingSetOf(
                transaction("repo-1", exposure = "150000", collateral = "100000", tenorDays = 400),
                transaction("repo-2", exposure = "90000", collateral = "100000", tenorDays = 500),
            )
        val volatility = Rate.ofPercentage(20.0)

        // When
        val runOff = simulateNettingSetExposureProfile(nettingSet, volatility, BigDecimal.ONE)
        val rollover =
            simulateNettingSetExposureProfile(
                nettingSet,
                volatility,
                BigDecimal.ONE,
                NettingSetSimulationOptions(rollover = RolloverAssumption.FULL_ROLLOVER),
            )

        // Then
        assertThat(runOff.points.map { it.epe.amount }).isEqualTo(rollover.points.map { it.epe.amount })
        assertThat(runOff.points.map { it.pfe.amount }).isEqualTo(rollover.points.map { it.pfe.amount })
    }

    @Test
    fun `run-off is the default rollover assumption`() {
        assertThat(NettingSetSimulationOptions().rollover).isEqualTo(RolloverAssumption.RUN_OFF)
    }

    @Test
    fun `a non-positive horizon is rejected`() {
        val nettingSet = nettingSetOf(transaction("repo-1", exposure = "150000", collateral = "100000", tenorDays = 30))

        assertThatIllegalArgumentException().isThrownBy {
            simulateNettingSetExposureProfile(nettingSet, Rate.ofPercentage(20.0), BigDecimal.ZERO)
        }
    }

    private fun transaction(
        id: String,
        exposure: String,
        collateral: String,
        tenorDays: Int,
        lent: AssetClass = AssetClass.CASH,
    ) = SftTransaction(
        id = id,
        kind = if (lent == AssetClass.CASH) SftTransactionKind.REPO else SftTransactionKind.SECURITIES_LENDING,
        exposureAmount = Money(BigDecimal(exposure), usd),
        exposureCurrency = usd,
        exposureAssetClass = lent,
        remainingTenorDays = tenorDays,
        collateral = listOf(CollateralPosition(Money(BigDecimal(collateral), usd), AssetClass.CASH, usd)),
    )

    private fun nettingSetOf(vararg transactions: SftTransaction) =
        NettingSet(id = "ns-1", counterpartyId = "cp-1", reportingCurrency = usd, transactions = transactions.toList())
}
