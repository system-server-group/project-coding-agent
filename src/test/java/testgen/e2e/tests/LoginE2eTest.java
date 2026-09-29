package testgen.e2e.tests;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import testgen.e2e.ProjectE2eTest;
import testgen.e2e.pages.CommonHeaderPage;
import testgen.e2e.pages.LoginPage;
import testgen.e2e.pages.ProjectListPage;
import testgen.e2e.support.E2eFeature;
import testgen.e2e.support.evidence.EvidenceV2;

/**
 * ログイン画面（機能 {@code 03_ログイン}）の E2E テスト。
 *
 * <p>
 * テストケース仕様書 {@code 03_ログイン/03_ログイン_テスト仕様書.xlsx} の全ケースを実装する。
 */
@EvidenceV2
@E2eFeature("03_ログイン")
class LoginE2eTest extends ProjectE2eTest {

    /** 一般ユーザ SM0001（営業 太郎／営業部）。 */
    private static final String GENERAL_USER = "SM0001";

    /** ベースラインのユーザに共通の開発用ダミーパスワード。 */
    private static final String PASSWORD = "Passw0rd!";

    /** ユーザマスタに登録されていないユーザID。 */
    private static final String UNKNOWN_USER = "SM9999";

    /** 必須入力エラー（ユーザID）。 */
    private static final String USER_ID_REQUIRED = "ユーザIDを入力してください。";

    /** 必須入力エラー（パスワード）。 */
    private static final String PASSWORD_REQUIRED = "パスワードを入力してください。";

    /** 最大文字数エラー。 */
    private static final String MAX_LENGTH_ERROR = "60文字以内で入力してください。";

    /** 使用文字エラー。 */
    private static final String ILLEGAL_CHARACTER_ERROR = "使用不可能な文字が含まれています。";

    /** ログイン失敗メッセージ。 */
    private static final String LOGIN_FAILURE = "ユーザIDかパスワードが間違っています。";

    private LoginPage loginPage;
    private CommonHeaderPage commonHeader;
    private ProjectListPage projectListPage;


    @BeforeEach
    void setUpPages() {
        loginPage = new LoginPage(page, baseUrl());
        commonHeader = new CommonHeaderPage(page, baseUrl());
        projectListPage = new ProjectListPage(page, baseUrl());
    }

    @Test
    @DisplayName("No.1-1 ログイン画面の初期表示（画面項目・プレースホルダー・マスク表示・失敗メッセージの非表示）")
    void case1x1() {
        verify.opOpen("1. / を開く", () -> loginPage.open());

        verify.visible("ログイン画面が単独の画面として表示される", loginPage.form());
        verify.text("システムタイトルに「営業情報管理システム」が表示される", loginPage.systemTitle(), "営業情報管理システム");
        verify.text("ユーザID欄のラベル「ユーザID」が表示される", loginPage.userIdLabel(), "ユーザID");
        verify.visible("ユーザID欄にプレースホルダー「SM0000」が表示される",
                loginPage.userIdInputWithPlaceholder("SM0000"));
        verify.text("パスワード欄のラベル「パスワード」が表示される", loginPage.passwordLabel(), "パスワード");
        verify.visible("パスワード表示トグルが表示される", loginPage.passwordToggle());
        verify.text("ログインボタン「ログイン」が表示される", loginPage.loginButton(), "ログイン");
        verify.absent("ログイン失敗メッセージ「ユーザIDかパスワードが間違っています。」は表示されない", loginPage.form(),
                loginPage.failureMessage());
        verify.absent("ユーザID欄・パスワード欄の直下に入力値チェックのエラーメッセージは表示されない", loginPage.form(),
                loginPage.visibleFieldErrors());
        verify.absent("共通ヘッダー（ナビゲーション・ユーザ名／部署名・ログアウト）は表示されない", loginPage.form(),
                commonHeader.header());
    }

