package io.github.riadhmnasri.counterpartyrisk.creditrisk

import io.github.riadhmnasri.counterpartyrisk.entity.Counterparty
import io.github.riadhmnasri.counterpartyrisk.model.CreditRating
import io.github.riadhmnasri.counterpartyrisk.model.Currency
import io.github.riadhmnasri.counterpartyrisk.model.Money
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.math.BigDecimal

class ExpectedLossTest {
    private val usd = Currency("USD")

    @Test
    fun `expected loss is PD times LGD times EAD`() {
        // Given: a BBB counterparty (0.15 percent PD) and 200,000 of EAD
        val counterparty =
            Counterparty(
                id = "cp-1",
                name = "Acme",
                rating = CreditRating.BBB,
                approvedLimit = Money(BigDecimal("500000"), usd),
            )
        val ead = Money(BigDecimal("200000"), usd)

        // When
        val expectedLoss = computeExpectedLoss(counterparty, ead)

        // Then: 200,000 x 0.15% x 45% = 135
        assertThat(expectedLoss.amount).isEqualByComparingTo(BigDecimal("135.000"))
    }
}
