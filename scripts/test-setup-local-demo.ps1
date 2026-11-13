$ErrorActionPreference = 'Stop'
$projectDir = [System.IO.Path]::GetFullPath((Join-Path $PSScriptRoot '..'))
$tokens = $null; $parseErrors = $null
$ast = [System.Management.Automation.Language.Parser]::ParseFile((Join-Path $PSScriptRoot 'setup-local-demo.ps1'), [ref]$tokens, [ref]$parseErrors)
if ($parseErrors.Count) { throw 'Setup script syntax is invalid.' }
# 설치 본문을 실행하지 않고 검사할 함수만 현재 테스트 프로세스에 읽는다.
$functions = $ast.FindAll({ param($node) $node -is [System.Management.Automation.Language.FunctionDefinitionAst] }, $false)
foreach ($function in $functions) { Invoke-Expression $function.Extent.Text }

$taskPublisher = 'Docker Inc'; $taskSignature = 'Valid'; $taskCertificateMissing = $false
function Get-AuthenticodeSignature {
    param([string]$LiteralPath)
    $certificate = New-Object PSObject
    $certificate | Add-Member -MemberType ScriptMethod -Name GetNameInfo -Value { param($type, $issuer) return $taskPublisher }
    return [PSCustomObject]@{Status=$taskSignature; SignerCertificate=$(if ($taskCertificateMissing) { $null } else { $certificate })}
}
function Must-Reject([scriptblock]$Action) {
    $failed = $false
    try { & $Action } catch { $failed = $true }
    if (-not $failed) { throw 'Expected the unsafe input to be rejected.' }
}
Assert-DockerInstaller 'fixture.exe'
$taskSignature = 'NotSigned'; Must-Reject { Assert-DockerInstaller 'fixture.exe' }
$taskSignature = 'Valid'; $taskPublisher = 'Unrelated publisher'; Must-Reject { Assert-DockerInstaller 'fixture.exe' }
$taskPublisher = 'Docker Inc'; $taskCertificateMissing = $true; Must-Reject { Assert-DockerInstaller 'fixture.exe' }

$taskFakeBranch = 'feat/seungyoon-ai'; $taskFakeRemote = 'https://github.com/mushroomcarrot/growwithus-server.git'
$taskFakeChanges = $null; $taskPushes = @(); $taskPushFails = $false
function git {
    $global:LASTEXITCODE = 0
    if ($args -contains 'branch') { return $taskFakeBranch }
    if ($args -contains 'remote') { return $taskFakeRemote }
    if ($args -contains 'status') { return $taskFakeChanges }
    if ($args -contains 'push') {
        if ($args[-1] -ne 'feat/seungyoon-ai') { throw 'Unexpected target branch.' }
        $script:taskPushes += 'feat/seungyoon-ai'
        if ($taskPushFails) { throw 'fixture network failure' }
        return
    }
    throw 'Unexpected git operation.'
}
$taskFakeBranch = 'main'; Publish-FeatureBranch
if ($taskPushes.Count -ne 0) { throw 'main must never be pushed.' }
$taskFakeBranch = 'feat/seungyoon-ai'; $taskFakeRemote = 'https://github.com/example/wrong.git'; Publish-FeatureBranch
if ($taskPushes.Count -ne 0) { throw 'Unexpected remote must never be pushed.' }
$taskFakeRemote = 'https://github.com/mushroomcarrot/growwithus-server.git'; $taskFakeChanges = ' M fixture'; Publish-FeatureBranch
if ($taskPushes.Count -ne 0) { throw 'Dirty checkout must not be uploaded automatically.' }
$taskFakeChanges = $null; Publish-FeatureBranch
if ($taskPushes.Count -ne 1) { throw 'Clean feature branch should be uploaded once.' }
$taskPushFails = $true; Publish-FeatureBranch
if ($taskPushes.Count -ne 2) { throw 'Failed upload should leave local setup available.' }
$dockerCli = { $global:LASTEXITCODE = 0; return 'fixture engine' }
if (-not (Test-DockerReady)) { throw 'A ready engine should be detected.' }
$dockerCli = { throw 'fixture engine unavailable' }
if (Test-DockerReady) { throw 'An unavailable engine should not stop the readiness retry loop.' }
Write-Host 'Setup signature and Git guard checks passed. No installers or Git requests were executed.'
