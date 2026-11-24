package io.github.oldgodshen.airdropalwaysreceive;

import android.content.Context;
import android.content.Intent;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;

import java.lang.reflect.Field;
import java.util.concurrent.atomic.AtomicBoolean;

import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;
import de.robv.android.xposed.XSharedPreferences;

final class Hooks {
    private static final AtomicBoolean INSTALLED = new AtomicBoolean(false);
    private static final AtomicBoolean APPLYING = new AtomicBoolean(false);
    private static final AtomicBoolean DISABLING = new AtomicBoolean(false);
    private static final long WATCH_INTERVAL_MS = 2000L;
    private static final MyHandler HANDLER = new MyHandler();

    private static volatile Context appContext;
    private static volatile XSharedPreferences modulePreferences;
    private static volatile Object miShareService;
    private static volatile long lastStateLogAt;
    private static volatile long lastBringUpAt;

    private static final String MI_SHARE_SERVICE =
            "com.miui.mishare.connectivity.MiShareService";
    private static final String BRIDGE =
            "com.miui.mishare.connectivity.AirDropShareBridge";
    private static final String AIR_DROP_SERVICE =
            "com.xiaomi.sharop.service.AirDropService";

    private static final String[] STOP_ACTIONS = {
            "com.miui.mishare.action.AIRDROP_FORCE_STOP_RECEIVE",
            "com.miui.mishare.action.AIRDROP_FORCE_STOP_DISCOVER",
            "com.xiaomi.sharop.STOP_SESSION",
            "com.xiaomi.sharop.STOP_SHAROP_SERVER",
            "com.xiaomi.sharop.STOP",
            "com.xiaomi.sharop.STOP_DISCOVERY"
    };

    private Hooks() {
    }

    static void install(Context context, ClassLoader loader) {
        if (!INSTALLED.compareAndSet(false, true)) {
            return;
        }
        appContext = context.getApplicationContext();
        modulePreferences = new XSharedPreferences(
                "io.github.oldgodshen.airdropalwaysreceive", Prefs.NAME);
        modulePreferences.makeWorldReadable();
        refreshState("install");

        try {
            installMiShareServiceHooks(loader);
            installBridgeHooks(loader);
            installAirDropServiceHooks(loader);
            Logs.info("hook families registered");
            HANDLER.postDelayed(new Runnable() {
                @Override
                public void run() {
                    tick();
                    HANDLER.postDelayed(this, WATCH_INTERVAL_MS);
                }
            }, WATCH_INTERVAL_MS);
        } catch (Throwable throwable) {
            Logs.warn("hook registration failed", throwable);
        }
    }

    private static final class MyHandler extends Handler {
        MyHandler() {
            super(Looper.getMainLooper());
        }
    }

    private static void tick() {
        try {
            boolean wasEnabled = PermanentReceive.isEnabled();
            refreshState("watchdog");
            if (!wasEnabled && PermanentReceive.isEnabled()) {
                bringUp("watchdog-enable");
            }
            if (wasEnabled && !PermanentReceive.isEnabled()) {
                tearDown("watchdog-disable");
            }
            if (PermanentReceive.isEnabled() && PermanentReceive.getBridge() == null) {
                bringUp("watchdog-missing-bridge");
            }
        } catch (Throwable throwable) {
            Logs.warn("watchdog failed", throwable);
        }
    }

