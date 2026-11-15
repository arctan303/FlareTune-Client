# Android 数据与 UI 接线合同

本文件给 Gemini 与后续 UI 协作者使用，以当前 Kotlin 类型和真实动作实现为准。用户后续明确授权本协作补齐Web普通听歌能力并实现基础UI，Gemini后续迭代视觉；这在本轮范围替代此前UI仅由Gemini修改的限制。用户挂机期间只做本机验证；2026-11-13用户返回后明确授权覆盖更新平板，dev.4已安装启动，不扩大为真实云端写入验收。视觉规则仍归 [Design-Brief](Design-Brief.md)。

历史已验证且已安装的基线：`0.1.0-dev.3` / versionCode 3，固定最新UI输入143/143 JVM/Compose/Robolectric 单测、assembleDebug、Lint通过（0错误/42警告）。143项包含本轮fresh R2两项修复，原审查者已聚焦确认关闭；旧43/85/131/139项是各自输入的历史证据，不证明全设备真实功能。设备与服务端歌词问题另见 [任务](tasks/android-data-20261112.md)。

## 1. 单一入口与分工

- UI继续调用 `ClientApp(state: ClientUiState, actions: ClientActions, imageLoader: ImageLoader?)`。真实入口由 MainActivity 注入 ClientViewModel，不在 Composable 内创建第二个API、MediaController或本机存储。
- 类型定义：[`ClientContracts.kt`](../android/app/src/main/java/com/flaretune/client/ui/ClientContracts.kt)、[`Models.kt`](../android/app/src/main/java/com/flaretune/client/model/Models.kt)、[`LibraryModels.kt`](../android/app/src/main/java/com/flaretune/client/model/LibraryModels.kt)、[`LyricsModels.kt`](../android/app/src/main/java/com/flaretune/client/model/LyricsModels.kt)。
- 歌曲行交给 `actions.playSong(song)`；历史入口优先 `actions.playHistorySong(song)`，完整收藏快捷入口交给 `actions.playFavorites()`。不要从UI将首屏数据组装成“完整收藏/专辑”。
- 所有封面复用传入的认证 ImageLoader。不要自行新建图片客户端、把Cookie拼进URL、重新请求未认证封面或缓存临时签名链接。
- 新动作有兼容默认实现，只保证旧UI实现者继续编译；**默认空函数不是界面已接线证据**。当前真实 ClientViewModel 已覆盖本页列出的动作，基础UI已接线；后续Gemini迭代继续绑定同一actions，不能创建另一套私人数据或播放事实。
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
| 继续收听 | `continueListeningSongs: List<Song>` | `playSongs(continueListeningSongs,index)` 播放当前混合列表 |
| 播放历史 | `playHistory: List<Song>`、`historyNotice` | `playHistorySong(Song)` 在当前身份下重新解析音频后播放 |
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
- dev.5新增 `translationLanguage: String? = null` 与 `translationTargetLanguage: String? = null`，原样解析 `translation.language/targetLanguage`，只供显示服务端目标语言及诊断，不在客户端推断翻译资格。

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
- Android与Web共用歌词接口和工作台来源链。服务端决定有无歌词、已有译文、是否支持/需要翻译；客户端不根据Song.language、歌名或字符覆盖这一结论。GET可按服务端既有自动补全策略产生pending，客户端只消费/有界轮询，不因进入播放器或显示开关主动POST。仅严格instrumental的既有零请求分支保持原约定。
- 只有用户明确调用 `requestLyricTranslation()` 才触发当前有效resultRef的 `POST /api/lyrics/translation`。resultRef由协调器内部保管，不出现在ClientUiState，不由UI持久化或拼装。服务端supported且state为IDLE/PENDING/FAILED才有生成状态入口；PENDING仅显示不可重复点击的进度，只有IDLE/FAILED可提交POST，UNAVAILABLE不提交。NOT_NEEDED不显示翻译入口，即使supported=true或存在旧译文；纯音乐无入口。译文显示开关要求确有返回的译文行，不能用supported替代实际译文。已有行内tlyric兼容保留，UNAVAILABLE/supported=false只禁止新生成，不删除接口已返回的译文或其显示开关（NOT_NEEDED除外）。
- 生成/补译轮询失败但当前引用仍有效时，保留READY原文、激活行和播放，translationState=FAILED并显示独立安全提示；无效/退役引用、越权、歌曲删除、退出/切号仍优先执行原有清理/失败规则，不能用旧词掩盖。
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
- 原只读范围已由本轮用户授权替代：收藏与普通歌单写入、成员/资料库排序、封面均已实现真实动作，详细见第9节。其当前本机验收状态归补全任务，不能用dev.3的旧结果冒称新实现通过。

