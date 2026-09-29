package testgen.e2e.pages;

import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.options.AriaRole;

/**
 * 共通レイアウト（{@code templates/layout/common.html}）の共通ヘッダー・画面タイトルの Page Object。
 *
 * <p>
 * 共通ヘッダーは案件情報一覧・登録・更新、各マスタ管理画面に共通して現れるため、画面ごとの Page Object とは別に本クラスへ集約する。
 */
public class CommonHeaderPage extends AppPage {

    /** ナビゲーション「一覧・検索」のラベル。 */
    public static final String NAV_LIST = "一覧・検索";

    /** ナビゲーション「新規登録」のラベル。 */
    public static final String NAV_REGISTER = "新規登録";

    /** ナビゲーション「会社マスタ」のラベル。 */
    public static final String NAV_COMPANY = "会社マスタ";

    /** ナビゲーション「ユーザマスタ」のラベル。 */
    public static final String NAV_USER = "ユーザマスタ";

    /** ナビゲーション「部署マスタ」のラベル。 */
    public static final String NAV_DEPARTMENT = "部署マスタ";

    /**
     * @param page 操作対象のページ
     * @param baseUrl 起動中アプリのベース URL
     */
    public CommonHeaderPage(Page page, String baseUrl) {
        super(page, baseUrl);
    }

    /**
     * 共通ヘッダー全体。
     *
     * @return 共通ヘッダーのロケータ
     */
    public Locator header() {
        return page.locator(".app-header");
    }

    /**
     * 共通レイアウトが保持する CSRF トークンの meta 要素（{@code meta[name="_csrf"]}）。
     *
     * <p>
     * クライアントJSが API 要求時に読み取って送信するトークンの格納元。CSRF トークン不一致ケースの
     * バイパス改変（{@code opBypassSetAttribute}）の対象として用いる。
     *
     * @return CSRF トークン meta 要素のロケータ
     */
    public Locator csrfTokenMeta() {
        return page.locator("head meta[name='_csrf']");
    }

    /**
     * システムタイトル（案件情報一覧画面へのリンク）。
     *
     * @return システムタイトルのロケータ
     */
    public Locator systemTitle() {
        return page.locator(".app-title");
    }

    /**
     * ユーザ名。
     *
     * @return ユーザ名のロケータ
     */
    public Locator userName() {
        return page.locator(".user-name");
    }

    /**
     * 部署名（表示は区切り文字「／」に続けて部署名が描画される）。
     *
     * @return 部署名のロケータ
     */
    public Locator departmentName() {
        return page.locator(".user-dept");
    }

    /**
     * ナビゲーションボタン。
     *
     * @param label ボタンのラベル
     * @return ナビゲーションボタンのロケータ
     */
    public Locator nav(String label) {
        return page.locator(".app-nav .nav-btn:text-is('" + label + "')");
    }

    /**
     * 強調表示（active）されているナビゲーションボタン。
     *
     * @param label ボタンのラベル
     * @return 強調表示されているボタンのロケータ
     */
    public Locator activeNav(String label) {
        return page.locator(".app-nav .nav-btn.active:text-is('" + label + "')");
    }

    /**
     * 案件情報のナビゲーション群（「一覧・検索」「新規登録」を含む実在要素。マスタ管理の ナビゲーションが「無いこと」の検証で文脈アンカーに用いる）。
     *
     * @return 案件情報ナビゲーション群のロケータ
     */
    public Locator projectNavGroup() {
        // setHas の内側ロケータは外側の要素に対する相対指定のため、.app-nav を前置しない。
        return page.locator(".app-nav .nav-group").filter(new Locator.FilterOptions()
                .setHas(page.locator(".nav-btn:text-is('" + NAV_LIST + "')")));
    }

    /**
     * マスタ管理のナビゲーション（会社・ユーザ・部署をまとめて指す）。
     *
     * @return マスタ管理ナビゲーションのロケータ
     */
    public Locator masterNavButtons() {
        return page.locator(".app-nav .nav-btn:text-is('" + NAV_COMPANY + "')," + " .app-nav"
                + " .nav-btn:text-is('" + NAV_USER + "')," + " .app-nav .nav-btn:text-is('"
                + NAV_DEPARTMENT + "')");
    }

    /**
     * ログアウトボタン。
     *
     * @return ログアウトボタンのロケータ
     */
    public Locator logoutButton() {
        return page.getByRole(AriaRole.BUTTON,
                new Page.GetByRoleOptions().setName("ログアウト").setExact(true));
    }

    /**
     * 画面タイトル（共通ヘッダー直下に表示される、表示中の画面の名称）。
     *
     * @return 画面タイトルのロケータ
     */
    public Locator screenTitle() {
        return page.locator(".screen-title");
    }

    /**
     * 指定した名称の画面タイトル（その画面に居ることの識別要素）。
     *
     * @param title 画面タイトルの名称
     * @return 画面タイトルのロケータ
     */
    public Locator screenTitleOf(String title) {
        return page.locator(".screen-title").filter(new Locator.FilterOptions().setHasText(title));
    }

    /**
     * メインビューポート（共通ヘッダーの下に置かれる、画面ごとの内容領域）。
     *
     * @return メインビューポートのロケータ
     */
    public Locator mainViewport() {
        return page.locator(".app-content");
    }

    /** システムタイトルをクリックする。 */
    public void clickSystemTitle() {
        systemTitle().click();
        page.waitForLoadState();
    }

    /**
     * ナビゲーションボタンをクリックする。
     *
     * @param label ボタンのラベル
     */
    public void clickNav(String label) {
        nav(label).click();
        page.waitForLoadState();
    }

    /** ログアウトボタンを押下する。 */
    public void pressLogout() {
        logoutButton().click();
        page.waitForLoadState();
    }
}