    @Test
    @DisplayName("No.1-2 ログイン画面の表示時に、保存されていた案件情報一覧の検索条件が初期化される")
    void case1x2() {
        loginPage.signIn(GENERAL_USER, PASSWORD, ProjectListPage.PATH);

        verify.opOpen("1. /projects を開く", () -> projectListPage.open());
        verify.op("1. 「案件検索」アコーディオンを開く", () -> projectListPage.toggleSearchAccordion(),
                projectListPage.searchPanelBody());

        verify.op("2. 「検索キーワード」に「保存確認」を入力する",
                () -> projectListPage.fill(ProjectListPage.KEYWORD, "保存確認"),
                projectListPage.input(ProjectListPage.KEYWORD));
        verify.op("2. ステータスの「クローズ」にチェックを入れる",
                () -> projectListPage.check(ProjectListPage.ST_CLOSED),
                projectListPage.input(ProjectListPage.ST_CLOSED));
        verify.value("2. 「検索キーワード」に「保存確認」が入力された状態になる",
                projectListPage.input(ProjectListPage.KEYWORD), "保存確認");
        verify.checked("2. ステータスの「クローズ」にチェックが付いた状態になる",
                projectListPage.input(ProjectListPage.ST_CLOSED));
        verify.opPress("2. 「検索」を押下する（この検索条件が保存される）", projectListPage.searchButton(),
                () -> projectListPage.pressSearch());

        verify.opPress("3. 共通ヘッダーの「ログアウト」を押下する", commonHeader.logoutButton(),
                () -> commonHeader.pressLogout());
        verify.visible("3. ログイン画面が表示される", loginPage.form());

        verify.op("4. ユーザID に SM0001 を入力する", () -> loginPage.fillUserId(GENERAL_USER),
                loginPage.userIdInput());
        verify.op("4. パスワードを入力する", () -> loginPage.fillPassword(PASSWORD),
                loginPage.passwordInput());
        verify.opPress("4. 「ログイン」を押下する", loginPage.loginButton(), () -> loginPage.pressLogin());

        verify.op("5. 「案件検索」アコーディオンを開く", () -> projectListPage.toggleSearchAccordion(),
                projectListPage.searchPanelBody());

        verify.value("「検索キーワード」は空欄である", projectListPage.input(ProjectListPage.KEYWORD), "");
        verify.checked("ステータス「オープン」はチェックありである", projectListPage.input(ProjectListPage.ST_OPEN));
        verify.checked("ステータス「交渉中」はチェックありである",
                projectListPage.input(ProjectListPage.ST_NEGOTIATING));
        verify.unchecked("ステータス「クローズ」はチェックなしである", projectListPage.input(ProjectListPage.ST_CLOSED));
    }

    @Test
    @DisplayName("No.2-1 パスワード表示トグルでパスワードの表示・非表示が切り替わる")
    void case2x1() {
        verify.opOpen("1. / を開く", () -> loginPage.open());
        verify.op("1. パスワード欄に「Passw0rd!」を入力する（初期状態は伏せ字表示）", () -> loginPage.fillPassword(PASSWORD),
                loginPage.passwordInput());
        verify.machineEquals("手順1でパスワード欄の type 属性が password である", "password",
                loginPage.passwordType());
        verify.machineEquals("手順1でパスワード表示トグルの aria-label が「パスワードを表示」である", "パスワードを表示",
                loginPage.passwordToggleAriaLabel());

        verify.opPress("2. パスワード表示トグルを押下する", loginPage.passwordToggle(),
                () -> loginPage.pressPasswordToggle(), loginPage.passwordInput());
        verify.value("2. パスワード欄の入力値が「Passw0rd!」である", loginPage.passwordInput(), PASSWORD);
        verify.machineEquals("手順2でパスワード欄の type 属性が text である", "text", loginPage.passwordType());
        verify.machineEquals("手順2でパスワード表示トグルの aria-label が「パスワードを非表示」である", "パスワードを非表示",
                loginPage.passwordToggleAriaLabel());

        verify.opPress("3. もう一度パスワード表示トグルを押下する", loginPage.passwordToggle(),
                () -> loginPage.pressPasswordToggle(), loginPage.passwordInput());
        verify.machineEquals("手順3でパスワード欄の type 属性が password である", "password",
                loginPage.passwordType());
        verify.machineEquals("手順3でパスワード表示トグルの aria-label が「パスワードを表示」である", "パスワードを表示",
                loginPage.passwordToggleAriaLabel());
    }

