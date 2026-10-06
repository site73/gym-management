@echo off
chcp 65001 >nul
setlocal EnableExtensions
title Starting MySQL 8.0 (port 3307)

set "MYDIR=E:\dev\mysql"
set "PORT=3307"

echo ============================================================
echo  MySQL 8.0.29  portable  ^|  port %PORT%  ^|  db: gym
echo ============================================================
echo.

rem ---- 1. 启动（若已在运行则跳过） ----
tasklist /FI "IMAGENAME eq mysqld.exe" 2>nul | find /I "mysqld.exe" >nul
if not errorlevel 1 (
  echo [1/3] MySQL is already running.
) else (
  echo [1/3] Starting MySQL in a separate window ...
  start "MySQL 8.0 (port %PORT%)" "%MYDIR%\bin\mysqld.exe" --defaults-file="%MYDIR%\my.ini" --console
)

rem ---- 2. 等待就绪（最多 30 秒） ----
echo [2/3] Waiting for MySQL to accept connections ...
set /a N=0
:wait
"%MYDIR%\bin\mysqladmin.exe" -u gym_app -pgym_app_pwd -h 127.0.0.1 -P %PORT% ping >nul 2>&1
if not errorlevel 1 goto ready
set /a N+=1
if %N% GEQ 30 goto failed
timeout /t 1 /nobreak >nul
goto wait

:ready
echo [3/3] MySQL is READY.
echo.
echo   user  : gym_app / gym_app_pwd      db : gym
echo   admin : root  (no password)
echo   note  : keep BOTH this window and the MySQL window open.
echo.
timeout /t 5 /nobreak >nul
exit /b 0

:failed
echo.
echo [ERROR] MySQL did not become ready within 30s.
echo.
echo   Possible causes and fixes:
echo    1^) Port %PORT% already in use or stale process
echo       -^> run:  taskkill /IM mysqld.exe /F    then run this again
echo    2^) Data directory locked by a previous crashed instance
echo       -^> same as above
echo    3^) Want the real error message
echo       -^> run directly in a console window:
echo          "%MYDIR%\bin\mysqld.exe" --defaults-file="%MYDIR%\my.ini" --console
echo.
pause
exit /b 1
