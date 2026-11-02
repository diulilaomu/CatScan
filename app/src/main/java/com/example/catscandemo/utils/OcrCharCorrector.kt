package com.example.catscandemo.utils

/**
 * 识别结果的字符偏好（纠错方向）。
 *
 * ML Kit 内置模型不接受权重/字符集参数，"0 被识别成 O、5 被识别成 S" 这类
 * 形近字符的判定无法在识别层调，只能在识别结果上做确定性映射。
 */
enum class OcrCharMode {
    /** 数字优先：形近字母还原成数字（默认，场景以数字编码为主） */
    DIGITS,

    /** 字母优先：形近数字还原成字母（场景以英文编码为主） */
    LETTERS,

    /** 不纠正：保留模型原文 */
    OFF;

    companion object {
        /** 持久化字符串 → 枚举；非法/缺失一律回落默认（数字优先） */
        fun fromName(name: String?): OcrCharMode =
            runCatching { valueOf(name.orEmpty()) }.getOrDefault(DIGITS)
    }
}

/**
 * OCR 行文本的形近字符纠正。
 *
 * 判定（全部满足才替换，任何一条不满足整段原样保留）：
 * 1. 只在 [A-Za-z0-9] 连续段内处理，中文/符号原样保留；
 * 2. 段内字符必须**全部**是数字或形近字母（数字优先：O/o/I/l/S/s/B/Z），
 *    混进其他字母（DR、CODE、ABC…）即视为字母编码，整段不动；
 * 3. 段内数字要明显占优（数字 ≥ 字母×2），接近对半的不猜方向；
 * 4. 只改**紧邻数字**的形近字母（字母优先模式下为紧邻字母）。
 *
 * 例（数字优先）：O05042O2781 → 00504202781；S12345678901 → 512345678901；
 * DR110504202781、CODE2024、B12AB34 原样不动。
 *
 * [correctForOcr] 另加一道保护：原文已经能命中当前正则时不做纠正，
 * 避免把用户明确指定的字母前缀（如 "SO"+数字）改坏。
 */
object OcrCharCorrector {

    /** 形近字母 → 数字（数字优先） */
    private val LETTER_TO_DIGIT: Map<Char, Char> = mapOf(
        'O' to '0', 'o' to '0',
        'I' to '1', 'l' to '1',
        'S' to '5', 's' to '5',
        'B' to '8',
        'Z' to '2'
    )

    /** 形近数字 → 字母（字母优先） */
    private val DIGIT_TO_LETTER: Map<Char, Char> = mapOf(
        '0' to 'O',
        '1' to 'I',
        '5' to 'S',
        '8' to 'B',
        '2' to 'Z'
    )

    /** 纠正一行识别文字；[mode] 为 OFF 或没有任何符合条件的段时原样返回 */
    fun correct(line: String, mode: OcrCharMode): String {
        if (mode == OcrCharMode.OFF || line.isEmpty()) return line
        val out = StringBuilder(line.length)
        var i = 0
        while (i < line.length) {
            if (!line[i].isAsciiAlnum()) {
                out.append(line[i])
                i++
                continue
            }
            var end = i
            while (end < line.length && line[end].isAsciiAlnum()) end++
            out.append(correctRun(line.substring(i, end), mode))
            i = end
        }
        return out.toString()
    }

    /**
     * 识别链路专用：先看原文是否已命中正则，已命中就原样保留（用户指定的
     * 字母前缀/编码不被改坏）；未命中才尝试纠正。
     */
    fun correctForOcr(line: String, mode: OcrCharMode, regex: String): String {
        if (mode == OcrCharMode.OFF) return line
        val corrected = correct(line, mode)
        if (corrected == line) return line
        if (OcrRegexMatcher.compileOrNull(regex)?.containsMatchIn(line) == true) return line
        return corrected
    }

    /** 纠正一整批行（相册识别结果用），同样带原文优先保护 */
    fun correctAllForOcr(lines: List<String>, mode: OcrCharMode, regex: String): List<String> =
        if (mode == OcrCharMode.OFF) lines else lines.map { correctForOcr(it, mode, regex) }

    private fun correctRun(run: String, mode: OcrCharMode): String {
        val (mapping, neighborIsTarget) = when (mode) {
            OcrCharMode.DIGITS -> LETTER_TO_DIGIT to Char::isDigit
            OcrCharMode.LETTERS -> DIGIT_TO_LETTER to Char::isLetter
            OcrCharMode.OFF -> return run
        }
        var digits = 0
        var letters = 0
        for (c in run) {
            if (c.isDigit()) digits++ else letters++
        }
        // 段内要明显以目标字符为主才猜方向：数字优先需数字 ≥ 字母×2，反之亦然。
        // 混编段（如 CODE2024、B12AB34）不猜，避免把合法编码改成数字串。
        val dominant = when (mode) {
            OcrCharMode.DIGITS -> digits >= letters * 2
            OcrCharMode.LETTERS -> letters >= digits * 2
            OcrCharMode.OFF -> false
        }
        if (!dominant) return run

        // 只改紧邻目标字符的形近字符；其余字母（如 DR 的 D、R）本来就不是形近字符，不参与
        val sb = StringBuilder(run.length)
        for (idx in run.indices) {
            val c = run[idx]
            val mapped = mapping[c]
            if (mapped != null && hasNeighbor(run, idx, neighborIsTarget)) {
                sb.append(mapped)
            } else {
                sb.append(c)
            }
        }
        return sb.toString()
    }

    /** 左右相邻字符中是否有符合 [predicate] 的（段边界视为不匹配） */
    private inline fun hasNeighbor(run: String, idx: Int, predicate: (Char) -> Boolean): Boolean {
        val left = if (idx > 0) run[idx - 1] else null
        val right = if (idx < run.length - 1) run[idx + 1] else null
        return (left != null && predicate(left)) || (right != null && predicate(right))
    }

    private fun Char.isAsciiAlnum(): Boolean =
        this in '0'..'9' || this in 'A'..'Z' || this in 'a'..'z'
}
