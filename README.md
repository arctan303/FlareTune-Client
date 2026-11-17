# FlareTune Client

[简体中文](README.md) · [English](README.en.md)

**为自己的 FlareTune 曲库准备的原生音乐客户端，专注于日常听歌。**

FlareTune Client 连接到你自己的 HTTPS [FlareTune](https://github.com/arctan303/FlareTune) 实例，提供面向移动设备与大屏平板的原生浏览、播放队列、歌词与系统媒体控制体验。当前已提供可构建运行的 Android 工程，并由 GitHub Actions 持续验证单元测试、Lint 与调试构建。

[使用前提与边界](#使用前提与边界) · [现有体验](#现有体验) · [平台路线](#平台路线) · [本地构建](#本地构建) · [参与协作](#参与协作)

> **当前阶段说明**：Android 客户端目前处于开发阶段（尚未发布正式 Release）。代码库内的调试构建、单元测试与静态检查已就绪，但真实机型、蓝牙车机固件、锁屏及各厂商后台保活策略仍在持续验证中。

## 现有体验

- **连接与多账号隔离**：使用实例 HTTPS 根地址与账号登录，支持同实例及跨实例多账号保存与切换；会话 Cookie 借助 Android Keystore 与 AES-GCM 加密存储，支持修改显示名称与更换头像。
- **围绕听歌的导航结构**：分为「主页」「发现」「搜索」「个人歌单库」四个主入口。主页汇总快捷入口、继续收听混合推荐、随机推荐与按曲库语种比例呈现的新入库歌曲；发现页提供语种筛选、歌手与专辑浏览。
- **收藏、歌单与多选整理**：完整读写个人收藏与自建歌单，支持歌单及歌单内曲目排序、批量多选管理（下一首播放、加入队列、加入歌单、批量收藏或移除）。
- **克制且顺手的播放语义**：区分列表上下文整列播放与搜索/历史单曲插播下一首，同时保留「播放全部」入口；本地保留按账号隔离的 4 标签播放历史（歌曲 / 歌手 / 专辑·歌单 / 历史队列快照恢复）与收听统计。
- **全屏播放器与歌词**：提供适配手机与平板双栏的播放界面、封面背景氛围与可拖拽编辑的持久化播放队列；支持逐行/逐字时间轴歌词、译文显示切换与按需请求生成译文（严格消费实例服务端能力）。
- **系统音频与蓝牙歌词**：基于 AndroidX Media3 `MediaSessionService` 提供后台播放、通知栏与锁屏控制、音频焦点及定时休眠（定时 / 播完当前曲目）；可选三态「蓝牙歌词」（关闭 / 仅蓝牙音频连接时 / 始终开启）并在系统媒体元数据层输出当前句歌词，共享封面缓存以减少切歌闪烁。

## 平台路线

| 平台 | 当前状态 | 说明 |
| --- | --- | --- |
| **Android** | 开发中（可本地构建） | 使用 Kotlin、Jetpack Compose 与 Media3，支持 `minSdk 23`（Android 6.0+），`compileSdk` / `targetSdk 36` |
| **Windows** | 规划中 | 已确认的后续官方桌面端方向，尚未创建工程 |
| **iOS** | 暂留社区空间 | 目前没有官方工程与排期 |

## 使用前提与边界

- **依赖已有服务端实例**：本应用不包含公共音乐资源，需连接已部署且处于就绪状态的 [FlareTune](https://github.com/arctan303/FlareTune) HTTPS 实例；具体可用能力（例如歌词来源链或外部音源）取决于服务端版本与管理员已安装的插件。HTTP 接入契约见服务端文档 [API.md](https://github.com/arctan303/FlareTune/blob/main/server/API.md)。
- **客户端职责边界**：专注于日常听歌与个人歌单整理，不提供实例初始化、管理员后台、曲库上传入库或插件配置入口。
- **暂未支持的能力**：离线音乐下载与持久音频缓存尚未实现；本仓库不内置签名发布密钥。

## 仓库结构

```text
.
├── android/                  # Android Gradle 工程（app 模块与 libs.versions.toml 基线）
├── scripts/                  # Windows PowerShell 本地构建、安装与投屏辅助脚本
└── .github/workflows/        # GitHub Actions CI（单测、Lint、assembleDebug）
```

## 本地构建

Android 工程位于 `android/`，技术栈与依赖版本集中声明在 [`android/gradle/libs.versions.toml`](android/gradle/libs.versions.toml)（Gradle 8.13、AGP 8.13.2、Kotlin 2.3.21、Compose BOM 2026.06.01、Media3 1.11.1）。

### 环境准备

- **JDK**：JDK 17 或更新版本（CI 与当前开发验证使用 JDK 21）
- **Android SDK**：Android SDK Platform 36 与 Build Tools 36.0.0
- 通过环境变量 `JAVA_HOME`、`ANDROID_HOME`，或在被 Git 忽略的 `android/local.properties` 中配置本地 SDK 路径

### 构建与检查命令

在仓库根目录（Windows PowerShell）：

```powershell
# 构建 Debug APK
.\scripts\build-android.ps1

# 同时运行 Android Lint
.\scripts\build-android.ps1 -Lint
```

或进入 `android/` 目录使用 Gradle Wrapper（Windows / macOS / Linux 通用）：

```bash
cd android
./gradlew :app:testDebugUnitTest :app:lintDebug :app:assembleDebug
```

Windows 下将 `./gradlew` 替换为 `.\gradlew.bat`。构建产物默认输出在 `android/app/build/outputs/apk/debug/app-debug.apk`（仅用于开发调试）。

### 设备调试辅助脚本（Windows）

`scripts/` 目录下提供用于连接真机调试的辅助脚本（需开启 USB 调试并配置 `ANDROID_HOME`）：

- `.\scripts\deploy-android.ps1`：构建并安装 Debug APK 至已连接的单一设备并重启应用（支持 `-Serial`、`-SkipBuild`、`-Preview`）。
- `.\scripts\watch-android.ps1`：监听 `android/` 源码改动并自动增量构建、覆盖安装与重启（支持 `-Once`、`-Preview`）。
- `.\scripts\mirror-android.ps1`：调用本机 `scrcpy` 打开投屏窗口辅助调试。

## 参与协作

- **分支约定**：日常开发在 `dev` 进行；`main` 禁止直接提交业务变更，仅接收通过 CI 检查（`verify`）的 Pull Request 合入。需要隔离的开发任务从 `dev` 创建临时分支，完成合回并验证后清理。
- **提交边界**：请勿提交任何账号凭据、会话 Cookie、签名密钥（`*.jks` / `*.keystore` / `keystore.properties`）、`android/local.properties` 或构建产物。
- **问题反馈**：提交 Issue 时请附带设备型号、Android 系统版本、连接的服务端版本、复现步骤以及脱敏后的现象描述。

## 相关仓库与许可

- 服务端与 Web 项目：[FlareTune](https://github.com/arctan303/FlareTune)
- 官方插件仓库：[FlareTune-Plugins](https://github.com/arctan303/FlareTune-Plugins)

本项目源代码及文档采用 [MIT](LICENSE) 许可。
