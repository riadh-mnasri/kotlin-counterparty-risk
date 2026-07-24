package io.github.riadhmnasri.counterpartyrisk.model

import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatIllegalArgumentException
import org.junit.jupiter.api.Test
import java.math.BigDecimal

class RateTest {
    private val usd = Currency("USD")

    @Test
    fun `ofPercentage and the equivalent ofDecimal represent the same rate`() {
        // Given / When
        val fourPercent = Rate.ofPercentage(4.0)
        val fourHundredthsAsDecimal = Rate.ofDecimal(0.04)

        // Then
        assertThat(fourPercent).isEqualTo(fourHundredthsAsDecimal)
    }

    @Test
    fun `applyTo scales a money amount by the rate`() {
        // Given
        val fourPercent = Rate.ofPercentage(4.0)
        val hundred = Money(BigDecimal("100.00"), usd)

        // When
        val result = fourPercent.applyTo(hundred)

        // Then
        assertThat(result.amount).isEqualByComparingTo(BigDecimal("4.0000"))
    }

    @Test
    fun `a negative rate is rejected`() {
        // Given / When / Then
        assertThatIllegalArgumentException().isThrownBy { Rate.ofPercentage(-1.0) }
    }
}
