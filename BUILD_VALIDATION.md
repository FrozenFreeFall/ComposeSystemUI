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
