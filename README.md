# AirDrop Always Receive for Xiaomi Mi Share

An LSPosed module that keeps Xiaomi Mi Share's AirDrop-compatible receiver alive
without requiring the user to stay on the "Transfer with Apple devices" page.

Target build: **Xiaomi Mi Share 5.5.0 (20261029_1004)**

Target package: `com.miui.mishare.connectivity`

## What it does

- Keeps the Mi Share AirDrop receiver session active in the background.
- Prevents the receive page's `onPause`/`onStop` from tearing down discovery.
- Keeps BLE advertising, mDNS `_airdrop._tcp`, the TLS `/Ask` endpoint, and the
  AWDL receive session alive while the feature is enabled.
- Suppresses screen-off pauses of AirDrop advertising.

## What it does not do

- It does not change an iPhone's AirDrop privacy setting.
- It does not make an iPhone permanently "Everyone".
- It does not make Xiaomi appear under iPhone's "Contacts Only" mode.
- It does not replace Xiaomi's private `xos0`/`IDomesticXOsComm` system service.

On the Apple side, the user must still select **Everyone (10 minutes)** in
Control Center, or use a separate Xiaomi interoperability app path.

## Requirements

- Rooted Xiaomi/Redmi device with LSPosed installed.
- The original Mi Share system app, not a repackaged third-party build.
- A ROM that still provides the `xos0` AWDL-equivalent interface.
- Bluetooth and Wi-Fi enabled.
- Acceptance of temporary 5 GHz Wi-Fi disruption and increased battery use.

## Before you install

Read [docs/technical-design.md](docs/technical-design.md) and
[docs/testing.md](docs/testing.md). The module is intentionally version-locked
to the class and field names in Mi Share 5.5.0. Other versions may need an
adapter update.

## Build

```powershell
.\gradlew.bat :app:assembleDebug
```

The output APK is `app/build/outputs/apk/debug/app-debug.apk`.

If Gradle or the Android SDK is not installed, see
[docs/build.md](docs/build.md).

## Install

1. Install the APK.
2. Enable **AirDrop Always Receive** in LSPosed.
3. Scope it to `com.miui.mishare.connectivity`.
4. Force-stop Mi Share, then reopen Mi Share once so the hooks load.
5. Enable the module's "Keep receiver active" switch.
6. Keep the iPhone AirDrop privacy setting at **Everyone (10 minutes)** for
   discovery testing.

See [docs/install.md](docs/install.md) for the exact steps and rollback.

## Verification

```powershell
adb logcat -s AirDropAlwaysReceive AirDropService AirDropShareBridge `
  AirDropBleAdvertiser NsdAdvertiser AwdlManager MiShareService
```

Expected signals:

- `permanent receive enabled`
- `receiver authorization kept`
- `BLE advertising started`
- `Registering NSD service`
- `XOS started on ch149`

Then test discovery from an iPhone's AirDrop share sheet with Mi Share in the
background. Full test cases are in [docs/testing.md](docs/testing.md).

## Safety and rollback

- Disable the switch, disable the LSPosed scope, then reboot or force-stop
  Mi Share.
- If the device becomes unstable, disable the module in LSPosed's safe mode
  (volume-key gesture on boot) and reboot.
- Do not enable this on a build where `xos0` is shared with another critical
  continuity feature without testing that feature first.

## Status

Code and documentation are provided as a reproducible prototype. The design is
based on static analysis of the supplied 5.5.0 APK. Runtime behavior still
needs validation on the target device and ROM.
