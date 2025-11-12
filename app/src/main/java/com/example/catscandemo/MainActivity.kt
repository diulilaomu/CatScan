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
import androidx.compose.material3.DividerDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.*
import androidx.compose.ui.graphics.*
import androidx.compose.ui.unit.*
import androidx.compose.runtime.*
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.compose.material.icons.filled.FlashlightOn
import androidx.compose.material.icons.filled.FlashlightOff
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ripple
import androidx.compose.ui.draw.shadow
import kotlinx.coroutines.launch
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight


class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            SplitScreen()
        }
    }
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

    // 添加交互状态用于阴影效果
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val elevation = animateDpAsState(
        targetValue = if (isPressed) 0.dp else 8.dp,
        animationSpec = tween(durationMillis = 150)
    )

    Box(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize()) {
            // 摄像头区域
            Box(
                modifier = Modifier
                    .weight(6f)
                    .fillMaxWidth()
                    .background(Color(0xFF90CAF9)),
                contentAlignment = Alignment.Center
            ) {
                AutoRequestCameraPermission {
                    CameraPreview(
                        lifecycleOwner,
                        onCameraReady = { cam -> camera = cam },
                        onBarcodeDetected = { code ->
                            if (code !in todoList) {
                                todoList = listOf(code) + todoList
                                clipboardManager.setText(AnnotatedString(code))
                                Toast.makeText(context, "已复制: $code", Toast.LENGTH_SHORT).show()
                            }
                        }
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
                    text = "识别结果列表",
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 16.dp, horizontal = 20.dp),
                    style = MaterialTheme.typography.headlineSmall, // Material Design 3 的样式
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

        // 闪光灯按钮 - 使用绝对定位覆盖在两个区域之间
        Box(
            modifier = Modifier
                .align(Alignment.TopCenter) // 顶部居中定位
                .offset(y = with(LocalDensity.current) {
                    // 计算按钮应该位于的位置：摄像头区域的底部
                    // 假设摄像头区域占6/10，按钮应该在6/10的位置
                    // 减去按钮高度的一半使其居中在两个区域之间
                    val screenHeight = LocalConfiguration.current.screenHeightDp.dp
                    (screenHeight * 0.6f) - 28.dp // 28.dp是按钮高度56.dp的一半
                })
                .fillMaxWidth(),
            contentAlignment = Alignment.CenterEnd // 内容靠右对齐
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

//相机显示
@Composable
fun CameraPreview(lifecycleOwner: androidx.lifecycle.LifecycleOwner
                  ,onCameraReady: (Camera) -> Unit
                  ,onBarcodeDetected: (String) -> Unit // 新增参数
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

private fun processImageProxy(
    barcodeScanner: BarcodeScanner,
    imageProxy: ImageProxy,
    onDetected: (String) -> Unit
) {
    @androidx.camera.core.ExperimentalGetImage
    val mediaImage = imageProxy.image
    @androidx.camera.core.ExperimentalGetImage
    if (mediaImage != null) {
        val image = InputImage.fromMediaImage(mediaImage, imageProxy.imageInfo.rotationDegrees)
        barcodeScanner.process(image)
            .addOnSuccessListener { barcodes ->
                for (barcode in barcodes) {
                    barcode.rawValue?.let { value ->
                        onDetected(value)
                    }
                }
            }
            .addOnCompleteListener {
                imageProxy.close()
            }
    } else {
        imageProxy.close()
    }
}