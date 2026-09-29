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
import testgen.e2e.pages.ErrorPage;
import testgen.e2e.pages.LoginPage;
import testgen.e2e.pages.ProjectListPage;
import testgen.e2e.pages.UserMasterPage;
import testgen.e2e.seams.UserMasterFaultSeamConfig;
import testgen.e2e.support.E2eFeature;
import testgen.e2e.support.evidence.EvidenceV2;

/**
 * ユーザマスタ管理画面（機能 {@code 22_ユーザマスタ管理}）の E2E テスト。
 *
 * <p>
 * テストケース仕様書 {@code 22_ユーザマスタ管理/22_ユーザマスタ管理_テスト仕様書.xlsx} の全ケースを実装する。 CSV
 * による行ごとの編集（追加・更新・削除・何もしない）・ダウンロードと、業務エラー・サーバー内部の 異常分岐（障害シーム）を扱う。
 */
@EvidenceV2
@Import(UserMasterFaultSeamConfig.class)
@E2eFeature("22_ユーザマスタ管理")
class UserMasterE2eTest extends ProjectE2eTest {

    /** システム管理者 SM0002（管理 花子／管理部）。 */
    private static final String ADMIN_USER = "SM0002";

    /** 一般ユーザ SM0001（営業 太郎／営業部）。 */
    private static final String GENERAL_USER = "SM0001";

    /** ベースラインのユーザに共通の開発用ダミーパスワード。 */
    private static final String PASSWORD = "Passw0rd!";

    /** ユーザマスタ80件のシード（ページネーション確認用）。 */
    private static final String SEED_PAGINATION = "22_ユーザマスタ管理/data/ページネーション_ユーザマスタ.csv";

    /** CSV 期待値正本: ユーザマスタのダウンロード（ユーザID昇順）。 */
    private static final String CSV_EXPECTED_ASC = "22_ユーザマスタ管理/data/CSV期待_ユーザマスタDL.csv";

    /** CSV 期待値正本: ユーザマスタのダウンロード（ユーザID降順）。 */
    private static final String CSV_EXPECTED_DESC = "22_ユーザマスタ管理/data/CSV期待_ユーザマスタDL降順.csv";

    /** ダウンロードされる CSV のファイル名。 */
    private static final String CSV_FILE_NAME = "ユーザマスタ.csv";

    /** ファイル形式チェック違反のエラータイトル。 */
    private static final String FORMAT_ERROR_TITLE = "CSVアップロードに失敗しました";

    /** 入力値チェック違反のエラータイトル。 */
    private static final String INPUT_ERROR_TITLE = "CSVの内容にエラーがあります。詳細は以下の通りです。";

    /** 業務エラーのエラータイトル（1行目で異常終了）。 */
    private static final String BUSINESS_ERROR_TITLE = "1行目で異常終了しました。";

    /** ロールの表示（一般ユーザ）。 */
    private static final String ROLE_USER = "0: 一般ユーザ";

    /** ロールの表示（システム管理者）。 */
    private static final String ROLE_ADMIN = "9: システム管理者";

    /** 生成する CSV のヘッダー行（ユーザマスタ）。 */
    private static final String CSV_HEADER = "編集属性,ユーザID,ユーザ名,メールアドレス,部署ID,ロール,パスワード";

    /** ベースラインのユーザID（昇順）。 */
    private static final List<String> BASELINE_KEYS = List.of("SM0001", "SM0002", "SM0003");

    private LoginPage loginPage;
    private CommonHeaderPage commonHeader;
    private UserMasterPage userPage;
    private ErrorPage errorPage;


    @BeforeEach
    void setUpPages() {
        loginPage = new LoginPage(page, baseUrl());
        commonHeader = new CommonHeaderPage(page, baseUrl());
        userPage = new UserMasterPage(page, baseUrl());
        errorPage = new ErrorPage(page, baseUrl());
        loginPage.signIn(ADMIN_USER, PASSWORD, ProjectListPage.PATH);
    }

    // ---- テストデータ・共通手順 ----

    /** ユーザIDの連番（SM0001 形式）を作る。 */
    private static List<String> userKeys(int from, int to) {
        List<String> keys = new ArrayList<>();
        for (int no = from; no <= to; no++) {
            keys.add(String.format("SM%04d", no));
        }
        return keys;
    }

    /** テストデータ正本のパス（22_ユーザマスタ管理/data 配下）。 */
    private Path data(String fileName) {
        return unitData("22_ユーザマスタ管理/data/" + fileName);
    }

    /** 手順「/master/users を開く」。 */
    private void openUserMaster(int stepNo) {
        verify.opOpen(stepNo + ". /master/users を開く", () -> userPage.open());
    }

    /** CSV をアップロードする（ファイル選択で即座にフォーム送信される）。 */
    private void uploadCsv(String stepDesc, Path file, Locator result) {
        verify.opChooseFileSubmits(stepDesc, userPage.csvUploadButton(), file, result);
    }

    /** ベースラインの3件が表示されたままであることを確認する。 */
    private void baselineTableUnchanged() {
        verify.gridRowsByKey("テーブルには3件（SM0001・SM0002・SM0003）が表示されたままである", userPage.grid(),
                UserMasterPage.COL_USER_ID, BASELINE_KEYS);
        verify.gridCellText("先頭行は SM0001「営業 太郎」である", userPage.grid(), 0,
                UserMasterPage.COL_USER_NAME, "営業 太郎");
        verify.gridCellText("末尾行は SM0003「開発 次郎」である", userPage.grid(), 2,
                UserMasterPage.COL_USER_NAME, "開発 次郎");
    }

    /** 取得データ件数がテーブル上部に指定件数で表示されることを確認する。 */
    private void dataCountIs(int count) {
        verify.text("取得データ件数に「取得データ件数: " + count + "件」が表示される",
                userPage.dataCountLabel(UserMasterPage.PAGER_TOP), "取得データ件数: " + count + "件");
    }

    /** CSV アップロードの入力値チェック違反を確認する共通手順。 */
    private void expectInputError(String fileName, String expectedMessage) {
        openUserMaster(1);
        verify.gridRowsByKey("1. テーブルに3件（SM0001・SM0002・SM0003）が表示されている", userPage.grid(),
                UserMasterPage.COL_USER_ID, BASELINE_KEYS);

        uploadCsv("2. 「CSVアップロード」を押下し、" + fileName + " を指定する", data(fileName),
                userPage.errorArea());

        verify.text("エラータイトル「CSVの内容にエラーがあります。詳細は以下の通りです。」が表示される", userPage.errorTitle(),
                INPUT_ERROR_TITLE);
        verify.text("エラー内容「" + expectedMessage + "」が表示される", userPage.errorMessages(),
                expectedMessage);
        baselineTableUnchanged();
    }

    /** CSV アップロードの業務エラーを確認する共通手順。 */
    private void expectBusinessError(String fileName, String expectedMessage) {
        openUserMaster(1);
        verify.gridRowsByKey("1. テーブルに3件（SM0001・SM0002・SM0003）が表示されている", userPage.grid(),
                UserMasterPage.COL_USER_ID, BASELINE_KEYS);

        uploadCsv("2. 「CSVアップロード」を押下し、" + fileName + " を指定する", data(fileName),
                userPage.errorArea());

        verify.text("エラータイトル「1行目で異常終了しました。」が表示される", userPage.errorTitle(), BUSINESS_ERROR_TITLE);
        verify.text("エラー内容「" + expectedMessage + "」が表示される", userPage.errorMessages(),
                expectedMessage);
        baselineTableUnchanged();
    }

    // ---- 1. 画面仕様・初期表示 ----

    @Test
    @DisplayName("No.1-1 ユーザマスタ管理画面の初期表示（画面項目・一覧・ロールの表示・取得データ件数・エラー表示の非表示）")
    void case1x1() {
        openUserMaster(1);

        verify.visible("画面タイトル「ユーザマスタ管理画面」が表示される",
                commonHeader.screenTitleOf(UserMasterPage.SCREEN_TITLE));
        verify.visible("「CSVダウンロード」ボタンが表示される", userPage.csvDownloadButton());
        verify.visible("「CSVアップロード」ボタンが表示される", userPage.csvUploadButton());
        verify.gridColumnHeaders("列ヘッダーは「ユーザID」「ユーザ名」「メールアドレス」「部署ID」「ロール」である", userPage.grid(),
                UserMasterPage.DEFAULT_HEADERS);
        verify.gridRowsByKey("ユーザマスタテーブルに3件が一覧表示される", userPage.grid(), UserMasterPage.COL_USER_ID,
                BASELINE_KEYS);
        verify.gridCellText("SM0001 の行のロールが「0: 一般ユーザ」と表示される", userPage.grid(), 0,
                UserMasterPage.COL_ROLE, ROLE_USER);
        verify.gridCellText("SM0002 の行のロールが「9: システム管理者」と表示される", userPage.grid(), 1,
                UserMasterPage.COL_ROLE, ROLE_ADMIN);
        verify.gridCellText("SM0003 の行のロールが「0: 一般ユーザ」と表示される", userPage.grid(), 2,
                UserMasterPage.COL_ROLE, ROLE_USER);
        verify.text("テーブル上部に「取得データ件数: 3件」が表示される", userPage.dataCountLabel(UserMasterPage.PAGER_TOP),
                "取得データ件数: 3件");
        verify.text("テーブル下部に「取得データ件数: 3件」が表示される",
                userPage.dataCountLabel(UserMasterPage.PAGER_BOTTOM), "取得データ件数: 3件");
        verify.value("表示件数セレクトボックスは「30件」が選択されている",
                userPage.pageSizeSelect(UserMasterPage.PAGER_TOP), "30");
        verify.visible("テーブル上部に表示ページセレクトボックスが表示される",
                userPage.pageJumpSelect(UserMasterPage.PAGER_TOP));
        verify.visible("テーブル上部にページネーションが表示される", userPage.pagerNums(UserMasterPage.PAGER_TOP));
        verify.visible("テーブル下部に表示ページセレクトボックスが表示される",
                userPage.pageJumpSelect(UserMasterPage.PAGER_BOTTOM));
        verify.visible("テーブル下部にページネーションが表示される", userPage.pagerNums(UserMasterPage.PAGER_BOTTOM));
        verify.absent("エラー表示（エラータイトル・エラー内容）は表示されない（【機械検証】要素が0件である）", userPage.csvUploadButton(),
                userPage.errorArea());
    }