    @Test
    @DisplayName("No.3-1 正しいユーザID・パスワードでユーザ認証が成功し、案件情報一覧画面へ遷移する")
    void case3x1() {
        verify.opOpen("1. / を開く", () -> loginPage.open());
        verify.op("2. ユーザID に SM0001 を入力する", () -> loginPage.fillUserId(GENERAL_USER),
                loginPage.userIdInput());
        verify.op("2. パスワードに SM0001 の正しいパスワードを入力する", () -> loginPage.fillPassword(PASSWORD),
                loginPage.passwordInput());
        verify.opPress("3. 「ログイン」を押下する", loginPage.loginButton(), () -> loginPage.pressLogin());

        verify.urlIs("/projects（案件情報一覧画面）へ遷移する", projectListPage.url());
        verify.text("画面タイトル「案件情報一覧画面」が表示される", commonHeader.screenTitle(),
                ProjectListPage.SCREEN_TITLE);
        verify.text("共通ヘッダーにユーザ名「営業 太郎」が表示される", commonHeader.userName(), "営業 太郎");
        verify.containsText("共通ヘッダーに部署名「営業部」が表示される", commonHeader.departmentName(), "営業部");
        verify.absent("入力値チェックのエラーメッセージ・ログイン失敗メッセージは表示されない", commonHeader.screenTitle(),
                loginPage.visibleFieldErrors().or(loginPage.failureMessage()));
    }

    @Test
    @DisplayName("No.3-2 存在しないユーザIDではユーザ認証に失敗し、ログイン失敗メッセージが表示される")
    void case3x2() {
        verify.opOpen("1. / を開く", () -> loginPage.open());
        verify.op("2. ユーザID に SM9999 を入力する", () -> loginPage.fillUserId(UNKNOWN_USER),
                loginPage.userIdInput());
        verify.op("2. パスワードに「Passw0rd!」を入力する", () -> loginPage.fillPassword(PASSWORD),
                loginPage.passwordInput());
        verify.opPress("3. 「ログイン」を押下する", loginPage.loginButton(), () -> loginPage.pressLogin());

        verify.visible("案件情報一覧画面へは遷移せず、ログイン画面が再表示される", loginPage.form());
        verify.text("ログイン失敗メッセージ「ユーザIDかパスワードが間違っています。」が表示される", loginPage.failureMessage(),
                LOGIN_FAILURE);
        verify.absent("ユーザID欄・パスワード欄の直下に入力値チェックのエラーメッセージは表示されない", loginPage.form(),
                loginPage.visibleFieldErrors());
        verify.absent("共通ヘッダーは表示されない（ログインしていない）", loginPage.form(), commonHeader.header());
    }

    @Test
    @DisplayName("No.3-3 実在するユーザIDでパスワードが異なる場合はユーザ認証に失敗する")
    void case3x3() {
        verify.opOpen("1. / を開く", () -> loginPage.open());
        verify.op("2. ユーザID に SM0001 を入力する", () -> loginPage.fillUserId(GENERAL_USER),
                loginPage.userIdInput());
        verify.op("2. パスワードに誤った値「WrongPass1」を入力する", () -> loginPage.fillPassword("WrongPass1"),
                loginPage.passwordInput());
        verify.opPress("3. 「ログイン」を押下する", loginPage.loginButton(), () -> loginPage.pressLogin());

        verify.visible("案件情報一覧画面へは遷移せず、ログイン画面が再表示される", loginPage.form());
        verify.text("ログイン失敗メッセージ「ユーザIDかパスワードが間違っています。」が表示される" + "（ユーザIDの存在有無を区別しない同一のメッセージ）",
                loginPage.failureMessage(), LOGIN_FAILURE);
        verify.absent("ユーザID欄・パスワード欄の直下に入力値チェックのエラーメッセージは表示されない", loginPage.form(),
                loginPage.visibleFieldErrors());
        verify.absent("共通ヘッダーは表示されない（ログインしていない）", loginPage.form(), commonHeader.header());
    }

