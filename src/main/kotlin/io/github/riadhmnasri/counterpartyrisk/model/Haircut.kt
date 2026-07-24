package io.github.riadhmnasri.counterpartyrisk.model

private const val FX_HAIRCUT_PERCENTAGE = 8.0

/**
 * A flat, app-defined haircut applied to a collateral position whose
 * currency does not match the exposure it secures (not a real regulatory
 * value). Haircuts driven by asset class instead live on [AssetClass.haircut].
 */
val FX_HAIRCUT: Rate = Rate.ofPercentage(FX_HAIRCUT_PERCENTAGE)
