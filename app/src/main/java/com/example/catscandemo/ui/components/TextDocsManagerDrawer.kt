package com.example.catscandemo.ui.components

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Save
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.catscandemo.domain.model.MarkMode
import com.example.catscandemo.domain.model.TemplateModel
import com.example.catscandemo.domain.model.toChineseUpper
import com.example.catscandemo.presentation.viewmodel.MainViewModel
import com.example.catscandemo.ui.main.TextDoc
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 文本模式下的文档管理抽屉：切换/新建/重命名/导出/删除
 */
@Composable
fun TextDocsManagerDrawer(
    visible: Boolean,
    onDismiss: () -> Unit,
    viewModel: MainViewModel,
    widthFraction: Float = 0.9f
) {
    if (visible) {
        BackHandler { onDismiss() }
    }

    val screenWidthDp = LocalConfiguration.current.screenWidthDp
    val drawerWidth = (screenWidthDp * widthFraction).dp

    Box(modifier = Modifier.fillMaxSize()) {
        AnimatedVisibility(visible = visible, enter = fadeIn(), exit = fadeOut()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.45f))
                    .clickable(onClick = onDismiss)
            )
        }

        AnimatedVisibility(
            visible = visible,
            enter = slideInHorizontally(initialOffsetX = { it }) + fadeIn(),
            exit = slideOutHorizontally(targetOffsetX = { it }) + fadeOut(),
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .imePadding()
        ) {
            Surface(
                modifier = Modifier
                    .fillMaxHeight()
                    .width(drawerWidth)
                    .windowInsetsPadding(WindowInsets.safeDrawing),
                tonalElevation = 6.dp,
                shape = MaterialTheme.shapes.large
            ) {
                TextDocsManagerPage(
                    viewModel = viewModel,
                    onClose = onDismiss,
                    modifier = Modifier.fillMaxSize()
                )
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun TextDocsManagerPage(
    viewModel: MainViewModel,
    onClose: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val docs = viewModel.textDocs
    val activeId = viewModel.activeTextDocId

    var renameTarget by remember { mutableStateOf<TextDoc?>(null) }
    var renameInput by remember { mutableStateOf("") }
    var deleteTarget by remember { mutableStateOf<TextDoc?>(null) }
    var showCreateDialog by remember { mutableStateOf(false) }
    var insertTarget by remember { mutableStateOf<TextDoc?>(null) }
    var showExportAllConfirm by remember { mutableStateOf(false) }

    fun toast(msg: String) {
        android.widget.Toast.makeText(context, msg, android.widget.Toast.LENGTH_SHORT).show()
    }

    fun exportDoc(doc: TextDoc) {
        // 导出前把编辑器里的最新内容同步回文档
        val content = if (doc.id == activeId) viewModel.textFieldValue.text else doc.content
        val time = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
        val fileName = "${safeFileNamePart(doc.name)}_$time.txt"
        val ok = saveTextToDownloads(
            context = context,
            fileName = fileName,
            mime = "text/plain",
            content = content
        )
        toast(if (ok) "已导出：$fileName" else "导出失败")
    }

    Column(modifier = modifier.padding(16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("文档管理", style = MaterialTheme.typography.titleLarge)
            Spacer(Modifier.weight(1f))
            IconButton(onClick = { showCreateDialog = true }) {
                Icon(Icons.Default.Add, contentDescription = "新建文本文档")
            }
            IconButton(onClick = onClose) {
                Icon(Icons.Default.Close, contentDescription = "关闭")
            }
        }

        Spacer(Modifier.height(12.dp))

        Box(modifier = Modifier.weight(1f, fill = true)) {
            if (docs.isEmpty()) {
                Text("暂无文档，点击右上角 + 新建。")
            } else {
                LazyColumn(verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(10.dp)) {
                    items(docs, key = { it.id }) { doc ->
                        val isActive = doc.id == activeId
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .combinedClickable(
                                    onClick = {
                                        viewModel.setActiveTextDoc(doc.id)
                                        onClose()
                                    },
                                    // 笔图标已改为“插入房间号”，重命名移到长按
                                    onLongClick = {
                                        renameTarget = doc
                                        renameInput = doc.name
                                    }
                                ),
                            shape = RoundedCornerShape(16.dp),
                            colors = CardDefaults.cardColors(
                                containerColor = if (isActive) {
                                    MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.72f)
                                } else {
                                    MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.62f)
                                }
                            )
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 14.dp, vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = doc.name,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        style = MaterialTheme.typography.titleMedium
                                    )
                                    val preview = if (doc.id == activeId) {
                                        viewModel.textFieldValue.text
                                    } else {
                                        doc.content
                                    }
                                    Text(
                                        text = "${preview.count { it == '\n' } + 1} 行 · ${preview.length} 字",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }

                                if (isActive) {
                                    Surface(
                                        shape = RoundedCornerShape(16.dp),
                                        color = MaterialTheme.colorScheme.primary
                                    ) {
                                        Text(
                                            text = "当前",
                                            modifier = Modifier.padding(
                                                horizontal = 8.dp,
                                                vertical = 4.dp
                                            ),
                                            style = MaterialTheme.typography.labelMedium,
                                            color = MaterialTheme.colorScheme.onPrimary
                                        )
                                    }
                                }

                                IconButton(
                                    onClick = { exportDoc(doc) },
                                    modifier = Modifier.size(40.dp)
                                ) {
                                    Icon(
                                        Icons.Default.Save,
                                        contentDescription = "导出",
                                        modifier = Modifier.size(22.dp)
                                    )
                                }

                                IconButton(
                                    onClick = { insertTarget = doc },
                                    modifier = Modifier.size(40.dp)
                                ) {
                                    Icon(
                                        Icons.Default.Edit,
                                        contentDescription = "编辑",
                                        modifier = Modifier.size(22.dp)
                                    )
                                }

                                IconButton(
                                    onClick = { deleteTarget = doc },
                                    modifier = Modifier.size(40.dp)
                                ) {
                                    Icon(
                                        Icons.Default.Delete,
                                        contentDescription = "删除",
                                        modifier = Modifier.size(22.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        // 导出全部文档：每个文档导出为一个独立 txt（当前文档先同步编辑器最新内容）
        if (docs.isNotEmpty()) {
            Spacer(Modifier.height(12.dp))
            OutlinedButton(
                onClick = { showExportAllConfirm = true },
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(
                    Icons.Default.Save,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(Modifier.width(6.dp))
                Text("导出全部文档（${docs.size} 个）")
            }
        }
    }

    // 导出全部：二次确认
    if (showExportAllConfirm) {
        AlertDialog(
            onDismissRequest = { showExportAllConfirm = false },
            title = { Text("导出全部文档") },
            text = { Text("确定一次性导出全部 ${docs.size} 个文档吗？每个文档将保存为一个独立的 txt 文件。") },
            confirmButton = {
                TextButton(
                    onClick = {
                        showExportAllConfirm = false
                        val time = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault())
                            .format(Date())
                        var okCount = 0
                        docs.forEach { doc ->
                            val content = if (doc.id == activeId) {
                                viewModel.textFieldValue.text
                            } else {
                                doc.content
                            }
                            val ok = saveTextToDownloads(
                                context = context,
                                fileName = "${safeFileNamePart(doc.name)}_$time.txt",
                                mime = "text/plain",
                                content = content
                            )
                            if (ok) okCount++
                        }
                        toast("已导出 $okCount/${docs.size} 个文档")
                    }
                ) { Text("导出") }
            },
            dismissButton = {
                TextButton(onClick = { showExportAllConfirm = false }) { Text("取消") }
            }
        )
    }

    renameTarget?.let { target ->
        AlertDialog(
            onDismissRequest = { renameTarget = null },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.renameTextDoc(target.id, renameInput)
                        renameTarget = null
                    }
                ) { Text("确定") }
            },
            dismissButton = {
                TextButton(onClick = { renameTarget = null }) { Text("取消") }
            },
            title = { Text("重命名文档") },
            text = {
                LabeledCompactField(
                    label = "文档名称",
                    value = renameInput,
                    onValueChange = { renameInput = it },
                    modifier = Modifier.fillMaxWidth()
                )
            }
        )
    }

    deleteTarget?.let { target ->
        AlertDialog(
            onDismissRequest = { deleteTarget = null },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.deleteTextDoc(target.id)
                        deleteTarget = null
                    }
                ) { Text("删除") }
            },
            dismissButton = {
                TextButton(onClick = { deleteTarget = null }) { Text("取消") }
            },
            title = { Text("删除文档") },
            text = { Text("确认删除「${target.name}」？文档内容将一并删除。") }
        )
    }

    // 新建文档：名称 + 可选房间号预设，创建后自动切换到新文档
    if (showCreateDialog) {
        CreateDocDialog(
            isCreateMode = true,
            templates = viewModel.templates,
            onDismiss = { showCreateDialog = false },
            onConfirm = { name, block ->
                viewModel.createTextDocWithContent(name, block)
                showCreateDialog = false
            }
        )
    }

    // 插入房间号到指定文档末尾（不替换已有内容）；同时支持在对话框顶部改名
    insertTarget?.let { target ->
        CreateDocDialog(
            isCreateMode = false,
            initialName = target.name,
            templates = viewModel.templates,
            onDismiss = { insertTarget = null },
            onConfirm = { name, block ->
                val newName = name.trim()
                if (newName.isNotEmpty() && newName != target.name) {
                    viewModel.renameTextDoc(target.id, newName)
                }
                if (block.isNotEmpty()) {
                    viewModel.appendBlockToTextDoc(target.id, block)
                    toast("已插入到「${newName.ifEmpty { target.name }}」末尾")
                }
                insertTarget = null
            }
        )
    }
}

