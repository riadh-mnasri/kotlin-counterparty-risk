package io.github.riadhmnasri.counterpartyrisk.model

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.math.BigDecimal

class HaircutTest {
    @Test
    fun `the FX haircut is a flat 8 percent`() {
        // Given / When / Then
        assertThat(FX_HAIRCUT.asDecimal).isEqualByComparingTo(BigDecimal("0.08"))
    }
}
