# -*- coding: utf-8 -*-
"""E2E エビデンスの受け入れ用 index.html を、テスト仕様書(xlsx)の本文とともに生成する。

各機能フォルダ ``<テスト実施単位>/<機能>/`` について、
  - ``<機能>_テスト仕様書.xlsx`` から ケースNo・テスト観点・操作手順・期待する結果・参照テストデータ を読み、
  - ``<エビデンス>/results.json`` から 各ケースの機械判定（合否）を読み、
  - ``<エビデンス>/`` の ``*.png`` / ``*.webm`` をケースNoで対応づけ、
  - 先頭に一覧・サマリ、以降は 1ケース＝1画面（スクロールなし）で確認できる ``index.html`` を出力する。

テスト実施単位（リリースターゲット・再テスト）は機能の 1 階層上に入り、仕様書・データ・ベースライン・
証跡が実施単位ごとに一式で閉じる。仕様書の参照テストデータ欄のパスは実施単位からの相対として解決する。
**1 回の呼び出しで対象にできる実施単位は 1 つだけ**であり、実施単位を跨ぐ指定は生成せずに終了する
（過去の実施単位は実施記録として残すものであり、作り直さない）。

フォルダ名・仕様書の命名はプロジェクトの規約で決まるため、本スクリプトは持たずに呼び出し側から受ける。

使い方::

    PYTHONUTF8=1 python e2e_evidence_index.py \\
        --evidence-dir-name <証跡フォルダ名> --spec-glob <仕様書のグロブ> --data-dir-name <データフォルダ名> \\
        <ディレクトリ> ... [--spec <仕様書ファイル名>]

対象は引数のディレクトリで明示する（全走査はしない）。機能フォルダを指定するとその機能を、実施単位を
指定するとその配下の全機能を対象にする。

終了コード： 0=生成した／対象なし、2=指定や対象の不備（引数不足・実施単位を跨ぐ・仕様書が複数 等）、
3=実行環境の不備（openpyxl 未導入）。
"""
import argparse
import csv
import html
import json
import os
import re
import sys
from dataclasses import dataclass
from pathlib import Path

try:
    from openpyxl import load_workbook
except ImportError:  # 環境不備は生成失敗（コード 2）と区別して報告する
    load_workbook = None

EXIT_OK = 0
EXIT_INVALID = 2
EXIT_NO_DEPENDENCY = 3
CASE_NO = re.compile(r"No\.(\d+(?:-\d+)?)")
# 参照テストデータ欄の本文から「パスらしき部分文字列」を拾う（ラベルや装飾は含めない）
PATH_LIKE = re.compile(
    r"[^\s,、。:：;；()（）\[\]{}｛｝「」『』【】〔〕〈〉《》"
    r"・※＊*●○▲■◆→←＞<>＜\"'｜|]*\.[0-9A-Za-z]+")
MAX_CSV_ROWS = 500


@dataclass(frozen=True)
class Layout:
    """機能フォルダ内の配置・命名。プロジェクト規約で決まるため呼び出し側から受ける。"""

    evidence_dir_name: str
    spec_glob: str
    data_dir_name: str


def fail(message):
    """指定・対象の不備を報告して終了する（終了コード 2）。"""
    print(message, file=sys.stderr)
    raise SystemExit(EXIT_INVALID)


def clean(value):
    return "" if value is None else str(value)


def esc(text):
    return html.escape(clean(text))


def read_cases(xlsx_path):
    """テスト仕様書 xlsx から {ケースNo: {no, name, steps, expected, data}} を読む。

    仕様書が読めないときは生成失敗として終了する（終了コード 2）。壊れたファイル・xlsx でないファイル・
    シートが無いファイルなど失敗要因が多岐にわたるため、読み取り（I/O・パース）は広く捕まえる。
    素の例外で落とすとスタックトレースになり、原因のファイルが分からないため。
    """
    cases = {}
    if not xlsx_path.exists():
        return cases
    # read_only=True は <dimension> 宣言を信用して走査範囲を決めるため、宣言が実データより
    # 狭い（＝行追加後に Excel 保存で正規化されていない）xlsx では末尾行を取りこぼす。
    # 仕様書は行数が小さく streaming 不要なので read_only=False で実データ全行を読む。
    try:
        workbook = load_workbook(xlsx_path, data_only=True)
        sheet = workbook["テストケース"] if "テストケース" in workbook.sheetnames \
            else workbook.active
        rows = list(sheet.iter_rows(values_only=True))
        workbook.close()
    except Exception as error:
        fail(f"仕様書を読めません: {xlsx_path}（{type(error).__name__}: {error}）")
    header_seen = False
    for row in rows:
        first = row[0]
        if not header_seen:
            if first == "No":
                header_seen = True
            continue
        if first is None or str(first).strip() == "":
            continue
        no = str(first).strip()
        if not re.fullmatch(r"\d+(?:-\d+)?", no):
            continue
        if clean(row[2]).strip() == "":
            # セクション見出し行（No=セクション番号のみ・観点列以降は結合で空）
            continue
        cases[no] = {
            "no": no,
            "name": clean(row[2]),      # C: テスト観点・シナリオ情報
            "steps": clean(row[4]),     # E: 操作手順
            "expected": clean(row[5]),  # F: 期待する結果
            "data": clean(row[6]),      # G: 参照テストデータ・期待値
        }
    workbook.close()
    return cases


def read_results(evidence_dir):
    """results.json から {ケースNo: status} を読む（無ければ空）。"""
    path = evidence_dir / "results.json"
    if not path.exists():
        return {}
    try:
        entries = json.loads(path.read_text(encoding="utf-8"))
    except (ValueError, OSError):
        return {}
    return {str(e.get("no")): e.get("status", "") for e in entries if e.get("no")}


def read_steps(evidence_dir):
    """steps.json（証跡V2）から {ケースNo: ケースエントリ} を読む（無ければ空）。

    steps.json はハーネスV2（E2eStepRecorder）が書き出す正本で、ケースごとの操作・検証ステップと
    フレーム画像・機械検証の実測値を持つ。存在するケースは V2 レイアウト（コマ送り）で描画する。
    """
    path = evidence_dir / "steps.json"
    if not path.exists():
        return {}
    try:
        root = json.loads(path.read_text(encoding="utf-8"))
    except (ValueError, OSError) as error:
        fail(f"steps.json を読めません: {path}（{type(error).__name__}: {error}）")
    cases = {}
    for entry in root.get("cases", []):
        no = str(entry.get("no", "")).strip()
        if no:
            cases[no] = entry
    return cases


def case_no_of(name):
    match = CASE_NO.search(name)
    return match.group(1) if match else None


def case_sort_key(no):
    """ケース No（`3`／`1-2` 形式）を数値タプルの並べ替えキーにする（No 無しは末尾）。"""
    if not no:
        return (1 << 30,)
    return tuple(int(part) for part in no.split("-"))


