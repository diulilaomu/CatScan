package com.example.catscandemo.camera

import android.graphics.Bitmap
import android.graphics.Matrix
import android.graphics.Rect
import android.media.Image
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * 透视与畸变校正器
 * 
 * 功能：
 * - 检测条码区域的透视变换
 * - 进行透视校正（Dewarping）
 * - 旋转矫正
 * - 亚像素级边缘对齐
 */
object PerspectiveCorrector {
    
    private const val TAG = "PerspectiveCorrector"
    
    /**
     * 四边形角点（用于透视变换）
     */
    data class Quadrilateral(
        val topLeft: Point,
        val topRight: Point,
        val bottomRight: Point,
        val bottomLeft: Point
    ) {
        fun toFloatArray(): FloatArray {
            return floatArrayOf(
                topLeft.x, topLeft.y,
                topRight.x, topRight.y,
                bottomRight.x, bottomRight.y,
                bottomLeft.x, bottomLeft.y
            )
        }
        
        fun width(): Float {
            val topWidth = sqrt((topRight.x - topLeft.x) * (topRight.x - topLeft.x) + 
                               (topRight.y - topLeft.y) * (topRight.y - topLeft.y))
            val bottomWidth = sqrt((bottomRight.x - bottomLeft.x) * (bottomRight.x - bottomLeft.x) + 
                                   (bottomRight.y - bottomLeft.y) * (bottomRight.y - bottomLeft.y))
            return (topWidth + bottomWidth) / 2
        }
        
        fun height(): Float {
            val leftHeight = sqrt((bottomLeft.x - topLeft.x) * (bottomLeft.x - topLeft.x) + 
                                  (bottomLeft.y - topLeft.y) * (bottomLeft.y - topLeft.y))
            val rightHeight = sqrt((bottomRight.x - topRight.x) * (bottomRight.x - topRight.x) + 
                                   (bottomRight.y - topRight.y) * (bottomRight.y - topRight.y))
            return (leftHeight + rightHeight) / 2
        }
    }
    
    data class Point(val x: Float, val y: Float)
    
    /**
     * 校正结果
     */
    data class CorrectionResult(
        val correctedData: ByteArray,
        val width: Int,
        val height: Int,
        val rotationAngle: Float = 0f,
        val perspectiveApplied: Boolean = false
    )
    
    /**
     * 从矩形边界框推断四边形（假设轻微透视）
     */
    fun rectToQuadrilateral(rect: Rect): Quadrilateral {
        return Quadrilateral(
            topLeft = Point(rect.left.toFloat(), rect.top.toFloat()),
            topRight = Point(rect.right.toFloat(), rect.top.toFloat()),
            bottomRight = Point(rect.right.toFloat(), rect.bottom.toFloat()),
            bottomLeft = Point(rect.left.toFloat(), rect.bottom.toFloat())
        )
    }
    
    /**
     * 检测旋转角度
     * 通过分析条码区域的边缘方向
     */
    fun detectRotationAngle(
        luminance: ByteArray,
        width: Int,
        height: Int,
        roi: Rect
    ): Float {
        // 使用 Sobel 算子计算梯度方向
        val gradients = mutableListOf<Float>()
        
        val roiWidth = roi.width()
        val roiHeight = roi.height()
        
        for (y in roi.top + 1 until roi.bottom - 1) {
            for (x in roi.left + 1 until roi.right - 1) {
                if (x < 1 || x >= width - 1 || y < 1 || y >= height - 1) continue
                
                // Sobel X
                val gx = (-1 * getPixel(luminance, width, x - 1, y - 1) +
                          1 * getPixel(luminance, width, x + 1, y - 1) +
                         -2 * getPixel(luminance, width, x - 1, y) +
                          2 * getPixel(luminance, width, x + 1, y) +
                         -1 * getPixel(luminance, width, x - 1, y + 1) +
                          1 * getPixel(luminance, width, x + 1, y + 1))
                
                // Sobel Y
                val gy = (-1 * getPixel(luminance, width, x - 1, y - 1) +
                         -2 * getPixel(luminance, width, x, y - 1) +
                         -1 * getPixel(luminance, width, x + 1, y - 1) +
                          1 * getPixel(luminance, width, x - 1, y + 1) +
                          2 * getPixel(luminance, width, x, y + 1) +
                          1 * getPixel(luminance, width, x + 1, y + 1))
                
                val magnitude = sqrt((gx * gx + gy * gy).toFloat())
                
                // 只考虑强边缘
                if (magnitude > 50) {
                    val angle = atan2(gy.toFloat(), gx.toFloat()) * 180f / Math.PI.toFloat()
                    gradients.add(angle)
                }
            }
        }
        
        if (gradients.isEmpty()) return 0f
        
        // 使用直方图找到主方向
        val histogram = IntArray(180)
        for (angle in gradients) {
            val bin = ((angle + 90) % 180).toInt().coerceIn(0, 179)
            histogram[bin]++
        }
        
        // 找到峰值
        var maxBin = 0
        var maxCount = 0
        for (i in 0 until 180) {
            if (histogram[i] > maxCount) {
                maxCount = histogram[i]
                maxBin = i
            }
        }
        
        // 转换回角度
        val dominantAngle = maxBin - 90f
        
        // 归一化到 [-45, 45] 度范围
        return when {
            dominantAngle > 45 -> dominantAngle - 90
            dominantAngle < -45 -> dominantAngle + 90
            else -> dominantAngle
        }
    }
    
