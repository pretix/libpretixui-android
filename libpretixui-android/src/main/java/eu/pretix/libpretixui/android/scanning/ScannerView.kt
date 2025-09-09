package eu.pretix.libpretixui.android.scanning

import android.annotation.SuppressLint
import android.content.Context
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraMetadata
import android.hardware.camera2.CaptureRequest
import android.util.AttributeSet
import android.util.Size
import android.view.OrientationEventListener
import android.view.Surface
import android.view.View
import android.widget.FrameLayout
import androidx.camera.camera2.interop.Camera2CameraControl
import androidx.camera.camera2.interop.Camera2CameraInfo
import androidx.camera.camera2.interop.CaptureRequestOptions
import androidx.camera.core.Camera
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.core.TorchState
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.content.ContextCompat
import androidx.lifecycle.findViewTreeLifecycleOwner
import java.util.concurrent.ExecutionException
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors


@SuppressLint("UnsafeOptInUsageError")
class ScannerView : FrameLayout {
    companion object {
        enum class ANALYZER { ZXING, MLKIT }
    }

    data class Result(
        val text: String,
        val rawBytes: ByteArray?,
    )

    interface ResultHandler {
        fun handleResult(rawResult: Result)
    }

    private var currentAnalyzer: Companion.ANALYZER = Companion.ANALYZER.ZXING
    private var resultHandler: ResultHandler? = null
    private var cameraProvider: ProcessCameraProvider? = null
    private var cameraExecutor: ExecutorService? = null
    private var previewView: PreviewView? = null
    private var torchState: Boolean = false
    private var torchTarget: Boolean = false
    private var autofocusState: Boolean = false
    private var autofocusTarget: Boolean = true
    private var orientationEventListener: OrientationEventListener? = null
    private var camera: Camera? = null

    constructor(context: Context) : super(context) {}

    constructor(context: Context, attributeSet: AttributeSet) : super(context, attributeSet) {}

    var torch: Boolean
        get() = torchTarget
        set(value) {
            torchTarget = value
            if (torchState != torchTarget) {
                camera?.cameraControl?.enableTorch(torchTarget)
            }
        }

    var autofocus: Boolean
        get() = autofocusTarget
        set(value) {
            autofocusTarget = value
            if (autofocusTarget != autofocusState) {
                enableAutofocus(autofocusTarget)
            }
        }

    override fun addView(child: View?) {
        super.addView(child)
    }

    fun setResultHandler(rh: ResultHandler) {
        this.resultHandler = rh
    }

    fun setAnalyzer(type: Companion.ANALYZER) {
        if (type != currentAnalyzer) {
            currentAnalyzer = type
            stopCamera()
            startCamera()
        }
    }

    fun startCamera() {
        removeAllViews()

        previewView = PreviewView(context).apply {
            scaleType = PreviewView.ScaleType.FILL_CENTER
            implementationMode = PreviewView.ImplementationMode.COMPATIBLE
        }
        addView(previewView)

        val cameraProviderFuture = ProcessCameraProvider.getInstance(context)
        cameraExecutor = Executors.newSingleThreadExecutor()

        cameraProviderFuture.addListener({
            val provider = try {
                cameraProviderFuture.get()
            } catch (e: ExecutionException) {
                // probably no camera? ignore
                e.printStackTrace()
                return@addListener
            }
            bindPreview(provider)
        }, ContextCompat.getMainExecutor(context))
    }

    @androidx.camera.camera2.interop.ExperimentalCamera2Interop
    private fun enableAutofocus(enabled: Boolean) {
        if (camera == null) return
        if (enabled) {
            Camera2CameraControl.from(camera!!.cameraControl).captureRequestOptions =
                CaptureRequestOptions.Builder()
                    .setCaptureRequestOption(
                        CaptureRequest.CONTROL_AF_MODE,
                        CameraMetadata.CONTROL_AF_MODE_CONTINUOUS_PICTURE
                    )
                    .build()
        } else {
            val chars = Camera2CameraInfo.from(camera!!.cameraInfo)
            val builder = CaptureRequestOptions.Builder()
            builder.setCaptureRequestOption(
                CaptureRequest.CONTROL_AF_MODE,
                CameraMetadata.CONTROL_AF_MODE_OFF
            )
            val distance = chars.getCameraCharacteristic(CameraCharacteristics.LENS_INFO_MINIMUM_FOCUS_DISTANCE)
            if (distance != null) {
                builder.setCaptureRequestOption(
                    CaptureRequest.LENS_FOCUS_DISTANCE,
                    distance
                )
            }
            Camera2CameraControl.from(camera!!.cameraControl).captureRequestOptions = builder.build()
        }
        autofocusState = enabled
    }

    private fun bindPreview(cameraProvider: ProcessCameraProvider) {
        this.cameraProvider = cameraProvider
        cameraProvider.unbindAll()

        val preview: Preview = Preview.Builder()
            .build()
        preview.setSurfaceProvider(previewView!!.surfaceProvider)

        var cameraSelector = CameraSelector.Builder().build()
        if (cameraProvider.hasCamera(CameraSelector.DEFAULT_BACK_CAMERA)) {
            cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA
        }

        val imageAnalysis = ImageAnalysis.Builder()
            .setTargetResolution(Size(this.width, this.height))
            /*.setResolutionSelector(
                ResolutionSelector.Builder()
                    .setResolutionStrategy(
                        ResolutionStrategy(
                            Size(this.width, this.height),
                            ResolutionStrategy.FALLBACK_RULE_CLOSEST_HIGHER_THEN_LOWER
                        )
                    )
                    .build()
            )*/
            .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
            .build()

        val resultHandler = object : ResultHandler {
            override fun handleResult(rawResult: Result) {
                post {
                    resultHandler?.handleResult(rawResult)
                }
            }
        }

        val analyzer = when(this.currentAnalyzer) {
            ANALYZER.ZXING -> ZXingBarcodeAnalyzer(resultHandler)
            ANALYZER.MLKIT -> MLKitBarcodeAnalyzer(resultHandler)
        }
        imageAnalysis.setAnalyzer(cameraExecutor!!, analyzer)

        orientationEventListener = object : OrientationEventListener(context) {
            override fun onOrientationChanged(orientation: Int) {
                // Monitors orientation values to determine the target rotation value
                val rotation: Int = when (orientation) {
                    in 45..134 -> Surface.ROTATION_270
                    in 135..224 -> Surface.ROTATION_180
                    in 225..314 -> Surface.ROTATION_90
                    else -> Surface.ROTATION_0
                }

                imageAnalysis.targetRotation = rotation
            }
        }
        orientationEventListener!!.enable()

        try {
            camera = cameraProvider.bindToLifecycle(
                findViewTreeLifecycleOwner()!!,
                cameraSelector,
                imageAnalysis,
                preview
            )
        } catch (e: IllegalArgumentException) {
            return  // Lifecycle no longer available
        }

        camera?.cameraControl?.enableTorch(torchTarget)
        if (!autofocusTarget) {
            enableAutofocus(autofocusTarget)
        }

        camera?.cameraInfo?.torchState?.observe(findViewTreeLifecycleOwner()!!) {
            this.torchState = it == TorchState.ON
        }

        camera?.cameraControl?.cancelFocusAndMetering()
    }

    fun stopCamera() {
        cameraProvider?.unbindAll()
        cameraExecutor?.shutdown()
        orientationEventListener?.disable()
    }


}
