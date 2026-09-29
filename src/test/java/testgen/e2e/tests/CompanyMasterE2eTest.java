package testgen.e2e.tests;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
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
import testgen.e2e.pages.CompanyMasterPage;
import testgen.e2e.pages.ErrorPage;
import testgen.e2e.pages.LoginPage;
import testgen.e2e.pages.ProjectListPage;
import testgen.e2e.seams.CompanyMasterFaultSeamConfig;
import testgen.e2e.support.E2eFeature;
import testgen.e2e.support.evidence.EvidenceV2;

/**
 * 会社マスタ管理画面（機能 {@code 21_会社マスタ管理}）の E2E テスト。
 *
 * <p>
 * テストケース仕様書 {@code 21_会社マスタ管理/21_会社マスタ管理_テスト仕様書.xlsx} の全ケースを実装する。 CSV
 * の洗い替え（アップロード）・ダウンロードと、サーバー内部の異常分岐（障害シーム）を扱う。
 */
@EvidenceV2
@Import(CompanyMasterFaultSeamConfig.class)
@E2eFeature("21_会社マスタ管理")
class CompanyMasterE2eTest extends ProjectE2eTest {

    /** システム管理者 SM0002（管理 花子／管理部）。 */
    private static final String ADMIN_USER = "SM0002";

    /** ベースラインのユーザに共通の開発用ダミーパスワード。 */
    private static final String PASSWORD = "Passw0rd!";

    /** 会社マスタ0件のシード。 */
    private static final String SEED_EMPTY = "21_会社マスタ管理/data/会社マスタ0件.csv";

    /** 会社マスタ80件のシード（ページネーション確認用）。 */
    private static final String SEED_PAGINATION = "00_共通部品/data/ページネーション_会社マスタ.csv";

    /** CSV 期待値正本: 会社マスタのダウンロード（会社ID昇順）。 */
    private static final String CSV_EXPECTED_ASC = "00_共通部品/data/CSV様式_期待_会社マスタDL.csv";

    /** CSV 期待値正本: 会社マスタのダウンロード（会社ID降順）。 */
    private static final String CSV_EXPECTED_DESC = "21_会社マスタ管理/data/CSV期待_会社マスタDL降順.csv";

    /** アップロード CSV: 洗い替え（正常5件）。 */
    private static final String UPLOAD_REPLACE = "21_会社マスタ管理/data/洗い替え_正常.csv";

    /** アップロード CSV: 洗い替え（境界値正常2件）。 */
    private static final String UPLOAD_BOUNDARY = "21_会社マスタ管理/data/洗い替え_境界値正常.csv";

    /** アップロード CSV: ボディ行0件。 */
    private static final String UPLOAD_NO_DATA = "21_会社マスタ管理/data/形式_データ0件.csv";

    /** ダウンロードされる CSV のファイル名。 */
    private static final String CSV_FILE_NAME = "会社マスタ.csv";

    /** ファイル形式チェック違反のエラータイトル。 */
    private static final String FORMAT_ERROR_TITLE = "CSVアップロードに失敗しました";

    /** 入力値チェック違反のエラータイトル。 */
    private static final String INPUT_ERROR_TITLE = "CSVの内容にエラーがあります。詳細は以下の通りです。";

    /** データ0件のときにテーブル本体へ表示される案内。 */
    private static final String NO_ROWS = "該当するデータがありません";

    /** 生成する CSV のヘッダー行（会社マスタ）。 */
    private static final String CSV_HEADER = "会社ID,会社名";

    private LoginPage loginPage;
    private CommonHeaderPage commonHeader;
    private CompanyMasterPage companyPage;
    private ErrorPage errorPage;


    @BeforeEach
    void setUpPages() {
        loginPage = new LoginPage(page, baseUrl());
        commonHeader = new CommonHeaderPage(page, baseUrl());
        companyPage = new CompanyMasterPage(page, baseUrl());
        errorPage = new ErrorPage(page, baseUrl());
        loginPage.signIn(ADMIN_USER, PASSWORD, ProjectListPage.PATH);
    }

    // ---- テストデータ・共通手順 ----

    /** 会社マスタを0件にする。 */
    private void seedEmpty() {
        replaceCompanies(unitData(SEED_EMPTY));
    }

    /** 会社マスタを80件（会社ID: 1〜80）にする。 */
    private void seedPagination() {
        replaceCompanies(unitData(SEED_PAGINATION));
    }

    /** 昇順に並ぶ行キーの一覧を作る。 */
    private static List<String> ascendingKeys(int from, int to) {
        List<String> keys = new ArrayList<>();
        for (int id = from; id <= to; id++) {
            keys.add(String.valueOf(id));
        }
        return keys;
    }

    /** ファイルの実バイト数。 */
    private static long sizeOf(Path file) {
        try {
            return Files.size(file);
        } catch (IOException e) {
            throw new UncheckedIOException("ファイルのバイト数を取得できません: " + file, e);
        }
    }

    /** 手順1「/master/companies を開く」。 */
    private void openCompanyMaster(int stepNo) {
        verify.opOpen(stepNo + ". /master/companies を開く", () -> companyPage.open());
    }

    /** CSV をアップロードする（ファイル選択で即座にフォーム送信される）。 */
    private void uploadCsv(String stepDesc, Path file, com.microsoft.playwright.Locator result) {
        verify.opChooseFileSubmits(stepDesc, companyPage.csvUploadButton(), file, result);
    }

    /** ベースラインの12件が表示されたままであることを確認する。 */
    private void baselineTableUnchanged() {
        verify.gridRowsByKey("テーブルには12件（会社ID: 1〜12）が表示されたままである", companyPage.grid(),
                CompanyMasterPage.COL_ID, ascendingKeys(1, 12));
        verify.gridCellText("先頭行は 会社ID: 1「株式会社アルファ」である", companyPage.grid(), 0,
                CompanyMasterPage.COL_NAME, "株式会社アルファ");
        verify.gridCellText("末尾行は 会社ID: 12「株式会社リマ」である", companyPage.grid(), 11,
                CompanyMasterPage.COL_NAME, "株式会社リマ");
    }

