# Reverse Engineering Notes

Input: `小米互传_5.5.0_20261029_1004.apk`

Tool: jadx 1.5.6

Output: `jadx_out/` (not committed)

## Entry points

- `com.miui.mishare.activity.MiShareAirDropSettingActivity`
- `com.miui.mishare.activity.AirDropShareDialogActivity`
- `com.miui.mishare.connectivity.MiShareService`
- `com.miui.mishare.connectivity.AirDropShareBridge`
- `com.xiaomi.sharop.service.AirDropService`
- `com.xiaomi.sharop.client.AirDropClient`
- `com.xiaomi.sharop.server.AirDropServer`
- `p141a.C1264b1` (mDNS advertiser)
- `p141a.C1227P0` (BLE advertiser)
- `com.xiaomi.sharop.awdl.AwdlServiceClient`

## Lifecycle evidence

### Page starts/stops receive

`MiShareAirDropSettingActivity`:

- `onStart`/`onResume` -> `AIRDROP_FORCE_START_RECEIVE`
- `onPause`/`onStop` -> `AIRDROP_FORCE_STOP_RECEIVE`
- `onPause`/`onStop` -> `AirDropService.ACTION_STOP_SESSION`

### Screen state

`AirDropShareBridge.onScreenStateChanged(false)`:

- `pauseAdvertising()`
- only resumes when page still visible

`AirDropService.pauseAdvertising()`:

- stops BLE scan
- unregisters NSD
- may release `xos0`

### Ask gate

`AirDropService.m18100a(...)`:

- if `f17101l0 == false`, respond `USER_DECLINE`

This is the decisive runtime gate.

## Protocol evidence

- `PlistHelper.buildDiscoverRequest()`: `DeviceSupportFlags = 46075`
- `PlistHelper.buildAskRequest()`: `SenderComputerName`, `SenderModelName`,
  `BundleID = com.apple.finder`
- `AirDropClient`: `/Discover`, `/Ask`, `/Upload`
- `AirDropServer`: TLS `SSLServerSocket`
- `AirDropServer` accepts only link-local `xos0`

## Observed constants

| Constant | Value |
|---|---|
| BLE Apple company ID | `76` / `0x004C` |
| BLE header | `5`, `18` |
| mDNS service | `_airdrop._tcp` |
| AWDL interface | `xos0` |
| receive channel | `149` |
| DVZIP content type | `application/x-dvzip` |

## Obfuscation caveats

Class and field names in this build are obfuscated. Hooks must use the exact
5.5.0 names listed in `technical-design.md` and fail closed on mismatch.
