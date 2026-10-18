package com.example.catscandemo.ui.components

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.DocumentsContract
import android.provider.MediaStore
import com.example.catscandemo.ui.main.SettingsStorage
import java.io.File

/**
 * 保存文本导出文件。默认写入系统下载目录 Downloads/CatScan/；
 * 若用户在设置里选择了自定义导出目录（SAF 树 URI），则写入该目录。
 * 成功返回 true。
 */
internal fun saveTextToDownloads(
    context: Context,
    fileName: String,
    mime: String,
    content: String
): Boolean {
    val treeUri = runCatching {
        SettingsStorage.load(context).exportTreeUri
    }.getOrDefault("")
    if (treeUri.isNotBlank()) {
        return saveTextToTree(context, treeUri, fileName, mime, content)
    }
    return try {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
            val downloadsDir = context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS)
                ?: return false
            val catScanDir = File(downloadsDir, "CatScan").apply { mkdirs() }
            File(catScanDir, fileName).writeText(content, Charsets.UTF_8)
            return true
        }

        val resolver = context.contentResolver
        val values = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, fileName)
            put(MediaStore.MediaColumns.MIME_TYPE, mime)
            put(
                MediaStore.MediaColumns.RELATIVE_PATH,
                Environment.DIRECTORY_DOWNLOADS + "/CatScan"
            )
        }
        val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
            ?: return false
        resolver.openOutputStream(uri)?.use { os ->
            os.write(content.toByteArray(Charsets.UTF_8))
        } ?: return false
        true
    } catch (_: Exception) {
        false
    }
}

/** 写入用户选择的 SAF 目录 */
private fun saveTextToTree(
    context: Context,
    treeUri: String,
    fileName: String,
    mime: String,
    content: String
): Boolean {
    return try {
        val resolver = context.contentResolver
        val tree = Uri.parse(treeUri)
        val treeDocId = DocumentsContract.getTreeDocumentId(tree)
        val parentUri = DocumentsContract.buildDocumentUriUsingTree(tree, treeDocId)
        val newUri = DocumentsContract.createDocument(resolver, parentUri, mime, fileName)
            ?: return false
        resolver.openOutputStream(newUri, "wt")?.use { os ->
            os.write(content.toByteArray(Charsets.UTF_8))
        } ?: return false
        true
    } catch (_: Exception) {
        false
    }
}

/** 把 SAF 树 URI 转成用户可读的路径说明，如 内部存储/Documents/CatScan */
internal fun exportTreeDisplayPath(treeUri: String): String {
    if (treeUri.isBlank()) return "系统下载目录 /CatScan"
    return runCatching {
        DocumentsContract.getTreeDocumentId(Uri.parse(treeUri))
            ?.replace("primary:", "内部存储/")
            ?.replace('/', '／')
            ?: "已选择自定义目录"
    }.getOrDefault("已选择自定义目录")
}

internal fun safeFileNamePart(s: String): String {
    return s.trim()
        .ifBlank { "未命名" }
        .replace(Regex("""[\\/:*?"<>|]"""), "_")
        .replace(Regex("""\s+"""), "_")
}
