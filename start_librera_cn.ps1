<#
===========================================================================
 Librera Reader 启动脚本
===========================================================================
 功能：自动构建、安装并启动 Librera Reader 应用
 环境：Windows PowerShell 5.1+
 依赖：Java 21, Android SDK, Android Emulator
===========================================================================
#>

# 强制设置控制台编码为 UTF-8
[Console]::OutputEncoding = [System.Text.Encoding]::UTF8
$OutputEncoding = [System.Text.Encoding]::UTF8
$PSDefaultParameterValues['*:Encoding'] = 'UTF8'

# 颜色定义
$Cyan = [ConsoleColor]::Cyan
$Green = [ConsoleColor]::Green
$Yellow = [ConsoleColor]::Yellow
$Red = [ConsoleColor]::Red

# 检查目录
if (-not (Test-Path -Path "d:\workspace\LibreraReader")) {
    Write-Host "错误：项目目录不存在！" -ForegroundColor $Red
    exit 1
}

Set-Location -Path "d:\workspace\LibreraReader"

# 检查 Java
Write-Host "`n=== 检查 Java 环境 ===" -ForegroundColor $Cyan
if (-not (Test-Path -Path "D:\java\java21")) {
    Write-Host "警告：未找到 Java 21，尝试自动检测..." -ForegroundColor $Yellow
    $javaPath = Get-Command java -ErrorAction SilentlyContinue
    if ($javaPath) {
        Write-Host "找到 Java: $($javaPath.Source)" -ForegroundColor $Green
    } else {
        Write-Host "错误：未找到 Java！" -ForegroundColor $Red
        exit 1
    }
} else {
    $env:JAVA_HOME = "D:\java\java21"
    $env:PATH = "$env:JAVA_HOME\bin;D:\AppData\Android\studio_sdk\platform-tools;$env:PATH"
    Write-Host "设置 Java 环境: $env:JAVA_HOME" -ForegroundColor $Green
}

# 检查设备
Write-Host "`n=== 检查设备 ===" -ForegroundColor $Cyan
$devices = & adb devices 2>&1
if ($LASTEXITCODE -ne 0) {
    Write-Host "错误：ADB 命令执行失败！" -ForegroundColor $Red
    exit 1
}
Write-Host "已连接设备:" -ForegroundColor $Green
& adb devices

# 构建应用
Write-Host "`n=== 构建 F-Droid 版本 ===" -ForegroundColor $Cyan
& .\gradlew assembleFdroidDebug
if ($LASTEXITCODE -ne 0) {
    Write-Host "错误：构建失败！" -ForegroundColor $Red
    exit 1
}
Write-Host "构建成功！" -ForegroundColor $Green

# 安装应用
Write-Host "`n=== 安装应用 ===" -ForegroundColor $Cyan
$apkPath = Get-ChildItem -Path "app\build\outputs\apk\fdroid\debug" -Filter "*x86_64*.apk" | Select-Object -First 1
if (-not $apkPath) {
    $apkPath = Get-ChildItem -Path "app\build\outputs\apk\fdroid\debug" -Filter "*.apk" | Select-Object -First 1
}
if (-not $apkPath) {
    Write-Host "错误：未找到 APK 文件！" -ForegroundColor $Red
    exit 1
}
Write-Host "安装 APK: $($apkPath.Name)" -ForegroundColor $Yellow
& adb install -r $apkPath.FullName

# 启动应用
Write-Host "`n=== 启动应用 ===" -ForegroundColor $Cyan
& adb shell am start -n com.foobnix.pro.pdf.reader/com.foobnix.ui2.MainTabs2
if ($LASTEXITCODE -eq 0) {
    Write-Host "应用启动成功！" -ForegroundColor $Green
} else {
    Write-Host "警告：启动命令返回非零代码" -ForegroundColor $Yellow
}

Write-Host "`n=== 完成 ===" -ForegroundColor $Green
Write-Host "Librera Reader 已启动！" -ForegroundColor $Green