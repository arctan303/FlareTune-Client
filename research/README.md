# FlareTune Client — 竞品前端美学研究

面向 **FlareTune Client（纯消费型音乐播放器，首个目标平台 Android/APK）** 的设计参考研究。

研究对象为三个已发布 APK：**音流（Musiver / StreamMusic）**、**Spotify**、**Apple Music**。
所有结论都标注了来源：`APK` = 从安装包静态提取，`截图` = 官方商店/官网截图目视与像素测量，
`推断` = 基于上述证据的判断。未验证的内容明确标为未知。

> 本仓库外的 `C:\git\FlareTune` 全程只读，未做任何修改。

## 结论摘要（先看这个）

1. **三家的共同骨架**：把封面当唯一色源、播放页用封面模糊图铺满 + 压暗遮罩、进度条 2–3dp、
   胶囊型筛选 chip、12–16dp 卡片圆角、底部 mini player 悬浮在 tab bar 之上。这六条可以直接抄。
2. **Spotify 是唯一把设计系统完整 token 化并落进 APK 的**：`encore_*` 命名空间里有
   间距（2/4/6/8/12/16/20/24/32/40/48/64）、字号（9/11/13/16/20/24/32/40/48）、
   圆角（2/4/6/8/16/999）、控件尺寸（32/48/56）四套阶梯。**建议直接照这套比例建 FlareTune 的 token**。
3. **Apple Music 的差异化在排版与歌词**：把 SF Pro 整套（含 Rounded 变体）打进 Android 包，
   页面大标题 34sp、歌词正文 24sp/行高 26sp/行距 32dp、静态歌词行距 40dp、
   翻译行 17sp、mini player 高 88dp、tab 图标 30dp + 文字 11sp。歌词页是它最强的一屏。
4. **音流 1.3.9（Flutter 版）的差异化在氛围**：默认深色 + 一张水下照片做全局背景（可换自定义图）、玻璃拟态卡片、
   播放页是黑胶唱片隐喻（`album_disc.png` 784×784 直接叠在圆形封面外）、
   顶部分段 tab（推荐/歌曲/歌词）、Material You 动态取色。
5. **音流 1.3.9 的短板同样明显**：没有自带正文字体（中文全靠系统字体），图标同时来自
   Material / Cupertino / Fluent / FontAwesome / MDI 五套图标字体，笔画粗细不一致；
   照片背景 + 实时模糊在没有遮罩约束时正文对比度不稳。**这三点是要避开的坑**。
6. **两家大厂的颜色都不在 `resources.arsc` 里**：Spotify 与 Apple Music 的颜色资源条目全是
   动态引用（值形如 `#000000ce`），真实色值在代码里。因此本研究的色板以**截图像素测量**为准，
   命名 token 只保留能确认的部分（如 Spotify 的 `encore_face_background_*`）。
7. **实测基准色**：Spotify 深色底 `#121212`（占采样像素 22.7%）；Apple Music 以纯白/纯黑为主
   （`#FFFFFF` 8.6% / `#000000` 15.9%，含营销黑边）；音流深色面 `#1A1C1E`（Material 3 dark surface 系）。
8. **图标体系**：Spotify 1542 个、Apple Music 1701 个 drawable XML，位图仅 31 / 96 个 ——
   图标近乎 100% 矢量。音乐客户端没有理由用位图图标。
9. **字体策略分歧**：Spotify 自带 Mix UI（正文）+ Mix Mono（数字/时长）+ spoticon（图标）；
   Apple 自带 SF Pro + Roboto Medium Numbers（数字）；音流零正文字体。
   **数字用等宽/表格数字**是两家的共识，播放进度与时长会跳字，必须处理。
10. **对 FlareTune Client 的取向建议**：以 Spotify 的 token 骨架 + Apple 的播放器/歌词层级为主，
    借用音流的"封面模糊背景 + 黑胶"作为可开关的氛围层，并强制加遮罩与对比度下限。

> **版本分水岭**：音流 2.0 是一次彻底重写 —— 1.3.9 是 Flutter/Dart AOT（`libapp.so`），
> 2.0 换成 **Rust 核心（`libmusiver_ffi.so`，UniFFI 绑定）+ Kotlin/Jetpack Compose 原生客户端**
> （桌面/Web 为 Tauri + React），包名也从 `cn.aqzscn.stream_music` 改为 `cn.musiver.android`。
> 上面第 4/5/7/9 条描述的是 **1.3.9** 的界面与资产；**2.0 的界面结构、动效与实现方式与此不同**，
> 全屏播放页的实测数据见 [gemini-brief-musiver.txt](gemini-brief-musiver.txt)（针对 2.0.1-beta.15）。

