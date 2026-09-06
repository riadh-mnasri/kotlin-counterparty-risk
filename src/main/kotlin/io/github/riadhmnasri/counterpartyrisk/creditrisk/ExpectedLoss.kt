package io.github.riadhmnasri.counterpartyrisk.creditrisk

import io.github.riadhmnasri.counterpartyrisk.entity.Counterparty
import io.github.riadhmnasri.counterpartyrisk.model.Money
import io.github.riadhmnasri.counterpartyrisk.model.Rate

private const val LOSS_GIVEN_DEFAULT_PERCENTAGE = 45.0

/**
 * A flat, app-defined Loss Given Default assumption for an unsecured
 * exposure (collateral quality is already priced into EAD via the
 * comprehensive haircut approach, see `computeExposure`). Used as the
 * default for `computeExpectedLoss`; callers who want to vary LGD by
 * collateral quality can pass [AssetClass.lgd] explicitly instead.
 */
val LOSS_GIVEN_DEFAULT: Rate = Rate.ofPercentage(LOSS_GIVEN_DEFAULT_PERCENTAGE)

/** Computes Expected Loss: `EL = PD(1y) x LGD x EAD`. */
fun computeExpectedLoss(
    counterparty: Counterparty,
    ead: Money,
    lgd: Rate = LOSS_GIVEN_DEFAULT,
): Money {
    val pd = counterparty.rating.probabilityOfDefault1y
    return ead.multiply(pd.asDecimal).multiply(lgd.asDecimal)
}
