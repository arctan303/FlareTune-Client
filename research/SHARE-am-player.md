# Apple Music 全屏播放页 · 反编译研究简报

> **这份文档是给外部评审方（人或 AI）看的自包含版本**：不需要任何其它文件即可完整理解。
> 研究方式：APK 静态反编译（布局 / 动画 / 资源表 / dalvik 指令）+ 商店截图像素测量。
> 每条结论标注证据等级：`[APK]` 直接读出 · `[截图]` 像素测量 · `[推断]` 未经真机验证。

---

## 0. 上下文：这份研究是为谁做的

**FlareTune Client** 是一个**纯消费型**音乐播放客户端（Android 优先，技术栈未定）。
"纯消费型"意味着用户不做内容管理，只做两件事——**选**和**听**。

产品定位已定：**首页信息架构取 Spotify，全屏播放页取 Apple Music，品牌色用暖橙 `#FF7A45`**。
本文只研究 Apple Music 那一半——**播放页的背景、动效、歌词**。

已有的一份设计提案里，播放页的取值是：
封面模糊 48–64dp + 55% 遮罩 / 歌词 20sp / 进度条 2dp / 播放键 56dp 实心。
**本文的实测结果与这份提案有多处冲突**（见 §7），需要外部判断哪些该改、哪些不该跟。

---

## 1. 样本与方法

| 项 | 值 |
| --- | --- |
| 包名 | `com.apple.android.music` |
| 版本 | 7.0.0（versionCode 1614） |
| 来源 | APKPure 分发包（base + arm64 + mdpi），113.7 MB |
| minSdk / targetSdk | 30 / 36 |
| 资源规模 | 1241 个 layout、73 个 anim、36 个 animator、4 个 dex（36 MB） |

工具：`androguard` 读 `resources.arsc`、`AXMLPrinter` 反编译二进制 XML、
自定义脚本 dump dalvik 指令（只看常量与方法调用，足够还原逻辑）。

**两个必须知道的解析坑**（如果复现会踩到）：

1. androguard 解析 `complex` 类型的 float 时会**右移 8 位**，`raw << 8` 才是真正的 IEEE754 位模式。
   例：`4161536 << 8 = 0x3F800000 = 1.0f`；`4096000 << 8 = 0x3E800000 = 0.25f`。
2. dimens 有多套限定符覆盖（`values-sw600dp` / `values-xxhdpi` 等）。
   本文所有数值取自**默认 values（手机竖屏）**，与宽屏目录的值不同，不构成矛盾。

---

## 2. 结论摘要

1. **背景是每帧实时合成的，不是一张预模糊图。** 封面放大 1.3× → 三层不同周期旋转 → 饱和度 ×2.5 →
   高斯模糊 radius 25。三个旋转周期 **120s / 90s / 70s**，用**真实时间**驱动（暂停后仍在流动）。
2. **下半屏靠"镜像模糊"填。** 封面只占上方 3:4，下方用封面底边的翻转副本（`scaleY=-1`）+ 模糊铺满，
   接缝处往封面里多压 22dp 防止露白边。这是性价比最高的一招，不需要任何额外素材。
3. **进出场 250ms / 300ms，都是 `decelerate`。出比进慢。**
   另有 `activity_hold`（`zAdjustment=-1`），说明是双 Activity 叠层——播放页"盖上来"，不是"推走"。
4. **播放页是 BottomSheet，`peekHeight = 0`。** 收起时完全不可见，不存在"半露的播放条"。
   滑到 30% 时切换系统栏明暗。
5. **歌词 34sp**（不是 24sp）。次级行 22sp，行距 32dp，静态歌词行距 40dp。
   非当前行 `scale 0.98` + `alpha 45%`，当前行 100%。
6. **逐字高亮 = 每个字一个独立 TextView + 改颜色**，不是 Spannable。
   整行边缘羽化用 `saveLayer` + `LinearGradient` shader 做遮罩，羽化宽度 **30dp**，
   唱到的音节**上浮 2dp**。
