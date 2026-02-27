package com.example.catscandemo.ui.components

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import com.example.catscandemo.data.network.DiscoveredServer

@Composable
fun DiscoveredPcDialog(
    server: DiscoveredServer,
    onUse: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss, // 点击外部或返回键时关闭，等同忽略
        title = { Text("发现电脑端") },
        text = {
            Text("发现 Windows 客户端：\n${server.url}\n\n是否设为上传目标？")
        },
        confirmButton = {
            TextButton(onClick = onUse) {
                Text("使用")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("忽略")
            }
        }
    )
}
