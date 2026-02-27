package com.example.catscandemo

import android.app.Application
import dagger.hilt.android.HiltAndroidApp

/**
 * CatScan 搴旂敤绋嬪簭绫? * 鐢ㄤ簬鍒濆鍖?Hilt 渚濊禆娉ㄥ叆妗嗘灦
 */
@HiltAndroidApp
class CatScanApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        // 鍙互鍦ㄨ繖閲屾坊鍔犲簲鐢ㄧ▼搴忓垵濮嬪寲浠ｇ爜
    }
}