7. **行切换不做 ItemAnimator 动画**（`animateChange` 直接 `return true`）。动感全靠滚动本身。
   手动滚动后 **7 秒**才恢复自动跟随。
8. **颜色只有白色四档 alpha：94 / 45 / 18 / 8**。不调色相、不加灰——
   因为背景是流动的彩色，任何固定灰都会显脏。

---

## 3. 页面层级

```
RelativeLayout
└── ConstraintLayout @id/player_root
    ├── LyricsBackgroundLayerView @id/background_layers   ← 流动背景，初始 alpha=0
    │       background = 16dp 顶部圆角 + #FF424242 纯色打底
    ├── Guideline（歌词缩略图的对齐基准，begin = 32dp）
    ├── ViewSwitcher @id/motion_switcher                  ← 动态封面图层，初始 alpha=0
    │       inAnimation = @android:anim/fade_in
    │       outAnimation = @android:anim/fade_out
    │   ├── include @id/motion_container1
    │   └── include @id/motion_container2      ← 两个容器交替，切歌时整体交叉淡入
    ├── FrameLayout @id/player_fragments_host             ← 歌词 / 队列页
    │   └── View @id/nav_bar_divider  (#14FFFFFF)
    └── include @id/mini_player                           ← 同一份 mini player 复用
```

**两个 `motion_container` + `ViewSwitcher`** 是关键设计：切歌不是"换一张图"，
而是**先在第二个容器里准备好新封面，再整体交叉淡入**。避免了封面跳变，也不用自己写 crossfade。

### motion 容器内部

```
ConstraintLayout
├── TextureView @id/motion_texture
│       0dp × 0dp，constraintDimensionRatio = "H,3:4"，贴顶、左右撑满
├── PlayerTransitionImageView @id/motion_preview_frame
│       完全覆盖 texture（封面静态帧，用于过渡）
├── Barrier @id/motion_barrier
│       barrierDirection = bottom，barrierMargin = -22dp，ref = motion_texture
├── View @id/motion_legibility_gradient     全屏（文字可读性渐变）
├── View @id/motion_blur
│       scaleY = -1（垂直翻转），top 约束到 motion_barrier
│       → 从封面底边往下铺满剩余区域，是封面的"镜像模糊"
└── View @id/motion_color_gradient_overlay
        top 约束到 motion_texture 的底部（只在封面下方那块）
```

---

## 4. 背景效果：完整机制

`LyricsBackgroundLayerView` 是自定义 View，收到封面后在 `onDraw` 里按当前时间**实时合成**背景 bitmap。

### 4.1 生成流程（`b(Bitmap, w, h, timeMs, Context)` 反编译还原）

```
1. 目标尺寸 = 视图尺寸 × 1.3                     （常量 1067869798 = 1.3f）
2. createBitmap(w × 1.3, h × 1.3, ARGB_8888)
3. 三层 drawBitmap，每层：
     Matrix.setScale(k, k)                      k = 1.3 × 目标尺寸 / 原图尺寸
     Matrix.postRotate(animValue, cx, cy)       三个 ValueAnimator 的当前角度
     Matrix.postTranslate(dx, dy)               各自的偏移
     Paint.setColorFilter(ColorMatrixColorFilter(
         ColorMatrix().setSaturation(2.5f)))    常量 1075838976 = 2.5f
4. 用封面取到的两个颜色 drawPaint 打底
5. Toolkit.a(bitmap, 25)                       RenderScript 高斯模糊，radius = 25
6. createBitmap + drawBitmap 缩放输出
```

### 4.2 三个旋转动画

```java
ValueAnimator a = ValueAnimator.ofFloat(...);
a.setDuration(120000);                          // 120 秒一圈
a.setInterpolator(new LinearInterpolator());
a.setRepeatCount(-1);                           // 无限

// 另外两个：90000ms、70000ms，同样是 Linear + 无限
```

三层周期 **120s / 90s / 70s**，互质程度不高但足够错开，叠加后"永远不重复"。

每帧都调 `setCurrentPlayTime(System.currentTimeMillis())`——**真实时间驱动**，
与播放进度无关。所以暂停时背景仍在流动，切歌也不重新计时。

