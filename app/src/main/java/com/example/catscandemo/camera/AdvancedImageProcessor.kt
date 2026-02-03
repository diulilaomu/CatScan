package com.example.catscandemo.camera

import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.Rect
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

/**
 * 高级图像预处理流水线
 * 
 * 提供专业级的图像增强功能：
 * - CLAHE (对比度受限自适应直方图均衡化)
 * - 自适应阈值二值化
 * - 噪声抑制（双边滤波/中值滤波）
 * - 高光/反光检测与补偿
 * - 超分辨率插值
 * - 透视校正准备
 */
object AdvancedImageProcessor {
    
    private const val TAG = "AdvancedImageProcessor"
    
    /**
     * 处理配置
     */
    data class ProcessConfig(
        val enableCLAHE: Boolean = true,              // 启用 CLAHE
        val claheClipLimit: Float = 2.5f,             // CLAHE 裁剪限制
        val claheTileSize: Int = 8,                   // CLAHE 分块大小
        val enableDenoising: Boolean = true,          // 启用降噪
        val denoiseStrength: Float = 0.5f,            // 降噪强度
        val enableAdaptiveThreshold: Boolean = false, // 启用自适应阈值
        val enableGlareDetection: Boolean = true,     // 启用高光检测
        val enableSuperResolution: Boolean = false,   // 启用超分辨率
        val superResScale: Float = 2.0f               // 超分辨率倍数
    )
    
    /**
     * 处理结果
     */
    data class ProcessResult(
        val processedData: ByteArray,       // 处理后的亮度数据
        val width: Int,
        val height: Int,
        val hasGlare: Boolean = false,      // 是否检测到高光
        val glareRegions: List<Rect> = emptyList(), // 高光区域
        val suggestedROI: Rect? = null      // 建议的扫描区域
    )
    
    /**
     * 完整处理流水线
     */
    fun process(
        luminance: ByteArray,
        width: Int,
        height: Int,
        config: ProcessConfig = ProcessConfig()
    ): ProcessResult {
        var data = luminance.copyOf()
        var glareRegions = emptyList<Rect>()
        var hasGlare = false
        
        // 1. 高光检测
        if (config.enableGlareDetection) {
            val glareResult = detectAndCompensateGlare(data, width, height)
            data = glareResult.first
            glareRegions = glareResult.second
            hasGlare = glareRegions.isNotEmpty()
        }
        
        // 2. 降噪
        if (config.enableDenoising) {
            data = fastBilateralFilter(data, width, height, config.denoiseStrength)
        }
        
        // 3. CLAHE 对比度增强
        if (config.enableCLAHE) {
            data = applyCLAHE(data, width, height, config.claheClipLimit, config.claheTileSize)
        }
        
        // 4. 自适应阈值（可选）
        if (config.enableAdaptiveThreshold) {
            data = adaptiveThreshold(data, width, height)
        }
        
        return ProcessResult(
            processedData = data,
            width = width,
            height = height,
            hasGlare = hasGlare,
            glareRegions = glareRegions
        )
    }
    
    /**
     * CLAHE - 对比度受限自适应直方图均衡化
     * 
     * 优点：
     * - 局部对比度增强，保留细节
     * - 避免过度增强导致的噪点放大
     * - 特别适合条码场景
     */
    fun applyCLAHE(
        luminance: ByteArray,
        width: Int,
        height: Int,
        clipLimit: Float = 2.5f,
        tileSize: Int = 8
    ): ByteArray {
        val result = ByteArray(luminance.size)
        
        // 计算分块数量
        val tilesX = (width + tileSize - 1) / tileSize
        val tilesY = (height + tileSize - 1) / tileSize
        
        // 为每个分块计算直方图和映射表
        val tileMappings = Array(tilesY) { ty ->
            Array(tilesX) { tx ->
                calculateTileMapping(luminance, width, height, tx, ty, tileSize, clipLimit)
            }
        }
        
        // 双线性插值应用映射
        for (y in 0 until height) {
            for (x in 0 until width) {
                val idx = y * width + x
                val pixel = luminance[idx].toInt() and 0xFF
                
                // 计算所在分块及相对位置
                val tileX = x / tileSize
                val tileY = y / tileSize
                val localX = (x % tileSize).toFloat() / tileSize
                val localY = (y % tileSize).toFloat() / tileSize
                
                // 获取四个相邻分块的映射值
                val tx0 = tileX.coerceIn(0, tilesX - 1)
                val ty0 = tileY.coerceIn(0, tilesY - 1)
                val tx1 = (tileX + 1).coerceIn(0, tilesX - 1)
                val ty1 = (tileY + 1).coerceIn(0, tilesY - 1)
                
                val v00 = tileMappings[ty0][tx0][pixel]
                val v01 = tileMappings[ty0][tx1][pixel]
                val v10 = tileMappings[ty1][tx0][pixel]
                val v11 = tileMappings[ty1][tx1][pixel]
                
                // 双线性插值
                val v0 = v00 * (1 - localX) + v01 * localX
                val v1 = v10 * (1 - localX) + v11 * localX
                val value = v0 * (1 - localY) + v1 * localY
                
                result[idx] = value.toInt().coerceIn(0, 255).toByte()
            }
        }
        
        return result
    }
    
