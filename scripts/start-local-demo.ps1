param([switch]$PrepareOnly)
$ErrorActionPreference = 'Stop'
$projectDir = [System.IO.Path]::GetFullPath((Join-Path $PSScriptRoot '..'))
$settingsFile = Join-Path $projectDir '.env.local-demo'
$composeFile = Join-Path $projectDir 'compose.local-demo.json'
function New-LocalSecret([int]$Length = 24) {
    $bytes = New-Object byte[] $Length
    $rng = [System.Security.Cryptography.RandomNumberGenerator]::Create()
    try { $rng.GetBytes($bytes) } finally { $rng.Dispose() }
    return [Convert]::ToBase64String($bytes)
}
if (-not (Test-Path -LiteralPath $settingsFile)) {
    $startedOn = [DateTimeOffset]::UtcNow.ToOffset([TimeSpan]::FromHours(9)).Date.AddDays(-21).ToString('yyyy-MM-dd')
    $demoPassword = 'D1!' + (New-LocalSecret 12).Replace('+', '-').Replace('/', '_')
    $settings = @(
        "LOCAL_DB_ROOT_PASSWORD=$(New-LocalSecret)"
        "LOCAL_DB_PASSWORD=$(New-LocalSecret)"
        "LOCAL_REDIS_PASSWORD=$(New-LocalSecret)"
        "LOCAL_JWT_SECRET=$(New-LocalSecret 32)"
        "LOCAL_SERVICE_KEY=$(New-LocalSecret)"
        "LOCAL_DEMO_PASSWORD=$demoPassword"
        "LOCAL_DEMO_STARTED_ON=$startedOn"
    ) -join "`n"
    [System.IO.File]::WriteAllText($settingsFile, $settings + "`n", (New-Object System.Text.UTF8Encoding($false)))
}
Get-Content -LiteralPath $composeFile -Raw | ConvertFrom-Json | Out-Null
if ($PrepareOnly) { Write-Host 'Prepared .env.local-demo. Existing settings were preserved. No services started.'; return }
if (-not (Get-Command docker -ErrorAction SilentlyContinue)) { throw 'Install and start Docker Desktop, then run this script again. See docs/local-demo.md.' }
& docker info --format '{{.ServerVersion}}' | Out-Null
if ($LASTEXITCODE -ne 0) { throw 'Docker engine is not running. Start Docker Desktop first.' }
& docker compose --env-file $settingsFile -f $composeFile config --quiet
if ($LASTEXITCODE -ne 0) { throw 'Compose settings are invalid. Existing secrets have not been changed.' }
& docker compose --env-file $settingsFile -f $composeFile up -d --build
if ($LASTEXITCODE -ne 0) { throw 'Startup failed. Inspect docker compose logs; do not delete the volumes.' }
Write-Host 'Waiting for the backend and demo accounts...'
$demoLogin = @{}
Get-Content -LiteralPath $settingsFile | ForEach-Object {
    if ($_ -match '^LOCAL_DEMO_PASSWORD=(.*)$') { $demoLogin.password = $Matches[1] }
}
for ($attempt = 0; $attempt -lt 90; $attempt++) {
    try {
        # HTTP 포트가 열렸어도 ApplicationRunner의 시드 트랜잭션은 아직 끝나지 않았을 수 있다.
        foreach ($username in @('soonja', 'minji', 'demo_admin')) {
            $demoLogin.username = $username
            $login = Invoke-RestMethod 'http://localhost:8080/auth/login' -Method Post -ContentType 'application/json' -Body ($demoLogin | ConvertTo-Json -Compress) -TimeoutSec 3
            if (-not $login.data.accessToken) { throw 'Demo accounts are not ready.' }
        }
        $status = Invoke-RestMethod 'http://localhost:8080/local-demo/status' -Headers @{Authorization = "Bearer $($login.data.accessToken)"} -TimeoutSec 3
        if ($status.data.localDemo) {
            Write-Host 'Open http://localhost:8080/local-demo'
            Write-Host 'Accounts: soonja (caretaker), minji (family), demo_admin (weather controls).'
            Write-Host 'Password: LOCAL_DEMO_PASSWORD in .env.local-demo. Do not paste secrets into chat.'
            return
        }
    } catch { }
    Start-Sleep -Seconds 2
}
throw 'The backend is not ready. Inspect docker compose logs backend; preserve existing settings and volumes.'
