package io.github.riadhmnasri.counterpartyrisk.model

private const val GOVERNMENT_BOND_HAIRCUT_PERCENTAGE = 0.5
private const val CORPORATE_BOND_HAIRCUT_PERCENTAGE = 4.0
private const val EQUITY_HAIRCUT_PERCENTAGE = 15.0
private const val CASH_HAIRCUT_PERCENTAGE = 0.0

private const val GOVERNMENT_BOND_LGD_PERCENTAGE = 5.0
private const val CORPORATE_BOND_LGD_PERCENTAGE = 40.0
private const val EQUITY_LGD_PERCENTAGE = 60.0
private const val CASH_LGD_PERCENTAGE = 0.0

/**
 * A simplified, app-defined supervisory haircut table (not real regulatory
 * values), one entry per asset class recognized by this library.
 *
 * [lgd] is likewise an app-defined Loss Given Default assumption for
 * collateral of this asset class, for callers of `computeExpectedLoss` who
 * want to vary LGD by collateral quality instead of using its flat default.
 */
enum class AssetClass(val haircut: Rate, val lgd: Rate) {
    GOVERNMENT_BOND(
        Rate.ofPercentage(GOVERNMENT_BOND_HAIRCUT_PERCENTAGE),
        Rate.ofPercentage(GOVERNMENT_BOND_LGD_PERCENTAGE),
    ),
    CORPORATE_BOND(
        Rate.ofPercentage(CORPORATE_BOND_HAIRCUT_PERCENTAGE),
        Rate.ofPercentage(CORPORATE_BOND_LGD_PERCENTAGE),
    ),
    EQUITY(
        Rate.ofPercentage(EQUITY_HAIRCUT_PERCENTAGE),
        Rate.ofPercentage(EQUITY_LGD_PERCENTAGE),
    ),
    CASH(
        Rate.ofPercentage(CASH_HAIRCUT_PERCENTAGE),
        Rate.ofPercentage(CASH_LGD_PERCENTAGE),
    ),
    ;

    companion object {
        fun of(code: String): AssetClass =
            entries.find { it.name == code.uppercase() }
                ?: throw IllegalArgumentException(
                    "Unknown asset class \"$code\", expected one of ${entries.joinToString { it.name }}",
                )
    }
}
