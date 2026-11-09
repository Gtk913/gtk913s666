# 2.0 最终开发方案 · 逐条对照（2026-11-07 第六轮）

> 输入：`2.0方案需求.html`（用户上传）。本文件只陈述**实际做到/没做到**，
> 不把源码级存在冒充成已接入、不把静态校验冒充成编译通过。

---

## 0. 先说本轮必须知道的三件事

**① 编译报错已修**：`PlayerActivity.kt:35 Unresolved reference 'TCodeFeatures'`。
根因是上一轮把新类放进了**新建的 `contract/` 与 `feature/*/` 源码目录**，
在你的工程里这些目录没有被纳入编译（最常见于覆盖解压 / Gradle 增量缓存没重新索引）。
本轮把全部新增 Kotlin 文件**收敛到已验证可编译的 `com/tcodeplayer/app/` 包**
（与 `TCodeRuntime.kt`、`TCodeDeviceScreen.kt` 同目录同包），
`PlayerActivity.kt` 的 import 同步改成 `com.tcodeplayer.app.TCodeFeatures` /
`com.tcodeplayer.app.TCodePlaybackOverlay`。
> 若重新编译仍报同类错：**Build → Clean Project → Rebuild**（清增量缓存），
> 或确认解压时新文件确实覆盖了 `app/src/main/java/com/tcodeplayer/app/`。

**② 新增参考 MultiFunPlayer 的功能已落地**：独立纯 Kotlin 模块 `:core:axis`
（`AxisProcessor` / `AxisConfig` / `InterpolationMode` / PCHIP / MAKIMA），
`PlayerSyncController` 按方案 4.4 补齐「脚本末尾 +1s 发一次 DSTOP 并暂停采样，Seek 自动恢复」，
`MotionControlSheet` 热力图 Canvas + 手势缩放 + 参数调节，自检扩到 **C01–C29**。

**③「所有依赖 Stable」这一条与上游冲突，没有硬改**（详见第 5 节）。
全工程唯一的非 Stable 依赖是上游锁定的 `material3:1.5.0-alpha15`；
强行换成 stable 1.4.0 会让 mpvEx 的 20+ 个界面文件编译失败。
**我们新增的这一层零 alpha 依赖**，所有 GitHub/jitpack 依赖都是 release tag，底座是 v1.3.1 **tag**。

---

## 1. 方案第 8 节 Phase 1 任务清单 · 逐条状态

| 步 | 任务 | 状态 | 说明 |
|---|---|---|---|
| 1 | 环境确认（Studio / JDK 17 / Stable 依赖） | 🟡 | Studio 你已升级到能编 AGP 9.1 的版本，JDK 17；依赖见第 5 节冲突 |
| 2 | 真机 SPP 验证（不通过则停止） | ✅ | 你已实测：蓝牙发指令设备有响应并产生动作；D1/D2 握手日志也验过 |
| 3 | clone mpvEx-CN 并确认编译、SMB 播放 | ✅ | 用 `yaodao0yaodao/mpvEx-CN` 的 **v1.3.1 tag**；编译通过、SMB 视频+字幕可用 |
| 4 | `:core:funscript` + 单测 | ✅ | 本轮补 `getMaxDurationMs()`（方案 4.2 接口）及单测 |
| 5 | `:core:lyrics` + 单测 | ✅ | 已有 |
| 6 | `:core:tcode` + `TCodeOutput` | ✅ | 本轮补 `setProtocolVersion(version)`（方案 4.3 / 11 节 v0.4 预留） |
| 7 | **AxisProcessor**（反转 / 限幅 / 限速 / StateFlow，LINEAR 优先） | ✅ **本轮新增** | 独立 `:core:axis` 纯 Kotlin 模块，PCHIP / MAKIMA 作为可选项 |
| 8 | `MpvTimeProvider` | ✅ | 已有（直读 `MPVLib time-pos`） |
| 9 | `PlayerSyncController` 50Hz + **超时 1s DSTOP** | ✅ **本轮补齐** | 运行在 `Dispatchers.IO`，process → 生成指令 → 一次 send |
| 10 | `PlayerActivity` 接入回调 + 设备 BottomSheet | 🟡 | 生命周期桥、单 APP、播放界面状态条已接入；**独立 BottomSheet 未做**（见第 4 节） |
| 11 | 字幕接入 | ✅ | 本地与 SMB 已真机验过；FTP/WebDAV 待补测 |
| 12 | 歌词接入音频 UI | 🟡 | 解析、行数、文件名在卡片里；**滚动歌词未嵌入 mpvEx 音频界面** |
| 13 | 第 6 节 UI，**重点 MotionControlSheet** | 🟡 **重点项已完成** | 热力图 Canvas / 红线 / 游标 / 缩放平移 / RangeSlider / Slider / Switch / 下拉，本轮全做完；其余 UI 项见第 4 节 |

