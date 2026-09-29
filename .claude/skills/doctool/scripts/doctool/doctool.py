#!/usr/bin/env python3
"""doctool — 設計書ドキュメント(docx/xlsx)を中間表現(IR)へ変換する読み書きツール

usage:
  PYTHONUTF8=1 python .claude/skills/doctool/scripts/doctool/doctool.py dump [--format] <file.docx|xlsx> [...]  # IR(JSON)。--format で書式オーバーレイ付加
  PYTHONUTF8=1 python .claude/skills/doctool/scripts/doctool/doctool.py preview <file.docx|xlsx> [...]  # Markdown(既定で .doctool-cache/ にも保存。未変更ならキャッシュ再利用。--force で強制再生成)
  PYTHONUTF8=1 python .claude/skills/doctool/scripts/doctool/doctool.py cache <file.docx|xlsx> [...]    # Markdownを .doctool-cache/ に保存(未変更ならスキップ。--force で強制再生成)
  PYTHONUTF8=1 python .claude/skills/doctool/scripts/doctool/doctool.py hash <file> [...]               # ファイル実体のsha256指紋(型不問)。変更検出のベースライン照合に使う
  PYTHONUTF8=1 python .claude/skills/doctool/scripts/doctool/doctool.py status --lock <lock.json> [...]  # lock記録の指紋を現在値と突合。changed/missing があれば終了コード1(リコンサイル・ゲート用)
  PYTHONUTF8=1 python .claude/skills/doctool/scripts/doctool/doctool.py analyze <file.docx> [...]       # 記入ストラテジ(JSON)
  PYTHONUTF8=1 python .claude/skills/doctool/scripts/doctool/doctool.py fill <spec.json> [...]          # 雛形差し替え生成(docx=要 python-docx / xlsx=要 lxml。出力拡張子で振り分け)
  PYTHONUTF8=1 python .claude/skills/doctool/scripts/doctool/doctool.py fitrows [--auto] <spec.json> [...]  # Excel実測htに基づく行高調整(頭打ちケース行の分割。Excel保存待ちは終了コード3。--auto で Excel 保存(COM)込みで完了まで自動実行)
  PYTHONUTF8=1 python .claude/skills/doctool/scripts/doctool/doctool.py excelsave <file.xlsx> [...]      # Excel(COM) で開いて保存し実測 ht を書き戻させる(Windows + デスクトップ版 Excel 専用)

モジュール構成(本ファイルは CLI とリーダー・ライター振り分けのみ):
  ir.py           IR の共通定義
  docx_reader.py  docx → IR(標準ライブラリのみ)
  xlsx_reader.py  xlsx → IR(要 openpyxl)
  preview.py      IR → Markdown プレビュー
  analyzer.py     IR → 雛形の記入ストラテジ
  docx_writer.py  記入スペック → docx(要 python-docx)
  xlsx_writer.py  記入スペック → xlsx(worksheet/styles/drawing の XML を直接編集。要 lxml)
  xlsx_fitrows.py 行高調整スペック → xlsx(Excel保存済みの実測 ht を測定手段に使う。要 lxml)
  xlsx_excelsave.py Excel(COM) で開いて保存(fitrows の測定用。Windows + デスクトップ版 Excel)
"""
import argparse
import hashlib
import json
import pathlib
import re
import sys

sys.path.insert(0, str(pathlib.Path(__file__).resolve().parent))

import analyzer      # noqa: E402
import docx_reader   # noqa: E402
import preview       # noqa: E402


DEFAULT_CACHE_DIR = '.doctool-cache'

# キャッシュ先頭に埋め込む元ファイル指紋のメタコメント。
# 例: <!-- doctool-cache source-sha256=abc123... -->
_CACHE_HEADER_RE = re.compile(r'^<!-- doctool-cache source-sha256=([0-9a-f]{64}) -->')


def source_digest(src):
    """元ファイルのバイト列 sha256(16進)。再生成要否の判定キー。"""
    h = hashlib.sha256()
    with open(src, 'rb') as fp:
        for chunk in iter(lambda: fp.read(65536), b''):
            h.update(chunk)
    return h.hexdigest()


def _cache_header(digest):
    return f'<!-- doctool-cache source-sha256={digest} -->'


def read_cached_digest(cache_path):
    """キャッシュ先頭コメントに埋め込まれた source-sha256 を返す。無ければ None。"""
    try:
        with open(cache_path, encoding='utf-8') as fp:
            first = fp.readline()
    except OSError:
        return None
    m = _CACHE_HEADER_RE.match(first)
    return m.group(1) if m else None


def strip_cache_header(text):
    """キャッシュ本文から先頭のメタコメント行を取り除いて返す。"""
    if _CACHE_HEADER_RE.match(text):
        nl = text.find('\n')
        return text[nl + 1:] if nl != -1 else ''
    return text


def cache_is_fresh(cache_path, digest):
    """キャッシュが存在し、埋め込み指紋が digest と一致すれば True。"""
    return cache_path is not None and cache_path.exists() \
        and read_cached_digest(cache_path) == digest


