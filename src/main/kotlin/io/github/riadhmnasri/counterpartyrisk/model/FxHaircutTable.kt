package io.github.riadhmnasri.counterpartyrisk.model

/**
 * A lookup table for the FX haircut applied to a collateral position
 * whose currency does not match the exposure it secures, app-defined
 * (not real regulatory values) like [AssetClass]'s haircuts.
 *
 * [ratesByPair] is looked up symmetrically: an entry keyed by `eur to usd`
 * also answers a lookup for `(usd, eur)`. Any pair not listed falls back
 * to [fallback], which itself defaults to the existing flat [FX_HAIRCUT],
 * so [FxHaircutTable.FLAT] (the default used throughout this library)
 * preserves today's single-flat-rate behavior exactly.
 */
data class FxHaircutTable(
    val ratesByPair: Map<Pair<Currency, Currency>, Rate> = emptyMap(),
    val fallback: Rate = FX_HAIRCUT,
) {
    fun rateFor(
        currencyA: Currency,
        currencyB: Currency,
    ): Rate = ratesByPair[currencyA to currencyB] ?: ratesByPair[currencyB to currencyA] ?: fallback

    companion object {
        val FLAT = FxHaircutTable()
    }
}
