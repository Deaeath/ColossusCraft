$ErrorActionPreference = "Stop"

$Root = Split-Path -Parent $MyInvocation.MyCommand.Path
$Prism = Join-Path $env:APPDATA "PrismLauncher"
$Java = Join-Path $Prism "java\java-runtime-delta\bin"
$Mods = Join-Path $Prism "instances\All the Mods 10 - ATM10\minecraft\mods"
$Original = Join-Path $Mods "baritone-standalone-neoforge-1.11.2.jar.disabled"
$Output = Join-Path $Root "baritone-neoforge-1.11.2-minefix.jar"
$Installed = Join-Path $Mods "baritone-neoforge-1.11.2-minefix.jar"
$Build = Join-Path $Root "build"
$Classes = Join-Path $Build "classes"
$Work = Join-Path $Build "work"
$TargetClass = "baritone/launch/mixins/MixinItemStack.class"

if (!(Test-Path $Original)) {
    throw "Missing original jar: $Original"
}

$Asm = Join-Path $Prism "libraries\org\ow2\asm\asm\9.9\asm-9.9.jar"
$AsmTree = Join-Path $Prism "libraries\org\ow2\asm\asm-tree\9.9\asm-tree-9.9.jar"
$Classpath = "$Asm;$AsmTree"

Remove-Item -Recurse -Force $Build -ErrorAction SilentlyContinue
New-Item -ItemType Directory -Force -Path $Classes, $Work | Out-Null

& (Join-Path $Java "javac.exe") -cp $Classpath -d $Classes (Join-Path $Root "src\Transform.java")
if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }

Copy-Item -Force $Original $Output
Push-Location $Work
& (Join-Path $Java "jar.exe") xf $Original $TargetClass
Pop-Location

$InputClass = Join-Path $Work $TargetClass.Replace('/', '\')
$OutputClass = Join-Path $Work "MixinItemStack.patched.class"
& (Join-Path $Java "java.exe") -cp "$Classes;$Classpath" Transform $InputClass $OutputClass
if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }

Copy-Item -Force $OutputClass $InputClass
Push-Location $Work
& (Join-Path $Java "jar.exe") uf $Output $TargetClass
Pop-Location

Copy-Item -Force $Output $Installed
Get-Item $Installed
