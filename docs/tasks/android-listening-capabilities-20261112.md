# ANDROID-LISTENING-CAPABILITIES-20261112

目标：补齐Web普通账号听歌能力，在Android可完成发现/选歌、收藏、个人歌单全流程和播放组织。用户明确举例创建、加歌、收藏及顺序，并授权基础UI由本协作完成供Gemini后续迭代。尽量使用Web相同宿主接口与业务逻辑，排除插件私有业务和管理员能力。

适用性：已完成（本机交付及后续授权设备更新）。产品补全，单一集成交付；不按界面当前缺口裁切数据合同。用户挂机无法反馈，按现有Web可观察行为自主实现，必要工程取舍记录为实现选择，不伪装用户决定。挂机期间仅本机测试，不操作平板或改云数据验收；2026-11-13用户返回后明确授权覆盖更新平板，替代该次设备操作限制，不扩大为云写入验收或正式发布；Git代码备份沿用单独授权，不等同正式发布。

基线：Android已推WIP 26d8522，随后Gemini两UI及5份回执文档未提交变化原样保留；当前dev.3/code3已覆盖平板，最终APK SHA604BBF5444EDA5CC4B8108BE43D2D44E2D28B77BD2F97860F97A0CA63EA05BB0、143/143、Lint0错42警告。用户已确认歌词没有问题。实际本轮开工文件摘要/备份在android/build/listening-capabilities-baseline-20261112，不触碰研究原件或私有AI记忆。

合同/设计：Product-Spec REQ15–18、Design-Brief本轮条款、Android-Data-Contract及宿主server/api/music.md。Web实际能力盘点由本地源码核对，证据暂存兄弟宿主output/android-listening-capabilities-20261112/web-inventory.md，当前清单已收回本文与Android-Data-Contract，研究细项不是运行时依赖。

预期验收：

- 收藏/取消可从歌曲行和当前播放完成，跨页状态同步，失败不显示假成功。
- 创建、元信息编辑、删除本人普通歌单；加入一首/多首、移除、顺序、封面和资料库排列沿用Web接口。favorite保护与真实ID、完整不可播放成员、expectedRevision/幂等/权限保留。
- 选歌/检索/专辑与歌手详情/完整队列播放、下一首/末尾排队、队列编辑、历史删除/清空、合格收听统计及个人播放偏好有真实交互，不新增Web没有的虚假能力。
- 加载/忙态/空态/失败/冲突/取消/删除确认完整；退出/切号/切实例和迟到回包不能污染新身份或重开旧弹窗。
- 本地HTTP合同/状态并发/持久化/播放器行为、Compose交互与渲染、assembleDebug/Lint通过，检查正式Manifest调试边界。测试基于合成fixture和本地TLS，不向真实站点写入。

风险R2：私人歌单写入及全量排序可能影响账号隔离/完整成员，需CSRF/Expected-Account、修订冲突、取消与迟到请求、真正删除和不可播放成员的行为证据及fresh独立复核。普通可逆UI样式按R1验证，不以本地截图冒称真机体验。

分工：album_api独占HTTP/API与其新测试；native_ui独占Models/Contracts/VM/data/playback及状态测试；主协作负责基础UI/资源/需求文档与集成，集中运行Gradle；host_api_review先只读Web盘点，后独占LibraryDialogs及其资源/交互测试；共享文件先约定方法，不并发写同一文件。R2审查由未参与实现的listening_review完成，主协作集成与实际执行验证。

状态：实施、本机验证与fresh R2独立复核完成；交付为debug开发包；用户返回后已单独授权并完成设备覆盖更新。代码备份沿用codex/android-client-backup-20261112，提交由本任务文件的Git记录回溯。dev.4已覆盖安装到平板、未正式发布。

## 实现与交接

