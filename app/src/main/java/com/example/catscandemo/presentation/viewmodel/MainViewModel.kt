package com.example.catscandemo.presentation.viewmodel

import android.content.Context
import android.graphics.Bitmap
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
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.common.InputImage
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject
import java.net.URL
import kotlin.math.max

/**
 * MainViewModel
 * 璐熻矗绠＄悊搴旂敤鐨勭姸鎬佸拰涓氬姟閫昏緫
 */
@HiltViewModel
class MainViewModel @Inject constructor(
    private val scanUseCases: ScanUseCases,
    private val templateUseCases: TemplateUseCases,
    private val networkUseCases: NetworkUseCases
) : ViewModel() {

    companion object {
        private const val TAG = "MainViewModel"
        private const val CLIENT_BLOCKED_FLAG = "CLIENT_BLOCKED"
        private const val CLIENT_BLOCKED_MESSAGE = "鐢佃剳瀹㈡埛绔凡闃绘姝よ澶?
    }

    // 鏁版嵁绠＄悊涓績
    private val dataManager = DataManager(scanUseCases, templateUseCases)

    // --- 妯℃澘瀛樺偍 ---
    val templates get() = dataManager.templates
    var activeTemplateId by mutableStateOf<String?>(null)
    var activeTemplate by mutableStateOf<TemplateModel?>(null)

    // 涓荤晫闈㈣瘑鍒粨鏋滆锛氭ゼ灞傞€夋嫨
    var scanSelectedFloor by mutableStateOf(1)

    // 鎵弿缁撴灉StateFlow锛岀敤浜嶶I灞傝瀵?    private val _scanResults = MutableStateFlow<List<ScanResult>>(emptyList())
    val scanResults: StateFlow<List<ScanResult>> = _scanResults.asStateFlow()

    // 姣忎釜妯℃澘 + 姣忓眰鐨勬埧闂磋疆璇㈡父鏍囷紙杩愯鎬侊紝涓嶅仛鎸佷箙鍖栵級
    private val cursorByTemplateFloor: MutableMap<String, MutableMap<Int, Int>> = HashMap()

    // 鍒濆鍖栨寔涔呭寲
    private var storeReady = false
    private var appContext: Context? = null
    // --- 璇嗗埆缁撴灉绂荤嚎瀛樺偍 ---
    private var historyReady = false
    // --- 璁剧疆绂荤嚎瀛樺偍 ---
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

        // 浣跨敤鏁版嵁绠＄悊涓績鍒濆鍖栨暟鎹?        dataManager.initializeData()

        // 鍚屾ViewModel鐨勭姸鎬?        activeTemplateId = dataManager.activeTemplateId
        activeTemplate = dataManager.activeTemplate

        // 鍒濆鍖栫粨鏋滃垪琛紝纭繚鏁版嵁鍚屾
        if (activeTemplateId != null) {
            dataManager.scanUseCases.setCurrentTemplateId(activeTemplateId)
        } else {
            dataManager.scanUseCases.setCurrentTemplateId(null)
        }
        getAllScans()
    }

    fun getAllScans(): List<ScanResult> {
        val scans = dataManager.getAllScans()
        // 纭繚姣忔閮藉垱寤轰竴涓柊鐨勫垪琛ㄥ璞★紝瑙﹀彂StateFlow鍊煎彉鍖?        _scanResults.value = scans.toList()
        return scans
    }

    fun setActiveTemplate(id: String) {
        // 浣跨敤鏁版嵁绠＄悊涓績璁剧疆婵€娲绘ā鏉?        dataManager.setActiveTemplate(id)

        // 鍒囨崲妯℃澘鏃堕粯璁ゅ洖鍒?灞傦紙閬垮厤涓婁竴妯℃澘鐨勬ゼ灞傛畫鐣欙級
        scanSelectedFloor = 1

        // 鍚屾ViewModel鐨勭姸鎬?        activeTemplateId = dataManager.activeTemplateId
        activeTemplate = dataManager.activeTemplate

        val t = activeTemplate
        if (t != null) {
            clampSelectedFloor(scanSelectedFloor, t.maxFloor)
        }

        // 鍒濆鍖栫粨鏋滃垪琛紝纭繚鏁版嵁鍚屾
        dataManager.scanUseCases.setCurrentTemplateId(id)
        // 绔嬪嵆鏇存柊UI鐨勬壂鎻忕粨鏋?        getAllScans()
    }

    /**
     * 娓呴櫎婵€娲绘ā鏉匡紙璁剧疆涓烘棤妯℃澘锛?     */
    fun clearActiveTemplate() {
        // 浣跨敤鏁版嵁绠＄悊涓績娓呴櫎婵€娲绘ā鏉?        dataManager.clearActiveTemplate()

        // 鍚屾ViewModel鐨勭姸鎬?        activeTemplateId = dataManager.activeTemplateId
        activeTemplate = dataManager.activeTemplate

        // 鍒濆鍖栫粨鏋滃垪琛紝纭繚鏁版嵁鍚屾
        dataManager.scanUseCases.setCurrentTemplateId(null)
        getAllScans()
    }

    fun addTemplate(name: String) {
        // 浣跨敤鏁版嵁绠＄悊涓績娣诲姞妯℃澘
        val template = dataManager.addTemplate(name)

        // 鍚屾ViewModel鐨勭姸鎬?        activeTemplateId = dataManager.activeTemplateId
        activeTemplate = dataManager.activeTemplate

        // 鍒囨崲妯℃澘鏃堕粯璁ゅ洖鍒?灞傦紙閬垮厤涓婁竴妯℃澘鐨勬ゼ灞傛畫鐣欙級
        scanSelectedFloor = 1

        val t = activeTemplate
        if (t != null) {
            clampSelectedFloor(scanSelectedFloor, t.maxFloor)
        }
    }

    fun deleteTemplate(id: String) {
        val wasActive = (activeTemplateId == id)
        val deletedTemplate = dataManager.deleteTemplate(id)

        // 鍚屾ViewModel鐨勭姸鎬?        activeTemplateId = dataManager.activeTemplateId
        activeTemplate = dataManager.activeTemplate

        if (wasActive) {
            // 娓呯┖娓告爣缂撳瓨锛堥伩鍏嶆畫鐣欙級
            cursorByTemplateFloor.remove(id)
        }

        // 鏇存柊鎵弿缁撴灉StateFlow锛岃Е鍙慤I鏇存柊
        getAllScans()

        // 鑷姩鍚屾鍒癙C瀹㈡埛绔?        if (uploadEnabled && serverUrl.isNotEmpty() && deletedTemplate != null) {
            viewModelScope.launch(Dispatchers.IO) {
                // 鍑嗗鍒犻櫎鍚屾鏁版嵁
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

                // 鎵归噺涓婁紶鍒犻櫎鍚屾鏁版嵁
                if (batchData.isNotEmpty()) {
                    networkUseCases.uploadBatchScanData(
                        scanDataList = batchData,
                        serverUrl = serverUrl,
                        onSuccess = {
                            Log.d(TAG, "Batch template-delete sync succeeded: ${batchData.size} items")
                        },
                        onError = { error ->
                            Log.e(TAG, "鎵归噺鍚屾鍒犻櫎妯℃澘鏁版嵁澶辫触: $error")
                        }
                    )
                }
            }
        }
    }

    fun updateTemplate(updated: TemplateModel) {
        // 浣跨敤鏁版嵁绠＄悊涓績鏇存柊妯℃澘
        dataManager.updateTemplate(updated)

        // 鍚屾ViewModel鐨勭姸鎬?        activeTemplateId = dataManager.activeTemplateId
        activeTemplate = dataManager.activeTemplate

        // 濡傛灉鏇存柊鐨勬槸褰撳墠娲诲姩妯℃澘锛屾洿鏂癮ctiveTemplate鐘舵€?        if (activeTemplateId == updated.id) {
            clampSelectedFloor(scanSelectedFloor, updated.maxFloor)
        }

        // 鑷姩鍚屾鍒癙C瀹㈡埛绔?        if (uploadEnabled && serverUrl.isNotEmpty()) {
            viewModelScope.launch(Dispatchers.IO) {
                // 鎵归噺涓婁紶鏁版嵁
                if (updated.scans.isNotEmpty()) {
                    try {
                        networkUseCases.uploadTemplateData(
                            templateId = updated.id,
                            templateName = updated.name,
                            scanDataList = updated.scans,
                            serverUrl = serverUrl,
                            onSuccess = {
                                Log.d(TAG, "Batch template sync succeeded: ${updated.scans.size} items")
                            },
                            onError = {
                                Log.e(TAG, "鎵归噺鍚屾妯℃澘鏁版嵁澶辫触: $it")
                            }
                        )
                    } catch (e: Exception) {
                        Log.e(TAG, "鍚屾妯℃澘鏁版嵁寮傚父: ${e.message}", e)
                    }
                }
            }
        }
    }

    fun clearTemplateScans(id: String) {
        // 浣跨敤鏁版嵁绠＄悊涓績娓呯┖妯℃澘鎵弿鏁版嵁
        dataManager.clearTemplateScans(id)

        // 鍚屾ViewModel鐨勭姸鎬?        activeTemplateId = dataManager.activeTemplateId
        activeTemplate = dataManager.activeTemplate

        // 鏇存柊鎵弿缁撴灉StateFlow锛岃Е鍙慤I鏇存柊
        getAllScans()
    }

    fun deleteTemplateScan(id: String, scanId: String) {
        // 浣跨敤鏁版嵁绠＄悊涓績鍒犻櫎妯℃澘鎵弿鏁版嵁
        dataManager.deleteTemplateScan(id, scanId)

        // 鍚屾ViewModel鐨勭姸鎬?        activeTemplateId = dataManager.activeTemplateId
        activeTemplate = dataManager.activeTemplate

        // 鏇存柊鎵弿缁撴灉StateFlow锛岃Е鍙慤I鏇存柊
        getAllScans()
    }

    // --- 杩愯鐘舵€?---
    var isFlashOn by mutableStateOf(false)
    var serverUrl by mutableStateOf("")
    var uploadEnabled by mutableStateOf(false)
    private var lastHeartbeatConnected: Boolean? = null
    private var kickedNotified: Boolean = false

    // --- 缃戠粶鍙戠幇 ---
    private var networkDiscovery: NetworkDiscovery? = null
    val discoveredServers = mutableStateListOf<com.example.catscandemo.data.network.DiscoveredServer>()
    var isDiscovering by mutableStateOf(false)

    // --- 琚姩鍙戠幇 PC锛氭瘡 1 绉掓壂鎻忥紝鍙戠幇鍚庡脊绐?---
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

    fun updateServerUrl(rawUrl: String) {
        val normalized = normalizeServerUrl(rawUrl)
        if (serverUrl == normalized) return
        serverUrl = normalized
        kickedNotified = false
        if (serverUrl.isEmpty()) {
            uploadEnabled = false
            lastHeartbeatConnected = null
            stopHeartbeatDetection()
            return
        }
        if (uploadEnabled) {
            lastHeartbeatConnected = null
            startHeartbeatDetection()
        }
    }

    fun setUploadEnabledByUser(enabled: Boolean) {
        if (!enabled) {
            uploadEnabled = false
            lastHeartbeatConnected = null
            kickedNotified = false
            stopHeartbeatDetection()
            return
        }
        if (serverUrl.isEmpty()) {
            uploadEnabled = false
            kickedNotified = false
            return
        }
        lastHeartbeatConnected = null
        kickedNotified = false
        uploadEnabled = true
        stopNetworkDiscovery()
        startHeartbeatDetection()
    }

    private fun normalizeServerUrl(rawUrl: String): String {
        var value = rawUrl.trim()
        if (value.isEmpty()) return ""
        if (value.startsWith("winClientLink:")) {
            value = value.removePrefix("winClientLink:")
        }
        if (!value.startsWith("http://") && !value.startsWith("https://")) {
            value = "http://$value"
        }
        return try {
            val parsed = URL(value)
            val host = parsed.host?.trim().orEmpty()
            if (host.isEmpty()) return value
            val protocol = if (parsed.protocol.isNullOrBlank()) "http" else parsed.protocol
            val portPart = if (parsed.port > 0) ":${parsed.port}" else ""
            val path = parsed.path?.trim().orEmpty()
            val normalizedPath = if (path.isEmpty() || path == "/") "/postqrdata" else path
            "$protocol://$host$portPart$normalizedPath"
        } catch (_: Exception) {
            value
        }
    }

    private fun isClientBlockedError(error: String?): Boolean {
        if (error.isNullOrBlank()) return false
        return error.contains(CLIENT_BLOCKED_FLAG) ||
            error.contains("client blocked", ignoreCase = true) ||
            error.contains("HTTP 403")
    }

    private fun notifyClientBlocked(showToast: ((String) -> Unit)? = null) {
        lastHeartbeatConnected = false
        uploadEnabled = false
        if (kickedNotified) return
        kickedNotified = true
        val message = CLIENT_BLOCKED_MESSAGE
        if (showToast != null) {
            showToast(message)
            return
        }
        val ctx = appContext ?: return
        viewModelScope.launch(Dispatchers.Main) {
            android.widget.Toast.makeText(ctx, message, android.widget.Toast.LENGTH_SHORT).show()
        }
    }

    private fun handleClientBlocked(error: String?, showToast: ((String) -> Unit)? = null): Boolean {
        if (!isClientBlockedError(error)) return false
        notifyClientBlocked(showToast)
        return true
    }

    private val _duplicateScanEnabled = mutableStateOf(true)
    var duplicateScanEnabled: Boolean
        get() = _duplicateScanEnabled.value
        set(value) {
            _duplicateScanEnabled.value = value
        }

    // 鏄惁鏄剧ず鏉＄爜妫€娴嬫
    private val _showBarcodeOverlay = mutableStateOf(true)
    var showBarcodeOverlay: Boolean
        get() = _showBarcodeOverlay.value
        set(value) {
            _showBarcodeOverlay.value = value
        }

    // ===== Scan parameters =====
    private val _channel1ScanFrameInterval = mutableStateOf(3)
    var channel1ScanFrameInterval: Int
        get() = _channel1ScanFrameInterval.value
        set(value) {
            _channel1ScanFrameInterval.value = value.coerceAtLeast(1)
        }

    private val _channel2MinAreaScore = mutableStateOf(3.5)
    var channel2MinAreaScore: Double
        get() = _channel2MinAreaScore.value
        set(value) {
            _channel2MinAreaScore.value = value.coerceIn(0.0, 100.0)
        }

    private val _channel2MinAspectScore = mutableStateOf(28.0)
    var channel2MinAspectScore: Double
        get() = _channel2MinAspectScore.value
        set(value) {
            _channel2MinAspectScore.value = value.coerceIn(0.0, 100.0)
        }

    private val _channel2MinSolidityScore = mutableStateOf(10.0)
    var channel2MinSolidityScore: Double
        get() = _channel2MinSolidityScore.value
        set(value) {
            _channel2MinSolidityScore.value = value.coerceIn(0.0, 100.0)
        }

    private val _channel2MinGradScore = mutableStateOf(8.0)
    var channel2MinGradScore: Double
        get() = _channel2MinGradScore.value
        set(value) {
            _channel2MinGradScore.value = value.coerceIn(0.0, 100.0)
        }

    var showTemplateEditor by mutableStateOf(false)

    // 褰撳墠涓嬫鎵爜鍐欏叆鐨勫瓧娈碉紙姣忔鎵爜鍓嶄細琚ā鏉垮埛鏂帮級
    var currentOperator by mutableStateOf("unknown")
    var currentCampus by mutableStateOf("澶╂渤鏍″尯")
    var currentBuilding by mutableStateOf("")
    var currentFloor by mutableStateOf("1F")
    var currentRoom by mutableStateOf("")

    // =============== "鎸夋ゼ灞傞『搴忚妯℃澘鎴块棿鍙?===============

    fun selectScanFloor(floor: Int) {
        val maxF = activeTemplate?.maxFloor ?: 1
        clampSelectedFloor(floor, maxF)
    }

    private fun clampSelectedFloor(floor: Int, maxFloor: Int) {
        val maxF = max(1, maxFloor)
        scanSelectedFloor = floor.coerceIn(1, maxF)
    }

    private fun floorOfRoomCode(code: String): Int? {
        // 绾﹀畾锛氭埧闂村彿鏈€鍚庝袱浣嶄负鎴块棿搴忓彿锛?~99锛夛紝鍓嶉潰涓烘ゼ灞傚彿
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
        // "3妤? / "3" / "3F" 閮借兘鍙栧埌 3
        return Regex("\\d+").find(floorStr)?.value?.toIntOrNull()
    }

    private fun rebuildCursorAfterDelete(floor: Int) {
        val t = activeTemplate ?: return

        val rooms = roomsForFloor(t, floor) // 浣犲凡鏈夌殑鏂规硶锛氳繑鍥炶妤煎眰鎴块棿鍙峰垪琛紙宸叉帓搴忥級

        if (rooms.isEmpty()) return

        // 鍒犻櫎鍚庯紝璇ユゼ灞傚凡璇嗗埆鏁伴噺锛堜互璇嗗埆缁撴灉鍒楄〃涓哄噯锛?        val usedCount = dataManager.getAllScans().count { parseFloorNumber(it.scanData.floor) == floor }

        // 娓告爣=宸蹭娇鐢ㄦ暟閲?% 鎴块棿鏁帮紙淇濊瘉涓嬩竴娆℃壂鎻忓彇"姝ｇ‘鐨勪笅涓€涓?)
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
        // 浠庝笅涓€灞傚紑濮嬫壘锛屽惊鐜竴鍛?        for (step in 1..maxF) {
            val nf = ((fromFloor - 1 + step) % maxF) + 1
            if (roomsForFloor(t, nf).isNotEmpty()) return nf
        }
        return null
    }

    /** 鎺ㄨ繘娓告爣锛岃繑鍥烇細鏄惁"鍒氬ソ鐢ㄥ畬鏈眰涓€杞?锛堝嵆涓嬫浼氬洖鍒版湰灞傜涓€涓埧闂达級 */
    private fun advanceRoomCursorAndCheckWrapped(t: TemplateModel, floor: Int): Boolean {
        val rooms = roomsForFloor(t, floor)
        if (rooms.isEmpty()) return false

        val map = cursorByTemplateFloor.getOrPut(t.id) { HashMap() }
        val idx = map[floor] ?: 0
        val nextIdx = idx + 1
        map[floor] = nextIdx % rooms.size

        // 濡傛灉 nextIdx 鍒氬ソ鏄?rooms.size 鐨勫€嶆暟锛岃鏄庢湰灞備竴杞敤瀹?        return (nextIdx % rooms.size) == 0
    }

    private fun applyTemplateForNextScan(showToast: (String) -> Unit): Triple<TemplateModel, Int, String?>? {
        val t = activeTemplate ?: return null

        val f = scanSelectedFloor.coerceIn(1, max(1, t.maxFloor))
        val room = peekRoom(t, f)

        currentOperator = t.operator.ifBlank { "unknown" }
        currentCampus = t.campus
        currentBuilding = t.building
        currentFloor = "${f}F"
        currentRoom = room ?: currentRoom

        return Triple(t, f, room)
    }

    /**
     * 鍚戝綋鍓嶆ā鏉胯拷鍔?鏉℃壂鐮侊紝涓庤瘑鍒粨鏋滃悓姝?     * @param scanData 鎵爜鏁版嵁瀵硅薄锛屽繀椤讳笌璇嗗埆缁撴灉涓殑瀵硅薄涓€鑷达紝纭繚鏁版嵁鍚屾
     */
    private fun appendScanToActiveTemplate(scanData: ScanData) {
        // 浣跨敤鏁版嵁绠＄悊涓績娣诲姞鎵弿鏁版嵁鍒板綋鍓嶆ā鏉?        dataManager.addScanToActiveTemplate(scanData)

        // 鍚屾ViewModel鐨勭姸鎬?        activeTemplateId = dataManager.activeTemplateId
        activeTemplate = dataManager.activeTemplate

        // 鏇存柊鎵弿缁撴灉StateFlow锛岀‘淇漊I鍚屾
        getAllScans()
    }

    // ===================== 鎵爜鍏ュ彛 =====================

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
                    // 鎵弿鍓嶏細鍏堟寜妯℃澘棰勫～瀛楁锛堜笉鎺ㄨ繘娓告爣锛?                    val applied = applyTemplateForNextScan(showToast)

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
                        // 鍏抽敭锛氬彧鏈夌湡姝ｅ啓鍏ユ垚鍔熸墠鎺ㄨ繘娓告爣锛岄伩鍏嶈烦鍙?                        applied?.let { (t, f, room) ->
                            if (room != null) {
                                val wrapped = advanceRoomCursorAndCheckWrapped(t, f)

                                // 濡傛灉鏈眰鐢ㄥ畬锛氳嚜鍔ㄥ垏鎹㈠埌涓嬩竴灞?                                if (wrapped) {
                                    val nextFloor = findNextFloorWithRooms(t, f)
                                    if (nextFloor != null && nextFloor != f) {
                                        // 鐩存帴鏇存柊閫変腑妤煎眰锛孶I 浼氳窡涓?                                        clampSelectedFloor(nextFloor, t.maxFloor)
                                    }
                                }
                            }
                        }

                        // 鍙湁鏈夋ā鏉挎椂鎵嶅啓鍏ユā鏉跨绾挎壂鐮佸垪琛?                        if (activeTemplate != null) {
                            // 鍐欏叆妯℃澘绂荤嚎鎵爜鍒楄〃锛堢洿鎺ヤ娇鐢ㄨ繑鍥炵殑scanData瀵硅薄锛岀‘淇濇暟鎹悓姝ワ級
                            appendScanToActiveTemplate(scanData)
                        }

                        if (clipboardEnabled) {
                            copyToClipboard(code)
                            showToast("宸插鍒? $code")
                        }

                        if (uploadEnabled && serverUrl.isNotEmpty()) {
                            uploadData(scanData, showToast)
                        }

                        // 鏇存柊鎵弿缁撴灉StateFlow锛岃Е鍙慤I鏇存柊
                        getAllScans()
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "鎵爜澶勭悊寮傚父: ${e.message}", e)
            showToast("Failed to process scan: ${e.message ?: "Unknown error"}")
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
                    // 鍒囨崲鍒颁富绾跨▼鏄剧ずToast
                    viewModelScope.launch(Dispatchers.Main) { showToast("涓婁紶鎴愬姛: ${scanData.text}") }
                },
                onError = { err ->
                    viewModelScope.launch(Dispatchers.Main) {
                        if (handleClientBlocked(err, showToast)) return@launch
                        showToast("涓婁紶澶辫触: $err")
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
        // 浣跨敤榛樿鎵弿鍣紝鑷姩妫€娴嬫墍鏈夋潯鐮佹牸寮?        val scanner = BarcodeScanning.getClient()

        // 棣栧厛灏濊瘯鐩存帴鎵弿鍘熷浘
        val originalImage = try {
            InputImage.fromFilePath(context, uri)
        } catch (e: Exception) {
            Log.e("CatScan", "InputImage.fromFilePath failed, uri=$uri", e)
            showToast("Failed to read image: ${e.message ?: e.javaClass.simpleName}")
            scanner.close()  // 纭繚寮傚父鏃跺叧闂璼canner
            return
        }

        scanner.process(originalImage)
            .addOnSuccessListener { barcodes ->
                val result = barcodes.firstOrNull()?.rawValue
                if (result != null) {
                    // 鍘熷浘鎵弿鎴愬姛
                    onBarcodeScanned(result, copyToClipboard, showToast)
                    scanner.close()
                } else {
                    // 鍘熷浘鎵弿澶辫触锛屽皾璇曞浘鍍忓寮哄悗鍐嶆壂鎻?                    Log.d("CatScan", "鍘熷浘鎵弿澶辫触锛屽皾璇曞寮哄浘鍍?..")
                    tryEnhancedScan(uri, context, scanner, copyToClipboard, showToast)
                }
            }
            .addOnFailureListener { e ->
                Log.e("CatScan", "Barcode scan failed, uri=$uri", e)
                showToast("璇嗗埆澶辫触")
                scanner.close()
            }
    }

    /**
     * 浣跨敤澧炲己鍥惧儚杩涜浜屾鎵弿
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
                    showToast("鏈瘑鍒埌鏉＄爜")
                    scanner.close()
                }
                return@launch
            }

            // 浣跨敤澧炲己閰嶇疆澶勭悊鍥惧儚
            val enhancedBitmap = buildCenterCroppedBitmap(bitmap, 0.7f)

            val enhancedImage = InputImage.fromBitmap(enhancedBitmap, 0)

            launch(Dispatchers.Main) {
                scanner.process(enhancedImage)
                    .addOnSuccessListener { enhancedBarcodes ->
                        val enhancedResult = enhancedBarcodes.firstOrNull()?.rawValue

                        // 鍥炴敹bitmap
                        if (enhancedBitmap != bitmap) {
                            enhancedBitmap.recycle()
                        }
                        bitmap.recycle()

                        if (enhancedResult != null) {
                            Log.d("CatScan", "澧炲己鍥惧儚鎵弿鎴愬姛")
                            onBarcodeScanned(enhancedResult, copyToClipboard, showToast)
                        } else {
                            showToast("鏈瘑鍒埌鏉＄爜")
                        }
                        scanner.close()
                    }
                    .addOnFailureListener { e ->
                        Log.e("CatScan", "Enhanced scan failed", e)

                        // 鍥炴敹bitmap
                        if (enhancedBitmap != bitmap) {
                            enhancedBitmap.recycle()
                        }
                        bitmap.recycle()

                        showToast("璇嗗埆澶辫触")
                        scanner.close()
                    }
            }
        }
    }

    private fun buildCenterCroppedBitmap(source: Bitmap, ratio: Float): Bitmap {
        val safeRatio = ratio.coerceIn(0.3f, 1.0f)
        val cropWidth = (source.width * safeRatio).toInt().coerceIn(1, source.width)
        val cropHeight = (source.height * safeRatio).toInt().coerceIn(1, source.height)
        val left = ((source.width - cropWidth) / 2).coerceAtLeast(0)
        val top = ((source.height - cropHeight) / 2).coerceAtLeast(0)
        return Bitmap.createBitmap(source, left, top, cropWidth, cropHeight)
    }

    fun deleteItemById(id: Long) {
        // 浣跨敤鏁版嵁绠＄悊涓績鍒犻櫎鎵弿鏁版嵁
        val deleted = dataManager.deleteScan(id)

        val floor = deleted?.let { parseFloorNumber(it.scanData.floor) }
        if (floor != null) {
            rebuildCursorAfterDelete(floor)
        }

        // 鍚屾ViewModel鐨勭姸鎬?        activeTemplateId = dataManager.activeTemplateId
        activeTemplate = dataManager.activeTemplate

        // 鏇存柊鎵弿缁撴灉StateFlow锛岃Е鍙慤I鏇存柊
        getAllScans()

        // 鑷姩鍚屾鍒癙C瀹㈡埛绔?        if (uploadEnabled && serverUrl.isNotEmpty() && deleted != null) {
            viewModelScope.launch(Dispatchers.IO) {
                val scanData = deleted.scanData.copy(
                    templateName = dataManager.activeTemplate?.name ?: ""
                )
                networkUseCases.uploadScanData(
                    scanData = scanData,
                    serverUrl = serverUrl,
                    action = "delete",
                    onSuccess = {
                        Log.d(TAG, "鍚屾鍒犻櫎缁撴灉鍒楄〃鏁版嵁鎴愬姛")
                    },
                    onError = {
                        Log.e(TAG, "鍚屾鍒犻櫎缁撴灉鍒楄〃鏁版嵁澶辫触: $it")
                    }
                )
            }
        }
    }

    fun updateItemById(id: Long, updated: ScanResult) {
        // 浣跨敤鏁版嵁绠＄悊涓績鏇存柊鎵弿鏁版嵁
        dataManager.updateScan(id, updated.scanData)

        // 鍚屾ViewModel鐨勭姸鎬?        activeTemplateId = dataManager.activeTemplateId
        activeTemplate = dataManager.activeTemplate

        // 鏇存柊鎵弿缁撴灉StateFlow锛岃Е鍙慤I鏇存柊
        getAllScans()

        // 鑷姩鍚屾鍒癙C瀹㈡埛绔?        if (uploadEnabled && serverUrl.isNotEmpty()) {
            viewModelScope.launch(Dispatchers.IO) {
                val scanData = updated.scanData.copy(
                    templateName = dataManager.activeTemplate?.name ?: ""
                )
                networkUseCases.uploadScanData(
                    scanData = scanData,
                    serverUrl = serverUrl,
                    action = "update",
                    onSuccess = {
                        Log.d(TAG, "鍚屾淇敼缁撴灉鍒楄〃鏁版嵁鎴愬姛")
                    },
                    onError = {
                        Log.e(TAG, "鍚屾淇敼缁撴灉鍒楄〃鏁版嵁澶辫触: $it")
                    }
                )
            }
        }
    }

    /**
     * 娓呯┖鎵€鏈夋壂鎻忕粨鏋?     */
    fun clearAllScans(showToast: (String) -> Unit) {
        // 浣跨敤鏁版嵁绠＄悊涓績娓呯┖鎵€鏈夋壂鎻忔暟鎹?        dataManager.clearAllScans()

        // 鍚屾ViewModel鐨勭姸鎬?        activeTemplateId = dataManager.activeTemplateId
        activeTemplate = dataManager.activeTemplate

        // 鏇存柊鎵弿缁撴灉StateFlow锛岃Е鍙慤I鏇存柊
        getAllScans()

        // 鏄剧ず娓呯┖鎴愬姛鎻愮ず
        showToast("Cleared all scan results")
    }

    // ===================== 缃戠粶鍙戠幇 =====================

    fun startNetworkDiscovery(context: Context, onDiscoveryComplete: () -> Unit = {}) {
        if (isDiscovering) return

        isDiscovering = true
        discoveredServers.clear()

        if (networkDiscovery == null) {
            networkDiscovery = NetworkDiscovery(context.applicationContext)
            // 鍚姩琚姩鐩戝惉鏈嶅姟
            networkDiscovery?.startPassiveListener()
        }

        networkDiscovery?.startContinuousDiscovery { server ->
            // 杞崲涓篸ata灞傜殑 DiscoveredServer 绫诲瀷
            val dataServer = com.example.catscandemo.data.network.DiscoveredServer(
                ip = server.ip,
                port = server.port,
                url = server.url,
                name = server.name
            )
            if (discoveredServers.none { s -> s.url == dataServer.url }) {
                discoveredServers.add(dataServer)
            }

            if (lastHeartbeatConnected == true || serverUrl.isNotEmpty()) {
                return@startContinuousDiscovery
            }

            selectDiscoveredServer(dataServer)
            stopNetworkDiscovery()
            isDiscovering = false
            onDiscoveryComplete()
        }
    }

    fun stopNetworkDiscovery() {
        networkUseCases.stopNetworkDiscovery()
        networkDiscovery?.stopDiscovery()
        isDiscovering = false
    }

    fun selectDiscoveredServer(server: com.example.catscandemo.data.network.DiscoveredServer) {
        // 杞崲涓篸omain灞傜殑 DiscoveredServer 绫诲瀷
        val domainServer = com.example.catscandemo.domain.use_case.DiscoveredServer(
            ip = server.ip,
            port = server.port,
            url = server.url,
            name = server.name
        )
        networkUseCases.selectDiscoveredServer(domainServer)
        updateServerUrl(server.url)
        setUploadEnabledByUser(true)
    }

    /**
     * 鍚姩琚姩鍙戠幇 PC 瀹㈡埛绔細姣?绉掓壂鎻忎竴娆★紝鍙戠幇鍚庝富鍔ㄥ脊绐?     * 鍚屾椂鍚姩琚姩鐩戝惉锛堝搷搴斿叾浠栬澶囩殑鍙戠幇璇锋眰锛?     */
    fun startPassivePcDiscovery(context: Context) {
        if (networkDiscovery == null) {
            networkDiscovery = NetworkDiscovery(context.applicationContext)
        }
        networkDiscovery?.startPassiveListener()
        networkDiscovery?.startContinuousDiscovery(onServerFound = {
            if (discoveredPcToNotify != null) return@startContinuousDiscovery
            if (lastHeartbeatConnected == true && serverUrl.isNotEmpty()) return@startContinuousDiscovery  // 宸茶繛鎺ュ垯涓嶅啀寮圭獥
            if (lastDismissedPcUrl == it.url &&
                (System.currentTimeMillis() - lastDismissedPcTime) < 5 * 60 * 1000
            ) return@startContinuousDiscovery
            discoveredPcToNotify = it
        })
    }

    /**
     * 鍏抽棴銆屽彂鐜癙C銆嶅脊绐楄嫢涓哄拷鐣ワ紙浼犲叆 server锛夛紝5 鍒嗛挓鍐呭悓1鍙?PC 涓嶅啀寮圭獥
     */
    fun dismissDiscoveredPcDialog(ignoredServer: com.example.catscandemo.data.network.DiscoveredServer? = null) {
        ignoredServer?.let {
            lastDismissedPcUrl = it.url
            lastDismissedPcTime = System.currentTimeMillis()
        }
        discoveredPcToNotify = null
    }

    /**
     * 浣跨敤鍙戠幇鐨?PC 浣滀负涓婁紶鐩爣锛屽苟鍏抽棴寮圭獥
     */
    fun onUseDiscoveredPc(server: com.example.catscandemo.data.network.DiscoveredServer) {
        selectDiscoveredServer(server)
        discoveredPcToNotify = null
    }

    /**
     * 鍚姩琚姩缃戠粶鍙戠幇鐩戝惉鏈嶅姟锛堝搷搴斿叾浠栬澶囩殑鍙戠幇璇锋眰锛?     */
    fun startPassiveDiscovery(context: Context) {
        if (networkDiscovery == null) {
            networkDiscovery = NetworkDiscovery(context.applicationContext)
        }
        networkDiscovery?.startPassiveListener()
    }

    /**
     * 鍚姩蹇冭烦妫€娴嬶細瀹氭湡妫€娴婸C瀹㈡埛绔槸鍚﹀湪绾?     */
    fun startHeartbeatDetection() {
        stopHeartbeatDetection() // 鍏堝仠姝箣鍓嶇殑蹇冭烦妫€娴?
        try {
            networkUseCases.startHeartbeatDetection(
                serverUrl = serverUrl,
                onConnectivityChanged = { isConnected ->
                    val previous = lastHeartbeatConnected
                    lastHeartbeatConnected = isConnected
                    uploadEnabled = isConnected
                    if (isConnected) {
                        kickedNotified = false
                        // 鏈嶅姟鍣ㄨ繛鎺ユ甯革紝涓婁紶鏈笂浼犵殑鏁版嵁
                        viewModelScope.launch {
                            try {
                                uploadPendingData()
                            } catch (e: Exception) {
                                Log.e(TAG, "蹇冭烦妫€娴嬩笂浼犳暟鎹紓甯? ${e.message}", e)
                            }
                        }
                    } else if (previous != false) {
                        // 鏂紑杩炴帴鍚庤繘鍏ヤ富鍔ㄥ彂鐜?                        appContext?.let {
                            try {
                                startNetworkDiscovery(it)
                            } catch (e: Exception) {
                                Log.e(TAG, "蹇冭烦妫€娴嬮噸鍚富鍔ㄥ彂鐜板紓甯? ${e.message}", e)
                            }
                        }
                    }
                },
                onBlocked = { err ->
                    handleClientBlocked(err)
                }
            )
        } catch (e: Exception) {
            Log.e(TAG, "鍚姩蹇冭烦妫€娴嬪紓甯? ${e.message}", e)
        }
    }

    /**
     * 鍋滄蹇冭烦妫€娴?     */
    fun stopHeartbeatDetection() {
        networkUseCases.stopHeartbeatDetection()
        lastHeartbeatConnected = null
        kickedNotified = false
    }

    /**
     * 妫€鏌ユ湇鍔″櫒杩炴帴鐘舵€?     */
    private suspend fun checkServerConnectivity() {
        if (serverUrl.isEmpty()) return

        val isConnected = networkUseCases.checkServerConnectivity(serverUrl)
        if (isConnected) {
            // 鏈嶅姟鍣ㄨ繛鎺ユ甯革紝鏍囪涓哄凡杩炴帴
            uploadEnabled = true
            // 妫€鏌ユ槸鍚︽湁鏈笂浼犵殑鏁版嵁闇€瑕佷笂浼?            uploadPendingData()
        } else {
            // 鏈嶅姟鍣ㄥ搷搴斿紓甯革紝鏍囪涓烘湭杩炴帴
            uploadEnabled = false
            Log.w(TAG, "Server connectivity check failed")
            // 鏂紑杩炴帴鍚庨噸鏂板惎鍔ㄨ鍔ㄥ彂鐜?            appContext?.let { startPassivePcDiscovery(it) }
        }
    }

    /**
     * 涓婁紶鏈笂浼犵殑鏁版嵁
     */
    private suspend fun uploadPendingData() {
        // 鍙笂浼犲綋鍓嶆ā鏉跨殑鏁版嵁
        val activeTemplate = this.activeTemplate
        if (activeTemplate != null) {
            try {
                // 鑾峰彇妯℃澘鐨勬壂鎻忔暟鎹?                val templateScans = dataManager.getAllScans().filter { it.scanData.templateId == activeTemplate.id }

                if (templateScans.isNotEmpty()) {
                    Log.d(TAG, "涓婁紶妯℃澘 ${activeTemplate.name} 鐨?${templateScans.size} 鏉℃暟鎹?..")

                    // 杞崲涓篠canData鍒楄〃
                    val scanDataList = templateScans.map { it.scanData }

                    networkUseCases.uploadTemplateData(
                        templateId = activeTemplate.id,
                        templateName = activeTemplate.name,
                        scanDataList = scanDataList,
                        serverUrl = serverUrl,
                        onSuccess = {
                            Log.d(TAG, "Batch upload succeeded: ${scanDataList.size} items")
                        },
                        onError = { err ->
                            Log.e(TAG, "鎵归噺涓婁紶澶辫触: $err")
                        }
                    )
                }
            } catch (e: Exception) {
                Log.e(TAG, "涓婁紶鏈笂浼犳暟鎹紓甯? ${e.message}", e)
            }
        }
    }

    /**
     * 涓婁紶妯℃澘鏁版嵁鍒扮數鑴?     */
    fun uploadTemplateData(template: TemplateModel, showToast: (String) -> Unit) {
        if (!uploadEnabled || serverUrl.isEmpty()) {
            showToast("璇峰厛杩炴帴鐢佃剳")
            return
        }

        if (template.scans.isEmpty()) {
            val name = template.name.ifBlank { "Unnamed" }
            showToast("Template $name has no data")
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
                        Log.d(TAG, "Batch upload succeeded: ${template.scans.size} items")

                        // 鍦║I绾跨▼涓樉绀篢oast
                        viewModelScope.launch(Dispatchers.Main) {
                            showToast("Upload complete: success ${template.scans.size}, failed 0")
                        }
                    },
                    onError = { err ->
                        Log.e(TAG, "鎵归噺涓婁紶澶辫触: $err")

                        // 鍦║I绾跨▼涓樉绀篢oast
                        viewModelScope.launch(Dispatchers.Main) {
                            showToast("Upload failed: check network connection")
                        }
                    }
                )
            } catch (e: Exception) {
                Log.e(TAG, "涓婁紶寮傚父: ${e.message}", e)

                // 鍦║I绾跨▼涓樉绀篢oast
                viewModelScope.launch(Dispatchers.Main) {
                    showToast("Upload failed: check network connection")
                }
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        networkDiscovery?.cleanup()
        stopHeartbeatDetection() // 鍋滄蹇冭烦妫€娴?    }
}
