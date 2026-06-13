param(
    [string]$Version,
    [switch]$Release
)

$ErrorActionPreference = 'Stop'
$Root = Split-Path -Parent $MyInvocation.MyCommand.Path

# ── Version ──────────────────────────────────────────────────────────────────
# If not passed, read from build.ps1
if (!$Version) {
    $line = Get-Content (Join-Path $Root 'build.ps1') | Where-Object { $_ -match "^\`$ToolsVersion\s*=" } | Select-Object -First 1
    if ($line -match "'([^']+)'") { $Version = $Matches[1] }
}
if (!$Version) { throw "Could not determine version. Pass -Version x.y.z or set \$ToolsVersion in build.ps1." }

$Jar     = Join-Path $Root "colossuscraft-neoforge-1.21.1-$Version.jar"
$Notes   = Join-Path $Root 'build\release-notes.md'
$Tag     = "v$Version"

Write-Host "==> ColossusCraft $Tag" -ForegroundColor Cyan

# ── Build ─────────────────────────────────────────────────────────────────────
Write-Host "Building..." -ForegroundColor Cyan
& (Join-Path $Root 'build.ps1')
if ($LASTEXITCODE -ne 0) { throw "Build failed." }

if (!(Test-Path $Jar)) { throw "Expected jar not found: $Jar" }
Write-Host "Jar ready: $Jar" -ForegroundColor Green

# ── Release ───────────────────────────────────────────────────────────────────
if ($Release) {
    Write-Host "Releasing $Tag to GitHub..." -ForegroundColor Cyan

    # Push latest commits
    git push origin main
    if ($LASTEXITCODE -ne 0) { throw "git push failed." }

    # Tag (skip if already exists)
    $existingTag = git tag -l $Tag
    if ($existingTag) {
        Write-Host "Tag $Tag already exists — updating release asset only." -ForegroundColor Yellow
        try { gh release delete-asset $Tag "colossuscraft-neoforge-1.21.1-$Version.jar" --yes 2>$null } catch {}
        gh release upload $Tag $Jar
    } else {
        git tag $Tag
        git push origin $Tag

        $notesArg = if (Test-Path $Notes) { "--notes-file `"$Notes`"" } else { "--generate-notes" }
        $cmd = "gh release create $Tag `"$Jar`" --title `"ColossusCraft $Tag`" $notesArg"
        Invoke-Expression $cmd
    }

    if ($LASTEXITCODE -ne 0) { throw "GitHub release failed." }
    Write-Host "Released: https://github.com/Deaeath/ColossusCraft/releases/tag/$Tag" -ForegroundColor Green
} else {
    Write-Host "Skipping release (pass -Release to publish to GitHub)." -ForegroundColor Yellow
}

Write-Host "Done." -ForegroundColor Green
