package com.example.catscandemo.ui.components

import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.YuvImage
import android.graphics.ImageFormat
import java.io.ByteArrayOutputStream
import android.graphics.Rect
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.media.Image
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.util.Size
import android.view.ViewGroup
import android.widget.LinearLayout
import androidx.annotation.OptIn
import androidx.camera.core.*
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color as ComposeColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import com.example.catscandemo.utils.NativeBarcodeDetector
import com.example.catscandemo.utils.RealtimeFrameCropEngine
import com.google.android.gms.tasks.Tasks
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.common.InputImage
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger

/**
 * 鏉＄爜绋冲畾鍣ㄧ被
 */
class BarcodeStabilizer {
    private var stableText: String? = null
    private var stableCount = 0
    private val stableThreshold = 2
    
    @Synchronized
    fun stabilize(text: String, onStable: (String) -> Unit) {
        if (text == stableText) {
            stableCount++
        } else {
            stableText = text
            stableCount = 1
        }
        
        if (stableCount >= stableThreshold) {
            onStable(text)
            reset()
        }
    }
    
    @Synchronized
    fun reset() {
        stableText = null
        stableCount = 0
    }
}

/**
 * 蹇€熷浘鍍忓寮?- 瀵规瘮搴︽媺浼?+ 閿愬寲
 */
object FastImageEnhancer {
    
    /**
     * 澧炲己鐗堬細瀵规瘮搴︽媺浼?+ 灞€閮ㄩ攼鍖?
     */
    fun enhanceFromLuminanceV2(luminance: ByteArray, width: Int, height: Int, scaleFactor: Int): Bitmap {
        val newWidth = width / scaleFactor
        val newHeight = height / scaleFactor
        
        val bitmap = Bitmap.createBitmap(newWidth, newHeight, Bitmap.Config.ARGB_8888)
        val pixels = IntArray(newWidth * newHeight)
        val grayValues = IntArray(newWidth * newHeight)
        
        // 绗竴閬嶏細閲囨牱骞舵壘 min/max
        var minVal = 255
        var maxVal = 0
        
        for (y in 0 until newHeight) {
            for (x in 0 until newWidth) {
                val srcX = x * scaleFactor
                val srcY = y * scaleFactor
                val idx = srcY * width + srcX
                if (idx < luminance.size) {
                    val gray = luminance[idx].toInt() and 0xFF
                    grayValues[y * newWidth + x] = gray
                    if (gray < minVal) minVal = gray
                    if (gray > maxVal) maxVal = gray
                }
            }
        }
        
        val range = maxVal - minVal
        
        // 绗簩閬嶏細瀵规瘮搴︽媺浼?+ 绠€鍗曢攼鍖?
        for (y in 0 until newHeight) {
            for (x in 0 until newWidth) {
                val i = y * newWidth + x
                var gray = grayValues[i]
                
                // 瀵规瘮搴︽媺浼?
                val stretched = if (range > 10) ((gray - minVal) * 255) / range else gray
                
                // 绠€鍗曢攼鍖栵細澧炲己涓庨偦鍩熺殑宸紓
                if (x > 0 && x < newWidth - 1) {
                    val left = grayValues[i - 1]
                    val right = grayValues[i + 1]
                    val avg = (left + right) / 2
                    val diff = stretched - avg
                    gray = (stretched + diff / 2).coerceIn(0, 255)
                } else {
                    gray = stretched.coerceIn(0, 255)
                }
                
                // 澧炲己瀵规瘮搴︼細灏嗕腑闂村€兼帹鍚戜袱绔?
                val enhanced = if (gray > 128) {
                    (gray + (gray - 128) / 2).coerceIn(0, 255)
                } else {
                    (gray - (128 - gray) / 2).coerceIn(0, 255)
                }
                
                pixels[i] = Color.rgb(enhanced, enhanced, enhanced)
            }
        }
        
        bitmap.setPixels(pixels, 0, newWidth, 0, 0, newWidth, newHeight)
        return bitmap
    }
    
