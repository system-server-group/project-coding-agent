"""doctool CLI のキャッシュ配置テスト。"""

import io
import json
import tempfile
import sys
import unittest
from contextlib import redirect_stdout
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))
import doctool


class CachePathTest(unittest.TestCase):
    def test_cache_path_keeps_project_relative_structure(self):
        with tempfile.TemporaryDirectory() as tmp:
            root = Path(tmp)
            src = root / 'docs' / 'specs' / '詳細設計' / 'sample.xlsx'
            src.parent.mkdir(parents=True)
            src.write_bytes(b'')

            out = doctool.cache_output_path(src, project_root=root)

            self.assertEqual(
                out,
                root / '.doctool-cache' / 'docs' / 'specs' / '詳細設計' / 'sample.xlsx.md',
            )

    def test_cache_path_returns_none_for_files_outside_project_root(self):
        with tempfile.TemporaryDirectory() as root_tmp, tempfile.TemporaryDirectory() as other_tmp:
            root = Path(root_tmp)
            src = Path(other_tmp) / 'outside.docx'
            src.write_bytes(b'')

            self.assertIsNone(doctool.cache_output_path(src, project_root=root))

    def test_write_preview_cache_creates_parent_directories(self):
        with tempfile.TemporaryDirectory() as tmp:
            root = Path(tmp)
            src = root / 'docs' / 'basic' / 'sample.docx'
            src.parent.mkdir(parents=True)
            src.write_bytes(b'')

            out = doctool.write_preview_cache('# sample\n', src, project_root=root)

            # 先頭に指紋メタコメントが付き、本文はそのまま残る
            self.assertEqual(doctool.strip_cache_header(out.read_text(encoding='utf-8')), '# sample\n')
            self.assertEqual(out.relative_to(root), Path('.doctool-cache/docs/basic/sample.docx.md'))


class CacheFreshnessTest(unittest.TestCase):
    def test_written_cache_embeds_source_digest(self):
        with tempfile.TemporaryDirectory() as tmp:
            root = Path(tmp)
            src = root / 'sample.docx'
            src.write_bytes(b'hello')

            out = doctool.write_preview_cache('# sample\n', src, project_root=root)

            self.assertEqual(doctool.read_cached_digest(out), doctool.source_digest(src))

    def test_cache_is_fresh_only_when_digest_matches(self):
        with tempfile.TemporaryDirectory() as tmp:
            root = Path(tmp)
            src = root / 'sample.docx'
            src.write_bytes(b'v1')
            out = doctool.write_preview_cache('# v1\n', src, project_root=root)

            self.assertTrue(doctool.cache_is_fresh(out, doctool.source_digest(src)))

            # 元ファイルが変わると指紋が変わり、fresh でなくなる
            src.write_bytes(b'v2-changed')
            self.assertFalse(doctool.cache_is_fresh(out, doctool.source_digest(src)))

    def test_cache_is_fresh_false_for_missing_or_headerless_cache(self):
        with tempfile.TemporaryDirectory() as tmp:
            root = Path(tmp)
            src = root / 'sample.docx'
            src.write_bytes(b'x')
            digest = doctool.source_digest(src)

            missing = root / '.doctool-cache' / 'sample.docx.md'
            self.assertFalse(doctool.cache_is_fresh(missing, digest))
            self.assertFalse(doctool.cache_is_fresh(None, digest))

            # メタコメントが無い旧キャッシュは fresh 扱いしない(1度は再生成させる)
            missing.parent.mkdir(parents=True)
            missing.write_text('# no header\n', encoding='utf-8')
            self.assertFalse(doctool.cache_is_fresh(missing, digest))

    def test_strip_cache_header_leaves_headerless_text_intact(self):
        self.assertEqual(doctool.strip_cache_header('# plain\nbody\n'), '# plain\nbody\n')


class HashCommandTest(unittest.TestCase):
    def test_hash_prints_sha256_of_any_file_type(self):
        with tempfile.TemporaryDirectory() as tmp:
            # バイナリ変換を要さない .md でも実体の sha256 が取れる(型不問)
            md = Path(tmp) / 'note.md'
            md.write_text('# hello\n', encoding='utf-8')

            buf = io.StringIO()
            with redirect_stdout(buf):
                doctool.main(['hash', str(md)])

            line = buf.getvalue().strip()
            self.assertEqual(line, f'{doctool.source_digest(md)}  {md}')


class StatusCommandTest(unittest.TestCase):
    def _write_lock(self, root, src, digest, role='primary', key='feat'):
        lock = root / 'sources.lock.json'
        lock.write_text(json.dumps({
            'version': 1,
            'artifacts': {key: {'sources': [
                {'path': str(src), 'role': role, 'sha256': digest}]}},
        }), encoding='utf-8')
        return lock

    def _run(self, lock):
        buf = io.StringIO()
        with redirect_stdout(buf):
            code = doctool.main(['status', '--lock', str(lock)])
        return code, buf.getvalue()

    def test_unchanged_source_returns_zero(self):
        with tempfile.TemporaryDirectory() as tmp:
            root = Path(tmp)
            src = root / 'a.docx'
            src.write_bytes(b'v1')
            lock = self._write_lock(root, src, doctool.source_digest(src))

            code, out = self._run(lock)

            self.assertEqual(code, 0)
            self.assertIn('unchanged', out)

    def test_changed_source_returns_nonzero(self):
        with tempfile.TemporaryDirectory() as tmp:
            root = Path(tmp)
            src = root / 'a.docx'
            src.write_bytes(b'v1')
            lock = self._write_lock(root, src, doctool.source_digest(src))
            src.write_bytes(b'v2-changed')  # 記録後に実体が変わる

            code, out = self._run(lock)

            self.assertEqual(code, 1)
            self.assertIn('changed', out)

    def test_missing_source_returns_nonzero(self):
        with tempfile.TemporaryDirectory() as tmp:
            root = Path(tmp)
            src = root / 'gone.docx'
            src.write_bytes(b'v1')
            lock = self._write_lock(root, src, doctool.source_digest(src))
            src.unlink()  # ファイルが消えた

            code, out = self._run(lock)

            self.assertEqual(code, 1)
            self.assertIn('missing', out)


if __name__ == '__main__':
    unittest.main()
