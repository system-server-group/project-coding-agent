"""doctool xlsx 行高調整(fitrows) — Excel が保存した実測行高(ht)に基づいて、
行高上限(409.5pt)で頭打ちになったケース行を複数行に分割し、全文を表示可能にする。

方式(Excel COM やフォントメトリクス計算は使わない):
  Excel は customHeight の無い行の高さを開いた時に内容へ合わせて再計算し、保存時に
  ht 属性へ実測値を書き戻す。ただし単一行は上限 409.5pt で頭打ちになり、結合セルは
  再計算の対象外。そこで本モジュールは「Excel で開いて保存する」ことを測定手段として、
  頭打ちの検出と、明示改行単位の断片(単体では頭打ちしない)の実測合算を行う。
  折返しは明示改行を跨がないため、断片高さの合算は真の必要高と一致する。

  なお customHeight="1" の行の ht は内容と無関係な明示値なので、頭打ち判定(ht>=409.4)では
  見切れを検出できない。そこで測定に先立ち、固定高のケース行は元の高さを退避したうえで
  いったん自動高さへ戻し、Excel に実測させる(round0)。

運用フロー(doctool fitrows <spec.json> で実行):
  1. doctool fill 等で xlsx を生成し、Excel で開いて Ctrl+S で保存する
  2. fitrows 実行(round0)
     - 固定高(customHeight)のケース行があれば、元の高さを表の下(印刷範囲外)へ退避行として
       書き込んでから固定を解除し、「Excel で開いて保存して再実行」を案内する(終了コード3)
  3. Excel で開いて保存 → fitrows 再実行(round0b → round1)
     - 退避行の実測 ht を回収し、元の高さで足りる行は元へ復元(意図した余裕は維持)、
       足りない行は 実測+slackPt へ引き上げる。実測が頭打ちの行は自動高さのまま次段へ送る
     - 頭打ち行(ht>=409.4)も既存の分割ブロックも無ければ「変更なし」で終了(通常ここまで)
     - あれば、対象セルの文章を明示改行単位の断片に分けて表の下(印刷範囲外)へスクラッチ行
       として書き込み、「Excel で開いて保存して再実行」を案内して終了(終了コード3)
  4. Excel で開いて保存 → fitrows 再実行(round2)
     - 断片の実測高を列ごとに合算して必要高を確定し、必要高が上限を超えるケースを
       n=ceil(必要高/maxRowPt) 行に分割(行挿入+縦マージ+明示行高)
     - スクラッチ行を削除し、事後検証(全行 409.5pt 以下・表示高 >= 必要高)

  ※ Windows + デスクトップ版 Excel があれば --auto で上記の「Excel で開いて保存」を
    excelsave(COM。xlsx_excelsave.py)が代行し、人手なしで完了まで実行できる。

制約:
  - 分割ブロックの行高は明示設定(customHeight)する。結合セルは Excel の自動調整対象外の
    ため、自動高さのままだと開いた時に既定高へ潰れる。同じ理由で、横方向に結合された
    セルを含む行は round0 の自動高さ再計算が効かない(Excel が折返し高さを算出しない)。
  - 明示改行を含まない一続きの文章が単体で上限を超える場合は測定不能(エラー。文章の分割が必要)
  - 1ケースの表示高が印刷1ページの本文高さ(用紙高 - 上下余白 - Print_Titles)を超える場合は
    エラー(spec の pageHeightPt で上書き可)
  - 手順1の保存忘れは機械検知できない(雛形由来の ht は頭打ち値にならないため「変更なし」と
    判定され得る)。手順3以降の保存忘れは退避行/スクラッチ行の ht 有無で検知する。
  - round0 で固定を解除した状態のまま中断しても、退避行が残っている限り次回実行で
    元の高さへ復元される(実測が無ければ終了コード3 を返して退避を維持する)。

spec(JSON)の例:
  {
    "file": "docs/tests/.../<機能名>_テスト仕様書.xlsx",
    "anchorColumn": "A",             // ケース行の判定列
    "anchorPattern": "^\\d+-\\d+$",  // 判定列がこの正規表現に一致する行を対象にする
    "columns": "A:I",                // 測定・縦マージの対象列範囲(anchorColumn を含むこと)
    "maxRowPt": 400,                 // 分割後の1行あたり上限(既定400。409.5との差はマージン)
    "slackPt": 3,                    // 固定高行を引き上げるときの上乗せ(既定3)
    "pageHeightPt": null             // 1ケースの上限高。省略時はページ設定から計算
  }
"""
import json
import math
import re
import sys

