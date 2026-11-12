# Apple Music 全屏播放页拆解（背景 / 动效 / 歌词）

研究对象：Apple Music Android **7.0.0 (1614)**，`com.apple.android.music`。
证据来源：`res/layout`、`res/anim`、`res/values`（dimens/colors）反编译 + 4 个 dex 的 dalvik 指令。
原始 XML 在 [`../evidence/layouts/applemusic/`](../evidence/layouts/applemusic/)。

> **本篇修正了 [04-applemusic.md](04-applemusic.md) 里两处早期错误**（歌词字号、mini player 高度），见 §8。

---

## 一句话结论

Apple Music 的播放页是**一个 BottomSheet 上盖着三层叠加物**：
底层是「封面放大 1.3× → 三层不同周期旋转 → 饱和度拉到 2.5 → RenderScript 模糊 25」的
**实时生成的流动色彩背景**；中层是歌词列表（34sp 巨字，逐字渐变遮罩，非当前行缩到 98%）；
顶层是控件区（占屏高 34.5%）。
进出场是 **250ms 进 / 300ms 出**，都用 `decelerate`——**慢进快出相反：进得快、出得更慢**。

---

## 一、页面层级

`res/layout/fragment_player_main.xml`（`PlayerMainFragment`）：

```
RelativeLayout
└── ConstraintLayout @id/player_root
    ├── LyricsBackgroundLayerView @id/background_layers   ← 流动背景，初始 alpha=0
    │       background = @drawable/bg_lyrics_page_default
    │       （16dp 顶部圆角 + #FF424242 纯色打底）
    ├── Guideline @id/lyrics_queue_thumbnail_top_guideline
    ├── Guideline @id/lyrics_queue_thumbnail_start_guideline   (begin = 32dp)
    ├── ViewSwitcher @id/motion_switcher                  ← 动态封面图层，初始 alpha=0
    │       inAnimation  = @android:anim/fade_in
    │       outAnimation = @android:anim/fade_out
    │   ├── include @id/motion_container1  → view_motion_container
    │   └── include @id/motion_container2  → view_motion_container
    ├── FrameLayout @id/player_fragments_host             ← 歌词/队列页放这里
    │   └── View @id/nav_bar_divider
    │           background = @color/player_vibrant_quarternary  (#14FFFFFF)
    └── include @id/mini_player                           ← 同一份 mini player 复用
```

**两个 `motion_container` + `ViewSwitcher` 的 fade** 是关键设计：
切歌时不是"换一张图"，而是**预先在第二个容器里准备好新封面，然后整体交叉淡入**。
这样避免了封面直接跳变，也不需要自己做 crossfade 动画。

### motion 容器内部

`res/layout/view_motion_container.xml`：

```
ConstraintLayout
├── TextureView @id/motion_texture
│       0dp × 0dp，constraintDimensionRatio = "H,3:4"，贴顶、左右撑满
├── PlayerTransitionImageView @id/motion_preview_frame
│       完全覆盖 motion_texture（封面静态帧，用于过渡）
├── Barrier @id/motion_barrier
│       barrierDirection = bottom，barrierMargin = -22dp，ref = motion_texture
├── View @id/motion_legibility_gradient     全屏（文字可读性渐变）
├── View @id/motion_blur
│       scaleY = -1（垂直翻转），top 约束到 motion_barrier
│       → 从封面底边往下铺满剩余区域，是封面的"镜像模糊"
└── View @id/motion_color_gradient_overlay
│       top 约束到 motion_texture 的底部（只在封面下方那块）
```

**"镜像模糊"是 Apple Music 最容易被忽略的一招**：封面只占屏幕上方 3:4 的一块，
下方空出来的区域用「封面底边的翻转副本 + 模糊」填满，所以整屏都是同一张图的气质，
但没有生硬的分界线。`barrierMargin = -22dp` 让镜像层往封面里**多压 22dp**，
保证接缝处不会露白边。

---

## 二、背景效果：不是一张模糊图，是实时算的

`LyricsBackgroundLayerView` 是自定义 View，`setArtwork(Bitmap)` 收到封面后，
在 `onDraw` 里按当前时间**实时合成**一张背景 bitmap。

