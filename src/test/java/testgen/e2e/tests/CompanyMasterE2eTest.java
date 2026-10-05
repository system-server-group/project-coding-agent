package testgen.e2e.tests;

import com.microsoft.playwright.Download;
import com.microsoft.playwright.Locator;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;
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
import testgen.e2e.support.E2eSeedSupport;
import testgen.e2e.support.evidence.E2eGridAdapter;
import testgen.e2e.support.evidence.E2eVerify;
import testgen.e2e.support.evidence.EvidenceV2;

/**
 * 会社マスタ管理画面（機能 {@code 21_会社マスタ管理}）の E2E テスト。
 *
 * <p>
 * テストケース仕様書 {@code 21_会社マスタ管理/21_会社マスタ管理_テスト仕様書.xlsx} の全ケース（No.1-1〜No.10-4）を実装する。
 * テーブル操作（列順序・列幅・ソート・スクロール・ページ送り）、CSV のダウンロード・アップロード（洗い替え）、ファイル形式チェック・入力値チェック、
 * セッション切れ・データベース例外（障害シーム）、認証・認可を扱う。
 */
@EvidenceV2
@Import(CompanyMasterFaultSeamConfig.class)
@E2eFeature("21_会社マスタ管理")
class CompanyMasterE2eTest extends ProjectE2eTest {

    /** システム管理者 admin01（管理者太郎／営業部）。 */
    private static final String ADMIN_USER = "admin01";

    /** 一般ユーザ user01（一般花子）。 */
    private static final String GENERAL_USER = "user01";

    /** ベースラインのユーザに共通の開発用ダミーパスワード。 */
    private static final String PASSWORD = "Passw0rd!";

    /** 機能のデータ正本の配置（実施単位からの相対）。 */
    private static final String DATA = "21_会社マスタ管理/data/";

    /** 洗い替え（正常3件。ファイル上の行順は ID 300・10・20）。 */
    private static final String UPLOAD_REPLACE = "洗い替え_正常.csv";

    /** ダウンロードされる CSV のファイル名。 */
    private static final String CSV_FILE_NAME = "会社マスタ.csv";

    /** CSV 期待値正本: 会社IDの昇順。 */
    private static final String CSV_EXPECTED_ID_ASC = "CSV期待_会社ID昇順.csv";

    /** ファイル容量境界の生成仕様の列（データ正本のヘッダー）。 */
    private static final String[] SIZE_SPEC_COLUMNS = {"file_name", "file_size", "header_line",
            "row_prefix", "fill_char", "row_suffix", "generation_rule"};

    /** ファイル形式チェック違反のエラータイトル。 */
    private static final String FORMAT_ERROR_TITLE = "CSVアップロードに失敗しました";

    /** 入力値チェック違反のエラータイトル。 */
    private static final String INPUT_ERROR_TITLE = "CSVの内容にエラーがあります。詳細は以下の通りです。";

    /** 既定の様式でないファイルのエラー内容。 */
    private static final String READ_FAILURE = "CSVの読み取りに失敗しました。フォーマットが正しいかファイルを確認してください。";

    /** ボディ行が0件のファイルのエラー内容。 */
    private static final String NO_DATA = "CSVファイルにデータがありませんでした。";

    /** 容量超過のエラー内容。 */
    private static final String SIZE_OVER = "ファイルの容量が大きすぎます(5MBまで)。";

    /** 表示ページセレクトボックスの表示ラベルからページ番号を取り出す（表示形式は設計に定めが無いため照合に含めない）。 */
    private static final Pattern PAGE_NUMBER = Pattern.compile("^\\d+");

    /** 表示件数セレクトボックスの選択肢。 */
    private static final List<String> PAGE_SIZE_OPTIONS = List.of("10件", "20件", "30件", "全て");

    /** ベースラインの会社ID（会社IDの昇順）。 */
    private static final List<String> BASELINE_IDS = List.of("1", "2", "10", "100");

    /** ベースラインの会社名（会社IDの昇順）。 */
    private static final List<String> BASELINE_NAMES = List.of("ワタナベ商事", "アオバ物産", "カシワ工業", "サクラ電機");

    /** ベースラインの4件の表記。 */
    private static final String BASELINE_DESC =
            "ID 1「ワタナベ商事」・ID 2「アオバ物産」・ID 10「カシワ工業」・ID 100「サクラ電機」";

    /** 洗い替え_正常.csv で洗い替えた後の会社ID（会社IDの昇順）。 */
    private static final List<String> REPLACED_IDS = List.of("10", "20", "300");

    /** 洗い替え_正常.csv で洗い替えた後の会社名（会社IDの昇順）。 */
    private static final List<String> REPLACED_NAMES = List.of("カシワ工業新社", "ミナト物流", "タチバナ商事");

    /** 会社名の昇順に並べたベースラインの会社ID。 */
    private static final List<String> NAME_ASC_IDS = List.of("2", "10", "100", "1");

    /** 会社名の昇順に並べたベースラインの会社名。 */
    private static final List<String> NAME_ASC_NAMES = List.of("アオバ物産", "カシワ工業", "サクラ電機", "ワタナベ商事");

    /** 会社名の降順に並べたベースラインの会社ID。 */
    private static final List<String> NAME_DESC_IDS = List.of("1", "100", "10", "2");

    /** 会社名の降順に並べたベースラインの会社名。 */
    private static final List<String> NAME_DESC_NAMES =
            List.of("ワタナベ商事", "サクラ電機", "カシワ工業", "アオバ物産");

    /** ページネーションのボタン（先頭・前・ページ番号「1」・次・末尾）の表示。 */
    private static final String[] PAGER_LABELS_SINGLE_PAGE = {"«", "‹", "1", "›", "»"};

    /** ページネーションのボタンの名称（{@link #PAGER_LABELS_SINGLE_PAGE} と同順）。 */
    private static final String[] PAGER_LABEL_NAMES = {"先頭", "前", "ページ番号「1」", "次", "末尾"};

    private LoginPage loginPage;
    private CommonHeaderPage commonHeader;
    private CompanyMasterPage companyPage;
    private ProjectListPage projectListPage;
    private ErrorPage errorPage;
    private E2eGridAdapter grid;

    @BeforeEach
    void setUpPages() {
        loginPage = new LoginPage(page, baseUrl());
        commonHeader = new CommonHeaderPage(page, baseUrl());
        companyPage = new CompanyMasterPage(page, baseUrl());
        projectListPage = new ProjectListPage(page, baseUrl());
        errorPage = new ErrorPage(page, baseUrl());
        grid = companyPage.grid();
    }

    // ---- 前提・共通手順 ----

    /** 前提条件: システム管理者（admin01）でログイン済み。 */
    private void signInAsAdmin() {
        loginPage.signIn(ADMIN_USER, PASSWORD, ProjectListPage.PATH);
    }

    /** 前提条件: 一般ユーザ（user01）でログイン済み。 */
    private void signInAsGeneralUser() {
        loginPage.signIn(GENERAL_USER, PASSWORD, ProjectListPage.PATH);
    }

    /** 機能のデータ正本（{@code 21_会社マスタ管理/data/} 配下）を解決する。 */
    private Path data(String fileName) {
        return unitData(DATA + fileName);
    }

    /** 手順「ブラウザで /master/companies を開く」。 */
    private void openCompanyMaster(int stepNo) {
        openCompanyMaster(stepNo, "");
    }

    /** 手順「ブラウザで /master/companies を開く」（仕様書の補足付き）。 */
    private void openCompanyMaster(int stepNo, String note) {
        verify.opOpen(stepNo + ". ブラウザで /master/companies を開く" + note, () -> companyPage.open());
    }

    /** 手順「ブラウザで /projects（案件情報一覧画面）を開く」。 */
    private void openProjectList(int stepNo) {
        verify.opOpen(stepNo + ". ブラウザで /projects（案件情報一覧画面）を開く", () -> projectListPage.open());
    }

    /** CSV をアップロードする（ファイル選択で即座にフォーム送信される）。 */
    private void upload(String stepDesc, Path file, Locator result) {
        verify.opChooseFileSubmits(stepDesc, companyPage.csvUploadButton(), file, result);
    }

    /** 昇順に並ぶ会社IDの一覧を作る。 */
    private static List<String> ascendingKeys(int from, int to) {
        List<String> keys = new ArrayList<>();
        for (int id = from; id <= to; id++) {
            keys.add(String.valueOf(id));
        }
        return keys;
    }

    /** シードの会社名（「テスト会社」＋全角2桁の会社ID）。 */
    private static String testCompanyName(int id) {
        return "テスト会社" + E2eTestFiles.fullWidthDigits(id, 2);
    }

    /** シードの会社名の一覧（会社IDの昇順）。 */
    private static List<String> testCompanyNames(int from, int to) {
        List<String> names = new ArrayList<>();
        for (int id = from; id <= to; id++) {
            names.add(testCompanyName(id));
        }
        return names;
    }

    /** ページャの位置の表記。 */
    private static String sideOf(int position) {
        return position == CompanyMasterPage.PAGER_TOP ? "上部" : "下部";
    }

    /** 行の並び（会社IDと会社名）を上から順に確認する（各列を連続フレーム規格で検証し、行数を締める）。 */
    private void rowsAre(String desc, E2eGridAdapter target, List<String> ids, List<String> names) {
        verify.gridRowsByKey(desc + "（会社ID列）", target, CompanyMasterPage.COL_ID, ids);
        verify.gridRowsByKey(desc + "（会社名列）", target, CompanyMasterPage.COL_NAME, names);
    }

    /** 会社マスタテーブルがベースラインの4件のままであることを確認する。 */
    private void baselineUnchanged(String prefix) {
        rowsAre(prefix + "洗い替えは行われず、会社マスタテーブルはベースラインの4件（" + BASELINE_DESC + "）のまま", grid,
                BASELINE_IDS, BASELINE_NAMES);
    }

    /** テーブル上部・下部の取得データ件数を確認する。 */
    private void dataCountIs(String prefix, int count, String suffix) {
        String expected = "取得データ件数: " + count + "件";
        for (int position : List.of(CompanyMasterPage.PAGER_TOP, CompanyMasterPage.PAGER_BOTTOM)) {
            verify.text(prefix + "テーブル" + sideOf(position) + "の取得データ件数に「" + expected + "」と表示される"
                    + suffix, companyPage.dataCountLabel(position), expected);
        }
    }

    /** エラー表示（エラータイトル・エラー内容の一覧）を確認する。 */
    private void errorShown(String prefix, String title, String exclusionNote, String... messages) {
        verify.text(prefix + "エラー表示に、エラータイトル「" + title + "」が表示される", companyPage.errorTitle(),
                title);
        verify.texts(prefix + "エラー内容に「" + String.join("」「", messages)
                + "」が上から順に表示される（これ以外のメッセージは表示されない）" + exclusionNote, companyPage.errorMessages(),
                messages);
    }

    /** エラー表示が無いことを確認する（表示位置を図示し、要素0件を機械検証する）。 */
    private void noErrorDisplayed(String prefix) {
        verify.absent(
                prefix + "エラー表示の表示位置（画面タイトルと「CSVアップロード」「CSVダウンロード」ボタンの間）にエラー表示が無い"
                        + "（【機械検証】エラー表示（エラータイトル・エラー内容）の要素が0件である）",
                companyPage.toolbar(), companyPage.errorArea());
    }

    /** 上部・下部の表示ページセレクトボックスで、指定のページ番号が選択された表示であることを確認する。 */
    private void pageJumpShows(String prefix, int pageNumber) {
        for (int position : List.of(CompanyMasterPage.PAGER_TOP, CompanyMasterPage.PAGER_BOTTOM)) {
            verify.selectedOption(
                    prefix + "テーブル" + sideOf(position) + "の表示ページセレクトボックスがページ番号" + pageNumber
                            + "の表示である",
                    companyPage.pageJumpSelect(position), String.valueOf(pageNumber), PAGE_NUMBER);
        }
    }

    /** 上部・下部の表示ページセレクトボックスの選択肢（ページ番号）を確認する。 */
    private void pageJumpOptionsAre(String prefix, int pages) {
        pageJumpOptionsAre(prefix, pages, "");
    }

    /** 上部・下部の表示ページセレクトボックスの選択肢（ページ番号）を確認する（選択肢の補足付き）。 */
    private void pageJumpOptionsAre(String prefix, int pages, String optionsNote) {
        List<String> expected = ascendingKeys(1, pages);
        String label = pages == 1 ? "1件（ページ番号1）のみ"
                : "ページ番号" + String.join("・", expected) + "の" + pages + "件" + optionsNote;
        for (int position : List.of(CompanyMasterPage.PAGER_TOP, CompanyMasterPage.PAGER_BOTTOM)) {
            verify.selectOptions(prefix + "【機械検証】テーブル" + sideOf(position) + "の表示ページセレクトボックスの選択肢が"
                    + label + "である", companyPage.pageJumpSelect(position), expected, PAGE_NUMBER);
        }
    }

