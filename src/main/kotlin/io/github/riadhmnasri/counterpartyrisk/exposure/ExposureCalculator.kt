package io.github.riadhmnasri.counterpartyrisk.exposure

import io.github.riadhmnasri.counterpartyrisk.entity.NettingSet
import io.github.riadhmnasri.counterpartyrisk.entity.SftTransaction
import io.github.riadhmnasri.counterpartyrisk.model.FxHaircutTable
import io.github.riadhmnasri.counterpartyrisk.model.Money

/**
 * Computes a netting set's exposure using a simplified version of the
 * Basel "comprehensive approach with supervisory haircuts" for SFTs:
 *
 * `E* = max(0, (sum of exposures - sum of collateral) + security haircut add-ons + FX haircut add-ons)`
 *
 * [fxHaircutTable] defaults to a single flat rate ([FxHaircutTable.FLAT]);
 * pass your own to vary the FX haircut by currency pair instead.
 */
fun computeExposure(
    nettingSet: NettingSet,
    fxHaircutTable: FxHaircutTable = FxHaircutTable.FLAT,
): ExposureResult {
    val eStar =
        nettingSet.transactions
            .fold(Money.zero(nettingSet.reportingCurrency)) { sum, transaction ->
                sum.add(netContribution(transaction, fxHaircutTable))
            }.flooredAtZero()

    return ExposureResult(eStar = eStar, ead = eStar)
}

/**
 * One transaction's own, un-floored share of `E*`: its exposure, minus
 * its collateral, plus every security and FX haircut add-on on both legs.
 * Negative when the transaction is over-collateralized, which is what
 * lets it offset other transactions in the same netting set.
 */
internal fun netContribution(
    transaction: SftTransaction,
    fxHaircutTable: FxHaircutTable,
): Money =
    transaction.collateral.fold(
        transaction.exposureAmount.add(transaction.exposureHaircutAddOn()),
    ) { contribution, position ->
        contribution
            .subtract(position.marketValue)
            .add(position.securityHaircutAddOn())
            .add(position.fxHaircutAddOn(transaction.exposureCurrency, fxHaircutTable))
    }
