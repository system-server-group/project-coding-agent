package testgen.e2e.tests;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.Import;
import testgen.e2e.ProjectE2eTest;
import testgen.e2e.pages.CommonHeaderPage;
import testgen.e2e.pages.ErrorPage;
import testgen.e2e.pages.LoginPage;
import testgen.e2e.pages.ProjectEditPage;
import testgen.e2e.pages.ProjectFormPage;
import testgen.e2e.pages.ProjectListPage;
import testgen.e2e.pages.ProjectRegisterPage;
import testgen.e2e.seams.ProjectSearchFaultSeamConfig;
import testgen.e2e.support.E2eFeature;
import testgen.e2e.support.evidence.EvidenceV2;

/**
 * 案件情報一覧画面（機能 {@code 11_案件情報一覧}）の E2E テスト。
 *
 * <p>
 * テストケース仕様書 {@code 11_案件情報一覧/11_案件情報一覧_テスト仕様書.xlsx} の全ケースを実装する。
 * 案件情報テーブル（ag-grid）の列順・列幅の変更はグリッドアダプタの操作 API を用い、サーバー内部の異常分岐には 障害シームを用いる。
 */
@EvidenceV2
@Import(ProjectSearchFaultSeamConfig.class)
@E2eFeature("11_案件情報一覧")
class ProjectListE2eTest extends ProjectE2eTest {

    /** 一般ユーザ SM0001（営業 太郎／営業部）。 */
    private static final String GENERAL_USER = "SM0001";

    /** ベースラインのユーザに共通の開発用ダミーパスワード。 */
    private static final String PASSWORD = "Passw0rd!";

    /** 案件情報5件のシード。 */
    private static final String SEED_PROJECT = "11_案件情報一覧/data/案件情報_シード.csv";

    /** 案件情報80件のシード（ページネーション確認用）。 */
    private static final String SEED_PAGINATION = "11_案件情報一覧/data/ページネーション_案件情報.csv";

    /** CSV 期待値正本: 既定検索（4件）。 */
    private static final String CSV_DEFAULT = "11_案件情報一覧/data/CSV期待_既定検索_管理コード降順.csv";

    /** CSV 期待値正本: 全件（5件）。 */
    private static final String CSV_ALL = "11_案件情報一覧/data/CSV期待_全件_管理コード降順.csv";

    /** CSV 期待値正本: 0件（ヘッダー行のみ）。 */
    private static final String CSV_EMPTY = "11_案件情報一覧/data/CSV期待_0件.csv";

    /** ダウンロードされる CSV のファイル名。 */
    private static final String CSV_FILE_NAME = "案件情報一覧.csv";

    /** 既定の検索条件（オープン・交渉中）で表示される行キー。 */
    private static final List<String> DEFAULT_KEYS = List.of("5", "4", "2", "1");

    /** 全ステータスを対象にしたときに表示される行キー。 */
    private static final List<String> ALL_KEYS = List.of("5", "4", "3", "2", "1");

    /** 「登録者」の列を「ステータス」の上へドロップした後の列順。 */
    private static final List<String> HEADERS_CREATED_BY_MOVED = List.of("ID", "件名", "登録者", "ステータス",
            "登録部署", "取引先", "商流", "案件概要", "開始日", "終了日", "スキル", "工程", "人数", "残数", "BP募集(要否)",
            "BP募集(詳細)", "作業場所", "見込単価", "契約種別", "備考", "登録日時", "更新者", "更新部署", "更新日時");

    /** 「ステータス」が「ID」の右側へ挿入された後の列順。 */
    private static final List<String> HEADERS_STATUS_SECOND = List.of("ID", "ステータス", "件名", "登録者",
            "登録部署", "取引先", "商流", "案件概要", "開始日", "終了日", "スキル", "工程", "人数", "残数", "BP募集(要否)",
            "BP募集(詳細)", "作業場所", "見込単価", "契約種別", "備考", "登録日時", "更新者", "更新部署", "更新日時");

    /** 最大文字数(100)の違反メッセージ。 */
    private static final String MAX_100 = "100文字以内で入力してください。";

    /** 最大文字数(50)の違反メッセージ。 */
    private static final String MAX_50 = "50文字以内で入力してください。";

    /** 最大文字数(200)の違反メッセージ。 */
    private static final String MAX_200 = "200文字以内で入力してください。";

    /** 使用文字の違反メッセージ。 */
    private static final String ILLEGAL_CHARACTER = "使用不可能な文字が含まれています。";

    /** 日付形式の違反メッセージ。 */
    private static final String INVALID_DATE = "無効な日付です。";

    /** データ0件のときにテーブル本体へ表示される案内。 */
    private static final String NO_ROWS = "該当するデータがありません";

    private LoginPage loginPage;
    private CommonHeaderPage commonHeader;
    private ProjectListPage listPage;
    private ProjectRegisterPage registerPage;
    private ProjectEditPage editPage;
    private ErrorPage errorPage;


    @BeforeEach
    void setUpPages() {
        loginPage = new LoginPage(page, baseUrl());
        commonHeader = new CommonHeaderPage(page, baseUrl());
        listPage = new ProjectListPage(page, baseUrl());
        registerPage = new ProjectRegisterPage(page, baseUrl());
        editPage = new ProjectEditPage(page, baseUrl());
        errorPage = new ErrorPage(page, baseUrl());
        loginPage.signIn(GENERAL_USER, PASSWORD, ProjectListPage.PATH);
        // 前提条件「ローカルストレージに検索条件・列レイアウトは保存されていない」を作る
        // （ログイン直後の一覧表示で検索条件が保存されるため、各ケースの手順1に先立って消去する）。
        listPage.clearStoredState();
    }

    // ---- テストデータ・共通手順 ----

    /** 案件情報5件（ID: 1〜5）を登録する。 */
    private void seedFive() {
        insertProjects(unitData(SEED_PROJECT));
    }

    /** 案件情報80件（ID: 1〜80、いずれもオープン）を登録する。 */
    private void seedPagination() {
        insertProjects(unitData(SEED_PAGINATION));
    }

    /** 管理コードの降順に並ぶ行キーの一覧を作る。 */
    private static List<String> descendingKeys(int from, int to) {
        List<String> keys = new ArrayList<>();
        for (int code = from; code >= to; code--) {
            keys.add(String.valueOf(code));
        }
        return keys;
    }

    /** 手順1「/projects を開く」。 */
    private void openList() {
        verify.opOpen("1. /projects を開く", () -> listPage.open());
    }

    /** 「案件検索」アコーディオンを押下して検索フォームを開く。 */
    private void openSearchForm(int stepNo) {
        verify.opPress(stepNo + ". 「案件検索」アコーディオンを押下して検索フォームを開く", listPage.searchAccordionHead(),
                () -> listPage.toggleSearchAccordion(), listPage.searchPanelBody());
    }

    /** 「検索」を押下する。 */
    private void pressSearch(int stepNo) {
        verify.opPress(stepNo + ". 「検索」を押下する", listPage.searchButton(),
                () -> listPage.pressSearch(), listPage.grid().root());
    }

    /** ステータスの「クローズ」にもチェックを入れて全ステータスを対象にする。 */
    private void includeClosed(int stepNo) {
        verify.op(stepNo + ". ステータスの「クローズ」にもチェックを入れて全ステータスを対象にする",
                () -> listPage.check(ProjectListPage.ST_CLOSED),
                listPage.input(ProjectListPage.ST_CLOSED));
    }

    /** 検索が実行されず、テーブルの表示が検索前の4件のまま変わらないことを確認する。 */
    private void tableUnchanged() {
        verify.gridRowsByKey("検索は実行されず、テーブルの表示は検索前の4件（ID: 5・4・2・1）のまま変わらない", listPage.grid(),
                ProjectListPage.COL_CODE, DEFAULT_KEYS);
    }

    /** 案件情報登録画面から「戻る」で案件情報一覧画面へ戻る。 */
    private void backToList() {
        registerPage.pressBackTop();
        page.waitForURL(listPage.url());
        listPage.waitForListLoaded();
    }

    /** 取得データ件数がテーブル上部に指定件数で表示されることを確認する。 */
    private void dataCountIs(int count) {
        verify.text("取得データ件数に「取得データ件数: " + count + "件」が表示される",
                listPage.dataCountLabel(ProjectListPage.PAGER_TOP), "取得データ件数: " + count + "件");
    }

    // ---- 1. 画面仕様・初期表示 ----

    @Test
    @DisplayName("No.1-1 案件情報一覧画面の初期表示（画面項目・既定の検索条件による一覧・取得データ件数・表示件数の初期値）")
    void case1x1() {
        seedFive();
        openList();

        verify.visible("画面タイトル「案件情報一覧画面」が表示される",
                commonHeader.screenTitleOf(ProjectListPage.SCREEN_TITLE));
        verify.visible("「案件検索」アコーディオンが表示される", listPage.searchAccordionHead());
        verify.absent("「案件検索」の検索フォームは初期は表示されない", listPage.searchAccordionHead(),
                listPage.visibleSearchPanelBody());
        verify.visible("「テーブル初期化」ボタンが表示される", listPage.resetLayoutButton());
        verify.visible("「CSVダウンロード」ボタンが表示される", listPage.csvDownloadButton());
        verify.visible("「新規登録」ボタンが表示される", listPage.newProjectButton());
        verify.gridRowsByKey(
                "ステータスが「オープン」または「交渉中」の4件（ID: 5・4・2・1）が管理コードの降順で表示される" + "（ID: 3「クローズ済み案件」は表示されない）",
                listPage.grid(), ProjectListPage.COL_CODE, DEFAULT_KEYS);
        verify.text("テーブル上部に「取得データ件数: 4件」が表示される",
                listPage.dataCountLabel(ProjectListPage.PAGER_TOP), "取得データ件数: 4件");
        verify.text("テーブル下部に「取得データ件数: 4件」が表示される",
                listPage.dataCountLabel(ProjectListPage.PAGER_BOTTOM), "取得データ件数: 4件");
        verify.value("表示件数セレクトボックスは「30件」が選択されている",
                listPage.pageSizeSelect(ProjectListPage.PAGER_TOP), "30");
        verify.visible("テーブル上部に表示ページセレクトボックスが表示される",
                listPage.pageJumpSelect(ProjectListPage.PAGER_TOP));
        verify.visible("テーブル上部にページネーションが表示される", listPage.pagerNums(ProjectListPage.PAGER_TOP));
        verify.visible("テーブル下部に表示ページセレクトボックスが表示される",
                listPage.pageJumpSelect(ProjectListPage.PAGER_BOTTOM));
        verify.visible("テーブル下部にページネーションが表示される", listPage.pagerNums(ProjectListPage.PAGER_BOTTOM));
    }

