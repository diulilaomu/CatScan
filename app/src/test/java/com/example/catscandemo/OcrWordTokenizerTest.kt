package com.example.catscandemo

import com.example.catscandemo.utils.OcrRegexMatcher
import com.example.catscandemo.utils.OcrRegexTemplates
import com.example.catscandemo.utils.OcrWordTokenizer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Big Bang 涂抹选词分词器单测：
 * 正则命中区段整块成 token、空隙文字用注入的假分词器切词、
 * 区段合并与边界情形。ICU 真分词不进 JVM 单测（依赖 android.icu）。
 */
class OcrWordTokenizerTest {

    // 假分词器：按字符逐个切（最细粒度），便于断言空隙的切分位置
    private val perChar: (String) -> List<String> = { text -> text.map { it.toString() } }

    private fun texts(tokens: List<OcrWordTokenizer.OcrToken>) = tokens.map { it.text }

    @Test
    fun `命中区段整块成token`() {
        val regex = OcrRegexTemplates.digits(4, 4)
        val tokens = OcrWordTokenizer.split("编号1234完", OcrRegexMatcher.findAllMatchRanges("编号1234完", regex), perChar)
        assertTrue(tokens[0].let { !it.isRegexMatch })
        assertEquals(listOf("编", "号"), tokens.take(2).map { it.text })
        val hit = tokens.first { it.isRegexMatch }
        assertEquals("1234", hit.text)
    }

    @Test
    fun `空隙按注入分词器切分`() {
        // 假分词器按两个字一组切，验证空隙确实走了 breakWords
        val byPair: (String) -> List<String> = { text ->
            text.chunked(2)
        }
        val tokens = OcrWordTokenizer.split("前缀A1B2后缀", listOf(2..5), byPair)
        // 空隙 "前缀" → 一块；命中 "A1B2"；空隙 "后缀" → 一块
        assertEquals(listOf("前缀", "A1B2", "后缀"), texts(tokens))
        assertEquals(listOf(false, true, false), tokens.map { it.isRegexMatch })
    }

    @Test
    fun `空正则整行一个命中token`() {
        val tokens = OcrWordTokenizer.split("整行文字", OcrRegexMatcher.findAllMatchRanges("整行文字", ""), perChar)
        assertEquals(1, tokens.size)
        assertEquals("整行文字", tokens[0].text)
        assertTrue(tokens[0].isRegexMatch)
    }

    @Test
    fun `非法正则同样整行一块`() {
        val tokens = OcrWordTokenizer.split("abc", OcrRegexMatcher.findAllMatchRanges("abc", "([0-9"), perChar)
        assertEquals(listOf("abc"), texts(tokens))
        assertTrue(tokens[0].isRegexMatch)
    }

    @Test
    fun `重叠区段合并后不再细拆`() {
        // "1234" 与 "34" 重叠 → 合并成 0..3 一个区段
        val tokens = OcrWordTokenizer.split("1234", listOf(0..3, 2..3), perChar)
        assertEquals(listOf("1234"), texts(tokens))
    }

    @Test
    fun `相接区段已在Matcher层合并`() {
        // findAllMatchRanges 已把相接区段合并成 0..3；tokenizer 只见一个区段
        val ranges = OcrRegexMatcher.findAllMatchRanges("1234", "\\d\\d")
        val tokens = OcrWordTokenizer.split("1234", ranges, perChar)
        assertEquals(listOf("1234"), texts(tokens))
        assertTrue(tokens.all { it.isRegexMatch })
    }

    @Test
    fun `无命中行全部是空隙词块`() {
        val regex = OcrRegexTemplates.digits(8, 8)
        val tokens = OcrWordTokenizer.split("没有数字", OcrRegexMatcher.findAllMatchRanges("没有数字", regex), perChar)
        assertEquals(listOf("没", "有", "数", "字"), texts(tokens))
        assertTrue(tokens.none { it.isRegexMatch })
    }

    @Test
    fun `命中之后的空隙也参与切分`() {
        val tokens = OcrWordTokenizer.split("99瓶", listOf(0..1), perChar)
        assertEquals(listOf("99", "瓶"), texts(tokens))
    }

    @Test
    fun `空白行不产出token`() {
        assertTrue(OcrWordTokenizer.split("   ", emptyList(), perChar).isEmpty())
    }

    @Test
    fun `乱序区段先按起点排序处理`() {
        // 传入前先排序是调用方（findAllMatchRanges）的约定；直接传乱序时 split 按起点排序处理
        // a0 b1 c2 d3 e4 f5：0..1="ab"，空隙 c，3..4="de"，空隙 f
        val tokens = OcrWordTokenizer.split("abcdef", listOf(3..4, 0..1), perChar)
        assertEquals(listOf("ab", "c", "de", "f"), texts(tokens))
    }
}
