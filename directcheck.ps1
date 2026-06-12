$ErrorActionPreference = 'Stop'
$Root = 'C:\baritone-minefix'
$Java = Join-Path $env:APPDATA 'PrismLauncher\java\java-runtime-delta\bin'
$Mods = 'C:\Users\Power User\AppData\Roaming\PrismLauncher\instances\All the Mods 10 - ATM10\minecraft\mods'
$Merged = Join-Path $Root 'build\moddev\artifacts\neoforge-21.1.228-merged.jar'
$Caches = Join-Path $env:USERPROFILE '.gradle\caches\modules-2'

$cp = @($Merged)
# Compile against the UN-OBFUSCATED Baritone so the faithful port can use the real baritone.api
# (IBaritone, GoalBlock, ICustomGoalProcess, IExploreProcess, Settings, Input, ...). The ATM10
# baritone jars are proguarded and must NOT be on the classpath or they shadow the real API.
$Unopt = Join-Path $Root 'baritone-unoptimized-neoforge-1.11.2.jar'
if (Test-Path $Unopt) { $cp += $Unopt }
# mod jars, excluding our own outputs and every baritone jar (real API comes from $Unopt above)
Get-ChildItem $Mods -Include '*.jar','*.jar.disabled' -Recurse | Where-Object {
    $_.Name -notmatch 'baritone' -and $_.Name -notmatch 'pveguard' -and $_.Name -notmatch 'colossuscraft'
} | ForEach-Object { $cp += $_.FullName }
# third-party libraries from Prism (brigadier, gson, slf4j, joml, fastutil, guava...) excluding mc/neoforge (have merged)
$PrismLibs = Join-Path $env:APPDATA 'PrismLauncher\libraries'
Get-ChildItem $PrismLibs -Recurse -Filter '*.jar' -ErrorAction SilentlyContinue | Where-Object {
    $_.FullName -notmatch '\\net\\minecraft\\' -and $_.Name -notmatch '\-sources\.jar$'
} | ForEach-Object { $cp += $_.FullName }
# jackson + mixinextras from gradle caches
Get-ChildItem $Caches -Recurse -Filter '*.jar' -ErrorAction SilentlyContinue | Where-Object {
    $_.Name -match '^jackson-(databind|annotations|core)-[0-9].*\.jar$' -or $_.Name -match '^mixinextras-neoforge-0.5.3\.jar$'
} | ForEach-Object { $cp += $_.FullName }

$cpStr = ($cp | ForEach-Object { $_.Replace('\','/') }) -join ';'

$Out = Join-Path $Root 'build\directcheck'
Remove-Item -Recurse -Force $Out -ErrorAction SilentlyContinue
New-Item -ItemType Directory -Force -Path $Out | Out-Null

$ArgFile = Join-Path $Root 'build\directcheck_args.txt'
$lines = @('-classpath', "`"$cpStr`"", '-d', "`"$($Out.Replace('\','/'))`"", '-encoding', 'UTF-8', '-nowarn', '-proc:none', '-Xmaxerrs', '4000')
Get-ChildItem -Path (Join-Path $Root 'src\main\java') -Recurse -Filter '*.java' | ForEach-Object {
    $lines += "`"$($_.FullName.Replace('\','/'))`""
}
[System.IO.File]::WriteAllLines($ArgFile, $lines)

Write-Host "CP entries: $($cp.Count); sources: $((Get-ChildItem (Join-Path $Root 'src\main\java') -Recurse -Filter *.java).Count)"
$JavacLog = Join-Path $Root 'build\javac_errors.log'
$ErrorActionPreference = 'Continue'
& (Join-Path $Java 'javac.exe') '-J-Xmx700m' '-J-Xms64m' '-J-XX:+UseSerialGC' "@$ArgFile" 2>&1 | ForEach-Object { $_.ToString() } | Out-File -FilePath $JavacLog -Encoding utf8
$ec = $LASTEXITCODE
Write-Host "JAVAC_EXIT=$ec"
Write-Host "ERROR_LINES=$((Get-Content $JavacLog | Select-String ' error:').Count)"
