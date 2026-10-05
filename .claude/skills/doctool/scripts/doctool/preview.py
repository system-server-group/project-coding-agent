"""doctool プレビュー — IR(docx/xlsx)を確認用の Markdown へ整形する。
"""


def _flatten_blocks(blocks):
    parts = []
    for b in blocks:
        if b['type'] == 'paragraph':
            t = _para_inline(b)
            if t:
                parts.append(t)
        elif b['type'] == 'table':
            parts.append('《表》')
    return ' / '.join(parts)


def _para_inline(b):
    t = b.get('text', '').replace('\n', ' ⏎ ')
    for f in b.get('fields', []):
        t += f'⟨{f}⟩'
    for d in b.get('drawings', []):
        if d['type'] == 'textbox':
            t += f'【テキストボックス: {_flatten_blocks(d.get("blocks", []))}】'
        else:
            t += '【画像】' if d['type'] == 'image' else '【図形】'
    if not t and b.get('numbering'):
        t = '(自動採番)'
    return t


def _md_table(tbl, lines):
    grid = [[''] * tbl['colCount'] for _ in range(tbl['rowCount'])]
    nested = []
    for cell in tbl['cells']:
        parts = []
        for blk in cell['blocks']:
            if blk['type'] == 'paragraph':
                t = _para_inline(blk)
                if t:
                    parts.append(t)
            elif blk['type'] == 'table':
                nested.append(blk)
                parts.append(f'《入れ子表{len(nested)}》')
        r0, c0 = cell['row'], cell['col']
        for rr in range(r0, r0 + cell['rowSpan']):
            for cc in range(c0, c0 + cell['colSpan']):
                grid[rr][cc] = '^' if rr > r0 else ('<' if cc > c0 else '')
        grid[r0][c0] = ' / '.join(parts).replace('|', '\\|')
    for i, row in enumerate(grid):
        lines.append('| ' + ' | '.join(row) + ' |')
        if i == 0:
            lines.append('|' + ' --- |' * tbl['colCount'])
    lines.append('')
    for i, nt in enumerate(nested, 1):
        lines.append(f'《入れ子表{i}》:')
        _md_table(nt, lines)


def _md_blocks(blocks, lines):
    for b in blocks:
        if b['type'] == 'paragraph':
            t = _para_inline(b)
            if t:
                lines.append(f'**{t}**' if b.get('style') else t)
        elif b['type'] == 'table':
            _md_table(b, lines)
    lines.append('')


def _xlsx_markdown(ir, lines):
    for sheet in ir['sheets']:
        hidden = '(非表示)' if sheet.get('hidden') else ''
        lines.append(f"## シート: {sheet['name']}{hidden}")
        lines.append('')
        blocks = sheet['blocks']
        for b in blocks:
            if b['type'] == 'table':
                _md_table(b, lines)
        shapes = [b for b in blocks if b['type'] == 'shape']
        connectors = [b for b in blocks if b['type'] == 'connector']
        images = [b for b in blocks if b['type'] == 'image']
        if shapes:
            lines.append('### 図形')
            for s in shapes:
                pos = s.get('anchor', {}).get('from')
                at = f" @r{pos[0] + 1}c{pos[1] + 1}" if pos else ''
                text = s.get('text', '').replace('\n', ' ⏎ ')
                lines.append(f"- [{s.get('shape', '?')}]{at}: {text or s.get('name', '')}")
            lines.append('')
        if connectors:
            lines.append('### 接続(遷移グラフ)')
            for c in connectors:
                src = c.get('from') or f"id={c.get('fromId', '?')}"
                dst = c.get('to') or f"id={c.get('toId', '?')}"
                lines.append(f"- {src.replace(chr(10), ' ')} → {dst.replace(chr(10), ' ')}")
            lines.append('')
        if images:
            lines.append(f'(貼り付け画像 {len(images)} 件 — 内容はXMLから取得不可)')
            lines.append('')
        if not blocks:
            lines.append('(内容なし)')
            lines.append('')


def to_markdown(ir):
    lines = [f"# {ir['source']['path']}", '']
    if ir['source'].get('kind') == 'xlsx':
        _xlsx_markdown(ir, lines)
        return '\n'.join(lines).rstrip() + '\n'
    for i, sec in enumerate(ir.get('sections', [])):
        for label, key in (('ヘッダー', 'headers'), ('フッター', 'footers')):
            for ref_type, blocks in sec.get(key, {}).items():
                if any(_para_inline(b) if b['type'] == 'paragraph' else True
                       for b in blocks):
                    lines.append(f'## {label}({ref_type})')
                    _md_blocks(blocks, lines)
    lines.append('## 本文')
    _md_blocks(ir['body'], lines)
    return '\n'.join(lines).rstrip() + '\n'
