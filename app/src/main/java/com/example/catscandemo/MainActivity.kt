package com.example.catscandemo

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.util.Log
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.Camera
import androidx.camera.core.CameraSelector
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import com.google.mlkit.vision.barcode.BarcodeScanner
import com.google.mlkit.vision.barcode.BarcodeScannerOptions
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.common.InputImage
import androidx.compose.foundation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.FlashlightOn
import androidx.compose.material.icons.filled.FlashlightOff
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.graphics.*
import androidx.compose.ui.unit.*
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.compose.material3.ripple
import androidx.compose.ui.draw.shadow
import kotlinx.coroutines.launch
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.TextFieldValue
import kotlinx.coroutines.Dispatchers
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.io.IOException
import java.util.concurrent.TimeUnit

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            SplitScreen()
        }
    }
}

// 网络请求客户端
val okHttpClient = OkHttpClient.Builder()
    .connectTimeout(10, TimeUnit.SECONDS)
    .readTimeout(10, TimeUnit.SECONDS)
    .build()

// 上传数据到电脑端
fun uploadToComputer(url: String, qrData: String, onSuccess: () -> Unit, onFailure: (String) -> Unit) {
    val mediaType = "application/json; charset=utf-8".toMediaType()
    val jsonObject = JSONObject().apply {
        put("qrdata", qrData)
    }
    val body = jsonObject.toString().toRequestBody(mediaType)

    val request = Request.Builder()
        .url(url)
        .post(body)
        .build()

    okHttpClient.newCall(request).enqueue(object : Callback {
        override fun onFailure(call: Call, e: IOException) {
            onFailure("网络连接失败: ${e.message}")
        }

        override fun onResponse(call: Call, response: Response) {
            if (response.isSuccessful) {
                try {
                    val responseBody = response.body?.string()
                    val jsonResponse = JSONObject(responseBody)
                    val code = jsonResponse.optInt("code", -1)

                    if (code == 200) {
                        onSuccess()
                    } else {
                        onFailure("服务器返回错误: $code")
                    }
                } catch (e: Exception) {
                    onFailure("解析响应失败: ${e.message}")
                }
            } else {
                onFailure("HTTP错误: ${response.code}")
            }
        }
    })
}

//结果显示项
@Composable
fun ResultToDoItem(text: String, onDelete: () -> Unit) {
    val clipboardManager = LocalClipboardManager.current
    val context = LocalContext.current
    val interactionSource = remember { MutableInteractionSource() }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp)
            .clickable(
                interactionSource = interactionSource,
                indication = ripple()
            ) {
                clipboardManager.setText(AnnotatedString(text))
                Toast.makeText(context, "已复制: $text", Toast.LENGTH_SHORT).show()
            },
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = text,
            modifier = Modifier
                .padding(start = 4.dp)
                .weight(1f),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        IconButton(onClick = onDelete) {
            Icon(
                imageVector = Icons.Default.Delete,
                contentDescription = "删除",
                tint = Color.Red
            )
        }
    }
}

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

// 设置抽屉
@Composable
fun SettingsDrawer(
    serverUrl: String,
    onServerUrlChange: (String) -> Unit,
    uploadEnabled: Boolean,
    onUploadEnabledChange: (Boolean) -> Unit,
    clipboardEnabled: Boolean,
    onClipboardEnabledChange: (Boolean) -> Unit
) {
    ModalDrawerSheet(
        drawerContainerColor = Color.White,
        modifier = Modifier.width(280.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp)
        ) {
            Text(
                text = "设置",
                style = MaterialTheme.typography.headlineMedium,
                modifier = Modifier.padding(bottom = 24.dp)
            )

            // 服务器地址设置
            Text(
                text = "电脑端连接地址",
                style = MaterialTheme.typography.titleSmall,
                modifier = Modifier.padding(bottom = 8.dp)
            )
            OutlinedTextField(
                value = serverUrl,
                onValueChange = { newUrl ->
                    onServerUrlChange(newUrl)
                    // 当地址从空变为有值时，自动开启上传
                    if (newUrl.isNotEmpty() && !uploadEnabled) {
                        onUploadEnabledChange(true)
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 16.dp),
                placeholder = { Text("请输入电脑端地址") },
                singleLine = true
            )

            // 上传开关
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("启用上传到电脑")
                Switch(
                    checked = uploadEnabled && serverUrl.isNotEmpty(),
                    onCheckedChange = { enabled ->
                        if (serverUrl.isNotEmpty()) {
                            onUploadEnabledChange(enabled)
                        }
                    },
                    enabled = serverUrl.isNotEmpty()
                )
            }

            // 剪贴板开关
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("自动复制到剪贴板")
                Switch(
                    checked = clipboardEnabled,
                    onCheckedChange = onClipboardEnabledChange
                )
            }

            if (serverUrl.isEmpty()) {
                Text(
                    text = "请先设置连接地址才能开启上传",
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.Red,
                    modifier = Modifier.padding(top = 8.dp)
                )
            }
        }
    }
}

