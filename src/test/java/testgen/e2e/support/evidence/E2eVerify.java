package testgen.e2e.support.evidence;

import static com.microsoft.playwright.assertions.PlaywrightAssertions.assertThat;

import com.microsoft.playwright.Dialog;
import com.microsoft.playwright.Download;
import com.microsoft.playwright.FileChooser;
import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.PlaywrightException;
import com.microsoft.playwright.Request;
import com.microsoft.playwright.Response;
import com.microsoft.playwright.options.BoundingBox;
import com.microsoft.playwright.options.WaitForSelectorState;
import jakarta.mail.internet.MimeMessage;
import jakarta.servlet.ServletContext;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Base64;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Consumer;
import java.util.function.Supplier;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import testgen.e2e.support.E2eFaultSeams;
import testgen.e2e.support.E2eMailbox;
import testgen.e2e.support.MailHogPage;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * 証跡V2の検証部品ファサード。テストコードは本クラス（と {@code op}）だけで検証を記述する。
 *
 * <p>
 * 各部品は「対象を画面内へ入れる → ハイライト → 見切れ検査 → フレーム撮影 → 機械検証 → steps.json 記録」を
 * 一括して行い、映り（証跡の有効性）を実装者の注意力でなく構造で担保する。検証（アサーション）自体は
 * 証跡モードでない実行（e2eTest）でも同一に実行され、撮影・記録だけが証跡モード限定になる。
 *
 * <p>
 * 検証が失敗した場合も、失敗時点の画面をフレームに残し、ステップを {@code status=fail} で記録してから 例外を送出する（中断時点までの証跡が steps.json
 * に残る）。検証対象・操作対象の<b>要素が画面に存在しない場合</b>（設計と実装の乖離の典型）も、短い待機の後に
 * 現画面のフレームと期待／実測の対を記録してから失敗させる（長い既定タイムアウトの無記録エラーで終わらせない）。
 * ビューポートに収まらない大きな要素は 要素単体ショット（attachFrame）を補助フレームとして添付する。
 */
public final class E2eVerify {

    /** 代理証跡パネルに実値で列挙する候補の上限（超過分は先頭サンプル＋機械突合で扱う）。 */
    private static final int PROXY_PANEL_HEAD = 10;

    /** グリッドの横スクロール後に仮想化の再描画を待つ時間（ms）。 */
    private static final int SCROLL_SETTLE_MS = 150;

    /**
     * 対象要素の出現（DOM への出現）を待つ上限（ms）。要素が存在しない失敗（設計と実装の乖離の典型）を
     * 長い既定タイムアウトの無記録エラーで終わらせないための短い待ち（web-first アサーションの既定と同程度）。
     */
    private static final double PRESENCE_WAIT_MS = 5_000;

    /** 操作に伴う HTTP 応答の捕捉を待つ上限（ms）。 */
    private static final double RESPONSE_WAIT_MS = 10_000;

    /** 否定観測（要求・ダウンロードが発生しないこと）の観測時間（ms）。{@code noMailSent} の待機と同水準。 */
    private static final double NEGATIVE_OBSERVATION_WAIT_MS = 2_000;

    /** HTTP 応答ボディ（JSON）の解析用。 */
    private static final ObjectMapper JSON = new ObjectMapper();

    private final Page page;
    private final E2eFrameService frames;
    private final E2eStepRecorder recorder;
    /** 直後の検証ステップへ紐づける DL 実物の証跡ファイル名（runVerify が消費する）。 */
    private String pendingArtifact;

    /**
     * 検証部品ファサードを生成する。
     *
     * @param page 操作対象のページ
     * @param frames フレーム撮影サービス（証跡モードでないときは null）
     * @param recorder ステップ記録（証跡モードでないときは null）
     */
    public E2eVerify(Page page, E2eFrameService frames, E2eStepRecorder recorder) {
        this.page = page;
        this.frames = frames;
        this.recorder = recorder;
    }

    private boolean recording() {
        return recorder != null && frames != null;
    }

    // ---- 操作フレーム ----

    /**
     * 操作を実行し、直後の画面をフレームに記録する。
     *
     * @param desc 何をする操作か（ビューアのキャプション）
     * @param action 実行する操作
     */
    public void op(String desc, Runnable action) {
        setOverlayStep("【操作】" + desc);
        runAction(desc, action);
        opStep(desc, null);
    }

    /**
     * 操作を実行し、操作した対象を画面内へ映して（青ハイライト付き）フレームに記録する。
     *
     * <p>
     * target には<b>操作で値・状態が変化した要素</b>（入力した欄・選択したファイル名の表示先・追加された行
     * 等）だけを指定する。画面を開く・画面遷移の操作には単一の結果要素が存在しないため {@link #opOpen}
     * を使う（読込完了の目印として無関係な要素に青ハイライトを引かない。desc に「アドレスバー」を含む
     * 操作の target 指定は本部品が拒否する）。1 つのフレームに収まらない複合操作は、観測可能な変化ごとに
     * 本メソッドで分割して記録する。
     *
     * @param desc 何をする操作か
     * @param action 実行する操作
     * @param target 操作の結果が現れる要素（フレームに必ず映す）
     */
    public void op(String desc, Runnable action, Locator target) {
        requireResultTarget(desc);
        setOverlayStep("【操作】" + desc);
        runAction(desc, action);
        opStep(desc, target);
    }

    /**
     * 画面を開く・画面遷移する操作（URL 直打ち・リロード・開き直し等）を実行し、遷移後の画面を
     * フレームに記録する。
     *
     * <p>
     * 遷移で画面全体が変わる操作には単一の「結果が現れる要素」が存在しないため、target は取らない
     * （読込完了の目印として無関係な要素に青ハイライトを引かない）。遷移の事実は情報バーの URL と
     * 証跡ログ（NAV）に残り、到達した画面の正しさは後続の検証ステップ（{@link #urlIs}・画面タイトル等）
     * で受ける。読込完了待ちは操作側（Page Object の open 等）が持つ。
     *
     * @param desc 何をする操作か（例: アドレスバーに URL を入力して開く）
     * @param action 実行する操作
     */
    public void opOpen(String desc, Runnable action) {
        setOverlayStep("【操作】" + desc);
        runAction(desc, action);
        opStep(desc, null);
    }

    /**
     * 直前に行った操作の結果として、現在の画面をフレームに記録する。
     *
     * @param desc 何をした操作か（ビューアのキャプション）
     */
    public void opFrame(String desc) {
        opStep(desc, null);
    }

    /**
     * 直前に行った操作の結果として、対象を画面内へ映して（青ハイライト付き）フレームに記録する。
     *
     * @param desc 何をした操作か
     * @param target 操作の結果が現れる要素（フレームに必ず映す）
     */
    public void opFrame(String desc, Locator target) {
        requireResultTarget(desc);
        opStep(desc, target);
    }

    /**
     * 押下系の操作（画面遷移・画面の作り替えを起こすボタン等）を、押下前後の 2 フレームで記録する。
     *
     * <p>
     * 押下前に操作対象を青ハイライトで映し（どの要素をどの画面状態で押すのか＝操作の再現性）、 押下後に結果の画面を映す。結果が同じ画面の要素に現れる入力系の操作は {@link #op}
     * を使う。
     *
     * @param desc 何をする操作か
     * @param control 押下する要素（押下前フレームに必ず映す）
     * @param action 実行する操作
     */
    public void opPress(String desc, Locator control, Runnable action) {
        opPress(desc, control, action, null);
    }

    /**
     * 押下系の操作を押下前後の 2 フレームで記録し、押下後は結果が現れる要素を映す。
     *
     * @param desc 何をする操作か
     * @param control 押下する要素（押下前フレームに必ず映す）
     * @param action 実行する操作
     * @param target 押下の結果が現れる要素（押下後フレームに映す。無ければ null）
     */
    public void opPress(String desc, Locator control, Runnable action, Locator target) {
        requirePressTarget(desc, control);
        opStep(desc + "（押下前・操作対象）", control);
        setOverlayStep("【操作】" + desc);
        runAction(desc, action);
        opStep(desc + "（押下後）", target);
    }

    /**
     * 入力を伴う押下系の複合操作（「…を入力し「ログイン」を押下する」型）を、入力後の押下前フレームと
     * 押下後フレームの 2 フレームで記録する。
     *
     * <p>
     * 仕様書の操作手順が「入力して押下する」を 1 手順で記す場合、{@link #opPress} に入力と押下を
     * まとめて渡すと押下前フレームが<b>入力実行前</b>に撮られ、キャプションの「…を入力し」に対して
     * 画像は未入力（プレースホルダー）のままになる（キャプションとフレームの不一致）。本部品は
     * 先に入力を実行してから押下前フレームを撮るため、押下時点の画面状態（入力値）がフレームと
     * 情報バーに写る（操作の再現性）。入力の結果が押下対象と別画角に現れる複合操作は、従来どおり
     * 観測可能な変化ごとに {@link #op} で分割する。
     *
     * @param desc 何をする操作か（「…を入力し…を押下する」の 1 手順）
     * @param input 押下に先立つ入力の操作（フレームは撮らず、結果は押下前フレームに写る）
     * @param control 押下する要素（入力実行後の押下前フレームに必ず映す）
     * @param press 押下の操作
     */
    public void opPressWithInput(String desc, Runnable input, Locator control, Runnable press) {
        opPressWithInput(desc, input, control, press, null);
    }

    /**
     * 入力を伴う押下系の複合操作を 2 フレームで記録し、押下後は結果が現れる要素を映す。
     *
     * @param desc 何をする操作か（「…を入力し…を押下する」の 1 手順）
     * @param input 押下に先立つ入力の操作（フレームは撮らず、結果は押下前フレームに写る）
     * @param control 押下する要素（入力実行後の押下前フレームに必ず映す）
     * @param press 押下の操作
     * @param target 押下の結果が現れる要素（押下後フレームに映す。無ければ null）
     */
    public void opPressWithInput(
            String desc,
            Runnable input,
            Locator control,
            Runnable press,
            Locator target) {
        setOverlayStep("【操作】" + desc);
        runAction(desc, input);
        requirePressTarget(desc, control);
        opStep(desc + "（押下前・操作対象）", control);
        setOverlayStep("【操作】" + desc);
        runAction(desc, press);
        opStep(desc + "（押下後）", target);
    }

    /**
     * ネイティブ confirm ダイアログを伴う押下操作を、押下前後の 2 フレーム＋代理証跡で記録する。
     *
     * <p>
     * confirm はネイティブ UI のため録画・撮影に写らず、Playwright の既定では自動キャンセルされる。 本部品がダイアログ本文を捕捉して
     * OK／キャンセルを応答し、捕捉した本文と応答を代理証跡パネル として押下後フレームに描画する。本文の一致は【機械検証】として記録する（ダイアログが
     * 表示されなければ失敗）。応答ハンドラは本操作の間だけ登録し、終了後は既定動作へ戻す。
     *
     * @param desc 何をする操作か
     * @param control 押下する要素（押下前フレームに必ず映す）
     * @param expectedMessage 期待するダイアログ本文
     * @param accept OK（承認）なら true、キャンセルなら false
     * @param action 実行する操作（confirm を発生させる押下）
     * @param target 応答後の結果が現れる要素（押下後フレームに映す。無ければ null）
     */
    public void opPressConfirm(
            String desc,
            Locator control,
            String expectedMessage,
            boolean accept,
            Runnable action,
            Locator target) {
        requirePressTarget(desc, control);
        opStep(desc + "（押下前・操作対象）", control);
        setOverlayStep("【操作】" + desc);
        String[] message = new String[1];
        Consumer<Dialog> handler = dialog -> {
            message[0] = dialog.message();
            if (accept) {
                dialog.accept();
            } else {
                dialog.dismiss();
            }
        };
        page.onDialog(handler);
        try {
            runAction(desc, action);
        } finally {
            page.offDialog(handler);
        }
        String choice = accept ? "OK" : "キャンセル";
        if (recording()) {
            frames.drawProxyPanel(desc + "（confirm ダイアログ）",
                    List.of("本文: " + (message[0] == null ? "（表示されず）" : message[0]),
                            "選択肢: OK ／ キャンセル", "応答: " + choice + " を押下"),
                    "ネイティブの confirm ダイアログは録画・撮影に写らないため、捕捉した本文と応答を描画した代理証跡");
        }
        opStep(desc + "（押下後・" + choice + " 応答）", target);
        machine("confirm", desc + "（confirm 本文）", expectedMessage,
                message[0] == null ? "（ダイアログ表示なし）" : message[0], expectedMessage.equals(message[0]),
                "confirm ダイアログの本文が期待と一致しない");
    }

    /**
     * サーバー検証をバイパスするため、入力欄の属性を除去する合成操作（例: {@code maxlength} の解除）。
     *
     * <p>
     * クライアント側の入力制限が妨げてサーバー検証の異常系へ到達できない場合に使う（バイパス手順は 仕様書の操作手順に明記されている前提）。画面に変化が現れない合成操作のためフレームは撮らず、
     * 効果の実測（除去前の属性値→除去後に属性なし）を【機械検証】として記録する。
     *
     * @param desc 何のための操作か（例: 「件名欄の maxlength を解除する（201文字入力のため）」）
     * @param target 対象の入力欄
     * @param attribute 除去する属性名（例: {@code maxlength}）
     */
    public void opBypassRemoveAttribute(String desc, Locator target, String attribute) {
        setOverlayStep("【操作】" + desc);
        requireBypassTarget(desc, target);
        Object before = target.first().evaluate("(el, a) => el.getAttribute(a)", attribute);
        target.first().evaluate("(el, a) => el.removeAttribute(a)", attribute);
        Object after = target.first().evaluate("(el, a) => el.getAttribute(a)", attribute);
        machine("bypass", desc + "（除去前の " + attribute + "=" + attributeLabel(before) + "）", "属性なし",
                attributeLabel(after), after == null, attribute + " を除去できていない");
    }

    /**
     * サーバー検証をバイパスするため、入力欄の属性を書き換える合成操作（例: {@code type} を {@code date} から {@code text}
     * へ変更して不正な日付を入力可能にする）。
     *
     * <p>
     * 画面に変化が現れない合成操作のためフレームは撮らず、効果の実測（変更前の属性値→変更後の値）を 【機械検証】として記録する。
     *
     * @param desc 何のための操作か（例: 「開始日欄の type を text へ変更する（不正な日付入力のため）」）
     * @param target 対象の入力欄
     * @param attribute 書き換える属性名（例: {@code type}）
     * @param value 設定する値（例: {@code text}）
     */
    public void opBypassSetAttribute(String desc, Locator target, String attribute, String value) {
        setOverlayStep("【操作】" + desc);
        requireBypassTarget(desc, target);
        Object before = target.first().evaluate("(el, a) => el.getAttribute(a)", attribute);
        target.first().evaluate("(el, arg) => el.setAttribute(arg.a, arg.v)",
                Map.of("a", attribute, "v", value));
        Object after = target.first().evaluate("(el, a) => el.getAttribute(a)", attribute);
        machine("bypass", desc + "（変更前の " + attribute + "=" + attributeLabel(before) + "）", value,
                attributeLabel(after), value.equals(after), attribute + " を変更できていない");
    }

    /** 属性値の機械記録用の表現（null は「属性なし」）。 */
    private static String attributeLabel(Object value) {
        return value == null ? "属性なし" : String.valueOf(value);
    }

    /**
     * ポップアップ型部品の配置検証に用いる画角を作るため、対象要素の直前へ余白要素を挿入する
     * 合成操作（開発者ツール相当。到達手段カタログ 手段1の系）。
     *
     * <p>
     * 「部品の上方向に十分な領域がある画角」は、対象要素の上にコンテンツが少ない画面ではスクロール
     * で作れない（スクロールは要素を上方向へしか動かせない）。本部品は指定高さの余白要素を対象の
     * 直前へ挿入し、上方向の領域を決定的に確保する。挿入はフレームを撮らず、挿入した余白の実高を
     * 【機械検証】として記録する（作られた画角は後続の操作フレームで受ける）。
     *
     * @param desc 何のための操作か（仕様書の手順・【機械検証】行と対応させる）
     * @param anchor 余白を直前に挿入する対象要素
     * @param heightPx 挿入する余白の高さ（px）
     */
    public void opInsertSpacerAbove(String desc, Locator anchor, int heightPx) {
        setOverlayStep("【操作】" + desc);
        requireBypassTarget(desc, anchor);
        Object measured = anchor.first().evaluate("""
                (el, h) => {
                  const spacer = document.createElement('div');
                  spacer.style.height = h + 'px';
                  el.parentElement.insertBefore(spacer, el);
                  return Math.round(spacer.getBoundingClientRect().height);
                }
                """, heightPx);
        machine("bypass", desc + "（余白要素の挿入）", heightPx + "px", measured + "px",
                measured instanceof Number number && number.intValue() == heightPx,
                "余白要素を挿入できていない");
    }

    /**
     * 障害シームを有効化する合成操作（仕様書の操作手順「（障害シーム）〈シーム名〉を有効化する」に対応）。
     *
     * <p>
     * サーバー内部の異常分岐（DB例外→ロールバック・送信失敗・競合検出等）へ画面操作だけでは到達
     * できない場合に使う（到達手段カタログ 手段4）。有効化は画面に変化が現れない合成操作のため
     * フレームは撮らず、有効化の実測を【機械検証】として記録する。シームは発火後に自動解除される
     * （{@link E2eFaultSeams}）。対象操作の後は {@link #faultFired} で発火を検証する。
     *
     * @param desc 何のための操作か（例: 「（障害シーム）データベース例外（登録）を有効化する」）
     * @param seamName シーム名（仕様書・シーム Bean と一致させる）
     */
    public void opArmFault(String desc, String seamName) {
        setOverlayStep("【操作】" + desc);
        E2eFaultSeams.arm(seamName);
        boolean armed = E2eFaultSeams.isArmed(seamName);
        machine("fault-arm", desc + "（障害シーム「" + seamName + "」の有効化）", "有効",
                armed ? "有効" : "無効", armed, "障害シームを有効化できていない");
    }

