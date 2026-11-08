# ishtar 按应用国内化适配

由 **dalong** 维护，适用于小米 13 Ultra `ishtar` 的 **EliteRomLite v4.05 / OS3.0.308.0.WMACNXM / Android 16**。KernelSU 模块 ID 为 `ishtar_cn_addons`，LSPosed 配套应用为 `com.dalong.cnbridge`。与 songyuan 配置分开打包、分开保存状态，不能互刷。

Elite ROM 的原生应用包含改签包，不能直接用官方 APK 覆盖。本方案保留原有系统应用，补齐 9 个同版本官方 APK，并在选中的应用进程内适配地区判断。**这不是把 Elite ROM 完整恢复成官方国行系统。** 国内云同步、支付、语音唤醒等完整业务还有账号、权限、网络与其他服务依赖；开关不会偷偷开启依赖项。

当前发布版为 **3.3.1-ishtar**，配套 APK 为 **1.0.0**。[真机验证记录](VALIDATION.md)说明实际测试范围与限制。

## 19 项独立开关

3.3.0 起，“补全组件”与“系统应用”都能单独开启，默认全部开启。只需要日历时：

1. 点击“全部关闭”。
2. 在“系统应用”开启“日历”。
3. 保存配置，手动重启并解锁，等待本次启动校验完成。

日历进程读取 CN / ishtar；其他未选应用保留 ROM 原有 HK / ishtar_elite_global 判断。模块不再全局设置 CN。关闭原生应用只撤销适配，不卸载原包、不清理数据。修改在保存前只是页面草稿，保存后也要到下次启动才切换。

| 分类 | 功能 |
| --- | --- |
| 补全组件：8 项、9 APK | NextPay、银联 TSM、小米支付服务、小爱语音与唤醒、小爱服务、传送门、黄页、快应用 |
| 原生应用：11 项 | 小米智能卡、小米钱包、小爱视觉、负一屏、短信、应用商店、云服务、日历、时钟、主题与壁纸、相册 |

所有补装 APK 都来自 **ishtar 同版本官方 ROM**，不混用 songyuan 306：

| 包名 | 功能 |
| --- | --- |
| `com.miui.nextpay` | NextPay |
| `com.unionpay.tsmservice.mi` | 银联 TSM |
| `com.xiaomi.payment` | 小米支付服务 |
| `com.miui.voiceassist`、`com.miui.voicetrigger` | 小爱语音与唤醒 |
| `com.xiaomi.aiasst.service` | 小爱服务 |
| `com.miui.contentextension` | 传送门 |
| `com.miui.yellowpage` | 黄页 |
| `com.miui.hybrid` | 快应用框架 |

完整作用域由 [Scope.java](companion/src/com/dalong/cnbridge/Scope.java)定义：19 项对应 22 个应用包。应用开关不隐式包含账号、推送框架、桌面、下载器或 system_server。云服务开关包含云服务、云备份和同步服务；相册开关只适配相册，完整同步仍需现有云服务配合。

## 安装与使用

需要 KernelSU 的挂载元模块和支持 **libxposed API 102** 的 LSPosed。当前实机使用 Magic Mount-rs 4.0.11 与 LSPosed 2.2.1（7939）。

从仓库 Actions 的 **Build Device Modules** 成功运行下载 `HyperOS-CN-Bridge-ishtar-运行编号`，解压后得到模块 ZIP、配套 APK、校验文件和构建记录；外层 Artifact ZIP 不能直接刷入。

1. 安装或更新 `cn-bridge-companion.apk`（至少 1.0.0），打开一次“国内化按应用适配”。
2. 在 LSPosed 中启用配套模块，勾选全部推荐应用。不要添加系统框架或其他用户；真正是否适配由 WebUI 的 19 个开关决定。
3. 允许配套应用后台启动。若使用 Thanox，也要将它从自启动阻止与后台清理中排除。它只提供配置接口，没有轮询常驻任务。
4. 在 KernelSU 安装 `ishtar-cn-addons-OS3.0.308.0.zip`，重启并解锁。
5. 从 KernelSU 模块页打开 WebUI。运行详情会报告启动校验或配套模块配置不一致，不能把旧启动的成功当成本次成功。
6. 初次补装或重新开启已卸载组件时，LSPosed 可能需要再次授权作用域。允许“作用域请求”通知后，点击 WebUI 的“授权后重新校验”；全部校验通过才显示已生效。