    @Test
    @DisplayName("No.1-2 案件検索フォームの初期表示（各検索条件の画面項目と初期値）")
    void case1x2() {
        seedFive();
        openList();
        openSearchForm(2);

        verify.value("「検索キーワード」が空欄である", listPage.input(ProjectListPage.KEYWORD), "");
        verify.checked("キーワード検索対象の「件名」がONである", listPage.input(ProjectListPage.KW_TITLE));
        verify.unchecked("キーワード検索対象の「スキル」がOFFである", listPage.input(ProjectListPage.KW_SKILL));
        verify.unchecked("キーワード検索対象の「商流」がOFFである", listPage.input(ProjectListPage.KW_FLOW));
        verify.unchecked("キーワード検索対象の「作業場所」がOFFである", listPage.input(ProjectListPage.KW_LOCATION));
        verify.unchecked("キーワード検索対象の「案件概要」がOFFである", listPage.input(ProjectListPage.KW_SUMMARY));
        verify.unchecked("キーワード検索対象の「備考」がOFFである", listPage.input(ProjectListPage.KW_NOTE));
        verify.checked("ステータスの「オープン」がONである", listPage.input(ProjectListPage.ST_OPEN));
        verify.checked("ステータスの「交渉中」がONである", listPage.input(ProjectListPage.ST_NEGOTIATING));
        verify.unchecked("ステータスの「クローズ」がOFFである", listPage.input(ProjectListPage.ST_CLOSED));
        verify.value("「開始日(自)」が空欄である", listPage.input(ProjectListPage.START_DATE_FROM), "");
        verify.value("「開始日(至)」が空欄である", listPage.input(ProjectListPage.START_DATE_TO), "");
        verify.value("「終了日(自)」が空欄である", listPage.input(ProjectListPage.END_DATE_FROM), "");
        verify.value("「終了日(至)」が空欄である", listPage.input(ProjectListPage.END_DATE_TO), "");
        verify.value("「登録日(自)」が空欄である", listPage.input(ProjectListPage.REGISTERED_DATE_FROM), "");
        verify.value("「登録日(至)」が空欄である", listPage.input(ProjectListPage.REGISTERED_DATE_TO), "");
        verify.value("「登録者」のオートコンプリートが空欄である", listPage.input(ProjectListPage.REGISTRANT), "");
        verify.value("「登録部署」のオートコンプリートが空欄である",
                listPage.input(ProjectListPage.REGISTRANT_DEPARTMENT), "");
        verify.value("「取引先」のオートコンプリートが空欄である", listPage.input(ProjectListPage.CLIENT), "");
        verify.unchecked("「BPが必要な案件のみ」がOFFである", listPage.input(ProjectListPage.BP_ONLY));
        verify.visible("「検索」ボタンが表示される", listPage.searchButton());
    }

    @Test
    @DisplayName("No.1-3 案件情報テーブルの列構成・区分値の表示・日付と日時の書式・初期ソート順")
    void case1x3() {
        seedFive();
        openList();

        verify.gridColumnHeaders("2. 列は左から「ID」「件名」…「更新日時」の24列である", listPage.grid(),
                ProjectListPage.DEFAULT_HEADERS);
        verify.gridRowsByKey("テーブルの行は管理コードの降順に並び、先頭行が ID: 5、末尾行が ID: 1 である", listPage.grid(),
                ProjectListPage.COL_CODE, DEFAULT_KEYS);
        verify.gridCellText("ID: 1 の行のステータスが「オープン」と表示される", listPage.grid(), 3,
                ProjectListPage.COL_STATUS, "オープン");
        verify.gridCellText("ID: 1 の行のBP募集(要否)が「必要」と表示される", listPage.grid(), 3,
                ProjectListPage.COL_BP, "必要");
        verify.gridCellText("ID: 1 の行の契約種別が「請負」と表示される", listPage.grid(), 3,
                ProjectListPage.COL_CONTRACT_TYPE, "請負");
        verify.gridCellText("ID: 1 の行の開始日が「2026/09/01」と表示される", listPage.grid(), 3,
                ProjectListPage.COL_START_DATE, "2026/09/01");
        verify.gridCellText("ID: 1 の行の終了日が「2026/12/31」と表示される", listPage.grid(), 3,
                ProjectListPage.COL_END_DATE, "2026/12/31");
        verify.gridCellText("ID: 1 の行の登録日時が「2026/08/01 10:00:00」と表示される", listPage.grid(), 3,
                ProjectListPage.COL_CREATED_AT, "2026/08/01 10:00:00");
        verify.gridCellText("ID: 1 の行の更新日時が「2026/08/01 10:00:00」と表示される", listPage.grid(), 3,
                ProjectListPage.COL_UPDATED_AT, "2026/08/01 10:00:00");
        verify.gridCellText("ID: 2 の行のステータスが「交渉中」と表示される", listPage.grid(), 2,
                ProjectListPage.COL_STATUS, "交渉中");
        verify.gridCellText("ID: 2 の行のBP募集(要否)が「不要」と表示される", listPage.grid(), 2,
                ProjectListPage.COL_BP, "不要");
        verify.gridCellText("ID: 2 の行の契約種別が「準委任」と表示される", listPage.grid(), 2,
                ProjectListPage.COL_CONTRACT_TYPE, "準委任");
    }

    @Test
    @DisplayName("No.1-4 値のない項目は空欄で表示され、案件概要・備考の改行は半角スペースに置換される")
    void case1x4() {
        seedFive();
        openList();
        openSearchForm(2);
        verify.op("2. ステータスの「クローズ」にチェックを入れる（ID: 3 を表示させる）",
                () -> listPage.check(ProjectListPage.ST_CLOSED),
                listPage.input(ProjectListPage.ST_CLOSED));
        pressSearch(2);

        verify.gridRowsByKey("2. テーブルに5件（ID: 5・4・3・2・1）が表示される", listPage.grid(),
                ProjectListPage.COL_CODE, ALL_KEYS);
        verify.gridCellText("ID: 3 の行の取引先が空欄である", listPage.grid(), 2, ProjectListPage.COL_CLIENT,
                "");
        verify.gridCellText("ID: 3 の行の商流が空欄である", listPage.grid(), 2, ProjectListPage.COL_FLOW, "");
        verify.gridCellText("ID: 3 の行の案件概要が空欄である", listPage.grid(), 2, ProjectListPage.COL_SUMMARY,
                "");
        verify.gridCellText("ID: 3 の行の開始日が空欄である", listPage.grid(), 2,
                ProjectListPage.COL_START_DATE, "");
        verify.gridCellText("ID: 3 の行の終了日が空欄である", listPage.grid(), 2, ProjectListPage.COL_END_DATE,
                "");
        verify.gridCellText("ID: 3 の行のスキルが空欄である", listPage.grid(), 2, ProjectListPage.COL_SKILL,
                "");
        verify.gridCellText("ID: 3 の行の工程が空欄である", listPage.grid(), 2, ProjectListPage.COL_PROCESS,
                "");
        verify.gridCellText("ID: 3 の行の人数が空欄である", listPage.grid(), 2, ProjectListPage.COL_HEADCOUNT,
                "");
        verify.gridCellText("ID: 3 の行の残数が空欄である", listPage.grid(), 2, ProjectListPage.COL_REMAINING,
                "");
        verify.gridCellText("ID: 3 の行のBP募集(詳細)が空欄である", listPage.grid(), 2,
                ProjectListPage.COL_BP_DETAIL, "");
        verify.gridCellText("ID: 3 の行の作業場所が空欄である", listPage.grid(), 2,
                ProjectListPage.COL_WORK_LOCATION, "");
        verify.gridCellText("ID: 3 の行の見込単価が空欄である", listPage.grid(), 2, ProjectListPage.COL_PRICE,
                "");
        verify.gridCellText("ID: 3 の行の契約種別が空欄である", listPage.grid(), 2,
                ProjectListPage.COL_CONTRACT_TYPE, "");
        verify.gridCellText("ID: 3 の行の備考が空欄である", listPage.grid(), 2, ProjectListPage.COL_NOTE, "");
        verify.gridCellText("ID: 3 の行のBP募集(要否)は「不要」と表示される", listPage.grid(), 2,
                ProjectListPage.COL_BP, "不要");
        verify.gridCellText("3. ID: 1 の「案件概要」が「基幹システムの改修。 二次開発フェーズ。」と1行で表示される（改行が半角スペースに置換されている）",
                listPage.grid(), 4, ProjectListPage.COL_SUMMARY, "基幹システムの改修。 二次開発フェーズ。");
    }

    @Test
    @DisplayName("No.1-5 表示内容が列幅に収まらない場合、先頭から収まる範囲を表示し末尾に省略表現を付す")
    void case1x5() {
        seedFive();
        openList();

        verify.gridCellText("2. ID: 1 の「案件概要」（初期幅350px）に案件概要が表示されている", listPage.grid(), 3,
                ProjectListPage.COL_SUMMARY, "基幹システムの改修。 二次開発フェーズ。");
        verify.op("3. 「案件概要」列の右側の境界線をドラッグして列幅を最小（80px）まで狭める",
                () -> listPage.grid().shrinkColumnToMinimum(ProjectListPage.COL_SUMMARY),
                listPage.grid().headerCell(ProjectListPage.COL_SUMMARY));

        verify.textTruncated("3. ID: 1 の「案件概要」の表示が列幅に収まる範囲で切り詰められ、末尾に省略表現が付く",
                listPage.grid().cell(3, ProjectListPage.COL_SUMMARY));
        verify.gridCellText("「ID」列の値（先頭行 ID: 5）は切り詰められずそのまま表示される", listPage.grid(), 0,
                ProjectListPage.COL_CODE, "5");
        verify.gridCellText("「ID」列の値（末尾行 ID: 1）は切り詰められずそのまま表示される", listPage.grid(), 3,
                ProjectListPage.COL_CODE, "1");
    }

