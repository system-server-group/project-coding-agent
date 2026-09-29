package testgen.e2e.pages;

import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
import java.nio.file.Path;
import testgen.e2e.support.evidence.E2eRawDom;

/**
 * 案件情報の入力フォーム（登録画面・更新画面）に共通する Page Object の基底。
 *
 * <p>
 * 両画面は同じ入力項目・同じ構造（{@code .f-cell} ＞ {@code .f-lab} ＋ {@code .f-body}）を共有するため、
 * 入力欄・エラーメッセージのロケータと入力操作を本クラスへ集約する。画面固有の操作（登録・更新・戻る等）は 各サブクラスが持つ。
 */
public abstract class ProjectFormPage extends AppPage {

    /** 件名の入力欄 id。 */
    public static final String SUBJECT = "subject";

    /** ステータスの入力欄 id。 */
    public static final String STATUS = "status";

    /** 取引先の入力欄 id。 */
    public static final String CLIENT = "client";

    /** 商流の入力欄 id。 */
    public static final String DISTRIBUTION = "distribution";

    /** 案件概要の入力欄 id。 */
    public static final String OVERVIEW = "overview";

    /** 工程の入力欄 id。 */
    public static final String PROCESS = "process";

    /** 開始日の入力欄 id。 */
    public static final String START_DATE = "startDate";

    /** 終了日の入力欄 id。 */
    public static final String END_DATE = "endDate";

    /** スキルの入力欄 id。 */
    public static final String SKILL = "skill";

    /** 人数の入力欄 id。 */
    public static final String HEADCOUNT = "headcount";

    /** 残数の入力欄 id。 */
    public static final String REMAINING_COUNT = "remainingCount";

    /** BP募集（「BPの募集が必要です」チェックボックス）の入力欄 id。 */
    public static final String BP_REQUIRED = "bpRequired";

    /** BP募集の「詳細: 」の入力欄 id。 */
    public static final String BP_DETAIL = "bpDetail";

    /** 作業場所の入力欄 id。 */
    public static final String WORKPLACE = "workplace";

    /** 見込単価の入力欄 id。 */
    public static final String ESTIMATED_PRICE = "estimatedPrice";

    /** 契約種別の入力欄 id。 */
    public static final String CONTRACT_TYPE = "contractType";

    /** 備考の入力欄 id。 */
    public static final String NOTE = "note";

    /** メール送信チェックボックスの入力欄 id。 */
    public static final String SEND_MAIL = "sendMail";

    /** メール本文の入力欄 id。 */
    public static final String MAIL_BODY = "mailBody";

    /**
     * @param page 操作対象のページ
     * @param baseUrl 起動中アプリのベース URL
     */
    protected ProjectFormPage(Page page, String baseUrl) {
        super(page, baseUrl);
    }

    /**
     * 入力欄。
     *
     * @param inputId 入力欄の id（本クラスの定数）
     * @return 入力欄のロケータ
     */
    public Locator input(String inputId) {
        return page.locator("#" + inputId);
    }

    /**
     * 入力欄の直下に表示される入力値チェックのエラーメッセージ。
     *
     * @param inputId 入力欄の id（本クラスの定数）
     * @return エラーメッセージのロケータ
     */
    public Locator fieldError(String inputId) {
        // setHas の内側ロケータは外側（.f-body）に対する相対指定として解決される。
        return page.locator(".f-body")
                .filter(new Locator.FilterOptions().setHas(page.locator("#" + inputId)))
                .locator(".text-danger");
    }

    /**
     * 画面上に表示されている（内容を持つ）入力値チェックのエラーメッセージ全件。
     *
     * <p>
     * 添付のエラー表示（{@code #attachmentError}）は違反が無くても要素自体は存在するため、内容を持つ ものだけを対象にする。
     *
     * @return 表示中のエラーメッセージのロケータ
     */
    public Locator visibleFieldErrors() {
        return page.locator(".f-body .text-danger:not(:empty)");
    }

