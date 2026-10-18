package com.example.catscandemo.ui.main

import android.content.Context
import android.util.Log
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.UUID

/**
 * 文本模式下的一个文本文档
 */
data class TextDoc(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val content: String = ""
)

/**
 * 文本文档离线存储：text_docs.json
 */
object TextDocsStorage {
    private const val FILE_NAME = "text_docs.json"

    data class Loaded(
        val docs: List<TextDoc>,
        val activeId: String?
    )

    fun load(context: Context): Loaded {
        val file = File(context.filesDir, FILE_NAME)
        val text = try {
            if (!file.exists()) return Loaded(emptyList(), null)
            file.readText(Charsets.UTF_8)
        } catch (e: Exception) {
            Log.w("TextDocsStorage", "读取文本文档失败: ${e.message}")
            backupCorruptedFile(file)
            return Loaded(emptyList(), null)
        }
        if (text.isBlank()) return Loaded(emptyList(), null)

        return try {
            val root = JSONObject(text)
            val activeId = root.optString("activeDocId", "").ifBlank { null }
            val arr = root.optJSONArray("docs") ?: JSONArray()
            val docs = buildList {
                for (i in 0 until arr.length()) {
                    val obj = arr.getJSONObject(i)
                    add(
                        TextDoc(
                            id = obj.optString("id", "").ifBlank { UUID.randomUUID().toString() },
                            name = obj.optString("name", "未命名文档"),
                            content = obj.optString("content", "")
                        )
                    )
                }
            }
            Loaded(docs, activeId)
        } catch (e: Exception) {
            Log.w("TextDocsStorage", "文本文档解析失败: ${e.message}")
            backupCorruptedFile(file)
            Loaded(emptyList(), null)
        }
    }

    fun save(context: Context, docs: List<TextDoc>, activeId: String?) {
        try {
            val root = JSONObject()
            root.put("activeDocId", activeId ?: "")
            val arr = JSONArray()
            docs.forEach { doc ->
                val obj = JSONObject()
                obj.put("id", doc.id)
                obj.put("name", doc.name)
                obj.put("content", doc.content)
                arr.put(obj)
            }
            root.put("docs", arr)
            writeTextAtomically(File(context.filesDir, FILE_NAME), root.toString())
        } catch (e: Exception) {
            Log.w("TextDocsStorage", "保存文本文档失败: ${e.message}")
        }
    }
}
