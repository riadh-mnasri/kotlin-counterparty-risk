package io.github.riadhmnasri.counterpartyrisk.model

import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatIllegalArgumentException
import org.junit.jupiter.api.Test
import java.math.BigDecimal

class CreditRatingTest {
    @Test
    fun `a top grade has a very low probability of default`() {
        // Given / When / Then
        assertThat(CreditRating.AAA.probabilityOfDefault1y.asDecimal).isEqualByComparingTo(BigDecimal("0.0001"))
    }

    @Test
    fun `a defaulted grade has a probability of default of 100 percent`() {
        // Given / When / Then
        assertThat(CreditRating.D.probabilityOfDefault1y.asDecimal).isEqualByComparingTo(BigDecimal.ONE)
    }

    @Test
    fun `of is case-insensitive`() {
        // Given / When
        val rating = CreditRating.of("bbb")

        // Then
        assertThat(rating).isEqualTo(CreditRating.BBB)
    }

    @Test
    fun `of rejects an unknown grade`() {
        // Given / When / Then
        assertThatIllegalArgumentException().isThrownBy { CreditRating.of("ZZZ") }
    }
}
