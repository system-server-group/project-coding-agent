package testgen.e2e.tests;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import testgen.e2e.ProjectE2eTest;
import testgen.e2e.data.E2eTestFiles;
import testgen.e2e.pages.CommonHeaderPage;
import testgen.e2e.pages.ErrorPage;
import testgen.e2e.pages.LoginPage;
import testgen.e2e.pages.ProjectEditPage;
import testgen.e2e.pages.ProjectFormPage;
import testgen.e2e.pages.ProjectListPage;
import testgen.e2e.pages.ProjectRegisterPage;
import testgen.e2e.seams.ProjectRegisterFaultSeamConfig;
import testgen.e2e.support.E2eFeature;
import testgen.e2e.support.E2eMailServer;
import testgen.e2e.support.E2eMailbox;
import testgen.e2e.support.E2eSeedSupport;
import testgen.e2e.support.E2eSpecLayout;
import testgen.e2e.support.evidence.EvidenceV2;

/**
 * 案件情報登録画面（機能 {@code 12_案件情報登録}）の E2E テスト。
 *
 * <p>
 * テストケース仕様書 {@code 12_案件情報登録/12_案件情報登録_テスト仕様書.xlsx} の全ケースを実装する。 営業情報メールの検証に偽
 * SMTP（MailHog）を、サーバー内部の異常分岐に障害シームを用いる。
 */
@EvidenceV2
@Import({E2eMailServer.class, ProjectRegisterFaultSeamConfig.class})
@E2eFeature("12_案件情報登録")
class ProjectRegisterE2eTest extends ProjectE2eTest {

    /** 一般ユーザ SM0001（営業 太郎／営業部）。 */
    private static final String GENERAL_USER = "SM0001";

    /** ベースラインのユーザに共通の開発用ダミーパスワード。 */
    private static final String PASSWORD = "Passw0rd!";

    /** ベースラインが案件情報0件のとき、登録される案件情報の管理コード。 */
    private static final int FIRST_CODE = 1001;

    /** 必須入力エラー。 */
    private static final String REQUIRED = "この項目は入力が必要です。";

    /** 使用文字エラー。 */
    private static final String ILLEGAL_CHARACTER = "使用不可能な文字が含まれています。";

    /** 半角カタカナを含む使用不可能な文字列。 */
    private static final String HALF_WIDTH_KANA = "ｱｲｳ";

    /** 入力チェック確認用の件名。 */
    private static final String CHECK_SUBJECT = "入力チェック確認";

    /** 添付ファイル（6MBちょうど）の名前。 */
    private static final String ATTACH_6MB = "attach-6mb.bin";

    /** 添付ファイル（6MB+1バイト）の名前。 */
    private static final String ATTACH_6MB_PLUS1 = "attach-6mb-plus1.bin";

    /** 添付ファイルの容量違反メッセージ。 */
    private static final String TOO_LARGE = "添付ファイルの容量が大きすぎます(6MBまで)";

    /** 添付ファイル名長の違反メッセージ。 */
    private static final String NAME_TOO_LONG = "ファイル名は200字までです。";

    /** ファイル未指定時のファイルピッカーの表示。 */
    private static final String DROP_PLACEHOLDER = "クリックでファイル選択 ／ ドラッグ＆ドロップ";

    /** 画面表示の日時（yyyy/MM/dd HH:mm:ss）の抽出用（sameText の複合表示比較）。 */
    private static final Pattern DATE_TIME_PATTERN =
            Pattern.compile("\\d{4}/\\d{2}/\\d{2} \\d{2}:\\d{2}:\\d{2}");

    @Autowired
    private E2eMailbox mailbox;

    private LoginPage loginPage;
    private CommonHeaderPage commonHeader;
    private ProjectRegisterPage registerPage;
    private ProjectEditPage editPage;
    private ProjectListPage listPage;
    private ErrorPage errorPage;


    @BeforeEach
    void setUpPages() {
        loginPage = new LoginPage(page, baseUrl());
        commonHeader = new CommonHeaderPage(page, baseUrl());
        registerPage = new ProjectRegisterPage(page, baseUrl());
        editPage = new ProjectEditPage(page, baseUrl());
        listPage = new ProjectListPage(page, baseUrl());
        errorPage = new ErrorPage(page, baseUrl());
        mailbox.reset();
        loginPage.signIn(GENERAL_USER, PASSWORD, ProjectListPage.PATH);
    }

    // ---- 1. 画面仕様・初期表示 ----

    @Test
    @DisplayName("No.1-1 案件情報登録画面の初期表示（画面項目とその初期値）")
    void case1x1() {
        verify.opOpen("1. /projects/new を開く", () -> registerPage.open());

        verify.text("画面タイトル「案件情報登録画面」が表示される", commonHeader.screenTitle(),
                ProjectRegisterPage.SCREEN_TITLE);
        for (String label : List.of("件名", "ステータス", "取引先", "商流", "案件概要", "工程", "開始日", "終了日", "スキル",
                "人数", "残数", "BP募集", "作業場所", "見込単価", "契約種別", "備考", "添付", "メール")) {
            verify.visible("入力項目「" + label + "」が表示される", registerPage.fieldLabel(label));
        }
        verify.visible("BP募集の「BPの募集が必要です」チェックボックスが表示される",
                registerPage.input(ProjectFormPage.BP_REQUIRED));
        verify.visible("BP募集の「詳細: 」が表示される", registerPage.input(ProjectFormPage.BP_DETAIL));
        verify.visible("添付のファイルピッカーが表示される", registerPage.attachmentDropArea());
        verify.visible("メールの「メール送信」チェックボックスが表示される", registerPage.input(ProjectFormPage.SEND_MAIL));
        verify.visible("メール本文が表示される", registerPage.visibleMailBody());

        verify.value("初期値としてステータスが「オープン」である", registerPage.input(ProjectFormPage.STATUS), "OPEN");
        verify.value("初期値として契約種別が「」(空欄)である", registerPage.input(ProjectFormPage.CONTRACT_TYPE), "");
        verify.unchecked("初期値として「BPの募集が必要です」がチェックなしである",
                registerPage.input(ProjectFormPage.BP_REQUIRED));
        verify.checked("初期値として「メール送信」がチェックありである", registerPage.input(ProjectFormPage.SEND_MAIL));

        verify.text("画面上部の「戻る」ボタンが表示される", registerPage.backButtonTop(), "← 戻る");
        verify.text("画面下部の「戻る」ボタンが表示される", registerPage.backButtonBottom(), "← 戻る");
        verify.text("「登録」ボタンが表示される", registerPage.registerButton(), "登録");
        verify.absent("各入力項目の直下に入力値チェックのエラーメッセージは表示されない", registerPage.form(),
                registerPage.visibleFieldErrors());
    }

    @Test
    @DisplayName("No.1-2 取引先のオートコンプリートに、全会社名が会社IDの昇順で表示される（自由記入用の空欄は選択肢として表示されない）")
    void case1x2() {
        verify.opOpen("1. /projects/new を開く", () -> registerPage.open());
        verify.op("2. 「取引先」の入力欄をクリックして選択肢を表示する",
                () -> registerPage.input(ProjectFormPage.CLIENT).click(),
                registerPage.input(ProjectFormPage.CLIENT));

        verify.autocompleteOptions("選択肢に会社名が会社IDの昇順で並ぶ（自由記入用の空欄は選択肢として表示されない）",
                registerPage.input(ProjectFormPage.CLIENT), "clientOptions",
                expectedClientOptions());
    }

    @Test
    @DisplayName("No.1-3 メール送信チェックボックスが初期値（送信あり）のため、メール本文が表示される")
    void case1x3() {
        verify.opOpen("1. /projects/new を開く", () -> registerPage.open());

        verify.checked("「メール送信」チェックボックスがチェックありの状態で表示される",
                registerPage.input(ProjectFormPage.SEND_MAIL));
        verify.visible("その下にメール本文のテキストエリアが表示される", registerPage.visibleMailBody());
    }

    // ---- 2. 画面イベント処理（メール送信切替・添付ファイル） ----

