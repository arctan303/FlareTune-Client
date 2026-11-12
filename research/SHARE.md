# FlareTune Client — 竞品前端实现研究（可分享简报）

> 本文档是给外部评审/协作方（人或 AI）看的**自包含**版本：不依赖本仓库其它文件即可完整理解结论。
> 研究方式：三个已发布 APK 的静态反编译 + 商店截图像素测量。所有结论标注证据等级。

**证据等级标记**（全文通用）

| 标记 | 含义 |
| --- | --- |
| `[APK]` | 从安装包资源/代码直接读出，可信 |
| `[截图]` | 官方截图目视 + 像素量化测量，可信但受压缩影响 |
| `[推断]` | 基于上述证据的判断，未经真机验证 |

---

## 0. 背景：这是什么产品

**FlareTune Client** 是一个**纯消费型**音乐播放客户端（Android 优先，尚未定技术栈）。
"纯消费型"意味着：用户不做内容管理（不上传、不编辑元数据、不做本地曲库整理），
只做两件事 —— **选**和**听**。这决定了界面密度应该偏低、主行动点必须唯一。

设计要求（用户明确给出）：

- 首页信息架构与视觉密度：**参考 Spotify**
- 全屏播放页的排版秩序：**参考 Apple Music**
- 第一版 = 两者结合，品牌色沿用 FlareTune 的暖橙

> 注：本仓库外还有一个 Web 端 FlareTune（`C:\git\FlareTune`），使用 zinc 中性色 + Inter + JetBrains Mono。
> 移动端提案刻意沿用同一套中性色家族，让两端看起来是同一个产品。

---

## 1. 研究样本

| App | 包名 | 版本 | 来源 | 体积 | SHA-256 前 16 位 |
| --- | --- | --- | --- | --- | --- |
| 音流 / Musiver | `cn.aqzscn.stream_music` | 1.3.9 (135) | GitHub Release `gitbobobo/StreamMusic` v1.3.9 arm64-v8a | 36.1 MB | `c866007aa0f90efd` |
| Spotify | `com.spotify.music` | 9.2.0.2053 (148387875) | APKPure 分发包（base + config splits） | 153.5 MB | `1b58e6a1aa278a5c` |
| Apple Music | `com.apple.android.music` | 7.0.0 (1614) | APKPure 分发包（base + arm64 + mdpi） | 113.7 MB | `e8cc2650cac97881` |

截图：Spotify / Apple Music 各 10 张 1080×1920 商店截图，音流 6 张（含 1290×1721 横版）。

### 工具链与两个关键限制

1. **androguard 4.1.4** 读 `resources.arsc`：能拿到 `encore_*` 命名的 spacing / dimens / 字号阶梯。
2. **AXMLPrinter** 反编译 `res/layout`、`res/anim`、`res/xml`（二进制 XML）。
3. **限制 A：大厂的颜色不在 ARSC 里。** Spotify 与 Apple Music 的颜色资源条目全是动态引用
   （值形如 `#000000ce`，是主题运行时求值的占位），真实色值在代码里且被混淆。
   → 因此**色板以截图像素测量为准**（Pillow 量化），命名 token 只保留能确认的部分。
4. **限制 B：Spotify 首页的 chip / 卡片内部已迁到 Jetpack Compose**（布局里是 `ComposeView`），
   XML 里只剩容器。所以"能从代码抄到的"与"只能靠截图量的"边界必须说清楚（见 §4.2）。

---

## 2. 核心结论（TL;DR）

1. **三家的共同骨架**（可以直接抄）：封面是唯一色源 / 播放页 = 封面模糊铺满 + 压暗遮罩 /
   进度条 2–3dp / 胶囊 chip / 卡片圆角 12–16dp / mini player 悬浮在 tab bar **之上**。
2. **Spotify 是唯一把设计系统完整 token 化并打进 APK 的**：`encore_*` 命名空间有
   间距（2/4/6/8/12/16/20/24/32/40/48/64）、字号（9/11/13/16/20/24/32/40/48）、
   圆角（2/4/6/8/16/999）、控件尺寸（32/48/56）四套阶梯。**建议直接照这套比例建 token。**
3. **Apple Music 的差异化在排版与歌词**：SF Pro 整套（含 Rounded）打进 Android 包，
   大标题 34sp、歌词 24sp/行高 26sp/块间距 32–40dp、静态歌词行距 40dp、翻译行 17sp、
   mini player 88dp、tab 图标 30dp + 文字 11sp。**歌词页是它最强的一屏。**
