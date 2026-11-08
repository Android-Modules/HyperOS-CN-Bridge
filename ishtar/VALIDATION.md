# ishtar 预览配置验证记录

验证日期：2026-11-07（开发机时区）。作者：dalong。此记录针对当前连接的小米 13 Ultra；不包含个人账号、设备序列号或原始日志。

## 环境与产物

- 设备：`ishtar`，EliteRomLite v4.05，`OS3.0.308.0.WMACNXM`，Android 16 / SDK 36。
- Root：KernelSU；挂载元模块：Magic Mount-rs 4.0.11。存在用户 0、10、999，未删除任何用户或双开数据。
- 来源：同机型同版本官方 Recovery ROM。整包 MD5 `e3d01be14a3cf95d8e1171a1a2a28eb9`；SHA256 和 31 个构建输入见 [锁文件](rom-inputs.lock.json)。
- 初次补全产物（3.2.0）：`ishtar-cn-addons-OS3.0.308.0.zip`，SHA256 `21c13eba861a9c89f8365ca3c78b06bd6ff974e1fbc5b00178c393b0633fe801`。后续修复产物见下方记录及安装包旁的 SHA256 文件。
- 代码、ROM 输入校验文件和文档提交到 Git；ROM、APK、安装包、手机数据备份及原始诊断仅保存在本地忽略目录。

## 构建与回归

| 项目 | 结果 |
| --- | --- |
| 9 个官方 APK 包名、原始证书与哈希 | 通过 |
| 31 个 ROM 输入锁定、ZIP 内文件哈希、CRC | 通过 |
| Java 控制器编译与 D8 转换、Shell / Python / JS 语法 | 通过 |
| ishtar WebUI：默认全开、删除/保留/取消、手动重启、失败优先显示、原生图标 | 通过 |
| WebUI：9 个开关、11 组只读 ROM 应用、360/393/850 宽度、深色模式、无 JS 异常 | 通过 |
| songyuan 原配置构建、原 WebUI 回归 | 通过 |
| ROM 输入校验的 5 项单元测试 | 通过 |
| 首次补装拒绝覆盖已登记包 | 真机只读探针通过；在展开文件前拒绝已有包 |

共享控制器按模块 ID 和配置类型隔离状态；ishtar 分支停用 songyuan 的 NFC UID、桌面和下载数据库迁移，沿用现有离线校验及事务流程。

## 真机调试

1. 使用 KernelSU 安装后，第一轮重启发现 product 组件未挂载。定位到已有提示音模块的目录软链接抢占 product 节点，规范目录后第二轮重启成功加载全部 9 个组件。提示音模块保留，262 个音频文件的 SHA256 一致。
2. 检查主题发现不能复用 songyuan 的独立商店属性。删除该属性后，原主题管理器的在线首页恢复。调试期间产生的主题入口禁用状态已恢复为安装前的默认值。
3. 修正后的最终 ZIP 再次通过 KernelSU 安装。主题入口状态经确认落盘后，额外进行一次完整重启复核；本轮共主动重启四次。最终核验结果见下表。

| 项目 | 结果 |
| --- | --- |
| 默认 9 个补全 APK | 全部加载 |
| 最终包与手机文件 | 180 个运行文件及组件文件哈希一致；私有离线源与最终 ZIP 一致 |
| CN 地区 / cust variant / mod_device | `CN` / `cn` / `ishtar` |
| 原有 21 个应用 | APK 哈希、包路径、UID、共享 UID、系统登记证书保持一致 |
| 多用户环境切换组件 | 明确拒绝；期望配置与已应用配置哈希均未改变 |
| KernelSU 中直接打开 WebUI | 成功，显示“已应用”，实际读取应用图标 |
| 小爱发现页 | 成功加载国内技能和在线内容 |
| 主题 | 原管理器在线首页与本地主题、壁纸设置可打开；桌面入口恢复 |
| 云服务 | 现有账号可读取云端项目和容量；未执行账号退出或数据恢复 |
| 相册、日历、时钟 | 启动正常，保留原包，未执行清数据或数据迁移 |
| 传送门、快应用框架 | 设置页面可打开 |
| 最终冷启动后的应用检查 | 上述启动检查通过，崩溃缓冲区未发现 Java 致命异常或原生崩溃记录 |

