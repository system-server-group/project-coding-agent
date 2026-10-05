package testgen.e2e.pages;

import com.microsoft.playwright.Page;

/**
 * 画面 Page Object の基底。ベース URL の保持と、パスを指定した画面遷移を提供する。
 *
 * <p>
 * Page Object の役割はロケータの提供と操作のみであり、DOM から読み取った値を返して検証材料にしない （読取・期待値との突合・記録は検証部品 {@code E2eVerify}
 * が担う）。
 */
public abstract class AppPage {

    /** 操作対象のページ。 */
    protected final Page page;

    private final String baseUrl;

    /**
     * @param page 操作対象のページ
     * @param baseUrl 起動中アプリのベース URL
     */
    protected AppPage(Page page, String baseUrl) {
        this.page = page;
        this.baseUrl = baseUrl;
    }

    /**
     * 指定したパスを開く。
     *
     * @param path アプリのパス（先頭スラッシュ付き）
     */
    protected void navigate(String path) {
        page.navigate(baseUrl + path);
    }

    /**
     * パスに対応する絶対 URL を返す（{@code urlIs} の期待値に用いる）。
     *
     * @param path アプリのパス（先頭スラッシュ付き）
     * @return 絶対 URL
     */
    public String url(String path) {
        return baseUrl + path;
    }
}
