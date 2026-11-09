# TCode 联动 APP · 详细更新日志（CHANGELOG）

> 本文件随代码包一起交付，记录**每一个交付批次改了什么、为什么改、验证到哪一步**。
> 早期批次的文件清单依据各轮说明文档与当前工程源码中的改动标记整理；
> 从批次 14 起逐文件精确列出。底部附「编译 / 验证状态」与「已知未完成项」。

---

## 0. 工程基线（一直未换）

| 项 | 值 |
|---|---|
| 底座 | `yaodao0yaodao/mpvEx-CN` **v1.3.1 tag**（2026-08-20，稳定版） |
| 上游源码 | 全量保留，播放内核 / 字幕渲染 / 网络浏览器未推倒重来 |
| 包名 | `io.github.yaodao0yaodao.mpvex`（底座）+ `com.tcodeplayer.*`（本方代码） |
| 工具链 | AGP 9.1.0 / Gradle 9.4.1（腾讯云镜像）/ Kotlin 2.3.20 / compileSdk 36 / JDK 17 |
| 依赖 | 国内镜像（阿里云）；`usb-serial-for-android:3.9.0` 稳定 tag；自带 libmpv AAR |
| **未解决的工具链约束** | `material3:1.5.0-alpha15` 无 stable 可换（官方最新 stable 1.4.0 不含 M3 Expressive API），20+ 上游文件依赖它 |
| 形态 | 单 APP、单 LAUNCHER、底部 3 tab（主页 / 播放列表 / 联动） |
| 模块 | `:core:funscript` `:core:lyrics` `:core:tcode` `:core:axis` + `app` 内 `com.tcodeplayer.*` |

**不随批次变化的既有能力**：funscript 插值（`pos÷100`、clamp 0~0.99）、LRC 毫秒解析、四位短命令 `L07700`、SPP D1/D2 500ms×2 重试、异常 DSTOP、50Hz `PlayerSyncController`、同名探测（主名严格区分大小写 / 扩展名忽略）、合并轴单文件 `axes[]` + 多轴多文件双格式、D2 轴过滤、音频脚本与歌词并行、播放状态条、⑦ 手动调试、⑥ 运动控制（PCHIP / Makima / 限速 / 行程 / 反转 / 热力图）、逐项自检 C01–C29。

---

## 批次 01 · 策划案 Phase 1 源码工程
- **诉求**：按策划案做 APP。
- **交付**：42 个 Kotlin 源码文件 + 4 份文档 + 独立 `spp-verify/` 真机验证工程；`core:funscript` / `core:lyrics` / `core:tcode` 三模块 + Compose Material3 界面 + Koin。
- **验证**：`tools/spec_check.py` 31 项等价复算全过；真机 / Gradle 未执行（当时无 SDK）。
- **遗留**：策划案第 1 步 SPP 真机验证必须先在目标手机 + 经典蓝牙模块上通过。

## 批次 02 · 音频适配补齐
- **诉求**：音频文件也要适配 funscript、也要有脚本。
- **改动**：接线说明 5.1（音频若走独立 Activity 必须同样接 4 个生命周期调用）；验收第 15 项；新增 2 个单测；**修 bug**——忽略大小写匹配原返回候选名，在大小写敏感文件系统（Linux/SMB）会命中却读不到内容，改为返回目录里的实际文件名。
- **验证**：浏览器预览页新增 `song.mp3` 模式；规格自检通过。

## 批次 03 · 零设备一键自检
- **交付**：`TCodePlayer_一键自检.html`，自动跑完整流程出 17 项 PASS/FAIL。
- **结果**：17/17、50.0 条/秒（模拟时钟）、129 批、3 次 DSTOP。用户确认「上面测试没啥大问题」。

## 批次 04 · SPP 真机 PASS
- **用户实测**：手机蓝牙给设备发指令，设备有响应且产生实际动作。
- **改动**：`PHASE1_STATUS.md` 结论改 PASS、`ACCEPTANCE.md` 第 1 项勾选、README/INTEGRATION 前置条件改「蓝牙链路已排除」。

## 批次 05 · 编译喂饭步骤 + 国内镜像
- **改动**：`编译步骤_喂饭版.md`；`gradle-wrapper.properties` 改华为云 8.9、依赖改阿里云（原 `services.gradle.org` 国内超时）。

## 批次 06 · 独立「能装版」（后废弃，不在当前工程）
- Java + MediaPlayer 单文件 APP，能直接出 APK。**用户判定 UI 不合格、且偏离策划案**，后续回到 Kotlin/Compose/mpvEx 主线。此分支代码不在当前包。

## 批次 07 · 框架版 + 逐项自检入口
- **诉求**：先搭框架，再一项一项测。
- **交付**：策划案原结构（4 模块 / Kotlin / Compose M3 / Koin / mpvEx 适配层）；C01–C17 自检调用工程里真实的 Kotlin 类；`FRAMEWORK_CN.md` 11 步完成度矩阵。

## 批次 08 · mpvEx 中文版全量合并
- **诉求**：给出完整项目 APP，不许两个软件。
- **改动**：下载 `yaodao0yaodao/mpvEx-CN` 全量源码（592 文件）；只改 4 个文件（settings、app/build.gradle.kts、Manifest、App.kt）；`PlayerActivity.kt` 一行未动，靠 `ActivityLifecycleCallbacks` + MPVLib path 轮询接管；`DirectMpvFacade` 直连 `MPVLib`；Gradle 换腾讯云 9.4.1、依赖换阿里云。
- **遗留**：GitHub `git clone` 403，但 codeload / raw / API 可用（后经核实）。

## 批次 09 · 连续编译排错（5 处）
| 报错 | 修复 |
|---|---|
| `non-ASCII path` | 工程路径全英文 + `android.overridePathCheck` 双保险 |
| `plugin already on classpath with unknown version` | 子模块 plugins 块删显式 version，改版本目录 alias |
| `org.jetbrains.kotlin.android no longer required (AGP 9.0)` | `core/tcode` 删该插件 + `kotlin{}` 块 + `JvmTarget` import |
| `AGP 9.1.0 / Latest supported 8.7.2` | 用户升级 Android Studio（最终 Rabbit 2 / 2026.2.2 Patch 1） |
| `BuildConfig.java GIT_COUNT = fatal: ...` | Git helpers 判退出码 + 校验纯数字 + 无 `.git` 回退 0/unknown |
| `Unresolved reference 'TCodeFeatures'` ×2 次 | ① 新类收回 `com/tcodeplayer/app/` 已编译目录；② KDoc 里 `` `core/*` `` 触发 Kotlin 注释嵌套 → 全文件未闭合，改掉并补 `FrameworkCheck` 的 3 个跨包 import |

