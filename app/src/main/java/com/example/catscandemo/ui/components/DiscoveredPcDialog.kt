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
        onDismissRequest = onDismiss, // 鐐瑰嚮澶栭儴鎴栬繑鍥為敭鏃跺叧闂紝绛夊悓蹇界暐
        title = { Text("鍙戠幇鐢佃剳绔?) },
        text = {
            Text("鍙戠幇 Windows 瀹㈡埛绔細\n${server.url}\n\n鏄惁璁句负涓婁紶鐩爣锛?)
        },
        confirmButton = {
            TextButton(onClick = onUse) {
                Text("浣跨敤")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("蹇界暐")
            }
        }
    )
}
