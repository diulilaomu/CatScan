package com.example.catscandemo.ui.components

import android.view.WindowManager
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Button
import androidx.compose.material3.DividerDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.toSize
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.DialogWindowProvider
import com.example.catscandemo.utils.OcrRegexMatcher
import com.example.catscandemo.utils.OcrWordTokenizer
import com.example.catscandemo.utils.OcrWordTokenizer.OcrToken

/**
 * 图片识别结果的 Big Bang 涂抹选词界面（锤子 Big Bang 式交互）
 *
 * 上半区：识别文字按行拆成词块铺开——正则命中块底色高亮；单击切换选中，
 * 手指涂抹（按住划过）只加选；空隙处按下可正常滚动列表。
 * 两半区中间右对齐"插入(N)"：选中块按阅读顺序拼成一行进缓存列表，随后清空全部选中。
 * 下半区：本次会话的缓存行，单击文本框直接弹输入法编辑，右侧垃圾桶删除。
 * 右下角确定=逐条走现有录入链路（模板模式推进游标/查重/上传，文本模式插入文档光标处）；
 * 缓存为空时确定禁用。取消/返回=整次会话丢弃。
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun OcrBangDialog(
    lines: List<String>,
    ocrRegex: String,
    onConfirm: (List<String>) -> Unit,
    onCancel: () -> Unit
) {
    // 会话数据：单张图生命周期，换图（lines 变化）即整体重置
    val rowTokens = remember(lines, ocrRegex) {
        lines.mapNotNull { raw ->
            val line = raw.trim()
            if (line.isEmpty()) return@mapNotNull null
            val ranges = OcrRegexMatcher.findAllMatchRanges(line, ocrRegex)
            OcrWordTokenizer.split(line, ranges)
        }
    }
    val hitCount = remember(lines, ocrRegex) {
        lines.sumOf { OcrRegexMatcher.findAllMatchRanges(it.trim(), ocrRegex).size }
    }

    val cachedItems = remember(lines) { mutableStateListOf<BangCachedItem>() }
    var nextItemId by remember(lines) { mutableStateOf(0L) }

    // 词块选中集合（key = "行:块"）与词块屏幕坐标表（涂抹命中测试用）
    val selected = remember(lines) { mutableStateListOf<String>() }
    val tokenRects = remember(lines) { mutableStateMapOf<String, Rect>() }
    var containerRootOffset by remember { mutableStateOf(Offset.Zero) }

    fun hitTest(rootPos: Offset): String? {
        for ((id, rect) in tokenRects) {
            if (rect.contains(rootPos)) return id
        }
        return null
    }

    fun insertSelected() {
        val sb = StringBuilder()
        rowTokens.forEachIndexed { rowIdx, tokens ->
            tokens.forEachIndexed { tokenIdx, token ->
                if ("$rowIdx:$tokenIdx" in selected) sb.append(token.text)
            }
        }
        val text = sb.toString()
        if (text.isNotBlank()) {
            cachedItems.add(BangCachedItem(nextItemId++, text))
        }
        selected.clear()
    }

    // 对话框是独立窗口，跟随输入法缩放，下半区编辑时整体上移不被软键盘遮挡
    val view = LocalView.current
    SideEffect {
        (view.parent as? DialogWindowProvider)?.window?.setSoftInputMode(
            WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE
        )
    }

    Dialog(
        onDismissRequest = onCancel,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = MaterialTheme.colorScheme.background
        ) {
            Column(Modifier.fillMaxSize().padding(16.dp)) {
                // 标题行
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "图片识别结果",
                        style = MaterialTheme.typography.titleLarge,
                        modifier = Modifier.weight(1f)
                    )
                    IconButton(onClick = onCancel, modifier = Modifier.size(28.dp)) {
                        Icon(
                            imageVector = Icons.Filled.Close,
                            contentDescription = "关闭",
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
                Text(
                    text = "共 ${rowTokens.size} 行 · 命中 $hitCount 处 · 已缓存 ${cachedItems.size} 条",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(8.dp))

                // 上半区：Big Bang 词块墙（涂抹选词）
                val scrollState = rememberScrollState()
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .verticalScroll(scrollState)
                        .onGloballyPositioned { containerRootOffset = it.positionInRoot() }
                        .pointerInput(rowTokens) {
                            val touchSlop = viewConfiguration.touchSlop
                            awaitEachGesture {
                                val down = awaitFirstDown(requireUnconsumed = false)
                                val downRoot = down.position + containerRootOffset
                                val hitId = hitTest(downRoot) ?: return@awaitEachGesture
                                down.consume()
                                var smeared = false
                                var lastRoot = downRoot
                                while (true) {
                                    val event = awaitPointerEvent()
                                    val change = event.changes.firstOrNull { it.id == down.id }
                                        ?: break
                                    if (!change.pressed) {
                                        // 未拖动=单击切换；拖动过=涂抹已沿途加选
                                        if (!smeared) {
                                            if (hitId in selected) selected.remove(hitId)
                                            else selected.add(hitId)
                                        }
                                        break
                                    }
                                    val posRoot = change.position + containerRootOffset
                                    if (!smeared &&
                                        (change.position - down.position).getDistance() > touchSlop
                                    ) {
                                        smeared = true
                                        if (hitId !in selected) selected.add(hitId)
                                    }
                                    if (smeared) {
                                        // 快速划动事件间距大，沿路径插值补采避免漏块
                                        val delta = posRoot - lastRoot
                                        val steps = (delta.getDistance() / 24f).toInt()
                                            .coerceIn(1, 80)
                                        for (i in 1..steps) {
                                            val p = lastRoot + delta * (i.toFloat() / steps)
                                            hitTest(p)?.let { if (it !in selected) selected.add(it) }
                                        }
                                    }
                                    lastRoot = posRoot
                                    // 按在词块上即拦截滚动（滚动只允许从空隙处发起）
                                    event.changes.forEach { if (it.pressed) it.consume() }
                                }
                            }
                        }
                ) {
                    if (rowTokens.isEmpty()) {
                        Text(
                            text = "未识别到文字",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(16.dp)
                        )
                    } else {
                        Column(Modifier.fillMaxWidth().padding(4.dp)) {
                            rowTokens.forEachIndexed { rowIdx, tokens ->
                                FlowRow(
                                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                                    verticalArrangement = Arrangement.spacedBy(10.dp),
                                    modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp)
                                ) {
                                    tokens.forEachIndexed { tokenIdx, token ->
                                        BangToken(
                                            token = token,
                                            selected = "$rowIdx:$tokenIdx" in selected,
                                            onRegisterRect = { rect ->
                                                tokenRects["$rowIdx:$tokenIdx"] = rect
                                            }
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                // 两半区之间：插入按钮（右对齐）；插入后清空全部选中
                Row(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    FilledTonalButton(
                        onClick = { insertSelected() },
                        enabled = selected.isNotEmpty()
                    ) {
                        Text(if (selected.isEmpty()) "插入" else "插入(${selected.size})")
                    }
                }

                HorizontalDivider(
                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                )
                Spacer(Modifier.height(4.dp))

                // 下半区：缓存列表（单击文本直接编辑，右侧垃圾桶删除）
                if (cachedItems.isEmpty()) {
                    Box(
                        modifier = Modifier.weight(1f).fillMaxWidth(),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "选词后点「插入」，内容会按顺序缓存到这里",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                        )
                    }
                } else {
                    BangCacheList(
                        items = cachedItems,
                        onDelete = { cachedItems.remove(it) },
                        modifier = Modifier.weight(1f).fillMaxWidth()
                    )
                }

                // 底部栏：右下角取消/确定；确定提交缓存列表，逐条走现有录入链路
                Row(
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TextButton(onClick = onCancel) { Text("取消") }
                    Spacer(Modifier.width(8.dp))
                    Button(
                        onClick = {
                            onConfirm(cachedItems.map { it.text }.filter { it.isNotBlank() })
                        },
                        enabled = cachedItems.isNotEmpty()
                    ) {
                        Text("确定")
                    }
                }
            }
        }
    }
}

/** 缓存列表项；text 用 mutableState 驱动编辑实时刷新 */
private class BangCachedItem(id: Long, initialText: String) {
    var text by mutableStateOf(initialText)
    val id: Long = id
}

