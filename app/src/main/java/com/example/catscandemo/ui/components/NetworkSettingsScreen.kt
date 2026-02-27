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
import com.example.catscandemo.presentation.viewmodel.MainViewModel

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
        // 椤堕儴鏍囬鏍?        Surface(
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
                    text = "缃戠粶璁剧疆",
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
        }
        
        HorizontalDivider(
            color = MaterialTheme.colorScheme.outlineVariant
        )
        
        // 鍐呭鍖哄煙
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            // 缃戠粶鍙戠幇鍖哄煙
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surface
                ),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
            ) {
                Column(
                    modifier = Modifier.padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "缃戠粶鍙戠幇",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        IconButton(
                            onClick = {
                                viewModel.startNetworkDiscovery(context) {
                                    if (viewModel.discoveredServers.isEmpty()) {
                                        showToast("鏈彂鐜版湇鍔″櫒")
                                    } else {
                                        showToast("鍙戠幇 ${viewModel.discoveredServers.size} 涓湇鍔″櫒")
                                    }
                                }
                            },
                            enabled = !viewModel.isDiscovering,
                            colors = IconButtonDefaults.iconButtonColors(
                                contentColor = MaterialTheme.colorScheme.primary
                            )
                        ) {
                            Icon(
                                imageVector = Icons.Default.Refresh,
                                contentDescription = "鍙戠幇鏈嶅姟鍣?,
                                modifier = Modifier.size(24.dp)
                            )
                        }
                    }
                    
                    if (viewModel.isDiscovering) {
                        LinearProgressIndicator(
                            modifier = Modifier.fillMaxWidth(),
                            color = MaterialTheme.colorScheme.primary
                        )
                        Text(
                            text = "姝ｅ湪鎼滅储鏈嶅姟鍣?..",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    
                    if (viewModel.discoveredServers.isNotEmpty()) {
                        Text(
                            text = "鍙戠幇鐨勬湇鍔″櫒锛?,
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
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
                                            showToast("宸查€夋嫨: ${server.url}")
                                        },
                                    colors = CardDefaults.cardColors(
                                        containerColor = if (serverUrl == server.url) {
                                            MaterialTheme.colorScheme.primaryContainer
                                        } else {
                                            MaterialTheme.colorScheme.surfaceVariant
                                        }
                                    ),
                                    elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
                                ) {
                                    Column(
                                        modifier = Modifier.padding(16.dp)
                                    ) {
                                        Text(
                                            text = server.name,
                                            style = MaterialTheme.typography.labelLarge,
                                            color = if (serverUrl == server.url) {
                                                MaterialTheme.colorScheme.onPrimaryContainer
                                            } else {
                                                MaterialTheme.colorScheme.onSurfaceVariant
                                            }
                                        )
                                        Spacer(Modifier.height(4.dp))
                                        Text(
                                            text = server.url,
                                            style = MaterialTheme.typography.bodySmall,
                                            color = if (serverUrl == server.url) {
                                                MaterialTheme.colorScheme.onPrimaryContainer
                                            } else {
                                                MaterialTheme.colorScheme.onSurfaceVariant
                                            }
                                        )
                                    }
                                }
                            }
                        }
                    } else if (!viewModel.isDiscovering) {
                        Text(
                            text = "鐐瑰嚮鍒锋柊鎸夐挳鎼滅储鍚屼竴缃戠粶涓嬬殑Windows瀹㈡埛绔?,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
            
            // 鏈嶅姟鍣ㄩ厤缃尯鍩?            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surface
                ),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
            ) {
                Column(
                    modifier = Modifier.padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(20.dp)
                ) {
                    Text(
                        text = "鏈嶅姟鍣ㄩ厤缃?,
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    
                    OutlinedTextField(
                        value = serverUrl,
                        onValueChange = onServerUrlChange,
                        label = { 
                            Text(
                                "鐢佃剳绔湴鍧€",
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            ) 
                        },
                        placeholder = { 
                            Text(
                                "http://192.168.1.100:29027/postqrdata",
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            ) 
                        },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = MaterialTheme.colorScheme.primary,
                            unfocusedBorderColor = MaterialTheme.colorScheme.outline,
                            cursorColor = MaterialTheme.colorScheme.primary,
                            focusedLabelColor = MaterialTheme.colorScheme.primary
                        )
                    )
                    
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "鍚敤涓婁紶鍒扮數鑴?,
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = "鎵弿缁撴灉灏嗚嚜鍔ㄤ笂浼犲埌鏈嶅姟鍣?,
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
                                    showToast("璇峰厛璁剧疆鏈嶅姟鍣ㄥ湴鍧€")
                                }
                            },
                            colors = SwitchDefaults.colors(
                                checkedTrackColor = MaterialTheme.colorScheme.primary,
                                checkedThumbColor = MaterialTheme.colorScheme.onPrimary,
                                uncheckedTrackColor = MaterialTheme.colorScheme.surfaceVariant
                            )
                        )
                    }
                }
            }
        }
    }
}
