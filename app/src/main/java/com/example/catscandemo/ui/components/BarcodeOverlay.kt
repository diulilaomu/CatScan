package com.example.catscandemo.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.DrawScope

/**
 * 检测到的条码信息
 *
 * 坐标统一为“转正后图像”坐标系：imageWidth/imageHeight 即转正后的宽高，
 * 两处识别通道（ML Kit 全帧、OpenCV 裁剪）输出前都已换算到该坐标系，
 * overlay 无需再做旋转。
 */
data class DetectedBarcode(
    val left: Float,
    val top: Float,
    val right: Float,
    val bottom: Float,
    val rawValue: String?,
    val format: Int,
    val imageWidth: Int,
    val imageHeight: Int
)

/**
 * 条码检测框叠加层
 * 在相机预览上绘制实时检测到的条码边界框
 */
@Composable
fun BarcodeOverlay(
    detectedBarcodes: List<DetectedBarcode>,
    modifier: Modifier = Modifier,
    boxColor: Color = Color(0xFF4CAF50),
    cornerColor: Color = Color(0xFF2196F3),
    strokeWidth: Float = 3f,
    cornerLength: Float = 30f
) {
    // 动画效果：边框透明度
    val alpha by animateFloatAsState(
        targetValue = if (detectedBarcodes.isNotEmpty()) 1f else 0f,
        animationSpec = tween(durationMillis = 150),
        label = "boxAlpha"
    )

    Canvas(modifier = modifier.fillMaxSize()) {
        detectedBarcodes.forEach { barcode ->
            // 转换坐标：从图像坐标系转换到Canvas坐标系
            val rect = transformBarcodeRect(
                barcode = barcode,
                canvasWidth = size.width,
                canvasHeight = size.height
            )

            // 绘制半透明填充
            drawRoundRect(
                color = boxColor.copy(alpha = 0.1f * alpha),
                topLeft = Offset(rect.left, rect.top),
                size = Size(rect.right - rect.left, rect.bottom - rect.top),
                cornerRadius = CornerRadius(8f, 8f)
            )

            // 绘制边框
            drawRoundRect(
                color = boxColor.copy(alpha = 0.6f * alpha),
                topLeft = Offset(rect.left, rect.top),
                size = Size(rect.right - rect.left, rect.bottom - rect.top),
                cornerRadius = CornerRadius(8f, 8f),
                style = Stroke(width = strokeWidth)
            )

            // 绘制四个角的强调角
            drawCorners(
                left = rect.left,
                top = rect.top,
                right = rect.right,
                bottom = rect.bottom,
                color = cornerColor.copy(alpha = alpha),
                strokeWidth = strokeWidth * 1.5f,
                cornerLength = cornerLength
            )
        }
    }
}

/**
 * 转换条码边界框坐标到Canvas坐标系
 *
 * 检测框已是转正后图像坐标，与 PreviewView 默认的 FILL_CENTER
 * （等比放大填满 + 居中裁切）显示方式对应：取较大缩放比并居中偏移即可。
 */
private fun transformBarcodeRect(
    barcode: DetectedBarcode,
    canvasWidth: Float,
    canvasHeight: Float
): TransformedRect {
    val imageWidth = barcode.imageWidth.toFloat()
    val imageHeight = barcode.imageHeight.toFloat()

    // 计算缩放比例（保持宽高比，填满整个Canvas）
    val scaleX = canvasWidth / imageWidth
    val scaleY = canvasHeight / imageHeight
    val scale = maxOf(scaleX, scaleY)

    // 计算偏移量（居中显示）
    val offsetX = (canvasWidth - imageWidth * scale) / 2
    val offsetY = (canvasHeight - imageHeight * scale) / 2

    // 应用缩放和偏移
    return TransformedRect(
        left = barcode.left * scale + offsetX,
        top = barcode.top * scale + offsetY,
        right = barcode.right * scale + offsetX,
        bottom = barcode.bottom * scale + offsetY
    )
}

private data class TransformedRect(val left: Float, val top: Float, val right: Float, val bottom: Float)

/**
 * 绘制四个角的强调角
 */
private fun DrawScope.drawCorners(
    left: Float,
    top: Float,
    right: Float,
    bottom: Float,
    color: Color,
    strokeWidth: Float,
    cornerLength: Float
) {
    val path = Path()

    // 左上角
    path.moveTo(left, top + cornerLength)
    path.lineTo(left, top)
    path.lineTo(left + cornerLength, top)

    // 右上角
    path.moveTo(right - cornerLength, top)
    path.lineTo(right, top)
    path.lineTo(right, top + cornerLength)

    // 右下角
    path.moveTo(right, bottom - cornerLength)
    path.lineTo(right, bottom)
    path.lineTo(right - cornerLength, bottom)

    // 左下角
    path.moveTo(left + cornerLength, bottom)
    path.lineTo(left, bottom)
    path.lineTo(left, bottom - cornerLength)

    drawPath(
        path = path,
        color = color,
        style = Stroke(width = strokeWidth)
    )
}