def resolve_ref(token, feature_dir, layout):
    """参照トークンを実在ファイルへ解決し (パス, index.html からの相対リンク) を返す。

    仕様書のパスは **テスト実施単位からの相対**（例 ``_baseline/data/users.csv``・
    ``21_サンプル機能/data/seed.csv``）で書かれる。``/`` を含まないトークンは当該機能フォルダの
    データフォルダ直下にあるとみなす。実施単位より上位から書かれたパスは、実施単位名以降を相対部分として
    受け取る。実在しないものは解決しない（＝リンクを張らない）。
    """
    rel = token
    marker = feature_dir.parent.name + "/"
    at = rel.find(marker)
    if at >= 0:
        rel = rel[at + len(marker):]
    if "/" in rel:
        path = feature_dir.parent / rel
    else:
        path = feature_dir / layout.data_dir_name / rel
    if not path.is_file():
        return None
    href = os.path.relpath(path, feature_dir / layout.evidence_dir_name).replace("\\", "/")
    return (path, href)


def _read_csv(path):
    """CSV を [[セル,...], ...] で読む。先頭 MAX_CSV_ROWS 行（＋ヘッダ）に制限する。"""
    try:
        with path.open("r", encoding="utf-8-sig", newline="") as stream:
            rows = [row for row in csv.reader(stream) if row]
    except OSError:
        return None
    total = len(rows)
    shown = rows[:MAX_CSV_ROWS + 1]
    return {"rows": shown, "total": total, "truncated": total > len(shown)}


def _render_dialog(dlg_id, name, preview):
    """1 つの CSV を表示する <dialog>（表・スクロール可）を組み立てる。"""
    rows = preview["rows"]
    if rows:
        head = "".join(f"<th>{esc(cell)}</th>" for cell in rows[0])
        body = "".join(
            "<tr>" + "".join(f"<td>{esc(cell)}</td>" for cell in row) + "</tr>"
            for row in rows[1:])
        inner = (f'<table class="csvtable"><thead><tr>{head}</tr></thead>'
                 f'<tbody>{body}</tbody></table>')
    else:
        inner = '<p class="muted">（空のファイル）</p>'
    note = ""
    if preview["truncated"]:
        note = (f'<p class="muted">全 {preview["total"] - 1} 行のうち先頭 '
                f'{MAX_CSV_ROWS} 行を表示</p>')
    return (
        f'<dialog id="{esc(dlg_id)}" class="csvdlg">'
        f'<form method="dialog" class="csvdlg-head">'
        f'<span class="csvdlg-title">{esc(name)}</span>'
        f'<button class="csvdlg-close" aria-label="閉じる">✕</button></form>'
        f'<div class="csvdlg-body">{inner}{note}</div></dialog>')


class CsvRegistry:
    """参照 CSV を index 内の <dialog>（表）へ集約する。同一ファイルは 1 つに束ねる。"""

    def __init__(self, feature_dir, layout):
        self._feature_dir = feature_dir
        self._layout = layout
        self._ids = {}
        self._dialogs = []

    def resolve(self, token):
        return resolve_ref(token, self._feature_dir, self._layout)

    def button(self, path):
        """実在 CSV なら「表で表示」ボタン HTML を返す（それ以外は空文字）。"""
        if path.suffix.lower() != ".csv":
            return ""
        key = str(path)
        dlg_id = self._ids.get(key)
        if dlg_id is None:
            preview = _read_csv(path)
            if preview is None:
                return ""
            dlg_id = f"csvdlg{len(self._ids)}"
            self._ids[key] = dlg_id
            self._dialogs.append(_render_dialog(dlg_id, path.name, preview))
        return (f'<button type="button" class="csv-open" '
                f'data-dialog="{esc(dlg_id)}">表で表示</button>')

    def dialogs_html(self):
        return "\n".join(self._dialogs)


def data_html(cell, registry):
    """参照テストデータ欄を、実在ファイルの箇所だけリンク化した HTML にする。

    ラベル・装飾・改行はそのまま残す（改行は CSS の pre-wrap で表示される）。
    """
    text = clean(cell)
    if not text.strip():
        return '<span class="muted">—</span>'
    parts = []
    pos = 0
    for match in PATH_LIKE.finditer(text):
        ref = registry.resolve(match.group())
        if ref is None:
            continue
        path, href = ref
        parts.append(esc(text[pos:match.start()]))
        parts.append(f'<a href="{esc(href)}">{esc(match.group())}</a>')
        parts.append(registry.button(path))
        pos = match.end()
    parts.append(esc(text[pos:]))
    return "".join(parts)


def verdict(no, results):
    status = results.get(str(no), "")
    label = {"pass": "PASS", "fail": "FAIL"}.get(status, status.upper() or "—")
    cls = {"pass": "pass", "fail": "fail"}.get(status, "other" if status else "none")
    return status, f'<span class="verdict {cls}">{esc(label)}</span>'


def build_case(shot, cases, results, registry, page_cls=""):
    stem = shot.stem
    no = case_no_of(stem)
    spec = cases.get(no, {}) if no else {}
    name = spec.get("name") or stem.replace("_", " ")
    anchor = f"case-{no}" if no else stem
    _, badge = verdict(no, results)
    webm = shot.with_suffix(".webm")
    section_cls = f"case {page_cls}".strip()
    out = [f'<section class="{section_cls}" id="{esc(anchor)}">']
    out.append('<div class="case-head">')
    if no:
        out.append(f'<span class="badge">No.{esc(no)}</span>')
    out.append(f'<span class="name">{esc(name)}</span>')
    out.append(badge)
    out.append('</div>')
    out.append('<div class="case-body">')
    out.append('<div class="media">')
    out.append(f'<img class="shot" src="{esc(shot.name)}" alt="{esc(name)}">')
    if webm.exists():
        out.append(f'<video class="clip" controls preload="none" hidden '
                   f'src="{esc(webm.name)}"></video>')
        out.append('<div class="toggle">'
                   '<button type="button" class="on" data-view="shot">画像</button>'
                   '<button type="button" data-view="clip">動画</button></div>')
    out.append('</div>')
    out.append('<aside class="spec">')
    out.append('<h3>操作手順</h3>')
    out.append(f'<div class="pre">{esc(spec.get("steps", ""))}</div>')
    out.append('<h3>期待する結果</h3>')
    out.append(f'<div class="pre">{esc(spec.get("expected", ""))}</div>')
    out.append('<h3>参照テストデータ・期待値</h3>')
    out.append(f'<div class="data">{data_html(spec.get("data", ""), registry)}</div>')
    out.append('</aside>')
    out.append('</div>')
    out.append('</section>')
    return "\n".join(out)


