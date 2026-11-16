# FlareTune Client

FlareTune 官方原生音乐客户端，连接你自己的 FlareTune 实例，专注于日常听歌。

## 项目方向

- Android 已有开发工程，使用 Kotlin、Jetpack Compose 和 Media3。
- Windows 是后续官方程序方向，目前尚未实现。
- iOS 暂留社区参与空间，目前没有官方工程。

当前为开发版本，尚未正式发布客户端。已有功能和本地测试不代表所有设备、蓝牙、锁屏或后台场景都已验证。

## 使用范围

客户端连接已有的 HTTPS 实例，使用实例账号登录，提供曲库浏览与搜索、个人收藏与歌单、播放队列、歌词及系统媒体控制。实例初始化和管理员操作由服务端负责。

服务端项目见 [FlareTune](https://github.com/arctan303/FlareTune)，接入合同见[宿主 HTTP API](https://github.com/arctan303/FlareTune/blob/main/server/API.md)。具体在线能力取决于实例版本及已安装的插件。

## 开发协作

日常开发在 `dev`。`main` 禁止直接开发，只接收通过检查的合并；常规由 `dev` 经 Pull Request 合入。确需隔离的任务从 `dev` 建立临时分支，完成后先合回 `dev` 并验证，再删除支线及临时工作树。紧急修复也必须同步回 `dev`。

## 本地构建

工程位于 `android/`。准备 JDK 17 或更新版本、Android SDK Platform 36 与 Build Tools 36.0.0；通过本机 `JAVA_HOME`、`ANDROID_HOME` 或 `android/local.properties` 配置工具位置。

在仓库根目录执行：

```powershell
.\scripts\build-android.ps1
```

同时运行 Android Lint：

```powershell
.\scripts\build-android.ps1 -Lint
```

也可以进入 `android/`，使用 Gradle Wrapper：

```powershell
.\gradlew.bat :app:assembleDebug :app:lintDebug
```

首次构建需要访问项目依赖与 Gradle 分发源。请勿提交账号凭据、签名密钥、本机 SDK 配置或构建缓存。
