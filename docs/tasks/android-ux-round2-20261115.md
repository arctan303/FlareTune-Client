# ANDROID-UX-ROUND2-20261115：安卓客户端第二轮体验与队列能力完善

## 目标与范围

- **目标**：依据 2026-11-15 用户实机使用反馈（13 项）及同日确认的产品/设计决策，系统解决播放队列闪烁/定位、Mini 播放器拖拽卡片预览、首页“来自你的曲库”按语种比例展示最新歌曲（含未打标）、“继续收听”卡片响应式字号、歌单/曲目排序与多选、歌单/歌曲菜单统一、歌手计数隐藏、Pad 设置页悬浮底栏，以及独立二级“播放历史”页面与防误替换队列保护。
- **当前适用性**：`active (implemented across Batch A / B / C & reviewed, awaiting real-device walk-through)` — Batch A、Batch B 与 Batch C 已按规划完成代码实现与独立审查（A-1 / B-1 / C-1 均给出 `APPROVED`），现存主要待验缺口为真机交互手势与流畅度实测。
- **风险与路线**：产品体验与状态/UI 改造（**R1** — 客户端状态、本地持久化扩展及 Compose 交互变更，可逆可控；其中 #3 若需服务端补充未打标语种计数/筛选，属向后兼容只读扩展，实施前需单独确认并同步 `FlareTune` 仓库接口文档）。
- **非目标**：不修改账号鉴权、Cookie/Keystore 隔离策略、歌词来源链/AI 翻译资格协议、管理员功能或正式发布/签名；不以文档规划代替实际编译、单测、Lint 与真机走查。

---

## 依据与已确认决策（2026-11-15 用户确认）

1. **首页“来自你的曲库”按语种比例取最新（#3）**：
   - 保持展示“最新入库歌曲”（非随机抽样），按曲库真实语种分布比例（含**未打标** `language = null / ""` 歌曲）取各语种最新歌曲组成首页 16 首货架，并补齐“播放全部”按钮。
   - **已核实后端现状**：`FlareTune` 宿主 `GET /api/songs?counts=language`（`server/src/routes/localMusicRead.js`）与 `buildSongLanguageFilter`（`server/src/utils/songLanguage.js`）当前均排除 `language IS NULL / ''`。实施 Batch C 时可向后兼容扩展宿主未打标计数与筛选（如 `includeUntagged=1` / `language=untagged`），并在客户端对旧版实例保留降级兼容。
2. **所有歌手卡片与歌手页隐藏曲目/专辑数量（#8）**：
   - 所有歌手卡片（`ListeningArtistCard`）与歌手详情页（`ListeningArtistPage`）均不显示“X 首歌曲 · Y 张专辑”计数，保持视觉简洁一致。
3. **区分队列替换语义并配“播放全部”按钮（#9、#10）**：
   - **明确具备队列设计意图的功能**（`继续收听`、`随机推荐`、`歌单详情`、`专辑详情`、`我的收藏`、`歌手详情`）保留点击歌曲即载入对应列表并替换播放队列的语义（其中“随机推荐”等确保有明确播放范围）。
   - **单点发现/检索场景**（`搜索结果`、`播放历史`歌曲列表、首页`来自你的曲库`）点击单曲默认改为**插播至当前队列下一首并立即切播**，不覆盖用户原有队列；上述列表同时提供显式的**“播放全部”**按钮用于主动以整列替换队列。
4. **Pad 设置页保持底栏悬浮一致性（#11 方案 A）**：
   - 保留宽屏/平板悬浮 Dock，去掉 `SettingsScreen` 外层 `Box` 对 `bottomInset` 的硬裁切，将 `bottomInset` 移入 `verticalScroll` 内部底边距，使设置卡片自然滚动至悬浮 Dock 下方。
5. **歌曲收藏星标与不误关菜单（#13）**：
   - 歌曲菜单中的“我喜欢”图标改用 `ClientIcons.Star` / `ClientIcons.StarFilled`，未收藏文案为“加入收藏”，已收藏时点亮图标并显示“已收藏”。
   - 点击收藏/取消收藏仅原地刷新状态与顶部轻提示，不再由 `libraryMutationVersion` 触发 `closeEditors()` 异常关闭；其余需关闭弹窗的菜单项统一经 `LocalFlareSheetDismiss` 完成收起动画后退出。
6. **分批实施节奏**：按 **Batch A → Batch B → Batch C** 顺序推进，文档先行对齐后开工。

---

## 分阶段实施计划

