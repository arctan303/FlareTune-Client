# FlareTune Client

FlareTune 官方音乐客户端，专注于音乐播放体验。

## 项目方向

- 首先覆盖 Android 设备，目标产物为 APK。
- 首版优先满足维护者本人的日常听歌需求。
- 后续根据实际需要考虑 iOS 或其他平台。

## 当前状态

当前已部署开发包为 **0.1.0-dev.9 / versionCode 9**（dev.8基线＋本轮系统切歌修复；共同目录另有Gemini在途页面改造，不纳入此APK），Kotlin + Jetpack Compose + Media3，工程位于 `android/`。本轮补齐Web普通听歌能力及可用基础UI，沿用相同宿主接口和现有Spotify主界面/Apple播放器方向，供Gemini继续迭代视觉。

- 收藏/取消收藏、创建/改名/简介/删除个人歌单、单首或多首加入歌单、移除歌曲、完整成员拖动/上下排序、歌单封面与资料库排列。
- 歌曲/专辑/歌手检索与详情、真实每日推荐/焦点音乐人、完整列表播放、下一首/加入队尾、队列删除/排序/清空与暂停恢复。
- 实际播放历史、继续收听与队列各自独立；历史时间/单删/清空、设备最近搜索、可靠有效收听上报与常听统计。
- 统一歌词、纯音乐与译文状态继续保留；译文显示和播放器音量为设备偏好，不改服务端来源或AI配置。
- 前后台/系统重建恢复同账号原页；系统媒体面板支持播放顺序与收藏，非空队列上一首/下一首在首尾保留，操作遵循原Timeline规则。
- 统一搜索与歌曲菜单保留YT真实歌手/专辑关联；全屏导航收起播放器，具体专辑版别的播放标记和点击上下文准确，关联经队列/历史恢复。

本次dev.9固定候选 **356/356自动测试、assembleDebug、Lint通过（0错误 / 64警告 / 1提示）**。覆盖本地TLS接口合同、身份/迟到响应、修订冲突与幂等、完整不可播放成员、播放/持久化和Compose操作；渲染含窄屏、宽屏、200%字号。修复和独立复核、负对照与限制见[听歌能力补全任务](docs/tasks/android-listening-capabilities-20261112.md)，UI/数据接线见[Android数据合同](docs/Android-Data-Contract.md)。

本地APK：`android/build/deliverables/FlareTune-0.1.0-dev.9-20261113-debug.apk`；SHA-256 `5AD28EDF381B37B3673C2E8B0CB16BF78C4C94363A69B0FAA5BC6A18CCBA5BA2`。这是debug开发测试包，未正式发布；[本轮系统面板修复、图标草案与USB投屏](docs/tasks/android-system-transport-20261113.md)已记录。本轮[歌曲来源与版本修复](docs/tasks/android-song-relations-20261113.md)已验证。YT歌曲检索过滤MV仅有本地插件补丁，原YT歌词HTTP500仍在服务端排查，不能据客户端安装宣称已恢复。**2026-11-13 dev.9/code9已覆盖安装到同一平板，回读版本/已安装APK摘要一致，MainActivity已启动且任务栈仅一个实例；未卸载或清data。** 本轮已纳入用户确认完工的Gemini界面更新，修复/聚焦复核/固定快照与部署边界见[页面与系统媒体任务](docs/tasks/android-lifecycle-media-20261113.md)。 歌词自动滚动、灰/白翻译按钮及服务端翻译资格修复见[本轮任务](docs/tasks/android-player-lyrics-20261113.md)，dev.4原交付与摘要保留在补全任务。 开发挂机阶段只做本机测试，未向真实站点写入测试数据。 用户此前已确认歌词恢复；新能力的真实听感、锁屏/蓝牙、后台耗电与厂商兼容性仍需设备验证。

本次先从已安装dev.4基线743c213建立独立工作树隔离修复，随后用户要求合入双方改动，并确认Gemini停写。独立修复b29eecf与Gemini当轮11文件快照已组成dev.6共同候选；后续双方回到共享工作区 `FlareTune-Client` / 分支 `codex/android-client-backup-20261112` 的同一Git基线，由用户调度修改范围。原隔离工作树只保留恢复与构建证据，不作为另一条并行开发基线；原Gemini快照保留具名恢复stash。具体整合提交与远程备份以Git记录和本轮任务为准，均不等同正式发布。持续预览可手动运行 `./scripts/watch-android.ps1 -Preview`，真实客户端覆盖更新使用 `./scripts/deploy-android.ps1`；当前没有常驻watcher；本轮从固定整合快照以ADB覆盖安装已验证APK。它们属于增量构建后覆盖更新，不是Android Studio Live Edit。

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