    @Test
    @DisplayName("No.1-6 テーブルの行が画面に収まらない場合、テーブル内で縦スクロールする")
    void case1x6() {
        seedPagination();
        openList();

        verify.gridCellText("1. 1ページ目の先頭行は ID: 80（管理コードの降順）である", listPage.grid(), 0,
                ProjectListPage.COL_CODE, "80");
        verify.gridRowsByKey(
                "2. テーブル内が縦にスクロールし、1ページ目の末尾行 ID: 51 まで到達できる" + "（【機械検証】末尾行 ID: 51 の次に行が存在しない）",
                listPage.grid(), ProjectListPage.COL_CODE, descendingKeys(80, 51));
    }

    // ---- 2. 画面イベント処理（検索フォーム・テーブル操作・レイアウト） ----

    @Test
    @DisplayName("No.2-1 案件検索アコーディオンのクリックで検索フォームの表示・非表示が切り替わる")
    void case2x1() {
        seedFive();
        openList();

        verify.absent("1. 検索フォーム（「検索キーワード」「検索」ボタン等）は表示されていない", listPage.searchAccordionHead(),
                listPage.visibleSearchPanelBody());
        openSearchForm(2);
        verify.visible("2. 「検索キーワード」が現れる", listPage.input(ProjectListPage.KEYWORD));
        verify.visible("2. 「検索」ボタンが現れる", listPage.searchButton());
        verify.transitionHideByPress("検索フォームが表示されている", listPage.searchPanelBody(),
                "3. もう一度「案件検索」アコーディオンを押下する", listPage.searchAccordionHead(),
                () -> listPage.toggleSearchAccordion(), listPage.searchAccordionHead(),
                "3. 同じ画角から検索フォームが消える");
    }

    @Test
    @DisplayName("No.2-2 登録者・登録部署・取引先のオートコンプリートに、それぞれのマスタの値が既定の順で表示される")
    void case2x2() {
        seedFive();
        openList();
        openSearchForm(2);

        verify.op("3. 「登録者」の入力欄をクリックして選択肢を表示する",
                () -> listPage.input(ProjectListPage.REGISTRANT).click(),
                listPage.input(ProjectListPage.REGISTRANT));
        verify.autocompleteOptions("3. 「営業 太郎」「管理 花子」「開発 次郎」がユーザIDの昇順で表示される",
                listPage.input(ProjectListPage.REGISTRANT), "registrantOptions",
                List.of("営業 太郎", "管理 花子", "開発 次郎"));
        verify.op("4. 「登録部署」の入力欄をクリックして選択肢を表示する",
                () -> listPage.input(ProjectListPage.REGISTRANT_DEPARTMENT).click(),
                listPage.input(ProjectListPage.REGISTRANT_DEPARTMENT));
        verify.autocompleteOptions("4. 「営業部」「開発部」「管理部」が部署IDの昇順で表示される",
                listPage.input(ProjectListPage.REGISTRANT_DEPARTMENT), "departmentOptions",
                List.of("営業部", "開発部", "管理部"));
        verify.op("5. 「取引先」の入力欄をクリックして選択肢を表示する",
                () -> listPage.input(ProjectListPage.CLIENT).click(),
                listPage.input(ProjectListPage.CLIENT));
        verify.autocompleteOptions("5. 「株式会社アルファ」から始まる会社名が会社IDの昇順で表示される（空欄の選択肢は含まれない）",
                listPage.input(ProjectListPage.CLIENT), "clientOptions",
                List.of("株式会社アルファ", "株式会社ブラボー", "株式会社チャーリー", "株式会社デルタ", "株式会社エコー", "株式会社フォックス",
                        "株式会社ゴルフ", "株式会社ホテル", "株式会社インディア", "株式会社ジュリエット", "株式会社キロ", "株式会社リマ"));
    }

    @Test
    @DisplayName("No.2-3 列ヘッダーのドラッグ＆ドロップで列の順序を変更できる（BにドロップするとBの左側にAが挿入される）")
    void case2x3() {
        seedFive();
        openList();

        verify.gridColumnHeaders("1. 列が左から「ID」「件名」「ステータス」「登録者」…の順である", listPage.grid(),
                ProjectListPage.DEFAULT_HEADERS);
        verify.op("2. 「登録者」の列ヘッダーをドラッグし、「ステータス」の列ヘッダーの上でドロップする",
                () -> listPage.grid().moveColumn(ProjectListPage.COL_CREATED_BY,
                        ProjectListPage.COL_STATUS),
                listPage.grid().headerCell(ProjectListPage.COL_CREATED_BY));

        verify.gridColumnHeaders("2. 列が左から「ID」「件名」「登録者」「ステータス」「登録部署」…の順に変わる", listPage.grid(),
                HEADERS_CREATED_BY_MOVED);
        verify.gridCellText("先頭行（ID: 5）の「登録者」の値が列の入れ替えに追従する", listPage.grid(), 0,
                ProjectListPage.COL_CREATED_BY, "開発 次郎");
        verify.gridCellText("先頭行（ID: 5）の「ステータス」の値が列の入れ替えに追従する", listPage.grid(), 0,
                ProjectListPage.COL_STATUS, "オープン");
    }

    @Test
    @DisplayName("No.2-4 管理コード（ID）列は移動不可であり、その上にドロップした列は右側に挿入される")
    void case2x4() {
        seedFive();
        openList();

        verify.op("2. 「ID」の列ヘッダーをドラッグし、「ステータス」の列ヘッダーの上でドロップしようとする",
                () -> listPage.grid().moveColumn(ProjectListPage.COL_CODE,
                        ProjectListPage.COL_STATUS),
                listPage.grid().headerCell(ProjectListPage.COL_CODE));
        verify.gridColumnHeaders("2. 列順は変わらず「ID」は左端のままである", listPage.grid(),
                ProjectListPage.DEFAULT_HEADERS);

        verify.op("3. 「ステータス」の列ヘッダーをドラッグし、「ID」の列ヘッダーの上でドロップする",
                () -> listPage.grid().moveColumn(ProjectListPage.COL_STATUS,
                        ProjectListPage.COL_CODE),
                listPage.grid().headerCell(ProjectListPage.COL_STATUS));
        verify.gridColumnHeaders("3. 「ステータス」が「ID」の右側に挿入され、列が左から「ID」「ステータス」「件名」「登録者」…の順に変わる",
                listPage.grid(), HEADERS_STATUS_SECOND);
    }

    @Test
    @DisplayName("No.2-5 列固定境界より左側の2列は、テーブルを横スクロールしても左端に固定表示される")
    void case2x5() {
        seedFive();
        openList();

        verify.gridColumnHeaders("1. 左端2列が「ID」「件名」であり、2. 横スクロールしても左端に固定表示されたまま他の列がスクロールする",
                listPage.grid(), ProjectListPage.DEFAULT_HEADERS);
        verify.op("3. 「件名」の列ヘッダーをドラッグし、「ステータス」の列ヘッダーの上でドロップする",
                () -> listPage.grid().moveColumn(ProjectListPage.COL_TITLE,
                        ProjectListPage.COL_STATUS),
                listPage.grid().headerCell(ProjectListPage.COL_TITLE));

        verify.gridColumnHeaders("3. 左端2列が「ID」「ステータス」になり、4. 再び横スクロールしても2列が左端に固定表示されたままである",
                listPage.grid(), HEADERS_STATUS_SECOND);
    }

    @Test
    @DisplayName("No.2-6 列ヘッダーの境界をドラッグして列幅を変更でき、最小値は80pxである")
    void case2x6() {
        seedFive();
        openList();

        verify.op("2. 「件名」列と「ステータス」列の間の境界線をドラッグして右へ100px広げる",
                () -> listPage.grid().resizeColumnBy(ProjectListPage.COL_TITLE, 100),
                listPage.grid().headerCell(ProjectListPage.COL_TITLE));
        verify.gridColumnWidthIs("【機械検証】手順2の後の「件名」列の幅が 320px である", listPage.grid(),
                ProjectListPage.COL_TITLE, 320);

        verify.op("3. 同じ境界線をドラッグして左へ、80pxより狭くなるまで動かす",
                () -> listPage.grid().shrinkColumnToMinimum(ProjectListPage.COL_TITLE),
                listPage.grid().headerCell(ProjectListPage.COL_TITLE));
        verify.gridColumnWidthIs("【機械検証】手順3の後の「件名」列の幅が 80px である（それより狭くならない）", listPage.grid(),
                ProjectListPage.COL_TITLE, 80);
    }

    @Test
    @DisplayName("No.2-7 表示件数の変更とページ送りができ、テーブル上部・下部の操作部品が連動する")
    void case2x7() {
        seedPagination();
        openList();

        verify.op("2. テーブル上部の「表示件数」で「10件」を選択する",
                () -> listPage.selectPageSize(ProjectListPage.PAGER_TOP, "10"),
                listPage.pageSizeSelect(ProjectListPage.PAGER_TOP));
        verify.gridRowsByKey("2. テーブルに ID: 80〜71 の10件が管理コードの降順で表示される", listPage.grid(),
                ProjectListPage.COL_CODE, descendingKeys(80, 71));
        verify.value("2. テーブル下部の「表示件数」も「10件」に連動して変わる",
                listPage.pageSizeSelect(ProjectListPage.PAGER_BOTTOM), "10");

        verify.op("3. テーブル下部の「表示ページ」で「2 / 8」を選択する",
                () -> listPage.selectPage(ProjectListPage.PAGER_BOTTOM, 1),
                listPage.pageJumpSelect(ProjectListPage.PAGER_BOTTOM));
        verify.gridRowsByKey("3. テーブルに ID: 70〜61 の10件が表示される", listPage.grid(10),
                ProjectListPage.COL_CODE, descendingKeys(70, 61));
        verify.value("3. テーブル上部の「表示ページ」も「2 / 8」に連動して変わる",
                listPage.pageJumpSelect(ProjectListPage.PAGER_TOP), "1");
        verify.text("取得データ件数は「取得データ件数: 80件」のまま変わらない",
                listPage.dataCountLabel(ProjectListPage.PAGER_TOP), "取得データ件数: 80件");
    }

