package com.example.catscandemo.ui.components

import android.content.ContentValues
import android.content.Context
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items as gridItems
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Save
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.toMutableStateList
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.catscandemo.domain.model.MarkMode
import com.example.catscandemo.domain.model.SeqConfig
import com.example.catscandemo.domain.model.TemplateModel
import com.example.catscandemo.domain.model.formatSeqValue
import com.example.catscandemo.presentation.viewmodel.MainViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private enum class DrawerPage { MANAGER, EDITOR, ROOMS }


@Composable
fun TemplateEditorNavigator(
    viewModel: MainViewModel,
    onClose: () -> Unit,
    modifier: Modifier = Modifier
) {
    var page by rememberSaveable { mutableStateOf(DrawerPage.MANAGER) }
    var editingId by rememberSaveable { mutableStateOf<String?>(null) }

    val templates = viewModel.templates
    val activeId = viewModel.activeTemplateId

    val editingTemplate = templates.firstOrNull { it.id == editingId }

    when (page) {
        DrawerPage.MANAGER -> {
            TemplateManagerPage(
                templates = templates,
                activeId = activeId,
                onSelect = { id -> viewModel.setActiveTemplate(id) },
                onAdd = { name, markMode, seqConfig, maxFloor, roomCount, tags, tagColors ->
                    viewModel.addTemplate(
                        name, markMode, seqConfig, maxFloor, roomCount, tags, tagColors
                    )
                },
                onDelete = { id -> viewModel.deleteTemplate(id) },
                onOpen = { id ->
                    editingId = id
                    page = DrawerPage.EDITOR
                },
                onClose = onClose,
                modifier = modifier
            )
        }

        DrawerPage.EDITOR -> {
            val t = editingTemplate ?: run {
                page = DrawerPage.MANAGER
                return
            }
            val context = LocalContext.current
            val showToast = { msg: String ->
                android.widget.Toast.makeText(context, msg, android.widget.Toast.LENGTH_SHORT).show()
            }

            TemplateEditorSheet(
                viewModel = viewModel,
                template = t,
                isActive = (t.id == activeId),
                onSetActive = { viewModel.setActiveTemplate(t.id) },
                onSave = { updated -> viewModel.updateTemplate(updated) },
                onClearScans = {
                    viewModel.clearTemplateFloorScans(
                        id = t.id,
                        floor = viewModel.scanSelectedFloor,
                        showToast = showToast
                    )
                },
                onUploadTemplateData = { templateData, toastCallback -> viewModel.uploadTemplateData(templateData, toastCallback) },
                onBack = { page = DrawerPage.MANAGER },
                onEditRooms = { page = DrawerPage.ROOMS },
                onClose = onClose,
                modifier = modifier
            )
        }

        DrawerPage.ROOMS -> {
            val t = editingTemplate ?: run {
                page = DrawerPage.EDITOR
                return
            }
            RoomPickerPage(
                templateId = t.id,
                maxFloor = t.maxFloor,
                roomCountPerFloor = t.roomCountPerFloor,
                initialSelected = t.selectedRooms,
                onBack = { page = DrawerPage.EDITOR },
                onApply = { rooms ->
                    viewModel.updateTemplate(t.copy(selectedRooms = rooms))
                    page = DrawerPage.EDITOR
                },
                modifier = modifier
            )
        }
    }
}