    fun extractAndEnhance(image: android.media.Image, scaleFactor: Int = 4): Bitmap {
        val yBuffer = image.planes[0].buffer
        val width = image.width
        val height = image.height
        
        val newWidth = width / scaleFactor
        val newHeight = height / scaleFactor
        
        val bitmap = Bitmap.createBitmap(newWidth, newHeight, Bitmap.Config.ARGB_8888)
        val pixels = IntArray(newWidth * newHeight)
        
        var minVal = 255
        var maxVal = 0
        
        for (y in 0 until newHeight) {
            for (x in 0 until newWidth) {
                val srcX = x * scaleFactor
                val srcY = y * scaleFactor
                val idx = srcY * width + srcX
                if (idx < yBuffer.capacity()) {
                    yBuffer.position(idx)
                    val gray = yBuffer.get().toInt() and 0xFF
                    if (gray < minVal) minVal = gray
                    if (gray > maxVal) maxVal = gray
                }
            }
        }
        
        val range = maxVal - minVal
        yBuffer.rewind()
        
        for (y in 0 until newHeight) {
            for (x in 0 until newWidth) {
                val srcX = x * scaleFactor
                val srcY = y * scaleFactor
                val idx = srcY * width + srcX
                if (idx < yBuffer.capacity()) {
                    yBuffer.position(idx)
                    val gray = yBuffer.get().toInt() and 0xFF
                    val stretched = if (range > 10) ((gray - minVal) * 255) / range else gray
                    val clamped = stretched.coerceIn(0, 255)
                    pixels[y * newWidth + x] = Color.rgb(clamped, clamped, clamped)
                }
            }
        }
        
        bitmap.setPixels(pixels, 0, newWidth, 0, 0, newWidth, newHeight)
        return bitmap
    }
    
    /**
     * 浠庡凡澶嶅埗鐨勭伆搴︽暟鎹寮哄浘鍍忥紙閬垮厤 imageProxy 鍏抽棴鍚庤闂棶棰橈級
     */
    fun enhanceFromLuminance(luminance: ByteArray, width: Int, height: Int, scaleFactor: Int): Bitmap {
        val newWidth = width / scaleFactor
        val newHeight = height / scaleFactor
        
        val bitmap = Bitmap.createBitmap(newWidth, newHeight, Bitmap.Config.ARGB_8888)
        val pixels = IntArray(newWidth * newHeight)
        
        // 鍗曟閬嶅巻锛氬悓鏃舵壘 min/max 骞跺～鍏呭儚绱?
        var minVal = 255
        var maxVal = 0
        val sampleValues = IntArray(newWidth * newHeight)
        
        for (y in 0 until newHeight) {
            for (x in 0 until newWidth) {
                val srcX = x * scaleFactor
                val srcY = y * scaleFactor
                val idx = srcY * width + srcX
                if (idx < luminance.size) {
                    val gray = luminance[idx].toInt() and 0xFF
                    sampleValues[y * newWidth + x] = gray
                    if (gray < minVal) minVal = gray
                    if (gray > maxVal) maxVal = gray
                }
            }
        }
        
        val range = maxVal - minVal
        
        // 搴旂敤瀵规瘮搴︽媺浼?
        for (i in sampleValues.indices) {
            val gray = sampleValues[i]
            val stretched = if (range > 10) ((gray - minVal) * 255) / range else gray
            val clamped = stretched.coerceIn(0, 255)
            pixels[i] = Color.rgb(clamped, clamped, clamped)
        }
        
        bitmap.setPixels(pixels, 0, newWidth, 0, 0, newWidth, newHeight)
        return bitmap
    }
    
