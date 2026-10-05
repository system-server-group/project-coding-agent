package testgen.e2e.pages;

import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.options.AriaRole;
import testgen.e2e.support.evidence.E2eRawDom;

/**
 * ログイン画面（{@code templates/login/login.html}）の Page Object。
 */
public class LoginPage extends AppPage {

    /** ログイン画面のパス（{@code LoginCtrl} の {@code @GetMapping("/")}）。 */
    public static final String PATH = "/";

    /**
     * @param page 操作対象のページ
     * @param baseUrl 起動中アプリのベース URL
     */
    public LoginPage(Page page, String baseUrl) {
        super(page, baseUrl);
    }

    /** ログイン画面を開く。 */
    public void open() {
        navigate(PATH);
    }

    /**
     * ログイン画面の URL。
     *
     * @return ログイン画面の絶対 URL
     */
    public String url() {
        return url(PATH);
    }

    /**
     * ログインフォーム（ログイン画面の実在要素。「無いこと」の検証で文脈アンカーに用いる）。
     *
     * @return ログインフォームのロケータ
     */
    public Locator form() {
        return page.locator("#loginForm");
    }

    /**
     * システムタイトル。
     *
     * @return システムタイトルのロケータ
     */
    public Locator systemTitle() {
        return page.locator(".login-title");
    }

    /**
     * ユーザID欄。
     *
     * @return ユーザID欄のロケータ
     */
    public Locator userIdInput() {
        return page.locator("#userId");
    }

    /**
     * パスワード欄。
     *
     * @return パスワード欄のロケータ
     */
    public Locator passwordInput() {
        return page.locator("#password");
    }

    /**
     * パスワード表示トグル。
     *
     * @return パスワード表示トグルのロケータ
     */
    public Locator passwordToggle() {
        return page.locator("#passwordToggle");
    }

    /**
     * ログインボタン。
     *
     * @return ログインボタンのロケータ
     */
    public Locator loginButton() {
        return page.getByRole(AriaRole.BUTTON,
                new Page.GetByRoleOptions().setName("ログイン").setExact(true));
    }

    /**
     * 指定したプレースホルダーを持つユーザID欄（プレースホルダーの表示を検証するためのロケータ）。
     *
     * @param placeholder 期待するプレースホルダー
     * @return プレースホルダー条件付きのユーザID欄のロケータ
     */
    public Locator userIdInputWithPlaceholder(String placeholder) {
        return page.locator("#userId[placeholder='" + placeholder + "']");
    }

    /**
     * パスワード欄の {@code type} 属性の実測値。
     *
     * @return {@code type} 属性の値
     */
    @E2eRawDom("03_ログイン 2-1 【機械検証】パスワード欄の type 属性")
    public String passwordType() {
        return passwordInput().getAttribute("type");
    }

    /**
     * パスワード表示トグルの {@code aria-label} 属性の実測値。
     *
     * @return {@code aria-label} 属性の値
     */
    @E2eRawDom("03_ログイン 2-1 【機械検証】パスワード表示トグルの aria-label")
    public String passwordToggleAriaLabel() {
        return passwordToggle().getAttribute("aria-label");
    }

    /**
     * ユーザID欄のラベル。
     *
     * @return ラベルのロケータ
     */
    public Locator userIdLabel() {
        return page.locator("label[for='userId']");
    }

    /**
     * パスワード欄のラベル。
     *
     * @return ラベルのロケータ
     */
    public Locator passwordLabel() {
        return page.locator("label[for='password']");
    }

    /**
     * ログイン失敗メッセージ。
     *
     * @return ログイン失敗メッセージのロケータ
     */
    public Locator failureMessage() {
        return page.locator("#loginFailureMessage");
    }

    /**
     * ユーザID欄の直下に表示される入力値チェックのエラーメッセージ。
     *
     * @return エラーメッセージのロケータ
     */
    public Locator userIdError() {
        return page.locator("#userIdError");
    }

    /**
     * パスワード欄の直下に表示される入力値チェックのエラーメッセージ。
     *
     * @return エラーメッセージのロケータ
     */
    public Locator passwordError() {
        return page.locator("#passwordError");
    }

    /**
     * ユーザID欄の直下に「表示されている」エラーメッセージ（消去・不在の検証に用いる。エラー要素自体は 常に DOM に在り、メッセージが設定されたときだけ内容を持つ）。
     *
     * @return 表示中のユーザIDエラーメッセージのロケータ
     */
    public Locator userIdErrorWhenShown() {
        return page.locator("#userIdError:not(:empty)");
    }

    /**
     * パスワード欄の直下に「表示されている」エラーメッセージ。
     *
     * @return 表示中のパスワードエラーメッセージのロケータ
     */
    public Locator passwordErrorWhenShown() {
        return page.locator("#passwordError:not(:empty)");
    }

    /**
     * ユーザID欄・パスワード欄の直下の、表示されているエラーメッセージ（表示されていないことの検証に用いる）。
     *
     * @return 表示中のエラーメッセージのロケータ
     */
    public Locator visibleFieldErrors() {
        return page.locator("#userIdError:not(:empty), #passwordError:not(:empty)");
    }

    /**
     * ユーザIDを入力する。
     *
     * @param userId 入力するユーザID
     */
    public void fillUserId(String userId) {
        userIdInput().fill(userId);
    }

    /**
     * パスワードを入力する。
     *
     * @param password 入力するパスワード
     */
    public void fillPassword(String password) {
        passwordInput().fill(password);
    }

    /** ログインボタンを押下する。 */
    public void pressLogin() {
        loginButton().click();
        page.waitForLoadState();
    }

    /** パスワード表示トグルを押下する。 */
    public void pressPasswordToggle() {
        passwordToggle().click();
    }

    /**
     * 前提条件としてログインする（証跡フレームを伴わない準備操作。操作手順にログインが含まれるケースは 検証部品の操作フレームで記述する）。
     *
     * @param userId ユーザID
     * @param password パスワード
     * @param landingPath ログイン後に到達するパス（待ち合わせに用いる）
     */
    public void signIn(String userId, String password, String landingPath) {
        open();
        fillUserId(userId);
        fillPassword(password);
        loginButton().click();
        page.waitForURL(url(landingPath));
    }
}
