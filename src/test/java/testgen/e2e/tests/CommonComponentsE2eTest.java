package testgen.e2e.tests;

import com.microsoft.playwright.Download;
import com.microsoft.playwright.Locator;
import jakarta.servlet.ServletContext;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import testgen.e2e.ProjectE2eTest;
import testgen.e2e.data.E2eTestFiles;
import testgen.e2e.pages.CommonHeaderPage;
import testgen.e2e.pages.CompanyMasterPage;
import testgen.e2e.pages.DepartmentMasterPage;
import testgen.e2e.pages.ErrorPage;
import testgen.e2e.pages.LoginPage;
import testgen.e2e.pages.PlainTextResourcePage;
import testgen.e2e.pages.ProjectEditPage;
import testgen.e2e.pages.ProjectFormPage;
import testgen.e2e.pages.ProjectListPage;
import testgen.e2e.pages.ProjectRegisterPage;
import testgen.e2e.pages.UserMasterPage;
import testgen.e2e.seams.ProjectSearchFaultSeamConfig;
import testgen.e2e.support.E2eFeature;
import testgen.e2e.support.E2eMailServer;
import testgen.e2e.support.E2eMailbox;
import testgen.e2e.support.evidence.E2eVerify;
import testgen.e2e.support.evidence.EvidenceV2;

/**
 * 共通部品（機能 {@code 00_共通部品}）の E2E テスト。
 *
 * <p>
 * テストケース仕様書 {@code 00_共通部品/00_共通部品_テスト仕様書.xlsx} の全ケースを実装する。 画面部品（テキスト・テキストエリア・セレクト・カレンダー・オートコンプリート・
 * ページネーション・ファイルピッカー）、共通レイアウト、認証・認可、パーマリンク、API 共通仕様、CSV 様式、 営業情報メールを横断して検証する。
 */
@E2eFeature("00_共通部品")
@EvidenceV2
@Import({E2eMailServer.class, ProjectSearchFaultSeamConfig.class})
class CommonComponentsE2eTest extends ProjectE2eTest {

    /** 一般ユーザ SM0001（営業 太郎／営業部）。 */
    private static final String GENERAL_USER = "SM0001";

    /** システム管理者 SM0002（管理 花子／管理部）。 */
    private static final String ADMIN_USER = "SM0002";

    /** ベースラインのユーザに共通の開発用ダミーパスワード。 */
    private static final String PASSWORD = "Passw0rd!";

    /** ベースラインが案件情報0件のとき、登録される案件情報の管理コード。 */
    private static final int FIRST_CODE = 1001;

    /** 会社マスタ80件のシード（ページネーション確認用）。 */
    private static final String SEED_PAGINATION = "00_共通部品/data/ページネーション_会社マスタ.csv";

    /** CSV 期待値正本: 会社マスタのダウンロード。 */
    private static final String CSV_EXPECTED = "00_共通部品/data/CSV様式_期待_会社マスタDL.csv";

    /** アップロード CSV: 既定の様式（囲み文字あり）。 */
    private static final String CSV_UPLOAD_OK = "00_共通部品/data/CSV様式_アップロード_正常.csv";

    /** アップロード CSV: 閉じられていない囲み文字を含む。 */
    private static final String CSV_UPLOAD_NG = "00_共通部品/data/CSV様式_アップロード_囲み文字不正.csv";

    /** トリム確認の入力値（前後に半角＋全角スペース、内部にも半角＋全角スペース）。 */
    private static final String TRIM_INPUT = " 　A 　B 　";

    /** トリム後に表示される値。 */
    private static final String TRIMMED = "A 　B";

    /** 必須入力エラー。 */
    private static final String REQUIRED = "この項目は入力が必要です。";

    /** 添付ファイル削除の確認ダイアログ本文。 */
    private static final String DELETE_CONFIRM = "この添付ファイルを削除しますか？";

    private LoginPage loginPage;
    private CommonHeaderPage commonHeader;
    private ProjectRegisterPage registerPage;
    private ProjectEditPage editPage;
    private ProjectListPage listPage;
    private CompanyMasterPage companyPage;
    private UserMasterPage userPage;
    private DepartmentMasterPage departmentPage;
    private ErrorPage errorPage;

    @Autowired
    private E2eMailbox mailbox;

    @Autowired
    private ServletContext servletContext;


    @BeforeEach
    void setUpPages() {
        loginPage = new LoginPage(page, baseUrl());
        commonHeader = new CommonHeaderPage(page, baseUrl());
        registerPage = new ProjectRegisterPage(page, baseUrl());
        editPage = new ProjectEditPage(page, baseUrl());
        listPage = new ProjectListPage(page, baseUrl());
        companyPage = new CompanyMasterPage(page, baseUrl());
        userPage = new UserMasterPage(page, baseUrl());
        departmentPage = new DepartmentMasterPage(page, baseUrl());
        errorPage = new ErrorPage(page, baseUrl());
        mailbox.reset();
    }

    // ---- 共通手順 ----

    /** 一般ユーザでログインする（前提条件の作成。仕様書の操作手順ではない）。 */
    private void signInAsGeneralUser() {
        loginPage.signIn(GENERAL_USER, PASSWORD, ProjectListPage.PATH);
    }

    /** システム管理者でログインする（前提条件の作成）。 */
    private void signInAsAdmin() {
        loginPage.signIn(ADMIN_USER, PASSWORD, ProjectListPage.PATH);
    }

    /** 案件情報登録画面を開く。 */
    private void openRegister(int stepNo) {
        verify.opOpen(stepNo + ". /projects/new を開く", () -> registerPage.open());
    }

    /** 「件名」を入力する。 */
    private void fillSubject(int stepNo, String subject) {
        verify.op(stepNo + ". 「件名」に「" + subject + "」を入力する",
                () -> registerPage.fill(ProjectFormPage.SUBJECT, subject),
                registerPage.input(ProjectFormPage.SUBJECT));
    }

    /** 「登録」を押下する。 */
    private void pressRegister(int stepNo) {
        verify.opPress(stepNo + ". 「登録」を押下する", registerPage.registerButton(),
                () -> registerPage.pressRegister());
    }

    /** 案件情報更新画面 /projects/detail/1001 へ遷移したことを確認する。 */
    private void movedToEdit() {
        verify.urlIs("案件情報更新画面 /projects/detail/1001 へ遷移する", editPage.url(FIRST_CODE));
    }

    /** 会社マスタ管理画面を開き、表示件数を10件にする。 */
    private void openCompanyMasterWithTenPerPage(int openStep, int sizeStep) {
        verify.opOpen(openStep + ". /master/companies を開く", () -> companyPage.open());
        verify.op(sizeStep + ". テーブル上部の「表示件数」で「10件」を選択する",
                () -> companyPage.selectPageSize(CompanyMasterPage.PAGER_TOP, "10"),
                companyPage.pageSizeSelect(CompanyMasterPage.PAGER_TOP));
    }

    /** 会社IDの昇順に並ぶ行キーの一覧を作る。 */
    private static List<String> companyKeys(int from, int to) {
        List<String> keys = new ArrayList<>();
        for (int id = from; id <= to; id++) {
            keys.add(String.valueOf(id));
        }
        return keys;
    }

    /** ページネーションのボタンを押下する。 */
    private void pressPager(String stepDesc, String label) {
        verify.opPress(stepDesc, companyPage.pagerButton(CompanyMasterPage.PAGER_TOP, label),
                () -> companyPage.pressPagerButton(CompanyMasterPage.PAGER_TOP, label),
                companyPage.grid().root());
    }

    /** ファイルピッカーでファイルを指定し、表示を確認する。 */
    private void chooseAttachment(String stepDesc, Path file) {
        verify.opChooseFile(stepDesc, registerPage.attachmentDropArea(), file,
                registerPage.attachmentDropArea());
    }

    // ---- 1. 画面部品（テキスト・テキストエリア・セレクト・カレンダー・エラーメッセージ） ----

    @Test
    @DisplayName("No.1-1 テキストのトリム（前後の半角・全角スペースを除去し、内部のスペースは保持する）")
    void case1x1() {
        signInAsGeneralUser();
        openRegister(1);
        verify.op("2. 「件名」に「␣　A␣　B␣　」を入力する",
                () -> registerPage.fill(ProjectFormPage.SUBJECT, TRIM_INPUT),
                registerPage.input(ProjectFormPage.SUBJECT));
        pressRegister(3);

        movedToEdit();
        verify.value("「件名」に「A␣　B」が表示される（前後のスペースが除去され、内部のスペースは保持される）",
                editPage.input(ProjectFormPage.SUBJECT), TRIMMED);
        verify.absent("「件名」欄の直下に入力値チェックのエラーメッセージは表示されない", editPage.fieldLabel("件名"),
                editPage.fieldError(ProjectFormPage.SUBJECT));
    }

    @Test
    @DisplayName("No.1-2 テキストの入力可能文字（全角文字・半角英数字・半角記号）と、Enterキーでは改行も送信もされないこと")
    void case1x2() {
        signInAsGeneralUser();
        openRegister(1);
        final String value = "全角アイウ ABCabc123 !#$%&()";
        fillSubject(2, value);

        verify.noRequestSent("【機械検証】手順3の後、登録の要求（POST /projects/new）が送信されていない", "POST",
                "/projects/new",
                () -> verify.op("3. 「件名」の入力欄にフォーカスした状態で Enter キーを押下する",
                        () -> registerPage.input(ProjectFormPage.SUBJECT).press("Enter"),
                        registerPage.input(ProjectFormPage.SUBJECT)));
        verify.value("3. 「件名」の入力欄は1行のまま変わらず、改行は入力されない", registerPage.input(ProjectFormPage.SUBJECT),
                value);
        verify.urlIs("3. 画面遷移せず案件情報登録画面のままである", registerPage.url());

        pressRegister(4);
        movedToEdit();
        verify.value("4. 「件名」に入力した文字列がそのまま表示される", editPage.input(ProjectFormPage.SUBJECT), value);
        verify.absent("「件名」欄の直下に「使用不可能な文字が含まれています。」は表示されない", editPage.fieldLabel("件名"),
                editPage.fieldError(ProjectFormPage.SUBJECT));
    }

    @Test
    @DisplayName("No.1-3 テキストエリアのトリム（前後の半角・全角スペースを除去し、内部のスペースは保持する）")
    void case1x3() {
        signInAsGeneralUser();
        openRegister(1);
        fillSubject(2, "トリム確認");
        verify.op("3. 「案件概要」に「␣　A␣　B␣　」を入力する",
                () -> registerPage.fill(ProjectFormPage.OVERVIEW, TRIM_INPUT),
                registerPage.input(ProjectFormPage.OVERVIEW));
        pressRegister(4);

        movedToEdit();
        verify.value("「案件概要」に「A␣　B」が表示される（前後のスペースが除去され、内部のスペースは保持される）",
                editPage.input(ProjectFormPage.OVERVIEW), TRIMMED);
    }

    @Test
    @DisplayName("No.1-4 テキストエリアは複数行を入力でき、改行文字が保持される")
    void case1x4() {
        signInAsGeneralUser();
        openRegister(1);
        fillSubject(2, "改行確認");
        verify.op("3. 「案件概要」に「1行目」を入力し、Enter キーを押下してから「2行目」を入力する", () -> {
            registerPage.input(ProjectFormPage.OVERVIEW).click();
            registerPage.input(ProjectFormPage.OVERVIEW).type("1行目");
            registerPage.input(ProjectFormPage.OVERVIEW).press("Enter");
            registerPage.input(ProjectFormPage.OVERVIEW).type("2行目");
        }, registerPage.input(ProjectFormPage.OVERVIEW));
        verify.value("3. 「案件概要」の入力欄が2行の表示になる", registerPage.input(ProjectFormPage.OVERVIEW),
                "1行目\n2行目");
        pressRegister(4);

        movedToEdit();
        verify.value("「案件概要」に「1行目」「2行目」が改行で区切られた2行として表示される",
                editPage.input(ProjectFormPage.OVERVIEW), "1行目\n2行目");
    }