### 2.1 生成流程（`b(Bitmap, w, h, timeMs, Context)` 反编译还原）

```
1. 目标尺寸 = 视图尺寸 × 1.3        （常量 1067869798 = 1.3f）
2. createBitmap(w×1.3, h×1.3, ARGB_8888)
3. 三层 drawBitmap，每层：
     Matrix.setScale(k, k)                    k = 1.3 × 目标尺寸 / 原图尺寸
     Matrix.postRotate(animValue, cx, cy)     三个 ValueAnimator 的当前值
     Matrix.postTranslate(dx, dy)             各自的偏移
     Paint.setColorFilter(ColorMatrixColorFilter(
         ColorMatrix().setSaturation(2.5f)))   常量 1075838976 = 2.5f
4. 用封面取到的两个颜色 drawPaint 打底（循环 drawPaint，后一层盖前一层）
5. Toolkit.a(bitmap, 25) → RenderScript 高斯模糊，radius = 25
6. 再 createBitmap + drawBitmap 缩放输出
```

### 2.2 三个旋转动画

```java
ValueAnimator a = ValueAnimator.ofFloat(...);   // 角度
a.setDuration(120000);                          // 120 秒一圈
a.setInterpolator(new LinearInterpolator());
a.setRepeatCount(-1);                           // 无限

ValueAnimator b = ...; b.setDuration(90000);    // 90 秒
ValueAnimator c = ...; c.setDuration(70000);    // 70 秒
```

三层周期 **120s / 90s / 70s**，互质程度不高但足够错开，
叠加后的视觉效果是"永远不重复的缓慢流动"。

关键：每帧都调 `setCurrentPlayTime(System.currentTimeMillis())`，
**用真实时间驱动**，与播放进度无关。所以暂停时背景仍在缓慢流动，
切歌也不会重新计时（时间轴是绝对的）。

### 2.3 平移系数（常量还原）

| 常量（raw） | float | 用途 |
| --- | --- | --- |
| `1067869798` | **1.3** | 放大倍数 / 旋转半径系数 |
| `1075838976` | **2.5** | 饱和度 |
| `-1082969293` | **-1.0** | 第 2 层 X 偏移系数 |
| `-1087163597` | **-0.8** | 第 2 层 Y 偏移系数 |
| `-1090519040` | **-2.0** | 第 3 层平移基数 |
| `1060320051` | **0.7** | 第 3 层 Y 系数 |

三层分别往不同方向偏移，叠加出"色块在缓慢漂移"的错觉。

### 2.4 性能处理

```java
HashMap<Pair<Integer,Integer>, Pair<Bitmap, Canvas>> cache;
```

按「视图宽高」做 key 缓存已生成的背景，尺寸不变就不重算。
`onSizeChanged` / `onDraw` 都走这个缓存 —— 旋转屏或分屏时才重建一次。

`setReducedEffects(boolean)` 可以关掉这套动画（系统"减少动画"或低电量模式）。

### 2.5 可读性遮罩（两层）

| 层 | 值 | 作用 |
| --- | --- | --- |
| `motion_legibility_gradient` | 全屏渐变，基色 `#B3171717`（70% 黑） | 保证白字压得住 |
| `player_motion_color_overlay_black_scrim` | `#59000000`（35% 黑） | 叠在封面下方那块 |
| `lyrics_bg_layer_black_scrim` | `#4D000000`（30% 黑） | 歌词列表背后 |
| `bg_lyrics_page_default` | `#FF424242` + 顶部圆角 16dp | 还没拿到封面时的兜底 |

---

## 三、进出场动效

`res/anim/` 里真正属于播放页的只有四个文件，其余全是 AppCompat/Material 自带的。

### 3.1 全屏播放页（Activity 级）

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
<alpha duration="200" interpolator="@android:interpolator/accelerate_decelerate_interpolator"
       fromAlpha="0" toAlpha="1"/>

