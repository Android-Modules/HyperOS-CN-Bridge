package com.dalong.cnbridge;

import android.accounts.Account;
import android.accounts.AccountManager;
import android.content.ContentResolver;
import android.content.Context;
import android.util.Log;

/** Repair the Global ROM's disabled adapter only after the user enables Xiaomi sync. */
final class GallerySync {
    private static final String AUTHORITY = "com.miui.gallery.cloud.provider";
    static void ensureRegistration(Context context) {
        if (context == null) return;
        try {
            for (Account account : AccountManager.get(context).getAccountsByType("com.xiaomi")) {
                if (!ContentResolver.getSyncAutomatically(account, AUTHORITY)) continue;
                if (ContentResolver.getIsSyncable(account, AUTHORITY) == 1) continue;
                ContentResolver.setIsSyncable(account, AUTHORITY, 1);
                if (ContentResolver.getIsSyncable(account, AUTHORITY) != 1)
                    throw new IllegalStateException("Gallery adapter registration did not apply");
                Log.i("CnBridge", "GALLERY sync adapter restored; user sync choice preserved");
            }
        } catch (Exception error) {
            Log.e("CnBridge", "Gallery sync registration failed", error);
        }
    }
}
