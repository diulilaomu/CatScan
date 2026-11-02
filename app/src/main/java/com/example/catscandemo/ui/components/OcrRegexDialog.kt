package com.example.catscandemo.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.catscandemo.utils.OcrCharMode
import com.example.catscandemo.utils.OcrRegexMatcher
import com.example.catscandemo.utils.OcrRegexTemplates
import org.json.JSONObject

/**
 * 文字识别正则编辑弹窗
 *
 * - 顶部输入框直接编辑正则；空 = 不限（横线附近整行直接录入）
 * - 内置模板可展开配置参数；**参数一改即自动同步到上方输入框**（无"填入"按钮），
 *   手机号/邮箱无可调参数，展开即同步
 * - 参数与所选模板持久化，重开时回填
 * - 确定：应用并持久化；取消：关闭不生效
 */
@Composable
fun OcrRegexDialog(
    initialRegex: String,
    initialTemplateId: String,
    initialTemplateParams: String,
    initialCharMode: OcrCharMode,
    onConfirm: (regex: String, templateId: String, templateParams: String, charMode: OcrCharMode) -> Unit,
    onDismiss: () -> Unit
) {
    val initialParams = remember(initialTemplateParams) {
        runCatching { JSONObject(initialTemplateParams.ifBlank { "{}" }) }.getOrNull() ?: JSONObject()
    }

    var regexText by remember { mutableStateOf(initialRegex) }
    // 当前展开的模板（重开时展开上次使用的）
    var expandedId by remember { mutableStateOf<String?>(initialTemplateId.takeIf { it.isNotBlank() }) }
    // 识别字符偏好：默认折叠，点标题行展开
    var charModeExpanded by remember { mutableStateOf(false) }
    var charMode by remember { mutableStateOf(initialCharMode) }

    var digitsMin by remember { mutableStateOf(initialParams.optString("digitsMin", "8")) }
    var digitsMax by remember { mutableStateOf(initialParams.optString("digitsMax", "15")) }
    var alnumMin by remember { mutableStateOf(initialParams.optString("alnumMin", "10")) }
    var alnumMax by remember { mutableStateOf(initialParams.optString("alnumMax", "12")) }
    var prefixText by remember { mutableStateOf(initialParams.optString("prefixText", "DR")) }
    var prefixMin by remember { mutableStateOf(initialParams.optString("prefixMin", "12")) }
    var prefixMax by remember { mutableStateOf(initialParams.optString("prefixMax", "12")) }

    fun digitsRegex(): String = OcrRegexTemplates.digits(
        digitsMin.toIntOrNull() ?: 1,
        digitsMax.toIntOrNull() ?: (digitsMin.toIntOrNull() ?: 1)
    )

    fun alnumRegex(): String = OcrRegexTemplates.alnum(
        alnumMin.toIntOrNull() ?: 1,
        alnumMax.toIntOrNull() ?: (alnumMin.toIntOrNull() ?: 1)
    )

    /** 前缀为空时返回 null（不同步、预览给提示） */
    fun prefixRegex(): String? {
        val prefix = prefixText.trim()
        if (prefix.isEmpty()) return null
        return OcrRegexTemplates.prefixDigits(
            prefix,
            prefixMin.toIntOrNull() ?: 1,
            prefixMax.toIntOrNull() ?: (prefixMin.toIntOrNull() ?: 1)
        )
    }

    /** 展开即把该模板按当前参数生成的正则同步到输入框（前缀为空时跳过，预览会给提示） */
    fun syncTemplateRegex(id: String) {
        when (id) {
            OcrRegexTemplates.ID_DIGITS -> regexText = digitsRegex()
            OcrRegexTemplates.ID_ALNUM -> regexText = alnumRegex()
            OcrRegexTemplates.ID_PREFIX_DIGITS -> prefixRegex()?.let { regexText = it }
            OcrRegexTemplates.ID_PHONE -> regexText = OcrRegexTemplates.PHONE
            OcrRegexTemplates.ID_EMAIL -> regexText = OcrRegexTemplates.EMAIL
        }
    }

    fun toggleTemplate(id: String) {
        if (expandedId == id) {
            expandedId = null
        } else {
            expandedId = id
            syncTemplateRegex(id)
        }
    }

    val trimmedRegex = regexText.trim()
    val regexInvalid = trimmedRegex.isNotEmpty() && OcrRegexMatcher.compileOrNull(trimmedRegex) == null

    fun confirm() {
        val paramsJson = JSONObject().apply {
            put("digitsMin", digitsMin)
            put("digitsMax", digitsMax)
            put("alnumMin", alnumMin)
            put("alnumMax", alnumMax)
            put("prefixText", prefixText.trim())
            put("prefixMin", prefixMin)
            put("prefixMax", prefixMax)
        }.toString()
        onConfirm(trimmedRegex, expandedId.orEmpty(), paramsJson, charMode)
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("识别规则（正则）") },
        text = {
            Column(
                modifier = Modifier
                    .heightIn(max = 460.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                // 识别字符偏好（默认折叠）：ML Kit 模型无权重参数，这里改的是识别结果的形近字符映射
                OcrCharModeSection(
                    expanded = charModeExpanded,
                    onToggle = { charModeExpanded = !charModeExpanded },
                    mode = charMode,
                    onModeChange = { charMode = it }
                )

                Spacer(Modifier.height(8.dp))
                CompactTextField(
                    value = regexText,
                    onValueChange = { regexText = it },
                    modifier = Modifier.fillMaxWidth(),
                    textStyle = MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Monospace),
                    placeholder = "留空 = 整行文字直接录入"
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    text = when {
                        regexInvalid -> "正则表达式无效，请检查"
                        trimmedRegex.isEmpty() -> "未配置正则：横线附近的整行文字都会录入"
                        else -> "已启用：录入文字中符合该正则的片段"
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = if (regexInvalid) {
                        MaterialTheme.colorScheme.error
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    }
                )

                Spacer(Modifier.height(12.dp))
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                Spacer(Modifier.height(8.dp))
                Text(
                    text = "常用模板（展开即同步到输入框，改参数即时生效）",
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(4.dp))

                RegexTemplateItem(
                    title = "匹配数字（可限定长度）",
                    expanded = expandedId == OcrRegexTemplates.ID_DIGITS,
                    onToggle = { toggleTemplate(OcrRegexTemplates.ID_DIGITS) }
                ) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        LabeledCompactField(
                            label = "最短",
                            value = digitsMin,
                            onValueChange = {
                                digitsMin = it.filter(Char::isDigit).take(3)
                                regexText = digitsRegex()
                            },
                            keyboardType = KeyboardType.Number,
                            centerText = true,
                            fieldModifier = Modifier.weight(1f),
                            modifier = Modifier.weight(1f)
                        )
                        LabeledCompactField(
                            label = "最长",
                            value = digitsMax,
                            onValueChange = {
                                digitsMax = it.filter(Char::isDigit).take(3)
                                regexText = digitsRegex()
                            },
                            keyboardType = KeyboardType.Number,
                            centerText = true,
                            fieldModifier = Modifier.weight(1f),
                            modifier = Modifier.weight(1f)
                        )
                    }
                    RegexTemplatePreview(preview = digitsRegex())
                }

                RegexTemplateItem(
                    title = "匹配字母+数字（可限定长度）",
                    expanded = expandedId == OcrRegexTemplates.ID_ALNUM,
                    onToggle = { toggleTemplate(OcrRegexTemplates.ID_ALNUM) }
                ) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        LabeledCompactField(
                            label = "最短",
                            value = alnumMin,
                            onValueChange = {
                                alnumMin = it.filter(Char::isDigit).take(3)
                                regexText = alnumRegex()
                            },
                            keyboardType = KeyboardType.Number,
                            centerText = true,
                            fieldModifier = Modifier.weight(1f),
                            modifier = Modifier.weight(1f)
                        )
                        LabeledCompactField(
                            label = "最长",
                            value = alnumMax,
                            onValueChange = {
                                alnumMax = it.filter(Char::isDigit).take(3)
                                regexText = alnumRegex()
                            },
                            keyboardType = KeyboardType.Number,
                            centerText = true,
                            fieldModifier = Modifier.weight(1f),
                            modifier = Modifier.weight(1f)
                        )
                    }
                    RegexTemplatePreview(preview = alnumRegex())
                }

                RegexTemplateItem(
                    title = "匹配特定字符开头的数字",
                    expanded = expandedId == OcrRegexTemplates.ID_PREFIX_DIGITS,
                    onToggle = { toggleTemplate(OcrRegexTemplates.ID_PREFIX_DIGITS) }
                ) {
                    // 开头独占一行，避免三个输入框挤在一行
                    LabeledCompactField(
                        label = "开头",
                        value = prefixText,
                        onValueChange = {
                            // 前缀按字面匹配，去掉空白与引号（引号会破坏参数 JSON 的回填解析）
                            prefixText = it.filterNot { c -> c.isWhitespace() || c == '"' }.take(8)
                            prefixRegex()?.let { generated -> regexText = generated }
                        },
                        placeholder = "如 DR",
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(Modifier.height(8.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        LabeledCompactField(
                            label = "最短",
                            value = prefixMin,
                            onValueChange = {
                                prefixMin = it.filter(Char::isDigit).take(3)
                                prefixRegex()?.let { generated -> regexText = generated }
                            },
                            keyboardType = KeyboardType.Number,
                            centerText = true,
                            fieldModifier = Modifier.weight(1f),
                            modifier = Modifier.weight(1f)
                        )
                        LabeledCompactField(
                            label = "最长",
                            value = prefixMax,
                            onValueChange = {
                                prefixMax = it.filter(Char::isDigit).take(3)
                                prefixRegex()?.let { generated -> regexText = generated }
                            },
                            keyboardType = KeyboardType.Number,
                            centerText = true,
                            fieldModifier = Modifier.weight(1f),
                            modifier = Modifier.weight(1f)
                        )
                    }
                    RegexTemplatePreview(
                        preview = prefixRegex() ?: "请先输入开头字符（如 DR）",
                        hint = prefixRegex() == null
                    )
                    if (prefixRegex() != null) {
                        Text(
                            text = "命中即录入含开头的整段，如 ${prefixText.trim()}110504202347",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(top = 2.dp)
                        )
                    }
                }

                RegexTemplateItem(
                    title = "匹配手机号",
                    expanded = expandedId == OcrRegexTemplates.ID_PHONE,
                    onToggle = {
                        toggleTemplate(OcrRegexTemplates.ID_PHONE)
                    }
                ) {
                    RegexTemplatePreview(preview = OcrRegexTemplates.PHONE)
                }

                RegexTemplateItem(
                    title = "匹配邮箱",
                    expanded = expandedId == OcrRegexTemplates.ID_EMAIL,
                    onToggle = {
                        toggleTemplate(OcrRegexTemplates.ID_EMAIL)
                    }
                ) {
                    RegexTemplatePreview(preview = OcrRegexTemplates.EMAIL)
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = !regexInvalid,
                onClick = { confirm() }
            ) { Text("确定") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("取消") }
        }
    )
}

