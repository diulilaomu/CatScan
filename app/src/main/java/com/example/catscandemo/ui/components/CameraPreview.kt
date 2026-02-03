package com.example.catscandemo.ui.components

import android.graphics.Bitmap
import android.graphics.Color
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
import com.google.android.gms.tasks.Tasks
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.common.InputImage
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger

/**
 * 条码稳定器类
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
 * 快速图像增强 - 对比度拉伸
 */
object FastImageEnhancer {
    
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
     * 从已复制的灰度数据增强图像（避免 imageProxy 关闭后访问问题）
     */
    fun enhanceFromLuminance(
        luminance: ByteArray,
        width: Int,
        height: Int,
        scaleFactor: Int,
        contrastBoost: Float = 1.0f
    ): Bitmap {
        val newWidth = width / scaleFactor
        val newHeight = height / scaleFactor
        
        val bitmap = Bitmap.createBitmap(newWidth, newHeight, Bitmap.Config.ARGB_8888)
        val pixels = IntArray(newWidth * newHeight)
        
        // 单次遍历：同时找 min/max 并填充像素
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
        
        // 应用对比度拉伸
        for (i in sampleValues.indices) {
            val gray = sampleValues[i]
            val stretched = if (range > 10) ((gray - minVal) * 255) / range else gray
            val clamped = stretched.coerceIn(0, 255)
            val boosted = (((clamped - 128) * contrastBoost) + 128).toInt().coerceIn(0, 255)
            pixels[i] = Color.rgb(boosted, boosted, boosted)
        }
        
        bitmap.setPixels(pixels, 0, newWidth, 0, 0, newWidth, newHeight)
        return bitmap
    }

    /**
     * 创建多层增强结果（用于双通道分层扫描）
     */
    fun createLayeredEnhancements(
        luminance: ByteArray,
        width: Int,
        height: Int,
        scaleFactor: Int = 4
    ): List<Bitmap> {
        // 从温和到强烈的对比度增强层
        return listOf(
            enhanceFromLuminance(luminance, width, height, scaleFactor, contrastBoost = 1.0f),
            enhanceFromLuminance(luminance, width, height, scaleFactor, contrastBoost = 1.5f),
            enhanceFromLuminance(luminance, width, height, scaleFactor, contrastBoost = 2.0f)
        )
    }
    