    /** 手順「上部／下部の表示件数セレクトボックスで〜を選択する」。 */
    private void selectPageSize(int stepNo, int position, String label, String value) {
        verify.op(stepNo + ". " + sideOf(position) + "の表示件数セレクトボックスで「" + label + "」を選択する",
                () -> companyPage.selectPageSize(position, value),
                companyPage.pageSizeSelect(position));
    }

    /** 手順「上部／下部の表示ページセレクトボックスでページ番号〜を選択する」。 */
    private void selectPage(int stepNo, int position, int pageNumber) {
        verify.op(stepNo + ". " + sideOf(position) + "の表示ページセレクトボックスでページ番号" + pageNumber + "を選択する",
                () -> companyPage.selectPage(position, pageNumber),
                companyPage.pageJumpSelect(position));
    }

    /** 手順「上部／下部のページネーションの〜ボタンを押下する」。 */
    private void pressPager(int stepNo, int position, String buttonName, String label) {
        verify.opPress(
                stepNo + ". " + sideOf(position) + "のページネーションの" + buttonName + "（" + label
                        + "）を押下する",
                companyPage.pagerButton(position, label),
                () -> companyPage.pressPagerButton(position, label));
    }

    /** 表示中のページの行（会社ID）を確認する（表示件数10件のページ送り用）。 */
    private void pageRowsAre(String desc, int fromId, int toId) {
        verify.gridRowsByKey(desc, companyPage.grid(fromId - 1), CompanyMasterPage.COL_ID,
                ascendingKeys(fromId, toId));
    }

    /** 手順「列ヘッダーをダブルクリックする」。 */
    private void doubleClickHeader(String stepDesc, String colId) {
        verify.op(stepDesc, () -> companyPage.doubleClickHeader(colId), grid.headerCell(colId));
    }

    /** 手順「開発者ツール相当の合成操作で、ブラウザが保持するセッションCookieを削除する」と効果の機械検証。 */
    private void clearSessionCookies(int stepNo) {
        verify.op(stepNo + ". 開発者ツール相当の合成操作で、ブラウザが保持するセッションCookieを削除する（ログインの有効期限が切れた状態にする）",
                () -> companyPage.clearSessionCookies());
        verify.machineEquals("【機械検証】手順" + stepNo + "の後、ブラウザにセッションCookieが存在しない（件数）", "0",
                String.valueOf(companyPage.sessionCookieCount()));
    }

    /** 手順「ログイン画面でシステム管理者（admin01）のユーザID・パスワードを入力し、「ログイン」を押下する」。 */
    private void signInOnLoginScreen(int stepNo) {
        verify.opPressWithInput(stepNo + ". ログイン画面でシステム管理者（admin01）のユーザID・パスワードを入力し、「ログイン」を押下する",
                () -> {
                    loginPage.fillUserId(ADMIN_USER);
                    loginPage.fillPassword(PASSWORD);
                }, loginPage.loginButton(),
                () -> page.waitForNavigation(() -> loginPage.loginButton().click()));
    }

    /** 手順「共通ヘッダーの「会社マスタ」ボタンを押下する」。 */
    private void pressCompanyNav(int stepNo) {
        verify.opPress(stepNo + ". 共通ヘッダーの「会社マスタ」ボタンを押下する",
                commonHeader.nav(CommonHeaderPage.NAV_COMPANY),
                () -> commonHeader.clickNav(CommonHeaderPage.NAV_COMPANY));
    }

    /**
     * 操作の間、ブラウザからアプリのサーバーへの要求が発生しないことを観測する（HTTP メソッドごとに否定観測を重ねる）。
     */
    private void noServerRequestDuring(String desc, Runnable action) {
        Runnable observed = action;
        for (String method : List.of("DELETE", "PATCH", "PUT", "POST", "GET")) {
            final Runnable inner = observed;
            observed =
                    () -> verify.noRequestSent(desc + "（" + method + "）", method, baseUrl(), inner);
        }
        observed.run();
    }

    /**
     * ファイル容量境界の生成仕様（{@code ファイル容量境界_生成仕様.csv}）の該当行に従って CSV を生成する。
     *
     * @param fileName 生成するファイル名（生成仕様の {@code file_name} 列）
     * @return 生成したファイル
     */
    private Path generateSizedCsv(String fileName) {
        Path spec = data("ファイル容量境界_生成仕様.csv");
        List<List<String>> records = E2eSeedSupport.parseCsv(E2eSeedSupport.readContent(spec));
        E2eSeedSupport.requireHeader(records.get(0), SIZE_SPEC_COLUMNS, spec);
        for (List<String> row : records.subList(1, records.size())) {
            if (!fileName.equals(row.get(0))) {
                continue;
            }
            if (!"a".equals(row.get(4))) {
                throw new IllegalStateException("生成仕様の fill_char が半角「a」ではありません: " + row.get(4));
            }
            return E2eTestFiles.csvOfSize(fileName, row.get(2), row.get(3), row.get(5),
                    Integer.parseInt(row.get(1)));
        }
        throw new IllegalStateException("生成仕様に " + fileName + " の行がありません: " + spec);
    }

    /** 生成したファイルのバイト数。 */
    private static long sizeOf(Path file) {
        try {
            return Files.size(file);
        } catch (IOException e) {
            throw new UncheckedIOException("生成したファイルのサイズを取得できません: " + file, e);
        }
    }

    /**
     * CSV アップロードでファイル形式チェック・入力値チェックに違反し、洗い替えされないことを確認する共通手順。
     *
     * @param fileName データ正本のファイル名
     * @param fileNote 仕様書の手順に記されたファイルの補足
     * @param title 期待するエラータイトル
     * @param exclusionNote 表示されないメッセージの補足（無ければ空文字）
     * @param messages 期待するエラー内容（上から順）
     */
    private void expectUploadRejected(
            String fileName,
            String fileNote,
            String title,
            String exclusionNote,
            String... messages) {
        signInAsAdmin();
        openCompanyMaster(1);
        upload("2. 「CSVアップロード」ボタンを押下し、ファイル選択ダイアログで " + fileName + fileNote + " を指定する",
                data(fileName), companyPage.errorArea());
        errorShown("", title, exclusionNote, messages);
        baselineUnchanged("");
        dataCountIs("", 4, "（のまま）");
    }

    // ---- 1. 画面仕様・初期表示 ----

    @Test
    @DisplayName("No.1-1 会社マスタ管理画面の初期表示（画面項目・ラベル・初期値）")
    void case1x1() {
        signInAsAdmin();
        openCompanyMaster(1);

        verify.text("会社マスタ管理画面が表示され、画面タイトル「会社マスタ管理画面」が表示される", commonHeader.screenTitle(),
                CompanyMasterPage.SCREEN_TITLE);
        verify.visible("「CSVアップロード」ボタンが表示される", companyPage.csvUploadButtonLabeled("CSVアップロード"));
        verify.visible("「CSVダウンロード」ボタンが表示される", companyPage.csvDownloadButtonLabeled("CSVダウンロード"));
        for (int position : List.of(CompanyMasterPage.PAGER_TOP, CompanyMasterPage.PAGER_BOTTOM)) {
            String side = "テーブル" + sideOf(position);
            verify.visible(side + "に取得データ件数が表示される", companyPage.dataCountLabel(position));
            verify.visible(side + "にページネーションが表示される", companyPage.pagerNums(position));
            verify.text(side + "の表示件数セレクトボックスのラベルが「表示件数」である", companyPage.pageSizeLabel(position),
                    "表示件数");
            verify.selectedOption(side + "の表示件数セレクトボックスの選択値が「30件」である",
                    companyPage.pageSizeSelect(position), "30件");
            verify.text(side + "の表示ページセレクトボックスのラベルが「表示ページ」である", companyPage.pageJumpLabel(position),
                    "表示ページ");
            verify.visible(side + "に表示ページセレクトボックスが表示される", companyPage.pageJumpSelect(position));
        }
        verify.gridColumnHeaders("会社マスタテーブルの列ヘッダーが左から「会社ID」「会社名」の順に表示される", grid,
                CompanyMasterPage.DEFAULT_HEADERS);
        for (int position : List.of(CompanyMasterPage.PAGER_TOP, CompanyMasterPage.PAGER_BOTTOM)) {
            verify.selectOptions(
                    "【機械検証】テーブル" + sideOf(position) + "の表示件数セレクトボックスの選択肢が「10件」「20件」「30件」「全て」の4件である",
                    companyPage.pageSizeSelect(position), PAGE_SIZE_OPTIONS);
        }
    }

    @Test
    @DisplayName("No.1-2 全ての会社マスタレコードが会社IDの昇順（数値の大小）で一覧表示され、取得データ件数が上部・下部に表示される")
    void case1x2() {
        signInAsAdmin();
        openCompanyMaster(1);

        rowsAre("会社マスタテーブルに4行が上から ID 1「ワタナベ商事」、ID 2「アオバ物産」、ID 10「カシワ工業」、"
                + "ID 100「サクラ電機」の順（会社IDの数値の昇順）に表示される", grid, BASELINE_IDS, BASELINE_NAMES);
        dataCountIs("", 4, "");
    }

    @Test
    @DisplayName("No.1-3 初期表示時点で会社ID列に昇順のソート状態が適用され、列ヘッダーにソート状態の意匠が表示される")
    void case1x3() {
        signInAsAdmin();
        openCompanyMaster(1);

        verify.visible("「会社ID」列ヘッダーに昇順を示すソートの意匠が表示される",
                companyPage.sortAscendingIcon(CompanyMasterPage.COL_ID));
        verify.absent("「会社名」列ヘッダーにはソートの意匠が表示されない", grid.headerCell(CompanyMasterPage.COL_NAME),
                companyPage.visibleSortIcon(CompanyMasterPage.COL_NAME));
    }

    @Test
    @DisplayName("No.1-4 各列の列幅がテーブルの横幅を2等分した幅で表示される")
    void case1x4() {
        signInAsAdmin();
        openCompanyMaster(1);

        final int tableWidth = companyPage.gridBodyWidthPx();
        final int idWidth = tableWidth / 2;
        final int nameWidth = tableWidth - idWidth;
        verify.gridColumnWidthIs(
                "【機械検証】「会社ID」列の幅が " + idWidth + "px（テーブルの表示幅 " + tableWidth + "px の2等分）である", grid,
                CompanyMasterPage.COL_ID, idWidth);
        verify.gridColumnWidthIs(
                "【機械検証】「会社名」列の幅が " + nameWidth + "px（テーブルの表示幅 " + tableWidth
                        + "px の2等分。2列の幅の差は1px以内で、合計がテーブルの表示幅と一致する）である",
                grid, CompanyMasterPage.COL_NAME, nameWidth);
    }

    @Test
    @DisplayName("No.1-5 初期表示ではエラー表示（エラータイトル・エラー内容）が表示されない")
    void case1x5() {
        signInAsAdmin();
        openCompanyMaster(1);

        noErrorDisplayed("");
    }

    @Test
    @DisplayName("No.1-6 表示内容が列幅を超える会社名は、列幅に収まる範囲の文字列に三点リーダー（…）を付して表示される")
    void case1x6() {
        replaceCompanies(data("シード_長い会社名.csv"));
        signInAsAdmin();
        openCompanyMaster(1);

        verify.gridCellText("会社マスタテーブルの先頭行が ID 1 の行である", grid, 0, CompanyMasterPage.COL_ID, "1");
        verify.textTruncated(
                "ID 1 の会社名セルに、会社名の先頭から列幅に収まる範囲の文字列が表示され、その末尾に三点リーダー（…）が付く"
                        + "（会社名の50文字全体は表示されない。表示の先頭が「あいうえおかきくけこ」であることはフレームで目視確認）",
                grid.cell(0, CompanyMasterPage.COL_NAME));
    }

    @Test
    @DisplayName("No.1-7 会社マスタが0件の場合の表示（列ヘッダー・列ボディ・取得データ件数・ページネーション・表示ページセレクトボックス）")
    void case1x7() {
        replaceCompanies(data("シード_会社0件.csv"));
        signInAsAdmin();
        openCompanyMaster(1);

        verify.gridColumnHeaders("列ヘッダー「会社ID」「会社名」がデータの存在する場合と同様に表示される", grid,
                CompanyMasterPage.DEFAULT_HEADERS);
        verify.text("列ボディに「該当するデータがありません」が表示される（列間の区切り線が無いこと・中央寄せであることは CSS の描画のためフレームで目視確認）",
                companyPage.noRowsOverlay(), "該当するデータがありません");
        dataCountIs("", 0, "");
        for (int position : List.of(CompanyMasterPage.PAGER_TOP, CompanyMasterPage.PAGER_BOTTOM)) {
            String side = "テーブル" + sideOf(position);
            verify.texts(side + "のページネーションはページ番号「1」のみで、ボタンは先頭・前・ページ番号「1」・次・末尾の5個である",
                    companyPage.pagerButtons(position), PAGER_LABELS_SINGLE_PAGE);
            for (int i = 0; i < PAGER_LABELS_SINGLE_PAGE.length; i++) {
                verify.disabled(
                        "【機械検証】" + side + "のページネーションの" + PAGER_LABEL_NAMES[i] + "ボタン（"
                                + PAGER_LABELS_SINGLE_PAGE[i] + "）が非活性（クリック不可）である",
                        companyPage.pagerButton(position, PAGER_LABELS_SINGLE_PAGE[i]));
            }
        }
        pageJumpShows("", 1);
        pageJumpOptionsAre("", 1);
    }

