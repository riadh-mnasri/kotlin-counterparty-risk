package io.github.riadhmnasri.counterpartyrisk.model

private const val AAA_PD_PERCENTAGE = 0.00
private const val AA_PD_PERCENTAGE = 0.02
private const val A_PD_PERCENTAGE = 0.05
private const val BBB_PD_PERCENTAGE = 0.15
private const val BB_PD_PERCENTAGE = 0.62
private const val B_PD_PERCENTAGE = 3.5
private const val CCC_PD_PERCENTAGE = 27.0
private const val D_PD_PERCENTAGE = 100.0

/**
 * A 1-year probability-of-default table based on S&P Global Ratings'
 * long-run global corporate average one-year default rates by rating
 * category, as published across their annual "Default, Transition, and
 * Recovery" study series. These are indicative long-run averages, not a
 * specific study year's exact published figures (S&P groups CCC and below
 * into a single "CCC/C" bucket, mapped here to [CCC]); actual year-by-year
 * and cohort-by-cohort default rates vary. `D` is this library's own
 * terminal "defaulted" state, not part of S&P's forward-looking scale.
 *
 * This remains a teaching tool, not a regulatory or investment input: see
 * the disclaimer in the README before using it for anything beyond
 * learning or prototyping.
 */
enum class CreditRating(val probabilityOfDefault1y: Rate) {
    AAA(Rate.ofPercentage(AAA_PD_PERCENTAGE)),
    AA(Rate.ofPercentage(AA_PD_PERCENTAGE)),
    A(Rate.ofPercentage(A_PD_PERCENTAGE)),
    BBB(Rate.ofPercentage(BBB_PD_PERCENTAGE)),
    BB(Rate.ofPercentage(BB_PD_PERCENTAGE)),
    B(Rate.ofPercentage(B_PD_PERCENTAGE)),
    CCC(Rate.ofPercentage(CCC_PD_PERCENTAGE)),
    D(Rate.ofPercentage(D_PD_PERCENTAGE)),
    ;

    companion object {
        fun of(grade: String): CreditRating =
            entries.find { it.name == grade.uppercase() }
                ?: throw IllegalArgumentException(
                    "Unknown credit rating \"$grade\", expected one of ${entries.joinToString { it.name }}",
                )
    }
}
