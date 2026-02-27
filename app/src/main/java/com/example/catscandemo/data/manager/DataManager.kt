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
 * 鏁版嵁绠＄悊涓績
 * 缁熶竴绠＄悊鎵€鏈夋暟鎹殑娣诲姞銆佸垹闄ゃ€佷慨鏀瑰拰鍚屾鎿嶄綔
 * 纭繚妯℃澘鏁版嵁鍜屾壂鎻忔暟鎹繚鎸佷竴鑷? */
class DataManager(
    val scanUseCases: ScanUseCases,
    private val templateUseCases: TemplateUseCases
) {

    // 妯℃澘鏁版嵁鐘舵€?    val templates = mutableStateListOf<TemplateModel>()
    var activeTemplateId by mutableStateOf<String?>(null)
    var activeTemplate by mutableStateOf<TemplateModel?>(null)

    // 鍗忕▼浣滅敤鍩?    private val coroutineScope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    /**
     * 鍒濆鍖栨暟鎹?     */
    fun initializeData() {
        loadTemplates()
    }

    /**
     * 鍔犺浇妯℃澘鏁版嵁
     */
    private fun loadTemplates() {
        val (loadedTemplates, activeId) = templateUseCases.loadTemplates.invoke()
        templates.clear()
        templates.addAll(loadedTemplates)
        activeTemplateId = activeId
        activeTemplate = activeId?.let { templateUseCases.getTemplateById(it) }

        // 鍚屾妯℃澘涓殑鎵弿鏁版嵁鍒扮粨鏋滃垪琛?        syncTemplateScansToResults()

        if (activeTemplateId == null && templates.isNotEmpty()) {
            setActiveTemplate(templates.first().id)
        }
    }
    
    /**
     * 鍚屾妯℃澘涓殑鎵弿鏁版嵁鍒扮粨鏋滃垪琛?     * 杩欐槸涓€涓崟鍚戝悓姝ワ細浠庢ā鏉?-> 鍒版壂鎻忕粨鏋滃垪琛?     */
    private fun syncTemplateScansToResults() {
        // 纭繚褰撳墠妯℃澘ID宸茶缃?        if (activeTemplateId != null) {
            // 鍔犺浇褰撳墠妯℃澘
            val currentTemplate = templateUseCases.getTemplateById(activeTemplateId!!)
            if (currentTemplate != null) {
                // 鐩存帴鏇挎崲鏁翠釜鎵弿缁撴灉鍒楄〃锛屼繚鎸佷笌妯℃澘鏁版嵁鐨勪竴鑷存€?                // 閫氳繃鐩存帴璧嬪€肩殑鏂瑰紡閬垮厤閲嶆柊鐢熸垚ID瀵艰嚧鐨勬暟鎹笉涓€鑷?                val scanResults = currentTemplate.scans.mapIndexed { index, scanData ->
                    // 浣跨敤scanData.id鐨刪ash浣滀负ScanResult鐨刬d锛岀‘淇濆彲杩借釜鎬?                    ScanResult(
                        id = index.toLong() + 1,  // 浣跨敤绠€鍗曠殑閫掑ID锛屼笌妯℃澘椤哄簭淇濇寔涓€鑷?                        index = index + 1,
                        scanData = scanData,
                        uploaded = scanData.uploaded
                    )
                }
                scanUseCases.replaceAll.invoke(scanResults)
            }
        }
    }

    /**
     * 淇濆瓨妯℃澘鏁版嵁
     */
    private fun saveTemplates() {
        val list = templates.toList()
        val active = activeTemplateId
        templateUseCases.saveTemplates.invoke(list, active)
    }

    /**
     * 娣诲姞妯℃澘
     */
    fun addTemplate(name: String): TemplateModel {
        val template = templateUseCases.addTemplate(name)
        templates.add(0, template)
        setActiveTemplate(template.id)
        saveTemplates()
        return template
    }

    /**
     * 鍒犻櫎妯℃澘
     */
    fun deleteTemplate(id: String): TemplateModel? {
        val wasActive = (activeTemplateId == id)
        val deletedTemplate = templateUseCases.deleteTemplate(id)

        val idx = templates.indexOfFirst { it.id == id }
        if (idx != -1) templates.removeAt(idx)

        if (wasActive) {
            // 鍒犵殑鏄綋鍓嶆ā鏉匡細灏濊瘯鎶?active 鍒囧埌绗竴涓紱濡傛灉娌℃湁妯℃澘浜嗗垯涓?null
            activeTemplateId = templates.firstOrNull()?.id
            activeTemplate = activeTemplateId?.let { templateUseCases.getTemplateById(it) }

            // 娓呯┖璇嗗埆缁撴灉锛堝叏閮級
            scanUseCases.clearAllScans.invoke()
        } else {
            // 鍒犵殑涓嶆槸褰撳墠妯℃澘锛氬彧鍒犻櫎璇ユā鏉跨殑璇嗗埆缁撴灉
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
     * 鏇存柊妯℃澘
     */
    fun updateTemplate(updated: TemplateModel) {
        templateUseCases.updateTemplate(updated)

        // 浣跨敤removeAt鍜宎dd鏉ヨЕ鍙戠姸鎬佹洿鏂?        val idx = templates.indexOfFirst { it.id == updated.id }
        if (idx != -1) {
            templates.removeAt(idx)
            templates.add(idx, updated)
        }

        // 濡傛灉鏇存柊鐨勬槸褰撳墠娲诲姩妯℃澘锛屾洿鏂癮ctiveTemplate鐘舵€佸苟閲嶆柊鍔犺浇鏁版嵁
        if (activeTemplateId == updated.id) {
            activeTemplate = updated
            // 閲嶆柊鍔犺浇褰撳墠妯℃澘鐨勬暟鎹紝纭繚缁撴灉鍒楄〃鍚屾
            scanUseCases.setCurrentTemplateId(updated.id)
            // 鍚屾妯℃澘涓殑鎵弿鏁版嵁鍒扮粨鏋滃垪琛?            syncTemplateScansToResults()
        }

        saveTemplates()
    }

    /**
     * 璁剧疆婵€娲绘ā鏉?     */
    fun setActiveTemplate(id: String) {
        activeTemplateId = id
        val t = templateUseCases.getTemplateById(id)
        activeTemplate = t
        saveTemplates()
        // 鉁?鍏抽敭锛氬繀椤诲厛璁剧疆 currentTemplateId锛岀劧鍚庡啀璋冪敤 syncTemplateScansToResults()
        // 杩欐牱 Repository 浼氬姞杞芥纭殑妯℃澘鏁版嵁鏂囦欢
        scanUseCases.setCurrentTemplateId(id)
        // 鍚屾妯℃澘涓殑鎵弿鏁版嵁鍒扮粨鏋滃垪琛?        syncTemplateScansToResults()
    }
    
    /**
     * 娓呴櫎婵€娲绘ā鏉匡紙璁剧疆涓烘棤妯℃澘锛?     */
    fun clearActiveTemplate() {
        activeTemplateId = null
        activeTemplate = null
        saveTemplates()
        // 鉁?鍏抽敭锛氬繀椤诲厛璁剧疆 setCurrentTemplateId(null)锛屽啀璋冪敤 syncTemplateScansToResults
        // 杩欐牱鎵嶈兘鍔犺浇鏃犳ā鏉跨姸鎬佷笅鐨勬暟鎹?        scanUseCases.setCurrentTemplateId(null)
        // 鍚屾妯℃澘涓殑鎵弿鏁版嵁鍒扮粨鏋滃垪琛紙姝ゆ椂搴旇鏄┖鐨勶級
        syncTemplateScansToResults()
    }

    /**
     * 娓呯┖妯℃澘鎵弿鏁版嵁
     */
    fun clearTemplateScans(id: String) {
        templateUseCases.clearTemplateScans(id)
        
        // 鍚屾椂娓呯┖鎵弿缁撴灉涓妯℃澘鐨勬墍鏈夋暟鎹?        val allScans = scanUseCases.getAllScans.invoke()
        val scansToClear = allScans.filter { it.scanData.templateId == id }
        scansToClear.forEach { scanUseCases.deleteScan(it.id) }

        // 鏇存柊妯℃澘鏁版嵁锛岀‘淇濇暟鎹悓姝?        val idx = templates.indexOfFirst { it.id == id }
        if (idx != -1) {
            val template = templates[idx]
            val updatedTemplate = template.copy(scans = emptyList())
            templates.removeAt(idx)
            templates.add(idx, updatedTemplate)

            // 濡傛灉鏄綋鍓嶆縺娲荤殑妯℃澘锛屼篃鏇存柊activeTemplate
            if (activeTemplateId == id) {
                activeTemplate = updatedTemplate
            }
        }

        saveTemplates()
    }

    /**
     * 鍒犻櫎妯℃澘涓殑鎵弿鏁版嵁
     */
    fun deleteTemplateScan(id: String, scanId: String) {
        templateUseCases.deleteTemplateScan(id, scanId)
        
        // 鍚屾椂浠庢壂鎻忕粨鏋滀腑鍒犻櫎璇ユ暟鎹?        val allScans = scanUseCases.getAllScans.invoke()
        val scanToDelete = allScans.find { it.scanData.id == scanId }
        if (scanToDelete != null) {
            scanUseCases.deleteScan(scanToDelete.id)
        }

        // 鏇存柊妯℃澘鏁版嵁锛岀‘淇濇暟鎹悓姝?        val idx = templates.indexOfFirst { it.id == id }
        if (idx != -1) {
            val template = templates[idx]
            val updatedTemplate = template.copy(
                scans = template.scans.filterNot { it.id == scanId }
            )
            templates.removeAt(idx)
            templates.add(idx, updatedTemplate)

            // 濡傛灉鏄綋鍓嶆縺娲荤殑妯℃澘锛屼篃鏇存柊activeTemplate
            if (activeTemplateId == id) {
                activeTemplate = updatedTemplate
            }
        }

        saveTemplates()
    }

    /**
     * 鍚戝綋鍓嶆ā鏉挎坊鍔犳壂鎻忔暟鎹?     * 娉ㄦ剰锛歴canData 宸茬粡閫氳繃 addScan() 娣诲姞鍒?ScanRepository 浜?     * 杩欓噷鍙渶瑕佸悓姝ュ埌 TemplateModel 浠ヤ繚璇佹ā鏉挎寔涔呭寲
     */
    fun addScanToActiveTemplate(scanData: ScanData): ScanData {
        val t = activeTemplate ?: throw IllegalStateException("No active template")
        
        // 纭繚templateId鍜宼emplateName姝ｇ‘璁剧疆
        val updatedScanData = scanData.copy(
            templateId = t.id,
            templateName = t.name
        )
        
        // 鏇存柊妯℃澘鏁版嵁锛圫canRepository 宸茬粡閫氳繃 addScan() 鏇存柊浜嗭級
        val updatedTemplate = t.copy(
            scans = listOf(updatedScanData) + t.scans
        )
        updateTemplate(updatedTemplate)
        
        // 鍚屾褰撳墠婵€娲绘ā鏉跨殑寮曠敤
        activeTemplate = updatedTemplate

        // 淇濆瓨鏁版嵁锛岀‘淇濇暟鎹悓姝?        saveTemplates()

        return updatedScanData
    }

    /**
     * 鍒犻櫎鎵弿鏁版嵁
     */
    fun deleteScan(id: Long): ScanResult? {
        val deleted = scanUseCases.deleteScan(id)

        // 鍚屾鍒版ā鏉挎暟鎹細浠庡搴旀ā鏉跨殑 scans 涓垹闄ゅ悓涓€鏉★紙鎸?templateId + 鏁版嵁id 鍖归厤锛?        if (deleted != null && deleted.scanData.templateId.isNotBlank()) {
            // 鏇存柊妯℃澘涓殑scans鍒楄〃
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
                
                // 濡傛灉鍒犻櫎鐨勬槸褰撳墠婵€娲绘ā鏉跨殑鏁版嵁锛屽悓姝ctiveTemplate
                if (activeTemplateId == templateId) {
                    activeTemplate = updatedTemplate
                }
            }
            
            saveTemplates()
        }

        return deleted
    }

    /**
     * 鏇存柊鎵弿鏁版嵁
     */
    fun updateScan(id: Long, scanData: ScanData) {
        scanUseCases.updateScan(id, scanData)

        // 鍚屾鍒版ā鏉挎暟鎹細鏇存柊瀵瑰簲妯℃澘涓殑鎵弿鏁版嵁
        if (scanData.templateId.isNotBlank()) {
            val templateId = scanData.templateId
            val template = templateUseCases.getTemplateById(templateId)
            if (template != null) {
                val updatedScans = template.scans.map {
                    if (it.id == scanData.id) scanData else it
                }
                val updatedTemplate = template.copy(scans = updatedScans)
                updateTemplate(updatedTemplate)
                
                // 濡傛灉鏇存柊鐨勬槸褰撳墠娲诲姩妯℃澘鐨勬暟鎹紝鍚屾activeTemplate
                if (activeTemplateId == templateId) {
                    activeTemplate = updatedTemplate
                }
            }
        }
        
        // 淇濆瓨鏁版嵁
        saveTemplates()
    }

    /**
     * 鑾峰彇鎵€鏈夋壂鎻忔暟鎹?     */
    fun getAllScans(): List<ScanResult> {
        return scanUseCases.getAllScans.invoke()
    }

    /**
     * 娓呯┖鎵€鏈夋壂鎻忔暟鎹?     */
    fun clearAllScans() {
        scanUseCases.clearAllScans.invoke()
        
        // 娓呯┖鎵€鏈夋ā鏉跨殑鎵弿鏁版嵁 - 浣跨敤map鍒涘缓鏇存柊鍚庣殑鍒楄〃閬垮厤骞跺彂淇敼闂
        val updatedTemplates = templates.map { template ->
            template.copy(scans = emptyList())
        }
        templates.clear()
        templates.addAll(updatedTemplates)
        
        // 鏇存柊褰撳墠婵€娲绘ā鏉跨殑寮曠敤
        activeTemplate = activeTemplate?.copy(scans = emptyList())
        
        saveTemplates()
    }

    /**
     * 鎵归噺鎿嶄綔锛氭坊鍔犲涓壂鎻忔暟鎹?     */
    fun addMultipleScans(scanDataList: List<ScanData>) {
        coroutineScope.launch {
            withContext(Dispatchers.IO) {
                scanDataList.forEach {scanData ->
                    try {
                        addScanToActiveTemplate(scanData)
                    } catch (e: Exception) {
                        android.util.Log.w("DataManager", "鎵归噺娣诲姞澶辫触: ${e.message}")
                    }
                }
            }
        }
    }

    /**
     * 鎵归噺鎿嶄綔锛氬垹闄ゅ涓壂鎻忔暟鎹?     */
    fun deleteMultipleScans(ids: List<Long>) {
        coroutineScope.launch {
            withContext(Dispatchers.IO) {
                ids.forEach {id ->
                    try {
                        deleteScan(id)
                    } catch (e: Exception) {
                        android.util.Log.w("DataManager", "鎵归噺鍒犻櫎澶辫触: ${e.message}")
                    }
                }
            }
        }
    }

    /**
     * 娣诲姞鎵弿鏁版嵁
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
        // 鉁?鍏抽敭锛氱‘淇?Repository 鐨?currentTemplateId 涓庤娣诲姞鐨?scanData.templateId 鍖归厤
        // 鍚﹀垯鏁版嵁浼氳淇濆瓨鍒伴敊璇殑鏂囦欢涓紝瀵艰嚧鏃犳ā鏉垮拰鏈夋ā鏉跨殑鏁版嵁娣锋潅
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