    /**
     * 裁剪条码中心区域并增强（只保留条码核心部分）
     * @param luminance 原始灰度数据
     * @param width 原始图像宽度
     * @param height 原始图像高度
     * @param left 检测到的条码左边界
     * @param top 检测到的条码上边界
     * @param right 检测到的条码右边界
     * @param bottom 检测到的条码下边界
     * @param targetHeightPx 目标高度（像素），从条码中心向内裁剪
     */
    fun enhanceCroppedRegion(
        luminance: ByteArray,
        width: Int,
        height: Int,
        left: Int,
        top: Int,
        right: Int,
        bottom: Int,
        targetHeightPx: Int = 40  // 约20dp，扙2x密度计算
    ): Bitmap {
        // ===== 第一步：对整个图像进行增强 =====
        // 找全图的 min/max
        var globalMin = 255
        var globalMax = 0
        for (i in luminance.indices) {
            val gray = luminance[i].toInt() and 0xFF
            if (gray < globalMin) globalMin = gray
            if (gray > globalMax) globalMax = gray
        }
        val globalRange = globalMax - globalMin
        
        // ===== 第二步：计算裁剪区域 =====
        // 计算条码中心
        val centerY = (top + bottom) / 2
        
        // 从中心向内裁剪，只保留目标高度
        val halfHeight = targetHeightPx / 2
        val cropTop = (centerY - halfHeight).coerceIn(0, height - 1)
        val cropBottom = (centerY + halfHeight).coerceIn(0, height)
        
        // 宽度保持条码宽度（稍微内缩5%）
        val barcodeWidth = right - left
        val inset = (barcodeWidth * 0.05).toInt()
        val cropLeft = (left + inset).coerceIn(0, width - 1)
        val cropRight = (right - inset).coerceIn(0, width)
        
        val cropWidth = cropRight - cropLeft
        val cropHeight = cropBottom - cropTop
        
        if (cropWidth <= 0 || cropHeight <= 0) {
            return Bitmap.createBitmap(1, 1, Bitmap.Config.ARGB_8888)
        }
        
        // ===== 第三步：裁剪并应用增强 =====
        val bitmap = Bitmap.createBitmap(cropWidth, cropHeight, Bitmap.Config.ARGB_8888)
        val pixels = IntArray(cropWidth * cropHeight)
        
        for (y in 0 until cropHeight) {
            for (x in 0 until cropWidth) {
                val srcX = cropLeft + x
                val srcY = cropTop + y
                val idx = srcY * width + srcX
                if (idx < luminance.size) {
                    val gray = luminance[idx].toInt() and 0xFF
                    // 使用全图的 min/max 进行对比度拉伸
                    val stretched = if (globalRange > 10) ((gray - globalMin) * 255) / globalRange else gray
                    // 简单的对比度增强：将中间值推向两端
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
     * 平扫增强 - 直接裁剪指定区域并增强（用于从上到下逐行扫描）
     * @param luminance 原始灰度数据
     * @param width 原始图像宽度
     * @param height 原始图像高度
     * @param left 裁剪区域左边界
     * @param top 裁剪区域上边界
     * @param right 裁剪区域右边界
     * @param bottom 裁剪区域下边界
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
        
        // 找裁剪区域的 min/max
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
        
        // 应用强对比度增强
        for (i in grayValues.indices) {
            val gray = grayValues[i]
            val stretched = if (range > 10) ((gray - minVal) * 255) / range else gray
            // 更强的对比度增强
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

@OptIn(ExperimentalGetImage::class)
@Composable
    fun CameraPreview(
        modifier: Modifier = Modifier,
        onCameraReady: (Camera) -> Unit = {},
        onBarcodeDetected: (String) -> Unit,
        showBarcodeOverlay: Boolean = true
    ) {
    val context = LocalContext.current
    val mainHandler = remember { Handler(Looper.getMainLooper()) }
    val analysisExecutor = remember { Executors.newSingleThreadExecutor() }
    val enhanceExecutor = remember { Executors.newSingleThreadExecutor() }
    val barcodeStabilizer = remember { BarcodeStabilizer() }
    
    // 使用 DetectedBarcode 类型，与 BarcodeOverlay 兼容
    var channel1Barcodes by remember { mutableStateOf<List<DetectedBarcode>>(emptyList()) }
    var channel2Barcodes by remember { mutableStateOf<List<DetectedBarcode>>(emptyList()) }
    
    // 帧计数器和通道2处理标志
    val frameCounter = remember { AtomicInteger(0) }
    val channel2Processing = remember { AtomicBoolean(false) }
    val layeredChannelProcessing = remember { AtomicBoolean(false) }

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
                            
                            // 复制灰度数据用于通道2（在关闭imageProxy前）
                            val yBuffer = mediaImage.planes[0].buffer
                            val luminanceData = ByteArray(yBuffer.remaining())
                            yBuffer.get(luminanceData)
                            yBuffer.rewind()
                            
                            // ===== 通道1: 原图扫描（每3帧执行一次）=====
                            if (currentFrame % 3 == 0) {
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
                                            Log.d("CameraPreview", "通道1(蓝)识别: ${first.rawValue}")
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
                                // 非通道1帧，直接关闭
                                imageProxy.close()
                            }
                            
                            // ===== 通道2: 增强扫描（每帧都执行，全速运行）=====
                            if (channel2Processing.compareAndSet(false, true)) {
                                enhanceExecutor.execute {
                                    var enhancedBitmap: Bitmap? = null
                                    try {
                                        val scaleFactor = 4
                                        enhancedBitmap = FastImageEnhancer.enhanceFromLuminance(
                                            luminanceData, imageWidth, imageHeight, scaleFactor
                                        )
                                        val inputImage2 = InputImage.fromBitmap(enhancedBitmap, rotationDegrees)
                                        
                                        scanner2.process(inputImage2)
                                            .addOnSuccessListener { barcodes ->
                                                val first = barcodes.firstOrNull()
                                                
                                                if (first?.rawValue != null) {
                                                    // 识别成功
                                                    val detected = barcodes.mapNotNull { barcode ->
                                                        barcode.boundingBox?.let { box ->
                                                            DetectedBarcode(
                                                                left = box.left.toFloat() * scaleFactor,
                                                                top = box.top.toFloat() * scaleFactor,
                                                                right = box.right.toFloat() * scaleFactor,
                                                                bottom = box.bottom.toFloat() * scaleFactor,
                                                                rawValue = barcode.rawValue,
                                                                format = barcode.format,
                                                                imageWidth = imageWidth,
                                                                imageHeight = imageHeight,
                                                                rotationDegrees = rotationDegrees
                                                            )
                                                        }
                                                    }
                                                    mainHandler.post { channel2Barcodes = detected }
                                                    
                                                    Log.d("CameraPreview", "通道2(红)识别: ${first.rawValue}")
                                                    mainHandler.post {
                                                        barcodeStabilizer.stabilize(first.rawValue!!) {
                                                            onBarcodeDetected(it)
                                                        }
                                                    }
                                                } else {
                                                    mainHandler.post { channel2Barcodes = emptyList() }
                                                }
                                            }
                                            .addOnCompleteListener {
                                                enhancedBitmap?.recycle()
                                                channel2Processing.set(false)
                                            }
                                    } catch (e: Exception) {
                                        enhancedBitmap?.recycle()
                                        channel2Processing.set(false)
                                    }
                                }
                            }

                            // ===== 通道2叠层: 多层增强扫描（针对低质量条码）=====
                            if (layeredChannelProcessing.compareAndSet(false, true)) {
                                enhanceExecutor.execute {
                                    val layers = FastImageEnhancer.createLayeredEnhancements(
                                        luminanceData, imageWidth, imageHeight, scaleFactor = 4
                                    )
                                    var capturedValue: String? = null
                                    try {
                                        for (layer in layers) {
                                            if (capturedValue != null) break
                                            val input = InputImage.fromBitmap(layer, rotationDegrees)
                                            val task = scanner2.process(input)
                                            // 同步等待简化控制流（ML Kit 任务为异步，这里阻塞到完成）
                                            val barcodes = try {
                                                Tasks.await(task)
                                            } catch (_: Exception) {
                                                emptyList<com.google.mlkit.vision.barcode.common.Barcode>()
                                            }
                                            val first = barcodes.firstOrNull()
                                            if (first?.rawValue != null) {
                                                capturedValue = first.rawValue
                                                val scale = 4f
                                                val detected = barcodes.mapNotNull { barcode ->
                                                    barcode.boundingBox?.let { box ->
                                                        DetectedBarcode(
                                                            left = box.left.toFloat() * scale,
                                                            top = box.top.toFloat() * scale,
                                                            right = box.right.toFloat() * scale,
                                                            bottom = box.bottom.toFloat() * scale,
                                                            rawValue = barcode.rawValue,
                                                            format = barcode.format,
                                                            imageWidth = imageWidth,
                                                            imageHeight = imageHeight,
                                                            rotationDegrees = rotationDegrees
                                                        )
                                                    }
                                                }
                                                mainHandler.post { channel2Barcodes = detected }
                                            }
                                        }
                                        
                                        capturedValue?.let { value ->
                                            Log.d("CameraPreview", "通道2叠层识别: $value")
                                            mainHandler.post {
                                                barcodeStabilizer.stabilize(value) {
                                                    onBarcodeDetected(it)
                                                }
                                            }
                                        }
                                    } finally {
                                        layers.forEach { it.recycle() }
                                        layeredChannelProcessing.set(false)
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
                        Log.e("CameraPreview", "相机初始化失败: ${e.message}")
                    }
                }, ContextCompat.getMainExecutor(ctx))

                previewView
            },
            modifier = Modifier.fillMaxSize()
        )
        
        // 通道1 检测框 (蓝色)
        if (showBarcodeOverlay) {
            BarcodeOverlay(
                detectedBarcodes = channel1Barcodes,
                modifier = Modifier.fillMaxSize(),
                boxColor = ComposeColor.Blue,
                cornerColor = ComposeColor.Cyan
            )
            
            // 通道2 检测框 (红色)
            BarcodeOverlay(
                detectedBarcodes = channel2Barcodes,
                modifier = Modifier.fillMaxSize(),
                boxColor = ComposeColor.Red,
                cornerColor = ComposeColor.Magenta
            )
        }
    }
}
