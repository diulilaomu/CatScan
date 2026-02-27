package com.example.catscandemo.domain.model

import java.util.UUID

/**
 * 基础扫描数据模型
 * 包含所有扫描数据的通用字段
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
 * 模板模型
 * 包含模板配置和扫描数据
 */
data class TemplateModel(
    val id: String = UUID.randomUUID().toString(),
    val name: String = "未命名模板",
    val operator: String = "",
    val campus: String = "",
    val building: String = "",
    val maxFloor: Int = 1,
    val roomCountPerFloor: Int = 1,
    val selectedRooms: List<String> = emptyList(),
    val scans: List<ScanData> = emptyList()
)

/**
 * 扫描结果模型
 * 用于识别结果列表
 */
data class ScanResult(
    val id: Long,
    val index: Int,
    val scanData: ScanData,
    val uploaded: Boolean = false
)

/**
 * 网络传输模型
 * 用于与PC客户端通信
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
