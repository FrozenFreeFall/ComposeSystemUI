# ComposeSystemUI

Kotlin + Compose 的简单 SystemUI 窗口 demo，同一份界面支持系统应用和普通应用。
没有 Flow、Store、Reducer、Intent/Effect 分发或业务协程。按钮直接调用
`SystemUiController` 的方法，Compose 用 `mutableStateOf` 更新界面；设备状态用
`BroadcastReceiver` 和 `ContentObserver` 回调更新。

## 两套构建

在 Android Studio 的 **Build Variants** 中选择 `demoDebug` 或 `systemDebug`。

| | 普通应用 `demo` | 系统应用 `system` |
| --- | --- | --- |
| 包名 | `com.example.simplesystemui.demo` | `com.example.simplesystemui` |
| 最低版本 | Android 8 / API 26 | Android 16 / API 36 |
| 窗口 type | 三个窗口都用 `TYPE_APPLICATION_OVERLAY` (2038) | 状态栏 2000、导航栏 2019、通知面板 2040 |
| Manifest | `app/src/demo/AndroidManifest.xml` | `app/src/system/AndroidManifest.xml` |
| 身份 / 权限 | 普通 UID，用户授权悬浮窗 | `android.uid.system`，系统签名权限 |
| 签名 | 调试签名 | 设备平台签名；未配置时生成未签名 APK |
| Insets | 普通悬浮窗 | 状态栏、导航栏向 WMS 提供 Insets |

共用的应用、启动 Activity 和前台服务声明放在 `app/src/main/AndroidManifest.xml`。
构建时自动合并对应 flavor 的 Manifest，`BuildConfig.SYSTEM_UI` 自动选择代码路径，
无需手动修改窗口 type 或注释 `sharedUserId`。

```powershell
.\gradlew.bat :app:assembleDemoDebug :app:assembleSystemDebug
```

需要 JDK 17 和 Android SDK 36；Android Studio 的 Gradle JDK 也请选择 17。APK 分别输出到：

- `app/build/outputs/apk/demo/debug/app-demo-debug.apk`
- `app/build/outputs/apk/system/debug/app-system-debug-unsigned.apk`（未配置平台签名时）
- `app/build/outputs/apk/system/debug/app-system-debug.apk`（配置平台签名时）

## 普通应用演示

1. 安装 `demoDebug` APK，打开控制台。
2. 点击“授权悬浮窗”，在系统设置允许显示在其它应用上层，再返回控制台。
3. 点击“显示窗口”，可打开快捷面板、控制导航栏或关闭控制台查看跨应用显示。
4. 在控制台或服务通知点击“停止”移除全部窗口。

普通版不会替换设备系统栏，也不会改变其它应用的系统栏 Insets。
主页按钮打开桌面；返回和最近任务按钮提示使用设备系统导航键。
Wi-Fi 按钮打开系统 Wi-Fi 设置，亮度调整需要“授权修改系统设置”。
部分 TV ROM 不提供悬浮窗授权入口或不允许悬浮窗，需设备本身支持。

## 系统应用演示

1. 将 `platform-signing.properties.example` 复制为 `platform-signing.properties`，
   填入目标设备的平台 keystore 信息。实际签名配置已加入 `.gitignore`。
2. 选择 `systemDebug` 构建，按目标 ROM 的系统应用流程部署。
3. 确认应用获得系统 UID、`STATUS_BAR_SERVICE`、`INTERNAL_SYSTEM_WINDOW`、
   `INJECT_EVENTS` 权限，再通过控制台启动窗口服务。

系统版不申请悬浮窗权限。系统 UID 需要与目标 ROM 匹配的平台证书，仅把 APK
放入系统目录不够。未配置密钥时可以编译，但未签名 APK 不能直接安装。
普通版始终使用调试签名，不受平台签名配置影响，两版包名不同可以共存。

原生状态栏、导航栏、通知面板在每个显示器上各只能有一个。需要在 ROM 中安排
原有 SystemUI 释放这些窗口或集成替换；现有 SystemUI 仍占用时会创建失败。
参见 [AOSP Android 16 DisplayPolicy](https://github.com/aosp-mirror/platform_frameworks_base/blob/android16-release/services/core/java/com/android/server/wm/DisplayPolicy.java)。
窗口 type 与 Insets 桥接以 Android 16 为准，厂商 ROM 的隐藏 API 可能需要适配。

## 主要代码

- `MainActivity.kt`：启动/停止服务和权限入口。
- `SystemUIService.kt`：前台通知、窗口与回调的创建/清理。
- `SystemUiController.kt`：按钮直接调用的简单方法和 Compose 状态。
- `data/DeviceRepository.kt`：时间、电量、Wi-Fi、亮度的系统回调。
- `window/SystemWindowHost.kt`：`addView`、显示/隐藏面板、切换按键焦点。
- `platform/SystemWindowTypes.kt`：两种窗口 type。
- `platform/PlatformWindowApi.kt`：系统版 Insets 隐藏 API 桥接。

窗口默认不抢按键。点击“控制导航栏”后可以用方向键操作；返回或“释放焦点”
把按键交还前台应用。导航操作会等待窗口释放焦点后执行。

本次构建与验证结果见 `BUILD_VALIDATION.md`。
