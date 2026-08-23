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
    private val persistenceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val pendingSaveLock = Any()
    private val pendingSaves = LinkedHashMap<String, SaveRequest>()
    private val saveSignals = Channel<Unit>(Channel.CONFLATED)

    init {
        initialize()
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
        if (currentTemplateId != templateId) {
            currentTemplateId = templateId
            // 重新初始化，加载对应模板的数据
            initialized = false
            initialize()
        }
    }

    private fun initialize() {
        if (!initialized) {
            val loaded = ScanHistoryStorage.load(context, currentTemplateId)
            scanResults = loaded.items.toMutableList()
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
    ): ScanData? {
        // 检查重复
        if (!allowDuplicate) {
            if (scanResults.any { it.scanData.text == text }) {
                return null
            }
        } else {
            if (scanResults.isNotEmpty() && scanResults.first().scanData.text == text) {
                return null
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
        saveScanResults()
        return scanData
    }

    override fun deleteScan(id: Long): ScanResult? {
        val deleted = scanResults.firstOrNull { it.id == id }
        scanResults.removeAll { it.id == id }
        saveScanResults()
        return deleted
    }

    override fun updateScan(id: Long, scanData: ScanData) {
        val index = scanResults.indexOfFirst { it.id == id }
        if (index != -1) {
            val updatedResult = scanResults[index].copy(scanData = scanData)
            scanResults[index] = updatedResult
            saveScanResults()
        }
    }

    override fun getScanById(id: Long): ScanResult? {
        return scanResults.firstOrNull { it.id == id }
    }

    override fun getAllScans(): List<ScanResult> {
        return scanResults.toList()
    }

    override fun getPendingScans(): List<ScanResult> {
        return scanResults.filter { !it.uploaded }
    }

    override fun markScanAsUploaded(id: Long) {
        val index = scanResults.indexOfFirst { it.id == id }
        if (index != -1) {
            val current = scanResults[index]
            val updatedResult = current.copy(
                scanData = current.scanData.copy(uploaded = true),
                uploaded = true
            )
            scanResults[index] = updatedResult
            saveScanResults()
        }
    }

    override fun replaceAll(scans: List<ScanResult>) {
        scanResults = scans.toMutableList()
        // 恢复自增序号，避免新扫描 id/index 重复
        // id 可能是稳定 hash（含负值），自增起点从非负最大值之后开始
        val maxId = scanResults.maxOfOrNull { it.id } ?: 0L
        val maxIndex = scanResults.maxOfOrNull { it.index } ?: 0
        nextId = maxOf(maxId, 0L) + 1
        nextIndex = maxIndex + 1
        saveScanResults()
    }

    /**
     * 清空所有扫描数据
     */
    override fun clearAllScans() {
        scanResults.clear()
        nextId = 1L
        nextIndex = 1
        saveScanResults()
    }

    private fun saveScanResults() {
        // 每个模板只保留最新快照，连续扫码不会堆积越来越大的历史列表副本。
        val request = SaveRequest(currentTemplateId, scanResults.toList())
        val key = currentTemplateId ?: "<no-template>"
        synchronized(pendingSaveLock) {
            pendingSaves[key] = request
        }
        saveSignals.trySend(Unit)
    }
}