    @Test
    @DisplayName("No.4-1 【クライアント検証】ユーザIDが未入力の場合、ログインを実施せずエラーメッセージを表示する")
    void case4x1() {
        verify.opOpen("1. / を開く", () -> loginPage.open());
        verify.op("2. パスワードに「Passw0rd!」を入力する（ユーザID は空のまま）", () -> loginPage.fillPassword(PASSWORD),
                loginPage.passwordInput());
        verify.opPress("3. 「ログイン」を押下する", loginPage.loginButton(), () -> loginPage.pressLogin());

        verify.visible("画面遷移せずログイン画面のままである", loginPage.form());
        verify.text("ユーザID欄の直下に「ユーザIDを入力してください。」が表示される", loginPage.userIdError(), USER_ID_REQUIRED);
        verify.absent("パスワード欄の直下にはエラーメッセージが表示されない", loginPage.form(),
                loginPage.passwordErrorWhenShown());
        verify.absent("ログイン失敗メッセージも表示されない（ログイン処理が実施されていない）", loginPage.form(),
                loginPage.failureMessage());
    }

    @Test
    @DisplayName("No.4-2 【クライアント検証】パスワードが未入力の場合、ログインを実施せずエラーメッセージを表示する")
    void case4x2() {
        verify.opOpen("1. / を開く", () -> loginPage.open());
        verify.op("2. ユーザID に SM0001 を入力する（パスワードは空のまま）", () -> loginPage.fillUserId(GENERAL_USER),
                loginPage.userIdInput());
        verify.opPress("3. 「ログイン」を押下する", loginPage.loginButton(), () -> loginPage.pressLogin());

        verify.visible("画面遷移せずログイン画面のままである", loginPage.form());
        verify.text("パスワード欄の直下に「パスワードを入力してください。」が表示される", loginPage.passwordError(),
                PASSWORD_REQUIRED);
        verify.absent("ユーザID欄の直下にはエラーメッセージが表示されない", loginPage.form(),
                loginPage.userIdErrorWhenShown());
        verify.absent("ログイン失敗メッセージも表示されない（ログイン処理が実施されていない）", loginPage.form(),
                loginPage.failureMessage());
    }

    @Test
    @DisplayName("No.4-3 【クライアント検証】ユーザID・パスワードが同時に未入力の場合、両方の項目にエラーメッセージを表示する")
    void case4x3() {
        verify.opOpen("1. / を開く", () -> loginPage.open());
        verify.opPress("2. ユーザID・パスワードをいずれも空のまま「ログイン」を押下する", loginPage.loginButton(),
                () -> loginPage.pressLogin());

        verify.visible("画面遷移せずログイン画面のままである", loginPage.form());
        verify.text("ユーザID欄の直下に「ユーザIDを入力してください。」が表示される", loginPage.userIdError(), USER_ID_REQUIRED);
        verify.text("パスワード欄の直下に「パスワードを入力してください。」が同時に表示される", loginPage.passwordError(),
                PASSWORD_REQUIRED);
    }

    @Test
    @DisplayName("No.4-4 【クライアント検証】【境界値】ユーザID 60文字は最大文字数エラーにならない")
    void case4x4() {
        verify.opOpen("1. / を開く", () -> loginPage.open());
        verify.op("2. ユーザID に半角英字60文字（a を60回）を入力する", () -> loginPage.fillUserId("a".repeat(60)),
                loginPage.userIdInput());
        verify.op("3. パスワードに「Passw0rd!」を入力する", () -> loginPage.fillPassword(PASSWORD),
                loginPage.passwordInput());
        verify.opPress("4. 「ログイン」を押下する", loginPage.loginButton(), () -> loginPage.pressLogin());

        verify.text("入力値チェックを通過してログイン処理が実施され、該当するユーザが存在しないため" + "ログイン失敗メッセージが表示される",
                loginPage.failureMessage(), LOGIN_FAILURE);
        verify.absent("ユーザID欄の直下に「60文字以内で入力してください。」は表示されない", loginPage.form(),
                loginPage.userIdErrorWhenShown());
    }

