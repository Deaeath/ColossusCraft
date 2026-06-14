$ErrorActionPreference = 'Stop'

$ToolsVersion = '1.0.2'
$Root = Split-Path -Parent $MyInvocation.MyCommand.Path
$Prism = Join-Path $env:APPDATA 'PrismLauncher'

# Automatically choose Java path
$Java = Join-Path $Prism 'java\java-runtime-delta\bin'
if (!(Test-Path (Join-Path $Java 'javac.exe'))) {
    if (Test-Path $env:JAVA_HOME) { $Java = Join-Path $env:JAVA_HOME 'bin' }
    else { $Java = 'C:\Program Files\Java\jdk-17\bin' }
}

$Mods = Join-Path $Prism 'instances\All the Mods 10 - ATM10\minecraft\mods'
$Original = Join-Path $Root 'baritone-unoptimized-neoforge-1.11.2.jar'
$Installed = Join-Path $Mods 'baritone-neoforge-1.11.2-minefix.jar'
$ToolsOutput = Join-Path $Root "colossuscraft-neoforge-1.21.1-$ToolsVersion.jar"
$ToolsInstalled = Join-Path $Mods "colossuscraft-neoforge-1.21.1-$ToolsVersion.jar"
$Build = Join-Path $Root 'build'
$Output = Join-Path $Build 'colossuscraft-pathfinder.patched.jar'
$Classes = Join-Path $Build 'classes'
$ModClasses = Join-Path $Build 'modclasses'
$JarRoot = Join-Path $Build 'jarroot'
$Work = Join-Path $Build 'work'
$TargetClass = 'baritone/launch/mixins/MixinItemStack.class'

if (!(Test-Path $Original)) {
    throw "Missing original jar: $Original"
}

$Asm = Join-Path $Prism 'libraries\org\ow2\asm\asm\9.9\asm-9.9.jar' 
$AsmTree = Join-Path $Prism 'libraries\org\ow2\asm\asm-tree\9.9\asm-tree-9.9.jar'
$Classpath = "$Asm;$AsmTree"

# Clean only ephemeral build dirs. Preserve build\moddev (NeoForge moddev MojMap artifacts).
foreach ($d in @($Classes, $ModClasses, $JarRoot, $Work, (Join-Path $Build 'libs'))) {
    if (Test-Path $d) { Remove-Item -Recurse -Force $d -ErrorAction SilentlyContinue }
}
New-Item -ItemType Directory -Force -Path $Classes, $ModClasses, $JarRoot, $Work, (Join-Path $Build 'libs') | Out-Null

& (Join-Path $Java 'javac.exe') -cp $Classpath -d $Classes (Join-Path $Root 'src\Transform.java')
if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }

Copy-Item -Force $Original $Output
Push-Location $Work
& (Join-Path $Java 'jar.exe') xf $Original $TargetClass
Pop-Location

$InputClass = Join-Path $Work $TargetClass.Replace('/', '\')
$OutputClass = Join-Path $Work 'MixinItemStack.patched.class'
& (Join-Path $Java 'java.exe') -cp "$Classes;$Classpath" Transform $InputClass $OutputClass
if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }

Copy-Item -Force $OutputClass $InputClass
Push-Location $Work
& (Join-Path $Java 'jar.exe') uf $Output $TargetClass
Pop-Location

# Compile against the MojMap (un-obfuscated) Minecraft + NeoForge classes that the NeoForge
# moddev gradle plugin decompiles. Source is written in MojMap names, so the SRG/intermediary
# Minecraft jars must NOT be on the classpath. Run `gradle :createMinecraftArtifacts` once to
# generate this merged jar if it is missing.
Write-Host 'Locating MojMap Minecraft + NeoForge (moddev merged jar) and mod dependencies...' -ForegroundColor Cyan
$Merged = Join-Path $Root 'build\moddev\artifacts\neoforge-21.1.228-merged.jar'
if (!(Test-Path $Merged)) {
    throw "Missing MojMap merged jar: $Merged`nRun NeoForge moddev first, e.g.:`n  gradle compileJava   (or)   gradle :createMinecraftArtifacts"
}
$LibPaths = @($Merged)

# Un-obfuscated Baritone supplies the real baritone.api for the faithful port. Proguarded ATM10
# baritone jars must be kept OFF the classpath or they shadow the real API with stripped names.
if (Test-Path $Original) { $LibPaths += $Original }

# Third-party libraries from Prism (brigadier, gson, slf4j, joml, fastutil, guava...).
# Exclude net/minecraft (provided by the merged jar) and -sources artifacts.
$PrismLibs = Join-Path $Prism 'libraries'
Get-ChildItem $PrismLibs -Recurse -Filter '*.jar' -ErrorAction SilentlyContinue | Where-Object {
    $_.FullName -notmatch '\\net\\minecraft\\' -and $_.Name -notmatch '\-sources\.jar$'
} | ForEach-Object { $LibPaths += $_.FullName }

# Mod jars from the ATM10 instance (architectury, ftbquests, ...). Exclude our own build outputs
# and every baritone jar (real API comes from the un-obfuscated $Original above).
Get-ChildItem $Mods -Include '*.jar', '*.jar.disabled' -Recurse | Where-Object {
    $_.Name -notmatch 'baritone' -and $_.Name -notmatch 'pveguard' -and $_.Name -notmatch 'colossuscraft'
} | ForEach-Object { $LibPaths += $_.FullName }

# Build parameters for the argument text container mapping using javac's line-by-line quote rule
$ArgFile = Join-Path $Build 'javac_args.txt'
$Sources = Get-ChildItem -Path (Join-Path $Root 'src\main\java') -Recurse -Filter '*.java' | ForEach-Object { $_.FullName }

