package io.github.riadhmnasri.counterpartyrisk.model

import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatIllegalArgumentException
import org.junit.jupiter.api.Test
import java.math.BigDecimal

class MoneyTest {
    private val usd = Currency("USD")

    @Test
    fun `zero has an amount of 0`() {
        // Given / When
        val zero = Money.zero(usd)

        // Then
        assertThat(zero.amount).isEqualByComparingTo(BigDecimal.ZERO)
    }

    @Test
    fun `adding two amounts in the same currency sums them`() {
        // Given
        val ten = Money(BigDecimal("10.00"), usd)
        val five = Money(BigDecimal("5.00"), usd)

        // When
        val total = ten.add(five)

        // Then
        assertThat(total.amount).isEqualByComparingTo(BigDecimal("15.00"))
    }

    @Test
    fun `adding amounts in different currencies is rejected`() {
        // Given
        val tenDollars = Money(BigDecimal("10.00"), usd)
        val tenEuros = Money(BigDecimal("10.00"), Currency("EUR"))

        // When / Then
        assertThatIllegalArgumentException().isThrownBy { tenDollars.add(tenEuros) }
    }

    @Test
    fun `subtracting an amount reduces the total`() {
        // Given
        val ten = Money(BigDecimal("10.00"), usd)
        val three = Money(BigDecimal("3.00"), usd)

        // When
        val remainder = ten.subtract(three)

        // Then
        assertThat(remainder.amount).isEqualByComparingTo(BigDecimal("7.00"))
    }

    @Test
    fun `subtracting amounts in different currencies is rejected`() {
        // Given
        val tenDollars = Money(BigDecimal("10.00"), usd)
        val tenEuros = Money(BigDecimal("10.00"), Currency("EUR"))

        // When / Then
        assertThatIllegalArgumentException().isThrownBy { tenDollars.subtract(tenEuros) }
    }

    @Test
    fun `multiplying scales the amount by the given factor`() {
        // Given
        val hundred = Money(BigDecimal("100.00"), usd)

        // When
        val result = hundred.multiply(BigDecimal("0.045"))

        // Then
        assertThat(result.amount).isEqualByComparingTo(BigDecimal("4.500"))
    }

    @Test
    fun `flooredAtZero clamps a negative amount to zero and leaves a positive one unchanged`() {
        // Given
        val negative = Money(BigDecimal("-42.00"), usd)
        val positive = Money(BigDecimal("42.00"), usd)

        // When / Then
        assertThat(negative.flooredAtZero().amount).isEqualByComparingTo(BigDecimal.ZERO)
        assertThat(positive.flooredAtZero().amount).isEqualByComparingTo(BigDecimal("42.00"))
    }

    @Test
    fun `sum adds up a list of amounts in the same currency`() {
        // Given
        val amounts = listOf(Money(BigDecimal("10"), usd), Money(BigDecimal("20"), usd), Money(BigDecimal("30"), usd))

        // When
        val total = Money.sum(amounts, usd)

        // Then
        assertThat(total.amount).isEqualByComparingTo(BigDecimal("60"))
    }
}
