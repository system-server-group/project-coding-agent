"""spec_integrity — テストケース仕様の参照整合チェッカー / 承認基準線(spec.lock)管理。

testgen-plan の「網羅の証明」(§8)と人手変更の関所検出を機械実行する。

照合内容:
  C1 前方向     : 導出判断メモの全行が ケースID または 対象外理由 を持つ
  C2 割当の実在 : メモの割当ケースID(機能横断参照を含む)が仕様書 xlsx に実在する
  C3 データ整合 : <機能>/data/ の孤児ファイル検出・仕様書が言及するパスの実在
  C4 採番連続   : セクション内のケース番号に欠番がない(警告)
  C5 逆引き     : xlsx の全ケースがメモ(または変更台帳の採用記録)から説明できる
  C6 対象外理由 : 対象外理由に E2E 以外での担保・再現不能の断定・1画面原則が書かれて
                  いない(警告。共有規範「目的と範囲」/「到達手段カタログ」との照合を促す)
  C7 データ事実 : ケース本文中の「<ファイル名>(<数値><単位>)」サイズ表記のうち、実施単位内の
                  実ファイルまたはシードCSVの宣言名にアンカーできたものをデータ実測と突合。
                  あわせてシードCSVの宣言サイズと素材ファイル実測の一致を検査する。
                  値の不一致=エラー、値は同じで表示精度のみ異なる=警告。
                  アンカーできない散文の言及は本検査の対象外(全数の保証はしない。散文側の
                  網羅は testgen-plan §8 のデータ突合サブエージェントが受け持つ)
  C8 記録の閉包 : メモ等の文中に現れる棚卸し行番号の参照(#n・#a〜#b。同一行内で直前に
                  機能フォルダ名があればそのメモ、無ければ自メモに解決)がすべて棚卸し表に
                  実在する。棚卸し表の欠番・行番号重複は警告。メモが言及する突合報告
                  (証拠ファイル)の実在も検査する。実施単位直下の文書(質問リスト等)の
                  機能修飾つき参照も対象。「やったと書いたが現物に無い」記帳を検出する
  C9 参照アンカー: 行アンカー付き横断参照「`<機能>` #<行>（<ケースID>）」を双方向に検証する
                  ——参照先メモに当該行が実在し、その行のケースID割当が当該ケースを含み、
                  ケースが参照先仕様書に実在する。ケース再採番による参照の意味ずれ
                  (番号は実在するが別内容を指す)を検出する
  C10 対割当    : 観点分類が「入力チェック」を含む棚卸し行(単項目・相関とも)に、正常系・
                  異常系のケースが対で割り当てられている(判定は仕様書の正常/異常系列。
                  横断参照先のケースも判定に含める。正常系は代表ケースの共有可=同じ
                  ケースIDを複数行に併記する)。正常系のみ=チェックが働くこと自体が未検証、
                  異常系のみ=正当入力の受理が未検証、というカバー漏れを検出する。
                  棚卸し表に観点分類列が無いことはエラー
  DIFF 基準線   : spec.lock.json と現 xlsx の差分(追加/削除/改変)を分類し「要裁定」として報告

コマンド:
  check <実施単位dir> [機能...] [--partial] [--json]
      照合を実行。整合エラーまたは未裁定差分があれば終了コード1。
      --partial は流用の部分再テスト用(欠落ケースへの割当・欠番を警告に降格)。
  lock  <実施単位dir> [機能...]
      spec.lock.json を更新(承認基準線の採取)。整合エラーが残る場合は拒否する。
      基準線差分(要裁定)は lock の更新対象そのものなので拒否理由にしない。
  facts <実施単位dir> [機能...] [--json]
      データ事実表を出力する(testgen-plan §7 の転記元。記憶や要約からの転記を防ぐ)。
      データ正本ごとに実バイト数・既定のサイズ表示文字列・CSVのデータ行数・整数列の
      値域を機械生成する。
  report <実施単位dir> [機能...]
      §8 突合記録の「機械実測」部を現物から生成する(突合記録の転記元)。棚卸し行数と
      内訳・ケース数・番号参照の閉包・アンカー付き横断参照・証拠ファイルの有無を
      機械観測して出力する。突合記録の統計はこの出力と突合報告(証拠ファイル)からの
      転記のみとし、意図・記憶からの記帳(「追補した」「解消済み」等の宣言だけの記録)を
      禁止する。

xlsx の読み取りは doctool dump(IR) を経由する(正本の読み書きは doctool に一元化)。
変更台帳(ケース変更台帳.md)の表は次の列を機械解釈する:
  | 日付 | 機能 | ケースNo | 種別 | 裁定 | 理由 |
  種別=追加 かつ 裁定=採用 → C5 の説明(メモ外ケースの容認)
  種別=削除 かつ 裁定=採用 → C2 の説明(欠落ケースへの割当の容認)
"""

import argparse
import csv
import hashlib
import json
import re
import subprocess
import sys
from datetime import datetime, timezone
from decimal import Decimal, ROUND_HALF_UP
from pathlib import Path

SCRIPT_DIR = Path(__file__).resolve().parent
DOCTOOL = SCRIPT_DIR.parents[2] / 'doctool' / 'scripts' / 'doctool' / 'doctool.py'

LOCK_NAME = 'spec.lock.json'
LOCK_FORMAT = 'testgen-spec-lock/1'
MEMO_NAME = '導出判断メモ.md'
JOURNAL_NAME = 'ケース変更台帳.md'

CASE_ID = re.compile(r'\d+-\d+')
CASE_ID_EXACT = re.compile(r'^\d+-\d+$')
SECTION_NO_EXACT = re.compile(r'^\d+$')
RANGE = re.compile(r'(\d+)-(\d+)\s*〜\s*(\d+)-(\d+)')
# 横断参照: `03_ログイン` 3-4 の形(バッククォート必須。対象外注釈の括弧内に現れる)
CROSS_REF = re.compile(r'`(\d{2}_[^`]+)`\s*(\d+-\d+)')
# C9: 行アンカー付き横断参照 `<機能>` #<棚卸し行>（<ケースID…>）。
# 参照先の行とケースの両方に固定するため、再採番で片側が変わると必ず検出される
ANCHORED_REF = re.compile(r'`(\d{2}_[^`]+)`\s*#(\d+)\s*[（(]([^）)]*)[）)]')
# C8: 棚卸し行番号の参照(#n)と範囲参照(#a〜#b)。#86a 等の枝番付き行番号は対象外
REF_RANGE = re.compile(r'#(\d+)\s*[〜~]\s*#?(\d+)(?![0-9A-Za-z])')
REF_NUM = re.compile(r'#(\d+)(?![0-9A-Za-z])')
# C8: 突合報告(証拠ファイル)への言及。機能フォルダ直下の相対名で解決する
EVIDENCE_PATH = re.compile(r'突合報告[^\s「」『』()（）、。:：;；|`]*\.md')
# n-m の紛らわしいダッシュ(ハイフン以外)
DASH_LOOKALIKE = re.compile(r'\d[‐‑–—−ー]\d')
# 実施単位相対のデータパス(規約: `_baseline/data/…`／`<機能>/data/…`)
DATA_PATH = re.compile(r'[^\s「」（）()、。：;；]*data/[^\s「」（）()、。：;；]+')
# C7: 本文中のサイズ表記「<ファイル名>(<数値><単位>)」。ファイル名(拡張子付き)に
# アンカーするため自由文全体の解釈はしない。単位の既定表示規則は format_size_default。
SIZE_TOKEN = re.compile(
    r'([^\s/\\|「」『』()（）、。:：;；]+\.[A-Za-z0-9]{1,5})\s*'
    r'\(\s*([0-9][0-9,]*(?:\.[0-9]+)?)\s*(B|KB|MB)\s*\)')
