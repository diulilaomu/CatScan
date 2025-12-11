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

class MainViewModel(
    private val repository: ScanRepository = ScanRepository()
) : ViewModel() {

    var todoList by mutableStateOf<List<String>>(listOf("支持条形码、二维码。"))
        private set

    var isFlashOn by mutableStateOf(false)
    var serverUrl by mutableStateOf("")
    var uploadEnabled by mutableStateOf(false)
    var clipboardEnabled by mutableStateOf(true)
    var showUrlChangeDialog by mutableStateOf(false)
    var pendingNewUrl by mutableStateOf("")
    var camera by mutableStateOf<Camera?>(null)

    // UI can pass lambdas for clipboard and toast actions
    fun onBarcodeScanned(
        code: String,
        copyToClipboard: (String) -> Unit,
        showToast: (String) -> Unit
    ) {
        if (code.startsWith("winClientLink:")) {
            pendingNewUrl = code.removePrefix("winClientLink:")
            showUrlChangeDialog = true
            return
        }

        // 原逻辑：if (code !in todoList) {
        // 新逻辑：仅当 code 不是列表第一个元素时，才添加到头部
        if (todoList.isEmpty() || todoList.first() != code) {
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

    fun onToggleFlash() {
        isFlashOn = !isFlashOn
        camera?.cameraControl?.enableTorch(isFlashOn)
    }

    private fun uploadData(code: String, showToast: (String) -> Unit) {
        viewModelScope.launch(Dispatchers.IO) {
            println("DEBUG: Starting upload for $code")

            repository.upload(
                code, serverUrl,
                onSuccess = {
                    println("DEBUG: Upload success callback received")
                    // 获取 ViewModelScope 而不是使用父协程的 scope
                    viewModelScope.launch(Dispatchers.Main) {
                        println("DEBUG: Inside viewModelScope.launch Main, about to show toast")
                        showToast("上传成功: $code")
                    }
                },
                onError = { err ->
                    println("DEBUG: Upload error callback received: $err")
                    // 获取 ViewModelScope 而不是使用父协程的 scope
                    viewModelScope.launch(Dispatchers.Main) {
                        println("DEBUG: Inside viewModelScope.launch Main, about to show error toast")
                        showToast("上传失败: $err")
                    }
                }
            )
        }
    }

    fun deleteItem(index: Int) {
        todoList = todoList.toMutableList().apply { removeAt(index) }
    }
}