### Batch A：基础体验缺陷修复、手势卡片化与悬浮适配（#1、#2、#4、#11、#12、#13）
- **depends_on**：无
- **覆盖反馈**：`#1` `#2` `#4` `#11` `#12` `#13`
- **范围与预期改动**：
  1. **#1 队列改序去闪烁**（[ClientApp.kt](../../android/app/src/main/java/com/flaretune/client/ui/ClientApp.kt)）：为队列项使用稳定唯一实例 key（兼容重复歌曲 `song.id`）；在异步 `MediaController` 确认期间以当前曲目实例 key 保持高亮；同步 `state.queue` 时做增量对齐而非 `entries.clear() + addAll()` 整表重建；统一 `animateItem()` 修饰，避免松手瞬间切换修饰器引起的跳闪。
  2. **#2 打开队列居中定位**（[ClientApp.kt](../../android/app/src/main/java/com/flaretune/client/ui/ClientApp.kt)）：`QueuePanel` 打开并完成首次列表测量后，计算视口中线偏移并 `scrollToItem(currentIndex, -centerOffset)`，使当前播放曲目尽量居中；标题栏可选补充“定位当前”快捷按钮。
  3. **#4 Mini 播放器左右滑切卡片化**（[TrackSwitchMotion.kt](../../android/app/src/main/java/com/flaretune/client/ui/TrackSwitchMotion.kt)、[ListeningShell.kt](../../android/app/src/main/java/com/flaretune/client/ui/ListeningShell.kt)、[AndroidPlaybackGateway.kt](../../android/app/src/main/java/com/flaretune/client/playback/AndroidPlaybackGateway.kt)）：
     - 在 `PlaybackSnapshot` 透传 Media3 Timeline 实际的 `previousQueueIndex` / `nextQueueIndex`（含随机播放顺序）；
     - 取消播放 >3 秒对上一首预览的硬屏蔽（保持合理滑动预览与阻尼边界）；
     - 为 `TrackCarouselViewport` 内部每个歌曲槽位增加清晰的卡片边界/背景与横向间距，左右拖动时真实看到上下首卡片随手指滑入。
  4. **#11 Pad 设置页悬浮底栏**（[AccountSidebarAndSettings.kt](../../android/app/src/main/java/com/flaretune/client/ui/AccountSidebarAndSettings.kt)）：移除外层 `Box` 的 `.padding(bottom = bottomInset)`，改在 `.verticalScroll(...)` 内部卡片列底部施加 `bottomInset` 留白。
  5. **#12 “继续收听”卡片字号响应式缩放**（[ListeningShell.kt](../../android/app/src/main/java/com/flaretune/client/ui/ListeningShell.kt) `ContinueListeningGridTile`）：字号、行高、内边距、渐变压暗高度与右侧箭头尺寸随卡片边长 `size` 等比自适应缩放（如 `13sp..16.5sp`），大卡时支持适当双行/宽裕排版。
  6. **#13 歌曲菜单星标改版与弹窗收起修复**（[LibraryDialogs.kt](../../android/app/src/main/java/com/flaretune/client/ui/LibraryDialogs.kt)、[ListeningInteractionHost.kt](../../android/app/src/main/java/com/flaretune/client/ui/ListeningInteractionHost.kt)、`values*/strings.xml` / `library_actions.xml`）：
     - `SongActionsSheet` 替换图标为 `Star` / `StarFilled`，文案改为“加入收藏 / 已收藏”；
     - 收藏/取消收藏不触发 `closeEditors()`，仅原地刷新高亮态与轻提示；
     - 排查 `ListeningInteractionHost` 全部菜单动作（下一首、加入队列、复制信息、歌单菜单等），统一走 `LocalFlareSheetDismiss` 动画收起。
- **验收与验证**：
  - 针对队列对齐/居中偏移、随机模式上下首索引、`ContinueListeningGridTile` 尺寸映射、收藏不关窗与 `LocalFlareSheetDismiss` 补充/更新单元测试与 Compose UI 测试；
  - 执行 `:app:testDebugUnitTest :app:assembleDebug :app:lintDebug`。

