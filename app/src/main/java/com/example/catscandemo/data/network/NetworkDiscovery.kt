package com.example.catscandemo.data.network

import android.content.Context
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.Inet4Address
import java.net.InetAddress
import java.net.NetworkInterface
import java.net.SocketTimeoutException
import java.nio.charset.StandardCharsets
import kotlin.coroutines.coroutineContext

data class DiscoveredServer(
    val ip: String,
    val port: Int,
    val url: String,
    val name: String = "Windows瀹㈡埛绔?
)

class NetworkDiscovery(private val context: Context) {
    private val discoveryScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var discoveryJob: Job? = null
    private var listenerJob: Job? = null
    @Volatile
    private var listenerSocket: DatagramSocket? = null
    @Volatile
    private var isContinuous = false

    companion object {
        private const val TAG = "NetworkDiscovery"
        private const val DISCOVERY_PORT = 29028
        private const val DISCOVERY_MESSAGE = "CATSCAN_DISCOVERY_REQUEST"
        private const val DISCOVERY_RESPONSE_PREFIX = "CATSCAN_DISCOVERY_RESPONSE:"
        private const val DISCOVERY_TIMEOUT_MS = 500L
        private const val DISCOVERY_INTERVAL_MS = 2000L
    }

    fun startDiscovery(
        onServerFound: (DiscoveredServer) -> Unit,
        onDiscoveryComplete: () -> Unit
    ) {
        stopDiscovery()
        isContinuous = false
        discoveryJob = discoveryScope.launch {
            try {
                performSingleDiscoveryRound(onServerFound)
            } finally {
                withContext(Dispatchers.Main) {
                    onDiscoveryComplete()
                }
            }
        }
    }

    fun startContinuousDiscovery(
        onServerFound: (DiscoveredServer) -> Unit
    ) {
        stopDiscovery()
        isContinuous = true
        discoveryJob = discoveryScope.launch {
            while (isContinuous && isActive) {
                try {
                    performSingleDiscoveryRound(onServerFound)
                } catch (e: Exception) {
                    Log.e(TAG, "Continuous discovery round failed: ${e.message}", e)
                }
                delay(DISCOVERY_INTERVAL_MS)
            }
        }
    }

    private suspend fun performSingleDiscoveryRound(
        onServerFound: (DiscoveredServer) -> Unit
    ) {
        val servers = mutableSetOf<String>()
        DatagramSocket().use { socket ->
            socket.soTimeout = DISCOVERY_TIMEOUT_MS.toInt()
            socket.broadcast = true

            val broadcastAddresses = getBroadcastAddresses()
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
                    Log.d(TAG, "Discovery request sent to ${broadcastAddr.hostAddress}")
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to send discovery request: ${e.message}")
                }
            }