    @Test
    @DisplayName("No.4-5 【クライアント検証】【境界値】ユーザID 61文字は最大文字数エラーになる")
    void case4x5() {
        verify.opOpen("1. / を開く", () -> loginPage.open());
        verify.opBypassRemoveAttribute("2. ユーザID入力欄の maxlength 属性を解除する", loginPage.userIdInput(),
                "maxlength");
        verify.op("3. ユーザID に半角英字61文字（a を61回）を入力する", () -> loginPage.fillUserId("a".repeat(61)),
                loginPage.userIdInput());
        verify.op("4. パスワードに「Passw0rd!」を入力する", () -> loginPage.fillPassword(PASSWORD),
                loginPage.passwordInput());
        verify.opPress("5. 「ログイン」を押下する", loginPage.loginButton(), () -> loginPage.pressLogin());

        verify.visible("画面遷移せずログイン画面のままである", loginPage.form());
        verify.text("ユーザID欄の直下に表示されるのは「60文字以内で入力してください。」のみである" + "（「使用不可能な文字が含まれています。」は表示されない）",
                loginPage.userIdError(), MAX_LENGTH_ERROR);
        verify.absent("ログイン失敗メッセージも表示されない（ログイン処理が実施されていない）", loginPage.form(),
                loginPage.failureMessage());
    }

    @Test
    @DisplayName("No.4-6 【クライアント検証】ユーザIDに半角英数字以外を含む場合、使用文字エラーになる")
    void case4x6() {
        verify.opOpen("1. / を開く", () -> loginPage.open());
        verify.op("2. ユーザID に「sm-0001」（半角ハイフンを含む）を入力する", () -> loginPage.fillUserId("sm-0001"),
                loginPage.userIdInput());
        verify.op("3. パスワードに「Passw0rd!」を入力する", () -> loginPage.fillPassword(PASSWORD),
                loginPage.passwordInput());
        verify.opPress("4. 「ログイン」を押下する", loginPage.loginButton(), () -> loginPage.pressLogin());

        verify.visible("画面遷移せずログイン画面のままである", loginPage.form());
        verify.text("ユーザID欄の直下に「使用不可能な文字が含まれています。」が表示される", loginPage.userIdError(),
                ILLEGAL_CHARACTER_ERROR);
        verify.absent("パスワード欄の直下にはエラーメッセージが表示されない", loginPage.form(),
                loginPage.passwordErrorWhenShown());
        verify.absent("ログイン失敗メッセージも表示されない（ログイン処理が実施されていない）", loginPage.form(),
                loginPage.failureMessage());
    }

    @Test
    @DisplayName("No.4-7 【クライアント検証】【境界値】パスワード 60文字は最大文字数エラーにならない")
    void case4x7() {
        verify.opOpen("1. / を開く", () -> loginPage.open());
        verify.op("2. ユーザID に SM0001 を入力する", () -> loginPage.fillUserId(GENERAL_USER),
                loginPage.userIdInput());
        verify.op("3. パスワードに半角英数字60文字（a を59回＋1）を入力する",
                () -> loginPage.fillPassword("a".repeat(59) + "1"), loginPage.passwordInput());
        verify.opPress("4. 「ログイン」を押下する", loginPage.loginButton(), () -> loginPage.pressLogin());

        verify.text("入力値チェックを通過してログイン処理が実施され、パスワードが一致しないため" + "ログイン失敗メッセージが表示される",
                loginPage.failureMessage(), LOGIN_FAILURE);
        verify.absent("パスワード欄の直下に「60文字以内で入力してください。」は表示されない", loginPage.form(),
                loginPage.passwordErrorWhenShown());
    }