<!-- activity_hold.xml -->  ← 给下层 Activity 用的"占位不动"
<translate duration="250" zAdjustment="-1"/>
```

| 方向 | 时长 | 曲线 |
| --- | --- | --- |
| 进 | **250ms** | decelerate（先快后慢） |
| 出 | **300ms** | decelerate |
| 背景淡入 | 200ms | accelerate_decelerate |

**进出不对称：出比进慢 50ms。** 进的时候用户是主动的、想快；出的时候要"退回"，
慢一点更从容，也更不容易误触。

`activity_hold` 的存在说明这是**双 Activity 叠层**：上层滑入时，下层不跟着动，
只是被 `zAdjustment=-1` 压到后面。所以视觉上是"播放页盖上来"，不是"页面被推走"。

### 3.2 通用滑动（给 BottomSheet / Fragment 用）

```xml
<!-- slide_up.xml -->
<translate duration="@android:integer/config_shortAnimTime"   <!-- 200ms -->
           fromYDelta="100%" toYDelta="0"/>
<!-- slide_down.xml 对称 -->
```

`config_shortAnimTime` = 200ms，无自定义插值器（走默认 `accelerate_decelerate`）。

### 3.3 唯一一个循环动画

```xml
<!-- rotation_anim.xml -->
<set interpolator="@android:anim/linear_interpolator">
  <objectAnimator duration="500" repeatCount="-1"
                  valueFrom="0" valueTo="360" propertyName="rotation"/>