    /**
     * 障害シームが発火した（対象操作がシームの障害経路を実際に通った）ことを検証する。
     *
     * <p>
     * 発火の事実は画面に現れないため【機械検証】として記録する。障害の結果（エラー画面・ロール
     * バック後のデータが画面上元のままであること・通知が出ないこと）は可視の検証部品で別途受ける。
     *
     * @param desc 何を確認するか（例: 「登録処理がデータベース例外の経路を通った」）
     * @param seamName シーム名
     */
    public void faultFired(String desc, String seamName) {
        boolean fired = E2eFaultSeams.wasFired(seamName);
        machine("fault-fired", desc + "（障害シーム「" + seamName + "」の発火）", "発火あり",
                fired ? "発火あり" : "発火なし", fired,
                "障害シームが発火していない（対象操作が障害経路を通っていない）");
    }

    /**
     * ドロップ領域へ<b>フォルダ</b>をドロップする合成操作。
     *
     * <p>
     * OS のファイルマネージャからのドラッグはブラウザ自動化の外側にあり再現できない。またフォルダを表す
     * {@code DataTransfer}（{@code items[0].webkitGetAsEntry()} がディレクトリを返すもの）はブラウザ API
     * から正規に組み立てられない。このため {@code drop} イベントを合成し、実際のフォルダドロップと同じ形の 標準プロパティ——<b>フォルダを表す疑似
     * File</b>（フォルダ名・サイズ0。実ドロップでもフォルダの中身は {@code files} に載らない）だけを持つ {@code files} と、ディレクトリを返す
     * {@code webkitGetAsEntry()} を持つ {@code items}——を与えて発火する。アプリのフォルダ判定への入力は実ドロップと同等になるが、
     * {@code DataTransfer} 実体を伴わない点で完全同一ではない（アプリが読む標準プロパティのみ備える）。
     *
     * <p>
     * ドロップの結果（エラーメッセージ等）は画面に現れるため操作フレームを撮り、画面に出ない合成入力 （ディレクトリ判定・添付件数・フォルダ名）は【機械検証】として記録する。
     *
     * @param desc 何のための操作か（例: 「フォルダ dropdir をファイルピッカーへドロップする」）
     * @param dropTarget ドロップ領域
     * @param folder ドロップするフォルダ（実在すること。証跡の記述と実体を対応させるため）
     * @param resultTarget 操作の結果が現れる要素（操作フレームに映す）
     */
    public void opDropFolder(String desc, Locator dropTarget, Path folder, Locator resultTarget) {
        if (!Files.isDirectory(folder)) {
            throw new IllegalStateException("ドロップするフォルダがありません: " + folder);
        }
        String folderName = folder.getFileName().toString();
        Object[] dropped = new Object[1];
        op(desc, () -> dropped[0] = dropTarget.first().evaluate(DROP_FOLDER_SCRIPT, folderName),
                resultTarget);
        @SuppressWarnings("unchecked")
        Map<String, Object> result = (Map<String, Object>) dropped[0];
        boolean ok = Boolean.TRUE.equals(result.get("isDirectory"))
                && result.get("fileCount") instanceof Number count && count.intValue() == 1
                && folderName.equals(result.get("folderName"));
        machine("drop", desc + "（合成した drop の入力）", "ディレクトリ=true・添付件数=1・フォルダ名=" + folderName,
                "ディレクトリ=" + result.get("isDirectory") + "・添付件数=" + result.get("fileCount")
                        + "・フォルダ名=" + result.get("folderName"),
                ok, "フォルダとして判定される drop を合成できていない");
    }

    /**
     * ドロップ領域へ<b>ファイル</b>をドロップする合成操作。
     *
     * <p>
     * OS のファイルマネージャからのドラッグはブラウザ自動化の外側にあり再現できないため、実ファイルの
     * 内容を積んだ {@code File} を持つ {@code drop} イベントを合成して発火する（{@link #opDropFolder} の
     * ファイル版。アプリが読む {@code dataTransfer.files}・{@code items[0].webkitGetAsEntry()} の双方を
     * 備える）。ファイル内容は Base64 でページへ渡すため、サイズの大きいファイルには
     * {@link #opChooseFile}（ファイル選択ダイアログ経由）を使う。
     *
     * <p>
     * ドロップの結果（選択されたファイル名の表示等）は画面に現れるため操作フレームを撮り、画面に出ない
     * 合成入力（ファイル判定・添付件数・ファイル名・バイト数）は【機械検証】として記録する。
     *
     * @param desc 何のための操作か（例: 「size-1024.bin をファイルピッカーへドロップする」）
     * @param dropTarget ドロップ領域
     * @param file ドロップするファイル（実在すること。証跡の記述と実体を対応させるため）
     * @param resultTarget 操作の結果が現れる要素（操作フレームに映す）
     */
    public void opDropFile(String desc, Locator dropTarget, Path file, Locator resultTarget) {
        if (!Files.isRegularFile(file)) {
            throw new IllegalStateException("ドロップするファイルがありません: " + file);
        }
        String fileName = file.getFileName().toString();
        byte[] bytes;
        try {
            bytes = Files.readAllBytes(file);
        } catch (IOException e) {
            throw new UncheckedIOException("ドロップするファイルの読み込みに失敗しました: " + file, e);
        }
        Map<String, String> arg = Map.of("name", fileName, "bytesBase64",
                Base64.getEncoder().encodeToString(bytes));
        Object[] dropped = new Object[1];
        op(desc, () -> dropped[0] = dropTarget.first().evaluate(DROP_FILE_SCRIPT, arg),
                resultTarget);
        @SuppressWarnings("unchecked")
        Map<String, Object> result = (Map<String, Object>) dropped[0];
        boolean ok = Boolean.TRUE.equals(result.get("isFile"))
                && result.get("fileCount") instanceof Number count && count.intValue() == 1
                && fileName.equals(result.get("fileName"))
                && result.get("size") instanceof Number size && size.longValue() == bytes.length;
        machine("drop", desc + "（合成した drop の入力）",
                "ファイル=true・添付件数=1・ファイル名=" + fileName + "・サイズ=" + bytes.length + "バイト",
                "ファイル=" + result.get("isFile") + "・添付件数=" + result.get("fileCount")
                        + "・ファイル名=" + result.get("fileName") + "・サイズ=" + result.get("size")
                        + "バイト",
                ok, "ファイルとして判定される drop を合成できていない");
    }

    /**
     * ファイル選択ダイアログを開く操作でファイルを1件指定する合成操作。
     *
     * <p>
     * ネイティブのファイル選択ダイアログは録画・撮影に写らない（証跡規範6）。トリガーの押下で開く
     * ダイアログを {@link FileChooser} として受けてファイルを設定し、結果（選択されたファイル名の
     * 表示等）は操作フレームで撮る。画面に出ない合成入力（input へ設定されたファイル名・実バイト数）は
     * 【機械検証】として記録する（期待は指定した実ファイル、実測は input の {@code files[0]}）。
     *
     * @param desc 何のための操作か（例: 「CSVファイル companies.csv を選択する」）
     * @param trigger ファイル選択ダイアログを開くトリガー（ボタン等）
     * @param file 指定するファイル（実在すること。証跡の記述と実体を対応させるため）
     * @param resultTarget 操作の結果が現れる要素（操作フレームに映す）
     */
    public void opChooseFile(String desc, Locator trigger, Path file, Locator resultTarget) {
        if (!Files.isRegularFile(file)) {
            throw new IllegalStateException("指定するファイルがありません: " + file);
        }
        FileChooser[] chooser = new FileChooser[1];
        op(desc, () -> {
            chooser[0] = page.waitForFileChooser(() -> trigger.first().click());
            chooser[0].setFiles(file);
        }, resultTarget);
        String expected = fileNameAndSize(file.getFileName().toString(), sizeOf(file));
        Object actual = chooser[0].element().evaluate(
                "el => el.files && el.files.length === 1"
                        + " ? {name: el.files[0].name, size: el.files[0].size} : null");
        String actualLabel = actual instanceof Map<?, ?> chosen
                ? fileNameAndSize(String.valueOf(chosen.get("name")),
                        ((Number) chosen.get("size")).longValue())
                : "設定なし";
        machine("file-choose", desc + "（ファイル選択ダイアログへの入力）", expected, actualLabel,
                expected.equals(actualLabel), "ファイル選択ダイアログへファイルを設定できていない");
    }

    /** file-choose 系の機械記録のラベル（ファイル名と実バイト数。証跡の代理記録の統一様式）。 */
    private static String fileNameAndSize(String fileName, long size) {
        return "ファイル名=" + fileName + "・サイズ=" + size + "バイト";
    }

    /** 指定した実ファイルのバイト数（読めない場合は実行時例外——証跡の記述と実体を対応させるため）。 */
    private static long sizeOf(Path file) {
        try {
            return Files.size(file);
        } catch (IOException e) {
            throw new UncheckedIOException("ファイルのバイト数を取得できません: " + file, e);
        }
    }

    /**
     * ファイル選択が<b>アプリ側で却下される</b>（形式チェック違反等で選択が解除される）ことを
     * 期待するケースのファイル選択操作。
     *
     * <p>
     * {@link #opChooseFile} は選択後に {@code input} へ設定されたファイル名の一致を【機械検証】する
     * ため、選択を解除する仕様のケースでは常に失敗し使えない。本部品は保持の検証を行わず、
     * ダイアログへの入力（指定した実ファイルの名前・実バイト数）を {@link #opChooseFileSubmits} と同じ規格で
     * 機械記録するに留める。却下の効果（エラーメッセージの表示・選択済みファイル名が表示されない
     * こと等）は、仕様書の期待結果に対応する後続の検証で受ける。
     *
     * @param desc 何のための操作か（却下されるファイルを選択する意図がわかる文言にする）
     * @param trigger ファイル選択ダイアログを開くトリガー（ボタン等）
     * @param file 指定するファイル（実在すること）
     * @param resultTarget 却下の結果が現れる要素（エラーメッセージ等。操作フレームに映す）
     */
    public void opChooseFileRejected(
            String desc,
            Locator trigger,
            Path file,
            Locator resultTarget) {
        if (!Files.isRegularFile(file)) {
            throw new IllegalStateException("指定するファイルがありません: " + file);
        }
        String expected = fileNameAndSize(file.getFileName().toString(), sizeOf(file));
        op(desc, () -> {
            FileChooser chooser = page.waitForFileChooser(() -> trigger.first().click());
            chooser.setFiles(file);
        }, resultTarget);
        machine("file-choose", desc + "（ファイル選択ダイアログへの入力・合成）", expected, expected, true,
                "ファイル選択ダイアログへファイルを設定できていない");
    }

    /**
     * ファイル選択が即座にフォーム送信（画面遷移）を起こす場合のファイル選択操作。
     *
     * <p>
     * マスタ画面の CSV アップロード等は、ファイル選択の {@code change} で即座に {@code form.submit()} し
     * 画面遷移する。この場合、{@link #opChooseFile} のように選択後に選択元 {@code input} を評価すると、
     * 遷移で実行コンテキストが破棄され {@code Execution context was destroyed} で失敗する。本部品は
     * ファイル設定に伴う画面遷移の完了を待ってから結果フレームを撮る。遷移で DOM が失われるため、
     * 選択したファイル名・実バイト数は<b>指定した実ファイル</b>から機械記録する（実際にアップロードが
     * 行われたことは、遷移後の画面に対する後続の検証——洗い替え後の一覧等——で受ける）。
     *
     * @param desc 何のための操作か（例: 「CSVアップロードで companies.csv を選択する」）
     * @param trigger ファイル選択ダイアログを開くトリガー（ボタン等）
     * @param file 指定するファイル（実在すること）
     * @param resultTarget 遷移後の画面で結果が現れる要素（操作フレームに映す）
     */
    public void opChooseFileSubmits(String desc, Locator trigger, Path file, Locator resultTarget) {
        if (!Files.isRegularFile(file)) {
            throw new IllegalStateException("指定するファイルがありません: " + file);
        }
        String expected = fileNameAndSize(file.getFileName().toString(), sizeOf(file));
        op(desc, () -> {
            FileChooser chooser = page.waitForFileChooser(() -> trigger.first().click());
            // setFiles の change ハンドラが form.submit() する。その遷移の完了まで待ってから戻る。
            page.waitForNavigation(() -> chooser.setFiles(file));
        }, resultTarget);
        machine("file-choose", desc + "（ファイル選択ダイアログへの入力・合成）", expected, expected, true,
                "ファイル選択ダイアログへファイルを設定できていない");
    }

    /**
     * 合成した HTTP 要求（{@code fetch}）の応答スナップショット。
     *
     * @param method 要求の HTTP メソッド
     * @param url 要求先 URL
     * @param status HTTP ステータスコード
     * @param body 応答ボディ（テキスト）
     */
    public record SyntheticResponse(String method, String url, int status, String body) {
    }

    /**
     * 開発者ツール相当の合成操作として、ブラウザ（ページのオリジン・セッション Cookie）から任意の
     * HTTP 要求を発行し、その応答を捕捉する。
     *
     * <p>
     * 画面の操作経路では到達できない要求（CSRF トークンを改変した削除 API 等）を発行するために使う。
     * ページ内で {@code fetch} を実行するため、要求はログイン中セッションの Cookie を伴い、指定した
     * ヘッダー（改変した CSRF トークン等）で送出される。画面遷移は伴わない合成要求のためフレームは
     * 撮らず、発行した要求（メソッド・URL・指定ヘッダー）と応答（ステータス）を【機械検証】として記録する。
     * 応答の検証は {@link #responseStatusIs}・{@link #responseBodyEmpty} 等を、捕捉結果を
     * {@link CapturedResponse} に移して行う。
     *
     * @param desc 何のための合成要求か（例: 「CSRF トークンを改変して DELETE を要求する」）
     * @param method HTTP メソッド（例: {@code DELETE}）
     * @param urlPath 要求先のパス（同一オリジン。例: {@code /api/attachments/1001}）
     * @param headers 付与するリクエストヘッダー（改変した CSRF トークン等）
     * @return 応答スナップショット
     */
    public CapturedResponse opApiRequest(
            String desc,
            String method,
            String urlPath,
            Map<String, String> headers) {
        setOverlayStep("【操作】" + desc);
        Map<String, Object> arg =
                Map.of("method", method, "url", urlPath, "headers", new LinkedHashMap<>(headers));
        Object raw = page.evaluate("""
                async (arg) => {
                  const res = await fetch(arg.url, {
                    method: arg.method,
                    headers: arg.headers,
                    credentials: 'same-origin'
                  });
                  const body = await res.text();
                  return {status: res.status, body: body};
                }
                """, arg);
        int status = 0;
        String body = "";
        if (raw instanceof Map<?, ?> map) {
            Object s = map.get("status");
            status = s instanceof Number number ? number.intValue() : 0;
            Object b = map.get("body");
            body = b == null ? "" : String.valueOf(b);
        }
        String sentHeaders = headers.entrySet().stream()
                .map(e -> e.getKey() + "=" + e.getValue())
                .reduce((a, b) -> a + "・" + b).orElse("なし");
        machine("api-request", desc + "（合成した HTTP 要求）",
                method + " " + urlPath + " を送出（ヘッダー: " + sentHeaders + "）",
                method + " " + urlPath + " → HTTP " + status, true, "");
        return new CapturedResponse(method, urlPath, status,
                body.getBytes(StandardCharsets.UTF_8), false);
    }

    /**
     * フォルダをドロップする {@code drop} イベントを合成して発火するスクリプト。アプリが読む
     * {@code dataTransfer.files}・{@code dataTransfer.items[0].webkitGetAsEntry()} の双方を備える。 files
     * に載せるのは実ドロップと同様「フォルダを表す疑似 File」（フォルダ名・サイズ0・type なし） であり、フォルダの中身は載せない。
     */
    private static final String DROP_FOLDER_SCRIPT = """
            (el, folderName) => {
              const pseudo = new File([], folderName);
              const holder = new DataTransfer();
              holder.items.add(pseudo);
              const entry = {isDirectory: true, isFile: false, name: folderName};
              const dataTransfer = {
                files: holder.files,
                types: ['Files'],
                items: [{kind: 'file', type: '',
                         webkitGetAsEntry: () => entry, getAsFile: () => pseudo}]
              };
              const event = new Event('drop', {bubbles: true, cancelable: true});
              Object.defineProperty(event, 'dataTransfer', {value: dataTransfer});
              el.dispatchEvent(event);
              return {isDirectory: entry.isDirectory, fileCount: dataTransfer.files.length,
                      folderName: dataTransfer.files[0].name};
            }
            """;

    /**
     * ファイルをドロップする {@code drop} イベントを合成して発火するスクリプト（{@link #DROP_FOLDER_SCRIPT} の
     * ファイル版）。実ファイルの内容（Base64）から {@code File} を組み立て、{@code dataTransfer.files}・
     * ファイルを返す {@code webkitGetAsEntry()} を持つ {@code items} を与える。
     */
    private static final String DROP_FILE_SCRIPT = """
            (el, arg) => {
              const bin = atob(arg.bytesBase64);
              const bytes = new Uint8Array(bin.length);
              for (let i = 0; i < bin.length; i++) bytes[i] = bin.charCodeAt(i);
              const file = new File([bytes], arg.name);
              const holder = new DataTransfer();
              holder.items.add(file);
              const entry = {isDirectory: false, isFile: true, name: arg.name};
              const dataTransfer = {
                files: holder.files,
                types: ['Files'],
                items: [{kind: 'file', type: '',
                         webkitGetAsEntry: () => entry, getAsFile: () => file}]
              };
              const event = new Event('drop', {bubbles: true, cancelable: true});
              Object.defineProperty(event, 'dataTransfer', {value: dataTransfer});
              el.dispatchEvent(event);
              return {isFile: entry.isFile, fileCount: dataTransfer.files.length,
                      fileName: dataTransfer.files[0].name, size: dataTransfer.files[0].size};
            }
            """;

