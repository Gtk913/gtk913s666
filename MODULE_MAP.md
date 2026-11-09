# TCode Player · 模块地图与本轮改动说明

> 版本：mpvEx **`v1.3.1` 稳定版** 底座 + TCode 整合层（2026-11-07 第五轮）
> 本轮按你的要求：**先解决现有问题 P01–P04，并把串口参数（重点波特率）显示出来**。
> 括号内建议（参考 MultiFunPlayer、模块化）已评估，结论见第二节。

---

## 一、本轮改了什么

| 编号 | 问题 | 本轮处理 | 状态 |
|---|---|---|---|
| **P01** | 视频播完自动退出软件（疑似闪退） | **定位到根因**：mpvEx 自带设置 `closeAfterReachingEndOfVideo` 在 `handleEndOfFile()` 里调用 `finishAndRemoveTask()` —— 是**设置行为，不是崩溃、不是 TCode 桥**。新增开关 `KEEP_PLAYER_ON_EOF`（默认开）拦住这两处调用；同时把 `TCodeRuntime` 的进入/退出生命周期回调**每一步都 runCatching 兜底**（生命周期回调里抛异常会直接崩进程），并加「诊断」行记录最近一次进入/结束 | ✅ 已改（含防御性修复） |
| **P02** | 切 TCode tab 不方便、视频界面看不到状态 | 新增可折叠状态条 `TCodePlaybackOverlay`，挂在 `setupPlayerControls()` 的 `Box` 里。**PlayerControls 及播放逻辑一行未改**，只在最外层加一行调用；开关 `PLAYER_OVERLAY` 可整项关闭 | ✅ 已改 |
| **P03** | 自检页上下遮挡 | 重写 `FrameworkCheckScreen`：加 `statusBarsPadding()` / `navigationBarsPadding()`、LazyColumn `weight(1f)` + 底部 `contentPadding`、卡片标题独占一行、「测这项」移到下一行右侧 | ✅ 已改 |
| **P04** | 网络卡片字幕写「（无）」但实际可用 | 改成「（由播放器按同名规则自动匹配并加载）」；note 也写明「脚本/歌词探测暂未启用（R01 待开发）」，区分**我们探测层没列到目录**与**mpvEx 自己匹配上了** | ✅ 已改 |
| **新需求** | 串口内容要显示，尤其波特率（设备不一定是 115200） | 新增 `feature/serial/` 整套模块 + TCode tab「② 串口 / 链路参数」卡片，详见第三节 | ✅ 已改 |
| 自检 | — | 新增 **C20**（串口参数校验），自检从 19 项 → **20 项** | ✅ 已改 |

**R01（网络驱动器同名脚本加载）本轮没做**：你上轮说的是「后期也需要做进去」，按你定的「一次一项」节奏留到下一轮。工程里 `NetworkDirectoryListing` / `MpvBrowserFacade` 抽象早就在，下一轮接上 mpvEx 三种网络客户端的列目录能力即可，不用重写探测逻辑（见第六节）。

---

## 二、关于 MultiFunPlayer 的评估（你标注「仅建议」）

**结论：不引入它的代码，只借鉴它的分层边界。**

