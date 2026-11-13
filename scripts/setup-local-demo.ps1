param([switch]$CheckOnly)
$ErrorActionPreference = 'Stop'
$projectDir = [System.IO.Path]::GetFullPath((Join-Path $PSScriptRoot '..'))
$installerDir = Join-Path $projectDir 'build\setup'
$installerFile = Join-Path $installerDir 'DockerDesktopInstaller.exe'
$installerDownload = Join-Path $installerDir 'DockerDesktopInstaller.partial.exe'
$installerUrl = 'https://desktop.docker.com/win/main/amd64/Docker%20Desktop%20Installer.exe'
$dockerHomes = @("$env:LOCALAPPDATA\Programs\DockerDesktop", 'C:\Program Files\Docker\Docker')

function Find-DockerCli {
    $command = Get-Command docker -ErrorAction SilentlyContinue
    if ($command) { return $command.Source }
    foreach ($directory in $dockerHomes) {
        $candidate = Join-Path $directory 'resources\bin\docker.exe'
        if (Test-Path -LiteralPath $candidate) { return $candidate }
    }
    return $null
}

function Assert-DockerInstaller([string]$Path) {
    $signature = Get-AuthenticodeSignature -LiteralPath $Path
    if ($signature.Status -ne 'Valid' -or -not $signature.SignerCertificate) { throw 'The Docker installer signature is invalid. Nothing was installed.' }
    $publisher = $signature.SignerCertificate.GetNameInfo([System.Security.Cryptography.X509Certificates.X509NameType]::SimpleName, $false)
    if ($publisher -notin @('Docker Inc', 'Docker, Inc', 'Docker, Inc.')) { throw 'Unexpected installer publisher. Nothing was installed.' }
}

function Publish-FeatureBranch {
    if (-not (Test-Path -LiteralPath (Join-Path $projectDir '.git'))) { Write-Host 'Source ZIP detected. Git upload skipped.'; return }
    if (-not (Get-Command git -ErrorAction SilentlyContinue)) { Write-Host 'Git not found. Local setup will continue.'; return }
    $branch = & git -C $projectDir branch --show-current
    if ($LASTEXITCODE -ne 0 -or $branch -ne 'feat/seungyoon-ai') { Write-Host 'Feature branch is not selected. Git upload skipped.'; return }
    $remote = & git -C $projectDir remote get-url origin
    if ($LASTEXITCODE -ne 0 -or $remote -notmatch '^https://github\.com/mushroomcarrot/growwithus-server(?:\.git)?/?$') { Write-Host 'Unexpected Git remote. Git upload skipped.'; return }
    $changes = & git -C $projectDir status --porcelain
    if ($LASTEXITCODE -ne 0 -or $changes) { Write-Host 'Uncommitted changes found. Git upload skipped; local setup will continue.'; return }
    try {
        & git -C $projectDir push -u origin feat/seungyoon-ai
        if ($LASTEXITCODE -ne 0) { Write-Host 'Git upload failed. Local setup will continue. No branch was merged.' }
    } catch { Write-Host 'Git upload could not finish. Local setup will continue. No branch was merged.' }
}

function Test-DockerReady {
    try {
        & $dockerCli info --format '{{.ServerVersion}}' 2>$null | Out-Null
        return ($LASTEXITCODE -eq 0)
    } catch { return $false }
}

$dockerCli = Find-DockerCli
if ($CheckOnly) {
    Write-Host "Project: $projectDir"
    Write-Host "Docker CLI available: $([bool]$dockerCli)"
    Write-Host 'Check only: no downloads, installations, Git writes, or services started.'
    return
}

Publish-FeatureBranch
if (-not $dockerCli) {
    Write-Host 'Downloading the official Docker Desktop installer for per-user installation.'
    New-Item -ItemType Directory -Path $installerDir -Force | Out-Null
    if (-not (Test-Path -LiteralPath $installerFile)) {
        Invoke-WebRequest -Uri $installerUrl -OutFile $installerDownload -UseBasicParsing -TimeoutSec 900
        Assert-DockerInstaller $installerDownload
        Move-Item -LiteralPath $installerDownload -Destination $installerFile
    }
    Assert-DockerInstaller $installerFile
    Write-Host 'Installing Docker Desktop for this Windows user. WSL setup or a restart may still be required.'
    $install = Start-Process -FilePath $installerFile -ArgumentList @('install', '--user', '--backend=wsl-2', '--quiet', '--accept-license') -WindowStyle Hidden -PassThru -Wait
    if ($install.ExitCode -ne 0) { throw "Docker installation stopped with exit code $($install.ExitCode). See docs/local-demo.md. No restart was requested by this script." }
    $dockerCli = Find-DockerCli
    if (-not $dockerCli) { throw 'Docker CLI was not found after installation. Check the installer result before retrying.' }
}

# PATH changes are limited to this PowerShell process after a new installation.
$env:PATH = (Split-Path $dockerCli) + ';' + $env:PATH
$ready = Test-DockerReady
if (-not $ready) {
    $desktop = $dockerHomes | ForEach-Object { Join-Path $_ 'Docker Desktop.exe' } | Where-Object { Test-Path -LiteralPath $_ } | Select-Object -First 1
    if (-not $desktop) { throw 'Start your Docker engine and run this file again. Existing Docker settings were preserved.' }
    Start-Process -FilePath $desktop -WindowStyle Hidden
    Write-Host 'Waiting for Docker. If Windows shows a WSL or permission prompt, complete it there. This script does not restart Windows.'
    for ($attempt = 0; $attempt -lt 90; $attempt++) {
        if (Test-DockerReady) { $ready = $true; break }
        Start-Sleep -Seconds 2
    }
}
if (-not $ready) { throw 'Docker is not ready. Complete WSL setup or any requested restart, then run RUN-LOCAL-DEMO.cmd again.' }

& (Join-Path $PSScriptRoot 'start-local-demo.ps1')
$python = Get-Command python -ErrorAction SilentlyContinue
if ($python) {
    & $python.Source (Join-Path $PSScriptRoot 'smoke_local_demo.py')
    if ($LASTEXITCODE -ne 0) { throw 'Backend smoke check failed. Keep existing settings and volumes, and inspect the error above.' }
}
Start-Process 'http://localhost:8080/local-demo' -WindowStyle Hidden
Write-Host 'Local demo is ready. Use the private LOCAL_DEMO_PASSWORD in .env.local-demo. Real voice and model quality still require verification.'