## 3.2.1 应用商店下载 / 安装修复

- 原因：现有商店位于 `/data/app`，系统未授予 `android.permission.INSTALL_PACKAGES`。商店创建的安装会话收到 `STATUS_PENDING_USER_ACTION=-1`，接收器未启动确认界面，将其记为错误 13。修复前采集中多个用户应用和系统应用均出现相同回调；下载本身存在成功完成的记录。
- 修复：仅给 `com.xiaomi.market` 添加一项平台签名权限白名单，归属 CN 配置。共享控制器为 ishtar 配置文件增加离线源哈希校验、事务展开和启动后的实际挂载校验。保持原 APK、UID、签名校验与下载数据库；不修改下载并发、温控保护或网络 DNS。
- 新产物：3.2.1-ishtar-preview，SHA256 `31f09003f3c203235e68bc76c4e577f0526491eb2b848afe22f7a2844307c770`。通过 KernelSU 完整升级，181 个运行文件及私有离线 ZIP 校验一致，运行状态为“已应用”。
- 真机本轮主动完整重启两次：首次验证权限授权，再用完整安装包验证升级与挂载。系统回读 `INSTALL_PACKAGES: granted=true`；联通云盘、黑猫投诉、无忧行、迅雷等 6 个会话安装成功，最终安装包重启后再次验证爱奇艺安装成功；对应采集内没有新的失败回调。
- 原队列约 191 项。最后 30 秒 Wi-Fi 收流量的 6 个采样区间为 3.74、5.07、5.93、2.43、3.01、3.52 MB/s，均值约 3.95 MB/s；此为整机 Wi-Fi 流量，不能全部算作商店净下载速度。下载线程也曾采样到约 3.2 MB/s，许多“等待中”任务尚未开始下载。最后更新列表仍有约 173 项，未等待整个批量更新完成。
- 安装权限修复不等于网络带宽提速。旧的失败任务记录可能仍显示在列表中，需要通过商店重试，不能把历史失败记录当作新版本仍失败的证据；本轮未批量清除任务或应用数据。
- 构建、9 个原签名 APK 校验、ZIP 哈希 / CRC、Java 编译与 D8、WebUI 原有回归、XML 单包单权限检查、`git diff --check` 通过。多用户下未实际切换 CN 开关，仍遵守原有组件切换限制。

## 3.2.2 WebUI 重设计

- 内置 imagegen 生成浅色 / 深色视觉参考，使用本地 HTML / CSS / JavaScript 落实；未增加运行时依赖。组件与系统应用分为两个页面，技术信息放入详情，固定保存栏显示未保存变更数。
- 保留真实应用图标、9 个开关、11 组只读 ROM 功能、默认删除 / 可选保留、显式保存及手动重启语义。沿用控制器和 Root 桥接命令；渲染、详情与状态处理分别封装，避免在卡片中重复拼接版本说明。
- Playwright 通过：默认值、取消 / 删除 / 保留、错误优先级、图标、双开保存拒绝、拒绝后草稿保留、切页草稿保留、键盘切页、详情关闭焦点恢复、离线源缺失、连接失败后重试、320 / 360 / 393 / 850 像素宽度和深色模式；无 JavaScript 异常。
- 完整构建通过 APK / 原签名 / 输入哈希 / ZIP CRC 检查。产物 SHA256：`47389cf895ad82edd783499ad060da519c40689ec3fc3e1a78a7c68a8b819274`。
- 与手机已装 3.2.1 的 ZIP 逐文件对比，仅 3 个 WebUI 文件、2 份 catalog 的版本字段和 module.prop 有差异；APK、权限 XML、原生库、脚本及 repair.jar 均一致。因此只热更新这 6 个文件与私有离线源，无需执行系统组件安装或重启。
- 真机 KernelSU WebUI 中检查首页、系统应用页、组件详情、关闭确认与取消操作，显示正常。取消后保持 9 项全开，没有提交开关变更；多用户拒绝等状态由浏览器模拟测试覆盖，没有绕过真机限制。
- 更新后 181 个运行文件及离线安装包与构建产物逐项校验一致，版本为 `3.2.2-ishtar-preview`，`pending=false`、`applying=false`，状态为“配置已生效”；desired / applied 哈希、boot ID 与 system_server PID 均保持不变。
- 手机旧界面和旧离线安装源备份：`/data/adb/ishtar_cn/webui-backup-3.2.1-20261107/`。本机测试截图与验证记录在 Git 忽略目录 `diagnostics/webui-redesign-20261107/`；设计稿和提示词在 `ishtar/design/`。

