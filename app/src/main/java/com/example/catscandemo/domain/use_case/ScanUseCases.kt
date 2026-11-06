package com.example.catscandemo.domain.use_case

import com.example.catscandemo.domain.model.ScanData
import com.example.catscandemo.domain.model.ScanResult
import com.example.catscandemo.domain.model.TemplateModel

/**
 * 扫描数据管理相关的 Use Case
 */
class ScanUseCases(
    val addScan: AddScanUseCase,
    val deleteScan: DeleteScanUseCase,
    val updateScan: UpdateScanUseCase,
    val getScanById: GetScanByIdUseCase,
    val getAllScans: GetAllScansUseCase,
    val getPendingScans: GetPendingScansUseCase,
    val markScanAsUploaded: MarkScanAsUploadedUseCase,
    val markScanAsUploadedByDataId: MarkScanAsUploadedByDataIdUseCase,
    val addScanToTemplate: AddScanToTemplateUseCase,
    val clearAllScans: ClearAllScansUseCase,
    val replaceAll: ReplaceAllScansUseCase,
    val saveTemplateScans: SaveTemplateScansUseCase,
    private val scanRepository: ScanRepository
) {
    fun setCurrentTemplateId(templateId: String?) {
        scanRepository.setCurrentTemplateId(templateId)
    }

    /** 清空无模板桶（scan_history_no_template.json），不影响任何模板的数据 */
    fun clearNoTemplateScans() {
        scanRepository.clearNoTemplateScans()
    }
}

/**
 * 清空所有扫描数据的 Use Case
 */
class ClearAllScansUseCase(
    private val scanRepository: ScanRepository
) {
    operator fun invoke() {
        scanRepository.clearAllScans()
    }
}

/**
 * 替换所有扫描数据的 Use Case
 */
class ReplaceAllScansUseCase(
    private val scanRepository: ScanRepository
) {
    operator fun invoke(scans: List<ScanResult>) {
        scanRepository.replaceAll(scans)
    }
}

/**
 * 把指定模板的扫码列表直接持久化到它的 history 文件（不影响当前激活列表）。
 * 单一数据源下非激活模板的删除/清空必须走这里，否则重启后会复活。
 */
class SaveTemplateScansUseCase(
    private val scanRepository: ScanRepository
) {
    operator fun invoke(templateId: String, scans: List<ScanResult>) {
        scanRepository.saveTemplateScans(templateId, scans)
    }
}

/**
 * 添加扫描数据的 Use Case
 */
class AddScanUseCase(
    private val scanRepository: ScanRepository
) {
    operator fun invoke(
        text: String,
        templateId: String = "",
        templateName: String = "",
        operator: String = "unknown",
        campus: String = "",
        building: String = "",
        floor: String = "",
        room: String = "",
        tag: String = "",
        allowDuplicate: Boolean = true
    ): ScanData? {
        return scanRepository.addScan(
            text = text,
            templateId = templateId,
            templateName = templateName,
            operator = operator,
            campus = campus,
            building = building,
            floor = floor,
            room = room,
            tag = tag,
            allowDuplicate = allowDuplicate
        )
    }
}

/**
 * 删除扫描数据的 Use Case
 */
class DeleteScanUseCase(
    private val scanRepository: ScanRepository
) {
    operator fun invoke(id: Long): ScanResult? {
        return scanRepository.deleteScan(id)
    }
}

/**
 * 更新扫描数据的 Use Case
 */
class UpdateScanUseCase(
    private val scanRepository: ScanRepository
) {
    operator fun invoke(id: Long, scanData: ScanData) {
        scanRepository.updateScan(id, scanData)
    }
}

/**
 * 根据 ID 获取扫描数据的 Use Case
 */
class GetScanByIdUseCase(
    private val scanRepository: ScanRepository
) {
    operator fun invoke(id: Long): ScanResult? {
        return scanRepository.getScanById(id)
    }
}

/**
 * 获取所有扫描数据的 Use Case
 */
class GetAllScansUseCase(
    private val scanRepository: ScanRepository
) {
    operator fun invoke(): List<ScanResult> {
        return scanRepository.getAllScans()
    }
}

/**
 * 获取未上传扫描数据的 Use Case
 */
class GetPendingScansUseCase(
    private val scanRepository: ScanRepository
) {
    operator fun invoke(): List<ScanResult> {
        return scanRepository.getPendingScans()
    }
}

/**
 * 标记扫码数据为已上传的 Use Case（按结果序号 id）
 */
class MarkScanAsUploadedUseCase(
    private val scanRepository: ScanRepository
) {
    operator fun invoke(id: Long) {
        scanRepository.markScanAsUploaded(id)
    }
}

/**
 * 标记扫码数据为已上传的 Use Case（按 ScanData.id，免去调用方先全量查询换 id）
 */
class MarkScanAsUploadedByDataIdUseCase(
    private val scanRepository: ScanRepository
) {
    operator fun invoke(dataId: String) {
        scanRepository.markScanAsUploadedByDataId(dataId)
    }
}

/**
 * 向模板添加扫描数据的 Use Case
 */
class AddScanToTemplateUseCase(
    private val templateRepository: TemplateRepository,
    private val updateTemplate: UpdateTemplateUseCase
) {
    operator fun invoke(templateId: String, scanData: ScanData) {
        val template = templateRepository.getTemplateById(templateId)
        if (template != null) {
            val updatedScans = listOf(scanData) + template.scans
            updateTemplate(template.copy(scans = updatedScans))
        }
    }
}

/**
 * 扫描数据仓库接口
 */
interface ScanRepository {
    fun setCurrentTemplateId(templateId: String?)
    
    fun addScan(
        text: String,
        templateId: String = "",
        templateName: String = "",
        operator: String = "unknown",
        campus: String = "",
        building: String = "",
        floor: String = "",
        room: String = "",
        tag: String = "",
        allowDuplicate: Boolean = true
    ): ScanData?

    fun deleteScan(id: Long): ScanResult?
    fun updateScan(id: Long, scanData: ScanData)
    fun getScanById(id: Long): ScanResult?
    fun getAllScans(): List<ScanResult>
    fun getPendingScans(): List<ScanResult>
    fun markScanAsUploaded(id: Long)
    fun markScanAsUploadedByDataId(dataId: String)
    fun replaceAll(scans: List<ScanResult>)
    fun saveTemplateScans(templateId: String, scans: List<ScanResult>)
    fun clearNoTemplateScans()
    fun clearAllScans()
}


