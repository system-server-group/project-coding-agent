package testgen.e2e.tests;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import testgen.e2e.ProjectE2eTest;
import testgen.e2e.pages.CommonHeaderPage;
import testgen.e2e.pages.CompanyMasterPage;
import testgen.e2e.pages.ErrorPage;
import testgen.e2e.pages.LoginPage;
import testgen.e2e.pages.ProjectListPage;
import testgen.e2e.support.E2eFeature;
import testgen.e2e.support.evidence.E2eVerify;
import testgen.e2e.support.evidence.EvidenceV2;

/**
 * エラー画面（機能 {@code 02_エラー}）の E2E テスト。
 *
 * <p>
 * テストケース仕様書 {@code 02_エラー/02_エラー_テスト仕様書.xlsx} の全ケースを実装する。
 */
@EvidenceV2
@E2eFeature("02_エラー")
class ErrorScreenE2eTest extends ProjectE2eTest {

    /** 存在しない URL（ページ未検出エラーの再現に用いる）。 */
    private static final String NOT_FOUND_PATH = "/no-such-page";

    /** 一般ユーザ SM0001（営業 太郎／営業部）。 */
    private static final String GENERAL_USER = "SM0001";

    /** ベースラインのユーザに共通の開発用ダミーパスワード。 */
    private static final String PASSWORD = "Passw0rd!";

    private ErrorPage errorPage;
    private LoginPage loginPage;
    private CommonHeaderPage commonHeader;
    private ProjectListPage projectListPage;


    @BeforeEach
    void setUpPages() {
        errorPage = new ErrorPage(page, baseUrl());
        loginPage = new LoginPage(page, baseUrl());
        commonHeader = new CommonHeaderPage(page, baseUrl());
        projectListPage = new ProjectListPage(page, baseUrl());
    }

    @Test
    @DisplayName("No.1-1 ページ未検出エラーの表示内容と、エラー画面の画面項目が表示される")
    void case1x1() {
        loginPage.signIn(GENERAL_USER, PASSWORD, ProjectListPage.PATH);

        verify.opOpen("1. 存在しないURL /no-such-page を開く", () -> errorPage.openUrl(NOT_FOUND_PATH));

        verify.text("システムタイトルに「営業情報管理システム」が表示される", errorPage.systemTitle(), "営業情報管理システム");
        verify.text("エラータイトルに「404 Not Found」が表示される", errorPage.errorTitle(), "404 Not Found");
        verify.text("エラー内容に「ページが見つかりませんでした。」が表示される", errorPage.errorMessage(), "ページが見つかりませんでした。");
        verify.text("リロードボタン「RELOAD」が表示される", errorPage.reloadButton(), "RELOAD");
        verify.text("インデックスボタン「TOINDEX」が表示される", errorPage.toIndexButton(), "TOINDEX");
        verify.absent("共通ヘッダー（ナビゲーション・ユーザ名／部署名・ログアウト）が表示されない", errorPage.card(),
                commonHeader.header());
    }

    @Test
    @DisplayName("No.1-2 権限不足エラーの表示内容")
    void case1x2() {
        loginPage.signIn(GENERAL_USER, PASSWORD, ProjectListPage.PATH);

        verify.opOpen("1. 一般ユーザに許可されていない /master/companies を開く",
                () -> errorPage.openUrl(CompanyMasterPage.PATH));

        verify.text("エラータイトルが「403 Forbidden」である（「404 Not Found」「An Error Occurred」ではない）",
                errorPage.errorTitle(), "403 Forbidden");
        verify.text("エラー内容に「ページにアクセスできません。」が表示される", errorPage.errorMessage(), "ページにアクセスできません。");
        verify.text("システムタイトルに「営業情報管理システム」が表示される", errorPage.systemTitle(), "営業情報管理システム");
        verify.text("リロードボタン「RELOAD」が表示される", errorPage.reloadButton(), "RELOAD");
        verify.text("インデックスボタン「TOINDEX」が表示される", errorPage.toIndexButton(), "TOINDEX");
    }

