# FlareTune Client · v1 界面

第一版界面原型。结构取 **Spotify 首页**，全屏播放页取 **Apple Music 秩序**，品牌色沿用 FlareTune 暖橙。

打开方式：直接双击 `index.html`（无依赖、无构建）。

---

## 一、这一版做了什么

| 区域 | 做法 | 来源 |
| --- | --- | --- |
| 首页信息架构 | 顶部 chip 筛选 → 两列快捷卡 → 横向推荐行 ×N → 列表 | Spotify |
| chip 行 | 胶囊、横向滚动、右端 12dp 渐隐 | Spotify（APK 直读） |
| 卡片 | 1:1 封面 + 类型标签 + 标题两行 + 艺人一行 | Spotify（`glue_card.xml`） |
| 区块标题 | 左右 16、下 16，字号 24/700 | Spotify（`glue_sectionheader_large.xml`） |
| 底部 dock | mini player 悬浮在 tab bar 之上 | Spotify |
| mini player | 高 56、圆角 8、内边距 8、封面 40、按钮 40、进度 2dp | Spotify（`now_playing_bar.xml`） |
| 展开动效 | 100ms | Spotify（`now_playing_bar_scene.xml`） |
| 全屏播放页 | 大封面居中 → 标题/艺人 → 歌词摘录 → 进度 → 控件 | Apple Music |
| 全屏背景 | 封面主色径向渐变 + 压暗遮罩 | Apple Music |
| 歌词页 | 整屏滚动、当前行高亮、远处行渐隐、点击跳转 | Apple Music + Spotify |
| 响应式 | 窄屏 2 列 / 宽屏 5 列，dock 竖排 → 横排 | Spotify（宽屏截图实测） |

## 二、数值对照表（可直接抄进代码）

| 常量 | 值 | 出处 |
| --- | --- | --- |
| `floating_now_playing_bar_height` | 56dp | `res/values` dimens |
| `floating_now_playing_bar_corner_radius` | 8dp | `floating_layout_bg.xml` |
| `floating_now_playing_bar_content_padding` | 8dp | 同上 |
| `floating_now_playing_bar_large_content_padding` | 16dp | 展开态进度条边距 |
| `now_playing_bar_actions_button_size` | 40dp | `now_playing_bar.xml` |
| `now_playing_bar_carousel_margin` | 12dp | 标题区左边距 |
| 展开动画时长 | 100ms | `now_playing_bar_scene.xml` |
| `tab_bar_size` | 48dp | `layout_tab.xml` |
| `encore_tab_heading_horizontal_padding` | 16dp | 同上 |
| `row_horizontal_padding` | 16dp | 全量布局统计（出现 48 次） |
| `carousel_row` 上/下内边距 | 16 / 40dp | `carousel_row.xml`（`spacer_16` / `spacer_40`） |
| 区块标题上/下外边距 | 40 / 16dp | `glue_sectionheader_large.xml` |
| 卡片标题行高 | 16dp，最多 3 行 | `glue_card.xml` |
| 列表行封面 | 48dp | `track_row_cover_art_size` |
| chip 行渐隐宽度 | 12dp | `funkis_filter_chips_header.xml` |
| 页面底色 | `#121212` | 截图像素测量（占采样 22.7%） |
| chip 未选中 | `#333333` | 截图测量 |
| 底栏 | `#1f1f1f` | 截图测量 |
| mini 染色 | 跟随封面主色 | 截图（深蓝 `#223466`） |

> 布局 token 使用频次统计见 `../../research/evidence/layouts/`，原始 XML 也在那里。
> 全量反编译产物（1391 个布局）未入库，脚本见 `../../research/scripts/decode_layouts.py`。

## 三、哪些是近似，不是原样

| 项 | 说明 |
| --- | --- |
| chip 高度 | 代码里是 Compose 实现（`ComposeView`），XML 拿不到。取 40dp，与 `now_playing_bar` 的按钮同高 |
| chip 选中色 | 截图实测 `#65D46E`（疑 JPEG 压缩偏淡），本版用品牌橙 `#FF7A45` |
| 卡片圆角 | 代码里由 `EncoreThemeInstaller` 运行时注入，未取到。取 4dp（截图目测） |
| 字体 | Spotify 用 Mix UI，本版用系统字体栈（PingFang / 微软雅黑） |
| 封面 | CSS 渐变占位，不含任何第三方素材 |
| 歌词 | 仅用于演示排版的摘录文本，非完整歌词 |

## 四、还没定的

1. 强调色：现在是暖橙。若第一版要更贴 Spotify，把 `--accent` 换成 `#1ed760` 即可。
2. mini player 高度：56（Spotify）还是 88（Apple）？现在 56。
3. 歌词字号：22（现在）/ 24（Apple 原值，汉字单行容纳字数偏少）。
4. 全屏页的歌词摘录区：现在占中段。若嫌空，可以换成"专辑卡片"或"相关推荐"。

## 五、交互清单（都能点）

- 点卡片 → 播放并载入 mini player
- 点 mini player → 全屏页从底部升起（100ms）
- 全屏页：拖进度条 / 播放暂停 / 上下首 / 随机循环 / 喜欢
- 点歌词摘录 → 进歌词页；点歌词任意行 → 跳到该时间
- 键盘：`空格` 播放暂停、`←→` 切歌、`Esc` 收起
- 拖窗口宽度：2 列 ↔ 5 列、dock 竖排 ↔ 横排

## 六、动效（全部来自 APK 实测）

| 动作 | 时长 | 曲线 | 实现 |
| --- | --- | --- | --- |
| mini player 出现 | **100ms** | overshoot | `scaleY` 0→1，原点在**底边**；`alpha` 0.5→1 |
| mini player 收起 | **100ms** | anticipate | `scaleY` 1→0；`alpha` 1→0 |
| 全屏页进入 | **150ms** | overshoot | 整屏 `translateY` 100%→0 |
| 全屏页退出 | **200ms** | overshoot | 整屏 `translateY` 0→100% |

**底栏本身不做位移。** mini player 是"长在底栏上方"的：它高度从 0 变 56dp，
页面底部留白同步增加，底部渐变遮罩也跟着变高（靠 Barrier 联动）。
所以看起来像底栏被顶上去了，实际上是上方的东西在长。

推导过程与原始 XML 见 [`../../research/docs/08-dock-motion.md`](../../research/docs/08-dock-motion.md)。

**怎么看这段动画**：长按 mini player 600ms 会停止播放并收起，能完整看到 anticipate 的"先蓄力再抽走"。
