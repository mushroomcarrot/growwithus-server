param([switch]$Rebuild)
$ErrorActionPreference = 'Stop'
$projectDir = [System.IO.Path]::GetFullPath((Join-Path $PSScriptRoot '..'))
Set-Location -LiteralPath $projectDir
$docker = Get-Command docker.exe -ErrorAction SilentlyContinue
if ($docker) { $dockerPath = $docker.Source }
else {
    $dockerPath = Join-Path $env:LOCALAPPDATA 'Programs\DockerDesktop\resources\bin\docker.exe'
    if (-not (Test-Path -LiteralPath $dockerPath)) {
        $dockerPath = Join-Path $env:ProgramFiles 'Docker\Docker\resources\bin\docker.exe'
    }
}
if (-not (Test-Path -LiteralPath $dockerPath)) { throw 'Docker CLI was not found.' }

if ($Rebuild) {
    Write-Host 'Rebuilding only STT with compatible audio libraries. Models and database records are preserved.'
    & $dockerPath compose --env-file .env.local-demo -f compose.local-demo.json build stt
    if ($LASTEXITCODE -ne 0) { throw 'STT build failed. The existing service was not replaced.' }
    & $dockerPath compose --env-file .env.local-demo -f compose.local-demo.json up -d --no-deps stt
    if ($LASTEXITCODE -ne 0) { throw 'STT restart failed. Keep this window open.' }
    $ready = $false
    for ($attempt = 0; $attempt -lt 60; $attempt++) {
        $container = & $dockerPath compose --env-file .env.local-demo -f compose.local-demo.json ps -q stt
        if ($LASTEXITCODE -ne 0 -or -not $container) { throw 'STT container was not found.' }
        $health = & $dockerPath inspect --format '{{.State.Health.Status}}' $container
        if ($LASTEXITCODE -ne 0) { throw 'STT health status could not be read.' }
        if ($health -eq 'healthy') { $ready = $true; break }
        if ($health -eq 'unhealthy') { throw 'STT did not become healthy. Existing records were preserved.' }
        Start-Sleep -Seconds 2
    }
    if (-not $ready) { throw 'STT startup timed out. Existing records were preserved.' }
}

# Only the public synthetic fixture is used. No settings, tokens or user recordings are printed.
$fixture = Join-Path $projectDir 'src\test\resources\ai-evaluation\synthetic-korean.wav'
$encoded = [Convert]::ToBase64String([System.IO.File]::ReadAllBytes($fixture))
$probe = @"
import base64, json, time
import stt_server
started = time.monotonic()
try:
    model = stt_server.load_model()
    result = stt_server.transcribe(model, base64.b64decode('$encoded'))
    report = {'status': 'passed', 'syntheticAudio': True, 'seconds': round(time.monotonic() - started, 2), **result}
except Exception as error:
    report = {'status': 'failed', 'syntheticAudio': True, 'errorType': type(error).__name__, 'message': str(error)[:600]}
print(json.dumps(report, ensure_ascii=True), flush=True)
"@
Write-Host 'Checking the installed STT model with synthetic Korean speech. Existing records are preserved.'
$result = $probe | & $dockerPath compose --env-file .env.local-demo -f compose.local-demo.json exec -T stt python -
if ($LASTEXITCODE -ne 0) { throw 'STT diagnostic command failed. Keep this window open.' }
$report = ($result -join "`n") | ConvertFrom-Json
if ($report.status -notin @('passed', 'failed')) { throw 'Unexpected STT diagnostic response.' }
$report | Add-Member -NotePropertyName checkedAt -NotePropertyValue ([DateTime]::UtcNow.ToString('o'))
$reportDir = Join-Path $projectDir 'build\reports'
New-Item -ItemType Directory -Path $reportDir -Force | Out-Null
$reportPath = Join-Path $reportDir 'local-stt-diagnostic.json'
$report | ConvertTo-Json -Depth 5 | Set-Content -LiteralPath $reportPath -Encoding UTF8
Write-Host "Result saved: $reportPath"
Write-Host "STT result: $($report.status)"
if ($report.status -eq 'failed') { Write-Host "$($report.errorType): $($report.message)"; exit 1 }
