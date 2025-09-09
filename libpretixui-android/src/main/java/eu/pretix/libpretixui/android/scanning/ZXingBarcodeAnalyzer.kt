package eu.pretix.libpretixui.android.scanning

import android.graphics.ImageFormat
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import com.google.zxing.BinaryBitmap
import com.google.zxing.DecodeHintType
import com.google.zxing.MultiFormatReader
import com.google.zxing.NotFoundException
import com.google.zxing.PlanarYUVLuminanceSource
import com.google.zxing.common.HybridBinarizer
import java.nio.ByteBuffer
import java.util.concurrent.atomic.AtomicBoolean

class ZXingBarcodeAnalyzer(private val listener: ScannerView.ResultHandler) : ImageAnalysis.Analyzer {
    private var multiFormatReader: MultiFormatReader = MultiFormatReader().apply {
        setHints(mapOf(DecodeHintType.ALSO_INVERTED to true))
    }
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
                listener.handleResult(ScannerView.Result(rawResult.text, rawResult.rawBytes))
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

    private fun getPixelData(width: Int, height: Int, plane: ImageProxy.PlaneProxy): ByteArray {
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