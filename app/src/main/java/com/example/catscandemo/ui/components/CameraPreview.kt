package com.example.catscandemo.ui.components

import android.media.Image
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.util.Size
import android.view.ViewGroup
import android.widget.LinearLayout
import androidx.annotation.OptIn
import androidx.camera.core.*
import androidx.camera.core.resolutionselector.ResolutionSelector
import androidx.camera.core.resolutionselector.ResolutionStrategy
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
import com.example.catscandemo.domain.model.CaptureKernel
import com.example.catscandemo.utils.NativeBarcodeDetector
import com.example.catscandemo.utils.OcrRegexMatcher
import com.example.catscandemo.utils.RealtimeFrameCropEngine
import com.google.android.gms.tasks.Tasks
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.TextRecognizer
import com.google.mlkit.vision.text.chinese.ChineseTextRecognizerOptions
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.RejectedExecutionException
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong

/**
 * 条码稳定器
 *
 * 连续 stableThreshold 帧读到同一文本才触发一次回调；触发后进入抑制名单，
 * 该码自身离开镜头超过 absenceResetMs 才允许再次触发。
 */
class BarcodeStabilizer(
    private val nowMs: () -> Long = { android.os.SystemClock.elapsedRealtime() },
    private val absenceResetMs: Long = 1_500L
) {
    private var stableText: String? = null
    private var stableCount = 0
    private val stableThreshold = 2
    private var suppressedText: String? = null

    // 被抑制码自身的最后可见时间：解除抑制只看它有没有离开镜头，
    // 不被画面里其他码/检测噪声刷新的全局时钟影响
    private var suppressedLastSeenAtMs = 0L
    private var lastSeenAtMs = 0L

    @Synchronized
    fun stabilize(text: String, onStable: (String) -> Unit) {
        val now = nowMs()

        // 抑制到期：被抑制的码自身超过 absenceResetMs 未出现才解除
        if (suppressedText != null && now - suppressedLastSeenAtMs > absenceResetMs) {
            suppressedText = null
            suppressedLastSeenAtMs = 0L
        }

        // 稳定计数状态机：任何码超过 absenceResetMs 未出现就重新计数
        if (lastSeenAtMs > 0L && now - lastSeenAtMs > absenceResetMs) {
            stableText = null
            stableCount = 0
        }
        lastSeenAtMs = now

        // 同一个码持续停留在镜头中时只触发一次；离开镜头后才允许再次识别。
        if (text == suppressedText) {
            suppressedLastSeenAtMs = now
            return
        }

        if (text == stableText) {
            stableCount++
        } else {
            stableText = text
            stableCount = 1
        }

        if (stableCount >= stableThreshold) {
            onStable(text)
            suppressedText = text
            suppressedLastSeenAtMs = now
            stableText = null
            stableCount = 0
        }
    }

    @Synchronized
    fun reset() {
        stableText = null
        stableCount = 0
        suppressedText = null
        suppressedLastSeenAtMs = 0L
        lastSeenAtMs = 0L
    }
}

/**
 * Image(YUV_420_888) → NV21 转换器，带可复用缓冲。
 *
 * pixelStride==1 的常见路径按行/整面批量拷贝（原逐字节 get 约百万次/帧，
 * 每 60ms 一次且跑在相机分析线程上，是明显的帧延迟热点）。
 *
 * 返回的 ByteArray 会被下一次 convert 复用覆盖，调用方不得跨帧持有：
 * 只有分析器在单线程 executor 上调用，且通道2的 channel2Processing
 * 标志保证同一时刻最多一帧在增强管线中，复用安全。
 */
private class Nv21Converter {
    private var nv21 = ByteArray(0)
    private var uTmp = ByteArray(0)
    private var vTmp = ByteArray(0)