    @Test
    @DisplayName("No.1-3 サーバー内部エラー（404・403 以外のエラー）の表示内容")
    void case1x3() {
        addUsers(unitData("01_共通レイアウト/data/部署未登録ユーザ.csv"));

        verify.opOpen("1. / を開く", () -> loginPage.open());
        verify.op("2. ユーザID に SM0009 を入力する", () -> loginPage.fillUserId("SM0009"),
                loginPage.userIdInput());
        verify.op("2. パスワードに該当ユーザのパスワードを入力する", () -> loginPage.fillPassword(PASSWORD),
                loginPage.passwordInput());
        verify.opPress("2. 「ログイン」を押下する", loginPage.loginButton(), () -> loginPage.pressLogin());

        verify.text("エラータイトルが「An Error Occurred」である（「404 Not Found」「403 Forbidden」ではない）",
                errorPage.errorTitle(), "An Error Occurred");
        verify.text("エラー内容に「エラーが発生しました。」が表示される", errorPage.errorMessage(), "エラーが発生しました。");
        verify.text("システムタイトルに「営業情報管理システム」が表示される", errorPage.systemTitle(), "営業情報管理システム");
        verify.text("リロードボタン「RELOAD」が表示される", errorPage.reloadButton(), "RELOAD");
        verify.text("インデックスボタン「TOINDEX」が表示される", errorPage.toIndexButton(), "TOINDEX");
    }

    @Test
    @DisplayName("No.1-4 ページ未検出エラーはログイン状態に関わらず表示される")
    void case1x4() {
        verify.opOpen("1. 未認証の状態で存在しないURL /no-such-page を開く",
                () -> errorPage.openUrl(NOT_FOUND_PATH));

        verify.urlIs("ログイン画面へは誘導されず URL は /no-such-page のままである", errorPage.url(NOT_FOUND_PATH));
        verify.text("エラータイトルに「404 Not Found」が表示される", errorPage.errorTitle(), "404 Not Found");
        verify.text("エラー内容に「ページが見つかりませんでした。」が表示される", errorPage.errorMessage(), "ページが見つかりませんでした。");
        verify.text("システムタイトルに「営業情報管理システム」が表示される", errorPage.systemTitle(), "営業情報管理システム");
        verify.absent("ログイン画面の識別要素（ユーザID欄・「ログイン」ボタン）が表示されない", errorPage.card(),
                loginPage.userIdInput().or(loginPage.loginButton()));
    }

    @Test
    @DisplayName("No.1-5 ステータス指定URL /error/403 を直接開くと権限不足エラーが表示され、応答のHTTPステータスも403になる")
    void case1x5() {
        loginPage.signIn(GENERAL_USER, PASSWORD, ProjectListPage.PATH);

        E2eVerify.CapturedResponse response = verify.captureResponse("応答を捕捉する", "GET", "/error/403",
                () -> verify.opOpen("1. エラー画面のURL /error/403 を開く",
                        () -> errorPage.openUrl("/error/403")));
        verify.responseStatusIs("【機械検証】/error/403 のHTTP応答が 403 である", response, 403);

        verify.text("エラー画面のエラータイトルが「403 Forbidden」である（「404 Not Found」「An Error Occurred」ではない）",
                errorPage.errorTitle(), "403 Forbidden");
        verify.text("エラー内容に「ページにアクセスできません。」が表示される", errorPage.errorMessage(), "ページにアクセスできません。");
    }

    @Test
    @DisplayName("No.1-6 ステータス指定URL /error/404 を直接開くとページ未検出エラーが表示され、応答のHTTPステータスも404になる")
    void case1x6() {
        loginPage.signIn(GENERAL_USER, PASSWORD, ProjectListPage.PATH);

        E2eVerify.CapturedResponse response = verify.captureResponse("応答を捕捉する", "GET", "/error/404",
                () -> verify.opOpen("1. エラー画面のURL /error/404 を開く",
                        () -> errorPage.openUrl("/error/404")));
        verify.responseStatusIs("【機械検証】/error/404 のHTTP応答が 404 である", response, 404);

        verify.text("エラー画面のエラータイトルが「404 Not Found」である（「403 Forbidden」「An Error Occurred」ではない）",
                errorPage.errorTitle(), "404 Not Found");
        verify.text("エラー内容に「ページが見つかりませんでした。」が表示される", errorPage.errorMessage(), "ページが見つかりませんでした。");
    }

    @Test
    @DisplayName("No.1-7 ステータス指定URLの403・404以外の指定（500）はサーバー内部エラーとして表示され、応答のHTTPステータスは500になる")
    void case1x7() {
        loginPage.signIn(GENERAL_USER, PASSWORD, ProjectListPage.PATH);

        E2eVerify.CapturedResponse response = verify.captureResponse("応答を捕捉する", "GET", "/error/500",
                () -> verify.opOpen("1. エラー画面のURL /error/500 を開く",
                        () -> errorPage.openUrl("/error/500")));
        verify.responseStatusIs("【機械検証】/error/500 のHTTP応答が 500 である", response, 500);

        verify.text("エラー画面のエラータイトルが「An Error Occurred」である（「403 Forbidden」「404 Not Found」ではない）",
                errorPage.errorTitle(), "An Error Occurred");
        verify.text("エラー内容に「エラーが発生しました。」が表示される", errorPage.errorMessage(), "エラーが発生しました。");
    }

