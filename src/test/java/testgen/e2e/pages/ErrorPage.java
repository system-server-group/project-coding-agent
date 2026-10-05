package testgen.e2e.pages;

import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.options.AriaRole;

/**
 * エラー画面（{@code templates/error/error.html}）の Page Object。
 *
 * <p>
 * エラー画面は共通レイアウトを使わない単独画面で、エラーカードにシステムタイトル・エラータイトル・ エラー内容・リロードボタン・インデックスボタンを持つ。
 */
public class ErrorPage extends AppPage {

    /**
     * @param page 操作対象のページ
     * @param baseUrl 起動中アプリのベース URL
     */
    public ErrorPage(Page page, String baseUrl) {
        super(page, baseUrl);
    }

    /**
     * 指定した URL を直接開く（存在しない URL・権限の無い URL によるエラー画面の再現に用いる）。
     *
     * @param path アプリのパス（先頭スラッシュ付き）
     */
    public void openUrl(String path) {
        navigate(path);
    }

    /**
     * エラーカード（エラー画面の実在要素。「無いこと」の検証で文脈アンカーに用いる）。
     *
     * @return エラーカードのロケータ
     */
    public Locator card() {
        return page.locator(".error-card");
    }

    /**
     * システムタイトル。
     *
     * @return システムタイトルのロケータ
     */
    public Locator systemTitle() {
        return page.locator(".error-brand");
    }

    /**
     * エラータイトル（「404 Not Found」等）。
     *
     * @return エラータイトルのロケータ
     */
    public Locator errorTitle() {
        return page.locator(".error-title");
    }

    /**
     * エラー内容（「ページが見つかりませんでした。」等）。
     *
     * @return エラー内容のロケータ
     */
    public Locator errorMessage() {
        return page.locator(".error-message");
    }

    /**
     * アプリケーション内部の詳細情報（例外クラス名・スタックトレース）を表示している要素。
     *
     * <p>
     * 「内部の詳細情報が表示されない」ことの不在検証に用いる。例外クラス名・スタックトレースの行に
     * 現れる字面（{@code Exception}・{@code Throwable}・{@code
     * at com.system_server}・ {@code java.lang}）のいずれかを含む要素を対象とする。
     *
     * @return 内部の詳細情報を含む要素のロケータ
     */
    public Locator internalDetails() {
        return page.locator("body :text-matches('Exception|Throwable"
                + "|at com\\\\.system_server|java\\\\.lang')");
    }

    /**
     * リロードボタン「RELOAD」。
     *
     * @return リロードボタンのロケータ
     */
    public Locator reloadButton() {
        return page.getByRole(AriaRole.BUTTON,
                new Page.GetByRoleOptions().setName("RELOAD").setExact(true));
    }

    /**
     * インデックスボタン「TOINDEX」。
     *
     * @return インデックスボタンのロケータ
     */
    public Locator toIndexButton() {
        return page.getByRole(AriaRole.LINK,
                new Page.GetByRoleOptions().setName("TOINDEX").setExact(true));
    }

    /** リロードボタンを押下する。 */
    public void pressReload() {
        reloadButton().click();
        page.waitForLoadState();
    }

    /** インデックスボタンを押下する。 */
    public void pressToIndex() {
        toIndexButton().click();
        page.waitForLoadState();
    }
}
