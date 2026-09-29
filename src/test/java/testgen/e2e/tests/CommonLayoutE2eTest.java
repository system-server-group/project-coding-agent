package testgen.e2e.tests;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import testgen.e2e.ProjectE2eTest;
import testgen.e2e.pages.CommonHeaderPage;
import testgen.e2e.pages.CompanyMasterPage;
import testgen.e2e.pages.DepartmentMasterPage;
import testgen.e2e.pages.ErrorPage;
import testgen.e2e.pages.LoginPage;
import testgen.e2e.pages.ProjectListPage;
import testgen.e2e.pages.ProjectRegisterPage;
import testgen.e2e.pages.UserMasterPage;
import testgen.e2e.support.E2eFeature;
import testgen.e2e.support.evidence.EvidenceV2;

/**
 * 共通レイアウト（機能 {@code 01_共通レイアウト}）の E2E テスト。
 *
 * <p>
 * テストケース仕様書 {@code 01_共通レイアウト/01_共通レイアウト_テスト仕様書.xlsx} の全ケースを実装する。
 */
@EvidenceV2
@E2eFeature("01_共通レイアウト")
class CommonLayoutE2eTest extends ProjectE2eTest {

    /** 一般ユーザ SM0001（営業 太郎／営業部）。 */
    private static final String GENERAL_USER = "SM0001";

    /** システム管理者 SM0002（管理 花子／管理部）。 */
    private static final String ADMIN_USER = "SM0002";

    /** 部署マスタに存在しない部署ID を持つユーザ SM0009（部署なし 四郎）。 */
    private static final String NO_DEPARTMENT_USER = "SM0009";

    /** ベースラインのユーザに共通の開発用ダミーパスワード。 */
    private static final String PASSWORD = "Passw0rd!";

    private LoginPage loginPage;
    private CommonHeaderPage commonHeader;
    private ProjectListPage projectListPage;
    private ProjectRegisterPage projectRegisterPage;
    private CompanyMasterPage companyMasterPage;
    private UserMasterPage userMasterPage;
    private DepartmentMasterPage departmentMasterPage;
    private ErrorPage errorPage;


    @BeforeEach
    void setUpPages() {
        loginPage = new LoginPage(page, baseUrl());
        commonHeader = new CommonHeaderPage(page, baseUrl());
        projectListPage = new ProjectListPage(page, baseUrl());
        projectRegisterPage = new ProjectRegisterPage(page, baseUrl());
        companyMasterPage = new CompanyMasterPage(page, baseUrl());
        userMasterPage = new UserMasterPage(page, baseUrl());
        departmentMasterPage = new DepartmentMasterPage(page, baseUrl());
        errorPage = new ErrorPage(page, baseUrl());
    }

    @Test
    @DisplayName("No.1-1 一般ユーザの共通ヘッダーに項目が表示され、マスタ管理のナビゲーションは表示されない")
    void case1x1() {
        loginPage.signIn(GENERAL_USER, PASSWORD, ProjectListPage.PATH);

        verify.opOpen("1. /projects（案件情報一覧画面）を開く", () -> projectListPage.open());

        verify.text("共通ヘッダーのシステムタイトルに「営業情報管理システム」が表示される", commonHeader.systemTitle(), "営業情報管理システム");
        verify.text("共通ヘッダーにユーザ名「営業 太郎」が表示される", commonHeader.userName(), "営業 太郎");
        verify.containsText("共通ヘッダーに部署名「営業部」が表示される", commonHeader.departmentName(), "営業部");
        verify.text("共通ヘッダーにナビゲーション「一覧・検索」が表示される", commonHeader.nav(CommonHeaderPage.NAV_LIST),
                CommonHeaderPage.NAV_LIST);
        verify.text("共通ヘッダーにナビゲーション「新規登録」が表示される", commonHeader.nav(CommonHeaderPage.NAV_REGISTER),
                CommonHeaderPage.NAV_REGISTER);
        verify.text("共通ヘッダーに「ログアウト」が表示される", commonHeader.logoutButton(), "ログアウト");
        verify.positionedAbove("共通ヘッダーの下にメインビューポートがある", commonHeader.header(),
                commonHeader.mainViewport());
        verify.text("メインビューポートに案件情報一覧画面の内容（画面タイトル）が表示される", commonHeader.screenTitle(),
                ProjectListPage.SCREEN_TITLE);
        verify.absent("ナビゲーション「会社マスタ」「ユーザマスタ」「部署マスタ」が表示されない", commonHeader.projectNavGroup(),
                commonHeader.masterNavButtons());
    }