## 批次 10 · 收成单 APP
- **诉求**：两个图标不行，脚本界面也要有。
- **改动**：删 `TCodePanelActivity` 与独立自检 Activity（含 LAUNCHER）；`MainScreen` 底部加第 5 个 tab；`TCodeDeviceScreen` 新增「③ 已加载脚本」卡片，显示实际命中的文件名、生效轴、轴的来源、字幕/歌词、格式标签；网络媒体写明降级。

## 批次 11 · 稳定版底座 + 双格式脚本
- **诉求**：mpvEx 用稳定版；单文件内嵌 axes 与多轴多文件都要支持。
- **改动**：底座从 `1.3.2-test.2` 换 **v1.3.1 tag**；`FunscriptJson.parseMulti()` 支持 `version 1.1` 的 `axes[]`；顶层 actions→L0、axes[]→各轴；两种同时存在时**分文件覆盖同名轴**；自检扩到 C18/C19。
- **真机**：多轴多文件 1490 批、合并轴单文件 1524 批，三轴数值全部差 ≤1，各 1 次 DSTOP。

## 批次 12 · P01–P04 修复 + 模块化 + 串口参数
- **P01** 播完自动退出：根因是底座 `closeAfterReachEndOfVideo` 在 `handleEndOfFile()` 调 `finishAndRemoveTask()`；加 `KEEP_PLAYER_ON_EOF` 开关 + 生命周期回调 `runCatching` 兜底 + 诊断行。
- **P02** 播放界面状态条（`setupPlayerControls()` 外层加一行，播放逻辑不动）、**P03** 自检页遮挡（insets + `weight(1f)` + 底部留白）、**P04** 网络字幕文案。
- **模块化**：`TCodeFeatures` 注册表 + ⑤ 功能开关；评估后**不引入 MultiFunPlayer 代码**（Windows/.NET 外挂、默认 PCHIP 会削平已验收基线），只借鉴分层边界。
- **串口参数**：新增波特率面板；后续经核查「Android SPP 既设不了也读不到波特率」，**批次 17 整块删除**。

## 批次 13 · 2.0 方案（AxisProcessor / 热力图 / 超时 DSTOP）
- **改动**：`:core:axis` 纯 Kotlin 模块（反转→行程限幅→速度限制→平滑，PCHIP / Makima 可选，默认 LINEAR）；`PlayerSyncController` 补 `positionMs > 末尾+1000ms` 发一次 DSTOP 并暂停采样；`MotionControlSheet` 原生 Canvas 热力图 + 双指缩放；自检 C21–C27。
- **默认值决策**：`maxSpeedPerSec` 默认 **0（不限速）**，避免削平三份已验收串口基线；滑块调 >0 立即生效。
- **稳定版核查**：GitHub/jitpack 依赖均为 release tag，唯一非 Stable 是上游锁定的 `material3:1.5.0-alpha15`（无解，见 §0）。

## 批次 14 · B 路线增量 ①（send 真超时 + 退后台 DSTOP）
- **评估**：对 2.1 / 2.2 方案逐条复核，指出「IDE 锁 Ladybug 与 AGP 9.4 冲突」「Phase 1 清零重做 = 重复造轮子」「依赖 100% Stable 无解」。用户选 **B：保留现有工程取真增量，不换底座**。
- **改动**：源码级检查 323 个条目后；`send()` 改独立 daemon 写线程 + `Future.get(100ms)`（同步阻塞 write 无法被协程取消打断），超时/忙碌**丢帧且不抛给 controller**（否则会被当成断连）；`onActivityStopped` 补退后台 DSTOP + 防重复；功能开关「退后台停止驱动」兼容 PiP；自检 C28/C29（共 29 项）。
- **遗留**：②③（DeviceDebugSheet、WiFi）留后续；50Hz 仍未严格证明。

## 批次 15 · 自检入口与 UI 遮挡
- **改动**：自检按钮移到「联动」页顶部、页面加 `navigationBarsPadding()`、⑤ 开关真生效（原直接调普通函数不触发重组）。

## 批次 16 · 表格化验收 + 6 项修复
- **交付**：`TCode逐项测试填写表.xlsx`（结果列下拉 + 自动汇总）。
- **修复**：V36/V05 遮挡（悬浮导航条需 88dp 底部留白 + `statusBarsPadding()`）、V47 概率 FAIL（自检断言改「等条件 + 稳定窗口」）、V31 波特率持久化（`commit()` 同步 + 回退全局默认）、V38 开关不重组（`@Composable featureEnabled()`）、V10 本地 flac（`MediaStore.Audio.Media` + 按 path 去重补齐）、新增 ⑦ `DeviceDebugSheet`。

## 批次 17 · 输出层扩展 + 名称统一 + UI 简化
- **删除**：② 串口参数面板 / `SerialLinkPanel` / `SerialLinkProfile` / `SerialProfileStore`（蓝牙读不到也设不了波特率，无信息价值）；V31/V55 关闭。
- **③ WiFi TCP/UDP**：`OutputRegistry`（蓝牙/TCP/UDP/USB 四选一）+ `TcpTCodeOutput` / `UdpTCodeOutput`；`output` 从绑死 `val` 变可切换，切换时停旧驱动→换实现→重建 controller。
- **④ USB 有线串口**：`usb-serial-for-android:3.9.0`，`getDefaultProber()` 自带 `Ch34xDriver`，8N1 / 300~4,000,000 真实生效；首次弹系统 USB 授权。
- **② 名称与导航**：底部 3 tab（主页 / 播放列表 / **联动**，双向同步箭头）；主页 FAB 5 项（打开文件 / 打开链接 / **上次播放** / **历史播放** / **网络映射**）——两个「播放」入口**未合并、未搞反**。
- **⑤ 底座升级**：不改工程结构，出 `上游接缝台账.md`（当时 12 处），回答「以后做会不会更难」。

