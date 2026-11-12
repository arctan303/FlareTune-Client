# Android 数据与 UI 接线合同

本文件给 Gemini 与后续 UI 协作者使用，以当前 Kotlin 类型和真实动作实现为准。2026-11-12 的分工为：**Gemini 负责 UI/资源；本协作负责 API、数据、状态、存储、播放与系统服务**。用户明确要求两部分并行继续开发；不再执行此前“整个 Android 停写”的旧限制。视觉规则仍归 [Design-Brief](Design-Brief.md)。

当前本地验证：`0.1.0-dev.3` / versionCode 3，143/143 JVM/Compose/Robolectric 单测、assembleDebug、Lint通过（0错误/40警告）。143项包含本轮fresh R2两项修复；旧43/85/131/139项是各自输入的历史证据，不证明全设备真实功能。设备与服务端歌词问题另见 [任务](tasks/android-data-20261112.md)。

## 1. 单一入口与分工

- UI继续调用 `ClientApp(state: ClientUiState, actions: ClientActions, imageLoader: ImageLoader?)`。真实入口由 MainActivity 注入 ClientViewModel，不在 Composable 内创建第二个API、MediaController或本机存储。
- 类型定义：[`ClientContracts.kt`](../android/app/src/main/java/com/flaretune/client/ui/ClientContracts.kt)、[`Models.kt`](../android/app/src/main/java/com/flaretune/client/model/Models.kt)、[`LibraryModels.kt`](../android/app/src/main/java/com/flaretune/client/model/LibraryModels.kt)、[`LyricsModels.kt`](../android/app/src/main/java/com/flaretune/client/model/LyricsModels.kt)。
- 歌曲行交给 `actions.playSong(song)`；历史入口优先 `actions.playHistorySong(song)`，完整收藏快捷入口交给 `actions.playFavorites()`。不要从UI将首屏数据组装成“完整收藏/专辑”。
- 所有封面复用传入的认证 ImageLoader。不要自行新建图片客户端、把Cookie拼进URL、重新请求未认证封面或缓存临时签名链接。
- 新动作有兼容默认实现，只保证旧UI实现者继续编译；**默认空函数不是界面已接线证据**。当前真实 ClientViewModel 已覆盖本页列出的新动作，Gemini须把对应按钮绑定到 actions。
- UI只消费安全 ClientNotice；不展示异常正文、服务端任意message、Cookie、CSRF、resultRef或媒体签名链接。

## 2. 状态字段与动作

原有连接/改密、主页/搜索/音乐库、歌曲/专辑分页及播控字段继续有效。以下是新增或本轮明确的接口：

| 功能 | ClientUiState 字段 | 动作与规则 |
| --- | --- | --- |
| 歌词 | `lyricState: LyricState`、便捷 `activeLyricIndex: Int` | `retryLyrics()`；不要由UI重复启动切歌请求/轮询 |
| 译文显示 | `translationEnabled: Boolean = false`、`translationNotice: ClientNotice?` | `toggleTranslation()` / `setTranslationEnabled(Boolean)` 只改变本机显示 |
| 请求生成译文 | `lyricState.translationSupported` / `translationState` / `polling` | `requestLyricTranslation()` 是独立生成动作，不能与显示开关混用 |
| 个人歌单列表 | `playlists`、`playlistsLoading`、`playlistsNotice` | `refreshPlaylists()`、`openPlaylist(PlaylistSummary)` |
| 个人歌单详情 | `selectedPlaylist`、`playlistSongs`、`playlistLoading`、`playlistNotice` | `closePlaylist()`、`refreshPlaylist()`；歌曲行 `playSong` |
| 首页收藏 | `favoriteSongs`、`latestFavoriteCoverUrl`、`favoritesLoading`、`favoritesNotice` | `refreshFavorites()` 只刷新；`playFavorites()` 重新取完整详情再播放 |
| 继续收听/历史 | `playHistory: List<Song>`、`historyNotice` | `playHistorySong(Song)` 在当前身份下重新解析音频后播放 |
| 歌曲分类 | `songLanguage: String = "all"` | `selectSongLanguage(String)`，只影响歌曲/搜索/收藏分类与分页 |
| 专辑分类 | `albumLanguage: String = "all"` | `selectAlbumLanguage(String)`，与歌曲分类独立 |
| 队列恢复 | `queueRestoring: Boolean`、`queueNotice: ClientNotice?` | 空播放器且恢复失败时 `retryQueueRestore()`；已有用户队列优先 |
| 队列编辑 | `playback.queue` / `currentIndex` | `removeQueueItem(index)`、`moveQueueItem(fromIndex,toIndex)`，索引针对当前完整队列 |
| 原有播控 | `playback` | play/pause/next/previous/seek/shuffle/repeat/select/volume/retry作用于同一Media3 Player |