/**
 * 新建文档 / 插入预设 对话框。
 * 两种模式顶部都有文档名称输入（插入模式修改名称即重命名）；
 * 「插入预设」为文字提示 + 三个按钮（序号/房间号/从模板导入），
 * 默认都不激活，点了哪个才展示其设置参数，都不点=空文档。
 * 主界面文档选择器下拉的"新建文档"也复用本对话框，保持能力一致。
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun CreateDocDialog(
    isCreateMode: Boolean,
    onDismiss: () -> Unit,
    onConfirm: (name: String, block: String) -> Unit,
    initialName: String = "",
    // 从模板导入预设的候选：全部模板（在对话框里直接多选，不必回模板模式激活）
    templates: List<TemplateModel> = emptyList()
) {
    var name by remember { mutableStateOf(initialName) }
    // 预设形式：-1=未选（不插入） 0=序号 1=房间号 2=从模板导入；点了才展示对应选项
    var presetMode by remember { mutableStateOf(-1) }
    // 从模板导入：勾选的模板 id 集合（多选，按列表顺序拼接）
    var importPick by remember { mutableStateOf<Set<String>>(emptySet()) }
    var floors by remember { mutableStateOf("") }
    var rooms by remember { mutableStateOf("") }
    var tags by remember { mutableStateOf(listOf<String>()) }
    var seqCount by remember { mutableStateOf("") }
    var seqPrefix by remember { mutableStateOf("") }
    var seqSuffix by remember { mutableStateOf("") }
    var seqChineseUpper by remember { mutableStateOf(false) }
    var seqDescending by remember { mutableStateOf(false) }
    // 自动补0（默认开启）：按总数位数补齐，如 100 个 → 001，10 个 → 01
    var seqPad by remember { mutableStateOf(true) }
    var showTagDialog by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(
                onClick = {
                    val block = when (presetMode) {
                        1 -> buildRoomPreset(
                            floors = floors.toIntOrNull() ?: 0,
                            roomsPerFloor = rooms.toIntOrNull() ?: 0,
                            tags = tags
                        )
                        2 -> templates
                            .filter { it.id in importPick && it.scans.isNotEmpty() }
                            .joinToString("\n\n") { buildTemplateImportBlock(it) }
                        0 -> buildSeqPreset(
                            count = seqCount.toIntOrNull() ?: 0,
                            prefix = seqPrefix,
                            suffix = seqSuffix,
                            chineseUpper = seqChineseUpper,
                            descending = seqDescending,
                            pad = seqPad
                        )
                        else -> ""
                    }
                    onConfirm(name.trim(), block)
                }
            ) { Text(if (isCreateMode) "创建" else "确认") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("取消") }
        },
        title = { Text(if (isCreateMode) "新建文档" else "编辑") },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // 两种模式都显示名称输入：插入模式下修改名称即重命名
                LabeledCompactField(
                    label = "文档名称",
                    value = name,
                    onValueChange = { name = it },
                    placeholder = if (isCreateMode) "" else "修改即重命名",
                    modifier = Modifier.fillMaxWidth()
                )

                // 插入预设：文字提示 + 三个预设按钮；点了哪个才展示哪个的选项（都不点=空文档）
                Text(
                    text = "插入预设（可选）",
                    style = MaterialTheme.typography.labelMedium
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilterChip(
                            selected = presetMode == 0,
                            onClick = { presetMode = 0 },
                            label = { Text("序号") }
                        )
                        FilterChip(
                            selected = presetMode == 1,
                            onClick = { presetMode = 1 },
                            label = { Text("房间号") }
                        )
                        FilterChip(
                            selected = presetMode == 2,
                            onClick = { presetMode = 2 },
                            label = { Text("从模板导入") }
                        )
                    }

                    if (presetMode == 2) {
                        // 从模板导入：在对话框里直接多选模板（按列表顺序拼接，模板之间空一行）
                        if (templates.isEmpty()) {
                            Text(
                                "还没有模板，可先到模板模式创建",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        } else {
                            FlowRow(
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                verticalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                templates.forEach { t ->
                                    val picked = t.id in importPick
                                    Box(
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(8.dp))
                                            .background(
                                                if (picked) {
                                                    MaterialTheme.colorScheme.primaryContainer
                                                } else {
                                                    MaterialTheme.colorScheme.surfaceVariant
                                                }
                                            )
                                            .border(
                                                width = 1.dp,
                                                color = if (picked) {
                                                    MaterialTheme.colorScheme.primary
                                                } else {
                                                    MaterialTheme.colorScheme.outlineVariant
                                                },
                                                shape = RoundedCornerShape(8.dp)
                                            )
                                            .clickable {
                                                importPick = if (picked) {
                                                    importPick - t.id
                                                } else {
                                                    importPick + t.id
                                                }
                                            }
                                            .padding(horizontal = 10.dp, vertical = 5.dp)
                                    ) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(5.dp)
                                        ) {
                                            TemplateTypeDot(markMode = t.markMode)
                                            Text(
                                                text = t.name.ifBlank { "未命名模板" },
                                                style = MaterialTheme.typography.labelSmall,
                                                color = if (picked) {
                                                    MaterialTheme.colorScheme.onPrimaryContainer
                                                } else {
                                                    MaterialTheme.colorScheme.onSurfaceVariant
                                                },
                                                maxLines = 1
                                            )
                                            Text(
                                                text = "${t.scans.size}条",
                                                style = MaterialTheme.typography.labelSmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                                            )
                                        }
                                    }
                                }
                            }
                            val pickedList = templates.filter { it.id in importPick }
                            val totalLines = pickedList.sumOf { it.scans.size }
                            Text(
                                text = "已选 ${pickedList.size} 个模板 · 共 $totalLines 条数据" +
                                        if (pickedList.isEmpty()) "（未选则创建空文档）" else "",
                                style = MaterialTheme.typography.labelMedium
                            )
                            if (totalLines > 0) {
                                val preview = pickedList
                                    .filter { it.scans.isNotEmpty() }
                                    .joinToString("\n\n") { buildTemplateImportBlock(it) }
                                    .lines().take(3).joinToString("、")
                                Text(
                                    text = "预览：$preview" + if (totalLines > 3) " …" else "",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Text(
                                text = "每行格式：序号-房间号-标签-数据（序号模板无房间号；" +
                                        "空字段自动跳过；序号=按录入先后编号，最早为 1）",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    } else if (presetMode == 1) {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            LabeledCompactField(
                                label = "楼层数",
                                value = floors,
                                onValueChange = { floors = it.filter(Char::isDigit).take(2) },
                                keyboardType = KeyboardType.Number,
                                modifier = Modifier.weight(1f)
                            )
                            LabeledCompactField(
                                label = "每层房间数",
                                value = rooms,
                                onValueChange = { rooms = it.filter(Char::isDigit).take(2) },
                                keyboardType = KeyboardType.Number,
                                modifier = Modifier.weight(1f)
                            )
                        }

                        // 添加标签：按钮唤出弹窗确认，避免用户输入后忘记点“添加”
                        OutlinedButton(
                            onClick = { showTagDialog = true },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(
                                imageVector = Icons.Default.Add,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(Modifier.width(4.dp))
                            Text("添加标签")
                        }

                        if (tags.isNotEmpty()) {
                            FlowRow(
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                verticalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                tags.forEach { tag ->
                                    Surface(
                                        shape = RoundedCornerShape(14.dp),
                                        color = MaterialTheme.colorScheme.primaryContainer
                                    ) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            modifier = Modifier.padding(start = 10.dp, end = 2.dp)
                                        ) {
                                            Text(
                                                text = tag,
                                                style = MaterialTheme.typography.labelMedium,
                                                color = MaterialTheme.colorScheme.onPrimaryContainer
                                            )
                                            IconButton(
                                                onClick = { tags = tags - tag },
                                                modifier = Modifier.size(24.dp)
                                            ) {
                                                Icon(
                                                    Icons.Default.Close,
                                                    contentDescription = "移除标签 $tag",
                                                    modifier = Modifier.size(14.dp),
                                                    tint = MaterialTheme.colorScheme.onPrimaryContainer
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }

                        Text(
                            text = "楼层/房间数留空则不插入预设内容；每层房间与标签按顺序一一配对，" +
                                    "标签不足时循环使用。例如 2 层 × 2 房间 + 标签[照明、空调]：" +
                                    "101照明-、102空调-，楼层之间空一行。",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    } else if (presetMode == 0) {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            LabeledCompactField(
                                label = "前缀",
                                value = seqPrefix,
                                onValueChange = { seqPrefix = it.take(10) },
                                placeholder = "可空",
                                modifier = Modifier.weight(1f)
                            )
                            LabeledCompactField(
                                label = "后缀",
                                value = seqSuffix,
                                onValueChange = { seqSuffix = it.take(10) },
                                placeholder = "可空",
                                modifier = Modifier.weight(1f)
                            )
                        }

                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            LabeledCompactField(
                                label = "序号数量",
                                value = seqCount,
                                onValueChange = { seqCount = it.filter(Char::isDigit).take(4) },
                                keyboardType = KeyboardType.Number,
                                modifier = Modifier.weight(1f)
                            )
                        }

                        Text("数字样式", style = MaterialTheme.typography.labelMedium)
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            FilterChip(
                                selected = !seqChineseUpper,
                                onClick = { seqChineseUpper = false },
                                label = { Text("数字", Modifier.fillMaxWidth(), textAlign = androidx.compose.ui.text.style.TextAlign.Center) },
                                modifier = Modifier.weight(1f)
                            )
                            FilterChip(
                                selected = seqChineseUpper,
                                onClick = { seqChineseUpper = true },
                                label = { Text("中文大写数字", Modifier.fillMaxWidth(), textAlign = androidx.compose.ui.text.style.TextAlign.Center) },
                                modifier = Modifier.weight(1f)
                            )
                        }

                        Text("录入顺序", style = MaterialTheme.typography.labelMedium)
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            FilterChip(
                                selected = !seqDescending,
                                onClick = { seqDescending = false },
                                label = { Text("正序", Modifier.fillMaxWidth(), textAlign = androidx.compose.ui.text.style.TextAlign.Center) },
                                modifier = Modifier.weight(1f)
                            )
                            FilterChip(
                                selected = seqDescending,
                                onClick = { seqDescending = true },
                                label = { Text("倒序", Modifier.fillMaxWidth(), textAlign = androidx.compose.ui.text.style.TextAlign.Center) },
                                modifier = Modifier.weight(1f)
                            )
                        }

                        if (!seqChineseUpper) {
                            Text("自动补0", style = MaterialTheme.typography.labelMedium)
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                FilterChip(
                                    selected = seqPad,
                                    onClick = { seqPad = true },
                                    label = { Text("补0", Modifier.fillMaxWidth(), textAlign = androidx.compose.ui.text.style.TextAlign.Center) },
                                    modifier = Modifier.weight(1f)
                                )
                                FilterChip(
                                    selected = !seqPad,
                                    onClick = { seqPad = false },
                                    label = { Text("不补", Modifier.fillMaxWidth(), textAlign = androidx.compose.ui.text.style.TextAlign.Center) },
                                    modifier = Modifier.weight(1f)
                                )
                            }
                            Text(
                                text = "按总数位数自动补齐：100 个序号 → 001 起，10 个 → 01 起。",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                        val previewCount = (seqCount.toIntOrNull() ?: 0).coerceAtLeast(1)
                        Text(
                            text = "预览：${buildSeqPreset(3.coerceAtMost(previewCount), seqPrefix, seqSuffix, seqChineseUpper, seqDescending, seqPad, previewCount)
                                .replace("\n", "、")}" +
                                    if (previewCount > 3) " …" else "",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )

                        Text(
                            text = "从 1 开始生成 N 行“前缀+序号+后缀-”；倒序时首行为最大号。" +
                                    "数量留空则不插入预设内容。",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
            }
        }
    )

    // 添加标签弹窗：确认后才真正加入列表
    if (showTagDialog) {
        TagInputDialog(
            existingTags = tags,
            onDismiss = { showTagDialog = false },
            onConfirm = { t ->
                if (t.isNotEmpty() && t !in tags) tags = tags + t
                showTagDialog = false
            }
        )
    }
}

/** 标签输入弹窗：输入 + 确认/取消，避免直接暴露输入框导致忘记点“添加” */
@Composable
private fun TagInputDialog(
    existingTags: List<String>,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit
) {
    var input by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("添加标签") },
        text = {
            Column {
                LabeledCompactField(
                    label = "标签",
                    value = input,
                    onValueChange = { input = it },
                    placeholder = "如：照明",
                    modifier = Modifier.fillMaxWidth()
                )
                if (input.trim().isNotEmpty() && input.trim() in existingTags) {
                    Text(
                        text = "该标签已存在",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.padding(start = 4.dp, top = 2.dp)
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onConfirm(input.trim()) },
                enabled = input.trim().isNotEmpty() && input.trim() !in existingTags
            ) { Text("确认") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("取消") }
        }
    )
}

