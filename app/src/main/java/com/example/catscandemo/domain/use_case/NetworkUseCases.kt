package com.example.catscandemo.domain.use_case

import com.example.catscandemo.domain.model.NetworkScanData
import com.example.catscandemo.domain.model.ScanData

/**
 * 缃戠粶閫氫俊鐩稿叧鐨?Use Case
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
 * 涓婁紶鍗曟潯鎵弿鏁版嵁鐨?Use Case
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
 * 鎵归噺涓婁紶鎵弿鏁版嵁鐨?Use Case
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
 * 涓婁紶妯℃澘鏁版嵁鐨?Use Case
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
 * 妫€鏌ユ湇鍔″櫒杩炴帴鐘舵€佺殑 Use Case
 */
class CheckServerConnectivityUseCase(
    private val networkRepository: NetworkRepository
) {
    suspend operator fun invoke(serverUrl: String): Boolean {
        return networkRepository.checkConnectivity(serverUrl)
    }
}

/**
 * 寮€濮嬬綉缁滃彂鐜扮殑 Use Case
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
 * 鍋滄缃戠粶鍙戠幇鐨?Use Case
 */
class StopNetworkDiscoveryUseCase(
    private val networkRepository: NetworkRepository
) {
    operator fun invoke() {
        networkRepository.stopDiscovery()
    }
}

/**
 * 閫夋嫨鍙戠幇鐨勬湇鍔″櫒鐨?Use Case
 */
class SelectDiscoveredServerUseCase(
    private val networkRepository: NetworkRepository
) {
    operator fun invoke(server: DiscoveredServer) {
        networkRepository.selectServer(server)
    }
}

/**
 * 寮€濮嬪績璺虫娴嬬殑 Use Case
 */
class StartHeartbeatDetectionUseCase(
    private val networkRepository: NetworkRepository
) {
    operator fun invoke(
        serverUrl: String,
        onConnectivityChanged: (Boolean) -> Unit,
        onBlocked: (String) -> Unit
    ) {
        networkRepository.startHeartbeatDetection(
            serverUrl = serverUrl,
            onConnectivityChanged = onConnectivityChanged,
            onBlocked = onBlocked
        )
    }
}

/**
 * 鍋滄蹇冭烦妫€娴嬬殑 Use Case
 */
class StopHeartbeatDetectionUseCase(
    private val networkRepository: NetworkRepository
) {
    operator fun invoke() {
        networkRepository.stopHeartbeatDetection()
    }
}

/**
 * 鍙戠幇鐨勬湇鍔″櫒妯″瀷
 */
data class DiscoveredServer(
    val ip: String,
    val port: Int,
    val url: String,
    val name: String = "Windows瀹㈡埛绔?
)

/**
 * 缃戠粶浠撳簱鎺ュ彛
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
        onConnectivityChanged: (Boolean) -> Unit,
        onBlocked: (String) -> Unit
    )
    
    fun stopHeartbeatDetection()
}