def _fold_value(text, limit=100, head=60):
    """長大な期待/実測値を要約表示にする（クリックで全文展開。記録自体は steps.json に全文が残る）。

    最大文字数系の保持検証（数千文字）でキャプションが画面を埋め、写真が見えなくなるのを防ぐ。
    """
    if text is None:
        return ""
    if len(text) <= limit:
        return esc(text)
    return (f'<details class="longv"><summary>{esc(text[:head])}…'
            f'（全{len(text)}文字・クリックで全文）</summary>'
            f'<div class="longv-full">{esc(text)}</div></details>')


def _caption_html(step, hidden):
    """フレーム 1 枚分のキャプション（画像・送りボタンの下に置く独立要素）。

    画像と同じ figure に入れるとキャプションの高さ次第で送りボタンの位置が動いてしまうため、
    フレームとは分離した ``.fcap`` として描画し、JS がフレームと同期して切り替える。
    """
    if step.get("kind") == "op":
        cls = "cap-op"
        body = f'【操作】{esc(step.get("desc", ""))}'
    else:
        ok = step.get("status") == "pass"
        cls = "cap-verify" + ("" if ok else " cap-fail")
        mark = "✓" if ok else "✗"
        body = (f'【検証】{esc(step.get("desc", ""))} ― 期待: {_fold_value(step.get("expected", ""))}'
                f' ／ 実測: {_fold_value(step.get("actual", ""))} {mark}')
    note = step.get("note")
    note_html = f'<div class="cap-note">{esc(note)}</div>' if note else ""
    ts = step.get("ts")
    ts_html = f'<span class="cap-ts">{esc(ts)}</span>' if ts else ""
    attach = step.get("attachFrame")
    attach_html = (f'<div class="cap-note"><a href="{esc(attach)}" target="_blank">'
                   '要素全体ショットを開く（ビューポートに収まらない要素の補助フレーム）</a></div>'
                   ) if attach else ""
    artifact = step.get("artifact")
    artifact_html = (f'<div class="cap-note"><a href="{esc(artifact)}" download>'
                     f'ダウンロード実物を開く: {esc(artifact)}</a>'
                     '（機械検証に使った実ファイル）</div>') if artifact else ""
    return (f'<div class="fcap {cls}"{hidden}>{ts_html}{body}{note_html}'
            f'{artifact_html}{attach_html}</div>')


def build_case_v2(entry, cases, results, registry, evidence, page_cls=""):
    """steps.json のケースを、コマ送り（フィルムストリップ）＋検証ログで描画する。"""
    no = str(entry.get("no", ""))
    spec = cases.get(no, {})
    name = spec.get("name") or entry.get("name") or ""
    anchor = f"case-{no}"
    _, badge = verdict(no, results)
    steps = entry.get("steps", [])
    # kind=whole（全体撮影）は G2 判断で廃止。旧データに残っていても描画しない。
    strip = [s for s in steps if s.get("frame") and s.get("kind") != "whole"]
    checks = [s for s in steps if s.get("kind") in ("verify", "machine")]
    n_visible = sum(1 for s in checks if s.get("kind") == "verify")
    n_machine = len(checks) - n_visible
    figs = []
    caps = []
    for index, step in enumerate(strip):
        hidden = "" if index == 0 else " hidden"
        figs.append(f'<figure class="fstep"{hidden}>'
                    f'<img src="{esc(step["frame"])}" loading="lazy" '
                    f'alt="{esc(step.get("desc", ""))}"></figure>')
        caps.append(_caption_html(step, hidden))
    thumb_parts = []
    for index, step in enumerate(strip):
        fail_cls = ' class="fail"' if step.get("status") == "fail" else ""
        thumb_parts.append(
            f'<img src="{esc(step["frame"])}"{fail_cls} loading="lazy" data-idx="{index}">')
    thumbs = "".join(thumb_parts)
    video_html = ""
    webms = sorted(evidence.glob(f"No.{no}_*.webm"))
    if webms:
        video_html = ('<details class="whole"><summary>動画（補助）</summary>'
                      f'<video controls preload="none" src="{esc(webms[0].name)}" '
                      'style="width:100%"></video></details>')
    log_items = []
    for step in checks:
        mark = ('<span class="ok">✓</span>' if step.get("status") == "pass"
                else '<span class="ng">✗</span>')
        kind_label = "機械" if step.get("kind") == "machine" else "可視"
        detail = ""
        if step.get("expected") is not None:
            detail = (f' <span class="muted">期待: {esc(step.get("expected"))} ／ '
                      f'実測: {esc(step.get("actual"))}</span>')
        log_items.append(f'<li>{mark} 【{kind_label}】{esc(step.get("desc", ""))}{detail}</li>')
    logs = entry.get("logs") or []
    console_html = ""
    if logs:
        log_rows = []
        for log in logs:
            level = str(log.get("level", "")).upper()
            lvl_cls = ("lerr" if level == "ERROR"
                       else "lwarn" if level in ("WARN", "WARNING") else "")
            src = str(log.get("src", ""))
            src_cls = {"browser": " lbrowser", "harness": " lharness"}.get(src, "")
            log_rows.append(
                f'<div class="lrow {lvl_cls}"><span class="lts">{esc(log.get("ts", ""))}</span>'
                f'<span class="lsrc{src_cls}">{esc(src)}</span>'
                f'<span class="llvl">{esc(level)}</span>'
                f'<span class="llog">{esc(log.get("logger", ""))}</span>'
                f'<span class="lmsg">{esc(log.get("message", ""))}</span></div>')
        console_html = (
            f'<details class="console" open><summary>証跡ログ（{len(logs)}件）'
            '</summary><div class="lbox">' + "".join(log_rows) + '</div></details>')
    fmeta = f'フレーム {len(strip)}枚 ／ 検証 可視{n_visible}・機械{n_machine}'
    section_cls = f"case {page_cls}".strip()
    out = [f'<section class="{section_cls}" id="{esc(anchor)}">']
    out.append('<div class="case-head">')
    out.append(f'<span class="badge">No.{esc(no)}</span>')
    out.append(f'<span class="name">{esc(name)}</span>')
    out.append(badge)
    out.append(f'<span class="fmeta">{fmeta}</span>')
    out.append('</div>')
    out.append('<div class="case-body">')
    out.append('<div class="media v2">')
    out.append(f'<div class="stage">{"".join(figs)}</div>')
    # 送りボタンは画像直下に固定（キャプションの高さでボタン位置が動かないよう、キャプションはこの下）。
    # フレームは原寸（等倍）表示が既定で、右側のズームで 50〜300% に変更できる（倍率は全ページ共有）。
    out.append('<div class="fnav"><button type="button" class="prev">◀ 前</button>'
               '<span class="fcount"></span>'
               '<button type="button" class="next">次 ▶</button>'
               '<span class="zoom"><button type="button" class="zfit">全体</button>'
               '<button type="button" class="zout">−</button>'
               '<span class="zval">100%</span>'
               '<button type="button" class="zin">＋</button></span></div>')
    out.append(f'<div class="fcaps">{"".join(caps)}</div>')
    # 証跡ログは動画（補助）より上に置き、既定で展開しておく（受け入れ時に必ず目に入る位置）。
    out.append(f'<div class="thumbs">{thumbs}</div>')
    out.append(console_html)
    out.append(video_html)
    out.append('</div>')
    out.append('<aside class="spec">')
    out.append('<h3>操作手順</h3>')
    out.append(f'<div class="pre">{esc(spec.get("steps", ""))}</div>')
    out.append('<h3>期待する結果</h3>')
    out.append(f'<div class="pre">{esc(spec.get("expected", ""))}</div>')
    out.append('<h3>参照テストデータ・期待値</h3>')
    out.append(f'<div class="data">{data_html(spec.get("data", ""), registry)}</div>')
    out.append('<h3>検証ログ</h3>')
    out.append(f'<ul class="mlog">{"".join(log_items)}</ul>')
    out.append('</aside>')
    out.append('</div>')
    out.append('</section>')
    return "\n".join(out)


