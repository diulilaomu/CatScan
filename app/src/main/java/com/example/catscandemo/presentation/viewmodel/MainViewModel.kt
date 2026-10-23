package com.example.catscandemo.presentation.viewmodel

import com.example.catscandemo.ui.main.SettingsStorage



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

import androidx.compose.runtime.snapshots.SnapshotStateMap

import androidx.lifecycle.ViewModel

import androidx.lifecycle.viewModelScope

import com.example.catscandemo.data.manager.DataManager

import com.example.catscandemo.data.network.DiscoveredServer

import com.example.catscandemo.data.network.NetworkDiscovery

import com.example.catscandemo.data.network.UpdateChecker

import androidx.compose.ui.text.TextRange

import androidx.compose.ui.text.input.TextFieldValue

import com.example.catscandemo.domain.model.CaptureKernel

import com.example.catscandemo.domain.model.MarkMode

import com.example.catscandemo.domain.model.ScanData

import com.example.catscandemo.domain.model.ScanResult

import com.example.catscandemo.domain.model.SeqConfig

import com.example.catscandemo.domain.model.TemplateMode

import com.example.catscandemo.domain.model.TemplateModel

import com.example.catscandemo.domain.model.WorkMode

import com.example.catscandemo.domain.model.formatSeqValue

import com.example.catscandemo.domain.model.seqTagOf

import com.example.catscandemo.domain.use_case.*

import com.example.catscandemo.ui.main.TextDoc

import com.example.catscandemo.ui.main.TextDocsStorage

import com.google.mlkit.vision.barcode.BarcodeScanning

import com.google.mlkit.vision.common.InputImage

import com.google.mlkit.vision.text.TextRecognition

import com.google.mlkit.vision.text.chinese.ChineseTextRecognizerOptions

import dagger.hilt.android.lifecycle.HiltViewModel

import java.time.LocalDate

import kotlinx.coroutines.Dispatchers

import kotlinx.coroutines.Job

import kotlinx.coroutines.delay

import kotlinx.coroutines.flow.MutableStateFlow

import kotlinx.coroutines.flow.StateFlow

import kotlinx.coroutines.flow.asStateFlow

import kotlinx.coroutines.launch

import javax.inject.Inject

import java.util.concurrent.atomic.AtomicBoolean

import kotlin.math.max


/** 下一次扫码要录入的地点（楼层 + 房间号 + 标签） */
data class ScanPosition(val floor: Int, val room: String, val tag: String)

/**
 * 扫码录入游标：楼层序 → 房间序 → 标签序 三层嵌套，标签推进最快。
 * 预设 2 层 2 房、标签[空调,照明]时依次录入 101空调→101照明→102空调→…→202照明→201空调(循环)。
 */
internal data class ScanCursor(val floorIdx: Int = 0, val roomIdx: Int = 0, val tagIdx: Int = 0)

/** 有可选房间的楼层列表（升序） */
internal fun floorsWithRooms(t: TemplateModel): List<Int> {
    return (1..max(1, t.maxFloor)).filter { floor ->
        t.selectedRooms.any { code -> code.length >= 3 && code.dropLast(2).toIntOrNull() == floor }
    }
}

/** 全量房间号：1~9 补零两位（1层1号 → "101"），与模板编辑器的房间网格一致 */
internal fun buildAllRoomCodes(maxFloor: Int, roomCount: Int): List<String> {
    return (1..maxFloor.coerceAtLeast(1)).flatMap { f ->
        (1..roomCount.coerceIn(1, 999)).map { r -> "$f${if (r in 1..9) "0$r" else "$r"}" }
    }
}

/** 录入序列要轮询的标签：未配置标签时用空标签占位（等于跳过标签维度） */
internal fun scanTagsOf(t: TemplateModel): List<String> {
    return if (t.tags.isEmpty()) listOf("") else t.tags
}

internal fun peekScanPosition(t: TemplateModel, cursor: ScanCursor): ScanPosition? {
    val floors = floorsWithRooms(t)
    if (floors.isEmpty()) return null
    val fi = cursor.floorIdx.coerceIn(0, floors.size - 1)
    val floor = floors[fi]
    val rooms = t.selectedRooms
        .filter { code -> code.length >= 3 && code.dropLast(2).toIntOrNull() == floor }
        .sorted()
    if (rooms.isEmpty()) return null
    val room = rooms[cursor.roomIdx.coerceIn(0, rooms.size - 1)]
    val tags = scanTagsOf(t)
    val tag = tags[cursor.tagIdx.coerceIn(0, tags.size - 1)]
    return ScanPosition(floor, room, tag)
}

internal fun advanceScanCursor(t: TemplateModel, cursor: ScanCursor): ScanCursor {
    val floors = floorsWithRooms(t)
    if (floors.isEmpty()) return ScanCursor()
    val tags = scanTagsOf(t)
    var fi = cursor.floorIdx.coerceIn(0, floors.size - 1)
    val rooms = t.selectedRooms
        .filter { code -> code.length >= 3 && code.dropLast(2).toIntOrNull() == floors[fi] }
        .sorted()
    var ri = cursor.roomIdx.coerceIn(0, rooms.size - 1)
    var ti = cursor.tagIdx + 1
    if (ti >= tags.size) {
        ti = 0
        ri += 1
    }
    if (ri >= rooms.size) {
        ri = 0
        fi += 1
    }
    if (fi >= floors.size) fi = 0
    return ScanCursor(fi, ri, ti)
}

/** 由最近一条扫码记录恢复游标：定位到该记录的位置后再前进一步；
 *  没有任何记录时从头开始（不前进一步，否则新模板首扫会从 102 起） */
internal fun restoredScanCursor(t: TemplateModel): ScanCursor {
    val last = t.scans.firstOrNull() ?: return ScanCursor()
    val floor = Regex("\\d+").find(last.floor)?.value?.toIntOrNull()
        ?: floorsWithRooms(t).firstOrNull()
        ?: return ScanCursor()
    return advanceScanCursor(t, cursorFromSelection(t, floor, last.room, last.tag))
}

