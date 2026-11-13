# ANDROID-FRONTEND-POLISH-20261113

## 目标与范围

- **目标**：承接 2026-11-13 从用户视角对 `C:\git\FlareTune-Client` 的全面前端与工程健康度审查，修复交互断层、补全细节反馈与轻量过渡动画、收敛信息架构与冗余文案，并按优先顺序清理历史死代码与资源债。
- **当前适用性**：`active`（已记录产品确认与自治优化方案；**尚未实施代码修改**，待与当前并行在途分支/工作区调度后开展）。
- **非目标**：不改变服务端鉴权、账号隔离与歌词消费端职责边界；不把本地已通过单测/Lint 等同为真机听感验收；未获明确授权不删除他人未提交 WIP 或擅自触发正式发布。

## 依据与已确认决策

- **2026-11-13 用户确认（信息架构与个人库语义）**：
  - 原“资料库”概念易被误解为全库总览，明确更正为用户的**个人歌单库**（仅承载“我喜欢的”与自建歌单的组织管理）。
  - 将「歌手」「专辑」「收听统计」从右上角头像下拉菜单迁出，放入**主页（Home）**可达入口；头像菜单聚焦账号级操作（`刷新`、`退出登录`等）。
- **2026-11-13 用户确认（继续收听播放语义）**：
  - 点击主页“继续收听”列表中的歌曲时，载入“继续收听”这套历史列表作为队列，并从被点击曲目位置立即开始连续播放（而非替换为仅 1 首歌曲的孤立队列）；对应已同步更新 [Product-Spec](../Product-Spec.md) `REQ-11` 与 [Design-Brief](../Design-Brief.md)。
- **自治优化判断（无需另行逐项请示，直接按体验边界落实）**：
  - 修复所有系统返回键断层、误跳弹窗、暂停误锁亮屏、缓冲封面抽搐、歌词遗留 `Modifier.blur` 等明确缺陷。
  - 统一“歌单”中文术语、本地化语种筛选、去除过时占位文案、为高频静默操作增加轻提示与触觉反馈、补齐主壳页面与背景渐变过渡动画、收敛双 `QueuePanel` 与冗余刷新按钮。

## 分级任务与预期产物

### P0：明确交互缺陷与已定规范冲突修复
1. **补齐系统返回拦截（`BackHandler`）**：
   - `ListeningShell` 中的 `homeSubpage`（全部歌曲 / 全部专辑）；
   - `ListeningArtistPage` 的 `subview`；
   - `ListeningSearchPage` 的 `sectionDetail`；
   - `NowPlayingScreen` 的歌词展示态退回封面态，且在 `mobilePane == LYRICS` 时切歌保持停留歌词页。
2. **修复“编辑歌曲顺序”按钮误跳转**：`ListeningPlaylistDetailPage` 直接触发排序面板，而非打开歌单更多选项菜单。
3. **落实已定歌词规范**：移除 `ClassicLyricsMotion` / `NowPlayingScreen` 在 API 31+ 上遗留的逐行 `Modifier.blur`。
4. **修复 `keepScreenOn` 耗电问题**：仅在打开全屏播放页且处于 `playing` 状态时开启屏幕常亮。

### P1：体验细节反馈、动画过渡与信息架构落位
1. **信息架构与文案落地**：
   - 底栏及页面命名调整为“个人歌单库 / 歌单库”；
   - 在 `HomeScreen` 增加「歌手」「专辑」「收听统计」入口，精简头像菜单与主页多处重复“刷新”；
   - 统一“加入歌单”文案、将 `HomeScreen` 语种标签提取至 `strings.xml`、更新 `ft_empty_playlists` 过时提示。
2. **继续收听与搜索交互**：
   - `HomeScreen`“继续收听”改为与播放历史一致的列表载入连播（从点击项起播，并按 REQ-11 解析媒体）；
   - 完善 `PlayHistoryPage` 单曲删除防误触（移入 `...` 菜单或增加确认/撤销）；
   - 搜索框支持防抖触发并修正移动端提交位置习惯。
