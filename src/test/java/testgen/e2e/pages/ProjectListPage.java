package testgen.e2e.pages;

import com.microsoft.playwright.Download;
import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.options.LoadState;
import java.util.List;
import testgen.e2e.support.evidence.E2eAgGridAdapter;
import testgen.e2e.support.evidence.E2eGridAdapter;
import testgen.e2e.support.evidence.E2eRawDom;

/**
 * 案件情報一覧画面の Page Object。
 *
 * <p>
 * 共通ヘッダー・画面タイトルは {@link CommonHeaderPage} が担う。案件情報テーブル（ag-grid）の構造は {@link #grid()}
 * が返すアダプタ経由で検証部品へ渡す（列幅・列順のドラッグ操作もアダプタの責務）。
 */
public class ProjectListPage extends AppPage {

    /** 案件情報一覧画面のパス。 */
    public static final String PATH = "/projects";

    /** 案件情報一覧画面の画面タイトル。 */
    public static final String SCREEN_TITLE = "案件情報一覧画面";

    /** 案件検索APIのパス（検索の完了待ちに用いる）。 */
    public static final String SEARCH_API = "/api/projects/search";

    /** 列識別子: 管理コード（ID）。 */
    public static final String COL_CODE = "managementCode";

    /** 列識別子: 件名。 */
    public static final String COL_TITLE = "title";

    /** 列識別子: ステータス。 */
    public static final String COL_STATUS = "status";

    /** 列識別子: 登録者。 */
    public static final String COL_CREATED_BY = "createdBy";

    /** 列識別子: 登録部署。 */
    public static final String COL_CREATED_DEPARTMENT = "createdDepartment";

    /** 列識別子: 取引先。 */
    public static final String COL_CLIENT = "clientName";

    /** 列識別子: 商流。 */
    public static final String COL_FLOW = "commercialFlow";

    /** 列識別子: 案件概要。 */
    public static final String COL_SUMMARY = "summary";

    /** 列識別子: 開始日。 */
    public static final String COL_START_DATE = "startDate";

    /** 列識別子: 終了日。 */
    public static final String COL_END_DATE = "endDate";

    /** 列識別子: スキル。 */
    public static final String COL_SKILL = "skill";

    /** 列識別子: 工程。 */
    public static final String COL_PROCESS = "process";

    /** 列識別子: 人数。 */
    public static final String COL_HEADCOUNT = "headcount";

    /** 列識別子: 残数。 */
    public static final String COL_REMAINING = "remainingCount";

    /** 列識別子: BP募集(要否)。 */
    public static final String COL_BP = "bpRecruitment";

    /** 列識別子: BP募集(詳細)。 */
    public static final String COL_BP_DETAIL = "bpRecruitmentDetail";

    /** 列識別子: 作業場所。 */
    public static final String COL_WORK_LOCATION = "workLocation";

    /** 列識別子: 見込単価。 */
    public static final String COL_PRICE = "estimatedUnitPrice";

    /** 列識別子: 契約種別。 */
    public static final String COL_CONTRACT_TYPE = "contractType";

    /** 列識別子: 備考。 */
    public static final String COL_NOTE = "note";

    /** 列識別子: 登録日時。 */
    public static final String COL_CREATED_AT = "createdAt";

    /** 列識別子: 更新者。 */
    public static final String COL_UPDATED_BY = "updatedBy";

    /** 列識別子: 更新部署。 */
    public static final String COL_UPDATED_DEPARTMENT = "updatedDepartment";

    /** 列識別子: 更新日時。 */
    public static final String COL_UPDATED_AT = "updatedAt";

    /** 既定の列順（左から24列の列ヘッダー）。 */
    public static final List<String> DEFAULT_HEADERS = List.of("ID", "件名", "ステータス", "登録者", "登録部署",
            "取引先", "商流", "案件概要", "開始日", "終了日", "スキル", "工程", "人数", "残数", "BP募集(要否)", "BP募集(詳細)",
            "作業場所", "見込単価", "契約種別", "備考", "登録日時", "更新者", "更新部署", "更新日時");

