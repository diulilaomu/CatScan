package com.example.catscandemo.ui.components


import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable

@Composable
fun ChangeServerUrlDialog(newUrl: String, onConfirm: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(onDismissRequest = onDismiss, title = { Text("妫€娴嬪埌鐢佃剳绔繛鎺?) }, text = { Text("鏄惁灏嗕笂浼犲湴鍧€淇敼涓?\n$newUrl") }, confirmButton = { TextButton(onClick = onConfirm) { Text("纭") } }, dismissButton = { TextButton(onClick = onDismiss) { Text("鍙栨秷") } })
}


