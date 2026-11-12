$ErrorActionPreference = 'Stop'
$taskRepo = (Resolve-Path -LiteralPath (Join-Path $PSScriptRoot '..')).Path
$taskMain = Join-Path $taskRepo 'src\main\java\community\likelion\growwithusserver\ai\logic'
$taskTest = Join-Path $taskRepo 'src\test\java\community\likelion\growwithusserver\ai\logic\AiLogicSelfTest.java'
$taskClasses = Join-Path $taskRepo 'build\ai-selftest'

if ($env:JAVA_HOME -and (Test-Path -LiteralPath (Join-Path $env:JAVA_HOME 'bin\javac.exe'))) {
    $taskJavac = Join-Path $env:JAVA_HOME 'bin\javac.exe'
    $taskJava = Join-Path $env:JAVA_HOME 'bin\java.exe'
} else {
    $taskJavac = (Get-Command javac -ErrorAction Stop).Source
    $taskJava = Join-Path (Split-Path $taskJavac) 'java.exe'
}

New-Item -ItemType Directory -Path $taskClasses -Force | Out-Null
$taskSources = @(Get-ChildItem -LiteralPath $taskMain -Filter '*.java' -File | Select-Object -ExpandProperty FullName)
$taskSources += $taskTest
& $taskJavac -J-XX:-UsePerfData -encoding UTF-8 --release 21 -d $taskClasses $taskSources
if ($LASTEXITCODE -ne 0) { throw 'AI core compilation failed. Use JDK 21 or later.' }
& $taskJava -XX:-UsePerfData -cp $taskClasses community.likelion.growwithusserver.ai.logic.AiLogicSelfTest
if ($LASTEXITCODE -ne 0) { throw 'AI core checks failed.' }