3. **轻反馈与状态细节**：
   - 为收藏、下一首播放、加入队列、切换循环模式（含防连击状态并发整理）增加轻量不遮挡操作反馈；
   - 为 MiniPlayer 左右切歌（含边界阻尼/图标提示）、拖拽排序、进度条拖动、收藏点击补充适度 `HapticFeedback`；
   - 缓冲期维持封面播放态比例；`ClassicGenerateTranslationButton` 的 `pending` 态补充旋转加载指示；`PlaylistOrderSheet` 改用 `LazyColumn` 且未修改时禁用保存，并逐步对齐全端排序交互。
4. **过渡动画补全**：
   - `ListeningShell` 一级/二级页面跳转引入轻量 `AnimatedContent` / 水平过渡；
   - 播放页系统返回键对齐弹簧收起动画；
   - `ClassicPlayerBackdrop` 渐变调色板增加 `animateColorAsState`；
   - 导航栏胶囊与筛选胶囊补充颜色/状态微动画，修正顶部加载条挤压列表跳动问题。

### P2：工程健康度与历史债务清理（先收敛脏工作树再动工）
1. **基线收口（本次部署已完成）**：双方本轮 Android 与研究资料已合入共同提交 `f6e1467` 并远程备份，`dev.7` 已覆盖安装，README 已记录实际版本和门禁。原“30 修改 / 19 未跟踪”仅为审查当时快照，不再作为待清理数量；后续新在途按对应具体任务处理。
2. **死代码与资源清理**：
   - 收敛 `ClientApp` 与 `ListeningShell` 双 `QueuePanel` 挂载；
   - 删除 `CatalogScreen` 不可达分支、`PlaylistOrderDialog`、`LibraryTab.ALBUMS` 孤立检查及 `Design-Brief` 已标记的遗留 `volume` 代码；
   - 清理 Lint 报告中的 25 个 `UnusedResources` 与 `FrequentlyChangingValue` / `AutoboxingStateCreation` / `ConfigurationScreenWidthHeight` 等告警；
   - 结合团队节奏拆分超长单文件（`NowPlayingScreen`、`ListeningShell`、`ClientViewModel`、`ListeningCatalogScreens`），并清理或隔离 `.research`、`.workbuddy-ai`、过时 `design/v1` 等本地目录。

## 当前基线与验证标准

- **当前验证基线（2026-11-13）**：
  - `.\gradlew.bat testDebugUnitTest lintDebug --no-daemon`：通过；单元测试 `29` 套件 / `318` 项全通过（`0` 失败/错误/跳过）；Lint `0` Error / `63` Warning / `1` Hint；
  - 仓库存在未提交在途改动（见项目健康性审查记录），[build.gradle.kts](../../android/app/build.gradle.kts) 当前为 `0.1.0-dev.7 / versionCode 7`。
- **实施后验收要求**：
  - 每次实施需同步通过 `testDebugUnitTest`、`lintDebug`（不新增警告，P2 后显著降低 `UnusedResources`）与 `assembleDebug`；
  - 返回栈、继续收听列表连播、歌曲排序（大列表边界）及歌词视图切歌保态须补齐 Compose / ViewModel 自动化回归测试；
  - 视觉、动画手感与暗黑/字体放大效果须通过真机或高保真预览截图核验。


## 本次部署后的基线说明

2026-11-13 用户确认 Gemini 当轮改造完工并明确部署，主协作已将双方改动合并为 dev.7/code7、覆盖平板并回读APK摘要一致。实际固定部署快照318/318、Lint0错误/65警告/1提示及R2相关复核、系统动作回读详见 [页面与系统媒体交付](android-lifecycle-media-20261113.md)。上文63警告保留为本任务此前本地命令记录，不替代固定部署输入的回执。本文件尚未实施的P0/P1/P2整改继续待后续调度，不因本次基线收口自动标为已实现。
