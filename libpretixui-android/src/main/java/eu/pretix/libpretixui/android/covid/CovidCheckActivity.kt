package eu.pretix.libpretixui.android.covid

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.databinding.DataBindingUtil
import com.ncorti.slidetoact.SlideToActView
import de.rki.covpass.sdk.cert.*
import de.rki.covpass.sdk.cert.models.Recovery
import de.rki.covpass.sdk.cert.models.RecoveryCertType
import de.rki.covpass.sdk.cert.models.TestCertType
import de.rki.covpass.sdk.cert.models.VaccinationCertType
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
        binding.acceptBarcode = binding.settings!!.accept_baercode || binding.settings!!.accept_eudgc

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
            val dgcResult = DGC().check(result)
            val dgcEntry = dgcResult.first
            val covCertificate = dgcResult.second

            checkData = "type: DGC"
            tvScannedDataPersonName.text = covCertificate.fullName
            tvScannedDataPersonDetails.text = covCertificate.birthDate.birthDate.toString()
            tvScannedDataMinDate.text = covCertificate.validFrom.toString()
            tvScannedDataMaxDate.text = covCertificate.validUntil.toString()
            binding.hasScannedResult = true
            binding.hasAcceptableScannedResult = true

            when (dgcEntry.type) {
                VaccinationCertType.VACCINATION_FULL_PROTECTION -> {
                    hideAllBut("vacc")
                }
                TestCertType.NEGATIVE_PCR_TEST -> {
                    hideAllBut("tested")
                }
                TestCertType.NEGATIVE_ANTIGEN_TEST -> {
                    hideAllBut("tested2")
                }
                RecoveryCertType.RECOVERY -> {
                    hideAllBut("cured")
                    if (!isValid((dgcEntry as Recovery).validFrom, dgcEntry.validUntil)) {
                        binding.hasAcceptableScannedResult = false
                    }
                }
                else -> {
                    binding.hasAcceptableScannedResult = false
                    hideAllBut()
                }
            }
            assertGermanValidationRuleSet(dgcResult.second.dgcEntry)
        } catch (exception: ValidationRuleViolationException) {
            binding.hasAcceptableScannedResult = false
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
            hideAllBut()
            binding.hasScannedResult = true
            binding.hasAcceptableScannedResult = false
            Toast.makeText(applicationContext, "Not a DGC", Toast.LENGTH_SHORT).show()
        }
    }

    fun hideAllBut(certType: String = "") {
        clVacc.visibility = View.GONE
        clCured.visibility = View.GONE
        clTested.visibility = View.GONE
        clTested2.visibility = View.GONE

        when (certType) {
            "vacc" -> {
                clVacc.visibility = View.VISIBLE
            }
            "cured" -> {
                clCured.visibility = View.VISIBLE
            }
            "tested" -> {
                clTested.visibility = View.VISIBLE
            }
            "tested2" -> {
                clTested2.visibility = View.VISIBLE
            }
        }
    }
}