# C7: シードCSVの列ヘッダー判定(セル全体一致。汎用名のみ・プロジェクト固有名は持たない)
NAME_COL = re.compile(r'(?:file_?name|name|ファイル名)', re.IGNORECASE)
SIZE_COL = re.compile(r'(?:file_?size|size|サイズ|容量|ファイル容量)', re.IGNORECASE)
SOURCE_COL = re.compile(r'(?:file_?source|source|素材|素材ファイル)', re.IGNORECASE)
# C6: 対象外理由に書いてはならない表現(共有規範「目的と範囲」・到達手段カタログ)。
# E2E 以外への委譲はユーザー裁定事項、再現不能の断定はカタログ照合を経る必要がある。
# 1画面原則はスクロール・ページングが本質の観点に適用されない(テストデータ規範の適用除外・
# 到達手段カタログ 手段7)ため、対象外理由に用いることを禁止する。
# 空白を除去した理由文に対する部分一致で検出する。
FORBIDDEN_EXCLUSION = ['JUnit', '結合テスト', '単体テストで担保', 'E2E以外',
                       '目視に委ね', '再現できない', '実施できない', '確認できない',
                       '手段が設計上定義されていない', '手段がない',
                       '1画面原則', 'フレームに収まら']

PLAN_COLS = range(0, 7)  # A..G(No・正常/異常系・観点・前提条件・操作手順・期待する結果・参照データ)


# ---------------------------------------------------------------- findings

class Findings:
    """指摘の集約。kind: error=整合エラー / diff=基準線差分(要裁定) / warn=警告"""

    def __init__(self):
        self.items = []

    def add(self, kind, code, func, message):
        self.items.append({'kind': kind, 'code': code, 'func': func, 'message': message})

    def errors(self):
        return [i for i in self.items if i['kind'] == 'error']

    def diffs(self):
        return [i for i in self.items if i['kind'] == 'diff']

    def warns(self):
        return [i for i in self.items if i['kind'] == 'warn']


# ---------------------------------------------------------------- xlsx 読み取り

def sha256_file(path):
    h = hashlib.sha256()
    with open(path, 'rb') as f:
        for chunk in iter(lambda: f.read(1 << 16), b''):
            h.update(chunk)
    return h.hexdigest()


def doctool_dump(xlsx):
    proc = subprocess.run(
        [sys.executable, str(DOCTOOL), 'dump', str(xlsx)],
        capture_output=True, text=True, encoding='utf-8', errors='replace')
    if proc.returncode != 0:
        raise RuntimeError(f'doctool dump 失敗: {xlsx}\n{proc.stderr[:500]}')
    return json.loads(proc.stdout)


def cell_text(cell):
    return '\n'.join(b.get('text', '') for b in cell.get('blocks', []))


def read_spec(xlsx):
    """仕様書 xlsx を doctool IR から読み、ケース・セクション・データ言及を抽出する。

    返り値: {'cases': {id: {'row': n, 'hash': sha256, 'kind': 正常/異常系列の値}},
             'sections': {no: title}, 'dataRefs': set, 'duplicates': [id...],
             'sizeTokens': [[caseId, ファイル名, 数値文字列, 単位], ...]}
    """
    ir = doctool_dump(xlsx)
    cases, sections, data_refs, duplicates, size_tokens = {}, {}, set(), [], []
    for sheet in ir.get('sheets', []):
        for block in sheet.get('blocks', []):
            rows = {}
            for c in block.get('cells', []):
                rows.setdefault(c['row'], {})[c['col']] = c
            for rno in sorted(rows):
                cols = rows[rno]
                head = cell_text(cols[0]).strip() if 0 in cols else ''
                for c in cols.values():
                    for m in DATA_PATH.findall(cell_text(c)):
                        data_refs.add(m.strip())
                if CASE_ID_EXACT.match(head):
                    payload = [cell_text(cols[i]) if i in cols else '' for i in PLAN_COLS]
                    digest = hashlib.sha256(
                        json.dumps(payload, ensure_ascii=False).encode('utf-8')).hexdigest()
                    if head in cases:
                        duplicates.append(head)
                    else:
                        cases[head] = {'row': rno + 1, 'hash': digest,
                                       'kind': payload[1].strip()}
                    for text in payload:
                        for m in SIZE_TOKEN.finditer(text):
                            size_tokens.append([head, m.group(1), m.group(2), m.group(3)])
                elif SECTION_NO_EXACT.match(head) and 1 in cols:
                    title = cell_text(cols[1]).strip()
                    if title and head not in sections:
                        sections[head] = title
    return {'cases': cases, 'sections': sections,
            'dataRefs': data_refs, 'duplicates': duplicates, 'sizeTokens': size_tokens}


def classify_kind(text):
    """仕様書「正常/異常系」列の値を分類する(規定語彙は「正常」/「異常」。境界値は該当側に分類済み)。"""
    s = (text or '').strip()
    if '異常' in s:
        return '異常'
    if '正常' in s:
        return '正常'
    return None


# ---------------------------------------------------------------- メモ読み取り

def strip_parens(s):
    prev = None
    while prev != s:
        prev = s
        s = re.sub(r'（[^（）]*）', '', s)
        s = re.sub(r'\([^()]*\)', '', s)
    return s