    @Test
    @DisplayName("No.2-8 列ヘッダーのダブルクリックでソート状態が切り替わり、別列の適用で既存のソートが解除される（初期は管理コード列に降順のソートが適用済み）")
    void case2x8() {
        seedFive();
        openList();

        verify.visible("1. 「ID」列ヘッダーに降順のソート意匠が表示されている",
                listPage.sortDescendingIcon(ProjectListPage.COL_CODE));
        verify.gridRowsByKey("1. 管理コードの降順で ID: 5・4・2・1 が表示されている", listPage.grid(),
                ProjectListPage.COL_CODE, DEFAULT_KEYS);

        verify.transitionHide("「ID」列に降順のソート意匠が表示されている",
                listPage.sortDescendingIcon(ProjectListPage.COL_CODE), "2. 「ID」の列ヘッダーをダブルクリックする",
                () -> listPage.doubleClickHeader(ProjectListPage.COL_CODE),
                listPage.grid().headerCell(ProjectListPage.COL_CODE), "2. ソートなしとなり「ID」列の意匠が消える");
        verify.gridRowsByKey("2. 表示は既定の並び順である管理コードの降順（ID: 5・4・2・1）のまま変わらない", listPage.grid(),
                ProjectListPage.COL_CODE, DEFAULT_KEYS);

        verify.op("3. もう一度「ID」の列ヘッダーをダブルクリックする",
                () -> listPage.doubleClickHeader(ProjectListPage.COL_CODE),
                listPage.grid().headerCell(ProjectListPage.COL_CODE));
        verify.visible("3. 「ID」列に昇順の意匠が付く", listPage.sortAscendingIcon(ProjectListPage.COL_CODE));
        verify.gridRowsByKey("3. IDの数値の昇順に並び替わり、先頭行が ID: 1、末尾行が ID: 5 になる", listPage.grid(),
                ProjectListPage.COL_CODE, List.of("1", "2", "4", "5"));

        verify.op("4. もう一度「ID」の列ヘッダーをダブルクリックする",
                () -> listPage.doubleClickHeader(ProjectListPage.COL_CODE),
                listPage.grid().headerCell(ProjectListPage.COL_CODE));
        verify.visible("4. 「ID」列に降順の意匠が付く", listPage.sortDescendingIcon(ProjectListPage.COL_CODE));
        verify.gridRowsByKey("4. IDの数値の降順に並び替わり、先頭行が ID: 5、末尾行が ID: 1 になる", listPage.grid(),
                ProjectListPage.COL_CODE, DEFAULT_KEYS);

        verify.op("5. 「ステータス」の列ヘッダーをダブルクリックする",
                () -> listPage.doubleClickHeader(ProjectListPage.COL_STATUS),
                listPage.grid().headerCell(ProjectListPage.COL_STATUS));
        verify.absent("5. 「ID」列のソート中の意匠が消える（複数列によるソートは行われない）",
                listPage.grid().headerCell(ProjectListPage.COL_CODE),
                listPage.visibleSortIcon(ProjectListPage.COL_CODE));
        verify.visible("5. 「ステータス」列に昇順の意匠が付く",
                listPage.sortAscendingIcon(ProjectListPage.COL_STATUS));
        verify.gridCellText("5. ステータスの文字列の昇順に並ぶ（1行目が「オープン」）", listPage.grid(), 0,
                ProjectListPage.COL_STATUS, "オープン");
        verify.gridCellText("5. ステータスの文字列の昇順に並ぶ（2行目が「オープン」）", listPage.grid(), 1,
                ProjectListPage.COL_STATUS, "オープン");
        verify.gridCellText("5. ステータスの文字列の昇順に並ぶ（3行目が「オープン」）", listPage.grid(), 2,
                ProjectListPage.COL_STATUS, "オープン");
        verify.gridCellText("5. ステータスの文字列の昇順に並ぶ（4行目が「交渉中」）", listPage.grid(), 3,
                ProjectListPage.COL_STATUS, "交渉中");
    }

    @Test
    @DisplayName("No.2-9 検索条件と列レイアウトがローカルストレージに保存され、画面の再表示時に復元される")
    void case2x9() {
        seedFive();
        openList();
        openSearchForm(2);

        verify.op("3. 「検索キーワード」に「案件」を入力する", () -> listPage.fill(ProjectListPage.KEYWORD, "案件"),
                listPage.input(ProjectListPage.KEYWORD));
        verify.op("3. ステータスの「クローズ」にチェックを入れる", () -> listPage.check(ProjectListPage.ST_CLOSED),
                listPage.input(ProjectListPage.ST_CLOSED));
        pressSearch(3);
        verify.gridRowsByKey("3. 検索が実行され、5件（ID: 5・4・3・2・1）が表示される", listPage.grid(),
                ProjectListPage.COL_CODE, ALL_KEYS);
        verify.visible("3. 検索フォームは表示状態のままである", listPage.searchPanelBody());
        verify.value("3. 入力した「検索キーワード」も入力されたままである", listPage.input(ProjectListPage.KEYWORD), "案件");

        verify.op("4. 「登録者」の列ヘッダーをドラッグして「ステータス」の列ヘッダーの上にドロップし、列順を変更する",
                () -> listPage.grid().moveColumn(ProjectListPage.COL_CREATED_BY,
                        ProjectListPage.COL_STATUS),
                listPage.grid().headerCell(ProjectListPage.COL_CREATED_BY));

        final String layoutBefore = listPage.savedColumnLayout();
        verify.opPress("5. 共通ヘッダーの「新規登録」を押下して案件情報登録画面へ移動する",
                commonHeader.nav(CommonHeaderPage.NAV_REGISTER),
                () -> commonHeader.clickNav(CommonHeaderPage.NAV_REGISTER), registerPage.form());
        verify.opPress("5. 「戻る」を押下して案件情報一覧画面に戻る", registerPage.backButtonTop(), () -> backToList(),
                listPage.grid().root());
        final String layoutAfter = listPage.savedColumnLayout();

        verify.gridRowsByKey("5. 再表示された画面は手順3の検索条件で検索した結果（5件）を表示する", listPage.grid(),
                ProjectListPage.COL_CODE, ALL_KEYS);
        verify.opPress("「案件検索」アコーディオンを開く", listPage.searchAccordionHead(),
                () -> listPage.toggleSearchAccordion(), listPage.searchPanelBody());
        verify.value("復元した「検索キーワード」に「案件」が反映されている", listPage.input(ProjectListPage.KEYWORD), "案件");
        verify.checked("ステータスの「オープン」がチェックありで復元されている", listPage.input(ProjectListPage.ST_OPEN));
        verify.checked("ステータスの「交渉中」がチェックありで復元されている",
                listPage.input(ProjectListPage.ST_NEGOTIATING));
        verify.checked("ステータスの「クローズ」がチェックありで復元されている", listPage.input(ProjectListPage.ST_CLOSED));
        verify.gridColumnHeaders("列順も手順4の状態（「ID」「件名」「登録者」「ステータス」…）で復元される", listPage.grid(),
                HEADERS_CREATED_BY_MOVED);
        verify.machineEquals("【機械検証】手順5の再表示の前後で、保存された列レイアウトの値が変化していない", layoutBefore, layoutAfter);
    }

    @Test
    @DisplayName("No.2-10 テーブル初期化ボタンで列レイアウトが既定にリセットされ、保存済みのレイアウトも更新される")
    void case2x10() {
        seedFive();
        openList();

        verify.op("2. 「登録者」の列ヘッダーをドラッグして「ステータス」の列ヘッダーの上にドロップする",
                () -> listPage.grid().moveColumn(ProjectListPage.COL_CREATED_BY,
                        ProjectListPage.COL_STATUS),
                listPage.grid().headerCell(ProjectListPage.COL_CREATED_BY));
        verify.op("2. 「件名」列の幅を最小（80px）まで狭める",
                () -> listPage.grid().shrinkColumnToMinimum(ProjectListPage.COL_TITLE),
                listPage.grid().headerCell(ProjectListPage.COL_TITLE));
        verify.gridColumnHeaders("2. 列順が「ID」「件名」「登録者」「ステータス」…に変わる", listPage.grid(),
                HEADERS_CREATED_BY_MOVED);
        verify.gridColumnWidthIs("【機械検証】手順2の後の「件名」列の幅が 80px である", listPage.grid(),
                ProjectListPage.COL_TITLE, 80);

        final String layoutBeforeReset = listPage.savedColumnLayout();
        verify.opPress("3. 「テーブル初期化」を押下する", listPage.resetLayoutButton(),
                () -> listPage.pressResetLayout(), listPage.grid().root());
        final String layoutAfterReset = listPage.savedColumnLayout();

        verify.gridColumnHeaders("3. 列順が既定の「ID」「件名」「ステータス」「登録者」…に戻る", listPage.grid(),
                ProjectListPage.DEFAULT_HEADERS);
        verify.gridColumnWidthIs("【機械検証】手順3の後の「件名」列の幅が 220px である（既定に戻る）", listPage.grid(),
                ProjectListPage.COL_TITLE, 220);
        verify.visible("3. 「ID」列には降順のソート意匠が付いたままである（既定レイアウトのソート状態）",
                listPage.sortDescendingIcon(ProjectListPage.COL_CODE));

        verify.opPress("4. 共通ヘッダーの「新規登録」を押下して案件情報登録画面へ移動する",
                commonHeader.nav(CommonHeaderPage.NAV_REGISTER),
                () -> commonHeader.clickNav(CommonHeaderPage.NAV_REGISTER), registerPage.form());
        verify.opPress("4. 「戻る」を押下して案件情報一覧画面に戻る", registerPage.backButtonTop(), () -> backToList(),
                listPage.grid().root());
        final String layoutAfterReturn = listPage.savedColumnLayout();

        verify.gridColumnHeaders("4. 再表示された画面も既定レイアウトのままである", listPage.grid(),
                ProjectListPage.DEFAULT_HEADERS);
        verify.machineEquals("【機械検証】手順3の前後でローカルストレージのレイアウト値が更新されている", "更新あり",
                layoutBeforeReset.equals(layoutAfterReset) ? "更新なし" : "更新あり");
        verify.machineEquals("【機械検証】手順4の再表示の前後ではローカルストレージのレイアウト値が変化しない", layoutAfterReset,
                layoutAfterReturn);
    }

