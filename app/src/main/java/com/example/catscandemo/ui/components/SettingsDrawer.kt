package com.example.catscandemo.ui.components


import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.example.catscandemo.data.network.DiscoveredServer
import com.example.catscandemo.ui.main.MainViewModel

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
    val context = LocalContext.current
    val showToast = { msg: String ->
        android.widget.Toast.makeText(context, msg, android.widget.Toast.LENGTH_SHORT).show()
    }
    
    ModalDrawerSheet(modifier = Modifier.width(280.dp)) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(top = 16.dp, bottom = 16.dp, start = 8.dp, end = 8.dp)
        ) {
            Text(text = "设置", style = MaterialTheme.typography.headlineMedium)
            
            Spacer(Modifier.height(8.dp))
            
            // 网络发现区域
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "网络发现",
                    style = MaterialTheme.typography.titleSmall
                )
                IconButton(
                    onClick = {
                        viewModel.startNetworkDiscovery(context) {
                            if (viewModel.discoveredServers.isEmpty()) {
                                showToast("未发现服务器")
                            } else {
                                showToast("发现 ${viewModel.discoveredServers.size} 个服务器")
                            }
                        }
                    },
                    enabled = !viewModel.isDiscovering
                ) {
                    Icon(
                        imageVector = Icons.Default.Refresh,
                        contentDescription = "发现服务器",
                        modifier = if (viewModel.isDiscovering) {
                            Modifier
                        } else {
                            Modifier
                        }
                    )
                }
            }
            
            if (viewModel.isDiscovering) {
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                Spacer(Modifier.height(4.dp))
                Text(
                    text = "正在搜索服务器...",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            
            if (viewModel.discoveredServers.isNotEmpty()) {
                Spacer(Modifier.height(4.dp))
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 120.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    items(viewModel.discoveredServers) { server ->
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    viewModel.selectDiscoveredServer(server)
                                    showToast("已选择: ${server.url}")
                                },
                            colors = CardDefaults.cardColors(
                                containerColor = if (serverUrl == server.url) {
                                    MaterialTheme.colorScheme.primaryContainer
                                } else {
                                    MaterialTheme.colorScheme.surfaceVariant
                                }
                            )
                        ) {
                            Column(
                                modifier = Modifier.padding(8.dp)
                            ) {
                                Text(
                                    text = server.name,
                                    style = MaterialTheme.typography.labelMedium
                                )
                                Text(
                                    text = server.url,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }
            
            Spacer(Modifier.height(8.dp))
            
            Divider()
            
            Spacer(Modifier.height(8.dp))

            OutlinedTextField(
                value = serverUrl,
                onValueChange = onServerUrlChange,
                label = { Text("电脑端地址") },
                modifier = Modifier.fillMaxWidth()
            )

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp, bottom = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(text = "启用上传到电脑")
                Switch(checked = uploadEnabled && serverUrl.isNotEmpty(), onCheckedChange = {
                    if (serverUrl.isNotEmpty()) {
                        onUploadEnabledChange(it)
                    } else {
                        showToast("上传地址不能为空！")
                    }
                })
            }

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
}
