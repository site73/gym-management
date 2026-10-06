@echo off
chcp 65001 >nul
title Gym System One-Click Launcher
rem Thin wrapper (ASCII only, to avoid any encoding issue).
rem All logic (and Chinese paths) live in the Node script below, which is UTF-8 safe.
"E:\dev\nodejs\node.exe" "E:\dev\start-system.js" %*
if errorlevel 1 pause
