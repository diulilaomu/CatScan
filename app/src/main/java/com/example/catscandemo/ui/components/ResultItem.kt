package com.example.catscandemo.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.catscandemo.domain.model.MarkMode
import com.example.catscandemo.domain.model.ScanResult
import com.example.catscandemo.domain.model.TemplateModel
import com.example.catscandemo.domain.model.tagColorIndexFor
import kotlinx.coroutines.delay


/**
 * ResultItemController: 管理单条扫描结果的数据与状态
 *
 * 行交互：点击 V 标/文本/房号徽标 → 展开/收起下方编辑抽屉；
 * 标签徽标/+标签 → 直接弹标签选择列表（不展开抽屉）；
 * 抽屉内修改即时生效（防抖落盘，收起时立即落盘），无需保存按钮；
 * 复制、删除走行尾按钮（删除带二次确认）。
 */
class ResultItemController(
    initialItem: ScanResult,
    private val onDelete: () -> Unit,
    private val onClickCopy: () -> Unit,
    private val onUpdate: (ScanResult) -> Unit,
    // 模板标签管理透传（主页接 ViewModel：新建/改名换色/删除 会持久化到模板）
    private val onAddTag: (String) -> Unit = {},
    private val onRenameTag: (String, String, Int) -> Unit = { _, _, _ -> },
    private val onRemoveTag: (String) -> Unit = {}
) {

    var item by mutableStateOf(initialItem)
        private set

    var expanded by mutableStateOf(false)

    // 可编辑字段状态
    var room by mutableStateOf(initialItem.scanData.room)
    var floor by mutableStateOf(initialItem.scanData.floor)
    var content by mutableStateOf(initialItem.scanData.text)
    var tag by mutableStateOf(initialItem.scanData.tag)

    /**
     * 更新本地字段状态
     * 展开编辑中不覆盖输入字段，避免后台上传回调清掉用户正在编辑的内容
     */
    fun syncItem(newItem: ScanResult) {
        item = newItem
        if (!expanded) {
            room = newItem.scanData.room
            floor = newItem.scanData.floor
            content = newItem.scanData.text
            tag = newItem.scanData.tag
        }
    }

    /** 是否有尚未落盘的修改 */
    fun hasPendingEdits(): Boolean {
        val d = item.scanData
        return content != d.text || room != d.room || floor != d.floor || tag != d.tag
    }

    /** 修改即时生效：回写并持久化（保持抽屉展开） */
    fun persist() {
        if (!hasPendingEdits()) return
        val updatedScanData = item.scanData.copy(
            text = content,
            room = room,
            floor = floor,
            tag = tag
        )
        val updatedItem = item.copy(scanData = updatedScanData)
        item = updatedItem
        onUpdate(updatedItem)
    }

    /** 展开/收起抽屉；收起前先把未落盘的修改立即生效 */
    fun toggleExpanded() {
        if (expanded) persist()
        expanded = !expanded
    }

    /**
     * Composable 渲染函数（紧凑单行：行序号 + V标 + 内容 + 标签/房间徽标 + 复制/删除）。
     * showCopy=false 隐藏复制按钮（游标修改弹窗的已录面板里不需要复制）。
     */
    @OptIn(ExperimentalMaterial3Api::class)
    @Composable
    fun Render(
        highlight: Boolean = false,
        template: TemplateModel? = null,
        lineNumber: Int? = null,
        showCopy: Boolean = true
    ) {
        // 重复命中：日间黄底深字，夜间换暗琥珀底浅字，两套主题下都可读
        val darkTheme = MaterialTheme.colorScheme.surface.luminance() < 0.5f
        val bgColor = when {
            highlight && darkTheme -> Color(0xFF5C4600)
            highlight -> Color(0xFFFFF59D)
            else -> MaterialTheme.colorScheme.surface
        }
        val contentColor = when {
            highlight && darkTheme -> Color(0xFFFFE082)
            highlight -> Color(0xFF4A4100)
            else -> MaterialTheme.colorScheme.onSurface
        }
        val deleteColor = Color(0xFFEF5350)
        val copyColor = Color(0xFF42A5F5)

        var showDeleteConfirm by remember { mutableStateOf(false) }
        var showRoomPicker by remember { mutableStateOf(false) }

        // 标签管理（行内徽标与抽屉按钮共用）：编辑目标 (名字 to 色号) / 新建标签
        var rowTagMenuOpen by remember { mutableStateOf(false) }
        var tagEditTarget by remember { mutableStateOf<Pair<String, Int>?>(null) }
        var showTagNew by remember { mutableStateOf(false) }
        var newTagInput by remember { mutableStateOf("") }

        // 抽屉内任何修改即时生效：停笔 300ms 后自动落盘（连续输入只落最后一次）
        LaunchedEffect(content, room, floor, tag) {
            if (hasPendingEdits()) {
                delay(300)
                persist()
            }
        }

        val templateTags = template?.tags.orEmpty()
        val tagColors = template?.tagColors.orEmpty()

        // V 标旋转动画：未展开指向文本（右向），展开后指向下方（旋转 90°）
        val chevronRotation by animateFloatAsState(
            targetValue = if (expanded) 90f else 0f,
            label = "chevronRotation"
        )

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(bgColor)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(32.dp)
                    .padding(horizontal = 4.dp)
            ) {
                // 第0列：行序号（第几个进入模板历史）
                if (lineNumber != null) {
                    Text(
                        text = "$lineNumber",
                        style = MaterialTheme.typography.labelSmall,
                        color = contentColor.copy(alpha = 0.62f),
                        textAlign = TextAlign.Center,
                        maxLines = 1,
                        modifier = Modifier.width(28.dp)
                    )
                }

                // V 标（文本左侧，未展开指向文本、展开后指向下方，颜色与复制按钮一致）
                Box(
                    modifier = Modifier
                        .size(24.dp)
                        .clickable { toggleExpanded() },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Filled.KeyboardArrowRight,
                        contentDescription = if (expanded) "收起" else "展开",
                        tint = copyColor,
                        modifier = Modifier
                            .size(18.dp)
                            .rotate(chevronRotation)
                    )
                }

                // 第一列：扫描数据（点击展开/收起编辑抽屉）
                Text(
                    text = item.scanData.text,
                    modifier = Modifier
                        .weight(1f)
                        .clickable { toggleExpanded() },
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.bodyMedium,
                    color = contentColor
                )

                // 第二列：房号、标签徽标（房号点击展开抽屉；标签点击直接弹选择列表）
                Row(
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(horizontal = 4.dp)
                ) {
                    if (item.scanData.room.isNotBlank()) {
                        Text(
                            text = item.scanData.room,
                            style = MaterialTheme.typography.labelSmall,
                            fontSize = 9.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            modifier = Modifier
                                .background(MaterialTheme.colorScheme.surfaceVariant)
                                .clickable { toggleExpanded() }
                                .padding(horizontal = 5.dp, vertical = 1.dp)
                        )
                    }

                    if (templateTags.isNotEmpty() || item.scanData.tag.isNotBlank()) {
                        val tagIndex = tagColorIndexFor(templateTags, tagColors, item.scanData.tag)
                        Box {
                            // 标签宽度封顶（文字区约40dp(6数字+省略号)，含内边距共50dp），超出省略号
                            Text(
                                text = item.scanData.tag.ifBlank { "+标签" },
                                style = MaterialTheme.typography.labelSmall,
                                fontSize = 9.sp,
                                color = when {
                                    item.scanData.tag.isNotBlank() -> tagContentColor(tagIndex)
                                    else -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.55f)
                                },
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier
                                    .background(
                                        when {
                                            item.scanData.tag.isNotBlank() -> tagContainerColor(tagIndex)
                                            else -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                                        }
                                    )
                                    .clickable { rowTagMenuOpen = true }
                                    .widthIn(max = 50.dp)
                                    .padding(horizontal = 5.dp, vertical = 1.dp)
                            )
                            DropdownMenu(
                                expanded = rowTagMenuOpen,
                                onDismissRequest = { rowTagMenuOpen = false }
                            ) {
                                TagMenuContent(
                                    templateTags = templateTags,
                                    tagColors = tagColors,
                                    currentTag = tag,
                                    onPick = {
                                        tag = it
                                        rowTagMenuOpen = false
                                    },
                                    onEditTag = { t, c ->
                                        rowTagMenuOpen = false
                                        tagEditTarget = t to c
                                    },
                                    onAddTag = {
                                        rowTagMenuOpen = false
                                        newTagInput = ""
                                        showTagNew = true
                                    }
                                )
                            }
                        }
                    }
                }

                // 第三列：复制（可隐藏） + 删除（删除带二次确认）
                if (showCopy) {
                    SmallItemIconButton(
                        icon = Icons.Filled.ContentCopy,
                        contentDescription = "复制",
                        tint = copyColor,
                        onClick = onClickCopy
                    )
                }

                SmallItemIconButton(
                    icon = Icons.Filled.Delete,
                    contentDescription = "删除",
                    tint = deleteColor,
                    onClick = { showDeleteConfirm = true }
                )
            }

            if (expanded) {
                ResultItemExpandEditor(
                    template = template,
                    onOpenRoomPicker = { showRoomPicker = true },
                    onAddTagRequest = {
                        newTagInput = ""
                        showTagNew = true
                    },
                    onEditTagRequest = { t, c -> tagEditTarget = t to c }
                )
            }
        }

        // 删除二次确认
        if (showDeleteConfirm) {
            AlertDialog(
                onDismissRequest = { showDeleteConfirm = false },
                title = { Text("删除这条记录") },
                text = {
                    Text(
                        text = item.scanData.text,
                        maxLines = 3,
                        overflow = TextOverflow.Ellipsis
                    )
                },
                confirmButton = {
                    TextButton(
                        onClick = {
                            showDeleteConfirm = false
                            onDelete()
                        }
                    ) {
                        Text("删除", color = MaterialTheme.colorScheme.error)
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showDeleteConfirm = false }) { Text("取消") }
                }
            )
        }

        // 标签编辑（改名+颜色，即时生效；红色删除）——与模板编辑器同一对话框
        tagEditTarget?.let { (t, c) ->
            TagEditDialog(
                target = t,
                initialColor = c,
                onUpdate = { old, new, colorIdx ->
                    onRenameTag(old, new, colorIdx)
                    // 标签改名后本条记录的标签值跟着走
                    if (tag == old) tag = new
                },
                onRemove = { removed ->
                    onRemoveTag(removed)
                    if (tag == removed) tag = ""
                },
                onDismiss = { tagEditTarget = null }
            )
        }

        // 新建标签：加入模板并直接给本条记录使用
        if (showTagNew) {
            AlertDialog(
                onDismissRequest = { showTagNew = false },
                title = { Text("新建标签") },
                text = {
                    LabeledCompactField(
                        label = "标签名",
                        value = newTagInput,
                        onValueChange = { newTagInput = it },
                        modifier = Modifier.fillMaxWidth()
                    )
                },
                confirmButton = {
                    TextButton(
                        onClick = {
                            val n = newTagInput.trim()
                            if (n.isNotBlank()) {
                                onAddTag(n)
                                tag = n
                            }
                            showTagNew = false
                        }
                    ) { Text("确定") }
                },
                dismissButton = {
                    TextButton(onClick = { showTagNew = false }) { Text("取消") }
                }
            )
        }

        // 房间号修改：弹房间选择器（楼层+房间网格+底部自由编辑），不再直接 input
        if (showRoomPicker && template != null) {
            DiscreteRoomSelectionDialog(
                template = template,
                initialFloor = Regex("\\d+").find(floor)?.value?.toIntOrNull() ?: 1,
                initialTag = tag,
                showTagSelector = false,
                allowCustomRoom = true,
                onConfirm = { pickedFloor, pickedRoom, _ ->
                    floor = "${pickedFloor}层"
                    room = pickedRoom
                    showRoomPicker = false
                },
                onDismiss = { showRoomPicker = false }
            )
        }
    }

    /**
     * 展开编辑抽屉（紧凑）：内容 + 房间/序号 + 标签按钮。
     * 输入框用 surface 底色与抽屉的 surfaceVariant 底色区分；
     * 房间按钮自适应宽度，标签按钮紧跟其后不被推到最右。
     */
    @Composable
    private fun ResultItemExpandEditor(
        template: TemplateModel?,
        onOpenRoomPicker: () -> Unit,
        onAddTagRequest: () -> Unit,
        onEditTagRequest: (String, Int) -> Unit
    ) {
        val templateTags = template?.tags.orEmpty()
        val tagColors = template?.tagColors.orEmpty()

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                MaterialTheme.colorScheme.onSurface.copy(alpha = 0.04f)
                    .compositeOver(MaterialTheme.colorScheme.surface),
                RoundedCornerShape(8.dp)
            )
                .padding(horizontal = 8.dp, vertical = 6.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            // 内容
            LabeledCompactField(
                label = "内容",
                value = content,
                onValueChange = { content = it },
                modifier = Modifier.fillMaxWidth(),
                singleLine = false,
            )

            if (template != null) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    if (template.markMode == MarkMode.SEQ) {
                        // 序号模板：输入框占满整行，把标签按钮顶到最右
                        LabeledCompactField(
                            label = "序号",
                            value = room,
                            onValueChange = { room = it },
                            modifier = Modifier.weight(1f),
                        )
                    } else {
                        // 房间号模板：自适应宽度，标签按钮紧跟其后不被推到最右
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = MaterialTheme.colorScheme.surface,
                            border = androidx.compose.foundation.BorderStroke(
                                width = 1.dp,
                                color = MaterialTheme.colorScheme.outline
                            ),
                            onClick = onOpenRoomPicker
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp),
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 9.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.LocationOn,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(14.dp)
                                )
                                Text(
                                    text = listOfNotNull(
                                        floor.ifBlank { null },
                                        room.ifBlank { null }
                                    ).joinToString("·").ifBlank { "选择房间" },
                                    style = MaterialTheme.typography.labelMedium,
                                    color = if (room.isBlank()) {
                                        MaterialTheme.colorScheme.onSurfaceVariant
                                    } else {
                                        MaterialTheme.colorScheme.onSurface
                                    },
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }

                    // 标签按钮：点击弹菜单（重选 / 笔编辑 / +新建）
                    TagPickButton(
                        templateTags = templateTags,
                        tagColors = tagColors,
                        onAddTagRequest = onAddTagRequest,
                        onEditTagRequest = onEditTagRequest
                    )

                    // 只有房间号模式留右侧空隙；序号模式输入框已占满，标签贴最右
                    if (template.markMode != MarkMode.SEQ) {
                        Spacer(Modifier.weight(1f))
                    }
                }
            } else {
                // 无模板记录：没有可选房间网格，退回自由输入
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    LabeledCompactField(
                        label = "房间",
                        value = room,
                        onValueChange = { room = it },
                        modifier = Modifier.weight(1f),
                    )
                    LabeledCompactField(
                        label = "标签",
                        value = tag,
                        onValueChange = { tag = it },
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }
    }

    /** 标签选择按钮：显示当前标签（带色），点击弹菜单在 无 + 模板已有标签 里重选/新建/编辑 */
    @Composable
    private fun TagPickButton(
        templateTags: List<String>,
        tagColors: Map<String, Int>,
        onAddTagRequest: () -> Unit,
        onEditTagRequest: (String, Int) -> Unit
    ) {
        var menuOpen by remember { mutableStateOf(false) }
        val colorIdx = tagColorIndexFor(templateTags, tagColors, tag)
        Box {
            Surface(
                shape = RoundedCornerShape(6.dp),
                color = if (tag.isNotBlank()) tagContainerColor(colorIdx) else MaterialTheme.colorScheme.surface,
                border = androidx.compose.foundation.BorderStroke(
                    width = 1.dp,
                    color = if (tag.isNotBlank()) Color.Transparent else MaterialTheme.colorScheme.outline
                ),
                onClick = { menuOpen = true }
            ) {
                Text(
                    text = tag.ifBlank { "标签" },
                    style = MaterialTheme.typography.labelMedium,
                    color = if (tag.isNotBlank()) {
                        tagContentColor(colorIdx)
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 9.dp)
                )
            }
            DropdownMenu(
                expanded = menuOpen,
                onDismissRequest = { menuOpen = false }
            ) {
                TagMenuContent(
                    templateTags = templateTags,
                    tagColors = tagColors,
                    currentTag = tag,
                    onPick = {
                        tag = it
                        menuOpen = false
                    },
                    onEditTag = { t, c ->
                        menuOpen = false
                        onEditTagRequest(t, c)
                    },
                    onAddTag = {
                        menuOpen = false
                        onAddTagRequest()
                    }
                )
            }
        }
    }
}

