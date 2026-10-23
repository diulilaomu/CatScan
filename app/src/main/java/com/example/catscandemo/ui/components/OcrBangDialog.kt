package com.example.catscandemo.ui.components

import android.view.WindowManager
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.toSize
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.DialogWindowProvider
import com.example.catscandemo.utils.OcrRegexMatcher
import com.example.catscandemo.utils.OcrWordTokenizer
import com.example.catscandemo.utils.OcrWordTokenizer.OcrToken
import kotlinx.coroutines.delay
import kotlin.math.roundToInt

/**
 * 图片识别结果的 Big Bang 涂抹选词界面（锤子 Big Bang 式交互）
 *
 * 悬浮窗居中：9/10 屏宽 × 3/4 屏高。无视图片原文换行：各行词块按阅读顺序
 * 铺成一面连续的词块墙，只在碰到窗口边缘才换行——正则命中块高亮。
 * 手势：单击块=切换选中；涂抹跟随按下瞬间起点块的状态——起点未选中
 * 则路径上全部加选，起点已选中则路径上全部取消；空隙处按下可正常滚动。
 * "插入(N)"小按钮悬浮在上半区右下角（词块墙尾部留一行空行给它让位）：
 * 选中块按阅读顺序拼成一行进缓存列表，随后清空全部选中。
 * 下半区（占 1/3）：本次会话的缓存行，单击文本框直接弹输入法编辑（自动滚到
 * 可见区），右侧垃圾桶删除。
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
    // 会话数据：单张图生命周期，换图（lines 变化）即整体重置。
    // 不按行分段，全部行平铺成一个词块序列（下标即阅读顺序）
    val allTokens = remember(lines, ocrRegex) {
        lines.flatMap { raw ->
            val line = raw.trim()
            if (line.isEmpty()) emptyList()
            else OcrWordTokenizer.split(line, OcrRegexMatcher.findAllMatchRanges(line, ocrRegex))
        }
    }
    val hitCount = remember(lines, ocrRegex) {
        lines.sumOf { OcrRegexMatcher.findAllMatchRanges(it.trim(), ocrRegex).size }
    }

    val cachedItems = remember(lines) { mutableStateListOf<BangCachedItem>() }
    var nextItemId by remember(lines) { mutableStateOf(0L) }

    // 词块选中集合（下标）与词块屏幕坐标表（涂抹命中测试用）
    val selected = remember(lines) { mutableStateListOf<Int>() }
    val tokenRects = remember(lines) { mutableStateMapOf<Int, Rect>() }
    var containerRootOffset by remember { mutableStateOf(Offset.Zero) }
    var insertButtonRect by remember { mutableStateOf<Rect?>(null) }

    fun hitTest(rootPos: Offset): Int? {
        for ((idx, rect) in tokenRects) {
            if (rect.contains(rootPos)) return idx
        }
        return null
    }

    fun applyTo(idx: Int, add: Boolean) {
        if (add) {
            if (idx !in selected) selected.add(idx)
        } else {
            selected.remove(idx)
        }
    }

    fun insertSelected() {
        val sb = StringBuilder()
        for (idx in allTokens.indices) {
            if (idx in selected) sb.append(allTokens[idx].text)
        }
        val text = sb.toString()
        if (text.isNotBlank()) {
            cachedItems.add(BangCachedItem(nextItemId++, text))
        }
        selected.clear()
    }

    // 对话框独立窗口：跟随输入法缩放。Surface 按比例占窗口（9/10 宽 × 3/4 高），
    // 键盘弹出时窗口变小 → 窗体自动抬升到输入法上方，收起后自动恢复
    val view = LocalView.current
    @Suppress("DEPRECATION")
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
            modifier = Modifier.fillMaxWidth(0.9f).fillMaxHeight(0.75f),
            shape = MaterialTheme.shapes.large,
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
                    text = "共 ${allTokens.size} 块 · 命中 $hitCount 处 · 已缓存 ${cachedItems.size} 条",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(8.dp))

                // 上半区（占 2/3）：Big Bang 词块墙（涂抹选词）
                val scrollState = rememberScrollState()
                var contentHeightPx by remember { mutableIntStateOf(0) }
                var viewportHeightPx by remember { mutableIntStateOf(0) }
                Box(
                    modifier = Modifier
                        .weight(2f)
                        .fillMaxWidth()
                        .onGloballyPositioned { coords ->
                            containerRootOffset = coords.positionInRoot()
                            viewportHeightPx = coords.size.height
                        }
                        .pointerInput(allTokens) {
                            val touchSlop = viewConfiguration.touchSlop
                            awaitEachGesture {
                                val down = awaitFirstDown(requireUnconsumed = false)
                                val downRoot = down.position + containerRootOffset
                                // 落点在悬浮插入按钮上：交给按钮自己处理点击
                                if (insertButtonRect?.contains(downRoot) == true) {
                                    return@awaitEachGesture
                                }
                                val hitId = hitTest(downRoot) ?: return@awaitEachGesture
                                // 涂抹语义在按下瞬间定格：起点未选中=加选模式，已选中=取消模式
                                val dragAdd = hitId !in selected
                                down.consume()
                                var smeared = false
                                var lastRoot = downRoot
                                while (true) {
                                    val event = awaitPointerEvent()
                                    val change = event.changes.firstOrNull { it.id == down.id }
                                        ?: break
                                    if (!change.pressed) {
                                        // 未拖动=单击按起点状态加/取消（即切换）；拖动过沿途已处理
                                        if (!smeared) applyTo(hitId, dragAdd)
                                        break
                                    }
                                    val posRoot = change.position + containerRootOffset
                                    if (!smeared &&
                                        (change.position - down.position).getDistance() > touchSlop
                                    ) {
                                        smeared = true
                                        applyTo(hitId, dragAdd)
                                    }
                                    if (smeared) {
                                        // 快速划动事件间距大，沿路径插值补采避免漏块
                                        val delta = posRoot - lastRoot
                                        val steps = (delta.getDistance() / 24f).toInt()
                                            .coerceIn(1, 80)
                                        for (i in 1..steps) {
                                            val p = lastRoot + delta * (i.toFloat() / steps)
                                            hitTest(p)?.let { applyTo(it, dragAdd) }
                                        }
                                    }
                                    lastRoot = posRoot
                                    // 按在词块上即拦截滚动（滚动只允许从空隙处发起）
                                    event.changes.forEach { if (it.pressed) it.consume() }
                                }
                            }
                        }
                ) {
                    if (allTokens.isEmpty()) {
                        Text(
                            text = "未识别到文字",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(16.dp)
                        )
                    } else {
                        Column(
                            Modifier
                                .fillMaxWidth()
                                .verticalScroll(scrollState)
                                .onGloballyPositioned { contentHeightPx = it.size.height }
                                .padding(4.dp)
                        ) {
                            // 无视原文换行，全部词块铺进同一个 FlowRow，只在窗口边缘换行
                            FlowRow(
                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                                verticalArrangement = Arrangement.spacedBy(10.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                allTokens.forEachIndexed { idx, token ->
                                    BangToken(
                                        token = token,
                                        selected = idx in selected,
                                        onRegisterRect = { rect -> tokenRects[idx] = rect }
                                    )
                                }
                            }
                            // 尾部永远留一行空行：滚到底时给悬浮的插入按钮让位
                            Spacer(Modifier.height(32.dp))
                        }
                        // 上半区右侧常驻滚动条（可拖动）
                        BangScrollbar(
                            scrollState = scrollState,
                            contentHeightPx = contentHeightPx,
                            viewportHeightPx = viewportHeightPx,
                            modifier = Modifier.align(Alignment.CenterEnd)
                        )
                    }
                    // 悬浮插入按钮（右下角压在词块墙上）；插入后清空全部选中
                    FilledTonalButton(
                        onClick = { insertSelected() },
                        enabled = selected.isNotEmpty(),
                        contentPadding = PaddingValues(horizontal = 12.dp),
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .padding(end = 6.dp, bottom = 6.dp)
                            .height(28.dp)
                            .onGloballyPositioned { coords ->
                                insertButtonRect = Rect(coords.positionInRoot(), coords.size.toSize())
                            }
                    ) {
                        Text(
                            text = if (selected.isEmpty()) "插入" else "插入(${selected.size})",
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }

                HorizontalDivider(
                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                )
                Spacer(Modifier.height(4.dp))

                // 下半区（占 1/3）：缓存列表（单击文本直接编辑，右侧垃圾桶删除）
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

/** 词块：未选中=正则命中高亮/普通淡底（暗色模式下可见可按），选中=主色实底反白 */
@Composable
private fun BangToken(
    token: OcrToken,
    selected: Boolean,
    onRegisterRect: (Rect) -> Unit
) {
    val background = when {
        selected -> MaterialTheme.colorScheme.primary
        token.isRegexMatch -> MaterialTheme.colorScheme.primaryContainer
        else -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f)
    }
    val contentColor = when {
        selected -> MaterialTheme.colorScheme.onPrimary
        token.isRegexMatch -> MaterialTheme.colorScheme.onPrimaryContainer
        else -> MaterialTheme.colorScheme.onSurface
    }
    Text(
        text = token.text,
        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium),
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