def read_memo(memo_path):
    """導出判断メモの棚卸し表を解釈する。

    返り値: {'rows': [{'no', 'line', 'excluded', 'kind', 'ids', 'crossRefs', 'issues'}],
             'found': bool, 'hasKind': bool}
      kind      : 観点分類列の値(列が無ければ空文字。列の有無は hasKind)
      ids       : 当機能ケースIDの集合(範囲展開済み)
      crossRefs : [(機能名, ケースID, 行番号)] 機能横断参照(対象外注釈内を含む)
      issues    : [(種別, メッセージ)] 表記の解釈不能など
    """
    rows, found = [], False
    lines = memo_path.read_text(encoding='utf-8').splitlines()
    id_col = no_col = kind_col = None
    in_table = False
    for lineno, line in enumerate(lines, 1):
        s = line.strip()
        if not in_table:
            if s.startswith('|') and 'ケースID' in s and '#' in s:
                headers = [h.strip() for h in s.strip('|').split('|')]
                for i, h in enumerate(headers):
                    if h == '#':
                        no_col = i
                    if 'ケースID' in h:
                        id_col = i
                    if kind_col is None and '観点' in h:
                        kind_col = i
                if no_col is not None and id_col is not None:
                    in_table, found = True, True
            continue
        if not s.startswith('|'):
            break
        cells = [h.strip() for h in s.strip('|').split('|')]
        if all(re.fullmatch(r':?-{2,}:?', c) for c in cells if c):
            continue
        if len(cells) <= max(no_col, id_col):
            continue
        no, assign = cells[no_col], cells[id_col]
        kind = cells[kind_col] if kind_col is not None and len(cells) > kind_col else ''
        row = {'no': no, 'line': lineno, 'excluded': '対象外' in assign, 'kind': kind,
               'text': assign, 'ids': set(), 'crossRefs': [], 'issues': []}
        if DASH_LOOKALIKE.search(assign):
            row['issues'].append(('dash', f'ハイフンに似た別文字を含む: {assign}'))
        for m in CROSS_REF.finditer(assign):
            row['crossRefs'].append((m.group(1).strip(), m.group(2), lineno))
        if not row['excluded']:
            body = strip_parens(assign)
            def expand(m):
                s1, a, s2, b = (int(g) for g in m.groups())
                if s1 == s2 and b >= a:
                    row['ids'].update(f'{s1}-{i}' for i in range(a, b + 1))
                else:
                    row['issues'].append(('range', f'解釈できない範囲表記: {m.group(0)}'))
                return ' '
            body = RANGE.sub(expand, body)
            row['ids'].update(CASE_ID.findall(body))
        rows.append(row)
    return {'rows': rows, 'found': found, 'hasKind': kind_col is not None}


def _blank(s, start, end):
    """位置を保ったまま区間を空白化する(文脈解決の位置計算を崩さないため)。"""
    return s[:start] + ' ' * (end - start) + s[end:]


def scan_memo_text(text, own_func, func_names):
    """メモ等の全文から、棚卸し行番号参照・アンカー付き横断参照・証拠パスを抽出する(C8/C9)。

    行番号参照(#n・#a〜#b)は、同一行内でその参照より前に現れた機能フォルダ名が
    あればそのメモ、無ければ own_func(自メモ)に解決する。own_func が None の場合
    (実施単位直下の文書)、未修飾の参照は解決先が定まらないため返さない。

    返り値: (refs, anchored, evidence)
      refs     : [(解決先機能名 or None, 行番号, 行位置)]
      anchored : [(機能名, 棚卸し行番号, [ケースID...], 行位置)]
      evidence : [(相対パス, 行位置)]
    """
    refs, anchored, evidence = [], [], []
    names = sorted(func_names, key=len, reverse=True)
    for lineno, line in enumerate(text.splitlines(), 1):
        if '#' not in line and '突合報告' not in line:
            continue
        for m in EVIDENCE_PATH.finditer(line):
            evidence.append((m.group(0), lineno))
        work = line
        for m in ANCHORED_REF.finditer(line):
            anchored.append((m.group(1).strip(), int(m.group(2)),
                             CASE_ID.findall(m.group(3)), lineno))
            work = _blank(work, m.start(), m.end())
        # 文脈解決用の機能名出現位置は元の行から取る(アンカー内の機能名も文脈になる)
        mentions = []
        for fn in names:
            start = 0
            while True:
                i = line.find(fn, start)
                if i < 0:
                    break
                mentions.append((i, fn))
                start = i + len(fn)

        def ctx(pos):
            # 機能名は「直近・同一文内・近接」の場合のみ #n を修飾する。
            # 文境界(。)を跨いだり離れすぎた機能名で修飾すると、
            # 「`他機能` x-y でケース化する。…は #n で…」の #n(自メモ参照)を誤帰属する
            best, best_i = own_func, -1
            for i, fn in mentions:
                if i < pos and i > best_i:
                    gap = line[i + len(fn):pos]
                    if '。' not in gap and len(gap) <= 40:
                        best, best_i = fn, i
            return best

        for m in REF_RANGE.finditer(work):
            a, b = int(m.group(1)), int(m.group(2))
            fn = ctx(m.start())
            if fn is not None and a <= b and b - a <= 1000:
                refs.extend((fn, n, lineno) for n in range(a, b + 1))
            work = _blank(work, m.start(), m.end())
        for m in REF_NUM.finditer(work):
            fn = ctx(m.start())
            if fn is not None:
                refs.append((fn, int(m.group(1)), lineno))
    return refs, anchored, evidence


# ---------------------------------------------------------------- 変更台帳読み取り

def read_journal(journal_path):
    """ケース変更台帳の表から機械解釈対象(採用済みの追加・削除)を取り出す。"""
    adopted_add, adopted_del = set(), set()
    if not journal_path.exists():
        return adopted_add, adopted_del
    cols = None
    for line in journal_path.read_text(encoding='utf-8').splitlines():
        s = line.strip()
        if not s.startswith('|'):
            cols = None
            continue
        cells = [c.strip() for c in s.strip('|').split('|')]
        if '種別' in cells and 'ケースNo' in cells:
            cols = {name: i for i, name in enumerate(cells)}
            continue
        if cols is None or all(re.fullmatch(r':?-{2,}:?', c) for c in cells if c):
            continue
        try:
            func = cells[cols['機能']]
            case = cells[cols['ケースNo']]
            kind = cells[cols['種別']]
            verdict = cells[cols['裁定']]
        except (KeyError, IndexError):
            continue
        if '採用' not in verdict or '不採用' in verdict:
            continue
        for cid in CASE_ID.findall(case):
            if '追加' in kind:
                adopted_add.add((func, cid))
            if '削除' in kind:
                adopted_del.add((func, cid))
    return adopted_add, adopted_del


# ---------------------------------------------------------------- データ事実(C7・facts)

