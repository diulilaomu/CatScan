package com.example.catscandemo.ui.components

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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items as gridItems
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.example.catscandemo.domain.model.ScanResult
import com.example.catscandemo.domain.model.TemplateModel
import com.example.catscandemo.domain.model.tagColorIndexFor

/**
 * 地点选择弹窗（紧凑版）：楼层 chips / 房间网格 / 标签 chips（可选）/ 底部自由编辑（可选）。
 *
 * 两种用法：
 * - 普通选择（历史行改房间）：点房间立即回调并关闭（由调用方关闭）。
 * - 游标修改（stickySelection=true，主页表头徽标）：楼层→房间→标签自上而下；
 *   点房间只高亮不关闭，楼层上方展示该房间已录数据（复用首页历史行，可展开编辑）；
 *   楼层/房间/标签任一变化即回调 onConfirm，改动自动生效，右上角 X 关闭。
 *
 * 房间按钮配色不跟随标签色：有标签数据的房间在文字下方居中显示各标签色圆点，
 * 有数据但无标签的房间显示绿点，没录过数据的房间不显示圆点（文字垂直居中）。
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun DiscreteRoomSelectionDialog(
    template: TemplateModel,
    initialFloor: Int,
    initialTag: String,
    onConfirm: (floor: Int, room: String, tag: String) -> Unit,
    onDismiss: () -> Unit,
    // false 时隐藏标签选择行（如历史记录抽屉里改标签走抽屉自己的标签 chips）
    showTagSelector: Boolean = true,
    // true 时在底部加自由编辑输入，房间号不受模板预设限制
    allowCustomRoom: Boolean = false,
    // 游标修改模式：选房间不关闭、改动即回调生效
    stickySelection: Boolean = false,
    initialRoom: String = "",
    // 结果仓库当前列表（与首页同一份数据）：已录面板优先按 UUID 取其真实 Long id，
    // 避免新扫码尚未重建为稳定 hash id 时点删除删不掉
    scanResults: List<ScanResult> = emptyList(),
    // 游标模式下已录数据行的操作回调（与首页历史列表同一套）
    onCopyItem: (String) -> Unit = {},
    onDeleteItem: (Long) -> Unit = {},
    onUpdateItem: (Long, ScanResult) -> Unit = { _, _ -> },
    onAddTag: (String) -> Unit = {},
    onRenameTag: (String, String, Int) -> Unit = { _, _, _ -> },
    onRemoveTag: (String) -> Unit = {}
) {
    fun floorOfRoom(code: String): Int? {
        if (code.length < 3) return null
        return code.dropLast(2).toIntOrNull()
    }

    val roomsByFloor = remember(template.id, template.selectedRooms) {
        template.selectedRooms
            .mapNotNull { room ->
                floorOfRoom(room)
                    ?.takeIf { floor -> floor in 1..template.maxFloor.coerceAtLeast(1) }
                    ?.let { floor -> floor to room }
            }
            .groupBy(keySelector = { it.first }, valueTransform = { it.second })
            .mapValues { (_, rooms) -> rooms.sorted() }
            .toSortedMap()
    }

    val floors = roomsByFloor.keys.toList()
    val firstFloor = initialFloor.takeIf { it in floors } ?: floors.firstOrNull() ?: 1
    var selectedFloor by remember(template.id, template.selectedRooms) {
        mutableIntStateOf(firstFloor)
    }

    // 标签选择：默认回到上次选中的标签，无记录时取第一个（与楼层策略一致）
    var selectedTag by remember(template.id, template.tags) {
        mutableStateOf(initialTag.takeIf { it in template.tags } ?: template.tags.firstOrNull() ?: "")
    }

    // 游标模式：已选房间（点房间只高亮，不关闭）
    var selectedRoom by remember(template.id) {
        mutableStateOf(
            if (stickySelection && initialRoom in (roomsByFloor[firstFloor].orEmpty())) initialRoom else ""
        )
    }

    // 自由编辑的房号（允许覆盖预设房间号网格）
    var customRoom by remember { mutableStateOf("") }

    // 房间 → 已录入的标签集合（圆点数据源）；完全没录过数据的房间不在此表
    val roomTagsMap = remember(template.id, template.scans) {
        buildMap<String, MutableSet<String>> {
            template.scans.forEach { scan ->
                val room = scan.room
                if (room.isBlank()) return@forEach
                getOrPut(room) { mutableSetOf() }.apply {
                    if (scan.tag.isNotBlank()) add(scan.tag)
                }
            }
        }
    }

    // 无标签数据的绿色圆点（有标签数据则用标签色点）
    val untaggedDotColor = Color(0xFF4CAF50)

    val rooms = roomsByFloor[selectedFloor].orEmpty()

    // 游标模式：楼层/房间/标签任一确定即自动生效（不关闭弹窗）
    fun applyCursor() {
        if (stickySelection && selectedRoom.isNotBlank()) {
            onConfirm(selectedFloor, selectedRoom, selectedTag)
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(if (stickySelection) "修改录入游标" else "选择房间")
                Spacer(Modifier.weight(1f))
                IconButton(onClick = onDismiss, modifier = Modifier.size(28.dp)) {
                    Icon(
                        imageVector = Icons.Filled.Close,
                        contentDescription = "关闭",
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // 已选房间的已录数据（游标模式，置于楼层上方）：复用首页历史行样式，点击行可展开编辑
                if (stickySelection && selectedRoom.isNotBlank()) {
                    RoomHistoryPanel(
                        template = template,
                        floor = selectedFloor,
                        room = selectedRoom,
                        scanResults = scanResults,
                        onCopyItem = onCopyItem,
                        onDeleteItem = onDeleteItem,
                        onUpdateItem = onUpdateItem,
                        onAddTag = onAddTag,
                        onRenameTag = onRenameTag,
                        onRemoveTag = onRemoveTag
                    )
                }

                // 楼层 chips（1层 2层…自解释，不加标题）；
                // 游标模式带录入进度（已录房间数/总房间数）：未录完=主题色，录完=灰
                LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    items(floors) { floor ->
                        FilterChip(
                            selected = selectedFloor == floor,
                            onClick = {
                                selectedFloor = floor
                                // 房间不属于新楼层时清空选择（已录面板随选房恢复）
                                if (selectedRoom.isNotBlank() && selectedRoom !in (roomsByFloor[floor].orEmpty())) {
                                    selectedRoom = ""
                                }
                            },
                            label = {
                                if (stickySelection) {
                                    val floorRooms = roomsByFloor[floor].orEmpty()
                                    val recorded = floorRooms.count { it in roomTagsMap }
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text("${floor}层")
                                        Spacer(Modifier.width(4.dp))
                                        Text(
                                            text = "$recorded/${floorRooms.size}",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = if (recorded < floorRooms.size) {
                                                MaterialTheme.colorScheme.primary
                                            } else {
                                                MaterialTheme.colorScheme.onSurfaceVariant
                                            }
                                        )
                                    }
                                } else {
                                    Text("${floor}层")
                                }
                            }
                        )
                    }
                }

                // 房间网格：选中高亮；文字下方圆点表示已录标签（无标签数据=黑/白点）
                LazyVerticalGrid(
                    columns = GridCells.Fixed(4),
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = if (stickySelection) 234.dp else 264.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    gridItems(rooms, key = { it }) { room ->
                        val isSelected = stickySelection && selectedRoom == room
                        // 圆点规则：无数据=不显示（文字垂直居中）；
                        // 有数据无标签=绿点；有标签数据=各标签色点
                        val hasData = room in roomTagsMap
                        val recordedTags = if (hasData) {
                            template.tags.filter { tag -> roomTagsMap[room]?.contains(tag) == true }
                        } else {
                            emptyList()
                        }
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(if (stickySelection) 46.dp else 38.dp)
                                .background(
                                    color = if (isSelected) {
                                        MaterialTheme.colorScheme.primary
                                    } else {
                                        MaterialTheme.colorScheme.surfaceVariant
                                    },
                                    shape = RoundedCornerShape(8.dp)
                                )
                                .border(
                                    width = 1.dp,
                                    color = if (isSelected) {
                                        MaterialTheme.colorScheme.primary
                                    } else {
                                        MaterialTheme.colorScheme.outlineVariant
                                    },
                                    shape = RoundedCornerShape(8.dp)
                                )
                                .clickable {
                                    if (stickySelection) {
                                        selectedRoom = room
                                        applyCursor()
                                    } else {
                                        onConfirm(selectedFloor, room, selectedTag)
                                    }
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.Center
                            ) {
                                Text(
                                    text = room,
                                    style = MaterialTheme.typography.labelMedium,
                                    maxLines = 1,
                                    color = if (isSelected) {
                                        MaterialTheme.colorScheme.onPrimary
                                    } else {
                                        MaterialTheme.colorScheme.onSurfaceVariant
                                    }
                                )
                                if (stickySelection && hasData) {
                                    Spacer(Modifier.height(3.dp))
                                    Row(
                                        horizontalArrangement = Arrangement.spacedBy(3.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        if (recordedTags.isEmpty()) {
                                            Box(
                                                Modifier
                                                    .size(5.dp)
                                                    .background(untaggedDotColor, CircleShape)
                                            )
                                        } else {
                                            recordedTags.forEach { tag ->
                                                val tagIndex = tagColorIndexFor(
                                                    template.tags, template.tagColors, tag
                                                )
                                                Box(
                                                    Modifier
                                                        .size(5.dp)
                                                        .background(tagContainerColor(tagIndex), CircleShape)
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                // 标签 chips（颜色即语义）：全部铺开展示，不做展开/收起；
                // 标签实在太多超出高度时在区域内上下滑动
                if (showTagSelector && template.tags.isNotEmpty()) {
                    FlowRow(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 120.dp)
                            .verticalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        template.tags.forEach { tag ->
                            val tagIndex = tagColorIndexFor(template.tags, template.tagColors, tag)
                            val tagSelected = selectedTag == tag
                            // 迷你标签 chip（比 FilterChip 小一圈）：
                            // 选中=标签色底+标签色字，未选中=surfaceVariant 底
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(
                                        if (tagSelected) {
                                            tagContainerColor(tagIndex)
                                        } else {
                                            MaterialTheme.colorScheme.surfaceVariant
                                        }
                                    )
                                    .clickable {
                                        selectedTag = tag
                                        applyCursor()
                                    }
                                    .padding(horizontal = 10.dp, vertical = 4.dp)
                            ) {
                                Text(
                                    text = tag,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = if (tagSelected) {
                                        tagContentColor(tagIndex)
                                    } else {
                                        MaterialTheme.colorScheme.onSurfaceVariant
                                    },
                                    maxLines = 1
                                )
                            }
                        }
                    }
                }

                // 自由编辑：预设网格之外的房号（占位符说明用途，确认走"使用"）
                if (allowCustomRoom) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        LabeledCompactField(
                            label = "自由输入房号",
                            value = customRoom,
                            onValueChange = { customRoom = it },
                            modifier = Modifier.weight(1f)
                        )
                        TextButton(
                            enabled = customRoom.isNotBlank(),
                            onClick = {
                                onConfirm(selectedFloor, customRoom.trim(), selectedTag)
                            }
                        ) { Text("使用") }
                    }
                }

                if (floors.isEmpty()) {
                    Text(
                        text = if (allowCustomRoom) {
                            "该模板未配置房间，可在上方自由输入房号"
                        } else {
                            "该模板未配置房间"
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        },
        confirmButton = {}
    )
}

/**
 * 已选房间的已录数据面板（游标修改弹窗顶部）：
 * 与首页历史列表同一套 ResultItem 行（圆角容器适配弹窗），点击行展开编辑、可复制/删除。
 */
