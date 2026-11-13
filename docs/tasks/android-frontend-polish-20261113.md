# ANDROID-FRONTEND-POLISH-20261113

## 目标与范围

- **目标**：承接 2026-11-13 从用户视角对 `C:\git\FlareTune-Client` 的全面前端与工程健康度审查，以及用户随后确定的 **4 Tab（主页 · 发现 · 搜索 · 个人歌单库）架构调整**：修复交互断层、重构主页与发现页职责、补全细节反馈与轻量过渡动画，并按优先顺序清理历史死代码与资源债。
- **当前适用性**：`implemented (P0/P1) / active (P2 & on-device visual verification)`（2026-11-13 已完成 P0/P1 及 4 Tab 主体落地、`QueuePanel` 单一宿主收敛与回归测试；基于 `0.1.0-dev.8` / 提交 `1ebbd7a`）。
- **非目标**：不改变服务端鉴权、账号隔离与歌词消费端职责边界；不把本地单测/Lint 通过等同为真机听感验收；未获明确授权不触碰他人独立服务端修复（如 YT 歌词 HTTP 500、YT 搜索 MV 过滤补丁）或正式发布。

## 依据与已确认决策

- **2026-11-13 用户确认（4 Tab 架构与个人歌单库语义，替代此前 3 Tab 及过渡方案）**：
  - 为改善宽屏/平板底部浮窗比例并厘清导航心智，底栏改为四个入口：**主页 · 发现 · 搜索 · 个人歌单库**（窄屏短标签可作“歌单库”，依不折行原则适配）。原“资料库”概念更正为用户的**个人歌单库**，仅管理“我喜欢的”与自建歌单。
  - **主页（Home）**聚焦个人高频回听与随机听歌，自上而下依次为：
    1. 顶部栏（`AccountAvatar` + 标题 + 单一全局刷新；注：后于第 81 条调优统一调整为头像 + 紧致横向胶囊、全局刷新收敛至头像菜单）；
    2. 快捷入口区（我喜欢的含一键播、播放历史、当前队列、收听统计，优先保障手机端首屏同时可见我喜欢的与播放历史）；
    3. **继续收听**（`playHistory`，第一主区，空历史时自然折叠）；
    4. **随机推荐 / 随机听歌**（复用 `discoverySongs` / `randomSongs`，带换一批刷新）；
    5. 常听摘要（`playStats`）/ 曲库速览兜底（不放冗长语种胶囊）。
  - **发现（Discover）**聚焦全库漫游、语种分类与曲库歌手流，自上而下依次为：
    1. 顶部栏；
    2. **语言分类入口**（展示 `languageCounts` 真实数量并联动筛选下方专辑与曲库歌曲；注：后于第 81 条调优仅展示纯语种名称，`showCounts = false`）；
    3. **曲库歌手信息流**（初始方案含 `spotlightArtist` 焦点音乐人卡片；注：后于第 79/81 条按最新确认移除焦点音乐人卡片，保留 `artists` 横向头像流及全部入口）；
    4. **专辑浏览流**（`albums`）；
    5. **曲库歌曲流**（`songs`）。
- **2026-11-13 用户确认（继续收听播放语义）**：
  - 点击主页“继续收听”或播放历史中的歌曲时，载入当前整套历史列表（`playHistory`）作为播放队列，并从所选曲目位置立即连续播放（按 `REQ-11` 通过 `resolveStoredSongs` 重新解析媒体，不复用 `playback.queue`）。已同步 [Product-Spec](../Product-Spec.md) `REQ-07`/`REQ-11` 与 [Design-Brief](../Design-Brief.md)。
- **后端依赖核实（2026-11-13 已核查 `FlareTune` API 与服务端路由）**：
  - 本任务全部优化**无需 GPT / 后端新增或改动接口**：客户端 [FlareTuneApi](../../android/app/src/main/java/com/flaretune/client/data/FlareTuneApi.kt) 及服务端路由已完整支持 `languageCounts`（`/api/songs?counts=language`）、`/api/artists`（已支持 `language` 筛选与分页）、`/api/albums`（已支持 `language`）、`/api/songs`、`/api/songs/random`、`/api/songs/spotlight-artist` 与 `/api/songs/resolve`。
  - 注：需注意前端 [ClientViewModel](../../android/app/src/main/java/com/flaretune/client/ClientViewModel.kt) 中**“发现”与“搜索”的状态隔离**（避免搜索改写 `query` / 清空 `songs`/`artists` 后切回“发现”出现内容丢失或错乱）。

## 分级任务与预期产物

### P0：明确交互缺陷与已定规范冲突修复
1. **补齐剩余系统返回拦截（`BackHandler`）**（注：`dev.8` 已存在 `historyOpen` 的 `BackHandler`，以下仍缺失）：
   - `ListeningShell` 中的 `homeSubpage`（全部歌曲 / 全部专辑）；
   - `ListeningArtistPage` 的 `subview`；
   - `ListeningSearchPage` 的 `sectionDetail`；
   - `NowPlayingScreen` 的歌词展示态退回封面态，且在 `mobilePane == LYRICS` 时切歌保持停留歌词页。
