# TCode 联动 APP · 项目交接文档（HANDOFF）

> **这份文档是写给一个完全没有本对话上下文的 AI / 工程师看的。**
> 目标：读完这一份 + 包内 `CHANGELOG_详细更新日志.md` + `卡顿问题_专项排查档案.md`，
> 就能直接接手，不需要翻聊天记录。
>
> 文档版本：2026-11-09（第十七轮 / 批次 22 交付时）
> 交付包：`TCodePlayer_MPVEx_CN_Full.zip`（约 81 MB，含完整可编译 Android 工程）
> 重要前提：**交付的是源码工程，不是 APK**。本方开发环境没有 Android SDK / JDK / 真机 / 蓝牙模块，
> 所有"已修复"都只做了静态校验，**编译与真机验证一直由用户在其 Windows 机器上完成**。

---

## 0. 一页速览（先读这里）

| 项 | 内容 |
|---|---|
| 项目是什么 | 在 mpvEx 中文版播放器里**整合** TCode 联动：播放视频/音频时按 `.funscript` 脚本，以 50Hz 把 6 轴指令发给外部设备（经典蓝牙 SPP / WiFi TCP / WiFi UDP / USB CH340） |
| 与 MultiFunPlayer 的关系 | MFP 是 Windows/.NET 的**外挂式**同步器；本项目是**整合进 Android 播放器**。只借鉴输出层思路与算法，不复用其代码 |
| 底座 | `yaodao0yaodao/mpvEx-CN` **v1.3.1 tag**（2026-08-20 稳定版），**用户明确要求不换底座** |
| 本方代码 | 全部在 `com.tcodeplayer.*`（app 模块）+ 4 个 Gradle 子模块 `:core:funscript` `:core:lyrics` `:core:tcode` `:core:axis` |
| 上游改动面 | **16 处**（逐条见 `上游接缝台账.md`），其余上游 592+ 文件一个字节没改 |
| 用户 IDE | Android Studio **Rabbit 2 \| 2026.2.2 Patch 1**，Build `AI-262.10968.63.2622.16840690`，2026-11-05，Windows 10，JDK 17（工程侧）。**不要再建议降级 / 升级 IDE** |
| 当前最大未解决问题 | **实机卡顿："动几下就停一会"**，连续两轮修复后用户反馈"现象和之前一样"。详见 `卡顿问题_专项排查档案.md` |
| 已稳定验收的能力 | 编译、单 APP、脚本双格式（单文件内嵌 axes / 多轴分文件）、6 轴指令、数值插值（4 份串口日志共 6000+ 批）、D1/D2 握手、DSTOP 规则、本地/SMB 字幕、音频 wav/flac、29 项内联自检、WiFi TCP、手动调试面板 |
| 测试体系 | APP 内 **C01–C29 逐项自检**（零设备）+ 外部 **V01–V99 验收表**（`TCode逐项测试填写表_v9.xlsx`，分「当前测试 / 归档记录 / 弃用」三个 sheet） |
| 接手后第一件事 | 见 §10「给接手者的最小路径」 |

---

## 1. 项目目标与不可动摇的约束

### 1.1 策划案要点（用户从一开始锁定的方案，中途不允许改）

1. 底座必须是 mpvEx（不是自己写播放器，不是 MediaPlayer 替代版）
2. `core:funscript`：`pos / 100.0` 线性插值，clamp 到 `[0, 0.99]`，首尾取值
3. `core:lyrics`：LRC → 毫秒
4. `core:tcode`：经典蓝牙 SPP UUID `00001101-0000-1000-8000-00805F9B34FB`，`D1`/`D2` 500ms × 2 重试，异常发 `DSTOP`
5. 50Hz 采样控制器，读 mpv `time-pos`
6. 视频/音频同名文件探测（精确 + 忽略大小写两轮），静默失败不崩
7. `D2` 轴列表过滤、字幕/LRC 接入
8. Koin、权限、5 套 Material 3 深色 Compose 界面
9. 指令格式：v0.3 短命令 `L07700`（轴 ID + 4 位幅度），**不发送 `G` / `<>`**
10. **暂停不发指令也不发 DSTOP；退出 / 媒体切换 / 退后台发一次 DSTOP**
11. 第 1 步「真机 SPP 验证」不通过则停止 —— 已由用户用手机蓝牙串口 App 发 `L00000` 且设备有实际动作通过

### 1.2 用户后来追加的硬约束