### 4.3 常量还原

| 常量（raw） | float | 用途 |
| --- | --- | --- |
| `1067869798` | **1.3** | 放大倍数 / 旋转半径系数 |
| `1075838976` | **2.5** | 饱和度 |
| `-1082969293` | **-1.0** | 第 2 层 X 偏移系数 |
| `-1087163597` | **-0.8** | 第 2 层 Y 偏移系数 |
| `-1090519040` | **-2.0** | 第 3 层平移基数 |
| `1060320051` | **0.7** | 第 3 层 Y 系数 |

### 4.4 性能处理

```java
HashMap<Pair<Integer,Integer>, Pair<Bitmap, Canvas>> cache;
```

按「视图宽高」做 key 缓存已生成的背景，尺寸不变就不重算。
`setReducedEffects(boolean)` 可以整套关掉（系统"减少动画"或低电量）。

### 4.5 遮罩层

| 层 | 值 | 作用 |
| --- | --- | --- |
| `motion_legibility_gradient` | 全屏渐变，基色 `#B3171717`（70% 黑） | 保证白字压得住 |
| `player_motion_color_overlay_black_scrim` | `#59000000`（35% 黑） | 叠在封面下方 |
| `lyrics_bg_layer_black_scrim` | `#4D000000`（30% 黑） | 歌词列表背后 |
| `bg_lyrics_page_default` | `#FF424242` + 顶部圆角 16dp | 没拿到封面时的兜底 |

---

## 5. 动效

`res/anim/` 里真正属于播放页的只有四个文件，其余全是 AppCompat/Material 自带。

```xml
<!-- activity_slide_up.xml -->
<translate duration="@integer/slide_animation_duration"   <!-- = 250 -->
           interpolator="@android:anim/decelerate_interpolator"
           fromYDelta="100%p" toYDelta="0"/>

<!-- activity_slide_down.xml -->
<translate duration="300"
           interpolator="@android:anim/decelerate_interpolator"
           fromYDelta="0" toYDelta="100%p"/>

<!-- activity_fade_in.xml -->
<alpha duration="200" interpolator="@android/interpolator/accelerate_decelerate"
       fromAlpha="0" toAlpha="1"/>

<!-- activity_hold.xml -->  ← 给下层 Activity 的"占位不动"
<translate duration="250" zAdjustment="-1"/>
```

| 方向 | 时长 | 曲线 |
| --- | --- | --- |
| 进 | **250ms** | decelerate |
| 出 | **300ms** | decelerate |
| 背景淡入 | 200ms | accelerate_decelerate |
| 通用滑动（BottomSheet/Fragment） | 200ms | 默认（`config_shortAnimTime`） |

**出比进慢 50ms**：进是用户主动的、想快；出要"退回"，慢一点更从容。

`activity_hold` 的存在说明这是**双 Activity 叠层**：上层滑入时下层不动，只被 `zAdjustment=-1` 压到后面。

### BottomSheet

`PlayerBottomSheetBehavior extends BottomSheetBehavior`，只覆写了 4 个方法
（`getExpandedOffset` / `onNestedPreScroll` / `onNestedScroll` / 拖拽判定）。

```java
// onBottomSheetSlide(float)
if (slide <= 0.3f) {   // 常量 1050253722 = 0.3f
    // 切换系统栏明暗
}
```

`player.xml` 里 `app:behavior_peekHeight = @dimen/player_peek_height` = **0dp**。

### 唯一的循环动画

```xml
<!-- rotation_anim.xml -->
<set interpolator="@android:anim/linear_interpolator">
  <objectAnimator duration="500" repeatCount="-1"
                  valueFrom="0" valueTo="360" propertyName="rotation"/>
</set>
```

500ms 一圈。**不是黑胶**（太快），是加载/缓冲指示器。

---

## 6. 歌词

### 6.1 结构

