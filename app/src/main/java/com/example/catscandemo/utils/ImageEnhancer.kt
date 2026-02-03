package com.example.catscandemo.utils

import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.Rect
import android.media.Image
import androidx.annotation.OptIn
import androidx.camera.core.ExperimentalGetImage
import androidx.camera.core.ImageProxy
import java.nio.ByteBuffer
import kotlin.math.max
import kotlin.math.min

/**
 * 轻量级图像增强器
 * 专为低质量扫码环境设计，提供焦点裁剪和图像增强功能
 * 
 * 设计原则：
 * 1. 最小化内存分配 - 复用缓冲区
 * 2. 延迟增强 - 只在首次扫描失败时启用增强
 * 3. 焦点裁剪 - 只处理中心区域，减少计算量
 * 4. 快速路径 - 大多数情况下跳过增强处理
 */
object ImageEnhancer {
    
    /**
     * 增强配置
     */
    data class EnhanceConfig(
        val enableCenterCrop: Boolean = true,      // 启用中心裁剪
        val cropRatio: Float = 0.6f,               // 裁剪区域占比 (0.5-0.8)
        val enableContrastBoost: Boolean = false,  // 启用对比度增强（仅在需要时开启）
        val contrastFactor: Float = 1.3f,          // 对比度增强因子 (1.0-2.0)
        val enableSharpening: Boolean = false,     // 启用锐化（仅在需要时开启）
        val sharpenStrength: Float = 0.3f          // 锐化强度 (0.1-0.5)
    )
    
    // 默认配置：轻量模式，只做中心裁剪
    val LIGHT_CONFIG = EnhanceConfig(
        enableCenterCrop = true,
        cropRatio = 0.65f,
        enableContrastBoost = false,
        enableSharpening = false
    )
    
    // 增强配置：适用于低质量环境
    val ENHANCE_CONFIG = EnhanceConfig(
        enableCenterCrop = false,          // 对齐实时全幅扫描，关闭中心裁剪
        cropRatio = 0.7f,
        enableContrastBoost = true,
        contrastFactor = 1.5f,             // 对齐实时分层的中等对比度
        enableSharpening = false           // 与实时分层保持一致，先不锐化
    )
    
    /**
     * 计算中心裁剪区域
     * 
     * @param width 原始宽度
     * @param height 原始高度
     * @param cropRatio 裁剪比例 (0.5-1.0)
     * @return 裁剪区域 Rect
     */
    fun calculateCenterCropRect(width: Int, height: Int, cropRatio: Float = 0.65f): Rect {
        val ratio = cropRatio.coerceIn(0.3f, 1.0f)
        val cropWidth = (width * ratio).toInt()
        val cropHeight = (height * ratio).toInt()
        val left = (width - cropWidth) / 2
        val top = (height - cropHeight) / 2
        return Rect(left, top, left + cropWidth, top + cropHeight)
    }
    
    /**
     * 从YUV_420_888格式图像中提取中心区域的亮度数据
     * 这是最轻量的处理方式，直接操作Y通道数据
     * 
     * @param image YUV_420_888格式的Image
     * @param cropRect 裁剪区域
     * @return 裁剪后的亮度数据数组
     */
    fun extractCenterLuminance(image: Image, cropRect: Rect): ByteArray {
        val yPlane = image.planes[0]
        val yBuffer = yPlane.buffer
        val rowStride = yPlane.rowStride
        val pixelStride = yPlane.pixelStride
        
        val cropWidth = cropRect.width()
        val cropHeight = cropRect.height()
        val result = ByteArray(cropWidth * cropHeight)
        
        for (row in 0 until cropHeight) {
            val srcRow = cropRect.top + row
            val srcOffset = srcRow * rowStride + cropRect.left * pixelStride
            val dstOffset = row * cropWidth
            
            if (pixelStride == 1) {
                // 快速路径：连续内存
                yBuffer.position(srcOffset)
                yBuffer.get(result, dstOffset, cropWidth)
            } else {
                // 慢速路径：非连续内存
                for (col in 0 until cropWidth) {
                    result[dstOffset + col] = yBuffer.get(srcOffset + col * pixelStride)
                }
            }
        }
        
        yBuffer.rewind()
        return result
    }
    
    /**
     * 快速对比度增强 - 直接操作亮度数据
     * 使用简化的线性变换，避免复杂计算
     * 
     * @param luminance 亮度数据
     * @param factor 对比度因子 (1.0 = 不变, >1.0 = 增强)
     */
    fun boostContrastInPlace(luminance: ByteArray, factor: Float = 1.3f) {
        if (factor == 1.0f) return
        
        // 快速计算平均亮度（采样方式，减少计算量）
        var sum = 0L
        val sampleStep = max(1, luminance.size / 1000) // 最多采样1000个点
        var sampleCount = 0
        for (i in luminance.indices step sampleStep) {
            sum += (luminance[i].toInt() and 0xFF)
            sampleCount++
        }
        val mean = (sum / sampleCount).toInt()
        
        // 应用对比度增强
        for (i in luminance.indices) {
            val pixel = luminance[i].toInt() and 0xFF
            val enhanced = ((pixel - mean) * factor + mean).toInt()
            luminance[i] = enhanced.coerceIn(0, 255).toByte()
        }
    }
    