def format_size_default(n):
    """サイズ表示文字列の既定整形(1024進・小数点第二位で四捨五入して第一位まで)。

    プロジェクトの表示仕様が異なる場合は「テスト規約」ロールの定めに従い本既定を
    上書きしてよい(正本は本関数の一箇所)。
    """
    if n <= 1023:
        return f'{n}B'
    unit, div = ('KB', 1024) if n <= 1048575 else ('MB', 1048576)
    v = (Decimal(n) / Decimal(div)).quantize(Decimal('0.1'), rounding=ROUND_HALF_UP)
    return f'{v}{unit}'


def read_csv_rows(path):
    try:
        with open(path, encoding='utf-8-sig', newline='') as f:
            return list(csv.reader(f))
    except (OSError, UnicodeDecodeError, csv.Error):
        return []


def read_declared_sizes(csv_path):
    """データCSVから (宣言ファイル名→宣言サイズ) と (宣言名→素材ファイル名) を取り出す。

    名前列とサイズ列(ヘッダーのセル全体一致)を両方持つCSVのみ対象。該当しなければ空。
    同一宣言名に異なるサイズがある場合はその名前を除外する(特定不能)。
    """
    rows = read_csv_rows(csv_path)
    if not rows:
        return {}, {}
    header = [h.strip() for h in rows[0]]
    name_i = size_i = src_i = None
    for i, h in enumerate(header):
        if name_i is None and NAME_COL.fullmatch(h):
            name_i = i
        elif size_i is None and SIZE_COL.fullmatch(h):
            size_i = i
        elif src_i is None and SOURCE_COL.fullmatch(h):
            src_i = i
    if name_i is None or size_i is None:
        return {}, {}
    sizes, sources, conflicted = {}, {}, set()
    for r in rows[1:]:
        if len(r) <= max(name_i, size_i):
            continue
        name = r[name_i].strip()
        try:
            size = int(r[size_i].strip().replace(',', ''))
        except ValueError:
            continue
        if not name:
            continue
        if name in sizes and sizes[name] != size:
            conflicted.add(name)
        sizes[name] = size
        if src_i is not None and len(r) > src_i and r[src_i].strip():
            sources[name] = r[src_i].strip()
    for name in conflicted:
        sizes.pop(name, None)
        sources.pop(name, None)
    return sizes, sources


def collect_data_facts(unit_dir, all_funcs):
    """C7 の突合先を集める。

    返り値: (file_bytes, declared, seed_checks)
      file_bytes  : {basename: {scope: 実バイト数}} 実ファイル(scope=機能 or '_baseline')
      declared    : {basename: {scope: 宣言サイズ}} シードCSVの宣言値
      seed_checks : [(scope, csv名, 宣言名, 宣言サイズ, 素材basename)] 宣言⇄素材の突合対象
    """
    scopes = {'_baseline': unit_dir / '_baseline' / 'data'}
    for func in all_funcs:
        scopes[func] = unit_dir / func / 'data'
    file_bytes, declared, seed_checks = {}, {}, []
    for scope, d in scopes.items():
        if not d.is_dir():
            continue
        for f in sorted(d.iterdir()):
            if not f.is_file():
                continue
            file_bytes.setdefault(f.name, {})[scope] = f.stat().st_size
            if f.suffix.lower() == '.csv':
                sizes, sources = read_declared_sizes(f)
                for name, size in sizes.items():
                    declared.setdefault(name, {})[scope] = size
                    seed_checks.append((scope, f.name, name, size, sources.get(name)))
    return file_bytes, declared, seed_checks


def resolve_bytes(name, func, file_bytes, declared):
    """本文中のファイル名を正のバイト数へ解決する(実ファイル優先→シード宣言値)。

    解決順: 当該機能 → _baseline → 実施単位内で一意。同名異サイズが複数機能に
    跨がり特定できない場合は None(アンカー不能として検査対象外)。
    """
    for pool in (file_bytes, declared):
        scoped = pool.get(name)
        if not scoped:
            continue
        if func in scoped:
            return scoped[func]
        if '_baseline' in scoped:
            return scoped['_baseline']
        vals = set(scoped.values())
        if len(vals) == 1:
            return vals.pop()
        return None
    return None


def csv_facts(path):
    """facts 用: CSVのデータ行数・ヘッダー・整数列の値域。CSVでなければ None。"""
    rows = read_csv_rows(path)
    if not rows:
        return None
    header = [h.strip() for h in rows[0]]
    body = [r for r in rows[1:] if any(c.strip() for c in r)]
    int_ranges = {}
    for i, h in enumerate(header):
        vals = []
        for r in body:
            v = r[i].strip() if len(r) > i else ''
            if not re.fullmatch(r'-?\d+', v):
                vals = None
                break
            vals.append(int(v))
        if vals:
            int_ranges[h] = (min(vals), max(vals))
    return {'dataRows': len(body), 'headers': header, 'intRanges': int_ranges}


# ---------------------------------------------------------------- 実施単位の走査

def discover_functions(unit_dir):
    funcs = {}
    for d in sorted(unit_dir.iterdir()):
        if not d.is_dir() or not re.match(r'^\d{2}_', d.name):
            continue
        xlsx = sorted(d.glob('*_テスト仕様書.xlsx'))
        if xlsx:
            funcs[d.name] = xlsx[0]
    return funcs


def load_lock(unit_dir):
    p = unit_dir / LOCK_NAME
    if not p.exists():
        return None
    return json.loads(p.read_text(encoding='utf-8'))


def get_spec(unit_dir, func, xlsx, lock, cache):
    """dump 結果(lock の指紋一致時は lock 記録)を返す。cache はメモ化。"""
    if func in cache:
        return cache[func]
    digest = sha256_file(xlsx)
    entry = (lock or {}).get('functions', {}).get(func)
    # sizeTokens/caseKinds を持たない旧形式の lock はショートカットに使わない(C7/C10 に必要)
    if (entry and entry.get('xlsxSha256') == digest
            and 'sizeTokens' in entry and 'caseKinds' in entry):
        kinds = entry.get('caseKinds', {})
        spec = {'cases': {cid: {'row': None, 'hash': h, 'kind': kinds.get(cid, '')}
                          for cid, h in entry['cases'].items()},
                'sections': dict(entry.get('sections', {})),
                'dataRefs': set(entry.get('dataRefs', [])),
                'duplicates': [],
                'sizeTokens': [list(t) for t in entry.get('sizeTokens', [])]}
    else:
        spec = read_spec(xlsx)
    spec['xlsxSha256'] = digest
    cache[func] = spec
    return spec


def load_all_memos(unit_dir, all_funcs):
    """全機能の導出判断メモを読み、(解釈結果, 全文) の対を返す(C8/C9 の解決先)。"""
    memos_all, memo_texts = {}, {}
    for func in all_funcs:
        mp = unit_dir / func / MEMO_NAME
        if mp.exists():
            memos_all[func] = read_memo(mp)
            memo_texts[func] = mp.read_text(encoding='utf-8')
        else:
            memos_all[func] = {'rows': [], 'found': False, 'hasKind': False}
            memo_texts[func] = ''
    return memos_all, memo_texts


