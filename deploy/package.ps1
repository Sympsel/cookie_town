# ============================================================
#  Cookie Town 本机打包脚本（Windows 开发机上运行）
#  作用：mvn 打可执行 jar → 组装出可直接上传服务器的 dist/ 目录 + zip 包
#  用法：powershell -ExecutionPolicy Bypass -File deploy\package.ps1
# ============================================================
$ErrorActionPreference = 'Stop'

# 1) 定位项目根（本脚本位于 deploy/ 下）
$deployDir = Split-Path -Parent $MyInvocation.MyCommand.Definition
$root      = Split-Path -Parent $deployDir
Set-Location $root
Write-Host "==> 项目根：$root" -ForegroundColor Cyan

# 2) 用 JDK 25 打包（跳过测试）
$env:JAVA_HOME = 'C:\Users\Symps\.jdks\openjdk-25'
Write-Host "==> mvn clean package -DskipTests" -ForegroundColor Cyan
& mvn clean package -DskipTests
if ($LASTEXITCODE -ne 0) { throw "Maven 打包失败（exit=$LASTEXITCODE）" }

$jar = Join-Path $root 'target\cookie-town.jar'
if (-not (Test-Path $jar)) { throw "未找到打包产物：$jar（检查 pom.xml 的 finalName 是否为 cookie-town）" }

# 3) 组装 dist/（首次部署的完整上线包）
$dist = Join-Path $root 'dist'
if (Test-Path $dist) { Remove-Item -Recurse -Force $dist }
New-Item -ItemType Directory -Force -Path $dist               | Out-Null
New-Item -ItemType Directory -Force -Path (Join-Path $dist 'config') | Out-Null

Copy-Item $jar                          (Join-Path $dist 'cookie-town.jar')
Copy-Item (Join-Path $root 'wwwroot')   (Join-Path $dist 'wwwroot') -Recurse
# 生产配置模板（含占位符）；上线前需在服务器上替换真实密码/密钥
Copy-Item (Join-Path $deployDir 'config.prod.json') (Join-Path $dist 'config\config.json')
# 部署期文件（systemd / Caddy / nginx / 指南）一并带上，方便在服务器就地取用
Copy-Item $deployDir (Join-Path $dist 'deploy') -Recurse

Write-Host "==> dist/ 组装完成：" -ForegroundColor Green
Get-ChildItem -Recurse $dist | ForEach-Object { $_.FullName.Substring($dist.Length + 1) }

# 4) 打成 zip 便于 scp 上传
$zip = Join-Path $root 'cookie-town-dist.zip'
if (Test-Path $zip) { Remove-Item -Force $zip }
Compress-Archive -Path (Join-Path $dist '*') -DestinationPath $zip
Write-Host "==> 已生成上传包：$zip" -ForegroundColor Green

# Write-Host ""
# Write-Host "下一步（首次部署）：" -ForegroundColor Yellow
# Write-Host "  scp cookie-town-dist.zip root@<服务器IP>:/opt/"
# Write-Host "  # 登录服务器后：unzip 到 /opt/cookie-town，按 deploy/部署指南.md 继续"
# Write-Host "后续重新部署：只需覆盖 /opt/cookie-town/cookie-town.jar（前端有改动再同步 wwwroot/），"
# Write-Host "             切勿覆盖服务器上的 config/config.json 与 uploads/。"