/** 上半区右侧常驻滚动条：内容超高才显示，拖动滑块直接滚动词块墙 */
@Composable
private fun BangScrollbar(
    scrollState: ScrollState,
    contentHeightPx: Int,
    viewportHeightPx: Int,
    modifier: Modifier = Modifier
) {
    if (contentHeightPx <= viewportHeightPx || viewportHeightPx <= 0) return
    val density = LocalDensity.current
    val thumbHeightPx = ((viewportHeightPx.toFloat() * viewportHeightPx / contentHeightPx)
        .roundToInt()).coerceAtLeast(40)
    val trackHeightPx = (viewportHeightPx - thumbHeightPx).coerceAtLeast(1)
    val thumbY = if (scrollState.maxValue > 0) {
        (scrollState.value.toFloat() / scrollState.maxValue * trackHeightPx).roundToInt()
    } else 0
    Box(
        modifier = modifier
            .fillMaxHeight()
            .width(16.dp)
            .pointerInput(trackHeightPx, scrollState.maxValue) {
                detectVerticalDragGestures { change, dragAmount ->
                    change.consume()
                    if (scrollState.maxValue > 0) {
                        // 拖动量按轨道比例换算成内容滚动量
                        val delta = dragAmount / trackHeightPx * scrollState.maxValue
                        val target = (scrollState.value + delta).roundToInt()
                            .coerceIn(0, scrollState.maxValue)
                        scrollState.dispatchRawDelta((target - scrollState.value).toFloat())
                    }
                }
            },
        contentAlignment = Alignment.CenterEnd
    ) {
        Box(
            Modifier
                .offset { IntOffset(0, thumbY) }
                .size(5.dp, with(density) { thumbHeightPx.toDp() })
                .clip(RoundedCornerShape(3.dp))
                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.45f))
        )
    }
}

/** 缓存列表：序号 + 可直接编辑文本（点击聚焦弹输入法并滚到可见区）+ 垃圾桶删除 */
@Composable
private fun BangCacheList(
    items: List<BangCachedItem>,
    onDelete: (BangCachedItem) -> Unit,
    modifier: Modifier = Modifier
) {
    val listState = rememberLazyListState()
    var focusScrollIndex by remember { mutableStateOf<Int?>(null) }
    LaunchedEffect(focusScrollIndex) {
        focusScrollIndex?.let { index ->
            // 等输入法把窗口顶起（窗口变小）后再滚动，保证编辑行落在输入法上方
            delay(300)
            listState.animateScrollToItem(index)
            focusScrollIndex = null
        }
    }
    LazyColumn(
        state = listState,
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
                    modifier = Modifier
                        .weight(1f)
                        .padding(vertical = 6.dp)
                        .onFocusChanged { if (it.isFocused) focusScrollIndex = index }
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