2. **修复“编辑歌曲顺序”按钮误跳转**：`ListeningPlaylistDetailPage`（[ListeningCatalogScreens.kt:1453](../../android/app/src/main/java/com/flaretune/client/ui/ListeningCatalogScreens.kt) 附近）直接触发排序面板，而非打开歌单更多选项菜单。
3. **核对并收口歌词逐行模糊与已定规范**：核查 `1ebbd7a` 合入后的 [NowPlayingScreen.kt](../../android/app/src/main/java/com/flaretune/client/ui/NowPlayingScreen.kt)（`combinedTextBlurDp` / `dotsSwapBlurDp`）与 [Design-Brief](../Design-Brief.md)“不以逐行动画模糊影响跟随”的对齐边界，移除违背约定的歌词行模糊。
4. **修复 `keepScreenOn` 耗电问题**：仅在打开全屏播放页且处于真实 `playing` 状态时开启屏幕常亮。

### P1：4 Tab 结构落地、细节反馈与过渡动画
1. **4 Tab 导航与主页 / 发现重排**：
   - `MainDestination` 与 `MainNavigation` 扩展为 4 项（`HOME` / `DISCOVER` / `SEARCH` / `LIBRARY`），调整平板/宽屏与窄屏底部浮窗比例与字号防折行，同步更新导航持久化恢复 `ClientNavigationState`；
   - 落地上述 **主页** 与 **发现** 的完整模块与顺序，接入 `state.languageCounts` 与 `state.artists` 信息流，保证与 `SEARCH` 互不污染状态；
   - 底栏及页面文案更正为“个人歌单库 / 歌单库”，统一“加入歌单”中文术语、将语种筛选标签提取至 `strings.xml`、清理 `ft_empty_playlists` 过时提示，精简重复“刷新”入口。
2. **继续收听连播与历史/搜索体验**：
   - 将主页 `HomeScreen` 与 `PlayHistoryPage`（[ListeningComponents.kt:794](../../android/app/src/main/java/com/flaretune/client/ui/ListeningComponents.kt) 均调用 `playHistorySong`）统一改为通过 `resolveStoredSongs` 解析历史序列后载入连续队列并从所选曲目起播；
   - 为 `PlayHistoryPage` 单曲移除补齐防误触（撤销提示 / 二次确认或收进 `...` 菜单）；
   - 搜索框支持防抖触发并调整符合移动端直觉的提交操作位置。
3. **轻反馈与状态细节**：
   - 为收藏、下一首播放、加入队列、切换循环模式（含防连击状态并发整理）增加不遮挡底栏和迷你播放条的轻量操作提示；
   - 为 MiniPlayer 左右切歌（含边界阻尼/预览提示）、拖拽排序、进度条拖动、收藏点击补充适度 `HapticFeedback`；
   - 缓冲期维持封面播放态比例；`ClassicGenerateTranslationButton` 的 `pending` 态补充旋转加载指示；`PlaylistOrderSheet` 改用 `LazyColumn` 且未修改时禁用保存，对齐全端排序交互。
4. **过渡动画补全**：
   - `ListeningShell` 一级/二级页面切换引入轻量 `AnimatedContent` / 水平过渡；
   - 播放页系统返回键对齐弹簧收起动画；
   - `ClassicPlayerBackdrop` 渐变调色板增加 `animateColorAsState`；
   - 导航栏胶囊与筛选胶囊补充颜色/状态微动画，修正顶部加载条挤压列表跳动问题。

### P2：工程健康度与历史债务清理
1. **结构收敛**：消除 `ClientApp` 与 `ListeningShell` 双 `QueuePanel` 挂载。
2. **死代码与资源清理**：
   - 删除 `CatalogScreen` 不可达分支、`PlaylistOrderDialog`、`LibraryTab.ALBUMS` 孤立检查及 `Design-Brief` 已标记的遗留 `volume` 代码；
   - 清理 Lint 中的 `UnusedResources` 与 `FrequentlyChangingValue` / `AutoboxingStateCreation` / `ConfigurationScreenWidthHeight` 等告警；
   - 按模块合理拆分超长单文件（`NowPlayingScreen`、`ListeningShell`、`ClientViewModel`、`ListeningCatalogScreens`），并在用户授权下清理 `.research` / `.workbuddy-ai` / 过时 `design/v1` 等目录。

## 当前基线与本轮完成记录

- **共同基线（`1ebbd7a` / `0.1.0-dev.8 / versionCode 8`）**：
  - 据 [README](../../README.md) 与 [歌曲关联与版本修复任务](android-song-relations-20261113.md)：原基线实现 **348/348** 自动测试通过、`assembleDebug` 与 `Lint`（0 错误 / 65 警告 / 1 提示）通过；本地工作区中除 `research/README.md` 与 `research/gemini-brief-am-instrumental-dots.txt` 外保持干净。