    @Test
    @DisplayName("No.1-2 システム管理者の共通ヘッダーにマスタ管理のナビゲーションが表示される")
    void case1x2() {
        loginPage.signIn(ADMIN_USER, PASSWORD, ProjectListPage.PATH);

        verify.opOpen("1. /projects（案件情報一覧画面）を開く", () -> projectListPage.open());

        verify.text("共通ヘッダーのシステムタイトルに「営業情報管理システム」が表示される", commonHeader.systemTitle(), "営業情報管理システム");
        verify.text("共通ヘッダーにユーザ名「管理 花子」が表示される", commonHeader.userName(), "管理 花子");
        verify.containsText("共通ヘッダーに部署名「管理部」が表示される", commonHeader.departmentName(), "管理部");
        verify.text("共通ヘッダーにナビゲーション「一覧・検索」が表示される", commonHeader.nav(CommonHeaderPage.NAV_LIST),
                CommonHeaderPage.NAV_LIST);
        verify.text("共通ヘッダーにナビゲーション「新規登録」が表示される", commonHeader.nav(CommonHeaderPage.NAV_REGISTER),
                CommonHeaderPage.NAV_REGISTER);
        verify.text("共通ヘッダーにナビゲーション「会社マスタ」が表示される", commonHeader.nav(CommonHeaderPage.NAV_COMPANY),
                CommonHeaderPage.NAV_COMPANY);
        verify.text("共通ヘッダーにナビゲーション「ユーザマスタ」が表示される", commonHeader.nav(CommonHeaderPage.NAV_USER),
                CommonHeaderPage.NAV_USER);
        verify.text("共通ヘッダーにナビゲーション「部署マスタ」が表示される",
                commonHeader.nav(CommonHeaderPage.NAV_DEPARTMENT), CommonHeaderPage.NAV_DEPARTMENT);
        verify.text("共通ヘッダーに「ログアウト」が表示される", commonHeader.logoutButton(), "ログアウト");
    }

    @Test
    @DisplayName("No.1-3 メインビューポートに表示中の画面に対応するナビゲーションが強調表示される（案件情報管理）")
    void case1x3() {
        loginPage.signIn(GENERAL_USER, PASSWORD, ProjectListPage.PATH);

        verify.opOpen("1. /projects を開く", () -> projectListPage.open());
        verify.visible("1. 「一覧・検索」が強調表示（active）されている",
                commonHeader.activeNav(CommonHeaderPage.NAV_LIST));
        verify.absent("1. 「新規登録」は強調表示されない", commonHeader.projectNavGroup(),
                commonHeader.activeNav(CommonHeaderPage.NAV_REGISTER));

        verify.opOpen("2. /projects/new を開く", () -> projectRegisterPage.open());
        verify.visible("2. 「新規登録」が強調表示（active）されている",
                commonHeader.activeNav(CommonHeaderPage.NAV_REGISTER));
        verify.absent("2. 「一覧・検索」は強調表示されない", commonHeader.projectNavGroup(),
                commonHeader.activeNav(CommonHeaderPage.NAV_LIST));
    }

    @Test
    @DisplayName("No.1-4 メインビューポートに表示中の画面に対応するナビゲーションが強調表示される（マスタ管理）")
    void case1x4() {
        loginPage.signIn(ADMIN_USER, PASSWORD, ProjectListPage.PATH);

        verify.opOpen("1. /master/companies を開く", () -> companyMasterPage.open());
        verify.visible("1. 「会社マスタ」が強調表示（active）されている",
                commonHeader.activeNav(CommonHeaderPage.NAV_COMPANY));
        verify.absent("1. 「ユーザマスタ」「部署マスタ」は強調表示されない", commonHeader.projectNavGroup(),
                commonHeader.activeNav(CommonHeaderPage.NAV_USER)
                        .or(commonHeader.activeNav(CommonHeaderPage.NAV_DEPARTMENT)));

        verify.opOpen("2. /master/users を開く", () -> userMasterPage.open());
        verify.visible("2. 「ユーザマスタ」が強調表示（active）されている",
                commonHeader.activeNav(CommonHeaderPage.NAV_USER));
        verify.absent("2. 「会社マスタ」「部署マスタ」は強調表示されない", commonHeader.projectNavGroup(),
                commonHeader.activeNav(CommonHeaderPage.NAV_COMPANY)
                        .or(commonHeader.activeNav(CommonHeaderPage.NAV_DEPARTMENT)));

        verify.opOpen("3. /master/departments を開く", () -> departmentMasterPage.open());
        verify.visible("3. 「部署マスタ」が強調表示（active）されている",
                commonHeader.activeNav(CommonHeaderPage.NAV_DEPARTMENT));
        verify.absent("3. 「会社マスタ」「ユーザマスタ」は強調表示されない", commonHeader.projectNavGroup(),
                commonHeader.activeNav(CommonHeaderPage.NAV_COMPANY)
                        .or(commonHeader.activeNav(CommonHeaderPage.NAV_USER)));
    }

    @Test
    @DisplayName("No.2-1 システムタイトルのクリックで案件情報一覧画面へ遷移する")
    void case2x1() {
        loginPage.signIn(GENERAL_USER, PASSWORD, ProjectListPage.PATH);

        verify.opOpen("1. /projects/new（案件情報登録画面）を開く", () -> projectRegisterPage.open());

        verify.opPress("2. 共通ヘッダーのシステムタイトル「営業情報管理システム」をクリックする", commonHeader.systemTitle(),
                () -> commonHeader.clickSystemTitle());

        verify.urlIs("/projects へ遷移する", projectListPage.url());
        verify.text("画面タイトル「案件情報一覧画面」が表示される", commonHeader.screenTitle(),
                ProjectListPage.SCREEN_TITLE);
        verify.visible("共通ヘッダーの「一覧・検索」が強調表示（active）されている",
                commonHeader.activeNav(CommonHeaderPage.NAV_LIST));
    }