    /**
     * 瑁佸壀鏉＄爜涓績鍖哄煙骞跺寮猴紙鍙繚鐣欐潯鐮佹牳蹇冮儴鍒嗭級
     * @param luminance 鍘熷鐏板害鏁版嵁
     * @param width 鍘熷鍥惧儚瀹藉害
     * @param height 鍘熷鍥惧儚楂樺害
     * @param left 妫€娴嬪埌鐨勬潯鐮佸乏杈圭晫
     * @param top 妫€娴嬪埌鐨勬潯鐮佷笂杈圭晫
     * @param right 妫€娴嬪埌鐨勬潯鐮佸彸杈圭晫
     * @param bottom 妫€娴嬪埌鐨勬潯鐮佷笅杈圭晫
     * @param targetHeightPx 鐩爣楂樺害锛堝儚绱狅級锛屼粠鏉＄爜涓績鍚戝唴瑁佸壀
     */
    fun enhanceCroppedRegion(
        luminance: ByteArray,
        width: Int,
        height: Int,
        left: Int,
        top: Int,
        right: Int,
        bottom: Int,
        targetHeightPx: Int = 40  // 绾?0dp锛屾墮2x瀵嗗害璁＄畻
    ): Bitmap {
        // ===== 绗竴姝ワ細瀵规暣涓浘鍍忚繘琛屽寮?=====
        // 鎵惧叏鍥剧殑 min/max
        var globalMin = 255
        var globalMax = 0
        for (i in luminance.indices) {
            val gray = luminance[i].toInt() and 0xFF
            if (gray < globalMin) globalMin = gray
            if (gray > globalMax) globalMax = gray
        }
        val globalRange = globalMax - globalMin
        
        // ===== 绗簩姝ワ細璁＄畻瑁佸壀鍖哄煙 =====
        // 璁＄畻鏉＄爜涓績
        val centerY = (top + bottom) / 2
        
        // 浠庝腑蹇冨悜鍐呰鍓紝鍙繚鐣欑洰鏍囬珮搴?
        val halfHeight = targetHeightPx / 2
        val cropTop = (centerY - halfHeight).coerceIn(0, height - 1)
        val cropBottom = (centerY + halfHeight).coerceIn(0, height)
        
        // 瀹藉害淇濇寔鏉＄爜瀹藉害锛堢◢寰唴缂?%锛?
        val barcodeWidth = right - left
        val inset = (barcodeWidth * 0.05).toInt()
        val cropLeft = (left + inset).coerceIn(0, width - 1)
        val cropRight = (right - inset).coerceIn(0, width)
        
        val cropWidth = cropRight - cropLeft
        val cropHeight = cropBottom - cropTop
        
        if (cropWidth <= 0 || cropHeight <= 0) {
            return Bitmap.createBitmap(1, 1, Bitmap.Config.ARGB_8888)
        }
        
        // ===== 绗笁姝ワ細瑁佸壀骞跺簲鐢ㄥ寮?=====
        val bitmap = Bitmap.createBitmap(cropWidth, cropHeight, Bitmap.Config.ARGB_8888)
        val pixels = IntArray(cropWidth * cropHeight)
        
        for (y in 0 until cropHeight) {
            for (x in 0 until cropWidth) {
                val srcX = cropLeft + x
                val srcY = cropTop + y
                val idx = srcY * width + srcX
                if (idx < luminance.size) {
                    val gray = luminance[idx].toInt() and 0xFF
                    // 浣跨敤鍏ㄥ浘鐨?min/max 杩涜瀵规瘮搴︽媺浼?
                    val stretched = if (globalRange > 10) ((gray - globalMin) * 255) / globalRange else gray
                    // 绠€鍗曠殑瀵规瘮搴﹀寮猴細灏嗕腑闂村€兼帹鍚戜袱绔?
                    val enhanced = if (stretched > 128) {
                        (stretched + (stretched - 128) / 2).coerceIn(0, 255)
                    } else {
                        (stretched - (128 - stretched) / 2).coerceIn(0, 255)
                    }
                    pixels[y * cropWidth + x] = Color.rgb(enhanced, enhanced, enhanced)
                }
            }
        }
        
        bitmap.setPixels(pixels, 0, cropWidth, 0, 0, cropWidth, cropHeight)
        return bitmap
    }
    
