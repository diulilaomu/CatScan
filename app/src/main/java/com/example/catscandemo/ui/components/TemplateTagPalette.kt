package com.example.catscandemo.ui.components

import androidx.compose.ui.graphics.Color

/**
 * 模板标签的高亮配色，标签在模板中的序号决定颜色。
 * 标签数量不限，超出 8 组后按取余循环复用配色。
 *
 * 区分度优先：8 组色相彼此拉开（蓝/绿/橙/青/玫红/紫/柠檬黄/棕），
 * 原淡蓝 vs 淡青、淡橙 vs 淡黄这类近似色对全部错开；底色用中饱和度，
 * 深色文字保证任何一组在日/夜间主题下都可读。
 */
private val TagContainerColors = listOf(
    Color(0xFF90CAF9), // 蓝
    Color(0xFFA5D6A7), // 绿
    Color(0xFFFFB74D), // 橙
    Color(0xFF4DD0E1), // 青
    Color(0xFFF48FB1), // 玫红
    Color(0xFFB39DDB), // 紫
    Color(0xFFFFEE58), // 柠檬黄
    Color(0xFFBCAAA4)  // 棕
)

private val TagContentColors = listOf(
    Color(0xFF0D47A1), // 深蓝
    Color(0xFF1B5E20), // 深绿
    Color(0xFF7A3C00), // 深橙棕
    Color(0xFF00363A), // 深青
    Color(0xFF880E4F), // 深玫红
    Color(0xFF311B92), // 深紫
    Color(0xFF6E5D00), // 深橄榄
    Color(0xFF3E2723)  // 深棕
)

private fun cycleIndex(index: Int, size: Int): Int =
    ((index % size) + size) % size

/** 预设标签颜色数量（标签编辑对话框的颜色选择用） */
val TagPresetColorCount: Int
    get() = TagContainerColors.size

fun tagContainerColor(index: Int): Color =
    TagContainerColors[cycleIndex(index, TagContainerColors.size)]

fun tagContentColor(index: Int): Color =
    TagContentColors[cycleIndex(index, TagContentColors.size)]