/**
 * 识别字符偏好折叠区：标题行右侧显示当前模式，展开后为三档 FilterChip。
 *
 * 复用 [RegexTemplateItem] 的展开动画（AnimatedVisibility 一次性布局 + 逐帧裁剪），
 * 折叠态零成本，展开态不逐帧重测输入区。
 */
@Composable
private fun OcrCharModeSection(
    expanded: Boolean,
    onToggle: () -> Unit,
    mode: OcrCharMode,
    onModeChange: (OcrCharMode) -> Unit
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onToggle)
                .padding(vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "识别字符偏好",
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.weight(1f)
            )
            Text(
                text = mode.label(),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            val arrowRotation by animateFloatAsState(
                targetValue = if (expanded) 180f else 0f,
                animationSpec = tween(durationMillis = 200, easing = FastOutSlowInEasing),
                label = "charModeArrowRotation"
            )
            Icon(
                imageVector = Icons.Filled.ArrowDropDown,
                contentDescription = if (expanded) "收起" else "展开",
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.graphicsLayer { rotationZ = arrowRotation }
            )
        }
        AnimatedVisibility(
            visible = expanded,
            enter = expandVertically(
                animationSpec = tween(durationMillis = 200, easing = FastOutSlowInEasing)
            ) + fadeIn(
                animationSpec = tween(durationMillis = 200, easing = FastOutSlowInEasing)
            ),
            exit = shrinkVertically(
                animationSpec = tween(durationMillis = 200, easing = FastOutSlowInEasing)
            ) + fadeOut(animationSpec = tween(durationMillis = 150))
        ) {
            Column(modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OcrCharMode.entries.forEach { candidate ->
                        FilterChip(
                            selected = mode == candidate,
                            onClick = { onModeChange(candidate) },
                            modifier = Modifier.weight(1f),
                            label = {
                                Text(
                                    text = candidate.label(),
                                    modifier = Modifier.fillMaxWidth(),
                                    textAlign = TextAlign.Center,
                                    maxLines = 1
                                )
                            }
                        )
                    }
                }
                Spacer(Modifier.height(6.dp))
                Text(
                    text = mode.hint(),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
    }
}

