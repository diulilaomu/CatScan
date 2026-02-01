package com.example.catscandemo

import android.app.Application
import dagger.hilt.android.HiltAndroidApp

/**
 * CatScan 应用程序类
 * 用于初始化 Hilt 依赖注入框架
 */
@HiltAndroidApp
class CatScanApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        // 可以在这里添加应用程序初始化代码
    }
}
