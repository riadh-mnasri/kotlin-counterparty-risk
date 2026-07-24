package io.github.riadhmnasri.counterpartyrisk.risklimit

import io.github.riadhmnasri.counterpartyrisk.entity.Counterparty
import io.github.riadhmnasri.counterpartyrisk.model.CreditRating
import io.github.riadhmnasri.counterpartyrisk.model.Currency
import io.github.riadhmnasri.counterpartyrisk.model.Money
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.math.BigDecimal

class RiskLimitCheckerTest {
    private val usd = Currency("USD")
    private val counterparty =
        Counterparty(
            id = "cp-1",
            name = "Acme",
            rating = CreditRating.BBB,
            approvedLimit = Money(BigDecimal("100000"), usd),
        )

    @Test
    fun `an EAD well below the limit is OK`() {
        // Given
        val ead = Money(BigDecimal("50000"), usd)

        // When / Then
        assertThat(checkRiskLimit(counterparty, ead)).isEqualTo(LimitStatus.OK)
    }

    @Test
    fun `an EAD above 80 percent of the limit is a WARNING`() {
        // Given
        val ead = Money(BigDecimal("85000"), usd)

        // When / Then
        assertThat(checkRiskLimit(counterparty, ead)).isEqualTo(LimitStatus.WARNING)
    }

    @Test
    fun `an EAD exactly at the limit is a WARNING, not a breach`() {
        // Given
        val ead = Money(BigDecimal("100000"), usd)

        // When / Then
        assertThat(checkRiskLimit(counterparty, ead)).isEqualTo(LimitStatus.WARNING)
    }

    @Test
    fun `an EAD above the limit is a BREACH`() {
        // Given
        val ead = Money(BigDecimal("150000"), usd)

        // When / Then
        assertThat(checkRiskLimit(counterparty, ead)).isEqualTo(LimitStatus.BREACH)
    }
}