    /**
     * 入力項目のラベル。
     *
     * @param label ラベルの文言
     * @return ラベルのロケータ
     */
    public Locator fieldLabel(String label) {
        return page.locator(".f-lab", new Page.LocatorOptions().setHasText(label));
    }

    /**
     * 添付のファイルピッカー（クリックでファイル選択、ドラッグ＆ドロップの領域）。
     *
     * @return ファイルピッカーのロケータ
     */
    public Locator attachmentDropArea() {
        return page.locator("#attachmentDrop");
    }

    /**
     * 添付のファイル入力（画面上は非表示。ファイルピッカーのクリックで開く）。
     *
     * @return ファイル入力のロケータ
     */
    public Locator attachmentInput() {
        return page.locator("#attachment");
    }

    /**
     * 添付のエラーメッセージ（ファイル形式チェック）。
     *
     * @return エラーメッセージのロケータ
     */
    public Locator attachmentError() {
        return page.locator("#attachmentError");
    }

    /**
     * 添付のエラーメッセージのうち、内容を持つ（表示されている）もの。
     *
     * @return 表示中の添付エラーメッセージのロケータ
     */
    public Locator attachmentErrorWhenShown() {
        return page.locator("#attachmentError:not(:empty)");
    }

    /**
     * メール本文の領域（メール送信チェックボックスの切替で表示・非表示が変わる）。
     *
     * @return メール本文領域のロケータ
     */
    public Locator mailBodyArea() {
        return page.locator("#mailBodyArea");
    }

    /**
     * 表示されているメール本文のテキストエリア（非表示時は CSS で消えるため可視のみを対象にする）。
     *
     * @return 表示中のメール本文テキストエリアのロケータ
     */
    public Locator visibleMailBody() {
        return page.locator("#mailBodyArea:visible textarea");
    }

    /**
     * 取引先のオートコンプリート候補（datalist）。
     *
     * @return datalist のロケータ
     */
    public Locator clientOptions() {
        return page.locator("#clientOptions");
    }

    /**
     * オートコンプリートの選択肢メニュー（{@code input[list]} を置き換える共通部品が body 直下に置く）。
     *
     * @return 選択肢メニューのロケータ
     */
    public Locator autocompleteMenu() {
        return page.locator("ul.ac-menu");
    }

    /**
     * 表示されているオートコンプリートの選択肢メニュー（不在検証に用いる）。
     *
     * @return 表示中の選択肢メニューのロケータ
     */
    public Locator visibleAutocompleteMenu() {
        return page.locator("ul.ac-menu:visible");
    }

    /**
     * オートコンプリートの選択肢（メニュー内の項目）。
     *
     * @return 選択肢のロケータ
     */
    public Locator autocompleteItems() {
        return page.locator("ul.ac-menu li");
    }

    /**
     * ファイルピッカーの主表示（未指定時は案内文、指定時は「{ファイル名}({ファイルサイズ})」と「選択解除」）。
     *
     * @return ファイルピッカー主表示のロケータ
     */
    public Locator attachmentDropMain() {
        return page.locator("#attachmentDrop .drop-main");
    }

    /**
     * ファイルピッカーの「選択解除」リンク（ファイル指定時のみ現れる）。
     *
     * @return 選択解除リンクのロケータ
     */
    public Locator attachmentClearLink() {
        return page.locator("#attachmentDrop .drop-clear");
    }

    /**
     * 指定した入力欄が画面の下端に来るまでメインビューポートを縦にスクロールする合成操作 （仕様書 00_共通部品 2-7 の「入力欄が画面の下端付近に来るまで縦にスクロールする」に対応）。
     *
     * <p>
     * 検証部品のスクロールは対象を画角の中央へ寄せるため、部品の下方向の領域が不足する画角は作れない。 下端寄せのスクロールを合成して、選択肢が上方向へ配置される条件を決定的に作る。
     *
     * @param inputId 入力欄の id
     */
    @E2eRawDom("00_共通部品 2-7 「取引先」の入力欄が画面の下端付近に来るまでメインビューポートを縦にスクロールする")
    public void scrollFieldToViewportBottom(String inputId) {
        input(inputId).evaluate("el => el.scrollIntoView({block: 'end', behavior: 'instant'})");
    }

