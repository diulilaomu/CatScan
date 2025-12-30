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
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material3.HorizontalDivider
import androidx.compose.ui.Alignment
import com.example.catscandemo.ui.components.CameraPreview
import com.example.catscandemo.ui.components.ResultItemController
import com.example.catscandemo.ui.components.SettingsDrawer
import com.example.catscandemo.ui.components.ChangeServerUrlDialog
import com.example.catscandemo.utils.AutoRequestCameraPermission
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts

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
    val imagePickerLauncher =
        rememberLauncherForActivityResult(
            contract = ActivityResultContracts.GetContent()
        ) { uri: Uri? ->
            uri?.let {
                viewModel.onImagePicked(
                    uri = it,
                    context = context,
                    copyToClipboard = copyToClipboard,
                    showToast = showToast
                )
            }
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
                        .weight(3f)
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
                        .weight(2.1f)
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
                    Column (
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .padding(bottom = 16.dp, end = 16.dp)
                    ) {

                        AlbumButton (
                            onClick = {
                                imagePickerLauncher.launch("image/*")
                            }
                        )

                        FlashlightButton(
                            isOn = viewModel.isFlashOn,
                            onToggle = { viewModel.onToggleFlash() },
                            modifier = Modifier
                        )
                    }

                }

                // 识别结果列表
                Column(
                    modifier = Modifier
                        .weight(4.2f)
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
                            .padding(horizontal = 4.dp)
                    ) {
                        itemsIndexed(viewModel.historyManager.items,key = { _, item -> item.index }) { index, item ->

                            val isDuplicate = viewModel.historyManager.items.count { it.text == item.text } > 1

                            val controller = remember {
                                ResultItemController(
                                    initialItem = item,
                                    onDelete = { viewModel.deleteItem(index) },
                                    onClickCopy = {
                                        copyToClipboard(item.text)
                                        showToast(item.text) },
                                    onUpdate = { updatedItem ->
                                        viewModel.updateItem(index, updatedItem)
                                    }
                                )
                            }
                            LaunchedEffect(item) {
                                controller.syncItem(item)
                            }
                            controller.Render(highlight = isDuplicate)

                            HorizontalDivider(
                                thickness = DividerDefaults.Thickness,
                                color = Color.LightGray
                            )
                        }
                    }
                }
<<<<<<< HEAD
=======
                // 底部操作
                Box(
                    modifier = Modifier
                        .weight(0.8f)
                        .fillMaxWidth()
                        .background(Color(0xFF488ECC)),
                    contentAlignment = Alignment.Center
                ) {

                }

>>>>>>> f7acbd8 (增加相册识别)
            }

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
            .shadow(elevation, CircleShape)
            .size(50.dp)
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
@Composable
fun AlbumButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .padding(top = 4.dp, bottom = 4.dp)
            .shadow(8.dp, CircleShape)
            .size(50.dp)
            .background(Color(0xFF9E9E9E), CircleShape)
            .clickable { onClick() },
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = Icons.Default.PhotoLibrary,
            contentDescription = "相册",
            tint = Color.White
        )
    }
}