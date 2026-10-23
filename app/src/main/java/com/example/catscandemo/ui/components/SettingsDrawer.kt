package com.example.catscandemo.ui.components

import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Surface
import androidx.compose.material3.IconButton
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.catscandemo.domain.model.CaptureKernel
import com.example.catscandemo.domain.model.WorkMode
import com.example.catscandemo.presentation.viewmodel.MainViewModel
import kotlin.math.roundToInt

private enum class SettingsPage { MAIN, NETWORK, SCAN_PARAMS }

@Composable
fun SettingsDrawer(
    viewModel: MainViewModel,
    serverUrl: String,
    onServerUrlChange: (String) -> Unit,
    uploadEnabled: Boolean,
    onUploadEnabledChange: (Boolean) -> Unit,
    clipboardEnabled: Boolean,
    onClipboardEnabledChange: (Boolean) -> Unit,
    duplicateScanEnabled: Boolean,
    onDuplicateScanEnabledChange: (Boolean) -> Unit,
    showBarcodeOverlay: Boolean,
    onShowBarcodeOverlayChange: (Boolean) -> Unit,
    channel1ScanFrameInterval: Int,
    onChannel1ScanFrameIntervalChange: (Int) -> Unit,
    channel2MinAreaScore: Double,
    onChannel2MinAreaScoreChange: (Double) -> Unit,
    channel2MinAspectScore: Double,
    onChannel2MinAspectScoreChange: (Double) -> Unit,
    channel2MinSolidityScore: Double,
    onChannel2MinSolidityScoreChange: (Double) -> Unit,
    channel2MinGradScore: Double,
    onChannel2MinGradScoreChange: (Double) -> Unit
) {
    var currentPage by remember { mutableStateOf(SettingsPage.MAIN) }

    ModalDrawerSheet(modifier = Modifier.width(300.dp)) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .weight(1f)
        ) {
            when (currentPage) {
                SettingsPage.MAIN -> {
                    MainSettingsPage(
                        viewModel = viewModel,
                        clipboardEnabled = clipboardEnabled,
                        onClipboardEnabledChange = onClipboardEnabledChange,
                        duplicateScanEnabled = duplicateScanEnabled,
                        onDuplicateScanEnabledChange = onDuplicateScanEnabledChange,
                        showBarcodeOverlay = showBarcodeOverlay,
                        onShowBarcodeOverlayChange = onShowBarcodeOverlayChange,
                        channel1ScanFrameInterval = channel1ScanFrameInterval,
                        onChannel1ScanFrameIntervalChange = onChannel1ScanFrameIntervalChange,
                        channel2MinAreaScore = channel2MinAreaScore,
                        onChannel2MinAreaScoreChange = onChannel2MinAreaScoreChange,
                        channel2MinAspectScore = channel2MinAspectScore,
                        onChannel2MinAspectScoreChange = onChannel2MinAspectScoreChange,
                        channel2MinSolidityScore = channel2MinSolidityScore,
                        onChannel2MinSolidityScoreChange = onChannel2MinSolidityScoreChange,
                        channel2MinGradScore = channel2MinGradScore,
                        onChannel2MinGradScoreChange = onChannel2MinGradScoreChange,
                        onNetworkClick = { currentPage = SettingsPage.NETWORK },
                        onScanParamsClick = { currentPage = SettingsPage.SCAN_PARAMS }
                    )
                }

                SettingsPage.NETWORK -> {
                    NetworkSettingsScreen(
                        viewModel = viewModel,
                        serverUrl = serverUrl,
                        onServerUrlChange = onServerUrlChange,
                        uploadEnabled = uploadEnabled,
                        onUploadEnabledChange = onUploadEnabledChange,
                        onBack = { currentPage = SettingsPage.MAIN }
                    )
                }

                SettingsPage.SCAN_PARAMS -> {
                    ScanParamsSettingsScreen(
                        viewModel = viewModel,
                        clipboardEnabled = clipboardEnabled,
                        duplicateScanEnabled = duplicateScanEnabled,
                        showBarcodeOverlay = showBarcodeOverlay,
                        channel1ScanFrameInterval = channel1ScanFrameInterval,
                        onChannel1ScanFrameIntervalChange = onChannel1ScanFrameIntervalChange,
                        channel2MinAreaScore = channel2MinAreaScore,
                        onChannel2MinAreaScoreChange = onChannel2MinAreaScoreChange,
                        channel2MinAspectScore = channel2MinAspectScore,
                        onChannel2MinAspectScoreChange = onChannel2MinAspectScoreChange,
                        channel2MinSolidityScore = channel2MinSolidityScore,
                        onChannel2MinSolidityScoreChange = onChannel2MinSolidityScoreChange,
                        channel2MinGradScore = channel2MinGradScore,
                        onChannel2MinGradScoreChange = onChannel2MinGradScoreChange,
                        onBack = { currentPage = SettingsPage.MAIN }
                    )
                }
            }
        }
        
        HorizontalDivider(
            color = MaterialTheme.colorScheme.outlineVariant,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
        )

        // 署名：点击跳转 GitHub 仓库；有新版本时仅此位置变为更新提示（红点+版本号），其余界面不做任何提示
        val uriHandler = LocalUriHandler.current
        val updateHintVersion = viewModel.updateHintVersion
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable {
                    uriHandler.openUri(
                        if (updateHintVersion == null) "https://github.com/diulilaomu/CatScan"
                        else "https://github.com/diulilaomu/CatScan/releases/latest"
                    )
                }
                .padding(vertical = 8.dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (updateHintVersion == null) {
                Text(
                    text = "Wenhe x Rnzhy 2025.11.1 CatScan MIT",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary,
                    maxLines = 1
                )
            } else {
                Box(
                    modifier = Modifier
                        .padding(end = 5.dp)
                        .size(8.dp)
                        .background(Color.Red, CircleShape)
                )
                Text(
                    text = "发现新版本 $updateHintVersion · 点击查看",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary,
                    maxLines = 1
                )
            }
        }
    }
}