    @Test
    @DisplayName("No.2-1 メール送信チェックボックスの切替でメール本文の表示・非表示が切り替わる")
    void case2x1() {
        verify.opOpen("1. /projects/new を開く（「メール送信」はチェックあり、メール本文が表示されている）",
                () -> registerPage.open());

        verify.transitionHide("1. メール本文のテキストエリアが表示されている", registerPage.visibleMailBody(),
                "2. 「メール送信」のチェックを外す", () -> registerPage.uncheck(ProjectFormPage.SEND_MAIL),
                registerPage.input(ProjectFormPage.SEND_MAIL), "メール本文のテキストエリアが消える");

        verify.op("3. もう一度「メール送信」にチェックを入れる", () -> registerPage.check(ProjectFormPage.SEND_MAIL),
                registerPage.input(ProjectFormPage.SEND_MAIL));
        verify.visible("メール本文のテキストエリアが再び表示される", registerPage.visibleMailBody());
    }

    @Test
    @DisplayName("No.2-2 ファイルピッカーで指定した添付ファイルが表示され、添付ファイルとして保持される")
    void case2x2() {
        Path file = E2eTestFiles.ofSize(ATTACH_6MB, 6291456);
        verify.opOpen("1. /projects/new を開く", () -> registerPage.open());
        verify.opChooseFile("2. 「添付」のファイルピッカーで attach-6mb.bin を指定する",
                registerPage.attachmentDropArea(), file, registerPage.attachmentDropArea());

        verify.containsText("「添付」のファイルピッカーに「attach-6mb.bin(6MB)」が表示される",
                registerPage.attachmentDropArea(), "attach-6mb.bin(6MB)");
        verify.absent("「添付」にファイル形式チェックのエラーメッセージは表示されない", registerPage.attachmentDropArea(),
                registerPage.attachmentErrorWhenShown());
    }

    @Test
    @DisplayName("No.2-3 【境界値】添付ファイルの容量が6MBを超える場合、ファイル形式チェックに違反する")
    void case2x3() {
        Path file = E2eTestFiles.ofSize(ATTACH_6MB_PLUS1, 6291457);
        verify.opOpen("1. /projects/new を開く", () -> registerPage.open());
        verify.opChooseFileRejected(
                "2. 「添付」のファイルピッカーで attach-6mb-plus1.bin を指定する（形式チェック違反で選択は却下される）",
                registerPage.attachmentDropArea(), file, registerPage.attachmentDropArea());

        verify.text("「添付」に表示されるのは「添付ファイルの容量が大きすぎます(6MBまで)」のみである" + "（「ファイル名は200字までです。」は表示されない）",
                registerPage.attachmentError(), TOO_LARGE);
        verify.text("指定したファイル名はファイルピッカーに表示されず、ファイルが指定されていない状態の表示に戻る",
                registerPage.attachmentDropArea().locator(".drop-main"), DROP_PLACEHOLDER);
    }

    @Test
    @DisplayName("No.2-4 【境界値】添付ファイルの容量が6MBちょうどの場合、容量のファイル形式チェックに違反しない")
    void case2x4() {
        Path file = E2eTestFiles.ofSize(ATTACH_6MB, 6291456);
        verify.opOpen("1. /projects/new を開く", () -> registerPage.open());
        verify.opChooseFile("2. 「添付」のファイルピッカーで attach-6mb.bin を指定する",
                registerPage.attachmentDropArea(), file, registerPage.attachmentDropArea());

        verify.containsText("「添付」のファイルピッカーに「attach-6mb.bin(6MB)」が表示される",
                registerPage.attachmentDropArea(), "attach-6mb.bin(6MB)");
        verify.absent("「添付ファイルの容量が大きすぎます(6MBまで)」は表示されない", registerPage.attachmentDropArea(),
                registerPage.attachmentErrorWhenShown());
    }

    @Test
    @DisplayName("No.2-5 【境界値】添付ファイル名が201文字の場合、ファイル形式チェックに違反する")
    void case2x5() {
        Path file = E2eTestFiles.ofNameLength(201);
        verify.opOpen("1. /projects/new を開く", () -> registerPage.open());
        verify.opChooseFileRejected("2. 「添付」のファイルピッカーで、ファイル名が201文字のファイルを指定する（形式チェック違反で選択は却下される）",
                registerPage.attachmentDropArea(), file, registerPage.attachmentDropArea());

        verify.text("「添付」のファイルピッカーに「ファイル名は200字までです。」が表示される", registerPage.attachmentError(),
                NAME_TOO_LONG);
        verify.text("指定したファイル名はファイルピッカーに表示されず、ファイルが指定されていない状態の表示に戻る",
                registerPage.attachmentDropArea().locator(".drop-main"), DROP_PLACEHOLDER);
    }

    @Test
    @DisplayName("No.2-6 【境界値】添付ファイル名が200文字の場合、ファイル名長のファイル形式チェックに違反しない")
    void case2x6() {
        Path file = E2eTestFiles.ofNameLength(200);
        verify.opOpen("1. /projects/new を開く", () -> registerPage.open());
        verify.opChooseFile("2. 「添付」のファイルピッカーで、ファイル名が200文字のファイルを指定する",
                registerPage.attachmentDropArea(), file, registerPage.attachmentDropArea());

        verify.containsText("「添付」のファイルピッカーに指定したファイル名が表示される", registerPage.attachmentDropArea(),
                file.getFileName().toString());
        verify.containsText("「添付」のファイルピッカーにファイルサイズ「(1B)」が表示される", registerPage.attachmentDropArea(),
                "(1B)");
        verify.absent("「ファイル名は200字までです。」は表示されない", registerPage.attachmentDropArea(),
                registerPage.attachmentErrorWhenShown());
    }

    @Test
    @DisplayName("No.2-7 ファイル形式チェック違反後に再度ファイルを指定すると、既存のエラーメッセージが削除されて再チェックされる")
    void case2x7() {
        Path tooLarge = E2eTestFiles.ofSize(ATTACH_6MB_PLUS1, 6291457);
        Path valid = E2eTestFiles.ofSize(ATTACH_6MB, 6291456);
        verify.opOpen("1. /projects/new を開く", () -> registerPage.open());
        verify.opChooseFileRejected(
                "2. 「添付」のファイルピッカーで attach-6mb-plus1.bin を指定する（形式チェック違反で選択は却下される）",
                registerPage.attachmentDropArea(), tooLarge, registerPage.attachmentDropArea());
        verify.text("3. 「添付」に「添付ファイルの容量が大きすぎます(6MBまで)」が表示される", registerPage.attachmentError(),
                TOO_LARGE);

        verify.transitionCleared("エラーメッセージが表示されている", registerPage.attachmentErrorWhenShown(),
                "4. 「添付」のファイルピッカーで attach-6mb.bin を指定する",
                () -> registerPage.attachmentInput().setInputFiles(valid),
                registerPage.attachmentDropArea(), "エラーメッセージが消える");
        verify.containsText("「attach-6mb.bin(6MB)」が表示される", registerPage.attachmentDropArea(),
                "attach-6mb.bin(6MB)");
    }

    @Test
    @DisplayName("No.2-8 同時に指定できる添付ファイルは1件であり、別のファイルを指定すると置き換わる")
    void case2x8() {
        Path first = E2eTestFiles.ofSize(ATTACH_6MB, 6291456);
        Path second = unitData("12_案件情報登録/data/添付ファイル境界.csv");
        verify.opOpen("1. /projects/new を開く", () -> registerPage.open());
        verify.opChooseFile("2. 「添付」のファイルピッカーで attach-6mb.bin を指定する",
                registerPage.attachmentDropArea(), first, registerPage.attachmentDropArea());
        verify.containsText("2. 「添付」に「attach-6mb.bin(6MB)」が表示される",
                registerPage.attachmentDropArea(), "attach-6mb.bin(6MB)");

        verify.opChooseFile("3. 「添付」のファイルピッカーで 添付ファイル境界.csv を指定する",
                registerPage.attachmentDropArea(), second, registerPage.attachmentDropArea());
        verify.text("「添付」の表示が「添付ファイル境界.csv(218B)」だけになり、" + "「attach-6mb.bin(6MB)」は表示されない",
                registerPage.attachmentDropArea().locator(".drop-main"),
                "添付ファイル境界.csv(218B) ✕ 選択解除");
    }

    // ---- 3. 画面遷移（戻る） ----

