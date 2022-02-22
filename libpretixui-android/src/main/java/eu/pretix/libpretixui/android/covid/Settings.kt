package eu.pretix.libpretixui.android.covid

import org.joda.time.LocalDate
import org.joda.time.LocalDateTime
import java.io.Serializable

data class CovidCheckSettings(
    val record_proof: Boolean,
    val allow_vaccinated: Boolean,
    val allow_vaccinated_min: Int,  // days ago
    val allow_vaccinated_max: Int, // days ago
    val allow_vaccinated_products: Set<String>,
    val record_proof_vaccinated: Boolean,
    val allow_cured: Boolean,
    val allow_cured_min: Int, // days ago
    val allow_cured_max: Int, // days ago
    val record_proof_cured: Boolean,
    val allow_tested_pcr: Boolean,
    val allow_tested_pcr_min: Int, // hours ago
    val allow_tested_pcr_max: Int,  // hours ago
    val record_proof_tested_pcr: Boolean,
    val allow_tested_antigen_unknown: Boolean,
    val allow_tested_antigen_unknown_min: Int, // hours ago
    val allow_tested_antigen_unknown_max: Int,  // hours ago
    val record_proof_tested_antigen_unknown: Boolean,
    val allow_other: Boolean,
    val record_proof_other: Boolean,
    val record_validity_time: Boolean,
    val accept_eudgc: Boolean,
    val accept_manual: Boolean,
    val combination_rules: String?,
) : Serializable {

    // Helper method for the actual checks.
    // Note that max/min is swapped, since the settings are worded as "min. X days ago" while the
    // check needs "max allowed point in time"
    fun vaccMinDate(): LocalDate {
        return LocalDate.now().minusDays(allow_vaccinated_max)
    }

    fun vaccMaxDate(): LocalDate {
        return LocalDate.now().minusDays(allow_vaccinated_min)
    }

    fun curedMinDate(): LocalDate {
        return LocalDate.now().minusDays(allow_cured_max)
    }

    fun curedMaxDate(): LocalDate {
        return LocalDate.now().minusDays(allow_cured_min)
    }

    fun testedPcrMinDateTime(): LocalDateTime {
        return LocalDateTime.now().minusHours(allow_tested_pcr_max)
    }

    fun testedPcrMaxDateTime(): LocalDateTime {
        return LocalDateTime.now().minusHours(allow_tested_pcr_min)
    }

    fun testedOtherMinDateTime(): LocalDateTime {
        return LocalDateTime.now().minusHours(allow_tested_antigen_unknown_max)
    }

    fun testedOtherMaxDateTime(): LocalDateTime {
        return LocalDateTime.now().minusHours(allow_tested_antigen_unknown_min)
    }
}

val SAMPLE_SETTINGS = CovidCheckSettings(
    true,
    true,
    14,
    365, 
    setOf(
            "EU/1/20/1528", // Comirnaty
            "EU/1/20/1525", // Janssen
            "EU/1/20/1507", // Moderna
            "EU/1/21/1529"  // Vaxzevria
    ),
    false,
    true,
    27,
    180,
    false,
    true,
    0,
    72,
    false,
    true,
    0,
    48,
    false,
    false,
    false,
    false,
    true,
    true,
    sampleRuleSingleFactor
)

