package com.example.catscandemo.ui.components


import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
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
        Column(modifier = Modifier.fillMaxSize().padding(top = 16.dp, bottom = 16.dp, start = 8.dp, end = 8.dp)) {
            Text(text = "设置", style = MaterialTheme.typography.headlineMedium)

            OutlinedTextField(value = serverUrl, onValueChange = onServerUrlChange, label = { Text("电脑端地址") }, modifier = Modifier.fillMaxWidth())

            Row(modifier = Modifier.fillMaxWidth().padding(top = 8.dp, bottom = 8.dp)
                , horizontalArrangement = Arrangement.SpaceBetween
                , verticalAlignment = Alignment.CenterVertically) {
                Text(text = "启用上传到电脑")
                Switch(checked = uploadEnabled && serverUrl.isNotEmpty(), onCheckedChange = {
                    if (serverUrl.isNotEmpty()) onUploadEnabledChange(it)
                })
            }

            Row(modifier = Modifier.fillMaxWidth().padding(top = 8.dp, bottom = 8.dp)
                , horizontalArrangement = Arrangement.SpaceBetween
                , verticalAlignment = Alignment.CenterVertically) {
                Text(text = "自动复制到剪贴板")
                Switch(checked = clipboardEnabled, onCheckedChange = onClipboardEnabledChange)
            }
        }
    }
}