4. **音流的差异化在氛围**：默认深色 + 一张水下照片做全局背景（可换自定义图）、玻璃拟态卡片、
   播放页是黑胶唱片隐喻（`album_disc.png` 784×784 直接叠在圆形封面外）、
   顶部分段 tab、Material You 动态取色。
5. **音流的三个短板必须避开**：没有自带正文字体（中文全靠系统）、图标混用 5 套库
   （Material / Cupertino / Fluent / FontAwesome / MDI，笔画粗细不一）、照片背景无遮罩导致正文对比度不稳。
6. **图标近乎 100% 矢量是行业默认**：Spotify 1542 个 drawable XML / 仅 31 个位图；
   Apple Music 1701 / 96。音乐客户端没有理由用位图图标。
7. **数字必须等宽**：Spotify 自带 Mix Mono，Apple 自带 Roboto Medium Numbers。
   播放进度与时长会跳字，是两家都专门处理过的细节。

---

## 3. 横向对比矩阵

| 维度 | Spotify（Encore） | Apple Music | 音流（Musiver） |
| --- | --- | --- | --- |
| 间距阶梯 | 2/4/6/8/12/16/20/24/32/40/48/64 `[APK]` | 未 token 化，实测块间距 32–40dp `[截图]` | 未 token 化 |
| 字号 | 9/11/13/16/20/24/32/40/48 `[APK]` | 大标题 34sp，歌词 24sp，翻译 17sp，tab 文字 11sp `[APK]` | 跟随系统 |
| 圆角 | 2/4/6/8/16/999 `[APK]` | 大卡 14dp `[截图]` | 卡片圆角 + 玻璃拟态 |
| 控件尺寸 | 32/48/56 `[APK]` | mini player 88dp，tab 图标 30dp `[APK]` | 分段 tab |
| 进度条 | 2dp | 4dp | 2dp |
| 播放键 | 56 实心 | 实心黑 | 56 描边圆 |
| 歌词 | 整屏铺满，非当前行 50% 白 `[截图]` | 正文 24sp / 行高 26sp / 块间距 32–40dp `[APK]` | 有歌词页 |
| 深色底 | `#121212`（占采样 22.7%）`[截图]` | 纯白/纯黑（`#FFF` 8.6% / `#000` 15.9%，含营销黑边）`[截图]` | `#1A1C1E`（M3 dark surface）`[截图]` |
| 字体 | Mix UI + Mix Mono + spoticon `[APK]` | SF Pro（含 Rounded）+ Roboto Medium Numbers `[APK]` | **无正文字体**（27 个字体全是图标字体）`[APK]` |
| 图标 | 1542 矢量 XML / 31 位图 `[APK]` | 1701 矢量 XML / 96 位图 `[APK]` | 混用 5 套图标库 `[APK]` |
| 布局技术 | 骨架 XML + 局部 Compose `[APK]` | 全原生 View `[APK]` | Flutter `[APK]` |

### 抄什么 / 不抄什么

| 抄 | 来源 | 理由 |
| --- | --- | --- |
| token 纪律（四套阶梯，别自造第五个值） | Spotify | 1391 个布局里 `spacer_16` 出现 510 次，说明收敛有效 |
| 播放页封面模糊 + 压暗遮罩 | Apple Music | 层级最稳，文字永远可读 |
| 歌词排版（行高/块间距/非当前行透明度） | Apple Music | 唯一把歌词当正式版面设计的 |
| 胶囊 chip + 右端渐隐 | Spotify | 横向滚动的边界处理得体 |
| 封面模糊 + 黑胶隐喻（做成开关） | 音流 | 差异化氛围，但必须可关 |

| 不抄 | 理由 |
| --- | --- |
| 音流的多套图标库 | 笔画粗细不一致，一眼看出廉价 |
| 音流不带正文字体 | 中文跨机型观感漂移 |
| 无遮罩的照片背景 | 正文对比度不可控 |
| Apple 的 1.08 行高 | 只适用于英文，中文必须 ≥ 1.35 |
| Apple 的 34sp 大标题 | 汉字方块占位，30sp 已是中文上限 |

---

## 4. Spotify 代码直读（这部分是可复现的硬证据）

### 4.1 关键布局文件