    /** 取得データ件数がテーブル上部に指定件数で表示されることを確認する。 */
    private void dataCountIs(int count) {
        verify.text("取得データ件数に「取得データ件数: " + count + "件」が表示される",
                companyPage.dataCountLabel(CompanyMasterPage.PAGER_TOP), "取得データ件数: " + count + "件");
    }

    /** 洗い替え後の5件（会社ID: 101〜105）が一覧表示されることを確認する。 */
    private void replacedFiveRows() {
        verify.gridRowsByKey("会社マスタ管理画面に5件（会社ID: 101〜105）が一覧表示される", companyPage.grid(),
                CompanyMasterPage.COL_ID, List.of("101", "102", "103", "104", "105"));
        verify.gridCellText("1行目が「株式会社ノベンバー」である", companyPage.grid(), 0, CompanyMasterPage.COL_NAME,
                "株式会社ノベンバー");
        verify.gridCellText("5行目が「株式会社ロメオ」である", companyPage.grid(), 4, CompanyMasterPage.COL_NAME,
                "株式会社ロメオ");
    }

    // ---- 1. 画面仕様・初期表示 ----

    @Test
    @DisplayName("No.1-1 会社マスタ管理画面の初期表示（画面項目・一覧・取得データ件数・表示件数の初期値・エラー表示の非表示）")
    void case1x1() {
        openCompanyMaster(1);

        verify.visible("画面タイトル「会社マスタ管理画面」が表示される",
                commonHeader.screenTitleOf(CompanyMasterPage.SCREEN_TITLE));
        verify.visible("「CSVダウンロード」ボタンが表示される", companyPage.csvDownloadButton());
        verify.visible("「CSVアップロード」ボタンが表示される", companyPage.csvUploadButton());
        verify.gridColumnHeaders("列ヘッダーは「会社ID」「会社名」である", companyPage.grid(),
                CompanyMasterPage.DEFAULT_HEADERS);
        verify.gridRowsByKey("会社マスタテーブルに12件が一覧表示される", companyPage.grid(), CompanyMasterPage.COL_ID,
                ascendingKeys(1, 12));
        verify.text("テーブル上部に「取得データ件数: 12件」が表示される",
                companyPage.dataCountLabel(CompanyMasterPage.PAGER_TOP), "取得データ件数: 12件");
        verify.text("テーブル下部に「取得データ件数: 12件」が表示される",
                companyPage.dataCountLabel(CompanyMasterPage.PAGER_BOTTOM), "取得データ件数: 12件");
        verify.value("表示件数セレクトボックスは「30件」が選択されている",
                companyPage.pageSizeSelect(CompanyMasterPage.PAGER_TOP), "30");
        verify.selectOptions("表示件数の選択肢は「10件」「20件」「30件」「全て」である",
                companyPage.pageSizeSelect(CompanyMasterPage.PAGER_TOP),
                List.of("10件", "20件", "30件", "全て"));
        verify.visible("テーブル上部に表示ページセレクトボックスが表示される",
                companyPage.pageJumpSelect(CompanyMasterPage.PAGER_TOP));
        verify.visible("テーブル上部にページネーションが表示される", companyPage.pagerNums(CompanyMasterPage.PAGER_TOP));
        verify.visible("テーブル下部に表示ページセレクトボックスが表示される",
                companyPage.pageJumpSelect(CompanyMasterPage.PAGER_BOTTOM));
        verify.visible("テーブル下部にページネーションが表示される",
                companyPage.pagerNums(CompanyMasterPage.PAGER_BOTTOM));
        verify.absent("エラー表示（エラータイトル・エラー内容）は表示されない（【機械検証】要素が0件である）", companyPage.csvUploadButton(),
                companyPage.errorArea());
    }

    @Test
    @DisplayName("No.1-2 既定レイアウト（列順序・列幅・初期ソート順が会社IDの昇順）")
    void case1x2() {
        openCompanyMaster(1);

        verify.gridColumnHeaders("列は左から「会社ID」「会社名」の順に並ぶ", companyPage.grid(),
                CompanyMasterPage.DEFAULT_HEADERS);
        final int half = companyPage.gridBodyWidthPx() / 2;
        verify.gridColumnWidthIs("「会社ID」列の幅はテーブルの横幅を2等分した幅である", companyPage.grid(),
                CompanyMasterPage.COL_ID, half);
        verify.gridColumnWidthIs("「会社名」列の幅はテーブルの横幅を2等分した幅である", companyPage.grid(),
                CompanyMasterPage.COL_NAME, half);
        verify.gridRowsByKey("テーブルの行は会社IDの昇順に並ぶ", companyPage.grid(), CompanyMasterPage.COL_ID,
                ascendingKeys(1, 12));
        verify.gridCellText("先頭行が 会社ID: 1「株式会社アルファ」である", companyPage.grid(), 0,
                CompanyMasterPage.COL_NAME, "株式会社アルファ");
        verify.gridCellText("末尾行が 会社ID: 12「株式会社リマ」である", companyPage.grid(), 11,
                CompanyMasterPage.COL_NAME, "株式会社リマ");
    }

    @Test
    @DisplayName("No.1-3 表示内容が列幅を超える場合、先頭から列幅に収まる範囲の文字列に三点リーダー(…)を加えて表示する")
    void case1x3() {
        openCompanyMaster(1);

        verify.op("2. 「会社名」列の幅を最小（80px）まで狭める",
                () -> companyPage.grid().shrinkColumnToMinimum(CompanyMasterPage.COL_NAME),
                companyPage.grid().headerCell(CompanyMasterPage.COL_NAME));

        verify.textTruncated("2. 会社ID: 3 の行の「会社名」が列幅に収まる範囲で切り詰められ、末尾に三点リーダー(…)が付く",
                companyPage.grid().cell(2, CompanyMasterPage.COL_NAME));
        verify.gridCellText("会社ID列の値（先頭行）は切り詰められずそのまま表示される", companyPage.grid(), 0,
                CompanyMasterPage.COL_ID, "1");
        verify.gridCellText("会社ID列の値（末尾行）は切り詰められずそのまま表示される", companyPage.grid(), 11,
                CompanyMasterPage.COL_ID, "12");
    }

