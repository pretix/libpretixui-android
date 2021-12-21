package eu.pretix.libpretixui.android.covid

import de.rki.covpass.sdk.cert.models.DGCEntry
import de.rki.covpass.sdk.cert.models.TestCert
import java.time.ZonedDateTime


enum class Proof {
    INVALID,
    VACC,
    CURED,
    TESTED_PCR,
    TESTED_AG_UNKNOWN,
    OTHER
}

data class ScanResult(
    val proof: Proof,
    val provider: String,
    val validUntil: ZonedDateTime?,
    val text: String?,
    val name: String?,
    val dob: String?,
    val dgcEntry: DGCEntry?
) {
    fun isValid(): Boolean {
        return proof != Proof.INVALID
    }
}