</set>
```

500ms 一圈、无限循环。**这不是黑胶**（太快），是**加载/缓冲指示器**的旋转。

### 3.4 播放页是 BottomSheet

`com.apple.android.music.player.PlayerBottomSheetBehavior extends BottomSheetBehavior`
（Material 的）。它只覆写了 4 个方法：

```
f(View)F                       ← getExpandedOffset（展开时的顶部偏移）
p/q(...)                       ← onNestedPreScroll / onNestedScroll（手势接管）
l/m(...)                       ← touch / 拖拽判定
```

以及 `onBottomSheetSlide(float)` 回调：

```java
// 反编译还原
if (slide <= 0.3f) {   // 常量 1050253722 = 0.3f
    x2(getActivity());  // 切状态栏 / 系统栏配色
}
```

**滑到 30% 时切换系统栏明暗** —— 因为背景从"浅色封面"变成"深色遮罩"了。

---

## 四、歌词：34sp 巨字 + 逐字渐变

### 4.1 结构

`res/layout/fragment_player_lyrics_sheet.xml`：

```
ConstraintLayout  (marginTop 32dp, 黑底)
├── LinearLayout @id/current_player_item          ← 顶部当前曲目条
│   ├── MaterialCardView @id/lyrics_thumbnail_container
│   │       cardCornerRadius = 5dp，stroke 0.5dp #1A000000
│   │   ├── TextureView @id/video_surface        (60×60，动态封面)
│   │   └── PlayerTransitionImageView @id/thumbnail
│   ├── 标题（BodyEmphasized，marquee 只跑 1 次）
│   ├── 副标题（Body，alpha = 0.45）
│   ├── ImageView @id/list_favorite_icon
│   └── ImageView @id/list_left_icon (更多)
├── AlphaGradientFrameLayout @id/recycler_view_gradients   ← 上下边缘渐隐
│   └── RecyclerView @id/lyrics_main_content
├── Guideline @id/tap_target_guideline   (42.5%)
├── FrameLayout @id/controls             (高度 = 34.5%)
│   └── include constraint_player_media_controls
├── ImageView @id/translations_button
├── VocalAttenuationControl @id/vocal_ctrl      ← 人声衰减旋钮（卡拉OK 用）
└── FrameLayout @id/loading_progress
```

**控件区固定占屏高 34.5%**（`player_controls_height_percent = 0.345`），
点击区从 **42.5%** 开始（`lyrics_controls_tap_target_height_percent = 0.425`）——
两者之间那 8% 是"点了不跳转也不误触"的缓冲带。

### 4.2 字号（修正 04 文档）

| 资源 | 值 | 样式 |
| --- | --- | --- |
| `lyrics_line_text_size` | **34sp** | `Title0Emphasized` |
| `lyrics_line_secondary_text_size` | **22sp** | `Title2ShortEmphasized` |
| `lyrics_line_static_line_height` | 26sp | `Body`（静态歌词） |
| `lyrics_line_writers_credits_text_size` | 22sp | 词曲作者行 |
| `lyrics_translations_menu_item_text_size` | 17sp | 翻译菜单 |

**主歌词 34sp**。之前的 24sp 是错的 —— 那个数字更接近 secondary（22sp）。
34sp 在 1080p 屏上是"一屏只放 4~5 行"的密度，这是 Apple Music 敢做大字的前提：
它把歌词当**海报**排版，不是当文本。

### 4.3 间距

| 资源 | 值 |
| --- | --- |
| `lyrics_line_between_line_spacing` | **32dp** |
| `lyrics_static_between_line_spacing` | **40dp** |
| `lyrics_static_top_margin` | 30dp |
| `lyrics_text_padding_top` | 2dp |
| `lyrics_text_padding_bottom` | 10dp |
| `lyrics_text_padding_horiz` | 12dp |
| `lyrics_line_margin_horiz` | 20dp |
| `lyrics_line_padding_horiz`（静态） | 32dp |

行内左右 = 20 + 12 = **32dp**；静态歌词直接 32dp。

### 4.4 行状态：0.98 / 1.0

```xml
<!-- res/values/dimens.xml -->
<dimen name="lyrics_line_scale_normal">1065017672</dimen>      <!-- = 0.98f -->
<dimen name="lyrics_line_scale_highlight">...</dimen>          <!-- = 1.0f -->
```

非当前行缩到 **98%**，当前行 100%。
**只有 2% 的差异**，但配合颜色（94% 白 vs 45% 白）和行距，视觉层级就立起来了。
这个手法很值得抄：不要用 0.8 / 1.2 那种夸张缩放，2% 就够了。

### 4.5 逐字卡拉OK

两套布局，分别对应「主唱行」和「背景人声行」：

```xml
<!-- lyrics_line_karaoke.xml -->
ConstraintLayout @id/karaoke_lyrics_line_container
├── FullWidthAlphaGradientFlexboxLayout @id/karaoke_line_flexbox   ← 主行，34sp
│       paddingTop = 2dp, paddingHorizontal = 12dp
│       alignItems = 4 (baseline), flexWrap = 1
├── FullWidthAlphaGradientFlexboxLayout @id/bg_line_flexbox        ← 背景人声，22sp
│       paddingTop = 22dp（lyrics_bg_lyrics_margin_top）
```

**每个字是一个独立的 `CustomTextView`**（`lyrics_word_karaoke.xml`），
用 `FlexboxLayout` 排成一行。逐字上色 = 改每个 word 的 `textColor`，
不需要 `Spannable` 的复杂 span。

`FullWidthAlphaGradientFlexboxLayout` 的作用是**给整行加左右羽化**，
让文字在容器边界处淡出，而不是硬切：

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

用 `saveLayer` + `LinearGradient` shader 的 mask 来做"整行边缘羽化"，
而不是给每个 TextView 单独设 alpha —— 这样一行里所有字共享同一个渐变坐标系，
边缘过渡是连续的。

### 4.6 逐字动画参数

| 资源 | 值 | 含义 |
| --- | --- | --- |
| `lyrics_karaoke_gradient_feather_width` | **30dp** | 高亮边缘羽化宽度 |
| `lyrics_karaoke_syllable_lift` | **-2dp** | 唱到的音节向上抬 2dp |
| `lyrics_karaoke_emphasis_shadow_radius` | 5dp | 强调字阴影半径 |
| `lyrics_karaoke_badge_shadow_radius` | 3dp | 徽章阴影半径 |
| `lyrics_karaoke_badge_shadow_alpha` | **0.6** | 徽章阴影透明度 |
| `lyrics_karaoke_duet_line_width_percent` | **0.85** | 对唱行宽度占比 |
| `lyrics_instrumental_dot_size` | 10dp | 间奏圆点直径 |
| `lyrics_instrumental_dot_margin_horiz` | 6dp | 间奏圆点间距 |

**羽化 30dp** 是个很大的值 —— 在一个 34sp 的字上，30dp 的过渡带意味着
高亮不是"切"过去的，是"扫"过去的。这是 Apple Music 逐字效果显得柔和的原因。

**音节上浮 -2dp**：唱到的字往上抬 2dp。这个细节几乎看不出来，但去掉之后
整个效果会"平"掉。

### 4.7 歌词滚动：不是动画，是计算

`PlayerLyricsViewFragment` 里：

```java
// ItemAnimator 的实现（内部类 $u）
@Override
public boolean animateChange(ViewHolder old, ViewHolder new, int a, int b, int c, int d) {
    return true;    // ← 直接返回 true，不做任何 item 动画
}
```

**行切换不做 ItemAnimator 动画**。原因：歌词是"整体滚动"的，如果每行还各自做
change 动画，会和滚动打架。所以行内容的更新是**瞬时的**，动感全靠滚动本身。

滚动策略里有个关键常量：

```java
if (y2(i) < 7000L) { ... }   // 7000ms = 7 秒
```

用户手动滚动后 **7 秒内不自动跟随**，之后恢复自动居中。
这是"别跟我抢滚动条"的标准做法。

### 4.8 边缘渐隐容器

`AlphaGradientFrameLayout` 包住 `RecyclerView`：

```xml
<AlphaGradientFrameLayout
    app:gradient_edge="0x0000000F"   <!-- 低 4 位 flag：1=左 2=上 4=右 8=下 → 四边全开 -->
    ...>
