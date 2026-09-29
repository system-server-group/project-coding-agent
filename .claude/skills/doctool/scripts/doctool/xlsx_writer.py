"""doctool xlsx ライター — worksheet / styles / drawing の XML を直接編集し、
図形・グラフ・画像などの描画層を保持したまま生成する (要 lxml)。

背景:
  openpyxl はファイルを自前モデルへ読み込み書き直すため、モデル化しない層
  (drawings=図形/テキストボックス/コネクタ・charts・media=画像・pivot 等) を保存時に落とす。
  本モジュールは xlsx(=ZIP+XML) を zip として開き、必要な XML パートだけを lxml で編集し、
  それ以外のパートは byte 単位でそのまま書き戻す。触らないパートは失われない。

対応する操作:
  - setCell    : セル値を設定(インライン文字列)。既存 style 参照(s)を保持。値 null は据え置き。
  - clearCell  : 値だけ消す(style は残す)。
  - replaceText: セル文字列の部分置換(既定は最初の一致、all:true で全置換)。
  - setFormat  : styles.xml に派生 xf(font/fill/alignment)を追記しセルの s を差し替える。
                 対応キー: bold/italic/underline/strike・color/sizePt/font・align・fill(=塗り)。
  - fillTable  : 見本行(templateRow=Excel 行番号)の書式を複製して rows を書き込む。既定は
                 overwrite(上書き。行数・結合・描画アンカーは変えない=固定表領域向け)。
                 `insert: true` のときは 1 行目を上書きし残りを挿入して、挿入位置より下の
                 行・結合・**描画アンカー**を下方向へずらす(行を増やして図形を追従させたいとき)。
  - deleteRow  : Excel 行を削除し、下の 行・結合・描画アンカーを上方向へずらす。
  - setRowHeight: 行高を設定する。heightPt=固定高(customHeight="1" を付与。上限 409.5pt)。
                 `auto: true` は固定を解除して Excel の自動高さに戻す。row(単一)または
                 rows(複数)で対象行を指定。
  - mergeCells : セル結合を追加する(ref="A10:A12" 形式)。同一の結合が既にあれば何もしない。
                 既存結合と部分的に重なる指定はエラー(重複結合はファイル破損の原因になるため)。

番地は ref(A1 表記)。結合セルは worksheet の mergeCells から起点(左上)へ解決する。
xlsx には Word の numId 共有連番が無いため採番正規化は行わない。

印刷範囲(_xlnm.Print_Area):
  記入(setCell / replaceText / fillTable)が既存の印刷範囲をはみ出す場合、その範囲を
  記入セルまで自動拡張する(既存領域 ∪ 記入セルの外接矩形)。insert / deleteRow の行シフトは
  印刷範囲側の行にも反映する。印刷範囲が未定義のシートには新規作成しない(全面印刷を維持)。
  Print_Titles(見出し行の繰返し)など Print_Area 以外の定義名は変更しない。
"""
import re
import zipfile
from collections import OrderedDict
from copy import deepcopy

from lxml import etree

SS = 'http://schemas.openxmlformats.org/spreadsheetml/2006/main'
R = 'http://schemas.openxmlformats.org/officeDocument/2006/relationships'
PKG_REL = 'http://schemas.openxmlformats.org/package/2006/relationships'
XDR = 'http://schemas.openxmlformats.org/drawingml/2006/spreadsheetDrawing'
XML_SPACE = '{http://www.w3.org/XML/1998/namespace}space'

_FONT_ORDER = ('b', 'i', 'strike', 'condense', 'extend', 'outline', 'shadow',
               'u', 'vertAlign', 'sz', 'color', 'name', 'family', 'charset', 'scheme')


def _q(ns, tag):
    return f'{{{ns}}}{tag}'


# ---------------------------------------------------------------------------
# ドキュメント状態(zip 内パートの遅延パース / 差分書き戻し)
# ---------------------------------------------------------------------------

class _Doc:
    def __init__(self, path):
        self.files = OrderedDict()
        with zipfile.ZipFile(path) as z:
            for name in z.namelist():
                self.files[name] = z.read(name)
        self._roots = {}
        self._dirty = set()

    def root(self, part):
        if part not in self._roots:
            self._roots[part] = etree.fromstring(self.files[part])
        return self._roots[part]

    def mark(self, part):
        self._dirty.add(part)

    def save(self, out):
        for part in self._dirty:
            self.files[part] = etree.tostring(self._roots[part], xml_declaration=True,
                                              encoding='UTF-8', standalone=True)
        with zipfile.ZipFile(out, 'w', zipfile.ZIP_DEFLATED) as z:
            for name, data in self.files.items():
                z.writestr(name, data)


def _resolve(base_part, target):
    if target.startswith('/'):
        return target[1:]
    parts = base_part.split('/')[:-1]
    for seg in target.split('/'):
        parts.pop() if seg == '..' else parts.append(seg)
    return '/'.join(parts)


