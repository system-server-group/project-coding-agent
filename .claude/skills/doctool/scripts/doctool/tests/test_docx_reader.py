"""doctool docx リーダーのテスト。

実行: PYTHONUTF8=1 python .claude/skills/doctool/scripts/doctool/tests/test_docx_reader.py
"""
import io
import sys
import unittest
import zipfile
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))
import analyzer
import docx_reader
import preview

REPO_ROOT = Path(__file__).resolve().parents[6]
HANREI_DIR = REPO_ROOT / 'docs' / 'specs' / '詳細設計_書式凡例'

NS = (
    'xmlns:w="http://schemas.openxmlformats.org/wordprocessingml/2006/main" '
    'xmlns:r="http://schemas.openxmlformats.org/officeDocument/2006/relationships" '
    'xmlns:wp="http://schemas.openxmlformats.org/drawingml/2006/wordprocessingDrawing" '
    'xmlns:a="http://schemas.openxmlformats.org/drawingml/2006/main" '
    'xmlns:wps="http://schemas.microsoft.com/office/word/2010/wordprocessingShape"'
)

DOCUMENT_XML = f'''<?xml version="1.0" encoding="UTF-8"?>
<w:document {NS}><w:body>
  <w:p>
    <w:pPr><w:pStyle w:val="Heading1"/></w:pPr>
    <w:r><w:t>タイトル</w:t></w:r>
  </w:p>
  <w:tbl>
    <w:tr>
      <w:tc><w:tcPr><w:gridSpan w:val="2"/></w:tcPr>
        <w:p><w:r><w:t>結合見出し</w:t></w:r></w:p></w:tc>
    </w:tr>
    <w:tr>
      <w:tc><w:tcPr><w:vMerge w:val="restart"/></w:tcPr>
        <w:p><w:r><w:t>縦結合</w:t></w:r></w:p></w:tc>
      <w:tc>
        <w:p><w:r><w:t>A</w:t></w:r></w:p>
        <w:tbl><w:tr><w:tc><w:p><w:r><w:t>入れ子</w:t></w:r></w:p></w:tc></w:tr></w:tbl>
        <w:p/>
      </w:tc>
    </w:tr>
    <w:tr>
      <w:tc><w:tcPr><w:vMerge/></w:tcPr><w:p/></w:tc>
      <w:tc>
        <w:p><w:pPr><w:numPr><w:ilvl w:val="0"/><w:numId w:val="5"/></w:numPr></w:pPr></w:p>
      </w:tc>
    </w:tr>
  </w:tbl>
  <w:p>
    <w:r><w:fldChar w:fldCharType="begin"/></w:r>
    <w:r><w:instrText> PAGE </w:instrText></w:r>
    <w:r><w:fldChar w:fldCharType="end"/></w:r>
  </w:p>
  <w:p><w:r><w:drawing><wp:inline><a:graphic><a:graphicData>
    <wps:wsp><wps:txbx><w:txbxContent>
      <w:p><w:r><w:t>枠内テキスト</w:t></w:r></w:p>
    </w:txbxContent></wps:txbx></wps:wsp>
  </a:graphicData></a:graphic></wp:inline></w:drawing></w:r></w:p>
</w:body></w:document>'''

DOCUMENT_WITH_HEADER_XML = f'''<?xml version="1.0" encoding="UTF-8"?>
<w:document {NS}><w:body>
  <w:p><w:r><w:t>本文</w:t></w:r></w:p>
  <w:sectPr>
    <w:headerReference w:type="default" r:id="rId1"/>
    <w:titlePg/>
  </w:sectPr>
</w:body></w:document>'''

HEADER_XML = f'''<?xml version="1.0" encoding="UTF-8"?>
<w:hdr {NS}><w:p><w:r><w:t>ヘッダーテスト</w:t></w:r></w:p></w:hdr>'''

