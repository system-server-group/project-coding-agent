package testgen.e2e.pages;

import com.microsoft.playwright.Download;
import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import testgen.e2e.support.evidence.E2eAgGridAdapter;
import testgen.e2e.support.evidence.E2eGridAdapter;
import testgen.e2e.support.evidence.E2eRawDom;

/**
 * 会社マスタ管理画面の Page Object。
 *
 * <p>
 * 共通ヘッダー・画面タイトルは {@link CommonHeaderPage} が担う。会社マスタテーブル（ag-grid）の構造は {@link #grid()}
 * が返すアダプタ経由で検証部品へ渡す（列幅・列順のドラッグ操作もアダプタの責務）。
 */
public class CompanyMasterPage extends AppPage {

    /** 会社マスタ管理画面のパス。 */
    public static final String PATH = "/master/companies";

    /** CSV ダウンロード・アップロードのパス。 */
    public static final String CSV_PATH = PATH + "/csv";

    /** 会社マスタ管理画面の画面タイトル。 */
    public static final String SCREEN_TITLE = "会社マスタ管理画面";

    /** 列識別子: 会社ID。 */
    public static final String COL_ID = "companyId";

    /** 列識別子: 会社名。 */
    public static final String COL_NAME = "companyName";

    /** 既定の列順（左から2列の列ヘッダー）。 */
    public static final List<String> DEFAULT_HEADERS = List.of("会社ID", "会社名");

    /** ページャの位置: テーブル上部。 */
    public static final int PAGER_TOP = 0;

    /** ページャの位置: テーブル下部。 */
    public static final int PAGER_BOTTOM = 1;

    /** 会社マスタテーブルのセレクタ。 */
    private static final String GRID = "#companyGrid";

    /** 経路外のアップロード要求（10-3）のために組み立てるフォームの id。 */
    private static final String OUT_OF_ROUTE_FORM_ID = "e2eOutOfRouteUpload";

    /**
     * 経路外のアップロード要求（10-3）のフォームを組み立てるスクリプト。画面の CSRF トークン（{@code meta[name=_csrf]}）を hidden
     * 項目に、ファイル項目を {@code csvFile} として持つ multipart のフォームを追加する。
     */
    private static final String BUILD_OUT_OF_ROUTE_FORM = """
            (arg) => {
              const meta = document.querySelector("meta[name='_csrf']");
              const form = document.createElement('form');
              form.id = arg.id;
              form.method = 'post';
              form.enctype = 'multipart/form-data';
              form.action = arg.action;
              form.style.display = 'none';
              const csrf = document.createElement('input');
              csrf.type = 'hidden';
              csrf.name = '_csrf';
              csrf.value = meta.getAttribute('content');
              const file = document.createElement('input');
              file.type = 'file';
              file.name = 'csvFile';
              form.append(csrf, file);
              document.body.appendChild(form);
            }
            """;

    /**
     * @param page 操作対象のページ
     * @param baseUrl 起動中アプリのベース URL
     */
    public CompanyMasterPage(Page page, String baseUrl) {
        super(page, baseUrl);
    }

    /** 会社マスタ管理画面を開く。 */
    public void open() {
        navigate(PATH);
        page.waitForLoadState();
    }

    /** CSV ダウンロードの URL を直接開く（権限の無いユーザの直接アクセスの再現に用いる）。 */
    public void openCsvUrl() {
        navigate(CSV_PATH);
        page.waitForLoadState();
    }

    /**
     * 会社マスタ管理画面の URL。
     *
     * @return 会社マスタ管理画面の絶対 URL
     */
    public String url() {
        return url(PATH);
    }

    // ---- ツールバー・エラー表示 ----

    /**
     * ツールバー（「CSVアップロード」「CSVダウンロード」ボタンの行。エラー表示の直下にあり、エラー表示が 「無いこと」の検証で文脈アンカーに用いる）。
     *
     * @return ツールバーのロケータ
     */
    public Locator toolbar() {
        return page.locator(".table-toolbar");
    }

    /**
     * 「CSVアップロード」ボタン（ファイル選択ダイアログを開く）。
     *
     * @return CSVアップロードボタンのロケータ
     */
    public Locator csvUploadButton() {
        return page.locator("#csvUploadButton");
    }

    /**
     * ラベルに指定の文字列を含む「CSVアップロード」ボタン（ラベルの表示の検証に用いる）。
     *
     * @param label ボタンのラベル
     * @return ラベルが一致する CSVアップロードボタンのロケータ
     */
    public Locator csvUploadButtonLabeled(String label) {
        return csvUploadButton().filter(new Locator.FilterOptions().setHasText(label));
    }

