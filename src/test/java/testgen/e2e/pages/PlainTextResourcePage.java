package testgen.e2e.pages;

import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;

/**
 * 生テキスト表示（CSS・JavaScript 等の静的リソースをブラウザが素のテキストとして表示した画面）の Page Object。
 *
 * <p>
 * テキスト応答の URL を直接開くと、ブラウザ（Chromium）は本文を 1 つの {@code pre} 要素に包んで
 * 描画する。この画面はアプリのテンプレートではないため専用の構造を持たず、その {@code pre} 要素を「リソースの内容がブラウザに表示される」ことの検証対象として供給する。
 */
public class PlainTextResourcePage {

    private final Page page;

    /**
     * @param page 操作対象のページ
     */
    public PlainTextResourcePage(Page page) {
        this.page = page;
    }

    /** @return 表示中のテキスト内容（{@code body > pre}）のロケータ */
    public Locator content() {
        return page.locator("body > pre");
    }
}
