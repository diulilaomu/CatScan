package com.example.catscandemo.data.repository

import android.content.Context
import android.util.Log
import com.example.catscandemo.domain.model.TemplateModel
import com.example.catscandemo.domain.use_case.TemplateRepository
import com.example.catscandemo.ui.main.TemplateStorage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch

/**
 * 榛樿妯℃澘浠撳簱瀹炵幇
 * 璐熻矗妯℃澘鏁版嵁鐨勫瓨鍌ㄥ拰绠＄悊
 */
class DefaultTemplateRepository(
    private val context: Context
) : TemplateRepository {

    private data class SaveRequest(
        val templates: List<TemplateModel>,
        val activeTemplateId: String?
    )

    private var templates: MutableList<TemplateModel> = mutableListOf()
    private var activeTemplateId: String? = null
    private var initialized = false
    private val persistenceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val saveRequests = Channel<SaveRequest>(Channel.CONFLATED)

    init {
        initialize()
        persistenceScope.launch {
            for (request in saveRequests) {
                try {
                    TemplateStorage.save(context, request.templates, request.activeTemplateId)
                } catch (error: Exception) {
                    Log.e("TemplateRepository", "保存模板失败", error)
                }
            }
        }
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
        // JSON 序列化和文件写入移出主线程；同一文件只需保留最新快照。
        saveRequests.trySend(SaveRequest(templates.toList(), activeTemplateId))
    }
}