| 布局文件 | 作用 | 关键值 |
| --- | --- | --- |
| `funkis_home.xml` | 首页骨架 | CoordinatorLayout + AppBar，头部 minHeight 48dp |
| `funkis_filter_chips_header.xml` | chip 行容器 | 内容区是 `ComposeView`；右端渐隐 12dp |
| `glue_card.xml` | 首页卡片 | 1:1 封面；标题行高 16dp / 最多 3 行；副标题 1 行 |
| `glue_sectionheader_large.xml` | 区块标题 | 左右 16、上 40、下 16；maxLines 2 |
| `carousel_row.xml` | 横向卡片行 | 上 16（`spacer_16`）、下 40（`spacer_40`） |
| `now_playing_bar.xml` | mini player | 高 56、圆角 8、内边距 8、封面 40、按钮 40、进度 2dp |
| `now_playing_bar_scene.xml` | mini 展开动效 | MotionLayout，default→large，**100ms** |
| `floating_layout_bg.xml` | mini 背景 | 纯色 + 8dp 圆角 |
| `layout_tab.xml` | 分段 tab | 高 48dp、左右 16dp |
| `activity_now_playing.xml` | 全屏播放页 | 6 层 FrameLayout 叠加 + `EncoreThemeInstaller`（主题运行时注入） |

### 4.2 token 真实使用频次（1391 个 layout 全量统计）

```
spacer_16  510 次    spacer_8   320    spacer_24  160    spacer_12  115
std_16dp   339       std_8dp    127    std_24dp    94    std_32dp    53
```

**结论：16 / 8 / 24 / 12 是四个主力间距；48dp 是唯一触控尺寸**（`header_action_row_button_size`、
`entity_action_row_action_size`、`encore_back_button_size` 全是 48）。别自己造第五个值。

### 4.3 能从代码抄 vs 只能靠截图量

| 能抄（XML 直读） | 只能量（Compose / 运行时注入） |
| --- | --- |
| 所有容器布局、间距、尺寸、圆角、动画时长 | chip 的实际高度与选中色（`ComposeView` 内部） |
| mini player 结构与动效 | 卡片圆角（`EncoreThemeInstaller` 运行时注入） |
| 底栏联动（Barrier）逻辑 | 所有真实颜色值 |
| 字号阶梯（dimens 里的 `encore_*`） | 字体族细节（Mix UI 为私有字体） |

---

## 5. 专题：mini player 出现 / 消失的过渡动画（重点）

这是用户最关心的一个细节，也是这套设计的"手感"所在。

### 5.1 一句话结论

**底栏不动。** mini player 出现时，底栏始终贴在底部；让位的是三样东西：
内容区底部 inset、底部渐变遮罩的高度、以及 mini player 自己那 100ms 的纵向展开。
大屏（平板/折叠）才不一样：底栏按 80% 宽度收缩，播放面板占屏宽 1/3，两者并排。

### 5.2 手机端布局：谁跟谁有关系

`res/layout/vertical_main.xml`（`MainLayout`，`app:mode="0"`）里，底部这几层是**平级兄弟**：

```
MainLayout
├── CoordinatorLayout ── fragment_container（页面内容）
├── fragment_container_inset_bottom            ← 内容区底部的占位
├── fragment_container_inset_bottom_spacing    ← 固定 24dp，给渐变留余量
├── bottom_gradient        (透明 → #D9000000, easing=1)
├── bottom_gradient_window_insets (纯 #D9000000，填满系统导航栏)
├── bottom_gradient_black_fade    (48dp，透明 → #FF000000)
├── fragment_container_bottom_overlap_touch_event_consumer
├── now_playing_view_container     ← mini player，bottom 约束到 navigation_bar 顶部
└── navigation_bar                 ← 底栏，bottom 约束到底部 barrier（贴底）
```

关键不在这些 View 自己，而在两个 **ConstraintLayout Barrier**：

```xml
<!-- 底部重叠屏障：谁高就跟谁 -->
<Barrier id="fragment_container_bottom_overlap_barrier"
    barrierDirection="bottom"
    constraint_referenced_ids="now_playing_view_attachments_container,
        now_playing_view_container,navigation_bar,
        limited_experience_indicator_container,
        navigation_bar_window_insets_space,page_ui_accessory_container,
        thinking_agent_container"/>

<!-- 渐变顶部屏障：再往上偏移 48dp -->
<Barrier id="bottom_gradient_barrier"
    barrierDirection="bottom"
    barrierMargin="-48dp"
    constraint_referenced_ids="fragment_container_bottom_overlap_barrier"/>
```

于是形成一条**纯布局驱动**的联动链，不需要一行补间代码：

```
mini player 高度 0 → 56dp
   └─> overlap barrier 上移 56dp
        ├─> fragment_container_inset_bottom 变高 → 页面内容底部留白增加（不会钻到 mini player 底下）
        └─> bottom_gradient_barrier 上移 56dp → 底部黑雾变高（渐变始终罩住 dock 区域）
```

