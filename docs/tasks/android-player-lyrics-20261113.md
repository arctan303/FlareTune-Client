# ANDROID-PLAYER-LYRICS-20261113

目标：修复平板横屏歌词跟随缓慢/卡顿、翻译按钮低于左侧播控；按用户确认改为关闭灰/启用白、透明底与统一封面氛围。随后修复《美酒加咖啡》误报翻译资格，并落实客户端仅消费Web共用接口的职责。

基线：已安装dev.4/code4，客户端743c213；旧233/233测试及APK摘要归听歌能力补全任务。用户确认Gemini继续修改共享前端，要求“先隔离你的修复”，因此从743c213建独立工作树FlareTune-Client-lyrics-fix / 分支codex/player-lyrics-fix-20261113。本轮构建与安装不包含Gemini此后的共享树增量，不覆盖其文件。

路线：R1维护与用户已确认的局部样式修正，单一短任务。范围为播放器、歌词状态/非致命翻译错误、服务端元数据过滤、兼容的可空语言展示字段；不改账号隔离、来源顺序、AI配置、队列、歌词文本/时间或插件包。沿用开发测试覆盖更新及dev通道授权，不卸载/清data，不写真实歌单或统计来冒充测试，不触main/生产/Demo或正式发布。

设计依据：Design-Brief的Apple经典播放器、中性色/封面氛围、48dp与200%字号，以及本轮灰/白、对齐和消费端要求。复用ClassicGlyphButton和原tokens；撤销排障中曾尝试的客户端同语种判断，客户端没有语种/字符翻译资格算法。Song严格instrumental零请求为既有合同，未扩大。

## 实现与根因

- 原isScrollInProgress把程序动画误认成手动拖动，userScrolling又是自动滚动effect的key，动画启动即自取消并暂停2.6秒。现只收真实DragInteraction，含同帧Start/Stop，拖动/fling结束2.6秒后恢复，切歌清暂停。可见行220ms定位，远距离seek直接定位，译文行高变化后重定位，末行也能抵达约30%焦点。
- 左右栏使用实测同高、同68dp底部区域，翻译按钮48dp透明底，关闭灰/启用白加状态点。固定行字体避免激活后行高变化，保留轻量alpha，移除逐行动态模糊/缩放。封面氛围用单层静态有界图；纯色模式保持中性。逐词插值改用单调时钟，未改基础播放器取样或歌词时间单位。
- LyricDocument/LyricState新增默认null的translationLanguage/translationTargetLanguage，仅原样传递服务端字段用于语言名称显示。NOT_NEEDED隐藏入口；已有译文与生成资格分开；显示开关不生成。翻译请求或pending轮询失败时，在有效引用/权限下保留原词和激活行，显示独立翻译失败提示。
- 服务端真实资产为中文、27行KRC，无译文，目标zh；原正文筛选把开头英文署名算入外语，触发AI并留下completion_failed。宿主shouldAiCompleteLyrics先用既有isLeadingProviderMetadata剔除前导元数据再判断，原歌词不删除、不重写。真实资产经实际Workbench源码重算为not_needed / supported:false，AI调用0、写入0，资产不变。已有译文、后段真实外文、未知/误标语种仍保留原合同。

## 验证与交付

- 独立客户端最终assembleDebug、testDebugUnitTest、lintDebug及processReleaseManifest通过：255/255，0失败/错误/跳过；Lint0错误/53警告/1提示。日志android/build/lyrics-dev5-final2.log；XML与源码摘要固定在android/build/player-lyrics-evidence-20261113/。
- 14项播放器回归覆盖实际视口bounds、连续切行/远seek/末行、真实拖动恢复/切歌、译文行高、200%系统字号、按钮几何与灰白透明像素、封面及服务端状态；另7项协调器、1项解析回归。Compose模拟截图已目视，不冒称真机帧率测试。
- 旧滚动分类的隔离负对照确实失败（Line3 top485 vs focus269.1）；同一测试恢复正确实现后通过，未改测试断言或真实共享树。证据源为共享树ignored build/lyrics-scroll-control-20261113，摘要复制入当前evidence。
- 首轮测试夹具缺4个actions后补齐；一次完整构建255通过但Lint拒绝Locale.getDefault，改用LocalConfiguration可观察locale，最终完整门禁通过。保留原失败日志，不声称首次全通过。
- 宿主42/42针对性Node测试通过；新提交3ed67d45f96a20d2765eddb0b2c13e77eea53b16已备份dev并经既有受控通道部署。source718124dffa54ac7e8973ebad660cf1017896a427f16ddae968accb1b2f652e4a，Worker82cc31bc-8ea9-4d95-8481-3970896f1efc为100%；内容回读匹配source。21项配置/安装记录含revision、5插件包、108首曲库元数据和绑定与部署前一致；独立聚焦审查通过。
- dev.5/code5 APK：android/build/deliverables/FlareTune-0.1.0-dev.5-20261113-debug.apk，SHA256 FF1F4B4BBA35E8D82E46474733D42DE479251B9E690DF4457041E5DC8976D0BC；签名v1/v2、16KB对齐、badging通过。已执行隔离树deploy脚本，install Success，MainActivity冷启动ok；回读versionCode5/versionName0.1.0-dev.5、base.apk摘要一致，数据保留。安装日志android/build/deploy-dev5-20261113.log。

