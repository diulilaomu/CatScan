package com.example.catscandemo

import com.example.catscandemo.domain.model.ScanData
import com.example.catscandemo.domain.model.TemplateMode
import com.example.catscandemo.domain.model.TemplateModel
import com.example.catscandemo.presentation.viewmodel.ScanCursor
import com.example.catscandemo.presentation.viewmodel.advanceScanCursor
import com.example.catscandemo.presentation.viewmodel.cursorFromSelection
import com.example.catscandemo.presentation.viewmodel.peekScanPosition
import com.example.catscandemo.presentation.viewmodel.restoredScanCursor
import org.junit.Assert.assertEquals
import org.junit.Test

class LinearCursorTest {

    private fun template(
        tags: List<String> = emptyList(),
        scans: List<ScanData> = emptyList()
    ) = TemplateModel(
        mode = TemplateMode.LINEAR,
        maxFloor = 2,
        roomCountPerFloor = 2,
        selectedRooms = listOf("101", "102", "201", "202"),
        tags = tags,
        scans = scans
    )

    @Test
    fun linearSequenceAdvancesRoomPerScan() {
        val t = template()
        var cursor = ScanCursor()
        val rooms = mutableListOf<String>()
        repeat(4) {
            rooms += peekScanPosition(t, cursor)!!.room
            cursor = advanceScanCursor(t, cursor)
        }
        // 2层2房：101→102→201→202，一轮用完循环回 101
        assertEquals(listOf("101", "102", "201", "202"), rooms)
        assertEquals("101", peekScanPosition(t, cursor)!!.room)
    }

    @Test
    fun tagsCycleFastestWithinRoom() {
        val t = template(tags = listOf("空调", "照明"))
        var cursor = ScanCursor()
        val seq = mutableListOf<String>()
        repeat(4) {
            val pos = peekScanPosition(t, cursor)!!
            seq += "${pos.room}${pos.tag}"
            cursor = advanceScanCursor(t, cursor)
        }
        // 标签推进最快：101空调→101照明→102空调→102照明→201空调
        assertEquals(listOf("101空调", "101照明", "102空调", "102照明"), seq)
        val next = peekScanPosition(t, cursor)!!
        assertEquals("201空调", "${next.room}${next.tag}")
    }

    @Test
    fun restartWithNoScansStartsFromFirstRoom() {
        // 新模板（无任何扫码记录）恢复游标不应前进一步，首扫从 101 开始
        val t = template()
        val pos = peekScanPosition(t, restoredScanCursor(t))!!
        assertEquals(1, pos.floor)
        assertEquals("101", pos.room)
    }

    @Test
    fun restartContinuesAfterLastScannedRecord() {
        // scans 为最新在先：最近一条是 1 层 102，恢复后应从 201 继续
        val t = template(
            scans = listOf(
                ScanData(text = "newest", floor = "1层", room = "102"),
                ScanData(text = "older", floor = "1层", room = "101")
            )
        )
        val pos = peekScanPosition(t, restoredScanCursor(t))!!
        assertEquals(2, pos.floor)
        assertEquals("201", pos.room)
    }

    @Test
    fun restartWrapsToFirstRoomAfterLast() {
        val t = template(
            scans = listOf(ScanData(text = "last", floor = "2层", room = "202"))
        )
        val pos = peekScanPosition(t, restoredScanCursor(t))!!
        assertEquals(1, pos.floor)
        assertEquals("101", pos.room)
    }

    @Test
    fun restartWithTagsContinuesTagSequence() {
        val t = template(
            tags = listOf("空调", "照明"),
            scans = listOf(ScanData(text = "last", floor = "1层", room = "101", tag = "空调"))
        )
        val pos = peekScanPosition(t, restoredScanCursor(t))!!
        assertEquals("101", pos.room)
        assertEquals("照明", pos.tag)
    }

    @Test
    fun cursorFromSelectionLocatesChosenRoomAndTag() {
        val t = template(tags = listOf("空调", "照明"))
        val cursor = cursorFromSelection(t, 2, "201", "照明")
        val pos = peekScanPosition(t, cursor)!!
        assertEquals(2, pos.floor)
        assertEquals("201", pos.room)
        assertEquals("照明", pos.tag)
    }
}
