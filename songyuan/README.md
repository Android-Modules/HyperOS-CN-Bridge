# HyperOS CN Bridge：songyuan 官方国际版适配

作者与维护：**dalong**。基于 LSHFGJ/HyperOS3EULocalization 适配官方国际版，提供配套国行服务、离线组件管理和真机问题修复。

目标仅限 `songyuan / Android 16 / OS3.0.306.0.WGNMIXM`。应用取自同版本 `WGNCNXM`，不使用其他机型的 APK，也不修改 APK 或重签名。

本次真机结果见 [VALIDATION.md](VALIDATION.md)，仓库与 ROM 提取物的逐包版本对比见 [COMPARISON.md](COMPARISON.md)。当前手机已安装完整包，KernelSU 所需挂载配置已核对。

原仓库主要面向 xiaomi.eu；本目录为官方国际版单独构建。它覆盖截图中的全部 14 个包，补充云服务、云同步、云备份、小米账号、日历、时钟、主题和相册，并包含云恢复所需的国行桌面、内容捕捉、语音唤醒、国区服务框架、SIM 激活、主题商店、相册插件及必要依赖，共 31 个包、41 个 APK。国区服务框架采用 ROM 原有的完整 split APK 集合。

当前模块为 **3.1.3-songyuan**，配套应用为 **1.0.2**。构建前设置 `CNBRIDGE_KEYSTORE` 指向原发布密钥，先运行 `python3 ishtar/companion/build.py`，再运行 `python3 songyuan/tools/build.py`。默认输入为仓库本地 `rom/cn/`，包括国行 product、system、system_ext 分区的目标应用、权限清单及下载服务的 4 个外置原生库。`--without-wallet` 只用于首次基础与云服务的隔离验证。

产物在 `dist/`，包含 ZIP、SHA256 和逐 APK 的来源清单。构建会验证每个 APK 在 Android 16 上的签名、包名和证书，并提取需要外置的 ARM64 库。短信、黄页、银联服务分别保留它们各自的原始证书。

安装器限定机型/系统版本、检查 Root 挂载能力和冲突模块；使用 `.replace` 在 `/product` 实际可见目录屏蔽 Global 同包应用，不操作已经合并进 overlay 的底层 mi_ext 目录。仅从国行权限文件中摘取本模块包名的特权权限，不覆盖整份系统权限文件。

新增的 `data-app` 原签名 APK 放入系统会在每次启动时扫描的 `app` 目录，相册放入 `priv-app` 并沿用系统对该包已有的权限白名单。每次启动会移走受影响包的解析缓存，使其重新登记组件；缓存保留在固定备份目录，不清应用数据。

本机的 MIUI 在 KernelSU 挂载后还会追加 `/system_ext/priv-app` 叠层，因此权限管理器单独使用同签名的 PackageManager 更新方式安装。国际版权限管理器缺少国区剪贴板规则，切换地区标识后会崩溃；国行下载服务则为商店补全所需字段及迅雷原生库。国际版权限管理器 APK 另存于 `/data/adb/songyuan_cn/managed-updates/AuthManager-global.apk`。仅停用模块不会撤销这一个已安装的应用更新。

故诗声音模块原布局存在 `system/product` 符号链接与重复 `system/media`，会让 Magic Mount 本轮跳过整个 product 挂载。`python3 songyuan/tools/fix_audio_layout.py` 从备份重新打包成单一 `system/product/media` 布局，262 个音频逐字节保留；不需要更换挂载元模块或给普通应用授予 Root。

`ro.product.mod_device=songyuan` 用于国行功能判断。通过启动时的文件 bind 提供 `cn`，并在 post-fs-data 与启动完成后重新应用 CN 属性；原始地区文件保留，停用并重启即撤销挂载。系统版本号、OTA 指纹、全局机型均保持 ROM 原值。

## AI 搜索本地分析

3.1.3 在安装时更新同签名的 `com.dalong.cnbridge` 配套应用。需要在支持 **libxposed API 102** 的 LSPosed 中启用“国内化按应用适配”及推荐作用域，然后重启。该功能独立于 19 项组件开关；系统仍采用 CN 地区时自动适配，恢复原有 Global 地区判断后跳过。

使用 Thanox 等后台管理工具时，需允许配套应用 `com.dalong.cnbridge` 启动和后台运行，否则 WebUI 无法读取配套状态。AI 引擎 `com.xiaomi.aicr` 也需要运行分析任务的后台权限；不要在分析过程中强停这些进程。按需为必要应用设置例外。

