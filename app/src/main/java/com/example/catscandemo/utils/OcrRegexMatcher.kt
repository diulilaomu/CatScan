package com.example.catscandemo.utils

/**
 * 文字识别（OCR）正则匹配工具
 *
 * 语义：提取型匹配（find），不是整串匹配——"dr110504202781" 配 12 位数字
 * 正则时应录入其中的 "110504202781"；但 14 位数字串里不应截取前 12 位，
 * 因此内置模板全部带前后边界断言（lookaround）。
 */
object OcrRegexMatcher {

    /** 编译正则；空白或非法返回 null（null = 未配置/无效，调用方按"任意文字"处理） */
    fun compileOrNull(regex: String): Regex? {
        val trimmed = regex.trim()
        if (trimmed.isEmpty()) return null
        return try {
            Regex(trimmed)
        } catch (_: Exception) {
            null
        }
    }

    /**
     * 提取一段文字里所有符合正则的片段（去重、保序）。
     * 正则为空或非法 → 整段文字（trim 后非空才返回）。
     */
    fun findAllMatches(text: String, regex: String): List<String> {
        val trimmed = text.trim()
        if (trimmed.isEmpty()) return emptyList()
        val compiled = compileOrNull(regex) ?: return listOf(trimmed)
        return compiled.findAll(trimmed)
            .map { it.value }
            .distinct()
            .toList()
    }
}

/**
 * 正则弹窗内置模板的生成器。
 *
 * 数字/字母数字模板长度支持 精确(n) 或 区间(min,max)，前后断言保证
 * "12位纯数字"只命中恰好 12 位、不贴着其他同类字符的连续段。
 */
object OcrRegexTemplates {

    const val ID_DIGITS = "digits"
    const val ID_ALNUM = "alnum"
    const val ID_PREFIX_DIGITS = "prefixDigits"
    const val ID_PHONE = "phone"
    const val ID_EMAIL = "email"

    /** 纯数字：min==max 为精确长度 */
    fun digits(min: Int, max: Int): String {
        val len = lengthPart(min, max)
        return "(?<!\\d)\\d{$len}(?!\\d)"
    }

    /** 字母+数字（不含其他符号） */
    fun alnum(min: Int, max: Int): String {
        val len = lengthPart(min, max)
        return "(?<![A-Za-z0-9])[A-Za-z0-9]{$len}(?![A-Za-z0-9])"
    }

    /**
     * 特定字符开头 + 数字（如 "DR"+12 位数字）：命中即录入含前缀的整段（DR110504202347）。
     * 前缀以字母/数字开头时加 alnum 前界（"ADR…" 不再误命中 DR 段），数字段后不能紧跟数字。
     */
    fun prefixDigits(prefix: String, min: Int, max: Int): String {
        val safePrefix = prefix.trim()
        require(safePrefix.isNotEmpty()) { "prefix 不能为空" }
        val len = lengthPart(min, max)
        val lookbehind = if (safePrefix.first().isLetterOrDigit()) "(?<![A-Za-z0-9])" else ""
        return "$lookbehind${Regex.escape(safePrefix)}\\d{$len}(?!\\d)"
    }

    /** 大陆手机号：1 开头第二位 3-9，共 11 位 */
    const val PHONE: String = "(?<!\\d)1[3-9]\\d{9}(?!\\d)"

    /** 常规邮箱 */
    const val EMAIL: String = "[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}"

    private fun lengthPart(min: Int, max: Int): String {
        val safeMin = min.coerceIn(1, 128)
        val safeMax = max.coerceIn(safeMin, 128)
        return if (safeMin == safeMax) "$safeMin" else "$safeMin,$safeMax"
    }
}

/**
 * 当前生效识别规则的展示文案（识别区底部标签用）：
 * - 与内置模板参数生成的正则一致 → 中文描述，如"11~12位纯数字"、"手机号"
 * - 用户自定义或手动改过 → 原样显示正则
 * - 空 → "整行录入"
 *
 * 模板参数 JSON 由正则弹窗按固定格式写入，这里用轻量提取而不引 org.json，
 * 保证纯 JVM 单测可跑。
 */
fun ocrRuleLabel(regex: String, templateId: String, templateParamsJson: String): String {
    val trimmed = regex.trim()
    if (trimmed.isEmpty()) return "整行录入"

    fun intParam(key: String): Int? = Regex("\"$key\"\\s*:\\s*\"?(\\d+)\"?")
        .find(templateParamsJson)
        ?.groupValues
        ?.get(1)
        ?.toIntOrNull()

    when (templateId) {
        OcrRegexTemplates.ID_DIGITS -> {
            val min = intParam("digitsMin")
            val max = intParam("digitsMax")
            if (min != null && max != null && OcrRegexTemplates.digits(min, max) == trimmed) {
                return lengthLabel(min, max, "纯数字")
            }
        }
        OcrRegexTemplates.ID_ALNUM -> {
            val min = intParam("alnumMin")
            val max = intParam("alnumMax")
            if (min != null && max != null && OcrRegexTemplates.alnum(min, max) == trimmed) {
                return lengthLabel(min, max, "字母+数字")
            }
        }
        OcrRegexTemplates.ID_PREFIX_DIGITS -> {
            val prefix = Regex("\"prefixText\"\\s*:\\s*\"([^\"]*)\"")
                .find(templateParamsJson)?.groupValues?.get(1).orEmpty()
            val min = intParam("prefixMin")
            val max = intParam("prefixMax")
            if (prefix.isNotEmpty() && min != null && max != null &&
                OcrRegexTemplates.prefixDigits(prefix, min, max) == trimmed
            ) {
                return "${prefix}开头" + lengthLabel(min, max, "数字")
            }
        }
        OcrRegexTemplates.ID_PHONE -> if (trimmed == OcrRegexTemplates.PHONE) return "手机号"
        OcrRegexTemplates.ID_EMAIL -> if (trimmed == OcrRegexTemplates.EMAIL) return "邮箱"
    }
    return trimmed
}

private fun lengthLabel(min: Int, max: Int, what: String): String =
    if (min == max) "${min}位$what" else "${min}~${max}位$what"