    /** 入力欄ID: 検索キーワード。 */
    public static final String KEYWORD = "keyword";

    /** 入力欄ID: 登録者。 */
    public static final String REGISTRANT = "registrant";

    /** 入力欄ID: 登録部署。 */
    public static final String REGISTRANT_DEPARTMENT = "registrantDepartment";

    /** 入力欄ID: 取引先。 */
    public static final String CLIENT = "client";

    /** 入力欄ID: 開始日(自)。 */
    public static final String START_DATE_FROM = "startDateFrom";

    /** 入力欄ID: 開始日(至)。 */
    public static final String START_DATE_TO = "startDateTo";

    /** 入力欄ID: 終了日(自)。 */
    public static final String END_DATE_FROM = "endDateFrom";

    /** 入力欄ID: 終了日(至)。 */
    public static final String END_DATE_TO = "endDateTo";

    /** 入力欄ID: 登録日(自)。 */
    public static final String REGISTERED_DATE_FROM = "registeredDateFrom";

    /** 入力欄ID: 登録日(至)。 */
    public static final String REGISTERED_DATE_TO = "registeredDateTo";

    /** チェックボックスID: キーワード検索対象「件名」。 */
    public static final String KW_TITLE = "kwTitle";

    /** チェックボックスID: キーワード検索対象「スキル」。 */
    public static final String KW_SKILL = "kwSkill";

    /** チェックボックスID: キーワード検索対象「商流」。 */
    public static final String KW_FLOW = "kwFlow";

    /** チェックボックスID: キーワード検索対象「作業場所」。 */
    public static final String KW_LOCATION = "kwLoc";

    /** チェックボックスID: キーワード検索対象「案件概要」。 */
    public static final String KW_SUMMARY = "kwSummary";

    /** チェックボックスID: キーワード検索対象「備考」。 */
    public static final String KW_NOTE = "kwNote";

    /** チェックボックスID: ステータス「オープン」。 */
    public static final String ST_OPEN = "stOpen";

    /** チェックボックスID: ステータス「交渉中」。 */
    public static final String ST_NEGOTIATING = "stNego";

    /** チェックボックスID: ステータス「クローズ」。 */
    public static final String ST_CLOSED = "stClosed";

    /** チェックボックスID: BPが必要な案件のみ。 */
    public static final String BP_ONLY = "bpOnly";

    /** ページャの位置: テーブル上部。 */
    public static final int PAGER_TOP = 0;

    /** ページャの位置: テーブル下部。 */
    public static final int PAGER_BOTTOM = 1;

    /** ローカルストレージのキー: 列レイアウト。 */
    private static final String LAYOUT_KEY = "projectList.columnState";

    /** ローカルストレージのキー: 検索条件。 */
    private static final String CONDITION_KEY = "projectList.condition";

    /**
     * @param page 操作対象のページ
     * @param baseUrl 起動中アプリのベース URL
     */
    public ProjectListPage(Page page, String baseUrl) {
        super(page, baseUrl);
    }

    /** 案件情報一覧画面を開き、一覧の取得（検索API）が完了するまで待つ。 */
    public void open() {
        page.waitForResponse(response -> response.url().contains(SEARCH_API), () -> navigate(PATH));
        waitForListLoaded();
    }

    /** 一覧の取得（検索API）と描画の完了を待つ。 */
    public void waitForListLoaded() {
        page.waitForLoadState(LoadState.NETWORKIDLE);
    }

    /**
     * 案件情報一覧画面の URL。
     *
     * @return 案件情報一覧画面の絶対 URL
     */
    public String url() {
        return url(PATH);
    }

    // ---- 案件検索アコーディオン・検索フォーム ----

    /**
     * 「案件検索」アコーディオンの見出し（クリックで開閉する。不在検証の文脈アンカーにも用いる）。
     *
     * @return アコーディオン見出しのロケータ
     */
    public Locator searchAccordionHead() {
        return page.locator("#searchToggle");
    }