```

`getBottomFadePositions()` 返回一个 `float[]`，可以运行时调整渐变位置。
`PlayerLyricsViewFragment.Q1(int)` 就是构造这个动画的：

```java
float[] fade = alphaGradientFrameLayout.getBottomFadePositions();
if (fade[0] == Float.NaN) { ... }        // 常量 2143289344 = NaN
ValueAnimator.ofFloat(...)               // 对 fade 位置做补间
+ ObjectAnimator.ofFloat(view, View.ALPHA, 0f)   // 同时把控件淡出
```

**歌词页进入/退出时，边缘渐变位置和控件透明度是一起动画的** ——
这就是为什么切到歌词页时，控件是"沉下去"的，而不是"消失"。

---

## 五、控件区规格

`constraint_player_media_controls.xml` + `player_progress_seek_bar.xml`：

| 元素 | 规格 |
| --- | --- |
| 进度条 `MinimalSeekBar` | 高 **7dp**，圆角 **3.5dp**（全圆角胶囊），上下 padding 5dp |
| 进度 thumb | 9dp；按下 36dp |
| 进度条左右边距 | 32dp（`player_seekbar_padding_horizontal`） |
| 时间文字 | `Caption1Emphasized` + `enableMonospaceDigits=true`（等宽数字） |
| 时间文字上下边距 | 10dp |
| 播放键 | `wrap_content` + padding 11dp（图标 30dp → 触控 52dp） |
| 上一首/下一首 | 同上，与播放键间距 **40dp** |
| 歌词/队列按钮 | 在播放键左右、下方，`marginBottom = 14dp` |
| 音质徽章 | 圆角 5dp，11sp 文字，padding 7×3.5dp |
| 播放页整体最小宽 | `player_width_min` = 320dp |
| 平板最大高 | `tablet_maximum_player_height` = 500dp |

进度条 7dp 是个意外值 —— 之前记的 "4dp" 是 `cast_seek_bar_progress_height`（投屏用的）。
**Apple Music 自己的进度条是 7dp 胶囊**，比 Spotify 的 2dp 粗得多。
配合 34sp 的歌词，这套设计整体是"粗笔画"路线。

### mini player（同页复用）

| 元素 | 值 |
| --- | --- |
| 高 | **48dp**（`miniplayer_height`） |
| 缩略图 | 30dp，圆角 5dp |
| 按钮 | 32dp |
| 内边距 | 左右 16dp |
| 外边距 | 左右 16dp |
| 最大宽 | 600dp |
| 与 nav tabs 间距 | 8dp |

> `mini_player.xml` 实际引用的是 `miniplayer_height`（48dp）。
> `compact_player_height = 88dp` 是另一个资源，在手机布局里没找到引用点（详见 §8）。

---

## 六、颜色阶梯（这是 Apple 最值得抄的部分）

播放页所有文字/图标只用**白色四档 alpha**，不引入第二个色相：

| token | 值 | 用途 |
| --- | --- | --- |
| `player_vibrant_primary` | `#F0FFFFFF`（94%） | 歌词当前行、曲名 |
| `player_vibrant_secondary` | `#73FFFFFF`（45%） | 艺人名、副标题、进度条已播放 |
| `player_vibrant_tertiary` | `#2EFFFFFF`（18%） | 未播放轨道、次要图标 |
| `player_vibrant_quarternary` | `#14FFFFFF`（8%） | 分隔线、极弱元素 |
| `player_vibrant_35` | `#59FFFFFF`（35%） | 中间态 |
| `lyrics_bg_layer_black_scrim` | `#4D000000`（30% 黑） | 歌词背后遮罩 |
| `player_motion_color_overlay_black_scrim` | `#59000000`（35% 黑） | 封面下方遮罩 |
| `player_motion_legibility_base` | `#B3171717`（70% 黑） | 可读性渐变基色 |
| `lyrics_bg_default` | `#FF424242` | 无封面时兜底 |

