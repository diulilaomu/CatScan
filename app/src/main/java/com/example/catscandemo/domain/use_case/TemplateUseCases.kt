package com.example.catscandemo.domain.use_case

import com.example.catscandemo.domain.model.ScanData
import com.example.catscandemo.domain.model.TemplateModel

/**
 * 妯℃澘绠＄悊鐩稿叧鐨?Use Case
 */
class TemplateUseCases(
    val addTemplate: AddTemplateUseCase,
    val deleteTemplate: DeleteTemplateUseCase,
    val updateTemplate: UpdateTemplateUseCase,
    val getTemplateById: GetTemplateByIdUseCase,
    val getActiveTemplate: GetActiveTemplateUseCase,
    val setActiveTemplate: SetActiveTemplateUseCase,
    val clearTemplateScans: ClearTemplateScansUseCase,
    val deleteTemplateScan: DeleteTemplateScanUseCase,
    val loadTemplates: LoadTemplatesUseCase,
    val saveTemplates: SaveTemplatesUseCase
)

/**
 * 鍔犺浇妯℃澘鐨?Use Case
 */
class LoadTemplatesUseCase(
    private val templateRepository: TemplateRepository
) {
    operator fun invoke(): Pair<List<TemplateModel>, String?> {
        return templateRepository.loadTemplates()
    }
}

/**
 * 淇濆瓨妯℃澘鐨?Use Case
 */
class SaveTemplatesUseCase(
    private val templateRepository: TemplateRepository
) {
    operator fun invoke(templates: List<TemplateModel>, activeId: String?) {
        templateRepository.saveTemplates(templates, activeId)
    }
}

/**
 * 娣诲姞妯℃澘鐨?Use Case
 */
class AddTemplateUseCase(
    private val templateRepository: TemplateRepository
) {
    operator fun invoke(name: String): TemplateModel {
        val template = TemplateModel(
            name = name.trim().ifBlank { "鏈懡鍚嶆ā鏉? },
            operator = "鐚ご鏋?,
            campus = "鐚ご鏍″尯",
            building = "",
            maxFloor = 1,
            roomCountPerFloor = 1,
            selectedRooms = emptyList(),
            scans = emptyList()
        )
        templateRepository.addTemplate(template)
        return template
    }
}

/**
 * 鍒犻櫎妯℃澘鐨?Use Case
 */
class DeleteTemplateUseCase(
    private val templateRepository: TemplateRepository
) {
    operator fun invoke(id: String): TemplateModel? {
        return templateRepository.deleteTemplate(id)
    }
}

/**
 * 鏇存柊妯℃澘鐨?Use Case
 */
class UpdateTemplateUseCase(
    private val templateRepository: TemplateRepository
) {
    operator fun invoke(template: TemplateModel) {
        templateRepository.updateTemplate(template)
    }
}

/**
 * 鏍规嵁 ID 鑾峰彇妯℃澘鐨?Use Case
 */
class GetTemplateByIdUseCase(
    private val templateRepository: TemplateRepository
) {
    operator fun invoke(id: String): TemplateModel? {
        return templateRepository.getTemplateById(id)
    }
}

/**
 * 鑾峰彇褰撳墠婵€娲绘ā鏉跨殑 Use Case
 */
class GetActiveTemplateUseCase(
    private val templateRepository: TemplateRepository
) {
    operator fun invoke(): TemplateModel? {
        return templateRepository.getActiveTemplate()
    }
}

/**
 * 璁剧疆婵€娲绘ā鏉跨殑 Use Case
 */
class SetActiveTemplateUseCase(
    private val templateRepository: TemplateRepository
) {
    operator fun invoke(id: String) {
        templateRepository.setActiveTemplate(id)
    }
}

/**
 * 娓呯┖妯℃澘鎵弿鏁版嵁鐨?Use Case
 */
class ClearTemplateScansUseCase(
    private val templateRepository: TemplateRepository,
    private val updateTemplate: UpdateTemplateUseCase
) {
    operator fun invoke(id: String) {
        val template = templateRepository.getTemplateById(id)
        if (template != null) {
            updateTemplate(template.copy(scans = emptyList()))
        }
    }
}

/**
 * 鍒犻櫎妯℃澘涓寚瀹氭壂鎻忔暟鎹殑 Use Case
 */
class DeleteTemplateScanUseCase(
    private val templateRepository: TemplateRepository,
    private val updateTemplate: UpdateTemplateUseCase
) {
    operator fun invoke(templateId: String, scanId: String) {
        val template = templateRepository.getTemplateById(templateId)
        if (template != null) {
            val updatedScans = template.scans.filterNot { it.id == scanId }
            updateTemplate(template.copy(scans = updatedScans))
        }
    }
}

/**
 * 妯℃澘浠撳簱鎺ュ彛
 */
interface TemplateRepository {
    fun addTemplate(template: TemplateModel)
    fun deleteTemplate(id: String): TemplateModel?
    fun updateTemplate(template: TemplateModel)
    fun getTemplateById(id: String): TemplateModel?
    fun getActiveTemplate(): TemplateModel?
    fun setActiveTemplate(id: String)
    fun getAllTemplates(): List<TemplateModel>
    fun saveTemplates(templates: List<TemplateModel>, activeId: String?)
    fun loadTemplates(): Pair<List<TemplateModel>, String?>
}
