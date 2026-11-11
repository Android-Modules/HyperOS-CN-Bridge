package com.dalong.cnbridge;

import android.content.pm.ApplicationInfo;
import android.util.Log;
import io.github.libxposed.api.XposedModule;
import io.github.libxposed.api.XposedInterface.HookHandle;
import java.io.FileInputStream;
import java.lang.reflect.Method;
import java.security.MessageDigest;
import java.util.*;

/** Select the shipped unified model only inside songyuan's AI search workers. */
public final class AiSearchCompat {
    public static final String PACKAGE = "com.xiaomi.aicr";
    private static final String APK_SHA256 = "f1f7314612528e12c6dcf1e9f13dfb86dda0bde1c6bf9154d114051c199f4d90";
    private static final Set<String> PROCESSES = new HashSet<>(Arrays.asList(
            PACKAGE + ":searchDataService", PACKAGE + ":searchService",
            PACKAGE + ":bertAlgo", PACKAGE + ":clipAlgo"));

    public static boolean worker(String process) { return PROCESSES.contains(process); }

    private static String digest(String path) throws Exception {
        MessageDigest hash = MessageDigest.getInstance("SHA-256");
        try (FileInputStream in = new FileInputStream(path)) {
            byte[] buffer = new byte[262144]; int count;
            while ((count = in.read(buffer)) != -1) hash.update(buffer, 0, count);
        }
        StringBuilder hex = new StringBuilder();
        for (byte b : hash.digest()) hex.append(String.format(Locale.ROOT, "%02x", b & 255));
        return hex.toString();
    }

    public static void install(XposedModule module, ApplicationInfo app, String process) {
        List<HookHandle> handles = new ArrayList<>();
        try {
            Class<?> properties = Class.forName("android.os.SystemProperties");
            Method get = properties.getDeclaredMethod("get", String.class); get.setAccessible(true);
            if (!"songyuan".equals(get.invoke(null, "ro.product.device")) ||
                    !"SM8850".equals(get.invoke(null, "ro.soc.model")) ||
                    !"OS3.0.306.0.WGNMIXM".equals(get.invoke(null, "ro.build.version.incremental")) ||
                    !"CN".equals(get.invoke(null, "ro.miui.region")) ||
                    !"songyuan".equals(get.invoke(null, "ro.product.mod_device"))) return;
            if (!APK_SHA256.equals(digest(app.sourceDir))) {
                Log.w("CnBridgeAI", "SKIP unverified AICR APK: " + process); return;
            }
            for (Method method : properties.getDeclaredMethods()) {
                Class<?>[] args = method.getParameterTypes();
                if (!(method.getName().equals("get") || method.getName().equals("native_get")) ||
                        method.getReturnType() != String.class || args.length == 0 || args[0] != String.class) continue;
                handles.add(module.hook(method).intercept(chain ->
                        "ro.product.mod_device".equals(chain.getArg(0)) ? "songyuan_global" : chain.proceed()));
            }
            if (handles.isEmpty() || !"songyuan_global".equals(get.invoke(null, "ro.product.mod_device")))
                throw new IllegalStateException("Model route hook unavailable");
            Log.i("CnBridgeAI", "UNIFIED_MODEL process=" + process + " region=" + get.invoke(null,"ro.miui.region"));
        } catch (Throwable error) {
            for (HookHandle handle : handles) try { handle.unhook(); } catch (Throwable ignored) { }
            Log.e("CnBridgeAI", "Model adaptation skipped: " + process, error);
        }
    }
}
