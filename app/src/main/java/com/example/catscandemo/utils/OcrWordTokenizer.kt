package com.example.catscandemo.utils

import android.icu.text.BreakIterator
import java.util.Locale

/**
 * Big Bang 涂抹选词的分词器：把一行识别文字切成可点选/涂抹的词块。
 *
 * 切分规则：
 * - 正则命中区段（[OcrRegexMatcher.findAllMatchRanges] 的输出）各自整块成 token，不再细拆；
 * - 命中区段之间的空隙文字用 ICU BreakIterator（系统内置中文词典）切词，
 *   中文按词典切、连续英文字母/数字保持完整、纯空白丢弃。
 */
object OcrWordTokenizer {

    /** 一个可涂抹的词块；isRegexMatch=true 时 UI 高亮显示 */
    data class OcrToken(val text: String, val isRegexMatch: Boolean)

    /**
     * @param line 一行识别文字（调用前应已 trim，坐标与 matchRanges 对齐）
     * @param matchRanges 正则命中区段（基于 line 的下标）
     * @param breakWords 空隙文字的分词函数，默认走 ICU；单测可注入假实现
     */
    fun split(
        line: String,
        matchRanges: List<IntRange>,
        breakWords: (String) -> List<String> = ::icuBreakWords
    ): List<OcrToken> {
        if (line.isBlank()) return emptyList()
        val tokens = mutableListOf<OcrToken>()
        var cursor = 0
        for (range in matchRanges.sortedBy { it.first }) {
            if (range.first < cursor) continue  // 与前一命中重叠的只保留先出现的
            if (range.first > cursor) {
                tokens.addAll(gapTokens(line, cursor until range.first, breakWords))
            }
            tokens.add(OcrToken(line.substring(range), isRegexMatch = true))
            cursor = range.last + 1
        }
        if (cursor < line.length) {
            tokens.addAll(gapTokens(line, cursor until line.length, breakWords))
        }
        return tokens
    }

    private fun gapTokens(
        line: String,
        indices: IntRange,
        breakWords: (String) -> List<String>
    ): List<OcrToken> {
        val gap = line.substring(indices)
        val tokens = mutableListOf<OcrToken>()
        var offset = indices.first
        for (word in breakWords(gap)) {
            if (word.isBlank()) {
                offset += word.length
                continue
            }
            // 空白没进词块，把词块前的空白补回偏移，保证能定位回原行
            while (offset < line.length && line[offset].isWhitespace()) offset++
            tokens.add(OcrToken(line.substring(offset, offset + word.length), isRegexMatch = false))
            offset += word.length
        }
        return tokens
    }

    /** ICU 词边界切分（中文按内置词典 cjdict，英文/数字串保持完整） */
    private fun icuBreakWords(text: String): List<String> {
        val iterator = BreakIterator.getWordInstance(Locale.CHINESE)
        iterator.setText(text)
        val words = mutableListOf<String>()
        var start = iterator.first()
        var end = iterator.next()
        while (end != BreakIterator.DONE) {
            words.add(text.substring(start, end))
            start = end
            end = iterator.next()
        }
        return words
    }
}