## 批次 18（上一批，文件名「第十三轮」）· 4 项真问题 + 音频复验素材
- **V71 返回键回桌面**：第十二轮把历史播放/网络映射改成 FAB 跳转，但两页原本是 root tab（`onBackClick = null`）→ 系统返回键直接 finish。`MainScreen` 加 `BackHandler(enabled = selectedTab==1||3)`，两页顶栏加可见返回箭头。
- **V72 按了没反应**：`TabNavBridge` 原 `MutableStateFlow` 同值不发射 + 1/3 被记成启动页 → 改 `MutableSharedFlow` 事件流 + `go()`，1/3 不再记忆。
- **V73 重复区块**：删 `NetworkStreamingScreen.StreamLinkSection`（与 FAB 打开链接都是 `MediaUtils.playFile(url)`），394→282 行，标题「网络映射」；多连接本来就支持。
- **V74 折叠 + WiFi 分组**：`OutputRegistry.uiGroups` 三分组 + `wifiMode` 二级；`OutputSelector` 改折叠卡片；④ 整段移入蓝牙分组插槽。
- **音频**：给 `音频测试素材.zip`（song.wav 30s + 主脚本含 pos:100 + V0 分轴 + lrc）；澄清 V10 的 FAIL 备注是网络页反馈**串行粘贴**，真实状态是「修了没验」。
- **表格**：v5 单张平表（71 项），备注拆 G/H/I，PASS 不挪走。
- **台账**：12 → 13 处。

---

## 批次 19 · 第十四轮（本批，2026-11-09）· 逐文件清单

**触发**：v5 填写结果（50 PASS / 5 待回归 / 9 未测 / 1 FAIL / 2 部分 / 4 不适用）+ 三条新要求：
① 表格改成「当前测试 / 归档记录 / 弃用」三类存放；② 主页进入文件夹后底部 tab 消失；③ 代码包要写详细更新日志；④「先修改已知问题」。

### 源码改动（8 改 + 1 新，共 9 个文件）

| # | 文件 | 类型 | 改了什么 / 为什么 | 对应项 |
|---|---|---|---|---|
| 1 | `app/.../ui/browser/MainScreen.kt` | 上游 | `AnimatedVisibility(visible = !hideNavigationBar.value)` → `visible = true`。底座在文件选择态 / 进入子文件夹时由 `FileSystemBrowserScreen.updateBottomBarVisibility(false)` 整条隐藏底部导航，用户反馈切页不便。现在 3 tab 常驻；上游状态仍写入但不再影响可见性，避免级联改动 | V79（新反馈） |
| 2 | `app/.../utils/storage/FolderViewScanner.kt` | 上游 | `scanMediaStoreImmediateChildren()` 只查 `MediaStore.Video.Media` → **只含 wav/flac/mp3 的文件夹根本不进主页列表**。补 `Audio.Media` 查询按 parent 归入同一 `videosByFolder`；`scanExternalVolumes()` 补 `Environment.getExternalStorageDirectory()` 递归（内部存储主目录不在 `getExternalStorageVolumes()` 里，MediaStore 未索引的新文件靠它按 path 去重补齐） | **V75 根因** / V10 / V57 |
| 3 | `app/.../utils/storage/TreeViewScanner.kt` | 上游 | 同上（`scanMediaStoreRecursive` 版本），保证树形浏览也能看到音频文件夹 | 同上 |
| 4 | `com/tcodeplayer/app/TCodeDeviceScreen.kt` | 自有 | ③ 卡片「脚本文件」行在网络媒体时无条件写「（无）同目录未找到同名 .funscript」，与上方 note 的「探测暂未启用」自相矛盾 → 按 `directory == "网络媒体"` 分支显示「同名脚本探测暂未启用 · R01 待开发」 | V28 |
| 5 | `com/tcodeplayer/app/OutputSelector.kt` | 自有 | **重写**。用户澄清「三者切换是下拉菜单，不是输出方式折叠」→ 删 `expanded` 折叠状态，改 `DropdownMenu` + `DropdownMenuItem` 三项（经典蓝牙 / WiFi / USB）；面板常驻，选中即显示对应配置；④ 仍只在蓝牙分组内 | V74 部分 |
| 6 | `com/tcodeplayer/app/SendTimestampLog.kt` | **新增** | **V50 补做**（用户指出「我之前就写过 你没做」）。APP 侧在 `output.send()` 调用前打点：环形缓冲 20,000 条（50Hz 约 6.7 分钟），记 seq / wall_clock / `SystemClock.elapsedRealtime()` / kind / batch；`stats()` 算实测批每秒 + 相邻间隔 avg/max；`exportCsv()` 生成 `seq,wall_clock,elapsed_ms,delta_ms,kind,batch`，batch 内换行替换 `|` 并加双引号 | V50 |
| 7 | `com/tcodeplayer/app/player/PlayerSyncController.kt` | 自有 | `output.send(batch)` 前加 `SendTimestampLog.record(batch, "batch")`，补 import。**只打点，不碰插值 / 超时丢帧 / 返回值** | V50 |
| 8 | `com/tcodeplayer/app/TCodeRuntime.kt` | 自有 | `sendManual()` 加 `record(line, "manual")`，手动指令不计入 50Hz 速率 | V50 |
| 9 | `com/tcodeplayer/app/DeviceDebugSheet.kt` | 自有 | ⑦ 面板新增「V50 · APP 侧 send 时间戳」：实时统计、最近 5 条预览、**复制 CSV** / **分享 CSV** / 清空 / 刷新；并修掉过期文案「网络输出尚未实现」（TCP/UDP 已在 V69 验证可用） | V50 |

### 交付物改动（非源码）

| 文件 | 说明 |
|---|---|
| `TCode逐项测试填写表_v6.xlsx` | **三个 sheet**：当前测试（23 项）/ 归档记录（50 项 PASS）/ 弃用（4 项不适用），各带 F 列下拉、条件格式、冻结、自动汇总；本轮修复项统一标「待回归」；新增 V77–V82 |
| `CHANGELOG_详细更新日志.md` | 本文件 |
| `第十四轮_修复说明.md` | 本轮判读与喂饭步骤 |
| `上游接缝台账.md` | 13 → **15 处**（+FolderViewScanner、+TreeViewScanner） |
| 包内清理 | 移除 v1–v4 旧表格，只留 v6 |