    @Test
    @DisplayName("No.3-1 戻るボタン（画面上部・下部の2箇所）で入力を破棄して案件情報一覧画面へ遷移する")
    void case3x1() {
        Path file = E2eTestFiles.ofSize(ATTACH_6MB, 6291456);

        verify.opOpen("1. /projects/new を開く", () -> registerPage.open());
        fillDiscardTargets(file);
        verify.opPress("3. 画面上部の「戻る」ボタンを押下する", registerPage.backButtonTop(),
                () -> registerPage.pressBackTop());
        verify.urlIs("3. /projects（案件情報一覧画面）へ遷移する", listPage.url());
        verify.text("3. 取得データ件数が 0件である", listPage.dataCount(), "0");

        verify.opPress("4. 「新規登録」で案件情報登録画面へ戻る", commonHeader.nav(CommonHeaderPage.NAV_REGISTER),
                () -> commonHeader.clickNav(CommonHeaderPage.NAV_REGISTER));
        fillDiscardTargets(file);
        verify.opPress("5. 画面下部の「戻る」ボタンを押下する", registerPage.backButtonBottom(),
                () -> registerPage.pressBackBottom());
        verify.urlIs("5. /projects（案件情報一覧画面）へ遷移する", listPage.url());
        verify.text("5. 取得データ件数が 0件であり、「破棄確認」の案件情報は登録されていない", listPage.dataCount(), "0");

        verify.opPress("6. 「新規登録」で案件情報登録画面へ戻る", commonHeader.nav(CommonHeaderPage.NAV_REGISTER),
                () -> commonHeader.clickNav(CommonHeaderPage.NAV_REGISTER));
        verify.value("6. 「件名」は空欄である", registerPage.input(ProjectFormPage.SUBJECT), "");
        verify.value("6. メール本文は空欄である", registerPage.input(ProjectFormPage.MAIL_BODY), "");
        verify.text("6. 「添付」はファイルが指定されていない状態である",
                registerPage.attachmentDropArea().locator(".drop-main"), DROP_PLACEHOLDER);
    }

    /** 3-1 の手順2（破棄対象の入力）。上部・下部の戻るボタンで同じ入力を繰り返す。 */
    private void fillDiscardTargets(Path file) {
        verify.op("2. 「件名」に「破棄確認」を入力する", () -> registerPage.fill(ProjectFormPage.SUBJECT, "破棄確認"),
                registerPage.input(ProjectFormPage.SUBJECT));
        verify.opChooseFile("2. 「添付」で attach-6mb.bin を指定する", registerPage.attachmentDropArea(),
                file, registerPage.attachmentDropArea());
        verify.op("2. メール本文に「破棄されるはず」を入力する",
                () -> registerPage.fill(ProjectFormPage.MAIL_BODY, "破棄されるはず"),
                registerPage.input(ProjectFormPage.MAIL_BODY));
    }

    // ---- 4. サーバー・APIサービス処理（案件情報の登録・営業情報メール） ----

    @Test
    @DisplayName("No.4-1 入力値チェックに違反がなければ案件情報が登録され、案件情報更新画面へ遷移する")
    void case4x1() {
        verify.opOpen("1. /projects/new を開く", () -> registerPage.open());
        verify.op("2. 「件名」に「登録確認案件」を入力する",
                () -> registerPage.fill(ProjectFormPage.SUBJECT, "登録確認案件"),
                registerPage.input(ProjectFormPage.SUBJECT));
        verify.opPress("3. 「登録」を押下する", registerPage.registerButton(),
                () -> registerPage.pressRegister());

        verify.urlIs("案件情報更新画面 /projects/detail/1001 へ遷移する", editPage.url(FIRST_CODE));
        verify.value("「件名」に「登録確認案件」が表示される", editPage.input(ProjectFormPage.SUBJECT), "登録確認案件");
        verify.absent("各入力項目の直下に入力値チェックのエラーメッセージは表示されない", editPage.form(),
                editPage.visibleFieldErrors());
    }

    @Test
    @DisplayName("No.4-2 入力した各項目とログインユーザから導出される項目が案件情報として登録される")
    void case4x2() {
        verify.opOpen("1. /projects/new を開く", () -> registerPage.open());
        verify.op("2. 「件名」に「全項目確認案件」を入力する",
                () -> registerPage.fill(ProjectFormPage.SUBJECT, "全項目確認案件"),
                registerPage.input(ProjectFormPage.SUBJECT));
        verify.op("2. 「ステータス」に「交渉中」を選択する",
                () -> registerPage.select(ProjectFormPage.STATUS, "NEGOTIATING"),
                registerPage.input(ProjectFormPage.STATUS));
        verify.op("2. 「取引先」に「株式会社アルファ」を入力する",
                () -> registerPage.fill(ProjectFormPage.CLIENT, "株式会社アルファ"),
                registerPage.input(ProjectFormPage.CLIENT));
        verify.op("2. 「商流」に「一次請け」を入力する",
                () -> registerPage.fill(ProjectFormPage.DISTRIBUTION, "一次請け"),
                registerPage.input(ProjectFormPage.DISTRIBUTION));
        verify.op("2. 「案件概要」に「概要テスト」を入力する",
                () -> registerPage.fill(ProjectFormPage.OVERVIEW, "概要テスト"),
                registerPage.input(ProjectFormPage.OVERVIEW));
        verify.op("2. 「工程」に「詳細設計」を入力する", () -> registerPage.fill(ProjectFormPage.PROCESS, "詳細設計"),
                registerPage.input(ProjectFormPage.PROCESS));
        verify.op("2. 「開始日」に 2026/09/01 を入力する",
                () -> registerPage.fill(ProjectFormPage.START_DATE, "2026-09-01"),
                registerPage.input(ProjectFormPage.START_DATE));
        verify.op("2. 「終了日」に 2026/12/31 を入力する",
                () -> registerPage.fill(ProjectFormPage.END_DATE, "2026-12-31"),
                registerPage.input(ProjectFormPage.END_DATE));
        verify.op("2. 「スキル」に「Java」を入力する", () -> registerPage.fill(ProjectFormPage.SKILL, "Java"),
                registerPage.input(ProjectFormPage.SKILL));
        verify.op("2. 「人数」に「3」を入力する", () -> registerPage.fill(ProjectFormPage.HEADCOUNT, "3"),
                registerPage.input(ProjectFormPage.HEADCOUNT));
        verify.op("2. 「残数」に「2」を入力する", () -> registerPage.fill(ProjectFormPage.REMAINING_COUNT, "2"),
                registerPage.input(ProjectFormPage.REMAINING_COUNT));

        verify.op("3. 「BPの募集が必要です」にチェックを入れる", () -> registerPage.check(ProjectFormPage.BP_REQUIRED),
                registerPage.input(ProjectFormPage.BP_REQUIRED));
        verify.op("3. 「詳細: 」に「即戦力歓迎」を入力する",
                () -> registerPage.fill(ProjectFormPage.BP_DETAIL, "即戦力歓迎"),
                registerPage.input(ProjectFormPage.BP_DETAIL));
        verify.op("3. 「作業場所」に「東京都港区」を入力する",
                () -> registerPage.fill(ProjectFormPage.WORKPLACE, "東京都港区"),
                registerPage.input(ProjectFormPage.WORKPLACE));
        verify.op("3. 「見込単価」に「600000」を入力する",
                () -> registerPage.fill(ProjectFormPage.ESTIMATED_PRICE, "600000"),
                registerPage.input(ProjectFormPage.ESTIMATED_PRICE));
        verify.op("3. 「契約種別」に「請負」を選択する",
                () -> registerPage.select(ProjectFormPage.CONTRACT_TYPE, "CONTRACT"),
                registerPage.input(ProjectFormPage.CONTRACT_TYPE));
        verify.op("3. 「備考」に「備考テスト」を入力する", () -> registerPage.fill(ProjectFormPage.NOTE, "備考テスト"),
                registerPage.input(ProjectFormPage.NOTE));

        verify.opPress("4. 「登録」を押下する", registerPage.registerButton(),
                () -> registerPage.pressRegister());

        verify.urlIs("4. 案件情報更新画面 /projects/detail/1001 へ遷移する", editPage.url(FIRST_CODE));
        verify.value("4. 「件名」に「全項目確認案件」が表示される", editPage.input(ProjectFormPage.SUBJECT), "全項目確認案件");
        verify.value("4. 「ステータス」に「交渉中」が表示される", editPage.input(ProjectFormPage.STATUS),
                "NEGOTIATING");
        verify.value("4. 「取引先」に「株式会社アルファ」が表示される", editPage.input(ProjectFormPage.CLIENT),
                "株式会社アルファ");
        verify.value("4. 「商流」に「一次請け」が表示される", editPage.input(ProjectFormPage.DISTRIBUTION), "一次請け");
        verify.value("4. 「案件概要」に「概要テスト」が表示される", editPage.input(ProjectFormPage.OVERVIEW), "概要テスト");
        verify.value("4. 「工程」に「詳細設計」が表示される", editPage.input(ProjectFormPage.PROCESS), "詳細設計");
        verify.value("4. 「開始日」に 2026/09/01 が表示される", editPage.input(ProjectFormPage.START_DATE),
                "2026-09-01");
        verify.value("4. 「終了日」に 2026/12/31 が表示される", editPage.input(ProjectFormPage.END_DATE),
                "2026-12-31");
        verify.value("4. 「スキル」に「Java」が表示される", editPage.input(ProjectFormPage.SKILL), "Java");
        verify.value("4. 「人数」に「3」が表示される", editPage.input(ProjectFormPage.HEADCOUNT), "3");
        verify.value("4. 「残数」に「2」が表示される", editPage.input(ProjectFormPage.REMAINING_COUNT), "2");
        verify.checked("4. 「BPの募集が必要です」がチェックありで表示される", editPage.input(ProjectFormPage.BP_REQUIRED));
        verify.value("4. 「詳細: 」に「即戦力歓迎」が表示される", editPage.input(ProjectFormPage.BP_DETAIL), "即戦力歓迎");
        verify.value("4. 「作業場所」に「東京都港区」が表示される", editPage.input(ProjectFormPage.WORKPLACE), "東京都港区");
        verify.value("4. 「見込単価」に「600000」が表示される", editPage.input(ProjectFormPage.ESTIMATED_PRICE),
                "600000");
        verify.value("4. 「契約種別」に「請負」が表示される", editPage.input(ProjectFormPage.CONTRACT_TYPE),
                "CONTRACT");
        verify.value("4. 「備考」に「備考テスト」が表示される", editPage.input(ProjectFormPage.NOTE), "備考テスト");

        verify.text("登録者に「営業 太郎」が表示される", editPage.meta("登録者"), "営業 太郎");
        verify.text("登録部署に「営業部」が表示される", editPage.meta("登録部署"), "営業部");
        verify.text("更新者は登録者と同じ「営業 太郎」が表示される", editPage.meta("更新者"), "営業 太郎");
        verify.text("更新部署は登録部署と同じ「営業部」が表示される", editPage.meta("更新部署"), "営業部");
        verify.sameText("更新日時は登録日時と同じ値が表示される", editPage.meta("更新日時"), editPage.meta("登録日時"));

        verify.opPress("5. 共通ヘッダーの「一覧・検索」を押下して案件情報一覧画面を開く",
                commonHeader.nav(CommonHeaderPage.NAV_LIST),
                () -> commonHeader.clickNav(CommonHeaderPage.NAV_LIST));
        verify.op("5. 「案件検索」アコーディオンを開く", () -> listPage.toggleSearchAccordion(),
                listPage.searchPanelBody());
        verify.op("5. ステータスの「クローズ」にもチェックを入れる", () -> listPage.check(ProjectListPage.ST_CLOSED),
                listPage.input(ProjectListPage.ST_CLOSED));
        verify.opPress("5. 「検索」を押下する", listPage.searchButton(), () -> {
            listPage.pressSearch();
            listPage.waitForListLoaded();
        });

        verify.gridCellText("ID: 1001 の行が表示される", listPage.grid(), 0, "managementCode", "1001");
        verify.gridCellText("件名「全項目確認案件」が表示される", listPage.grid(), 0, "title", "全項目確認案件");
        verify.gridCellText("ステータス「交渉中」が表示される", listPage.grid(), 0, "status", "交渉中");
        verify.gridCellText("取引先「株式会社アルファ」が表示される", listPage.grid(), 0, "clientName", "株式会社アルファ");
        verify.gridCellText("BP募集(要否)「必要」が表示される", listPage.grid(), 0, "bpRecruitment", "必要");
        verify.gridCellText("契約種別「請負」が表示される", listPage.grid(), 0, "contractType", "請負");
        verify.gridCellText("開始日「2026/09/01」が表示される", listPage.grid(), 0, "startDate", "2026/09/01");
        verify.gridCellText("終了日「2026/12/31」が表示される", listPage.grid(), 0, "endDate", "2026/12/31");
    }