def index_memo_rows(memos_all):
    """棚卸し表を行番号で索引化する。

    返り値: (rows_by_func {func: {行番号}}, ids_by_row {func: {行番号: ケースID集合}},
             dup_rows {func: [重複行番号]})
    """
    rows_by_func, ids_by_row, dup_rows = {}, {}, {}
    for func, memo in memos_all.items():
        nums, ids_map, dups = set(), {}, []
        for row in memo['rows']:
            if not row['no'].isdigit():
                continue
            n = int(row['no'])
            if n in nums:
                dups.append(n)
            nums.add(n)
            ids_map[n] = row['ids'] | {cid for _, cid, _ in row['crossRefs']}
        rows_by_func[func] = nums
        ids_by_row[func] = ids_map
        dup_rows[func] = dups
    return rows_by_func, ids_by_row, dup_rows


UNIT_DOCS = ('質問リスト.md', 'README.md', '変更影響分析.md', JOURNAL_NAME)


# ---------------------------------------------------------------- 照合本体

def run_checks(unit_dir, target_funcs, partial, findings):
    all_funcs = discover_functions(unit_dir)
    if not all_funcs:
        raise RuntimeError(f'機能フォルダ(仕様書 xlsx を含む)が見つからない: {unit_dir}')
    unknown = [f for f in target_funcs if f not in all_funcs]
    if unknown:
        raise RuntimeError(f'指定された機能が実施単位に存在しない: {", ".join(unknown)}')
    scoped = {f: all_funcs[f] for f in (target_funcs or all_funcs)}

    lock = load_lock(unit_dir)
    adopted_add, adopted_del = read_journal(unit_dir / JOURNAL_NAME)
    cache = {}

    # 言及プール(孤児検出用)は実施単位内の全機能から集める
    for func, xlsx in all_funcs.items():
        get_spec(unit_dir, func, xlsx, lock, cache)
    mention_pool = set()
    for spec in cache.values():
        mention_pool.update(spec['dataRefs'])

    # C7 の突合先(実ファイル・シード宣言値)は実施単位全体から集める
    file_bytes, declared, seed_checks = collect_data_facts(unit_dir, all_funcs)

    # C8/C9 の解決先: 全機能のメモ(棚卸し行番号→ケースID割当)を先に読む
    memos_all, memo_texts = load_all_memos(unit_dir, all_funcs)
    rows_by_func, ids_by_row, dup_rows = index_memo_rows(memos_all)

    stats = {}
    for func, xlsx in scoped.items():
        spec = cache[func]
        cases = spec['cases']
        for dup in spec['duplicates']:
            findings.add('error', 'DUP', func, f'ケースNo {dup} が仕様書内で重複している')

        memo = memos_all[func]
        if not memo['found']:
            findings.add('error', 'C1', func, f'{MEMO_NAME} の棚卸し表が見つからない')
        elif not memo.get('hasKind'):
            findings.add('error', 'C10', func,
                         '棚卸し表に観点分類列が無い(入力チェックの対割当検査を適用できない。'
                         '列を設け、入力チェック系の行は観点分類に「入力チェック」を含める)')

        claimed = set()
        for row in memo['rows']:
            for kind, msg in row['issues']:
                findings.add('warn', 'C1', func, f'メモ #{row["no"]} (L{row["line"]}): {msg}')
            if not row['excluded'] and not row['ids']:
                findings.add('error', 'C1', func,
                             f'メモ #{row["no"]} (L{row["line"]}) にケースIDも対象外理由もない')
            # C6: 対象外理由の禁止表現(E2E以外への委譲・再現不能の断定)
            if row['excluded']:
                compact = row['text'].replace(' ', '').replace('　', '')
                hits = [t for t in FORBIDDEN_EXCLUSION if t in compact]
                if hits:
                    findings.add('warn', 'C6', func,
                                 f'メモ #{row["no"]} (L{row["line"]}) の対象外理由に「'
                                 + '」「'.join(hits)
                                 + '」— E2E以外での担保・再現不能の断定・1画面原則は対象外理由に'
                                 'できない(到達手段カタログと照合し、ケース化または質問リストで'
                                 '裁定を得る。スクロール・ページングが本質の観点は手段7=連続'
                                 'フレーム規格でケース化する)')
            # C2: 当機能への割当の実在
            for cid in sorted(row['ids']):
                if cid in cases:
                    claimed.add(cid)
                    continue
                if (func, cid) in adopted_del:
                    claimed.add(cid)  # 台帳で削除採用済み(説明あり)
                    continue
                kind = 'warn' if partial else 'error'
                findings.add(kind, 'C2', func,
                             f'メモ #{row["no"]} (L{row["line"]}) の割当先 {cid} が仕様書に存在しない')
            # C2: 機能横断参照の実在
            for ref_func, cid, lineno in row['crossRefs']:
                if ref_func not in all_funcs:
                    findings.add('warn', 'C2', func,
                                 f'メモ #{row["no"]} (L{lineno}) の参照先機能 {ref_func} が実施単位に無い')
                    continue
                ref_spec = get_spec(unit_dir, ref_func, all_funcs[ref_func], lock, cache)
                if cid not in ref_spec['cases']:
                    findings.add('error', 'C2', func,
                                 f'メモ #{row["no"]} (L{lineno}) の横断参照 {ref_func} {cid} が存在しない')
            # C10: 入力チェック行の正常・異常の対割当(判定は仕様書の正常/異常系列。
            # 正常系のみ=チェックが働くこと自体が未検証/異常系のみ=正当入力の受理が未検証)
            if (not row['excluded'] and '入力チェック' in row.get('kind', '')
                    and (row['ids'] or row['crossRefs'])):
                have, unknown = set(), []
                pools = [(cases, cid) for cid in sorted(row['ids'])]
                pools += [(get_spec(unit_dir, rf, all_funcs[rf], lock, cache)['cases'], cid)
                          for rf, cid, _ in row['crossRefs'] if rf in all_funcs]
                for pool, cid in pools:
                    if cid not in pool:
                        continue  # 実在しない割当は C2 が指摘する(partial では欠落が正常)
                    k = classify_kind(pool[cid]['kind'])
                    if k:
                        have.add(k)
                    else:
                        unknown.append(cid)
                if unknown:
                    findings.add('warn', 'C10', func,
                                 f'メモ #{row["no"]} (L{row["line"]}) の割当先 '
                                 + '・'.join(unknown)
                                 + ' の正常/異常系欄を解釈できない(規定語彙は「正常」/「異常」)')
                missing = [s for s in ('正常', '異常') if s not in have]
                if missing:
                    sev = 'warn' if partial else 'error'
                    findings.add(sev, 'C10', func,
                                 f'メモ #{row["no"]} (L{row["line"]}) の入力チェック行に'
                                 + '・'.join(f'{s}系' for s in missing)
                                 + 'のケース割当が無い——正常・異常を対で割り当てる(正常系は'
                                 '代表ケースの共有可。異常系を設計できない場合は到達手段'
                                 'カタログと照合し、質問リストで裁定を得る)')

        # C8: 記録の閉包(文中の棚卸し行番号参照・証拠ファイル言及が現物に実在するか)
        own_nums = rows_by_func.get(func, set())
        if own_nums:
            gap = sorted(set(range(min(own_nums), max(own_nums) + 1)) - own_nums)
            if gap:
                findings.add('warn', 'C8', func,
                             '棚卸し表に欠番: ' + '・'.join(f'#{n}' for n in gap))
        for n in sorted(dup_rows.get(func, [])):
            findings.add('warn', 'C8', func, f'棚卸し表の行番号 #{n} が重複している')
        refs, anchored, evidence = scan_memo_text(
            memo_texts.get(func, ''), func, list(all_funcs))
        seen_refs = set()
        for rf, n, lineno in refs:
            if (rf, n) in seen_refs:
                continue
            seen_refs.add((rf, n))
            pool = rows_by_func.get(rf)
            if pool is None:
                findings.add('warn', 'C8', func,
                             f'メモ L{lineno} の参照先機能 {rf} が実施単位に無い(#{n})')
            elif n not in pool:
                where = '自メモ' if rf == func else f'{rf} のメモ'
                tail = f'(表の終端: #{max(pool)})' if pool else '(棚卸し表なし)'
                findings.add('error', 'C8', func,
                             f'メモ L{lineno} が参照する {where} の棚卸し行 #{n} が存在しない{tail}'
                             '——記録が宣言だけで現物に反映されていない疑い')
        for path, lineno in evidence:
            if not (unit_dir / func / path).exists():
                findings.add('error', 'C8', func,
                             f'メモ L{lineno} が参照する証拠ファイルが実在しない: {func}/{path}')

        # C9: 行アンカー付き横断参照の双方向検証(行の実在×行のケースID割当×ケースの実在)
        for rf, rowno, cids, lineno in anchored:
            if rf not in all_funcs:
                findings.add('warn', 'C9', func,
                             f'メモ L{lineno} のアンカー参照先機能 {rf} が実施単位に無い')
                continue
            if rowno not in rows_by_func.get(rf, set()):
                findings.add('error', 'C9', func,
                             f'メモ L{lineno} のアンカー参照 {rf} #{rowno} の棚卸し行が存在しない')
                continue
            row_ids = ids_by_row[rf].get(rowno, set())
            ref_spec = get_spec(unit_dir, rf, all_funcs[rf], lock, cache)
            if not cids:
                findings.add('warn', 'C9', func,
                             f'メモ L{lineno} のアンカー参照 {rf} #{rowno} にケースIDが無い')
            for cid in cids:
                if cid not in row_ids:
                    have = '・'.join(sorted(row_ids, key=case_sort_key)) or '(対象外行)'
                    findings.add('error', 'C9', func,
                                 f'メモ L{lineno} のアンカー参照 {rf} #{rowno}（{cid}）: '
                                 f'参照先行のケースID割当は {have} で {cid} を含まない'
                                 '——再採番等による参照の意味ずれの疑い')
                elif cid not in ref_spec['cases']:
                    findings.add('error', 'C9', func,
                                 f'メモ L{lineno} のアンカー参照 {rf} #{rowno}（{cid}）: '
                                 f'ケース {cid} が {rf} の仕様書に存在しない')

        # C5: 逆引き(仕様書の全ケースに導出根拠があるか)
        for cid in sorted(cases, key=case_sort_key):
            if cid not in claimed and (func, cid) not in adopted_add:
                findings.add('error', 'C5', func,
                             f'ケース {cid} はメモのどの導出単位からも割り当てられていない(変更台帳にも採用記録なし)')

        # C4: セクション内採番の連続性(警告)
        by_sec = {}
        for cid in cases:
            sec, no = cid.split('-')
            by_sec.setdefault(int(sec), set()).add(int(no))
        for sec, nums in sorted(by_sec.items()):
            missing = sorted(set(range(1, max(nums) + 1)) - nums)
            if missing:
                gaps = '・'.join(f'{sec}-{n}' for n in missing)
                findings.add('warn', 'C4', func, f'セクション {sec} に欠番: {gaps}')

        # C3: データ整合
        data_dir = unit_dir / func / 'data'
        if data_dir.is_dir():
            for f in sorted(data_dir.iterdir()):
                if f.is_file() and f'{func}/data/{f.name}' not in mention_pool:
                    findings.add('error', 'C3', func,
                                 f'孤児データファイル: {func}/data/{f.name} はどの仕様書からも参照されていない')
        for ref in sorted(spec['dataRefs']):
            rel = ref if not ref.startswith('data/') else f'{func}/{ref}'
            if not (unit_dir / rel).exists():
                findings.add('error', 'C3', func, f'仕様書が参照するパスが実在しない: {ref}')

        # C7: データ事実整合(既知の名前にアンカーできたサイズ表記のみ。散文の網羅は §8 が担う)
        seen_c7 = set()
        for case_id, name, num, unit in spec.get('sizeTokens', []):
            actual = resolve_bytes(name, func, file_bytes, declared)
            if actual is None:
                continue
            claim = num.replace(',', '') + unit
            key = (case_id, name, claim)
            if key in seen_c7:
                continue
            seen_c7.add(key)
            expected = format_size_default(actual)
            if claim == expected:
                continue
            if claim == re.sub(r'\.0(?=[KM]B$)', '', expected):
                findings.add('warn', 'C7', func,
                             f'ケース {case_id}: 「{name}({num}{unit})」は値は正しいが'
                             f'表示精度が既定表示({expected})と異なる')
            else:
                findings.add('error', 'C7', func,
                             f'ケース {case_id}: 「{name}({num}{unit})」がデータ実測と'
                             f'不一致(実測 {actual} バイト → 表示 {expected})')

        # DIFF: 承認基準線との突合
        entry = (lock or {}).get('functions', {}).get(func)
        if entry:
            base = entry.get('cases', {})
            if spec['xlsxSha256'] != entry.get('xlsxSha256'):
                for cid in sorted(set(cases) - set(base), key=case_sort_key):
                    findings.add('diff', 'ADD', func,
                                 f'ケース {cid} が承認基準線に無い(承認後の追加。理由を確認し台帳へ記帳のこと)')
                for cid in sorted(set(base) - set(cases), key=case_sort_key):
                    findings.add('diff', 'DEL', func,
                                 f'ケース {cid} が仕様書から消えている(承認後の削除。裁定のこと)')
                for cid in sorted(set(cases) & set(base), key=case_sort_key):
                    if cases[cid]['hash'] != base[cid]:
                        findings.add('diff', 'MOD', func,
                                     f'ケース {cid} の内容が承認基準線から変わっている(承認後の改変。裁定のこと)')
                base_sec = entry.get('sections', {})
                for sec in sorted(set(spec['sections']) | set(base_sec), key=int):
                    if spec['sections'].get(sec) != base_sec.get(sec):
                        findings.add('diff', 'MOD', func,
                                     f'セクション {sec} の見出しが基準線と異なる'
                                     f'(基準線: {base_sec.get(sec)!r} / 現在: {spec["sections"].get(sec)!r})')
        elif lock is not None:
            findings.add('warn', 'LOCK', func, 'spec.lock に本機能の基準線が無い(未採取)')

        stats[func] = {'cases': len(cases), 'memoRows': len(memo['rows']),
                       'baseline': bool(entry)}

    # C7: シードCSVの宣言サイズ ⇄ 素材ファイル実測の突合(データ内・データ間の検査)
    for scope, csv_name, name, size, src in seed_checks:
        if target_funcs and scope not in target_funcs and scope != '_baseline':
            continue
        if not src:
            continue
        actual = resolve_bytes(src, scope, file_bytes, {})
        if actual is None:
            findings.add('warn', 'C7', scope,
                         f'{csv_name}: 「{name}」の素材 {src} が実施単位内に見つからない'
                         '(宣言サイズを検証できない)')
        elif size != actual:
            findings.add('error', 'C7', scope,
                         f'{csv_name}: 「{name}」の宣言サイズ {size} が素材 {src} の実測 '
                         f'{actual} バイトと不一致')

    # C8: 実施単位直下の文書(質問リスト等)の機能修飾つき番号参照の閉包
    if not target_funcs:
        for name in UNIT_DOCS:
            p = unit_dir / name
            if not p.exists():
                continue
            refs, _, _ = scan_memo_text(
                p.read_text(encoding='utf-8'), None, list(all_funcs))
            seen = set()
            for rf, n, lineno in refs:
                if (rf, n) in seen:
                    continue
                seen.add((rf, n))
                pool = rows_by_func.get(rf)
                if pool is not None and n not in pool:
                    findings.add('error', 'C8', name,
                                 f'L{lineno} が参照する {rf} の棚卸し行 #{n} が存在しない')

    # _baseline の孤児(共有物なので警告どまり)
    baseline_dir = unit_dir / '_baseline' / 'data'
    if baseline_dir.is_dir() and not target_funcs:
        for f in sorted(baseline_dir.iterdir()):
            if f.is_file() and f'_baseline/data/{f.name}' not in mention_pool:
                findings.add('warn', 'C3', '_baseline',
                             f'_baseline/data/{f.name} はどの仕様書からも参照されていない')

    return stats, cache, all_funcs