    // ---- 3. 画面遷移 ----

    @Test
    @DisplayName("No.3-1 新規登録ボタンで案件情報登録画面へ遷移する")
    void case3x1() {
        seedFive();
        openList();

        verify.opPress("2. 「新規登録」ボタンを押下する", listPage.newProjectButton(),
                () -> listPage.newProjectButton().click(), registerPage.form());
        verify.urlIs("/projects/new へ遷移する", registerPage.url());
        verify.visible("画面タイトル「案件情報登録画面」が表示される",
                commonHeader.screenTitleOf(ProjectRegisterPage.SCREEN_TITLE));
    }

    @Test
    @DisplayName("No.3-2 テーブルの行をクリックすると、該当する案件情報を表示した案件情報更新画面へ遷移する")
    void case3x2() {
        seedFive();
        openList();

        verify.opPress("2. ID: 2「Python分析案件」の行をクリックする",
                listPage.grid().cell(2, ProjectListPage.COL_TITLE), () -> listPage.clickRow(2),
                editPage.form());
        verify.urlIs("/projects/detail/2 へ遷移する", editPage.url(2));
        verify.visible("画面タイトル「案件情報更新画面」が表示される",
                commonHeader.screenTitleOf(ProjectEditPage.SCREEN_TITLE));
        verify.value("「件名」に「Python分析案件」が表示される", editPage.input(ProjectFormPage.SUBJECT),
                "Python分析案件");
        verify.value("「ステータス」に区分値 NEGOTIATING の表示名「交渉中」が表示される",
                editPage.input(ProjectFormPage.STATUS), "NEGOTIATING");
        verify.value("「取引先」に「株式会社ブラボー」が表示される", editPage.input(ProjectFormPage.CLIENT), "株式会社ブラボー");
    }

    // ---- 4. サーバー・APIサービス処理（案件検索） ----

    @Test
    @DisplayName("No.4-1 既定の検索条件で検索を実行し、結果がテーブルに反映される")
    void case4x1() {
        seedFive();
        openList();
        openSearchForm(2);
        pressSearch(3);

        verify.gridRowsByKey("ステータスが「オープン」または「交渉中」の4件（ID: 5・4・2・1）が管理コードの降順で表示される", listPage.grid(),
                ProjectListPage.COL_CODE, DEFAULT_KEYS);
        dataCountIs(4);
        verify.visible("検索フォームは表示状態のままである", listPage.searchPanelBody());
        verify.checked("入力した検索条件（キーワード検索対象「件名」）も入力されたままである",
                listPage.input(ProjectListPage.KW_TITLE));
        verify.absent("各検索条件の入力欄の直下に入力値チェックのエラーメッセージは表示されない", listPage.searchPanelBody(),
                listPage.filledFieldErrors());
    }

    @Test
    @DisplayName("No.4-2 キーワード検索は指定した属性で部分一致検索する（連続した部分文字列のみ一致）")
    void case4x2() {
        seedFive();
        openList();
        openSearchForm(2);

        verify.op("3. 「検索キーワード」に「分析」を入力する", () -> listPage.fill(ProjectListPage.KEYWORD, "分析"),
                listPage.input(ProjectListPage.KEYWORD));
        pressSearch(3);
        verify.gridRowsByKey("3. ID: 2「Python分析案件」の1件のみが表示される", listPage.grid(),
                ProjectListPage.COL_CODE, List.of("2"));
        dataCountIs(1);

        verify.op("4. 「検索キーワード」を「Pyhon」に変更する",
                () -> listPage.fill(ProjectListPage.KEYWORD, "Pyhon"),
                listPage.input(ProjectListPage.KEYWORD));
        pressSearch(4);
        verify.text("4. 「該当するデータがありません」が表示される", listPage.noRowsOverlay(), NO_ROWS);
        dataCountIs(0);
    }

    @Test
    @DisplayName("No.4-3 キーワード検索対象を複数選択した場合はOR検索になる")
    void case4x3() {
        seedFive();
        openList();
        openSearchForm(2);

        verify.op("3. 「検索キーワード」に「Java」を入力する", () -> listPage.fill(ProjectListPage.KEYWORD, "Java"),
                listPage.input(ProjectListPage.KEYWORD));
        pressSearch(3);
        verify.gridRowsByKey("3. ID: 1「Java開発案件」の1件のみが表示される（件名に「Java」を含む）", listPage.grid(),
                ProjectListPage.COL_CODE, List.of("1"));

        verify.op("4. キーワード検索対象の「スキル」にもチェックを入れる", () -> listPage.check(ProjectListPage.KW_SKILL),
                listPage.input(ProjectListPage.KW_SKILL));
        pressSearch(4);
        verify.gridRowsByKey("4. ID: 5「BP募集の案件」（スキル「JavaScript」）が加わり2件（ID: 5・1）が表示される",
                listPage.grid(), ProjectListPage.COL_CODE, List.of("5", "1"));
        dataCountIs(2);
    }

    @Test
    @DisplayName("No.4-4 ステータス検索は複数指定でOR検索になる")
    void case4x4() {
        seedFive();
        openList();
        openSearchForm(2);

        verify.op("3. ステータスの「オープン」のチェックを外す", () -> listPage.uncheck(ProjectListPage.ST_OPEN),
                listPage.input(ProjectListPage.ST_OPEN));
        verify.op("3. ステータスの「交渉中」のチェックを外す", () -> listPage.uncheck(ProjectListPage.ST_NEGOTIATING),
                listPage.input(ProjectListPage.ST_NEGOTIATING));
        verify.op("3. ステータスの「クローズ」のみチェックする", () -> listPage.check(ProjectListPage.ST_CLOSED),
                listPage.input(ProjectListPage.ST_CLOSED));
        pressSearch(3);
        verify.gridRowsByKey("3. ID: 3「クローズ済み案件」の1件のみが表示される", listPage.grid(),
                ProjectListPage.COL_CODE, List.of("3"));

        verify.op("4. ステータスの「オープン」にもチェックを入れる", () -> listPage.check(ProjectListPage.ST_OPEN),
                listPage.input(ProjectListPage.ST_OPEN));
        pressSearch(4);
        verify.gridRowsByKey("4. ID: 5・4・3・1 の4件が表示される（オープン3件とクローズ1件のOR検索）", listPage.grid(),
                ProjectListPage.COL_CODE, List.of("5", "4", "3", "1"));
    }

    @Test
    @DisplayName("No.4-5 開始日範囲検索は下限値・上限値そのものを含む範囲で検索する")
    void case4x5() {
        seedFive();
        openList();
        openSearchForm(2);
        includeClosed(3);

        verify.op("4. 「開始日(自)」に 2026/09/15 を入力する",
                () -> listPage.fill(ProjectListPage.START_DATE_FROM, "2026-09-15"),
                listPage.input(ProjectListPage.START_DATE_FROM));
        verify.op("4. 「開始日(至)」に 2026/11/01 を入力する",
                () -> listPage.fill(ProjectListPage.START_DATE_TO, "2026-11-01"),
                listPage.input(ProjectListPage.START_DATE_TO));
        pressSearch(4);

        verify.gridRowsByKey(
                "4. ID: 5・4・2 の3件が管理コードの降順で表示される（下限値・上限値そのものを含む。"
                        + "ID: 1 は範囲外、ID: 3 は開始日が未入力のため表示されない）",
                listPage.grid(), ProjectListPage.COL_CODE, List.of("5", "4", "2"));
        dataCountIs(3);
    }

    @Test
    @DisplayName("No.4-6 範囲検索は片側のみでも実施でき、対象項目が未入力の案件情報は条件を満たさない")
    void case4x6() {
        seedFive();
        openList();
        openSearchForm(2);
        includeClosed(3);

        verify.op("4. 「終了日(自)」にのみ 2027/01/01 を入力する",
                () -> listPage.fill(ProjectListPage.END_DATE_FROM, "2027-01-01"),
                listPage.input(ProjectListPage.END_DATE_FROM));
        pressSearch(4);

        verify.gridRowsByKey(
                "4. ID: 5・4・2 の3件が管理コードの降順で表示される" + "（ID: 1 は範囲外、ID: 3 は終了日が未入力のため表示されない）",
                listPage.grid(), ProjectListPage.COL_CODE, List.of("5", "4", "2"));
        dataCountIs(3);
    }

    @Test
    @DisplayName("No.4-7 登録日範囲検索は登録日時の時間・分・秒を考慮せず日付のみで比較する")
    void case4x7() {
        seedFive();
        openList();
        openSearchForm(2);
        includeClosed(3);

        verify.op("4. 「登録日(自)」に 2026/08/05 を入力する",
                () -> listPage.fill(ProjectListPage.REGISTERED_DATE_FROM, "2026-08-05"),
                listPage.input(ProjectListPage.REGISTERED_DATE_FROM));
        verify.op("4. 「登録日(至)」に 2026/08/05 を入力する",
                () -> listPage.fill(ProjectListPage.REGISTERED_DATE_TO, "2026-08-05"),
                listPage.input(ProjectListPage.REGISTERED_DATE_TO));
        pressSearch(4);

        verify.gridRowsByKey(
                "4. ID: 2 の1件のみが表示される（登録日時 2026/08/05 09:30:00 が"
                        + "時刻を考慮せず日付 2026/08/05 として範囲に含まれる）",
                listPage.grid(), ProjectListPage.COL_CODE, List.of("2"));
        dataCountIs(1);
    }

