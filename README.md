# FlareTune Client

FlareTune 官方音乐客户端，专注于音乐播放体验。

## 项目方向

- 首先覆盖 Android 设备，目标产物为 APK。
- 首版优先满足维护者本人的日常听歌需求。
- 后续根据实际需要考虑 iOS 或其他平台。

## 当前状态

当前源码与本地开发包为 **0.1.0-dev.4 / versionCode 4**，Kotlin + Jetpack Compose + Media3，工程位于 `android/`。本轮补齐Web普通听歌能力及可用基础UI，沿用相同宿主接口和现有Spotify主界面/Apple播放器方向，供Gemini继续迭代视觉。

- 收藏/取消收藏、创建/改名/简介/删除个人歌单、单首或多首加入歌单、移除歌曲、完整成员拖动/上下排序、歌单封面与资料库排列。
- 歌曲/专辑/歌手检索与详情、真实每日推荐/焦点音乐人、完整列表播放、下一首/加入队尾、队列删除/排序/清空与暂停恢复。
- 实际播放历史、继续收听与队列各自独立；历史时间/单删/清空、设备最近搜索、可靠有效收听上报与常听统计。
- 统一歌词、纯音乐与译文状态继续保留；译文显示和播放器音量为设备偏好，不改服务端来源或AI配置。

本机 **233/233自动测试、assembleDebug、Lint通过（0错误 / 52警告 / 1提示）**。覆盖本地TLS接口合同、身份/迟到响应、修订冲突与幂等、完整不可播放成员、播放/持久化和Compose操作；渲染含窄屏、宽屏、200%字号。修复和独立复核、负对照与限制见[听歌能力补全任务](docs/tasks/android-listening-capabilities-20261112.md)，UI/数据接线见[Android数据合同](docs/Android-Data-Contract.md)。

本地APK：`android/build/deliverables/FlareTune-0.1.0-dev.4-20261113-debug.apk`；SHA-256 `945F9F8ECD973AA12910A0D16D9A793FD28BBE0F752BF48915038693ABDB52CF`。这是debug开发测试包，未正式发布。**2026-11-13 用户返回后明确授权更新，dev.4/code4已覆盖安装到平板并启动MainActivity，已安装APK摘要与本候选一致；未卸载或清data。** 开发挂机阶段只做本机测试，未向真实站点写入测试数据。 用户此前已确认歌词恢复；新能力的真实听感、锁屏/蓝牙、后台耗电与厂商兼容性仍需设备验证。

代码沿用已授权的远程备份分支 `codex/android-client-backup-20261112`；备份不等同正式发布，提交可从本轮任务与分支Git记录回溯。持续预览可手动运行 `./scripts/watch-android.ps1 -Preview`，真实客户端覆盖更新使用 `./scripts/deploy-android.ps1`；当前没有常驻watcher，本轮未执行这两项设备脚本。它们属于增量构建后覆盖更新，不是Android Studio Live Edit。

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