**94 / 45 / 18 / 8 这四档**就是全部层级。
不调色相、不加灰、不透明度解决一切 —— 因为背景是流动的彩色，任何固定灰都会"脏"。

---

## 七、FlareTune 怎么落地

### 7.1 背景：可以照抄，但要降级

Apple 的做法（三层旋转 + 饱和度 2.5 + 模糊 25）在 Compose 里可以这样实现：

```kotlin
// 1) 封面 → 三层合成图（用 Canvas + ColorMatrix）
fun buildFlowBackground(src: Bitmap, w: Int, h: Int, t: Long): Bitmap {
    val out = Bitmap.createBitmap((w * 1.3f).toInt(), (h * 1.3f).toInt(), ARGB_8888)
    val c = Canvas(out)
    val paint = Paint().apply {
        colorFilter = ColorMatrixColorFilter(ColorMatrix().apply { setSaturation(2.5f) })
    }
    // 三个周期：120s / 90s / 70s，角度 = (t % period) / period * 360
    val angles = listOf(120_000L, 90_000L, 70_000L).map { (t % it) / it.toFloat() * 360f }
    angles.forEachIndexed { i, deg ->
        val m = Matrix().apply {
            setScale(1.3f, 1.3f)
            postRotate(deg, out.width / 2f, out.height / 2f)
            postTranslate(offsets[i].x, offsets[i].y)
        }
        c.drawBitmap(src, m, paint)
    }
    return Toolkit.blur(out, 25f)   // 或 RenderEffect.createBlurEffect(25f,25f,CLAMP)
}

// 2) 每 16ms 重算太贵 → 降到 10fps 就够（背景是缓慢流动的）
LaunchedEffect(artwork) {
    while (true) {
        bg = buildFlowBackground(artwork, w, h, System.currentTimeMillis())
        delay(100)      // 10fps
    }
}
```

**必须降级的点**：

| Apple 原做法 | 成本 | FlareTune 建议 |
| --- | --- | --- |
| 每帧重建整张 bitmap | 高（1.3× 屏宽 × 屏高，ARGB_8888 ≈ 4MB） | 降到 10fps，且只在"背景层可见"时跑 |
| RenderScript blur radius 25 | RenderScript 已废弃 | `RenderEffect`（API 31+）/ 预渲染 3 档模糊图切换（低端机） |
| 三层 drawBitmap | 中 | 保留（这是效果的来源） |
| 真实时间驱动 | 零 | 保留（暂停时背景继续流动，是加分项） |

**更省的做法**：把三层旋转的结果**预渲染成 8~12 帧**，然后循环播。
视觉上几乎看不出差别（因为转速是 70s+/圈），但成本从"每帧合成"降到"换图"。

