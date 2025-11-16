package com.example.catscandemo.utils

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.unit.*
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat


//权限申请
@Composable
fun AutoRequestCameraPermission(
    onGranted: @Composable () -> Unit,
) {
    val context = LocalContext.current

    // 是否已授权
    var hasPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(
                context, Manifest.permission.CAMERA
            ) == PackageManager.PERMISSION_GRANTED
        )
    }
    // 权限请求器
    val launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
        onResult = { granted ->
            hasPermission = granted
        }
    )
    LaunchedEffect(Unit) {
        if (!hasPermission) {
            launcher.launch(Manifest.permission.CAMERA)
        }
    }
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        if (hasPermission) {
            onGranted() // 已授权 → 打开相机界面
        } else {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text("请授予相机权限以继续使用应用")
                Spacer(Modifier.height(8.dp))
                Button(onClick = {
                    launcher.launch(Manifest.permission.CAMERA)
                }) {
                    Text("重新申请权限")
                }
            }
        }
    }
}