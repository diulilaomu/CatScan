package com.example.catscandemo.data.manager

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.example.catscandemo.domain.model.ScanData
import com.example.catscandemo.domain.model.ScanResult
import com.example.catscandemo.domain.model.TemplateModel
import com.example.catscandemo.domain.use_case.ScanUseCases
import com.example.catscandemo.domain.use_case.TemplateUseCases
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * 数据管理中心
 * 统一管理所有数据的添加、删除、修改和同步操作
 * 确保模板数据和扫描数据保持一致
 */
class DataManager(
    val scanUseCases: ScanUseCases,
    private val templateUseCases: TemplateUseCases
) {

    // 模板数据状态
    val templates = mutableStateListOf<TemplateModel>()
    var activeTemplateId by mutableStateOf<String?>(null)
    var activeTemplate by mutableStateOf<TemplateModel?>(null)

    // 协程作用域
    private val coroutineScope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    /**
     * 初始化数据
     */
    fun initializeData() {
        loadTemplates()
    }

    /**
     * 加载模板数据
     */
    private fun loadTemplates() {
        val (loadedTemplates, activeId) = templateUseCases.loadTemplates.invoke()
        templates.clear()
        templates.addAll(loadedTemplates)
        activeTemplateId = activeId
        activeTemplate = activeId?.let { templateUseCases.getTemplateById(it) }

        // 同步模板中的扫描数据到结果列表
        syncTemplateScansToResults()

        if (activeTemplateId == null && templates.isNotEmpty()) {
            setActiveTemplate(templates.first().id)
        }
    }
    
    /**
     * 同步模板中的扫描数据到结果列表
     * 这是一个单向同步：从模板 -> 到扫描结果列表
     */
    private fun syncTemplateScansToResults() {
        // 确保当前模板ID已设置
        if (activeTemplateId != null) {
            // 加载当前模板
            val currentTemplate = templateUseCases.getTemplateById(activeTemplateId!!)
            if (currentTemplate != null) {
                // 直接替换整个扫描结果列表，保持与模板数据的一致性
                // 通过直接赋值的方式避免重新生成ID导致的数据不一致
                val scanResults = currentTemplate.scans.mapIndexed { index, scanData ->
                    // 使用scanData.id的hash作为ScanResult的id，确保可追踪性
                    ScanResult(
                        id = index.toLong() + 1,  // 使用简单的递增ID，与模板顺序保持一致
                        index = index + 1,
                        scanData = scanData,
                        uploaded = scanData.uploaded
                    )
                }
                scanUseCases.replaceAll.invoke(scanResults)
            }
        }
    }

    /**
     * 保存模板数据
     */
    private fun saveTemplates() {
        val list = templates.toList()
        val active = activeTemplateId
        templateUseCases.saveTemplates.invoke(list, active)
    }

    /**
     * 添加模板
     */
    fun addTemplate(name: String): TemplateModel {
        val template = templateUseCases.addTemplate(name)
        templates.add(0, template)
        setActiveTemplate(template.id)
        saveTemplates()
        return template
    }

    /**
     * 删除模板
     */
    fun deleteTemplate(id: String): TemplateModel? {
        val wasActive = (activeTemplateId == id)
        val deletedTemplate = templateUseCases.deleteTemplate(id)

        val idx = templates.indexOfFirst { it.id == id }
        if (idx != -1) templates.removeAt(idx)

        if (wasActive) {
            // 删的是当前模板：尝试把 active 切到第一个；如果没有模板了则为 null
            activeTemplateId = templates.firstOrNull()?.id
            activeTemplate = activeTemplateId?.let { templateUseCases.getTemplateById(it) }

            // 清空识别结果（全部）
            scanUseCases.clearAllScans.invoke()
        } else {
            // 删的不是当前模板：只删除该模板的识别结果
            val allScans = scanUseCases.getAllScans.invoke()
            val scansToDelete = allScans.filter { it.scanData.templateId == id }
            scansToDelete.forEach {
                scanUseCases.deleteScan(it.id)
            }
        }

        saveTemplates()
        return deletedTemplate
    }

    /**
     * 更新模板
     */
    fun updateTemplate(updated: TemplateModel) {
        templateUseCases.updateTemplate(updated)

        // 使用removeAt和add来触发状态更新
        val idx = templates.indexOfFirst { it.id == updated.id }
        if (idx != -1) {
            templates.removeAt(idx)
            templates.add(idx, updated)
        }

        // 如果更新的是当前活动模板，更新activeTemplate状态并重新加载数据
        if (activeTemplateId == updated.id) {
            activeTemplate = updated
            // 重新加载当前模板的数据，确保结果列表同步
            scanUseCases.setCurrentTemplateId(updated.id)
            // 同步模板中的扫描数据到结果列表
            syncTemplateScansToResults()
        }

        saveTemplates()
    }

    /**
     * 设置激活模板
     */
    fun setActiveTemplate(id: String) {
        activeTemplateId = id
        val t = templateUseCases.getTemplateById(id)
        activeTemplate = t
        saveTemplates()
        // ✅ 关键：必须先设置 currentTemplateId，然后再调用 syncTemplateScansToResults()
        // 这样 Repository 会加载正确的模板数据文件
        scanUseCases.setCurrentTemplateId(id)
        // 同步模板中的扫描数据到结果列表
        syncTemplateScansToResults()
    }
    
    /**
     * 清除激活模板（设置为无模板）
     */
    fun clearActiveTemplate() {
        activeTemplateId = null
        activeTemplate = null
        saveTemplates()
        // ✅ 关键：必须先设置 setCurrentTemplateId(null)，再调用 syncTemplateScansToResults
        // 这样才能加载无模板状态下的数据
        scanUseCases.setCurrentTemplateId(null)
        // 同步模板中的扫描数据到结果列表（此时应该是空的）
        syncTemplateScansToResults()
    }

    /**
     * 清空模板扫描数据
     */
    fun clearTemplateScans(id: String) {
        templateUseCases.clearTemplateScans(id)
        
        // 同时清空扫描结果中该模板的所有数据
        val allScans = scanUseCases.getAllScans.invoke()
        val scansToClear = allScans.filter { it.scanData.templateId == id }
        scansToClear.forEach { scanUseCases.deleteScan(it.id) }

        // 更新模板数据，确保数据同步
        val idx = templates.indexOfFirst { it.id == id }
        if (idx != -1) {
            val template = templates[idx]
            val updatedTemplate = template.copy(scans = emptyList())
            templates.removeAt(idx)
            templates.add(idx, updatedTemplate)

            // 如果是当前激活的模板，也更新activeTemplate
            if (activeTemplateId == id) {
                activeTemplate = updatedTemplate
            }
        }

        saveTemplates()
    }

    /**
     * 删除模板中的扫描数据
     */
    fun deleteTemplateScan(id: String, scanId: String) {
        templateUseCases.deleteTemplateScan(id, scanId)
        
        // 同时从扫描结果中删除该数据
        val allScans = scanUseCases.getAllScans.invoke()
        val scanToDelete = allScans.find { it.scanData.id == scanId }
        if (scanToDelete != null) {
            scanUseCases.deleteScan(scanToDelete.id)
        }

        // 更新模板数据，确保数据同步
        val idx = templates.indexOfFirst { it.id == id }
        if (idx != -1) {
            val template = templates[idx]
            val updatedTemplate = template.copy(
                scans = template.scans.filterNot { it.id == scanId }
            )
            templates.removeAt(idx)
            templates.add(idx, updatedTemplate)

            // 如果是当前激活的模板，也更新activeTemplate
            if (activeTemplateId == id) {
                activeTemplate = updatedTemplate
            }
        }

        saveTemplates()
    }

    /**
     * 向当前模板添加扫描数据
     * 注意：scanData 已经通过 addScan() 添加到 ScanRepository 了
     * 这里只需要同步到 TemplateModel 以保证模板持久化
     */
    fun addScanToActiveTemplate(scanData: ScanData): ScanData {
        val t = activeTemplate ?: throw IllegalStateException("No active template")
        
        // 确保templateId和templateName正确设置
        val updatedScanData = scanData.copy(
            templateId = t.id,
            templateName = t.name
        )
        
        // 更新模板数据（ScanRepository 已经通过 addScan() 更新了）
        val updatedTemplate = t.copy(
            scans = listOf(updatedScanData) + t.scans
        )
        updateTemplate(updatedTemplate)
        
        // 同步当前激活模板的引用
        activeTemplate = updatedTemplate

        // 保存数据，确保数据同步
        saveTemplates()

        return updatedScanData
    }

    /**
     * 删除扫描数据
     */
    fun deleteScan(id: Long): ScanResult? {
        val deleted = scanUseCases.deleteScan(id)

        // 同步到模板数据：从对应模板的 scans 中删除同一条（按 templateId + 数据id 匹配）
        if (deleted != null && deleted.scanData.templateId.isNotBlank()) {
            // 更新模板中的scans列表
            val templateId = deleted.scanData.templateId
            val scanId = deleted.scanData.id
            val idx = templates.indexOfFirst { it.id == templateId }
            if (idx != -1) {
                val template = templates[idx]
                val updatedTemplate = template.copy(
                    scans = template.scans.filterNot { it.id == scanId }
                )
                templates.removeAt(idx)
                templates.add(idx, updatedTemplate)
                
                // 如果删除的是当前激活模板的数据，同步activeTemplate
                if (activeTemplateId == templateId) {
                    activeTemplate = updatedTemplate
                }
            }
            
            saveTemplates()
        }

        return deleted
    }

    /**
     * 更新扫描数据
     */
    fun updateScan(id: Long, scanData: ScanData) {
        scanUseCases.updateScan(id, scanData)

        // 同步到模板数据：更新对应模板中的扫描数据
        if (scanData.templateId.isNotBlank()) {
            val templateId = scanData.templateId
            val template = templateUseCases.getTemplateById(templateId)
            if (template != null) {
                val updatedScans = template.scans.map {
                    if (it.id == scanData.id) scanData else it
                }
                val updatedTemplate = template.copy(scans = updatedScans)
                updateTemplate(updatedTemplate)
                
                // 如果更新的是当前活动模板的数据，同步activeTemplate
                if (activeTemplateId == templateId) {
                    activeTemplate = updatedTemplate
                }
            }
        }
        
        // 保存数据
        saveTemplates()
    }

    /**
     * 获取所有扫描数据
     */
    fun getAllScans(): List<ScanResult> {
        return scanUseCases.getAllScans.invoke()
    }

    /**
     * 清空所有扫描数据
     */
    fun clearAllScans() {
        scanUseCases.clearAllScans.invoke()
        
        // 清空所有模板的扫描数据 - 使用map创建更新后的列表避免并发修改问题
        val updatedTemplates = templates.map { template ->
            template.copy(scans = emptyList())
        }
        templates.clear()
        templates.addAll(updatedTemplates)
        
        // 更新当前激活模板的引用
        activeTemplate = activeTemplate?.copy(scans = emptyList())
        
        saveTemplates()
    }

    /**
     * 批量操作：添加多个扫描数据
     */
    fun addMultipleScans(scanDataList: List<ScanData>) {
        coroutineScope.launch {
            withContext(Dispatchers.IO) {
                scanDataList.forEach {scanData ->
                    try {
                        addScanToActiveTemplate(scanData)
                    } catch (e: Exception) {
                        android.util.Log.w("DataManager", "批量添加失败: ${e.message}")
                    }
                }
            }
        }
    }

    /**
     * 批量操作：删除多个扫描数据
     */
    fun deleteMultipleScans(ids: List<Long>) {
        coroutineScope.launch {
            withContext(Dispatchers.IO) {
                ids.forEach {id ->
                    try {
                        deleteScan(id)
                    } catch (e: Exception) {
                        android.util.Log.w("DataManager", "批量删除失败: ${e.message}")
                    }
                }
            }
        }
    }

    /**
     * 添加扫描数据
     */
    fun addScan(
        text: String,
        templateId: String = "",
        templateName: String = "",
        operator: String = "unknown",
        campus: String = "",
        building: String = "",
        floor: String = "",
        room: String = "",
        allowDuplicate: Boolean = true
    ): ScanData? {
        // ✅ 关键：确保 Repository 的 currentTemplateId 与要添加的 scanData.templateId 匹配
        // 否则数据会被保存到错误的文件中，导致无模板和有模板的数据混杂
        if (templateId.isNotBlank()) {
            scanUseCases.setCurrentTemplateId(templateId)
        } else {
            scanUseCases.setCurrentTemplateId(null)
        }
        
        return scanUseCases.addScan.invoke(
            text = text,
            templateId = templateId,
            templateName = templateName,
            operator = operator,
            campus = campus,
            building = building,
            floor = floor,
            room = room,
            allowDuplicate = allowDuplicate
        )
    }
}
