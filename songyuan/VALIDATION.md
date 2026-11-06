# 真机验收：3.1.0-songyuan

目标手机：songyuan，官方国际版 OS3.0.306.0.WGNMIXM / Android 16；原签名组件来自同机型 OS3.0.306.0.WGNCNXM。日期为手机当地时间 2026-11-06。

默认 19 个功能全部开启，包含截图的 14 个应用及云服务、日历、时钟、主题、相册。共 31 个包、41 个国行原签名 APK、4 个下载器原生库，另存 1 个原签名国际版时钟回退 APK。

## 本轮修复与真机结果

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

## 保留的业务实测记录

下列下载业务在 3.0.1 已实际执行，3.1.0 保留相同原签名应用及依赖；与本轮的加载、状态和回退测试区分。

- 主题：下载实际成功，16,074,940 字节；免费在线壁纸实际应用成功，原壁纸归档后已恢复。主题商店闪退所需能力属性继续保留。
- 应用商店：实际下载安装 com.phoenix.read 7.4.3.32，安装来源 com.xiaomi.market。
- 云恢复：多份备份详情可进入选择页；未启动会覆盖现有数据的恢复。
- 相册：云端照片、视频列表及缩略图曾实际加载；未验证全部原片下载。
- 时钟：国行 17.66.0，秒表运行/暂停/复位，3 秒倒计时结束及临时闹钟响铃均已验证；临时项目已清理，原 3 个闹钟保留。
- 日历、小爱、短信、负一屏、传送门：对应国行页面可进入；未发送短信、进行支付或验证实体 NFC 刷卡。

## 真机测试时的历史交付

以下哈希记录本轮真机验收的本地构建，不是后续每次 Actions 构建的固定值。云端产物应以对应运行附带的 SHA256 和 build-info.json 为准。

完整包：`dist/songyuan-cn-complete-OS3.0.306.0.zip`。
SHA256：`a537971bb4803ee0bbeb7da516aa657cb255d5579af61d7918fa9fc92d69559a`。
手机另存 `/sdcard/Download/songyuan-cn-complete-OS3.0.306.0.zip` 及 SHA256 文件，缓存源与交付包哈希一致。最终安装日志见 `diagnostics/webui/install-3.1.0-final.txt`，运行文件哈希与交付 ZIP 已逐项核对。
来源：`dist/sources-complete.json`，逐文件校验：`diagnostics/verification-complete.json`。

运行数据、账号相关归档和原包设置位于 Root 私有 `/data/adb/songyuan_cn/`，电脑副本位于忽略的 `diagnostics/private/`。不将这些文件打进模块，也不公开。旧整份包设置只用于当次即时失败回退，不能日后覆盖系统全部包设置。

WebUI 的删除只移除国内版展开文件及对应更新层，保留私有应用数据备份与压缩安装源。系统区域由所有启用组件共享；无法按单个应用隔离。核心云/账号/NFC 更新建议配套；“同包名同签名官方更新通常可用”不代表保证所有后续版本兼容。