## 3.2.3 重启复测与启动状态修复

- 发现并修复：没有待应用事务的普通重启会跳过 `late` 检查，WebUI 可能继续使用上次启动的成功结果。现在每次启动解锁后，校验已启用组件的包登记、基础 APK / 原生库 / 权限文件哈希及地区配置，并记录当前 boot ID；旧成功结果显示“等待启动校验”。校验逻辑集中复用，仅为 ishtar 增加每次启动检查，保留 songyuan 的原有无事务行为。
- 更新为 `3.2.3-ishtar-preview`，产物 SHA256：`7f73f6167737c8df35dd862b4df8ed479dd3a2e19efbffae0e97bd7803fde9bd`。与 3.2.2 对比仅 module.prop、两份 catalog、repair.jar、app.js、style.css 共 6 个文件变化。备份旧文件后更新这 6 个运行文件与私有离线 ZIP，再主动完整重启一次，专门验证没有安装事务的普通启动路径。
- 真机重启后自动完成检查：结果 boot ID 与本次启动一致，`bootVerified=true`、`pending=false`、`applying=false`。181 个模块运行文件、159 个实际挂载文件与安装包一致，私有离线 ZIP 哈希一致；`.replace` 是挂载管理器标记，不要求出现在实际系统目录。期望配置与已应用配置哈希保持不变，仍为 9 项全开。
- 真机 KernelSU WebUI 显示“配置已生效”和“9 / 9 项配置开启”，图标正常，无需再次保存配置。小爱语音、小爱服务、银联组件已存在商店更新；系统仍能识别这些更新，同时基础模块挂载校验通过。
- 相册、日历、时钟、小爱、云服务、主题、钱包、智能卡、传送门、快应用框架和应用商店入口检查通过。时钟首次使用说明确认后可进入闹钟页，未改动闹钟；云服务现有账号可读取容量和项目，主题与小爱在线首页正常。应用商店的 `INSTALL_PACKAGES` 授权重启后仍有效，更新列表显示全部应用均为最新版本；本轮没有新增下载任务，因此没有重新测量下载速度。
- 新增真机只读回归探针 `tools/BootValidationTest.java`，覆盖普通启动、旧 boot ID、缺失包、错误挂载哈希、缺失权限文件、未完成事务、地区错误、缺失已应用配置和 songyuan 隔离。测试仅在新的 `/data/local/tmp/ishtar-boot-test-*` 目录写入模拟状态，不改动真实配置或系统文件。全部通过；两个配置的 WebUI 回归、ROM 输入校验 5 项测试及完整构建校验通过。
- 本轮未发现上述应用新增 Java 致命异常或 ANR，system_server 在复测期间保持稳定。但发现独立的 vendor 启动异常：`poweroffm64` 在启动时收到 SIGABRT；`vendor.modemManager` 反复记录 `libminijail: blocked syscall: rt_tgsigqueueinfo` 并以 SIGSYS 退出，约每 5 秒被 init 拉起。模块未提供或修改这些 vendor 服务和 seccomp 策略，尚未通过停用模块对照确定因果，不能据此判断整个系统无异常。没有停用该服务或放宽其沙箱策略。
- 手机旧版本备份：`/data/adb/ishtar_cn/bootcheck-backup-3.2.2-20261107/`；本机原始日志、截图、部署及探针结果保存在 Git 忽略目录 `diagnostics/ishtar-reboot-check-20261107/`。

## 3.2.4 modemManager 修复与状态图标

