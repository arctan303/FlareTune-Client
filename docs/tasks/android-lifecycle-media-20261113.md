# ANDROID-LIFECYCLE-MEDIA-20261113

目标：退后台返回原页；系统媒体面板新增播放顺序和收藏。后续用户截图补充搜索歌曲已有结果却报无法识别内容，调查发现合法YT歌手/专辑响应的客户端兼容缺陷，一并修数据消费和必要字段展示，不改Gemini的其他页面设计。

基线：共同433ba8a，起点客户端工作树干净。用户继续让Gemini改其他UI；本协作限定MainActivity/VM导航、ClientApp极小恢复守卫、MediaSession系统控件与歌词无关的搜索响应兼容。共享NowPlayingScreen/ClassicInterlude/原strings/PlayerLyricsMotionTest/research出现Gemini并行工作，保留且不声称属于本轮实现。后续从同一共享分支继续，由用户调度。

路线：维护+已确认的小型系统媒体增量，单一短任务。采用Design-Brief现有壳层/播放器与真实actions，不重设计界面。后台系统收藏和导航恢复涉及当前账号/媒体项、迟到结果，按R2保留拒绝路径证据和独立复核。宿主当前存在其他人API/歌词补丁，本轮不修改/部署。

根因证据：平板任务栈出现两个MainActivity，manifest主入口无launchMode；同实例setUiVisible并不导航。新Activity/系统重建创建的VM默认HOME，原ClientApp在RESTORING清保存的全屏播放器。修主任务复用与PendingIntent，SavedStateHandle只保存同实例/账号的路由和查询/筛选/详情标识，详情重新读取；退出/切号/改密清旧路由，Android状态不保存Song、URL、Cookie、CSRF或resultRef。保存的播放器面板不被初始化空Player和RESTORING误清，真实队列清空/退出仍关闭。

系统媒体：保留上一首/播放暂停/下一首，额外两槽为播放顺序和收藏。顺序→列表循环→单曲循环→随机，作用同一Player并随状态更新。收藏用现代本人favorite列表/完整详情/带revision写入；绑定当前曲目令牌/连接，拒绝旧命令/旧身份，失败不乐观标成功，真实成功经Application内存事件同步UI。最多5槽和OEM最终布局依据[Android媒体界面](https://developer.android.com/media/implement/surfaces/mobile)与[Media3控制](https://developer.android.com/media/media3/session/control-playback)，不承诺所有系统固定显示五键。

导航实现依据：[Android任务与返回栈](https://developer.android.com/guide/components/activities/tasks-and-back-stack)、[SavedStateHandle与自定义工厂](https://developer.android.com/topic/libraries/architecture/viewmodel/viewmodel-savedstate)。恢复系统保存的任务状态，不承诺显式清data/卸载/强制结束任务后仍恢复页面。

搜索：宿主固定YT产物normalize.item内存回放能合法产生songCount:null的artist和yt:album:...的album，旧客户端requiredInt/validAlbumId拒绝；歌曲分支可独立成功。截图具体失败请求尚无带认证回执，不把合同回放写成真实HTTP定位。修复须保留未知计数、真实远端ID与详情，合法有界opaque ID不被拒绝，非法结构继续报错；未知计数不展示伪0。

验证计划：原页前后台/重建/账号隔离、保存播放器状态、真实MediaSession preferences/命令、模式与持久化、收藏并发/网络/身份和现代接口合同、外部搜索解析与详情。原缺陷负对照在隔离副本完成，不回退共享树他人源码。聚焦后必要完整测试/APK/Lint，R2独立复核；固定APK后按既有授权覆盖平板回读，不自动写真实私人收藏来验收。

状态：实现、验证、独立R2聚焦复核与平板部署完成。授权沿用客户端开发测试覆盖更新、远程备份，不触宿主部署、main、生产/Demo或正式发布。完成后回填真实验证和缺口。

## 最终交付与证据

- 统一接口对照Web的client/src/services/catalogRead.js、utils/musicSource.js及ArtistDetailView，Android没有另一套后端。YT合法null计数/opaque ID已兼容，真实ID贯穿详情、同名key、最近搜索、SavedState；unknown与0分开。截图具体原请求未取得认证HTTP回执，合同复现与8项TLS测试闭合，不声称已对原查询抓包。
- 7组聚焦59/59通过，含Navigation10、LifecycleUI6、Favorites15、Controls6、ServiceQueue4、ExternalCatalog8、FavoriteConcurrency10。普通迟到GET的测试在真实IO线程挂起，先让新ack上屏再释放，覆盖列表/详情/null/显式完整播放/summary5-detail4及换号。系统模式断言实际Player flags，不能只用自己的枚举解释结果。
- 独立R2初审长路由、恢复空队列面板、普通GET收藏回滚、未知占位及系统随机语义均已闭合，59项与原完整318项XML由审查者直接读取确认。20生产文件受审聚合4c3c366c4da4cb7c5c03e9ffc6bacae847a83755d31bfb8111dfa6f9acddc128；最后Gemini三处仅删除搜索历史小标题/调整2dp间距，未改这些权限、恢复和数据守卫，随后最新完整318项再次通过。
- 原导航缺陷负对照在android/build/lifecycle-control-20261113：只禁用route restore及恢复中清panel，原样2测试实际失败（expected SEARCH was HOME、player_play不存在）；同副本恢复正向源码2/2通过，未回退共享工程或改断言。代码与日志保留，不声称所有新能力都做过负对照。
- 首轮系统测试夹具nullable/API差异后修正；新TLS夹具NETWORK失败补全环回SAN与登录后8/8通过。首个完整317项通过但Lint5 WrongConstant失败，改用当前SDK等值SessionError标准常量，不压Lint、不建立豁免。历史失败日志保留。
- Gemini并行写入曾使共享构建输入变化，未将该过程冒充不可变产物。固定android/build/lifecycle-media-candidate-20261113快照；用户确认其完工后纳入最后3文件，最新android/final.log BUILD SUCCESSFUL 1m44s，实际318/318、0失败/错误/跳过，Lint0错误/65警告/1提示，assembleDebug与processReleaseManifest通过。source-input.json固定107文件摘要；交付时逐文件核验共享源码与候选一致。该快照只用于构建，不是另一开发基线。
- APK android/build/deliverables/FlareTune-0.1.0-dev.7-20261113-debug.apk，SHA256 1EF70343E23E9552E333DA4F83DCAAB262F42317EDACA83D3B08D26305CCCDA9。签名与16KB ZIP对齐通过。用户明确“部署”后ADB install -r Success、MainActivity Status ok，回读code7/version0.1.0-dev.7与已安装base.apk摘要一致；未卸载/清data，日志android/build/deploy-dev7-20261113.log。
- 平板SDK37，部署后只读任务栈一个MainActivity。系统原生MediaSession状态已读到两个额外custom动作（播放顺序、取消喜欢），非仅App内部按钮；标准三键由Player继续提供。截图看到真实播放/歌词与当前已喜欢状态。没有替用户自动点击真实收藏或抢占其正在进行的测试；实际每个OEM按钮摆放/操作、用户前后台听感与云写入仍不冒称全面验收。

共同工作区仍为FlareTune-Client / codex/android-client-backup-20261112，本次把已完工Gemini修改与本协作修复一并备份，Git提交以分支回读为准；其他宿主仓库的并行API/歌词补丁未修改/部署。后续由用户继续调度，保持同一基线。
