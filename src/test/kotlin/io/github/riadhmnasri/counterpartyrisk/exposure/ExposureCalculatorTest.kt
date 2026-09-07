package io.github.riadhmnasri.counterpartyrisk.exposure

import io.github.riadhmnasri.counterpartyrisk.entity.CollateralPosition
import io.github.riadhmnasri.counterpartyrisk.entity.NettingSet
import io.github.riadhmnasri.counterpartyrisk.entity.SftTransaction
import io.github.riadhmnasri.counterpartyrisk.entity.SftTransactionKind
import io.github.riadhmnasri.counterpartyrisk.model.AssetClass
import io.github.riadhmnasri.counterpartyrisk.model.Currency
import io.github.riadhmnasri.counterpartyrisk.model.FxHaircutTable
import io.github.riadhmnasri.counterpartyrisk.model.Money
import io.github.riadhmnasri.counterpartyrisk.model.Rate
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.math.BigDecimal

class ExposureCalculatorTest {
    private val usd = Currency("USD")
    private val eur = Currency("EUR")

    @Test
    fun `exposure combines uncollateralized amount with security haircut add-ons`() {
        // Given: 1,000,000 of equity lent out (15% haircut) against 950,000 of cash collateral
        val transaction =
            SftTransaction(
                id = "sft-1",
                kind = SftTransactionKind.SECURITIES_LENDING,
                exposureAmount = Money(BigDecimal("1000000"), usd),
                exposureCurrency = usd,
                exposureAssetClass = AssetClass.EQUITY,
                remainingTenorDays = 30,
                collateral = listOf(CollateralPosition(Money(BigDecimal("950000"), usd), AssetClass.CASH, usd)),
            )
        val nettingSet =
            NettingSet(
                id = "ns-1",
                counterpartyId = "cp-1",
                reportingCurrency = usd,
                transactions = listOf(transaction),
            )

        // When
        val result = computeExposure(nettingSet)

        // Then: (1,000,000 - 950,000) + 150,000 (equity haircut) + 0 (cash haircut) = 200,000
        assertThat(result.eStar.amount).isEqualByComparingTo(BigDecimal("200000.00"))
        assertThat(result.ead.amount).isEqualByComparingTo(result.eStar.amount)
    }

    @Test
    fun `exposure is floored at zero when collateral and haircuts fully cover the exposure`() {
        // Given: 1,000,000 of cash lent against 2,000,000 of cash collateral
        val transaction =
            SftTransaction(
                id = "sft-1",
                kind = SftTransactionKind.REPO,
                exposureAmount = Money(BigDecimal("1000000"), usd),
                exposureCurrency = usd,
                exposureAssetClass = AssetClass.CASH,
                remainingTenorDays = 30,
                collateral = listOf(CollateralPosition(Money(BigDecimal("2000000"), usd), AssetClass.CASH, usd)),
            )
        val nettingSet =
            NettingSet(
                id = "ns-1",
                counterpartyId = "cp-1",
                reportingCurrency = usd,
                transactions = listOf(transaction),
            )

        // When
        val result = computeExposure(nettingSet)

        // Then
        assertThat(result.eStar.amount).isEqualByComparingTo(BigDecimal.ZERO)
    }

    @Test
    fun `a currency-mismatched collateral position adds the FX haircut`() {
        // Given: 100,000 of cash lent, secured by 100,000 (already reported in USD) of
        // collateral whose native currency is EUR, so an FX haircut applies
        val transaction =
            SftTransaction(
                id = "sft-1",
                kind = SftTransactionKind.REPO,
                exposureAmount = Money(BigDecimal("100000"), usd),
                exposureCurrency = usd,
                exposureAssetClass = AssetClass.CASH,
                remainingTenorDays = 30,
                collateral = listOf(CollateralPosition(Money(BigDecimal("100000"), usd), AssetClass.CASH, eur)),
            )
        val nettingSet =
            NettingSet(
                id = "ns-1",
                counterpartyId = "cp-1",
                reportingCurrency = usd,
                transactions = listOf(transaction),
            )

        // When
        val result = computeExposure(nettingSet)

        // Then: (100,000 - 100,000) + 0 (cash haircut) + 8,000 (FX haircut) = 8,000
        assertThat(result.eStar.amount).isEqualByComparingTo(BigDecimal("8000.00"))
    }

    @Test
    fun `a per-pair fx haircut table is honored instead of the flat default`() {
        // Given: same currency mismatch as above, but EUR-USD is listed at 2 percent
        val transaction =
            SftTransaction(
                id = "sft-1",
                kind = SftTransactionKind.REPO,
                exposureAmount = Money(BigDecimal("100000"), usd),
                exposureCurrency = usd,
                exposureAssetClass = AssetClass.CASH,
                remainingTenorDays = 30,
                collateral = listOf(CollateralPosition(Money(BigDecimal("100000"), usd), AssetClass.CASH, eur)),
            )
        val nettingSet =
            NettingSet(
                id = "ns-1",
                counterpartyId = "cp-1",
                reportingCurrency = usd,
                transactions = listOf(transaction),
            )
        val fxHaircutTable = FxHaircutTable(ratesByPair = mapOf((eur to usd) to Rate.ofPercentage(2.0)))

        // When
        val result = computeExposure(nettingSet, fxHaircutTable)

        // Then: (100,000 - 100,000) + 0 (cash haircut) + 2,000 (2% FX haircut) = 2,000
        assertThat(result.eStar.amount).isEqualByComparingTo(BigDecimal("2000.00"))
    }
}
