"""doctool writer — 雛形 docx に記入スペックを適用して文書を生成する (要 python-docx)。

記入スペック(JSON)の例:
{
  "template": "凡例/型情報定義書/型情報定義書一覧表_凡例.docx",
  "output": "out.docx",
  "operations": [
    {"op": "setCell", "part": "header", "block": 0, "row": 0, "col": 0, "text": "..."},
    {"op": "setParagraph", "part": "body", "block": 2, "text": "..."},
    {"op": "replaceText", "part": "body", "block": 2, "find": "誤", "replace": "正"},
    {"op": "setFormat", "part": "body", "block": 2, "find": "必須",
     "format": {"bold": true, "color": "FF0000"}},
    {"op": "deleteBlock", "part": "body", "block": 0},
    {"op": "fillTable", "part": "body", "block": 3, "dataRow": 1,
     "rows": [[null, "論理名", "physicalName", "概要"]]},
    {"op": "appendSection", "part": "body", "insertAfter": 5, "blocks": [
      {"cloneFrom": 0, "text": "追加する見出し"},
      {"cloneFrom": 1, "dataRow": 1, "rows": [["項目", "値"]]}
    ]}
  ]
}

- 座標(block / row / col)はすべて doctool dump の IR と同じアドレス。
  block は対象パート内のブロック(段落・表)配列の添字、row / col は結合解決済みの
  グリッド座標(起点セルのみ指定可)。
- 添字は全操作とも「雛形の状態」を基準に解決される(deleteBlock 後のずれを考えない)。
- appendSection は既存ブロックを複製して挿入する(書式を継承した節追加)。
  blocks の各要素は cloneFrom(複製元の添字)と、段落なら text、表なら dataRow+rows
  (fillTable と同じ規約)を指定する。insertAfter の添字の直後(省略時は末尾の sectPr 直前)
  に、列挙順で挿入される。
- 値が null のセルは雛形のまま残す(自動採番の No 列等)。
- setCell / setParagraph は、混在書式の段落では新旧テキストの共通接頭辞・接尾辞を
  保って変化した中間部分だけを差し替え、ラン書式を維持する(「ラベル+値」型の欄)。
  差し替え範囲が書式境界を跨ぐ場合のみ先頭ラン書式に均し、警告を返す。
- fillTable で自動採番(numPr)の段落へ空でない値を設定しようとした場合はスキップし
  警告を返す(No 列の保護)。setCell / setParagraph は明示的な指定とみなし採番付き
  段落でもテキストを設定する(番号付き見出し等は採番+テキストが正当な構造のため)。
- replaceText は段落内の部分置換(レビュー指摘の修正用)。ランの書式を保持し、複数ラン
  にまたがる一致も置換できる(一致範囲の先頭ランの書式に揃う)。block が表の場合は
  row / col でセルを指定する(セル直下の段落のみ対象。入れ子表には降りない)。
  既定は最初の一致のみ。"all": true で全置換。一致が無い場合は警告を返す。
- 生成(apply_spec)は保存前に自動採番を表ごとに独立採番へ正規化する
  (normalize_table_numbering)。Word は numId 単位で連番カウンタを共有するため、
  表の複製や凡例の numId 共有があると表をまたいで連番が地続きになる。これを各表
  1 始まりに振り直す(対象は連番=ordered のみ)。採番が段落スタイル由来(heading 等)の
  場合は、当該段落へ直接 numPr を被せて独立させる。既存ファイルへは
  normalize_numbering_file で同じ正規化を適用する。
- setFormat は書式の設定(レビュー指摘の書式修正用)。format に指定できるキー:
  ラン書式 bold/italic/underline/strike(true/false。false はスタイル継承の明示上書き)、
  color/highlight/sizePt/font、段落の align、セルの shading(row/col 指定時のみ)。
  find を指定すると一致範囲だけに適用する(範囲境界でランを分割。"all": true で全一致)。
  find なしは対象段落(セルなら直下の全段落)の全ランに適用する。
"""
import copy

