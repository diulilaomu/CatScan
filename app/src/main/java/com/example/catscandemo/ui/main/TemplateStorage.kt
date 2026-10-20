package com.example.catscandemo.ui.main

import android.content.Context
import android.util.Log
import com.example.catscandemo.domain.model.MarkMode
import com.example.catscandemo.domain.model.ScanData
import com.example.catscandemo.domain.model.ScanResult
import com.example.catscandemo.domain.model.SeqConfig
import com.example.catscandemo.domain.model.TemplateMode
import com.example.catscandemo.domain.model.TemplateModel
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/**
 * 原子写入：先写临时文件再改名，进程被杀/断电时不会留下半截 JSON。
 */
internal fun writeTextAtomically(file: File, text: String) {
    val tmp = File(file.parentFile, file.name + ".tmp")
    tmp.writeText(text, Charsets.UTF_8)
    if (tmp.renameTo(file)) return
    // 个别文件系统在目标已存在时 rename 失败，退回先删后改名
    file.delete()
    if (tmp.renameTo(file)) return
    tmp.delete()
    file.writeText(text, Charsets.UTF_8)
}

/**
 * JSON 损坏时把原文件改名保留，避免下次保存把最后的恢复机会覆盖掉。
 */
internal fun backupCorruptedFile(file: File) {
    try {
        if (file.exists()) {
            val backup = File(file.parentFile, file.name + ".corrupt")
            backup.delete()
            if (!file.renameTo(backup)) {
                Log.w("TemplateStorage", "备份损坏文件失败: ${file.name}")
            }
        }
    } catch (e: Exception) {
        Log.w("TemplateStorage", "备份损坏文件异常: ${file.name}, ${e.message}")
    }
}

/**
 * 旧版记录缺失 id 时的兜底：由关键字段确定性推导，
 * 保证每次加载生成同一 id——否则 PC 端按 id 去重会失效（补传产生重复）。
 */
private fun legacyScanId(templateId: String, text: String, timestamp: Long): String {
    val key = "$templateId|$text|$timestamp"
    return "legacy-" + Integer.toHexString(key.hashCode())
}

object TemplateStorage {
    private const val FILE_NAME = "templates.json"

    data class Loaded(
        val templates: List<TemplateModel>,
        val activeId: String?
    )

    fun load(context: Context): Loaded {
        val file = File(context.filesDir, FILE_NAME)
        val text = try {
            if (!file.exists()) return Loaded(emptyList(), null)
            file.readText(Charsets.UTF_8)
        } catch (e: Exception) {
            Log.w("TemplateStorage", "读取模板文件失败: ${e.message}")
            backupCorruptedFile(file)
            return Loaded(emptyList(), null)
        }
        if (text.isBlank()) return Loaded(emptyList(), null)

        return try {
            val root = JSONObject(text)
            val activeId = root.optString("activeTemplateId", "").ifBlank { null }

            val arr = root.optJSONArray("templates") ?: JSONArray()
            val list = buildList {
                for (i in 0 until arr.length()) {
                    add(templateFromJson(arr.getJSONObject(i)))
                }
            }

            // 单一数据源：扫码记录只落在 scan_history_{id}.json，templates.json 只存模板配置。
            // 旧版内嵌在 templates.json 里的 scans 以内嵌为准回写 history 文件
            // （与旧运行时 syncTemplateScansToResults 覆盖 history 的语义一致），
            // 随后重存一次剥离后的模板文件，避免每次扫码全量重序列化所有模板的所有记录。
            var migrated = false
            val hydrated = list.map { t ->
                if (t.scans.isNotEmpty()) {
                    seedHistoryFromEmbedded(context, t)
                    migrated = true
                    t
                } else if (t.id.isBlank()) {
                    // 空白 id 会映射到 no_template 文件，绝不能当成该模板的历史来水合
                    t
                } else {
                    t.copy(scans = ScanHistoryStorage.load(context, t.id).items.map { it.scanData })
                }
            }
            if (migrated) {
                save(context, hydrated, activeId)
            }
            Loaded(hydrated, activeId)
        } catch (e: Exception) {
            Log.w("TemplateStorage", "模板文件解析失败: ${e.message}")
            backupCorruptedFile(file)
            Loaded(emptyList(), null)
        }
    }

