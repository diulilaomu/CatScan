package com.example.catscandemo.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
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
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
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
        when (currentPage) {
            SettingsPage.MAIN -> {
                MainSettingsPage(
                    clipboardEnabled = clipboardEnabled,
                    onClipboardEnabledChange = onClipboardEnabledChange,
                    duplicateScanEnabled = duplicateScanEnabled,
                    onDuplicateScanEnabledChange = onDuplicateScanEnabledChange,
                    showBarcodeOverlay = showBarcodeOverlay,
                    onShowBarcodeOverlayChange = onShowBarcodeOverlayChange,
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
}

@Composable
private fun MainSettingsPage(
    clipboardEnabled: Boolean,
    onClipboardEnabledChange: (Boolean) -> Unit,
    duplicateScanEnabled: Boolean,
    onDuplicateScanEnabledChange: (Boolean) -> Unit,
    showBarcodeOverlay: Boolean,
    onShowBarcodeOverlayChange: (Boolean) -> Unit,
    onNetworkClick: () -> Unit,
    onScanParamsClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val uriHandler = LocalUriHandler.current
    val linkInteractionSource = remember { MutableInteractionSource() }

    Box(
        modifier = modifier
            .fillMaxSize()
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(top = 24.dp, bottom = 64.dp, start = 16.dp, end = 16.dp)
        ) {
            Text(
                text = "璁剧疆",
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
                            contentDescription = "缃戠粶璁剧疆",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(24.dp)
                        )
                        Column {
                            Text(
                                text = "缃戠粶璁剧疆",
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = "鏈嶅姟鍣ㄥ湴鍧€銆佺綉缁滃彂鐜?,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    Icon(
                        imageVector = Icons.Default.ArrowDropDown,
                        contentDescription = "杩涘叆缃戠粶璁剧疆",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }

            Spacer(Modifier.height(12.dp))

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
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Tune,
                            contentDescription = "鎵弿鍙傛暟",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(24.dp)
                        )
                        Column {
                            Text(
                                text = "鎵弿鍙傛暟",
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = "閫氶亾闃堝€间笌甯ч棿闅?,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    Icon(
                        imageVector = Icons.Default.ArrowDropDown,
                        contentDescription = "杩涘叆鎵弿鍙傛暟",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }

            Spacer(Modifier.height(12.dp))
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            Spacer(Modifier.height(12.dp))

            Text(
                text = "鎵弿璁剧疆",
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
                        title = "鑷姩澶嶅埗",
                        checked = clipboardEnabled,
                        onCheckedChange = onClipboardEnabledChange
                    )
                    HorizontalDivider(
                        color = MaterialTheme.colorScheme.outlineVariant,
                        modifier = Modifier.padding(horizontal = 16.dp)
                    )
                    ToggleSettingRow(
                        title = "閲嶅鎵弿",
                        checked = duplicateScanEnabled,
                        onCheckedChange = onDuplicateScanEnabledChange
                    )
                    HorizontalDivider(
                        color = MaterialTheme.colorScheme.outlineVariant,
                        modifier = Modifier.padding(horizontal = 16.dp)
                    )
                    ToggleSettingRow(
                        title = "瀹炴椂妫€娴?,
                        checked = showBarcodeOverlay,
                        onCheckedChange = onShowBarcodeOverlayChange
                    )
                }
            }
        }

        Surface(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth(),
            color = MaterialTheme.colorScheme.surface
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                Spacer(Modifier.height(6.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "漏 Wenhe x Rnzy 2025.11.1 ",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center
                    )
                    Text(
                        text = "CatScan",
                        style = MaterialTheme.typography.labelSmall.copy(
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        ),
                        modifier = Modifier.clickable(
                            interactionSource = linkInteractionSource,
                            indication = null
                        ) {
                            uriHandler.openUri("https://github.com/diulilaomu/CatScan")
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun ScanParamsSettingsScreen(
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
    Column(modifier = modifier.fillMaxSize()) {
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
                        Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "杩斿洖",
                        tint = MaterialTheme.colorScheme.onSurface
                    )
                }
                Text(
                    text = "鎵弿鍙傛暟",
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
        }

        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(16.dp)
        ) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
            ) {
                Column {
                    SliderSettingRow(
                        title = "閫氶亾1 甯ч棿闅?,
                        valueText = "${channel1ScanFrameInterval.coerceAtLeast(1)} 甯?,
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
                        title = "閫氶亾2 闈㈢Н鍒?,
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
                        title = "閫氶亾2 闀垮姣斿垎",
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
                        title = "閫氶亾2 瀹炲績搴﹀垎",
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
                        title = "閫氶亾2 姊害鍒?,
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
