package testgen.e2e.support.evidence;

import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Mouse;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.options.BoundingBox;
import com.microsoft.playwright.options.WaitForSelectorState;

/**
 * ag-grid 用の {@link E2eGridAdapter} 実装。ag-grid の内部構造（クラス名・属性）と内部挙動への
 * 依存を本クラスへ隔離する（検証部品・テストコードには持ち込まない）。
 *
 * <p>
 * Page Object がグリッドのルートセレクタを渡して生成し、グリッド系部品へ渡す。別のグリッド
 * ライブラリを使うプロジェクトは本クラスを使わず、{@link E2eGridAdapter} をプロジェクト側で
 * 実装する。
 *
 * <h2>列幅ドラッグ操作が前提とする ag-grid のリサイズ機構（ag-grid-community 32.2.0 のソースで実測）</h2>
 * <ul>
 * <li><b>リサイズ量は mousedown 位置からの純差分</b>（{@code clientX - dragStartX}）で解釈される
 * （horizontalResizeService）。途中経過やタイミングに意味はなく、各 mousemove イベントの
 * ポインタ位置だけで「要求幅」が決まる。ドラッグ開始しきい値も無い（リサイズバーは
 * {@code dragStartPixels: 0} で登録され、mousedown 直後からドラッグ扱い）。</li>
 * <li><b>要求幅が minWidth を下回る mousemove は、下限へ丸められず「イベントごと棄却」される</b>
 * （columnSizeService.resizeColumnSets の min/max 事前チェックが不合格だと早期 return）。
 * mouseup 時も最後の要求幅を再送するだけで、下限へのスナップは行われない。つまり列幅は
 * 「最後に受理された（下限以上だった）要求幅」で止まる。下限を通り越して引く操作は、通り越した
 * 分がすべて棄却されるため、最終幅が開始幅と移動刻みの位相に依存する非決定な結果を生む。</li>
 * <li><b>固定（ピン留め）列は、固定領域の合計幅が本体ビューポート幅に迫る方向（広げる）の要求も
 * 黙って棄却される</b>ことがある（ResizeFeature.onResizing の固定列ガード）。</li>
 * </ul>
 * この前提により、本実装は（1）目標幅ちょうどのポインタ位置へ 1 回で置く（{@link #resizeColumnTo}）、
 * （2）要求幅が 1px 刻みで単調に下がる mousemove 列を送り「最後に受理された要求幅＝実際の下限」で
 * 止める（{@link #shrinkColumnToMinimum}）ことで、決定的な結果を得る。
 *
 * <h2>列順変更（{@link #moveColumn}）が前提とする ag-grid の列移動機構（同ソースで実測）</h2>
 * <ul>
 * <li><b>列ヘッダーのドラッグは既定しきい値 {@code dragStartPixels=4} を超えてから開始される</b>
 * （dragService。リサイズバーの {@code 0} とは異なる）。本実装は移動方向への初動でしきい値を
 * 確実に超えてから目標へ移動する。</li>
 * <li><b>挿入位置は「処理された dragging イベント時点のポインタX位置と移動方向」から都度計算される</b>
 * （attemptMoveColumns → getBestColumnMoveIndexFromXPosition。表示列の累積幅と突合し、右方向
 * ドラッグでは 1 つ先へ進める補正が入る）。経路には依存しないため、ドロップ先ヘッダー中心への
 * 段階移動で決定的な結果になる。</li>
 * <li><b>移動不可（{@code suppressMovable}・{@code lockPosition}）の列はドラッグ自体が始まらず</b>
 * （workOutDraggable）、また移動候補の計算段階でも棄却される（calculateValidMoves）。この場合
 * 本操作は並びを変えずに終わる（可否の検証は検証部品側で行う）。</li>
 * <li><b>ドラッグ中の列には {@code ag-header-cell-moving} が付き、移動状態の解除で外れる</b>
 * （movingChanged リスナー）。本実装はこの解除を待ってから戻る。</li>
 * </ul>
 */
public final class E2eAgGridAdapter implements E2eGridAdapter {

    /** リサイズのドラッグ試行上限（ポインタが画面左端で頭打ちになり 1 回で届かない場合の再試行）。 */
    private static final int RESIZE_MAX_PASSES = 8;

    /** ドラッグ後の反映確認の読取間隔（ミリ秒）。反映は同期のため通常 1 回で安定する。 */
    private static final double RESIZE_POLL_INTERVAL_MS = 50;

    /** ドラッグ後の反映確認の読取回数上限。 */
    private static final int RESIZE_POLL_MAX_READS = 10;

