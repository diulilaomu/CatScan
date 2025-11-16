package com.example.catscandemo.domain.model

data class ScanResult(
    val raw: String,
    val timestamp: Long = System.currentTimeMillis()
)
