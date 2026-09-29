package testgen.e2e.tests;

import com.microsoft.playwright.Locator;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.Import;
import testgen.e2e.ProjectE2eTest;
import testgen.e2e.data.E2eTestFiles;
import testgen.e2e.pages.CommonHeaderPage;
import testgen.e2e.pages.DepartmentMasterPage;
import testgen.e2e.pages.ErrorPage;
import testgen.e2e.pages.LoginPage;
import testgen.e2e.pages.ProjectListPage;
import testgen.e2e.seams.DepartmentMasterFaultSeamConfig;
import testgen.e2e.support.E2eFeature;
import testgen.e2e.support.evidence.EvidenceV2;

/**
 * 部署マスタ管理画面（機能 {@code 23_部署マスタ管理}）の E2E テスト。
 *
 * <p>
 * テストケース仕様書 {@code 23_部署マスタ管理/23_部署マスタ管理_テスト仕様書.xlsx} の全ケースを実装する。 CSV
 * の洗い替え（アップロード）・ダウンロードと、サーバー内部の異常分岐（障害シーム）を扱う。
 */
@EvidenceV2
@Import(DepartmentMasterFaultSeamConfig.class)
@E2eFeature("23_部署マスタ管理")
class DepartmentMasterE2eTest extends ProjectE2eTest {

    /** システム管理者 SM0002（管理 花子／管理部）。 */
    private static final String ADMIN_USER = "SM0002";

    /** ベースラインのユーザに共通の開発用ダミーパスワード。 */
    private static final String PASSWORD = "Passw0rd!";

    /** 部署マスタ80件のシード（ページネーション確認用）。 */
    private static final String SEED_PAGINATION = "23_部署マスタ管理/data/ページネーション_部署マスタ.csv";

    /** 部署マスタ3件のシード（部署名の大小比較でソートを確認する）。 */
    private static final String SEED_SORT = "23_部署マスタ管理/data/ソート確認_部署マスタ.csv";

    /** 部署マスタ3件のシード（部署ID: 1 の部署名が全角50文字）。 */
    private static final String SEED_LONG_NAME = "23_部署マスタ管理/data/長い部署名.csv";

    /** CSV 期待値正本: 部署マスタのダウンロード（部署ID昇順）。 */
    private static final String CSV_EXPECTED_ASC = "23_部署マスタ管理/data/CSV期待_部署マスタDL.csv";

    /** CSV 期待値正本: 部署マスタのダウンロード（部署ID降順）。 */
    private static final String CSV_EXPECTED_DESC = "23_部署マスタ管理/data/CSV期待_部署マスタDL降順.csv";

    /** アップロード CSV: 洗い替え（正常3件）。 */
    private static final String UPLOAD_REPLACE = "23_部署マスタ管理/data/洗い替え_正常.csv";

    /** アップロード CSV: 洗い替え（境界値正常3件）。 */
    private static final String UPLOAD_BOUNDARY = "23_部署マスタ管理/data/洗い替え_境界値正常.csv";

    /** アップロード CSV: ボディ行0件。 */
    private static final String UPLOAD_NO_DATA = "23_部署マスタ管理/data/形式_データ0件.csv";

    /** アップロード CSV: 既定の様式でない（閉じられていない囲み文字を含む）。 */
    private static final String UPLOAD_BAD_FORMAT = "23_部署マスタ管理/data/形式_様式不正.csv";

    /** ダウンロードされる CSV のファイル名。 */
    private static final String CSV_FILE_NAME = "部署マスタ.csv";

    /** ファイル形式チェック違反のエラータイトル。 */
    private static final String FORMAT_ERROR_TITLE = "CSVアップロードに失敗しました";

    /** 入力値チェック違反のエラータイトル。 */
    private static final String INPUT_ERROR_TITLE = "CSVの内容にエラーがあります。詳細は以下の通りです。";

    /** 生成する CSV のヘッダー行（部署マスタ）。 */
    private static final String CSV_HEADER = "部署ID,部署名";

    private LoginPage loginPage;
    private CommonHeaderPage commonHeader;
    private DepartmentMasterPage departmentPage;
    private ErrorPage errorPage;


    @BeforeEach
    void setUpPages() {
        loginPage = new LoginPage(page, baseUrl());
        commonHeader = new CommonHeaderPage(page, baseUrl());
        departmentPage = new DepartmentMasterPage(page, baseUrl());
        errorPage = new ErrorPage(page, baseUrl());
        loginPage.signIn(ADMIN_USER, PASSWORD, ProjectListPage.PATH);
    }

    // ---- テストデータ・共通手順 ----

    /** 昇順に並ぶ行キーの一覧を作る。 */
    private static List<String> ascendingKeys(int from, int to) {
        List<String> keys = new ArrayList<>();
        for (int id = from; id <= to; id++) {
            keys.add(String.valueOf(id));
        }
        return keys;
    }

    /** 手順「/master/departments を開く」。 */
    private void openDepartmentMaster(int stepNo) {
        verify.opOpen(stepNo + ". /master/departments を開く", () -> departmentPage.open());
    }

    /** CSV をアップロードする（ファイル選択で即座にフォーム送信される）。 */
    private void uploadCsv(String stepDesc, Path file, Locator result) {
        verify.opChooseFileSubmits(stepDesc, departmentPage.csvUploadButton(), file, result);
    }

    /** ベースラインの3件が表示されたままであることを確認する。 */
    private void baselineTableUnchanged() {
        verify.gridRowsByKey("テーブルには3件（部署ID: 1〜3）が表示されたままである", departmentPage.grid(),
                DepartmentMasterPage.COL_ID, ascendingKeys(1, 3));
        verify.gridCellText("部署ID: 1 の部署名が「営業部」である", departmentPage.grid(), 0,
                DepartmentMasterPage.COL_NAME, "営業部");
        verify.gridCellText("部署ID: 2 の部署名が「開発部」である", departmentPage.grid(), 1,
                DepartmentMasterPage.COL_NAME, "開発部");
        verify.gridCellText("部署ID: 3 の部署名が「管理部」である", departmentPage.grid(), 2,
                DepartmentMasterPage.COL_NAME, "管理部");
    }

    /** 取得データ件数がテーブル上部に指定件数で表示されることを確認する。 */
    private void dataCountIs(int count) {
        verify.text("取得データ件数に「取得データ件数: " + count + "件」が表示される",
                departmentPage.dataCountLabel(DepartmentMasterPage.PAGER_TOP),
                "取得データ件数: " + count + "件");
    }

