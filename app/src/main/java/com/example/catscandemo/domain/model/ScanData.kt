package com.example.catscandemo.domain.model

import java.util.UUID

enum class TemplateMode {
    LINEAR,
    DISCRETE
}

/**
 * 录入标记模式（房间号/序号可来回切换，切换不影响已有记录）：
 * ROOM = 房间号模式：楼层→房间→标签 游标自动推进
 * SEQ  = 序号模式：自定义前缀+序号+后缀，按正/倒序独立递增（与扫码顺序无关）
 */
enum class MarkMode {
    ROOM,
    SEQ
}

/**
 * 序号模式配置
 */
data class SeqConfig(
    val prefix: String = "",
    val suffix: String = "",
    /** true=中文大写数字（壹贰叁），false=数字 */
    val chineseUpper: Boolean = false,
    val start: Int = 1,
    /** true=倒序录入（每扫一次减一），false=正序（加一） */
    val descending: Boolean = false,
    /** 数字补零位数：0=不补零，>0=补到该位数（如 2 → 01、02…），中文大写不受影响 */
    val padWidth: Int = 2,
    /** 自动补零（padWidth=0 时生效）：位数=模板当前总条数的位数——
     *  10 条数据 01~09 补零、100 条 001~099 补零，以此类推 */
    val autoPad: Boolean = false
)

/** 格式化一个序号标记：前缀 + 数字（数字/中文大写，可补零） + 后缀。
 *  totalForAuto：自动补零模式下用于推断位数（传模板总条数；下一条传 总数+1） */
fun formatSeqValue(seq: SeqConfig, value: Int, totalForAuto: Int = 0): String {
    val num = when {
        seq.chineseUpper -> toChineseUpper(value)
        seq.padWidth > 0 -> {
            val pad = kotlin.math.abs(value).toString().padStart(seq.padWidth, '0')
            if (value < 0) "-$pad" else pad
        }
        seq.autoPad -> {
            val width = maxOf(1, kotlin.math.abs(value), kotlin.math.abs(totalForAuto))
                .toString().length
            val pad = kotlin.math.abs(value).toString().padStart(width, '0')
            if (value < 0) "-$pad" else pad
        }
        else -> value.toString()
    }
    return seq.prefix + num + seq.suffix
}

/** 序号模式下一条要轮询的标签（seqTagCursor<0 或无标签 = 稳定无标签，不再轮询） */
fun seqTagOf(t: TemplateModel): String {
    if (t.tags.isEmpty() || t.seqTagCursor < 0) return ""
    return t.tags[t.seqTagCursor % t.tags.size]
}

/** 标签的调色板色号：优先取模板里显式指定的颜色，否则按标签在列表中的序号循环配色 */
fun tagColorIndexFor(tags: List<String>, tagColors: Map<String, Int>, tag: String): Int {
    tagColors[tag]?.let { return it }
    return tags.indexOf(tag).coerceAtLeast(0)
}

/** 整数转中文大写数字（财务用字），支持 0 与负数（前缀“负”），万/亿分组 */
fun toChineseUpper(n: Int): String {
    if (n == 0) return "零"
    val neg = n < 0
    var v = kotlin.math.abs(n)
    val digits = listOf("零", "壹", "贰", "叁", "肆", "伍", "陆", "柒", "捌", "玖")
    val units = listOf("", "拾", "佰", "仟")
    val groupUnits = listOf("", "万", "亿")
    val pow10 = listOf(1, 10, 100, 1000)

    // 按 4 位一组从低到高拆分，组内从高位到低位拼，再倒序连接
    val parts = mutableListOf<Pair<Int, String>>() // (组原始值, 组中文)
    var gi = 0
    while (v > 0 && gi <= 2) {
        val group = v % 10000
        if (group > 0) {
            val sb = StringBuilder()
            var zeroPending = false
            for (i in 3 downTo 0) {
                val d = (group / pow10[i]) % 10
                if (d == 0) {
                    if (sb.isNotEmpty()) zeroPending = true
                } else {
                    if (zeroPending) {
                        sb.append("零")
                        zeroPending = false
                    }
                    sb.append(digits[d]).append(units[i])
                }
            }
            sb.append(groupUnits[gi])
            parts.add(0, group to sb.toString())
        }
        v /= 10000
        gi++
    }
    val sb = StringBuilder()
    parts.forEachIndexed { index, (groupValue, str) ->
        // 组间补零：低组不足千位（如 0001/0101）且上方还有更高组时，按习惯补“零”
        if (index > 0 && groupValue < 1000) sb.append("零")
        sb.append(str)
    }
    return (if (neg) "负" else "") + sb.toString()
}

/**
 * 工作模式：模板模式（默认）/ 文本模式
 */
enum class WorkMode {
    TEMPLATE,
    TEXT
}

/**
 * 基础扫描数据模型
 * 包含所有扫描数据的通用字段
 */
data class ScanData(
    val id: String = UUID.randomUUID().toString(),
    val text: String,
    val timestamp: Long = System.currentTimeMillis(),
    val operator: String = "unknown",
    val campus: String = "",
    val building: String = "",
    val floor: String = "",
    val room: String = "",
    val tag: String = "",
    val templateId: String = "",
    val templateName: String = "",
    val uploaded: Boolean = false
)

/**
 * 模板模型
 * 包含模板配置和扫描数据
 */
data class TemplateModel(
    val id: String = UUID.randomUUID().toString(),
    val name: String = "未命名模板",
    val operator: String = "",
    val campus: String = "",
    val building: String = "",
    val maxFloor: Int = 1,
    val roomCountPerFloor: Int = 1,
    val mode: TemplateMode = TemplateMode.LINEAR,

    /** 录入标记模式：房间号 / 序号，编辑器可来回切换，切换不改动已有记录 */
    val markMode: MarkMode = MarkMode.ROOM,
    /** 序号模式配置（markMode=SEQ 时生效） */
    val seq: SeqConfig = SeqConfig(),
    /** 序号模式下一次扫码要录入的序号值（独立持久化，与扫码顺序序号无关） */
    val nextSeq: Int = 1,
    /** 序号模式标签轮询游标：-1=无标签（后续一直无标签），>=0=下次用该下标的标签；
     *  标签轮完一圈后序号才推进一位（01甲→01乙→02甲…） */
    val seqTagCursor: Int = 0,

    val lastSelectedFloor: Int = 1,
    val lastSelectedTag: String = "",

    val tags: List<String> = emptyList(),
    /** 标签自定义颜色（调色板色号）；未指定的标签按列表序号循环配色 */
    val tagColors: Map<String, Int> = emptyMap(),

    val selectedRooms: List<String> = emptyList(),
    val scans: List<ScanData> = emptyList()
)

/**
 * 扫描结果模型
 * 用于识别结果列表
 */
data class ScanResult(
    val id: Long,
    val index: Int,
    val scanData: ScanData,
    val uploaded: Boolean = false
)

/**
 * 网络传输模型
 * 用于与PC客户端通信
 */
data class NetworkScanData(
    val qrdata: String,
    val templateName: String,
    val operator: String,
    val campus: String,
    val building: String,
    val floor: String,
    val room: String,
    val tag: String = "",
    val scanTimestamp: Long = 0L,
    val id: String,
    val action: String = "add"
)