    /**
     * 「CSVダウンロード」ボタン。
     *
     * @return CSVダウンロードボタンのロケータ
     */
    public Locator csvDownloadButton() {
        return page.locator("#csvDownloadButton");
    }

    /**
     * ラベルに指定の文字列を含む「CSVダウンロード」ボタン（ラベルの表示の検証に用いる）。
     *
     * @param label ボタンのラベル
     * @return ラベルが一致する CSVダウンロードボタンのロケータ
     */
    public Locator csvDownloadButtonLabeled(String label) {
        return csvDownloadButton().filter(new Locator.FilterOptions().setHasText(label));
    }

    /**
     * エラー表示の領域（エラーが無いときは要素ごと描画されない）。
     *
     * @return エラー領域のロケータ
     */
    public Locator errorArea() {
        return page.locator("#errorArea");
    }

    /**
     * エラータイトル。
     *
     * @return エラータイトルのロケータ
     */
    public Locator errorTitle() {
        return page.locator("#errorArea .title");
    }

    /**
     * エラー内容（複数件）。
     *
     * @return エラー内容のロケータ
     */
    public Locator errorMessages() {
        return page.locator("#errorArea li");
    }

    /**
     * 指定の文言のエラー内容（再アップロードで消えることの検証に用いる）。
     *
     * @param message エラー内容の文言
     * @return 文言が一致するエラー内容のロケータ
     */
    public Locator errorMessage(String message) {
        return page.locator("#errorArea li:text-is('" + message + "')");
    }

    // ---- ページャ（テーブル上部・下部の2箇所） ----

    /**
     * ページャ（テーブル上部・下部）。
     *
     * @param position {@link #PAGER_TOP} または {@link #PAGER_BOTTOM}
     * @return ページャのロケータ
     */
    public Locator pager(int position) {
        return page.locator("[data-pager]").nth(position);
    }

    /**
     * 取得データ件数の表示（「取得データ件数: {件数}件」の全体）。
     *
     * @param position {@link #PAGER_TOP} または {@link #PAGER_BOTTOM}
     * @return 取得データ件数表示のロケータ
     */
    public Locator dataCountLabel(int position) {
        return pager(position).locator("span").first();
    }

    /**
     * 「表示件数」セレクトボックス。
     *
     * @param position {@link #PAGER_TOP} または {@link #PAGER_BOTTOM}
     * @return 表示件数セレクトボックスのロケータ
     */
    public Locator pageSizeSelect(int position) {
        return pager(position).locator("[data-page-size]");
    }

    /**
     * 「表示件数」セレクトボックスのラベル。
     *
     * @param position {@link #PAGER_TOP} または {@link #PAGER_BOTTOM}
     * @return ラベルのロケータ
     */
    public Locator pageSizeLabel(int position) {
        return pager(position).locator("label")
                .filter(new Locator.FilterOptions().setHas(page.locator("[data-page-size]")))
                .locator("span");
    }

    /**
     * 「表示ページ」セレクトボックス。
     *
     * @param position {@link #PAGER_TOP} または {@link #PAGER_BOTTOM}
     * @return 表示ページセレクトボックスのロケータ
     */
    public Locator pageJumpSelect(int position) {
        return pager(position).locator("[data-page-jump]");
    }

    /**
     * 「表示ページ」セレクトボックスのラベル。
     *
     * @param position {@link #PAGER_TOP} または {@link #PAGER_BOTTOM}
     * @return ラベルのロケータ
     */
    public Locator pageJumpLabel(int position) {
        return pager(position).locator("label")
                .filter(new Locator.FilterOptions().setHas(page.locator("[data-page-jump]")))
                .locator("span");
    }

    /**
     * ページネーション（« ‹ ページ番号 › » のボタン群）。
     *
     * @param position {@link #PAGER_TOP} または {@link #PAGER_BOTTOM}
     * @return ページネーションのロケータ
     */
    public Locator pagerNums(int position) {
        return pager(position).locator("[data-pager-nums]");
    }

    /**
     * ページネーションの全ボタン（表示順）。
     *
     * @param position {@link #PAGER_TOP} または {@link #PAGER_BOTTOM}
     * @return ボタン群のロケータ
     */
    public Locator pagerButtons(int position) {
        return pagerNums(position).locator(".pg-btn");
    }

    /**
     * ページネーションのボタン。
     *
     * @param position {@link #PAGER_TOP} または {@link #PAGER_BOTTOM}
     * @param label ボタンの表示（«・‹・ページ番号・›・»）
     * @return ボタンのロケータ
     */
    public Locator pagerButton(int position, String label) {
        return pagerNums(position).locator(".pg-btn:text-is('" + label + "')");
    }

