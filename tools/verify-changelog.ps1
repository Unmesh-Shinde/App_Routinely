param(
    [string]$BaseRef = "HEAD"
)

$ErrorActionPreference = "Stop"

if (-not (Test-Path "CHANGELOG.md")) {
    throw "CHANGELOG.md is required."
}

$changedFiles = @(git diff --name-only $BaseRef --)
$changedFiles += @(git diff --cached --name-only --)
$changedFiles += @(git status --short | ForEach-Object {
    if ($_.Length -gt 3) { $_.Substring(3) }
})
$changedFiles = @($changedFiles | Sort-Object -Unique)
$codeChangePatterns = @(
    '\.(kt|java|xml|gradle|kts|properties|json|ts|tsx|js|jsx|css|html|md|yml|yaml)$',
    '(^|/)(AndroidManifest\.xml|Dockerfile|Makefile)$'
)
$hasProjectChange = $false
foreach ($file in $changedFiles) {
    if ($file -eq "CHANGELOG.md") {
        continue
    }
    if ($codeChangePatterns | Where-Object { $file -match $_ }) {
        $hasProjectChange = $true
        break
    }
}

if (-not $hasProjectChange) {
    Write-Host "No tracked source or project-configuration changes detected. Changelog check passed."
    exit 0
}

if ($changedFiles -notcontains "CHANGELOG.md") {
    throw "Source or project configuration changed without updating CHANGELOG.md."
}

$changelog = Get-Content "CHANGELOG.md" -Raw
if ($changelog -notmatch '(?m)^##\s+\d{4}-\d{2}-\d{2}' -or
    $changelog -notmatch '(?mi)^-\s+\*\*By:\*\*') {
    throw "CHANGELOG.md must add a dated entry with a By: identity."
}

Write-Host "Changelog check passed."