def build_summary(feature, items, results, registry):
    rows = []
    passed = failed = 0
    for item in items:
        no = item["no"]
        status, badge = verdict(no, results)
        if status == "pass":
            passed += 1
        elif status == "fail":
            failed += 1
        rows.append(
            f'<tr><td><a href="{esc(item["href"])}">No.{esc(no or "")}</a></td>'
            f'<td>{esc(item["name"])}</td><td>{badge}</td>'
            f'<td><div class="data">{data_html(item["data"], registry)}</div>'
            f'</td></tr>')
    total = len(items)
    counts = (
        f'<div class="counts"><span>総数 <b>{total}</b></span>'
        f'<span class="ok">成功 <b>{passed}</b></span>'
        f'<span class="ng">失敗 <b>{failed}</b></span>'
        f'<span class="na">未判定 <b>{total - passed - failed}</b></span></div>')
    table = (
        '<table><thead><tr><th>No</th><th>テスト観点</th><th>合否</th>'
        '<th>参照テストデータ</th></tr></thead><tbody>'
        + "".join(rows) + '</tbody></table>')
    return (
        f'<section class="case summary" id="summary">'
        f'<div class="case-head"><span class="name">テスト一覧・サマリ — {esc(feature)}</span></div>'
        f'{counts}{table}</section>')


def build_toc(items, results):
    links = ['<a href="#summary"><span class="dot none"></span>サマリ・一覧</a>']
    for item in items:
        no = item["no"]
        name = item["name"]
        short = name if len(name) <= 20 else name[:19] + "…"
        status, _ = verdict(no, results)
        cls = {"pass": "pass", "fail": "fail"}.get(status, "other" if status else "none")
        label = f"No.{no}　{short}" if no else short
        links.append(
            f'<a href="{esc(item["href"])}"><span class="dot {cls}"></span>{esc(label)}</a>')
    return "\n".join(links)