import xlsx_writer as xw

CAP_DETECT_PT = 409.4    # 保存済み ht がこの値以上なら「上限で頭打ち」とみなす
PAD_ROUND_PT = 3.0       # 断片合算の丸め誤差ぶんの余裕
SLACK_PT = 3.0           # 固定高行を引き上げるときの上乗せ(spec の slackPt で上書き)
MARKER = '#doctool-fitrows-scratch v1'
MARKER_HT = '#doctool-fitrows-heights v1'
NEED_EXCEL_SAVE = 3      # 終了コード: Excel で開いて保存してから再実行が必要

_KEY_RE = re.compile(r'^(\d+)\|([A-Z]+)\|(\d+)$')
_HT_KEY_RE = re.compile(r'^ht\|(\d+)\|([\d.]+)$')

# 用紙サイズ(pageSetup@paperSize)→ (縦向きの幅pt, 高さpt)
_PAPER_PT = {
    1: (612.0, 792.0),          # Letter
    5: (612.0, 1008.0),         # Legal
    8: (841.89, 1190.55),       # A3
    9: (595.28, 841.89),        # A4
    11: (419.53, 595.28),       # A5
    12: (728.5, 1031.81),       # B4(JIS)
    13: (515.91, 728.5),        # B5(JIS)
}


def _q(tag):
    return xw._q(xw.SS, tag)


def _col_range(value):
    a, sep, b = str(value).upper().partition(':')
    if not sep or not a or not b:
        raise ValueError(f'fitrows: columns は "A:I" 形式で指定してください: {value!r}')
    n1, n2 = sorted((xw._col_num(a), xw._col_num(b)))
    return [xw._col_letters(n) for n in range(n1, n2 + 1)]


class _FitContext:
    """対象ファイル・シートの読み取り状態と spec 由来のパラメータを束ねる。"""

    def __init__(self, spec):
        for key in ('file', 'anchorColumn', 'anchorPattern', 'columns'):
            if not spec.get(key):
                raise ValueError(f'fitrows: spec に {key} がありません')
        self.spec = spec
        self.path = spec['file']
        self.anchor_col = str(spec['anchorColumn']).upper()
        self.pattern = re.compile(spec['anchorPattern'])
        self.cols = _col_range(spec['columns'])
        if self.anchor_col not in self.cols:
            raise ValueError('fitrows: anchorColumn は columns の範囲内で指定してください')
        self.max_row_pt = float(spec.get('maxRowPt', 400.0))
        if not (50.0 <= self.max_row_pt <= xw.EXCEL_MAX_ROW_PT):
            raise ValueError(f'fitrows: maxRowPt は 50〜{xw.EXCEL_MAX_ROW_PT} で指定してください')
        self.slack_pt = float(spec.get('slackPt', SLACK_PT))
        if self.slack_pt < 0:
            raise ValueError('fitrows: slackPt は 0 以上で指定してください')
        self.key_col = xw._col_letters(xw._col_num(self.cols[-1]) + 1)
        self.warnings = []

        self.doc = xw._Doc(self.path)
        self.doc.extents = {}
        part_map = xw._sheet_parts(self.doc)
        sheet = spec.get('sheet', 0)
        name = list(part_map)[sheet] if isinstance(sheet, int) else sheet
        part = part_map.get(name)
        if part is None or part not in self.doc.files:
            raise ValueError(f'fitrows: シート {name!r} の worksheet パートが見つかりません')
        self.sheet_index = list(part_map).index(name)
        self.part = part
        self.root = self.doc.root(part)
        self.shared = xw._shared_strings(self.doc)
        self.rescan()

    def rescan(self):
        sd = xw._sheet_data(self.root)
        self.rows = {int(row.get('r')): row for row in sd.findall(_q('row'))}
        fmt = self.root.find(_q('sheetFormatPr'))
        self.default_ht = (float(fmt.get('defaultRowHeight'))
                           if fmt is not None and fmt.get('defaultRowHeight') else 15.0)
        # アンカー列の縦結合 = 既存の分割ブロック
        self.vmerge = {}
        anchor_n = xw._col_num(self.anchor_col)
        for m in self._merge_elems():
            r1, c1, r2, c2 = xw._range_box(m.get('ref'))
            if c1 == c2 == anchor_n and r2 > r1:
                self.vmerge[r1] = r2
        in_block = set()
        for top, bottom in self.vmerge.items():
            in_block.update(range(top + 1, bottom + 1))
        # 対象ケース: (先頭行, ブロックか, 末尾行)
        self.cases = []
        for r in sorted(self.rows):
            if r in in_block:
                continue
            text = self.cell_text(r, self.anchor_col)
            if text and self.pattern.match(text):
                self.cases.append((r, r in self.vmerge, self.vmerge.get(r, r)))

    def _merge_elems(self):
        mc = self.root.find(_q('mergeCells'))
        return mc.findall(_q('mergeCell')) if mc is not None else []

    def cell(self, r, col):
        row = self.rows.get(r)
        if row is None:
            return None
        ref = f'{col}{r}'
        for c in row.findall(_q('c')):
            if c.get('r') == ref:
                return c
        return None

    def cell_text(self, r, col):
        c = self.cell(r, col)
        return xw._cell_text(c, self.shared) if c is not None else ''

    def row_ht(self, r):
        row = self.rows.get(r)
        ht = row.get('ht') if row is not None else None
        return float(ht) if ht else None

    def row_custom(self, r):
        """行高が明示設定(customHeight)されているか。真なら ht は内容と無関係な固定値。"""
        row = self.rows.get(r)
        return row is not None and row.get('customHeight') == '1'

    def disp_ht(self, r):
        """表示高。ht が無い行(自動・未計測)は既定行高とみなす。上限で丸める。"""
        ht = self.row_ht(r)
        return min(ht, xw.EXCEL_MAX_ROW_PT) if ht is not None else self.default_ht

    def marker_top(self, marker):
        for r in sorted(self.rows):
            if self.cell_text(r, self.key_col).startswith(marker):
                return r
        return None

    def scratch_top(self):
        return self.marker_top(MARKER)

    def heights_top(self):
        return self.marker_top(MARKER_HT)


