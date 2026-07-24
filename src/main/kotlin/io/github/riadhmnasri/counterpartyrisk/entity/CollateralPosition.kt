package io.github.riadhmnasri.counterpartyrisk.entity

import io.github.riadhmnasri.counterpartyrisk.model.AssetClass
import io.github.riadhmnasri.counterpartyrisk.model.Currency
import io.github.riadhmnasri.counterpartyrisk.model.FX_HAIRCUT
import io.github.riadhmnasri.counterpartyrisk.model.Money
import java.math.BigDecimal

/** A piece of collateral posted or received against an [SftTransaction]. */
data class CollateralPosition(
    val marketValue: Money,
    val assetClass: AssetClass,
    /** The collateral's own currency, used only to detect an FX mismatch. */
    val currency: Currency,
) {
    init {
        require(marketValue.amount >= BigDecimal.ZERO) { "Collateral market value cannot be negative" }
    }

    fun securityHaircutAddOn(): Money = assetClass.haircut.applyTo(marketValue)

    fun isCurrencyMismatchedWith(exposureCurrency: Currency): Boolean = currency != exposureCurrency

    fun fxHaircutAddOn(exposureCurrency: Currency): Money =
        if (isCurrencyMismatchedWith(exposureCurrency)) {
            FX_HAIRCUT.applyTo(marketValue)
        } else {
            Money.zero(marketValue.currency)
        }
}
