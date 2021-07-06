package eu.pretix.libpretixui.android.covid

import de.rki.covpass.sdk.cert.*
import de.rki.covpass.sdk.cert.models.*
import de.rki.covpass.sdk.dependencies.sdkDeps


class DGC() {
    fun check(qrContent: String): Pair<DGCEntry, CovCertificate> {

        val qrCoder: QRCoder = sdkDeps.qrCoder

        try {
            val covCertificate = qrCoder.decodeCovCert(qrContent)
            val dgcEntry = covCertificate.dgcEntry
            return Pair(dgcEntry, covCertificate)
        } catch (exception: Exception) {
            throw exception
        }
    }
}