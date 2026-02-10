# CatScan - 二维码/条码扫描工具

CatScan 是一个跨平台扫码采集系统：Android 负责实时扫描与模板化录入，Windows 客户端负责集中展示、管理与自动输入。系统支持 UDP 发现、二维码直连、批量上传、去重与高亮、模板房间轮询等能力。

> 说明：本项目完全由 AI 开发，代码 100% 为 AI 生成。

## ✨ 主要特性

### Android 客户端
- ✅ **实时扫码**：CameraX + ML Kit，支持二维码与常见一维码（CODE128、CODE39、EAN-13、EAN-8）。
- ✅ **双通道识别**：
  - 通道1：对原始帧按间隔扫描，性能稳定。
  - 通道2：OpenCV 检测条码候选区域，裁剪后再用 ML Kit 识别，提高一维码命中率。
- ✅ **可视化扫码框**：叠加检测框与扫描遮罩，可按需开关。
- ✅ **相册识别**：支持从相册选图识别，失败时自动增强与中心裁剪再识别。
- ✅ **模板化录入**：模板包含操作人/校区/楼栋/楼层/房间等字段，扫码自动填充。
- ✅ **房间轮询**：按楼层与房间号轮询；本层用完后自动切换到下一层。
- ✅ **结果管理**：编辑/删除单条记录、清空列表、重复高亮、可选去重策略。
- ✅ **数据同步**：HTTP 单条/批量上传；支持删除/更新动作同步。
- ✅ **网络发现**：UDP 自动发现 PC，支持主动发现 + 被动发现弹窗。
- ✅ **自动连接检测**：心跳检测服务端在线状态，断开后自动进入发现模式。
- ✅ **快捷联动**：扫描 PC 端生成的 `winClientLink:` 二维码即可一键绑定上传地址。

### Windows 客户端
- ✅ **桌面应用**：Eel + FastAPI，本地桌面 UI（Vue 3 + Element Plus）。
- ✅ **数据表格**：搜索、排序、分页、复制、删除、清空（含撤销）。
- ✅ **CSV 导出**：一键导出当前数据。
- ✅ **自动输入**：可切换自动键盘输入（pynput），适配任意文本输入窗口。
- ✅ **连接助手**：展示本机 IP 列表、生成二维码供手机扫描绑定。
- ✅ **UDP 发现服务**：响应 Android 广播请求。
- ✅ **日志记录**：运行日志落地到 `winClient/log/`。

## 🔎 扫描引擎与流程

- **通道1（快速扫描）**：对原始帧按可配置间隔进行 ML Kit 扫描，保证实时性。
- **通道2（增强扫描）**：
  - OpenCV 在 ROI 区域内检测条码候选框（面积/长宽比/实心度/梯度评分）。
  - 对候选框进行二次裁剪（扩展框 + 中心条带），交给 ML Kit 二次识别。
  - 多框稳定器减少抖动，提升连续识别稳定性。

## 🧩 模板与数据组织

- **模板字段**：名称、操作人、校区、楼栋、最大楼层、每层房间数、已选房间、扫描记录。
- **房间轮询**：按模板选定房间号顺序自动填充；每层轮询完成自动跳转下一层。
- **离线存储**：
  - 模板：`templates.json`（应用内部存储）。
  - 扫码记录：`scan_history_<templateId>.json` / `scan_history_no_template.json`。
- **导出能力**：模板管理页支持 TXT / JSON 导出（支持批量选择），保存到 `Downloads/CatScan/`。

## 🧭 数据同步与网络发现

- **同步协议**：Android 使用 HTTP 上传到 Windows（支持单条与批量）。
- **动作类型**：`add` / `update` / `delete`，PC 端按动作更新表格。
- **心跳检测**：GET `/postqrdata` 作为在线检测接口。
- **UDP 发现**：
  - 请求：`CATSCAN_DISCOVERY_REQUEST`
  - 响应：`CATSCAN_DISCOVERY_RESPONSE:http://IP:29027/postqrdata`
- **二维码直连**：PC 生成 `winClientLink:<url>` 二维码，Android 扫描后弹窗确认切换地址。

## 📁 项目结构

```
CatScan/
├── app/                          # Android 客户端
│   ├── src/main/java/com/example/catscandemo/
│   │   ├── data/                # 数据层（repository/network/manager）
│   │   ├── domain/              # 领域层（model/use_case）
│   │   ├── presentation/        # ViewModel
│   │   ├── ui/                  # Compose UI（components/main）
│   │   ├── utils/               # 扫描引擎与权限工具
│   │   ├── di/                  # Hilt 依赖注入
│   │   └── CatScanApplication.kt
│   └── src/main/res/
│
├── winClient/                    # Windows 客户端
│   ├── main.py                   # FastAPI + Eel 入口
│   ├── udp_discovery.py          # UDP 发现服务
│   ├── requirements.txt          # Python 依赖
│   └── web/                      # Web UI（Vue + Element Plus）
│
└── ARCHITECTURE.md               # 架构设计文档
```

## 🛠️ 技术栈

### Android 客户端
- **语言**：Kotlin
- **UI**：Jetpack Compose + Material 3
- **架构**：MVVM + Hilt
- **相机**：CameraX
- **识别**：ML Kit Barcode Scanning
- **图像处理**：OpenCV 4.x
- **网络**：OkHttp3
- **协程**：Kotlin Coroutines