    @Test
    @DisplayName("No.1-5 セレクトボックスはクリックで選択肢が表示され、選択肢のクリックで値が入力される")
    void case1x5() {
        signInAsGeneralUser();
        openRegister(1);
        fillSubject(2, "セレクト確認");

        verify.op("3. 「契約種別」をクリックして選択肢を表示する",
                () -> registerPage.input(ProjectFormPage.CONTRACT_TYPE).click(),
                registerPage.input(ProjectFormPage.CONTRACT_TYPE));
        verify.selectOptions("3. 「契約種別」の選択肢が「」(空欄)・「請負」・「準委任」・「派遣」である",
                registerPage.input(ProjectFormPage.CONTRACT_TYPE), List.of("", "請負", "準委任", "派遣"));

        verify.op("4. 選択肢「準委任」をクリックする",
                () -> registerPage.select(ProjectFormPage.CONTRACT_TYPE, "QUASI_MANDATE"),
                registerPage.input(ProjectFormPage.CONTRACT_TYPE));
        verify.value("4. 「契約種別」の表示が「準委任」に変わる", registerPage.input(ProjectFormPage.CONTRACT_TYPE),
                "QUASI_MANDATE");

        pressRegister(5);
        movedToEdit();
        verify.value("「契約種別」に区分値 QUASI_MANDATE の表示名「準委任」が表示される",
                editPage.input(ProjectFormPage.CONTRACT_TYPE), "QUASI_MANDATE");
    }

    @Test
    @DisplayName("No.1-6 カレンダーで年・月・日を数値として入力できる")
    void case1x6() {
        signInAsGeneralUser();
        openRegister(1);
        fillSubject(2, "カレンダー確認");
        verify.op("3. 「開始日」に 2026/09/01 を入力する",
                () -> registerPage.fill(ProjectFormPage.START_DATE, "2026-09-01"),
                registerPage.input(ProjectFormPage.START_DATE));
        verify.value("3. 「開始日」に 2026/09/01 が表示される", registerPage.input(ProjectFormPage.START_DATE),
                "2026-09-01");
        pressRegister(4);

        movedToEdit();
        verify.value("「開始日」に 2026/09/01 が表示される", editPage.input(ProjectFormPage.START_DATE),
                "2026-09-01");
        verify.absent("「開始日」欄の直下に「無効な日付です。」は表示されない", editPage.fieldLabel("開始日"),
                editPage.fieldError(ProjectFormPage.START_DATE));
    }

    @Test
    @DisplayName("No.1-7 入力値チェックのエラーメッセージが該当する画面項目の直下に赤文字で表示される")
    void case1x7() {
        signInAsGeneralUser();
        openRegister(1);
        pressRegister(2);

        verify.urlIs("画面遷移せず案件情報登録画面が再表示される", registerPage.url());
        verify.text("「件名」の入力欄の直下に「この項目は入力が必要です。」が赤文字で表示される",
                registerPage.fieldError(ProjectFormPage.SUBJECT), REQUIRED);
        verify.countIs("「取引先」「商流」「案件概要」など他の画面項目の直下にはエラーメッセージが表示されない",
                registerPage.visibleFieldErrors(), 1);

        verify.opOpen("【機械検証】案件情報は登録されていない（/projects を開いて取得データ件数を確認する）", () -> listPage.open());
        verify.machineEquals("【機械検証】/projects の取得データ件数が 0件である", "0", listPage.dataCountText());
    }

    @Test
    @DisplayName("No.1-8 エラーメッセージはフォームを再提出するまで表示され、再提出後は表示されない")
    void case1x8() {
        signInAsGeneralUser();
        openRegister(1);
        pressRegister(1);
        verify.text("2. 「件名」欄の直下に「この項目は入力が必要です。」が表示される",
                registerPage.fieldError(ProjectFormPage.SUBJECT), REQUIRED);

        verify.op("3. 「件名」に「再提出確認」を入力する", () -> registerPage.fill(ProjectFormPage.SUBJECT, "再提出確認"),
                registerPage.input(ProjectFormPage.SUBJECT));
        verify.text("3. 「件名」に値を入力してもエラーメッセージは表示されたままである",
                registerPage.fieldError(ProjectFormPage.SUBJECT), REQUIRED);

        verify.transitionClearedByPress("「件名」欄の直下にエラーメッセージが表示されている",
                registerPage.fieldError(ProjectFormPage.SUBJECT), "4. 「登録」を押下する",
                registerPage.registerButton(), () -> registerPage.pressRegister(), editPage.form(),
                "4. 「件名」欄の直下のエラーメッセージが消えている");
        movedToEdit();
    }

    @Test
    @DisplayName("No.1-9 エラーメッセージはページを再度読み込むと表示されなくなる")
    void case1x9() {
        signInAsGeneralUser();
        openRegister(1);
        pressRegister(1);
        verify.text("2. 「件名」欄の直下に「この項目は入力が必要です。」が表示される",
                registerPage.fieldError(ProjectFormPage.SUBJECT), REQUIRED);

        verify.transitionCleared("「件名」欄の直下にエラーメッセージが表示されている",
                registerPage.fieldError(ProjectFormPage.SUBJECT),
                "3. ブラウザの再読み込みで /projects/new を開き直す", () -> registerPage.open(),
                registerPage.form(), "3. 「件名」欄の直下のエラーメッセージが消えている" + "（【機械検証】エラーメッセージの要素が 0件である）");
    }

    // ---- 2. オートコンプリート ----

    @Test
    @DisplayName("No.2-1 フィールドをクリックすると選択肢が表示される（会社名が会社IDの昇順で並ぶ）")
    void case2x1() {
        signInAsGeneralUser();
        openRegister(1);
        verify.op("2. 「取引先」の入力欄をクリックする", () -> registerPage.input(ProjectFormPage.CLIENT).click(),
                registerPage.autocompleteMenu());

        verify.visible("「取引先」の入力欄の下に選択肢の一覧が表示される", registerPage.autocompleteMenu());
        verify.listItemsByText("【機械検証】選択肢の全件が会社名を会社IDの昇順で並べたものと一致する" + "（自由記入用の空欄は選択肢として表示されない）",
                registerPage.autocompleteMenu(), registerPage.autocompleteItems(),
                List.of("株式会社アルファ", "株式会社ブラボー", "株式会社チャーリー", "株式会社デルタ", "株式会社エコー", "株式会社フォックス",
                        "株式会社ゴルフ", "株式会社ホテル", "株式会社インディア", "株式会社ジュリエット", "株式会社キロ", "株式会社リマ"));
    }

    @Test
    @DisplayName("No.2-2 一度に表示される選択肢は8件で、9件以上あるときは選択肢内で縦にスクロールする")
    void case2x2() {
        signInAsGeneralUser();
        openRegister(1);
        verify.op("2. 「取引先」の入力欄をクリックして選択肢を表示する",
                () -> registerPage.input(ProjectFormPage.CLIENT).click(),
                registerPage.autocompleteMenu());

        verify.listVisibleCount("2. 選択肢の表示領域に8件ぶんが収まって表示され、9件目以降は領域内に現れない",
                registerPage.autocompleteMenu(), registerPage.autocompleteItems(), 8);
        verify.listItemsByText(
                "3. 選択肢の一覧が内部で縦にスクロールし、末尾の「株式会社リマ」まで到達できる" + "（【機械検証】末尾の選択肢の次に選択肢が存在しない）",
                registerPage.autocompleteMenu(), registerPage.autocompleteItems(),
                List.of("株式会社アルファ", "株式会社ブラボー", "株式会社チャーリー", "株式会社デルタ", "株式会社エコー", "株式会社フォックス",
                        "株式会社ゴルフ", "株式会社ホテル", "株式会社インディア", "株式会社ジュリエット", "株式会社キロ", "株式会社リマ"));
    }

    @Test
    @DisplayName("No.2-3 文字を入力すると、入力文字列を一連の部分文字列として含む選択肢のみに絞り込まれる")
    void case2x3() {
        signInAsGeneralUser();
        openRegister(1);
        verify.op("2. 「取引先」の入力欄をクリックして選択肢を表示する",
                () -> registerPage.input(ProjectFormPage.CLIENT).click(),
                registerPage.autocompleteMenu());
        verify.countIs("2. 複数の会社名が並んでいる", registerPage.autocompleteItems(), 12);

        verify.op("3. 「取引先」に「リエ」と入力する", () -> registerPage.input(ProjectFormPage.CLIENT).type("リエ"),
                registerPage.autocompleteMenu());
        verify.texts("3. 選択肢は「株式会社ジュリエット」の1件のみに絞り込まれる" + "（「リエ」を連続した部分文字列として含まない会社名は表示されない）",
                registerPage.autocompleteItems(), new String[] {"株式会社ジュリエット"});
    }

    @Test
    @DisplayName("No.2-4 入力文字列を一連の部分文字列として含む選択肢がない場合、選択肢を表示しない")
    void case2x4() {
        signInAsGeneralUser();
        openRegister(1);
        verify.op("2. 「取引先」の入力欄をクリックして選択肢を表示する",
                () -> registerPage.input(ProjectFormPage.CLIENT).click(),
                registerPage.autocompleteMenu());
        verify.countIs("2. 選択肢が並んでいる", registerPage.autocompleteItems(), 12);

        verify.transitionHide("選択肢の一覧が表示されている", registerPage.autocompleteMenu(),
                "3. 「取引先」に「ジュエ」と入力する", () -> registerPage.input(ProjectFormPage.CLIENT).type("ジュエ"),
                registerPage.input(ProjectFormPage.CLIENT),
                "3. 同じ画角で選択肢の一覧が消える（【機械検証】表示されている選択肢が 0件である）");
    }

    @Test
    @DisplayName("No.2-5 選択肢をクリックするとフィールドに選択肢の文字列が上書き入力される")
    void case2x5() {
        signInAsGeneralUser();
        openRegister(1);
        verify.op("2. 「取引先」に「デルタ」と入力する", () -> {
            registerPage.input(ProjectFormPage.CLIENT).click();
            registerPage.input(ProjectFormPage.CLIENT).type("デルタ");
        }, registerPage.autocompleteMenu());

        verify.transitionHide("絞り込まれた選択肢の一覧が表示されている", registerPage.autocompleteMenu(),
                "3. 選択肢「株式会社デルタ」をクリックする", () -> registerPage.autocompleteItems().first().click(),
                registerPage.input(ProjectFormPage.CLIENT), "3. 選択肢の一覧は表示されなくなる");
        verify.value("3. 「取引先」の値が「デルタ」から「株式会社デルタ」に上書きされる",
                registerPage.input(ProjectFormPage.CLIENT), "株式会社デルタ");
    }

    @Test
    @DisplayName("No.2-6 オートコンプリートのトリム（前後の半角・全角スペースを除去し、内部のスペースは保持する）")
    void case2x6() {
        signInAsGeneralUser();
        openRegister(1);
        fillSubject(2, "取引先トリム確認");
        verify.op("3. 「取引先」に「␣　A␣　B␣　」を入力する",
                () -> registerPage.fill(ProjectFormPage.CLIENT, TRIM_INPUT),
                registerPage.input(ProjectFormPage.CLIENT));
        pressRegister(4);

        movedToEdit();
        verify.value("「取引先」に「A␣　B」が表示される（前後のスペースが除去され、内部のスペースは保持される）",
                editPage.input(ProjectFormPage.CLIENT), TRIMMED);
    }

