# ANDROID-UI-20261112

目标：基于已打通的听歌链路，交付 Spotify 主界面与自适应底栏、Apple 风格全屏播放器，并让用户在设备上直接查看迭代。

依据：2026-11-12 用户否定上一版界面完成度，明确上述参照；随后询问热更新。首版自主交付、复用成熟实现的授权继续有效。设计见 [Design Brief](../Design-Brief.md)，功能基础见 [上一轮任务](android-listening-foundation-20261112.md)。

范围：主页/搜索/音乐库，现有只读专辑列表和完整详情，歌曲/收藏浏览，播放器/队列重构，Media3播放器音量，调试界面预览及增量部署。非目标：推荐算法、歌词、离线下载、管理功能、正式发布。相邻服务端与助手研究原件只读；未推送。

## 当前状态与交付

适用性：active。本轮实现、本地验证与独立复核完成，已在设备展示调试预览；用户视觉验收和真实实例音频验收尚未确认，不把自动测试替代为用户认可。

版本：0.1.0-dev.2 / versionCode 2。交付：`android/build/deliverables/FlareTune-0.1.0-dev.2-20261112-debug.apk`。
APK 16866024 字节；SHA-256 `548025A9742FCFCF2BB5B5158553A845CA291EC2FB234B96E6FA2044AE3697DD`。
50个工程/脚本/属性文件摘要：`09D4F14EC19E3ED55E693BF4F01D470DB31069C6FAFD511E88D57BD9F99AB215`，详细记录在被忽略的 `android/build/ui2-source-SHA256.txt` / `ui2-verification.json`。

主要实现：

- ListeningShell：头像菜单、真实内容封面架、主页/搜索/音乐库三入口、完整专辑详情、歌曲/收藏/专辑筛选。宽窗口导航与播放条并排，窄窗口上下排列。
- NowPlayingScreen：独立全屏、大封面、中性三键播控、细进度、实际gain控制、随机/循环/队列，宽屏双列。背景切换收进更多菜单。
- 复用 GET /api/albums?limit=20&offset=N 和 /api/albums/{id}，完整曲目可作为队列；URL/凭据仍走原受控会话。
- 专辑列表与详情分别有请求revision，并检查identity、当前连接与active状态；关闭、退出、切换后不接受旧结果。
- debug PreviewActivity 位于独立 :ui_preview 进程，以本地16首/8专辑/生成封面展示同一套真实Compose组件；不调用真实凭据、网络或音频。Application只在真实connections首次被取用时创建会话及注册全局失效监听。
- deploy/watch脚本支持 -Preview。自动更新采用构建、覆盖安装、重启；保留磁盘数据，重置当前内存页面/队列，不是真正Live Edit。拒绝安装会停止，不卸载或更改设备安全设置。

## 验证与独立复核

- `ui2-validation-fixed.log`：BUILD SUCCESSFUL in38s；43项全部通过：14 Session/crypto、7 Album HTTPS合同、11状态、11 Compose。0失败/错误；Lint 0错误、20警告（12版本更新、5KTX、2未使用资源、1Modifier参数）。
- 第一轮41项中1项失败来自对固定album_back误用滚动测试，改为可见性检查+点击；之后补了Android Back先收播放器再关专辑的行为检查。
- 首次独立复核发现P2：取消歌曲加载切专辑后保留loading=true，刷新按钮持续禁用。现已同步清loading；负向副本撤回单行修复后新用例1失败（`ui2-loading-negative.log`），恢复后1通过（`ui2-loading-restored.log`）。
- 最终56文件快照聚合 `CBC4839CAA68DF5B325BBAB4CB88BC1BB80AC0ECE1EA1B7B2FBA138E69B58E4A`，独立聚焦回读关闭P2，未发现新增可行动问题；核对了专辑隔离、完整队列、debug导出边界和lazy全局监听。快照位于 `android/build/review-ui2-fixed-20261112`；后续仅更新本轮状态文档。
- Robolectric真实组件渲染已目视对照：390dp窄屏、1280dp宽屏、320dp浮窗、360dp/200%中文字号；主页、搜索、详情、错误、播放器、菜单背景切换均有截图和交互证据。图形封面为本地生成fixture。
- Android17/API37设备：`watch-android.ps1 -Preview -Once` 增量构建1s、安装Success、PreviewActivity启动Status ok。设备熄屏锁定后仅唤醒；用户自行解锁后确认实际主页与全屏播放器画面，保存于 `ui2-device-preview-visible.png` / `ui2-device-player.png`。预览AndroidRuntime错误筛查为空。
- 监听源检测经临时探针证明能发现main/debug变更并回到原基线；单次构建/安装/重启链已执行。持续watcher未常驻启动，未宣称已长跑。
- Debug APK v1/v2签名与16KB ZIP对齐通过。processReleaseMainManifest通过，实际release合并清单无PreviewActivity；未构建或发布正式分发包。

## 隐私与边界

新源和fixture无真实凭据常量；没有增加日志正文、遥测或第三方私有运行库。研究原APK、个人本地配置、签名私钥、截图及构建输出均不入Git。预览忽略Intent输入，Coil所有分支直接返回内存图片，打开真实应用仅固定MainActivity，不转发输入；release源集不包含预览入口或样例数据。部署保持同应用更新，不清用户数据。

未验证：真实实例登录/会话恢复、真实音乐播放与听感、锁屏/蓝牙/音频焦点、后台稳定性、设备Keystore迁移、长时间耗电和持续watcher运行。此前额外androidTest包被设备拒绝，未重试绕过；本轮真实设备预览不算这些音频/账号检查通过。

后续：在真实实例环境完成音频验收，用户再参与细节优化。恢复先核README、Design Brief、Product Spec、当前工作树与上述源码摘要，保留助手研究及服务端他人改动。旧APK副本保留，后续同签名更新；不要用卸载代替更新。