**所以"底栏让位"是错觉。** 底栏一直贴底，是它上方的东西在长高，内容区和渐变通过 Barrier 自动跟随。
这也是为什么滚动列表不会突然被 mini player 盖住 —— inset 是布局算出来的，不是硬编码 padding。

### 5.3 出现动画

`res/anim/slide_up_and_fade_in.xml`（未被任何 layout 静态引用，属于代码里动态挂的 View 动画）：

```xml
<set interpolator="@android:interpolator/overshoot" fillAfter="true">
  <alpha  duration="100" fromAlpha="0.5" toAlpha="1.0"/>
  <scale  duration="100" pivotX="0%" pivotY="100%"
          fromYScale="0.0" toYScale="1.0"/>
</set>
```

| 参数 | 值 |
| --- | --- |
| 时长 | **100ms** |
| 纵向缩放 | `scaleY` 0 → 1 |
| 缩放原点 | `pivotY = 100%`（**底边**，所以是"从底部长出来"，不是从中间撑开） |
| 透明度 | 0.5 → 1（**不从 0 起**，避免整块消失感） |
| 插值器 | `@android:interpolator/overshoot`（冲过头再回弹） |

100ms 不是孤例：`now_playing_bar_scene.xml` 里 mini player 自身两态切换
（`default_size` ↔ `large_size`）的 `Transition duration` 也是 **100**。

### 5.4 消失动画

`res/anim/slide_down_and_fade_out.xml`，与出现严格对称：

```xml
<set interpolator="@android:interpolator/anticipate" fillAfter="true">
  <alpha  duration="100" fromAlpha="1.0" toAlpha="0.0"/>
  <scale  duration="100" pivotX="0%" pivotY="100%"
          fromYScale="1.0" toYScale="0.0"/>
</set>
```

| 参数 | 出现 | 消失 |
| --- | --- | --- |
| 时长 | 100ms | 100ms |
| scaleY | 0 → 1 | 1 → 0 |
| alpha | 0.5 → 1 | 1 → 0 |
| 插值器 | **overshoot** | **anticipate** |

**进 overshoot / 出 anticipate** 是这套动效的核心手感：进来时略微冲过头再落回，
出去时先反向蓄力再抽走。两者都不是线性，所以虽然只有 100ms，仍然有"弹"的感觉。

### 5.5 全屏播放页的进出

```xml
<!-- nowplaying_activity_enter.xml -->
<translate duration="150" interpolator="overshoot" fromYDelta="100%p" toYDelta="0"/>
<!-- nowplaying_activity_exit.xml -->
<translate duration="200" interpolator="overshoot" fromYDelta="0" toYDelta="100%p"/>
```

- 进入 **150ms**，退出 **200ms**（退出更慢，避免"甩出去"的仓促感）
- 都是整屏 `100%p` 位移，配合 overshoot
- 和 mini player 的 100ms 不是一个数量级：**dock 的动效刻意更短**，因为它是高频、局部的变化

### 5.6 大屏走的是另一套

| 元素 | 规则 | 值 |
| --- | --- | --- |
| 内容区 | 左右留白 + 圆角，浮在黑色底上 | `main_layout_large_floating_margin` = **8dp**，圆角 **16dp** |
| 底栏 | 宽度 = 内容宽度 × 比例，最小 360dp | `navigation_bar_width_fragment_fraction` = **0.8** |
| 播放面板 | 占屏宽比例，360–450dp 之间 | `side_bar_width_screen_fraction` = **0.3333**，min 360 / max 450 |
| 面板展开/收起 | 两个 `Space` 定义目标矩形 + 两条 Guideline | `now_playing_view_region_expanded` / `_collapsed` |

大屏没有"让位"问题：面板出现时底栏横向收缩到 80%，两者并排。
**所以那套 100ms 纵向展开只属于手机竖屏。**

### 5.7 还有一层容易漏掉的：渐变遮罩

底部渐变不是静态背景，是 `MainLayoutGradientView`：

```xml
<MainLayoutGradientView id="bottom_gradient"
    topColor="#00000000" bottomColor="#D9000000" easing="1"
    constraintTop_toTopOf="bottom_gradient_barrier"/>
```

- 从完全透明过渡到 **85% 不透明黑**（`#D9`）
- 高度由 Barrier 决定 → **mini player 出现时渐变同步变高**
- 内容列表滚动时，`AdjustBottomGradientBasedOnScrollPositionBehavior` 会继续调整它

这就是为什么内容看起来是"沉进底部黑雾"，而不是被硬切。

### 5.8 Compose 落地代码

