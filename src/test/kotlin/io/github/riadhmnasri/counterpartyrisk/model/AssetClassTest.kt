package io.github.riadhmnasri.counterpartyrisk.model

import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatIllegalArgumentException
import org.junit.jupiter.api.Test
import java.math.BigDecimal

class AssetClassTest {
    @Test
    fun `each asset class carries its own supervisory haircut`() {
        // Given / When / Then
        assertThat(AssetClass.GOVERNMENT_BOND.haircut.asDecimal).isEqualByComparingTo(BigDecimal("0.005"))
        assertThat(AssetClass.CORPORATE_BOND.haircut.asDecimal).isEqualByComparingTo(BigDecimal("0.04"))
        assertThat(AssetClass.EQUITY.haircut.asDecimal).isEqualByComparingTo(BigDecimal("0.15"))
        assertThat(AssetClass.CASH.haircut.asDecimal).isEqualByComparingTo(BigDecimal.ZERO)
    }

    @Test
    fun `of is case-insensitive`() {
        // Given / When
        val assetClass = AssetClass.of("equity")

        // Then
        assertThat(assetClass).isEqualTo(AssetClass.EQUITY)
    }

    @Test
    fun `of rejects an unknown code`() {
        // Given / When / Then
        assertThatIllegalArgumentException().isThrownBy { AssetClass.of("crypto") }
    }
}