- **单 APP 单图标**，不许出现第二个测试 App；测试入口必须在主 APP 内（底部「联动」tab）
- 「已加载脚本」界面必须能看到**实际命中的文件名、生效轴、轴的来源**
- 音频（mp3/wav/flac）与视频共用同一条驱动链路
- 脚本**两种格式都要支持**：① 单文件内嵌 `axes:[L1,R0,...]`（version 1.1）② 多轴分文件 `NAME.L1.funscript`。两种同时存在时**分文件覆盖同名轴**
- **轴 ID 仅允许 `L0 L1 L2 R0 R1 R2` 六个**，`V0`/`A0` 一律不发
- 文件名规则：**主名严格区分大小写、仅扩展名忽略大小写**（`song.LRC` 命中，`SONG.lrc` 不命中）
- 所有 git 依赖用稳定版本 / release tag；**底座暂时不换**，但要便于以后升级中文版新稳定版
- 所有串口链路相关名称统一（见 §6）
- UI：底部 3 tab（主页 / 播放列表 / 联动），主页 FAB 5 项；「上次播放」≠「历史播放」
- 卡顿问题「必须和 MultiFunPlayer 体验一样」

### 1.3 明确不做的（别重复提议）

- 不改底座为 `azxcvn/mpvEx-CN`、不换上游 `marlboro-advance/mpvEx`、不升级到 1.3.2-test.2
- 不把 `material3:1.5.0-alpha15` 换成 1.4.0（官方截至 2026-09-23 最新 stable 是 1.4.0，不含 M3 Expressive API，上游 20+ 文件依赖它，换了必大面积编译失败）
- 不照 DeepSeek 2.1/2.2 方案「从 Phase 1 清零重做」（会把已验收能力拆掉再原样做一遍）
- 不引入 MultiFunPlayer 的 PCHIP/Makima 作为**默认**插值（会让 4 份已验收串口数值基线作废；⑥ 面板可选）
- 不做 AGP 降级、不换 IDE、不改 Gradle 到 8.x

---

## 2. 底座与工具链（精确值，别猜）

| 项 | 值 |
|---|---|
| 底座仓库 | `yaodao0yaodao/mpvEx-CN` |
| 底座版本 | **v1.3.1 tag**（2026-08-20），`versionName 1.3.1` / `versionCode 13010` |
| 上游关系 | fork 自 `marlboro-advance/mpvEx`；汉化基于 v1.2.9；独立包名 `io.github.yaodao0yaodao.mpvex` |
| 架构 | **仅 arm64-v8a**，自带 libmpv AAR（约 74–77 MB） |
| AGP / Gradle | 9.1.0 / 9.4.1（Gradle 发行包走腾讯云镜像） |
| Kotlin / compileSdk | 2.3.20 / 36 |
| JDK | 17（工程 Gradle JDK；IDE 自带 Runtime 25 是另一回事，别混） |
| 依赖镜像 | 阿里云；`usb-serial-for-android:3.9.0`（稳定 tag） |
| material3 | `1.5.0-alpha15`（**无 stable 可换**，唯一非 Stable 依赖，上游自带） |
| 构建变体 | 用户实际编译 `assembleFdroidDebug` → `app-fdroid-debug.apk`（也可能 `app-standard-debug.apk`，取决于 variant） |
| Studio | Rabbit 2 \| 2026.2.2 Patch 1（用户 2026-11-09 提供，见其贴的 About 信息） |

**上游仓库当前状态（用户提供的网页快照，2026-11-09）**：`master` 最新 commit `67906ad`（2026-08-29），
README 宣告计划引入 ExoPlayer 双内核；Releases 显示 **v1.3.1 仍标 Latest**（6 个 release）。
仓库明确写「已停止无验证的上游自动合并，上游改动人工检查后按需引入」——这正是本项目做「上游接缝台账」的原因。

---

## 3. 工程结构（接手时按这个找代码）