    /**
     * 「案件検索」アコーディオンの本体（閉じているときは CSS で非表示になる）。
     *
     * @return アコーディオン本体のロケータ
     */
    public Locator searchPanelBody() {
        return page.locator("#searchPanel .search-body");
    }

    /**
     * 表示されている検索フォーム本体（CSS 非表示を可視フィルタで受ける。不在検証に用いる）。
     *
     * @return 表示中の検索フォーム本体のロケータ
     */
    public Locator visibleSearchPanelBody() {
        return page.locator("#searchPanel .search-body:visible");
    }

    /**
     * 検索フォームの入力欄・チェックボックス。
     *
     * @param inputId 入力欄の id
     * @return 入力欄のロケータ
     */
    public Locator input(String inputId) {
        return page.locator("#" + inputId);
    }

    /**
     * 「検索」ボタン。
     *
     * @return 検索ボタンのロケータ
     */
    public Locator searchButton() {
        return page.locator("#searchButton");
    }

    /**
     * 指定した入力欄の直下のエラーメッセージ表示欄（メッセージが無いときは空要素として存在する）。
     *
     * @param inputId 入力欄の id
     * @return エラーメッセージ欄のロケータ
     */
    public Locator fieldError(String inputId) {
        return page.locator("[data-error-for='" + inputId + "']");
    }

    /**
     * 指定した入力欄の直下の、メッセージが表示されているエラー欄（消去の遷移検証に用いる）。
     *
     * @param inputId 入力欄の id
     * @return メッセージ表示中のエラー欄のロケータ
     */
    public Locator filledFieldError(String inputId) {
        return page.locator("[data-error-for='" + inputId + "']:not(:empty)");
    }

    /**
     * メッセージが表示されている全てのエラー欄（「他の項目にはエラーが出ない」の検証に用いる）。
     *
     * @return メッセージ表示中のエラー欄のロケータ
     */
    public Locator filledFieldErrors() {
        return page.locator("[data-error-for]:not(:empty)");
    }

    // ---- ツールバー ----