from docx import Document
from lxml import etree

import docx_reader
from docx_reader import format_from_rpr

W = '{http://schemas.openxmlformats.org/wordprocessingml/2006/main}'
XML_SPACE = '{http://www.w3.org/XML/1998/namespace}space'


# ---------------------------------------------------------------------------
# 要素アクセス(IR と同じアドレス体系)
# ---------------------------------------------------------------------------

def _part_element(doc, op):
    part = op.get('part', 'body')
    if part == 'body':
        return doc.element.body
    section = doc.sections[op.get('section', 0)]
    prefix = {'default': '', 'first': 'first_page_', 'even': 'even_page_'}[op.get('ref', 'default')]
    return getattr(section, prefix + part)._element


def _blocks_of(parent):
    """直下の段落・表ブロック(w:sdt は中身を展開)。IR の blocks と同順。"""
    out = []
    for el in parent:
        if el.tag in (W + 'p', W + 'tbl'):
            out.append(el)
        elif el.tag == W + 'sdt':
            content = el.find(W + 'sdtContent')
            if content is not None:
                out.extend(c for c in content if c.tag in (W + 'p', W + 'tbl'))
    return out


def _span(tc):
    grid_span, v_merge = 1, None
    tc_pr = tc.find(W + 'tcPr')
    if tc_pr is not None:
        gs = tc_pr.find(W + 'gridSpan')
        if gs is not None:
            grid_span = int(gs.get(W + 'val') or 1)
        vm = tc_pr.find(W + 'vMerge')
        if vm is not None:
            v_merge = vm.get(W + 'val') or 'continue'
    return grid_span, v_merge


def _grid_origins(tbl):
    """グリッド座標 (row, col) → 結合起点の w:tc。"""
    origins = {}
    merging_cols = set()
    for r, tr in enumerate(tbl.findall(W + 'tr')):
        c = 0
        for tc in tr.findall(W + 'tc'):
            grid_span, v_merge = _span(tc)
            if not (v_merge == 'continue' and c in merging_cols):
                origins[(r, c)] = tc
                if v_merge == 'restart':
                    merging_cols.add(c)
                else:
                    merging_cols.discard(c)
            c += grid_span
    return origins


def _para_text(p):
    return ''.join(t.text or '' for t in p.iter(W + 't'))


def _has_numbering(p):
    return p.find(W + 'pPr/' + W + 'numPr') is not None


# ---------------------------------------------------------------------------
# テキスト差し替え(ラン書式を可能な限り維持)
# ---------------------------------------------------------------------------

def _run_fmt_key(r):
    """ラン書式の同一性判定キー。

    リーダーと同じ「意味のある書式」だけで比較する。Word は同一見た目でも
    言語属性等の差でランを分割するため、rPr 全体の比較では誤検出する。
    """
    return tuple(sorted(format_from_rpr(r.find(W + 'rPr')).items()))


def _set_paragraph_text_flat(p, text):
    """全文を先頭ランへ設定する(他のラン・図形等は削除)。単一書式段落用。"""
    runs = p.findall(W + 'r')
    first = runs[0] if runs else None
    for child in list(p):
        if child.tag == W + 'pPr' or child is first:
            continue
        p.remove(child)
    if first is None:
        first = etree.SubElement(p, W + 'r')
    for child in list(first):
        if child.tag != W + 'rPr':
            first.remove(child)
    t = etree.SubElement(first, W + 't')
    t.set(XML_SPACE, 'preserve')
    t.text = text