    @Test
    @DisplayName("No.1-8 会社マスタ管理画面が共通レイアウト（共通ヘッダー＋メインビューポート）で表示される")
    void case1x8() {
        signInAsAdmin();
        openCompanyMaster(1);

        verify.visible("画面上部に共通ヘッダーが表示される", commonHeader.header());
        verify.text("共通ヘッダーにログイン中のユーザ名「管理者太郎」が表示される", commonHeader.userName(), "管理者太郎");
        verify.containsText("共通ヘッダーに部署名「営業部」が表示される", commonHeader.departmentName(), "営業部");
        verify.positionedAbove("共通ヘッダーの下にメインビューポートがある", commonHeader.header(),
                commonHeader.mainViewport());
        verify.text("メインビューポートに会社マスタ管理画面（画面タイトル「会社マスタ管理画面」）が表示される", commonHeader.screenTitle(),
                CompanyMasterPage.SCREEN_TITLE);
    }

    // ---- 2. 画面イベント処理（テーブル操作：列順序・列幅・ソート・スクロール） ----

    @Test
    @DisplayName("No.2-1 列順序の変更：左側の列ヘッダーを右側の列ヘッダーの上へドロップすると、右側の列の右側に挿入される")
    void case2x1() {
        signInAsAdmin();
        openCompanyMaster(1, "（列順は左から「会社ID」「会社名」）");

        verify.gridColumnHeaders("1. 列ヘッダーは左から「会社ID」「会社名」の順である", grid,
                CompanyMasterPage.DEFAULT_HEADERS);
        verify.op("2. 「会社ID」列ヘッダーをドラッグし、「会社名」列ヘッダーの上にドロップする",
                () -> grid.moveColumn(CompanyMasterPage.COL_ID, CompanyMasterPage.COL_NAME),
                grid.headerCell(CompanyMasterPage.COL_ID));
        verify.gridColumnHeaders("2. 列順が左から「会社名」「会社ID」に変わる（「会社ID」列が「会社名」列の右側に挿入される）", grid,
                List.of("会社名", "会社ID"));
        verify.gridCellText("2. 各行の値は列とともに移動し、先頭行の左の列（会社名）に「ワタナベ商事」と表示される", grid, 0,
                CompanyMasterPage.COL_NAME, "ワタナベ商事");
        verify.gridCellText("2. 先頭行の右の列（会社ID）に「1」と表示される", grid, 0, CompanyMasterPage.COL_ID, "1");
    }

    @Test
    @DisplayName("No.2-2 列順序の変更：右側の列ヘッダーを左側の列ヘッダーの上へドロップすると、左側の列の左側に挿入される")
    void case2x2() {
        signInAsAdmin();
        openCompanyMaster(1, "（列順は左から「会社ID」「会社名」）");

        verify.gridColumnHeaders("1. 列ヘッダーは左から「会社ID」「会社名」の順である", grid,
                CompanyMasterPage.DEFAULT_HEADERS);
        verify.op("2. 「会社名」列ヘッダーをドラッグし、「会社ID」列ヘッダーの上にドロップする",
                () -> grid.moveColumn(CompanyMasterPage.COL_NAME, CompanyMasterPage.COL_ID),
                grid.headerCell(CompanyMasterPage.COL_NAME));
        verify.gridColumnHeaders("2. 列順が左から「会社名」「会社ID」に変わる（「会社名」列が「会社ID」列の左側に挿入される）", grid,
                List.of("会社名", "会社ID"));
        verify.gridCellText("2. 各行の値は列とともに移動し、先頭行の左の列（会社名）に「ワタナベ商事」と表示される", grid, 0,
                CompanyMasterPage.COL_NAME, "ワタナベ商事");
        verify.gridCellText("2. 先頭行の右の列（会社ID）に「1」と表示される", grid, 0, CompanyMasterPage.COL_ID, "1");
    }

    @Test
    @DisplayName("No.2-3 列幅の変更：列ヘッダー間の境界線をドラッグすると、境界線の左側の列の幅が変わる")
    void case2x3() {
        signInAsAdmin();
        openCompanyMaster(1);

        final int before = companyPage.columnWidthPx(CompanyMasterPage.COL_ID);
        verify.op("2. 「会社ID」列ヘッダーと「会社名」列ヘッダーの間の境界線を右へ100pxドラッグする",
                () -> grid.resizeColumnBy(CompanyMasterPage.COL_ID, 100),
                grid.headerCell(CompanyMasterPage.COL_ID));
        verify.gridColumnWidthIs("境界線の左側にある「会社ID」列の幅が広がる（【機械検証】「会社ID」列の幅が手順1の時点の " + before
                + "px より100px広い " + (before + 100) + "px である）", grid, CompanyMasterPage.COL_ID,
                before + 100);
    }

    @Test
    @DisplayName("No.2-4 【境界値】列幅の最小値は80pxであり、80px未満には縮まない")
    void case2x4() {
        signInAsAdmin();
        openCompanyMaster(1);

        verify.op("2. 「会社ID」列と「会社名」列の間の境界線を左へドラッグし、「会社ID」列の幅を81pxにする",
                () -> grid.resizeColumnTo(CompanyMasterPage.COL_ID, 81),
                grid.headerCell(CompanyMasterPage.COL_ID));
        verify.gridColumnWidthIs("2. 「会社ID」列が狭くなる（【機械検証】手順2の後の「会社ID」列の幅が81pxである）", grid,
                CompanyMasterPage.COL_ID, 81);
        verify.op("3. さらに境界線を左へドラッグし、「会社ID」列の幅を79pxにしようとする（要求する幅を1pxずつ狭めて80px未満を要求する）",
                () -> grid.shrinkColumnToMinimum(CompanyMasterPage.COL_ID),
                grid.headerCell(CompanyMasterPage.COL_ID));
        verify.text("3. 「会社ID」列は消えずに表示され、列ヘッダー「会社ID」が表示される",
                grid.headerLabel(grid.headerCell(CompanyMasterPage.COL_ID)), "会社ID");
        verify.gridRowsByKey("3. 各行の会社ID（ID 1・2・10・100）が表示される", grid, CompanyMasterPage.COL_ID,
                BASELINE_IDS);
        verify.gridColumnWidthIs("3. 最小幅より狭くならない（【機械検証】手順3の後の「会社ID」列の幅が80px（最小値）である）", grid,
                CompanyMasterPage.COL_ID, 80);
    }

    @Test
    @DisplayName("No.2-5 テーブルの列が画面に収まらない場合、テーブル内で横スクロールする")
    void case2x5() {
        signInAsAdmin();
        openCompanyMaster(1);

        final int tableWidth = companyPage.gridBodyWidthPx();
        verify.op("2. 「会社ID」列と「会社名」列の間の境界線を右へドラッグし、「会社ID」列の幅をテーブルの表示幅（" + tableWidth + "px）と同じ幅にする",
                () -> grid.resizeColumnTo(CompanyMasterPage.COL_ID, tableWidth),
                grid.headerCell(CompanyMasterPage.COL_ID));
        verify.visible("2. テーブル内に横スクロールバーが表示される", companyPage.horizontalScrollbar());

        verify.positionsUnchanged("3. テーブル内の横スクロールに伴って、テーブルの外側（画面タイトル・取得データ件数・ページネーション）は移動しない",
                List.of(commonHeader.screenTitle(),
                        companyPage.dataCountLabel(CompanyMasterPage.PAGER_TOP),
                        companyPage.pagerNums(CompanyMasterPage.PAGER_TOP)),
                grid.root(), () -> {
                    verify.gridRowsByKey("3. 最初のフレーム（左端）では「会社ID」列に ID 1・2・10・100 が表示される", grid,
                            CompanyMasterPage.COL_ID, BASELINE_IDS);
                    verify.gridColumnHeaders(
                            "3. テーブル内を右端まで横スクロールし、列ヘッダー「会社ID」「会社名」が順に表示される"
                                    + "（各フレームのキャプションにフレーム n/N・列範囲を記録）",
                            grid, CompanyMasterPage.DEFAULT_HEADERS);
                    for (int i = 0; i < BASELINE_IDS.size(); i++) {
                        verify.gridCellText(
                                "3. 右端のフレームでは「会社名」列に ID " + BASELINE_IDS.get(i) + " の行の会社名（セルの値「"
                                        + BASELINE_NAMES.get(i) + "」）が表示される"
                                        + "（最小幅80pxの列に収まらない部分の省略表示はフレームで目視確認）",
                                grid, i, CompanyMasterPage.COL_NAME, BASELINE_NAMES.get(i));
                    }
                });
    }

    @Test
    @DisplayName("No.2-6 テーブルの行が画面に収まらない場合、テーブル内で縦スクロールする")
    void case2x6() {
        replaceCompanies(data("シード_会社30件.csv"));
        signInAsAdmin();
        openCompanyMaster(1);

        verify.gridCellText("1. テーブル内に ID 1 から順に行が表示される（先頭行が ID 1）", grid, 0,
                CompanyMasterPage.COL_ID, "1");
        verify.visible("1. 30行はテーブル内に収まらず、テーブル内に縦スクロールバーが表示される", companyPage.verticalScrollbar());
        verify.positionsUnchanged("2. テーブル内の縦スクロールに伴って、テーブルの外側（画面タイトル・上部の取得データ件数・ページネーション）は移動しない",
                List.of(commonHeader.screenTitle(),
                        companyPage.dataCountLabel(CompanyMasterPage.PAGER_TOP),
                        companyPage.pagerNums(CompanyMasterPage.PAGER_TOP)),
                grid.root(),
                () -> verify.gridRowsByKey(
                        "2. テーブル内を末尾まで縦スクロールし、連続するフレームで ID 1〜30 の行が昇順に途切れなく表示される"
                                + "（各フレームのキャプションにフレーム n/N・ID範囲を記録。【機械検証】ID 30 の行の次の行が存在しない）",
                        grid, CompanyMasterPage.COL_ID, ascendingKeys(1, 30)));
        verify.gridCellText("2. 最後のフレームの末尾行は ID 30「テスト会社３０」である", grid, 29,
                CompanyMasterPage.COL_NAME, testCompanyName(30));
    }

    @Test
    @DisplayName("No.2-7 ソート切替（会社ID列）：ダブルクリックで昇順→降順→ソートなし→昇順と切り替わり、会社IDは数値の大小で並ぶ")
    void case2x7() {
        signInAsAdmin();
        openCompanyMaster(1, "（会社ID列には昇順が適用済み）");

        verify.gridRowsByKey("1. 行は ID 1・2・10・100 の順である", grid, CompanyMasterPage.COL_ID,
                BASELINE_IDS);
        verify.visible("1. 「会社ID」列ヘッダーに昇順の意匠が表示される",
                companyPage.sortAscendingIcon(CompanyMasterPage.COL_ID));

        doubleClickHeader("2. 「会社ID」列ヘッダーをダブルクリックする", CompanyMasterPage.COL_ID);
        verify.gridRowsByKey("2. 適用済みの昇順を起点として降順に切り替わり、行は ID 100・10・2・1 の順（数値の降順）になる", grid,
                CompanyMasterPage.COL_ID, List.of("100", "10", "2", "1"));
        verify.visible("2. 「会社ID」列ヘッダーに降順の意匠が表示される",
                companyPage.sortDescendingIcon(CompanyMasterPage.COL_ID));

        verify.transitionHide("「会社ID」列ヘッダーにソートの意匠が表示されている",
                companyPage.visibleSortIcon(CompanyMasterPage.COL_ID), "3. 「会社ID」列ヘッダーをダブルクリックする",
                () -> companyPage.doubleClickHeader(CompanyMasterPage.COL_ID),
                grid.headerCell(CompanyMasterPage.COL_ID), "3. ソートなしに切り替わり、「会社ID」列ヘッダーのソートの意匠が消える");
        verify.gridRowsByKey("3. 行は既定の並び順（ID 1・2・10・100）になる", grid, CompanyMasterPage.COL_ID,
                BASELINE_IDS);

        doubleClickHeader("4. 「会社ID」列ヘッダーをダブルクリックする", CompanyMasterPage.COL_ID);
        verify.gridRowsByKey("4. 再び昇順に切り替わり、行は ID 1・2・10・100 の順になる", grid, CompanyMasterPage.COL_ID,
                BASELINE_IDS);
        verify.visible("4. 「会社ID」列ヘッダーに昇順の意匠が表示される",
                companyPage.sortAscendingIcon(CompanyMasterPage.COL_ID));
    }

