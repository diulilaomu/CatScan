package com.example.catscandemo

import com.example.catscandemo.domain.model.ScanData
import com.example.catscandemo.domain.model.TemplateMode
import com.example.catscandemo.domain.model.TemplateModel
import com.example.catscandemo.presentation.viewmodel.nextLinearRoomIndex
import org.junit.Assert.assertEquals
import org.junit.Test

class LinearCursorTest {

    @Test
    fun restartContinuesAfterLastScannedRoomOnEachFloor() {
        val template = TemplateModel(
            mode = TemplateMode.LINEAR,
            maxFloor = 2,
            roomCountPerFloor = 3,
            selectedRooms = listOf("101", "102", "103", "201", "202", "203"),
            scans = listOf(
                ScanData(text = "floor-2", floor = "2层", room = "202"),
                ScanData(text = "floor-1", floor = "1层", room = "102")
            )
        )

        assertEquals(2, nextLinearRoomIndex(template, 1))
        assertEquals(2, nextLinearRoomIndex(template, 2))
    }

    @Test
    fun restartWrapsToFirstRoomAfterLastRoom() {
        val template = TemplateModel(
            mode = TemplateMode.LINEAR,
            selectedRooms = listOf("101", "102", "103"),
            scans = listOf(
                ScanData(text = "last", floor = "1层", room = "103")
            )
        )

        assertEquals(0, nextLinearRoomIndex(template, 1))
    }
}
