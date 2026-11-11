# 真机验收记录

## 3.1.3-songyuan：AI 搜索模型适配

电脑日期 2026-11-09，手机日志为 2026-11-10（GMT+08:00）。实际连接 songyuan 官方 OS3.0.306.0.WGNMIXM / SM8850；本轮没有连接 ishtar。生产模块 3.1.3、同签名配套应用 1.0.1（10001）已安装并重启生效，LSPosed API 102。

| 检查 | 实测结果 |
| --- | --- |
| 根因 | 全局国内化使 AICR 3.63.1 选择缺少资源的国内 BERT/CLIP 链路；文件 vectorCount=0，进度反复停在约 88%。已连接非计费 Wi-Fi 重试，国内模型仍未就绪 |
| 改动范围 | 仅主用户 AICR 四个搜索工作进程选择本机已有的统一模型；完整机型、SoC、系统版本、当前 CN 属性和 APK SHA256 均匹配才安装 Hook。保留全局 CN、设置界面及其他国内化组件 |
| 实际推理 | initVision、initText、initE5、initNLU 全部返回 0；中文文本得到有限的 BERT/CLIP 特征，合成 RGB 图像得到 768 个有效非零特征，实际接口返回 0 |
| 图片与视频 | 原先失败的 2959 张图片 clip_state 全部变为 1；视频已成功 69 个并持续处理，向量表 4303 行、每行 3072 字节（768 个 float）。17 张图片状态为 10，视频仍有 307 个未处理、1 个失败；这是 15:40 的只读快照，不代表图库全量完成 |
| 文件索引 | 手机 15:28:02 切换为文件优先分析，15:32:17 实际 vectorCount=5700、文件 progress=100。数据库确认 5700 条 store_state=2，重启后保留；另有 88 条 store_state=-1，本轮未逐条鉴定 |
| 实际相册搜索 | 重启后从相册 SearchActivity 输入中文“猫”，语义结果区显示两张猫咪照片，文本识别及文件名结果单独显示；英文 cat 也有图片/视频结果。未把 root 调用返回空结果当作普通相册搜索结果 |
| 启动与后台 | Thanox 曾拦截配套应用导致状态 Provider 无法启动；通过其 Binder API 解除配套启动/后台限制及系统自启动 AppOps，重启回读仍允许。AICR 原有启动/后台例外保留，其他省电规则未改 |
| 生产包与重启 | 15 个活动脚本、JAR、WebUI 和元数据文件逐项 SHA256 与生产 ZIP 一致；19 项 desired/applied 全开且一致，31 包已加载，pending=false、applying=false、result.ok=true；重启后观察超过 7 分钟 system_server 与 boot ID 保持 |
| 原生 WebUI | KernelSU 操作按钮实际打开新页面，显示 3.1.3、配置已应用、AI 适配作用域已就绪。该状态不代表索引已完成 |
| 回归 | ROM 输入 5 项单元测试、生产构建中的 APK 签名/包名/证书/文件哈希及 ZIP CRC、WebUI 浏览器回归通过；未知 AICR APK 和过旧配套的只读状态探针均拒绝就绪 |

总进度已从 88% 实际推进到 99%；相册随后新增照片，分母变化后显示 98%，剩余视频及新增照片已在系统页面恢复分析。未修改进度、删除数据库或解除温控/电量条件。短信、录音、笔记的授权及全量搜索覆盖、所有视频完成、长期稳定性不在本轮已通过范围内。可选国内 clip 元信息查询仍可能返回资源缺失；有效的统一模型推理及向量写入已分别验证。

15:44 增量快照：图片成功已增至 3023、视频成功增至 79、向量表增至 4652 行。文件来源也在变化，此时语义完成 5699、待处理 137、失败 86；前述 5700 全部完成与重启保留是对应验收时刻的结果，不是后续不断新增数据的固定总量。剩余任务继续运行，本轮临时诊断 JAR/DEX 已从手机清理。

生产 ZIP：`dist/songyuan-cn-complete-OS3.0.306.0.zip`，SHA256：`62fb0828890c69caf7bf5b6f1349672e9838500ec35135effcd773f3fc57592c`。手机 Download 同名包及 Root 私有缓存一致。升级前 3.1.1 包与配置备份保存在忽略的 `diagnostics/songyuan-ai-search-20261109/`；同目录含安装、计数、状态和私有截图，均不进入发行物。本轮未执行 Git 提交或推送。

## 3.1.1-songyuan：相册空白与作者信息

在同一 songyuan / OS3.0.306.0.WGNMIXM / Android 16 手机上验证。KernelSU 3.3.1，Magic Mount-rs 4.1.12-959，SELinux Enforcing。模块作者改为 `dalong`，名称为 `HyperOS CN Bridge · 国行组件`；安装器、WebUI、简介与 README 同步调整，保留上游来源及致谢。

