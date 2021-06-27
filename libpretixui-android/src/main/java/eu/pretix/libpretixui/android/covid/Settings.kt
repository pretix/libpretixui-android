package eu.pretix.libpretixui.android.covid

data class CovidCheckSettings(
        val allow_vaccinated: Boolean,
        val allow_vaccinated_min: Int,  // days ago
        val allow_vaccinated_max: Int, // days ago
        val allow_cured: Boolean,
        val allow_cured_min: Int, // days ago
        val allow_cured_max: Int, // days ago
        val allow_tested_pcr: Boolean,
        val allow_tested_pcr_min: Int, // hours ago
        val allow_tested_pcr_max: Int,  // hours ago
        val allow_tested_antigen_unknown: Boolean,
        val allow_tested_antigen_unknown_min: Int, // hours ago
        val allow_tested_antigen_unknown_max: Int,  // hours ago
        val accept_eudgc: Boolean,
        val accept_baercode: Boolean,
        val accept_manual: Boolean
) {
}

val SAMPLE_SETTINGS = CovidCheckSettings(
        true,
        14,
        365,
        true,
        28,
        182,  // todo: is days okay? do we need to actually count calendar months?
true,
        0,
        72,
        true,
        0,
        24,
        true,
        true,
        true
)

