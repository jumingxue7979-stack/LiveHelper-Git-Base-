$ErrorActionPreference = "Stop"

$Root = Split-Path -Parent $MyInvocation.MyCommand.Path
$Script = Join-Path $Root "livehelper-windows-build-agent.js"

$NodeCandidates = @(
  "C:\Program Files\nodejs\node.exe",
  "C:\Program Files (x86)\nodejs\node.exe"
)

$Node = $NodeCandidates | Where-Object { Test-Path -LiteralPath $_ } | Select-Object -First 1
if (-not $Node) {
  $Node = (Get-Command node -ErrorAction Stop).Source
}

Write-Host "Starting LiveHelper Windows Build Agent..."
Write-Host "Root: $Root"
Write-Host "Node: $Node"
Write-Host "URL:  http://192.168.1.86:8790"

try {
  Invoke-WebRequest -Uri "http://127.0.0.1:8790/" -UseBasicParsing -TimeoutSec 2 | Out-Null
  Write-Host "LiveHelper Windows Build Agent is already running."
} catch {
  Start-Process -FilePath $Node -ArgumentList "`"$Script`"" -WorkingDirectory $Root -WindowStyle Hidden
  Start-Sleep -Seconds 1
  try {
    Invoke-WebRequest -Uri "http://127.0.0.1:8790/" -UseBasicParsing -TimeoutSec 2 | Out-Null
    Write-Host "LiveHelper Windows Build Agent is running."
  } catch {
    Write-Host "Agent start requested, but health check did not respond yet."
  }
}