| 检查 | 实测结果 |
| --- | --- |
| 相册空白原因 | 当前相册 UID=10221，外置目录中 6517 个条目仍属于历史 UID；相册缓存及下载目录反复报 Permission denied，云端列表本身可用 |
| 修复范围 | 修正相册自身 Android/data 树中的旧属主，修改前保存权限元数据；修复后逐项对照原记录，组与模式没有变化，不清空照片、缓存或账号 |
| 幂等与重启 | 修复后再次检查 repaired=0；安装最终模块并重启，启动报告 checked=6541、repaired=0，后续目录统计全部属于当前相册 UID |
| 显示与预览 | 修复前空白的缩略图恢复，最终包重启后照片网格与单张照片预览实际显示；没有执行全部原片下载 |
| 同步与日志 | 重启后 syncable=1、enabled=true；本次相册进程日志中 Permission denied / EACCES、位图解码失败、FATAL EXCEPTION 均为 0 |
| 配置与组件 | 19 项 applied 与 desired 一致并全部启用，31 个包加载；pending=false、applying=false、result.ok=true。保留已安装的官方相册插件更新层 |
| 安装与运行文件 | KernelSU 安装退出 0；最终 ZIP、Root 私有 source.zip、活动模块的 10 个脚本 / WebUI / JAR / 元数据文件逐项哈希一致 |
| 应用双开 | 手机已有用户 999，保持现有配置升级成功，未删除双开空间。实测组件保存被保护检查拒绝，desired / applied 哈希不变；多用户数据切换仍未支持 |
| 稳定性 | 最终包重启后连续观察 180 秒，system_server PID 与 boot ID 保持；相册启动、网格和预览期间没有软重启。本记录不替代长期稳定性或所有云端原片验收 |
| 构建与页面 | APK 签名、包名、证书、最终组件哈希及 ZIP CRC 检查通过；WebUI 的默认项、删除 / 保留、保存、手动重启、更新说明、移动端、深色模式、状态及图标测试通过 |

本地最终模块：`dist/songyuan-cn-complete-OS3.0.306.0.zip`，SHA256：`c821079a442ed20476ad266fdd3f6802c8c4b5662fd1f2182f51984deeb6bc31`。手机下载目录存有同名 ZIP 和校验文件。私有日志、截图及安装记录位于忽略的 `diagnostics/gallery-blank-20261106/`，不随模块或源码发布。

## 3.1.2-songyuan 构建回归

- 与 ishtar 分开构建，保留 OS3.0.306.0 国行 / 国际版原始输入、原签名 APK 与现有 songyuan 功能。ishtar 的按应用 LSPosed 配套、QESDK 覆盖和单用户安装登记逻辑不进入此模块。
- 共享控制器抽取 verifyLoaded / appliedResult；songyuan 仍按原逻辑校验所选包是否加载，新增的每次启动挂载校验、boot ID 和权限文件处理均限定 nativeAddons 配置。
- 两套 WebUI 回归、ROM 输入 5 项测试与完整构建通过；APK 签名、锁文件哈希和 ZIP CRC 通过。生产 ZIP SHA256：c5315a8b72cf35ce10e790b3625174045fef191d0c1e2688ab672b50dd78e9fa。
- 本轮只连接 ishtar，未将 songyuan 包跨机型安装；songyuan 3.1.2 的结果是构建与共享逻辑回归，下面的 songyuan 真机业务结果属于历史版本。Actions 会单独产出 songyuan 模块与校验、来源记录。

## 3.1.0-songyuan（历史验收）

目标手机：songyuan，官方国际版 OS3.0.306.0.WGNMIXM / Android 16；原签名组件来自同机型 OS3.0.306.0.WGNCNXM。日期为手机当地时间 2026-11-06。

默认 19 个功能全部开启，包含截图的 14 个应用及云服务、日历、时钟、主题、相册。共 31 个包、41 个国行原签名 APK、4 个下载器原生库，另存 1 个原签名国际版时钟回退 APK。

### 本轮修复与真机结果