    @Test
    @DisplayName("No.2-7 部品の下方向に十分な領域が無い場合、選択肢は部品の上方向に表示される")
    void case2x7() {
        signInAsGeneralUser();
        openRegister(1);
        verify.op("2. 「取引先」の入力欄をクリックして選択肢を表示する",
                () -> registerPage.input(ProjectFormPage.CLIENT).click(),
                registerPage.autocompleteMenu());
        verify.positionedAbove("2. 選択肢の一覧が「取引先」の入力欄の下に表示される",
                registerPage.input(ProjectFormPage.CLIENT), registerPage.autocompleteMenu());

        verify.op("3. Escape キーを押下して選択肢を閉じる",
                () -> registerPage.input(ProjectFormPage.CLIENT).press("Escape"),
                registerPage.input(ProjectFormPage.CLIENT));
        verify.opInsertSpacerAbove("4. 「取引先」の入力欄の上に高さ600pxの余白要素を挿入する",
                registerPage.input(ProjectFormPage.CLIENT), 600);
        verify.op("5. 「取引先」の入力欄が画面の下端付近に来るまでメインビューポートを縦にスクロールする",
                () -> registerPage.scrollFieldToViewportBottom(ProjectFormPage.CLIENT),
                registerPage.input(ProjectFormPage.CLIENT));
        verify.op("6. 「取引先」の入力欄をクリックして選択肢を表示する", () -> {
            // 手順5のフレーム取得で対象が画角の中央へ寄せ直されるため、選択肢を開く直前に
            // 手順5と同じ下端寄せのスクロールを再適用してから押下する。
            registerPage.scrollFieldToViewportBottom(ProjectFormPage.CLIENT);
            registerPage.input(ProjectFormPage.CLIENT).click();
        }, registerPage.autocompleteMenu());

        verify.positionedAbove("6. 選択肢の一覧が「取引先」の入力欄の上に表示され、画面内に収まって見切れない",
                registerPage.autocompleteMenu(), registerPage.input(ProjectFormPage.CLIENT));
    }

    // ---- 3. ページネーション ----

    @Test
    @DisplayName("No.3-1 ページネーションの構成部品（«・‹・ページ番号・›・»）が表示される")
    void case3x1() {
        replaceCompanies(unitData(SEED_PAGINATION));
        signInAsAdmin();
        openCompanyMasterWithTenPerPage(1, 2);

        verify.listItemsByText("ページネーションに「«」「‹」「1」「2」「3」「4」「›」「»」のボタンが左から順に表示される",
                companyPage.pagerNums(CompanyMasterPage.PAGER_TOP),
                companyPage.pagerNums(CompanyMasterPage.PAGER_TOP).locator(".pg-btn"),
                List.of("«", "‹", "1", "2", "3", "4", "›", "»"));
        verify.text("取得データ件数に「取得データ件数: 80件」が表示される",
                companyPage.dataCountLabel(CompanyMasterPage.PAGER_TOP), "取得データ件数: 80件");
    }

    @Test
    @DisplayName("No.3-2 表示中のページが先頭の場合、先頭ページボタン «・前ページボタン ‹ が非活性になる")
    void case3x2() {
        replaceCompanies(unitData(SEED_PAGINATION));
        signInAsAdmin();
        openCompanyMasterWithTenPerPage(1, 2);
        verify.gridRowsByKey("2. 1ページ目（会社ID: 1〜10）が表示される", companyPage.grid(),
                CompanyMasterPage.COL_ID, companyKeys(1, 10));

        verify.disabled("【機械検証】「«」に disabled 属性が付与されている（グレー表示で押下できない）",
                companyPage.pagerButton(CompanyMasterPage.PAGER_TOP, "«"));
        verify.disabled("【機械検証】「‹」に disabled 属性が付与されている（グレー表示で押下できない）",
                companyPage.pagerButton(CompanyMasterPage.PAGER_TOP, "‹"));
        verify.op("3. 「«」を押下する",
                () -> companyPage.pagerButton(CompanyMasterPage.PAGER_TOP, "«")
                        .click(new Locator.ClickOptions().setForce(true)),
                companyPage.grid().root());
        verify.op("4. 「‹」を押下する",
                () -> companyPage.pagerButton(CompanyMasterPage.PAGER_TOP, "‹")
                        .click(new Locator.ClickOptions().setForce(true)),
                companyPage.grid().root());
        verify.gridRowsByKey("手順3・手順4を行ってもテーブルの表示は変わらず、会社ID: 1〜10 のままである", companyPage.grid(),
                CompanyMasterPage.COL_ID, companyKeys(1, 10));
    }

    @Test
    @DisplayName("No.3-3 表示中のページが末尾の場合、次ページボタン ›・末尾ページボタン » が非活性になる")
    void case3x3() {
        replaceCompanies(unitData(SEED_PAGINATION));
        signInAsAdmin();
        openCompanyMasterWithTenPerPage(1, 2);

        pressPager("3. 「»」を押下して8ページ目（末尾）を表示する", "»");
        verify.gridRowsByKey("3. 8ページ目（会社ID: 71〜80）が表示される", companyPage.grid(70),
                CompanyMasterPage.COL_ID, companyKeys(71, 80));
        verify.disabled("【機械検証】「›」に disabled 属性が付与されている（グレー表示で押下できない）",
                companyPage.pagerButton(CompanyMasterPage.PAGER_TOP, "›"));
        verify.disabled("【機械検証】「»」に disabled 属性が付与されている（グレー表示で押下できない）",
                companyPage.pagerButton(CompanyMasterPage.PAGER_TOP, "»"));

        verify.op("4. 「›」を押下する",
                () -> companyPage.pagerButton(CompanyMasterPage.PAGER_TOP, "›")
                        .click(new Locator.ClickOptions().setForce(true)),
                companyPage.grid().root());
        verify.gridRowsByKey("手順4を行ってもテーブルの表示は変わらず、会社ID: 71〜80 のままである", companyPage.grid(70),
                CompanyMasterPage.COL_ID, companyKeys(71, 80));
    }

    @Test
    @DisplayName("No.3-4 次ページボタン ›・前ページボタン ‹ で、画面遷移を伴わずにページが切り替わる")
    void case3x4() {
        replaceCompanies(unitData(SEED_PAGINATION));
        signInAsAdmin();
        openCompanyMasterWithTenPerPage(1, 2);
        verify.gridRowsByKey("2. 1ページ目（会社ID: 1〜10）が表示される", companyPage.grid(),
                CompanyMasterPage.COL_ID, companyKeys(1, 10));

        pressPager("3. 「›」を押下する", "›");
        verify.urlIs("3. URL は /master/companies のまま変わらない", companyPage.url());
        verify.gridRowsByKey("3. テーブルに会社ID: 11〜20 の10件が表示される", companyPage.grid(10),
                CompanyMasterPage.COL_ID, companyKeys(11, 20));

        pressPager("4. 「‹」を押下する", "‹");
        verify.gridRowsByKey("4. テーブルに会社ID: 1〜10 の10件が表示される", companyPage.grid(),
                CompanyMasterPage.COL_ID, companyKeys(1, 10));
    }

    @Test
    @DisplayName("No.3-5 末尾ページボタン »・先頭ページボタン « でページが切り替わる")
    void case3x5() {
        replaceCompanies(unitData(SEED_PAGINATION));
        signInAsAdmin();
        openCompanyMasterWithTenPerPage(1, 2);
        verify.gridRowsByKey("2. 1ページ目（会社ID: 1〜10）が表示される", companyPage.grid(),
                CompanyMasterPage.COL_ID, companyKeys(1, 10));

        pressPager("3. 「»」を押下する", "»");
        verify.gridRowsByKey("3. テーブルに会社ID: 71〜80 の10件（8ページ目）が表示される", companyPage.grid(70),
                CompanyMasterPage.COL_ID, companyKeys(71, 80));

        pressPager("4. 「«」を押下する", "«");
        verify.gridRowsByKey("4. テーブルに会社ID: 1〜10 の10件（1ページ目）が表示される", companyPage.grid(),
                CompanyMasterPage.COL_ID, companyKeys(1, 10));
    }

    @Test
    @DisplayName("No.3-6 表示中のページ番号ボタンは強調表示され、押下しても表示中のページは変化しない")
    void case3x6() {
        replaceCompanies(unitData(SEED_PAGINATION));
        signInAsAdmin();
        openCompanyMasterWithTenPerPage(1, 2);

        pressPager("3. ページ番号ボタン「3」を押下する", "3");
        verify.gridRowsByKey("3. テーブルに会社ID: 21〜30 の10件が表示される", companyPage.grid(20),
                CompanyMasterPage.COL_ID, companyKeys(21, 30));
        verify.text("3. ページ番号ボタン「3」が他のページ番号ボタンと異なる意匠で強調表示される",
                companyPage.activePagerButton(CompanyMasterPage.PAGER_TOP), "3");

        pressPager("4. もう一度ページ番号ボタン「3」を押下する", "3");
        verify.gridRowsByKey("4. テーブルの表示は会社ID: 21〜30 のまま変わらない", companyPage.grid(20),
                CompanyMasterPage.COL_ID, companyKeys(21, 30));
        verify.text("4. 「3」の強調表示も維持される", companyPage.activePagerButton(CompanyMasterPage.PAGER_TOP),
                "3");
    }

    @Test
    @DisplayName("No.3-7 ページ番号ボタンは表示中のページとその前後最大3件が配置される")
    void case3x7() {
        replaceCompanies(unitData(SEED_PAGINATION));
        signInAsAdmin();
        openCompanyMasterWithTenPerPage(1, 2);
        verify.text("2. 取得データ件数80件・表示件数10件のため全8ページになる",
                companyPage.dataCountLabel(CompanyMasterPage.PAGER_TOP), "取得データ件数: 80件");

        pressPager("3. ページ番号ボタン「4」を押下する", "4");
        pressPager("4. ページ番号ボタン「5」を押下する", "5");

        verify.listItemsByText("4. ページ番号ボタンは「2」〜「8」の7件が表示される（ページ番号「1」は表示されない）",
                companyPage.pagerNums(CompanyMasterPage.PAGER_TOP),
                companyPage.pagerNums(CompanyMasterPage.PAGER_TOP).locator(".pg-btn"),
                List.of("«", "‹", "2", "3", "4", "5", "6", "7", "8", "›", "»"));
        verify.text("4. 「5」が強調表示される", companyPage.activePagerButton(CompanyMasterPage.PAGER_TOP),
                "5");
    }

    // ---- 4. ファイルピッカー ----

    @Test
    @DisplayName("No.4-1 ファイル選択ダイアログで指定したファイルが「{ファイル名}({ファイルサイズ})」形式で表示される")
    void case4x1() {
        signInAsGeneralUser();
        openRegister(1);
        chooseAttachment("2. 「添付」のファイルピッカーで size-1023.bin を指定する",
                E2eTestFiles.ofSize("size-1023.bin", 1023));
        verify.containsText("「添付」のファイルピッカーに「size-1023.bin(1023B)」が表示される",
                registerPage.attachmentDropMain(), "size-1023.bin(1023B)");
    }

    @Test
    @DisplayName("No.4-2 ドラッグ&ドロップでファイルを指定できる")
    void case4x2() {
        signInAsGeneralUser();
        openRegister(1);
        verify.opDropFile("2. size-1024.bin を「添付」のファイルピッカーの上へドラッグし、ドロップする",
                registerPage.attachmentDropArea(), E2eTestFiles.ofSize("size-1024.bin", 1024),
                registerPage.attachmentDropArea());
        verify.containsText("「添付」のファイルピッカーに「size-1024.bin(1KB)」が表示される",
                registerPage.attachmentDropMain(), "size-1024.bin(1KB)");
    }