    /** 洗い替え後の3件（部署ID: 3・101・102）が一覧表示されることを確認する。 */
    private void replacedThreeRows() {
        verify.gridRowsByKey("部署マスタ管理画面に3件が部署IDの昇順で一覧表示される（部署ID: 1・2 は削除されている）",
                departmentPage.grid(), DepartmentMasterPage.COL_ID, List.of("3", "101", "102"));
        verify.gridCellText("部署ID: 3 の部署名が「管理部」から「総務部」に置き換わっている", departmentPage.grid(), 0,
                DepartmentMasterPage.COL_NAME, "総務部");
        verify.gridCellText("部署ID: 101 の部署名が「第一営業部」である", departmentPage.grid(), 1,
                DepartmentMasterPage.COL_NAME, "第一営業部");
        verify.gridCellText("部署ID: 102 の部署名が「第二営業部」である", departmentPage.grid(), 2,
                DepartmentMasterPage.COL_NAME, "第二営業部");
    }

    // ---- 1. 画面仕様・初期表示 ----

    @Test
    @DisplayName("No.1-1 部署マスタ管理画面の初期表示（画面項目・一覧・取得データ件数・表示件数の初期値・エラー表示の非表示）")
    void case1x1() {
        openDepartmentMaster(1);

        verify.visible("画面タイトル「部署マスタ管理画面」が表示される",
                commonHeader.screenTitleOf(DepartmentMasterPage.SCREEN_TITLE));
        verify.visible("「CSVダウンロード」ボタンが表示される", departmentPage.csvDownloadButton());
        verify.visible("「CSVアップロード」ボタンが表示される", departmentPage.csvUploadButton());
        verify.gridColumnHeaders("列ヘッダーは「部署ID」「部署名」である", departmentPage.grid(),
                DepartmentMasterPage.DEFAULT_HEADERS);
        verify.gridRowsByKey("部署マスタテーブルに3件が一覧表示される", departmentPage.grid(),
                DepartmentMasterPage.COL_ID, ascendingKeys(1, 3));
        verify.text("テーブル上部に「取得データ件数: 3件」が表示される",
                departmentPage.dataCountLabel(DepartmentMasterPage.PAGER_TOP), "取得データ件数: 3件");
        verify.text("テーブル下部に「取得データ件数: 3件」が表示される",
                departmentPage.dataCountLabel(DepartmentMasterPage.PAGER_BOTTOM), "取得データ件数: 3件");
        verify.value("表示件数セレクトボックスは「30件」が選択されている",
                departmentPage.pageSizeSelect(DepartmentMasterPage.PAGER_TOP), "30");
        verify.selectOptions("表示件数の選択肢は「10件」「20件」「30件」「全て」である",
                departmentPage.pageSizeSelect(DepartmentMasterPage.PAGER_TOP),
                List.of("10件", "20件", "30件", "全て"));
        verify.visible("テーブル上部に表示ページセレクトボックスが表示される",
                departmentPage.pageJumpSelect(DepartmentMasterPage.PAGER_TOP));
        verify.visible("テーブル上部にページネーションが表示される",
                departmentPage.pagerNums(DepartmentMasterPage.PAGER_TOP));
        verify.visible("テーブル下部に表示ページセレクトボックスが表示される",
                departmentPage.pageJumpSelect(DepartmentMasterPage.PAGER_BOTTOM));
        verify.visible("テーブル下部にページネーションが表示される",
                departmentPage.pagerNums(DepartmentMasterPage.PAGER_BOTTOM));
        verify.absent("エラー表示（エラータイトル・エラー内容）は表示されない（【機械検証】要素が0件である）",
                departmentPage.csvUploadButton(), departmentPage.errorArea());
    }

    @Test
    @DisplayName("No.1-2 既定レイアウト（列順序・列幅・初期ソート順が部署IDの昇順）")
    void case1x2() {
        openDepartmentMaster(1);

        verify.gridColumnHeaders("列は左から「部署ID」「部署名」の順に並ぶ", departmentPage.grid(),
                DepartmentMasterPage.DEFAULT_HEADERS);
        final int half = departmentPage.gridBodyWidthPx() / 2;
        verify.gridColumnWidthIs("「部署ID」列の幅はテーブルの横幅を2等分した幅である", departmentPage.grid(),
                DepartmentMasterPage.COL_ID, half);
        verify.gridColumnWidthIs("「部署名」列の幅はテーブルの横幅を2等分した幅である", departmentPage.grid(),
                DepartmentMasterPage.COL_NAME, half);
        verify.gridRowsByKey("テーブルの行は部署IDの昇順に並ぶ", departmentPage.grid(),
                DepartmentMasterPage.COL_ID, ascendingKeys(1, 3));
        verify.gridCellText("先頭行が 部署ID: 1「営業部」である", departmentPage.grid(), 0,
                DepartmentMasterPage.COL_NAME, "営業部");
        verify.gridCellText("末尾行が 部署ID: 3「管理部」である", departmentPage.grid(), 2,
                DepartmentMasterPage.COL_NAME, "管理部");
    }

    @Test
    @DisplayName("No.1-3 表示内容が列幅を超える場合、先頭から列幅に収まる範囲の文字列に三点リーダー(…)を加えて表示する")
    void case1x3() {
        replaceDepartments(unitData(SEED_LONG_NAME));
        openDepartmentMaster(1);

        verify.op("2. 「部署名」列の幅を最小（80px）まで狭める",
                () -> departmentPage.grid().shrinkColumnToMinimum(DepartmentMasterPage.COL_NAME),
                departmentPage.grid().headerCell(DepartmentMasterPage.COL_NAME));

        verify.textTruncated("2. 部署ID: 1 の「部署名」が列幅に収まる範囲で切り詰められ、末尾に三点リーダー(…)が付く",
                departmentPage.grid().cell(0, DepartmentMasterPage.COL_NAME));
        verify.gridCellText("部署ID列の値（先頭行）は切り詰められずそのまま表示される", departmentPage.grid(), 0,
                DepartmentMasterPage.COL_ID, "1");
        verify.gridCellText("部署ID列の値（末尾行）は切り詰められずそのまま表示される", departmentPage.grid(), 2,
                DepartmentMasterPage.COL_ID, "3");
    }