### 7.2 镜像模糊：强烈建议抄

`motion_blur` 那一层（scaleY=-1 + 模糊 + 从封面底边往下铺）是**性价比最高的一招**：

- 不需要额外素材
- 解决了"封面只占上半屏，下半屏空着"的问题
- 比整屏高斯模糊省一半算力（只模糊下方那块）

Compose 里：

```kotlin
Box(Modifier.fillMaxSize()) {
    // 上半：封面
    Image(artwork, Modifier.fillMaxWidth().aspectRatio(3f / 4f))
    // 下半：镜像 + 模糊
    Box(Modifier.fillMaxSize().offset(y = coverHeight - 22.dp)) {
        Image(
            artwork,
            Modifier.fillMaxWidth().graphicsLayer {
                scaleY = -1f
                renderEffect = BlurEffect(48f, 48f, TileMode.Clamp)
            },
        )
    }
}
```

### 7.3 动效数值：直接抄

| 场景 | FlareTune 建议 |
| --- | --- |
| 播放页进 | **250ms** `decelerate`（`CubicBezierEasing(0f, 0f, 0f, 1f)` 近似） |
| 播放页出 | **300ms** `decelerate` |
| 背景淡入 | 200ms `accelerate_decelerate` |
| 切歌封面交叉淡入 | `ViewSwitcher` 式：两个容器 + 系统 fade（默认 200ms） |
| 歌词非当前行 | `scale 0.98` + `alpha 0.45`，**不要超过 2% 缩放** |
| 逐字高亮羽化 | **30dp**（34sp 字上） |
| 逐字上浮 | **-2dp** |

`decelerate` 的 Compose 近似：`CubicBezierEasing(0f, 0f, 0f, 1f)`；
`accelerate_decelerate`：`CubicBezierEasing(0.4f, 0f, 0.2f, 1f)`。

### 7.4 歌词：34sp 要不要跟

**不建议直接跟 34sp**。原因：

- Apple Music 是"英文为主 + 海报式排版"，34sp 一行能放 12~15 个字母
- 中文 34sp 在 360dp 宽屏上一行只能放 **9~10 个汉字**，一句歌词经常要折 2 行
- 折行后 34sp 的行距会让一屏只剩 3 行，信息密度过低

[06 提案](06-flaretune-client-design.md) 里的 20sp 是保守值。
折中建议：**24sp**（比 20 大、比 34 小），行高 1.4，羽化 16dp（按比例从 30dp 缩）。

如果要保留"巨字"的观感，可以做**双模式**：
默认 24sp，开启"歌词海报模式"后切到 30~34sp（并接受一屏 3 行）。

### 7.5 颜色阶梯：直接抄四档

```
text.lyrics.current  = #F0FFFFFF   (94%)
text.lyrics.normal   = #73FFFFFF   (45%)
text.lyrics.past     = #2EFFFFFF   (18%)
divider / 极弱       = #14FFFFFF   (8%)
```

这套比 [06 提案](06-flaretune-client-design.md) 里的 45% 单档更细，值得替换。

---

## 八、对 04 文档的修正与补充

| 项 | 04 文档原值 | 本次实测 | 说明 |
| --- | --- | --- | --- |
| 歌词正文 | 24sp | **34sp** | `lyrics_line_text_size`；24 更接近 secondary 的 22sp |
| 翻译行 | 17sp | **22sp**（`lyrics_line_secondary_text_size`） | 17sp 是翻译**菜单项**的字号，不是歌词里的翻译行 |
| 进度条 | 4dp | **7dp 胶囊** | 4dp 是投屏 seekbar（`cast_seek_bar_progress_height`） |
| 静态歌词行距 | 40dp | 40dp ✓ | 正确 |
| mini player | 88dp | **48dp**（`miniplayer_height`，`mini_player.xml` 实际引用） | 88dp 是 `compact_player_height`，用在哪个布局**未确认**，不能当作底部 mini player |

