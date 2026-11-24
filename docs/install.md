# Install and Rollback

## Prerequisites

- Xiaomi/Redmi device with root
- LSPosed installed and working
- Xiaomi Mi Share 5.5.0 (20261029_1004)
- Bluetooth and Wi-Fi enabled

## Install

1. Build `app-debug.apk`.
2. Install:

   ```powershell
   adb install -r app\build\outputs\apk\debug\app-debug.apk
   ```

3. Open **AirDrop Always Receive** once.
4. In LSPosed:
   - enable the module
   - set scope to `com.miui.mishare.connectivity`
5. Force-stop Mi Share:

   ```powershell
   adb shell am force-stop com.miui.mishare.connectivity
   ```

6. Open Mi Share once.
7. Enable **Keep receiver active** in the module.

## Verify hooks loaded

```powershell
adb logcat -c
adb logcat -s AirDropAlwaysReceive
```

Expected lines include:

```text
hooks installed
permanent receive enabled
receiver authorization kept
```

## Verify discovery

1. Leave Mi Share's receive page.
2. On iPhone, open the share sheet.
3. Set AirDrop to **Everyone (10 minutes)**.
4. Confirm the Xiaomi device appears.

If it does not appear, collect:

```powershell
adb logcat -d -s AirDropAlwaysReceive AirDropService `
  AirDropShareBridge AirDropBleAdvertiser NsdAdvertiser AwdlManager MiShareService
```

## Disable

1. Turn off **Keep receiver active**.
2. Disable the LSPosed scope.
3. Force-stop Mi Share:

   ```powershell
   adb shell am force-stop com.miui.mishare.connectivity
   ```

4. Reopen Mi Share if needed.

## Emergency rollback

If the device bootloops or Mi Share crashes repeatedly:

1. Boot into LSPosed safe mode using its volume-key gesture.
2. Disable the module.
3. Reboot.
4. Uninstall the module APK.

The module does not modify Mi Share's APK or system partition, so uninstalling
the module restores stock behavior.
