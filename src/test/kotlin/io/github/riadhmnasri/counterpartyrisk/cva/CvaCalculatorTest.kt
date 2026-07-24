package io.github.riadhmnasri.counterpartyrisk.cva

import io.github.riadhmnasri.counterpartyrisk.entity.CollateralPosition
import io.github.riadhmnasri.counterpartyrisk.entity.Counterparty
import io.github.riadhmnasri.counterpartyrisk.entity.NettingSet
import io.github.riadhmnasri.counterpartyrisk.entity.SftTransaction
import io.github.riadhmnasri.counterpartyrisk.entity.SftTransactionKind
import io.github.riadhmnasri.counterpartyrisk.model.AssetClass
import io.github.riadhmnasri.counterpartyrisk.model.CreditRating
import io.github.riadhmnasri.counterpartyrisk.model.Currency
import io.github.riadhmnasri.counterpartyrisk.model.Money
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatIllegalArgumentException
import org.junit.jupiter.api.Test
import java.math.BigDecimal

class CvaCalculatorTest {
    private val usd = Currency("USD")
    private val counterparty =
        Counterparty(
            id = "cp-1",
            name = "Acme",
            rating = CreditRating.BBB,
            approvedLimit = Money(BigDecimal("500000"), usd),
        )
    private val ead = Money(BigDecimal("200000"), usd)

    private fun transaction(
        id: String,
        remainingTenorDays: Int,
    ) = SftTransaction(
        id = id,
        kind = SftTransactionKind.REPO,
        exposureAmount = Money(BigDecimal("100000"), usd),
        exposureCurrency = usd,
        exposureAssetClass = AssetClass.CASH,
        remainingTenorDays = remainingTenorDays,
        collateral = listOf(CollateralPosition(Money(BigDecimal("100000"), usd), AssetClass.CASH, usd)),
    )

    @Test
    fun `CVA is EAD times PD times LGD times the longest remaining tenor in years`() {
        // Given: a 73 day (0.2 year) transaction
        val nettingSet =
            NettingSet(
                id = "ns-1",
                counterpartyId = "cp-1",
                reportingCurrency = usd,
                transactions = listOf(transaction("sft-1", 73)),
            )

        // When
        val cva = computeCva(counterparty, nettingSet, ead)

        // Then: 200,000 x 0.15% x 45% x 0.2 = 27
        assertThat(cva.amount).isEqualByComparingTo(BigDecimal("27.0000"))
    }

    @Test
    fun `the maturity used is the longest remaining tenor across all transactions`() {
        // Given: a short 73 day transaction and a longer 365 day (1 year) transaction
        val nettingSet =
            NettingSet(
                id = "ns-1",
                counterpartyId = "cp-1",
                reportingCurrency = usd,
                transactions = listOf(transaction("sft-1", 73), transaction("sft-2", 365)),
            )

        // When
        val cva = computeCva(counterparty, nettingSet, ead)

        // Then: 200,000 x 0.15% x 45% x 1.0 = 135
        assertThat(cva.amount).isEqualByComparingTo(BigDecimal("135.0000"))
    }

    @Test
    fun `a netting set with no transactions cannot produce a CVA horizon`() {
        // Given
        val nettingSet =
            NettingSet(id = "ns-1", counterpartyId = "cp-1", reportingCurrency = usd, transactions = emptyList())

        // When / Then
        assertThatIllegalArgumentException().isThrownBy { computeCva(counterparty, nettingSet, ead) }
    }
}
