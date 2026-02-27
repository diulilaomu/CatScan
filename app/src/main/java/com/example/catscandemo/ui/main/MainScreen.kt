package com.example.catscandemo.ui.main

import android.annotation.SuppressLint
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.CloudDone
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.FlashlightOff
import androidx.compose.material.icons.filled.FlashlightOn
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.DividerDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.catscandemo.presentation.viewmodel.MainViewModel
import com.example.catscandemo.ui.components.CameraPreview
import com.example.catscandemo.ui.components.ChangeServerUrlDialog
import com.example.catscandemo.ui.components.DiscoveredPcDialog
import com.example.catscandemo.ui.components.ResultItemController
import com.example.catscandemo.ui.components.SettingsDrawer
import com.example.catscandemo.ui.components.TemplateEditorRightDrawer
import com.example.catscandemo.utils.AutoRequestCameraPermission
import kotlinx.coroutines.launch
import java.nio.charset.Charset

private fun parseFloorNumber(floorStr: String): Int? {
    return Regex("\\d+").find(floorStr)?.value?.toIntOrNull()
}

private fun cjkCount(text: String): Int {
    var count = 0
    for (ch in text) {
        if (ch in '\u4e00'..'\u9fff') {
            count++
        }
    }
    return count
}

private fun normalizeQrToastText(rawText: String): String {
    val trimmed = rawText.trim()
    if (trimmed.isEmpty()) return ""

    val originalScore = cjkCount(trimmed)
    val candidate = runCatching {
        val bytes = trimmed.toByteArray(Charsets.ISO_8859_1)
        String(bytes, Charset.forName("GB18030"))
    }.getOrDefault(trimmed)
    val candidateScore = cjkCount(candidate)
    val best = if (candidateScore > originalScore) candidate else trimmed

    val sanitized = best.replace(Regex("\\p{C}+"), " ").trim()
    if (sanitized.length <= 120) return sanitized
    return sanitized.take(120) + "..."
}