### 本轮新增验收项

| 编号 | 验什么 | 条件 |
|---|---|---|
| V77 | 本地磁盘音频可见（**V75 根因修复回归**）：素材拷进手机内部存储，下拉刷新 | 素材（无需蓝牙） |
| V78 | 输出方式下拉菜单（V74 修复回归） | 零设备 |
| V79 | 底部 3 tab 常驻（新反馈修复回归） | 零设备 |
| V80 | APP 侧 send 时间戳导出（V50 补做）→ 复制 CSV 发我 | 蓝牙 + 素材 |
| V81 | 网络脚本卡片文案（V28 修复回归） | 局域网 SMB |
| V82 | C01–C29 自检回归（29 项，项数不变） | 零设备 |

### 本轮判读要点

- **V75 的 FAIL 不是脚本/歌词坏**：用户明确写了「素材有 2 轴脚本输出、有歌词显示」——那是 U 盘路径（`scanExternalVolumes` filesystem 递归，批次 16 已放开 `MEDIA_EXTENSIONS`）。本地磁盘读不到是**文件夹发现层**只查 `Video.Media`，两条路径不对称。修的是这一层，不动脚本探测。
- **V21「能有输出，但是播放有问题」**：并入 V75/V77 一起复验；本地磁盘可见后若播放仍异常，再单独查播放侧。
- **V74 部分**：承认理解偏了——「折叠」做成了整卡收起，用户要的是三者**下拉菜单切换**；已改。
- **V50**：认账，之前确实只在说明里写「二选一」没落地 APP 侧导出；本轮补上。
- **V28 待回归**：只修文案误导，**R01 网络盘同名脚本仍未实现**，不冒充完成。

### 验证状态（不糊弄）

- 本轮改 9 个文件，**必须重新编译**；本环境无 Android SDK / CH340，**只做了静态校验**：注释闭合、括号平衡、跨包 import、29 项自检计数、残留符号扫描（全过），**未编译验证、未真机验证**。
- 底座仍是 v1.3.1 tag，未换；`material3 alpha` 约束未变。
- 历史三份真机串口基线（1093 / 1490 / 1524 / 2065 批）理论上不受影响：本轮未碰 `TCodeOutput` 实现、插值、50Hz 循环（只加一行打点）。

---


---

## 批次 20 · 第十五轮（2026-11-09）· 逐文件清单

**触发**：v6 填写结果（新 PASS 5 项：V10/V74/V77/V78/V82；FAIL 2 项：V21/V79；部分 1 项：V75）+ 四条新要求：
①「实机测试显示忙碌丢帧，设备主机就动了几下」；② UDP 默认地址改 `tcode.local:8000`；③ 地址更改后退出重进能保存；④ 轴 ID 处理仅 6 个 `L0 L1 L2 R0 R1 R2`。
另有 V82「目前两个自检进入口」、V79「主页进文件夹后底部 TAG 仍是消失」两条反馈一并修。

### A. 忙碌丢帧 —— 真因（本轮最重要）

`TimedBlockingWriter.submit()` 原逻辑：

```kotlin
if (!busy.compareAndSet(false, true)) { ... return DROPPED_BUSY }
val future = executor.submit(Callable { try { block() } finally { busy.set(false) } })
future.get(timeoutMs, MILLISECONDS)
catch (TimeoutException) { future.cancel(true); return DROPPED_TIMEOUT }   // ← 致命
```

`busy` 只在**写线程的 finally** 里清除。`future.cancel(true)` 对**尚未开始执行**的任务会把它从单线程池队列移除，`Callable` 的 `finally` 永远不会执行 → `busy` 永久 `true` → 之后 50Hz 的每一帧都 `DROPPED_BUSY`，设备「只动了几下」就彻底停发。

这解释了为什么之前三份串口基线（1093 / 1490 / 1524 / 2065 批）没问题、而这轮音频播放出问题：基线里没有触发过一次写超时，一旦某帧 `flush()` 略超 100ms 并命中「任务尚未启动」的竞态，链路就永久死掉。

**修法**（`TimedBlockingWriter.kt`）：
- 超时后**绝不 `cancel`**，让写线程一定执行完并在 finally 里清 `busy`；短暂丢帧后自动恢复
- 加 `consecutiveDrops`：连续丢帧 ≥50（50Hz 约 1 秒）→ 返回 `FAILED`，走原有断连路径（发 DSTOP + 提示断连），避免写线程真卡死时永远静默丢帧
- 加 `reset()`，连接成功 / 断开时清状态，防上一次连接残留 `busy`
- `ClassicBluetoothTCodeOutput.connect()` / `disconnect()` 调 `writer.reset()`

> 未改 `PlayerSyncController`、未改插值、未改 50Hz 循环；`send()` 仍不把丢帧异常抛给控制器。

### B. UDP 默认地址 + 持久化

`OutputRegistry` 重写：
- `DEFAULT_HOST = "tcode.local"`、`DEFAULT_PORT = 8000`（替换原 `192.168.1.50:8080`）
- `install()` 从 SharedPreferences 读回 host / port / wifiMode / baud / key；首次或覆盖安装用 `default_seeded_v2` 标记**强制切一次新默认**，之后完全尊重用户手动改的值
- `setHost / setPort / setWifiMode / setBaud / setKey` 全部 `e.commit()` **同步落盘**，退出重进一定能读到
- `create()` 仍按当前 StateFlow 值构造 `TcpTCodeOutput` / `UdpTCodeOutput`

⚠️ **已知限制**：`tcode.local` 是 mDNS 域名，Android 普通 `InetAddress.getByName()` 在部分机型/系统版本上**不解析 .local**。若 V85 显示默认但连接失败，把主机改成设备实际 IP 即可（V86 会保存）。彻底支持要接 `NsdManager` 解析，排在后续轮次。

### C. 轴 ID 白名单：仅 6 个

