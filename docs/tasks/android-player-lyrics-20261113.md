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

状态：实现、本机验证、测试站部署及平板覆盖更新完成；独立客户端分支单独远程备份，提交以分支Git记录为准。没有把共享树Gemini新增界面合入本包。尚未做修复后同条件真机帧率A/B或用户实际听感验收；也未取得该中文歌曲修复后带认证HTTP的独立回执，真实资产零副作用重算及部署内容回读与HTTP实测应区分。旧gfx混合会话不用于量化提速。后续将此分支有选择地整合进Gemini最终UI，保留本文服务端权威与交互回归，不再恢复客户端语种判断。
