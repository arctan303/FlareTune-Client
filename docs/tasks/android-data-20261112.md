# ANDROID-DATA-20261112

目标：按用户转交的前端分工1，完成Android数据/API/状态与播放支撑：统一歌词、个人歌单读取、首页收藏与完整队列、本机持久历史、专辑/歌曲语种分类。

当前分工：Gemini只写ui/ClientApp.kt、ListeningShell.kt、NowPlayingScreen.kt、ClientIcons.kt、ui/theme/FlareTuneTheme.kt及res/values*；本协作写models/data/ClientViewModel/playback及共享ui/ClientContracts.kt的非破坏性新增。此授权替代此前整个Android仓库停写的范围，不改Gemini视觉实现、研究原件、服务端或远程Git。

依据：用户2026-11-12转交明确职责与5项验收；统一歌词与网页调用同一宿主GET，服务端内部执行工作台/来源链。沿用当前Design-Brief、Product-Spec及宿主server/API.md和server/api/lyrics.md。

验收：
- Song.language默认null，只有严格instrumental零网络产出timeSeconds=0/纯音乐，请欣赏；其他值走统一GET；兼容服务端纯音乐联合分支。歌词状态/词行时间/译文/激活索引/重试供UI使用；pending有界轮询，旧ref409恢复不退出账号，切歌/切身份拒旧结果。
- 本人现代歌单列表/详情、加载错误与开关/刷新；收藏真实id，最新封面按addedAt，playFavorites取完整详情不截断；退出/切换清旧状态。
- 设备私有存储持久历史，实例origin+accountId分键，实际播放事件记录，暂停/恢复/拖动不重复；背景服务同样记录。只存id/展示元数据，不存Cookie/CSRF/resultRef/媒体临时URL；恢复点击先解析当前媒体，退出/切换清理并拒迟到写入。
- songs/albums的独立分类、重置分页/取消旧请求，分类进入请求代次；all使用现有合法集合或按接口省略，不传all给服务端。
- 新字段/方法有兼容默认值，不要求Gemini同步才能编译。数据合同/状态/存储回归、APK编译与Lint，并对个人数据持久化/隔离安排独立R2复核。

风险R2：新持久个人收听历史、歌词跨账号与旧异步结果可能泄露；需要隔离/清理/恢复行为证据。纯数据层不变更UI外观，不将既有43项或Gemini视觉变更声明为本轮已验。

状态：active，本地实施/143项验证完成，fresh R2最后两项聚焦收尾与授权设备覆盖更新进行中，未正式发布。此前不连接远程/不安装的临时限制，已分别由用户本轮WIP备份和达标后平板更新授权替代；根整合数据与播放，Gemini负责UI，Gradle继续单会话独占。
## 后续用户反馈与新增范围

- 用户反馈上一首需滑两次：已确认3秒重播阈值，改为一次直接上一曲（无上一曲才归零）。继续收听在Gemini的ListeningShell读取完整queue，已交前端改playHistory；历史弹层当前读playHistory，生产只记实际current+isPlaying，未确认整队列写历史的根因，不以队列兜底制造历史。
- 用户授权新增持久队列/currentIndex/position/shuffle/repeat，500项/512KiB、只存元数据，冷启先验证当前账号与媒体后暂停恢复；缺失曲目跳过，当前曲目缺失从后继可用曲目零位置；已有实际queue或新的用户播放/编辑优先，旧restore不得覆盖。
- 新增removeQueueItem/moveQueueItem原生Timeline动作及Service事件/每2秒/正常销毁保存。覆盖安装保留新版本已记录的队列；旧版本从未保存的队列不推断为历史来恢复。
- 独立首审3个P2已修：同应用仓库复用VM立即消费已有历史/notice；被动收藏刷新不取消明确播放意图；短ID批量解析、81..256字符ID最多4并发单曲fallback，避免单个合法长ID拖垮整批历史。
- 用户新报入库歌词失败、YT来源及无译文显示开关。现存手机logcat无歌词HTTP/解析信息，旧日志不足定因；确认YT来源选择可符合宿主链（首good优先/weak回退），不因此改源或配置。确认Android ready无显示开关与Web默认关闭不符；新增translationEnabled默认false、设备显示偏好持久化，toggle/set动作仅切显示不POST，UI接线交Gemini。生成动作继续独立。
- 仅debug Application安装FlareTuneLyrics脱敏诊断sink；仅HTTP状态/规范机器码/结构计数，无URL/歌曲ID/歌词/ref/凭据正文，release关闭。不得为排障放宽解析、修改服务端配置或绕过账号隔离。
- 对用户正常回复不加“后端”；只有需要用户转交Gemini的消息带“后端：”。

