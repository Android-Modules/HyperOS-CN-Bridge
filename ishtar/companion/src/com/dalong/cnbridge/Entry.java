package com.dalong.cnbridge;

import android.content.SharedPreferences;
import android.os.Process;
import android.util.Log;
import io.github.libxposed.api.*;
import java.lang.reflect.*;
import java.util.*;

public final class Entry extends XposedModule {
    private String processName;
    private boolean handled, systemServer;
    private static final Map<String,String> CN = new HashMap<>();
    static {
        CN.put("ro.product.mod_device", "ishtar");
        CN.put("ro.miui.region", "CN"); CN.put("ro.vendor.miui.region", "CN");
        CN.put("ro.miui.cust_variant", "cn"); CN.put("ro.vendor.miui.cust_variant", "cn");
    }
    @Override public void onModuleLoaded(ModuleLoadedParam param) {
        processName = param.getProcessName(); systemServer = param.isSystemServer();
    }
    @Override public void onPackageLoaded(PackageLoadedParam param) {
        if (handled || systemServer || !param.isFirstPackage() || Process.myUid()/100000 != 0 || !Scope.ALL.contains(param.getPackageName())) return;
        handled = true;
        // A shared process cannot isolate a different package. Fail closed.
        String pkg = param.getPackageName();
        if (!(processName.equals(pkg) || processName.startsWith(pkg + ":"))) {
            Log.w("CnBridge", "SKIP shared process: " + pkg + " / " + processName); return;
        }
        List<HookHandle> hooks = new ArrayList<>();
        Map<Field, Boolean> originalFlags = new LinkedHashMap<>();
        try {
            Class<?> properties = Class.forName("android.os.SystemProperties");
            Method get = properties.getDeclaredMethod("get", String.class); get.setAccessible(true);
            java.io.File marker=new java.io.File("/system/etc/cnbridge-ishtar");
            if (!marker.isFile() || !"ishtar-elite308-scoped-v1".equals(new String(java.nio.file.Files.readAllBytes(marker.toPath()),"UTF-8").trim()) ||
                    !"ishtar".equals(get.invoke(null, "ro.product.device")) ||
                    !"OS3.0.308.0.WMACNXM".equals(get.invoke(null, "ro.build.version.incremental"))) return;
            SharedPreferences prefs = getRemotePreferences("localization");
            String prefix=Selection.prefix(prefs);
            boolean selected = prefs.getStringSet(prefix+"packages", Collections.emptySet()).contains(pkg);
            if (selected) {
                for (Method method : properties.getDeclaredMethods()) {
                    Class<?>[] args = method.getParameterTypes();
                    if (!(method.getName().equals("get") || method.getName().equals("native_get")) ||
                            method.getReturnType() != String.class || args.length == 0 || args[0] != String.class) continue;
                    hooks.add(hook(method).intercept(chain -> {
                        String value = CN.get((String)chain.getArg(0));
                        return value == null ? chain.proceed() : value;
                    }));
                }
                if (hooks.isEmpty()) throw new IllegalStateException("No compatible property methods");
                for (String name : new String[]{"miui.os.Build", "miuix.os.Build"}) {
                    try {
                        Class<?> build = Class.forName(name, true, param.getDefaultClassLoader());
                        for (String flag : new String[]{"IS_INTERNATIONAL_BUILD", "IS_GLOBAL_BUILD"}) {
                            try { Field f = build.getDeclaredField(flag); f.setAccessible(true);
                                originalFlags.put(f, f.getBoolean(null)); f.setBoolean(null, false); }
                            catch (NoSuchFieldException ignored) { }
                        }
                    } catch (ClassNotFoundException ignored) { }
                }
                if ("com.miui.gallery".equals(pkg)) {
                    // The Global APK hard-codes this method to true; region properties alone
                    // do not select its existing Xiaomi Cloud settings and sync implementation.
                    Class<?> galleryBuild = Class.forName("com.miui.gallery.util.BuildUtil", false, param.getDefaultClassLoader());
                    Method global = galleryBuild.getDeclaredMethod("isGlobal");
                    if (global.getReturnType() != boolean.class || !Modifier.isStatic(global.getModifiers()))
                        throw new IllegalStateException("Unsupported gallery build selector");
                    hooks.add(hook(global).intercept(chain -> false));
                    Method created = Class.forName("com.miui.gallery.GalleryApp", false, param.getDefaultClassLoader()).getDeclaredMethod("onCreate");
                    hooks.add(hook(created).intercept(chain -> {
                        Object result = chain.proceed();
                        GallerySync.ensureRegistration((android.content.Context) chain.getThisObject());
                        return result;
                    }));
                    Class<?> sync = Class.forName("com.miui.gallery.util.SyncUtil", false, param.getDefaultClassLoader());
                    for (Method method : sync.getDeclaredMethods()) {
                        Class<?>[] types = method.getParameterTypes();
                        if (!method.getName().equals("setSyncAutomatically") || types.length < 2 ||
                                types[0] != android.content.Context.class || types[1] != boolean.class) continue;
                        hooks.add(hook(method).intercept(chain -> {
                            Object result = chain.proceed();
                            if (Boolean.TRUE.equals(chain.getArg(1)))
                                GallerySync.ensureRegistration((android.content.Context) chain.getArg(0));
                            return result;
                        }));
                    }
                    Log.i("CnBridge", "GALLERY global=" + global.invoke(null));
                }
            }
            Object international = Class.forName("miui.os.Build").getField("IS_INTERNATIONAL_BUILD").get(null);
            Log.i("CnBridge", "STATE pkg="+pkg+" process="+processName+" selected="+selected+
                    " revision="+prefs.getString(prefix+"revision", "none")+" region="+get.invoke(null,"ro.miui.region")+
                    " mod_device="+get.invoke(null,"ro.product.mod_device")+" international="+international);
        } catch (Throwable error) {
            for (HookHandle handle : hooks) try { handle.unhook(); } catch (Throwable ignored) { }
            for (Map.Entry<Field, Boolean> flag : originalFlags.entrySet())
                try { flag.getKey().setBoolean(null, flag.getValue()); } catch (Throwable ignored) { }
            Log.e("CnBridge", "Adaptation failed for " + pkg, error);
        }
    }
}