    @Test
    @DisplayName("No.1-2 既定レイアウト（列順序・列幅・初期ソート順がユーザIDの昇順）")
    void case1x2() {
        openUserMaster(1);

        verify.gridColumnHeaders("列は左から「ユーザID」「ユーザ名」「メールアドレス」「部署ID」「ロール」の順に並ぶ", userPage.grid(),
                UserMasterPage.DEFAULT_HEADERS);
        final int bodyWidth = userPage.gridBodyWidthPx();
        final int fifth = bodyWidth / 5;
        verify.gridColumnWidthIs("「ユーザID」列の幅はテーブルの横幅を5等分した幅である", userPage.grid(),
                UserMasterPage.COL_USER_ID, fifth);
        verify.gridColumnWidthIs("「ロール」列の幅はテーブルの横幅を5等分した幅である（横幅が5で割り切れない場合の丸め端数は最終列が吸収する）",
                userPage.grid(), UserMasterPage.COL_ROLE, bodyWidth - fifth * 4);
        verify.gridRowsByKey("テーブルの行はユーザIDの昇順に並ぶ", userPage.grid(), UserMasterPage.COL_USER_ID,
                BASELINE_KEYS);
        verify.gridCellText("先頭行 SM0001 のユーザ名が「営業 太郎」である", userPage.grid(), 0,
                UserMasterPage.COL_USER_NAME, "営業 太郎");
        verify.gridCellText("先頭行 SM0001 のメールアドレスが sm0001@example.com である", userPage.grid(), 0,
                UserMasterPage.COL_EMAIL, "sm0001@example.com");
        verify.gridCellText("先頭行 SM0001 の部署IDが 1 である", userPage.grid(), 0,
                UserMasterPage.COL_DEPARTMENT_ID, "1");
        verify.gridCellText("先頭行 SM0001 のロールが「0: 一般ユーザ」である", userPage.grid(), 0,
                UserMasterPage.COL_ROLE, ROLE_USER);
        verify.gridCellText("末尾行 SM0003 のユーザ名が「開発 次郎」である", userPage.grid(), 2,
                UserMasterPage.COL_USER_NAME, "開発 次郎");
        verify.gridCellText("末尾行 SM0003 のメールアドレスが sm0003@example.com である", userPage.grid(), 2,
                UserMasterPage.COL_EMAIL, "sm0003@example.com");
        verify.gridCellText("末尾行 SM0003 の部署IDが 2 である", userPage.grid(), 2,
                UserMasterPage.COL_DEPARTMENT_ID, "2");
        verify.gridCellText("末尾行 SM0003 のロールが「0: 一般ユーザ」である", userPage.grid(), 2,
                UserMasterPage.COL_ROLE, ROLE_USER);
    }

    @Test
    @DisplayName("No.1-3 表示内容が列幅を超える場合、先頭から列幅に収まる範囲の文字列に三点リーダー(…)を加えて表示する")
    void case1x3() {
        openUserMaster(1);

        verify.op("2. 「メールアドレス」列の幅を最小（80px）まで狭める",
                () -> userPage.grid().shrinkColumnToMinimum(UserMasterPage.COL_EMAIL),
                userPage.grid().headerCell(UserMasterPage.COL_EMAIL));

        verify.textTruncated("2. SM0001 の行の「メールアドレス」が列幅に収まる範囲で切り詰められ、末尾に三点リーダー(…)が付く",
                userPage.grid().cell(0, UserMasterPage.COL_EMAIL));
        verify.gridCellText("「部署ID」列の値（先頭行）は切り詰められずそのまま表示される", userPage.grid(), 0,
                UserMasterPage.COL_DEPARTMENT_ID, "1");
        verify.gridCellText("「部署ID」列の値（末尾行）は切り詰められずそのまま表示される", userPage.grid(), 2,
                UserMasterPage.COL_DEPARTMENT_ID, "2");
    }

    @Test
    @DisplayName("No.1-4 テーブルの行が画面に収まらない場合、メインビューポート内で縦スクロールする")
    void case1x4() {
        replaceUsers(unitData(SEED_PAGINATION));
        openUserMaster(1);

        verify.gridCellText("1. 1ページ目の先頭行は SM0001 である", userPage.grid(), 0,
                UserMasterPage.COL_USER_ID, "SM0001");
        verify.gridRowsByKey(
                "2. 縦にスクロールし、1ページ目の末尾行 SM0030 まで到達できる" + "（【機械検証】末尾行 SM0030 の次に行が存在しない）",
                userPage.grid(), UserMasterPage.COL_USER_ID, userKeys(1, 30));
    }

    // ---- 2. 画面イベント処理（テーブル操作） ----

    @Test
    @DisplayName("No.2-1 列ヘッダーのドラッグ＆ドロップで列の順序を変更できる（BにドロップするとBの左側にAが挿入される）")
    void case2x1() {
        openUserMaster(1);

        verify.gridColumnHeaders("1. 列が左から「ユーザID」「ユーザ名」…の順である", userPage.grid(),
                UserMasterPage.DEFAULT_HEADERS);
        verify.op("2. 「ユーザ名」の列ヘッダーをドラッグし、「ユーザID」の列ヘッダーの上でドロップする",
                () -> userPage.grid().moveColumn(UserMasterPage.COL_USER_NAME,
                        UserMasterPage.COL_USER_ID),
                userPage.grid().headerCell(UserMasterPage.COL_USER_NAME));

        verify.gridColumnHeaders("2. 列が左から「ユーザ名」「ユーザID」「メールアドレス」「部署ID」「ロール」の順に変わる", userPage.grid(),
                List.of("ユーザ名", "ユーザID", "メールアドレス", "部署ID", "ロール"));
        verify.gridCellText("先頭行の「ユーザ名」が「営業 太郎」で表示される（値が列の入れ替えに追従する）", userPage.grid(), 0,
                UserMasterPage.COL_USER_NAME, "営業 太郎");
        verify.gridCellText("先頭行の「ユーザID」が「SM0001」で表示される（値が列の入れ替えに追従する）", userPage.grid(), 0,
                UserMasterPage.COL_USER_ID, "SM0001");
    }

    @Test
    @DisplayName("No.2-2 列ヘッダーの境界をドラッグして列幅を変更でき、最小値は80pxである")
    void case2x2() {
        openUserMaster(1);

        // 広げた列の右端が画面外になると手順3で境界線を掴めないため、テーブルの横幅から
        // 40px 内側までを目標幅にする（他の列は最小幅 80px になり、合計は横幅を超える）。
        final int widened = userPage.gridBodyWidthPx() - 40;
        verify.op("2. 「ユーザID」列と「ユーザ名」列の間の境界線をドラッグして右へ大きく広げる",
                () -> userPage.grid().resizeColumnTo(UserMasterPage.COL_USER_ID, widened),
                userPage.grid().root());
        verify.gridColumnHeaders("2. テーブルの列が画面に収まらなくなり、テーブル内で横スクロールが発生する", userPage.grid(),
                UserMasterPage.DEFAULT_HEADERS);

        verify.op("3. 同じ境界線をドラッグして左へ、80pxより狭くなるまで動かす",
                () -> userPage.grid().shrinkColumnToMinimum(UserMasterPage.COL_USER_ID),
                userPage.grid().headerCell(UserMasterPage.COL_USER_ID));
        verify.gridColumnWidthIs("【機械検証】手順3の後の「ユーザID」列の幅が 80px である（それより狭くならない）", userPage.grid(),
                UserMasterPage.COL_USER_ID, 80);
    }

