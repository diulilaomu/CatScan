package com.example.catscandemo.ui.components


import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.catscandemo.ui.main.MainViewModel

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
    onDuplicateScanEnabledChange: (Boolean) -> Unit
)
 {
    var currentPage by remember { mutableStateOf(SettingsPage.MAIN) }
    
    ModalDrawerSheet(modifier = Modifier.width(280.dp)) {
        when (currentPage) {
            SettingsPage.MAIN -> {
                MainSettingsPage(
                    clipboardEnabled = clipboardEnabled,
                    onClipboardEnabledChange = onClipboardEnabledChange,
                    duplicateScanEnabled = duplicateScanEnabled,
                    onDuplicateScanEnabledChange = onDuplicateScanEnabledChange,
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
    onNetworkClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(top = 16.dp, bottom = 16.dp, start = 8.dp, end = 8.dp)
    ) {
        Text(text = "设置", style = MaterialTheme.typography.headlineMedium)
        
        Spacer(Modifier.height(16.dp))
        
        // 网络设置入口
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onNetworkClick)
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
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Wifi,
                        contentDescription = "网络设置"
                    )
                    Column {
                        Text(
                            text = "网络设置",
                            style = MaterialTheme.typography.titleMedium
                        )
                        Text(
                            text = "服务器地址、网络发现",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                Icon(
                    imageVector = Icons.Default.Settings,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        
        Spacer(Modifier.height(16.dp))
        
        HorizontalDivider()
        
        Spacer(Modifier.height(16.dp))
        
        // 其他设置
        Text(
            text = "扫描设置",
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        
        Spacer(Modifier.height(8.dp))
        
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 8.dp, bottom = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(text = "自动复制到剪贴板")
            Switch(checked = clipboardEnabled, onCheckedChange = onClipboardEnabledChange)
        }
        
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 8.dp, bottom = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(text = "扫描重复项")
            Switch(
                checked = duplicateScanEnabled,
                onCheckedChange = onDuplicateScanEnabledChange
            )
        }
    }
}