## 5. 继续收听与设备历史

**继续收听读取state.continueListeningSongs；历史读取state.playHistory；完整当前队列读取state.playback.queue。三者不得互相兜底。**

CONTINUE-LISTENING-20261113：继续收听按近期/收藏/累计/搜索4/3/3/2名额混排，近期7天次数>=2且24小时重复优先；来源+稳定ID去重而非标题/歌手合并。同池顺延、跨池补位，候选耗尽显示实际数量。累计次数加权、收藏随机、搜索实体按最近顺序加权；仅歌曲实体参与，并经当前连接重新解析，保留来源/版本和最新封面，失败释放名额。启动/显式刷新读取真实本机搜索记录，不消费可能已锁定的旧展示列表；所有数据到齐后生成一次快照，后续播放/切歌不重排。账号/实例切换、刷新代际拒绝迟到结果。

历史由实际Media3 Service的current+isPlaying事件记录；整队列、暂停/恢复、拖动或仅恢复暂停队列不记录为已经听过。最近播放倒序、同songId去重、最多200条；再次真正切回该曲会更新位置。VM销毁后服务播放仍可记录，单纯页面存活不作为记录条件。

`HistoryEntry`仅有id/title/artist/album/durationSeconds/language/playedAt；按规范化origin+accountId的SHA-256分键存储，v1 JSON≤512KiB。绝不保存audioUrl/coverUrl/Cookie/CSRF/resultRef或临时签名链接。切号/退出以detach撤销旧内存绑定并保留该身份磁盘历史；generation和串行IO拒绝旧读写发布到新账号。显式历史清理仍删除当前身份记录。此规则由2026-11-13多账号请求替代原切号删除旧存储。

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
- 空queue保存清本身份文件但保持绑定；切号/退出detach立即退绑定/清状态并保留磁盘快照，必须先于Player清空；显式clear仍串行删除旧文件。迟到load/write与A→B→A不得复活旧数据；删除失败身份先隔离，后续重试成功才允许读写。
- 仅空播放器的失败恢复使用retryQueueRestore；播放中的STORAGE_FAILED是保存故障提示，不应以重载旧文件覆盖正在播放的用户队列。

## 7. 分类与错误

songLanguage与albumLanguage接受：
`all, zh, en, ja, ko, instrumental, ru, es, fr, de, sv, vi, yue, it, th, pt, other`。
这是歌曲分类，不是界面语言列表。

选择有效新分类会取消该范围旧请求、清该范围内容并重置分页；歌曲/专辑各自独立，分类属于请求代次。all在歌曲列表映射合法分类集合，搜索与专辑按接口省略language，绝不把字面all送到宿主。缺失/非法Song.language保留null，不编造纯音乐分类。

常用ClientNotice：INVALID_URL/NOT_READY/NETWORK/INVALID_LOGIN/SESSION_EXPIRED/PASSWORD_CHANGED/REQUEST_FAILED/RATE_LIMITED/INVALID_RESPONSE/STORAGE_FAILED/PAGE_LIMIT/PLAYBACK_FAILED/SOME_SOURCES_UNAVAILABLE/FORBIDDEN。不要把NETWORK/INVALID_RESPONSE/NOT_READY显示成“没有歌词”或“空收藏”。

## 8. 诊断与验证边界

仅debug Application安装 `FlareTuneLyrics` 脱敏日志sink，release关闭。允许HTTP状态、封闭机器码/原因类别、格式/同步类型、行数/空行数/译文结构计数、ref长度；不记录URL、歌曲ID、歌词文本、resultRef、账户、Cookie或CSRF。日志用于判断HTTP/结构失败，不根据旧无相关日志的设备记录猜测具体故障。

