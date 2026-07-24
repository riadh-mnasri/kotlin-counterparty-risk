package io.github.riadhmnasri.counterpartyrisk.entity

import io.github.riadhmnasri.counterpartyrisk.model.AssetClass
import io.github.riadhmnasri.counterpartyrisk.model.Currency
import io.github.riadhmnasri.counterpartyrisk.model.Money
import java.math.BigDecimal
import java.math.MathContext

private val DAYS_PER_YEAR = BigDecimal(365)

enum class SftTransactionKind {
    REPO,
    SECURITIES_LENDING,
}

/** A single repo or securities lending transaction. */
data class SftTransaction(
    val id: String,
    val kind: SftTransactionKind,
    /**
     * Cash lent (Repo) or market value of the security lent (Securities Lending),
     * in the netting set's reporting currency.
     */
    val exposureAmount: Money,
    /** The native currency of the underlying transaction, used only to detect an FX mismatch against its collateral. */
    val exposureCurrency: Currency,
    /**
     * What is actually lent (CASH for a typical Repo, a bond/equity class for Securities Lending),
     * driving the exposure leg's own security haircut.
     */
    val exposureAssetClass: AssetClass,
    val remainingTenorDays: Int,
    val collateral: List<CollateralPosition>,
) {
    init {
        require(id.isNotBlank()) { "SftTransaction id must not be blank" }
        require(exposureAmount.amount >= BigDecimal.ZERO) { "SftTransaction exposure amount cannot be negative" }
        require(remainingTenorDays > 0) { "SftTransaction remaining tenor must be a positive number of days" }
        require(collateral.isNotEmpty()) { "SftTransaction requires at least one collateral position" }
    }

    fun remainingTenorYears(): BigDecimal = BigDecimal(remainingTenorDays).divide(DAYS_PER_YEAR, MathContext.DECIMAL64)

    /**
     * The security haircut add-on for the exposure leg itself (e.g. a bond lent
     * out in Securities Lending). Zero for cash.
     */
    fun exposureHaircutAddOn(): Money = exposureAssetClass.haircut.applyTo(exposureAmount)
}
