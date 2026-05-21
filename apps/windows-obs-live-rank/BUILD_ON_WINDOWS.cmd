@echo off
setlocal
cd /d "%~dp0"

set REQUIRED_BRANCH=codex/work6-channel-analysis
set CURRENT_BRANCH=
set CURRENT_COMMIT=

where git >nul 2>nul
if not errorlevel 1 (
  for /f "delims=" %%B in ('git -C "%~dp0..\.." branch --show-current 2^>nul') do set CURRENT_BRANCH=%%B
  for /f "delims=" %%C in ('git -C "%~dp0..\.." rev-parse --short HEAD 2^>nul') do set CURRENT_COMMIT=%%C
  if not "%CURRENT_BRANCH%"=="%REQUIRED_BRANCH%" (
    echo Wrong source branch for Work6 build.
    echo Current branch: %CURRENT_BRANCH%
    echo Required branch: %REQUIRED_BRANCH%
    echo.
    echo Run these commands from the repository root, then build again:
    echo git fetch origin
    echo git switch %REQUIRED_BRANCH%
    echo git pull --ff-only origin %REQUIRED_BRANCH%
    pause
    exit /b 1
  )
)

findstr /c:"work6-channel-analysis" ComparisonModels.cs >nul
if errorlevel 1 (
  echo Work6 source marker was not found.
  echo This build is blocked to avoid creating a legacy comparison EXE.
  pause
  exit /b 1
)

findstr /c:"FinishMonitoringAfterComparison" LiveHelperWindowsObsRank.cs >nul
if errorlevel 1 (
  echo Work6 stability fix marker was not found.
  echo Pull the latest %REQUIRED_BRANCH% source, then build again.
  pause
  exit /b 1
)

set CSC=
if exist "%WINDIR%\Microsoft.NET\Framework64\v4.0.30319\csc.exe" set CSC=%WINDIR%\Microsoft.NET\Framework64\v4.0.30319\csc.exe
if not defined CSC if exist "%WINDIR%\Microsoft.NET\Framework\v4.0.30319\csc.exe" set CSC=%WINDIR%\Microsoft.NET\Framework\v4.0.30319\csc.exe

if not defined CSC (
  echo Windows .NET Framework compiler csc.exe not found.
  echo Try this on a normal Windows 10/11 PC, or install .NET Framework developer tools.
  pause
  exit /b 1
)

if not exist dist mkdir dist

"%CSC%" ^
  /nologo ^
  /target:winexe ^
  /platform:anycpu ^
  /out:dist\LiveHelperWindowsObsRank.exe ^
  /reference:System.dll ^
  /reference:System.Core.dll ^
  /reference:System.Drawing.dll ^
  /reference:System.Windows.Forms.dll ^
  /reference:System.Web.Extensions.dll ^
  LiveHelperWindowsObsRank.cs ^
  YoutubeClient.cs ^
  ComparisonModels.cs

if errorlevel 1 (
  echo.
  echo Build failed.
  pause
  exit /b 1
)

echo.
echo Build complete:
echo %~dp0dist\LiveHelperWindowsObsRank.exe
if defined CURRENT_COMMIT echo Source: %REQUIRED_BRANCH% @ %CURRENT_COMMIT%
echo.
pause