| 文件 | 改动 |
|---|---|
| `core/tcode/.../TCodeCommandFormatter.kt` | 新增 `ALLOWED_AXES = setOf("L0","L1","L2","R0","R1","R2")`；`format()` 先查白名单，不在直接返回 `null`（不再发 `V0xxxx` / `A0xxxx`） |
| `app/.../probe/SidecarSpec.kt` | `AXIS_ID` 正则 `[LRV]\d` → `[LR][0-2]`；新增 `ALLOWED_AXES` 供加载层复用；`NAME.V0.funscript` 不再被探测识别 |
| `core/funscript/.../FunscriptJson.kt` | `parseMulti()` 的 `AXIS_ID` 同步收紧；内嵌 `axes[]` 里 `V0` / `A0` / 非法字符串静默跳过 |
| `app/.../probe/SidecarLoadUseCase.kt` | 最终 `filtered` 再过一次白名单（无论来源是内嵌 axes、分文件还是 D2 轴列表） |
| `core/tcode/.../ClassicBluetoothTCodeOutput.kt` | D2 响应解析正则 `\b[LRV]\d\b` → `\b[LR][0-2]\b` |
| `app/.../DeviceDebugSheet.kt` | ⑦ 默认轴列表改 6 个，自定义指令示例 `V09900` → `R29900` |
| `app/.../TCodeDeviceScreen.kt` | ③ 卡片说明改 `NAME.L1/R0/R2.funscript` + 明确「轴 ID 仅 6 个」 |

**自检与单测同步**（项数仍 **29**）：`FrameworkCheck` 的 C06 改成验 `R21739` + `V0/A0` 被拒；C08/C09/C11/C12/C14 的 `V0` 断言统一改 `R2`（都在白名单内）；`FunscriptJsonTest` / `TCodeCommandFormatterTest` / `LinearFunscriptEngineTest` 同步改。**C01–C29 项数不变、业务断言未削弱**。

**素材影响**：音频测试素材 `song.V0.funscript` 已改名 **`song.L1.funscript`**（包内 `测试素材/音频/` 与独立 `音频测试素材.zip` 同步）。历史文档里描述旧素材的地方保留原文并加了注，历史真机日志（曾出现 V0）是既成事实，不回改。

### D. V79 底部 tab 消失（第十四轮诊断不完整，本轮补正）

第十四轮只改了 `MainScreen` 的 `AnimatedVisibility(visible = true)`，但查 `MainActivity.Navigator()` 才发现真相：

```
NavDisplay(backStack = rememberNavBackStack(MainScreen),
           entryProvider = { route -> NavEntry(route) { route.Content() } })
```

主页点文件夹是 `backstack.add(VideoListScreen(...))`，**NavDisplay 把整个 `MainScreen`（连同它的 Scaffold/bottomBar）替换掉**，所以改 `visible` 完全无效。V79 必然 FAIL —— 这是诊断漏看导航根，认账。

**修法**：
- `MainActivity.Navigator()`：`CompositionLocalProvider` 内改用 `Column { Box(weight=1f) { NavDisplay(...) } ; GlobalBottomNav(backstack) }`，导航条位于所有 route 之下，任何压栈页面都不重叠、不消失
- `GlobalBottomNav`：3 个 `NavigationBarItem`，点任意 tab → `goTab()` 清栈回 MainScreen → `TabNavBridge.go(index)`
- `TabNavBridge` 新增 `activeTab = MutableStateFlow(0)` 供全局导航条高亮；`requested` 改 `replay=1`，保证清栈后 MainScreen 重新组合也能收到最后一次 tab 请求
- `MainScreen` 删掉自己的 `bottomBar`（避免双层），`LaunchedEffect(selectedTab)` 同步 `activeTab`

上游接缝 15 → **16 处**（+`MainActivity.kt`）。

### E. V82 两个自检入口

`TCodeDeviceScreen` 底部 Row 里原还有一个「逐项自检」按钮，与第十五轮移到顶部的入口重复。已删底部那个，只留「断开」占满整行；说明文字改为「逐项自检入口在本页顶部标题右侧」。

### F. 源码改动清单（13 改 + 素材改名，无新增文件）

| # | 文件 | 层 | 改动 |
|---|---|---|---|
| 1 | `core/tcode/.../TimedBlockingWriter.kt` | core | 丢帧真因修复 + 连续丢帧兜底 + reset |
| 2 | `core/tcode/.../ClassicBluetoothTCodeOutput.kt` | core | writer.reset() + D2 正则白名单 |
| 3 | `core/tcode/.../TCodeCommandFormatter.kt` | core | 6 轴白名单 |
| 4 | `core/funscript/.../FunscriptJson.kt` | core | parseMulti 白名单 |
| 5 | `core/funscript/.../FunscriptModels.kt` | core | 注释改 6 轴 |
| 6 | `core/tcode/src/test/.../TCodeCommandFormatterTest.kt` | 单测 | R2 + 拒绝 V0/A0 |
| 7 | `core/funscript/src/test/.../FunscriptJsonTest.kt` | 单测 | 6 轴白名单用例 |
| 8 | `core/funscript/src/test/.../LinearFunscriptEngineTest.kt` | 单测 | V0 → R2 |
| 9 | `app/.../OutputRegistry.kt` | 自有 | 新默认地址 + SharedPreferences 持久化 |
| 10 | `app/.../probe/SidecarSpec.kt` | 自有 | 白名单正则 + ALLOWED_AXES |
| 11 | `app/.../probe/SidecarLoadUseCase.kt` | 自有 | 最终白名单过滤 |
| 12 | `app/.../DeviceDebugSheet.kt` | 自有 | 默认 6 轴 + 示例文案 |
| 13 | `app/.../TCodeDeviceScreen.kt` | 自有 | 删底部重复自检入口 + ③ 说明 |
| 14 | `app/.../ui/framework/FrameworkCheck.kt` | 自有 | 断言同步，仍 29 项 |
| 15 | `app/marlboroadvance/mpvEx/MainActivity.kt` | **上游** | 全局 Column + GlobalBottomNav + goTab |
| 16 | `app/marlboroadvance/mpvex/ui/browser/MainScreen.kt` | 上游 | 删 bottomBar + activeTab 同步 + replay=1 |
| — | `测试素材/音频/song.V0.funscript` → `song.L1.funscript` | 素材 | 改名 |

### 交付物改动（非源码）

