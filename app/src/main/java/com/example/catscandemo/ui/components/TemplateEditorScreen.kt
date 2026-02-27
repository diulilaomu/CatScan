package com.example.catscandemo.ui.components

import androidx.compose.foundation.layout.*
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
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.catscandemo.presentation.viewmodel.MainViewModel
import com.example.catscandemo.domain.model.TemplateModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import android.content.ContentValues
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import androidx.compose.ui.platform.LocalContext
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
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
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
            } else {
                val downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
                val targetDir = File(downloadsDir, "CatScan")
                if (!targetDir.exists() && !targetDir.mkdirs()) {
                    return false
                }
                val targetFile = File(targetDir, fileName)
                targetFile.writeText(content, Charsets.UTF_8)
                true
            }
        } catch (_: Exception) {
            false
        }
    }

    fun floorFromRoomCode(code: String): String {
        // 绾﹀畾锛氭渶鍚庝袱浣嶄负鎴块棿搴忓彿锛?1~99锛夛紝鍓嶉潰涓烘ゼ灞傚彿
        return if (code.length >= 3) {
            code.dropLast(2).toIntOrNull()?.let { "${it}灞? } ?: ""
        } else ""
    }

    fun buildExportTxt(selectedTemplates: List<TemplateModel> = templates): String {
        val sdf = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())

        val header = "搴忓彿\t妯℃澘鍚嶇О\t鏍″尯鍚嶇О\t妤兼爧\t妤煎眰\t鎴块棿鍙穃t鎿嶄綔浜篭t鏃堕棿\t鎵爜鍐呭"
        val lines = ArrayList<String>()
        lines.add(header)

        var seq = 1

        selectedTemplates.forEach { t ->
            // 鎸?scan 鏄庣粏瀵煎嚭
            if (t.scans.isEmpty()) {
                // 濡傛灉浣犲笇鏈涒€滄病鏈夋壂鐮佷篃瀵煎嚭涓€琛屸€濓紝鍙栨秷娉ㄩ噴涓嬮潰杩欒鍗冲彲锛?
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

        return root.toString(2) // 甯︾缉杩涳紝渚夸簬闃呰
    }

    fun toast(msg: String) {
        android.widget.Toast.makeText(context, msg, android.widget.Toast.LENGTH_SHORT).show()
    }
    fun safeFileNamePart(s: String): String {
        return s.trim()
            .ifBlank { "鏈懡鍚? }
            .replace(Regex("""[\\/:*?"<>|]"""), "_")
            .replace(Regex("""\s+"""), "_")
    }

    fun exportBaseName(selectedCount: Int = 0): String {
        val sdf = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault())
        val time = sdf.format(Date())
        return if (selectedCount > 0 && selectedCount < templates.size) {
            "鎵归噺瀵煎嚭_${selectedCount}涓ā鏉縚$time"
        } else {
            val t = templates.firstOrNull { it.id == activeId }
            val name = safeFileNamePart(t?.name ?: "妯℃澘")
            val campus = safeFileNamePart(t?.campus ?: "鏍″尯")
            "${name}_${campus}_$time"
        }
    }

    val selectedTemplates = templates.filter { it.id in selectedTemplateIds }
    val allSelected = templates.isNotEmpty() && selectedTemplateIds.size == templates.size

    Column(modifier = modifier.padding(16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("妯℃澘绠＄悊", style = MaterialTheme.typography.titleLarge)
            Spacer(Modifier.weight(1f))
            if (isBatchMode) {
                Text(
                    "宸查€?${selectedTemplateIds.size}/${templates.size}",
                    style = MaterialTheme.typography.bodyMedium
                )
                Spacer(Modifier.width(8.dp))
                TextButton(
                    onClick = {
                        isBatchMode = false
                        selectedTemplateIds = emptySet()
                    }
                ) {
                    Text("瀹屾垚")
                }
            }
            if (!isBatchMode) {
                IconButton(onClick = { showAdd = true }) {
                    Icon(Icons.Default.Add, contentDescription = "鏂板妯℃澘")
                }
            }
            IconButton(onClick = onClose) {
                Icon(Icons.Default.Close, contentDescription = "鍏抽棴")
            }
        }

        Spacer(Modifier.height(12.dp))

// 鉁?涓棿鍐呭鍗犳弧鍓╀綑绌洪棿
        Box(modifier = Modifier.weight(1f, fill = true)) {
            if (templates.isEmpty()) {
                Text("鏆傛棤妯℃澘锛岀偣鍑诲彸涓婅 + 鏂板銆?)
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
                                            contentDescription = if (isChecked) "鍙栨秷閫夋嫨" else "閫夋嫨"
                                        )
                                    }
                                }
                                Column(Modifier.weight(1f)) {
                                    Text(t.name, style = MaterialTheme.typography.titleMedium)
                                    Text(
                                        "${t.campus} / ${t.building}  | 妤煎眰:${t.maxFloor} 鎴块棿/灞?${t.roomCountPerFloor}  | 宸查€夋埧闂?${t.selectedRooms.size}  | 鎵爜:${t.scans.size}",
                                        style = MaterialTheme.typography.bodySmall
                                    )
                                }
                                if (!isBatchMode) {
                                    if (isActive) {
                                        AssistChip(onClick = {}, label = { Text("宸查€?) })
                                    }
                                    IconButton(onClick = { onOpen(t.id) }) {
                                        Icon(Icons.Default.Edit, contentDescription = "鏌ョ湅/缂栬緫")
                                    }
                                    IconButton(onClick = { showDeleteId = t.id }) {
                                        Icon(Icons.Default.Delete, contentDescription = "鍒犻櫎")
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        Spacer(Modifier.height(12.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            val exportButtonTextStyle = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp)
            val exportButtonContentPadding = PaddingValues(horizontal = 8.dp, vertical = 6.dp)
            if (isBatchMode) {
                OutlinedButton(
                    onClick = {
                        selectedTemplateIds = if (allSelected) {
                            emptySet()
                        } else {
                            templates.map { it.id }.toSet()
                        }
                    },
                    enabled = templates.isNotEmpty(),
                    modifier = Modifier.weight(1f),
                    contentPadding = exportButtonContentPadding,
                ) {
                    Text(
                        text = if (allSelected) "鍙栨秷鍏ㄩ€? else "鍏ㄩ€?,
                        style = exportButtonTextStyle,
                        maxLines = 1,
                        softWrap = false
                    )
                }

                Button(
                    onClick = {
                        if (selectedTemplateIds.isEmpty()) {
                            toast("璇疯嚦灏戦€夋嫨涓€涓ā鏉?)
                            return@Button
                        }
                        val fileName = "${exportBaseName(selectedTemplateIds.size)}.txt"
                        val ok = saveTextToDownloads(
                            fileName = fileName,
                            mime = "text/plain",
                            content = buildExportTxt(selectedTemplates)
                        )
                        toast(if (ok) "宸插鍑?${selectedTemplateIds.size} 涓ā鏉匡細涓嬭浇/CatScan/$fileName" else "瀵煎嚭TXT澶辫触")
                        isBatchMode = false
                        selectedTemplateIds = emptySet()
                    },
                    enabled = selectedTemplateIds.isNotEmpty(),
                    modifier = Modifier.weight(1f),
                    contentPadding = exportButtonContentPadding,
                ) {
                    Text(
                        text = "瀵煎嚭TXT",
                        style = exportButtonTextStyle,
                        maxLines = 1,
                        softWrap = false
                    )
                }

                Button(
                    onClick = {
                        if (selectedTemplateIds.isEmpty()) {
                            toast("璇疯嚦灏戦€夋嫨涓€涓ā鏉?)
                            return@Button
                        }
                        val fileName = "${exportBaseName(selectedTemplateIds.size)}.json"
                        val ok = saveTextToDownloads(
                            fileName = fileName,
                            mime = "application/json",
                            content = buildExportJson(selectedTemplates)
                        )
                        toast(if (ok) "宸插鍑?${selectedTemplateIds.size} 涓ā鏉匡細涓嬭浇/CatScan/$fileName" else "瀵煎嚭JSON澶辫触")
                        isBatchMode = false
                        selectedTemplateIds = emptySet()
                    },
                    enabled = selectedTemplateIds.isNotEmpty(),
                    modifier = Modifier.weight(1f),
                    contentPadding = exportButtonContentPadding,
                ) {
                    Text(
                        text = "瀵煎嚭JSON",
                        style = exportButtonTextStyle,
                        maxLines = 1,
                        softWrap = false
                    )
                }

            } else {
                Button(
                    onClick = {
                        val fileName = "${exportBaseName()}.txt"
                        val ok = saveTextToDownloads(
                            fileName = fileName,
                            mime = "text/plain",
                            content = buildExportTxt()
                        )
                        toast(if (ok) "宸插鍑猴細涓嬭浇/CatScan/$fileName" else "瀵煎嚭TXT澶辫触")
                    },
                    enabled = templates.isNotEmpty(),
                    modifier = Modifier.weight(1f),
                    contentPadding = exportButtonContentPadding,
                ) {
                    Text(
                        text = "瀵煎嚭TXT",
                        style = exportButtonTextStyle,
                        maxLines = 1,
                        softWrap = false
                    )
                }

                Button(
                    onClick = {
                        val fileName = "${exportBaseName()}.json"
                        val ok = saveTextToDownloads(
                            fileName = fileName,
                            mime = "application/json",
                            content = buildExportJson()
                        )
                        toast(if (ok) "宸插鍑猴細涓嬭浇/CatScan/$fileName" else "瀵煎嚭JSON澶辫触")
                    },
                    enabled = templates.isNotEmpty(),
                    modifier = Modifier.weight(1f),
                    contentPadding = exportButtonContentPadding,
                ) {
                    Text(
                        text = "瀵煎嚭JSON",
                        style = exportButtonTextStyle,
                        maxLines = 1,
                        softWrap = false
                    )
                }

                OutlinedButton(
                    onClick = {
                        isBatchMode = true
                        selectedTemplateIds = emptySet()
                    },
                    enabled = templates.isNotEmpty(),
                    modifier = Modifier.weight(1f),
                    contentPadding = exportButtonContentPadding,
                ) {
                    Text(
                        text = "鎵归噺瀵煎嚭",
                        style = exportButtonTextStyle,
                        maxLines = 1,
                        softWrap = false
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
                        onAdd(newName.trim().ifBlank { "鏈懡鍚嶆ā鏉? })
                        newName = ""
                        showAdd = false
                    }
                ) { Text("鍒涘缓") }
            },
            dismissButton = {
                TextButton(onClick = { showAdd = false }) { Text("鍙栨秷") }
            },
            title = { Text("鏂板妯℃澘") },
            text = {
                OutlinedTextField(
                    value = newName,
                    onValueChange = { newName = it },
                    label = { Text("妯℃澘鍚嶇О") },
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
                ) { Text("鍒犻櫎") }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteId = null }) { Text("鍙栨秷") }
            },
            title = { Text("鍒犻櫎妯℃澘") },
            text = { Text("纭鍒犻櫎璇ユā鏉匡紵璇ユā鏉跨殑绂荤嚎鎵爜鏁版嵁涔熶細涓€骞跺垹闄ゃ€?) }
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
     * 浠呭湪鈥滄ゼ灞傛暟閲?鎴块棿鏁伴噺鈥濊緭鍏ュ畬鎴愬悗鑷姩淇濆瓨锛?
     * - 鑷姩璁＄畻鍏ㄩ噺鎴块棿鍙?
     * - selectedRooms锛氫繚鐣欐湁鏁堥」锛涜嫢涓虹┖鍒欓粯璁ゅ叏閫?
     */
    fun scheduleAutoSaveCounts() {
        autoSaveJob?.cancel()
        autoSaveJob = scope.launch {
            delay(450) // 璁や负鐢ㄦ埛鍋滄杈撳叆
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
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "杩斿洖")
            }
            Text("妯℃澘缂栬緫", style = MaterialTheme.typography.titleLarge)
            Spacer(Modifier.weight(1f))
            if (!isActive) {
                TextButton(onClick = onSetActive) { Text("璁句负褰撳墠") }
            } else {
                AssistChip(onClick = {}, label = { Text("褰撳墠妯℃澘") })
            }
            IconButton(onClick = onClose) {
                Icon(Icons.Default.Close, contentDescription = "鍏抽棴")
            }
        }

        // 鍏抽敭锛氱敤 LazyColumn 鎵胯浇鎵€鏈夊唴瀹癸紝閬垮厤 verticalScroll + LazyColumn 宓屽宕╂簝
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
            contentPadding = PaddingValues(bottom = 16.dp)
        ) {
            item {
                OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("妯℃澘鍚嶇О") }, modifier = Modifier.fillMaxWidth())
            }
            item {
                OutlinedTextField(value = op, onValueChange = { op = it }, label = { Text("鎿嶄綔浜?) }, modifier = Modifier.fillMaxWidth())
            }
            item {
                OutlinedTextField(value = campus, onValueChange = { campus = it }, label = { Text("鏍″尯") }, modifier = Modifier.fillMaxWidth())
            }
            item {
                OutlinedTextField(value = building, onValueChange = { building = it }, label = { Text("妤兼爧") }, modifier = Modifier.fillMaxWidth())
            }
            item {
                OutlinedTextField(
                    value = floorText,
                    onValueChange = {
                        floorText = it.filter(Char::isDigit)
                        scheduleAutoSaveCounts()
                    },
                    label = { Text("妤煎眰鏁伴噺锛堟渶澶фゼ灞傛暟锛?) },
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
                    label = { Text("鎴块棿鏁伴噺锛堟瘡灞傛埧闂存暟锛?) },
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
                    Text("宸查€夋埧闂达細${template.selectedRooms.size}")
                    TextButton(onClick = onEditRooms) { Text("缂栬緫鎴块棿鍙?) }
                }
            }

            item {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedButton(onClick = onClearScans) { Text("娓呯┖鎵爜鏁版嵁") }
                    Button(
                        onClick = {
                            val f = parsePositiveInt(floorText, 1).coerceAtLeast(1)
                            val r = parsePositiveInt(roomText, 1).coerceAtLeast(1)
                            onSave(
                                template.copy(
                                    name = name.trim().ifBlank { "鏈懡鍚嶆ā鏉? },
                                    operator = op,
                                    campus = campus,
                                    building = building,
                                    maxFloor = f,
                                    roomCountPerFloor = r
                                )
                            )
                            // 鉁?淇濆瓨鍚庤嚜鍔ㄨ繑鍥烇紙鍥炲埌妯℃澘绠＄悊鍒楄〃锛?
                            onBack()
                        }
                    ) { Text("淇濆瓨妯℃澘") }

                }
            }

            item {
                Spacer(Modifier.height(8.dp))
                Text("妯℃澘鍐呭凡鎵弿鏁版嵁锛堢绾匡級", style = MaterialTheme.typography.titleMedium)
            }
            
            // 涓婁紶妯℃澘鏁版嵁鎸夐挳
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
                                "涓婁紶妯℃澘鏁版嵁鍒扮數鑴?
                            } else {
                                "璇峰厛杩炴帴鐢佃剳"
                            }
                        )
                    }
                    Spacer(Modifier.height(16.dp))
                }
            }

            if (template.scans.isEmpty()) {
                item { Text("鏆傛棤鎵爜鏁版嵁銆?) }
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
                                Icon(Icons.Default.Delete, contentDescription = "鍒犻櫎璇ユ潯")
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

    // 鉁?鐢熸垚鎵€鏈夋埧闂村彿锛堟墍鏈夋ゼ灞傦級
    // 瑙勫垯锛?~9 -> 01~09锛屾墍浠?1 灞備负 101~109
    val allRooms = remember(maxFloor, roomCountPerFloor) {
        val mf = maxFloor.coerceAtLeast(1)
        val rc = roomCountPerFloor.coerceAtLeast(1)
        (1..mf).flatMap { f ->
            (1..rc).map { r -> "$f${roomSuffix(r)}" }
        }
    }

    // 鉁?鐢?rememberSaveable 淇濆瓨鈥淟ist<String>鈥濓紙鍙繚瀛橈級锛岄伩鍏?Set 鐨勪繚瀛?濮旀墭闂
    val selectedRoomsState = rememberSaveable {
        mutableStateOf(
            if (initialSelected.isEmpty()) allRooms else initialSelected
        )
    }

    // 褰撳墠閫変腑闆嗗悎锛堢敤浜?contains / 璁℃暟锛?
    val selectedSet = remember(selectedRoomsState.value) { selectedRoomsState.value.toSet() }

    // 鉁?褰撴ゼ灞?鎴块棿鏁伴噺鍙樺寲鏃讹紝鍓旈櫎鏃犳晥鎴块棿锛涘鏋滄槸榛樿鍏ㄩ€夊満鏅笖琚竻绌猴紝鍒欐仮澶嶅叏閫?
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
    // 鉁?鑻ユゼ灞?鎴块棿鏁伴噺鍙樺寲锛屽墧闄ゆ棤鏁堥€変腑锛?
    // 濡傛灉浣犲笇鏈涒€滃彉鍖栧悗浠嶄繚鎸佸叏閫夆€濓紝鎶?intersect 鏀规垚 selectedSet = allRooms.toSet()

    Column(modifier = modifier.padding(16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "杩斿洖")
            }
            Text("鎴块棿鍙风紪杈?, style = MaterialTheme.typography.titleLarge)
            Spacer(Modifier.weight(1f))
            TextButton(onClick = { onApply(selectedSet.toList()) }) { Text("搴旂敤") }
        }

        Spacer(Modifier.height(10.dp))

        Text("閫夋嫨妤煎眰", style = MaterialTheme.typography.titleMedium)
        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            items(floors) { f ->
                FilterChip(
                    selected = f == selectedFloor,
                    onClick = { selectedFloor = f },
                    label = { Text("${f}灞?) }
                )
            }
        }

        Spacer(Modifier.height(8.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("鏈眰鎴块棿锛?{roomsOfFloor.size}")
            Text("鏈眰宸查€夛細$selectedCount")
        }

        Spacer(Modifier.height(8.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            OutlinedButton(
                onClick = {
                    selectedRoomsState.value = (selectedSet + roomsOfFloor).toList()
                },
                modifier = Modifier.weight(1f)
            ) { Text("鍏ㄩ€夋湰灞?) }

            OutlinedButton(
                onClick = {
                    selectedRoomsState.value = (selectedSet - roomsOfFloor.toSet()).toList()
                },
                modifier = Modifier.weight(1f)
            ) { Text("鍙栨秷鏈眰") }

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
