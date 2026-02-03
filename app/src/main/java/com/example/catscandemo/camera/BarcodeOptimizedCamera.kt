package com.example.catscandemo.camera

import android.graphics.Rect
import android.hardware.camera2.CaptureRequest
import android.util.Log
import android.util.Size
import androidx.camera.camera2.interop.Camera2Interop
import androidx.camera.camera2.interop.ExperimentalCamera2Interop
import androidx.camera.core.Camera
import androidx.camera.core.CameraControl
import androidx.camera.core.FocusMeteringAction
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.core.SurfaceOrientedMeteringPointFactory
import java.util.concurrent.TimeUnit

/**
 * 条码场景优化的相机配置器
 * 
 * 针对条码扫描场景优化：
 * - 连续自动对焦 (CAF) 优先保证条纹清晰
 * - 自动曝光优化，避免过曝/欠曝
 * - 低延迟预览与高帧率采集
 * - 快速白平衡响应
 */
object BarcodeOptimizedCamera {
    
    private const val TAG = "BarcodeOptimizedCamera"
    
    /**
     * 相机配置
     */
    data class CameraConfig(
        val targetResolution: Size = Size(1920, 1080),  // 目标分辨率
        val enableHighFrameRate: Boolean = true,        // 启用高帧率
        val targetFrameRate: Int = 30,                  // 目标帧率
        val enableContinuousAF: Boolean = true,         // 连续自动对焦
        val enableBarcodeAE: Boolean = true,            // 条码场景曝光优化
        val enableFastAWB: Boolean = true,              // 快速白平衡
        val afRegionWeight: Float = 0.8f                // 对焦区域权重
    )
    
    /**
     * 配置 Preview 用于条码扫描场景
     */
    @OptIn(ExperimentalCamera2Interop::class)
    fun configurePreview(
        previewBuilder: Preview.Builder,
        config: CameraConfig = CameraConfig()
    ): Preview.Builder {
        val extender = Camera2Interop.Extender(previewBuilder)
        
        // 设置连续自动对焦
        if (config.enableContinuousAF) {
            extender.setCaptureRequestOption(
                CaptureRequest.CONTROL_AF_MODE,
                CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_PICTURE
            )
        }
        
        // 设置自动曝光模式
        if (config.enableBarcodeAE) {
            extender.setCaptureRequestOption(
                CaptureRequest.CONTROL_AE_MODE,
                CaptureRequest.CONTROL_AE_MODE_ON
            )
            // 设置曝光补偿为略微欠曝，有助于提高条纹对比度
            extender.setCaptureRequestOption(
                CaptureRequest.CONTROL_AE_EXPOSURE_COMPENSATION,
                -1 // 略微欠曝
            )
        }
        
        // 设置快速白平衡
        if (config.enableFastAWB) {
            extender.setCaptureRequestOption(
                CaptureRequest.CONTROL_AWB_MODE,
                CaptureRequest.CONTROL_AWB_MODE_AUTO
            )
        }
        
        // 降低延迟
        extender.setCaptureRequestOption(
            CaptureRequest.CONTROL_MODE,
            CaptureRequest.CONTROL_MODE_AUTO
        )
        
        return previewBuilder
    }
    