```
ConstraintLayout  (marginTop 32dp, 黑底)
├── 顶部当前曲目条
│   ├── MaterialCardView  圆角 5dp，描边 0.5dp #1A000000
│   │   ├── TextureView (60×60，动态封面)
│   │   └── PlayerTransitionImageView
│   ├── 标题（BodyEmphasized，marquee 只跑 1 次）
│   ├── 副标题（Body，alpha 45%）
│   └── 喜欢 / 更多
├── AlphaGradientFrameLayout @id/recycler_view_gradients   ← 上下边缘渐隐
│   └── RecyclerView @id/lyrics_main_content
├── Guideline @id/tap_target_guideline   (42.5%)
├── FrameLayout @id/controls             (高度 = 屏高 34.5%)
│   └── include 控件区
├── ImageView @id/translations_button
├── VocalAttenuationControl @id/vocal_ctrl      ← 人声衰减旋钮（卡拉OK）
└── FrameLayout @id/loading_progress
```

**控件区固定占屏高 34.5%**，点击区从 **42.5%** 开始——中间 8% 是"点了不跳转也不误触"的缓冲带。

### 6.2 字号与间距

| 资源 | 值 | 样式 |
| --- | --- | --- |
| `lyrics_line_text_size` | **34sp** | `Title0Emphasized` |
| `lyrics_line_secondary_text_size` | **22sp** | `Title2ShortEmphasized` |
| `lyrics_line_static_line_height` | 26sp | `Body`（静态歌词） |
| `lyrics_line_between_line_spacing` | **32dp** | |
| `lyrics_static_between_line_spacing` | **40dp** | |
| `lyrics_static_top_margin` | 30dp | |
| `lyrics_text_padding_top` / `_bottom` | 2 / 10dp | |
| `lyrics_text_padding_horiz` | 12dp | |
| `lyrics_line_margin_horiz` | 20dp | |
| `lyrics_line_padding_horiz`（静态） | 32dp | |

行内左右 = 20 + 12 = **32dp**。

34sp 在 1080p 屏上"一屏只放 4~5 行"。Apple 敢这么做的前提是**把歌词当海报排版，不是当文本**。

### 6.3 行状态

```xml
<dimen name="lyrics_line_scale_normal">1065017672</dimen>   <!-- = 0.98f -->
<dimen name="lyrics_line_scale_highlight">…</dimen>         <!-- ≈ 1.0f，见 §9 -->
```

非当前行缩到 **98%**，当前行 100%。**只有 2% 的差异**，配合颜色（94% vs 45% 白）层级就立起来了。
不用 0.8 / 1.2 那种夸张缩放。

### 6.4 逐字卡拉OK

两套布局，分别对应「主唱行」和「背景人声行」：

```xml
ConstraintLayout @id/karaoke_lyrics_line_container
├── FullWidthAlphaGradientFlexboxLayout @id/karaoke_line_flexbox   ← 主行，34sp
│       paddingTop = 2dp, paddingHorizontal = 12dp, alignItems = baseline
├── FullWidthAlphaGradientFlexboxLayout @id/bg_line_flexbox        ← 背景人声，22sp
│       paddingTop = 22dp
```

**每个字是一个独立的 `CustomTextView`**（`lyrics_word_karaoke.xml`），用 Flexbox 排成一行。
逐字上色 = 改每个 word 的 `textColor`，不需要复杂 span。

`FullWidthAlphaGradientFlexboxLayout` 负责整行羽化：

```java
// dispatchDraw 反编译还原
for (SparseArray<Mask> masks : new SparseArray[]{mMasks, mBgMasks}) {
    for (Mask m : masks) {
        if (m.dirty) {
            m.rect.set(0, top + m.topInset, width, height - bottomInset);
            m.paint.setShader(new LinearGradient(
                0, m.rect.top, width, m.rect.bottom,
                m.colors, m.positions, Shader.TileMode.CLAMP));
            m.dirty = false;
        }
    }
}
int save = canvas.saveLayer(0, 0, w, h, mLayerPaint);   // 整层合成
super.dispatchDraw(canvas);
// 再逐 mask drawRect 做 alpha 遮罩
canvas.restoreToCount(save);
```

