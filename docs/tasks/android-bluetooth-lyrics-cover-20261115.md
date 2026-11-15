# ANDROID-BLUETOOTH-LYRICS-COVER-20261115：蓝牙歌词与切歌封面缓存共享

## 目标与范围

- **目标**：依据 2026-11-15 用户新增需求与确认边界，补齐两项系统媒体播放体验能力：
  1. **蓝牙歌词（AVRCP 元数据逐行同步）**：在「设置 → 播放」新增三态偏好，在允许生效的播放期间将系统媒体/蓝牙歌名位替换为当前句原文歌词、歌手位展示 `原歌名 - 原歌手`，同时保证 App 内界面、播放队列与播放历史零污染，并在后台/息屏期间持续随行推进；
  2. **切歌系统封面防闪烁**：统一 `PlaybackService` `MediaSession` 与客户端 UI 的图片加载与缓存链路（共享 `PlaybackRuntime.imageLoader` 的内存缓存与账号隔离磁盘缓存），并在队列邻曲预热，避免切歌瞬间系统通知/媒体卡片先闪退为 `ic_notification` 默认图标再加载封面。
- **当前适用性**：`active (implemented & independently reviewed APPROVED; awaiting real-device Bluetooth/notification walk-through)` — 已完成代码实现、单测/UI测试/Lint/APK 构建验证及独立只读审查（`APPROVED`）；真实蓝牙车机 AVRCP 刷新延迟、锁屏通知与切歌体感仍需真机实测。
- **风险与路线**：系统播放服务与本地偏好扩展（**R1** — 客户端 `PlaybackRuntime`、`PlaybackService`、设置偏好及测试改造，不涉及鉴权、凭据、服务端接口或不可逆数据写入）。
- **非目标**：不实现 AVRCP 逐字滚动/KTV 染色、不在未连接音频或离线未取词歌曲上强行本地生成歌词、不批量全队列预载上百首封面、不申请 Android `BLUETOOTH_CONNECT` 运行时权限、不新增独立桌面歌词悬浮窗。

---

## 已确认产品与设计边界（2026-11-15 用户确认）

1. **蓝牙歌词三态策略与设置入口**：
   - 在「设置 → 播放」新增「蓝牙歌词」三态选择，持久化至 `playback_display_v1`：
     - `OFF`（**关闭，默认值**）：不做系统歌词元数据映射；
     - `BLUETOOTH_AUDIO`（**仅蓝牙音频连接时**）：通过系统 `AudioManager.AudioDeviceCallback` + `getDevices(GET_DEVICES_OUTPUTS)` 检测当前是否存在已连接的蓝牙音频输出设备（`TYPE_BLUETOOTH_A2DP`、`TYPE_BLE_HEADSET`、`TYPE_BLE_SPEAKER`、`TYPE_BLE_BROADCAST`、`TYPE_HEARING_AID`），仅蓝牙已连接时激活，断开即刻回退（无需敏感附近设备权限）；
     - `ALWAYS`（**始终开启**）：无论是否连接蓝牙音频均生效，兼容不走标准音频输出路由的蓝牙手环/手表通知同步与本地锁屏直观自测。
2. **元数据映射格式与回退原则**：
   - 激活条件满足、处于真实播放态（`isPlaying`）且当前歌曲拥有有效整行同步歌词并落在非空白有效行上时：
     - 系统可见 `title` = **当前句原文歌词**（不强行拼接译文，防止窄屏/车载截断；仅整行切换时更新，附轻量防抖，不做逐字高频刷新）；
     - 系统可见 `artist` = **`原歌名 - 原歌手`**（无歌手信息时仅 **`原歌名`**）。
   - 处于暂停态、前奏/间奏空白行、纯音乐（`instrumental`）、暂无歌词/加载失败、开关关闭或仅蓝牙模式下蓝牙音频已断开时，自动回退显示真实 `原歌名` 与 `原歌手`。
   - **App 内部零污染约束**：底层 `ExoPlayer` 曲目及元数据 `extras`（包含 `original_title` / `original_artist` 或等效原字段）保持完整原值，`PlaybackSnapshot`、队列恢复、播放历史（`PlayedTrackTracker`）、合格收听统计（`QualifiedListeningTracker`）、Mini 播放器及全屏播放页一律读取原始歌曲歌名与歌手。
   - **客观限制透明**：由于 Android 通知栏、锁屏控件与蓝牙 AVRCP 共用 `MediaSession.MediaMetadata`，激活期间系统媒体控件亦会同步显示歌词；刷新延迟、长度截断及部分老旧车机仅首帧读元数据的限制由硬件固件决定。
3. **后台/息屏驱动机制**：
   - 将当前会话激活态 `LyricsCoordinator` 收敛或共享至 `PlaybackRuntime` 归当前 `ActiveConnection` 拥有（UI 与 `PlaybackService` 共用同一份词库请求与状态流，切换/失效账号同步清理，不重复请求后端），`PlaybackService` 在蓝牙歌词可能激活且正在播放时利用既有 500ms 轮询推进歌词行索引并刷新系统 `MediaMetadata`，锁屏/切后台不中断。
