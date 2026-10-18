package com.example.catscandemo.ui.components

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.Save
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
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
                OutlinedTextField(
                    value = renameInput,
                    onValueChange = { renameInput = it },
                    label = { Text("文档名称") },
                    singleLine = true,
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
 * 新建文档 / 插入房间号 对话框。
 * 两种模式顶部都有文档名称输入（插入模式修改名称即重命名）；
 * “插入房间号”折叠框默认折叠，展开后有楼层数、每层房间数两个输入
 * （留空=不插入预设内容）和标签列表（点“添加标签”按钮弹窗输入确认）。
 */
@Composable
private fun CreateDocDialog(
    isCreateMode: Boolean,
    onDismiss: () -> Unit,
    onConfirm: (name: String, block: String) -> Unit,
    initialName: String = ""
) {
    var name by remember { mutableStateOf(initialName) }
    var expandPreset by remember { mutableStateOf(false) }
    var floors by remember { mutableStateOf("") }
    var rooms by remember { mutableStateOf("") }
    var tags by remember { mutableStateOf(listOf<String>()) }
    var showTagDialog by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(
                onClick = {
                    val block = buildRoomPreset(
                        floors = floors.toIntOrNull() ?: 0,
                        roomsPerFloor = rooms.toIntOrNull() ?: 0,
                        tags = tags
                    )
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
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = {
                        Text(if (isCreateMode) "文档名称" else "文档名称（修改即重命名）")
                    },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                // 折叠框：插入房间号
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { expandPreset = !expandPreset }
                        .padding(vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Icon(
                        imageVector = if (expandPreset) {
                            Icons.Default.KeyboardArrowDown
                        } else {
                            Icons.Default.ChevronRight
                        },
                        contentDescription = if (expandPreset) "收起" else "展开",
                        modifier = Modifier.size(20.dp)
                    )
                    Text(
                        text = "插入房间号（可选）",
                        style = MaterialTheme.typography.bodyMedium
                    )
                }

                if (expandPreset) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(
                            value = floors,
                            onValueChange = { floors = it.filter(Char::isDigit).take(2) },
                            label = { Text("楼层数") },
                            singleLine = true,
                            modifier = Modifier.weight(1f)
                        )
                        OutlinedTextField(
                            value = rooms,
                            onValueChange = { rooms = it.filter(Char::isDigit).take(2) },
                            label = { Text("每层房间数") },
                            singleLine = true,
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
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            modifier = Modifier.fillMaxWidth()
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
            OutlinedTextField(
                value = input,
                onValueChange = { input = it },
                label = { Text("标签（如：照明）") },
                supportingText = {
                    if (input.trim().isNotEmpty() && input.trim() in existingTags) {
                        Text("该标签已存在")
                    }
                },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
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