    @Test
    @DisplayName("No.1-4 会社マスタのレコードが0件の場合の表示")
    void case1x4() {
        seedEmpty();
        openCompanyMaster(1);

        verify.gridColumnHeaders("列ヘッダー「会社ID」「会社名」はデータが存在する場合と同様に表示される", companyPage.grid(),
                CompanyMasterPage.DEFAULT_HEADERS);
        verify.absent("列ボディに行が1件も無く、列間の区切りの線が表示されない", companyPage.grid().verticalViewport(),
                companyPage.dataRows());
        verify.text("列ボディに中央寄せで「該当するデータがありません」が表示される", companyPage.noRowsOverlay(), NO_ROWS);
        dataCountIs(0);
        verify.disabled("ページネーションの «（先頭へ）がクリック不可である",
                companyPage.pagerButton(CompanyMasterPage.PAGER_TOP, "«"));
        verify.disabled("ページネーションの ‹（前へ）がクリック不可である",
                companyPage.pagerButton(CompanyMasterPage.PAGER_TOP, "‹"));
        verify.disabled("ページネーションのページ番号「1」がクリック不可である",
                companyPage.pagerButton(CompanyMasterPage.PAGER_TOP, "1"));
        verify.disabled("ページネーションの ›（次へ）がクリック不可である",
                companyPage.pagerButton(CompanyMasterPage.PAGER_TOP, "›"));
        verify.disabled("ページネーションの »（末尾へ）がクリック不可である",
                companyPage.pagerButton(CompanyMasterPage.PAGER_TOP, "»"));
        verify.selectOptions("表示ページセレクトボックスはページ番号「1」のみの選択肢1つとなる",
                companyPage.pageJumpSelect(CompanyMasterPage.PAGER_TOP), List.of("1 / 1"));
    }

    @Test
    @DisplayName("No.1-5 テーブルの行が画面に収まらない場合、メインビューポート内で縦スクロールする")
    void case1x5() {
        seedPagination();
        openCompanyMaster(1);

        verify.gridCellText("1. 1ページ目の先頭行は 会社ID: 1 である", companyPage.grid(), 0,
                CompanyMasterPage.COL_ID, "1");
        verify.gridRowsByKey(
                "2. 縦にスクロールし、1ページ目の末尾行 会社ID: 30 まで到達できる" + "（【機械検証】末尾行 会社ID: 30 の次に行が存在しない）",
                companyPage.grid(), CompanyMasterPage.COL_ID, ascendingKeys(1, 30));
    }

    // ---- 2. 画面イベント処理（テーブル操作） ----

    @Test
    @DisplayName("No.2-1 列ヘッダーのドラッグ＆ドロップで列の順序を変更できる（BにドロップするとBの左側にAが挿入される）")
    void case2x1() {
        openCompanyMaster(1);

        verify.gridColumnHeaders("1. 列が左から「会社ID」「会社名」の順である", companyPage.grid(),
                CompanyMasterPage.DEFAULT_HEADERS);
        verify.op("2. 「会社名」の列ヘッダーをドラッグし、「会社ID」の列ヘッダーの上でドロップする",
                () -> companyPage.grid().moveColumn(CompanyMasterPage.COL_NAME,
                        CompanyMasterPage.COL_ID),
                companyPage.grid().headerCell(CompanyMasterPage.COL_NAME));

        verify.gridColumnHeaders("2. 列が左から「会社名」「会社ID」の順に変わる", companyPage.grid(),
                List.of("会社名", "会社ID"));
        verify.gridCellText("先頭行の「会社名」が「株式会社アルファ」で表示される（値が列の入れ替えに追従する）", companyPage.grid(), 0,
                CompanyMasterPage.COL_NAME, "株式会社アルファ");
        verify.gridCellText("先頭行の「会社ID」が「1」で表示される（値が列の入れ替えに追従する）", companyPage.grid(), 0,
                CompanyMasterPage.COL_ID, "1");
    }

    @Test
    @DisplayName("No.2-2 列ヘッダーの境界をドラッグして列幅を変更でき、最小値は80pxである")
    void case2x2() {
        openCompanyMaster(1);

        // 広げた列の右端が画面外になると手順3で境界線を掴めないため、テーブルの横幅から
        // 40px 内側までを目標幅にする（もう一方の列は最小幅 80px になり、合計は横幅を超える）。
        final int widened = companyPage.gridBodyWidthPx() - 40;
        verify.op("2. 「会社ID」列と「会社名」列の間の境界線をドラッグして右へ広げる",
                () -> companyPage.grid().resizeColumnTo(CompanyMasterPage.COL_ID, widened),
                companyPage.grid().root());
        verify.gridColumnHeaders("2. テーブルの列が画面に収まらなくなり、テーブル内で横スクロールが発生する", companyPage.grid(),
                CompanyMasterPage.DEFAULT_HEADERS);

        verify.op("3. 同じ境界線をドラッグして左へ、80pxより狭くなるまで動かす",
                () -> companyPage.grid().shrinkColumnToMinimum(CompanyMasterPage.COL_ID),
                companyPage.grid().headerCell(CompanyMasterPage.COL_ID));
        verify.gridColumnWidthIs("【機械検証】手順3の後の「会社ID」列の幅が 80px である（それより狭くならない）", companyPage.grid(),
                CompanyMasterPage.COL_ID, 80);
    }

