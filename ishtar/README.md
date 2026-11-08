# ishtar EliteRomLite 补全适配（预览）

由 **dalong** 维护。该配置用于小米 13 Ultra `ishtar` 的 **EliteRomLite v4.05 / OS3.0.308.0.WMACNXM / Android 16**，模块 ID 为 `ishtar_cn_addons`。它与 songyuan 国际版转换配置分别打包、分别保存状态，不能互刷。

这台 ROM 基于国行系统，但设置为 HK / `ishtar_elite_global`，部分系统应用已改签为 Elite 证书。官方应用和已安装应用的签名不一致时，不能作为普通同签名更新直接覆盖。本配置保留现有相册、账号、云服务、短信、日历、桌面、主题管理器、NFC 和下载器，补齐缺失组件并恢复国内功能判断。

**这是受限的补全适配，不等于把 Elite ROM 完整恢复为官方国行系统。** WebUI 明确区分“模块管理的补全项”和只读的“ROM 原有应用”。原有应用的国内业务、云同步、支付和未来更新兼容性需要逐项验收。

真机安装、修复与测试范围见 [验证记录](VALIDATION.md)。

## 本次内容

9 个原签名 APK 均来自 **ishtar 同版本官方 Recovery ROM**，不使用 songyuan 306 APK：

| 功能 | 包名 |
| --- | --- |
| NextPay | `com.miui.nextpay` |
| 银联 TSM | `com.unionpay.tsmservice.mi` |
| 小米支付服务 | `com.xiaomi.payment` |
| 小爱语音 | `com.miui.voiceassist` |
| 语音唤醒 | `com.miui.voicetrigger` |
| 小爱服务 | `com.xiaomi.aiasst.service` |
| 传送门 | `com.miui.contentextension` |
| 黄页 | `com.miui.yellowpage` |
| 快应用框架 | `com.miui.hybrid` |

8 个组件功能开关，另有 1 个独立 CN 配置开关，默认全部开启。已存在的 11 组功能只显示安装情况，不伪装成模块可以切换的应用。

CN 配置将 `ro.miui.region` / `ro.vendor.miui.region` 设为 `CN`，cust variant 设为 `cn`，`ro.product.mod_device` 设为 `ishtar`。当前相册内的 `miuix.os.Build.IS_INTERNATIONAL_BUILD` 由 `mod_device` 是否包含 `_global` 决定。关闭 CN 配置后，在下次启动恢复本 ROM 的 HK / `ishtar_elite_global` 判断，不修改硬件机型。

本版本提取的官方应用中没有 songyuan 的独立 `com.miui.themestore` 和 `com.hyper.gallery.plugin` APK，因此没有跨机型补装它们。主题、相册本身保留现有 ROM 包。主题使用原管理器内的在线内容，不启用 `ro.miui.support.system.app.uninstall.v2` 独立商店模式，避免隐藏桌面入口并跳转下载缺失的商店。

## 数据与开关

- 首次安装仅允许补装当前不存在的包。系统版本、Elite 版本和必需共享库均检查后才准备文件。
- 不执行 songyuan 专用的 NFC UID / `packages.xml` 修改、POCO 桌面切换、下载数据库 123/125 迁移或 AuthManager 更新。
- 开关复用现有控制器的离线文件校验、数据快照、更新层处理和启动提交。状态保存在 `/data/adb/ishtar_cn/`，不使用 `/data/adb/songyuan_cn/`。
- 关闭默认删除展开文件；保留模式保存展开文件和受管理更新。压缩安装源和数据备份在两种模式下都会保留。
- **多用户 / 应用双开环境仅支持首次补装和保持配置升级。** 后续组件切换仍要求单用户，以免只保存主用户的数据却影响其他用户。不要删除双开数据绕过限制。
- WebUI 不自动重启。保存后手动重启并解锁主用户，等待状态显示“已应用”。
- 同包名、同签名且适配本系统的官方更新通常可继续运行；不能保证未来版本兼容。Elite 改签应用可能无法接受官方更新，模块不修改签名校验。

### 已有提示音模块的挂载冲突

真机的 `hyperos_gushi_audio/system/product -> ../product` 软链接会在 Magic Mount-rs 4.0.11 中抢占整个 product 节点，日志出现 `cannot create tmpfs on /, ignore: product`，使其他模块的 product 补丁全部被忽略。

安装器只针对这个已知模块和精确的旧布局，把实际 `product` 目录移入 `system/product`，将顶层 `product` 改为指向它的兼容链接。所有音频文件和模块保留，原链接记录保存在 `/data/adb/ishtar_cn/mount-compat/`；不会卸载模块或修改它的音频内容。未知链接布局会明确失败，不自动处理。

## 本地构建

依赖 Python 3.10+、JDK 17+、Android SDK 36 / Build Tools 36.0.0。ROM 二进制和手机采集资料均不进入 Git。

1. 从 [小米官方服务器](https://bigota.d.miui.com/OS3.0.308.0.WMACNXM/ishtar-ota_full-OS3.0.308.0.WMACNXM-user-16.0-e3d01be14a.zip) 获取同版本 Recovery ROM。
2. 按 [rom-inputs.lock.json](rom-inputs.lock.json) 检查整包 MD5 / SHA256，使用 `payload-dumper-go` 提取 `product`，再用 `fsck.erofs --extract=rom/ishtar/cn product.img` 提取内容。
3. 运行 `python3 ishtar/tools/build.py`，所有 31 个输入（9 APK 与权限 XML）必须匹配锁文件。APK 包名和原始证书单独校验。
4. 安装产物 `dist/ishtar-cn-addons-OS3.0.308.0.zip`；旁边有 SHA256 校验文件。构建后的 ZIP 逐文件复核哈希和 CRC。

只读采集工具：`python3 ishtar/tools/preflight.py --serial <明确的 ADB 序列号> --pull-apks`。它读取 ROM 属性、用户 ID、包路径、UID、系统登记证书和原始 APK 签名，默认输出到 Git 忽略的 `diagnostics/ishtar-adaptation/baseline`。不输出账号名、不修改手机。

WebUI 测试：设置 `PLAYWRIGHT_MODULE`（Playwright 模块路径，可省略）及 `CHROME_EXECUTABLE`（使用系统 Chrome 时），运行 `node ishtar/tools/test_webui.mjs`。原 songyuan 构建、ROM 输入校验和 WebUI 也需要通过回归检查。

当前 GitHub Actions 的正式打包任务仍针对 songyuan；ishtar 预览配置按上述步骤本地构建。