// 修改地址确认对话框
@Composable
fun ChangeServerUrlDialog(
    newUrl: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("检测到电脑端连接") },
        text = {
            Column {
                Text("是否将上传地址修改为：")
                Text(newUrl, fontWeight = FontWeight.Bold, modifier = Modifier.padding(vertical = 8.dp))
            }
        },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text("确认修改")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("取消")
            }
        }
    )
}

//主界面
@Composable
fun SplitScreen() {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    var isFlashOn by remember { mutableStateOf(false) }
    val coroutineScope = rememberCoroutineScope()
    var camera by remember { mutableStateOf<Camera?>(null) }
    var todoList by remember { mutableStateOf(listOf("支持条形码、二维码！")) }
    val clipboardManager = LocalClipboardManager.current

    // 新增状态变量
    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
    var serverUrl by remember { mutableStateOf("") }
    var uploadEnabled by remember { mutableStateOf(false) }
    var clipboardEnabled by remember { mutableStateOf(true) }
    var showUrlChangeDialog by remember { mutableStateOf(false) }
    var pendingNewUrl by remember { mutableStateOf("") }

    // 添加交互状态用于阴影效果
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val elevation = animateDpAsState(
        targetValue = if (isPressed) 0.dp else 8.dp,
        animationSpec = tween(durationMillis = 150)
    )

    // 处理扫码结果
    fun handleBarcodeResult(code: String) {
        // 检查是否是电脑端连接二维码
        if (code.startsWith("winClientLink:")) {
            val newUrl = code.removePrefix("winClientLink:")
            pendingNewUrl = newUrl
            showUrlChangeDialog = true
            return
        }

        // 添加到列表
        if (code !in todoList) {
            todoList = listOf(code) + todoList

            // 复制到剪贴板
            if (clipboardEnabled) {
                clipboardManager.setText(AnnotatedString(code))
                Toast.makeText(context, "已复制: $code", Toast.LENGTH_SHORT).show()
            }

            // 上传到电脑端
            if (uploadEnabled && serverUrl.isNotEmpty()) {
                coroutineScope.launch(Dispatchers.IO) {
                    uploadToComputer(
                        url = serverUrl,
                        qrData = code,
                        onSuccess = {
                            coroutineScope.launch(Dispatchers.Main) {
                                Toast.makeText(context, "上传成功", Toast.LENGTH_SHORT).show()
                            }
                        },
                        onFailure = { error ->
                            coroutineScope.launch(Dispatchers.Main) {
                                Toast.makeText(context, "上传失败: $error", Toast.LENGTH_LONG).show()
                            }
                        }
                    )
                }
            }
        }
    }

    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            SettingsDrawer(
                serverUrl = serverUrl,
                onServerUrlChange = { newUrl ->
                    serverUrl = newUrl
                    // 当地址从空变为有值时，自动开启上传
                    if (newUrl.isNotEmpty() && !uploadEnabled) {
                        uploadEnabled = true
                    }
                },
                uploadEnabled = uploadEnabled,
                onUploadEnabledChange = { uploadEnabled = it },
                clipboardEnabled = clipboardEnabled,
                onClipboardEnabledChange = { clipboardEnabled = it }
            )
        },
        gesturesEnabled = true, // 启用滑动手势
        scrimColor = Color.Black.copy(alpha = 0.5f) // 添加半透明遮罩
    ) {
        Box(Modifier.fillMaxSize()) {
            Column(Modifier.fillMaxSize()) {
                // 顶部栏
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(100.dp)
                        .background(Color(0xFF90CAF9))
                        .padding(horizontal = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // 菜单按钮
                    IconButton(
                        onClick = {
                            coroutineScope.launch {
                                drawerState.open()
                            }
                        }
                    ) {
                        Icon(
                            imageVector = Icons.Default.Menu,
                            contentDescription = "设置",
                            tint = Color.White
                        )
                    }

                    Text(
                        text = "猫头枪",
                        color = Color.White,
                        fontWeight = FontWeight.Bold
                    )

                    // 占位空间，保持标题居中
                    Box(modifier = Modifier.size(48.dp))
                }

                // 摄像头区域
                Box(
                    modifier = Modifier
                        .weight(6f)
                        .fillMaxWidth()
                        .background(Color(0xFF488ECC)),
                    contentAlignment = Alignment.Center
                ) {
                    AutoRequestCameraPermission {
                        CameraPreview(
                            lifecycleOwner,
                            onCameraReady = { cam -> camera = cam },
                            onBarcodeDetected = { code -> handleBarcodeResult(code) }
                        )
                    }
                }

                // 扫描结果列表
                Column(
                    modifier = Modifier
                        .weight(4f)
                        .fillMaxWidth()
                        .background(Color(0xFFF5F5F5))
                ) {
                    // 添加列表标题
                    Text(
                        text = "识别结果",
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 8.dp, horizontal = 20.dp),
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF333333)
                    )

                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp)
                    ) {
                        itemsIndexed(todoList) { index, item ->
                            ResultToDoItem(
                                text = item,
                                onDelete = {
                                    todoList = todoList.toMutableList().apply { removeAt(index) }
                                }
                            )
                            HorizontalDivider(Modifier, DividerDefaults.Thickness, color = Color.LightGray)
                        }
                    }
                }
            }

            // 闪光灯按钮
            Box(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .offset(y = with(LocalDensity.current) {
                        val screenHeight = LocalConfiguration.current.screenHeightDp.dp
                        (screenHeight * 0.6f) + 12.dp
                    })
                    .fillMaxWidth(),
                contentAlignment = Alignment.CenterEnd
            ) {
                Box(
                    modifier = Modifier
                        .padding(end = 24.dp)
                        .shadow(
                            elevation = elevation.value,
                            shape = CircleShape,
                            clip = false
                        )
                        .size(56.dp)
                        .background(
                            color = if (isFlashOn) Color(0xFFFFC107) else Color(0xFF9E9E9E),
                            shape = CircleShape
                        )
                        .clickable(
                            interactionSource = interactionSource,
                            indication = null
                        ) {
                            isFlashOn = !isFlashOn
                            coroutineScope.launch {
                                camera?.cameraControl?.enableTorch(isFlashOn)
                            }
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = if (isFlashOn) Icons.Filled.FlashlightOn else Icons.Filled.FlashlightOff,
                        contentDescription = if (isFlashOn) "关闭手电筒" else "开启手电筒",
                        tint = Color.White,
                        modifier = Modifier.size(24.dp)
                    )
                }
            }
        }
    }

    // 显示修改地址确认对话框
    if (showUrlChangeDialog) {
        ChangeServerUrlDialog(
            newUrl = pendingNewUrl,
            onConfirm = {
                serverUrl = pendingNewUrl
                // 设置新地址后自动开启上传
                uploadEnabled = true
                showUrlChangeDialog = false
                pendingNewUrl = ""
                Toast.makeText(context, "已更新上传地址", Toast.LENGTH_SHORT).show()
            },
            onDismiss = {
                showUrlChangeDialog = false
                pendingNewUrl = ""
            }
        )
    }
}

