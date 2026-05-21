@echo off
setlocal EnableExtensions EnableDelayedExpansion
cd /d "%~dp0"

set REQUIRED_BRANCH=codex/work6-channel-analysis
set REPO_ROOT=%~dp0..\..
set IS_GIT_WORKTREE=
set CURRENT_BRANCH=
set CURRENT_COMMIT=
set CURRENT_FULL_COMMIT=
set REMOTE_COMMIT=
set REMOTE_FULL_COMMIT=

where git >nul 2>nul
if not errorlevel 1 (
  for /f "delims=" %%G in ('git -C "%REPO_ROOT%" rev-parse --is-inside-work-tree 2^>nul') do set IS_GIT_WORKTREE=%%G
  if /i "!IS_GIT_WORKTREE!"=="true" (
    for /f "delims=" %%B in ('git -C "%REPO_ROOT%" branch --show-current 2^>nul') do set CURRENT_BRANCH=%%B
    for /f "delims=" %%C in ('git -C "%REPO_ROOT%" rev-parse --short HEAD 2^>nul') do set CURRENT_COMMIT=%%C
    for /f "delims=" %%H in ('git -C "%REPO_ROOT%" rev-parse HEAD 2^>nul') do set CURRENT_FULL_COMMIT=%%H
    if not "!CURRENT_BRANCH!"=="%REQUIRED_BRANCH%" (
      echo Wrong source branch for Work6 build.
      echo Current branch: !CURRENT_BRANCH!
      echo Required branch: %REQUIRED_BRANCH%
      echo.
      echo Run these commands from the repository root, then build again:
      echo git fetch origin
      echo git switch %REQUIRED_BRANCH%
      echo git pull --ff-only origin %REQUIRED_BRANCH%
      pause
      exit /b 1
    )

    echo Checking latest Work6 source from origin...
    git -C "%REPO_ROOT%" fetch origin +refs/heads/%REQUIRED_BRANCH%:refs/remotes/origin/%REQUIRED_BRANCH% >nul 2>nul
    if errorlevel 1 (
      echo Could not fetch origin/%REQUIRED_BRANCH%.
      echo Connect this Windows PC to the network, pull the latest Work6 branch, then build again.
      pause
      exit /b 1
    )
    for /f "delims=" %%R in ('git -C "%REPO_ROOT%" rev-parse --short origin/%REQUIRED_BRANCH% 2^>nul') do set REMOTE_COMMIT=%%R
    for /f "delims=" %%O in ('git -C "%REPO_ROOT%" rev-parse origin/%REQUIRED_BRANCH% 2^>nul') do set REMOTE_FULL_COMMIT=%%O
    if not "!CURRENT_FULL_COMMIT!"=="!REMOTE_FULL_COMMIT!" (
      echo Local Work6 source is not the latest pushed commit.
      echo Local:  !CURRENT_COMMIT!
      echo Remote: !REMOTE_COMMIT!
      echo.
      echo Run this from the repository root:
      echo git pull --ff-only origin %REQUIRED_BRANCH%
      pause
      exit /b 1
    )

    git -C "%REPO_ROOT%" diff --quiet -- apps/windows-obs-live-rank
    if errorlevel 1 (
      echo Local Windows source has uncommitted changes.
      echo Commit, stash, or restore them before making a release EXE.
      pause
      exit /b 1
    )
  ) else (
    echo Git repository not detected. Standalone source marker checks will be used.
  )
)

findstr /c:"work6-channel-analysis" ComparisonModels.cs >nul
if errorlevel 1 (
  echo Work6 source marker was not found.
  echo This build is blocked to avoid creating a legacy comparison EXE.
  pause
  exit /b 1
)

findstr /c:"ownRankAccess" ComparisonModels.cs >nul
if errorlevel 1 (
  echo Work6 detailed score marker was not found.
  echo Pull the latest %REQUIRED_BRANCH% source, then build again.
  pause
  exit /b 1
)

findstr /c:"fallbackItem != null && result.TargetVideoId.Length == 0" YoutubeClient.cs >nul
if errorlevel 1 (
  echo Work6 exact video rank matching marker was not found.
  echo Pull the latest %REQUIRED_BRANCH% source, then build again.
  pause
  exit /b 1
)

findstr /c:"refreshTimer.Stop();" LiveHelperWindowsObsRank.cs >nul
if errorlevel 1 (
  echo Work6 timer stability marker was not found.
  echo Pull the latest %REQUIRED_BRANCH% source, then build again.
  pause
  exit /b 1
)

findstr /c:"CategoryDisplayName" LiveHelperWindowsObsRank.cs >nul
if errorlevel 1 (
  echo Work6 7-item dashboard marker was not found.
  echo Pull the latest %REQUIRED_BRANCH% source, then build again.
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

if not defined CSC (
  set CSC=
)

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
if defined CURRENT_COMMIT echo Source: %REQUIRED_BRANCH% @ !CURRENT_COMMIT!
echo.
pause