```
TCodePlayerMPVEx/
├── settings.gradle.kts            # 上游 + 4 个 core 模块 include（接缝 1）
├── build.gradle.kts               # 根插件版本目录 apply false（接缝 2）
├── gradle/libs.versions.toml      # AGP / Kotlin / material3 等版本
├── gradle.properties              # android.overridePathCheck=true（中文路径保险）
├── app/
│   ├── build.gradle.kts           # +4 个 core 依赖、usb-serial、TCode sourceSet（接缝 3）
│   └── src/main/
│       ├── AndroidManifest.xml    # 权限 + 单 LAUNCHER（接缝 4）
│       ├── java/app/marlboroadvance/mpvex/    # ← 上游 592+ 文件，除 16 处接缝外一律不动
│       └── java/com/tcodeplayer/app/          # ← 本方全部业务代码
│           ├── TCodeRuntime.kt               # 全局运行时：生命周期、连接、输出、脚本加载、统计
│           ├── TCodeDeviceScreen.kt          # 「联动」tab 主界面（①~⑦ 卡片）
│           ├── TCodeFeatures.kt             # 功能开关注册表（14 个 key）+ install() 一次性迁移
│           ├── MotionDriveConfig.kt         # 驱动参数快照（周期/单行/I/dirty/心跳）
│           ├── OutputRegistry.kt            # 蓝牙/WiFi TCP/WiFi UDP/USB 四输出注册与持久化
│           ├── OutputSelector.kt            # 下拉菜单切换输出方式
│           ├── NetworkTCodeOutputs.kt       # TcpTCodeOutput / UdpTCodeOutput
│           ├── UsbSerialTCodeOutput.kt      # CH340，setParameters 真实设波特率
│           ├── UsbSerialSpec.kt
│           ├── DeviceDebugSheet.kt          # ⑦ 手动调试：滑块/D1/D2/DSTOP/回显/CSV
│           ├── MotionControlSheet.kt        # ⑥ 热力图 + 行程/速度/反转 + PCHIP/Makima 可选
│           ├── SendTimestampLog.kt          # 每帧 send 前打点，导出 CSV（判 50Hz 用）
│           ├── TCodePlaybackOverlay.kt      # 播放界面可拖动状态条
│           ├── PlayerActivityIntegration.kt # 上游生命周期接缝（ActivityLifecycleCallbacks）
│           ├── DirectMpvFacade.kt / SidecarScriptExtractor.kt
│           ├── bluetooth/DeviceHandshake.kt
│           ├── player/
│           │   ├── MotionDriveLoop.kt       # ★ 50Hz 专用线程驱动循环（卡顿问题主战场）
│           │   ├── PlayerSyncController.kt  # 外观，委托 MotionDriveLoop
│           │   ├── MpvTimeProvider.kt       # 读 mpv time-pos / pause
│           │   ├── MpvFacade.kt / PlayerTimeProvider.kt / PreviewTimeProvider.kt
│           ├── probe/                       # 同目录脚本/歌词/字幕探测
│           └── ui/framework/
│               ├── FrameworkCheck.kt        # ★ C01–C29 自检（29 项，调真实 Kotlin 类）
│               └── FrameworkCheckScreen.kt  # 自检页（顶部入口 + 底部留白）
├── core/
│   ├── funscript/   # FunscriptJson（含 parseMulti 内嵌 axes）/ LinearFunscriptEngine
│   ├── lyrics/      # LRC → 毫秒
│   ├── tcode/       # TCodeCommandFormatter / TCodeOutput / ClassicBluetoothTCodeOutput / TimedBlockingWriter(已不用)
│   └── axis/        # AxisProcessor（反转→限幅→限速→平滑）+ PCHIP/Makima 可选 + 10 单测
├── 测试素材/        # song.wav / song.funscript / song.L1.funscript / song.lrc 等
├── docs/ 及根目录   # 各轮说明、CHANGELOG、上游接缝台账、测试表
```

**关键设计**：不改 `PlayerActivity.kt` 主体；通过 `Application.ActivityLifecycleCallbacks` +
轮询 `MPVLib` 的 `path` / `time-pos` 接管播放生命周期。这是为了让上游升级时接缝最少。

---

## 4. 完整变更时间线（批次 01–22）

> 每一批的**逐文件清单**在包内 `CHANGELOG_详细更新日志.md`，各轮的用户原话与判读在
> `第X轮_*说明.md`。这里给主线索引，避免接手者重复做已做过的事。

