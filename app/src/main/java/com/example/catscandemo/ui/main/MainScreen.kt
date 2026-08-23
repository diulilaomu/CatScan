package com.example.catscandemo.ui.main



import android.annotation.SuppressLint

import android.net.Uri

import androidx.activity.compose.rememberLauncherForActivityResult

import androidx.activity.result.contract.ActivityResultContracts

import androidx.compose.animation.core.animateDpAsState

import androidx.compose.animation.core.tween

import androidx.compose.foundation.background

import androidx.compose.foundation.border

import androidx.compose.foundation.clickable

import androidx.compose.foundation.interaction.MutableInteractionSource

import androidx.compose.foundation.interaction.collectIsPressedAsState

import androidx.compose.foundation.layout.*

import androidx.compose.ui.platform.LocalConfiguration

import androidx.compose.foundation.lazy.LazyColumn

import androidx.compose.foundation.shape.CircleShape

import androidx.compose.material.icons.Icons

import androidx.compose.material.icons.filled.ArrowDropDown

import androidx.compose.material.icons.filled.Description

import androidx.compose.material.icons.filled.FlashlightOff

import androidx.compose.material.icons.filled.FlashlightOn

import androidx.compose.material.icons.filled.Menu

import androidx.compose.material.icons.filled.PhotoLibrary

import androidx.compose.material3.*

import androidx.compose.runtime.*

import androidx.compose.runtime.collectAsState

import androidx.compose.ui.Alignment

import androidx.compose.ui.Modifier

import androidx.compose.ui.draw.shadow

import androidx.compose.ui.graphics.Color

import androidx.compose.ui.platform.LocalClipboardManager

import androidx.compose.ui.platform.LocalContext

import androidx.compose.ui.text.AnnotatedString

import androidx.compose.ui.unit.dp

import com.example.catscandemo.presentation.viewmodel.MainViewModel

import com.example.catscandemo.ui.components.CameraPreview

import com.example.catscandemo.ui.components.ChangeServerUrlDialog

import com.example.catscandemo.ui.components.DiscoveredPcDialog

import com.example.catscandemo.ui.components.DiscreteRoomSelectionDialog

import com.example.catscandemo.ui.components.ResultItemController

import com.example.catscandemo.ui.components.SettingsDrawer

import com.example.catscandemo.ui.components.TemplateEditorRightDrawer

import com.example.catscandemo.utils.AutoRequestCameraPermission

import kotlinx.coroutines.launch

import androidx.compose.foundation.lazy.items

import androidx.compose.ui.text.style.TextOverflow

import androidx.compose.ui.text.style.TextAlign



@SuppressLint("ConfigurationScreenWidthHeight")

@OptIn(ExperimentalMaterial3Api::class)

@Composable

