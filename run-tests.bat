@echo off
setlocal
powershell.exe -NoLogo -NoProfile -ExecutionPolicy RemoteSigned -File "%~dp0scripts\run-tests.ps1" %*
set "testExitCode=%ERRORLEVEL%"
if /I not "%~1"=="-NoPause" pause
exit /b %testExitCode%
