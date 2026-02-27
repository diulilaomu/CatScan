package com.example.catscandemo.domain.model

import java.util.UUID

/**
 * 鍩虹鎵弿鏁版嵁妯″瀷
 * 鍖呭惈鎵€鏈夋壂鎻忔暟鎹殑閫氱敤瀛楁
 */
data class ScanData(
    val id: String = UUID.randomUUID().toString(),
    val text: String,
    val timestamp: Long = System.currentTimeMillis(),
    val operator: String = "unknown",
    val campus: String = "",
    val building: String = "",
    val floor: String = "",
    val room: String = "",
    val templateId: String = "",
    val templateName: String = "",
    val uploaded: Boolean = false
)

/**
 * 妯℃澘妯″瀷
 * 鍖呭惈妯℃澘閰嶇疆鍜屾壂鎻忔暟鎹? */
data class TemplateModel(
    val id: String = UUID.randomUUID().toString(),
    val name: String = "鏈懡鍚嶆ā鏉?,
    val operator: String = "",
    val campus: String = "",
    val building: String = "",
    val maxFloor: Int = 1,
    val roomCountPerFloor: Int = 1,
    val selectedRooms: List<String> = emptyList(),
    val scans: List<ScanData> = emptyList()
)

/**
 * 鎵弿缁撴灉妯″瀷
 * 鐢ㄤ簬璇嗗埆缁撴灉鍒楄〃
 */
data class ScanResult(
    val id: Long,
    val index: Int,
    val scanData: ScanData,
    val uploaded: Boolean = false
)

/**
 * 缃戠粶浼犺緭妯″瀷
 * 鐢ㄤ簬涓嶱C瀹㈡埛绔€氫俊
 */
data class NetworkScanData(
    val qrdata: String,
    val templateName: String,
    val operator: String,
    val campus: String,
    val building: String,
    val floor: String,
    val room: String,
    val id: String,
    val action: String = "add"
)
