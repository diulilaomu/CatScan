package com.example.catscandemo.ui.components

import androidx.compose.ui.graphics.Color

/**
 * 离散模板标签的高亮配色（淡色），标签在模板中的序号决定颜色。
 * 标签最多 4 个，这里给出 4 组“底色 + 深色文字”的组合。
 */
private val TagContainerColors = listOf(
    Color(0xFFBBDEFB), // 淡蓝
    Color(0xFFC8E6C9), // 淡绿
    Color(0xFFFFE0B2), // 淡橙
    Color(0xFFE1BEE7)  // 淡紫
)

private val TagContentColors = listOf(
    Color(0xFF0D47A1), // 深蓝
    Color(0xFF1B5E20), // 深绿
    Color(0xFFBF360C), // 深橙
    Color(0xFF4A148C)  // 深紫
)

fun tagContainerColor(index: Int): Color =
    TagContainerColors[index.coerceIn(0, TagContainerColors.lastIndex)]

fun tagContentColor(index: Int): Color =
    TagContentColors[index.coerceIn(0, TagContentColors.lastIndex)]
