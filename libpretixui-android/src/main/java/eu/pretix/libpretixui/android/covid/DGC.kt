package eu.pretix.libpretixui.android.covid

import android.util.Log
import de.rki.covpass.sdk.cert.*
import de.rki.covpass.sdk.cert.models.*
import de.rki.covpass.sdk.dependencies.sdkDeps
import de.rki.covpass.sdk.utils.isValid


class DGC() {
    fun check(qrContent: String) {

        val qrCoder: QRCoder = sdkDeps.qrCoder

        try {
            val covCertificate = qrCoder.decodeCovCert(qrContent)
            val dgcEntry = covCertificate.dgcEntry
            assertGermanValidationRuleSet(dgcEntry)
            when (dgcEntry) {
                is Vaccination -> {
                    when (dgcEntry.type) {
                        VaccinationCertType.VACCINATION_FULL_PROTECTION -> {
                            Log.d("FOOBAR", "VACCINATION_FULL_PROTECTION - OK")
//                            eventNotifier {
//                                onValidationSuccess(covCertificate)
//                            }
                        }
                        else -> {
                            Log.d("FOOBAR", "VACCINATION_FULL_PROTECTION - FAIL")
//                            eventNotifier {
//                                onValidationFailure()
//                            }
                        }
                    }
                }
                is Test -> {
                    when (dgcEntry.type) {
                        TestCertType.NEGATIVE_PCR_TEST -> {
                            Log.d("FOOBAR", "NEGATIVE_PCR_TEST")
//                            handleNegativePcrResult(covCertificate)
                        }
                        TestCertType.POSITIVE_PCR_TEST -> {
                            Log.d("FOOBAR", "POSITIVE_PCR_TEST")
//                            eventNotifier { onValidationFailure() }
                        }
                        TestCertType.NEGATIVE_ANTIGEN_TEST -> {
                            Log.d("FOOBAR", "NEGATIVE_ANTIGEN_TEST")
//                            handleNegativeAntigenResult(covCertificate)
                        }
                        TestCertType.POSITIVE_ANTIGEN_TEST -> {
                            Log.d("FOOBAR", "POSITIVE_ANTIGEN_TEST")
//                            eventNotifier { onValidationFailure() }
                        }
                        // .let{} to enforce exhaustiveness
                    }.let {}
                }
                is Recovery -> {
                    if (isValid(dgcEntry.validFrom, dgcEntry.validUntil)) {
                        Log.d("FOOBAR", "RECOVERY - OK")
//                        eventNotifier { onValidationSuccess(covCertificate) }
                    } else {
                        Log.d("FOOBAR", "RECOVERY - NOK")
//                        eventNotifier { onValidationFailure() }
                    }
                }
                // .let{} to enforce exhaustiveness
            }.let {}
        } catch (exception: Exception) {
            when (exception) {
                is BadCoseSignatureException,
                is ExpiredCwtException,
                is NoMatchingExtendedKeyUsageException -> {
                    Log.d("FOOBAR", "VALIDATION FAILURE")
//                    Lumber.e(exception)
//                    eventNotifier { onValidationFailure() }
                }
                else -> throw exception
            }
        }
    }
}