    /**
     * 骞虫壂澧炲己 - 鐩存帴瑁佸壀鎸囧畾鍖哄煙骞跺寮猴紙鐢ㄤ簬浠庝笂鍒颁笅閫愯鎵弿锛?
     * @param luminance 鍘熷鐏板害鏁版嵁
     * @param width 鍘熷鍥惧儚瀹藉害
     * @param height 鍘熷鍥惧儚楂樺害
     * @param left 瑁佸壀鍖哄煙宸﹁竟鐣?
     * @param top 瑁佸壀鍖哄煙涓婅竟鐣?
     * @param right 瑁佸壀鍖哄煙鍙宠竟鐣?
     * @param bottom 瑁佸壀鍖哄煙涓嬭竟鐣?
     */
    fun enhanceScanLine(
        luminance: ByteArray,
        width: Int,
        height: Int,
        left: Int,
        top: Int,
        right: Int,
        bottom: Int
    ): Bitmap {
        val cropLeft = left.coerceIn(0, width - 1)
        val cropTop = top.coerceIn(0, height - 1)
        val cropRight = right.coerceIn(0, width)
        val cropBottom = bottom.coerceIn(0, height)
        
        val cropWidth = cropRight - cropLeft
        val cropHeight = cropBottom - cropTop
        
        if (cropWidth <= 0 || cropHeight <= 0) {
            return Bitmap.createBitmap(1, 1, Bitmap.Config.ARGB_8888)
        }
        
        val bitmap = Bitmap.createBitmap(cropWidth, cropHeight, Bitmap.Config.ARGB_8888)
        val pixels = IntArray(cropWidth * cropHeight)
        
        // 鎵捐鍓尯鍩熺殑 min/max
        var minVal = 255
        var maxVal = 0
        val grayValues = IntArray(cropWidth * cropHeight)
        
        for (y in 0 until cropHeight) {
            for (x in 0 until cropWidth) {
                val srcX = cropLeft + x
                val srcY = cropTop + y
                val idx = srcY * width + srcX
                if (idx < luminance.size) {
                    val gray = luminance[idx].toInt() and 0xFF
                    grayValues[y * cropWidth + x] = gray
                    if (gray < minVal) minVal = gray
                    if (gray > maxVal) maxVal = gray
                }
            }
        }
        
        val range = maxVal - minVal
        
        // 搴旂敤寮哄姣斿害澧炲己
        for (i in grayValues.indices) {
            val gray = grayValues[i]
            val stretched = if (range > 10) ((gray - minVal) * 255) / range else gray
            // 鏇村己鐨勫姣斿害澧炲己
            val enhanced = if (stretched > 128) {
                (stretched + (stretched - 128) * 2 / 3).coerceIn(0, 255)
            } else {
                (stretched - (128 - stretched) * 2 / 3).coerceIn(0, 255)
            }
            pixels[i] = Color.rgb(enhanced, enhanced, enhanced)
        }
        
        bitmap.setPixels(pixels, 0, cropWidth, 0, 0, cropWidth, cropHeight)
        return bitmap
    }
}

/**
 * 瀵笲itmap搴旂敤3x3楂樻柉妯＄硦
 */
fun applyGaussianBlur3x3(bitmap: Bitmap): Bitmap {
    val width = bitmap.width
    val height = bitmap.height
    val pixels = IntArray(width * height)
    bitmap.getPixels(pixels, 0, width, 0, 0, width, height)
    
    // 3x3楂樻柉鏍?(sigma 鈮?0.85)
    val kernel = floatArrayOf(
        1/16f, 2/16f, 1/16f,
        2/16f, 4/16f, 2/16f,
        1/16f, 2/16f, 1/16f
    )
    
    val result = IntArray(width * height)
    
    for (y in 1 until height - 1) {
        for (x in 1 until width - 1) {
            var r = 0f; var g = 0f; var b = 0f
            var ki = 0
            for (ky in -1..1) {
                for (kx in -1..1) {
                    val pixel = pixels[(y + ky) * width + (x + kx)]
                    val weight = kernel[ki++]
                    r += Color.red(pixel) * weight
                    g += Color.green(pixel) * weight
                    b += Color.blue(pixel) * weight
                }
            }
            result[y * width + x] = Color.rgb(r.toInt().coerceIn(0, 255), g.toInt().coerceIn(0, 255), b.toInt().coerceIn(0, 255))
        }
    }
    
    // 澶嶅埗杈圭紭鍍忕礌
    for (x in 0 until width) {
        result[x] = pixels[x]
        result[(height - 1) * width + x] = pixels[(height - 1) * width + x]
    }
    for (y in 0 until height) {
        result[y * width] = pixels[y * width]
        result[y * width + width - 1] = pixels[y * width + width - 1]
    }
    
    val resultBitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
    resultBitmap.setPixels(result, 0, width, 0, 0, width, height)
    return resultBitmap
}