# ---------------------------------------------------------------------------
# 表の下(印刷範囲外)に置く作業行(マーカー行 + キー付きの行)の読み書き
# ---------------------------------------------------------------------------

def _write_marker(ctx, marker, style_row):
    """マーカー行をシート末尾(印刷範囲外)に作り、その行番号を返す。

    2行分の文言+折返しスタイルにして、Excel が保存すると必ず ht が付く
    (=保存済みかどうかを機械判定できる)ようにする。
    """
    sd = xw._sheet_data(ctx.root)
    merge_bottom = max((xw._range_box(m.get('ref'))[2] for m in ctx._merge_elems()), default=0)
    top = max(max(ctx.rows, default=1), merge_bottom) + 2
    anchor_cell = ctx.cell(style_row, ctx.anchor_col)
    style = anchor_cell.get('s') if anchor_cell is not None else None
    row = xw._get_or_create_row(sd, top)
    cell = xw._get_or_create_cell(row, f'{ctx.key_col}{top}')
    if style:
        cell.set('s', style)         # 折返しスタイル(2行分の高さを計測させる)
    xw._set_inline(cell, marker + '\n.')
    return top


def _remove_marker_rows(ctx, top, key_re):
    """マーカー行と、その下に続くキー付きの作業行を削除する。"""
    sd = xw._sheet_data(ctx.root)
    for r in sorted(ctx.rows):
        if r == top or (r > top and key_re.match(ctx.cell_text(r, ctx.key_col))):
            sd.remove(ctx.rows[r])
    ctx.doc.mark(ctx.part)
    ctx.rescan()


# ---------------------------------------------------------------------------
# round0: 固定高(customHeight)のケース行を自動高さへ戻して Excel に実測させる
# ---------------------------------------------------------------------------

def _round0(ctx, check):
    """固定高のケース行の高さを退避してから固定を解除する。

    固定高の ht は内容と無関係な明示値なので、頭打ち判定(ht>=409.4)では見切れを
    検出できない。Excel の自動高さ計算を測定手段にするため、いったん固定を外す。
    戻り値 None は「対象なし/--check」で、呼び出し元が _round1 へ進む。
    """
    fixed = [r for r, is_block, _ in ctx.cases if not is_block and ctx.row_custom(r)]
    if not fixed:
        return None
    nos = [ctx.cell_text(r, ctx.anchor_col) for r in fixed]
    head = ', '.join(nos[:10]) + (' …' if len(nos) > 10 else '')
    print(f'固定高のケース行 {len(fixed)} 件を自動高さへ戻して再測定します: {head}')
    if check:
        print('--check のため変更しません(見切れの判定には Excel 保存による実測が必要です)')
        return None
    _write_height_backup(ctx, fixed)
    ctx.doc.save(ctx.path)
    print('元の行高をシート末尾(印刷範囲外)へ退避し、固定を解除しました'
          '(この状態では対象行が既定高に見えます)。')
    print('次の手順: このファイルを Excel で開いて Ctrl+S で保存し、fitrows を再実行してください。')
    return NEED_EXCEL_SAVE


