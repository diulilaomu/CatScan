package com.example.catscandemo.presentation.viewmodel

import android.content.Context
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.Log
import androidx.camera.core.Camera
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.catscandemo.data.manager.DataManager
import com.example.catscandemo.data.network.DiscoveredServer
import com.example.catscandemo.data.network.NetworkDiscovery
import com.example.catscandemo.domain.model.ScanData
import com.example.catscandemo.domain.model.ScanResult
import com.example.catscandemo.domain.model.TemplateModel
import com.example.catscandemo.domain.use_case.*
import com.example.catscandemo.utils.ImageEnhancer
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.common.InputImage
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject
import kotlin.math.max

/**
 * 主 ViewModel
 * 负责管理应用的状态和业务逻辑
 */
@HiltViewModel
class MainViewModel @Inject constructor(
    private val scanUseCases: ScanUseCases,
    private val templateUseCases: TemplateUseCases,
    private val networkUseCases: NetworkUseCases
) : ViewModel() {

    companion object {
        private const val TAG = "MainViewModel"
    }

    // 数据管理中心
    private val dataManager = DataManager(scanUseCases, templateUseCases)

    // --- 模板存储 ---  
    val templates get() = dataManager.templates
    var activeTemplateId by mutableStateOf<String?>(null)
    var activeTemplate by mutableStateOf<TemplateModel?>(null)

    // 主界面“识别结果”行：楼层选择
    var scanSelectedFloor by mutableStateOf(1)
    
    // 扫描结果StateFlow，用于UI层观察
    private val _scanResults = MutableStateFlow<List<ScanResult>>(emptyList())
    val scanResults: StateFlow<List<ScanResult>> = _scanResults.asStateFlow()

    // 每个模板 + 每层的房间轮询游标（运行态，不做持久化）
    private val cursorByTemplateFloor: MutableMap<String, MutableMap<Int, Int>> = HashMap()

    // 初始化/持久化
    private var storeReady = false
    private var appContext: Context? = null
    // --- 识别结果离线存储 ---
    private var historyReady = false
    // --- 设置离线存储 ---
    private var settingsReady = false

    fun initHistoryStore(context: Context) {
        if (historyReady) return
        historyReady = true
        appContext = context.applicationContext
    }

    fun initSettingsStore(context: Context) {
        if (settingsReady) return
        settingsReady = true
        if (appContext == null) {
            appContext = context.applicationContext
        }
    }

    fun initTemplateStore(context: Context) {
        if (storeReady) return
        storeReady = true
        appContext = context.applicationContext

        // 使用数据管理中心初始化数据
        dataManager.initializeData()
        
        // 同步ViewModel的状态
        activeTemplateId = dataManager.activeTemplateId
        activeTemplate = dataManager.activeTemplate
        
        // 初始化结果列表，确保数据同步
        if (activeTemplateId != null) {
            dataManager.scanUseCases.setCurrentTemplateId(activeTemplateId)
        } else {
            dataManager.scanUseCases.setCurrentTemplateId(null)
        }
        getAllScans()
    }

    fun getAllScans(): List<ScanResult> {
        val scans = dataManager.getAllScans()
        // 确保每次都创建一个新的列表对象，触发StateFlow值变化
        _scanResults.value = scans.toList()
        return scans
    }

    fun setActiveTemplate(id: String) {
        // 使用数据管理中心设置激活模板
        dataManager.setActiveTemplate(id)
        
        // ✅ 切换模板时默认回到 1 层（避免上一模板的楼层残留）
        scanSelectedFloor = 1

        // 同步ViewModel的状态
        activeTemplateId = dataManager.activeTemplateId
        activeTemplate = dataManager.activeTemplate
        
        val t = activeTemplate
        if (t != null) {
            clampSelectedFloor(scanSelectedFloor, t.maxFloor)
        }
        
        // 初始化结果列表，确保数据同步
        dataManager.scanUseCases.setCurrentTemplateId(id)
        // 立即更新UI的扫描结果
        getAllScans()
    }
    
    /**
     * 清除激活模板（设置为无模板）
     */
    fun clearActiveTemplate() {
        // 使用数据管理中心清除激活模板
        dataManager.clearActiveTemplate()
        
        // 同步ViewModel的状态
        activeTemplateId = dataManager.activeTemplateId
        activeTemplate = dataManager.activeTemplate
        
        // 初始化结果列表，确保数据同步
        dataManager.scanUseCases.setCurrentTemplateId(null)
        getAllScans()
    }

    fun addTemplate(name: String) {
        // 使用数据管理中心添加模板
        val template = dataManager.addTemplate(name)
        
        // 同步ViewModel的状态
        activeTemplateId = dataManager.activeTemplateId
        activeTemplate = dataManager.activeTemplate
        
        // ✅ 切换模板时默认回到 1 层（避免上一模板的楼层残留）
        scanSelectedFloor = 1
        
        val t = activeTemplate
        if (t != null) {
            clampSelectedFloor(scanSelectedFloor, t.maxFloor)
        }
    }

    fun deleteTemplate(id: String) {
        val wasActive = (activeTemplateId == id)
        val deletedTemplate = dataManager.deleteTemplate(id)

        // 同步ViewModel的状态
        activeTemplateId = dataManager.activeTemplateId
        activeTemplate = dataManager.activeTemplate

        if (wasActive) {
            // 清空游标缓存（避免残留）
            cursorByTemplateFloor.remove(id)
        }
        
        // 更新扫描结果StateFlow，触发UI更新
        getAllScans()
        
        // 自动同步到PC客户端
        if (uploadEnabled && serverUrl.isNotEmpty() && deletedTemplate != null) {
            viewModelScope.launch(Dispatchers.IO) {
                // 准备删除同步数据
                val batchData = deletedTemplate.scans.map {
                    ScanData(
                        id = it.id,
                        text = it.text,
                        timestamp = it.timestamp,
                        operator = it.operator,
                        campus = it.campus,
                        building = it.building,
                        floor = it.floor,
                        room = it.room,
                        templateId = deletedTemplate.id,
                        templateName = deletedTemplate.name,
                        uploaded = it.uploaded
                    )
                }
                
                // 批量上传删除同步数据
                if (batchData.isNotEmpty()) {
                    networkUseCases.uploadBatchScanData(
                        scanDataList = batchData,
                        serverUrl = serverUrl,
                        onSuccess = {
                            Log.d(TAG, "批量同步删除模板数据成功: ${batchData.size} 条数据")
                        },
                        onError = { error ->
                            Log.e(TAG, "批量同步删除模板数据失败: $error")
                        }
                    )
                }
            }
        }
    }

    fun updateTemplate(updated: TemplateModel) {
        // 使用数据管理中心更新模板
        dataManager.updateTemplate(updated)
        
        // 同步ViewModel的状态
        activeTemplateId = dataManager.activeTemplateId
        activeTemplate = dataManager.activeTemplate
        
        // 如果更新的是当前活动模板，更新activeTemplate状态
        if (activeTemplateId == updated.id) {
            clampSelectedFloor(scanSelectedFloor, updated.maxFloor)
        }
        
        // 自动同步到PC客户端
        if (uploadEnabled && serverUrl.isNotEmpty()) {
            viewModelScope.launch(Dispatchers.IO) {
                // 批量上传数据
                if (updated.scans.isNotEmpty()) {
                    try {
                        networkUseCases.uploadTemplateData(
                            templateId = updated.id,
                            templateName = updated.name,
                            scanDataList = updated.scans,
                            serverUrl = serverUrl,
                            onSuccess = {
                                Log.d(TAG, "批量同步模板数据成功: ${updated.scans.size} 条数据")
                            },
                            onError = {
                                Log.e(TAG, "批量同步模板数据失败: $it")
                            }
                        )
                    } catch (e: Exception) {
                        Log.e(TAG, "同步模板数据异常: ${e.message}", e)
                    }
                }
            }
        }
    }

    fun clearTemplateScans(id: String) {
        // 使用数据管理中心清空模板扫描数据
        dataManager.clearTemplateScans(id)
        
        // 同步ViewModel的状态
        activeTemplateId = dataManager.activeTemplateId
        activeTemplate = dataManager.activeTemplate
        
        // 更新扫描结果StateFlow，触发UI更新
        getAllScans()
    }

    fun deleteTemplateScan(id: String, scanId: String) {
        // 使用数据管理中心删除模板扫描数据
        dataManager.deleteTemplateScan(id, scanId)
        
        // 同步ViewModel的状态
        activeTemplateId = dataManager.activeTemplateId
        activeTemplate = dataManager.activeTemplate
        
        // 更新扫描结果StateFlow，触发UI更新
        getAllScans()
    }

    // --- 运行状态 ---
    var isFlashOn by mutableStateOf(false)
    var serverUrl by mutableStateOf("")
    var uploadEnabled by mutableStateOf(false)
    
    // --- 网络发现 ---
    private var networkDiscovery: NetworkDiscovery? = null
    val discoveredServers = mutableStateListOf<com.example.catscandemo.data.network.DiscoveredServer>()
    var isDiscovering by mutableStateOf(false)
    
    // --- 被动发现 PC：每 1 秒扫描，发现后弹窗 ---
    var discoveredPcToNotify by mutableStateOf<com.example.catscandemo.data.network.DiscoveredServer?>(null)
    private var lastDismissedPcUrl: String? = null
    private var lastDismissedPcTime: Long = 0
    
    private val _clipboardEnabled = mutableStateOf(true)
    var clipboardEnabled: Boolean
        get() = _clipboardEnabled.value
        set(value) {
            _clipboardEnabled.value = value
        }
    
    var showUrlChangeDialog by mutableStateOf(false)
    var pendingNewUrl by mutableStateOf("")
    var camera by mutableStateOf<Camera?>(null)

    private val _duplicateScanEnabled = mutableStateOf(true)
    var duplicateScanEnabled: Boolean
        get() = _duplicateScanEnabled.value
        set(value) {
            _duplicateScanEnabled.value = value
        }

    // 是否显示条码检测框
    private val _showBarcodeOverlay = mutableStateOf(true)
    var showBarcodeOverlay: Boolean
        get() = _showBarcodeOverlay.value
        set(value) {
            _showBarcodeOverlay.value = value
        }

    var showTemplateEditor by mutableStateOf(false)

    // 当前一次扫码写入的字段（每次扫码前会被模板刷新）
    var currentOperator by mutableStateOf("猫头枪")
    var currentCampus by mutableStateOf("天河校区")
    var currentBuilding by mutableStateOf("")
    var currentFloor by mutableStateOf("1层")
    var currentRoom by mutableStateOf("")

    // =============== “按楼层顺序读模板房间号” ===============

    fun selectScanFloor(floor: Int) {
        val maxF = activeTemplate?.maxFloor ?: 1
        clampSelectedFloor(floor, maxF)
    }

    private fun clampSelectedFloor(floor: Int, maxFloor: Int) {
        val maxF = max(1, maxFloor)
        scanSelectedFloor = floor.coerceIn(1, maxF)
    }

    private fun floorOfRoomCode(code: String): Int? {
        // 约定：房间号最后两位为房间序号（01~99），前面为楼层号
        if (code.length < 3) return null
        val floorPart = code.dropLast(2)
        return floorPart.toIntOrNull()
    }

    private fun roomsForFloor(t: TemplateModel, floor: Int): List<String> {
        return t.selectedRooms.filter { floorOfRoomCode(it) == floor }.sorted()
    }

    private fun peekRoom(t: TemplateModel, floor: Int): String? {
        val rooms = roomsForFloor(t, floor)
        if (rooms.isEmpty()) return null

        val map = cursorByTemplateFloor.getOrPut(t.id) { HashMap() }
        val idx = map[floor] ?: 0
        return rooms[idx % rooms.size]
    }
    private fun parseFloorNumber(floorStr: String): Int? {
        // "3层" / "3" / "3F" 都能取到 3
        return Regex("\\d+").find(floorStr)?.value?.toIntOrNull()
    }
    private fun rebuildCursorAfterDelete(floor: Int) {
        val t = activeTemplate ?: return
        val rooms = roomsForFloor(t, floor) // 你已有的方法：返回该楼层房间号列表（已排序）
        if (rooms.isEmpty()) return

        // 删除后，该楼层已识别数量（以识别结果列表为准）
        val usedCount = dataManager.getAllScans().count { parseFloorNumber(it.scanData.floor) == floor }

        // 游标=已使用数量 % 房间数（保证下一次扫描取“正确的下一个”）
        val map = cursorByTemplateFloor.getOrPut(t.id) { HashMap() }
        map[floor] = usedCount % rooms.size
    }

    private fun advanceRoomCursor(t: TemplateModel, floor: Int) {
        val rooms = roomsForFloor(t, floor)
        if (rooms.isEmpty()) return

        val map = cursorByTemplateFloor.getOrPut(t.id) { HashMap() }
        val idx = map[floor] ?: 0
        map[floor] = (idx + 1) % rooms.size
    }

    private fun findNextFloorWithRooms(t: TemplateModel, fromFloor: Int): Int? {
        val maxF = max(1, t.maxFloor)
        // 从下一层开始找，循环一圈
        for (step in 1..maxF) {
            val nf = ((fromFloor - 1 + step) % maxF) + 1
            if (roomsForFloor(t, nf).isNotEmpty()) return nf
        }
        return null
    }

    /** 推进游标，返回：是否“刚好用完本层一轮”(即下次会回到本层第一个房间) */
    private fun advanceRoomCursorAndCheckWrapped(t: TemplateModel, floor: Int): Boolean {
        val rooms = roomsForFloor(t, floor)
        if (rooms.isEmpty()) return false

        val map = cursorByTemplateFloor.getOrPut(t.id) { HashMap() }
        val idx = map[floor] ?: 0
        val nextIdx = idx + 1
        map[floor] = nextIdx % rooms.size

        // 如果 nextIdx 刚好是 rooms.size 的倍数，说明本层一轮用完
        return (nextIdx % rooms.size) == 0
    }

    private fun applyTemplateForNextScan(showToast: (String) -> Unit): Triple<TemplateModel, Int, String?>? {
        val t = activeTemplate ?: return null

        val f = scanSelectedFloor.coerceIn(1, max(1, t.maxFloor))
        val room = peekRoom(t, f)

        if (room == null) {
            showToast("模板「${t.name}」${f}层未选择房间号")
        }

        currentOperator = t.operator.ifBlank { "unknown" }
        currentCampus = t.campus
        currentBuilding = t.building
        currentFloor = "${f}层"
        currentRoom = room ?: currentRoom

        return Triple(t, f, room)
    }


    /**
     * 往当前模板追加一条扫码，与识别结果同步。
     * @param scanData 扫描数据对象，必须与识别结果中的对象一致，确保数据同步。
     */
    private fun appendScanToActiveTemplate(scanData: ScanData) {
        // 使用数据管理中心添加扫描数据到当前模板
        dataManager.addScanToActiveTemplate(scanData)
        
        // 同步ViewModel的状态
        activeTemplateId = dataManager.activeTemplateId
        activeTemplate = dataManager.activeTemplate
        
        // 更新扫描结果StateFlow，确保UI同步
        getAllScans()
    }

    // ===================== 扫码入口 =====================

    fun onBarcodeScanned(
        code: String,
        copyToClipboard: (String) -> Unit,
        showToast: (String) -> Unit
    ) {
        try {
            when {
                code.startsWith("winClientLink:") -> {
                    pendingNewUrl = code.removePrefix("winClientLink:")
                    showUrlChangeDialog = true
                }
                else -> {
                    // 扫描前：先按模板预填字段（不推进游标）
                    val applied = applyTemplateForNextScan(showToast)

                    val activeTemplate = this.activeTemplate
                    val scanData = dataManager.addScan(
                        text = code,
                        templateId = activeTemplate?.id ?: "",
                        templateName = activeTemplate?.name ?: "",
                        operator = currentOperator,
                        campus = currentCampus,
                        building = currentBuilding,
                        floor = currentFloor,
                        room = currentRoom,
                        allowDuplicate = duplicateScanEnabled
                    )

                    if (scanData != null) {
                        // 关键：只有真正写入成功才推进游标，避免跳号
                        applied?.let { (t, f, room) ->
                            if (room != null) {
                                val wrapped = advanceRoomCursorAndCheckWrapped(t, f)

                                // ✅ 本层用完：自动切到下一层
                                if (wrapped) {
                                    val nextFloor = findNextFloorWithRooms(t, f)
                                    if (nextFloor != null && nextFloor != f) {
                                        // 直接更新选中楼层，UI 会跟着变
                                        clampSelectedFloor(nextFloor, t.maxFloor)
                                    }
                                }
                            }
                        }

                        // 只有有模板时才写入模板离线扫码列表
                        if (activeTemplate != null) {
                            // 写入模板离线扫码列表（直接使用返回的scanData对象，确保数据同步）
                            appendScanToActiveTemplate(scanData)
                        }

                        if (clipboardEnabled) {
                            copyToClipboard(code)
                            showToast("已复制: $code")
                        }
                        if (uploadEnabled && serverUrl.isNotEmpty()) {
                            uploadData(scanData, showToast)
                        }
                        // 更新扫描结果StateFlow，触发UI更新
                        getAllScans()

                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "扫码处理异常: ${e.message}", e)
            showToast("处理扫码数据失败：${e.message ?: "未知错误"}")
        }
    }

    fun onToggleFlash() {
        isFlashOn = !isFlashOn
        camera?.cameraControl?.enableTorch(isFlashOn)
    }

    private fun uploadData(scanData: ScanData, showToast: (String) -> Unit) {
        viewModelScope.launch(Dispatchers.IO) {
            networkUseCases.uploadScanData(
                scanData = scanData,
                serverUrl = serverUrl,
                action = "add",
                onSuccess = {
                    // 切换到主线程显示Toast
                    viewModelScope.launch(Dispatchers.Main) { showToast("上传成功: ${scanData.text}") }
                },
                onError = { err ->
                    viewModelScope.launch(Dispatchers.Main) { showToast("上传失败: $err") }
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
        // 使用默认扫描器，自动检测所有条码格式
        val scanner = BarcodeScanning.getClient()

        // 首先尝试直接扫描原图
        val originalImage = try {
            InputImage.fromFilePath(context, uri)
        } catch (e: Exception) {
            Log.e("CatScan", "InputImage.fromFilePath failed, uri=$uri", e)
            showToast("图片读取失败：${e.message ?: e.javaClass.simpleName}")
            scanner.close()  // 确保异常时关闭 scanner
            return
        }

        scanner.process(originalImage)
            .addOnSuccessListener { barcodes ->
                val result = barcodes.firstOrNull()?.rawValue
                if (result != null) {
                    // 原图扫描成功
                    onBarcodeScanned(result, copyToClipboard, showToast)
                    scanner.close()
                } else {
                    // 原图扫描失败，尝试图像增强后再扫描
                    Log.d("CatScan", "原图扫描失败，尝试增强图像...")
                    tryEnhancedScan(uri, context, scanner, copyToClipboard, showToast)
                }
            }
            .addOnFailureListener { e ->
                Log.e("CatScan", "Barcode scan failed, uri=$uri", e)
                showToast("识别失败")
                scanner.close()
            }
    }
    
    /**
     * 使用增强图像进行二次扫描
     */
    private fun tryEnhancedScan(
        uri: Uri,
        context: Context,
        scanner: com.google.mlkit.vision.barcode.BarcodeScanner,
        copyToClipboard: (String) -> Unit,
        showToast: (String) -> Unit
    ) {
        viewModelScope.launch(Dispatchers.IO) {
            val bitmap = try {
                context.contentResolver.openInputStream(uri)?.use { inputStream ->
                    BitmapFactory.decodeStream(inputStream)
                }
            } catch (e: Exception) {
                Log.e("CatScan", "Failed to decode bitmap for enhancement", e)
                null
            }
            
            if (bitmap == null) {
                launch(Dispatchers.Main) {
                    showToast("未识别到条码")
                    scanner.close()
                }
                return@launch
            }
            
            // 使用增强配置处理图像
            val enhancedBitmap = ImageEnhancer.enhanceBitmap(
                bitmap,
                ImageEnhancer.ENHANCE_CONFIG
            )
            
            val enhancedImage = InputImage.fromBitmap(enhancedBitmap, 0)
            
            launch(Dispatchers.Main) {
                scanner.process(enhancedImage)
                    .addOnSuccessListener { enhancedBarcodes ->
                        val enhancedResult = enhancedBarcodes.firstOrNull()?.rawValue
                        
                        // 回收bitmap
                        if (enhancedBitmap != bitmap) {
                            enhancedBitmap.recycle()
                        }
                        bitmap.recycle()
                        
                        if (enhancedResult != null) {
                            Log.d("CatScan", "增强图像扫描成功")
                            onBarcodeScanned(enhancedResult, copyToClipboard, showToast)
                        } else {
                            showToast("未识别到条码")
                        }
                        scanner.close()
                    }
                    .addOnFailureListener { e ->
                        Log.e("CatScan", "Enhanced scan failed", e)
                        
                        // 回收bitmap
                        if (enhancedBitmap != bitmap) {
                            enhancedBitmap.recycle()
                        }
                        bitmap.recycle()
                        
                        showToast("识别失败")
                        scanner.close()
                    }
            }
        }
    }

    fun deleteItemById(id: Long) {
        // 使用数据管理中心删除扫描数据
        val deleted = dataManager.deleteScan(id)

        val floor = deleted?.let { parseFloorNumber(it.scanData.floor) }
        if (floor != null) {
            rebuildCursorAfterDelete(floor)
        }
        
        // 同步ViewModel的状态
        activeTemplateId = dataManager.activeTemplateId
        activeTemplate = dataManager.activeTemplate
        
        // 更新扫描结果StateFlow，触发UI更新
        getAllScans()
        
        // 自动同步到PC客户端
        if (uploadEnabled && serverUrl.isNotEmpty() && deleted != null) {
            viewModelScope.launch(Dispatchers.IO) {
                val scanData = deleted.scanData.copy(
                    templateName = dataManager.activeTemplate?.name ?: ""
                )
                networkUseCases.uploadScanData(
                    scanData = scanData,
                    serverUrl = serverUrl,
                    action = "delete",
                    onSuccess = {
                        Log.d(TAG, "同步删除结果列表数据成功")
                    },
                    onError = {
                        Log.e(TAG, "同步删除结果列表数据失败: $it")
                    }
                )
            }
        }
    }

    fun updateItemById(id: Long, updated: ScanResult) {
        // 使用数据管理中心更新扫描数据
        dataManager.updateScan(id, updated.scanData)
        
        // 同步ViewModel的状态
        activeTemplateId = dataManager.activeTemplateId
        activeTemplate = dataManager.activeTemplate
        
        // 更新扫描结果StateFlow，触发UI更新
        getAllScans()
        
        // 自动同步到PC客户端
        if (uploadEnabled && serverUrl.isNotEmpty()) {
            viewModelScope.launch(Dispatchers.IO) {
                val scanData = updated.scanData.copy(
                    templateName = dataManager.activeTemplate?.name ?: ""
                )
                networkUseCases.uploadScanData(
                    scanData = scanData,
                    serverUrl = serverUrl,
                    action = "update",
                    onSuccess = {
                        Log.d(TAG, "同步修改结果列表数据成功")
                    },
                    onError = {
                        Log.e(TAG, "同步修改结果列表数据失败: $it")
                    }
                )
            }
        }
    }
    
    /**
     * 清空所有扫描结果
     */
    fun clearAllScans(showToast: (String) -> Unit) {
        // 使用数据管理中心清空所有扫描数据
        dataManager.clearAllScans()
        
        // 同步ViewModel的状态
        activeTemplateId = dataManager.activeTemplateId
        activeTemplate = dataManager.activeTemplate
        
        // 更新扫描结果StateFlow，触发UI更新
        getAllScans()
        
        // 显示清空成功提示
        showToast("已清空所有扫描结果")
    }
    
    // ===================== 网络发现 =====================
    
    fun startNetworkDiscovery(context: Context, onDiscoveryComplete: () -> Unit = {}) {
        if (isDiscovering) return
        
        isDiscovering = true
        discoveredServers.clear()
        
        if (networkDiscovery == null) {
            networkDiscovery = NetworkDiscovery(context.applicationContext)
            // 启动被动监听服务
            networkDiscovery?.startPassiveListener()
        }
        
        networkUseCases.startNetworkDiscovery(
            onServerFound = {
                // 转换为 data 层的 DiscoveredServer 类型
                val dataServer = com.example.catscandemo.data.network.DiscoveredServer(
                    ip = it.ip,
                    port = it.port,
                    url = it.url,
                    name = it.name
                )
                if (discoveredServers.none { server -> server.url == dataServer.url }) {
                    discoveredServers.add(dataServer)
                }
            },
            onDiscoveryComplete = {
                isDiscovering = false
                onDiscoveryComplete()
                startPassivePcDiscovery(context) // 手动发现结束后重启被动发现
            }
        )
    }
    
    fun stopNetworkDiscovery() {
        networkUseCases.stopNetworkDiscovery()
        isDiscovering = false
    }
    
    fun selectDiscoveredServer(server: com.example.catscandemo.data.network.DiscoveredServer) {
        // 转换为 domain 层的 DiscoveredServer 类型
        val domainServer = com.example.catscandemo.domain.use_case.DiscoveredServer(
            ip = server.ip,
            port = server.port,
            url = server.url,
            name = server.name
        )
        networkUseCases.selectDiscoveredServer(domainServer)
        serverUrl = server.url
        uploadEnabled = true
        startHeartbeatDetection() // 启动心跳检测
    }
    
    /**
     * 启动被动发现 PC 客户端：每 1 秒扫描一次，发现后主动弹窗。
     * 同时启动被动监听（响应其他设备的发现请求）。
     */
    fun startPassivePcDiscovery(context: Context) {
        if (networkDiscovery == null) {
            networkDiscovery = NetworkDiscovery(context.applicationContext)
        }
        networkDiscovery?.startPassiveListener()
        networkDiscovery?.startContinuousDiscovery(onServerFound = {
            if (discoveredPcToNotify != null) return@startContinuousDiscovery
            if (uploadEnabled && serverUrl.isNotEmpty()) return@startContinuousDiscovery  // 已连接则不再弹窗
            if (lastDismissedPcUrl == it.url &&
                (System.currentTimeMillis() - lastDismissedPcTime) < 5 * 60 * 1000
            ) return@startContinuousDiscovery
            discoveredPcToNotify = it
        })
    }
    
    /**
     * 关闭「发现 PC」弹窗。若为忽略（传入 server），5 分钟内同一 PC 不再弹窗。
     */
    fun dismissDiscoveredPcDialog(ignoredServer: com.example.catscandemo.data.network.DiscoveredServer? = null) {
        ignoredServer?.let {
            lastDismissedPcUrl = it.url
            lastDismissedPcTime = System.currentTimeMillis()
        }
        discoveredPcToNotify = null
    }
    
    /**
     * 使用发现的 PC 作为上传目标，并关闭弹窗。
     */
    fun onUseDiscoveredPc(server: com.example.catscandemo.data.network.DiscoveredServer) {
        selectDiscoveredServer(server)
        discoveredPcToNotify = null
    }
    
    /**
     * 启动被动网络发现监听服务（响应其他设备的发现请求）
     */
    fun startPassiveDiscovery(context: Context) {
        if (networkDiscovery == null) {
            networkDiscovery = NetworkDiscovery(context.applicationContext)
        }
        networkDiscovery?.startPassiveListener()
    }
    
    /**
     * 启动心跳检测：定期检查PC客户端是否在线
     */
    fun startHeartbeatDetection() {
        stopHeartbeatDetection() // 先停止之前的心跳检测
        
        try {
            networkUseCases.startHeartbeatDetection(
                serverUrl = serverUrl,
                onConnectivityChanged = { isConnected ->
                    uploadEnabled = isConnected
                    if (isConnected) {
                        // 服务器连接正常，上传未上传的数据
                        viewModelScope.launch {
                            try {
                                uploadPendingData()
                            } catch (e: Exception) {
                                Log.e(TAG, "心跳检测上传数据异常: ${e.message}", e)
                            }
                        }
                    } else {
                        // 断开连接后重新启动被动发现
                        appContext?.let { 
                            try {
                                startPassivePcDiscovery(it)
                            } catch (e: Exception) {
                                Log.e(TAG, "心跳检测重启被动发现异常: ${e.message}", e)
                            }
                        }
                    }
                }
            )
        } catch (e: Exception) {
            Log.e(TAG, "启动心跳检测异常: ${e.message}", e)
        }
    }
    
    /**
     * 停止心跳检测
     */
    fun stopHeartbeatDetection() {
        networkUseCases.stopHeartbeatDetection()
    }
    
    /**
     * 检查服务器连接状态
     */
    private suspend fun checkServerConnectivity() {
        if (serverUrl.isEmpty()) return
        
        val isConnected = networkUseCases.checkServerConnectivity(serverUrl)
        if (isConnected) {
            // 服务器连接正常，标记为已连接
            uploadEnabled = true
            // 检查是否有未上传的数据需要上传
            uploadPendingData()
        } else {
            // 服务器响应异常，标记为未连接
            uploadEnabled = false
            Log.w(TAG, "服务器连接异常")
            // 断开连接后重新启动被动发现
            appContext?.let { startPassivePcDiscovery(it) }
        }
    }
    
    /**
     * 上传未上传的数据
     */
    private suspend fun uploadPendingData() {
        // 只上传当前选中模板的数据
        val activeTemplate = this.activeTemplate
        if (activeTemplate != null) {
            try {
                // 获取模板的扫描数据
                val templateScans = dataManager.getAllScans().filter { it.scanData.templateId == activeTemplate.id }
                if (templateScans.isNotEmpty()) {
                    Log.d(TAG, "上传模板 ${activeTemplate.name} 的 ${templateScans.size} 条数据...")
                    
                    // 转换为ScanData列表
                    val scanDataList = templateScans.map { it.scanData }
                    
                    networkUseCases.uploadTemplateData(
                        templateId = activeTemplate.id,
                        templateName = activeTemplate.name,
                        scanDataList = scanDataList,
                        serverUrl = serverUrl,
                        onSuccess = {
                            Log.d(TAG, "批量上传成功: ${scanDataList.size} 条数据")
                        },
                        onError = { err ->
                            Log.e(TAG, "批量上传失败: $err")
                        }
                    )
                }
            } catch (e: Exception) {
                Log.e(TAG, "上传未上传数据异常: ${e.message}", e)
            }
        }
    }
    
    /**
     * 上传模板数据到电脑
     */
    fun uploadTemplateData(template: TemplateModel, showToast: (String) -> Unit) {
        if (!uploadEnabled || serverUrl.isEmpty()) {
            showToast("请先连接电脑")
            return
        }
        
        if (template.scans.isEmpty()) {
            showToast("模板内暂无数据")
            return
        }
        
        viewModelScope.launch(Dispatchers.IO) {
            try {
                networkUseCases.uploadTemplateData(
                    templateId = template.id,
                    templateName = template.name,
                    scanDataList = template.scans,
                    serverUrl = serverUrl,
                    onSuccess = {
                        Log.d(TAG, "批量上传成功: ${template.scans.size} 条数据")
                        // 在UI线程中显示Toast
                        viewModelScope.launch(Dispatchers.Main) {
                            showToast("上传完成：成功 ${template.scans.size} 条，失败 0 条")
                        }
                    },
                    onError = { err ->
                        Log.e(TAG, "批量上传失败: $err")
                        // 在UI线程中显示Toast
                        viewModelScope.launch(Dispatchers.Main) {
                            showToast("上传失败：请检查网络连接")
                        }
                    }
                )
            } catch (e: Exception) {
                Log.e(TAG, "上传异常: ${e.message}", e)
                // 在UI线程中显示Toast
                viewModelScope.launch(Dispatchers.Main) {
                    showToast("上传失败：请检查网络连接")
                }
            }
        }
    }
    
    override fun onCleared() {
        super.onCleared()
        networkDiscovery?.cleanup()
        stopHeartbeatDetection() // 停止心跳检测
    }
}
