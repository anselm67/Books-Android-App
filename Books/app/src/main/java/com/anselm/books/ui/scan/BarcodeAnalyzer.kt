package com.anselm.books.ui.scan

import android.annotation.SuppressLint
import android.graphics.Rect
import android.util.Log
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.core.ResolutionInfo
import androidx.camera.view.PreviewView
import com.anselm.books.TAG
import com.google.mlkit.vision.barcode.BarcodeScanner
import com.google.mlkit.vision.barcode.BarcodeScannerOptions
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.common.InputImage
import kotlin.math.roundToInt

class BarcodeAnalyzer(
    private val overlay: OverlayView,
    private val onISBN: ((String) -> Unit)? = null,
) : ImageAnalysis.Analyzer {
    private var scanner: BarcodeScanner
    private val knownBarcodes = mutableSetOf<String>()

    init {
        val options = BarcodeScannerOptions.Builder()
            .setBarcodeFormats(
                Barcode.FORMAT_EAN_13,
            )
            .build()
        scanner = BarcodeScanning.getClient(options)
    }

    override fun analyze(imageProxy: ImageProxy) {
        @SuppressLint("UnsafeOptInUsageError")
        val mediaImage = imageProxy.image
        if (mediaImage == null ) {
            imageProxy.close()
            return
        } else {
            val image = InputImage.fromMediaImage(mediaImage, imageProxy.imageInfo.rotationDegrees)
            scanner.process(image)
                .addOnSuccessListener { barcodes -> handleBarcodes(barcodes) }
                .addOnFailureListener {
                    Log.e(TAG, "Failed", it)
                }
                .addOnCompleteListener {
                    imageProxy.close()
                }
        }
    }

    /**
     * Atomically adds a barcode to the set; Returns true if the isbn is new,false if it was
     * already known of.
     */
    @Synchronized
    private fun addBarcode(isbn: String): Boolean {
        return if (knownBarcodes.contains(isbn) ) {
            false
        } else {
            knownBarcodes.add(isbn)
            return true
        }
    }

    private fun handleBarcodes(barcodes: List<Barcode>) {
        overlay.clearRect()
        for (barcode in barcodes) {
            if ((barcode.valueType != Barcode.TYPE_ISBN) || (barcode.rawValue == null)) {
                continue
            }
            val isbn = barcode.rawValue!!
            if ( addBarcode(isbn) ) {
                // We're adding this isbn to our known list.
                barcode.boundingBox?.let { drawAddedRect(it) }
                onISBN?.invoke(isbn)
            } else {
                // This is a known isbn, draw it as such.
                barcode.boundingBox?.let { drawKnownRect(it) }
            }
        }
        overlay.postInvalidate()
    }

    private fun drawKnownRect(rect: Rect) {
        overlay.drawRect(convertRect(rect), known = true)
    }

    private fun drawAddedRect(rect: Rect) {
        overlay.drawRect(convertRect(rect), known = false)
    }

    private fun convertRect(analyzerRect: Rect): Rect {
        val (left, top) = scale(analyzerRect.left, analyzerRect.top)
        val (right, bottom)  = scale(analyzerRect.right, analyzerRect.bottom)
        return Rect(left, top, right,bottom)
    }

    private var scaleX:Float = 1.0F
    private var scaleY:Float = 1.0F

    private fun scale(x: Int, y: Int): Pair<Int, Int> {
        return Pair((x * scaleX).roundToInt(), (y * scaleY).roundToInt())
    }

    @SuppressLint("SwitchIntDef")
    fun scaleFor(resolutionInfo: ResolutionInfo, previewView: PreviewView) {
        when (resolutionInfo.rotationDegrees) {
            0 -> {
                scaleX = previewView.width.toFloat() / resolutionInfo.resolution.width.toFloat()
                scaleY = previewView.height.toFloat() / resolutionInfo.resolution.height.toFloat()
            }
            90 -> {
                scaleX = previewView.width.toFloat() / resolutionInfo.resolution.height.toFloat()
                scaleY = previewView.height.toFloat() / resolutionInfo.resolution.width.toFloat()
            }
            180 -> {
                scaleX = previewView.width.toFloat() / resolutionInfo.resolution.width.toFloat()
                scaleY = previewView.height.toFloat() / resolutionInfo.resolution.height.toFloat()
            }
            270 -> {
                scaleX = previewView.width.toFloat() / resolutionInfo.resolution.height.toFloat()
                scaleY = previewView.height.toFloat() / resolutionInfo.resolution.width.toFloat()
            }
        }
    }

}