| 批次 | 主题 | 关键改动 | 结果 |
|---|---|---|---|
| 01 | 按策划案出 Phase 1 源码 | 3 个 core 模块 + Compose UI + `spp-verify` 独立验证工程 + 文档 | 源码齐，**真机 SPP 未测**（按策划案第 1 步停） |
| 02 | 用户不会用 | 中文喂饭步骤 | — |
| 03 | 没硬件也要看效果 | 零设备 HTML 预览页 | 17 项等价复算全过 |
| 04 | 音频也要 funscript | 探测按文件名去扩展名，音视频共用链路；修「忽略大小写返回候选名」bug | 链路补上 |
| 05–06 | 不想装 Android Studio | 一键自检 HTML | **17/17 PASS**，50.0 条/秒（模拟） |
| 07 | 用户手机蓝牙发 `L00000` 设备有动作 | 策划案第 1 步判 PASS，状态文档改 | ✅ 硬件门槛清除 |
| 08–12 | 编译真机验证工程 | 中文步骤 → Gradle 换华为云/阿里云 → 锁 8.9 → Sync → 出 APK | 编译链路打通；`SPP VERIFY` 显示 INCONCLUSIVE（设备不回显），但设备有实际动作 |
| 13 | 「要完整 APP」 | 做了 Java + MediaPlayer 版（**后来废弃**，不是主线） | 用户指出 UI 差、未按方案 |
| 14 | 中文路径报错 | 全英文名包 + `android.overridePathCheck=true` | 修复 |
| 15 | 「按原方案、先搭框架、一项一项测」 | `TCodePlayerFramework`：Kotlin/Compose/Koin/4 模块 + C01–C17 自检 | 主线回到策划案 |
| 16 | 给 mpvEx / mpvEx-CN 仓库地址 | codeload/raw 可访问（git clone 仍 403），下载中文维护版 592 文件**真合并** | 单图标版雏形 |
| 17 | `org.jetbrains.kotlin.jvm` 版本重复 | 版本目录统一 alias，子模块不写 version | 修复 |
| 18 | AGP 9 禁用 `kotlin.android` 插件 | 删该插件 + `kotlin{}` 块 | 修复（AGP 9 内置 Kotlin） |
| 19 | IDE 太老只支持 AGP 8.7.2 | 用户自行升级 Studio（现 Rabbit 2 2026.2.2） | 修复 |
| 20 | `BuildConfig.GIT_COUNT = fatal: not a git repository` | Git helpers 判退出码 + 无 `.git` 回退 0 | 修复 |
| 21 | 「两个软件 / UI 全是 mpvEx」 | 收成单 LAUNCHER，设备面板并入底部 tab；加「已加载脚本」卡片 | 用户认可 |
| 22 | 完成度 / 底座版本 / 29 项测试清单 | `全功能测试清单.md` | — |
| 23 | 第一份真机串口日志（1093 条） | 判读：插值/格式/DSTOP 全对；**单文件内嵌 axes 未解析**；50Hz 未严格证明 | 发现格式兼容缺口 |
| 24 | D1/D2 握手日志 + 分轴素材 | 三轴 1526 条全对；要求双格式 + 稳定版 → **换 v1.3.1 tag**，`parseMulti()` | V17 补 PASS |
| 25 | 双格式真机 1490/1524 批 | 合并轴单文件 + 多轴分文件全过；SMB 指南 | ✅ |
| 26 | 截图 + 暂停/主动停止 | 记 P01（播完退出）、P02（切换不便）、P03（自检页遮挡）、P04（网络字幕文案）、R01（网络盘同名脚本） | — |
| 27 | 修 P01–P04 + 波特率 + 模块化 | 新增 `TCodeFeatures` 注册表、串口参数面板、C01–C20；评估 MultiFunPlayer 后**不引入其代码** | — |
| 28 | 2.0 方案 + `unresolved reference` | 新增 `:core:axis`、热力图、C01–C27；把新类收进已编译包路径 | — |
| 29 | DeepSeek 2.1 方案评估 | 指出 3 处硬伤：IDE 锁 Ladybug 冲突、Phase1 清零重做、100% Stable 无解 | 未改代码 |
| 30 | 用户提供 IDE 版本 → 2.2 复核 | 收回「IDE 太老」判断；查实 `azxcvn` 有 9 个 Release 但 material3 仍 alpha | 未改代码 |
| 31 | 用户选「B + 不换底座」 | 源码级检查 323 条目 → 增量 ① `send()` 真超时丢帧 + 退后台 DSTOP + C01–C29 | — |
| 32 | 65 个编译错 | 真因 2 个：KDoc 里 `core/*` 造成嵌套注释未闭合 + C20 缺跨包 import；改全量文本覆盖 + md5 校验 | 修复 |
| 33 | 自检入口点不到 | 按钮移到「联动」页顶部 + `navigationBarsPadding()` | 修复 |
| 34 | 要可填写表格 | `TCode逐项测试填写表.xlsx`（40 项） | — |
| 35 | v2 → 修 2 FAIL + 4 部分 | 底部留白、`commit()` 持久化、`featureEnabled()` 重组、flac/wav 扩展名；新增 ⑦ 手动调试 | — |
| 36 | v3 → 归档 + 拆备注 | 28 项 PASS 归档、备注拆三列、截图索引；Q01 大小写规则、V11 坏脚本文案 | — |
| 37 | v4 → 名称统一 + 输出扩展 | **删除波特率面板**（SPP 既设不了也读不到）；3 tab + FAB 5 项 + 改名「联动」+ 换图标；WiFi TCP/UDP + `OutputRegistry`；USB CH340；`上游接缝台账.md` | — |
| 38 | v5 → 返回逻辑等 | `BackHandler` 修返回键回桌面、`SharedFlow` 修 FAB 连点、删重复「流视频媒体」、TCP/UDP 收进 WiFi 二级 | — |
| 39 | v6 → 分类归档 + CHANGELOG | 修本地磁盘音频发现（补 `Audio.Media`）、输出下拉菜单、底部 tab 常驻、**APP 侧 send 时间戳 CSV 导出**；`CHANGELOG_详细更新日志.md` 批次 01–19 | — |
| 40 | v7 → 忙碌丢帧 | 真因 `future.cancel(true)` 让 `finally` 不执行 → `busy` 永久 true；改成超时不 cancel；UDP 默认 `tcode.local:8000` + 持久化；**6 轴白名单**；全局底部 tab；单自检入口 | 用户反馈「还是不行」 |
| 41 | 第十六轮 · 流畅度 | 拉 MultiFunPlayer 源码 430 文件对照 → 专用线程 `MotionDriveLoop` + 绝对周期 + `I` 间隔 + dirty-only + 移除丢帧 | **用户反馈「现象和之前一样」** |
| **42 / 批次 22（本轮）** | **再修卡顿 + 完整交接** | 见 §5 与 `第十七轮_修复说明.md`；新增 HANDOFF / 专项档案 / v9 表格 | **待用户编译真机验证** |