    /** 旧格式迁移：把内嵌的扫码记录写入对应模板的 history 文件 */
    private fun seedHistoryFromEmbedded(context: Context, template: TemplateModel) {
        try {
            val usedIds = HashSet<Long>()
            val items = template.scans.mapIndexed { index, scanData ->
                var id = scanData.id.hashCode().toLong()
                while (!usedIds.add(id)) id++
                ScanResult(id = id, index = index + 1, scanData = scanData, uploaded = scanData.uploaded)
            }
            ScanHistoryStorage.save(context, template.id, items)
        } catch (e: Exception) {
            Log.w("TemplateStorage", "内嵌扫码记录回写历史文件失败: ${template.id}, ${e.message}")
        }
    }

    fun save(context: Context, templates: List<TemplateModel>, activeId: String?) {
        val root = JSONObject()
        root.put("activeTemplateId", activeId ?: "")

        val arr = JSONArray()
        templates.forEach { arr.put(templateToJson(it)) }
        root.put("templates", arr)

        writeTextAtomically(File(context.filesDir, FILE_NAME), root.toString())
    }

    private fun templateToJson(t: TemplateModel): JSONObject {
        val obj = JSONObject()
        obj.put("id", t.id)
        obj.put("name", t.name)
        obj.put("operator", t.operator)
        obj.put("campus", t.campus)
        obj.put("building", t.building)
        obj.put("maxFloor", t.maxFloor)
        obj.put("roomCountPerFloor", t.roomCountPerFloor)
        obj.put("mode", t.mode.name)
        obj.put("markMode", t.markMode.name)
        obj.put("nextSeq", t.nextSeq)
        obj.put("seqTagCursor", t.seqTagCursor)

        val seqObj = JSONObject()
        seqObj.put("prefix", t.seq.prefix)
        seqObj.put("suffix", t.seq.suffix)
        seqObj.put("chineseUpper", t.seq.chineseUpper)
        seqObj.put("start", t.seq.start)
        seqObj.put("descending", t.seq.descending)
        seqObj.put("padWidth", t.seq.padWidth)
        seqObj.put("autoPad", t.seq.autoPad)
        obj.put("seq", seqObj)

        val colorsObj = JSONObject()
        t.tagColors.forEach { (tag, idx) -> colorsObj.put(tag, idx) }
        obj.put("tagColors", colorsObj)
        obj.put("lastSelectedFloor", t.lastSelectedFloor)
        obj.put("lastSelectedTag", t.lastSelectedTag)

        val tags = JSONArray()
        t.tags.forEach { tags.put(it) }
        obj.put("tags", tags)

        val rooms = JSONArray()
        t.selectedRooms.forEach { rooms.put(it) }
        obj.put("selectedRooms", rooms)

        // 扫码记录不写入模板文件：单一数据源在 scan_history_{id}.json，
        // 模板文件保持小体积，扫码时无需全量重序列化
        return obj
    }