@Composable
private fun MainSettingsPage(
    viewModel: MainViewModel,
    clipboardEnabled: Boolean,
    onClipboardEnabledChange: (Boolean) -> Unit,
    duplicateScanEnabled: Boolean,
    onDuplicateScanEnabledChange: (Boolean) -> Unit,
    showBarcodeOverlay: Boolean,
    onShowBarcodeOverlayChange: (Boolean) -> Unit,
    channel1ScanFrameInterval: Int,
    onChannel1ScanFrameIntervalChange: (Int) -> Unit,
    channel2MinAreaScore: Double,
    onChannel2MinAreaScoreChange: (Double) -> Unit,
    channel2MinAspectScore: Double,
    onChannel2MinAspectScoreChange: (Double) -> Unit,
    channel2MinSolidityScore: Double,
    onChannel2MinSolidityScoreChange: (Double) -> Unit,
    channel2MinGradScore: Double,
    onChannel2MinGradScoreChange: (Double) -> Unit,
    onNetworkClick: () -> Unit,
    onScanParamsClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    LaunchedEffect(
        clipboardEnabled,
        duplicateScanEnabled,
        showBarcodeOverlay,
        channel1ScanFrameInterval,
        channel2MinAreaScore,
        channel2MinAspectScore,
        channel2MinSolidityScore,
        channel2MinGradScore
    ) {
        viewModel.persistSettings()
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(top = 12.dp, bottom = 16.dp, start = 16.dp, end = 16.dp)
    ) {
        Text(
            text = "设置",
            style = MaterialTheme.typography.headlineSmall,
            color = MaterialTheme.colorScheme.onSurface
        )

        Spacer(Modifier.height(8.dp))

        // 采集内核切换：二维码 / 文字识别（决定上方识别区怎么采集，与录入模式正交）
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "采集内核：",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.width(4.dp))
            Row(
                modifier = Modifier.weight(1f),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                CaptureKernel.entries.forEach { kernel ->
                    FilterChip(
                        selected = viewModel.captureKernel == kernel,
                        onClick = { viewModel.onCaptureKernelChange(kernel) },
                        modifier = Modifier.weight(1f),
                        label = {
                            Text(
                                text = if (kernel == CaptureKernel.QR) "二维码" else "文字识别",
                                modifier = Modifier.fillMaxWidth(),
                                textAlign = TextAlign.Center,
                                maxLines = 1
                            )
                        }
                    )
                }
            }
        }

        Spacer(Modifier.height(8.dp))

        // 录入模式切换：模板 / 文本（决定下方录入区录到哪）
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "录入模式：",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.width(4.dp))
            Row(
                modifier = Modifier.weight(1f),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                WorkMode.entries.forEach { mode ->
                    val selected = viewModel.workMode == mode
                    FilterChip(
                        selected = selected,
                        onClick = { viewModel.onWorkModeChange(mode) },
                        modifier = Modifier.weight(1f),
                        label = {
                            Text(
                                text = if (mode == WorkMode.TEMPLATE) "模板模式" else "文本模式",
                                modifier = Modifier.fillMaxWidth(),
                                textAlign = TextAlign.Center,
                                maxLines = 1
                            )
                        }
                    )
                }
            }
        }

        Spacer(Modifier.height(8.dp))

        Card(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onNetworkClick),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp, vertical = 10.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Wifi,
                        contentDescription = "网络设置",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(24.dp)
                    )
                    Column {
                        Text(
                            text = "网络设置",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = "服务器地址、网络发现",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                Icon(
                    imageVector = Icons.Default.ArrowDropDown,
                    contentDescription = "进入网络设置",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(20.dp)
                )
            }
        }

        Spacer(Modifier.height(8.dp))

        Card(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onScanParamsClick),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp, vertical = 10.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.QrCodeScanner,
                        contentDescription = "扫描参数",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(24.dp)
                    )
                    Column {
                        Text(
                            text = "扫描参数",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = "通道 1 帧间隔、通道 2 检测阈值",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                Icon(
                    imageVector = Icons.Default.ArrowDropDown,
                    contentDescription = "进入扫描参数",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(20.dp)
                )
            }
        }

        Spacer(Modifier.height(8.dp))

        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

        Spacer(Modifier.height(8.dp))

        Text(
            text = "扫描设置",
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Spacer(Modifier.height(8.dp))

        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
        ) {
            Column {
                ToggleSettingRow(
                    title = "自动复制",
                    checked = clipboardEnabled,
                    onCheckedChange = onClipboardEnabledChange
                )
                HorizontalDivider(
                    color = MaterialTheme.colorScheme.outlineVariant,
                    modifier = Modifier.padding(horizontal = 16.dp)
                )
                ToggleSettingRow(
                    title = "重复扫描",
                    checked = duplicateScanEnabled,
                    onCheckedChange = onDuplicateScanEnabledChange
                )
                HorizontalDivider(
                    color = MaterialTheme.colorScheme.outlineVariant,
                    modifier = Modifier.padding(horizontal = 16.dp)
                )
                ToggleSettingRow(
                    title = "实时检测",
                    checked = showBarcodeOverlay,
                    onCheckedChange = onShowBarcodeOverlayChange
                )
                // 镜头休眠（仅文本模式显示并生效）：一分钟没扫到内容自动关闭摄像头
                if (viewModel.workMode == WorkMode.TEXT) {
                    HorizontalDivider(
                        color = MaterialTheme.colorScheme.outlineVariant,
                        modifier = Modifier.padding(horizontal = 16.dp)
                    )
                    ToggleSettingRow(
                        title = "镜头休眠",
                        checked = viewModel.cameraSleepEnabled,
                        onCheckedChange = { viewModel.onCameraSleepEnabledChange(it) }
                    )
                }
            }
        }

        Spacer(Modifier.height(8.dp))

        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

        Spacer(Modifier.height(8.dp))

        Text(
            text = "应用设置",
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Spacer(Modifier.height(8.dp))

        // 主题与导出位置
        val context = LocalContext.current
        val exportTreeLauncher = rememberLauncherForActivityResult(
            ActivityResultContracts.OpenDocumentTree()
        ) { uri ->
            if (uri != null) {
                val flags = Intent.FLAG_GRANT_READ_URI_PERMISSION or
                        Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                runCatching {
                    context.contentResolver.takePersistableUriPermission(uri, flags)
                }
                viewModel.onExportTreeUriChange(uri.toString())
            }
        }

        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
        ) {
            Column {
                // 主题：系统 / 日间 / 夜间
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp)
                ) {
                    Text(
                        text = "主题",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(Modifier.height(6.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        listOf("SYSTEM" to "系统", "LIGHT" to "日间", "DARK" to "夜间")
                            .forEach { (mode, label) ->
                                FilterChip(
                                    selected = viewModel.themeMode == mode,
                                    onClick = { viewModel.onThemeModeChange(mode) },
                                    modifier = Modifier.weight(1f),
                                    label = {
                                        Text(
                                            text = label,
                                            modifier = Modifier.fillMaxWidth(),
                                            textAlign = TextAlign.Center,
                                            style = MaterialTheme.typography.labelMedium,
                                            maxLines = 1,
                                            softWrap = false
                                        )
                                    }
                                )
                            }
                    }
                }
                HorizontalDivider(
                    color = MaterialTheme.colorScheme.outlineVariant,
                    modifier = Modifier.padding(horizontal = 16.dp)
                )
                // 导出文件位置
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { exportTreeLauncher.launch(null) }
                        .padding(horizontal = 16.dp, vertical = 10.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(
                            imageVector = Icons.Default.FolderOpen,
                            contentDescription = "导出文件位置",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(22.dp)
                        )
                        Column {
                            Text(
                                text = "导出文件位置",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = exportTreeDisplayPath(viewModel.exportTreeUri),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    Icon(
                        imageVector = Icons.Default.ArrowDropDown,
                        contentDescription = "选择目录",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun ToggleSettingRow(
    title: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 2.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface
        )
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(
                checkedTrackColor = MaterialTheme.colorScheme.primary,
                checkedThumbColor = MaterialTheme.colorScheme.onPrimary
            )
        )
    }
}

@Composable
private fun SliderSettingRow(
    title: String,
    valueText: String,
    sliderValue: Float,
    valueRange: ClosedFloatingPointRange<Float>,
    steps: Int,
    onSliderValueChange: (Float) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = valueText,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        Slider(
            value = sliderValue.coerceIn(valueRange.start, valueRange.endInclusive),
            onValueChange = onSliderValueChange,
            valueRange = valueRange,
            steps = steps
        )
    }
}

private fun toOneDecimal(value: Double): String {
    return String.format("%.1f", value)
}

@Composable
private fun ScanParamsSettingsScreen(
    viewModel: MainViewModel,
    clipboardEnabled: Boolean,
    duplicateScanEnabled: Boolean,
    showBarcodeOverlay: Boolean,
    channel1ScanFrameInterval: Int,
    onChannel1ScanFrameIntervalChange: (Int) -> Unit,
    channel2MinAreaScore: Double,
    onChannel2MinAreaScoreChange: (Double) -> Unit,
    channel2MinAspectScore: Double,
    onChannel2MinAspectScoreChange: (Double) -> Unit,
    channel2MinSolidityScore: Double,
    onChannel2MinSolidityScoreChange: (Double) -> Unit,
    channel2MinGradScore: Double,
    onChannel2MinGradScoreChange: (Double) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    LaunchedEffect(
        clipboardEnabled,
        duplicateScanEnabled,
        showBarcodeOverlay,
        channel1ScanFrameInterval,
        channel2MinAreaScore,
        channel2MinAspectScore,
        channel2MinSolidityScore,
        channel2MinGradScore
    ) {
        viewModel.persistSettings()
    }

    Column(modifier = modifier.fillMaxSize()) {
        // 顶部标题栏 - 对齐网络设置样式
        Surface(
            modifier = Modifier.fillMaxWidth(),
            color = MaterialTheme.colorScheme.surface
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onBack) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "返回",
                        tint = MaterialTheme.colorScheme.onSurface
                    )
                }
                Text(
                    text = "扫描参数",
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
        }
        
        HorizontalDivider(
            color = MaterialTheme.colorScheme.outlineVariant
        )
        
        // 内容区域
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
            ) {
                Column {
                    SliderSettingRow(
                        title = "通道 1 帧间隔",
                        valueText = "${channel1ScanFrameInterval.coerceAtLeast(1)} 帧",
                        sliderValue = channel1ScanFrameInterval.coerceAtLeast(1).toFloat(),
                        valueRange = 1f..10f,
                        steps = 8,
                        onSliderValueChange = { value ->
                            onChannel1ScanFrameIntervalChange(value.roundToInt().coerceIn(1, 10))
                        }
                    )
                    HorizontalDivider(
                        color = MaterialTheme.colorScheme.outlineVariant,
                        modifier = Modifier.padding(horizontal = 16.dp)
                    )
                    SliderSettingRow(
                        title = "通道 2 面积分",
                        valueText = toOneDecimal(channel2MinAreaScore),
                        sliderValue = channel2MinAreaScore.coerceIn(0.0, 100.0).toFloat(),
                        valueRange = 0f..100f,
                        steps = 199,
                        onSliderValueChange = { value ->
                            onChannel2MinAreaScoreChange(((value * 2f).roundToInt() / 2.0).coerceIn(0.0, 100.0))
                        }
                    )
                    HorizontalDivider(
                        color = MaterialTheme.colorScheme.outlineVariant,
                        modifier = Modifier.padding(horizontal = 16.dp)
                    )
                    SliderSettingRow(
                        title = "通道 2 长宽比分",
                        valueText = toOneDecimal(channel2MinAspectScore),
                        sliderValue = channel2MinAspectScore.coerceIn(0.0, 100.0).toFloat(),
                        valueRange = 0f..100f,
                        steps = 199,
                        onSliderValueChange = { value ->
                            onChannel2MinAspectScoreChange(((value * 2f).roundToInt() / 2.0).coerceIn(0.0, 100.0))
                        }
                    )
                    HorizontalDivider(
                        color = MaterialTheme.colorScheme.outlineVariant,
                        modifier = Modifier.padding(horizontal = 16.dp)
                    )
                    SliderSettingRow(
                        title = "通道 2 实心度分",
                        valueText = toOneDecimal(channel2MinSolidityScore),
                        sliderValue = channel2MinSolidityScore.coerceIn(0.0, 100.0).toFloat(),
                        valueRange = 0f..100f,
                        steps = 199,
                        onSliderValueChange = { value ->
                            onChannel2MinSolidityScoreChange(((value * 2f).roundToInt() / 2.0).coerceIn(0.0, 100.0))
                        }
                    )
                    HorizontalDivider(
                        color = MaterialTheme.colorScheme.outlineVariant,
                        modifier = Modifier.padding(horizontal = 16.dp)
                    )
                    SliderSettingRow(
                        title = "通道 2 梯度分",
                        valueText = toOneDecimal(channel2MinGradScore),
                        sliderValue = channel2MinGradScore.coerceIn(0.0, 100.0).toFloat(),
                        valueRange = 0f..100f,
                        steps = 199,
                        onSliderValueChange = { value ->
                            onChannel2MinGradScoreChange(((value * 2f).roundToInt() / 2.0).coerceIn(0.0, 100.0))
                        }
                    )
                }
            }
        }
    }
}