/** 上半区单个词块：未选中=正则命中高亮/普通透明，选中=主色实底反白 */
@Composable
private fun BangToken(
    token: OcrToken,
    selected: Boolean,
    onRegisterRect: (Rect) -> Unit
) {
    val background = when {
        selected -> MaterialTheme.colorScheme.primary
        token.isRegexMatch -> MaterialTheme.colorScheme.primaryContainer
        else -> Color.Transparent
    }
    val contentColor = when {
        selected -> MaterialTheme.colorScheme.onPrimary
        token.isRegexMatch -> MaterialTheme.colorScheme.onPrimaryContainer
        else -> MaterialTheme.colorScheme.onSurface
    }
    Text(
        text = token.text,
        style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Medium),
        color = contentColor,
        modifier = Modifier
            .clip(MaterialTheme.shapes.small)
            .background(background)
            .padding(horizontal = 6.dp, vertical = 4.dp)
            .onGloballyPositioned { coords ->
                onRegisterRect(Rect(coords.positionInRoot(), coords.size.toSize()))
            }
    )
}

/** 缓存列表：序号 + 可直接编辑文本（点击聚焦弹输入法）+ 垃圾桶删除 */
@Composable
private fun BangCacheList(
    items: List<BangCachedItem>,
    onDelete: (BangCachedItem) -> Unit,
    modifier: Modifier = Modifier
) {
    LazyColumn(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        itemsIndexed(items, key = { _, item -> item.id }) { index, item ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "${index + 1}.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.width(24.dp)
                )
                BasicTextField(
                    value = item.text,
                    onValueChange = { item.text = it },
                    textStyle = MaterialTheme.typography.bodyMedium.copy(
                        color = MaterialTheme.colorScheme.onSurface
                    ),
                    cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                    modifier = Modifier.weight(1f).padding(vertical = 6.dp)
                )
                IconButton(onClick = { onDelete(item) }, modifier = Modifier.size(30.dp)) {
                    Icon(
                        imageVector = Icons.Filled.Delete,
                        contentDescription = "删除",
                        tint = Color(0xFFEF5350),
                        modifier = Modifier.size(17.dp)
                    )
                }
            }
            HorizontalDivider(
                thickness = DividerDefaults.Thickness,
                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)
            )
        }
    }
}
