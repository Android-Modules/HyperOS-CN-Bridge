package com.dalong.cnbridge;

import android.content.*;
import android.database.Cursor;
import android.net.Uri;
import android.os.*;
import android.util.Base64;
import org.json.*;
import java.util.*;
import java.util.concurrent.*;
import io.github.libxposed.service.*;

/** Root-only configuration surface; no arbitrary property keys, files, or packages. */
public final class ConfigProvider extends ContentProvider {
    private static volatile XposedService service;
    private static final CountDownLatch ready = new CountDownLatch(1);
    @Override public boolean onCreate() {
        XposedServiceHelper.registerListener(new XposedServiceHelper.OnServiceListener() {
            public void onServiceBind(XposedService value) { service = value; ready.countDown(); }
            public void onServiceDied(XposedService value) { if (service == value) service = null; }
        });
        return true;
    }
    @Override public Bundle call(String method, String arg, Bundle extras) {
        if (Binder.getCallingUid() != 0) throw new SecurityException("Root required");
        Bundle result = new Bundle();
        try {
            if (service == null) ready.await(5, TimeUnit.SECONDS);
            XposedService current = service;
            if (current == null) throw new IllegalStateException("LSPosed module is not enabled or service unavailable");
            SharedPreferences prefs = current.getRemotePreferences("localization");
            if ("scope".equals(method)) {
                if (arg == null || arg.length() > 16384) throw new IllegalArgumentException("Invalid configuration size");
                JSONObject config = new JSONObject(new String(Base64.decode(arg, Base64.DEFAULT), "UTF-8"));
                JSONArray ids = config.getJSONArray("enabled");
                Set<String> enabled = new HashSet<>();
                for (int i=0; i<ids.length(); i++) enabled.add(ids.getString(i));
                Set<String> missing = Scope.packages(enabled);
                missing.removeAll(current.getScope());
                if (!missing.isEmpty()) current.requestScope(new ArrayList<>(missing), new XposedService.OnScopeEventListener() {});
            } else if ("configure".equals(method) || "stage".equals(method)) {
                if (arg == null || arg.length() > 16384) throw new IllegalArgumentException("Invalid configuration size");
                JSONObject config = new JSONObject(new String(Base64.decode(arg, Base64.DEFAULT), "UTF-8"));
                JSONArray ids = config.getJSONArray("enabled");
                Set<String> enabled = new HashSet<>();
                for (int i=0; i<ids.length(); i++) enabled.add(ids.getString(i));
                Set<String> packages = Scope.packages(enabled);
                String revision = config.getString("revision");
                if (!revision.matches("[a-zA-Z0-9._-]{1,80}")) throw new IllegalArgumentException("Invalid revision");
                String previous=Selection.prefix(prefs);
                Set<String> previousEnabled=new HashSet<>(prefs.getStringSet(previous+"enabled",Collections.emptySet()));
                Set<String> previousPackages=new HashSet<>(prefs.getStringSet(previous+"packages",Collections.emptySet()));
                String previousRevision=prefs.getString(previous+"revision", "");
                String boot="stage".equals(method)?Selection.boot():"";
                if("stage".equals(method)&&(boot==null||!boot.matches("[0-9]+")))throw new IllegalStateException("Boot generation unavailable");
                if (!prefs.edit().clear().putStringSet("enabled", enabled).putStringSet("packages", packages)
                        .putString("revision", revision).putString("activationBoot",boot)
                        .putStringSet("previous_enabled",previousEnabled).putStringSet("previous_packages",previousPackages)
                        .putString("previous_revision",previousRevision).commit()) throw new IllegalStateException("Preference write failed");
            } else if (!"status".equals(method)) throw new IllegalArgumentException("Unsupported operation");
            String selected=Selection.prefix(prefs);
            JSONObject status = new JSONObject().put("api", current.getApiVersion())
                    .put("version", BuildInfo.VERSION_CODE)
                    .put("aiSearchScopeReady", current.getScope().contains(AiSearchCompat.PACKAGE))
                    .put("scope", new JSONArray(current.getScope()))
                    .put("enabled", new JSONArray(prefs.getStringSet(selected+"enabled", Collections.emptySet())))
                    .put("revision", prefs.getString(selected+"revision", ""))
                    .put("stagedRevision",prefs.getString("revision", ""));
            result.putBoolean("ok", true); result.putString("json", status.toString());
            result.putString("encoded", Base64.encodeToString(status.toString().getBytes("UTF-8"), Base64.NO_WRAP));
        } catch (Exception e) { result.putBoolean("ok", false); result.putString("message", e.toString()); }
        return result;
    }
    @Override public Cursor query(Uri u,String[] p,String s,String[] a,String o) { return null; }
    @Override public String getType(Uri u) { return null; }
    @Override public Uri insert(Uri u,ContentValues v) { throw new UnsupportedOperationException(); }
    @Override public int delete(Uri u,String s,String[] a) { throw new UnsupportedOperationException(); }
    @Override public int update(Uri u,ContentValues v,String s,String[] a) { throw new UnsupportedOperationException(); }
}
