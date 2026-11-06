"""One ownership table for the installer, boot reconciler and WebUI."""
FEATURES = [
    ("tsm", "小米智能卡", "钱包与支付", "公交卡、门禁卡与 NFC 智能卡客户端", "paired"),
    ("wallet", "小米钱包", "钱包与支付", "国内钱包与生活服务", "official"),
    ("nextpay", "小米支付 / NextPay", "钱包与支付", "国内支付组件", "paired"),
    ("unionpay", "银联 TSM 服务", "钱包与支付", "银联安全支付依赖", "paired"),
    ("payment", "小米支付服务", "钱包与支付", "钱包支付服务组件", "paired"),
    ("voice", "小爱语音", "日常与智能", "小爱语音与语音唤醒", "official"),
    ("vision", "小爱视觉", "日常与智能", "识图、扫一扫与视觉服务", "official"),
    ("aiasst", "小爱服务", "日常与智能", "小爱智能服务", "paired"),
    ("personal", "负一屏", "日常与智能", "国内信息卡片与快捷服务", "official"),
    ("sms", "短信", "日常与智能", "国行短信应用，保留短信数据", "official"),
    ("portal", "传送门", "日常与智能", "内容识别与跨应用服务", "official"),
    ("yellow", "黄页", "日常与智能", "国内号码与生活服务", "official"),
    ("hybrid", "快应用框架", "日常与智能", "国内快应用运行环境", "paired"),
    ("market", "小米应用商店", "日常与智能", "国内应用下载与更新", "official"),
    ("cloud", "云服务", "云与系统应用", "国内云服务与备份；包含配套国行桌面", "paired"),
    ("calendar", "日历", "云与系统应用", "国行日历与节假日服务", "official"),
    ("clock", "时钟", "云与系统应用", "国行闹钟、计时器与秒表", "official"),
    ("theme", "主题与壁纸", "云与系统应用", "主题管理器与主题商店", "official"),
    ("gallery", "相册", "云与系统应用", "国行相册、编辑插件与云同步依赖", "official"),
]
OWNERS = {
    "com.miui.tsmclient": ["tsm"], "com.mipay.wallet": ["wallet"],
    "com.miui.nextpay": ["nextpay"], "com.unionpay.tsmservice.mi": ["unionpay"],
    "com.xiaomi.payment": ["payment"], "com.miui.voiceassist": ["voice"],
    "com.xiaomi.aiasst.vision": ["vision"], "com.xiaomi.aiasst.service": ["aiasst"],
    "com.miui.personalassistant": ["personal"], "com.android.mms": ["sms"],
    "com.miui.contentextension": ["portal"], "com.miui.yellowpage": ["yellow"],
    "com.miui.hybrid": ["hybrid"], "com.xiaomi.market": ["market"],
    "com.miui.cloudservice": ["cloud"], "com.miui.cloudbackup": ["cloud"],
    "com.miui.micloudsync": ["cloud", "gallery"],
    "com.xiaomi.account": ["cloud", "gallery", "wallet", "tsm", "voice", "vision", "personal", "theme"],
    "com.miui.contentcatcher": ["voice", "vision", "portal"],
    "com.miui.voicetrigger": ["voice"],
    "com.xiaomi.simactivate.service": ["cloud", "wallet", "tsm"],
    "com.xiaomi.xmsf": ["cloud", "gallery", "wallet", "tsm", "voice", "vision", "personal", "theme"],
    "com.android.calendar": ["calendar"], "com.android.deskclock": ["clock"],
    "com.android.thememanager": ["theme"], "com.miui.themestore": ["theme"],
    "com.miui.gallery": ["gallery"], "com.hyper.gallery.plugin": ["gallery"],
    "com.lbe.security.miui": ["*"], "com.android.providers.downloads": ["*"],
    "com.miui.home": ["cloud"],
}
ALIASES = {
    "com.miui.tsmclient": ["app/MITSMClientGlobal"],
    "com.xiaomi.aiasst.vision": ["app/AiAsstVision"],
    "com.android.mms": ["priv-app/MiuiMmsGlobal"],
    "com.miui.yellowpage": ["priv-app/MIUIYellowPageGlobal"],
    "com.xiaomi.market": ["app/MIUIMiPicks"],
    "com.android.calendar": ["app/Calendar"],
    "com.android.thememanager": ["app/MIUIThemeManagerGlobal"],
    "com.miui.gallery": ["priv-app/MIUIGalleryGlobal"],
    "com.android.deskclock": ["data-app/MIUIDeskClockGlobal"],
    "com.miui.home": ["priv-app/MiLauncherGlobal"],
    "com.xiaomi.account": ["app/MIUIXiaomiAccountGlobal"],
    "com.miui.cloudbackup": ["priv-app/MIUICloudBackupGlobal"],
    "com.xiaomi.xmsf": ["app/XiaomiServiceFrameworkGlobal"],
    "com.xiaomi.simactivate.service": ["app/XiaomiSimActivateServiceGlobal"],
}
# Package identities in the unmodified Global ROM. Calendar and GetApps have
# different identities there; turning their CN versions off reveals that ROM.
GLOBAL_PACKAGES = set("""com.miui.tsmclient com.xiaomi.aiasst.vision com.android.mms
com.miui.yellowpage com.miui.cloudservice com.miui.micloudsync com.miui.cloudbackup
com.xiaomi.account com.xiaomi.simactivate.service com.xiaomi.xmsf com.android.deskclock
com.android.thememanager com.miui.gallery com.lbe.security.miui
com.android.providers.downloads""".split())


def feature_catalog(selected):
    ids = {OWNERS[pkg][0] for _, pkg, _ in selected if OWNERS[pkg][0] != "*"}
    return [dict(id=i, name=n, category=c, description=d, update=u)
            for i, n, c, d, u in FEATURES if i in ids]
