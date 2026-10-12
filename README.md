# ComposeSystemUI
kotlin+flow+compose SystemUI DEMO


## 普通应用悬浮窗演示

当前窗口使用 `TYPE_APPLICATION_OVERLAY`，可作为普通应用安装，无需系统 UID。
未配置 `platform-signing.properties` 时，debug 构建使用默认 debug 签名；配置后保留平台签名。

1. 安装 debug APK，打开「SystemUIService 控制台」。
2. 点击「授权悬浮窗」，在系统设置中允许本应用显示在其它应用上层，再返回控制台。
3. 点击「显示悬浮窗」，可打开快捷面板、控制导航栏或关闭控制台演示跨应用显示。
4. 在控制台点击「停止服务」，或在服务通知中点击「停止」移除窗口。

普通应用模式不会替换设备系统栏，也不会为其它应用提供系统栏 Insets。
返回和最近任务按键需要系统注入权限，普通应用模式会提示使用设备导航键；主页通过启动桌面实现。
Wi-Fi 开关受系统权限限制，可通过面板打开系统设置；调整亮度前可点击「授权修改系统设置」。
部分 TV 系统没有悬浮窗授权入口或禁止此类窗口，需要该系统支持才能演示。

本次尝试 `assembleDebug testDebugUnitTest --offline`（含 `--no-daemon` 重试），
均在 Gradle 启动阶段因 `Unable to establish loopback connection` 失败，尚未完成编译或设备验证。