- 同版本官方 OTA 提取 vendor 并校验；QESDK 涉及的六个二进制 / 库与手机一致，两个文本文件不同：rc 多三个尾部换行，policy 两处版权注释被改写。八个官方文件哈希均能在 modemManager 内置校验值中对应。
- 故障链：文本哈希不符 → `Qesdk env is not ready(1)` → 服务注册失败 → `Scudo ERROR: invalid chunk state` → abort 的 `rt_tgsigqueueinfo` 被 seccomp 拦截 → init 每五秒重启。先只覆盖 rc 时，失败点转向 policy；再恢复 policy 后，服务认证、注册并进入 READY。临时验证保持同一进程数分钟，5G SA 注册正常。
- 持久修复位于 `compat/` 与 `qesdk-compat.sh`，由 post-fs-data 执行；仅已知旧文件可被覆盖，恢复的都是官方原始字节。两个挂载为只读，未改变沙箱规则或 `persist.vendor.qesdkenv_verify`。正确输入验证通过，损坏 payload 被拒绝且未改动挂载；启动检查增加两份实际文件哈希验证。
- 3.2.4 通过 KernelSU 完整升级后，主动重启一次。启动 ID 更新，自动挂载两份官方文件，模块状态 `bootVerified=true`、`pending=false`、`applying=false`。modemManager 自开机保持同一进程，5G SA 重新注册成功。原 `poweroffm64` 的单次启动异常不属于本项修复的验证结论。
- 原桥形标识改为状态符号：绿色圆形勾表示已生效，时钟表示等待，双箭头表示读取 / 应用，感叹号表示失败。颜色和形状同时变化，辅助技术仍读取相邻文字状态。既有 WebUI 流程、错误与等待图标联动、320–850 像素宽度及深色模式回归通过。
- 原始比较、临时对照、安装和重启证据位于 Git 忽略目录 `diagnostics/ishtar-modem-fix-20261107/`。之前 3.2.3 关于 vendor 循环退出的待查项由本节补充闭环。

## 3.3.0 按应用独立适配