    @Test
    @DisplayName("No.2-8 ソート切替（会社名列）：会社名は文字列としてソートされる")
    void case2x8() {
        signInAsAdmin();
        openCompanyMaster(1);

        doubleClickHeader("2. 「会社名」列ヘッダーをダブルクリックする", CompanyMasterPage.COL_NAME);
        rowsAre("2. 会社名の昇順になり、行は「アオバ物産」（ID 2）・「カシワ工業」（ID 10）・「サクラ電機」（ID 100）・「ワタナベ商事」（ID 1）の順になる",
                grid, NAME_ASC_IDS, NAME_ASC_NAMES);
        verify.visible("2. 「会社名」列ヘッダーに昇順の意匠が表示される",
                companyPage.sortAscendingIcon(CompanyMasterPage.COL_NAME));

        doubleClickHeader("3. 「会社名」列ヘッダーをダブルクリックする", CompanyMasterPage.COL_NAME);
        rowsAre("3. 会社名の降順になり、行は「ワタナベ商事」（ID 1）・「サクラ電機」（ID 100）・「カシワ工業」（ID 10）・「アオバ物産」（ID 2）の順になる",
                grid, NAME_DESC_IDS, NAME_DESC_NAMES);
        verify.visible("3. 「会社名」列ヘッダーに降順の意匠が表示される",
                companyPage.sortDescendingIcon(CompanyMasterPage.COL_NAME));
    }

    @Test
    @DisplayName("No.2-9 別の列ヘッダーをダブルクリックすると既存のソートが解除され、ダブルクリックした列のみにソートが適用される（複数列ソートは行わない）")
    void case2x9() {
        signInAsAdmin();
        openCompanyMaster(1, "（会社ID列には昇順が適用済み）");

        verify.transitionHide("1. 「会社ID」列ヘッダーにソートの意匠が表示されている（昇順が適用済み）",
                companyPage.visibleSortIcon(CompanyMasterPage.COL_ID), "2. 「会社名」列ヘッダーをダブルクリックする",
                () -> companyPage.doubleClickHeader(CompanyMasterPage.COL_NAME),
                grid.headerCell(CompanyMasterPage.COL_NAME), "2. 「会社ID」列ヘッダーのソートの意匠が消える");
        verify.visible("2. 「会社名」列ヘッダーにのみ昇順の意匠が表示される",
                companyPage.sortAscendingIcon(CompanyMasterPage.COL_NAME));
        verify.gridRowsByKey("2. 行は会社名の昇順（ID 2・10・100・1）になる", grid, CompanyMasterPage.COL_ID,
                NAME_ASC_IDS);

        verify.transitionHide("「会社名」列ヘッダーにソートの意匠が表示されている",
                companyPage.visibleSortIcon(CompanyMasterPage.COL_NAME), "3. 「会社ID」列ヘッダーをダブルクリックする",
                () -> companyPage.doubleClickHeader(CompanyMasterPage.COL_ID),
                grid.headerCell(CompanyMasterPage.COL_ID), "3. 「会社名」列ヘッダーのソートの意匠が消える");
        verify.visible("3. 「会社ID」列ヘッダーにのみ昇順の意匠が表示される",
                companyPage.sortAscendingIcon(CompanyMasterPage.COL_ID));
        verify.gridRowsByKey("3. 行は ID 1・2・10・100 の順になる", grid, CompanyMasterPage.COL_ID,
                BASELINE_IDS);
    }

    @Test
    @DisplayName("No.2-10 すべての列がソートなしの場合は、会社ID列の昇順が既定の並び順となる")
    void case2x10() {
        signInAsAdmin();
        openCompanyMaster(1);

        doubleClickHeader("2. 「会社名」列ヘッダーをダブルクリックする（会社名の昇順）", CompanyMasterPage.COL_NAME);
        doubleClickHeader("3. 「会社名」列ヘッダーをダブルクリックする（会社名の降順）", CompanyMasterPage.COL_NAME);
        verify.gridRowsByKey("3. 行は会社名の降順（ID 1・100・10・2）で表示される", grid, CompanyMasterPage.COL_ID,
                NAME_DESC_IDS);

        verify.transitionHide("「会社名」列ヘッダーにソートの意匠が表示されている",
                companyPage.visibleSortIcon(CompanyMasterPage.COL_NAME),
                "4. 「会社名」列ヘッダーをダブルクリックする（ソートなし）",
                () -> companyPage.doubleClickHeader(CompanyMasterPage.COL_NAME),
                grid.headerCell(CompanyMasterPage.COL_NAME), "4. 「会社名」列ヘッダーのソートの意匠が消える");
        verify.absent("4. 「会社ID」列ヘッダーにもソートの意匠が表示されない", grid.headerCell(CompanyMasterPage.COL_ID),
                companyPage.visibleSortIcon(CompanyMasterPage.COL_ID));
        verify.gridRowsByKey("4. 行は会社IDの昇順（ID 1・2・10・100）で表示される", grid, CompanyMasterPage.COL_ID,
                BASELINE_IDS);
    }

    // ---- 3. 画面イベント処理（テーブル操作：表示件数・ページ送り） ----

    @Test
    @DisplayName("No.3-1 【境界値】取得件数が表示件数と等しい場合（30件・表示件数30件）は、ページが分かれず1ページに表示される")
    void case3x1() {
        replaceCompanies(data("シード_会社30件.csv"));
        signInAsAdmin();
        openCompanyMaster(1, "（表示件数は初期値の30件）");

        dataCountIs("", 30, "");
        pageJumpShows("", 1);
        pageJumpOptionsAre("", 1);
        verify.gridRowsByKey(
                "2. テーブル内を末尾まで縦スクロールし、1ページ目に ID 1〜30 の全行が表示される"
                        + "（各フレームのキャプションにフレーム n/N・ID範囲を記録。【機械検証】ID 30 の行の次の行が存在しない）",
                grid, CompanyMasterPage.COL_ID, ascendingKeys(1, 30));
        verify.gridCellText("2. 末尾行が ID 30「テスト会社３０」である", grid, 29, CompanyMasterPage.COL_NAME,
                testCompanyName(30));
    }

    @Test
    @DisplayName("No.3-2 【境界値】取得件数が表示件数を1件超える場合（31件・表示件数30件）は、2ページに分かれる")
    void case3x2() {
        replaceCompanies(data("シード_会社31件.csv"));
        signInAsAdmin();
        openCompanyMaster(1, "（表示件数は初期値の30件）");

        dataCountIs("", 31, "");
        pageJumpOptionsAre("", 2);
        verify.gridRowsByKey(
                "2. テーブル内を末尾まで縦スクロールし、1ページ目に ID 1〜30 が表示される"
                        + "（各フレームのキャプションにフレーム n/N・ID範囲を記録。【機械検証】1ページ目で ID 30 の行の次の行が存在しない）",
                grid, CompanyMasterPage.COL_ID, ascendingKeys(1, 30));
        verify.gridCellText("2. 1ページ目の末尾行が ID 30「テスト会社３０」である", grid, 29, CompanyMasterPage.COL_NAME,
                testCompanyName(30));

        selectPage(3, CompanyMasterPage.PAGER_TOP, 2);
        verify.gridRowsByKey("3. 2ページ目に ID 31 の1行のみが表示される", companyPage.grid(30),
                CompanyMasterPage.COL_ID, List.of("31"));
        verify.gridCellText("3. 2ページ目の行の会社名が「テスト会社３１」である", companyPage.grid(30), 0,
                CompanyMasterPage.COL_NAME, testCompanyName(31));
    }

    @Test
    @DisplayName("No.3-3 表示件数セレクトボックス（上部）で10件を選択すると1ページに10件ずつ表示され、下部の表示件数も連動し、表示ページの選択肢が全ページ番号になる")
    void case3x3() {
        replaceCompanies(data("シード_会社31件.csv"));
        signInAsAdmin();
        openCompanyMaster(1);

        selectPageSize(2, CompanyMasterPage.PAGER_TOP, "10件", "10");
        verify.gridRowsByKey("2. テーブルに ID 1〜10 の10行が表示され、ID 11 以降は表示されない（【機械検証】ID 10 の行の次の行が存在しない）",
                grid, CompanyMasterPage.COL_ID, ascendingKeys(1, 10));
        verify.selectedOption("2. テーブル下部の表示件数セレクトボックスも「10件」の表示になる",
                companyPage.pageSizeSelect(CompanyMasterPage.PAGER_BOTTOM), "10件");
        pageJumpShows("2. ", 1);
        pageJumpOptionsAre("2. ", 4, "（全ページ番号）");
        dataCountIs("2. ", 31, "（のまま変わらない）");
    }

    @Test
    @DisplayName("No.3-4 表示件数セレクトボックス（下部）で20件を選択すると1ページに20件ずつ表示され、上部の表示件数も連動する")
    void case3x4() {
        replaceCompanies(data("シード_会社31件.csv"));
        signInAsAdmin();
        openCompanyMaster(1);

        selectPageSize(2, CompanyMasterPage.PAGER_BOTTOM, "20件", "20");
        verify.selectedOption("2. テーブル上部の表示件数セレクトボックスも「20件」の表示になる",
                companyPage.pageSizeSelect(CompanyMasterPage.PAGER_TOP), "20件");
        pageJumpOptionsAre("2. ", 2);
        verify.gridRowsByKey(
                "3. テーブル内を末尾まで縦スクロールし、1ページ目に ID 1〜20 が表示される"
                        + "（各フレームのキャプションにフレーム n/N・ID範囲を記録。【機械検証】ID 20 の行の次の行が存在しない）",
                grid, CompanyMasterPage.COL_ID, ascendingKeys(1, 20));
        verify.gridCellText("3. 末尾行が ID 20「テスト会社２０」である", grid, 19, CompanyMasterPage.COL_NAME,
                testCompanyName(20));
    }

    @Test
    @DisplayName("No.3-5 表示件数セレクトボックスで「全て」を選択すると、全件が1ページに表示される")
    void case3x5() {
        replaceCompanies(data("シード_会社31件.csv"));
        signInAsAdmin();
        openCompanyMaster(1);

        selectPageSize(2, CompanyMasterPage.PAGER_TOP, "全て", "all");
        verify.selectedOption("2. テーブル下部の表示件数セレクトボックスも「全て」の表示になる",
                companyPage.pageSizeSelect(CompanyMasterPage.PAGER_BOTTOM), "全て");
        pageJumpOptionsAre("2. ", 1);
        verify.gridRowsByKey(
                "3. テーブル内を末尾まで縦スクロールし、1ページ目に ID 1〜31 の全行が表示される"
                        + "（各フレームのキャプションにフレーム n/N・ID範囲を記録。【機械検証】ID 31 の行の次の行が存在しない）",
                grid, CompanyMasterPage.COL_ID, ascendingKeys(1, 31));
        verify.gridCellText("3. 末尾行が ID 31「テスト会社３１」である", grid, 30, CompanyMasterPage.COL_NAME,
                testCompanyName(31));
    }

    @Test
    @DisplayName("No.3-6 表示件数を30件に戻すと、30件ごとに再ページングされる")
    void case3x6() {
        replaceCompanies(data("シード_会社31件.csv"));
        signInAsAdmin();
        openCompanyMaster(1);

        selectPageSize(2, CompanyMasterPage.PAGER_TOP, "10件", "10");
        verify.gridRowsByKey("2. テーブルに ID 1〜10 の10行が表示される", grid, CompanyMasterPage.COL_ID,
                ascendingKeys(1, 10));
        selectPageSize(3, CompanyMasterPage.PAGER_BOTTOM, "30件", "30");
        verify.selectedOption("3. テーブル上部の表示件数セレクトボックスも「30件」の表示になる",
                companyPage.pageSizeSelect(CompanyMasterPage.PAGER_TOP), "30件");
        pageJumpOptionsAre("3. ", 2);
        verify.gridRowsByKey(
                "4. テーブル内を末尾まで縦スクロールし、1ページ目に ID 1〜30 が表示される"
                        + "（各フレームのキャプションにフレーム n/N・ID範囲を記録。【機械検証】ID 30 の行の次の行が存在しない）",
                grid, CompanyMasterPage.COL_ID, ascendingKeys(1, 30));
        verify.gridCellText("4. 末尾行が ID 30「テスト会社３０」である", grid, 29, CompanyMasterPage.COL_NAME,
                testCompanyName(30));
    }

