# Android 开发与构建

## 当前契约

来源：2026-11-12 用户明确选择 Kotlin + Jetpack Compose + Media3，并授权在本地初始化安卓工程、验证 APK 构建。

- 当前优先安卓；客户端界面独立重写，服务端与 Web 仍在相邻 FlareTune 仓库。
- 安卓工程位于 `android/`，为未来其他客户端保留独立目录空间。
- 应用 ID / namespace：`com.flaretune.client`。
- 当前工程提供连接/登录、曲库/搜索/收藏、悬浮/全屏播放器、队列与 Media3 系统播放服务。服务仅应用内部可启动；未登录和受限账号不创建播放器。
- 账号、实例、流媒体、统一歌词/译文状态、个人歌单/收藏、实际播放历史与持久队列已实现；真实账号逐项验收见当前任务。持久音频缓存和离线下载未实现。
- 设计范围见 [设计入口](Design-Brief.md)；本轮采用的竞品布局与比例已列入该入口，未采用的研究内容继续作为候选。

## 依赖基线

版本集中管理于 [版本目录](../android/gradle/libs.versions.toml)。

| 组件 | 固定版本 |
| --- | --- |
| Gradle Wrapper | 8.13，分发包 SHA-256 已固定 |
| Android Gradle Plugin | 8.13.2 |
| Kotlin / Compose Compiler | 2.3.21 |
| Compose BOM | 2026.06.01 |
| Activity Compose | 1.13.0 |
| Media3 | 1.11.1 |
| compileSdk / targetSdk | 36 |
| minSdk | 23（Android 6.0） |
| SDK Build Tools | 36.0.0 |
| Java / Kotlin 编译目标 | JVM 17 |

选择该组合是为了兼容当前已安装并验证的 API 36；更新的 Compose BOM 2026.09.00 实际要求 API 37 / AGP 9.1，本次不升级整套构建链。

## 本机准备

需要 JDK 17 或更新版本（当前机器验证使用 JDK 21）、Android SDK Platform 36 与 Build Tools 36.0.0。

SDK 位置通过本机 `ANDROID_HOME` 或被 Git 忽略的 `android/local.properties` 提供；不要把个人绝对路径和签名密钥写进版本库。

Windows 下可直接从仓库根目录执行：

```powershell
.\scripts\build-android.ps1
```

同时执行 Android Lint：

```powershell
.\scripts\build-android.ps1 -Lint
```

脚本读取用户已配置的 JDK / SDK 环境。也可使用标准命令：

```powershell
cd android
.\gradlew.bat :app:assembleDebug :app:lintDebug
```

macOS / Linux 使用 `bash ./gradlew :app:assembleDebug`，并配置当地 JDK 与 SDK。首次构建需要访问 Google Maven、Maven Central 和 Gradle 官方分发源。

## 产物

Debug APK：`android/app/build/outputs/apk/debug/app-debug.apk`。它使用 Android debug 签名，仅用于开发测试。

本地安装（手机打开 USB 调试并授权后）：

```powershell
adb install -r android\app\build\outputs\apk\debug\app-debug.apk
```

## 当前数据迭代与交付（0.1.0-dev.3）

本轮由Gemini继续UI，本协作负责API/状态/存储/Media3服务，用户明确要求并行开发。新增接线见[数据合同](Android-Data-Contract.md)，需求/风险/历史证据见[数据任务](tasks/android-data-20261112.md)。

- versionName `0.1.0-dev.3`、versionCode 3；143/143自动测试、assembleDebug与Lint通过，Lint 0错误/40警告。
- 构建命令：`./android/gradlew.bat --project-dir android --console=plain :app:testDebugUnitTest :app:assembleDebug :app:lintDebug`；日志 `android/build/data-r2-queue-final.log`。
- fresh R2最后两项修复为Unicode批量解析按UTF-8≤60KiB/每批≤200，以及当前身份队列保存notice持续传递并保留部分恢复提示；4文件delta和38文件固定快照在 `android/build/review-data-r2-queue-20261112/`，待同一审查者聚焦关闭。
- 最终测试APK副本 `android/build/deliverables/FlareTune-0.1.0-dev.3-20261112-debug.apk`，16,948,244字节，SHA-256 `90CDB1B93BA69B38A9A9415304690262C85B0645282A9C38F3423D9C72F9F20F`。debug v1/v2签名验证与16KB对齐检查通过，签名日志 `android/build/data-r2-apk-signature.log`。
- 用户已授权达到本轮本地测试与聚焦复核标准后覆盖更新现有平板，使用真实MainActivity；不卸载、不清数据、不改设备安全设置。部署回执见任务，不把这份准备说明当已经安装。
- 现已实现歌词/译文状态、个人歌单读取、完整收藏、实际播放历史、分类及持久队列。译文显示设备保存、默认关闭；显示开关不请求生成。
- 队列恢复是重新验证并解析媒体后的暂停恢复；force-stop只恢复最后提交快照。旧dev.2未保存的队列不能从历史倒推。真实音频、蓝牙/锁屏/后台、歌词实例联调与UI视觉验收仍分别记录。