    // ---- 会社マスタテーブル（ag-grid） ----

    /**
     * 会社マスタテーブルの構造アダプタ。
     *
     * @return グリッドアダプタ
     */
    public E2eGridAdapter grid() {
        return new E2eAgGridAdapter(page, GRID);
    }

    /**
     * ページ送り後の会社マスタテーブルの構造アダプタ。
     *
     * <p>
     * ag-grid はページネーション時も {@code row-index} を全件通しの絶対値で振るため、2ページ目以降は
     * 表示先頭行の絶対インデックスをオフセットとして与える（表示件数10件の2ページ目なら 10）。
     *
     * @param rowIndexOffset 表示中のページの先頭行の絶対行インデックス
     * @return グリッドアダプタ
     */
    public E2eGridAdapter grid(int rowIndexOffset) {
        return new E2eAgGridAdapter(page, GRID, rowIndexOffset);
    }

    /**
     * データ0件のときにテーブル本体へ表示される案内。
     *
     * @return 「該当するデータがありません」のロケータ
     */
    public Locator noRowsOverlay() {
        return page.locator(GRID + " .ag-overlay-no-rows-wrapper");
    }

    /**
     * テーブル内の横スクロールバー（スクロールが不要なときは要素ごと非表示）。
     *
     * @return 横スクロールバーのロケータ
     */
    public Locator horizontalScrollbar() {
        return page.locator(GRID + " .ag-body-horizontal-scroll");
    }

    /**
     * テーブル内の縦スクロールバー（スクロールが不要なときは要素ごと非表示）。
     *
     * @return 縦スクロールバーのロケータ
     */
    public Locator verticalScrollbar() {
        return page.locator(GRID + " .ag-body-vertical-scroll");
    }

    /**
     * 列ヘッダーの昇順ソート意匠（適用されていないときは CSS で非表示）。
     *
     * @param colId 列識別子
     * @return 昇順ソート意匠のロケータ
     */
    public Locator sortAscendingIcon(String colId) {
        return grid().headerCell(colId).locator(".ag-sort-ascending-icon");
    }

    /**
     * 列ヘッダーの降順ソート意匠（適用されていないときは CSS で非表示）。
     *
     * @param colId 列識別子
     * @return 降順ソート意匠のロケータ
     */
    public Locator sortDescendingIcon(String colId) {
        return grid().headerCell(colId).locator(".ag-sort-descending-icon");
    }

    /**
     * 列ヘッダーに表示されているソート意匠（昇順・降順のいずれか。不在検証に用いる）。
     *
     * @param colId 列識別子
     * @return 表示中のソート意匠のロケータ
     */
    public Locator visibleSortIcon(String colId) {
        return grid().headerCell(colId)
                .locator(".ag-sort-ascending-icon:visible, .ag-sort-descending-icon:visible");
    }

    // ---- 操作 ----

    /**
     * 列ヘッダーをダブルクリックしてソート状態を切り替える。
     *
     * @param colId 列識別子
     */
    public void doubleClickHeader(String colId) {
        E2eGridAdapter grid = grid();
        grid.headerLabel(grid.headerCell(colId)).dblclick();
    }

    /**
     * 「表示件数」を選択する（上部・下部の連動を確認するため位置を指定する）。
     *
     * @param position {@link #PAGER_TOP} または {@link #PAGER_BOTTOM}
     * @param value 選択する値（{@code 10}・{@code 20}・{@code 30}・{@code all}）
     */
    public void selectPageSize(int position, String value) {
        pageSizeSelect(position).selectOption(value);
    }

    /**
     * 「表示ページ」を選択する（ページ番号の選択肢を表示ラベルの先頭の番号で特定する）。
     *
     * @param position {@link #PAGER_TOP} または {@link #PAGER_BOTTOM}
     * @param pageNumber 1 起点のページ番号
     */
    public void selectPage(int position, int pageNumber) {
        pageJumpSelect(position).selectOption(String.valueOf(pageNumber - 1));
    }

    /**
     * ページネーションのボタンを押下する。
     *
     * @param position {@link #PAGER_TOP} または {@link #PAGER_BOTTOM}
     * @param label ボタンの表示
     */
    public void pressPagerButton(int position, String label) {
        pagerButton(position, label).click();
    }

    /**
     * 「CSVダウンロード」を押下してダウンロードを受け取る。
     *
     * @return ダウンロード
     */
    public Download pressCsvDownload() {
        return page.waitForDownload(() -> csvDownloadButton().click());
    }

