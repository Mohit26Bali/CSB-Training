# Stops the spare Incident service started by start-sync-spare.ps1 (only that one: it is the java process started with
# --eureka.client.register-with-eureka=false). The normal stack is not touched.
$found = Get-CimInstance Win32_Process | Where-Object { $_.Name -eq 'java.exe' -and $_.CommandLine -match 'register-with-eureka=false' }
if (-not $found) { Write-Host "No spare service is running."; exit 0 }
foreach ($p in $found) { Stop-Process -Id $p.ProcessId -Force; Write-Host ("stopped process " + $p.ProcessId) }
