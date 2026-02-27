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


//鏉冮檺鐢宠
@Composable
fun AutoRequestCameraPermission(
    onGranted: @Composable () -> Unit,
) {
    val context = LocalContext.current

    // 鏄惁宸叉巿鏉?
    var hasPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(
                context, Manifest.permission.CAMERA
            ) == PackageManager.PERMISSION_GRANTED
        )
    }
    // 鏉冮檺璇锋眰鍣?
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
            onGranted() // 宸叉巿鏉?鈫?鎵撳紑鐩告満鐣岄潰
        } else {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text("璇锋巿浜堢浉鏈烘潈闄愪互缁х画浣跨敤搴旂敤")
                Spacer(Modifier.height(8.dp))
                Button(onClick = {
                    launcher.launch(Manifest.permission.CAMERA)
                }) {
                    Text("閲嶆柊鐢宠鏉冮檺")
                }
            }
        }
    }
}