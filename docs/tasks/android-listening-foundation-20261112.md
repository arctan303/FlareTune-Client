# ANDROID-LISTENING-20261112

目标：交付可安装的原生首版，连接实例、登录、曲库/搜索/收藏浏览、连续播放和系统播控。

范围：[Product Spec](../Product-Spec.md)。设计：[Design Brief](../Design-Brief.md)。依赖：既有 API、JDK 21、API 36、固定 Gradle/Compose/Media3。服务端与既有研究只读；不推送、不改生产、不向他人发消息。

用户约定（2026-11-12）：复用成熟组件，前端参考仓库 Apple Music / Spotify 等研究；首版自主完成，用户在首版之后参与细节与前端优化。该约定仅适用于客户端本轮首版，不写成跨项目规则。

## 状态与交付

当前适用性：active。功能基础的本地实施与交付完成；用户否定其 UI 完成度，UI 范围由 [ANDROID-UI-20261112](android-listening-ui-20261112.md) 接替；自动检查通过，核心 R2 审查通过；真实实例与设备音频验收保留为后续工作。未正式发布、未配置远程 Git，本轮改动保留本地工作区。

- Kotlin / Compose 原生连接、改密、曲库/搜索/收藏、迷你/全屏播放器、队列已接通。
- Media3 统一播放器与系统会话；OkHttp 复用既有实例协议；Coil 共用会话网络加载封面。
- Cookie 来源隔离、加密持久化、会话恢复、退出与旧结果拒绝已实现。密码不持久化。
- 当前播放列表、暂停/继续、上下首、定位、随机与循环均连接真实控制器回调。
- 界面按已有研究调整构图；本轮未新增推荐算法、歌词、离线下载或歌单写入。

交付文件：`android/build/deliverables/FlareTune-0.1.0-dev-20261112-debug.apk`。标准输出：`android/app/build/outputs/apk/debug/app-debug.apk`。

APK：16053929 字节，SHA-256 `3E0A4991A30458506D4DDCCD211F36645BC1D5A23F9587A27B6DAE3677E101B4`。
41 个工程/脚本/属性文件摘要：`60B25AF17307DDD33D3555EB44F4EC51140E278A9CA21635F4DDB81E8A22EA97`。详细条目在被忽略的 `android/build/listening-source-SHA256.txt`，汇总为 `android/build/listening-verification.json`。

## 验证证据

最终命令：在 android 目录执行 `./gradlew.bat --console=plain :app:assembleDebug :app:assembleDebugAndroidTest :app:lintDebug :app:testDebugUnitTest`。

- `android/build/listening-validation-final.log`：BUILD SUCCESSFUL in 38s，83 项任务。
- JUnit XML：14 项 HTTPS/接口/隔离/加密 + 6 项状态 + 7 项 Compose 交互，共 27 项；0 失败、0 错误、0 跳过。
- Lint：0 错误、17 警告（12 条版本更新、3 条 KTX 建议、2 条 Compose 配置/参数建议）；保持已验证的 API 36 依赖组合。
- Robolectric SDK35 原生 Canvas：连接页、曲库、错误态、明暗播放器、生成封面背景与纯色切换、360dp/200% 中文字号均已渲染并目视检查；Slider 可访问定位会调用 60000ms seek。这些是模拟截图，不是真机听感证据。
- APK 应用 ID、SDK23/36、启动入口和私有 MediaSessionService 清单检查通过；v1/v2 签名及 16KB ZIP 对齐通过，记录为 `listening-apk-*-final.log`。
- 设备型号 25079RPDCC、Android17/API37：首次安装冷启动 Status ok；最终更新 Success，最终 APK 冷启动 Status ok（477ms）；回读设备 base.apk 的 SHA-256 与交付 APK 完全相同。最终应用 AndroidRuntime 错误筛查为空，不能据此宣称所有运行路径无错误。

首轮测试失败包含 JUnit 返回类型、TLS fixture 的 localhost/127.0.0.1 SAN、Robolectric PixelCopy 超时；均已修正，历史日志保留。最终 Compose 截图使用实际 View/Canvas 绘制，不使用超时的 PixelCopy。

## R2 核心审查与修复

风险为凭据与账号隔离，要求独立 reviewer。首审固定 45 文件快照，发现三个问题：

1. 恢复存档读取与代次分配分两次加锁，旧恢复可能越过退出。
2. 旧改密完成后可能清除新连接；退出捕获连接与清除不原子。
3. 新搜索/标签首批失败后可能沿用旧分页游标和旧结果。

现已分别改为：恢复读取/票据同锁；改密绑定连接+generation、过期返回 RETIRED，退出同锁捕获/退役/清除；新上下文清空旧结果及分页资格。

新鲜独立 reviewer 对固定 23 文件核心快照完成聚焦复核，三项关闭，未发现相邻隔离问题。输入摘要 `3127D89FB0F76B5DA5472E0F9BEB1D7A6BDDBD1FE1C4136388ADCF067272FF46`；本地快照为 `android/build/review-listening-fixed-20261112`。之后仅修改设备测试 Range 夹具及状态文档；核心运行时代码未改变。最终 UI 由主会话差异自查与渲染/交互验证，不扩称独立全量 UI 审查。

负向对照：隔离副本仅撤回 Manager/VM 修复并保留新测试，3 项回归全部失败（`listening-negative-regressions.log`）；恢复修复后三项通过（`listening-restored-regressions.log`及对应 XML）。恢复 HTTP 在途后退出不复活有行为回归；原两锁极窄间隙没有确定性重现，修复依据为静态锁范围和代次提交检查。

## 隐私、设备缺口与后续

本轮 Android 源码、测试、构建配置、脚本、受影响文档和截图已检查：敏感词命中为字段、协议与临时 fixture；未发现真实凭据常量、请求正文日志、遥测 SDK 或第三方账号注入。未将研究原始 APK/整个私有目录作为本轮审计范围。密码不落盘；备份关闭；调试签名由本机工具生成，不提交私钥。

设备测试 APK 已编译，包含生成静音 WAV 的 HTTPS/Media3 控制、后台/通知以及独立临时 Keystore key 检查。安装返回 `INSTALL_FAILED_USER_RESTRICTED: Install canceled by user`，instrumentation 未执行；没有重试、绕过限制或更改设备安全设置。设备 fixture 的 Content-Range 插值问题已修正，并纳入最终编译。

尚未验证：真实实例登录与会话磁盘恢复、实际音频、锁屏/蓝牙/焦点中断、后台连续播放、设备 Keystore/备份迁移、耗电与长期稳定性。17 条 Lint 警告和测试 API 的弃用提示仍保留。上述不算通过，也不阻止本地 APK 交付。

下一步在允许测试包的设备或实际账号环境中验证这些路径；使用者在应用内输入凭据，无需把密码写入聊天或仓库。后续细节/前端优化按用户体验反馈开展。恢复工作先读 README、Product Spec、Design Brief 与实际工作树，保留 research 和服务端他人修改。

保留当前带日期的 APK 作为本地恢复基线；后续同签名 APK 可覆盖更新。不要以卸载代替更新，因为卸载会清除应用数据。正式发布的签名、分发和生产操作需另行授权。