def _set_paragraph_text(p, text, warnings, where):
    """段落テキストを差し替える。

    雛形段落のラン書式が単一なら従来どおり先頭ランへ全文を設定する。
    混在書式の段落では、新旧テキストの共通接頭辞・接尾辞を保って変化した
    中間部分だけを差し替えることで、ラン書式を可能な限り維持する
    (「ラベル(太字)+値」型の欄は全文指定でも書式が保たれる)。
    差し替え範囲が複数のラン書式に跨る場合は先頭ランの書式に均し、警告を返す。
    """
    runs = [r for r in _iter_runs(p) if r.findall(W + 't')]
    keys = {_run_fmt_key(r) for r in runs}
    old = ''.join(t.text or '' for r in runs for t in r.findall(W + 't'))
    if len(keys) <= 1 or not old:
        _set_paragraph_text_flat(p, text)
        return
    if old == text:
        return

    i = 0
    limit = min(len(old), len(text))
    while i < limit and old[i] == text[i]:
        i += 1
    j = 0
    while j < limit - i and old[len(old) - 1 - j] == text[len(text) - 1 - j]:
        j += 1
    a, b = i, len(old) - j
    if a == b:  # 純挿入はラン境界で取りこぼすため、隣接1文字を含めて置換する
        if a > 0:
            a -= 1
        else:
            b += 1
    mid = text[a:len(text) - (len(old) - b)]

    pos = 0
    covered = set()
    for r in runs:
        length = sum(len(t.text or '') for t in r.findall(W + 't'))
        s, e = pos, pos + length
        pos = e
        if s < b and e > a:
            covered.add(_run_fmt_key(r))
    if len(covered) > 1:
        warnings.append(f'{where}: 差し替え範囲 {old[a:b]!r} が複数のラン書式に'
                        f'跨るため先頭ランの書式に均されます'
                        f'(replaceText / setFormat の利用を検討)')
    _rewrite_spans(p, [(a, b)], mid)


def _set_cell(tc, value, warnings, where, guard_numbering=False):
    texts = value if isinstance(value, list) else [value]
    paras = tc.findall(W + 'p')
    while len(paras) < len(texts):
        clone = copy.deepcopy(paras[-1])
        paras[-1].addnext(clone)
        paras.append(clone)
    while len(paras) > len(texts) and len(paras) > 1:
        tc.remove(paras.pop())
    for p, text in zip(paras, texts):
        if guard_numbering and _has_numbering(p) and text:
            warnings.append(f'{where}: 自動採番の段落のため値の設定をスキップ: {text!r}')
            continue
        _set_paragraph_text(p, text, warnings, where)


# ---------------------------------------------------------------------------
# 部分置換(ランの書式を保持)
# ---------------------------------------------------------------------------

def _rewrite_spans(p, spans, replace):
    """段落内ラン(図形は除外)の w:t 連結テキスト上で、各 span を replace に
    差し替える。span が複数ランにまたがる場合は、開始位置のランに置換文字列を
    入れ、後続ランの被覆部分を削る(先頭ランの書式に揃う)。"""
    ts = [t for r in _iter_runs(p) for t in r.findall(W + 't')]
    full = ''.join(t.text or '' for t in ts)
    pos = 0
    for t in ts:
        s, e = pos, pos + len(t.text or '')
        pos = e
        parts, cur = [], s
        for a, b in spans:
            if b <= s or a >= e:
                continue
            parts.append(full[cur:max(a, cur)])
            if a >= s:
                parts.append(replace)
            cur = min(b, e)
        new = ''.join(parts) + full[cur:e]
        if new != full[s:e]:
            t.text = new
            if new != new.strip():
                t.set(XML_SPACE, 'preserve')


def _replace_in_paragraph(p, find, replace, replace_all):
    """段落内の w:t 連結テキスト上で find を置換し、置換数を返す。"""
    full = ''.join(t.text or ''
                   for r in _iter_runs(p) for t in r.findall(W + 't'))
    spans = []
    start = full.find(find)
    while start >= 0:
        spans.append((start, start + len(find)))
        if not replace_all:
            break
        start = full.find(find, start + len(find))
    if not spans:
        return 0
    _rewrite_spans(p, spans, replace)
    return len(spans)