    @Test
    @DisplayName("No.2-3 列ヘッダーのダブルクリックでソート状態が切り替わる（初期はユーザID列に昇順が適用済みのため降順→ソートなし→昇順の順に遷移する。文字列のソート）")
    void case2x3() {
        openUserMaster(1);

        verify.visible("1. 「ユーザID」列ヘッダーに昇順のソート意匠が表示されている",
                userPage.sortAscendingIcon(UserMasterPage.COL_USER_ID));

        verify.op("2. 「ユーザID」の列ヘッダーをダブルクリックする",
                () -> userPage.doubleClickHeader(UserMasterPage.COL_USER_ID),
                userPage.grid().headerCell(UserMasterPage.COL_USER_ID));
        verify.gridRowsByKey("2. ユーザIDの降順に並び替わり、先頭行が SM0003、末尾行が SM0001 になる", userPage.grid(),
                UserMasterPage.COL_USER_ID, List.of("SM0003", "SM0002", "SM0001"));
        verify.visible("2. 「ユーザID」列に降順の意匠が付く",
                userPage.sortDescendingIcon(UserMasterPage.COL_USER_ID));

        verify.transitionHide("「ユーザID」列に降順のソート意匠が表示されている",
                userPage.sortDescendingIcon(UserMasterPage.COL_USER_ID),
                "3. もう一度「ユーザID」の列ヘッダーをダブルクリックする",
                () -> userPage.doubleClickHeader(UserMasterPage.COL_USER_ID),
                userPage.grid().headerCell(UserMasterPage.COL_USER_ID),
                "3. ソートなしとなり「ユーザID」列の意匠が消える");
        verify.gridRowsByKey("3. 既定の並び順であるユーザID列の昇順（先頭行 SM0001、末尾行 SM0003）に戻る", userPage.grid(),
                UserMasterPage.COL_USER_ID, BASELINE_KEYS);

        verify.op("4. もう一度「ユーザID」の列ヘッダーをダブルクリックする",
                () -> userPage.doubleClickHeader(UserMasterPage.COL_USER_ID),
                userPage.grid().headerCell(UserMasterPage.COL_USER_ID));
        verify.visible("4. 「ユーザID」列に昇順の意匠が付く",
                userPage.sortAscendingIcon(UserMasterPage.COL_USER_ID));
        verify.gridRowsByKey("4. ユーザIDの昇順（先頭行 SM0001、末尾行 SM0003）に並ぶ", userPage.grid(),
                UserMasterPage.COL_USER_ID, BASELINE_KEYS);
    }

    @Test
    @DisplayName("No.2-4 部署ID列は数字の大小でソートされ、別の列のソートを適用すると既存のソートが解除される")
    void case2x4() {
        openUserMaster(1);

        verify.op("2. 「ユーザID」の列ヘッダーをダブルクリックしてユーザIDの降順にする",
                () -> userPage.doubleClickHeader(UserMasterPage.COL_USER_ID),
                userPage.grid().headerCell(UserMasterPage.COL_USER_ID));
        verify.gridRowsByKey("2. ユーザIDの降順に並び替わる", userPage.grid(), UserMasterPage.COL_USER_ID,
                List.of("SM0003", "SM0002", "SM0001"));
        verify.visible("2. 「ユーザID」列にソート中の意匠が付く",
                userPage.sortDescendingIcon(UserMasterPage.COL_USER_ID));

        verify.transitionHide("「ユーザID」列にソート中の意匠が表示されている",
                userPage.sortDescendingIcon(UserMasterPage.COL_USER_ID),
                "3. 「部署ID」の列ヘッダーをダブルクリックする",
                () -> userPage.doubleClickHeader(UserMasterPage.COL_DEPARTMENT_ID),
                userPage.grid().headerCell(UserMasterPage.COL_DEPARTMENT_ID),
                "3. 「ユーザID」列のソート中の意匠が消える");
        verify.visible("3. 「部署ID」列にソート中の意匠が付く",
                userPage.sortAscendingIcon(UserMasterPage.COL_DEPARTMENT_ID));
        verify.gridRowsByKey("3. 部署IDの数値の昇順（SM0001・SM0003・SM0002）に並び、" + "ユーザIDによるソートは併用されない",
                userPage.grid(), UserMasterPage.COL_USER_ID, List.of("SM0001", "SM0003", "SM0002"));
    }

    @Test
    @DisplayName("No.2-5 表示件数の変更とページ送りができ、テーブル上部・下部の操作部品が連動する")
    void case2x5() {
        replaceUsers(unitData(SEED_PAGINATION));
        openUserMaster(1);

        verify.op("2. テーブル上部の「表示件数」で「10件」を選択する",
                () -> userPage.selectPageSize(UserMasterPage.PAGER_TOP, "10"),
                userPage.pageSizeSelect(UserMasterPage.PAGER_TOP));
        verify.gridRowsByKey("2. テーブルに SM0001〜SM0010 の10件が表示される", userPage.grid(),
                UserMasterPage.COL_USER_ID, userKeys(1, 10));
        verify.value("2. テーブル下部の「表示件数」も「10件」に連動して変わる",
                userPage.pageSizeSelect(UserMasterPage.PAGER_BOTTOM), "10");

        verify.op("3. テーブル下部の「表示ページ」で「2 / 8」を選択する",
                () -> userPage.selectPage(UserMasterPage.PAGER_BOTTOM, 1),
                userPage.pageJumpSelect(UserMasterPage.PAGER_BOTTOM));
        verify.gridRowsByKey("3. テーブルに SM0011〜SM0020 の10件が表示される", userPage.grid(10),
                UserMasterPage.COL_USER_ID, userKeys(11, 20));
        verify.value("3. テーブル上部の「表示ページ」も「2 / 8」に連動して変わる",
                userPage.pageJumpSelect(UserMasterPage.PAGER_TOP), "1");
        verify.text("3. テーブル上部のページネーションは2ページ目を強調表示する",
                userPage.activePagerButton(UserMasterPage.PAGER_TOP), "2");
        verify.text("3. テーブル下部のページネーションは2ページ目を強調表示する",
                userPage.activePagerButton(UserMasterPage.PAGER_BOTTOM), "2");
    }

    @Test
    @DisplayName("No.2-6 テーブル操作は画面内で完結し、画面遷移およびデータの再取得を伴わない")
    void case2x6() {
        replaceUsers(unitData(SEED_PAGINATION));
        openUserMaster(1);

        verify.noRequestSent("【機械検証】手順2〜4の間にサーバーへのデータ取得要求が 0件である", "GET", "/master/users", () -> {
            verify.op("2. 「表示件数」で「10件」を選択する",
                    () -> userPage.selectPageSize(UserMasterPage.PAGER_TOP, "10"),
                    userPage.pageSizeSelect(UserMasterPage.PAGER_TOP));
            verify.op("3. 「ユーザID」の列ヘッダーをダブルクリックしてソートを切り替える",
                    () -> userPage.doubleClickHeader(UserMasterPage.COL_USER_ID),
                    userPage.grid().headerCell(UserMasterPage.COL_USER_ID));
            verify.op("4. ページネーションの「›」を押下する",
                    () -> userPage.pressPagerButton(UserMasterPage.PAGER_TOP, "›"),
                    userPage.pagerNums(UserMasterPage.PAGER_TOP));
        });

        verify.urlIs("URL は /master/users のまま変わらず、画面は遷移しない", userPage.url());
        verify.text("取得データ件数は「取得データ件数: 80件」のまま変わらない",
                userPage.dataCountLabel(UserMasterPage.PAGER_TOP), "取得データ件数: 80件");
    }

    // ---- 3. 外部入出力（CSVダウンロード） ----

    @Test
    @DisplayName("No.3-1 CSVダウンロードで全てのユーザマスタレコードが既定の様式・列順・ファイル名で出力される")
    void case3x1() {
        openUserMaster(1);

        verify.csvDownloaded(
                "2. ファイル名「ユーザマスタ.csv」でダウンロードされ、" + "【機械検証】バイト列が CSV期待_ユーザマスタDL.csv（243バイト）と一致する",
                CSV_FILE_NAME, unitData(CSV_EXPECTED_ASC), () -> userPage.pressCsvDownload());
    }

    @Test
    @DisplayName("No.3-2 CSVのボディ行の並び順が表示中テーブルのソート順に従う")
    void case3x2() {
        openUserMaster(1);

        verify.op(
                "2. 「ユーザID」の列ヘッダーをダブルクリックしてユーザIDの降順に並び替える"
                        + "（初期表示でユーザID列に昇順が適用済みのため、1回のダブルクリックで降順になる）",
                () -> userPage.doubleClickHeader(UserMasterPage.COL_USER_ID),
                userPage.grid().headerCell(UserMasterPage.COL_USER_ID));
        verify.gridCellText("2. テーブルの先頭行が SM0003「開発 次郎」になる", userPage.grid(), 0,
                UserMasterPage.COL_USER_NAME, "開発 次郎");
        verify.gridCellText("2. テーブルの末尾行が SM0001「営業 太郎」になる", userPage.grid(), 2,
                UserMasterPage.COL_USER_NAME, "営業 太郎");

        verify.csvDownloaded(
                "3. ファイル名「ユーザマスタ.csv」でダウンロードされ、" + "【機械検証】バイト列が CSV期待_ユーザマスタDL降順.csv（243バイト）と一致する",
                CSV_FILE_NAME, unitData(CSV_EXPECTED_DESC), () -> userPage.pressCsvDownload());
    }