```kotlin
AnimatedVisibility(
    visible = hasTrack,
    enter = scaleIn(
        animationSpec = tween(100, easing = CubicBezierEasing(0.34f, 1.56f, 0.64f, 1f)), // overshoot 近似
        initialScale = 0f,
        transformOrigin = TransformOrigin(0.5f, 1f),   // pivotY = 100%
    ) + fadeIn(tween(100, initialAlpha = 0.5f)),
    exit = scaleOut(
        animationSpec = tween(100, easing = CubicBezierEasing(0.36f, 0f, 0.66f, -0.56f)), // anticipate 近似
        targetScale = 0f,
        transformOrigin = TransformOrigin(0.5f, 1f),
    ) + fadeOut(tween(100)),
) { MiniPlayer(...) }

// 内容底部 inset 跟着 dock 高度走，不要写死
Scaffold(bottomBar = { Dock() }) { padding ->
    LazyColumn(contentPadding = PaddingValues(bottom = padding.calculateBottomPadding() + 24.dp))
}
```

### 5.9 这一段**没能拿到**的

mini player 容器高度本身是否有 `ValueAnimator` 补间（布局重排的时长）。XML 里没有对应资源，
`MainLayout` 的 Kotlin 实现被混淆，只能确认 View 动画是 100ms 那一套。
如果高度是瞬时变化，视觉上会被 `scaleY` + 渐变掩盖 —— 这也是为什么只做 100ms 就够。

---

## 6. FlareTune Client 设计系统提案

> 原则：结构取 Spotify 的 token 纪律，单屏秩序取 Apple 的播放器/歌词，
> 氛围层取音流的封面模糊 + 黑胶，并补齐音流的三个短板。

### 6.1 三条设计原则

1. **封面是唯一的色源。** 界面自身不提供装饰色；强调色只出现在"正在播放 / 已喜欢 / 主按钮"。
2. **一屏一件事。** 纯消费定位下，用户不做管理，只做选择与播放。宁可留白，不要塞满。
3. **氛围可关闭。** 模糊背景、黑胶、动效都必须是可关的增强项，关掉后仍然是完整清晰的界面。

### 6.2 色彩

| token | Dark | Light | 用途 |
| --- | --- | --- | --- |
| `bg.base` | `#09090B` | `#FFFFFF` | 页面底 |
| `bg.surface` | `#121214` | `#FAFAFA` | 卡片、列表分组 |
| `bg.elevated` | `#18181B` | `#F4F4F5` | 悬浮层、mini player、弹层 |
| `bg.overlay` | `#000000` @ 55% | `#FFFFFF` @ 75% | 沉浸背景遮罩 |
| `border.hairline` | `#27272A` | `#E4E4E7` | 1dp 分隔线 |
| `text.primary` | `#FAFAFA` | `#18181B` | 标题、正文 |
| `text.secondary` | `#A1A1AA` | `#52525B` | 艺人名、副标题 |
| `text.tertiary` | `#71717A` | `#71717A` | 时长、元信息、占位 |
| `accent.base` | `#FF7A45` | `#E8590C` | 主按钮底、当前播放指示、已喜欢 |
| `accent.on` | `#1A0F08` | `#FFFFFF` | 强调色上的文字 |
| `accent.soft` | `#FF7A45` @ 16% | `#E8590C` @ 12% | 选中行底、chip 选中态 |

**硬约束**：

- 强调色**不用于正文文字**，只用于填充、图标、2dp 指示条。
- 沉浸背景上的文字，对比度 **≥ 4.5:1**；达不到就加深遮罩，而不是换字色。
- 封面主色与 `accent.base` 色相接近时，播放页指示改用 `text.primary`，不硬用强调色。

**从封面取色的收敛规则**（若实现动态主色）：

1. 取封面主色 → 转 HSL → 饱和度压到 **≤ 45%**、亮度锁在 **18–28%（dark）/ 88–94%（light）**。
2. 只用于**背景层**（渐变底、卡片微染），不用于文字与按钮。
3. 单曲切换时 **400ms 交叉淡入**，不要瞬间跳色。

好处：保留"随封面变化"的惊喜，又不会出现某张封面把界面染成荧光绿。

### 6.3 排版

| 用途 | 建议 | 理由 |
| --- | --- | --- |
| 正文/标题 | **Inter** + 系统中文字体（PingFang SC / HarmonyOS Sans / 思源黑体） | 与 Web 端一致；中文交给系统，避免打包巨型 CJK 字体（省 5–10MB） |
| 数字 | **JetBrains Mono** 或 Inter 的 `tnum` 表格数字 | 时长/进度/序号不跳字 |
| 图标 | **一套矢量图标集**（Material Symbols 或 Lucide，二选一） | 音流最大的短板就是混用 5 套 |

