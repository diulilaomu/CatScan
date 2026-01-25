package com.example.catscandemo.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
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
fun NetworkSettingsScreen(
    viewModel: MainViewModel,
    serverUrl: String,
    onServerUrlChange: (String) -> Unit,
    uploadEnabled: Boolean,
    onUploadEnabledChange: (Boolean) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val showToast = { msg: String ->
        android.widget.Toast.makeText(context, msg, android.widget.Toast.LENGTH_SHORT).show()
    }
    
    Column(modifier = modifier.fillMaxSize()) {
        // 顶部标题栏
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
            }
            Text(
                text = "网络设置",
                style = MaterialTheme.typography.titleLarge
            )
        }
        
        HorizontalDivider()
        
        // 内容区域
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // 网络发现区域
            Card(
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "网络发现",
                            style = MaterialTheme.typography.titleMedium
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
                                contentDescription = "发现服务器"
                            )
                        }
                    }
                    
                    if (viewModel.isDiscovering) {
                        LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                        Text(
                            text = "正在搜索服务器...",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    
                    if (viewModel.discoveredServers.isNotEmpty()) {
                        Text(
                            text = "发现的服务器：",
                            style = MaterialTheme.typography.labelMedium
                        )
                        LazyColumn(
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(max = 200.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
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
                                        modifier = Modifier.padding(12.dp)
                                    ) {
                                        Text(
                                            text = server.name,
                                            style = MaterialTheme.typography.labelLarge
                                        )
                                        Spacer(Modifier.height(4.dp))
                                        Text(
                                            text = server.url,
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                            }
                        }
                    } else if (!viewModel.isDiscovering) {
                        Text(
                            text = "点击刷新按钮搜索同一网络下的Windows客户端",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
            
            // 服务器配置区域
            Card(
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Text(
                        text = "服务器配置",
                        style = MaterialTheme.typography.titleMedium
                    )
                    
                    OutlinedTextField(
                        value = serverUrl,
                        onValueChange = onServerUrlChange,
                        label = { Text("电脑端地址") },
                        placeholder = { Text("http://192.168.1.100:29027/postqrdata") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )
                    
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "启用上传到电脑",
                                style = MaterialTheme.typography.bodyLarge
                            )
                            Text(
                                text = "扫描结果将自动上传到服务器",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Switch(
                            checked = uploadEnabled && serverUrl.isNotEmpty(),
                            onCheckedChange = {
                                if (serverUrl.isNotEmpty()) {
                                    onUploadEnabledChange(it)
                                } else {
                                    showToast("请先设置服务器地址")
                                }
                            }
                        )
                    }
                }
            }
        }
    }
}
