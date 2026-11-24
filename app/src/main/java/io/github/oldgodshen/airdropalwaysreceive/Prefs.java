package io.github.oldgodshen.airdropalwaysreceive;

import android.content.Context;
import android.content.SharedPreferences;

final class Prefs {
    static final String NAME = "settings";
    private static final String KEY_ENABLED = "enabled";
    private static final String KEY_DIAGNOSTICS = "diagnostics";

    private Prefs() {
    }

    static SharedPreferences get(Context context) {
        Context appContext = context.getApplicationContext();
        if (appContext != null) {
            context = appContext;
        }
        return context.getSharedPreferences(NAME, Context.MODE_PRIVATE);
    }

    static boolean isEnabled(Context context) {
        return get(context).getBoolean(KEY_ENABLED, false);
    }

    static void setEnabled(Context context, boolean enabled) {
        get(context).edit().putBoolean(KEY_ENABLED, enabled).commit();
    }

    static String diagnostics(Context context) {
        return get(context).getString(KEY_DIAGNOSTICS, "");
    }

    static void setDiagnostics(Context context, String diagnostics) {
        get(context).edit().putString(KEY_DIAGNOSTICS, diagnostics).commit();
    }
}