    /**
     * 快速锐化 - 使用简化的拉普拉斯算子
     * 只在边缘区域增强，减少噪点放大
     * 
     * @param luminance 亮度数据
     * @param width 图像宽度
     * @param height 图像高度
     * @param strength 锐化强度 (0.1-0.5)
     */
    fun sharpenInPlace(luminance: ByteArray, width: Int, height: Int, strength: Float = 0.3f) {
        if (strength <= 0f) return
        
        // 复制原始数据（必须，因为锐化需要原始邻域值）
        val original = luminance.copyOf()
        
        // 简化的锐化核：只使用上下左右4个邻域
        // 跳过边缘像素，避免越界检查
        for (y in 1 until height - 1) {
            for (x in 1 until width - 1) {
                val idx = y * width + x
                val center = original[idx].toInt() and 0xFF
                val top = original[idx - width].toInt() and 0xFF
                val bottom = original[idx + width].toInt() and 0xFF
                val left = original[idx - 1].toInt() and 0xFF
                val right = original[idx + 1].toInt() and 0xFF
                
                // 拉普拉斯边缘检测
                val laplacian = 4 * center - top - bottom - left - right
                
                // 应用锐化
                val sharpened = center + (laplacian * strength).toInt()
                luminance[idx] = sharpened.coerceIn(0, 255).toByte()
            }
        }
    }
    
    /**
     * 检测图像质量 - 快速评估是否需要增强
     * 通过分析亮度分布和对比度来判断
     * 
     * @param luminance 亮度数据
     * @return true 如果图像质量较低需要增强
     */
    fun needsEnhancement(luminance: ByteArray): Boolean {
        if (luminance.isEmpty()) return false
        
        // 采样分析，减少计算量
        val sampleStep = max(1, luminance.size / 500)
        var min = 255
        var max = 0
        var sum = 0L
        var count = 0
        
        for (i in luminance.indices step sampleStep) {
            val pixel = luminance[i].toInt() and 0xFF
            if (pixel < min) min = pixel
            if (pixel > max) max = pixel
            sum += pixel
            count++
        }
        
        val contrast = max - min
        val mean = (sum / count).toInt()
        
        // 判断条件：
        // 1. 对比度过低 (< 100)
        // 2. 整体过暗 (mean < 60) 或过亮 (mean > 200)
        return contrast < 100 || mean < 60 || mean > 200
    }
    
    /**
     * 从Bitmap创建增强后的Bitmap（用于相册图片识别）
     * 
     * @param bitmap 原始Bitmap
     * @param config 增强配置
     * @return 增强后的Bitmap
     */
    fun enhanceBitmap(bitmap: Bitmap, config: EnhanceConfig = ENHANCE_CONFIG): Bitmap {
        val width = bitmap.width
        val height = bitmap.height
        
        // 计算裁剪区域
        val cropRect = if (config.enableCenterCrop) {
            calculateCenterCropRect(width, height, config.cropRatio)
        } else {
            Rect(0, 0, width, height)
        }
        
        // 裁剪
        val croppedBitmap = Bitmap.createBitmap(
            bitmap,
            cropRect.left,
            cropRect.top,
            cropRect.width(),
            cropRect.height()
        )
        
        if (!config.enableContrastBoost && !config.enableSharpening) {
            return croppedBitmap
        }
        
        // 提取像素进行增强
        val pixels = IntArray(croppedBitmap.width * croppedBitmap.height)
        croppedBitmap.getPixels(pixels, 0, croppedBitmap.width, 0, 0, croppedBitmap.width, croppedBitmap.height)
        
        // 对比度增强
        if (config.enableContrastBoost) {
            enhanceContrastRGB(pixels, config.contrastFactor)
        }
        
        // 创建增强后的Bitmap
        val result = Bitmap.createBitmap(croppedBitmap.width, croppedBitmap.height, Bitmap.Config.ARGB_8888)
        result.setPixels(pixels, 0, croppedBitmap.width, 0, 0, croppedBitmap.width, croppedBitmap.height)
        
        if (croppedBitmap != bitmap) {
            croppedBitmap.recycle()
        }
        
        return result
    }