def read_document(src, source_name=None, with_format=False):
    """拡張子でリーダーを振り分ける(docx は標準ライブラリのみ、xlsx は openpyxl)。"""
    name = source_name or str(src)
    if name.lower().endswith(('.xlsx', '.xlsm')):
        import xlsx_reader
        return xlsx_reader.read_xlsx(src, source_name, with_format=with_format)
    return docx_reader.read_docx(src, source_name, with_format=with_format)


def cache_output_path(src, project_root='.', cache_dir=DEFAULT_CACHE_DIR):
    """project_root 相対の配置を保った Markdown キャッシュ先を返す。

    例: docs/specs/foo.xlsx -> .doctool-cache/docs/specs/foo.xlsx.md
    project_root 外のファイルは、プロジェクト相対配置を定義できないため None を返す。
    """
    root = pathlib.Path(project_root).resolve()
    source = pathlib.Path(src).resolve()
    try:
        rel = source.relative_to(root)
    except ValueError:
        return None

    cache_root = pathlib.Path(cache_dir)
    if not cache_root.is_absolute():
        cache_root = root / cache_root

    out = cache_root / rel
    if out.suffix:
        return out.with_suffix(out.suffix + '.md')
    return out.with_suffix('.md')


def write_preview_cache(markdown, src, project_root='.', cache_dir=DEFAULT_CACHE_DIR, digest=None):
    """Markdown プレビューを .doctool-cache/ 配下へ保存する。

    先頭に元ファイルの sha256 指紋メタコメントを付与し、次回の再生成要否判定に使う。
    """
    out = cache_output_path(src, project_root=project_root, cache_dir=cache_dir)
    if out is None:
        return None
    if digest is None:
        digest = source_digest(src)
    out.parent.mkdir(parents=True, exist_ok=True)
    out.write_text(_cache_header(digest) + '\n' + markdown, encoding='utf-8', newline='\n')
    return out


def status_lock(lock_path):
    """1つの sources.lock.json を検査し (results, drift) を返す。

    results: [(artifactキー, status, role, path)]。status は
      unchanged … 記録指紋と現在の実体が一致
      changed   … 実体が変わっている
      missing   … ファイルが存在しない
    drift: changed / missing が1件でもあれば True。
    lock スキーマは {"artifacts": {"<キー>": {"sources": [{path, role, sha256}]}}}。
    トップレベルに直接 "sources" を持つ単一成果物形式も許容する。
    """
    with open(lock_path, encoding='utf-8') as fp:
        data = json.load(fp)
    groups = data['artifacts'].items() if 'artifacts' in data else [(None, data)]
    results = []
    drift = False
    for key, art in groups:
        for s in art.get('sources', []):
            path = s['path']
            role = s.get('role', '-')
            if not pathlib.Path(path).exists():
                st = 'missing'
            elif source_digest(path) == s.get('sha256'):
                st = 'unchanged'
            else:
                st = 'changed'
            if st != 'unchanged':
                drift = True
            results.append((key, st, role, path))
    return results, drift


def run_cache_command(ns, f):
    """preview / cache コマンドの1ファイル分。

    元ファイルが前回キャッシュ時から未変更(sha256 一致)なら、コストの高い
    読み取り(read_document)自体を省いてキャッシュを再利用する。
    """
    # --no-cache(preview のみ)は stdout だけ。キャッシュを読まず書かず常に再生成。
    if getattr(ns, 'no_cache', False):
        print(preview.to_markdown(read_document(f)))
        return

    out_path = cache_output_path(f, project_root=ns.project_root, cache_dir=ns.cache_dir)
    digest = source_digest(f)

    if not ns.force and cache_is_fresh(out_path, digest):
        if ns.cmd == 'preview':
            print(strip_cache_header(out_path.read_text(encoding='utf-8')))
        print(f'up-to-date: {out_path}',
              file=sys.stderr if ns.cmd == 'preview' else sys.stdout)
        return

    markdown = preview.to_markdown(read_document(f))
    if ns.cmd == 'preview':
        print(markdown)
    out = write_preview_cache(markdown, f, ns.project_root, ns.cache_dir, digest=digest)
    if out is None:
        print(f'WARN: project-root 外のためキャッシュをスキップしました: {f}', file=sys.stderr)
    else:
        print(f'cached: {out}', file=sys.stderr if ns.cmd == 'preview' else sys.stdout)