| 文件 | 说明 |
|---|---|
| `TCode逐项测试填写表_v7.xlsx` | 三 sheet：当前测试 25 / 归档记录 55（含本轮新 PASS 5 项）/ 弃用 4；新增 V83–V89 |
| `CHANGELOG_详细更新日志.md` | 本文件，追加批次 20 |
| `第十五轮_修复说明.md` | 本轮判读、真因、喂饭验收步骤 |
| `上游接缝台账.md` | 15 → 16 处（+MainActivity） |
| `音频测试素材.zip` / 包内素材 | `song.V0.funscript` → `song.L1.funscript` |
| 包内清理 | 移除 v6 表格，只留 v7 |

### 本轮新增验收项

| 编号 | 验什么 | 条件 |
|---|---|---|
| V83 | 忙碌丢帧修复回归（**V21/V75 真因**）→ 设备连续动作 | 蓝牙 + 音频素材 |
| V84 | 全局底部 3 tab（V79 修复回归） | 零设备 |
| V85 | UDP 默认 `tcode.local:8000` | 零设备 |
| V86 | 地址改后退出重进仍保存 | 零设备 |
| V87 | 轴 ID 仅 6 个，V0/A0 不发指令 | 素材 |
| V88 | 单自检入口 + C01–C29 = 29/29 | 零设备 |
| V89 | UI 精修登记 P10（**本轮不改**） | — |

### 本轮判读要点

- **V21 / V75 的「忙碌丢帧」不是音频脚本/歌词坏**，是蓝牙写链路的竞态 bug；V77 已 PASS 证明本地磁盘音频可见，V10 PASS 证明脚本+歌词加载正常。三件事分开，不再混。
- **V79 第十四轮诊断漏看导航根**，本轮补正；改的是 `MainActivity`，不是再改 `visible` 开关。
- **V82「两个自检进入口」** 是第十五轮把按钮移到顶部时没删底部旧的，已收成一个。
- **V74/V78「UI 仍需细调」**：按你说的「UI 精修再搞，先记下」登记 P10，本轮一行 UI 视觉都没动。

### 验证状态（不糊弄）

- 改 16 个源码/单测文件 + 素材改名，**必须重新编译**；本环境无 Android SDK / 蓝牙模块 / CH340，**只做了静态校验**：全工程 278 个 Kotlin 文件的注释闭合 / 括号平衡（3 个上游原文件为简易解析器对三引号字符串的误报，已逐一比对其与上一版 zip 内文件 md5 完全一致）、关键符号、29 项计数、跨包 import、V0 残留扫描。**未编译验证、未真机验证**。
- 底座仍 `v1.3.1` tag；自检仍 **29 项**；`material3 alpha` 约束未变。
- 历史三份真机串口基线理论上不受影响：本轮未碰插值 / 50Hz 循环 / 指令幅度，只改了「超时后不 cancel」和「非白名单轴不发指令」。**注意**：若历史脚本里含 `V0` 轴，新版本将不再发该轴 —— 这是本次规格变更的直接结果，不是回归失败。

## 已知未完成项（按优先级）

| 编号 | 项 | 状态 |
|---|---|---|
| R01 | 网络盘（SMB/FTP/WebDAV）同名 `.funscript` / `.lrc` 探测 | 待开发，③ 卡片如实写明降级 |
| V50 | 50Hz 严格验证 | 工具已给（V80 导出 CSV），**待用户导出后判读** |
| P07 | 热力图点击游标 seek（与单指平移冲突，需手势设计） | 未做 |
| P10 | V74/V78 视觉细调（下拉菜单/输出选择器 UI） | 用户要求「先记下、UI 精修再搞」，本轮不动 |
| P08 | 主页音视频分类 / UI 精修 | 用户说「现不考虑 UI」 |
| P09 | FLAC 内嵌歌词 | 未做 |
| V41 | 脚本末尾 +1s DSTOP + Seek 恢复（需播满 2 分钟） | 未测 |
| V42/V43 | 速度限制 / 行程限幅 / 反转 真机 | 未测 |
| V51 | 双格式覆盖优先级 | 未测 |
| V26/V27/V29 | FTP / WebDAV / 底座冒烟 | 未测 |
| V70 | USB OTG + CH340 真机 | 条件不足 |
| V23 | 断连容错 | 条件不足 |
| — | 底座三层重组（upstream / patches / overlay） | 用户说「功能做好再动」，台账已备好，随时可做 |
| — | 独立 library 模块 + 3 处接缝 | 同上 |
| — | WiFi WebSocket 输出 | 未做（TCP/UDP 已可用） |

## 升级底座的操作顺序（台账配套）

1. 拉中文版新稳定 tag → 覆盖 `upstream` 原样文件
2. 按 `上游接缝台账.md` 重放 **16 处**补丁
3. 拷回 `com/tcodeplayer.*` 与 4 个 `core:*` 模块
4. Sync → Clean → Rebuild
5. 跑 C01–C29 → 按 `TCode逐项测试填写表_v6.xlsx` 的「当前测试」真机回归


---

## 批次 21 · 第十六轮：脚本输出流畅度修复（对齐 MultiFunPlayer）

**用户诉求**：实机卡顿、动几下停一会，要求「必须和 MultiFunPlayer 体验一样」，并去学习其算法逻辑与代码。

**动作**：下载 `Yoooi0/MultiFunPlayer` master 源码（codeload 200 / 430 文件），读
`AbstractOutputTarget.cs`（专用线程 + 绝对周期 + `SleepPrecise` / `PeriodicTimer`）、
`IUpdateContext.cs`（UpdateInterval 默认 10ms、`OffloadElapsedTime=false`、`SendDirtyValuesOnly=true`）、
`SerialOutputTarget.cs`（固定速率采样 → dirty 过滤 → `DeviceAxis.ToString(values, elapsed*1000)` → 顺序写）、
`DeviceAxis.cs`（`I<ms>` 后缀、`IsValueDirty` 阈值、轴白名单）、`MathUtils.cs`（PCHIP / Makima）。

**根因（三个结构性缺口，非上一轮的 busy 竞态）**：
1. 指令无 `I` 间隔 → 设备收到 `L07700` 立刻到位后静止等下一条，主机抖一帧就停一下
2. `TimedBlockingWriter` 100ms 超时 / busy 丢帧 → 整批 6 轴指令直接跳过
3. 协程 + 每轮 `withContext(IO)` + `Future.get` 阻塞 → 50Hz 时钟被调度器拖歪

