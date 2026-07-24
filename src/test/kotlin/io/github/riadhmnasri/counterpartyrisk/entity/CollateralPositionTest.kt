package io.github.riadhmnasri.counterpartyrisk.entity

import io.github.riadhmnasri.counterpartyrisk.model.AssetClass
import io.github.riadhmnasri.counterpartyrisk.model.Currency
import io.github.riadhmnasri.counterpartyrisk.model.Money
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatIllegalArgumentException
import org.junit.jupiter.api.Test
import java.math.BigDecimal

class CollateralPositionTest {
    private val usd = Currency("USD")
    private val eur = Currency("EUR")

    @Test
    fun `security haircut add-on is the asset class haircut applied to the market value`() {
        // Given
        val position = CollateralPosition(Money(BigDecimal("1000"), usd), AssetClass.CORPORATE_BOND, usd)

        // When
        val addOn = position.securityHaircutAddOn()

        // Then
        assertThat(addOn.amount).isEqualByComparingTo(BigDecimal("40.00"))
    }

    @Test
    fun `fx haircut add-on applies when the collateral currency does not match the exposure currency`() {
        // Given
        val position = CollateralPosition(Money(BigDecimal("1000"), eur), AssetClass.CASH, eur)

        // When
        val addOn = position.fxHaircutAddOn(exposureCurrency = usd)

        // Then
        assertThat(addOn.amount).isEqualByComparingTo(BigDecimal("80.00"))
    }

    @Test
    fun `fx haircut add-on is zero when currencies match`() {
        // Given
        val position = CollateralPosition(Money(BigDecimal("1000"), usd), AssetClass.CASH, usd)

        // When
        val addOn = position.fxHaircutAddOn(exposureCurrency = usd)

        // Then
        assertThat(addOn.amount).isEqualByComparingTo(BigDecimal.ZERO)
    }

    @Test
    fun `a negative market value is rejected`() {
        // Given / When / Then
        assertThatIllegalArgumentException().isThrownBy {
            CollateralPosition(Money(BigDecimal("-1"), usd), AssetClass.CASH, usd)
        }
    }
}
