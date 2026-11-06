# 仓库与本机 ROM 提取物对比

对象为上游原版 v2.0 ZIP、同机型 Global/CN 同版本 ROM，以及本地另一项目提取的分区。

已将 .workbuddy 提取的 CN/Global product、mi_ext 与 Downloads 内对应 ROM 的 super.img 内容逐字节比较，一致后复用这些提取物。补充使用的 CN system、system_ext 也已核对。实际安装的 APK 均保留 ROM 原始签名，没有修改或重签名。

## 截图中的 14 个包

| 包名 | 仓库原版 ZIP | 同版本国行 ROM |
| --- | --- | --- |
| com.android.mms | 17.0.1.32 | 17.0.3.2 |
| com.mipay.wallet | 6.108.1.5626.2710 | 6.105.0.5596.2691 |
| com.miui.contentextension | 4.1.3 | 4.2.1 |
| com.miui.hybrid | 1.30.7.3 | 1.30.7.3 |
| com.miui.nextpay | 25.12.19.1 | 26.01.30.1 |
| com.miui.personalassistant | 25.30.50-01231139 | 25.30.76-04301124 |
| com.miui.tsmclient | 26.01.06.1.f | 26.06.02.1.f |
| com.miui.voiceassist | 7.10.3.1514 | 7.13.32.0511 |
| com.miui.yellowpage | 23.0.260108 | 23.0.260506 |
| com.unionpay.tsmservice.mi | 01.00.57 | 01.00.57 |
| com.xiaomi.aiasst.service | 6.0.3 | 6.0.3 |
| com.xiaomi.aiasst.vision | 5.10.1.40 | 5.12.9.20-06171423 |
| com.xiaomi.market | 4.106.2 | 4.116.1 |
| com.xiaomi.payment | 2.9.4 | 2.9.8 |

版本号有高有低，因此没有简单地以“更新”作为兼容判断依据。本次使用同机型、同系统版本的国行完整依赖集合。

## 原版未生效的原因与处理

- 原仓库主要面向 xiaomi.eu，本机是官方 Global；同包国际版目录与原始包解析缓存需要一并处理。
- 本机故诗声音模块的 product 符号链接和重复 media 目录与挂载结构冲突；已改成单一 system/product/media，262 个声音文件保持原样。
- 智能卡的 Global 独立 UID 与国行 NFC 共享 UID 不同。已备份后迁移为 1027，并保留原应用数据。
- Global 云服务旧清单缓存与国行代码混合会触发 Firebase 组件崩溃；启动时重建目标包的解析缓存，保留应用数据。
- Global 权限管理器缺少国行剪贴板规则；采用原签名的国行应用更新。Global 下载服务缺少国行商店需要的字段和原生库，已补全对应服务与四个库。
- 早期测试的已更新 Global 下载器数据库版本是 125，但 ROM 原版 Global 实际要求 123，CN 要求 125。3.1.0 在保留记录与字段的前提下显式迁移版本，并在 FBE 解锁后原子发布；详见 VALIDATION.md，不能将旧更新版本当作 ROM 原版要求。
- 国内主题商店会检查系统应用卸载能力属性，缺少该属性会主动结束页面；3.0.1 补齐对应属性。
- 相册原账号访问权限与系统同步登记不完整；3.0.1 修复权限并以系统 UID 登记同步，真机已拉取云端照片。
- 国内云备份按桌面命名空间判断兼容性。3.0.1 增加同 ROM 国行桌面及其属性，备份并迁移原 POCO 布局；恢复详情已能加载。

## 新增范围

在截图全部 14 个包以外，补充了云服务、云同步、云备份、小米账号、国区服务框架及 SIM 激活组件；另加入日历、时钟、主题、主题商店、相册及相册插件，并补齐内容捕捉、语音唤醒、国行桌面和两个系统依赖。最终为 31 个包、41 个 APK，另含 4 个外置原生库。

仓库原有 Focus/XMS 通知 overlay 不属于截图要求，本次未加入；未替换整套 framework。此适配仅限 songyuan / Android 16 / OS3.0.306.0.WGNMIXM。