STYLE = """
*{box-sizing:border-box}
[hidden]{display:none!important}
body{margin:0;font-family:'Noto Sans JP',system-ui,sans-serif;color:#1f2733;background:#f4f6f9}
.toc{position:fixed;left:0;top:0;width:250px;height:100vh;overflow:auto;background:#1f2733;
 color:#e8edf3;padding:14px 10px}
.toc h1{font-size:.95rem;margin:4px 6px 12px;color:#fff}
.toc h1 small{font-weight:400;font-size:.78rem;color:#94a3b8}
.toc a{display:block;padding:7px 8px;margin:2px 0;border-radius:6px;color:#cdd6e2;
 text-decoration:none;font-size:.82rem;line-height:1.3}
.toc a:hover{background:#2d3846;color:#fff}
.dot{display:inline-block;width:8px;height:8px;border-radius:50%;margin-right:7px;vertical-align:middle}
.dot.pass{background:#22c55e}.dot.fail{background:#ef4444}
.dot.other{background:#eab308}.dot.none{background:#94a3b8}
.main{margin-left:250px;height:100vh;overflow-y:scroll;scroll-snap-type:y mandatory;
 scroll-behavior:smooth}
.case{height:100vh;scroll-snap-align:start;padding:16px 22px;display:flex;flex-direction:column}
.case-head{flex:0 0 auto;display:flex;align-items:center;gap:10px;margin-bottom:12px}
.badge{background:#2563eb;color:#fff;font-weight:700;font-size:.8rem;padding:3px 10px;
 border-radius:999px}
.name{font-size:1.05rem;font-weight:700}
.verdict{font-weight:700;font-size:.72rem;padding:2px 9px;border-radius:999px}
.verdict.pass{background:#dcfce7;color:#166534}
.verdict.fail{background:#fee2e2;color:#991b1b}
.verdict.other{background:#fef9c3;color:#854d0e}
.verdict.none{background:#e5e9ef;color:#64748b}
.case-body{flex:1 1 auto;display:flex;gap:18px;min-height:0}
.media{flex:1 1 62%;display:flex;flex-direction:column;min-height:0}
.media .shot,.media .clip{flex:1 1 auto;min-height:0;width:100%;object-fit:contain;
 object-position:top;background:#fff;border:1px solid #d5dce6;border-radius:8px}
.toggle{flex:0 0 auto;margin-top:8px;display:flex;gap:6px}
.toggle button{border:1px solid #c4ccd8;background:#fff;border-radius:6px;padding:4px 14px;
 cursor:pointer;font-size:.82rem}
.toggle button.on{background:#2563eb;border-color:#2563eb;color:#fff}
.spec{flex:1 1 38%;min-height:0;overflow:auto;background:#fff;border:1px solid #d5dce6;
 border-radius:8px;padding:14px 16px}
.spec h3{font-size:.8rem;color:#5b6675;margin:14px 0 6px;letter-spacing:.04em}
.spec h3:first-child{margin-top:0}
.pre{white-space:pre-wrap;font-size:.92rem;line-height:1.6}
.data{white-space:pre-wrap;font-size:.92rem;line-height:1.9}
.data a{color:#2563eb}
.data .muted{color:#94a3b8}
.summary td .data{font-size:inherit;line-height:1.7}
.summary{overflow:auto;display:block}
.summary .name{font-size:1.15rem}
.counts{display:flex;gap:20px;font-size:1rem;margin:10px 0 14px}
.counts b{font-size:1.35rem}
.counts .ok b{color:#16a34a}.counts .ng b{color:#dc2626}.counts .na b{color:#64748b}
.summary table{border-collapse:collapse;width:100%;font-size:.9rem}
.summary th,.summary td{border:1px solid #dbe2ea;padding:6px 10px;text-align:left;
 vertical-align:top}
.summary th{background:#eef2f7;position:sticky;top:0}
.csv-open{margin:2px 0 2px 6px;border:1px solid #2563eb;background:#fff;color:#2563eb;
 border-radius:6px;padding:1px 9px;font-size:.78rem;cursor:pointer;vertical-align:middle}
.csv-open:hover{background:#2563eb;color:#fff}
dialog.csvdlg{border:none;border-radius:10px;padding:0;max-width:90vw;max-height:85vh;
 box-shadow:0 12px 48px rgba(15,23,35,.35);overflow:hidden}
dialog.csvdlg::backdrop{background:rgba(15,23,35,.55)}
.csvdlg-head{display:flex;align-items:center;justify-content:space-between;gap:20px;
 margin:0;padding:10px 16px;background:#1f2733;color:#fff}
.csvdlg-title{font-weight:700;font-size:.95rem}
.csvdlg-close{border:none;background:transparent;color:#fff;font-size:1.05rem;
 line-height:1;cursor:pointer;padding:2px 6px}
.csvdlg-body{padding:12px 16px;max-height:calc(85vh - 48px);overflow:auto}
.csvtable{border-collapse:collapse;font-size:.85rem;white-space:nowrap}
.csvtable th,.csvtable td{border:1px solid #dbe2ea;padding:5px 11px;text-align:left}
.csvtable thead th{background:#eef2f7;position:sticky;top:0}
.csvdlg-body .muted{color:#94a3b8;margin:8px 2px 0}
.fmeta{font-size:.75rem;color:#5b6675;margin-left:auto;white-space:nowrap}
.media.v2 .stage{flex:1 1 auto;min-height:0;display:flex}
.fstep{margin:0;flex:1;display:flex;flex-direction:column;min-height:0;min-width:0}
.fstep img{flex:1 1 auto;min-height:0;width:100%;object-fit:contain;object-position:top;
 background:#fff;border:1px solid #d5dce6;border-radius:8px}
.fcaps{flex:0 0 auto;margin-top:6px;min-height:3.6em}
.fcap{padding:7px 12px;border-radius:6px;
 font-size:.88rem;line-height:1.5;background:#eef2f7;color:#1f2733}
.fcap.cap-verify{background:#dcfce7;color:#14532d}
.fcap.cap-fail{background:#fee2e2;color:#991b1b}
.fcap{max-height:32vh;overflow:auto}
.longv{display:inline-block;vertical-align:top;max-width:100%}
.longv summary{cursor:pointer;display:inline}
.longv-full{max-height:180px;overflow:auto;white-space:pre-wrap;word-break:break-all;
  background:rgba(0,0,0,.06);padding:6px 8px;border-radius:4px;margin-top:4px}
.cap-note{color:#7c3aed;font-size:.78rem;margin-top:2px}
.fnav{flex:0 0 auto;display:flex;align-items:center;gap:10px;margin-top:6px}
.fnav button{border:1px solid #c4ccd8;background:#fff;border-radius:6px;padding:4px 14px;
 cursor:pointer;font-size:.82rem}
.fnav button:hover{background:#eef2f7}
.fcount{font-size:.85rem;color:#5b6675}
.thumbs{flex:0 0 auto;display:flex;gap:6px;margin-top:6px;overflow-x:auto}
.thumbs img{height:52px;border:2px solid #d5dce6;border-radius:4px;cursor:pointer;
 background:#fff}
.thumbs img.on{border-color:#2563eb}
.thumbs img.fail{border-color:#ef4444}
details.whole{flex:0 0 auto;margin-top:6px;font-size:.85rem;color:#5b6675}
details.whole summary{cursor:pointer}
details.whole img{max-width:340px;border:1px solid #eab308;border-radius:6px;display:block;
 margin-top:6px}
.mlog{list-style:none;padding:0;margin:4px 0;font-size:.85rem}
.mlog li{padding:4px 0;border-bottom:1px dashed #e2e8f0;line-height:1.5}
.mlog .ok{color:#16a34a;font-weight:700}
.mlog .ng{color:#dc2626;font-weight:700}
.mlog .muted{color:#94a3b8}
.pagenav{display:flex;gap:12px;margin:14px 22px 0;font-size:.9rem;flex-wrap:wrap}
.pagenav a{color:#2563eb;text-decoration:none;border:1px solid #c4ccd8;background:#fff;
 border-radius:6px;padding:5px 12px}
.pagenav a:hover{background:#eef2f7}
.pagenav .off{color:#94a3b8;border:1px solid #e2e8f0;border-radius:6px;padding:5px 12px}
.cpage{height:auto;min-height:0}
.cpage .stage{position:relative;display:block;height:62vh;overflow:hidden;
 overscroll-behavior:contain;background:#f8fafc;border:1px solid #e2e8f0;border-radius:8px;
 cursor:grab}
.cpage .stage.dragging{cursor:grabbing}
.cpage .fstep{position:absolute;left:0;top:0;flex:none;display:block;margin:0;
 width:auto;max-width:none;transform-origin:0 0}
.cpage .fstep img{width:auto;height:auto;max-width:none;max-height:none;display:block;
 border:none;border-radius:0}
.fnav .zoom{margin-left:auto;display:flex;align-items:center;gap:6px}
.fnav .zval{font-size:.85rem;color:#5b6675;min-width:46px;text-align:center}
.cap-ts{float:right;color:#94a3b8;font-size:.75rem;margin-left:10px}
details.console{margin-top:8px;font-size:.85rem;color:#5b6675}
details.console summary{cursor:pointer}
.lbox{margin-top:6px;background:#0f172a;color:#d7dde6;
 font:12px/1.65 ui-monospace,Consolas,monospace;border-radius:8px;padding:10px 12px;
 height:170px;min-height:60px;max-height:70vh;resize:vertical;overflow:auto}
.lrow{display:flex;gap:10px;white-space:pre-wrap;word-break:break-all}
.lrow .lts{color:#64748b;flex:0 0 auto}
.lrow .lsrc{color:#94a3b8;flex:0 0 auto;width:60px}
.lrow .lsrc.lbrowser{color:#c084fc}
.lrow .lsrc.lharness{color:#5eead4}
.lrow .llvl{flex:0 0 auto;width:46px}
.lrow.lwarn .llvl,.lrow.lwarn .lmsg{color:#fbbf24}
.lrow.lerr .llvl,.lrow.lerr .lmsg{color:#f87171}
.lrow .llog{color:#7dd3fc;flex:0 0 auto;max-width:220px;overflow:hidden;
 text-overflow:ellipsis}
.lrow .lmsg{flex:1 1 auto}
.tocbtn{position:absolute;left:6px;top:8px;border:1px solid #c4ccd8;
 background:#fff;border-radius:6px;padding:3px 9px;cursor:pointer;font-size:.95rem}
.tocbtn:hover{background:#eef2f7}
.toc{padding-top:46px}
html.toc-hidden .toc{width:38px;padding:46px 0 10px;overflow:hidden}
html.toc-hidden .toc h1,html.toc-hidden .toc a{display:none}
html.toc-hidden .tocbtn{left:5px;right:5px;padding:4px 0;text-align:center}
html.toc-hidden .main{margin-left:38px}
.main.pagemode{height:100vh;overflow:hidden;display:flex;flex-direction:column;
 scroll-snap-type:none}
.main.pagemode .pagenav{flex:0 0 auto;margin-bottom:10px}
.main.pagemode .case{flex:1 1 auto;min-height:0;height:auto;scroll-snap-align:none}
.cpage .case-body{flex:1 1 auto;min-height:0}
.cpage .media{overflow-y:auto;min-height:0;overscroll-behavior:contain;padding-right:4px}
.cpage .spec{position:static;max-height:none;overflow-y:auto;
 overscroll-behavior:contain}
"""

