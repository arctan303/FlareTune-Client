# ANDROID-ROOT-CAUSE-PERF-UX-20261113

## 目标与范围

- **目标**：对 `C:\git\FlareTune-Client` 执行前端性能与交互体验的“治本”级架构重构与微交互补齐，彻底解决首页/发现/曲库上下滚动卡顿、顶栏定位胶囊跳转卡顿与手势冲突、底部 4 Tab 切换重建与卡顿、全屏播放器开关掉帧及逐字歌词高 GPU 消耗问题，并补全统一的触感反馈与横向磁吸体验。
- **当前适用性**：`implemented (Phase 1–4 implemented, verified & reviewed)`（2026-11-13 已依次完成 Phase 1–4 实施、全量单测与 Lint 验证，并通过 4 轮独立 `phase-reviewer` 审查；真机高刷手感与暗场模糊观感仍待实机走查）。
- **风险与路线**：产品与体验架构改造（**R1** — 客户端 UI / 状态流 / 单元测试可控可逆变更，需在 Phase 3/4 同步更新 [Design-Brief.md](../Design-Brief.md) 与 [PlayerLyricsMotionTest.kt](../../android/app/src/test/java/com/flaretune/client/PlayerLyricsMotionTest.kt) 中已被用户覆盖的旧合约）。
- **非目标**：
  - 不修改后端 API、认证或待后端完成的统计接口（如 `play-stats?days=N`）；
  - 不触碰或覆盖当前工作区内已有但与本任务无关的未提交修改；
  - 不执行 `git push` 或真机正式发布。

## 依据与已确认决策（2026-11-13 用户确认）

1. **顶栏遮罩路线（`supersedes` [android-frontend-polish-20261113.md](android-frontend-polish-20261113.md) 第 81 条 `FrostedHeaderScaffold` 实时模糊）**：
   - 将 `FrostedHeaderScaffold` 改为 **Spotify 式纯渐变半透明遮罩**，彻底移除 `rememberGraphicsLayer()` 全屏离屏录制、`CompositingStrategy.Offscreen`、`BlurEffect(20.dp)` 与 `BlendMode.DstIn`，让滚动全程零离屏模糊税且各 Android 版本表现一致。
2. **全屏播放器与歌词模糊质感及常亮策略（`supersedes` [android-frontend-polish-20261113.md](android-frontend-polish-20261113.md) 显式保留项 `keepScreenOn`）**：
   - 完整保留流光专辑背景模糊与苹果风逐行歌词模糊深度（保留 `0 / 0.5 / 1.2 / 2.2dp` 离散模糊档位契约）；
   - 通过底层架构重构消解卡顿：背景图改用降采样一次性预模糊缓存纹理，逐行歌词通过离散固定模糊层隔离 + 绘制层卡拉OK裁剪渲染；
   - **`keepScreenOn` 改为仅在真实播放且全屏播放器可见时生效**（`state.playing == true`），暂停或退出即恢复防耗电（同步更新单测与设计简报）。
3. **转场与微交互全量落地**：
   - 4 个主 Tab 状态保活 + 柔和切页 + 点按当前激活 Tab 平滑回顶；
   - 全屏播放器统一弹簧景深开关转场（下层页面随展开度轻微缩沉）+ MiniPlayer 上滑展开；
   - 列表/歌单/歌手/专辑卡片统一弹簧按压回弹与轻触觉反馈、首页 4 行横向货架磁吸滚动。

---

## 分阶段实施计划