/** 模式短标签（chip 与折叠标题右侧共用） */
private fun OcrCharMode.label(): String = when (this) {
    OcrCharMode.DIGITS -> "数字优先"
    OcrCharMode.LETTERS -> "字母优先"
    OcrCharMode.OFF -> "不纠正"
}

/** 模式说明：讲清"纠正的是什么"以及"什么情况下不纠正" */
private fun OcrCharMode.hint(): String = when (this) {
    OcrCharMode.DIGITS ->
        "数字为主的串里，紧邻数字的 O/o→0、S/s→5、I/l→1、B→8、Z→2 会被还原成数字；" +
            "字母为主的串（如 CODE2024、DR 前缀）保持原样"
    OcrCharMode.LETTERS ->
        "字母为主的串里，紧邻字母的 0→O、1→I、5→S、8→B、2→Z 会被还原成字母；" +
            "数字为主的串保持原样"
    OcrCharMode.OFF -> "完全保留模型原文，不做任何形近字符替换"
}

/**
 * 模板行：标题 + 展开箭头，点击切换；展开区放参数与预览。
 *
 * 展开动画用官方 AnimatedVisibility 的 expandVertically/shrinkVertically：
 * 内容一次性按最终尺寸完成测量布局，逐帧只动裁剪（placeholder 机制），
 * 展开区内的输入框不参与逐帧重测——animateContentSize 会带子内容逐帧重测，勿换回。
 */
