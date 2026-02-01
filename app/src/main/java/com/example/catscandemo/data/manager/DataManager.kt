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
    private val scanUseCases: ScanUseCases,
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

        if (activeTemplateId == null && templates.isNotEmpty()) {
            setActiveTemplate(templates.first().id)
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

        // 如果更新的是当前活动模板，更新activeTemplate状态
        if (activeTemplateId == updated.id) {
            activeTemplate = updated
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
    }

    /**
     * 清空模板扫描数据
     */
    fun clearTemplateScans(id: String) {
        templateUseCases.clearTemplateScans(id)

        // 同时从扫描结果列表中删除对应模板的所有数据
        val allScans = scanUseCases.getAllScans.invoke()
        val scansToDelete = allScans.filter { it.scanData.templateId == id }
        scansToDelete.forEach {
            scanUseCases.deleteScan(it.id)
        }

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

        // 同时从扫描结果列表中删除对应的数据
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
     */
    fun addScanToActiveTemplate(scanData: ScanData): ScanData {
        val t = activeTemplate ?: throw IllegalStateException("No active template")
        
        // 确保templateId和templateName正确设置
        val updatedScanData = scanData.copy(
            templateId = t.id,
            templateName = t.name
        )
        
        // 添加到扫描结果
        scanUseCases.addScanToTemplate(t.id, updatedScanData)

        // 更新模板数据
        val updatedTemplate = t.copy(
            scans = listOf(updatedScanData) + t.scans
        )
        updateTemplate(updatedTemplate)

        return updatedScanData
    }

    /**
     * 删除扫描数据
     */
    fun deleteScan(id: Long): ScanResult? {
        val deleted = scanUseCases.deleteScan(id)

        // 同步到模板数据：从对应模板的 scans 中删除同一条（按 templateId + timestamp 匹配）
        if (deleted != null && deleted.scanData.templateId.isNotBlank()) {
            deleteTemplateScan(deleted.scanData.templateId, deleted.scanData.id)
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
            }
        }
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
        
        // 清空所有模板的扫描数据
        templates.forEachIndexed { index, template ->
            val updatedTemplate = template.copy(scans = emptyList())
            templates.removeAt(index)
            templates.add(index, updatedTemplate)
            
            if (activeTemplateId == template.id) {
                activeTemplate = updatedTemplate
            }
        }
        
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
                        // 处理异常
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
                        // 处理异常
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