    @Test
    @DisplayName("No.3-7 ページネーションの先頭ページボタン（上部・下部それぞれ）で先頭ページに切り替わる")
    void case3x7() {
        replaceCompanies(data("シード_会社31件.csv"));
        signInAsAdmin();
        openCompanyMaster(1);

        selectPageSize(2, CompanyMasterPage.PAGER_TOP, "10件", "10");
        selectPage(3, CompanyMasterPage.PAGER_TOP, 3);
        pageRowsAre("3. 3ページ目（ID 21〜30）が表示される", 21, 30);
        pressPager(4, CompanyMasterPage.PAGER_TOP, "先頭ページボタン", "«");
        pageRowsAre("4. 1ページ目（ID 1〜10）が表示される", 1, 10);
        pageJumpShows("4. ", 1);
        selectPage(5, CompanyMasterPage.PAGER_TOP, 3);
        pageRowsAre("5. 3ページ目（ID 21〜30）が表示される", 21, 30);
        pressPager(6, CompanyMasterPage.PAGER_BOTTOM, "先頭ページボタン", "«");
        pageRowsAre("6. 1ページ目（ID 1〜10）が表示される", 1, 10);
        pageJumpShows("6. ", 1);
    }

    @Test
    @DisplayName("No.3-8 ページネーションの前ページボタン（上部・下部それぞれ）でひとつ前のページに切り替わる")
    void case3x8() {
        replaceCompanies(data("シード_会社31件.csv"));
        signInAsAdmin();
        openCompanyMaster(1);

        selectPageSize(2, CompanyMasterPage.PAGER_TOP, "10件", "10");
        selectPage(3, CompanyMasterPage.PAGER_TOP, 3);
        pageRowsAre("3. 3ページ目（ID 21〜30）が表示される", 21, 30);
        pressPager(4, CompanyMasterPage.PAGER_TOP, "前ページボタン", "‹");
        pageRowsAre("4. 2ページ目（ID 11〜20）が表示される", 11, 20);
        pageJumpShows("4. ", 2);
        pressPager(5, CompanyMasterPage.PAGER_BOTTOM, "前ページボタン", "‹");
        pageRowsAre("5. 1ページ目（ID 1〜10）が表示される", 1, 10);
        pageJumpShows("5. ", 1);
    }

    @Test
    @DisplayName("No.3-9 ページネーションのページ番号ボタン（上部・下部それぞれ）で、ボタンの番号のページに切り替わる")
    void case3x9() {
        replaceCompanies(data("シード_会社31件.csv"));
        signInAsAdmin();
        openCompanyMaster(1);

        selectPageSize(2, CompanyMasterPage.PAGER_TOP, "10件", "10");
        pressPager(3, CompanyMasterPage.PAGER_TOP, "ページ番号ボタン", "3");
        pageRowsAre("3. 3ページ目（ID 21〜30）が表示される", 21, 30);
        pageJumpShows("3. ", 3);
        pressPager(4, CompanyMasterPage.PAGER_BOTTOM, "ページ番号ボタン", "2");
        pageRowsAre("4. 2ページ目（ID 11〜20）が表示される", 11, 20);
        pageJumpShows("4. ", 2);
    }

    @Test
    @DisplayName("No.3-10 ページネーションの次ページボタン（上部・下部それぞれ）でひとつ後のページに切り替わる")
    void case3x10() {
        replaceCompanies(data("シード_会社31件.csv"));
        signInAsAdmin();
        openCompanyMaster(1);

        selectPageSize(2, CompanyMasterPage.PAGER_TOP, "10件", "10");
        pressPager(3, CompanyMasterPage.PAGER_TOP, "次ページボタン", "›");
        pageRowsAre("3. 2ページ目（ID 11〜20）が表示される", 11, 20);
        pageJumpShows("3. ", 2);
        pressPager(4, CompanyMasterPage.PAGER_BOTTOM, "次ページボタン", "›");
        pageRowsAre("4. 3ページ目（ID 21〜30）が表示される", 21, 30);
        pageJumpShows("4. ", 3);
    }

    @Test
    @DisplayName("No.3-11 ページネーションの末尾ページボタン（上部・下部それぞれ）で末尾のページに切り替わる")
    void case3x11() {
        replaceCompanies(data("シード_会社31件.csv"));
        signInAsAdmin();
        openCompanyMaster(1);

        selectPageSize(2, CompanyMasterPage.PAGER_TOP, "10件", "10");
        pressPager(3, CompanyMasterPage.PAGER_TOP, "末尾ページボタン", "»");
        lastPageShown("3. ");
        selectPage(4, CompanyMasterPage.PAGER_TOP, 1);
        pageRowsAre("4. 1ページ目（ID 1〜10）が表示される", 1, 10);
        pressPager(5, CompanyMasterPage.PAGER_BOTTOM, "末尾ページボタン", "»");
        lastPageShown("5. ");
    }

    /** 表示件数10件の4ページ目（ID 31 の1行のみ）が表示されていることを確認する。 */
    private void lastPageShown(String prefix) {
        verify.gridRowsByKey(prefix + "4ページ目に ID 31 の1行のみが表示される", companyPage.grid(30),
                CompanyMasterPage.COL_ID, List.of("31"));
        verify.gridCellText(prefix + "4ページ目の行の会社名が「テスト会社３１」である", companyPage.grid(30), 0,
                CompanyMasterPage.COL_NAME, testCompanyName(31));
        pageJumpShows(prefix, 4);
    }

    @Test
    @DisplayName("No.3-12 表示ページセレクトボックス（上部・下部それぞれ）で選択したページに切り替わる")
    void case3x12() {
        replaceCompanies(data("シード_会社31件.csv"));
        signInAsAdmin();
        openCompanyMaster(1);

        selectPageSize(2, CompanyMasterPage.PAGER_TOP, "10件", "10");
        selectPage(3, CompanyMasterPage.PAGER_TOP, 4);
        verify.gridRowsByKey("3. 4ページ目に ID 31 の1行のみが表示される", companyPage.grid(30),
                CompanyMasterPage.COL_ID, List.of("31"));
        verify.gridCellText("3. 4ページ目の行の会社名が「テスト会社３１」である", companyPage.grid(30), 0,
                CompanyMasterPage.COL_NAME, testCompanyName(31));
        verify.selectedOption("3. テーブル下部の表示ページセレクトボックスもページ番号4の表示になる",
                companyPage.pageJumpSelect(CompanyMasterPage.PAGER_BOTTOM), "4", PAGE_NUMBER);
        selectPage(4, CompanyMasterPage.PAGER_BOTTOM, 2);
        pageRowsAre("4. 2ページ目（ID 11〜20）が表示される", 11, 20);
        verify.selectedOption("4. テーブル上部の表示ページセレクトボックスもページ番号2の表示になる",
                companyPage.pageJumpSelect(CompanyMasterPage.PAGER_TOP), "2", PAGE_NUMBER);
    }

    @Test
    @DisplayName("No.3-13 テーブル操作（列順序・列幅・ソート・ページ送り・表示件数変更）は画面内で完結し、サーバーとの通信を伴わない")
    void case3x13() {
        replaceCompanies(data("シード_会社31件.csv"));
        signInAsAdmin();
        openCompanyMaster(1);

        noServerRequestDuring("【機械検証】手順2〜6の操作の間、ブラウザからサーバーへのリクエストが発生しない（0件）", () -> {
            verify.op("2. 「会社ID」列ヘッダーをドラッグし、「会社名」列ヘッダーの上にドロップする",
                    () -> grid.moveColumn(CompanyMasterPage.COL_ID, CompanyMasterPage.COL_NAME),
                    grid.headerCell(CompanyMasterPage.COL_ID));
            verify.gridColumnHeaders("2. 列順が左から「会社名」「会社ID」になる", grid, List.of("会社名", "会社ID"));

            final int before = companyPage.columnWidthPx(CompanyMasterPage.COL_NAME);
            verify.op("3. 列ヘッダー間の境界線を右へ100pxドラッグする",
                    () -> grid.resizeColumnBy(CompanyMasterPage.COL_NAME, 100),
                    grid.headerCell(CompanyMasterPage.COL_NAME));
            verify.gridColumnWidthIs("3. 境界線の左側の「会社名」列の幅が広がる（手順2の時点の " + before + "px から "
                    + (before + 100) + "px になる）", grid, CompanyMasterPage.COL_NAME, before + 100);

            doubleClickHeader("4. 「会社名」列ヘッダーをダブルクリックする", CompanyMasterPage.COL_NAME);
            verify.visible("4. 「会社名」列ヘッダーに昇順の意匠が表示される",
                    companyPage.sortAscendingIcon(CompanyMasterPage.COL_NAME));

            selectPageSize(5, CompanyMasterPage.PAGER_TOP, "10件", "10");
            verify.gridRowsByKey("5. 1ページに10行（「テスト会社０１」〜「テスト会社１０」）が表示される", grid,
                    CompanyMasterPage.COL_NAME, testCompanyNames(1, 10));

            pressPager(6, CompanyMasterPage.PAGER_TOP, "次ページボタン", "›");
            verify.gridRowsByKey("6. 2ページ目（「テスト会社１１」〜「テスト会社２０」）が表示される", companyPage.grid(10),
                    CompanyMasterPage.COL_NAME, testCompanyNames(11, 20));
        });
    }

    // ---- 4. 画面遷移 ----

    @Test
    @DisplayName("No.4-1 共通ヘッダーの会社マスタボタンを押下すると会社マスタ管理画面へ遷移する")
    void case4x1() {
        signInAsAdmin();
        openProjectList(1);

        pressCompanyNav(2);
        verify.urlIs("2. /master/companies へ遷移する", companyPage.url());
        verify.text("2. 会社マスタ管理画面（画面タイトル「会社マスタ管理画面」）が表示される", commonHeader.screenTitle(),
                CompanyMasterPage.SCREEN_TITLE);
        verify.gridRowsByKey("2. 会社マスタテーブルに4件（ID 1・2・10・100）が一覧表示される", grid,
                CompanyMasterPage.COL_ID, BASELINE_IDS);
    }

    @Test
    @DisplayName("No.4-2 会社マスタ管理画面の表示中は、共通ヘッダーの会社マスタボタンが強調表示される")
    void case4x2() {
        signInAsAdmin();
        openProjectList(1);

        verify.absent("1. 共通ヘッダーの「会社マスタ」ボタンは強調表示されていない", commonHeader.masterNavGroup(),
                commonHeader.activeNav(CommonHeaderPage.NAV_COMPANY));
        verify.visible("1. 「一覧・検索」ボタンが強調表示されている",
                commonHeader.activeNav(CommonHeaderPage.NAV_LIST));

        pressCompanyNav(2);
        verify.text("2. 会社マスタ管理画面が表示される", commonHeader.screenTitle(),
                CompanyMasterPage.SCREEN_TITLE);
        verify.visible("2. 共通ヘッダーの「会社マスタ」ボタンが強調表示される",
                commonHeader.activeNav(CommonHeaderPage.NAV_COMPANY));
        verify.absent("2. 「一覧・検索」ボタンは強調表示されない", commonHeader.projectNavGroup(),
                commonHeader.activeNav(CommonHeaderPage.NAV_LIST));
    }

    // ---- 5. 外部入出力（CSVダウンロード） ----

    /** CSV ダウンロードの内容を期待値正本と突合する。 */
    private void expectDownload(int stepNo, String contentDesc, String expectedFile) {
        verify.csvDownloaded(stepNo + ". 「CSVダウンロード」ボタンを押下する：ダウンロードしたファイルの内容（代理証跡）は、" + contentDesc
                + "（【機械検証】ダウンロードされたファイルの名前が「会社マスタ.csv」で、バイト列が期待値ファイル " + expectedFile
                + " と一致する：文字コード UTF-8 BOMあり・改行コード CR+LF・区切り文字カンマ・囲み文字なし・ヘッダーの「ID」は半角・会社IDは0埋め無し）",
                CSV_FILE_NAME, data(expectedFile), () -> companyPage.pressCsvDownload());
    }

    @Test
    @DisplayName("No.5-1 初期表示の状態でCSVダウンロードすると、全件が会社IDの昇順で既定の様式のCSVファイル「会社マスタ.csv」に出力される")
    void case5x1() {
        signInAsAdmin();
        openCompanyMaster(1, "（会社ID列の昇順）");

        expectDownload(2,
                "1行目がヘッダー行「会社ID,会社名」、2行目以降がボディ行「1,ワタナベ商事」「2,アオバ物産」「10,カシワ工業」「100,サクラ電機」の順である",
                CSV_EXPECTED_ID_ASC);
    }

    @Test
    @DisplayName("No.5-2 会社IDの降順で表示中にCSVダウンロードすると、ボディ行が表示中のソート順（会社IDの降順）で出力される")
    void case5x2() {
        signInAsAdmin();
        openCompanyMaster(1);

        doubleClickHeader("2. 「会社ID」列ヘッダーをダブルクリックする（会社IDの降順）", CompanyMasterPage.COL_ID);
        expectDownload(3, "ヘッダー行「会社ID,会社名」に続き、ボディ行が「100,サクラ電機」「10,カシワ工業」「2,アオバ物産」「1,ワタナベ商事」の順である",
                "CSV期待_会社ID降順.csv");
    }