SCRIPT = """
document.querySelectorAll('.media').forEach(function(media){
  var shot=media.querySelector('.shot');
  var clip=media.querySelector('.clip');
  if(!clip) return;
  media.querySelectorAll('.toggle button').forEach(function(btn){
    btn.addEventListener('click',function(){
      var view=btn.dataset.view;
      media.querySelectorAll('.toggle button').forEach(function(b){b.classList.remove('on');});
      btn.classList.add('on');
      if(view==='clip'){shot.hidden=true;clip.hidden=false;}
      else{clip.hidden=true;if(clip.pause)clip.pause();shot.hidden=false;}
    });
  });
});
document.querySelectorAll('.csv-open').forEach(function(btn){
  btn.addEventListener('click',function(){
    var dlg=document.getElementById(btn.dataset.dialog);
    if(dlg&&dlg.showModal){dlg.showModal();}
  });
});
document.querySelectorAll('dialog.csvdlg').forEach(function(dlg){
  dlg.addEventListener('click',function(e){if(e.target===dlg){dlg.close();}});
});
var tocbtn=document.querySelector('.tocbtn');
if(tocbtn){tocbtn.addEventListener('click',function(){
  var hidden=document.documentElement.classList.toggle('toc-hidden');
  try{localStorage.setItem('e2eTocHidden',hidden?'1':'0');}catch(e){}
});}
// 目次のスクロール位置をページ間で維持し、現在ページの項目が見えないときだけ追従する。
var tocNav=document.querySelector('.toc');
if(tocNav){
  try{
    var ts0=parseInt(localStorage.getItem('e2eTocScroll')||'',10);
    if(ts0>=0){tocNav.scrollTop=ts0;}
  }catch(e){}
  var here=location.pathname.split('/').pop();
  var cur=here?tocNav.querySelector('a[href="'+here+'"]'):null;
  if(cur){
    var nr=tocNav.getBoundingClientRect(),cr=cur.getBoundingClientRect();
    if(cr.top<nr.top||cr.bottom>nr.bottom){cur.scrollIntoView({block:'center'});}
  }
  tocNav.addEventListener('scroll',function(){
    try{localStorage.setItem('e2eTocScroll',String(Math.round(tocNav.scrollTop)));}catch(e){}
  });
}
var lbox=document.querySelector('.lbox');
if(lbox){
  try{
    var lh=parseInt(localStorage.getItem('e2eLogH')||'',10);
    if(lh>=60){lbox.style.height=lh+'px';}
  }catch(e){}
  if(window.ResizeObserver){
    new ResizeObserver(function(){
      var hh=Math.round(lbox.getBoundingClientRect().height);
      if(hh>0){try{localStorage.setItem('e2eLogH',String(hh));}catch(e){}}
    }).observe(lbox);
  }
}
document.querySelectorAll('.media.v2').forEach(function(m){
  var figs=[].slice.call(m.querySelectorAll('.fstep'));
  if(!figs.length) return;
  var caps=[].slice.call(m.querySelectorAll('.fcap'));
  var thumbs=[].slice.call(m.querySelectorAll('.thumbs img'));
  var label=m.querySelector('.fcount');
  var stage=m.querySelector('.stage');
  var zval=m.querySelector('.zval');
  var idx=0,s=1,tx=0,ty=0,fitS=1,touched=false;
  function activeImg(){return figs[idx]?figs[idx].querySelector('img'):null;}
  function imgSize(){
    var im=activeImg();
    return {w:(im&&im.naturalWidth)||1280,h:(im&&im.naturalHeight)||800};
  }
  function clampPan(){
    var r=stage.getBoundingClientRect(),z=imgSize(),w=z.w*s,h=z.h*s;
    if(w<=r.width){tx=(r.width-w)/2;}else{tx=Math.min(0,Math.max(r.width-w,tx));}
    if(h<=r.height){ty=(r.height-h)/2;}else{ty=Math.min(0,Math.max(r.height-h,ty));}
  }
  function render(){
    clampPan();
    figs.forEach(function(f,j){
      f.hidden=j!==idx;
      if(j===idx){f.style.transform='translate('+tx+'px,'+ty+'px) scale('+s+')';}
    });
    caps.forEach(function(c,j){c.hidden=j!==idx;});
    thumbs.forEach(function(t,j){t.classList.toggle('on',j===idx);});
    if(label){label.textContent=(idx+1)+' / '+figs.length;}
    if(zval){zval.textContent=Math.round(s*100)+'%';}
  }
  function fit(){
    var r=stage.getBoundingClientRect(),z=imgSize();
    fitS=Math.min(r.width/z.w,r.height/z.h,1);
    s=fitS;touched=false;render();
  }
  function zoomAt(k,cx,cy){
    var ns=Math.min(4,Math.max(fitS,s*k));
    if(ns===s){return;}
    k=ns/s;
    tx=cx-(cx-tx)*k;ty=cy-(cy-ty)*k;s=ns;touched=true;render();
  }
  function show(i){idx=(i+figs.length)%figs.length;render();}
  var prev=m.querySelector('.prev');
  var next=m.querySelector('.next');
  if(prev){prev.addEventListener('click',function(){show(idx-1);});}
  if(next){next.addEventListener('click',function(){show(idx+1);});}
  thumbs.forEach(function(t,j){t.addEventListener('click',function(){show(j);});});
  stage.addEventListener('wheel',function(e){
    e.preventDefault();
    var r=stage.getBoundingClientRect();
    zoomAt(e.deltaY<0?1.15:1/1.15,e.clientX-r.left,e.clientY-r.top);
  },{passive:false});
  var drag=null;
  stage.addEventListener('mousedown',function(e){
    if(e.button!==0){return;}
    drag={x:e.clientX,y:e.clientY};stage.classList.add('dragging');e.preventDefault();
  });
  window.addEventListener('mousemove',function(e){
    if(!drag){return;}
    tx+=e.clientX-drag.x;ty+=e.clientY-drag.y;
    drag={x:e.clientX,y:e.clientY};touched=true;render();
  });
  window.addEventListener('mouseup',function(){drag=null;stage.classList.remove('dragging');});
  stage.addEventListener('dblclick',function(){fit();});
  function centerZoom(k){var r=stage.getBoundingClientRect();zoomAt(k,r.width/2,r.height/2);}
  var zin=m.querySelector('.zin');
  var zout=m.querySelector('.zout');
  var zfit=m.querySelector('.zfit');
  if(zin){zin.addEventListener('click',function(){centerZoom(1.25);});}
  if(zout){zout.addEventListener('click',function(){centerZoom(1/1.25);});}
  if(zfit){zfit.addEventListener('click',function(){fit();});}
  figs.forEach(function(f){
    var im=f.querySelector('img');
    if(im&&!im.complete){
      im.addEventListener('load',function(){if(!touched){fit();}else{render();}});
    }
  });
  window.addEventListener('resize',function(){if(!touched){fit();}else{render();}});
  document.addEventListener('keydown',function(e){
    if(e.altKey||e.ctrlKey||e.metaKey||e.shiftKey){return;}
    if(e.key==='ArrowRight'){show(idx+1);e.preventDefault();}
    else if(e.key==='ArrowLeft'){show(idx-1);e.preventDefault();}
  });
  show(0);
  fit();
});
"""


