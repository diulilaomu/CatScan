package com.example.catscandemo.ui.main

import android.content.Context
import com.example.catscandemo.domain.model.ScanData
import com.example.catscandemo.domain.model.ScanResult
import com.example.catscandemo.domain.model.TemplateModel
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

object TemplateStorage {
    private const val FILE_NAME = "templates.json"

    data class Loaded(
        val templates: List<TemplateModel>,
        val activeId: String?
    )

    fun load(context: Context): Loaded {
        return try {
            val file = File(context.filesDir, FILE_NAME)
            if (!file.exists()) return Loaded(emptyList(), null)

            val text = file.readText(Charsets.UTF_8)
            if (text.isBlank()) return Loaded(emptyList(), null)

            val root = JSONObject(text)
            val activeId = root.optString("activeTemplateId", "").ifBlank { null }

            val arr = root.optJSONArray("templates") ?: JSONArray()
            val list = buildList {
                for (i in 0 until arr.length()) {
                    add(templateFromJson(arr.getJSONObject(i)))
                }
            }
            Loaded(list, activeId)
        } catch (_: Exception) {
            Loaded(emptyList(), null)
        }
    }

    fun save(context: Context, templates: List<TemplateModel>, activeId: String?) {
        val root = JSONObject()
        root.put("activeTemplateId", activeId ?: "")

        val arr = JSONArray()
        templates.forEach { arr.put(templateToJson(it)) }
        root.put("templates", arr)

        File(context.filesDir, FILE_NAME).writeText(root.toString(), Charsets.UTF_8)
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

        val rooms = JSONArray()
        t.selectedRooms.forEach { rooms.put(it) }
        obj.put("selectedRooms", rooms)

        val scans = JSONArray()
        t.scans.forEach { scans.put(scanToJson(it)) }
        obj.put("scans", scans)
        return obj
    }

    private fun templateFromJson(obj: JSONObject): TemplateModel {
        val roomsArr = obj.optJSONArray("selectedRooms") ?: JSONArray()
        val rooms = buildList {
            for (i in 0 until roomsArr.length()) add(roomsArr.getString(i))
        }

        val scansArr = obj.optJSONArray("scans") ?: JSONArray()
        val scans = buildList {
            for (i in 0 until scansArr.length()) add(scanFromJson(scansArr.getJSONObject(i)))
        }

        return TemplateModel(
            id = obj.optString("id", ""),
            name = obj.optString("name", "鏈懡鍚嶆ā鏉?),
            operator = obj.optString("operator", ""),
            campus = obj.optString("campus", ""),
            building = obj.optString("building", ""),
            maxFloor = obj.optInt("maxFloor", 1).coerceAtLeast(1),
            roomCountPerFloor = obj.optInt("roomCountPerFloor", 1).coerceAtLeast(1),
            selectedRooms = rooms,
            scans = scans
        )
    }

    private fun scanToJson(s: ScanData): JSONObject {
        val obj = JSONObject()
        obj.put("id", s.id)  // 鉁?淇锛氭坊鍔爄d瀛楁搴忓垪鍖栵紝纭繚鏁版嵁鍙拷韪?
        obj.put("text", s.text)
        obj.put("timestamp", s.timestamp)
        obj.put("operator", s.operator)
        obj.put("campus", s.campus)
        obj.put("building", s.building)
        obj.put("floor", s.floor)
        obj.put("room", s.room)
        obj.put("templateId", s.templateId)  // 鉁?娣诲姞templateId瀛楁
        obj.put("templateName", s.templateName)  // 鉁?娣诲姞templateName瀛楁
        obj.put("uploaded", s.uploaded)  // 鉁?娣诲姞uploaded鐘舵€佸瓧娈?
        return obj
    }

    private fun scanFromJson(obj: JSONObject): ScanData {
        return ScanData(
            id = obj.optString("id", java.util.UUID.randomUUID().toString()),  // 鉁?淇锛氳鍙杋d瀛楁锛屾彁渚涢粯璁ゅ€?
            text = obj.optString("text", ""),
            timestamp = obj.optLong("timestamp", System.currentTimeMillis()),
            operator = obj.optString("operator", ""),
            campus = obj.optString("campus", ""),
            building = obj.optString("building", ""),
            floor = obj.optString("floor", ""),
            room = obj.optString("room", ""),
            templateId = obj.optString("templateId", ""),  // 鉁?璇诲彇templateId瀛楁
            templateName = obj.optString("templateName", ""),  // 鉁?璇诲彇templateName瀛楁
            uploaded = obj.optBoolean("uploaded", false)  // 鉁?璇诲彇uploaded鐘舵€佸瓧娈?
        )
    }
}
// ===================== 璇嗗埆缁撴灉锛圫canResult锛夌绾垮瓨鍌?=====================
object ScanHistoryStorage {
    private const val BASE_FILE_NAME = "scan_history_"
    private const val FILE_EXTENSION = ".json"