def _replace_text(target, op, warnings):
    find = op['find']
    if not find:
        raise ValueError('replaceText: find が空です')
    replace_all = op.get('all', False)
    paras = [target] if target.tag == W + 'p' else target.findall(W + 'p')
    count = 0
    for p in paras:
        count += _replace_in_paragraph(p, find, op.get('replace', ''), replace_all)
        if count and not replace_all:
            break
    if count == 0:
        where = f'block={op.get("block")}' + (
            f' ({op["row"]},{op["col"]})' if 'row' in op else '')
        warnings.append(f'replaceText: {find!r} が見つかりません({where})')


# ---------------------------------------------------------------------------
# 書式設定(setFormat)
# ---------------------------------------------------------------------------

_RUN_FMT_KEYS = ('bold', 'italic', 'underline', 'strike',
                 'color', 'highlight', 'sizePt', 'font')

# OOXML スキーマの子要素順(順序を保って挿入するための序列)
_RPR_ORDER = ('rStyle', 'rFonts', 'b', 'bCs', 'i', 'iCs', 'caps', 'smallCaps',
              'strike', 'dstrike', 'outline', 'shadow', 'emboss', 'imprint',
              'noProof', 'snapToGrid', 'vanish', 'webHidden', 'color',
              'spacing', 'w', 'kern', 'position', 'sz', 'szCs', 'highlight',
              'u', 'effect', 'bdr', 'shd', 'fitText', 'vertAlign', 'rtl',
              'cs', 'em', 'lang', 'eastAsianLayout')
_PPR_ORDER = ('pStyle', 'keepNext', 'keepLines', 'pageBreakBefore', 'framePr',
              'widowControl', 'numPr', 'suppressLineNumbers', 'pBdr', 'shd',
              'tabs', 'suppressAutoHyphens', 'kinsoku', 'wordWrap',
              'overflowPunct', 'topLinePunct', 'autoSpaceDE', 'autoSpaceDN',
              'bidi', 'adjustRightInd', 'snapToGrid', 'spacing', 'ind',
              'contextualSpacing', 'mirrorIndents', 'suppressOverlap', 'jc',
              'textDirection', 'textAlignment', 'outlineLvl', 'rPr', 'sectPr')
_TCPR_ORDER = ('cnfStyle', 'tcW', 'gridSpan', 'hMerge', 'vMerge', 'tcBorders',
               'shd', 'noWrap', 'tcMar', 'textDirection', 'tcFitText',
               'vAlign', 'hideMark')


def _ordered_child(parent, tag, order):
    """parent 直下の w:tag を取得(無ければスキーマ順を保って挿入)。"""
    el = parent.find(W + tag)
    if el is not None:
        return el
    el = etree.Element(W + tag)
    rank = order.index(tag)
    for child in parent:
        local = etree.QName(child).localname
        if local in order and order.index(local) > rank:
            child.addprevious(el)
            return el
    parent.append(el)
    return el


def _props_of(el, tag):
    """w:r / w:p / w:tc の先頭プロパティ要素を取得(無ければ作成)。"""
    pr = el.find(W + tag)
    if pr is None:
        pr = etree.Element(W + tag)
        el.insert(0, pr)
    return pr


def _apply_run_format(r, fmt):
    r_pr = _props_of(r, 'rPr')
    for key, tag in (('bold', 'b'), ('italic', 'i'), ('strike', 'strike')):
        if key in fmt:
            el = _ordered_child(r_pr, tag, _RPR_ORDER)
            if fmt[key]:
                el.attrib.pop(W + 'val', None)
            else:
                el.set(W + 'val', '0')  # スタイル継承の上書きとして明示的に無効化
    if 'underline' in fmt:
        _ordered_child(r_pr, 'u', _RPR_ORDER).set(
            W + 'val', 'single' if fmt['underline'] else 'none')
    if 'color' in fmt:
        _ordered_child(r_pr, 'color', _RPR_ORDER).set(
            W + 'val', str(fmt['color']).lstrip('#'))
    if 'highlight' in fmt:
        _ordered_child(r_pr, 'highlight', _RPR_ORDER).set(W + 'val', fmt['highlight'])
    if 'sizePt' in fmt:
        val = str(int(round(float(fmt['sizePt']) * 2)))
        _ordered_child(r_pr, 'sz', _RPR_ORDER).set(W + 'val', val)
        _ordered_child(r_pr, 'szCs', _RPR_ORDER).set(W + 'val', val)
    if 'font' in fmt:
        el = _ordered_child(r_pr, 'rFonts', _RPR_ORDER)
        for attr in ('ascii', 'hAnsi', 'eastAsia'):
            el.set(W + attr, fmt['font'])


