# HyperOS CN Bridge

[![Build Songyuan Module](https://github.com/Android-Modules/HyperOS-CN-Bridge/actions/workflows/release.yml/badge.svg)](https://github.com/Android-Modules/HyperOS-CN-Bridge/actions/workflows/release.yml)
[![License: Apache-2.0](https://img.shields.io/badge/code-Apache--2.0-blue.svg)](LICENSE)

为 **HyperOS 3 官方国际版恢复国行应用与服务**的 Root 模块，包含钱包、小爱、云服务、主题、相册等 **19 个功能开关**，提供 KernelSU WebUI、组件按需启停、删除或保留文件及离线恢复。

当前发行版是 **songyuan 专用适配**，应用来自同机型、同版本国行 ROM，保留官方原始签名。项目源自 HyperOS3EULocalization，在此基础上实现官方国际版适配、依赖修复及组件管理。

> 当前仅验证下表中的系统组合。安装器会检查机型和版本，不适用于所有 HyperOS 3 设备；不要修改检查条件后跨机型刷入。

## 适配与运行要求

| 项目 | 当前范围 |
| --- | --- |
| 设备代号 | `songyuan` |
| 目标系统 | 官方国际版 `OS3.0.306.0.WGNMIXM` |
| Android / 架构 | Android 16 / SDK 36 / ARM64 |
| 国行组件来源 | 同机型 `OS3.0.306.0.WGNCNXM` |
| 模块版本 / ID | `3.1.0-songyuan` / `songyuan_cn_complete` |
| 已验证 Root 环境 | KernelSU 3.3.1 + `magic_mount_rs` 4.0.11-930 |
| SELinux | Enforcing |
| 用户范围 | 主用户 `0`；未验证多用户、工作资料的数据切换 |

KernelSU 需要可用的系统文件挂载能力。真机验证使用上述挂载元模块，普通应用无需为此授予 Root。App Profile 中若主动隐藏模块，可能导致应用看不到替换文件；本项目未验证其他 Root 管理器及所有挂载实现。

## 功能清单

**默认全部开启**。19 个功能对应 31 个包、41 个国行原签名 APK（包含服务框架的完整 split APK），另带 4 个下载器原生库和 1 个国际版时钟回退 APK。功能开关和共享依赖统一由构建时生成的 `catalog.json` 管理。

| 功能 | 主要包名 | 说明 |
| --- | --- | --- |
| 小米智能卡 | `com.miui.tsmclient` | 国行 NFC 共享 UID 迁移与回退 |
| 小米钱包 | `com.mipay.wallet` | 钱包客户端 |
| 小米支付 / NextPay | `com.miui.nextpay` | 支付链路组件 |
| 银联 TSM | `com.unionpay.tsmservice.mi` | 保留银联原始证书 |
| 小米支付服务 | `com.xiaomi.payment` | 支付服务依赖 |
| 小爱语音 | `com.miui.voiceassist` | 包含语音唤醒等关联依赖 |
| 小爱视觉 | `com.xiaomi.aiasst.vision` | 国行视觉组件 |
| 小爱服务 | `com.xiaomi.aiasst.service` | 国行助手服务 |
| 负一屏 | `com.miui.personalassistant` | 国行内容与卡片入口 |
| 短信 | `com.android.mms` | 保留短信应用原始证书 |
| 传送门 | `com.miui.contentextension` | 包含内容捕捉依赖 |
| 黄页 | `com.miui.yellowpage` | 国行黄页与服务入口 |
| 快应用框架 | `com.miui.hybrid` | 国行快应用运行框架 |
| 小米应用商店 | `com.xiaomi.market` | 配套国行下载服务与库 |
| 云服务 | `com.miui.cloudservice` | 联动账号、同步、备份、推送、SIM 激活与国行桌面 |
| 日历 | `com.android.calendar` | 同版本国行日历 |
| 时钟 | `com.android.deskclock` | 国行时钟；关闭时提供国际版回退 |
| 主题与壁纸 | `com.android.thememanager` | 包含 `com.miui.themestore` |
| 相册 | `com.miui.gallery` | 包含 `com.hyper.gallery.plugin` 与同步修复 |

账号、下载服务、权限管理器等为共享依赖：只要仍有功能需要，就会继续保留。云备份需要国行桌面命名空间，启停云服务会联动国行桌面与 POCO 桌面，并迁移布局。

系统区域由所有已开启功能共享。有任何国行组件仍开启时会保留 CN 属性，因此单独关闭一个应用不代表可以为该进程单独恢复地区判断。

## 下载和安装

1. 在 [Actions](https://github.com/Android-Modules/HyperOS-CN-Bridge/actions/workflows/release.yml) 打开一次 **成功的** `Build Songyuan Module` 运行，下载 `HyperOS-CN-Bridge-songyuan-运行编号` Artifact。下载 Artifact 需要登录 GitHub，保留期为 30 天。
2. 解压 Artifact，得到真正的模块 `songyuan-cn-complete-OS3.0.306.0.zip`、对应 `.sha256`、`sources-complete.json` 和 `build-info.json`。**不要把 Artifact 外层 ZIP 或 ROM 构建资源当模块安装。**
3. 确认机型、系统版本及挂载能力符合上表，并备份重要应用数据。涉及 NFC 身份和系统应用切换，应保留可用的模块恢复途径。
4. 在 KernelSU 管理器内选择安装模块 ZIP，检查安装结果，然后手动重启并解锁主用户。
5. 打开模块卡片的 WebUI；也可使用模块操作按钮打开。状态页应显示配置已应用，核对实际组件加载情况。

校验下载文件：

```bash
# Linux
sha256sum -c songyuan-cn-complete-OS3.0.306.0.zip.sha256

# macOS
shasum -a 256 -c songyuan-cn-complete-OS3.0.306.0.zip.sha256
```

## WebUI 使用

页面完全离线，包含组件、状态与说明三个页面，支持深色模式。组件图标优先从当前已安装应用读取，未安装或读取失败时显示占位图。

- **启用**：从 Root 私有的压缩安装源恢复所需组件，无需重新联网下载。
- **关闭 → 删除（默认）**：下次启动移除该组件的国行展开文件与受管理的应用更新层。私有安装源和切换备份继续保留。
- **关闭 → 保留**：保留文件和受管理的更新，便于再次启用时恢复。
- **保存配置**：准备下一次启动的文件树及数据快照，随后由用户手动重启。WebUI 不会自动重启手机。
- **状态**：区分等待手动重启、启动配置中、应用失败和已应用；以本次启动实际完成结果为准。

保存时需先解锁主用户。保存后应及时重启，快照之后继续使用待关闭应用产生的数据不在该次快照内。关闭国行专属应用后，该应用会消失；国际版原有对应应用时，切回 ROM 原版。

组件配置、压缩安装源、更新备份和迁移数据位于 Root 私有目录 `/data/adb/songyuan_cn/`。删除组件文件不会以照片、短信、账号或 NFC 应用数据为删除目标；但不同版本之间的业务数据兼容性与硬件安全状态不能由文件备份保证。

原始设计稿保存在 [songyuan/design/webui-v1.png](songyuan/design/webui-v1.png)，实际页面以 [WebUI 源码](songyuan/module/webroot/) 为准。

## 应用商店更新后还能用吗？

**同包名、同签名的官方国行更新通常可以继续生效。** Android 的 `/data/app` 更新会优先于模块内置 APK，模块继续提供区域属性、权限、共享依赖和启动修复。WebUI 的组件详情及说明页也注明了这个范围。

不能保证所有未来版本兼容本系统。云服务、账号、推送、NFC 和支付等核心组件建议保持配套版本。不要混装改签名、其他机型或来源不明的 APK。

WebUI 关闭组件时会同时处理受管理的 `/data/app` 更新层，避免只移除模块 APK 后仍运行国内版：保留模式可在重启启用时恢复保留更新，删除模式再次启用时恢复模块配套版本。

## 已集成的修复

| 问题 | 模块内的处理 |
| --- | --- |
| 主题商店闪退 | 补齐 `ro.miui.support.system.app.uninstall.v2=true` |
| 主题、壁纸和应用商店下载失败 | 配套国行 DownloadProvider、特权权限和 4 个原生库，迁移下载数据库字段 |
| 国行 / 国际版切换时反复软重启 | 显式处理下载数据库 CN 125 / Global 123 的版本差异，校验迁移副本并等待 FBE 解锁后原子发布 |
| 账号入口无响应、智能卡未加载 | 固定发布目录 0755、文件 0644，避免私有 umask 使系统无法遍历 APK |
| 智能卡安装后不可用 | 启动扫描前处理独立 UID 与 `android.uid.nfc` 的差异，同时处理数据属主和 SELinux 标签 |
| 云备份显示本机不可用 | 使用同 ROM 国行桌面及对应命名空间，切换时迁移桌面布局 |
| 相册不能同步 | 修复账号访问权限、AppOps 和系统同步注册；成功后不持续覆盖用户的同步选择 |
| 查找设备定位没有结果 | 首次启用云服务时修复已有官方国行查找设备的运行时定位权限与 UID AppOps，后续保留用户权限选择 |
| 已重启仍显示等待重启 | 按待处理任务、启动提交和最终结果区分状态 |
| 关闭国内时钟后时钟消失 | 提供同签名国际版回退 APK，并登记应用更新以保留可扫描入口及原闹钟 |

查找设备修复在已安装官方国行查找设备更新的测试环境验证，模块没有把该更新列入 31 个内置包。不能据此保证任意国际版查找设备版本都有相同行为。

真机验证记录见 [VALIDATION.md](songyuan/VALIDATION.md)，技术说明见 [songyuan/README.md](songyuan/README.md)，代码复核见 [CODE_REVIEW.md](songyuan/CODE_REVIEW.md)。当前已验证组件加载、主题及商店下载、云恢复入口、相册云端列表、定位刷新、时钟功能，以及全关闭 / 全开启回退。**实体 NFC 刷卡、银行卡支付、全部原片下载和整机云恢复未完成业务验收**。

## 停用、回退与卸载

1. 在 WebUI 关闭需要回退的组件，保存配置。
2. 手动重启并解锁，等待状态显示已应用，确认国际版应用、桌面和时钟正常。
3. 需要卸载整个模块时，先完成全部组件关闭及回退，再从 KernelSU 移除模块并重启。

直接停用或移除整个模块不会执行 WebUI 的 UID、下载数据库、应用更新和桌面数据迁移。权限管理器采用同签名应用更新安装，单纯隐藏模块不会自动撤销这一层。

NFC 数据虽有备份，Android Keystore 仍与 UID 关联，身份切换后可能需要重新登录或配置卡片。硬件安全元件、银行卡或交通卡发行状态不能通过普通文件备份保证迁移。

若无法正常进入系统，先通过当前 Root 方案支持的安全模式或恢复方式停用模块，再处理回退。不要恢复很久以前的整份 `packages.xml`；它只适用于同一次失败切换的即时回滚。

## 从源码构建

### 依赖

- Python 3.10+、JDK 17+。
- Android SDK：`platforms;android-36`、`build-tools;36.0.0`。
- GitHub CLI `gh`，用于下载锁定的构建资源。
- 可选：Node.js 和 Playwright，用于 WebUI 自动化验证。

构建脚本从 `JAVA_HOME` 或 `PATH` 寻找 Java，从 `ANDROID_SDK_ROOT` / `ANDROID_HOME` 读取 SDK 路径；macOS 未设置 SDK 变量时使用 `~/Library/Android/sdk`。`--build-tools` 可以显式覆盖 SDK Build Tools 目录。

### 获取输入并打包

```bash
git clone https://github.com/Android-Modules/HyperOS-CN-Bridge.git
cd HyperOS-CN-Bridge

# 在全新检出中执行；rom/ 不应已存在。
gh release download rom-songyuan-OS3.0.306.0 \
  --repo Android-Modules/HyperOS-CN-Bridge \
  --pattern songyuan-rom-inputs.tar.gz --dir dist
python3 songyuan/tools/rom_inputs.py extract

# 使用本机实际安装路径设置 JAVA_HOME 和 ANDROID_HOME，然后构建。
python3 songyuan/tools/build.py
```

资源来自固定的 [ROM 输入 Release](https://github.com/Android-Modules/HyperOS-CN-Bridge/releases/tag/rom-songyuan-OS3.0.306.0)，仅供构建，不能刷入。完整压缩包及每个输入文件的 SHA256 锁定于 [rom-inputs.lock.json](songyuan/rom-inputs.lock.json)，解压前后均会验证。ROM 二进制不存入 Git 历史或 Git LFS。

默认构建完整功能包。`--without-wallet` 为开发隔离诊断入口，不是本仓库默认交付配置。产物位于 `dist/`：

| 文件 | 用途 |
| --- | --- |
| `songyuan-cn-complete-OS3.0.306.0.zip` | 可安装的完整模块 |
| 同名 `.zip.sha256` | 本次构建校验值 |
| `sources-complete.json` | 每个 APK 的包名、版本、原始证书和文件哈希 |
| `build-info.json` | Actions 附加的提交、输入哈希和工作流地址 |

构建会编译 Java 修复工具为 `repair.jar`，验证每个原始 APK 的包名、Android 16 签名和预期证书，提取必要 ARM64 库，生成组件目录与包级权限清单，并复核最终 ZIP 内的文件哈希及 CRC。APK 不修改、不重签；没有输入资源时构建直接失败，不生成缺应用的空壳模块。

从本地 ROM 提取物更新输入的维护命令为 `python3 songyuan/tools/rom_inputs.py pack --source /path/to/rom`。所需目录由 `build.py` 的 `SPECS` 决定，国行 product 应用位于 `cn/app`、`cn/priv-app` 或 `cn/data-app`，其他分区位于 `cn/system` 和 `cn/system_ext`，国际版时钟位于 `global/data-app/MIUIDeskClockGlobal`。修改资源时应使用新 Release 标识，提交新锁文件后再构建。

### GitHub Actions 打包

在仓库 **Actions → Build Songyuan Module → Run workflow** 选择分支并运行，也可执行：

```bash
gh workflow run release.yml --repo Android-Modules/HyperOS-CN-Bridge --ref main
```

推送 `v*` 标签同样触发构建。工作流在 Ubuntu 24.04 上安装 Java / SDK，下载锁定的 ROM 原始资源，执行完整构建、签名和哈希检查，再使用 Chromium 运行 WebUI 自动化验证。成功后上传模块、校验值、来源清单及构建记录；当前不自动发布正式版本 Release。

工作流仅需仓库读取权限，官方 Actions 固定到提交 SHA。不需要额外配置个人 Token；`GITHUB_TOKEN` 用于读取资源。Fork 默认仍引用锁文件指向的资源仓库。

本地运行 WebUI 测试（先完成完整模块构建）：

```bash
npm install --prefix /tmp/cn-bridge-test playwright@1.58.2
/tmp/cn-bridge-test/node_modules/.bin/playwright install chromium
PLAYWRIGHT_MODULE=/tmp/cn-bridge-test/node_modules/playwright/index.mjs \
  node songyuan/tools/test_webui.mjs
```

可用 `CHROME_EXECUTABLE` 指定已有 Chrome。测试覆盖 19 个默认开关、目录归属、删除/保留、取消、保存、不自动重启、更新说明、移动端布局、深色模式、失败/完成状态及真实图标显示。

## 项目结构与排查

```text
.github/workflows/release.yml   # 完整构建与 WebUI 验证
META-INF/                      # 模块安装入口
songyuan/
  module/                      # 安装、启动、回退脚本与 WebUI
  tools/                       # 构建器、组件归属、Java 控制器、诊断与测试
  design/                      # 初始 WebUI 设计稿和提示文本
  rom-inputs.lock.json          # ROM 构建输入与逐文件哈希
  README.md                    # 设备适配技术说明
  VALIDATION.md                # 真机验收范围和历史交付记录
  CODE_REVIEW.md               # 运行时代码复核
  COMPARISON.md                # 上游及同版本 ROM 的组件对比
rom/                           # 本地 ROM 输入，Git 忽略
dist/                          # 本地构建输出，Git 忽略
diagnostics/                   # 私有日志与验证数据，Git 忽略
```

根目录的旧版安装脚本、`lang/`、`tools/` 与 `overlay-src/` 作为上游参考保留，**不参与当前 songyuan 发行构建**；当前发行没有将旧版 Focus / XMS overlay 纳入功能范围。

排查时先记录 ROM 版本、模块版本、挂载环境、WebUI 状态及复现步骤。手机启动报告位于 `/data/adb/songyuan_cn/boot-status.txt`。开发者可用 `python3 songyuan/tools/verify_device.py --adb /path/to/adb` 对照本次构建来源清单检查手机真正加载的 APK / 库；官方更新后字节哈希不同，需要另行核对版本与签名。

诊断目录、账号数据、应用数据库和系统包设置均不随源码发布。提交 Issue 时请先移除账号、定位、照片、手机号等个人信息，不要上传整个私有备份目录。

## 许可与致谢

本仓库脚本与源码沿用 [Apache License 2.0](LICENSE)。Xiaomi / MIUI / HyperOS、银联及其他 ROM 内置 APK、库、图标和商标的权利归各自权利人，代码许可证不授予这些资源的额外使用或再分发权利。本项目不代表 Xiaomi 官方。

感谢上游 [LSHFGJ/HyperOS3EULocalization](https://github.com/LSHFGJ/HyperOS3EULocalization)、原始工作 [MinaMichita/MiuiEULocalizationToolsBox](https://github.com/MinaMichita/MiuiEULocalizationToolsBox)，以及 [KernelSU](https://github.com/tiann/KernelSU) 与 [magic_mount_rs](https://github.com/KernelSU-Modules-Repo/magic_mount_rs) 项目。