### Windows 客户端
- **语言**：Python 3.8+
- **桌面框架**：Eel
- **后端 API**：FastAPI + Uvicorn
- **前端**：Vue 3 + Element Plus
- **发现**：UDP 广播
- **键盘输入**：pynput
- **二维码**：qrcode.js
- **打包**：PyInstaller（可选）

## 📡 通信协议

### HTTP API
- **端口**：29027
- **端点**：`POST /postqrdata`、`GET /postqrdata`（心跳）
- **数据格式**：JSON

#### 单条数据格式
```json
{
  "qrdata": "扫码内容",
  "templateName": "模板名称",
  "operator": "操作员",
  "campus": "校区",
  "building": "楼栋",
  "floor": "楼层",
  "room": "房间",
  "id": "唯一标识符",
  "action": "add/update/delete"
}
```

#### 批量数据格式
```json
{
  "batch": true,
  "data": [
    {
      "qrdata": "扫码内容1",
      "templateName": "模板名称",
      "operator": "操作员",
      "campus": "校区",
      "building": "楼栋",
      "floor": "楼层",
      "room": "房间",
      "id": "唯一标识符",
      "action": "add/update/delete"
    }
  ]
}
```

### UDP 发现协议
- **端口**：29028
- **请求消息**：`CATSCAN_DISCOVERY_REQUEST`
- **响应消息**：`CATSCAN_DISCOVERY_RESPONSE:http://IP:29027/postqrdata`

### 二维码绑定
- **格式**：`winClientLink:http://IP:29027/postqrdata`

## 📊 数据结构

### ScanData
| 字段 | 类型 | 说明 |
|------|------|------|
| id | String | 唯一标识符（UUID） |
| text | String | 扫码内容 |
| timestamp | Long | 扫码时间戳 |
| operator | String | 操作人 |
| campus | String | 校区 |
| building | String | 楼栋 |
| floor | String | 楼层 |
| room | String | 房间 |
| templateId | String | 模板 ID |
| templateName | String | 模板名称 |
| uploaded | Boolean | 是否已上传 |

### TemplateModel
| 字段 | 类型 | 说明 |
|------|------|------|
| id | String | 唯一标识符 |
| name | String | 模板名称 |
| operator | String | 操作人 |
| campus | String | 校区 |
| building | String | 楼栋 |
| maxFloor | Int | 最大楼层 |
| roomCountPerFloor | Int | 每层房间数 |
| selectedRooms | List<String> | 选中的房间 |
| scans | List<ScanData> | 扫码数据 |

### ScanResult
| 字段 | 类型 | 说明 |
|------|------|------|
| id | Long | 唯一标识符 |
| index | Int | 索引 |
| scanData | ScanData | 扫码数据 |
| uploaded | Boolean | 是否已上传 |

## 🚀 快速开始

### Android 客户端

#### 环境要求
- Android Studio Hedgehog 2023.1.1 或更高版本
- JDK 11+
- Android SDK 36+
- Gradle 8.0+

#### 构建步骤
```bash
# 克隆项目
git clone <repository-url>
cd CatScan

# 使用 Android Studio 打开项目
# 或使用命令行构建
./gradlew assembleRelease
```

### Windows 客户端

#### 环境要求
- Python 3.8+
- Windows 10/11

#### 运行步骤
```bash
cd winClient
pip install -r requirements.txt
python main.py
```

#### 打包为 EXE（可选）
```bash
cd winClient
pip install pyinstaller
build.cmd
```

## 📖 使用说明

### Android 端
1. 创建或选择模板，配置楼层与房间号。
2. 在主界面选择模板与楼层，开始扫码（支持相册识别）。
3. 在设置中连接 PC（自动发现 / 手动输入 / 扫描二维码）。
4. 开启“上传到 PC”，自动同步扫码结果。

### Windows 端
1. 运行 `main.py`，自动启动 Web UI 与 API 服务。
2. 通过“本机 IP”列表生成二维码，供 Android 扫描绑定。
3. 使用表格搜索/排序/导出，或开启自动输入。

## ⚙️ 设置项速览
- **自动复制**：扫码后复制到剪贴板。
- **重复扫描**：控制去重策略。
- **实时检测框**：显示/隐藏检测框与扫描遮罩。
- **通道1 帧间隔**：降低频率节省性能。
- **通道2 阈值**：面积/长宽比/实心度/梯度评分。

## 🐛 常见问题

**无法扫码**
- 确认相机权限已授予，光线充足。

**无法发现 PC**
- 确保手机与电脑在同一局域网。
- 检查防火墙是否放行 UDP 29028。

**无法上传**
- 确保 PC 端服务运行，端口 29027 未被占用。

**自动输入无效**
- 确认已安装 `pynput`，且目标窗口处于焦点。

## 📄 许可证

本项目采用 MIT 许可证。

## 🤝 贡献

欢迎提交 Issue 和 Pull Request。

## 📞 联系方式

如有问题或建议，请通过 Issue 反馈。

---

**版本**：3.3.0  
**更新日期**：2026-02-10  
**项目地址**：<repository-url>