    fun convert(image: Image): ByteArray {
        val width = image.width
        val height = image.height
        val ySize = width * height
        val chromaWidth = width / 2
        val chromaHeight = height / 2
        if (nv21.size != ySize + chromaWidth * chromaHeight * 2) {
            nv21 = ByteArray(ySize + chromaWidth * chromaHeight * 2)
        }
        val out = nv21

        val yPlane = image.planes[0]
        val uPlane = image.planes[1]
        val vPlane = image.planes[2]

        // ===== Y 平面 =====
        val yBuffer = yPlane.buffer
        if (yPlane.pixelStride == 1) {
            if (yPlane.rowStride == width) {
                yBuffer.position(0)
                yBuffer.get(out, 0, ySize)
            } else {
                var outIndex = 0
                for (row in 0 until height) {
                    yBuffer.position(row * yPlane.rowStride)
                    yBuffer.get(out, outIndex, width)
                    outIndex += width
                }
            }
        } else {
            val rowStride = yPlane.rowStride
            val pixelStride = yPlane.pixelStride
            var outIndex = 0
            for (row in 0 until height) {
                val rowStart = row * rowStride
                for (col in 0 until width) {
                    out[outIndex++] = yBuffer.get(rowStart + col * pixelStride)
                }
            }
        }

        // ===== UV 平面：NV21 要求 VU 交错 =====
        // 先整行批量取出到数组，再在数组层交错，避免逐字节跨 JNI 读取
        val uBuffer = uPlane.buffer
        val vBuffer = vPlane.buffer
        if (uPlane.pixelStride == 1 && vPlane.pixelStride == 1) {
            if (uTmp.size < chromaWidth) uTmp = ByteArray(chromaWidth)
            if (vTmp.size < chromaWidth) vTmp = ByteArray(chromaWidth)
            var outIndex = ySize
            for (row in 0 until chromaHeight) {
                vBuffer.position(row * vPlane.rowStride)
                vBuffer.get(vTmp, 0, chromaWidth)
                uBuffer.position(row * uPlane.rowStride)
                uBuffer.get(uTmp, 0, chromaWidth)
                for (col in 0 until chromaWidth) {
                    out[outIndex++] = vTmp[col]
                    out[outIndex++] = uTmp[col]
                }
            }
        } else {
            val uRowStride = uPlane.rowStride
            val uPixelStride = uPlane.pixelStride
            val vRowStride = vPlane.rowStride
            val vPixelStride = vPlane.pixelStride
            var outIndex = ySize
            for (row in 0 until chromaHeight) {
                val uRowStart = row * uRowStride
                val vRowStart = row * vRowStride
                for (col in 0 until chromaWidth) {
                    out[outIndex++] = vBuffer.get(vRowStart + col * vPixelStride)
                    out[outIndex++] = uBuffer.get(uRowStart + col * uPixelStride)
                }
            }
        }
        return out
    }
}

/**
 * overlay 更新签名：坐标量化到 4px + 码值，内容没变就不触发重组。
 */
private fun List<DetectedBarcode>.overlaySignature(): String = buildString {
    for (b in this@overlaySignature) {
        append(b.left.toInt() shr 2).append(',')
        append(b.top.toInt() shr 2).append(',')
        append(b.right.toInt() shr 2).append(',')
        append(b.bottom.toInt() shr 2).append(',')
        append(b.rawValue ?: "").append(';')
    }
}

/**
 * 持有相机相关的可释放资源，供 Compose 销毁时统一释放。
 * factory 只执行一次，引用写在这里 onDispose 才能拿到。
 */
private class CameraHandle {
    @Volatile
    var released = false
        private set

    var cameraProvider: ProcessCameraProvider? = null
    var analysis: ImageAnalysis? = null

    // ML Kit 的 client 进程内共享底层模型，两通道共用一个 client 即可，
    // 双 client 只多一份 Java 侧开销与释放管理
    var scanner: com.google.mlkit.vision.barcode.BarcodeScanner? = null

    // 文字识别内核（内置中文+拉丁模型）：client 创建开销极小，模型在首次
    // process 时才加载；与 barcode scanner 并存，按帧读取当前内核选择用哪个
    var textRecognizer: TextRecognizer? = null

    fun release() {
        released = true
        try {
            analysis?.clearAnalyzer()
        } catch (_: Exception) {
        }
        try {
            cameraProvider?.unbindAll()
        } catch (_: Exception) {
        }
        try {
            scanner?.close()
        } catch (_: Exception) {
        }
        try {
            textRecognizer?.close()
        } catch (_: Exception) {
        }
        cameraProvider = null
        analysis = null
        scanner = null
        textRecognizer = null
    }
}

// ===== 文字识别内核参数 =====
// 全帧 OCR 派发节流：内置中文模型单帧识别约几十到一百多毫秒，
// 200ms（≈5fps）够用且显著低于相机帧率，配合在途标志不排队堆积
private const val OCR_FRAME_INTERVAL_MS = 200L

// 横线判定带宽：行中心到画面竖直中心的距离不超过画面高度的该比例才算"对准横线"
private const val OCR_LINE_BAND_RATIO = 0.10f

/** 横线带内的一行文字：距横线距离 + 原文 + 正则命中片段 */
private class OcrNearLine(val distanceToLine: Float, val text: String, val matches: List<String>)

