package com.example.catscandemo

import com.example.catscandemo.utils.OcrRegexMatcher
import com.example.catscandemo.utils.OcrRegexTemplates
import com.example.catscandemo.utils.ocrRuleLabel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * OCR 正则提取语义单测——需求给出的 5 个示例是验收标准：
 * 12 位纯数字正则下，"110504202781" 与 "dr110504202781" 录入 12 位数字，
 * 其余三种脏数据一律不录入。
 */
class OcrRegexMatcherTest {

    private val digits12 = OcrRegexTemplates.digits(12, 12)

    @Test
    fun `纯12位数字整行命中`() {
        assertEquals(listOf("110504202781"), OcrRegexMatcher.findAllMatches("110504202781", digits12))
    }

    @Test
    fun `前缀字母时仍提取12位数字`() {
        assertEquals(listOf("110504202781"), OcrRegexMatcher.findAllMatches("dr110504202781", digits12))
    }

    @Test
    fun `14位数字串不截取前12位`() {
        assertTrue(OcrRegexMatcher.findAllMatches("dr11050420278111", digits12).isEmpty())
    }

    @Test
    fun `10位数字不命中12位模板`() {
        assertTrue(OcrRegexMatcher.findAllMatches("dr110504202", digits12).isEmpty())
    }

    @Test
    fun `混杂中文与短数字段不命中`() {
        assertTrue(OcrRegexMatcher.findAllMatches("dr11111a2号2233", digits12).isEmpty())
    }

    @Test
    fun `空白正则视为任意文字整行录入`() {
        assertEquals(listOf("任意一行文字"), OcrRegexMatcher.findAllMatches(" 任意一行文字 ", ""))
    }

    @Test
    fun `非法正则回退为整行而不是崩溃`() {
        assertEquals(listOf("abc"), OcrRegexMatcher.findAllMatches("abc", "([0-9"))
        assertNull(OcrRegexMatcher.compileOrNull("([0-9"))
    }

    @Test
    fun `区间长度模板`() {
        val r = OcrRegexTemplates.digits(6, 8)
        assertEquals(listOf("123456"), OcrRegexMatcher.findAllMatches("编号123456完", r))
        // 9 位超过区间上限，不命中
        assertTrue(OcrRegexMatcher.findAllMatches("123456789", r).isEmpty())
        assertEquals(listOf("1234567"), OcrRegexMatcher.findAllMatches("v1234567", r))
    }

    @Test
    fun `字母数字模板不跨过符号`() {
        val r = OcrRegexTemplates.alnum(10, 12)
        assertEquals(listOf("AB12CD34EF"), OcrRegexMatcher.findAllMatches("SN-AB12CD34EF-01", r))
        // 14 位超上限，且中间无符号可断开，不命中
        assertTrue(OcrRegexMatcher.findAllMatches("AB12CD34EF5678", r).isEmpty())
    }

    @Test
    fun `手机号模板`() {
        val r = OcrRegexTemplates.PHONE
        assertEquals(listOf("13812345678"), OcrRegexMatcher.findAllMatches("电话13812345678", r))
        // 12 位数字串不是手机号
        assertTrue(OcrRegexMatcher.findAllMatches("138123456789", r).isEmpty())
        // 第二位 2 不合法
        assertTrue(OcrRegexMatcher.findAllMatches("12812345678", r).isEmpty())
    }

    @Test
    fun `邮箱模板`() {
        val r = OcrRegexTemplates.EMAIL
        assertEquals(
            listOf("test.user@example.com"),
            OcrRegexMatcher.findAllMatches("邮箱 test.user@example.com 收", r)
        )
    }

    @Test
    fun `一行内多个命中全部提取并去重`() {
        val r = OcrRegexTemplates.digits(4, 4)
        assertEquals(
            listOf("1105", "0402"),
            OcrRegexMatcher.findAllMatches("1105-0402-1105", r)
        )
    }

