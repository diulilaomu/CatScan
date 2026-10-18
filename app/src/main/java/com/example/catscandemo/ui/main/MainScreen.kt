package com.example.catscandemo.ui.main

import android.annotation.SuppressLint
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.automirrored.filled.WrapText
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.FlashlightOff
import androidx.compose.material.icons.filled.FlashlightOn
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Keyboard
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.zIndex
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.catscandemo.domain.model.WorkMode
import com.example.catscandemo.presentation.viewmodel.MainViewModel
import com.example.catscandemo.ui.components.CameraPreview
import com.example.catscandemo.ui.components.ChangeServerUrlDialog
import com.example.catscandemo.ui.components.DiscoveredPcDialog
import com.example.catscandemo.ui.components.DiscreteRoomSelectionDialog
import com.example.catscandemo.ui.components.LineNumberTextEditor
import com.example.catscandemo.ui.components.ResultItemController
import com.example.catscandemo.ui.components.SettingsDrawer
import com.example.catscandemo.ui.components.TemplateEditorRightDrawer
import com.example.catscandemo.ui.components.TextDocsManagerDrawer
import com.example.catscandemo.ui.components.TextFindState
import com.example.catscandemo.utils.AutoRequestCameraPermission
import kotlinx.coroutines.launch

private val FLOOR_NUMBER_REGEX = Regex("\\d+")

