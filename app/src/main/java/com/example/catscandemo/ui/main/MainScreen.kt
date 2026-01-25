package com.example.catscandemo.ui.main

import android.annotation.SuppressLint
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import com.example.catscandemo.ui.components.CameraPreview
import com.example.catscandemo.ui.components.ChangeServerUrlDialog
import com.example.catscandemo.ui.components.DiscoveredPcDialog
import com.example.catscandemo.ui.components.ResultItemController
import com.example.catscandemo.ui.components.SettingsDrawer
import com.example.catscandemo.ui.components.TemplateEditorRightDrawer
import com.example.catscandemo.utils.AutoRequestCameraPermission
import kotlinx.coroutines.launch
import androidx.compose.foundation.lazy.items
import androidx.compose.ui.text.style.TextOverflow

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

    val scanItems = viewModel.historyManager.items
    fun parseFloorNumberLocal(floorStr: String): Int? {
        return Regex("\\d+").find(floorStr)?.value?.toIntOrNull()
    }

    val activeId = viewModel.activeTemplateId

    val displayItems by remember(scanItems, activeId, viewModel.scanSelectedFloor) {
        derivedStateOf {
            if (activeId.isNullOrBlank()) {
                scanItems
            } else {
                scanItems.filter {
                    // 兼容旧数据：templateId 为空的也暂时算到当前模板（可选）
                    (it.templateId == activeId || it.templateId.isBlank()) &&
                            parseFloorNumberLocal(it.area.floor) == viewModel.scanSelectedFloor
                }
            }
        }
    }



    val duplicateTextSet by remember {
        derivedStateOf {
            displayItems.groupBy { it.text }
                .filterValues { it.size > 1 }
                .keys
        }
    }



    // TopBar title：校区 + 楼栋（实时，字体更小）
    val titleText by remember {
        derivedStateOf {
            val t = viewModel.getActiveTemplate()
            val campus = t?.campus?.trim().orEmpty()
            val building = t?.building?.trim().orEmpty()
            when {
                campus.isNotBlank() && building.isNotBlank() -> "$campus · $building"
                campus.isNotBlank() -> campus
                building.isNotBlank() -> building
                else -> "CatScan"
            }
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
                onClipboardEnabledChange = { viewModel.clipboardEnabled = it },
                duplicateScanEnabled = viewModel.duplicateScanEnabled,
                onDuplicateScanEnabledChange = { viewModel.duplicateScanEnabled = it }
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
                            .background(Color(0xFF488ECC)),
                        contentAlignment = Alignment.Center
                    ) {
                        AutoRequestCameraPermission {
                            CameraPreview(
                                onBarcodeDetected = { code ->
                                    viewModel.onBarcodeScanned(code, copyToClipboard, showToast)
                                },
                                onCameraReady = { cam -> viewModel.camera = cam }
                            )
                        }

                        Column(
                            modifier = Modifier
                                .align(Alignment.BottomEnd)
                                .padding(end = 16.dp, bottom = 16.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
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
                            .background(Color(0xFFF5F5F5))
                    ) {
                        // 识别结果行：楼层选择（折叠/下拉） + “识别结果”
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 12.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            // 左：识别结果
                            Text("识别结果")

                            Spacer(Modifier.weight(1f))

                            // ===== 右侧：模板选择器（固定宽度 + 超长省略）=====
                            val templates = viewModel.templates
                            val activeTemplateId = viewModel.activeTemplateId
                            val activeTemplateName = viewModel.getActiveTemplate()?.name?.ifBlank { "未命名模板" } ?: "无模板"
                            var templateMenuExpanded by remember { mutableStateOf(false) }

                            Box {
                                AssistChip(
                                    onClick = { if (templates.isNotEmpty()) templateMenuExpanded = true },
                                    enabled = templates.isNotEmpty(),
                                    modifier = Modifier.width(120.dp), // 固定大小
                                    label = {
                                        Text(
                                            text = activeTemplateName,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    },
                                    trailingIcon = {
                                        Icon(
                                            imageVector = Icons.Default.ArrowDropDown,
                                            contentDescription = "选择模板"
                                        )
                                    }
                                )

                                DropdownMenu(
                                    expanded = templateMenuExpanded,
                                    onDismissRequest = { templateMenuExpanded = false }
                                ) {
                                    templates.forEach { t ->
                                        DropdownMenuItem(
                                            text = {
                                                Text(
                                                    text = t.name.ifBlank { "未命名模板" },
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis,
                                                    modifier = Modifier.width(240.dp) // 下拉列表文字固定宽度
                                                )
                                            },
                                            onClick = {
                                                viewModel.setActiveTemplate(t.id)   // 切换模板
                                                templateMenuExpanded = false
                                            }
                                        )
                                    }
                                }
                            }

                            Spacer(Modifier.width(10.dp))

// ===== 右侧：楼层下拉（无模板时显示“无”）=====
                            val activeTemplate = viewModel.getActiveTemplate()
                            val maxFloor = activeTemplate?.maxFloor ?: 0
                            val hasTemplate = activeTemplate != null
                            var floorMenuExpanded by remember { mutableStateOf(false) }

                            Box {
                                AssistChip(
                                    onClick = { if (hasTemplate) floorMenuExpanded = true },
                                    enabled = hasTemplate,
                                    label = { Text(if (hasTemplate) "${viewModel.scanSelectedFloor}层" else "无") },
                                    trailingIcon = {
                                        Icon(
                                            imageVector = Icons.Default.ArrowDropDown,
                                            contentDescription = "选择楼层"
                                        )
                                    }
                                )

                                DropdownMenu(
                                    expanded = floorMenuExpanded,
                                    onDismissRequest = { floorMenuExpanded = false }
                                ) {
                                    for (f in 1..maxFloor.coerceAtLeast(1)) {
                                        DropdownMenuItem(
                                            text = { Text("${f}层") },
                                            onClick = {
                                                viewModel.selectScanFloor(f)
                                                floorMenuExpanded = false
                                            }
                                        )
                                    }
                                }
                            }

                            Spacer(Modifier.width(10.dp))

                            // ===== 右侧：已识别数量（当前楼层/当前模板的 displayItems 数量）=====
                            Text(
                                text = "已识别：${displayItems.size}",
                                style = MaterialTheme.typography.bodySmall,
                                color = Color(0xFF6B7280)
                            )



                        }

                        LazyColumn(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(horizontal = 4.dp)
                        ) {
                            items(
                                items = displayItems,
                                key = { it.id }
                            )  { item ->
                                val isDuplicate = item.text in duplicateTextSet

                                val controller = remember(item.id) {
                                    ResultItemController(
                                        initialItem = item,
                                        onDelete = { viewModel.deleteItemById(item.id) },
                                        onClickCopy = {
                                            copyToClipboard(item.text)
                                            showToast(item.text)
                                        },
                                        onUpdate = { updatedItem ->
                                            viewModel.updateItemById(item.id, updatedItem)
                                        }
                                    )
                                }

                                LaunchedEffect(item) { controller.syncItem(item) }
                                controller.Render(highlight = isDuplicate)

                                HorizontalDivider(
                                    thickness = DividerDefaults.Thickness,
                                    color = Color.LightGray
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
            .size(50.dp)
            .background(if (isOn) Color(0xFFFFC107) else Color(0xFF9E9E9E), CircleShape)
            .clickable(
                interactionSource = interactionSource,
                indication = null
            ) { onToggle() },
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = if (isOn) Icons.Filled.FlashlightOn else Icons.Filled.FlashlightOff,
            contentDescription = "手电筒",
            tint = Color.White
        )
    }
}

@Composable
fun AlbumButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .shadow(8.dp, CircleShape)
            .size(50.dp)
            .background(Color(0xFF9E9E9E), CircleShape)
            .clickable { onClick() },
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = Icons.Filled.PhotoLibrary,
            contentDescription = "相册",
            tint = Color.White
        )
    }
}
