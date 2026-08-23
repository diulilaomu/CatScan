package com.example.catscandemo.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items as gridItems
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.catscandemo.domain.model.TemplateModel

@Composable
fun DiscreteRoomSelectionDialog(
    template: TemplateModel,
    initialFloor: Int,
    initialTag: String,
    onConfirm: (floor: Int, room: String, tag: String) -> Unit,
    onDismiss: () -> Unit
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

    // 已选择（已扫过）的房间：配置了标签时只统计当前标签下的扫描记录
    val scannedRooms = remember(template.id, template.scans, selectedTag) {
        val source = if (template.tags.isEmpty()) {
            template.scans
        } else {
            template.scans.filter { it.tag == selectedTag }
        }
        source.mapNotNull { scan -> scan.room.takeIf(String::isNotBlank) }.toSet()
    }

    // 高亮配色：配置了标签时用该标签的淡色，否则沿用主题色
    val activeTagIndex = template.tags.indexOf(selectedTag)
    val highlightBg = if (activeTagIndex >= 0) {
        tagContainerColor(activeTagIndex)
    } else {
        MaterialTheme.colorScheme.primary
    }
    val highlightFg = if (activeTagIndex >= 0) {
        tagContentColor(activeTagIndex)
    } else {
        MaterialTheme.colorScheme.onPrimary
    }

    val rooms = roomsByFloor[selectedFloor].orEmpty()

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("地点选择") },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                Text("选择楼层", style = MaterialTheme.typography.labelLarge)
                Spacer(Modifier.height(6.dp))
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(floors) { floor ->
                        FilterChip(
                            selected = selectedFloor == floor,
                            onClick = { selectedFloor = floor },
                            label = { Text("${floor}层") }
                        )
                    }
                }

                if (template.tags.isNotEmpty()) {
                    Spacer(Modifier.height(14.dp))
                    Text("选择标签", style = MaterialTheme.typography.labelLarge)
                    Spacer(Modifier.height(6.dp))
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(template.tags) { tag ->
                            val tagIndex = template.tags.indexOf(tag)
                            FilterChip(
                                selected = selectedTag == tag,
                                onClick = { selectedTag = tag },
                                label = { Text(tag) },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = tagContainerColor(tagIndex),
                                    selectedLabelColor = tagContentColor(tagIndex)
                                )
                            )
                        }
                    }
                }

                Spacer(Modifier.height(14.dp))
                Text("选择房间号", style = MaterialTheme.typography.labelLarge)
                Spacer(Modifier.height(8.dp))
                LazyVerticalGrid(
                    columns = GridCells.Fixed(4),
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 320.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    gridItems(rooms, key = { it }) { room ->
                        val isSelected = room in scannedRooms
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(48.dp)
                                .background(
                                    color = if (isSelected) highlightBg else MaterialTheme.colorScheme.surfaceVariant,
                                    shape = RoundedCornerShape(12.dp)
                                )
                                .clickable {
                                    onConfirm(selectedFloor, room, selectedTag)
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = room,
                                style = MaterialTheme.typography.labelLarge,
                                color = if (isSelected) highlightFg else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {}
    )
}
