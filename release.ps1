# Local release script for ColossusCraft
# Requires GitHub CLI (gh) installed and authenticated

$VERSION = "1.0.1"
Write-Host "Releasing ColossusCraft v$VERSION..."

# Link to GitHub if remote is missing
if (!(git remote)) {
    Write-Host "No git remote found. Initializing repository and linking to GitHub (pathmoor)..." -ForegroundColor Yellow
    git init
    git remote add origin https://github.com/Deaeath/ColossusCraft.git
    git add .
    git commit -m "Initial release of ColossusCraft v$VERSION"
}

    # Check if repo exists on GitHub and create if not, then push initial commit
if (!(gh repo view Deaeath/ColossusCraft 2>$null)) {
    Write-Host "Creating GitHub repository Deaeath/ColossusCraft..." -ForegroundColor Cyan
    gh repo create Deaeath/ColossusCraft --public --source=. --remote=origin --push
}

.\build.ps1

# Create and push tag before creating release
$TAG = "v$VERSION"
Write-Host "Creating git tag $TAG..." -ForegroundColor Cyan
git tag $TAG
Write-Host "Pushing git tag $TAG to remote..." -ForegroundColor Cyan
git push origin $TAG

Write-Host "Creating GitHub release $TAG..." -ForegroundColor Cyan
gh release create $TAG "colossuscraft-neoforge-1.21.1-$VERSION.jar" --title "ColossusCraft $TAG" --notes "Unified release for NeoForge 1.21.1. GF-approved." --generate-notes


Write-Host "Done! Check your GitHub."