> **关于数值目录的坑**：dimens 有多套限定符覆盖（`values-sw600dp` / `values-xxhdpi` 等）。
> 本页所有数值取自**默认 values**（手机竖屏）。
> 04 文档里的 `mini_player_thumbnail_height=40` / `mini_player_button_size=48` /
> `mini_player_max_width=690` / `mini_player_horizontal_margin=90` 明显是宽屏或高 dpi 目录的值，
> 与本页的 30 / 32 / 600 / 16 并不冲突，是**同一资源的不同 config**。
> 引用时务必带上设备条件。

### 补充：BottomSheet 的 peekHeight 是 0

`res/layout/player.xml`：

```xml
<FrameLayout android:id="@id/player_sheet_container"
    android:elevation="@dimen/player_container_elevation"        <!-- 4dp -->
    app:behavior_peekHeight="@dimen/player_peek_height"          <!-- 0dp -->
    app:layout_behavior="com.apple.android.music.player.PlayerBottomSheetBehavior"/>
```

**peekHeight = 0**：播放页收起时完全不可见，不存在"半露的播放条"。
所以 Apple Music 手机端只有两种状态——全屏播放页 / 底部 48dp mini player，
中间的 88dp "compact player" 形态在手机布局里没有出现。

---

## 九、证据清单

| 结论 | 文件 / 位置 |
| --- | --- |
| 播放页层级、两个 motion 容器 | `res/layout/fragment_player_main.xml` |
| motion 容器内部（3:4 + 镜像模糊 + -22dp barrier） | `res/layout/view_motion_container.xml` |
| 背景三层旋转 + 饱和度 2.5 + blur 25 | `LyricsBackgroundLayerView.b()` @ classes2.dex |
| 120s / 90s / 70s 三个周期 | 同上（`setDuration` 常量） |
| 进出 250 / 300ms decelerate | `res/anim/activity_slide_up.xml` / `_down.xml` |
| 通用滑动 200ms | `res/anim/slide_up.xml`（`config_shortAnimTime`） |
| 500ms 无限旋转 | `res/anim/rotation_anim.xml` |
| BottomSheet 30% 切系统栏 | `PlayerLyricsViewFragment.onBottomSheetSlide` |
| 歌词 34sp / 22sp / 26sp | `res/values/dimens.xml` |
| 行缩放 0.98 / 1.0 | `lyrics_line_scale_normal` / `_highlight` |
| 逐字羽化 30dp、上浮 -2dp | `lyrics_karaoke_gradient_feather_width` / `_syllable_lift` |
| 逐字渐变用 saveLayer + LinearGradient | `FullWidthAlphaGradientFlexboxLayout.dispatchDraw` |
| ItemAnimator 不做动画 | `PlayerLyricsViewFragment$u.animateChange` → `return true` |
| 手动滚动后 7 秒恢复跟随 | `PlayerLyricsViewFragment.X1` 中 `cmp-long 7000` |
| 控件区 34.5%、点击区 42.5% | `player_controls_height_percent` / `lyrics_controls_tap_target_height_percent` |
| 白色四档 alpha | `res/values/colors.xml`（`white_alpha_94/45/18/8`） |

### 没能拿到的

1. **逐字高亮的时间轴推进**：`com.apple.android.music.ttml.javanative.LyricsController` 是 JNI 包装，
   时间对齐在 native 层（TTML 解析）。只能确认它是**按 syllable 粒度**驱动的，看不到具体算法。
2. **歌词行滚动的插值曲线**：`RecyclerView` 的 smoothScroll 没有显式 duration，
   走的是 `LinearSmoothScroller` 默认速度（按像素/毫秒算），未反编译到具体系数。
3. **BottomSheet 的 peek / expanded 偏移**：`PlayerBottomSheetBehavior` 里没有常量，
   由父类 `BottomSheetBehavior` 按 `peekHeight` 计算，而 peekHeight 在代码里设，没找到赋值点。
4. **`lyrics_line_scale_highlight` 的确切值**：ARSC 里被 androguard 解析成 `4161536.0px`，
   按 `<<8` 还原是 `1.0f`，但同一组的 `_normal` 是未经此变形的 `0.98`，两者编码方式不一致，
   保守标注为「≈1.0，待真机核实」。
