# 简化版构建与验证

日期：2026-10-13。

本次改动移除 MVI / Flow / 业务协程，新增 `demo`、`system` 两个构建版本。
旧 Store / Reducer 和其单元测试已经删除，旧 APK 与测试记录不代表当前源码。

## 构建结果

- 环境：Gradle 8.11.1、AGP 8.10.1、JBR 17.0.14、Android SDK 36。
- 最终执行 `:app:assembleDemoDebug :app:assembleSystemDebug :app:lintDemoDebug :app:lintSystemDebug --offline --no-daemon`，退出码 0。
- 普通版 lint：0 errors、11 warnings；系统版 lint：0 errors、20 warnings。
- 剩余警告主要涉及依赖更新、系统版隐藏 API / shared UID，以及两版共用源码中的版本判断与既有资源。
- 业务源码无 Flow、MVI、`kotlinx.coroutines` 或 `suspend` 方法；Compose 焦点设置保留 `LaunchedEffect`。
- `git diff --check` 通过。旧 MVI 测试已随旧架构删除，未将其计为本次通过的测试。

## 最终 APK 核对

使用 SDK 的 `aapt2 dump xmltree` 检查最终 APK 内的 Manifest，并用 `apksigner verify` 核对签名。

| | 普通版 | 系统版 |
| --- | --- | --- |
| APK | `app/build/outputs/apk/demo/debug/app-demo-debug.apk` | `app/build/outputs/apk/system/debug/app-system-debug-unsigned.apk` |
| 包名 | `com.example.simplesystemui.demo` | `com.example.simplesystemui` |
| minSdk / targetSdk | 26 / 36 | 36 / 36 |
| sharedUserId | 无 | `android.uid.system` |
| 悬浮窗权限 | 有 | 无 |
| STATUS_BAR_SERVICE / INTERNAL_SYSTEM_WINDOW / INJECT_EVENTS | 无 | 有 |
| 服务 | 不导出，无系统调用权限限制，`specialUse` | 导出，受 `STATUS_BAR_SERVICE` 保护，`specialUse` |
| 签名 | 签名验证成功，Android Debug 证书 | 未配置平台密钥，预期为未签名；不能直接安装 |

## 本机验证环境

本机 JBR 17 的 Windows NIO Unix-domain pipe 在 Gradle 启动阶段出现
`Unable to establish loopback connection`。验证时仅为当前构建进程及其子进程设置
`JAVA_TOOL_OPTIONS=-Djdk.net.unixdomain.tmpdir=<项目绝对路径>/gradle.properties`，
使 pipe 回退到 TCP，并用 `-Pkotlin.compiler.execution.strategy=in-process` 编译。
构建结束后恢复原环境，没有修改全局 Java 配置或项目默认 JVM 参数。
lint 工具首次不在离线缓存中；联网补齐后，最终完整检查以离线方式通过。

## 设备验证范围

未执行真实设备窗口、遥控器、平台签名安装与厂商亮度验证；系统版还需要目标
ROM 的平台密钥和 SystemUI 集成环境。

## 状态栏下拉手势验证（2026-10-14）

新增状态栏下拉识别。状态栏持续处理整个触摸序列，面板用裁剪高度跟随手指；
超过触摸阈值才拦截，保留按钮轻点。松手按速度或一半高度判断展开/收起，
通过 `ValueAnimator` 补完动画。拖动预览不切换控制器的按键目标；取消、外部
焦点切换与窗口销毁会清理预览和动画。

- 使用已安装的 JDK 21，Java/Kotlin 编译目标仍为 17。本机 Android Studio JDK 25
  与当前 Gradle/Kotlin 不兼容；仅为构建进程选择 JDK 21。
- 离线执行两版 `assemble` 与 `lint`，使用 `--no-daemon`、
  `-Pkotlin.compiler.execution.strategy=in-process` 及上文的 pipe 回退参数。
- 最终构建退出码 0；两版 lint 均为 0 errors，普通版 11 warnings、系统版
  21 warnings。`git diff --check` 通过。
- 在 Android 15 模拟器 `emulator-5558` 安装普通版 APK，验证顶部空白区下拉跟手、
  未过一半停住后松手收起、超过一半展开、快速短下滑展开、按钮轻点打开、
  返回关闭、顶部横滑不展开，以及切到桌面后仍能下拉打开。
- 拖动期间通过窗口信息确认焦点仍在原 Activity；完整展开后焦点进入面板，
  返回关闭后交还原应用。截屏和 UI 树保存在 `app/build/gesture-validation/`。
- 系统版已构建，但本次模拟器为 Android 15，未验证 Android 16 原生系统窗口。

## 快捷面板上滑收起验证（2026-10-14）

上下滑动共用 `VerticalSwipeHandler`。上滑收起时保留面板的触摸和按键焦点，
动画结束后再关闭；短距离停住后松手恢复展开，快速上滑或收起超过一半关闭，
`ACTION_CANCEL` 恢复原来的展开状态。

内容区在按下时决定是否优先滚动，本次手势不会在滚动途中变成关闭；
内容触底后的下一次上滑可收起。固定 48dp 提示区放在 64dp 导航栏区域上方，
面板两侧空白区也可上滑收起。

- Android 15 普通版模拟器验证了底部上滑跟手、快速收起、短距离恢复、超过一半
  收起、取消恢复，以及内容先滚动、触底后下一次手势收起。
  最终 APK 还验证了侧边空白区上滑收起，以及关闭按钮仍正常工作。
- 回归验证状态栏下拉仍可打开面板。截图保存在 `app/build/gesture-validation/`
  的 `up-panel.png`、`up-dragging.png`。
- 两版构建与 lint 通过，系统版原生窗口仍需 Android 16 目标 ROM 验证。
