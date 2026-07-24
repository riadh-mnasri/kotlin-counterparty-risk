package io.github.riadhmnasri.counterpartyrisk.exposure

import io.github.riadhmnasri.counterpartyrisk.entity.NettingSet
import io.github.riadhmnasri.counterpartyrisk.model.Money

/**
 * Computes a netting set's exposure using a simplified version of the
 * Basel "comprehensive approach with supervisory haircuts" for SFTs:
 *
 * `E* = max(0, (sum of exposures - sum of collateral) + security haircut add-ons + FX haircut add-ons)`
 */
fun computeExposure(nettingSet: NettingSet): ExposureResult {
    val currency = nettingSet.reportingCurrency

    var sumExposure = Money.zero(currency)
    var sumCollateral = Money.zero(currency)
    var haircutAddOns = Money.zero(currency)

    for (transaction in nettingSet.transactions) {
        sumExposure = sumExposure.add(transaction.exposureAmount)
        haircutAddOns = haircutAddOns.add(transaction.exposureHaircutAddOn())

        for (position in transaction.collateral) {
            sumCollateral = sumCollateral.add(position.marketValue)
            haircutAddOns = haircutAddOns.add(position.securityHaircutAddOn())
            haircutAddOns = haircutAddOns.add(position.fxHaircutAddOn(transaction.exposureCurrency))
        }
    }

    val eStar = sumExposure.subtract(sumCollateral).add(haircutAddOns).flooredAtZero()

    return ExposureResult(eStar = eStar, ead = eStar)
}