    @Test
    @DisplayName("No.3-3 ダウンロードしたCSVのパスワード列は、ユーザマスタに値が存在していても空欄で出力される")
    void case3x3() {
        verify.opPress("1. 共通ヘッダーの「ログアウト」を押下する", commonHeader.logoutButton(),
                () -> commonHeader.pressLogout(), loginPage.form());
        verify.opPressWithInput("1. / で SM0001 のユーザID・パスワードを入力し「ログイン」を押下する", () -> {
            loginPage.fillUserId(GENERAL_USER);
            loginPage.fillPassword(PASSWORD);
        }, loginPage.loginButton(), () -> {
            loginPage.loginButton().click();
            page.waitForURL(baseUrl() + ProjectListPage.PATH);
        }, commonHeader.screenTitleOf(ProjectListPage.SCREEN_TITLE));
        verify.urlIs("1. SM0001 のログインに成功し、案件情報一覧画面へ遷移する（パスワードが登録されている）",
                baseUrl() + ProjectListPage.PATH);

        verify.opPress("1. ログアウトする", commonHeader.logoutButton(), () -> commonHeader.pressLogout(),
                loginPage.form());
        verify.opPressWithInput("2. SM0002 でログインする", () -> {
            loginPage.fillUserId(ADMIN_USER);
            loginPage.fillPassword(PASSWORD);
        }, loginPage.loginButton(), () -> {
            loginPage.loginButton().click();
            page.waitForURL(baseUrl() + ProjectListPage.PATH);
        }, commonHeader.screenTitleOf(ProjectListPage.SCREEN_TITLE));
        openUserMaster(2);

        verify.csvDownloaded(
                "3. 【機械検証】ダウンロードしたCSVの各ボディ行の末尾のパスワード列が空欄である" + "（バイト列が CSV期待_ユーザマスタDL.csv と一致する）",
                CSV_FILE_NAME, unitData(CSV_EXPECTED_ASC), () -> userPage.pressCsvDownload());
    }

    // ---- 4. 外部入出力（CSVアップロード・編集） ----

    @Test
    @DisplayName("No.4-1 編集属性「1: ユーザの追加」でユーザマスタレコードが登録される")
    void case4x1() {
        openUserMaster(1);
        verify.gridRowsByKey("1. テーブルに3件（SM0001・SM0002・SM0003）が表示されている", userPage.grid(),
                UserMasterPage.COL_USER_ID, BASELINE_KEYS);

        uploadCsv("2. 「CSVアップロード」を押下し、編集_追加.csv を指定する", data("編集_追加.csv"), userPage.grid().root());

        verify.gridRowsByKey("ユーザマスタ管理画面に4件が一覧表示され、SM0010 が SM0003 の次に並ぶ", userPage.grid(),
                UserMasterPage.COL_USER_ID, List.of("SM0001", "SM0002", "SM0003", "SM0010"));
        verify.gridCellText("SM0010 のユーザ名が「新規 一郎」である", userPage.grid(), 3,
                UserMasterPage.COL_USER_NAME, "新規 一郎");
        verify.gridCellText("SM0010 のメールアドレスが sm0010@example.com である", userPage.grid(), 3,
                UserMasterPage.COL_EMAIL, "sm0010@example.com");
        verify.gridCellText("SM0010 の部署IDが 1 である", userPage.grid(), 3,
                UserMasterPage.COL_DEPARTMENT_ID, "1");
        verify.gridCellText("SM0010 のロールが「0: 一般ユーザ」である", userPage.grid(), 3,
                UserMasterPage.COL_ROLE, ROLE_USER);
        verify.gridCellText("既存の SM0001 の行は変わらない", userPage.grid(), 0, UserMasterPage.COL_USER_NAME,
                "営業 太郎");
        dataCountIs(4);
        verify.absent("エラー表示（エラータイトル・エラー内容）は表示されない", userPage.csvUploadButton(),
                userPage.errorArea());
    }

    @Test
    @DisplayName("No.4-2 編集属性「2: ユーザの更新」でユーザマスタレコードが更新される")
    void case4x2() {
        openUserMaster(1);
        verify.gridRowsByKey("1. テーブルに3件（SM0001・SM0002・SM0003）が表示されている", userPage.grid(),
                UserMasterPage.COL_USER_ID, BASELINE_KEYS);

        uploadCsv("2. 「CSVアップロード」を押下し、編集_更新.csv を指定する", data("編集_更新.csv"), userPage.grid().root());

        verify.gridRowsByKey("ユーザマスタ管理画面に3件が一覧表示される", userPage.grid(), UserMasterPage.COL_USER_ID,
                BASELINE_KEYS);
        verify.gridCellText("SM0001 の行のユーザ名が「営業 太郎（更新）」に変わる", userPage.grid(), 0,
                UserMasterPage.COL_USER_NAME, "営業 太郎（更新）");
        verify.gridCellText("SM0002 の行は変わらない", userPage.grid(), 1, UserMasterPage.COL_USER_NAME,
                "管理 花子");
        verify.gridCellText("SM0003 の行は変わらない", userPage.grid(), 2, UserMasterPage.COL_USER_NAME,
                "開発 次郎");
        dataCountIs(3);
        verify.absent("エラー表示は表示されない", userPage.csvUploadButton(), userPage.errorArea());
    }

    @Test
    @DisplayName("No.4-3 編集属性「3: ユーザの削除」でユーザマスタレコードが削除される")
    void case4x3() {
        openUserMaster(1);
        verify.gridRowsByKey("1. テーブルに3件（SM0001・SM0002・SM0003）が表示されている", userPage.grid(),
                UserMasterPage.COL_USER_ID, BASELINE_KEYS);

        uploadCsv("2. 「CSVアップロード」を押下し、編集_削除.csv を指定する", data("編集_削除.csv"), userPage.grid().root());

        verify.gridRowsByKey("SM0003「開発 次郎」の行が一覧から消え、SM0001・SM0002 の2行だけが表示される", userPage.grid(),
                UserMasterPage.COL_USER_ID, List.of("SM0001", "SM0002"));
        dataCountIs(2);
        verify.absent("エラー表示は表示されない", userPage.csvUploadButton(), userPage.errorArea());
    }

    @Test
    @DisplayName("No.4-4 編集属性「0: 何もしない」ではユーザマスタレコードに処理を実施しない")
    void case4x4() {
        openUserMaster(1);
        verify.gridRowsByKey("1. テーブルに3件（SM0001・SM0002・SM0003）が表示されている", userPage.grid(),
                UserMasterPage.COL_USER_ID, BASELINE_KEYS);

        uploadCsv("2. 「CSVアップロード」を押下し、編集_何もしない.csv を指定する", data("編集_何もしない.csv"),
                userPage.grid().root());

        verify.gridRowsByKey(
                "CSVに含まれない SM0002・SM0003 の行も削除されず表示されたままである" + "（洗い替えではなく行ごとの編集属性に従う編集である）",
                userPage.grid(), UserMasterPage.COL_USER_ID, BASELINE_KEYS);
        verify.gridCellText("SM0001 の行は「営業 太郎」のまま変わらない", userPage.grid(), 0,
                UserMasterPage.COL_USER_NAME, "営業 太郎");
        verify.gridCellText("SM0001 のメールアドレスも変わらない", userPage.grid(), 0, UserMasterPage.COL_EMAIL,
                "sm0001@example.com");
        verify.gridCellText("SM0001 の部署IDも変わらない", userPage.grid(), 0,
                UserMasterPage.COL_DEPARTMENT_ID, "1");
        verify.gridCellText("SM0001 のロールも変わらない", userPage.grid(), 0, UserMasterPage.COL_ROLE,
                ROLE_USER);
        dataCountIs(3);
        verify.absent("エラー表示は表示されない", userPage.csvUploadButton(), userPage.errorArea());
    }

    @Test
    @DisplayName("No.4-5 1つのCSVで「何もしない」「更新」「削除」「追加」が行ごとに実行される")
    void case4x5() {
        openUserMaster(1);
        verify.gridRowsByKey("1. テーブルに3件（SM0001・SM0002・SM0003）が表示されている", userPage.grid(),
                UserMasterPage.COL_USER_ID, BASELINE_KEYS);

        uploadCsv("2. 「CSVアップロード」を押下し、編集_混在.csv を指定する", data("編集_混在.csv"), userPage.grid().root());

        verify.gridRowsByKey(
                "テーブルはユーザIDの昇順に SM0001・SM0002・SM0010 の3行が並ぶ" + "（SM0003 は削除され、SM0010 が追加されている）",
                userPage.grid(), UserMasterPage.COL_USER_ID, List.of("SM0001", "SM0002", "SM0010"));
        verify.gridCellText("SM0001 の行のユーザ名が「営業 太郎（更新）」に変わる（更新）", userPage.grid(), 0,
                UserMasterPage.COL_USER_NAME, "営業 太郎（更新）");
        verify.gridCellText("SM0002「管理 花子」の行は変わらない（何もしない）", userPage.grid(), 1,
                UserMasterPage.COL_USER_NAME, "管理 花子");
        verify.gridCellText("SM0010「新規 一郎」の行が追加される（追加）", userPage.grid(), 2,
                UserMasterPage.COL_USER_NAME, "新規 一郎");
        dataCountIs(3);
        verify.absent("エラー表示は表示されない", userPage.csvUploadButton(), userPage.errorArea());
    }

