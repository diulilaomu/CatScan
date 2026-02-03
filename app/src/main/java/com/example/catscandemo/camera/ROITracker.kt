package com.example.catscandemo.camera

import android.graphics.Rect
import android.graphics.RectF
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

/**
 * ROI (Region of Interest) 检测与跟踪器
 * 
 * 功能：
 * - 实时定位条码区域
 * - 跟踪已检测到的条码区域，避免每帧重新定位
 * - 预测下一帧位置，提高跟踪鲁棒性
 * - 自适应扩展 ROI 区域以应对运动模糊
 */
class ROITracker {
    
    companion object {
        private const val TAG = "ROITracker"
        
        // 跟踪参数
        private const val MAX_TRACKING_FRAMES = 30      // 最大跟踪帧数
        private const val IOU_THRESHOLD = 0.3f          // IoU 阈值，低于此值认为丢失
        private const val VELOCITY_SMOOTHING = 0.7f     // 速度平滑因子
        private const val ROI_EXPANSION_RATIO = 0.15f   // ROI 扩展比例
        private const val MIN_ROI_SIZE = 50             // 最小 ROI 尺寸
    }
    
    /**
     * 跟踪状态
     */
    enum class TrackingState {
        IDLE,           // 空闲，未检测到目标
        TRACKING,       // 正在跟踪
        PREDICTING,     // 预测模式（目标暂时丢失）
        LOST            // 丢失
    }
    
    /**
     * 跟踪的 ROI 信息
     */
    data class TrackedROI(
        val rect: Rect,                     // 当前边界框
        val confidence: Float,              // 置信度 (0-1)
        val state: TrackingState,           // 跟踪状态
        val frameCount: Int,                // 跟踪帧数
        val velocityX: Float = 0f,          // X 方向速度
        val velocityY: Float = 0f,          // Y 方向速度
        val timestamp: Long = System.currentTimeMillis()
    )
    
    // 内部状态
    private var currentROI: TrackedROI? = null
    private var lastDetectionRect: Rect? = null
    private var predictionFrames = 0
    private val historyRects = mutableListOf<Rect>()
    
    /**
     * 更新跟踪器状态
     * 
     * @param detectedRect 当前帧检测到的条码边界框（可为 null 表示未检测到）
     * @param imageWidth 图像宽度
     * @param imageHeight 图像高度
     * @return 当前跟踪的 ROI（可能是检测结果或预测结果）
     */
    fun update(detectedRect: Rect?, imageWidth: Int, imageHeight: Int): TrackedROI? {
        val now = System.currentTimeMillis()
        
        if (detectedRect != null) {
            // 有新检测结果
            val expandedRect = expandROI(detectedRect, imageWidth, imageHeight)
            
            if (currentROI == null) {
                // 首次检测
                currentROI = TrackedROI(
                    rect = expandedRect,
                    confidence = 1.0f,
                    state = TrackingState.TRACKING,
                    frameCount = 1
                )
            } else {
                // 更新跟踪
                val iou = calculateIoU(currentROI!!.rect, detectedRect)
                
                if (iou > IOU_THRESHOLD) {
                    // 匹配成功，更新位置和速度
                    val (vx, vy) = calculateVelocity(currentROI!!.rect, detectedRect)
                    
                    currentROI = TrackedROI(
                        rect = expandedRect,
                        confidence = min(1.0f, currentROI!!.confidence + 0.1f),
                        state = TrackingState.TRACKING,
                        frameCount = currentROI!!.frameCount + 1,
                        velocityX = vx * VELOCITY_SMOOTHING + currentROI!!.velocityX * (1 - VELOCITY_SMOOTHING),
                        velocityY = vy * VELOCITY_SMOOTHING + currentROI!!.velocityY * (1 - VELOCITY_SMOOTHING),
                        timestamp = now
                    )
                } else {
                    // IoU 低，可能是新目标
                    currentROI = TrackedROI(
                        rect = expandedRect,
                        confidence = 0.8f,
                        state = TrackingState.TRACKING,
                        frameCount = 1,
                        timestamp = now
                    )
                }
            }
            
            predictionFrames = 0
            lastDetectionRect = detectedRect
            
            // 保存历史
            historyRects.add(detectedRect)
            if (historyRects.size > 10) {
                historyRects.removeAt(0)
            }
            
        } else if (currentROI != null) {
            // 未检测到，尝试预测
            predictionFrames++
            
            if (predictionFrames <= MAX_TRACKING_FRAMES / 3) {
                // 短期预测
                val predictedRect = predictNextPosition(currentROI!!, imageWidth, imageHeight)
                currentROI = TrackedROI(
                    rect = predictedRect,
                    confidence = max(0f, currentROI!!.confidence - 0.05f),
                    state = TrackingState.PREDICTING,
                    frameCount = currentROI!!.frameCount + 1,
                    velocityX = currentROI!!.velocityX * 0.9f,
                    velocityY = currentROI!!.velocityY * 0.9f,
                    timestamp = now
                )
            } else if (predictionFrames <= MAX_TRACKING_FRAMES) {
                // 长期预测，置信度快速下降
                currentROI = currentROI!!.copy(
                    confidence = max(0f, currentROI!!.confidence - 0.1f),
                    state = TrackingState.PREDICTING
                )
            } else {
                // 丢失
                currentROI = currentROI!!.copy(
                    state = TrackingState.LOST,
                    confidence = 0f
                )
            }
        }
        
        // 如果置信度太低，重置
        if (currentROI?.confidence ?: 0f < 0.1f) {
            reset()
        }
        
        return currentROI
    }
    