    @Test
    @DisplayName("No.4-3 「選択解除」を押下するとファイルの指定が解除される")
    void case4x3() {
        signInAsGeneralUser();
        openRegister(1);
        chooseAttachment("2. 「添付」のファイルピッカーで size-1023.bin を指定する",
                E2eTestFiles.ofSize("size-1023.bin", 1023));
        verify.containsText("3. 「添付」に「size-1023.bin(1023B)」が表示される",
                registerPage.attachmentDropMain(), "size-1023.bin(1023B)");
        verify.visible("3. 「選択解除」のリンクが表示される", registerPage.attachmentClearLink());

        verify.transitionClearedByPress("「選択解除」のリンクが表示されている", registerPage.attachmentClearLink(),
                "4. 「選択解除」のリンクを押下する", registerPage.attachmentClearLink(),
                () -> registerPage.attachmentClearLink().click(), registerPage.attachmentDropArea(),
                "4. ファイル名の表示と「選択解除」のリンクが消える");
        verify.text("4. ファイルが指定されていない状態の表示に戻る", registerPage.attachmentDropMain(),
                "クリックでファイル選択 ／ ドラッグ＆ドロップ");
    }

    @Test
    @DisplayName("No.4-4 別のファイルを指定すると既存の指定が解除され、新しく指定したファイルに置き換わる")
    void case4x4() {
        signInAsGeneralUser();
        openRegister(1);
        chooseAttachment("2. 「添付」のファイルピッカーで size-1023.bin を指定する",
                E2eTestFiles.ofSize("size-1023.bin", 1023));
        verify.containsText("2. 「添付」に「size-1023.bin(1023B)」が表示される",
                registerPage.attachmentDropMain(), "size-1023.bin(1023B)");

        chooseAttachment("3. 「添付」のファイルピッカーで size-1024.bin を指定する",
                E2eTestFiles.ofSize("size-1024.bin", 1024));
        verify.containsText("3. 「添付」の表示が「size-1024.bin(1KB)」になる", registerPage.attachmentDropMain(),
                "size-1024.bin(1KB)");
        verify.absent("3. 「size-1023.bin(1023B)」は表示されない（同時に指定できるファイルは1件のため置き換わる）",
                registerPage.attachmentDropArea(),
                page.locator("#attachmentDrop .drop-main:has-text('size-1023.bin')"));
    }

    @Test
    @DisplayName("No.4-5 【境界値】1023バイトのファイルサイズは「1023B」と表示される")
    void case4x5() {
        signInAsGeneralUser();
        openRegister(1);
        chooseAttachment("2. 「添付」のファイルピッカーで size-1023.bin を指定する",
                E2eTestFiles.ofSize("size-1023.bin", 1023));
        verify.containsText("「添付」に「size-1023.bin(1023B)」が表示される（1023バイト以下は単位 B）",
                registerPage.attachmentDropMain(), "size-1023.bin(1023B)");
    }

    @Test
    @DisplayName("No.4-6 【境界値】1024バイトのファイルサイズは「1KB」と表示される")
    void case4x6() {
        signInAsGeneralUser();
        openRegister(1);
        chooseAttachment("2. 「添付」のファイルピッカーで size-1024.bin を指定する",
                E2eTestFiles.ofSize("size-1024.bin", 1024));
        verify.containsText("「添付」に「size-1024.bin(1KB)」が表示される（小数部が0のため整数で表示する）",
                registerPage.attachmentDropMain(), "size-1024.bin(1KB)");
    }

    @Test
    @DisplayName("No.4-7 【境界値】1048575バイトのファイルサイズは「1024KB」と表示される")
    void case4x7() {
        signInAsGeneralUser();
        openRegister(1);
        chooseAttachment("2. 「添付」のファイルピッカーで size-1048575.bin を指定する",
                E2eTestFiles.ofSize("size-1048575.bin", 1048575));
        verify.containsText("「添付」に「size-1048575.bin(1024KB)」が表示される",
                registerPage.attachmentDropMain(), "size-1048575.bin(1024KB)");
    }

    @Test
    @DisplayName("No.4-8 【境界値】1048576バイトのファイルサイズは「1MB」と表示される")
    void case4x8() {
        signInAsGeneralUser();
        openRegister(1);
        chooseAttachment("2. 「添付」のファイルピッカーで size-1048576.bin を指定する",
                E2eTestFiles.ofSize("size-1048576.bin", 1048576));
        verify.containsText("「添付」に「size-1048576.bin(1MB)」が表示される（1048576バイト以上は単位 MB）",
                registerPage.attachmentDropMain(), "size-1048576.bin(1MB)");
    }

    @Test
    @DisplayName("No.4-9 ファイル形式を制限しないファイルピッカーでは、任意の形式のファイルを指定できる")
    void case4x9() {
        signInAsGeneralUser();
        openRegister(1);
        chooseAttachment("2. 「添付」のファイルピッカーで size-1023.bin（拡張子 .bin）を指定する",
                E2eTestFiles.ofSize("size-1023.bin", 1023));
        verify.containsText("2. 「添付」に「size-1023.bin(1023B)」が表示される",
                registerPage.attachmentDropMain(), "size-1023.bin(1023B)");
        verify.absent("2. ファイル形式に関するエラーメッセージは表示されない", registerPage.attachmentDropArea(),
                registerPage.attachmentErrorWhenShown());

        verify.op("3. 「選択解除」を押下する", () -> registerPage.attachmentClearLink().click(),
                registerPage.attachmentDropArea());
        chooseAttachment("3. CSV様式_アップロード_正常.csv（拡張子 .csv）を指定する", unitData(CSV_UPLOAD_OK));
        verify.containsText("3. 「添付」に「CSV様式_アップロード_正常.csv(112B)」が表示される",
                registerPage.attachmentDropMain(), "CSV様式_アップロード_正常.csv(112B)");
        verify.absent("3. ファイル形式に関するエラーメッセージは表示されない", registerPage.attachmentDropArea(),
                registerPage.attachmentErrorWhenShown());
    }

    // ---- 5. 共通レイアウト ----

    @Test
    @DisplayName("No.5-1 共通レイアウトを使用する画面は、共通ヘッダーとメインビューポートで構成される")
    void case5x1() {
        signInAsGeneralUser();
        verify.opOpen("1. /projects（案件情報一覧画面）を開く", () -> listPage.open());

        verify.visible("画面の上部に共通ヘッダーが表示される", commonHeader.header());
        verify.visible("その下のメインビューポートに案件情報一覧画面の内容が表示される", commonHeader.mainViewport());
        verify.containsText("共通ヘッダーにログインユーザのユーザ名「営業 太郎」が表示される", commonHeader.userName(), "営業 太郎");
        verify.containsText("共通ヘッダーに部署名「営業部」が表示される", commonHeader.departmentName(), "営業部");
    }

    @Test
    @DisplayName("No.5-2 ログイン画面では共通レイアウトを使用しない")
    void case5x2() {
        verify.opOpen("1. / を開く", () -> loginPage.open());

        verify.visible("ログイン画面が単独の画面として表示される", loginPage.form());
        verify.absent("共通ヘッダー（システムタイトル・ナビゲーション・ユーザ名／部署名・ログアウト）は表示されない" + "（【機械検証】共通ヘッダーの要素が 0件である）",
                loginPage.form(), commonHeader.header());
    }

    @Test
    @DisplayName("No.5-3 エラー画面では共通レイアウトを使用しない")
    void case5x3() {
        signInAsGeneralUser();
        verify.opOpen("1. 存在しないURL /no-such-page を開く", () -> errorPage.openUrl("/no-such-page"));

        verify.text("エラータイトル「404 Not Found」が表示される", errorPage.errorTitle(), "404 Not Found");
        verify.text("エラー内容「ページが見つかりませんでした。」が表示される", errorPage.errorMessage(), "ページが見つかりませんでした。");
        verify.absent("共通ヘッダー（システムタイトル・ナビゲーション・ユーザ名／部署名・ログアウト）は表示されない" + "（【機械検証】共通ヘッダーの要素が 0件である）",
                errorPage.card(), commonHeader.header());
    }

    // ---- 6. 認証・認可 ----

    @Test
    @DisplayName("No.6-1 未認証で実在する画面のURLへアクセスするとログイン画面へ誘導される")
    void case6x1() {
        verify.opOpen("1. 未認証の状態で /projects/new を開く", () -> {
            registerPage.open();
            page.waitForURL(loginPage.url());
        });

        verify.visible("案件情報登録画面は表示されず、ログイン画面が表示される", loginPage.form());
        verify.visible("ユーザID欄が表示される", loginPage.userIdInput());
        verify.visible("パスワード欄が表示される", loginPage.passwordInput());
        verify.visible("「ログイン」ボタンが表示される", loginPage.loginButton());
        verify.absent("【機械検証】案件情報登録画面の識別要素（画面タイトル「案件情報登録画面」）が 0件である", loginPage.form(),
                commonHeader.screenTitleOf(ProjectRegisterPage.SCREEN_TITLE));
    }

    @Test
    @DisplayName("No.6-2 未認証で存在しないURLへアクセスするとエラー画面に【ページ未検出エラー】が表示される")
    void case6x2() {
        verify.opOpen("1. 未認証の状態で存在しないURL /no-such-page を開く",
                () -> errorPage.openUrl("/no-such-page"));

        verify.text("エラータイトル「404 Not Found」が表示される", errorPage.errorTitle(), "404 Not Found");
        verify.text("エラー内容「ページが見つかりませんでした。」が表示される", errorPage.errorMessage(), "ページが見つかりませんでした。");
        verify.absent("【機械検証】ログイン画面の識別要素（ユーザID欄・「ログイン」ボタン）が 0件である", errorPage.card(),
                loginPage.form());
    }

    @Test
    @DisplayName("No.6-3 静的リソース（CSS・JavaScript）は未認証でも取得できる")
    void case6x3() {
        PlainTextResourcePage rawText = new PlainTextResourcePage(page);
        E2eVerify.CapturedResponse css = verify.captureResponse("1. /css/app.css の応答を捕捉する", "GET",
                "/css/app.css", () -> verify.opOpen("1. 未認証の状態で /css/app.css を開く",
                        () -> page.navigate(baseUrl() + "/css/app.css")));
        verify.containsText("1. リソースの内容（app.css の画面テーマコメントを含むテキスト）がブラウザに表示される", rawText.content(),
                "営業情報管理システム — 画面テーマ");
        verify.responseStatusIs("【機械検証】/css/app.css のHTTP応答が 200 である", css, 200);
        verify.responseNotRedirected("【機械検証】ログイン画面へのリダイレクトが発生していない", css);

        E2eVerify.CapturedResponse js = verify.captureResponse("2. /js/autocomplete.js の応答を捕捉する",
                "GET", "/js/autocomplete.js",
                () -> verify.opOpen("2. 未認証の状態で /js/autocomplete.js を開く",
                        () -> page.navigate(baseUrl() + "/js/autocomplete.js")));
        verify.containsText("2. リソースの内容（autocomplete.js の共通部品コメントを含むテキスト）がブラウザに表示される",
                rawText.content(), "画面部品仕様「オートコンプリート」準拠の共通部品");
        verify.responseStatusIs("【機械検証】/js/autocomplete.js のHTTP応答が 200 である", js, 200);
        verify.responseNotRedirected("【機械検証】ログイン画面へのリダイレクトが発生していない", js);
    }