`playback`包含 `current: Song?`、`queue: List<Song>`、`currentIndex: Int`、`playing`、`buffering`、`positionMs: Long`、`durationMs: Long`、`shuffle`、`repeatMode: Int`、`failed`、`volume: Float`。空队列索引为-1；repeatMode 0关闭/1单曲/2列表；volume是播放器gain，范围0..1，不代表系统或蓝牙设备音量。UI进度来自该快照，seek只在拖动结束提交，不维护另一套播放时钟。

上一首已改为一次直接到上一曲；没有上一曲才回到当前曲目0位置。不要继续在UI增加“三秒先回开头、再点一次才上一首”的规则。

## 3. 歌词与译文

### 展示类型

`LyricState`：
- `songId: String?`；`status: LyricStatus`；`lines: List<LyricLine>`。
- `activeLyricIndex: Int = -1`；`translationState: LyricTranslationStatus`；`translationSupported: Boolean`。
- `syncMode: String`（word/line/none）；`polling: Boolean`；`notice: ClientNotice?`。

`LyricLine`有 `text: String`、`translation: String?`、`timeSeconds: Double?`、`endTimeSeconds: Double?`、`words: List<LyricWord>`。`LyricWord`包含text/startTimeSeconds/endTimeSeconds，时间均为**秒**；播放器positionMs是毫秒。UI使用已经解析的时间与激活索引，不猜测无时间歌词的时间，不把毫秒当秒。

| LyricStatus | UI含义 |
| --- | --- |
| IDLE | 没有当前有效歌曲/会话，清掉旧歌词 |
| LOADING | 当前曲目请求中 |
| INSTRUMENTAL | 纯音乐占位，使用提供的line/active index |
| READY | 可显示原词、已有译文、行或逐词时间 |
| EMPTY | 明确的无词结果；不能把任意网络/结构错误当无词 |
| FAILED | 安全错误与显式重试，保留区分故障的状态 |

`LyricTranslationStatus`为 IDLE/PENDING/READY/FAILED/UNAVAILABLE/NOT_NEEDED。状态不同于显示开关；READY不强制显示，translationEnabled=false时仍隐藏已有译文。某行translation为空时不伪造或复制原文为译文。

### 请求边界

- `Song.language == "instrumental"` 严格匹配时，协调器零网络直接给 `timeSeconds=0`、`纯音乐，请欣赏` 与 INSTRUMENTAL；null、空、大小写变体或其他值仍走统一GET。
- 其他歌曲调用宿主 `GET /api/lyrics?songId=...`；客户端不绕过来源链直读工作台或本地文件。服务端返回的纯音乐联合类型同样支持。
- 切歌、账号/实例、会话代次或歌曲language变化均撤销旧请求；晚返回不能污染新曲目/账号。401/精确account_changed与歌词resultRef冲突分别处理，不通过message子串注销账号。
- pending由协调器最多追加20次GET轮询，默认3秒间隔；UI只显示polling和状态，不自行无限轮询。409 LYRIC_RESULT_RETIRED/CHANGED最多做一次新来源选择恢复；失败可显式retryLyrics。
- 只有 `requestLyricTranslation()` 触发当前有效resultRef的 `POST /api/lyrics/translation`。resultRef由协调器内部保管，不出现在ClientUiState，不由UI持久化或拼装。
- `toggleTranslation/setTranslationEnabled` 默认关闭，通过设备私有 `playback_display_v1` 偏好保存，仅控制显示；不会POST生成、切换来源、修改服务端AI设置。保存失败保留原开关值并设置translationNotice。
- 无译文/上游不可用/结构错误不能通过放宽parser、强行改sourceId或修改实例来源顺序掩盖；宿主资产/工作台问题独立排障。

