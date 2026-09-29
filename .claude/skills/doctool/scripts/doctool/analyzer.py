"""doctool template analyzer — IR から雛形の記入ストラテジを導出する。

.claude/skills/docgen-exec/SKILL.md §2 の「生成前に雛形を解析して記入ストラテジを決める」のコード化。
IR から事実のみを導出し、解釈(どの欄に何を書くか)は AI/人に委ねる。

混在書式(mixedFormatCells / mixedFormat)の検出には書式オーバーレイ付き IR
(read_docx(with_format=True))が必要。CLI の analyze は常にオーバーレイ付きで読む。
"""
from ir import prune


def _is_mixed_format(p):
    """書式オーバーレイ付き IR で、段落内にラン書式の切り替えがあるか。

    リーダーは隣接する同一書式ランを結合するため、runs が2要素以上なら
    段落内に書式差があると判断できる。
    """
    return len(p.get('runs', [])) > 1


def _analyze_table(index, tbl):
    auto_cells, field_cells, multi_para, mixed_cells = [], [], [], []
    ordered_cells, style_num_cells = [], []
    for cell in tbl['cells']:
        pos = {'row': cell['row'], 'col': cell['col']}
        paras = [b for b in cell['blocks'] if b['type'] == 'paragraph']
        nums = [p['numbering'] for p in paras if p.get('numbering')]
        if nums:
            auto_cells.append(pos)
            if any(n.get('ordered') for n in nums):
                ordered_cells.append(pos)
            if any(n.get('source') == 'style' for n in nums):
                style_num_cells.append(pos)
        fields = [f for p in paras for f in p.get('fields', [])]
        if fields:
            field_cells.append({**pos, 'fields': fields})
        if len(paras) > 1:
            multi_para.append({**pos, 'paragraphs': len(paras)})
        if any(_is_mixed_format(p) for p in paras):
            mixed_cells.append(pos)
    merged = [{k: c[k] for k in ('row', 'col', 'rowSpan', 'colSpan')}
              for c in tbl['cells'] if c['rowSpan'] > 1 or c['colSpan'] > 1]

    notes = []
    # 連番(ordered)列のデータ行のみ fillTable null の対象。箇条書き(bullet)は除く
    auto_cols = {p['col'] for p in ordered_cells}
    for col in sorted(auto_cols):
        data_rows = [p['row'] for p in ordered_cells if p['col'] == col]
        if len(data_rows) >= 2 and 0 not in data_rows:
            notes.append(f'列{col}はデータ行が自動採番 — fillTable では null を渡し値を入れない')
    if style_num_cells:
        cols = sorted({p['col'] for p in style_num_cells})
        notes.append(f'列{cols}に書式(スタイル)由来の自動採番 — 段落の numPr ではなく'
                     f'スタイル定義に依存する(直接の numId 書換では制御不可)')
    for fc in field_cells:
        notes.append(f"({fc['row']},{fc['col']})はフィールド{fc['fields']} — 雛形のまま触らない")
    for mc in mixed_cells:
        notes.append(f"({mc['row']},{mc['col']})は混在書式 — setCell の全文差し替えで"
                     f"共通部分が残らない場合は書式が均される。replaceText / setFormat を検討")

    return prune({
        'block': index, 'rowCount': tbl['rowCount'], 'colCount': tbl['colCount'],
        'autoNumberCells': auto_cells, 'fieldCells': field_cells,
        'mergedCells': merged, 'multiParagraphCells': multi_para,
        'mixedFormatCells': mixed_cells, 'notes': notes,
    })


def _iter_parts(ir):
    for si, sec in enumerate(ir.get('sections', [])):
        for kind in ('headers', 'footers'):
            for ref, blocks in sec.get(kind, {}).items():
                yield {'part': kind[:-1], 'ref': ref, 'section': si}, blocks
    yield {'part': 'body'}, ir.get('body', [])


def analyze_ir(ir):
    """IR から雛形の記入ストラテジ(自動採番・フィールド・結合・複数段落セル)を導出する。"""
    parts = []
    for addr, blocks in _iter_parts(ir):
        tables, paragraphs = [], []
        for i, b in enumerate(blocks):
            if b['type'] == 'table':
                tables.append(_analyze_table(i, b))
            elif b.get('fields') or b.get('numbering') or _is_mixed_format(b):
                num = b.get('numbering')
                paragraphs.append(prune({
                    'block': i, 'text': b.get('text', ''),
                    'fields': b.get('fields'),
                    'autoNumber': True if num else None,
                    'numberFormat': num.get('format') if num else None,
                    'numberSource': num.get('source') if num else None,
                    'mixedFormat': True if _is_mixed_format(b) else None,
                }))
        entry = prune({**addr, 'tables': tables, 'paragraphs': paragraphs})
        if entry.keys() - addr.keys():
            parts.append(entry)
    return {'template': ir['source']['path'], 'parts': parts}