RELS_XML = '''<?xml version="1.0" encoding="UTF-8"?>
<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">
  <Relationship Id="rId1"
    Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/header"
    Target="header1.xml"/>
</Relationships>'''

STYLES_XML = '''<?xml version="1.0" encoding="UTF-8"?>
<w:styles xmlns:w="http://schemas.openxmlformats.org/wordprocessingml/2006/main">
  <w:style w:type="paragraph" w:styleId="Heading1"><w:name w:val="見出し 1"/></w:style>
</w:styles>'''


def build_docx(parts):
    buf = io.BytesIO()
    with zipfile.ZipFile(buf, 'w') as z:
        for name, data in parts.items():
            z.writestr(name, data)
    buf.seek(0)
    return buf


class DocxReaderTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.ir = docx_reader.read_docx(build_docx({
            'word/document.xml': DOCUMENT_XML,
            'word/styles.xml': STYLES_XML,
        }), source_name='fixture.docx')
        cls.table = cls.ir['body'][1]

    def cell(self, row, col):
        return next(c for c in self.table['cells']
                    if c['row'] == row and c['col'] == col)

    def test_paragraph_style_resolved(self):
        p = self.ir['body'][0]
        self.assertEqual(p['text'], 'タイトル')
        self.assertEqual(p['style'], {'id': 'Heading1', 'name': '見出し 1'})

    def test_table_shape(self):
        self.assertEqual(self.table['rowCount'], 3)
        self.assertEqual(self.table['colCount'], 2)

    def test_colspan(self):
        self.assertEqual(self.cell(0, 0)['colSpan'], 2)

    def test_rowspan_and_merged_cell_folded(self):
        self.assertEqual(self.cell(1, 0)['rowSpan'], 2)
        # vMerge continue のセルは起点に畳み込まれ、(2,0) は存在しない
        self.assertFalse(any(c['row'] == 2 and c['col'] == 0
                             for c in self.table['cells']))

    def test_nested_table(self):
        blocks = self.cell(1, 1)['blocks']
        self.assertEqual([b['type'] for b in blocks],
                         ['paragraph', 'table', 'paragraph'])
        nested = blocks[1]
        self.assertEqual(nested['cells'][0]['blocks'][0]['text'], '入れ子')

    def test_numbering(self):
        p = self.cell(2, 1)['blocks'][0]
        # 直接 numPr。numbering.xml が無いため format/ordered は未解決
        self.assertEqual(p['numbering'],
                         {'numId': '5', 'level': '0', 'source': 'direct'})

    def test_field(self):
        self.assertEqual(self.ir['body'][2]['fields'], ['PAGE'])

    def test_textbox(self):
        drawings = self.ir['body'][3]['drawings']
        self.assertEqual(drawings[0]['type'], 'textbox')
        self.assertEqual(drawings[0]['blocks'][0]['text'], '枠内テキスト')

    def test_preview_renders_merges(self):
        md = preview.to_markdown(self.ir)
        self.assertIn('| 結合見出し | < |', md)
        self.assertIn('《入れ子表1》', md)
        self.assertIn('(自動採番)', md)

    def test_preview_gfm_table(self):
        # 1行目直後に区切り行があり GFM テーブルとして成立する
        md = preview.to_markdown(self.ir)
        lines = md.splitlines()
        header = lines.index('| 結合見出し | < |')
        self.assertEqual(lines[header + 1], '| --- | --- |')

    def test_format_overlay_off_by_default(self):
        # 既定の IR は設計原則どおり書式を持たない
        for b in self.ir['body']:
            self.assertNotIn('runs', b)
            self.assertNotIn('align', b)

    def test_preview_escapes_pipe(self):
        ir = docx_reader.read_docx(build_docx({
            'word/document.xml': DOCUMENT_XML.replace('結合見出し', 'A|B'),
            'word/styles.xml': STYLES_XML,
        }), source_name='fixture.docx')
        self.assertIn('| A\\|B | < |', preview.to_markdown(ir))


