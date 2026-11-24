# Technical Design

## 1. Scope

This module targets one build:

- App: Xiaomi Mi Share
- Version: `5.5.0 (20261029_1004)`
- Package: `com.miui.mishare.connectivity`

It keeps the AirDrop-compatible receive path alive after the receive page is
left. It does not alter the iPhone side.

## 2. Observed protocol implementation

The stock app implements an AirDrop-compatible endpoint:

| Layer | Evidence |
|---|---|
| BLE | `ManufacturerData(76, ...)`, header `5, 18` |
| mDNS | service type `_airdrop._tcp`, TXT `flags`, `_dc`, `model` |
| AWDL | `IDomesticXOsComm/default`, interface `xos0`, channel `149` |
| Control | plist fields, `/Discover`, `/Ask` |
| Data | `POST /Upload`, `application/x-dvzip` |

This is an interoperability implementation, not Apple's private API.

## 3. Target invariants

For discovery and receive to survive page exit, all five invariants must hold.

### I1: Receive authorization

`AirDropShareBridge.hasReceiveAuthorization()` must stay true.

Stock behavior:

- page enter increments a counter
- page exit decrements it

Module behavior:

- acquire exactly once when permanent receive is enabled and the bridge appears
- do not increment on every page resume

### I2: Bridge page-visible state

Stock `startSharopServer()` sets:

```java
mReceiveRequested = true;
mReceivePageVisible = true;
```

The module sets both when enabling and when the bridge is recreated.

### I3: Service session flag

`AirDropService.f17101l0` gates `/Ask`. It must remain true while permanent
receive is active.

The module sets it after:

- `AirDropService.onCreate`
- `startSharopServer`
- service reconnect

### I4: Advertising lifetime

Screen-off calls `pauseAdvertising()`. In permanent mode the hook skips the
pause and ensures advertising is resumed if a race already paused it.

### I5: TLS server lifetime

The `AirDropServer` must stay bound. The module blocks:

- `stopSharopServer`
- `stopDiscovery`
- `ACTION_STOP_SESSION`
- `ACTION_STOP_SHAROP_SERVER`
- `ACTION_STOP`

It does not block transfers already in progress.

## 4. Hook contract

The module uses classic Xposed API classes (`de.robv.android.xposed.*`) so it
runs under LSPosed without depending on a newer hidden API.

### 4.1 Entry

```java
public final class ModuleEntry implements IXposedHookLoadPackage
```

For package `com.miui.mishare.connectivity`:

1. read module preferences
2. locate `MiShareService`
3. install process-appropriate hooks
4. attach to `Application.onCreate` to trigger early initialization

### 4.2 MiShareService hooks

| Target | Action |
|---|---|
| `onCreate` after | remember service, start permanent receive if enabled |
| `onStartCommand` before | drop stop actions in permanent mode |
| `onDestroy` before | clear remembered service |

### 4.3 AirDropShareBridge hooks

| Target | Action |
|---|---|
| constructor after | remember bridge, apply permanent state |
| `startSharopServer` after | force authorization and visibility |
| `stopSharopServer` before | no-op in permanent mode |
| `stopSharopServerInternal` before | no-op in permanent mode |
| `stopDiscover` before | no-op in permanent mode |
| `onScreenStateChanged` before | suppress screen-off pause |
| `destroy` before | clear remembered bridge |

### 4.4 AirDropService hooks

| Target | Action |
|---|---|
| `onCreate` after | remember service, set `f17101l0` |
| `onStartCommand` before | drop stop actions in permanent mode |
| `startSharopServer` after | set session flag |
| `stopSharopServer` before | no-op in permanent mode |
| `stopDiscovery` before | no-op in permanent mode |
| `pauseAdvertising` before | no-op in permanent mode |
| `resumeAdvertising` after | best-effort ensure resumed |

## 5. Process and lifetime model

`MiShareService` already declares:

```xml
android:foregroundServiceType="connectedDevice"
```

The stock `PrePareHelper` can bring it to foreground. The module does not invent
a second service; it reuses the stock service and asks it to start foreground.

The module cannot resurrect a process killed by the system. It relies on:

- stock foreground service behavior
- user keeping Mi Share out of aggressive battery restrictions
- re-applying state whenever the process is recreated

## 6. Failure handling

- Missing class: log `class-missing`, continue.
- Missing field: log `field-missing`, continue.
- Exception in hook: log `hook-error`, swallow unless it is a fatal VM error.
- Repeated failure: keep a bounded diagnostic string for the UI.

All hooks are idempotent. Re-entering `MiShareService.onCreate` or
`AirDropService.onCreate` must not acquire duplicate receive authorizations.

## 7. Security and privacy

- No network calls from the module.
- No file access outside module preferences.
- No credentials or device identifiers are stored.
- The module only alters its target app's in-process state.
- The switch is user-visible and reversible.

## 8. Compatibility strategy

The implementation is intentionally written against one build. The current
prototype records the target version in logs but does not hard-block a version
mismatch. A future Mi Share update that renames obfuscated classes or fields
should degrade safely because each hook reports a missing class/field instead
of silently applying partial state:

- log a clear version mismatch
- do not alter receive behavior
- keep the stock page-bound behavior intact

An adapter layer, plus a hard version gate, can be added later for additional
builds.