### Phase 1：播放期间零全局掉帧——进度流与图片/状态管线解耦
- **depends_on**：无
- **用户可感知结果**：持续播歌时，首页/列表滚动与交互不再每数百毫秒被动全树重组或重复序列化导航状态，封面二次加载无闪烁与重复解码，进度条推进不触发每帧布局重算。
- **范围与预期改动**：
  - [PlaybackGateway.kt](../../android/app/src/main/java/com/flaretune/client/playback/PlaybackGateway.kt) & [AndroidPlaybackGateway.kt](../../android/app/src/main/java/com/flaretune/client/playback/AndroidPlaybackGateway.kt)：新增独立进度流 `positionMsFlow`；常规约 `200ms` 进度轮询仅更新 `positionMsFlow`，不再复制/发射整个 `PlaybackSnapshot`；仅当播放态、曲目、队列、时长或显式 `seekTo`/恢复队列等结构变更时更新 `state`。
  - [ClientViewModel.kt](../../android/app/src/main/java/com/flaretune/client/ClientViewModel.kt) & [LyricsCoordinator.kt](../../android/app/src/main/java/com/flaretune/client/data/LyricsCoordinator.kt)：暴露 `playbackPositionMs: StateFlow<Long>`；在 `init` 中按实际持久化的导航字段做 `distinctUntilChanged()`，消除无关状态变更引起的 `SHA-256` 与 `ArrayList` 分配；`LyricsCoordinator.updatePosition()` 仅在 `activeLyricIndex` 真实变化时发射新 `LyricState`。
  - [ListeningInteractionHost.kt](../../android/app/src/main/java/com/flaretune/client/ui/ListeningInteractionHost.kt)：使用 `remember` + `rememberUpdatedState` 稳定 `ListeningInteractions` 实例，避免每次宿主重组触发 `LocalListeningInteractions` 全子树失效。
  - [PlaybackRuntime.kt](../../android/app/src/main/java/com/flaretune/client/playback/PlaybackRuntime.kt)：为 Coil `ImageLoader` 启用有界磁盘缓存（`64 MiB`，`context.cacheDir/coil_image_cache`），关闭全局 `.crossfade(true)` 消除快滑时并发淡入与重复网络解码。
  - [ListeningShell.kt](../../android/app/src/main/java/com/flaretune/client/ui/ListeningShell.kt)（`ShellMiniPlayer`）& [NowPlayingScreen.kt](../../android/app/src/main/java/com/flaretune/client/ui/NowPlayingScreen.kt)（`StreamlinedTrackBar`）：读取独立 `playbackPositionMs` 状态流并以 `Canvas` / `drawBehind` 绘制进度，替代 `Modifier.fillMaxWidth(progress)` 造成的每跳测量布局开销。
- **验收标准**：
  - 单元测试证明 `positionMsFlow` 连续推进不会导致 `ClientUiState` 重复发射或触发导航序列化；原有快照与队列恢复测试保持通过。
  - `.\gradlew.bat testDebugUnitTest --no-daemon` 通过。

### Phase 2：列表滚动、顶栏定位与 4 主 Tab 无缝切换治本
- **depends_on**：Phase 1
- **用户可感知结果**：首页/发现/曲库上下滚动轻快跟手；顶栏使用纯渐变半透明遮罩，点击首页分区胶囊定位干脆且不再乱锁手势；底部 4 Tab 切换顺滑瞬时并保留浏览位置，再点当前 Tab 平滑回顶。
- **范围与预期改动**：
  - [ListeningShell.kt](../../android/app/src/main/java/com/flaretune/client/ui/ListeningShell.kt)（`FrostedHeaderScaffold`）：移除全屏离屏 `graphicsLayer` 录制与实时 `BlurEffect(20.dp)`，替换为 **Spotify 式纯渐变半透明顶栏遮罩**，随滚动深度柔和变化浓度并平滑羽化底边。
  - [ListeningShell.kt](../../android/app/src/main/java/com/flaretune/client/ui/ListeningShell.kt) & [ClientViewModel.kt](../../android/app/src/main/java/com/flaretune/client/ClientViewModel.kt)：
    - 将 4 个主 Tab 切换从销毁重建式的 `AnimatedContent(routeKey)` 改为**状态保活容器 + 柔和切页过渡**（保持各自 `LazyListState` 与浏览现场，详情子页作为独立叠层进出）；
    - 点击当前已激活底栏 Tab（无详情遮罩时）触发当前页面列表平滑回顶；优化 `ClientViewModel.navigate()` 合并单次状态更新，减少切 Tab 时的无效同步全量重载（保留 `SEARCH` 返回 `DISCOVER` 恢复语言筛选等已测试语义）。
  - [ListeningShell.kt](../../android/app/src/main/java/com/flaretune/client/ui/ListeningShell.kt)（`HomeScreen`）：优化滚动 `snapshotFlow` 减少每帧对象分配；重构 `jumpToSection()` 引入可取消任务与代次号保护，跨长距离异构高度区块先预跳至临近锚点再平滑滚动到位，消除快速连点竞态与长距离掉帧。
  - [ClientApp.kt](../../android/app/src/main/java/com/flaretune/client/ui/ClientApp.kt)、[ListeningComponents.kt](../../android/app/src/main/java/com/flaretune/client/ui/ListeningComponents.kt) & [ListeningCatalogScreens.kt](../../android/app/src/main/java/com/flaretune/client/ui/ListeningCatalogScreens.kt)：收紧 `SongRow` / `Artwork` 显式尺寸避免大图解码；修复 `PlayHistoryPage` 等处含波动列表索引的 `itemsIndexed` key；修复 `CollectionDetailScaffold` 与 `ListeningArtistPage` 越过 `scrolledPastHero` 阈值时条件插入 `Spacer(Modifier.weight(1f))` 导致的标题瞬跳。