    private static void installMiShareServiceHooks(ClassLoader loader) {
        Class<?> serviceClass = findClassOrNull(MI_SHARE_SERVICE, loader);
        if (serviceClass == null) {
            Logs.warn("MiShareService missing");
            return;
        }

        XposedBridge.hookAllMethods(serviceClass, "onCreate", new XC_MethodHook() {
            @Override
            protected void afterHookedMethod(MethodHookParam param) {
                try {
                    Object service = param.thisObject;
                    miShareService = service;
                    Object bridge = readField(service, "f13089q", "q", "mAirDropShareBridge");
                    if (bridge != null) {
                        PermanentReceive.setBridge(bridge);
                    }
                    refreshState("MiShareService.onCreate");
                    if (PermanentReceive.isEnabled()) {
                        bringUp("MiShareService.onCreate");
                    }
                } catch (Throwable throwable) {
                    Logs.warn("MiShareService.onCreate hook failed", throwable);
                }
            }
        });

        XposedBridge.hookAllMethods(serviceClass, "onStartCommand", new XC_MethodHook() {
            @Override
            protected void beforeHookedMethod(MethodHookParam param) {
                refreshState("MiShareService.onStartCommand");
                String action = actionOf(param.args);
                if (!PermanentReceive.isEnabled() || !isStopAction(action)) {
                    return;
                }
                Logs.info("blocked MiShareService stop action=" + action);
                param.setResult(1);
            }
        });

        XposedBridge.hookAllMethods(serviceClass, "onDestroy", new XC_MethodHook() {
            @Override
            protected void beforeHookedMethod(MethodHookParam param) {
                miShareService = null;
                PermanentReceive.setBridge(null);
                PermanentReceive.setAirDropService(null);
            }
        });
    }

    private static void installBridgeHooks(ClassLoader loader) {
        Class<?> bridgeClass = findClassOrNull(BRIDGE, loader);
        if (bridgeClass == null) {
            Logs.warn("AirDropShareBridge missing");
            return;
        }

        XposedBridge.hookAllConstructors(bridgeClass, new XC_MethodHook() {
            @Override
            protected void afterHookedMethod(MethodHookParam param) {
                try {
                    Object bridge = param.thisObject;
                    PermanentReceive.setBridge(bridge);
                    refreshState("bridge constructor");
                    if (PermanentReceive.isEnabled()) {
                        applyBridgeState(bridge, "bridge constructor");
                    }
                } catch (Throwable throwable) {
                    Logs.warn("bridge constructor hook failed", throwable);
                }
            }
        });

        hookVoid(bridgeClass, "startSharopServer", new Runnable() {
            @Override
            public void run() {
                refreshState("bridge startSharopServer");
                applyPermanentState("bridge startSharopServer");
            }
        });

        blockVoid(bridgeClass, "stopSharopServer", "bridge stopSharopServer");
        blockVoid(bridgeClass, "stopSharopServerInternal", "bridge stopSharopServerInternal");
        blockVoid(bridgeClass, "stopDiscover", "bridge stopDiscover");

        XposedBridge.hookAllMethods(bridgeClass, "onScreenStateChanged", new XC_MethodHook() {
            @Override
            protected void beforeHookedMethod(MethodHookParam param) {
                refreshState("bridge onScreenStateChanged");
                if (!PermanentReceive.isEnabled() || param.args.length == 0) {
                    return;
                }
                if (Boolean.FALSE.equals(param.args[0])) {
                    Logs.info("screen-off advertising pause suppressed");
                    param.setResult(null);
                }
            }
        });

        XposedBridge.hookAllMethods(bridgeClass, "destroy", new XC_MethodHook() {
            @Override
            protected void beforeHookedMethod(MethodHookParam param) {
                PermanentReceive.setBridge(null);
            }
        });
    }

    private static void installAirDropServiceHooks(ClassLoader loader) {
        Class<?> serviceClass = findClassOrNull(AIR_DROP_SERVICE, loader);
        if (serviceClass == null) {
            Logs.warn("AirDropService missing");
            return;
        }

        XposedBridge.hookAllMethods(serviceClass, "onCreate", new XC_MethodHook() {
            @Override
            protected void afterHookedMethod(MethodHookParam param) {
                try {
                    Object service = param.thisObject;
                    PermanentReceive.setAirDropService(service);
                    refreshState("AirDropService.onCreate");
                    if (PermanentReceive.isEnabled()) {
                        applyAirDropServiceState(service, "AirDropService.onCreate");
                    }
                } catch (Throwable throwable) {
                    Logs.warn("AirDropService.onCreate hook failed", throwable);
                }
            }
        });

        XposedBridge.hookAllMethods(serviceClass, "onStartCommand", new XC_MethodHook() {
            @Override
            protected void beforeHookedMethod(MethodHookParam param) {
                String action = actionOf(param.args);
                refreshState("AirDropService.onStartCommand");
                if (!PermanentReceive.isEnabled() || !isStopAction(action)) {
                    return;
                }
                Logs.info("blocked AirDropService stop action=" + action);
                param.setResult(2);
            }
        });

        hookVoid(serviceClass, "startSharopServer", new Runnable() {
            @Override
            public void run() {
                refreshState("AirDropService.startSharopServer");
                applyPermanentState("AirDropService.startSharopServer");
            }
        });

        blockVoid(serviceClass, "stopSharopServer", "AirDropService.stopSharopServer");
        blockVoid(serviceClass, "stopDiscovery", "AirDropService.stopDiscovery");
        blockVoid(serviceClass, "pauseAdvertising", "AirDropService.pauseAdvertising");

        XposedBridge.hookAllMethods(serviceClass, "onDestroy", new XC_MethodHook() {
            @Override
            protected void beforeHookedMethod(MethodHookParam param) {
                PermanentReceive.setAirDropService(null);
            }
        });
    }