# Convert paths to forward slashes to protect backslashes from being consumed as escape keys
$CleanLibPaths = $LibPaths | ForEach-Object { $_.Replace('\', '/') }
$ModClasspathString = $CleanLibPaths -join ';'

$ArgFileContent = @()
$ArgFileContent += '-classpath'
$ArgFileContent += "`"$ModClasspathString`""
$ArgFileContent += '-d'
$ArgFileContent += "`"$($ModClasses.Replace('\', '/'))`""
$ArgFileContent += '-proc:none'
$ArgFileContent += '-encoding'
$ArgFileContent += 'UTF-8'
foreach ($src in $Sources) {
    $ArgFileContent += "`"$($src.Replace('\', '/'))`""
}

# Write a clean, raw file using .NET Framework configuration (No BOM signature)
[System.IO.File]::WriteAllLines($ArgFile, $ArgFileContent)

# Execute javac using the @argfile flag wrapped safely in quotes for PowerShell
& (Join-Path $Java 'javac.exe') '-J-Xmx512m' "@$ArgFile"
if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }

Copy-Item -Recurse -Force (Join-Path $ModClasses '*') $JarRoot
Copy-Item -Recurse -Force (Join-Path $Root 'src\main\resources\*') $JarRoot

Add-Type -AssemblyName System.IO.Compression.FileSystem

# Bundle the patched Baritone jar into the tools jar so Prism only needs one active local mod jar.
# Keep our combined neoforge.mods.toml and manifest; copy Baritone classes/resources/mixins.
Write-Host 'Merging patched Baritone into the tools jar...' -ForegroundColor Cyan
$baritoneZip = [System.IO.Compression.ZipFile]::OpenRead($Output)
try {
    foreach ($entry in $baritoneZip.Entries) {
        if ($entry.Name -eq '') { continue }
        if ($entry.FullName.StartsWith('META-INF/')) { continue }
        if ($entry.FullName -eq 'pack.mcmeta') { continue }
        $dest = Join-Path $JarRoot ($entry.FullName -replace '/', '\')
        $destDir = Split-Path -Parent $dest
        if (-not (Test-Path $destDir)) { New-Item -ItemType Directory -Force -Path $destDir | Out-Null }
        [System.IO.Compression.ZipFileExtensions]::ExtractToFile($entry, $dest, $true)
    }
} finally {
    $baritoneZip.Dispose()
}

# Shade Jackson into the mod jar. NeoForge isolates each mod as its own module, so libraries
# that are not provided by the pack must be bundled here or the mod fails at construction with
# NoClassDefFoundError: com/fasterxml/jackson/... Only the base com/fasterxml/** classes are
# extracted (no META-INF/manifest/module-info) to avoid clobbering our jar metadata.
Write-Host 'Shading Jackson (databind/core/annotations) into the mod jar...' -ForegroundColor Cyan
$Modules2 = Join-Path $env:USERPROFILE '.gradle\caches\modules-2\files-2.1\com.fasterxml.jackson.core'
foreach ($pkg in @('jackson-databind', 'jackson-core', 'jackson-annotations')) {
    $jjar = Get-ChildItem (Join-Path $Modules2 "$pkg\2.17.1") -Recurse -Filter "$pkg-2.17.1.jar" -ErrorAction SilentlyContinue | Select-Object -First 1
    if (-not $jjar) { throw "Missing Jackson jar to shade: $pkg-2.17.1.jar under $Modules2" }
    $zip = [System.IO.Compression.ZipFile]::OpenRead($jjar.FullName)
    try {
        foreach ($entry in $zip.Entries) {
            if ($entry.FullName.StartsWith('com/fasterxml/') -and $entry.Name -ne '') {
                $dest = Join-Path $JarRoot ($entry.FullName -replace '/', '\')
                $destDir = Split-Path -Parent $dest
                if (-not (Test-Path $destDir)) { New-Item -ItemType Directory -Force -Path $destDir | Out-Null }
                [System.IO.Compression.ZipFileExtensions]::ExtractToFile($entry, $dest, $true)
            }
        }
    } finally { $zip.Dispose() }
}

Remove-Item -Force $ToolsOutput -ErrorAction SilentlyContinue
$Manifest = Join-Path $Build 'MANIFEST.MF'
[System.IO.File]::WriteAllLines($Manifest, @(
    'Manifest-Version: 1.0',
    'MixinConfigs: mixins.baritone.json',
    'MixinConnector: baritone.launch.BaritoneMixinConnector',
    'Implementation-Title: ColossusCraft',
    "Implementation-Version: $ToolsVersion",
    ''
))
Push-Location $JarRoot
& (Join-Path $Java 'jar.exe') --create --file $ToolsOutput --manifest $Manifest -C . .
Pop-Location

Remove-Item -Force $Installed -ErrorAction SilentlyContinue
Get-ChildItem $Mods -Filter 'baritone-autoeat-neoforge-1.21.1-*.jar' | Remove-Item -Force
Get-ChildItem $Mods -Filter 'baritone-minefix-tools-neoforge-1.21.1-*.jar' | Remove-Item -Force
Get-ChildItem $Mods -Filter 'colossuscraft-neoforge-1.21.1-*.jar' | Remove-Item -Force
Get-ChildItem $Mods -Filter 'pveguard-neoforge-1.21.1-*.jar' | Remove-Item -Force
Copy-Item -Force $ToolsOutput $ToolsInstalled

Get-Item $ToolsInstalled