    @Test
    @DisplayName("No.1-4 テーブルの行が画面に収まらない場合、メインビューポート内で縦スクロールする")
    void case1x4() {
        replaceDepartments(unitData(SEED_PAGINATION));
        openDepartmentMaster(1);

        verify.gridCellText("1. 1ページ目の先頭行は 部署ID: 1 である", departmentPage.grid(), 0,
                DepartmentMasterPage.COL_ID, "1");
        verify.gridRowsByKey(
                "2. 縦にスクロールし、1ページ目の末尾行 部署ID: 30 まで到達できる" + "（【機械検証】末尾行 部署ID: 30 の次に行が存在しない）",
                departmentPage.grid(), DepartmentMasterPage.COL_ID, ascendingKeys(1, 30));
    }

    // ---- 2. 画面イベント処理（テーブル操作） ----

    @Test
    @DisplayName("No.2-1 列ヘッダーのドラッグ＆ドロップで列の順序を変更できる（BにドロップするとBの左側にAが挿入される）")
    void case2x1() {
        openDepartmentMaster(1);

        verify.gridColumnHeaders("1. 列が左から「部署ID」「部署名」の順である", departmentPage.grid(),
                DepartmentMasterPage.DEFAULT_HEADERS);
        verify.op("2. 「部署名」の列ヘッダーをドラッグし、「部署ID」の列ヘッダーの上でドロップする",
                () -> departmentPage.grid().moveColumn(DepartmentMasterPage.COL_NAME,
                        DepartmentMasterPage.COL_ID),
                departmentPage.grid().headerCell(DepartmentMasterPage.COL_NAME));

        verify.gridColumnHeaders("2. 列が左から「部署名」「部署ID」の順に変わる", departmentPage.grid(),
                List.of("部署名", "部署ID"));
        verify.gridCellText("先頭行の「部署名」が「営業部」で表示される（値が列の入れ替えに追従する）", departmentPage.grid(), 0,
                DepartmentMasterPage.COL_NAME, "営業部");
        verify.gridCellText("先頭行の「部署ID」が「1」で表示される（値が列の入れ替えに追従する）", departmentPage.grid(), 0,
                DepartmentMasterPage.COL_ID, "1");
    }

    @Test
    @DisplayName("No.2-2 列ヘッダーの境界をドラッグして列幅を変更でき、最小値は80pxである")
    void case2x2() {
        openDepartmentMaster(1);

        // 広げた列の右端が画面外になると手順3で境界線を掴めないため、テーブルの横幅から
        // 40px 内側までを目標幅にする（もう一方の列は最小幅 80px になり、合計は横幅を超える）。
        final int widened = departmentPage.gridBodyWidthPx() - 40;
        verify.op("2. 「部署ID」列と「部署名」列の間の境界線をドラッグして右へ広げる",
                () -> departmentPage.grid().resizeColumnTo(DepartmentMasterPage.COL_ID, widened),
                departmentPage.grid().root());
        verify.gridColumnHeaders("2. テーブルの列が画面に収まらなくなり、テーブル内で横スクロールが発生する", departmentPage.grid(),
                DepartmentMasterPage.DEFAULT_HEADERS);

        verify.op("3. 同じ境界線をドラッグして左へ、80pxより狭くなるまで動かす",
                () -> departmentPage.grid().shrinkColumnToMinimum(DepartmentMasterPage.COL_ID),
                departmentPage.grid().headerCell(DepartmentMasterPage.COL_ID));
        verify.gridColumnWidthIs("【機械検証】手順3の後の「部署ID」列の幅が 80px である（それより狭くならない）",
                departmentPage.grid(), DepartmentMasterPage.COL_ID, 80);
    }

    @Test
    @DisplayName("No.2-3 列ヘッダーのダブルクリックでソート状態が昇順→降順→ソートなしの順に切り替わる")
    void case2x3() {
        replaceDepartments(unitData(SEED_SORT));
        openDepartmentMaster(1);

        verify.gridCellText("1. 先頭行が 部署ID: 1「デルタ課」である", departmentPage.grid(), 0,
                DepartmentMasterPage.COL_NAME, "デルタ課");
        verify.gridCellText("1. 末尾行が 部署ID: 3「チャーリー課」である", departmentPage.grid(), 2,
                DepartmentMasterPage.COL_NAME, "チャーリー課");

        verify.op("2. 「部署名」の列ヘッダーをダブルクリックする",
                () -> departmentPage.doubleClickHeader(DepartmentMasterPage.COL_NAME),
                departmentPage.grid().headerCell(DepartmentMasterPage.COL_NAME));
        verify.gridRowsByKey("2. 部署名の昇順に並び替わり、部署ID: 2・3・1 の順になる", departmentPage.grid(),
                DepartmentMasterPage.COL_ID, List.of("2", "3", "1"));
        verify.gridCellText("2. 先頭行が 部署ID: 2「アルファ課」になる", departmentPage.grid(), 0,
                DepartmentMasterPage.COL_NAME, "アルファ課");
        verify.gridCellText("2. 2行目が 部署ID: 3「チャーリー課」になる", departmentPage.grid(), 1,
                DepartmentMasterPage.COL_NAME, "チャーリー課");
        verify.gridCellText("2. 末尾行が 部署ID: 1「デルタ課」になる", departmentPage.grid(), 2,
                DepartmentMasterPage.COL_NAME, "デルタ課");

        verify.op("3. もう一度「部署名」の列ヘッダーをダブルクリックする",
                () -> departmentPage.doubleClickHeader(DepartmentMasterPage.COL_NAME),
                departmentPage.grid().headerCell(DepartmentMasterPage.COL_NAME));
        verify.gridCellText("3. 部署名の降順に並び替わり、先頭行が 部署ID: 1「デルタ課」になる", departmentPage.grid(), 0,
                DepartmentMasterPage.COL_NAME, "デルタ課");
        verify.gridCellText("3. 末尾行が 部署ID: 2「アルファ課」になる", departmentPage.grid(), 2,
                DepartmentMasterPage.COL_NAME, "アルファ課");

        verify.op("4. もう一度「部署名」の列ヘッダーをダブルクリックする",
                () -> departmentPage.doubleClickHeader(DepartmentMasterPage.COL_NAME),
                departmentPage.grid().headerCell(DepartmentMasterPage.COL_NAME));
        verify.gridRowsByKey("4. ソートなしとなり、既定の並び順である部署ID列の昇順に戻る", departmentPage.grid(),
                DepartmentMasterPage.COL_ID, ascendingKeys(1, 3));
        verify.gridCellText("4. 先頭行が 部署ID: 1「デルタ課」である", departmentPage.grid(), 0,
                DepartmentMasterPage.COL_NAME, "デルタ課");
        verify.gridCellText("4. 末尾行が 部署ID: 3「チャーリー課」である", departmentPage.grid(), 2,
                DepartmentMasterPage.COL_NAME, "チャーリー課");
    }