- **验收标准**：
  - 新增 Compose UI / 状态测试验证 `FrostedHeaderScaffold` 滚动稳定、4-Tab 状态保活与点按当前 Tab 回顶、顶栏胶囊快速连续定位跳转稳定性。
  - `.\gradlew.bat testDebugUnitTest --no-daemon` 通过。

### Phase 3：全屏播放器景深开关、预模糊质感与逐字歌词渲染隔离
- **depends_on**：Phase 1
- **用户可感知结果**：点击或上滑 MiniPlayer 均可以连贯统一的弹簧景深展开全屏播放器（背景首页轻微缩沉），完整保住全屏流光背景与逐行歌词模糊深度；卡拉OK逐字推进满帧顺滑、拖动进度松手无回弹，且仅真实播放时防息屏。
- **范围与预期改动**：
  - [ClientApp.kt](../../android/app/src/main/java/com/flaretune/client/ui/ClientApp.kt)：
    - 统一播放器打开/关闭弹簧动画曲线，消除关闭时各部位时长分裂；为下层 `ListeningShell` 加入与播放器展开度联动的 `graphicsLayer` 轻微缩沉景深效果；
    - 为 `ShellMiniPlayer` 增加垂直上滑展开手势；将 `enableEdgeToEdge(...)` 从无条件 `SideEffect` 迁入 `LaunchedEffect(showNowPlaying, isDark)`，彻底消除过渡与普通重组期间重复触碰系统栏。
  - [NowPlayingScreen.kt](../../android/app/src/main/java/com/flaretune/client/ui/NowPlayingScreen.kt)（`ClassicPlayerBackdrop`）：
    - 移除全屏 `Modifier.blur(50.dp)`，在 `Dispatchers.Default` 将专辑封面降采样（如 `96×96`）并一次性预模糊缓存为环境纹理，换曲时平滑交叉淡入并保留轻量律动；底部渐变遮罩 `palette.deep` 接入颜色动画。
  - [NowPlayingScreen.kt](../../android/app/src/main/java/com/flaretune/client/ui/NowPlayingScreen.kt)（布局、常亮与播控）：
    - 精简嵌套 `SubcomposeLayout` 为直接约束/单次测量布局；
    - 将 `keepScreenOn` 改为 `DisposableEffect(view, state.playing)`，仅在真实播放中启用，暂停或退出立即恢复 `false`；
    - 修复 `ClassicPlayerTransportPanel` 松手 `seekTo` 立即清空 `dragging` 引发的 1 帧回弹（保留短时目标锚点直至真实进度追平或超时）。
  - [NowPlayingScreen.kt](../../android/app/src/main/java/com/flaretune/client/ui/NowPlayingScreen.kt)（`ClassicLyricsViewport` & `KaraokeLyricText`）& [ClassicLyricsMotion.kt](../../android/app/src/main/java/com/flaretune/client/ui/ClassicLyricsMotion.kt)：
    - 保持离散模糊档位（`0 / 0.5 / 1.2 / 2.2dp`）与弹簧布局求解契约不变；将每行歌词的离散模糊隔离到独立固定着色器缓存的 `graphicsLayer`，动画仅驱动透明度/缩放而不连续变动模糊半径；
    - 重构 `KaraokeLyricText` 将逐字推进改为绘制层（`drawWithContent` / 裁剪渐变）实时采样进度，彻底移除 `delay(40L)` 循环内的 `AnnotatedString` 全句重建。
