package com.example.catscandemo.data.repository

import android.content.Context
import com.example.catscandemo.data.network.CatScanClient
import com.example.catscandemo.data.network.NetworkDiscovery
import com.example.catscandemo.domain.model.NetworkScanData
import com.example.catscandemo.domain.use_case.DiscoveredServer
import com.example.catscandemo.domain.use_case.NetworkRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL

/**
 * 默认网络仓库实现
 * 负责网络通信逻辑
 */
class DefaultNetworkRepository(
    private val context: Context,
    private val catScanClient: CatScanClient
) : NetworkRepository {

    private var networkDiscovery: NetworkDiscovery? = null
    private var heartbeatJob: Job? = null
    private val heartbeatScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val HEARTBEAT_INTERVAL_MS = 2000L // 2秒检测一次
    private val HEARTBEAT_TIMEOUT_MS = 1000L // 1秒超时

    init {
        networkDiscovery = NetworkDiscovery(context)
    }

    override suspend fun uploadData(
        data: NetworkScanData,
        url: String,
        onSuccess: () -> Unit,
        onError: (String) -> Unit
    ) {
        withContext(Dispatchers.IO) {
            catScanClient.uploadToComputer(
                url = url,
                qrData = data.qrdata,
                templateName = data.templateName,
                operator = data.operator,
                campus = data.campus,
                building = data.building,
                floor = data.floor,
                room = data.room,
                id = data.id,
                action = data.action,
                onSuccess = onSuccess,
                onFailure = onError
            )
        }
    }

    override suspend fun uploadBatchData(
        dataList: List<NetworkScanData>,
        url: String,
        onSuccess: () -> Unit,
        onError: (String) -> Unit
    ) {
        withContext(Dispatchers.IO) {
            val batchData = dataList.map {
                mapOf(
                    "qrdata" to it.qrdata,
                    "templateName" to it.templateName,
                    "operator" to it.operator,
                    "campus" to it.campus,
                    "building" to it.building,
                    "floor" to it.floor,
                    "room" to it.room,
                    "id" to it.id,
                    "action" to it.action
                )
            }
            
            catScanClient.uploadBatchToComputer(
                url = url,
                dataList = batchData,
                onSuccess = onSuccess,
                onFailure = onError
            )
        }
    }

    override suspend fun checkConnectivity(url: String): Boolean {
        return withContext(Dispatchers.IO) {
            try {
                val urlObj = URL(url)
                val connection = urlObj.openConnection() as HttpURLConnection
                connection.connectTimeout = HEARTBEAT_TIMEOUT_MS.toInt()
                connection.readTimeout = HEARTBEAT_TIMEOUT_MS.toInt()
                connection.requestMethod = "GET"
                
                val responseCode = connection.responseCode
                connection.disconnect()
                return@withContext responseCode == 200
            } catch (e: Exception) {
                return@withContext false
            }
        }
    }

    override fun startDiscovery(
        onServerFound: (DiscoveredServer) -> Unit,
        onDiscoveryComplete: () -> Unit
    ) {
        networkDiscovery?.startDiscovery(
            onServerFound = { server ->
                val discoveredServer = DiscoveredServer(
                    ip = server.ip,
                    port = server.port,
                    url = server.url,
                    name = server.name
                )
                onServerFound(discoveredServer)
            },
            onDiscoveryComplete = onDiscoveryComplete
        )
    }

    override fun stopDiscovery() {
        networkDiscovery?.stopDiscovery()
    }

    override fun selectServer(server: DiscoveredServer) {
        // 这里可以添加服务器选择的逻辑，比如保存服务器信息等
    }

    override fun startHeartbeatDetection(
        serverUrl: String,
        onConnectivityChanged: (Boolean) -> Unit
    ) {
        stopHeartbeatDetection() // 先停止之前的心跳检测

        // 用可取消的 Job 管理心跳（避免 GlobalScope 泄漏、避免 stopHeartbeatDetection 无效）
        heartbeatJob = heartbeatScope.launch {
            while (isActive) {
                if (serverUrl.isNotEmpty()) {
                    val isConnected = checkConnectivity(serverUrl)
                    withContext(Dispatchers.Main) {
                        onConnectivityChanged(isConnected)
                    }
                }
                delay(HEARTBEAT_INTERVAL_MS)
            }
        }
    }

    override fun stopHeartbeatDetection() {
        heartbeatJob?.cancel()
        heartbeatJob = null
    }

    /**
     * 启动被动监听服务
     */
    fun startPassiveListener() {
        networkDiscovery?.startPassiveListener()
    }

    /**
     * 清理资源
     */
    fun cleanup() {
        networkDiscovery?.cleanup()
        stopHeartbeatDetection()
    }
}