状态：实现、本机验证、测试站部署及平板覆盖更新完成；dev.5修复提交b29eecf先本地固定，随后随下节dev.6共用分支一并远程备份。dev.5包本身没有把共享树Gemini新增界面合入。尚未做修复后同条件真机帧率A/B或用户实际听感验收；也未取得该中文歌曲修复后带认证HTTP的独立回执，真实资产零副作用重算及部署内容回读与HTTP实测应区分。旧gfx混合会话不用于量化提速。后续整合由下节明确的新指令承接；本文服务端权威与交互回归持续有效，不再恢复客户端语种判断。

## 同一基线整合（用户后续指令）

用户要求“没问题后记得把你的修改和Gemini的并入，这样你们两个同一个基线修改，我来调度”，并通过异步回复明确“已经停写，可以同步共同基线”。这替代本轮只保留隔离候选、不纳入Gemini后续改动的临时限制，未撤销原隔离阶段证据。

以b29eecf歌词修复为底，捕获共享743c213上Gemini真实11文件快照（10已跟踪+新增ListeningComponents），逐文件摘要核对无写入变化，且与歌词修复无实质同文件重叠；协调器/测试共享M标记仅换行归一化差异，不把它们当第二套修复覆盖。集成基础UI、图标、详情/表单、动作面板和资源/测试，未把.gitignored私有材料或其他未提交内容纳入。

合并候选先以原dev.5版本号完成258/258、构建/Lint；随后仅升dev.6/code6，完整assembleDebug/testDebugUnitTest/lintDebug/processReleaseManifest再次通过（2m4s），0失败/错误/跳过，Lint0错误/59警告/1提示。日志android/build/merged-dev6-final.log，固定XML/输入摘要在android/build/merged-dev6-evidence-20261113。播放器及主页真实Compose模拟截图已目视，不等同全设备验收。独立R1集成聚焦检查通过：11文件摘要一致，歌词修复未被覆盖，服务端权威/历史队列区分/完整成员与修订守卫及关键actions接线保留；不冒称全量界面审计或真机听感通过。

合并包android/build/deliverables/FlareTune-0.1.0-dev.6-20261113-debug.apk，SHA256 41A70598797BC8EDC6325E68DF5BB8126C25D7B1256CF6F5C6A1F21E9CF6162C，签名v1/v2、16KB ZIP对齐、badging通过。覆盖安装Success；首次启动处于系统PackageUpdateActivity过渡，随后明确start返回com.flaretune.client/.MainActivity、前台与进程存在；回读version6和base.apk摘要一致。未卸载/清data，安装日志android/build/deploy-dev6-20261113.log。

共同工作区同步完成：两树已快进到合并输入f42ba1321e5f73bb4034c598ea572bea0fa82efe，忽略CRLF/LF的逐项源码比较与dev.6证据一致，工作树干净。已测试隔离输入仍逐项匹配原始字节摘要；Git检出对两文件执行换行归一化，不改Kotlin语义。Gemini原始快照保留stash 0fbf01198abd4a34cb9fe20f8c2a007d74339b04；两份无实质差异文件另保留原字节，Git索引状态刷新后快进成功，没有丢弃业务改动。本次收尾说明提交只改文档，远程备份沿用同名分支，本地共同分支、隔离恢复树与远端HEAD一致性已回读；收尾文档后再次核对。隔离树已转为detached恢复用途，已合入的临时本地分支删除，b29eecf/f42ba13仍在共同分支历史中。后续双方从共享FlareTune-Client分支codex/android-client-backup-20261112继续，用户负责调度；隔离树不再独立开展新功能。


## 翻译入口复核与用户澄清（2026-11-13 后续）

用户要求再次确认按钮来自接口，随后明确已有译文直接显示是正常兼容；目标是杜绝无需翻译时仍有按钮，Worker由另一位助手修复。此前“非ready的旧tlyric必须移除”仅为本协作审计建议，已被用户否定，不作为待实现要求。有效REQ-20、设计入口与数据合同已原位同步；不新增客户端语种/字符/同目标语言判断，不修改Worker或插件。

本次为R1局部维护：既有显示开关和NOT_NEEDED条件保留，抽取共享生成资格供UI与协调器使用。旧协调器使用负面排除列表，可能接受UNAVAILABLE/supported=true的直接调用，尽管UI不展示该生成按钮；现只允许IDLE/FAILED，PENDING仅作进度。已有译文解析、渲染和显示偏好不改。补充NOT_NEEDED夹带旧译文、切歌清旧按钮、纯音乐、无生成能力仍可显示已返回译文，以及不可用状态不POST的行为回归。

协作边界：仅锚点修改共享播放器的生成资格和新增import，不覆盖Gemini其余界面改动；其余UI仍为在途工作。本机验证捕获118个Android输入，HEAD为1e0eca4737b14ac56f7865c0135d8522e2c2cbe6并含共享树快照，固定于宿主output/translation-controls-20261113/candidate，source-input.json记录逐文件SHA256。未将该快照整体回写/提交、未合并图标、未部署到平板。原guard负对照已失败于“Unavailable generation must not POST”（1/1失败，非编译错误）；修复后65/65聚焦回归通过（接口16、协调器27、播放器22），assembleDebug及lintDebug通过：0 错误/68 警告/0 提示。已目视1280×800本机模拟渲染：NOT_NEEDED无按钮、返回的旧译文可见且白色显示开关保留；无自动POST。固定候选逐文件摘要未变化，本轮5个源码/测试文件与共享工作区一致；期间仅其他协作者ClientUiTest继续变化，不宣称完整Gemini回归已覆盖。日志、原失败XML、截图和validation-summary.json位于上述证据目录。候选APK SHA256为18616887BF518F52F2CA8FF34B3619913DE7DF9FF1352FE84CA72FC33D86A532，仅本机产物，未安装平板；Worker接口必要性误报仍由另一位助手修复。
