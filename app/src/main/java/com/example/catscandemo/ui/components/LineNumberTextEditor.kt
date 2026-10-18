package com.example.catscandemo.ui.components

import android.view.inputmethod.InputMethodManager
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.VectorConverter
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.LocalOverscrollConfiguration
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.selection.LocalTextSelectionColors
import androidx.compose.foundation.text.selection.TextSelectionColors
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.SoftwareKeyboardController
import androidx.compose.ui.platform.LocalTextToolbar
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.catscandemo.R
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** 自定义水滴半径（直径约为系统手柄的一半） */
private val DROP_RADIUS = 6.dp

/** 查找命中高亮色：常规=浅黄，当前=橙（VSCode 风格） */
private val FindMatchColor = Color(0xFFFFF176)
private val FindCurrentColor = Color(0xFFFFB300)

/** 查找/替换状态：编辑器与查找工具条共享 */
class TextFindState {
    var visible by mutableStateOf(false)
    var query by mutableStateOf("")
    var replacement by mutableStateOf("")
    var showReplace by mutableStateOf(false)

    /** 命中区段（文本 offset 区间），大小写不敏感 */
    var matches by mutableStateOf<List<IntRange>>(emptyList())

    /** 当前定位到的命中下标，-1 表示无 */
    var currentIndex by mutableStateOf(-1)

    /** 上/下定位导航计数器：每次导航自增，编辑器据此把命中行滚到可视区第 2 行 */
    var navTick by mutableStateOf(0)
}

/**
 * 在编辑层自绘的水滴（尖端朝上顶着光标/选区端点下方）。
 * 系统手柄已在 Popup 层渲染且无法改尺寸，这里画半尺寸版本替代。
 * 路径：尖端 + 两侧切线 + 经过圆底部的大弧（经典水滴轮廓）。
 */
private fun DrawScope.drawTeardrop(tip: Offset, radius: Float, color: Color) {
    val cx = tip.x
    val cy = tip.y + radius * 2f
    val circle = Rect(cx - radius, cy - radius, cx + radius, cy + radius)
    val path = Path().apply {
        moveTo(cx, tip.y)
        // 左切点（210°），圆心距尖端 2r 时切点恰在 ±60°方向
        lineTo(cx - radius * 0.866f, cy - radius * 0.5f)
        // 逆时针大弧：210° → 90°(圆底) → 330°
        arcTo(
            rect = circle,
            startAngleDegrees = 210f,
            sweepAngleDegrees = -240f,
            forceMoveTo = false
        )
        close()
    }
    drawPath(path = path, color = color)
}

/** 行号栏宽度 */
private val GUTTER_WIDTH = 44.dp

/**
 * 禁用输入法时的连续压制：框架聚焦后由 TextInputServiceAndroid 命令队列在下一帧
 * 批处理 StartInput 并强制 showKeyboard，单次 hide 可能赶在弹窗之前执行而失效。
 * 立即 + 50/150/300/600ms 多次收起，覆盖慢机上的弹出时序窗。
 */
private suspend fun suppressImeWindow(hideIme: () -> Unit) {
    hideIme()
    delay(50); hideIme()
    delay(100); hideIme()
    delay(150); hideIme()
    delay(300); hideIme()
}