4. **切歌 Cover 缓存共享与相邻预热**：
   - 实现自定义 Media3 `BitmapLoader`（`CoilBitmapLoader`），替换原 `PlaybackService` 中彼此隔离的 `CacheBitmapLoader(DataSourceBitmapLoader(...))`，直接复用 `PlaybackRuntime.imageLoader`（遵守当前账号 `coil_image_cache_v2` 隔离、`respectCacheHeaders(false)` 与 `512px` 合理上限）。
   - 若对应封面已在 Coil 内存缓存命中（包含预热与 UI 已加载缓存），直接返回同步完成的 `Futures.immediateFuture(bitmap)`，使 Media3 第一帧通知直接带图下发而不先显示占位小图标。
   - 切歌或播放队列/随机/循环模式变化时，后台顺手通过 `imageLoader` 预加载当前、下一首（`nextQueueIndex`）与上一首（`previousQueueIndex`）封面。

---

## 实现清单、审查结论与验证实证

1. **设置偏好与状态契约**（[TranslationPreferences.kt](../../android/app/src/main/java/com/flaretune/client/data/TranslationPreferences.kt)、[ClientContracts.kt](../../android/app/src/main/java/com/flaretune/client/ui/ClientContracts.kt)、[ClientViewModel.kt](../../android/app/src/main/java/com/flaretune/client/ClientViewModel.kt)、[AccountSidebarAndSettings.kt](../../android/app/src/main/java/com/flaretune/client/ui/AccountSidebarAndSettings.kt)、`values/strings.xml`、`values-zh-rCN/strings.xml`）：
   - 已落地 `BluetoothLyricsMode`（`OFF` 默认 / `BLUETOOTH_AUDIO` / `ALWAYS`）持久化读写、写入失败回滚与 `STORAGE_FAILED` 提示；设置页「播放」卡片新增 `SettingsBluetoothLyricsSelectorRow` 三态单选列（`>= 48.dp` 触控高度、适配 200% 字号放大）。
2. **运行时共享歌词与蓝牙路由监测**（[PlaybackRuntime.kt](../../android/app/src/main/java/com/flaretune/client/playback/PlaybackRuntime.kt)、[BluetoothLyricsPolicy.kt](../../android/app/src/main/java/com/flaretune/client/playback/BluetoothLyricsPolicy.kt)）：
   - `PlaybackRuntime` 托管共享 `lyricsCoordinator`（切号/登出清理，VM 非所有方 `onCleared()` 不误清）；`PlaybackService` 注册/注销免权限 `AudioDeviceCallback` 并通过 500ms 周期任务在息屏/后台持续推进歌词行。
3. **系统媒体元数据映射与 `CoilBitmapLoader` 预热**（[PlaybackService.kt](../../android/app/src/main/java/com/flaretune/client/playback/PlaybackService.kt)、[PersistentTransportPlayer.kt](../../android/app/src/main/java/com/flaretune/client/playback/PersistentTransportPlayer.kt)、[AndroidPlaybackGateway.kt](../../android/app/src/main/java/com/flaretune/client/playback/AndroidPlaybackGateway.kt)、[QueuePlaybackOperations.kt](../../android/app/src/main/java/com/flaretune/client/playback/QueuePlaybackOperations.kt)、[CoilBitmapLoader.kt](../../android/app/src/main/java/com/flaretune/client/playback/CoilBitmapLoader.kt)、[CoverArtworkCache.kt](../../android/app/src/main/java/com/flaretune/client/playback/CoverArtworkCache.kt)、[PlayerTransition.kt](../../android/app/src/main/java/com/flaretune/client/ui/PlayerTransition.kt)）：
   - 在 `MediaItem` `extras` 与 `toSingleNextBundle` 中保留 `original_title` / `original_artist` 确保内部完全零污染；`PersistentTransportPlayer` 在系统发布层覆写 `currentMetadata` 并附 `350ms` 节流与 `0ms` 立即回退；
   - `CoilBitmapLoader` 内存命中同步返回不可变软件位图，切歌及队列拓扑变更由 `CoverArtworkPrewarmer` 预热当前及前后邻曲封面。
4. **验证与独立审查记录（2026-11-15）**：
   - 新增与扩展测试：[BluetoothLyricsPolicyTest.kt](../../android/app/src/test/java/com/flaretune/client/BluetoothLyricsPolicyTest.kt)、[CoilBitmapLoaderTest.kt](../../android/app/src/test/java/com/flaretune/client/CoilBitmapLoaderTest.kt)、[TranslationPreferencesTest.kt](../../android/app/src/test/java/com/flaretune/client/TranslationPreferencesTest.kt)、[AccountSettingsStateTest.kt](../../android/app/src/test/java/com/flaretune/client/AccountSettingsStateTest.kt)、[SidebarAndSettingsUiTest.kt](../../android/app/src/test/java/com/flaretune/client/SidebarAndSettingsUiTest.kt)。
   - 独立只读审查结论：**`APPROVED`**（0 Critical / 0 Major；2 条非阻塞 Minor：`RESTORING` 阶段初始连接前清理共享协调器、以及 `AudioDeviceInfo` API 保护常量的 `InlinedApi` Lint 提示）。
   - 在 `c:\git\FlareTune-Client\android` 执行 `.\gradlew.bat :app:testDebugUnitTest :app:lintDebug :app:assembleDebug --no-daemon`：**`BUILD SUCCESSFUL`**，50 个测试套件共 **577** 项单测与 Compose UI 测试全部通过（`0 failed, 0 errors, 0 skipped`），`:app:lintDebug` **0 Error**（108 Warning / 1 Hint），`:app:assembleDebug` 通过。
   - **待真机确认边界**：蓝牙 AVRCP 实际车载/耳机端刷新速度与长句截断兼容、系统媒体通知切歌零小图标闪烁体感仍需在连接实体蓝牙与手机/平板端做一次真机走查。
