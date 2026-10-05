"""doctool excelsave — Excel(COM) で対象 xlsx を非表示で開いて保存する。

fitrows は「Excel で開いて保存したファイルの実測 ht」を測定手段にしており、
本モジュールはその「Excel で開いて Ctrl+S」を PowerShell 経由の COM 自動化で代行する
(追加の Python 依存を増やさないため pywin32 は使わない)。測定ロジック自体には
関与しない。fitrows --auto から呼ばれるほか、doctool excelsave <file> で単独でも使える。

前提・制約:
  - Windows + デスクトップ版 Excel が必要(headless / CI では使えない)
  - 対象ファイルが開かれていると失敗する(実行前にロックを検査して明確にエラーにする)
  - マクロは強制無効(AutomationSecurity=3)、ダイアログは抑止(DisplayAlerts=False)で開く
  - タイムアウト時は PowerShell を強制終了するが、起動済みの EXCEL.EXE が残ることがある
"""
import os
import subprocess
import sys

DEFAULT_TIMEOUT_SEC = 180

# 対象パスは引用符・エスケープ事故を避けるため環境変数で渡す
_ENV_PATH = 'DOCTOOL_EXCELSAVE_PATH'

# Open → 保存強制(Saved=$false)→ Save。Close/Quit/COM 解放は finally で必ず行う。
# OutputEncoding は Python 側の decode(utf-8)と揃える(既定の cp932 だと文字化けする)。
_PS_SCRIPT = r'''
$ErrorActionPreference = 'Stop'
try { [Console]::OutputEncoding = [System.Text.Encoding]::UTF8 } catch {}
$path = $env:DOCTOOL_EXCELSAVE_PATH
if (-not $path) { throw 'DOCTOOL_EXCELSAVE_PATH が設定されていません' }
$excel = $null
$wb = $null
try {
    $excel = New-Object -ComObject Excel.Application
    $excel.Visible = $false
    $excel.DisplayAlerts = $false
    $excel.AutomationSecurity = 3
    $wb = $excel.Workbooks.Open($path, 0, $false)
    if ($wb.ReadOnly) { throw "読み取り専用で開かれました(別プロセスが使用中の可能性): $path" }
    $wb.Saved = $false
    $wb.Save()
} finally {
    if ($null -ne $wb) { try { $wb.Close($false) } catch {} }
    if ($null -ne $excel) { try { $excel.Quit() } catch {} }
    foreach ($o in @($wb, $excel)) {
        if ($null -ne $o) { [void][System.Runtime.InteropServices.Marshal]::ReleaseComObject($o) }
    }
    [System.GC]::Collect()
    [System.GC]::WaitForPendingFinalizers()
}
'''


class ExcelSaveError(RuntimeError):
    """excelsave の失敗(呼び出し側で手動フローへの切替を案内する)。"""


def excel_save(path, timeout=DEFAULT_TIMEOUT_SEC):
    """path を Excel で開いて保存する(手動の「開いて Ctrl+S」と等価)。

    自動高さ行の実測 ht が書き戻される。失敗は ExcelSaveError(部分適用は起きない)。
    """
    if sys.platform != 'win32':
        raise ExcelSaveError('excelsave は Windows(デスクトップ版 Excel)でのみ使えます')
    full = os.path.abspath(path)
    if not os.path.isfile(full):
        raise ExcelSaveError(f'ファイルが見つかりません: {path}')
    try:
        with open(full, 'r+b'):
            pass
    except PermissionError:
        raise ExcelSaveError(
            f'対象ファイルが開かれています。Excel を閉じて再実行してください: {path}') from None
    env = dict(os.environ)
    env[_ENV_PATH] = full
    try:
        proc = subprocess.run(
            ['powershell', '-NoProfile', '-NonInteractive', '-ExecutionPolicy', 'Bypass',
             '-Command', _PS_SCRIPT],
            env=env, capture_output=True, encoding='utf-8', errors='replace',
            timeout=timeout)
    except FileNotFoundError:
        raise ExcelSaveError('powershell が見つかりません(Windows PowerShell が必要です)') from None
    except subprocess.TimeoutExpired:
        raise ExcelSaveError(
            f'Excel の保存が {timeout} 秒で完了しませんでした。'
            'EXCEL.EXE が残っていれば手動で終了してください') from None
    if proc.returncode != 0:
        detail = (proc.stderr or proc.stdout or '').strip() or f'exit={proc.returncode}'
        raise ExcelSaveError(f'Excel での保存に失敗しました: {detail}')
