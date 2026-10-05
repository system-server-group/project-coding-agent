"""doctool docx リーダー — docx を IR へ変換する(標準ライブラリのみ)。

書式オーバーレイ(read_docx(with_format=True) / CLI: dump --format):
  既定の IR は設計原則どおり書式を持たないが、レビュー用途向けにオプトインで
  書式情報を付加できる。
  - paragraph.runs: ラン単位の書式 [{text, bold, italic, underline, strike,
    color, highlight, sizePt, font}, ...](書式が付いたランがある段落のみ)
  - paragraph.align / spacing / indent: 段落配置(w:jc)・段落間隔・インデント(pt)
  - table の borders / セルの borders / shading: 罫線(w:tblBorders / w:tcBorders)
    と網掛け色(w:shd fill)
  - ルートの styleFormats: スタイルID → basedOn 連鎖を解決した書式
    (docDefaults は "default")。段落側の style.id と突合して実効書式を判断する
    (ラン直接指定 > スタイル連鎖 > default)。
"""
import xml.etree.ElementTree as ET
import zipfile

from ir import IR_FORMAT, IR_VERSION, prune

W = '{http://schemas.openxmlformats.org/wordprocessingml/2006/main}'
R = '{http://schemas.openxmlformats.org/officeDocument/2006/relationships}'
PKG_REL = '{http://schemas.openxmlformats.org/package/2006/relationships}'
A = '{http://schemas.openxmlformats.org/drawingml/2006/main}'
VML = '{urn:schemas-microsoft-com:vml}'

# 段落内で図形要素として特別扱いするタグ
_DRAWING_TAGS = (W + 'drawing', W + 'pict', W + 'object')


def _bool_attr(el):
    """w:titlePg 等のトグル要素: 存在し、val が偽値でなければ True。"""
    if el is None:
        return False
    val = el.get(W + 'val')
    return val not in ('0', 'false', 'off')


# ---------------------------------------------------------------------------
# 書式オーバーレイ
# ---------------------------------------------------------------------------

def _run_format(r):
    return format_from_rpr(r.find(W + 'rPr'))


