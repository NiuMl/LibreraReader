@echo off
setlocal enabledelayedexpansion

set JAVA_HOME=D:\java\java21
set PATH=%JAVA_HOME%\bin;D:\AppData\Android\studio_sdk\platform-tools;%PATH%

echo ================================================
echo   Librera Reader Build ^& Run
echo ================================================
echo.

echo [1/4] Checking Java environment...
java -version 2>&1 | findstr /i "version"
if %ERRORLEVEL% neq 0 (
    echo ERROR: Java not found!
    pause
    exit /b 1
)
echo OK
echo.

echo [2/4] Checking connected devices...
adb devices
echo.

echo [3/4] Building F-Droid version...
call gradlew assembleFdroidDebug
if %ERRORLEVEL% neq 0 (
    echo ERROR: Build failed!
    pause
    exit /b 1
)
echo BUILD SUCCESSFUL
echo.

echo [4/4] Installing and launching...
set "APK_PATH="
for /f "delims=" %%a in ('dir /b /s app\build\outputs\apk\fdroid\debug\*.apk') do (
    set "APK_PATH=%%a"
)
if not defined APK_PATH (
    echo ERROR: APK file not found!
    pause
    exit /b 1
)
echo Installing: !APK_PATH!
adb install -r -d "!APK_PATH!"
if %ERRORLEVEL% equ 0 (
    adb shell am start -n com.foobnix.pro.pdf.reader/com.foobnix.ui2.MainTabs2
    if %ERRORLEVEL% equ 0 (
        echo.
        echo ================================================
        echo   Librera Reader started successfully!
        echo ================================================
    ) else (
        echo WARNING: Launch returned non-zero code
    )
) else (
    echo ERROR: Installation failed!
)

pause