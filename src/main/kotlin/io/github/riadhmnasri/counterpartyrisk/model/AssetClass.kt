package io.github.riadhmnasri.counterpartyrisk.model

private const val GOVERNMENT_BOND_HAIRCUT_PERCENTAGE = 0.5
private const val CORPORATE_BOND_HAIRCUT_PERCENTAGE = 4.0
private const val EQUITY_HAIRCUT_PERCENTAGE = 15.0
private const val CASH_HAIRCUT_PERCENTAGE = 0.0

/**
 * A simplified, app-defined supervisory haircut table (not real regulatory
 * values), one entry per asset class recognized by this library.
 */
enum class AssetClass(val haircut: Rate) {
    GOVERNMENT_BOND(Rate.ofPercentage(GOVERNMENT_BOND_HAIRCUT_PERCENTAGE)),
    CORPORATE_BOND(Rate.ofPercentage(CORPORATE_BOND_HAIRCUT_PERCENTAGE)),
    EQUITY(Rate.ofPercentage(EQUITY_HAIRCUT_PERCENTAGE)),
    CASH(Rate.ofPercentage(CASH_HAIRCUT_PERCENTAGE)),
    ;

    companion object {
        fun of(code: String): AssetClass =
            entries.find { it.name == code.uppercase() }
                ?: throw IllegalArgumentException(
                    "Unknown asset class \"$code\", expected one of ${entries.joinToString { it.name }}",
                )
    }
}
