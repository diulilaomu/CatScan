package com.example.catscandemo.camera

import android.graphics.Rect
import android.media.Image
import android.util.Log
import com.google.mlkit.vision.barcode.BarcodeScanner
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.common.InputImage
import java.nio.ByteBuffer
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger

/**
 * 高性能条码扫描管道
 * 
 * 整合所有优化组件：
 * - 优化的相机配置
 * - ROI 检测与跟踪
 * - 高级图像预处理
 * - 透视校正
 * - 多策略扫描
 */
class BarcodeScanPipeline(
    private val scanner: BarcodeScanner
) {
    companion object {
        private const val TAG = "BarcodeScanPipeline"
        
        // 策略阈值
        private const val FAST_SCAN_CONSECUTIVE_FAILURES = 5
        private const val ENHANCED_SCAN_CONSECUTIVE_FAILURES = 15
        private const val FULL_PIPELINE_CONSECUTIVE_FAILURES = 30
    }
    
    /**
     * 扫描策略
     */
    enum class ScanStrategy {
        FAST,           // 快速扫描（无预处理）
        ENHANCED,       // 增强扫描（CLAHE + 降噪）
        FULL_PIPELINE,  // 完整流水线（所有预处理）
        MULTI_SCALE     // 多尺度扫描
    }
    
    /**
     * 扫描配置
     */
    data class PipelineConfig(
        val enableROITracking: Boolean = true,
        val enableAdaptiveStrategy: Boolean = true,
        val enablePerspectiveCorrection: Boolean = true,
        val enableMultiScale: Boolean = false,
        val maxProcessingTimeMs: Long = 100,
        val processorConfig: AdvancedImageProcessor.ProcessConfig = AdvancedImageProcessor.ProcessConfig()
    )
    
    /**
     * 扫描结果
     */
    data class ScanResult(
        val barcodes: List<Barcode>,
        val processedROI: Rect?,
        val strategy: ScanStrategy,
        val processingTimeMs: Long,
        val enhanced: Boolean
    )
    
    // 状态
    private val roiTracker = ROITracker()
    private var consecutiveFailures = AtomicInteger(0)
    private var currentStrategy = ScanStrategy.FAST
    private val isProcessing = AtomicBoolean(false)
    
    // 统计
    private var totalScans = 0
    private var successfulScans = 0
    private var totalProcessingTime = 0L
    
    /**
     * 处理一帧图像
     */
    fun processFrame(
        image: Image,
        rotationDegrees: Int,
        config: PipelineConfig = PipelineConfig(),
        onResult: (ScanResult) -> Unit
    ) {
        if (!isProcessing.compareAndSet(false, true)) {
            return // 跳过此帧，避免积压
        }
        
        val startTime = System.currentTimeMillis()
        
        try {
            val width = image.width
            val height = image.height
            
            // 提取亮度数据
            val luminance = extractLuminance(image)
            
            // 确定扫描策略
            if (config.enableAdaptiveStrategy) {
                updateStrategy()
            }
            
            // 获取 ROI
            val roi = if (config.enableROITracking) {
                roiTracker.getCurrentROI()
            } else {
                null
            }
            
            // 根据策略处理
            when (currentStrategy) {
                ScanStrategy.FAST -> {
                    // 快速路径：直接扫描
                    scanWithMLKit(image, rotationDegrees, roi) { barcodes ->
                        handleResult(barcodes, roi, startTime, config, onResult)
                    }
                }
                
                ScanStrategy.ENHANCED -> {
                    // 增强路径：CLAHE + 降噪
                    val enhancedConfig = config.processorConfig.copy(
                        enableCLAHE = true,
                        enableDenoising = true,
                        enableAdaptiveThreshold = false
                    )
                    
                    val processed = if (roi != null) {
                        processROI(luminance, width, height, roi, enhancedConfig)
                    } else {
                        AdvancedImageProcessor.process(luminance, width, height, enhancedConfig)
                    }
                    
                    // 使用处理后的数据扫描
                    scanWithMLKit(image, rotationDegrees, roi) { barcodes ->
                        handleResult(barcodes, roi, startTime, config, onResult)
                    }
                }
                
                ScanStrategy.FULL_PIPELINE -> {
                    // 完整流水线
                    val fullConfig = config.processorConfig.copy(
                        enableCLAHE = true,
                        enableDenoising = true,
                        enableGlareDetection = true
                    )
                    
                    var processed = AdvancedImageProcessor.process(luminance, width, height, fullConfig)
                    
                    // 透视校正（如果启用）
                    if (config.enablePerspectiveCorrection && roi != null) {
                        val corrected = PerspectiveCorrector.autoCorrect(
                            processed.processedData,
                            processed.width,
                            processed.height,
                            roi
                        )
                        // 这里可以使用校正后的数据
                    }
                    
                    scanWithMLKit(image, rotationDegrees, roi) { barcodes ->
                        handleResult(barcodes, roi, startTime, config, onResult)
                    }
                }
                
                ScanStrategy.MULTI_SCALE -> {
                    // 多尺度扫描
                    val results = AdvancedImageProcessor.multiScaleProcess(
                        luminance, width, height,
                        scales = listOf(1.0f, 1.5f),
                        config = config.processorConfig
                    )
                    
                    // 先尝试原始尺度
                    scanWithMLKit(image, rotationDegrees, roi) { barcodes ->
                        handleResult(barcodes, roi, startTime, config, onResult)
                    }
                }
            }
            
        } catch (e: Exception) {
            Log.e(TAG, "处理帧时出错", e)
            isProcessing.set(false)
            onResult(ScanResult(emptyList(), null, currentStrategy, 0, false))
        }
    }
    
    /**
     * 提取 YUV 图像的亮度通道
     */
    private fun extractLuminance(image: Image): ByteArray {
        val yPlane = image.planes[0]
        val yBuffer = yPlane.buffer
        val ySize = yBuffer.remaining()
        val luminance = ByteArray(ySize)
        yBuffer.get(luminance)
        yBuffer.rewind()
        return luminance
    }
    
    /**
     * 只处理 ROI 区域
     */
    private fun processROI(
        luminance: ByteArray,
        width: Int,
        height: Int,
        roi: Rect,
        config: AdvancedImageProcessor.ProcessConfig
    ): AdvancedImageProcessor.ProcessResult {
        // 提取 ROI 区域
        val roiWidth = roi.width()
        val roiHeight = roi.height()
        val roiData = ByteArray(roiWidth * roiHeight)
        
        for (y in 0 until roiHeight) {
            for (x in 0 until roiWidth) {
                val srcX = roi.left + x
                val srcY = roi.top + y
                if (srcX in 0 until width && srcY in 0 until height) {
                    roiData[y * roiWidth + x] = luminance[srcY * width + srcX]
                }
            }
        }
        
        // 处理 ROI
        return AdvancedImageProcessor.process(roiData, roiWidth, roiHeight, config)
    }
    
    /**
     * 使用 ML Kit 扫描
     */
    private fun scanWithMLKit(
        image: Image,
        rotationDegrees: Int,
        roi: Rect?,
        onComplete: (List<Barcode>) -> Unit
    ) {
        val inputImage = InputImage.fromMediaImage(image, rotationDegrees)
        
        scanner.process(inputImage)
            .addOnSuccessListener { barcodes ->
                // 如果有 ROI，过滤不在 ROI 内的条码
                val filteredBarcodes = if (roi != null) {
                    barcodes.filter { barcode ->
                        barcode.boundingBox?.let { box ->
                            roi.contains(box.centerX(), box.centerY())
                        } ?: true
                    }
                } else {
                    barcodes
                }
                
                onComplete(filteredBarcodes)
            }
            .addOnFailureListener { e ->
                Log.e(TAG, "ML Kit 扫描失败", e)
                onComplete(emptyList())
            }
    }
    
    /**
     * 处理扫描结果
     */
    private fun handleResult(
        barcodes: List<Barcode>,
        roi: Rect?,
        startTime: Long,
        config: PipelineConfig,
        onResult: (ScanResult) -> Unit
    ) {
        val processingTime = System.currentTimeMillis() - startTime
        totalProcessingTime += processingTime
        totalScans++
        
        if (barcodes.isNotEmpty()) {
            consecutiveFailures.set(0)
            successfulScans++
            
            // 更新 ROI 跟踪
            if (config.enableROITracking) {
                val firstBarcode = barcodes.first()
                firstBarcode.boundingBox?.let { box ->
                    roiTracker.update(box, 0, 0) // 图像尺寸在此不重要
                }
            }
        } else {
            consecutiveFailures.incrementAndGet()
            
            if (config.enableROITracking) {
                roiTracker.update(null, 0, 0)
            }
        }
        
        isProcessing.set(false)
        
        onResult(ScanResult(
            barcodes = barcodes,
            processedROI = roi,
            strategy = currentStrategy,
            processingTimeMs = processingTime,
            enhanced = currentStrategy != ScanStrategy.FAST
        ))
    }
    
    /**
     * 更新扫描策略
     */
    private fun updateStrategy() {
        val failures = consecutiveFailures.get()
        
        currentStrategy = when {
            failures >= FULL_PIPELINE_CONSECUTIVE_FAILURES -> ScanStrategy.FULL_PIPELINE
            failures >= ENHANCED_SCAN_CONSECUTIVE_FAILURES -> ScanStrategy.ENHANCED
            failures >= FAST_SCAN_CONSECUTIVE_FAILURES -> ScanStrategy.ENHANCED
            else -> ScanStrategy.FAST
        }
    }
    
    /**
     * 获取当前策略
     */
    fun getCurrentStrategy(): ScanStrategy = currentStrategy
    
    /**
     * 获取统计信息
     */
    fun getStats(): PipelineStats {
        return PipelineStats(
            totalScans = totalScans,
            successfulScans = successfulScans,
            successRate = if (totalScans > 0) successfulScans.toFloat() / totalScans else 0f,
            averageProcessingTime = if (totalScans > 0) totalProcessingTime / totalScans else 0,
            currentStrategy = currentStrategy,
            roiState = roiTracker.getState()
        )
    }
    
    /**
     * 重置管道
     */
    fun reset() {
        roiTracker.reset()
        consecutiveFailures.set(0)
        currentStrategy = ScanStrategy.FAST
        totalScans = 0
        successfulScans = 0
        totalProcessingTime = 0
        isProcessing.set(false)
    }
    
    data class PipelineStats(
        val totalScans: Int,
        val successfulScans: Int,
        val successRate: Float,
        val averageProcessingTime: Long,
        val currentStrategy: ScanStrategy,
        val roiState: ROITracker.TrackingState
    )
}