    @Test
    @DisplayName("No.2-3 列ヘッダーのダブルクリックでソート状態が昇順→降順→ソートなしの順に切り替わる")
    void case2x3() {
        openCompanyMaster(1);

        verify.op("2. 「会社名」の列ヘッダーをダブルクリックする",
                () -> companyPage.doubleClickHeader(CompanyMasterPage.COL_NAME),
                companyPage.grid().headerCell(CompanyMasterPage.COL_NAME));
        verify.gridCellText("2. 会社名の昇順に並び替わり、先頭行が 会社ID: 1「株式会社アルファ」になる", companyPage.grid(), 0,
                CompanyMasterPage.COL_NAME, "株式会社アルファ");
        verify.gridCellText("2. 2行目が 会社ID: 9「株式会社インディア」になる（会社IDの昇順とは異なる並び）", companyPage.grid(), 1,
                CompanyMasterPage.COL_NAME, "株式会社インディア");
        verify.gridCellText("2. 末尾行が 会社ID: 12「株式会社リマ」になる", companyPage.grid(), 11,
                CompanyMasterPage.COL_NAME, "株式会社リマ");

        verify.op("3. もう一度「会社名」の列ヘッダーをダブルクリックする",
                () -> companyPage.doubleClickHeader(CompanyMasterPage.COL_NAME),
                companyPage.grid().headerCell(CompanyMasterPage.COL_NAME));
        verify.gridCellText("3. 会社名の降順に並び替わり、先頭行が 会社ID: 12「株式会社リマ」になる", companyPage.grid(), 0,
                CompanyMasterPage.COL_NAME, "株式会社リマ");
        verify.gridCellText("3. 2行目が 会社ID: 8「株式会社ホテル」になる", companyPage.grid(), 1,
                CompanyMasterPage.COL_NAME, "株式会社ホテル");
        verify.gridCellText("3. 末尾行が 会社ID: 1「株式会社アルファ」になる", companyPage.grid(), 11,
                CompanyMasterPage.COL_NAME, "株式会社アルファ");

        verify.op("4. もう一度「会社名」の列ヘッダーをダブルクリックする",
                () -> companyPage.doubleClickHeader(CompanyMasterPage.COL_NAME),
                companyPage.grid().headerCell(CompanyMasterPage.COL_NAME));
        verify.gridRowsByKey("4. ソートなしとなり、既定の並び順である会社ID列の昇順に戻る", companyPage.grid(),
                CompanyMasterPage.COL_ID, ascendingKeys(1, 12));
    }

    @Test
    @DisplayName("No.2-4 別の列ヘッダーをダブルクリックすると既存のソートが解除され、複数列によるソートは行われない")
    void case2x4() {
        openCompanyMaster(1);

        verify.op("2. 「会社名」の列ヘッダーをダブルクリックして会社名の昇順にする",
                () -> companyPage.doubleClickHeader(CompanyMasterPage.COL_NAME),
                companyPage.grid().headerCell(CompanyMasterPage.COL_NAME));
        verify.gridCellText("2. 会社名の昇順に並び替わる（2行目が 会社ID: 9「株式会社インディア」）", companyPage.grid(), 1,
                CompanyMasterPage.COL_NAME, "株式会社インディア");
        verify.visible("2. 「会社名」列にソート中の意匠が付く",
                companyPage.sortAscendingIcon(CompanyMasterPage.COL_NAME));

        verify.transitionHide("「会社名」列にソート中の意匠が表示されている",
                companyPage.sortAscendingIcon(CompanyMasterPage.COL_NAME),
                "3. 「会社ID」の列ヘッダーをダブルクリックする",
                () -> companyPage.doubleClickHeader(CompanyMasterPage.COL_ID),
                companyPage.grid().headerCell(CompanyMasterPage.COL_ID), "3. 「会社名」列のソート中の意匠が消える");
        verify.visible("3. 「会社ID」列にソート中の意匠が付く",
                companyPage.sortAscendingIcon(CompanyMasterPage.COL_ID));
        verify.gridRowsByKey("3. テーブルは会社IDの昇順に並び、会社名によるソートは併用されない", companyPage.grid(),
                CompanyMasterPage.COL_ID, ascendingKeys(1, 12));
    }

    @Test
    @DisplayName("No.2-5 表示件数の変更とページ送りができ、テーブル上部・下部の操作部品が連動する")
    void case2x5() {
        seedPagination();
        openCompanyMaster(1);

        verify.op("2. テーブル上部の「表示件数」で「10件」を選択する",
                () -> companyPage.selectPageSize(CompanyMasterPage.PAGER_TOP, "10"),
                companyPage.pageSizeSelect(CompanyMasterPage.PAGER_TOP));
        verify.gridRowsByKey("2. テーブルに 会社ID: 1〜10 の10件が表示される", companyPage.grid(),
                CompanyMasterPage.COL_ID, ascendingKeys(1, 10));
        verify.value("2. テーブル下部の「表示件数」も「10件」に連動して変わる",
                companyPage.pageSizeSelect(CompanyMasterPage.PAGER_BOTTOM), "10");

        verify.op("3. テーブル下部の「表示ページ」で「2 / 8」を選択する",
                () -> companyPage.selectPage(CompanyMasterPage.PAGER_BOTTOM, 1),
                companyPage.pageJumpSelect(CompanyMasterPage.PAGER_BOTTOM));
        verify.gridRowsByKey("3. テーブルに 会社ID: 11〜20 の10件が表示される", companyPage.grid(10),
                CompanyMasterPage.COL_ID, ascendingKeys(11, 20));
        verify.value("3. テーブル上部の「表示ページ」も「2 / 8」に連動して変わる",
                companyPage.pageJumpSelect(CompanyMasterPage.PAGER_TOP), "1");
        verify.text("3. テーブル上部のページネーションは2ページ目を強調表示する",
                companyPage.activePagerButton(CompanyMasterPage.PAGER_TOP), "2");
        verify.text("3. テーブル下部のページネーションは2ページ目を強調表示する",
                companyPage.activePagerButton(CompanyMasterPage.PAGER_BOTTOM), "2");
    }

    @Test
    @DisplayName("No.2-6 テーブル操作は画面内で完結し、画面遷移およびデータの再取得を伴わない")
    void case2x6() {
        seedPagination();
        openCompanyMaster(1);

        verify.noRequestSent("【機械検証】手順2〜4の間にサーバーへのデータ取得要求が 0件である", "GET", "/master/companies",
                () -> {
                    verify.op("2. 「表示件数」で「10件」を選択する",
                            () -> companyPage.selectPageSize(CompanyMasterPage.PAGER_TOP, "10"),
                            companyPage.pageSizeSelect(CompanyMasterPage.PAGER_TOP));
                    verify.op("3. 「会社名」の列ヘッダーをダブルクリックしてソートを切り替える",
                            () -> companyPage.doubleClickHeader(CompanyMasterPage.COL_NAME),
                            companyPage.grid().headerCell(CompanyMasterPage.COL_NAME));
                    verify.op("4. ページネーションの「›」を押下する",
                            () -> companyPage.pressPagerButton(CompanyMasterPage.PAGER_TOP, "›"),
                            companyPage.pagerNums(CompanyMasterPage.PAGER_TOP));
                });

        verify.urlIs("URL は /master/companies のまま変わらず、画面は遷移しない", companyPage.url());
        verify.text("取得データ件数は「取得データ件数: 80件」のまま変わらない",
                companyPage.dataCountLabel(CompanyMasterPage.PAGER_TOP), "取得データ件数: 80件");
    }