dev.3数据/状态及TLS合同回归已通过，用户已确认歌词恢复；dev.4补全候选的测试/构建/复核状态见[本轮任务](tasks/android-listening-capabilities-20261112.md)。不据此声称每首歌曲、真实声音、蓝牙、锁屏、后台耗电或后续视觉优化均已验收。设备覆盖更新与服务器故障状态记在任务；不要将WIP Git备份当正式发布。

## 9. 收藏、个人歌单与完整成员写入

新增状态：favoriteSongIds、selectedPlaylistEntries、playlistShelf、libraryMutationBusy/Notice/Conflict/Results/Version。成功序号只表示当前完整操作明确成功或noop；partial/失败/409不据此关闭编辑器。UI保留草稿，真正修订冲突需关闭/刷新/重新审阅，不将旧顺序配新revision盲重发。

| ClientActions | 用途与宿主合同 |
| --- | --- |
| toggleFavorite(song) | 用真实favorite ID，通过现代playlist-songs批加或成员DELETE，读取实际成员态，跨页同步收藏集合 |
| createPlaylist(name,description,songIds) | POST /api/account/playlists；新意图新幂等键，同一表单网络重试复用键，不重复创建 |
| updatePlaylist(playlist,name,description) | PUT /api/account/playlists/{id}，expectedRevision；favorite元信息受保护 |
| deletePlaylist(playlist) | DELETE同路径带expectedRevision JSON；只删本人普通集合，不删共享歌曲、不清播放队列 |
| addPlaylistSongs(playlistIds,songIds) | POST /api/account/playlist-songs，真实targets各带revision；按目标反馈applied/noop/failed，partial保留剩余目标重试意图 |
| removePlaylistSongs(songIds) | 当前详情完整membership原子替换，保留未删成员及不可播放项；不使用过滤后的playlistSongs反推集合 |
| replacePlaylistOrder(songIds) | 完整ID集合、顺序与expectedRevision；禁止丢项/重复/越权；排序动作不增删成员，空歌单只能提交[]。明确移除全部成员使用removePlaylistSongs，不借排序清空非空集合 |
| replacePlaylistShelfOrder(ids) / movePlaylistShelf(from,to) | GET/PUT /api/account/playlist-shelf的完整混排集合与独立shelf revision；favorite与普通歌单均保留一次 |
| playPlaylist(playlist,shuffle) | 重新读取完整详情后建立播放队列，不从首屏预览截断 |
| dismissLibraryMutationNotice() | 新UI意图清旧错误/目标结果；同一失败表单重试不调用，以保持幂等键 |

每账号最多50个普通歌单，favorite另计；每歌单500首唯一歌曲。名称trim后1–40 Unicode码点、简介0–300。批加1–50目标、1–100首唯一歌曲且乘积≤500；UI不无声截断。容量/图片限额映射PLAYLIST_LIMIT、PLAYLIST_SONG_LIMIT、IMAGE_UPLOAD_LIMIT、IMAGE_TOO_LARGE等封闭安全文案。

PlaylistDetail.entries / selectedPlaylistEntries是完整成员；其songIds用于写入完整性，.songs / playlistSongs仅为可播放投影。Song/PlaylistSong.canPlay不代表历史元数据不可重试；历史行仍应调用playHistorySong重新解析。只有明确的歌单不可播放成员禁直接播放，仍能排序或移除。

## 10. 歌单封面与选歌

