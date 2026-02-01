package com.example.catscandemo.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.example.catscandemo.domain.model.ScanResult
import androidx.compose.ui.text.style.TextOverflow


/**
 * ResultItemController: 管理单条扫描结果的数据与状态
 */
class ResultItemController(
    initialItem: ScanResult,
    private val onDelete: () -> Unit,
    private val onClickCopy: () -> Unit,
    private val onUpdate: (ScanResult) -> Unit
) {

    var item by mutableStateOf(initialItem)
        private set

    var expanded by mutableStateOf(false)

    // 可编辑字段状态
    var operator by mutableStateOf(initialItem.scanData.operator)
    var room by mutableStateOf(initialItem.scanData.room)
    var content by mutableStateOf(initialItem.scanData.text)

    /**
     * 更新本地字段状态
     */
    fun syncItem(newItem: ScanResult) {
        item = newItem
        operator = newItem.scanData.operator
        room = newItem.scanData.room
        content = newItem.scanData.text
    }

    /**
     * 保存当前编辑
     */
    fun save() {
        val updatedScanData = item.scanData.copy(
            operator = operator,
            text = content,
            room = room
        )
        val updatedItem = item.copy(scanData = updatedScanData)
        item = updatedItem
        onUpdate(updatedItem)
        expanded = false
    }

    /**
     * Composable 渲染函数
     */
    @Composable
    fun Render(highlight: Boolean = false) {
        val bgColor = if (highlight) Color(0xFFFFF59D) else Color.White
        val deleteColor = Color(0xFFF44336)
        val expandColor = Color(0xFF2196F3)

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(bgColor)
                .padding(vertical = 6.dp, horizontal = 8.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = item.scanData.text,
                    modifier = Modifier
                        .weight(1f)
                        .clickable { onClickCopy() },
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.bodyLarge
                )

                IconButton(onClick = { expanded = !expanded }) {
                    Icon(
                        imageVector = if (expanded) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
                        contentDescription = if (expanded) "收起" else "展开",
                        tint = expandColor
                    )
                }

                IconButton(onClick = onDelete) {
                    Icon(
                        imageVector = Icons.Filled.Delete,
                        contentDescription = "删除",
                        tint = deleteColor
                    )
                }
            }

            if (expanded) {
                ResultItemExpandEditor()
            }
        }
    }

    /**
     * 下拉编辑区域 Composable
     */
    @Composable
    private fun ResultItemExpandEditor() {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(Color(0xFFF5F5F5), RoundedCornerShape(8.dp))
                .padding(12.dp)
        ) {
            Text(text = "序号：${item.index}", style = MaterialTheme.typography.bodyMedium)

            Spacer(modifier = Modifier.height(8.dp))

            OutlinedTextField(
                value = operator,
                onValueChange = { operator = it },
                label = { Text("牛马") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(8.dp))

            OutlinedTextField(
                value = room,
                onValueChange = { room = it},
                label = { Text("房间") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(8.dp))

            OutlinedTextField(
                value = content,
                onValueChange = { content = it },
                label = { Text("内容") },
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(12.dp))

            Button(
                onClick = { save() },
                modifier = Modifier.align(Alignment.End)
            ) {
                Text("保存")
            }
        }
    }
}