def case_sort_key(cid):
    a, b = cid.split('-')
    return (int(a), int(b))


# ---------------------------------------------------------------- lock 更新

def update_lock(unit_dir, target_funcs, cache, all_funcs):
    lock = load_lock(unit_dir) or {'format': LOCK_FORMAT, 'functions': {}}
    scoped = target_funcs or list(all_funcs)
    now = datetime.now(timezone.utc).astimezone().isoformat(timespec='seconds')
    for func in scoped:
        spec = cache[func]
        lock['functions'][func] = {
            'xlsx': f'{func}/{all_funcs[func].name}',
            'xlsxSha256': spec['xlsxSha256'],
            'lockedAt': now,
            'sections': {k: spec['sections'][k] for k in sorted(spec['sections'], key=int)},
            'cases': {cid: spec['cases'][cid]['hash']
                      for cid in sorted(spec['cases'], key=case_sort_key)},
            'caseKinds': {cid: spec['cases'][cid].get('kind', '')
                          for cid in sorted(spec['cases'], key=case_sort_key)},
            'dataRefs': sorted(spec['dataRefs']),
            'sizeTokens': [list(t) for t in spec.get('sizeTokens', [])],
        }
    lock['functions'] = dict(sorted(lock['functions'].items()))
    out = unit_dir / LOCK_NAME
    out.write_text(json.dumps(lock, ensure_ascii=False, indent=2) + '\n', encoding='utf-8')
    return out


