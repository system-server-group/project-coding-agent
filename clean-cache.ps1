<#
.SYNOPSIS
    プロジェクトのキャッシュ・生成物を削除し、クリーンな状態に戻す。

.DESCRIPTION
    リポジトリルート（このスクリプトの置き場所）を基準に、次の項目を削除する。

        .doctool-cache/   doctool の Markdown 読取キャッシュ（Word/Excel 正本から再生成される）
        .docgen/          docgen 系スキルの計画・進捗/決定台帳・設計書の指紋記録
        .codegen/         codegen 系スキルの計画・進捗/決定台帳・設計書の指紋記録
        .gradle/          Gradle のプロジェクト内キャッシュ
        build/            Gradle のビルド出力
        bin/              Eclipse (Buildship) の出力先
        __pycache__/      Python バイトコード（.git/・venv/・.venv/ の配下は対象外）

    削除前に対象の一覧と容量を表示し、y/n で実行の可否を確認する。
    .docgen/ と .codegen/ は進捗・決定台帳を含むため、存在する場合は警告を表示する。

    上記以外（venv/・.git/・docs/・src/ など）には触れない。
    シンボリックリンクやジャンクションは辿らず、リンク先の内容は削除しない。

.PARAMETER DryRun
    削除を行わず、対象の一覧と容量のみ表示する。

.EXAMPLE
    .\clean-cache.ps1
    対象を表示し、y を入力すると削除する。

.EXAMPLE
    .\clean-cache.ps1 -DryRun
    対象の表示のみ行う。

.NOTES
    終了コード
        0 : 完了、または中止（n を選択）・削除対象なし
        1 : 削除に失敗した項目がある
        2 : リポジトリルートを判定できない

    Gradle デーモンや IDE（Eclipse Buildship、VS Code の Java 拡張など）が .gradle/ や build/ を
    使用中だと削除に失敗する。その場合は .\gradlew.bat --stop を実行し、IDE を終了してから再実行する。

    Windows PowerShell 5.1 と PowerShell 7 の両方で動作する。5.1 で日本語を正しく扱うため、
    本ファイルは UTF-8 (BOM 付き) で保存する。
#>
[CmdletBinding()]
param(
    [switch]$DryRun
)

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'

# ---------------------------------------------------------------------------
# 設定
# ---------------------------------------------------------------------------

# リポジトリルート。誤った場所で実行されないよう、次のマーカーの存在を確認する
$Root = $PSScriptRoot
$RootMarkers = @('settings.gradle.kts', '.claude')

# ルート直下の固定対象（ディレクトリごと削除する）
$FixedTargets = @(
    @{ Name = '.doctool-cache'; Note = 'doctool の Markdown 読取キャッシュ';               Ledger = $false }
    @{ Name = '.docgen';        Note = 'docgen 系スキルの計画・進捗/決定台帳・指紋記録';  Ledger = $true }
    @{ Name = '.codegen';       Note = 'codegen 系スキルの計画・進捗/決定台帳・指紋記録'; Ledger = $true }
    @{ Name = '.gradle';        Note = 'Gradle のプロジェクト内キャッシュ';                Ledger = $false }
    @{ Name = 'build';          Note = 'Gradle のビルド出力';                              Ledger = $false }
    @{ Name = 'bin';            Note = 'Eclipse (Buildship) の出力先';                     Ledger = $false }
)

# __pycache__ の再帰検索で、どの階層でも中に入らないディレクトリ名
$SearchExcludes = @('.git', 'venv', '.venv')
$PycacheName = '__pycache__'

# ---------------------------------------------------------------------------
# 関数
# ---------------------------------------------------------------------------

function Test-ReparsePoint {
    param([System.IO.FileSystemInfo]$Item)
    return (($Item.Attributes -band [System.IO.FileAttributes]::ReparsePoint) -ne 0)
}

# ディレクトリ配下のファイル容量の合計（リンクは辿らない。読めない場所は集計しない）
function Get-TreeSize {
    param([string]$Path)
    $total = [long]0
    $dir = [System.IO.DirectoryInfo]$Path
    if (Test-ReparsePoint $dir) { return $total }
    try {
        foreach ($f in $dir.EnumerateFiles()) {
            if (-not (Test-ReparsePoint $f)) { $total += $f.Length }
        }
        foreach ($d in $dir.EnumerateDirectories()) {
            $total += Get-TreeSize -Path $d.FullName
        }
    } catch {
        # アクセスできない場所は集計をあきらめる（削除時に改めて失敗として報告される）
    }
    return $total
}

# __pycache__ ディレクトリを再帰検索する（見つけた __pycache__ の中には入らない）
#   SkipTop : この階層でのみ飛ばすディレクトリ名（固定対象はディレクトリごと消すため二重に扱わない）
#   SkipAny : どの階層でも飛ばすディレクトリ名
function Find-PycacheDirs {
    param([string]$Path, [string[]]$SkipTop, [string[]]$SkipAny)
    $dir = [System.IO.DirectoryInfo]$Path
    foreach ($d in $dir.EnumerateDirectories()) {
        if (Test-ReparsePoint $d) { continue }
        if (($SkipTop -contains $d.Name) -or ($SkipAny -contains $d.Name)) { continue }
        if ($d.Name -eq $PycacheName) {
            $d.FullName
            continue
        }
        Find-PycacheDirs -Path $d.FullName -SkipTop @() -SkipAny $SkipAny
    }
}

function Format-Size {
    param([long]$Bytes)
    if ($Bytes -ge 1MB) { return ('{0:N1} MB' -f ($Bytes / 1MB)) }
    if ($Bytes -ge 1KB) { return ('{0:N0} KB' -f ($Bytes / 1KB)) }
    return ('{0} B' -f $Bytes)
}