    @Test
    @DisplayName("No.6-4 共通ヘッダーのログアウトボタンでログイン状態が失効し、ログイン画面へ遷移する")
    void case6x4() {
        signInAsGeneralUser();
        verify.opOpen("1. /projects を開く", () -> listPage.open());
        verify.containsText("1. 共通ヘッダーにユーザ名「営業 太郎」が表示されている", commonHeader.userName(), "営業 太郎");

        verify.opPress("2. 共通ヘッダーの「ログアウト」ボタンを押下する", commonHeader.logoutButton(),
                () -> commonHeader.pressLogout(), loginPage.form());
        verify.visible("2. ログイン画面へ遷移する", loginPage.form());

        verify.opOpen("3. /projects を開く", () -> {
            page.navigate(baseUrl() + ProjectListPage.PATH);
            page.waitForURL(loginPage.url());
        });
        verify.visible("3. 案件情報一覧画面は表示されず、ログイン画面が表示される", loginPage.form());
        verify.absent("【機械検証】案件情報一覧画面の識別要素（画面タイトル「案件情報一覧画面」）が 0件である", loginPage.form(),
                commonHeader.screenTitleOf(ProjectListPage.SCREEN_TITLE));
    }

    @Test
    @DisplayName("No.6-5 セッション失効後の画面表示（GET）はログイン画面へ誘導され、ログイン成功で要求していた画面へ復帰する")
    void case6x5() {
        signInAsGeneralUser();
        verify.opOpen("1. /projects を開く", () -> listPage.open());

        verify.op("2. ブラウザが保持するセッションCookieを削除する（ログインの有効期限が切れた状態にする）",
                () -> listPage.clearSessionCookies());
        verify.machineEquals("【機械検証】手順2の前後で、ブラウザが保持するセッションCookieが削除されている", "0",
                String.valueOf(listPage.sessionCookieCount()));

        verify.opPress("3. 共通ヘッダーの「新規登録」ボタンを押下して /projects/new を要求する",
                commonHeader.nav(CommonHeaderPage.NAV_REGISTER), () -> {
                    commonHeader.clickNav(CommonHeaderPage.NAV_REGISTER);
                    page.waitForURL(loginPage.url());
                }, loginPage.form());
        verify.visible("3. 案件情報登録画面は表示されずログイン画面が表示される", loginPage.form());

        verify.opPressWithInput("4. SM0001 のユーザID・パスワードを入力し「ログイン」を押下する", () -> {
            loginPage.fillUserId(GENERAL_USER);
            loginPage.fillPassword(PASSWORD);
        }, loginPage.loginButton(), () -> {
            loginPage.loginButton().click();
            page.waitForURL(url -> url.contains(ProjectRegisterPage.PATH));
        }, registerPage.form());
        verify.urlIs("4. 要求していた案件情報登録画面 /projects/new へ遷移する", registerPage.url());
        verify.visible("4. 画面タイトル「案件情報登録画面」が表示される",
                commonHeader.screenTitleOf(ProjectRegisterPage.SCREEN_TITLE));
    }

    @Test
    @DisplayName("No.6-6 セッション失効後の更新操作（POST）は実行されず、ログイン成功で案件情報一覧画面へ遷移する")
    void case6x6() {
        signInAsGeneralUser();
        openRegister(1);
        fillSubject(1, "失効後登録");

        verify.op("2. ブラウザが保持するセッションCookieを削除する（ログインの有効期限が切れた状態にする）",
                () -> registerPage.clearSessionCookies());
        verify.machineEquals("手順2の後、ブラウザが保持するセッションCookieが削除されている", "0",
                String.valueOf(registerPage.sessionCookieCount()));

        verify.opPress("3. 「登録」を押下する", registerPage.registerButton(), () -> {
            registerPage.pressRegister();
            page.waitForURL(loginPage.url());
        }, loginPage.form());
        verify.visible("3. 案件情報更新画面へは遷移せずログイン画面が表示される", loginPage.form());

        verify.opPressWithInput("4. SM0001 のユーザID・パスワードを入力し「ログイン」を押下する", () -> {
            loginPage.fillUserId(GENERAL_USER);
            loginPage.fillPassword(PASSWORD);
        }, loginPage.loginButton(), () -> {
            loginPage.loginButton().click();
            page.waitForURL(baseUrl() + ProjectListPage.PATH);
        }, commonHeader.screenTitleOf(ProjectListPage.SCREEN_TITLE));
        verify.urlIs("4. 案件情報登録画面ではなく案件情報一覧画面 /projects へ遷移する", baseUrl() + ProjectListPage.PATH);
        verify.text("4. 「該当するデータがありません」が表示され、「失効後登録」の行は表示されない", listPage.noRowsOverlay(),
                "該当するデータがありません");
        verify.machineEquals("【機械検証】案件情報一覧画面の取得データ件数が 0件である", "0", listPage.dataCountText());
    }

    @Test
    @DisplayName("No.6-7 一般ユーザがマスタ管理の画面へアクセスするとエラー画面に【権限不足エラー】が表示される")
    void case6x7() {
        signInAsGeneralUser();
        verify.opOpen("1. /master/companies（会社マスタ管理画面）を開く",
                () -> errorPage.openUrl(CompanyMasterPage.PATH));

        verify.text("エラータイトル「403 Forbidden」が表示される", errorPage.errorTitle(), "403 Forbidden");
        verify.text("エラー内容「ページにアクセスできません。」が表示される", errorPage.errorMessage(), "ページにアクセスできません。");
        verify.absent("【機械検証】会社マスタ管理画面の識別要素（画面タイトル「会社マスタ管理画面」）が 0件である", errorPage.card(),
                commonHeader.screenTitleOf(CompanyMasterPage.SCREEN_TITLE));
    }

    @Test
    @DisplayName("No.6-8 システム管理者はマスタ管理の画面へアクセスできる")
    void case6x8() {
        signInAsAdmin();
        verify.opOpen("1. /master/companies（会社マスタ管理画面）を開く", () -> companyPage.open());
        verify.visible("1. 画面タイトル「会社マスタ管理画面」が表示される",
                commonHeader.screenTitleOf(CompanyMasterPage.SCREEN_TITLE));

        verify.opOpen("2. /master/users（ユーザマスタ管理画面）を開く", () -> userPage.open());
        verify.visible("2. 画面タイトル「ユーザマスタ管理画面」が表示される",
                commonHeader.screenTitleOf(UserMasterPage.SCREEN_TITLE));

        verify.opOpen("3. /master/departments（部署マスタ管理画面）を開く", () -> departmentPage.open());
        verify.visible("3. 画面タイトル「部署マスタ管理画面」が表示される",
                commonHeader.screenTitleOf(DepartmentMasterPage.SCREEN_TITLE));
    }

    @Test
    @DisplayName("No.6-9 未ログインで許可されない画面へアクセスし、ログイン成功後に復帰すると【権限不足エラー】が表示される")
    void case6x9() {
        verify.opOpen("1. 未認証の状態で /master/companies を開く", () -> {
            page.navigate(baseUrl() + CompanyMasterPage.PATH);
            page.waitForURL(loginPage.url());
        });
        verify.visible("1. エラー画面ではなくログイン画面が表示される（認可に先立ち認証が要求される）", loginPage.form());

        verify.opPressWithInput("2. 一般ユーザ SM0001 のユーザID・パスワードを入力し「ログイン」を押下する", () -> {
            loginPage.fillUserId(GENERAL_USER);
            loginPage.fillPassword(PASSWORD);
        }, loginPage.loginButton(), () -> {
            loginPage.loginButton().click();
            page.waitForLoadState();
        }, errorPage.card());
        verify.text("2. エラータイトル「403 Forbidden」が表示される", errorPage.errorTitle(), "403 Forbidden");
        verify.text("2. エラー内容「ページにアクセスできません。」が表示される", errorPage.errorMessage(), "ページにアクセスできません。");
    }

    @Test
    @DisplayName("No.6-10 CSRFトークンが一致しない更新操作は【権限不足エラー】となる")
    void case6x10() {
        signInAsGeneralUser();
        openRegister(1);
        fillSubject(1, "CSRF確認");

        verify.opBypassSetAttribute("2. フォーム内のCSRFトークンの hidden 入力の値を「invalid-token」に書き換える",
                registerPage.csrfTokenInput(), "value", "invalid-token");

        verify.opPress("3. 「登録」を押下する", registerPage.registerButton(),
                () -> registerPage.pressRegister(), errorPage.card());
        verify.text("エラータイトル「403 Forbidden」が表示される", errorPage.errorTitle(), "403 Forbidden");
        verify.text("エラー内容「ページにアクセスできません。」が表示される", errorPage.errorMessage(), "ページにアクセスできません。");

        verify.opOpen("【機械検証】案件情報は登録されていない（/projects を開いて取得データ件数を確認する）", () -> listPage.open());
        verify.machineEquals("【機械検証】/projects の取得データ件数が 0件である", "0", listPage.dataCountText());
    }

    @Test
    @DisplayName("No.6-11 【機械検証】ログイン維持の時間経過（最後のアクセスから30分）の有効設定値が30分である")
    void case6x11() {
        signInAsGeneralUser();
        verify.opOpen("1. /projects を開く", () -> listPage.open());

        verify.sessionTimeoutIs(servletContext, Duration.ofMinutes(30));
        verify.visible("案件情報一覧画面は表示されたままである",
                commonHeader.screenTitleOf(ProjectListPage.SCREEN_TITLE));
        verify.containsText("共通ヘッダーにユーザ名「営業 太郎」が表示されている", commonHeader.userName(), "営業 太郎");
    }

    // ---- 7. パーマリンク・遷移の保留 ----

    @Test
    @DisplayName("No.7-1 未ログインでドメインURL ( / ) にアクセスするとログイン画面が表示される")
    void case7x1() {
        verify.opOpen("1. 未認証の状態で / を開く", () -> loginPage.open());

        verify.urlIs("URL は / のままで、他の画面へリダイレクトされない", loginPage.url());
        verify.visible("ユーザID欄が表示される", loginPage.userIdInput());
        verify.visible("パスワード欄が表示される", loginPage.passwordInput());
        verify.visible("「ログイン」ボタンが表示される", loginPage.loginButton());
    }

    @Test
    @DisplayName("No.7-2 ログイン済みでドメインURL ( / ) にアクセスすると案件情報一覧画面へリダイレクトされる")
    void case7x2() {
        signInAsGeneralUser();
        verify.opOpen("1. / を開く", () -> {
            page.navigate(loginPage.url());
            page.waitForURL(baseUrl() + ProjectListPage.PATH);
        });

        verify.urlIs("/projects（案件情報一覧画面）へリダイレクトされる", baseUrl() + ProjectListPage.PATH);
        verify.visible("画面タイトル「案件情報一覧画面」が表示される",
                commonHeader.screenTitleOf(ProjectListPage.SCREEN_TITLE));
        verify.absent("【機械検証】ログイン画面の識別要素（ユーザID欄）が 0件である", commonHeader.header(),
                loginPage.userIdInput());
    }

    @Test
    @DisplayName("No.7-3 未ログインで実在する画面のURLへアクセスすると、ログイン成功後に保留していた画面へ遷移する")
    void case7x3() {
        verify.opOpen("1. 未認証の状態で /master/users を開く", () -> {
            page.navigate(baseUrl() + UserMasterPage.PATH);
            page.waitForURL(loginPage.url());
        });
        verify.visible("1. ログイン画面が表示される", loginPage.form());

        verify.opPressWithInput("2. システム管理者 SM0002 のユーザID・パスワードを入力し「ログイン」を押下する", () -> {
            loginPage.fillUserId(ADMIN_USER);
            loginPage.fillPassword(PASSWORD);
        }, loginPage.loginButton(), () -> {
            loginPage.loginButton().click();
            page.waitForURL(url -> url.contains(UserMasterPage.PATH));
        }, commonHeader.screenTitleOf(UserMasterPage.SCREEN_TITLE));
        verify.urlIs("2. 案件情報一覧画面ではなく保留していた /master/users へ遷移する", userPage.url());
        verify.visible("2. 画面タイトル「ユーザマスタ管理画面」が表示される",
                commonHeader.screenTitleOf(UserMasterPage.SCREEN_TITLE));
    }

