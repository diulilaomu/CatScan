package com.example.catscandemo.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DividerDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.catscandemo.utils.OcrRegexMatcher

/** 一行图片识别文字：原文 + 正则命中片段（命中为空 = 该行不可录入） */
private class OcrLineUi(val text: String, val matches: List<String>)

/**
 * 相册图片文字识别结果弹窗
 *
 * 逐行展示识别文字；符合正则的片段高亮，点击即录入（可连续点选多条）；
 * 未命中行灰字仅展示。未配置正则 = 整行皆为可点命中项。
 */
@OptIn(ExperimentalLayoutApi::class, androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
fun OcrImageResultDialog(
    lines: List<String>,
    ocrRegex: String,
    onPickText: (String) -> Unit,
    onDismiss: () -> Unit
) {
    val lineUi = remember(lines, ocrRegex) {
        lines.map { line -> OcrLineUi(line, OcrRegexMatcher.findAllMatches(line, ocrRegex)) }
    }
    val hitCount = lineUi.sumOf { it.matches.size }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("图片识别结果") },
        text = {
            Column {
                Text(
                    text = "共 ${lineUi.size} 行 · 命中 $hitCount 处" +
                            if (ocrRegex.isBlank()) "（未配置正则，整行可录入）" else "",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(8.dp))
                LazyColumn(
                    modifier = Modifier
                        .heightIn(max = 380.dp)
                        .fillMaxWidth()
                    ) {
                    items(items = lineUi) { line ->
                        Column(Modifier.fillMaxWidth()) {
                            if (line.matches.isEmpty()) {
                                Text(
                                    text = line.text,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                                    modifier = Modifier.padding(vertical = 6.dp)
                                )
                            } else {
                                FlowRow(
                                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                                    verticalArrangement = Arrangement.spacedBy(4.dp),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 6.dp)
                                ) {
                                    // 未命中的行整行灰字；有命中的行只展示命中片段（可点），
                                    // 避免长行里夹杂高亮碎片反而难点
                                    line.matches.forEach { match ->
                                        Surface(
                                            shape = MaterialTheme.shapes.small,
                                            color = MaterialTheme.colorScheme.primaryContainer,
                                            onClick = { onPickText(match) }
                                        ) {
                                            Text(
                                                text = match,
                                                style = MaterialTheme.typography.bodyMedium.copy(
                                                    fontFamily = FontFamily.Monospace,
                                                    fontWeight = FontWeight.Medium
                                                ),
                                                color = MaterialTheme.colorScheme.onPrimaryContainer,
                                                modifier = Modifier.padding(
                                                    horizontal = 8.dp, vertical = 5.dp
                                                )
                                            )
                                        }
                                    }
                                }
                            }
                            HorizontalDivider(
                                thickness = DividerDefaults.Thickness,
                                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                            )
                        }
                    }
                }
                if (hitCount == 0 && ocrRegex.isNotBlank()) {
                    Spacer(Modifier.height(6.dp))
                    Text(
                        text = "没有符合正则的文字，可在左下角调整识别规则",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("关闭") }
        }
    )
}
