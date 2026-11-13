# 系统媒体切歌按钮与品牌图标（2026-11-13）

## 目标与边界

用户反馈：单曲或队列边界时，系统媒体面板上一首/下一首消失；封面角上的应用标记难看。按R1有界修复。非空有限音频的标准切歌按钮保持存在，按钮行为沿用当前队列/随机/循环规则；不为显示按钮改变播放模式、自动替换歌曲或账户。空队列和明确不可seek的直播不虚报资格。

用户最新选择 C「声流」，软件图标要求固定纯白背景，不随应用深浅主题切成黑底；保留声波轮廓，白底配色按可辨识度调整。通知版继续使用同形透明单色遮罩，由系统染色。此决定替代此前“尚未选定”的状态；原六组SVG保留作草案历史。用户明确先等Gemini停写，再将本协作修改与Gemini当前修改统一合并、验证；等待期间不合并、不构建部署新整合包、不改运行图标资源。

## 根因

现有sessionExtras已预留previous/next槽位，但Android只留空，仍按Player可用命令决定是否画切歌按钮；ExoPlayer在首尾撤掉对应命令，故单曲next会消失。不能只在onConnect声明DEFAULT_PLAYER_COMMANDS，因为MediaSession最终与实际Player动态命令求交集。

截图封面角上的圆圈播放是锁定Media3 1.11.1的默认通知小图标：其AAR的res/values/values.xml把media3_notification_small_icon别名指向media3_icon_circular_play。工程此前启动图标另为黑底三角占位，不是正式品牌资源。选定后可用Media3官方支持的drawable资源覆盖接入；本轮已撤回该临时接入。

## 实现与验证

已接线PersistentTransportPlayer，通过ForwardingSimpleBasePlayer保持状态/事件与真实播放器一致；底层Exo完成音频配置后包装，listener/session/保存/清理统一使用wrapper。新增真实Exo/MediaSession标准action回归，图标草案另行预览且不进入运行资源；构建、渲染、平板回读完成后补记。旧控制器授权、收藏及队列持久化继续回归。

## 构建与协作

当前共同目录另有Gemini在途页面改造，保留且不覆盖。本次构建快照基于已验证已安装dev.8/1ebbd7a，仅加入本轮媒体服务/图标/测试与版本；不是另一永久开发基线。打包结果不能冒称已验收当前所有在途前端。依已有开发部署授权覆盖安装同一平板，保留数据，不操作生产/宿主/MV插件或歌词资产。

官方依据：https://developer.android.com/media/implement/surfaces/mobile ，https://developer.android.com/reference/androidx/media3/common/ForwardingSimpleBasePlayer ，https://developer.android.com/reference/androidx/media3/session/DefaultMediaNotificationProvider 。

## 最终证据与当前状态

- 26项聚焦全部通过；新8项使用真实Exo/MediaSession发布标准skip action，覆盖单曲、首尾、多曲、循环/随机、空队列恢复、placeholder和BUFFERING/READY/ENDED。初次ENDED测试在暂停尾采样不耗尽；仅修夹具显式play及虚拟时钟，保留断言，定点与整组通过。
- 完整356/356，0失败/错误/跳过；assembleDebug及Lint（0错误/64警告/1提示）通过，full-final.log 1m28s。首个完整运行中收藏状态/revision已正确，仅记录器混入setup的revision3；受控复现确认SharedFlow观察滞后，改test的UnconfinedTestDispatcher并保留所有原断言，整class10项及最终完整通过。未改收藏生产代码。原失败和受控诊断保留host output/system-media-20261113/favorite-recorder-diagnosis.md。
- dev.9/code9已覆盖安装同一平板，安装Success/启动Status ok，摘要回读一致、任务栈一个MainActivity；SHA256 5AD28EDF381B37B3673C2E8B0CB16BF78C4C94363A69B0FAA5BC6A18CCBA5BA2。未卸载/清data。候选基于dev.8加本轮5文件，不包含共同工作区23项当时在途前端变化；这些改动保留给Gemini继续整合。
- 图标仅为[六组SVG草案](../../research/icon-studies/20261113/README.md)，含软件/通知两版、浅深与390px/1400px预览、16/24px检查和打包。13个SVG结构校验通过，最终静态页面24张图片引用与6个内联应用图标完整，通知深浅背景并排展示，无缺图/横向溢出；初次交付时未选择、未部署，随后用户选择C白底版，当前仍未部署。
- 官方scrcpy5.0.1通过WinGet当前用户便携安装（官方GitHub下载与哈希验证），可见窗口“FlareTune 平板投屏”已启动且Responding，D3D11VA视频1920×1200，60fps上限，音频留平板，未开启录制/自动剪贴板同步。scripts/mirror-android.ps1可复用；已验证重复调用保留现有窗口。关闭窗口结束投屏。


平板原生MediaSession最终回读：previous=true、next=true；两个自定义动作“顺序播放”和“我喜欢的”同时保留，非只改App内部UI。签名与16KB对齐通过。运行图标资源与dev.8基线相同，六组候选只在research/icon-studies。远程备份以Git回读为准，共同目录其他前端在途改动保留。

## 等待共同基线整合

当前门槛是用户要求等待Gemini停写，由用户调度。停写后读取双方实际diff与基线，合入已验证的系统媒体修复、当前Gemini前端/数据改造及C白底品牌资源，再做必要验证与开发部署。不要把当前白底决定当作已完成资源接入，也不要覆盖在途文件。Design-Brief中本协作的品牌段落在停写后与Gemini当前设计改动一起对账。
