package eu.pretix.libpretixui.android.covid

import org.joda.time.LocalDate
import org.joda.time.LocalDateTime
import java.io.Serializable

data class CovidCheckSettings(
        val allow_vaccinated: Boolean,
        val allow_vaccinated_min: Int,  // days ago
        val allow_vaccinated_max: Int, // days ago
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
        val accept_eudgc: Boolean,
        val accept_manual: Boolean,
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
        14,
        365,
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
        true,
        true,
)