    // ---- 3. 外部入出力（CSVダウンロード） ----

    @Test
    @DisplayName("No.3-1 CSVダウンロードで全ての会社マスタレコードが既定の様式・列順・ファイル名で出力される")
    void case3x1() {
        openCompanyMaster(1);

        verify.csvDownloaded(
                "2. ファイル名「会社マスタ.csv」でダウンロードされ、" + "【機械検証】バイト列が CSV様式_期待_会社マスタDL.csv（353バイト）と一致する",
                CSV_FILE_NAME, unitData(CSV_EXPECTED_ASC), () -> companyPage.pressCsvDownload());
    }

    @Test
    @DisplayName("No.3-2 CSVのボディ行の並び順が表示中テーブルのソート順に従う")
    void case3x2() {
        openCompanyMaster(1);

        verify.op(
                "2. 「会社ID」の列ヘッダーをダブルクリックして会社IDの降順に並び替える"
                        + "（初期表示で会社ID列に昇順が適用済みのため、1回のダブルクリックで降順になる）",
                () -> companyPage.doubleClickHeader(CompanyMasterPage.COL_ID),
                companyPage.grid().headerCell(CompanyMasterPage.COL_ID));
        verify.gridCellText("2. テーブルの先頭行が 会社ID: 12「株式会社リマ」になる", companyPage.grid(), 0,
                CompanyMasterPage.COL_NAME, "株式会社リマ");
        verify.gridCellText("2. テーブルの末尾行が 会社ID: 1「株式会社アルファ」になる", companyPage.grid(), 11,
                CompanyMasterPage.COL_NAME, "株式会社アルファ");

        verify.csvDownloaded(
                "3. ファイル名「会社マスタ.csv」でダウンロードされ、" + "【機械検証】バイト列が CSV期待_会社マスタDL降順.csv（353バイト）と一致する",
                CSV_FILE_NAME, unitData(CSV_EXPECTED_DESC), () -> companyPage.pressCsvDownload());
    }

    @Test
    @DisplayName("No.3-3 会社マスタのレコードが0件の場合、ヘッダー行のみのCSVファイルが出力される")
    void case3x3() {
        seedEmpty();
        openCompanyMaster(1);

        verify.text("1. 「該当するデータがありません」が表示されている", companyPage.noRowsOverlay(), NO_ROWS);
        verify.csvDownloadedBodyRows("2. ファイル名「会社マスタ.csv」でダウンロードされ、" + "【機械検証】ヘッダー行のみでボディ行が0行である",
                CSV_FILE_NAME, 0, () -> companyPage.pressCsvDownload());
    }

    // ---- 4. 外部入出力（CSVアップロード・洗い替え） ----

    @Test
    @DisplayName("No.4-1 CSVアップロードで会社マスタが洗い替えられ、一覧が再表示される")
    void case4x1() {
        openCompanyMaster(1);
        verify.gridRowsByKey("1. テーブルに12件（会社ID: 1〜12）が表示されている", companyPage.grid(),
                CompanyMasterPage.COL_ID, ascendingKeys(1, 12));

        final Path file = unitData(UPLOAD_REPLACE);
        uploadCsv("2. 「CSVアップロード」を押下し、洗い替え_正常.csv を指定する", file, companyPage.grid().root());
        verify.machineEquals("【機械検証】指定したファイルの実バイト数が 167 である", "167", String.valueOf(sizeOf(file)));

        replacedFiveRows();
        dataCountIs(5);
        verify.absent("エラー表示（エラータイトル・エラー内容）は表示されない", companyPage.csvUploadButton(),
                companyPage.errorArea());
    }

    @Test
    @DisplayName("No.4-2 ファイルが指定されずにアップロードが実行された場合、洗い替えを実施せずエラーを表示する")
    void case4x2() {
        openCompanyMaster(1);

        verify.op("2. 開発者ツール相当の合成操作で、CSVファイルを指定しないままアップロードを実行する",
                () -> companyPage.submitUploadWithoutFile(), companyPage.errorArea());

        verify.text("エラータイトル「CSVアップロードに失敗しました」が表示される", companyPage.errorTitle(),
                FORMAT_ERROR_TITLE);
        verify.text("エラー内容「この項目は入力が必要です。」が表示される", companyPage.errorMessages(), "この項目は入力が必要です。");
        baselineTableUnchanged();
    }

    @Test
    @DisplayName("No.4-3 【境界値】ファイル容量が5MBを超える場合、ファイル形式チェックに違反する")
    void case4x3() {
        openCompanyMaster(1);

        final Path file = E2eTestFiles.csvOfSize("会社マスタ_5MB超過.csv", CSV_HEADER, "1,", "", 5242881);
        uploadCsv("2. 「CSVアップロード」を押下し、会社マスタ_5MB超過.csv を指定する", file, companyPage.errorArea());

        verify.text("エラータイトル「CSVアップロードに失敗しました」が表示される", companyPage.errorTitle(),
                FORMAT_ERROR_TITLE);
        verify.texts("エラー内容は「ファイルの容量が大きすぎます(5MBまで)。」の1件のみである" + "（他のファイル形式チェックのメッセージは表示されない）",
                companyPage.errorMessages(), new String[] {"ファイルの容量が大きすぎます(5MBまで)。"});
        baselineTableUnchanged();
    }

