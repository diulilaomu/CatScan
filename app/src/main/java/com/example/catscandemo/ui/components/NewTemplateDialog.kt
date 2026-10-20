package com.example.catscandemo.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.catscandemo.domain.model.MarkMode
import com.example.catscandemo.domain.model.SeqConfig
import com.example.catscandemo.domain.model.TemplateModel
import com.example.catscandemo.domain.model.formatSeqValue
import com.example.catscandemo.domain.model.tagColorIndexFor

/**
 * 新建/编辑模板共用的参数状态（全 app 只有一套参数界面）：
 * 新建对话框直接编辑；模板编辑器 loadFrom(template) 后复用同一 UI。
 * 参数区默认展开（与编辑器一致，新建时不播展开动画）。
 * 起始序号不在参数里暴露——改起点点主页表头徽标。
 */
class NewTemplateParamsState {
    var markMode by mutableStateOf(MarkMode.ROOM)
    var advancedOpen by mutableStateOf(true)

    // 序号模式参数
    var seqPrefix by mutableStateOf("")
    var seqSuffix by mutableStateOf("")
    var seqChineseUpper by mutableStateOf(false)
    var seqDescending by mutableStateOf(false)
    var seqPad by mutableStateOf(true)
    /** 补零位数：0=自动（位数=总条数位数，10条→01..09、100条→001..099），1-9=固定 */
    var seqPadWidth by mutableStateOf("0")
    /** 序号起始值：仅 loadFrom 时带入（无 UI），新建默认 1 */
    var seqStartValue: Int = 1

    // 房间号模式参数
    var floorText by mutableStateOf("1")
    var roomCountText by mutableStateOf("1")

    // 随模板一起创建的标签与颜色（编辑器里由 tagsSlot 提供持久化版编辑器）
    var tags by mutableStateOf(listOf<String>())
    var tagColors by mutableStateOf(mapOf<String, Int>())

    fun loadFrom(t: TemplateModel) {
        markMode = t.markMode
        seqPrefix = t.seq.prefix
        seqSuffix = t.seq.suffix
        seqChineseUpper = t.seq.chineseUpper
        seqDescending = t.seq.descending
        seqPad = t.seq.padWidth > 0 || t.seq.autoPad
        seqPadWidth = if (t.seq.padWidth > 0) t.seq.padWidth.toString() else "0"
        seqStartValue = t.seq.start
        floorText = t.maxFloor.toString()
        roomCountText = t.roomCountPerFloor.toString()
        tags = t.tags
        tagColors = t.tagColors
    }

    fun buildSeqConfig(): SeqConfig = SeqConfig(
        prefix = seqPrefix,
        suffix = seqSuffix,
        chineseUpper = seqChineseUpper,
        start = seqStartValue,
        descending = seqDescending,
        padWidth = if (seqPad) (seqPadWidth.toIntOrNull() ?: 0).coerceIn(0, 9) else 0,
        // 位数填 0 = 自动补零（位数跟随总条数）
        autoPad = seqPad && (seqPadWidth.toIntOrNull() ?: 0) <= 0
    )
}

/**
 * 标签编辑对话框：删除（红）/ 取消 / 保存 三按钮。
 * 保存时一次性应用改名+颜色；删除直接移除标签。
 * 模板编辑页与历史记录行共用。
 */
@Composable
fun TagEditDialog(
    target: String,
    initialColor: Int,
    onUpdate: (oldTag: String, newTag: String, colorIdx: Int) -> Unit,
    onRemove: (String) -> Unit,
    onDismiss: () -> Unit
) {
    var name by remember(target) { mutableStateOf(target) }
    var color by remember(target) { mutableStateOf(initialColor) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("编辑标签") },
        text = {
            Column {
                LabeledCompactField(
                    label = "标签名",
                    value = name,
                    onValueChange = { name = it },
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(12.dp))
                Text("颜色", style = MaterialTheme.typography.labelMedium)
                Spacer(Modifier.height(6.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    repeat(TagPresetColorCount) { i ->
                        val selectedColor = (i == color % TagPresetColorCount)
                        Box(
                            modifier = Modifier
                                .size(30.dp)
                                .background(tagContainerColor(i), CircleShape)
                                .border(
                                    width = if (selectedColor) 3.dp else 1.dp,
                                    color = if (selectedColor) {
                                        MaterialTheme.colorScheme.primary
                                    } else {
                                        MaterialTheme.colorScheme.outlineVariant
                                    },
                                    shape = CircleShape
                                )
                                .clickable { color = i }
                        )
                    }
                }
            }
        },
        dismissButton = {
            Row {
                TextButton(onClick = {
                    onRemove(target)
                    onDismiss()
                }) { Text("删除", color = MaterialTheme.colorScheme.error) }
                TextButton(onClick = onDismiss) { Text("取消") }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val n = name.trim()
                if (n.isNotBlank()) onUpdate(target, n, color)
                onDismiss()
            }) { Text("保存") }
        }
    )
}

