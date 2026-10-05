package testgen.e2e.pages;

import com.microsoft.playwright.Download;
import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.options.AriaRole;
import com.microsoft.playwright.options.WaitForSelectorState;
import testgen.e2e.support.evidence.E2eRawDom;

/**
 * 案件情報更新画面の Page Object。
 *
 * <p>
 * 入力項目のロケータ・入力操作は共通の {@link ProjectFormPage} が持つ。本クラスは更新画面固有の要素
 * （登録済み添付ファイル一覧・登録者／更新者等のメタ情報・更新／戻る）を担う。
 */
public class ProjectEditPage extends ProjectFormPage {

    /** 案件情報更新画面の画面タイトル。 */
    public static final String SCREEN_TITLE = "案件情報更新画面";

    /**
     * @param page 操作対象のページ
     * @param baseUrl 起動中アプリのベース URL
     */
    public ProjectEditPage(Page page, String baseUrl) {
        super(page, baseUrl);
    }

    /**
     * 案件情報更新画面のパス。
     *
     * @param managementCode 管理コード
     * @return パス
     */
    public static String pathOf(int managementCode) {
        return "/projects/detail/" + managementCode;
    }

    /**
     * 案件情報更新画面を開く。
     *
     * @param managementCode 管理コード
     */
    public void open(int managementCode) {
        navigate(pathOf(managementCode));
    }

    /**
     * 案件情報更新画面の URL。
     *
     * @param managementCode 管理コード
     * @return 絶対 URL
     */
    public String url(int managementCode) {
        return url(pathOf(managementCode));
    }

    /**
     * 更新フォーム（画面の実在要素。「無いこと」の検証で文脈アンカーに用いる）。
     *
     * @return 更新フォームのロケータ
     */
    public Locator form() {
        return page.locator("#editForm");
    }

    /**
     * 登録済みの添付ファイル一覧（0件のときは要素ごと表示されない）。
     *
     * @return 添付ファイル一覧のロケータ
     */
    public Locator attachmentList() {
        return page.locator("#attachmentList");
    }

    /**
     * 登録済みの添付ファイル一覧の行。
     *
     * @return 添付ファイル行のロケータ
     */
    public Locator attachmentRows() {
        return page.locator("#attachmentList .attachment-row");
    }

    /**
     * 添付ファイル一覧の指定行のファイル名表示（「{連番}. {ファイル名}({ファイルサイズ})」形式）。
     *
     * @param ordinal 1 起点の行番号
     * @return ファイル名表示のロケータ
     */
    public Locator attachmentName(int ordinal) {
        return attachmentRows().nth(ordinal - 1).locator(".att-name");
    }

    /**
     * 添付ファイル一覧の指定行のメタ情報表示（「{登録者}({登録部署}) – {登録日時}」形式）。
     *
     * @param ordinal 1 起点の行番号
     * @return メタ情報表示のロケータ
     */
    public Locator attachmentMeta(int ordinal) {
        return attachmentRows().nth(ordinal - 1).locator(".att-meta");
    }

    /**
     * 「添付」項目のラベル（添付ファイル一覧が「無いこと」の検証で文脈アンカーに用いる）。
     *
     * @return 添付ラベルのロケータ
     */
    public Locator attachmentSectionLabel() {
        return fieldLabel("添付");
    }

    /**
     * メタ情報（登録者・更新者・登録部署・更新部署・登録日時・更新日時）の表示。
     *
     * @param label 項目のラベル（例 {@code 登録者}）
     * @return メタ情報表示のロケータ
     */
    public Locator meta(String label) {
        return page.locator(".f-cell")
                .filter(new Locator.FilterOptions().setHas(
                        page.locator(".f-lab", new Page.LocatorOptions().setHasText(label))))
                .locator(".meta-txt");
    }

    /**
     * 編集保護のチェックボックス（オンの間は入力欄が読み取り専用になる）。
     *
     * @return 編集保護チェックボックスのロケータ
     */
    public Locator editProtectCheckbox() {
        return page.locator("#editProtect");
    }

    /**
     * 「更新」ボタン。
     *
     * @return 更新ボタンのロケータ
     */
    public Locator submitButton() {
        return page.locator("#submitButton");
    }

