<#
  私酒馆一键构建脚本（Windows / PowerShell）

  用法：
    powershell -ExecutionPolicy Bypass -File .\build.ps1
    powershell -ExecutionPolicy Bypass -File .\build.ps1 -OutApk "D:\apk\私酒馆-debug.apk"

  说明：
  - AGP 不接受中文项目路径；脚本会把项目复制到纯英文构建目录（默认 %TEMP%\tavern-build）再构建。
  - 需要 JDK 17+、Android SDK（platform-34 + build-tools 34.0.0）、Gradle 8.7。
  - 路径优先取环境变量 JAVA_HOME / ANDROID_HOME / GRADLE_BIN，其次取常见安装位置。
#>
param(
    [string]$BuildDir = (Join-Path $env:TEMP "tavern-build"),
    [string]$OutApk = "",
    [switch]$SkipClean
)

$ErrorActionPreference = "Stop"
$project = Split-Path -Parent $MyInvocation.MyCommand.Path

# ---- 定位 JDK ----
$jdk = $env:JAVA_HOME
if (-not $jdk -or -not (Test-Path (Join-Path $jdk "bin\java.exe"))) {
    $candidates = @(
        (Join-Path $env:LOCALAPPDATA "Programs\Android Studio\jbr"),
        (Join-Path $env:ProgramFiles "Android\Android Studio\jbr")
    )
    $jdk = $candidates | Where-Object { $_ -and (Test-Path (Join-Path $_ "bin\java.exe")) } | Select-Object -First 1
}
if (-not $jdk -or -not (Test-Path (Join-Path $jdk "bin\java.exe"))) {
    throw "找不到 JDK，请设置 JAVA_HOME（JDK 17 或更高）"
}

# ---- 定位 Android SDK ----
$sdk = $env:ANDROID_HOME
if (-not $sdk) { $sdk = $env:ANDROID_SDK_ROOT }
if (-not $sdk) { $sdk = Join-Path $env:LOCALAPPDATA "Android\Sdk" }
if (-not (Test-Path (Join-Path $sdk "platforms\android-34"))) {
    throw "Android SDK 缺少 platform-34，请设置 ANDROID_HOME 或用 sdkmanager 安装"
}

# ---- 定位 Gradle ----
$gradleBat = $env:GRADLE_BIN
if (-not $gradleBat) {
    $cmd = Get-Command gradle.bat -ErrorAction SilentlyContinue
    if ($cmd) { $gradleBat = $cmd.Source }
}
if (-not $gradleBat) { $gradleBat = "gradle.bat" }

$env:JAVA_HOME = $jdk
$env:ANDROID_HOME = $sdk
$env:ANDROID_SDK_ROOT = $sdk
$env:PATH = "$jdk\bin;$env:PATH"

# ---- 准备 ASCII 构建目录 ----
if (-not $SkipClean) {
    & $gradleBat --stop 2>$null | Out-Null
    Start-Sleep -Seconds 2
    if (Test-Path $BuildDir) {
        Remove-Item $BuildDir -Recurse -Force -ErrorAction SilentlyContinue
    }
}
New-Item -ItemType Directory -Force -Path (Split-Path $BuildDir) | Out-Null
Copy-Item $project $BuildDir -Recurse -Force
Set-Content -Path (Join-Path $BuildDir "local.properties") `
    -Value ("sdk.dir=" + ($sdk -replace '\\', '\\')) -Encoding ascii
Add-Content -Path (Join-Path $BuildDir "gradle.properties") `
    -Value "`nandroid.overridePathCheck=true" -Encoding utf8

# ---- 构建 ----
Push-Location $BuildDir
try {
    & $gradleBat assembleDebug --no-daemon --console=plain
    if ($LASTEXITCODE -ne 0) { throw "gradle assembleDebug 失败（exit $LASTEXITCODE）" }
} finally {
    Pop-Location
}

$apk = Join-Path $BuildDir "app\build\outputs\apk\debug\app-debug.apk"
if (-not (Test-Path $apk)) { throw "没有找到 APK 产物：$apk" }

if (-not $OutApk) {
    $OutApk = Join-Path $project "私酒馆-debug.apk"
}
Copy-Item $apk $OutApk -Force

Write-Host ""
Write-Host "构建成功：$OutApk" -ForegroundColor Green
Get-Item $OutApk | Select-Object FullName, Length | Format-List
Get-FileHash $OutApk -Algorithm SHA256 | Format-List