    /** 列移動ドラッグの開始しきい値（既定 {@code dragStartPixels=4}）を確実に超える初動量（px）。 */
    private static final int MOVE_DRAG_START_NUDGE_PX = 8;

    /** 列移動後、moving 状態（{@code ag-header-cell-moving}）の解除を待つ上限（ms）。 */
    private static final double MOVE_SETTLE_TIMEOUT_MS = 2_000;

    private final Page page;
    private final String gridSelector;
    private final int rowIndexOffset;

    /**
     * ag-grid のアダプタを生成する。
     *
     * @param page 操作対象のページ
     * @param gridSelector グリッドのルート要素のセレクタ
     */
    public E2eAgGridAdapter(Page page, String gridSelector) {
        this(page, gridSelector, 0);
    }

    /**
     * 行インデックスのオフセットを指定して ag-grid のアダプタを生成する。
     *
     * <p>
     * ag-grid はページネーション時も {@code row-index} 属性を全件通しの絶対値で振るため、2 ページ目
     * 以降では表示先頭行の {@code row-index} が 0 にならない（表示件数 10 件の 2 ページ目なら 10）。
     * 検証部品は「0 起点の表示行インデックス」で行を指すため、現在のページの先頭行の絶対インデックスを
     * オフセットとして与えて対応づける。1 ページ目（既定）は 0。
     *
     * @param page 操作対象のページ
     * @param gridSelector グリッドのルート要素のセレクタ
     * @param rowIndexOffset 表示中のページの先頭行の絶対行インデックス（1 ページ目は 0）
     */
    public E2eAgGridAdapter(Page page, String gridSelector, int rowIndexOffset) {
        this.page = page;
        this.gridSelector = gridSelector;
        this.rowIndexOffset = rowIndexOffset;
    }

    @Override
    public Locator root() {
        return page.locator(gridSelector);
    }

    @Override
    public Locator verticalViewport() {
        return page.locator(gridSelector + " .ag-body-viewport");
    }

    @Override
    public Locator horizontalViewport() {
        return page.locator(gridSelector + " .ag-body-horizontal-scroll-viewport");
    }

    @Override
    public Locator scrollableHeaderArea() {
        return page.locator(gridSelector + " .ag-header-viewport");
    }

    @Override
    public Locator scrollableHeaderCells() {
        return page.locator(gridSelector + " .ag-header-viewport .ag-header-cell");
    }

    @Override
    public Locator pinnedHeaderCells() {
        return page.locator(gridSelector + " .ag-pinned-left-header .ag-header-cell");
    }

    @Override
    public Locator headerLabel(Locator headerCell) {
        return headerCell.locator(".ag-header-cell-text");
    }

    @Override
    public Locator headerCell(String colId) {
        return page.locator(gridSelector + " .ag-header-cell[col-id='" + colId + "']");
    }

    @Override
    public Locator cell(int rowIndex, String colId) {
        // 行要素はピン留め列コンテナと中央コンテナに重複して存在するが、
        // 列は必ずどちらか一方にのみ属するため row-index × col-id で一意になる。
        return page.locator(gridSelector + " [row-index='" + (rowIndexOffset + rowIndex)
                + "'] [col-id='" + colId + "']");
    }

    @Override
    public int rowCount() {
        // 行要素はピン留め列コンテナにも重複して存在するため、中央コンテナの行のみを数える。
        return page.locator(gridSelector + " .ag-center-cols-container [row-index]").count();
    }

    // ---- 列幅ドラッグ操作（クラス javadoc「リサイズ機構」の前提に基づく） ----

    @Override
    public void resizeColumnBy(String colId, int deltaPx) {
        resizeColumnTo(colId, columnWidthPx(colId) + deltaPx);
    }

    @Override
    public void resizeColumnTo(String colId, int targetWidthPx) {
        for (int pass = 0; pass < RESIZE_MAX_PASSES; pass++) {
            int width = columnWidthPx(colId);
            if (width == targetWidthPx) {
                return;
            }
            // 要求幅＝目標幅のポインタ位置へ 1 回で置く。ポインタは x=0 までしか動かせないため、
            // 列が画面左端に近い大幅な縮小は 1 回で届かないことがある（その場合は繰り返す）。
            dragResizeOnce(colId, targetWidthPx, false);
            int after = settledWidthPx(colId);
            if (after == targetWidthPx) {
                return;
            }
            if (after == width) {
                // ドラッグしても変化しない＝ag-grid が要求を棄却している（下限未満・固定列ガード等）
                break;
            }
        }
        throw new IllegalStateException(colId + " 列の幅を " + targetWidthPx + "px へ変更できません"
                + "でした（実測 " + columnWidthPx(colId) + "px）。ag-grid が要求を棄却しています"
                + "（minWidth 未満、または固定列領域の上限超過の可能性）。");
    }

