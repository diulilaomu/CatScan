package com.example.catscandemo.domain.use_case

import com.example.catscandemo.domain.model.ScanData
import com.example.catscandemo.domain.model.TemplateModel

/**
 * 模板管理相关的 Use Case
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
 * 加载模板的 Use Case
 */
class LoadTemplatesUseCase(
    private val templateRepository: TemplateRepository
) {
    operator fun invoke(): Pair<List<TemplateModel>, String?> {
        return templateRepository.loadTemplates()
    }
}

/**
 * 保存模板的 Use Case
 */
class SaveTemplatesUseCase(
    private val templateRepository: TemplateRepository
) {
    operator fun invoke(templates: List<TemplateModel>, activeId: String?) {
        templateRepository.saveTemplates(templates, activeId)
    }
}

/**
 * 添加模板的 Use Case
 */
class AddTemplateUseCase(
    private val templateRepository: TemplateRepository
) {
    operator fun invoke(name: String): TemplateModel {
        val template = TemplateModel(
            name = name.trim().ifBlank { "未命名模板" },
            operator = "猫头枪",
            campus = "猫头校区",
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
 * 删除模板的 Use Case
 */
class DeleteTemplateUseCase(
    private val templateRepository: TemplateRepository
) {
    operator fun invoke(id: String): TemplateModel? {
        return templateRepository.deleteTemplate(id)
    }
}

/**
 * 更新模板的 Use Case
 */
class UpdateTemplateUseCase(
    private val templateRepository: TemplateRepository
) {
    operator fun invoke(template: TemplateModel) {
        templateRepository.updateTemplate(template)
    }
}

/**
 * 根据 ID 获取模板的 Use Case
 */
class GetTemplateByIdUseCase(
    private val templateRepository: TemplateRepository
) {
    operator fun invoke(id: String): TemplateModel? {
        return templateRepository.getTemplateById(id)
    }
}

/**
 * 获取当前激活模板的 Use Case
 */
class GetActiveTemplateUseCase(
    private val templateRepository: TemplateRepository
) {
    operator fun invoke(): TemplateModel? {
        return templateRepository.getActiveTemplate()
    }
}

/**
 * 设置激活模板的 Use Case
 */
class SetActiveTemplateUseCase(
    private val templateRepository: TemplateRepository
) {
    operator fun invoke(id: String) {
        templateRepository.setActiveTemplate(id)
    }
}

/**
 * 清空模板扫描数据的 Use Case
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
 * 删除模板中指定扫描数据的 Use Case
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
 * 模板仓库接口
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
