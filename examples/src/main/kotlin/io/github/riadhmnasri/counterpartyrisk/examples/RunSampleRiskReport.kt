package io.github.riadhmnasri.counterpartyrisk.examples

import io.github.riadhmnasri.counterpartyrisk.creditrisk.computeExpectedLoss
import io.github.riadhmnasri.counterpartyrisk.cva.computeCva
import io.github.riadhmnasri.counterpartyrisk.entity.CollateralPosition
import io.github.riadhmnasri.counterpartyrisk.entity.Counterparty
import io.github.riadhmnasri.counterpartyrisk.entity.NettingSet
import io.github.riadhmnasri.counterpartyrisk.entity.SftTransaction
import io.github.riadhmnasri.counterpartyrisk.entity.SftTransactionKind
import io.github.riadhmnasri.counterpartyrisk.exposure.ExposureResult
import io.github.riadhmnasri.counterpartyrisk.exposure.computeExposure
import io.github.riadhmnasri.counterpartyrisk.model.AssetClass
import io.github.riadhmnasri.counterpartyrisk.model.CreditRating
import io.github.riadhmnasri.counterpartyrisk.model.Currency
import io.github.riadhmnasri.counterpartyrisk.model.Money
import io.github.riadhmnasri.counterpartyrisk.risklimit.LimitStatus
import io.github.riadhmnasri.counterpartyrisk.risklimit.checkRiskLimit
import java.math.BigDecimal

/**
 * A runnable walkthrough of a counterparty risk report, meant as living
 * documentation: it builds a counterparty and a netting set with two
 * SFTs, then computes exposure, expected loss, CVA and the credit limit
 * status, printing each step.
 *
 * This is a teaching/prototyping tool, not a regulatory capital engine:
 * see the README for the full list of simplifications this library makes.
 *
 * Run it with `./gradlew :examples:run`.
 */
fun main() {
    val usd = Currency("USD")
    val eur = Currency("EUR")

    val counterparty =
        Counterparty(
            id = "cp-1",
            name = "Acme Capital Partners",
            rating = CreditRating.BBB,
            approvedLimit = Money(BigDecimal("250000"), usd),
        )

    val securitiesLending =
        SftTransaction(
            id = "sft-1",
            kind = SftTransactionKind.SECURITIES_LENDING,
            exposureAmount = Money(BigDecimal("1000000"), usd),
            exposureCurrency = usd,
            exposureAssetClass = AssetClass.EQUITY,
            remainingTenorDays = 30,
            collateral = listOf(CollateralPosition(Money(BigDecimal("950000"), usd), AssetClass.CASH, usd)),
        )

    val repo =
        SftTransaction(
            id = "sft-2",
            kind = SftTransactionKind.REPO,
            exposureAmount = Money(BigDecimal("100000"), usd),
            exposureCurrency = usd,
            exposureAssetClass = AssetClass.CASH,
            remainingTenorDays = 365,
            collateral = listOf(CollateralPosition(Money(BigDecimal("100000"), usd), AssetClass.CASH, eur)),
        )

    val nettingSet =
        NettingSet(
            id = "ns-1",
            counterpartyId = counterparty.id,
            reportingCurrency = usd,
            transactions = listOf(securitiesLending, repo),
        )

    val exposure = computeExposure(nettingSet)
    val expectedLoss = computeExpectedLoss(counterparty, exposure.ead)
    val cva = computeCva(counterparty, nettingSet, exposure.ead)
    val limitStatus = checkRiskLimit(counterparty, exposure.ead)

    printReport(counterparty, nettingSet, exposure, expectedLoss, cva, limitStatus)
}

private fun printReport(
    counterparty: Counterparty,
    nettingSet: NettingSet,
    exposure: ExposureResult,
    expectedLoss: Money,
    cva: Money,
    limitStatus: LimitStatus,
) {
    println("Counterparty risk report: ${counterparty.name} (${counterparty.rating})")
    println("Netting set ${nettingSet.id}: ${nettingSet.transactions.size} transaction(s)")
    println()

    for (transaction in nettingSet.transactions) {
        println(
            "  ${transaction.id} (${transaction.kind}): " +
                "${transaction.exposureAmount.amount} ${transaction.exposureAmount.currency.code} of " +
                "${transaction.exposureAssetClass}, ${transaction.remainingTenorDays} days remaining",
        )
    }
    println()

    println("Net exposure (E*)     : ${exposure.eStar.amount} ${exposure.eStar.currency.code}")
    println("Exposure at Default   : ${exposure.ead.amount} ${exposure.ead.currency.code}")
    println("Expected Loss         : ${expectedLoss.amount} ${expectedLoss.currency.code}")
    println("CVA (simplified)      : ${cva.amount} ${cva.currency.code}")
    println("Approved limit        : ${counterparty.approvedLimit.amount} ${counterparty.approvedLimit.currency.code}")
    println("Limit status          : $limitStatus")
}