    @Test
    @DisplayName("No.4-3 添付ファイルが指定されていた場合、案件情報と同時に添付ファイルが登録される")
    void case4x3() {
        Path file = E2eTestFiles.ofSize(ATTACH_6MB, 6291456);
        verify.opOpen("1. /projects/new を開く", () -> registerPage.open());
        verify.op("2. 「件名」に「添付あり登録」を入力する",
                () -> registerPage.fill(ProjectFormPage.SUBJECT, "添付あり登録"),
                registerPage.input(ProjectFormPage.SUBJECT));
        verify.opChooseFile("2. 「添付」で attach-6mb.bin を指定する", registerPage.attachmentDropArea(),
                file, registerPage.attachmentDropArea());
        verify.opPress("3. 「登録」を押下する", registerPage.registerButton(),
                () -> registerPage.pressRegister());

        verify.urlIs("案件情報更新画面 /projects/detail/1001 へ遷移する", editPage.url(FIRST_CODE));
        verify.countIs("添付ファイル一覧に行が1件表示される", editPage.attachmentRows(), 1);
        verify.text("添付ファイル一覧に「attach-6mb.bin」の行が表示され、ファイル容量が「6MB」である", editPage.attachmentName(1),
                "1. attach-6mb.bin(6MB)");
        verify.containsText("登録者が「営業 太郎」、登録部署が「営業部」と表示される", editPage.attachmentMeta(1),
                "営業 太郎(営業部)");
        verify.sameText("添付ファイルの登録日時は、案件情報の登録日時と同じ値である", editPage.attachmentMeta(1),
                editPage.meta("登録日時"), DATE_TIME_PATTERN);
    }

    @Test
    @DisplayName("No.4-4 添付ファイルを指定しない場合は案件情報のみが登録される")
    void case4x4() {
        verify.opOpen("1. /projects/new を開く", () -> registerPage.open());
        verify.op("2. 「件名」に「添付なし登録」を入力する（「添付」にファイルを指定しない）",
                () -> registerPage.fill(ProjectFormPage.SUBJECT, "添付なし登録"),
                registerPage.input(ProjectFormPage.SUBJECT));
        verify.opPress("3. 「登録」を押下する", registerPage.registerButton(),
                () -> registerPage.pressRegister());

        verify.urlIs("案件情報更新画面 /projects/detail/1001 へ遷移する", editPage.url(FIRST_CODE));
        verify.value("「件名」に「添付なし登録」が表示される", editPage.input(ProjectFormPage.SUBJECT), "添付なし登録");
        verify.absent("添付ファイル一覧に行が表示されない", editPage.attachmentSectionLabel(),
                editPage.attachmentRows());
    }

    @Test
    @DisplayName("No.4-5 メール送信ありで登録すると、登録した案件情報について営業情報メールが送信される")
    void case4x5() {
        verify.opOpen("1. /projects/new を開く", () -> registerPage.open());
        verify.op("2. 「件名」に「メール送信あり案件」を入力する",
                () -> registerPage.fill(ProjectFormPage.SUBJECT, "メール送信あり案件"),
                registerPage.input(ProjectFormPage.SUBJECT));
        verify.op("2. メール本文に「本文の入力値」を入力する",
                () -> registerPage.fill(ProjectFormPage.MAIL_BODY, "本文の入力値"),
                registerPage.input(ProjectFormPage.MAIL_BODY));
        verify.checked("3. 「メール送信」がチェックありである", registerPage.input(ProjectFormPage.SEND_MAIL));
        verify.opPress("3. 「登録」を押下する", registerPage.registerButton(),
                () -> registerPage.pressRegister());

        verify.urlIs("3. 案件情報更新画面 /projects/detail/1001 へ遷移する", editPage.url(FIRST_CODE));
        verify.mailReceived(mailbox, "【営業情報】(新規) メール送信あり案件", null, "本文の入力値");
    }