/**
 * 组件销毁瞬间 executor 可能已 shutdown，此时提交任务会抛
 * RejectedExecutionException 且发生在 CameraX 回调里无人捕获，直接崩溃。
 */
private inline fun executeUnlessShutdown(
    executor: ExecutorService,
    crossinline onRejected: () -> Unit,
    crossinline action: () -> Unit
) {
    try {
        executor.execute { action() }
    } catch (e: RejectedExecutionException) {
        onRejected()
    }
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
    channel2MinGradScore: Double = 8.0,
    captureKernel: CaptureKernel = CaptureKernel.QR,
    ocrRegex: String = "",
    onOcrLiveText: (String) -> Unit = {}
) {
    val context = LocalContext.current
    val mainHandler = remember { Handler(Looper.getMainLooper()) }
    val analysisExecutor = remember { Executors.newSingleThreadExecutor() }
    val enhanceExecutor = remember { Executors.newSingleThreadExecutor() }
    val barcodeStabilizer = remember { BarcodeStabilizer() }
    val nv21Converter = remember { Nv21Converter() }
    val channel1ScanFrameIntervalState = rememberUpdatedState(channel1ScanFrameInterval.coerceAtLeast(1))
    val channel2MinAreaScoreState = rememberUpdatedState(channel2MinAreaScore.coerceIn(0.0, 100.0))
    val channel2MinAspectScoreState = rememberUpdatedState(channel2MinAspectScore.coerceIn(0.0, 100.0))
    val channel2MinSolidityScoreState = rememberUpdatedState(channel2MinSolidityScore.coerceIn(0.0, 100.0))
    val channel2MinGradScoreState = rememberUpdatedState(channel2MinGradScore.coerceIn(0.0, 100.0))
    // 内核/正则/回调都经 rememberUpdatedState，分析器闭包逐帧读到最新值
    val captureKernelState = rememberUpdatedState(captureKernel)
    val ocrRegexState = rememberUpdatedState(ocrRegex)
    val onBarcodeDetectedState = rememberUpdatedState(onBarcodeDetected)
    val onOcrLiveTextState = rememberUpdatedState(onOcrLiveText)

    var channel1Barcodes by remember { mutableStateOf<List<DetectedBarcode>>(emptyList()) }
    var channel2Barcodes by remember { mutableStateOf<List<DetectedBarcode>>(emptyList()) }

    // 帧计数器与通道2节流标志
    val frameCounter = remember { AtomicInteger(0) }
    val channel2Processing = remember { AtomicBoolean(false) }
    val lastChannel2FrameAt = remember { AtomicLong(0L) }

    // OCR 内核节流：时间间隔 + 在途标志（识别完成前不派发下一帧）
    val ocrInFlight = remember { AtomicBoolean(false) }
    val lastOcrFrameAt = remember { AtomicLong(0L) }

    // 切换采集内核时清掉稳定器状态，避免上一内核的抑制/计数串到新内核
    LaunchedEffect(captureKernel) {
        barcodeStabilizer.reset()
    }

    // overlay 去抖：仅在主线程读写。0=通道1，1=通道2
    val lastOverlaySignatures = remember { arrayOfNulls<String>(2) }

    val cameraHandle = remember { CameraHandle() }

    DisposableEffect(Unit) {
        onDispose {
            // 先停分析器再关线程池，保证 analyzer 不会再往已关闭的 executor 投帧
            cameraHandle.release()
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
                        // 竞态兜底：权限页快速切换时 onDispose 可能先于本回调执行，
                        // 此时不再绑定，避免组件销毁后又挂上相机
                        if (cameraHandle.released) {
                            Log.w("CameraPreview", "preview already disposed, skip camera binding")
                            return@addListener
                        }

                        val cameraProvider = cameraProviderFuture.get()
                        cameraHandle.cameraProvider = cameraProvider

                        val preview = Preview.Builder().build()
                        preview.setSurfaceProvider(previewView.surfaceProvider)

                        val scanner = BarcodeScanning.getClient()
                        cameraHandle.scanner = scanner

                        val textRecognizer = TextRecognition.getClient(ChineseTextRecognizerOptions.Builder().build())
                        cameraHandle.textRecognizer = textRecognizer

                        val analysis = ImageAnalysis.Builder()
                            .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                            .setResolutionSelector(
                                ResolutionSelector.Builder()
                                    .setResolutionStrategy(
                                        ResolutionStrategy(
                                            Size(1280, 720),
                                            ResolutionStrategy.FALLBACK_RULE_CLOSEST_HIGHER_THEN_LOWER
                                        )
                                    )
                                    .build()
                            )
                            .build()
                        cameraHandle.analysis = analysis

                        analysis.setAnalyzer(analysisExecutor) { imageProxy ->
                            // imageProxy 必须有且只有一次 close：移交给 scanner 完成回调的帧由回调负责关闭，
                            // 其余情况（无 mediaImage / 跳帧 / 任何同步异常）统一在 finally 关闭。
                            // KEEP_ONLY_LATEST 下漏关一帧，后续帧不再送达，扫码会静默冻结。

                            // ===== 文字识别内核：整帧 OCR + 横线带内提取；二维码两通道全部跳过 =====
                            // 独立于下方二维码 try/finally（自带唯一的关帧路径），避免外层 finally 二次 close
                            if (captureKernelState.value == CaptureKernel.OCR) {
                                var ocrClaimed = false
                                var ownedByOcr = false
                                try {
                                    val mediaImage = imageProxy.image
                                    if (mediaImage != null) {
                                        val recognizer = cameraHandle.textRecognizer
                                        val now = android.os.SystemClock.elapsedRealtime()
                                        if (recognizer != null &&
                                            now - lastOcrFrameAt.get() >= OCR_FRAME_INTERVAL_MS &&
                                            ocrInFlight.compareAndSet(false, true)
                                        ) {
                                            ocrClaimed = true
                                            lastOcrFrameAt.set(now)
                                            val rotationDegrees = imageProxy.imageInfo.rotationDegrees
                                            val ocrImage = InputImage.fromMediaImage(mediaImage, rotationDegrees)
                                            recognizer.process(ocrImage)
                                                .addOnSuccessListener { result ->
                                                    handleOcrFrameText(
                                                        result = result,
                                                        rotationDegrees = rotationDegrees,
                                                        imageWidth = mediaImage.width,
                                                        imageHeight = mediaImage.height,
                                                        ocrRegex = ocrRegexState.value,
                                                        barcodeStabilizer = barcodeStabilizer,
                                                        onOcrLiveText = onOcrLiveTextState.value,
                                                        onBarcodeDetected = onBarcodeDetectedState.value
                                                    )
                                                }
                                                .addOnCompleteListener {
                                                    ocrInFlight.set(false)
                                                    imageProxy.close()
                                                }
                                            ownedByOcr = true
                                        }
                                    }
                                } catch (e: Exception) {
                                    Log.e("CameraPreview", "OCR 帧分析异常: ${e.message}")
                                } finally {
                                    // 认领后未移交（recognizer 缺失/同步异常）：归还标志；没移交就自己关帧
                                    if (ocrClaimed && !ownedByOcr) {
                                        ocrInFlight.set(false)
                                    }
                                    if (!ownedByOcr) {
                                        imageProxy.close()
                                    }
                                }
                                return@setAnalyzer
                            }

                            var ownedByScanner = false
                            var channel2Claimed = false
                            var channel2Dispatched = false
                            try {
                                val mediaImage = imageProxy.image
                                if (mediaImage == null) return@setAnalyzer

                                val imageWidth = mediaImage.width
                                val imageHeight = mediaImage.height
                                val rotationDegrees = imageProxy.imageInfo.rotationDegrees

                                val currentFrame = frameCounter.incrementAndGet()

                                // 仅在增强通道空闲且到达采样间隔时复制帧，避免无谓的整帧拷贝
                                val now = android.os.SystemClock.elapsedRealtime()
                                val shouldProcessChannel2 =
                                    now - lastChannel2FrameAt.get() >= 60L &&
                                        channel2Processing.compareAndSet(false, true)
                                val channel2Frame = if (shouldProcessChannel2) {
                                    channel2Claimed = true
                                    lastChannel2FrameAt.set(now)
                                    nv21Converter.convert(mediaImage)
                                } else {
                                    null
                                }

                                // ===== 通道1: ML Kit 全帧直扫（按配置间隔采样） =====
                                if (currentFrame % channel1ScanFrameIntervalState.value == 0) {
                                    val inputImage = InputImage.fromMediaImage(mediaImage, rotationDegrees)
                                    scanner.process(inputImage)
                                        .addOnSuccessListener { barcodes ->
                                            // ML Kit 返回的框已在“转正后”坐标系，宽高需对调为转正后尺寸，
                                            // overlay 直接按转正坐标映射，不再携带旋转角
                                            val upright = rotationDegrees == 90 || rotationDegrees == 270
                                            val uprightWidth = if (upright) imageHeight else imageWidth
                                            val uprightHeight = if (upright) imageWidth else imageHeight
                                            val detected = barcodes.mapNotNull { barcode ->
                                                barcode.boundingBox?.let { box ->
                                                    DetectedBarcode(
                                                        left = box.left.toFloat(),
                                                        top = box.top.toFloat(),
                                                        right = box.right.toFloat(),
                                                        bottom = box.bottom.toFloat(),
                                                        rawValue = barcode.rawValue,
                                                        format = barcode.format,
                                                        imageWidth = uprightWidth,
                                                        imageHeight = uprightHeight
                                                    )
                                                }
                                            }
                                            mainHandler.post {
                                                val sig = detected.overlaySignature()
                                                if (sig != lastOverlaySignatures[0]) {
                                                    lastOverlaySignatures[0] = sig
                                                    channel1Barcodes = detected
                                                }
                                            }
                                            val first = barcodes.firstOrNull()
                                            if (first?.rawValue != null) {
                                                Log.d("CameraPreview", "通道1识别: ${first.rawValue}")
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
                                    ownedByScanner = true
                                }

                                // ===== 通道2: OpenCV 候选区裁剪 + ML Kit 二次识别 =====
                                // 独立线程执行，只使用已复制的帧数据，不依赖 imageProxy 生命周期
                                channel2Frame?.let { frame ->
                                    executeUnlessShutdown(enhanceExecutor, { channel2Processing.set(false) }) {
                                        try {
                                            val frameOutput = RealtimeFrameCropEngine.processNv21Frame(
                                                nv21 = frame,
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
                                                    minProcessIntervalMs = 60L,
                                                    maxOutputs = 8,
                                                    cropPaddingPx = 18,
                                                    enableStabilizer = true
                                                )
                                            )

                                            if (frameOutput == null) {
                                                mainHandler.post {
                                                    if (lastOverlaySignatures[1] != null) {
                                                        lastOverlaySignatures[1] = null
                                                        channel2Barcodes = emptyList()
                                                    }
                                                }
                                                channel2Processing.set(false)
                                                return@executeUnlessShutdown
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
                                                    imageHeight = frameOutput.frameHeight
                                                )
                                            }
                                            mainHandler.post {
                                                val sig = candidates.overlaySignature()
                                                if (sig != lastOverlaySignatures[1]) {
                                                    lastOverlaySignatures[1] = sig
                                                    channel2Barcodes = candidates
                                                }
                                            }

                                            if (frameOutput.crops.isEmpty()) {
                                                frameOutput.roiBitmap.recycle()
                                                channel2Processing.set(false)
                                                return@executeUnlessShutdown
                                            }

                                            val recognized = java.util.Collections.synchronizedList(
                                                mutableListOf<DetectedBarcode>()
                                            )
                                            val tasks = frameOutput.crops.map { crop ->
                                                val inputImage2 = InputImage.fromBitmap(crop.bitmap, 0)
                                                scanner.process(inputImage2)
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
                                                                    imageHeight = frameOutput.frameHeight
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
                                                        val sig = finalRecognized.overlaySignature()
                                                        if (sig != lastOverlaySignatures[1]) {
                                                            lastOverlaySignatures[1] = sig
                                                            channel2Barcodes = finalRecognized
                                                        }
                                                        finalRecognized
                                                            .mapNotNull { it.rawValue }
                                                            .distinct()
                                                            .forEach { value ->
                                                                barcodeStabilizer.stabilize(value) { stable ->
                                                                    onBarcodeDetected(stable)
                                                                }
                                                            }
                                                    } else if (lastOverlaySignatures[1] != null) {
                                                        lastOverlaySignatures[1] = null
                                                        channel2Barcodes = emptyList()
                                                    }
                                                }

                                                channel2Processing.set(false)
                                            }
                                        } catch (e: Exception) {
                                            Log.e("CameraPreview", "Channel2 processing failed: ${e.message}")
                                            mainHandler.post {
                                                if (lastOverlaySignatures[1] != null) {
                                                    lastOverlaySignatures[1] = null
                                                    channel2Barcodes = emptyList()
                                                }
                                            }
                                            channel2Processing.set(false)
                                        }
                                    }
                                }
                                channel2Dispatched = true
                            } catch (e: Exception) {
                                Log.e("CameraPreview", "帧分析异常: ${e.message}")
                                // 通道2已认领但尚未派发时释放标志，避免增强通道永久停摆
                                if (channel2Claimed && !channel2Dispatched) {
                                    channel2Processing.set(false)
                                }
                            } finally {
                                if (!ownedByScanner) {
                                    imageProxy.close()
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

        if (captureKernel == CaptureKernel.QR) {
            // 扫描蒙版遮罩层（显示扫描区域）
            ScanMaskOverlay(
                modifier = Modifier.fillMaxSize()
            )

            if (showBarcodeOverlay) {
                // 通道1 检测框（蓝色）
                BarcodeOverlay(
                    detectedBarcodes = channel1Barcodes,
                    modifier = Modifier.fillMaxSize(),
                    boxColor = ComposeColor.Blue,
                    cornerColor = ComposeColor.Cyan
                )

                // 通道2 检测框（红色）
                BarcodeOverlay(
                    detectedBarcodes = channel2Barcodes,
                    modifier = Modifier.fillMaxSize(),
                    boxColor = ComposeColor.Red,
                    cornerColor = ComposeColor.Magenta
                )
            }
        } else {
            // 文字识别内核：识别区域从方框改为中央横线
            OcrLineOverlay(
                modifier = Modifier.fillMaxSize()
            )
        }
    }
}

/**
 * OCR 帧结果处理（ML Kit 回调默认在主线程）：
 * 1. 取横线带（画面竖直中心 ±10%）内的文字行；
 * 2. 离横线最近的一行原文上报为实时文字（识别区底部展示）；
 * 3. 带内命中的行里挑最靠近横线的一行，其第一个正则命中片段经稳定器录入
 *    （与二维码同一防重复语义：同内容触发一次，离开镜头约 1.5s 后可再触发）。
 */
private fun handleOcrFrameText(
    result: com.google.mlkit.vision.text.Text,
    rotationDegrees: Int,
    imageWidth: Int,
    imageHeight: Int,
    ocrRegex: String,
    barcodeStabilizer: BarcodeStabilizer,
    onOcrLiveText: (String) -> Unit,
    onBarcodeDetected: (String) -> Unit
) {
    val upright = rotationDegrees == 90 || rotationDegrees == 270
    val uprightHeight = (if (upright) imageWidth else imageHeight).toFloat()
    val lineCenterY = uprightHeight / 2f
    val bandHalf = uprightHeight * OCR_LINE_BAND_RATIO

    val nearLines = ArrayList<OcrNearLine>(8)
    result.textBlocks.forEach { block ->
        block.lines.forEach { line ->
            val box = line.boundingBox ?: return@forEach
            val text = line.text.trim()
            if (text.isEmpty()) return@forEach
            // ML Kit 的 boundingBox 已是转正后坐标，与条码通道同一约定
            val centerY = (box.top + box.bottom) / 2f
            val distance = kotlin.math.abs(centerY - lineCenterY)
            if (distance <= bandHalf) {
                nearLines.add(
                    OcrNearLine(distance, text, OcrRegexMatcher.findAllMatches(text, ocrRegex))
                )
            }
        }
    }

    nearLines.minByOrNull { it.distanceToLine }?.let { onOcrLiveText(it.text) }

    val best = nearLines.filter { it.matches.isNotEmpty() }.minByOrNull { it.distanceToLine }
    if (best != null) {
        barcodeStabilizer.stabilize(best.matches.first()) { onBarcodeDetected(it) }
    }
}

// ==================== 扫描蒙版遮罩层 ====================

@Composable
fun ScanMaskOverlay(
    modifier: Modifier = Modifier,
    maskColor: ComposeColor = ComposeColor.Black.copy(alpha = 0.5f),
    cornerColor: ComposeColor = ComposeColor(0xFF2196F3)
) {
    // 与 RealtimeFrameCropEngine 的 ROI 比例保持一致
    val maskTopRatio = 0.40f
    val maskBottomRatio = 0.40f
    val maskLeftRatio = 0.20f
    val maskRightRatio = 0.20f

    androidx.compose.foundation.Canvas(modifier = modifier) {
        val canvasWidth = size.width
        val canvasHeight = size.height

        // 透明扫描区
        val scanLeft = canvasWidth * maskLeftRatio
        val scanRight = canvasWidth * (1 - maskRightRatio)
        val scanTop = canvasHeight * maskTopRatio
        val scanBottom = canvasHeight * (1 - maskBottomRatio)
        val scanWidth = scanRight - scanLeft
        val scanHeight = scanBottom - scanTop
        val scanCenterX = (scanLeft + scanRight) / 2f
        val scanCenterY = (scanTop + scanBottom) / 2f
        val cornerLength = minOf(scanWidth, scanHeight) * 0.13f

        // 上/下/左/右遮罩
        drawRect(
            color = maskColor,
            topLeft = androidx.compose.ui.geometry.Offset(0f, 0f),
            size = androidx.compose.ui.geometry.Size(canvasWidth, scanTop)
        )
        drawRect(
            color = maskColor,
            topLeft = androidx.compose.ui.geometry.Offset(0f, scanBottom),
            size = androidx.compose.ui.geometry.Size(canvasWidth, canvasHeight - scanBottom)
        )
        drawRect(
            color = maskColor,
            topLeft = androidx.compose.ui.geometry.Offset(0f, scanTop),
            size = androidx.compose.ui.geometry.Size(scanLeft, scanBottom - scanTop)
        )
        drawRect(
            color = maskColor,
            topLeft = androidx.compose.ui.geometry.Offset(scanRight, scanTop),
            size = androidx.compose.ui.geometry.Size(canvasWidth - scanRight, scanBottom - scanTop)
        )

        // 扫描区描边
        drawRoundRect(
            color = ComposeColor.White.copy(alpha = 0.65f),
            topLeft = androidx.compose.ui.geometry.Offset(scanLeft, scanTop),
            size = androidx.compose.ui.geometry.Size(scanWidth, scanHeight),
            cornerRadius = androidx.compose.ui.geometry.CornerRadius(16f, 16f),
            style = androidx.compose.ui.graphics.drawscope.Stroke(width = 2.2f)
        )

        // 四角高亮
        val cornerStroke = androidx.compose.ui.graphics.drawscope.Stroke(width = 4.2f)
        drawLine(
            color = cornerColor.copy(alpha = 0.9f),
            start = androidx.compose.ui.geometry.Offset(scanLeft, scanTop + cornerLength),
            end = androidx.compose.ui.geometry.Offset(scanLeft, scanTop),
            strokeWidth = cornerStroke.width
        )
        drawLine(
            color = cornerColor.copy(alpha = 0.9f),
            start = androidx.compose.ui.geometry.Offset(scanLeft, scanTop),
            end = androidx.compose.ui.geometry.Offset(scanLeft + cornerLength, scanTop),
            strokeWidth = cornerStroke.width
        )
        drawLine(
            color = cornerColor.copy(alpha = 0.9f),
            start = androidx.compose.ui.geometry.Offset(scanRight - cornerLength, scanTop),
            end = androidx.compose.ui.geometry.Offset(scanRight, scanTop),
            strokeWidth = cornerStroke.width
        )
        drawLine(
            color = cornerColor.copy(alpha = 0.9f),
            start = androidx.compose.ui.geometry.Offset(scanRight, scanTop),
            end = androidx.compose.ui.geometry.Offset(scanRight, scanTop + cornerLength),
            strokeWidth = cornerStroke.width
        )
        drawLine(
            color = cornerColor.copy(alpha = 0.9f),
            start = androidx.compose.ui.geometry.Offset(scanRight, scanBottom - cornerLength),
            end = androidx.compose.ui.geometry.Offset(scanRight, scanBottom),
            strokeWidth = cornerStroke.width
        )
        drawLine(
            color = cornerColor.copy(alpha = 0.9f),
            start = androidx.compose.ui.geometry.Offset(scanRight - cornerLength, scanBottom),
            end = androidx.compose.ui.geometry.Offset(scanRight, scanBottom),
            strokeWidth = cornerStroke.width
        )
        drawLine(
            color = cornerColor.copy(alpha = 0.9f),
            start = androidx.compose.ui.geometry.Offset(scanLeft + cornerLength, scanBottom),
            end = androidx.compose.ui.geometry.Offset(scanLeft, scanBottom),
            strokeWidth = cornerStroke.width
        )
        drawLine(
            color = cornerColor.copy(alpha = 0.9f),
            start = androidx.compose.ui.geometry.Offset(scanLeft, scanBottom),
            end = androidx.compose.ui.geometry.Offset(scanLeft, scanBottom - cornerLength),
            strokeWidth = cornerStroke.width
        )

        // 中央准星
        val crossSize = minOf(scanWidth, scanHeight) * 0.08f
        drawLine(
            color = ComposeColor.White.copy(alpha = 0.85f),
            start = androidx.compose.ui.geometry.Offset(scanCenterX - crossSize, scanCenterY),
            end = androidx.compose.ui.geometry.Offset(scanCenterX + crossSize, scanCenterY),
            strokeWidth = 2.2f
        )
        drawLine(
            color = ComposeColor.White.copy(alpha = 0.85f),
            start = androidx.compose.ui.geometry.Offset(scanCenterX, scanCenterY - crossSize),
            end = androidx.compose.ui.geometry.Offset(scanCenterX, scanCenterY + crossSize),
            strokeWidth = 2.2f
        )
        drawCircle(
            color = cornerColor.copy(alpha = 0.7f),
            radius = 4.5f,
            center = androidx.compose.ui.geometry.Offset(scanCenterX, scanCenterY)
        )
    }
}

// ==================== 文字识别横线遮罩层 ====================

/**
 * OCR 内核识别区：中央横线（对准哪行录哪行）。
 *
 * 上下半透明压暗 + 中央亮横线；横线带宽与 OCR_LINE_BAND_RATIO 一致
 * （画面竖直中心 ±10%），行中心落在这个带内才算"对准横线"。
 */
@Composable
fun OcrLineOverlay(
    modifier: Modifier = Modifier,
    maskColor: ComposeColor = ComposeColor.Black.copy(alpha = 0.5f),
    lineColor: ComposeColor = ComposeColor(0xFF2196F3)
) {
    androidx.compose.foundation.Canvas(modifier = modifier) {
        val canvasWidth = size.width
        val canvasHeight = size.height
        val centerY = canvasHeight / 2f
        val bandHalf = canvasHeight * OCR_LINE_BAND_RATIO
        val bandTop = centerY - bandHalf
        val bandBottom = centerY + bandHalf

        // 上/下遮罩（横线带保持透亮）
        drawRect(
            color = maskColor,
            topLeft = androidx.compose.ui.geometry.Offset(0f, 0f),
            size = androidx.compose.ui.geometry.Size(canvasWidth, bandTop)
        )
        drawRect(
            color = maskColor,
            topLeft = androidx.compose.ui.geometry.Offset(0f, bandBottom),
            size = androidx.compose.ui.geometry.Size(canvasWidth, canvasHeight - bandBottom)
        )

        // 横线带上下边缘细线（提示有效判定范围）
        drawLine(
            color = ComposeColor.White.copy(alpha = 0.35f),
            start = androidx.compose.ui.geometry.Offset(canvasWidth * 0.08f, bandTop),
            end = androidx.compose.ui.geometry.Offset(canvasWidth * 0.92f, bandTop),
            strokeWidth = 1.2f
        )
        drawLine(
            color = ComposeColor.White.copy(alpha = 0.35f),
            start = androidx.compose.ui.geometry.Offset(canvasWidth * 0.08f, bandBottom),
            end = androidx.compose.ui.geometry.Offset(canvasWidth * 0.92f, bandBottom),
            strokeWidth = 1.2f
        )

        // 中央亮横线（主体）
        drawLine(
            color = ComposeColor.White.copy(alpha = 0.95f),
            start = androidx.compose.ui.geometry.Offset(canvasWidth * 0.10f, centerY),
            end = androidx.compose.ui.geometry.Offset(canvasWidth * 0.90f, centerY),
            strokeWidth = 3f
        )

        // 横线两端强调角标（与二维码遮罩同一视觉语言）
        val tick = 10f
        drawLine(
            color = lineColor.copy(alpha = 0.9f),
            start = androidx.compose.ui.geometry.Offset(canvasWidth * 0.10f, centerY),
            end = androidx.compose.ui.geometry.Offset(canvasWidth * 0.10f, centerY - tick),
            strokeWidth = 3.4f
        )
        drawLine(
            color = lineColor.copy(alpha = 0.9f),
            start = androidx.compose.ui.geometry.Offset(canvasWidth * 0.10f, centerY),
            end = androidx.compose.ui.geometry.Offset(canvasWidth * 0.10f + tick, centerY),
            strokeWidth = 3.4f
        )
        drawLine(
            color = lineColor.copy(alpha = 0.9f),
            start = androidx.compose.ui.geometry.Offset(canvasWidth * 0.90f, centerY),
            end = androidx.compose.ui.geometry.Offset(canvasWidth * 0.90f, centerY + tick),
            strokeWidth = 3.4f
        )
        drawLine(
            color = lineColor.copy(alpha = 0.9f),
            start = androidx.compose.ui.geometry.Offset(canvasWidth * 0.90f, centerY),
            end = androidx.compose.ui.geometry.Offset(canvasWidth * 0.90f - tick, centerY),
            strokeWidth = 3.4f
        )
    }
}