    /**
     * 画面上部に表示されるエラー領域（更新の競合・案件情報未検出等）。
     *
     * @return エラー領域のロケータ
     */
    public Locator errorArea() {
        return page.locator("#errorArea");
    }

    /**
     * 画面レベルのエラータイトル。
     *
     * @return エラータイトルのロケータ
     */
    public Locator errorTitle() {
        return page.locator("#errorArea .title");
    }

    /**
     * 画面レベルのエラーメッセージ。
     *
     * @return エラーメッセージのロケータ
     */
    public Locator errorMessage() {
        return page.locator("#errorArea li");
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

    /**
     * 画面タイトルに続けて表示される管理コードのバッジ（「ID: 1」）。
     *
     * @return 管理コードバッジのロケータ
     */
    public Locator managementCodeBadge() {
        return page.locator(".screen-title .id-badge");
    }

    /**
     * 表示されている（編集保護オフのときだけ現れる）「編集確定」ボタン。
     *
     * <p>
     * 編集保護オンのときはツールバー内の位置を保つため {@code visibility:hidden} で隠されるので、
     * 「表示されていないこと」の検証には可視フィルタ付きの本ロケータを用いる。
     *
     * @return 表示中の「編集確定」ボタンのロケータ
     */
    public Locator visibleSubmitButton() {
        return page.locator("#submitButton:visible");
    }

    /**
     * 表示されている（編集保護オフのときだけ現れる）メール領域。
     *
     * @return 表示中のメール領域のロケータ
     */
    public Locator visibleMailSection() {
        return page.locator(".edit-only:visible");
    }

    /** 「編集保護」のトグルをオフに切り替える。 */
    public void turnOffEditProtect() {
        editProtectCheckbox().uncheck();
    }

    /** 「編集保護」のトグルをオンに戻す。 */
    public void turnOnEditProtect() {
        editProtectCheckbox().check();
    }

    /** 「編集確定」ボタンを押下する。 */
    public void pressSubmit() {
        submitButton().click();
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
     * ダイアログの役割を持つ要素（削除確認はネイティブ confirm のため DOM 要素を持たない。 「ダイアログが表示されていない」ことの検証に用いる）。
     *
     * @return ダイアログ要素のロケータ
     */
    public Locator dialogs() {
        return page.getByRole(AriaRole.DIALOG);
    }

    /**
     * 添付ファイル一覧の指定行をクリックする（編集保護オンならダウンロード、オフなら削除確認）。
     *
     * @param ordinal 1 起点の行番号
     */
    public void clickAttachmentRow(int ordinal) {
        attachmentRows().nth(ordinal - 1).click();
    }

    /**
     * 添付ファイル一覧の指定行をクリックしてダウンロードを受け取る（編集保護オンのとき）。
     *
     * @param ordinal 1 起点の行番号
     * @return ダウンロード
     */
    public Download downloadAttachmentRow(int ordinal) {
        return page.waitForDownload(() -> clickAttachmentRow(ordinal));
    }

    /**
     * 添付ファイル一覧が指定件数で描画されるまで待つ（一覧は API 取得後に描画される）。
     *
     * @param expected 期待する行数（0 のときは一覧要素自体が消えるまで待つ）
     */
    public void waitForAttachmentRows(int expected) {
        if (expected == 0) {
            attachmentList()
                    .waitFor(new Locator.WaitForOptions().setState(WaitForSelectorState.DETACHED));
            return;
        }
        attachmentRows().nth(expected - 1)
                .waitFor(new Locator.WaitForOptions().setState(WaitForSelectorState.VISIBLE));
    }

    /**
     * 「件名」入力欄が入力できない状態か（仕様書 13_案件情報更新 2-2 の【機械検証】に対応）。
     *
     * @return {@code disabled} または {@code readonly} なら「入力不可」、そうでなければ「入力可能」
     */
    @E2eRawDom("13_案件情報更新 2-2 【機械検証】「件名」入力欄が入力不可（disabled または readonly）である")
    public String subjectInputEditability() {
        Object disabled =
                input(SUBJECT).evaluate("el => el.disabled || el.readOnly ? '入力不可' : '入力可能'");
        return String.valueOf(disabled);
    }
}
