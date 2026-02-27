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
 * 妫€娴嬪埌鐨勬潯鐮佷俊鎭?
 * @param boundingBox 杈圭晫妗?(褰掍竴鍖栧潗鏍?0-1)
 * @param rawValue 鏉＄爜鍊?
 * @param format 鏉＄爜鏍煎紡
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
 * 鏉＄爜妫€娴嬫鍙犲姞灞?
 * 鍦ㄧ浉鏈洪瑙堜笂缁樺埗瀹炴椂妫€娴嬪埌鐨勬潯鐮佽竟鐣屾
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
    // 鍔ㄧ敾鏁堟灉锛氳竟妗嗛€忔槑搴?
    val alpha by animateFloatAsState(
        targetValue = if (detectedBarcodes.isNotEmpty()) 1f else 0f,
        animationSpec = tween(durationMillis = 150),
        label = "boxAlpha"
    )
    
    Canvas(modifier = modifier.fillMaxSize()) {
        detectedBarcodes.forEach { barcode ->
            // 杞崲鍧愭爣锛氫粠鍥惧儚鍧愭爣绯昏浆鎹㈠埌Canvas鍧愭爣绯?
            val rect = transformBarcodeRect(
                barcode = barcode,
                canvasWidth = size.width,
                canvasHeight = size.height
            )
            
            // 缁樺埗鍗婇€忔槑濉厖
            drawRoundRect(
                color = boxColor.copy(alpha = 0.1f * alpha),
                topLeft = Offset(rect.left, rect.top),
                size = Size(rect.right - rect.left, rect.bottom - rect.top),
                cornerRadius = CornerRadius(8f, 8f)
            )
            
            // 缁樺埗杈规
            drawRoundRect(
                color = boxColor.copy(alpha = 0.6f * alpha),
                topLeft = Offset(rect.left, rect.top),
                size = Size(rect.right - rect.left, rect.bottom - rect.top),
                cornerRadius = CornerRadius(8f, 8f),
                style = Stroke(width = strokeWidth)
            )
            
            // 缁樺埗鍥涗釜瑙掔殑寮鸿皟绾?
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
 * 杞崲鏉＄爜杈圭晫妗嗗潗鏍囧埌Canvas鍧愭爣绯?
 */
private fun transformBarcodeRect(
    barcode: DetectedBarcode,
    canvasWidth: Float,
    canvasHeight: Float
): TransformedRect {
    val imageWidth = barcode.imageWidth.toFloat()
    val imageHeight = barcode.imageHeight.toFloat()
    
    // 鏍规嵁鏃嬭浆瑙掑害璋冩暣鍧愭爣
    val (transformedLeft, transformedTop, transformedRight, transformedBottom) = when (barcode.rotationDegrees) {
        90 -> {
            // 鍥惧儚鏃嬭浆90搴︼細x鍜寉浜ゆ崲锛寉杞寸炕杞?
            val newLeft = barcode.top
            val newTop = imageWidth - barcode.right
            val newRight = barcode.bottom
            val newBottom = imageWidth - barcode.left
            Quadruple(newLeft, newTop, newRight, newBottom)
        }
        180 -> {
            // 鍥惧儚鏃嬭浆180搴︼細x鍜寉閮界炕杞?
            val newLeft = imageWidth - barcode.right
            val newTop = imageHeight - barcode.bottom
            val newRight = imageWidth - barcode.left
            val newBottom = imageHeight - barcode.top
            Quadruple(newLeft, newTop, newRight, newBottom)
        }
        270 -> {
            // 鍥惧儚鏃嬭浆270搴︼細x鍜寉浜ゆ崲锛寈杞寸炕杞?
            val newLeft = imageHeight - barcode.bottom
            val newTop = barcode.left
            val newRight = imageHeight - barcode.top
            val newBottom = barcode.right
            Quadruple(newLeft, newTop, newRight, newBottom)
        }
        else -> {
            // 0搴︽垨鍏朵粬锛氫笉鍙?
            Quadruple(barcode.left, barcode.top, barcode.right, barcode.bottom)
        }
    }
    
    // 鏍规嵁鏃嬭浆瑙掑害纭畾瀹為檯鐨勫浘鍍忓昂瀵?
    val (actualImageWidth, actualImageHeight) = when (barcode.rotationDegrees) {
        90, 270 -> imageHeight to imageWidth
        else -> imageWidth to imageHeight
    }
    
    // 璁＄畻缂╂斁姣斾緥锛堜繚鎸佸楂樻瘮锛屽～鍏呮暣涓狢anvas锛?
    val scaleX = canvasWidth / actualImageWidth
    val scaleY = canvasHeight / actualImageHeight
    val scale = maxOf(scaleX, scaleY)
    
    // 璁＄畻鍋忕Щ閲忥紙灞呬腑鏄剧ず锛?
    val offsetX = (canvasWidth - actualImageWidth * scale) / 2
    val offsetY = (canvasHeight - actualImageHeight * scale) / 2
    
    // 搴旂敤缂╂斁鍜屽亸绉?
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
 * 缁樺埗鍥涗釜瑙掔殑寮鸿皟绾?
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
    
    // 宸︿笂瑙?
    path.moveTo(left, top + cornerLength)
    path.lineTo(left, top)
    path.lineTo(left + cornerLength, top)
    
    // 鍙充笂瑙?
    path.moveTo(right - cornerLength, top)
    path.lineTo(right, top)
    path.lineTo(right, top + cornerLength)
    
    // 鍙充笅瑙?
    path.moveTo(right, bottom - cornerLength)
    path.lineTo(right, bottom)
    path.lineTo(right - cornerLength, bottom)
    
    // 宸︿笅瑙?
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
 * 鎵弿妗嗗紩瀵煎彔鍔犲眰
 * 鏄剧ず鎵弿鍖哄煙鐨勫紩瀵兼
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
        
        // 缁樺埗鍥涗釜瑙?
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
        
        // 缁樺埗杈规铏氱嚎
        drawRoundRect(
            color = guideColor,
            topLeft = Offset(left, top),
            size = Size(scanSize, scanSize),
            cornerRadius = CornerRadius(12f, 12f),
            style = Stroke(width = 1f)
        )
    }
}
