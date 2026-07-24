package io.github.riadhmnasri.counterpartyrisk.exposure

import io.github.riadhmnasri.counterpartyrisk.model.Money

/**
 * [eStar] is the net exposure after the comprehensive haircut approach.
 * [ead] is the Exposure at Default. For standardized SFTs this equals
 * [eStar] directly, with no alpha multiplier (that is an IMM/derivatives
 * convention, out of scope for this library).
 */
data class ExposureResult(val eStar: Money, val ead: Money)