    @Override
    public void shrinkColumnToMinimum(String colId) {
        // 要求幅を 1px 刻みで 0 まで降ろす。下限未満の要求はすべて棄却されるため、最後に受理された
        // 要求幅＝実際の下限でちょうど止まる（幅の読取は再試行の要否判定にのみ用いる）。
        for (int pass = 0; pass < RESIZE_MAX_PASSES; pass++) {
            int before = columnWidthPx(colId);
            dragResizeOnce(colId, 0, true);
            int after = settledWidthPx(colId);
            if (after >= before) {
                return; // これ以上狭まらない＝下限に到達している
            }
        }
    }

    /**
     * リサイズハンドルを 1 回ドラッグして、列幅の要求が {@code requestWidthPx} になるポインタ
     * 位置まで引く（ポインタは x=0 で頭打ち）。{@code onePixelSteps} 指定時は、要求幅が 1px 刻みで
     * 単調に変化する等間隔の mousemove 列として送る。
     */
    private void dragResizeOnce(String colId, int requestWidthPx, boolean onePixelSteps) {
        BoundingBox handle = requireBox(
                headerCell(colId).locator(".ag-header-cell-resize"), colId + " 列のリサイズハンドル");
        int startWidth = columnWidthPx(colId);
        double pressX = Math.rint(handle.x + handle.width / 2);
        double pressY = Math.rint(handle.y + handle.height / 2);
        // リサイズ量は mousedown 位置からの純差分のため、最終位置だけで要求幅が決まる。
        double targetX = Math.max(0, pressX + (requestWidthPx - startWidth));
        Mouse mouse = page.mouse();
        mouse.move(pressX, pressY);
        mouse.down();
        Mouse.MoveOptions options = new Mouse.MoveOptions();
        if (onePixelSteps) {
            options.setSteps(Math.max(1, (int) Math.round(Math.abs(targetX - pressX))));
        }
        mouse.move(targetX, pressY, options);
        mouse.up();
    }

    @Override
    public void moveColumn(String colId, String targetColId) {
        BoundingBox from = requireBox(headerCell(colId), colId + " の列ヘッダー");
        BoundingBox to = requireBox(headerCell(targetColId), targetColId + " の列ヘッダー");
        final double startX = Math.rint(from.x + from.width / 2);
        final double startY = Math.rint(from.y + from.height / 2);
        final double endX = Math.rint(to.x + to.width / 2);
        final double endY = Math.rint(to.y + to.height / 2);
        Mouse mouse = page.mouse();
        mouse.move(startX, startY);
        mouse.down();
        // ドラッグ開始しきい値（クラス javadoc「列移動機構」）を移動方向への初動で確実に超える
        double direction = Math.signum(endX - startX);
        if (direction == 0) {
            direction = 1;
        }
        mouse.move(startX + direction * MOVE_DRAG_START_NUDGE_PX, startY,
                new Mouse.MoveOptions().setSteps(2));
        // ドロップ先ヘッダーの中心へ段階移動して離す（挿入位置はポインタ位置と移動方向で決まる）
        mouse.move(endX, endY, new Mouse.MoveOptions().setSteps(12));
        mouse.up();
        // moving 状態の解除（移動の完了）を待つ。移動不可列などでドラッグが始まらなかった場合は
        // moving 状態の要素が存在せず、即時に解除済みとして返る。
        page.locator(gridSelector + " .ag-header-cell-moving").first()
                .waitFor(new Locator.WaitForOptions()
                        .setState(WaitForSelectorState.HIDDEN)
                        .setTimeout(MOVE_SETTLE_TIMEOUT_MS));
    }

    /** ドラッグ直後の列幅（ピクセル）。連続 2 回の読取が一致するまで待って揺れを除く。 */
    private int settledWidthPx(String colId) {
        int last = columnWidthPx(colId);
        for (int i = 0; i < RESIZE_POLL_MAX_READS; i++) {
            page.waitForTimeout(RESIZE_POLL_INTERVAL_MS);
            int now = columnWidthPx(colId);
            if (now == last) {
                return now;
            }
            last = now;
        }
        return last;
    }

    /** 列ヘッダーの現在幅（ピクセル）。操作の制御にのみ用い、検証材料として外へ返さない。 */
    private int columnWidthPx(String colId) {
        return (int) Math.rint(requireBox(headerCell(colId), colId + " の列ヘッダー").width);
    }

    private static BoundingBox requireBox(Locator target, String what) {
        BoundingBox box = target.boundingBox();
        if (box == null) {
            throw new IllegalStateException(what + " が描画されていません");
        }
        return box;
    }
}