| 项目 | 结果与证据 |
| --- | --- |
| 账号登录无反应、TSM 未加载 | 新建目录继承私有 umask，0700 导致 PackageManager 报 No packages found in split。准备和启动提交前固定目录 0755、文件 0644；重启后两包正常加载。账号应用数据归档恢复后，小米账号已重新登记，无需重新输入凭据 |
| 智能卡身份 | com.miui.tsmclient UID=1027，CE/DE 属主 nfc、标签 nfc_data_file；NFC=on；显式 MainActivity 可进入，未绑卡或支付 |
| 查找设备定位 | 本机已有官方国行查找设备 20.15.70 更新；运行时精确、粗略、后台定位均为 granted=true，UID Fine/Coarse AppOps=allow。当前设备刷新返回有效地址及“刚刚”，定位 AppOps 记录新访问。未向其他设备发送控制指令 |
| 定位修复持久化 | 首次启用云服务时修复权限；成功写入 finddevice-location-v1 后不反复覆盖后续权限选择。已移除本系统不支持的 BACKGROUND_LOCATION AppOps 名称，后台定位使用 runtime permission |
| 重启状态 | next-job 才显示等待手动重启；mounted 显示启动配置中，错误优先显示应用失败。完整启动后 pending=false、applying=false，19 个功能的 applied 与 desired 均开启 |
| 实际应用图标 | Android PackageManager 成功读取 19/19 个功能图标，原生 KernelSU WebUI 已显示智能卡、钱包等真实图标；缺失应用回退默认图形 |
| 相册同步 | 小米账号已被系统账户服务识别，syncable=1、enabled=true；云服务显示已有相册项目。未宣称所有原片下载完成 |
| 云恢复入口 | SystemRestoreActivity 已加载备份列表，3 个数据恢复入口可见，没有“本机不可用”；最终包已进入详情并加载“开始恢复”，未执行整机恢复 |
| 全部开启加载 | 31/31 包被 PackageManager 加载；国行 APK 和外置库 45/45 SHA256 与构建来源一致 |
| WebUI | 浏览器通过 19 默认项、详情、取消、默认删除、保留、保存、不自动重启、官方更新说明、移动端宽度、深色模式、失败/已应用状态和图标测试；原生模块操作按钮能打开 WebUI |
| 配置输入 | 缺失项、字符串布尔、额外 ID 均拒绝，desired 文件不变 |
| 全关闭回退 | 本轮此前实测地区 GB，Global 原版账号/下载器/主题/相册/TSM、POCO 及国际版时钟正常；19 项 applied=false、无待应用任务；原 3 个闹钟与 70 条桌面位置保持 |
| 下载器软重启 | 原因是 ROM Global 数据库版本 123、CN 125，自动降级抛异常进入 system_server。保存生成保留记录与列的迁移副本；启动预载程序等待 FBE 解锁后原子替换，CN/Global 双向启动已验证。不得再把旧的已更新 Global 数据库版本当作 ROM 原版版本 |
| 桌面与闹钟最终保持 | 最终包启动后 70 条桌面项目的 ID、屏幕、格子、跨度等位置字段逐项一致；3 个原有闹钟保留；桌面、闹钟及下载数据库 integrity_check=ok |
| 稳定性范围 | 最终包重启后连续观察超过 5 分钟，跨过多个原故障重启周期，system_server PID=6707 保持，未再出现下载器 SQLite 降级异常或系统软重启。启动早期 crash buffer 有单次 init 辅助进程原生信号记录，无新增 tombstone；不宣称整个 crash buffer 为空或长期零故障 |
| 代码复核 | 安装器、启动事务、共享依赖、更新包、数据/UID、路径边界、WebUI 与权限流程已人工复核，见 CODE_REVIEW.md |

### 保留的业务实测记录

下列下载业务在 3.0.1 已实际执行，3.1.0 保留相同原签名应用及依赖；与本轮的加载、状态和回退测试区分。

- 主题：下载实际成功，16,074,940 字节；免费在线壁纸实际应用成功，原壁纸归档后已恢复。主题商店闪退所需能力属性继续保留。
- 应用商店：实际下载安装 com.phoenix.read 7.4.3.32，安装来源 com.xiaomi.market。
- 云恢复：多份备份详情可进入选择页；未启动会覆盖现有数据的恢复。
- 相册：云端照片、视频列表及缩略图曾实际加载；未验证全部原片下载。
- 时钟：国行 17.66.0，秒表运行/暂停/复位，3 秒倒计时结束及临时闹钟响铃均已验证；临时项目已清理，原 3 个闹钟保留。
- 日历、小爱、短信、负一屏、传送门：对应国行页面可进入；未发送短信、进行支付或验证实体 NFC 刷卡。

### 真机测试时的历史交付

以下哈希记录本轮真机验收的本地构建，不是后续每次 Actions 构建的固定值。云端产物应以对应运行附带的 SHA256 和 build-info.json 为准。

完整包：`dist/songyuan-cn-complete-OS3.0.306.0.zip`。
SHA256：`a537971bb4803ee0bbeb7da516aa657cb255d5579af61d7918fa9fc92d69559a`。
手机另存 `/sdcard/Download/songyuan-cn-complete-OS3.0.306.0.zip` 及 SHA256 文件，缓存源与交付包哈希一致。最终安装日志见 `diagnostics/webui/install-3.1.0-final.txt`，运行文件哈希与交付 ZIP 已逐项核对。
来源：`dist/sources-complete.json`，逐文件校验：`diagnostics/verification-complete.json`。

运行数据、账号相关归档和原包设置位于 Root 私有 `/data/adb/songyuan_cn/`，电脑副本位于忽略的 `diagnostics/private/`。不将这些文件打进模块，也不公开。旧整份包设置只用于当次即时失败回退，不能日后覆盖系统全部包设置。

WebUI 的删除只移除国内版展开文件及对应更新层，保留私有应用数据备份与压缩安装源。系统区域由所有启用组件共享；无法按单个应用隔离。核心云/账号/NFC 更新建议配套；“同包名同签名官方更新通常可用”不代表保证所有后续版本兼容。