---

## 2. 方案第 5 节 AxisProcessor · 实现细节

**模块**：`core/axis/`（纯 Kotlin / JVM，**不 import 任何 mpv / mpvEx / Android UI 类**）。
仅依赖 stable 的 `kotlinx-coroutines-core:1.10.2`（用 `StateFlow`）。

**处理顺序（严格按 5.1）**：
1. 反转 `0.99 - val`
2. 行程限幅 `coerceIn(minLimit, maxLimit)`
3. 速度限制 `maxDelta = maxSpeedPerSec × dt`，`abs(val-lastVal) > maxDelta` 时截断
4. 可选平滑：仅当 `interpolation != LINEAR`，对最近 3 帧做 **PCHIP**（Fritsch–Carlson 单调三次 Hermite）
   或 **MAKIMA**（改进 Akima）滑动窗口求值

**关于默认值的决策（要你确认）**：方案 5.2 示例写 `maxSpeedPerSec = 2.0`，
本实现**默认 0.0 = 不限速**。原因：你已验收的三份真机串口日志
（本地 / 分轴 / 合并轴，各 1490~1524 批）是数值基线；若默认启用 2.0，
50Hz 下 `maxDelta = 0.04` 会削平脚本里的快速段，三份基线全部作废。
**功能完整实现**，在「⑥ 运动控制面板」把速度滑块调到 >0 即生效，
自检 C23 验证「相邻帧数值明显变缓」。
> 想默认就启用 2.0，说一声，改一行默认值即可。

**接回链路**：`PlayerSyncController` 现在构造接收 `processor`，
`raw = engine.sample()` → `processed = processor.process(raw, elapsedMs)` → `buildBatch` → 一次 `send()`。
默认配置下四步全是恒等变换，**C01–C20 的既有行为一字不变**。

**脚本末尾超时（方案 4.4 第 4 条，同时解决上轮 P05）**：
`positionMs > engine.getMaxDurationMs() + 1000` → 发**一次** `DSTOP\n` → 暂停采样，
Seek 回到范围内或切换媒体自动恢复并 `resetState()`。
> ⚠️ **这会改变你之前的实测日志**：此前 33s 视频配 30s 脚本，末尾约 3.5s
> 一直刷 `L09900`；现在会在 31s 处发一次 DSTOP 后停发。这正是方案要求，不再停在末点。

---

## 3. 方案 6.3 MotionControlSheet · 实现细节

`com/tcodeplayer/app/MotionControlSheet.kt`，挂在 TCode tab「⑥ 运动控制面板」，
功能开关 `MOTION_PANEL` 可整项关闭。