字号阶梯（中文已调整）：

| token | 字号/行高 | 字重 | 用途 |
| --- | --- | --- | --- |
| `type.display` | 30 / 40 | 700 | 歌单/专辑页大标题（中文上限 30） |
| `type.title` | 24 / 32 | 600 | 页面标题 |
| `type.headline` | 20 / 28 | 600 | 区块标题、播放器歌名 |
| `type.body-lg` | 17 / 26 | 500 | 列表主标题（重要） |
| `type.body` | 15 / 23 | 400 | 列表主标题、正文 |
| `type.label` | 13 / 18 | 400 | 副标题、艺人名 |
| `type.caption` | 11 / 16 | 500 | 时长、角标、tab 文字 |
| `type.numeric` | 13 / 18 | 500 | 等宽数字 |

- Spotify 是 9–48，Apple 是 11–34。本方案取 **11–30**，因为中文比拉丁字母需要**更大的最小字号、
  更小的最大字号**（汉字方块占位，大字号折行难看，小字号笔画糊）。
- 中文行高一律 **≥ 1.35**（Apple 歌词的 1.08 只适用于英文）。
- 字距：中文标题 `-0.2`，英文 `-0.5`；正文不调整。

### 6.4 间距与圆角

间距阶梯（照搬 Spotify 比例，去掉 2/6）：`4 · 8 · 12 · 16 · 20 · 24 · 32 · 40 · 48 · 64`

- 页面左右边距：**16**（列表页）/ **20**（沉浸页、播放器）
- 区块之间：24–32 ｜ 列表行内：8/12 ｜ 图标与文字：8

| token | 值 | 用途 |
| --- | --- | --- |
| `radius.xs` | 4 | 按钮、输入框、tag |
| `radius.sm` | 8 | 小封面（列表 48dp）、图标底 |
| `radius.md` | **12** | 卡片、网格封面、播放器封面 |
| `radius.lg` | 16 | mini player、弹层、底部 sheet |
| `radius.xl` | 24 | 全屏播放器顶部圆角 |
| `radius.full` | 999 | chip、播放按钮、头像 |

> 比 Spotify（封面 8）大、比 Apple（14）略小，取 12：既不像"格子"，也不至于"太软"。

### 6.5 组件规格

**筛选 chip**

| 项 | 值 |
| --- | --- |
| 高度 | 36（触摸区 48，靠 `minimumInteractiveComponentSize` 撑） |
| 圆角 | 18（= 高度一半） ｜ 横向内边距 16 |
| 文字 | `type.label` 13 / 500 |
| 未选 | `bg.surface` 底 + `text.secondary` 字 |
| 选中 | `text.primary` 底 + `bg.base` 字（深色下反相），**不用强调色** |

> 比 Spotify 的 48 高更紧凑：Spotify 的 chip 是"一级导航"，我们的 chip 只是"过滤"。

**列表行**

| 项 | 值 |
| --- | --- |
| 高度 | 64（单行）/ 72（带副标题两行） |
| 左侧封面 | 48×48，圆角 8（正在播放时改圆形 + 2dp 强调色环） |
| 主标题 | `type.body` 15，单行省略 ｜ 副标题 `type.label` 13 / `text.secondary` |
| 右侧 | 时长 `type.numeric` 13 / 三点菜单 |
| 分隔 | 无（靠间距），或 `border.hairline` 1dp 内缩 64 |

**mini player**

| 项 | 值 |
| --- | --- |
| 高度 | **72**（Apple 88 偏厚、小屏挤压内容；72 是折中） |
| 圆角 | 16 ｜ 外边距 8（左右下） |
| 背景 | `bg.elevated` @ 92% + 背景模糊（关氛围时改纯色） |
| 封面 | 44×44，圆角 8 |
| 文字 | 歌名 15 / 600，艺人 13 / `text.secondary` |
| 控件 | 播放 40、下一首 32、队列 32（触摸区 48） |
| 进度 | 2dp，贴在 pill **顶部内缘**，圆角 1 |
| 交互 | 上滑/点按展开播放器；左滑下一首（可选） |

**全屏播放器**

