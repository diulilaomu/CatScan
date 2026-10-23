package com.example.catscandemo.ui.components

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp

/**
 * 文字识别（OCR）采集内核图标
 *
 * 与 QrCodeScanner 同语言：四角扫描括号 + 中间字母 A（即"二维码图标中
 * 二维码部分替换为 A"）；禁用态斜杠由调用处 drawWithContent 叠加，
 * 与二维码开关图标共用同一套画法。
 */
val TextScanIcon: ImageVector by lazy {
    ImageVector.Builder(
        name = "TextScan",
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 24f,
        viewportHeight = 24f
    ).apply {
        // 四角扫描括号
        path(
            stroke = SolidColor(Color.Black),
            strokeLineWidth = 2f,
            strokeLineCap = StrokeCap.Round,
            strokeLineJoin = StrokeJoin.Round
        ) {
            moveTo(3f, 8f)
            lineTo(3f, 5.5f)
            curveTo(3f, 4.12f, 4.12f, 3f, 5.5f, 3f)
            lineTo(8f, 3f)
            moveTo(16f, 3f)
            lineTo(18.5f, 3f)
            curveTo(19.88f, 3f, 21f, 4.12f, 21f, 5.5f)
            lineTo(21f, 8f)
            moveTo(21f, 16f)
            lineTo(21f, 18.5f)
            curveTo(21f, 19.88f, 19.88f, 21f, 18.5f, 21f)
            lineTo(16f, 21f)
            moveTo(8f, 21f)
            lineTo(5.5f, 21f)
            curveTo(4.12f, 21f, 3f, 19.88f, 3f, 18.5f)
            lineTo(3f, 16f)
        }
        // 中间字母 A（两斜笔 + 横笔）
        path(
            stroke = SolidColor(Color.Black),
            strokeLineWidth = 2f,
            strokeLineCap = StrokeCap.Round,
            strokeLineJoin = StrokeJoin.Round
        ) {
            moveTo(8.6f, 17.2f)
            lineTo(12f, 7.4f)
            lineTo(15.4f, 17.2f)
            moveTo(9.9f, 13.9f)
            lineTo(14.1f, 13.9f)
        }
    }.build()
}