    @Test
    @DisplayName("No.4-4 【境界値】ファイル容量が5MBちょうどの場合、容量のファイル形式チェックに違反しない")
    void case4x4() {
        openCompanyMaster(1);

        final Path file =
                E2eTestFiles.csvOfSize("会社マスタ_5MBちょうど.csv", CSV_HEADER, "1,", "", 5242880);
        uploadCsv("2. 「CSVアップロード」を押下し、会社マスタ_5MBちょうど.csv を指定する", file, companyPage.errorArea());

        verify.text("エラータイトル「CSVの内容にエラーがあります。詳細は以下の通りです。」が表示される" + "（容量のチェックを通過して後続の入力値チェックに進んでいる）",
                companyPage.errorTitle(), INPUT_ERROR_TITLE);
        verify.texts(
                "エラー内容は「0002 - データ1行目 会社名: 50文字以内で入力してください。」の1件のみであり、"
                        + "「ファイルの容量が大きすぎます(5MBまで)。」は表示されない",
                companyPage.errorMessages(), new String[] {"0002 - データ1行目 会社名: 50文字以内で入力してください。"});
        baselineTableUnchanged();
    }

    @Test
    @DisplayName("No.4-5 ボディ行に会社マスタレコードが1件も存在しない場合、ファイル形式チェックに違反する")
    void case4x5() {
        openCompanyMaster(1);

        uploadCsv("2. 「CSVアップロード」を押下し、形式_データ0件.csv を指定する", unitData(UPLOAD_NO_DATA),
                companyPage.errorArea());

        verify.text("エラータイトル「CSVアップロードに失敗しました」が表示される", companyPage.errorTitle(),
                FORMAT_ERROR_TITLE);
        verify.text("エラー内容「CSVファイルにデータがありませんでした。」が表示される", companyPage.errorMessages(),
                "CSVファイルにデータがありませんでした。");
        baselineTableUnchanged();
    }

    @Test
    @DisplayName("No.4-6 ファイル形式チェック違反後に再度アップロードすると、既存のエラー表示が削除されたうえで再チェックされる")
    void case4x6() {
        openCompanyMaster(1);

        uploadCsv("1. 形式_データ0件.csv をアップロードする", unitData(UPLOAD_NO_DATA), companyPage.errorArea());
        verify.text("2. エラータイトル「CSVアップロードに失敗しました」が表示される", companyPage.errorTitle(),
                FORMAT_ERROR_TITLE);
        verify.text("2. エラー内容「CSVファイルにデータがありませんでした。」が表示される", companyPage.errorMessages(),
                "CSVファイルにデータがありませんでした。");

        verify.transitionCleared("エラー表示が表示されている", companyPage.errorArea(),
                "3. 「CSVアップロード」を押下し、洗い替え_正常.csv を指定する",
                () -> uploadCsv("3. 「CSVアップロード」を押下し、洗い替え_正常.csv を指定する", unitData(UPLOAD_REPLACE),
                        companyPage.grid().root()),
                companyPage.grid().root(), "3. エラー表示が消える");
        replacedFiveRows();
    }

    @Test
    @DisplayName("No.4-7 入力値チェック違反後に正しいCSVをアップロードすると、既存のエラーメッセージが削除されて洗い替えが実施される")
    void case4x7() {
        openCompanyMaster(1);

        uploadCsv("1. 入力_会社ID必須.csv をアップロードする", unitData("21_会社マスタ管理/data/入力_会社ID必須.csv"),
                companyPage.errorArea());
        verify.text("2. エラータイトル「CSVの内容にエラーがあります。詳細は以下の通りです。」が表示される", companyPage.errorTitle(),
                INPUT_ERROR_TITLE);
        verify.text("2. エラー内容「0002 - データ1行目 会社ID: この項目は入力が必要です。」が表示される",
                companyPage.errorMessages(), "0002 - データ1行目 会社ID: この項目は入力が必要です。");

        verify.transitionCleared("エラー表示（エラータイトル・エラー内容）が表示されている", companyPage.errorArea(),
                "3. 「CSVアップロード」を押下し、洗い替え_正常.csv を指定する",
                () -> uploadCsv("3. 「CSVアップロード」を押下し、洗い替え_正常.csv を指定する", unitData(UPLOAD_REPLACE),
                        companyPage.grid().root()),
                companyPage.grid().root(), "3. エラー表示（エラータイトル・エラー内容）が消える");
        replacedFiveRows();
        dataCountIs(5);
    }

    // ---- 5. 入力チェック（CSV取込の会社マスタレコード） ----

    /** CSV アップロードの入力値チェック違反を確認する共通手順。 */
    private void expectInputError(String fileName, String expectedMessage) {
        openCompanyMaster(1);
        verify.gridRowsByKey("1. テーブルに12件（会社ID: 1〜12）が表示されている", companyPage.grid(),
                CompanyMasterPage.COL_ID, ascendingKeys(1, 12));

        uploadCsv("2. 「CSVアップロード」を押下し、" + fileName + " を指定する",
                unitData("21_会社マスタ管理/data/" + fileName), companyPage.errorArea());

        verify.text("エラータイトル「CSVの内容にエラーがあります。詳細は以下の通りです。」が表示される", companyPage.errorTitle(),
                INPUT_ERROR_TITLE);
        verify.text("エラー内容「" + expectedMessage + "」が表示される", companyPage.errorMessages(),
                expectedMessage);
        baselineTableUnchanged();
    }

    @Test
    @DisplayName("No.5-1 会社IDが未入力の場合、必須入力に違反する")
    void case5x1() {
        expectInputError("入力_会社ID必須.csv", "0002 - データ1行目 会社ID: この項目は入力が必要です。");
    }

    @Test
    @DisplayName("No.5-2 会社IDが整数でない場合、整数チェックに違反する")
    void case5x2() {
        expectInputError("入力_会社ID整数以外.csv", "0002 - データ1行目 会社ID: 整数を入力してください。");
    }

    @Test
    @DisplayName("No.5-3 【境界値】会社IDが0の場合、最小数値(1)に違反する")
    void case5x3() {
        expectInputError("入力_会社ID最小数値.csv", "0002 - データ1行目 会社ID: 1以上の数値を入力してください。");
    }

    @Test
    @DisplayName("No.5-4 【境界値】会社IDが2147483648の場合、最大数値(2147483647)に違反する")
    void case5x4() {
        expectInputError("入力_会社ID最大数値.csv", "0002 - データ1行目 会社ID: 2147483647以下の数値を入力してください。");
    }

