"""doctool excelsave / fitrows --auto のテスト。

実行: PYTHONUTF8=1 python .claude/skills/doctool/scripts/doctool/tests/test_xlsx_excelsave.py

実 Excel は起動しない(subprocess.run / excel_save を差し替えて経路だけ検証する)。
実 Excel を通した確認は fitrows --auto の E2E(手動)で行う。
"""
import io
import subprocess
import sys
import tempfile
import unittest
from contextlib import redirect_stderr, redirect_stdout
from pathlib import Path
from unittest import mock

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))

import doctool
import xlsx_excelsave

try:
    import xlsx_fitrows
    HAS_LXML = True
except ImportError:
    HAS_LXML = False


def _ok(returncode=0, stderr=''):
    return subprocess.CompletedProcess([], returncode, stdout='', stderr=stderr)


class ExcelSaveTest(unittest.TestCase):
    def _tmpfile(self):
        d = Path(tempfile.mkdtemp())
        path = d / 'target.xlsx'
        path.write_bytes(b'dummy')
        return path

    def test_non_windows_rejected(self):
        with mock.patch.object(xlsx_excelsave.sys, 'platform', 'linux'):
            with self.assertRaisesRegex(xlsx_excelsave.ExcelSaveError, 'Windows'):
                xlsx_excelsave.excel_save('x.xlsx')

    @unittest.skipUnless(sys.platform == 'win32', 'Windows 専用の経路')
    def test_missing_file_rejected(self):
        with self.assertRaisesRegex(xlsx_excelsave.ExcelSaveError, '見つかりません'):
            xlsx_excelsave.excel_save('no/such/file.xlsx')

    @unittest.skipUnless(sys.platform == 'win32', 'Windows 専用の経路')
    def test_success_invokes_powershell_with_env_path(self):
        path = self._tmpfile()
        with mock.patch.object(xlsx_excelsave.subprocess, 'run',
                               return_value=_ok()) as run:
            xlsx_excelsave.excel_save(path)
        args, kwargs = run.call_args
        cmd = args[0]
        self.assertEqual(cmd[0], 'powershell')
        self.assertIn('-NonInteractive', cmd)
        # 対象パスは環境変数で渡す(コマンドラインには載せない)
        self.assertEqual(kwargs['env'][xlsx_excelsave._ENV_PATH], str(path.resolve()))
        self.assertNotIn(str(path), ' '.join(cmd))

    @unittest.skipUnless(sys.platform == 'win32', 'Windows 専用の経路')
    def test_failure_raises_with_detail(self):
        path = self._tmpfile()
        with mock.patch.object(xlsx_excelsave.subprocess, 'run',
                               return_value=_ok(1, stderr='COM error: boom')):
            with self.assertRaisesRegex(xlsx_excelsave.ExcelSaveError, 'boom'):
                xlsx_excelsave.excel_save(path)

    @unittest.skipUnless(sys.platform == 'win32', 'Windows 専用の経路')
    def test_timeout_raises(self):
        path = self._tmpfile()
        with mock.patch.object(
                xlsx_excelsave.subprocess, 'run',
                side_effect=subprocess.TimeoutExpired(['powershell'], 1)):
            with self.assertRaisesRegex(xlsx_excelsave.ExcelSaveError, '完了しませんでした'):
                xlsx_excelsave.excel_save(path, timeout=1)


@unittest.skipUnless(HAS_LXML, 'lxml なし')
class RunAutoTest(unittest.TestCase):
    SPEC = {'file': 'x.xlsx', 'anchorColumn': 'A', 'anchorPattern': r'^\d+-\d+$',
            'columns': 'A:C'}

    def _run_auto(self, run_rcs, save_side_effect=None):
        """excel_save と run を差し替えて run_auto を実行し (rc, 保存回数, 実行回数) を返す。"""
        save = mock.Mock(side_effect=save_side_effect)
        run = mock.Mock(side_effect=run_rcs)
        with mock.patch.object(xlsx_excelsave, 'excel_save', save), \
                mock.patch.object(xlsx_fitrows, 'run', run), \
                redirect_stdout(io.StringIO()):
            rc = xlsx_fitrows.run_auto(self.SPEC)
        return rc, save.call_count, run.call_count

    def test_completes_after_second_save(self):
        # 保存→検出(3)→保存→適用(0)の標準経路
        rc, saves, runs = self._run_auto([xlsx_fitrows.NEED_EXCEL_SAVE, 0])
        self.assertEqual((rc, saves, runs), (0, 2, 2))

    def test_no_capped_rows_completes_with_single_save(self):
        rc, saves, runs = self._run_auto([0])
        self.assertEqual((rc, saves, runs), (0, 1, 1))

    def test_gives_up_when_save_makes_no_progress(self):
        rc, saves, runs = self._run_auto([xlsx_fitrows.NEED_EXCEL_SAVE] * 10)
        self.assertEqual((rc, saves, runs), (1, xlsx_fitrows.MAX_AUTO_SAVES,
                                             xlsx_fitrows.MAX_AUTO_SAVES))

    def test_save_failure_reports_manual_fallback(self):
        rc, saves, runs = self._run_auto(
            [0], save_side_effect=xlsx_excelsave.ExcelSaveError('Excel なし'))
        self.assertEqual((rc, saves, runs), (1, 1, 0))

    def test_error_rc_from_run_is_returned(self):
        rc, saves, runs = self._run_auto([xlsx_fitrows.NEED_EXCEL_SAVE, 1])
        self.assertEqual((rc, saves, runs), (1, 2, 2))


class CliTest(unittest.TestCase):
    def test_auto_with_check_rejected(self):
        with redirect_stderr(io.StringIO()) as err:
            rc = doctool.main(['fitrows', '--auto', '--check', 'spec.json'])
        self.assertEqual(rc, 2)
        self.assertIn('併用できません', err.getvalue())


if __name__ == '__main__':
    unittest.main(verbosity=2)