- 新增 Java-only LSPosed API 102 配套应用，作用域严格限定为主用户 0 的 22 个应用包，归属 19 个独立开关。系统框架、账号、推送、桌面、下载器及其他用户均不加入作用域。
- 取消全局 CN 切换，恢复 Elite ROM 的 HK / hk / ishtar_elite_global 基线。在所选进程内适配 5 个地区属性与 MIUI / MIUIX Build 标记。应用包、UID、签名和原有数据不替换。
- 配置通过 root-only ContentProvider 写入框架远程偏好，按系统 BOOT_COUNT 区分当前和下次启动。保存不改变本次进程选择；启动前保存的配置在新进程载入时即可读取，避免启动完成后强停时钟等原生应用。
- 配套服务曾被 Thanox 的新应用自启动阻止和后台清理默认规则拦截，已仅解除 com.dalong.cnbridge 的两项限制，并允许其 MIUI 后台自启动。其余既有应用启动规则保留。
- 实际重启“仅日历开启、其余保留应用”后，boot ID 为 51ab238a-ac36-485f-a759-8896b2c1151b。日历读取 CN / ishtar / international=false；时钟、负一屏、云服务等未选进程读取 HK / ishtar_elite_global / international=true，全局保持 HK。desired / applied 一致，启动校验通过。
- 逐项进程测试：每次配置仅一个功能，重新启动该功能对应的应用进程，并以日历或时钟为未选对照。19 项、22 个目标包均获得所选 CN / 对照 HK 证据。快应用先被既有后台启动限制挡住，改用其公开设置页前台启动后通过；没有修改其原有启动规则。
- 这里的逐项通过是**作用域、地区判断和进程启动检查**。无桌面入口的组件以 Provider 类型读取触发初始化，不读取业务数据；部分 Provider 不实现 getType 而返回异常，该异常在测试命令中结束，不作为业务功能验收。未实际支付、开卡或发送短信。
- 应用商店关闭后，签名权限 XML 挂载不存在，INSTALL_PACKAGES 不再显示 granted=true。它已从旧 CN 总开关独立归属商店开关。
- WebUI 的 19 项逐个“全部关闭→仅开启一项→保存”均通过；原生应用关闭不出现卸载对话框。仍覆盖取消、保留、默认删除、作用域缺失、草稿保留、启动状态、320–850px、深色模式及真实图标。
- ScopedValidationTest 实机只读回归通过：19 项互不重叠、全关为空、配置字段与类型校验、缺失作用域拒绝、修复文件哈希与缺失包检测、JSON 键顺序不影响配置版本。
- 第二轮重启实际测试“仅日历开启、其余补装组件从主用户卸载”：主用户包数由 932 变成 923，差集恰为 9 个补装 APK；用户 10 的 85 包、用户 999 的 32 包完全一致。9 个应用的 CE / DE 数据目录 inode、UID、GID 保持不变。
- 发现并修复卸载后的重新开启问题：LSPosed 会删除这些应用的作用域，仅 install-existing 无法恢复。现在允许暂存已卸载补装项，重启恢复安装后经 libxposed requestScope 请求作用域；缺少授权时显示“等待作用域授权”，保留事务并提供重试。实际允许 9 包请求后，通过真机 WebUI 的重试按钮完成校验，22 包作用域与 19 项全开回读一致。启动时通知未出现的场景可再次请求或在框架中勾选推荐应用，WebUI 已给出说明。
- 按用户要求恢复全部开启，启动 ID 为 a1635f5d-92ad-418c-8d07-7432df225b32，pending / applying / waitingScope 全为 false，bootVerified / companionReady 为 true。主用户恢复 932 包，三个用户包列表和 9 个组件数据目录均与卸载测试前一致；商店 INSTALL_PACKAGES 重新 granted=true，全局仍为 HK。
- 恢复后的 22 包再次取得 CN / ishtar / international=false 证据。Thanox 新应用模板会拦截重装组件的后台 Provider 启动；仅测试期间临时解除相应启动、后台限制，随后逐项恢复，23 个检查包规则前后完全一致。已有进程的启动日志可能滚出缓冲区，因此缺少日志不直接判定适配失败，复测会重新启动目标进程。没有将国内化开关绑定为自动解除后台限制。
- 新增 WebUI 作用域等待、再次请求 / 校验、未出现通知提示的回归，全部通过；最终 ZIP SHA256 为 62a4db1a853534a1c45a8417e81ee978bd9858f86ddd5c269c468b1ee052e3c5。186 个运行文件、配套 APK 与离线源逐项匹配；customize.sh 由安装器执行后移除，不作为运行文件缺失。
- LSPosed 开发者模式已恢复关闭，禁用安全模式和 Hook 调试保持原样。普通重启发现一次服务绑定时序问题：用户已解锁时，配套应用仍可能尚未取得 LSPosed 服务；稍后只读检查已就绪，但原先单次启动检查留下错误状态。service.sh 增加有次数上限的 ready 预检，仅查询服务，等待成功后再进入配置事务；连续失败也会结束等待并报告检查结果。模拟立即成功、前两次失败及持续失败三种路径通过。
- 修复启动时序后再次普通重启，boot ID 为 bfaf63b0-710f-44d0-bf40-680989a1f456；不手动执行 late 即自动完成检查。19 项全开，pending / applying / waitingScope 为 false，bootVerified / companionReady 为 true；只读回归再次通过，186 个运行文件、配套 APK 与最终离线 ZIP 全部匹配。
- 最终真机 WebUI 的补全组件页、系统应用页均显示绿色圆形勾、“配置已生效”和 19 / 19；独立开关与当前应用图标正常。最后启动中 modemManager PID 持续为 6422，观察到进程运行 2 分 24 秒，采集日志无该服务退出或重启记录；两份官方 QESDK 文件哈希一致。
- 完整原始证据在本地忽略目录 diagnostics/ishtar-modem-fix-20261107：solo-matrix.json、hybrid-retest.json、status-330-calendar.json、scoped-regression.txt。

## 3.3.1 生产版：相册小米云入口与同步修复

