package com.example.catscandemo.domain.use_case

import com.example.catscandemo.domain.model.ScanData
import com.example.catscandemo.domain.model.ScanResult
import com.example.catscandemo.domain.model.TemplateModel

/**
 * 鎵弿鏁版嵁绠＄悊鐩稿叧鐨?Use Case
 */
class ScanUseCases(
    val addScan: AddScanUseCase,
    val deleteScan: DeleteScanUseCase,
    val updateScan: UpdateScanUseCase,
    val getScanById: GetScanByIdUseCase,
    val getAllScans: GetAllScansUseCase,
    val getPendingScans: GetPendingScansUseCase,
    val markScanAsUploaded: MarkScanAsUploadedUseCase,
    val addScanToTemplate: AddScanToTemplateUseCase,
    val clearAllScans: ClearAllScansUseCase,
    val replaceAll: ReplaceAllScansUseCase,
    private val scanRepository: ScanRepository
) {
    fun setCurrentTemplateId(templateId: String?) {
        scanRepository.setCurrentTemplateId(templateId)
    }
}

/**
 * 娓呯┖鎵€鏈夋壂鎻忔暟鎹殑 Use Case
 */
class ClearAllScansUseCase(
    private val scanRepository: ScanRepository
) {
    operator fun invoke() {
        scanRepository.clearAllScans()
    }
}

/**
 * 鏇挎崲鎵€鏈夋壂鎻忔暟鎹殑 Use Case
 */
class ReplaceAllScansUseCase(
    private val scanRepository: ScanRepository
) {
    operator fun invoke(scans: List<ScanResult>) {
        scanRepository.replaceAll(scans)
    }
}

/**
 * 娣诲姞鎵弿鏁版嵁鐨?Use Case
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
 * 鍒犻櫎鎵弿鏁版嵁鐨?Use Case
 */
class DeleteScanUseCase(
    private val scanRepository: ScanRepository
) {
    operator fun invoke(id: Long): ScanResult? {
        return scanRepository.deleteScan(id)
    }
}

/**
 * 鏇存柊鎵弿鏁版嵁鐨?Use Case
 */
class UpdateScanUseCase(
    private val scanRepository: ScanRepository
) {
    operator fun invoke(id: Long, scanData: ScanData) {
        scanRepository.updateScan(id, scanData)
    }
}

/**
 * 鏍规嵁 ID 鑾峰彇鎵弿鏁版嵁鐨?Use Case
 */
class GetScanByIdUseCase(
    private val scanRepository: ScanRepository
) {
    operator fun invoke(id: Long): ScanResult? {
        return scanRepository.getScanById(id)
    }
}

/**
 * 鑾峰彇鎵€鏈夋壂鎻忔暟鎹殑 Use Case
 */
class GetAllScansUseCase(
    private val scanRepository: ScanRepository
) {
    operator fun invoke(): List<ScanResult> {
        return scanRepository.getAllScans()
    }
}

/**
 * 鑾峰彇鏈笂浼犳壂鎻忔暟鎹殑 Use Case
 */
class GetPendingScansUseCase(
    private val scanRepository: ScanRepository
) {
    operator fun invoke(): List<ScanResult> {
        return scanRepository.getPendingScans()
    }
}

/**
 * 鏍囪鎵弿鏁版嵁涓哄凡涓婁紶鐨?Use Case
 */
class MarkScanAsUploadedUseCase(
    private val scanRepository: ScanRepository
) {
    operator fun invoke(id: Long) {
        scanRepository.markScanAsUploaded(id)
    }
}

/**
 * 鍚戞ā鏉挎坊鍔犳壂鎻忔暟鎹殑 Use Case
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
 * 鎵弿鏁版嵁浠撳簱鎺ュ彛
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
    fun replaceAll(scans: List<ScanResult>)
    fun clearAllScans()
}