    @Test
    @DisplayName("No.1-8 エラー画面のURL /error（ステータス指定なし）は直接アクセスでき、ユーザー通知を目的としたエラー画面の表示のみを行う")
    void case1x8() {
        loginPage.signIn(GENERAL_USER, PASSWORD, ProjectListPage.PATH);

        verify.opOpen("1. エラー画面のURL /error を開く", () -> errorPage.openUrl("/error"));

        verify.text("エラー画面のエラータイトルに「An Error Occurred」が表示される", errorPage.errorTitle(),
                "An Error Occurred");
        verify.text("エラー内容に「エラーが発生しました。」が表示される", errorPage.errorMessage(), "エラーが発生しました。");
        verify.text("システムタイトルに「営業情報管理システム」が表示される", errorPage.systemTitle(), "営業情報管理システム");
        verify.text("リロードボタン「RELOAD」が表示される", errorPage.reloadButton(), "RELOAD");
        verify.text("インデックスボタン「TOINDEX」が表示される", errorPage.toIndexButton(), "TOINDEX");
        verify.urlIs("業務画面へは遷移せず、URL は /error のままである", errorPage.url("/error"));
    }

    @Test
    @DisplayName("No.2-1 リロードボタンで現在表示中のエラー画面が再読み込みされる")
    void case2x1() {
        loginPage.signIn(GENERAL_USER, PASSWORD, ProjectListPage.PATH);

        verify.opOpen("1. 存在しないURL /no-such-page を開く", () -> errorPage.openUrl(NOT_FOUND_PATH));
        verify.text("1. エラータイトル「404 Not Found」が表示されている", errorPage.errorTitle(), "404 Not Found");

        verify.opPress("2. 「RELOAD」を押下する", errorPage.reloadButton(), () -> errorPage.pressReload());

        verify.text("エラータイトル「404 Not Found」が変わらず表示される", errorPage.errorTitle(), "404 Not Found");
        verify.text("エラー内容「ページが見つかりませんでした。」が変わらず表示される", errorPage.errorMessage(),
                "ページが見つかりませんでした。");
        verify.urlIs("URL は /no-such-page のままで、他の画面へは遷移しない", errorPage.url(NOT_FOUND_PATH));
    }

    @Test
    @DisplayName("No.2-2 インデックスボタンでログイン済み時は案件情報一覧画面へ遷移する")
    void case2x2() {
        loginPage.signIn(GENERAL_USER, PASSWORD, ProjectListPage.PATH);

        verify.opOpen("1. 存在しないURL /no-such-page を開く", () -> errorPage.openUrl(NOT_FOUND_PATH));
        verify.visible("1. エラー画面（エラーカード）が表示されている", errorPage.card());

        verify.opPress("2. 「TOINDEX」を押下する", errorPage.toIndexButton(),
                () -> errorPage.pressToIndex());

        verify.urlIs("/projects（案件情報一覧画面）へ遷移する", projectListPage.url());
        verify.text("画面タイトル「案件情報一覧画面」が表示される", commonHeader.screenTitle(),
                ProjectListPage.SCREEN_TITLE);
        verify.text("共通ヘッダーにユーザ名「営業 太郎」が表示される", commonHeader.userName(), "営業 太郎");
        verify.absent("ログイン画面は表示されない", commonHeader.screenTitle(), loginPage.form());
    }

    @Test
    @DisplayName("No.2-3 インデックスボタンで未ログイン時はログイン画面へ遷移する")
    void case2x3() {
        verify.opOpen("1. 未認証の状態で存在しないURL /no-such-page を開く",
                () -> errorPage.openUrl(NOT_FOUND_PATH));
        verify.visible("1. エラー画面（エラーカード）が表示されている", errorPage.card());

        verify.opPress("2. 「TOINDEX」を押下する", errorPage.toIndexButton(),
                () -> errorPage.pressToIndex());

        verify.visible("ログイン画面が表示される", loginPage.form());
        verify.visible("ユーザID欄が表示される", loginPage.userIdInput());
        verify.visible("パスワード欄が表示される", loginPage.passwordInput());
        verify.text("「ログイン」ボタンが表示される", loginPage.loginButton(), "ログイン");
        verify.absent("案件情報一覧画面の識別要素（画面タイトル「案件情報一覧画面」）が表示されない", loginPage.form(),
                commonHeader.screenTitleOf(ProjectListPage.SCREEN_TITLE));
    }
}
