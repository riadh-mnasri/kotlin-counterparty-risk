package io.github.riadhmnasri.counterpartyrisk.model

private const val AAA_PD_PERCENTAGE = 0.01
private const val AA_PD_PERCENTAGE = 0.02
private const val A_PD_PERCENTAGE = 0.05
private const val BBB_PD_PERCENTAGE = 0.15
private const val BB_PD_PERCENTAGE = 1.0
private const val B_PD_PERCENTAGE = 3.0
private const val CCC_PD_PERCENTAGE = 8.0
private const val D_PD_PERCENTAGE = 100.0

/**
 * A simplified, app-defined 1-year probability-of-default table, not a
 * real rating agency or regulatory table.
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