    private fun templateFromJson(obj: JSONObject): TemplateModel {
        val roomsArr = obj.optJSONArray("selectedRooms") ?: JSONArray()
        val rooms = buildList {
            for (i in 0 until roomsArr.length()) add(roomsArr.getString(i))
        }
        val maxFloor = obj.optInt("maxFloor", 1).coerceAtLeast(1)
        val roomCountPerFloor = obj.optInt("roomCountPerFloor", 1).coerceIn(1, 999)
        val validRooms = rooms.filter { code ->
            if (code.length < 3) {
                false
            } else {
                val floor = code.dropLast(2).toIntOrNull()
                val room = code.takeLast(2).toIntOrNull()
                floor != null &&
                        room != null &&
                        floor in 1..maxFloor &&
                        room in 1..roomCountPerFloor
            }
        }.distinct()

        // 标签：数量与单标签长度均不限（旧版 4 个/4 字限制已放开），去空白去重
        val tagsArr = obj.optJSONArray("tags") ?: JSONArray()
        val tags = buildList {
            for (i in 0 until tagsArr.length()) add(tagsArr.getString(i))
        }.map { it.trim() }
            .filter { it.isNotBlank() }
            .distinct()

        val scansArr = obj.optJSONArray("scans") ?: JSONArray()
        val scans = buildList {
            for (i in 0 until scansArr.length()) add(scanFromJson(scansArr.getJSONObject(i)))
        }


        // 序号模式配置（markMode/seq/nextSeq/seqTagCursor），旧文件缺省为房间号模式
        val seqObj = obj.optJSONObject("seq")
        val seqConfig = SeqConfig(
            prefix = seqObj?.optString("prefix") ?: "",
            suffix = seqObj?.optString("suffix") ?: "",
            chineseUpper = seqObj?.optBoolean("chineseUpper") ?: false,
            start = seqObj?.optInt("start") ?: 1,
            descending = seqObj?.optBoolean("descending") ?: false,
            padWidth = seqObj?.optInt("padWidth", 2) ?: 2,
            autoPad = seqObj?.optBoolean("autoPad") ?: false
        )

        // 标签自定义颜色：{标签名: 调色板色号}，只保留仍存在的标签
        val colorsObj = obj.optJSONObject("tagColors")
        val tagColors = buildMap {
            colorsObj?.keys()?.forEach { key ->
                colorsObj.optInt(key).let { if (key in tags) put(key, it) }
            }
        }

        return TemplateModel(
            id = obj.optString("id", ""),
            name = obj.optString("name", "未命名模板"),
            operator = obj.optString("operator", ""),
            campus = obj.optString("campus", ""),
            building = obj.optString("building", ""),
            maxFloor = maxFloor,
            roomCountPerFloor = roomCountPerFloor,
            mode = runCatching {
                TemplateMode.valueOf(obj.optString("mode", TemplateMode.LINEAR.name))
            }.getOrDefault(TemplateMode.LINEAR),
            markMode = runCatching {
                MarkMode.valueOf(obj.optString("markMode", MarkMode.ROOM.name))
            }.getOrDefault(MarkMode.ROOM),
            seq = seqConfig,
            nextSeq = obj.optInt("nextSeq", seqConfig.start),
            seqTagCursor = obj.optInt("seqTagCursor", 0),
            lastSelectedFloor = obj.optInt("lastSelectedFloor", 1)
                .coerceIn(1, maxFloor),
            lastSelectedTag = obj.optString("lastSelectedTag", "")
                .takeIf { it in tags } ?: "",
            tags = tags,
            tagColors = tagColors,
            selectedRooms = validRooms,
            scans = scans
        )
    }

    private fun scanFromJson(obj: JSONObject): ScanData {
        val templateId = obj.optString("templateId", "")
        val text = obj.optString("text", "")
        val timestamp = obj.optLong("timestamp", 0L)
        return ScanData(
            // 旧记录缺 id 时确定性推导，避免每次加载生成新 UUID 导致 PC 端重复
            id = obj.optString("id", "").ifBlank { legacyScanId(templateId, text, timestamp) },
            text = text,
            timestamp = timestamp,
            operator = obj.optString("operator", ""),
            campus = obj.optString("campus", ""),
            building = obj.optString("building", ""),
            floor = obj.optString("floor", ""),
            room = obj.optString("room", ""),
            tag = obj.optString("tag", ""),
            templateId = templateId,
            templateName = obj.optString("templateName", ""),
            uploaded = obj.optBoolean("uploaded", false)
        )
    }
}

object ScanHistoryStorage {
    private const val BASE_FILE_NAME = "scan_history_"
    private const val FILE_EXTENSION = ".json"

    data class Loaded(val items: List<ScanResult>)

    fun load(context: Context, templateId: String?): Loaded {
        val file = File(context.filesDir, getFileNameForTemplate(templateId))
        val text = try {
            if (!file.exists()) return Loaded(emptyList())
            file.readText(Charsets.UTF_8)
        } catch (e: Exception) {
            Log.w("ScanHistoryStorage", "读取扫码历史失败: ${file.name}, ${e.message}")
            backupCorruptedFile(file)
            return Loaded(emptyList())
        }
        if (text.isBlank()) return Loaded(emptyList())

        return try {
            val root = JSONObject(text)
            val arr = root.optJSONArray("items") ?: JSONArray()

            val list = buildList<ScanResult> {
                for (i in 0 until arr.length()) {
                    add(fromJson(arr.getJSONObject(i)))
                }
            }
            Loaded(list)
        } catch (e: Exception) {
            Log.w("ScanHistoryStorage", "扫码历史解析失败: ${file.name}, ${e.message}")
            backupCorruptedFile(file)
            Loaded(emptyList())
        }
    }

