package eu.pretix.libpretixui.android

import android.Manifest
import android.content.Context
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.graphics.SurfaceTexture
import android.hardware.usb.UsbDevice
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.util.Size
import android.view.Menu
import android.view.Surface
import android.view.View
import android.widget.Toast
import androidx.annotation.RequiresApi
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.serenegiant.usb.USBMonitor
import com.serenegiant.usb.UVCCamera
import eu.pretix.libpretixui.android.uvc.CameraDialog
import kotlinx.android.synthetic.main.activity_photo_capture.*
import java.io.File
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.Executors


@RequiresApi(Build.VERSION_CODES.LOLLIPOP)
class PhotoCaptureActivity : CameraDialog.CameraDialogParent, AppCompatActivity() {
    companion object {
        private const val TAG = "PhotoCaptureActivity"
        private const val FILENAME_FORMAT = "yyyy-MM-dd-HH-mm-ss-SSS"
        private const val REQUEST_CODE_PERMISSIONS = 10
        private const val PREVIEW_RES_W = 900
        private const val PREVIEW_RES_H = 1200
        private const val STORAGE_RES_W = 900
        private const val STORAGE_RES_H = 1200
        private val REQUIRED_PERMISSIONS = arrayOf(Manifest.permission.CAMERA)
    }

    private var imageCapture: ImageCapture? = null
    private var cameraProvider: ProcessCameraProvider? = null
    private var requestedCameraString: String? = "back"
    private lateinit var outputDirectory: File
    private lateinit var prefs: SharedPreferences

    private val sync = Any()

    // for accessing USB and USB camera
    private var usbMonitor: USBMonitor? = null
    private var uvcCamera: UVCCamera? = null
    private var uvcPreviewSurface: Surface? = null
    val executorService = Executors.newFixedThreadPool(1)


