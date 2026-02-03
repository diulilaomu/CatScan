# 测试模板数据导入指南

已生成了包含100条扫描数据的测试模板，文件位置：
- `templates.json` - 模板定义文件
- `scan_history.json` - 扫描历史数据

## 测试模板信息
- **模板名称**: 猫头枪
- **校区**: 猫头校区
- **楼栋**: 2号猫屋
- **楼层**: 6层
- **房间/层**: 24个（共144个房间）
- **扫描数据**: 100条

## 导入方法

### 方法1：通过文件系统导入（开发调试）

Android 应用的数据文件存储在应用沙箱目录 `/data/data/com.example.catscandemo/files/`

使用 ADB 命令导入数据：

```bash
# 将 templates.json 推送到设备
adb push templates.json /data/data/com.example.catscandemo/files/

# 将 scan_history 推送到设备
adb push scan_history.json /data/data/com.example.catscandemo/files/scan_history_d1057093-6cbe-4a57-b4db-b33406b537ca.json
```

**注意**：需要将 `d1057093-6cbe-4a57-b4db-b33406b537ca` 替换为实际的模板ID（在 templates.json 中的 `id` 字段）

### 方法2：通过 Android Studio 导入

1. 打开 Android Studio
2. 选择菜单：`Device File Explorer` (或 View → Tool Windows → Device File Explorer)
3. 导航到 `/data/data/com.example.catscandemo/files/`
4. 拖拽 `templates.json` 到该目录
5. 拖拽 `scan_history.json` 到该目录（可能需要重命名）

### 方法3：通过应用代码导入（推荐）

修改 `MainViewModel.initTemplateStore()` 方法临时导入数据：

```kotlin
fun initTemplateStore(context: Context) {
    if (storeReady) return
    storeReady = true
    appContext = context.applicationContext

    // 如果需要测试，可以先清空数据，然后导入测试数据
    val testTemplates = listOf(
        TemplateModel(
            id = "d1057093-6cbe-4a57-b4db-b33406b537ca",
            name = "猫头枪",
            operator = "测试员",
            campus = "猫头校区",
            building = "2号猫屋",
            maxFloor = 6,
            roomCountPerFloor = 24,
            selectedRooms = (1..6).flatMap { floor ->
                (1..24).map { room -> "F${floor}R${room.toString().padStart(2, '0')}" }
            },
            scans = emptyList()  // 扫描数据会通过 scan_history.json 加载
        )
    )
    
    // 保存测试数据
    templateUseCases.saveTemplates.invoke(testTemplates, testTemplates.first().id)
    
    // 使用数据管理中心初始化数据
    dataManager.initializeData()
    
    // 同步 ViewModel 状态
    activeTemplateId = dataManager.activeTemplateId
    activeTemplate = dataManager.activeTemplate
    
    // 加载扫描结果
    getAllScans()
}
```

## 验证数据

导入后验证方式：

1. **在应用中查看模板**
   - 打开应用
   - 检查模板列表是否显示"猫头枪"
   - 选中模板，检查识别结果列表是否显示100条数据

2. **检查数据持久化**
   ```bash
   # 使用 ADB 查看文件
   adb shell cat /data/data/com.example.catscandemo/files/templates.json
   adb shell cat /data/data/com.example.catscandemo/files/scan_history_d1057093-6cbe-4a57-b4db-b33406b537ca.json
   ```

3. **在文件浏览器中查看**
   - Android Studio → Device File Explorer
   - 导航到 `/data/data/com.example.catscandemo/files/`
   - 查看文件列表和大小

## 数据生成脚本

脚本位置：`generate_test_template.py`

修改脚本可以自定义：
- 数据数量（修改 `range(1, 101)` 中的 101）
- 模板名称、校区、楼栋等信息
- 楼层数和房间数
- 操作员名称

## 测试场景

导入后可以进行的测试：

1. **数据隔离测试**
   - 在猫头枪模板中看到100条数据
   - 无模板状态下应该是空的

2. **切换模板测试**
   - 创建另一个模板
   - 在模板间切换，验证数据独立显示

3. **网络同步测试**
   - 配置 PC 服务器地址
   - 上传数据到 PC
   - 验证 PC 端能接收到所有100条数据

4. **性能测试**
   - 验证应用加载100条数据的速度
   - 检查内存使用情况
   - 验证列表滚动流畅度
