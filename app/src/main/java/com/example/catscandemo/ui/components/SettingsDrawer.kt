package com.example.catscandemo.ui.components


import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
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
import com.example.catscandemo.presentation.viewmodel.MainViewModel

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
            .padding(top = 24.dp, bottom = 16.dp, start = 16.dp, end = 16.dp)
    ) {
        Text(
            text = "设置", 
            style = MaterialTheme.typography.headlineMedium,
            color = MaterialTheme.colorScheme.onSurface
        )
        
        Spacer(Modifier.height(24.dp))
        
        // 网络设置入口
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onNetworkClick),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant
            ),
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
        
        Spacer(Modifier.height(24.dp))
        
        HorizontalDivider(
            color = MaterialTheme.colorScheme.outlineVariant
        )
        
        Spacer(Modifier.height(24.dp))
        
        // 其他设置
        Text(
            text = "扫描设置",
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        
        Spacer(Modifier.height(12.dp))
        
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surface
            ),
            elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
        ) {
            Column {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "自动复制",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Switch(
                        checked = clipboardEnabled, 
                        onCheckedChange = onClipboardEnabledChange,
                        colors = SwitchDefaults.colors(
                            checkedTrackColor = MaterialTheme.colorScheme.primary,
                            checkedThumbColor = MaterialTheme.colorScheme.onPrimary
                        )
                    )
                }
                
                HorizontalDivider(
                    color = MaterialTheme.colorScheme.outlineVariant,
                    modifier = Modifier.padding(horizontal = 16.dp)
                )
                
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "重复扫描",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Switch(
                        checked = duplicateScanEnabled,
                        onCheckedChange = onDuplicateScanEnabledChange,
                        colors = SwitchDefaults.colors(
                            checkedTrackColor = MaterialTheme.colorScheme.primary,
                            checkedThumbColor = MaterialTheme.colorScheme.onPrimary
                        )
                    )
                }
            }
        }
    }
}
