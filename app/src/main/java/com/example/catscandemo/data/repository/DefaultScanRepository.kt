package com.example.catscandemo.data.repository

import android.content.Context
import android.util.Log
import com.example.catscandemo.domain.model.ScanData
import com.example.catscandemo.domain.model.ScanResult
import com.example.catscandemo.domain.use_case.ScanRepository
import com.example.catscandemo.ui.main.ScanHistoryStorage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch

/**
 * 默认扫描数据仓库实现
 * 负责扫描数据的存储和管理
 *
 * 内存列表会被主线程（扫码/删除）和 IO 线程（上传回调读列表）并发访问，
 * 所有读写都经由 stateLock 串行化，避免并发修改。
 */
class DefaultScanRepository(
    private val context: Context
) : ScanRepository {

    private data class SaveRequest(
        val templateId: String?,
        val scanResults: List<ScanResult>
    )

    private var scanResults: MutableList<ScanResult> = mutableListOf()
    private var nextId: Long = 1L
    private var nextIndex: Int = 1
    private var initialized = false
    private var currentTemplateId: String? = null
    private val stateLock = Any()
    private val persistenceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val pendingSaveLock = Any()
    private val pendingSaves = LinkedHashMap<String, SaveRequest>()
    private val saveSignals = Channel<Unit>(Channel.CONFLATED)

    init {
        synchronized(stateLock) {
            initializeLocked()
        }
        persistenceScope.launch {
            for (ignored in saveSignals) {
                while (true) {
                    val request = synchronized(pendingSaveLock) {
                        val entry = pendingSaves.entries.firstOrNull()
                            ?: return@synchronized null
                        entry.value.also { pendingSaves.remove(entry.key) }
                    } ?: break
                    try {
                        ScanHistoryStorage.save(context, request.templateId, request.scanResults)
                    } catch (error: Exception) {
                        Log.e("ScanRepository", "保存扫码历史失败", error)
                    }
                }
            }
        }
    }

    override fun setCurrentTemplateId(templateId: String?) {
        synchronized(stateLock) {
            if (currentTemplateId != templateId) {
                currentTemplateId = templateId
                // 重新初始化，加载对应模板的数据
                initialized = false
                initializeLocked()
            }
        }
    }

    private fun initializeLocked() {
        if (!initialized) {
            val loaded = ScanHistoryStorage.load(context, currentTemplateId)
            // 无模板历史里只应保留 templateId 为空的记录：历史上一个启动时序问题
            // 曾把激活模板的扫码整表写进 no_template 文件，加载时把这类污染数据滤掉，
            // 下次保存即完成文件层面的清理
            scanResults = if (currentTemplateId == null) {
                loaded.items.filter { it.scanData.templateId.isBlank() }.toMutableList()
            } else {
                loaded.items.toMutableList()
            }
            // 恢复自增序号，避免新扫描 id/index 重复
            // id 可能是稳定 hash（含负值），自增起点从非负最大值之后开始
            val maxId = scanResults.maxOfOrNull { it.id } ?: 0L
            val maxIndex = scanResults.maxOfOrNull { it.index } ?: 0
            nextId = maxOf(maxId, 0L) + 1
            nextIndex = maxIndex + 1
            initialized = true
        }
    }

    override fun addScan(
        text: String,
        templateId: String,
        templateName: String,
        operator: String,
        campus: String,
        building: String,
        floor: String,
        room: String,
        tag: String,
        allowDuplicate: Boolean
    ): ScanData? = synchronized(stateLock) {
        // 检查重复
        if (!allowDuplicate) {
            if (scanResults.any { it.scanData.text == text }) {
                return@synchronized null
            }
        } else {
            if (scanResults.isNotEmpty() && scanResults.first().scanData.text == text) {
                return@synchronized null
            }
        }

        val scanData = ScanData(
            text = text,
            operator = operator,
            campus = campus,
            building = building,
            floor = floor,
            room = room,
            tag = tag,
            templateId = templateId,
            templateName = templateName,
            uploaded = false
        )

        val scanResult = ScanResult(
            id = nextId++,
            index = nextIndex++,
            scanData = scanData,
            uploaded = false
        )

        scanResults.add(0, scanResult)
        saveScanResultsLocked()
        scanData
    }

    override fun deleteScan(id: Long): ScanResult? = synchronized(stateLock) {
        val deleted = scanResults.firstOrNull { it.id == id }
        scanResults.removeAll { it.id == id }
        saveScanResultsLocked()
        deleted
    }

    override fun updateScan(id: Long, scanData: ScanData) {
        synchronized(stateLock) {
            val index = scanResults.indexOfFirst { it.id == id }
            if (index != -1) {
                val updatedResult = scanResults[index].copy(scanData = scanData)
                scanResults[index] = updatedResult
                saveScanResultsLocked()
            }
        }
    }

    override fun getScanById(id: Long): ScanResult? = synchronized(stateLock) {
        scanResults.firstOrNull { it.id == id }
    }

    override fun getAllScans(): List<ScanResult> = synchronized(stateLock) {
        scanResults.toList()
    }

    override fun getPendingScans(): List<ScanResult> = synchronized(stateLock) {
        scanResults.filter { !it.uploaded }
    }

    override fun markScanAsUploaded(id: Long) {
        synchronized(stateLock) {
            val index = scanResults.indexOfFirst { it.id == id }
            if (index != -1) {
                val current = scanResults[index]
                val updatedResult = current.copy(
                    scanData = current.scanData.copy(uploaded = true),
                    uploaded = true
                )
                scanResults[index] = updatedResult
                saveScanResultsLocked()
            }
        }
    }

    override fun markScanAsUploadedByDataId(dataId: String) {
        synchronized(stateLock) {
            val index = scanResults.indexOfFirst { it.scanData.id == dataId }
            if (index != -1) {
                val current = scanResults[index]
                val updatedResult = current.copy(
                    scanData = current.scanData.copy(uploaded = true),
                    uploaded = true
                )
                scanResults[index] = updatedResult
                saveScanResultsLocked()
            }
        }
    }

    override fun replaceAll(scans: List<ScanResult>) {
        synchronized(stateLock) {
            scanResults = scans.toMutableList()
            // 恢复自增序号，避免新扫描 id/index 重复
            // id 可能是稳定 hash（含负值），自增起点从非负最大值之后开始
            val maxId = scanResults.maxOfOrNull { it.id } ?: 0L
            val maxIndex = scanResults.maxOfOrNull { it.index } ?: 0
            nextId = maxOf(maxId, 0L) + 1
            nextIndex = maxIndex + 1
            saveScanResultsLocked()
        }
    }

    /**
     * 只清空无模板桶（scan_history_no_template.json）。
     * 当前激活的可能是其他模板（内存列表不受影响），也可能正指向无模板文件——
     * 后者必须同步清内存，否则未触发重载时列表里还留着已"删除"的记录。
     * 落盘一律走异步保存队列，避免在调用线程写文件。
     */
    override fun clearNoTemplateScans() {
        synchronized(stateLock) {
            if (currentTemplateId == null) {
                scanResults.clear()
                nextId = 1L
                nextIndex = 1
                saveScanResultsLocked()
            } else {
                val request = SaveRequest(null, emptyList())
                synchronized(pendingSaveLock) {
                    pendingSaves["<no-template>"] = request
                }
                saveSignals.trySend(Unit)
            }
        }
    }

    /**
     * 清空所有扫描数据
     */
    override fun clearAllScans() {
        synchronized(stateLock) {
            scanResults.clear()
            nextId = 1L
            nextIndex = 1
            saveScanResultsLocked()
        }
    }

    /**
     * 把指定模板的扫码列表直接写入它的 history 文件，不经过、也不影响
     * 当前内存中的激活模板列表。单一数据源下，非激活模板的删除/清空
     * 不再有 templates.json 内嵌副本兜底，必须显式走这里落盘。
     */
    override fun saveTemplateScans(templateId: String, scans: List<ScanResult>) {
        if (templateId.isBlank()) return
        val request = SaveRequest(templateId, scans.toList())
        synchronized(pendingSaveLock) {
            pendingSaves[templateId] = request
        }
        saveSignals.trySend(Unit)
    }

    private fun saveScanResultsLocked() {
        // 每个模板只保留最新快照，连续扫码不会堆积越来越大的历史列表副本。
        val request = SaveRequest(currentTemplateId, scanResults.toList())
        val key = currentTemplateId ?: "<no-template>"
        synchronized(pendingSaveLock) {
            pendingSaves[key] = request
        }
        saveSignals.trySend(Unit)
    }
}