    @Test
    @DisplayName("No.2-4 別の列ヘッダーをダブルクリックすると既存のソートが解除され、複数列によるソートは行われない")
    void case2x4() {
        replaceDepartments(unitData(SEED_SORT));
        openDepartmentMaster(1);

        verify.op("2. 「部署名」の列ヘッダーをダブルクリックして部署名の昇順にする",
                () -> departmentPage.doubleClickHeader(DepartmentMasterPage.COL_NAME),
                departmentPage.grid().headerCell(DepartmentMasterPage.COL_NAME));
        verify.gridCellText("2. 先頭行が 部署ID: 2「アルファ課」になる", departmentPage.grid(), 0,
                DepartmentMasterPage.COL_NAME, "アルファ課");
        verify.visible("2. 「部署名」列にソート中の意匠が付く",
                departmentPage.sortAscendingIcon(DepartmentMasterPage.COL_NAME));

        verify.transitionHide("「部署名」列にソート中の意匠が表示されている",
                departmentPage.sortAscendingIcon(DepartmentMasterPage.COL_NAME),
                "3. 「部署ID」の列ヘッダーをダブルクリックする",
                () -> departmentPage.doubleClickHeader(DepartmentMasterPage.COL_ID),
                departmentPage.grid().headerCell(DepartmentMasterPage.COL_ID),
                "3. 「部署名」列のソート中の意匠が消える");
        verify.visible("3. 「部署ID」列にソート中の意匠が付く",
                departmentPage.sortAscendingIcon(DepartmentMasterPage.COL_ID));
        verify.gridRowsByKey("3. テーブルは部署IDの昇順に並び、部署名によるソートは併用されない", departmentPage.grid(),
                DepartmentMasterPage.COL_ID, ascendingKeys(1, 3));
        verify.gridCellText("3. 先頭行が 部署ID: 1「デルタ課」である", departmentPage.grid(), 0,
                DepartmentMasterPage.COL_NAME, "デルタ課");
        verify.gridCellText("3. 末尾行が 部署ID: 3「チャーリー課」である", departmentPage.grid(), 2,
                DepartmentMasterPage.COL_NAME, "チャーリー課");
    }

    @Test
    @DisplayName("No.2-5 表示件数の変更とページ送りができ、テーブル上部・下部の操作部品が連動する")
    void case2x5() {
        replaceDepartments(unitData(SEED_PAGINATION));
        openDepartmentMaster(1);

        verify.op("2. テーブル上部の「表示件数」で「10件」を選択する",
                () -> departmentPage.selectPageSize(DepartmentMasterPage.PAGER_TOP, "10"),
                departmentPage.pageSizeSelect(DepartmentMasterPage.PAGER_TOP));
        verify.gridRowsByKey("2. テーブルに 部署ID: 1〜10 の10件が表示される", departmentPage.grid(),
                DepartmentMasterPage.COL_ID, ascendingKeys(1, 10));
        verify.value("2. テーブル下部の「表示件数」も「10件」に連動して変わる",
                departmentPage.pageSizeSelect(DepartmentMasterPage.PAGER_BOTTOM), "10");

        verify.op("3. テーブル下部の「表示ページ」で「2 / 8」を選択する",
                () -> departmentPage.selectPage(DepartmentMasterPage.PAGER_BOTTOM, 1),
                departmentPage.pageJumpSelect(DepartmentMasterPage.PAGER_BOTTOM));
        verify.gridRowsByKey("3. テーブルに 部署ID: 11〜20 の10件が表示される", departmentPage.grid(10),
                DepartmentMasterPage.COL_ID, ascendingKeys(11, 20));
        verify.value("3. テーブル上部の「表示ページ」も「2 / 8」に連動して変わる",
                departmentPage.pageJumpSelect(DepartmentMasterPage.PAGER_TOP), "1");
        verify.text("3. テーブル上部のページネーションは2ページ目を強調表示する",
                departmentPage.activePagerButton(DepartmentMasterPage.PAGER_TOP), "2");
        verify.text("3. テーブル下部のページネーションは2ページ目を強調表示する",
                departmentPage.activePagerButton(DepartmentMasterPage.PAGER_BOTTOM), "2");
    }

    @Test
    @DisplayName("No.2-6 テーブル操作は画面内で完結し、画面遷移およびデータの再取得を伴わない")
    void case2x6() {
        replaceDepartments(unitData(SEED_PAGINATION));
        openDepartmentMaster(1);

        verify.noRequestSent("【機械検証】手順2〜4の間にサーバーへのデータ取得要求が 0件である", "GET", "/master/departments",
                () -> {
                    verify.op("2. 「表示件数」で「10件」を選択する",
                            () -> departmentPage.selectPageSize(DepartmentMasterPage.PAGER_TOP,
                                    "10"),
                            departmentPage.pageSizeSelect(DepartmentMasterPage.PAGER_TOP));
                    verify.op("3. 「部署名」の列ヘッダーをダブルクリックしてソートを切り替える",
                            () -> departmentPage.doubleClickHeader(DepartmentMasterPage.COL_NAME),
                            departmentPage.grid().headerCell(DepartmentMasterPage.COL_NAME));
                    verify.op(
                            "4. ページネーションの「›」を押下する", () -> departmentPage
                                    .pressPagerButton(DepartmentMasterPage.PAGER_TOP, "›"),
                            departmentPage.pagerNums(DepartmentMasterPage.PAGER_TOP));
                });

        verify.urlIs("URL は /master/departments のまま変わらず、画面は遷移しない", departmentPage.url());
        verify.text("取得データ件数は「取得データ件数: 80件」のまま変わらない",
                departmentPage.dataCountLabel(DepartmentMasterPage.PAGER_TOP), "取得データ件数: 80件");
    }

    // ---- 3. 外部入出力（CSVダウンロード） ----