    @Test
    @DisplayName("No.5-3 会社名の昇順で表示中にCSVダウンロードすると、ボディ行が会社名の昇順で出力される")
    void case5x3() {
        signInAsAdmin();
        openCompanyMaster(1);

        doubleClickHeader("2. 「会社名」列ヘッダーをダブルクリックする（会社名の昇順）", CompanyMasterPage.COL_NAME);
        expectDownload(3, "ヘッダー行「会社ID,会社名」に続き、ボディ行が「2,アオバ物産」「10,カシワ工業」「100,サクラ電機」「1,ワタナベ商事」の順である",
                "CSV期待_会社名昇順.csv");
    }

    @Test
    @DisplayName("No.5-4 会社名の降順で表示中にCSVダウンロードすると、ボディ行が会社名の降順で出力される")
    void case5x4() {
        signInAsAdmin();
        openCompanyMaster(1);

        doubleClickHeader("2. 「会社名」列ヘッダーをダブルクリックする（会社名の昇順）", CompanyMasterPage.COL_NAME);
        doubleClickHeader("3. 「会社名」列ヘッダーをダブルクリックする（会社名の降順）", CompanyMasterPage.COL_NAME);
        expectDownload(4, "ヘッダー行「会社ID,会社名」に続き、ボディ行が「1,ワタナベ商事」「100,サクラ電機」「10,カシワ工業」「2,アオバ物産」の順である",
                "CSV期待_会社名降順.csv");
    }

    @Test
    @DisplayName("No.5-5 すべての列がソートなしの状態でCSVダウンロードすると、ボディ行が既定の並び順（会社IDの昇順）で出力される")
    void case5x5() {
        signInAsAdmin();
        openCompanyMaster(1);

        doubleClickHeader("2. 「会社ID」列ヘッダーをダブルクリックする（会社IDの降順）", CompanyMasterPage.COL_ID);
        doubleClickHeader("3. 「会社ID」列ヘッダーをダブルクリックする（ソートなし）", CompanyMasterPage.COL_ID);
        expectDownload(4, "ヘッダー行「会社ID,会社名」に続き、ボディ行が「1,ワタナベ商事」「2,アオバ物産」「10,カシワ工業」「100,サクラ電機」の順である",
                CSV_EXPECTED_ID_ASC);
    }

    @Test
    @DisplayName("No.5-6 テーブルの列順序を変更した状態でCSVダウンロードしても、ファイルの列順は定義どおり（会社ID、会社名）である")
    void case5x6() {
        signInAsAdmin();
        openCompanyMaster(1);

        verify.op("2. 「会社ID」列ヘッダーをドラッグし、「会社名」列ヘッダーの上にドロップする（列順を左から「会社名」「会社ID」にする）",
                () -> grid.moveColumn(CompanyMasterPage.COL_ID, CompanyMasterPage.COL_NAME),
                grid.headerCell(CompanyMasterPage.COL_ID));
        expectDownload(3,
                "ヘッダー行が「会社ID,会社名」（会社ID が先）で、ボディ行が「1,ワタナベ商事」「2,アオバ物産」「10,カシワ工業」「100,サクラ電機」の順である",
                CSV_EXPECTED_ID_ASC);
    }

    @Test
    @DisplayName("No.5-7 会社マスタが0件の場合、ヘッダー行のみのCSVファイルが出力される")
    void case5x7() {
        replaceCompanies(data("シード_会社0件.csv"));
        signInAsAdmin();
        openCompanyMaster(1);

        expectDownload(2, "ヘッダー行「会社ID,会社名」の1行のみで、ボディ行が無い", "CSV期待_0件.csv");
    }

    // ---- 6. 外部入出力（CSVアップロード・洗い替え） ----

    @Test
    @DisplayName("No.6-1 CSVアップロードボタンを押下するとファイル選択ダイアログ（ブラウザ標準）が開き、選択できるファイル形式はCSVのみである")
    void case6x1() {
        signInAsAdmin();
        openCompanyMaster(1);

        verify.opPressFileChooser(
                "2. 「CSVアップロード」ボタンを押下する（【機械検証】手順1の時点ではファイル選択ダイアログが開かれていない（ファイル選択の要求が0件）・"
                        + "押下でファイル選択ダイアログが開く（要求が1件発生する）・選択できるファイル形式が CSV（拡張子 .csv）に限定されている）",
                companyPage.csvUploadButton(), List.of(".csv"));
        verify.opFrame("3. ファイルを指定せずにファイル選択ダイアログを閉じる（ダイアログへファイルを設定しない）");
        verify.text("3. 画面は変わらず、会社マスタ管理画面が表示されている", commonHeader.screenTitle(),
                CompanyMasterPage.SCREEN_TITLE);
        rowsAre("3. 会社マスタテーブルはベースラインの4件（" + BASELINE_DESC + "）のまま", grid, BASELINE_IDS,
                BASELINE_NAMES);
        noErrorDisplayed("3. ");
    }

    @Test
    @DisplayName("No.6-2 ファイル選択ダイアログでCSVファイルを指定すると洗い替えが実行され、既存データが削除されてファイルの内容のみが会社IDの昇順で一覧再表示される")
    void case6x2() {
        signInAsAdmin();
        openCompanyMaster(1, "（4件が表示されている）");

        upload("2. 「CSVアップロード」ボタンを押下し、ファイル選択ダイアログで 洗い替え_正常.csv"
                + "（ファイル名は「会社マスタ.csv」以外。行順は ID 300・10・20）を指定する" + "（指定のほかに送信の操作は行わない）",
                data(UPLOAD_REPLACE), grid.root());
        verify.text("ファイルを指定しただけで洗い替えが実行され、会社マスタ管理画面が再表示される", commonHeader.screenTitle(),
                CompanyMasterPage.SCREEN_TITLE);
        rowsAre("会社マスタテーブルに ID 10「カシワ工業新社」、ID 20「ミナト物流」、ID 300「タチバナ商事」の3行が会社IDの昇順で表示される"
                + "（ファイル上の行順によらない。ベースラインの ID 1・2・100 は表示されず、ID 10 の会社名は「カシワ工業新社」に置き換わっている）", grid,
                REPLACED_IDS, REPLACED_NAMES);
        dataCountIs("", 3, "");
        noErrorDisplayed("");
    }

    @Test
    @DisplayName("No.6-3 【境界値】境界値の値（会社ID 1・2147483647、会社名50文字・サロゲートペアを含む50文字）と、"
            + "全角英数記号・全角スペースを含む会社名で洗い替えできる")
    void case6x3() {
        signInAsAdmin();
        openCompanyMaster(1);

        final String name50 = "さしすせそたちつてと".repeat(5);
        final String surrogate50 = "𠮷野家".repeat(16) + "𠮷𠮷";
        upload("2. 「CSVアップロード」ボタンを押下し、ファイル選択ダイアログで 洗い替え_境界値正常.csv を指定する", data("洗い替え_境界値正常.csv"),
                grid.root());
        verify.gridRowsByKey("会社マスタテーブルに4行が会社IDの昇順（ID 1・3・4・2147483647）で表示される", grid,
                CompanyMasterPage.COL_ID, List.of("1", "3", "4", "2147483647"));
        verify.gridCellText("ID 3 の会社名が「ＡＢＣ（株）１２３」である", grid, 1, CompanyMasterPage.COL_NAME,
                "ＡＢＣ（株）１２３");
        verify.gridCellText("ID 4 の会社名が「アオバ　物産」（全角スペースを含む）である", grid, 2, CompanyMasterPage.COL_NAME,
                "アオバ　物産");
        verify.textTruncated("ID 1 の会社名は列幅に収まる範囲で表示され、末尾に三点リーダー（…）が付く",
                grid.cell(0, CompanyMasterPage.COL_NAME));
        verify.textTruncated("ID 2147483647 の会社名は列幅に収まる範囲で表示され、末尾に三点リーダー（…）が付く",
                grid.cell(3, CompanyMasterPage.COL_NAME));
        dataCountIs("", 4, "");
        noErrorDisplayed("");
        verify.gridCellText("【機械検証】ID 1 の会社名セルの値が、ファイル上の50文字の会社名（「さしすせそたちつてと」を5回繰り返した50文字）と一致する"
                + "（省略表示のため全文は画面から読み取れない）", grid, 0, CompanyMasterPage.COL_NAME, name50);
        verify.gridCellText(
                "【機械検証】ID 2147483647 の会社名セルの値が、ファイル上の50文字の会社名（「𠮷野家」を16回繰り返した後に「𠮷𠮷」を続けた50文字）と一致する"
                        + "（省略表示のため全文は画面から読み取れない）",
                grid, 3, CompanyMasterPage.COL_NAME, surrogate50);
    }

    @Test
    @DisplayName("No.6-4 ダウンロードしたCSVファイルをそのままアップロードすると洗い替えでき、一覧の内容が変わらない（アップロードの様式はダウンロードと同じ）")
    void case6x4() {
        signInAsAdmin();
        openCompanyMaster(1);

        final Path downloaded = E2eTestFiles.downloadTarget(CSV_FILE_NAME);
        verify.csvDownloaded(
                "2. 「CSVダウンロード」ボタンを押下し、会社マスタ.csv を取得する（【機械検証】取得したファイルのバイト列が期待値ファイル "
                        + CSV_EXPECTED_ID_ASC + "（会社IDの昇順）と一致する）",
                CSV_FILE_NAME, data(CSV_EXPECTED_ID_ASC), () -> {
                    Download download = companyPage.pressCsvDownload();
                    download.saveAs(downloaded);
                    return download;
                });
        upload("3. 「CSVアップロード」ボタンを押下し、ファイル選択ダイアログで 手順2で取得した 会社マスタ.csv を指定する", downloaded,
                grid.root());
        noErrorDisplayed("3. ファイル形式チェック・入力値チェックのエラーは表示されない：");
        rowsAre("3. 会社マスタテーブルは " + BASELINE_DESC + " の4行である", grid, BASELINE_IDS, BASELINE_NAMES);
        dataCountIs("3. ", 4, "");
    }

    @Test
    @DisplayName("No.6-5 ファイル形式チェックの違反後に正しいファイルで洗い替えすると、エラー表示が全て消去されて一覧が更新される")
    void case6x5() {
        signInAsAdmin();
        openCompanyMaster(1);

        upload("2. 「CSVアップロード」ボタンを押下し、ファイル選択ダイアログで 形式_BOMなし.csv を指定する", data("形式_BOMなし.csv"),
                companyPage.errorArea());
        errorShown("2. ", FORMAT_ERROR_TITLE, "", READ_FAILURE);
        rowsAre("2. 会社マスタテーブルはベースラインの4件のまま", grid, BASELINE_IDS, BASELINE_NAMES);

        final String step3 = "3. 「CSVアップロード」ボタンを押下し、ファイル選択ダイアログで 洗い替え_正常.csv を指定する";
        verify.transitionCleared("手順2のエラー表示（エラータイトル・エラー内容）が表示されている", companyPage.errorArea(), step3,
                () -> upload(step3, data(UPLOAD_REPLACE), grid.root()), grid.root(),
                "3. 手順2のエラー表示（エラータイトル・エラー内容）が消える（消えた領域を図示。【機械検証】エラー表示（エラータイトル・エラー内容）の要素が0件である）");
        rowsAre("3. 会社マスタテーブルが ID 10「カシワ工業新社」・ID 20「ミナト物流」・ID 300「タチバナ商事」の3行に更新される", grid,
                REPLACED_IDS, REPLACED_NAMES);
    }

    @Test
    @DisplayName("No.6-6 入力値チェックの違反後に再度アップロードすると、既存のエラーメッセージが全て消去され、新しいチェック結果のみが表示される")
    void case6x6() {
        signInAsAdmin();
        openCompanyMaster(1);

        final String idRequired = "0002 - データ1行目 会社ID: この項目は入力が必要です。";
        final String nameRequired = "0002 - データ1行目 会社名: この項目は入力が必要です。";
        upload("2. 「CSVアップロード」ボタンを押下し、ファイル選択ダイアログで 入力_会社ID必須.csv を指定する", data("入力_会社ID必須.csv"),
                companyPage.errorArea());
        errorShown("2. ", INPUT_ERROR_TITLE, "", idRequired);
        baselineUnchanged("2. ");

        final String step3 = "3. 「CSVアップロード」ボタンを押下し、ファイル選択ダイアログで 入力_会社名必須.csv を指定する";
        verify.transitionCleared("手順2のエラー内容「" + idRequired + "」が表示されている",
                companyPage.errorMessage(idRequired), step3,
                () -> upload(step3, data("入力_会社名必須.csv"), companyPage.errorArea()),
                companyPage.errorArea(), "3. 手順2のエラー内容「" + idRequired + "」が消える（消えた領域を図示）");
        errorShown("3. ", INPUT_ERROR_TITLE, "（エラー内容は上記の1件のみ）", nameRequired);
        baselineUnchanged("3. ");
    }

