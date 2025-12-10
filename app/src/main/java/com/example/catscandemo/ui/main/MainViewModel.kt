package com.example.catscandemo.ui.main

import androidx.camera.core.Camera
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.catscandemo.data.repository.ScanRepository
import com.example.catscandemo.domain.model.ScanResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.*

class MainViewModel(
    private val repository: ScanRepository = ScanRepository()
) : ViewModel() {

    var todoList by mutableStateOf<List<String>>(listOf("支持条形码、二维码！","22维码！"))
        private set

    var isFlashOn by mutableStateOf(false)
    var serverUrl by mutableStateOf("")
    var uploadEnabled by mutableStateOf(false)
    var clipboardEnabled by mutableStateOf(true)
    var showUrlChangeDialog by mutableStateOf(false)
    var pendingNewUrl by mutableStateOf("")
    var camera by mutableStateOf<Camera?>(null)

    // UI can pass lambdas for clipboard and toast actions
    fun onBarcodeScanned(code: String, copyToClipboard: (String) -> Unit, showToast: (String) -> Unit) {
        if (code.startsWith("winClientLink:")) {
            pendingNewUrl = code.removePrefix("winClientLink:")
            showUrlChangeDialog = true
            return
        }

        if (code !in todoList) {
            todoList = listOf(code) + todoList

            if (clipboardEnabled) {
                copyToClipboard(code)
                showToast("已复制: $code")
            }

            if (uploadEnabled && serverUrl.isNotEmpty()) {
                uploadData(code, showToast)
            }
        }
    }
    fun onToggleFlash(){
        isFlashOn =! isFlashOn
        camera?.cameraControl?.enableTorch(isFlashOn)
    }
    private fun uploadData(code: String, showToast: (String) -> Unit) {
        viewModelScope.launch(Dispatchers.IO) {
            repository.upload(code, serverUrl,
                onSuccess = { showToast("上传成功") },
                onError = { err -> showToast("上传失败: $err") }
            )
        }
    }

    fun deleteItem(index: Int) {
        todoList = todoList.toMutableList().apply { removeAt(index) }
    }
}