    /** 「CSVダウンロード」を押下する（ダウンロードが発生しない場合の確認に用いる）。 */
    public void pressCsvDownloadWithoutWaiting() {
        csvDownloadButton().click();
    }

    /**
     * ファイルを指定しないまま CSVアップロードのフォームを送信する合成操作（仕様書 7-1 の「開発者ツール相当の 合成操作で、ファイルを指定しないまま
     * CSVアップロードのフォームを送信する」に対応）。
     *
     * <p>
     * 画面のボタンはファイル選択ダイアログを開き、ファイルが選択されたときだけフォームを送信するため、 未指定のままの送信は画面操作では起こせない。フォームの送信を直接発火する。
     */
    @E2eRawDom("21_会社マスタ管理 7-1 手順2 ファイルを指定しないまま CSVアップロードのフォームを送信する合成操作")
    public void submitUploadWithoutFile() {
        page.waitForNavigation(
                () -> page.evaluate("() => document.getElementById('csvUploadForm').submit()"));
    }

    /**
     * 画面が保持する CSRF トークンと CSV ファイルを付けて、CSV アップロードの要求（POST）を送信する合成操作 （仕様書 10-3 手順2 に対応）。
     *
     * <p>
     * 一般ユーザには会社マスタ管理画面とアップロードの操作部品が提供されないため、表示中の画面（共通レイアウト）の CSRF
     * トークン（{@code meta[name=_csrf]}）を用いて multipart のフォームを組み立て、指定のファイルを設定して送信する（経路外の要求。送信で画面遷移する）。
     *
     * @param csvFile 送信する CSV ファイル
     */
    @E2eRawDom("21_会社マスタ管理 10-3 手順2 CSRFトークンと CSV を付けてアップロードの要求（POST）を送信する合成操作")
    public void submitUploadOutOfRoute(Path csvFile) {
        page.evaluate(BUILD_OUT_OF_ROUTE_FORM,
                Map.of("id", OUT_OF_ROUTE_FORM_ID, "action", CSV_PATH));
        page.locator("#" + OUT_OF_ROUTE_FORM_ID + " input[type=file]").setInputFiles(csvFile);
        page.waitForNavigation(() -> page.evaluate("(id) => document.getElementById(id).submit()",
                OUT_OF_ROUTE_FORM_ID));
    }

    /**
     * 会社マスタテーブルの表示幅（ピクセル）。
     *
     * <p>
     * 仕様書 1-4 の【機械検証】「2列の幅の合計がテーブルの表示幅と一致する」の期待値の算出元、および 2-5 手順2
     * の「列の幅をテーブルの表示幅と同じ幅にする」操作の目標幅として読み取る（列幅そのものの実測は検証部品が行う）。
     *
     * @return テーブルの表示幅（ピクセル）
     */
    @E2eRawDom("21_会社マスタ管理 1-4【機械検証】の期待値の算出元・2-5 手順2 の操作の目標幅（テーブルの表示幅）")
    public int gridBodyWidthPx() {
        Object width = page.locator(GRID + " .ag-body-viewport").first()
                .evaluate("el => Math.round(el.getBoundingClientRect().width)");
        return ((Number) width).intValue();
    }

    /**
     * 列の現在の幅（ピクセル。列ヘッダーセルの描画幅を四捨五入した値で、検証部品 {@code gridColumnWidthIs} と同じ測り方）。
     *
     * <p>
     * 仕様書 2-3 の【機械検証】「手順1の時点より100px広い」と 3-13 手順3 の「列の幅が広がる」の判定の基準（操作前の幅）として読み取る。
     *
     * @param colId 列識別子
     * @return 列の幅（ピクセル）
     */
    @E2eRawDom("21_会社マスタ管理 2-3【機械検証】・3-13 手順3 の判定の基準（操作前の列幅）")
    public int columnWidthPx(String colId) {
        Object width = grid().headerCell(colId).first()
                .evaluate("el => Math.round(el.getBoundingClientRect().width)");
        return ((Number) width).intValue();
    }

    /**
     * ブラウザが保持するセッション Cookie を削除する合成操作（仕様書 9-1・9-2 に対応）。
     *
     * <p>
     * 効果は後続フレームに現れないため、呼び出し側は {@link #sessionCookieCount()} の実測を 【機械検証】として記録する。
     */
    public void clearSessionCookies() {
        page.context().clearCookies();
    }

    /**
     * ブラウザが保持するセッション Cookie の件数（合成操作の効果の記録に用いる）。
     *
     * @return セッション Cookie（{@code JSESSIONID}）の件数
     */
    public int sessionCookieCount() {
        return (int) page.context().cookies().stream()
                .filter(cookie -> "JSESSIONID".equals(cookie.name)).count();
    }
}