    @Test
    @DisplayName("No.4-6 ファイルが指定されずにアップロードが実行された場合、編集を実施せずエラーを表示する")
    void case4x6() {
        openUserMaster(1);

        verify.op("2. 開発者ツール相当の合成操作で、CSVファイルを指定しないままアップロードを実行する",
                () -> userPage.submitUploadWithoutFile(), userPage.errorArea());

        verify.text("エラータイトル「CSVアップロードに失敗しました」が表示される", userPage.errorTitle(), FORMAT_ERROR_TITLE);
        verify.text("エラー内容「この項目は入力が必要です。」が表示される", userPage.errorMessages(), "この項目は入力が必要です。");
        baselineTableUnchanged();
    }

    @Test
    @DisplayName("No.4-7 【境界値】ファイル容量が5MBを超える場合、ファイル形式チェックに違反する")
    void case4x7() {
        openUserMaster(1);

        final Path file = E2eTestFiles.csvOfSize("ユーザマスタ_5MB超過.csv", CSV_HEADER, "1,SM0010,",
                ",sm0010@example.com,1,0,Passw0rd!", 5242881);
        uploadCsv("2. 「CSVアップロード」を押下し、ユーザマスタ_5MB超過.csv を指定する", file, userPage.errorArea());

        verify.text("エラータイトル「CSVアップロードに失敗しました」が表示される", userPage.errorTitle(), FORMAT_ERROR_TITLE);
        verify.texts("エラー内容は「ファイルの容量が大きすぎます(5MBまで)。」の1件のみである" + "（他のファイル形式チェックのメッセージは表示されない）",
                userPage.errorMessages(), new String[] {"ファイルの容量が大きすぎます(5MBまで)。"});
        baselineTableUnchanged();
    }

    @Test
    @DisplayName("No.4-8 【境界値】ファイル容量が5MBちょうどの場合、容量のファイル形式チェックに違反しない")
    void case4x8() {
        openUserMaster(1);

        final Path file = E2eTestFiles.csvOfSize("ユーザマスタ_5MBちょうど.csv", CSV_HEADER, "1,SM0010,",
                ",sm0010@example.com,1,0,Passw0rd!", 5242880);
        uploadCsv("2. 「CSVアップロード」を押下し、ユーザマスタ_5MBちょうど.csv を指定する", file, userPage.errorArea());

        verify.text("エラータイトル「CSVの内容にエラーがあります。詳細は以下の通りです。」が表示される" + "（容量のチェックを通過して後続の入力値チェックに進んでいる）",
                userPage.errorTitle(), INPUT_ERROR_TITLE);
        verify.texts(
                "エラー内容は「0002 - データ1行目 ユーザ名: 30文字以内で入力してください。」の1件のみであり、"
                        + "「ファイルの容量が大きすぎます(5MBまで)。」は表示されない",
                userPage.errorMessages(), new String[] {"0002 - データ1行目 ユーザ名: 30文字以内で入力してください。"});
        baselineTableUnchanged();
    }

    @Test
    @DisplayName("No.4-9 既定の様式のCSVファイルでない場合、ファイル形式チェックに違反する")
    void case4x9() {
        openUserMaster(1);
        verify.gridRowsByKey("1. テーブルに3件（SM0001・SM0002・SM0003）が表示されている", userPage.grid(),
                UserMasterPage.COL_USER_ID, BASELINE_KEYS);

        uploadCsv("2. 「CSVアップロード」を押下し、形式_様式不正.csv を指定する", data("形式_様式不正.csv"),
                userPage.errorArea());

        verify.text("エラータイトル「CSVアップロードに失敗しました」が表示される", userPage.errorTitle(), FORMAT_ERROR_TITLE);
        verify.text("エラー内容「CSVの読み取りに失敗しました。フォーマットが正しいかファイルを確認してください。」が表示される",
                userPage.errorMessages(), "CSVの読み取りに失敗しました。フォーマットが正しいかファイルを確認してください。");
        baselineTableUnchanged();
    }

    @Test
    @DisplayName("No.4-10 ボディ行にユーザマスタレコードが1件も存在しない場合、ファイル形式チェックに違反する")
    void case4x10() {
        openUserMaster(1);
        verify.gridRowsByKey("1. テーブルに3件（SM0001・SM0002・SM0003）が表示されている", userPage.grid(),
                UserMasterPage.COL_USER_ID, BASELINE_KEYS);

        uploadCsv("2. 「CSVアップロード」を押下し、形式_データ0件.csv を指定する", data("形式_データ0件.csv"),
                userPage.errorArea());

        verify.text("エラータイトル「CSVアップロードに失敗しました」が表示される", userPage.errorTitle(), FORMAT_ERROR_TITLE);
        verify.text("エラー内容「CSVファイルにデータがありませんでした。」が表示される", userPage.errorMessages(),
                "CSVファイルにデータがありませんでした。");
        baselineTableUnchanged();
    }

    @Test
    @DisplayName("No.4-11 ファイル形式チェック違反後に再度アップロードすると、既存のエラー表示が削除されたうえで再チェックされる")
    void case4x11() {
        openUserMaster(1);

        uploadCsv("1. 形式_データ0件.csv をアップロードする", data("形式_データ0件.csv"), userPage.errorArea());
        verify.text("2. エラータイトル「CSVアップロードに失敗しました」が表示される", userPage.errorTitle(),
                FORMAT_ERROR_TITLE);
        verify.text("2. エラー内容「CSVファイルにデータがありませんでした。」が表示される", userPage.errorMessages(),
                "CSVファイルにデータがありませんでした。");

        verify.transitionCleared("エラー表示が表示されている", userPage.errorArea(),
                "3. 「CSVアップロード」を押下し、編集_追加.csv を指定する",
                () -> uploadCsv("3. 「CSVアップロード」を押下し、編集_追加.csv を指定する", data("編集_追加.csv"),
                        userPage.grid().root()),
                userPage.grid().root(), "3. エラー表示が消える");
        verify.gridRowsByKey("3. 編集が実施されて4件（SM0001・SM0002・SM0003・SM0010）が一覧表示される", userPage.grid(),
                UserMasterPage.COL_USER_ID, List.of("SM0001", "SM0002", "SM0003", "SM0010"));
    }

    @Test
    @DisplayName("No.4-12 入力値チェック違反後に正しいCSVをアップロードすると、既存のエラーメッセージが削除されて編集が実施される")
    void case4x12() {
        openUserMaster(1);

        uploadCsv("1. 入力_ユーザ名必須.csv をアップロードする", data("入力_ユーザ名必須.csv"), userPage.errorArea());
        verify.text("2. エラータイトル「CSVの内容にエラーがあります。詳細は以下の通りです。」が表示される", userPage.errorTitle(),
                INPUT_ERROR_TITLE);
        verify.text("2. エラー内容「0002 - データ1行目 ユーザ名: この項目は入力が必要です。」が表示される", userPage.errorMessages(),
                "0002 - データ1行目 ユーザ名: この項目は入力が必要です。");

        verify.transitionCleared("エラー表示（エラータイトル・エラー内容）が表示されている", userPage.errorArea(),
                "3. 「CSVアップロード」を押下し、編集_追加.csv を指定する",
                () -> uploadCsv("3. 「CSVアップロード」を押下し、編集_追加.csv を指定する", data("編集_追加.csv"),
                        userPage.grid().root()),
                userPage.grid().root(), "3. エラー表示が消える");
        verify.gridRowsByKey("3. 編集が実施されて4件（SM0001・SM0002・SM0003・SM0010）が一覧表示される", userPage.grid(),
                UserMasterPage.COL_USER_ID, List.of("SM0001", "SM0002", "SM0003", "SM0010"));
    }

