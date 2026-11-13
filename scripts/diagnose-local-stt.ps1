param([switch]$Rebuild, [switch]$CheckLlm, [switch]$Evaluate)
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

if ($Evaluate) {
    Write-Host 'Running the real Java analysis service against 12 public synthetic examples. Existing records are preserved.'
    & $dockerPath compose --env-file .env.local-demo -f compose.local-demo.json --profile evaluation run --rm --build --no-deps ai-evaluation
    $evaluationExit = $LASTEXITCODE
    Write-Host 'Report: build/reports/local-ai-evaluation.json'
    if ($evaluationExit -ne 0) {
        Write-Host 'The evaluation did not pass every case, or the command failed. Inspect the report and build output.'
        exit $evaluationExit
    }
    Write-Host 'All evaluation cases passed. This does not measure human speech recognition accuracy.'
    return
}

if ($CheckLlm) {
    # Match the application prompt, using only the public fixture reference text.
    $source = Get-Content -LiteralPath (Join-Path $projectDir 'src\main\java\community\likelion\growwithusserver\ai\service\VoiceMemoAnalysisService.java') -Raw -Encoding UTF8
    $match = [regex]::Match($source, '(?s)llmClient\.complete\("""(.*?)"""')
    if (-not $match.Success) { throw 'The voice analysis prompt was not found.' }
    $reference = (Get-Content -LiteralPath (Join-Path $projectDir 'src\test\resources\ai-evaluation\synthetic-korean-reference.txt') -Raw -Encoding UTF8).Trim()
    $payload = @{system = $match.Groups[1].Value.Trim(); transcript = $reference} | ConvertTo-Json -Compress
    $encodedPayload = [Convert]::ToBase64String([System.Text.Encoding]::UTF8.GetBytes($payload))
    $probe = @"
import base64, json, time, urllib.request, urllib.error
fixture = json.loads(base64.b64decode('$encodedPayload'))
body = {'model': 'qwen2.5:3b', 'stream': False, 'format': 'json', 'messages': [
    {'role': 'system', 'content': fixture['system']},
    {'role': 'user', 'content': json.dumps({'transcript': fixture['transcript']}, ensure_ascii=False)}],
    'options': {'temperature': 0, 'num_predict': 1024}}
started = time.monotonic()
report = {'syntheticText': True}
try:
    request = urllib.request.Request('http://127.0.0.1:11434/api/chat', data=json.dumps(body).encode(), headers={'Content-Type': 'application/json'})
    with urllib.request.urlopen(request, timeout=100) as response:
        reply = json.load(response)
    content = reply.get('message', {}).get('content', '')
    report.update(status='passed', modelReply=content[:16000], doneReason=reply.get('done_reason'))
except urllib.error.HTTPError as error:
    report.update(status='failed', errorType=type(error).__name__, httpStatus=error.code, message=error.read(1200).decode('utf-8', errors='replace'))
except Exception as error:
    report.update(status='failed', errorType=type(error).__name__, message=str(error)[:600])
report['seconds'] = round(time.monotonic() - started, 2)
print(json.dumps(report, ensure_ascii=True), flush=True)
"@
    Write-Host 'Checking local LLM with the application prompt and public synthetic text. No records are changed.'
    $result = $probe | & $dockerPath compose --env-file .env.local-demo -f compose.local-demo.json exec -T stt python -
    if ($LASTEXITCODE -ne 0) { throw 'LLM diagnostic command failed. Keep this window open.' }
    $report = ($result -join "`n") | ConvertFrom-Json
    $report | Add-Member -NotePropertyName checkedAt -NotePropertyValue ([DateTime]::UtcNow.ToString('o'))
    $reportDir = Join-Path $projectDir 'build\reports'
    New-Item -ItemType Directory -Path $reportDir -Force | Out-Null
    $reportPath = Join-Path $reportDir 'local-llm-diagnostic.json'
    $report | ConvertTo-Json -Depth 5 | Set-Content -LiteralPath $reportPath -Encoding UTF8
    Write-Host "Result saved: $reportPath"
    Write-Host "LLM transport result: $($report.status). Application summary validation is checked separately."
    if ($report.status -eq 'failed') { Write-Host "$($report.errorType): $($report.message)"; exit 1 }
    return
}

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
