package io.github.riadhmnasri.counterpartyrisk.cva

import io.github.riadhmnasri.counterpartyrisk.creditrisk.LOSS_GIVEN_DEFAULT
import io.github.riadhmnasri.counterpartyrisk.entity.Counterparty
import io.github.riadhmnasri.counterpartyrisk.entity.NettingSet
import io.github.riadhmnasri.counterpartyrisk.model.Money

/**
 * Computes a simplified, single-period Credit Valuation Adjustment:
 *
 * `CVA ~= EAD x PD(1y) x LGD x maturity_in_years`
 *
 * where maturity is the netting set's longest remaining transaction tenor.
 * This is a linear teaching approximation, not a real multi-period CVA
 * model. Because SFT tenors are short, expect small CVA figures relative
 * to EAD: a deliberate, realistic observation (real desks often treat
 * short-dated SFT CVA as immaterial).
 */
fun computeCva(
    counterparty: Counterparty,
    nettingSet: NettingSet,
    ead: Money,
): Money {
    require(nettingSet.transactions.isNotEmpty()) {
        "Cannot compute a CVA horizon for a netting set with no transactions"
    }
    val maturityYears = nettingSet.transactions.maxOf { it.remainingTenorYears() }
    val pd = counterparty.rating.probabilityOfDefault1y

    return ead.multiply(pd.asDecimal).multiply(LOSS_GIVEN_DEFAULT.asDecimal).multiply(maturityYears)
}