def _iter_runs(el):
    """段落直下のラン(w:hyperlink 等は透過、図形の中へは降りない)。"""
    for child in el:
        tag = child.tag
        if tag in (W + 'pPr', W + 'drawing', W + 'pict', W + 'object'):
            continue
        if tag == W + 'r':
            yield child
        else:
            yield from _iter_runs(child)


def _split_run(r, cuts):
    """ランを w:t 連結テキストの相対オフセット cuts で分割する(rPr は複製)。"""
    parent = r.getparent()
    idx = parent.index(r)
    r_pr = r.find(W + 'rPr')
    frags = [[] for _ in range(len(cuts) + 1)]
    bounds = list(cuts) + [None]
    fi = 0
    pos = 0
    for child in list(r):
        if child.tag == W + 'rPr':
            continue
        while bounds[fi] is not None and pos >= bounds[fi]:
            fi += 1
        if child.tag == W + 't':
            text = child.text or ''
            start = 0
            while start < len(text):
                limit = bounds[fi]
                if limit is None or pos + (len(text) - start) <= limit:
                    frags[fi].append(('t', text[start:]))
                    pos += len(text) - start
                    break
                take = limit - pos
                if take:
                    frags[fi].append(('t', text[start:start + take]))
                pos = limit
                start += take
                fi += 1
        else:
            frags[fi].append(('el', child))
    parent.remove(r)
    for i, frag in enumerate(frags):
        new_run = etree.Element(W + 'r')
        if r_pr is not None:
            new_run.append(copy.deepcopy(r_pr))
        for kind, val in frag:
            if kind == 'el':
                new_run.append(val)
            else:
                t = etree.SubElement(new_run, W + 't')
                t.text = val
                if val != val.strip():
                    t.set(XML_SPACE, 'preserve')
        parent.insert(idx + i, new_run)


def _format_span(p, find, run_fmt, fmt_all):
    """段落内の find 一致範囲だけに書式を当てる(範囲境界でランを分割)。"""
    runs = list(_iter_runs(p))
    texts = [''.join(t.text or '' for t in r.findall(W + 't')) for r in runs]
    full = ''.join(texts)
    spans = []
    start = full.find(find)
    while start >= 0:
        spans.append((start, start + len(find)))
        if not fmt_all:
            break
        start = full.find(find, start + len(find))
    if not spans:
        return 0

    pos = 0
    for r, text in zip(runs, texts):
        s, e = pos, pos + len(text)
        pos = e
        cuts = sorted({x - s for a, b in spans for x in (a, b) if s < x < e})
        if cuts:
            _split_run(r, cuts)

    pos = 0
    for r in _iter_runs(p):
        text = ''.join(t.text or '' for t in r.findall(W + 't'))
        s, e = pos, pos + len(text)
        pos = e
        if text and any(a <= s and e <= b for a, b in spans):
            _apply_run_format(r, run_fmt)
    return len(spans)