//相机显示
@Composable
fun CameraPreview(
    lifecycleOwner: androidx.lifecycle.LifecycleOwner,
    onCameraReady: (Camera) -> Unit,
    onBarcodeDetected: (String) -> Unit
) {
    AndroidView(
        factory = { ctx ->
            val previewView = PreviewView(ctx).apply {
                layoutParams = LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT
                )
            }
            val cameraProviderFuture = ProcessCameraProvider.getInstance(ctx)
            cameraProviderFuture.addListener({
                val cameraProvider = cameraProviderFuture.get()
                val preview = Preview.Builder().build().apply {
                    setSurfaceProvider(previewView.surfaceProvider)
                }
                val options = BarcodeScannerOptions.Builder()
                    .setBarcodeFormats(
                        Barcode.FORMAT_QR_CODE,
                        Barcode.FORMAT_CODE_128,
                        Barcode.FORMAT_CODE_39,
                        Barcode.FORMAT_EAN_13,
                        Barcode.FORMAT_EAN_8
                    )
                    .build()
                val barcodeScanner = BarcodeScanning.getClient(options)

                val imageAnalyzer = ImageAnalysis.Builder()
                    .setOutputImageFormat(ImageAnalysis.OUTPUT_IMAGE_FORMAT_RGBA_8888)
                    .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                    .build()
                    .apply {
                        setAnalyzer(ContextCompat.getMainExecutor(ctx)) { imageProxy ->
                            try {
                                val bitmap = previewView.bitmap
                                if (bitmap != null) {
                                    val image = InputImage.fromBitmap(bitmap, 0)
                                    barcodeScanner.process(image)
                                        .addOnSuccessListener { barcodes ->
                                            for (barcode in barcodes) {
                                                barcode.rawValue?.let { value ->
                                                    onBarcodeDetected(value)
                                                }
                                            }
                                        }
                                        .addOnCompleteListener {
                                            imageProxy.close()
                                        }
                                } else {
                                    imageProxy.close()
                                }
                            } catch (e: Exception) {
                                Log.e("Barcode", "分析失败: ${e.message}")
                                imageProxy.close()
                            }
                        }
                    }
                val cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA

                try {
                    cameraProvider.unbindAll()
                    //回调camera对象
                    val camera = cameraProvider.bindToLifecycle(
                        lifecycleOwner,
                        cameraSelector,
                        preview,
                        imageAnalyzer
                    )
                    onCameraReady(camera)
                } catch (e: Exception) {
                    Log.e("CameraX", "绑定失败: ${e.message}")
                }
            }, ContextCompat.getMainExecutor(ctx))
            previewView
        },
        modifier = Modifier.fillMaxSize()
    )
}