---

## 5. 本轮（批次 22 / 第十七轮）到底改了什么

**先说结论**：第十六轮的机制**确实接进了运行时**（已核对 `PlayerSyncController` → `MotionDriveLoop` → `ClassicBluetoothTCodeOutput.sendNow()`，
zip 内 `TCodeRuntime.kt` / `PlayerSyncController.kt` 时间戳虽旧但内容已委托新循环）。
所以"现象和之前一样"**不是"新代码没生效"**，而是新代码的输出字节与设备预期仍不匹配。

逐行核对 MFP 源码后发现 3 个真实代码级原因 + 1 个可观测性缺陷：

| 编号 | 真因 | 证据 |
|---|---|---|
| **R1（首要）** | **批量格式不同**：我方一轴一行、每行 `\n`、每行带 `I`；MFP 是**所有轴空格拼成一行 + 一个 `\n`** | MFP `DeviceAxis.cs:51-52`：`values.Aggregate(..., $"{s} {ToString(x.Key, x.Value, interval)}") + "\n"`；`SerialOutputTarget.cs:210-214` 一次 `serialPort.Write(commands)`。TCode 固件按行分帧，6 行 × I 会排队 6 倍时间，主机 20ms 一批 → 队列无限积压 |
| **R2** | 第十六轮把 `I` 间隔 / dirty-only **默认全开**；固件不认 `I` 会丢帧，dirty-only 在脚本恒定段（历史日志有 7.49→8.60s 的 `L05000` 平台 40 条）会长时间完全不发 → 设备超时保护停机。且**覆盖安装时 SharedPreferences 恢复旧 true**，改默认值不生效 | `TCodeFeatures.install()` 用 `sp.getBoolean(key, fallback)`；第十六轮已把 true 落盘 |
| **R3** | `isPlaying()` 每帧读 `core-idle` / `idle-active`，mpv 在音频/seek/轨道切换时瞬时 true → 整帧不发 | `MpvTimeProvider.kt` 原实现 |
| **R4** | 面板看不到**实际发出的字节**，无法区分「APP 没发」/「发了格式不对」/「发了设备没吃」 | `TCodeRuntime.sendStats()` 只有统计数字 |

**改动清单（9 个文件，全部自有包，上游 md5 未变）**：

| 文件 | 改动 |
|---|---|
| `core/tcode/TCodeCommandFormatter.kt` | 新增 `buildBatch(sample, intervalMs, singleLine)`；2 参数版本默认 **MFP 单行空格**；1 参数版本保留旧一轴一行（手动调试/历史基线/C17）；`I` 用真实帧间隔 |
| `app/TCodeFeatures.kt` | `TCODE_INTERVAL` / `DIRTY_ONLY` 默认改 **false**；新增 `SINGLE_LINE`（默认 true）、`HEARTBEAT`（默认 true）；`install()` 加 `__pref_version=2` 一次性清旧值 |
| `app/MotionDriveConfig.kt` | Snapshot 加 `singleLine` / `heartbeatMs`（都带默认值，兼容 C28 的位置参数构造）；默认组合 = 单行 + 无 I + 全量 + 心跳 + 50Hz |
| `app/player/MotionDriveLoop.kt` | 走单行格式、删 `+4ms` 余量、**静默心跳**（>60ms 无指令强制补一帧全量）、Stats 新增 `lastFrame`/`heartbeats`/`silentTicks`/`singleLine`/`threadName` |
| `app/player/MpvTimeProvider.kt` | `isPlaying()` 只用 `pause` + `hasMedia()`，去掉 `core-idle`/`idle-active` |
| `app/TCodeRuntime.kt` | `sendStats()` 显示**首行字节样例**、格式、心跳/静默帧、线程名 |
| `app/player/PlayerSyncController.kt` | 注释同步 |
| `app/ui/framework/FrameworkCheck.kt` | C28 增加多轴单行格式断言；**自检仍 29 项** |
| `core/tcode/.../TCodeCommandFormatterTest.kt` | 双轴断言改单行；新增单行/回退对照单测 |

