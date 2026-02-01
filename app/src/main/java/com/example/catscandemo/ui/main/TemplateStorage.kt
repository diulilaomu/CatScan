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
            name = obj.optString("name", "未命名模板"),
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
        obj.put("text", s.text)
        obj.put("timestamp", s.timestamp)
        obj.put("operator", s.operator)
        obj.put("campus", s.campus)
        obj.put("building", s.building)
        obj.put("floor", s.floor)
        obj.put("room", s.room)
        return obj
    }

    private fun scanFromJson(obj: JSONObject): ScanData {
        return ScanData(
            text = obj.optString("text", ""),
            timestamp = obj.optLong("timestamp", System.currentTimeMillis()),
            operator = obj.optString("operator", ""),
            campus = obj.optString("campus", ""),
            building = obj.optString("building", ""),
            floor = obj.optString("floor", ""),
            room = obj.optString("room", "")
        )
    }
}
// ===================== 识别结果（ScanResult）离线存储（同文件，不新增文件） =====================
object ScanHistoryStorage {
    private const val FILE_NAME = "scan_history.json"

    data class Loaded(
        val items: List<ScanResult>
    )

    fun load(context: Context): Loaded {
        return try {
            val file = File(context.filesDir, FILE_NAME)
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

    fun save(context: Context, items: List<ScanResult>) {
        val root = JSONObject()
        val arr = JSONArray()
        items.forEach { arr.put(toJson(it)) }
        root.put("items", arr)

        File(context.filesDir, FILE_NAME).writeText(root.toString(), Charsets.UTF_8)
    }

    private fun toJson(item: ScanResult): JSONObject {
        val obj = JSONObject()
        obj.put("id", item.id)
        obj.put("index", item.index)
        obj.put("text", item.scanData.text)
        obj.put("operator", item.scanData.operator)
        obj.put("timestamp", item.scanData.timestamp)
        obj.put("uploaded", item.uploaded)
        obj.put("templateId", item.scanData.templateId) // ✅ 新增

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
        val scanData = ScanData(
            text = obj.optString("text", ""),
            timestamp = obj.optLong("timestamp", System.currentTimeMillis()),
            operator = obj.optString("operator", "unknown"),
            campus = areaObj.optString("campus", ""),
            building = areaObj.optString("building", ""),
            floor = areaObj.optString("floor", ""),
            room = areaObj.optString("room", ""),
            templateId = obj.optString("templateId", "") // ✅ 新增
        )

        return ScanResult(
            id = obj.optLong("id", System.currentTimeMillis()),
            index = obj.optInt("index", 0),
            scanData = scanData,
            uploaded = obj.optBoolean("uploaded", false)
        )
    }
}

// ===================== 设置离线存储 =====================
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
            // 保存失败时静默处理
        }
    }
}