/**
 * 生成序号预设内容：从 1 开始 N 行“前缀+序号+后缀-”，倒序时首行为最大号；
 * 补0开启时按总数量级补齐（100 个 → 001 起，10 个 → 01 起）；
 * 行尾固定 "-" 作为扫码内容追加点。
 */
private fun buildSeqPreset(
    count: Int,
    prefix: String,
    suffix: String,
    chineseUpper: Boolean,
    descending: Boolean,
    pad: Boolean,
    padTotalHint: Int = count
): String {
    if (count <= 0) return ""
    // 预览时用真实总数确定补零位数，避免只看前 3 条时位数来回跳
    val padWidth = if (pad && !chineseUpper) padTotalHint.toString().length else 0
    return (0 until count).joinToString("\n") { i ->
        val v = if (descending) count - i else i + 1
        val num = when {
            chineseUpper -> toChineseUpper(v)
            padWidth > 0 -> v.toString().padStart(padWidth, '0')
            else -> v.toString()
        }
        "$prefix$num$suffix-"
    }
}

/**
 * 生成房间号预设内容：
 * 每层楼的房间号 = 楼层号×100 + 房间序号；每个房间都要包含每种标签各一行
 * （如 101空调-/101照明-/101热水-，再 102空调-/102照明-/102热水-）；
 * 行尾固定 "-" 作为扫码内容追加点；楼层之间空一行；末尾不留换行。
 */