internal fun cursorFromSelection(t: TemplateModel, floor: Int, room: String, tag: String): ScanCursor {
    val floors = floorsWithRooms(t)
    if (floors.isEmpty()) return ScanCursor()
    val fi = floors.indexOf(floor).takeIf { it >= 0 } ?: 0
    val rooms = t.selectedRooms
        .filter { code -> code.length >= 3 && code.dropLast(2).toIntOrNull() == floors[fi] }
        .sorted()
    val ri = rooms.indexOf(room).takeIf { it >= 0 } ?: 0
    val tags = scanTagsOf(t)
    val ti = tags.indexOf(tag).takeIf { it >= 0 } ?: 0
    return ScanCursor(fi, ri, ti)
}



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

        /** 无模板伪模板的保留 id：模板仓库不认识它，绝不落进 templates.json */
        const val NO_TEMPLATE_FAKE_ID = "__no_template__"

    }



    // 数据管理中心

    private val dataManager = DataManager(scanUseCases, templateUseCases)
    private val pendingUploadInProgress = AtomicBoolean(false)



    // --- 模板存储 ---  

    val templates get() = dataManager.templates

    var activeTemplateId by mutableStateOf<String?>(null)

    var activeTemplate by mutableStateOf<TemplateModel?>(null)



    // 主界面“识别结果”行：楼层选择

    var scanSelectedFloor by mutableStateOf(1)

    

    // 扫描结果StateFlow，用于UI层观察

    private val _scanResults = MutableStateFlow<List<ScanResult>>(emptyList())

    val scanResults: StateFlow<List<ScanResult>> = _scanResults.asStateFlow()



    // 每个模板的扫码录入游标（楼层→房间→标签，标签推进最快；运行态，不做持久化，
    // 由最近一条扫码记录恢复）。SnapshotStateMap 保证表头"下一个房号"实时重组。
    private val scanCursorByTemplate = SnapshotStateMap<String, ScanCursor>()



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
        loadSavedSettings()

        // 快扫预设兜底：磁贴请求可能早于进程内任何初始化到达（冷启动直拉），
        // 存储一就绪立刻套用，不依赖 UI 层副作用时序
        applyQuickScanIfPending()

    }

    private fun loadSavedSettings() {
        val context = appContext ?: return
        val settings = SettingsStorage.load(context)
        _clipboardEnabledText.value = settings.clipboardEnabledText
        _clipboardEnabledTemplate.value = settings.clipboardEnabledTemplate
        duplicateScanEnabled = settings.duplicateScanEnabled
        showBarcodeOverlay = settings.showBarcodeOverlay
        channel1ScanFrameInterval = settings.channel1ScanFrameInterval
        channel2MinAreaScore = settings.channel2MinAreaScore
        channel2MinAspectScore = settings.channel2MinAspectScore
        channel2MinSolidityScore = settings.channel2MinSolidityScore
        channel2MinGradScore = settings.channel2MinGradScore
        workMode = runCatching { WorkMode.valueOf(settings.workMode) }
            .getOrDefault(WorkMode.TEXT)
        val ratio = settings.splitRatio.coerceIn(0.2f, 0.75f)
        expandedSplitRatio = ratio
        splitRatio = ratio
        textAutoNewline = settings.textAutoNewline
        cameraSleepEnabled = settings.cameraSleepEnabled
        themeMode = settings.themeMode
        exportTreeUri = settings.exportTreeUri
        cameraEnabled = settings.cameraEnabled
        captureKernel = runCatching { CaptureKernel.valueOf(settings.captureKernel) }
            .getOrDefault(CaptureKernel.QR)
        ocrRegex = settings.ocrRegex
        ocrTemplateId = settings.ocrTemplateId
        ocrTemplateParams = settings.ocrTemplateParams
        scheduleCameraSleep()
    }

    // --- 每日更新检查（GitHub Release）：有新版本时仅设置抽屉署名位变为更新提示 ---

    /** 非空 = GitHub 上有比本地新的版本 tag，设置抽屉底部署名行替换为更新提示 */
    var updateHintVersion by mutableStateOf<String?>(null)

    private var updateCheckStarted = false

    /**
     * 启动时调用：先用最近一次检查结果恢复提示，再按"每天最多一次"拉取 GitHub
     * 最新 release 比对。请求失败静默（不记当天），下次启动自然重试。
     */
    fun checkForAppUpdate(context: Context) {
        if (updateCheckStarted) return
        updateCheckStarted = true
        val appCtx = context.applicationContext
        if (appContext == null) {
            appContext = appCtx
        }
        val prefs = appCtx.getSharedPreferences("update_check", Context.MODE_PRIVATE)
        val currentVersion = currentVersionName(appCtx)
        fun refreshHint(latest: String?) {
            updateHintVersion = latest?.takeIf { UpdateChecker.isNewerVersion(it, currentVersion) }
        }
        // 恢复上次结果：装上新版后本地版本已追上，比对后提示自动消失
        refreshHint(prefs.getString("latest_version", null))
        val today = LocalDate.now().toEpochDay()
        if (prefs.getLong("last_check_day", -1L) == today) return
        viewModelScope.launch(Dispatchers.IO) {
            val latest = UpdateChecker.fetchLatestReleaseTag() ?: return@launch
            prefs.edit()
                .putLong("last_check_day", today)
                .putString("latest_version", latest)
                .apply()
            refreshHint(latest)
        }
    }

    private fun currentVersionName(context: Context): String {
        return runCatching {
            @Suppress("DEPRECATION")
            context.packageManager.getPackageInfo(context.packageName, 0).versionName
        }.getOrNull() ?: ""
    }

    /** 集中持久化设置，供 ViewModel 内部各 setter 与设置界面调用 */
    fun persistSettings() {
        val context = appContext ?: return
        viewModelScope.launch(Dispatchers.IO) {
            SettingsStorage.save(
                context,
                SettingsStorage.Settings(
                clipboardEnabledText = _clipboardEnabledText.value,
                clipboardEnabledTemplate = _clipboardEnabledTemplate.value,
                duplicateScanEnabled = duplicateScanEnabled,
                showBarcodeOverlay = showBarcodeOverlay,
                channel1ScanFrameInterval = channel1ScanFrameInterval,
                channel2MinAreaScore = channel2MinAreaScore,
                channel2MinAspectScore = channel2MinAspectScore,
                channel2MinSolidityScore = channel2MinSolidityScore,
                channel2MinGradScore = channel2MinGradScore,
                    workMode = workMode.name,
                    splitRatio = expandedSplitRatio,
                    textAutoNewline = textAutoNewline,
                    cameraSleepEnabled = cameraSleepEnabled,
                    themeMode = themeMode,
                    exportTreeUri = exportTreeUri,
                    cameraEnabled = cameraEnabled,
                    captureKernel = captureKernel.name,
                    ocrRegex = ocrRegex,
                    ocrTemplateId = ocrTemplateId,
                    ocrTemplateParams = ocrTemplateParams
                )
            )
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

        activeTemplate?.let { template ->
            clampSelectedFloor(0, template.maxFloor)
            restoreScanCursor(template)
        }

        

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

    // ===== 无模板数据（scan_history_no_template.json）=====
    // 模板管理导出与文档"从模板导入"都通过这个伪模板访问无模板桶

    var noTemplateTemplate by mutableStateOf<TemplateModel?>(null)
        private set

    /** 从磁盘读无模板桶并刷新伪模板（打开模板管理/导入对话框时调用，IO 线程） */
    fun refreshNoTemplateTemplate() {

        val context = appContext ?: return

        viewModelScope.launch(Dispatchers.IO) {

            // 无模板正激活时内存列表就是该桶的实时真身（落盘是异步的，读盘可能漏最后一扫）；
            // 否则仓库指向别的模板，只能读 no_template 历史文件。
            // 读盘结果同样滤掉带 templateId 的污染记录（与仓库加载规则一致）

            val results = if (dataManager.activeTemplateId == null) {

                dataManager.scanUseCases.getAllScans.invoke()

            } else {

                runCatching {

                    com.example.catscandemo.ui.main.ScanHistoryStorage.load(context, null).items

                }.getOrDefault(emptyList())

                    .filter { it.scanData.templateId.isBlank() }

            }

            val template = TemplateModel(

                id = NO_TEMPLATE_FAKE_ID,

                name = "无模板",

                markMode = MarkMode.ROOM,

                scans = results.sortedByDescending { it.scanData.timestamp }.map { it.scanData }

            )

            kotlinx.coroutines.withContext(Dispatchers.Main) {

                noTemplateTemplate = template.takeIf { it.scans.isNotEmpty() }

            }

        }

    }

    /** 清空无模板桶并刷新伪模板 */

    fun clearNoTemplateScans() {

        dataManager.scanUseCases.clearNoTemplateScans()

        noTemplateTemplate = null

        // 若当前正处在无模板模式，同步刷新主列表

        if (activeTemplateId == null) {

            getAllScans()

        }

    }



    fun setActiveTemplate(id: String) {

        // 使用数据管理中心设置激活模板

        dataManager.setActiveTemplate(id)

        

        // 同步ViewModel的状态

        activeTemplateId = dataManager.activeTemplateId

        activeTemplate = dataManager.activeTemplate

        

        val t = activeTemplate

        if (t != null) {
            clampSelectedFloor(0, t.maxFloor)
            restoreScanCursor(t)

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



    fun addTemplate(
        name: String,
        markMode: MarkMode = MarkMode.ROOM,
        seqConfig: SeqConfig? = null,
        maxFloor: Int? = null,
        roomCountPerFloor: Int? = null,
        tags: List<String> = emptyList(),
        tagColors: Map<String, Int> = emptyMap()
    ) {

        // 使用数据管理中心添加模板（离散/线性已合并，内部一律 LINEAR）
        val template = dataManager.addTemplate(name, TemplateMode.LINEAR)

        // 新建时直接带入参数：序号模板=录入配置；房间号模板=楼层数/每层房间数（房间默认全选）；
        // 标签两种模式都带入（序号模式也按 标签→序号 轮询录入）
        var configured = template
        if (markMode == MarkMode.SEQ) {
            val cfg = seqConfig ?: SeqConfig()
            configured = configured.copy(
                markMode = MarkMode.SEQ,
                seq = cfg,
                nextSeq = cfg.start,
                seqTagCursor = 0
            )
        } else {
            val f = (maxFloor ?: template.maxFloor).coerceAtLeast(1)
            val r = (roomCountPerFloor ?: template.roomCountPerFloor).coerceIn(1, 999)
            configured = configured.copy(
                maxFloor = f,
                roomCountPerFloor = r,
                selectedRooms = buildAllRoomCodes(f, r)
            )
        }
        if (tags.isNotEmpty()) {
            // 新建时带入的标签配色（与模板编辑器同一调色板；未指定的按序号循环配色）
            configured = configured.copy(
                tags = tags.distinct(),
                tagColors = tagColors.filterKeys { it in tags.distinct() }
            )
        }
        if (configured != template) {
            dataManager.updateTemplate(configured)
        }



        // 同步ViewModel的状态

        activeTemplateId = dataManager.activeTemplateId

        activeTemplate = dataManager.activeTemplate

        

        // ✅ 新建模板默认展示所有楼层（避免上一模板的楼层筛选残留）

        scanSelectedFloor = 0



        val t = activeTemplate

        if (t != null) {

            clampSelectedFloor(scanSelectedFloor, t.maxFloor)
            restoreScanCursor(t)

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

            scanCursorByTemplate.remove(id)

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

                    batchData.forEach { scanData ->
                        networkUseCases.uploadScanData(
                            scanData = scanData,
                            serverUrl = serverUrl,
                            action = "delete",
                            onSuccess = {
                                Log.d(TAG, "同步删除模板数据成功: ${scanData.id}")
                            },
                            onError = { error ->
                                Log.e(TAG, "同步删除模板数据失败: ${scanData.id}, $error")
                            }
                        )
                    }

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
            restoreScanCursor(updated)

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



    fun clearTemplateFloorScans(
        id: String,
        floor: Int,
        showToast: (String) -> Unit
    ) {
        val template = templates.firstOrNull { it.id == id }
        if (template == null) {
            showToast("模板不存在")
            return
        }

        // floor <= 0：清空整个模板（楼层筛选为"所有楼层"时的清空语义）
        if (floor <= 0) {
            val deletedScans = template.scans.map {
                it.copy(templateId = template.id, templateName = template.name)
            }
            if (deletedScans.isEmpty()) {
                showToast("模板暂无扫描数据")
                return
            }
            dataManager.clearTemplateScans(id)
            activeTemplateId = dataManager.activeTemplateId
            activeTemplate = dataManager.activeTemplate
            activeTemplate?.let { restoreScanCursor(it) }
            getAllScans()

            if (uploadEnabled && serverUrl.isNotEmpty()) {
                viewModelScope.launch(Dispatchers.IO) {
                    deletedScans.forEach { scanData ->
                        networkUseCases.uploadScanData(
                            scanData = scanData,
                            serverUrl = serverUrl,
                            action = "delete",
                            onSuccess = {
                                Log.d(TAG, "同步清空模板数据成功: ${scanData.id}")
                            },
                            onError = { error ->
                                Log.e(TAG, "同步清空模板数据失败: ${scanData.id}, $error")
                            }
                        )
                    }
                }
            }
            showToast("已清空模板全部 ${deletedScans.size} 条扫描数据")
            return
        }

        val validFloor = floor.coerceIn(1, template.maxFloor.coerceAtLeast(1))
        val deletedScans = dataManager.clearTemplateFloorScans(id, validFloor)

        activeTemplateId = dataManager.activeTemplateId
        activeTemplate = dataManager.activeTemplate
        if (activeTemplateId == id) {
            activeTemplate?.let { restoreScanCursor(it) }
        }
        getAllScans()

        if (deletedScans.isEmpty()) {
            showToast("${validFloor}层暂无扫描数据")
            return
        }

        if (uploadEnabled && serverUrl.isNotEmpty()) {
            viewModelScope.launch(Dispatchers.IO) {
                deletedScans.forEach { scanData ->
                    networkUseCases.uploadScanData(
                        scanData = scanData,
                        serverUrl = serverUrl,
                        action = "delete",
                        onSuccess = {
                            Log.d(TAG, "同步清空楼层数据成功: ${scanData.id}")
                        },
                        onError = { error ->
                            Log.e(TAG, "同步清空楼层数据失败: ${scanData.id}, $error")
                        }
                    )
                }
            }
        }

        showToast("已清空${validFloor}层 ${deletedScans.size} 条扫描数据")
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

    

    // 自动复制（扫到内容自动进剪贴板）：文本/模板模式各自独立记忆
    // 文本模式默认关（避免大段文档反复覆盖剪贴板），模板模式默认开
    private val _clipboardEnabledText = mutableStateOf(false)

    private val _clipboardEnabledTemplate = mutableStateOf(true)

    val clipboardEnabled: Boolean
        get() = if (workMode == WorkMode.TEXT) {
            _clipboardEnabledText.value
        } else {
            _clipboardEnabledTemplate.value
        }

    fun onClipboardEnabledChange(enabled: Boolean) {
        if (workMode == WorkMode.TEXT) {
            _clipboardEnabledText.value = enabled
        } else {
            _clipboardEnabledTemplate.value = enabled
        }
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

        }



    // 是否显示条码检测框

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



    // ===== 工作模式 =====

    var workMode by mutableStateOf(WorkMode.TEMPLATE)
        private set

    fun onWorkModeChange(mode: WorkMode) {
        if (workMode == mode) return
        workMode = mode
        persistSettings()
        scheduleCameraSleep()
    }

    // ===== 采集内核（二维码 / 文字识别），与工作模式正交 =====

    var captureKernel by mutableStateOf(CaptureKernel.QR)
        private set

    fun onCaptureKernelChange(kernel: CaptureKernel) {
        if (captureKernel == kernel) return
        captureKernel = kernel
        persistSettings()
    }

    /** 文字识别正则（空 = 任意整行直接录入），由正则弹窗"确定"写入 */
    var ocrRegex by mutableStateOf("")
        private set

    /** 正则弹窗回填用：上次选择的内置模板 id 与参数 JSON */
    var ocrTemplateId by mutableStateOf("")
        private set

    var ocrTemplateParams by mutableStateOf("")
        private set

    fun setOcrRegex(regex: String, templateId: String, templateParams: String) {
        ocrRegex = regex.trim()
        ocrTemplateId = templateId
        ocrTemplateParams = templateParams
        persistSettings()
    }

    // 相册文字识别结果弹窗：识别出的文字行 + 命中状态（命中与否由弹窗按 ocrRegex 现算）
    var showOcrImageDialog by mutableStateOf(false)

    var ocrImageLines by mutableStateOf<List<String>>(emptyList())
        private set

    // 扫描区/数据区分割比例（扫描区高度占比），拖动拉杆调节
    var splitRatio by mutableStateOf(0.45f)
        private set

    // 摄像头开启时用户习惯的展开高度；关闭眼睛自动收起后，重新打开恢复到这里
    var expandedSplitRatio by mutableStateOf(0.45f)
        private set

    fun onSplitRatioChange(ratio: Float) {
        // 摄像头开启时保持最小可见高度；关闭后允许拉到顶完全盖住扫描区
        val min = if (cameraEnabled) 0.2f else 0f
        val clamped = ratio.coerceIn(min, 0.75f)
        if (splitRatio == clamped) return
        splitRatio = clamped
        if (cameraEnabled) {
            expandedSplitRatio = clamped
            schedulePersistSettings()
        }
    }

    /** 收起/展开相机动画逐帧设置：不更新记忆高度、不持久化 */
    fun setSplitRatioAnimated(ratio: Float) {
        splitRatio = ratio.coerceIn(0f, 0.75f)
    }

    private var persistSettingsJob: Job? = null

    private fun schedulePersistSettings() {
        persistSettingsJob?.cancel()
        persistSettingsJob = viewModelScope.launch(Dispatchers.IO) {
            delay(400)
            persistSettings()
        }
    }

    // ===== 快扫入口（控制中心磁贴）=====

    /** 磁贴/外部入口已请求快扫预设，等存储就绪后由 applyQuickScanIfPending 消费 */
    private var quickScanPending = false

    /** 磁贴点击时调用：先记请求，无论进程是否已启动都会在入口就绪后套用预设 */
    fun requestQuickScan() {
        quickScanPending = true
    }

    /**
     * 应用快扫预设：切到 TEMPLATE 工作模式 + 无模板 + 自动复制开 + 摄像头开启 +
     * 采集内核切回二维码（磁贴入口就是扫码，OCR 内核下次用正则弹窗切回即可）。
     * 存储未就绪（冷启动极早调用）时保留请求直接返回，等 MainScreen 的
     * LaunchedEffect(Unit) 初始化完再调；就绪后调用则幂等消费。
     * 必须在 initTemplateStore 之后执行 clearActiveTemplate，
     * 否则空仓库会把 templates.json 覆盖成空。
     * workMode 切 TEMPLATE 而不是留在 TEXT：模板模式的自动复制默认开、
     * 无模板桶的录入路径与模板列表互不干扰，且镜头休眠只作用于文本模式，
     * 扫码场景不该被一分钟休眠掐断。
     */
    fun applyQuickScanIfPending() {
        if (!quickScanPending) return
        if (!storeReady || !settingsReady) return
        quickScanPending = false
        if (workMode != WorkMode.TEMPLATE) {
            onWorkModeChange(WorkMode.TEMPLATE)
        }
        if (captureKernel != CaptureKernel.QR) {
            onCaptureKernelChange(CaptureKernel.QR)
        }
        if (activeTemplateId != null) {
            clearActiveTemplate()
        }
        if (!_clipboardEnabledTemplate.value) {
            onClipboardEnabledChange(true)
        }
        if (!cameraEnabled) {
            onToggleCameraEnabled()
        }
    }


    // 摄像头开关（扫码图标）：关闭后不绑定相机以省电；状态持久化，下次启动保持
    var cameraEnabled by mutableStateOf(true)
        private set

    /**
     * Big Bang 选词弹窗期间的相机临时抑制位（不持久化）：
     * 相册识别结果弹出时置 true 暂停预览省电，弹窗关闭恢复；
     * 与 cameraEnabled 独立，不污染用户保存的开关状态。
     */
    var ocrBangSuppressCamera by mutableStateOf(false)

    fun onToggleCameraEnabled() {
        // 关闭前记住当前展开高度，重新打开时恢复
        if (cameraEnabled && splitRatio > 0.05f) {
            expandedSplitRatio = splitRatio
        }
        cameraEnabled = !cameraEnabled
        if (!cameraEnabled) {
            isFlashOn = false
            camera = null
        }
        scheduleCameraSleep()
        persistSettings()
    }

    // 禁用输入法开关（文本模式表头，运行态、默认不禁用）：
    // 开启后点击编辑区/拖动光标均不弹出软键盘，便于纯扫码录入场景
    var keyboardDisabled by mutableStateOf(false)

    /** 从"摄像头已关闭"占位处点击重新开启：以当前 splitRatio 为准——
     *  已≥扫描区最小比例(0.2f)则保持当前高度不动，不足才撑开至 0.2f。
     *  （不再使用关闭前记忆的 expandedSplitRatio，避免"够高却被拉回旧高度"） */
    fun reopenCameraWithMinHeight() {
        if (cameraEnabled) return
        val current = splitRatio
        onToggleCameraEnabled()
        expandedSplitRatio = if (current >= 0.2f) current else 0.2f
        persistSettings()
    }

    // ===== 镜头休眠（仅文本模式）：一分钟没扫到内容自动关闭摄像头 =====
    var cameraSleepEnabled by mutableStateOf(true)
        private set

    fun onCameraSleepEnabledChange(enabled: Boolean) {
        cameraSleepEnabled = enabled
        persistSettings()
        scheduleCameraSleep()
    }

    // ===== 应用设置：主题 / 导出位置 =====

    /** 主题模式：SYSTEM（系统）/ LIGHT（日间）/ DARK（夜间） */
    var themeMode by mutableStateOf("SYSTEM")
        private set

    fun onThemeModeChange(mode: String) {
        themeMode = mode
        persistSettings()
    }

    /** 自定义导出目录（SAF 树 URI 字符串），空 = 默认系统下载目录/CatScan */
    var exportTreeUri by mutableStateOf("")
        private set

    fun onExportTreeUriChange(uri: String) {
        exportTreeUri = uri
        persistSettings()
    }

    private var cameraSleepJob: Job? = null

    private fun scheduleCameraSleep() {
        cameraSleepJob?.cancel()
        if (!cameraSleepEnabled || workMode != WorkMode.TEXT || !cameraEnabled) return
        cameraSleepJob = viewModelScope.launch {
            delay(60_000L)
            if (cameraSleepEnabled && workMode == WorkMode.TEXT && cameraEnabled) {
                onToggleCameraEnabled()
            }
        }
    }

    // ===== 文本模式 =====

    // 自动换行：开启后扫码插入自动处理换行/光标移动
    var textAutoNewline by mutableStateOf(true)
        private set

    fun onTextAutoNewlineChange(enabled: Boolean) {
        textAutoNewline = enabled
        persistSettings()
    }

    val textDocs = mutableStateListOf<TextDoc>()

    var activeTextDocId by mutableStateOf<String?>(null)
        private set

    var showTextDocsManager by mutableStateOf(false)

    // 当前文档编辑器内容（含光标选区）
    var textFieldValue by mutableStateOf(TextFieldValue(""))
        private set

    var canUndoText by mutableStateOf(false)
        private set

    private val undoStacks = mutableMapOf<String, ArrayDeque<TextFieldValue>>()

    private var lastUserEditAtMs = 0L

    private var textDocsReady = false

    private var persistDocsJob: Job? = null

    fun initTextDocsStore(context: Context) {
        if (textDocsReady) return
        textDocsReady = true
        if (appContext == null) {
            appContext = context.applicationContext
        }
        val loaded = TextDocsStorage.load(context.applicationContext)
        textDocs.clear()
        textDocs.addAll(loaded.docs)
        if (textDocs.isEmpty()) {
            textDocs.add(TextDoc(name = "文档1"))
        }
        activeTextDocId = loaded.activeId
            ?.takeIf { id -> textDocs.any { it.id == id } }
            ?: textDocs.first().id
        loadActiveDocIntoEditor()
    }

    private fun activeDocIndex(): Int = textDocs.indexOfFirst { it.id == activeTextDocId }

    private fun loadActiveDocIntoEditor() {
        val content = textDocs.getOrNull(activeDocIndex())?.content ?: ""
        textFieldValue = TextFieldValue(content, TextRange(content.length))
        lastUserEditAtMs = 0L
        refreshCanUndo()
    }

    private fun refreshCanUndo() {
        canUndoText = undoStacks[activeTextDocId]?.isNotEmpty() == true
    }

    private fun pushUndoSnapshot() {
        val id = activeTextDocId ?: return
        val stack = undoStacks.getOrPut(id) { ArrayDeque() }
        stack.addLast(textFieldValue)
        if (stack.size > 50) stack.removeFirst()
        refreshCanUndo()
    }

    private fun syncEditorContentToDoc() {
        val idx = activeDocIndex()
        if (idx >= 0 && textDocs[idx].content != textFieldValue.text) {
            textDocs[idx] = textDocs[idx].copy(content = textFieldValue.text)
        }
        schedulePersistTextDocs()
    }

    private fun schedulePersistTextDocs() {
        val context = appContext ?: return
        persistDocsJob?.cancel()
        persistDocsJob = viewModelScope.launch(Dispatchers.IO) {
            delay(500)
            TextDocsStorage.save(context, textDocs.toList(), activeTextDocId)
        }
    }

    /** 用户在编辑器中的手动输入：快速连续输入合并为一步撤回 */
    fun onTextEditorChange(newValue: TextFieldValue) {
        val now = System.currentTimeMillis()
        if (now - lastUserEditAtMs > 800L) pushUndoSnapshot()
        lastUserEditAtMs = now
        textFieldValue = newValue
        syncEditorContentToDoc()
    }

    /** 查找定位：仅移动选区/光标，不进撤回栈 */
    fun selectTextRange(start: Int, end: Int) {
        val text = textFieldValue.text
        val s = start.coerceIn(0, text.length)
        val e = end.coerceIn(0, text.length)
        textFieldValue = textFieldValue.copy(
            selection = TextRange(minOf(s, e), maxOf(s, e))
        )
    }

    /** 扫码插入到光标处；自动换行开启时按规则补换行/移动光标 */
    fun insertScannedText(code: String) {
        scheduleCameraSleep()
        pushUndoSnapshot()
        lastUserEditAtMs = 0L
        val current = textFieldValue
        val text = current.text
        val sel = current.selection
        val start = sel.min.coerceIn(0, text.length)
        val end = sel.max.coerceIn(0, text.length)
        var replaced = text.replaceRange(start, end, code)
        var cursor = start + code.length
        if (textAutoNewline) {
            if (end == text.length) {
                // 插入位置在文档末尾：自动回车起下一行
                replaced += "\n"
                cursor = replaced.length
            } else {
                // 非末尾：光标移动到下一行的末尾
                // 先找当前行结尾的换行符，再找下一行结尾的换行符
                val curLineEnd = replaced.indexOf('\n', cursor)
                cursor = if (curLineEnd < 0) {
                    replaced.length
                } else {
                    val nextLineEnd = replaced.indexOf('\n', curLineEnd + 1)
                    if (nextLineEnd >= 0) nextLineEnd else replaced.length
                }
            }
        }
        textFieldValue = TextFieldValue(replaced, TextRange(cursor.coerceIn(0, replaced.length)))
        syncEditorContentToDoc()
    }

    fun undoText() {
        val id = activeTextDocId ?: return
        val stack = undoStacks[id] ?: return
        if (stack.isEmpty()) return
        textFieldValue = stack.removeLast()
        lastUserEditAtMs = 0L
        refreshCanUndo()
        syncEditorContentToDoc()
    }

    fun setActiveTextDoc(id: String) {
        if (id == activeTextDocId || textDocs.none { it.id == id }) return
        syncEditorContentToDoc()
        activeTextDocId = id
        loadActiveDocIntoEditor()
        schedulePersistTextDocs()
    }

    fun addTextDoc(): TextDoc {
        syncEditorContentToDoc()
        val names = textDocs.map { it.name }.toSet()
        var n = 1
        while ("文档$n" in names) n++
        val doc = TextDoc(name = "文档$n")
        textDocs.add(doc)
        activeTextDocId = doc.id
        loadActiveDocIntoEditor()
        schedulePersistTextDocs()
        return doc
    }

    /** 新建文档并写入预设内容（房间号等），创建后自动切换到新文档 */
    fun createTextDocWithContent(name: String, content: String) {
        syncEditorContentToDoc()
        val finalName = name.trim().ifBlank {
            val names = textDocs.map { it.name }.toSet()
            var n = 1
            while ("文档$n" in names) n++
            "文档$n"
        }
        val doc = TextDoc(name = finalName, content = content)
        textDocs.add(doc)
        activeTextDocId = doc.id
        loadActiveDocIntoEditor()
        schedulePersistTextDocs()
    }

    /** 在指定文档末尾新建一行追加内容（不替换已有内容；当前文档走编辑器支持撤回） */
    fun appendBlockToTextDoc(id: String, block: String) {
        if (block.isEmpty()) return
        val idx = textDocs.indexOfFirst { it.id == id }
        if (idx < 0) return
        val base = if (id == activeTextDocId) textFieldValue.text else textDocs[idx].content
        val prefix = if (base.isEmpty() || base.endsWith("\n")) "" else "\n"
        val newContent = base + prefix + block
        if (id == activeTextDocId) {
            pushUndoSnapshot()
            lastUserEditAtMs = 0L
            textFieldValue = TextFieldValue(newContent, TextRange(newContent.length))
            syncEditorContentToDoc()
        } else {
            textDocs[idx] = textDocs[idx].copy(content = newContent)
            schedulePersistTextDocs()
        }
    }

    fun renameTextDoc(id: String, newName: String) {
        val idx = textDocs.indexOfFirst { it.id == id }
        if (idx < 0) return
        val trimmed = newName.trim()
        if (trimmed.isEmpty() || trimmed == textDocs[idx].name) return
        textDocs[idx] = textDocs[idx].copy(name = trimmed)
        schedulePersistTextDocs()
    }

    fun deleteTextDoc(id: String) {
        val idx = textDocs.indexOfFirst { it.id == id }
        if (idx < 0) return
        textDocs.removeAt(idx)
        undoStacks.remove(id)
        if (textDocs.isEmpty()) {
            textDocs.add(TextDoc(name = "文档1"))
        }
        if (activeTextDocId == id) {
            activeTextDocId = textDocs.first().id
            loadActiveDocIntoEditor()
        } else {
            refreshCanUndo()
        }
        schedulePersistTextDocs()
    }



    // 当前一次扫码写入的字段（每次扫码前会被模板刷新）

    var currentOperator by mutableStateOf("")

    var currentCampus by mutableStateOf("")

    var currentBuilding by mutableStateOf("")

    var currentFloor by mutableStateOf("1层")

    var currentRoom by mutableStateOf("")

    var currentTag by mutableStateOf("")

    /** 表头徽标：下一个录入标记（序号模式=前缀+序号+后缀[+标签]，房间号模式=房间+标签） */
    val nextScanMark: String?
        get() {
            val t = activeTemplate ?: return null
            return if (t.markMode == MarkMode.SEQ) {
                // 自动补零：位数按"录入这一条后的总条数"推断
                val mark = formatSeqValue(t.seq, t.nextSeq, totalForAuto = t.scans.size + 1)
                val tag = seqTagOf(t)
                if (tag.isNotBlank()) "$mark·$tag" else mark
            } else {
                nextScanPosition?.let { p -> p.room + if (p.tag.isNotBlank()) "·${p.tag}" else "" }
            }
        }

    val isSeqMarkMode: Boolean
        get() = activeTemplate?.markMode == MarkMode.SEQ

    /**
     * 序号模式：修改下次录入的序号值与标签。
     * tagCursor：null=不动；-1=后续固定无标签；>=0=下次用该下标的标签（此后继续轮询）
     */
    fun setNextSeq(value: Int, tagCursor: Int? = null) {
        val t = activeTemplate ?: return
        if (t.markMode != MarkMode.SEQ) return
        val newCursor = when {
            tagCursor == null -> t.seqTagCursor
            tagCursor < 0 -> -1
            else -> tagCursor % max(1, t.tags.size)
        }
        dataManager.updateTemplate(t.copy(nextSeq = value, seqTagCursor = newCursor))
        activeTemplate = dataManager.activeTemplate
    }

    /** 添加模板标签（立即生效并持久化） */
    fun addTemplateTag(name: String) {
        val t = activeTemplate ?: return
        val tag = name.trim()
        if (tag.isBlank() || tag in t.tags) return
        dataManager.updateTemplate(t.copy(tags = t.tags + tag))
        activeTemplate = dataManager.activeTemplate
    }

    /** 删除模板标签（只删配置，已有扫码记录保持原标记） */
    fun removeTemplateTag(name: String) {
        val t = activeTemplate ?: return
        val removedIdx = t.tags.indexOf(name)
        if (removedIdx < 0) return
        val colors = t.tagColors.toMutableMap()
        colors.remove(name)
        // 序号标签游标下标随列表缩短对齐，尽量仍指向原来的下一个标签
        var cursor = t.seqTagCursor
        if (cursor >= 0) {
            val newSize = t.tags.size - 1
            cursor = when {
                removedIdx < cursor -> cursor - 1
                removedIdx == cursor -> cursor % max(1, newSize)
                else -> cursor
            }
        }
        dataManager.updateTemplate(
            t.copy(tags = t.tags.filterNot { it == name }, tagColors = colors, seqTagCursor = cursor)
        )
        activeTemplate = dataManager.activeTemplate
    }

    /** 改标签名/颜色：改名会同步到已有扫码记录（含 history 落盘），颜色存调色板色号 */
    fun updateTemplateTag(oldName: String, newName: String, colorIndex: Int) {
        val t = activeTemplate ?: return
        val name = newName.trim()
        if (oldName !in t.tags || name.isBlank() || (name != oldName && name in t.tags)) return
        val newTags = t.tags.map { if (it == oldName) name else it }
        val newColors = t.tagColors.toMutableMap()
        newColors.remove(oldName)
        if (colorIndex >= 0) newColors[name] = colorIndex
        val newScans = if (name == oldName) t.scans else t.scans.map {
            if (it.tag == oldName) it.copy(tag = name) else it
        }
        val newLastTag = if (t.lastSelectedTag == oldName) name else t.lastSelectedTag
        dataManager.updateTemplate(
            t.copy(
                tags = newTags,
                tagColors = newColors,
                scans = newScans,
                lastSelectedTag = newLastTag
            ),
            syncScanResults = true
        )
        activeTemplate = dataManager.activeTemplate
        getAllScans()
    }

    /**
     * 自动补零重算：位数=当前总条数的位数（10条→2位、100条→3位），
     * 重写该模板全部序号记录的标记(room)。无法解析出数字的标记（用户手改过）跳过。
     */
    private fun rewriteAutoPaddedSeqMarks(templateId: String) {
        val t = dataManager.templates.firstOrNull { it.id == templateId } ?: return
        if (t.markMode != MarkMode.SEQ || !t.seq.autoPad || t.seq.chineseUpper) return
        val width = maxOf(1, t.scans.size).toString().length
        val fixed = t.seq.copy(padWidth = width, autoPad = false)
        var changed = false
        val newScans = t.scans.map { s ->
            val core = s.room.removePrefix(t.seq.prefix).removeSuffix(t.seq.suffix)
            val n = core.toIntOrNull() ?: return@map s
            val newRoom = formatSeqValue(fixed, n)
            if (newRoom != s.room) {
                changed = true
                s.copy(room = newRoom)
            } else {
                s
            }
        }
        if (changed) {
            dataManager.updateTemplate(t.copy(scans = newScans), syncScanResults = true)
            activeTemplate = dataManager.activeTemplate
            getAllScans()
        }
    }



    // =============== “按楼层顺序读模板房间号” ===============



    fun selectScanFloor(floor: Int) {

        val maxF = activeTemplate?.maxFloor ?: 1

        clampSelectedFloor(floor, maxF)

    }



    private fun clampSelectedFloor(floor: Int, maxFloor: Int) {

        val maxF = max(1, maxFloor)

        // 0 = 所有楼层（仅影响列表筛选，不影响扫码录入楼层）
        scanSelectedFloor = floor.coerceIn(0, maxF)

    }



    /** 表头"下一个房号"徽标：当前模板的下一录入位置（楼层+房间+标签） */
    val nextScanPosition: ScanPosition?
        get() = activeTemplate?.let { t ->
            peekScanPosition(t, scanCursorByTemplate[t.id] ?: ScanCursor())
        }

    /** 从地点选择器设置游标（表头房号徽标点击后调用），下次扫码从该房间开始录入 */
    fun setScanCursorFromSelection(floor: Int, room: String, tag: String) {
        val t = activeTemplate ?: return
        if (floorsWithRooms(t).isEmpty()) return
        scanCursorByTemplate[t.id] = cursorFromSelection(t, floor, room, tag)
    }

    /** 由模板当前扫码记录恢复游标（初始化/切换模板/增删数据后调用） */
    private fun restoreScanCursor(template: TemplateModel) {
        if (floorsWithRooms(template).isEmpty()) {
            scanCursorByTemplate.remove(template.id)
            return
        }
        scanCursorByTemplate[template.id] = restoredScanCursor(template)
    }



    /** 扫码前按游标预填字段（不推进游标），返回 模板+下一录入位置；无房间配置时位置为 null */
    private fun applyTemplateForNextScan(): Pair<TemplateModel, ScanPosition>? {
        val t = activeTemplate ?: run {
            // 无模板：清掉上个模板游标残留的字段，否则扫码记录会带上旧房号/标签
            currentFloor = ""
            currentRoom = ""
            currentTag = ""
            return null
        }
        val pos = peekScanPosition(t, scanCursorByTemplate[t.id] ?: ScanCursor())

        currentOperator = t.operator
        currentCampus = t.campus
        currentBuilding = t.building

        if (pos != null) {
            currentFloor = "${pos.floor}层"
            currentRoom = pos.room
            currentTag = pos.tag
        } else {
            // 楼层未配置房间号：不阻断、不弹提示，扫到的内容以空房间直接进扫码历史列表
            val f = if (scanSelectedFloor in 1..max(1, t.maxFloor)) scanSelectedFloor else 1
            currentFloor = "${f}层"
            currentRoom = ""
            currentTag = ""
        }

        return pos?.let { t to it }
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

    /**
     * Big Bang 涂抹选词界面的确定入口：按缓存顺序逐条复用扫码录入链路
     * （模板模式=游标推进/查重/上传 PC/列表刷新；文本模式=逐条插入文档光标处）。
     */
    fun onOcrBangConfirm(
        texts: List<String>,
        copyToClipboard: (String) -> Unit,
        showToast: (String) -> Unit
    ) {
        var accepted = 0
        texts.forEach { text ->
            val before = scanResults.value.size
            onBarcodeScanned(text, copyToClipboard, showToast)
            if (scanResults.value.size > before || workMode == WorkMode.TEXT) accepted++
        }
        showToast(if (accepted == texts.size) "已录入 $accepted 条" else "已录入 $accepted/${texts.size} 条（重复项已跳过）")
    }

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

                workMode == WorkMode.TEXT -> {

                    // 文本模式：扫码内容直接插入文本文档光标处，不入识别结果/不上传

                    insertScannedText(code)

                    if (clipboardEnabled) {

                        copyToClipboard(code)

                        showToast("已复制: $code")

                    }

                }

                else -> {

                    // ===== 序号模式：序号标记写入 room 字段，独立递增（正/倒序），与扫码顺序无关；
                    // 配置了标签时按 标签→序号 轮询：01甲→01乙→02甲…，标签轮完一圈序号才推进一位 =====
                    val seqTemplate = activeTemplate
                    if (seqTemplate?.markMode == MarkMode.SEQ) {
                        val value = seqTemplate.nextSeq
                        val tagNow = seqTagOf(seqTemplate)
                        currentOperator = seqTemplate.operator
                        currentCampus = seqTemplate.campus
                        currentBuilding = seqTemplate.building
                        currentFloor = ""
                        currentRoom = formatSeqValue(
                            seqTemplate.seq, value,
                            totalForAuto = seqTemplate.scans.size + 1
                        )
                        currentTag = tagNow

                        val seqScanData = dataManager.addScan(
                            text = code,
                            templateId = seqTemplate.id,
                            templateName = seqTemplate.name,
                            operator = currentOperator,
                            campus = currentCampus,
                            building = currentBuilding,
                            floor = currentFloor,
                            room = currentRoom,
                            tag = tagNow,
                            allowDuplicate = duplicateScanEnabled
                        )

                        if (seqScanData != null) {
                            // 写入成功才推进（避免重复码被拒时跳号/跳标签）：
                            // 无标签或固定"无标签"→ 每次推进序号；有标签 → 轮完一圈才推进序号
                            val tags = seqTemplate.tags
                            val cursor = seqTemplate.seqTagCursor
                            val advanceSeq: Boolean
                            val newCursor: Int
                            if (tags.isEmpty() || cursor < 0) {
                                advanceSeq = true
                                newCursor = cursor
                            } else {
                                newCursor = (cursor + 1) % tags.size
                                advanceSeq = newCursor == 0
                            }
                            val step = if (seqTemplate.seq.descending) -1 else 1
                            // 先持久化新游标再追加扫码，两者落在同一份模板快照里
                            dataManager.updateTemplate(
                                seqTemplate.copy(
                                    nextSeq = if (advanceSeq) value + step else value,
                                    seqTagCursor = newCursor
                                )
                            )
                            appendScanToActiveTemplate(seqScanData)

                            // 序号记录无楼层概念：楼层筛选切回"所有楼层"，保证新纪录立刻可见
                            if (scanSelectedFloor != 0) {
                                clampSelectedFloor(0, seqTemplate.maxFloor)
                            }

                            if (clipboardEnabled) {
                                copyToClipboard(code)
                                showToast("已复制: $code")
                            }
                            if (uploadEnabled && serverUrl.isNotEmpty()) {
                                uploadData(seqScanData, showToast)
                            }
                            // 更新扫描结果StateFlow，触发UI更新（无条件，保证扫码实时进列表）
                            getAllScans()

                            // 自动补零：总条数跨位（9→10、99→100…）时重算本模板全部序号标记
                            if (seqTemplate.seq.autoPad) {
                                rewriteAutoPaddedSeqMarks(seqTemplate.id)
                            }
                        }
                        return
                    }

                    // ===== 房间号模式：按 楼层→房间→标签 游标自动推进 =====

                    // 扫描前：先按模板游标预填字段（不推进游标）
                    val applied = applyTemplateForNextScan()

                    // 楼层未配置房间号不再拦截：扫到的内容以空房间直接进列表
                    // （游标推进由下方 applied != null 分支保证）

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
                        tag = currentTag,
                        allowDuplicate = duplicateScanEnabled
                    )

                    if (scanData != null) {

                        // 关键：只有真正写入成功才推进游标，避免跳号；
                        // 游标按 楼层→房间→标签 推进（标签最快），一轮用完自动进入下一房间/楼层
                        applied?.let { (t, pos) ->
                            scanCursorByTemplate[t.id] = advanceScanCursor(
                                t,
                                scanCursorByTemplate[t.id] ?: cursorFromSelection(t, pos.floor, pos.room, pos.tag)
                            )
                            // 楼层筛选选中具体楼层时跟随本次录入楼层，保证新纪录立刻可见
                            if (scanSelectedFloor != 0 && scanSelectedFloor != pos.floor) {
                                clampSelectedFloor(pos.floor, t.maxFloor)
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

                        // 更新扫描结果StateFlow，触发UI更新。
                        // 必须无条件执行：无模板路径不经过 appendScanToActiveTemplate，
                        // 只靠这里的刷新，扫码内容才能实时进列表
                        getAllScans()

                    }

                }

            }

        } catch (e: Exception) {

            Log.e(TAG, "扫码处理异常: ${e.message}", e)

            showToast("处理扫码数据失败：${e.message ?: "未知错误"}")

        }

    }



    /**
     * 扫码 winClientLink 后确认切换上传地址：
     * 先探测目标是否真的是 CatScan 服务端（校验响应形状），
     * 防止现场贴伪造二维码把后续扫码数据引到任意 HTTP 服务。
     */
    fun confirmServerUrlChange(newUrl: String, showToast: (String) -> Unit) {
        if (newUrl.isBlank()) {
            showUrlChangeDialog = false
            return
        }
        viewModelScope.launch(Dispatchers.IO) {
            val isCatScanServer = try {
                networkUseCases.checkServerConnectivity(newUrl)
            } catch (e: Exception) {
                Log.e(TAG, "验证上传地址异常: ${e.message}", e)
                false
            }
            launch(Dispatchers.Main) {
                showUrlChangeDialog = false
                if (isCatScanServer) {
                    serverUrl = newUrl
                    uploadEnabled = true
                    startHeartbeatDetection()
                    showToast("已更新上传地址")
                } else {
                    showToast("地址验证失败，未切换: $newUrl")
                }
            }
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

                    // 按扫码数据 id 直接标记已上传，免去一次全列表拷贝查询
                    scanUseCases.markScanAsUploadedByDataId(scanData.id)
                    // 切换到主线程显示Toast

                    viewModelScope.launch(Dispatchers.Main) {
                        getAllScans()
                        showToast("上传成功: ${scanData.text}")
                    }

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

        // 文字识别内核：相册图片走 OCR，识别出的文字行交由结果弹窗点选录入
        if (captureKernel == CaptureKernel.OCR) {
            onImagePickedOcr(uri, context, showToast)
            return
        }

        // 使用默认扫描器，自动检测所有条码格式
        val scanner = BarcodeScanning.getClient()

        viewModelScope.launch(Dispatchers.IO) {
            // 大图解码耗时，放 IO 线程；MLKit 的 process 可在任意线程调用，
            // 监听回调默认仍回到主线程
            val originalImage = try {
                InputImage.fromFilePath(context, uri)
            } catch (e: Exception) {
                Log.e("CatScan", "InputImage.fromFilePath failed, uri=$uri", e)
                launch(Dispatchers.Main) {
                    showToast("图片读取失败：${e.message ?: e.javaClass.simpleName}")
                    scanner.close()  // 确保异常时关闭 scanner
                }
                return@launch
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
    }

    

    /** 文字识别内核的相册入口：识别整图文字行，弹窗展示后由用户点选录入 */
    private fun onImagePickedOcr(uri: Uri, context: Context, showToast: (String) -> Unit) {
        val recognizer = TextRecognition.getClient(ChineseTextRecognizerOptions.Builder().build())
        viewModelScope.launch(Dispatchers.IO) {
            val image = try {
                InputImage.fromFilePath(context, uri)
            } catch (e: Exception) {
                Log.e("CatScan", "OCR InputImage.fromFilePath failed, uri=$uri", e)
                launch(Dispatchers.Main) {
                    showToast("图片读取失败：${e.message ?: e.javaClass.simpleName}")
                    recognizer.close()
                }
                return@launch
            }

            recognizer.process(image)
                .addOnSuccessListener { result ->
                    // 按块→行展开：行是最小可点选单位，块内多行不该挤成一条
                    val lines = result.textBlocks
                        .flatMap { block -> block.lines }
                        .map { line -> line.text.trim() }
                        .filter { it.isNotEmpty() }
                    if (lines.isEmpty()) {
                        showToast("未识别到文字")
                    } else {
                        ocrImageLines = lines
                        showOcrImageDialog = true
                    }
                    recognizer.close()
                }
                .addOnFailureListener { e ->
                    Log.e("CatScan", "OCR image process failed", e)
                    showToast("识别失败")
                    recognizer.close()
                }
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

            val enhancedBitmap = buildCenterCroppedBitmap(bitmap, 0.7f)

            

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



    private fun buildCenterCroppedBitmap(source: Bitmap, ratio: Float): Bitmap {

        val safeRatio = ratio.coerceIn(0.3f, 1.0f)

        val cropWidth = (source.width * safeRatio).toInt().coerceIn(1, source.width)

        val cropHeight = (source.height * safeRatio).toInt().coerceIn(1, source.height)

        val left = ((source.width - cropWidth) / 2).coerceAtLeast(0)

        val top = ((source.height - cropHeight) / 2).coerceAtLeast(0)

        return Bitmap.createBitmap(source, left, top, cropWidth, cropHeight)

    }



    fun deleteItemById(id: Long) {

        // 使用数据管理中心删除扫描数据

        val deleted = dataManager.deleteScan(id)

        // 删除后按剩余最近一条记录恢复游标（表头"下一个房号"跟随变化）
        dataManager.activeTemplate?.let { restoreScanCursor(it) }

        // 自动补零：删除后总条数降位（100→99…）时同步收紧全部序号标记
        dataManager.activeTemplate?.let {
            if (it.markMode == MarkMode.SEQ && it.seq.autoPad) {
                rewriteAutoPaddedSeqMarks(it.id)
            }
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
        val template = activeTemplate
        if (template == null) {
            showToast("请先选择模板")
            return
        }

        clearTemplateFloorScans(
            id = template.id,
            floor = scanSelectedFloor,
            showToast = showToast
        )

    }

    

    // ===================== 网络发现 =====================

    

    fun startNetworkDiscovery(context: Context, onDiscoveryComplete: () -> Unit = {}) {

        if (isDiscovering) return

        

        isDiscovering = true

        discoveredServers.clear()

        

        if (networkDiscovery == null) {

            networkDiscovery = NetworkDiscovery(context.applicationContext)

            // 初始化周期性发现器


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


     */

    fun startPassivePcDiscovery(context: Context) {

        if (networkDiscovery == null) {

            networkDiscovery = NetworkDiscovery(context.applicationContext)

        }


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

     * 启动周期性 PC 发现。

     */

    fun startPassiveDiscovery(context: Context) {
        startPassivePcDiscovery(context)
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
            if (!pendingUploadInProgress.compareAndSet(false, true)) return

            try {

                // 获取模板的扫描数据

                val pendingScans = scanUseCases.getPendingScans()
                    .filter { it.scanData.templateId == activeTemplate.id }

                if (pendingScans.isNotEmpty()) {

                    Log.d(TAG, "补传模板 ${activeTemplate.name} 的 ${pendingScans.size} 条数据...")

                    

                    // 转换为ScanData列表

                    val scanDataList = pendingScans.map { it.scanData }

                    

                    networkUseCases.uploadTemplateData(

                        templateId = activeTemplate.id,

                        templateName = activeTemplate.name,

                        scanDataList = scanDataList,

                        serverUrl = serverUrl,

                        onSuccess = {

                            pendingScans.forEach { scanUseCases.markScanAsUploaded(it.id) }
                            getAllScans()
                            pendingUploadInProgress.set(false)
                            Log.d(TAG, "批量上传成功: ${scanDataList.size} 条数据")

                        },

                        onError = { err ->

                            pendingUploadInProgress.set(false)
                            Log.e(TAG, "批量上传失败: $err")

                        }

                    )

                } else {
                    pendingUploadInProgress.set(false)
                }

            } catch (e: Exception) {

                pendingUploadInProgress.set(false)
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

