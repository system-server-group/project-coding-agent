package testgen.e2e.support.evidence;

import com.microsoft.playwright.Locator;

/**
 * 仮想化グリッドの構造契約。検証部品（{@link E2eVerify} のグリッド系部品）が必要とする
 * ロケータだけを供給する。
 *
 * <p>
 * グリッドライブラリ固有のセレクタ（内部クラス名・属性）は本契約の実装へ隔離し、検証部品・
 * テストコードには持ち込まない。ag-grid はキット同梱の {@link E2eAgGridAdapter} を使い、
 * 別のライブラリを使うプロジェクトは本契約をプロジェクト側（{@code testgen.e2e} 配下）で
 * 実装して部品へ渡す。
 *
 * <p>
 * アダプタは<b>構造（ロケータ）の供給</b>と<b>グリッド固有のドラッグ操作（列幅変更・列順変更）</b>を
 * 担う。DOM から値を読み取って返してはならない——値の読取・期待値との突合・記録は検証部品側の責務
 * （収集と判断の分離をコード構造で保つ）。操作の実装内部での幅・座標の読取は操作の制御にのみ用い、
 * 検証材料として外へ返さない。
 *
 * <p>
 * グリッドのドラッグ操作をアダプタの責務とするのは、ドラッグイベントをグリッドライブラリがどう
 * 解釈するか（リサイズ量の基準点・下限/上限の扱い・イベントの棄却・ドラッグ開始しきい値・並べ替えの
 * 判定）が実装固有の内部仕様であり、テスト実装側で素の {@code page.mouse()} により組むと非決定的な
 * 結果を生むため（ag-grid 実装の注記を参照）。
 * <b>テストコード・Page Object でリサイズ・列移動のドラッグを手組みしてはならない。</b>
 */
public interface E2eGridAdapter {

    /**
     * 列ヘッダーのリサイズハンドルをドラッグして、列幅を指定幅へ決定的に変更する。
     *
     * <p>
     * 指定幅へ到達できない場合（ライブラリ側の制約——下限・上限・固定列領域の制限——で要求が
     * 棄却された場合）は例外で失敗を報告する。近似値で黙って成功扱いにしない。
     *
     * @param colId 列の識別子
     * @param targetWidthPx 変更後の列幅（ピクセル）
     */
    void resizeColumnTo(String colId, int targetWidthPx);

    /**
     * 列幅を現在幅からの相対量でドラッグ変更する（正で広げる・負で狭める）。
     * 到達できない場合は例外で失敗を報告する。
     *
     * @param colId 列の識別子
     * @param deltaPx 幅の変更量（ピクセル）
     */
    void resizeColumnBy(String colId, int deltaPx);

    /**
     * 列ヘッダーのリサイズハンドルをドラッグして、列幅をライブラリが許す下限まで決定的に狭める。
     *
     * <p>
     * 下限値の事前知識は不要（操作は「これ以上狭まらない」点で止まる）。到達後の幅が仕様どおりで
     * あることの検証は検証部品側で行う。
     *
     * @param colId 列の識別子
     */
    void shrinkColumnToMinimum(String colId);

    /**
     * 列ヘッダーをドラッグして、別の列ヘッダーの上へドロップする（列順変更）。
     *
     * <p>
     * 操作の契約は「対象列のヘッダーをつかみ、ドロップ先列のヘッダー中心で離す」まで。移動の可否
     * （移動不可列・固定領域の制約）とドロップ位置の解釈（左右どちらへ挿入されるか）はライブラリの
     * 挙動に委ね、操作は結果を返さない。並び替えの結果は検証部品（{@code gridColumnHeaders} 等）で
     * 検証する。
     *
     * @param colId 移動する列の識別子
     * @param targetColId ドロップ先の列の識別子
     */
    void moveColumn(String colId, String targetColId);

    /**
     * グリッド全体のルート要素。
     *
     * @return ルート要素
     */
    Locator root();

    /**
     * 行（縦）スクロールのコンテナ（{@code scrollTop} を操作する要素）。
     *
     * @return 縦スクロールコンテナ
     */
    Locator verticalViewport();

    /**
     * 列（横）スクロールのコンテナ（{@code scrollLeft} を操作する要素）。
     *
     * @return 横スクロールコンテナ
     */
    Locator horizontalViewport();

    /**
     * 横スクロール領域の列ヘッダーを視覚的に切り抜くコンテナ。この要素の矩形が「列が完全に
     * 見えているか」の判定基準（ヘッダー窓）になる。
     *
     * @return 列ヘッダーの可視窓
     */
    Locator scrollableHeaderArea();

    /**
     * 横スクロール領域に現在描画されている列ヘッダーセル群（仮想化により画面外の列は含まれない）。
     *
     * @return 描画中の列ヘッダーセル群
     */
    Locator scrollableHeaderCells();

    /**
     * 固定（ピン留め）領域の列ヘッダーセル群。固定列が無いグリッドでは 0 件に一致する。
     *
     * @return 固定列のヘッダーセル群
     */
    Locator pinnedHeaderCells();

    /**
     * ヘッダーセル内の表示名要素（テキスト検証・ハイライトの対象）。表示名を持たないセル
     * （選択チェックボックス列等）では 0 件に一致してよい。
     *
     * @param headerCell 対象のヘッダーセル
     * @return 表示名要素
     */
    Locator headerLabel(Locator headerCell);

    /**
     * 指定列のヘッダーセル。仮想化で描画されていなければ 0 件に一致する（待たない）。
     *
     * @param colId 列の識別子
     * @return ヘッダーセル
     */
    Locator headerCell(String colId);

    /**
     * 表示行 {@code rowIndex}・列 {@code colId} のセル。
     *
     * @param rowIndex 0 起点の表示行インデックス
     * @param colId 列の識別子
     * @return セル
     */
    Locator cell(int rowIndex, String colId);

    /**
     * DOM に描画されている行の数。
     *
     * <p>
     * 行仮想化グリッドでは画面外の行が DOM に存在しないため、全行が描画されている状態
     * （表示件数の範囲に収まるデータ量）での利用を前提とする。
     *
     * @return 描画されている行数
     */
    int rowCount();
}