    private void opStep(String desc, Locator target) {
        setOverlayStep("【操作】" + desc);
        logEvent(true, "【操作】" + desc);
        if (!recording()) {
            return;
        }
        List<Map<String, Object>> rects = null;
        String note = null;
        if (target != null) {
            if (awaitPresent(target)) {
                frames.reveal(target);
                frames.ensureInViewport(target, desc);
                rects = frames.highlight(List.of(target), E2eFrameService.COLOR_OP,
                        "rgba(37,99,235,.22)");
            } else {
                // 操作自体は完了している。対象の不在は後続の検証ステップが期待／実測の対で受ける。
                note = missingNote("操作結果が現れる要素");
            }
        }
        int seq = recorder.nextSeq();
        E2eStepRecorder.Step step = new E2eStepRecorder.Step();
        step.seq = seq;
        step.kind = "op";
        step.desc = desc;
        step.ts = E2eStepRecorder.nowTs();
        step.frame = recorder.frameName(seq);
        step.url = frames.currentPath();
        step.highlight = rects;
        step.note = note;
        frames.capture(step.frame);
        frames.clearOverlays();
        recorder.add(step);
    }

    /**
     * 画面を開く操作へ target（青ハイライト）を流用する再発パターンを構造的に弾く。読込完了の目印として
     * ヘッダー・タイトル等へ青枠を引くと「何の操作結果か」が誤読されるため、開く操作は {@link #opOpen} に限る。
     */
    private static void requireResultTarget(String desc) {
        if (desc.contains("アドレスバー")) {
            throw new IllegalArgumentException(
                    "画面を開く操作は opOpen を使う（target は操作で値・状態が変化した要素に限り、読込完了の目印にしない）: "
                            + desc);
        }
    }

    /** 押下対象の存在を確認し、無ければ fail の操作ステップを記録して中断する（クリックの長い既定待ちを避ける）。 */
    private void requirePressTarget(String desc, Locator control) {
        if (awaitPresent(control)) {
            return;
        }
        recordFailedOp(desc + "（押下前・操作対象）", missingNote("押下する操作対象の要素"));
        throw new AssertionError("操作対象の要素が画面に存在しない: " + desc);
    }

    /** バイパス対象の存在を確認し、無ければ【機械検証】の fail として記録して中断する。 */
    private void requireBypassTarget(String desc, Locator target) {
        if (awaitPresent(target)) {
            return;
        }
        machine("bypass", desc + "（対象要素の存在）", "存在する", "画面に存在しない", false,
                "バイパス対象の要素が画面に存在しない");
    }

    /** 操作を実行し、Playwright 例外（要素不在・タイムアウト等）も fail の操作ステップとして記録してから送出する。 */
    private void runAction(String desc, Runnable action) {
        try {
            action.run();
        } catch (PlaywrightException error) {
            recordFailedOp(desc, "操作が失敗した: " + excerpt(error));
            throw error;
        }
    }

    /** 失敗した操作ステップを現画面のフレーム付きで記録する。 */
    private void recordFailedOp(String desc, String note) {
        logEvent(false, "【操作】" + desc + " — " + note);
        if (!recording()) {
            return;
        }
        int seq = recorder.nextSeq();
        E2eStepRecorder.Step step = new E2eStepRecorder.Step();
        step.seq = seq;
        step.kind = "op";
        step.desc = desc;
        step.ts = E2eStepRecorder.nowTs();
        step.frame = recorder.frameName(seq);
        step.url = frames.currentPath();
        step.note = note;
        step.status = "fail";
        frames.capture(step.frame);
        frames.clearOverlays();
        recorder.add(step);
    }

    // ---- 単体の可視検証 ----

    /**
     * 要素が表示されていることを検証する（ハイライト＋フレーム）。
     *
     * @param desc 何を確認するか
     * @param target 検証対象
     */
    public void visible(String desc, Locator target) {
        verifyLocator("visible", desc, target, "表示", "表示", () -> assertThat(target).isVisible());
    }

    /**
     * 要素のテキストを検証する（ハイライト＋フレーム）。
     *
     * @param desc 何を確認するか
     * @param target 検証対象
     * @param expected 期待するテキスト
     */
    public void text(String desc, Locator target, String expected) {
        verifyLocator("text", desc, target, expected, null,
                () -> assertThat(target).hasText(expected));
    }

    /**
     * 複数要素のテキスト一覧を検証する（ハイライト＋フレーム）。
     *
     * @param desc 何を確認するか
     * @param target 検証対象（複数一致ロケータ）
     * @param expected 期待するテキストの配列
     */
    public void texts(String desc, Locator target, String[] expected) {
        verifyLocator("text", desc, target, String.join(" ／ ", expected), null,
                () -> assertThat(target).hasText(expected));
    }

    /**
     * 要素のテキストが部分文字列を含むことを検証する（ハイライト＋フレーム）。
     *
     * @param desc 何を確認するか
     * @param target 検証対象
     * @param expected 含まれるべき文字列
     */
    public void containsText(String desc, Locator target, String expected) {
        verifyLocator("containsText", desc, target, "「" + expected + "」を含む", null,
                () -> assertThat(target).containsText(expected));
    }

    /**
     * 要素の表示内容が要素幅に収まらず、末尾が省略表現（三点リーダー等）で切り詰められて描画されて
     * いることを検証する（ハイライト＋フレーム）。
     *
     * <p>
     * CSS の text-overflow による省略表現は描画のみで、DOM のテキストは全文のまま変わらない。
     * そのため {@link #text} 等の内容突合では検証できず、本部品が「内容幅が要素幅を超えている」実測
     * （scrollWidth &gt; clientWidth）を規格の一部として機械記録し、省略表現の見た目はハイライト
     * 付きフレームで目視確認できる形に残す。切り詰めが起きていなければ fail として記録する。
     * 対象要素は描画済みであること（仮想化グリッドの列は {@link #gridCellText} 等で画面内へ
     * 入れてから用いる）。
     *
     * @param desc 何を確認するか
     * @param target 検証対象（省略表現が描画される要素そのもの）
     */
    public void textTruncated(String desc, Locator target) {
        long[] widths = new long[2];
        verifyLocator("text-truncated", desc, target,
                "表示内容が要素幅に収まらず切り詰め（末尾に省略表現）", null, () -> {
                    Object value = target.first()
                            .evaluate("el => el.scrollWidth + '/' + el.clientWidth");
                    String[] parts = String.valueOf(value).split("/");
                    widths[0] = Long.parseLong(parts[0]);
                    widths[1] = Long.parseLong(parts[1]);
                    if (widths[0] <= widths[1]) {
                        throw new AssertionError("切り詰めが起きていない（内容幅 " + widths[0]
                                + "px ≦ 要素幅 " + widths[1] + "px）");
                    }
                });
        machine("text-truncated", desc + "（内容幅が要素幅を超えている実測）", "内容幅 > 要素幅",
                "内容幅 " + widths[0] + "px ／ 要素幅 " + widths[1] + "px",
                widths[0] > widths[1], "切り詰めが起きていない");
    }

    /**
     * 対象が基準要素の<b>上方向に配置</b>されていることを検証する（配置は CSS 描画にのみ現れる
     * 視覚表現のため、{@link #textTruncated} と同型——見た目は両要素のハイライト＋フレームで受け、
     * 位置（対象の下端 ≦ 基準の上端）の実測を規格内の機械記録として残す）。
     *
     * <p>
     * 本部品はページをスクロールしない（reveal を行わない）——位置関係はスクロールで変わり得る画角の
     * 前提そのものであり、ポップアップ型の対象（オートコンプリート候補等）はページスクロールで閉じる
     * ため。対象・基準の両方が画面内にある画角を作ってから呼ぶこと。
     *
     * @param desc 何を確認するか
     * @param subject 検証対象（上に配置されるはずの要素。例: 選択肢の一覧）
     * @param anchor 基準要素（例: 入力欄）
     */
    public void positionedAbove(String desc, Locator subject, Locator anchor) {
        double[] edges = new double[2];
        List<Map<String, Object>> rects = null;
        String note = "対象（上）と基準（下）の両方をハイライトし、対象の下端が基準の上端より上にあることの実測を規格内の機械記録として残す";
        if (recording()) {
            if (awaitPresent(subject) && awaitPresent(anchor)) {
                frames.ensureInViewport(subject, desc + "（対象）");
                frames.ensureInViewport(anchor, desc + "（基準）");
                rects = frames.highlight(List.of(subject, anchor));
            } else {
                note = missingNote("位置関係の検証対象または基準の要素");
            }
        }
        runVerify("positioned-above", desc, "対象の下端 ≦ 基準の上端", null, rects, note, null,
                () -> {
                    BoundingBox subjectBox = subject.first().boundingBox();
                    BoundingBox anchorBox = anchor.first().boundingBox();
                    if (subjectBox == null || anchorBox == null) {
                        throw new AssertionError("要素の位置（boundingBox）が取得できない: " + desc);
                    }
                    edges[0] = subjectBox.y + subjectBox.height;
                    edges[1] = anchorBox.y;
                    if (edges[0] > edges[1] + 0.5) {
                        throw new AssertionError("対象が基準の上に配置されていない（対象下端 " + edges[0]
                                + "px > 基準上端 " + edges[1] + "px）");
                    }
                }, true);
        machine("positioned-above", desc + "（位置の実測）", "対象の下端 ≦ 基準の上端",
                "対象下端 " + edges[0] + "px ／ 基準上端 " + edges[1] + "px",
                edges[0] <= edges[1] + 0.5, "対象が基準の上に配置されていない");
    }

    /**
     * 対象が基準要素と<b>同じテキスト</b>を表示していることを検証する（期待値が実行時にしか確定
     * しない値の要素間比較。{@link #positionedAbove} と同型——見た目は両要素のハイライト＋フレームで
     * 受け、両要素の実測テキストとその一致を規格内の機械記録として残す）。
     *
     * <p>
     * 「更新日時は登録日時と同じ値が表示される」等、期待値が操作時刻に依存して事前に確定できない
     * 期待結果に用いる。固定の期待値と突合できる場合は {@link #text} を使う。部品は両要素が同一
     * 画角に収まるようスクロールを調整（{@link E2eFrameService#revealTogether}）してから見切れ検査を
     * 行う。両要素の外接範囲が1画角に収まらない配置は見切れの失敗として記録される。
     *
     * @param desc 何を確認するか
     * @param subject 検証対象（基準と同じ表示になるはずの要素。例: 更新日時）
     * @param reference 基準要素（例: 登録日時）
     */
    public void sameText(String desc, Locator subject, Locator reference) {
        sameText(desc, subject, reference, null);
    }

    /**
     * {@link #sameText(String, Locator, Locator)} の抽出付き版。片方または両方が複合表示
     * （例: 添付ファイル一覧の「{登録者}({登録部署}) – {登録日時}」）で、比較したい値が表示の
     * 一部でしかない場合に、両要素の表示テキストへ同じ正規表現を適用して最初の一致部分どうしを
     * 比較する（一致箇所が無い側は失敗として記録する）。
     *
     * @param desc 何を確認するか
     * @param subject 検証対象（基準と同じ値を表示するはずの要素）
     * @param reference 基準要素
     * @param extract 比較部分の抽出用正規表現（{@code null} なら表示テキスト全体を比較する）
     */
    public void sameText(String desc, Locator subject, Locator reference, Pattern extract) {
        String expected = extract == null ? "対象のテキスト＝基準のテキスト"
                : "対象と基準の /" + extract.pattern() + "/ の一致部分が同じ値";
        String[] texts = new String[2];
        List<Map<String, Object>> rects = null;
        String note = "対象と基準の両方をハイライトし、両要素の表示テキストが一致する実測を規格内の機械記録として残す";
        if (recording()) {
            if (awaitPresent(subject) && awaitPresent(reference)) {
                frames.revealTogether(subject, reference);
                frames.ensureInViewport(subject, desc + "（対象）");
                frames.ensureInViewport(reference, desc + "（基準）");
                rects = frames.highlight(List.of(subject, reference));
            } else {
                note = missingNote("テキスト比較の対象または基準の要素");
            }
        }
        runVerify("same-text", desc, expected, null, rects, note, null, () -> {
            texts[0] = comparedText(extract, subject.first().innerText(), "対象");
            texts[1] = comparedText(extract, reference.first().innerText(), "基準");
            if (!texts[0].equals(texts[1])) {
                throw new AssertionError("対象と基準のテキストが一致しない（対象「" + texts[0]
                        + "」／基準「" + texts[1] + "」）");
            }
        }, true);
        machine("same-text", desc + "（両要素の実測テキスト）", expected,
                "対象「" + texts[0] + "」／基準「" + texts[1] + "」",
                texts[0] != null && texts[0].equals(texts[1]), "対象と基準のテキストが一致しない");
    }

    /** {@link #sameText} の比較対象テキスト（抽出正規表現があれば最初の一致部分、無ければ全体）。 */
    private static String comparedText(Pattern extract, String raw, String side) {
        String text = raw == null ? "" : raw.trim();
        if (extract == null) {
            return text;
        }
        Matcher matcher = extract.matcher(text);
        if (!matcher.find()) {
            throw new AssertionError(side + "のテキストに抽出正規表現 /" + extract.pattern()
                    + "/ の一致箇所が無い（実測「" + text + "」）");
        }
        return matcher.group();
    }

    /**
     * 入力欄の値を検証する（ハイライト＋フレーム）。
     *
     * @param desc 何を確認するか
     * @param target 検証対象
     * @param expected 期待する値
     */
    public void value(String desc, Locator target, String expected) {
        verifyLocator("value", desc, target, expected, null,
                () -> assertThat(target).hasValue(expected));
    }

    /**
     * チェックボックス等がチェックされていることを検証する（ハイライト＋フレーム）。
     *
     * @param desc 何を確認するか
     * @param target 検証対象
     */
    public void checked(String desc, Locator target) {
        verifyLocator("checked", desc, target, "チェックON", "チェックON",
                () -> assertThat(target).isChecked());
    }

    /**
     * チェックボックス等がチェックされていないことを検証する（ハイライト＋フレーム）。
     *
     * @param desc 何を確認するか
     * @param target 検証対象
     */
    public void unchecked(String desc, Locator target) {
        verifyLocator("checked", desc, target, "チェックOFF", "チェックOFF",
                () -> assertThat(target).not().isChecked());
    }

    /**
     * 要素の件数を検証する（1件以上）。0件（無いこと）の検証は {@link #absent} を使う。
     *
     * @param desc 何を確認するか
     * @param target 検証対象（複数一致ロケータ）
     * @param expected 期待する件数（1以上）
     */
    public void countIs(String desc, Locator target, int expected) {
        if (expected == 0) {
            throw new IllegalArgumentException(
                    "0件（無いこと）の検証は absent 部品を使う（文脈アンカー＋領域マーカーで図示する）: " + desc);
        }
        String label = expected + "件";
        verifyLocator("count", desc, target, label, label,
                () -> assertThat(target).hasCount(expected));
    }

    /**
     * 要素が「無いこと」を、実在する文脈要素（アンカー）とセットで検証する（初期状態の不在の一般解）。
     *
     * <p>
     * 無い要素は画面上で指せないため、素通しのスクリーンショットでは「どこを見て無いと判断したか」が
     * 証跡に残らない。本部品は無いことの文脈を示す実在要素（閉じたアコーディオンのバー・列ヘッダー行・
     * フォーム領域等）を可視検証したうえで破線枠＋ラベルで図示し、その文脈で対象が存在しないこと
     * （0件）を機械検証する。DOM に在るが CSS で見えない要素を「無い」と数える場合は、target に
     * {@code :visible} フィルタ等を適用したロケータを渡す。操作による消失（表示→非表示・消去）は
     * transition 部品を使う（前後フレーム必須）。
     *
     * @param desc 何が無いことを確認するか
     * @param context 無いことの文脈を示す実在要素（可視検証し、破線枠＋ラベルで図示する）
     * @param target 無いはずの要素（0件を検証する）
     */
    public void absent(String desc, Locator context, Locator target) {
        List<Map<String, Object>> ghost = null;
        String note = "無いことの文脈を示す実在要素（アンカー）を破線枠で図示し、その文脈で対象が存在しないこと（0件）を機械検証";
        if (recording()) {
            if (awaitPresent(context)) {
                frames.reveal(context);
                // 高さがビューポートを超える文脈要素は許容（戻り値 false。見える範囲に破線を描く）。
                frames.ensureInViewport(context, desc + "（文脈要素）");
                BoundingBox box = context.first().boundingBox();
                if (box != null) {
                    // ラベルは枠外配置（アンカーの実在コンテンツを覆うと目視確認できなくなるため）
                    ghost = frames.ghost(box.x, box.y, box.width, box.height,
                            "この領域に対象の表示なし（0件）", true);
                }
            } else {
                note = missingNote("文脈要素（アンカー）");
            }
        }
        runVerify("absent", desc, "文脈要素が可視・対象は0件（表示なし）", null, ghost, note, null, () -> {
            assertThat(context).isVisible();
            assertThat(target).hasCount(0);
        }, true);
    }

    /**
     * 要素が無効（disabled）であることを検証する（ハイライト＋フレーム）。
     *
     * @param desc 何を確認するか
     * @param target 検証対象
     */
    public void disabled(String desc, Locator target) {
        verifyLocator("disabled", desc, target, "無効", "無効",
                () -> assertThat(target).isDisabled());
    }

    /**
     * 現在の URL を検証する（フレーム内の情報バーに URL が写る）。
     *
     * @param desc 何を確認するか
     * @param expectedUrl 期待する URL
     */
    public void urlIs(String desc, String expectedUrl) {
        String actual = page.url();
        runVerify("url", desc, expectedUrl, actual, null, null, null,
                () -> org.junit.jupiter.api.Assertions.assertEquals(expectedUrl, actual, desc),
                true);
    }

    // ---- 状態遷移（前後フレーム必須） ----

