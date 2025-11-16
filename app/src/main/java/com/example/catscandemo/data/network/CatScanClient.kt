package com.example.catscandemo.data.network

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

    /**
     * 上传数据到电脑端。异步回调在 OkHttp 线程中，调用方一般切回主线程显示 UI。
     */
    fun uploadToComputer(
        url: String,
        qrData: String,
        onSuccess: () -> Unit,
        onFailure: (String) -> Unit
    ) {
        if (url.isEmpty()) {
            onFailure("目标地址为空")
            return
        }

        val json = JSONObject().apply { put("qrdata", qrData) }
        val body = json.toString().toRequestBody("application/json; charset=utf-8".toMediaType())
        val request = Request.Builder().url(url).post(body).build()

        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                onFailure(e.message ?: "网络连接失败")
            }

            override fun onResponse(call: Call, response: Response) {
                response.use {
                    if (!it.isSuccessful) {
                        onFailure("HTTP ${it.code}")
                        return
                    }
                    try {
                        val bodyStr = it.body?.string() ?: ""
                        val obj = JSONObject(bodyStr)
                        val code = obj.optInt("code", -1)
                        if (code == 200) onSuccess() else onFailure("服务器返回错误: $code")
                    } catch (e: Exception) {
                        onFailure("解析响应失败: ${e.message}")
                    }
                }
            }
        })
    }
}

