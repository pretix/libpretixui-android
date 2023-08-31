package eu.pretix.libpretixui.android.scanning

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.view.Window
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.google.zxing.Result
import eu.pretix.libpretixui.android.R
import eu.pretix.libpretixui.android.databinding.ActivityScanBinding
import me.dm7.barcodescanner.zxing.ZXingScannerView

class ScanActivity : AppCompatActivity(), ZXingScannerView.ResultHandler {
    companion object {
        val RESULT = "RESULT"
    }

    private lateinit var binding: ActivityScanBinding

    public override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        requestWindowFeature(Window.FEATURE_NO_TITLE)
        supportActionBar?.setDisplayUseLogoEnabled(true)
        supportActionBar?.setDisplayShowHomeEnabled(true)
        binding = ActivityScanBinding.inflate(layoutInflater)
        setContentView(binding.root)


        val requestPermissionLauncher =
                registerForActivityResult(ActivityResultContracts.RequestPermission()
                ) { isGranted: Boolean ->
                    if (!isGranted) {
                        Toast.makeText(this, R.string.android_permission_required, Toast.LENGTH_SHORT).show()
                        finish()
                    } else {
                        binding.scannerView.startCamera()
                    }
                }
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            requestPermissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }

    override fun onResume() {
        super.onResume()
        binding.scannerView.setResultHandler(this)
        binding.scannerView.startCamera()
    }

    override fun onPause() {
        super.onPause()
        binding.scannerView.stopCamera()
    }

    override fun handleResult(rawResult: Result) {
        val i = Intent()
        i.putExtra(RESULT, rawResult.text)
        setResult(Activity.RESULT_OK, i)
        finish()
    }
}