def _write_height_backup(ctx, case_rows):
    """case_rows の元の行高を退避行に控えてから、固定(customHeight)を解除する。"""
    top = _write_marker(ctx, MARKER_HT, case_rows[0])
    sd = xw._sheet_data(ctx.root)
    for i, case_row in enumerate(case_rows):
        r = top + 1 + i
        row = xw._get_or_create_row(sd, r)
        kc = xw._get_or_create_cell(row, f'{ctx.key_col}{r}')
        xw._set_inline(kc, f'ht|{case_row}|{xw._fmt_pt(ctx.row_ht(case_row) or 0)}')
    xw._set_row_height(ctx.root, {'rows': case_rows, 'auto': True})
    ctx.doc.mark(ctx.part)


def _read_height_backup(ctx, top):
    """退避行から {元行: 元の行高pt} を回収する。"""
    out = {}
    for r in sorted(ctx.rows):
        if r <= top:
            continue
        m = _HT_KEY_RE.match(ctx.cell_text(r, ctx.key_col))
        if m:
            out[int(m.group(1))] = float(m.group(2))
    return out


def _round0b(ctx, heights_top, check):
    """自動高さ化した行の実測を回収し、元の高さの復元/引き上げを適用する。

    実測が頭打ち(>=409.4)の行は自動高さのまま _round1 へ送り、断片測定に進ませる。
    """
    if ctx.row_ht(heights_top) is None:
        print('退避行に実測高がありません(Excel 保存前の状態です)。')
        print('このファイルを Excel で開いて Ctrl+S で保存し、fitrows を再実行してください。')
        return NEED_EXCEL_SAVE
    backup = _read_height_backup(ctx, heights_top)
    if check:
        print(f'--check のため変更しません(退避 {len(backup)} 行は維持されます)。'
              '固定解除の状態を元に戻すには --check なしで再実行してください。')
        return 0

    page_h = _page_height_pt(ctx)
    case_rows = {r for r, is_block, _ in ctx.cases if not is_block}
    heights, raised, capped, ng = [], [], [], []
    for r in sorted(backup):
        prev = backup[r]
        if r not in case_rows:
            ctx.warnings.append(f'退避した行{r}が対象ケースに見つかりません(元の高さへ戻します)')
            heights.append((r, prev))
            continue
        meas = ctx.row_ht(r)
        meas = ctx.default_ht if meas is None else meas
        if meas >= CAP_DETECT_PT:
            capped.append(r)                     # 頭打ち → 断片測定へ(自動高さのまま送る)
            continue
        if prev + 0.1 >= meas:                   # 0.25pt 丸めの許容
            heights.append((r, prev))            # 現行の固定高で表示できる(高さは変えない)
            continue
        per = min(math.ceil((meas + ctx.slack_pt) * 4) / 4, xw.EXCEL_MAX_ROW_PT)
        if per > page_h:
            ng.append(f'{ctx.cell_text(r, ctx.anchor_col)}(行{r}): 必要高 {meas:.1f}pt が'
                      f'1ページの本文高さ {page_h:.1f}pt を超えます。文章の分割・短縮が必要です')
            heights.append((r, prev))
            continue
        heights.append((r, per))
        raised.append((r, prev, meas, per))

    _remove_marker_rows(ctx, heights_top, _HT_KEY_RE)
    for r, per in heights:
        xw._set_row_height(ctx.root, {'row': r, 'heightPt': per})
    ctx.doc.mark(ctx.part)
    ctx.rescan()

    for r, prev, meas, per in raised:
        print(f'{ctx.cell_text(r, ctx.anchor_col):>6} 行{r:>4} 必要高 {meas:>7.1f}pt '
              f'> 現行 {prev:.1f}pt → 行高 {per}pt へ引き上げ')
    print(f'固定高の再測定: {len(backup)} 行(引き上げ {len(raised)} / '
          f'現状維持 {len(heights) - len(raised)} / 頭打ち {len(capped)})')
    for msg in ng:
        print(f'NG: {msg}')
    # NG(ページ高超過)は該当行を元へ戻すだけにして、頭打ち行の断片測定は続行する
    rc = _round1(ctx, check)
    if rc == 0:
        ctx.doc.save(ctx.path)       # _round1 は「変更なし」のとき保存しない
        rc = 1 if ng else 0
    return rc


