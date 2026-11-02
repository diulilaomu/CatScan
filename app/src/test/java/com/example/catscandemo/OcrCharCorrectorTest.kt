package com.example.catscandemo

import com.example.catscandemo.utils.OcrCharCorrector
import com.example.catscandemo.utils.OcrCharMode
import com.example.catscandemo.utils.OcrRegexTemplates
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * 识别字符偏好单测：数字优先把形近字母还原成数字，字母优先反向，
 * 且只动"整段都是数字+形近字母、数字明显占优、形近字母紧邻数字"的片段。
 */
class OcrCharCorrectorTest {

    private fun fix(line: String, mode: OcrCharMode = OcrCharMode.DIGITS) =
        OcrCharCorrector.correct(line, mode)

    @Test
    fun `数字优先把O还原成0`() {
        assertEquals("00504202781", fix("O05042O2781"))
    }

    @Test
    fun `数字优先把S还原成5`() {
        assertEquals("512345678901", fix("S12345678901"))
    }

    @Test
    fun `数字优先把l与I还原成1`() {
        assertEquals("110504202781", fix("l1050420278I"))
    }

    @Test
    fun `数字优先把B还原成8`() {
        assertEquals("81020240102", fix("B1020240102"))
    }

    @Test
    fun `纯数字行不受影响`() {
        assertEquals("110504202781", fix("110504202781"))
    }

    @Test
    fun `含其他字母的段整段不动`() {
        // CODE / DR / ABC 不是形近字母，段内混入即视为字母编码
        assertEquals("CODE2024", fix("CODE2024"))
        assertEquals("DR110504202781", fix("DR110504202781"))
        assertEquals("B12AB34", fix("B12AB34"))
    }

    @Test
    fun `字母与数字接近对半时不纠正`() {
        // 0O 两字符对半，不猜方向
        assertEquals("0O", fix("0O"))
    }

    @Test
    fun `只纠正紧邻数字的形近字母`() {
        // A、C、D 不是形近字母，夹在中间的位置不动；只有紧邻数字的 O 被还原
        assertEquals("ABC1203456789", fix("ABC12O3456789"))
        // 同一段里字母占多数时整段不猜方向
        assertEquals("ABOCD123456789", fix("ABOCD123456789"))
    }

    @Test
    fun `中文与符号原样保留`() {
        assertEquals("编号012345号", fix("编号O12345号"))
        // 孤立的 O（段内没有数字）不纠正
        assertEquals("O-12345", fix("O-12345"))
    }

    @Test
    fun `字母优先方向相反`() {
        // SO0FT：字母占多数，0 紧邻字母 → O
        assertEquals("SOOFT", fix("SO0FT", OcrCharMode.LETTERS))
        // 纯数字段在字母优先下不动（数字占多数，方向不符）
        assertEquals("110504202781", fix("110504202781", OcrCharMode.LETTERS))
    }

    @Test
    fun `关闭模式原样返回`() {
        assertEquals("O05042O2781", fix("O05042O2781", OcrCharMode.OFF))
    }

    @Test
    fun `纠正后长度不变`() {
        val line = "O05042O2781 S12345678901"
        assertEquals(line.length, fix(line).length)
    }

    // ===== 识别链路保护：原文已命中正则就不改动 =====

    private val digits12 = OcrRegexTemplates.digits(12, 12)

    @Test
    fun `原文已命中正则时保留原文`() {
        // 前缀模板 SO+12位数字：原文已命中，不能把 SO 里的 O 改成 0
        val prefixRegex = OcrRegexTemplates.prefixDigits("SO", 12, 12)
        assertEquals(
            "SO110504202781",
            OcrCharCorrector.correctForOcr("SO110504202781", OcrCharMode.DIGITS, prefixRegex)
        )
    }

    @Test
    fun `原文未命中正则时按偏好纠正`() {
        // O05042O2781 不满足 12 位纯数字，纠正后 00504202781 才命中
        assertEquals(
            "00504202781",
            OcrCharCorrector.correctForOcr("O05042O2781", OcrCharMode.DIGITS, digits12)
        )
    }

    @Test
    fun `关闭模式不纠正也不看正则`() {
        assertEquals(
            "O05042O2781",
            OcrCharCorrector.correctForOcr("O05042O2781", OcrCharMode.OFF, digits12)
        )
    }

    @Test
    fun `批量纠正逐行生效`() {
        assertEquals(
            listOf("00504202781", "512345678901"),
            OcrCharCorrector.correctAllForOcr(
                listOf("O05042O2781", "S12345678901"),
                OcrCharMode.DIGITS,
                digits12
            )
        )
    }
}