此前43/85/131/139项各轮结果不被这次143项取代为“全部真机通过”；它们保留对应输入与原失败/限制。远程WIP备份由独立协作者按用户指定仓库执行，不是正式发布，不包含APK、签名密钥、私有日志或账号资料。

## UI阶段与设备预览（0.1.0-dev.2，历史）

主界面采用 Spotify 的内容层级和实际窗口适配，全屏播放器采用 Apple 的排版和中性播控。新增完整专辑读取/播放，复用既有接口。结果见 [本轮任务](tasks/android-listening-ui-20261112.md)：43项测试通过、Lint 0错误/20警告、独立复核通过，设备已显示本地预览的主页和播放器。

当前交付：`android/build/deliverables/FlareTune-0.1.0-dev.2-20261112-debug.apk`，SHA-256 `548025A9742FCFCF2BB5B5158553A845CA291EC2FB234B96E6FA2044AE3697DD`。上一轮APK与证据保留为历史，不代表本轮界面。

持续查看修改：

```powershell
cd C:\git\FlareTune-Client
.\scripts\watch-android.ps1 -Preview
```

脚本每500ms检查main/debug源码与关键构建配置，合并短时间连续改动后增量构建、覆盖安装并重启预览。Ctrl+C停止。多设备时用 `-Serial`；只有一个已授权设备时自动选择。已实际验证源变更检测及 `-Preview -Once` 单次部署；当前没有后台常驻watcher。

```powershell
.\scripts\watch-android.ps1 -Preview -Once
.\scripts\deploy-android.ps1
```

第一条只更新一次预览；第二条更新并打开真实客户端。默认构建最新源码，`-SkipBuild` 仅部署已生成APK。构建失败保留已安装版本；设备拒绝安装就停止，不自动重试或改设备安全设置。