    @Test
    @DisplayName("No.4-8 【クライアント検証】【境界値】パスワード 61文字は最大文字数エラーになる")
    void case4x8() {
        verify.opOpen("1. / を開く", () -> loginPage.open());
        verify.op("2. ユーザID に SM0001 を入力する", () -> loginPage.fillUserId(GENERAL_USER),
                loginPage.userIdInput());
        verify.opBypassRemoveAttribute("3. パスワード入力欄の maxlength 属性を解除する", loginPage.passwordInput(),
                "maxlength");
        verify.op("4. パスワードに半角英数字61文字（a を60回＋1）を入力する",
                () -> loginPage.fillPassword("a".repeat(60) + "1"), loginPage.passwordInput());
        verify.opPress("5. 「ログイン」を押下する", loginPage.loginButton(), () -> loginPage.pressLogin());

        verify.visible("画面遷移せずログイン画面のままである", loginPage.form());
        verify.text("パスワード欄の直下に「60文字以内で入力してください。」が表示される", loginPage.passwordError(),
                MAX_LENGTH_ERROR);
        verify.absent("ユーザID欄の直下にはエラーメッセージが表示されない", loginPage.form(),
                loginPage.userIdErrorWhenShown());
        verify.absent("ログイン失敗メッセージも表示されない（ログイン処理が実施されていない）", loginPage.form(),
                loginPage.failureMessage());
    }

    @Test
    @DisplayName("No.4-9 【クライアント検証】パスワードに半角英数字・半角記号以外を含む場合、使用文字エラーになる")
    void case4x9() {
        verify.opOpen("1. / を開く", () -> loginPage.open());
        verify.op("2. ユーザID に SM0001 を入力する", () -> loginPage.fillUserId(GENERAL_USER),
                loginPage.userIdInput());
        verify.op("3. パスワードに「パスワード1」（全角文字を含む）を入力する", () -> loginPage.fillPassword("パスワード1"),
                loginPage.passwordInput());
        verify.opPress("4. 「ログイン」を押下する", loginPage.loginButton(), () -> loginPage.pressLogin());

        verify.visible("画面遷移せずログイン画面のままである", loginPage.form());
        verify.text("パスワード欄の直下に「使用不可能な文字が含まれています。」が表示される", loginPage.passwordError(),
                ILLEGAL_CHARACTER_ERROR);
        verify.absent("ユーザID欄の直下にはエラーメッセージが表示されない", loginPage.form(),
                loginPage.userIdErrorWhenShown());
        verify.absent("ログイン失敗メッセージも表示されない（ログイン処理が実施されていない）", loginPage.form(),
                loginPage.failureMessage());
    }

    @Test
    @DisplayName("No.4-10 【クライアント検証】入力値チェック違反後に再度ログインを実施すると、既存のエラーメッセージが削除されてから再チェックされる")
    void case4x10() {
        verify.opOpen("1. / を開く", () -> loginPage.open());
        verify.opPress("1. ユーザID・パスワードをいずれも空のまま「ログイン」を押下する", loginPage.loginButton(),
                () -> loginPage.pressLogin());

        verify.text("2. ユーザID欄の直下に「ユーザIDを入力してください。」が表示される", loginPage.userIdError(),
                USER_ID_REQUIRED);
        verify.text("2. パスワード欄の直下に「パスワードを入力してください。」が表示される", loginPage.passwordError(),
                PASSWORD_REQUIRED);

        verify.op("3. ユーザID に SM0001 を入力する", () -> loginPage.fillUserId(GENERAL_USER),
                loginPage.userIdInput());
        verify.transitionClearedByPress("ユーザID欄の直下にエラーメッセージが表示されている",
                loginPage.userIdErrorWhenShown(), "3. パスワードは空のまま「ログイン」を押下する",
                loginPage.loginButton(), () -> loginPage.pressLogin(),
                loginPage.passwordErrorWhenShown(), "ユーザID欄の直下のエラーメッセージが消える");

        verify.text("パスワード欄の直下の「パスワードを入力してください。」だけが表示される", loginPage.passwordError(),
                PASSWORD_REQUIRED);
    }
}