    /**
     * テキスト系の入力欄へ値を入力する。
     *
     * @param inputId 入力欄の id
     * @param value 入力する値
     */
    public void fill(String inputId, String value) {
        input(inputId).fill(value);
    }

    /**
     * セレクトボックスの値を選択する。
     *
     * @param inputId 入力欄の id
     * @param value 選択する値（option の value）
     */
    public void select(String inputId, String value) {
        input(inputId).selectOption(value);
    }

    /**
     * チェックボックスにチェックを入れる。
     *
     * @param inputId 入力欄の id
     */
    public void check(String inputId) {
        input(inputId).check();
    }

    /**
     * チェックボックスのチェックを外す。
     *
     * @param inputId 入力欄の id
     */
    public void uncheck(String inputId) {
        input(inputId).uncheck();
    }

    /**
     * クライアント側のファイル形式チェックを迂回して添付ファイルを設定する合成操作（仕様書 6-4 の
     * バイパス手順「ファイル選択イベントのファイル形式チェックを迂回して添付として送信させる」に対応）。
     *
     * <p>
     * ファイル形式チェックは {@code change} イベントのハンドラで行われ、違反時は選択自体を解除する。
     * 迂回のため、ファイル入力をリスナーを持たない複製へ置き換えてからファイルを設定する （開発者ツールでイベントリスナーを外すのと同等の操作）。設定の効果は画面に現れないため、 呼び出し側は
     * {@link #attachedFileName()} の実測を【機械検証】として記録する。
     *
     * @param file 添付として設定するファイル
     */
    @E2eRawDom("12_案件情報登録 6-4・13_案件情報更新 7-7/7-8 バイパス手順「ファイル選択イベントのファイル形式チェックを迂回して添付として送信させる」")
    public void attachBypassingClientCheck(Path file) {
        attachmentInput().evaluate("el => el.replaceWith(el.cloneNode(true))");
        attachmentInput().setInputFiles(file);
    }

    /**
     * 添付として設定されているファイル名の実測値（画面に現れない合成操作の効果の記録に用いる）。
     *
     * @return 設定されているファイル名（未設定なら空文字）
     */
    @E2eRawDom("12_案件情報登録 6-4・13_案件情報更新 7-7/7-8 バイパス手順の効果の実測（添付として設定されたファイル名）")
    public String attachedFileName() {
        Object name = attachmentInput()
                .evaluate("el => el.files && el.files.length === 1 ? el.files[0].name : ''");
        return String.valueOf(name);
    }

    /**
     * ステータスの選択値を、選択肢に無い値も含めて直接設定する合成操作（仕様書 5-36・6-3 の 「選択肢に無い値を選択済みの値として設定する」「値を空にする」に対応）。
     *
     * <p>
     * 選択肢に無い値・空値は画面操作では選べないため、{@code select} 要素へ該当する {@code option} を
     * 合成して選択状態にする。設定の効果は画面表示（選択中の値）に現れる。
     *
     * @param value 設定する値（空文字を含む）
     */
    @E2eRawDom("12_案件情報登録 5-36・6-3／13_案件情報更新 6-35・7-6 バイパス手順「ステータスの値を空にする／選択肢に無い値を設定する」")
    public void forceStatusValue(String value) {
        input(STATUS).evaluate("""
                (el, v) => {
                  let option = Array.from(el.options).find((o) => o.value === v);
                  if (!option) {
                    option = document.createElement('option');
                    option.value = v;
                    option.textContent = v;
                    el.appendChild(option);
                  }
                  el.value = v;
                }
                """, value);
    }

    /**
     * ステータスに設定されている値の実測値（合成操作の効果の記録に用いる）。
     *
     * @return 設定されている値
     */
    @E2eRawDom("12_案件情報登録 5-36・6-3／13_案件情報更新 6-35・7-6 バイパス手順の効果の実測（ステータスに設定された値）")
    public String statusValue() {
        return String.valueOf(input(STATUS).evaluate("el => el.value"));
    }
}
