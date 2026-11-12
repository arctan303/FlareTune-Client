# FlareTune Client

FlareTune 官方音乐客户端，专注于音乐播放体验。

## 项目方向

- 首先覆盖 Android 设备，目标产物为 APK。
- 首版优先满足维护者本人的日常听歌需求。
- 后续根据实际需要考虑 iOS 或其他平台。

## 当前状态

当前本地版本 `0.1.0-dev.3`（versionCode 3）：Kotlin + Jetpack Compose + Media3，工程位于 `android/`。主页/搜索/音乐库与全屏播放器由 Gemini 继续迭代 UI；本协作负责 API、状态、存储与播放支撑，两部分按用户要求并行进行。

已实现统一歌词/纯音乐与译文状态、个人歌单读取、完整收藏播放、设备实际播放历史、独立歌曲/专辑分类，以及保序保重复的队列编辑和持久恢复。重新启动先核验会话并解析媒体，再**暂停恢复**已保存队列；历史不能当队列兜底。译文显示默认关闭，可在设备保存开关，显示切换不触发生成。

当前输入143/143自动测试、APK与Lint通过（0错误/40警告），fresh R2两项队列发现已修，正在同一轮聚焦复核。此前43/85/131/139项结果各自保留历史，不表示真实声音、锁屏/蓝牙、后台稳定性或所有歌词已完成真机验收。用户报告的入库歌词失败正由主协作独立排查；已取得ready歌词资产，不能将未知失败显示为“没有歌词”。

交付APK：`android/build/deliverables/FlareTune-0.1.0-dev.3-20261112-debug.apk`；SHA-256 `90CDB1B93BA69B38A9A9415304690262C85B0645282A9C38F3423D9C72F9F20F`。这是debug开发测试包，未正式发布。用户已授权按本轮标准更新平板继续测试；覆盖安装状态见[数据任务](docs/tasks/android-data-20261112.md)。远程WIP备份已单独获授权，由备份协作者执行，不等同于发布。

持续查看修改：运行 `./scripts/watch-android.ps1 -Preview` 自动增量构建、覆盖更新并重启无账号的本地界面预览；运行 `./scripts/deploy-android.ps1` 更新真实客户端。当前没有常驻watcher。脚本属于覆盖更新，不是 Android Studio Live Edit；磁盘数据保留，已保存的新队列按上面的恢复合同处理。

## 开发入口

- [Android 数据与UI接线合同](docs/Android-Data-Contract.md)
- [Android 开发与构建](docs/Android-Development.md)
- [当前有效设计范围](docs/Design-Brief.md)
- [首版需求与范围](docs/Product-Spec.md)
- [本轮界面实现、验证与待验事项](docs/tasks/android-listening-ui-20261112.md)
- [第一轮功能基础](docs/tasks/android-listening-foundation-20261112.md)

## 设计研究

`research/` 下是对竞品前端美学的拆解（音流 / Spotify / Apple Music 的 APK 静态分析 + 截图测量），
以及一套可直接落地的设计系统提案：

- **播放器与界面预览（可交互）**：[`research/preview.html`](research/preview.html)
- 阅读入口：[`research/README.md`](research/README.md)
- 可视化报告：[`research/report.html`](research/report.html)
- 设计规格：[`research/docs/06-flaretune-client-design.md`](research/docs/06-flaretune-client-design.md)
- token 提案：[`research/tokens/flaretune-client.proposal.json`](research/tokens/flaretune-client.proposal.json)