def _set_format(target, op, warnings):
    fmt = op.get('format') or {}
    unknown = fmt.keys() - set(_RUN_FMT_KEYS) - {'align', 'shading'}
    if not fmt:
        raise ValueError('setFormat: format が空です')
    if unknown:
        raise ValueError(f'setFormat: 未知の書式キー {sorted(unknown)}')

    is_cell = target.tag == W + 'tc'
    if 'shading' in fmt:
        if not is_cell:
            raise ValueError('setFormat: shading は row / col で指定したセルのみ')
        tc_pr = _props_of(target, 'tcPr')
        shd = _ordered_child(tc_pr, 'shd', _TCPR_ORDER)
        shd.set(W + 'val', 'clear')
        shd.set(W + 'fill', str(fmt['shading']).lstrip('#'))

    paras = [target] if target.tag == W + 'p' else target.findall(W + 'p')
    if 'align' in fmt:
        for p in paras:
            p_pr = _props_of(p, 'pPr')
            _ordered_child(p_pr, 'jc', _PPR_ORDER).set(W + 'val', fmt['align'])

    run_fmt = {k: v for k, v in fmt.items() if k in _RUN_FMT_KEYS}
    if not run_fmt:
        return
    if op.get('find'):
        count = 0
        for p in paras:
            count += _format_span(p, op['find'], run_fmt, op.get('all', False))
            if count and not op.get('all', False):
                break
        if count == 0:
            warnings.append(f'setFormat: {op["find"]!r} が見つかりません'
                            f'(block={op.get("block")})')
    else:
        for p in paras:
            for r in _iter_runs(p):
                _apply_run_format(r, run_fmt)


# ---------------------------------------------------------------------------
# 操作の解決と適用
# ---------------------------------------------------------------------------

def _resolve(doc, op):
    """雛形の状態を基準に、操作対象の要素を特定する。"""
    kind = op['op']
    parent = _part_element(doc, op)
    blocks = _blocks_of(parent)

    if kind == 'appendSection':
        try:
            sources = [blocks[item['cloneFrom']] for item in op['blocks']]
        except IndexError:
            raise ValueError('appendSection: cloneFrom が範囲外です')
        anchor = blocks[op['insertAfter']] if 'insertAfter' in op else None
        return {'parent': parent, 'sources': sources, 'anchor': anchor}

    if 'block' in op:
        try:
            target = blocks[op['block']]
        except IndexError:
            raise ValueError(f'{kind}: block={op["block"]} は範囲外です')
    elif kind == 'setParagraph' and 'match' in op:
        hits = [b for b in blocks if b.tag == W + 'p' and _para_text(b) == op['match']]
        if len(hits) != 1:
            raise ValueError(f'setParagraph: match={op["match"]!r} の該当が {len(hits)} 件')
        target = hits[0]
    else:
        raise ValueError(f'{kind}: block または match を指定してください')

    if kind in ('setCell', 'fillTable') and target.tag != W + 'tbl':
        raise ValueError(f'{kind}: block={op.get("block")} は表ではありません')
    if kind == 'setParagraph' and target.tag != W + 'p':
        raise ValueError(f'setParagraph: block={op.get("block")} は段落ではありません')
    if kind in ('replaceText', 'setFormat') and target.tag == W + 'tbl' and \
            ('row' not in op or 'col' not in op):
        raise ValueError(f'{kind}: block={op.get("block")} は表のため'
                         f' row / col を指定してください')

    if kind == 'setCell' or \
            (kind in ('replaceText', 'setFormat') and target.tag == W + 'tbl'):
        origins = _grid_origins(target)
        key = (op['row'], op['col'])
        if key not in origins:
            raise ValueError(
                f'{kind}: ({op["row"]},{op["col"]}) は起点セルではありません'
                f'(結合の被覆側か範囲外)')
        return origins[key]
    return target


def _apply(op, target, warnings):
    kind = op['op']
    if kind == 'setCell':
        _set_cell(target, op.get('paragraphs', op.get('text', '')), warnings,
                  f'setCell({op["row"]},{op["col"]})')
    elif kind == 'setParagraph':
        _set_paragraph_text(target, op['text'], warnings,
                            f'setParagraph(block={op.get("block")})')
    elif kind == 'replaceText':
        _replace_text(target, op, warnings)
    elif kind == 'setFormat':
        _set_format(target, op, warnings)
    elif kind == 'deleteBlock':
        target.getparent().remove(target)
    elif kind == 'fillTable':
        _fill_table(target, op, warnings)
    elif kind == 'appendSection':
        _append_section(op, target, warnings)
    else:
        raise ValueError(f'未知の操作: {kind}')