def main(argv=None):
    if hasattr(sys.stdout, 'reconfigure'):
        sys.stdout.reconfigure(encoding='utf-8')
    ap = argparse.ArgumentParser(prog='doctool', description=__doc__)
    ap.set_defaults(with_format=False)
    sub = ap.add_subparsers(dest='cmd', required=True)
    for cmd, help_ in (('dump', 'IR(JSON)を出力'),
                       ('preview', 'Markdownプレビューを出力し、既定でキャッシュにも保存'),
                       ('cache', 'Markdownプレビューをキャッシュへ保存'),
                       ('hash', 'ファイル実体のsha256指紋を出力(型不問)'),
                       ('status', 'lock記録の指紋を現在値と突合し変更を報告(changed/missingで終了コード1)'),
                       ('analyze', '雛形の記入ストラテジ(JSON)を出力'),
                       ('fill', '記入スペック(JSON)を雛形へ適用して docx を生成'),
                       ('fitrows', '行高上限で頭打ちのケース行を Excel 実測 ht に基づき分割・調整(Excel保存待ちは終了コード3)'),
                       ('excelsave', 'Excel(COM) で開いて保存し実測行高(ht)を書き戻させる(Windows 専用)'),
                       ('normalize', '表ごとに独立した自動採番になるよう numId を正規化(既存 docx を上書き)')):
        p = sub.add_parser(cmd, help=help_)
        if cmd == 'status':
            p.add_argument('--lock', nargs='+', required=True,
                           help='検査する sources.lock.json(複数可)')
        else:
            p.add_argument('files', nargs='+')
        if cmd == 'dump':
            p.add_argument('--format', dest='with_format', action='store_true',
                           help='書式オーバーレイ(runs/罫線/styleFormats 等)を付加')
        if cmd in ('preview', 'cache'):
            p.add_argument('--project-root', default='.',
                           help='キャッシュ配置の基準ディレクトリ(既定: カレントディレクトリ)')
            p.add_argument('--cache-dir', default=DEFAULT_CACHE_DIR,
                           help=f'Markdownキャッシュの出力先(既定: {DEFAULT_CACHE_DIR})')
        if cmd in ('preview', 'cache'):
            p.add_argument('--force', action='store_true',
                           help='元ファイルが未変更でもキャッシュを再生成する')
        if cmd == 'preview':
            p.add_argument('--no-cache', action='store_true',
                           help='Markdownプレビューを標準出力のみに出し、キャッシュへ保存しない')
        if cmd == 'fitrows':
            p.add_argument('--check', action='store_true',
                           help='検出・計画の表示のみ(ファイルを書き換えない)')
            p.add_argument('--auto', action='store_true',
                           help='「Excel で開いて保存」を excelsave(COM)で代行し完了まで自動実行'
                                '(Windows + デスクトップ版 Excel 専用)')
    ns = ap.parse_args(argv)

    if ns.cmd == 'fill':
        for f in ns.files:
            with open(f, encoding='utf-8') as fp:
                spec = json.load(fp)
            if str(spec['output']).lower().endswith(('.xlsx', '.xlsm')):
                import xlsx_writer
                writer = xlsx_writer
            else:
                import docx_writer
                writer = docx_writer
            for warning in writer.apply_spec(spec):
                print(f'WARN: {warning}', file=sys.stderr)
            print('generated:', spec['output'])
        return

    if ns.cmd == 'fitrows':
        if ns.auto and ns.check:
            print('エラー: --auto と --check は併用できません'
                  '(--auto は測定のために Excel 保存でファイルを更新します)', file=sys.stderr)
            return 2
        import xlsx_fitrows
        run_one = xlsx_fitrows.run_file_auto if ns.auto else xlsx_fitrows.run_file
        rc = 0
        for f in ns.files:
            rc = max(rc, run_one(f, check=ns.check))
        return rc

    if ns.cmd == 'excelsave':
        import xlsx_excelsave
        rc = 0
        for f in ns.files:
            try:
                xlsx_excelsave.excel_save(f)
                print('saved:', f)
            except xlsx_excelsave.ExcelSaveError as e:
                print(f'エラー: {e}', file=sys.stderr)
                rc = 1
        return rc

    if ns.cmd == 'normalize':
        import docx_writer
        for f in ns.files:
            changed = docx_writer.normalize_numbering_file(f)
            print(('normalized:' if changed else 'unchanged: '), f)
        return

    if ns.cmd == 'hash':
        # sha256sum 互換の "<hex>␠␠<path>" 形式。変更検出のベースライン照合に使う。
        for f in ns.files:
            print(f'{source_digest(f)}  {f}')
        return

    if ns.cmd == 'status':
        any_drift = False
        for lk in ns.lock:
            try:
                results, drift = status_lock(lk)
            except FileNotFoundError:
                print(f'WARN: lock が見つかりません: {lk}', file=sys.stderr)
                any_drift = True
                continue
            any_drift = any_drift or drift
            print(f'# {lk}')
            for key, st, role, path in results:
                keypart = f'[{key}] ' if key else ''
                print(f'{st:<9} {role:<10} {keypart}{path}')
        # 変更/欠落があれば終了コード1(スキルのゲートが機械的に分岐できる)
        return 1 if any_drift else 0

    for f in ns.files:
        if ns.cmd in ('preview', 'cache'):
            run_cache_command(ns, f)
            continue
        # analyze は混在書式の検出に書式オーバーレイが必要
        ir = read_document(f, with_format=ns.with_format or ns.cmd == 'analyze')
        if ns.cmd == 'analyze' and ir['source']['kind'] != 'docx':
            print(f'analyze は docx 雛形専用です: {f}', file=sys.stderr)
            continue
        if ns.cmd == 'dump':
            print(json.dumps(ir, ensure_ascii=False, indent=2))
        elif ns.cmd == 'analyze':
            print(json.dumps(analyzer.analyze_ir(ir), ensure_ascii=False, indent=2))


if __name__ == '__main__':
    sys.exit(main())
