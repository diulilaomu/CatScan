package com.example.catscandemo.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.example.catscandemo.domain.model.MarkMode

/**
 * 模板类型徽标：序号模板=蓝，房间号模板=绿。
 * 用于模板管理列表、主页模板选择器展开项，一眼区分两种录入标记模式。
 */
@Composable
fun TemplateTypeTag(markMode: MarkMode, modifier: Modifier = Modifier) {
    val isSeq = markMode == MarkMode.SEQ
    val bg = if (isSeq) Color(0xFFBBDEFB) else Color(0xFFC8E6C9)
    val fg = if (isSeq) Color(0xFF0D47A1) else Color(0xFF1B5E20)
    Text(
        text = if (isSeq) "序号" else "房间号",
        style = MaterialTheme.typography.labelSmall,
        color = fg,
        maxLines = 1,
        modifier = modifier
            .background(bg, RoundedCornerShape(4.dp))
            .padding(horizontal = 4.dp, vertical = 1.dp)
    )
}

/** 模板类型圆点（8dp）：中亮度蓝/绿，选择器日/夜间（primaryContainer）底色上都清晰 */
@Composable
fun TemplateTypeDot(markMode: MarkMode, modifier: Modifier = Modifier) {
    val color = if (markMode == MarkMode.SEQ) Color(0xFF42A5F5) else Color(0xFF66BB6A)
    androidx.compose.foundation.layout.Box(
        modifier = modifier
            .size(8.dp)
            .background(color, CircleShape)
    )
}