    /**
     * 提取 Bitmap 的灰度数据（与实时分层管线对齐）
     */
    fun extractLuminanceFromBitmap(bitmap: Bitmap): ByteArray {
        val width = bitmap.width
        val height = bitmap.height
        val size = width * height
        val pixels = IntArray(size)
        bitmap.getPixels(pixels, 0, width, 0, 0, width, height)
        val luminance = ByteArray(size)
        for (i in pixels.indices) {
            val p = pixels[i]
            val r = Color.red(p)
            val g = Color.green(p)
            val b = Color.blue(p)
            // 简单加权转换为灰度，权重与实时摄像头 Y 相近
            val y = (0.299f * r + 0.587f * g + 0.114f * b).toInt().coerceIn(0, 255)
            luminance[i] = y.toByte()
        }
        return luminance
    }
    
    /**
     * RGB图像对比度增强
     */
    private fun enhanceContrastRGB(pixels: IntArray, factor: Float) {
        // 计算平均亮度
        var sumR = 0L
        var sumG = 0L
        var sumB = 0L
        val sampleStep = max(1, pixels.size / 1000)
        var count = 0
        
        for (i in pixels.indices step sampleStep) {
            val pixel = pixels[i]
            sumR += Color.red(pixel)
            sumG += Color.green(pixel)
            sumB += Color.blue(pixel)
            count++
        }
        
        val meanR = (sumR / count).toInt()
        val meanG = (sumG / count).toInt()
        val meanB = (sumB / count).toInt()
        
        // 应用对比度增强
        for (i in pixels.indices) {
            val pixel = pixels[i]
            val a = Color.alpha(pixel)
            val r = ((Color.red(pixel) - meanR) * factor + meanR).toInt().coerceIn(0, 255)
            val g = ((Color.green(pixel) - meanG) * factor + meanG).toInt().coerceIn(0, 255)
            val b = ((Color.blue(pixel) - meanB) * factor + meanB).toInt().coerceIn(0, 255)
            pixels[i] = Color.argb(a, r, g, b)
        }
    }
}

/**
 * 自适应扫码增强策略
 * 根据连续扫码失败次数自动调整增强级别
 */
class AdaptiveEnhanceStrategy {
    private var consecutiveFailures = 0
    private var lastEnhanceLevel = 0
    
    companion object {
        const val LEVEL_NONE = 0        // 不增强
        const val LEVEL_CROP = 1        // 只裁剪
        const val LEVEL_CONTRAST = 2    // 裁剪 + 对比度
        const val LEVEL_FULL = 3        // 全部增强
        
        private const val FAILURES_FOR_CROP = 5       // 5次失败后启用裁剪
        private const val FAILURES_FOR_CONTRAST = 10  // 10次失败后启用对比度
        private const val FAILURES_FOR_FULL = 20      // 20次失败后启用全部
    }
    
    /**
     * 记录扫码成功，重置计数器
     */
    fun onScanSuccess() {
        consecutiveFailures = 0
        lastEnhanceLevel = LEVEL_NONE
    }
    
    /**
     * 记录扫码失败，返回建议的增强配置
     */
    fun onScanFailure(): ImageEnhancer.EnhanceConfig {
        consecutiveFailures++
        
        val newLevel = when {
            consecutiveFailures >= FAILURES_FOR_FULL -> LEVEL_FULL
            consecutiveFailures >= FAILURES_FOR_CONTRAST -> LEVEL_CONTRAST
            consecutiveFailures >= FAILURES_FOR_CROP -> LEVEL_CROP
            else -> LEVEL_NONE
        }
        
        lastEnhanceLevel = newLevel
        
        return when (newLevel) {
            LEVEL_FULL -> ImageEnhancer.EnhanceConfig(
                enableCenterCrop = true,
                cropRatio = 0.7f,
                enableContrastBoost = true,
                contrastFactor = 1.5f,
                enableSharpening = true,
                sharpenStrength = 0.3f
            )
            LEVEL_CONTRAST -> ImageEnhancer.EnhanceConfig(
                enableCenterCrop = true,
                cropRatio = 0.65f,
                enableContrastBoost = true,
                contrastFactor = 1.3f,
                enableSharpening = false
            )
            LEVEL_CROP -> ImageEnhancer.EnhanceConfig(
                enableCenterCrop = true,
                cropRatio = 0.6f,
                enableContrastBoost = false,
                enableSharpening = false
            )
            else -> ImageEnhancer.EnhanceConfig(
                enableCenterCrop = false,
                enableContrastBoost = false,
                enableSharpening = false
            )
        }
    }
    
    /**
     * 获取当前增强级别
     */
    fun getCurrentLevel(): Int = lastEnhanceLevel
    
    /**
     * 重置策略
     */
    fun reset() {
        consecutiveFailures = 0
        lastEnhanceLevel = LEVEL_NONE
    }
}