# ---------------------------------------------------------------------------
# 1回目: 頭打ち検出とスクラッチ(測定用断片)の書き込み
# ---------------------------------------------------------------------------

def _round1(ctx, check):
    capped = [r for r, is_block, _ in ctx.cases
              if not is_block and (ctx.row_ht(r) or 0) >= CAP_DETECT_PT]
    blocks = [r for r, is_block, _ in ctx.cases if is_block]
    if not capped and not blocks:
        print('変更なし: 頭打ち行(ht>=409.4pt)も既存の分割ブロックもありません')
        return 0
    targets = sorted(capped + blocks)
    nos = [ctx.cell_text(r, ctx.anchor_col) for r in targets]
    print(f'測定対象: {len(targets)} ケース '
          f'(頭打ち {len(capped)} / 既存ブロック検証 {len(blocks)}): {", ".join(nos)}')
    if check:
        print('--check のためスクラッチ行は作成しません')
        return 0
    _write_scratch(ctx, targets)
    ctx.doc.save(ctx.path)
    print('測定用のスクラッチ行をシート末尾(印刷範囲外)に書き込みました。')
    print('次の手順: このファイルを Excel で開いて Ctrl+S で保存し、fitrows を再実行してください。')
    return NEED_EXCEL_SAVE


def _write_scratch(ctx, case_rows):
    """case_rows の各セル文章を明示改行単位の断片にして、シート末尾へ1断片=1行で並べる。

    断片行は自動高さ(ht なし)で作る。Excel が保存すると実測 ht が付く。
    キー列(対象列範囲の右隣)に「元行|列|断片番号」を書き、2回目の照合に使う。
    """
    top = _write_marker(ctx, MARKER, case_rows[0])
    sd = xw._sheet_data(ctx.root)
    r = top + 1
    for case_row in case_rows:
        for col in ctx.cols:
            text = ctx.cell_text(case_row, col)
            if not text:
                continue
            src = ctx.cell(case_row, col)
            style = src.get('s') if src is not None else None
            for i, frag in enumerate(str(text).split('\n')):
                row = xw._get_or_create_row(sd, r)
                fc = xw._get_or_create_cell(row, f'{col}{r}')
                if style:
                    fc.set('s', style)   # 元セルと同じフォント・折返しで測らせる
                xw._set_inline(fc, frag if frag != '' else ' ')  # 空行も1行分として測る
                kc = xw._get_or_create_cell(row, f'{ctx.key_col}{r}')
                xw._set_inline(kc, f'{case_row}|{col}|{i}')
                r += 1
    ctx.doc.mark(ctx.part)


# ---------------------------------------------------------------------------
# 2回目: 断片実測の回収 → 分割計画 → 適用 → 検証
# ---------------------------------------------------------------------------

def _read_fragments(ctx, scratch_top):
    """スクラッチ行から (元行, 列) → [断片高さ...] を回収する。

    ht の無い断片行は「既定行高ちょうど」として扱う(Excel は既定高と一致する行の
    ht を省略する)。保存自体の有無はマーカー行の ht で判定済みであること。
    戻り値: (frags, 不整合メッセージのリスト)
    """
    by_key = {}
    for r in sorted(ctx.rows):
        if r <= scratch_top:
            continue
        m = _KEY_RE.match(ctx.cell_text(r, ctx.key_col))
        if m:
            case_row, col, idx = int(m.group(1)), m.group(2), int(m.group(3))
            ht = ctx.row_ht(r)
            by_key.setdefault((case_row, col), {})[idx] = \
                ht if ht is not None else ctx.default_ht
    frags, problems = {}, []
    for (case_row, col), by_idx in sorted(by_key.items()):
        text = ctx.cell_text(case_row, col)
        expected = len(str(text).split('\n')) if text else 0
        if sorted(by_idx) != list(range(expected)):
            problems.append(f'行{case_row} {col}列: 断片数 {len(by_idx)} が'
                            f'現在の内容({expected}断片)と一致しません')
            continue
        frags[(case_row, col)] = [by_idx[i] for i in range(expected)]
    return frags, problems