### Batch B：队列替换语义修正、队列快照保护与独立“播放历史”二级页（#9、#10）
- **depends_on**：Batch A（共享 `ClientApp` / `QueuePanel` / `ListeningInteractionHost` 交互基线）
- **覆盖反馈**：`#9` `#10`
- **范围与预期改动**：
  1. **#10 区分单曲插播与队列替换**（[ClientViewModel.kt](../../android/app/src/main/java/com/flaretune/client/ClientViewModel.kt)、[ListeningCatalogScreens.kt](../../android/app/src/main/java/com/flaretune/client/ui/ListeningCatalogScreens.kt)、[ListeningShell.kt](../../android/app/src/main/java/com/flaretune/client/ui/ListeningShell.kt)、[ListeningComponents.kt](../../android/app/src/main/java/com/flaretune/client/ui/ListeningComponents.kt)）：
     - 新增 `playSingleAsNext(song)`（若当前队列为空或不可播则直接起播；否则插入当前曲目下一位并切到该首播放，保留前后原有队列）；
     - 应用于**搜索结果单曲、最近搜索单曲、首页“来自你的曲库”单曲、播放历史歌曲**；
     - 在**播放历史歌曲页**、**首页“来自你的曲库”**（含全部歌曲页相应入口）及**搜索歌曲区**确保具备清晰的**“播放全部”**按钮以执行整列替换队列；
     - `继续收听`、`随机推荐`、`歌单详情`、`专辑详情`、`我的收藏`、`歌手详情`保持整列队列语义。
  2. **#9 历史队列快照与多标签“播放历史”二级页**（[PlaybackHistory.kt](../../android/app/src/main/java/com/flaretune/client/data/PlaybackHistory.kt)、[PlaybackQueuePersistence.kt](../../android/app/src/main/java/com/flaretune/client/data/PlaybackQueuePersistence.kt)、[ListeningComponents.kt](../../android/app/src/main/java/com/flaretune/client/ui/ListeningComponents.kt)、[ClientViewModel.kt](../../android/app/src/main/java/com/flaretune/client/ClientViewModel.kt)）：
     - 按 `HistoryIdentity`（账号+实例隔离）新增本地有界存储：**近期访问歌手**、**近期播放专辑/歌单**、**历史队列快照**（当发生整列替换队列且原队列有效曲目 ≥ 2 首或经用户调整时，自动保存最近例如 15 份快照，记录来源标签、时间戳、曲目与 `currentIndex`，不含敏感凭据或临时 URL）；
     - 重构 `PlayHistoryPage` 为带顶部切换标签的独立二级页面：**歌曲**（最新倒序）｜**歌手**｜**专辑 / 歌单**｜**历史队列**（支持一键恢复整套队列及查看当前队列）。
- **验收与验证**：
  - 单元测试覆盖 `playSingleAsNext` 队列保留、队列快照去重/上限/账号隔离/冷恢复、最近歌手与专辑歌单记录；
  - Compose UI 测试验证 4 标签历史页切换、恢复历史队列与单曲插播行为；
  - 执行 `:app:testDebugUnitTest :app:assembleDebug :app:lintDebug`。