@SuppressLint("ConfigurationScreenWidthHeight")
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(viewModel: MainViewModel) {
    val context = LocalContext.current
    val clipboardManager = LocalClipboardManager.current
    val coroutineScope = rememberCoroutineScope()
    val drawerState = rememberDrawerState(initialValue = androidx.compose.material3.DrawerValue.Closed)

    LaunchedEffect(Unit) {
        viewModel.initTemplateStore(context.applicationContext)
        viewModel.initHistoryStore(context.applicationContext)
        viewModel.initSettingsStore(context.applicationContext)
        viewModel.startPassivePcDiscovery(context.applicationContext)
        viewModel.getAllScans()
    }

    val latestContext by rememberUpdatedState(context)
    val latestClipboard by rememberUpdatedState(clipboardManager)
    val showToast: (String) -> Unit = { message ->
        android.widget.Toast.makeText(
            latestContext,
            message,
            android.widget.Toast.LENGTH_SHORT
        ).show()
    }
    val copyToClipboard: (String) -> Unit = { text ->
        latestClipboard.setText(AnnotatedString(text))
    }

    val imagePickerLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let {
            viewModel.onImagePicked(
                uri = it,
                context = latestContext,
                copyToClipboard = copyToClipboard,
                showToast = showToast
            )
        }
    }

    val scanItems by viewModel.scanResults.collectAsState()
    val activeTemplateId = viewModel.activeTemplateId
    val selectedFloor = viewModel.scanSelectedFloor

    val displayItems = remember(scanItems, activeTemplateId, selectedFloor) {
        val items = if (activeTemplateId.isNullOrBlank()) {
            scanItems
        } else {
            scanItems.filter {
                (it.scanData.templateId == activeTemplateId || it.scanData.templateId.isBlank()) &&
                    parseFloorNumber(it.scanData.floor) == selectedFloor
            }
        }
        items.toList()
    }

    val duplicateTextSet = remember(displayItems) {
        displayItems.groupBy { it.scanData.text }
            .filterValues { it.size > 1 }
            .keys
    }

    val titleText = remember(viewModel.activeTemplate) {
        val template = viewModel.activeTemplate
        val campus = template?.campus?.trim().orEmpty()
        val building = template?.building?.trim().orEmpty()
        when {
            campus.isNotBlank() && building.isNotBlank() -> "$campus 路 $building"
            campus.isNotBlank() -> campus
            building.isNotBlank() -> building
            else -> "CatScan"
        }
    }
    val isConnected = viewModel.uploadEnabled && viewModel.serverUrl.isNotBlank()

    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            SettingsDrawer(
                viewModel = viewModel,
                serverUrl = viewModel.serverUrl,
                onServerUrlChange = { viewModel.updateServerUrl(it) },
                uploadEnabled = viewModel.uploadEnabled,
                onUploadEnabledChange = { viewModel.setUploadEnabledByUser(it) },
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
        Box(modifier = Modifier.fillMaxSize()) {
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
                            IconButton(onClick = { coroutineScope.launch { drawerState.open() } }) {
                                Icon(
                                    imageVector = Icons.Filled.Menu,
                                    contentDescription = "鑿滃崟"
                                )
                            }
                        },
                        actions = {
                            Icon(
                                imageVector = if (isConnected) Icons.Filled.CloudDone else Icons.Filled.CloudOff,
                                contentDescription = "杩炴帴鐘舵€?,
                                tint = if (isConnected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
                                modifier = Modifier
                                    .padding(end = 4.dp)
                                    .size(20.dp)
                            )
                            IconButton(onClick = { viewModel.showTemplateEditor = true }) {
                                Icon(
                                    imageVector = Icons.Filled.Description,
                                    contentDescription = "妯℃澘绠＄悊"
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
                                onCameraReady = { camera -> viewModel.camera = camera },
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
                                .padding(end = 14.dp, bottom = 14.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            AlbumButton(onClick = { imagePickerLauncher.launch("image/*") })
                            FlashlightButton(
                                isOn = viewModel.isFlashOn,
                                onToggle = { viewModel.onToggleFlash() }
                            )
                        }
                    }

                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1.15f)
                            .background(MaterialTheme.colorScheme.surface)
                    ) {
                        val templates = viewModel.templates
                        val hasTemplate = viewModel.activeTemplate != null
                        val activeTemplateName = viewModel.activeTemplate?.name?.ifBlank { "鏈懡鍚嶆ā鏉? } ?: "鏃犳ā鏉?
                        var templateMenuExpanded by remember { mutableStateOf(false) }
                        var floorMenuExpanded by remember { mutableStateOf(false) }
                        val countText = displayItems.size.coerceIn(0, 999).toString().padStart(3, '0')

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 10.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Box(modifier = Modifier.weight(1f)) {
                                AssistChip(
                                    onClick = { templateMenuExpanded = true },
                                    enabled = true,
                                    modifier = Modifier.fillMaxWidth(),
                                    label = {
                                        Text(
                                            text = activeTemplateName,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                            style = MaterialTheme.typography.bodySmall
                                        )
                                    },
                                    trailingIcon = {
                                        Icon(
                                            imageVector = Icons.Filled.ArrowDropDown,
                                            contentDescription = "閫夋嫨妯℃澘",
                                            modifier = Modifier.size(16.dp)
                                        )
                                    }
                                )

                                DropdownMenu(
                                    expanded = templateMenuExpanded,
                                    onDismissRequest = { templateMenuExpanded = false },
                                    modifier = Modifier.width(220.dp)
                                ) {
                                    DropdownMenuItem(
                                        text = { Text("鏃犳ā鏉?) },
                                        onClick = {
                                            viewModel.clearActiveTemplate()
                                            templateMenuExpanded = false
                                        }
                                    )
                                    HorizontalDivider()
                                    templates.forEach { template ->
                                        DropdownMenuItem(
                                            text = {
                                                Text(
                                                    text = template.name.ifBlank { "鏈懡鍚嶆ā鏉? },
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis
                                                )
                                            },
                                            onClick = {
                                                viewModel.setActiveTemplate(template.id)
                                                templateMenuExpanded = false
                                            }
                                        )
                                    }
                                }
                            }

                            Box(modifier = Modifier.width(82.dp)) {
                                AssistChip(
                                    onClick = { if (hasTemplate) floorMenuExpanded = true },
                                    enabled = hasTemplate,
                                    modifier = Modifier.fillMaxWidth(),
                                    label = {
                                        Text(
                                            text = if (hasTemplate) "${selectedFloor}灞? else "妤煎眰",
                                            style = MaterialTheme.typography.bodySmall
                                        )
                                    },
                                    trailingIcon = if (hasTemplate) {
                                        {
                                            Icon(
                                                imageVector = Icons.Filled.ArrowDropDown,
                                                contentDescription = "閫夋嫨妤煎眰",
                                                modifier = Modifier.size(16.dp)
                                            )
                                        }
                                    } else {
                                        null
                                    }
                                )

                                if (hasTemplate) {
                                    DropdownMenu(
                                        expanded = floorMenuExpanded,
                                        onDismissRequest = { floorMenuExpanded = false }
                                    ) {
                                        val maxFloor = viewModel.activeTemplate?.maxFloor?.coerceAtLeast(1) ?: 1
                                        for (floor in 1..maxFloor) {
                                            DropdownMenuItem(
                                                text = { Text("${floor}灞?) },
                                                onClick = {
                                                    viewModel.selectScanFloor(floor)
                                                    floorMenuExpanded = false
                                                }
                                            )
                                        }
                                    }
                                }
                            }

                            Surface(
                                modifier = Modifier
                                    .size(height = 32.dp, width = 56.dp),
                                shape = RoundedCornerShape(10.dp),
                                color = MaterialTheme.colorScheme.primaryContainer
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 4.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.Center
                                ) {
                                    countText.forEachIndexed { index, digit ->
                                        Box(
                                            modifier = Modifier.width(14.dp),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Text(
                                                text = digit.toString(),
                                                style = MaterialTheme.typography.labelLarge,
                                                color = MaterialTheme.colorScheme.onPrimaryContainer
                                            )
                                        }
                                        if (index < countText.lastIndex) {
                                            Box(
                                                modifier = Modifier
                                                    .size(width = 1.dp, height = 16.dp)
                                                    .background(
                                                        MaterialTheme.colorScheme.outline.copy(alpha = 0.45f)
                                                    )
                                            )
                                        }
                                    }
                                }
                            }

                            AssistChip(
                                onClick = {
                                    if (hasTemplate) {
                                        viewModel.activeTemplateId?.let {
                                            viewModel.clearTemplateScans(it)
                                            showToast("宸叉竻绌哄綋鍓嶆ā鏉胯褰?)
                                        }
                                    } else {
                                        viewModel.clearAllScans(showToast)
                                    }
                                },
                                label = {
                                    Text(
                                        text = "娓呯┖",
                                        style = MaterialTheme.typography.bodySmall
                                    )
                                },
                                colors = AssistChipDefaults.assistChipColors(
                                    containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.62f),
                                    labelColor = MaterialTheme.colorScheme.error
                                )
                            )
                        }

                        if (displayItems.isEmpty()) {
                            Box(
                                modifier = Modifier.fillMaxSize(),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = "鏆傛棤鎵爜璁板綍",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        } else {
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
                                    val controller = remember(item.id) {
                                        ResultItemController(
                                            initialItem = item,
                                            onDelete = { viewModel.deleteItemById(item.id) },
                                            onClickCopy = {
                                                copyToClipboard(item.scanData.text)
                                                val toastText = normalizeQrToastText(item.scanData.text)
                                                val copyLabel = "\u5df2\u590d\u5236"
                                                showToast(if (toastText.isBlank()) copyLabel else "$copyLabel: $toastText")
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
                                        color = MaterialTheme.colorScheme.outlineVariant
                                    )
                                }
                            }
                        }
                    }
                }
            }

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
                viewModel.updateServerUrl(viewModel.pendingNewUrl)
                viewModel.setUploadEnabledByUser(true)
                viewModel.showUrlChangeDialog = false
                showToast("宸叉洿鏂颁笂浼犲湴鍧€")
            },
            onDismiss = { viewModel.showUrlChangeDialog = false }
        )
    }

    viewModel.discoveredPcToNotify?.let { server ->
        DiscoveredPcDialog(
            server = server,
            onUse = {
                viewModel.onUseDiscoveredPc(server)
                showToast("宸插垏鎹㈠埌: ${server.url}")
            },
            onDismiss = { viewModel.dismissDiscoveredPcDialog(ignoredServer = server) }
        )
    }
}

@Composable
private fun FlashlightButton(
    isOn: Boolean,
    onToggle: () -> Unit,
    modifier: Modifier = Modifier
) {
    FilledIconButton(
        onClick = onToggle,
        modifier = modifier.size(46.dp),
        shape = RoundedCornerShape(14.dp),
        colors = IconButtonDefaults.filledIconButtonColors(
            containerColor = if (isOn) {
                MaterialTheme.colorScheme.errorContainer
            } else {
                MaterialTheme.colorScheme.surfaceVariant
            },
            contentColor = if (isOn) {
                MaterialTheme.colorScheme.onErrorContainer
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant
            }
        )
    ) {
        Icon(
            imageVector = if (isOn) Icons.Filled.FlashlightOn else Icons.Filled.FlashlightOff,
            contentDescription = "鎵嬬數绛?,
            modifier = Modifier.size(20.dp)
        )
    }
}

@Composable
private fun AlbumButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    FilledIconButton(
        onClick = onClick,
        modifier = modifier.size(46.dp),
        shape = RoundedCornerShape(14.dp),
        colors = IconButtonDefaults.filledIconButtonColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant,
            contentColor = MaterialTheme.colorScheme.onSurfaceVariant
        )
    ) {
        Icon(
            imageVector = Icons.Filled.PhotoLibrary,
            contentDescription = "鐩稿唽",
            modifier = Modifier.size(20.dp)
        )
    }
}