def _rels_of(doc, part):
    head, _, tail = part.rpartition('/')
    rels_part = f'{head}/_rels/{tail}.rels'
    if rels_part not in doc.files:
        return {}
    root = etree.fromstring(doc.files[rels_part])
    return {rel.get('Id'): _resolve(part, rel.get('Target'))
            for rel in root.findall(_q(PKG_REL, 'Relationship'))}


def _sheet_parts(doc):
    wb = etree.fromstring(doc.files['xl/workbook.xml'])
    rels = _rels_of(doc, 'xl/workbook.xml')
    out = OrderedDict()
    sheets = wb.find(_q(SS, 'sheets'))
    for sheet in (sheets if sheets is not None else []):
        target = rels.get(sheet.get(_q(R, 'id')))
        if target:
            out[sheet.get('name')] = target
    return out


def _drawing_parts(doc, sheet_part):
    rels = _rels_of(doc, sheet_part)
    ws = doc.root(sheet_part)
    out = []
    for d in ws.findall(_q(SS, 'drawing')):
        target = rels.get(d.get(_q(R, 'id')))
        if target and target in doc.files:
            out.append(target)
    return out


# ---------------------------------------------------------------------------
# セル座標
# ---------------------------------------------------------------------------

_REF_RE = re.compile(r'^([A-Z]+)(\d+)$')


def _split_ref(ref):
    m = _REF_RE.match(ref.split(':', 1)[0])
    if not m:
        raise ValueError(f'不正なセル参照: {ref}')
    return m.group(1), int(m.group(2))


def _col_num(letters):
    n = 0
    for ch in letters:
        n = n * 26 + (ord(ch) - ord('A') + 1)
    return n


def _col_letters(n):
    s = ''
    while n > 0:
        n, rem = divmod(n - 1, 26)
        s = chr(ord('A') + rem) + s
    return s


def _norm_rgb(value):
    hexv = str(value).lstrip('#')
    return ('FF' + hexv if len(hexv) == 6 else hexv).upper()


def _merge_anchor(root, ref):
    col_letters, row = _split_ref(ref)
    col = _col_num(col_letters)
    mc = root.find(_q(SS, 'mergeCells'))
    if mc is None:
        return ref
    for m in mc.findall(_q(SS, 'mergeCell')):
        a, b = (m.get('ref').split(':') + [m.get('ref')])[:2]
        (c1, r1), (c2, r2) = _split_ref(a), _split_ref(b)
        if r1 <= row <= r2 and _col_num(c1) <= col <= _col_num(c2):
            return f'{c1}{r1}'
    return ref


def _merge_box(root, ref):
    """ref を含む結合セルの矩形 (r1, c1, r2, c2) を返す。無ければ単一セル。"""
    col_letters, row = _split_ref(ref)
    col = _col_num(col_letters)
    mc = root.find(_q(SS, 'mergeCells'))
    if mc is not None:
        for m in mc.findall(_q(SS, 'mergeCell')):
            a, b = (m.get('ref').split(':') + [m.get('ref')])[:2]
            (c1, r1), (c2, r2) = _split_ref(a), _split_ref(b)
            if r1 <= row <= r2 and _col_num(c1) <= col <= _col_num(c2):
                return (r1, _col_num(c1), r2, _col_num(c2))
    return (row, col, row, col)


# ---------------------------------------------------------------------------
# 記入範囲の追跡(印刷範囲 _xlnm.Print_Area の自動拡張に使う)
# ---------------------------------------------------------------------------

class _Extent:
    """シートに記入したセルの外接矩形と、挿入/削除に伴う行シフト履歴を保持する。"""

    def __init__(self):
        self.bbox = None            # [min_row, min_col, max_row, max_col]
        self.shifts = []            # [(pivot, delta), ...] 適用順

    def add_range(self, r1, c1, r2, c2):
        lo_r, hi_r = sorted((r1, r2))
        lo_c, hi_c = sorted((c1, c2))
        if self.bbox is None:
            self.bbox = [lo_r, lo_c, hi_r, hi_c]
        else:
            b = self.bbox
            b[0] = min(b[0], lo_r)
            b[1] = min(b[1], lo_c)
            b[2] = max(b[2], hi_r)
            b[3] = max(b[3], hi_c)

    def apply_shift(self, pivot, delta):
        """行 pivot より下を delta ずらす。既記録の bbox も同じ規則で追従させる。"""
        self.shifts.append((pivot, delta))
        if self.bbox is not None:
            b = self.bbox
            if b[0] > pivot:
                b[0] += delta
            if b[2] > pivot:
                b[2] += delta


def _ext(doc, part):
    e = doc.extents.get(part)
    if e is None:
        e = _Extent()
        doc.extents[part] = e
    return e


