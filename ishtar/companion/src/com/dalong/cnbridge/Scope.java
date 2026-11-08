package com.dalong.cnbridge;

import java.util.*;

/** Only explicit application processes are eligible. Never scope android or system_server. */
public final class Scope {
    public static final Map<String,List<String>> FEATURES = new LinkedHashMap<>();
    static {
        add("tsm", "com.miui.tsmclient");
        add("wallet", "com.mipay.wallet");
        add("nextpay", "com.miui.nextpay");
        add("unionpay", "com.unionpay.tsmservice.mi");
        add("payment", "com.xiaomi.payment");
        add("voice", "com.miui.voiceassist", "com.miui.voicetrigger");
        add("vision", "com.xiaomi.aiasst.vision");
        add("aiasst", "com.xiaomi.aiasst.service");
        add("personal", "com.miui.personalassistant");
        add("sms", "com.android.mms");
        add("portal", "com.miui.contentextension");
        add("yellow", "com.miui.yellowpage");
        add("hybrid", "com.miui.hybrid");
        add("market", "com.xiaomi.market");
        add("cloud", "com.miui.cloudservice", "com.miui.cloudbackup", "com.miui.micloudsync");
        add("calendar", "com.android.calendar");
        add("clock", "com.android.deskclock");
        add("theme", "com.android.thememanager");
        add("gallery", "com.miui.gallery");
    }
    private static void add(String id, String... packages) { FEATURES.put(id, Arrays.asList(packages)); }
    public static Set<String> packages(Collection<String> enabled) {
        Set<String> result = new HashSet<>();
        for (String id : enabled) {
            List<String> list = FEATURES.get(id);
            if (list == null) throw new IllegalArgumentException("Unknown feature: " + id);
            result.addAll(list);
        }
        return result;
    }
    public static final Set<String> ALL = packages(FEATURES.keySet());
}