    @Test
    @DisplayName("No.6-7 案件情報の取引先が参照している会社を含まないファイルでも洗い替えできる（参照不整合の検査を行わない）")
    void case6x7() {
        insertProjects(data("シード_案件取引先.csv"));
        signInAsAdmin();
        openCompanyMaster(1);

        upload("2. 「CSVアップロード」ボタンを押下し、ファイル選択ダイアログで 洗い替え_正常.csv（「ワタナベ商事」を含まない）を指定する",
                data(UPLOAD_REPLACE), grid.root());
        noErrorDisplayed("参照不整合を理由とするエラーは表示されない：");
        rowsAre("会社マスタテーブルが ID 10「カシワ工業新社」・ID 20「ミナト物流」・ID 300「タチバナ商事」の3行に更新される（「ワタナベ商事」は一覧に無い）",
                grid, REPLACED_IDS, REPLACED_NAMES);
    }

    // ---- 7. 入力チェック（ファイル指定・ファイル形式チェック） ----

    @Test
    @DisplayName("No.7-1 ファイルを指定せずにアップロードを実行すると、洗い替えされずエラーが表示される")
    void case7x1() {
        signInAsAdmin();
        openCompanyMaster(1);

        verify.op(
                "2. 開発者ツール相当の合成操作で、ファイルを指定しないまま CSVアップロードのフォームを送信する"
                        + "（画面のスクリプトはファイルが指定された時だけ送信するため、その事前チェックを迂回する）",
                () -> companyPage.submitUploadWithoutFile(), companyPage.errorArea());
        verify.text("会社マスタ管理画面が再表示される", commonHeader.screenTitle(), CompanyMasterPage.SCREEN_TITLE);
        errorShown("", FORMAT_ERROR_TITLE, "（エラー内容は上記の1件のみ）", "この項目は入力が必要です。");
        baselineUnchanged("");
        dataCountIs("", 4, "（のまま）");
    }

    @Test
    @DisplayName("No.7-2 【境界値】ファイル容量がちょうど5MB（5,242,880バイト）のファイルは容量チェックを通過する")
    void case7x2() {
        signInAsAdmin();
        openCompanyMaster(1);

        final Path file = generateSizedCsv("会社マスタ_5MBちょうど.csv");
        verify.machineEquals(
                "【機械検証】手順2（テスト実行時に 会社マスタ_5MBちょうど.csv を ファイル容量境界_生成仕様.csv の"
                        + "該当行に従って生成する）で生成したファイルのサイズが 5,242,880 バイトである",
                "5242880バイト", sizeOf(file) + "バイト");
        upload("3. 「CSVアップロード」ボタンを押下し、ファイル選択ダイアログで 手順2で生成した 会社マスタ_5MBちょうど.csv を指定する", file,
                companyPage.errorArea());
        errorShown("容量・様式・データ件数のファイル形式チェックを通過して入力値チェックが行われ、", INPUT_ERROR_TITLE,
                "（容量超過のエラー「" + SIZE_OVER + "」は表示されない）", "0002 - データ1行目 会社名: 50文字以内で入力してください。");
        baselineUnchanged("");
        dataCountIs("", 4, "（のまま）");
    }

    @Test
    @DisplayName("No.7-3 【境界値】ファイル容量が5MBを1バイト超える（5,242,881バイト）ファイルは容量超過のエラーになる")
    void case7x3() {
        signInAsAdmin();
        openCompanyMaster(1);

        final Path file = generateSizedCsv("会社マスタ_5MB超過.csv");
        verify.machineEquals(
                "【機械検証】手順2（テスト実行時に 会社マスタ_5MB超過.csv を ファイル容量境界_生成仕様.csv の"
                        + "該当行に従って生成する）で生成したファイルのサイズが 5,242,881 バイトである",
                "5242881バイト", sizeOf(file) + "バイト");
        upload("3. 「CSVアップロード」ボタンを押下し、ファイル選択ダイアログで 手順2で生成した 会社マスタ_5MB超過.csv を指定する", file,
                companyPage.errorArea());
        errorShown("", FORMAT_ERROR_TITLE, "（エラー内容は上記の1件のみ）", SIZE_OVER);
        baselineUnchanged("");
        dataCountIs("", 4, "（のまま）");
    }

    @Test
    @DisplayName("No.7-4 既定の様式でないファイル（文字コード UTF-8 だが BOM なし）は読み取り失敗のエラーになる")
    void case7x4() {
        expectUploadRejected("形式_BOMなし.csv", "（BOM が無い以外は 洗い替え_正常.csv と同じ内容）", FORMAT_ERROR_TITLE,
                "（エラー内容は上記の1件のみ）", READ_FAILURE);
    }

    @Test
    @DisplayName("No.7-5 既定の様式でないファイル（文字コード Shift_JIS）は読み取り失敗のエラーになる")
    void case7x5() {
        expectUploadRejected("形式_ShiftJIS.csv", "（文字コードが Shift_JIS である以外は 洗い替え_正常.csv と同じ内容）",
                FORMAT_ERROR_TITLE, "（エラー内容は上記の1件のみ）", READ_FAILURE);
    }

    @Test
    @DisplayName("No.7-6 既定の様式でないファイル（改行コード LF）は読み取り失敗のエラーになる")
    void case7x6() {
        expectUploadRejected("形式_改行LF.csv", "（改行コードが LF である以外は 洗い替え_正常.csv と同じ内容）",
                FORMAT_ERROR_TITLE, "（エラー内容は上記の1件のみ）", READ_FAILURE);
    }

    @Test
    @DisplayName("No.7-7 既定の様式でないファイル（区切り文字がタブ）は読み取り失敗のエラーになる")
    void case7x7() {
        expectUploadRejected("形式_区切りタブ.csv", "（区切り文字がタブである以外は 洗い替え_正常.csv と同じ内容）",
                FORMAT_ERROR_TITLE, "（エラー内容は上記の1件のみ）", READ_FAILURE);
    }

    @Test
    @DisplayName("No.7-8 既定の様式でないファイル（ヘッダー行の「ID」が全角の「会社ＩＤ,会社名」）は読み取り失敗のエラーになる")
    void case7x8() {
        expectUploadRejected("形式_ヘッダーID全角.csv", "", FORMAT_ERROR_TITLE, "（エラー内容は上記の1件のみ）",
                READ_FAILURE);
    }

    @Test
    @DisplayName("No.7-9 既定の様式でないファイル（ヘッダー行・列の順序が「会社名,会社ID」と逆）は読み取り失敗のエラーになる")
    void case7x9() {
        expectUploadRejected("形式_ヘッダー列順逆.csv", "", FORMAT_ERROR_TITLE, "（エラー内容は上記の1件のみ）",
                READ_FAILURE);
    }

    @Test
    @DisplayName("No.7-10 既定の様式でないファイル（ヘッダー行が無く1行目からボディ行）は読み取り失敗のエラーになる")
    void case7x10() {
        expectUploadRejected("形式_ヘッダーなし.csv", "（1行目が「300,タチバナ商事」）", FORMAT_ERROR_TITLE,
                "（エラー内容は上記の1件のみ）", READ_FAILURE);
    }

    @Test
    @DisplayName("No.7-11 既定の様式でないファイル（ボディ行の列数が3列と多い）は読み取り失敗のエラーになる")
    void case7x11() {
        expectUploadRejected("形式_列数過多.csv", "（2行目が「300,タチバナ商事,余分」）", FORMAT_ERROR_TITLE,
                "（エラー内容は上記の1件のみ）", READ_FAILURE);
    }

    @Test
    @DisplayName("No.7-12 既定の様式でないファイル（ボディ行の列数が1列と少ない）は読み取り失敗のエラーになる")
    void case7x12() {
        expectUploadRejected("形式_列数不足.csv", "（2行目が「300」）", FORMAT_ERROR_TITLE, "（エラー内容は上記の1件のみ）",
                READ_FAILURE);
    }

    @Test
    @DisplayName("No.7-13 ボディ行が0件（ヘッダー行のみ）のファイルはデータなしのエラーになる")
    void case7x13() {
        expectUploadRejected("形式_データ0件.csv", "（ヘッダー行「会社ID,会社名」のみ）", FORMAT_ERROR_TITLE,
                "（エラー内容は上記の1件のみ）", NO_DATA);
    }

    @Test
    @DisplayName("No.7-14 空のファイル（0バイト）はファイルが指定されていないものとして、必須入力のエラーになる")
    void case7x14() {
        expectUploadRejected("形式_空ファイル.csv", "（0バイト）", FORMAT_ERROR_TITLE,
                "（エラー内容は上記の1件のみ。ファイル形式チェックのエラー（読み取り失敗のエラー「" + READ_FAILURE + "」・データなしのエラー「"
                        + NO_DATA + "」）は表示されない）",
                "この項目は入力が必要です。");
    }

    @Test
    @DisplayName("No.7-15 ファイル形式チェックは違反した時点で中断される（容量超過かつ様式違反のファイルは容量超過のエラーのみ）")
    void case7x15() {
        signInAsAdmin();
        openCompanyMaster(1);

        final Path file = generateSizedCsv("会社マスタ_5MB超過_ヘッダー列順逆.csv");
        verify.machineEquals(
                "【機械検証】手順2（テスト実行時に 会社マスタ_5MB超過_ヘッダー列順逆.csv を"
                        + " ファイル容量境界_生成仕様.csv の該当行に従って生成する）で生成したファイルのサイズが 5,242,881 バイトである",
                "5242881バイト", sizeOf(file) + "バイト");
        upload("3. 「CSVアップロード」ボタンを押下し、ファイル選択ダイアログで 手順2で生成した 会社マスタ_5MB超過_ヘッダー列順逆.csv を指定する", file,
                companyPage.errorArea());
        errorShown("", FORMAT_ERROR_TITLE, "（エラー内容は上記の1件のみ。様式違反のエラー「" + READ_FAILURE + "」は表示されない）",
                SIZE_OVER);
        baselineUnchanged("");
        dataCountIs("", 4, "（のまま）");
    }

    @Test
    @DisplayName("No.7-16 様式違反のファイルは入力値チェックが行われない（BOM なしでボディ行に入力値の違反があるファイルは読み取り失敗のエラーのみ）")
    void case7x16() {
        expectUploadRejected("形式_BOMなし_入力値違反.csv", "（BOM なし。2行目の会社IDが空）", FORMAT_ERROR_TITLE,
                "（エラー内容は上記の1件のみ。エラータイトル「" + INPUT_ERROR_TITLE + "」と入力値チェックのメッセージは表示されない）",
                READ_FAILURE);
    }

    @Test
    @DisplayName("No.7-17 ファイル形式チェックは違反した時点で中断される（BOM なしでボディ行が0件のファイルは読み取り失敗のエラーのみ）")
    void case7x17() {
        expectUploadRejected("形式_BOMなし_データ0件.csv", "（BOM なし。ヘッダー行のみ）", FORMAT_ERROR_TITLE,
                "（エラー内容は上記の1件のみ。データなしのエラー「" + NO_DATA + "」は表示されない）", READ_FAILURE);
    }

    // ---- 8. 入力チェック（CSV入力値チェック） ----

    @Test
    @DisplayName("No.8-1 入力値チェック：会社IDの必須入力違反（空）")
    void case8x1() {
        expectUploadRejected("入力_会社ID必須.csv", "（データ1行目の会社IDが空。データ2行目は正しい値）", INPUT_ERROR_TITLE,
                "（データ1行目の会社名、データ2行目についてのメッセージは表示されない）", "0002 - データ1行目 会社ID: この項目は入力が必要です。");
    }

    @Test
    @DisplayName("No.8-2 入力値チェック：会社IDの整数違反（英字・小数・全角数字・プラス記号・先頭の半角スペース）")
    void case8x2() {
        expectUploadRejected("入力_会社ID整数.csv",
                "（データ1〜5行目の会社IDが「abc」「1.5」「１」（全角）「+1」「 5」（先頭に半角スペース）。データ6行目は正しい値）",
                INPUT_ERROR_TITLE, "（データ6行目についてのメッセージは表示されない）", "0002 - データ1行目 会社ID: 整数を入力してください。",
                "0003 - データ2行目 会社ID: 整数を入力してください。", "0004 - データ3行目 会社ID: 整数を入力してください。",
                "0005 - データ4行目 会社ID: 整数を入力してください。", "0006 - データ5行目 会社ID: 整数を入力してください。");
    }

    @Test
    @DisplayName("No.8-3 【境界値】入力値チェック：会社IDの最小数値(1)違反（0・-1。1は違反にならない）")
    void case8x3() {
        expectUploadRejected("入力_会社ID最小数値.csv", "（データ1〜3行目の会社IDが「0」「-1」「1」）", INPUT_ERROR_TITLE,
                "（データ3行目（会社ID「1」）についてのメッセージは表示されない）", "0002 - データ1行目 会社ID: 1以上の数値を入力してください。",
                "0003 - データ2行目 会社ID: 1以上の数値を入力してください。");
    }

