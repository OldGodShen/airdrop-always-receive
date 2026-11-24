package io.github.oldgodshen.airdropalwaysreceive;

import java.lang.ref.WeakReference;

final class PermanentReceive {
    private static volatile boolean enabled;
    private static volatile WeakReference<Object> bridge = new WeakReference<>(null);
    private static volatile WeakReference<Object> airDropService = new WeakReference<>(null);
    private static volatile boolean receiveAuthorizationHeld;

    private PermanentReceive() {
    }

    static boolean isEnabled() {
        return enabled;
    }

    static void setEnabled(boolean value) {
        enabled = value;
    }

    static void setBridge(Object value) {
        bridge = new WeakReference<Object>(value);
    }

    static Object getBridge() {
        return bridge.get();
    }

    static void setAirDropService(Object value) {
        airDropService = new WeakReference<Object>(value);
    }

    static Object getAirDropService() {
        return airDropService.get();
    }

    static boolean isReceiveAuthorizationHeld() {
        return receiveAuthorizationHeld;
    }

    static void markReceiveAuthorizationHeld(boolean value) {
        receiveAuthorizationHeld = value;
    }
}
