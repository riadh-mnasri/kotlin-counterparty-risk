package io.github.riadhmnasri.counterpartyrisk.cva

import io.github.riadhmnasri.counterpartyrisk.creditrisk.LOSS_GIVEN_DEFAULT
import io.github.riadhmnasri.counterpartyrisk.entity.CollateralPosition
import io.github.riadhmnasri.counterpartyrisk.entity.Counterparty
import io.github.riadhmnasri.counterpartyrisk.entity.NettingSet
import io.github.riadhmnasri.counterpartyrisk.entity.SftTransaction
import io.github.riadhmnasri.counterpartyrisk.entity.SftTransactionKind
import io.github.riadhmnasri.counterpartyrisk.model.AssetClass
import io.github.riadhmnasri.counterpartyrisk.model.CreditRating
import io.github.riadhmnasri.counterpartyrisk.model.Currency
import io.github.riadhmnasri.counterpartyrisk.model.Money
import io.github.riadhmnasri.counterpartyrisk.model.Rate
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatIllegalArgumentException
import org.assertj.core.data.Offset
import org.assertj.core.data.Percentage
import org.junit.jupiter.api.Test
import java.math.BigDecimal

class MultiPeriodCvaTest {
    private val usd = Currency("USD")
    private val counterparty =
        Counterparty(
            id = "cp-1",
            name = "Acme",
            rating = CreditRating.BBB,
            approvedLimit = Money(BigDecimal("500000"), usd),
        )
    private val ead = Money(BigDecimal("200000"), usd)

    private fun transaction(remainingTenorDays: Int) =
        SftTransaction(
            id = "sft-1",
            kind = SftTransactionKind.REPO,
            exposureAmount = Money(BigDecimal("100000"), usd),
            exposureCurrency = usd,
            exposureAssetClass = AssetClass.CASH,
            remainingTenorDays = remainingTenorDays,
            collateral = listOf(CollateralPosition(Money(BigDecimal("100000"), usd), AssetClass.CASH, usd)),
        )

    private fun nettingSet(remainingTenorDays: Int) =
        NettingSet(
            id = "ns-1",
            counterpartyId = "cp-1",
            reportingCurrency = usd,
            transactions = listOf(transaction(remainingTenorDays)),
        )

    @Test
    fun `without discounting, the marginal PDs across all periods telescope to the cumulative PD at maturity`() {
        // Given: a 2 year netting set, split into quarterly periods
        val twoYears = nettingSet(remainingTenorDays = 730)

        // When
        val cva =
            computeMultiPeriodCva(
                counterparty,
                twoYears,
                ead,
                CvaAssumptions(discountRate = Rate.ofDecimal(BigDecimal.ZERO), periodsPerYear = 4),
            )

        // Then: sum of marginal PDs over any number of periods is exactly the
        // cumulative PD at maturity (a telescoping sum), so this must match
        // EAD x cumulativePD(2y) x LGD regardless of how many periods were used
        val curve = CreditCurve(counterparty.rating.probabilityOfDefault1y)
        val expectedCumulativePd = curve.cumulativeProbabilityOfDefault(BigDecimal("2"))
        val expected = ead.amount.multiply(expectedCumulativePd).multiply(LOSS_GIVEN_DEFAULT.asDecimal)
        assertThat(cva.amount).isCloseTo(expected, Percentage.withPercentage(0.01))
    }

    @Test
    fun `the number of periods used does not change the undiscounted result`() {
        // Given
        val twoYears = nettingSet(remainingTenorDays = 730)

        // When
        val quarterly = computeMultiPeriodCva(counterparty, twoYears, ead, CvaAssumptions(periodsPerYear = 4))
        val monthly = computeMultiPeriodCva(counterparty, twoYears, ead, CvaAssumptions(periodsPerYear = 12))

        // Then
        assertThat(quarterly.amount).isCloseTo(monthly.amount, Percentage.withPercentage(0.01))
    }

    @Test
    fun `discounting reduces the CVA compared to no discounting`() {
        // Given
        val twoYears = nettingSet(remainingTenorDays = 730)

        // When
        val undiscounted = computeMultiPeriodCva(counterparty, twoYears, ead)
        val discounted =
            computeMultiPeriodCva(counterparty, twoYears, ead, CvaAssumptions(discountRate = Rate.ofPercentage(5.0)))

        // Then
        assertThat(discounted.amount).isLessThan(undiscounted.amount)
    }

    @Test
    fun `a custom LGD scales the result proportionally`() {
        // Given
        val oneYear = nettingSet(remainingTenorDays = 365)

        // When
        val flatLgd = computeMultiPeriodCva(counterparty, oneYear, ead, CvaAssumptions(lgd = LOSS_GIVEN_DEFAULT))
        val cashLgd = computeMultiPeriodCva(counterparty, oneYear, ead, CvaAssumptions(lgd = AssetClass.CASH.lgd))

        // Then: cash LGD is 0%, so the CVA collapses to zero
        assertThat(cashLgd.amount).isCloseTo(BigDecimal.ZERO, Offset.offset(BigDecimal("0.0001")))
        assertThat(flatLgd.amount).isGreaterThan(BigDecimal.ZERO)
    }

    @Test
    fun `a netting set with no transactions cannot produce a CVA horizon`() {
        // Given
        val emptyNettingSet =
            NettingSet(id = "ns-1", counterpartyId = "cp-1", reportingCurrency = usd, transactions = emptyList())

        // When / Then
        assertThatIllegalArgumentException().isThrownBy { computeMultiPeriodCva(counterparty, emptyNettingSet, ead) }
    }

    @Test
    fun `periodsPerYear must be positive`() {
        // Given / When / Then
        assertThatIllegalArgumentException().isThrownBy { CvaAssumptions(periodsPerYear = 0) }
    }
}