    @Test
    @DisplayName("No.5-5 会社IDが重複している場合、2回目以降の行に一意違反のエラーを表示する")
    void case5x5() {
        openCompanyMaster(1);
        verify.gridRowsByKey("1. テーブルに12件（会社ID: 1〜12）が表示されている", companyPage.grid(),
                CompanyMasterPage.COL_ID, ascendingKeys(1, 12));

        uploadCsv("2. 「CSVアップロード」を押下し、入力_会社ID重複.csv を指定する",
                unitData("21_会社マスタ管理/data/入力_会社ID重複.csv"), companyPage.errorArea());

        verify.text("エラータイトル「CSVの内容にエラーがあります。詳細は以下の通りです。」が表示される", companyPage.errorTitle(),
                INPUT_ERROR_TITLE);
        verify.texts(
                "エラー内容は「0003 - データ2行目 会社ID: 重複して設定しないでください(7)」の1件のみであり、"
                        + "「0002 - データ1行目」で始まるエラーメッセージは表示されない",
                companyPage.errorMessages(),
                new String[] {"0003 - データ2行目 会社ID: 重複して設定しないでください(7)"});
        baselineTableUnchanged();
    }

    @Test
    @DisplayName("No.5-6 会社名が未入力の場合、必須入力に違反する")
    void case5x6() {
        expectInputError("入力_会社名必須.csv", "0002 - データ1行目 会社名: この項目は入力が必要です。");
    }

    @Test
    @DisplayName("No.5-7 【境界値】会社名が51文字の場合、最大文字数(50)に違反する")
    void case5x7() {
        expectInputError("入力_会社名最大文字数.csv", "0002 - データ1行目 会社名: 50文字以内で入力してください。");
    }

    @Test
    @DisplayName("No.5-8 会社名に全角文字以外を含む場合、使用文字に違反する")
    void case5x8() {
        expectInputError("入力_会社名使用文字.csv", "0002 - データ1行目 会社名: 使用不可能な文字が含まれています。");
    }

    @Test
    @DisplayName("No.5-9 【境界値】会社ID 1・2147483647、会社名 全角50文字は入力値チェックに違反せず取り込まれる")
    void case5x9() {
        openCompanyMaster(1);
        verify.gridRowsByKey("1. テーブルに12件（会社ID: 1〜12）が表示されている", companyPage.grid(),
                CompanyMasterPage.COL_ID, ascendingKeys(1, 12));

        uploadCsv("2. 「CSVアップロード」を押下し、洗い替え_境界値正常.csv を指定する", unitData(UPLOAD_BOUNDARY),
                companyPage.grid().root());

        verify.absent("エラー表示（エラータイトル・エラー内容）は表示されない", companyPage.csvUploadButton(),
                companyPage.errorArea());
        verify.gridRowsByKey("会社マスタ管理画面に2件が会社IDの昇順で一覧表示される", companyPage.grid(),
                CompanyMasterPage.COL_ID, List.of("1", "2147483647"));
        verify.gridCellText("会社ID: 1 の会社名が全角50文字である", companyPage.grid(), 0,
                CompanyMasterPage.COL_NAME, "あ".repeat(50));
        verify.gridCellText("会社ID: 2147483647 の会社名が「零」である", companyPage.grid(), 1,
                CompanyMasterPage.COL_NAME, "零");
        dataCountIs(2);
    }

    @Test
    @DisplayName("No.5-10 同じ行の複数の項目でチェック違反がある場合、列の順序ごとに全てのエラーメッセージを表示する")
    void case5x10() {
        openCompanyMaster(1);
        verify.gridRowsByKey("1. テーブルに12件（会社ID: 1〜12）が表示されている", companyPage.grid(),
                CompanyMasterPage.COL_ID, ascendingKeys(1, 12));

        uploadCsv("2. 「CSVアップロード」を押下し、入力_同一行複数項目.csv を指定する",
                unitData("21_会社マスタ管理/data/入力_同一行複数項目.csv"), companyPage.errorArea());

        verify.text("エラータイトル「CSVの内容にエラーがあります。詳細は以下の通りです。」が表示される", companyPage.errorTitle(),
                INPUT_ERROR_TITLE);
        verify.texts("エラー内容が会社ID・会社名の順（列の順序）で2件並ぶ", companyPage.errorMessages(), new String[] {
                "0002 - データ1行目 会社ID: 1以上の数値を入力してください。", "0002 - データ1行目 会社名: この項目は入力が必要です。"});
        baselineTableUnchanged();
    }

    @Test
    @DisplayName("No.5-11 同じ項目内で複数の入力値チェックに違反した場合、より小さな番号のエラーメッセージ1つのみを表示する")
    void case5x11() {
        openCompanyMaster(1);
        verify.gridRowsByKey("1. テーブルに12件（会社ID: 1〜12）が表示されている", companyPage.grid(),
                CompanyMasterPage.COL_ID, ascendingKeys(1, 12));

        uploadCsv("2. 「CSVアップロード」を押下し、入力_会社ID必須.csv を指定する",
                unitData("21_会社マスタ管理/data/入力_会社ID必須.csv"), companyPage.errorArea());

        verify.text("エラータイトル「CSVの内容にエラーがあります。詳細は以下の通りです。」が表示される", companyPage.errorTitle(),
                INPUT_ERROR_TITLE);
        verify.texts("エラー内容は「0002 - データ1行目 会社ID: この項目は入力が必要です。」の1件のみであり、" + "「整数を入力してください。」は表示されない",
                companyPage.errorMessages(), new String[] {"0002 - データ1行目 会社ID: この項目は入力が必要です。"});
        baselineTableUnchanged();
    }

    @Test
    @DisplayName("No.5-12 CSV行番号が5桁以上の場合、0埋めをせずそのまま表示する")
    void case5x12() {
        openCompanyMaster(1);
        verify.gridRowsByKey("1. テーブルに12件（会社ID: 1〜12）が表示されている", companyPage.grid(),
                CompanyMasterPage.COL_ID, ascendingKeys(1, 12));

        List<String> lines = new ArrayList<>();
        for (int id = 1; id <= 9999; id++) {
            String name = id == 9999 ? "" : "社" + E2eTestFiles.fullWidthDigits(id, 4);
            lines.add(id + "," + name);
        }
        final Path file = E2eTestFiles.csvOfLines("会社マスタ_行番号5桁.csv", CSV_HEADER, lines);
        uploadCsv("2. 「CSVアップロード」を押下し、会社マスタ_行番号5桁.csv を指定する", file, companyPage.errorArea());

        verify.text("エラータイトル「CSVの内容にエラーがあります。詳細は以下の通りです。」が表示される", companyPage.errorTitle(),
                INPUT_ERROR_TITLE);
        verify.text(
                "エラー内容「10000 - データ9999行目 会社名: この項目は入力が必要です。」が表示される"
                        + "（CSV行番号 10000 は5桁のため0埋めをせずそのまま表示される）",
                companyPage.errorMessages(), "10000 - データ9999行目 会社名: この項目は入力が必要です。");
        baselineTableUnchanged();
    }