    @Test
    @DisplayName("No.4-13 追加したユーザのパスワードがハッシュ化して登録され、そのパスワードでログインできる")
    void case4x13() {
        openUserMaster(1);
        uploadCsv("1. 編集_追加_ログイン確認.csv をアップロードする（SM0011「認証 花子」がパスワード NewPass1! で追加される）",
                data("編集_追加_ログイン確認.csv"), userPage.grid().root());

        verify.gridRowsByKey("2. テーブルに SM0011 の行が追加される", userPage.grid(),
                UserMasterPage.COL_USER_ID, List.of("SM0001", "SM0002", "SM0003", "SM0011"));
        verify.gridCellText("2. SM0011 のユーザ名が「認証 花子」である", userPage.grid(), 3,
                UserMasterPage.COL_USER_NAME, "認証 花子");
        verify.gridCellText("2. SM0011 のメールアドレスが sm0011@example.com である", userPage.grid(), 3,
                UserMasterPage.COL_EMAIL, "sm0011@example.com");
        verify.gridCellText("2. SM0011 の部署IDが 1 である", userPage.grid(), 3,
                UserMasterPage.COL_DEPARTMENT_ID, "1");
        verify.gridCellText("2. SM0011 のロールが「0: 一般ユーザ」である", userPage.grid(), 3,
                UserMasterPage.COL_ROLE, ROLE_USER);
        dataCountIs(4);
        verify.absent("2. 追加したユーザの平文パスワード（NewPass1!）はテーブルに出力されない", userPage.grid().root(),
                page.locator("#userGrid :text('NewPass1!')"));

        verify.opPress("3. 共通ヘッダーの「ログアウト」を押下する", commonHeader.logoutButton(),
                () -> commonHeader.pressLogout(), loginPage.form());
        verify.opPressWithInput("4. ユーザID に SM0011、パスワードに NewPass1! を入力し「ログイン」を押下する", () -> {
            loginPage.fillUserId("SM0011");
            loginPage.fillPassword("NewPass1!");
        }, loginPage.loginButton(), () -> {
            loginPage.loginButton().click();
            page.waitForURL(baseUrl() + ProjectListPage.PATH);
        }, commonHeader.screenTitleOf(ProjectListPage.SCREEN_TITLE));
        verify.urlIs("4. ログインに成功し、案件情報一覧画面 /projects へ遷移する", baseUrl() + ProjectListPage.PATH);

        verify.opPress("5. 共通ヘッダーの「ログアウト」を押下する", commonHeader.logoutButton(),
                () -> commonHeader.pressLogout(), loginPage.form());
        verify.opPressWithInput("5. ユーザID に SM0011、パスワードに WrongPass1! を入力して「ログイン」を押下する", () -> {
            loginPage.fillUserId("SM0011");
            loginPage.fillPassword("WrongPass1!");
        }, loginPage.loginButton(), () -> {
            loginPage.loginButton().click();
            page.waitForLoadState();
        }, loginPage.failureMessage());
        verify.text("5. ログインに失敗し、ログイン失敗メッセージが表示される", loginPage.failureMessage(),
                "ユーザIDかパスワードが間違っています。");
    }

    // ---- 5. 入力チェック（CSV取込のユーザマスタレコード） ----

    @Test
    @DisplayName("No.5-1 編集属性が未入力の場合、必須入力に違反する")
    void case5x1() {
        expectInputError("入力_編集属性必須.csv", "0002 - データ1行目 編集属性: この項目は入力が必要です。");
    }

    @Test
    @DisplayName("No.5-2 編集属性が整数でない場合、整数チェックに違反する")
    void case5x2() {
        expectInputError("入力_編集属性整数以外.csv", "0002 - データ1行目 編集属性: 整数を入力してください。");
    }

    @Test
    @DisplayName("No.5-3 【境界値】編集属性が -1 の場合、最小数値(0)に違反する")
    void case5x3() {
        expectInputError("入力_編集属性最小数値.csv",
                "0002 - データ1行目 編集属性: 0:何もしない, 1:追加, 2:更新, 3:削除のいずれかで入力してください");
    }

    @Test
    @DisplayName("No.5-4 【境界値】編集属性が 4 の場合、最大数値(3)に違反する")
    void case5x4() {
        expectInputError("入力_編集属性最大数値.csv",
                "0002 - データ1行目 編集属性: 0:何もしない, 1:追加, 2:更新, 3:削除のいずれかで入力してください");
    }

    @Test
    @DisplayName("No.5-5 ユーザIDが未入力の場合、必須入力に違反する")
    void case5x5() {
        expectInputError("入力_ユーザID必須.csv", "0002 - データ1行目 ユーザID: この項目は入力が必要です。");
    }

    @Test
    @DisplayName("No.5-6 【境界値】ユーザIDが61文字の場合、最大文字数(60)に違反する")
    void case5x6() {
        expectInputError("入力_ユーザID最大文字数.csv", "0002 - データ1行目 ユーザID: 60文字以内で入力してください。");
    }

    @Test
    @DisplayName("No.5-7 ユーザIDに半角英数字以外を含む場合、使用文字に違反する")
    void case5x7() {
        expectInputError("入力_ユーザID使用文字.csv", "0002 - データ1行目 ユーザID: 使用不可能な文字が含まれています。");
    }

    @Test
    @DisplayName("No.5-8 ユーザIDが重複している場合、2回目以降の行に一意違反のエラーを表示する")
    void case5x8() {
        openUserMaster(1);
        verify.gridRowsByKey("1. テーブルに3件（SM0001・SM0002・SM0003）が表示されている", userPage.grid(),
                UserMasterPage.COL_USER_ID, BASELINE_KEYS);

        uploadCsv("2. 「CSVアップロード」を押下し、入力_ユーザID重複.csv を指定する", data("入力_ユーザID重複.csv"),
                userPage.errorArea());

        verify.text("エラータイトル「CSVの内容にエラーがあります。詳細は以下の通りです。」が表示される", userPage.errorTitle(),
                INPUT_ERROR_TITLE);
        verify.texts(
                "エラー内容は「0003 - データ2行目 ユーザID: 重複して設定しないでください(SM0010)」の1件のみであり、"
                        + "「0002 - データ1行目 ユーザID:」で始まるエラーメッセージは表示されない",
                userPage.errorMessages(),
                new String[] {"0003 - データ2行目 ユーザID: 重複して設定しないでください(SM0010)"});
        baselineTableUnchanged();
    }

    @Test
    @DisplayName("No.5-9 ユーザ名が未入力の場合、必須入力に違反する")
    void case5x9() {
        expectInputError("入力_ユーザ名必須.csv", "0002 - データ1行目 ユーザ名: この項目は入力が必要です。");
    }

    @Test
    @DisplayName("No.5-10 【境界値】ユーザ名が31文字の場合、最大文字数(30)に違反する")
    void case5x10() {
        expectInputError("入力_ユーザ名最大文字数.csv", "0002 - データ1行目 ユーザ名: 30文字以内で入力してください。");
    }

    @Test
    @DisplayName("No.5-11 ユーザ名に使用できない文字（半角カタカナ）を含む場合、使用文字に違反する")
    void case5x11() {
        expectInputError("入力_ユーザ名使用文字.csv", "0002 - データ1行目 ユーザ名: 使用不可能な文字が含まれています。");
    }

    @Test
    @DisplayName("No.5-12 メールアドレスが未入力の場合、必須入力に違反する")
    void case5x12() {
        expectInputError("入力_メールアドレス必須.csv", "0002 - データ1行目 メールアドレス: この項目は入力が必要です。");
    }

    @Test
    @DisplayName("No.5-13 【境界値】メールアドレスが61文字の場合、最大文字数(60)に違反する")
    void case5x13() {
        expectInputError("入力_メールアドレス最大文字数.csv", "0002 - データ1行目 メールアドレス: 60文字以内で入力してください。");
    }

    @Test
    @DisplayName("No.5-14 メールアドレスに半角英数字・半角記号以外を含む場合、使用文字に違反する")
    void case5x14() {
        expectInputError("入力_メールアドレス使用文字.csv", "0002 - データ1行目 メールアドレス: 使用不可能な文字が含まれています。");
    }

    @Test
    @DisplayName("No.5-15 部署IDが未入力の場合、必須入力に違反する")
    void case5x15() {
        expectInputError("入力_部署ID必須.csv", "0002 - データ1行目 部署ID: この項目は入力が必要です。");
    }

    @Test
    @DisplayName("No.5-16 部署IDが整数でない場合、整数チェックに違反する")
    void case5x16() {
        expectInputError("入力_部署ID整数以外.csv", "0002 - データ1行目 部署ID: 整数を入力してください。");
    }

    @Test
    @DisplayName("No.5-17 部署IDが部署マスタに存在しない場合、存在チェックに違反する")
    void case5x17() {
        expectInputError("入力_部署IDマスタ不存在.csv", "0002 - データ1行目 部署ID: 部署IDはマスタに存在する値を設定してください");
    }

    @Test
    @DisplayName("No.5-18 ロールが未入力の場合、必須入力に違反する")
    void case5x18() {
        expectInputError("入力_ロール必須.csv", "0002 - データ1行目 ロール: この項目は入力が必要です。");
    }

    @Test
    @DisplayName("No.5-19 ロールが整数でない場合、整数チェックに違反する")
    void case5x19() {
        expectInputError("入力_ロール整数以外.csv", "0002 - データ1行目 ロール: 整数を入力してください。");
    }

    @Test
    @DisplayName("No.5-20 ロールが 0 または 9 以外の場合、区分値のチェックに違反する")
    void case5x20() {
        expectInputError("入力_ロール区分値以外.csv", "0002 - データ1行目 ロール: 0:一般ユーザ, 9:システム管理者のいずれかで入力してください。");
    }