    /**
     * 表示 → 操作 → 非表示 の状態遷移を、前後フレーム付きで検証する（2-1 型の一般解）。
     *
     * <p>
     * 変化前の表示状態をフレームに残し、操作後は同じスクロール位置へ戻して「消えた領域」を 破線ゴースト枠で示したフレームを残す。単発の非表示検証（前状態なし）は認めない。
     *
     * @param beforeDesc 変化前の確認内容（例: メール本文欄が表示されている）
     * @param target 遷移する対象
     * @param actionDesc 操作の説明（例: メール送信チェックを外す）
     * @param action 遷移を起こす操作
     * @param actionTarget 操作の結果が現れる要素（操作フレームに映す）
     * @param afterDesc 変化後の確認内容（例: メール本文欄が非表示になる）
     */
    public void transitionHide(
            String beforeDesc,
            Locator target,
            String actionDesc,
            Runnable action,
            Locator actionTarget,
            String afterDesc) {
        transitionGone("hidden", "非表示", beforeDesc, target,
                () -> op(actionDesc, action, actionTarget), afterDesc,
                () -> assertThat(target).isHidden());
    }

    /**
     * 押下 → 非表示 の状態遷移を、押下前ハイライト付きで検証する（{@link #transitionHide} の押下系）。
     *
     * <p>
     * 画面遷移・画面の作り替えを起こす押下で対象が非表示になる場合に使う。変化前・押下前（操作対象を 青枠で明示）・押下後（消えた領域を破線ゴースト枠で明示）の 3
     * フレームを残し、「操作の再現性」と 「状態遷移の前後」を同時に満たす。実クリックを伴わない合成送信には使わない。
     *
     * @param beforeDesc 変化前の確認内容
     * @param target 遷移する対象
     * @param actionDesc 操作の説明
     * @param control 押下する操作対象（押下前フレームで青枠を引く）
     * @param action 遷移を起こす操作
     * @param actionTarget 操作の結果が現れる要素（押下後フレームに映す）
     * @param afterDesc 変化後の確認内容
     */
    public void transitionHideByPress(
            String beforeDesc,
            Locator target,
            String actionDesc,
            Locator control,
            Runnable action,
            Locator actionTarget,
            String afterDesc) {
        transitionGone("hidden", "非表示", beforeDesc, target,
                () -> opPress(actionDesc, control, action, actionTarget), afterDesc,
                () -> assertThat(target).isHidden());
    }

    /**
     * 表示 → 操作 → 消去（DOM から除去） の状態遷移を、前後フレーム付きで検証する（エラー消去型）。
     *
     * @param beforeDesc 変化前の確認内容（例: 件名の必須エラーが表示されている）
     * @param target 消去される対象
     * @param actionDesc 操作の説明（例: 件名を入力して再送信する）
     * @param action 消去を起こす操作
     * @param actionTarget 操作の結果が現れる要素（操作フレームに映す）
     * @param afterDesc 変化後の確認内容（例: 件名のエラーが消える）
     */
    public void transitionCleared(
            String beforeDesc,
            Locator target,
            String actionDesc,
            Runnable action,
            Locator actionTarget,
            String afterDesc) {
        transitionGone("cleared", "消去（0件）", beforeDesc, target,
                () -> op(actionDesc, action, actionTarget), afterDesc,
                () -> assertThat(target).hasCount(0));
    }

    /**
     * 押下 → 消去（DOM から除去） の状態遷移を、押下前ハイライト付きで検証する （{@link #transitionCleared} の押下系）。
     *
     * <p>
     * 画面遷移で対象が DOM ごと無くなる場合に使う。変化前・押下前（操作対象を青枠で明示）・押下後 （消えた領域を破線ゴースト枠で明示）の 3
     * フレームを残す。実クリックを伴わない合成送信には使わない。
     *
     * @param beforeDesc 変化前の確認内容
     * @param target 消去される対象
     * @param actionDesc 操作の説明
     * @param control 押下する操作対象（押下前フレームで青枠を引く）
     * @param action 消去を起こす操作
     * @param actionTarget 操作の結果が現れる要素（押下後フレームに映す）
     * @param afterDesc 変化後の確認内容
     */
    public void transitionClearedByPress(
            String beforeDesc,
            Locator target,
            String actionDesc,
            Locator control,
            Runnable action,
            Locator actionTarget,
            String afterDesc) {
        transitionGone("cleared", "消去（0件）", beforeDesc, target,
                () -> opPress(actionDesc, control, action, actionTarget), afterDesc,
                () -> assertThat(target).hasCount(0));
    }

    /**
     * confirm ダイアログを伴う押下 → 消去（DOM から除去） の状態遷移を検証する （{@link #transitionClearedByPress} の confirm
     * 系。行削除等の確認ダイアログ付き削除に使う）。
     *
     * <p>
     * 変化前（対象を赤枠）・押下前（操作対象を青枠）・押下後（confirm の代理証跡パネル込み）・ 消去確認（破線ゴースト枠）の 4 フレームを残す。confirm
     * 本文の一致は【機械検証】として記録される （{@link #opPressConfirm}）。応答は常に OK（承認）——キャンセル応答で「消えないこと」を確認する
     * ケースは、本部品ではなく {@code opPressConfirm}＋{@code visible} で受ける。
     *
     * @param beforeDesc 変化前の確認内容（対象の同定情報を含める。例: 添付「a.txt」の行が表示されている）
     * @param target 消去される対象
     * @param actionDesc 操作の説明
     * @param control 押下する操作対象（押下前フレームで青枠を引く）
     * @param expectedMessage 期待する confirm ダイアログ本文
     * @param action 消去を起こす操作
     * @param actionTarget 操作の結果が現れる要素（押下後フレームに映す）
     * @param afterDesc 変化後の確認内容
     */
    public void transitionClearedByConfirm(
            String beforeDesc,
            Locator target,
            String actionDesc,
            Locator control,
            String expectedMessage,
            Runnable action,
            Locator actionTarget,
            String afterDesc) {
        transitionGone(
                "cleared", "消去（0件）", beforeDesc, target, () -> opPressConfirm(actionDesc, control,
                        expectedMessage, true, action, actionTarget),
                afterDesc, () -> assertThat(target).hasCount(0));
    }

    private void transitionGone(
            String verifyName,
            String stateLabel,
            String beforeDesc,
            Locator target,
            Runnable actionStep,
            String afterDesc,
            Runnable afterAssert) {
        visible(beforeDesc, target);
        BoundingBox box = recording() ? target.first().boundingBox() : null;
        double scrollBefore = recording() ? frames.scrollTop() : 0;
        if (recording() && box != null) {
            // 消える前に祖先チェーンを記録し、削除後の占有判定（祖先背景＝空／別要素＝流入）に使う。
            frames.markGhostAncestors(target);
        }
        actionStep.run();
        List<Map<String, Object>> ghost = null;
        String note = null;
        if (recording() && box != null) {
            frames.scrollTo(scrollBefore); // 変化前と同じ画角でゴースト枠を示す
            // 削除で別の要素が旧位置へ流入すると枠内に他の内容が写る。誤読を防ぐため占有を機械判定
            // （祖先コンテナの背景は「空」とみなす）し、ラベルと steps.json の note で明示する。
            String occupier = frames.occupierAt(box.x + box.width / 2, box.y + box.height / 2);
            String label = occupier != null ? stateLabel + "を確認（元の表示位置。枠内に見えるのは削除後にこの位置へ流入した別の要素）"
                    : stateLabel + "を確認（元の表示位置）";
            ghost = frames.ghost(box.x, box.y, box.width, box.height, label);
            if (occupier != null) {
                note = "元の表示位置に写っている要素: " + occupier;
            }
        }
        runVerify(verifyName, afterDesc, stateLabel, stateLabel, ghost, note, null, afterAssert,
                true);
    }

    // ---- 画面に出ない対象の可視化（代理証跡） ----

    /**
     * オートコンプリート（datalist）候補を検証する。
     *
     * <p>
     * ネイティブの候補ポップアップは録画・撮影に写らないため、DOM 上の datalist 内容をパネル描画した 【代理証跡】フレームを残し、あわせて候補一覧の一致を機械検証する。
     *
     * @param desc 何を確認するか
     * @param field オートコンプリート入力欄
     * @param datalistId datalist 要素の id
     * @param expected 期待する候補（表示順）
     */
    public void autocompleteOptions(
            String desc,
            Locator field,
            String datalistId,
            List<String> expected) {
        autocompleteOptions(desc, field, datalistId, expected, null);
    }

    /**
     * オートコンプリート（datalist）候補を、期待値正本（CSV）と突合して検証する。
     *
     * <p>
     * 候補が多い場合に用いる。パネルには先頭サンプル＋スキャン対象を表示し、全件は正本との 機械突合で確認する。期待値 CSV はヘッダー1行＋1行1候補（空行は空欄候補として保持）。
     *
     * @param desc 何を確認するか
     * @param field オートコンプリート入力欄
     * @param datalistId datalist 要素の id
     * @param expectedCsv 期待候補の正本 CSV
     */
    public void autocompleteOptions(
            String desc,
            Locator field,
            String datalistId,
            Path expectedCsv) {
        autocompleteOptions(desc, field, datalistId, readExpectedValues(expectedCsv),
                expectedCsv.getFileName().toString());
    }

    @SuppressWarnings("unchecked")
    private void autocompleteOptions(
            String desc,
            Locator field,
            String datalistId,
            List<String> expected,
            String expectedSource) {
        if (recording()) {
            frames.reveal(field);
            frames.ensureInViewport(field, desc);
        }
        List<String> actual = (List<String>) page.locator("#" + datalistId + " option")
                .evaluateAll("els => els.map(e => e.value)");
        String source = expectedSource == null ? "期待リスト（仕様書記載）" : "期待値正本 " + expectedSource;
        // スキャン対象（datalist の id）を必ず明記し、人が同じ確認を再現できるようにする。
        String note = "ネイティブ候補UIは録画・撮影に写らないため、DOM上の datalist#" + datalistId + " の内容を描画した代理証跡";
        List<String> panelItems = actual;
        if (actual.size() > PROXY_PANEL_HEAD) {
            // 全件のフレーム化はしない（先頭サンプル＋全件機械突合の規格）。
            panelItems = actual.subList(0, PROXY_PANEL_HEAD);
            note += "。先頭" + PROXY_PANEL_HEAD + "件のみ表示（以降" + (actual.size() - PROXY_PANEL_HEAD)
                    + "件は省略）・全" + actual.size() + "件を" + source + "と機械突合";
        } else if (expectedSource != null) {
            note += "。全" + actual.size() + "件を" + source + "と機械突合";
        }
        if (recording()) {
            frames.highlight(List.of(field));
            frames.drawProxyPanel(desc, panelItems, note);
        }
        runVerify("autocomplete", desc, String.valueOf(expected), String.valueOf(actual), null,
                note, null,
                () -> org.junit.jupiter.api.Assertions.assertEquals(expected, actual, desc), true);
    }

    /**
     * セレクトボックス（select 要素）の選択肢を検証する。
     *
     * <p>
     * ネイティブの選択肢一覧（ドロップダウン）は録画・撮影に写らないため、DOM 上の {@code option} の
     * 表示ラベルをパネル描画した【代理証跡】フレームを残し、あわせて選択肢一覧（表示順）の一致を
     * 機械検証する（{@link #autocompleteOptions} の select 版・同じ規格）。空欄の選択肢は空文字として
     * 期待に含める。
     *
     * @param desc 何を確認するか
     * @param select セレクトボックス（select 要素）
     * @param expected 期待する選択肢の表示ラベル（表示順）
     */
    public void selectOptions(String desc, Locator select, List<String> expected) {
        selectOptions(desc, select, expected, null);
    }

    /**
     * セレクトボックス（select 要素）の選択肢を、期待値正本（CSV）と突合して検証する。
     *
     * <p>
     * 選択肢が多い場合に用いる。パネルには先頭サンプル＋スキャン対象を表示し、全件は正本との
     * 機械突合で確認する。期待値 CSV はヘッダー1行＋1行1選択肢（空行は空欄の選択肢として保持）。
     *
     * @param desc 何を確認するか
     * @param select セレクトボックス（select 要素）
     * @param expectedCsv 期待選択肢の正本 CSV
     */
    public void selectOptions(String desc, Locator select, Path expectedCsv) {
        selectOptions(desc, select, readExpectedValues(expectedCsv),
                expectedCsv.getFileName().toString());
    }

    @SuppressWarnings("unchecked")
    private void selectOptions(
            String desc,
            Locator select,
            List<String> expected,
            String expectedSource) {
        if (recording()) {
            frames.reveal(select);
            frames.ensureInViewport(select, desc);
        }
        List<String> actual = (List<String>) select.first().locator("option")
                .evaluateAll("els => els.map(e => e.textContent)");
        String source = expectedSource == null ? "期待リスト（仕様書記載）" : "期待値正本 " + expectedSource;
        // スキャン対象（select 要素の option）を必ず明記し、人が同じ確認を再現できるようにする。
        String note = "ネイティブの選択肢一覧は録画・撮影に写らないため、DOM上の select 要素の選択肢（option）の内容を描画した代理証跡";
        List<String> panelItems = actual;
        if (actual.size() > PROXY_PANEL_HEAD) {
            // 全件のフレーム化はしない（先頭サンプル＋全件機械突合の規格）。
            panelItems = actual.subList(0, PROXY_PANEL_HEAD);
            note += "。先頭" + PROXY_PANEL_HEAD + "件のみ表示（以降" + (actual.size() - PROXY_PANEL_HEAD)
                    + "件は省略）・全" + actual.size() + "件を" + source + "と機械突合";
        } else if (expectedSource != null) {
            note += "。全" + actual.size() + "件を" + source + "と機械突合";
        }
        if (recording()) {
            frames.highlight(List.of(select));
            frames.drawProxyPanel(desc, panelItems, note);
        }
        runVerify("select-options", desc, String.valueOf(expected), String.valueOf(actual), null,
                note, null,
                () -> org.junit.jupiter.api.Assertions.assertEquals(expected, actual, desc), true);
    }

    /** 期待候補の正本 CSV（ヘッダー1行＋1行1候補。空行は空欄候補として保持）を読み込む。 */
    private static List<String> readExpectedValues(Path csv) {
        try {
            return Files.readAllLines(csv, StandardCharsets.UTF_8).stream().skip(1).toList();
        } catch (IOException e) {
            throw new UncheckedIOException("期待値CSVの読み込みに失敗しました: " + csv, e);
        }
    }

    /**
     * CSV 等のダウンロード内容を検証する。
     *
     * <p>
     * DL ファイルは画面に表示されないため、実際にダウンロードした内容の先頭行をパネル描画した 【代理証跡】フレームを残し、ファイル名と本文（期待値ファイルとのバイト一致）を機械検証する。
     *
     * @param desc 何を確認するか
     * @param expectedFileName 期待するファイル名
     * @param expectedContent 期待値ファイル（バイト一致で照合する正本）
     * @param download ダウンロードを実行する操作（Page Object の DL メソッド）
     */
    public void csvDownloaded(
            String desc,
            String expectedFileName,
            Path expectedContent,
            Supplier<Download> download) {
        setOverlayStep("【検証】" + desc);
        Download dl = download.get();
        machine("download-name", "ダウンロードファイル名", expectedFileName, dl.suggestedFilename(),
                expectedFileName.equals(dl.suggestedFilename()), "ダウンロードファイル名が期待と異なる");
        byte[] actualBytes;
        byte[] expectedBytes;
        try {
            actualBytes = Files.readAllBytes(dl.path());
            expectedBytes = Files.readAllBytes(expectedContent);
        } catch (IOException e) {
            throw new UncheckedIOException("CSV内容の読み込みに失敗しました: " + expectedContent, e);
        }
        boolean same = Arrays.equals(expectedBytes, actualBytes);
        String sha256 = sha256Hex(actualBytes);
        String actualLabel = same ? "一致（" + actualBytes.length + " bytes・SHA-256=" + sha256 + "）"
                : "不一致（期待 " + expectedBytes.length + " / 実測 " + actualBytes.length
                        + " bytes・実測SHA-256=" + sha256 + "）";
        String note = "DLファイルは画面に表示されないため、実際にダウンロードした内容の先頭行を描画した代理証跡" + "（一致判定は期待値ファイルとのバイト比較）";
        if (recording()) {
            // DL 実物そのものを証跡として保存し、受け入れ者がビューアから開けるようにする。
            pendingArtifact =
                    recorder.saveArtifact(recorder.nextSeq(), dl.suggestedFilename(), actualBytes);
            note += "。DL実物を証跡として保存: " + pendingArtifact;
            note = drawDownloadPanel(desc, actualBytes, note, "期待値ファイルとのバイト一致で機械突合");
        }
        runVerify("download", desc, "期待値ファイルとバイト一致", actualLabel, null, note, null, () -> {
            if (!same) {
                throw new AssertionError(desc + "（" + actualLabel + "）");
            }
        }, true);
    }

