package com.example.catscandemo.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.catscandemo.presentation.viewmodel.MainViewModel
import kotlin.math.roundToInt

private enum class SettingsPage { MAIN, NETWORK }

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
        when (currentPage) {
            SettingsPage.MAIN -> {
                MainSettingsPage(
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
                    onNetworkClick = { currentPage = SettingsPage.NETWORK }
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
        }
    }
}

@Composable
private fun MainSettingsPage(
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
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(top = 24.dp, bottom = 16.dp, start = 16.dp, end = 16.dp)
    ) {
        Text(
            text = "设置",
            style = MaterialTheme.typography.headlineMedium,
            color = MaterialTheme.colorScheme.onSurface
        )

        Spacer(Modifier.height(12.dp))

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
                    .padding(16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
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

        Spacer(Modifier.height(12.dp))

        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

        Spacer(Modifier.height(12.dp))

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
            }
        }

        Spacer(Modifier.height(12.dp))

        Text(
            text = "扫描参数",
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
                SliderSettingRow(
                    title = "通道1 帧间隔",
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
                    title = "通道2 面积分",
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
                    title = "通道2 长宽比分",
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
                    title = "通道2 实心度分",
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
                    title = "通道2 梯度分",
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

@Composable
private fun ToggleSettingRow(
    title: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
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
            .padding(horizontal = 16.dp, vertical = 12.dp)
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