FORMATTED_XML = f'''<?xml version="1.0" encoding="UTF-8"?>
<w:document {NS}><w:body>
  <w:p>
    <w:pPr><w:jc w:val="center"/>
      <w:spacing w:before="120" w:after="60"/>
      <w:ind w:left="420" w:firstLine="210"/></w:pPr>
    <w:r><w:rPr><w:b/><w:color w:val="FF0000"/></w:rPr><w:t>必須</w:t></w:r>
    <w:r><w:rPr><w:b/><w:color w:val="FF0000"/></w:rPr><w:t>項目</w:t></w:r>
    <w:r><w:t>を入力</w:t></w:r>
    <w:r><w:rPr><w:sz w:val="21"/><w:u w:val="single"/></w:rPr><w:t>注記</w:t></w:r>
  </w:p>
  <w:tbl>
    <w:tblPr><w:tblBorders><w:top w:val="single"/><w:insideH w:val="dashed"/>
      </w:tblBorders></w:tblPr>
    <w:tr>
      <w:tc><w:tcPr><w:shd w:val="clear" w:fill="D9D9D9"/>
          <w:tcBorders><w:bottom w:val="double"/></w:tcBorders></w:tcPr>
        <w:p><w:r><w:t>見出しセル</w:t></w:r></w:p></w:tc>
    </w:tr>
  </w:tbl>
</w:body></w:document>'''

FORMAT_STYLES_XML = '''<?xml version="1.0" encoding="UTF-8"?>
<w:styles xmlns:w="http://schemas.openxmlformats.org/wordprocessingml/2006/main">
  <w:docDefaults><w:rPrDefault><w:rPr>
    <w:rFonts w:eastAsia="ＭＳ 明朝"/><w:sz w:val="21"/>
  </w:rPr></w:rPrDefault></w:docDefaults>
  <w:style w:type="paragraph" w:styleId="Base">
    <w:name w:val="base"/><w:rPr><w:b/></w:rPr></w:style>
  <w:style w:type="paragraph" w:styleId="H1">
    <w:name w:val="見出し 1"/><w:basedOn w:val="Base"/>
    <w:pPr><w:jc w:val="center"/></w:pPr><w:rPr><w:sz w:val="28"/></w:rPr></w:style>
</w:styles>'''


class FormatOverlayTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.ir = docx_reader.read_docx(build_docx({
            'word/document.xml': FORMATTED_XML,
            'word/styles.xml': FORMAT_STYLES_XML,
        }), source_name='fixture.docx', with_format=True)

    def test_runs_merged_by_format(self):
        p = self.ir['body'][0]
        # 同一書式の隣接ランは結合される(必須+項目)
        self.assertEqual(p['runs'], [
            {'text': '必須項目', 'bold': True, 'color': 'FF0000'},
            {'text': 'を入力'},
            {'text': '注記', 'underline': True, 'sizePt': 10.5},
        ])
        self.assertEqual(p['text'], '必須項目を入力注記')

    def test_paragraph_align(self):
        self.assertEqual(self.ir['body'][0]['align'], 'center')

    def test_cell_shading(self):
        cell = self.ir['body'][1]['cells'][0]
        self.assertEqual(cell['shading'], 'D9D9D9')
        # 書式なしランのみの段落には runs を付けない
        self.assertNotIn('runs', cell['blocks'][0])

    def test_spacing_and_indent(self):
        p = self.ir['body'][0]
        self.assertEqual(p['spacing'], {'beforePt': 6, 'afterPt': 3})
        self.assertEqual(p['indent'], {'leftPt': 21, 'firstLinePt': 10.5})

    def test_borders(self):
        table = self.ir['body'][1]
        self.assertEqual(table['borders'], {'top': 'single', 'insideH': 'dashed'})
        self.assertEqual(table['cells'][0]['borders'], {'bottom': 'double'})

    def test_style_formats_resolved(self):
        sf = self.ir['styleFormats']
        self.assertEqual(sf['default'], {'font': 'ＭＳ 明朝', 'sizePt': 10.5})
        self.assertEqual(sf['Base'], {'bold': True})
        # basedOn 連鎖がマージされる(Base の bold + 自身の sz/jc)
        self.assertEqual(sf['H1'], {'bold': True, 'sizePt': 14, 'align': 'center'})

    def test_style_formats_absent_by_default(self):
        ir = docx_reader.read_docx(build_docx({
            'word/document.xml': FORMATTED_XML,
            'word/styles.xml': FORMAT_STYLES_XML,
        }), source_name='fixture.docx')
        self.assertNotIn('styleFormats', ir)


