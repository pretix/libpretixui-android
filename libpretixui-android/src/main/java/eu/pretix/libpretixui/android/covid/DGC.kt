package eu.pretix.libpretixui.android.covid

import android.app.Application
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequest
import androidx.work.WorkManager
import com.ensody.reactivestate.DependencyAccessor
import de.rki.covpass.sdk.cert.*
import de.rki.covpass.sdk.cert.models.*
import de.rki.covpass.sdk.dependencies.sdkDeps
import de.rki.covpass.sdk.cert.models.DGCEntry
import de.rki.covpass.sdk.cert.models.Recovery
import de.rki.covpass.sdk.cert.models.TestCert
import de.rki.covpass.sdk.cert.models.Vaccination
import de.rki.covpass.sdk.dependencies.SdkDependencies
import de.rki.covpass.sdk.utils.isOlderThan
import de.rki.covpass.sdk.worker.DSC_UPDATE_INTERVAL_HOURS
import de.rki.covpass.sdk.worker.DscListWorker
import org.bouncycastle.jce.provider.BouncyCastleProvider
import org.conscrypt.Conscrypt
import java.security.Security
import java.util.concurrent.TimeUnit

const val RULE_VR_001: String = "VR_001"
const val RULE_VR_002: String = "VR_002"
const val RULE_VR_003: String = "VR_003"
const val RULE_VR_004: String = "VR_004"
const val RULE_TR_001: String = "TR_001"
const val RULE_TR_002: String = "TR_002"
const val RULE_TR_003: String = "TR_003"
const val RULE_TR_004: String = "TR_004"
const val RULE_RR_001: String = "RR_001"
const val RULE_RR_002: String = "RR_002"


class ValidationRuleViolationException(val ruleIdentifier: String) : Exception()

@OptIn(DependencyAccessor::class)
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

    fun assertVaccinationRules(vaccination: Vaccination, minDays: Int, maxDays: Int) {
        assertRuleVr001(vaccination)
        assertRuleVr002(vaccination)
        assertRuleVr003(vaccination, minDays)
        assertRuleVr004(vaccination, maxDays)
    }

    fun assertTestPCRRules(test: TestCert, minHours: Int, maxHours: Int) {
        assertRuleTr001(test, TestCert.PCR_TEST)
        assertRuleTr002(test, minHours)
        assertRuleTr003(test, maxHours)
        assertRuleTr004(test)
    }

    fun assertTestAGRules(test: TestCert, minHours: Int, maxHours: Int) {
        assertRuleTr001(test, TestCert.ANTIGEN_TEST)
        assertRuleTr002(test, minHours)
        assertRuleTr003(test, maxHours)
        assertRuleTr004(test)
    }

    fun assertRecoveryRules(recovery: Recovery, minDays: Int, maxDays: Int) {
        assertRuleRr001(recovery, minDays)
        assertRuleRr002(recovery, maxDays)
    }

    private fun assertRuleVr001(vaccination: Vaccination) {
        assertValidationSuccess(vaccination.doseNumber == vaccination.totalSerialDoses || vaccination.isBooster, RULE_VR_001)
    }

    private fun assertRuleVr002(vaccination: Vaccination) {
        assertValidationSuccess(
            vaccination.product in listOf(
                "EU/1/20/1528", // Comirnaty
                "EU/1/20/1525", // Janssen
                "EU/1/20/1507", // Moderna
                "EU/1/21/1529"  // Vaxzevria
            ),
            RULE_VR_002
        )
    }

    private fun assertRuleVr003(vaccination: Vaccination, minDays: Int) {
        assertValidationSuccess(vaccination.occurrence?.isOlderThan(days = minDays.toLong()) == true || vaccination.isBooster, RULE_VR_003)
    }

    private fun assertRuleVr004(vaccination: Vaccination, maxDays: Int) {
        assertValidationSuccess(vaccination.occurrence?.isOlderThan(days = maxDays.toLong()) == false, RULE_VR_004)
    }

    private fun assertRuleTr001(test: TestCert, testType: String) {
        assertValidationSuccess(test.testType == testType, RULE_TR_001)
    }

    private fun assertRuleTr002(test: TestCert, minHours: Int) {
        assertValidationSuccess(test.sampleCollection?.isOlderThan(hours = minHours.toLong()) == true, RULE_TR_002)
    }

    private fun assertRuleTr003(test: TestCert, maxHours: Int) {
        assertValidationSuccess(test.sampleCollection?.isOlderThan(hours = maxHours.toLong()) == false, RULE_TR_003)
    }

    private fun assertRuleTr004(test: TestCert) {
        assertValidationSuccess(test.testResult == TestCert.NEGATIVE_RESULT, RULE_TR_004)
    }

    private fun assertRuleRr001(recovery: Recovery, minDays: Int) {
        assertValidationSuccess(recovery.firstResult?.isOlderThan(minDays.toLong()) == true, RULE_RR_001)
    }

    private fun assertRuleRr002(recovery: Recovery, maxDays: Int) {
        assertValidationSuccess(recovery.firstResult?.isOlderThan(maxDays.toLong()) == false, RULE_RR_002)
    }

    private fun assertValidationSuccess(success: Boolean, ruleIdentifier: String) {
        if (!success) throw ValidationRuleViolationException(ruleIdentifier)
    }

    fun init(application: Application) {
        // covpass-android-sdk requires at least SDK-Level 23, so we won't even try init the SDK.
        if (android.os.Build.VERSION.SDK_INT < 23) {
            return
        }
        // First, install Conscrypt and Bouncy Castle security providers.

        // Disable patented algorithms in Bouncy Castle
        System.setProperty("org.bouncycastle.ec.disable_mqv", "true")
        try {
            Security.removeProvider("BC")
        } catch (e: Throwable) {
            // Ignore if it's missing.
        }
        Security.addProvider(BouncyCastleProvider())
        Security.insertProviderAt(Conscrypt.newProvider(), 1)

        // Now init the SDK
        sdkDeps = object : SdkDependencies() {
            override val application: Application = application
        }

        sdkDeps.validator.updateTrustedCerts(sdkDeps.dscRepository.dscList.value.toTrustedCerts())

        val tag = "dscListWorker"
        val dscListWorker: PeriodicWorkRequest =
            PeriodicWorkRequest.Builder(DscListWorker::class.java, DSC_UPDATE_INTERVAL_HOURS, TimeUnit.HOURS)
                .addTag(tag)
                .build()
        WorkManager.getInstance(application).enqueueUniquePeriodicWork(
            tag,
            ExistingPeriodicWorkPolicy.KEEP,
            dscListWorker,
        )
    }
}