    @Test
    @DisplayName("No.3-1 CSVダウンロードで全ての部署マスタレコードが既定の様式・列順・ファイル名で出力される")
    void case3x1() {
        openDepartmentMaster(1);

        verify.csvDownloaded(
                "2. ファイル名「部署マスタ.csv」でダウンロードされ、" + "【機械検証】バイト列が CSV期待_部署マスタDL.csv（62バイト）と一致する",
                CSV_FILE_NAME, unitData(CSV_EXPECTED_ASC), () -> departmentPage.pressCsvDownload());
    }

    @Test
    @DisplayName("No.3-2 CSVのボディ行の並び順が表示中テーブルのソート順に従う")
    void case3x2() {
        openDepartmentMaster(1);

        verify.op(
                "2. 「部署ID」の列ヘッダーをダブルクリックして部署IDの降順に並び替える"
                        + "（初期表示で部署ID列に昇順が適用済みのため、1回のダブルクリックで降順になる）",
                () -> departmentPage.doubleClickHeader(DepartmentMasterPage.COL_ID),
                departmentPage.grid().headerCell(DepartmentMasterPage.COL_ID));
        verify.gridCellText("2. テーブルの先頭行が 部署ID: 3「管理部」になる", departmentPage.grid(), 0,
                DepartmentMasterPage.COL_NAME, "管理部");
        verify.gridCellText("2. テーブルの末尾行が 部署ID: 1「営業部」になる", departmentPage.grid(), 2,
                DepartmentMasterPage.COL_NAME, "営業部");

        verify.csvDownloaded(
                "3. ファイル名「部署マスタ.csv」でダウンロードされ、" + "【機械検証】バイト列が CSV期待_部署マスタDL降順.csv（62バイト）と一致する",
                CSV_FILE_NAME, unitData(CSV_EXPECTED_DESC),
                () -> departmentPage.pressCsvDownload());
    }

    // ---- 4. 外部入出力（CSVアップロード・洗い替え） ----

    @Test
    @DisplayName("No.4-1 CSVアップロードで部署マスタが洗い替えられ、一覧が再表示される")
    void case4x1() {
        openDepartmentMaster(1);
        verify.gridRowsByKey("1. テーブルに3件（部署ID: 1〜3）が表示されている", departmentPage.grid(),
                DepartmentMasterPage.COL_ID, ascendingKeys(1, 3));

        uploadCsv("2. 「CSVアップロード」を押下し、洗い替え_正常.csv を指定する", unitData(UPLOAD_REPLACE),
                departmentPage.grid().root());

        replacedThreeRows();
        dataCountIs(3);
        verify.containsText("共通ヘッダーの部署名は「総務部」に変わる（実施者 SM0002 の部署ID 3 の部署名）",
                commonHeader.departmentName(), "総務部");
        verify.absent("エラー表示（エラータイトル・エラー内容）は表示されない", departmentPage.csvUploadButton(),
                departmentPage.errorArea());
    }

    @Test
    @DisplayName("No.4-2 ファイルが指定されずにアップロードが実行された場合、洗い替えを実施せずエラーを表示する")
    void case4x2() {
        openDepartmentMaster(1);

        verify.op("2. 開発者ツール相当の合成操作で、CSVファイルを指定しないままアップロードを実行する",
                () -> departmentPage.submitUploadWithoutFile(), departmentPage.errorArea());

        verify.text("エラータイトル「CSVアップロードに失敗しました」が表示される", departmentPage.errorTitle(),
                FORMAT_ERROR_TITLE);
        verify.text("エラー内容「この項目は入力が必要です。」が表示される", departmentPage.errorMessages(), "この項目は入力が必要です。");
        baselineTableUnchanged();
    }

    @Test
    @DisplayName("No.4-3 【境界値】ファイル容量が5MBを超える場合、ファイル形式チェックに違反する")
    void case4x3() {
        openDepartmentMaster(1);

        final Path file = E2eTestFiles.csvOfSize("部署マスタ_5MB超過.csv", CSV_HEADER, "1,", "", 5242881);
        uploadCsv("2. 「CSVアップロード」を押下し、部署マスタ_5MB超過.csv を指定する", file, departmentPage.errorArea());

        verify.text("エラータイトル「CSVアップロードに失敗しました」が表示される", departmentPage.errorTitle(),
                FORMAT_ERROR_TITLE);
        verify.texts("エラー内容は「ファイルの容量が大きすぎます(5MBまで)。」の1件のみである" + "（他のファイル形式チェックのメッセージは表示されない）",
                departmentPage.errorMessages(), new String[] {"ファイルの容量が大きすぎます(5MBまで)。"});
        baselineTableUnchanged();
    }

    @Test
    @DisplayName("No.4-4 【境界値】ファイル容量が5MBちょうどの場合、容量のファイル形式チェックに違反しない")
    void case4x4() {
        openDepartmentMaster(1);

        final Path file =
                E2eTestFiles.csvOfSize("部署マスタ_5MBちょうど.csv", CSV_HEADER, "1,", "", 5242880);
        uploadCsv("2. 「CSVアップロード」を押下し、部署マスタ_5MBちょうど.csv を指定する", file, departmentPage.errorArea());

        verify.text("エラータイトル「CSVの内容にエラーがあります。詳細は以下の通りです。」が表示される" + "（容量のチェックを通過して後続の入力値チェックに進んでいる）",
                departmentPage.errorTitle(), INPUT_ERROR_TITLE);
        verify.texts(
                "エラー内容は「0002 - データ1行目 部署名: 50文字以内で入力してください。」の1件のみであり、"
                        + "「ファイルの容量が大きすぎます(5MBまで)。」は表示されない",
                departmentPage.errorMessages(),
                new String[] {"0002 - データ1行目 部署名: 50文字以内で入力してください。"});
        baselineTableUnchanged();
    }

    @Test
    @DisplayName("No.4-5 ボディ行に部署マスタレコードが1件も存在しない場合、ファイル形式チェックに違反する")
    void case4x5() {
        openDepartmentMaster(1);

        uploadCsv("2. 「CSVアップロード」を押下し、形式_データ0件.csv を指定する", unitData(UPLOAD_NO_DATA),
                departmentPage.errorArea());

        verify.text("エラータイトル「CSVアップロードに失敗しました」が表示される", departmentPage.errorTitle(),
                FORMAT_ERROR_TITLE);
        verify.text("エラー内容「CSVファイルにデータがありませんでした。」が表示される", departmentPage.errorMessages(),
                "CSVファイルにデータがありませんでした。");
        baselineTableUnchanged();
    }