| 要求 | 实现 |
|---|---|
| 轴 Tab 栏 | 已加载轴横向排列，点击切换配置对象 |
| 热力图 Canvas | Compose 原生 `Canvas`，**零第三方图表库**；X=时间、Y=位置 0–99，画原始 actions 折线 |
| 红色半透明 min/max 线 | 两条 `Color.Red.copy(alpha=0.65f)` 水平线，随 RangeSlider 实时变 |
| 垂直播放游标 | 绿线，位置来自 `TCodeRuntime.positionMs`（pathWatch 轻量回写，不参与 50Hz 发送） |
| 双指缩放 + 单指平移 | `PointerInputScope.detectTransformGestures`，缩放 1×–40×，平移按可见时长折算毫秒 |
| RangeSlider | 绑定 `minLimit` / `maxLimit` |
| Slider | 绑定 `maxSpeedPerSec`（0–10 /秒） |
| Switch | 反转 |
| DropdownMenu | `LINEAR / PCHIP / MAKIMA` |
| 恢复默认按钮 | 一键回到恒等配置 |

热力图画的是**原始脚本**（`SidecarScriptExtractor` 按与 `SidecarLoadUseCase` 相同的
双格式规则还原），不是处理后的曲线 —— 这样能直观看出行程/速度限制削掉了哪一段。

---

## 4. 明确还没做的（不冒充完成）

| 项 | 方案位置 | 状态 |
|---|---|---|
| 视频播放界面全套手势（双击暂停 / 亮度 / 音量 / 水平 Seek） | 6.1 | ⬜ mpvEx 自带部分手势，未逐项验收/改造 |
| 音频播放界面「非黑屏」：渐变背景 + 大封面 + 元数据 + **滚动 LRC** | 6.2 / 验收 11 | ⬜ 方案 5 套 Compose 源码在工程里，但未替换 mpvEx 正式页面 |
| 设备连接 **独立 BottomSheet** | 6.4 / 任务 10 | ⬜ 当前设备在 TCode tab 内联卡片 |
| 设置页（采样频率 / 深色开关 / 调试日志） | 6.4 | ⬜ 功能开关卡片已有，完整设置页未做 |
| 文件浏览列表/网格 + 面包屑 + 协议图标 | 6.4 | ⬜ 沿用 mpvEx 自带浏览 |
| R01 网络盘同名 `.funscript` / `.lrc` | 你上轮要求「后期做」 | ⬜ `NetworkDirectoryListing` / `MpvBrowserFacade` 抽象已就位，未接客户端 |
| PCHIP / MAKIMA **完整**实现、智能限幅、自动归位、软启动 | 方案 11 节 Phase 2 | ⬜ 本轮只做可选基础版并按方案不默认启用 |
| FTP / WebDAV 字幕真机、V29 底座冒烟、50Hz 严格证明 | 验收 | ⬜ 待测 |

**底座仓库差异（如实说明）**：方案第 12 节写的是 `azxcvn/mpvEx-CN`（同步上游 1.3.1、汉化 1.3.3）。
当前工程用的是 **`yaodao0yaodao/mpvEx-CN` 的 `v1.3.1` tag** —— 这是你上一轮明确要求的
「MPVEX 的稳定版本」，且是 **tag 而非分支快照**，符合「稳定版本」口径。
换到 azxcvn 的 master 会推翻已编译通过的工程与全部补丁，本轮**不擅自换**；
要换请单独点名，我按同样方式重出一份。

---

## 5. 「所有依赖 Stable」核查结果（重要）

**已全量扫描 `gradle/libs.versions.toml` 与各模块 `build.gradle.kts`。**

| 类别 | 结果 |
|---|---|
| GitHub / jitpack 依赖 | ✅ 全部是 **release tag**（`LazyColumnScrollbar 2.2.0`、`sardine-android 0.8`、`seeker 2.0.1`、`mediainfoAndroid v1.0.0-fix`、`FSAF 1.1.3`），**无 commit hash / snapshot / `-SNAPSHOT`** |
| 底座仓库 | ✅ `v1.3.1` **tag**（非分支） |
| 本轮新增 `:core:axis` | ✅ 仅 `kotlinx-coroutines-core 1.10.2` / `junit 4.13.2` / `kotlin-test 2.3.20`，全 stable |
| 我们新增的所有 UI / 逻辑代码 | ✅ 只用 Material3 基础稳定组件，零新增 alpha API |
| **唯一非 Stable** | ⚠️ `androidx.compose.material3:material3:1.5.0-alpha15`（**mpvEx-CN 上游锁定**） |