# ---------------------------------------------------------------------------
# 行・セルの取得/生成
# ---------------------------------------------------------------------------

def _sheet_data(root):
    sd = root.find(_q(SS, 'sheetData'))
    if sd is None:
        raise ValueError('worksheet に sheetData がありません')
    return sd


def _find_row(sheet_data, r):
    for row in sheet_data.findall(_q(SS, 'row')):
        if int(row.get('r')) == r:
            return row
    return None


def _get_or_create_row(sheet_data, r):
    for row in sheet_data.findall(_q(SS, 'row')):
        rn = int(row.get('r'))
        if rn == r:
            return row
        if rn > r:
            new = etree.Element(_q(SS, 'row'), r=str(r))
            row.addprevious(new)
            return new
    return etree.SubElement(sheet_data, _q(SS, 'row'), r=str(r))


def _get_or_create_cell(row, ref):
    col = _col_num(_split_ref(ref)[0])
    for c in row.findall(_q(SS, 'c')):
        cn = _col_num(_split_ref(c.get('r'))[0])
        if cn == col:
            return c
        if cn > col:
            new = etree.Element(_q(SS, 'c'), r=ref)
            c.addprevious(new)
            return new
    return etree.SubElement(row, _q(SS, 'c'), r=ref)


def _clear_value(cell):
    for tag in ('v', 'is', 'f'):
        for el in cell.findall(_q(SS, tag)):
            cell.remove(el)
    cell.attrib.pop('t', None)


def _set_inline(cell, text):
    _clear_value(cell)
    cell.set('t', 'inlineStr')
    t_el = etree.SubElement(etree.SubElement(cell, _q(SS, 'is')), _q(SS, 't'))
    t_el.text = text
    if text != text.strip() or '\n' in text:
        t_el.set(XML_SPACE, 'preserve')


def _flatten(value):
    if isinstance(value, list):
        return '\n'.join('' if v is None else str(v) for v in value)
    return None if value is None else (value if isinstance(value, str) else str(value))


# ---------------------------------------------------------------------------
# 現在値(replaceText 用)
# ---------------------------------------------------------------------------

def _shared_strings(doc):
    xml = doc.files.get('xl/sharedStrings.xml')
    if xml is None:
        return []
    root = etree.fromstring(xml)
    return [''.join(t.text or '' for t in si.iter(_q(SS, 't')))
            for si in root.findall(_q(SS, 'si'))]


def _cell_text(cell, shared):
    t = cell.get('t')
    if t == 'inlineStr':
        is_el = cell.find(_q(SS, 'is'))
        return ''.join(x.text or '' for x in is_el.iter(_q(SS, 't'))) if is_el is not None else ''
    v = cell.find(_q(SS, 'v'))
    if v is None:
        return ''
    if t == 's':
        try:
            return shared[int(v.text)]
        except (ValueError, IndexError):
            return ''
    return v.text or ''


# ---------------------------------------------------------------------------
# styles.xml 編集(setFormat)
# ---------------------------------------------------------------------------

def _dedup_append(container, elem):
    s = etree.tostring(elem)
    for i, ch in enumerate(container):
        if etree.tostring(ch) == s:
            return i
    container.append(elem)
    if container.get('count') is not None:
        container.set('count', str(len(container)))
    return len(container) - 1


def _ordered_font_insert(font, tag):
    el = font.find(_q(SS, tag))
    if el is not None:
        return el
    el = etree.Element(_q(SS, tag))
    rank = _FONT_ORDER.index(tag)
    for child in font:
        local = etree.QName(child).localname
        if local in _FONT_ORDER and _FONT_ORDER.index(local) > rank:
            child.addprevious(el)
            return el
    font.append(el)
    return el


def _derive_font(styles, base_id, fmt):
    fonts = styles.find(_q(SS, 'fonts'))
    font = deepcopy(list(fonts)[int(base_id)])
    for key, tag in (('bold', 'b'), ('italic', 'i'), ('strike', 'strike')):
        if key in fmt:
            el = font.find(_q(SS, tag))
            if fmt[key] and el is None:
                _ordered_font_insert(font, tag)
            elif not fmt[key] and el is not None:
                font.remove(el)
    if 'underline' in fmt:
        u = font.find(_q(SS, 'u'))
        if fmt['underline'] and u is None:
            _ordered_font_insert(font, 'u')
        elif not fmt['underline'] and u is not None:
            font.remove(u)
    if 'sizePt' in fmt:
        _ordered_font_insert(font, 'sz').set('val', str(fmt['sizePt']))
    if 'color' in fmt:
        col = _ordered_font_insert(font, 'color')
        col.attrib.clear()
        col.set('rgb', _norm_rgb(fmt['color']))
    if 'font' in fmt:
        _ordered_font_insert(font, 'name').set('val', fmt['font'])
    return _dedup_append(fonts, font)


