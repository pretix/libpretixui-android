package eu.pretix.libpretixui.android.scanning

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.ImageFormat
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraMetadata
import android.hardware.camera2.CaptureRequest
import android.preference.PreferenceManager
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
import androidx.camera.core.ImageProxy
import androidx.camera.core.ImageProxy.PlaneProxy
import androidx.camera.core.Preview
import androidx.camera.core.TorchState
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.content.ContextCompat
import androidx.lifecycle.findViewTreeLifecycleOwner
import com.google.zxing.BinaryBitmap
import com.google.zxing.MultiFormatReader
import com.google.zxing.NotFoundException
import com.google.zxing.PlanarYUVLuminanceSource
import com.google.zxing.common.HybridBinarizer
import java.nio.ByteBuffer
import java.util.concurrent.ExecutionException
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean


@SuppressLint("UnsafeOptInUsageError")
class ScannerView : FrameLayout {
    data class Result(
        val text: String,
        val rawBytes: ByteArray?,
    )

    interface ResultHandler {
        fun handleResult(rawResult: Result)
    }

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
    private var preferFrontCameraTarget: Boolean = false

    constructor(context: Context) : super(context) {}

    constructor(context: Context, attributeSet: AttributeSet) : super(context, attributeSet) {}

    var preferFrontCamera: Boolean
        get() = preferFrontCameraTarget
        set(value) {
            preferFrontCameraTarget = value
        }
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

        if (preferFrontCamera && cameraProvider.hasCamera(CameraSelector.DEFAULT_FRONT_CAMERA)) {
            cameraSelector = CameraSelector.DEFAULT_FRONT_CAMERA
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
        val analyzer = ZXingBarcodeAnalyzer(object : ResultHandler {
            override fun handleResult(rawResult: Result) {
                post {
                    resultHandler?.handleResult(rawResult)
                }
            }
        })
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


    class ZXingBarcodeAnalyzer(private val listener: ResultHandler) : ImageAnalysis.Analyzer {
        private var multiFormatReader: MultiFormatReader = MultiFormatReader()
        private var isScanning = AtomicBoolean(false)

        override fun analyze(image: ImageProxy) {
            if (isScanning.get()) {
                image.close()
                return
            }

            isScanning.set(true)

            if ((image.format == ImageFormat.YUV_420_888 || image.format == ImageFormat.YUV_422_888 || image.format == ImageFormat.YUV_444_888) && image.planes.size == 3) {
                val luminancePlane = image.planes[0]
                val rotatedImage = RotatedImage(
                    getPixelData(image.width, image.height, luminancePlane),
                    image.width,
                    image.height
                )
                rotateImageArray(rotatedImage, image.imageInfo.rotationDegrees)

                val planarYUVLuminanceSource = PlanarYUVLuminanceSource(
                    rotatedImage.byteArray,
                    rotatedImage.width,
                    rotatedImage.height,
                    0, 0,
                    rotatedImage.width,
                    rotatedImage.height,
                    false
                )
                val hybridBinarizer = HybridBinarizer(planarYUVLuminanceSource)
                val binaryBitmap = BinaryBitmap(hybridBinarizer)
                try {
                    val rawResult = multiFormatReader.decodeWithState(binaryBitmap)
                    listener.handleResult(Result(rawResult.text, rawResult.rawBytes))
                } catch (e: NotFoundException) {
                    // ignore, no barcode found
                } catch (e: ArrayIndexOutOfBoundsException) {
                    // ignore, this is something zxing seems to do if it does not like the barcode
                } finally {
                    multiFormatReader.reset()
                    image.close()
                }

                isScanning.set(false)
            }
        }

        private fun rotateImageArray(image: RotatedImage, degrees: Int) {
            val rotationCount = degrees / 90
            if (rotationCount == 1 || rotationCount == 3) {
                for (i in 0 until rotationCount) {
                    val rotatedData = ByteArray(image.width * image.height)
                    for (y in 0 until image.height) {
                        for (x in 0 until image.width) {
                            rotatedData[x * image.height + image.height - y - 1] =
                                image.byteArray[x + y * image.width]
                        }
                    }
                    image.byteArray = rotatedData
                    val tmp = image.width
                    image.width = image.height
                    image.height = tmp
                }
            }
        }

        private fun byteBufferToByteArray(buf: ByteBuffer): ByteArray {
            val ba = ByteArray(buf.remaining())
            buf.get(ba)
            buf.rewind()
            return ba
        }

        private fun getPixelData(width: Int, height: Int, plane: PlaneProxy): ByteArray {
            // On some devices, the data from camerax has a plane.pixelStride > 1 that is not handled by zxing
            val rawData = byteBufferToByteArray(plane.buffer)
            if (plane.pixelStride == 1 && plane.pixelStride == width) {
                return rawData
            }
            val rowOffset = plane.rowStride
            val nextPixelOffset = plane.pixelStride

            val cleanData = ByteArray(width * height)
            for (y in 0 until height) {
                for (x in 0 until width) {
                    cleanData[y * width + x] = rawData[y * rowOffset + x * nextPixelOffset]
                }
            }
            return cleanData
        }


        private class RotatedImage(var byteArray: ByteArray, var width: Int, var height: Int)
    }
}