    /**
     * 「テーブル初期化」ボタン。
     *
     * @return テーブル初期化ボタンのロケータ
     */
    public Locator resetLayoutButton() {
        return page.locator("#resetLayoutButton");
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
     * テーブル上部の「＋ 新規登録」ボタン。
     *
     * @return 新規登録ボタンのロケータ
     */
    public Locator newProjectButton() {
        return page.locator(".table-toolbar a");
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
     * 取得データ件数の表示（件数部分。上部・下部のページャに同じものが出る）。
     *
     * @return 取得データ件数のロケータ（上部ページャ）
     */
    public Locator dataCount() {
        return page.locator("[data-data-count]").first();
    }

    /**
     * 取得データ件数の実測値。
     *
     * @return 取得データ件数（文字列）
     */
    @E2eRawDom("12_案件情報登録・13_案件情報更新 【機械検証】/projects の取得データ件数")
    public String dataCountText() {
        return dataCount().textContent();
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

    // ---- 案件情報テーブル（ag-grid） ----

    /**
     * 案件情報の一覧グリッド（ag-grid）の構造アダプタ。
     *
     * @return グリッドアダプタ
     */
    public E2eGridAdapter grid() {
        return new E2eAgGridAdapter(page, "#projectGrid");
    }

    /**
     * ページ送り後の案件情報テーブルの構造アダプタ。
     *
     * <p>
     * ag-grid はページネーション時も {@code row-index} を全件通しの絶対値で振るため、2ページ目以降は
     * 表示先頭行の絶対インデックスをオフセットとして与える（表示件数10件の2ページ目なら 10）。
     *
     * @param rowIndexOffset 表示中のページの先頭行の絶対行インデックス
     * @return グリッドアダプタ
     */
    public E2eGridAdapter grid(int rowIndexOffset) {
        return new E2eAgGridAdapter(page, "#projectGrid", rowIndexOffset);
    }

    /**
     * テーブル本体に描画されているデータ行（行仮想化のため画面外の行は含まれない）。
     *
     * @return データ行のロケータ
     */
    public Locator dataRows() {
        return page.locator("#projectGrid .ag-center-cols-container .ag-row");
    }

    /**
     * データ0件のときにテーブル本体へ表示される案内。
     *
     * @return 「該当するデータがありません」のロケータ
     */
    public Locator noRowsOverlay() {
        return page.locator("#projectGrid .ag-overlay-no-rows-wrapper");
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

    /** 「案件検索」アコーディオンの見出しを押下する（開閉が切り替わる）。 */
    public void toggleSearchAccordion() {
        searchAccordionHead().click();
    }

    /**
     * 検索条件のテキスト・日付欄へ入力する。
     *
     * @param inputId 入力欄の id
     * @param value 入力値
     */
    public void fill(String inputId, String value) {
        input(inputId).fill(value);
    }

    /**
     * 検索条件のチェックボックスをオンにする。
     *
     * @param checkboxId チェックボックスの id
     */
    public void check(String checkboxId) {
        input(checkboxId).check();
    }

    /**
     * 検索条件のチェックボックスをオフにする。
     *
     * @param checkboxId チェックボックスの id
     */
    public void uncheck(String checkboxId) {
        input(checkboxId).uncheck();
    }

    /** 「検索」ボタンを押下し、案件検索APIの応答を待つ。 */
    public void pressSearch() {
        page.waitForResponse(response -> response.url().contains(SEARCH_API),
                () -> searchButton().click());
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

    /** 「テーブル初期化」を押下する。 */
    public void pressResetLayout() {
        resetLayoutButton().click();
    }

    /**
     * 「CSVダウンロード」を押下してダウンロードを受け取る。
     *
     * @return ダウンロード
     */
    public Download pressCsvDownload() {
        return page.waitForDownload(() -> csvDownloadButton().click());
    }

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
     * テーブルの行をクリックする（案件情報更新画面へ遷移する）。
     *
     * @param rowIndex 0 起点の表示行インデックス
     */
    public void clickRow(int rowIndex) {
        grid().cell(rowIndex, COL_TITLE).click();
        page.waitForLoadState();
    }

    // ---- ローカルストレージ（画面に出ない保存内容の【機械検証】用） ----

    /**
     * ローカルストレージに保存された列レイアウトの値（未保存なら {@code null} 文字列）。
     *
     * @return 保存された列レイアウトの JSON 文字列
     */
    @E2eRawDom("11_案件情報一覧 2-9・2-10 【機械検証】ローカルストレージに保存された列レイアウトの値")
    public String savedColumnLayout() {
        return String
                .valueOf(page.evaluate("() => String(localStorage.getItem('" + LAYOUT_KEY + "'))"));
    }

    /**
     * ローカルストレージに保存された検索条件の値（未保存なら {@code null} 文字列）。
     *
     * @return 保存された検索条件の JSON 文字列
     */
    @E2eRawDom("11_案件情報一覧 2-9 【機械検証】ローカルストレージに保存された検索条件の値")
    public String savedCondition() {
        return String.valueOf(
                page.evaluate("() => String(localStorage.getItem('" + CONDITION_KEY + "'))"));
    }

    /**
     * ローカルストレージの保存内容（検索条件・列レイアウト）を消去する。
     *
     * <p>
     * 全ケースの前提条件「ローカルストレージに検索条件・列レイアウトは保存されていない」を作るための 準備であり、仕様書の操作手順ではない（ログイン直後の一覧表示で検索条件が保存されるため、
     * 各ケースの手順1に先立って消去する）。
     */
    @E2eRawDom("11_案件情報一覧 全ケース 前提条件「ローカルストレージに検索条件・列レイアウトは保存されていない」")
    public void clearStoredState() {
        page.evaluate("() => localStorage.clear()");
    }

    /**
     * ブラウザが保持するセッション Cookie を削除する合成操作（仕様書 7-1 に対応）。
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