- 复现：ROM 自带相册为 4.3.1.18-global，应用内 BuildUtil.isGlobal() 固定返回 true，地区属性和 miui.os.Build 标记变化不足以切换其设置页。现在仅在主用户相册适配开启、机型 / 系统版本 / 挂载标记通过检查时拦截该判断；取消选择相册后不安装相册专用 Hook。
- 另一处阻塞是系统相册 authority 的 syncable=0，而用户自动同步已开启。配套 APK 1.0.0 只在 getSyncAutomatically=true 时将该账号、该 authority 的可同步登记恢复为 1，不修改自动同步开关、WLAN / 下载选择或照片数据库。控制器拒绝旧配套 APK，避免仅升级 ZIP 后误报完整适配。
- 真机界面对照：关闭相册适配后显示 Google 相册备份；恢复开启后显示小米云空间、相册云同步及“已开启”。“立即开启”进入小米云确认与设置，没有再跳转 Google。全局地区始终保持 HK。
- 系统同步统计显示相册 syncable=1、automatic=true，并记录 11 次成功、0 次失败，首次云端拉取约 217 秒；同步日志没有 ERROR / Exception。相册照片列表和缩略图已实际显示。此结果验证了当前账号的同步链路，不等于所有原片都已下载。
- 新一轮逐项复测：19 个功能各自单独开启，22 个包均取得 CN / ishtar / international=false 日志；每项同时验证未选中的日历或时钟保持 HK / ishtar_elite_global / international=true。恢复全开后 22 包再次通过。测试期间临时调整的 22 包 Thanox 启动规则已逐项恢复，前后回读一致。
- 两套 WebUI 回归、ROM 输入校验 5 项测试、两个完整模块构建、原签名 / 输入哈希 / ZIP CRC 检查通过。ishtar 从干净的 CI 归档重新解包并构建，31 个输入均匹配锁文件。
- 生产 ZIP 3.3.1-ishtar 已通过 KernelSU 完整安装并重启，配套 APK 为 1.0.0。新 boot ID 为 f365e27e-f10c-472b-986b-2ff04f175ac5；自动校验后 19 项全开，bootVerified / companionReady=true，pending / applying / waitingScope=false。ScopedValidationTest 再次通过，包含旧配套 APK 拒绝检查。
- 真机 KernelSU WebUI 已显示绿色圆形勾、“配置已生效”和 19 / 19，独立开关与当前应用图标正常。
- 安装后 186 个运行文件、私有离线源和配套 APK 共 188 项 SHA256 匹配；customize.sh 由安装器执行后移除。生产模块 SHA256：ed4f1f6d32d3717b45fa4a0e450c732db11b3e2b6d14bfcafe54d9f4e35f83bb。应用商店 INSTALL_PACKAGES 仍为 granted=true。
- 生产包启动后实际检查日历、时钟、主题在线页、商店首页、云服务账号 / 相册项目、小爱发现页、智能卡、钱包首次使用页、快应用设置、传送门设置及相册列表。小爱已更新的版本使用 ReactNativeHomeActivity；直接调用旧 DiscoveryReactNativeActivity 会提示加载失败，更换为当前首页入口后在线内容正常。未修改三项原有闹钟，未开卡或支付。
- 开机早期出现一次 com.android.phone 的 SimPhonebookProvider 初始化异常，远端栈位于系统 Settings.Config 的空 Application；随后主用户电话进程恢复并维持 PID 10026，NR_SA 注册正常。该包与 system_server 不在适配作用域，但尚未做停用模块对照，不能断言其归因。还有一次 init 辅助进程 SIGABRT；未据此声称系统日志零异常。modemManager PID 6504 保持稳定，采集未出现其退出、QESDK 校验失败或 Hook 错误。
- CI 改为 ishtar / songyuan 两项矩阵。ishtar 原始输入单独存入带哈希锁的 Release 资源，配套 APK 使用仓库加密 Secret 中的原生产签名密钥，构建后删除临时密钥。仓库仅保存源代码、官方文本修复、输入哈希与验证文档。

## 验证边界

- 这是针对指定 Elite 改签 ROM 的适配包，不等同于 songyuan 的完整 APK 转换。3.3.x 提供 19 个独立适配开关；原生应用不替换 APK。
- 3.3.1 已验证小米云同步成功和缩略图显示；未等待全库原片下载，不保证所有相册或未来版本均兼容。
- 没有进行支付、绑卡、短信发送、语音唤醒录制、云端恢复/擦除或未来商店更新测试；相应业务仍待实际使用验证。
- 3.3.0 主用户开关可独立保存，其他用户安装状态保持不变；系统共享组件文件继续保留，不将用户级卸载描述成释放所有文件空间。
- 没有跨机型补装独立主题商店或 HyperGalleryPluginCn。主题在线内容由现有管理器提供。
- UIAutomator 与手机现有 UI 自动化服务冲突，工具进程注册失败；页面检查改用 ADB 截图，未强停现有自动化服务。
- 此轮是安装、挂载、包身份与启动功能检查，不构成续航或长期稳定性结论。
