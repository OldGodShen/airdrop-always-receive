# Build

## Requirements

- JDK 17 or newer
- Android SDK with:
  - platform 35
  - build-tools 35.0.0
  - platform-tools
- Gradle 8.11.1 or the wrapper

## Configure SDK

Create `local.properties`:

```properties
sdk.dir=C\:\\path\\to\\Android\\Sdk
```

This file is ignored by git.

## Build

```powershell
.\gradlew.bat :app:assembleDebug
```

If the wrapper JAR is unavailable, fetch the toolchain into a cache outside the
repository:

```powershell
powershell -ExecutionPolicy Bypass -File .\scripts\fetch-build-tools.ps1
```

Then build with the cached Gradle:

```powershell
$env:JAVA_HOME = "C:\Program Files\Zulu\zulu-17"
$env:ANDROID_SDK_ROOT = "$env:LOCALAPPDATA\AirDropAlwaysReceiveBuild\android-sdk"
$env:ANDROID_HOME = $env:ANDROID_SDK_ROOT
& "$env:LOCALAPPDATA\AirDropAlwaysReceiveBuild\gradle-8.11.1\bin\gradle.bat" :app:assembleDebug
```

## Output

```text
app\build\outputs\apk\debug\app-debug.apk
```

## Notes

- The module compiles against the classic Xposed API 82.
- The `compileOnly` dependency is intentional: LSPosed provides the API at
  runtime, and the Xposed API must not be packaged into the module APK.
- If Gradle cannot find the SDK, verify `local.properties` and `ANDROID_HOME`.