**默认配置的取舍说明（重要）**：MFP 默认 `OffloadElapsedTime=false`（带 I）、`SendDirtyValuesOnly=true`。
本轮**没有照抄这个默认**，而是选了 MFP 也支持的 `OffloadElapsedTime=true + SendDirtyValuesOnly=false` 等价配置：
每帧全量、连续喂、格式与 MFP 完全一致，**不依赖设备固件是否智能解析 `I`**。
理由：用户设备固件能力未知，且 4 份已验收的串口日志都是"每帧全量、无 I"。
三个开关（I / dirty-only / 单行回退）在 ⑤ 里逐项可开，专门用来做 A/B 判读。

**验证状态**：静态校验通过（288 个 Kotlin 文件扫描，4 处告警全部落在上游原文件且 md5 与第十六轮一致，属简易解析器对三引号/嵌套注释的误报；
9 个改动文件的关键符号引用、29 项自检计数、格式断言逐条核对）。
**未编译、未真机验证** —— 这一关必须由用户完成。

---

## 6. 术语对照表（用户明确要求统一，别再用旧叫法）

| 统一叫法 | 含义 |
|---|---|
| **PC 端 XCOM** | 电脑上看字节流的串口助手（不要再叫"串口助手"泛指） |
| **蓝牙模块** | HC-05 / TCodeESP32 等 SPP 透传模块 |
| **设备主控** | 真正产生动作的那块板子（用户口中的"目标设备"） |
| **SPP 链路** | 手机 ↔ 蓝牙模块之间的 RFCOMM 字节流，**没有波特率概念** |
| **模块 UART 参数** | 蓝牙模块 ↔ 设备主控之间的速率（9600/115200），由模块 AT 指令配置，APP 既设不了也读不到 |
| **④ 已配对设备** | 联动页里的设备列表卡片 |
| **⑦ 手动调试** | 滑块 + D1/D2/DSTOP + 断开 + 回显 + CSV |
| **③ 已加载脚本** | 显示实际文件名/生效轴/轴的来源/格式标签/字幕 |
| **播放状态条** | 播放画面左上角可拖动的可折叠条 |
| **历史播放** | 列表页（原「最近播放」tab，内容保留，入口移到 FAB 第 4 项） |
| **上次播放** | 动作（FAB 第 3 项，点了直接续播上一个媒体）—— **两者不可混淆** |
| **网络映射** | 原「网络」tab，SMB/FTP/WebDAV |
| **联动** | 底部第 3 个 tab（原「TCode」，图标为双向同步箭头） |

---

## 7. 测试体系（接手后怎么验）

### 7.1 APP 内自检 C01–C29（零设备，入口在「联动」页顶部）

`FrameworkCheck.kt` 里 29 个 `CheckItem`，**调的是工程里真实的 Kotlin 类**，不是网页模拟。
重点项：C06（6 轴白名单）、C08（大小写规则）、C16（暂停/恢复）、C20（输出注册表）、
C21–C27（AxisProcessor/热力图/超时）、C28（专用时钟 + 单行格式）、C29（退后台开关）。
每轮改动后**必须**跑「▶ 全部运行（共 29 项）」，目标 `29/29 PASS`。

### 7.2 外部验收表 `TCode逐项测试填写表_v9.xlsx`

三个 sheet：**当前测试 / 归档记录 / 弃用**。
只填 F 列（下拉选 `PASS / FAIL / 部分 / 不适用 / 待回归`）。
G 用户原话 / H 截图 / I 判读与下一步 / J 判定来源 四列已拆开，**不要把多轮判读堆进备注**。
用户明确要求：**已完全 PASS 的项归档即可，弃用的单独放，不要混在当前测试里**。

### 7.3 真机串口日志的判读口径（非常容易误判）

- 用户链路：`安卓 APP →蓝牙 SPP→ 蓝牙模块 →USB 串口→ PC 端 XCOM`
- XCOM 的时间戳是**接收缓冲分组时间**（块间隔常呈 15.6ms 整数倍，块内 1~10 批无逐行时间），
  **不能拿它算 50Hz**。这是 V50 一直判不了的原因。
- 有效证据只有两种：XCOM 改「每行到达打毫秒时间戳」，或 **APP 侧 `SendTimestampLog` 导出 CSV**
  （⑦ 面板「刷新统计 → 复制 CSV」，列 `seq/wall_clock/elapsed_ms/delta_ms/batch`）。
  用户已明确选择「在 APP 加导出」，第十四轮已实现，**别再建议改 XCOM**。