    @Test
    @DisplayName("No.4-6 メール送信なしで登録すると、営業情報メールは送信されない")
    void case4x6() {
        verify.opOpen("1. /projects/new を開く", () -> registerPage.open());
        verify.op("2. 「件名」に「メール送信なし案件」を入力する",
                () -> registerPage.fill(ProjectFormPage.SUBJECT, "メール送信なし案件"),
                registerPage.input(ProjectFormPage.SUBJECT));
        verify.op("3. 「メール送信」のチェックを外す", () -> registerPage.uncheck(ProjectFormPage.SEND_MAIL),
                registerPage.input(ProjectFormPage.SEND_MAIL));
        verify.opPress("3. 「登録」を押下する", registerPage.registerButton(),
                () -> registerPage.pressRegister());

        verify.urlIs("3. 案件情報更新画面 /projects/detail/1001 へ遷移し、案件情報は登録されている",
                editPage.url(FIRST_CODE));
        verify.noMailSent(mailbox);
    }

    @Test
    @DisplayName("No.4-7 メールの送信に失敗しても登録は取り消されず、ユーザに失敗は通知されない")
    void case4x7() {
        verify.opArmFault("1. （障害シーム）「メール送信失敗」を有効化する",
                ProjectRegisterFaultSeamConfig.MAIL_SEND_FAILURE);
        verify.opOpen("2. /projects/new を開く", () -> registerPage.open());
        verify.op("2. 「件名」に「メール失敗案件」を入力する",
                () -> registerPage.fill(ProjectFormPage.SUBJECT, "メール失敗案件"),
                registerPage.input(ProjectFormPage.SUBJECT));
        verify.checked("3. 「メール送信」がチェックありである", registerPage.input(ProjectFormPage.SEND_MAIL));
        verify.opPress("3. 「登録」を押下する", registerPage.registerButton(),
                () -> registerPage.pressRegister());

        verify.urlIs("案件情報更新画面 /projects/detail/1001 へ遷移する（登録は取り消されていない）",
                editPage.url(FIRST_CODE));
        verify.value("「件名」に「メール失敗案件」が表示される", editPage.input(ProjectFormPage.SUBJECT), "メール失敗案件");
        verify.faultFired("登録処理がメール送信失敗の経路を通った", ProjectRegisterFaultSeamConfig.MAIL_SEND_FAILURE);
        verify.absent("画面にメール送信の失敗を知らせるメッセージ・エラー表示は現れない", commonHeader.screenTitle(),
                editPage.errorArea().or(editPage.visibleFieldErrors()));
    }

    // ---- 5. 入力チェック ----

    @Test
    @DisplayName("No.5-1 件名が未入力の場合、必須入力に違反する")
    void case5x1() {
        verify.opOpen("1. /projects/new を開く", () -> registerPage.open());
        verify.opPress("2. 「件名」を空のまま「登録」を押下する", registerPage.registerButton(),
                () -> registerPage.pressRegister());

        verifyOnlyFieldError(ProjectFormPage.SUBJECT, "件名", REQUIRED);
        verifyNoProjectRegistered();
    }

    @Test
    @DisplayName("No.5-2 【境界値】件名が201文字の場合、最大文字数(200)に違反する")
    void case5x2() {
        maxLengthCase(ProjectFormPage.SUBJECT, "件名", 200);
    }

    @Test
    @DisplayName("No.5-3 件名に使用できない文字を含む場合、使用文字に違反する")
    void case5x3() {
        illegalCharacterCase(ProjectFormPage.SUBJECT, "件名");
    }

    @Test
    @DisplayName("No.5-4 【境界値】取引先が201文字の場合、最大文字数(200)に違反する")
    void case5x4() {
        maxLengthCase(ProjectFormPage.CLIENT, "取引先", 200);
    }

    @Test
    @DisplayName("No.5-5 取引先に使用できない文字を含む場合、使用文字に違反する")
    void case5x5() {
        illegalCharacterCase(ProjectFormPage.CLIENT, "取引先");
    }

    @Test
    @DisplayName("No.5-6 【境界値】商流が201文字の場合、最大文字数(200)に違反する")
    void case5x6() {
        maxLengthCase(ProjectFormPage.DISTRIBUTION, "商流", 200);
    }

    @Test
    @DisplayName("No.5-7 商流に使用できない文字を含む場合、使用文字に違反する")
    void case5x7() {
        illegalCharacterCase(ProjectFormPage.DISTRIBUTION, "商流");
    }

    @Test
    @DisplayName("No.5-8 【境界値】案件概要が2001文字の場合、最大文字数(2000)に違反する")
    void case5x8() {
        maxLengthCase(ProjectFormPage.OVERVIEW, "案件概要", 2000);
    }

    @Test
    @DisplayName("No.5-9 案件概要に使用できない文字を含む場合、使用文字に違反する")
    void case5x9() {
        illegalCharacterCase(ProjectFormPage.OVERVIEW, "案件概要");
    }

    @Test
    @DisplayName("No.5-10 【境界値】工程が201文字の場合、最大文字数(200)に違反する")
    void case5x10() {
        maxLengthCase(ProjectFormPage.PROCESS, "工程", 200);
    }

    @Test
    @DisplayName("No.5-11 工程に使用できない文字を含む場合、使用文字に違反する")
    void case5x11() {
        illegalCharacterCase(ProjectFormPage.PROCESS, "工程");
    }

    @Test
    @DisplayName("No.5-12 開始日が日付として解釈できない場合、日付形式に違反する")
    void case5x12() {
        invalidDateCase(ProjectFormPage.START_DATE, "開始日", "2026/13/01");
    }

    @Test
    @DisplayName("No.5-13 終了日が日付として解釈できない場合、日付形式に違反する")
    void case5x13() {
        invalidDateCase(ProjectFormPage.END_DATE, "終了日", "2026/02/30");
    }

    @Test
    @DisplayName("No.5-14 終了日が開始日より前の場合、日付下限に違反する")
    void case5x14() {
        verify.opOpen("1. /projects/new を開く", () -> registerPage.open());
        verify.op("2. 「件名」に「入力チェック確認」を入力する",
                () -> registerPage.fill(ProjectFormPage.SUBJECT, CHECK_SUBJECT),
                registerPage.input(ProjectFormPage.SUBJECT));
        verify.op("3. 「開始日」に 2026/12/01 を入力する",
                () -> registerPage.fill(ProjectFormPage.START_DATE, "2026-12-01"),
                registerPage.input(ProjectFormPage.START_DATE));
        verify.op("3. 「終了日」に 2026/11/30 を入力する",
                () -> registerPage.fill(ProjectFormPage.END_DATE, "2026-11-30"),
                registerPage.input(ProjectFormPage.END_DATE));
        verify.opPress("4. 「登録」を押下する", registerPage.registerButton(),
                () -> registerPage.pressRegister());

        verifyOnlyFieldError(ProjectFormPage.END_DATE, "終了日", "開始日以降の日付を入力してください。");
        verifyNoProjectRegistered();
    }

    @Test
    @DisplayName("No.5-15 【境界値】スキルが201文字の場合、最大文字数(200)に違反する")
    void case5x15() {
        maxLengthCase(ProjectFormPage.SKILL, "スキル", 200);
    }

    @Test
    @DisplayName("No.5-16 スキルに使用できない文字を含む場合、使用文字に違反する")
    void case5x16() {
        illegalCharacterCase(ProjectFormPage.SKILL, "スキル");
    }

    @Test
    @DisplayName("No.5-17 【境界値】人数が201文字の場合、最大文字数(200)に違反する")
    void case5x17() {
        maxLengthCase(ProjectFormPage.HEADCOUNT, "人数", 200);
    }

    @Test
    @DisplayName("No.5-18 人数に使用できない文字を含む場合、使用文字に違反する")
    void case5x18() {
        illegalCharacterCase(ProjectFormPage.HEADCOUNT, "人数");
    }

    @Test
    @DisplayName("No.5-19 【境界値】残数が201文字の場合、最大文字数(200)に違反する")
    void case5x19() {
        maxLengthCase(ProjectFormPage.REMAINING_COUNT, "残数", 200);
    }

    @Test
    @DisplayName("No.5-20 残数に使用できない文字を含む場合、使用文字に違反する")
    void case5x20() {
        illegalCharacterCase(ProjectFormPage.REMAINING_COUNT, "残数");
    }

    @Test
    @DisplayName("No.5-21 【境界値】詳細: が201文字の場合、最大文字数(200)に違反する")
    void case5x21() {
        maxLengthCase(ProjectFormPage.BP_DETAIL, "詳細: ", 200);
    }