def _solid_fill(styles, rgb):
    fills = styles.find(_q(SS, 'fills'))
    fill = etree.Element(_q(SS, 'fill'))
    pf = etree.SubElement(fill, _q(SS, 'patternFill'), patternType='solid')
    etree.SubElement(pf, _q(SS, 'fgColor'), rgb=rgb)
    etree.SubElement(pf, _q(SS, 'bgColor'), rgb=rgb)
    return _dedup_append(fills, fill)


_FONT_FMT = {'bold', 'italic', 'underline', 'strike', 'color', 'sizePt', 'font'}


def _derive_xf(styles, s_index, fmt):
    cell_xfs = styles.find(_q(SS, 'cellXfs'))
    xf = deepcopy(list(cell_xfs)[int(s_index)])
    if _FONT_FMT & fmt.keys():
        xf.set('fontId', str(_derive_font(styles, xf.get('fontId', '0'), fmt)))
        xf.set('applyFont', '1')
    fill = fmt.get('fill', fmt.get('shading'))
    if fill is not None:
        xf.set('fillId', str(_solid_fill(styles, _norm_rgb(fill))))
        xf.set('applyFill', '1')
    if 'align' in fmt:
        al = xf.find(_q(SS, 'alignment'))
        if al is None:
            al = etree.Element(_q(SS, 'alignment'))
            xf.insert(0, al)
        al.set('horizontal', fmt['align'])
        xf.set('applyAlignment', '1')
    return _dedup_append(cell_xfs, xf)


def _set_format(doc, root, ref, fmt, warnings):
    known = _FONT_FMT | {'align', 'fill', 'shading'}
    unknown = fmt.keys() - known
    if unknown:
        raise ValueError(f'setFormat: 未知の書式キー {sorted(unknown)}')
    styles = doc.root('xl/styles.xml')
    cell = _get_or_create_cell(_get_or_create_row(_sheet_data(root), _split_ref(ref)[1]), ref)
    new_s = _derive_xf(styles, cell.get('s', '0'), fmt)
    cell.set('s', str(new_s))
    doc.mark('xl/styles.xml')


# ---------------------------------------------------------------------------
# 行シフト(挿入/削除に伴う 行・結合・描画アンカーの追従)
# ---------------------------------------------------------------------------

def _shift_rows(sheet_data, pivot, delta):
    """行番号 > pivot の 行・セル参照を delta だけずらす。"""
    for row in sheet_data.findall(_q(SS, 'row')):
        rn = int(row.get('r'))
        if rn > pivot:
            row.set('r', str(rn + delta))
            for c in row.findall(_q(SS, 'c')):
                letters, _ = _split_ref(c.get('r'))
                c.set('r', f'{letters}{rn + delta}')


def _shift_merges(root, pivot, delta, warnings):
    mc = root.find(_q(SS, 'mergeCells'))
    if mc is None:
        return
    for m in mc.findall(_q(SS, 'mergeCell')):
        a, b = m.get('ref').split(':')
        (c1, r1), (c2, r2) = _split_ref(a), _split_ref(b)
        if r1 > pivot and r2 > pivot:
            m.set('ref', f'{c1}{r1 + delta}:{c2}{r2 + delta}')
        elif r1 <= pivot < r2 or r2 <= pivot < r1:
            warnings.append(f'結合 {m.get("ref")} が挿入/削除位置を跨ぐためずらしません')


def _shift_drawings(doc, sheet_part, pivot, delta, warnings):
    """描画アンカー(0 基点の xdr:row)を、Excel 行 > pivot の分だけ delta ずらす。"""
    for part in _drawing_parts(doc, sheet_part):
        root = doc.root(part)
        changed = False
        for tag in ('from', 'to'):
            for pos in root.iter(_q(XDR, tag)):
                row_el = pos.find(_q(XDR, 'row'))
                if row_el is None:
                    continue
                excel_row = int(row_el.text) + 1        # 0 基点 → Excel 行
                if excel_row > pivot:
                    row_el.text = str(int(row_el.text) + delta)
                    changed = True
        if changed:
            doc.mark(part)


# ---------------------------------------------------------------------------
# fillTable / deleteRow
# ---------------------------------------------------------------------------

def _row_horizontal_merges(root, row_num):
    """指定行内で完結する横結合の (c1, c2) 列文字を返す。"""
    mc = root.find(_q(SS, 'mergeCells'))
    out = []
    if mc is None:
        return out
    for m in mc.findall(_q(SS, 'mergeCell')):
        a, b = m.get('ref').split(':')
        (c1, r1), (c2, r2) = _split_ref(a), _split_ref(b)
        if r1 == r2 == row_num:
            out.append((c1, c2))
    return out


