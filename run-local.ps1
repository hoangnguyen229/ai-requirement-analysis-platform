# run-local.ps1

# ==========================================
# Local Runtime Script
# Requirement Document Analyzer Agent
# ==========================================

$ErrorActionPreference = "Stop"

# Gemini API Key
# Replace with your own key
$env:GEMINI_API_KEY = "xxxxxxxxxxxxxxxxxxxxxxx"

# Optional port override
$env:PORT = "8080"

# Java Runtime
$javaExe = "C:\path\to\your\java\bin\java.exe"

# JAR location
$jarFile = ".\backend\target\excel-analyzer-0.0.1-SNAPSHOT.jar"

Write-Host ""
Write-Host "==========================================" -ForegroundColor Cyan
Write-Host " Requirement Document Analyzer Agent" -ForegroundColor Cyan
Write-Host "==========================================" -ForegroundColor Cyan
Write-Host ""

Write-Host "PORT: $env:PORT" -ForegroundColor Yellow
Write-Host "Starting Spring Boot application..." -ForegroundColor Yellow
Write-Host ""

& $javaExe -jar $jarFile
```
