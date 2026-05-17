@echo off
setlocal
cd /d "%~dp0"

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
echo.
pause
