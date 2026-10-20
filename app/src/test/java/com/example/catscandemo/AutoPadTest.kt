package com.example.catscandemo

import com.example.catscandemo.domain.model.SeqConfig
import com.example.catscandemo.domain.model.formatSeqValue
import org.junit.Assert.assertEquals
import org.junit.Test

/** 自动补零（位数=0）：位数跟随总条数，10条→01..09、100条→001..099 */
class AutoPadTest {

    private val auto = SeqConfig(autoPad = true, padWidth = 0)

    @Test
    fun autoPad_followsTotalCount() {
        assertEquals("01", formatSeqValue(auto, 1, totalForAuto = 10))
        assertEquals("09", formatSeqValue(auto, 9, totalForAuto = 10))
        assertEquals("10", formatSeqValue(auto, 10, totalForAuto = 10))
        assertEquals("001", formatSeqValue(auto, 1, totalForAuto = 100))
        assertEquals("099", formatSeqValue(auto, 99, totalForAuto = 100))
        assertEquals("100", formatSeqValue(auto, 100, totalForAuto = 100))
    }

    @Test
    fun autoPad_keepsPrefixSuffixAndValueThatExceedsTotal() {
        val cfg = auto.copy(prefix = "A-", suffix = "#")
        assertEquals("A-01#", formatSeqValue(cfg, 1, totalForAuto = 10))
        // 序号本身超过总条数时按序号自身位数
        assertEquals("A-150#", formatSeqValue(cfg, 150, totalForAuto = 100))
    }

    @Test
    fun fixedPadWidth_overridesAuto() {
        val cfg = SeqConfig(padWidth = 3, autoPad = true)
        assertEquals("005", formatSeqValue(cfg, 5, totalForAuto = 10))
    }

    @Test
    fun padOff_whenSwitchOff() {
        val cfg = SeqConfig(padWidth = 0, autoPad = false)
        assertEquals("5", formatSeqValue(cfg, 5, totalForAuto = 10))
    }
}