/** 模板管理页：常驻勾选框（选中后底部变红色删除）+ 单一导出按钮（点击再选格式），紧凑列表 */
@Composable
private fun TemplateManagerPage(
    templates: List<TemplateModel>,
    activeId: String?,
    onSelect: (String) -> Unit,
    onAdd: (String, MarkMode, SeqConfig?, Int?, Int?, List<String>, Map<String, Int>) -> Unit,
    onDelete: (String) -> Unit,
    onOpen: (String) -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier
) {
    var showAdd by remember { mutableStateOf(false) }
    var newName by remember { mutableStateOf("") }
    val addState = remember { NewTemplateParamsState() }

    // 勾选框长期展示：勾选即进入"待删除"选择；导出只在未勾选时可用
    var selectedTemplateIds by remember { mutableStateOf<Set<String>>(emptySet()) }
    var showExportFormat by remember { mutableStateOf(false) }
    var showBatchDeleteConfirm by remember { mutableStateOf(false) }

    val context = LocalContext.current

    fun saveTextToDownloads(fileName: String, mime: String, content: String): Boolean {
        // 统一走共享导出（遵循设置里的自定义导出目录）
        return saveTextToDownloads(
            context, fileName, mime, content
        )
    }

    fun buildExportTxt(selectedTemplates: List<TemplateModel> = templates): String {
        val sdf = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())

        // 扫码内容等字段来自外部，含制表符/换行会破坏 TXT 列结构，统一替换为空格
        fun esc(v: String) = v.replace("\t", " ").replace("\n", " ").replace("\r", " ")

        val header = "序号\t模板名称\t楼层\t房间号\t标签\t时间\t扫码内容"
        val lines = ArrayList<String>()
        lines.add(header)

        var seq = 1
        selectedTemplates.forEach { t ->
            if (t.scans.isEmpty()) {
                lines.add("${seq++}\t${esc(t.name)}\t\t\t\t\t")
            } else {
                t.scans.forEach { s ->
                    val time = sdf.format(Date(s.timestamp))
                    lines.add(
                        "${seq++}\t${esc(t.name)}\t${esc(s.floor)}\t${esc(s.room)}\t${esc(s.tag)}\t$time\t${esc(s.text)}"
                    )
                }
            }
        }
        return lines.joinToString("\n")
    }

    fun buildExportJson(selectedTemplates: List<TemplateModel> = templates): String {
        val root = JSONObject()
        root.put("activeTemplateId", activeId ?: "")

        val arr = JSONArray()
        selectedTemplates.forEach { t ->
            val obj = JSONObject()
            obj.put("id", t.id)
            obj.put("name", t.name)
            obj.put("maxFloor", t.maxFloor)
            obj.put("roomCountPerFloor", t.roomCountPerFloor)
            obj.put("mode", t.mode.name)
            obj.put("lastSelectedFloor", t.lastSelectedFloor)
            obj.put("lastSelectedTag", t.lastSelectedTag)

            val tags = JSONArray()
            t.tags.forEach { tags.put(it) }
            obj.put("tags", tags)

            val rooms = JSONArray()
            t.selectedRooms.forEach { rooms.put(it) }
            obj.put("selectedRooms", rooms)

            val scans = JSONArray()
            t.scans.forEach { s ->
                val so = JSONObject()
                so.put("text", s.text)
                so.put("timestamp", s.timestamp)
                so.put("floor", s.floor)
                so.put("room", s.room)
                so.put("tag", s.tag)
                scans.put(so)
            }
            obj.put("scans", scans)

            arr.put(obj)
        }
        root.put("templates", arr)

        return root.toString(2) // 带缩进，便于阅读
    }

    fun toast(msg: String) {
        android.widget.Toast.makeText(context, msg, android.widget.Toast.LENGTH_SHORT).show()
    }

    // 默认模板名：猫猫1栋，重名则猫猫2栋，以此类推
    fun suggestTemplateName(): String {
        val names = templates.map { it.name }.toSet()
        var n = 1
        while ("猫猫${n}栋" in names) n++
        return "猫猫${n}栋"
    }

    fun safeFileNamePart(s: String): String {
        return s.trim()
            .ifBlank { "未命名" }
            .replace(Regex("""[\\/:*?"<>|]"""), "_")
            .replace(Regex("""\s+"""), "_")
    }

    fun exportBaseName(): String {
        val sdf = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault())
        val time = sdf.format(Date())
        val t = templates.firstOrNull { it.id == activeId }
        val name = safeFileNamePart(t?.name ?: "模板")
        return "${name}_$time"
    }

    fun doExportTxt() {
        val picked = templates.filter { it.id in selectedTemplateIds }
        if (picked.isNotEmpty()) {
            // 多选导出：每个勾选的模板各导出一个文件（不再只导出当前模板）
            val time = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
            var okCount = 0
            picked.forEach { t ->
                val fileName = "${safeFileNamePart(t.name)}_$time.txt"
                if (saveTextToDownloads(fileName, "text/plain", buildExportTxt(listOf(t)))) okCount++
            }
            toast(if (okCount == picked.size) "已导出 $okCount 个模板文件" else "导出成功 $okCount/${picked.size} 个")
            return
        }
        val fileName = "${exportBaseName()}.txt"
        val ok = saveTextToDownloads(
            fileName = fileName,
            mime = "text/plain",
            content = buildExportTxt()
        )
        toast(if (ok) "已导出：$fileName" else "导出TXT失败")
    }

    fun doExportJson() {
        val picked = templates.filter { it.id in selectedTemplateIds }
        if (picked.isNotEmpty()) {
            // 多选导出：每个勾选的模板各导出一个文件
            val time = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
            var okCount = 0
            picked.forEach { t ->
                val fileName = "${safeFileNamePart(t.name)}_$time.json"
                if (saveTextToDownloads(fileName, "application/json", buildExportJson(listOf(t)))) okCount++
            }
            toast(if (okCount == picked.size) "已导出 $okCount 个模板文件" else "导出成功 $okCount/${picked.size} 个")
            return
        }
        val fileName = "${exportBaseName()}.json"
        val ok = saveTextToDownloads(
            fileName = fileName,
            mime = "application/json",
            content = buildExportJson()
        )
        toast(if (ok) "已导出：$fileName" else "导出JSON失败")
    }

    val selectedCount = selectedTemplateIds.size

    Column(
        modifier = modifier.padding(horizontal = 12.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        // 头部：标题 + 已选计数 + 右上动作区（勾选后出现垃圾桶；软盘=导出；X=关闭）
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("模板管理", style = MaterialTheme.typography.titleMedium)
            if (selectedCount > 0) {
                Spacer(Modifier.width(8.dp))
                Text(
                    "已选 $selectedCount",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.error
                )
            }
            Spacer(Modifier.weight(1f))
            if (selectedCount > 0) {
                IconButton(
                    onClick = { showBatchDeleteConfirm = true },
                    modifier = Modifier.size(36.dp)
                ) {
                    Icon(
                        Icons.Default.Delete,
                        contentDescription = "删除选中模板",
                        tint = MaterialTheme.colorScheme.error
                    )
                }
            }
            IconButton(
                onClick = { showExportFormat = true },
                enabled = templates.isNotEmpty(),
                modifier = Modifier.size(36.dp)
            ) {
                Icon(Icons.Default.Save, contentDescription = "导出")
            }
            IconButton(onClick = onClose, modifier = Modifier.size(36.dp)) {
                Icon(Icons.Default.Close, contentDescription = "关闭")
            }
        }

        // 模板列表：紧凑行（勾选框 + 名称 + 当前徽标 + 类型tag + 编辑笔），
        // 尾部固定一项"+ 新建模板"（样式与模板行一致，替代右上角加号）
        Box(modifier = Modifier.weight(1f, fill = true)) {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                items(templates, key = { it.id }) { t ->
                    val isActive = (t.id == activeId)
                    val isChecked = t.id in selectedTemplateIds
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(
                                color = if (isActive) {
                                    MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.72f)
                                } else {
                                    MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.62f)
                                },
                                shape = RoundedCornerShape(10.dp)
                            )
                            .clickable { onSelect(t.id) }
                            .padding(start = 6.dp, end = 2.dp, top = 4.dp, bottom = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        CompactCheckbox(
                            checked = isChecked,
                            onToggle = {
                                selectedTemplateIds = if (isChecked) {
                                    selectedTemplateIds - t.id
                                } else {
                                    selectedTemplateIds + t.id
                                }
                            }
                        )

                        // 名称+当前徽标打包占满左侧（内容左对齐）；
                        // 类型tag+笔不受名称长度影响，始终贴右
                        Row(
                            modifier = Modifier.weight(1f),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Text(
                                text = t.name.ifBlank { "未命名模板" },
                                modifier = Modifier.weight(1f, fill = false),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                style = MaterialTheme.typography.bodyLarge
                            )
                            if (isActive) {
                                Surface(
                                    shape = RoundedCornerShape(10.dp),
                                    color = MaterialTheme.colorScheme.primary
                                ) {
                                    Text(
                                        text = "当前",
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onPrimary
                                    )
                                }
                            }
                        }

                        TemplateTypeTag(markMode = t.markMode)

                        IconButton(
                            onClick = { onOpen(t.id) },
                            modifier = Modifier.size(32.dp)
                        ) {
                            Icon(
                                Icons.Default.Edit,
                                contentDescription = "查看/编辑",
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                }

                // 尾部固定项：+ 新建模板（样式与模板行一致）
                item(key = "__add_template__") {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(38.dp)
                            .background(
                                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
                                shape = RoundedCornerShape(10.dp)
                            )
                            .clickable {
                                newName = suggestTemplateName()
                                showAdd = true
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "＋ 新建模板",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }
        }
    }

    // 新增模板：默认房间号模板，参数区默认展开（与编辑器/主页新建一致）
    if (showAdd) {
        AlertDialog(
            onDismissRequest = { showAdd = false },
            confirmButton = {
                TextButton(
                    onClick = {
                        onAdd(
                            newName.trim().ifBlank { suggestTemplateName() },
                            addState.markMode,
                            addState.buildSeqConfig(),
                            addState.floorText.toIntOrNull(),
                            addState.roomCountText.toIntOrNull(),
                            addState.tags,
                            addState.tagColors
                        )
                        newName = ""
                        showAdd = false
                    }
                ) { Text("创建") }
            },
            dismissButton = {
                TextButton(onClick = { showAdd = false }) { Text("取消") }
            },
            title = { Text("新增模板") },
            text = {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState())
                ) {
                    LabeledCompactField(
                        label = "模板名称",
                        value = newName,
                        onValueChange = { newName = it },
                        placeholder = "默认 ${suggestTemplateName()}",
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(Modifier.height(12.dp))
                    NewTemplateFields(state = addState)
                }
            }
        )
    }

    // 导出格式选择（导出合并为单按钮后，格式在此选择）
    if (showExportFormat) {
        AlertDialog(
            onDismissRequest = { showExportFormat = false },
            title = { Text("选择导出格式") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(
                        onClick = {
                            showExportFormat = false
                            doExportTxt()
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(40.dp),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp)
                    ) { Text("TXT（表格）") }
                    Button(
                        onClick = {
                            showExportFormat = false
                            doExportJson()
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(40.dp),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp)
                    ) { Text("JSON（完整）") }
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = { showExportFormat = false }) { Text("取消") }
            }
        )
    }

    // 批量删除二次确认（垃圾桶已从列表行移除，删除统一走底部红色按钮）
    if (showBatchDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { showBatchDeleteConfirm = false },
            confirmButton = {
                TextButton(
                    onClick = {
                        val ids = selectedTemplateIds
                        selectedTemplateIds = emptySet()
                        showBatchDeleteConfirm = false
                        ids.forEach { onDelete(it) }
                    }
                ) {
                    Text("删除", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showBatchDeleteConfirm = false }) { Text("取消") }
            },
            title = { Text("删除 ${selectedCount} 个模板") },
            text = { Text("确认删除选中的 ${selectedCount} 个模板？它们的离线扫码数据也会一并删除，且不可恢复。") }
        )
    }
}