| 区域 | 规格 |
| --- | --- |
| 背景 | 封面 → 高斯模糊 48–64dp → 叠 `bg.overlay`（dark 55% / light 75%）→ 顶部再叠垂直渐变（黑 60% → 透明 40%） |
| 顶部 | 返回 chevron 32 + 中间"正在播放"11sp + 右侧队列 32 |
| 封面 | 屏宽 − 64（左右各 32），圆角 12，投影极轻（黑 25%，y=8，blur=24） |
| 黑胶模式 | 圆形裁切 + 唱片纹；直径 = 屏宽 − 96；24s/圈；暂停时**缓慢停转**（800ms 缓出） |
| 标题区 | 歌名 `type.headline` 20/600，艺人 `type.label` 13 / `text.secondary`，左对齐 |
| 进度 | 2dp 轨道（未播放 `text.primary` @ 20%），thumb 12dp，两端时间 `type.numeric` 11 |
| 控件 | 循环 32 · 上一首 32 · **播放 56 实心 `accent.base`** · 下一首 32 · 喜欢 32 |
| 底部 | 音质/设备入口（可选），留 24 安全区 |

**与三家的差异（有意为之）**：

- 播放键用**实心强调色圆**（学 Spotify），不用音流的描边圆 —— 消费场景需要明确的主行动点。
- 黑胶是**可选形态**（学音流，但做成开关），默认关闭，因为唱片纹会吃掉封面的细节。
- 三段 tab（播放/队列/歌词）只在有内容时出现，避免空 tab。

**歌词**

| 项 | 值 |
| --- | --- |
| 正文 | 20 / 行高 1.4（28），字重 600 |
| 当前行 | `text.primary` 100% ｜ 非当前行 `text.primary` 45%（**不是纯灰**，保持色温） |
| 块间距 | 28（中文比 Apple 的 32 略紧，因为汉字行本身更高） |
| 翻译行 | 15 / `text.secondary`，紧跟主行下方 4 |
| 左右边距 | 32（居中偏左对齐，不用两端对齐） |
| 逐字高亮 | 若做，羽化 24dp，扫过速度跟随音频 |
| 交互 | 点击行 → 跳转该时间点；长按 → 复制/分享 |

**关键**：非当前行不要降到 20% 以下 —— 用户会想"预读下一句"，太暗就读不了。

**首页**

| 区块 | 规格 |
| --- | --- |
| 顶部 | 问候语 `type.title` 24 + 头像 32；下方 chip 行 |
| 快速入口 | 2×2 或 4 宫格（继续收听/随机/收藏/最近添加），图标 24 + 文字 13 |
| 横向卡片 | 1:1 或 1.6:1 封面，圆角 12，卡下标题 15 / 副标题 13；**右侧露出 8–12% 下一张** |
| 区块标题 | `type.headline` 20 + 右侧"全部" 13 |
| 空态 | 一句话 + 一个主按钮，不要插画 |

### 6.6 动效总表

| 场景 | 时长 | 曲线 |
| --- | --- | --- |
| 按压反馈 | 100 | linear |
| 状态切换（chip/选中） | 180 | `cubic-bezier(0.2, 0, 0, 1)` |
| 页面进入 | 240 | 同上 + 8dp 上移 |
| mini player 出现 | **100** | overshoot（`scaleY` 0→1，原点底边，alpha 0.5→1） |
| mini player 消失 | **100** | anticipate（`scaleY` 1→0） |
| mini ↔ 全屏播放器 | 320 | 共享元素（封面位移 + 缩放），背景淡入 |
| 全屏页进入 / 退出 | **150 / 200** | 整屏 `translateY`，overshoot |
| 歌词行切换 | 220 | 标准 |
| 封面取色变化 | 400 | 交叉淡入 |
| 黑胶旋转 | 24s/圈 | linear；暂停 800ms 缓出 |

原则：**所有动效可被系统"减少动画"设置一次性关闭**（关掉后直接切换，不做降级动画）。

### 6.7 无障碍下限

- 触摸目标 ≥ 48dp（视觉可以更小，用透明 padding 撑）。
- 正文对比度 ≥ 4.5:1，大字/图标 ≥ 3:1。
- 支持系统字号放大到 200%：列表行给 `minHeight` 而非 `height`。
- 不靠颜色单独传达状态（正在播放除颜色外还要有图标或字重变化）。
- 沉浸背景必须提供"纯色模式"降级。

---

## 7. 第一版原型（已实现，可交互）

位置：`design/v1/index.html`（纯静态，双击打开，无构建）。