def _remove_scratch(ctx, scratch_top):
    _remove_marker_rows(ctx, scratch_top, _KEY_RE)


def _remove_block_merges(ctx, top, bottom):
    """既存ブロックの対象列の縦結合を除去する(行追加・結合し直しの前処理)。"""
    mc = ctx.root.find(_q('mergeCells'))
    if mc is None:
        return
    col_nums = {xw._col_num(c) for c in ctx.cols}
    for m in list(mc.findall(_q('mergeCell'))):
        r1, c1, r2, c2 = xw._range_box(m.get('ref'))
        if r1 == top and r2 == bottom and c1 == c2 and c1 in col_nums:
            mc.remove(m)
    mc.set('count', str(len(mc)))


def _page_height_pt(ctx):
    """1ケースの上限高。spec 指定が無ければページ設定から本文高さを計算する。"""
    if ctx.spec.get('pageHeightPt'):
        return float(ctx.spec['pageHeightPt'])
    ps = ctx.root.find(_q('pageSetup'))
    pm = ctx.root.find(_q('pageMargins'))
    paper = int(ps.get('paperSize', 9)) if ps is not None else 9
    orient = (ps.get('orientation') or 'portrait') if ps is not None else 'portrait'
    if paper not in _PAPER_PT:
        ctx.warnings.append(f'未対応の用紙サイズ paperSize={paper} のため A4 とみなします'
                            '(spec の pageHeightPt で上書き可)')
    w, h = _PAPER_PT.get(paper, _PAPER_PT[9])
    page = w if orient == 'landscape' else h
    top = float(pm.get('top', 0.75)) if pm is not None else 0.75
    bottom = float(pm.get('bottom', 0.75)) if pm is not None else 0.75
    return page - (top + bottom) * 72.0 - _print_titles_height(ctx)


def _print_titles_height(ctx):
    """Print_Titles(見出し行の繰返し)ぶんの高さ。各ページで本文高さを消費する。"""
    wb = ctx.doc.root('xl/workbook.xml')
    dnc = wb.find(_q('definedNames'))
    for dn in (dnc.findall(_q('definedName')) if dnc is not None else []):
        if (dn.get('name') == '_xlnm.Print_Titles'
                and dn.get('localSheetId') == str(ctx.sheet_index)):
            m = re.search(r'\$(\d+):\$(\d+)', dn.text or '')
            if m:
                return sum(ctx.disp_ht(r)
                           for r in range(int(m.group(1)), int(m.group(2)) + 1))
    return 0.0


def _plan(ctx, frags):
    """断片実測から (計画リスト, ページ高NGリスト, 頭打ち断片リスト) を作る。"""
    required = {}
    capped_frags = []
    for (case_row, col), heights in frags.items():
        for i, ht in enumerate(heights):
            if ht >= CAP_DETECT_PT:
                capped_frags.append(f'行{case_row} {col}列 断片{i + 1}')
        required[case_row] = max(required.get(case_row, 0.0), sum(heights))
    if capped_frags:
        return [], [], capped_frags

    case_info = {r: (is_block, bottom) for r, is_block, bottom in ctx.cases}
    page_h = _page_height_pt(ctx)
    plans, ng = [], []
    for case_row in sorted(required):
        info = case_info.get(case_row)
        if info is None:
            ctx.warnings.append(f'測定済みの行{case_row}が対象ケースに見つかりません(スキップ)')
            continue
        is_block, bottom = info
        no = ctx.cell_text(case_row, ctx.anchor_col)
        req = required[case_row]
        req_pad = req + PAD_ROUND_PT
        if is_block:
            span = bottom - case_row + 1
            n_need = math.ceil(req_pad / ctx.max_row_pt)
            if n_need <= span:
                total_now = sum(ctx.disp_ht(i) for i in range(case_row, bottom + 1))
                if total_now + 0.1 >= req:
                    continue                     # 現状で表示可能(意図した余裕は維持)
                n, per = span, math.ceil(req_pad / span * 4) / 4
            else:
                n, per = n_need, math.ceil(req_pad / n_need * 4) / 4
        else:
            if (ctx.row_ht(case_row) or 0) < CAP_DETECT_PT:
                continue                         # 頭打ちでない行は触らない
                                                 # (固定高行は round0/0b で処理済み)
            n = math.ceil(req_pad / ctx.max_row_pt)
            per = math.ceil(req_pad / n * 4) / 4
        per = min(per, ctx.max_row_pt)
        if per * n > page_h:
            ng.append(f'{no}(行{case_row}): 必要高 {req:.1f}pt が1ページの本文高さ '
                      f'{page_h:.1f}pt を超えます。文章の分割・短縮が必要です')
            continue
        plans.append({'row': case_row, 'no': no, 'n': n, 'per': per, 'req': req,
                      'span': (bottom - case_row + 1) if is_block else 1,
                      'is_block': is_block})
    return plans, ng, []