def resolve_spec(feature_dir, layout, spec_name):
    """機能フォルダのテスト仕様書(xlsx)を1件に確定する。

    ``spec_name`` の指定があればそれを、無ければ ``layout.spec_glob`` で探す。
    複数見つかった場合はどれが正本か決められないため中断する。
    """
    if spec_name:
        path = feature_dir / spec_name
        if not path.is_file():
            fail(f"仕様書が見つかりません: {path}")
        return path
    found = sorted(feature_dir.glob(layout.spec_glob))
    if len(found) > 1:
        names = "／".join(p.name for p in found)
        fail(f"仕様書が複数あります（--spec で指定してください）: {feature_dir}（{names}）")
    return found[0] if found else None


def page_name(no, fallback):
    """ケース別ページのファイル名（``case-<No>.html``）。No 無しはスラグから安全な名前を作る。"""
    base = no if no else re.sub(r"[^0-9A-Za-z_-]+", "_", fallback)[:40]
    return f"case-{base}.html"


def render_doc(title, toc_html, main_html, dialogs, feature, target, main_cls=""):
    """左に目次・右に本文の共通骨格で 1 つの HTML 文書を組み立てる。

    ``main_cls`` に ``pagemode`` を渡すと、ページ全体スクロール・スナップを行わない
    2ペイン構成（左右が独立スクロール）になる（証跡V2のケースページ・サマリ用）。
    """
    main_attr = f' class="main {main_cls}"' if main_cls else ' class="main"'
    return (
        "<!DOCTYPE html>\n<html lang=\"ja\">\n<head>\n<meta charset=\"UTF-8\">\n"
        "<meta name=\"viewport\" content=\"width=device-width, initial-scale=1\">\n"
        f"<title>{esc(title)}</title>\n"
        f"<style>{STYLE}</style>\n</head>\n"
        "<body>\n"
        # メニュー開閉状態は localStorage でページ間・再訪問間で維持する（描画前に反映してちらつきを防ぐ）。
        "<script>try{if(localStorage.getItem('e2eTocHidden')==='1')"
        "document.documentElement.classList.add('toc-hidden')}catch(e){}</script>\n"
        # ☰ はメニュー（レール）内に置き、内容と重ならないようにする（メニューは畳んでも38pxのレールが残る）。
        f"<nav class=\"toc\">\n"
        "<button type=\"button\" class=\"tocbtn\" title=\"メニュー表示切替\">☰</button>\n"
        f"<h1>{esc(feature)}<br>エビデンス"
        f"<br><small>{esc(target)}</small></h1>\n{toc_html}\n</nav>\n"
        f"<div{main_attr}>\n{main_html}\n</div>\n"
        f"{dialogs}\n"
        f"<script>{SCRIPT}</script>\n</body>\n</html>\n"
    )


def build_case_pages(evidence, feature_dir, layout, items, cases, results, feature, target):
    """サマリ専用 index.html と、ケースごとの個別ページ（case-<No>.html）を生成する（証跡V2）。

    ケース数が多くても 1 ファイルが肥大しないよう、index はサマリ表（各ページへのリンク）だけを持つ。
    既存の case-*.html はケース構成の変化で残骸が残らないよう、毎回消してから作り直す。
    """
    for stale in evidence.glob("case-*.html"):
        stale.unlink()
    toc = build_toc(items, results)
    for position, item in enumerate(items):
        registry = CsvRegistry(feature_dir, layout)
        entry = item["entry"]
        if entry["kind"] == "v2":
            section = build_case_v2(entry["entry"], cases, results, registry, evidence,
                                    page_cls="cpage")
        else:
            section = build_case(entry["shot"], cases, results, registry, page_cls="cpage")
        prev_item = items[position - 1] if position > 0 else None
        next_item = items[position + 1] if position + 1 < len(items) else None
        nav = ['<div class="pagenav">', '<a href="index.html">≡ 一覧へ</a>']
        if prev_item:
            nav.append(
                f'<a href="{esc(prev_item["href"])}">◀ No.{esc(prev_item["no"] or "")}</a>')
        else:
            nav.append('<span class="off">◀ 前ケースなし</span>')
        if next_item:
            nav.append(
                f'<a href="{esc(next_item["href"])}">No.{esc(next_item["no"] or "")} ▶</a>')
        else:
            nav.append('<span class="off">次ケースなし ▶</span>')
        nav.append('</div>')
        doc = render_doc(f"No.{item['no']} {item['name']} — {feature}", toc,
                         "".join(nav) + "\n" + section, registry.dialogs_html(),
                         feature, target, main_cls="pagemode")
        (evidence / item["href"]).write_text(doc, encoding="utf-8", newline="\n")
    registry = CsvRegistry(feature_dir, layout)
    summary = build_summary(feature, items, results, registry)
    doc = render_doc(f"E2E エビデンス — {target} / {feature}", toc, summary,
                     registry.dialogs_html(), feature, target, main_cls="pagemode")
    (evidence / "index.html").write_text(doc, encoding="utf-8", newline="\n")
    print(f"ケースページ {len(items)} 件を生成: {evidence.as_posix()}")
    return True