**改动文件清单（7 改 + 3 新，上游 `app/marlboroadvance/*` 零改动）**：

| 文件 | 改动 |
|---|---|
| `core/tcode/TCodeCommandFormatter.kt` | 新增 `buildBatch(sample, intervalMs)` 追加 `I<ms>`；`isDirty()`（MFP 阈值）、`filterDirty()`；原签名输出一字不变 |
| `core/tcode/TCodeOutput.kt` | 新增 `sendNow()` 同步默认实现（`runBlocking` 委托） |
| `core/tcode/ClassicBluetoothTCodeOutput.kt` | **移除主链路 TimedBlockingWriter / 超时丢帧 / busy 丢帧**；`sendNow()` 直写；只由 `IOException` 判断连 |
| `app/NetworkTCodeOutputs.kt` | TCP / UDP 各加 `sendNow()` override 直写，TCP 写加同步 |
| `app/UsbSerialTCodeOutput.kt` | 加 `sendNow()` override 直写 |
| `app/player/PlayerSyncController.kt` | 重写为外观，委托 `MotionDriveLoop`；对外接口 `start/stop/onMediaChanged/resetProcessingState/isTimedOut` 一字不变 |
| `app/TCodeFeatures.kt` | 新增 3 个开关：`TCODE_INTERVAL`（默认开）、`DIRTY_ONLY`（默认开）、`HIGH_RATE`（默认关） |
| `app/TCodeRuntime.kt` | `sendStats()` 改显示实测批/秒、周期、I 开关、dirty 开关、最大帧间隔、>60ms 间隙、最大写耗时、写失败、丢帧=0 |
| `app/ui/framework/FrameworkCheck.kt` | C28 改为真跑专用线程：30ms 写延迟下 0 丢帧 + `I` 指令 + dirty 阈值 + 绝对周期补偿；自检仍 29 项 |
| `app/MotionDriveConfig.kt`（新） | 运行参数快照；关 I 时强制关 dirty-only（安全联动）；UDP 强制全发 |
| `app/player/MotionDriveLoop.kt`（新） | **核心**：专用高优先级线程 + `nanoTime` 绝对周期 + `LockSupport.parkNanos` + 真实 elapsed + 不丢帧 + I 间隔 + dirty-only + 统计 |
| `core/tcode/TCodeCommandFormatterTest.kt` | 新增 3 项单测（I 间隔 / dirty 阈值 / 过滤） |
| `app/test/.../MotionClockTest.kt`（新） | 2 项单测（绝对周期补偿 / 长卡顿重置不追赶） |

**等价复算**（30s / 50Hz / 1500 帧，同一组写延迟）：
送达帧 1476→**1500**、丢帧 24→**0**、静止总时长 31983ms→**3928ms（↓87.7%）**、静止占比 100%→13.1%。
残留：0.5% 概率的单帧 150ms 蓝牙阻塞仍留一个孤立 ~144ms 间隙，属 SPP 层物理延迟，主机算法消不掉；① 卡片已加「最大写耗时」便于实机定位。

**验证状态**：本环境无 Android SDK / 蓝牙模块 / CH340，只做静态校验（749 条目、288 个 Kotlin 自有包括号/注释闭合 0 异常、29 项自检唯一、12 key / 12 Definition、4 输出均 override `sendNow`、跨包 import）。**未编译、未真机验证**。

**未做**：PCHIP 改默认（避免作废三份已验收串口数值基线；⑥ 面板可选）、I 的握手自动探测（若固件不认 I，需 ⑤ 手动关）、R01 / P07–P10 / V41–V43 / V70 / V50 照旧挂着。

---

## 批次 22 / 第十七轮（2026-11-09）· 再修卡顿 + 完整项目交接

**用户诉求（原话）**：「还是不行  问题现象和之前一样   你再尝试修复一下   这次打包把此次项目到现在为止的全部变化都做记录  我打算换AI试试  尽量详细能让新AI 看懂并接手项目」

### 先做的核对（不是直接改代码）

解压第十六轮交付 zip，逐文件核对调用链与 md5：
- `TCodeRuntime` → `PlayerSyncController`（外观）→ `MotionDriveLoop` → `ClassicBluetoothTCodeOutput.sendNow()` **确认已接通**；
  `MotionDriveLoop.kt`(9589B) / `MotionDriveConfig.kt`(1488B) 确实在包内。→ 排除「新代码没生效」。
- 16 轮 zip 内 `TCodeRuntime.kt` / `PlayerSyncController.kt` 时间戳虽为 11-07，但内容已是委托新循环，不是旧协程。
- 逐行读 MultiFunPlayer 源码：`DeviceAxis.cs:47-52`、`SerialOutputTarget.cs:207-214`、`AbstractOutputTarget.cs:442-461`、
  `IUpdateContext.cs:44/79-86`。

### 定位到的 4 个真实原因

| 编号 | 原因 | 证据 |
|---|---|---|
| R1（首要） | **批量格式不同**：我方「一轴一行 + 每行 `\n` + 每行 `I`」，MFP 是「所有轴空格拼一行 + 一个 `\n`」，一次 Write。固件按行分帧并对每行 I 排队 → 6 行 × I ≈ 144ms，主机 20ms 一批 → 队列无限积压 | `DeviceAxis.cs:51` 的 `Aggregate` 空格拼接；`SerialOutputTarget.cs:213` 单次 Write |
| R2 | 第十六轮 `TCODE_INTERVAL` / `DIRTY_ONLY` 默认全开；固件不认 I 会丢帧，dirty-only 在脚本恒定段（历史日志有 7.49→8.60s 连续 40 条 `L05000`）会完全静默 → 固件超时保护停机。且覆盖安装时 `sp.getBoolean(key, fallback)` 读回旧 `true`，改默认值无效 | `TCodeFeatures.install()` |
| R3 | `isPlaying()` 每帧读 `core-idle` / `idle-active`，mpv 瞬时 idle 会整帧跳过 | `MpvTimeProvider.kt` |
| R4 | 面板只有统计数字，看不到实际发出的字节，三种故障（没发 / 格式错 / 设备没吃）在截图上无法区分 | `TCodeRuntime.sendStats()` |