            val startTime = System.currentTimeMillis()
            while (coroutineContext.isActive && (System.currentTimeMillis() - startTime < DISCOVERY_TIMEOUT_MS)) {
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
                    if (!response.startsWith(DISCOVERY_RESPONSE_PREFIX)) {
                        continue
                    }

                    val serverIp = responsePacket.address.hostAddress ?: continue
                    val localIp = getLocalIpAddress()
                    if (localIp != null && serverIp == localIp) {
                        Log.d(TAG, "Ignore local discovery response: $serverIp")
                        continue
                    }

                    val serverUrl = response.removePrefix(DISCOVERY_RESPONSE_PREFIX).trim()
                    val serverKey = "$serverIp:29027"
                    if (!servers.add(serverKey)) {
                        continue
                    }

                    val server = parseServerResponse(serverIp, serverUrl) ?: continue
                    withContext(Dispatchers.Main) {
                        onServerFound(server)
                    }
                    Log.d(TAG, "Discovered server ${server.url}")
                } catch (_: SocketTimeoutException) {
                    // Wait for remaining responses within this round.
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to receive discovery response: ${e.message}")
                }
            }
        }
    }

    fun stopDiscovery() {
        isContinuous = false
        discoveryJob?.cancel()
        discoveryJob = null
    }

    fun startPassiveListener() {
        stopPassiveListener()

        listenerJob = discoveryScope.launch {
            try {
                val socket = DatagramSocket(DISCOVERY_PORT).apply {
                    broadcast = true
                    reuseAddress = true
                    soTimeout = 1000
                }
                listenerSocket = socket
                Log.d(TAG, "Passive listener started on port $DISCOVERY_PORT")

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
                        if (request != DISCOVERY_MESSAGE) {
                            continue
                        }

                        val localIp = getLocalIpAddress() ?: continue
                        val responseUrl = "http://$localIp:29027/postqrdata"
                        val response = "$DISCOVERY_RESPONSE_PREFIX$responseUrl"
                        val responseData = response.toByteArray(StandardCharsets.UTF_8)
                        val responsePacket = DatagramPacket(
                            responseData,
                            responseData.size,
                            packet.address,
                            packet.port
                        )
                        socket.send(responsePacket)
                        Log.d(
                            TAG,
                            "Discovery response sent to ${packet.address.hostAddress} -> $responseUrl"
                        )
                    } catch (_: SocketTimeoutException) {
                        // Keep looping so cancellation can be observed quickly.
                    } catch (e: Exception) {
                        if (isActive) {
                            Log.e(TAG, "Passive listener error: ${e.message}")
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to start passive listener: ${e.message}", e)
            } finally {
                listenerSocket?.close()
                listenerSocket = null
            }
        }
    }

    fun stopPassiveListener() {
        listenerSocket?.close()
        listenerSocket = null
        listenerJob?.cancel()
        listenerJob = null
    }

    private fun getLocalIpAddress(): String? {
        return try {
            val interfaces = NetworkInterface.getNetworkInterfaces()
            while (interfaces.hasMoreElements()) {
                val networkInterface = interfaces.nextElement()
                if (networkInterface.isLoopback || !networkInterface.isUp) {
                    continue
                }

                val addresses = networkInterface.inetAddresses
                while (addresses.hasMoreElements()) {
                    val address = addresses.nextElement()
                    if (address is Inet4Address && !address.isLoopbackAddress) {
                        return address.hostAddress
                    }
                }
            }
            null
        } catch (e: Exception) {
            Log.e(TAG, "Failed to get local ip: ${e.message}")
            null
        }
    }

    private fun getBroadcastAddresses(): List<InetAddress> {
        val addresses = mutableListOf<InetAddress>()

        try {
            val interfaces = NetworkInterface.getNetworkInterfaces()
            while (interfaces.hasMoreElements()) {
                val networkInterface = interfaces.nextElement()
                if (networkInterface.isLoopback || !networkInterface.isUp) {
                    continue
                }

                val inetAddresses = networkInterface.inetAddresses
                while (inetAddresses.hasMoreElements()) {
                    val inetAddress = inetAddresses.nextElement()
                    if (inetAddress is Inet4Address) {
                        val broadcast = getBroadcastAddress(inetAddress, networkInterface)
                        if (broadcast != null && !addresses.contains(broadcast)) {
                            addresses.add(broadcast)
                        }
                    }
                }
            }

            if (addresses.isEmpty()) {
                addresses.add(InetAddress.getByName("255.255.255.255"))
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to enumerate broadcast addresses: ${e.message}")
            try {
                addresses.add(InetAddress.getByName("255.255.255.255"))
            } catch (ex: Exception) {
                Log.e(TAG, "Fallback broadcast address failed: ${ex.message}")
            }
        }

        return addresses
    }

    private fun getBroadcastAddress(
        inetAddress: Inet4Address,
        networkInterface: NetworkInterface
    ): InetAddress? {
        return try {
            networkInterface.interfaceAddresses
                .firstOrNull { it.address == inetAddress }
                ?.broadcast
        } catch (e: Exception) {
            Log.e(TAG, "Failed to get broadcast address: ${e.message}")
            null
        }
    }

    private fun parseServerResponse(ip: String, response: String): DiscoveredServer? {
        return try {
            val url = if (response.startsWith("http://") || response.startsWith("https://")) {
                response.trim()
            } else {
                "http://$ip:29027/postqrdata"
            }
            DiscoveredServer(
                ip = ip,
                port = 29027,
                url = url,
                name = "Windows瀹㈡埛绔?
            )
        } catch (e: Exception) {
            Log.e(TAG, "Failed to parse server response: ${e.message}")
            null
        }
    }

    fun cleanup() {
        stopDiscovery()
        stopPassiveListener()
        discoveryScope.cancel()
    }
}