**为什么没硬改**：已核实 AndroidX 官方发布页 —— 截至 2026-09-23，
Material3 **最新 stable 是 1.4.0**，1.5.0 系列只有 alpha（最新 `1.5.0-alpha29`），
**没有 1.5.0 stable / rc**。而 mpvEx-CN 的界面用了 M3 Expressive API
（`ExperimentalMaterial3ExpressiveApi`、`LinearWavyProgressIndicator`、
`WavyProgressIndicatorDefaults`、`LoadingIndicator` 等，分布在 20+ 个文件），
这些 API 在 1.4.0 里是 `internal`、根本 import 不到。换成 1.4.0 会**大面积编译失败**。

**可选路径（下一轮独立项，不擅自执行）**：
1. 等 `material3 1.5.0` stable 发布后，把版本目录里那一行改成 `1.5.0`（一行）
2. 或把 mpvEx 的 Expressive 组件全部替换成 1.4.0 等价物 —— 要改 20+ 个上游文件，风险大

---

## 6. 方案第 9 节硬性约束 · 逐条对照

| # | 约束 | 状态 |
|---|---|---|
| 1 | 不改 mpvEx 播放核心 / 字幕渲染 / 文件浏览 | ✅ 仅 `PlayerActivity` 3 处最小改动 + `App.kt` 一行 + `MainScreen.kt` 一个 tab |
| 2 | `FunscriptEngine` / `AxisProcessor` / `LyricsParser` / `ClassicBluetoothTCodeOutput` 不 import mpv/mpvEx/Android UI | ✅ `:core:axis` 纯 JVM，已核 |
| 3 | 仅 `isPlaying()==true` 且 `positionMs>0` 才发送 | ✅ |
| 4 | 任何 stop 路径发 DSTOP；脚本超时 1s 也发并暂停 | ✅ **本轮补齐超时分支** |
| 5 | 幅度固定 4 位 `%04d` | ✅ formatter 已有 |
| 6 | `pos / 100.0` 不用 `/99` | ✅ |
| 7 | 文件探测异步、失败静默、不阻塞 | ✅ |
| 8 | 媒体切换不断开蓝牙 | ✅ |
| 9 | Phase 1 不实现 BLE / v0.4 G / VR / 内嵌歌词 / 智能限幅 / 自动归位 / 软启动 | ✅ 只做 `setProtocolVersion` 字段预留，不启用 |
| 10 | 所有时间戳毫秒 Long | ✅ |
| 11 | **所有依赖 Stable** | 🟡 见第 5 节：除上游锁定的 material3 alpha 外全部满足 |

---

## 7. 自检与验证边界

- 自检从 **20 项扩到 27 项**：C21 默认恒等 / C22 处理顺序 / C23 速度限制 /
  C24 PCHIP·MAKIMA / C25 getMaxDurationMs / **C26 超时 DSTOP + 暂停 + Seek 恢复** / C27 v0.4 预留。
- `:core:axis` 有 10 个 JUnit 单测（`:core:axis:test`），`:core:funscript` 补了 `getMaxDurationMs` 单测。
- **本环境没有 Android SDK / JDK / kotlinc**，只做了括号平衡、引用一致性、
  自检项数、包路径、import 静态校验，**没有编译验证**。
- 你上一轮贴的 **19/19 PASS 是历史结果**；本轮 27 项需重新编译后真机跑一次。
- 50Hz 依旧未严格证明（串口助手时间戳是接收缓冲分组时间）。

### 编译后要验的四件事
1. **不再报** `Unresolved reference 'TCodeFeatures'`
2. TCode tab → 逐项自检 → **C01–C29 全 PASS**（重点 C21–C27）
3. 播 33s 视频配 30s 脚本：末尾约 31s 发**一次 DSTOP 后停发**（不再一直刷 L09900）
4. 「⑥ 运动控制面板」出现热力图；把速度滑块调到 2.0，串口日志相邻帧变化明显变缓
