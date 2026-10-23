package com.example.catscandemo.ui.main

import android.app.KeyguardManager
import android.app.PendingIntent
import android.content.Intent
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import android.graphics.drawable.Icon
import com.example.catscandemo.R

/**
 * 控制中心（通知栏下拉）快捷扫码磁贴：
 * 用户在系统控制中心的编辑里把"猫头枪扫码"拖进常用区后，
 * 点击即拉起 MainActivity 并应用快扫预设（无模板 + 自动复制 + 摄像头开启）。
 *
 * 磁贴不做有状态开关（不记忆 on/off）：每次点击都是"进入扫码"，
 * active 状态只在拉起瞬间短暂展示，回来后由 onStartListening 复位。
 */
class QuickScanTileService : TileService() {

    override fun onStartListening() {
        qsTile?.let { tile ->
            tile.icon = Icon.createWithResource(this, R.mipmap.ic_quick_scan_tile)
            tile.label = getString(R.string.quick_scan_tile_label)
            tile.state = Tile.STATE_INACTIVE
            tile.updateTile()
        }
    }

    override fun onClick() {
        val tile = qsTile
        if (tile != null) {
            tile.state = Tile.STATE_ACTIVE
            tile.updateTile()
        }
        val intent = Intent(this, MainActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            putExtra(EXTRA_QUICK_SCAN, true)
        }
        val pending = PendingIntent.getActivity(
            this,
            REQUEST_CODE,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        // startActivityAndCollapse 是磁贴拉起界面的规范入口：系统收起控制中心后
        // 再显示 Activity；锁屏上先 unlockAndRun 解锁。直接 pending.send() 不会收起面板。
        val km = getSystemService(KeyguardManager::class.java)
        if (km?.isKeyguardLocked == true) {
            unlockAndRun { runCatching { startActivityAndCollapse(pending) } }
        } else {
            runCatching { startActivityAndCollapse(pending) }
        }
        if (tile != null) {
            tile.state = Tile.STATE_INACTIVE
            tile.updateTile()
        }
    }

    companion object {
        /** MainActivity 据此应用快扫预设：无模板 + 自动复制 + 摄像头开启 */
        const val EXTRA_QUICK_SCAN = "quick_scan"
        private const val REQUEST_CODE = 1001
    }
}