MIXED_CELL_XML = f'''<?xml version="1.0" encoding="UTF-8"?>
<w:document {NS}><w:body>
  <w:tbl><w:tr><w:tc>
    <w:p>
      <w:r><w:rPr><w:b/></w:rPr><w:t>種別: </w:t></w:r>
      <w:r><w:t>未記入</w:t></w:r>
    </w:p>
  </w:tc></w:tr></w:tbl>
</w:body></w:document>'''


class AnalyzeTest(unittest.TestCase):
    def test_strategy_facts(self):
        ir = docx_reader.read_docx(build_docx({
            'word/document.xml': DOCUMENT_XML,
            'word/styles.xml': STYLES_XML,
        }), source_name='fixture.docx')
        strategy = analyzer.analyze_ir(ir)
        body = next(p for p in strategy['parts'] if p['part'] == 'body')
        table = body['tables'][0]
        self.assertEqual(table['autoNumberCells'], [{'row': 2, 'col': 1}])
        self.assertIn({'row': 0, 'col': 0, 'rowSpan': 1, 'colSpan': 2},
                      table['mergedCells'])
        self.assertIn({'row': 1, 'col': 0, 'rowSpan': 2, 'colSpan': 1},
                      table['mergedCells'])
        # 本文段落のフィールド(PAGE)が報告される
        self.assertTrue(any(p.get('fields') == ['PAGE']
                            for p in body['paragraphs']))

    def test_mixed_format_cell_detected(self):
        # 書式オーバーレイ付き IR から混在書式セルを検出する(CLI analyze の経路)
        ir = docx_reader.read_docx(build_docx({
            'word/document.xml': MIXED_CELL_XML,
        }), source_name='fixture.docx', with_format=True)
        table = analyzer.analyze_ir(ir)['parts'][0]['tables'][0]
        self.assertEqual(table['mixedFormatCells'], [{'row': 0, 'col': 0}])
        self.assertTrue(any('混在書式' in n for n in table['notes']))

    def test_mixed_format_skipped_without_overlay(self):
        # オーバーレイなしの IR では runs が無いため検出対象外(事実のみ導出の原則)
        ir = docx_reader.read_docx(build_docx({
            'word/document.xml': MIXED_CELL_XML,
        }), source_name='fixture.docx')
        table = analyzer.analyze_ir(ir)['parts'][0]['tables'][0]
        self.assertNotIn('mixedFormatCells', table)

    def test_mixed_format_paragraph_reported(self):
        ir = docx_reader.read_docx(build_docx({
            'word/document.xml': FORMATTED_XML,
            'word/styles.xml': FORMAT_STYLES_XML,
        }), source_name='fixture.docx', with_format=True)
        body = next(p for p in analyzer.analyze_ir(ir)['parts']
                    if p['part'] == 'body')
        self.assertTrue(any(p.get('mixedFormat') for p in body['paragraphs']))