## 目录

| 文件 | 内容 |
| --- | --- |
| **[SHARE.md](SHARE.md)** | **自包含简报：整份研究浓缩成一页，可直接给外部评审/AI** |
| **[SHARE-am-player.md](SHARE-am-player.md)** | **自包含简报：Apple Music 播放页专题（背景/动效/歌词），可直接给外部评审/AI** |
| **[gemini-brief-musiver.txt](gemini-brief-musiver.txt)** | **音流 2.0 全屏播放页「顶部拖动关闭条」逆向手册：手势链路 / 双条件阈值 / spring 落定（纯事实，可直接转交外部 AI）** |
| **[gemini-brief.txt](gemini-brief.txt)** | **给外部 AI 的「设计任务书」：喂全套逆向数据 + 要求产出播放页设计规格** |
| [gemini-prompt.txt](gemini-prompt.txt) | 给外部 AI 的「评审版」提问稿（问该不该跟，非设计任务） |
| **[docs/00-cheatsheet.md](docs/00-cheatsheet.md)** | **一页速查：实现时要查的数与坑（开发常开）** |
| [docs/01-method.md](docs/01-method.md) | 样本、版本、工具链、证据分级与局限 |
| [docs/02-streammusic.md](docs/02-streammusic.md) | 音流 1.3.9（Flutter 版）逐屏拆解 —— 2.0 的 Compose 重写版另见 gemini-brief-musiver.txt |
| [docs/03-spotify.md](docs/03-spotify.md) | Spotify 9.2.0 逐屏拆解 + Encore token 全表 |
| [docs/04-applemusic.md](docs/04-applemusic.md) | Apple Music 7.0.0 逐屏拆解 + 排版/歌词规格 |
| [docs/05-comparison.md](docs/05-comparison.md) | 横向对比矩阵与"抄什么/不抄什么" |
| [docs/06-flaretune-client-design.md](docs/06-flaretune-client-design.md) | **可直接落地的设计系统提案（含数值）** |
| [docs/08-dock-motion.md](docs/08-dock-motion.md) | **mini player 出现/消失与底栏联动动效（反编译实测）** |
| [docs/09-applemusic-player.md](docs/09-applemusic-player.md) | **Apple Music 全屏播放页：流动背景 / 进出场 / 逐字歌词（反编译实测）** |
| **[../design/v1/index.html](../design/v1/index.html)** | **第一版界面：Spotify 首页 + Apple 全屏（可交互）** |
| **[am-player.html](am-player.html)** | **Apple Music 播放页还原（流动背景 + 逐字歌词，可交互）** |
| **[preview.html](preview.html)** | 播放器与界面预览（三家对照 + 提案实现） |
| [report.html](report.html) | 可视化报告（截图 + 色板 + token 一览） |
| tokens/ | 从 APK 提取的原始 token JSON + 提案 token |
| evidence/ | 截图（720px 宽）、音流内置资产、调色板测量、精简版 APK 报告 |
| evidence/layouts/ | Spotify 关键布局反编译产物（mini player / chip 行 / 卡片 / 标题） |
| scripts/ | 复现脚本（APK 解析 / 截图抓取 / 调色板测量 / token 生成） |

## 样本与校验

| App | 包名 | 版本 | 来源 | 体积 | SHA-256（前 16 位） |
| --- | --- | --- | --- | --- | --- |
| 音流 / Musiver | `cn.aqzscn.stream_music` | 1.3.9 (135) | GitHub Release `gitbobobo/StreamMusic` v1.3.9 arm64-v8a（Flutter 版） | 36.1 MB | `c866007aa0f90efd` |
| 音流 / Musiver | `cn.musiver.android` | 2.0.1-beta.15 (15) | 真机 `adb pull`（Kotlin + Jetpack Compose 重写版） | 48.1 MB | — |
| Spotify | `com.spotify.music` | 9.2.0.2053 (148387875) | APKPure 分发包（base + config splits） | 153.5 MB | `1b58e6a1aa278a5c` |
| Apple Music | `com.apple.android.music` | 7.0.0 (1614) | APKPure 分发包（base + arm64 + mdpi） | 113.7 MB | `e8cc2650cac97881` |

APK 原始文件不入库（体积与授权原因），保留在本地 `.research/raw/`（已 gitignore）。
重新获取方式与校验值见 [docs/01-method.md](docs/01-method.md)。

## 一句话结论

三家里 **Spotify 最值得抄结构**（token 完整、密度与层级经得起推敲），
**Apple Music 最值得抄播放器与歌词的排版秩序**，
**音流最值得抄"氛围感"这个差异化方向，但要补齐它的工程短板**。