- 已判读过的 4 份日志：1093 批 / 1490 批 / 1524 批 / 2065 批（47.002s，6 轴 12390 行，
  4182 次逐轴比对 99.98% 差=0、100% 差≤1）。数值层**从未出过错**，别再去改插值。

### 7.4 测试素材

- `测试素材/` 与 `音频测试素材.zip`：`song.wav` + `song.funscript` + `song.L1.funscript`（原 `V0` 已改名，因 6 轴白名单）+ `song.lrc`
- `多轴拆分素材.zip`：分轴三文件
- 脚本末尾 tail 在 **120.152s**，V41（末尾 +1s DSTOP）需播满 2 分钟

---

## 8. 已知问题台账（都没做完，别当成已完成）

| 编号 | 内容 | 状态 |
|---|---|---|
| **P01** | 播完自动退出（mpvEx `closeAfterReachingEndOfVideo`） | 已加 `KEEP_PLAYER_ON_EOF` 拦住两处 `finishAndRemoveTask()`；无 logcat 佐证，不宣称彻底修复 |
| P02 | 播放页看不到联动状态 / 切 tab 割裂 | 已加可拖动播放状态条；**③ 关键信息并入状态条未做** |
| P03 | 自检页上下遮挡 | 已加 insets + 底部留白；V53/V54 已 PASS |
| P04 | 网络媒体字幕行写「（无）」 | 已改「由播放器按同名规则自动匹配并加载」 |
| P05 | 播完不退出后脚本保持末点 | 由脚本末尾 +1s DSTOP 规则兜底；未真机验 V41 |
| P06 | 直接杀进程不发 DSTOP | Android 不保证生命周期回调，判为预期，不处理 |
| P07 | 热力图游标点击 seek 与单指平移冲突 | 待设计 |
| P08 | 主页音视频分类 / ③ 并入播放状态条 | 待做 |
| P09 | FLAC 内嵌歌词 | 待做 |
| P10 | UI 精修（用户：「UI 仍需细调，精修时再搞」） | **用户明确说先记下，暂不动** |
| **R01** | 网络盘（SMB/FTP/WebDAV）同名 `.funscript` / `.lrc` 探测 | **未实现**，卡片如实写「探测暂未启用 · R01 待开发」；抽象层 `NetworkDirectoryListing` 已在 |
| V50 | 50Hz 严格验证 | 待 APP 侧 CSV 导出后判读（用户选此路） |
| V41/V42/V43 | 脚本末尾超时 DSTOP / 速度限制 / 行程限幅反转 | 逻辑与自检已过，未真机逐项验 |
| V70 | USB CH340 真机 | 用户暂时没条件 |
| V26/V27/V28/V29/V37 | FTP / WebDAV / 网络脚本降级 / 底座冒烟 / 字幕文案 | 未测 |
| 工具链 | `material3 1.5.0-alpha15` 无 stable | 无解，等官方 1.5.0 stable 或改 20+ 上游文件 |

---

## 9. 编译步骤与历史踩坑（用户环境专用）

### 9.1 标准编译（用户已跑通多次）

1. **解压到全新空英文目录**（如 `Desktop\TCodePlayerMPVEx_New`），**绝不覆盖旧目录**
2. Android Studio → `File → Open` 选该目录 → 等 Sync
3. `Build → Clean Project → Rebuild`（清增量缓存）
4. `Build → Generate App Bundles or APKs → Build APK(s)` → 弹窗点 `locate` 拿 APK
5. 装手机 → 「联动」页顶部「逐项自检」→ 全部运行

### 9.2 历史踩过的坑（别再重复诊断）

| 现象 | 真因 / 解法 |
|---|---|
| `non-ASCII characters in project path` | Windows 路径含中文；包已全英文 + `android.overridePathCheck=true` |
| Gradle 下载超时 `services.gradle.org` | 已换腾讯云 9.4.1 + 阿里云依赖；别让用户点 Try Again |
| `plugin already on classpath with unknown version` | 子模块重复写 `version`；已统一版本目录 alias |
| `org.jetbrains.kotlin.android no longer required since AGP 9.0` | AGP 9 内置 Kotlin；Android 模块不要声明该插件（纯 JVM 的 `:core:*` 仍用 `kotlin.jvm`） |
| `Latest supported version is AGP 8.7.2` | 旧 Studio；用户已升级到 Rabbit 2 2026.2.2，**不要建议改 agp 版本号** |
| `BuildConfig.java:16 需要';' ... fatal: not a git repository` | 从 zip 解压无 `.git`，Git helpers 把 stderr 混进 stdout；已判退出码 + 回退 0 |
| `Unresolved reference 'TCodeFeatures'`（65 个级联错） | ① KDoc 里写 `` `core/*` `` 造成 Kotlin 嵌套块注释未闭合 ② C20 缺跨包 import。**全量文本覆盖 + md5 校验**已成常规 |
| 自检按钮点不到 | 埋在长页最底且被悬浮导航条压住；已移到顶部 + `navigationBarsPadding()` |
| 覆盖解压后代码仍是旧版 | 用户机器出现过两个目录（`DZ_Mi` / `Felix`），务必强调「新目录 + Clean/Rebuild」 |
| `Start AGP Upgrade Assistant` 提示 | **别点**，会改版本目录弄乱已验证工具链 |