### Batch C：首页按语种比例、排序、批量多选、菜单完善与歌手计数移除（#3、#5、#6、#7、#8）
- **depends_on**：Batch B（复用新版菜单关闭动画、单曲插播与多选加入队列/歌单链路）
- **覆盖反馈**：`#3` `#5` `#6` `#7` `#8`
- **范围与预期改动**：
  1. **#3 首页“来自你的曲库”按语种比例展示最新歌曲（含未打标）**（[ClientViewModel.kt](../../android/app/src/main/java/com/flaretune/client/ClientViewModel.kt)、[FlareTuneApi.kt](../../android/app/src/main/java/com/flaretune/client/data/FlareTuneApi.kt)、[ListeningShell.kt](../../android/app/src/main/java/com/flaretune/client/ui/ListeningShell.kt)，配合 `FlareTune` 仓库 [localMusicRead.js](../../../FlareTune/server/src/routes/localMusicRead.js) / [songLanguage.js](../../../FlareTune/server/src/utils/songLanguage.js) / [music.md](../../../FlareTune/server/api/music.md)）：
     - 后端向后兼容支持未打标计数与筛选（如 `GET /api/songs?counts=language&includeUntagged=1` 与 `language=untagged`）；
     - 客户端按真实各语种（含未打标）占曲库比例采用最大余额法分配 16 个名额（非空语种保底 1 首），分别拉取各语种 `sort=desc` 最新歌曲并交错编排到首页“来自你的曲库”货架（与“发现/全部歌曲”独立分页互不污染，旧服务端自动降级）；货架标题补充“播放全部”。
  2. **#5 歌单排序与歌单内歌曲排序**（[ListeningCatalogScreens.kt](../../android/app/src/main/java/com/flaretune/client/ui/ListeningCatalogScreens.kt)、[LibraryDialogs.kt](../../android/app/src/main/java/com/flaretune/client/ui/LibraryDialogs.kt)）：
     - 个人歌单库增加视图排序选项（自定义顺序 / 名称 / 曲目数 / 最近更新）及升级 `ListeningShelfOrderSheet` 支持拖动调序与一键按名称/曲目数重排；
     - 歌单详情页增加视图排序（默认顺序 / 歌名 / 歌手 / 时长 / 倒序），并在 `PlaylistOrderSheet` 内提供一键按规则重排快捷按钮（需显式保存才写回服务端）。
  3. **#6 批量多选能力**（[ListeningCatalogScreens.kt](../../android/app/src/main/java/com/flaretune/client/ui/ListeningCatalogScreens.kt)、[ListeningComponents.kt](../../android/app/src/main/java/com/flaretune/client/ui/ListeningComponents.kt)）：
     - 在歌单详情、搜索结果歌曲列表、全部歌曲与专辑详情页支持“多选”按钮或长按歌曲进入多选态；
     - 底部浮出批量操作栏（已选计数、全选/反选、下一首播放、加入队列、加入歌单、批量收藏，歌单详情另有批量移除）。
  4. **#7 完善三点菜单（歌单及同类页面）**（[ListeningInteractionHost.kt](../../android/app/src/main/java/com/flaretune/client/ui/ListeningInteractionHost.kt)、[LibraryDialogs.kt](../../android/app/src/main/java/com/flaretune/client/ui/LibraryDialogs.kt)）：
     - 重构歌单三点菜单为 `PlaylistActionsSheet`：封面缩略图 + 名称 + 曲目数头部、3 格快捷卡片（播放 / 随机播放 / 加入队列）、带图标分组操作（添加歌曲 / 排序 / 多选 / 编辑信息 / 更换或重置封面 / 删除歌单）；同步检查专辑、歌手页快捷动作一致性。
  5. **#8 隐藏所有歌手卡片及歌手页歌曲/专辑计数**（[ListeningCatalogScreens.kt](../../android/app/src/main/java/com/flaretune/client/ui/ListeningCatalogScreens.kt)、[ListeningComponents.kt](../../android/app/src/main/java/com/flaretune/client/ui/ListeningComponents.kt)）：
     - 移除 `ListeningArtistTile`、`ListeningArtistCard`、`ListeningArtistPage` 头图及历史歌手页中的歌曲/专辑计数文本显示，并清理已废弃的 `CatalogCountLabels.kt` 与对应字符串资源。
- **验收与验证**：
  - 单元测试覆盖比例配额算法（含未打标与少数语种保底）、歌单/歌曲排序稳定性、多选批量动作边界；
  - 若涉及 `FlareTune` 后端未打标计数/筛选改动，补充对应服务端路由与工具测试及 `server/api/music.md`；
  - 执行 `:app:testDebugUnitTest :app:assembleDebug :app:lintDebug` 与（如改动后端）服务端定向/全量测试。

---

## 风险与后续边界

- **跨仓库协同边界**：`#3` 已在 `FlareTune` 服务端完成向后兼容参数（`includeUntagged=1` / `language=untagged`）开发与单测，且客户端对未升级服务端保留降级兼容；服务端变更部署前可先依降级路径运行，上线服务端后自动生效完整比例。
- **真机手势与长队列性能**：`#1`（队列拖动）、`#2`（居中定位）、`#4`（Mini 播放器横向卡片滑动）、`#6`（大字号多选底部操作栏）的触感与 60/120Hz 流畅度仍需在本地平板/手机真机走查，JVM/Robolectric 单测与 Lint 不替代真机验证。
- **状态与证据**：
  - **实现与审查状态**：Batch A（A-1）、Batch B（B-1）与 Batch C（C-1）均已完成实现与独立只读审查（全部 `APPROVED`）。
  - **客户端实证**：`.\gradlew.bat :app:testDebugUnitTest :app:assembleDebug :app:lintDebug` 通过 — 48 个测试套件共 **559** 个单测/Compose UI 测试全通过（`0 failed, 0 errors, 0 skipped`），`:app:assembleDebug` 通过，`:app:lintDebug` **0 Error**（102 Warning）。
  - **服务端实证（`FlareTune` `#3`）**：定向语言/曲库读取测试 **53/53** 通过；全量 `run-source-tests.mjs` 中 **2793/2806** 通过（剩余 13 失败属既有未提交的歌词翻译/元数据分支基线，与 Batch C 修改无关）。