    private static void applyPermanentState(String reason) {
        refreshState(reason);
        if (!PermanentReceive.isEnabled() || !APPLYING.compareAndSet(false, true)) {
            return;
        }
        try {
            Object bridge = PermanentReceive.getBridge();
            if (bridge != null) {
                applyBridgeState(bridge, reason);
            }
            Object service = PermanentReceive.getAirDropService();
            if (service != null) {
                applyAirDropServiceState(service, reason);
            }
        } finally {
            APPLYING.set(false);
        }
    }

    private static void applyBridgeState(Object bridge, String reason) {
        try {
            Field authorization = Reflect.findField(bridge.getClass(),
                    "mReceiveAuthorizationCount", "q", "a");
            if (authorization != null) {
                if (authorization.getInt(bridge) <= 0) {
                    authorization.setInt(bridge, 1);
                    Logs.info("receiver authorization kept (" + reason + ")");
                }
            } else {
                Logs.warn("receiver authorization field missing (" + reason + ")");
            }

            setBoolean(bridge, true, "mReceiveRequested", "r");
            setBoolean(bridge, true, "mReceivePageVisible", "s");
            setBoolean(bridge, true, "mReceiving", "t");
            PermanentReceive.markReceiveAuthorizationHeld(true);
        } catch (Throwable throwable) {
            Logs.warn("apply bridge state failed (" + reason + ")", throwable);
        }
    }

    private static void applyAirDropServiceState(Object service, String reason) {
        Field session = Reflect.findField(service.getClass(),
                "l0", "f17101l0", "mPageSessionActive");
        if (session == null) {
            Logs.warn("AirDropService session field missing (" + reason + ")");
            return;
        }
        try {
            if (!session.getBoolean(service)) {
                session.setBoolean(service, true);
                Logs.info("AirDrop session activated (" + reason + ")");
            }
        } catch (Throwable throwable) {
            Logs.warn("apply AirDropService state failed (" + reason + ")", throwable);
        }
    }

    private static void refreshState(String reason) {
        XSharedPreferences preferences = modulePreferences;
        if (preferences == null) {
            return;
        }
        try {
            preferences.reload();
            boolean enabled = preferences.getBoolean("enabled", false);
            boolean changed = enabled != PermanentReceive.isEnabled();
            PermanentReceive.setEnabled(enabled);
            long now = SystemClock.elapsedRealtime();
            if (changed || now - lastStateLogAt > 60_000L) {
                lastStateLogAt = now;
                Logs.info("state reason=" + reason + " enabled=" + enabled);
            }
        } catch (Throwable throwable) {
            Logs.warn("preference reload failed", throwable);
        }
    }