def build_index(feature_dir, layout, spec_name=None):
    evidence = feature_dir / layout.evidence_dir_name
    shots = sorted(evidence.glob("*.png"),
                   key=lambda p: (case_sort_key(case_no_of(p.stem)), p.name))
    steps_cases = read_steps(evidence)
    if not shots and not steps_cases:
        return False
    # steps.json（V2）の整合検査: 参照フレームは実在しなければならない（生成失敗を黙らせない）。
    # 要素単体ショット（attachFrame。ビューポートより大きい要素のフォールバック）も参照フレーム。
    referenced = {step[key] for entry in steps_cases.values()
                  for step in entry.get("steps", [])
                  for key in ("frame", "attachFrame") if step.get(key)}
    missing = sorted(name for name in referenced if not (evidence / name).is_file())
    if missing:
        fail(f"steps.json が参照するフレームがありません: {evidence}"
             f"（{'／'.join(missing[:5])}{' ほか' if len(missing) > 5 else ''}）")
    orphans = sorted(p.name for p in evidence.glob("*.jpg") if p.name not in referenced)
    if orphans:
        print(f"警告: steps.json に現れないフレームがあります: {evidence}"
              f"（{'／'.join(orphans[:5])}{' ほか' if len(orphans) > 5 else ''}）")
    spec = resolve_spec(feature_dir, layout, spec_name)
    if spec is None:
        print(f"警告: 仕様書が無いためケース本文なしで生成します: {feature_dir}")
    cases = read_cases(spec) if spec else {}
    results = read_results(evidence)
    feature = feature_dir.name
    target = feature_dir.parent.name
    # V2（steps.json）と V1（png）のケースを No 昇順に統合する。同じ No は V2 を正とする。
    entries = [{"kind": "v2", "no": no, "entry": entry}
               for no, entry in steps_cases.items()]
    for shot in shots:
        no = case_no_of(shot.stem)
        if no in steps_cases:
            print(f"警告: V2ケースと重複する png を無視します: {shot.name}")
            continue
        entries.append({"kind": "v1", "no": no, "shot": shot})
    entries.sort(key=lambda e: (case_sort_key(e["no"]),
                                e["shot"].name if e.get("shot") else ""))
    items = []
    for entry in entries:
        no = entry["no"]
        spec_row = cases.get(no, {}) if no else {}
        if entry["kind"] == "v2":
            name = spec_row.get("name") or entry["entry"].get("name") or ""
        else:
            name = spec_row.get("name") or entry["shot"].stem.replace("_", " ")
        if steps_cases:
            fallback = entry["shot"].stem if entry.get("shot") else "case"
            href = page_name(no, fallback)
        else:
            href = "#" + (f"case-{no}" if no else entry["shot"].stem)
        items.append({"no": no, "name": name, "href": href,
                      "data": spec_row.get("data", ""), "entry": entry})
    if steps_cases:
        # 証跡V2: サマリ index＋ケース別ページに分割（多ケースでも 1 ファイルが肥大しない）。
        return build_case_pages(evidence, feature_dir, layout, items, cases, results,
                                feature, target)
    # 従来（V1）: 単一ページ（過去実施単位の再生成互換）。
    registry = CsvRegistry(feature_dir, layout)
    sections = [build_case(item["entry"]["shot"], cases, results, registry)
                for item in items]
    toc = build_toc(items, results)
    summary = build_summary(feature, items, results, registry)
    doc = render_doc(f"E2E エビデンス — {target} / {feature}", toc,
                     summary + "\n" + "\n".join(sections),
                     registry.dialogs_html(), feature, target)
    (evidence / "index.html").write_text(doc, encoding="utf-8", newline="\n")
    return True


def feature_dirs(directory, layout):
    """エビデンス（png）を持つ機能フォルダを列挙する。

    機能フォルダを指定すればそれ自身、テスト実施単位を指定すればその配下の全機能が対象になる。深さを
    ``<実施単位>/<機能>/<エビデンス>`` に固定するため、より上位のフォルダを渡しても実施単位を跨いで拾うことはない。
    """
    if (directory / layout.evidence_dir_name).is_dir():
        candidates = [directory]
    else:
        candidates = sorted(path.parent
                            for path in directory.glob(f"*/{layout.evidence_dir_name}")
                            if path.is_dir())
    return [dir for dir in candidates
            if any((dir / layout.evidence_dir_name).glob("*.png"))
            or (dir / layout.evidence_dir_name / "steps.json").is_file()]


def parse_args(argv):
    parser = argparse.ArgumentParser(
        description="E2E エビデンスの受け入れ用 index.html を仕様書(xlsx)本文とともに生成する。")
    parser.add_argument("directories", nargs="+", metavar="ディレクトリ",
                        help="機能フォルダ、または実施単位フォルダ（配下の全機能を対象にする）")
    parser.add_argument("--spec", metavar="仕様書ファイル名",
                        help="機能フォルダ内に仕様書が複数あるとき正本を 1 件へ確定する")
    parser.add_argument("--evidence-dir-name", required=True, metavar="名前",
                        help="機能フォルダ内の証跡フォルダ名")
    parser.add_argument("--spec-glob", required=True, metavar="グロブ",
                        help="機能フォルダ内のテスト仕様書(xlsx)を選ぶグロブ")
    parser.add_argument("--data-dir-name", required=True, metavar="名前",
                        help="機能フォルダ内のテストデータフォルダ名")
    return parser.parse_args(argv)


def resolve_targets(args, layout):
    """対象の機能フォルダを解決する。実施単位を跨ぐ指定は生成せずに終了する。"""
    roots = [Path(name) for name in args.directories]
    for root in roots:
        if not root.is_dir():
            fail(f"ディレクトリが見つかりません: {root}")
    if args.spec and len(roots) != 1:
        fail("--spec は機能フォルダを1つだけ指定するときに使えます。")
    targets = sorted({dir for root in roots for dir in feature_dirs(root, layout)})
    if not targets:
        print("対象（png を持つ機能）がありませんでした。")
        return []
    units = sorted({dir.parent for dir in targets})
    if len(units) > 1:
        fail("テスト実施単位を跨ぐ指定はできません: " + "／".join(unit.name for unit in units))
    return targets


def main(argv):
    args = parse_args(argv)
    layout = Layout(args.evidence_dir_name, args.spec_glob, args.data_dir_name)
    targets = resolve_targets(args, layout)
    if not targets:
        return EXIT_OK
    if load_workbook is None:
        print("openpyxl が導入されていないため index を生成できません（pip install openpyxl）。",
              file=sys.stderr)
        return EXIT_NO_DEPENDENCY
    for feature_dir in targets:
        if build_index(feature_dir, layout, args.spec):
            print(f"index.html generated: {feature_dir.as_posix()}")
    return EXIT_OK


if __name__ == "__main__":
    raise SystemExit(main(sys.argv[1:]))
