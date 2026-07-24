package io.github.riadhmnasri.counterpartyrisk.entity

import io.github.riadhmnasri.counterpartyrisk.model.AssetClass
import io.github.riadhmnasri.counterpartyrisk.model.Currency
import io.github.riadhmnasri.counterpartyrisk.model.Money
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatIllegalArgumentException
import org.junit.jupiter.api.Test
import java.math.BigDecimal

class NettingSetTest {
    private val usd = Currency("USD")
    private val eur = Currency("EUR")

    private fun transactionIn(currency: Currency) =
        SftTransaction(
            id = "sft-1",
            kind = SftTransactionKind.REPO,
            exposureAmount = Money(BigDecimal("100"), currency),
            exposureCurrency = currency,
            exposureAssetClass = AssetClass.CASH,
            remainingTenorDays = 30,
            collateral = listOf(CollateralPosition(Money(BigDecimal("100"), currency), AssetClass.CASH, currency)),
        )

    @Test
    fun `a blank id is rejected`() {
        // Given / When / Then
        assertThatIllegalArgumentException().isThrownBy {
            NettingSet(id = " ", counterpartyId = "cp-1", reportingCurrency = usd, transactions = emptyList())
        }
    }

    @Test
    fun `a blank counterparty id is rejected`() {
        // Given / When / Then
        assertThatIllegalArgumentException().isThrownBy {
            NettingSet(id = "ns-1", counterpartyId = " ", reportingCurrency = usd, transactions = emptyList())
        }
    }

    @Test
    fun `a transaction whose currency does not match the reporting currency is rejected`() {
        // Given / When / Then
        assertThatIllegalArgumentException().isThrownBy {
            NettingSet(
                id = "ns-1",
                counterpartyId = "cp-1",
                reportingCurrency = usd,
                transactions = listOf(transactionIn(eur)),
            )
        }
    }

    @Test
    fun `withTransaction appends a transaction that matches the reporting currency`() {
        // Given
        val nettingSet =
            NettingSet(id = "ns-1", counterpartyId = "cp-1", reportingCurrency = usd, transactions = emptyList())
        val transaction = transactionIn(usd)

        // When
        val updated = nettingSet.withTransaction(transaction)

        // Then
        assertThat(updated.transactions).containsExactly(transaction)
    }

    @Test
    fun `withTransaction rejects a transaction in a mismatched currency`() {
        // Given
        val nettingSet =
            NettingSet(id = "ns-1", counterpartyId = "cp-1", reportingCurrency = usd, transactions = emptyList())

        // When / Then
        assertThatIllegalArgumentException().isThrownBy { nettingSet.withTransaction(transactionIn(eur)) }
    }
}
