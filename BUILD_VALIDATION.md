# v2 构建与验证

- 日期：2026-09-24。
- Gradle 8.11.1 / AGP 8.10.1 / JDK 17.0.12 / Android SDK 36。
- 执行：`assembleDebug testDebugUnitTest`，进程退出码 0。
- 单元测试：10 项，失败 0，错误 0。
- Reducer 测试 8 项；Store 测试 2 项。
- 产物：`apk/SimpleSystemUI-system-unsigned.apk`。
- 包名：com.example.simplesystemui；versionCode=2；versionName=2.0-system。
- APK 检查：minSdk=36，compileSdk=36，targetSdk=36，含 TV 启动入口。
- Manifest 检查：android.uid.system、SystemUIService、STATUS_BAR_SERVICE、INJECT_EVENTS 及前台服务声明存在。
- SHA-256：`77b9542c3b47539b1fefc37ffdabddc83dcb066776e1ce7898f5c90b249faaa2`。

## 签名与运行验证范围

此 APK 未签名，因为没有你设备的平台私钥。它是构建产物，不能直接安装。请配置 platform-signing.properties 后从 Android Studio Run，或使用你的平台签名流程处理该 APK。

未连接你的系统 UID 电视，也没有执行真实 WMS 挂载、隐藏 API、Insets、遥控器导航和厂商亮度实测。当前验证证明代码编译成功、MVI JVM 单元测试通过、APK Manifest 正确，不代表 ROM 集成已经验收。

## 隐藏 API 核对

已对照 AOSP android16-release 的源码核对：

- DisplayPolicy 对 TYPE_STATUS_BAR / TYPE_NAVIGATION_BAR / TYPE_NOTIFICATION_SHADE 检查 STATUS_BAR_SERVICE，并执行 ADD_MULTIPLE_SINGLETON 检查。
- InsetsFrameProvider 的 Android 16 构造签名是 `(Object, int, int)`，SOURCE_FRAME=2，并提供 setSource / setInsetsSize。

平台桥接被集中到 platform 目录；ROM 分支变动时可在此适配。