# 読み取り専用属性を外す（読み取り専用のファイル・ディレクトリは削除に失敗するため）
function Clear-ReadOnlyAttribute {
    param([System.IO.FileSystemInfo]$Item)
    if (($Item.Attributes -band [System.IO.FileAttributes]::ReadOnly) -ne 0) {
        $Item.Attributes = [System.IO.FileAttributes](
            [int]$Item.Attributes -band (-bnot [int][System.IO.FileAttributes]::ReadOnly))
    }
}

# ディレクトリを丸ごと削除する。
# リンク（シンボリックリンク・ジャンクション）はリンク自体だけを削除し、リンク先には入らない。
# .NET の Directory.Delete(recursive) はジャンクションを含むと失敗するため、自前で再帰する。
function Remove-Tree {
    param([string]$Path)
    $dir = [System.IO.DirectoryInfo]$Path
    if (Test-ReparsePoint $dir) {
        [System.IO.Directory]::Delete($dir.FullName)
        return
    }
    foreach ($item in $dir.EnumerateFileSystemInfos()) {
        if ($item -is [System.IO.DirectoryInfo]) {
            Remove-Tree -Path $item.FullName
        } else {
            if (-not (Test-ReparsePoint $item)) { Clear-ReadOnlyAttribute -Item $item }
            $item.Delete()
        }
    }
    Clear-ReadOnlyAttribute -Item $dir
    [System.IO.Directory]::Delete($dir.FullName)
}

# ---------------------------------------------------------------------------
# 本体
# ---------------------------------------------------------------------------

foreach ($marker in $RootMarkers) {
    if (-not (Test-Path -LiteralPath (Join-Path $Root $marker))) {
        Write-Host "エラー: リポジトリルートと判定できません（$marker が見つかりません）: $Root" -ForegroundColor Red
        exit 2
    }
}

# 対象の収集
$targets = New-Object System.Collections.Generic.List[object]
$absent = New-Object System.Collections.Generic.List[string]
foreach ($t in $FixedTargets) {
    $full = Join-Path $Root $t.Name
    if (Test-Path -LiteralPath $full -PathType Container) {
        $targets.Add([pscustomobject]@{
            Kind = 'dir'; Rel = $t.Name; Full = $full; Note = $t.Note; Ledger = $t.Ledger
            Size = (Get-TreeSize -Path $full)
        })
    } else {
        $absent.Add($t.Name)
    }
}
$skipTop = @($FixedTargets | ForEach-Object { $_.Name })
foreach ($full in @(Find-PycacheDirs -Path $Root -SkipTop $skipTop -SkipAny $SearchExcludes)) {
    $targets.Add([pscustomobject]@{
        Kind = 'pyc'; Rel = $full.Substring($Root.Length).TrimStart('\', '/'); Full = $full
        Note = 'Python バイトコード'; Ledger = $false
        Size = (Get-TreeSize -Path $full)
    })
}

# 対象の表示
Write-Host ''
Write-Host "clean-cache: リポジトリルート = $Root"
Write-Host ''

if ($targets.Count -eq 0) {
    Write-Host '削除対象はありません。既にクリーンな状態です。'
    exit 0
}

Write-Host '削除対象:'
foreach ($t in $targets) {
    Write-Host ('  [{0}] {1}  ({2})  {3}' -f $t.Kind, $t.Rel, (Format-Size -Bytes $t.Size), $t.Note)
}
if ($absent.Count -gt 0) {
    Write-Host ('  （存在しないため対象外: {0}）' -f ($absent -join ', ')) -ForegroundColor DarkGray
}
$totalSize = [long]0
foreach ($t in $targets) { $totalSize += $t.Size }
Write-Host ('合計: {0} 項目 / {1}' -f $targets.Count, (Format-Size -Bytes $totalSize))
Write-Host ''

$ledgers = @($targets | Where-Object { $_.Ledger })
if ($ledgers.Count -gt 0) {
    $names = ($ledgers | ForEach-Object { $_.Rel + '/' }) -join ', '
    Write-Host "警告: $names には進捗・決定台帳（progress.md）と設計書の指紋記録（sources.lock.json）が含まれます。" -ForegroundColor Yellow
    Write-Host '      削除すると決定ログが失われ、次回は docgen-plan / codegen-plan による台帳の再生成が必要になります。' -ForegroundColor Yellow
    Write-Host ''
}

if ($DryRun) {
    Write-Host 'DryRun のため削除は行いません。'
    exit 0
}

# 実行の確認
$answer = Read-Host '削除を実行しますか? [y/N]'
if ($answer -notmatch '^[yY]$') {
    Write-Host '中止しました。何も削除していません。'
    exit 0
}
Write-Host ''

# 削除
$failed = New-Object System.Collections.Generic.List[string]
$deletedSize = [long]0
foreach ($t in $targets) {
    try {
        Remove-Tree -Path $t.Full
        Write-Host ('  削除: {0}' -f $t.Rel)
        $deletedSize += $t.Size
    } catch {
        Write-Host ('  失敗: {0} ({1})' -f $t.Rel, $_.Exception.GetBaseException().Message) -ForegroundColor Red
        $failed.Add($t.Rel)
    }
}
Write-Host ''

if ($failed.Count -gt 0) {
    Write-Host ('{0} 項目の削除に失敗しました: {1}' -f $failed.Count, ($failed -join ', ')) -ForegroundColor Red
    Write-Host 'Gradle デーモンや IDE が使用中の可能性があります。.\gradlew.bat --stop を実行し、IDE を終了してから再実行してください。' -ForegroundColor Yellow
    exit 1
}
Write-Host ('完了: {0} 項目 / {1} を削除しました。' -f $targets.Count, (Format-Size -Bytes $deletedSize))
exit 0