这是覆盖更新后重启，会保留磁盘登录资料并重置内存页面；dev.3按当前有效账号恢复已提交的持久队列并保持暂停，旧版未保存队列不能从历史倒推。**真正的Compose实时修改使用Android Studio Live Edit**：本机尚未装Studio；现有AGP/Compose与API37设备满足文档下限，需在Studio运行工程并启用Live Edit。它主要支持函数体内UI修改，新增方法/改签名等需要重新运行，不能把命令行覆盖部署称为Live Edit。[官方说明](https://developer.android.com/develop/ui/compose/tooling/iterative-development)

预览入口只包含在debug源集中，使用独立进程、生成封面与本地样例状态；不会真实播放音频或使用账号。“打开应用”进入真实客户端。已核release合并清单排除该入口，未发布正式版本。真实音乐、锁屏、蓝牙和后台验收仍待完成。

## 第一轮功能基线验证（历史）

本轮首版采用已有研究的播放器构图、细进度条、悬浮播放条与排版层级；先复用成熟组件完成链路，用户在首版后参与细节优化。设计规则见 [设计入口](Design-Brief.md)，范围与证据见 [首版任务](tasks/android-listening-foundation-20261112.md)。

- 版本 `0.1.0-dev`，Debug APK 16053929 字节，SHA-256：`3E0A4991A30458506D4DDCCD211F36645BC1D5A23F9587A27B6DAE3677E101B4`。
- 带日期交付副本：`android/build/deliverables/FlareTune-0.1.0-dev-20261112-debug.apk`，不进入 Git。
- 最终构建、Lint、设备测试 APK 编译与 27 项 JVM/Compose 测试通过；Lint 0 错误、17 条已记录警告。
- 核心会话隔离修复已通过独立聚焦复核；原代码三项负向回归失败、恢复修复后三项通过。
- 最终主 APK 已成功更新到 Android17/API37 设备并启动；回读安装包与交付 SHA-256 一致。
- 额外自动测试包被设备安装限制拒绝，因此 instrumentation 未执行。真实账号/设备 Keystore/音频/锁屏/蓝牙/后台行为仍未验收，不应从安装成功推断。
- 当前已实现能力替代下方初始化时的骨架范围；下方旧大小、哈希和限制仅属于当时的历史证据。

自动验证命令：

```powershell
cd android
.\gradlew.bat :app:assembleDebug :app:assembleDebugAndroidTest :app:lintDebug :app:testDebugUnitTest
```

设备连接：

1. 在设备“关于”页面连续点击版本号，开启开发者选项。
2. 开启 USB 调试，用能传输数据的 USB 线连接 Windows；必要时选择文件传输模式。
3. 在设备弹窗允许此电脑调试；`adb devices -l` 应显示 `device`，`unauthorized` 表示还未授权。列表为空时检查线缆、USB 模式或厂商 Windows 驱动。
4. 执行上方安装命令，打开 FlareTune，在设备内填写有效 HTTPS 根实例地址和账号。

若终端找不到 adb：

```powershell
$adb = Join-Path $env:ANDROID_HOME 'platform-tools/adb.exe'
& $adb devices -l
& $adb install -r android\app\build\outputs\apk\debug\app-debug.apk
```

设备自动化测试仅在设备允许安装测试包时手动运行；不要求测试真实账号，检测到已有账号会跳过媒体 fixture。当前设备的拒绝记录见任务，不自动重试。

```powershell
adb install -r android\app\build\outputs\apk\androidTest\debug\app-debug-androidTest.apk
adb shell am instrument -w -r -e class com.flaretune.client.DevicePlaybackTest com.flaretune.client.test/androidx.test.runner.AndroidJUnitRunner
```

测试使用 [AndroidX Test 稳定依赖](https://developer.android.com/jetpack/androidx/releases/test) runner 1.7.0 / ext.junit 1.3.0；设备步骤见 [Android 官方设备连接说明](https://developer.android.com/studio/run/device)。

## 初始化验证（历史）

目标：真实生成包含 Kotlin / Compose 启动页及 Media3 服务的 debug APK，并检查清单、签名、对齐和 Lint。

2026-11-12：ANDROID-BOOTSTRAP-20261112 实施与本地构建完成；本次为 R1 工程初始化，已做差异自查，未进行正式发布。

- 从仓库根目录执行 `scripts/build-android.ps1 -Lint`：最终 `BUILD SUCCESSFUL in 10s`，48 项任务（23 执行、25 已是最新）。
- Android Lint：0 错误、5 条版本更新提示（Gradle、AGP、Compose BOM、Kotlin Android / Compose Compiler）；保留当前经过验证的 API 36 兼容组合。
- APK 清单中应用 ID 为 `com.flaretune.client`，启动入口为 `MainActivity`，Media3 播放服务为非导出服务；minSdk 23、targetSdk 36。
- 实际 DEX 中包含启动 Activity、Compose 主题、PlaybackService 和 ExoPlayer；APK 签名及 16KB ZIP 对齐检查通过。
- APK 大小：15626744 字节；SHA-256：`287678F4C753485FA8D29512E363D3E27CA9CF203F7752D113E07190F0A9BC87`。
- Android / 构建脚本源文件快照 SHA-256：`807DD9101DC3B7AF85EB031A4380FF14ECEBC65EB93D0EC20C8D5D63381E879D`（22 个文件，不含本机 SDK 路径、构建输出及研究材料）。
- 首轮 APK 已生成，但 Lint 因本机 `local.properties` 中冒号未转义失败；修复后重跑通过。首轮失败记录保留于 `android/build/bootstrap-build.log`，最终记录为 `android/build/bootstrap-build-final.log`。
- 签名记录 `android/build/apk-signature-final.log`、清单摘要 `android/build/apk-badging-final.log`、产物与源文件摘要 `android/build/bootstrap-verification.json` 均为被 Git 忽略的本地产物。
- 初始化清单关闭自动备份，并声明云备份及安卓设备迁移的排除规则；这是当前骨架的配置事实，未通过真实备份/恢复流程验证。
- 构建中存在旧 SDK XML 解析兼容提示及 debug 原生库未剥离符号提示；本次编译、打包和签名已完成，后续更新构建链时再核对。
- 当前没有连接的安卓设备；启动页截图、触控、实际音频、系统播控和后台行为均未验证。
- 远程仓库保持未配置；本轮工程改动留在本地工作区，既有研究与忽略规则保留。

## 官方依据

- [AGP 8.13 兼容性及 Kotlin 2.3 支持](https://developer.android.com/build/releases/agp-8-13-0-release-notes)
- [Kotlin 2.x 的 Compose Compiler 配置](https://developer.android.com/jetpack/androidx/releases/compose-kotlin)
- [Media3 稳定版与接入说明](https://developer.android.com/jetpack/androidx/releases/media3)
