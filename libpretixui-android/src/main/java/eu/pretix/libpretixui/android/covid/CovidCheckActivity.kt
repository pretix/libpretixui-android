package eu.pretix.libpretixui.android.covid

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.databinding.DataBindingUtil
import com.ncorti.slidetoact.SlideToActView
import de.rki.covpass.sdk.cert.*
import de.rki.covpass.sdk.cert.models.*
import de.rki.covpass.sdk.utils.isValid
import eu.pretix.libpretixui.android.R
import eu.pretix.libpretixui.android.databinding.ActivityCovidCheckBinding
import eu.pretix.libpretixui.android.scanning.HardwareScanner
import eu.pretix.libpretixui.android.scanning.ScanReceiver
import kotlinx.android.synthetic.main.activity_covid_check.*
import org.joda.time.LocalDate

class CovidCheckActivity : AppCompatActivity() {
    companion object {
        val REQUEST_CODE = 30175
        val RESULT_CODE = "result"
        val EXTRA_NAME = "name"
        val EXTRA_HARDWARE_SCAN = "hardware_scanner"
        val EXTRA_BIRTHDATE = "birthdate"
        val EXTRA_SETTINGS = "settings"
    }

    lateinit var binding: ActivityCovidCheckBinding
    var checkData = "type: manual"

    private val hardwareScanner = HardwareScanner(object : ScanReceiver {
        override fun scanResult(result: String) {
            handleScan(result)
        }
    })

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        binding = DataBindingUtil.setContentView<ActivityCovidCheckBinding>(this, R.layout.activity_covid_check)

        if (intent.extras?.containsKey(EXTRA_SETTINGS) == true) {
            binding.settings = intent.extras?.getSerializable(EXTRA_SETTINGS) as CovidCheckSettings
        } else {
            finish()
        }

        binding.name = intent.extras?.getString(EXTRA_NAME)
        binding.hasHardwareScanner = intent.extras?.getBoolean(EXTRA_HARDWARE_SCAN, false) ?: false
        binding.acceptBarcode = binding.settings!!.accept_eudgc

        if (intent.extras?.containsKey(EXTRA_BIRTHDATE) == true) {
            try {
                binding.birthdate = LocalDate.parse(intent.extras!!.getString(EXTRA_BIRTHDATE))
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }

        staConfirm.onSlideCompleteListener = object : SlideToActView.OnSlideCompleteListener {
            override fun onSlideComplete(view: SlideToActView) {
                setResult(Activity.RESULT_OK, Intent().putExtra(RESULT_CODE, checkData))
                finish()
            }
        }
    }

    override fun onResume() {
        super.onResume()
        hardwareScanner.start(this)
    }

    override fun onPause() {
        super.onPause()
        hardwareScanner.stop(this)
    }

    fun handleScan(result: String) {
        try {
            val dgc = DGC()
            val dgcResult = dgc.check(result)
            val dgcEntry = dgcResult.first
            val covCertificate = dgcResult.second

            checkData = "type: DGC"
            tvScannedDataPersonName.text = covCertificate.fullName
            tvScannedDataPersonDetails.text = covCertificate.birthDate.birthDate.toString()
            binding.hasScannedResult = true
            binding.hasAcceptableScannedResult = true

            when (dgcEntry.type) {
                VaccinationCertType.VACCINATION_INCOMPLETE,
                VaccinationCertType.VACCINATION_COMPLETE,
                VaccinationCertType.VACCINATION_FULL_PROTECTION -> {
                    tvScanValid.text = String.format("%s (DGC)", resources.getString(R.string.covid_check_vaccinated))
                    dgc.assertVaccinationRules(
                        dgcEntry as Vaccination,
                        binding.settings!!.allow_vaccinated_min,
                        binding.settings!!.allow_vaccinated_max
                    )

                    if (!binding.settings!!.allow_vaccinated) {
                        binding.hasAcceptableScannedResult = false
                    }
                }
                TestCertType.POSITIVE_PCR_TEST,
                TestCertType.NEGATIVE_PCR_TEST -> {
                    tvScanValid.text = String.format("%s (DGC)", resources.getString(R.string.covid_check_tested_pcr))
                    dgc.assertTestPCRRules(
                        dgcEntry as Test,
                        binding.settings!!.allow_tested_pcr_min,
                        binding.settings!!.allow_tested_pcr_max
                    )

                    if (!binding.settings!!.allow_tested_pcr) {
                        binding.hasAcceptableScannedResult = false
                    }
                }
                TestCertType.POSITIVE_ANTIGEN_TEST,
                TestCertType.NEGATIVE_ANTIGEN_TEST -> {
                    tvScanValid.text = String.format("%s (DGC)", resources.getString(R.string.covid_check_tested_other))
                    dgc.assertTestAGRules(
                        dgcEntry as Test,
                        binding.settings!!.allow_tested_antigen_unknown_min,
                        binding.settings!!.allow_tested_antigen_unknown_max
                    )

                    if (!binding.settings!!.allow_tested_antigen_unknown) {
                        binding.hasAcceptableScannedResult = false
                    }
                }
                RecoveryCertType.RECOVERY -> {
                    tvScanValid.text = String.format("%s (DGC)", resources.getString(R.string.covid_check_recovered))
                    dgc.assertRecoveryRules(
                        dgcEntry as Recovery,
                        binding.settings!!.allow_cured_min,
                        binding.settings!!.allow_cured_max
                    )

                    if (!binding.settings!!.allow_cured) {
                        binding.hasAcceptableScannedResult = false
                    }

                    if (!isValid(dgcEntry.validFrom, dgcEntry.validUntil)) {
                        binding.hasAcceptableScannedResult = false
                    }
                }
                else -> {
                    binding.hasAcceptableScannedResult = false

                }
            }
        } catch (exception: ValidationRuleViolationException) {
            binding.hasAcceptableScannedResult = false
            tvScanInvalid.text = String.format("%s (%s)", resources.getString(getValidationException(exception.ruleIdentifier)), exception.ruleIdentifier)
            Toast.makeText(applicationContext, "DGC ValidationRuleViolationException", Toast.LENGTH_SHORT).show()
        } catch (exception: BadCoseSignatureException) {
            binding.hasAcceptableScannedResult = false
            Toast.makeText(applicationContext, "DGC BadCoseSignatureException", Toast.LENGTH_SHORT).show()
        } catch (exception: ExpiredCwtException) {
            binding.hasAcceptableScannedResult = false
            Toast.makeText(applicationContext, "DGC ExpiredCwtException", Toast.LENGTH_SHORT).show()
        } catch (exception: NoMatchingExtendedKeyUsageException) {
            binding.hasAcceptableScannedResult = false
            Toast.makeText(applicationContext, "DGC NoMatchingExtendedKeyUsageException", Toast.LENGTH_SHORT).show()
        } catch (exception: Exception) {
            // Here we would normally run the next validator
            binding.hasScannedResult = true
            binding.hasAcceptableScannedResult = false
            Toast.makeText(applicationContext, "Not a DGC", Toast.LENGTH_SHORT).show()
        }
    }

    fun getValidationException(ruleIdentifier: String): Int {
        return resources.getIdentifier(String.format("covid_check_rules_%s", ruleIdentifier), "string", packageName)
    }
}