    @Test
    @DisplayName("No.5-22 詳細: に使用できない文字を含む場合、使用文字に違反する")
    void case5x22() {
        illegalCharacterCase(ProjectFormPage.BP_DETAIL, "詳細: ");
    }

    @Test
    @DisplayName("No.5-23 【境界値】作業場所が201文字の場合、最大文字数(200)に違反する")
    void case5x23() {
        maxLengthCase(ProjectFormPage.WORKPLACE, "作業場所", 200);
    }

    @Test
    @DisplayName("No.5-24 作業場所に使用できない文字を含む場合、使用文字に違反する")
    void case5x24() {
        illegalCharacterCase(ProjectFormPage.WORKPLACE, "作業場所");
    }

    @Test
    @DisplayName("No.5-25 【境界値】見込単価が201文字の場合、最大文字数(200)に違反する")
    void case5x25() {
        maxLengthCase(ProjectFormPage.ESTIMATED_PRICE, "見込単価", 200);
    }

    @Test
    @DisplayName("No.5-26 見込単価に使用できない文字を含む場合、使用文字に違反する")
    void case5x26() {
        illegalCharacterCase(ProjectFormPage.ESTIMATED_PRICE, "見込単価");
    }

    @Test
    @DisplayName("No.5-27 【境界値】備考が4001文字の場合、最大文字数(4000)に違反する")
    void case5x27() {
        maxLengthCase(ProjectFormPage.NOTE, "備考", 4000);
    }

    @Test
    @DisplayName("No.5-28 備考に使用できない文字を含む場合、使用文字に違反する")
    void case5x28() {
        illegalCharacterCase(ProjectFormPage.NOTE, "備考");
    }

    @Test
    @DisplayName("No.5-29 【境界値】メール本文が4001文字の場合、最大文字数(4000)に違反する")
    void case5x29() {
        maxLengthCase(ProjectFormPage.MAIL_BODY, "メール本文", 4000);
    }

    @Test
    @DisplayName("No.5-30 メール本文に使用できない文字を含む場合、使用文字に違反する")
    void case5x30() {
        illegalCharacterCase(ProjectFormPage.MAIL_BODY, "メール本文");
    }

    @Test
    @DisplayName("No.5-31 【境界値】各項目の最大文字数ちょうど・改行を含むテキストエリアは入力値チェックに違反しない")
    void case5x31() {
        String text200 = "あ".repeat(200);

        verify.opOpen("1. /projects/new を開く", () -> registerPage.open());
        verify.op("2. 「件名」に全角200文字を入力する", () -> registerPage.fill(ProjectFormPage.SUBJECT, text200),
                registerPage.input(ProjectFormPage.SUBJECT));
        Map<String, String> text200Fields = new LinkedHashMap<>();
        text200Fields.put(ProjectFormPage.CLIENT, "取引先");
        text200Fields.put(ProjectFormPage.DISTRIBUTION, "商流");
        text200Fields.put(ProjectFormPage.PROCESS, "工程");
        text200Fields.put(ProjectFormPage.SKILL, "スキル");
        text200Fields.put(ProjectFormPage.HEADCOUNT, "人数");
        text200Fields.put(ProjectFormPage.REMAINING_COUNT, "残数");
        text200Fields.put(ProjectFormPage.BP_DETAIL, "詳細: ");
        text200Fields.put(ProjectFormPage.WORKPLACE, "作業場所");
        text200Fields.put(ProjectFormPage.ESTIMATED_PRICE, "見込単価");
        text200Fields.forEach((id, label) -> verify.op("2. 「" + label + "」に全角200文字を入力する",
                () -> registerPage.fill(id, text200), registerPage.input(id)));

        // テキストエリアの改行は送信時に CR+LF へ正規化される（HTML の仕様）。最大文字数ちょうど
        // （改行文字は U+000D・U+000A の 2 文字）になるよう、入力する文字数を組み立てる。
        String overview2000 = "あ".repeat(999) + "\n" + "あ".repeat(999);
        String note4000 = "あ".repeat(1999) + "\n" + "あ".repeat(1999);
        verify.op("3. 「案件概要」に改行を含む全角2000文字を入力する",
                () -> registerPage.fill(ProjectFormPage.OVERVIEW, overview2000),
                registerPage.input(ProjectFormPage.OVERVIEW));
        verify.op("3. 「備考」に改行を含む全角4000文字を入力する",
                () -> registerPage.fill(ProjectFormPage.NOTE, note4000),
                registerPage.input(ProjectFormPage.NOTE));
        verify.op("3. メール本文に改行を含む全角4000文字を入力する",
                () -> registerPage.fill(ProjectFormPage.MAIL_BODY, note4000),
                registerPage.input(ProjectFormPage.MAIL_BODY));
        verify.op("4. 「開始日」に 2026/09/01 を入力する",
                () -> registerPage.fill(ProjectFormPage.START_DATE, "2026-09-01"),
                registerPage.input(ProjectFormPage.START_DATE));
        verify.op("4. 「終了日」に 2026/09/01 を入力する（開始日と同日）",
                () -> registerPage.fill(ProjectFormPage.END_DATE, "2026-09-01"),
                registerPage.input(ProjectFormPage.END_DATE));
        verify.opPress("5. 「登録」を押下する", registerPage.registerButton(),
                () -> registerPage.pressRegister());

        verify.urlIs("案件情報更新画面 /projects/detail/1001 へ遷移する", editPage.url(FIRST_CODE));
        verify.absent("各入力項目の直下に入力値チェックのエラーメッセージは表示されない", editPage.form(),
                editPage.visibleFieldErrors());
        verify.value("「件名」に入力した値が表示される", editPage.input(ProjectFormPage.SUBJECT), text200);
        verify.value("「案件概要」に入力した値（改行を含む）が表示される", editPage.input(ProjectFormPage.OVERVIEW),
                overview2000);
        verify.value("「備考」に入力した値（改行を含む）が表示される", editPage.input(ProjectFormPage.NOTE), note4000);
    }

    @Test
    @DisplayName("No.5-32 チェック違反のあった全ての画面項目にエラーメッセージを表示する")
    void case5x32() {
        verify.opOpen("1. /projects/new を開く", () -> registerPage.open());
        verify.op("2. 「商流」に半角カタカナを含む「ｱｲｳ」を入力する（「件名」は空のまま）",
                () -> registerPage.fill(ProjectFormPage.DISTRIBUTION, HALF_WIDTH_KANA),
                registerPage.input(ProjectFormPage.DISTRIBUTION));
        verify.opPress("3. 「登録」を押下する", registerPage.registerButton(),
                () -> registerPage.pressRegister());

        verify.text("「件名」の入力欄の直下に「この項目は入力が必要です。」が表示される",
                registerPage.fieldError(ProjectFormPage.SUBJECT), REQUIRED);
        verify.text("「商流」の入力欄の直下に「使用不可能な文字が含まれています。」が同時に表示される",
                registerPage.fieldError(ProjectFormPage.DISTRIBUTION), ILLEGAL_CHARACTER);
        verify.urlIs("画面遷移せずに案件情報登録画面が再表示される", registerPage.url());
        verifyNoProjectRegistered();
    }

    @Test
    @DisplayName("No.5-33 同じ項目内で複数の入力値チェックに違反した場合、より小さな番号のエラーメッセージ1つのみを表示する")
    void case5x33() {
        verify.opOpen("1. /projects/new を開く", () -> registerPage.open());
        verify.opBypassRemoveAttribute("2. 「件名」入力欄の maxlength 属性を解除する",
                registerPage.input(ProjectFormPage.SUBJECT), "maxlength");
        verify.op("3. 「件名」に、半角カタカナ「ｱ」を含む201文字を入力する",
                () -> registerPage.fill(ProjectFormPage.SUBJECT, "ｱ" + "a".repeat(200)),
                registerPage.input(ProjectFormPage.SUBJECT));
        verify.opPress("4. 「登録」を押下する", registerPage.registerButton(),
                () -> registerPage.pressRegister());

        verify.text("「件名」の入力欄の直下に表示されるのは「200文字以内で入力してください。」のみである" + "（「使用不可能な文字が含まれています。」は表示されない）",
                registerPage.fieldError(ProjectFormPage.SUBJECT), "200文字以内で入力してください。");
        verify.urlIs("画面遷移せずに案件情報登録画面が再表示される", registerPage.url());
        verifyNoProjectRegistered();
    }

