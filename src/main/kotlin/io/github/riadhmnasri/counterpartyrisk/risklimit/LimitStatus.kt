package io.github.riadhmnasri.counterpartyrisk.risklimit

/** The result of comparing an EAD against a counterparty's approved credit limit. */
enum class LimitStatus {
    OK,
    WARNING,
    BREACH,
}