def _append_section(op, resolved, warnings):
    parent = resolved['parent']
    anchor = resolved['anchor']
    if anchor is None:
        # 末尾へ追加する。本文の場合は末尾の sectPr の直前に入れる
        sect_pr = parent.find(W + 'sectPr')
        anchor = sect_pr.getprevious() if sect_pr is not None else None

    for item, source in zip(op['blocks'], resolved['sources']):
        clone = copy.deepcopy(source)
        if anchor is None:
            parent.insert(0, clone)
        else:
            anchor.addnext(clone)
        anchor = clone
        if clone.tag == W + 'p':
            _set_paragraph_text(clone, item['text'], warnings, 'appendSection')
        elif clone.tag == W + 'tbl':
            _fill_table(clone, item, warnings)
        else:
            raise ValueError('appendSection: 複製できるのは段落と表のみです')


def _fill_table(tbl, op, warnings):
    trs = tbl.findall(W + 'tr')
    data_row = op['dataRow']
    if not 0 < data_row < len(trs):
        raise ValueError(f'fillTable: dataRow={data_row} は範囲外です')
    proto = copy.deepcopy(trs[data_row])
    for tr in trs[data_row:]:
        tbl.remove(tr)
    for ri, values in enumerate(op['rows']):
        tr = copy.deepcopy(proto)
        tbl.append(tr)
        c = 0
        for tc in tr.findall(W + 'tc'):
            grid_span, _ = _span(tc)
            value = values[c] if c < len(values) else None
            if value is not None:
                _set_cell(tc, value, warnings, f'fillTable 行{ri} 列{c}',
                          guard_numbering=True)
            c += grid_span


# ---------------------------------------------------------------------------
# 自動採番の正規化(表ごとに独立採番)
# ---------------------------------------------------------------------------

def _numbering_root(doc):
    """文書に関連付く numbering パートの w:numbering 要素(無ければ None)。"""
    from docx.opc.constants import RELATIONSHIP_TYPE as RT
    try:
        return doc.part.part_related_by(RT.NUMBERING).element
    except KeyError:
        return None


def _set_direct_numpr(p_pr, num_id, ilvl):
    """段落 pPr に直接 numPr(ilvl, numId)を設定する(無ければ作成・あれば上書き)。

    スタイル由来の採番を表ごとに独立させる際、段落へ直接の numPr を被せて
    スタイル採番を上書きするのに使う(直接 numId の振り直しにも使える)。
    """
    num_pr = _ordered_child(p_pr, 'numPr', _PPR_ORDER)
    ilvl_el = num_pr.find(W + 'ilvl')
    if ilvl_el is None:
        ilvl_el = etree.Element(W + 'ilvl')
        num_pr.insert(0, ilvl_el)               # numPr 内は ilvl, numId の順
    ilvl_el.set(W + 'val', ilvl)
    num_id_el = num_pr.find(W + 'numId')
    if num_id_el is None:
        num_id_el = etree.SubElement(num_pr, W + 'numId')
    num_id_el.set(W + 'val', num_id)


