package testgen.e2e.support;

import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;

/**
 * 偽 SMTP（MailHog）の受信箱 Web UI の Page Object。
 *
 * <p>
 * 送信メールはアプリの画面に現れないため、機械判定だけで済ませず本 UI を実ブラウザで開いて受信内容（件名・宛先・ 本文）を映してから検証する（動画の目視性）。MailHog
 * の画面構造を本クラスに閉じ込め、シナリオには意図のみを書く。 接続先 URL は {@code E2eMailbox#webUiUrl()} が供給する（ポートは Testcontainers
 * の自動割当）。
 *
 * <p>
 * 受信一覧は {@code .msglist-message}、行を選ぶと {@code .preview} に内容が表示される。ヘッダー（From／Subject／To）は
 * 見出し（{@code th}）付きの表で描画されるため、見出しの文言で値を特定する。本文は平文タブ（{@code #preview-plain}）に MIME デコード済みで描画される。
 *
 * <p>
 * 表示されるのは開発用ダミーの宛先・本文のみであり、秘匿情報は扱わない。
 */
public class MailHogPage {

    private final Page page;

    /**
     * 受信箱 Web UI の Page Object を生成する。
     *
     * @param page 操作対象のページ
     */
    public MailHogPage(Page page) {
        this.page = page;
    }

    /**
     * 受信箱（受信一覧）を開く。
     *
     * <p>
     * MailHog の内容ペイン（{@code .content}）は {@code position:fixed; top:51px} で、ハーネスの
     * 情報バー（最前面の固定オーバーレイ）に上端が覆われる。外部 UI のため基底のオーバーレイは
     * レイアウトへ介入しない（{@code E2eBaseTest} の注記）ので、本 Page Object が情報バーの実高だけ
     * 内容ペインを下げ、確認対象（ツールバー・受信一覧・プレビュー）が見切れ検査（フレーム内・
     * 上部情報バー除く）を満たす画角にする。
     *
     * @param webUiUrl 受信箱 Web UI の URL
     * @return このページオブジェクト
     */
    public MailHogPage navigate(String webUiUrl) {
        page.navigate(webUiUrl);
        page.evaluate("""
                () => {
                  const bar = document.getElementById('__e2e_overlay__');
                  const content = document.querySelector('.content');
                  if (bar && content) { content.style.top = (51 + bar.offsetHeight) + 'px'; }
                }""");
        return this;
    }

    /** @return 受信一覧の各行（{@code .msglist-message}）のロケータ（0件＝受信箱が空） */
    public Locator messageRows() {
        return page.locator(".msglist-message");
    }

    /**
     * 受信一覧のツールバー（更新ボタン・ページャ）のロケータを返す。受信が 0 件でも実在するため、
     * 「受信一覧が空である」ことの不在検証（{@code absent}）の文脈アンカーに使う（空の一覧領域は
     * 高さ 0 で可視検証できず、ページ全体は上端が情報バーに覆われ見切れ検査を満たさないため）。
     *
     * @return 受信一覧のツールバー（{@code .toolbar}。一覧表示中は一覧用の 1 件のみが DOM に存在する）
     */
    public Locator listToolbar() {
        return page.locator(".toolbar");
    }

    /**
     * 受信一覧の先頭（最新）のメールを開き、内容を表示する。
     *
     * @return このページオブジェクト
     */
    public MailHogPage openLatest() {
        messageRows().first().click();
        preview().waitFor();
        return this;
    }

    /** @return 表示中メールの内容領域（{@code .preview}）のロケータ */
    public Locator preview() {
        return page.locator(".preview");
    }

    /** @return 表示中メールの件名（ヘッダー表の Subject 行の値）のロケータ */
    public Locator subject() {
        return header("Subject");
    }

    /** @return 表示中メールの送信元（ヘッダー表の From 行の値）のロケータ */
    public Locator from() {
        return header("From");
    }

    /** @return 表示中メールの宛先（ヘッダー表の To 行の値）のロケータ */
    public Locator to() {
        return header("To");
    }

    /** @return 表示中メールの本文（平文タブ {@code #preview-plain}）のロケータ */
    public Locator body() {
        return page.locator("#preview-plain");
    }

    /**
     * ヘッダー表の指定見出しの値（{@code td}）のロケータを返す。
     *
     * @param name ヘッダーの見出し（{@code From}／{@code Subject}／{@code To}）
     * @return 値のロケータ
     */
    private Locator header(String name) {
        return page.locator(".preview .headers tr:has(th:text-is('" + name + "')) td");
    }
}