    @Test
    @DisplayName("No.4-6 ファイル形式チェック違反後に再度アップロードすると、既存のエラー表示が削除されたうえで再チェックされる")
    void case4x6() {
        openDepartmentMaster(1);

        uploadCsv("1. 形式_データ0件.csv をアップロードする", unitData(UPLOAD_NO_DATA),
                departmentPage.errorArea());
        verify.text("2. エラータイトル「CSVアップロードに失敗しました」が表示される", departmentPage.errorTitle(),
                FORMAT_ERROR_TITLE);
        verify.text("2. エラー内容「CSVファイルにデータがありませんでした。」が表示される", departmentPage.errorMessages(),
                "CSVファイルにデータがありませんでした。");

        verify.transitionCleared("エラー表示が表示されている", departmentPage.errorArea(),
                "3. 「CSVアップロード」を押下し、洗い替え_正常.csv を指定する",
                () -> uploadCsv("3. 「CSVアップロード」を押下し、洗い替え_正常.csv を指定する", unitData(UPLOAD_REPLACE),
                        departmentPage.grid().root()),
                departmentPage.grid().root(), "3. エラー表示が消える");
        replacedThreeRows();
    }

    @Test
    @DisplayName("No.4-7 入力値チェック違反後に正しいCSVをアップロードすると、既存のエラーメッセージが削除されて洗い替えが実施される")
    void case4x7() {
        openDepartmentMaster(1);

        uploadCsv("1. 入力_部署ID必須.csv をアップロードする", unitData("23_部署マスタ管理/data/入力_部署ID必須.csv"),
                departmentPage.errorArea());
        verify.text("2. エラータイトル「CSVの内容にエラーがあります。詳細は以下の通りです。」が表示される", departmentPage.errorTitle(),
                INPUT_ERROR_TITLE);
        verify.text("2. エラー内容「0002 - データ1行目 部署ID: この項目は入力が必要です。」が表示される",
                departmentPage.errorMessages(), "0002 - データ1行目 部署ID: この項目は入力が必要です。");

        verify.transitionCleared("エラー表示（エラータイトル・エラー内容）が表示されている", departmentPage.errorArea(),
                "3. 「CSVアップロード」を押下し、洗い替え_正常.csv を指定する",
                () -> uploadCsv("3. 「CSVアップロード」を押下し、洗い替え_正常.csv を指定する", unitData(UPLOAD_REPLACE),
                        departmentPage.grid().root()),
                departmentPage.grid().root(), "3. エラー表示（エラータイトル・エラー内容）が消える");
        replacedThreeRows();
        dataCountIs(3);
    }

    @Test
    @DisplayName("No.4-8 既定の様式のCSVファイルでない場合、ファイル形式チェックに違反する")
    void case4x8() {
        openDepartmentMaster(1);

        uploadCsv("2. 「CSVアップロード」を押下し、形式_様式不正.csv（閉じられていない囲み文字を含む）を指定する",
                unitData(UPLOAD_BAD_FORMAT), departmentPage.errorArea());

        verify.text("エラータイトル「CSVアップロードに失敗しました」が表示される", departmentPage.errorTitle(),
                FORMAT_ERROR_TITLE);
        verify.text("エラー内容「CSVの読み取りに失敗しました。フォーマットが正しいかファイルを確認してください。」が表示される",
                departmentPage.errorMessages(), "CSVの読み取りに失敗しました。フォーマットが正しいかファイルを確認してください。");
        baselineTableUnchanged();
    }

    // ---- 5. 入力チェック（CSV取込の部署マスタレコード） ----

    /** CSV アップロードの入力値チェック違反を確認する共通手順。 */
    private void expectInputError(String fileName, String expectedMessage) {
        openDepartmentMaster(1);
        verify.gridRowsByKey("1. テーブルに3件（部署ID: 1〜3）が表示されている", departmentPage.grid(),
                DepartmentMasterPage.COL_ID, ascendingKeys(1, 3));

        uploadCsv("2. 「CSVアップロード」を押下し、" + fileName + " を指定する",
                unitData("23_部署マスタ管理/data/" + fileName), departmentPage.errorArea());

        verify.text("エラータイトル「CSVの内容にエラーがあります。詳細は以下の通りです。」が表示される", departmentPage.errorTitle(),
                INPUT_ERROR_TITLE);
        verify.text("エラー内容「" + expectedMessage + "」が表示される", departmentPage.errorMessages(),
                expectedMessage);
        baselineTableUnchanged();
    }

    @Test
    @DisplayName("No.5-1 部署IDが未入力の場合、必須入力に違反する")
    void case5x1() {
        expectInputError("入力_部署ID必須.csv", "0002 - データ1行目 部署ID: この項目は入力が必要です。");
    }

    @Test
    @DisplayName("No.5-2 部署IDが整数でない場合、整数チェックに違反する")
    void case5x2() {
        expectInputError("入力_部署ID整数以外.csv", "0002 - データ1行目 部署ID: 整数を入力してください。");
    }

    @Test
    @DisplayName("No.5-3 【境界値】部署IDが0の場合、最小数値(1)に違反する")
    void case5x3() {
        expectInputError("入力_部署ID最小数値.csv", "0002 - データ1行目 部署ID: 1以上の数値を入力してください。");
    }

    @Test
    @DisplayName("No.5-4 【境界値】部署IDが2147483648の場合、最大数値(2147483647)に違反する")
    void case5x4() {
        expectInputError("入力_部署ID最大数値.csv", "0002 - データ1行目 部署ID: 2147483647以下の数値を入力してください。");
    }

    @Test
    @DisplayName("No.5-5 部署IDが重複している場合、2回目以降の行に一意違反のエラーを表示する")
    void case5x5() {
        openDepartmentMaster(1);
        verify.gridRowsByKey("1. テーブルに3件（部署ID: 1〜3）が表示されている", departmentPage.grid(),
                DepartmentMasterPage.COL_ID, ascendingKeys(1, 3));

        uploadCsv("2. 「CSVアップロード」を押下し、入力_部署ID重複.csv を指定する",
                unitData("23_部署マスタ管理/data/入力_部署ID重複.csv"), departmentPage.errorArea());

        verify.text("エラータイトル「CSVの内容にエラーがあります。詳細は以下の通りです。」が表示される", departmentPage.errorTitle(),
                INPUT_ERROR_TITLE);
        verify.texts(
                "エラー内容は「0003 - データ2行目 部署ID: 重複して設定しないでください(7)」の1件のみであり、"
                        + "「0002 - データ1行目」で始まるエラーメッセージは表示されない",
                departmentPage.errorMessages(),
                new String[] {"0003 - データ2行目 部署ID: 重複して設定しないでください(7)"});
        baselineTableUnchanged();
    }