    private val onDeviceConnectListener = object : USBMonitor.OnDeviceConnectListener {
        override fun onAttach(device: UsbDevice) {
            if (requestedCameraString == "usb:${device.serialNumber}") {
                usbMonitor!!.requestPermission(device)
            }
        }

        override fun onConnect(device: UsbDevice, ctrlBlock: USBMonitor.UsbControlBlock, createNew: Boolean) {
            releaseUVCCamera()
            if (requestedCameraString == "usb:${device.serialNumber}") {
                runOnUiThread {
                    viewFinder.visibility = View.GONE
                    uvcTexture.visibility = View.VISIBLE
                    cameraProvider?.unbindAll()
                }
                prefs.edit().putString("camera", requestedCameraString).apply()
            } else {
                return
            }
            executorService.execute {
                val camera = UVCCamera()
                camera.open(ctrlBlock)
                camera.setStatusCallback { statusClass, event, selector, statusAttribute, data ->
                    Log.d(TAG, "USB camera reported onStatus(statusClass=" + statusClass
                            + "; " +
                            "event=" + event + "; " +
                            "selector=" + selector + "; " +
                            "statusAttribute=" + statusAttribute + "; " +
                            "data=...)")
                }
                camera.setButtonCallback { button, state ->
                    Log.d(TAG, "USB camera reported onButton(button=" + button + "; state=" + state + ")")
                }
                //					camera.setPreviewTexture(camera.getSurfaceTexture());
                uvcPreviewSurface?.release()
                uvcPreviewSurface = null
                val modes = listOf(
                        arrayOf(PREVIEW_RES_W, PREVIEW_RES_H, UVCCamera.FRAME_FORMAT_MJPEG),
                        arrayOf(PREVIEW_RES_H, PREVIEW_RES_W, UVCCamera.FRAME_FORMAT_MJPEG),
                        arrayOf(1280, 720, UVCCamera.FRAME_FORMAT_MJPEG),
                        arrayOf(UVCCamera.DEFAULT_PREVIEW_WIDTH, UVCCamera.DEFAULT_PREVIEW_HEIGHT, UVCCamera.FRAME_FORMAT_MJPEG),
                        arrayOf(UVCCamera.DEFAULT_PREVIEW_WIDTH, UVCCamera.DEFAULT_PREVIEW_HEIGHT, UVCCamera.DEFAULT_PREVIEW_MODE)
                )
                var previewSize: Size? = null
                for (m in modes) {
                    try {
                        camera.setPreviewSize(m[0], m[1], m[2])
                        previewSize = Size(m[0], m[1])
                        break
                    } catch (e: IllegalArgumentException) {
                        continue
                    }
                }
                if (previewSize != null) {
                    uvcTexture.setDataSize(previewSize.width, previewSize.height)
                    val st: SurfaceTexture = uvcTexture.surfaceTexture ?: return@execute
                    uvcPreviewSurface = Surface(st)
                    camera.setPreviewDisplay(uvcPreviewSurface)
                    camera.startPreview()
                    synchronized(sync) { uvcCamera = camera }
                } else {
                    // todo: no supported camera resolution
                }
            }
        }

        override fun onDisconnect(device: UsbDevice, ctrlBlock: USBMonitor.UsbControlBlock?) {
            // TODO you should check whether the coming device equal to camera device that currently using
            releaseUVCCamera()
        }

        override fun onDettach(device: UsbDevice?) {}

        override fun onCancel(device: UsbDevice?) {}
    }


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_photo_capture)

        outputDirectory = getOutputDirectory()
        prefs = getSharedPreferences("PhotoCaptureActivity", Context.MODE_PRIVATE)
        requestedCameraString = prefs.getString("camera", "back")

        if (allPermissionsGranted()) {
            startCamera()
        } else {
            ActivityCompat.requestPermissions(
                    this, REQUIRED_PERMISSIONS, REQUEST_CODE_PERMISSIONS)
        }

        btCapture.setOnClickListener { takePhoto() }
        uvcTexture.setAspectRatio(3.0 / 4.0)
        usbMonitor = USBMonitor(this, onDeviceConnectListener)
    }

    private fun takePhoto() {
        val imageCapture = imageCapture ?: return

        val photoFile = File(
                outputDirectory,
                SimpleDateFormat(FILENAME_FORMAT, Locale.US).format(System.currentTimeMillis()) + ".jpg"
        )

        val outputOptions = ImageCapture.OutputFileOptions.Builder(photoFile)
                .build()

        imageCapture.takePicture(
                outputOptions, ContextCompat.getMainExecutor(this), object : ImageCapture.OnImageSavedCallback {
            override fun onError(exc: ImageCaptureException) {
                Log.e(TAG, "Photo capture failed: ${exc.message}", exc)
            }

            override fun onImageSaved(output: ImageCapture.OutputFileResults) {
                val savedUri = Uri.fromFile(photoFile)
                val msg = "Photo capture succeeded: $savedUri"
                Toast.makeText(baseContext, msg, Toast.LENGTH_SHORT).show()
                Log.d(TAG, msg)
            }
        })

    }

    private fun hasBackCamera(): Boolean {
        return cameraProvider?.hasCamera(CameraSelector.DEFAULT_BACK_CAMERA) ?: false
    }

    private fun hasFrontCamera(): Boolean {
        return cameraProvider?.hasCamera(CameraSelector.DEFAULT_FRONT_CAMERA) ?: false
    }

    private fun startCamera() {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(this)
        cameraProviderFuture.addListener(Runnable {
            cameraProvider = cameraProviderFuture.get()
            invalidateOptionsMenu()

            if (requestedCameraString == "front" || requestedCameraString == "back") {
                runOnUiThread {
                    viewFinder.visibility = View.VISIBLE
                    uvcTexture.visibility = View.GONE
                }

                val preview = Preview.Builder()
                        .build()
                        .also {
                            it.setSurfaceProvider(viewFinder.surfaceProvider)
                        }

                imageCapture = ImageCapture.Builder()
                        .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
                        .setTargetResolution(Size(STORAGE_RES_W, STORAGE_RES_H))
                        .build()

                // Select back camera as a default
                val cameraSelector = if (requestedCameraString == "front" && hasFrontCamera()) {
                    CameraSelector.DEFAULT_FRONT_CAMERA
                } else {
                    CameraSelector.DEFAULT_BACK_CAMERA
                }

                try {
                    cameraProvider!!.unbindAll()
                    cameraProvider!!.bindToLifecycle(this, cameraSelector, preview, imageCapture)
                } catch (exc: Exception) {
                    Log.e(TAG, "Use case binding failed", exc)
                }
            } else if (requestedCameraString == "usb") {
                runOnUiThread {
                    viewFinder.visibility = View.GONE
                    uvcTexture.visibility = View.VISIBLE
                }
                cameraProvider!!.unbindAll()
            }
        }, ContextCompat.getMainExecutor(this))
    }

    private fun allPermissionsGranted() = REQUIRED_PERMISSIONS.all {
        ContextCompat.checkSelfPermission(baseContext, it) == PackageManager.PERMISSION_GRANTED
    }

    private fun getOutputDirectory(): File {
        val mediaDir = externalMediaDirs.firstOrNull()?.let {
            File(it, "tmp").apply { mkdirs() }
        }
        return if (mediaDir != null && mediaDir.exists())
            mediaDir else filesDir
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        if (hasBackCamera()) {
            val mi = menu.add(getString(R.string.camera_back))
            mi.setOnMenuItemClickListener {
                requestedCameraString = "back"
                prefs.edit().putString("camera", requestedCameraString).apply()
                startCamera()
                true
            }
        }
        if (hasFrontCamera()) {
            val mi = menu.add(getString(R.string.camera_front))
            mi.setOnMenuItemClickListener {
                requestedCameraString = "front"
                prefs.edit().putString("camera", requestedCameraString).apply()
                startCamera()
                true
            }
        }
        val mi = menu.add(getString(R.string.camera_usb))
        mi.setOnMenuItemClickListener {
            synchronized(sync) {
                CameraDialog.showDialog(this)
            }
            true
        }
        return super.onCreateOptionsMenu(menu)
    }

    override fun onRequestPermissionsResult(
            requestCode: Int, permissions: Array<String>, grantResults:
            IntArray) {
        if (requestCode == REQUEST_CODE_PERMISSIONS) {
            if (allPermissionsGranted()) {
                startCamera()
            } else {
                Toast.makeText(this,
                        "No permission to open camera.",
                        Toast.LENGTH_SHORT).show()
                finish()
            }
        }
    }

    override fun onResume() {
        super.onResume()
        synchronized(sync) {
            usbMonitor?.register()
            uvcCamera?.startPreview()
        }
    }

    override fun onPause() {
        super.onPause()
        releaseUVCCamera()
        synchronized(sync) {
            usbMonitor?.unregister()
        }
    }

    @Synchronized
    private fun releaseUVCCamera() {
        synchronized(sync) {
            try {
                uvcCamera?.setStatusCallback(null)
                uvcCamera?.setButtonCallback(null)
                uvcCamera?.close()
                uvcCamera?.destroy()
            } catch (e: java.lang.Exception) {
                e.printStackTrace()
            }
            uvcCamera = null
            uvcPreviewSurface?.release()
            uvcPreviewSurface = null
        }
    }

    override fun getUSBMonitor(): USBMonitor? {
        return usbMonitor
    }

    override fun onDialogResult(canceled: Boolean, usbDevice: UsbDevice?) {
        if (!canceled) {
            requestedCameraString = "usb:${usbDevice!!.serialNumber}"
            cameraProvider?.unbindAll()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        usbMonitor?.destroy()
        usbMonitor = null
    }
}