    @Test
    @DisplayName("No.5-21 【境界値】ユーザID 60文字・ユーザ名 30文字・メールアドレス 60文字・パスワード 8文字は入力値チェックに違反せず取り込まれる")
    void case5x21() {
        openUserMaster(1);
        verify.gridRowsByKey("1. テーブルに3件（SM0001・SM0002・SM0003）が表示されている", userPage.grid(),
                UserMasterPage.COL_USER_ID, BASELINE_KEYS);

        uploadCsv("2. 「CSVアップロード」を押下し、入力_境界値正常.csv を指定する", data("入力_境界値正常.csv"),
                userPage.grid().root());

        verify.absent("エラー表示（エラータイトル・エラー内容）は表示されない", userPage.csvUploadButton(),
                userPage.errorArea());
        verify.gridRowsByKey("ユーザマスタ管理画面に4件が一覧表示される", userPage.grid(), UserMasterPage.COL_USER_ID,
                List.of("SM0001", "SM0002", "SM0003", "a".repeat(60)));
        verify.gridCellText("追加された行のユーザIDは半角英字60文字である", userPage.grid(), 3,
                UserMasterPage.COL_USER_ID, "a".repeat(60));
        verify.gridCellText("追加された行のユーザ名は全角30文字である", userPage.grid(), 3,
                UserMasterPage.COL_USER_NAME, "あ".repeat(30));
        verify.gridCellText("追加された行のメールアドレスは60文字である", userPage.grid(), 3, UserMasterPage.COL_EMAIL,
                "a".repeat(48) + "@example.com");
        verify.gridCellText("追加された行の部署IDは 1 である", userPage.grid(), 3,
                UserMasterPage.COL_DEPARTMENT_ID, "1");
        verify.gridCellText("追加された行のロールは「0: 一般ユーザ」である", userPage.grid(), 3, UserMasterPage.COL_ROLE,
                ROLE_USER);
        dataCountIs(4);
    }

    @Test
    @DisplayName("No.5-22 編集属性が「1: ユーザの追加」でパスワードが未入力の場合、必須入力に違反する")
    void case5x22() {
        expectInputError("入力_パスワード必須.csv", "0002 - データ1行目 パスワード: ユーザ追加の場合、必須項目です。");
    }

    @Test
    @DisplayName("No.5-23 編集属性が「2: ユーザの更新」でパスワードがブランクの場合、パスワードの入力値チェックを行わない")
    void case5x23() {
        openUserMaster(1);
        verify.gridRowsByKey("1. テーブルに3件（SM0001・SM0002・SM0003）が表示されている", userPage.grid(),
                UserMasterPage.COL_USER_ID, BASELINE_KEYS);

        uploadCsv("2. 「CSVアップロード」を押下し、入力_更新パスワードブランク.csv を指定する", data("入力_更新パスワードブランク.csv"),
                userPage.grid().root());

        verify.absent("パスワードに関するエラーメッセージを含むエラー表示は表示されない", userPage.csvUploadButton(),
                userPage.errorArea());
        verify.gridRowsByKey("編集が実施されて3件が一覧表示される", userPage.grid(), UserMasterPage.COL_USER_ID,
                BASELINE_KEYS);
        verify.gridCellText("SM0001 の行のユーザ名が「営業 太郎（更新）」に変わる", userPage.grid(), 0,
                UserMasterPage.COL_USER_NAME, "営業 太郎（更新）");
    }

    @Test
    @DisplayName("No.5-24 同じ行の複数の項目でチェック違反がある場合、列の順序ごとに全てのエラーメッセージを表示する")
    void case5x24() {
        openUserMaster(1);
        verify.gridRowsByKey("1. テーブルに3件（SM0001・SM0002・SM0003）が表示されている", userPage.grid(),
                UserMasterPage.COL_USER_ID, BASELINE_KEYS);

        uploadCsv("2. 「CSVアップロード」を押下し、入力_同一行複数項目.csv を指定する", data("入力_同一行複数項目.csv"),
                userPage.errorArea());

        verify.text("エラータイトル「CSVの内容にエラーがあります。詳細は以下の通りです。」が表示される", userPage.errorTitle(),
                INPUT_ERROR_TITLE);
        verify.texts("エラー内容がユーザ名・ロールの順（列の順序）で2件並ぶ", userPage.errorMessages(),
                new String[] {"0002 - データ1行目 ユーザ名: この項目は入力が必要です。",
                        "0002 - データ1行目 ロール: 0:一般ユーザ, 9:システム管理者のいずれかで入力してください。"});
        baselineTableUnchanged();
    }

    @Test
    @DisplayName("No.5-25 同じ項目内で複数の入力値チェックに違反した場合、より小さな番号のエラーメッセージ1つのみを表示する")
    void case5x25() {
        openUserMaster(1);
        verify.gridRowsByKey("1. テーブルに3件（SM0001・SM0002・SM0003）が表示されている", userPage.grid(),
                UserMasterPage.COL_USER_ID, BASELINE_KEYS);

        uploadCsv("2. 「CSVアップロード」を押下し、入力_編集属性必須.csv を指定する", data("入力_編集属性必須.csv"),
                userPage.errorArea());

        verify.text("エラータイトル「CSVの内容にエラーがあります。詳細は以下の通りです。」が表示される", userPage.errorTitle(),
                INPUT_ERROR_TITLE);
        verify.texts("エラー内容は「0002 - データ1行目 編集属性: この項目は入力が必要です。」の1件のみであり、" + "「整数を入力してください。」は表示されない",
                userPage.errorMessages(), new String[] {"0002 - データ1行目 編集属性: この項目は入力が必要です。"});
        baselineTableUnchanged();
    }

    @Test
    @DisplayName("No.5-26 CSV行番号が5桁以上の場合、0埋めをせずそのまま表示する")
    void case5x26() {
        openUserMaster(1);
        verify.gridRowsByKey("1. テーブルに3件（SM0001・SM0002・SM0003）が表示されている", userPage.grid(),
                UserMasterPage.COL_USER_ID, BASELINE_KEYS);

        List<String> lines = new ArrayList<>();
        for (int no = 1; no <= 9999; no++) {
            String userId = String.format("SM%04d", no);
            String digits = String.format("%04d", no);
            String userName = no == 9999 ? "" : "利用者" + digits;
            lines.add("0," + userId + "," + userName + "," + userId.toLowerCase()
                    + "@example.com,1,0,");
        }
        final Path file = E2eTestFiles.csvOfLines("ユーザマスタ_行番号5桁.csv", CSV_HEADER, lines);
        uploadCsv("2. 「CSVアップロード」を押下し、ユーザマスタ_行番号5桁.csv を指定する", file, userPage.errorArea());

        verify.text("エラータイトル「CSVの内容にエラーがあります。詳細は以下の通りです。」が表示される", userPage.errorTitle(),
                INPUT_ERROR_TITLE);
        verify.text(
                "エラー内容「10000 - データ9999行目 ユーザ名: この項目は入力が必要です。」が表示される"
                        + "（CSV行番号 10000 は5桁のため0埋めをせずそのまま表示される）",
                userPage.errorMessages(), "10000 - データ9999行目 ユーザ名: この項目は入力が必要です。");
        baselineTableUnchanged();
    }

    @Test
    @DisplayName("No.5-27 入力値チェック違反後に再度アップロードすると、既存のエラーメッセージが削除されたうえで再チェックされる")
    void case5x27() {
        openUserMaster(1);

        uploadCsv("1. 入力_ユーザ名必須.csv をアップロードする", data("入力_ユーザ名必須.csv"), userPage.errorArea());
        verify.text("2. エラー内容「0002 - データ1行目 ユーザ名: この項目は入力が必要です。」が表示される", userPage.errorMessages(),
                "0002 - データ1行目 ユーザ名: この項目は入力が必要です。");

        uploadCsv("3. 「CSVアップロード」を押下し、入力_メールアドレス必須.csv を指定する", data("入力_メールアドレス必須.csv"),
                userPage.errorArea());
        verify.texts(
                "3. エラー内容が「0002 - データ1行目 メールアドレス: この項目は入力が必要です。」だけになる" + "（手順2のエラーメッセージが消えている）",
                userPage.errorMessages(), new String[] {"0002 - データ1行目 メールアドレス: この項目は入力が必要です。"});
        baselineTableUnchanged();
    }

    @Test
    @DisplayName("No.5-28 【境界値】パスワードが7文字の場合、最小文字数(8)に違反する")
    void case5x28() {
        expectInputError("入力_パスワード最小文字数.csv", "0002 - データ1行目 パスワード: 8文字以上で入力してください。");
    }

    @Test
    @DisplayName("No.5-29 【境界値】パスワードが61文字の場合、最大文字数(60)に違反する")
    void case5x29() {
        expectInputError("入力_パスワード最大文字数.csv", "0002 - データ1行目 パスワード: 60文字以内で入力してください。");
    }

    @Test
    @DisplayName("No.5-30 【境界値】パスワードが60文字の場合、最大文字数(60)に違反しない")
    void case5x30() {
        openUserMaster(1);
        verify.gridRowsByKey("1. テーブルに3件（SM0001・SM0002・SM0003）が表示されている", userPage.grid(),
                UserMasterPage.COL_USER_ID, BASELINE_KEYS);

        uploadCsv("2. 「CSVアップロード」を押下し、入力_境界値正常_パスワード60文字.csv を指定する", data("入力_境界値正常_パスワード60文字.csv"),
                userPage.grid().root());

        verify.absent("パスワード欄に関するエラーメッセージを含むエラー表示は表示されない", userPage.csvUploadButton(),
                userPage.errorArea());
        verify.gridRowsByKey("編集が実施されて4件が一覧表示され、SM0012 の行が追加される", userPage.grid(),
                UserMasterPage.COL_USER_ID, List.of("SM0001", "SM0002", "SM0003", "SM0012"));
        verify.gridCellText("SM0012 のユーザ名が「境界 三郎」である", userPage.grid(), 3,
                UserMasterPage.COL_USER_NAME, "境界 三郎");
        verify.gridCellText("SM0012 のメールアドレスが sm0012@example.com である", userPage.grid(), 3,
                UserMasterPage.COL_EMAIL, "sm0012@example.com");
        verify.gridCellText("SM0012 の部署IDが 1 である", userPage.grid(), 3,
                UserMasterPage.COL_DEPARTMENT_ID, "1");
        verify.gridCellText("SM0012 のロールが「0: 一般ユーザ」である", userPage.grid(), 3,
                UserMasterPage.COL_ROLE, ROLE_USER);
        dataCountIs(4);
    }

