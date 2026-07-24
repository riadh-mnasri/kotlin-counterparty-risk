package io.github.riadhmnasri.counterpartyrisk.entity

import io.github.riadhmnasri.counterpartyrisk.model.CreditRating
import io.github.riadhmnasri.counterpartyrisk.model.Money
import java.math.BigDecimal

/** A counterparty our institution faces across one or more netting sets. */
data class Counterparty(
    val id: String,
    val name: String,
    val rating: CreditRating,
    val approvedLimit: Money,
) {
    init {
        require(id.isNotBlank()) { "Counterparty id must not be blank" }
        require(name.isNotBlank()) { "Counterparty name must not be blank" }
        require(approvedLimit.amount >= BigDecimal.ZERO) { "Counterparty approved limit cannot be negative" }
    }
}