@SuppressLint("ConfigurationScreenWidthHeight")
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(viewModel: MainViewModel) {
    val context = LocalContext.current
    val clipboardManager = LocalClipboardManager.current
    val coroutineScope = rememberCoroutineScope()
    val drawerState = rememberDrawerState(DrawerValue.Closed)

    // 初始化离线存储（只做一次）
    LaunchedEffect(Unit) {
        viewModel.initTemplateStore(context.applicationContext)
        viewModel.initHistoryStore(context.applicationContext)
        viewModel.initSettingsStore(context.applicationContext)
        viewModel.initTextDocsStore(context.applicationContext)
        // 启动被动发现 PC：每 1 秒扫描，发现后弹窗
        viewModel.startPassivePcDiscovery(context.applicationContext)
        // 加载初始扫描数据并更新StateFlow
        viewModel.getAllScans()
    }

    val latestContext by rememberUpdatedState(context)
    val latestClipboard by rememberUpdatedState(clipboardManager)

    val copyToClipboard: (String) -> Unit = { text ->
        latestClipboard.setText(AnnotatedString(text))
    }
    val showToast: (String) -> Unit = { msg ->
        android.widget.Toast.makeText(latestContext, msg, android.widget.Toast.LENGTH_SHORT).show()
    }

    val imagePickerLauncher =
        rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
            uri?.let {
                viewModel.onImagePicked(
                    uri = it,
                    context = latestContext,
                    copyToClipboard = copyToClipboard,
                    showToast = showToast
                )
            }
        }

    // 使用collectAsState观察扫描结果StateFlow
    val scanItems by viewModel.scanResults.collectAsState()

    fun parseFloorNumberLocal(floorStr: String): Int? {
        return FLOOR_NUMBER_REGEX.find(floorStr)?.value?.toIntOrNull()
    }

    val activeId = viewModel.activeTemplateId
    val selectedFloor = viewModel.scanSelectedFloor

    val displayItems = remember(activeId, selectedFloor, scanItems) {
        if (activeId.isNullOrBlank()) {
            scanItems
        } else {
            scanItems.filter {
                // 兼容旧数据：templateId 为空的也暂时算到当前模板（可选）
                (it.scanData.templateId == activeId || it.scanData.templateId.isBlank()) &&
                        parseFloorNumberLocal(it.scanData.floor) == selectedFloor
            }
        }
    }

    val duplicateTextSet = remember(displayItems) {
        val seen = HashSet<String>()
        buildSet {
            displayItems.forEach { item ->
                val text = item.scanData.text
                if (!seen.add(text)) add(text)
            }
        }
    }

    val isTextMode = viewModel.workMode == WorkMode.TEXT

    // ===== 查找/替换（文本模式，VSCode Ctrl+F 风格）=====
    val textFindState = remember { TextFindState() }
    // "全部替换"后自增，强制编辑器重组刷新（等长替换时受控文本框可能不重绘）
    var editorResetKey by remember { mutableStateOf(0) }

    fun goFindMatch(step: Int) {
        val matches = textFindState.matches
        if (matches.isEmpty()) return
        val n = matches.size
        val next = (((textFindState.currentIndex + step) % n) + n) % n
        textFindState.currentIndex = next
        val m = matches[next]
        viewModel.selectTextRange(m.first, m.last + 1)
        // 触发编辑器把命中行滚动到可视区第 2 行
        textFindState.navTick++
    }

    // 用当前正文与查询词同步重算命中（大小写不敏感），
    // 替换点击时立即重算，不依赖 LaunchedEffect 的异步旧状态
    fun computeFindMatchesNow(): List<IntRange> {
        val query = textFindState.query
        if (query.isEmpty()) return emptyList()
        val lower = viewModel.textFieldValue.text.lowercase()
        val q = query.lowercase()
        return buildList {
            var i = lower.indexOf(q)
            while (i >= 0) {
                add(i until (i + q.length))
                i = lower.indexOf(q, i + 1)
            }
        }
    }

    fun replaceCurrentMatch() {
        val matches = computeFindMatchesNow()
        if (matches.isEmpty()) {
            showToast("没有可替换的命中")
            return
        }
        val m = matches[textFindState.currentIndex.coerceIn(0, matches.size - 1)]
        val rep = textFindState.replacement
        val oldText = viewModel.textFieldValue.text
        val newText = oldText.replaceRange(m.first, m.last + 1, rep)
        viewModel.onTextEditorChange(
            TextFieldValue(newText, TextRange(m.first + rep.length))
        )
        showToast(
            if (viewModel.textFieldValue.text == newText) {
                "已替换 1 处（${oldText.length}→${newText.length} 字）"
            } else {
                "替换未生效（状态被回写），请反馈此提示"
            }
        )
    }

    fun replaceAllMatches() {
        val query = textFindState.query
        val rep = textFindState.replacement
        if (query.isEmpty()) {
            showToast("没有可替换的命中")
            return
        }
        // 查找词与替换词相同时，"全部替换"结果等于原文，直接提示而不是假装成功
        if (query == rep) {
            showToast("替换内容与查找内容相同")
            return
        }
        val oldText = viewModel.textFieldValue.text
        val lower = oldText.lowercase()
        val q = query.lowercase()
        if (lower.indexOf(q) < 0) {
            showToast("没有可替换的命中")
            return
        }
        // 逐处替换：每处都走与"单步替换"完全相同的写入路径（该路径已验证可用），
        // 避免批量重建 TextFieldValue 时出现状态不一致
        var text = oldText
        var count = 0
        var searchFrom = 0
        while (count < 2000) {
            val idx = text.indexOf(query, searchFrom, ignoreCase = true)
            if (idx < 0) break
            text = text.replaceRange(idx, idx + query.length, rep)
            count++
            // 从替换后的新内容继续找，防止替换词里包含查找词导致死循环
            searchFrom = idx + rep.length
        }
        if (count == 0) {
            showToast("没有可替换的命中")
            return
        }
        val firstIdx = oldText.lowercase().indexOf(q)
        viewModel.onTextEditorChange(
            TextFieldValue(text, TextRange((firstIdx + rep.length).coerceAtMost(text.length)))
        )
        // 强制编辑器重组刷新，排除等长替换时受控文本框显示不更新的可能
        editorResetKey++
        val readBack = viewModel.textFieldValue.text
        showToast(
            if (readBack != text) "替换未生效（状态被回写），请反馈此提示"
            else "已替换 $count 处（${oldText.length}→${text.length} 字）"
        )
    }

    // 查询/正文/开关变化时重算命中（大小写不敏感）
    LaunchedEffect(textFindState.visible, textFindState.query, viewModel.textFieldValue.text) {
        val list = if (textFindState.visible) computeFindMatchesNow() else emptyList()
        textFindState.matches = list
        textFindState.currentIndex = when {
            list.isEmpty() -> -1
            textFindState.currentIndex in list.indices -> textFindState.currentIndex
            else -> 0
        }
    }

    // TopBar 标题：模板模式显示 模板名+房间；文本模式显示当前文档名
    val titleTemplate = viewModel.activeTemplate
    val titleRoom = viewModel.currentRoom.trim()
    val titleText = if (isTextMode) {
        viewModel.textDocs.firstOrNull { it.id == viewModel.activeTextDocId }?.name
            ?.ifBlank { "文本模式" } ?: "文本模式"
    } else {
        remember(titleTemplate, titleRoom) {
            val name = titleTemplate?.name?.trim().orEmpty()
            listOf(name, titleRoom)
                .filter { it.isNotBlank() }
                .joinToString(" · ")
                .ifBlank { "CatScan" }
        }
    }

    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            SettingsDrawer(
                viewModel = viewModel,
                serverUrl = viewModel.serverUrl,
                onServerUrlChange = { viewModel.serverUrl = it },
                uploadEnabled = viewModel.uploadEnabled,
                onUploadEnabledChange = { viewModel.uploadEnabled = it },
                clipboardEnabled = viewModel.clipboardEnabled,
                onClipboardEnabledChange = { viewModel.onClipboardEnabledChange(it) },
                duplicateScanEnabled = viewModel.duplicateScanEnabled,
                onDuplicateScanEnabledChange = { viewModel.duplicateScanEnabled = it },
                showBarcodeOverlay = viewModel.showBarcodeOverlay,
                onShowBarcodeOverlayChange = { viewModel.showBarcodeOverlay = it },
                channel1ScanFrameInterval = viewModel.channel1ScanFrameInterval,
                onChannel1ScanFrameIntervalChange = { viewModel.channel1ScanFrameInterval = it },
                channel2MinAreaScore = viewModel.channel2MinAreaScore,
                onChannel2MinAreaScoreChange = { viewModel.channel2MinAreaScore = it },
                channel2MinAspectScore = viewModel.channel2MinAspectScore,
                onChannel2MinAspectScoreChange = { viewModel.channel2MinAspectScore = it },
                channel2MinSolidityScore = viewModel.channel2MinSolidityScore,
                onChannel2MinSolidityScoreChange = { viewModel.channel2MinSolidityScore = it },
                channel2MinGradScore = viewModel.channel2MinGradScore,
                onChannel2MinGradScoreChange = { viewModel.channel2MinGradScore = it }
            )
        }
    ) {
        Box(Modifier.fillMaxSize()) {
            Scaffold(
                topBar = {
                    CenterAlignedTopAppBar(
                        title = {
                            Text(
                                text = titleText,
                                style = MaterialTheme.typography.titleMedium,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        },
                        navigationIcon = {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                IconButton(onClick = { coroutineScope.launch { drawerState.open() } }) {
                                    Icon(
                                        imageVector = Icons.Default.Settings,
                                        contentDescription = "设置",
                                        modifier = Modifier.size(24.dp)
                                    )
                                }
                                // 模式快速切换：文本 ⇄ 模板 一键来回（齿轮右侧，与右侧摄像头开关大致轴对称）
                                IconButton(
                                    onClick = {
                                        viewModel.onWorkModeChange(
                                            if (isTextMode) WorkMode.TEMPLATE else WorkMode.TEXT
                                        )
                                    }
                                ) {
                                    Icon(
                                        imageVector = Icons.Filled.SwapHoriz,
                                        contentDescription = "切换模式",
                                        modifier = Modifier.size(24.dp)
                                    )
                                }
                            }
                        },
                        actions = {
                            // 摄像头开关（扫码图标）：关闭后自动收起扫描区，打开后恢复到记忆高度；
                            // 禁用时叠加斜线表示禁用
                            IconButton(onClick = { viewModel.onToggleCameraEnabled() }) {
                                val slashColor = MaterialTheme.colorScheme.onSurfaceVariant
                                Icon(
                                    imageVector = Icons.Filled.QrCodeScanner,
                                    contentDescription = if (viewModel.cameraEnabled) {
                                        "关闭摄像头"
                                    } else {
                                        "开启摄像头"
                                    },
                                    tint = if (viewModel.cameraEnabled) {
                                        LocalContentColor.current
                                    } else {
                                        MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.55f)
                                    },
                                    modifier = Modifier
                                        .size(24.dp)
                                        .drawWithContent {
                                            drawContent()
                                            if (!viewModel.cameraEnabled) {
                                                val stroke = 2.dp.toPx()
                                                drawLine(
                                                    color = slashColor,
                                                    start = Offset(
                                                        size.width * 0.12f,
                                                        size.height * 0.88f
                                                    ),
                                                    end = Offset(
                                                        size.width * 0.88f,
                                                        size.height * 0.12f
                                                    ),
                                                    strokeWidth = stroke,
                                                    cap = StrokeCap.Round
                                                )
                                            }
                                        }
                                )
                            }
                            IconButton(onClick = {
                                if (isTextMode) {
                                    viewModel.showTextDocsManager = true
                                } else {
                                    viewModel.showTemplateEditor = true
                                }
                            }) {
                                Icon(
                                    imageVector = if (isTextMode) {
                                        Icons.AutoMirrored.Filled.MenuBook
                                    } else {
                                        Icons.Filled.Description
                                    },
                                    contentDescription = if (isTextMode) "文档管理" else "模板管理/编辑",
                                    modifier = Modifier.size(24.dp)
                                )
                            }
                        }
                    )
                }
            ) { innerPadding ->
                var containerHeightPx by remember { mutableStateOf(0) }

                // 关闭眼睛：动画收起扫描区；打开眼睛：动画恢复到记忆高度
                LaunchedEffect(viewModel.cameraEnabled) {
                    val target = if (viewModel.cameraEnabled) {
                        viewModel.expandedSplitRatio
                    } else {
                        0f
                    }
                    animate(
                        initialValue = viewModel.splitRatio,
                        targetValue = target,
                        animationSpec = tween(durationMillis = 300, easing = FastOutSlowInEasing)
                    ) { value, _ ->
                        viewModel.setSplitRatioAnimated(value)
                    }
                }

                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding)
                        // 不再加 imePadding：adjustResize 已把窗口缩到键盘上方，
                        // 再垫 IME 高度会在输入法上方挤出一条白区
                        .onSizeChanged { containerHeightPx = it.height }
                ) {
                    // 摄像头区域（高度占比 = splitRatio，可拖动拉杆调节；收起后完全移除）
                    if (viewModel.splitRatio > 0.004f) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(viewModel.splitRatio)
                            .background(
                                if (viewModel.cameraEnabled) {
                                    MaterialTheme.colorScheme.primary
                                } else {
                                    // 商务蓝灰面板（随暗色模式切换）
                                    MaterialTheme.colorScheme.surfaceVariant
                                }
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        if (viewModel.cameraEnabled) {
                            AutoRequestCameraPermission {
                                CameraPreview(
                                    onBarcodeDetected = { code ->
                                        viewModel.onBarcodeScanned(code, copyToClipboard, showToast)
                                    },
                                    onCameraReady = { cam -> viewModel.camera = cam },
                                    showBarcodeOverlay = viewModel.showBarcodeOverlay,
                                    channel1ScanFrameInterval = viewModel.channel1ScanFrameInterval,
                                    channel2MinAreaScore = viewModel.channel2MinAreaScore,
                                    channel2MinAspectScore = viewModel.channel2MinAspectScore,
                                    channel2MinSolidityScore = viewModel.channel2MinSolidityScore,
                                    channel2MinGradScore = viewModel.channel2MinGradScore
                                )
                            }
                        } else {
                            // 眼睛关闭：不绑定相机，省电；图标/文字可点击重新开启，
                            // 展开高度不足扫描区最小比例时自动撑开（见 VM.reopenCameraWithMinHeight）
                            val offSlashColor = MaterialTheme.colorScheme.onSurfaceVariant
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                modifier = Modifier.clickable { viewModel.reopenCameraWithMinHeight() }
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.QrCodeScanner,
                                    contentDescription = "开启摄像头",
                                    tint = Color(0xFF8E8A8E),
                                    modifier = Modifier
                                        .size(40.dp)
                                        .drawWithContent {
                                            drawContent()
                                            val stroke = 2.5.dp.toPx()
                                            drawLine(
                                                color = offSlashColor,
                                                start = Offset(
                                                    size.width * 0.12f,
                                                    size.height * 0.88f
                                                ),
                                                end = Offset(
                                                    size.width * 0.88f,
                                                    size.height * 0.12f
                                                ),
                                                strokeWidth = stroke,
                                                cap = StrokeCap.Round
                                            )
                                        }
                                )
                                Spacer(Modifier.height(8.dp))
                                Text(
                                    text = "摄像头已关闭，点击开启",
                                    color = Color(0xFF8E8A8E),
                                    style = MaterialTheme.typography.bodyMedium
                                )
                            }
                        }

                        // 右下横排：相册 / 手电筒（眼睛开关已移至顶栏）
                        Row(
                            modifier = Modifier
                                .align(Alignment.BottomEnd)
                                .padding(end = 12.dp, bottom = 12.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            CameraSquareButton(
                                icon = Icons.Filled.PhotoLibrary,
                                contentDescription = "相册",
                                onClick = { imagePickerLauncher.launch("image/*") }
                            )
                            CameraSquareButton(
                                icon = if (viewModel.isFlashOn) {
                                    Icons.Filled.FlashlightOn
                                } else {
                                    Icons.Filled.FlashlightOff
                                },
                                contentDescription = "手电筒",
                                enabled = viewModel.cameraEnabled,
                                active = viewModel.isFlashOn,
                                onClick = { viewModel.onToggleFlash() }
                            )
                        }
                    }
                    }

                    // 滑动拉杆：拖动调节扫描区/数据区高度
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(18.dp)
                            // 与下方数据区同色，随暗色模式切换
                            .background(MaterialTheme.colorScheme.surface)
                            .pointerInput(containerHeightPx) {
                                detectVerticalDragGestures { change, dragAmount ->
                                    change.consume()
                                    if (containerHeightPx > 0) {
                                        viewModel.onSplitRatioChange(
                                            viewModel.splitRatio +
                                                    dragAmount / containerHeightPx.toFloat()
                                        )
                                    }
                                }
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        Box(
                            modifier = Modifier
                                .width(44.dp)
                                .height(4.dp)
                                .background(
                                    color = Color(0xFF8E8A8E),
                                    shape = RoundedCornerShape(50)
                                )
                        )
                    }

                    // 数据区
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f - viewModel.splitRatio)
                            .background(MaterialTheme.colorScheme.surface)
                    ) {
                        if (isTextMode) {
                            // 表头+查找条整体置顶：zIndex 提到文本编辑层之上并铺不透明底，
                            // 滚动/橡皮筋位移的文本永远压不到文档选择器与图标
                            Column(
                                Modifier
                                    .zIndex(2f)
                                    .fillMaxWidth()
                                    .background(MaterialTheme.colorScheme.surface)
                            ) {
                                TextModeHeader(
                                    viewModel = viewModel,
                                    findActive = textFindState.visible,
                                    onToggleFind = { textFindState.visible = !textFindState.visible },
                                    onCopyAll = {
                                        copyToClipboard(viewModel.textFieldValue.text)
                                        showToast("已复制全文")
                                    }
                                )
                                // 查找/替换工具条（展开时文本输入框下移，不影响输入）
                                AnimatedVisibility(visible = textFindState.visible) {
                                    TextFindBar(
                                        state = textFindState,
                                        onPrev = { goFindMatch(-1) },
                                        onNext = { goFindMatch(1) },
                                        onReplace = { replaceCurrentMatch() },
                                        onReplaceAll = { replaceAllMatches() },
                                        onClose = { textFindState.visible = false }
                                    )
                                }
                            }
                            // key() 强制重建：全部替换等长时受控文本框可能不重绘
                            key(editorResetKey) {
                            LineNumberTextEditor(
                                value = viewModel.textFieldValue,
                                onValueChange = viewModel::onTextEditorChange,
                                findState = textFindState,
                                onSelectRange = viewModel::selectTextRange,
                                keyboardDisabled = viewModel.keyboardDisabled,
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(horizontal = 4.dp)
                            )
                            }
                        } else {
                            TemplateModeHeader(
                                viewModel = viewModel,
                                count = displayItems.size,
                                onClear = { viewModel.clearAllScans(showToast) }
                            )

                            LazyColumn(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(horizontal = 8.dp, vertical = 4.dp)
                            ) {
                                items(
                                    items = displayItems,
                                    key = { it.id }
                                ) { item ->
                                    val isDuplicate = item.scanData.text in duplicateTextSet

                                    // 用最新状态读值：controller 只随 id 重建，
                                    // 闭包里不能捕获初始 item，否则复制/删除/保存拿到的是旧数据
                                    val latestItem by rememberUpdatedState(item)

                                    val controller = remember(item.id) {
                                        ResultItemController(
                                            initialItem = item,
                                            onDelete = { viewModel.deleteItemById(latestItem.id) },
                                            onClickCopy = {
                                                copyToClipboard(latestItem.scanData.text)
                                                showToast(latestItem.scanData.text)
                                            },
                                            onUpdate = { updatedItem ->
                                                viewModel.updateItemById(latestItem.id, updatedItem)
                                            }
                                        )
                                    }

                                    LaunchedEffect(item) { controller.syncItem(item) }

                                    controller.Render(
                                        highlight = isDuplicate,
                                        templateTags = viewModel.activeTemplate?.tags.orEmpty()
                                    )

                                    HorizontalDivider(
                                        thickness = DividerDefaults.Thickness,
                                        color = MaterialTheme.colorScheme.outlineVariant
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // 右侧抽屉：模板管理/编辑（模板模式）
            TemplateEditorRightDrawer(
                visible = viewModel.showTemplateEditor,
                onDismiss = { viewModel.showTemplateEditor = false },
                viewModel = viewModel
            )

            // 右侧抽屉：文档管理（文本模式）
            TextDocsManagerDrawer(
                visible = viewModel.showTextDocsManager,
                onDismiss = { viewModel.showTextDocsManager = false },
                viewModel = viewModel
            )
        }
    }

    if (viewModel.pendingDiscreteScanCode != null) {
        viewModel.activeTemplate?.let { template ->
            DiscreteRoomSelectionDialog(
                template = template,
                initialFloor = viewModel.preferredDiscreteFloor(template),
                initialTag = template.lastSelectedTag,
                onConfirm = { floor, room, tag ->
                    viewModel.confirmDiscreteScan(
                        floor = floor,
                        room = room,
                        tag = tag,
                        copyToClipboard = copyToClipboard,
                        showToast = showToast
                    )
                },
                onDismiss = viewModel::cancelPendingDiscreteScan
            )
        }
    }

    if (viewModel.showUrlChangeDialog) {
        ChangeServerUrlDialog(
            newUrl = viewModel.pendingNewUrl,
            onConfirm = {
                // 先探测目标确为 CatScan 服务端再切换，防止伪造二维码引流
                viewModel.confirmServerUrlChange(viewModel.pendingNewUrl, showToast)
            },
            onDismiss = { viewModel.showUrlChangeDialog = false }
        )
    }

    // 被动发现 PC 后弹窗
    viewModel.discoveredPcToNotify?.let { server ->
        DiscoveredPcDialog(
            server = server,
            onUse = {
                viewModel.onUseDiscoveredPc(server)
                showToast("已切换到: ${server.url}")
            },
            onDismiss = { viewModel.dismissDiscoveredPcDialog(ignoredServer = server) }
        )
    }
}

/** 模板模式数据区表头：模板切换（加宽补位）+ 楼层 + 计数 + 清空 */
@Composable
private fun TemplateModeHeader(
    viewModel: MainViewModel,
    count: Int,
    onClear: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(48.dp)
            .padding(horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        // 模板选择器（调宽，占据原红点位置）
        val templates = viewModel.templates
        val activeTemplateName = viewModel.activeTemplate?.name?.ifBlank { "未命名模板" } ?: "无模板"
        var templateMenuExpanded by remember { mutableStateOf(false) }

        Box(modifier = Modifier.weight(1f)) {
            AssistChip(
                onClick = { if (templates.isNotEmpty()) templateMenuExpanded = true },
                enabled = templates.isNotEmpty(),
                modifier = Modifier.fillMaxWidth(),
                label = {
                    Text(
                        text = activeTemplateName,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        style = MaterialTheme.typography.bodyMedium
                    )
                },
                trailingIcon = {
                    Icon(
                        imageVector = Icons.Default.ArrowDropDown,
                        contentDescription = "选择模板",
                        modifier = Modifier.size(18.dp)
                    )
                },
                colors = AssistChipDefaults.assistChipColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    labelColor = MaterialTheme.colorScheme.onPrimaryContainer
                )
            )

            DropdownMenu(
                expanded = templateMenuExpanded,
                onDismissRequest = { templateMenuExpanded = false },
                modifier = Modifier.widthIn(max = 240.dp)
            ) {
                // 添加"无模板"选项
                DropdownMenuItem(
                    text = {
                        Text(
                            text = "无模板",
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            style = MaterialTheme.typography.bodyMedium
                        )
                    },
                    onClick = {
                        viewModel.clearActiveTemplate()
                        templateMenuExpanded = false
                    }
                )

                HorizontalDivider()

                // 模板列表
                templates.forEach { t ->
                    DropdownMenuItem(
                        text = {
                            Text(
                                text = t.name.ifBlank { "未命名模板" },
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                style = MaterialTheme.typography.bodyMedium
                            )
                        },
                        onClick = {
                            viewModel.setActiveTemplate(t.id)
                            templateMenuExpanded = false
                        }
                    )
                }
            }
        }

        // 楼层选择器（始终显示）
        val hasTemplate = viewModel.activeTemplate != null
        var floorMenuExpanded by remember { mutableStateOf(false) }

        Box(modifier = Modifier.width(70.dp)) {
            AssistChip(
                onClick = { if (hasTemplate) floorMenuExpanded = true },
                enabled = hasTemplate,
                modifier = Modifier.fillMaxWidth(),
                label = {
                    Text(
                        text = if (hasTemplate) "${viewModel.scanSelectedFloor}层" else "无",
                        style = MaterialTheme.typography.bodySmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                },
                trailingIcon = {
                    Icon(
                        imageVector = Icons.Default.ArrowDropDown,
                        contentDescription = "选择楼层",
                        modifier = Modifier.size(16.dp)
                    )
                },
                colors = AssistChipDefaults.assistChipColors(
                    containerColor = MaterialTheme.colorScheme.secondaryContainer,
                    labelColor = MaterialTheme.colorScheme.onSecondaryContainer,
                    disabledContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                    disabledLabelColor = MaterialTheme.colorScheme.onSurfaceVariant
                )
            )

            DropdownMenu(
                expanded = floorMenuExpanded,
                onDismissRequest = { floorMenuExpanded = false }
            ) {
                val maxFloor = viewModel.activeTemplate?.maxFloor ?: 0
                for (f in 1..maxFloor.coerceAtLeast(1)) {
                    DropdownMenuItem(
                        text = {
                            Text(
                                text = "${f}层",
                                style = MaterialTheme.typography.bodyMedium
                            )
                        },
                        onClick = {
                            viewModel.selectScanFloor(f)
                            floorMenuExpanded = false
                        }
                    )
                }
            }
        }

        // 计数牌
        Box(
            modifier = Modifier
                .width(56.dp)
                .height(32.dp)
                .background(
                    color = MaterialTheme.colorScheme.primaryContainer,
                    shape = MaterialTheme.shapes.small
                ),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = String.format("%03d", count),
                style = MaterialTheme.typography.bodySmall.copy(
                    fontWeight = androidx.compose.ui.text.font.FontWeight.Bold
                ),
                color = MaterialTheme.colorScheme.onPrimaryContainer
            )
        }

        // 清空按钮
        Box(modifier = Modifier.width(70.dp)) {
            AssistChip(
                onClick = onClear,
                modifier = Modifier.fillMaxWidth(),
                label = {
                    Text(
                        text = "清空",
                        style = MaterialTheme.typography.bodySmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth()
                    )
                },
                colors = AssistChipDefaults.assistChipColors(
                    containerColor = MaterialTheme.colorScheme.errorContainer,
                    labelColor = MaterialTheme.colorScheme.onErrorContainer
                )
            )
        }
    }
}

/** 文本模式数据区表头：文档选择 / 查找替换 / 复制 / 自动换行 / 撤回 */
@Composable
private fun TextModeHeader(
    viewModel: MainViewModel,
    findActive: Boolean,
    onToggleFind: () -> Unit,
    onCopyAll: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(48.dp)
            .padding(horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        // 文档选择（仅切换，不做管理）
        val docs = viewModel.textDocs
        val activeDocName = docs.firstOrNull { it.id == viewModel.activeTextDocId }?.name
            ?.ifBlank { "未命名文档" } ?: "无文档"
        var docMenuExpanded by remember { mutableStateOf(false) }

        Box(modifier = Modifier.weight(1f)) {
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(36.dp),
                shape = RoundedCornerShape(8.dp),
                color = MaterialTheme.colorScheme.primaryContainer,
                onClick = { if (docs.isNotEmpty()) docMenuExpanded = true },
                enabled = docs.isNotEmpty()
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = activeDocName,
                        modifier = Modifier.weight(1f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                    Icon(
                        imageVector = Icons.Default.ArrowDropDown,
                        contentDescription = "文档选择",
                        tint = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }

            DropdownMenu(
                expanded = docMenuExpanded,
                onDismissRequest = { docMenuExpanded = false },
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.widthIn(max = 240.dp)
            ) {
                docs.forEach { doc ->
                    val isActive = doc.id == viewModel.activeTextDocId
                    DropdownMenuItem(
                        text = {
                            Text(
                                text = doc.name.ifBlank { "未命名文档" },
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                style = MaterialTheme.typography.bodyMedium,
                                color = if (isActive) {
                                    MaterialTheme.colorScheme.primary
                                } else {
                                    MaterialTheme.colorScheme.onSurface
                                }
                            )
                        },
                        onClick = {
                            viewModel.setActiveTextDoc(doc.id)
                            docMenuExpanded = false
                        }
                    )
                }
            }
        }

        // 禁用输入法（运行态开关，默认不禁用）：开启后点击/拖动光标均不弹软键盘。
        // 视觉与摄像头开关一致：禁用=灰色键盘+斜线，启用=蓝底蓝色键盘无斜线
        val softKeyboard = LocalSoftwareKeyboardController.current
        HeaderIconButton(
            icon = Icons.Filled.Keyboard,
            contentDescription = if (viewModel.keyboardDisabled) "启用输入法" else "禁用输入法",
            active = !viewModel.keyboardDisabled,
            slashed = viewModel.keyboardDisabled,
            onClick = {
                viewModel.keyboardDisabled = !viewModel.keyboardDisabled
                if (viewModel.keyboardDisabled) softKeyboard?.hide()
            }
        )

        // 自动换行开关
        HeaderIconButton(
            icon = Icons.AutoMirrored.Filled.WrapText,
            contentDescription = "自动换行",
            active = viewModel.textAutoNewline,
            onClick = { viewModel.onTextAutoNewlineChange(!viewModel.textAutoNewline) }
        )

        // 复制到剪贴板
        HeaderIconButton(
            icon = Icons.Default.ContentCopy,
            contentDescription = "复制到剪贴板",
            onClick = onCopyAll
        )

        // 查找替换
        HeaderIconButton(
            icon = Icons.Default.Search,
            contentDescription = "查找替换",
            active = findActive,
            onClick = onToggleFind
        )

        // 撤回
        HeaderIconButton(
            icon = Icons.AutoMirrored.Filled.Undo,
            contentDescription = "撤回",
            enabled = viewModel.canUndoText,
            onClick = { viewModel.undoText() }
        )
    }
}

/** 文本模式表头用的小图标按钮：36dp 方形，与文档选择器同高 */
@Composable
private fun HeaderIconButton(
    icon: ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    active: Boolean = false,
    slashed: Boolean = false
) {
    val slashColor = MaterialTheme.colorScheme.onSurfaceVariant
    Box(
        modifier = modifier
            .size(36.dp)
            .background(
                color = if (active) {
                    MaterialTheme.colorScheme.primaryContainer
                } else {
                    Color.Transparent
                },
                shape = RoundedCornerShape(8.dp)
            )
            .clickable(enabled = enabled) { onClick() },
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            tint = when {
                !enabled -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.38f)
                active -> MaterialTheme.colorScheme.primary
                else -> MaterialTheme.colorScheme.onSurfaceVariant
            },
            modifier = Modifier
                .size(20.dp)
                .then(
                    if (slashed) {
                        Modifier.drawWithContent {
                            drawContent()
                            val stroke = 2.dp.toPx()
                            drawLine(
                                color = slashColor,
                                start = Offset(size.width * 0.12f, size.height * 0.88f),
                                end = Offset(size.width * 0.88f, size.height * 0.12f),
                                strokeWidth = stroke,
                                cap = StrokeCap.Round
                            )
                        }
                    } else {
                        Modifier
                    }
                )
        )
    }
}

