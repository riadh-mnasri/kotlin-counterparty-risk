package io.github.riadhmnasri.counterpartyrisk.model

/** An ISO 4217-style currency code (e.g. "USD", "EUR"). This library does not perform FX conversion. */
data class Currency(val code: String) {
    init {
        require(code.isNotBlank()) { "Currency code must not be blank" }
    }
}