| 能力 | 当前实现与入口 |
| --- | --- |
| 收藏与歌单 | 列表/当前歌曲统一操作面板；真实favorite ID；创建/元信息/删除确认；独立选歌多选加入；完整成员移除/长按拖动/上下排序；资料库混排顺序 |
| 数据完整性 | 与Web相同现代API；CSRF/Expected-Account；expectedRevision冲突；创建幂等；逐目标partial/noop与剩余重试；不可播放成员不会被播放投影误删 |
| 封面 | 系统PhotoPicker临时URI→有界采样/方形1024/WebP→上传与独立slot revision；失败尽力清draft，退休网络不重发凭据，TTL兜底 |
| 浏览与发现 | 歌曲/歌手/专辑三类搜索、独立分页/错误；歌手歌曲与专辑；分类/收录排序；现有random/spotlight接口及私有metadata日缓存 |
| 播放组织 | 完整上下文播放、下一首/队尾、真实队列增删/重排/清空及原有持久恢复；继续收听只用实际历史 |
| 个人记录 | 历史时间/单删/清空；提交词和最近实体metadata私有保存；合格进度统计及稳定eventId持久outbox，按真实常听单曲汇总歌手；音量与译文显示偏好 |

新增主要模块：MusicApi/CatalogModels、CoverImageProcessor、ListeningLocalState、QualifiedListeningTracker/PlayEventRepository、LibraryDialogs/ListeningInteractionHost/ListeningCatalogScreens。VM与ClientContracts承接全部真实动作；既有Gemini播放器和资料库变化保留，不以本轮名义覆盖回旧版。接口、字段、状态与UI接线以[Android数据合同9–12节](../Android-Data-Contract.md#9-收藏个人歌单与完整成员写入)为交接入口。

## 本机验证与修复证据

- 最终命令：`.\gradlew.bat --console=plain --continue :app:testDebugUnitTest :app:assembleDebug :app:lintDebug :app:processReleaseManifest`，成功；233项、0失败、0错误、0跳过，Lint 0错误 / 52警告 / 1提示。
- 固定运行代码/测试输入取自`android/build/review-listening-runtime-20261113-v4`，manifest `f215a1cdfe85d60daf47ab1142669bce100d3f9dc6ae33d6b2c9f0fafce0dea3`；剔除文档的代码/测试输入摘要`06e18b63f334228ce6a44012585cb8e12194b5f9cc544b87f2593665425c949c`。最终文档状态不改变该运行输入。完整XML/Lint/JSON保留`android/build/listening-capabilities-final-evidence/`，原失败日志保留，不以旧143项代替本轮验收。
- 回归覆盖现代写入载荷/完整成员/50目标/限额、真实TLS同源与凭据、CAS/幂等/partial与退休身份、封面独立revision/失败清draft/SDK23及27、私有metadata缓存与搜索、后台有效收听/稳定ID重试、队列操作与恢复、Compose真实按钮/保存/取消/长按排序。
- 首次集成修复编译fixture旧JSON/测试API用法及API23 Map兼容；round2曾233项9失败。已修资料库菜单先绑定选歌目标、metadata历史可重解析、继续收听误用队列、旧Android封面采样初值、partial目标刷新后的剩余重试、搜索重试派发。元信息表单改为有界BasicAlertDialog，原4失败与大字号5/5通过，保留全部断言。新UI测试Coil fixture内部读取rule.activity曾触发Robolectric嵌套事件循环，改为组合前缓存resources；生产系统栏未因此改写。聚焦17/17通过后才运行最终全套。
- 最终模拟渲染文件归`android/app/build/screenshots/`：listening-library-compact、listening-playlist-create、listening-search-wide、listening-song-actions-wide、listening-playlist-large、library-form-large-zh、library-target-partial。已目视窄屏与200%字号表单/成员/动作，宽屏需以完整search-wide而非独立弹层推断主页面。
- 隔离负对照在 android/build/listening-negative-controls-20261113：5处精确逆转、6个目标均实际失败，恢复正确副本后同6项通过，0跳过/0 setup失败。归属逆转在真实空picker的行存在断言失败，未夸称后续owner等式已执行；Continue保留原标签验证未播放队列项确实出现，history验证回调缺失，partial验证错误冲突，旧封面验证除零。86个correct输入零差异、23测试文件未动。SDK23 LEGACY的压缩影子写PNG，仅覆盖合法图片流程；SDK27 NATIVE验证真实WebP容器，不声称SDK23原生编码已验。本任务未放宽断言或超时掩盖失败。

## 本地交付边界

dev.4/code4 debug APK `android/build/deliverables/FlareTune-0.1.0-dev.4-20261113-debug.apk`，17745475字节，SHA-256 `945F9F8ECD973AA12910A0D16D9A793FD28BBE0F752BF48915038693ABDB52CF`。Release合并Manifest不含PreviewActivity/ui_preview或debuggable=true；仅debug带预览入口。凭据来自既有Keystore会话，不新增秘密；构建只读本机JDK/Android SDK，debug签名为本机开发密钥，未提交密钥、local.properties、构建物、原APK研究或私有AI工作状态。

本轮仅本机合成fixture/TLS/播放器与Compose运行，不向真实站点写私有歌单/统计或操作照片库。开发挂机阶段平板dev.3及服务器/插件保持原状，当时未进行ADB或覆盖安装；随后按用户返回后的明确授权完成dev.4覆盖更新。服务器/插件未部署，未卸载或清data。正式签名和设备新功能验收未执行。需要恢复代码可使用本轮前备份26d8522或dev.3已交付APK；调试包降级是否保留新metadata需按Android安装规则另验，不承诺强制降级。账号资料/头像、Web全部外观参数、离线、私有插件、管理与iOS不纳入本轮听歌补全。

APK签名v1/v2、16KB ZIP对齐与aapt包信息检查通过：com.flaretune.client、dev.4/code4、min23/target36、MainActivity。日志为android/build/listening-apk-{signature,alignment,badging}.log；无安装/设备启动。暂存文件路径/可识别密钥模式检查未发现问题，回执android/build/listening-staged-audit.json仅保存文件摘要，不是整仓安全审计。

## 独立复核与备份

fresh R2 reviewer listening_review最终聚焦复核通过，无剩余阻断项。复用此前完整扫描，检查原六项发现/拖动/三处v3变更及v4系统字号增强，核实81文件快照、77运行与测试输入同当前树零差异、233项XML、正负对照、真实字号/宽屏PNG、APK摘要/签名/16KB对齐及release边界。该复核只读，未参与实现或运行构建/设备。真实云写入、听感/蓝牙/锁屏/后台耗电/厂商兼容为明确未验范围，不伪装通过。

本轮备份基线26d8522，沿用已授权远程origin与codex/android-client-backup-20261112，功能提交标题为feat(android): complete personal listening and playlist workflows。实际提交及远端对应ref由Git记录回读；不合并main、不打tag、不发Release、不把APK/私有资料推入仓库。

## 用户返回后的平板更新（2026-11-13）

用户明确要求将新版更新到平板供其测试，替代先前挂机期间不操作平板的限制。复用已验证APK，不重新构建；deploy-android.ps1 -SkipBuild对唯一已授权连接的平板执行install -r，返回Success，随后启动真实MainActivity，Status ok且回读为前台topResumedActivity。设备回读dev.4/code4、min23/target36；已安装base.apk SHA-256与945F9F8E…ABDB52CF候选全值一致。未卸载、清data或启动独立预览；当前播放会因应用更新重启，持久状态沿用既有恢复合同。

安装/启动日志与精确回读分别为android/build/deploy-dev4-20261113.log、android/build/deploy-dev4-20261113-verification.json。该回执证明覆盖安装和启动成功，不代替用户接下来的真实播放/歌单/歌词与后台功能测试。