private fun yuv420888ToNv21(image: Image): ByteArray {
    val width = image.width
    val height = image.height
    val ySize = width * height
    val uvSize = width * height / 4
    val out = ByteArray(ySize + uvSize * 2)

    val yPlane = image.planes[0]
    val uPlane = image.planes[1]
    val vPlane = image.planes[2]

    val yBuffer = yPlane.buffer
    val uBuffer = uPlane.buffer
    val vBuffer = vPlane.buffer

    val yRowStride = yPlane.rowStride
    val yPixelStride = yPlane.pixelStride
    var outIndex = 0
    for (row in 0 until height) {
        val rowStart = row * yRowStride
        for (col in 0 until width) {
            out[outIndex++] = yBuffer.get(rowStart + col * yPixelStride)
        }
    }

    val uRowStride = uPlane.rowStride
    val uPixelStride = uPlane.pixelStride
    val vRowStride = vPlane.rowStride
    val vPixelStride = vPlane.pixelStride
    val chromaHeight = height / 2
    val chromaWidth = width / 2
    for (row in 0 until chromaHeight) {
        val uRowStart = row * uRowStride
        val vRowStart = row * vRowStride
        for (col in 0 until chromaWidth) {
            out[outIndex++] = vBuffer.get(vRowStart + col * vPixelStride)
            out[outIndex++] = uBuffer.get(uRowStart + col * uPixelStride)
        }
    }

    return out
}

