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
 * @param boundingBox 边界框 (归一化坐标 0-1)
 * @param rawValue 条码值
 * @param format 条码格式
 */
data class DetectedBarcode(
    val left: Float,
    val top: Float,
    val right: Float,
    val bottom: Float,
    val rawValue: String?,
    val format: Int,
    val imageWidth: Int,
    val imageHeight: Int,
    val rotationDegrees: Int
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
            
            // 绘制四个角的强调线
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
 */
private fun transformBarcodeRect(
    barcode: DetectedBarcode,
    canvasWidth: Float,
    canvasHeight: Float
): TransformedRect {
    val imageWidth = barcode.imageWidth.toFloat()
    val imageHeight = barcode.imageHeight.toFloat()
    
    // 根据旋转角度调整坐标
    val (transformedLeft, transformedTop, transformedRight, transformedBottom) = when (barcode.rotationDegrees) {
        90 -> {
            // 图像旋转90度：x和y交换，y轴翻转
            val newLeft = barcode.top
            val newTop = imageWidth - barcode.right
            val newRight = barcode.bottom
            val newBottom = imageWidth - barcode.left
            Quadruple(newLeft, newTop, newRight, newBottom)
        }
        180 -> {
            // 图像旋转180度：x和y都翻转
            val newLeft = imageWidth - barcode.right
            val newTop = imageHeight - barcode.bottom
            val newRight = imageWidth - barcode.left
            val newBottom = imageHeight - barcode.top
            Quadruple(newLeft, newTop, newRight, newBottom)
        }
        270 -> {
            // 图像旋转270度：x和y交换，x轴翻转
            val newLeft = imageHeight - barcode.bottom
            val newTop = barcode.left
            val newRight = imageHeight - barcode.top
            val newBottom = barcode.right
            Quadruple(newLeft, newTop, newRight, newBottom)
        }
        else -> {
            // 0度或其他：不变
            Quadruple(barcode.left, barcode.top, barcode.right, barcode.bottom)
        }
    }
    
    // 根据旋转角度确定实际的图像尺寸
    val (actualImageWidth, actualImageHeight) = when (barcode.rotationDegrees) {
        90, 270 -> imageHeight to imageWidth
        else -> imageWidth to imageHeight
    }
    
    // 计算缩放比例（保持宽高比，填充整个Canvas）
    val scaleX = canvasWidth / actualImageWidth
    val scaleY = canvasHeight / actualImageHeight
    val scale = maxOf(scaleX, scaleY)
    
    // 计算偏移量（居中显示）
    val offsetX = (canvasWidth - actualImageWidth * scale) / 2
    val offsetY = (canvasHeight - actualImageHeight * scale) / 2
    
    // 应用缩放和偏移
    return TransformedRect(
        left = transformedLeft * scale + offsetX,
        top = transformedTop * scale + offsetY,
        right = transformedRight * scale + offsetX,
        bottom = transformedBottom * scale + offsetY
    )
}

private data class Quadruple(val first: Float, val second: Float, val third: Float, val fourth: Float)
private data class TransformedRect(val left: Float, val top: Float, val right: Float, val bottom: Float)

/**
 * 绘制四个角的强调线
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

/**
 * 扫描框引导叠加层
 * 显示扫描区域的引导框
 */
@Composable
fun ScanGuideOverlay(
    modifier: Modifier = Modifier,
    guideColor: Color = Color.White.copy(alpha = 0.5f),
    cornerColor: Color = Color(0xFF2196F3),
    scanAreaRatio: Float = 0.7f
) {
    Canvas(modifier = modifier.fillMaxSize()) {
        val centerX = size.width / 2
        val centerY = size.height / 2
        val scanSize = minOf(size.width, size.height) * scanAreaRatio
        
        val left = centerX - scanSize / 2
        val top = centerY - scanSize / 2
        val right = centerX + scanSize / 2
        val bottom = centerY + scanSize / 2
        
        // 绘制四个角
        val cornerLength = scanSize * 0.1f
        val strokeWidth = 4f
        
        drawCorners(
            left = left,
            top = top,
            right = right,
            bottom = bottom,
            color = cornerColor,
            strokeWidth = strokeWidth,
            cornerLength = cornerLength
        )
        
        // 绘制边框虚线
        drawRoundRect(
            color = guideColor,
            topLeft = Offset(left, top),
            size = Size(scanSize, scanSize),
            cornerRadius = CornerRadius(12f, 12f),
            style = Stroke(width = 1f)
        )
    }
}
