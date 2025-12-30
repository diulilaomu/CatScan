package com.example.catscandemo.ui.main

import androidx.camera.core.Camera
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.catscandemo.data.repository.ScanRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.*
import androidx.compose.runtime.mutableStateListOf
import android.content.Context
import android.graphics.ImageDecoder
import android.net.Uri
import android.os.Build
import com.google.mlkit.vision.barcode.BarcodeScannerOptions
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.common.InputImage

//todolist数据管理对象
data class AreaInfo(
    val campus: String = "",
    val building: String = "",
    val floor: String = "",
    val room: String = ""
) {
    // 用于拼接显示，例如：天河校区 / A栋 / 3楼 / 302
    fun display(): String {
        return listOf(campus, building, floor, room).joinToString(" / ")
    }
}
data class ScanItem(
    val index: Int,                     // 序号
    val text: String,                   // 扫码内容
    val operator: String = "unknown",   // 操作人
    val area: AreaInfo = AreaInfo(),    // 区域信息
    val timestamp: Long = System.currentTimeMillis(),
    val uploaded: Boolean = false
)
class ScanHistoryManager {

    private var nextIndex: Int = 1

    val items = mutableStateListOf<ScanItem>()

    init {
        // 示例项
        add(
            text = "支持条形码、二维码。",
            operator = "张国豪",
            area = AreaInfo(
                campus = "白云校区",
                building = "A7栋",
                floor = "3层",
                room = "303"
            )
        )
    }

    fun add(
        text: String,
        operator: String = "unknown",
        area: AreaInfo = AreaInfo()
    ): Boolean {
        if (items.isNotEmpty() && items.first().text == text) {
            return false
        }

        val item = ScanItem(
            index = nextIndex++,
            text = text,
            operator = operator,
            area = area
        )

        items.add(0, item)
        return true
    }

    fun delete(index: Int) {
        if (index in items.indices) {
            items.removeAt(index)
        }
    }

    fun getAll(): List<ScanItem> = items
}

class MainViewModel(
    private val repository: ScanRepository = ScanRepository(),
    val historyManager: ScanHistoryManager = ScanHistoryManager()
) : ViewModel() {

    var isFlashOn by mutableStateOf(false)
    var serverUrl by mutableStateOf("")
    var uploadEnabled by mutableStateOf(false)
    var clipboardEnabled by mutableStateOf(true)
    var showUrlChangeDialog by mutableStateOf(false)
    var pendingNewUrl by mutableStateOf("")
    var camera by mutableStateOf<Camera?>(null)

    // 新增：操作人（未来可从设置页修改）
    var currentOperator by mutableStateOf("张国豪")

    // 区域信息（未来可从下拉菜单选择）
    var currentArea by mutableStateOf(
        AreaInfo(
            campus = "天河校区",
            building = "",
            floor = "",
            room = "0"
        )
    )

    fun onBarcodeScanned(
        code: String,
        copyToClipboard: (String) -> Unit,
        showToast: (String) -> Unit
    ) {
        when {
            code.startsWith("winClientLink:") -> {
                pendingNewUrl = code.removePrefix("winClientLink:")
                showUrlChangeDialog = true
            }
            historyManager.add(
                text = code,
                operator = currentOperator,
                area = currentArea
            ) -> {
                if (clipboardEnabled) {
                    copyToClipboard(code)
                    showToast("已复制: $code")
                }

                if (uploadEnabled && serverUrl.isNotEmpty()) {
                    uploadData(code, showToast)
                }
            }

            else -> {
                // 重复扫码，不做任何处理
            }
        }
    }

    fun onToggleFlash() {
        isFlashOn = !isFlashOn
        camera?.cameraControl?.enableTorch(isFlashOn)
    }

    private fun uploadData(code: String, showToast: (String) -> Unit) {
        viewModelScope.launch(Dispatchers.IO) {
            repository.upload(
                code, serverUrl,
                onSuccess = {
                    viewModelScope.launch(Dispatchers.Main) {
                        showToast("上传成功: $code")
                    }
                },
                onError = { err ->
                    viewModelScope.launch(Dispatchers.Main) {
                        showToast("上传失败: $err")
                    }
                }
            )
        }
    }
    fun onImagePicked(
        uri: Uri,
        context: Context,
        copyToClipboard: (String) -> Unit,
        showToast: (String) -> Unit
    ) {
        viewModelScope.launch(Dispatchers.IO) {
            val bitmap = try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                    val source = ImageDecoder.createSource(context.contentResolver, uri)
                    ImageDecoder.decodeBitmap(source)
                } else {
                    @Suppress("DEPRECATION")
                    android.provider.MediaStore.Images.Media.getBitmap(
                        context.contentResolver,
                        uri
                    )
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    showToast("图片读取失败")
                }
                return@launch
            }

            val image = InputImage.fromBitmap(bitmap, 0)

            val options = BarcodeScannerOptions.Builder()
                .setBarcodeFormats(
                    Barcode.FORMAT_QR_CODE,
                    Barcode.FORMAT_CODE_128,
                    Barcode.FORMAT_CODE_39,
                    Barcode.FORMAT_EAN_13,
                    Barcode.FORMAT_EAN_8
                )
                .build()

            val scanner = BarcodeScanning.getClient(options)

            scanner.process(image)
                .addOnSuccessListener { barcodes ->
                    barcodes.firstOrNull()?.rawValue?.let { code ->
                        onBarcodeScanned(code, copyToClipboard, showToast)
                    } ?: showToast("未识别到条码")
                }
                .addOnFailureListener {
                    showToast("识别失败")
                }
        }
    }
    fun deleteItem(index: Int) {
        historyManager.delete(index)
    }
    fun updateItem(index: Int, updatedItem: ScanItem) {
        historyManager.items[index] = updatedItem
    }
}
