# Build automation script for Excel Requirement Analyzer

$ErrorActionPreference = "Stop"
$root = Get-Location

# 1. Add portable Node.js to path
Write-Host "Configuring build environment..." -ForegroundColor Cyan
$env:PATH = "$root\node-dist;" + $env:PATH
$env:JAVA_HOME = "C:\Program Files\JetBrains\IntelliJ IDEA 2025.3.3\jbr"

# 2. Build Frontend
Write-Host "Building Angular Frontend..." -ForegroundColor Cyan
Set-Location "$root\frontend"
& npm run build

# 3. Prepare static resources directory in Backend
Write-Host "Preparing static resource directories..." -ForegroundColor Cyan
$staticDir = "$root\backend\src\main\resources\static"
if (Test-Path $staticDir) {
    Remove-Item -Recurse -Force "$staticDir\*"
} else {
    New-Item -ItemType Directory -Path $staticDir -Force
}

# 4. Copy build artifacts from frontend/dist/frontend/browser to backend static resources
Write-Host "Bundling Frontend into Spring Boot..." -ForegroundColor Cyan
Copy-Item -Path "$root\frontend\dist\frontend\browser\*" -Destination $staticDir -Recurse -Force

# 5. Build Backend
Write-Host "Building Spring Boot Backend..." -ForegroundColor Cyan
Set-Location "$root\backend"
& "C:\Program Files\JetBrains\IntelliJ IDEA 2025.3.3\plugins\maven\lib\maven3\bin\mvn.cmd" clean package

Write-Host "Build complete! The executable JAR is located at: backend\target\excel-analyzer-0.0.1-SNAPSHOT.jar" -ForegroundColor Green
Set-Location $root
