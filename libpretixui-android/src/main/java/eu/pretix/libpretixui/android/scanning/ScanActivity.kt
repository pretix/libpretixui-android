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
import kotlinx.android.synthetic.main.activity_scan.*
import me.dm7.barcodescanner.zxing.ZXingScannerView

class ScanActivity : AppCompatActivity(), ZXingScannerView.ResultHandler {
    companion object {
        val RESULT = "RESULT"
    }

    public override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        requestWindowFeature(Window.FEATURE_NO_TITLE)
        supportActionBar?.setDisplayUseLogoEnabled(true)
        supportActionBar?.setDisplayShowHomeEnabled(true)
        setContentView(R.layout.activity_scan)

        val requestPermissionLauncher =
                registerForActivityResult(ActivityResultContracts.RequestPermission()
                ) { isGranted: Boolean ->
                    if (!isGranted) {
                        Toast.makeText(this, R.string.android_permission_required, Toast.LENGTH_SHORT).show()
                        finish()
                    } else {
                        scanner_view.startCamera()
                    }
                }
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            requestPermissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }

    override fun onResume() {
        super.onResume()
        scanner_view.setResultHandler(this)
        scanner_view.startCamera()
    }

    override fun onPause() {
        super.onPause()
        scanner_view.stopCamera()
    }

    override fun handleResult(rawResult: Result) {
        val i = Intent()
        i.putExtra(RESULT, rawResult.text)
        setResult(Activity.RESULT_OK, i)
        finish()
    }
}
