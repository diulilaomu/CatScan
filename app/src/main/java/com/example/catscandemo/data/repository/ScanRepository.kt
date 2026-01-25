package com.example.catscandemo.data.repository

import com.example.catscandemo.data.network.CatScanClient

class ScanRepository(
    private val client: CatScanClient = CatScanClient()
) {
    fun upload(
        qrData: String,
        url: String,
        templateName: String? = null,
        operator: String? = null,
        campus: String? = null,
        building: String? = null,
        floor: String? = null,
        room: String? = null,
        onSuccess: () -> Unit,
        onError: (String) -> Unit
    ) {
        client.uploadToComputer(
            url, qrData,
            templateName, operator, campus, building, floor, room,
            onSuccess, onError
        )
    }
}