/**
 * 带行号栏的轻量纯文本编辑器（VSCode 风格）。
 * - Consolas 字体 + Trim.None：行号与正文逐行等高对齐
 * - 逻辑行号：仅真实换行(\n)编行号，软换行并入上一逻辑行（VSCode 行为）
 * - 双击选中词；点空白处光标落到最近位置/文末并唤出输入法
 * - 内容超出一屏时允许向下再滚半屏（scroll past end）
 * - 橡皮筋：到顶/到底后继续拖动有阻尼位移，松手回弹
 * - 右侧滚动条：滚动时显示，可按住拖动
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun LineNumberTextEditor(
    value: TextFieldValue,
    onValueChange: (TextFieldValue) -> Unit,
    modifier: Modifier = Modifier,
    findState: TextFindState? = null,
    onSelectRange: (Int, Int) -> Unit = { _, _ -> },
    // 禁用输入法：开启后点击编辑区不弹出软键盘（光标/选区仍可用，便于纯扫码录入）。
    // 实现 = 拦截 composition local 的 show()（imeBlockingController）+ 聚焦/点击后
    // 连续 hide 压制（suppressImeWindow），不改 readOnly 以免破坏 InputConnection。
    keyboardDisabled: Boolean = false
) {
    val vScroll = rememberScrollState()
    val density = LocalDensity.current
    val focusRequester = remember { FocusRequester() }
    val keyboardController = LocalSoftwareKeyboardController.current
    val composeView = LocalView.current
    val context = LocalContext.current

    // 禁用开关的最新值：pointerInput 手势协程生命周期跨越多次重组，直接捕获
    // keyboardDisabled 参数拿到的是协程启动时的旧值（切换开关不会重启手势）——
    // 这就是"切到禁用后点击文档仍弹键盘"的根因：手势里读到旧值 false 走了 show()。
    // 协程/回调内一律读 keyboardDisabledState.value。
    val keyboardDisabledState = rememberUpdatedState(keyboardDisabled)

    // 提供给 BasicTextField 子树的键盘控制器：禁用时吞掉框架经 composition local
    // 发出的一切 show()（如聚焦后 tapToFocus），hide() 正常转发。确定性拦截，
    // 不依赖 hide 时序。注意编辑器自己的 keyboardController 仍是原始实例不受影响。
    val imeBlockingController = remember(keyboardController) {
        object : SoftwareKeyboardController {
            override fun show() {
                if (!keyboardDisabledState.value) keyboardController?.show()
            }

            override fun hide() {
                keyboardController?.hide()
            }
        }
    }

    // 强制隐藏输入法：keyboardController.hide() 在某些时机（如刚 requestFocus 后）
    // 会失效，改用 InputMethodManager 底层 API + windowToken 直接收起，更可靠。
    val hideIme: () -> Unit = {
        val imm = context.getSystemService(InputMethodManager::class.java)
        imm?.hideSoftInputFromWindow(composeView.windowToken, 0)
        keyboardController?.hide()
    }

    val latestValue by rememberUpdatedState(value)
    val latestOnValueChange by rememberUpdatedState(onValueChange)
    val latestOnSelectRange by rememberUpdatedState(onSelectRange)

    // Trim.None：默认 Trim.Both 会裁掉首行顶部/末行底部的行距，
    // 导致正文第一行比其余行矮、单行渲染的行号组件被双向裁剪——这是行号错位的根因
    val consolas = remember { FontFamily(Font(R.font.consolas)) }
    val textStyle = TextStyle(
        fontFamily = consolas,
        fontSize = 14.sp,
        lineHeight = 21.sp,
        lineHeightStyle = LineHeightStyle(
            alignment = LineHeightStyle.Alignment.Center,
            trim = LineHeightStyle.Trim.None
        ),
        color = MaterialTheme.colorScheme.onSurface
    )

    // 配色
    val gutterBg = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)
    val gutterDivider = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.7f)
    val lineNumberColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.55f)
    val activeLineNumberColor = MaterialTheme.colorScheme.primary
    val currentLineBg = MaterialTheme.colorScheme.primary.copy(alpha = 0.06f)
    val scrollbarColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.45f)

    var layoutResult by remember { mutableStateOf<TextLayoutResult?>(null) }
    var viewportHeightPx by remember { mutableIntStateOf(0) }
    val contentHeightPx = layoutResult?.size?.height ?: 0

    // 逻辑行起始 offset 列表：仅 \n 产生新行号，软换行不算
    val logicalLineStarts = remember(value.text) {
        val starts = mutableListOf(0)
        value.text.forEachIndexed { i, c -> if (c == '\n') starts.add(i + 1) }
        starts.toList()
    }

    // 光标所在视觉行
    val cursorLine = remember(value.selection, value.text, layoutResult) {
        val layout = layoutResult
        when {
            layout == null || layout.lineCount == 0 -> 0
            value.text.isEmpty() -> 0
            else -> layout.getLineForOffset(
                value.selection.end.coerceIn(0, value.text.length)
            ).coerceIn(0, layout.lineCount - 1)
        }
    }

    // 焦点状态：供光标水滴显隐与"禁用输入法时压制键盘"使用
    var hasFocus by remember { mutableStateOf(false) }

    // 键盘（IME）遮挡高度：edge-to-edge 下 adjustResize 不再缩窗，编辑器不做 imePadding
    // （缩布局会让键盘上方露出一条背景色"白条"），改为读取键盘高度驱动滚动——
    // 键盘弹出时把光标行滚到键盘顶边之上，编辑器底部被键盘直接盖住、无白条。
    val imeBottomPx = WindowInsets.ime.getBottom(density)

    // 光标跟随：selection/text/视口高度/键盘开合变化时，把光标行滚进可视区
    // （可视区底部 = 视口底 - 键盘遮挡高度）
    LaunchedEffect(value.selection, value.text, viewportHeightPx, imeBottomPx) {
        val layout = layoutResult ?: return@LaunchedEffect
        if (viewportHeightPx <= 0 || layout.lineCount == 0) return@LaunchedEffect
        withFrameNanos { }
        val line = cursorLine.coerceIn(0, layout.lineCount - 1)
        val top = layout.getLineTop(line)
        val bottom = layout.getLineBottom(line)
        val cur = vScroll.value.toFloat()
        val viewBottom = cur + (viewportHeightPx - imeBottomPx).coerceAtLeast(0)
        when {
            bottom > viewBottom ->
                vScroll.scrollTo((bottom - viewBottom + cur).toInt().coerceAtLeast(0))
            top < cur ->
                vScroll.scrollTo(top.toInt().coerceAtLeast(0))
        }
    }

    // 禁用输入法时压制键盘：Compose 1.7 的 BasicTextField 获得焦点会异步 startInput，
    // 命令队列下一帧强制 showKeyboard。这里在禁用+聚焦状态下连续压制（见 suppressImeWindow）。
    // 注意：不使用 readOnly=keyboardDisabled——在有焦点的字段上切换 readOnly 会破坏
    // InputConnection，导致重新开启后 show() 失效（输入法闪一下就没、再也叫不出）。
    LaunchedEffect(keyboardDisabled, hasFocus) {
        if (keyboardDisabled && hasFocus) {
            suppressImeWindow(hideIme)
        }
    }

    // scroll past end：内容超出视口才追加半屏，短文本不留多余空白；
    // 键盘弹出时额外追加键盘高度的可滚余量，保证被遮住的末尾行也能滚到键盘上方
    val extraPx = imeBottomPx + if (viewportHeightPx > 0 && contentHeightPx > viewportHeightPx) {
        viewportHeightPx / 2
    } else {
        0
    }
    val textAreaHeightDp = with(density) {
        (maxOf(contentHeightPx, viewportHeightPx) + extraPx).toDp()
    }

    // 橡皮筋过滚动：禁用系统 edge effect 后自行实现阻尼位移 + 回弹
    var overscroll by remember { mutableFloatStateOf(0f) }
    val overscrollConnection = remember {
        object : NestedScrollConnection {
            override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                if (source != NestedScrollSource.UserInput) return Offset.Zero
                val y = available.y
                val cur = overscroll
                if (cur == 0f || (cur > 0f) == (y > 0f)) return Offset.Zero
                // 反向拖动：先抵消橡皮筋，过零后余量交还滚动
                val new = cur + y
                return if ((cur > 0f) == (new > 0f)) {
                    overscroll = new
                    Offset(0f, y)
                } else {
                    overscroll = 0f
                    Offset(0f, y - new)
                }
            }

            override fun onPostScroll(
                consumed: Offset,
                available: Offset,
                source: NestedScrollSource
            ): Offset {
                if (source != NestedScrollSource.UserInput) return Offset.Zero
                val y = available.y
                if (y == 0f) return Offset.Zero
                val maxOver = viewportHeightPx * 0.35f
                if (maxOver <= 0f) return Offset.Zero
                overscroll = (overscroll + y * 0.35f).coerceIn(-maxOver, maxOver)
                return Offset(0f, y)
            }

            override suspend fun onPreFling(available: Velocity): Velocity {
                if (overscroll != 0f) {
                    // NoBouncy：只归位不弹过头
                    animate(
                        typeConverter = Float.VectorConverter,
                        initialValue = overscroll,
                        targetValue = 0f,
                        animationSpec = spring(
                            dampingRatio = Spring.DampingRatioNoBouncy,
                            stiffness = Spring.StiffnessMedium
                        )
                    ) { v, _ ->
                        overscroll = v
                    }
                    return available
                }
                return Velocity.Zero
            }
        }
    }

    // 滚动条可见性：滚动中/拖动时显示，停止后淡出
    var scrollbarVisible by remember { mutableStateOf(false) }
    var scrollbarDragging by remember { mutableStateOf(false) }
    LaunchedEffect(vScroll.isScrollInProgress, scrollbarDragging) {
        if (vScroll.isScrollInProgress || scrollbarDragging) {
            scrollbarVisible = true
        } else {
            delay(900)
            scrollbarVisible = false
        }
    }

    // ===== 自定义水滴 =====
    // 系统手柄通过 LocalTextSelectionColors 设为透明（其拖动交互仍由框架接管），
    // 视觉上由编辑层自绘的半尺寸水滴替代：
    // - 画在主内容层，设置抽屉展开时自然被遮罩盖住
    // - 光标水滴与多选双水滴按选区状态互斥
    // - 滚动中隐藏，完全停稳后再淡入
    val selectionColors = TextSelectionColors(
        handleColor = Color.Transparent,
        backgroundColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.4f)
    )
    val dropColor = MaterialTheme.colorScheme.primary
    val dropRadiusPx = with(density) { DROP_RADIUS.toPx() }

    var textFieldCoords by remember { mutableStateOf<LayoutCoordinates?>(null) }
    val textToolbar = LocalTextToolbar.current
    val clipboardManager = LocalClipboardManager.current
    val gestureScope = rememberCoroutineScope()

    // 双击手势 → 水滴（光标态）拖动的让位信号：双击手势成功选词时同步记录该
    // 指针 id，水滴拖动启动前比对，命中则让位给"双击第二下不抬手滑动扩选"。
    // 不能用 down.isConsumed 当判据——框架 Main 通道的 detectTapGestures 对任何
    // 按下都会消费 down，正常按在水滴上也会被误判成双击，导致水滴永远拖不动。
    // 手势结束（抬起）时清零，下一手势重新判定。
    val doubleSlidePointerId = remember { mutableLongStateOf(-1L) }

    // 选区折叠（光标态）时自动隐藏选择菜单：双击/长按选词后点击别处、或程序解除框选，
    // 都应让浮动菜单消失。
    LaunchedEffect(value.selection) {
        if (value.selection.collapsed) {
            textToolbar.hide()
        }
    }

    // 滚动停稳判定：滚动开始立即隐藏，停止 250ms 后再显示
    var scrollSettled by remember { mutableStateOf(true) }
    LaunchedEffect(vScroll.isScrollInProgress) {
        if (vScroll.isScrollInProgress) {
            scrollSettled = false
        } else {
            delay(250)
            scrollSettled = true
        }
    }

    // 查找上/下定位：把命中行滚动到可视区第 2 行（上方留出一行上下文）
    val navTick = findState?.navTick ?: 0
    LaunchedEffect(navTick) {
        if (navTick <= 0) return@LaunchedEffect
        val fs = findState ?: return@LaunchedEffect
        val m = fs.matches.getOrNull(fs.currentIndex) ?: return@LaunchedEffect
        val layout = layoutResult ?: return@LaunchedEffect
        if (layout.lineCount == 0) return@LaunchedEffect
        val line = layout.getLineForOffset(m.first.coerceIn(0, value.text.length))
        val lineTop = layout.getLineTop(line)
        val lineHeight = layout.getLineBottom(line) - lineTop
        val target = (lineTop - lineHeight).toInt().coerceIn(0, vScroll.maxValue)
        vScroll.animateScrollTo(target)
    }

    // 水滴整体透明度：滚动未停稳/失焦时淡出，停稳聚焦后淡入
    val dropVisible = scrollSettled && hasFocus
    val dropAlpha by animateFloatAsState(
        targetValue = if (dropVisible) 1f else 0f,
        animationSpec = tween(durationMillis = if (dropVisible) 150 else 120),
        label = "dropAlpha"
    )

    // 与长按一致的浮动菜单（复制/剪切/粘贴/全选）；回调读取最新值，菜单常驻时不过期
    fun showSelectionMenu(rootRect: Rect) {
        val current = latestValue
        val sel = current.selection
        val canCopyCut = !sel.collapsed
        val canSelectAll = current.text.isNotEmpty() &&
                (sel.min > 0 || sel.max < current.text.length)
        textToolbar.showMenu(
            rect = rootRect,
            onCopyRequested = if (canCopyCut) ({
                val v = latestValue
                val s = v.selection
                if (!s.collapsed) {
                    clipboardManager.setText(
                        AnnotatedString(v.text.substring(s.min, s.max))
                    )
                }
                textToolbar.hide()
            }) else null,
            onCutRequested = if (canCopyCut) ({
                val v = latestValue
                val s = v.selection
                if (!s.collapsed) {
                    clipboardManager.setText(
                        AnnotatedString(v.text.substring(s.min, s.max))
                    )
                    latestOnValueChange(
                        v.copy(
                            text = v.text.removeRange(s.min, s.max),
                            selection = TextRange(s.min)
                        )
                    )
                }
                textToolbar.hide()
            }) else null,
            onPasteRequested = {
                val clipText = clipboardManager.getText()?.text
                if (!clipText.isNullOrEmpty()) {
                    val v = latestValue
                    val min = v.selection.min.coerceIn(0, v.text.length)
                    val max = v.selection.max.coerceIn(0, v.text.length)
                    latestOnValueChange(
                        v.copy(
                            text = v.text.replaceRange(min, max, clipText),
                            selection = TextRange(min + clipText.length)
                        )
                    )
                }
                textToolbar.hide()
            },
            onSelectAllRequested = if (canSelectAll) ({
                val v = latestValue
                latestOnValueChange(v.copy(selection = TextRange(0, v.text.length)))
            }) else null
        )
    }

    // 以当前选区范围（重）弹浮动选择菜单。拖动水滴开始时会 hide 菜单，框架不会
    // 自动重弹——松手后若框选还在，必须手动恢复，否则"选了白选"。
    // 选区折叠/无布局/无坐标时静默返回（调用方无需预检）。
    fun showMenuForCurrentSelection() {
        val layout = layoutResult ?: return
        if (layout.lineCount == 0) return
        val current = latestValue
        val sel = current.selection
        if (sel.collapsed) return
        val textLen = current.text.length
        val start = sel.min.coerceIn(0, textLen)
        val end = sel.max.coerceIn(0, textLen)
        if (end <= start) return
        val coords = textFieldCoords ?: return
        val localRect = layout.getPathForRange(start, end).getBounds()
        val topLeft = coords.localToRoot(Offset(localRect.left, localRect.top))
        showSelectionMenu(
            Rect(
                topLeft.x,
                topLeft.y,
                topLeft.x + localRect.width,
                topLeft.y + localRect.height
            )
        )
    }

    // 禁用系统 edge effect（本主题下不可见），由橡皮筋接管边界反馈；
    // 系统选择手柄透明化（交互保留，视觉由正文区 draw 层自绘水滴替代）
    CompositionLocalProvider(
        LocalOverscrollConfiguration provides null,
        LocalTextSelectionColors provides selectionColors,
        // 见 imeBlockingController：禁用时框架经此 local 的 show() 全部被吞
        LocalSoftwareKeyboardController provides imeBlockingController
    ) {
        Box(
            modifier = modifier
                .onSizeChanged { viewportHeightPx = it.height }
                // 裁剪内容：橡皮筋 graphicsLayer 位移会把文本推出编辑区，
                // 不裁剪会盖到上方文档选择器/复制/换行/撤回图标
                .clipToBounds()
        ) {
        // 橡皮筋位移层：行号背景条与滚动内容一起移动
        Box(
            Modifier
                .fillMaxSize()
                .nestedScroll(overscrollConnection)
                .graphicsLayer { translationY = overscroll }
        ) {
            // 固定行号背景条与分隔线（不随内容滚动，始终铺满高度）
            Row(Modifier.fillMaxSize()) {
                Box(
                    Modifier
                        .width(GUTTER_WIDTH)
                        .fillMaxHeight()
                        .background(gutterBg)
                )
                Box(
                    Modifier
                        .width(1.dp)
                        .fillMaxHeight()
                        .background(gutterDivider)
                )
            }

            // 滚动内容：行号列 + 正文
            Row(
                Modifier
                    .fillMaxSize()
                    .verticalScroll(vScroll)
                    .padding(vertical = 8.dp)
            ) {
                // 行号列：每个逻辑行一个行号，高度=该逻辑行全部视觉行高之和，顶对齐首排
                Column(Modifier.width(GUTTER_WIDTH)) {
                    val layout = layoutResult
                    if (layout != null && layout.lineCount > 0) {
                        val textLen = value.text.length
                        logicalLineStarts.forEachIndexed { i, startOffset ->
                            val firstVisual = layout
                                .getLineForOffset(startOffset.coerceAtMost(textLen))
                                .coerceIn(0, layout.lineCount - 1)
                            val nextFirstVisual = if (i + 1 < logicalLineStarts.size) {
                                layout
                                    .getLineForOffset(
                                        logicalLineStarts[i + 1].coerceAtMost(textLen)
                                    )
                                    .coerceIn(0, layout.lineCount - 1)
                            } else {
                                layout.lineCount
                            }
                            val lastVisual = (nextFirstVisual - 1).coerceAtLeast(firstVisual)
                            val heightPx =
                                layout.getLineBottom(lastVisual) - layout.getLineTop(firstVisual)
                            val isCursorLine = cursorLine in firstVisual..lastVisual
                            Box(
                                modifier = Modifier
                                    .width(GUTTER_WIDTH)
                                    .height(with(density) { heightPx.toDp() }),
                                contentAlignment = Alignment.TopEnd
                            ) {
                                Text(
                                    text = "${i + 1}",
                                    style = textStyle.copy(
                                        color = if (isCursorLine) {
                                            activeLineNumberColor
                                        } else {
                                            lineNumberColor
                                        },
                                        fontWeight = if (isCursorLine) {
                                            FontWeight.Bold
                                        } else {
                                            FontWeight.Normal
                                        }
                                    ),
                                    textAlign = TextAlign.End,
                                    maxLines = 1,
                                    softWrap = false,
                                    modifier = Modifier.padding(end = 10.dp)
                                )
                            }
                        }
                    }
                }

                // 分隔线占位 + 与正文的间距
                Spacer(Modifier.width(9.dp))

                // 正文区（查找高亮垫底 → 当前行高亮/文本 → 水滴最后画在文字上方）
                Box(
                    Modifier
                        .weight(1f)
                        .height(textAreaHeightDp)
                        // 查找命中高亮：画在所有内容之下（文字下方），VSCode 风格
                        .drawBehind {
                            val st = findState ?: return@drawBehind
                            val lay = layoutResult ?: return@drawBehind
                            if (!st.visible || st.query.isEmpty()) return@drawBehind
                            val textLen = value.text.length
                            st.matches.forEachIndexed { i, m ->
                                val start = m.first.coerceIn(0, textLen)
                                val end = (m.last + 1).coerceIn(0, textLen)
                                if (end <= start) return@forEachIndexed
                                val isCurrent = i == st.currentIndex
                                val color = (if (isCurrent) FindCurrentColor else FindMatchColor)
                                    .copy(alpha = if (isCurrent) 0.55f else 0.45f)
                                val startLine = lay.getLineForOffset(start)
                                val endLine = lay.getLineForOffset(end - 1)
                                for (line in startLine..endLine) {
                                    val x0 = if (line == startLine) {
                                        lay.getHorizontalPosition(start, true)
                                    } else {
                                        lay.getLineLeft(line)
                                    }
                                    val x1 = if (line == endLine) {
                                        if (end >= textLen) {
                                            lay.getLineRight(line)
                                        } else {
                                            lay.getHorizontalPosition(end, true)
                                        }
                                    } else {
                                        lay.getLineRight(line)
                                    }
                                    drawRect(
                                        color = color,
                                        topLeft = Offset(x0, lay.getLineTop(line)),
                                        size = Size(
                                            (x1 - x0).coerceAtLeast(0f),
                                            lay.getLineBottom(line) - lay.getLineTop(line)
                                        )
                                    )
                                }
                            }
                        }
                        // 水滴层：drawContent 之后绘制，确保盖在文字之上
                        .drawWithContent {
                            drawContent()
                            val lay = layoutResult
                            if (dropAlpha <= 0.01f || lay == null || lay.lineCount == 0) {
                                return@drawWithContent
                            }
                            val textLen = value.text.length
                            if (value.selection.collapsed) {
                                val r = lay.getCursorRect(
                                    value.selection.end.coerceIn(0, textLen)
                                )
                                // getCursorRect().left 是光标竖条的左边缘，竖条宽 2dp
                                //（框架 TextFieldCursor 固定值），尖端右移半个条宽，
                                // 才能指向光标底部的正中间而非左下角
                                drawTeardrop(
                                    tip = Offset(r.left + 1.dp.toPx(), r.bottom),
                                    radius = dropRadiusPx,
                                    color = dropColor.copy(alpha = dropAlpha)
                                )
                            } else {
                                val s = lay.getCursorRect(
                                    value.selection.min.coerceIn(0, textLen)
                                )
                                val e = lay.getCursorRect(
                                    value.selection.max.coerceIn(0, textLen)
                                )
                                drawTeardrop(
                                    tip = Offset(s.left, s.bottom),
                                    radius = dropRadiusPx,
                                    color = dropColor.copy(alpha = dropAlpha)
                                )
                                drawTeardrop(
                                    tip = Offset(e.left, e.bottom),
                                    radius = dropRadiusPx,
                                    color = dropColor.copy(alpha = dropAlpha)
                                )
                            }
                        }
                        // 选区端点 / 光标水滴拖动：
                        // - 非折叠选区：按下位置靠近任一端点水滴则接管，拖动改选区范围；
                        // - 折叠选区（光标）：按下位置靠近光标水滴则接管，拖动移动光标位置。
                        //   （Initial 通道抢先消费，文本框不会误触光标/长按）
                        .pointerInput(dropRadiusPx) {
                            val touchRadius = 26.dp.toPx()
                            awaitEachGesture {
                                val down = awaitFirstDown(
                                    pass = PointerEventPass.Initial,
                                    requireUnconsumed = false
                                )
                                val v = latestValue
                                val layout = layoutResult ?: return@awaitEachGesture
                                val r = dropRadiusPx
                                val textLen = v.text.length
                                val sel = v.selection

                                // 计算光标/端点水滴 tip 位置（tip=行底，圆心在 tip 下方 2r）
                                val sRect = layout.getCursorRect(sel.min.coerceIn(0, textLen))
                                val startTip = Offset(sRect.left, sRect.bottom)
                                fun nearDrop(pos: Offset, tip: Offset): Boolean {
                                    val center = Offset(tip.x, tip.y + 2 * r)
                                    return (pos - tip).getDistance() <= touchRadius ||
                                            (pos - center).getDistance() <= touchRadius
                                }

                                if (sel.collapsed) {
                                    // 折叠选区：拖动光标水滴移动光标。
                                    // 注意：不在 down 时 consume——否则双击的第二次点击
                                    // （落在第一次点击设置的光标附近）会被这里拦截，
                                    // 导致双击选词失效。等手指移动超过 touchSlop 才接管。
                                    if (!nearDrop(down.position, startTip)) return@awaitEachGesture
                                    var dragStarted = false
                                    while (true) {
                                        val event = awaitPointerEvent(PointerEventPass.Initial)
                                        val change = event.changes
                                            .firstOrNull { it.id == down.id } ?: break
                                        if (!change.pressed) {
                                            // 未拖动就抬起：不消费，交给单击/双击逻辑处理
                                            break
                                        }
                                        val moved = (change.position - down.position).getDistance()
                                        if (!dragStarted) {
                                            // 双击第二下不抬手：双击手势已选词并同步记录了
                                            // 该指针 id，接续拖动（扩选）由它负责，这里让出——
                                            // 否则本手势在修饰符链靠前，会抢先把移动事件消费
                                            // 成"移动光标"。判据必须是私有信号而非
                                            // down.isConsumed（框架 Main 通道对任何按下都会
                                            // 消费 down，会把正常拖动误判让位）。
                                            if (down.id.value == doubleSlidePointerId.longValue) {
                                                break
                                            }
                                            if (moved > viewConfiguration.touchSlop) {
                                                dragStarted = true
                                                down.consume()
                                                textToolbar.hide()
                                            } else {
                                                continue
                                            }
                                        }
                                        change.consume()
                                        val lay = layoutResult ?: continue
                                        val newOffset = lay.getOffsetForPosition(change.position)
                                        latestOnSelectRange(newOffset, newOffset)
                                    }
                                    return@awaitEachGesture
                                }

                                // 非折叠选区：拖动端点水滴改选区范围
                                val eRect = layout.getCursorRect(sel.max.coerceIn(0, textLen))
                                val endTip = Offset(eRect.left, eRect.bottom)
                                val nearStart = nearDrop(down.position, startTip)
                                val nearEnd = nearDrop(down.position, endTip)
                                if (!nearStart && !nearEnd) return@awaitEachGesture
                                // 两端都命中时取更近的一端：离终点更近才拖终点
                                val dragEnd = if (nearStart && nearEnd) {
                                    (down.position - startTip).getDistance() >
                                            (down.position - endTip).getDistance()
                                } else {
                                    nearEnd
                                }
                                down.consume()
                                textToolbar.hide()
                                val anchor = if (dragEnd) sel.min else sel.max
                                while (true) {
                                    val event = awaitPointerEvent(PointerEventPass.Initial)
                                    val change = event.changes
                                        .firstOrNull { it.id == down.id } ?: break
                                    if (!change.pressed) {
                                        // 指针抬起，拖动结束
                                        change.consume()
                                        break
                                    }
                                    change.consume()
                                    val lay = layoutResult ?: continue
                                    val newOffset = lay.getOffsetForPosition(change.position)
                                    if (dragEnd) {
                                        latestOnSelectRange(anchor, newOffset)
                                    } else {
                                        latestOnSelectRange(newOffset, anchor)
                                    }
                                }
                                // 拖动结束：框选还在则恢复选择菜单（等两帧让最后一次
                                // 选区更新经 ViewModel 回流到 latestValue）
                                gestureScope.launch {
                                    withFrameNanos { }
                                    withFrameNanos { }
                                    showMenuForCurrentSelection()
                                }
                            }
                        }
                        // 统一处理单击/双击（Initial 通道抢在文本框内部手势之前）：
                        // 单击：吞掉抬起事件——框架从此不做"点击放光标"，也就不会创建
                        //       光标手柄弹窗（该弹窗在双击选词后会残留在词首，把起点水滴
                        //       的拖动劫持成"移动光标"），放光标/聚焦/唤起输入法由我们完成；
                        //       移动事件不消费，滚动与框架长按选词不受影响。
                        // 双击：消费第二次 down 选词并弹菜单；手指不抬起继续拖动则接续拖动端点。
                        .pointerInput(layoutResult) {
                            var lastTapTime = 0L
                            var lastTapPos = Offset.Zero
                            awaitEachGesture {
                                val down = awaitFirstDown(
                                    pass = PointerEventPass.Initial,
                                    requireUnconsumed = false
                                )
                                if (down.isConsumed) {
                                    // 端点水滴拖动手势已接管（按下位置在水滴附近），
                                    // 等该手势结束再继续监听
                                    while (true) {
                                        val ev = awaitPointerEvent(PointerEventPass.Initial)
                                        if (ev.changes.all { !it.pressed }) break
                                    }
                                    return@awaitEachGesture
                                }
                                val now = System.currentTimeMillis()
                                val isDouble =
                                    now - lastTapTime in 1..400 &&
                                            (down.position - lastTapPos).getDistance() < 60f
                                lastTapTime = 0L
                                // 双击选词结果：null=非双击或选词失败
                                var doubleBoundary: TextRange? = null
                                if (isDouble) {
                                    down.consume()
                                    val layout = layoutResult
                                    if (layout != null) {
                                        val pos = layout.getOffsetForPosition(down.position)
                                        val boundary = layout.getWordBoundary(pos)
                                        if (boundary.end > boundary.start) {
                                            doubleBoundary = boundary
                                            // 同步记录让位信号：水滴（光标态）拖动手势据此
                                            // 在本次手势内让出移动事件（见上 doubleSlidePointerId）
                                            doubleSlidePointerId.longValue = down.id.value
                                            // 聚焦（不弹键盘）让框架的选择手柄可用
                                            focusRequester.requestFocus()
                                            if (keyboardDisabledState.value) {
                                                gestureScope.launch {
                                                    suppressImeWindow(hideIme)
                                                }
                                            }
                                            latestOnValueChange(
                                                latestValue.copy(selection = boundary)
                                            )
                                            // 等焦点真正生效后再弹菜单：
                                            // FloatingActionMode 依赖已聚焦的 View，立即弹会被吞。
                                            // awaitEachGesture 是受限挂起作用域，不能直接 delay，
                                            // 改用外部协程延时弹出
                                            gestureScope.launch {
                                                delay(120)
                                                val sel = latestValue.selection
                                                if (sel.min == boundary.start &&
                                                    sel.max == boundary.end
                                                ) {
                                                    showMenuForCurrentSelection()
                                                }
                                            }
                                        }
                                    }
                                }
                                val downTime = System.currentTimeMillis()
                                var dragStarted = false
                                while (true) {
                                    val event = awaitPointerEvent(PointerEventPass.Initial)
                                    val change = event.changes
                                        .firstOrNull { it.id == down.id } ?: break
                                    if (change.isConsumed) break
                                    if (!change.pressed) {
                                        // 抬起
                                        val elapsed = System.currentTimeMillis() - downTime
                                        if (!dragStarted && doubleBoundary == null &&
                                            elapsed <= viewConfiguration.longPressTimeoutMillis + 50
                                        ) {
                                            // 单击：吞掉 up 阻止框架"点击放光标"，
                                            // 自己完成放光标+聚焦+唤起输入法
                                            change.consume()
                                            // 解除框选：关闭双击/长按弹出的选择菜单
                                            textToolbar.hide()
                                            val layout = layoutResult
                                            if (layout != null) {
                                                val pos =
                                                    layout.getOffsetForPosition(change.position)
                                                latestOnValueChange(
                                                    latestValue.copy(selection = TextRange(pos))
                                                )
                                                focusRequester.requestFocus()
                                                if (keyboardDisabledState.value) {
                                                    // 禁用输入法：聚焦后连续压制，
                                                    // 兜住框架异步 startInput 弹 IME 的时机。
                                                    // awaitEachGesture 是受限挂起作用域，
                                                    // 不能直接 delay，用外部协程。
                                                    gestureScope.launch {
                                                        suppressImeWindow(hideIme)
                                                    }
                                                } else {
                                                    keyboardController?.show()
                                                }
                                                lastTapTime = System.currentTimeMillis()
                                                lastTapPos = down.position
                                            }
                                        } else if (!dragStarted && doubleBoundary != null) {
                                            change.consume() // 双击选词的抬起也吞掉
                                        }
                                        break
                                    }
                                    val moved =
                                        (change.position - down.position).getDistance()
                                    if (doubleBoundary == null) {
                                        // 非双击：移动超 slop 视为滚动，交给父级，不再干预；
                                        // 长按超时不吞 up，让框架长按选词照常触发
                                        if (moved > viewConfiguration.touchSlop) {
                                            dragStarted = true
                                        }
                                    } else if (!dragStarted &&
                                        moved > viewConfiguration.touchSlop
                                    ) {
                                        // 双击选词后手指未抬起直接拖动：接续拖动端点改框选范围
                                        dragStarted = true
                                        val layout = layoutResult ?: break
                                        val boundary = doubleBoundary
                                        val sRect = layout.getCursorRect(boundary.min)
                                        val eRect = layout.getCursorRect(boundary.max)
                                        val startTip = Offset(sRect.left, sRect.bottom)
                                        val endTip = Offset(eRect.left, eRect.bottom)
                                        // 起点与终点都命中时取更近的一端
                                        val dragEnd =
                                            (change.position - startTip).getDistance() >
                                                    (change.position - endTip).getDistance()
                                        change.consume()
                                        textToolbar.hide()
                                        val anchor = if (dragEnd) boundary.min else boundary.max
                                        while (true) {
                                            val ev2 = awaitPointerEvent(PointerEventPass.Initial)
                                            val ch2 = ev2.changes
                                                .firstOrNull { it.id == down.id } ?: break
                                            if (ch2.isConsumed) break
                                            if (!ch2.pressed) {
                                                ch2.consume()
                                                break
                                            }
                                            ch2.consume()
                                            val lay = layoutResult ?: continue
                                            val newOffset =
                                                lay.getOffsetForPosition(ch2.position)
                                            if (dragEnd) {
                                                latestOnSelectRange(anchor, newOffset)
                                            } else {
                                                latestOnSelectRange(newOffset, anchor)
                                            }
                                        }
                                        // 拖动结束：框选还在则恢复选择菜单
                                        gestureScope.launch {
                                            withFrameNanos { }
                                            withFrameNanos { }
                                            showMenuForCurrentSelection()
                                        }
                                        break
                                    }
                                }
                                // 手势结束（抬起/被接管）：清零双击滑动让位信号，
                                // 否则下一次按下水滴会因残留 id 被误判让位、拖不动
                                doubleSlidePointerId.longValue = -1L
                            }
                        }
                ) {
                    val layout = layoutResult
                    if (layout != null && layout.lineCount > 0) {
                        val topDp = with(density) { layout.getLineTop(cursorLine).toDp() }
                        val heightDp = with(density) {
                            (layout.getLineBottom(cursorLine) - layout.getLineTop(cursorLine)).toDp()
                        }
                        Box(
                            Modifier
                                .fillMaxWidth()
                                .offset(y = topDp)
                                .height(heightDp)
                                .background(currentLineBg)
                        )
                    }

                    BasicTextField(
                        value = value,
                        onValueChange = onValueChange,
                        textStyle = textStyle,
                        cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                        onTextLayout = { layoutResult = it },
                        // readOnly 始终为 false：在有焦点的字段上切换 readOnly 会破坏
                        // InputConnection，导致重新开启输入法后 show() 失效。
                        // 禁用输入法纯靠 keyboardController.hide() 压制（见 onFocusChanged
                        // 与上方 LaunchedEffect），扫码插入走 ViewModel 程序写入不受影响。
                        readOnly = false,
                        modifier = Modifier
                            .fillMaxWidth()
                            .focusRequester(focusRequester)
                            .onFocusChanged {
                                hasFocus = it.isFocused
                                // 禁用输入法时：聚焦立即收起键盘，防止框架 startInput 弹出 IME
                                if (it.isFocused && keyboardDisabledState.value) hideIme()
                            }
                            .onGloballyPositioned { textFieldCoords = it }
                            .padding(end = 20.dp),
                        decorationBox = { inner ->
                            Box {
                                if (value.text.isEmpty()) {
                                    Text(
                                        "扫码或手动输入内容…",
                                        style = textStyle.copy(color = lineNumberColor)
                                    )
                                }
                                inner()
                            }
                        }
                    )
                }
            }
        }

        // 右侧滚动条：滚动/拖动时显示，可按住拖动（不随橡皮筋位移）
        val maxScroll = vScroll.maxValue
        if (maxScroll > 0 && viewportHeightPx > 0) {
            val totalPx = (maxScroll + viewportHeightPx).toFloat()
            val minThumbPx = with(density) { 28.dp.toPx() }
            val thumbHeightPx =
                (viewportHeightPx * viewportHeightPx / totalPx).coerceAtLeast(minThumbPx)
            val trackPx = viewportHeightPx.toFloat()
            val thumbOffsetPx =
                (vScroll.value.toFloat() / maxScroll) * (trackPx - thumbHeightPx)

            AnimatedVisibility(
                visible = scrollbarVisible || scrollbarDragging,
                enter = fadeIn(),
                exit = fadeOut(),
                modifier = Modifier.align(Alignment.CenterEnd)
            ) {
                Box(
                    Modifier
                        .fillMaxHeight()
                        .width(16.dp)
                        .pointerInput(maxScroll, viewportHeightPx) {
                            detectVerticalDragGestures(
                                onDragStart = { scrollbarDragging = true },
                                onDragEnd = { scrollbarDragging = false },
                                onDragCancel = { scrollbarDragging = false }
                            ) { change, _ ->
                                change.consume()
                                val newOffset = (change.position.y - thumbHeightPx / 2f)
                                    .coerceIn(0f, trackPx - thumbHeightPx)
                                val target =
                                    newOffset / (trackPx - thumbHeightPx) * maxScroll
                                vScroll.dispatchRawDelta(target - vScroll.value)
                            }
                        }
                ) {
                    Box(
                        Modifier
                            .align(Alignment.TopEnd)
                            .offset { IntOffset(0, thumbOffsetPx.toInt()) }
                            .padding(end = 3.dp)
                            .width(5.dp)
                            .height(with(density) { thumbHeightPx.toDp() })
                            .background(scrollbarColor, RoundedCornerShape(3.dp))
                    )
                }
            }
        }
    }
    }
}