    @Test
    @DisplayName("No.5-13 入力値チェック違反後に再度アップロードすると、既存のエラーメッセージが削除されたうえで再チェックされる")
    void case5x13() {
        openCompanyMaster(1);

        uploadCsv("1. 入力_会社ID必須.csv をアップロードする", unitData("21_会社マスタ管理/data/入力_会社ID必須.csv"),
                companyPage.errorArea());
        verify.text("2. エラー内容「0002 - データ1行目 会社ID: この項目は入力が必要です。」が表示される",
                companyPage.errorMessages(), "0002 - データ1行目 会社ID: この項目は入力が必要です。");

        uploadCsv("3. 「CSVアップロード」を押下し、入力_会社名必須.csv を指定する", unitData("21_会社マスタ管理/data/入力_会社名必須.csv"),
                companyPage.errorArea());
        verify.texts("3. エラー内容が「0002 - データ1行目 会社名: この項目は入力が必要です。」だけになる" + "（手順2のエラーメッセージが消えている）",
                companyPage.errorMessages(), new String[] {"0002 - データ1行目 会社名: この項目は入力が必要です。"});
        baselineTableUnchanged();
    }

    // ---- 6. サーバー・APIサービス処理／認証・認可（異常分岐） ----

    @Test
    @DisplayName("No.6-1 洗い替え中にデータベース例外が発生した場合、洗い替え前の状態に戻りエラー画面に【サーバー内部エラー】が表示される")
    void case6x1() {
        verify.opArmFault("1. （障害シーム）「データベース例外（会社マスタ洗い替え）」を有効化する",
                CompanyMasterFaultSeamConfig.DB_EXCEPTION_ON_REPLACE);
        openCompanyMaster(2);

        uploadCsv("3. 「CSVアップロード」を押下し、洗い替え_正常.csv を指定する", unitData(UPLOAD_REPLACE),
                errorPage.card());

        verify.text("3. エラータイトル「An Error Occurred」が表示される", errorPage.errorTitle(),
                "An Error Occurred");
        verify.text("3. エラー内容「エラーが発生しました。」が表示される", errorPage.errorMessage(), "エラーが発生しました。");
        verify.faultFired("【機械検証】障害シーム「データベース例外（会社マスタ洗い替え）」が発火した",
                CompanyMasterFaultSeamConfig.DB_EXCEPTION_ON_REPLACE);

        openCompanyMaster(4);
        verify.gridRowsByKey("4. 12件（会社ID: 1〜12）が表示され、洗い替え前の状態に戻っている" + "（会社ID: 101〜105 は登録されていない）",
                companyPage.grid(), CompanyMasterPage.COL_ID, ascendingKeys(1, 12));
        dataCountIs(12);
    }

    @Test
    @DisplayName("No.6-2 CSVダウンロード時にログインの有効期限が切れている場合、ダウンロードは実施されず案件情報一覧画面へ遷移する")
    void case6x2() {
        openCompanyMaster(1);

        verify.op("2. ブラウザが保持するセッションCookieを削除する（ログインの有効期限が切れた状態にする）",
                () -> companyPage.clearSessionCookies());
        verify.machineEquals("手順2の後、ブラウザが保持するセッションCookieが削除されている", "0",
                String.valueOf(companyPage.sessionCookieCount()));

        verify.noDownload("【機械検証】手順3を通じてファイルのダウンロードが発生していない",
                () -> verify.opPress("3. 「CSVダウンロード」を押下する", companyPage.csvDownloadButton(), () -> {
                    companyPage.pressCsvDownloadWithoutWaiting();
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
        verify.urlIs("4. 会社マスタ管理画面ではなく案件情報一覧画面 /projects へ遷移する", baseUrl() + ProjectListPage.PATH);
    }

    @Test
    @DisplayName("No.6-3 洗い替え時にログインの有効期限が切れている場合、洗い替えは実施されず案件情報一覧画面へ遷移する")
    void case6x3() {
        openCompanyMaster(1);

        verify.op("2. ブラウザが保持するセッションCookieを削除する（ログインの有効期限が切れた状態にする）",
                () -> companyPage.clearSessionCookies());
        verify.machineEquals("手順2の後、ブラウザが保持するセッションCookieが削除されている", "0",
                String.valueOf(companyPage.sessionCookieCount()));

        uploadCsv("3. 「CSVアップロード」を押下して洗い替え_正常.csv を指定する", unitData(UPLOAD_REPLACE),
                loginPage.form());
        verify.visible("3. 会社マスタ管理画面は再表示されずログイン画面が表示される", loginPage.form());

        verify.opPressWithInput("4. SM0002 のユーザID・パスワードを入力し「ログイン」を押下する", () -> {
            loginPage.fillUserId(ADMIN_USER);
            loginPage.fillPassword(PASSWORD);
        }, loginPage.loginButton(), () -> {
            loginPage.loginButton().click();
            page.waitForURL(baseUrl() + ProjectListPage.PATH);
        }, commonHeader.screenTitleOf(ProjectListPage.SCREEN_TITLE));
        verify.urlIs("4. 会社マスタ管理画面ではなく案件情報一覧画面 /projects へ遷移する", baseUrl() + ProjectListPage.PATH);

        openCompanyMaster(5);
        verify.gridRowsByKey("5. 12件（会社ID: 1〜12）が表示され、洗い替えは実施されていない" + "（会社ID: 101〜105 は登録されていない）",
                companyPage.grid(), CompanyMasterPage.COL_ID, ascendingKeys(1, 12));
    }
}
