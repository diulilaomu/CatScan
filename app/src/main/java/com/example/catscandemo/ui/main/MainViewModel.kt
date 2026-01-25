package com.example.catscandemo.ui.main

import android.content.Context
import android.net.Uri
import androidx.camera.core.Camera
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.catscandemo.data.network.DiscoveredServer
import com.example.catscandemo.data.network.NetworkDiscovery
import com.example.catscandemo.data.repository.ScanRepository
import com.google.mlkit.vision.barcode.BarcodeScannerOptions
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.common.InputImage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.util.UUID
import kotlin.math.max

// ===================== 基础扫码数据结构 =====================

data class AreaInfo(
    val campus: String = "",
    val building: String = "",
    val floor: String = "",
    val room: String = ""
) {
    fun display(): String = listOf(campus, building, floor, room).joinToString(" / ")
}

data class ScanItem(
    val id: Long,
    val index: Int,
    val text: String,
    val templateId: String = "",       // ✅ 新增：归属模板
    val operator: String = "unknown",
    val area: AreaInfo = AreaInfo(),
    val timestamp: Long = System.currentTimeMillis(),
    val uploaded: Boolean = false
)


class ScanHistoryManager {
    private var nextIndex: Int = 1
    private var nextId: Long = 1L

    val items = mutableStateListOf<ScanItem>()

    fun add(
        text: String,
        templateId: String = "",           // ✅ 新增
        operator: String = "unknown",
        area: AreaInfo = AreaInfo(),
        allowDuplicate: Boolean
    ): Boolean {
        if (allowDuplicate) {
            if (items.isNotEmpty() && items.first().text == text) return false
        } else {
            if (items.any { it.text == text }) return false
        }

        val item = ScanItem(
            id = nextId++,
            index = nextIndex++,
            text = text,
            templateId = templateId,       // ✅ 写入
            operator = operator,
            area = area
        )
        items.add(0, item)
        return true
    }


    fun deleteById(id: Long) {
        val idx = items.indexOfFirst { it.id == id }
        if (idx != -1) items.removeAt(idx)
    }

    fun updateById(id: Long, updated: ScanItem) {
        val idx = items.indexOfFirst { it.id == id }
        if (idx != -1) items[idx] = updated
    }
    fun replaceAll(newItems: List<ScanItem>) {
        items.clear()
        items.addAll(newItems)

        // 恢复自增序号，避免新扫描 id/index 重复
        val maxId = newItems.maxOfOrNull { it.id } ?: 0L
        val maxIndex = newItems.maxOfOrNull { it.index } ?: 0
        nextId = maxId + 1
        nextIndex = maxIndex + 1
    }

}

// ===================== 模板数据结构（离线 JSON 保存） =====================

data class TemplateScan(
    val text: String,
    val timestamp: Long,
    val operator: String,
    val campus: String,
    val building: String,
    val floor: String,
    val room: String
)

data class TemplateModel(
    val id: String = UUID.randomUUID().toString(),
    val name: String = "未命名模板",
    val operator: String = "",
    val campus: String = "",
    val building: String = "",
    val maxFloor: Int = 1,
    val roomCountPerFloor: Int = 1,
    val selectedRooms: List<String> = emptyList(),
    val scans: List<TemplateScan> = emptyList()
)

// ===================== ViewModel =====================