@OptIn(ExperimentalGetImage::class)
@Composable
fun CameraPreview(
    modifier: Modifier = Modifier,
    onCameraReady: (Camera) -> Unit = {},
    onBarcodeDetected: (String) -> Unit,
    showBarcodeOverlay: Boolean = true,
    channel1ScanFrameInterval: Int = 3,
    channel2MinAreaScore: Double = 3.5,
    channel2MinAspectScore: Double = 28.0,
    channel2MinSolidityScore: Double = 10.0,
    channel2MinGradScore: Double = 8.0
) {
    val context = LocalContext.current
    val mainHandler = remember { Handler(Looper.getMainLooper()) }
    val analysisExecutor = remember { Executors.newSingleThreadExecutor() }
    val enhanceExecutor = remember { Executors.newSingleThreadExecutor() }
    val barcodeStabilizer = remember { BarcodeStabilizer() }
    val channel1ScanFrameIntervalState = rememberUpdatedState(channel1ScanFrameInterval.coerceAtLeast(1))
    val channel2MinAreaScoreState = rememberUpdatedState(channel2MinAreaScore.coerceIn(0.0, 100.0))
    val channel2MinAspectScoreState = rememberUpdatedState(channel2MinAspectScore.coerceIn(0.0, 100.0))
    val channel2MinSolidityScoreState = rememberUpdatedState(channel2MinSolidityScore.coerceIn(0.0, 100.0))
    val channel2MinGradScoreState = rememberUpdatedState(channel2MinGradScore.coerceIn(0.0, 100.0))
    
    // 浣跨敤 DetectedBarcode 绫诲瀷锛屼笌 BarcodeOverlay 鍏煎
    var channel1Barcodes by remember { mutableStateOf<List<DetectedBarcode>>(emptyList()) }
    var channel2Barcodes by remember { mutableStateOf<List<DetectedBarcode>>(emptyList()) }
    
    // 甯ц鏁板櫒鍜岄€氶亾2澶勭悊鏍囧織
    val frameCounter = remember { AtomicInteger(0) }
    val channel2Processing = remember { AtomicBoolean(false) }

    DisposableEffect(Unit) {
        onDispose {
            analysisExecutor.shutdown()
            enhanceExecutor.shutdown()
        }
    }
    
    Box(modifier = modifier) {
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
                    try {
                        val cameraProvider = cameraProviderFuture.get()

                        val preview = Preview.Builder().build()
                        preview.setSurfaceProvider(previewView.surfaceProvider)
                        
                        val scanner1 = BarcodeScanning.getClient()
                        val scanner2 = BarcodeScanning.getClient()

                        val analysis = ImageAnalysis.Builder()
                            .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                            .setTargetResolution(Size(1280, 720))
                            .build()

                        analysis.setAnalyzer(analysisExecutor) { imageProxy ->
                            val mediaImage = imageProxy.image
                            if (mediaImage == null) {
                                imageProxy.close()
                                return@setAnalyzer
                            }
                            
                            val imageWidth = mediaImage.width
                            val imageHeight = mediaImage.height
                            val rotationDegrees = imageProxy.imageInfo.rotationDegrees
                            val currentFrame = frameCounter.incrementAndGet()
                            
                            // 澶嶅埗YUV鏁版嵁鐢ㄤ簬閫氶亾2锛堝湪鍏抽棴imageProxy鍓嶏級
                            val nv21Data = yuv420888ToNv21(mediaImage)
                            
                            // ===== 閫氶亾1: scan source frame with configurable interval =====
                            if (currentFrame % channel1ScanFrameIntervalState.value == 0) {
                                val inputImage1 = InputImage.fromMediaImage(mediaImage, rotationDegrees)
                                
                                scanner1.process(inputImage1)
                                    .addOnSuccessListener { barcodes ->
                                        val detected = barcodes.mapNotNull { barcode ->
                                            barcode.boundingBox?.let { box ->
                                                DetectedBarcode(
                                                    left = box.left.toFloat(),
                                                    top = box.top.toFloat(),
                                                    right = box.right.toFloat(),
                                                    bottom = box.bottom.toFloat(),
                                                    rawValue = barcode.rawValue,
                                                    format = barcode.format,
                                                    imageWidth = imageWidth,
                                                    imageHeight = imageHeight,
                                                    rotationDegrees = rotationDegrees
                                                )
                                            }
                                        }
                                        
                                        mainHandler.post { channel1Barcodes = detected }
                                        
                                        val first = barcodes.firstOrNull()
                                        if (first?.rawValue != null) {
                                            Log.d("CameraPreview", "閫氶亾1(钃?璇嗗埆: ${first.rawValue}")
                                            mainHandler.post {
                                                barcodeStabilizer.stabilize(first.rawValue!!) {
                                                    onBarcodeDetected(it)
                                                }
                                            }
                                        }
                                    }
                                    .addOnCompleteListener {
                                        imageProxy.close()
                                    }
                            } else {
                                // 闈為€氶亾1甯э紝鐩存帴鍏抽棴
                                imageProxy.close()
                            }
                            
                            // ===== 閫氶亾2: 澶氬垎杈ㄧ巼澧炲己鎵弿锛堟瘡甯ч兘鎵ц锛屽叏閫熻繍琛岋級=====
                            if (channel2Processing.compareAndSet(false, true)) {
                                val channel2Frame = nv21Data.copyOf()
                                enhanceExecutor.execute {
                                    try {
                                        val frameOutput = RealtimeFrameCropEngine.processNv21Frame(
                                            nv21 = channel2Frame,
                                            width = imageWidth,
                                            height = imageHeight,
                                            rotationDegrees = rotationDegrees,
                                            config = RealtimeFrameCropEngine.FrameConfig(
                                                detectionConfig = NativeBarcodeDetector.DetectionConfig(
                                                    minAreaScore = channel2MinAreaScoreState.value,
                                                    minAspectScore = channel2MinAspectScoreState.value,
                                                    minSolidityScore = channel2MinSolidityScoreState.value,
                                                    minGradScore = channel2MinGradScoreState.value
                                                ),
                                                minProcessIntervalMs = 0L,
                                                maxOutputs = 8,
                                                cropPaddingPx = 18,
                                                enableStabilizer = true
                                            )
                                        )

                                        if (frameOutput == null) {
                                            mainHandler.post { channel2Barcodes = emptyList() }
                                            channel2Processing.set(false)
                                            return@execute
                                        }

                                        val candidates = frameOutput.detections.map { det ->
                                            DetectedBarcode(
                                                left = (det.boundingBox.left + frameOutput.roiLeft).toFloat(),
                                                top = (det.boundingBox.top + frameOutput.roiTop).toFloat(),
                                                right = (det.boundingBox.right + frameOutput.roiLeft).toFloat(),
                                                bottom = (det.boundingBox.bottom + frameOutput.roiTop).toFloat(),
                                                rawValue = null,
                                                format = 0,
                                                imageWidth = frameOutput.frameWidth,
                                                imageHeight = frameOutput.frameHeight,
                                                rotationDegrees = 0
                                            )
                                        }
                                        mainHandler.post { channel2Barcodes = candidates }

                                        if (frameOutput.crops.isEmpty()) {
                                            frameOutput.roiBitmap.recycle()
                                            channel2Processing.set(false)
                                            return@execute
                                        }

                                        val recognized = java.util.Collections.synchronizedList(
                                            mutableListOf<DetectedBarcode>()
                                        )

                                        val tasks = frameOutput.crops.map { crop ->
                                            val inputImage2 = InputImage.fromBitmap(crop.bitmap, 0)
                                            scanner2.process(inputImage2)
                                                .addOnSuccessListener { barcodes ->
                                                    barcodes.forEach { barcode ->
                                                        val rawValue = barcode.rawValue ?: return@forEach
                                                        recognized.add(
                                                            DetectedBarcode(
                                                                left = (crop.sourceBox.left + frameOutput.roiLeft).toFloat(),
                                                                top = (crop.sourceBox.top + frameOutput.roiTop).toFloat(),
                                                                right = (crop.sourceBox.right + frameOutput.roiLeft).toFloat(),
                                                                bottom = (crop.sourceBox.bottom + frameOutput.roiTop).toFloat(),
                                                                rawValue = rawValue,
                                                                format = barcode.format,
                                                                imageWidth = frameOutput.frameWidth,
                                                                imageHeight = frameOutput.frameHeight,
                                                                rotationDegrees = 0
                                                            )
                                                        )
                                                    }
                                                }
                                                .addOnCompleteListener {
                                                    crop.bitmap.recycle()
                                                }
                                        }

                                        Tasks.whenAllComplete(tasks).addOnCompleteListener {
                                            frameOutput.roiBitmap.recycle()

                                            val finalRecognized = recognized.distinctBy {
                                                "${it.rawValue}_${it.left.toInt()}_${it.top.toInt()}_${it.right.toInt()}_${it.bottom.toInt()}"
                                            }

                                            mainHandler.post {
                                                if (finalRecognized.isNotEmpty()) {
                                                    channel2Barcodes = finalRecognized
                                                    finalRecognized
                                                        .mapNotNull { it.rawValue }
                                                        .distinct()
                                                        .forEach { value ->
                                                            barcodeStabilizer.stabilize(value) { stable ->
                                                                onBarcodeDetected(stable)
                                                            }
                                                        }
                                                } else {
                                                    channel2Barcodes = emptyList()
                                                }
                                            }

                                            channel2Processing.set(false)
                                        }
                                    } catch (e: Exception) {
                                        Log.e("CameraPreview", "Channel2 processing failed: ${e.message}")
                                        mainHandler.post { channel2Barcodes = emptyList() }
                                        channel2Processing.set(false)
                                    }
                                }
                            }
                        }

                        cameraProvider.unbindAll()
                        val camera = cameraProvider.bindToLifecycle(
                            ctx as LifecycleOwner,
                            CameraSelector.DEFAULT_BACK_CAMERA,
                            preview,
                            analysis
                        )
                        onCameraReady(camera)
                    } catch (e: Exception) {
                        Log.e("CameraPreview", "鐩告満鍒濆鍖栧け璐? ${e.message}")
                    }
                }, ContextCompat.getMainExecutor(ctx))

                previewView
            },
            modifier = Modifier.fillMaxSize()
        )
        
        // 钂欑増閬僵灞傦紙鏄剧ず鎵弿鍖哄煙锛?
        ScanMaskOverlay(
            modifier = Modifier.fillMaxSize()
        )
        
        // 閫氶亾1 妫€娴嬫 (钃濊壊)
        if (showBarcodeOverlay) {
            BarcodeOverlay(
                detectedBarcodes = channel1Barcodes,
                modifier = Modifier.fillMaxSize(),
                boxColor = ComposeColor.Blue,
                cornerColor = ComposeColor.Cyan
            )
            
            // 閫氶亾2 妫€娴嬫 (绾㈣壊)
            BarcodeOverlay(
                detectedBarcodes = channel2Barcodes,
                modifier = Modifier.fillMaxSize(),
                boxColor = ComposeColor.Red,
                cornerColor = ComposeColor.Magenta
            )
        }
    }
}

