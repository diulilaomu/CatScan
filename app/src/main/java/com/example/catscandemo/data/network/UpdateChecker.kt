package com.example.catscandemo.data.network

import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.util.concurrent.TimeUnit

/**
 * GitHub Release 更新检查：拉取最新 release 的 tag 与本地版本名做数值比较。
 * 版本号约定形如 v4.2.0 / 4.2.0：忽略 v 前缀后按点分段逐段比数值。
 */
object UpdateChecker {
    private const val LATEST_RELEASE_URL =
        "https://api.github.com/repos/diulilaomu/CatScan/releases/latest"

    private val client by lazy {
        OkHttpClient.Builder()
            .connectTimeout(5, TimeUnit.SECONDS)
            .readTimeout(5, TimeUnit.SECONDS)
            .callTimeout(10, TimeUnit.SECONDS)
            .build()
    }

    /** 同步请求（须在 IO 线程调用）：成功返回 tag（如 v4.2.1），失败返回 null */
    fun fetchLatestReleaseTag(): String? {
        val request = Request.Builder()
            .url(LATEST_RELEASE_URL)
            .header("Accept", "application/vnd.github+json")
            .header("User-Agent", "CatScan-Android")
            .build()
        return runCatching {
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return@runCatching null
                val body = response.body?.string() ?: return@runCatching null
                JSONObject(body).optString("tag_name").trim().ifBlank { null }
            }
        }.getOrNull()
    }

    /** candidate 严格大于 current 才算有更新（相等或更低都不提示） */
    fun isNewerVersion(candidate: String, current: String): Boolean {
        val c = parseNumericParts(candidate) ?: return false
        val cur = parseNumericParts(current) ?: return false
        val size = maxOf(c.size, cur.size)
        for (i in 0 until size) {
            val a = c.getOrElse(i) { 0 }
            val b = cur.getOrElse(i) { 0 }
            if (a != b) return a > b
        }
        return false
    }

    /** "v4.10.0" -> [4, 10, 0]；每段取前导数字，整串无任何数字视为无法解析返回 null */
    private fun parseNumericParts(version: String): List<Int>? {
        val cleaned = version.trim().removePrefix("v").removePrefix("V")
        val parts = cleaned.split('.').map { segment ->
            segment.takeWhile { it.isDigit() }.toIntOrNull() ?: 0
        }
        if (parts.all { it == 0 }) return null
        return parts
    }
}