    @Test
    @DisplayName("No.4-8 登録者検索は入力された文字列で部分一致検索する")
    void case4x8() {
        seedFive();
        openList();
        openSearchForm(2);
        includeClosed(3);

        verify.op("4. 「登録者」に「太郎」を入力する", () -> listPage.fill(ProjectListPage.REGISTRANT, "太郎"),
                listPage.input(ProjectListPage.REGISTRANT));
        pressSearch(4);

        verify.gridRowsByKey(
                "4. ID: 4・1 の2件が管理コードの降順で表示される（登録者「営業 太郎」。" + "「開発 次郎」「管理 花子」の案件情報は表示されない）",
                listPage.grid(), ProjectListPage.COL_CODE, List.of("4", "1"));
        dataCountIs(2);
    }

    @Test
    @DisplayName("No.4-9 登録部署検索は入力された文字列で部分一致検索する")
    void case4x9() {
        seedFive();
        openList();
        openSearchForm(2);
        includeClosed(3);

        verify.op("4. 「登録部署」に「開発」を入力する",
                () -> listPage.fill(ProjectListPage.REGISTRANT_DEPARTMENT, "開発"),
                listPage.input(ProjectListPage.REGISTRANT_DEPARTMENT));
        pressSearch(4);

        verify.gridRowsByKey("4. ID: 5・2 の2件が管理コードの降順で表示される（登録部署「開発部」）", listPage.grid(),
                ProjectListPage.COL_CODE, List.of("5", "2"));
        dataCountIs(2);
    }

    @Test
    @DisplayName("No.4-10 取引先検索は入力された文字列で部分一致検索する")
    void case4x10() {
        seedFive();
        openList();
        openSearchForm(2);
        includeClosed(3);

        verify.op("4. 「取引先」に「デルタ」を入力する", () -> listPage.fill(ProjectListPage.CLIENT, "デルタ"),
                listPage.input(ProjectListPage.CLIENT));
        pressSearch(4);

        verify.gridRowsByKey("4. ID: 5「BP募集の案件」の1件のみが表示される（取引先「株式会社デルタ」）", listPage.grid(),
                ProjectListPage.COL_CODE, List.of("5"));
        dataCountIs(1);
    }

    @Test
    @DisplayName("No.4-11 BP条件検索はBP募集(要否)が「必要」の案件情報に限定する")
    void case4x11() {
        seedFive();
        openList();
        openSearchForm(2);
        includeClosed(3);

        verify.op("4. 「BPが必要な案件のみ」にチェックを入れる", () -> listPage.check(ProjectListPage.BP_ONLY),
                listPage.input(ProjectListPage.BP_ONLY));
        pressSearch(4);

        verify.gridRowsByKey(
                "4. ID: 5・1 の2件が管理コードの降順で表示される（いずれもBP募集(要否)が「必要」。" + "「不要」の ID: 4・3・2 は表示されない）",
                listPage.grid(), ProjectListPage.COL_CODE, List.of("5", "1"));
        dataCountIs(2);
    }

    @Test
    @DisplayName("No.4-12 複数の検索条件が入力された場合は全ての条件を満たす案件情報を抽出する（AND条件）")
    void case4x12() {
        seedFive();
        openList();
        openSearchForm(2);
        includeClosed(3);

        verify.op("4. 「登録者」に「次郎」を入力する", () -> listPage.fill(ProjectListPage.REGISTRANT, "次郎"),
                listPage.input(ProjectListPage.REGISTRANT));
        verify.op("4. 「BPが必要な案件のみ」にチェックを入れる", () -> listPage.check(ProjectListPage.BP_ONLY),
                listPage.input(ProjectListPage.BP_ONLY));
        pressSearch(4);

        verify.gridRowsByKey(
                "4. ID: 5「BP募集の案件」の1件のみが表示される（登録者が「開発 次郎」かつ"
                        + "BP募集(要否)が「必要」の両方を満たす。「不要」の ID: 2 は表示されない）",
                listPage.grid(), ProjectListPage.COL_CODE, List.of("5"));
        dataCountIs(1);
    }

    @Test
    @DisplayName("No.4-13 いずれの検索条件も入力されていない場合は案件情報を全件検索する")
    void case4x13() {
        seedFive();
        openList();
        openSearchForm(2);

        verify.op("3. キーワード検索対象の「件名」のチェックを外す", () -> listPage.uncheck(ProjectListPage.KW_TITLE),
                listPage.input(ProjectListPage.KW_TITLE));
        verify.op("3. ステータスの「オープン」のチェックを外す", () -> listPage.uncheck(ProjectListPage.ST_OPEN),
                listPage.input(ProjectListPage.ST_OPEN));
        verify.op("3. ステータスの「交渉中」のチェックを外す", () -> listPage.uncheck(ProjectListPage.ST_NEGOTIATING),
                listPage.input(ProjectListPage.ST_NEGOTIATING));
        pressSearch(4);

        verify.gridRowsByKey("4. 全5件（ID: 5・4・3・2・1）が管理コードの降順で表示される", listPage.grid(),
                ProjectListPage.COL_CODE, ALL_KEYS);
        dataCountIs(5);
    }

    @Test
    @DisplayName("No.4-14 検索対象が一件もチェックされていない場合はキーワード検索を行わない")
    void case4x14() {
        seedFive();
        openList();
        openSearchForm(2);

        verify.op("3. 「検索キーワード」に「分析」を入力する", () -> listPage.fill(ProjectListPage.KEYWORD, "分析"),
                listPage.input(ProjectListPage.KEYWORD));
        verify.op("3. キーワード検索対象の「件名」のチェックを外す（対象を一件もチェックしない状態にする）",
                () -> listPage.uncheck(ProjectListPage.KW_TITLE),
                listPage.input(ProjectListPage.KW_TITLE));
        pressSearch(3);

        verify.gridRowsByKey(
                "キーワード検索は行われず、ステータスの既定条件（オープン・交渉中）だけで検索された" + "4件（ID: 5・4・2・1）が管理コードの降順で表示される",
                listPage.grid(), ProjectListPage.COL_CODE, DEFAULT_KEYS);
        dataCountIs(4);
    }

    @Test
    @DisplayName("No.4-15 検索結果が0件の場合の画面表示")
    void case4x15() {
        seedFive();
        openList();
        openSearchForm(2);

        verify.op("3. 「検索キーワード」に「該当しない文字列」を入力する",
                () -> listPage.fill(ProjectListPage.KEYWORD, "該当しない文字列"),
                listPage.input(ProjectListPage.KEYWORD));
        pressSearch(3);

        verify.gridColumnHeaders("列ヘッダーはデータが存在する場合と同様に24列が表示される", listPage.grid(),
                ProjectListPage.DEFAULT_HEADERS);
        verify.absent("列ボディに行が1件も無く、列間の区切りの線が表示されない", listPage.grid().verticalViewport(),
                listPage.dataRows());
        verify.text("列ボディに中央寄せで「該当するデータがありません」が表示される", listPage.noRowsOverlay(), NO_ROWS);
        dataCountIs(0);
        verify.disabled("ページネーションの «（先頭へ）がクリック不可である",
                listPage.pagerButton(ProjectListPage.PAGER_TOP, "«"));
        verify.disabled("ページネーションの ‹（前へ）がクリック不可である",
                listPage.pagerButton(ProjectListPage.PAGER_TOP, "‹"));
        verify.disabled("ページネーションのページ番号「1」がクリック不可である",
                listPage.pagerButton(ProjectListPage.PAGER_TOP, "1"));
        verify.disabled("ページネーションの ›（次へ）がクリック不可である",
                listPage.pagerButton(ProjectListPage.PAGER_TOP, "›"));
        verify.disabled("ページネーションの »（末尾へ）がクリック不可である",
                listPage.pagerButton(ProjectListPage.PAGER_TOP, "»"));
        verify.selectOptions("表示ページセレクトボックスはページ番号「1」のみの選択肢1つとなる",
                listPage.pageJumpSelect(ProjectListPage.PAGER_TOP), List.of("1 / 1"));
    }

    // ---- 5. 入力チェック（検索条件） ----

    @Test
    @DisplayName("No.5-1 【境界値】検索キーワードが101文字の場合、最大文字数(100)に違反する")
    void case5x1() {
        seedFive();
        openList();
        openSearchForm(2);

        verify.opBypassRemoveAttribute("3. 「検索キーワード」入力欄の maxlength 属性を解除する（101文字入力のため）",
                listPage.input(ProjectListPage.KEYWORD), "maxlength");
        verify.op("4. 「検索キーワード」に半角英字101文字（a を101回）を入力する",
                () -> listPage.fill(ProjectListPage.KEYWORD, "a".repeat(101)),
                listPage.input(ProjectListPage.KEYWORD));
        pressSearch(4);

        verify.text("「検索キーワード」の入力欄の直下に「100文字以内で入力してください。」が表示される",
                listPage.fieldError(ProjectListPage.KEYWORD), MAX_100);
        verify.countIs("他の検索条件の入力欄の直下にはエラーメッセージが表示されない", listPage.filledFieldErrors(), 1);
        tableUnchanged();
    }

    @Test
    @DisplayName("No.5-2 同じ項目内で複数の入力値チェックに違反した場合、より小さな番号のエラーメッセージ1つのみを表示する")
    void case5x2() {
        seedFive();
        openList();
        openSearchForm(2);

        verify.opBypassRemoveAttribute("3. 「検索キーワード」入力欄の maxlength 属性を解除する（101文字入力のため）",
                listPage.input(ProjectListPage.KEYWORD), "maxlength");
        verify.op("4. 「検索キーワード」に、使用できない文字（半角カタカナ「ｱ」）を含む101文字を入力する",
                () -> listPage.fill(ProjectListPage.KEYWORD, "ｱ" + "a".repeat(100)),
                listPage.input(ProjectListPage.KEYWORD));
        pressSearch(4);

        verify.text("「検索キーワード」の入力欄の直下に「100文字以内で入力してください。」が表示される（" + "「使用不可能な文字が含まれています。」は表示されない）",
                listPage.fieldError(ProjectListPage.KEYWORD), MAX_100);
        tableUnchanged();
    }