**关键**：用 `saveLayer` + 一个共享的 `LinearGradient` 遮罩，而不是给每个 TextView 单独设 alpha。
这样一行里所有字共享同一个渐变坐标系，边缘过渡是连续的。

### 6.5 逐字动画参数

| 资源 | 值 | 含义 |
| --- | --- | --- |
| `lyrics_karaoke_gradient_feather_width` | **30dp** | 高亮边缘羽化宽度 |
| `lyrics_karaoke_syllable_lift` | **-2dp** | 唱到的音节向上抬 2dp |
| `lyrics_karaoke_emphasis_shadow_radius` | 5dp | 强调字阴影 |
| `lyrics_karaoke_badge_shadow_radius` / `_alpha` | 3dp / 0.6 | 徽章阴影 |
| `lyrics_karaoke_duet_line_width_percent` | 0.85 | 对唱行宽度占比 |
| `lyrics_instrumental_dot_size` / `_margin_horiz` | 10 / 6dp | 间奏圆点 |

**羽化 30dp** 在 34sp 的字上意味着高亮是"扫"过去而不是"切"过去。
**上浮 2dp** 几乎看不出来，但去掉之后整个效果会"平"掉。

### 6.6 滚动：不是动画，是计算

```java
// ItemAnimator（内部类）
@Override
public boolean animateChange(ViewHolder old, ViewHolder new, int a, int b, int c, int d) {
    return true;    // ← 直接返回 true，不做任何 item 动画
}
```

行内容更新是**瞬时的**，动感全靠滚动本身。如果每行还各自做 change 动画，会和滚动打架。

```java
if (y2(i) < 7000L) { ... }   // 7000ms = 7 秒
```

用户手动滚动后 **7 秒内不自动跟随**，之后恢复自动居中。

### 6.7 边缘渐隐容器

```xml
<AlphaGradientFrameLayout
    app:gradient_edge="0x0000000F"   <!-- 1=左 2=上 4=右 8=下 → 四边全开 -->
    ...>
```

`getBottomFadePositions()` 返回 `float[]`，可运行时调整。
进入/退出歌词页时，**边缘渐变位置和控件透明度是一起动画的**——所以控件是"沉下去"而不是"消失"。

---

## 7. 控件区与 mini player

| 元素 | 规格 |
| --- | --- |
| 进度条 `MinimalSeekBar` | 高 **7dp**，圆角 **3.5dp**（全圆角胶囊），上下 padding 5dp |
| 进度 thumb | 9dp；按下 36dp |
| 进度条左右边距 | 32dp |
| 时间文字 | `Caption1Emphasized` + **等宽数字**（`enableMonospaceDigits=true`） |
| 播放键 | `wrap_content` + padding 11dp（图标 30dp → 触控 52dp） |
| 上一首/下一首 | 同上，与播放键间距 **40dp** |
| 歌词/队列按钮 | 播放键左右下方，`marginBottom = 14dp` |
| 音质徽章 | 圆角 5dp，11sp 文字，padding 7 × 3.5dp |
| 播放页最小宽 | 320dp ｜ 平板最大高 500dp |

**进度条 7dp 是个意外值**——比 Spotify 的 2dp 粗得多。配合 34sp 的歌词，整体是"粗笔画"路线。

**mini player**（同页复用，`mini_player.xml`）：

| 元素 | 值 |
| --- | --- |
| 高 | **48dp** |
| 缩略图 | 30dp，圆角 5dp |
| 按钮 | 32dp |
| 内边距 / 外边距 | 左右 16dp / 左右 16dp |
| 最大宽 | 600dp |
| 与 nav tabs 间距 | 8dp |

### 颜色阶梯

| token | 值 | 用途 |
| --- | --- | --- |
| `player_vibrant_primary` | `#F0FFFFFF`（94%） | 歌词当前行、曲名 |
| `player_vibrant_secondary` | `#73FFFFFF`（45%） | 艺人名、副标题、进度条已播放 |
| `player_vibrant_tertiary` | `#2EFFFFFF`（18%） | 未播放轨道、次要图标 |
| `player_vibrant_quarternary` | `#14FFFFFF`（8%） | 分隔线、极弱元素 |