private fun buildRoomPreset(floors: Int, roomsPerFloor: Int, tags: List<String>): String {
    if (floors <= 0 || roomsPerFloor <= 0) return ""
    return (1..floors).joinToString("\n\n") { f ->
        (1..roomsPerFloor).joinToString("\n") { i ->
            val room = f * 100 + i
            if (tags.isEmpty()) {
                "$room-"
            } else {
                tags.joinToString("\n") { tag -> "$room$tag-" }
            }
        }
    }
}

/**
 * 把模板扫码数据导出为文档预设行：
 * 房间号模板 = 序号-房间号-标签-数据；序号模板 = 序号-标签-数据（无房间号）。
 * 序号 = 按录入先后编号（最早一条为 1）；房间号/标签为空的字段自动跳过，保持行干净。
 */
internal fun buildTemplateImportBlock(t: TemplateModel): String {
    if (t.scans.isEmpty()) return ""
    return t.scans.asReversed().mapIndexed { index, s ->
        val middle = if (t.markMode == MarkMode.SEQ) {
            listOfNotNull(s.tag.takeIf { it.isNotBlank() })
        } else {
            listOfNotNull(
                s.room.takeIf { it.isNotBlank() },
                s.tag.takeIf { it.isNotBlank() }
            )
        }
        (listOf((index + 1).toString()) + middle + s.text).joinToString("-")
    }.joinToString("\n")
}