### 改动清单（9 个文件，全部自有包；上游文件 md5 与第十六轮完全一致）

| 文件 | 改动 |
|---|---|
| `core/tcode/TCodeCommandFormatter.kt` | 新增 `buildBatch(sample, intervalMs, singleLine)`；2 参数版本默认 **MFP 单行空格**；1 参数版本保留旧一轴一行（手动调试 / 历史基线 / C17）；`I` 用真实帧间隔，删 `+4ms` 余量 |
| `app/TCodeFeatures.kt` | `TCODE_INTERVAL` / `DIRTY_ONLY` 的 `enabledByDefault` 改 **false**；新增 `SINGLE_LINE`（默认 true）、`HEARTBEAT`（默认 true）；`install()` 加 `__pref_version=2` 一次性 `remove` 旧值，覆盖安装也能生效 |
| `app/MotionDriveConfig.kt` | `Snapshot` 追加 `singleLine: Boolean = true` / `heartbeatMs: Long = 60L`（都带默认值，兼容 FrameworkCheck C28 的 `Snapshot(20L,true,false)` 位置参数）；`HEARTBEAT_MS=60`；默认组合 = 单行 + 无 I + 全量 + 心跳 + 50Hz |
| `app/player/MotionDriveLoop.kt` | 走 `cfg.singleLine`；删除 `INTERVAL_MARGIN_MS`；**静默心跳**：dirty 为空且距上次发送 ≥ heartbeatMs → 强制补一帧全量；`Stats` 新增 `lastFrame` / `heartbeats` / `silentTicks` / `singleLine` / `threadName`；`lastSendNano` 跟踪 |
| `app/player/MpvTimeProvider.kt` | `isPlaying()` 改为只用 `pause` + `hasMedia()`，删 `coreIdle` / `idleActive`；播完由脚本末尾 +1s DSTOP 兜底 |
| `app/TCodeRuntime.kt` | `sendStats()` 增加「首行样例」「格式=单行(MFP)」「心跳 / 静默帧」「线程名 / 共 N 批」 |
| `app/player/PlayerSyncController.kt` | 第 6/7 条注释同步为单行批量 |
| `app/ui/framework/FrameworkCheck.kt` | C28 新增多轴单行断言 `L07700I20 L19000I20 R01739I20\n` 与回退格式断言；**自检仍 29 项** |
| `core/tcode/src/test/.../TCodeCommandFormatterTest.kt` | 双轴 I 间隔断言改单行；新增 `single line format can be switched back to legacy one axis per line` 单测 |

### 默认配置的取舍（记录在案，避免接手者又改回去）

MFP 默认 `OffloadElapsedTime=false`（带 I）+ `SendDirtyValuesOnly=true`。
本轮**没有照抄**，选的是 MFP 同样支持的 `OffloadElapsedTime=true + SendDirtyValuesOnly=false`：
每帧全量、连续喂、格式与 MFP 一致、不依赖固件是否解析 `I`。
理由：设备固件能力未知；4 份已验收串口日志（1093/1490/1524/2065 批）全是「每帧全量、无 I」；
用户明确要求「流畅度与 MFP 一样」，指的是体验而非配置值。
I / dirty-only / 单行回退 / 100Hz 四个开关保留在 ⑤，供一次一个变量的 A/B。

### 静态校验结果

- 288 个 Kotlin 文件扫描：4 处告警全部落在上游原文件 `PlayerUtils.kt` / `M3UParser.kt`，
  md5 与第十六轮 zip **完全一致**（简易解析器对三引号字符串与嵌套注释的误报），未触碰上游。
- 9 个改动文件的关键符号引用、`INTERVAL_MARGIN_MS` / `coreIdle` / `idleActive` 残留 = 0、
  29 项自检唯一、单行格式断言逐条核对通过。
- **未编译、未真机验证**：本环境无 Android SDK / JDK / 蓝牙模块 / CH340。

### 交接资产（用户打算换 AI，本轮全部落入交付包）

| 文件 | 作用 |
|---|---|
| `HANDOFF_项目交接.md`（新） | 自包含总览：目标与硬约束、底座与工具链精确版本、工程结构树、**批次 01–22 完整时间线**、术语对照表、测试体系、已知问题台账、编译步骤与 10 条历史踩坑、给接手者的最小路径 |
| `卡顿问题_专项排查档案.md`（新） | 现象原文、已排除项、三轮尝试与结果、MFP 源码证据、R1–R4、A/B 判读流程、9600 波特率物理上限提醒、给新 AI 的技术备注 |
| `第十七轮_修复说明.md`（新） | 本轮简明说明与喂饭验收 |
| `CHANGELOG_详细更新日志.md` | 追加本批次（即本段） |
| `TCode逐项测试填写表_v9.xlsx`（新） | 当前测试 / 归档记录 / 弃用 三 sheet，新增 V95–V99 |
| `历史测试表格/`（新目录） | v1–v8 全部历史表格，保留编号避免对不上 |
| `上游接缝台账.md` | 16 处上游改动（本轮未新增），升级中文版新稳定版时逐条重放 |

### 未做（照旧挂账，不冒充完成）

R01 网络盘同名脚本、P07（游标 seek 冲突）、P08（主页音视频分类 / ③ 并入状态条）、P09（FLAC 内嵌歌词）、
P10（UI 精修，用户明确说先记下）、V41/V42/V43、V50（50Hz 严格验证，等 APP 侧 CSV）、
V70（CH340 真机）、V26/V27/V28/V29/V37、`material3 1.5.0-alpha15` 无 stable。

### 下一步（给接手者）

1. 用户编译 → ① 卡片「首行样例」是否为空格分隔单行（不是则包没换，先解决构建）
2. 流畅度主判 V99；仍卡则导出 ⑦ CSV 看 `delta_ms`：有洞 → APP 侧；无洞 → 固件/链路侧
3. 按专项档案 §6 一次一个变量做 A/B；特别核对模块 UART 波特率是否够 6 轴 × 50Hz 的 ~3000 B/s
4. 卡顿解决后再回到 R01 / P07–P10 / V41–V43