class HeaderTest(unittest.TestCase):
    def test_header_resolved_via_rels(self):
        ir = docx_reader.read_docx(build_docx({
            'word/document.xml': DOCUMENT_WITH_HEADER_XML,
            'word/_rels/document.xml.rels': RELS_XML,
            'word/header1.xml': HEADER_XML,
        }), source_name='fixture.docx')
        sec = ir['sections'][0]
        self.assertTrue(sec['titlePg'])
        self.assertEqual(sec['headers']['default'][0]['text'], 'ヘッダーテスト')


@unittest.skipUnless(HANREI_DIR.is_dir(), '凡例ディレクトリなし')
class HanreiIntegrationTest(unittest.TestCase):
    def test_table_list_template(self):
        path = HANREI_DIR / 'テーブル定義書' / 'テーブル定義書一覧表_凡例.docx'
        ir = docx_reader.read_docx(path, source_name=path.name)
        header = ir['sections'][0]['headers']['default']
        header_table = next(b for b in header if b['type'] == 'table')
        self.assertEqual(
            header_table['cells'][0]['blocks'][0]['text'], 'テーブル定義書一覧表')
        body_table = next(b for b in ir['body'] if b['type'] == 'table')
        self.assertEqual(body_table['colCount'], 4)
        # No列(1列目)のデータ行は自動採番
        no_cell = next(c for c in body_table['cells']
                       if c['row'] == 1 and c['col'] == 0)
        self.assertIn('numbering', no_cell['blocks'][0])

    def test_all_templates_readable(self):
        files = sorted(HANREI_DIR.rglob('*.docx'))
        self.assertGreaterEqual(len(files), 10)
        for f in files:
            ir = docx_reader.read_docx(f, source_name=f.name)
            self.assertTrue(ir['body'], f'{f.name}: 本文が空')
            preview.to_markdown(ir)  # プレビューが例外なく生成できる


