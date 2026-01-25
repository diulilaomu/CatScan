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
    private var listenerJob: Job? = null // 被动监听任务
    private var isContinuous = false
    
    companion object {
        private const val TAG = "NetworkDiscovery"
        private const val DISCOVERY_PORT = 29028 // 发现端口（与Windows客户端通信）
        private const val DISCOVERY_MESSAGE = "CATSCAN_DISCOVERY_REQUEST"
        private const val DISCOVERY_RESPONSE_PREFIX = "CATSCAN_DISCOVERY_RESPONSE:"
        private const val DISCOVERY_TIMEOUT_MS = 500L // 500ms超时（单次扫描）
        private const val DISCOVERY_INTERVAL_MS = 1000L // 1秒扫描一次
    }
    
    /**
     * 开始单次网络发现
     * @param onServerFound 发现服务器时的回调
     * @param onDiscoveryComplete 发现完成时的回调（无论是否找到服务器）
     */
    fun startDiscovery(
        onServerFound: (DiscoveredServer) -> Unit,
        onDiscoveryComplete: () -> Unit
    ) {
        stopDiscovery()
        isContinuous = false
        performSingleDiscovery(onServerFound, onDiscoveryComplete)
    }
    
    /**
     * 开始周期性网络发现（每1秒扫描一次）
     * @param onServerFound 发现服务器时的回调
     */
    fun startContinuousDiscovery(
        onServerFound: (DiscoveredServer) -> Unit
    ) {
        stopDiscovery()
        isContinuous = true
        
        discoveryJob = discoveryScope.launch {
            while (isContinuous && isActive) {
                performSingleDiscovery(
                    onServerFound = onServerFound,
                    onDiscoveryComplete = {}
                )
                delay(DISCOVERY_INTERVAL_MS)
            }
        }
    }
    
    /**
     * 执行单次发现
     */
    private fun performSingleDiscovery(
        onServerFound: (DiscoveredServer) -> Unit,
        onDiscoveryComplete: () -> Unit
    ) {
        discoveryScope.launch {
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
                if (!isContinuous) {
                    withContext(Dispatchers.Main) {
                        onDiscoveryComplete()
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "网络发现失败: ${e.message}", e)
                if (!isContinuous) {
                    withContext(Dispatchers.Main) {
                        onDiscoveryComplete()
                    }
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
     * 启动被动监听服务（响应其他设备的发现请求）
     */
    fun startPassiveListener() {
        stopPassiveListener()
        
        listenerJob = discoveryScope.launch {
            try {
                val socket = DatagramSocket(DISCOVERY_PORT).apply {
                    broadcast = true
                    reuseAddress = true
                }
                
                Log.d(TAG, "被动监听服务已启动，端口 $DISCOVERY_PORT")
                
                while (isActive) {
                    try {
                        val buffer = ByteArray(1024)
                        val packet = DatagramPacket(buffer, buffer.size)
                        socket.receive(packet)
                        
                        val request = String(
                            packet.data,
                            0,
                            packet.length,
                            StandardCharsets.UTF_8
                        )
                        
                        if (request == DISCOVERY_MESSAGE) {
                            // 获取本机IP地址
                            val localIp = getLocalIpAddress()
                            if (localIp != null) {
                                // 构造响应（虽然Android客户端不提供HTTP服务，但可以响应发现请求）
                                val responseUrl = "http://$localIp:29027/postqrdata"
                                val response = "${DISCOVERY_RESPONSE_PREFIX}$responseUrl"
                                
                                val responseData = response.toByteArray(StandardCharsets.UTF_8)
                                val responsePacket = DatagramPacket(
                                    responseData,
                                    responseData.size,
                                    packet.address,
                                    packet.port
                                )
                                socket.send(responsePacket)
                                Log.d(TAG, "响应发现请求: ${packet.address.hostAddress} -> $responseUrl")
                            }
                        }
                    } catch (e: Exception) {
                        if (isActive) {
                            Log.e(TAG, "被动监听错误: ${e.message}")
                        }
                    }
                }
                
                socket.close()
            } catch (e: Exception) {
                Log.e(TAG, "启动被动监听服务失败: ${e.message}", e)
            }
        }
    }
    
    /**
     * 停止被动监听服务
     */
    fun stopPassiveListener() {
        listenerJob?.cancel()
        listenerJob = null
    }
    
    /**
     * 获取本机IP地址
     */
    private fun getLocalIpAddress(): String? {
        try {
            val interfaces = NetworkInterface.getNetworkInterfaces()
            while (interfaces.hasMoreElements()) {
                val networkInterface = interfaces.nextElement()
                
                // 跳过回环接口和未激活的接口
                if (networkInterface.isLoopback || !networkInterface.isUp) {
                    continue
                }
                
                val addresses = networkInterface.inetAddresses
                while (addresses.hasMoreElements()) {
                    val address = addresses.nextElement()
                    
                    // 只返回IPv4地址，且不是回环地址
                    if (address is Inet4Address && !address.isLoopbackAddress) {
                        return address.hostAddress
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "获取本机IP地址失败: ${e.message}")
        }
        return null
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
        stopPassiveListener()
        discoveryScope.cancel()
    }
}