    @Test
    @DisplayName("No.8-4 【境界値】入力値チェック：会社IDの最大数値(2147483647)違反"
            + "（2147483648・20桁の数値。2147483647は違反にならない）")
    void case8x4() {
        expectUploadRejected("入力_会社ID最大数値.csv",
                "（データ1〜3行目の会社IDが「2147483648」「99999999999999999999」「2147483647」）", INPUT_ERROR_TITLE,
                "（データ3行目（会社ID「2147483647」）についてのメッセージは表示されない）",
                "0002 - データ1行目 会社ID: 2147483647以下の数値を入力してください。",
                "0003 - データ2行目 会社ID: 2147483647以下の数値を入力してください。");
    }

    @Test
    @DisplayName("No.8-5 入力値チェック：会社IDの一意違反は、2回目以降に同じ値を設定した行に表示される")
    void case8x5() {
        expectUploadRejected("入力_会社ID一意.csv", "（データ1〜4行目の会社IDが「5」「6」「5」「5」）", INPUT_ERROR_TITLE,
                "（最初に「5」を設定したデータ1行目と、データ2行目（「6」）についてのメッセージは表示されない）",
                "0004 - データ3行目 会社ID: 重複して設定しないでください(5)", "0005 - データ4行目 会社ID: 重複して設定しないでください(5)");
    }

    @Test
    @DisplayName("No.8-6 入力値チェック：会社名の必須入力違反（空）")
    void case8x6() {
        expectUploadRejected("入力_会社名必須.csv", "（データ1行目の会社名が空。データ2行目は正しい値）", INPUT_ERROR_TITLE,
                "（データ1行目の会社ID、データ2行目についてのメッセージは表示されない）", "0002 - データ1行目 会社名: この項目は入力が必要です。");
    }

    @Test
    @DisplayName("No.8-7 【境界値】入力値チェック：会社名の最大文字数(50)違反（51文字・サロゲートペアを含む51文字。50文字は違反にならない）")
    void case8x7() {
        expectUploadRejected("入力_会社名最大文字数.csv",
                "（データ1行目の会社名が51文字、データ2行目が「𠮷野家」を17回繰り返した51文字、データ3行目が50文字）", INPUT_ERROR_TITLE,
                "（データ3行目（50文字）についてのメッセージは表示されない）", "0002 - データ1行目 会社名: 50文字以内で入力してください。",
                "0003 - データ2行目 会社名: 50文字以内で入力してください。");
    }

    @Test
    @DisplayName("No.8-8 入力値チェック：会社名の使用文字（全角文字）違反（半角英字・半角スペース・半角カタカナ・半角記号・制御文字）")
    void case8x8() {
        expectUploadRejected("入力_会社名使用文字.csv",
                "（データ1〜5行目の会社名が「ABC商事」「アオバ 物産」（半角スペース）「ｱｵﾊﾞ物産」「アオバ(株)」「アオバ<タブ文字>物産」。データ6行目は正しい値）",
                INPUT_ERROR_TITLE, "（データ6行目についてのメッセージは表示されない）",
                "0002 - データ1行目 会社名: 使用不可能な文字が含まれています。", "0003 - データ2行目 会社名: 使用不可能な文字が含まれています。",
                "0004 - データ3行目 会社名: 使用不可能な文字が含まれています。", "0005 - データ4行目 会社名: 使用不可能な文字が含まれています。",
                "0006 - データ5行目 会社名: 使用不可能な文字が含まれています。");
    }

    @Test
    @DisplayName("No.8-9 同じ行の複数の項目で違反がある場合、列の順序（会社ID、会社名）で全てのメッセージが表示される")
    void case8x9() {
        expectUploadRejected("入力_同一行複数項目.csv", "（データ1行目の会社IDが「abc」、会社名が「ABC商事」）", INPUT_ERROR_TITLE,
                "（データ2行目についてのメッセージは表示されない）", "0002 - データ1行目 会社ID: 整数を入力してください。",
                "0002 - データ1行目 会社名: 使用不可能な文字が含まれています。");
    }

    @Test
    @DisplayName("No.8-10 同じ項目で複数の入力値チェックに違反した場合、一覧でより小さな番号のメッセージ1つだけが表示される")
    void case8x10() {
        expectUploadRejected("入力_同一項目複数違反.csv",
                "（データ1行目の会社名が半角「A」を含む51文字＝最大文字数と使用文字に違反。データ2・3行目の会社IDがともに「0」＝データ3行目は最小数値と一意に違反）",
                INPUT_ERROR_TITLE, "（「使用不可能な文字が含まれています。」と「重複して設定しないでください(0)」は表示されない）",
                "0002 - データ1行目 会社名: 50文字以内で入力してください。", "0003 - データ2行目 会社ID: 1以上の数値を入力してください。",
                "0004 - データ3行目 会社ID: 1以上の数値を入力してください。");
    }

    @Test
    @DisplayName("No.8-11 【境界値】エラーメッセージのCSV行番号は4桁で0埋めし、5桁以上はそのまま表示する（9999行目・10000行目）")
    void case8x11() {
        expectUploadRejected("入力_行番号5桁.csv",
                "（ヘッダー行＋ボディ行9999行。データ9998行目（CSV 9999行目）とデータ9999行目（CSV 10000行目）の会社IDが「abc」、他は正しい値）",
                INPUT_ERROR_TITLE, "（データ1〜9997行目についてのメッセージは表示されない）",
                "9999 - データ9998行目 会社ID: 整数を入力してください。", "10000 - データ9999行目 会社ID: 整数を入力してください。");
    }

    // ---- 9. サーバー/APIサービス処理（異常系：セッション切れ・データベース例外） ----

    @Test
    @DisplayName("No.9-1 ログインの有効期限が切れた状態でCSVダウンロードすると、ログイン画面に遷移しダウンロードされない（ログイン後は案件情報一覧画面へ遷移する）")
    void case9x1() {
        signInAsAdmin();
        openCompanyMaster(1);

        clearSessionCookies(2);
        verify.noDownload("【機械検証】手順3でファイルはダウンロードされない（ダウンロードの発生が0件）", () -> verify
                .opPress("3. 「CSVダウンロード」ボタンを押下する", companyPage.csvDownloadButton(), () -> {
                    companyPage.pressCsvDownloadWithoutWaiting();
                    page.waitForURL(loginPage.url());
                }, loginPage.form()));
        verify.visible("3. ログイン画面が表示される", loginPage.form());
        signInOnLoginScreen(4);
        verify.urlIs("4. 案件情報一覧画面（/projects）へ遷移する（会社マスタ管理画面には戻らない）", projectListPage.url());
    }

    @Test
    @DisplayName("No.9-2 ログインの有効期限が切れた状態でCSVアップロードすると、ログイン画面に遷移し洗い替えされない（ログイン後は案件情報一覧画面へ遷移する）")
    void case9x2() {
        signInAsAdmin();
        openCompanyMaster(1);

        clearSessionCookies(2);
        upload("3. 「CSVアップロード」ボタンを押下し、ファイル選択ダイアログで 洗い替え_正常.csv を指定する", data(UPLOAD_REPLACE),
                loginPage.form());
        verify.visible("3. ログイン画面が表示される", loginPage.form());
        signInOnLoginScreen(4);
        verify.urlIs("4. 案件情報一覧画面（/projects）へ遷移する（会社マスタ管理画面には戻らない）", projectListPage.url());
        pressCompanyNav(5);
        rowsAre("5. 会社マスタテーブルはベースラインの4件（" + BASELINE_DESC
                + "）のままで、洗い替え_正常.csv の内容（ID 20「ミナト物流」・ID 300「タチバナ商事」）は表示されない" + "（洗い替えは実施されていない）",
                grid, BASELINE_IDS, BASELINE_NAMES);
    }

    @Test
    @DisplayName("No.9-3 洗い替え中にデータベース例外が発生すると洗い替えが中止されて実施前の状態に戻り、エラー画面にサーバー内部エラーが表示される")
    void case9x3() {
        signInAsAdmin();
        openCompanyMaster(1);

        verify.opArmFault(
                "2. （障害シーム）「データベース例外（会社マスタ登録）」を有効化する（洗い替えの全件削除の後、会社マスタレコードの登録時にデータベース例外を発生させる）",
                CompanyMasterFaultSeamConfig.DB_EXCEPTION_ON_INSERT);
        upload("3. 「CSVアップロード」ボタンを押下し、ファイル選択ダイアログで 洗い替え_正常.csv を指定する", data(UPLOAD_REPLACE),
                errorPage.card());
        verify.faultFired("【機械検証】手順3で障害シーム「データベース例外（会社マスタ登録）」が発火した",
                CompanyMasterFaultSeamConfig.DB_EXCEPTION_ON_INSERT);
        verify.text("3. エラー画面に遷移し、エラータイトル「An Error Occurred」が表示される", errorPage.errorTitle(),
                "An Error Occurred");
        verify.text("3. エラー内容「エラーが発生しました。」（サーバー内部エラー）が表示される", errorPage.errorMessage(),
                "エラーが発生しました。");

        openCompanyMaster(4);
        rowsAre("4. 会社マスタテーブルはベースラインの4件（" + BASELINE_DESC
                + "）のまま（全件削除も取り消されている）で、ID 20「ミナト物流」・ID 300「タチバナ商事」は表示されない", grid, BASELINE_IDS,
                BASELINE_NAMES);
    }

    // ---- 10. 認証・認可 ----

    @Test
    @DisplayName("No.10-1 一般ユーザが会社マスタ管理画面（/master/companies）にアクセスすると権限不足エラーになる")
    void case10x1() {
        signInAsGeneralUser();
        openCompanyMaster(1);

        verify.text("エラー画面に遷移し、エラータイトル「403 Forbidden」が表示される", errorPage.errorTitle(),
                "403 Forbidden");
        verify.text("エラー内容「ページにアクセスできません。」（権限不足エラー）が表示される", errorPage.errorMessage(),
                "ページにアクセスできません。");
        verify.absent("会社マスタ管理画面（会社マスタの一覧）は表示されない", errorPage.card(), grid.root());
    }

    @Test
    @DisplayName("No.10-2 一般ユーザがCSVダウンロードのURL（/master/companies/csv）に直接アクセスしても、会社マスタはダウンロードされない")
    void case10x2() {
        signInAsGeneralUser();

        final E2eVerify.CapturedResponse[] response = new E2eVerify.CapturedResponse[1];
        verify.noDownload("【機械検証】CSVファイルはダウンロードされない（ダウンロードの発生が0件）",
                () -> response[0] = verify.captureResponse("1. /master/companies/csv への応答", "GET",
                        CompanyMasterPage.CSV_PATH,
                        () -> verify.opOpen("1. ブラウザで /master/companies/csv を開く",
                                () -> companyPage.openCsvUrl())));
        verify.responseStatusIs("【機械検証】/master/companies/csv への応答の HTTP ステータスが 403（権限なし）である",
                response[0], 403);
    }

    @Test
    @DisplayName("No.10-3 一般ユーザがCSVアップロード（洗い替え）の要求を送信しても、権限不足エラーとなり洗い替えされない")
    void case10x3() {
        signInAsGeneralUser();
        openProjectList(1);

        verify.op(
                "2. 開発者ツール相当の合成操作で、画面が保持するCSRFトークンと 洗い替え_正常.csv を付けて、"
                        + "/master/companies/csv へアップロードの要求（POST）を送信する"
                        + "（一般ユーザには会社マスタ管理画面とアップロードの操作部品が提供されないため、経路外の要求として送る）",
                () -> companyPage.submitUploadOutOfRoute(data(UPLOAD_REPLACE)), errorPage.card());
        verify.text("2. エラー画面に遷移し、エラータイトル「403 Forbidden」が表示される", errorPage.errorTitle(),
                "403 Forbidden");
        verify.text("2. エラー内容「ページにアクセスできません。」（権限不足エラー）が表示される", errorPage.errorMessage(),
                "ページにアクセスできません。");

        verify.opPress("3. エラー画面の「TOINDEX」（インデックスボタン）を押下する", errorPage.toIndexButton(),
                () -> errorPage.pressToIndex());
        verify.opPress("4. 共通ヘッダーの「ログアウト」ボタンを押下する", commonHeader.logoutButton(),
                () -> commonHeader.pressLogout());
        signInOnLoginScreen(5);
        openCompanyMaster(6);
        rowsAre("6. 会社マスタテーブルはベースラインの4件（" + BASELINE_DESC
                + "）のままで、ID 20「ミナト物流」・ID 300「タチバナ商事」は表示されない（洗い替えされていない）", grid, BASELINE_IDS,
                BASELINE_NAMES);
    }

    @Test
    @DisplayName("No.10-4 一般ユーザでは共通ヘッダーに会社マスタボタンが表示されない")
    void case10x4() {
        signInAsGeneralUser();
        openProjectList(1);

        verify.absent(
                "共通ヘッダーのナビゲーション（「一覧・検索」「新規登録」ボタンの並び）が表示され、その中に「会社マスタ」ボタンが無い"
                        + "（【機械検証】共通ヘッダー内の「会社マスタ」ボタンの要素が0件である）",
                commonHeader.navigation(), commonHeader.nav(CommonHeaderPage.NAV_COMPANY));
    }
}