# CT_Worksheet のスキーマ順で mergeCells より後に来る要素。mergeCells を新規作成する
# ときは、これらのうち最初に現れる要素の直前へ挿入する(worksheet 末尾に追加すると
# phoneticPr / pageMargins 等の後ろになり、スキーマ違反で Excel がファイルを開けない)
_AFTER_MERGE_CELLS = (
    'phoneticPr', 'conditionalFormatting', 'dataValidations', 'hyperlinks',
    'printOptions', 'pageMargins', 'pageSetup', 'headerFooter', 'rowBreaks',
    'colBreaks', 'customProperties', 'cellWatches', 'ignoredErrors', 'smartTags',
    'drawing', 'drawingHF', 'picture', 'oleObjects', 'controls',
    'webPublishItems', 'tableParts', 'extLst',
)


def _add_merge(root, ref):
    mc = root.find(_q(SS, 'mergeCells'))
    if mc is None:
        mc = etree.Element(_q(SS, 'mergeCells'))
        successors = {_q(SS, tag) for tag in _AFTER_MERGE_CELLS}
        pos = next((el for el in root if el.tag in successors), None)
        if pos is not None:
            pos.addprevious(mc)
        else:
            root.append(mc)
    etree.SubElement(mc, _q(SS, 'mergeCell'), ref=ref)
    mc.set('count', str(len(mc)))


def _write_row_values(row, values):
    """行内の各セル(列順)に values を割り当てる(null は据え置き)。"""
    cells = row.findall(_q(SS, 'c'))
    for i, cell in enumerate(cells):
        if i < len(values) and values[i] is not None:
            _set_inline(cell, _flatten(values[i]))


def _clone_row(proto_copy, target, values):
    """見本行(コピー)を target 行として複製し values を書き込む。"""
    clone = deepcopy(proto_copy)
    clone.set('r', str(target))
    for c in clone.findall(_q(SS, 'c')):
        letters, _ = _split_ref(c.get('r'))
        c.set('r', f'{letters}{target}')
    _write_row_values(clone, values)
    return clone


def _place_row(sheet_data, clone, target):
    """target 行を clone で置き換える(既存があれば入れ替え、無ければ番地順に挿入)。"""
    for row in sheet_data.findall(_q(SS, 'row')):
        rn = int(row.get('r'))
        if rn == target:
            row.addprevious(clone)
            sheet_data.remove(row)
            return
        if rn > target:
            row.addprevious(clone)
            return
    sheet_data.append(clone)


def _merge_exists(root, ref):
    mc = root.find(_q(SS, 'mergeCells'))
    return mc is not None and any(m.get('ref') == ref for m in mc.findall(_q(SS, 'mergeCell')))


# ---------------------------------------------------------------------------
# setRowHeight / mergeCells
# ---------------------------------------------------------------------------

EXCEL_MAX_ROW_PT = 409.5    # Excel の行高上限(仕様と制限)。超える値は Excel が切り捨てる


def _fmt_pt(value):
    s = f'{float(value):.2f}'.rstrip('0').rstrip('.')
    return s or '0'


def _set_row_height(root, op):
    """行高の設定。heightPt=固定(customHeight 付与)/ auto:true=固定解除(自動高さへ)。"""
    if 'rows' in op:
        rows = op['rows']
    elif 'row' in op:
        rows = [op['row']]
    else:
        rows = None
    if not rows or not all(isinstance(r, int) and r >= 1 for r in rows):
        raise ValueError('setRowHeight: row(Excel 行番号)または rows を指定してください')
    height = op.get('heightPt')
    auto = bool(op.get('auto'))
    if auto == (height is not None):
        raise ValueError('setRowHeight: heightPt か auto:true のどちらか一方を指定してください')
    if height is not None and not (0 < float(height) <= EXCEL_MAX_ROW_PT):
        raise ValueError(f'setRowHeight: heightPt は 0 より大きく {EXCEL_MAX_ROW_PT}'
                         f'(Excel 上限)以下で指定してください: {height}')
    sheet_data = _sheet_data(root)
    for r in rows:
        row = _get_or_create_row(sheet_data, r)
        if auto:
            row.attrib.pop('ht', None)
            row.attrib.pop('customHeight', None)
        else:
            row.set('ht', _fmt_pt(height))
            row.set('customHeight', '1')


def _range_box(ref):
    """"A10:B12" → (min_row, min_col, max_row, max_col)。単一セル参照も可。"""
    a, sep, b = ref.partition(':')
    c1, r1 = _split_ref(a)
    c2, r2 = _split_ref(b) if sep else (c1, r1)
    return (min(r1, r2), min(_col_num(c1), _col_num(c2)),
            max(r1, r2), max(_col_num(c1), _col_num(c2)))