从 3.2.x 升级时，原有 8 项选择保留，旧 CN 开关迁移为 11 项原生应用选择；迁移前配置备份到 `/data/adb/ishtar_cn/before-app-scoped/`。未完成旧版配置事务时拒绝升级。

配套模块只在本模块的只读标记实际挂载、机型和系统版本匹配、主用户 0、明确列出的应用进程中工作。停止 KernelSU 模块并重启后，配套适配也停止。不要仅卸载配套 APK 并保留待应用的配置；状态检查会明确报错。

## 关闭、删除与多用户

- **原生应用**：关闭只撤销本模块的地区适配，保留原包、数据和原有安装状态。
- **补装应用选择删除（默认）**：下次启动从主用户 0 卸载，使用保留数据的用户级卸载；重新开启时恢复主用户安装登记。
- LSPosed 会清除已卸载应用的作用域。恢复后通过 [libxposed 的公开 requestScope 接口](https://github.com/libxposed/service)请求授权；WebUI 显示等待状态并提供重试。未授权不会假报生效，也不使用开发调试 CLI 作为自动化接口。
- **补装应用选择保留**：仍安装在主用户中，撤销地区适配。更新包也保留，不清理其个人数据。
- **共享文件**：系统 APK、原生库、离线安装源继续保留，以支持其他用户和再次开启。因此“删除”表示主用户卸载，不表示释放全部 APK 文件空间。
- 不更改用户 10、999 等其他用户的安装状态，不向这些用户注入适配，不删除分身数据。不要通过删除用户绕过依赖问题。
- 不修改 NFC UID、包签名身份、桌面或下载数据库。3.3.0 使用独立的 [IshtarController.java](tools/IshtarController.java)，不调用 songyuan 的包身份迁移、更新层移除和用户数据迁移路径。

开关只控制国内化，不覆盖 Thanox 或系统的后台管理规则。已卸载组件恢复时，Thanox 的新应用模板可能重新限制它们的启动；需要使用语音唤醒、同步等后台功能时，请另行检查相应启动限制。

状态目录为 `/data/adb/ishtar_cn/`。启动应用失败会记录原因并尝试恢复主用户安装登记与旧配套配置；仍需查看报错并重新启动完成挂载与进程切换。不要手动删除状态目录。卸载整个模块前，先备份并检查其他用户是否仍依赖补装应用。

## WebUI 与更新兼容

WebUI 离线运行，支持浅色 / 深色主题、手机双列 / 宽屏三列、真实应用图标、包信息详情、全部开启 / 关闭和保存草稿。系统应用与补装组件使用一致的独立开关。

“配置已生效”旁使用圆形勾；等待重启显示时钟，处理中显示箭头，失败显示感叹号。图标与文字状态一致。最初的整体布局参考 [imagegen 设计稿](design/webui-v2-concept.png)；这次状态图标依照 imagegen 技能对代码原生图形的建议，直接修改内联 SVG。

商店更新仍优先运行。配套适配按包名选择作用域，同包名更新继续被选中；如果新版更换地区判断方式，需要重新验证。只有同签名、同包名、适配当前系统的更新才可正常安装，Elite 改签应用可能拒绝官方更新。本模块不绕过签名校验，也不保证未来版本兼容。

官方 ishtar 包中没有独立 `com.miui.themestore` 和 `com.hyper.gallery.plugin`，因此不跨机型补装。主题继续使用当前 ROM 的集成管理器，不启用会隐藏其入口的独立商店模式。

### 相册小米云同步

Elite 自带的 `4.3.1.18-global` 相册另有硬编码的 `BuildUtil.isGlobal()`，单纯修改地区属性仍会显示 Google 备份。3.3.1 配套模块只在相册适配开启时拦截该判断，使用 APK 内已有的小米云备份界面和同步实现。

国际版留下的相册同步适配器可能为不可同步状态。配套代码只在**用户已经开启小米云同步**时恢复该账号、该相册 authority 的可同步登记；不会自动开启云同步，不更改 WLAN、自动下载、相册上传选择，也不读写照片数据库。关闭相册适配后不再执行这两项处理。

初次使用仍需在相册中点击“立即开启”并确认小米云服务提示。当前版本已实际验证小米云同步入口与系统同步登记；完整照片库的传输进度取决于网络和云端数据量。

### 应用商店安装权限

应用商店开关单独管理 `com.xiaomi.market` 的 `INSTALL_PACKAGES` 签名权限白名单。这台 ROM 的商店位于 /data/app，缺少 [Android 15+ 显式授权](https://source.android.com/docs/core/permissions/signature-permission-allowlist)时，下载后可能要求用户操作，而商店将其视作安装失败。补丁只声明这一项权限，系统仍核对平台签名。关闭商店适配并重启后移除该挂载。

安装权限修复不改变宽带、CDN、下载并发、DNS 或温控；排队等待与实际网络下载速度需分别判断。

## modemManager 循环退出修复

Elite v4.05 修改了 `/vendor/etc/init/qesdk-manager.rc` 的尾部空行和 `/vendor/etc/seccomp_policy/qesdk.policy` 的版权注释。QESDK 对文件逐字节校验，哈希不匹配导致初始化失败；失败清理触发 Scudo 内存错误，abort 信号又被 seccomp 拦截，表现为 modemManager 约每 5 秒重启。

[qesdk-compat.sh](module/qesdk-compat.sh)仅对已核验的机型、ROM、modemManager 和两份文本哈希，以只读 bind 挂载恢复同版本官方字节。先验证两份输入，再挂载；失败会撤回本轮挂载。备份在 `/data/adb/ishtar_cn/qesdk-compat/`。不修改系统调用规则、不关闭校验、不停用服务、不刷写 vendor；停止模块并重启可撤销覆盖。此修复独立于 19 个应用开关。

### 已有提示音模块挂载兼容

已知的 `hyperos_gushi_audio/system/product -> ../product` 旧布局会让 Magic Mount-rs 4.0.11 抢占整个 product 节点。安装器只调整该已知布局：实际 product 目录移入 system/product，顶层改为指向它的链接，保留全部音频内容。记录在 `/data/adb/ishtar_cn/mount-compat/`。未知布局会明确失败。

## 本地构建与测试

依赖 Python 3.10+、JDK 17+、Android SDK / Build Tools 36.0.0。ROM、APK、安装包、密钥与手机原始诊断均不进入 Git。

1. 获取[同版本小米官方 Recovery ROM](https://bigota.d.miui.com/OS3.0.308.0.WMACNXM/ishtar-ota_full-OS3.0.308.0.WMACNXM-user-16.0-e3d01be14a.zip)，按 [rom-inputs.lock.json](rom-inputs.lock.json)核验整包。
2. 用 payload-dumper-go 提取 product，再用 fsck.erofs 解包到 `rom/ishtar/cn`。构建校验 9 APK、权限 XML、包名与原始证书。
3. 运行 `python3 ishtar/companion/build.py`。API / service / interface 102.0.0 依赖使用固定 SHA256；API 仅参与编译，不打进模块 DEX。发布与升级通过 `CNBRIDGE_KEYSTORE` 使用同一密钥，构建会校验生产证书 SHA256，勿提交密钥。CI 从仓库加密 Secret `CNBRIDGE_SIGNING_KEY` 读取 Base64 编码的 keystore，构建后删除临时密钥。
4. 运行 `python3 ishtar/tools/build.py`。输出模块 ZIP 与 SHA256；ZIP 逐文件核验 APK、库、权限文件哈希和 CRC。

WebUI 回归：运行 `node ishtar/tools/test_webui.mjs`，可通过 `PLAYWRIGHT_MODULE` 和 `CHROME_EXECUTABLE` 指定环境。覆盖 19 项单独保存、关闭 / 保留 / 取消、启动状态、作用域拒绝、真实图标、键盘操作、320–850px 与深色模式。

[ScopedValidationTest.java](tools/ScopedValidationTest.java)与两个控制器、DeviceRepair 一起编译和 D8 转换后，可在本机型通过 root app_process 执行。它只读配置、挂载和安装状态，不修改手机，检查 19 项作用域隔离、全关、配置校验、作用域缺失、挂载损坏等。旧 [BootValidationTest.java](tools/BootValidationTest.java)只适用于 3.2.x 控制器，不用于 3.3.0。

真机逐进程测试是地区判断与启动验证，不代表已完成真实支付、开卡、云端恢复或所有业务端到端测试。GitHub Actions 使用两项构建矩阵分别打包 songyuan 与 ishtar，每项使用独立 ROM 锁和校验；ishtar Artifact 同时包含签名配套 APK。CI 输入可通过 `python3 ishtar/tools/rom_inputs.py extract --archive <归档路径>` 解包，归档及每个原始文件都需通过哈希与大小校验。