此 ROM 的 AICR 3.63.1 在国内分支请求未配套的 BERT / CLIP 模型，导致语义索引失败，旧进度仍可能显示约 88%。适配仅在 `com.xiaomi.aicr` 的 `:searchDataService`、`:searchService`、`:bertAlgo`、`:clipAlgo` 四个进程内提供 `songyuan_global` 的模型选择标识，使用该机自带的 `ai_search_qcom_qnn_8850` 统一模型。AI 搜索设置界面和其他应用继续使用国区判断，不替换 AICR、AIService 或 ROM APK，不修改进度、温控和电量条件。

适配限定 songyuan、SM8850、OS3.0.306.0.WGNMIXM 和 AICR APK SHA256 `f1f7314612528e12c6dcf1e9f13dfb86dda0bde1c6bf9154d114051c199f4d90`。其他系统或 APK 更新后跳过，需重新适配；不能保证后续应用商店版本兼容。WebUI 状态页检查 APK 版本、配套版本与 LSPosed 作用域，不将“作用域就绪”当作“模型和索引已完成”。

首次切换模型分支需要重新分析。请连接 Wi-Fi、接电并按系统页面提示开始分析；几千张照片的文字识别可能耗时较长，进度也可能重新计算。不要通过修改索引数据库或进度强行标记完成。模型初始化、特征计算与完整索引完成的实测范围分别记在 [VALIDATION.md](VALIDATION.md)。

## WebUI（3.1 系列）

在 KernelSU 模块卡片打开 WebUI，或点模块的操作按钮。页面完全离线，不依赖额外 WebUI 应用。设计稿和生成提示保存在 `design/`，成品使用原生 HTML/CSS/JavaScript，实现组件、状态和说明三个页面，支持深色模式。组件图标默认读取当前已安装应用，无法读取时使用内置占位图；状态页区分待重启、启动配置中、应用失败与已应用。

19 个功能默认全部启用。单独关闭时默认删除国内版文件，也可保留文件；保存只准备下一次启动的组件树，必须手动重启。关闭国行专属应用后该应用消失；国际版有对应应用时恢复 ROM 原版。账号、推送、下载器等共享依赖由仍启用的组件共同决定，权限白名单也按包撤销。区域是系统共享属性，仍有国行组件启用时保持 CN，不能按单个进程隔离地区。

保存要求主用户已解锁。控制器先校验文件并备份受影响应用的 CE/DE 数据；NFC、照片、短信和账号数据不作为“删除文件”的删除对象。请保存后及时重启，备份之后继续使用待关闭应用产生的新数据不在这次快照中。下一次启动在系统扫描 APK 前原子切换组件树，启动解锁后恢复需要的数据、UID 与标签，再清理旧文件。配置、文件源、保留包和数据备份均位于 Root 私有目录 `/data/adb/songyuan_cn/`。目前数据迁移以本机主用户 0 为范围。

检测到应用双开或其他用户时，3.1.1 允许已安装模块保持全部现有配置升级修复；首次安装或组件切换仍要求单用户，避免只迁移主用户却改变其他用户的包身份与数据。不会删除双开用户或双开应用。

压缩安装源会私有缓存一份，因此删除展开文件后仍可离线启用。保留模式还可恢复保留的商店更新；删除模式重新启用时恢复模块配套版本。应用商店的 `/data/app` 更新优先于模块 APK，控制器关闭组件时也会处理这一层，避免开关失效。

国际版时钟原本位于不会在已初始化手机上重新扫描的 `data-app` 目录。模块额外保存一个原签名国际版时钟 APK，关闭时钟国内化时发布到系统扫描目录，保留原有闹钟；启动完成后同时登记为同签名应用更新，全部关闭后卸载模块仍能保留时钟。这一个回退 APK 不计入 41 个国行 APK。

