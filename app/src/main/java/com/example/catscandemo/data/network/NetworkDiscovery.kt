package com.example.catscandemo.data.network

import android.content.Context
import android.net.wifi.WifiManager
import android.util.Log
import kotlinx.coroutines.*
import java.net.*
import java.nio.charset.StandardCharsets

data class DiscoveredServer(
    val ip: String,
    val port: Int,
    val url: String,
    val name: String = "Windows客户端"
)

class NetworkDiscovery(private val context: Context) {
    private val discoveryScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var discoveryJob: Job? = null
    
    companion object {
        private const val TAG = "NetworkDiscovery"
        private const val DISCOVERY_PORT = 29028 // 发现端口（与Windows客户端通信）
        private const val DISCOVERY_MESSAGE = "CATSCAN_DISCOVERY_REQUEST"
        private const val DISCOVERY_RESPONSE_PREFIX = "CATSCAN_DISCOVERY_RESPONSE:"
        private const val DISCOVERY_TIMEOUT_MS = 3000L // 3秒超时
    }
    
    /**
     * 开始网络发现
     * @param onServerFound 发现服务器时的回调
     * @param onDiscoveryComplete 发现完成时的回调（无论是否找到服务器）
     */
    fun startDiscovery(
        onServerFound: (DiscoveredServer) -> Unit,
        onDiscoveryComplete: () -> Unit
    ) {
        stopDiscovery() // 先停止之前的发现
        
        discoveryJob = discoveryScope.launch {
            try {
                val servers = mutableSetOf<String>() // 用于去重
                val socket = DatagramSocket().apply {
                    soTimeout = DISCOVERY_TIMEOUT_MS.toInt()
                    broadcast = true
                }
                
                // 获取广播地址
                val broadcastAddresses = getBroadcastAddresses()
                
                // 发送发现请求
                val requestData = DISCOVERY_MESSAGE.toByteArray(StandardCharsets.UTF_8)
                broadcastAddresses.forEach { broadcastAddr ->
                    try {
                        val packet = DatagramPacket(
                            requestData,
                            requestData.size,
                            broadcastAddr,
                            DISCOVERY_PORT
                        )
                        socket.send(packet)
                        Log.d(TAG, "发送发现请求到: ${broadcastAddr.hostAddress}")
                    } catch (e: Exception) {
                        Log.e(TAG, "发送发现请求失败: ${e.message}")
                    }
                }
                
                // 接收响应
                val startTime = System.currentTimeMillis()
                while (System.currentTimeMillis() - startTime < DISCOVERY_TIMEOUT_MS) {
                    try {
                        val buffer = ByteArray(1024)
                        val responsePacket = DatagramPacket(buffer, buffer.size)
                        socket.receive(responsePacket)
                        
                        val response = String(
                            responsePacket.data,
                            0,
                            responsePacket.length,
                            StandardCharsets.UTF_8
                        )
                        
                        if (response.startsWith(DISCOVERY_RESPONSE_PREFIX)) {
                            val serverUrl = response.removePrefix(DISCOVERY_RESPONSE_PREFIX).trim()
                            val serverKey = "${responsePacket.address.hostAddress}:29027"
                            
                            if (servers.add(serverKey)) {
                                val server = parseServerResponse(
                                    responsePacket.address.hostAddress,
                                    serverUrl
                                )
                                if (server != null) {
                                    withContext(Dispatchers.Main) {
                                        onServerFound(server)
                                    }
                                    Log.d(TAG, "发现服务器: ${server.url}")
                                }
                            }
                        }
                    } catch (e: SocketTimeoutException) {
                        // 超时是正常的，继续等待其他响应
                    } catch (e: Exception) {
                        Log.e(TAG, "接收响应失败: ${e.message}")
                    }
                }
                
                socket.close()
                withContext(Dispatchers.Main) {
                    onDiscoveryComplete()
                }
            } catch (e: Exception) {
                Log.e(TAG, "网络发现失败: ${e.message}", e)
                withContext(Dispatchers.Main) {
                    onDiscoveryComplete()
                }
            }
        }
    }
    
    /**
     * 停止网络发现
     */
    fun stopDiscovery() {
        discoveryJob?.cancel()
        discoveryJob = null
    }
    
    /**
     * 获取广播地址列表
     */
    private fun getBroadcastAddresses(): List<InetAddress> {
        val addresses = mutableListOf<InetAddress>()
        
        try {
            // 获取所有网络接口
            val interfaces = NetworkInterface.getNetworkInterfaces()
            while (interfaces.hasMoreElements()) {
                val networkInterface = interfaces.nextElement()
                
                // 跳过回环接口和未激活的接口
                if (networkInterface.isLoopback || !networkInterface.isUp) {
                    continue
                }
                
                // 获取接口的IP地址
                val inetAddresses = networkInterface.inetAddresses
                while (inetAddresses.hasMoreElements()) {
                    val inetAddress = inetAddresses.nextElement()
                    
                    // 只处理IPv4地址
                    if (inetAddress is Inet4Address) {
                        try {
                            val broadcast = getBroadcastAddress(inetAddress, networkInterface)
                            if (broadcast != null && !addresses.contains(broadcast)) {
                                addresses.add(broadcast)
                            }
                        } catch (e: Exception) {
                            Log.e(TAG, "计算广播地址失败: ${e.message}")
                        }
                    }
                }
            }
            
            // 如果没有找到，使用通用广播地址
            if (addresses.isEmpty()) {
                addresses.add(InetAddress.getByName("255.255.255.255"))
            }
        } catch (e: Exception) {
            Log.e(TAG, "获取网络接口失败: ${e.message}")
            // 使用通用广播地址作为后备
            try {
                addresses.add(InetAddress.getByName("255.255.255.255"))
            } catch (ex: Exception) {
                Log.e(TAG, "无法获取广播地址: ${ex.message}")
            }
        }
        
        return addresses
    }
    
    /**
     * 计算广播地址
     */
    private fun getBroadcastAddress(
        inetAddress: Inet4Address,
        networkInterface: NetworkInterface
    ): InetAddress? {
        try {
            val addresses = networkInterface.interfaceAddresses
            for (addr in addresses) {
                if (addr.address == inetAddress) {
                    return addr.broadcast
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "获取广播地址失败: ${e.message}")
        }
        return null
    }
    
    /**
     * 解析服务器响应
     */
    private fun parseServerResponse(ip: String, response: String): DiscoveredServer? {
        return try {
            // 如果响应是URL，直接使用
            if (response.startsWith("http://") || response.startsWith("https://")) {
                val url = response.trim()
                DiscoveredServer(
                    ip = ip,
                    port = 29027,
                    url = url,
                    name = "Windows客户端"
                )
            } else {
                // 否则构造URL
                val url = "http://$ip:29027/postqrdata"
                DiscoveredServer(
                    ip = ip,
                    port = 29027,
                    url = url,
                    name = "Windows客户端"
                )
            }
        } catch (e: Exception) {
            Log.e(TAG, "解析服务器响应失败: ${e.message}")
            null
        }
    }
    
    /**
     * 清理资源
     */
    fun cleanup() {
        stopDiscovery()
        discoveryScope.cancel()
    }
}