---

## 10. 给接手者的最小路径

1. **先读** `卡顿问题_专项排查档案.md` —— 这是当前唯一阻塞问题，里面有证据链、假设表、A/B 判读流程。
2. **不要重写工程**。用户已经因为「重复造轮子 / 原地转圈」表达过不满；保留现有结构，一次只动一项。
3. 拿到用户第十七轮编译结果后，**先要 ① 卡片的「首行样例」截图**：
   - 若样例仍是 `L0xxxx\n` 一轴一行 → 包没换 / 没重编 / Gradle 增量缓存，属于构建问题，不是算法问题
   - 若样例已是 `L0xxxx L1xxxx R0xxxx …`（空格一行）→ 新代码生效，进入下一步
4. 再要 **⑦ 面板导出的 CSV**，看 `delta_ms` 分布：
   - 若 `delta_ms` 有 >100ms 的洞 → APP 侧没发（查 `time-pos` / 线程被抢占 / `isPlaying`）
   - 若 `delta_ms` 均匀 ~20ms 但设备仍卡 → **问题在设备固件侧**（`I` 解析、行缓冲、超时保护、串口波特率与字节量不匹配），
     此时不要再改驱动算法，应改「输出字节形态」或让用户提供固件文档
5. 按专项档案的 A/B 顺序逐个打开 ⑤ 里的开关（单行回退 / I 间隔 / dirty-only / 心跳 / 100Hz），
   **一次只改一个变量**，每改一次让用户体验 30 秒。
6. 卡顿解决后再回到 V41/V42/V43、R01、P07–P10、UI 精修。
7. **底座升级**：等中文版发新稳定版时，按 `上游接缝台账.md` 的 16 处逐条重放，
   顺序：拉 tag → 覆盖 upstream → 重放 16 处 → 拷回 overlay → Sync/Rebuild → C01–C29 → 真机回归。
   用户要求「便于升级」就是靠这份台账，不是靠现在重构工程结构。

---

## 11. 交付包内文件清单（本轮新增/更新）

| 文件 | 作用 |
|---|---|
| `HANDOFF_项目交接.md` | **本文件**，自包含交接总览 |
| `卡顿问题_专项排查档案.md` | 当前阻塞问题的完整证据链与排查路径 |
| `CHANGELOG_详细更新日志.md` | 批次 01–22 逐批改动清单（含逐文件） |
| `第十七轮_修复说明.md` | 本轮简明说明与喂饭验收步骤 |
| `TCode逐项测试填写表_v9.xlsx` | 当前测试 / 归档记录 / 弃用 三 sheet，新增 V95–V99 |
| `上游接缝台账.md` | 16 处上游改动，升级用 |
| `第X轮_*说明.md`（第 10–16 轮） | 各轮用户原话、判读、修复记录 |
| `本轮实测判读.md` / `第十一轮_串口日志判读与补测结论.md` | 4 份串口日志的数值判读 |
| `README_CN.md` / `MODULE_MAP.md` / `SPEC_2_0.md` / `2.0方案对照与依赖核查.md` | 模块地图、方案对照、依赖 Stable 核查 |
| `测试素材/` `音频测试素材.zip` `多轴拆分素材.zip` | 复验素材 |
| `历史测试表格/` | v1–v8 全部历史表格（保留编号避免对不上） |

---

## 12. 三条最容易犯的错（给接手 AI 的忠告）

1. **不要凭"我觉得"改**。本项目前 41 批次里多次出现"声称改了但包里没同步"、
   "改了配置但 SharedPreferences 恢复旧值"、"加了新类但没接进运行时"，
   每次都是用户真机反馈才发现。**改完必须核对 zip 内容 / md5 / 调用链**。
2. **不要动数值层**。`pos÷100`、四位幅度、clamp、6 轴白名单已被 4 份串口日志反复验证。
   卡顿是**时序与字节形态**问题，不是插值问题。
3. **不要一次甩十几个文件**。用户明确要求「先搭框架，再单独一项一项测试功能」。
   每轮只解决一类问题，改完给一个可截图判定的验收项。
