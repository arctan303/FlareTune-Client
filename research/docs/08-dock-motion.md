# Spotify 的 mini player 出现 / 消失动效（反编译实测）

研究对象：Spotify 9.2.0.2053 APK。
证据：`res/layout/vertical_main.xml`、`large_main.xml`、`res/anim/*.xml`、`res/xml/now_playing_bar_scene.xml`。
原始文件在 [`../evidence/layouts/`](../evidence/layouts/)，本页只写结论。

---

## 一句话结论

**底栏不动。** mini player 出现时，底栏始终贴在底部，让位的是三样东西：
内容区底部 inset、底部渐变遮罩的高度、以及 mini player 自己那 100ms 的纵向展开。
大屏才不一样：底栏按 80% 宽度收缩，播放面板占屏宽 1/3，两者并排。

---

## 一、手机端布局：谁跟谁有关系

`res/layout/vertical_main.xml`（`MainLayout`，`app:mode="0"`）里，底部这几层是平级兄弟：

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

关键不在这些 View 自己，而在两个 **Barrier**：

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

于是形成一条联动链：

```
mini player 高度 0 → 56dp
   └─> overlap barrier 上移 56dp
        ├─> fragment_container_inset_bottom 变高 → 页面内容底部留白增加（不会钻到 mini player 底下）
        └─> bottom_gradient_barrier 上移 56dp → 底部黑雾变高（渐变始终罩住 dock 区域）
```

**所以"底栏让位"是错觉**：底栏一直贴底，是它上方的东西在长高，而内容区和渐变通过 Barrier 自动跟随。
这也是为什么滚动列表不会突然被 mini player 盖住 —— inset 是布局算出来的，不是硬编码 padding。

---

## 二、出现动画

`res/anim/slide_up_and_fade_in.xml`（没有被任何 layout 静态引用，属于代码里动态挂的 View 动画）：

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
| 透明度 | 0.5 → 1（不从 0 起，避免整块消失感） |
| 插值器 | `@android:interpolator/overshoot`（0x010a0006，冲过头再回弹） |

100ms 这个值不是孤例：`res/xml/now_playing_bar_scene.xml` 里 mini player 自身两态切换（`default_size` ↔ `large_size`）的 `Transition duration` 也是 **100**。

## 三、消失动画

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

**进 overshoot / 出 anticipate** 是这套动效的核心手感：进来时略微冲过头再落回，出去时先反向蓄力再抽走。
两者都不是线性，所以虽然只有 100ms，仍然有"弹"的感觉。

## 四、全屏播放页的进出

`res/anim/nowplaying_activity_enter.xml` / `_exit.xml`：

```xml
<!-- enter -->
<translate duration="150" interpolator="overshoot" fromYDelta="100%p" toYDelta="0"/>
<!-- exit -->
<translate duration="200" interpolator="overshoot" fromYDelta="0" toYDelta="100%p"/>
```

- 进入 **150ms**，退出 **200ms**（退出更慢，避免"甩出去"的仓促感）
- 都是整屏 `100%p` 位移，配合 overshoot
- 和 mini player 的 100ms 不是一个数量级：**dock 的动效刻意更短**，因为它是高频、局部的变化

## 五、大屏（`large_main.xml`，`app:mode="2"`）走的是另一套

手机端是"上下堆叠"，大屏是"左右分栏"：

| 元素 | 规则 | 值 |
| --- | --- | --- |
| 内容区 | 左右留白 + 16dp 圆角，浮在黑色底上 | `main_layout_large_floating_margin` = **8dp**，圆角 **16dp** |
| 底栏 | 宽度 = 内容宽度 × 比例，最小 360dp | `navigation_bar_width_fragment_fraction` = **0.8**，min **360dp** |
| 播放面板 | 占屏宽比例，360–450dp 之间 | `side_bar_width_screen_fraction` = **0.3333**，min 360 / max 450 |
| 面板展开/收起 | 两个 `Space` 定义目标矩形 + 两条 Guideline | `now_playing_view_region_expanded` / `_collapsed` |
| 内容区圆角 | 用 `layer-list` 描边画外圆角（32dp 直径的 stroke 切出 16dp 圆角） | `main_layout_large_floating_corner_bottom_left/right` |