    @Test
    @DisplayName("No.7-4 保留していた画面が無い場合と、ログアウトの処理URLへの要求は保留されず、ログイン成功で案件情報一覧画面へ遷移する")
    void case7x4() {
        signInAsGeneralUser();
        openRegister(1);

        verify.opPress("2. 共通ヘッダーの「ログアウト」を押下する", commonHeader.logoutButton(),
                () -> commonHeader.pressLogout(), loginPage.form());
        verify.visible("2. ログイン画面が表示される", loginPage.form());

        verify.opPressWithInput("3. 一般ユーザ SM0001 のユーザID・パスワードを入力し「ログイン」を押下する", () -> {
            loginPage.fillUserId(GENERAL_USER);
            loginPage.fillPassword(PASSWORD);
        }, loginPage.loginButton(), () -> {
            loginPage.loginButton().click();
            page.waitForURL(baseUrl() + ProjectListPage.PATH);
        }, commonHeader.screenTitleOf(ProjectListPage.SCREEN_TITLE));
        verify.urlIs("【機械検証】ログイン成功後の遷移先が /projects であり、/logout・/projects/new ではない",
                baseUrl() + ProjectListPage.PATH);
        verify.visible("3. 画面タイトル「案件情報一覧画面」が表示される",
                commonHeader.screenTitleOf(ProjectListPage.SCREEN_TITLE));
    }

    @Test
    @DisplayName("No.7-5 CSVダウンロードのURL（画面ではないURL）は遷移の保留対象にならない")
    void case7x5() {
        verify.opOpen("1. 未認証の状態で /master/companies/csv を要求する", () -> {
            page.navigate(baseUrl() + "/master/companies/csv");
            page.waitForURL(loginPage.url());
        });
        verify.visible("1. ログイン画面が表示される", loginPage.form());

        verify.noDownload("【機械検証】手順2の後にファイルのダウンロードが発生していない", () -> verify
                .opPressWithInput("2. システム管理者 SM0002 のユーザID・パスワードを入力し「ログイン」を押下する", () -> {
                    loginPage.fillUserId(ADMIN_USER);
                    loginPage.fillPassword(PASSWORD);
                }, loginPage.loginButton(), () -> {
                    loginPage.loginButton().click();
                    page.waitForURL(baseUrl() + ProjectListPage.PATH);
                }, commonHeader.screenTitleOf(ProjectListPage.SCREEN_TITLE)));
        verify.urlIs("2. CSVダウンロードは実施されず、案件情報一覧画面 /projects へ遷移する",
                baseUrl() + ProjectListPage.PATH);
    }

    @Test
    @DisplayName("No.7-6 各画面に固定のURL（パーマリンク）が割り当てられている")
    void case7x6() {
        signInAsAdmin();
        openRegister(1);
        fillSubject(1, "パーマリンク確認");
        pressRegister(1);

        verify.opOpen("2. /projects を開く", () -> listPage.open());
        verify.visible("2. 画面タイトル「案件情報一覧画面」が表示される",
                commonHeader.screenTitleOf(ProjectListPage.SCREEN_TITLE));

        verify.opOpen("3. /projects/new を開く", () -> registerPage.open());
        verify.visible("3. 画面タイトル「案件情報登録画面」が表示される",
                commonHeader.screenTitleOf(ProjectRegisterPage.SCREEN_TITLE));

        verify.opOpen("4. /projects/detail/1001 を開く", () -> editPage.open(FIRST_CODE));
        verify.visible("4. 画面タイトル「案件情報更新画面」が表示される",
                commonHeader.screenTitleOf(ProjectEditPage.SCREEN_TITLE));
        verify.value("4. 「件名」に「パーマリンク確認」が表示される", editPage.input(ProjectFormPage.SUBJECT),
                "パーマリンク確認");

        verify.opOpen("5. /master/companies を開く", () -> companyPage.open());
        verify.visible("5. 画面タイトル「会社マスタ管理画面」が表示される",
                commonHeader.screenTitleOf(CompanyMasterPage.SCREEN_TITLE));

        verify.opOpen("6. /master/users を開く", () -> userPage.open());
        verify.visible("6. 画面タイトル「ユーザマスタ管理画面」が表示される",
                commonHeader.screenTitleOf(UserMasterPage.SCREEN_TITLE));

        verify.opOpen("7. /master/departments を開く", () -> departmentPage.open());
        verify.visible("7. 画面タイトル「部署マスタ管理画面」が表示される",
                commonHeader.screenTitleOf(DepartmentMasterPage.SCREEN_TITLE));
    }

    @Test
    @DisplayName("No.7-7 エラー画面はパーマリンクの対象外であり、エラー発生時に表示される")
    void case7x7() {
        signInAsGeneralUser();
        verify.opOpen("1. /projects を開く", () -> listPage.open());
        verify.absent("【機械検証】手順1の画面にエラータイトル・エラー内容の要素が 0件である", listPage.grid().root(),
                errorPage.card());

        verify.opOpen("2. 存在しないURL /no-such-page を開く", () -> errorPage.openUrl("/no-such-page"));
        verify.text("2. エラータイトル「404 Not Found」が表示される", errorPage.errorTitle(), "404 Not Found");
        verify.text("2. エラー内容「ページが見つかりませんでした。」が表示される", errorPage.errorMessage(), "ページが見つかりませんでした。");
    }

    @Test
    @DisplayName("No.7-8 エラー画面のURLは未認証でも直接アクセスでき、対応するエラーの表示のみを行う（業務画面への到達手段にはならない）")
    void case7x8() {
        verify.opOpen("1. 未認証の状態でエラー画面のURL /error/403 を開く", () -> errorPage.openUrl("/error/403"));

        verify.urlIs("ログイン画面へは誘導されず、業務画面へも遷移せず、URL は /error/403 のままである",
                errorPage.url("/error/403"));
        verify.text("エラー画面のエラータイトル「403 Forbidden」が表示される", errorPage.errorTitle(), "403 Forbidden");
        verify.text("エラー内容「ページにアクセスできません。」が表示される", errorPage.errorMessage(), "ページにアクセスできません。");
        verify.absent("【機械検証】ログイン画面の識別要素（ユーザID欄・「ログイン」ボタン）が 0件である", errorPage.card(),
                loginPage.userIdInput().or(loginPage.loginButton()));
    }

    // ---- 8. API 共通仕様 ----

    @Test
    @DisplayName("No.8-1 APIが正常終了するとHTTP 200で取得結果が画面に反映される")
    void case8x1() {
        signInAsGeneralUser();
        openRegister(1);
        fillSubject(1, "API正常確認");
        pressRegister(1);

        verify.opOpen("2. /projects を開く", () -> listPage.open());
        verify.opPress("3. 「案件検索」アコーディオンを開く", listPage.searchAccordionHead(),
                () -> listPage.toggleSearchAccordion(), listPage.searchPanelBody());

        E2eVerify.CapturedResponse response =
                verify.captureResponse("案件検索APIの応答を捕捉する", "POST", ProjectListPage.SEARCH_API,
                        () -> verify.opPress("4. 検索条件を変更せずに「検索」を押下する", listPage.searchButton(),
                                () -> listPage.pressSearch(), listPage.grid().root()));
        verify.responseStatusIs("【機械検証】案件検索API POST /api/projects/search のHTTP応答が 200 である",
                response, 200);
        verify.gridRowsByKey("案件情報テーブルに ID: 1001 の行が表示される", listPage.grid(),
                ProjectListPage.COL_CODE, List.of(String.valueOf(FIRST_CODE)));
        verify.gridCellText("件名「API正常確認」の行が表示される", listPage.grid(), 0, ProjectListPage.COL_TITLE,
                "API正常確認");
        verify.text("取得データ件数に「取得データ件数: 1件」が表示される",
                listPage.dataCountLabel(ProjectListPage.PAGER_TOP), "取得データ件数: 1件");
    }

    @Test
    @DisplayName("No.8-2 入力チェック違反のAPI応答はHTTP 400で、違反した画面項目にエラーメッセージが表示される")
    void case8x2() {
        signInAsGeneralUser();
        verify.opOpen("1. /projects を開く", () -> listPage.open());
        verify.opPress("2. 「案件検索」アコーディオンを開く", listPage.searchAccordionHead(),
                () -> listPage.toggleSearchAccordion(), listPage.searchPanelBody());
        verify.opBypassRemoveAttribute("3. 「検索キーワード」入力欄の maxlength 属性を解除する",
                listPage.input(ProjectListPage.KEYWORD), "maxlength");
        verify.op("4. 「検索キーワード」に半角英字101文字を入力する",
                () -> listPage.fill(ProjectListPage.KEYWORD, "a".repeat(101)),
                listPage.input(ProjectListPage.KEYWORD));

        E2eVerify.CapturedResponse response =
                verify.captureResponse("案件検索APIの応答を捕捉する", "POST", ProjectListPage.SEARCH_API,
                        () -> verify.opPress("5. 「検索」を押下する", listPage.searchButton(),
                                () -> listPage.pressSearch(), listPage.searchPanelBody()));
        verify.responseStatusIs("【機械検証】案件検索API POST /api/projects/search のHTTP応答が 400 である",
                response, 400);
        verify.text("「検索キーワード」の入力欄の直下に「100文字以内で入力してください。」が表示される",
                listPage.fieldError(ProjectListPage.KEYWORD), "100文字以内で入力してください。");
        verify.countIs("他の検索条件の入力欄の直下にはエラーメッセージが表示されない", listPage.filledFieldErrors(), 1);
    }

    @Test
    @DisplayName("No.8-3 HTTP 400のエラーレスポンスは違反した全ての項目分を errors 配列で返す")
    void case8x3() {
        signInAsGeneralUser();
        verify.opOpen("1. /projects を開く", () -> listPage.open());
        verify.opPress("2. 「案件検索」アコーディオンを開く", listPage.searchAccordionHead(),
                () -> listPage.toggleSearchAccordion(), listPage.searchPanelBody());
        verify.opBypassRemoveAttribute("3. 「検索キーワード」入力欄の maxlength 属性を解除する",
                listPage.input(ProjectListPage.KEYWORD), "maxlength");
        verify.opBypassRemoveAttribute("3. 「登録者」入力欄の maxlength 属性を解除する",
                listPage.input(ProjectListPage.REGISTRANT), "maxlength");
        verify.op("4. 「検索キーワード」に半角英字101文字を入力する",
                () -> listPage.fill(ProjectListPage.KEYWORD, "a".repeat(101)),
                listPage.input(ProjectListPage.KEYWORD));
        verify.op("4. 「登録者」に半角英字51文字を入力する",
                () -> listPage.fill(ProjectListPage.REGISTRANT, "a".repeat(51)),
                listPage.input(ProjectListPage.REGISTRANT));

        E2eVerify.CapturedResponse response =
                verify.captureResponse("案件検索APIの応答を捕捉する", "POST", ProjectListPage.SEARCH_API,
                        () -> verify.opPress("5. 「検索」を押下する", listPage.searchButton(),
                                () -> listPage.pressSearch(), listPage.searchPanelBody()));
        verify.responseErrorsAre("【機械検証】errors 配列に要素が2件あり、field が keyword・registrant である", response,
                Map.of("keyword", "100文字以内で入力してください。", "registrant", "50文字以内で入力してください。"));
        verify.text("「検索キーワード」の入力欄の直下に「100文字以内で入力してください。」が表示される",
                listPage.fieldError(ProjectListPage.KEYWORD), "100文字以内で入力してください。");
        verify.text("「登録者」の入力欄の直下に「50文字以内で入力してください。」が同時に表示される",
                listPage.fieldError(ProjectListPage.REGISTRANT), "50文字以内で入力してください。");
    }