@Composable
private fun RoomHistoryPanel(
    template: TemplateModel,
    floor: Int,
    room: String,
    scanResults: List<ScanResult>,
    onCopyItem: (String) -> Unit,
    onDeleteItem: (Long) -> Unit,
    onUpdateItem: (Long, ScanResult) -> Unit,
    onAddTag: (String) -> Unit,
    onRenameTag: (String, String, Int) -> Unit,
    onRemoveTag: (String) -> Unit
) {
    val floorRegex = remember { Regex("\\d+") }
    val roomScans = remember(template.id, template.scans, floor, room) {
        template.scans.filter { scan ->
            scan.room == room && floorRegex.find(scan.floor)?.value?.toIntOrNull() == floor
        }
    }

    // 行 Long id 解析：优先取结果仓库里的真实 id（按 scanData.id/UUID 匹配）——
    // 刚扫入的记录在仓库里还是入库时的顺序自增 id，尚未重建为稳定 hash，
    // 若这里自己按 hash 现算就会删不到（曾导致"最上面那条删不掉"）；
    // 仓库里查不到时才回退到与 DataManager 同规则的稳定 hash id（冲突递增）
    val rows = remember(roomScans, scanResults) {
        val repoIdByDataId = scanResults.associate { it.scanData.id to it.id }
        val usedIds = HashSet<Long>()
        roomScans.map { scan ->
            val id = repoIdByDataId[scan.id] ?: run {
                var candidate = scan.id.hashCode().toLong()
                while (!usedIds.add(candidate)) candidate++
                candidate
            }
            usedIds.add(id)
            id to scan
        }
    }

    Surface(
        shape = RoundedCornerShape(10.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 6.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(
                text = "$room 已录 ${roomScans.size} 条",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            if (rows.isEmpty()) {
                Text(
                    text = "该房间暂无录入数据",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 150.dp)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    rows.forEach { (longId, scan) ->
                        // key 含 longId：仓库 id 迟到/变化时重建控制器，回调始终用当前真实 id
                        val controller = remember(scan.id, longId) {
                            ResultItemController(
                                initialItem = ScanResult(id = longId, index = 0, scanData = scan),
                                onDelete = { onDeleteItem(longId) },
                                onClickCopy = { onCopyItem(scan.text) },
                                onUpdate = { updated -> onUpdateItem(longId, updated) },
                                onAddTag = onAddTag,
                                onRenameTag = onRenameTag,
                                onRemoveTag = onRemoveTag
                            )
                        }
                        // 模板刷新（编辑/删除后）同步行数据，保持展开态
                        LaunchedEffect(scan) {
                            controller.syncItem(ScanResult(id = longId, index = 0, scanData = scan))
                        }
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                        ) {
                            controller.Render(
                                highlight = false,
                                template = template,
                                showCopy = false
                            )
                        }
                    }
                }
            }
        }
    }
}