def _merge_cells(root, op):
    """セル結合の追加。同一結合は何もしない。部分重複はエラー(ファイル破損防止)。"""
    ref = str(op.get('ref', '')).upper()
    if ':' not in ref:
        raise ValueError(f'mergeCells: 結合範囲(例 "A10:A12")を指定してください: {ref!r}')
    if _merge_exists(root, ref):
        return
    r1, c1, r2, c2 = _range_box(ref)
    if (r1, c1) == (r2, c2):
        raise ValueError(f'mergeCells: 単一セルは結合できません: {ref}')
    mc = root.find(_q(SS, 'mergeCells'))
    for m in (mc.findall(_q(SS, 'mergeCell')) if mc is not None else []):
        er1, ec1, er2, ec2 = _range_box(m.get('ref'))
        if not (er2 < r1 or r2 < er1 or ec2 < c1 or c2 < ec1):
            raise ValueError(f'mergeCells: 既存の結合 {m.get("ref")} と重なります: {ref}')
    _add_merge(root, ref)


def _fill_table(doc, root, sheet_part, op, warnings):
    """見本行(templateRow)を複製して rows を書き込む。

    既定は overwrite(上書き。行数・結合・描画アンカーを変えない)。`insert: true` のときは
    1 行目を上書きし残りを挿入して、挿入位置より下の 行・結合・描画アンカーを下方向へずらす
    (固定表領域でなく、行を増やして描画を追従させたい場合に使う)。
    """
    template_row = op['templateRow']
    rows = op['rows']
    if not rows:
        return
    sheet_data = _sheet_data(root)
    proto = _find_row(sheet_data, template_row)
    if proto is None:
        raise ValueError(f'fillTable: templateRow={template_row} の行が雛形にありません')
    start_row = op.get('startRow', template_row)
    h_merges = _row_horizontal_merges(root, template_row)
    proto_copy = deepcopy(proto)               # スタイル見本(書き込み前の状態)
    proto_cols = [_col_num(_split_ref(c.get('r'))[0])
                  for c in proto_copy.findall(_q(SS, 'c'))]
    min_c = min(proto_cols) if proto_cols else 1
    max_c = max(proto_cols) if proto_cols else 1
    ext = _ext(doc, sheet_part)

    if op.get('insert'):                       # 挿入モード: 下方を押し下げて追従
        insert_count = len(rows) - 1
        if insert_count > 0:
            _shift_rows(sheet_data, template_row, insert_count)
            _shift_merges(root, template_row, insert_count, warnings)
            _shift_drawings(doc, sheet_part, template_row, insert_count, warnings)
            ext.apply_shift(template_row, insert_count)
        ext.add_range(template_row, min_c, template_row + len(rows) - 1, max_c)
        _write_row_values(proto, rows[0])
        prev = proto
        for i in range(1, len(rows)):
            target = template_row + i
            clone = _clone_row(proto_copy, target, rows[i])
            prev.addnext(clone)
            prev = clone
            for c1, c2 in h_merges:
                _add_merge(root, f'{c1}{target}:{c2}{target}')
    else:                                      # 既定: overwrite(上書き・行数不変)
        for i, values in enumerate(rows):
            target = start_row + i
            clone = _clone_row(proto_copy, target, values)
            _place_row(sheet_data, clone, target)
            for c1, c2 in h_merges:
                ref = f'{c1}{target}:{c2}{target}'
                if not _merge_exists(root, ref):
                    _add_merge(root, ref)
        ext.add_range(start_row, min_c, start_row + len(rows) - 1, max_c)
    doc.mark(sheet_part)


def _delete_row(doc, root, sheet_part, op, warnings):
    r = op.get('row')
    if not isinstance(r, int):
        raise ValueError('deleteRow: row(Excel 行番号)を指定してください')
    sheet_data = _sheet_data(root)
    target = _find_row(sheet_data, r)
    if target is not None:
        sheet_data.remove(target)
    # 対象行の結合を除去
    mc = root.find(_q(SS, 'mergeCells'))
    if mc is not None:
        for m in list(mc.findall(_q(SS, 'mergeCell'))):
            (_, r1), (_, r2) = (_split_ref(x) for x in m.get('ref').split(':'))
            if r1 == r2 == r:
                mc.remove(m)
        mc.set('count', str(len(mc)))
    _shift_rows(sheet_data, r, -1)
    _shift_merges(root, r, -1, warnings)
    _shift_drawings(doc, sheet_part, r, -1, warnings)
    _ext(doc, sheet_part).apply_shift(r, -1)
    doc.mark(sheet_part)


# ---------------------------------------------------------------------------
# 適用
# ---------------------------------------------------------------------------