核实结果：MultiFunPlayer 是 **Windows / .NET 8 (C#) 桌面应用**，架构是
`MediaSource（VLC / MPV / DeoVR 等播放器适配）→ Script（解析 + pchip/makima 插值 + 限速）→
OutputTarget（Serial / TCP / UDP / WebSocket / Buttplug）` 的**外挂式**同步器：它靠播放器 IPC 拿播放位置，再往外部设备发指令 。

三条不移植的理由：

1. **平台不同**：C#/.NET 8 / WPF 插件体系，无法直接搬进 Kotlin + Compose 的 Android 工程，硬搬等于重写。
2. **架构方向相反**：你明确说「他是外挂 MPV，咱们要做的是整合」。我们直接读 `MPVLib time-pos`，**不需要 MediaSource IPC 适配层**，也不需要多播放器支持。
3. **会破坏已验收链路**：插值算法换成 pchip/makima 会改变所有已实测的幅度值（现有 3 份串口日志的数值证据全部作废），必须单独开一轮重新验收，不适合混在「解决现有问题」这一轮。

**借鉴的部分**：把它「输入 / 脚本处理 / 输出目标 / 可插拔扩展」的**边界**在我们工程里显式化，做成 `contract/TCodeFeatures.kt` 注册表 + `feature/*` 包，见下一节。它的高级算法列入「后期可选模块」（第六节），随时可以按模块加，不影响现有链路。

---

## 三、串口参数（重点：波特率）

### 先说清一个技术事实，不糊弄

**Android 经典蓝牙 SPP（RFCOMM）链路本身不开放波特率设置。** 手机 ↔ 蓝牙模块这一段走的是 RFCOMM 字节流；波特率只存在于**蓝牙模块 UART 侧（模块 ↔ 设备主控）**，由模块固件 / AT 指令配置。Android 的 `BluetoothSocket` 没有任何 `setBaudRate()` 之类的 API；SPP 规范里的 Remote Port Negotiation 命令 Android 也没暴露 。

所以本轮做的是：

- **显示 + 记录 + 按设备分别保存** 模块侧串口参数，让你能核对「设备到底是 9600 还是 115200」
- **不假装 APP 能改链路波特率**；卡片底部把这条边界明写出来
- 改动这些值**不会改变本 APP 的蓝牙发送行为**，也不会影响已验证的指令链路

你要的「端口号」也给了：当前工程是经典蓝牙 SPP，没有 TCP 端口号；卡片显示 `SPP UUID` 和 `RFCOMM 通道（自动 SDP 发现）`，并**预留** `networkHost:networkPort` 字段，标注「网络输出（TCP/UDP/WebSocket）未启用」。以后真加网络 TCode 设备时，这个字段直接能用。

### ② 串口 / 链路参数卡片内容

| 项 | 值 / 行为 |
|---|---|
| **波特率** | 大号醒目显示当前值；「常用值 ▾」展开 11 档（1200…921600）；也可直接输入自定义值（300 ~ 4,000,000），非法值不应用并红字提示 |
| 持久化 | 按 **MAC 地址**分别存（`SerialProfileStore`），换 HC-05 / TCodeESP32 模块不串配置；连接成功自动载入该设备的参数 |
| 链路类型 | 经典蓝牙 SPP / RFCOMM |
| SPP UUID | `00001101-0000-1000-8000-00805F9B34FB` |
| RFCOMM 通道 | 自动（SDP 发现） |
| 设备 / MAC | 已连接设备名与地址 |
| 数据帧 | 8 数据位 / 1 停止位 / 无校验（默认，可随 profile 存） |
| 行尾 | 显示 LF（`\n`），并注明 **TCode 协议固定 LF，此项仅供核对模块配置** |
| 端口号 | 预留字段，当前显示「—（网络输出未启用）」 |
| 技术说明 | 明写「蓝牙 SPP 不设波特率，此为模块 UART 侧参数，改动不影响本 APP 发送」 |

**C20 自检**验证：默认 115200、可改 9600、非法值 0 被拒、`withValidBaud()` 回落、按设备地址独立、链路与端口文案正确。

---

## 四、模块地图

```
TCodePlayerMPVEx/
├── app/                                  ← mpvEx v1.3.1 稳定版（播放核心 / 字幕 / 文件浏览 未改）
│   ├── src/main/java/app/marlboroadvance/mpvex/
│   │   ├── App.kt                        ← 只加一行 TCodeRuntime.install(this)
│   │   ├── ui/browser/MainScreen.kt     ← 只加一个底部导航项「TCode」
│   │   └── ui/player/PlayerActivity.kt  ← 本轮 3 处最小改动：
│   │                                      ① setupPlayerControls 包 Box + 状态条
│   │                                      ② / ③ handleEndOfFile 两处 EOF 关闭加开关
│   └── src/main/java/com/tcodeplayer/
│       ├── contract/
│       │   └── TCodeFeatures.kt         ← 【新】功能注册表：所有特性开关 + 定义清单
│       ├── runtime/
│       │   ├── TCodeRuntime.kt          ← 生命周期桥、状态、脚本探测、串口状态
│       │   └── PlayerActivityIntegration.kt
│       ├── core-bridge/                 ← DirectMpvFacade / player/*（时间源 ↔ 输出）
│       ├── bluetooth/DeviceHandshake.kt
│       ├── probe/                       ← DirectoryListing / MediaSidecarResolver /
│       │                                 SidecarLoadUseCase / SidecarSpec
│       ├── feature/                     ← 【可插拔功能层】
│       │   ├── serial/                  ← 【新】串口参数：模型 / 存储 / Compose 面板
│       │   ├── overlay/                 ← 【新】播放界面状态条（P02）
│       │   └── script/                  ← 已加载脚本面板（在 TCodeDeviceScreen 内）
│       ├── ui/framework/                ← C01–C20 自检（FrameworkCheck / Screen）
│       └── ui/{player,device,settings,browser,theme}/  ← 方案里的 5 套 Compose 屏（源码级存在，待逐屏接入）
├── core/funscript/                      ← 纯 JVM：模型 / JSON（含内嵌 axes）/ 线性插值
├── core/lyrics/                         ← 纯 JVM：LRC → 毫秒
├── core/tcode/                          ← Android library：SPP 输出 / D1 D2 / 指令格式化
└── MODULE_MAP.md                        ← 本文件
```

分层对照（借鉴 MultiFunPlayer 的边界，但按「整合」重排）：

| 层 | 我们工程 | 职责 |
|---|---|---|
| 时间源 | `player/MpvTimeProvider` + `DirectMpvFacade` | 直读 `MPVLib time-pos`（**不需要外挂 IPC**） |
| 脚本处理 | `core/funscript` + `probe/SidecarLoadUseCase` | 探测、双格式解析、插值、D2 轴过滤 |
| 输出目标 | `core/tcode` + `bluetooth/DeviceHandshake` | SPP 连接、D1/D2、批量发指令、DSTOP |
| 功能 / UI | `contract` + `feature/*` + `ui/*` | 可插拔的界面与特性 |

### 怎么增减一个功能（标准 4 步）

1. 在 `com.tcodeplayer.app/feature/<名字>/` 新建包，**只依赖 `contract` 和 `core/*`**，不碰 mpvEx 的类
2. 在 `TCodeFeatures` 加一个 `const val` key + 一条 `Definition`（标题 / 说明 / 默认开关）
3. 调用点用 `if (TCodeFeatures.isEnabled(KEY)) { ... }` 包一层；UI 里加一张卡片或一行
4. 在 `FrameworkCheck` 加一项 `Cxx` 自检验证它

关掉开关 → 该功能从**界面和运行时同时消失**，代码保留、随时开回来。「⑤ 功能开关」卡片里能直接操作。
物理删除整个模块时，记得移除 `PlayerActivity.kt` 里 `TCodeFeatures` 与 `TCodePlaybackOverlay` 的 import 和 3 处引用即可。

---

## 五、P01 的诚实边界

- 上面说的 `closeAfterReachingEndOfVideo` 是**从源码里读到的确切调用点**，不是猜测；但**没有 logcat 崩溃栈**，所以不能 100% 排除「还有第二个触发点」。
- 本轮做的是：拦住这两处自动关闭 + 生命周期回调全兜底 + 加诊断行。**没有拿到你的 logcat 去复现确认**，不能写成「已彻底修复闪退」。
- 若改完仍会退出：把 TCode tab 里「诊断：…」那一行和 mpvEx 设置里「播放结束后关闭」的状态发我，再定位下一处。
- **新副作用（已记为 P05）**：播放结束不再退出后，脚本时间轴走到末尾会一直保持最后一个 action 值（之前日志里是 `L09900 / L15000 / R05000`），设备会停在末点直到你主动停止发 DSTOP。不想这样就到「⑤ 功能开关」关掉 `播放结束保持界面`，恢复 mpvEx 原行为。**要不要改成「播完自动回中位 / 发 DSTOP」，等你确认，本轮不擅自改数值行为。**

---

## 六、后期可选模块（本轮一律不实现）

| 来源 | 模块 | 为什么不现在加 |
|---|---|---|
| MultiFunPlayer | pchip / makima / step 插值 | 会改变全部已实测幅度值，3 份串口日志的数值证据要重验 |
| MultiFunPlayer | 每轴速度限制 / 智能限速 / 自动归位 / 软启动 | 同上，且属于运动策略，不是「现有问题」 |
| MultiFunPlayer | 全局 / 每轴时间偏移 | 需要新的时间同步层与单独验收 |
| MultiFunPlayer | MotionProvider（随机 / 模式 / 自定义曲线） | 独立大模块 |
| 你的需求 | **R01 网络盘同名脚本** | 明确说「后期」；`NetworkDirectoryListing` + `MpvBrowserFacade` 抽象已就位，下一轮把 mpvEx 的 SMB/FTP/WebDAV 客户端接进 `MpvBrowserFacade` 即可，探测规则一行不用改 |
| 你的反馈 | 5 套 Compose 界面逐屏接入 / UI 精修 | 你说过「现不考虑 UI 问题」，单独排期 |

---

## 七、编译与验证边界

- **本轮改了 11 个文件、新增 5 个 Kotlin 文件，必须重新编译一次**（`Build → Generate App Bundles or APKs → Build APK(s)`）。
- 我这边**没有 Android SDK / JDK，无法编译验证**，只做了括号平衡、引用一致性、自检项数（20 项）等静态校验。
- 真机要验的三件事：
  1. TCode tab 出现「② 串口 / 链路参数」，波特率显示 115200、能改成 9600、重进不丢
  2. 自检 **C01–C20 全 PASS**（重点 C20）
  3. 播完一个视频**不再自动退出**；播放界面左上角有小状态条，点一下能展开
- 冒红字原样贴我，我只告诉你下一个按钮点哪。

> 引用依据：MultiFunPlayer 架构与设置项见其项目说明与设置指南 [citation:14][citation:15][citation:18]；
> Android RFCOMM/SPP 不开放波特率设置见 SPP/RFCOMM 资料 [citation:12][citation:13][citation:16]。


---

# 第六轮（2026-11-07）：2.0 方案接入 + 编译报错修复

## 1. 编译报错（最高优先，已修）

`PlayerActivity.kt:35 Unresolved reference 'TCodeFeatures'` —— 上一轮把新类放进
**新建的 `contract/` 与 `feature/*/` 源码目录**，该目录在用户工程里未被纳入编译。
本轮把 5 个新增文件全部收敛到 **`com/tcodeplayer/app/`**
（与 `TCodeRuntime.kt`、`TCodeDeviceScreen.kt` 同目录同包），
PlayerActivity 的 import 改成 `com.tcodeplayer.app.TCodeFeatures` /
`com.tcodeplayer.app.TCodePlaybackOverlay`。
功能开关注册表 `TCodeFeatures` 仍在，只是文件位置变了 —— **模块化语义不丢**。
若仍报同类错：`Build → Clean Project → Rebuild`。

## 2. 新增 `:core:axis` 模块（方案第 5 节 / 任务 7）

```
core/axis/
├── AxisConfig.kt        AxisConfig / InterpolationMode（LINEAR / PCHIP / MAKIMA）
├── Interpolation.kt     PCHIP（Fritsch–Carlson）与 MAKIMA（改进 Akima），纯 Kotlin 重写
├── AxisProcessor.kt     反转 → 限幅 → 限速 → 可选平滑；configs: StateFlow；resetState()
└── AxisProcessorTest.kt 10 个 JUnit 单测
```

接入 `PlayerSyncController`：`sample → process → buildBatch → 一次 send`。
默认配置是**恒等变换**（不限速 / 不反转 / 全行程 / LINEAR），
保证三份已验收真机日志的数值基线一字不变。

## 3. 方案 4.4 超时规则（同时解决 P05）

`positionMs > getMaxDurationMs() + 1000` → 发**一次** DSTOP → 暂停采样；
Seek 回范围内或切换媒体自动恢复 + `processor.resetState()`。
**行为变化**：33s 视频配 30s 脚本，末尾不再一直刷 `L09900`。

## 4. MotionControlSheet（方案 6.3 / 任务 13 重点）

`MotionControlSheet.kt`（同包）：轴 Tab、原生 Canvas 热力图、红色 min/max 线、
绿色播放游标、`detectTransformGestures` 缩放/平移、RangeSlider / Slider / Switch /
插值下拉、恢复默认。挂在 TCode tab「⑥」，受 `MOTION_PANEL` 开关控制。
热力图数据由 `SidecarScriptExtractor` 按双格式规则还原原始 actions。

## 5. 依赖 Stable 核查

见 `SPEC_2_0.md` 第 5 节。结论：全工程**唯一**非 Stable 是上游锁定的
`material3:1.5.0-alpha15`；官方最新 stable 为 `1.4.0`（2026-09-23），
1.5.0 系列只有 alpha（最高 alpha29），且 1.4.0 里 mpvEx 用到的
M3 Expressive API 是 internal —— **硬改会大面积编译失败**。
所有 GitHub/jitpack 依赖均为 release tag，底座为 `v1.3.1` tag，
本轮新增代码零 alpha 依赖。

## 6. 边界

- 方案 6.1 / 6.2 / 6.4 全套 UI（视频手势、音频非黑屏滚动歌词、独立设备 BottomSheet、
  完整设置页）**未做**；R01 网络盘同名脚本仍待下一轮。
- 方案第 12 节指定 `azxcvn/mpvEx-CN`（汉化 1.3.3）；本工程用
  `yaodao0yaodao/mpvEx-CN` 的 **v1.3.1 tag**（你上轮指定的稳定版本），未擅自换。
- 自检 20 → **27 项**；无 Android SDK，只静态校验，未编译验证。
