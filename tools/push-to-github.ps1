#Requires -Version 5.1
<#
.SYNOPSIS
    一键把本仓库推到 GitHub（本机此前无网络时用得上）。

.DESCRIPTION
    在能联网的机器上运行。会自动：初始化仓库（若没有）→ 提交 → 设置远端 → 推送。
    推送完成后去 GitHub 的 Actions 页面等 "Android CI" 跑完即可下载 APK。

.EXAMPLE
    .\tools\push-to-github.ps1 -RepoUrl https://github.com/me/ambient-lamp.git

.EXAMPLE
    .\tools\push-to-github.ps1 -RepoUrl git@github.com:me/ambient-lamp.git -Tag v2.0.0
#>
param(
    [Parameter(Mandatory = $true)]
    [string]$RepoUrl,

    [string]$Branch = 'main',

    [string]$Message = 'feat: ambient lamp v2 (adaptive 800x480 + phone)',

    # 可选：推送后打一个 tag 触发 GitHub Release 出包
    [string]$Tag
)

$ErrorActionPreference = 'Stop'

function Invoke-Git {
    param([Parameter(ValueFromRemainingArguments = $true)][string[]]$Args)
    & git @Args
    if ($LASTEXITCODE -ne 0) {
        throw "git $($Args -join ' ') 失败（退出码 $LASTEXITCODE）"
    }
}

$root = Split-Path -Parent $PSScriptRoot
Write-Host "仓库目录: $root" -ForegroundColor Cyan

if (-not (Get-Command git -ErrorAction SilentlyContinue)) {
    throw '未找到 git，请先安装 Git for Windows。'
}

Push-Location $root
try {
    if (-not (Test-Path '.git')) {
        Write-Host '→ git init' -ForegroundColor Yellow
        Invoke-Git init
    }

    # 没有配置身份时补一个仓库级身份，避免首次提交失败
    $name = & git config user.name
    if ([string]::IsNullOrWhiteSpace($name)) {
        Write-Host '→ 未配置 git 身份，写入仓库级 user.name/user.email' -ForegroundColor Yellow
        Invoke-Git config user.name 'Ambient Lamp'
        Invoke-Git config user.email 'ambient-lamp@users.noreply.github.com'
    }

    Write-Host '→ git add -A' -ForegroundColor Yellow
    Invoke-Git add -A

    $staged = & git diff --cached --name-only
    if ([string]::IsNullOrWhiteSpace(($staged -join ''))) {
        Write-Host '没有需要提交的改动。' -ForegroundColor Yellow
    }
    else {
        Write-Host "→ git commit（$($staged.Count) 个文件）" -ForegroundColor Yellow
        Invoke-Git commit -m $Message
    }

    Write-Host "→ 分支 $Branch" -ForegroundColor Yellow
    Invoke-Git branch -M $Branch

    $remotes = @(& git remote)
    if ($remotes -contains 'origin') {
        Write-Host '→ 更新 origin' -ForegroundColor Yellow
        Invoke-Git remote set-url origin $RepoUrl
    }
    else {
        Write-Host '→ 添加 origin' -ForegroundColor Yellow
        Invoke-Git remote add origin $RepoUrl
    }

    Write-Host '→ 推送（首次可能需要登录）' -ForegroundColor Yellow
    Invoke-Git push -u origin $Branch

    if ($Tag) {
        Write-Host "→ 打 tag $Tag 并推送（触发 Release）" -ForegroundColor Yellow
        & git tag $Tag 2>$null
        Invoke-Git push origin $Tag
    }

    Write-Host ''
    Write-Host '完成。下一步：' -ForegroundColor Green
    Write-Host '  1. 打开 GitHub 仓库 → Actions → Android CI'
    Write-Host '  2. 等构建结束，在 Artifacts 下载 ambient-lamp-apk'
    Write-Host '  3. 若要正式签名，在 Settings → Secrets 配 KEYSTORE_BASE64 等四项'
}
finally {
    Pop-Location
}