    /**
     * 计算单个分块的直方图均衡化映射表
     */
    private fun calculateTileMapping(
        luminance: ByteArray,
        width: Int,
        height: Int,
        tileX: Int,
        tileY: Int,
        tileSize: Int,
        clipLimit: Float
    ): IntArray {
        val histogram = IntArray(256)
        
        // 计算分块范围
        val startX = tileX * tileSize
        val startY = tileY * tileSize
        val endX = min(startX + tileSize, width)
        val endY = min(startY + tileSize, height)
        val tilePixels = (endX - startX) * (endY - startY)
        
        // 构建直方图
        for (y in startY until endY) {
            for (x in startX until endX) {
                val pixel = luminance[y * width + x].toInt() and 0xFF
                histogram[pixel]++
            }
        }
        
        // 裁剪直方图
        val clipValue = (clipLimit * tilePixels / 256).toInt()
        var excess = 0
        for (i in 0 until 256) {
            if (histogram[i] > clipValue) {
                excess += histogram[i] - clipValue
                histogram[i] = clipValue
            }
        }
        
        // 重新分配多余的像素
        val increment = excess / 256
        val remainder = excess % 256
        for (i in 0 until 256) {
            histogram[i] += increment
            if (i < remainder) histogram[i]++
        }
        
        // 计算 CDF 并生成映射表
        val mapping = IntArray(256)
        var cdf = 0
        for (i in 0 until 256) {
            cdf += histogram[i]
            mapping[i] = (cdf * 255 / tilePixels).coerceIn(0, 255)
        }
        
        return mapping
    }
    
    /**
     * 快速双边滤波（简化版）
     * 保边降噪，适合条码边缘保持
     */
    fun fastBilateralFilter(
        luminance: ByteArray,
        width: Int,
        height: Int,
        strength: Float = 0.5f
    ): ByteArray {
        val result = ByteArray(luminance.size)
        val kernelSize = 3
        val halfKernel = kernelSize / 2
        val sigmaSpace = 1.5f
        val sigmaColor = 30f * strength
        
        // 预计算空间权重
        val spaceWeights = Array(kernelSize) { dy ->
            FloatArray(kernelSize) { dx ->
                val dist = sqrt(((dx - halfKernel) * (dx - halfKernel) + (dy - halfKernel) * (dy - halfKernel)).toFloat())
                kotlin.math.exp(-(dist * dist) / (2 * sigmaSpace * sigmaSpace))
            }
        }
        
        for (y in 0 until height) {
            for (x in 0 until width) {
                val centerIdx = y * width + x
                val centerValue = luminance[centerIdx].toInt() and 0xFF
                
                var weightSum = 0f
                var valueSum = 0f
                
                for (dy in 0 until kernelSize) {
                    for (dx in 0 until kernelSize) {
                        val ny = y + dy - halfKernel
                        val nx = x + dx - halfKernel
                        
                        if (ny in 0 until height && nx in 0 until width) {
                            val neighborValue = luminance[ny * width + nx].toInt() and 0xFF
                            val colorDiff = abs(neighborValue - centerValue).toFloat()
                            val colorWeight = kotlin.math.exp(-(colorDiff * colorDiff) / (2 * sigmaColor * sigmaColor))
                            val weight = spaceWeights[dy][dx] * colorWeight
                            
                            weightSum += weight
                            valueSum += weight * neighborValue
                        }
                    }
                }
                
                result[centerIdx] = if (weightSum > 0) {
                    (valueSum / weightSum).toInt().coerceIn(0, 255).toByte()
                } else {
                    luminance[centerIdx]
                }
            }
        }
        
        return result
    }
    