/** 扫描区方形半透明灰底图标按钮（眼睛/相册/手电筒） */
@Composable
private fun CameraSquareButton(
    icon: ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    active: Boolean = false
) {
    Box(
        modifier = modifier
            .size(36.dp)
            .background(
                color = when {
                    !enabled -> Color.Gray.copy(alpha = 0.25f)
                    active -> MaterialTheme.colorScheme.primary.copy(alpha = 0.8f)
                    else -> Color.Gray.copy(alpha = 0.5f)
                },
                shape = RoundedCornerShape(6.dp)
            )
            .clickable(enabled = enabled) { onClick() },
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            tint = Color.White.copy(alpha = if (enabled) 1f else 0.6f),
            modifier = Modifier.size(20.dp)
        )
    }
}

/** 查找/替换工具条（文本模式，VSCode Ctrl+F 风格）：默认一行查找，左侧箭头展开第二行替换 */
@Composable
private fun TextFindBar(
    state: TextFindState,
    onPrev: () -> Unit,
    onNext: () -> Unit,
    onReplace: () -> Unit,
    onReplaceAll: () -> Unit,
    onClose: () -> Unit
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 4.dp, vertical = 2.dp),
        shape = RoundedCornerShape(10.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f),
        tonalElevation = 1.dp
    ) {
        Column(Modifier.padding(horizontal = 6.dp, vertical = 5.dp)) {
            // 两行右侧操作区宽度自适应且严格等宽（取两者内容的最大自然宽），
            // 输入框 weight(1f) 吃掉释放出的全部剩余空间——按钮变宽时 input 同步让步缩短。
            // 用 widthIn(min=) 而非 width()：外层只设最小宽度，内层 wrapContent 可自由撑大，
            // 这样 onSizeChanged 能上报真实自然宽，操作区变宽（如出现"无结果"）时不会被
            // 旧宽度卡死导致关闭按钮被挤没。
            var actionsWidthDp by remember { mutableStateOf(0.dp) }
            val barDensity = LocalDensity.current
            val reportActionsWidth: (Int) -> Unit = { wPx ->
                val w = with(barDensity) { wPx.toDp() }
                if (w > actionsWidthDp) actionsWidthDp = w
            }
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                // 展开/收起第二行替换
                Box(
                    modifier = Modifier
                        .size(28.dp)
                        .clickable { state.showReplace = !state.showReplace },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = if (state.showReplace) {
                            Icons.Default.KeyboardArrowDown
                        } else {
                            Icons.Default.ChevronRight
                        },
                        contentDescription = if (state.showReplace) "收起替换" else "展开替换",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(20.dp)
                    )
                }

                FindTextField(
                    value = state.query,
                    onValueChange = { state.query = it },
                    hint = "查找",
                    modifier = Modifier.weight(1f)
                )

                // 外层按等宽值对齐（仅设最小宽，内层可自由撑大上报真实宽），内层 wrapContent 上报自然宽
                Row(
                    modifier = Modifier.then(
                        if (actionsWidthDp > 0.dp) Modifier.widthIn(min = actionsWidthDp) else Modifier
                    ),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        modifier = Modifier.onSizeChanged { reportActionsWidth(it.width) },
                        horizontalArrangement = Arrangement.spacedBy(2.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        val countText = when {
                            state.query.isEmpty() -> ""
                            state.matches.isEmpty() -> "无结果"
                            else -> "${state.currentIndex + 1}/${state.matches.size}"
                        }
                        Text(
                            text = countText,
                            style = MaterialTheme.typography.labelMedium,
                            color = if (state.query.isNotEmpty() && state.matches.isEmpty()) {
                                MaterialTheme.colorScheme.error
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            },
                            textAlign = TextAlign.Center,
                            maxLines = 1,
                            softWrap = false
                        )

                        val hasMatches = state.matches.isNotEmpty()
                        FindBarIconButton(
                            icon = Icons.Default.KeyboardArrowUp,
                            contentDescription = "上一个",
                            enabled = hasMatches,
                            onClick = onPrev
                        )
                        FindBarIconButton(
                            icon = Icons.Default.KeyboardArrowDown,
                            contentDescription = "下一个",
                            enabled = hasMatches,
                            onClick = onNext
                        )
                        FindBarIconButton(
                            icon = Icons.Default.Close,
                            contentDescription = "关闭查找",
                            onClick = onClose
                        )
                    }
                }
            }

            AnimatedVisibility(visible = state.showReplace) {
                Column {
                    // 两个输入框之间留空隙
                    Spacer(Modifier.height(6.dp))
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Spacer(Modifier.width(28.dp))
                        FindTextField(
                            value = state.replacement,
                            onValueChange = { state.replacement = it },
                            hint = "替换为（留空即删除）",
                            modifier = Modifier.weight(1f)
                        )
                        Row(
                            modifier = Modifier.then(
                                if (actionsWidthDp > 0.dp) Modifier.widthIn(min = actionsWidthDp) else Modifier
                            ),
                            horizontalArrangement = Arrangement.End,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(
                                modifier = Modifier.onSizeChanged { reportActionsWidth(it.width) },
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                FindBarTextButton(
                                    text = "替换",
                                    onClick = onReplace
                                )
                                FindBarTextButton(
                                    text = "全部替换",
                                    onClick = onReplaceAll
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun FindBarIconButton(
    icon: ImageVector,
    contentDescription: String,
    enabled: Boolean = true,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .size(28.dp)
            .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            tint = if (enabled) {
                MaterialTheme.colorScheme.onSurfaceVariant
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.38f)
            },
            modifier = Modifier.size(18.dp)
        )
    }
}

@Composable
private fun FindBarTextButton(text: String, onClick: () -> Unit) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.primary,
        maxLines = 1,
        softWrap = false,
        modifier = Modifier
            .clickable(onClick = onClick)
            .defaultMinSize(minWidth = 44.dp, minHeight = 32.dp)
            .padding(horizontal = 8.dp, vertical = 6.dp)
    )
}

/** 紧凑单行输入框（查找/替换共用），内容非空时右侧显示 X 一键清空 */
@Composable
private fun FindTextField(
    value: String,
    onValueChange: (String) -> Unit,
    hint: String,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .height(30.dp)
            .background(
                MaterialTheme.colorScheme.surface.copy(alpha = 0.92f),
                RoundedCornerShape(8.dp)
            )
            .padding(start = 10.dp, end = 4.dp),
        contentAlignment = Alignment.CenterStart
    ) {
        if (value.isEmpty()) {
            Text(
                text = hint,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                maxLines = 1
            )
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            BasicTextField(
                value = value,
                onValueChange = onValueChange,
                singleLine = true,
                textStyle = MaterialTheme.typography.bodySmall.copy(
                    color = MaterialTheme.colorScheme.onSurface
                ),
                cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                modifier = Modifier.weight(1f)
            )
            if (value.isNotEmpty()) {
                Box(
                    modifier = Modifier
                        .size(22.dp)
                        .clickable { onValueChange("") },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "清空",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(14.dp)
                    )
                }
            }
        }
    }
}
