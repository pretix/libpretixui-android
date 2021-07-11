package eu.pretix.libpretixui.android.scanning

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.view.Window
import androidx.appcompat.app.AppCompatActivity
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