def _apply(ctx, plans):
    """計画を下の行から適用する(上方の行番号を安定させるため降順)。"""
    for p in sorted(plans, key=lambda x: x['row'], reverse=True):
        top, n, per = p['row'], p['n'], p['per']
        row_elem = ctx.rows.get(top)
        width = len(row_elem.findall(_q('c'))) if row_elem is not None else len(ctx.cols)
        if p['is_block']:
            _remove_block_merges(ctx, top, top + p['span'] - 1)
            add = n - p['span']
        else:
            add = n - 1
        if add > 0:
            op = {'op': 'fillTable', 'templateRow': top, 'insert': True,
                  'rows': [[None] * width] + [[''] * width] * add}
            xw._fill_table(ctx.doc, ctx.root, ctx.part, op, ctx.warnings)
        for col in ctx.cols:
            if n > 1:
                xw._merge_cells(ctx.root, {'op': 'mergeCells',
                                           'ref': f'{col}{top}:{col}{top + n - 1}'})
        xw._set_row_height(ctx.root, {'rows': list(range(top, top + n)), 'heightPt': per})
    ctx.doc.mark(ctx.part)
    xw._update_print_areas(ctx.doc, ctx.warnings)
    ctx.rescan()


def _verify(ctx, plans):
    """適用結果の検証。(不足リスト, 上限超リスト) を返す。"""
    pos = {ctx.cell_text(r, ctx.anchor_col): (r, bottom) for r, _, bottom in ctx.cases}
    bad = []
    for p in plans:
        loc = pos.get(p['no'])
        if loc is None:
            bad.append(f"{p['no']}: 適用後に行が見つかりません")
            continue
        top, bottom = loc
        disp = sum(ctx.disp_ht(i) for i in range(top, bottom + 1))
        if disp + 0.35 < p['req']:               # 0.25pt 丸めの許容
            bad.append(f"{p['no']}: 表示高 {disp:.1f}pt < 必要高 {p['req']:.1f}pt")
    over = [f'行{r}: ht={ctx.row_ht(r)}' for r in sorted(ctx.rows)
            if (ctx.row_ht(r) or 0) > xw.EXCEL_MAX_ROW_PT + 0.01]
    return bad, over