    @Test
    @DisplayName("No.5-3 検索キーワードに使用できない文字を含む場合、使用文字に違反する")
    void case5x3() {
        seedFive();
        openList();
        openSearchForm(2);

        verify.op("3. 「検索キーワード」に半角カタカナを含む「ｱｲｳ」を入力する",
                () -> listPage.fill(ProjectListPage.KEYWORD, "ｱｲｳ"),
                listPage.input(ProjectListPage.KEYWORD));
        pressSearch(3);

        verify.text("「検索キーワード」の入力欄の直下に「使用不可能な文字が含まれています。」が表示される",
                listPage.fieldError(ProjectListPage.KEYWORD), ILLEGAL_CHARACTER);
        tableUnchanged();
    }

    @Test
    @DisplayName("No.5-4 開始日(自)が日付として解釈できない場合、日付形式に違反する")
    void case5x4() {
        seedFive();
        openList();
        openSearchForm(2);

        verify.opBypassSetAttribute("3. 「開始日(自)」入力欄の type 属性を date から text へ変更する（不正な日付入力のため）",
                listPage.input(ProjectListPage.START_DATE_FROM), "type", "text");
        verify.op("4. 「開始日(自)」に「2026/13/01」を入力する",
                () -> listPage.fill(ProjectListPage.START_DATE_FROM, "2026/13/01"),
                listPage.input(ProjectListPage.START_DATE_FROM));
        pressSearch(4);

        verify.text("「開始日(自)」の入力欄の直下に「無効な日付です。」が表示される",
                listPage.fieldError(ProjectListPage.START_DATE_FROM), INVALID_DATE);
        tableUnchanged();
    }

    @Test
    @DisplayName("No.5-5 開始日(至)が日付として解釈できない場合、日付形式に違反する")
    void case5x5() {
        seedFive();
        openList();
        openSearchForm(2);

        verify.opBypassSetAttribute("3. 「開始日(至)」入力欄の type 属性を date から text へ変更する（不正な日付入力のため）",
                listPage.input(ProjectListPage.START_DATE_TO), "type", "text");
        verify.op("4. 「開始日(至)」に「2026/02/30」（カレンダーに存在しない日付）を入力する",
                () -> listPage.fill(ProjectListPage.START_DATE_TO, "2026/02/30"),
                listPage.input(ProjectListPage.START_DATE_TO));
        pressSearch(4);

        verify.text("「開始日(至)」の入力欄の直下に「無効な日付です。」が表示される",
                listPage.fieldError(ProjectListPage.START_DATE_TO), INVALID_DATE);
        tableUnchanged();
    }

    @Test
    @DisplayName("No.5-6 開始日(至)が開始日(自)より前の場合、日付下限に違反する")
    void case5x6() {
        seedFive();
        openList();
        openSearchForm(2);

        verify.op("3. 「開始日(自)」に 2026/12/01 を入力する",
                () -> listPage.fill(ProjectListPage.START_DATE_FROM, "2026-12-01"),
                listPage.input(ProjectListPage.START_DATE_FROM));
        verify.op("3. 「開始日(至)」に 2026/11/30 を入力する",
                () -> listPage.fill(ProjectListPage.START_DATE_TO, "2026-11-30"),
                listPage.input(ProjectListPage.START_DATE_TO));
        pressSearch(3);

        verify.text("「開始日(至)」の入力欄の直下に「開始日(自)以降の日付を入力してください。」が表示される",
                listPage.fieldError(ProjectListPage.START_DATE_TO), "開始日(自)以降の日付を入力してください。");
        tableUnchanged();
    }

    @Test
    @DisplayName("No.5-7 終了日(至)が終了日(自)より前の場合、日付下限に違反する")
    void case5x7() {
        seedFive();
        openList();
        openSearchForm(2);

        verify.op("3. 「終了日(自)」に 2027/03/01 を入力する",
                () -> listPage.fill(ProjectListPage.END_DATE_FROM, "2027-03-01"),
                listPage.input(ProjectListPage.END_DATE_FROM));
        verify.op("3. 「終了日(至)」に 2027/02/28 を入力する",
                () -> listPage.fill(ProjectListPage.END_DATE_TO, "2027-02-28"),
                listPage.input(ProjectListPage.END_DATE_TO));
        pressSearch(3);

        verify.text("「終了日(至)」の入力欄の直下に「終了日(自)以降の日付を入力してください。」が表示される",
                listPage.fieldError(ProjectListPage.END_DATE_TO), "終了日(自)以降の日付を入力してください。");
        tableUnchanged();
    }

    @Test
    @DisplayName("No.5-8 登録日(至)が登録日(自)より前の場合、日付下限に違反する")
    void case5x8() {
        seedFive();
        openList();
        openSearchForm(2);

        verify.op("3. 「登録日(自)」に 2026/08/10 を入力する",
                () -> listPage.fill(ProjectListPage.REGISTERED_DATE_FROM, "2026-08-10"),
                listPage.input(ProjectListPage.REGISTERED_DATE_FROM));
        verify.op("3. 「登録日(至)」に 2026/08/09 を入力する",
                () -> listPage.fill(ProjectListPage.REGISTERED_DATE_TO, "2026-08-09"),
                listPage.input(ProjectListPage.REGISTERED_DATE_TO));
        pressSearch(3);

        verify.text("「登録日(至)」の入力欄の直下に「登録日(自)以降の日付を入力してください。」が表示される",
                listPage.fieldError(ProjectListPage.REGISTERED_DATE_TO), "登録日(自)以降の日付を入力してください。");
        tableUnchanged();
    }

    @Test
    @DisplayName("No.5-9 【境界値】登録者が51文字の場合、最大文字数(50)に違反する")
    void case5x9() {
        seedFive();
        openList();
        openSearchForm(2);

        verify.opBypassRemoveAttribute("3. 「登録者」入力欄の maxlength 属性を解除する（51文字入力のため）",
                listPage.input(ProjectListPage.REGISTRANT), "maxlength");
        verify.op("4. 「登録者」に半角英字51文字（a を51回）を入力する",
                () -> listPage.fill(ProjectListPage.REGISTRANT, "a".repeat(51)),
                listPage.input(ProjectListPage.REGISTRANT));
        pressSearch(4);

        verify.text("「登録者」の入力欄の直下に「50文字以内で入力してください。」が表示される",
                listPage.fieldError(ProjectListPage.REGISTRANT), MAX_50);
        tableUnchanged();
    }

    @Test
    @DisplayName("No.5-10 登録者に使用できない文字を含む場合、使用文字に違反する")
    void case5x10() {
        seedFive();
        openList();
        openSearchForm(2);

        verify.op("3. 「登録者」に半角カタカナを含む「ﾀﾛｳ」を入力する",
                () -> listPage.fill(ProjectListPage.REGISTRANT, "ﾀﾛｳ"),
                listPage.input(ProjectListPage.REGISTRANT));
        pressSearch(3);

        verify.text("「登録者」の入力欄の直下に「使用不可能な文字が含まれています。」が表示される",
                listPage.fieldError(ProjectListPage.REGISTRANT), ILLEGAL_CHARACTER);
        tableUnchanged();
    }

    @Test
    @DisplayName("No.5-11 【境界値】登録部署が51文字の場合、最大文字数(50)に違反する")
    void case5x11() {
        seedFive();
        openList();
        openSearchForm(2);

        verify.opBypassRemoveAttribute("3. 「登録部署」入力欄の maxlength 属性を解除する（51文字入力のため）",
                listPage.input(ProjectListPage.REGISTRANT_DEPARTMENT), "maxlength");
        verify.op("4. 「登録部署」に半角英字51文字（a を51回）を入力する",
                () -> listPage.fill(ProjectListPage.REGISTRANT_DEPARTMENT, "a".repeat(51)),
                listPage.input(ProjectListPage.REGISTRANT_DEPARTMENT));
        pressSearch(4);

        verify.text("「登録部署」の入力欄の直下に「50文字以内で入力してください。」が表示される",
                listPage.fieldError(ProjectListPage.REGISTRANT_DEPARTMENT), MAX_50);
        tableUnchanged();
    }

    @Test
    @DisplayName("No.5-12 【境界値】取引先が201文字の場合、最大文字数(200)に違反する")
    void case5x12() {
        seedFive();
        openList();
        openSearchForm(2);

        verify.opBypassRemoveAttribute("3. 「取引先」入力欄の maxlength 属性を解除する（201文字入力のため）",
                listPage.input(ProjectListPage.CLIENT), "maxlength");
        verify.op("4. 「取引先」に半角英字201文字（a を201回）を入力する",
                () -> listPage.fill(ProjectListPage.CLIENT, "a".repeat(201)),
                listPage.input(ProjectListPage.CLIENT));
        pressSearch(4);

        verify.text("「取引先」の入力欄の直下に「200文字以内で入力してください。」が表示される",
                listPage.fieldError(ProjectListPage.CLIENT), MAX_200);
        tableUnchanged();
    }

    @Test
    @DisplayName("No.5-13 取引先に使用できない文字を含む場合、使用文字に違反する")
    void case5x13() {
        seedFive();
        openList();
        openSearchForm(2);

        verify.op("3. 「取引先」に半角カタカナを含む「ｱﾙﾌｧ」を入力する",
                () -> listPage.fill(ProjectListPage.CLIENT, "ｱﾙﾌｧ"),
                listPage.input(ProjectListPage.CLIENT));
        pressSearch(3);

        verify.text("「取引先」の入力欄の直下に「使用不可能な文字が含まれています。」が表示される",
                listPage.fieldError(ProjectListPage.CLIENT), ILLEGAL_CHARACTER);
        tableUnchanged();
    }