@Composable
private fun RegexTemplateItem(
    title: String,
    expanded: Boolean,
    onToggle: () -> Unit,
    content: @Composable ColumnScope.() -> Unit
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onToggle)
                .padding(vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.weight(1f)
            )
            val arrowRotation by animateFloatAsState(
                targetValue = if (expanded) 180f else 0f,
                animationSpec = tween(durationMillis = 200, easing = FastOutSlowInEasing),
                label = "templateArrowRotation"
            )
            Icon(
                imageVector = Icons.Filled.ArrowDropDown,
                contentDescription = if (expanded) "收起" else "展开",
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.graphicsLayer { rotationZ = arrowRotation }
            )
        }
        AnimatedVisibility(
            visible = expanded,
            enter = expandVertically(
                animationSpec = tween(durationMillis = 200, easing = FastOutSlowInEasing)
            ) + fadeIn(
                animationSpec = tween(durationMillis = 200, easing = FastOutSlowInEasing)
            ),
            exit = shrinkVertically(
                animationSpec = tween(durationMillis = 200, easing = FastOutSlowInEasing)
            ) + fadeOut(animationSpec = tween(durationMillis = 150))
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 8.dp),
                content = content
            )
        }
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
    }
}

/** 模板展开区底部的生成预览（参数改动实时刷新） */
@Composable
private fun RegexTemplatePreview(preview: String, hint: Boolean = false) {
    Spacer(Modifier.height(6.dp))
    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = if (hint) 0.35f else 0.6f),
        shape = MaterialTheme.shapes.small
    ) {
        Text(
            text = preview,
            style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
            color = if (hint) {
                MaterialTheme.colorScheme.error
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant
            },
            maxLines = 2,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 6.dp)
        )
    }
}