def _round2(ctx, scratch_top, check):
    # 保存判定: スクラッチ行は ht 無しで作るので、どこかに ht が付いていれば保存済み。
    # (マーカー行は必ず既定高と異なる高さになるよう作ってあるが、念のため断片行も見る)
    saved = ctx.row_ht(scratch_top) is not None or any(
        ctx.row_ht(r) is not None for r in ctx.rows
        if r > scratch_top and _KEY_RE.match(ctx.cell_text(r, ctx.key_col)))
    if not saved:
        print('スクラッチ行に実測高がありません(Excel 保存前の状態です)。')
        print('このファイルを Excel で開いて Ctrl+S で保存し、fitrows を再実行してください。')
        return NEED_EXCEL_SAVE

    frags, problems = _read_fragments(ctx, scratch_top)
    if problems:
        print('スクラッチ行が現在の内容と一致しません(測定後にセルが変更された可能性):')
        for msg in problems:
            print(f'  {msg}')
        if not check:
            _remove_scratch(ctx, scratch_top)
            ctx.doc.save(ctx.path)
            print('スクラッチ行を削除しました。fitrows を再実行して測定し直してください。')
        return 1

    plans, ng, capped_frags = _plan(ctx, frags)
    if capped_frags:
        print('明示改行の無い一続きの文章が単体で行高上限に達しており、測定できません:')
        for msg in capped_frags:
            print(f'  {msg}')
        print('該当セルの文章を改行で分割してから、fitrows を再実行してください。')
        if not check:
            _remove_scratch(ctx, scratch_top)
            ctx.doc.save(ctx.path)
        return 1
    if ng:
        for msg in ng:
            print(f'NG: {msg}')
        if not check:
            _remove_scratch(ctx, scratch_top)
            ctx.doc.save(ctx.path)
        return 1

    for p in plans:
        kind = 'ブロック調整' if p['is_block'] else '分割'
        print(f"{p['no']:>6} 行{p['row']:>4} 必要高 {p['req']:>7.1f}pt "
              f"→ {kind}: {p['n']}行 × {p['per']}pt(縦マージ)")
    if check:
        print(f'--check のため変更しません(計画 {len(plans)} 件。スクラッチは維持)')
        return 0
    if not plans:
        _remove_scratch(ctx, scratch_top)
        ctx.doc.save(ctx.path)
        print('変更なし: 測定の結果、全ケースが現在の行構成で表示可能です(スクラッチは削除)')
        return 0

    _remove_scratch(ctx, scratch_top)
    _apply(ctx, plans)
    bad, over = _verify(ctx, plans)
    ctx.doc.save(ctx.path)
    splits = sum(1 for p in plans if p['n'] > p['span'] or not p['is_block'])
    print(f'適用完了: {len(plans)} ケース(行分割/拡張 {splits} 件)、スクラッチ削除済み')
    print(f'事後検証: 表示高不足 {len(bad)} 件 / 上限超の行高 {len(over)} 件'
          + (' → OK' if not bad and not over else ''))
    for msg in bad + over:
        print(f'  {msg}')
    return 1 if bad or over else 0


# ---------------------------------------------------------------------------
# エントリポイント
# ---------------------------------------------------------------------------

def run(spec, check=False):
    """spec(dict)を実行し終了コードを返す(0=完了/変更なし, 1=エラー, 3=Excel保存待ち)。"""
    ctx = _FitContext(spec)
    if not check:
        try:
            with open(ctx.path, 'r+b'):
                pass
        except PermissionError:
            print('エラー: 対象ファイルが開かれています。Excel を閉じて再実行してください。')
            return 1
    scratch = ctx.scratch_top()
    heights = ctx.heights_top()
    if scratch is not None:
        rc = _round2(ctx, scratch, check)
    elif heights is not None:
        rc = _round0b(ctx, heights, check)
    else:
        rc = _round0(ctx, check)
        if rc is None:                           # 固定高のケース行なし → 従来の頭打ち検出へ
            rc = _round1(ctx, check)
    for w in ctx.warnings:
        print(f'WARN: {w}', file=sys.stderr)
    return rc


MAX_AUTO_SAVES = 4       # --auto での Excel 保存回数の上限(通常2〜3回で完了する)


def run_auto(spec, check=False, max_saves=MAX_AUTO_SAVES):
    """excelsave(COM)で「Excel で開いて保存」を代行しながら完了まで実行する。

    保存 → run() を、Excel 保存待ち(終了コード3)でなくなるまで繰り返す。
    保存しても進展しない場合(Excel が実測 ht を書き戻していない等)は
    無限ループを避けて打ち切り、手動フローを案内する。
    """
    import xlsx_excelsave
    if not spec.get('file'):
        return run(spec, check=check)        # 標準の spec 検証エラーに任せる
    for _ in range(max_saves):
        try:
            xlsx_excelsave.excel_save(spec['file'])
        except xlsx_excelsave.ExcelSaveError as e:
            print(f'エラー: excelsave に失敗しました: {e}')
            print('手動フロー(Excel で開いて Ctrl+S → fitrows 再実行)に切り替えてください。')
            return 1
        print(f"excelsave: Excel で開いて保存しました: {spec['file']}")
        rc = run(spec, check=check)
        if rc != NEED_EXCEL_SAVE:
            return rc
    print(f'エラー: Excel 保存を {max_saves} 回行っても測定が完了しませんでした。')
    print('手動フロー(Excel で開いて Ctrl+S → fitrows 再実行)に切り替えてください。')
    return 1


def run_file(spec_path, check=False):
    with open(spec_path, encoding='utf-8') as fp:
        spec = json.load(fp)
    return run(spec, check=check)


def run_file_auto(spec_path, check=False):
    with open(spec_path, encoding='utf-8') as fp:
        spec = json.load(fp)
    return run_auto(spec, check=check)