大屏没有"让位"问题：面板出现时底栏横向收缩到 80%，两者并排。**所以那套 100ms 纵向展开只属于手机竖屏。**

## 六、还有一层容易漏掉的：渐变遮罩

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

---

## 七、FlareTune 怎么落地（Compose）

```kotlin
// 1) dock 的三种状态：无播放 / 有播放 / 展开全屏
// 2) mini player 用 AnimatedVisibility + 自定义 enter/exit，不要用默认 fade
AnimatedVisibility(
    visible = hasTrack,
    enter = scaleIn(
        animationSpec = tween(100, easing = CubicBezierEasing(0.34f, 1.56f, 0.64f, 1f)), // overshoot
        initialScale = 0f,
        transformOrigin = TransformOrigin(0.5f, 1f),   // pivotY = 100%
    ) + fadeIn(tween(100, initialAlpha = 0.5f)),
    exit = scaleOut(
        animationSpec = tween(100, easing = CubicBezierEasing(0.36f, 0f, 0.66f, -0.56f)), // anticipate
        targetScale = 0f,
        transformOrigin = TransformOrigin(0.5f, 1f),
    ) + fadeOut(tween(100)),
) { MiniPlayer(...) }

// 3) 内容底部 inset 跟着 dock 高度走，不要写死
Scaffold(bottomBar = { Dock() }) { padding ->
    LazyColumn(contentPadding = PaddingValues(bottom = padding.calculateBottomPadding() + 24.dp))
}

// 4) 全屏页：150ms 进 / 200ms 出，都带轻微 overshoot
AnimatedVisibility(
    visible = playerOpen,
    enter = slideInVertically(tween(150, easing = CubicBezierEasing(0.34f, 1.56f, 0.64f, 1f))) { it },
    exit = slideOutVertically(tween(200, easing = CubicBezierEasing(0.34f, 1.56f, 0.64f, 1f))) { it },
) { NowPlayingScreen() }
```

`CubicBezierEasing(0.34f, 1.56f, 0.64f, 1f)` 是 Material 的 `overshoot` 近似曲线；
`anticipate` 的近似是 `(0.36f, 0f, 0.66f, -0.56f)`。两边的控制点都允许 y 越界，Compose 的 `CubicBezierEasing` 支持。

---

## 八、证据清单

| 结论 | 文件 |
| --- | --- |
| 手机端 dock 结构、两个 Barrier | `res/layout/vertical_main.xml` |
| 大屏分栏、比例 0.8 / 0.3333 | `res/layout/large_main.xml` |
| 出现：100ms scaleY + overshoot | `res/anim/slide_up_and_fade_in.xml` |
| 消失：100ms scaleY + anticipate | `res/anim/slide_down_and_fade_out.xml` |
| mini player 内部两态 100ms | `res/xml/now_playing_bar_scene.xml` |
| 全屏页 150 / 200ms | `res/anim/nowplaying_activity_enter|exit.xml` |
| 渐变遮罩色值 | `bottomColor="#D9000000"`，topColor 透明 |
| 尺寸值 | `encore_spacer_base=16`、`floating_now_playing_bar_height=56`、`corner_radius=8`、`bottom_navigation_bar_top_margin=24` |

**没能拿到的**：mini player 容器高度本身是否有 `ValueAnimator` 补间（布局重排的时长）。
XML 里没有对应资源，`MainLayout` 的 Kotlin 实现被混淆，只能确认 View 动画是 100ms 那一套。
如果高度是瞬时变化，视觉上会被 `scaleY` + 渐变掩盖 —— 这也是为什么只做 100ms 就够。
