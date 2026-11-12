# 01 · 方法与局限

## 1. 目标

为 FlareTune Client（Android 优先的纯消费型音乐播放器）收集**可执行的**前端美学参考：
不是"看起来高级"的形容词，而是能落到 dp / sp / hex / 曲线上的具体数值，以及这些数值背后的取舍。

## 2. 样本选择理由

| App | 为什么选它 | 它能回答的问题 |
| --- | --- | --- |
| 音流 / Musiver | 同类产品：自建音乐库（Subsonic/Navidrome/Jellyfin/Emby）的跨平台客户端，用户画像与 FlareTune 最接近 | 小团队做自建音乐客户端，视觉上能做到什么程度？哪些地方会露怯？ |
| Spotify | 移动端音乐播放器的交互事实标准，且罕见地把设计系统以 `encore_*` 命名空间写进 APK | 一套成熟的音乐 App token 阶梯长什么样？ |
| Apple Music | 另一套事实标准，与 Spotify 的取向几乎相反（浅色/排版/留白 vs 深色/密度/品牌色） | 播放器与歌词页的排版秩序怎么建立？ |

三者覆盖了三种取向：**氛围型（音流）/ 密度型（Spotify）/ 秩序型（Apple Music）**。

## 3. 工具链

| 环节 | 工具 | 说明 |
| --- | --- | --- |
| APK 元信息 / 资源表 | `androguard` 4.1.4（隔离 venv，Python 3.13） | 解析 `AndroidManifest.xml`、`resources.arsc` 的 color / dimen / style / string |
| 包结构 | `zipfile` | 条目清单、`lib/`、`assets/flutter_assets`、字体文件 |
| Flutter 线索 | `FontManifest.json` + `libapp.so` 字符串表 | 图标字体家族、内置图片、设置项命名、主题相关字符串 |
| 截图 | Google Play 页面 + App Store 页面 | 音流为 App Store 截图（同设计语言），另两家为 Play 商店截图 |
| 色板测量 | Pillow `quantize`（MEDIANCUT，8 色）+ 中间 70% 裁剪 | 规避营销边框，输出占比 |
| 复现 | `scripts/*.py` | 见下 |

复现命令（在仓库根目录）：

```bash
python scripts/analyze_apk.py <apk> out/<tag>     # 资源与清单
python scripts/fetch_shots.py <package> out/shots # 商店截图
python scripts/palette.py "out/shots/*.png" out/palette.json
python scripts/make_tokens.py                     # 生成 research/tokens/*.json
```

> 脚本依赖：`androguard`、`pillow`。建议用独立 venv，不要装进系统环境。

## 4. APK 重新获取方式

| App | 获取方式 |
| --- | --- |
| 音流 1.3.9（Flutter 版） | `https://github.com/gitbobobo/StreamMusic/releases/download/v1.3.9/musiver_1.3.9_arm64-v8a.apk` |
| 音流 2.0.1-beta.15（Compose 重写版） | 无公开直链（beta 渠道）。从已登录真机 `adb shell pm path cn.musiver.android` → `adb pull` 取 base.apk |
| Spotify | `https://d.apkpure.com/b/APK/com.spotify.music?version=latest`（返回 split 分发包，需解包取 `com.spotify.music.apk`） |
| Apple Music | `https://d.apkpure.com/b/APK/com.apple.android.music?version=latest`（同上，取 `com.apple.android.music.apk`） |

注意：

- `apkpure.com` / `apkmirror.com` 的网页对普通 HTTP 客户端返回 403（Cloudflare），
  但 `d.apkpure.com` 的直链端点可用；APKMirror 用无头 Chrome 也过不了 `Just a moment` 挑战。
- 两家的 `?version=latest` 拿到的是**分发包**（zip 内多个 `*.apk`），
  设计 token 分析只需要 base APK；密度相关的位图在 `config.*dpi.apk` 里，本次未纳入。

校验值：

```
c866007aa0f90efdacc5ad4592fc33ec863ca45a2da5dabefa06b2c2b2a6fe83  musiver_1.3.9_arm64-v8a.apk
1b58e6a1aa278a5c1e141c1408b9d26621cb6943e02d7b56838fce5bc9b33f37  spotify_latest.apk
e8cc2650cac9788122bf91edc339d6d96bbbf3eac2c90e4c2a97ec955c6581b1  applemusic_latest.apk
```

## 5. 证据分级

本研究只用三级标注，不做模糊表述：

| 标注 | 含义 | 例子 |
| --- | --- | --- |
| **APK** | 直接从安装包读出的事实 | `encore_spacer_base = 16dip`；`compact_player_height = 88dip` |
| **截图** | 官方商店截图目视 + 像素量化 | "Spotify 深色底实测 `#121212`，占采样 22.7%" |
| **推断** | 基于上述证据的判断，可能有误 | "音流用 Material You 动态取色，因此主色随壁纸变化" |

## 6. 已知局限（重要）

1. **颜色几乎不可从 ARSC 取得。** Spotify 与 Apple Music 的 `color` 资源条目全部是**动态引用**
   （解析出的值形如 `#000000ce`，实际是指向运行时颜色表的索引）。两家都把色板写在代码里
   （Compose / 自研主题）。因此**色值结论主要来自截图像素测量与目视**，不是安装包字面值。
2. **截图不是原始渲染。** 商店截图带营销边框、状态栏、模拟器圆角，且经过有损压缩；
   色板测量已做中间 70% 裁剪，但仍会有边框色渗入。绝对值仅供参考，**相对关系**更可信。
3. **未在真机/模拟器运行。** 没有安装运行 APK，因此**动效曲线、手势、触感反馈、滚动惯性**
   全部没有实测，只能从静态资源（`res/anim`、`animator`）与截图推断。这是本次最大的空白。
4. **音流的视觉 token 编译进 `libapp.so`。** Flutter AOT 里颜色是整数常量、不是字符串，
   静态提取噪声极大，因此音流一节的设计数值**以截图测量为主**，不给"精确 token"，
   只给"可复现的比例关系"。
5. **版本会漂移。** 结论绑定在上述版本；Spotify / Apple Music 迭代很快，重做研究前先核对版本号。
6. **未做法律评估。** 截图为研究引用，字体（Spotify Mix、SF Pro）为各自厂商授权资产，
   **不可直接用于 FlareTune Client 的分发**，只能参考其比例与策略。

## 7. 明确未覆盖

- 平板 / 折叠屏适配细节（仅音流 iPad 截图有观察）
- 无障碍（TalkBack 顺序、动态字号上限）
- 深浅色切换的过渡与状态保持
- 离线/下载态、错误态的视觉
- 播放器通知栏与锁屏（只从 ARSC 看到通知相关 dimens 存在）
