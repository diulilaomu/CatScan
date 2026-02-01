package com.example.catscandemo.data.repository

import android.content.Context
import com.example.catscandemo.domain.model.ScanData
import com.example.catscandemo.domain.model.ScanResult
import com.example.catscandemo.domain.use_case.ScanRepository
import com.example.catscandemo.ui.main.ScanHistoryStorage

/**
 * 默认扫描数据仓库实现
 * 负责扫描数据的存储和管理
 */
class DefaultScanRepository(
    private val context: Context
) : ScanRepository {

    private var scanResults: MutableList<ScanResult> = mutableListOf()
    private var nextId: Long = 1L
    private var nextIndex: Int = 1
    private var initialized = false

    init {
        initialize()
    }

    private fun initialize() {
        if (!initialized) {
            val loaded = ScanHistoryStorage.load(context)
            scanResults = loaded.items.toMutableList()
            // 恢复自增序号，避免新扫描 id/index 重复
            val maxId = scanResults.maxOfOrNull { it.id } ?: 0L
            val maxIndex = scanResults.maxOfOrNull { it.index } ?: 0
            nextId = maxId + 1
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
            val updatedResult = scanResults[index].copy(uploaded = true)
            scanResults[index] = updatedResult
            saveScanResults()
        }
    }

    override fun replaceAll(scans: List<ScanResult>) {
        scanResults = scans.toMutableList()
        // 恢复自增序号，避免新扫描 id/index 重复
        val maxId = scanResults.maxOfOrNull { it.id } ?: 0L
        val maxIndex = scanResults.maxOfOrNull { it.index } ?: 0
        nextId = maxId + 1
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
        ScanHistoryStorage.save(context, scanResults)
    }
}
