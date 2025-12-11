package com.example.catscandemo.ui.main

import android.annotation.SuppressLint
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FlashlightOff
import androidx.compose.material.icons.filled.FlashlightOn
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.size
import androidx.compose.ui.draw.shadow
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.Alignment
import androidx.compose.ui.platform.LocalConfiguration
import com.example.catscandemo.ui.components.CameraPreview
import com.example.catscandemo.ui.components.ResultItem
import com.example.catscandemo.ui.components.SettingsDrawer
import com.example.catscandemo.ui.components.ChangeServerUrlDialog
import com.example.catscandemo.utils.AutoRequestCameraPermission

@SuppressLint("ConfigurationScreenWidthHeight")
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(viewModel: MainViewModel) {

    val context = LocalContext.current
    val clipboardManager = LocalClipboardManager.current
    val coroutineScope = rememberCoroutineScope()

    val drawerState = rememberDrawerState(DrawerValue.Closed)

    val copyToClipboard = { text: String ->
        clipboardManager.setText(AnnotatedString(text))
    }
    val showToast = { msg: String ->
        android.widget.Toast.makeText(context, msg, android.widget.Toast.LENGTH_SHORT).show()
    }
    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            SettingsDrawer(
                serverUrl = viewModel.serverUrl,
                onServerUrlChange = { viewModel.serverUrl = it },
                uploadEnabled = viewModel.uploadEnabled,
                onUploadEnabledChange = { viewModel.uploadEnabled = it },
                clipboardEnabled = viewModel.clipboardEnabled,
                onClipboardEnabledChange = { viewModel.clipboardEnabled = it }
            )
        }
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            Column(modifier = Modifier.fillMaxSize()) {
                // 顶部栏
                TopAppBar(
                    modifier = Modifier
                        .weight(2.1f)
                        .padding(2.dp),
                    title = { Text("") },
                    navigationIcon = {
                        IconButton(onClick = { coroutineScope.launch { drawerState.open() } }) {
                            Icon(Icons.Default.Menu, contentDescription = "菜单")
                        }
                    }
                )

                // 摄像头区域
                Box(
                    modifier = Modifier
                        .weight(4f)
                        .fillMaxWidth()
                        .background(Color(0xFF488ECC)),
                    contentAlignment = Alignment.Center
                ) {
                    AutoRequestCameraPermission {
                        CameraPreview(
                            onBarcodeDetected = { code ->
                                viewModel.onBarcodeScanned(code, copyToClipboard, showToast)
                            },
                            onCameraReady = { cam -> viewModel.camera = cam }
                        )
                    }
                }

                // 识别结果列表
                Column(
                    modifier = Modifier
                        .weight(3.9f)
                        .fillMaxWidth()
                        .background(Color(0xFFF5F5F5))
                ) {

                    Text(
                        text = "识别结果",
                        modifier = Modifier.padding(16.dp)
                    )
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(start = 4.dp, end = 4.dp)
                    ) {
                        itemsIndexed(viewModel.todoList) { index, item ->
                            ResultItem(
                                text = item,
                                onDelete = { viewModel.deleteItem(index) },
                                onClickCopy = {
                                    copyToClipboard(item)
                                    showToast("已复制: $item")
                                }
                            )
                            HorizontalDivider(
                                Modifier,
                                DividerDefaults.Thickness,
                                color = Color.LightGray
                            )
                        }
                    }
                }

            }
            // 闪光灯按钮
            FlashlightButton(
                isOn = viewModel.isFlashOn,
                onToggle = {
                    viewModel.onToggleFlash()
                },
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .offset(y = LocalConfiguration.current.screenHeightDp.dp * 0.56f + 12.dp)

            )
        }
    }

    // 自动提示修改上传地址的弹窗
    if (viewModel.showUrlChangeDialog) {
        ChangeServerUrlDialog(
            newUrl = viewModel.pendingNewUrl,
            onConfirm = {
                viewModel.serverUrl = viewModel.pendingNewUrl
                viewModel.uploadEnabled = true
                viewModel.showUrlChangeDialog = false
                showToast("已更新上传地址")
            },
            onDismiss = { viewModel.showUrlChangeDialog = false }
        )
    }
}


@Composable
fun FlashlightButton(isOn: Boolean, onToggle: () -> Unit, modifier: Modifier) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed = interactionSource.collectIsPressedAsState()
    val elevation by animateDpAsState(
        targetValue = if (isPressed.value) 0.dp else 8.dp,
        animationSpec = tween(150)
    )

    Box(
        modifier = modifier
            .padding(end = 24.dp)
            .shadow(elevation, CircleShape)
            .size(56.dp)
            .background(if (isOn) Color(0xFFFFC107) else Color(0xFF9E9E9E), CircleShape)
            .clickable(
                interactionSource = interactionSource,
                indication = null
            ) {
                onToggle()
            },
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = if (isOn) Icons.Filled.FlashlightOn else Icons.Filled.FlashlightOff,
            contentDescription = "手电筒",
            tint = Color.White
        )
    }
}