| 区域 | 做法 | 来源 |
| --- | --- | --- |
| 首页信息架构 | 顶部 chip → 两列快捷卡 → 横向推荐行 ×N → 列表 | Spotify |
| chip 行 | 胶囊、横向滚动、右端 12dp 渐隐 | Spotify（APK 直读） |
| 卡片 | 1:1 封面 + 类型标签 + 标题两行 + 艺人一行 | Spotify（`glue_card.xml`） |
| 底部 dock | mini player 悬浮在 tab bar 之上 | Spotify |
| mini player | 高 56、圆角 8、内边距 8、封面 40、按钮 40、进度 2dp | Spotify（`now_playing_bar.xml`） |
| 展开动效 | 100ms overshoot / anticipate | Spotify（`res/anim`） |
| 全屏播放页 | 大封面居中 → 标题/艺人 → 歌词摘录 → 进度 → 控件 | Apple Music |
| 全屏背景 | 封面主色径向渐变 + 压暗遮罩 | Apple Music |
| 歌词页 | 整屏滚动、当前行高亮、远处行渐隐、点击跳转 | Apple + Spotify |
| 响应式 | 窄屏 2 列 / 宽屏 5 列，dock 竖排 → 横排 | Spotify 宽屏截图实测 |

**这一版是"数值验证版"，不是最终视觉**。它故意贴着 Spotify 的原值做（mini player 56dp），
而提案（§6）里改成了 72dp —— 两者差异是**刻意留待真机对比后再定**的。

已知近似项：

| 项 | 说明 |
| --- | --- |
| chip 高度 | 代码里是 Compose 实现，XML 拿不到；取 40dp |
| chip 选中色 | 截图实测 `#65D46E`（疑 JPEG 偏淡），本版用品牌橙 `#FF7A45` |
| 卡片圆角 | 运行时注入未取到；取 4dp（截图目测） |
| 字体 | Spotify 用 Mix UI，本版用系统字体栈 |
| 封面 | CSS 渐变占位，不含第三方素材 |

**交互清单**：点卡片播放 → 载入 mini player；点 mini player → 全屏页 100ms 升起；
拖进度条 / 播放暂停 / 上下首 / 随机循环 / 喜欢；点歌词行跳时间；
键盘 `空格` / `←→` / `Esc`；拖窗口宽度看响应式。
**长按 mini player 600ms** 会停止播放并收起，能完整看到 anticipate 的"先蓄力再抽走"。

---

## 8. 待定项（需要拍板）

| # | 问题 | 提案倾向 |
| --- | --- | --- |
| 1 | 播放键：实心强调色还是描边？ | 实心 `#FF7A45`（消费场景需要唯一主行动点） |
| 2 | mini player 高度：72 还是 88？ | 72（比 Apple 紧、比 Spotify 松；需真机验小屏竖向空间） |
| 3 | 歌词字号：20 还是 24？ | 20（24sp 下汉字单行容纳字数偏少） |
| 4 | 黑胶模式默认开还是关？ | 默认关，作为可开启的氛围层 |

## 9. 待验证清单（实现时优先验）

- [ ] 真机上模糊 48–64dp 在中低端机的帧率（音流用 `BackdropFilter`，成本不低）
- [ ] 中文 30sp 标题在 360dp 宽屏上的折行表现
- [ ] 强调色 `#FF7A45` 在浅色模式下的对比度（可能需降到 `#E8590C`）
- [ ] 72dp mini player + 手势条 + tab bar 的竖向空间是否够
- [ ] 歌词 45% 不透明度的实际可读性（不同屏幕亮度下差异大）
- [ ] 黑胶旋转的功耗（持续动画会阻止 GPU 降频）

---

## 10. 仓库内文件索引

| 路径 | 内容 |
| --- | --- |
| `research/README.md` | 研究索引与结论摘要 |
| `research/docs/00-cheatsheet.md` | 一页速查（实现时要查的数与坑） |
| `research/docs/01-method.md` | 样本、工具链、证据分级与局限 |
| `research/docs/02-streammusic.md` | 音流逐屏拆解 |
| `research/docs/03-spotify.md` | Spotify 逐屏拆解 + Encore token 全表 |
| `research/docs/04-applemusic.md` | Apple Music 逐屏拆解 + 排版/歌词规格 |
| `research/docs/05-comparison.md` | 横向对比矩阵 |
| `research/docs/06-flaretune-client-design.md` | 设计系统提案（本文件 §6 的来源） |
| `research/docs/08-dock-motion.md` | dock 动效完整推导（本文件 §5 的来源） |
| `research/tokens/*.json` | 从 APK 提取的原始 token + 提案 token |
| `research/evidence/layouts/` | Spotify 关键布局的反编译 XML 原件（16 个） |
| `research/evidence/screenshots/` | 三家商店截图（26 张） |
| `research/evidence/palettes/` | 截图像素测量的调色板数据 |
| `research/report.html` | 可视化报告 |
| `research/preview.html` | 三家播放器还原 + 提案可交互预览 |
| `design/v1/index.html` | 第一版界面原型 |

---

*本简报生成于 2026-11-12。APK 原始文件不入库（体积与授权原因），校验值见 §1。*
