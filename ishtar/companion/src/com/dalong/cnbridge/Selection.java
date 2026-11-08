package com.dalong.cnbridge;

import android.content.SharedPreferences;
import android.content.Context;
import android.provider.Settings;

/** A saved generation starts on the next boot, even before the root late service runs. */
final class Selection {
    static String boot() throws Exception {
        Class<?> type=Class.forName("android.app.ActivityThread");
        Object thread=type.getMethod("currentActivityThread").invoke(null);
        java.lang.reflect.Field bound=type.getDeclaredField("mBoundApplication");bound.setAccessible(true);
        Object data=bound.get(thread);
        java.lang.reflect.Field info=data.getClass().getDeclaredField("info");info.setAccessible(true);
        java.lang.reflect.Method create=Class.forName("android.app.ContextImpl").getDeclaredMethod(
                "createAppContext",type,Class.forName("android.app.LoadedApk"));create.setAccessible(true);
        Context context=(Context)create.invoke(null,thread,info.get(data));
        return Settings.Global.getString(context.getContentResolver(),Settings.Global.BOOT_COUNT);
    }
    static String prefix(SharedPreferences prefs) throws Exception {
        String staged=prefs.getString("activationBoot", "");
        String boot=boot();
        return !staged.isEmpty()&&(boot==null||boot.isEmpty()||staged.equals(boot))?"previous_":"";
    }
}
