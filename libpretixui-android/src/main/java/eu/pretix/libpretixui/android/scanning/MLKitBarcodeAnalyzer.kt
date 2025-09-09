package eu.pretix.libpretixui.android.scanning

import androidx.camera.core.ExperimentalGetImage
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import com.google.mlkit.vision.barcode.BarcodeScannerOptions
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.common.InputImage
import java.util.concurrent.atomic.AtomicBoolean

class MLKitBarcodeAnalyzer(private val listener: ScannerView.ResultHandler) : ImageAnalysis.Analyzer {

    private var isScanning = AtomicBoolean(false)

    private val options = BarcodeScannerOptions.Builder()
        // .setBarcodeFormats(Barcode.FORMAT_QR_CODE) // let us recognize everything
        .build()

    private val scanner = BarcodeScanning.getClient(options)

    @ExperimentalGetImage
    override fun analyze(imageProxy: ImageProxy) {
        val mediaImage = imageProxy.image
        if (mediaImage != null && !isScanning.get()) {
            val image = InputImage.fromMediaImage(mediaImage, imageProxy.imageInfo.rotationDegrees)

            isScanning.set(true)

            scanner.process(image)
                .addOnSuccessListener { barcodes ->
                    if (barcodes.isNotEmpty()) {
                        val bc = barcodes.first()
                        listener.handleResult(ScannerView.Result(bc.rawValue ?: "", bc.rawBytes))
                    }
                    isScanning.set(false)
                    imageProxy.close()
                }
                .addOnFailureListener { exception ->
                    isScanning.set(false)
                    imageProxy.close()
                }
        }
    }
}