---

## 8. 与现有 FlareTune 提案的冲突

| 项 | 提案 | Apple 实测 | 是否建议跟 |
| --- | --- | --- | --- |
| 背景 | 封面模糊 48–64dp + 55% 遮罩 | 三层旋转 + 饱和度 2.5 + 模糊 25 | **部分跟**：三层旋转成本高，镜像模糊必抄 |
| 歌词字号 | 20sp | **34sp** | **不跟**（中文 34sp 一行只有 9~10 字，见下） |
| 歌词非当前行 | 45% 不透明 | 45% + scale 0.98 | **跟**（0.98 这个细节值得抄） |
| 进度条 | 2dp | **7dp 胶囊** | 待定（7dp 更"粗壮"，与 34sp 歌词匹配） |
| 播放键 | 56dp 实心 | wrap_content + 11dp padding（≈52dp 触控） | 接近，不用改 |
| mini player | 72dp | **48dp** | 待定（48 更紧凑，但信息密度低） |
| 进场/退场 | 320ms 共享元素 | **250 / 300ms** 整屏位移 | **跟**（数值更短、更快） |
| 逐字羽化 | 24dp | **30dp** | 可按字号等比缩 |

### 34sp 的问题（这是最需要外部判断的一点）

- Apple 是"英文为主 + 海报式排版"，34sp 一行能放 12~15 个字母
- **中文 34sp 在 360dp 宽屏上一行只能放 9~10 个汉字**，一句歌词经常折 2 行
- 折行后 34sp 的行距会让一屏只剩 3 行，信息密度过低

折中方案：默认 24sp，另做一个"歌词海报模式"切到 30~34sp。

---

## 9. 已知局限（这些是**没拿到**的，不是结论）

1. **逐字高亮的时间轴推进**：`com.apple.android.music.ttml.javanative.LyricsController` 是 JNI 包装，
   时间对齐在 native 层（TTML 解析）。只能确认按 **syllable 粒度**驱动，看不到具体算法。
2. **歌词行滚动的插值曲线**：`RecyclerView` 的 smoothScroll 没有显式 duration，
   走 `LinearSmoothScroller` 默认速度（按像素/毫秒算），未反编译到具体系数。
3. **BottomSheet 的 peek / expanded 偏移**：`PlayerBottomSheetBehavior` 里没有常量，
   由父类按 `peekHeight` 计算，而 peekHeight 在代码里设，没找到赋值点（`player.xml` 里是 0）。
4. **`lyrics_line_scale_highlight` 的确切值**：ARSC 里被解析成 `4161536.0px`，
   按 `<<8` 还原是 `1.0f`，但同组的 `_normal` 是未经此变形的 `0.98`，编码方式不一致。
   保守标注为「≈1.0，待真机核实」。

---

## 10. 希望得到的反馈

1. **背景的 Compose 落地**：Apple 是每帧重建整张 bitmap（1.3× 屏宽 × 屏高，ARGB_8888 ≈ 4MB）。
   如果降到 10fps + `RenderEffect`，在中低端机上还扛得住吗？有没有更省的做法
   （比如预渲染 8~12 帧循环播放）？
2. **34sp 的取舍**：中文场景下，是坚持 24sp 稳妥，还是做双模式（24 / 34）？双模式的切换点怎么设计？
3. **逐字高亮的替代实现**：Apple 用"每字一个 View"，中文一个字一个 View 数量会翻倍（英文按单词切，中文按字切）。
   在 Compose 里有没有更合适的方案（`AnnotatedString` + 逐段改 style？自定义 `Text` 绘制？）
4. **哪些结论看起来不可信**：本文是从 dalvik 指令和资源表还原的，可能有解读错误。
   特别是 §4.1 的三层叠加逻辑（如果是纯 SRC_OVER，前两层会被完全覆盖，那三层的意义是什么？）
5. **250 / 300ms 这组不对称时长**在 Material Motion 体系里是否合理？还是 Apple 自己的口味？

---

*2026-11-12 · 基于 Apple Music 7.0.0 (1614) APK 静态分析*