    @Test
    @DisplayName("No.5-6 部署名が未入力の場合、必須入力に違反する")
    void case5x6() {
        expectInputError("入力_部署名必須.csv", "0002 - データ1行目 部署名: この項目は入力が必要です。");
    }

    @Test
    @DisplayName("No.5-7 【境界値】部署名が51文字の場合、最大文字数(50)に違反する")
    void case5x7() {
        expectInputError("入力_部署名最大文字数.csv", "0002 - データ1行目 部署名: 50文字以内で入力してください。");
    }

    @Test
    @DisplayName("No.5-8 部署名に全角文字以外を含む場合、使用文字に違反する")
    void case5x8() {
        expectInputError("入力_部署名使用文字.csv", "0002 - データ1行目 部署名: 使用不可能な文字が含まれています。");
    }

    @Test
    @DisplayName("No.5-9 【境界値】部署ID 1・2147483647、部署名 全角50文字は入力値チェックに違反せず取り込まれる")
    void case5x9() {
        openDepartmentMaster(1);
        verify.gridRowsByKey("1. テーブルに3件（部署ID: 1〜3）が表示されている", departmentPage.grid(),
                DepartmentMasterPage.COL_ID, ascendingKeys(1, 3));

        uploadCsv("2. 「CSVアップロード」を押下し、洗い替え_境界値正常.csv を指定する", unitData(UPLOAD_BOUNDARY),
                departmentPage.grid().root());

        verify.absent("エラー表示（エラータイトル・エラー内容）は表示されない", departmentPage.csvUploadButton(),
                departmentPage.errorArea());
        verify.gridRowsByKey("部署マスタ管理画面に3件が部署IDの昇順で一覧表示される", departmentPage.grid(),
                DepartmentMasterPage.COL_ID, List.of("1", "3", "2147483647"));
        verify.gridCellText("部署ID: 1 の部署名が全角50文字である", departmentPage.grid(), 0,
                DepartmentMasterPage.COL_NAME, "あ".repeat(50));
        verify.gridCellText("部署ID: 3 の部署名が「零」である", departmentPage.grid(), 1,
                DepartmentMasterPage.COL_NAME, "零");
        verify.gridCellText("部署ID: 2147483647 の部署名が「壱」である", departmentPage.grid(), 2,
                DepartmentMasterPage.COL_NAME, "壱");
        dataCountIs(3);
    }

    @Test
    @DisplayName("No.5-10 同じ行の複数の項目でチェック違反がある場合、列の順序ごとに全てのエラーメッセージを表示する")
    void case5x10() {
        openDepartmentMaster(1);
        verify.gridRowsByKey("1. テーブルに3件（部署ID: 1〜3）が表示されている", departmentPage.grid(),
                DepartmentMasterPage.COL_ID, ascendingKeys(1, 3));

        uploadCsv("2. 「CSVアップロード」を押下し、入力_同一行複数項目.csv を指定する",
                unitData("23_部署マスタ管理/data/入力_同一行複数項目.csv"), departmentPage.errorArea());

        verify.text("エラータイトル「CSVの内容にエラーがあります。詳細は以下の通りです。」が表示される", departmentPage.errorTitle(),
                INPUT_ERROR_TITLE);
        verify.texts("エラー内容が部署ID・部署名の順（列の順序）で2件並ぶ", departmentPage.errorMessages(), new String[] {
                "0002 - データ1行目 部署ID: 1以上の数値を入力してください。", "0002 - データ1行目 部署名: この項目は入力が必要です。"});
        baselineTableUnchanged();
    }

    @Test
    @DisplayName("No.5-11 同じ項目内で複数の入力値チェックに違反した場合、より小さな番号のエラーメッセージ1つのみを表示する")
    void case5x11() {
        openDepartmentMaster(1);
        verify.gridRowsByKey("1. テーブルに3件（部署ID: 1〜3）が表示されている", departmentPage.grid(),
                DepartmentMasterPage.COL_ID, ascendingKeys(1, 3));

        uploadCsv("2. 「CSVアップロード」を押下し、入力_部署ID必須.csv を指定する",
                unitData("23_部署マスタ管理/data/入力_部署ID必須.csv"), departmentPage.errorArea());

        verify.text("エラータイトル「CSVの内容にエラーがあります。詳細は以下の通りです。」が表示される", departmentPage.errorTitle(),
                INPUT_ERROR_TITLE);
        verify.texts("エラー内容は「0002 - データ1行目 部署ID: この項目は入力が必要です。」の1件のみであり、" + "「整数を入力してください。」は表示されない",
                departmentPage.errorMessages(), new String[] {"0002 - データ1行目 部署ID: この項目は入力が必要です。"});
        baselineTableUnchanged();
    }

    @Test
    @DisplayName("No.5-12 CSV行番号が5桁以上の場合、0埋めをせずそのまま表示する")
    void case5x12() {
        openDepartmentMaster(1);
        verify.gridRowsByKey("1. テーブルに3件（部署ID: 1〜3）が表示されている", departmentPage.grid(),
                DepartmentMasterPage.COL_ID, ascendingKeys(1, 3));

        List<String> lines = new ArrayList<>();
        for (int id = 1; id <= 9999; id++) {
            String name = id == 9999 ? "" : "部" + E2eTestFiles.fullWidthDigits(id, 4);
            lines.add(id + "," + name);
        }
        final Path file = E2eTestFiles.csvOfLines("部署マスタ_行番号5桁.csv", CSV_HEADER, lines);
        uploadCsv("2. 「CSVアップロード」を押下し、部署マスタ_行番号5桁.csv を指定する", file, departmentPage.errorArea());

        verify.text("エラータイトル「CSVの内容にエラーがあります。詳細は以下の通りです。」が表示される", departmentPage.errorTitle(),
                INPUT_ERROR_TITLE);
        verify.text(
                "エラー内容「10000 - データ9999行目 部署名: この項目は入力が必要です。」が表示される"
                        + "（CSV行番号 10000 は5桁のため0埋めをせずそのまま表示される）",
                departmentPage.errorMessages(), "10000 - データ9999行目 部署名: この項目は入力が必要です。");
        baselineTableUnchanged();
    }