当前证据：第一轮85项数据+旧安全回归及APK/Lint通过；独立原始账号substring负对照14项1失败、恢复14通过，实际工作树未回退。新增队列后全量131/131与APK/Lint通过；显示偏好/诊断更新后139项回归通过；新增两项R2修复后143项与APK/Lint通过，聚焦复核状态见下。日志与快照在android/build，不将他人的UI视觉验收归为本轮数据验证。设备只读确认实际MainActivity和当时安装APK匹配构建，已排除误连音流/预览猜测；歌词失败仍等实际请求证据。
## dev.3 本地验证与聚焦收尾

2026-11-12用户进一步要求App开发与宿主歌词排障并行，并授权达到下一次本地可测试标准时更新现有平板。Gemini仅负责UI/资源；本协作负责数据/状态/系统播放，不因等待宿主排障而停止App工作。远程WIP备份单独授权给备份协作者，目标为用户指定仓库；它不等同于正式发布。

- 本轮显示偏好/脱敏诊断输入139/139、APK与Lint通过，日志 `android/build/data-lyrics-diagnostics-final.log`；冻结38文件manifest `465E4F2884849492244E12D68AC1B04088921543515F1BED5D7355C1D6ED7A4A`。保留为修复前真实证据。
- fresh R2对原三P2确认关闭，新增两项：500个合法80字符Unicode ID的JSON可超过host64KiB，以及Service保存失败未持续进入VM.queueNotice。已在FlareTuneApi/ClientViewModel及两个现有测试文件修复。
- 新解析按稳定唯一顺序分批，每批≤200且真实序列化UTF-8≤60KiB，继续复用同一Origin/账号/CSRF/会话门禁；不提高宿主限制。真实TLS回归使用500个Unicode ID，保留缺失略过及输入顺序。
- VM捕获当前连接/身份收集仓库notice，重建立即消费；成功保存只清存储错误，保留缺曲恢复提示；退出取消收集并拒旧身份晚通知。回归覆盖失败→恢复、部分恢复、VM重建和退出晚值。
- 修复后当前143/143自动测试、assembleDebug与Lint通过（0错误/40警告），含当前Gemini UI；日志 `android/build/data-r2-queue-final.log`。固定38文件manifest `694A9ACC6708B1EB88CD55E68022F66341BDF832F10D5AFD77138B58E03715DC`，4文件delta `74BA49AB6D574D7738048808F42C90B30F2057F3347A85641317527C8B8FD239`，位于 `android/build/review-data-r2-queue-20261112/`；原审查者一次focused recheck中。
- versionName `0.1.0-dev.3` / code3，APK `android/build/deliverables/FlareTune-0.1.0-dev.3-20261112-debug.apk`，16,948,244字节，SHA-256 `90CDB1B93BA69B38A9A9415304690262C85B0645282A9C38F3423D9C72F9F20F`。debug v1/v2签名与16KB对齐检查通过；这是开发测试包。
- 用户授权的设备动作：聚焦复核关闭后覆盖安装并启动真实MainActivity，保留账号/应用数据；不使用Preview、不卸载或清数据。回执待补，不将构建通过写成设备全部功能通过。
- 宿主排障已获得真实ready歌词资产（79行/448词），因此用户当前失败不能解释为“本来无词”。最终HTTP/结构故障原因仍由主协作追踪；本App不放宽parser或切源掩盖，不记录私有歌词/ID/URL/ref/凭据。

当前交接：[Android-Data-Contract](../Android-Data-Contract.md)给Gemini列出全部新增状态/动作、歌词/译文开关、个人歌单/收藏/历史/分类、队列恢复/删除/移动与设备存储边界。源码与UI分工继续有效；剩余为本次focused审查、授权覆盖更新、开发中真实功能验证和独立宿主故障收尾。