    fun save(context: Context, templateId: String?, items: List<ScanResult>) {
        val fileName = getFileNameForTemplate(templateId)
        val root = JSONObject()
        val arr = JSONArray()
        items.forEach { arr.put(toJson(it)) }
        root.put("items", arr)

        writeTextAtomically(File(context.filesDir, fileName), root.toString())
    }

    private fun getFileNameForTemplate(templateId: String?): String {
        return if (templateId.isNullOrBlank()) {
            "${BASE_FILE_NAME}no_template${FILE_EXTENSION}"
        } else {
            "${BASE_FILE_NAME}${templateId}${FILE_EXTENSION}"
        }
    }

    private fun toJson(item: ScanResult): JSONObject {
        val obj = JSONObject()
        obj.put("id", item.id)
        obj.put("index", item.index)
        obj.put("scanDataId", item.scanData.id)
        obj.put("text", item.scanData.text)
        obj.put("operator", item.scanData.operator)
        obj.put("timestamp", item.scanData.timestamp)
        obj.put("uploaded", item.uploaded)
        obj.put("templateId", item.scanData.templateId)
        obj.put("templateName", item.scanData.templateName)

        val area = JSONObject()
        area.put("campus", item.scanData.campus)
        area.put("building", item.scanData.building)
        area.put("floor", item.scanData.floor)
        area.put("room", item.scanData.room)
        area.put("tag", item.scanData.tag)
        obj.put("area", area)

        return obj
    }

    private fun fromJson(obj: JSONObject): ScanResult {
        val areaObj = obj.optJSONObject("area") ?: JSONObject()
        val templateId = obj.optString("templateId", "")
        val text = obj.optString("text", "")
        val timestamp = obj.optLong("timestamp", 0L)
        return ScanResult(
            id = obj.optLong("id", System.currentTimeMillis()),
            index = obj.optInt("index", 0),
            scanData = ScanData(
                // scanDataId 是模板 scans 里的记录 id（UUID）；旧文件没有该字段，
                // 退回 "id"（结果序号）仅为兼容，删除同步会走兜底匹配；
                // 两者都没有时确定性推导，避免每次加载生成新 id 导致 PC 端重复
                id = obj.optString("scanDataId", "")
                    .ifBlank { obj.optString("id", "") }
                    .ifBlank { legacyScanId(templateId, text, timestamp) },
                text = text,
                timestamp = timestamp,
                operator = obj.optString("operator", "unknown"),
                campus = areaObj.optString("campus", ""),
                building = areaObj.optString("building", ""),
                floor = areaObj.optString("floor", ""),
                room = areaObj.optString("room", ""),
                tag = areaObj.optString("tag", ""),
                templateId = templateId,
                templateName = obj.optString("templateName", ""),
                uploaded = obj.optBoolean("uploaded", false)
            ),
            uploaded = obj.optBoolean("uploaded", false)
        )
    }
}

object SettingsStorage {
    private const val FILE_NAME = "settings.json"

    data class Settings(
        // 自动复制按模式独立：文本默认关、模板默认开（旧单键迁移到模板模式值）
        val clipboardEnabledText: Boolean = false,
        val clipboardEnabledTemplate: Boolean = true,
        val duplicateScanEnabled: Boolean = true,
        val showBarcodeOverlay: Boolean = true,
        val channel1ScanFrameInterval: Int = 3,
        val channel2MinAreaScore: Double = 3.5,
        val channel2MinAspectScore: Double = 28.0,
        val channel2MinSolidityScore: Double = 10.0,
        val channel2MinGradScore: Double = 8.0,
        val workMode: String = "TEXT",
        val splitRatio: Float = 0.45f,
        val textAutoNewline: Boolean = true,
        val cameraSleepEnabled: Boolean = true,
        // 应用设置：主题（SYSTEM/LIGHT/DARK）与自定义导出目录（SAF 树 URI，空=默认下载目录）
        val themeMode: String = "SYSTEM",
        val exportTreeUri: String = "",
        // 摄像头开关状态（默认开启），退出后下次启动保持
        val cameraEnabled: Boolean = true
    )