    /**
     * 获取当前 ROI 区域（用于只处理该区域）
     */
    fun getCurrentROI(): Rect? {
        return currentROI?.rect
    }
    
    /**
     * 获取跟踪状态
     */
    fun getState(): TrackingState {
        return currentROI?.state ?: TrackingState.IDLE
    }
    
    /**
     * 获取置信度
     */
    fun getConfidence(): Float {
        return currentROI?.confidence ?: 0f
    }
    
    /**
     * 重置跟踪器
     */
    fun reset() {
        currentROI = null
        lastDetectionRect = null
        predictionFrames = 0
        historyRects.clear()
    }
    
    /**
     * 计算两个矩形的 IoU (Intersection over Union)
     */
    private fun calculateIoU(rect1: Rect, rect2: Rect): Float {
        val intersectLeft = max(rect1.left, rect2.left)
        val intersectTop = max(rect1.top, rect2.top)
        val intersectRight = min(rect1.right, rect2.right)
        val intersectBottom = min(rect1.bottom, rect2.bottom)
        
        if (intersectLeft >= intersectRight || intersectTop >= intersectBottom) {
            return 0f
        }
        
        val intersectArea = (intersectRight - intersectLeft) * (intersectBottom - intersectTop)
        val area1 = rect1.width() * rect1.height()
        val area2 = rect2.width() * rect2.height()
        val unionArea = area1 + area2 - intersectArea
        
        return if (unionArea > 0) intersectArea.toFloat() / unionArea else 0f
    }
    
    /**
     * 计算速度
     */
    private fun calculateVelocity(oldRect: Rect, newRect: Rect): Pair<Float, Float> {
        val vx = (newRect.centerX() - oldRect.centerX()).toFloat()
        val vy = (newRect.centerY() - oldRect.centerY()).toFloat()
        return Pair(vx, vy)
    }
    
    /**
     * 预测下一帧位置
     */
    private fun predictNextPosition(roi: TrackedROI, imageWidth: Int, imageHeight: Int): Rect {
        val offsetX = roi.velocityX.toInt()
        val offsetY = roi.velocityY.toInt()
        
        val newRect = Rect(
            (roi.rect.left + offsetX).coerceIn(0, imageWidth - MIN_ROI_SIZE),
            (roi.rect.top + offsetY).coerceIn(0, imageHeight - MIN_ROI_SIZE),
            (roi.rect.right + offsetX).coerceIn(MIN_ROI_SIZE, imageWidth),
            (roi.rect.bottom + offsetY).coerceIn(MIN_ROI_SIZE, imageHeight)
        )
        
        return newRect
    }
    
    /**
     * 扩展 ROI 区域
     * 适当扩大检测区域，应对运动模糊和边界不准确
     */
    private fun expandROI(rect: Rect, imageWidth: Int, imageHeight: Int): Rect {
        val expandX = (rect.width() * ROI_EXPANSION_RATIO).toInt()
        val expandY = (rect.height() * ROI_EXPANSION_RATIO).toInt()
        
        return Rect(
            max(0, rect.left - expandX),
            max(0, rect.top - expandY),
            min(imageWidth, rect.right + expandX),
            min(imageHeight, rect.bottom + expandY)
        )
    }
    