- **验收标准**：
  - 更新 [PlayerLyricsMotionTest.kt](../../android/app/src/test/java/com/flaretune/client/PlayerLyricsMotionTest.kt)：改写 `fullScreenPlayerKeepsScreenAwakeWhileMountedAndRestoresOnDispose` 以验证“仅播放时常亮、暂停与关闭恢复”；保留并验证全部既有模糊档位与动效测试通过；补充进度条 `seekTo` 无回弹与 MiniPlayer 上滑展开测试。
  - `.\gradlew.bat testDebugUnitTest --no-daemon` 通过。

### Phase 4：全站微交互统一补齐、文档同步与最终质量检查
- **depends_on**：Phase 2, Phase 3
- **用户可感知结果**：全站可点卡片/行项/胶囊具备一致的按压弹簧反馈与轻触感，首页 4 行横向货架支持顺滑磁吸对齐；设计文档与本任务记录精确反映最终生效规则。
- **范围与预期改动**：
  - [ListeningComponents.kt](../../android/app/src/main/java/com/flaretune/client/ui/ListeningComponents.kt)、[ListeningCatalogScreens.kt](../../android/app/src/main/java/com/flaretune/client/ui/ListeningCatalogScreens.kt) & [ListeningShell.kt](../../android/app/src/main/java/com/flaretune/client/ui/ListeningShell.kt)：
    - 抽取可复用轻量按压回弹 Modifier，统一应用到 `ListeningArtistCard`、`PlaylistGridCard`、`ListeningAlbumCard`、`FeaturedHeroCard`、`CompactCategoryPill` 与 `SongRow`；
    - 为 `UnifiedSongSectionList` 横向 `LazyRow` 增加 `rememberSnapFlingBehavior` 磁吸对齐；
    - 为 `ListeningArtistPage` 与 `ListeningSearchPage` 子视图切换补充轻量柔和过渡。
  - [ClientApp.kt](../../android/app/src/main/java/com/flaretune/client/ui/ClientApp.kt)（`QueuePanel`）：边缘拖拽自动滚动由 `delay(24L)` 改为 `withFrameNanos` 帧同步驱动。
  - [Design-Brief.md](../Design-Brief.md) & [android-frontend-polish-20261113.md](android-frontend-polish-20261113.md)：同步更新生效约定（Spotify 渐变顶栏遮罩、全屏低耗预模糊与歌词层隔离、`keepScreenOn` 仅播放时生效）与状态承接，保留与本任务无关的待续项（后端统计与 P2 死代码清理）。
- **验收标准**：
  - 在 `C:\git\FlareTune-Client\android` 运行 `.\gradlew.bat testDebugUnitTest --no-daemon` 与 `.\gradlew.bat lintDebug --no-daemon` 全部通过（0 错误、0 跳过），且工作区仅包含预期改动，不损坏他人已有修改。
  - 交付时诚实说明本机本地测试与 Lint 范围及仍需实机目视/触控体验走查项（如高刷新率滚动手感与真机 OLED 暗场模糊观感）。

---

## 执行状态与验证记录

