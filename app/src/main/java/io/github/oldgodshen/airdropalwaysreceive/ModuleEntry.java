package io.github.oldgodshen.airdropalwaysreceive;

import android.content.Context;

import de.robv.android.xposed.IXposedHookLoadPackage;
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.callbacks.XC_LoadPackage;

public final class ModuleEntry implements IXposedHookLoadPackage {
    private static final String TARGET = "com.miui.mishare.connectivity";
    private static final String TARGET_VERSION = "5.5.0";
    private static final String MODULE_PACKAGE = "io.github.oldgodshen.airdropalwaysreceive";

    @Override
    public void handleLoadPackage(XC_LoadPackage.LoadPackageParam loadPackageParam) {
        if (!TARGET.equals(loadPackageParam.packageName)) {
            return;
        }

        try {
            ClassLoader loader = loadPackageParam.classLoader;
            XposedBridge.hookAllMethods(android.app.Application.class, "onCreate", new XC_MethodHook() {
                @Override
                protected void afterHookedMethod(MethodHookParam param) {
                    try {
                        Context context = (Context) param.thisObject;
                        if (context == null) {
                            return;
                        }
                        Logs.info("hooks installed process=" + currentProcessName());
                        Logs.attach(context);
                        Hooks.install(context, loader);
                    } catch (Throwable throwable) {
                        Logs.warn("early hook install failed", throwable);
                    }
                }
            });
            Logs.info("target matched package=" + TARGET + " version=" + TARGET_VERSION);
        } catch (Throwable throwable) {
            Logs.warn("package hook failed", throwable);
        }
    }

    private static String currentProcessName() {
        try {
            Class<?> activityThreadClass = Class.forName("android.app.ActivityThread");
            Object activityThread = activityThreadClass.getMethod("currentProcessName").invoke(null);
            return activityThread == null ? "unknown" : activityThread.toString();
        } catch (Throwable ignored) {
            return "unknown";
        }
    }
}