# ---------------------------------------------------------------- facts(データ事実表)

def cmd_facts(unit_dir, target_funcs, as_json):
    """データ事実表を出力する(testgen-plan §7 の転記元)。

    本文に書くデータ由来の値(サイズ表示・件数・ID帯)はこの出力から転記する。
    記憶・要約からの転記による事実ドリフト(実物と異なる値の焼き込み)を防ぐ。
    """
    all_funcs = discover_functions(unit_dir)
    unknown = [f for f in target_funcs if f not in all_funcs]
    if unknown:
        raise RuntimeError(f'指定された機能が実施単位に存在しない: {", ".join(unknown)}')
    scopes = ['_baseline'] + sorted(target_funcs or all_funcs)
    facts = []
    for scope in scopes:
        d = unit_dir / scope / 'data'
        if not d.is_dir():
            continue
        for f in sorted(d.iterdir()):
            if not f.is_file():
                continue
            n = f.stat().st_size
            entry = {'path': f'{scope}/data/{f.name}', 'bytes': n,
                     'sizeDisplay': format_size_default(n)}
            if f.suffix.lower() == '.csv':
                cf = csv_facts(f)
                if cf:
                    entry['dataRows'] = cf['dataRows']
                    entry['headers'] = cf['headers']
                    entry['intRanges'] = {k: f'{lo}〜{hi}'
                                          for k, (lo, hi) in cf['intRanges'].items()}
            facts.append(entry)
    if as_json:
        print(json.dumps(facts, ensure_ascii=False, indent=2))
        return
    for e in facts:
        line = f'{e["path"]}  実測={e["bytes"]}バイト  サイズ表示={e["sizeDisplay"]}'
        if 'dataRows' in e:
            line += f'  データ行={e["dataRows"]}'
            if e['intRanges']:
                ranges = ' '.join(f'{k}={v}' for k, v in e['intRanges'].items())
                line += f'  整数列: {ranges}'
        print(line)
    print(f'---\n{len(facts)} ファイル(値はこの表から転記する。記憶・要約からの転記をしない)')