    /**
     * 配置 ImageAnalysis 用于高帧率条码检测
     */
    @OptIn(ExperimentalCamera2Interop::class)
    fun configureImageAnalysis(
        analysisBuilder: ImageAnalysis.Builder,
        config: CameraConfig = CameraConfig()
    ): ImageAnalysis.Builder {
        // 设置目标分辨率
        analysisBuilder.setTargetResolution(config.targetResolution)
        
        // 保留最新帧策略，减少延迟
        analysisBuilder.setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
        
        // 设置输出图像格式为 YUV，更高效
        analysisBuilder.setOutputImageFormat(ImageAnalysis.OUTPUT_IMAGE_FORMAT_YUV_420_888)
        
        val extender = Camera2Interop.Extender(analysisBuilder)
        
        // 连续自动对焦
        if (config.enableContinuousAF) {
            extender.setCaptureRequestOption(
                CaptureRequest.CONTROL_AF_MODE,
                CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_VIDEO
            )
        }
        
        // 场景模式设置为条码（如果支持）
        extender.setCaptureRequestOption(
            CaptureRequest.CONTROL_SCENE_MODE,
            CaptureRequest.CONTROL_SCENE_MODE_BARCODE
        )
        
        // 噪点降低模式 - 使用快速模式减少延迟
        extender.setCaptureRequestOption(
            CaptureRequest.NOISE_REDUCTION_MODE,
            CaptureRequest.NOISE_REDUCTION_MODE_FAST
        )
        
        // 边缘增强 - 有助于条码边缘清晰
        extender.setCaptureRequestOption(
            CaptureRequest.EDGE_MODE,
            CaptureRequest.EDGE_MODE_FAST
        )
        
        return analysisBuilder
    }
    
    /**
     * 触发区域对焦
     * 在检测到条码区域后，对该区域进行精确对焦
     */
    fun focusOnRegion(
        camera: Camera,
        previewWidth: Int,
        previewHeight: Int,
        barcodeRect: Rect,
        config: CameraConfig = CameraConfig()
    ) {
        val cameraControl = camera.cameraControl
        
        // 计算对焦点（条码区域中心）
        val centerX = (barcodeRect.left + barcodeRect.right) / 2f / previewWidth
        val centerY = (barcodeRect.top + barcodeRect.bottom) / 2f / previewHeight
        
        // 创建测光点
        val factory = SurfaceOrientedMeteringPointFactory(
            previewWidth.toFloat(),
            previewHeight.toFloat()
        )
        val point = factory.createPoint(
            centerX * previewWidth,
            centerY * previewHeight
        )
        
        // 构建对焦动作
        val action = FocusMeteringAction.Builder(point, FocusMeteringAction.FLAG_AF or FocusMeteringAction.FLAG_AE)
            .setAutoCancelDuration(2, TimeUnit.SECONDS)
            .build()
        
        cameraControl.startFocusAndMetering(action)
            .addListener({
                Log.d(TAG, "区域对焦完成: rect=$barcodeRect")
            }, { it.run() })
    }
    
    /**
     * 设置变焦以放大条码区域
     */
    fun zoomToRegion(
        camera: Camera,
        previewWidth: Int,
        previewHeight: Int,
        barcodeRect: Rect,
        maxZoomRatio: Float = 2.5f
    ) {
        val cameraControl = camera.cameraControl
        val cameraInfo = camera.cameraInfo
        
        // 计算条码区域占预览的比例
        val barcodeWidth = (barcodeRect.right - barcodeRect.left).toFloat()
        val barcodeHeight = (barcodeRect.bottom - barcodeRect.top).toFloat()
        val barcodeRatio = maxOf(barcodeWidth / previewWidth, barcodeHeight / previewHeight)
        
        // 如果条码区域太小，适当放大
        if (barcodeRatio < 0.3f) {
            val targetRatio = 0.4f
            val zoomRatio = (targetRatio / barcodeRatio).coerceIn(1f, maxZoomRatio)
            
            cameraControl.setZoomRatio(zoomRatio)
                .addListener({
                    Log.d(TAG, "变焦到: $zoomRatio")
                }, { it.run() })
        }
    }
    
    /**
     * 重置相机到默认状态
     */
    fun resetCamera(camera: Camera) {
        val cameraControl = camera.cameraControl
        cameraControl.setZoomRatio(1f)
        cameraControl.cancelFocusAndMetering()
    }
    
    /**
     * 开启/关闭闪光灯补光
     */
    fun setTorchEnabled(camera: Camera, enabled: Boolean) {
        camera.cameraControl.enableTorch(enabled)
    }
}