    @Test
    @DisplayName("No.5-34 入力値チェック違反後に再度登録を実施すると、既存のエラーメッセージが削除されてから再チェックされる")
    void case5x34() {
        verify.opOpen("1. /projects/new を開く", () -> registerPage.open());
        verify.opPress("1. 「件名」を空のまま「登録」を押下する", registerPage.registerButton(),
                () -> registerPage.pressRegister());
        verify.text("2. 「件名」の入力欄の直下に「この項目は入力が必要です。」が表示される",
                registerPage.fieldError(ProjectFormPage.SUBJECT), REQUIRED);

        verify.op("3. 「件名」に「再チェック確認」を入力する",
                () -> registerPage.fill(ProjectFormPage.SUBJECT, "再チェック確認"),
                registerPage.input(ProjectFormPage.SUBJECT));
        verify.op("3. 「商流」に半角カタカナを含む「ｱｲｳ」を入力する",
                () -> registerPage.fill(ProjectFormPage.DISTRIBUTION, HALF_WIDTH_KANA),
                registerPage.input(ProjectFormPage.DISTRIBUTION));
        verify.transitionClearedByPress("「件名」の直下にエラーメッセージが表示されている",
                registerPage.fieldError(ProjectFormPage.SUBJECT), "3. 「登録」を押下する",
                registerPage.registerButton(), () -> registerPage.pressRegister(),
                registerPage.fieldError(ProjectFormPage.DISTRIBUTION), "「件名」の直下のエラーメッセージが消える");

        verify.text("「商流」の入力欄の直下だけに「使用不可能な文字が含まれています。」が表示される",
                registerPage.fieldError(ProjectFormPage.DISTRIBUTION), ILLEGAL_CHARACTER);
        verify.countIs("表示されている入力値チェックのエラーメッセージは1件のみである", registerPage.visibleFieldErrors(), 1);
        verify.urlIs("画面遷移せずに案件情報登録画面が再表示される", registerPage.url());
        verifyNoProjectRegistered();
    }

    @Test
    @DisplayName("No.5-35 入力値チェック違反による再表示で、入力済みの値が各項目へ戻る")
    void case5x35() {
        verify.opOpen("1. /projects/new を開く", () -> registerPage.open());
        verify.op("2. 「商流」に「一次請け」を入力する（「件名」は空のまま）",
                () -> registerPage.fill(ProjectFormPage.DISTRIBUTION, "一次請け"),
                registerPage.input(ProjectFormPage.DISTRIBUTION));
        verify.op("2. 「案件概要」に「概要テスト」を入力する",
                () -> registerPage.fill(ProjectFormPage.OVERVIEW, "概要テスト"),
                registerPage.input(ProjectFormPage.OVERVIEW));
        verify.op("2. 「スキル」に「Java」を入力する", () -> registerPage.fill(ProjectFormPage.SKILL, "Java"),
                registerPage.input(ProjectFormPage.SKILL));
        verify.opPress("3. 「登録」を押下する", registerPage.registerButton(),
                () -> registerPage.pressRegister());

        verify.text("「件名」の入力欄の直下に「この項目は入力が必要です。」が表示される",
                registerPage.fieldError(ProjectFormPage.SUBJECT), REQUIRED);
        verify.value("再表示された画面の「商流」に「一次請け」が入力されたまま残っている",
                registerPage.input(ProjectFormPage.DISTRIBUTION), "一次請け");
        verify.value("再表示された画面の「案件概要」に「概要テスト」が入力されたまま残っている",
                registerPage.input(ProjectFormPage.OVERVIEW), "概要テスト");
        verify.value("再表示された画面の「スキル」に「Java」が入力されたまま残っている",
                registerPage.input(ProjectFormPage.SKILL), "Java");
        verify.urlIs("画面遷移せずに案件情報登録画面が再表示される", registerPage.url());
        verifyNoProjectRegistered();
    }

    @Test
    @DisplayName("No.5-36 ステータスに空値を送った場合、必須入力に違反する")
    void case5x36() {
        verify.opOpen("1. /projects/new を開く", () -> registerPage.open());
        verify.op("2. 「件名」に「入力チェック確認」を入力する",
                () -> registerPage.fill(ProjectFormPage.SUBJECT, CHECK_SUBJECT),
                registerPage.input(ProjectFormPage.SUBJECT));
        verify.op("3. 「ステータス」の値を空にする（選択肢に空欄が無いため画面操作では選べない）",
                () -> registerPage.forceStatusValue(""),
                registerPage.input(ProjectFormPage.STATUS));
        verify.machineEquals("手順3で「ステータス」に空値が設定されている", "", registerPage.statusValue());
        verify.opPress("4. 「登録」を押下する", registerPage.registerButton(),
                () -> registerPage.pressRegister());

        verify.urlIs("エラー画面へは遷移せず、画面遷移せずに案件情報登録画面が再表示される", registerPage.url());
        verifyOnlyFieldError(ProjectFormPage.STATUS, "ステータス", REQUIRED);
        verifyNoProjectRegistered();
    }

    // ---- 6. 認証・認可／サーバー処理の異常分岐 ----

    @Test
    @DisplayName("No.6-1 登録時にログインの有効期限が切れている場合、入力値は破棄され登録は行われない")
    void case6x1() {
        verify.opOpen("1. /projects/new を開く", () -> registerPage.open());
        verify.op("1. 「件名」に「失効時登録」を入力する", () -> registerPage.fill(ProjectFormPage.SUBJECT, "失効時登録"),
                registerPage.input(ProjectFormPage.SUBJECT));

        verify.op("2. ブラウザが保持するセッションCookieを削除する（ログインの有効期限が切れた状態にする）",
                () -> registerPage.clearSessionCookies());
        verify.machineEquals("手順2の後、セッションCookie（JSESSIONID）は 0件である", "0",
                String.valueOf(registerPage.sessionCookieCount()));

        verify.opPress("3. 「登録」を押下する", registerPage.registerButton(),
                () -> registerPage.pressRegister());
        verify.visible("案件情報更新画面へは遷移せずログイン画面が表示される", loginPage.form());

        verify.op("4. ユーザID に SM0001 を入力する", () -> loginPage.fillUserId(GENERAL_USER),
                loginPage.userIdInput());
        verify.op("4. パスワードを入力する", () -> loginPage.fillPassword(PASSWORD),
                loginPage.passwordInput());
        verify.opPress("4. 「ログイン」を押下する", loginPage.loginButton(), () -> {
            loginPage.pressLogin();
            listPage.waitForListLoaded();
        });

        verify.urlIs("4. 案件情報登録画面ではなく案件情報一覧画面 /projects へ遷移する", listPage.url());
        verify.text("4. 取得データ件数が 0件である（案件情報の登録は行われていない）", listPage.dataCount(), "0");

        verify.opPress("5. 共通ヘッダーの「新規登録」を押下して案件情報登録画面を開く",
                commonHeader.nav(CommonHeaderPage.NAV_REGISTER),
                () -> commonHeader.clickNav(CommonHeaderPage.NAV_REGISTER));
        verify.value("5. 「件名」は空欄である（入力した値は破棄されている）", registerPage.input(ProjectFormPage.SUBJECT),
                "");
    }

    @Test
    @DisplayName("No.6-2 登録処理でデータベース例外が発生した場合、登録前の状態に戻りエラー画面に【サーバー内部エラー】が表示される")
    void case6x2() {
        Path file = E2eTestFiles.ofSize(ATTACH_6MB, 6291456);
        verify.opArmFault("1. （障害シーム）「データベース例外（案件登録）」を有効化する",
                ProjectRegisterFaultSeamConfig.DB_EXCEPTION_ON_REGISTER);
        verify.opOpen("2. /projects/new を開く", () -> registerPage.open());
        verify.op("2. 「件名」に「例外時登録」を入力する", () -> registerPage.fill(ProjectFormPage.SUBJECT, "例外時登録"),
                registerPage.input(ProjectFormPage.SUBJECT));
        verify.opChooseFile("2. 「添付」で attach-6mb.bin を指定する", registerPage.attachmentDropArea(),
                file, registerPage.attachmentDropArea());
        verify.opPress("3. 「登録」を押下する", registerPage.registerButton(),
                () -> registerPage.pressRegister());

        verify.text("3. エラータイトル「An Error Occurred」が表示される", errorPage.errorTitle(),
                "An Error Occurred");
        verify.text("3. エラー内容「エラーが発生しました。」が表示される", errorPage.errorMessage(), "エラーが発生しました。");
        verify.faultFired("登録処理がデータベース例外の経路を通った",
                ProjectRegisterFaultSeamConfig.DB_EXCEPTION_ON_REGISTER);

        verify.opPress("4. 「TOINDEX」を押下して案件情報一覧画面を開く", errorPage.toIndexButton(), () -> {
            errorPage.pressToIndex();
            listPage.waitForListLoaded();
        });
        verify.text("4. 取得データ件数が 0件であり、案件情報・添付ファイルとも登録実施前の状態に戻っている", listPage.dataCount(), "0");
    }