def normalize_table_numbering(doc):
    """表ごとに独立した自動採番(各表 1 始まり)になるよう正規化する。

    Word の自動採番は numId 単位で 1 つの連番カウンタを共有するため、表を
    複製(appendSection)したり凡例が複数表で numId を共有していると、表を
    またいで連番が継続してしまう(リセットされず地続きになる)。
    文書内の表を出現順に走査し、ある実効 numId(連番=ordered)を最初に使う表は
    維持し、2 つ目以降の表には「同一 abstractNum を指し開始値を 1 にリセットする
    新しい numId」を割り当て直す。

    採番が段落の直接 numPr でなく **段落スタイル由来**(heading 等)の場合は、
    スタイルを書き換えられない(共有)ため、当該段落へ直接 numPr を被せて
    表ごとに独立させる。直接 numPr の場合はその numId を振り直す。
    新規作成した numId の個数を返す。
    """
    numbering = _numbering_root(doc)
    if numbering is None:
        return 0
    # 効果的採番(直接 + スタイル由来)を解決する文脈を組む
    styles_el = doc.styles.element if doc.styles is not None else None
    style_num = docx_reader._style_numbering_from_styles(styles_el)
    resolver = docx_reader._numbering_resolver_from_root(numbering, style_num)
    ctx = {'styleNum': style_num, 'numbering': resolver}

    num_to_abs = {}
    used = []
    for num in numbering.findall(W + 'num'):
        nid = num.get(W + 'numId')
        if nid is None:
            continue
        used.append(int(nid))
        abs_el = num.find(W + 'abstractNumId')
        if abs_el is not None:
            num_to_abs[nid] = abs_el.get(W + 'val')
    next_id = max(used) + 1 if used else 1

    def fresh_num(abstract_id, ilvls):
        nonlocal next_id
        new_id = str(next_id)
        next_id += 1
        num = etree.SubElement(numbering, W + 'num')
        num.set(W + 'numId', new_id)
        abs_el = etree.SubElement(num, W + 'abstractNumId')
        abs_el.set(W + 'val', abstract_id)
        for ilvl in sorted(ilvls):
            ov = etree.SubElement(num, W + 'lvlOverride')
            ov.set(W + 'ilvl', ilvl)
            so = etree.SubElement(ov, W + 'startOverride')
            so.set(W + 'val', '1')
        return new_id

    created = 0
    claimed = set()
    for tbl in doc.element.body.iter(W + 'tbl'):
        paras = []        # (p_pr, 実効 numId, 実効 ilvl)
        ilvls = {}        # 実効 numId -> 使用 ilvl 集合
        for p in tbl.iter(W + 'p'):
            p_pr = p.find(W + 'pPr')
            if p_pr is None:
                continue
            p_style = p_pr.find(W + 'pStyle')
            style_id = p_style.get(W + 'val') if p_style is not None else None
            eff = docx_reader._effective_numbering(p_pr, style_id, ctx)
            if not eff or not eff.get('ordered'):
                continue            # 連番(ordered)のみ対象。箇条書きは振り直さない
            nid = eff.get('numId')
            if nid is None:
                continue
            ilvl = eff.get('level') or '0'
            paras.append((p_pr, nid, ilvl))
            ilvls.setdefault(nid, set()).add(ilvl)
        remap = {}
        for nid in ilvls:
            if nid not in claimed:
                claimed.add(nid)            # 最初に使う表は維持
            elif nid in num_to_abs:
                remap[nid] = fresh_num(num_to_abs[nid], ilvls[nid])
                created += 1
        for p_pr, nid, ilvl in paras:
            if nid in remap:
                _set_direct_numpr(p_pr, remap[nid], ilvl)
    return created


def normalize_numbering_file(path):
    """既存 docx を開いて表ごとの独立採番に正規化し、変更があれば上書きする。

    新たに numId を割り当て直した場合のみ保存し、True を返す。
    """
    doc = Document(path)
    if normalize_table_numbering(doc):
        doc.save(path)
        return True
    return False


def apply_spec(spec):
    """記入スペックを適用して docx を生成し、警告のリストを返す。"""
    doc = Document(spec['template'])
    warnings = []
    resolved = [_resolve(doc, op) for op in spec['operations']]
    for op, target in zip(spec['operations'], resolved):
        _apply(op, target, warnings)
    # 複製・凡例由来で表をまたいだ連番継続が起きないよう、保存前に採番を正規化する
    normalize_table_numbering(doc)
    doc.save(spec['output'])
    return warnings