同包名、同签名的官方国行更新通常可继续生效；模块的区域、依赖和修复继续提供。不能保证后续所有版本兼容，云、账号、推送、NFC、支付等核心依赖建议保持本模块配套版本。每个组件详情和说明页均注明此范围。更新与签名规则参考 [Android 应用签名](https://developer.android.com/studio/publish/app-signing#considerations)，WebUI 入口遵循 [KernelSU Module WebUI](https://kernelsu.org/guide/module-webui.html)。

国内云备份依赖国行桌面，关闭云服务会切换回 POCO 并迁移当前布局；重新启用则反向迁移。卸载整个模块前应先在 WebUI 关闭组件并重启，完成 UID 与桌面的回退；直接停用或移除整个模块不会执行 WebUI 的数据切换流程。

## 智能卡共享 UID

国际版 `com.miui.tsmclient` 使用独立应用 UID，而国行原签名 APK 声明 `android.uid.nfc`（1027）。仅复制 APK、删除目录或更改目录属主都不能完整解决包管理器身份差异。3.1.0 将主用户的身份切换纳入安装与 WebUI 启动流程；检查 NFC 签名索引、备份当次包设置，仅修改智能卡的 UID 属性，并匹配数据属主与 SELinux 标签。回国际版时检测 UID 占用，必要时分配空闲 UID。

`tsm_uid.py` 保留为早期主机端检查工具，默认只读；日常开关使用 WebUI 即可。包设置启用了 fs-verity，控制器通过临时文件原子替换并保留当次原文件备份。旧整份包设置只适用于当次即时失败回退，不得在日后恢复覆盖全系统设置。

迁移不主动删除卡片或应用数据，但 Android Keystore 与 UID 绑定，国行应用可能要求重新登录或重新配置卡片。备份不能保证已发行交通卡、银行卡或硬件安全元件状态可迁移。卸载或回滚到国际版时必须同时处理智能卡 UID，不能只卸载模块。

诊断和数据备份保存在本机 `diagnostics/`（已忽略）及手机 `/data/adb/songyuan_cn/`。不要公开其中的个人应用数据。启动路径报告位于手机 `/data/adb/songyuan_cn/boot-status.txt`。

`python3 songyuan/tools/verify_device.py` 逐 APK、逐外置库比较真机当前加载路径的 SHA256 和构建来源清单。它不会把“模块已启用”当作实际挂载成功。

3.0.1 增加 `ro.miui.support.system.app.uninstall.v2=true`，修复主题商店启动后立即退出。3.1.0 进一步确认 ROM 原版下载服务要求数据库版本 123，国行要求 125；旧版数据库曾经被更新过，不能据此推断 ROM 原版要求。保存时先备份，通过事务生成保留全部记录与列的目标数据库。启动前预载迁移程序，凭据加密存储解锁后立即原子替换下载数据库，并在完成后才恢复其他应用数据。这样兼容 FBE，也避免旧快照覆盖目标版本号。首次启用时同时补齐国行字段；原数据库另存私有备份。`migrate_downloads.py` 是早期主机端补列工具，默认只读。

`DeviceRepair.java` 随模块编译为工具，修复相册账号权限与同步注册。同步迁移通过系统 UID 调用 ContentService，并校验写入结果；完成后不再强制覆盖用户开关。构建需要本机 OpenJDK 和 Android 36 SDK。

国内云备份按桌面命名空间区分备份，POCO 桌面会把国内版备份列为“本机不可用”。使用同 ROM 的国行桌面和 `ro.miui.product.home=com.miui.home`，由系统自带的条件 overlay 切换最近任务资源。国内版桌面放入原 `MiLauncherGlobal` 目录，避开 POCO 按目录跳过扫描的逻辑。安装时先归档原 POCO 数据，首次启动迁移布局；3.1.0 的 WebUI 开关同时处理回 POCO 和重新启用时的布局迁移。

组件加载、启动、登录页检查与真实短信收发、实体 NFC 刷卡、支付、云端同步是不同验收项目；未经实测的项目不能标为成功。

3.1.0 修复 KernelSU 私有 umask 导致新组件目录不可遍历的问题：准备及启动提交前统一目录 0755、文件 0644，避免账号和智能卡被包管理器判为缺失。查找设备沿用已安装的官方国行更新，首次启用云服务时修复精确、粗略、后台定位权限及被拒绝的 UID AppOps；完成后不反复覆盖用户之后的权限选择。

3.1.1 修复同步后缩略图空白和原图下载 Permission denied：切换后相册 UID 可能重新分配，但 `/data/media/0/Android/data/com.miui.gallery` 内旧缓存仍由原 UID 所有。每次主用户解锁启动后，对照 PackageManager 与 DE 数据目录的当前 UID，仅修正该应用外置树中不一致的属主；保留文件内容、组、模式和 SELinux 标签，不清空相册、不重置账号或用户权限。修复前将旧权限及 inode 记录保存在 Root 私有 `gallery-storage-时间戳.json`，拒绝符号链接、非规范路径、硬链接及变化中的条目；重复执行没有差异时不停止相册。即使切回国际版也执行该检查。