    @Test
    fun `空白行不产出任何结果`() {
        assertTrue(OcrRegexMatcher.findAllMatches("   ", "").isEmpty())
    }

    // ===== 生效规则展示文案（识别区底部标签） =====

    @Test
    fun `内置数字模板区间显示中文描述`() {
        assertEquals(
            "11~12位纯数字",
            ocrRuleLabel(
                OcrRegexTemplates.digits(11, 12), "digits",
                """{"digitsMin":"11","digitsMax":"12"}"""
            )
        )
    }

    @Test
    fun `内置数字模板精确长度显示中文描述`() {
        assertEquals(
            "12位纯数字",
            ocrRuleLabel(
                OcrRegexTemplates.digits(12, 12), "digits",
                """{"digitsMin":"12","digitsMax":"12"}"""
            )
        )
    }

    @Test
    fun `内置字母数字模板显示中文描述`() {
        assertEquals(
            "10~12位字母+数字",
            ocrRuleLabel(
                OcrRegexTemplates.alnum(10, 12), "alnum",
                """{"alnumMin":"10","alnumMax":"12"}"""
            )
        )
    }

    @Test
    fun `手机号与邮箱模板显示中文描述`() {
        assertEquals(
            "手机号",
            ocrRuleLabel(OcrRegexTemplates.PHONE, "phone", "{}")
        )
        assertEquals(
            "邮箱",
            ocrRuleLabel(OcrRegexTemplates.EMAIL, "email", "{}")
        )
    }

    @Test
    fun `手改过的正则显示原文而不是模板描述`() {
        // 模板参数 8~15 但正则被手动改成 12 位精确：不能再说"8~15位纯数字"
        assertEquals(
            OcrRegexTemplates.digits(12, 12),
            ocrRuleLabel(
                OcrRegexTemplates.digits(12, 12), "digits",
                """{"digitsMin":"8","digitsMax":"15"}"""
            )
        )
    }

    @Test
    fun `空正则显示整行录入`() {
        assertEquals("整行录入", ocrRuleLabel("", "digits", """{"digitsMin":"8"}"""))
        assertEquals("整行录入", ocrRuleLabel("  ", "", ""))
    }

    // ===== 特定字符开头 + 数字 模板 =====

    @Test
    fun `前缀模板命中即录入含前缀整段`() {
        val r = OcrRegexTemplates.prefixDigits("DR", 12, 12)
        assertEquals(listOf("DR110504202347"), OcrRegexMatcher.findAllMatches("DR110504202347", r))
    }

    @Test
    fun `前缀模板忽略前缀外的干扰字符`() {
        val r = OcrRegexTemplates.prefixDigits("DR", 12, 12)
        assertEquals(
            listOf("DR110504202347"),
            OcrRegexMatcher.findAllMatches("编号 DR110504202347 完", r)
        )
    }

    @Test
    fun `前缀被更长字母串包裹不命中`() {
        val r = OcrRegexTemplates.prefixDigits("DR", 12, 12)
        assertTrue(OcrRegexMatcher.findAllMatches("ADR110504202347", r).isEmpty())
    }

    @Test
    fun `前缀模板数字超长不截取`() {
        val r = OcrRegexTemplates.prefixDigits("DR", 12, 12)
        assertTrue(OcrRegexMatcher.findAllMatches("DR110504202347889", r).isEmpty())
    }

    @Test
    fun `前缀模板数字不足不命中`() {
        val r = OcrRegexTemplates.prefixDigits("DR", 12, 12)
        assertTrue(OcrRegexMatcher.findAllMatches("DR110504202", r).isEmpty())
    }

    @Test
    fun `前缀模板大小写敏感`() {
        val r = OcrRegexTemplates.prefixDigits("DR", 12, 12)
        assertTrue(OcrRegexMatcher.findAllMatches("dr110504202347", r).isEmpty())
    }

