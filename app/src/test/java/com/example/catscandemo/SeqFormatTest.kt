package com.example.catscandemo

import com.example.catscandemo.domain.model.SeqConfig
import com.example.catscandemo.domain.model.formatSeqValue
import com.example.catscandemo.domain.model.toChineseUpper
import org.junit.Assert.assertEquals
import org.junit.Test

class SeqFormatTest {

    @Test
    fun chineseUpperBasics() {
        assertEquals("零", toChineseUpper(0))
        assertEquals("壹", toChineseUpper(1))
        assertEquals("玖", toChineseUpper(9))
        assertEquals("壹拾", toChineseUpper(10))
        assertEquals("壹拾壹", toChineseUpper(11))
        assertEquals("贰拾", toChineseUpper(20))
        assertEquals("贰拾壹", toChineseUpper(21))
    }

    @Test
    fun chineseUpperHundredsAndThousands() {
        assertEquals("壹佰", toChineseUpper(100))
        assertEquals("壹佰零壹", toChineseUpper(101))
        assertEquals("壹佰壹拾", toChineseUpper(110))
        assertEquals("壹佰壹拾壹", toChineseUpper(111))
        assertEquals("玖佰玖拾玖", toChineseUpper(999))
        assertEquals("壹仟", toChineseUpper(1000))
        assertEquals("壹仟零壹", toChineseUpper(1001))
        assertEquals("壹仟零壹拾", toChineseUpper(1010))
    }

    @Test
    fun chineseUpperGroups() {
        assertEquals("壹万", toChineseUpper(10000))
        assertEquals("壹万零壹", toChineseUpper(10001))
        assertEquals("壹万零壹佰", toChineseUpper(10100))
        assertEquals("壹拾贰万叁仟肆佰伍拾陆", toChineseUpper(123456))
        assertEquals("负伍", toChineseUpper(-5))
    }

    @Test
    fun formatSeqWithAffixes() {
        assertEquals("P-1", formatSeqValue(SeqConfig(prefix = "P-", padWidth = 0), 1))
        assertEquals("P-123-END", formatSeqValue(SeqConfig(prefix = "P-", suffix = "-END", padWidth = 0), 123))
        assertEquals("设备·壹号", formatSeqValue(SeqConfig(prefix = "设备·", suffix = "号", chineseUpper = true), 1))
        assertEquals("第贰拾项", formatSeqValue(SeqConfig(prefix = "第", suffix = "项", chineseUpper = true), 20))
    }

    @Test
    fun formatSeqZeroPadding() {
        // 默认 padWidth=2：补到两位
        assertEquals("P-01", formatSeqValue(SeqConfig(prefix = "P-"), 1))
        assertEquals("P-10", formatSeqValue(SeqConfig(prefix = "P-"), 10))
        assertEquals("P-100", formatSeqValue(SeqConfig(prefix = "P-"), 100))
        // 指定位数
        assertEquals("001", formatSeqValue(SeqConfig(padWidth = 3), 1))
        assertEquals("007-END", formatSeqValue(SeqConfig(suffix = "-END", padWidth = 3), 7))
        // 中文大写不受补零影响
        assertEquals("壹号", formatSeqValue(SeqConfig(suffix = "号", chineseUpper = true, padWidth = 3), 1))
    }
}