# ---------------------------------------------------------------- report(突合記録の機械実測)

def cmd_report(unit_dir, target_funcs):
    """§8 突合記録の「機械実測」部を現物から生成する(突合記録の統計の転記元)。

    突合記録に書く統計はこの出力からの転記のみとし、意図・記憶からの記帳
    (「追補した」「解消済み」等の宣言だけの記録)を禁止する。列挙件数・対応付け件数など
    機械観測できない値は、突合報告(証拠ファイル)からの転記のみとする。
    """
    all_funcs = discover_functions(unit_dir)
    unknown = [f for f in target_funcs if f not in all_funcs]
    if unknown:
        raise RuntimeError(f'指定された機能が実施単位に存在しない: {", ".join(unknown)}')
    lock = load_lock(unit_dir)
    cache = {}
    memos_all, memo_texts = load_all_memos(unit_dir, all_funcs)
    rows_by_func, ids_by_row, dup_rows = index_memo_rows(memos_all)
    for func in (target_funcs or sorted(all_funcs)):
        spec = get_spec(unit_dir, func, all_funcs[func], lock, cache)
        rows = memos_all[func]['rows']
        assigned = sum(1 for r in rows if not r['excluded'])
        nums = rows_by_func.get(func, set())
        gap = sorted(set(range(min(nums), max(nums) + 1)) - nums) if nums else []
        refs, anchored, _ = scan_memo_text(memo_texts.get(func, ''), func, list(all_funcs))
        bad = sorted({(rf, n) for rf, n, _ in refs
                      if rf in rows_by_func and n not in rows_by_func[rf]})
        rng = f'#{min(nums)}〜#{max(nums)}' if nums else '—'
        gap_s = '欠番なし' if not gap else '欠番: ' + '・'.join(f'#{n}' for n in gap)
        bad_s = 'すべて実在' if not bad else (
            '実在しない参照: ' + '・'.join(f'{rf} #{n}' for rf, n in bad))
        ev_files = sorted(f.name for f in (unit_dir / func).glob('突合報告*.md'))
        ev = ['・'.join(ev_files)] if ev_files else ['なし']
        print(f'### {func}(spec_integrity report による機械実測)')
        print('| 項目 | 実測 |')
        print('| --- | --- |')
        print(f'| 棚卸し表 | {len(rows)} 行({rng}、{gap_s}) |')
        print(f'| 内訳 | ケースID割当 {assigned} 行/対象外 {len(rows) - assigned} 行 |')
        print(f'| 仕様書ケース | {len(spec["cases"])} 件(セクション {len(spec["sections"])}) |')
        print(f'| メモ内の棚卸し行番号参照 | {len({(rf, n) for rf, n, _ in refs})} 件({bad_s}) |')
        print(f'| アンカー付き横断参照 | {len(anchored)} 件 |')
        print(f'| 突合報告(証拠ファイル) | {"・".join(ev)} |')
        print()
    print('---')
    print('上記は現物からの機械実測(この出力からの転記のみ可)。列挙件数・対応付け件数・')
    print('【対応行なし】/【不一致】の解消は、突合報告(証拠ファイル)を保存した上でそこからの')
    print('転記のみとする。「解消済み」と記す前に check を再実行し指摘ゼロを確認すること。')


# ---------------------------------------------------------------- 出力

def print_report(stats, findings, as_json):
    if as_json:
        print(json.dumps({'stats': stats, 'findings': findings.items},
                         ensure_ascii=False, indent=2))
        return
    for func, st in stats.items():
        base = '基準線あり' if st['baseline'] else '基準線なし'
        print(f'{func}: ケース {st["cases"]} 件 / メモ導出単位 {st["memoRows"]} 行 / {base}')
    for i in findings.items:
        tag = {'error': 'ERROR', 'diff': '要裁定', 'warn': 'WARN'}[i['kind']]
        print(f'[{tag}][{i["code"]}] {i["func"]}: {i["message"]}')
    ne, nd, nw = len(findings.errors()), len(findings.diffs()), len(findings.warns())
    print(f'---\n整合エラー {ne} / 要裁定差分 {nd} / 警告 {nw}')


def main(argv=None):
    if hasattr(sys.stdout, 'reconfigure'):
        sys.stdout.reconfigure(encoding='utf-8')
    ap = argparse.ArgumentParser(prog='spec_integrity', description=__doc__)
    sub = ap.add_subparsers(dest='cmd', required=True)
    for cmd in ('check', 'lock', 'facts', 'report'):
        p = sub.add_parser(cmd)
        p.add_argument('unit_dir', help='実施単位ディレクトリ')
        p.add_argument('functions', nargs='*', help='機能フォルダ名(省略時は全機能)')
        if cmd == 'check':
            p.add_argument('--partial', action='store_true',
                           help='流用の部分再テスト(欠落割当・欠番を警告へ降格)')
        if cmd in ('check', 'facts'):
            p.add_argument('--json', action='store_true')
    ns = ap.parse_args(argv)

    unit_dir = Path(ns.unit_dir)
    if not unit_dir.is_dir():
        print(f'エラー: 実施単位ディレクトリが無い: {unit_dir}', file=sys.stderr)
        return 2
    if not DOCTOOL.exists():
        print(f'エラー: doctool が見つからない: {DOCTOOL}', file=sys.stderr)
        return 2

    if ns.cmd == 'facts':
        try:
            cmd_facts(unit_dir, ns.functions, ns.json)
        except RuntimeError as e:
            print(f'エラー: {e}', file=sys.stderr)
            return 2
        return 0

    if ns.cmd == 'report':
        try:
            cmd_report(unit_dir, ns.functions)
        except RuntimeError as e:
            print(f'エラー: {e}', file=sys.stderr)
            return 2
        return 0

    findings = Findings()
    try:
        stats, cache, all_funcs = run_checks(
            unit_dir, ns.functions, getattr(ns, 'partial', False), findings)
    except RuntimeError as e:
        print(f'エラー: {e}', file=sys.stderr)
        return 2

    if ns.cmd == 'check':
        print_report(stats, findings, ns.json)
        return 1 if (findings.errors() or findings.diffs()) else 0

    # lock: 整合エラーが残る間は基準線を採らない(要裁定差分は lock で解消されるので拒否しない)
    if findings.errors():
        print_report(stats, findings, False)
        print('拒否: 整合エラーが残っています。是正(または台帳への記帳)後に再実行してください。')
        return 1
    out = update_lock(unit_dir, ns.functions, cache, all_funcs)
    print(f'基準線を更新しました: {out}')
    for func in (ns.functions or all_funcs):
        print(f'  {func}: ケース {len(cache[func]["cases"])} 件')
    return 0


if __name__ == '__main__':
    sys.exit(main())