    @Test
    @DisplayName("No.6-3 区分項目に選択肢に無い値を受けた場合、入力値チェックとして扱わずサーバー内部エラーになる")
    void case6x3() {
        verify.opOpen("1. /projects/new を開く", () -> registerPage.open());
        verify.op("1. 「件名」に「区分値改ざん」を入力する",
                () -> registerPage.fill(ProjectFormPage.SUBJECT, "区分値改ざん"),
                registerPage.input(ProjectFormPage.SUBJECT));
        verify.op("2. 「ステータス」の選択肢に無い値「UNKNOWN」を選択済みの値として設定する",
                () -> registerPage.forceStatusValue("UNKNOWN"),
                registerPage.input(ProjectFormPage.STATUS));
        verify.machineEquals("手順2で「ステータス」に「UNKNOWN」が設定されている", "UNKNOWN",
                registerPage.statusValue());
        verify.opPress("3. 「登録」を押下する", registerPage.registerButton(),
                () -> registerPage.pressRegister());

        verify.text("エラータイトル「An Error Occurred」が表示される", errorPage.errorTitle(),
                "An Error Occurred");
        verify.text("エラー内容「エラーが発生しました。」が表示される", errorPage.errorMessage(), "エラーが発生しました。");
        verify.absent("案件情報登録画面は再表示されず、入力値チェックのエラーメッセージも表示されない", errorPage.card(),
                registerPage.form().or(registerPage.visibleFieldErrors()));
        verifyNoProjectRegistered();
    }

    @Test
    @DisplayName("No.6-4 クライアント側のファイル形式チェックを迂回しても、サーバ側の防御的な検証で容量違反が検出される")
    void case6x4() {
        Path file = E2eTestFiles.ofSize(ATTACH_6MB_PLUS1, 6291457);
        verify.opOpen("1. /projects/new を開く", () -> registerPage.open());
        verify.op("1. 「件名」に「サーバ側検証」を入力する",
                () -> registerPage.fill(ProjectFormPage.SUBJECT, "サーバ側検証"),
                registerPage.input(ProjectFormPage.SUBJECT));
        verify.op("2. ファイル選択イベントのファイル形式チェックを迂回して attach-6mb-plus1.bin を" + "添付として送信させる",
                () -> registerPage.attachBypassingClientCheck(file));
        verify.machineEquals("手順2で attach-6mb-plus1.bin が添付として設定されている", ATTACH_6MB_PLUS1,
                registerPage.attachedFileName());
        verify.opPress("3. 「登録」を押下する", registerPage.registerButton(),
                () -> registerPage.pressRegister());

        verify.urlIs("登録は実施されず、案件情報登録画面が再表示される", registerPage.url());
        verify.text("「添付」の項目に「添付ファイルの容量が大きすぎます(6MBまで)」が表示される", registerPage.attachmentError(),
                TOO_LARGE);
        verifyNoProjectRegistered();
    }

    // ---- 共通シナリオ・検証ヘルパー ----

    /** 最大文字数違反の共通シナリオ（maxlength を解除して超過文字数を入力し登録する）。 */
    private void maxLengthCase(String inputId, String label, int max) {
        verify.opOpen("1. /projects/new を開く", () -> registerPage.open());
        verify.op("2. 「件名」に「入力チェック確認」を入力する",
                () -> registerPage.fill(ProjectFormPage.SUBJECT, CHECK_SUBJECT),
                registerPage.input(ProjectFormPage.SUBJECT));
        verify.opBypassRemoveAttribute("3. 「" + label + "」入力欄の maxlength 属性を解除する",
                registerPage.input(inputId), "maxlength");
        verify.op("4. 「" + label + "」に半角英字" + (max + 1) + "文字（a を" + (max + 1) + "回）を入力する",
                () -> registerPage.fill(inputId, "a".repeat(max + 1)), registerPage.input(inputId));
        verify.opPress("5. 「登録」を押下する", registerPage.registerButton(),
                () -> registerPage.pressRegister());

        verifyOnlyFieldError(inputId, label, max + "文字以内で入力してください。");
        verifyNoProjectRegistered();
    }

    /** 使用文字違反の共通シナリオ（半角カタカナを含む値を入力して登録する）。 */
    private void illegalCharacterCase(String inputId, String label) {
        verify.opOpen("1. /projects/new を開く", () -> registerPage.open());
        verify.op("2. 「件名」に「入力チェック確認」を入力する",
                () -> registerPage.fill(ProjectFormPage.SUBJECT, CHECK_SUBJECT),
                registerPage.input(ProjectFormPage.SUBJECT));
        verify.op("3. 「" + label + "」に半角カタカナを含む「ｱｲｳ」を入力する",
                () -> registerPage.fill(inputId, HALF_WIDTH_KANA), registerPage.input(inputId));
        verify.opPress("4. 「登録」を押下する", registerPage.registerButton(),
                () -> registerPage.pressRegister());

        verifyOnlyFieldError(inputId, label, ILLEGAL_CHARACTER);
        verifyNoProjectRegistered();
    }

    /** 日付形式違反の共通シナリオ（type を date から text へ変更して不正な日付を入力する）。 */
    private void invalidDateCase(String inputId, String label, String value) {
        verify.opOpen("1. /projects/new を開く", () -> registerPage.open());
        verify.op("2. 「件名」に「入力チェック確認」を入力する",
                () -> registerPage.fill(ProjectFormPage.SUBJECT, CHECK_SUBJECT),
                registerPage.input(ProjectFormPage.SUBJECT));
        verify.opBypassSetAttribute("3. 「" + label + "」入力欄の type 属性を date から text へ変更する",
                registerPage.input(inputId), "type", "text");
        verify.op("4. 「" + label + "」に「" + value + "」を入力する",
                () -> registerPage.fill(inputId, value), registerPage.input(inputId));
        verify.opPress("5. 「登録」を押下する", registerPage.registerButton(),
                () -> registerPage.pressRegister());

        verifyOnlyFieldError(inputId, label, "無効な日付です。");
        verifyNoProjectRegistered();
    }

    /** 指定項目にだけエラーメッセージが表示されていることを検証する。 */
    private void verifyOnlyFieldError(String inputId, String label, String message) {
        verify.text("「" + label + "」の入力欄の直下に「" + message + "」が表示される",
                registerPage.fieldError(inputId), message);
        verify.countIs("表示されている入力値チェックのエラーメッセージは1件のみで、" + "他の画面項目の直下には表示されない",
                registerPage.visibleFieldErrors(), 1);
        verify.urlIs("登録は実施されず、画面遷移せずに案件情報登録画面が再表示される", registerPage.url());
    }

    /** 【機械検証】案件情報が登録されていないこと（/projects の取得データ件数が 0件）。 */
    private void verifyNoProjectRegistered() {
        verify.opOpen("【機械検証】のため /projects（案件情報一覧画面）を開く", () -> listPage.open());
        verify.machineEquals("案件情報は登録されていない（/projects の取得データ件数が 0件）", "0",
                listPage.dataCountText());
    }

    /** 取引先のオートコンプリート候補の期待値（会社名を会社IDの昇順。自由記入用の空欄は含まない）。 */
    private List<String> expectedClientOptions() {
        List<String> expected = new ArrayList<>();
        Path csv = E2eSpecLayout.baselineDataDir().resolve("companies.csv");
        List<List<String>> records = E2eSeedSupport.parseCsv(E2eSeedSupport.readContent(csv));
        records.stream().skip(1)
                .sorted((a, b) -> Integer.compare(Integer.parseInt(a.get(0).trim()),
                        Integer.parseInt(b.get(0).trim())))
                .forEach(row -> expected.add(row.get(1)));
        return expected;
    }
}