// ==================== 鎵弿钂欑増閬僵灞?====================

@Composable
fun ScanMaskOverlay(
    modifier: Modifier = Modifier,
    maskColor: ComposeColor = ComposeColor.Black.copy(alpha = 0.5f)
) {
    // 钂欑増鍙傛暟锛氫笌閫氶亾2瑁佸壀鍙傛暟涓€鑷?
    val maskTopRatio = 0.40f
    val maskBottomRatio = 0.40f
    val maskLeftRatio = 0.20f
    val maskRightRatio = 0.20f
    
    androidx.compose.foundation.Canvas(modifier = modifier) {
        val canvasWidth = size.width
        val canvasHeight = size.height
        
        // 璁＄畻閫忔槑鍖哄煙锛堜腑闂存壂鎻忓尯鍩燂級
        val scanLeft = canvasWidth * maskLeftRatio
        val scanRight = canvasWidth * (1 - maskRightRatio)
        val scanTop = canvasHeight * maskTopRatio
        val scanBottom = canvasHeight * (1 - maskBottomRatio)
        
        // 缁樺埗涓婃柟閬僵
        drawRect(
            color = maskColor,
            topLeft = androidx.compose.ui.geometry.Offset(0f, 0f),
            size = androidx.compose.ui.geometry.Size(canvasWidth, scanTop)
        )
        
        // 缁樺埗涓嬫柟閬僵
        drawRect(
            color = maskColor,
            topLeft = androidx.compose.ui.geometry.Offset(0f, scanBottom),
            size = androidx.compose.ui.geometry.Size(canvasWidth, canvasHeight - scanBottom)
        )
        
        // 缁樺埗宸︿晶閬僵
        drawRect(
            color = maskColor,
            topLeft = androidx.compose.ui.geometry.Offset(0f, scanTop),
            size = androidx.compose.ui.geometry.Size(scanLeft, scanBottom - scanTop)
        )
        
        // 缁樺埗鍙充晶閬僵
        drawRect(
            color = maskColor,
            topLeft = androidx.compose.ui.geometry.Offset(scanRight, scanTop),
            size = androidx.compose.ui.geometry.Size(canvasWidth - scanRight, scanBottom - scanTop)
        )
        
        // 缁樺埗鎵弿鍖哄煙杈规
        drawRect(
            color = ComposeColor.White,
            topLeft = androidx.compose.ui.geometry.Offset(scanLeft, scanTop),
            size = androidx.compose.ui.geometry.Size(scanRight - scanLeft, scanBottom - scanTop),
            style = androidx.compose.ui.graphics.drawscope.Stroke(width = 2f)
        )
    }
}



