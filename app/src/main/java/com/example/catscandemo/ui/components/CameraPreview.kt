package com.example.catscandemo.ui.components

import android.util.Log
import android.view.ViewGroup
import android.widget.LinearLayout
import androidx.annotation.OptIn
import androidx.camera.core.*
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import com.google.mlkit.vision.barcode.BarcodeScannerOptions
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.common.InputImage



private var lastScanText: String? = null
private var lastScanTime = 0L
private const val DEBOUNCE_MS = 100
fun barcodeDebounce(
    text: String,
    onResult: (String) -> Unit
) {
    val now = System.currentTimeMillis()

    // 跟上一次一样 → 忽略
    if (text == lastScanText) return

    // 距离上次扫描太近 → 忽略（防抖）
    if (now - lastScanTime < DEBOUNCE_MS) return

    lastScanText = text
    lastScanTime = now

    onResult(text)
}
@OptIn(ExperimentalGetImage::class)
@Composable

fun CameraPreview(
    modifier: Modifier = Modifier,
    onCameraReady: (Camera) -> Unit = {},
    onBarcodeDetected: (String) -> Unit
) {
    val context = LocalContext.current

    AndroidView(
        factory = { ctx ->
            val previewView = PreviewView(ctx).apply {
                layoutParams = LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT
                )
            }

            val cameraProviderFuture = ProcessCameraProvider.getInstance(ctx)
            cameraProviderFuture.addListener({
                val cameraProvider = cameraProviderFuture.get()

                val preview = Preview.Builder().build()
                preview.setSurfaceProvider(previewView.surfaceProvider)
                val options = BarcodeScannerOptions.Builder()
                    .setBarcodeFormats(
                        Barcode.FORMAT_QR_CODE,
                        Barcode.FORMAT_CODE_128,
                        Barcode.FORMAT_CODE_39,
                        Barcode.FORMAT_EAN_13,
                        Barcode.FORMAT_EAN_8
                    ) // 速度更快，可选
                    .build()
                // MLKit Scanner（不用 bitmap）
                val scanner = BarcodeScanning.getClient(options)

                val analysis = ImageAnalysis.Builder()
                    .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                    .build()

                analysis.setAnalyzer(ContextCompat.getMainExecutor(context)) { imageProxy ->

                    try {
                        val mediaImage = imageProxy.image
                        if (mediaImage != null) {
                            val inputImage = InputImage.fromMediaImage(mediaImage, imageProxy.imageInfo.rotationDegrees)

                            scanner.process(inputImage)
                                .addOnSuccessListener { barcodes ->
                                    for (b in barcodes) {
                                        b.rawValue?.let {
                                            onBarcodeDetected(it)
                                        }
                                    }
                                }
                                .addOnCompleteListener {
                                    imageProxy.close()
                                }
                        } else {
                            imageProxy.close()
                        }
                    } catch (e: Exception) {
                        Log.e("CameraPreview", "分析失败: ${e.message}")
                        imageProxy.close()
                    }
                }

                try {
                    cameraProvider.unbindAll()
                    val camera = cameraProvider.bindToLifecycle(
                        ctx as LifecycleOwner,
                        CameraSelector.DEFAULT_BACK_CAMERA,
                        preview,
                        analysis
                    )
                    onCameraReady(camera)
                } catch (e: Exception) {
                    Log.e("CameraPreview", "绑定失败: ${e.message}")
                }
            }, ContextCompat.getMainExecutor(ctx))

            previewView
        },
        modifier = modifier
    )
}
