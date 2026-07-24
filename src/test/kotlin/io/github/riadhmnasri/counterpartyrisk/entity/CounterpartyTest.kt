package io.github.riadhmnasri.counterpartyrisk.entity

import io.github.riadhmnasri.counterpartyrisk.model.CreditRating
import io.github.riadhmnasri.counterpartyrisk.model.Currency
import io.github.riadhmnasri.counterpartyrisk.model.Money
import org.assertj.core.api.Assertions.assertThatIllegalArgumentException
import org.junit.jupiter.api.Test
import java.math.BigDecimal

class CounterpartyTest {
    private val usd = Currency("USD")

    @Test
    fun `a blank id is rejected`() {
        // Given / When / Then
        assertThatIllegalArgumentException().isThrownBy {
            Counterparty(
                id = " ",
                name = "Acme",
                rating = CreditRating.A,
                approvedLimit = Money(BigDecimal("1000"), usd),
            )
        }
    }

    @Test
    fun `a blank name is rejected`() {
        // Given / When / Then
        assertThatIllegalArgumentException().isThrownBy {
            Counterparty(
                id = "cp-1",
                name = " ",
                rating = CreditRating.A,
                approvedLimit = Money(BigDecimal("1000"), usd),
            )
        }
    }

    @Test
    fun `a negative approved limit is rejected`() {
        // Given / When / Then
        assertThatIllegalArgumentException().isThrownBy {
            Counterparty(
                id = "cp-1",
                name = "Acme",
                rating = CreditRating.A,
                approvedLimit = Money(BigDecimal("-1"), usd),
            )
        }
    }
}
