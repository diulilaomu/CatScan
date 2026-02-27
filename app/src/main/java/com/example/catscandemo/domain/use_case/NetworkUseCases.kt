package com.example.catscandemo.domain.use_case

import com.example.catscandemo.domain.model.NetworkScanData
import com.example.catscandemo.domain.model.ScanData

/**
 * 网络通信相关的 Use Case
 */
class NetworkUseCases(
    val uploadScanData: UploadScanDataUseCase,
    val uploadBatchScanData: UploadBatchScanDataUseCase,
    val uploadTemplateData: UploadTemplateDataUseCase,
    val checkServerConnectivity: CheckServerConnectivityUseCase,
    val startNetworkDiscovery: StartNetworkDiscoveryUseCase,
    val stopNetworkDiscovery: StopNetworkDiscoveryUseCase,
    val selectDiscoveredServer: SelectDiscoveredServerUseCase,
    val startHeartbeatDetection: StartHeartbeatDetectionUseCase,
    val stopHeartbeatDetection: StopHeartbeatDetectionUseCase
)

/**
 * 上传单条扫描数据的 Use Case
 */
class UploadScanDataUseCase(
    private val networkRepository: NetworkRepository
) {
    suspend operator fun invoke(
        scanData: ScanData,
        serverUrl: String,
        action: String = "add",
        onSuccess: () -> Unit,
        onError: (String) -> Unit
    ) {
        val networkData = NetworkScanData(
            qrdata = scanData.text,
            templateName = scanData.templateName,
            operator = scanData.operator,
            campus = scanData.campus,
            building = scanData.building,
            floor = scanData.floor,
            room = scanData.room,
            id = scanData.id,
            action = action
        )
        
        networkRepository.uploadData(
            data = networkData,
            url = serverUrl,
            onSuccess = onSuccess,
            onError = onError
        )
    }
}

/**
 * 批量上传扫描数据的 Use Case
 */
class UploadBatchScanDataUseCase(
    private val networkRepository: NetworkRepository
) {
    suspend operator fun invoke(
        scanDataList: List<ScanData>,
        serverUrl: String,
        onSuccess: () -> Unit,
        onError: (String) -> Unit
    ) {
        val networkDataList = scanDataList.map {
            NetworkScanData(
                qrdata = it.text,
                templateName = it.templateName,
                operator = it.operator,
                campus = it.campus,
                building = it.building,
                floor = it.floor,
                room = it.room,
                id = it.id,
                action = "add"
            )
        }
        
        networkRepository.uploadBatchData(
            dataList = networkDataList,
            url = serverUrl,
            onSuccess = onSuccess,
            onError = onError
        )
    }
}

/**
 * 上传模板数据的 Use Case
 */
class UploadTemplateDataUseCase(
    private val networkRepository: NetworkRepository
) {
    suspend operator fun invoke(
        templateId: String,
        templateName: String,
        scanDataList: List<ScanData>,
        serverUrl: String,
        onSuccess: () -> Unit,
        onError: (String) -> Unit
    ) {
        val networkDataList = scanDataList.map {
            NetworkScanData(
                qrdata = it.text,
                templateName = templateName,
                operator = it.operator,
                campus = it.campus,
                building = it.building,
                floor = it.floor,
                room = it.room,
                id = it.id,
                action = "add"
            )
        }
        
        networkRepository.uploadBatchData(
            dataList = networkDataList,
            url = serverUrl,
            onSuccess = onSuccess,
            onError = onError
        )
    }
}

/**
 * 检查服务器连接状态的 Use Case
 */
class CheckServerConnectivityUseCase(
    private val networkRepository: NetworkRepository
) {
    suspend operator fun invoke(serverUrl: String): Boolean {
        return networkRepository.checkConnectivity(serverUrl)
    }
}

/**
 * 开始网络发现的 Use Case
 */
class StartNetworkDiscoveryUseCase(
    private val networkRepository: NetworkRepository
) {
    operator fun invoke(
        onServerFound: (DiscoveredServer) -> Unit,
        onDiscoveryComplete: () -> Unit
    ) {
        networkRepository.startDiscovery(
            onServerFound = onServerFound,
            onDiscoveryComplete = onDiscoveryComplete
        )
    }
}

/**
 * 停止网络发现的 Use Case
 */
class StopNetworkDiscoveryUseCase(
    private val networkRepository: NetworkRepository
) {
    operator fun invoke() {
        networkRepository.stopDiscovery()
    }
}

/**
 * 选择发现的服务器的 Use Case
 */
class SelectDiscoveredServerUseCase(
    private val networkRepository: NetworkRepository
) {
    operator fun invoke(server: DiscoveredServer) {
        networkRepository.selectServer(server)
    }
}

/**
 * 开始心跳检测的 Use Case
 */
class StartHeartbeatDetectionUseCase(
    private val networkRepository: NetworkRepository
) {
    operator fun invoke(
        serverUrl: String,
        onConnectivityChanged: (Boolean) -> Unit
    ) {
        networkRepository.startHeartbeatDetection(
            serverUrl = serverUrl,
            onConnectivityChanged = onConnectivityChanged
        )
    }
}

/**
 * 停止心跳检测的 Use Case
 */
class StopHeartbeatDetectionUseCase(
    private val networkRepository: NetworkRepository
) {
    operator fun invoke() {
        networkRepository.stopHeartbeatDetection()
    }
}

/**
 * 发现的服务器模型
 */
data class DiscoveredServer(
    val ip: String,
    val port: Int,
    val url: String,
    val name: String = "Windows客户端"
)

/**
 * 网络仓库接口
 */
interface NetworkRepository {
    suspend fun uploadData(
        data: NetworkScanData,
        url: String,
        onSuccess: () -> Unit,
        onError: (String) -> Unit
    )
    
    suspend fun uploadBatchData(
        dataList: List<NetworkScanData>,
        url: String,
        onSuccess: () -> Unit,
        onError: (String) -> Unit
    )
    
    suspend fun checkConnectivity(url: String): Boolean
    
    fun startDiscovery(
        onServerFound: (DiscoveredServer) -> Unit,
        onDiscoveryComplete: () -> Unit
    )
    
    fun stopDiscovery()
    
    fun selectServer(server: DiscoveredServer)
    
    fun startHeartbeatDetection(
        serverUrl: String,
        onConnectivityChanged: (Boolean) -> Unit
    )
    
    fun stopHeartbeatDetection()
}