fun MainScreen(viewModel: MainViewModel) {

    val context = LocalContext.current

    val clipboardManager = LocalClipboardManager.current

    val coroutineScope = rememberCoroutineScope()

    val drawerState = rememberDrawerState(DrawerValue.Closed)



    // 初始化模板离线存储（只做一次）

    LaunchedEffect(Unit) {

        viewModel.initTemplateStore(context.applicationContext)

        viewModel.initHistoryStore(context.applicationContext)

        viewModel.initSettingsStore(context.applicationContext)

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

        return Regex("\\d+").find(floorStr)?.value?.toIntOrNull()

    }



    val activeId = viewModel.activeTemplateId

    val selectedFloor = viewModel.scanSelectedFloor



    val displayItems = remember(activeId, selectedFloor, scanItems) {

        val result = if (activeId.isNullOrBlank()) {

            scanItems

        } else {

            scanItems.filter {

                // 兼容旧数据：templateId 为空的也暂时算到当前模板（可选）

                (it.scanData.templateId == activeId || it.scanData.templateId.isBlank()) &&

                        parseFloorNumberLocal(it.scanData.floor) == selectedFloor

            }

        }

        // 确保每次都返回一个新的列表对象，触发LazyColumn更新

        result.toList()

    }







    val duplicateTextSet = remember(displayItems) {

        displayItems.groupBy { it.scanData.text }

            .filterValues { it.size > 1 }

            .keys

    }







    // TopBar title：校区 + 楼栋 + 当前房间号

    val titleTemplate = viewModel.activeTemplate
    val titleRoom = viewModel.currentRoom.trim()
    val titleText = remember(titleTemplate, titleRoom) {

        val campus = titleTemplate?.campus?.trim().orEmpty()

        val building = titleTemplate?.building?.trim().orEmpty()

        listOf(campus, building, titleRoom)
            .filter { it.isNotBlank() }
            .joinToString(" · ")
            .ifBlank { "CatScan" }

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

                onClipboardEnabledChange = { viewModel.clipboardEnabled = it },

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

                                style = MaterialTheme.typography.titleMedium // 比默认更小

                            )

                        },

                        navigationIcon = {

                            IconButton(onClick = { coroutineScope.launch { drawerState.open() } }) {

                                Icon(

                                    imageVector = Icons.Default.Menu,

                                    contentDescription = "菜单",

                                    modifier = Modifier.size(24.dp)

                                )

                            }

                        },

                        actions = {

                            IconButton(onClick = { viewModel.showTemplateEditor = true }) {

                                Icon(

                                    imageVector = Icons.Filled.Description,

                                    contentDescription = "模板管理/编辑",

                                    modifier = Modifier.size(24.dp)

                                )

                            }

                        }

                    )

                }

            ) { innerPadding ->

                Column(

                    modifier = Modifier

                        .fillMaxSize()

                        .padding(innerPadding)

                ) {

                    // 摄像头区域

                    Box(

                        modifier = Modifier

                            .fillMaxWidth()

                            .heightIn(min = 220.dp)

                            .weight(1f)

                            .background(MaterialTheme.colorScheme.primary),

                        contentAlignment = Alignment.Center

                    ) {

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



                        Column(

                            modifier = Modifier

                                .align(Alignment.BottomEnd)

                                .padding(end = 16.dp, bottom = 16.dp),

                            verticalArrangement = Arrangement.spacedBy(16.dp)

                        ) {

                            AlbumButton(onClick = { imagePickerLauncher.launch("image/*") })

                            FlashlightButton(

                                isOn = viewModel.isFlashOn,

                                onToggle = { viewModel.onToggleFlash() }

                            )

                        }

                    }



                    // 列表区域

                    Column(

                        modifier = Modifier

                            .fillMaxWidth()

                            .weight(1.2f)

                            .background(MaterialTheme.colorScheme.surface)

                    ) {

                        // 识别结果行：楼层选择（折叠/下拉） + “识别结果”

                        val configuration = LocalConfiguration.current

                        val screenWidth = configuration.screenWidthDp.dp

                        val isSmallScreen = screenWidth < 400.dp

                        

                        // 使用稳定的布局结构，避免抖动
                        val headerHeight = 48.dp
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(headerHeight)
                                .padding(horizontal = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceAround
                        ) {
                            // 模板选择器
                            val templates by remember(viewModel.templates) { mutableStateOf(viewModel.templates) }
                            val activeTemplateName = remember(viewModel.activeTemplate) {
                                viewModel.activeTemplate?.name?.ifBlank { "未命名模板" } ?: "无模板"
                            }
                            var templateMenuExpanded by remember { mutableStateOf(false) }

                            Box(modifier = Modifier.width(100.dp)) {
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

                                    // 分隔线
                                    Divider()

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
                            val hasTemplate = remember(viewModel.activeTemplate) {
                                viewModel.activeTemplate != null
                            }
                            val selectedFloor = remember(viewModel.scanSelectedFloor) {
                                viewModel.scanSelectedFloor
                            }
                            var floorMenuExpanded by remember { mutableStateOf(false) }

                            Box(modifier = Modifier.width(70.dp)) {
                                AssistChip(
                                    onClick = { if (hasTemplate) floorMenuExpanded = true },
                                    enabled = hasTemplate,
                                    modifier = Modifier.fillMaxWidth(),
                                    label = {
                                        Text(
                                            text = if (hasTemplate) "${selectedFloor}层" else "无",
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

                            // 计数牌 - 改成3位
                            val count = displayItems.size
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
                                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                                    modifier = Modifier
                                        .width(48.dp)
                                        .height(24.dp)
                                        .wrapContentSize()
                                )
                            }

                            // 清空按钮（始终显示）
                            Box(modifier = Modifier.width(70.dp)) {
                                AssistChip(
                                    onClick = { viewModel.clearAllScans(showToast) },
                                    enabled = true,
                                    modifier = Modifier.fillMaxWidth(),
                                    label = {
                                        Text(
                                            text = "清空本层",
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

                            // 连接状态指示器
                            val isConnected = remember(viewModel.uploadEnabled, viewModel.serverUrl) {
                                viewModel.uploadEnabled && viewModel.serverUrl.isNotEmpty()
                            }
                            Box(
                                modifier = Modifier
                                    .size(8.dp)
                                    .background(
                                        color = if (isConnected) {
                                            MaterialTheme.colorScheme.primary // 已连接：蓝色
                                        } else {
                                            MaterialTheme.colorScheme.error // 未连接：红色
                                        },
                                        shape = CircleShape
                                    )
                            )
                        }



                        LazyColumn(

                            modifier = Modifier

                                .fillMaxSize()

                                .padding(horizontal = 8.dp, vertical = 4.dp)

                        ) {

                            items(

                                items = displayItems,

                                key = { it.id }

                            )  { item ->

                                val isDuplicate = item.scanData.text in duplicateTextSet



                                val controller = remember(item.id) {

                                    ResultItemController(

                                        initialItem = item,

                                        onDelete = { viewModel.deleteItemById(item.id) },

                                        onClickCopy = {

                                            copyToClipboard(item.scanData.text)

                                            showToast(item.scanData.text)

                                        },

                                        onUpdate = { updatedItem ->

                                            viewModel.updateItemById(item.id, updatedItem)

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



            // 右侧抽屉：模板管理/编辑（一级/二级/三级）

            TemplateEditorRightDrawer(

                visible = viewModel.showTemplateEditor,

                onDismiss = { viewModel.showTemplateEditor = false },

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

                viewModel.serverUrl = viewModel.pendingNewUrl

                viewModel.uploadEnabled = true

                viewModel.showUrlChangeDialog = false

                showToast("已更新上传地址")

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



@Composable

fun FlashlightButton(

    isOn: Boolean,

    onToggle: () -> Unit,

    modifier: Modifier = Modifier

) {

    val interactionSource = remember { MutableInteractionSource() }

    val isPressed = interactionSource.collectIsPressedAsState()

    val elevation by animateDpAsState(

        targetValue = if (isPressed.value) 0.dp else 8.dp,

        animationSpec = tween(150)

    )



    Box(

        modifier = modifier

            .shadow(elevation, CircleShape)

            .size(51.dp)

            .background(

                color = if (isOn) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.surfaceVariant,

                shape = CircleShape

            )

            .clickable(

                interactionSource = interactionSource,

                indication = null

            ) { onToggle() },

        contentAlignment = Alignment.Center

    ) {

        Icon(

            imageVector = if (isOn) Icons.Filled.FlashlightOn else Icons.Filled.FlashlightOff,

            contentDescription = "手电筒",

            tint = if (isOn) MaterialTheme.colorScheme.onError else MaterialTheme.colorScheme.onSurfaceVariant,

            modifier = Modifier.size(24.dp)

        )

    }

}



@Composable

fun CounterDisplay(

    count: Int,

    modifier: Modifier = Modifier

) {

    AssistChip(

        onClick = {},

        enabled = false,

        modifier = modifier,

        label = {

            Text(

                text = String.format("%02d", count),

                style = MaterialTheme.typography.bodySmall,

                fontWeight = androidx.compose.ui.text.font.FontWeight.Bold,

                color = MaterialTheme.colorScheme.onPrimaryContainer

            )

        },

        colors = AssistChipDefaults.assistChipColors(

            containerColor = MaterialTheme.colorScheme.primaryContainer,

            labelColor = MaterialTheme.colorScheme.onPrimaryContainer,

            disabledContainerColor = MaterialTheme.colorScheme.primaryContainer,

            disabledLabelColor = MaterialTheme.colorScheme.onPrimaryContainer

        )

    )

}



@Composable

fun AlbumButton(

    onClick: () -> Unit,

    modifier: Modifier = Modifier

) {

    val interactionSource = remember { MutableInteractionSource() }

    val isPressed = interactionSource.collectIsPressedAsState()

    val elevation by animateDpAsState(

        targetValue = if (isPressed.value) 0.dp else 8.dp,

        animationSpec = tween(150)

    )



    Box(

        modifier = modifier

            .shadow(elevation, CircleShape)

            .size(51.dp)

            .background(

                color = MaterialTheme.colorScheme.surfaceVariant,

                shape = CircleShape

            )

            .clickable(

                interactionSource = interactionSource,

                indication = null

            ) { onClick() },

        contentAlignment = Alignment.Center

    ) {

        Icon(

            imageVector = Icons.Filled.PhotoLibrary,

            contentDescription = "相册",

            tint = MaterialTheme.colorScheme.onSurfaceVariant,

            modifier = Modifier.size(24.dp)

        )

    }

}