    private fun getPixel(data: ByteArray, width: Int, x: Int, y: Int): Int {
        return data[y * width + x].toInt() and 0xFF
    }
    
    /**
     * 旋转图像
     */
    fun rotate(
        luminance: ByteArray,
        width: Int,
        height: Int,
        angleDegrees: Float
    ): CorrectionResult {
        if (abs(angleDegrees) < 0.5f) {
            return CorrectionResult(luminance, width, height, 0f, false)
        }
        
        val angleRadians = angleDegrees * Math.PI.toFloat() / 180f
        val cosA = cos(angleRadians)
        val sinA = sin(angleRadians)
        
        // 计算旋转后的尺寸
        val newWidth = (abs(width * cosA) + abs(height * sinA)).toInt()
        val newHeight = (abs(width * sinA) + abs(height * cosA)).toInt()
        
        val result = ByteArray(newWidth * newHeight)
        
        val cx = width / 2f
        val cy = height / 2f
        val ncx = newWidth / 2f
        val ncy = newHeight / 2f
        
        for (ny in 0 until newHeight) {
            for (nx in 0 until newWidth) {
                // 反向映射
                val dx = nx - ncx
                val dy = ny - ncy
                
                val sx = dx * cosA + dy * sinA + cx
                val sy = -dx * sinA + dy * cosA + cy
                
                if (sx >= 0 && sx < width - 1 && sy >= 0 && sy < height - 1) {
                    // 双线性插值
                    val x0 = sx.toInt()
                    val y0 = sy.toInt()
                    val fx = sx - x0
                    val fy = sy - y0
                    
                    val v00 = getPixel(luminance, width, x0, y0)
                    val v01 = getPixel(luminance, width, x0 + 1, y0)
                    val v10 = getPixel(luminance, width, x0, y0 + 1)
                    val v11 = getPixel(luminance, width, x0 + 1, y0 + 1)
                    
                    val value = v00 * (1 - fx) * (1 - fy) +
                                v01 * fx * (1 - fy) +
                                v10 * (1 - fx) * fy +
                                v11 * fx * fy
                    
                    result[ny * newWidth + nx] = value.toInt().coerceIn(0, 255).toByte()
                }
            }
        }
        
        return CorrectionResult(result, newWidth, newHeight, angleDegrees, false)
    }
    
    /**
     * 透视校正（简化版）
     * 将倾斜的条码区域校正为矩形
     */
    fun correctPerspective(
        luminance: ByteArray,
        width: Int,
        height: Int,
        quad: Quadrilateral
    ): CorrectionResult {
        val targetWidth = quad.width().toInt()
        val targetHeight = quad.height().toInt()
        
        if (targetWidth <= 0 || targetHeight <= 0) {
            return CorrectionResult(luminance, width, height, 0f, false)
        }
        
        val result = ByteArray(targetWidth * targetHeight)
        
        // 计算透视变换矩阵（简化：使用双线性插值）
        for (ty in 0 until targetHeight) {
            for (tx in 0 until targetWidth) {
                val u = tx.toFloat() / targetWidth
                val v = ty.toFloat() / targetHeight
                
                // 双线性插值计算源坐标
                val topX = quad.topLeft.x + u * (quad.topRight.x - quad.topLeft.x)
                val topY = quad.topLeft.y + u * (quad.topRight.y - quad.topLeft.y)
                val bottomX = quad.bottomLeft.x + u * (quad.bottomRight.x - quad.bottomLeft.x)
                val bottomY = quad.bottomLeft.y + u * (quad.bottomRight.y - quad.bottomLeft.y)
                
                val sx = topX + v * (bottomX - topX)
                val sy = topY + v * (bottomY - topY)
                
                if (sx >= 0 && sx < width - 1 && sy >= 0 && sy < height - 1) {
                    val x0 = sx.toInt()
                    val y0 = sy.toInt()
                    val fx = sx - x0
                    val fy = sy - y0
                    
                    val v00 = getPixel(luminance, width, x0, y0)
                    val v01 = getPixel(luminance, width, x0 + 1, y0)
                    val v10 = getPixel(luminance, width, x0, y0 + 1)
                    val v11 = getPixel(luminance, width, x0 + 1, y0 + 1)
                    
                    val value = v00 * (1 - fx) * (1 - fy) +
                                v01 * fx * (1 - fy) +
                                v10 * (1 - fx) * fy +
                                v11 * fx * fy
                    
                    result[ty * targetWidth + tx] = value.toInt().coerceIn(0, 255).toByte()
                }
            }
        }
        
        return CorrectionResult(result, targetWidth, targetHeight, 0f, true)
    }
    