示例接线（布局由Gemini决定）：

```kotlin
val lyric = state.lyricState
val line = lyric.lines.getOrNull(state.activeLyricIndex)
val shownTranslation = line?.translation.takeIf { state.translationEnabled }
// 译文显示控件 -> actions.toggleTranslation()
// 单独生成控件 -> actions.requestLyricTranslation()
// 歌词重试 -> actions.retryLyrics()
```

## 4. 歌单与收藏

`PlaylistSummary`：id/name/kind/description/songCount/revision/previewCovers/customCoverUrl/createdAt/updatedAt。`kind` 为服务端真实种类；收藏须按kind=favorite找**真实id**，不要把兼容路径字符串favorite当现代写入ID。

`PlaylistDetail`含playlist与entries；每项 `PlaylistSong(song,sortOrder,addedAt)`，`songs`按详情条目顺序提供。UI接线：
- 列表打开 `openPlaylist(summary)`，详情使用selectedPlaylist与playlistSongs；close取消当前详情请求，旧响应不重新打开。
- refreshPlaylists刷新本人列表，已有列表不因刷新失败被假装清空；详情独立loading/notice。
- openPlaylist与openAlbum互斥，返回分别closePlaylist/closeAlbum，不把歌单详情放进专辑假对象。
- favoriteSongs是完整收藏详情的歌曲。latestFavoriteCoverUrl取最大的addedAt（并以sortOrder作并列顺序），不是当前队列第一首或“列表第一张”。
- playFavorites点击后重新请求完整现代收藏详情，构造完整队列；不能用首页卡片的截断预览代替。
- 被动refreshFavorites不会取消用户明确的收藏播放意图。退出/切换取消旧列表/详情/播放请求并清私人状态。
- 本轮只有读取与播放；没有新增收藏写入、歌单创建/改名/删除或排序API动作，不为UI展示未接线的管理成功结果。

## 5. 继续收听与设备历史

**继续收听/历史读取state.playHistory；完整当前队列读取state.playback.queue。两者不得互相兜底。**

历史由实际Media3 Service的current+isPlaying事件记录；整队列、暂停/恢复、拖动或仅恢复暂停队列不记录为已经听过。最近播放倒序、同songId去重、最多200条；再次真正切回该曲会更新位置。VM销毁后服务播放仍可记录，单纯页面存活不作为记录条件。

`HistoryEntry`仅有id/title/artist/album/durationSeconds/language/playedAt；按规范化origin+accountId的SHA-256分键存储，v1 JSON≤512KiB。绝不保存audioUrl/coverUrl/Cookie/CSRF/resultRef或临时签名链接。退出/换实例/换账号清旧存储，并通过generation和串行IO拒绝旧读写复活。

UI可能先得到audioUrl为空的安全占位Song；不能直接把它交给自行创建的播放器。playHistorySong始终用当前会话解析单曲；后台hydrate解析只改善当前界面元数据，不代替点击时的新验证。≤80字符ID批量解析每批≤200且序列化UTF-8≤60KiB；81..256字符ID最多4并发单曲读取。缺失项跳过，错误通过historyNotice显示。

历史只存展示元数据，因此重启后封面可能先占位；UI必须继续使用传入loader获取重新解析出的封面。用户报告“历史像队列”不能直接证明整队列被持久记录；数据回归已覆盖30首队列只有一首真正播放时历史只有一项，前端仍须使用正确字段。

