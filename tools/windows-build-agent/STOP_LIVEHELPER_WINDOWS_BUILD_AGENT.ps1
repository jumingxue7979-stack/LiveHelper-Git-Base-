$ErrorActionPreference = "SilentlyContinue"

$AgentScript = "livehelper-windows-build-agent.js"

Get-CimInstance Win32_Process |
  Where-Object { $_.Name -eq "node.exe" -and $_.CommandLine -like "*$AgentScript*" } |
  ForEach-Object {
    Stop-Process -Id $_.ProcessId -Force
    Write-Host "Stopped LiveHelper Windows Build Agent PID $($_.ProcessId)"
  }