    @Test
    @DisplayName("No.2-2 「一覧・検索」「新規登録」のクリックで対応する画面へ遷移する")
    void case2x2() {
        loginPage.signIn(GENERAL_USER, PASSWORD, ProjectListPage.PATH);

        verify.opOpen("1. /projects を開く", () -> projectListPage.open());

        verify.opPress("2. 共通ヘッダーの「新規登録」をクリックする", commonHeader.nav(CommonHeaderPage.NAV_REGISTER),
                () -> commonHeader.clickNav(CommonHeaderPage.NAV_REGISTER));
        verify.urlIs("2. /projects/new へ遷移する", projectRegisterPage.url());
        verify.text("2. 画面タイトル「案件情報登録画面」が表示される", commonHeader.screenTitle(),
                ProjectRegisterPage.SCREEN_TITLE);

        verify.opPress("3. 共通ヘッダーの「一覧・検索」をクリックする", commonHeader.nav(CommonHeaderPage.NAV_LIST),
                () -> commonHeader.clickNav(CommonHeaderPage.NAV_LIST));
        verify.urlIs("3. /projects へ遷移する", projectListPage.url());
        verify.text("3. 画面タイトル「案件情報一覧画面」が表示される", commonHeader.screenTitle(),
                ProjectListPage.SCREEN_TITLE);
    }

    @Test
    @DisplayName("No.2-3 「会社マスタ」「ユーザマスタ」「部署マスタ」のクリックで対応する画面へ遷移する")
    void case2x3() {
        loginPage.signIn(ADMIN_USER, PASSWORD, ProjectListPage.PATH);

        verify.opOpen("1. /projects を開く", () -> projectListPage.open());

        verify.opPress("2. 共通ヘッダーの「会社マスタ」をクリックする", commonHeader.nav(CommonHeaderPage.NAV_COMPANY),
                () -> commonHeader.clickNav(CommonHeaderPage.NAV_COMPANY));
        verify.urlIs("2. /master/companies へ遷移する", companyMasterPage.url());
        verify.text("2. 画面タイトル「会社マスタ管理画面」が表示される", commonHeader.screenTitle(),
                CompanyMasterPage.SCREEN_TITLE);

        verify.opPress("3. 共通ヘッダーの「ユーザマスタ」をクリックする", commonHeader.nav(CommonHeaderPage.NAV_USER),
                () -> commonHeader.clickNav(CommonHeaderPage.NAV_USER));
        verify.urlIs("3. /master/users へ遷移する", userMasterPage.url());
        verify.text("3. 画面タイトル「ユーザマスタ管理画面」が表示される", commonHeader.screenTitle(),
                UserMasterPage.SCREEN_TITLE);

        verify.opPress("4. 共通ヘッダーの「部署マスタ」をクリックする",
                commonHeader.nav(CommonHeaderPage.NAV_DEPARTMENT),
                () -> commonHeader.clickNav(CommonHeaderPage.NAV_DEPARTMENT));
        verify.urlIs("4. /master/departments へ遷移する", departmentMasterPage.url());
        verify.text("4. 画面タイトル「部署マスタ管理画面」が表示される", commonHeader.screenTitle(),
                DepartmentMasterPage.SCREEN_TITLE);

        verify.absent("いずれもエラー画面には遷移しない", commonHeader.screenTitle(), errorPage.card());
    }

    @Test
    @DisplayName("No.3-1 ユーザマスタの部署IDが部署マスタに登録されていない場合、エラー画面に【サーバー内部エラー】が表示される")
    void case3x1() {
        addUsers(unitData("01_共通レイアウト/data/部署未登録ユーザ.csv"));

        verify.opOpen("1. / を開く", () -> loginPage.open());
        verify.op("2. ユーザID に SM0009 を入力する", () -> loginPage.fillUserId(NO_DEPARTMENT_USER),
                loginPage.userIdInput());
        verify.op("2. パスワードに該当ユーザのパスワードを入力する", () -> loginPage.fillPassword(PASSWORD),
                loginPage.passwordInput());

        verify.opPress("3. 「ログイン」を押下する", loginPage.loginButton(), () -> loginPage.pressLogin());

        verify.text("エラータイトル「An Error Occurred」が表示される", errorPage.errorTitle(),
                "An Error Occurred");
        verify.text("エラー内容「エラーが発生しました。」が表示される", errorPage.errorMessage(), "エラーが発生しました。");
        verify.absent("共通ヘッダーは表示されない", errorPage.card(), commonHeader.header());
        verify.absent("案件情報一覧画面の識別要素（画面タイトル「案件情報一覧画面」）が表示されない", errorPage.card(),
                commonHeader.screenTitleOf(ProjectListPage.SCREEN_TITLE));
    }
}
