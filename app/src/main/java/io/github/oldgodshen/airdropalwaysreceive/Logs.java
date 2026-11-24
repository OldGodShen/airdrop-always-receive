package io.github.oldgodshen.airdropalwaysreceive;

import android.util.Log;

import java.lang.ref.WeakReference;
import java.io.File;
import java.io.FileOutputStream;
import java.nio.charset.StandardCharsets;

final class Logs {
    static final String TAG = "AirDropAlwaysReceive";
    private static final int MAX_DIAGNOSTICS = 8000;
    private static final StringBuilder DIAGNOSTICS = new StringBuilder();
    private static volatile WeakReference<android.content.Context> appContext =
            new WeakReference<android.content.Context>(null);
    private static final String TARGET_DIAGNOSTICS_NAME = "airdrop-always-receive.log";

    private Logs() {
    }

    static void info(String message) {
        Log.i(TAG, message);
        append(message);
    }

    static void attach(android.content.Context context) {
        if (context != null) {
            android.content.Context app = context.getApplicationContext();
            appContext = new WeakReference<android.content.Context>(app);
            if (!"io.github.oldgodshen.airdropalwaysreceive".equals(app.getPackageName())
                    && !app.getPackageName().startsWith("io.github.oldgodshen.airdropalwaysreceive.")) {
                writeTargetDiagnostics(app);
            }
        }
    }

    static void warn(String message) {
        Log.w(TAG, message);
        append(message);
    }

    static void warn(String message, Throwable throwable) {
        Log.w(TAG, message, throwable);
        append(message + ": " + throwable.getClass().getSimpleName());
    }

    static synchronized String diagnostics() {
        return DIAGNOSTICS.toString();
    }

    private static synchronized void append(String message) {
        if (DIAGNOSTICS.length() > MAX_DIAGNOSTICS) {
            DIAGNOSTICS.delete(0, DIAGNOSTICS.length() - MAX_DIAGNOSTICS);
        }
        DIAGNOSTICS.append(message).append('\n');
        android.content.Context context = appContext.get();
        if (context != null) {
            try {
                Prefs.setDiagnostics(context, DIAGNOSTICS.toString());
            } catch (Throwable ignored) {
                // Diagnostics must never affect the hooked process.
            }
        }
    }

    private static synchronized void writeTargetDiagnostics(android.content.Context context) {
        try {
            File directory = new File(context.getFilesDir(), "airdrop-always-receive");
            if (!directory.exists() && !directory.mkdirs()) {
                return;
            }
            File file = new File(directory, TARGET_DIAGNOSTICS_NAME);
            try (FileOutputStream output = new FileOutputStream(file, false)) {
                output.write(DIAGNOSTICS.toString().getBytes(StandardCharsets.UTF_8));
            }
        } catch (Throwable ignored) {
            // Best-effort diagnostics only.
        }
    }
}
