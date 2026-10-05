package testgen.e2e.pages;

import com.microsoft.playwright.Download;
import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
import java.util.List;
import testgen.e2e.support.evidence.E2eAgGridAdapter;
import testgen.e2e.support.evidence.E2eGridAdapter;
import testgen.e2e.support.evidence.E2eRawDom;

/**
 * ユーザマスタ管理画面の Page Object。
 *
 * <p>
 * 共通ヘッダー・画面タイトルは {@link CommonHeaderPage} が担う。ユーザマスタテーブル（ag-grid）の構造は {@link #grid()}
 * が返すアダプタ経由で検証部品へ渡す（列幅・列順のドラッグ操作もアダプタの責務）。
 */
public class UserMasterPage extends AppPage {

    /** ユーザマスタ管理画面のパス。 */
    public static final String PATH = "/master/users";

    /** ユーザマスタ管理画面の画面タイトル。 */
    public static final String SCREEN_TITLE = "ユーザマスタ管理画面";

    /** 列識別子: ユーザID。 */
    public static final String COL_USER_ID = "userId";

    /** 列識別子: ユーザ名。 */
    public static final String COL_USER_NAME = "userName";

    /** 列識別子: メールアドレス。 */
    public static final String COL_EMAIL = "email";

    /** 列識別子: 部署ID。 */
    public static final String COL_DEPARTMENT_ID = "departmentId";

    /** 列識別子: ロール。 */
    public static final String COL_ROLE = "role";

    /** 既定の列順（左から5列の列ヘッダー）。 */
    public static final List<String> DEFAULT_HEADERS =
            List.of("ユーザID", "ユーザ名", "メールアドレス", "部署ID", "ロール");

    /** ページャの位置: テーブル上部。 */
    public static final int PAGER_TOP = 0;

    /** ページャの位置: テーブル下部。 */
    public static final int PAGER_BOTTOM = 1;

    /**
     * @param page 操作対象のページ
     * @param baseUrl 起動中アプリのベース URL
     */
    public UserMasterPage(Page page, String baseUrl) {
        super(page, baseUrl);
    }

    /** ユーザマスタ管理画面を開く。 */
    public void open() {
        navigate(PATH);
        page.waitForLoadState();
    }

    /**
     * ユーザマスタ管理画面の URL。
     *
     * @return ユーザマスタ管理画面の絶対 URL
     */
    public String url() {
        return url(PATH);
    }

    // ---- ツールバー・エラー表示 ----

    /**
     * 「CSVアップロード」ボタン（ファイル選択ダイアログを開く。不在検証の文脈アンカーにも用いる）。
     *
     * @return CSVアップロードボタンのロケータ
     */
    public Locator csvUploadButton() {
        return page.locator("#csvUploadButton");
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
     * 「表示ページ」セレクトボックス。
     *
     * @param position {@link #PAGER_TOP} または {@link #PAGER_BOTTOM}
     * @return 表示ページセレクトボックスのロケータ
     */
    public Locator pageJumpSelect(int position) {
        return pager(position).locator("[data-page-jump]");
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
     * ページネーションのボタン。
     *
     * @param position {@link #PAGER_TOP} または {@link #PAGER_BOTTOM}
     * @param label ボタンの表示（«・‹・ページ番号・›・»）
     * @return ボタンのロケータ
     */
    public Locator pagerButton(int position, String label) {
        return pagerNums(position).locator(".pg-btn:text-is('" + label + "')");
    }

    /**
     * ページネーションの強調表示中（現在ページ）のボタン。
     *
     * @param position {@link #PAGER_TOP} または {@link #PAGER_BOTTOM}
     * @return 強調表示中のボタンのロケータ
     */
    public Locator activePagerButton(int position) {
        return pagerNums(position).locator(".pg-btn.active");
    }

    // ---- ユーザマスタテーブル（ag-grid） ----

    /**
     * ユーザマスタテーブルの構造アダプタ。
     *
     * @return グリッドアダプタ
     */
    public E2eGridAdapter grid() {
        return new E2eAgGridAdapter(page, "#userGrid");
    }

    /**
     * ページ送り後のユーザマスタテーブルの構造アダプタ。
     *
     * <p>
     * ag-grid はページネーション時も {@code row-index} を全件通しの絶対値で振るため、2ページ目以降は
     * 表示先頭行の絶対インデックスをオフセットとして与える（表示件数10件の2ページ目なら 10）。
     *
     * @param rowIndexOffset 表示中のページの先頭行の絶対行インデックス
     * @return グリッドアダプタ
     */
    public E2eGridAdapter grid(int rowIndexOffset) {
        return new E2eAgGridAdapter(page, "#userGrid", rowIndexOffset);
    }

    /**
     * テーブル本体に描画されているデータ行（行仮想化のため画面外の行は含まれない）。
     *
     * @return データ行のロケータ
     */
    public Locator dataRows() {
        return page.locator("#userGrid .ag-center-cols-container .ag-row");
    }

    /**
     * データ0件のときにテーブル本体へ表示される案内。
     *
     * @return 「該当するデータがありません」のロケータ
     */
    public Locator noRowsOverlay() {
        return page.locator("#userGrid .ag-overlay-no-rows-wrapper");
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
     * 「表示ページ」を選択する（0 起点のページ番号）。
     *
     * @param position {@link #PAGER_TOP} または {@link #PAGER_BOTTOM}
     * @param pageIndex 0 起点のページ番号
     */
    public void selectPage(int position, int pageIndex) {
        pageJumpSelect(position).selectOption(String.valueOf(pageIndex));
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
     * CSVファイルを指定しないままアップロードを実行する合成操作（仕様書 4-2 の「開発者ツール相当の 合成操作で、CSVファイルを指定しないままアップロードを実行する」に対応）。
     *
     * <p>
     * 画面のボタンはファイル選択ダイアログを開き、ファイルが選択されたときだけフォームを送信するため、 未指定のままの送信は画面操作では起こせない。フォームの送信を直接発火する。
     */
    @E2eRawDom("22_ユーザマスタ管理 4-2 CSVファイルを指定しないままアップロードを実行する合成操作")
    public void submitUploadWithoutFile() {
        page.evaluate("() => document.getElementById('csvUploadForm').submit()");
        page.waitForLoadState();
    }

    /**
     * ユーザマスタテーブルの横スクロール領域の幅（ピクセル）。
     *
     * <p>
     * 仕様書 1-2 の「各列の列幅はテーブルの横幅を5等分した幅である」の期待値を、環境で定まるテーブルの 横幅から算出するために読み取る（列幅そのものの実測は検証部品が行う）。
     *
     * @return テーブルの横幅（ピクセル）
     */
    @E2eRawDom("22_ユーザマスタ管理 1-2 各列の列幅はテーブルの横幅を5等分した幅である（期待値の算出元）")
    public int gridBodyWidthPx() {
        Object width = page.locator("#userGrid .ag-body-viewport").first()
                .evaluate("el => Math.round(el.getBoundingClientRect().width)");
        return ((Number) width).intValue();
    }

    /**
     * ブラウザが保持するセッション Cookie を削除する合成操作（仕様書 6-2・6-3 に対応）。
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