    @Test
    fun `前缀模板区间长度`() {
        val r = OcrRegexTemplates.prefixDigits("DR", 8, 12)
        assertEquals(listOf("DR110504202347"), OcrRegexMatcher.findAllMatches("DR110504202347", r))
        assertEquals(listOf("DR11050420"), OcrRegexMatcher.findAllMatches("DR11050420", r))
        assertTrue(OcrRegexMatcher.findAllMatches("DR110504", r).isEmpty())
    }

    @Test
    fun `前缀含正则特殊字符按字面匹配`() {
        // "#" 开头（非字母数字）不加前界，可出现在任意位置；"." 按字面匹配
        val r = OcrRegexTemplates.prefixDigits("#A.", 4, 4)
        assertEquals(listOf("#A.2345"), OcrRegexMatcher.findAllMatches("X#A.2345", r))
        assertTrue(OcrRegexMatcher.findAllMatches("#AB2345", r).isEmpty())
    }

    @Test
    fun `前缀模板标签显示开头加位数`() {
        assertEquals(
            "DR开头12位数字",
            ocrRuleLabel(
                OcrRegexTemplates.prefixDigits("DR", 12, 12), "prefixDigits",
                """{"prefixText":"DR","prefixMin":"12","prefixMax":"12"}"""
            )
        )
        assertEquals(
            "DR开头8~12位数字",
            ocrRuleLabel(
                OcrRegexTemplates.prefixDigits("DR", 8, 12), "prefixDigits",
                """{"prefixText":"DR","prefixMin":"8","prefixMax":"12"}"""
            )
        )
    }

    // ===== findAllMatchRanges（Big Bang 涂抹选词的命中区段） =====

    @Test
    fun `区段与findAllMatches一致`() {
        val r = OcrRegexTemplates.digits(4, 4)
        assertEquals(listOf(0..3, 5..8), OcrRegexMatcher.findAllMatchRanges("1105-0402", r))
    }

    @Test
    fun `区段基于trim后坐标`() {
        val r = OcrRegexTemplates.digits(4, 4)
        assertEquals(listOf(2..5), OcrRegexMatcher.findAllMatchRanges("  ab1234cd", r))
    }

    @Test
    fun `空正则返回整行区段`() {
        assertEquals(listOf(0..2), OcrRegexMatcher.findAllMatchRanges("abc", ""))
    }

    @Test
    fun `非法正则返回整行区段而不是崩溃`() {
        assertEquals(listOf(0..2), OcrRegexMatcher.findAllMatchRanges("abc", "([0-9"))
    }

    @Test
    fun `无命中返回空区段列表`() {
        assertTrue(OcrRegexMatcher.findAllMatchRanges("abc", OcrRegexTemplates.digits(4, 4)).isEmpty())
    }

    @Test
    fun `空白行返回空`() {
        assertTrue(OcrRegexMatcher.findAllMatchRanges("   ", "").isEmpty())
    }

    @Test
    fun `lookaround边界不产生重叠区段`() {
        // 14 位数字串配 4 位模板：lookaround 保证 12 位里不截取前 4 位，整串不命中
        val r = OcrRegexTemplates.digits(4, 4)
        assertTrue(OcrRegexMatcher.findAllMatchRanges("123456781234", r).isEmpty())
    }

    @Test
    fun `相接命中合并成一个区段`() {
        // "1234" 配裸 2 位模板命中 "12"(0..1) 与 "34"(2..3)，相接 → 合并 0..3
        assertEquals(listOf(0..3), OcrRegexMatcher.findAllMatchRanges("1234", "\\d\\d"))
    }

    @Test
    fun `重叠命中合并成一个区段`() {
        // "abcd" 配 "a|abc" 类多分支命中 (0..0) 与 (0..2) → 合并 0..2
        assertEquals(listOf(0..2), OcrRegexMatcher.findAllMatchRanges("abcd", "abc|a"))
    }
}
