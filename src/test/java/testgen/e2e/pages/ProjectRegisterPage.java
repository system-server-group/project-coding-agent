package testgen.e2e.pages;

import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;

/**
 * 案件情報登録画面の Page Object。
 *
 * <p>
 * 入力項目のロケータ・入力操作は共通の {@link ProjectFormPage} が持ち、本クラスは登録画面固有の操作
 * （画面を開く・登録・戻る）と、仕様書に明記されたバイパス手順の合成操作を担う。
 */
public class ProjectRegisterPage extends ProjectFormPage {

    /** 案件情報登録画面のパス。 */
    public static final String PATH = "/projects/new";

    /** 案件情報登録画面の画面タイトル。 */
    public static final String SCREEN_TITLE = "案件情報登録画面";

    /**
     * @param page 操作対象のページ
     * @param baseUrl 起動中アプリのベース URL
     */
    public ProjectRegisterPage(Page page, String baseUrl) {
        super(page, baseUrl);
    }

    /** 案件情報登録画面を開く。 */
    public void open() {
        navigate(PATH);
    }

    /**
     * 案件情報登録画面の URL。
     *
     * @return 案件情報登録画面の絶対 URL
     */
    public String url() {
        return url(PATH);
    }

    /**
     * 登録フォーム（画面の実在要素。「無いこと」の検証で文脈アンカーに用いる）。
     *
     * @return 登録フォームのロケータ
     */
    public Locator form() {
        return page.locator("#registerForm");
    }

    /**
     * 登録フォーム内の CSRF トークンの hidden 入力（共通ヘッダーのログアウトフォームにも同名の入力が あるため、登録フォームに限定して解決する）。
     *
     * @return CSRF トークン入力のロケータ
     */
    public Locator csrfTokenInput() {
        return form().locator("input[name='_csrf']");
    }

    /**
     * 「登録」ボタン。
     *
     * @return 登録ボタンのロケータ
     */
    public Locator registerButton() {
        return page.locator("#registerButton");
    }

    /**
     * 画面上部の「戻る」ボタン。
     *
     * @return 画面上部の戻るボタンのロケータ
     */
    public Locator backButtonTop() {
        return page.locator(".btn-wide");
    }

    /**
     * 画面下部の「戻る」ボタン。
     *
     * @return 画面下部の戻るボタンのロケータ
     */
    public Locator backButtonBottom() {
        return page.locator(".table-toolbar a");
    }

    /** 「登録」ボタンを押下する。 */
    public void pressRegister() {
        registerButton().click();
        page.waitForLoadState();
    }

    /** 画面上部の「戻る」ボタンを押下する。 */
    public void pressBackTop() {
        backButtonTop().click();
        page.waitForLoadState();
    }

    /** 画面下部の「戻る」ボタンを押下する。 */
    public void pressBackBottom() {
        backButtonBottom().click();
        page.waitForLoadState();
    }


    /**
     * ブラウザが保持するセッション Cookie を削除する合成操作（仕様書 6-1 の「ブラウザが保持する
     * セッションCookieを削除する（ログインの有効期限が切れた状態にする）」に対応）。
     *
     * <p>
     * 効果は後続フレームに現れないため、呼び出し側は {@link #sessionCookieCount()} の実測を 【機械検証】として記録する。
     */
    public void clearSessionCookies() {
        page.context().clearCookies();
    }

    /**
     * ブラウザが保持するセッション Cookie の件数（合成操作の効果の記録に用いる）。
     *
     * @return セッション Cookie（{@code JSESSIONID}）の件数
     */
    public int sessionCookieCount() {
        return (int) page.context().cookies().stream()
                .filter(cookie -> "JSESSIONID".equals(cookie.name)).count();
    }
}