/**
 * 标签编辑器（新建模板与模板编辑页共用同一套，不另做第二份）：
 * 标题行「标签(n) + 添加」；点标签本体弹改名+选颜色（即时生效），点 × 删除。
 * 新建模式写回 NewTemplateParamsState；编辑模式由回调直接持久化到模板。
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun TemplateTagsEditor(
    tags: List<String>,
    tagColors: Map<String, Int>,
    onAdd: (String) -> Unit,
    onUpdate: (oldTag: String, newTag: String, colorIdx: Int) -> Unit,
    onRemove: (String) -> Unit,
    emptyHint: String = "未设置标签，扫码按 楼层→房间 顺序录入"
) {
    var showTagAdd by remember { mutableStateOf(false) }
    var tagInput by remember { mutableStateOf("") }
    // 标签编辑：null 表示未打开
    var editTarget by remember { mutableStateOf<String?>(null) }
    var editColor by remember { mutableStateOf(0) }

    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("标签(${tags.size})", style = MaterialTheme.typography.labelLarge)
            Spacer(Modifier.weight(1f))
            TextButton(onClick = {
                tagInput = ""
                showTagAdd = true
            }) { Text("添加") }
        }

        if (tags.isEmpty()) {
            Text(
                emptyHint,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        } else {
            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                tags.forEach { tag ->
                    val colorIdx = tagColorIndexFor(tags, tagColors, tag)
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = tagContainerColor(colorIdx)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(start = 8.dp, end = 2.dp)
                        ) {
                            Text(
                                text = tag,
                                style = MaterialTheme.typography.labelMedium,
                                color = tagContentColor(colorIdx),
                                modifier = Modifier
                                    .clickable {
                                        editTarget = tag
                                        editColor = colorIdx
                                    }
                                    .padding(vertical = 6.dp)
                            )
                            // 删除 X 刻意做小（18dp 触达区 + 12dp 图标），避免误触
                            Box(
                                modifier = Modifier
                                    .size(18.dp)
                                    .clickable { onRemove(tag) },
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.Close,
                                    contentDescription = "删除标签 $tag",
                                    tint = tagContentColor(colorIdx),
                                    modifier = Modifier.size(12.dp)
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    // 添加标签
    if (showTagAdd) {
        AlertDialog(
            onDismissRequest = { showTagAdd = false },
            title = { Text("添加标签") },
            text = {
                LabeledCompactField(
                    label = "标签名",
                    value = tagInput,
                    onValueChange = { tagInput = it },
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        val t = tagInput.trim()
                        if (t.isNotBlank() && t !in tags) onAdd(t)
                        showTagAdd = false
                    }
                ) { Text("确定") }
            },
            dismissButton = {
                TextButton(onClick = { showTagAdd = false }) { Text("取消") }
            }
        )
    }

    // 编辑标签：改名+颜色即时生效，删除走红色按钮
    editTarget?.let { target ->
        TagEditDialog(
            target = target,
            initialColor = editColor,
            onUpdate = onUpdate,
            onRemove = onRemove,
            onDismiss = { editTarget = null }
        )
    }
}

/**
 * 新建/编辑模板共用的参数区：
 * 模式 chips（序号在前）+「参数设置」折叠面板。
 * 序号面板：前缀/后缀默认收起；数字/顺序 分组行；补零开关；实时预览。
 * 房间号面板：楼层数/房间数 + 实时预览。
 * 标签区：tagsSlot 为空时编辑本地 state（新建模板）；
 * 编辑器传入 viewModel 持久化版（同一 TemplateTagsEditor，不同数据源）。
 */