/** 紧凑勾选框：22dp 方框，点击勾选/取消（替代 48dp 默认 Checkbox 以压缩行高） */
@Composable
private fun CompactCheckbox(
    checked: Boolean,
    onToggle: () -> Unit
) {
    Box(
        modifier = Modifier
            .size(22.dp)
            .background(
                color = if (checked) {
                    MaterialTheme.colorScheme.primary
                } else {
                    Color.Transparent
                },
                shape = RoundedCornerShape(4.dp)
            )
            .border(
                width = 1.5.dp,
                color = if (checked) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.outline
                },
                shape = RoundedCornerShape(4.dp)
            )
            .clickable(onClick = onToggle),
        contentAlignment = Alignment.Center
    ) {
        if (checked) {
            Icon(
                imageVector = Icons.Filled.Check,
                contentDescription = if (checked) "取消选择" else "选择",
                tint = MaterialTheme.colorScheme.onPrimary,
                modifier = Modifier.size(15.dp)
            )
        }
    }
}

@OptIn(
    ExperimentalMaterial3Api::class,
    androidx.compose.foundation.ExperimentalFoundationApi::class,
    androidx.compose.foundation.layout.ExperimentalLayoutApi::class
)
@Composable
fun TemplateEditorSheet(
    viewModel: MainViewModel,
    template: TemplateModel,
    isActive: Boolean,
    onSetActive: () -> Unit,
    onSave: (TemplateModel) -> Unit,
    onClearScans: () -> Unit,
    onUploadTemplateData: (TemplateModel, (String) -> Unit) -> Unit,
    onBack: () -> Unit,
    onEditRooms: () -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier
) {
    var name by rememberSaveable(template.id) { mutableStateOf(template.name) }

    // 参数区与"新建模板"共用同一套界面/状态（NewTemplateFields），
    // 编辑器从模板载入；切换模式不改动已有扫码记录
    val paramsState = remember(template.id) {
        NewTemplateParamsState().apply { loadFrom(template) }
    }

    fun roomSuffix(r: Int): String = if (r in 1..9) "0$r" else "$r"

    fun buildAllRooms(maxFloor: Int, roomCount: Int): List<String> {
        val mf = maxFloor.coerceAtLeast(1)
        val rc = roomCount.coerceIn(1, 999)
        return (1..mf).flatMap { f ->
            (1..rc).map { r -> "$f${roomSuffix(r)}" }
        }
    }

    // 自动保存：任何参数/名称改动停顿 500ms 后落盘（无保存按钮）。
    // 标签由参数区内 tagsSlot 的 TemplateTagsEditor 自己即时持久化，
    // 因此这里的 copy 不触碰 tags/tagColors/scans。
    fun buildUpdated(): TemplateModel? {
        val f = (paramsState.floorText.toIntOrNull() ?: template.maxFloor).coerceAtLeast(1)
        val r = (paramsState.roomCountText.toIntOrNull() ?: template.roomCountPerFloor)
            .coerceIn(1, 999)
        // 只有楼层数/房间数变化时才重建全选房间，避免改名等无关自动保存抹掉精细勾选
        val selectedRooms =
            if (paramsState.markMode == MarkMode.ROOM &&
                (f != template.maxFloor || r != template.roomCountPerFloor)
            ) {
                buildAllRooms(f, r)
            } else {
                template.selectedRooms
            }
        // 起始值不在参数区暴露（改起点点主页表头徽标）：保留模板当前 start/nextSeq
        val newSeq = paramsState.buildSeqConfig().copy(start = template.seq.start)
        val candidate = template.copy(
            name = name.trim().ifBlank { "未命名模板" },
            maxFloor = f,
            roomCountPerFloor = r,
            markMode = paramsState.markMode,
            seq = newSeq,
            nextSeq = template.nextSeq,
            lastSelectedFloor = template.lastSelectedFloor.coerceIn(1, f),
            selectedRooms = selectedRooms
        )
        return if (candidate == template) null else candidate
    }

    LaunchedEffect(
        name, paramsState.markMode,
        paramsState.seqPrefix, paramsState.seqSuffix,
        paramsState.seqChineseUpper, paramsState.seqDescending,
        paramsState.seqPad, paramsState.seqPadWidth,
        paramsState.floorText, paramsState.roomCountText
    ) {
        if (buildUpdated() != null) {
            delay(500)
            buildUpdated()?.let(onSave)
        }
    }

    Column(modifier = modifier.fillMaxSize()) {
        // 头部：返回 + 标题 + 设为当前 + 关闭（紧凑单行）
        Row(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack, modifier = Modifier.size(36.dp)) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
            }
            Text("模板编辑", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.weight(1f))
            if (!isActive) {
                TextButton(onClick = onSetActive) { Text("设为当前") }
            } else {
                Text(
                    text = "当前模板",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(horizontal = 6.dp)
                )
            }
            IconButton(onClick = onClose, modifier = Modifier.size(36.dp)) {
                Icon(Icons.Default.Close, contentDescription = "关闭")
            }
        }

        // 关键：用 LazyColumn 承载所有内容，避免 verticalScroll + LazyColumn 嵌套崩溃
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            contentPadding = PaddingValues(top = 4.dp, bottom = 12.dp)
        ) {
            item {
                LabeledCompactField(
                    label = "模板名称",
                    value = name,
                    onValueChange = { name = it },
                    modifier = Modifier.fillMaxWidth()
                )
            }

            // 参数区：与主页"新建模板"完全同一套界面与逻辑；
            // 标签管理纳入参数设置（viewModel 持久化版，与新建时的位置/交互一致）
            item {
                NewTemplateFields(
                    state = paramsState,
                    tagsSlot = {
                        TemplateTagsEditor(
                            tags = template.tags,
                            tagColors = template.tagColors,
                            onAdd = { viewModel.addTemplateTag(it) },
                            onUpdate = { old, new, colorIdx ->
                                viewModel.updateTemplateTag(old, new, colorIdx)
                            },
                            onRemove = { viewModel.removeTemplateTag(it) }
                        )
                    }
                )
            }

            // 房间号模式：已选房间入口（精细勾选房间）
            if (paramsState.markMode == MarkMode.ROOM) {
                item {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            "已选房间：${template.selectedRooms.size}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        TextButton(onClick = onEditRooms) { Text("编辑房间号") }
                    }
                }
            }

            // 底部操作：清空（低频功能：低调底色+红字，不做整块红底，带二次确认）。
            // 保存已改为修改后自动保存
            item {
                var showClearConfirm by remember { mutableStateOf(false) }
                val clearAll = viewModel.scanSelectedFloor == 0
                val darkTheme = MaterialTheme.colorScheme.surface.luminance() < 0.5f
                Button(
                    onClick = { showClearConfirm = true },
                    colors = ButtonDefaults.buttonColors(
                        // 常规按钮同族的中性底：日间浅灰蓝 / 夜间深灰，仅文字标红
                        containerColor = if (darkTheme) Color(0xFF3A424C) else Color(0xFFDDE5ED),
                        contentColor = MaterialTheme.colorScheme.error
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(40.dp),
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)
                ) {
                    Text(
                        if (clearAll) "清空全部数据" else "清空当前楼层",
                        maxLines = 1
                    )
                }

                if (showClearConfirm) {
                    AlertDialog(
                        onDismissRequest = { showClearConfirm = false },
                        title = { Text(if (clearAll) "清空全部数据" else "清空当前楼层") },
                        text = {
                            Text(
                                if (clearAll) {
                                    "确认清空该模板的全部扫码数据？此操作不可撤回。"
                                } else {
                                    "确认清空该模板 ${viewModel.scanSelectedFloor} 层的扫码数据？此操作不可撤回。"
                                }
                            )
                        },
                        confirmButton = {
                            TextButton(
                                onClick = {
                                    showClearConfirm = false
                                    onClearScans()
                                }
                            ) { Text("清空", color = MaterialTheme.colorScheme.error) }
                        },
                        dismissButton = {
                            TextButton(onClick = { showClearConfirm = false }) { Text("取消") }
                        }
                    )
                }
            }

            // 上传模板数据按钮（扫码明细统一在主界面历史列表查看/编辑，编辑器不再重复展示）
            if (template.scans.isNotEmpty()) {
                item {
                    val context = LocalContext.current
                    val showToast = { msg: String ->
                        android.widget.Toast.makeText(context, msg, android.widget.Toast.LENGTH_SHORT).show()
                    }
                    Button(
                        onClick = { onUploadTemplateData(template, showToast) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(40.dp),
                        enabled = viewModel.uploadEnabled && viewModel.serverUrl.isNotEmpty(),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp)
                    ) {
                        Text(
                            if (viewModel.uploadEnabled && viewModel.serverUrl.isNotEmpty()) {
                                "上传模板数据到电脑（${template.scans.size} 条）"
                            } else {
                                "请先连接电脑"
                            },
                            maxLines = 1
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun RoomPickerPage(
    templateId: String,
    maxFloor: Int,
    roomCountPerFloor: Int,
    initialSelected: List<String>,
    onBack: () -> Unit,
    onApply: (List<String>) -> Unit,
    modifier: Modifier = Modifier
) {
    fun roomSuffix(r: Int): String = if (r in 1..9) "0$r" else "$r"

    var selectedFloor by rememberSaveable(templateId) { mutableStateOf(1) }
    if (selectedFloor > maxFloor) selectedFloor = maxFloor.coerceAtLeast(1)

    // 生成所有房间号（所有楼层）
    // 规则：1~9 -> 01~09，所以 1 层为 101~109
    val allRooms = remember(maxFloor, roomCountPerFloor) {
        val mf = maxFloor.coerceAtLeast(1)
        val rc = roomCountPerFloor.coerceIn(1, 999)
        (1..mf).flatMap { f ->
            (1..rc).map { r -> "$f${roomSuffix(r)}" }
        }
    }

    // 用 rememberSaveable 保存“List<String>”（可保存），避免 Set 的保存/委托问题
    val selectedRoomsState = rememberSaveable(
        templateId,
        maxFloor,
        roomCountPerFloor,
        initialSelected
    ) {
        mutableStateOf(
            if (initialSelected.isEmpty()) allRooms else initialSelected
        )
    }

    // 当前选中集合（用于 contains / 计数）
    val selectedSet = remember(selectedRoomsState.value) { selectedRoomsState.value.toSet() }

    // 当楼层/房间数量变化时，剔除无效房间；如果是默认全选场景且被清空，则恢复全选
    LaunchedEffect(allRooms) {
        val filtered = selectedRoomsState.value.filter { it in allRooms }
        selectedRoomsState.value = if (initialSelected.isEmpty() && filtered.isEmpty()) {
            allRooms
        } else {
            filtered
        }
    }

    val floors = (1..maxFloor.coerceAtLeast(1)).toList()
    val roomsOfFloor = remember(selectedFloor, roomCountPerFloor) {
        (1..roomCountPerFloor.coerceIn(1, 999)).map { r -> "$selectedFloor${roomSuffix(r)}" }
    }
    val selectedCount = roomsOfFloor.count { it in selectedSet }

    Column(
        modifier = modifier.padding(horizontal = 12.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack, modifier = Modifier.size(36.dp)) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
            }
            Text("房间号编辑", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.weight(1f))
            TextButton(onClick = { onApply(selectedSet.toList()) }) { Text("应用") }
        }

        LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            items(floors) { f ->
                FilterChip(
                    selected = f == selectedFloor,
                    onClick = { selectedFloor = f },
                    label = { Text("${f}层") }
                )
            }
        }

        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                "本层房间：${roomsOfFloor.size} · 已选：$selectedCount",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                TextButton(
                    onClick = {
                        selectedRoomsState.value = (selectedSet + roomsOfFloor).toList()
                    },
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp)
                ) { Text("全选本层") }
                TextButton(
                    onClick = {
                        selectedRoomsState.value = (selectedSet - roomsOfFloor.toSet()).toList()
                    },
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp)
                ) { Text("取消本层") }
            }
        }

        LazyVerticalGrid(
            columns = GridCells.Adaptive(minSize = 76.dp),
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            contentPadding = PaddingValues(bottom = 8.dp)
        ) {
            gridItems(roomsOfFloor, key = { it }) { code ->
                val selected = code in selectedSet
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(40.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = if (selected) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.surface
                        }
                    ),
                    elevation = CardDefaults.cardElevation(
                        defaultElevation = 0.dp,
                        pressedElevation = 0.dp
                    ),
                    border = androidx.compose.foundation.BorderStroke(
                        width = 1.dp,
                        color = if (selected) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.outlineVariant
                        }
                    ),
                    onClick = {
                        selectedRoomsState.value = if (selected) {
                            selectedRoomsState.value.filterNot { it == code }
                        } else {
                            selectedRoomsState.value + code
                        }
                    }
                ) {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text(
                            code,
                            style = MaterialTheme.typography.bodyMedium,
                            color = if (selected) {
                                MaterialTheme.colorScheme.onPrimary
                            } else {
                                MaterialTheme.colorScheme.onSurface
                            }
                        )
                    }
                }
            }
        }
    }
}
