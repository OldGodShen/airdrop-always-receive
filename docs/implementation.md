# Implementation Notes

## Files

| File | Responsibility |
|---|---|
| `ModuleEntry.java` | LSPosed entry and target-package gate |
| `Hooks.java` | Hook families, watchdog, state application |
| `PermanentReceive.java` | Process-local state |
| `Prefs.java` | Module-owned settings |
| `Reflect.java` | Defensive reflection helpers |
| `MainActivity.java` | Switch and diagnostics |

## Preference bridge

The module UI runs in `io.github.oldgodshen.airdropalwaysreceive`; the hooks run
inside `com.miui.mishare.connectivity`.

They are different processes and different `SharedPreferences` sandboxes, so the
hook cannot call the module's own `Context.getSharedPreferences()`.

Instead:

1. the module writes `settings.xml` with `commit()`
2. `Hooks` opens it with `XSharedPreferences`
3. `reload()` runs on a two-second watchdog

This is why the switch takes effect without restarting Mi Share.

## State application

`bringUp()` applies all five invariants:

1. keep `mReceiveAuthorizationCount > 0`
2. set `mReceiveRequested`
3. set `mReceivePageVisible`
4. set `mReceiving`
5. set `AirDropService.l0` and start the Sharop server

`APPLYING` guards against re-entry because `startSharopServer()` is itself
hooked and would otherwise call back into state application.

## Teardown

When the switch turns off, `tearDown()`:

1. calls `releaseReceiveAuthorization()` once
2. calls `stopSharopServer()`
3. clears the held flag

Hook gates check `PermanentReceive.isEnabled()` first, so the stock page
lifecycle resumes when the module is disabled.

## Version coupling

The current hook names are tied to Mi Share 5.5.0:

- `MiShareService` field `f13089q` (display) / `q` (DEX fallback)
- `AirDropService` session field `l0`
- `mReceiveAuthorizationCount`
- `mReceiveRequested`
- `mReceivePageVisible`
- `mReceiving`

If a candidate field is missing, the hook logs it and does not pretend success.
