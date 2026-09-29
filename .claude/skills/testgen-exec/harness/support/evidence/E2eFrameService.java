package testgen.e2e.support.evidence;

import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.options.BoundingBox;
import com.microsoft.playwright.options.ScreenshotType;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 証跡V2のフレーム撮影（固定ビューポート・ハイライト・見切れ検査・代理証跡パネル）。
 *
 * <p>
 * 標準フレームは実行中のビューポート（1280×800）のまま撮る。シナリオ途中でビューポートを変更すると resize
 * イベントで検証対象の状態（開いたメニュー等）を壊すため行わない。検証対象は撮影前に {@link #reveal(Locator)}
 * で画面中央へ入れ、{@link #ensureInViewport(Locator, String)} で フレーム内に完全に収まること（見切れ禁止）を機械検査する。
 */
public final class E2eFrameService {

    /** ハイライト・パネルの z-index（情報バー 2147483647 より下・アプリより上）。 */
    private static final String DRAW_HIGHLIGHT = """
            (rects) => {
              let c = document.getElementById('__e2e_hl__');
              if (!c) { c = document.createElement('div'); c.id = '__e2e_hl__';
                        document.body.appendChild(c); }
              c.innerHTML = '';
              rects.forEach((r) => {
                const color = r.color || '#e11d48';
                const shadow = r.shadow || 'rgba(225,29,72,.22)';
                const d = document.createElement('div');
                d.style.cssText = 'position:fixed;pointer-events:none;z-index:2147483645;'
                  + 'left:' + (r.x - 3) + 'px;top:' + (r.y - 3) + 'px;'
                  + 'width:' + (r.w + 6) + 'px;height:' + (r.h + 6) + 'px;'
                  + 'border:3px ' + (r.ghost ? 'dashed' : 'solid') + ' ' + color + ';'
                  + 'border-radius:6px;box-shadow:0 0 0 3px ' + shadow;
                if (!r.nobadge) {
                  // バッジは自分の枠の左上角に重ねる（枠の上外側に置くと、行が縦に密に並ぶリストで
                  // 番号が1行上の要素に載って見え、対応関係を誤読させるため）。
                  const b = document.createElement('span');
                  b.textContent = String(r.badge);
                  b.style.cssText = 'position:absolute;left:-3px;top:-3px;background:' + color
                    + ';color:#fff;font:700 12px/1 sans-serif;padding:3px 7px;border-radius:4px';
                  d.appendChild(b);
                }
                if (r.ghost) {
                  const l = document.createElement('span');
                  l.textContent = r.ghostLabel || '非表示を確認（元の表示位置）';
                  const base = 'position:absolute;background:rgba(225,29,72,.92);color:#fff;'
                    + 'font:700 13px/1.4 sans-serif;padding:4px 10px;border-radius:4px;'
                    + 'white-space:nowrap;';
                  if (r.labelOutside) {
                    // 枠内に実在コンテンツ（アンカー）が写る用途: 覆わないよう枠外へ置く
                    // （下外側 → 上外側（情報バーを避ける）→ 枠内下端 の順のフォールバック）
                    const labelSpace = 34;
                    if (r.y + r.h + labelSpace < window.innerHeight) {
                      l.style.cssText = base + 'left:50%;top:100%;transform:translate(-50%,8px)';
                    } else if (r.y - labelSpace > 90) {
                      l.style.cssText = base + 'left:50%;top:-8px;transform:translate(-50%,-100%)';
                    } else {
                      l.style.cssText = base + 'left:50%;bottom:6px;transform:translate(-50%,0)';
                    }
                  } else {
                    l.style.cssText = base + 'left:50%;top:50%;transform:translate(-50%,-50%)';
                  }
                  d.appendChild(l);
                }
                c.appendChild(d);
              });
            }
            """;

    /**
     * 祖先の overflow（スクロールコンテナ等）とビューポートで切り取った可視矩形を返すスクリプト。
     * スクロールで領域外にある部分に枠を描くと、無関係な要素の上にハイライトが重なり証跡を誤読させる
     * ため、実際に見えている範囲だけを返す（完全に見えていなければ null）。
     */
    private static final String VISIBLE_BOX = """
            el => {
              const r = el.getBoundingClientRect();
              let x1 = r.left, y1 = r.top, x2 = r.right, y2 = r.bottom;
              for (let a = el.parentElement; a; a = a.parentElement) {
                const s = getComputedStyle(a);
                if (/(auto|scroll|hidden|clip)/.test(s.overflowX + s.overflowY)) {
                  const b = a.getBoundingClientRect();
                  x1 = Math.max(x1, b.left); y1 = Math.max(y1, b.top);
                  x2 = Math.min(x2, b.right); y2 = Math.min(y2, b.bottom);
                }
              }
              x1 = Math.max(x1, 0); y1 = Math.max(y1, 0);
              x2 = Math.min(x2, window.innerWidth); y2 = Math.min(y2, window.innerHeight);
              // 数px のスライバー（スクロール境界に上端だけ覗く行等）は「見えている」と扱わない。
              return (x2 - x1 >= 6 && y2 - y1 >= 6)
                  ? {x: x1, y: y1, w: x2 - x1, h: y2 - y1} : null;
            }
            """;

    private static final String CLEAR_OVERLAYS = """
            () => {
              for (const id of ['__e2e_hl__', '__e2e_proxy__']) {
                const el = document.getElementById(id);
                if (el) el.remove();
              }
            }
            """;

    private static final String DRAW_PROXY_PANEL = """
            (data) => {
              const esc = (s) => String(s).replace(/[&<>"]/g,
                  (ch) => ({'&':'&amp;','<':'&lt;','>':'&gt;','"':'&quot;'}[ch]));
              const p = document.createElement('div');
              p.id = '__e2e_proxy__';
              p.style.cssText = 'position:fixed;right:24px;top:96px;z-index:2147483645;'
                + 'background:#fff;border:2px solid #7c3aed;border-radius:10px;'
                + 'box-shadow:0 12px 40px rgba(0,0,0,.35);min-width:340px;max-width:520px;'
                + 'pointer-events:none;font-family:sans-serif';
              const items = data.items.map((v) => '<li>' + (v === ''
                  ? '<i style="color:#94a3b8">（空欄）</i>' : esc(v)) + '</li>').join('');
              p.innerHTML =
                '<div style="background:#7c3aed;color:#fff;font:700 13px/1.4 sans-serif;'
                + 'padding:8px 12px;border-radius:8px 8px 0 0">【代理証跡】' + esc(data.title)
                + '</div>'
                + '<ol style="margin:8px 0;padding:6px 12px 10px 34px;font:14px/1.9 sans-serif">'
                + items + '</ol>'
                + '<div style="padding:0 12px 10px;font:11px/1.5 sans-serif;color:#64748b">'
                + esc(data.note) + '</div>';
              document.body.appendChild(p);
            }
            """;

    private final Page page;
    private final Path evidenceDir;

    /**
     * フレーム撮影サービスを生成する。
     *
     * @param page 操作対象のページ
     * @param evidenceDir 証跡フォルダ
     */
    public E2eFrameService(Page page, Path evidenceDir) {
        this.page = page;
        this.evidenceDir = evidenceDir;
    }

    /**
     * 現在の画面を JPEG（品質80）で撮影する。
     *
     * @param fileName フレーム画像ファイル名
     */
    public void capture(String fileName) {
        page.screenshot(new Page.ScreenshotOptions().setType(ScreenshotType.JPEG).setQuality(80)
                .setPath(evidenceDir.resolve(fileName)));
    }

    /**
     * 検証対象を表示領域の中央へスクロールして映す。
     *
     * @param target 検証対象
     */
    public void reveal(Locator target) {
        target.first().evaluate("el => el.scrollIntoView({block: 'center', behavior: 'instant'})");
    }

    /**
     * 対象と基準の<b>2要素が同一画角に収まる</b>ようスクロールを調整して映す（{@code sameText} 等の
     * 要素間比較部品用）。対象を中央へ寄せた後、両要素の外接範囲がフレームの有効領域
     * （上部情報バー除く）から縦にはみ出す場合に限り、外接範囲が収まる位置へスクロールを
     * 微調整する。微調整の対象は<b>対象要素の最も近いスクロール可能な祖先</b>（無ければ文書＝
     * window）——本スイートの対象アプリは文書ではなく内容コンテナ（overflow-y:auto）がスクロールを
     * 持つ構成があり、window へのスクロールでは動かないため。基準要素が対象と別のスクロール
     * コンテナに属する配置は対象外（後続の見切れ検査が検出・記録する）。外接範囲が有効領域より
     * 大きく1画角に収まり得ない場合も何もしない（同じく {@link #ensureInViewport(Locator, String)}
     * が見切れとして検出・記録する）。
     *
     * @param subject 検証対象（中央寄せの基準。微調整するスクロール祖先もこの要素から解決する）
     * @param reference 基準要素
     */
    public void revealTogether(Locator subject, Locator reference) {
        reveal(subject);
        BoundingBox s = subject.first().boundingBox();
        BoundingBox r = reference.first().boundingBox();
        if (s == null || r == null) {
            return; // 矩形を持たない要素は通常の見切れ検査へ委ねる
        }
        double margin = 4;
        double top = Math.min(s.y, r.y);
        double bottom = Math.max(s.y + s.height, r.y + r.height);
        double allowedTop = barHeight() + margin;
        double allowedBottom = page.viewportSize().height - margin;
        if ((top >= allowedTop && bottom <= allowedBottom)
                || bottom - top > allowedBottom - allowedTop) {
            return;
        }
        double delta = 0;
        if (bottom > allowedBottom) {
            delta = bottom - allowedBottom;
        }
        if (top - delta < allowedTop) {
            delta = top - allowedTop;
        }
        subject.first().evaluate("""
                (el, d) => {
                  let node = el.parentElement;
                  while (node) {
                    const style = getComputedStyle(node);
                    if (/(auto|scroll)/.test(style.overflowY)
                        && node.scrollHeight > node.clientHeight) {
                      node.scrollTop += d;
                      return;
                    }
                    node = node.parentElement;
                  }
                  window.scrollBy(0, d);
                }""", delta);
    }

    /**
     * 対象がフレーム内（上部情報バー除く）に完全に収まっていることを検査する（見切れ禁止）。
     *
     * @param target 検証対象
     * @param desc 検証の説明（エラーメッセージ用）
     * @return 収まっていれば真。対象がビューポートの有効高より大きく 1 フレームに収まり得ない場合は偽 （呼び出し側が要素単体ショット等のフォールバックを行う）
     * @throws AssertionError 収まるはずの対象が見切れているとき
     */
    public boolean ensureInViewport(Locator target, String desc) {
        BoundingBox box = target.first().boundingBox();
        if (box == null) {
            return true; // 非表示検証（hidden）の対象は矩形を持たない
        }
        int bar = barHeight();
        int viewportWidth = page.viewportSize().width;
        int viewportHeight = page.viewportSize().height;
        double margin = 4;
        if (box.height > viewportHeight - bar - margin * 2) {
            return false; // ビューポートより大きい要素（フォールバック対象）
        }
        boolean fits = box.x >= -margin && box.y >= bar - margin
                && box.x + box.width <= viewportWidth + margin
                && box.y + box.height <= viewportHeight + margin;
        if (!fits) {
            throw new AssertionError(String.format(
                    "検証対象がフレーム内に収まっていません（見切れ）: %s [x=%.0f y=%.0f w=%.0f h=%.0f bar=%d]", desc,
                    box.x, box.y, box.width, box.height, bar));
        }
        return true;
    }

    /**
     * 要素単体ショットを撮る（ビューポートに収まらない要素の見切れフォールバック）。
     *
     * <p>
     * ページ・ビューポートには手を触れないため、標準フレームの画角・アプリ状態に影響しない。
     *
     * @param target 撮影する要素
     * @param fileName 出力ファイル名
     */
    public void captureElement(Locator target, String fileName) {
        target.first().screenshot(new Locator.ScreenshotOptions().setType(ScreenshotType.JPEG)
                .setQuality(80).setPath(evidenceDir.resolve(fileName)));
    }

    /** 検証対象のハイライト色（赤）。 */
    public static final String COLOR_VERIFY = "#e11d48";
    /** 操作対象のハイライト色（青）。検証（赤）と区別する。 */
    public static final String COLOR_OP = "#2563eb";

    /**
     * 対象要素へハイライト枠＋番号バッジを描画する（検証用・赤）。
     *
     * @param targets ハイライトする要素（描画順に 1, 2, … のバッジ）
     * @return 描画した矩形（steps.json 記録用。ビューポート座標）
     */
    public List<Map<String, Object>> highlight(List<Locator> targets) {
        return highlight(targets, COLOR_VERIFY, "rgba(225,29,72,.22)");
    }

    /**
     * 対象要素へハイライト枠＋番号バッジを指定色で描画する。
     *
     * @param targets ハイライトする要素（描画順に 1, 2, … のバッジ）
     * @param color 枠・バッジの色
     * @param shadow 外周のぼかし色
     * @return 描画した矩形（steps.json 記録用。ビューポート座標）
     */
    public List<Map<String, Object>> highlight(List<Locator> targets, String color, String shadow) {
        return highlight(targets, color, shadow, true);
    }

    /**
     * 対象要素へハイライト枠を指定色で描画する（バッジ有無を選べる）。
     *
     * <p>
     * グリッドの行キー列のように縦へ密に並ぶ対象は、バッジが上隣のセル値を覆って読めなくするため バッジ無し（枠のみ）で描画する。
     *
     * @param targets ハイライトする要素
     * @param color 枠の色
     * @param shadow 外周のぼかし色
     * @param withBadges 番号バッジを付けるか
     * @return 描画した矩形（steps.json 記録用。ビューポート座標）
     */
    public List<Map<String, Object>> highlight(
            List<Locator> targets,
            String color,
            String shadow,
            boolean withBadges) {
        List<Map<String, Object>> rects = new ArrayList<>();
        int badge = 1;
        for (Locator target : targets) {
            int count = target.count();
            for (int i = 0; i < count; i++) {
                BoundingBox box = visibleBox(target.nth(i));
                if (box == null) {
                    continue;
                }
                Map<String, Object> rect =
                        rect(box.x, box.y, box.width, box.height, badge++, false);
                rect.put("color", color);
                rect.put("shadow", shadow);
                if (!withBadges) {
                    rect.put("nobadge", true);
                }
                rects.add(rect);
            }
        }
        if (!rects.isEmpty()) {
            page.evaluate(DRAW_HIGHLIGHT, rects);
        }
        return rects;
    }

    /**
     * 対象要素へハイライト枠＋明示番号のバッジを描画する。
     *
     * <p>
     * 連続フレーム（列の横スクロール等）でフレームをまたいで対象の同一性を示すために使う。 自動採番の {@link #highlight(List, String, String)}
     * と異なり、呼び出し側が対象ごとの 通し番号を与える（同一対象は全フレームで同一番号になる）。 番号 0 以下の対象は番号を付けず枠のみで描画する
     * （進行する系列に属さない対象——固定列等——を番号の系列から外すため）。
     *
     * @param targets ハイライトする要素（各要素は単一要素に解決されること）
     * @param badgeNumbers 対象ごとのバッジ番号（targets と同数。0 以下は番号なし＝枠のみ）
     * @param color 枠・バッジの色
     * @param shadow 外周のぼかし色
     * @return 描画した矩形（steps.json 記録用。ビューポート座標）
     */
    public List<Map<String, Object>> highlightNumbered(
            List<Locator> targets,
            List<Integer> badgeNumbers,
            String color,
            String shadow) {
        if (targets.size() != badgeNumbers.size()) {
            throw new IllegalArgumentException("targets と badgeNumbers の数が一致しません: "
                    + targets.size() + " / " + badgeNumbers.size());
        }
        List<Map<String, Object>> rects = new ArrayList<>();
        for (int i = 0; i < targets.size(); i++) {
            BoundingBox box = visibleBox(targets.get(i).first());
            if (box == null) {
                continue;
            }
            int badge = badgeNumbers.get(i);
            Map<String, Object> rect =
                    rect(box.x, box.y, box.width, box.height, Math.max(badge, 0), false);
            rect.put("color", color);
            rect.put("shadow", shadow);
            if (badge <= 0) {
                rect.put("nobadge", true);
            }
            rects.add(rect);
        }
        if (!rects.isEmpty()) {
            page.evaluate(DRAW_HIGHLIGHT, rects);
        }
        return rects;
    }

    /**
     * 指定座標の矩形へハイライト枠（検証用・赤）を描画する。
     *
     * <p>
     * 要素単位ではない検証対象——テキストの一部（メール本文の1行等）——のハイライトに使う
     * （{@code Range#getBoundingClientRect} 等で得たビューポート座標を渡す）。番号バッジは
     * 付けない（テキスト行は縦に密で、バッジが行頭の文字を覆って読めなくするため。単一矩形の
     * ため列挙の番号も不要）。
     *
     * @param x 矩形 x／@param y 矩形 y／@param w 幅／@param h 高さ（ビューポート座標）
     * @return 描画した矩形（steps.json 記録用）
     */
    public List<Map<String, Object>> highlightRect(double x, double y, double w, double h) {
        Map<String, Object> rect = rect(x, y, w, h, 1, false);
        rect.put("color", COLOR_VERIFY);
        rect.put("shadow", "rgba(225,29,72,.22)");
        rect.put("nobadge", true);
        List<Map<String, Object>> rects = List.of(rect);
        page.evaluate(DRAW_HIGHLIGHT, rects);
        return rects;
    }

    /**
     * 対象の可視矩形（祖先スクロールコンテナ・ビューポートで切り取った範囲）を返す。
     *
     * @param target 単一要素に解決されるロケータ
     * @return 可視矩形。要素が不在・非表示・完全に領域外の場合は null
     */
    private BoundingBox visibleBox(Locator target) {
        if (target.boundingBox() == null) {
            return null; // 非表示・不在
        }
        Object clipped = target.evaluate(VISIBLE_BOX);
        if (!(clipped instanceof Map<?, ?> visible)) {
            return null;
        }
        BoundingBox box = new BoundingBox();
        box.x = ((Number) visible.get("x")).doubleValue();
        box.y = ((Number) visible.get("y")).doubleValue();
        box.width = ((Number) visible.get("w")).doubleValue();
        box.height = ((Number) visible.get("h")).doubleValue();
        return box;
    }

    /**
     * 「非表示になった」ことを示す破線ゴースト枠を、元の表示位置へ描画する。
     *
     * @param x 元位置 x／@param y 元位置 y／@param w 幅／@param h 高さ（ビューポート座標）
     * @return 描画した矩形（steps.json 記録用）
     */
    public List<Map<String, Object>> ghost(double x, double y, double w, double h) {
        return ghost(x, y, w, h, null);
    }

    /**
     * 破線ゴースト枠を、ラベル文言を指定して元の表示位置へ描画する。
     *
     * <p>
     * 削除により後続要素が詰め上がる場合など、枠内に別要素が写る状況ではその旨をラベルで明示する （呼び出し側が {@link #occupiedAt} の判定結果に応じて文言を組み立てる）。
     *
     * @param x 元位置 x／@param y 元位置 y／@param w 幅／@param h 高さ（ビューポート座標）
     * @param label 枠中央に表示するラベル（null なら既定の「非表示を確認（元の表示位置）」）
     * @return 描画した矩形（steps.json 記録用）
     */
    public List<Map<String, Object>> ghost(double x, double y, double w, double h, String label) {
        return ghost(x, y, w, h, label, false);
    }

    /**
     * 破線ゴースト枠を、ラベルの配置を指定して描画する。
     *
     * <p>
     * ラベル枠外配置（labelOutside=true）は、枠内に実在コンテンツが写る用途（absent 部品の文脈アンカー等）
     * で使う。枠の下外側 → 上外側 → 枠内下端の順で画面に収まる位置へ置き、確認対象を覆わない。
     * 枠内が空である前提の用途（transition 部品の消失位置）は中央配置（labelOutside=false）を使う。
     *
     * @param x 元位置 x／@param y 元位置 y／@param w 幅／@param h 高さ（ビューポート座標）
     * @param label 枠に表示するラベル（null なら既定の「非表示を確認（元の表示位置）」）
     * @param labelOutside ラベルを枠外へ置くか
     * @return 描画した矩形（steps.json 記録用）
     */
    public List<Map<String, Object>> ghost(
            double x, double y, double w, double h, String label, boolean labelOutside) {
        Map<String, Object> rect = rect(x, y, w, h, 1, true);
        rect.put("nobadge", true); // ゴーストは検証対象の列挙ではないため番号バッジを付けない
        if (label != null) {
            rect.put("ghostLabel", label);
        }
        if (labelOutside) {
            rect.put("labelOutside", true);
        }
        List<Map<String, Object>> rects = List.of(rect);
        page.evaluate(DRAW_HIGHLIGHT, rects);
        return rects;
    }

    /**
     * ゴースト判定の前準備として、対象の祖先チェーンを記録する（操作で対象が消える前に呼ぶ）。
     *
     * <p>
     * 削除後の {@link #occupierAt} が「祖先コンテナの背景（視覚的に空）」と「流入した別の要素」を 区別するために使う。記録はページの {@code window} 上の
     * {@code WeakSet} で、判定時に破棄される。
     *
     * @param target 消える予定の対象
     */
    public void markGhostAncestors(Locator target) {
        target.first().evaluate("""
                el => {
                  const anc = new WeakSet();
                  let n = el.parentElement;
                  while (n) { anc.add(n); n = n.parentElement; }
                  window.__e2eGhostAnc = anc;
                }
                """);
    }

    /**
     * 指定座標（ビューポート座標）を現在占有している「別の要素」の識別情報を返す。
     *
     * <p>
     * {@link #markGhostAncestors} で記録した祖先コンテナの背景がヒットした場合は「視覚的に空」と みなして null
     * を返す。祖先以外の要素（削除で流入した行・繰り上がった後続内容・遷移後の別画面 等）がヒットした場合は、タグ・class・先頭テキスト（20字）の識別情報を返す。ページ遷移等で
     * 記録が失われている場合はヒット要素を全て「別の要素」として扱う。記録は判定時に破棄する。 情報バー等のハーネスオーバーレイは {@code pointer-events:none}
     * のため判定に掛からない。
     *
     * @param x 判定する x 座標
     * @param y 判定する y 座標
     * @return 占有している別要素の識別情報（空・祖先コンテナの背景のみなら null）
     */
    public String occupierAt(double x, double y) {
        Object hit = page.evaluate("""
                ([x, y]) => {
                  const el = document.elementFromPoint(x, y);
                  const anc = window.__e2eGhostAnc;
                  delete window.__e2eGhostAnc;
                  if (!el || el === document.body || el === document.documentElement) {
                    return null;
                  }
                  if (anc && anc.has(el)) return null;
                  const cls = el.classList && el.classList.length
                      ? '.' + Array.from(el.classList).join('.') : '';
                  const text = (el.textContent || '').trim().slice(0, 20);
                  return el.tagName.toLowerCase() + cls + (text ? '「' + text + '」' : '');
                }
                """, List.of(x, y));
        return hit == null ? null : hit.toString();
    }

    /**
     * 代理証跡パネル（画面に出ない内容の描画）を表示する。
     *
     * @param title パネル表題
     * @param items 一覧表示する値
     * @param note 代理証跡である旨の注記
     */
    public void drawProxyPanel(String title, List<String> items, String note) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("title", title);
        data.put("items", items);
        data.put("note", note);
        page.evaluate(DRAW_PROXY_PANEL, data);
    }

    /** ハイライト・代理証跡パネルを消す（撮影後に呼ぶ）。 */
    public void clearOverlays() {
        page.evaluate(CLEAR_OVERLAYS);
    }

    /**
     * アプリの内部スクロール位置を返す（{@code .app-content}。無ければウィンドウ）。
     *
     * @return 現在のスクロール位置
     */
    public double scrollTop() {
        Object top = page.evaluate("() => { const c = document.querySelector('.app-content');"
                + " return c ? c.scrollTop : window.scrollY; }");
        return top instanceof Number number ? number.doubleValue() : 0;
    }

    /**
     * アプリの内部スクロール位置を設定する（変化前と同じ画角へ戻す用途）。
     *
     * @param top 設定するスクロール位置
     */
    public void scrollTo(double top) {
        page.evaluate(
                "(top) => { const c = document.querySelector('.app-content');"
                        + " if (c) { c.scrollTop = top; } else { window.scrollTo(0, top); } }",
                top);
    }

    /** @return 撮影時のパス（{@code location.pathname + location.search}） */
    public String currentPath() {
        Object result = page.evaluate("() => location.pathname + location.search");
        return result == null ? "" : result.toString();
    }

    private int barHeight() {
        Object height =
                page.evaluate("() => { const b = document.getElementById('__e2e_overlay__');"
                        + " return b ? b.offsetHeight : 0; }");
        return height instanceof Number number ? number.intValue() : 0;
    }

    private static Map<String, Object> rect(
            double x,
            double y,
            double w,
            double h,
            int badge,
            boolean ghost) {
        Map<String, Object> rect = new LinkedHashMap<>();
        rect.put("x", x);
        rect.put("y", y);
        rect.put("w", w);
        rect.put("h", h);
        rect.put("badge", badge);
        rect.put("ghost", ghost);
        return rect;
    }
}