    /**
     * ダウンロードしたファイルの**ボディ行数**を検証する（バイト一致の期待値正本が無い場合）。
     *
     * <p>
     * 「出力されるのは表示中の1ページ分ではなく取得済みの全件である」等、仕様の期待値が内容そのもの
     * ではなく行数である場合に用いる。{@link #csvDownloaded} と同じく DL 実物を証跡として保存し、
     * 先頭行のパネルを【代理証跡】フレームとして残したうえで、ファイル名とボディ行数を機械検証する。
     * ボディ行数はヘッダー1行を除いた行数（末尾の改行は行として数えない）。
     *
     * @param desc 何を確認するか（仕様書の【機械検証】行と対応させる）
     * @param expectedFileName 期待するファイル名
     * @param expectedBodyRows 期待するボディ行数（ヘッダー行を除く）
     * @param download ダウンロードを実行する操作（Page Object の DL メソッド）
     */
    public void csvDownloadedBodyRows(
            String desc,
            String expectedFileName,
            int expectedBodyRows,
            Supplier<Download> download) {
        setOverlayStep("【検証】" + desc);
        Download dl = download.get();
        machine("download-name", "ダウンロードファイル名", expectedFileName, dl.suggestedFilename(),
                expectedFileName.equals(dl.suggestedFilename()), "ダウンロードファイル名が期待と異なる");
        byte[] actualBytes;
        try {
            actualBytes = Files.readAllBytes(dl.path());
        } catch (IOException e) {
            throw new UncheckedIOException("DL内容の読み込みに失敗しました: " + dl.suggestedFilename(), e);
        }
        int bodyRows = Math.max(0, countLines(actualBytes) - 1);
        String actualLabel = bodyRows + "行";
        String note = "DLファイルは画面に表示されないため、実際にダウンロードした内容の先頭行を描画した代理証跡"
                + "（判定はヘッダー1行を除いたボディ行数）";
        if (recording()) {
            // DL 実物そのものを証跡として保存し、受け入れ者がビューアから開けるようにする。
            pendingArtifact =
                    recorder.saveArtifact(recorder.nextSeq(), dl.suggestedFilename(), actualBytes);
            note += "。DL実物を証跡として保存: " + pendingArtifact;
            note = drawDownloadPanel(desc, actualBytes, note, "ボディ行数の判定で機械検証");
        }
        runVerify("download", desc, expectedBodyRows + "行", actualLabel, null, note, null, () -> {
            if (bodyRows != expectedBodyRows) {
                throw new AssertionError(
                        desc + "（期待 " + expectedBodyRows + "行 / 実測 " + bodyRows + "行）");
            }
        }, true);
    }

    /**
     * 操作の後にファイルのダウンロードが<b>発生しないこと</b>を検証する（否定観測）。
     *
     * <p>
     * ダウンロードの不発生は画面に現れないため、操作の実行から一定時間（{@value #NEGATIVE_OBSERVATION_WAIT_MS}
     * ms）ダウンロードイベントを観測し、0 件であることを【機械検証】として記録する（{@link #noMailSent} と
     * 同型の規格）。操作の結果（遷移先の画面等）は後続の可視検証で受ける。
     *
     * @param desc 何を確認するか（仕様書の【機械検証】行と対応させる）
     * @param action ダウンロードを発生させないはずの操作（{@code opPress} 等の操作部品呼び出しを渡す）
     */
    public void noDownload(String desc, Runnable action) {
        List<String> downloads = new ArrayList<>();
        Consumer<Download> listener = download -> downloads.add(download.suggestedFilename());
        page.onDownload(listener);
        try {
            action.run();
            page.waitForTimeout(NEGATIVE_OBSERVATION_WAIT_MS);
        } finally {
            page.offDownload(listener);
        }
        machine("no-download", desc,
                "ダウンロードなし（" + (int) NEGATIVE_OBSERVATION_WAIT_MS + "ms 観測）",
                downloads.isEmpty() ? "ダウンロードなし" : "ダウンロードあり: " + String.join("・", downloads),
                downloads.isEmpty(), "発生しないはずのダウンロードが発生した");
    }

    /** DL 実物の先頭行パネルに載せる最大行数。 */
    private static final int DOWNLOAD_PANEL_HEAD = 12;

    /**
     * DL 実物の先頭行を代理証跡パネルに描画する。パネルに載らない行がある場合は、省略の事実と
     * 全行が機械判定の対象であることを脚注として note へ明記する（先頭サンプル＋全件機械突合の
     * 統一様式。証跡規範6）。
     *
     * @param desc 検証の説明（パネル表題に用いる）
     * @param actualBytes DL 実物の内容
     * @param note 代理証跡である旨の注記（脚注を追記して返す）
     * @param machineLabel 全行を受ける機械判定の説明（脚注の文言）
     * @return 脚注を反映した note（steps.json にも同じ文言で残す）
     */
    private String drawDownloadPanel(
            String desc,
            byte[] actualBytes,
            String note,
            String machineLabel) {
        List<String> head = firstLines(actualBytes, DOWNLOAD_PANEL_HEAD);
        int total = countLines(actualBytes);
        if (total > head.size()) {
            note += "。先頭" + head.size() + "行のみ表示（以降" + (total - head.size()) + "行は省略）・全"
                    + total + "行を" + machineLabel;
        }
        frames.drawProxyPanel(desc + "（DL実物の先頭行）", head, note);
        return note;
    }

    /** 内容の行数（末尾の改行は行として数えない）。 */
    private static int countLines(byte[] bytes) {
        String text = new String(bytes, StandardCharsets.UTF_8);
        if (text.isEmpty()) {
            return 0;
        }
        String trimmed = text.replaceAll("(\r\n|\r|\n)+$", "");
        if (trimmed.isEmpty()) {
            return 0;
        }
        return trimmed.split("\r\n|\r|\n", -1).length;
    }

