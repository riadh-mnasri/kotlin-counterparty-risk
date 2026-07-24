package io.github.riadhmnasri.counterpartyrisk.entity

import io.github.riadhmnasri.counterpartyrisk.model.AssetClass
import io.github.riadhmnasri.counterpartyrisk.model.Currency
import io.github.riadhmnasri.counterpartyrisk.model.Money
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatIllegalArgumentException
import org.junit.jupiter.api.Test
import java.math.BigDecimal

class SftTransactionTest {
    private val usd = Currency("USD")

    private fun cashCollateral(amount: String) =
        listOf(CollateralPosition(Money(BigDecimal(amount), usd), AssetClass.CASH, usd))

    @Test
    fun `remaining tenor in years is the remaining days divided by 365`() {
        // Given
        val transaction = repo(remainingTenorDays = 73)

        // When
        val years = transaction.remainingTenorYears()

        // Then
        assertThat(years).isEqualByComparingTo(BigDecimal("0.2"))
    }

    @Test
    fun `exposure haircut add-on is zero for a cash exposure`() {
        // Given
        val transaction = repo(exposureAssetClass = AssetClass.CASH)

        // When / Then
        assertThat(transaction.exposureHaircutAddOn().amount).isEqualByComparingTo(BigDecimal.ZERO)
    }

    @Test
    fun `exposure haircut add-on applies the exposure asset class haircut for securities lending`() {
        // Given
        val transaction = repo(exposureAmount = Money(BigDecimal("1000"), usd), exposureAssetClass = AssetClass.EQUITY)

        // When
        val addOn = transaction.exposureHaircutAddOn()

        // Then
        assertThat(addOn.amount).isEqualByComparingTo(BigDecimal("150.00"))
    }

    @Test
    fun `a blank id is rejected`() {
        // Given / When / Then
        assertThatIllegalArgumentException().isThrownBy { repo(id = " ") }
    }

    @Test
    fun `a negative exposure amount is rejected`() {
        // Given / When / Then
        assertThatIllegalArgumentException().isThrownBy { repo(exposureAmount = Money(BigDecimal("-1"), usd)) }
    }

    @Test
    fun `a non-positive remaining tenor is rejected`() {
        // Given / When / Then
        assertThatIllegalArgumentException().isThrownBy { repo(remainingTenorDays = 0) }
    }

    @Test
    fun `at least one collateral position is required`() {
        // Given / When / Then
        assertThatIllegalArgumentException().isThrownBy { repo(collateral = emptyList()) }
    }

    private fun repo(
        id: String = "sft-1",
        exposureAmount: Money = Money(BigDecimal("100"), usd),
        exposureAssetClass: AssetClass = AssetClass.CASH,
        remainingTenorDays: Int = 30,
        collateral: List<CollateralPosition> = cashCollateral("100"),
    ) = SftTransaction(
        id = id,
        kind = SftTransactionKind.REPO,
        exposureAmount = exposureAmount,
        exposureCurrency = usd,
        exposureAssetClass = exposureAssetClass,
        remainingTenorDays = remainingTenorDays,
        collateral = collateral,
    )
}
