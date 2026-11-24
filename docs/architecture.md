# Architecture

## System layers

```text
iPhone AirDrop UI
        |
        | BLE advertisement + Bonjour _airdrop._tcp + TLS
        v
Xiaomi Mi Share (com.miui.mishare.connectivity)
  |
  +-- MiShareService
  |     +-- AirDropShareBridge
  |           +-- receive authorization state
  |           +-- page-visible state
  |           +-- screen-state pause/resume
  |
  +-- AirDropService (com.xiaomi.sharop.service)
        +-- AwdlManager ------> xos0 / IDomesticXOsComm
        +-- NsdAdvertiser ---> _airdrop._tcp
        +-- BLE advertiser --> Apple manufacturer data 0x004C
        +-- AirDropServer ---> TLS /Discover /Ask /Upload
```

## Why the stock implementation is page-bound

The receive page is the privacy and lifecycle owner:

```text
MiShareAirDropSettingActivity.onStart/onResume
  -> AIRDROP_FORCE_START_RECEIVE
  -> MiShareService
  -> AirDropShareBridge.acquireReceiveAuthorization()
  -> AirDropShareBridge.startSharopServer()
  -> AirDropService.startSharopServer()

MiShareAirDropSettingActivity.onPause/onStop
  -> AIRDROP_FORCE_STOP_RECEIVE
  -> AirDropShareBridge.releaseReceiveAuthorization()
  -> AirDropShareBridge.stopSharopServer()
  -> AirDropService.stopSharopServer()
  -> ACTION_STOP_SESSION
```

Screen-off adds a second teardown path:

```text
SCREEN_OFF
  -> AirDropShareBridge.onScreenStateChanged(false)
  -> AirDropService.pauseAdvertising()
  -> BLE scan stop + mDNS unregister + possible xos0 release
```

Finally, `/Ask` rejects requests when the page session flag is false:

```java
if (!this.f17101l0) {
    log("handleAsk: receive page session is inactive");
    respond(false, USER_DECLINE, ...);
    return;
}
```

The module therefore has to preserve all of the following together:

1. receive authorization count
2. bridge `mReceiveRequested` / `mReceivePageVisible`
3. service page-session flag `f17101l0`
4. BLE + mDNS advertising across screen-off
5. TLS server lifetime

Keeping only one of them is not sufficient.

## Module components

```text
app/
  MainActivity          enable switch and diagnostics
  ModuleEntry           LSPosed entry point
  HookInstaller         version check and hook registration
  PermanentReceive      process-wide state
  Hooks                 runtime hooks against Mi Share internals
```

### PermanentReceive

Holds:

- enabled switch
- currently known `AirDropShareBridge`
- currently known `AirDropService`
- last hook error for UI diagnostics

The switch is stored in module-owned preferences. It is deliberately not
stored in Mi Share's settings database, so disabling the module cannot leave
the stock app in an inconsistent state.

### HookInstaller

Runs in every process of `com.miui.mishare.connectivity`. It only installs the
hooks relevant to the process:

- `:ui` process: page lifecycle and screen-state hooks
- main process: `MiShareService`, `AirDropShareBridge`, `AirDropService`

Every hook is guarded with `try/catch`; a missing class or field must produce a
diagnostic, not a crash loop.

## State machine

```text
module disabled
  -> stock page lifecycle wins
  -> no background discovery

module enabled, bridge created
  -> keep receive authorization
  -> page-visible = true
  -> start Sharop server

module enabled, screen off
  -> suppress pauseAdvertising
  -> keep BLE + mDNS + TLS + xos0

module enabled, page leaves
  -> suppress stop actions
  -> keep session

module disabled at runtime
  -> release authorization once
  -> stop server through the stock path
  -> return to page-bound behavior
```

## Non-goals

- Apple Contacts Only matching.
- Permanent Apple "Everyone".
- Repackaging Mi Share as a normal third-party APK.
- Bypassing Xiaomi's permission model or SELinux.
