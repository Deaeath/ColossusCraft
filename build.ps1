$ErrorActionPreference = "Stop"

$ToolsVersion = "1.3.4"
$Root = Split-Path -Parent $MyInvocation.MyCommand.Path
$Prism = Join-Path $env:APPDATA "PrismLauncher"
$Java = Join-Path $Prism "java\java-runtime-delta\bin"
$Mods = Join-Path $Prism "instances\All the Mods 10 - ATM10\minecraft\mods"
$Original = Join-Path $Mods "baritone-standalone-neoforge-1.11.2.jar.disabled"
$Output = Join-Path $Root "baritone-neoforge-1.11.2-minefix.jar"
$Installed = Join-Path $Mods "baritone-neoforge-1.11.2-minefix.jar"
$ToolsOutput = Join-Path $Root "baritone-minefix-tools-neoforge-1.21.1-$ToolsVersion.jar"
$ToolsInstalled = Join-Path $Mods "baritone-minefix-tools-neoforge-1.21.1-$ToolsVersion.jar"
$Build = Join-Path $Root "build"
$Classes = Join-Path $Build "classes"
$ModClasses = Join-Path $Build "modclasses"
$JarRoot = Join-Path $Build "jarroot"
$Work = Join-Path $Build "work"
$TargetClass = "baritone/launch/mixins/MixinItemStack.class"

function FirstModJar($pattern) {
    $match = Get-ChildItem $Mods -Filter $pattern | Sort-Object LastWriteTime -Descending | Select-Object -First 1
    if ($null -eq $match) {
        throw "Missing mod jar: $pattern"
    }
    return $match.FullName
}

if (!(Test-Path $Original)) {
    throw "Missing original jar: $Original"
}

$Asm = Join-Path $Prism "libraries\org\ow2\asm\asm\9.9\asm-9.9.jar"
$AsmTree = Join-Path $Prism "libraries\org\ow2\asm\asm-tree\9.9\asm-tree-9.9.jar"
$Classpath = "$Asm;$AsmTree"

Remove-Item -Recurse -Force $Build -ErrorAction SilentlyContinue
New-Item -ItemType Directory -Force -Path $Classes, $ModClasses, $JarRoot, $Work | Out-Null

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

$ModClasspath = @(
    Join-Path $Prism "libraries\net\minecraft\client\1.21.1-20240808.144430\client-1.21.1-20240808.144430-srg.jar"
    Join-Path $Prism "libraries\net\neoforged\neoforge\21.1.228\neoforge-21.1.228-client.jar"
    Join-Path $Prism "libraries\net\neoforged\neoforge\21.1.228\neoforge-21.1.228-universal.jar"
    Join-Path $Prism "libraries\net\neoforged\fancymodloader\loader\4.0.42\loader-4.0.42.jar"
    Join-Path $Prism "libraries\net\neoforged\bus\8.0.5\bus-8.0.5.jar"
    Join-Path $Prism "libraries\net\neoforged\mergetool\2.0.0\mergetool-2.0.0-api.jar"
    Join-Path $Prism "libraries\com\mojang\brigadier\1.3.10\brigadier-1.3.10.jar"
    Join-Path $Prism "libraries\com\mojang\datafixerupper\8.0.16\datafixerupper-8.0.16.jar"
    Join-Path $Prism "libraries\it\unimi\dsi\fastutil\8.5.15\fastutil-8.5.15.jar"
    Join-Path $Prism "libraries\com\google\guava\guava\33.3.1-jre\guava-33.3.1-jre.jar"
    Join-Path $Prism "libraries\io\netty\netty-common\4.1.118.Final\netty-common-4.1.118.Final.jar"
    Join-Path $Prism "libraries\io\netty\netty-buffer\4.1.118.Final\netty-buffer-4.1.118.Final.jar"
    Join-Path $Prism "libraries\io\netty\netty-transport\4.1.118.Final\netty-transport-4.1.118.Final.jar"
    Join-Path $Prism "libraries\com\fasterxml\jackson\core\jackson-core\2.13.4\jackson-core-2.13.4.jar"
    Join-Path $Prism "libraries\com\fasterxml\jackson\core\jackson-annotations\2.13.4\jackson-annotations-2.13.4.jar"
    Join-Path $Prism "libraries\com\fasterxml\jackson\core\jackson-databind\2.13.4.2\jackson-databind-2.13.4.2.jar"
    FirstModJar "architectury-*-neoforge.jar"
    FirstModJar "ftb-library-neoforge-*.jar"
    FirstModJar "ftb-teams-neoforge-*.jar"
    FirstModJar "ftb-quests-neoforge-*.jar"
) -join ";"

$Sources = Get-ChildItem -Path (Join-Path $Root "src\main\java") -Recurse -Filter "*.java" | ForEach-Object { $_.FullName }
& (Join-Path $Java "javac.exe") -cp $ModClasspath -d $ModClasses $Sources
if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }

Copy-Item -Recurse -Force (Join-Path $ModClasses "*") $JarRoot
$JacksonCore = Join-Path $Prism "libraries\com\fasterxml\jackson\core\jackson-core\2.13.4\jackson-core-2.13.4.jar"
$JacksonAnnotations = Join-Path $Prism "libraries\com\fasterxml\jackson\core\jackson-annotations\2.13.4\jackson-annotations-2.13.4.jar"
$JacksonDatabind = Join-Path $Prism "libraries\com\fasterxml\jackson\core\jackson-databind\2.13.4.2\jackson-databind-2.13.4.2.jar"
Push-Location $JarRoot
& (Join-Path $Java "jar.exe") xf $JacksonCore
& (Join-Path $Java "jar.exe") xf $JacksonAnnotations
& (Join-Path $Java "jar.exe") xf $JacksonDatabind
Pop-Location
Copy-Item -Recurse -Force (Join-Path $Root "src\main\resources\*") $JarRoot

Remove-Item -Force $ToolsOutput -ErrorAction SilentlyContinue
Push-Location $JarRoot
& (Join-Path $Java "jar.exe") --create --file $ToolsOutput -C . .
Pop-Location

Get-ChildItem $Mods -Filter "baritone-autoeat-neoforge-1.21.1-*.jar" | Remove-Item -Force
Get-ChildItem $Mods -Filter "baritone-minefix-tools-neoforge-1.21.1-*.jar" | Remove-Item -Force
Get-ChildItem $Mods -Filter "pveguard-neoforge-1.21.1-*.jar" | Remove-Item -Force
Copy-Item -Force $ToolsOutput $ToolsInstalled

Get-Item $Installed, $ToolsInstalled
