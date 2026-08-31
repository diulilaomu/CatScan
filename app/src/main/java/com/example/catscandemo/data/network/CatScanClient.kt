package com.example.catscandemo.data.network

import android.os.Handler
import android.os.Looper
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import org.json.JSONArray
import java.io.IOException
import java.util.concurrent.TimeUnit

class CatScanClient {
    private val client = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS)
        .build()
    private val heartbeatClient = client.newBuilder()
        .connectTimeout(1, TimeUnit.SECONDS)
        .readTimeout(1, TimeUnit.SECONDS)
        .callTimeout(2, TimeUnit.SECONDS)
        .build()

    // 心跳回调转发到主线程的 Handler
    private val mainHandler = Handler(Looper.getMainLooper())

    companion object {
        const val CLIENT_BLOCKED_FLAG = "CLIENT_BLOCKED"
    }

    private fun isBlockedResponse(httpCode: Int, bodyStr: String): Boolean {
        if (httpCode == 403) return true
        if (bodyStr.isBlank()) return false
        val text = bodyStr.lowercase()
        return text.contains("client blocked") ||
            text.contains("\"code\":403") ||
            text.contains("\"status\":\"forbidden\"")
    }

    fun uploadToComputer(
        url: String,
        qrData: String,
        templateName: String? = null,
        operator: String? = null,
        campus: String? = null,
        building: String? = null,
        floor: String? = null,
        room: String? = null,
        tag: String? = null,
        scanTimestamp: Long = 0L,
        id: String? = null,
        action: String? = null,
        clientIp: String? = null,
        clientMac: String? = null,
        onSuccess: () -> Unit,
        onFailure: (String) -> Unit
    ) {
        if (url.isEmpty()) {
            mainHandler.post { onFailure("目标地址为空") }
            return
        }

        val json = JSONObject().apply {
            put("qrdata", qrData)
            put("templateName", templateName ?: "")
            put("operator", operator ?: "")
            put("campus", campus ?: "")
            put("building", building ?: "")
            put("floor", floor ?: "")
            put("room", room ?: "")
            put("tag", tag ?: "")
            put("scanTimestamp", scanTimestamp)
            put("id", id ?: "")
            put("action", action ?: "add")
            if (!clientIp.isNullOrBlank()) put("clientIp", clientIp)
            if (!clientMac.isNullOrBlank()) put("clientMac", clientMac)
        }
        val body = json.toString().toRequestBody("application/json; charset=utf-8".toMediaType())
        val request = Request.Builder().url(url).post(body).build()

        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                mainHandler.post { onFailure(e.message ?: "网络连接失败") }
            }

            override fun onResponse(call: Call, response: Response) {
                response.use {
                    val bodyStr = it.body?.string() ?: ""
                    if (!it.isSuccessful) {
                        if (isBlockedResponse(it.code, bodyStr)) {
                            mainHandler.post { onFailure(CLIENT_BLOCKED_FLAG) }
                        } else {
                            mainHandler.post { onFailure("HTTP ${it.code}") }
                        }
                        return
                    }
                    try {
                        val obj = JSONObject(bodyStr)
                        val code = obj.optInt("code", -1)
                        if (code == 200) {
                            mainHandler.post(onSuccess)
                        } else if (code == 403 || obj.optString("status") == "forbidden" || isBlockedResponse(0, bodyStr)) {
                            mainHandler.post { onFailure(CLIENT_BLOCKED_FLAG) }
                        } else {
                            mainHandler.post { onFailure("Server error $code") }
                        }
                    } catch (e: Exception) {
                        if (isBlockedResponse(it.code, bodyStr)) {
                            mainHandler.post { onFailure(CLIENT_BLOCKED_FLAG) }
                        } else {
                            mainHandler.post { onFailure("Parse response failed: ${e.message}") }
                        }
                    }
                }
            }
        })
    }

    /**
     * 批量上传数据
     */
    fun uploadBatchToComputer(
        url: String,
        dataList: List<Map<String, Any>>,
        onSuccess: () -> Unit,
        onFailure: (String) -> Unit
    ) {
        if (url.isEmpty()) {
            mainHandler.post { onFailure("目标地址为空") }
            return
        }

        val json = JSONObject().apply {
            put("batch", true)
            val dataArray = JSONArray()
            dataList.forEach {
                val itemJson = JSONObject()
                itemJson.put("qrdata", it["qrdata"] ?: "")
                itemJson.put("templateName", it["templateName"] ?: "")
                itemJson.put("operator", it["operator"] ?: "")
                itemJson.put("campus", it["campus"] ?: "")
                itemJson.put("building", it["building"] ?: "")
                itemJson.put("floor", it["floor"] ?: "")
                itemJson.put("room", it["room"] ?: "")
                itemJson.put("tag", it["tag"] ?: "")
                itemJson.put("scanTimestamp", (it["scanTimestamp"] as? Long) ?: 0L)
                itemJson.put("id", it["id"] ?: "")
                itemJson.put("action", it["action"] ?: "add")
                itemJson.put("clientIp", it["clientIp"] ?: "")
                itemJson.put("clientMac", it["clientMac"] ?: "")
                dataArray.put(itemJson)
            }
            put("data", dataArray)
        }
        val body = json.toString().toRequestBody("application/json; charset=utf-8".toMediaType())
        val request = Request.Builder().url(url).post(body).build()

        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                mainHandler.post { onFailure(e.message ?: "网络连接失败") }
            }

            override fun onResponse(call: Call, response: Response) {
                response.use {
                    val bodyStr = it.body?.string() ?: ""
                    if (!it.isSuccessful) {
                        if (isBlockedResponse(it.code, bodyStr)) {
                            mainHandler.post { onFailure(CLIENT_BLOCKED_FLAG) }
                        } else {
                            mainHandler.post { onFailure("HTTP ${it.code}") }
                        }
                        return
                    }
                    try {
                        val obj = JSONObject(bodyStr)
                        val code = obj.optInt("code", -1)
                        if (code == 200) {
                            mainHandler.post(onSuccess)
                        } else if (code == 403 || obj.optString("status") == "forbidden" || isBlockedResponse(0, bodyStr)) {
                            mainHandler.post { onFailure(CLIENT_BLOCKED_FLAG) }
                        } else {
                            mainHandler.post { onFailure("Server error $code") }
                        }
                    } catch (e: Exception) {
                        if (isBlockedResponse(it.code, bodyStr)) {
                            mainHandler.post { onFailure(CLIENT_BLOCKED_FLAG) }
                        } else {
                            mainHandler.post { onFailure("Parse response failed: ${e.message}") }
                        }
                    }
                }
            }
        })
    }

    fun uploadHeartbeatToComputer(
        url: String,
        clientIp: String? = null,
        clientMac: String? = null,
        onSuccess: () -> Unit,
        onFailure: (String) -> Unit
    ) {
        if (url.isEmpty()) {
            mainHandler.post { onFailure("目标地址为空") }
            return
        }

        val json = JSONObject().apply {
            put("heartbeat", true)
            put("action", "heartbeat")
            if (!clientIp.isNullOrBlank()) put("clientIp", clientIp)
            if (!clientMac.isNullOrBlank()) put("clientMac", clientMac)
        }
        val body = json.toString().toRequestBody("application/json; charset=utf-8".toMediaType())
        val request = Request.Builder().url(url).post(body).build()

        heartbeatClient.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                mainHandler.post { onFailure(e.message ?: "网络连接失败") }
            }

            override fun onResponse(call: Call, response: Response) {
                response.use {
                    val bodyStr = it.body?.string() ?: ""
                    if (!it.isSuccessful) {
                        if (isBlockedResponse(it.code, bodyStr)) {
                            mainHandler.post { onFailure(CLIENT_BLOCKED_FLAG) }
                        } else {
                            mainHandler.post { onFailure("HTTP ${it.code}") }
                        }
                        return
                    }
                    if (isBlockedResponse(it.code, bodyStr)) {
                        mainHandler.post { onFailure(CLIENT_BLOCKED_FLAG) }
                        return
                    }
                    mainHandler.post(onSuccess)
                }
            }
        })
    }
}
