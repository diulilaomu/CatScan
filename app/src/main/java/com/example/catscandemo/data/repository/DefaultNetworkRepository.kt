package com.example.catscandemo.data.repository

import android.content.Context
import android.util.Log
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
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.net.DatagramSocket
import java.net.HttpURLConnection
import java.net.Inet4Address
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.NetworkInterface
import java.net.URL
import kotlin.coroutines.resume

class DefaultNetworkRepository(
    private val context: Context,
    private val catScanClient: CatScanClient
) : NetworkRepository {

    private var networkDiscovery: NetworkDiscovery? = null
    private var heartbeatJob: Job? = null
    private val heartbeatScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val heartbeatIntervalMs = 2000L
    private val heartbeatTimeoutMs = 1000L

    companion object {
        private const val TAG = "DefaultNetworkRepository"
    }

    private data class ClientNetworkInfo(
        val ip: String,
        val mac: String
    )

    private sealed class HeartbeatResult {
        object Connected : HeartbeatResult()
        object Disconnected : HeartbeatResult()
        data class Blocked(val message: String) : HeartbeatResult()
    }

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
            val networkInfo = collectClientNetworkInfo(url)
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
                clientIp = networkInfo.ip,
                clientMac = networkInfo.mac,
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
            val networkInfo = collectClientNetworkInfo(url)
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
                    "action" to it.action,
                    "clientIp" to networkInfo.ip,
                    "clientMac" to networkInfo.mac
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
                val connection = (URL(url).openConnection() as HttpURLConnection).apply {
                    connectTimeout = heartbeatTimeoutMs.toInt()
                    readTimeout = heartbeatTimeoutMs.toInt()
                    requestMethod = "GET"
                }
                val responseCode = connection.responseCode
                connection.disconnect()
                responseCode == 200
            } catch (e: Exception) {
                false
            }
        }
    }

    override fun startDiscovery(
        onServerFound: (DiscoveredServer) -> Unit,
        onDiscoveryComplete: () -> Unit
    ) {
        networkDiscovery?.startDiscovery(
            onServerFound = { server ->
                onServerFound(
                    DiscoveredServer(
                        ip = server.ip,
                        port = server.port,
                        url = server.url,
                        name = server.name
                    )
                )
            },
            onDiscoveryComplete = onDiscoveryComplete
        )
    }

    override fun stopDiscovery() {
        networkDiscovery?.stopDiscovery()
    }

    override fun selectServer(server: DiscoveredServer) {
        // keep interface compatibility; no local persistence required here.
    }

    override fun startHeartbeatDetection(
        serverUrl: String,
        onConnectivityChanged: (Boolean) -> Unit,
        onBlocked: (String) -> Unit
    ) {
        stopHeartbeatDetection()
        heartbeatJob = heartbeatScope.launch {
            while (isActive) {
                val result = if (serverUrl.isNotEmpty()) {
                    val networkInfo = collectClientNetworkInfo(serverUrl)
                    sendHeartbeat(serverUrl, networkInfo)
                } else {
                    HeartbeatResult.Disconnected
                }

                withContext(Dispatchers.Main) {
                    when (result) {
                        is HeartbeatResult.Connected -> onConnectivityChanged(true)
                        is HeartbeatResult.Disconnected -> onConnectivityChanged(false)
                        is HeartbeatResult.Blocked -> {
                            onConnectivityChanged(false)
                            onBlocked(result.message)
                        }
                    }
                }
                delay(heartbeatIntervalMs)
            }
        }
    }

    override fun stopHeartbeatDetection() {
        heartbeatJob?.cancel()
        heartbeatJob = null
    }

    fun startPassiveListener() {
        networkDiscovery?.startPassiveListener()
    }

    fun cleanup() {
        networkDiscovery?.cleanup()
        stopHeartbeatDetection()
    }

    private suspend fun sendHeartbeat(serverUrl: String, networkInfo: ClientNetworkInfo): HeartbeatResult {
        return suspendCancellableCoroutine { cont ->
            catScanClient.uploadHeartbeatToComputer(
                url = serverUrl,
                clientIp = networkInfo.ip,
                clientMac = networkInfo.mac,
                onSuccess = {
                    if (cont.isActive) cont.resume(HeartbeatResult.Connected)
                },
                onFailure = { err ->
                    if (cont.isActive) {
                        if (isClientBlockedError(err)) {
                            cont.resume(HeartbeatResult.Blocked(err))
                        } else {
                            cont.resume(HeartbeatResult.Disconnected)
                        }
                    }
                }
            )
        }
    }

    private fun isClientBlockedError(error: String?): Boolean {
        if (error.isNullOrBlank()) return false
        return error.contains(CatScanClient.CLIENT_BLOCKED_FLAG) ||
            error.contains("client blocked", ignoreCase = true) ||
            error.contains("HTTP 403")
    }

    private fun collectClientNetworkInfo(serverUrl: String? = null): ClientNetworkInfo {
        val routedAddress = resolveLocalAddressForServer(serverUrl)
        if (routedAddress != null) {
            val routedIp = (routedAddress.hostAddress ?: "").substringBefore("%")
            val routedMac = resolveMacByAddress(routedAddress)
            if (routedIp.isNotEmpty()) {
                return ClientNetworkInfo(
                    ip = routedIp,
                    mac = routedMac
                )
            }
        }

        var ip = ""
        var mac = ""
        try {
            val interfaces = NetworkInterface.getNetworkInterfaces() ?: return ClientNetworkInfo(ip, mac)
            while (interfaces.hasMoreElements()) {
                val networkInterface = interfaces.nextElement()
                if (networkInterface.isLoopback || !networkInterface.isUp) continue

                if (mac.isEmpty()) {
                    val hardware = networkInterface.hardwareAddress
                    if (hardware != null && hardware.isNotEmpty()) {
                        mac = hardware.joinToString(":") { b -> "%02x".format(b.toInt() and 0xff) }
                    }
                }

                val addresses = networkInterface.inetAddresses
                while (addresses.hasMoreElements()) {
                    val address = addresses.nextElement()
                    if (address is Inet4Address && !address.isLoopbackAddress) {
                        ip = address.hostAddress ?: ""
                        break
                    }
                }

                if (ip.isNotEmpty() && mac.isNotEmpty()) break
            }
        } catch (e: Exception) {
            Log.e(TAG, "collectClientNetworkInfo failed: ${e.message}", e)
        }
        return ClientNetworkInfo(ip = ip, mac = mac)
    }

    private fun resolveLocalAddressForServer(serverUrl: String?): InetAddress? {
        if (serverUrl.isNullOrBlank()) return null
        return try {
            val target = URL(serverUrl)
            val targetHost = target.host
            val targetPort = if (target.port > 0) {
                target.port
            } else if (target.protocol.equals("https", ignoreCase = true)) {
                443
            } else {
                80
            }

            DatagramSocket().use { socket ->
                socket.connect(InetSocketAddress(targetHost, targetPort))
                val local = socket.localAddress
                if (local != null && !local.isLoopbackAddress && !local.isAnyLocalAddress) {
                    local
                } else {
                    null
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "resolveLocalAddressForServer failed: ${e.message}")
            null
        }
    }

    private fun resolveMacByAddress(address: InetAddress): String {
        return try {
            val networkInterface = NetworkInterface.getByInetAddress(address) ?: return ""
            val hardwareAddress = networkInterface.hardwareAddress ?: return ""
            if (hardwareAddress.isEmpty()) return ""
            hardwareAddress.joinToString(":") { b -> "%02x".format(b.toInt() and 0xff) }
        } catch (e: Exception) {
            Log.w(TAG, "resolveMacByAddress failed: ${e.message}")
            ""
        }
    }
}
