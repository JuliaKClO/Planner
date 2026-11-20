@echo off
setlocal
powershell.exe -NoLogo -NoProfile -ExecutionPolicy RemoteSigned -File "%~dp0scripts\run-planner.ps1"
set "plannerExit=%ERRORLEVEL%"
if not "%plannerExit%"=="0" pause
exit /b %plannerExit%