@Composable
fun NewTemplateFields(
    state: NewTemplateParamsState,
    tagsSlot: (@Composable () -> Unit)? = null
) {
    var showAffix by remember { mutableStateOf(false) }

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        // 录入标记模式：序号(前缀+序号+后缀) / 房间号(楼层+房间+标签游标)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(
                selected = state.markMode == MarkMode.SEQ,
                onClick = { state.markMode = MarkMode.SEQ },
                label = {
                    Text(
                        text = "序号模板",
                        modifier = Modifier.fillMaxWidth(),
                        textAlign = TextAlign.Center
                    )
                },
                modifier = Modifier.weight(1f)
            )
            FilterChip(
                selected = state.markMode == MarkMode.ROOM,
                onClick = { state.markMode = MarkMode.ROOM },
                label = {
                    Text(
                        text = "房间号模板",
                        modifier = Modifier.fillMaxWidth(),
                        textAlign = TextAlign.Center
                    )
                },
                modifier = Modifier.weight(1f)
            )
        }

        // 参数设置折叠面板
        val arrowRotation by animateFloatAsState(
            targetValue = if (state.advancedOpen) 180f else 0f,
            label = "paramsArrow"
        )
        Surface(
            shape = RoundedCornerShape(10.dp),
            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { state.advancedOpen = !state.advancedOpen }
                        .padding(horizontal = 12.dp, vertical = 6.dp)
                ) {
                    Text(
                        text = "参数设置",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Spacer(Modifier.weight(1f))
                    Icon(
                        imageVector = Icons.Filled.KeyboardArrowDown,
                        contentDescription = if (state.advancedOpen) "收起参数" else "展开参数",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier
                            .size(20.dp)
                            .rotate(arrowRotation)
                    )
                }

                AnimatedVisibility(visible = state.advancedOpen) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            // 底部留白与「参数设置」文字上边视觉等高（含行高留白）
                            .padding(start = 10.dp, end = 10.dp, top = 4.dp, bottom = 10.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        if (state.markMode == MarkMode.SEQ) {
                            // 前缀/后缀：默认收起，触发行与「参数设置」同高同构
                            val affixArrowRotation by animateFloatAsState(
                                targetValue = if (showAffix) 180f else 0f,
                                label = "affixArrow"
                            )
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { showAffix = !showAffix }
                                    .padding(horizontal = 2.dp, vertical = 6.dp)
                            ) {
                                Text(
                                    text = "前缀 / 后缀" +
                                            if (state.seqPrefix.isNotBlank() || state.seqSuffix.isNotBlank()) {
                                                "（已设置）"
                                            } else {
                                                ""
                                            },
                                    style = MaterialTheme.typography.labelLarge,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Spacer(Modifier.weight(1f))
                                Icon(
                                    imageVector = Icons.Filled.KeyboardArrowDown,
                                    contentDescription = if (showAffix) "收起" else "展开",
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier
                                        .size(20.dp)
                                        .rotate(affixArrowRotation)
                                )
                            }
                            if (showAffix) {
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    LabeledCompactField(
                                        label = "前缀",
                                        value = state.seqPrefix,
                                        onValueChange = { state.seqPrefix = it.take(10) },
                                        placeholder = "可空",
                                        centerText = true,
                                        modifier = Modifier.weight(1f)
                                    )
                                    LabeledCompactField(
                                        label = "后缀",
                                        value = state.seqSuffix,
                                        onValueChange = { state.seqSuffix = it.take(10) },
                                        placeholder = "可空",
                                        centerText = true,
                                        modifier = Modifier.weight(1f)
                                    )
                                }
                            }

                            LabeledChipGroup(
                                label = "数字",
                                firstLabel = "数字",
                                firstSelected = !state.seqChineseUpper,
                                onFirst = { state.seqChineseUpper = false },
                                secondLabel = "中文",
                                secondSelected = state.seqChineseUpper,
                                onSecond = { state.seqChineseUpper = true }
                            )
                            LabeledChipGroup(
                                label = "顺序",
                                firstLabel = "正序",
                                firstSelected = !state.seqDescending,
                                onFirst = { state.seqDescending = false },
                                secondLabel = "倒序",
                                secondSelected = state.seqDescending,
                                onSecond = { state.seqDescending = true }
                            )

                            // 补零开关 + 位数（数字样式才有意义）：
                            // 开关左缘与「正序」chip 左缘对齐（同为 32dp 标签 + 8dp 间距）
                            if (!state.seqChineseUpper) {
                                Row(
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = "补零",
                                        style = MaterialTheme.typography.labelMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.width(32.dp)
                                    )
                                    Switch(
                                        checked = state.seqPad,
                                        onCheckedChange = { state.seqPad = it }
                                    )
                                    if (state.seqPad) {
                                        LabeledCompactField(
                                            label = "位数",
                                            value = state.seqPadWidth,
                                            onValueChange = {
                                                state.seqPadWidth = it.filter(Char::isDigit).take(1)
                                            },
                                            keyboardType = KeyboardType.Number,
                                            centerText = true,
                                            fieldModifier = Modifier.width(56.dp)
                                        )
                                        // 0=自动：位数跟随总条数（10条→01..09、100条→001..099）
                                        if ((state.seqPadWidth.toIntOrNull() ?: 0) == 0) {
                                            Text(
                                                text = "自动",
                                                style = MaterialTheme.typography.labelSmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                    }
                                    Spacer(Modifier.weight(1f))
                                }
                            }

                            // 实时预览（自动补零按 10 条预估位数）
                            val previewCfg = state.buildSeqConfig()
                            val base = state.seqStartValue
                            Text(
                                text = "预览　" + (0..2).joinToString(" → ") { step ->
                                    val v = base + if (previewCfg.descending) -step else step
                                    formatSeqValue(previewCfg, v, totalForAuto = 10)
                                },
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.primary
                            )
                        } else {
                            // 楼层数量 / 每层房间数：各占一整行，不挤在一起
                            LabeledCompactField(
                                label = "楼层数量",
                                value = state.floorText,
                                onValueChange = { state.floorText = it.filter(Char::isDigit).take(3) },
                                keyboardType = KeyboardType.Number,
                                modifier = Modifier.fillMaxWidth()
                            )
                            LabeledCompactField(
                                label = "每层房间数",
                                value = state.roomCountText,
                                onValueChange = {
                                    state.roomCountText = it.filter(Char::isDigit).take(3)
                                },
                                keyboardType = KeyboardType.Number,
                                modifier = Modifier.fillMaxWidth()
                            )

                            val f = (state.floorText.toIntOrNull() ?: 1).coerceAtLeast(1)
                            val r = (state.roomCountText.toIntOrNull() ?: 1).coerceIn(1, 999)
                            Text(
                                text = "预览　1层101 ~ ${f}层${f}${if (r in 1..9) "0$r" else "$r"} · 共 ${f * r} 间",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }

                        // 标签（两种模式共用：房间号=楼层→房间→标签轮询；序号=标签→序号轮询）
                        if (tagsSlot != null) {
                            tagsSlot()
                        } else {
                            TemplateTagsEditor(
                                tags = state.tags,
                                tagColors = state.tagColors,
                                onAdd = { state.tags = state.tags + it },
                                onUpdate = { old, new, colorIdx ->
                                    state.tags = state.tags.map { if (it == old) new else it }
                                    val colors = state.tagColors.toMutableMap()
                                    colors.remove(old)
                                    if (new.isNotBlank()) colors[new] = colorIdx
                                    state.tagColors = colors
                                },
                                onRemove = { t ->
                                    state.tags = state.tags.filterNot { it == t }
                                    val colors = state.tagColors.toMutableMap()
                                    colors.remove(t)
                                    state.tagColors = colors
                                }
                            )
                        }
                    }
                }
            }
        }
    }
}

/** 分组选项行：左侧短标签 + 右侧两枚平分宽度的 chips */
@Composable
private fun LabeledChipGroup(
    label: String,
    firstLabel: String,
    firstSelected: Boolean,
    onFirst: () -> Unit,
    secondLabel: String,
    secondSelected: Boolean,
    onSecond: () -> Unit
) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.width(32.dp)
        )
        FilterChip(
            selected = firstSelected,
            onClick = onFirst,
            label = {
                Text(
                    text = firstLabel,
                    modifier = Modifier.fillMaxWidth(),
                    textAlign = TextAlign.Center
                )
            },
            modifier = Modifier.weight(1f)
        )
        FilterChip(
            selected = secondSelected,
            onClick = onSecond,
            label = {
                Text(
                    text = secondLabel,
                    modifier = Modifier.fillMaxWidth(),
                    textAlign = TextAlign.Center
                )
            },
            modifier = Modifier.weight(1f)
        )
    }
}
