package io.github.riadhmnasri.counterpartyrisk.entity

import io.github.riadhmnasri.counterpartyrisk.model.Currency

/** A group of SFTs against a single counterparty, netted together under a GMRA/GMSLA-style master agreement. */
data class NettingSet(
    val id: String,
    val counterpartyId: String,
    /** The single currency every Money amount in this netting set is expressed in. */
    val reportingCurrency: Currency,
    val transactions: List<SftTransaction>,
) {
    init {
        require(id.isNotBlank()) { "NettingSet id must not be blank" }
        require(counterpartyId.isNotBlank()) { "NettingSet counterpartyId must not be blank" }
        transactions.forEach { assertMatchesReportingCurrency(it) }
    }

    fun withTransaction(transaction: SftTransaction): NettingSet {
        assertMatchesReportingCurrency(transaction)
        return copy(transactions = transactions + transaction)
    }

    private fun assertMatchesReportingCurrency(transaction: SftTransaction) {
        require(transaction.exposureCurrency == reportingCurrency) {
            "Transaction \"${transaction.id}\" exposure currency (${transaction.exposureCurrency}) " +
                "must match the netting set's reporting currency ($reportingCurrency)"
        }
    }
}
