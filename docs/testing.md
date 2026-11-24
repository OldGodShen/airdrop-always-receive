# Testing

## Test matrix

| ID | Scenario | Expected |
|---|---|---|
| T1 | Module disabled, receive page open | stock behavior only |
| T2 | Module enabled, receive page open | discovery works |
| T3 | Module enabled, leave receive page | discovery remains |
| T4 | Module enabled, screen off/on | discovery remains |
| T5 | iPhone sends file after T3 | `/Ask` appears, accepted transfer completes |
| T6 | Module switch off | discovery stops after debounce |
| T7 | Force-stop Mi Share, reopen | hooks reapply, discovery returns |
| T8 | Bluetooth off | clear failure, no crash |
| T9 | Wi-Fi off | clear failure, no crash |
| T10 | Other Mi Share transfer active | AirDrop path yields or defers, no crash |

## Logs

```powershell
adb logcat -c
adb logcat -s AirDropAlwaysReceive AirDropService AirDropShareBridge `
  AirDropBleAdvertiser NsdAdvertiser AwdlManager MiShareService
```

## Key signals

### Hooks loaded

```text
AirDropAlwaysReceive: hooks installed
AirDropAlwaysReceive: permanent receive enabled
```

### Advertising active

```text
AirDropBleAdvertiser: BLE advertising started
NsdAdvertiser: Registering NSD service
AwdlManager: XOS started on ch149
```

### Session kept

```text
AirDropShareBridge: receiver authorization kept
AirDropService: receive page session is inactive
```

The second line should **not** appear for a request while permanent receive is
enabled.

## Negative checks

- iPhone AirDrop set to **Receiving Off**: Xiaomi must not be discoverable.
- iPhone AirDrop set to **Contacts Only**: Xiaomi must not be assumed
  discoverable; this is an Apple limitation, not a module bug.
- Module disabled: leaving the page must stop discovery.

## Runtime evidence to capture

- module version
- Mi Share version
- Android version and ROM build
- whether `xos0` exists:

  ```powershell
  adb shell ip link show xos0
  ```

- whether `IDomesticXOsComm` is present:

  ```powershell
  adb shell service list | Select-String domestic
  ```

- full filtered logcat for T3 and T4
