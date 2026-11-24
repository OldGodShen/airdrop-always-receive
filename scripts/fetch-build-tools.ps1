param(
    [string]$CacheRoot = "$env:LOCALAPPDATA\AirDropAlwaysReceiveBuild"
)

$ErrorActionPreference = "Stop"
Add-Type -AssemblyName System.IO.Compression.FileSystem
New-Item -ItemType Directory -Force $CacheRoot | Out-Null

function Expand-Zip([string]$ZipPath, [string]$Destination) {
    [System.IO.Compression.ZipFile]::ExtractToDirectory($ZipPath, $Destination)
}

$gradleBat = Join-Path $CacheRoot "gradle-8.11.1\bin\gradle.bat"
if (-not (Test-Path $gradleBat)) {
    $zip = Join-Path $CacheRoot "gradle-8.11.1-bin.zip"
    if (-not (Test-Path $zip)) {
        Invoke-WebRequest -Uri "https://mirrors.cloud.tencent.com/gradle/gradle-8.11.1-bin.zip" -OutFile $zip
    }
    Expand-Zip $zip $CacheRoot
}

$sdkRoot = Join-Path $CacheRoot "android-sdk"
$sdkManager = Get-ChildItem (Join-Path $sdkRoot "cmdline-tools") -Recurse -Filter sdkmanager.bat -ErrorAction SilentlyContinue | Select-Object -First 1 -ExpandProperty FullName
if (-not $sdkManager) {
    $toolsZip = Join-Path $CacheRoot "commandlinetools-win-13114758_latest.zip"
    if (-not (Test-Path $toolsZip)) {
        Invoke-WebRequest -Uri "https://mirrors.cloud.tencent.com/AndroidSDK/commandlinetools-win-13114758_latest.zip" -OutFile $toolsZip
    }
    $tmp = Join-Path $CacheRoot "cmdline-tools-tmp"
    New-Item -ItemType Directory -Force $tmp | Out-Null
    Expand-Zip $toolsZip $tmp
    New-Item -ItemType Directory -Force (Join-Path $sdkRoot "cmdline-tools") | Out-Null
    Move-Item -LiteralPath (Join-Path $tmp "cmdline-tools") -Destination (Join-Path $sdkRoot "cmdline-tools\latest") -Force
    Remove-Item -LiteralPath $tmp -Recurse -Force
    $sdkManager = Join-Path $sdkRoot "cmdline-tools\latest\bin\sdkmanager.bat"
}

$env:JAVA_HOME = "C:\Program Files\Zulu\zulu-17"
$env:ANDROID_SDK_ROOT = $sdkRoot
$env:ANDROID_HOME = $sdkRoot

if (-not (Test-Path (Join-Path $sdkRoot "platforms\android-35"))) {
    $answers = (1..20 | ForEach-Object { "y" }) -join "`n"
    $answers | & $sdkManager --sdk_root=$sdkRoot "platform-tools" "platforms;android-35" "build-tools;35.0.0"
}

Write-Output "GRADLE=$gradleBat"
Write-Output "SDK=$sdkRoot"