    @Test
    @DisplayName("No.8-4 未認証のAPI要求はHTTP 401（ボディなし）となり、クライアントはログイン画面へ遷移する")
    void case8x4() {
        signInAsGeneralUser();
        verify.opOpen("1. /projects を開く", () -> listPage.open());
        verify.opPress("1. 「案件検索」アコーディオンを開く", listPage.searchAccordionHead(),
                () -> listPage.toggleSearchAccordion(), listPage.searchPanelBody());

        verify.op("2. ブラウザが保持するセッションCookieを削除する（ログインの有効期限が切れた状態にする）",
                () -> listPage.clearSessionCookies());
        verify.machineEquals("手順2の後、ブラウザが保持するセッションCookieが削除されている", "0",
                String.valueOf(listPage.sessionCookieCount()));

        E2eVerify.CapturedResponse response =
                verify.captureResponse("案件検索APIの応答を捕捉する", "POST", ProjectListPage.SEARCH_API,
                        () -> verify.opPress("3. 「検索」を押下する", listPage.searchButton(), () -> {
                            listPage.pressSearch();
                            page.waitForURL(loginPage.url());
                        }, loginPage.form()));
        verify.responseStatusIs("【機械検証】案件検索API POST /api/projects/search のHTTP応答が 401 である",
                response, 401);
        verify.responseBodyEmpty("【機械検証】レスポンスボディが空である", response);
        verify.visible("3. ログイン画面へ遷移する", loginPage.form());

        verify.opPress("【機械検証】ログイン成功後の遷移先として /api/projects/search は保留されていない",
                loginPage.loginButton(),
                () -> loginPage.signIn(GENERAL_USER, PASSWORD, ProjectListPage.PATH),
                commonHeader.screenTitleOf(ProjectListPage.SCREEN_TITLE));
        verify.urlIs("ログイン成功後の遷移先が /projects である", baseUrl() + ProjectListPage.PATH);
    }

    @Test
    @DisplayName("No.8-5 CSRFトークンが一致しないAPI要求はHTTP 403（ボディなし）となり、エラー画面へ遷移する")
    void case8x5() {
        signInAsGeneralUser();
        openRegister(1);
        fillSubject(1, "API権限確認");
        chooseAttachment("1. 「添付」で size-1023.bin を指定する",
                E2eTestFiles.ofSize("size-1023.bin", 1023));
        pressRegister(1);

        verify.opOpen("2. /projects/detail/1001 を開く", () -> editPage.open(FIRST_CODE));
        verify.countIs("2. 添付ファイル一覧に「size-1023.bin」が1件表示されている", editPage.attachmentRows(), 1);

        verify.op("3. 「編集保護」のトグルをオフに切り替える（削除要求の到達手段）", () -> editPage.turnOffEditProtect(),
                editPage.editProtectCheckbox());
        verify.opBypassSetAttribute(
                "3. （開発者ツール相当の合成操作）手順3で送信するCSRFトークン（ページの meta[_csrf]）を「invalid-token」に改変する",
                commonHeader.csrfTokenMeta(), "content", "invalid-token");

        E2eVerify.CapturedResponse response = verify.captureResponse("添付ファイル削除APIの応答を捕捉する",
                "DELETE", "/api/attachments/" + FIRST_CODE,
                () -> verify.opPressConfirm(
                        "3. 「添付ファイル」の一覧の1行目をクリックし、削除確認ダイアログの「OK」ボタンを押下する"
                                + "（添付ファイル削除API DELETE /api/attachments/1001 を要求する）",
                        editPage.attachmentRows().first(), DELETE_CONFIRM, true, () -> {
                            editPage.clickAttachmentRow(1);
                            page.waitForURL(url -> url.contains("/error"));
                        }, errorPage.card()));
        verify.responseStatusIs("【機械検証】DELETE /api/attachments/1001 のHTTP応答が 403 である", response,
                403);
        verify.responseBodyEmpty("【機械検証】レスポンスボディが空である", response);

        verify.text("手順3の後、エラー画面へ遷移し、エラータイトル「403 Forbidden」が表示される", errorPage.errorTitle(),
                "403 Forbidden");
        verify.text("エラー内容「ページにアクセスできません。」が表示される", errorPage.errorMessage(), "ページにアクセスできません。");
        verify.urlIs("手順3の後の URL は /error/403 である（エラーの種別を保ったエラー画面URLへの遷移）",
                errorPage.url("/error/403"));

        verify.opOpen("4. /projects/detail/1001 を開く", () -> editPage.open(FIRST_CODE));
        verify.countIs("4. 添付ファイル一覧に「size-1023.bin」が1件表示され、削除されていない", editPage.attachmentRows(), 1);
    }

    @Test
    @DisplayName("No.8-6 存在しないAPIのURLへの要求はHTTP 404となり、エラー画面へ遷移する")
    void case8x6() {
        signInAsGeneralUser();

        E2eVerify.CapturedResponse response = verify.captureResponse("応答を捕捉する", "GET",
                "/api/no-such-api", () -> verify.opOpen("1. /api/no-such-api を開く",
                        () -> errorPage.openUrl("/api/no-such-api")));
        verify.responseStatusIs("【機械検証】/api/no-such-api のHTTP応答が 404 である", response, 404);
        verify.responseBodyExcludes("【機械検証】レスポンスボディにアプリケーション内部の詳細情報（スタックトレース・例外クラス名）が含まれない",
                response, "Exception", "at com.system_server", "java.lang");
        verify.text("エラータイトル「404 Not Found」が表示される", errorPage.errorTitle(), "404 Not Found");
        verify.text("エラー内容「ページが見つかりませんでした。」が表示される", errorPage.errorMessage(), "ページが見つかりませんでした。");
    }

    @Test
    @DisplayName("No.8-7 サーバー内部エラーのAPI応答はHTTP 500となり、内部の詳細情報を含まずエラー画面へ遷移する")
    void case8x7() {
        signInAsGeneralUser();
        verify.opOpen("1. /projects を開く", () -> listPage.open());
        verify.opArmFault("2. （障害シーム）「データベース例外（案件検索）」を有効化する",
                ProjectSearchFaultSeamConfig.DB_EXCEPTION_ON_SEARCH);
        verify.opPress("3. 「案件検索」アコーディオンを押下して検索フォームを開く", listPage.searchAccordionHead(),
                () -> listPage.toggleSearchAccordion(), listPage.searchPanelBody());

        E2eVerify.CapturedResponse response =
                verify.captureResponse("案件検索APIの応答を捕捉する", "POST", ProjectListPage.SEARCH_API,
                        () -> verify.opPress("3. 「検索」を押下する", listPage.searchButton(), () -> {
                            listPage.pressSearch();
                            page.waitForURL(url -> url.contains("/error"));
                        }, errorPage.card()));
        verify.responseStatusIs("【機械検証】案件検索APIのHTTP応答が 500 である", response, 500);
        verify.responseBodyExcludes("【機械検証】レスポンスボディにスタックトレース・例外クラス名が含まれない", response, "Exception",
                "at com.system_server", "java.lang");
        verify.urlIs("遷移後の URL は /error/500 である（エラーの種別を保ったエラー画面URLへの遷移）",
                errorPage.url("/error/500"));
        verify.text("エラータイトル「An Error Occurred」が表示される", errorPage.errorTitle(),
                "An Error Occurred");
        verify.text("エラー内容「エラーが発生しました。」が表示される", errorPage.errorMessage(), "エラーが発生しました。");
        verify.absent("画面にスタックトレース・例外クラス名などアプリケーション内部の詳細情報は表示されない", errorPage.card(),
                errorPage.internalDetails());
    }

    // ---- 9. CSV 様式 ----

    @Test
    @DisplayName("No.9-1 ダウンロードしたCSVはUTF-8 BOMあり・CR+LF・カンマ区切りで、囲み文字を要しない値は括られない")
    void case9x1() {
        signInAsAdmin();
        verify.opOpen("1. /master/companies を開く", () -> companyPage.open());

        verify.csvDownloaded(
                "2. ファイル「会社マスタ.csv」がダウンロードされ、" + "【機械検証】バイト列が CSV様式_期待_会社マスタDL.csv（353バイト）と一致する",
                "会社マスタ.csv", unitData(CSV_EXPECTED), () -> companyPage.pressCsvDownload());
    }

    @Test
    @DisplayName("No.9-2 囲み文字で括られた値が値として読み取られる（囲み文字の外のカンマのみが区切り文字）")
    void case9x2() {
        signInAsAdmin();
        verify.opOpen("1. /master/companies を開く", () -> companyPage.open());
        verify.gridRowsByKey("1. テーブルに12件が表示されている", companyPage.grid(), CompanyMasterPage.COL_ID,
                companyKeys(1, 12));

        verify.opChooseFileSubmits("2. 「CSVアップロード」を押下し、CSV様式_アップロード_正常.csv を指定する",
                companyPage.csvUploadButton(), unitData(CSV_UPLOAD_OK), companyPage.grid().root());

        verify.gridRowsByKey("洗い替えが実施され、会社マスタ管理画面に3件が一覧表示される", companyPage.grid(),
                CompanyMasterPage.COL_ID, companyKeys(1, 3));
        verify.gridCellText("会社ID: 1 の会社名が「株式会社アルファ」である", companyPage.grid(), 0,
                CompanyMasterPage.COL_NAME, "株式会社アルファ");
        verify.gridCellText("会社ID: 2 の会社名は囲み文字を含まない「株式会社ブラボー」として表示される", companyPage.grid(), 1,
                CompanyMasterPage.COL_NAME, "株式会社ブラボー");
        verify.gridCellText("会社ID: 3 の会社名が「株式会社チャーリー」である", companyPage.grid(), 2,
                CompanyMasterPage.COL_NAME, "株式会社チャーリー");
        verify.absent("エラー表示は表示されない", companyPage.csvUploadButton(), companyPage.errorArea());
    }

    @Test
    @DisplayName("No.9-3 ダウンロードしたCSVをそのままアップロードすると同一の内容に復元される（ラウンドトリップ）")
    void case9x3() {
        signInAsAdmin();
        verify.opOpen("1. /master/companies を開く", () -> companyPage.open());
        verify.gridRowsByKey("1. テーブルに12件が表示されている", companyPage.grid(), CompanyMasterPage.COL_ID,
                companyKeys(1, 12));

        final Path first = E2eTestFiles.downloadTarget("会社マスタ_round.csv");
        verify.csvDownloaded("2. 「CSVダウンロード」を押下して 会社マスタ.csv を取得する", "会社マスタ.csv",
                unitData(CSV_EXPECTED), () -> {
                    Download download = companyPage.pressCsvDownload();
                    download.saveAs(first);
                    return download;
                });

        verify.opChooseFileSubmits("3. 「CSVアップロード」を押下し、手順2で取得した 会社マスタ.csv を指定する",
                companyPage.csvUploadButton(), first, companyPage.grid().root());
        verify.gridRowsByKey("3. 会社マスタ管理画面に12件が一覧表示される", companyPage.grid(),
                CompanyMasterPage.COL_ID, companyKeys(1, 12));
        verify.gridCellText("3. 先頭行は 会社ID: 1「株式会社アルファ」である", companyPage.grid(), 0,
                CompanyMasterPage.COL_NAME, "株式会社アルファ");
        verify.gridCellText("3. 末尾行は 会社ID: 12「株式会社リマ」である", companyPage.grid(), 11,
                CompanyMasterPage.COL_NAME, "株式会社リマ");
        verify.absent("3. エラー表示は表示されない", companyPage.csvUploadButton(), companyPage.errorArea());

        verify.csvDownloaded("4. 【機械検証】手順4で取得したファイルのバイト列が手順2で取得したファイルと一致する", "会社マスタ.csv", first,
                () -> companyPage.pressCsvDownload());
    }