def _apply_cell_op(doc, root, sheet_part, op, shared, warnings):
    ref = _merge_anchor(root, op['ref'])
    kind = op['op']
    if kind == 'setCell':
        text = _flatten(op.get('paragraphs', op.get('text', '')))
        if text is None:
            return
        _set_inline(_get_or_create_cell(_get_or_create_row(_sheet_data(root),
                    _split_ref(ref)[1]), ref), text)
        _ext(doc, sheet_part).add_range(*_merge_box(root, op['ref']))
        doc.mark(sheet_part)
    elif kind == 'clearCell':
        _clear_value(_get_or_create_cell(_get_or_create_row(_sheet_data(root),
                     _split_ref(ref)[1]), ref))
        doc.mark(sheet_part)
    elif kind == 'replaceText':
        find = op.get('find')
        if not find:
            raise ValueError('replaceText: find が空です')
        cell = _get_or_create_cell(_get_or_create_row(_sheet_data(root),
                                                      _split_ref(ref)[1]), ref)
        cur = _cell_text(cell, shared)
        if find not in cur:
            warnings.append(f'replaceText: {find!r} が見つかりません({ref})')
            return
        _set_inline(cell, cur.replace(find, op.get('replace', ''),
                                      -1 if op.get('all') else 1))
        _ext(doc, sheet_part).add_range(*_merge_box(root, op['ref']))
        doc.mark(sheet_part)
    elif kind == 'setFormat':
        fmt = op.get('format') or {}
        if not fmt:
            raise ValueError('setFormat: format が空です')
        if op.get('find'):
            warnings.append(f'setFormat: Excel はセル単位書式のため find は無視します({ref})')
        _set_format(doc, root, ref, fmt, warnings)
        doc.mark(sheet_part)
    else:
        raise ValueError(f'未知の操作: {kind}')


# ---------------------------------------------------------------------------
# 印刷範囲(_xlnm.Print_Area)の自動拡張
# ---------------------------------------------------------------------------

_AREA_RE = re.compile(r'^\$?([A-Za-z]+)\$?(\d+)(?::\$?([A-Za-z]+)\$?(\d+))?$')


def _split_top_level_commas(value):
    """引用符(')の外側にあるカンマだけで分割する。"""
    parts, buf, in_q = [], '', False
    for ch in value:
        if ch == "'":
            in_q = not in_q
            buf += ch
        elif ch == ',' and not in_q:
            parts.append(buf)
            buf = ''
        else:
            buf += ch
    parts.append(buf)
    return parts


def _rewrite_print_area(value, ext, warnings):
    """印刷範囲文字列に行シフトを反映し、記入範囲(bbox)を内包するよう拡張する。"""
    parsed = []                                 # [(sheet_ref, [r1,c1,r2,c2]) | (raw_segment, None)]
    for seg in _split_top_level_commas(value):
        sref, sep, rng = seg.rpartition('!')
        m = _AREA_RE.match(rng.strip()) if sep else None
        if not m:
            parsed.append((seg, None))          # 解釈不能な領域は素通し
            continue
        c1, r1, c2, r2 = m.group(1), int(m.group(2)), m.group(3), m.group(4)
        if c2 is None:
            c2, r2 = c1, r1
        else:
            r2 = int(r2)
        parsed.append((sref, [r1, _col_num(c1.upper()), r2, _col_num(c2.upper())]))

    for _, box in parsed:                        # 挿入/削除の行シフトを既存領域へ反映
        if box is None:
            continue
        for pivot, delta in ext.shifts:
            if box[0] > pivot:
                box[0] += delta
            if box[2] > pivot:
                box[2] += delta

    if ext.bbox is not None:                     # 記入範囲を内包するよう拡張
        idxs = [i for i, (_, box) in enumerate(parsed) if box is not None]
        if idxs:
            bb = ext.bbox
            target = next((i for i in idxs
                           if not (parsed[i][1][3] < bb[1] or parsed[i][1][1] > bb[3])),
                          idxs[-1])
            if len(idxs) > 1:
                warnings.append('印刷範囲に複数領域があるため、拡張は列が重なる1領域のみに適用しました')
            box = parsed[target][1]
            box[0], box[1] = min(box[0], bb[0]), min(box[1], bb[1])
            box[2], box[3] = max(box[2], bb[2]), max(box[3], bb[3])

    out = []
    for sref, box in parsed:
        if box is None:
            out.append(sref)                     # 素通し(sref に元の segment 全体を格納)
        else:
            out.append(f'{sref}!${_col_letters(box[1])}${box[0]}:'
                       f'${_col_letters(box[3])}${box[2]}')
    return ','.join(out)


