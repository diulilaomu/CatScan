package com.example.catscandemo.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

/**
 * 输入区配色：日间=浅色（surface 系，靠描边与背景区分）；
 * 深色主题=黑色 28% 罩（比深色背景更深一档）。Color.Unspecified 时用该自适应值。
 */
@Composable
private fun fieldContainerColor(override: Color): Color {
    if (override != Color.Unspecified) return override
    val dark = MaterialTheme.colorScheme.surface.luminance() < 0.5f
    return if (dark) Color(0xFF000000).copy(alpha = 0.28f)
    else MaterialTheme.colorScheme.surface.copy(alpha = 0.92f)
}

/** prepend 标签配色：两个主题都用 surfaceVariant 灰（日间=深灰衬托浅色输入区） */
@Composable
private fun prependContainerColor(): Color = MaterialTheme.colorScheme.surfaceVariant

/**
 * 全局紧凑输入框：38dp 单行、细描边圆角、文字垂直居中、默认左对齐。
 * 配色（element-plus 同款）：prepend 标签=灰，输入区=日间浅色/深色主题更深一档。
 * 只有极窄输入（补零位数、前缀、后缀）用 centerText=true 水平居中。
 * 提示文字（placeholder）空值时显示在框内左侧。
 *
 * 多行模式禁止外挂 verticalScroll：LazyColumn 条目内高度约束无限，
 * 可滚动组件遇无限高度约束会直接 IllegalStateException 闪退（历史行展开闪退根因）。
 */
@Composable
fun CompactTextField(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    singleLine: Boolean = true,
    keyboardType: KeyboardType = KeyboardType.Text,
    textStyle: TextStyle = MaterialTheme.typography.bodyMedium,
    placeholder: String = "",
    centerText: Boolean = false,
    container: Color = Color.Unspecified,
    shape: RoundedCornerShape = RoundedCornerShape(8.dp)
) {
    val alignment = if (centerText) TextAlign.Center else TextAlign.Start
    Box(
        modifier = modifier
            .then(if (singleLine) Modifier.height(38.dp) else Modifier.heightIn(min = 38.dp, max = 160.dp))
            .background(fieldContainerColor(container), shape)
            .border(
                width = 1.dp,
                color = MaterialTheme.colorScheme.outlineVariant,
                shape = shape
            )
            .padding(horizontal = 10.dp, vertical = 4.dp),
        contentAlignment = if (centerText) Alignment.Center else Alignment.CenterStart
    ) {
        if (value.isEmpty() && placeholder.isNotEmpty()) {
            Text(
                text = placeholder,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                textAlign = alignment,
                modifier = Modifier.fillMaxWidth()
            )
        }
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            singleLine = singleLine,
            textStyle = textStyle.copy(
                color = MaterialTheme.colorScheme.onSurface,
                textAlign = alignment
            ),
            cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
            keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
            modifier = Modifier.fillMaxWidth()
        )
    }
}

/**
 * 左标签 + 右输入框（element-plus prepend 拼接样式）：
 * 「位数:│ 2 」——灰色标签盒 + 浅色输入区（element-plus 同款深浅搭配），
 * 外侧圆角、拼接处直角，视觉上是一个控件。
 * 标签用半角冒号（全角冒号右侧自带空隙，会导致文字左右留白不等）。
 * label 自动补冒号；多行输入时标签盒随内容等高（IntrinsicSize）。
 */
@Composable
fun LabeledCompactField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    fieldModifier: Modifier = Modifier,
    singleLine: Boolean = true,
    keyboardType: KeyboardType = KeyboardType.Text,
    textStyle: TextStyle = MaterialTheme.typography.bodyMedium,
    placeholder: String = "",
    centerText: Boolean = false,
    container: Color = Color.Unspecified
) {
    val prependShape = RoundedCornerShape(topStart = 8.dp, bottomStart = 8.dp)
    val fieldShape = RoundedCornerShape(topEnd = 8.dp, bottomEnd = 8.dp)
    Row(
        modifier = modifier.height(IntrinsicSize.Min),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // prepend 标签盒：浅色调，与深色输入区、任意页面/抽屉背景区分
        Box(
            modifier = Modifier
                .fillMaxHeight()
                .background(prependContainerColor(), prependShape)
                .border(
                    width = 1.dp,
                    color = MaterialTheme.colorScheme.outlineVariant,
                    shape = prependShape
                )
                .padding(horizontal = 10.dp, vertical = 4.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "$label:",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1
            )
        }
        // 输入框：拼接处直角；默认占满行内剩余宽度，显式传 fieldModifier 时以传入为准
        val effectiveFieldModifier =
            if (fieldModifier == Modifier) Modifier.weight(1f) else fieldModifier
        CompactTextField(
            value = value,
            onValueChange = onValueChange,
            modifier = effectiveFieldModifier,
            singleLine = singleLine,
            keyboardType = keyboardType,
            textStyle = textStyle,
            placeholder = placeholder,
            centerText = centerText,
            container = container,
            shape = fieldShape
        )
    }
}