    @Test
    @DisplayName("No.5-31 パスワードに半角英数字・半角記号以外を含む場合、使用文字に違反する")
    void case5x31() {
        expectInputError("入力_パスワード使用文字.csv", "0002 - データ1行目 パスワード: 使用不可能な文字が含まれています。");
    }

    @Test
    @DisplayName("No.5-32 パスワードが大文字・小文字・数字・記号のうち3種類以上を含まない場合、種類数のチェックに違反する")
    void case5x32() {
        expectInputError("入力_パスワード種類数.csv", "0002 - データ1行目 パスワード: 大文字小文字数字記号のうち３つ以上含めてください。");
    }

    // ---- 6. 業務エラー・サーバー処理／認証・認可（異常分岐） ----

    @Test
    @DisplayName("No.6-1 編集属性「1: ユーザの追加」で既存のユーザIDを登録しようとすると異常終了する")
    void case6x1() {
        expectBusinessError("失敗_追加_既存ユーザID.csv", "ユーザIDSM0001は既に存在します。");
    }

    @Test
    @DisplayName("No.6-2 編集属性「2: ユーザの更新」で存在しないユーザIDを更新しようとすると異常終了する")
    void case6x2() {
        expectBusinessError("失敗_更新_存在しないユーザ.csv", "存在しないユーザ(SM9999)です。");
    }

    @Test
    @DisplayName("No.6-3 編集属性「2: ユーザの更新」で他のユーザが同じレコードを更新・削除しようとしている場合、異常終了する")
    void case6x3() {
        verify.opArmFault("1. （障害シーム）「排他エラー（ユーザマスタ更新）」を有効化する",
                UserMasterFaultSeamConfig.CONFLICT_ON_UPDATE);
        openUserMaster(2);
        verify.gridRowsByKey("2. テーブルに3件が表示されている", userPage.grid(), UserMasterPage.COL_USER_ID,
                BASELINE_KEYS);

        uploadCsv("3. 「CSVアップロード」を押下し、編集_更新.csv を指定する", data("編集_更新.csv"), userPage.errorArea());

        verify.text("エラータイトル「1行目で異常終了しました。」が表示される", userPage.errorTitle(), BUSINESS_ERROR_TITLE);
        verify.text("エラー内容「他のユーザがデータを更新中です。」が表示される", userPage.errorMessages(), "他のユーザがデータを更新中です。");
        verify.faultFired("【機械検証】障害シーム「排他エラー（ユーザマスタ更新）」が発火した",
                UserMasterFaultSeamConfig.CONFLICT_ON_UPDATE);
        baselineTableUnchanged();
    }

    @Test
    @DisplayName("No.6-4 編集属性「3: ユーザの削除」で自身のユーザマスタレコードを削除しようとすると異常終了する")
    void case6x4() {
        expectBusinessError("失敗_削除_自身.csv", "自分のデータは削除できません。");
    }

    @Test
    @DisplayName("No.6-5 編集属性「3: ユーザの削除」で存在しないユーザIDを削除しようとすると異常終了する")
    void case6x5() {
        expectBusinessError("失敗_削除_存在しないユーザ.csv", "存在しないユーザ(SM9999)です。");
    }

    @Test
    @DisplayName("No.6-6 編集属性「3: ユーザの削除」で他のユーザが同じレコードを更新・削除しようとしている場合、異常終了する")
    void case6x6() {
        verify.opArmFault("1. （障害シーム）「排他エラー（ユーザマスタ削除）」を有効化する",
                UserMasterFaultSeamConfig.CONFLICT_ON_DELETE);
        openUserMaster(2);
        verify.gridRowsByKey("2. テーブルに3件が表示されている", userPage.grid(), UserMasterPage.COL_USER_ID,
                BASELINE_KEYS);

        uploadCsv("3. 「CSVアップロード」を押下し、編集_削除.csv を指定する", data("編集_削除.csv"), userPage.errorArea());

        verify.text("エラータイトル「1行目で異常終了しました。」が表示される", userPage.errorTitle(), BUSINESS_ERROR_TITLE);
        verify.text("エラー内容「他のユーザがデータを更新中です。」が表示される", userPage.errorMessages(), "他のユーザがデータを更新中です。");
        verify.faultFired("【機械検証】障害シーム「排他エラー（ユーザマスタ削除）」が発火した",
                UserMasterFaultSeamConfig.CONFLICT_ON_DELETE);
        baselineTableUnchanged();
    }

    @Test
    @DisplayName("No.6-7 業務エラー以外のエラーが発生した場合、編集前の状態に戻りエラー画面に【サーバー内部エラー】が表示される")
    void case6x7() {
        verify.opArmFault("1. （障害シーム）「データベース例外（ユーザマスタ編集）」を有効化する",
                UserMasterFaultSeamConfig.DB_EXCEPTION_ON_EDIT);
        openUserMaster(2);

        uploadCsv("3. 「CSVアップロード」を押下し、編集_追加.csv を指定する", data("編集_追加.csv"), errorPage.card());

        verify.text("3. エラータイトル「An Error Occurred」が表示される", errorPage.errorTitle(),
                "An Error Occurred");
        verify.text("3. エラー内容「エラーが発生しました。」が表示される", errorPage.errorMessage(), "エラーが発生しました。");
        verify.faultFired("【機械検証】障害シーム「データベース例外（ユーザマスタ編集）」が発火した",
                UserMasterFaultSeamConfig.DB_EXCEPTION_ON_EDIT);

        openUserMaster(4);
        verify.gridRowsByKey("4. 3件（SM0001・SM0002・SM0003）が表示され、編集前の状態に戻っている" + "（SM0010 は登録されていない）",
                userPage.grid(), UserMasterPage.COL_USER_ID, BASELINE_KEYS);
        dataCountIs(3);
    }

    @Test
    @DisplayName("No.6-8 CSVダウンロード時にログインの有効期限が切れている場合、ダウンロードは実施されず案件情報一覧画面へ遷移する")
    void case6x8() {
        openUserMaster(1);

        verify.op("2. ブラウザが保持するセッションCookieを削除する（ログインの有効期限が切れた状態にする）",
                () -> userPage.clearSessionCookies());
        verify.machineEquals("手順2の後、ブラウザが保持するセッションCookieが削除されている", "0",
                String.valueOf(userPage.sessionCookieCount()));

        verify.noDownload("【機械検証】手順3を通じてファイルのダウンロードが発生していない",
                () -> verify.opPress("3. 「CSVダウンロード」を押下する", userPage.csvDownloadButton(), () -> {
                    userPage.pressCsvDownloadWithoutWaiting();
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
        verify.urlIs("4. ユーザマスタ管理画面ではなく案件情報一覧画面 /projects へ遷移する", baseUrl() + ProjectListPage.PATH);
    }

    @Test
    @DisplayName("No.6-9 編集時にログインの有効期限が切れている場合、編集は実施されず案件情報一覧画面へ遷移する")
    void case6x9() {
        openUserMaster(1);

        verify.op("2. ブラウザが保持するセッションCookieを削除する（ログインの有効期限が切れた状態にする）",
                () -> userPage.clearSessionCookies());
        verify.machineEquals("手順2の後、ブラウザが保持するセッションCookieが削除されている", "0",
                String.valueOf(userPage.sessionCookieCount()));

        uploadCsv("3. 「CSVアップロード」を押下して編集_追加.csv を指定する", data("編集_追加.csv"), loginPage.form());
        verify.visible("3. ユーザマスタ管理画面は再表示されずログイン画面が表示される", loginPage.form());

        verify.opPressWithInput("4. SM0002 のユーザID・パスワードを入力し「ログイン」を押下する", () -> {
            loginPage.fillUserId(ADMIN_USER);
            loginPage.fillPassword(PASSWORD);
        }, loginPage.loginButton(), () -> {
            loginPage.loginButton().click();
            page.waitForURL(baseUrl() + ProjectListPage.PATH);
        }, commonHeader.screenTitleOf(ProjectListPage.SCREEN_TITLE));
        verify.urlIs("4. ユーザマスタ管理画面ではなく案件情報一覧画面 /projects へ遷移する", baseUrl() + ProjectListPage.PATH);

        openUserMaster(5);
        verify.gridRowsByKey("5. 3件（SM0001・SM0002・SM0003）が表示され、編集は実施されていない" + "（SM0010 は登録されていない）",
                userPage.grid(), UserMasterPage.COL_USER_ID, BASELINE_KEYS);
    }
}