class StyleNumberingTest(unittest.TestCase):
    """書式(段落スタイル)由来の自動採番の検出と numFmt 解決(合成 docx)。"""

    NUMBERING = '''<?xml version="1.0" encoding="UTF-8"?>
<w:numbering xmlns:w="http://schemas.openxmlformats.org/wordprocessingml/2006/main">
  <w:abstractNum w:abstractNumId="0"><w:lvl w:ilvl="0"><w:numFmt w:val="decimal"/></w:lvl></w:abstractNum>
  <w:abstractNum w:abstractNumId="1"><w:lvl w:ilvl="0"><w:numFmt w:val="bullet"/></w:lvl></w:abstractNum>
  <w:num w:numId="5"><w:abstractNumId w:val="0"/></w:num>
  <w:num w:numId="7"><w:abstractNumId w:val="0"/></w:num>
  <w:num w:numId="9"><w:abstractNumId w:val="1"/></w:num>
</w:numbering>'''

    STYLES = '''<?xml version="1.0" encoding="UTF-8"?>
<w:styles xmlns:w="http://schemas.openxmlformats.org/wordprocessingml/2006/main">
  <w:style w:type="paragraph" w:styleId="ListNum"><w:name w:val="番号付き"/>
    <w:pPr><w:numPr><w:ilvl w:val="0"/><w:numId w:val="7"/></w:numPr></w:pPr></w:style>
  <w:style w:type="paragraph" w:styleId="ListNumChild"><w:name w:val="番号付き子"/>
    <w:basedOn w:val="ListNum"/></w:style>
  <w:style w:type="paragraph" w:styleId="Bullet"><w:name w:val="箇条書き"/>
    <w:pPr><w:numPr><w:numId w:val="9"/></w:numPr></w:pPr></w:style>
  <w:style w:type="paragraph" w:styleId="Plain"><w:name w:val="標準"/></w:style>
</w:styles>'''

    DOC = f'''<?xml version="1.0" encoding="UTF-8"?>
<w:document {NS}><w:body>
  <w:p><w:pPr><w:pStyle w:val="ListNum"/></w:pPr><w:r><w:t>スタイル採番</w:t></w:r></w:p>
  <w:p><w:pPr><w:pStyle w:val="ListNumChild"/></w:pPr><w:r><w:t>継承採番</w:t></w:r></w:p>
  <w:p><w:pPr><w:pStyle w:val="Bullet"/></w:pPr><w:r><w:t>箇条書き</w:t></w:r></w:p>
  <w:p><w:pPr><w:pStyle w:val="ListNum"/>
    <w:numPr><w:ilvl w:val="0"/><w:numId w:val="5"/></w:numPr></w:pPr>
    <w:r><w:t>直接優先</w:t></w:r></w:p>
  <w:p><w:pPr><w:pStyle w:val="ListNum"/>
    <w:numPr><w:numId w:val="0"/></w:numPr></w:pPr><w:r><w:t>採番打消し</w:t></w:r></w:p>
  <w:p><w:pPr><w:pStyle w:val="Plain"/></w:pPr><w:r><w:t>採番なし</w:t></w:r></w:p>
</w:body></w:document>'''

    @classmethod
    def setUpClass(cls):
        cls.body = docx_reader.read_docx(build_docx({
            'word/document.xml': cls.DOC,
            'word/styles.xml': cls.STYLES,
            'word/numbering.xml': cls.NUMBERING,
        }), source_name='style_num.docx')['body']

    def test_style_based_numbering_detected(self):
        self.assertEqual(self.body[0]['numbering'], {
            'numId': '7', 'level': '0', 'source': 'style',
            'format': 'decimal', 'ordered': True})

    def test_basedon_inherits_numbering(self):
        self.assertEqual(self.body[1]['numbering']['numId'], '7')
        self.assertEqual(self.body[1]['numbering']['source'], 'style')

    def test_bullet_not_ordered(self):
        num = self.body[2]['numbering']
        self.assertEqual(num['format'], 'bullet')
        self.assertNotIn('ordered', num)  # bullet は連番扱いしない

    def test_direct_numpr_overrides_style(self):
        self.assertEqual(self.body[3]['numbering'], {
            'numId': '5', 'level': '0', 'source': 'direct',
            'format': 'decimal', 'ordered': True})

    def test_numid_zero_cancels_numbering(self):
        self.assertNotIn('numbering', self.body[4])  # 採番打ち消し → 無し

    def test_plain_paragraph_has_no_numbering(self):
        self.assertNotIn('numbering', self.body[5])


class StyleNumberingAnalyzeTest(unittest.TestCase):
    """analyzer がスタイル由来採番を拾い、連番/書式由来を注記する。"""

    def test_style_numbered_cell_flagged(self):
        doc = f'''<?xml version="1.0" encoding="UTF-8"?>
<w:document {NS}><w:body><w:tbl>
  <w:tr><w:tc><w:p><w:r><w:t>No</w:t></w:r></w:p></w:tc></w:tr>
  <w:tr><w:tc><w:p><w:pPr><w:pStyle w:val="ListNum"/></w:pPr></w:p></w:tc></w:tr>
  <w:tr><w:tc><w:p><w:pPr><w:pStyle w:val="ListNum"/></w:pPr></w:p></w:tc></w:tr>
</w:tbl></w:body></w:document>'''
        ir = docx_reader.read_docx(build_docx({
            'word/document.xml': doc,
            'word/styles.xml': StyleNumberingTest.STYLES,
            'word/numbering.xml': StyleNumberingTest.NUMBERING,
        }), with_format=True)
        result = analyzer.analyze_ir(ir)
        table = result['parts'][0]['tables'][0]
        self.assertEqual(table['autoNumberCells'],
                         [{'row': 1, 'col': 0}, {'row': 2, 'col': 0}])
        notes = ' '.join(table['notes'])
        self.assertIn('自動採番', notes)        # 連番列として検出
        self.assertIn('書式(スタイル)由来', notes)  # 書式由来である旨


if __name__ == '__main__':
    unittest.main(verbosity=2)
