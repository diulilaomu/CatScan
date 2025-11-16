package com.example.catscandemo.data.repository
import com.example.catscandemo.data.network.CatScanClient

class ScanRepository(
    private val client: CatScanClient = CatScanClient()
) {
    fun upload(qrData: String, url: String, onSuccess: () -> Unit, onError: (String) -> Unit) {
        client.uploadToComputer(url, qrData, onSuccess, onError)
    }
}