    /**
     * 获取稳定的 ROI（基于历史平均）
     */
    fun getStableROI(): Rect? {
        if (historyRects.size < 3) return currentROI?.rect
        
        val recentRects = historyRects.takeLast(5)
        val avgLeft = recentRects.map { it.left }.average().toInt()
        val avgTop = recentRects.map { it.top }.average().toInt()
        val avgRight = recentRects.map { it.right }.average().toInt()
        val avgBottom = recentRects.map { it.bottom }.average().toInt()
        
        return Rect(avgLeft, avgTop, avgRight, avgBottom)
    }
    
    /**
     * 判断是否应该进行全图扫描
     * 当跟踪丢失或空闲时返回 true
     */
    fun shouldFullScan(): Boolean {
        val state = getState()
        return state == TrackingState.IDLE || state == TrackingState.LOST
    }
}

/**
 * 多目标 ROI 跟踪器
 * 支持同时跟踪多个条码
 */
class MultiROITracker(private val maxTargets: Int = 5) {
    
    private val trackers = mutableMapOf<String, ROITracker>()
    private var nextId = 0
    
    /**
     * 更新所有目标
     */
    fun update(detectedRects: List<Pair<Rect, String?>>, imageWidth: Int, imageHeight: Int): List<ROITracker.TrackedROI> {
        val results = mutableListOf<ROITracker.TrackedROI>()
        val matchedIds = mutableSetOf<String>()
        
        // 尝试匹配现有跟踪器
        for ((rect, rawValue) in detectedRects) {
            val matchedTracker = findBestMatchingTracker(rect)
            
            if (matchedTracker != null) {
                val roi = matchedTracker.second.update(rect, imageWidth, imageHeight)
                if (roi != null) {
                    results.add(roi)
                    matchedIds.add(matchedTracker.first)
                }
            } else if (trackers.size < maxTargets) {
                // 创建新跟踪器
                val id = "target_${nextId++}"
                val tracker = ROITracker()
                val roi = tracker.update(rect, imageWidth, imageHeight)
                if (roi != null) {
                    trackers[id] = tracker
                    results.add(roi)
                    matchedIds.add(id)
                }
            }
        }
        
        // 更新未匹配的跟踪器（预测模式）
        for ((id, tracker) in trackers) {
            if (id !in matchedIds) {
                tracker.update(null, imageWidth, imageHeight)
            }
        }
        
        // 移除丢失的跟踪器
        val lostIds = trackers.filter { it.value.getState() == ROITracker.TrackingState.LOST }.keys
        lostIds.forEach { trackers.remove(it) }
        
        return results
    }
    
    private fun findBestMatchingTracker(rect: Rect): Pair<String, ROITracker>? {
        var bestMatch: Pair<String, ROITracker>? = null
        var bestIoU = 0.3f
        
        for ((id, tracker) in trackers) {
            val currentROI = tracker.getCurrentROI() ?: continue
            val iou = calculateIoU(currentROI, rect)
            if (iou > bestIoU) {
                bestIoU = iou
                bestMatch = Pair(id, tracker)
            }
        }
        
        return bestMatch
    }
    
    private fun calculateIoU(rect1: Rect, rect2: Rect): Float {
        val intersectLeft = max(rect1.left, rect2.left)
        val intersectTop = max(rect1.top, rect2.top)
        val intersectRight = min(rect1.right, rect2.right)
        val intersectBottom = min(rect1.bottom, rect2.bottom)
        
        if (intersectLeft >= intersectRight || intersectTop >= intersectBottom) {
            return 0f
        }
        
        val intersectArea = (intersectRight - intersectLeft) * (intersectBottom - intersectTop)
        val area1 = rect1.width() * rect1.height()
        val area2 = rect2.width() * rect2.height()
        val unionArea = area1 + area2 - intersectArea
        
        return if (unionArea > 0) intersectArea.toFloat() / unionArea else 0f
    }
    
    fun reset() {
        trackers.values.forEach { it.reset() }
        trackers.clear()
    }
}