    @Test
    @DisplayName("No.5-13 入力値チェック違反後に再度アップロードすると、既存のエラーメッセージが削除されたうえで再チェックされる")
    void case5x13() {
        openDepartmentMaster(1);

        uploadCsv("1. 入力_部署ID必須.csv をアップロードする", unitData("23_部署マスタ管理/data/入力_部署ID必須.csv"),
                departmentPage.errorArea());
        verify.text("2. エラー内容「0002 - データ1行目 部署ID: この項目は入力が必要です。」が表示される",
                departmentPage.errorMessages(), "0002 - データ1行目 部署ID: この項目は入力が必要です。");

        uploadCsv("3. 「CSVアップロード」を押下し、入力_部署名必須.csv を指定する", unitData("23_部署マスタ管理/data/入力_部署名必須.csv"),
                departmentPage.errorArea());
        verify.texts("3. エラー内容が「0002 - データ1行目 部署名: この項目は入力が必要です。」だけになる" + "（手順2のエラーメッセージが消えている）",
                departmentPage.errorMessages(), new String[] {"0002 - データ1行目 部署名: この項目は入力が必要です。"});
        baselineTableUnchanged();
    }

    // ---- 6. サーバー・APIサービス処理／認証・認可（異常分岐） ----

    @Test
    @DisplayName("No.6-1 洗い替え中にデータベース例外が発生した場合、洗い替え前の状態に戻りエラー画面に【サーバー内部エラー】が表示される")
    void case6x1() {
        verify.opArmFault("1. （障害シーム）「データベース例外（部署マスタ洗い替え）」を有効化する",
                DepartmentMasterFaultSeamConfig.DB_EXCEPTION_ON_REPLACE);
        openDepartmentMaster(2);

        uploadCsv("3. 「CSVアップロード」を押下し、洗い替え_正常.csv を指定する", unitData(UPLOAD_REPLACE),
                errorPage.card());

        verify.text("3. エラータイトル「An Error Occurred」が表示される", errorPage.errorTitle(),
                "An Error Occurred");
        verify.text("3. エラー内容「エラーが発生しました。」が表示される", errorPage.errorMessage(), "エラーが発生しました。");
        verify.faultFired("【機械検証】障害シーム「データベース例外（部署マスタ洗い替え）」が発火した",
                DepartmentMasterFaultSeamConfig.DB_EXCEPTION_ON_REPLACE);

        openDepartmentMaster(4);
        verify.gridRowsByKey("4. 3件（部署ID: 1〜3）が表示され、洗い替え前の状態に戻っている" + "（部署ID: 101・102 は登録されていない）",
                departmentPage.grid(), DepartmentMasterPage.COL_ID, ascendingKeys(1, 3));
        dataCountIs(3);
    }

    @Test
    @DisplayName("No.6-2 CSVダウンロード時にログインの有効期限が切れている場合、ダウンロードは実施されず案件情報一覧画面へ遷移する")
    void case6x2() {
        openDepartmentMaster(1);

        verify.op("2. ブラウザが保持するセッションCookieを削除する（ログインの有効期限が切れた状態にする）",
                () -> departmentPage.clearSessionCookies());
        verify.machineEquals("手順2の後、ブラウザが保持するセッションCookieが削除されている", "0",
                String.valueOf(departmentPage.sessionCookieCount()));

        verify.noDownload("【機械検証】手順3を通じてファイルのダウンロードが発生していない", () -> verify
                .opPress("3. 「CSVダウンロード」を押下する", departmentPage.csvDownloadButton(), () -> {
                    departmentPage.pressCsvDownloadWithoutWaiting();
                    page.waitForURL(loginPage.url());
                }, loginPage.form()));
        verify.visible("3. CSVファイルはダウンロードされず、ログイン画面が表示される", loginPage.form());

        verify.noDownload("【機械検証】手順4を通じてファイルのダウンロードが発生していない",
                () -> verify.opPressWithInput("4. SM0002 のユーザID・パスワードを入力し「ログイン」を押下する", () -> {
                    loginPage.fillUserId(ADMIN_USER);
                    loginPage.fillPassword(PASSWORD);
                }, loginPage.loginButton(), () -> {
                    loginPage.loginButton().click();
                    page.waitForURL(baseUrl() + ProjectListPage.PATH);
                }, commonHeader.screenTitleOf(ProjectListPage.SCREEN_TITLE)));
        verify.urlIs("4. 部署マスタ管理画面ではなく案件情報一覧画面 /projects へ遷移する", baseUrl() + ProjectListPage.PATH);
    }

    @Test
    @DisplayName("No.6-3 洗い替え時にログインの有効期限が切れている場合、洗い替えは実施されず案件情報一覧画面へ遷移する")
    void case6x3() {
        openDepartmentMaster(1);

        verify.op("2. ブラウザが保持するセッションCookieを削除する（ログインの有効期限が切れた状態にする）",
                () -> departmentPage.clearSessionCookies());
        verify.machineEquals("手順2の後、ブラウザが保持するセッションCookieが削除されている", "0",
                String.valueOf(departmentPage.sessionCookieCount()));

        uploadCsv("3. 「CSVアップロード」を押下して洗い替え_正常.csv を指定する", unitData(UPLOAD_REPLACE),
                loginPage.form());
        verify.visible("3. 部署マスタ管理画面は再表示されずログイン画面が表示される", loginPage.form());

        verify.opPressWithInput("4. SM0002 のユーザID・パスワードを入力し「ログイン」を押下する", () -> {
            loginPage.fillUserId(ADMIN_USER);
            loginPage.fillPassword(PASSWORD);
        }, loginPage.loginButton(), () -> {
            loginPage.loginButton().click();
            page.waitForURL(baseUrl() + ProjectListPage.PATH);
        }, commonHeader.screenTitleOf(ProjectListPage.SCREEN_TITLE));
        verify.urlIs("4. 部署マスタ管理画面ではなく案件情報一覧画面 /projects へ遷移する", baseUrl() + ProjectListPage.PATH);

        openDepartmentMaster(5);
        verify.gridRowsByKey("5. 3件（部署ID: 1〜3）が表示され、洗い替えは実施されていない" + "（部署ID: 101・102 は登録されていない）",
                departmentPage.grid(), DepartmentMasterPage.COL_ID, ascendingKeys(1, 3));
    }
}