## 6. 完整队列、编辑与暂停恢复

当前队列由Media3 Timeline持有，保留顺序与重复歌曲，UI不自行distinctBy(songId)。删除/移动使用索引；移动当前项由原生Timeline保留播放项与位置，越界/同位置请求忽略。

持久对象：
```kotlin
QueueEntry(id, title, artist = "", album = "", durationSeconds = 0.0, language = null)
SavedPlaybackQueue(entries, currentIndex, positionMs = 0, shuffle = false, repeatMode = 0)
```

- 最多500项、JSON v1≤512KiB；超限报STORAGE，不静默截断当前索引。只存上述元数据和索引/位置/随机/循环，不存URL、凭据、播放意图或音频文件。
- Service见过真实非空queue后，在Player事件、每2秒及正常onDestroy时保存；启动初始空Player不覆盖磁盘队列。force-stop不保证调用onDestroy，恢复最后已提交快照，不承诺未提交的最后几毫秒。
- 冷启动先核验当前会话，再重新解析媒体，恢复队列与模式，**保持暂停**；用户点击播放后才出声。若缺失曲目，保留剩余出现顺序；当前曲目缺失则取后继可用项（没有后继取末项）从0播放位置恢复，并给queueNotice提示。
- 已有真实queue或新的播放/队列编辑动作优先，迟到restore不得覆盖。恢复不从历史伪造，旧dev.2从未保存的队列无法事后还原。
- queueRestoring覆盖读盘/解析/设置的恢复阶段；queueNotice区分持久化失败与部分恢复。正常保存后清存储失败提示，但不能抹掉仍有效的缺曲提示。VM重建立即消费Application仓库已有notice，并只收当前有效身份的后续notice。
- 空queue保存清本身份文件但保持绑定；退出clear立即退绑定/清状态，再串行删除旧文件。迟到load/write与A→B→A不得复活旧数据；删除失败身份先隔离，后续重试成功才允许读写。
- 仅空播放器的失败恢复使用retryQueueRestore；播放中的STORAGE_FAILED是保存故障提示，不应以重载旧文件覆盖正在播放的用户队列。

## 7. 分类与错误

songLanguage与albumLanguage接受：
`all, zh, en, ja, ko, instrumental, ru, es, fr, de, sv, vi, yue, it, th, pt, other`。
这是歌曲分类，不是界面语言列表。

选择有效新分类会取消该范围旧请求、清该范围内容并重置分页；歌曲/专辑各自独立，分类属于请求代次。all在歌曲列表映射合法分类集合，搜索与专辑按接口省略language，绝不把字面all送到宿主。缺失/非法Song.language保留null，不编造纯音乐分类。

常用ClientNotice：INVALID_URL/NOT_READY/NETWORK/INVALID_LOGIN/SESSION_EXPIRED/PASSWORD_CHANGED/REQUEST_FAILED/RATE_LIMITED/INVALID_RESPONSE/STORAGE_FAILED/PAGE_LIMIT/PLAYBACK_FAILED/SOME_SOURCES_UNAVAILABLE/FORBIDDEN。不要把NETWORK/INVALID_RESPONSE/NOT_READY显示成“没有歌词”或“空收藏”。

## 8. 诊断与验证边界

仅debug Application安装 `FlareTuneLyrics` 脱敏日志sink，release关闭。允许HTTP状态、封闭机器码/原因类别、格式/同步类型、行数/空行数/译文结构计数、ref长度；不记录URL、歌曲ID、歌词文本、resultRef、账户、Cookie或CSRF。日志用于判断HTTP/结构失败，不根据旧无相关日志的设备记录猜测具体故障。

本轮数据/状态及TLS合同回归通过，不代表当前实例的每首歌词、真实声音、蓝牙、锁屏、后台耗电或Gemini视觉验收已完成。设备覆盖更新与服务器故障状态记在任务；不要将WIP Git备份当正式发布。
