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

The 0.1.0 debug APK built during development has SHA-256:

```text
83B4D828A56F0DC125F1BE7A770929307B4737FE8C9E3121678186BBEBE40694
```

This hash changes after any rebuild, so treat it as a build record rather than
an authenticity guarantee.

## Verify hooks loaded

```powershell
adb logcat -c
adb logcat -s AirDropAlwaysReceive
```

Expected lines include:

```text
target matched package=com.miui.mishare.connectivity
hooks installed process=com.miui.mishare.connectivity
hook families registered
state reason=install enabled=true
receiver authorization kept
```

If you only see `target matched` but not `hooks installed`, the target
application class was not hooked. Scope the module to
`com.miui.mishare.connectivity`, then force-stop and reopen Mi Share.

If you see `class missing` or `field missing`, the installed Mi Share build is
not the expected 5.5.0 layout. The module deliberately does not continue with
partially applied state.

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

## Diagnose by symptom

| Symptom | First check |
|---|---|
| Switch on, no `receiver authorization kept` | module not scoped or hooks not loaded |
| `class missing` for `AirDropService` | Mi Share version mismatch |
| `field missing` for `l0` | obfuscated field renamed |
| authorization kept but no discovery | `xos0` unavailable or Bluetooth/Wi-Fi off |
| discovery dies on screen off | screen-state hook not applied |
| discovery dies after leaving page | stop-action hook not applied |

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
