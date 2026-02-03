# 数据同步问题分析和修复方案

## 问题根源

应用中存在**两个独立的数据存储**，但它们之间的同步机制不完善，导致数据不一致：

### 1. 第一数据源：TemplateModel.scans（模板中的扫描列表）
```kotlin
data class TemplateModel(
    val id: String,
    val name: String,
    val scans: List<ScanData> = emptyList(),  // ← 模板内部存储
    // ...
)
```
- **存储位置**：`TemplateStorage` (JSON文件 `templates.json`)
- **由谁维护**：TemplateUseCases / TemplateRepository
- **特点**：每个模板有自己的扫描列表，用于模板管理和持久化

### 2. 第二数据源：ScanRepository.scanResults（扫描结果列表）
```kotlin
class DefaultScanRepository : ScanRepository {
    private var scanResults: MutableList<ScanResult> = mutableListOf()
    private var currentTemplateId: String? = null
    // ...
}
```
- **存储位置**：`ScanHistoryStorage` (JSON文件 `scan_history_[templateId].json`)
- **由谁维护**：ScanUseCases / DefaultScanRepository
- **特点**：按模板隔离存储，通过 `setCurrentTemplateId()` 切换加载不同模板的数据

### 3. 关键差异

| 属性 | TemplateModel.scans | ScanRepository.scanResults |
|------|-------|--------|
| 数据类型 | `List<ScanData>` | `List<ScanResult>` |
| 存储文件 | `templates.json` | `scan_history_[templateId].json` |
| ID类型 | `String` (UUID) | `Long` (自增) |
| 维护者 | TemplateRepository | DefaultScanRepository |
| UI 用途 | 模板配置管理 | 显示识别结果列表 |

---

## 数据不同步的具体表现

### 问题场景 1：添加新扫描后识别结果列表没有更新
**流程**：
1. 用户扫描条码 → 调用 `appendScanToActiveTemplate(scanData)`
2. 该方法原本只调用 `scanUseCases.addScanToTemplate()` 
3. `addScanToTemplate` **只修改 TemplateModel**，完全忽视 ScanRepository
4. 结果：模板数据更新了，但 UI 列表（来自 ScanRepository）没有更新

### 问题场景 2：切换模板后显示的数据不对
**流程**：
1. 用户从模板A切换到模板B → 调用 `setActiveTemplate(id)`
2. `syncTemplateScansToResults()` 被调用，试图同步数据
3. 但它使用 `addScan()` 方法，这生成了**新的 ID**
4. 导致 ID 映射混乱，数据无法正确关联

---

## 修复方案

### 修复 1：`syncTemplateScansToResults()` 方法

**原问题**：使用 `addScan()` 生成新 ID，导致 ID 不一致

**修复**：直接使用 `replaceAll()` 替换整个列表，用递增index作为ID

```kotlin
private fun syncTemplateScansToResults() {
    if (activeTemplateId != null) {
        val currentTemplate = templateUseCases.getTemplateById(activeTemplateId!!)
        if (currentTemplate != null) {
            val scanResults = currentTemplate.scans.mapIndexed { index, scanData ->
                ScanResult(
                    id = index.toLong() + 1,  // ← 使用简单递增ID
                    index = index + 1,
                    scanData = scanData,
                    uploaded = scanData.uploaded
                )
            }
            scanUseCases.replaceAll.invoke(scanResults)  // ← 直接替换
        }
    }
}
```

### 修复 2：`addScanToActiveTemplate()` 方法

**原问题**：只更新 TemplateModel，忽视 ScanRepository，导致 UI 列表不更新

**修复**：同时操作两个数据源

```kotlin
fun addScanToActiveTemplate(scanData: ScanData): ScanData {
    val t = activeTemplate ?: throw IllegalStateException("No active template")
    
    val updatedScanData = scanData.copy(
        templateId = t.id,
        templateName = t.name
    )
    
    // 1. 更新模板存储（TemplateModel）
    val updatedTemplate = t.copy(
        scans = listOf(updatedScanData) + t.scans
    )
    updateTemplate(updatedTemplate)  // 保存到 templates.json
    
    // 2. 同时更新扫描结果列表（ScanRepository）
    val currentScans = scanUseCases.getAllScans.invoke().toMutableList()
    val scanResult = ScanResult(
        id = System.currentTimeMillis(),
        index = 1,
        scanData = updatedScanData,
        uploaded = false
    )
    currentScans.add(0, scanResult)
    
    // 重新调整索引
    val adjustedScans = currentScans.mapIndexed { index, result ->
        result.copy(index = index + 1)
    }
    scanUseCases.replaceAll.invoke(adjustedScans)  // 保存到 scan_history.json
    
    activeTemplate = updatedTemplate
    saveTemplates()
    
    return updatedScanData
}
```

---

## 数据流更新后的同步机制

```
┌─────────────────────────────────┐
│  MainViewModel / UI 界面          │
└────────────┬──────────────────────┘
             │ appendScanToActiveTemplate()
             ↓
┌─────────────────────────────────┐
│  DataManager                      │
│  - addScanToActiveTemplate()      │
│    ├─ 更新 TemplateModel          │
│    └─ 更新 ScanRepository         │
└────────────┬──────────────────────┘
             │
        ┌────┴────┐
        ↓         ↓
    ┌─────┐   ┌──────────┐
    │模板  │   │扫描结果  │
    │存储  │   │存储      │
    └─────┘   └──────────┘
```

---

## 关键改进点

1. **`addScanToActiveTemplate()` 现在**：
   - ✅ 同时修改 TemplateModel（用于模板持久化）
   - ✅ 同时修改 ScanRepository（用于UI显示）
   - ✅ 两个列表的ID保持一致性（使用时间戳）

2. **`syncTemplateScansToResults()` 现在**：
   - ✅ 使用 `replaceAll()` 而不是 `addScan()`
   - ✅ 避免生成新ID导致的不一致
   - ✅ 用简单递增index作为ScanResult的ID

3. **状态流传播**：
   - ✅ `MainViewModel.getAllScans()` 更新 `_scanResults` StateFlow
   - ✅ `appendScanToActiveTemplate()` 调用后立即 `getAllScans()`
   - ✅ UI 能立即看到新扫描

---

## 测试验证步骤

1. **新增扫描测试**：
   - 扫描条码 → 识别结果列表立即出现（不需要刷新）
   - 模板管理中也能看到该扫描

2. **切换模板测试**：
   - 在模板A中添加3条扫描
   - 切换到模板B，显示为空
   - 切换回模板A，3条扫描恢复显示

3. **删除扫描测试**：
   - 删除一条扫描，模板和列表同时更新
   - 刷新应用，数据持久化正确

4. **网络同步测试**：
   - 上传后，uploaded字段同步到两个存储
   - PC端能正确接收到扫描数据
