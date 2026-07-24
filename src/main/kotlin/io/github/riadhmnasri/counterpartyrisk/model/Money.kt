package io.github.riadhmnasri.counterpartyrisk.model

import java.math.BigDecimal

/**
 * An amount expressed in a single currency. This library does not perform
 * FX conversion: every [Money] value within a netting set is expected to
 * already be expressed in that netting set's reporting currency.
 */
data class Money(val amount: BigDecimal, val currency: Currency) {
    companion object {
        fun zero(currency: Currency): Money = Money(BigDecimal.ZERO, currency)

        fun sum(
            amounts: List<Money>,
            currency: Currency,
        ): Money =
            amounts.fold(
                zero(currency),
            ) { total, next -> total.add(next) }
    }

    fun add(other: Money): Money {
        requireSameCurrency(other)
        return Money(amount + other.amount, currency)
    }

    fun subtract(other: Money): Money {
        requireSameCurrency(other)
        return Money(amount - other.amount, currency)
    }

    fun multiply(factor: BigDecimal): Money = Money(amount * factor, currency)

    fun flooredAtZero(): Money = Money(amount.max(BigDecimal.ZERO), currency)

    private fun requireSameCurrency(other: Money) {
        require(currency == other.currency) {
            "Cannot combine amounts in different currencies: $currency and ${other.currency}"
        }
    }
}
