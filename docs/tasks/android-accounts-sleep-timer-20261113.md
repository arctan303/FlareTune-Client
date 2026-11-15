# 多账号隔离与后台休眠接口

- 依据：2026-11-13 用户请求；代码基线 `61522e4`，用户提供414条单测全绿。
- 路线：产品变更，短任务；R2：凭据隔离、账号热切换与迟到响应可能串用私人数据，需独立审查。
- 范围：加密多会话与旧档迁移、添加/取消/切换/移除/注销、Media3后台休眠、独立截止时间Flow、常亮偏好及图片缓存清理结果。
- 非目标：侧边栏/设置页及任何页面布局样式、真机部署、正式发布；保留已有research未提交修改。
- 契约：每个账号单独验活；失败只使目标需重认证；切换撤销旧代次、停播、清活跃内存与图片内存，历史/队列按origin+accountId保留并重新加载。注销仅删除当前凭据后回CONNECT；移除当前账号依最近顺序尝试有效账号，皆无效回CONNECT。添加取消重新核验原账号。
- 休眠：Service负责TIMED/END_OF_TRACK，淡出暂停后恢复原音量；切换/注销/stopAndClear取消。截止时间仅在独立StateFlow，不向ClientUiState逐秒推送。
- 依赖：现有AndroidKeyStore/AES-GCM、Media3、按身份分键仓库，无新增库。
- 验证：迁移/持久化、同实例与跨实例账号、并发迟到/失效隔离、后台定时/曲终/取消/淡出复位、低频UI状态、偏好与缓存结果；最终执行 `.\gradlew.bat testDebugUnitTest lintDebug --no-daemon`。
- 状态：适用active；实施完成；本地验证通过；fresh R2独立审查通过；未发布/未安装。

## 实现与接线

- SessionStore：encrypted_accounts_v2整体AES-GCM密文，沿用AndroidKeyStore旧alias/AAD；旧encrypted_session原子迁移，验活补全真实accountId，不存密码/CSRF。失效只清目标Cookie并保留needsReauth摘要。
- Connections/ClientActions：添加/取消、切号、移除与仅当前注销；logout回CONNECT、移除活跃时尝试最近有效账号。用户意图在首次挂起前领取代次，过期IO开始与提交均拒绝；VM自动回退每次挂起前后核验身份票据。
- Runtime/仓库：先detach再停播清空，保留账号分键安全磁盘数据、清活跃内存，按身份重新加载历史/队列/统计/歌单；图片内存清空、磁盘身份隔离且复用单实例。
- Service/Gateway：独立静态SleepTimerState截止Flow，Service任务与限时PARTIAL_WAKE_LOCK；600ms淡出pause后立刻恢复gain，EOT用原生pause-at-item-end拦截。临时gain变化不会高频写入全局UI状态。
- 设备偏好keepScreenOn默认true；显式缓存清理返回真实CLEARING/CLEARED/FAILED/UNAVAILABLE。本批页面源码、布局样式均未改；常亮偏好的实际页面绑定由后续接线。

## 验证与审查记录

- 早期定向56项：55通过，1项迁移commit失败测试因Editor替身链式调用返回原对象而错误执行成功commit；已修复替身。
- 首轮完整命令：450项，447通过、3失败。3项是本次替代的“注销删除磁盘历史/队列”旧断言，现已改为保留磁盘并验证清空活跃内存；迟到存储失败测试改为真实写入中触发注销后抛错，继续证明不能污染CONNECT。
- 修复后VM/仓库/ClientData定向29项：全部通过，无失败/错误/跳过。
- fresh R2首审发现：P1 IO分发后才领取身份代次；P1旧移除自动回退可打断新意图；P2同目录多DiskCache实例。均已修复；新增8项IO执行反转测试以及挂起移除、缓存复用回归。同reviewer聚焦复核确认全部关闭，无剩余阻断问题。
- JVM使用真实JCE AES-GCM与Android偏好路径，注入密钥不等于设备AndroidKeyStore验收；实际硬件Keystore、锁屏时序/音频和OEM后台策略仍需真机验证。本批未部署/未安装、无线上账号写入。



## 最终交付证据

- 在 `C:\git\FlareTune-Client\android` 执行 `.\gradlew.bat testDebugUnitTest lintDebug --no-daemon --console=plain`：BUILD SUCCESSFUL，2分13秒。
- 最终41套件、459项（基线414项 + 本批45项），0失败/0错误/0跳过。JUnit XML：`android/app/build/test-results/testDebugUnitTest/`；完整日志：`android/build/accounts-sleep-verification.log`。
- Lint：0 Error / 71 Warning / 1 Hint；报告：`android/app/build/reports/lint-results-debug.html`、同名XML。涉及新增偏好/会话的提示为SharedPreferences.edit的UseKtx建议；当前保留同步commit结果检查。
- fresh reviewer已只读核验最终日志、41套件与Lint XML，结论“本地验证及R2独立审查通过”；未发现邻近新增问题，审查排除原有research改动。
- 输入摘要：`android/build/accounts-sleep-inputs.json`，记录基线与当前20个变更main/test文件的SHA-256；本次工作未提交，未执行push/安装/发布。

- 隔离负对照：仅在忽略的 `android/build/accounts-sleep-negative/` 副本撤销VM过时回退保护和磁盘缓存复用，两项对应行为测试均因具体连接/缓存实例断言失败（7项中2失败）；恢复这两项修复后同组7项全绿。日志 `android/build/accounts-sleep-negative.log` 与 `android/build/accounts-sleep-negative-restored.log`。真实工作区未被回退；该副本已恢复为当前修复代码。

## 下一棒

按数据合同接入savedAccounts与添加/取消/切换/移除动作、needsReauth预填登录；侧边栏/设置页消费独立sleepTimerFlow截止时间局部渲染。常亮开关消费keepScreenOnEnabled，保持全屏且真实播放时才常亮；展示imageCacheClearResult真实反馈。继续沿用本地Design-Brief，不改变本批已验证的身份代次、Service定时与队列隔离边界。（2026-11-14 UI体验接入更新：`switchAccount` / `cancelAddAccount` 通过 `ClientScreen.RESTORING` 与 `splashNonce` 触发开屏动画加载，失败或需重新认证时退回预填目标账号的 `ConnectionScreen`，支持无需重auth时直接点击提交重试或点选其他已保存账号；同日补充个人资料与头像加载/更新/大图查看保存能力：`PATCH /api/account/profile` 与 `/api/account/images/slots/avatar/avatar` `512×512` 静态 WebP 上传。）
