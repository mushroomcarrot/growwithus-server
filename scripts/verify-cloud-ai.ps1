param([switch]$CheckOnly, [switch]$TestsOnly)
$ErrorActionPreference = 'Stop'
$projectDir = [IO.Path]::GetFullPath((Join-Path $PSScriptRoot '..'))
$compose = Join-Path $projectDir 'compose.cloud-verification.json'
$reports = Join-Path $projectDir 'build/reports'
New-Item -ItemType Directory -Path $reports -Force | Out-Null
$log = Join-Path $reports 'cloud-verification.log'
$docker = Get-Command docker.exe -ErrorAction SilentlyContinue
if ($docker) { $dockerPath=$docker.Source } else { $dockerPath=Join-Path $env:USERPROFILE 'AppData/Local/Programs/DockerDesktop/resources/bin/docker.exe' }
if (-not (Test-Path -LiteralPath $dockerPath)) { $dockerPath=Join-Path $env:ProgramFiles 'Docker/Docker/resources/bin/docker.exe' }
if (-not (Test-Path -LiteralPath $dockerPath)) { throw 'Docker CLI not found.' }
$null = Get-Content -LiteralPath $compose -Raw -Encoding UTF8 | ConvertFrom-Json
if ($CheckOnly) { Write-Host 'Cloud verification configuration and Docker CLI checks passed. No credentials read and no network calls made.'; return }
if (-not (Test-Path -LiteralPath (Join-Path $projectDir '.env.local-demo'))) { throw 'Existing local test database settings are required.' }
if (-not $TestsOnly -and -not (Test-Path -LiteralPath (Join-Path $projectDir '.env.cloud-ai'))) { throw 'Run scripts/SETUP-CLOUD-AI.cmd first. Do not paste a key in chat.' }
function Invoke-Docker {
    $ErrorActionPreference = 'Continue'
    & $dockerPath @args 2>&1 | Tee-Object -FilePath $log -Append
    $code = $LASTEXITCODE
    $ErrorActionPreference = 'Stop'
    if ($code -ne 0) { throw 'Docker verification failed. No automatic retries were made. Check build/reports/cloud-verification.log.' }
}
$started=[DateTime]::UtcNow
Set-Content -LiteralPath $log -Encoding UTF8 -Value 'Cloud verification: ordinary tests use fake keys. Live checks use only project synthetic audio and an attributed public tomato photo.'
$common=@('compose','--project-directory',$projectDir,'--env-file',(Join-Path $projectDir '.env.local-demo'))
Invoke-Docker @common -f (Join-Path $projectDir 'compose.local-demo.json') up -d db redis
Invoke-Docker @common -f $compose run --rm --build --no-deps ai-evaluation ./gradlew --no-daemon test
$count=0
$xmlReports=@(Get-ChildItem -LiteralPath (Join-Path $reports 'junit') -Filter 'TEST-*.xml' | Where-Object { $_.LastWriteTimeUtc -ge $started.AddSeconds(-2) })
foreach ($file in $xmlReports) {
    $xml=[xml](Get-Content -LiteralPath $file.FullName -Raw -Encoding UTF8)
    if ([int]$xml.testsuite.failures -ne 0 -or [int]$xml.testsuite.errors -ne 0) { throw 'A unit or database test failed.' }
    $count += [int]$xml.testsuite.tests - [int]$xml.testsuite.skipped
}
if ($count -lt 303) { throw 'Expected at least 303 fresh successful tests.' }
$native=$xmlReports | Where-Object { $_.Name -eq 'TEST-community.likelion.growwithusserver.common.ai.NativeAudioTest.xml' }
if (-not $native) { throw 'Native decoder and Korean TTS tests did not run.' }
$nativeXml=[xml](Get-Content -LiteralPath $native.FullName -Raw -Encoding UTF8)
if ([int]$nativeXml.testsuite.tests -ne 3 -or [int]$nativeXml.testsuite.skipped -ne 0) { throw 'Native audio checks must run, not skip.' }
Write-Host "Passed $count ordinary tests, including real native audio conversion and MP3 decoding."
if ($TestsOnly) { return }
$fixtures=Join-Path $reports 'ai-fixtures'
New-Item -ItemType Directory -Path $fixtures -Force | Out-Null
$photo=Join-Path $fixtures 'tomato.jpg'
if (-not (Test-Path -LiteralPath $photo)) {
    Write-Host 'Downloading the attributed public tomato photo. No user data is sent.'
    Invoke-WebRequest -UseBasicParsing -Uri 'https://upload.wikimedia.org/wikipedia/commons/8/89/Tomato_je.jpg' -OutFile $photo -TimeoutSec 45
}
if ((Get-FileHash -LiteralPath $photo -Algorithm SHA1).Hash.ToLowerInvariant() -ne 'b31a3f11a711cf86c8fcafbb4eb243c9f5c37932') { throw 'Public photo checksum mismatch. No live AI calls were made.' }
Write-Host 'Starting live Gemini checks: up to 9 public-fixture requests, no retries or paid fallback. Existing database records are not changed by these live checks.'
$liveStarted=[DateTime]::UtcNow
Invoke-Docker @common -f $compose run --rm --build --no-deps live-ai ./gradlew --no-daemon verifyCloudAi
$livePath=Join-Path $reports 'cloud-ai-predeploy.json'
if (-not (Test-Path -LiteralPath $livePath) -or (Get-Item -LiteralPath $livePath).LastWriteTimeUtc -lt $liveStarted.AddSeconds(-2)) { throw 'Fresh cloud AI report was not generated.' }
$live=Get-Content -LiteralPath $livePath -Raw -Encoding UTF8 | ConvertFrom-Json
if ($live.status -ne 'passed' -or $live.provider -ne 'gemini') { throw 'Live checks did not all pass. Review cloud-ai-predeploy.json.' }
$head=& git "--git-dir=$projectDir/.git" "--work-tree=$projectDir" rev-parse HEAD
if ($LASTEXITCODE -ne 0) { throw 'Could not identify the verified commit.' }
@{head=$head; ordinaryTests=$count; liveStatus='passed'; model=$live.model; checkedAt=[DateTime]::UtcNow.ToString('o'); productionValidated=$false; humanVoiceValidated=$false; ttsListeningValidated=$false} | ConvertTo-Json | Set-Content -LiteralPath (Join-Path $reports 'cloud-live-receipt.json') -Encoding UTF8
Write-Host 'Synthetic voice, summary, confirmed memo handoff, growth, photo and TTS checks passed.'
Write-Host 'Review build/reports/tts-review.mp3 by listening. Production HTTP and real human speech remain unverified.'