    /**
     * 自适应阈值二值化
     * 适用于光照不均匀的场景
     */
    fun adaptiveThreshold(
        luminance: ByteArray,
        width: Int,
        height: Int,
        blockSize: Int = 15,
        c: Int = 5
    ): ByteArray {
        val result = ByteArray(luminance.size)
        val halfBlock = blockSize / 2
        
        // 使用积分图加速计算
        val integral = LongArray((width + 1) * (height + 1))
        
        // 计算积分图
        for (y in 0 until height) {
            var rowSum = 0L
            for (x in 0 until width) {
                rowSum += luminance[y * width + x].toInt() and 0xFF
                integral[(y + 1) * (width + 1) + (x + 1)] = rowSum + integral[y * (width + 1) + (x + 1)]
            }
        }
        
        // 应用自适应阈值
        for (y in 0 until height) {
            for (x in 0 until width) {
                val x1 = max(0, x - halfBlock)
                val y1 = max(0, y - halfBlock)
                val x2 = min(width - 1, x + halfBlock)
                val y2 = min(height - 1, y + halfBlock)
                
                val count = (x2 - x1 + 1) * (y2 - y1 + 1)
                val sum = integral[(y2 + 1) * (width + 1) + (x2 + 1)] -
                          integral[y1 * (width + 1) + (x2 + 1)] -
                          integral[(y2 + 1) * (width + 1) + x1] +
                          integral[y1 * (width + 1) + x1]
                
                val mean = (sum / count).toInt()
                val threshold = mean - c
                
                val pixel = luminance[y * width + x].toInt() and 0xFF
                result[y * width + x] = if (pixel > threshold) 255.toByte() else 0
            }
        }
        
        return result
    }
    
    /**
     * 高光/反光检测与补偿
     */
    fun detectAndCompensateGlare(
        luminance: ByteArray,
        width: Int,
        height: Int,
        glareThreshold: Int = 240
    ): Pair<ByteArray, List<Rect>> {
        val result = luminance.copyOf()
        val glareRegions = mutableListOf<Rect>()
        val visited = BooleanArray(luminance.size)
        
        // 检测高光像素并进行区域生长
        for (y in 0 until height) {
            for (x in 0 until width) {
                val idx = y * width + x
                val pixel = luminance[idx].toInt() and 0xFF
                
                if (pixel >= glareThreshold && !visited[idx]) {
                    // 区域生长找到高光区域
                    val region = growRegion(luminance, width, height, x, y, glareThreshold, visited)
                    if (region.width() > 5 && region.height() > 5) {
                        glareRegions.add(region)
                        
                        // 补偿高光区域
                        compensateGlareRegion(result, width, height, region)
                    }
                }
            }
        }
        
        return Pair(result, glareRegions)
    }
    
    /**
     * 区域生长
     */
    private fun growRegion(
        luminance: ByteArray,
        width: Int,
        height: Int,
        startX: Int,
        startY: Int,
        threshold: Int,
        visited: BooleanArray
    ): Rect {
        var minX = startX
        var minY = startY
        var maxX = startX
        var maxY = startY
        
        val stack = mutableListOf(Pair(startX, startY))
        
        while (stack.isNotEmpty()) {
            val (x, y) = stack.removeAt(stack.size - 1)
            val idx = y * width + x
            
            if (x < 0 || x >= width || y < 0 || y >= height) continue
            if (visited[idx]) continue
            
            val pixel = luminance[idx].toInt() and 0xFF
            if (pixel < threshold) continue
            
            visited[idx] = true
            minX = min(minX, x)
            minY = min(minY, y)
            maxX = max(maxX, x)
            maxY = max(maxY, y)
            
            // 添加邻居
            stack.add(Pair(x - 1, y))
            stack.add(Pair(x + 1, y))
            stack.add(Pair(x, y - 1))
            stack.add(Pair(x, y + 1))
        }
        
        return Rect(minX, minY, maxX + 1, maxY + 1)
    }
    
