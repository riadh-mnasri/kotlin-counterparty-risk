package io.github.riadhmnasri.counterpartyrisk.model

import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatIllegalArgumentException
import org.junit.jupiter.api.Test
import java.math.BigDecimal

class CreditRatingTest {
    @Test
    fun `each grade carries its own one-year probability of default`() {
        // Given / When / Then
        assertThat(CreditRating.AAA.probabilityOfDefault1y.asDecimal).isEqualByComparingTo(BigDecimal.ZERO)
        assertThat(CreditRating.AA.probabilityOfDefault1y.asDecimal).isEqualByComparingTo(BigDecimal("0.0002"))
        assertThat(CreditRating.A.probabilityOfDefault1y.asDecimal).isEqualByComparingTo(BigDecimal("0.0005"))
        assertThat(CreditRating.BBB.probabilityOfDefault1y.asDecimal).isEqualByComparingTo(BigDecimal("0.0015"))
        assertThat(CreditRating.BB.probabilityOfDefault1y.asDecimal).isEqualByComparingTo(BigDecimal("0.0062"))
        assertThat(CreditRating.B.probabilityOfDefault1y.asDecimal).isEqualByComparingTo(BigDecimal("0.035"))
        assertThat(CreditRating.CCC.probabilityOfDefault1y.asDecimal).isEqualByComparingTo(BigDecimal("0.27"))
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
