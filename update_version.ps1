#!/usr/bin/env pwsh
# CatScan 版本更新脚本
# 用法：.\update_version.ps1 <major|minor|patch>

param(
    [Parameter(Mandatory=$true)]
    [ValidateSet('major', 'minor', 'patch')]
    [string]$type
)

$gradleFile = "app\build.gradle.kts"
$content = Get-Content $gradleFile -Raw

# 解析当前版本号
if ($content -match 'versionName\s*=\s*"(\d+)\.(\d+)\.(\d+)"') {
    $major = [int]$matches[1]
    $minor = [int]$matches[2]
    $patch = [int]$matches[3]
    
    Write-Host "当前版本：$major.$minor.$patch" -ForegroundColor Cyan
    
    # 根据类型更新版本号
    switch ($type) {
        'major' {
            $major++
            $minor = 0
            $patch = 0
            Write-Host "主版本更新：$major.0.0" -ForegroundColor Green
        }
        'minor' {
            $minor++
            $patch = 0
            Write-Host "次要版本更新：$major.$minor.0" -ForegroundColor Green
        }
        'patch' {
            $patch++
            Write-Host "补丁版本更新：$major.$minor.$patch" -ForegroundColor Green
        }
    }
    
    $newVersion = "$major.$minor.$patch"
    $newVersionCode = $major * 10000 + $minor * 100 + $patch
    
    # 更新 versionName
    $content = $content -replace 'versionName\s*=\s*"[^"]+"', "versionName = `"$newVersion`""
    
    # 更新 versionCode
    $content = $content -replace 'versionCode\s*=\s*\d+', "versionCode = $newVersionCode"
    
    # 保存文件
    Set-Content -Path $gradleFile -Value $content -Encoding UTF8 -NoNewline
    
    Write-Host "`n✅ 版本已更新：$newVersion (versionCode: $newVersionCode)" -ForegroundColor Green
    Write-Host "`n下一步:" -ForegroundColor Yellow
    Write-Host "1. git add $gradleFile" -ForegroundColor White
    Write-Host "2. git commit -m `"chore: bump version to $newVersion`"" -ForegroundColor White
    Write-Host "3. git push" -ForegroundColor White
    Write-Host "4. .\gradlew.bat assembleRelease" -ForegroundColor White
    
} else {
    Write-Host "❌ 无法解析版本号，请检查 $gradleFile 文件" -ForegroundColor Red
    exit 1
}
