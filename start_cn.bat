@echo off
:: 设置控制台编码为 UTF-8
chcp 65001 >nul

:: 设置字体支持中文
reg add "HKCU\Console" /v "FaceName" /t REG_SZ /d "Lucida Console" /f >nul 2>&1

:: 启动 PowerShell 脚本
powershell -NoProfile -ExecutionPolicy Bypass -Command "& 'd:\workspace\LibreraReader\start_librera_cn.ps1'"

pause