    data class Loaded(
        val items: List<ScanResult>
    )

    fun load(context: Context, templateId: String?): Loaded {
        return try {
            val fileName = getFileNameForTemplate(templateId)
            val file = File(context.filesDir, fileName)
            if (!file.exists()) return Loaded(emptyList())

            val text = file.readText(Charsets.UTF_8)
            if (text.isBlank()) return Loaded(emptyList())

            val root = JSONObject(text)
            val arr = root.optJSONArray("items") ?: JSONArray()

            val list = buildList<ScanResult> {
                for (i in 0 until arr.length()) {
                    val obj = arr.getJSONObject(i)
                    add(fromJson(obj))
                }
            }
            Loaded(list)
        } catch (_: Exception) {
            Loaded(emptyList())
        }
    }

    fun save(context: Context, templateId: String?, items: List<ScanResult>) {
        val fileName = getFileNameForTemplate(templateId)
        val root = JSONObject()
        val arr = JSONArray()
        items.forEach { arr.put(toJson(it)) }
        root.put("items", arr)

        File(context.filesDir, fileName).writeText(root.toString(), Charsets.UTF_8)
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
        val uploaded = item.uploaded || item.scanData.uploaded
        obj.put("id", item.id)
        obj.put("index", item.index)
        obj.put("scanDataId", item.scanData.id)
        obj.put("text", item.scanData.text)
        obj.put("operator", item.scanData.operator)
        obj.put("timestamp", item.scanData.timestamp)
        obj.put("uploaded", uploaded)
        obj.put("templateId", item.scanData.templateId) // 鉁?鏂板
        obj.put("templateName", item.scanData.templateName)

        val area = JSONObject()
        area.put("campus", item.scanData.campus)
        area.put("building", item.scanData.building)
        area.put("floor", item.scanData.floor)
        area.put("room", item.scanData.room)
        obj.put("area", area)

        return obj
    }

    private fun fromJson(obj: JSONObject): ScanResult {
        val areaObj = obj.optJSONObject("area") ?: JSONObject()
        val uploaded = obj.optBoolean("uploaded", false)
        val scanData = ScanData(
            id = obj.optString("scanDataId", java.util.UUID.randomUUID().toString()),
            text = obj.optString("text", ""),
            timestamp = obj.optLong("timestamp", System.currentTimeMillis()),
            operator = obj.optString("operator", "unknown"),
            campus = areaObj.optString("campus", ""),
            building = areaObj.optString("building", ""),
            floor = areaObj.optString("floor", ""),
            room = areaObj.optString("room", ""),
            templateId = obj.optString("templateId", ""), // 鉁?鏂板
            templateName = obj.optString("templateName", ""),
            uploaded = uploaded
        )

        return ScanResult(
            id = obj.optLong("id", System.currentTimeMillis()),
            index = obj.optInt("index", 0),
            scanData = scanData,
            uploaded = uploaded
        )
    }
}

// ===================== 璁剧疆绂荤嚎瀛樺偍 =====================
object SettingsStorage {
    private const val FILE_NAME = "settings.json"

    data class Settings(
        val clipboardEnabled: Boolean = true,
        val duplicateScanEnabled: Boolean = true
    )

    fun load(context: Context): Settings {
        return try {
            val file = File(context.filesDir, FILE_NAME)
            if (!file.exists()) return Settings()

            val text = file.readText(Charsets.UTF_8)
            if (text.isBlank()) return Settings()

            val root = JSONObject(text)
            Settings(
                clipboardEnabled = root.optBoolean("clipboardEnabled", true),
                duplicateScanEnabled = root.optBoolean("duplicateScanEnabled", true)
            )
        } catch (_: Exception) {
            Settings()
        }
    }

    fun save(context: Context, settings: Settings) {
        try {
            val root = JSONObject()
            root.put("clipboardEnabled", settings.clipboardEnabled)
            root.put("duplicateScanEnabled", settings.duplicateScanEnabled)

            File(context.filesDir, FILE_NAME).writeText(root.toString(), Charsets.UTF_8)
        } catch (_: Exception) {
            // 淇濆瓨澶辫触鏃堕潤榛樺鐞?
        }
    }
}

