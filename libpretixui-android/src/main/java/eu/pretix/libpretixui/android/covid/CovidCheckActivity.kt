package eu.pretix.libpretixui.android.covid

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.databinding.DataBindingUtil
import com.ncorti.slidetoact.SlideToActView
import de.rki.covpass.sdk.cert.*
import de.rki.covpass.sdk.cert.models.*
import de.rki.covpass.sdk.dependencies.sdkDeps
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

        val acceptBarcode = binding.acceptBarcode!!
        when {
            binding.settings!!.accept_manual and acceptBarcode -> {
                tvInstructions.text = String.format(
                    "%s %s",
                    resources.getString(R.string.covid_check_instructions_manual),
                    resources.getString(R.string.covid_check_instructions_also_barcode)
                )
            }
            binding.settings!!.accept_manual -> {
                tvInstructions.text = resources.getString(R.string.covid_check_instructions_manual)
            }
            acceptBarcode -> {
                tvInstructions.text = resources.getString(R.string.covid_check_instructions_barcode)
            }
            else -> {
                tvInstructions.text = resources.getString(R.string.covid_check_instructions_none)
            }
        }

        // Since we haven't implemented the Camera-Barcodescanner yet, we disable scanning on those devices for now
        if (binding.acceptBarcode == true && binding.hasHardwareScanner == false) {
            binding.acceptBarcode = false
        }

        // covpass-android-sdk requires at least SDK-Level 23, so we disable barcode-parsing for everything below that
        if (android.os.Build.VERSION.SDK_INT < 23) {
            tvDGCserver.text = "No DGC"
            tvDGCupdate.text = String.format("SDK_INT: %d", android.os.Build.VERSION.SDK_INT)
            binding.acceptBarcode = false
        } else {
            tvDGCserver.text = sdkDeps.trustServiceHost
            tvDGCupdate.text = sdkDeps.dscRepository.lastUpdate.value.toString()
        }

        if (intent.extras?.containsKey(EXTRA_BIRTHDATE) == true) {
            try {
                binding.birthdate = LocalDate.parse(intent.extras!!.getString(EXTRA_BIRTHDATE))
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }

        val proofClickListener = View.OnClickListener {
            hardwareScanner.stop(this)
            hideAllSections(except=it)
            binding.hasResult = true
            binding.hasAcceptableResult = true
        }

        clVacc.setOnClickListener(proofClickListener)
        clCured.setOnClickListener(proofClickListener)
        clTested.setOnClickListener(proofClickListener)
        clTested2.setOnClickListener(proofClickListener)

        staConfirm.onSlideCompleteListener = object : SlideToActView.OnSlideCompleteListener {
            override fun onSlideComplete(view: SlideToActView) {
                setResult(Activity.RESULT_OK, Intent().putExtra(RESULT_CODE, checkData))
                finish()
            }
        }

        btCapture.setOnClickListener {
            // ToDo
            // 1. Open ScanView
            // 2. pass the result to handleScan()
            // 3. Profit
        }
    }

    override fun onResume() {
        super.onResume()
        if (binding.acceptBarcode == true) {
            hardwareScanner.start(this)
        }
    }

    override fun onPause() {
        super.onPause()
        try {
            hardwareScanner.stop(this)
        } catch (exception: Exception) {
            // Scanner has probably been already stopped elsewhere.
        }
    }

    override fun onBackPressed() {
        if (binding.hasResult == true) {
            this.recreate()
        } else {
            super.onBackPressed()
        }
    }

    fun handleScan(result: String) {
        hardwareScanner.stop(this)
        binding.hasResult = true
        binding.hasScannedResult = true
        hideAllSections()

        try {
            val dgc = DGC()
            val dgcResult = dgc.check(result)
            val dgcEntry = dgcResult.first
            val covCertificate = dgcResult.second

            checkData = "type: DGC"
            tvScannedDataPersonName.text = covCertificate.fullName
            tvScannedDataPersonDetails.text = covCertificate.birthDate.birthDate.toString()
            hideAllSections(clScannedData as View)
            binding.hasAcceptableResult = true

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
                        binding.hasAcceptableResult = false
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
                        binding.hasAcceptableResult = false
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
                        binding.hasAcceptableResult = false
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
                        binding.hasAcceptableResult = false
                    }

                    if (!isValid(dgcEntry.validFrom, dgcEntry.validUntil)) {
                        binding.hasAcceptableResult = false
                    }
                }
                else -> {
                    binding.hasAcceptableResult = false

                }
            }
        } catch (exception: Exception) {
            binding.hasAcceptableResult = false
            when (exception) {
                is ValidationRuleViolationException -> {
                    tvScanInvalid.text = String.format("%s (%s)", resources.getString(getValidationException(exception.ruleIdentifier)), exception.ruleIdentifier)
                }
                is BadCoseSignatureException -> {
                    tvScanInvalid.text = String.format("%s (DGC)", resources.getString(R.string.covid_check_scan_badcosesignature))
                }
                is ExpiredCwtException -> {
                    tvScanInvalid.text = String.format("%s (DGC)", resources.getString(R.string.covid_check_scan_expiredcwt))
                }
                is NoMatchingExtendedKeyUsageException -> {
                    tvScanInvalid.text = String.format("%s (DGC)", resources.getString(R.string.covid_check_scan_nomatchingextendedkeyusage))
                }
                else -> {
                    tvScanInvalid.text = String.format("%s (EX)", resources.getString(R.string.covid_check_scan_invalid))
                }
            }
        }
    }

    fun getValidationException(ruleIdentifier: String): Int {
        return resources.getIdentifier(String.format("covid_check_rules_%s", ruleIdentifier), "string", packageName)
    }

    fun hideAllSections(except: View? = null) {
        clVacc.visibility = View.GONE
        clCured.visibility = View.GONE
        clTested.visibility = View.GONE
        clTested2.visibility = View.GONE
        except?.visibility = View.VISIBLE
    }
}