    @Test
    @DisplayName("No.5-14 チェック違反のあった全ての画面項目にエラーメッセージを表示する")
    void case5x14() {
        seedFive();
        openList();
        openSearchForm(2);

        verify.opBypassRemoveAttribute("3. 「検索キーワード」入力欄の maxlength 属性を解除する（101文字入力のため）",
                listPage.input(ProjectListPage.KEYWORD), "maxlength");
        verify.opBypassRemoveAttribute("3. 「登録者」入力欄の maxlength 属性を解除する（51文字入力のため）",
                listPage.input(ProjectListPage.REGISTRANT), "maxlength");
        verify.op("4. 「検索キーワード」に半角英字101文字を入力する",
                () -> listPage.fill(ProjectListPage.KEYWORD, "a".repeat(101)),
                listPage.input(ProjectListPage.KEYWORD));
        verify.op("4. 「登録者」に半角英字51文字を入力する",
                () -> listPage.fill(ProjectListPage.REGISTRANT, "a".repeat(51)),
                listPage.input(ProjectListPage.REGISTRANT));
        pressSearch(4);

        verify.text("「検索キーワード」の入力欄の直下に「100文字以内で入力してください。」が表示される",
                listPage.fieldError(ProjectListPage.KEYWORD), MAX_100);
        verify.text("「登録者」の入力欄の直下に「50文字以内で入力してください。」が同時に表示される",
                listPage.fieldError(ProjectListPage.REGISTRANT), MAX_50);
        tableUnchanged();
    }

    @Test
    @DisplayName("No.5-15 入力値チェック違反後に再度検索を実施すると、既存のエラーメッセージが削除されてから再チェックされる")
    void case5x15() {
        seedFive();
        openList();
        openSearchForm(2);

        verify.op("3. 「検索キーワード」に半角カタカナを含む「ｱｲｳ」を入力する",
                () -> listPage.fill(ProjectListPage.KEYWORD, "ｱｲｳ"),
                listPage.input(ProjectListPage.KEYWORD));
        pressSearch(3);
        verify.text("4. 「検索キーワード」の入力欄の直下に「使用不可能な文字が含まれています。」が表示される",
                listPage.fieldError(ProjectListPage.KEYWORD), ILLEGAL_CHARACTER);

        verify.transitionCleared("「検索キーワード」の直下にエラーメッセージが表示されている",
                listPage.filledFieldError(ProjectListPage.KEYWORD),
                "5. 「検索キーワード」を空欄に戻し、「登録者」に半角カタカナを含む「ﾀﾛｳ」を入力して「検索」を押下する", () -> {
                    listPage.fill(ProjectListPage.KEYWORD, "");
                    listPage.fill(ProjectListPage.REGISTRANT, "ﾀﾛｳ");
                    listPage.pressSearch();
                }, listPage.searchPanelBody(), "5. 「検索キーワード」の直下のエラーメッセージが消える");
        verify.text("5. 「登録者」の入力欄の直下だけに「使用不可能な文字が含まれています。」が表示される",
                listPage.fieldError(ProjectListPage.REGISTRANT), ILLEGAL_CHARACTER);
        verify.countIs("5. 表示されているエラーメッセージは「登録者」の1件だけである", listPage.filledFieldErrors(), 1);
        tableUnchanged();
    }

    // ---- 6. 外部入出力（CSVダウンロード） ----

    @Test
    @DisplayName("No.6-1 CSVダウンロードで表示中の案件情報が既定の様式・列順・ファイル名で出力される")
    void case6x1() {
        seedFive();
        openList();

        verify.csvDownloaded(
                "2. ファイル名「案件情報一覧.csv」でダウンロードされ、"
                        + "【機械検証】バイト列が CSV期待_既定検索_管理コード降順.csv（1456バイト）と一致する",
                CSV_FILE_NAME, unitData(CSV_DEFAULT), () -> listPage.pressCsvDownload());
    }

    @Test
    @DisplayName("No.6-2 ダウンロード対象は押下時点で取得済みの案件情報一覧（全ページ）である")
    void case6x2() {
        seedPagination();
        openList();

        verify.op("2. テーブル上部の「表示件数」で「10件」を選択する",
                () -> listPage.selectPageSize(ProjectListPage.PAGER_TOP, "10"),
                listPage.pageSizeSelect(ProjectListPage.PAGER_TOP));
        verify.gridRowsByKey("2. テーブルには ID: 80〜71 の10件だけが表示される", listPage.grid(),
                ProjectListPage.COL_CODE, descendingKeys(80, 71));
        verify.text("2. 取得データ件数は「取得データ件数: 80件」である",
                listPage.dataCountLabel(ProjectListPage.PAGER_TOP), "取得データ件数: 80件");

        verify.csvDownloadedBodyRows(
                "3. ファイル名「案件情報一覧.csv」でダウンロードされ、"
                        + "【機械検証】ボディ行が80行である（表示中の1ページ分ではなく取得済みの全ページ分が出力されている）",
                CSV_FILE_NAME, 80, () -> listPage.pressCsvDownload());
    }

    @Test
    @DisplayName("No.6-3 CSVのボディ行の順序と列の順序は、表示中のテーブルのソート順・列順に従う")
    void case6x3() {
        seedFive();
        openList();
        openSearchForm(2);
        verify.op("3. ステータスの「クローズ」にもチェックを入れる", () -> listPage.check(ProjectListPage.ST_CLOSED),
                listPage.input(ProjectListPage.ST_CLOSED));
        pressSearch(3);

        verify.gridRowsByKey("3. テーブルに5件（ID: 5・4・3・2・1）が管理コードの降順で表示される", listPage.grid(),
                ProjectListPage.COL_CODE, ALL_KEYS);
        verify.csvDownloaded(
                "4. 【機械検証】ダウンロードしたファイルのバイト列が CSV期待_全件_管理コード降順.csv"
                        + "（1606バイト）と一致する（ボディ行が管理コードの降順で5行、列順はテーブルの現在の列順と同じ24列）",
                CSV_FILE_NAME, unitData(CSV_ALL), () -> listPage.pressCsvDownload());
    }

    @Test
    @DisplayName("No.6-4 案件情報テーブルのデータが0件の場合、ヘッダー行のみのCSVファイルが出力される")
    void case6x4() {
        seedFive();
        openList();
        openSearchForm(2);

        verify.op("3. 「検索キーワード」に「該当しない文字列」を入力する",
                () -> listPage.fill(ProjectListPage.KEYWORD, "該当しない文字列"),
                listPage.input(ProjectListPage.KEYWORD));
        pressSearch(3);
        verify.text("3. 「該当するデータがありません」が表示される", listPage.noRowsOverlay(), NO_ROWS);

        verify.csvDownloaded(
                "4. ファイル名「案件情報一覧.csv」でダウンロードされ、" + "【機械検証】バイト列が CSV期待_0件.csv（263バイト）と一致する",
                CSV_FILE_NAME, unitData(CSV_EMPTY), () -> listPage.pressCsvDownload());
    }

    // ---- 7. 認証・認可／サーバー処理の異常分岐 ----

    @Test
    @DisplayName("No.7-1 検索時にログインの有効期限が切れている場合、未保存の検索条件は破棄され案件情報一覧画面へ遷移する")
    void case7x1() {
        seedFive();
        openList();
        openSearchForm(2);

        verify.op("3. 「検索キーワード」に「破棄確認」を入力する（この時点では検索を実行しない）",
                () -> listPage.fill(ProjectListPage.KEYWORD, "破棄確認"),
                listPage.input(ProjectListPage.KEYWORD));
        verify.op("4. ブラウザが保持するセッションCookieを削除する（ログインの有効期限が切れた状態にする）",
                () -> listPage.clearSessionCookies());
        verify.machineEquals("【機械検証】手順4の前後で、ブラウザが保持するセッションCookieが削除されている", "0",
                String.valueOf(listPage.sessionCookieCount()));

        verify.opPress("5. 「検索」を押下する", listPage.searchButton(), () -> {
            listPage.pressSearch();
            page.waitForURL(loginPage.url());
        }, loginPage.form());
        verify.visible("5. 検索は実行されずログイン画面が表示される", loginPage.form());

        verify.opPressWithInput("6. SM0001 のユーザID・パスワードを入力し「ログイン」を押下する", () -> {
            loginPage.fillUserId(GENERAL_USER);
            loginPage.fillPassword(PASSWORD);
        }, loginPage.loginButton(), () -> {
            loginPage.loginButton().click();
            page.waitForURL(baseUrl() + ProjectListPage.PATH);
        }, listPage.grid().root());
        verify.urlIs("6. 案件情報一覧画面 /projects へ遷移する", listPage.url());
        verify.opPress("「案件検索」アコーディオンを開く", listPage.searchAccordionHead(),
                () -> listPage.toggleSearchAccordion(), listPage.searchPanelBody());
        verify.value("「検索キーワード」は空欄であり、手順3で入力した「破棄確認」は破棄されている",
                listPage.input(ProjectListPage.KEYWORD), "");
    }

    @Test
    @DisplayName("No.7-2 検索でデータベース例外が発生した場合、エラー画面に【サーバー内部エラー】が表示される")
    void case7x2() {
        seedFive();
        openList();
        verify.opArmFault("2. （障害シーム）「データベース例外（案件検索）」を有効化する",
                ProjectSearchFaultSeamConfig.DB_EXCEPTION_ON_SEARCH);
        openSearchForm(3);
        verify.opPress("3. 「検索」を押下する", listPage.searchButton(), () -> {
            listPage.pressSearch();
            page.waitForURL(url -> url.contains("/error"));
        }, errorPage.card());

        verify.text("エラータイトル「An Error Occurred」が表示される", errorPage.errorTitle(),
                "An Error Occurred");
        verify.text("エラー内容「エラーが発生しました。」が表示される", errorPage.errorMessage(), "エラーが発生しました。");
        verify.absent("画面にスタックトレース・例外クラス名などアプリケーション内部の詳細情報は表示されない", errorPage.card(),
                errorPage.internalDetails());
        verify.faultFired("【機械検証】障害シーム「データベース例外（案件検索）」が発火した",
                ProjectSearchFaultSeamConfig.DB_EXCEPTION_ON_SEARCH);
    }
}