    fun load(context: Context): Settings {
        val file = File(context.filesDir, FILE_NAME)
        val text = try {
            if (!file.exists()) return Settings()
            file.readText(Charsets.UTF_8)
        } catch (e: Exception) {
            Log.w("SettingsStorage", "读取设置失败: ${e.message}")
            backupCorruptedFile(file)
            return Settings()
        }
        if (text.isBlank()) return Settings()

        return try {
            val root = JSONObject(text)
            // v2 起镜头休眠默认开启；旧版本配置一律迁移为开启
            val version = root.optInt("settingsVersion", 1)
            // 自动复制迁移：旧单键"clipboardEnabled"的值归模板模式；文本模式固定默认关
            val legacyClipboard = root.optBoolean("clipboardEnabled", true)
            Settings(
                clipboardEnabledText = root.optBoolean("clipboardEnabledText", false),
                clipboardEnabledTemplate = root.optBoolean("clipboardEnabledTemplate", legacyClipboard),
                duplicateScanEnabled = root.optBoolean("duplicateScanEnabled", true),
                showBarcodeOverlay = root.optBoolean("showBarcodeOverlay", true),
                channel1ScanFrameInterval = root.optInt("channel1ScanFrameInterval", 3),
                channel2MinAreaScore = root.optDouble("channel2MinAreaScore", 3.5),
                channel2MinAspectScore = root.optDouble("channel2MinAspectScore", 28.0),
                channel2MinSolidityScore = root.optDouble("channel2MinSolidityScore", 10.0),
                channel2MinGradScore = root.optDouble("channel2MinGradScore", 8.0),
                workMode = root.optString("workMode", "TEXT"),
                splitRatio = root.optDouble("splitRatio", 0.45).toFloat()
                    .coerceIn(0.2f, 0.75f),
                textAutoNewline = root.optBoolean("textAutoNewline", true),
                cameraSleepEnabled = if (version < 2) {
                    true
                } else {
                    root.optBoolean("cameraSleepEnabled", true)
                },
                themeMode = root.optString("themeMode", "SYSTEM"),
                exportTreeUri = root.optString("exportTreeUri", ""),
                cameraEnabled = root.optBoolean("cameraEnabled", true)
            )
        } catch (e: Exception) {
            Log.w("SettingsStorage", "设置解析失败: ${e.message}")
            backupCorruptedFile(file)
            Settings()
        }
    }

    fun save(context: Context, settings: Settings) {
        try {
            val root = JSONObject()
            root.put("settingsVersion", 2)
            root.put("clipboardEnabledText", settings.clipboardEnabledText)
            root.put("clipboardEnabledTemplate", settings.clipboardEnabledTemplate)
            root.put("duplicateScanEnabled", settings.duplicateScanEnabled)
            root.put("showBarcodeOverlay", settings.showBarcodeOverlay)
            root.put("channel1ScanFrameInterval", settings.channel1ScanFrameInterval)
            root.put("channel2MinAreaScore", settings.channel2MinAreaScore)
            root.put("channel2MinAspectScore", settings.channel2MinAspectScore)
            root.put("channel2MinSolidityScore", settings.channel2MinSolidityScore)
            root.put("channel2MinGradScore", settings.channel2MinGradScore)
            root.put("workMode", settings.workMode)
            root.put("splitRatio", settings.splitRatio.toDouble())
            root.put("textAutoNewline", settings.textAutoNewline)
            root.put("cameraSleepEnabled", settings.cameraSleepEnabled)
            root.put("themeMode", settings.themeMode)
            root.put("exportTreeUri", settings.exportTreeUri)
            root.put("cameraEnabled", settings.cameraEnabled)

            writeTextAtomically(File(context.filesDir, FILE_NAME), root.toString())
        } catch (e: Exception) {
            Log.w("SettingsStorage", "保存设置失败: ${e.message}")
        }
    }
}
