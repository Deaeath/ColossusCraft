# Local release script for ColossusCraft
# Requires GitHub CLI (gh) installed and authenticated

$VERSION = "1.0.1"
Write-Host "Releasing ColossusCraft v$VERSION..."

# Link to GitHub if remote is missing
if (!(git remote)) {
    Write-Host "No git remote found. Initializing repository and linking to GitHub (pathmoor)..." -ForegroundColor Yellow
    git init
    git remote add origin https://github.com/pathmoor/ColossusCraft.git
    git add .
    git commit -m "Initial release of ColossusCraft v$VERSION"
}

.\build.ps1

gh release create "v$VERSION" --title "ColossusCraft v$VERSION" --notes "Unified release for NeoForge 1.21.1. GF-approved." --generate-notes
gh release upload "v$VERSION" "colossuscraft-neoforge-1.21.1-$VERSION.jar"

Write-Host "Done! Check your GitHub."