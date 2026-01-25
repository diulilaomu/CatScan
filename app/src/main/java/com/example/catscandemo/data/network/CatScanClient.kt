package com.example.catscandemo.data.network

import android.os.Handler
import android.os.Looper
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.io.IOException
import java.util.concurrent.TimeUnit

class CatScanClient {
    private val client = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS)
        .build()

    // 添加主线程 Handler
    private val mainHandler = Handler(Looper.getMainLooper())

    fun uploadToComputer(
        url: String,
        qrData: String,
        templateName: String? = null,
        operator: String? = null,
        campus: String? = null,
        building: String? = null,
        floor: String? = null,
        room: String? = null,
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
        }
        val body = json.toString().toRequestBody("application/json; charset=utf-8".toMediaType())
        val request = Request.Builder().url(url).post(body).build()

        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                mainHandler.post { onFailure(e.message ?: "网络连接失败") }
            }

            override fun onResponse(call: Call, response: Response) {
                response.use {
                    if (!it.isSuccessful) {
                        mainHandler.post { onFailure("HTTP ${it.code}") }
                        return
                    }
                    try {
                        val bodyStr = it.body?.string() ?: ""
                        val obj = JSONObject(bodyStr)
                        val code = obj.optInt("code", -1)
                        if (code == 200) {
                            mainHandler.post(onSuccess)
                        } else {
                            mainHandler.post { onFailure("服务器返回错误: $code") }
                        }
                    } catch (e: Exception) {
                        mainHandler.post { onFailure("解析响应失败: ${e.message}") }
                    }
                }
            }
        })
    }
}