    /**
     * 自动校正
     * 先检测旋转角度，再进行旋转校正
     */
    fun autoCorrect(
        luminance: ByteArray,
        width: Int,
        height: Int,
        roi: Rect? = null
    ): CorrectionResult {
        val effectiveROI = roi ?: Rect(0, 0, width, height)
        
        // 检测旋转角度
        val angle = detectRotationAngle(luminance, width, height, effectiveROI)
        
        // 如果角度很小，跳过旋转
        if (abs(angle) < 2f) {
            return CorrectionResult(luminance, width, height, 0f, false)
        }
        
        // 执行旋转
        return rotate(luminance, width, height, -angle)
    }
}

/**
 * 条码区域检测器
 * 快速定位图像中的条码区域
 */
object BarcodeRegionDetector {
    
    /**
     * 检测条码候选区域
     * 使用梯度分析快速定位可能包含条码的区域
     */
    fun detectCandidateRegions(
        luminance: ByteArray,
        width: Int,
        height: Int,
        minSize: Int = 30
    ): List<Rect> {
        val regions = mutableListOf<Rect>()
        
        // 计算水平梯度（条形码主要是水平条纹）
        val gradientMap = ByteArray(width * height)
        
        for (y in 1 until height - 1) {
            for (x in 1 until width - 1) {
                val idx = y * width + x
                val left = luminance[idx - 1].toInt() and 0xFF
                val right = luminance[idx + 1].toInt() and 0xFF
                val gradient = abs(right - left)
                gradientMap[idx] = gradient.coerceIn(0, 255).toByte()
            }
        }
        
        // 使用滑动窗口检测高梯度区域
        val windowSize = 32
        val step = 16
        val threshold = 30
        
        for (y in 0 until height - windowSize step step) {
            for (x in 0 until width - windowSize step step) {
                var sum = 0
                for (wy in 0 until windowSize) {
                    for (wx in 0 until windowSize) {
                        sum += gradientMap[(y + wy) * width + (x + wx)].toInt() and 0xFF
                    }
                }
                val avg = sum / (windowSize * windowSize)
                
                if (avg > threshold) {
                    regions.add(Rect(x, y, x + windowSize, y + windowSize))
                }
            }
        }
        
        // 合并重叠区域
        return mergeOverlappingRegions(regions, minSize)
    }
    
    /**
     * 合并重叠区域
     */
    private fun mergeOverlappingRegions(regions: List<Rect>, minSize: Int): List<Rect> {
        if (regions.isEmpty()) return emptyList()
        
        val merged = mutableListOf<Rect>()
        val used = BooleanArray(regions.size)
        
        for (i in regions.indices) {
            if (used[i]) continue
            
            var current = regions[i]
            used[i] = true
            
            var changed = true
            while (changed) {
                changed = false
                for (j in regions.indices) {
                    if (used[j]) continue
                    
                    if (Rect.intersects(current, regions[j]) || 
                        isAdjacent(current, regions[j])) {
                        current = Rect(
                            minOf(current.left, regions[j].left),
                            minOf(current.top, regions[j].top),
                            maxOf(current.right, regions[j].right),
                            maxOf(current.bottom, regions[j].bottom)
                        )
                        used[j] = true
                        changed = true
                    }
                }
            }
            
            if (current.width() >= minSize && current.height() >= minSize) {
                merged.add(current)
            }
        }
        
        return merged
    }
    
    private fun isAdjacent(r1: Rect, r2: Rect, threshold: Int = 5): Boolean {
        return r1.right + threshold >= r2.left && r2.right + threshold >= r1.left &&
               r1.bottom + threshold >= r2.top && r2.bottom + threshold >= r1.top
    }
}