- **实施状态**：`Phase 1–4 全部完成并通过独立审查`
- **已完成阶段记录**：
  - **Phase 1（已通过审查 `5182f36f`）**：分离 `PlaybackGateway.positionMsFlow`、优化 `ClientViewModel` `distinctUntilChanged` 与 `LyricsCoordinator.updatePosition`、稳定 `LocalListeningInteractions`、`PlaybackRuntime` Coil 启用 `64 MiB` 磁盘缓存且关闭列表图全局 `.crossfade(true)`、改造 `ShellMiniPlayer` 与 `StreamlinedTrackBar` 进度条绘制避免每帧布局重算。
  - **Phase 2（已通过审查 `8b983c08`）**：`FrostedHeaderScaffold` 改用 Spotify 式纯渐变半透明遮罩并移除全屏离屏模糊录制；`ListeningShell` 实现 4 主 Tab 状态保活、柔和 `drawWithContent` 切页与点按已激活当前 Tab 回顶；优化 `HomeScreen` `snapshotFlow` 与可取消 `jumpToSection` 分段预跳；修正 `ClientViewModel.navigate()` 状态重置合并、`PlayHistoryPage` 稳定 key 与 `CollectionDetailScaffold` / `ListeningArtistPage` 标题不瞬跳。
  - **Phase 3（已通过审查 `0a48ecf1`）**：`ClientApp` 统一播放器开关弹簧景深转场并迁出 `enableEdgeToEdge` 至 `LaunchedEffect`；`ShellMiniPlayer` 增加垂直上滑展开手势；`NowPlayingScreen` 将 `keepScreenOn` 改为仅在全屏打开且 `state.playing == true` 时生效、替换嵌套 `SubcomposeLayout`、引入 `ClassicPlayerBackdrop` `96×96` 预模糊纹理、`ClassicLyricsViewport` 离散固定 `BlurEffect` 层隔离、`KaraokeLyricText` / `InterludeDotsRow` `withFrameNanos` 绘制层卡拉OK裁剪渲染、以及 `pendingSeekMs` 乐观防回弹。
  - **Phase 4（已通过最终审查 `cbf9ff7b`）**：在 `ListeningComponents.kt` 抽取 `Modifier.flarePressScale` 并接入 `UnifiedSongSectionList` `rememberSnapFlingBehavior` 磁吸；统一接入 `SongRow`、`FeaturedHeroCard`、`CompactCategoryPill`、`ListeningAlbumCard`、`ListeningArtistCard`、`PlaylistGridCard`；`ListeningArtistPage`（`subview`）与 `ListeningSearchPage`（`sectionDetail`）接入轻量 `AnimatedContent` 过渡；`QueuePanel` 边缘自动滚动改用 `withFrameNanos` 帧同步驱动；并随轮收口 `ShellMiniPlayer` 切歌时上滑手势状态稳定性与 `ClassicPlayerBackdrop` 加载失败兜底重置；同步更新 [Design-Brief.md](../Design-Brief.md) 与 [android-frontend-polish-20261113.md](android-frontend-polish-20261113.md)。
- **非阻塞后续观察项**：`ListeningCatalogScreens.kt` 内 `ListeningSearchPage` 的 `BackHandler(enabled = sectionDetail != "overview")` 在 4-Tab 保活架构下未限定 `SEARCH` 为当前激活 Tab，若从搜索子视图直接切换至其他主 Tab 再按返回会优先退回后台搜索子视图，可于后续 P2 顺带补齐 `state.destination == MainDestination.SEARCH` 条件。
- **最新全量验证（Phase 4 全部改动与文档同步后）**：
  - `C:\git\FlareTune-Client\android` 下执行 `.\gradlew.bat testDebugUnitTest lintDebug --no-daemon`：`BUILD SUCCESSFUL`（**37 个测试套件、414 条单元测试 414/414 全部通过，0 失败 / 0 错误 / 0 跳过**；`lintDebug` **0 错误 / 70 警告 / 1 提示**）。
  - 已知局限：以上验证为本地 JVM / Robolectric 单测与静态 Lint 检查，真机高刷新率滚动跟手感、OLED 暗场预模糊质感与多机窗口适配仍需真机目视/触控走查。
