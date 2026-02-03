# PC 客户端滚轮修复说明

## 修复内容

### 问题
- 数据量多时，翻页按钮和其他内容会被挤出窗口外
- 无法使用滚轮滚动查看被挤出的内容

### 根本原因
1. `.el-card__body` 设置了 `overflow: hidden`，导致内容无法滚动
2. 表格(`table-wrapper`)、分页等内容没有合适的滚动容器

### 修复方案

#### 1. 新增滚动容器 `content-scroll-container`
```html
<div class="content-scroll-container">
    <div class="table-controls">...</div>
    <div class="table-wrapper">...</div>
    <div class="pagination-section">...</div>
</div>
```

#### 2. CSS 修改

**a) 新增容器样式**
```css
.content-scroll-container {
    flex: 1;
    overflow-y: auto;      /* ✅ 启用垂直滚动 */
    overflow-x: hidden;
    display: flex;
    flex-direction: column;
    gap: 12px;
    padding-right: 4px;    /* 为滚轮留空间 */
}
```

**b) 自定义滚轮样式**
```css
.content-scroll-container::-webkit-scrollbar {
    width: 12px;           /* 滚轮宽度 */
}

.content-scroll-container::-webkit-scrollbar-track {
    background: rgba(0, 0, 0, 0.3);
    border-radius: 6px;
}

.content-scroll-container::-webkit-scrollbar-thumb {
    background: rgba(255, 255, 255, 0.25);
    border-radius: 6px;
    min-height: 40px;      /* 最小高度，避免太小 */
}

.content-scroll-container::-webkit-scrollbar-thumb:hover {
    background: rgba(255, 255, 255, 0.4);
}

.content-scroll-container::-webkit-scrollbar-thumb:active {
    background: rgba(255, 255, 255, 0.5);
}
```

**c) table-wrapper 样式调整**
```css
.table-wrapper {
    flex: 1;
    min-height: 300px;     /* ✅ 最小高度 */
    max-height: none;
    overflow: auto;
    border: 1px solid rgba(255, 255, 255, 0.1);
    border-radius: 8px;
    background: rgba(0, 0, 0, 0.2);
}
```

## 效果验证

### 测试步骤
1. 打开 PC 客户端
2. 添加或导入大量数据（例如使用测试模板的100条数据）
3. 检查以下功能：

✅ **滚轮功能**
- 使用鼠标滚轮可以上下滚动表格区域
- 滚轮条显示并可见

✅ **翻页按钮可见**
- 向下滚动时可以看到分页控件
- 分页按钮不再被挤出窗口外

✅ **滚轮样式**
- 滚轮条宽度：12px，易于抓取
- 颜色：浅灰色，与深色主题匹配
- Hover 时变亮，Active 时更亮

✅ **布局完整性**
- 搜索框始终固定在顶部（在 `.el-card__header` 中）
- 表格内容可滚动
- 分页控件可滚动到
- 无布局断裂

## 浏览器兼容性

- ✅ Chrome/Edge (推荐)
- ✅ Firefox (使用 scrollbar-width: thin)
- ✅ Safari (使用 -webkit- 前缀)

## 如果滚轮仍未显示

请检查：
1. **浏览器兼容性**：确保使用现代浏览器（Chrome 80+）
2. **Windows 设置**：检查是否禁用了滚轮
3. **数据量**：确保数据确实超过了窗口高度
4. **F12 开发者工具**：
   - 检查 `.content-scroll-container` 是否有 `overflow-y: auto`
   - 检查该容器的高度是否小于其内容高度
   - 检查是否有 CSS 覆盖了这些样式

## 其他改进

- 滚轮宽度增加到 12px，便于拖拽
- 添加 min-height: 40px，避免滚轮条过小
- 添加 hover 和 active 状态，改进交互反馈
- 添加 padding-right: 4px，为滚轮留出空间
