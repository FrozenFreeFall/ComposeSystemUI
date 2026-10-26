# 简化版构建与验证

## CommandQueue 系统栏命令验证（2026-10-26）

- 新增系统版 `IStatusBar` Binder 注册与快照恢复，主线程分发图标、disable、外观区域、
  窗口状态、临时栏、IME、面板和最近任务命令；普通版使用空桥接。
- 核对 Android 16 官方 AIDL / Parcelable，支持 `disableForAllDisplays` 的
  `DisableStates.displaysWithStates`，使用 ROM 的 transaction ID 和 Parcelable Creator。
- 状态栏 / 导航栏 Insets provider 根视图保留，由 WMS 控制 surface；逻辑可见性使用
  WMS windowState，应用 requestedVisibleTypes 不覆盖 IME / 分屏强制显示。
- 临时栏使用无障碍推荐超时调用 `IWindowManager.hideTransientBars(0)`，清理本地
  transient 状态；面板、导航焦点、触摸按住期间暂停，交互后重新计时。
- JDK 21、Gradle 8.11.1、SDK 36；沿用 pipe 回退与进程内 Kotlin 编译。最终离线执行：

  ```powershell
  .\gradlew.bat :app:testDemoDebugUnitTest :app:testSystemDebugUnitTest :app:assembleDemoDebug :app:assembleSystemDebug :app:lintDemoDebug :app:lintSystemDebug --offline --no-daemon '-Pkotlin.compiler.execution.strategy=in-process'
  ```

- 最终退出码 0。普通版 10 项测试、系统版 16 项测试均通过，0 failures / errors / skipped。
  两版 assemble 成功；lint 普通版 0 errors / 18 warnings，系统版 0 errors / 40 warnings。
  警告包含现有资源和依赖提示，以及系统版有意使用的非 SDK 接口反射。
- 状态策略单测覆盖 flag 隔离、分区图标明暗、临时栏、WMS 强制显示与 IME 返回；
  控制器测试覆盖入口禁用、关闭面板、焦点释放和不打断拖动的外观更新。
  系统版 Binder 测试使用真实 Parcel，覆盖主线程排队、默认屏过滤、close 丢弃任务、
  命令参数顺序、自动隐藏服务调用及长按暂停。
- Robolectric 使用 API 35 的框架测试环境；Android 16 特有的 `DisableStates` 和
  注册快照字段按官方源码核对，尚未在 API 36 真机运行。完整注册、实际 WMS surface / Insets、
  厂商图标资源、IME 切换与沉浸模式仍需目标 ROM、匹配平台签名及系统权限验证。
- 构建日志与下载的官方参考源码保存在 `app/build/command-queue-validation/`。

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

## InputMonitor 系统全局返回验证（2026-10-14）

系统版新增真实 `InputMonitor` / `InputEventReceiver` 输入通道。按下时只观察，
左侧单指向右横滑超过 touchSlop 后才 `pilferPointers()`；56dp 松手提交返回。
每个事件在 finally 中确认处理完成，返回动作在确认之后执行。停止或重建时按
receiver → monitor 顺序释放。普通版保留原来的窄输入窗，两版共用识别逻辑。

- `hidden-api-stubs` 仅通过 `systemCompileOnly` 引用；普通版没有隐藏输入类型依赖。
  `MONITOR_INPUT` 仅在系统 Manifest 声明，启动时检查授权。
- 工作区同时有最近任务代码更新：首次构建遇到该部分缺类，重试时编译通过，但
  并行打包出现 APK ZIP 条目冲突。没有删除或回退这些改动。
- 最终将当前全部源码复制到 `app/build/input-monitor-validation/project/` 独立构建，
  两版 `assemble` / `lint` 退出码 0。JDK 21，Java/Kotlin 目标 17，SDK 36，离线、
  `--no-daemon` 和 `-Pkotlin.compiler.execution.strategy=in-process`，沿用 pipe 回退参数。
  普通版 lint：0 errors、15 warnings；系统版：0 errors、30 warnings。
- 解析最终两 APK 的 DEX 类定义、类型和方法引用，并检查合并 Manifest，12 项全部通过：
  两 APK 均不包含三个隐藏框架类；普通版没有这些类型引用；系统版有真实 receiver 子类，
  以及 `getInputChannel`、`pilferPointers`、`finishInputEvent`、`dispose` 引用。
  结果保存在 `app/build/input-monitor-validation/apk-report.json`。
- Android 15 模拟器安装最终普通版 APK，验证原窄输入窗、拖动箭头、取消隐藏、短滑、
  回拉、纵滑不返回，长横滑关闭快捷面板且不再次返回底层 Activity。状态栏下拉和
  面板上滑回归通过。测试结束停止窗口服务，未修改权限设置；脚本、截图位于上述验证目录。
- `git diff --check` 通过。未在本次模拟器执行系统版输入监视器；模拟器为 API 35，
  系统版仍需 API 36 目标 ROM、匹配的平台签名、系统 UID 和系统权限验证。

## 最近任务验证（2026-10-14）

补齐 `RecentTask`、`RecentTasksRepository`，系统版通过现有面板窗口显示最近任务，
支持后台加载、任务快照、点击恢复、上滑/按钮移除与刷新。快捷面板与最近任务互斥，
返回关闭面板；切回任务前等待释放窗口焦点。普通版使用已有无障碍服务的
`GLOBAL_ACTION_RECENTS` 打开设备原生界面。

- 核对 Android 16 AOSP 任务服务：`getTaskSnapshot(taskId, lowResolution)` 使用两参
  签名；恢复任务仅接受 0..99 成功码；工作资料按任务所属用户加载名称和图标。
  硬件快照复制为软件 Bitmap 后关闭 HardwareBuffer，临时硬件 Bitmap 在 finally 释放。
- 在当前工作区离线执行两版 `assemble` 与 `lint`，沿用 JDK 21、pipe 回退和进程内
  Kotlin 编译参数。最终退出码 0；普通版 0 errors、16 warnings，系统版
  0 errors、31 warnings。新增警告包括系统版专用任务桥接中的隐藏 API 反射提示。
- 在 Android 15 模拟器安装普通版，点击控制台“最近任务”已进入服务和控制器。
  当时全局导航服务未连接，验证到“请在控制台启用全局导航服务”提示；未将此次
  检查计为成功显示原生最近任务。没有修改无障碍权限设置。
- `git diff --check` 通过。系统版任务列表、快照、恢复与移除的运行时验证仍需
  API 36 目标 ROM、匹配的平台签名、系统 UID 及任务管理权限。
