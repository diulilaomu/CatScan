package com.example.catscandemo.ui.components


import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
fun SettingsDrawer(
    serverUrl: String,
    onServerUrlChange: (String) -> Unit,
    uploadEnabled: Boolean,
    onUploadEnabledChange: (Boolean) -> Unit,
    clipboardEnabled: Boolean,
    onClipboardEnabledChange: (Boolean) -> Unit
) {
    ModalDrawerSheet(modifier = Modifier.width(280.dp)) {
        Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
            Text(text = "设置", style = MaterialTheme.typography.headlineMedium)

            OutlinedTextField(value = serverUrl, onValueChange = onServerUrlChange, label = { Text("电脑端地址") }, modifier = Modifier.fillMaxWidth())

            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(text = "启用上传到电脑")
                Switch(checked = uploadEnabled && serverUrl.isNotEmpty(), onCheckedChange = {
                    if (serverUrl.isNotEmpty()) onUploadEnabledChange(it)
                })
            }

            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(text = "自动复制到剪贴板")
                Switch(checked = clipboardEnabled, onCheckedChange = onClipboardEnabledChange)
            }
        }
    }
}
