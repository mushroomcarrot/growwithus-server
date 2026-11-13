$ErrorActionPreference = 'Stop'
$projectDir = [IO.Path]::GetFullPath((Join-Path $PSScriptRoot '..'))
$destination = Join-Path $projectDir '.env.cloud-ai'
$model = 'gemini-3.5-flash-lite'
Write-Host 'Use only a Gemini API key from a FREE project WITHOUT Cloud Billing.'
Write-Host 'This check sends one public text prompt. No recording or photo is uploaded.'
Write-Host 'The key is hidden. Do not paste it in chat or commit it.'
$secure = Read-Host 'Gemini API key' -AsSecureString
$pointer = [IntPtr]::Zero
try {
    $pointer = [Runtime.InteropServices.Marshal]::SecureStringToBSTR($secure)
    $apiKey = [Runtime.InteropServices.Marshal]::PtrToStringBSTR($pointer)
    if ([string]::IsNullOrWhiteSpace($apiKey) -or $apiKey -notmatch '^[A-Za-z0-9_-]{20,200}$') { throw 'Invalid key format.' }
    $body = @{contents=@(@{role='user'; parts=@(@{text='Return JSON with ok set to true.'})}); generationConfig=@{responseMimeType='application/json'; maxOutputTokens=1024; temperature=0}} | ConvertTo-Json -Depth 10 -Compress
    try {
        $reply = Invoke-RestMethod -Method Post -Uri "https://generativelanguage.googleapis.com/v1beta/models/${model}:generateContent" -Headers @{'x-goog-api-key'=$apiKey} -ContentType 'application/json' -Body $body -TimeoutSec 60
    } catch {
        throw 'Gemini request failed. Check the free project, model access, API key and quota in AI Studio. The key was not saved.'
    }
    if ($reply.candidates[0].finishReason -ne 'STOP') { throw 'The response was incomplete. The key was not saved.' }
    $json = ($reply.candidates[0].content.parts | Where-Object { -not $_.thought } | ForEach-Object { $_.text }) -join ''
    if (($json | ConvertFrom-Json).ok -ne $true) { throw 'Unexpected response. The key was not saved.' }
    $lines = @('AI_GEMINI_ENABLED=true', "GEMINI_API_KEY=$apiKey", "GEMINI_MODEL=$model", 'AI_LOCAL_ENABLED=false', 'AI_LOCAL_STT_ENABLED=false', 'AI_LOCAL_TTS_ENABLED=false')
    [IO.File]::WriteAllLines($destination, $lines, (New-Object Text.UTF8Encoding($false)))
    Write-Host 'Public text check passed. Private settings saved to .env.cloud-ai.'
    Write-Host 'Add these settings to the server private .env without replacing DB/JWT settings, then recreate the app.'
    Write-Host 'Speech, photo quality and deployed server memory still require verification.'
} catch { Write-Host $_.Exception.Message; exit 1 }
finally {
    if ($pointer -ne [IntPtr]::Zero) { [Runtime.InteropServices.Marshal]::ZeroFreeBSTR($pointer) }
    $apiKey = $null
    $secure.Dispose()
}