class MainViewModel(
    private val repository: ScanRepository = ScanRepository(),
    val historyManager: ScanHistoryManager = ScanHistoryManager()
) : ViewModel() {

    // --- 模板存储 ---
    val templates = mutableStateListOf<TemplateModel>()
    var activeTemplateId by mutableStateOf<String?>(null)

    // 主界面“识别结果”行：楼层选择
    var scanSelectedFloor by mutableStateOf(1)

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
        appContext = context.applicationContext  // 复用同一个 appContext

        val loaded = ScanHistoryStorage.load(appContext!!)
        historyManager.replaceAll(loaded.items)
    }

    fun initSettingsStore(context: Context) {
        if (settingsReady) return
        settingsReady = true
        if (appContext == null) {
            appContext = context.applicationContext
        }

        val loaded = SettingsStorage.load(appContext!!)
        _clipboardEnabled.value = loaded.clipboardEnabled
        _duplicateScanEnabled.value = loaded.duplicateScanEnabled
    }

    private fun persistHistory() {
        val ctx = appContext ?: return
        val list = historyManager.items.toList()
        viewModelScope.launch(Dispatchers.IO) {
            ScanHistoryStorage.save(ctx, list)
        }
    }

    fun initTemplateStore(context: Context) {
        if (storeReady) return
        storeReady = true
        appContext = context.applicationContext

        val loaded = TemplateStorage.load(appContext!!)
        templates.clear()
        templates.addAll(loaded.templates)
        activeTemplateId = loaded.activeId

        if (activeTemplateId == null && templates.isNotEmpty()) {
            setActiveTemplate(templates.first().id)
        }
    }

    private fun persistTemplates() {
        val ctx = appContext ?: return
        val list = templates.toList()
        val active = activeTemplateId
        viewModelScope.launch(Dispatchers.IO) {
            TemplateStorage.save(ctx, list, active)
        }
    }

    private fun persistSettings() {
        val ctx = appContext ?: return
        val settings = SettingsStorage.Settings(
            clipboardEnabled = clipboardEnabled,
            duplicateScanEnabled = duplicateScanEnabled
        )
        viewModelScope.launch(Dispatchers.IO) {
            SettingsStorage.save(ctx, settings)
        }
    }

    fun getActiveTemplate(): TemplateModel? {
        val id = activeTemplateId ?: return null
        return templates.firstOrNull { it.id == id }
    }

    fun setActiveTemplate(id: String) {
        activeTemplateId = id

        // ✅ 切换模板时默认回到 1 层（避免上一模板的楼层残留）
        scanSelectedFloor = 1

        val t = templates.firstOrNull { it.id == id }
        if (t != null) {
            clampSelectedFloor(scanSelectedFloor, t.maxFloor)
        }
        persistTemplates()
    }


    fun addTemplate(name: String) {
        val t = TemplateModel(
            name = name.trim().ifBlank { "未命名模板" },
            operator = "张国豪",
            campus = "天河校区",
            building = "",
            maxFloor = 1,
            roomCountPerFloor = 1,
            selectedRooms = emptyList(),
            scans = emptyList()
        )
        templates.add(0, t)
        setActiveTemplate(t.id)
        persistTemplates()
    }

    fun deleteTemplate(id: String) {
        val wasActive = (activeTemplateId == id)

        val idx = templates.indexOfFirst { it.id == id }
        if (idx != -1) templates.removeAt(idx)

        if (wasActive) {
            // 删的是当前模板：尝试把 active 切到第一个；如果没有模板了则为 null
            activeTemplateId = templates.firstOrNull()?.id

            // ✅ 清空识别结果（全部）
            historyManager.items.clear()

            // ✅ 清空游标缓存（避免残留）
            cursorByTemplateFloor.remove(id)

            // ✅ 保存识别结果到本地（scan_history.json）
            persistHistory()
        }

        persistTemplates()
    }


    fun updateTemplate(updated: TemplateModel) {
        val idx = templates.indexOfFirst { it.id == updated.id }
        if (idx != -1) templates[idx] = updated
        // active 的话，校正楼层选择
        if (activeTemplateId == updated.id) {
            clampSelectedFloor(scanSelectedFloor, updated.maxFloor)
        }
        persistTemplates()
    }

    fun clearTemplateScans(id: String) {
        val t = templates.firstOrNull { it.id == id } ?: return
        updateTemplate(t.copy(scans = emptyList()))
    }

    fun deleteTemplateScan(id: String, timestamp: Long) {
        val t = templates.firstOrNull { it.id == id } ?: return
        updateTemplate(t.copy(scans = t.scans.filterNot { it.timestamp == timestamp }))
    }

    // --- 运行状态 ---
    var isFlashOn by mutableStateOf(false)
    var serverUrl by mutableStateOf("")
    var uploadEnabled by mutableStateOf(false)
    
    // --- 网络发现 ---
    private var networkDiscovery: NetworkDiscovery? = null
    val discoveredServers = mutableStateListOf<DiscoveredServer>()
    var isDiscovering by mutableStateOf(false)
    
    // --- 被动发现 PC：每 1 秒扫描，发现后弹窗 ---
    var discoveredPcToNotify by mutableStateOf<DiscoveredServer?>(null)
    private var lastDismissedPcUrl: String? = null
    private var lastDismissedPcTime: Long = 0
    
    private val _clipboardEnabled = mutableStateOf(true)
    var clipboardEnabled: Boolean
        get() = _clipboardEnabled.value
        set(value) {
            _clipboardEnabled.value = value
            persistSettings()
        }
    
    var showUrlChangeDialog by mutableStateOf(false)
    var pendingNewUrl by mutableStateOf("")
    var camera by mutableStateOf<Camera?>(null)

    private val _duplicateScanEnabled = mutableStateOf(true)
    var duplicateScanEnabled: Boolean
        get() = _duplicateScanEnabled.value
        set(value) {
            _duplicateScanEnabled.value = value
            persistSettings()
        }
    
    var showTemplateEditor by mutableStateOf(false)

    // 当前一次扫码写入的字段（每次扫码前会被模板刷新）
    var currentOperator by mutableStateOf("张国豪")
    var currentArea by mutableStateOf(AreaInfo(campus = "天河校区"))

    // =============== “按楼层顺序读模板房间号” ===============

    fun selectScanFloor(floor: Int) {
        val maxF = getActiveTemplate()?.maxFloor ?: 1
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
        val t = getActiveTemplate() ?: return
        val rooms = roomsForFloor(t, floor) // 你已有的方法：返回该楼层房间号列表（已排序）
        if (rooms.isEmpty()) return

        // 删除后，该楼层已识别数量（以识别结果列表为准）
        val usedCount = historyManager.items.count { parseFloorNumber(it.area.floor) == floor }

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
        val t = getActiveTemplate() ?: return null

        val f = scanSelectedFloor.coerceIn(1, max(1, t.maxFloor))
        val room = peekRoom(t, f)

        if (room == null) {
            showToast("模板「${t.name}」${f}层未选择房间号")
        }

        currentOperator = t.operator.ifBlank { "unknown" }
        currentArea = currentArea.copy(
            campus = t.campus,
            building = t.building,
            floor = "${f}层",
            room = room ?: currentArea.room
        )

        return Triple(t, f, room)
    }


    /**
     * 往当前模板追加一条扫码，与识别结果同步。
     * @param timestamp 必须与对应 ScanItem 的 timestamp 一致，以便删除时能正确匹配并同步到模板。
     */
    private fun appendScanToActiveTemplate(text: String, timestamp: Long) {
        val t = getActiveTemplate() ?: return
        val record = TemplateScan(
            text = text,
            timestamp = timestamp,
            operator = currentOperator,
            campus = currentArea.campus,
            building = currentArea.building,
            floor = currentArea.floor,
            room = currentArea.room
        )
        updateTemplate(t.copy(scans = listOf(record) + t.scans))
    }

    // ===================== 扫码入口 =====================

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
            else -> {
                // 扫描前：先按模板预填字段（不推进游标）
                val applied = applyTemplateForNextScan(showToast)

                val added = historyManager.add(
                    text = code,
                    templateId = activeTemplateId ?: "",   // ✅ 关键：绑定到当前模板
                    operator = currentOperator,
                    area = currentArea,
                    allowDuplicate = duplicateScanEnabled
                )


                if (added) {
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



                    // 写入模板离线扫码列表（与识别结果同一条，用相同 timestamp 以便删除时同步）
                    appendScanToActiveTemplate(code, historyManager.items.first().timestamp)

                    if (clipboardEnabled) {
                        copyToClipboard(code)
                        showToast("已复制: $code")
                    }
                    if (uploadEnabled && serverUrl.isNotEmpty()) {
                        uploadData(code, showToast)
                    }
                    persistHistory()

                }
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
                qrData = code,
                url = serverUrl,
                templateName = getActiveTemplate()?.name ?: "",
                operator = currentOperator,
                campus = currentArea.campus,
                building = currentArea.building,
                floor = currentArea.floor,
                room = currentArea.room,
                onSuccess = {
                    viewModelScope.launch(Dispatchers.Main) { showToast("上传成功: $code") }
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
        viewModelScope.launch {
            val image = try {
                InputImage.fromFilePath(context, uri)
            } catch (e: Exception) {
                android.util.Log.e("CatScan", "InputImage.fromFilePath failed, uri=$uri", e)
                showToast("图片读取失败：${e.message ?: e.javaClass.simpleName}")
                return@launch
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

            val scanner = BarcodeScanning.getClient(options)

            scanner.process(image)
                .addOnSuccessListener { barcodes ->
                    val c = barcodes.firstOrNull()?.rawValue
                    viewModelScope.launch(Dispatchers.Main) {
                        if (c != null) onBarcodeScanned(c, copyToClipboard, showToast)
                        else showToast("未识别到条码")
                    }
                    scanner.close()
                }
                .addOnFailureListener { e2 ->
                    android.util.Log.e("CatScan", "Barcode scan failed, uri=$uri", e2)
                    viewModelScope.launch(Dispatchers.Main) { showToast("识别失败") }
                    scanner.close()
                }
        }
    }

    fun deleteItemById(id: Long) {
        val deleted = historyManager.items.firstOrNull { it.id == id }
        historyManager.deleteById(id)

        // 同步到模板数据：从对应模板的 scans 中删除同一条（按 templateId + timestamp 匹配）
        if (deleted != null && deleted.templateId.isNotBlank()) {
            deleteTemplateScan(deleted.templateId, deleted.timestamp)
        }

        val floor = deleted?.let { parseFloorNumber(it.area.floor) }
        if (floor != null) {
            rebuildCursorAfterDelete(floor)
        }

        // ✅ 保存识别结果列表
        persistHistory()
    }



    fun updateItemById(id: Long, updated: ScanItem) {
        historyManager.updateById(id, updated)
        persistHistory()
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
        
        networkDiscovery?.startDiscovery(
            onServerFound = { server ->
                if (discoveredServers.none { it.url == server.url }) {
                    discoveredServers.add(server)
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
        networkDiscovery?.stopDiscovery()
        isDiscovering = false
    }
    
    fun selectDiscoveredServer(server: DiscoveredServer) {
        serverUrl = server.url
        uploadEnabled = true
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
        networkDiscovery?.startContinuousDiscovery(onServerFound = { server ->
            if (discoveredPcToNotify != null) return@startContinuousDiscovery
            if (serverUrl.isNotEmpty()) return@startContinuousDiscovery  // 已配置连接则不再弹窗
            if (lastDismissedPcUrl == server.url &&
                (System.currentTimeMillis() - lastDismissedPcTime) < 5 * 60 * 1000
            ) return@startContinuousDiscovery
            discoveredPcToNotify = server
        })
    }
    
    /**
     * 关闭「发现 PC」弹窗。若为忽略（传入 server），5 分钟内同一 PC 不再弹窗。
     */
    fun dismissDiscoveredPcDialog(ignoredServer: DiscoveredServer? = null) {
        ignoredServer?.let {
            lastDismissedPcUrl = it.url
            lastDismissedPcTime = System.currentTimeMillis()
        }
        discoveredPcToNotify = null
    }
    
    /**
     * 使用发现的 PC 作为上传目标，并关闭弹窗。
     */
    fun onUseDiscoveredPc(server: DiscoveredServer) {
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
    
    override fun onCleared() {
        super.onCleared()
        networkDiscovery?.cleanup()
    }

}
