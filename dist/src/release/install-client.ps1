# NeFobric @VERSION@ 客户端安装脚本（Windows）
# 用法：双击 install-client.bat，或在 PowerShell 里运行
#   .\install-client.ps1 [-MinecraftDir "D:\Games\.minecraft"]
param(
    [string]$MinecraftDir = (Join-Path $env:APPDATA ".minecraft")
)
$ErrorActionPreference = "Stop"
$Version = "@VERSION@"
$Mc = "@MC@"
$Id = "@VERSION_ID@"
$Here = Split-Path -Parent $MyInvocation.MyCommand.Path

Write-Host "NeFobric $Version 客户端安装"
Write-Host "Minecraft 目录：$MinecraftDir"

$vanillaJson = Join-Path $MinecraftDir "versions\$Mc\$Mc.json"
if (-not (Test-Path $vanillaJson)) {
    Write-Host "找不到原版 $Mc（$vanillaJson）。" -ForegroundColor Red
    Write-Host "请先在启动器里安装并运行一次原版 $Mc；如果 .minecraft 不在默认位置（例如 HMCL / PCL 放在启动器旁边），用 -MinecraftDir 指定。" -ForegroundColor Red
    exit 1
}

# 1. 加载器 jar 放进 libraries（版本文件里的 dev.nefobric:nefobric-loader:@VERSION@）
$libDir = Join-Path $MinecraftDir "libraries\dev\nefobric\nefobric-loader\$Version"
New-Item -ItemType Directory -Force -Path $libDir | Out-Null
Copy-Item (Join-Path $Here "nefobric-loader-$Version.jar") (Join-Path $libDir "nefobric-loader-$Version.jar") -Force

# 2. 版本文件（继承原版 $Mc，主类换成 NeFobric）
$verDir = Join-Path $MinecraftDir "versions\$Id"
New-Item -ItemType Directory -Force -Path $verDir | Out-Null
Copy-Item (Join-Path $Here "$Id.json") (Join-Path $verDir "$Id.json") -Force
# 和 Fabric 安装器一样放一个空的版本 jar，官方启动器要求它存在
$dummy = Join-Path $verDir "$Id.jar"
if (-not (Test-Path $dummy)) { New-Item -ItemType File -Path $dummy | Out-Null }

# 3. 模组文件夹
$mods = Join-Path $MinecraftDir "mods"
New-Item -ItemType Directory -Force -Path $mods | Out-Null

Write-Host ""
Write-Host "安装完成：$verDir" -ForegroundColor Green
Write-Host "  官方启动器：安装 → 新安装 → 版本选择 “release $Id”。"
Write-Host "  HMCL / PCL：刷新版本列表，选择 $Id。"
Write-Host "  模组放进：$mods（可以先放 test-mods 文件夹里的三个测试模组）"
Write-Host "  启动后日志在：$MinecraftDir\logs\nefobric-latest.log"