    private static void bringUp(String reason) {
        if (!PermanentReceive.isEnabled()) {
            return;
        }
        if (!APPLYING.compareAndSet(false, true)) {
            return;
        }
        try {
            Logs.info("bringUp reason=" + reason);
            long now = SystemClock.elapsedRealtime();
            if (now - lastBringUpAt < 3000L) {
                return;
            }
            lastBringUpAt = now;
            Context context = appContext;
            if (context != null) {
                if (miShareService instanceof android.app.Service) {
                    try {
                        ((android.app.Service) miShareService).startForegroundService(
                                new Intent(context, miShareService.getClass())
                                        .setAction("com.miui.mishare.action.START_MISHARE_FOREGROUND"));
                    } catch (Throwable throwable) {
                        Logs.warn("self foreground request failed", throwable);
                    }
                }
            }
            Object bridge = PermanentReceive.getBridge();
            if (bridge != null) {
                applyBridgeState(bridge, reason);
                invokeBridge(bridge, "startSharopServer");
            }
            Object service = PermanentReceive.getAirDropService();
            if (service != null) {
                applyAirDropServiceState(service, reason);
                invokeBridge(service, "startSharopServer");
            }
        } finally {
            APPLYING.set(false);
        }
    }

    private static void tearDown(String reason) {
        if (!APPLYING.compareAndSet(false, true)) {
            return;
        }
        try {
            Logs.info("tearDown reason=" + reason);
            DISABLING.set(true);
            Object bridge = PermanentReceive.getBridge();
            if (bridge != null) {
                invokeBridge(bridge, "releaseReceiveAuthorization");
                invokeBridge(bridge, "stopSharopServer");
            }
            Object service = PermanentReceive.getAirDropService();
            if (service != null) {
                invokeBridge(service, "stopSharopServer");
            }
            PermanentReceive.markReceiveAuthorizationHeld(false);
        } finally {
            DISABLING.set(false);
            APPLYING.set(false);
        }
    }

    private static void invokeBridge(Object target, String methodName) {
        try {
            Object result = XposedHelpers.callMethod(target, methodName);
            Logs.info("invoked " + methodName);
            if (result instanceof Boolean && !((Boolean) result)) {
                Logs.warn("invocation returned false: " + methodName);
            }
        } catch (Throwable throwable) {
            Logs.warn("invoke failed: " + methodName, throwable);
        }
    }

    private static void setBoolean(Object target, boolean value, String... names) {
        Field field = Reflect.findField(target.getClass(), names);
        if (field == null) {
            Logs.warn("boolean field missing: " + String.join("/", names));
            return;
        }
        try {
            if (field.getBoolean(target) != value) {
                field.setBoolean(target, value);
            }
        } catch (Throwable throwable) {
            Logs.warn("boolean field write failed: " + field.getName(), throwable);
        }
    }

    private static Object readField(Object target, String... names) {
        Field field = Reflect.findField(target.getClass(), names);
        if (field == null) {
            Logs.warn("object field missing: " + String.join("/", names));
            return null;
        }
        return Reflect.getField(target, field);
    }

    private static void hookVoid(Class<?> owner, String methodName, final Runnable action) {
        XposedBridge.hookAllMethods(owner, methodName, new XC_MethodHook() {
            @Override
            protected void afterHookedMethod(MethodHookParam param) {
                if (!PermanentReceive.isEnabled()) {
                    return;
                }
                action.run();
            }
        });
    }

    private static void blockVoid(Class<?> owner, String methodName, final String label) {
        XposedBridge.hookAllMethods(owner, methodName, new XC_MethodHook() {
            @Override
            protected void beforeHookedMethod(MethodHookParam param) {
                if (!PermanentReceive.isEnabled() || DISABLING.get()) {
                    return;
                }
                Logs.info("blocked " + label);
                param.setResult(null);
            }
        });
    }

    private static boolean isStopAction(String action) {
        if (action == null) {
            return false;
        }
        for (String stopAction : STOP_ACTIONS) {
            if (stopAction.equals(action)) {
                return true;
            }
        }
        return false;
    }

    private static String actionOf(Object[] args) {
        if (args == null || args.length == 0 || !(args[0] instanceof Intent)) {
            return null;
        }
        return ((Intent) args[0]).getAction();
    }

    private static Class<?> findClassOrNull(String name, ClassLoader loader) {
        try {
            return XposedHelpers.findClass(name, loader);
        } catch (Throwable throwable) {
            Logs.warn("class missing: " + name, throwable);
            return null;
        }
    }
}