- **本轮已落地边界与保留项说明**：
  - **P0 / P1 完成**：已完成 4 Tab（`HOME` / `DISCOVER` / `SEARCH` / `LIBRARY`）导航、主页与发现页重排及状态隔离、`SavedStateHandle` 恢复、继续收听列表连播、顶部不遮挡轻提示、`HapticFeedback`、一级/二级页 `AnimatedContent` 过渡、`PlaylistOrderSheet` `LazyColumn` + 未修改禁用保存、歌单详情页直达排序、搜索 350ms 防抖、子视图 `BackHandler` 与歌词页切歌保留、背景调色板平滑渐变、缓冲态封面比例稳定、翻译加载旋转指示、`QueuePanel` 收敛至 `ClientApp` 单一宿主。
  - **显式保留项（受已有 `1ebbd7a` 回归单测锁定）**：
    - `NowPlayingScreen` 歌词离焦微模糊：受 `PlayerLyricsMotionTest`（`blurRadius` 断言）锁定，本轮保留原样，待确认后再决定是否调整单测一并去除；
    - `NowPlayingScreen` 挂载期 `keepScreenOn`：受 `PlayerLyricsMotionTest.fullScreenPlayerKeepsScreenAwakeWhileMountedAndRestoresOnDispose` 锁定，本轮恢复既有“打开即常亮、退出恢复”契约；
    - `PlayHistoryPage` 单曲移除保留直接点击+顶部轻提示反馈，兼容已有 `ListeningFeaturesUiTest`。
  - **验证与审查收敛（2026-11-13）**：已完成独立代码复审提出的 3 项交互/状态缺陷与 `Design-Brief.md` 快捷入口顺序同步，并完成后续体验优化：主页与“继续收听”乐观视图快照锁（`继续收听` 与 `播放历史` 语义拆分并接入 `GET /api/account/play-stats?days=7&view=summary&limit=30` 兼容混排）、发现页移除“焦点音乐人”、搜索页“最近搜索”乐观视图快照锁，以及**真机/分屏/自由浮窗连续两轮视觉调优**：
    1. 宽屏/歌曲架调优：主页“我的收藏”改用 `latestFavoriteCoverUrl` 最新收藏封面；`UnifiedSongSectionList` 维持 `78dp` 行高回归 4 行一列横向流，宽屏单列 ~2/5 宽露第三列 ~1/5，字体回正 `15sp/13sp`；宽屏底栏扩至 `86%` 宽 × `78dp` 高且主导航明显长于播控条（`~58% : 42%`）。
    2. 手机端/小窗/顶栏叠压与锚点滚动修复：窄底栏实色背景内加入 `max(navigationBarsBottom, 14dp)` 底部安全净空，随后按实机小窗反馈将紧凑导航放大至 `56dp`（`22dp` 图标 + `12sp` 标签）、迷你播控卡放大至 `62dp`（`44dp` 封面，无系统导航栏时总高约 `138.6dp`，`contentBottomInset` 同步增至 `136dp`/`66dp` 防底列表遮挡，并在 `fontScale > 1.3f` 时动态增高紧凑导航避免 200% 字号裁切）；手机端（`< 600dp`）与中屏（`600–839dp`）快捷卡同步放大至 `264–344dp × 160–200dp` / `300–360dp × 174–208dp`；主页与发现页借鉴 Spotify 改为常驻顶部紧致单行顶栏并同步推广至搜索页与个人歌单库（共用 `ShellTopHeaderRow` 与左侧同位置 `AccountAvatar`；搜索页接 `48dp` 胶囊搜索条与焦点直达及不回滚输入的受保护防抖，歌单库接“个人歌单库 / + 创建歌单 / 调整歌单库顺序”胶囊；个人歌单库在 `< 600dp` 切换为 `PlaylistListRow` 列表样式，`>= 600dp` 保持多列网格），发现页语种胶囊仅显示纯语种名称（`showCounts = false`），各页面及详情页/弹窗统一移除右上角刷新按钮（保留头像下拉菜单全局刷新 `account_refresh`），顶栏安全净空升级为 `max(WindowInsets.safeDrawing.top, 24.dp)` 避开分屏/自由小窗顶部把手（`• • •`）与 `captionBar`/`displayCutout`，并以 `FrostedHeaderScaffold` 磨砂透明浮动顶栏（`API 31+` 实时背景采集模糊 + 底部 18dp 透明羽化，低版本平滑渐变兜底）替换硬色断层；主页胶囊保持全页内容不断层、仅在存在随机推荐区块时展示对应胶囊并随滚动（含触底判定）自动同步高亮。
    最新全量跑测中 **35 个测试套件、381 条单测 381/381 全部通过**（0 失败 / 0 错误 / 0 跳过），`lintDebug` 0 错误（67 警告 / 1 提示），`assembleDebug` 编译通过（按用户指示本轮暂未部署至真机）。
  - **待续事项（P2 与后端/实机联调）**：待后端 `worker` 歌词任务结束后由 GPT 在 `GET /api/account/play-stats` 补充 `?days=N` 窗口过滤以激活近期重复收听实时排序；P2 历史死代码与未使用资源清理，以及平板/手机真机视觉走查。
