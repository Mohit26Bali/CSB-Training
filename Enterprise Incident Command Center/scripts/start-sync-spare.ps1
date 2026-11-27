# Starts a SPARE Incident service on port 18086 with the ServiceNow sync switched on (D38), for the live sync test.
#
#   powershell -File scripts\start-sync-spare.ps1
#   node scripts\sync-live-test.mjs
#   powershell -File scripts\stop-sync-spare.ps1
#
# The normal stack keeps running with icc.itsm.mode=local and sends nothing to ServiceNow.
# The spare does NOT register with Eureka, so the gateway never sends the browser's requests to it.
# It reads .env (database, JWT secret, ServiceNow connection) and prints none of it.
$root = Split-Path -Parent $PSScriptRoot
$envFile = Join-Path $root ".env"
if (-not (Test-Path $envFile)) { Write-Host "No .env file in $root" -ForegroundColor Red; exit 1 }
Get-Content $envFile | ForEach-Object {
    if ($_ -match '^\s*([^#=\s]+)=(.*)$') { Set-Item -Path ("env:" + $matches[1]) -Value $matches[2].Trim() }
}
foreach ($key in 'SERVICENOW_URL', 'SERVICENOW_USER', 'SERVICENOW_PASSWORD') {
    if (-not (Get-Item -Path ("env:" + $key) -ErrorAction SilentlyContinue).Value) { Write-Host "$key is missing in .env" -ForegroundColor Red; exit 1 }
}
$jar = Join-Path $root "services\incident-service\target\incident-service-0.0.1-SNAPSHOT.jar"
if (-not (Test-Path $jar)) { Write-Host "Build first: mvn -q -DskipTests package" -ForegroundColor Red; exit 1 }
if (Get-NetTCPConnection -State Listen -LocalPort 18086 -ErrorAction SilentlyContinue) { Write-Host "Port 18086 is already in use. Run stop-sync-spare.ps1 first." -ForegroundColor Red; exit 1 }

$env:ICC_ITSM_MODE = 'servicenow'
$env:SERVER_PORT = '18086'
Start-Process java -ArgumentList '-jar', "`"$jar`"", '--eureka.client.register-with-eureka=false' -WindowStyle Hidden
Write-Host "Starting the spare Incident service (sync ON) on port 18086 ..."
for ($i = 0; $i -lt 40; $i++) {
    Start-Sleep 3
    if (Get-NetTCPConnection -State Listen -LocalPort 18086 -ErrorAction SilentlyContinue) {
        Write-Host "Ready. Now run: node scripts\sync-live-test.mjs" -ForegroundColor Green
        exit 0
    }
}
Write-Host "It did not start within two minutes." -ForegroundColor Red
exit 1