    /**
     * 补偿高光区域
     * 使用周围像素的平均值填充
     */
    private fun compensateGlareRegion(
        luminance: ByteArray,
        width: Int,
        height: Int,
        region: Rect
    ) {
        // 计算边界像素的平均值
        var sum = 0L
        var count = 0
        
        // 上边界
        if (region.top > 0) {
            for (x in region.left until region.right) {
                sum += luminance[(region.top - 1) * width + x].toInt() and 0xFF
                count++
            }
        }
        
        // 下边界
        if (region.bottom < height) {
            for (x in region.left until region.right) {
                sum += luminance[region.bottom * width + x].toInt() and 0xFF
                count++
            }
        }
        
        // 左边界
        if (region.left > 0) {
            for (y in region.top until region.bottom) {
                sum += luminance[y * width + (region.left - 1)].toInt() and 0xFF
                count++
            }
        }
        
        // 右边界
        if (region.right < width) {
            for (y in region.top until region.bottom) {
                sum += luminance[y * width + region.right].toInt() and 0xFF
                count++
            }
        }
        
        val avgValue = if (count > 0) (sum / count).toByte() else 128.toByte()
        
        // 填充高光区域
        for (y in region.top until region.bottom) {
            for (x in region.left until region.right) {
                if (x in 0 until width && y in 0 until height) {
                    luminance[y * width + x] = avgValue
                }
            }
        }
    }
    
    /**
     * 双三次插值超分辨率
     * 对小条码进行放大以提高识别率
     */
    fun bicubicUpscale(
        luminance: ByteArray,
        width: Int,
        height: Int,
        scale: Float = 2.0f
    ): Triple<ByteArray, Int, Int> {
        val newWidth = (width * scale).toInt()
        val newHeight = (height * scale).toInt()
        val result = ByteArray(newWidth * newHeight)
        
        for (newY in 0 until newHeight) {
            for (newX in 0 until newWidth) {
                val srcX = newX / scale
                val srcY = newY / scale
                
                val value = bicubicInterpolate(luminance, width, height, srcX, srcY)
                result[newY * newWidth + newX] = value.coerceIn(0, 255).toByte()
            }
        }
        
        return Triple(result, newWidth, newHeight)
    }
    
    /**
     * 双三次插值
     */
    private fun bicubicInterpolate(
        data: ByteArray,
        width: Int,
        height: Int,
        x: Float,
        y: Float
    ): Int {
        val x0 = x.toInt()
        val y0 = y.toInt()
        val dx = x - x0
        val dy = y - y0
        
        var sum = 0.0
        for (j in -1..2) {
            for (i in -1..2) {
                val px = (x0 + i).coerceIn(0, width - 1)
                val py = (y0 + j).coerceIn(0, height - 1)
                val pixel = data[py * width + px].toInt() and 0xFF
                
                val wx = cubicWeight(i - dx)
                val wy = cubicWeight(j - dy)
                
                sum += pixel * wx * wy
            }
        }
        
        return sum.toInt()
    }
    
    /**
     * 三次插值核函数
     */
    private fun cubicWeight(t: Float): Float {
        val a = -0.5f
        val absT = abs(t)
        
        return when {
            absT <= 1 -> (a + 2) * absT * absT * absT - (a + 3) * absT * absT + 1
            absT < 2 -> a * absT * absT * absT - 5 * a * absT * absT + 8 * a * absT - 4 * a
            else -> 0f
        }
    }
    
    /**
     * 多尺度处理
     * 在多个尺度上处理图像，提高不同大小条码的识别率
     */
    fun multiScaleProcess(
        luminance: ByteArray,
        width: Int,
        height: Int,
        scales: List<Float> = listOf(1.0f, 1.5f, 2.0f),
        config: ProcessConfig = ProcessConfig()
    ): List<ProcessResult> {
        return scales.map { scale ->
            val (scaledData, scaledWidth, scaledHeight) = if (scale != 1.0f) {
                bicubicUpscale(luminance, width, height, scale)
            } else {
                Triple(luminance, width, height)
            }
            
            process(scaledData, scaledWidth, scaledHeight, config).copy(
                width = scaledWidth,
                height = scaledHeight
            )
        }
    }
}
