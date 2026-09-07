package io.github.riadhmnasri.counterpartyrisk.model

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class FxHaircutTableTest {
    private val usd = Currency("USD")
    private val eur = Currency("EUR")
    private val brl = Currency("BRL")

    @Test
    fun `the flat table returns the same fallback rate for any mismatched pair`() {
        // Given / When / Then
        assertThat(FxHaircutTable.FLAT.rateFor(usd, eur)).isEqualTo(FX_HAIRCUT)
        assertThat(FxHaircutTable.FLAT.rateFor(usd, brl)).isEqualTo(FX_HAIRCUT)
    }

    @Test
    fun `a listed pair returns its specific rate`() {
        // Given: EUR-USD is listed at a lower rate than the flat fallback
        val table = FxHaircutTable(ratesByPair = mapOf((eur to usd) to Rate.ofPercentage(2.0)))

        // When / Then
        assertThat(table.rateFor(eur, usd)).isEqualTo(Rate.ofPercentage(2.0))
    }

    @Test
    fun `a listed pair is looked up symmetrically regardless of currency order`() {
        // Given
        val table = FxHaircutTable(ratesByPair = mapOf((eur to usd) to Rate.ofPercentage(2.0)))

        // When / Then
        assertThat(table.rateFor(usd, eur)).isEqualTo(Rate.ofPercentage(2.0))
    }

    @Test
    fun `an unlisted pair falls back to the table's fallback rate`() {
        // Given: only EUR-USD is listed, with a custom fallback for everything else
        val customFallback = Rate.ofPercentage(15.0)
        val table =
            FxHaircutTable(
                ratesByPair = mapOf((eur to usd) to Rate.ofPercentage(2.0)),
                fallback = customFallback,
            )

        // When / Then
        assertThat(table.rateFor(usd, brl)).isEqualTo(customFallback)
    }

    @Test
    fun `the default fallback is the existing flat FX haircut`() {
        // Given
        val table = FxHaircutTable(ratesByPair = mapOf((eur to usd) to Rate.ofPercentage(2.0)))

        // When / Then
        assertThat(table.rateFor(usd, brl)).isEqualTo(FX_HAIRCUT)
    }
}
