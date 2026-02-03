package com.example.catscandemo.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items as gridItems
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.CheckBox
import androidx.compose.material.icons.filled.CheckBoxOutlineBlank
import androidx.compose.material.icons.filled.SelectAll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.example.catscandemo.presentation.viewmodel.MainViewModel
import com.example.catscandemo.domain.model.TemplateModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import android.content.ContentValues
import android.os.Environment
import android.provider.MediaStore
import androidx.compose.ui.platform.LocalContext
import org.json.JSONArray
import org.json.JSONObject
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
    val activeTemplate = templates.firstOrNull { it.id == activeId }

    when (page) {
        DrawerPage.MANAGER -> {
            TemplateManagerPage(
                templates = templates,
                activeId = activeId,
                onSelect = { id -> viewModel.setActiveTemplate(id) },
                onAdd = { name -> viewModel.addTemplate(name) },
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
                onDeleteScan = { ts -> viewModel.deleteTemplateScan(t.id, ts) },
                onClearScans = { viewModel.clearTemplateScans(t.id) },
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TemplateManagerPage(
    templates: List<TemplateModel>,
    activeId: String?,
    onSelect: (String) -> Unit,
    onAdd: (String) -> Unit,
    onDelete: (String) -> Unit,
    onOpen: (String) -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier
) {
    var showAdd by remember { mutableStateOf(false) }
    var showDeleteId by remember { mutableStateOf<String?>(null) }
    var newName by remember { mutableStateOf("") }
    var isBatchMode by remember { mutableStateOf(false) }
    var selectedTemplateIds by remember { mutableStateOf<Set<String>>(emptySet()) }
    val context = LocalContext.current

    fun saveTextToDownloads(fileName: String, mime: String, content: String): Boolean {
        return try {
            val resolver = context.contentResolver
            val values = ContentValues().apply {
                put(MediaStore.MediaColumns.DISPLAY_NAME, fileName)
                put(MediaStore.MediaColumns.MIME_TYPE, mime)
                put(
                    MediaStore.MediaColumns.RELATIVE_PATH,
                    Environment.DIRECTORY_DOWNLOADS + "/CatScan"
                )
            }
            val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values) ?: return false
            resolver.openOutputStream(uri)?.use { os ->
                os.write(content.toByteArray(Charsets.UTF_8))
            } ?: return false
            true
        } catch (_: Exception) {
            false
        }
    }

    fun floorFromRoomCode(code: String): String {
        // 约定：最后两位为房间序号（01~99），前面为楼层号
        return if (code.length >= 3) {
            code.dropLast(2).toIntOrNull()?.let { "${it}层" } ?: ""
        } else ""
    }

    fun buildExportTxt(selectedTemplates: List<TemplateModel> = templates): String {
        val sdf = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())

        val header = "序号\t模板名称\t校区名称\t楼栋\t楼层\t房间号\t操作人\t时间\t扫码内容"
        val lines = ArrayList<String>()
        lines.add(header)

        var seq = 1

        selectedTemplates.forEach { t ->
            // 按 scan 明细导出
            if (t.scans.isEmpty()) {
                // 如果你希望“没有扫码也导出一行”，取消注释下面这行即可：
                lines.add("${seq++}\t${t.name}\t${t.campus}\t${t.building}\t\t\t${t.operator}\t\t")
            } else {
                t.scans.forEach { s ->
                    val time = sdf.format(Date(s.timestamp))
                    lines.add(
                        "${seq++}\t${t.name}\t${t.campus}\t${t.building}\t${s.floor}\t${s.room}\t${s.operator}\t$time\t${s.text}"
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
            obj.put("operator", t.operator)
            obj.put("campus", t.campus)
            obj.put("building", t.building)
            obj.put("maxFloor", t.maxFloor)
            obj.put("roomCountPerFloor", t.roomCountPerFloor)

            val rooms = JSONArray()
            t.selectedRooms.forEach { rooms.put(it) }
            obj.put("selectedRooms", rooms)

            val scans = JSONArray()
            t.scans.forEach { s ->
                val so = JSONObject()
                so.put("text", s.text)
                so.put("timestamp", s.timestamp)
                so.put("operator", s.operator)
                so.put("campus", s.campus)
                so.put("building", s.building)
                so.put("floor", s.floor)
                so.put("room", s.room)
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
    fun safeFileNamePart(s: String): String {
        return s.trim()
            .ifBlank { "未命名" }
            .replace(Regex("""[\\/:*?"<>|]"""), "_")
            .replace(Regex("""\s+"""), "_")
    }

    fun exportBaseName(selectedCount: Int = 0): String {
        val sdf = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault())
        val time = sdf.format(Date())
        return if (selectedCount > 0 && selectedCount < templates.size) {
            "批量导出_${selectedCount}个模板_$time"
        } else {
            val t = templates.firstOrNull { it.id == activeId }
            val name = safeFileNamePart(t?.name ?: "模板")
            val campus = safeFileNamePart(t?.campus ?: "校区")
            "${name}_${campus}_$time"
        }
    }

    val selectedTemplates = templates.filter { it.id in selectedTemplateIds }
    val allSelected = templates.isNotEmpty() && selectedTemplateIds.size == templates.size

    Column(modifier = modifier.padding(16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("模板管理", style = MaterialTheme.typography.titleLarge)
            Spacer(Modifier.weight(1f))
            if (isBatchMode) {
                Text(
                    "已选 ${selectedTemplateIds.size}/${templates.size}",
                    style = MaterialTheme.typography.bodyMedium
                )
            }
            if (!isBatchMode) {
                IconButton(onClick = { showAdd = true }) {
                    Icon(Icons.Default.Add, contentDescription = "新增模板")
                }
            }
            IconButton(
                onClick = {
                    isBatchMode = !isBatchMode
                    if (!isBatchMode) {
                        selectedTemplateIds = emptySet()
                    }
                }
            ) {
                Icon(
                    imageVector = if (isBatchMode) Icons.Default.Close else Icons.Default.SelectAll,
                    contentDescription = if (isBatchMode) "取消批量选择" else "批量选择"
                )
            }
            IconButton(onClick = onClose) {
                Icon(Icons.Default.Close, contentDescription = "关闭")
            }
        }

        Spacer(Modifier.height(12.dp))

// ✅ 中间内容占满剩余空间
        Box(modifier = Modifier.weight(1f, fill = true)) {
            if (templates.isEmpty()) {
                Text("暂无模板，点击右上角 + 新增。")
            } else {
                LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    items(templates, key = { it.id }) { t ->
                        val isActive = (t.id == activeId)
                        val isChecked = t.id in selectedTemplateIds
                        Card(
                            onClick = {
                                if (isBatchMode) {
                                    selectedTemplateIds = if (isChecked) {
                                        selectedTemplateIds - t.id
                                    } else {
                                        selectedTemplateIds + t.id
                                    }
                                } else {
                                    onSelect(t.id)
                                }
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                if (isBatchMode) {
                                    IconButton(
                                        onClick = {
                                            selectedTemplateIds = if (isChecked) {
                                                selectedTemplateIds - t.id
                                            } else {
                                                selectedTemplateIds + t.id
                                            }
                                        }
                                    ) {
                                        Icon(
                                            imageVector = if (isChecked) Icons.Default.CheckBox else Icons.Default.CheckBoxOutlineBlank,
                                            contentDescription = if (isChecked) "取消选择" else "选择"
                                        )
                                    }
                                }
                                Column(Modifier.weight(1f)) {
                                    Text(t.name, style = MaterialTheme.typography.titleMedium)
                                    Text(
                                        "${t.campus} / ${t.building}  | 楼层:${t.maxFloor} 房间/层:${t.roomCountPerFloor}  | 已选房间:${t.selectedRooms.size}  | 扫码:${t.scans.size}",
                                        style = MaterialTheme.typography.bodySmall
                                    )
                                }
                                if (!isBatchMode) {
                                    if (isActive) {
                                        AssistChip(onClick = {}, label = { Text("已选") })
                                    }
                                    IconButton(onClick = { onOpen(t.id) }) {
                                        Icon(Icons.Default.Edit, contentDescription = "查看/编辑")
                                    }
                                    IconButton(onClick = { showDeleteId = t.id }) {
                                        Icon(Icons.Default.Delete, contentDescription = "删除")
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        Spacer(Modifier.height(12.dp))

        // 底部操作栏（始终显示）
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (isBatchMode) {
                // 批量选择模式：导出TXT - 全选 - 导出JSON
                Button(
                    onClick = {
                        if (selectedTemplateIds.isEmpty()) {
                            toast("请至少选择一个模板")
                            return@Button
                        }
                        val fileName = "${exportBaseName(selectedTemplateIds.size)}.txt"
                        val ok = saveTextToDownloads(
                            fileName = fileName,
                            mime = "text/plain",
                            content = buildExportTxt(selectedTemplates)
                        )
                        toast(if (ok) "已导出 ${selectedTemplateIds.size} 个模板：下载/CatScan/$fileName" else "导出TXT失败")
                        isBatchMode = false
                        selectedTemplateIds = emptySet()
                    },
                    enabled = selectedTemplateIds.isNotEmpty(),
                    modifier = Modifier.weight(1f)
                ) { 
                    Text(
                        text = "导出TXT",
                        style = MaterialTheme.typography.labelSmall
                    )
                }
            } else {
                // 普通模式：导出TXT
                Button(
                    onClick = {
                        val fileName = "${exportBaseName()}.txt"
                        val ok = saveTextToDownloads(
                            fileName = fileName,
                            mime = "text/plain",
                            content = buildExportTxt()
                        )
                        toast(if (ok) "已导出：下载/CatScan/$fileName" else "导出TXT失败")
                    },
                    enabled = templates.isNotEmpty(),
                    modifier = Modifier.weight(1f)
                ) { 
                    Text(
                        text = "导出TXT",
                        style = MaterialTheme.typography.labelSmall
                    )
                }
            }
            
            // 全选按钮（中间，始终显示）
            OutlinedButton(
                onClick = {
                    if (!isBatchMode) {
                        // 如果不在批量模式，先进入批量模式
                        isBatchMode = true
                    }
                    selectedTemplateIds = if (allSelected) {
                        emptySet()
                    } else {
                        templates.map { it.id }.toSet()
                    }
                },
                enabled = templates.isNotEmpty(),
                modifier = Modifier.weight(0.7f),
                colors = ButtonDefaults.outlinedButtonColors(
                    contentColor = if (isBatchMode && allSelected) {
                        MaterialTheme.colorScheme.error
                    } else {
                        MaterialTheme.colorScheme.onSurface
                    }
                ),
                border = BorderStroke(
                    width = 1.dp,
                    color = if (isBatchMode && allSelected) {
                        MaterialTheme.colorScheme.error
                    } else {
                        MaterialTheme.colorScheme.outline
                    }
                )
            ) {
                Text(
                    text = "全选",
                    style = MaterialTheme.typography.labelSmall
                )
            }
            
            if (isBatchMode) {
                // 批量选择模式：导出JSON
                Button(
                    onClick = {
                        if (selectedTemplateIds.isEmpty()) {
                            toast("请至少选择一个模板")
                            return@Button
                        }
                        val fileName = "${exportBaseName(selectedTemplateIds.size)}.json"
                        val ok = saveTextToDownloads(
                            fileName = fileName,
                            mime = "application/json",
                            content = buildExportJson(selectedTemplates)
                        )
                        toast(if (ok) "已导出 ${selectedTemplateIds.size} 个模板：下载/CatScan/$fileName" else "导出JSON失败")
                        isBatchMode = false
                        selectedTemplateIds = emptySet()
                    },
                    enabled = selectedTemplateIds.isNotEmpty(),
                    modifier = Modifier.weight(1f)
                ) { 
                    Text(
                        text = "导出JSON",
                        style = MaterialTheme.typography.labelSmall
                    )
                }
            } else {
                // 普通模式：导出JSON
                Button(
                    onClick = {
                        val fileName = "${exportBaseName()}.json"
                        val ok = saveTextToDownloads(
                            fileName = fileName,
                            mime = "application/json",
                            content = buildExportJson()
                        )
                        toast(if (ok) "已导出：下载/CatScan/$fileName" else "导出JSON失败")
                    },
                    enabled = templates.isNotEmpty(),
                    modifier = Modifier.weight(1f)
                ) { 
                    Text(
                        text = "导出JSON",
                        style = MaterialTheme.typography.labelSmall
                    )
                }
            }
        }

    }

    if (showAdd) {
        AlertDialog(
            onDismissRequest = { showAdd = false },
            confirmButton = {
                TextButton(
                    onClick = {
                        onAdd(newName.trim().ifBlank { "未命名模板" })
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
                OutlinedTextField(
                    value = newName,
                    onValueChange = { newName = it },
                    label = { Text("模板名称") },
                    singleLine = true
                )
            }
        )
    }

    showDeleteId?.let { id ->
        AlertDialog(
            onDismissRequest = { showDeleteId = null },
            confirmButton = {
                TextButton(
                    onClick = {
                        onDelete(id)
                        showDeleteId = null
                    }
                ) { Text("删除") }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteId = null }) { Text("取消") }
            },
            title = { Text("删除模板") },
            text = { Text("确认删除该模板？该模板的离线扫码数据也会一并删除。") }
        )
    }

}
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TemplateEditorSheet(
    viewModel: MainViewModel,
    template: TemplateModel,
    isActive: Boolean,
    onSetActive: () -> Unit,
    onSave: (TemplateModel) -> Unit,
    onDeleteScan: (String) -> Unit,
    onClearScans: () -> Unit,
    onUploadTemplateData: (TemplateModel, (String) -> Unit) -> Unit,
    onBack: () -> Unit,
    onEditRooms: () -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier
) {
    var name by rememberSaveable(template.id) { mutableStateOf(template.name) }
    var op by rememberSaveable(template.id) { mutableStateOf(template.operator) }
    var campus by rememberSaveable(template.id) { mutableStateOf(template.campus) }
    var building by rememberSaveable(template.id) { mutableStateOf(template.building) }
    var floorText by rememberSaveable(template.id) { mutableStateOf(template.maxFloor.toString()) }
    var roomText by rememberSaveable(template.id) { mutableStateOf(template.roomCountPerFloor.toString()) }
    val scope = rememberCoroutineScope()
    var autoSaveJob by remember { mutableStateOf<Job?>(null) }

    fun roomSuffix(r: Int): String = if (r in 1..9) "0$r" else "$r"

    fun buildAllRooms(maxFloor: Int, roomCount: Int): List<String> {
        val mf = maxFloor.coerceAtLeast(1)
        val rc = roomCount.coerceAtLeast(1)
        return (1..mf).flatMap { f ->
            (1..rc).map { r -> "$f${roomSuffix(r)}" }
        }
    }

    /**
     * 仅在“楼层数量/房间数量”输入完成后自动保存：
     * - 自动计算全量房间号
     * - selectedRooms：保留有效项；若为空则默认全选
     */
    fun scheduleAutoSaveCounts() {
        autoSaveJob?.cancel()
        autoSaveJob = scope.launch {
            delay(450) // 认为用户停止输入
            val f = floorText.toIntOrNull()?.takeIf { it > 0 } ?: return@launch
            val r = roomText.toIntOrNull()?.takeIf { it > 0 } ?: return@launch

            val allRooms = buildAllRooms(f, r)
            val newSelected = allRooms

            onSave(
                template.copy(
                    maxFloor = f,
                    roomCountPerFloor = r,
                    selectedRooms = newSelected
                )
            )

        }
    }

    fun parsePositiveInt(text: String, default: Int): Int =
        text.toIntOrNull()?.takeIf { it > 0 } ?: default

    val df = remember { java.text.SimpleDateFormat("MM-dd HH:mm", java.util.Locale.getDefault()) }

    Column(modifier = modifier.fillMaxSize()) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
            }
            Text("模板编辑", style = MaterialTheme.typography.titleLarge)
            Spacer(Modifier.weight(1f))
            if (!isActive) {
                TextButton(onClick = onSetActive) { Text("设为当前") }
            } else {
                AssistChip(onClick = {}, label = { Text("当前模板") })
            }
            IconButton(onClick = onClose) {
                Icon(Icons.Default.Close, contentDescription = "关闭")
            }
        }

        // 关键：用 LazyColumn 承载所有内容，避免 verticalScroll + LazyColumn 嵌套崩溃
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
            contentPadding = PaddingValues(bottom = 16.dp)
        ) {
            item {
                OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("模板名称") }, modifier = Modifier.fillMaxWidth())
            }
            item {
                OutlinedTextField(value = op, onValueChange = { op = it }, label = { Text("操作人") }, modifier = Modifier.fillMaxWidth())
            }
            item {
                OutlinedTextField(value = campus, onValueChange = { campus = it }, label = { Text("校区") }, modifier = Modifier.fillMaxWidth())
            }
            item {
                OutlinedTextField(value = building, onValueChange = { building = it }, label = { Text("楼栋") }, modifier = Modifier.fillMaxWidth())
            }
            item {
                OutlinedTextField(
                    value = floorText,
                    onValueChange = {
                        floorText = it.filter(Char::isDigit)
                        scheduleAutoSaveCounts()
                    },
                    label = { Text("楼层数量（最大楼层数）") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth()
                )
            }
            item {
                OutlinedTextField(
                    value = roomText,
                    onValueChange = {
                        roomText = it.filter(Char::isDigit)
                        scheduleAutoSaveCounts()
                    },
                    label = { Text("房间数量（每层房间数）") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth()
                )
            }

            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("已选房间：${template.selectedRooms.size}")
                    TextButton(onClick = onEditRooms) { Text("编辑房间号") }
                }
            }

            item {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedButton(onClick = onClearScans) { Text("清空扫码数据") }
                    Button(
                        onClick = {
                            val f = parsePositiveInt(floorText, 1).coerceAtLeast(1)
                            val r = parsePositiveInt(roomText, 1).coerceAtLeast(1)
                            onSave(
                                template.copy(
                                    name = name.trim().ifBlank { "未命名模板" },
                                    operator = op,
                                    campus = campus,
                                    building = building,
                                    maxFloor = f,
                                    roomCountPerFloor = r
                                )
                            )
                            // ✅ 保存后自动返回（回到模板管理列表）
                            onBack()
                        }
                    ) { Text("保存模板") }

                }
            }

            item {
                Spacer(Modifier.height(8.dp))
                Text("模板内已扫描数据（离线）", style = MaterialTheme.typography.titleMedium)
            }
            
            // 上传模板数据按钮
            item {
                if (template.scans.isNotEmpty()) {
                    val context = LocalContext.current
                    val showToast = { msg: String ->
                        android.widget.Toast.makeText(context, msg, android.widget.Toast.LENGTH_SHORT).show()
                    }
                    
                    Button(
                        onClick = { onUploadTemplateData(template, showToast) },
                        modifier = Modifier.fillMaxWidth(),
                        enabled = viewModel.uploadEnabled && viewModel.serverUrl.isNotEmpty()
                    ) {
                        Text(
                            if (viewModel.uploadEnabled && viewModel.serverUrl.isNotEmpty()) {
                                "上传模板数据到电脑"
                            } else {
                                "请先连接电脑"
                            }
                        )
                    }
                    Spacer(Modifier.height(16.dp))
                }
            }

            if (template.scans.isEmpty()) {
                item { Text("暂无扫码数据。") }
            } else {
                items(template.scans, key = { it.timestamp }) { s ->
                    Card(Modifier.fillMaxWidth()) {
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .padding(10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(Modifier.weight(1f)) {
                                Text(s.text, style = MaterialTheme.typography.titleSmall)
                                Text(
                                    "${df.format(java.util.Date(s.timestamp))} | ${s.operator} | ${s.campus}/${s.building} | ${s.floor} ${s.room}",
                                    style = MaterialTheme.typography.bodySmall
                                )
                            }
                            IconButton(onClick = { onDeleteScan(s.id) }) {
                                Icon(Icons.Default.Delete, contentDescription = "删除该条")
                            }
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun RoomPickerPage(
    maxFloor: Int,
    roomCountPerFloor: Int,
    initialSelected: List<String>,
    onBack: () -> Unit,
    onApply: (List<String>) -> Unit,
    modifier: Modifier = Modifier
) {
    fun roomSuffix(r: Int): String = if (r in 1..9) "0$r" else "$r"

    var selectedFloor by rememberSaveable { mutableStateOf(1) }
    if (selectedFloor > maxFloor) selectedFloor = maxFloor.coerceAtLeast(1)

    // ✅ 生成所有房间号（所有楼层）
    // 规则：1~9 -> 01~09，所以 1 层为 101~109
    val allRooms = remember(maxFloor, roomCountPerFloor) {
        val mf = maxFloor.coerceAtLeast(1)
        val rc = roomCountPerFloor.coerceAtLeast(1)
        (1..mf).flatMap { f ->
            (1..rc).map { r -> "$f${roomSuffix(r)}" }
        }
    }

    // ✅ 用 rememberSaveable 保存“List<String>”（可保存），避免 Set 的保存/委托问题
    val selectedRoomsState = rememberSaveable {
        mutableStateOf(
            if (initialSelected.isEmpty()) allRooms else initialSelected
        )
    }

    // 当前选中集合（用于 contains / 计数）
    val selectedSet = remember(selectedRoomsState.value) { selectedRoomsState.value.toSet() }

    // ✅ 当楼层/房间数量变化时，剔除无效房间；如果是默认全选场景且被清空，则恢复全选
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
        (1..roomCountPerFloor.coerceAtLeast(1)).map { r -> "$selectedFloor${roomSuffix(r)}" }
    }
    val selectedCount = roomsOfFloor.count { it in selectedSet }
    // ✅ 若楼层/房间数量变化，剔除无效选中；
    // 如果你希望“变化后仍保持全选”，把 intersect 改成 selectedSet = allRooms.toSet()

    Column(modifier = modifier.padding(16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
            }
            Text("房间号编辑", style = MaterialTheme.typography.titleLarge)
            Spacer(Modifier.weight(1f))
            TextButton(onClick = { onApply(selectedSet.toList()) }) { Text("应用") }
        }

        Spacer(Modifier.height(10.dp))

        Text("选择楼层", style = MaterialTheme.typography.titleMedium)
        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            items(floors) { f ->
                FilterChip(
                    selected = f == selectedFloor,
                    onClick = { selectedFloor = f },
                    label = { Text("${f}层") }
                )
            }
        }

        Spacer(Modifier.height(8.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("本层房间：${roomsOfFloor.size}")
            Text("本层已选：$selectedCount")
        }

        Spacer(Modifier.height(8.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            OutlinedButton(
                onClick = {
                    selectedRoomsState.value = (selectedSet + roomsOfFloor).toList()
                },
                modifier = Modifier.weight(1f)
            ) { Text("全选本层") }

            OutlinedButton(
                onClick = {
                    selectedRoomsState.value = (selectedSet - roomsOfFloor.toSet()).toList()
                },
                modifier = Modifier.weight(1f)
            ) { Text("取消本层") }

        }

        Spacer(Modifier.height(10.dp))

        LazyVerticalGrid(
            columns = GridCells.Adaptive(minSize = 86.dp),
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
            contentPadding = PaddingValues(bottom = 8.dp)
        ) {
            gridItems(roomsOfFloor, key = { it }) { code ->
                val selected = code in selectedSet
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(46.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant
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
                            color = if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
}
