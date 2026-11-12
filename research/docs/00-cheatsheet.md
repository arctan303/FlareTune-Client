# 开发速查（Cheatsheet）

一页装下实现时真正会查的数。全部来自 APK 静态提取或截图像素测量，
来源与证据等级见 [01-method.md](01-method.md)；推导过程见 [05-comparison.md](05-comparison.md)。

---

## 1. 六条可以直接抄的共性

1. 封面是唯一色源 —— 播放页背景由封面模糊 + 压暗遮罩生成，不引入第二种强调色。
2. 播放页 = 全屏背景（封面模糊）+ 前景内容，背景必须有遮罩，正文对比度设下限。
3. 进度条 2–3dp，拖动时 thumb 才出现（12dp）。
4. 筛选/分类用胶囊 chip：高 48、圆角 24、横内边距 34。
5. 卡片圆角 12–16dp。
6. mini player 悬浮在 tab bar 之上，不占 tab bar 的位置。

## 2. 三端关键数值对照

| 维度 | Spotify（Encore） | Apple Music | 音流（Musiver） |
| --- | --- | --- | --- |
| 间距阶梯 | 2/4/6/8/12/16/20/24/32/40/48/64 | 未 token 化，实测块间距 32–40dp | 未 token 化 |
| 字号 | 9/11/13/16/20/24/32/40/48 | 大标题 34sp，歌词 24sp，翻译行 17sp，tab 文字 11sp | 跟随系统 |
| 圆角 | 2/4/6/8/16/999 | 大卡 14dp | 卡片圆角 + 玻璃拟态 |
| 控件尺寸 | 32/48/56 | mini player 88dp，tab 图标 30dp | 分段 tab |
| 进度条 | 2dp | 4dp | 2dp |
| 播放键 | 56 实心 | 实心黑 | 56 描边圆 |
| 歌词 | 整屏铺满，非当前行 50% 白 | 正文 24sp / 行高 26sp / 块间距 32–40dp，静态歌词行距 40dp | 有歌词页 |
| 深色底 | `#121212`（实测 22.7%） | 纯白 / 纯黑（营销黑边占 15.9%） | `#1A1C1E`（M3 dark surface） |
| 字体 | Mix UI + Mix Mono + spoticon | SF Pro（含 Rounded）+ Roboto Medium Numbers | **无正文字体**（27 个字体全是图标字体） |
| 图标 | 1542 个矢量 XML / 31 位图 | 1701 个矢量 XML / 96 位图 | 混用 5 套图标库 |

## 3. FlareTune Client 提案数值（实现直接取值）

```
色彩    底色 zinc 家族；强调色 #FF7A45（暖橙）；文字三级（primary / secondary / tertiary）
字体    Inter；数字用等宽（tabular-nums），避免进度跳字
字号    11 / 13 / 15 / 17 / 20 / 24 / 30   ← 比 Spotify 收窄（汉字方块占位）
封面    326dp = 屏宽 − 64；圆角 12
标题    20 / 600
进度    轨道 2dp + thumb 12dp
播放键  56dp 实心强调色
mini    72dp 高
歌词    20sp / 行高 1.4 / 块间距 28 / 非当前行 45% 不透明度
黑胶    326dp，24s 一圈，暂停即停
```

## 4. 反面教材（音流踩过的坑，别重复）

- **不自带正文字体**：中文全靠系统字体，跨机型观感漂移。必须自带一套中文可用的正文族。
- **图标混用五套库**：Material / Cupertino / Fluent / FontAwesome / MDI 笔画粗细不一致。统一一套。
- **照片背景无遮罩约束**：正文对比度不稳。背景层强制加压暗遮罩 + 最小对比度。
- **颜色不进 token**：一旦换主题就散架。先把 token 立起来再写界面。

## 5. 待定（需要拍板）

- 播放键用实心强调色还是描边？（当前提案：实心 `#FF7A45`）
- mini player 72 还是 88？（提案取 72，比 Apple 紧、比 Spotify 松）
- 歌词字号 20 还是 24？（汉字在 24sp 下单行容纳字数偏少）
- 黑胶模式默认开还是关？（提案：默认关，作为可开启的氛围层）

## 6. Spotify 布局代码直读（反编译 `res/layout`）

Spotify 首页骨架仍在 XML，chip / 卡片内部已迁到 Jetpack Compose（`ComposeView`），
所以"能从代码抄到的"与"只能靠截图量的"边界很清楚：

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

**布局里 token 的真实使用频次**（1391 个 layout 全量统计）：

```
spacer_16  510 次    spacer_8   320    spacer_24  160    spacer_12  115
std_16dp   339       std_8dp    127    std_24dp    94    std_32dp    53
48dp 触控目标：header_action_row_button_size / entity_action_row_action_size / encore_back_button_size
```

结论：**16 / 8 / 24 / 12 是四个主力间距**，48dp 是唯一的触控尺寸，别自己造第五个值。
