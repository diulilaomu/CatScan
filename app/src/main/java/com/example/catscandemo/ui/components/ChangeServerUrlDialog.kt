package com.example.catscandemo.ui.components


import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable

@Composable
fun ChangeServerUrlDialog(newUrl: String, onConfirm: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(onDismissRequest = onDismiss, title = { Text("检测到电脑端连接") }, text = { Text("是否将上传地址修改为:\n$newUrl") }, confirmButton = { TextButton(onClick = onConfirm) { Text("确认") } }, dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } })
}


