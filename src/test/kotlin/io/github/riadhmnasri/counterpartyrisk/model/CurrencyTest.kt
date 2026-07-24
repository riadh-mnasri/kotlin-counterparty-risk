package io.github.riadhmnasri.counterpartyrisk.model

import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatIllegalArgumentException
import org.junit.jupiter.api.Test

class CurrencyTest {
    @Test
    fun `two currencies with the same code are equal`() {
        // Given
        val first = Currency("USD")
        val second = Currency("USD")

        // When / Then
        assertThat(first).isEqualTo(second)
    }

    @Test
    fun `a blank code is rejected`() {
        // Given
        val blankCode = "  "

        // When / Then
        assertThatIllegalArgumentException().isThrownBy { Currency(blankCode) }
    }
}
