package io.github.riadhmnasri.counterpartyrisk.risklimit

import io.github.riadhmnasri.counterpartyrisk.entity.Counterparty
import io.github.riadhmnasri.counterpartyrisk.model.Money
import io.github.riadhmnasri.counterpartyrisk.model.Rate

private const val WARNING_THRESHOLD_PERCENTAGE = 80.0

/** App-defined early-warning threshold, as a fraction of the approved limit. */
val WARNING_THRESHOLD: Rate = Rate.ofPercentage(WARNING_THRESHOLD_PERCENTAGE)

/**
 * Compares a netting set's (or counterparty's) EAD against the
 * counterparty's approved credit limit.
 *
 * [warningThreshold] defaults to [WARNING_THRESHOLD] (80%); pass your own
 * to use a stricter or looser early-warning point for a specific
 * counterparty or business line.
 */
fun checkRiskLimit(
    counterparty: Counterparty,
    ead: Money,
    warningThreshold: Rate = WARNING_THRESHOLD,
): LimitStatus {
    val limit = counterparty.approvedLimit
    val warningAmount = warningThreshold.applyTo(limit)

    return when {
        ead.amount > limit.amount -> LimitStatus.BREACH
        ead.amount > warningAmount.amount -> LimitStatus.WARNING
        else -> LimitStatus.OK
    }
}
