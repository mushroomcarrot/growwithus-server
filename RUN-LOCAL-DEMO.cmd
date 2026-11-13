@echo off
setlocal
powershell.exe -NoLogo -NoProfile -ExecutionPolicy Bypass -File "%~dp0scripts\setup-local-demo.ps1"
set "taskExit=%ERRORLEVEL%"
if not "%taskExit%"=="0" (
  echo.
  echo Setup stopped. Read the message above. Existing data was preserved.
)
pause
exit /b %taskExit%