/**
 * 标签选择菜单内容（历史行徽标与抽屉标签按钮共用）：
 * 无标签 / 模板标签（右侧笔=编辑）/ 底部固定「+新建标签」
 */
@Composable
private fun TagMenuContent(
    templateTags: List<String>,
    tagColors: Map<String, Int>,
    currentTag: String,
    onPick: (String) -> Unit,
    onEditTag: (String, Int) -> Unit,
    onAddTag: () -> Unit
) {
    DropdownMenuItem(
        text = { Text("无标签", style = MaterialTheme.typography.bodyMedium) },
        onClick = { onPick("") }
    )
    if (templateTags.isNotEmpty()) {
        HorizontalDivider()
    }
    templateTags.forEach { t ->
        val colorIdx = tagColorIndexFor(templateTags, tagColors, t)
        DropdownMenuItem(
            text = {
                Text(
                    text = t,
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (t == currentTag) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.onSurface
                    }
                )
            },
            // 笔：编辑该标签（改名+颜色），置于行尾右对齐
            trailingIcon = {
                Icon(
                    imageVector = Icons.Filled.Edit,
                    contentDescription = "编辑标签 $t",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier
                        .size(20.dp)
                        .clickable { onEditTag(t, colorIdx) }
                )
            },
            onClick = { onPick(t) }
        )
    }
    HorizontalDivider()
    DropdownMenuItem(
        text = {
            Text(
                text = "+新建标签",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.primary
            )
        },
        onClick = onAddTag
    )
}

/** 列表行内小图标按钮：30dp 触达区 + 16dp 图标，替代默认 48dp IconButton 以压缩行高 */
@Composable
private fun SmallItemIconButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    contentDescription: String,
    tint: Color,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .size(30.dp)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            tint = tint,
            modifier = Modifier.size(17.dp)
        )
    }
}