def format_from_rpr(r_pr):
    """w:rPr → 書式 dict(レビューに有用なものだけ。既定値は出さない)。"""
    if r_pr is None:
        return {}
    fmt = {}
    for key, tag in (('bold', 'b'), ('italic', 'i'), ('strike', 'strike')):
        if _bool_attr(r_pr.find(W + tag)):
            fmt[key] = True
    u = r_pr.find(W + 'u')
    if u is not None and (u.get(W + 'val') or 'single') != 'none':
        fmt['underline'] = True
    color = r_pr.find(W + 'color')
    if color is not None and color.get(W + 'val') not in (None, 'auto'):
        fmt['color'] = color.get(W + 'val')
    highlight = r_pr.find(W + 'highlight')
    if highlight is not None and highlight.get(W + 'val') != 'none':
        fmt['highlight'] = highlight.get(W + 'val')
    sz = r_pr.find(W + 'sz')
    if sz is not None and sz.get(W + 'val'):
        half_pt = float(sz.get(W + 'val'))
        fmt['sizePt'] = int(half_pt // 2) if half_pt % 2 == 0 else half_pt / 2
    fonts = r_pr.find(W + 'rFonts')
    if fonts is not None:
        font = fonts.get(W + 'eastAsia') or fonts.get(W + 'ascii')
        if font:
            fmt['font'] = font
    return fmt


def _merge_segments(segments):
    """隣接する同一書式のランを結合する。"""
    merged = []
    for text, fmt in segments:
        if merged and merged[-1][1] == fmt:
            merged[-1][0] += text
        else:
            merged.append([text, fmt])
    return merged


def _pt_attr(el, *names):
    """twips 属性(別名対応)→ pt。20の倍数なら int に丸める。"""
    for name in names:
        v = el.get(W + name)
        if v is not None:
            n = int(v)
            return n // 20 if n % 20 == 0 else n / 20
    return None


def _borders(borders_el, sides):
    """w:tblBorders / w:tcBorders → {辺: 線種}。"""
    if borders_el is None:
        return None
    out = {}
    for side in sides:
        el = borders_el.find(W + side)
        if el is not None and el.get(W + 'val'):
            out[side] = el.get(W + 'val')
    return out or None


def _style_formats(z):
    """styles.xml → スタイルID ごとの解決済み書式(basedOn 連鎖をマージ)。

    docDefaults(文書既定のラン書式)は "default" キーで返す。
    """
    root = _read_xml(z, 'word/styles.xml')
    if root is None:
        return {}
    out = {}
    defaults = root.find(W + 'docDefaults/' + W + 'rPrDefault/' + W + 'rPr')
    default_fmt = format_from_rpr(defaults)
    if default_fmt:
        out['default'] = default_fmt
    records = {}
    for style in root.findall(W + 'style'):
        style_id = style.get(W + 'styleId')
        if not style_id:
            continue
        based_on = style.find(W + 'basedOn')
        fmt = format_from_rpr(style.find(W + 'rPr'))
        jc = style.find(W + 'pPr/' + W + 'jc')
        if jc is not None and jc.get(W + 'val'):
            fmt['align'] = jc.get(W + 'val')
        records[style_id] = (
            based_on.get(W + 'val') if based_on is not None else None, fmt)
    for style_id in records:
        chain, cur, seen = [], style_id, set()
        while cur and cur in records and cur not in seen:
            seen.add(cur)
            chain.append(records[cur][1])
            cur = records[cur][0]
        resolved = {}
        for fmt in reversed(chain):
            resolved.update(fmt)
        if resolved:
            out[style_id] = resolved
    return out


# ---------------------------------------------------------------------------
# ブロック走査
# ---------------------------------------------------------------------------

class _RunAcc:
    def __init__(self):
        self.text = []
        self.fields = []
        self.drawings = []
        self.segments = []  # with_format 時のみ使用: (text, fmt)


def _parse_drawing(el, ctx):
    """w:drawing / w:pict / w:object → textbox / image / shape ブロック群。"""
    out = []
    for tx in el.iter(W + 'txbxContent'):
        out.append(prune({'type': 'textbox', 'blocks': _walk_blocks(tx, ctx)}))
    if not out:
        has_image = next(el.iter(A + 'blip'), None) is not None or \
            next(el.iter(VML + 'imagedata'), None) is not None
        out.append({'type': 'image' if has_image else 'shape'})
    return out


def _collect_runs(el, acc, ctx, fmt=None):
    for child in el:
        tag = child.tag
        if tag == W + 'pPr':
            continue
        elif tag == W + 't':
            acc.text.append(child.text or '')
            if ctx['format']:
                acc.segments.append((child.text or '', fmt or {}))
        elif tag == W + 'tab':
            acc.text.append('\t')
        elif tag in (W + 'br', W + 'cr'):
            acc.text.append('\n')
        elif tag == W + 'instrText':
            t = (child.text or '').strip()
            if t:
                acc.fields.append(t)
        elif tag in _DRAWING_TAGS:
            acc.drawings.extend(_parse_drawing(child, ctx))
        elif tag == W + 'r':
            _collect_runs(child, acc, ctx,
                          _run_format(child) if ctx['format'] else None)
        else:
            # w:hyperlink / w:smartTag 等は中を再帰走査する(ランの書式は引き継ぐ)
            _collect_runs(child, acc, ctx, fmt)


def _paragraph(el, ctx):
    acc = _RunAcc()
    _collect_runs(el, acc, ctx)

    style = numbering = align = runs = spacing = indent = None
    p_pr = el.find(W + 'pPr')
    if p_pr is not None:
        style_id = None
        p_style = p_pr.find(W + 'pStyle')
        if p_style is not None:
            style_id = p_style.get(W + 'val')
            style = prune({'id': style_id, 'name': ctx['styles'].get(style_id)})
        numbering = _effective_numbering(p_pr, style_id, ctx)
        if ctx['format']:
            jc = p_pr.find(W + 'jc')
            if jc is not None:
                align = jc.get(W + 'val')
            spacing_el = p_pr.find(W + 'spacing')
            if spacing_el is not None:
                spacing = prune({
                    'beforePt': _pt_attr(spacing_el, 'before'),
                    'afterPt': _pt_attr(spacing_el, 'after'),
                    'line': spacing_el.get(W + 'line'),
                    'lineRule': spacing_el.get(W + 'lineRule'),
                })
            ind_el = p_pr.find(W + 'ind')
            if ind_el is not None:
                indent = prune({
                    'leftPt': _pt_attr(ind_el, 'left', 'start'),
                    'rightPt': _pt_attr(ind_el, 'right', 'end'),
                    'firstLinePt': _pt_attr(ind_el, 'firstLine'),
                    'hangingPt': _pt_attr(ind_el, 'hanging'),
                })
    if ctx['format']:
        merged = _merge_segments(acc.segments)
        if any(fmt for _, fmt in merged):
            runs = [prune({'text': text, **fmt})
                    for text, fmt in merged if text]
    block = prune({
        'type': 'paragraph',
        'style': style,
        'numbering': numbering,
        'align': align,
        'spacing': spacing,
        'indent': indent,
        'runs': runs,
        'fields': acc.fields,
        'drawings': acc.drawings,
    })
    block['text'] = ''.join(acc.text)
    return block


def _table(el, ctx):
    cells = []
    col_origin = {}  # 開始列 → 縦結合の起点セル
    col_count = 0
    tbl_borders = None
    if ctx['format']:
        tbl_pr = el.find(W + 'tblPr')
        if tbl_pr is not None:
            tbl_borders = _borders(
                tbl_pr.find(W + 'tblBorders'),
                ('top', 'left', 'bottom', 'right', 'insideH', 'insideV'))
    rows = el.findall(W + 'tr')
    for r, tr in enumerate(rows):
        c = 0
        for tc in tr.findall(W + 'tc'):
            tc_pr = tc.find(W + 'tcPr')
            grid_span = 1
            v_merge = None
            shading = cell_borders = None
            if tc_pr is not None:
                gs = tc_pr.find(W + 'gridSpan')
                if gs is not None:
                    grid_span = int(gs.get(W + 'val') or 1)
                vm = tc_pr.find(W + 'vMerge')
                if vm is not None:
                    v_merge = vm.get(W + 'val') or 'continue'
                if ctx['format']:
                    shd = tc_pr.find(W + 'shd')
                    if shd is not None and shd.get(W + 'fill') not in (None, 'auto'):
                        shading = shd.get(W + 'fill')
                    cell_borders = _borders(tc_pr.find(W + 'tcBorders'),
                                            ('top', 'left', 'bottom', 'right'))
            if v_merge == 'continue' and c in col_origin:
                col_origin[c]['rowSpan'] += 1
            else:
                cell = {
                    'row': r, 'col': c, 'rowSpan': 1, 'colSpan': grid_span,
                    'blocks': _walk_blocks(tc, ctx),
                }
                if shading:
                    cell['shading'] = shading
                if cell_borders:
                    cell['borders'] = cell_borders
                cells.append(cell)
                if v_merge == 'restart':
                    col_origin[c] = cell
                else:
                    col_origin.pop(c, None)
            c += grid_span
        col_count = max(col_count, c)
    table = {'type': 'table', 'rowCount': len(rows), 'colCount': col_count,
             'cells': cells}
    if tbl_borders:
        table['borders'] = tbl_borders
    return table


def _walk_blocks(container, ctx):
    blocks = []
    for el in container:
        tag = el.tag
        if tag == W + 'p':
            blocks.append(_paragraph(el, ctx))
        elif tag == W + 'tbl':
            blocks.append(_table(el, ctx))
        elif tag == W + 'sdt':
            content = el.find(W + 'sdtContent')
            if content is not None:
                blocks.extend(_walk_blocks(content, ctx))
    return blocks


# ---------------------------------------------------------------------------
# パート読み取り
# ---------------------------------------------------------------------------

def _read_xml(z, part):
    try:
        return ET.fromstring(z.read(part))
    except KeyError:
        return None


def _read_styles(z):
    styles = {}
    root = _read_xml(z, 'word/styles.xml')
    if root is None:
        return styles
    for style in root.findall(W + 'style'):
        style_id = style.get(W + 'styleId')
        name = style.find(W + 'name')
        if style_id and name is not None:
            styles[style_id] = name.get(W + 'val')
    return styles


_ORDERED_NONE = {'bullet', 'none'}


def _is_ordered(num_fmt):
    """numFmt が連番(decimal/roman/letter 等)か。bullet/none/未定義は False。"""
    return bool(num_fmt) and num_fmt not in _ORDERED_NONE


class _NumberingResolver:
    """numId(+ilvl) から実効的な numFmt を解決する。

    num → abstractNum の参照、num の lvlOverride、abstractNum の numStyleLink
    (採番定義を別スタイルへ委譲する連結)まで辿る。
    """

    def __init__(self, num_map, abs_lvls, abs_link, style_num):
        self._num = num_map          # numId -> (abstractNumId, {ilvl: numFmt override})
        self._abs = abs_lvls         # abstractNumId -> {ilvl: numFmt}
        self._link = abs_link        # abstractNumId -> styleId (numStyleLink)
        self._style_num = style_num  # styleId -> (numId, ilvl)

    def fmt(self, num_id, ilvl, _seen=None):
        if num_id is None:
            return None
        ilvl = ilvl if ilvl is not None else '0'
        entry = self._num.get(num_id)
        if entry is None:
            return None
        abs_id, overrides = entry
        if overrides.get(ilvl) is not None:
            return overrides[ilvl]
        num_fmt = self._abs.get(abs_id, {}).get(ilvl)
        if num_fmt is not None:
            return num_fmt
        # numStyleLink: 採番定義を委譲しているスタイルの num へ辿る
        link = self._link.get(abs_id)
        if link is not None:
            seen = _seen if _seen is not None else set()
            if num_id not in seen:
                seen.add(num_id)
                linked = self._style_num.get(link)
                if linked is not None and linked[0] != num_id:
                    return self.fmt(linked[0], ilvl, seen)
        return None


def _read_style_numbering(z):
    """styleId -> (numId, ilvl)。basedOn 連鎖を辿り最初に numPr を持つ祖先を採る。"""
    return _style_numbering_from_styles(_read_xml(z, 'word/styles.xml'))


def _style_numbering_from_styles(root):
    """<w:styles> 要素から styleId -> (numId, ilvl) を解決する(root は ET/lxml 可)。"""
    if root is None:
        return {}
    records = {}
    for style in root.findall(W + 'style'):
        style_id = style.get(W + 'styleId')
        if not style_id:
            continue
        based_on = style.find(W + 'basedOn')
        num_pr = style.find(W + 'pPr/' + W + 'numPr')
        num_id = ilvl = None
        if num_pr is not None:
            nid = num_pr.find(W + 'numId')
            il = num_pr.find(W + 'ilvl')
            num_id = nid.get(W + 'val') if nid is not None else None
            ilvl = il.get(W + 'val') if il is not None else None
        records[style_id] = (
            based_on.get(W + 'val') if based_on is not None else None,
            num_id, ilvl)
    out = {}
    for style_id in records:
        cur, seen = style_id, set()
        while cur and cur in records and cur not in seen:
            seen.add(cur)
            based_on, num_id, ilvl = records[cur]
            if num_id is not None:
                out[style_id] = (num_id, ilvl)
                break
            cur = based_on
    return out


def _read_numbering(z, style_num):
    """numbering.xml から numId(+ilvl) → numFmt を解決するリゾルバを作る。"""
    return _numbering_resolver_from_root(_read_xml(z, 'word/numbering.xml'), style_num)


def _numbering_resolver_from_root(root, style_num):
    """<w:numbering> 要素からリゾルバを作る(root は ET/lxml 可)。"""
    if root is None:
        return _NumberingResolver({}, {}, {}, style_num)
    abs_lvls, abs_link = {}, {}
    for an in root.findall(W + 'abstractNum'):
        aid = an.get(W + 'abstractNumId')
        lvls = {}
        for lvl in an.findall(W + 'lvl'):
            nf = lvl.find(W + 'numFmt')
            lvls[lvl.get(W + 'ilvl')] = nf.get(W + 'val') if nf is not None else None
        abs_lvls[aid] = lvls
        nsl = an.find(W + 'numStyleLink')
        if nsl is not None and nsl.get(W + 'val'):
            abs_link[aid] = nsl.get(W + 'val')
    num_map = {}
    for num in root.findall(W + 'num'):
        nid = num.get(W + 'numId')
        ab = num.find(W + 'abstractNumId')
        overrides = {}
        for ov in num.findall(W + 'lvlOverride'):
            lvl = ov.find(W + 'lvl')
            if lvl is not None:
                nf = lvl.find(W + 'numFmt')
                if nf is not None:
                    overrides[ov.get(W + 'ilvl')] = nf.get(W + 'val')
        num_map[nid] = (ab.get(W + 'val') if ab is not None else None, overrides)
    return _NumberingResolver(num_map, abs_lvls, abs_link, style_num)


def _effective_numbering(p_pr, style_id, ctx):
    """段落の実効採番を解決する。

    直接 numPr を優先し、無ければ段落スタイル由来(basedOn 連鎖)を採る。
    numId=0 は採番打ち消し(スタイル採番も無効)。出所(direct/style)と numFmt、
    連番か(ordered)を付す。
    """
    direct_num = direct_ilvl = None
    removed = False
    num_pr = p_pr.find(W + 'numPr')
    if num_pr is not None:
        nid = num_pr.find(W + 'numId')
        il = num_pr.find(W + 'ilvl')
        direct_ilvl = il.get(W + 'val') if il is not None else None
        if nid is not None:
            val = nid.get(W + 'val')
            if val == '0':
                removed = True
            else:
                direct_num = val

    num_id, ilvl, source = direct_num, direct_ilvl, None
    if direct_num is not None:
        source = 'direct'
    elif not removed and style_id is not None:
        linked = ctx['styleNum'].get(style_id)
        if linked is not None and linked[0] not in (None, '0'):
            num_id = linked[0]
            ilvl = direct_ilvl if direct_ilvl is not None else linked[1]
            source = 'style'

    if num_id is not None:
        num_fmt = ctx['numbering'].fmt(num_id, ilvl)
        return prune({
            'numId': num_id, 'level': ilvl, 'source': source,
            'format': num_fmt, 'ordered': True if _is_ordered(num_fmt) else None,
        })
    if num_pr is not None and not removed:
        return {'numId': None}  # numId を解決できない裸の numPr(従来互換)
    return None


def _read_rels(z, part='word/document.xml'):
    head, _, tail = part.rpartition('/')
    root = _read_xml(z, f'{head}/_rels/{tail}.rels')
    if root is None:
        return {}
    return {rel.get('Id'): rel.get('Target')
            for rel in root.findall(PKG_REL + 'Relationship')}


def _read_part_blocks(z, part, ctx):
    root = _read_xml(z, part)
    return _walk_blocks(root, ctx) if root is not None else []


def _sections(doc_root, z, rels, ctx):
    sections = []
    for sect in doc_root.iter(W + 'sectPr'):
        headers, footers = {}, {}
        for kind, dest in (('headerReference', headers), ('footerReference', footers)):
            for ref in sect.findall(W + kind):
                ref_type = ref.get(W + 'type') or 'default'
                target = rels.get(ref.get(R + 'id'))
                if target:
                    dest[ref_type] = _read_part_blocks(z, 'word/' + target, ctx)
        sections.append(prune({
            'titlePg': _bool_attr(sect.find(W + 'titlePg')) or None,
            'headers': headers,
            'footers': footers,
        }))
    return sections


def read_docx(src, source_name=None, with_format=False):
    """docx(パスまたはファイルオブジェクト)を IR(dict) に変換する。

    with_format=True で書式オーバーレイ(runs/align/shading)を付加する。
    """
    with zipfile.ZipFile(src) as z:
        style_num = _read_style_numbering(z)
        ctx = {
            'styles': _read_styles(z),
            'styleNum': style_num,
            'numbering': _read_numbering(z, style_num),
            'format': with_format,
        }
        rels = _read_rels(z)
        root = ET.fromstring(z.read('word/document.xml'))
        body = root.find(W + 'body')

        settings = {}
        settings_root = _read_xml(z, 'word/settings.xml')
        if settings_root is not None and \
                _bool_attr(settings_root.find(W + 'evenAndOddHeaders')):
            settings['evenAndOddHeaders'] = True

        return prune({
            'format': IR_FORMAT,
            'version': IR_VERSION,
            'source': {'path': source_name or str(src), 'kind': 'docx'},
            'settings': settings,
            'styleFormats': _style_formats(z) if with_format else None,
            'sections': _sections(root, z, rels, ctx),
            'body': _walk_blocks(body, ctx),
        })
