package com.example.catscandemo.data.repository

import android.content.Context
import com.example.catscandemo.domain.model.TemplateModel
import com.example.catscandemo.domain.use_case.TemplateRepository
import com.example.catscandemo.ui.main.TemplateStorage

/**
 * 默认模板仓库实现
 * 负责模板数据的存储和管理
 */
class DefaultTemplateRepository(
    private val context: Context
) : TemplateRepository {

    private var templates: MutableList<TemplateModel> = mutableListOf()
    private var activeTemplateId: String? = null
    private var initialized = false

    init {
        initialize()
    }

    private fun initialize() {
        if (!initialized) {
            val (loadedTemplates, loadedActiveId) = TemplateStorage.load(context)
            templates = loadedTemplates.toMutableList()
            activeTemplateId = loadedActiveId
            initialized = true
        }
    }

    override fun addTemplate(template: TemplateModel) {
        templates.add(0, template)
        saveTemplates()
    }

    override fun deleteTemplate(id: String): TemplateModel? {
        val deletedTemplate = templates.firstOrNull { it.id == id }
        templates.removeAll { it.id == id }
        if (activeTemplateId == id) {
            activeTemplateId = templates.firstOrNull()?.id
        }
        saveTemplates()
        return deletedTemplate
    }

    override fun updateTemplate(template: TemplateModel) {
        val index = templates.indexOfFirst { it.id == template.id }
        if (index != -1) {
            templates[index] = template
            saveTemplates()
        }
    }

    override fun getTemplateById(id: String): TemplateModel? {
        return templates.firstOrNull { it.id == id }
    }

    override fun getActiveTemplate(): TemplateModel? {
        return activeTemplateId?.let { getTemplateById(it) }
    }

    override fun setActiveTemplate(id: String) {
        activeTemplateId = id
        saveTemplates()
    }

    override fun getAllTemplates(): List<TemplateModel> {
        return templates
    }

    override fun saveTemplates(templates: List<TemplateModel>, activeId: String?) {
        this.templates = templates.toMutableList()
        this.activeTemplateId = activeId
        saveTemplates()
    }

    override fun loadTemplates(): Pair<List<TemplateModel>, String?> {
        initialize()
        return Pair(templates, activeTemplateId)
    }

    private fun saveTemplates() {
        TemplateStorage.save(context, templates, activeTemplateId)
    }
}