    private static String sha256Hex(byte[] bytes) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 が利用できません", e);
        }
    }

    private static List<String> firstLines(byte[] bytes, int max) {
        String text = new String(bytes, StandardCharsets.UTF_8);
        List<String> lines = new ArrayList<>();
        for (String line : text.split("\r?\n")) {
            if (lines.size() >= max) {
                break;
            }
            lines.add(line);
        }
        return lines;
    }

    // ---- リスト（非仮想化）の可視件数・連続フレーム ----

    /**
     * スクロールするリストで、コンテナの可視領域に<b>完全に収まって表示される</b>アイテム数を検証する。
     *
     * <p>
     * 「一度に表示される件数は N 件（それ以降は領域内に現れない）」型の仕様に対応する。可視領域に
     * 完全に含まれるアイテムを実測で数え、該当アイテムをハイライトしたフレームで受ける（件数の実測は
     * 規格内の機械記録として steps.json に残る）。対象は非仮想化リスト（全アイテムが DOM に存在する）
     * であること。
     *
     * @param desc 何を確認するか
     * @param scrollContainer リストのスクロールコンテナ（可視領域の基準）
     * @param items リストのアイテム（複数一致ロケータ）
     * @param expected 可視領域に完全に収まって表示される期待アイテム数
     */
    public void listVisibleCount(
            String desc,
            Locator scrollContainer,
            Locator items,
            int expected) {
        requireForContinuousFrames("list", desc, "リストの先頭アイテム", items.first());
        if (recording()) {
            frames.reveal(scrollContainer);
            frames.ensureInViewport(scrollContainer, desc);
        }
        BoundingBox view = scrollContainer.first().boundingBox();
        int count = items.count();
        List<Locator> fullyVisible = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            BoundingBox box = items.nth(i).boundingBox();
            if (view != null && box != null && box.y >= view.y - 0.5
                    && box.y + box.height <= view.y + view.height + 0.5) {
                fullyVisible.add(items.nth(i));
            }
        }
        int actual = fullyVisible.size();
        List<Map<String, Object>> rects =
                recording() && !fullyVisible.isEmpty() ? frames.highlight(fullyVisible) : null;
        String note = "コンテナの可視領域に完全に収まって表示されているアイテムを実測で数えた（全 " + count
                + " 件中 " + actual + " 件が完全可視）";
        runVerify("list", desc, expected + "件", actual + "件", rects, note, null, () -> {
            if (actual != expected) {
                throw new AssertionError(
                        desc + "（期待 " + expected + "件 / 実測 " + actual + "件）");
            }
        }, true);
    }

    /**
     * 非仮想化リストのアイテムを、縦スクロール連続フレーム規格で検証する。
     *
     * <p>
     * オートコンプリートの候補一覧など、コンテナ内で縦にスクロールするリストは 1 フレームに全件が
     * 写らないため、可視件数ずつスクロールしながら検証・撮影し、連続する各フレームは直前の末尾 2 件を
     * 重複して含める（{@link #gridRowsByKey} と同じ規格。リストには列・行キーの概念が無いため、
     * アイテムのテキスト自体をキーとして各フレームに写す）。末尾フレームの後に件数の締めとして、
     * 期待件数の次のアイテムが存在しないこと（余剰の不在）を機械検証する（非仮想化のため DOM の
     * 全件数で判定できる）。
     *
     * <p>
     * ページ側のスクロールで閉じるポップアップ型のリスト（オートコンプリート候補等）では、リストを
     * 開く前に入力欄が画面内にある画角を作ってから本部品を呼ぶこと（部品はコンテナ内部のみを
     * スクロールする）。
     *
     * @param desc 何を確認するか
     * @param scrollContainer リストのスクロールコンテナ（{@code scrollTop} を操作する要素）
     * @param items リストのアイテム（複数一致ロケータ・DOM 順）
     * @param expectedTexts 期待するアイテムのテキスト（表示順の全件）
     */
    public void listItemsByText(
            String desc,
            Locator scrollContainer,
            Locator items,
            List<String> expectedTexts) {
        setOverlayStep("【検証】" + desc);
        int total = expectedTexts.size();
        requireForContinuousFrames("list", desc, "リストの先頭アイテム", items.first());
        if (recording()) {
            frames.reveal(scrollContainer); // リストを画面内へ入れ、写る件数を最大化する
        }
        BoundingBox itemBox = items.first().boundingBox();
        BoundingBox viewBox = scrollContainer.first().boundingBox();
        double itemHeight = itemBox == null ? 24 : itemBox.height;
        // コンテナのうち「ページのビューポートに実際に写っている」高さだけを使う（見切れ防止）。
        double pageHeight = page.viewportSize().height;
        double viewTop = viewBox == null ? 0 : Math.max(viewBox.y, 0);
        double viewBottom =
                viewBox == null ? itemHeight : Math.min(viewBox.y + viewBox.height, pageHeight);
        double viewHeight = Math.max(itemHeight, viewBottom - viewTop);
        int visibleRows = Math.max(1, (int) Math.floor(viewHeight / itemHeight));
        int overlap = Math.min(2, visibleRows - 1);
        int stepSize = Math.max(1, visibleRows - overlap);
        int frameCount = total <= visibleRows ? 1
                : 1 + (int) Math.ceil((double) (total - visibleRows) / stepSize);
        int frameIndex = 0;
        int start = 0;
        while (true) {
            frameIndex++;
            scrollContainer.first().evaluate("(el, top) => { el.scrollTop = top; }",
                    start * itemHeight);
            int end = Math.min(start + visibleRows, total) - 1;
            List<Locator> frameItems = new ArrayList<>();
            for (int i = start; i <= end; i++) {
                frameItems.add(items.nth(i));
            }
            String range = expectedTexts.get(start) + "〜" + expectedTexts.get(end);
            String note = "連続フレーム " + frameIndex + "/" + frameCount + "・" + range
                    + (frameIndex > 1 ? "（直前フレームと" + overlap + "件重複）" : "");
            List<Map<String, Object>> rects = recording()
                    ? frames.highlight(frameItems, E2eFrameService.COLOR_VERIFY,
                            "rgba(225,29,72,.22)", false) // バッジはテキストを覆うため枠のみ
                    : null;
            // アイテムの照合は runVerify の内側で行う（外側で素のアサーションを実行すると、
            // 不一致時にステップ・フレームが記録されないまま失敗し、証跡に失敗が残らない）。
            int frameStart = start;
            runVerify("list", desc + "（" + range + "）", "アイテムが表示順どおり", "一致", rects, note,
                    null, () -> {
                        for (int i = frameStart; i <= end; i++) {
                            assertThat(items.nth(i)).hasText(expectedTexts.get(i));
                        }
                    }, true);
            if (end >= total - 1) {
                break;
            }
            start += stepSize;
        }
        // 件数の締め: 期待件数の次のアイテムが存在しないこと（非仮想化のため全件数で判定できる）。
        int actualCount = items.count();
        machine("list", desc + "（件数の締め）", "全 " + total + " 件（" + total + " 件目の次のアイテムなし）",
                actualCount == total ? "全 " + total + " 件"
                        : "件数 " + actualCount + "（余剰あり）",
                actualCount == total, "期待件数を超えるアイテムが存在する");
    }

    // ---- グリッド（DOM 仮想化領域）の連続フレーム ----

    /**
     * グリッドの行キー列を、縦スクロール連続フレーム規格で検証する。
     *
     * <p>
     * 仮想化グリッド（ag-grid 等）は画面外の行が DOM に存在せず、どの撮影技術でも 1 枚に写らない。
     * ビューポートに入る行数ずつスクロールしながら検証・撮影し、連続する各フレームは直前の末尾 2 行を重複して含める（行の連続性を証跡として保証する）。
     * 末尾フレームの後に行数の締めとして、期待件数の次の行（row-index = N）が存在しないことを機械検証する
     * （期待全件が正順で並んでいても余剰行があれば失敗させる。末尾までスクロールした時点で余剰行は DOM に
     * 描画されるため、行仮想化でも判定できる）。 グリッド構造の解決は {@link E2eGridAdapter} が担う
     * （ライブラリ固有セレクタを部品に持ち込まない）。
     *
     * @param desc 何を確認するか
     * @param grid グリッド構造のアダプタ
     * @param keyColId 行キー列の列識別子（各フレームに必ず写す）
     * @param expectedKeys 期待する行キー（表示順の全件）
     */
    public void gridRowsByKey(
            String desc,
            E2eGridAdapter grid,
            String keyColId,
            List<String> expectedKeys) {
        setOverlayStep("【検証】" + desc);
        int total = expectedKeys.size();
        Locator viewport = grid.verticalViewport();
        // 行ロケータは固定列・中央のコンテナ間で重複し得るため、キー列セル（1要素）で行高を取る。
        Locator firstKeyCell = grid.cell(0, keyColId);
        requireForContinuousFrames("grid", desc, "行キー列の先頭セル", firstKeyCell);
        if (recording()) {
            frames.reveal(viewport); // グリッドを画面内へ入れ、写る行数を最大化する
        }
        BoundingBox rowBox = firstKeyCell.boundingBox();
        BoundingBox viewBox = viewport.boundingBox();
        double rowHeight = rowBox == null ? 42 : rowBox.height;
        // グリッド内部ビューポートのうち「ページのビューポートに実際に写っている」高さだけを使う。
        // グリッドが画面下へはみ出していると、アサートした行がフレームに写らない（見切れ）ため。
        double pageHeight = page.viewportSize().height;
        double viewTop = viewBox == null ? 0 : Math.max(viewBox.y, 0);
        double viewBottom =
                viewBox == null ? rowHeight : Math.min(viewBox.y + viewBox.height, pageHeight);
        double viewHeight = Math.max(rowHeight, viewBottom - viewTop);
        int visibleRows = Math.max(1, (int) Math.floor(viewHeight / rowHeight));
        int overlap = Math.min(2, visibleRows - 1);
        int stepSize = Math.max(1, visibleRows - overlap);
        int frameCount = total <= visibleRows ? 1
                : 1 + (int) Math.ceil((double) (total - visibleRows) / stepSize);
        int frameIndex = 0;
        int start = 0;
        while (true) {
            frameIndex++;
            viewport.evaluate("(el, top) => { el.scrollTop = top; }", start * rowHeight);
            int end = Math.min(start + visibleRows, total) - 1;
            List<Locator> keyCells = new ArrayList<>();
            for (int i = start; i <= end; i++) {
                Locator keyCell = grid.cell(i, keyColId);
                assertThat(keyCell).hasText(expectedKeys.get(i));
                keyCells.add(keyCell);
            }
            String range = keyColId + " " + expectedKeys.get(start) + "〜" + expectedKeys.get(end);
            String note = "連続フレーム " + frameIndex + "/" + frameCount + "・" + range
                    + (frameIndex > 1 ? "（直前フレームと" + overlap + "行重複）" : "");
            List<Map<String, Object>> rects = recording()
                    ? frames.highlight(keyCells, E2eFrameService.COLOR_VERIFY,
                            "rgba(225,29,72,.22)", false) // バッジはセル値を覆うため枠のみ
                    : null;
            runVerify("grid", desc + "（" + range + "）", "行キーが表示順どおり", "一致", rects, note, null,
                    () -> {
                    }, true);
            if (end >= total - 1) {
                break;
            }
            start += stepSize;
        }
        // 行数の締め: 期待件数の次の行が存在しないこと（余剰行の検出）。末尾スクロール済みのため
        // 余剰行があれば DOM に描画されており、行仮想化でも判定できる。
        int beyond = grid.cell(total, keyColId).count();
        machine("grid", desc + "（行数の締め）", "row-index=" + total + " の行なし（全 " + total + " 行）",
                beyond == 0 ? "row-index=" + total + " の行なし"
                        : "row-index=" + total + " の行あり（余剰行）",
                beyond == 0, "期待件数を超える行が存在する");
    }

    /**
     * グリッドの列ヘッダーの並びを、横スクロール連続フレーム規格で検証する。
     *
     * <p>
     * 列が仮想化されるグリッドは画面外の列が DOM に存在せず、1 フレームに全列は写らない。事前走査で
     * 全列の位置・幅・表示名を測って期待列名と全件突合（機械記録）し、撮影計画（フレーム数・スクロール
     * 位置）を確定してから、フレームごとに「ヘッダー窓に完全に見えている列ヘッダー」全てを期待列名と
     * web-first で突合してハイライトする。連続する各フレームは直前フレームの末尾 1 列を重複して含める
     * （列の連続性の証跡）。固定（ピン留め）列は全フレームに表示されるため、毎フレーム期待名と突合する。
     * ハイライトのバッジ番号は固定列を含む全列の列順の番号であり、フィルム全体を通して 1→N へ一度だけ進む。
     * 固定列（列順の 1..P）は最初のフレームでのみ番号を付け、以降のフレームでは枠のみで示す（期待名との
     * 突合は毎フレーム行う）。隣接フレームの重複列は同一番号で両フレームに現れ、継ぎ目の連続性を示す。
     * 本採番は列の順序確認（本部品）に固有の規格であり、他のグリッド証跡には適用しない。
     *
     * <p>
     * 列幅がヘッダー窓より大きい列は完全可視にできないため、その列だけの単独フレームとし、要素単体
     * ショットを補助フレームとして添付する（見切れの明示）。検証後は横スクロールを左端へ戻す。
     *
     * @param desc 何を確認するか
     * @param grid グリッド構造のアダプタ
     * @param expectedHeaders 期待する列ヘッダーの表示名（固定列を含む左からの全列・表示順）
     */
    public void gridColumnHeaders(String desc, E2eGridAdapter grid, List<String> expectedHeaders) {
        setOverlayStep("【検証】" + desc);
        Locator area = grid.scrollableHeaderArea();
        requireForContinuousFrames("grid-columns", desc, "列ヘッダー領域", area);
        if (recording()) {
            frames.reveal(area); // ヘッダー行を画面内へ入れる（縦方向）
            frames.ensureInViewport(area, desc);
        }
        List<Locator> pinnedCells = sortedByX(grid.pinnedHeaderCells());
        if (pinnedCells.size() >= expectedHeaders.size()) {
            throw new IllegalStateException("固定列数（" + pinnedCells.size() + "）が期待列数（"
                    + expectedHeaders.size() + "）以上です: " + desc);
        }
        final List<String> pinnedExpected = expectedHeaders.subList(0, pinnedCells.size());
        final List<String> scrollExpected =
                expectedHeaders.subList(pinnedCells.size(), expectedHeaders.size());
        Locator viewport = grid.horizontalViewport();
        BoundingBox areaBox = area.boundingBox();
        if (areaBox == null) {
            throw new IllegalStateException("列ヘッダー領域の座標が取得できません: " + desc);
        }
        double range = scrollRange(viewport);
        // 事前走査: 全列の位置・幅・表示名を収集し、期待列名（固定列を除く）と全件を機械突合する
        List<GridColumn> columns = scanGridColumns(grid, viewport, area, areaBox, range);
        List<String> scannedNames = columns.stream().map(GridColumn::name).toList();
        machine("grid-columns", desc + "（走査した列の数と並び・固定列を除く）",
                scrollExpected.size() + "列: " + String.join("／", scrollExpected),
                scannedNames.size() + "列: " + String.join("／", scannedNames),
                scannedNames.equals(scrollExpected), "列ヘッダーの並びが期待と一致しない");
        // 撮影計画: ヘッダー窓に完全に収まる列で区切り、直前フレームの末尾1列を次フレームへ重複させる
        List<int[]> plan = new ArrayList<>();
        int start = 0;
        while (start < columns.size()) {
            double windowStart = columns.get(start).left();
            int end = start;
            while (end + 1 < columns.size() && columns.get(end + 1).left()
                    + columns.get(end + 1).width() <= windowStart + areaBox.width + 1) {
                end++;
            }
            plan.add(new int[] {start, end});
            if (end >= columns.size() - 1) {
                break;
            }
            start = end == start ? end + 1 : end; // 窓超過の単独列は重複を作れない
        }
        int frameCount = plan.size();
        boolean[] covered = new boolean[scrollExpected.size()];
        int previousLast = -1;
        for (int index = 0; index < frameCount; index++) {
            int[] window = plan.get(index);
            double target = Math.min(columns.get(window[0]).left(), range);
            // 位置の対応付けは要求値ではなく、セル計測と同じ座標系（ヘッダー窓）の実測値で行う
            double actual = settleScrollLeft(viewport, area, target);
            for (int p = 0; p < pinnedCells.size(); p++) {
                assertThat(grid.headerLabel(pinnedCells.get(p))).hasText(pinnedExpected.get(p));
            }
            List<RenderedColumn> visible = fullyVisibleColumns(grid, areaBox, columns, actual);
            List<Locator> highlightTargets = new ArrayList<>(pinnedCells);
            List<Integer> highlightBadges = new ArrayList<>();
            for (int p = 1; p <= pinnedCells.size(); p++) {
                // 固定列は列順の 1..P として最初のフレームでのみ数え、以降は枠のみ（番号は
                // フィルム全体で 1→N へ一度だけ進む）。期待名との突合は毎フレーム行う。
                highlightBadges.add(index == 0 ? p : 0);
            }
            Locator elementShot = null;
            String rangeLabel;
            String note;
            if (visible.isEmpty()) {
                // ヘッダー窓より幅の広い列: 完全可視にできないため単独フレーム＋要素単体ショット
                int colIndex = window[0];
                Locator cell = renderedCellFor(grid, areaBox, columns, actual, colIndex);
                assertThat(grid.headerLabel(cell)).hasText(scrollExpected.get(colIndex));
                covered[colIndex] = true;
                highlightTargets.add(cell);
                highlightBadges.add(pinnedCells.size() + colIndex + 1);
                elementShot = cell;
                rangeLabel = scrollExpected.get(colIndex);
                note = "連続フレーム " + (index + 1) + "/" + frameCount + "・列「"
                        + scrollExpected.get(colIndex) + "」は列幅がヘッダー窓を超えるため単独フレーム"
                        + "（要素単体ショット添付・直前フレームとの重複なし）" + pinnedNote(pinnedExpected);
                previousLast = colIndex;
            } else {
                for (RenderedColumn column : visible) {
                    assertThat(grid.headerLabel(column.cell()))
                            .hasText(scrollExpected.get(column.colIndex()));
                    covered[column.colIndex()] = true;
                    highlightTargets.add(column.cell());
                    highlightBadges.add(pinnedCells.size() + column.colIndex() + 1);
                }
                int first = visible.get(0).colIndex();
                int last = visible.get(visible.size() - 1).colIndex();
                rangeLabel = scrollExpected.get(first) + "〜" + scrollExpected.get(last);
                int overlap = previousLast < 0 ? 0 : Math.max(0, previousLast - first + 1);
                String continuity = index == 0 ? ""
                        : (overlap > 0 ? "（直前フレームと" + overlap + "列重複）"
                                : "（直前は列幅超過の単独フレームのため重複なし）");
                note = "連続フレーム " + (index + 1) + "/" + frameCount + "・完全可視の "
                        + visible.size() + " 列を検証" + continuity + pinnedNote(pinnedExpected);
                previousLast = last;
            }
            List<Map<String, Object>> rects = recording()
                    ? frames.highlightNumbered(highlightTargets, highlightBadges,
                            E2eFrameService.COLOR_VERIFY, "rgba(225,29,72,.22)")
                    : null;
            runVerify("grid-columns", desc + "（連続フレーム " + (index + 1) + "/" + frameCount
                    + "・列範囲 " + rangeLabel + "）", "列ヘッダーが表示順どおり", "一致", rects, note,
                    elementShot, () -> {
                    }, true);
        }
        setScrollLeft(viewport, 0);
        page.waitForTimeout(SCROLL_SETTLE_MS);
        long coveredCount = 0;
        for (boolean flag : covered) {
            if (flag) {
                coveredCount++;
            }
        }
        machine("grid-columns", desc + "（連続フレームによる全列の被覆・固定列を除く）",
                "全" + scrollExpected.size() + "列", coveredCount + "列",
                coveredCount == scrollExpected.size(), "連続フレームで全列を写せていない");
    }

    /**
     * 仮想化で DOM に存在しない可能性のある列のセルの表示内容を、列を画面内へ入れてから検証する
     * （ハイライト＋フレーム）。
     *
     * <p>
     * 列仮想化グリッドでは対象列が横スクロール位置によって DOM から外れ、素の {@link #text} では
     * ロケータが解決できない。本部品が列の描画位置まで横スクロールしてヘッダー窓内へ寄せ、その後は
     * 通常の可視検証（reveal＋見切れ検査＋赤ハイライト＋フレーム）として記録する。
     *
     * @param desc 何を確認するか
     * @param grid グリッド構造のアダプタ
     * @param rowIndex 0 起点の表示行インデックス
     * @param colId 列の識別子
     * @param expected 期待する表示内容
     */
    public void gridCellText(
            String desc,
            E2eGridAdapter grid,
            int rowIndex,
            String colId,
            String expected) {
        if (!revealColumn(grid, colId)) {
            // 列自体が実装に存在しない乖離。現画面のフレーム付きの fail として記録してから中断する。
            runVerify("text", desc, expected, null, null,
                    "対象の列（" + colId + "）が横スクロール全域を走査しても描画されない。現画面のフレームを記録", null,
                    () -> {
                        throw new AssertionError("列が画面に存在しない: " + colId);
                    }, true);
        }
        Locator cell = grid.cell(rowIndex, colId);
        verifyLocator("text", desc, cell, expected, null, () -> assertThat(cell).hasText(expected));
    }

    /** 事前走査で得た 1 列の情報（横スクロール座標系の左端・幅・表示名）。 */
    private record GridColumn(double left, double width, String name) {
    }

    /** 現在描画されている列ヘッダーセルと、事前走査上の列番号・画面上 x 座標の対応。 */
    private record RenderedColumn(int colIndex, Locator cell, double x) {
    }

    /** 固定列の注記（固定列が無ければ空文字列）。 */
    private static String pinnedNote(List<String> pinnedExpected) {
        return pinnedExpected.isEmpty() ? ""
                : "。固定列（" + String.join("・", pinnedExpected) + "）は毎フレーム表示・検証";
    }

    /** ヘッダーセル群を画面上の x 座標順に並べたリストにする。 */
    private static List<Locator> sortedByX(Locator cells) {
        List<RenderedColumn> items = new ArrayList<>();
        int count = cells.count();
        for (int i = 0; i < count; i++) {
            Locator cell = cells.nth(i);
            BoundingBox box = cell.boundingBox();
            if (box != null && box.width > 0) {
                items.add(new RenderedColumn(-1, cell, box.x));
            }
        }
        items.sort(Comparator.comparingDouble(RenderedColumn::x));
        return items.stream().map(RenderedColumn::cell).toList();
    }

    /** 横スクロールしながら全列の位置・幅・表示名を収集する（終了時に左端へ戻す）。 */
    private List<GridColumn> scanGridColumns(
            E2eGridAdapter grid,
            Locator viewport,
            Locator area,
            BoundingBox areaBox,
            double range) {
        List<GridColumn> raw = new ArrayList<>();
        double step = Math.max(120, areaBox.width / 2);
        double position = 0;
        while (true) {
            double target = Math.min(position, range);
            // 位置の計算は要求値（target）ではなく、セル計測と同じ座標系（ヘッダー窓自身）の
            // 実スクロール量で行う。グリッドのスクロール発生源調停等で要求が反映されなかった場合、
            // 要求値で計算すると同一列が別位置として重複記録され、実在列が重複統合に飲み込まれる。
            double actual = settleScrollLeft(viewport, area, target);
            Locator cells = grid.scrollableHeaderCells();
            int count = cells.count();
            for (int i = 0; i < count; i++) {
                Locator cell = cells.nth(i);
                BoundingBox box = cell.boundingBox();
                if (box == null || box.width <= 0) {
                    continue;
                }
                Locator label = grid.headerLabel(cell);
                String name = label.count() > 0 ? label.first().textContent() : "";
                raw.add(new GridColumn(actual + box.x - areaBox.x, box.width,
                        name == null ? "" : name.strip()));
            }
            if (position >= range) {
                break;
            }
            position += step;
        }
        raw.sort(Comparator.comparingDouble(GridColumn::left));
        List<GridColumn> columns = new ArrayList<>();
        for (GridColumn column : raw) {
            // 同一列を別スクロール位置で重複測定した分（±数px）をまとめる。隣接列は最小列幅ぶん
            // 離れる前提で、40px 未満の間隔は同一列とみなす。
            if (!columns.isEmpty() && column.left() - columns.get(columns.size() - 1).left() < 40) {
                continue;
            }
            columns.add(column);
        }
        setScrollLeft(viewport, 0);
        page.waitForTimeout(SCROLL_SETTLE_MS);
        return columns;
    }

    /** 現在ヘッダー窓に完全に収まって見えているスクロール領域の列を、事前走査の列番号と対応付けて返す。 */
    private List<RenderedColumn> fullyVisibleColumns(
            E2eGridAdapter grid,
            BoundingBox areaBox,
            List<GridColumn> columns,
            double scrollLeft) {
        List<RenderedColumn> result = new ArrayList<>();
        Locator cells = grid.scrollableHeaderCells();
        int count = cells.count();
        for (int i = 0; i < count; i++) {
            Locator cell = cells.nth(i);
            BoundingBox box = cell.boundingBox();
            if (box == null || box.width <= 0) {
                continue;
            }
            boolean fits = box.x >= areaBox.x - 1
                    && box.x + box.width <= areaBox.x + areaBox.width + 1;
            if (!fits) {
                continue;
            }
            int colIndex = matchColumn(columns, scrollLeft + box.x - areaBox.x);
            if (colIndex >= 0) {
                result.add(new RenderedColumn(colIndex, cell, box.x));
            }
        }
        result.sort(Comparator.comparingDouble(RenderedColumn::x));
        return result;
    }

    /** 画面上の左端座標（横スクロール座標系）から事前走査の列番号を引く（±20px 許容）。 */
    private static int matchColumn(List<GridColumn> columns, double left) {
        for (int i = 0; i < columns.size(); i++) {
            if (Math.abs(columns.get(i).left() - left) <= 20) {
                return i;
            }
        }
        return -1;
    }

    /** 指定した列番号の列がいま描画されていればそのヘッダーセルを返す（完全可視でなくてよい）。 */
    private Locator renderedCellFor(
            E2eGridAdapter grid,
            BoundingBox areaBox,
            List<GridColumn> columns,
            double scrollLeft,
            int colIndex) {
        Locator cells = grid.scrollableHeaderCells();
        int count = cells.count();
        for (int i = 0; i < count; i++) {
            Locator cell = cells.nth(i);
            BoundingBox box = cell.boundingBox();
            if (box == null) {
                continue;
            }
            if (matchColumn(columns, scrollLeft + box.x - areaBox.x) == colIndex) {
                return cell;
            }
        }
        throw new IllegalStateException("列が描画されていません: " + columns.get(colIndex).name());
    }

    /**
     * 仮想化で描画されていない列を、横スクロールで描画させてヘッダー窓の中央へ寄せる。
     *
     * @return 列が描画されれば真（横スクロール全域を走査しても現れなければ偽）
     */
    private boolean revealColumn(E2eGridAdapter grid, String colId) {
        Locator header = grid.headerCell(colId);
        Locator viewport = grid.horizontalViewport();
        Locator area = grid.scrollableHeaderArea();
        double range = scrollRange(viewport);
        if (header.count() == 0) {
            for (double left = 0; left <= range && header.count() == 0; left += 240) {
                settleScrollLeft(viewport, area, Math.min(left, range));
            }
            if (header.count() == 0) {
                return false;
            }
        }
        BoundingBox areaBox = area.boundingBox();
        BoundingBox cellBox = header.first().boundingBox();
        if (areaBox == null || cellBox == null
                || cellBox.x + cellBox.width <= areaBox.x + 1) {
            return true; // 固定（ピン留め）列・座標が取れない場合は横位置決め不要
        }
        // 中央寄せの起点も、セル座標と同じ描画状態を映すヘッダー窓の実測スクロール量から取る
        double current = currentScrollLeft(area);
        double delta = (cellBox.x + cellBox.width / 2) - (areaBox.x + areaBox.width / 2);
        double target = Math.max(0, Math.min(range, current + delta));
        settleScrollLeft(viewport, area, target);
        return true;
    }

    /**
     * 横スクロールを設定し、ヘッダー窓の実スクロール量が要求値へ追従するまで待って実測値を返す。
     *
     * <p>
     * グリッドはスクロール発生源を調停していることがあり（直前の別発生源のスクロールが生きている間、
     * スクロールバーへの設定が本体・ヘッダーへ反映されない）、設定した値と実際の描画位置がずれ得る。
     * 反映の成否を、セル計測と同じ座標系であるヘッダー窓自身の scrollLeft で実測確認し、未達なら
     * 設定を再送する。それでも追従しない場合は現在の実測値を返す（呼び出し側は実測値で座標を計算
     * するため、記録の正しさは保たれる）。
     *
     * @param viewport 横スクロールコンテナ（スクロールの設定先）
     * @param area 列ヘッダー領域（実測の読み取り元）
     * @param target 要求スクロール位置
     * @return 待機後の実スクロール量
     */
    private double settleScrollLeft(Locator viewport, Locator area, double target) {
        double actual = 0;
        for (int attempt = 0; attempt < 3; attempt++) {
            if (attempt > 0) {
                // 同値の再設定は scroll イベントを発火せず再送にならないため、
                // いったん実測値へ戻して（＝値を変えて）から要求値を設定し直す。
                setScrollLeft(viewport, actual);
                page.waitForTimeout(SCROLL_SETTLE_MS);
            }
            setScrollLeft(viewport, target);
            page.waitForTimeout(SCROLL_SETTLE_MS);
            actual = currentScrollLeft(area);
            if (Math.abs(actual - target) <= 2) {
                return actual;
            }
        }
        logEvent(true, "【走査】横スクロールが要求値へ追従しない（要求 " + target + "／実測 " + actual
                + "）。実測値で座標対応を継続");
        return actual;
    }

    /** 横スクロールコンテナの scrollLeft を設定する。 */
    private static void setScrollLeft(Locator viewport, double left) {
        viewport.first().evaluate("(el, v) => { el.scrollLeft = v; }", left);
    }

    /** 横スクロールコンテナの現在の scrollLeft を返す。 */
    private static double currentScrollLeft(Locator viewport) {
        Object value = viewport.first().evaluate("el => el.scrollLeft");
        return value instanceof Number number ? number.doubleValue() : 0;
    }

    /** 横スクロールコンテナのスクロール可能量（scrollWidth - clientWidth）を返す。 */
    private static double scrollRange(Locator viewport) {
        Object value = viewport.first().evaluate("el => el.scrollWidth - el.clientWidth");
        return value instanceof Number number ? number.doubleValue() : 0;
    }

    // ---- メール（受信箱 Web UI） ----

    /**
     * アプリケーションが送信するメールの受信を、受信箱（MailHog Web UI）を開いて検証する。
     *
     * @param mailbox 偽 SMTP の受信箱
     * @param expectedSubject 期待する件名
     * @param expectedToContains 宛先に含まれるべき文字列（確認しないときは null）
     * @param expectedBodyContains 本文に含まれるべき文字列（複数可）
     */
    public void mailReceived(
            E2eMailbox mailbox,
            String expectedSubject,
            String expectedToContains,
            String... expectedBodyContains) {
        boolean received = mailbox.waitForIncomingEmail(5000, 1);
        machine("mail-wait", "メールの受信待機（MailHog API）", "1通以上受信", received ? "受信あり" : "受信なし",
                received, "メールが受信されない");
        MailHogPage inbox = new MailHogPage(page).navigate(mailbox.webUiUrl());
        // 受信箱 UI の描画完了を web-first で待ってから件数を記録し、行クリックの不安定さを避ける。
        assertThat(inbox.messageRows().first()).isVisible();
        int rows = inbox.messageRows().count();
        machine("mail-count", "受信一覧の件数", "1", String.valueOf(rows), rows == 1, "受信メールが1通でない");
        inbox.openLatest();
        opFrame("受信箱（MailHog Web UI）を開き最新メールを表示する");
        text("メール件名が「" + expectedSubject + "」である", inbox.subject(), expectedSubject);
        if (expectedToContains != null) {
            containsText("宛先に " + expectedToContains + " が含まれる", inbox.to(), expectedToContains);
        }
        for (String body : expectedBodyContains) {
            containsText("メール本文に「" + body + "」を含む", inbox.body(), body);
        }
    }

    /**
     * 本文テキスト上の一致範囲（文字オフセット）を DOM Range に解決し、本文ペインのスクロール可能な
     * 祖先を調整して一致範囲を画角（上部情報バー除く）へ入れ、一致範囲の矩形と画角判定を返すスクリプト。
     */
    private static final String REVEAL_TEXT_RANGE_SCRIPT = """
            (el, arg) => {
              const doc = el.ownerDocument;
              const walker = doc.createTreeWalker(el, NodeFilter.SHOW_TEXT);
              const range = doc.createRange();
              let pos = 0, startSet = false, endSet = false;
              for (let node = walker.nextNode(); node; node = walker.nextNode()) {
                const next = pos + node.textContent.length;
                if (!startSet && arg.start < next) {
                  range.setStart(node, arg.start - pos); startSet = true;
                }
                if (startSet && arg.end <= next) {
                  range.setEnd(node, arg.end - pos); endSet = true; break;
                }
                pos = next;
              }
              if (!startSet || !endSet) return null;
              const bar = doc.getElementById('__e2e_overlay__');
              const barH = bar ? bar.offsetHeight : 0;
              const margin = 8;
              let sc = el;
              while (sc && sc !== doc.body) {
                const s = getComputedStyle(sc);
                if (/(auto|scroll)/.test(s.overflowY) && sc.scrollHeight > sc.clientHeight) break;
                sc = sc.parentElement;
              }
              const scrollable = sc && sc !== doc.body;
              const scRect = scrollable ? sc.getBoundingClientRect()
                  : {top: 0, bottom: window.innerHeight};
              const winTop = Math.max(scRect.top, barH) + margin;
              const winBottom = Math.min(scRect.bottom, window.innerHeight) - margin;
              let r = range.getBoundingClientRect();
              let delta = 0;
              if (r.bottom > winBottom) delta = r.bottom - winBottom;
              if (r.top - delta < winTop) delta = r.top - winTop;
              if (delta !== 0) {
                if (scrollable) { sc.scrollTop += delta; } else { window.scrollBy(0, delta); }
              }
              r = range.getBoundingClientRect();
              const fits = r.height > 0 && r.top >= barH && r.bottom <= window.innerHeight;
              return {x: r.x, y: r.y, w: r.width, h: r.height, fits: fits};
            }
            """;

    /**
     * 表示中メール（受信箱 UI で開いているメール）の本文から、正規表現に一致する行を本文ペインの
     * 内部スクロールで画角へ入れ、一致範囲をハイライトしたフレームで可視検証する。
     *
     * <p>
     * MailHog の本文ペイン（{@code #preview-plain}）は枠自体がフレーム内に収まるため要素単位の
     * 見切れ検査を通過するが、長い本文はペイン内部でスクロールし、下部の行はどのフレームにも
     * 写らない。本部品は本文テキスト上の一致範囲を DOM Range に解決し、ペインのスクロールを調整して
     * 一致範囲を画角（上部情報バー除く）へ入れ、一致範囲の矩形を赤枠ハイライトしたフレームを撮る。
     * 一致した実測文字列と画角判定は規格内の機械記録として steps.json に残る（{@code textTruncated}
     * と同型——見た目はハイライト＋フレーム、実測は規格内の機械記録）。
     *
     * <p>
     * 事前に {@link #mailReceived} 等で受信箱 UI を開き、対象メールを表示していること。正規表現は
     * {@link Pattern#MULTILINE}（{@code ^}・{@code $} が行頭・行末に一致）で適用する。値が空欄の
     * 行は「ラベルの後が行末まで空」の一致（例: {@code "^【商流】 *$"}）として行ごと映す。
     *
     * @param desc 何を確認するか（仕様書の期待結果の行と対応させる）
     * @param lineRegex 本文の行に一致すべき正規表現（{@link Pattern} 構文・MULTILINE で適用）
     */
    public void mailBodyLineShown(String desc, String lineRegex) {
        Locator body = new MailHogPage(page).body();
        String expected = "正規表現 /" + lineRegex + "/ に一致する行が本文ペインの画角内に表示される";
        String note = "本文ペインの内部スクロールで一致行を画角へ入れ、一致範囲をハイライトして撮影（一致内容と画角判定は規格内の機械記録）";
        if (!awaitPresent(body)) {
            runVerify("mail-line", desc, expected, null, null, missingNote("メール本文ペイン"), null,
                    () -> {
                        throw new AssertionError("メール本文ペイン（#preview-plain）が表示されない: " + desc);
                    }, true);
            return; // 到達しない（上の runVerify が必ず送出する）
        }
        String text = String.valueOf(body.first().evaluate("el => el.textContent"));
        Matcher matcher = Pattern.compile(lineRegex, Pattern.MULTILINE).matcher(text);
        if (!matcher.find()) {
            runVerify("mail-line", desc, expected, "一致なし", null, note, null, () -> {
                throw new AssertionError(desc + "（本文に /" + lineRegex + "/ に一致する行がない）");
            }, true);
            return; // 到達しない（上の runVerify が必ず送出する）
        }
        String actual = "一致: 「" + matcher.group() + "」";
        Object revealed = body.first().evaluate(REVEAL_TEXT_RANGE_SCRIPT,
                Map.of("start", matcher.start(), "end", matcher.end()));
        boolean fits = false;
        List<Map<String, Object>> rects = null;
        if (revealed instanceof Map<?, ?> r) {
            fits = Boolean.TRUE.equals(r.get("fits"));
            if (recording() && fits) {
                rects = frames.highlightRect(((Number) r.get("x")).doubleValue(),
                        ((Number) r.get("y")).doubleValue(),
                        ((Number) r.get("w")).doubleValue(),
                        ((Number) r.get("h")).doubleValue());
            }
        }
        boolean shown = fits;
        runVerify("mail-line", desc, expected, actual + "（画角内）", rects, note, null, () -> {
            if (!shown) {
                throw new AssertionError("一致行を本文ペインの画角へ入れられない（見切れ）: " + desc);
            }
        }, true);
    }

    /**
     * 受信したメールの送信元（From）・宛先（To）を、受信箱（MailHog Web UI）を開いて検証する。
     *
     * <p>
     * 送信元・宛先はアプリ画面に現れないため、受信箱 UI を実ブラウザで開いてヘッダー（From／To）を
     * 映してから検証する（動画の目視性）。件名で対象メールを取り違えないことも併せて確認する。
     * アドレスは表示名付きで表示される場合があるため、期待アドレスの<b>含有</b>で判定する。
     *
     * @param mailbox 偽 SMTP の受信箱
     * @param expectedSubject 期待する件名
     * @param expectedFrom 送信元に含まれるべきアドレス
     * @param expectedTo 宛先に含まれるべきアドレス
     */
    public void mailAddressesAre(
            E2eMailbox mailbox,
            String expectedSubject,
            String expectedFrom,
            String expectedTo) {
        boolean received = mailbox.waitForIncomingEmail(5000, 1);
        machine("mail-wait", "メールの受信待機（MailHog API）", "1通以上受信", received ? "受信あり" : "受信なし",
                received, "メールが受信されない");
        MailHogPage inbox = new MailHogPage(page).navigate(mailbox.webUiUrl());
        assertThat(inbox.messageRows().first()).isVisible();
        inbox.openLatest();
        opFrame("受信箱（MailHog Web UI）を開き最新メールを表示する");
        text("メール件名が「" + expectedSubject + "」である", inbox.subject(), expectedSubject);
        containsText("メールの送信元（From）に " + expectedFrom + " が含まれる", inbox.from(), expectedFrom);
        containsText("メールの宛先（To）に " + expectedTo + " が含まれる", inbox.to(), expectedTo);
    }

    /**
     * メール本文が指定の正規表現に一致する箇所を含むことを機械検証する。
     *
     * <p>
     * 「【登録日時】が YYYY年MM月DD日 hh時mm分ss秒 の形式である」等、固定値ではなく<b>書式</b>を
     * 検証する場合に用いる。本文の可視検証では書式の妥当性を確定できないため、デコード済みの本文
     * テキストに対する正規表現照合で受ける（対象は最後に受信したメール）。一致した実測部分文字列を
     * 実測として記録する。
     *
     * @param desc 何を確認するか（仕様書の【機械検証】行と対応させる）
     * @param mailbox 偽 SMTP の受信箱
     * @param regex 本文に一致すべき正規表現（{@link Pattern} 構文）
     */
    public void mailBodyMatches(String desc, E2eMailbox mailbox, String regex) {
        String body = latestMailBody(mailbox);
        Matcher matcher = Pattern.compile(regex).matcher(body);
        boolean found = matcher.find();
        String actual = found ? "一致: 「" + matcher.group() + "」" : "一致なし";
        machine("mail-body", desc, "正規表現 /" + regex + "/ に一致する箇所がある", actual, found,
                "メール本文が期待する書式に一致しない");
    }

    /**
     * メールが 1 通も送信されないことを、受信箱（MailHog Web UI）が空であることを映して検証する。
     *
     * <p>
     * 不在の文脈アンカーには受信一覧のツールバー（{@link MailHogPage#listToolbar}。0 件でも実在する）
     * を用いる。ページ全体（body）は上端が情報バーに覆われ見切れ検査を満たさず、空の一覧領域は
     * 高さ 0 で可視検証できないため。
     *
     * @param mailbox 偽 SMTP の受信箱
     */
    public void noMailSent(E2eMailbox mailbox) {
        boolean received = mailbox.waitForIncomingEmail(2000, 1);
        machine("mail-wait", "メールの不送信確認（MailHog API・2秒待機）", "受信なし", received ? "受信あり" : "受信なし",
                !received, "メールが送信された");
        MailHogPage inbox = new MailHogPage(page).navigate(mailbox.webUiUrl());
        opFrame("受信箱（MailHog Web UI）を開く");
        absent("受信一覧が空（0件）である", inbox.listToolbar(), inbox.messageRows());
    }

    /**
     * メール本文の項目名の並びを、期待リストと機械突合して検証する。
     *
     * <p>
     * メール本文はテンプレートの項目行「【項目名】　{値}」で構成されるため、本文の<b>各行頭の【…】</b>を
     * 出現順に抽出し、期待の項目名の並びとの完全一致を機械検証する（テンプレートの項目名・並びの検証。
     * 値の中に現れる【…】は行頭でない限り抽出しない）。項目が多い場合の代理証跡は先頭サンプル＋全件
     * 機械突合の規格で残す（証跡規範6）。対象は<b>最後に受信したメール</b>（通常は受信箱を空にして
     * 1 通だけ受信した状態で用いる）。
     *
     * @param desc 何を確認するか（仕様書の【機械検証】行と対応させる）
     * @param mailbox 偽 SMTP の受信箱
     * @param expectedItemNames 期待する項目名（出現順の全件。【】は含めない）
     */
    public void mailBodyItemNamesAre(
            String desc,
            E2eMailbox mailbox,
            List<String> expectedItemNames) {
        String body = latestMailBody(mailbox);
        List<String> actual = new ArrayList<>();
        Matcher matcher = Pattern.compile("^【(.+?)】", Pattern.MULTILINE).matcher(body);
        while (matcher.find()) {
            actual.add(matcher.group(1));
        }
        String note = "メール本文の各行頭の【…】を出現順に抽出し、期待の項目名の並びと機械突合した代理証跡";
        List<String> panelItems = actual;
        if (actual.size() > PROXY_PANEL_HEAD) {
            // 全件のフレーム化はしない（先頭サンプル＋全件機械突合の規格）。
            panelItems = actual.subList(0, PROXY_PANEL_HEAD);
            note += "。先頭" + PROXY_PANEL_HEAD + "件のみ表示（以降" + (actual.size() - PROXY_PANEL_HEAD)
                    + "件は省略）・全" + actual.size() + "件を期待リストと機械突合";
        }
        if (recording()) {
            frames.drawProxyPanel(desc, panelItems, note);
        }
        runVerify("mail-body", desc, String.valueOf(expectedItemNames), String.valueOf(actual),
                null, note, null,
                () -> org.junit.jupiter.api.Assertions.assertEquals(expectedItemNames, actual,
                        desc),
                true);
    }

    /**
     * メール本文に指定の文字列が<b>含まれないこと</b>（0件）を機械検証する。
     *
     * <p>
     * 不含有は受信箱 UI の可視検証では受けられない（見えないことの根拠が残らない）ため、デコード済みの
     * 本文テキストに対する機械検証で受ける。対象は最後に受信したメール。
     *
     * @param desc 何を確認するか（仕様書の【機械検証】行と対応させる）
     * @param mailbox 偽 SMTP の受信箱
     * @param text 含まれないはずの文字列
     */
    public void mailBodyNotContains(String desc, E2eMailbox mailbox, String text) {
        String body = latestMailBody(mailbox);
        int count = 0;
        for (int from = body.indexOf(text); from >= 0; from = body.indexOf(text, from + 1)) {
            count++;
        }
        machine("mail-body", desc, "「" + text + "」が 0件", count + "件", count == 0,
                "含まれないはずの文字列がメール本文に含まれる");
    }

    /** 最後に受信したメールのデコード済み本文を返す（未受信は【機械検証】の fail として記録する）。 */
    private String latestMailBody(E2eMailbox mailbox) {
        MimeMessage[] messages = mailbox.getReceivedMessages();
        machine("mail-body", "メール本文の検証対象（最後に受信したメール）", "1通以上受信",
                messages.length + "通", messages.length > 0, "メールが受信されていない");
        return E2eMailbox.bodyOf(messages[messages.length - 1]);
    }

    /**
     * グリッドの1列の値が数値として厳密な降順で並んでいることを機械検証する。
     *
     * <p>
     * 「数値としての並び」は表示文字列の可視検証では確定できないため【機械検証】で受ける（証跡規範7。
     * 仕様書の【機械検証】行と対応させる）。行仮想化グリッドでは DOM に存在する行だけが対象になるため、
     * 全行が描画されている状態（表示件数の範囲に収まるデータ量）で用いる（{@link E2eGridAdapter#rowCount()} の規約）。
     *
     * @param desc 何を確認するか（仕様書の【機械検証】行と対応させる）
     * @param grid グリッド構造のアダプタ
     * @param colId 列の識別子
     */
    public void gridColumnNumericDescending(String desc, E2eGridAdapter grid, String colId) {
        int rows = grid.rowCount();
        List<String> values = new ArrayList<>();
        for (int i = 0; i < rows; i++) {
            String text = grid.cell(i, colId).textContent();
            values.add(text == null ? "" : text.trim());
        }
        boolean ok = rows > 0;
        String reason = ok ? "降順" : "行が0件";
        long previous = Long.MAX_VALUE;
        for (String value : values) {
            long number;
            try {
                number = Long.parseLong(value);
            } catch (NumberFormatException e) {
                ok = false;
                reason = "数値でない値「" + value + "」";
                break;
            }
            if (number >= previous) {
                ok = false;
                reason = "並びの違反（" + previous + " の次に " + number + "）";
                break;
            }
            previous = number;
        }
        machine("grid-column", desc, "数値として降順",
                String.join("・", values) + "（" + reason + "）", ok,
                "列の値が数値として降順に並んでいない");
    }

    /**
     * グリッドの1列の<b>実測幅</b>を検証する（仕様書の【機械検証】行と対応。{@link #positionedAbove}・
     * {@link #textTruncated} と同型——見た目は対象列ヘッダーのハイライト＋フレームで受け、実測幅の
     * 一致を規格内の機械記録として残す）。
     *
     * <p>
     * 幅は列ヘッダーセルの描画幅（boundingBox）を四捨五入したピクセル値で、アダプタのリサイズ操作が
     * 制御に用いる測り方と同じ。リサイズ操作（{@link E2eGridAdapter#resizeColumnTo} 等）や
     * レイアウト復元の結果検証に用いる。対象列のヘッダーが描画されている状態で呼ぶ
     * （列仮想化で DOM に無い列は {@code gridColumnHeaders}・{@code gridCellText} 等で画角を
     * 作ってから）。
     *
     * @param desc 何を確認するか（仕様書の【機械検証】行と対応させる）
     * @param grid グリッド構造のアダプタ
     * @param colId 列の識別子
     * @param expectedPx 期待する列幅（ピクセル）
     */
    public void gridColumnWidthIs(String desc, E2eGridAdapter grid, String colId, int expectedPx) {
        Locator header = grid.headerCell(colId);
        long[] measured = new long[1];
        List<Map<String, Object>> rects = null;
        String note = "対象列のヘッダーをハイライトし、実測幅（ヘッダーセルの描画幅）の一致を規格内の機械記録として残す";
        if (recording()) {
            if (awaitPresent(header)) {
                frames.reveal(header);
                frames.ensureInViewport(header, desc + "（対象列ヘッダー）");
                rects = frames.highlight(List.of(header));
            } else {
                note = missingNote("対象列（" + colId + "）のヘッダー");
            }
        }
        runVerify("grid-column-width", desc, expectedPx + "px", null, rects, note, null, () -> {
            BoundingBox box = header.first().boundingBox();
            if (box == null) {
                throw new AssertionError("列ヘッダーの位置（boundingBox）が取得できない: " + colId);
            }
            measured[0] = Math.round(box.width);
            if (measured[0] != expectedPx) {
                throw new AssertionError("列幅が期待値でない（実測 " + measured[0] + "px）");
            }
        }, true);
        machine("grid-column-width", desc + "（実測幅）", expectedPx + "px", measured[0] + "px",
                measured[0] == expectedPx, "列幅が期待値でない");
    }

    // ---- HTTP 応答・要求（画面に出ない検証） ----

    /**
     * {@link #captureResponse} が捕捉した HTTP 応答のスナップショット。
     *
     * <p>
     * 応答の生読取をテストコードへ返さないための不透明ハンドルであり、{@code responseStatusIs} 等の
     * 検証部品だけが消費する（テスト側でフィールドを読み取って {@code machineEquals} へ転記しない）。
     * 応答ボディは捕捉時に取得して保持する（画面遷移後は Playwright の {@code Response} から
     * ボディを取得できなくなるため）。
     *
     * @param method 要求の HTTP メソッド
     * @param url 応答の URL（実測）
     * @param status HTTP ステータスコード（実測）
     * @param body 応答ボディ（ボディなし・取得不能の場合は空）
     * @param redirected この応答へ至るリダイレクトがあったか
     */
    public record CapturedResponse(
            String method, String url, int status, byte[] body, boolean redirected) {
    }

    /**
     * 操作に伴う HTTP 応答を捕捉する。
     *
     * <p>
     * 仕様書の【機械検証】「〜の HTTP 応答が NNN である」等を受けるための捕捉部品。操作（{@code opPress}・
     * {@code opOpen} 等の部品呼び出しをそのまま {@code action} に渡す）の実行中に、メソッドと URL
     * （部分一致）が合致する<b>最初の応答</b>を捕捉し、ステータス・ボディ・リダイレクト有無の
     * スナップショットを返す。検証は {@link #responseStatusIs} 等の部品で行う。
     * 捕捉の成立（何を捕捉したか）は規格内の機械記録として steps.json に残す。
     *
     * <p>
     * リダイレクト応答（302 等）自体を検証したい場合、その応答も URL が合致すれば捕捉される
     * （リダイレクトチェーンの各応答は別々の応答として流れる）。
     *
     * @param desc 何のための捕捉か
     * @param method 捕捉する要求の HTTP メソッド（大文字小文字は区別しない）
     * @param urlPattern 捕捉する URL の部分文字列（例: {@code "/api/projects/search"}）
     * @param action 応答を発生させる操作（操作部品の呼び出しを渡す）
     * @return 捕捉した応答のスナップショット
     */
    public CapturedResponse captureResponse(
            String desc,
            String method,
            String urlPattern,
            Runnable action) {
        Response response;
        try {
            response = page.waitForResponse(
                    r -> r.request().method().equalsIgnoreCase(method)
                            && r.url().contains(urlPattern),
                    new Page.WaitForResponseOptions().setTimeout(RESPONSE_WAIT_MS), action::run);
        } catch (PlaywrightException e) {
            machine("response", desc + "（応答の捕捉）",
                    method + " " + urlPattern + " の応答を捕捉",
                    "捕捉できない（" + (int) (RESPONSE_WAIT_MS / 1000) + "秒待機）: " + excerpt(e), false,
                    "対象の HTTP 応答を捕捉できない");
            throw new IllegalStateException("到達しない");
        }
        byte[] body;
        try {
            body = response.body();
        } catch (PlaywrightException e) {
            body = new byte[0]; // ボディなし応答・取得不能（遷移で失効等）は空として扱う
        }
        boolean redirected = response.request().redirectedFrom() != null;
        machine("response", desc + "（応答の捕捉）", method + " " + urlPattern + " の応答を捕捉",
                method + " " + response.url() + " → HTTP " + response.status(), true, "");
        return new CapturedResponse(method, response.url(), response.status(), body, redirected);
    }

    /**
     * 捕捉した HTTP 応答のステータスコードを機械検証する。
     *
     * @param desc 何を確認するか（仕様書の【機械検証】行と対応させる）
     * @param response {@link #captureResponse} の捕捉結果
     * @param expected 期待するステータスコード
     */
    public void responseStatusIs(String desc, CapturedResponse response, int expected) {
        machine("response", desc, "HTTP " + expected, "HTTP " + response.status(),
                response.status() == expected, "HTTP 応答のステータスが期待と異なる");
    }

    /**
     * 捕捉した HTTP 応答のボディが空（ボディなし）であることを機械検証する。
     *
     * @param desc 何を確認するか（仕様書の【機械検証】行と対応させる）
     * @param response {@link #captureResponse} の捕捉結果
     */
    public void responseBodyEmpty(String desc, CapturedResponse response) {
        machine("response", desc, "ボディなし（0バイト）",
                response.body().length + "バイト", response.body().length == 0,
                "空のはずのレスポンスボディに内容がある");
    }

    /**
     * 捕捉した HTTP 応答（400）の {@code errors} 配列を機械検証する。
     *
     * <p>
     * ボディを JSON として解析し、{@code errors[]} の {@code field}→{@code message} の集合が期待と
     * 一致することを検証する（<b>順序は比較しない</b>。項目間の順序を規定しない API 仕様に合わせる）。
     * 項目に紐づかない違反（{@code field=null}）はキー {@code null} で表現する。
     *
     * @param desc 何を確認するか（仕様書の【機械検証】行と対応させる）
     * @param response {@link #captureResponse} の捕捉結果
     * @param expectedFieldMessages 期待する違反の {@code field}→{@code message}
     */
    public void responseErrorsAre(
            String desc,
            CapturedResponse response,
            Map<String, String> expectedFieldMessages) {
        Map<String, String> actual = new LinkedHashMap<>();
        String parseError = null;
        try {
            JsonNode errors = JSON.readTree(
                    new String(response.body(), StandardCharsets.UTF_8)).path("errors");
            for (JsonNode error : errors) {
                actual.put(error.path("field").isNull() ? null : error.path("field").asString(),
                        error.path("message").asString());
            }
        } catch (RuntimeException e) {
            parseError = excerpt(e);
        }
        boolean ok = parseError == null && actual.equals(expectedFieldMessages);
        machine("response", desc, String.valueOf(expectedFieldMessages),
                parseError != null ? "JSON として解析できない: " + parseError : String.valueOf(actual),
                ok, "errors 配列が期待と一致しない");
    }

    /**
     * 捕捉した HTTP 応答のボディに、指定の文字列がいずれも<b>含まれないこと</b>を機械検証する
     * （「内部の詳細情報（スタックトレース・例外クラス名等）を含まない」型の期待に対応）。
     *
     * @param desc 何を確認するか（仕様書の【機械検証】行と対応させる）
     * @param response {@link #captureResponse} の捕捉結果
     * @param tokens 含まれないはずの文字列（1つ以上）
     */
    public void responseBodyExcludes(String desc, CapturedResponse response, String... tokens) {
        if (tokens.length == 0) {
            throw new IllegalArgumentException("含まれないはずの文字列を1つ以上指定する: " + desc);
        }
        String body = new String(response.body(), StandardCharsets.UTF_8);
        List<String> found = new ArrayList<>();
        for (String token : tokens) {
            if (body.contains(token)) {
                found.add(token);
            }
        }
        machine("response", desc, "「" + String.join("」「", tokens) + "」をいずれも含まない",
                found.isEmpty() ? "いずれも含まない" : "「" + String.join("」「", found) + "」を含む",
                found.isEmpty(), "含まれないはずの文字列がレスポンスボディに含まれる");
    }

    /**
     * 捕捉した HTTP 応答が<b>リダイレクトを経ていない</b>ことを機械検証する
     * （「ログイン画面へのリダイレクトが発生していない」型の期待に対応）。
     *
     * @param desc 何を確認するか（仕様書の【機械検証】行と対応させる）
     * @param response {@link #captureResponse} の捕捉結果
     */
    public void responseNotRedirected(String desc, CapturedResponse response) {
        machine("response", desc, "リダイレクトなし（要求 URL がそのまま応答）",
                response.redirected() ? "リダイレクトあり（最終 URL: " + response.url() + "）"
                        : "リダイレクトなし（" + response.url() + "）",
                !response.redirected(), "発生しないはずのリダイレクトが発生した");
    }

    /**
     * 操作の後に指定の HTTP 要求が<b>送信されないこと</b>を検証する（否定観測）。
     *
     * <p>
     * 「登録の要求が送信されていない」等の不呼出の期待（証跡規範7）を受ける。操作の実行から一定時間
     * （{@value #NEGATIVE_OBSERVATION_WAIT_MS} ms）要求を観測し、メソッドと URL（部分一致）が合致する
     * 要求が 0 件であることを【機械検証】として記録する（{@link #noMailSent} と同型の規格）。
     *
     * @param desc 何を確認するか（仕様書の【機械検証】行と対応させる）
     * @param method 観測する要求の HTTP メソッド（大文字小文字は区別しない）
     * @param urlPattern 観測する URL の部分文字列
     * @param action 要求を発生させないはずの操作（操作部品の呼び出しを渡す）
     */
    public void noRequestSent(String desc, String method, String urlPattern, Runnable action) {
        List<String> matched = new ArrayList<>();
        Consumer<Request> listener = request -> {
            if (request.method().equalsIgnoreCase(method) && request.url().contains(urlPattern)) {
                matched.add(request.method() + " " + request.url());
            }
        };
        page.onRequest(listener);
        try {
            action.run();
            page.waitForTimeout(NEGATIVE_OBSERVATION_WAIT_MS);
        } finally {
            page.offRequest(listener);
        }
        machine("no-request", desc,
                "要求なし（" + method + " " + urlPattern + "・"
                        + (int) NEGATIVE_OBSERVATION_WAIT_MS + "ms 観測）",
                matched.isEmpty() ? "要求なし" : "要求あり: " + String.join("・", matched),
                matched.isEmpty(), "送信されないはずの要求が送信された");
    }

    // ---- 機械検証（画面に出ない事項） ----

    /**
     * 画面に現れない事項の機械検証を記録する（フレーム無し・実測値をログとして残す）。
     *
     * @param desc 何を確認するか（仕様書の【機械検証】行と対応させる）
     * @param expected 期待値の表現
     * @param actual 実測値の表現
     */
    public void machineEquals(String desc, String expected, String actual) {
        machine("machine", desc, expected, actual, Objects.equals(expected, actual), desc);
    }

    /**
     * セッションタイムアウトの有効設定値を、起動中アプリのサーブレットコンテキストから読み取って検証する。
     *
     * <p>
     * タイムアウト長（「n分経過で失効する」の n）は実時間待ちでは決定的に検証できないため、実行中アプリに
     * 適用されている設定値の機械検証で受ける（到達手段カタログ 手段6）。失効時の挙動そのものは
     * 別セッションのログアウト等（手段3）で検証する。E2E プロファイル（{@code application-e2e.yml}）が
     * この設定を上書きしないことが前提（実行ハーネス規範。上書きすると本検証は E2E 用の値を見るだけになる）。
     *
     * @param servletContext 起動中アプリのサーブレットコンテキスト
     * @param expected 期待するタイムアウト（分単位で比較する）
     */
    public void sessionTimeoutIs(ServletContext servletContext, Duration expected) {
        int actualMinutes = servletContext.getSessionTimeout();
        machine("session-timeout", "セッションタイムアウトの有効設定値",
                expected.toMinutes() + "分", actualMinutes + "分",
                actualMinutes == expected.toMinutes(), "セッションタイムアウトが期待値でない");
    }

    private void machine(
            String verifyName,
            String desc,
            String expected,
            String actual,
            boolean pass,
            String failMessage) {
        logEvent(pass,
                "【機械検証】" + desc + " — 期待: " + expected + " ／ 実測: " + actual + (pass ? " ✓" : " ✗"));
        if (recording()) {
            E2eStepRecorder.Step step = new E2eStepRecorder.Step();
            step.seq = recorder.nextSeq();
            step.kind = "machine";
            step.verify = verifyName;
            step.desc = desc;
            step.ts = E2eStepRecorder.nowTs();
            step.expected = expected;
            step.actual = actual;
            step.status = pass ? "pass" : "fail";
            recorder.add(step);
        }
        if (!pass) {
            throw new AssertionError(failMessage + "（期待: " + expected + " / 実測: " + actual + "）");
        }
    }

    // ---- 共通後段 ----

    /** 証跡ログ（ハーネスイベント）へ 1 行記録する（見た目で確認しにくい事実の補強）。 */
    private void logEvent(boolean pass, String message) {
        if (recording()) {
            recorder.addLog(E2eStepRecorder.nowTs(), "harness", pass ? "INFO" : "ERROR", "",
                    message);
        }
    }

    /** 動画用オーバーレイに現在のステップ名を表示する（補助媒体の可読性向上のみが目的）。 */
    private void setOverlayStep(String text) {
        if (!recording()) {
            return;
        }
        try {
            page.evaluate("t => { if (window.__e2eSetStep) { window.__e2eSetStep(t); } }", text);
        } catch (RuntimeException ignored) {
            // ページ遷移直後など評価できないときは動画用の表示だけを諦める（証跡本体には影響しない）
        }
    }

    /**
     * 対象要素の出現（DOM への出現）を短時間だけ待つ。要素が存在しない失敗を、reveal 等の長い既定
     * タイムアウト（無記録の {@link PlaywrightException}）で終わらせず、記録付きの失敗へ切り替える
     * 判断に使う。
     *
     * @param target 対象要素
     * @return 出現すれば真
     */
    private static boolean awaitPresent(Locator target) {
        try {
            target.first().waitFor(new Locator.WaitForOptions()
                    .setState(WaitForSelectorState.ATTACHED).setTimeout(PRESENCE_WAIT_MS));
            return true;
        } catch (PlaywrightException e) {
            return false;
        }
    }

    /** 要素不在のときの note 文言（何が・何秒待って居らず・どう記録したか）。 */
    private static String missingNote(String subject) {
        return subject + "が画面に存在しない（" + (int) (PRESENCE_WAIT_MS / 1000)
                + "秒待機）。現画面のフレームを記録";
    }

    /** 連続フレーム部品の前提要素を検証し、失敗時も現画面のフレーム付きの fail として記録する。 */
    private void requireForContinuousFrames(
            String verifyName,
            String desc,
            String subject,
            Locator target) {
        try {
            assertThat(target).isVisible();
        } catch (AssertionError | PlaywrightException error) {
            runVerify(verifyName, desc, subject + "が表示される", null, null, missingNote(subject),
                    null, () -> {
                        throw new AssertionError(subject + "が表示されない: " + excerpt(error));
                    }, true);
        }
    }

    /** ロケータ対象の可視検証（reveal＋見切れ検査＋ハイライト＋フレーム＋記録）。 */
    private void verifyLocator(
            String verifyName,
            String desc,
            Locator target,
            String expected,
            String actual,
            Runnable assertion) {
        List<Map<String, Object>> rects = null;
        Locator elementShot = null;
        String note = null;
        if (recording()) {
            if (awaitPresent(target)) {
                frames.reveal(target);
                if (!frames.ensureInViewport(target, desc)) {
                    elementShot = target; // ビューポートより大きい要素は要素単体ショットを添付する
                }
                rects = frames.highlight(List.of(target));
            } else {
                // 要素不在（設計と実装の乖離の典型）。現画面のフレームを撮り、続くアサーションが
                // 期待／実測の対を fail として記録する。
                note = missingNote("検証対象の要素");
            }
        }
        runVerify(verifyName, desc, expected, actual, rects, note, elementShot, assertion, true);
    }

    /**
     * 検証の共通後段: フレーム撮影 → アサーション実行 → 記録。失敗時は fail で記録してから送出する。
     */
    private void runVerify(
            String verifyName,
            String desc,
            String expected,
            String actual,
            List<Map<String, Object>> rects,
            String note,
            Locator elementShot,
            Runnable assertion,
            boolean captureFrame) {
        setOverlayStep("【検証】" + desc);
        E2eStepRecorder.Step step = null;
        if (recording()) {
            int seq = recorder.nextSeq();
            step = new E2eStepRecorder.Step();
            step.seq = seq;
            step.kind = "verify";
            step.verify = verifyName;
            step.desc = desc;
            step.ts = E2eStepRecorder.nowTs();
            step.expected = expected;
            step.actual = actual;
            step.note = note;
            step.url = frames.currentPath();
            step.artifact = pendingArtifact;
            pendingArtifact = null;
            step.highlight = rects;
            if (captureFrame) {
                step.frame = recorder.frameName(seq);
                frames.capture(step.frame);
            }
            if (elementShot != null) {
                step.attachFrame = recorder.frameName(seq).replace(".jpg", "a.jpg");
                frames.captureElement(elementShot, step.attachFrame);
            }
            frames.clearOverlays();
        }
        try {
            assertion.run();
        } catch (AssertionError | PlaywrightException error) {
            if (step != null) {
                step.status = "fail";
                // 失敗時は実測を例外要旨で上書きする（成功時用の事前ラベルを実測として残さない）。
                step.actual = excerpt(error);
                recorder.add(step);
                logEvent(false,
                        "【検証】" + desc + " — 期待: " + expected + " ／ 実測: " + step.actual + " ✗");
            }
            throw error;
        }
        if (step != null) {
            step.status = "pass";
            if (step.actual == null) {
                step.actual = step.expected; // web-first アサーション成立＝期待どおり
            }
            recorder.add(step);
            logEvent(true, "【検証】" + desc + " — 期待: " + expected + " ／ 実測: " + step.actual + " ✓");
        }
    }

    private static String excerpt(Throwable error) {
        String text = error.getMessage() == null ? error.getClass().getSimpleName()
                : error.getMessage().strip();
        int limit = 200;
        return text.length() > limit ? text.substring(0, limit) + "…" : text;
    }
}
