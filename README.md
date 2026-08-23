# CatScan

CatScan 是一套 Android + Windows 的局域网扫码采集工具。Android 端负责扫码、模板和历史数据，Windows 端接收实时镜像，可用于检索、导出和自动输入。

## 核心功能

- CameraX 相机预览，结合 ML Kit 与 OpenCV 识别二维码/条码
- 支持线性模板和离散模板
  - 线性模板按楼层、房间顺序自动推进，重启后从最后一个房间的下一个继续
  - 离散模板扫码后直接选择楼层和房间，同一房间可绑定多个二维码
  - 离散模板支持最多 4 个标签（每个最多 4 个字）：选房窗口中按标签区分已扫房间的高亮颜色（淡色系），所选标签与楼层一样会被记住
- 首页扫码列表采用“数据 - 徽标 - 操作”三列布局，标签和房间号以独立小徽标显示
- 手机端离线保存模板、扫码记录和操作记录
- 通过局域网自动发现、二维码配对和心跳保持连接
- 新增、修改、删除和当前楼层清空操作实时镜像到电脑端
- 支持 TXT、JSON 导出，以及电脑端搜索、排序和自动输入

> 手机端是数据源；电脑端只保存本次运行期间的镜像数据，重启电脑客户端后数据会清空。

## 项目结构

```text
app/        Android 客户端
winClient/  Windows 客户端
docs/       补充文档
```

## 环境要求

- Android：JDK 11、Android SDK 36
- Windows：Python 3.8+

## Android 构建

调试包：

```powershell
.\gradlew.bat :app:assembleDebug
```

发布包需要先复制签名配置示例并填写真实信息：

```powershell
Copy-Item keystore.properties.example keystore.properties
.\gradlew.bat :app:assembleRelease
```

也可以使用 `CATSCAN_STORE_FILE`、`CATSCAN_STORE_PASSWORD`、`CATSCAN_KEY_ALIAS` 和 `CATSCAN_KEY_PASSWORD` 环境变量提供签名配置。`keystore.properties` 已被 Git 忽略，请勿提交真实密钥。

构建产物位于 `app/build/outputs/apk/`。

## Windows 客户端

```powershell
Set-Location winClient
python -m pip install -r requirements.txt
python main.py
```

需要生成可执行文件时可运行 `winClient/build.cmd`。

## 使用流程

1. 启动 Windows 客户端。
2. 在手机端新增模板并选择线性或离散模式。
3. 通过局域网发现或二维码完成配对。
4. 开始扫码，电脑端同步显示本次运行的数据。

默认端口：TCP `29027`、UDP `29028`。手机和电脑需处于同一局域网，并允许程序通过防火墙。

## 数据规则

- 清空扫码数据只影响当前模板的当前楼层
- 线性模板重启后从最后一次扫码房间的下一个房间开始
- 离散模板不会自动递增房间，选择房间后立即完成绑定
- 同一房间允许绑定多个二维码
- 离散模板每条扫码记录携带所选标签；删除扫码记录后，对应标签下该房间的高亮会同步取消
- 标签超量（>4 个）、超长（>4 字）或重复时会在保存/读取时自动裁剪清理
- 电脑端仅作镜像同步，不持久化扫码记录

## 检查与测试

```powershell
.\gradlew.bat :app:testDebugUnitTest :app:lintDebug
.\gradlew.bat :app:assembleDebug
```

当前 Android 版本：`3.6.0`。