def _update_print_areas(doc, warnings):
    """記入のあったシートについて、既存の Print_Area を記入範囲まで拡張する。

    印刷範囲が未定義のシートには新規作成しない(全面印刷の挙動を変えないため)。
    Print_Titles(見出し行の繰返し)など Print_Area 以外の定義名は対象外。
    """
    wb = doc.root('xl/workbook.xml')
    dn_container = wb.find(_q(SS, 'definedNames'))
    if dn_container is None:
        return
    rels = _rels_of(doc, 'xl/workbook.xml')
    sheets_el = wb.find(_q(SS, 'sheets'))
    ordered = [rels.get(sheet.get(_q(R, 'id'))) for sheet in (sheets_el if sheets_el is not None else [])]
    dirty = False
    for dn in dn_container.findall(_q(SS, 'definedName')):
        if dn.get('name') != '_xlnm.Print_Area':
            continue
        lsid = dn.get('localSheetId')
        if lsid is None or not dn.text:
            continue
        idx = int(lsid)
        if not (0 <= idx < len(ordered)):
            continue
        ext = doc.extents.get(ordered[idx])
        if ext is None or (ext.bbox is None and not ext.shifts):
            continue
        new_val = _rewrite_print_area(dn.text, ext, warnings)
        if new_val != dn.text:
            dn.text = new_val
            dirty = True
    if dirty:
        doc.mark('xl/workbook.xml')


def _used_range_ref(root):
    """worksheet の実在セル(<c>)の外接矩形を "A1:I74" 形式で返す(セルが無ければ None)。

    Excel が保存時に書く <dimension>(値・書式を持つセルの外接矩形)に相当する近似。
    材料化された全セルを必ず包含するため、実データより狭くなることはない。
    """
    sd = root.find(_q(SS, 'sheetData'))
    if sd is None:
        return None
    min_r = max_r = min_c = max_c = None
    for row in sd.findall(_q(SS, 'row')):
        for cell in row.findall(_q(SS, 'c')):
            letters, r = _split_ref(cell.get('r'))
            c = _col_num(letters)
            if min_r is None:
                min_r, max_r, min_c, max_c = r, r, c, c
            else:
                min_r, max_r = min(min_r, r), max(max_r, r)
                min_c, max_c = min(min_c, c), max(max_c, c)
    if min_r is None:
        return None
    if min_r == max_r and min_c == max_c:
        return f'{_col_letters(min_c)}{min_r}'
    return f'{_col_letters(min_c)}{min_r}:{_col_letters(max_c)}{max_r}'


def _update_dimensions(doc):
    """記入で変化した各 worksheet の <dimension> を実在セルの外接矩形へ更新する。

    doctool の記入(行挿入・セル追加)は <dimension> を触らないため、宣言が実データより
    狭いまま残ると、宣言を信じて走査する読み手(openpyxl の read_only 等)が末尾行を
    取りこぼす。保存直前に used range を再計算して合わせる(過小にはならない)。
    """
    for part in list(doc._dirty):
        if not re.match(r'xl/worksheets/sheet\d+\.xml$', part):
            continue
        root = doc.root(part)
        ref = _used_range_ref(root)
        if ref is None:
            continue
        dim = root.find(_q(SS, 'dimension'))
        if dim is None:                      # 通例は存在。無ければスキーマ順で補う
            dim = etree.Element(_q(SS, 'dimension'))
            sheet_pr = root.find(_q(SS, 'sheetPr'))
            pos = list(root).index(sheet_pr) + 1 if sheet_pr is not None else 0
            root.insert(pos, dim)
        dim.set('ref', ref)


def apply_spec(spec):
    """記入スペックをサージカルに適用して xlsx を生成し、警告のリストを返す。

    worksheet / styles / drawing のパートのみ書き換え、その他は byte 単位で保持する。
    記入(setCell / replaceText / fillTable)が既存の印刷範囲(_xlnm.Print_Area)を
    はみ出す場合は、その範囲を記入セルまで自動拡張する。
    """
    doc = _Doc(spec['template'])
    doc.extents = {}
    shared = _shared_strings(doc)
    part_map = _sheet_parts(doc)
    warnings = []

    for op in spec['operations']:
        sheet = op.get('sheet', 0)
        name = list(part_map.keys())[sheet] if isinstance(sheet, int) else sheet
        part = part_map.get(name)
        if part is None or part not in doc.files:
            raise ValueError(f'シート {name!r} の worksheet パートが見つかりません')
        root = doc.root(part)
        kind = op['op']
        if kind == 'fillTable':
            _fill_table(doc, root, part, op, warnings)
        elif kind == 'deleteRow':
            _delete_row(doc, root, part, op, warnings)
        elif kind == 'setRowHeight':
            _set_row_height(root, op)
            doc.mark(part)
        elif kind == 'mergeCells':
            _merge_cells(root, op)
            doc.mark(part)
        else:
            if 'ref' not in op:
                raise ValueError(f'{kind}: サージカルでは ref 指定のみ対応です')
            _apply_cell_op(doc, root, part, op, shared, warnings)

    _update_print_areas(doc, warnings)
    _update_dimensions(doc)
    doc.save(spec['output'])
    return warnings