    @Test
    @DisplayName("No.9-4 閉じられていない囲み文字を含むCSVは既定の様式のCSVファイルとして読み取れない")
    void case9x4() {
        signInAsAdmin();
        verify.opOpen("1. /master/companies を開く", () -> companyPage.open());
        verify.gridRowsByKey("1. テーブルに12件が表示されている", companyPage.grid(), CompanyMasterPage.COL_ID,
                companyKeys(1, 12));

        verify.opChooseFileSubmits("2. 「CSVアップロード」を押下し、CSV様式_アップロード_囲み文字不正.csv を指定する",
                companyPage.csvUploadButton(), unitData(CSV_UPLOAD_NG), companyPage.errorArea());

        verify.text("エラータイトル「CSVアップロードに失敗しました」が表示される", companyPage.errorTitle(),
                "CSVアップロードに失敗しました");
        verify.text("エラー内容「CSVの読み取りに失敗しました。フォーマットが正しいかファイルを確認してください。」が表示される",
                companyPage.errorMessages(), "CSVの読み取りに失敗しました。フォーマットが正しいかファイルを確認してください。");
        verify.gridRowsByKey("テーブルには12件が表示されたままである", companyPage.grid(), CompanyMasterPage.COL_ID,
                companyKeys(1, 12));
        verify.gridCellText("先頭行は 会社ID: 1「株式会社アルファ」である", companyPage.grid(), 0,
                CompanyMasterPage.COL_NAME, "株式会社アルファ");
        verify.gridCellText("末尾行は 会社ID: 12「株式会社リマ」である", companyPage.grid(), 11,
                CompanyMasterPage.COL_NAME, "株式会社リマ");
    }

    // ---- 10. 営業情報メール ----

    @Test
    @DisplayName("No.10-1 案件情報の登録時に営業情報メールが送信され、タイトルとヘッダー文が登録時の内容になる")
    void case10x1() {
        signInAsGeneralUser();
        openRegister(1);
        fillSubject(2, "メール登録確認");
        verify.checked("3. 「メール送信」がチェックされている", registerPage.input(ProjectFormPage.SEND_MAIL));
        pressRegister(4);

        movedToEdit();
        verify.mailReceived(mailbox, "【営業情報】(新規) メール登録確認", null, "案件情報が新規登録されました");
    }

    @Test
    @DisplayName("No.10-2 案件情報の更新時に営業情報メールが送信され、タイトルとヘッダー文が更新時の内容になる")
    void case10x2() {
        signInAsGeneralUser();
        openRegister(1);
        fillSubject(1, "メール更新確認");
        verify.op("2. 「メール送信」のチェックを外す", () -> registerPage.uncheck(ProjectFormPage.SEND_MAIL),
                registerPage.input(ProjectFormPage.SEND_MAIL));
        pressRegister(2);

        verify.op("3. 「編集保護」のトグルをオフに切り替える", () -> editPage.turnOffEditProtect(),
                editPage.editProtectCheckbox());
        verify.op("4. 「メール送信」にチェックを入れる", () -> editPage.check(ProjectFormPage.SEND_MAIL),
                editPage.input(ProjectFormPage.SEND_MAIL));
        verify.opPress("5. 「編集確定」を押下する", editPage.submitButton(), () -> editPage.pressSubmit());

        verify.mailReceived(mailbox, "【営業情報】(更新) メール更新確認", null, "案件情報が更新されました");
    }

    @Test
    @DisplayName("No.10-3 メール本文がテンプレートの構成（更新画面URL・メール本文・区切り線・案件情報21項目）で出力される")
    void case10x3() {
        signInAsGeneralUser();
        openRegister(1);
        fillSubject(2, "テンプレート確認");
        verify.op("2. 「メール本文」に「本文テスト」を入力する",
                () -> registerPage.fill(ProjectFormPage.MAIL_BODY, "本文テスト"),
                registerPage.input(ProjectFormPage.MAIL_BODY));
        pressRegister(3);

        verify.mailReceived(mailbox, "【営業情報】(新規) テンプレート確認", null, "案件情報が新規登録されました");
        verify.mailBodyMatches("メール本文に更新画面URL（/projects/detail/1001）が出力される", mailbox,
                "(?s).*/projects/detail/1001.*");
        verify.mailBodyMatches("メール本文に入力した「本文テスト」が出力される", mailbox, "(?s).*本文テスト.*");
        verify.mailBodyMatches("メール本文に「● 案件情報」が出力される", mailbox, "(?s).*● 案件情報.*");
        verify.mailBodyMatches("【管理コード】に 1001 が出力される", mailbox, "(?s).*【管理コード】\\s*1001.*");
        verify.mailBodyMatches("【件名】に「テンプレート確認」が出力される", mailbox, "(?s).*【件名】\\s*テンプレート確認.*");
        verify.mailBodyItemNamesAre("【機械検証】21項目の項目名と並びがテンプレートと一致する", mailbox,
                List.of("管理コード", "件名", "取引先", "商流", "案件概要", "工程", "開始日", "終了日", "スキル", "人数", "残数",
                        "ＢＰ募集(要否)", "ＢＰ募集(詳細)", "作業場所", "見込単価", "契約種別", "ステータス", "備考", "登録日時",
                        "登録部署", "登録者"));
    }

    @Test
    @DisplayName("No.10-4 メール本文の共通出力様式（値のない項目は空欄・改行は改行・日付と日時の書式）")
    void case10x4() {
        signInAsGeneralUser();
        openRegister(1);
        fillSubject(2, "様式確認");
        verify.op("2. 「案件概要」に「1行目」＋改行＋「2行目」を入力する", () -> {
            registerPage.input(ProjectFormPage.OVERVIEW).click();
            registerPage.input(ProjectFormPage.OVERVIEW).type("1行目");
            registerPage.input(ProjectFormPage.OVERVIEW).press("Enter");
            registerPage.input(ProjectFormPage.OVERVIEW).type("2行目");
        }, registerPage.input(ProjectFormPage.OVERVIEW));
        verify.op("2. 「開始日」に 2026/09/01 を入力する",
                () -> registerPage.fill(ProjectFormPage.START_DATE, "2026-09-01"),
                registerPage.input(ProjectFormPage.START_DATE));
        verify.op("2. 「終了日」に 2026/09/30 を入力する",
                () -> registerPage.fill(ProjectFormPage.END_DATE, "2026-09-30"),
                registerPage.input(ProjectFormPage.END_DATE));
        verify.op("3. 「商流」「工程」「スキル」を空のままにする（何も入力しない）", () -> {
        }, registerPage.input(ProjectFormPage.DISTRIBUTION));
        pressRegister(4);

        verify.mailReceived(mailbox, "【営業情報】(新規) 様式確認", null, "案件情報が新規登録されました");
        verify.mailBodyLineShown("【商流】の値が空欄で表示される", "^【商流】 *$");
        verify.mailBodyLineShown("【工程】の値が空欄で表示される", "^【工程】 *$");
        verify.mailBodyLineShown("【スキル】の値が空欄で表示される", "^【スキル】 *$");
        verify.mailBodyLineShown("【案件概要】の値が「1行目」「2行目」の2行に改行されて表示される", "^【案件概要】 *1行目\\n2行目$");
        verify.mailBodyLineShown("【開始日】が「2026年09月01日」の形式で表示される", "^【開始日】 *2026年09月01日$");
        verify.mailBodyLineShown("【終了日】が「2026年09月30日」の形式で表示される", "^【終了日】 *2026年09月30日$");
        verify.mailBodyLineShown("【登録日時】が「YYYY年MM月DD日 hh時mm分ss秒」の形式で表示される",
                "^【登録日時】 *\\d{4}年\\d{2}月\\d{2}日 \\d{2}時\\d{2}分\\d{2}秒$");
    }

    @Test
    @DisplayName("No.10-5 案件情報に添付ファイルがある場合、添付ファイル注釈「(添付ファイルの登録あり)」が付く")
    void case10x5() {
        signInAsGeneralUser();
        openRegister(1);
        fillSubject(2, "添付あり確認");
        chooseAttachment("3. 「添付」のファイルピッカーで size-1023.bin を指定する",
                E2eTestFiles.ofSize("size-1023.bin", 1023));
        pressRegister(4);

        verify.mailReceived(mailbox, "【営業情報】(新規) 添付あり確認", null, "(添付ファイルの登録あり)");
        verify.mailBodyMatches("メール本文の更新画面URLの後ろに「(添付ファイルの登録あり)」が表示される", mailbox,
                "(?s).*/projects/detail/1001\\s*\\(添付ファイルの登録あり\\).*");
    }

    @Test
    @DisplayName("No.10-6 案件情報に添付ファイルがない場合、添付ファイル注釈は空欄になる")
    void case10x6() {
        signInAsGeneralUser();
        openRegister(1);
        fillSubject(2, "添付なし確認");
        pressRegister(3);

        verify.mailReceived(mailbox, "【営業情報】(新規) 添付なし確認", null, "案件情報が新規登録されました");
        verify.mailBodyNotContains("【機械検証】メール本文に文字列「(添付ファイルの登録あり)」が 0件である", mailbox,
                "(添付ファイルの登録あり)");
    }

    @Test
    @DisplayName("No.10-7 メールの送信元・宛先が案件情報を登録したユーザのメールアドレスになる（開発環境）")
    void case10x7() {
        signInAsGeneralUser();
        openRegister(1);
        fillSubject(2, "アドレス確認");
        pressRegister(3);

        verify.mailReceived(mailbox, "【営業情報】(新規) アドレス確認", null, "案件情報が新規登録されました");
        verify.mailAddressesAre(mailbox, "【営業情報】(新規) アドレス確認", "sm0001@example.com",
                "sm0001@example.com");
    }

    @Test
    @DisplayName("No.10-8 メール本文の「【ＢＰ募集(要否)】」「【ＢＰ募集(詳細)】」の「ＢＰ」が全角で出力される")
    void case10x8() {
        signInAsGeneralUser();
        openRegister(1);
        fillSubject(2, "全角ＢＰ確認");
        verify.op("2. 「BPの募集が必要です」をチェックする", () -> registerPage.check(ProjectFormPage.BP_REQUIRED),
                registerPage.input(ProjectFormPage.BP_REQUIRED));
        verify.op("2. 「詳細:」に「BP詳細テスト」を入力する",
                () -> registerPage.fill(ProjectFormPage.BP_DETAIL, "BP詳細テスト"),
                registerPage.input(ProjectFormPage.BP_DETAIL));
        pressRegister(3);

        verify.mailReceived(mailbox, "【営業情報】(新規) 全角ＢＰ確認", null, "【ＢＰ募集(要否)】");
        verify.mailBodyMatches("メール本文の項目名が「【ＢＰ募集(詳細)】」である（ＢＰが全角）", mailbox,
                "(?s).*【ＢＰ募集\\(詳細\\)】.*");
        verify.mailBodyNotContains("【機械検証】メール本文に「【BP募集(要否)】」（半角BP）が 0件である", mailbox, "【BP募集(要否)】");
    }
}