setPlaylistCover(playlist,sourceUri)只接系统照片选择器临时content URI，设备处理器有界读取、解码采样、中心方形/1024缩放并重编码WebP；不保存URI或原EXIF，不申请整个相册读取权限。UI选择依据[Android照片选择器](https://developer.android.com/training/data-storage/shared/photo-picker)。clearPlaylistCover(playlist)移除自定义槽引用。

宿主三步为POST /api/account/images/upload/playlist、GET/PUT /api/account/images/slots/playlist/{id}，图片槽revision独立于playlist.revision。slot仅本人普通歌单；失败/取消尝试DELETE未采用draft。旧网络退休后不会再发送凭据，未引用草稿保留宿主TTL回收兜底，不承诺退出后立即远端删除。

选歌状态pickerSongs/Query/Loading/LoadingMore/HasMore/Notice独立于全局检索；动作searchPlaylistSongs、loadMorePlaylistSongs、closePlaylistSongPicker。先打开/确认目标详情再启动选歌，不能从资料库菜单沿用旧selectedPlaylist。多选遵守100首批次边界，提交给真实目标ID。

## 11. 检索、发现、历史与统计

dev.7与Web的catalogRead/musicSource链路对齐：Artist新增默认兼容的id/knownSongCount/knownAlbumCount，Album新增knownTrackCount，ArtistDetail新增albums。外部歌手/专辑ID是宿主返回的完整opaque ID，UI把原Artist/Album交给actions，不能用显示名称重新构造而丢id。最近搜索/页面恢复也保留来源ID，同名本地与外部行有独立key。known*为null就是未知，不把0或已加载首批数量伪装成总量；旧Int字段保留仅供源码兼容，真实展示消费known*。外部歌手详情使用返回的albums，本地歌手继续原本的分页专辑查询。

- 三类检索：歌曲原query/songs；歌手artists/artistQuery/artistsLoading*；专辑searchAlbums/albumSearchQuery/albumSearchLoading*，各自错误/分页互不覆盖。动作refreshArtists/loadMoreArtists、searchAlbums/loadMoreSearchAlbums。本地目录songSort=asc|desc只读切换收录顺序，不永久改曲库。
- 歌手详情selectedArtist、artistSongs、artistAlbums及各自Loading/More/HasMore/Notice；openArtist/closeArtist、loadMoreArtistSongs/loadMoreArtistAlbums。署名拆分沿Web规则，专辑用真实opaque ID，不自造ID。
- discoverySongs/spotlightArtist/discoveryLoading/Notice，refreshDiscovery消费已有random/spotlight宿主接口；当日账号+实例metadata缓存先重解析当前媒体，刷新有Web同类冷却。不是新增AI或插件推荐算法。
- recentSearches是song/artist实体，recentSearchTerms是提交的词，不在每次输入时保存。openRecentSearch会重新解析；实体/词可单删、清空，私有分键，退出清UI而不抹除其他主体历史。只存展示元数据。
- playHistoryPlayedAt提供本机实际播放时间，removeHistory/clearHistory仅改活跃身份下历史，不解绑后续记录、不清队列或云统计。“继续收听”来自continueListeningSongs的独立混合快照，队列有独立入口。
- playSongs(songs,startIndex,shuffle)明确当前列表上下文；playNext/enqueue/clearQueue作用真实Media3 Timeline并同步持久化，不回写歌单。历史/恢复占位会按当前身份重解析，不捏造已播放记录。
- playStats/statsLoading/Notice与refreshPlayStats是真实GET /api/account/play-stats；常听歌手按真实常听单曲聚合，不把队列或最近记录当次数。窗口查询复用该接口的days=1/7与view=summary，PlayStats.window为PlayStatsWindow(days,fromMs,toMs)，必须匹配请求且跨度为days×86400000；缺失/错误回显按无近期统计降级，不把累计榜充作近期榜。时间均毫秒，窗口内play_count/last_played_at不可与累计混用；无days保持原DTO兼容。
- 独立QualifiedListeningTracker每播放迭代最多一事件：真实连续进度累计30秒，短于60秒曲目半程也合格；暂停/seek跳跃不算。Service后台同样采样，不能用历史启动事件冒充。PlayEventRepository以稳定eventId私有持久化、最多25批次、只删除acceptedEventIds、失败退避重试；身份退役拒绝旧ack污染新主体，未确认旧事件仅由原身份后续重试，不持久化网络凭据/媒体URL。
- 音量与译文显示仅本机偏好；gain即时控制并有界去抖保存，不触系统或蓝牙音量，不修改服务器来源/AI配置。

## 12. 当前补全验收边界

dev.4/code4补全已通过本机验证并覆盖安装；后续隔离的dev.5/code5修复已通过255项测试、构建与Lint（0错），2026-11-13覆盖安装并回读版本/摘要一致。随后用户要求双方合并并确认Gemini停写，dev.6/code6已整合其11文件前端快照，258项测试、构建及Lint0错误，已覆盖平板并回读版本/摘要一致。后续双方以共享工程同一分支提交为基线，由用户调度，详见[播放器歌词修复](tasks/android-player-lyrics-20261113.md)。挂机期间禁止自动覆盖的限制适用于此前开发阶段；本次安装不等同全部真机功能验收，也不以云端私人数据写入代替本地TLS/状态/播放器/Compose测试。当前实现、失败修复、独立复核、最终APK摘要和备份由[补全任务](tasks/android-listening-capabilities-20261112.md)统一记账；不要把默认空actions或单纯编译通过当成功能力。

## 13. 页面恢复与系统媒体（dev.7）

主入口singleTask配合媒体PendingIntent的CLEAR_TOP/SINGLE_TOP，返回现有任务不创建首页副本。Android SavedStateHandle只存同origin/account身份hash对应的路由、检索/筛选及详情ID，详情重新读取，不存URL/凭据/Song/resultRef；退出/切号/改密清除。ClientUiState.navigationIdentity用于壳层恢复隔离；playbackReady默认true保持fixture兼容，真实VM初始false，连接/恢复发起后true，queueRestoring覆盖异步磁盘恢复。ClientApp在RESTORING或媒体恢复中保留保存的面板，确认恢复结束且为空才关闭，后续新播放不会突然打开旧面板。

系统模式与App语义相同：顺序(false,OFF)→列表(false,ALL)→单曲(false,ONE)→随机(true,OFF)；shuffle优先显示，修改同一Player并沿原服务持久化。标准三键继续发布，两个custom按钮用Media3 preferences，Android/OEM决定最终布局。收藏写入绑定真实UID/当前连接/曲目命令，后台无Activity也可用，失败保留真实状态与重试，只有确认读回后才成功。

Application级SystemMediaFavoriteChanges仅内存传播真实完整detail，VM/UI与Service双向同步。最高favorite摘要与完整成员按当前ActiveConnection隔离、revision单调；旧列表/详情/null/旧事件不能回滚新确认状态，明确playFavorites不被被动刷新取消。缺少达到最新摘要revision的完整成员时有界重读一次，仍不足明确失败，不混用新revision与旧members。

本轮318项、R2聚焦复核、设备安装与实际系统custom动作回读见[任务](tasks/android-lifecycle-media-20261113.md)。不以本机测试冒称所有OEM布局、真实账户写入或所有前后台杀进程策略已验收。

## 歌曲来源关联与具体专辑版本（2026-11-13）

Song 新增默认兼容的 source、artists:List<SongArtist>、artistId、albumId。UI 使用 relatedArtists()/relatedAlbum() 的真实来源引用，不能把 YT 名称重建为本地 Artist，已知专辑使用 openAlbum 直达。关联经 MediaItem 和安全元数据快照传递，恢复媒体仍在当前身份下重新解析。全屏菜单发出导航意图时收起面板，不依赖目标 ID 必须变化。专辑详情播放标记核对 song.id 与明确的 albumId；同视频可在不同专辑出现，不用封面、时长或标题推断实体相等。详情见[修复任务](tasks/android-song-relations-20261113.md)。

## 系统媒体切歌边界（2026-11-13）

用户明确非空队列的上一首/下一首不能因首尾边界消失。系统会话使用与底层Player保持事件一致的转发适配，持续公布可处理的标准切歌命令；手动上一曲沿用previousTrack，下一曲按原Timeline/随机/循环处理，无下一项时不擅自绕回。空队列撤回命令，直播不伪造seek。软件图标尚未选定；本轮仅提供成对SVG候选，品牌资源不随按钮修复部署。详细实现、验证和开发包边界见[本轮任务](tasks/android-system-transport-20261113.md)。


## 多账号与设置底层接口（2026-11-13）

本批只修改数据/会话/播放/VM及单元测试，页面布局样式和侧边栏/设置页接线由下一棒完成。

- SessionStore的readAccounts/writeAccounts原子读写SavedAccountsSnapshot。所有账号信息和Cookie整体AES-GCM加密（沿用AndroidKeyStore旧alias与AAD），不含密码/CSRF。encrypted_session首次读取迁移为暂定origin|记录，验活后补全真实accountId及规范键，不掉有效会话；移除单条不删除共享密钥。
- ClientUiState.savedAccounts只含accountKey/origin/accountId/username/displayName/active/needsReauth，不含Cookie。beginAddAccount/cancelAddAccount支持取消返回；switchAccount验活目标，清旧播放/活跃内存/图片内存、取消休眠并更新navigationIdentity，恢复目标账号历史/队列并加载统计/歌单库。过期只将目标标needsReauth并预填连接表单。logout只移除当前凭据回CONNECT；removeSavedAccount移除当前时按最近使用顺序尝试其他有效账号。
- PlaybackRuntime在清空Player前detach仓库，持久队列不被旧Service销毁的空快照覆盖；图片diskCache以规范origin/accountId的SHA-256分目录，旧共享目录不再复用；Runtime每个身份只创建并复用一个DiskCache，避免A→B→A产生多个journal持有者。显式clearImageCache只清当前账号图片缓存，反馈imageCacheClearResult（CLEARING/CLEARED/FAILED/UNAVAILABLE）。
- PlaybackGateway.sleepTimerFlow / ClientViewModel.sleepTimerFlow为独立StateFlow<SleepTimerState>，只含mode与endElapsedRealtimeMs；setSleepTimer(minutes)、setSleepTimerEndOfTrack、cancelSleepTimer经ClientActions暴露。Service调度+限时唤醒锁，不依赖Composable；结束淡出pause并恢复gain，自动OFF。手动跳曲/替换列表取消曲终休眠。
- keepScreenOnEnabled默认true，通过setKeepScreenOnEnabled持久化至playback_display_v1，失败保留原选择；本批不修改全屏播放器现有keepScreenOn接线。
- 验证、R2审查及硬件边界见[多账号与休眠任务](tasks/android-accounts-sleep-timer-20261113.md)。Keystore硬件、真实锁屏精度与OEM后台策略仍需设备验证，JVM测试不替代真机证据。

## 第二轮体验与队列保护契约（2026-11-15 已实现与审查）

对应任务计划见 [第二轮体验与队列优化规划](tasks/android-ux-round2-20261115.md)，下列契约已完成 Batch A / B / C 接线并通过单测、UI 测试及独立审查（A-1 / B-1 / C-1）：

- **队列相邻预览索引与防闪烁同步（Batch A）**：`PlaybackSnapshot` 需支持在随机/循环模式下仍稳定提供可用前后曲目预览索引（或由 `QueuePlaybackOperations` 解析真实的队列前一项/下一项，而不受 `<= 3000ms` 播放进度阈值屏蔽）；`QueuePanel` 拖拽落点提交时维持乐观条目顺序与稳定唯一键（含重复曲目唯一出现序号），待播放器回调到达且同源确认后再收敛，避免重置列表与视觉闪烁；首次打开队列时定位并居中滚动至 `currentIndex`。
- **单曲插播与队列快照历史（Batch B）**：
  - 新增显式语义动作 `playSingleAsNext(song)`（插播到当前曲目下一首并立即切播，保留原有队列上下游），供搜索结果单曲点击、播放历史单曲点击及首页“来自你的曲库”单曲点击调用；列表/歌单/专辑/继续收听/随机推荐/“播放全部”按钮继续调用 `playSongs` 整队替换并播放。
  - 按规范 `HistoryIdentity`（`origin + "|" + accountId`）持久化四类最近记录：播放单曲（现有 `PlayHistoryPage` 底座）、访问过的歌手、播放过的专辑/歌单，以及**队列替换前快照**（仅当当前队列非空且即将被整队替换/切换播放时捕获，固定容量滚动淘汰如 15–20 条），提供一键恢复历史队列动作（`restoreQueueSnapshot(snapshotId)`）。
- **首页“来自你的曲库”按语种比例取最新与歌单排序/批量选择（Batch C）**：
  - `FlareTune` 服务端 `/api/songs?counts=language` 及 `/api/songs?language=...` 扩展对未打标语种（`language IS NULL OR TRIM(language) = ''`）的计数与筛选支持（`untagged`）；客户端首页按各语种（含未打标）曲库占比分配展示名额，并在每个语种桶内按入库最新顺序（`sort=desc`）拉取合并，兼容未升级服务端做降级合并。
  - 歌单库（`PlaylistSortMode`：自定义排序 / 最近更新 / 名称 / 曲目数）与歌单内歌曲排序（`PlaylistSongSortMode`：自定义 / 标题 / 歌手 / 最近加入及升序降序快捷键）持久化至本机轻量存储；歌单详情、搜索结果、全部歌曲与专辑详情接入统一多选态（`selectedSongIds`）与批量底部操作（下一首播放、加入队列、加入歌单、批量移除）。
