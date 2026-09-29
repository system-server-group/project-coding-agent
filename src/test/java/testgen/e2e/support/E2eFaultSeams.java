package testgen.e2e.support;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 障害シームの状態管理（ケース単位トグル・発火後自動解除・発火記録）。
 *
 * <p>
 * サーバー内部の異常分岐（データベース例外→ロールバック・外部送信の失敗・更新件数0等の競合検出）は
 * 画面操作だけでは決定的に再現できないため、<b>テストプロファイル限定の委譲シーム Bean</b>（機能対応物。
 * アプリの Bean を委譲でラップし、{@link #shouldFire} が真のときだけ該当箇所で障害を発生させる。
 * exec が転写時に作る）で再現する（到達手段カタログ 手段4）。本クラスはその共有状態だけを担い、
 * アプリの型には依存しない（キットは中立を保つ）。
 *
 * <p>
 * テストとアプリは {@code @SpringBootTest} の同一プロセスで動くため、状態は static で受け渡す。
 * 契約は次のとおり:
 *
 * <ul>
 * <li>テスト側は {@code E2eVerify#opArmFault} 経由で {@link #arm} し（有効化の機械記録付き）、
 * 対象操作の後に {@code E2eVerify#faultFired} で発火を機械検証する。</li>
 * <li>アプリ側シーム Bean は障害を注入する分岐で {@link #shouldFire} を呼ぶ。有効時は発火を記録して
 * <b>自動解除</b>し、同じシームが後続の処理へ影響を残さない。</li>
 * <li>{@link #reset} はベースラインリセット時（{@code E2eBaseTest} が毎ケース前）に呼ばれ、
 * 前ケースの取りこぼしを含めて全解除する。</li>
 * </ul>
 *
 * <p>
 * シーム名は日本語の短い名前（例: 「データベース例外（登録）」「メール送信失敗」）とし、テスト仕様書の
 * 操作手順に書かれた名前とテスト実装・シーム Bean で一致させる（証跡と仕様書を突合できるようにする）。
 */
public final class E2eFaultSeams {

    private static final Set<String> ARMED = ConcurrentHashMap.newKeySet();
    private static final Set<String> FIRED = ConcurrentHashMap.newKeySet();

    private E2eFaultSeams() {
    }

    /**
     * シームを有効化する（ケース単位トグル）。テストコードからは {@code E2eVerify#opArmFault} 経由で
     * 呼び、有効化を機械記録に残す。
     *
     * @param name シーム名（仕様書の操作手順と一致させる）
     */
    public static void arm(String name) {
        ARMED.add(name);
    }

    /**
     * アプリ側シーム Bean が障害注入の分岐で呼ぶ。有効なら発火を記録して自動解除し、true を返す。
     *
     * @param name シーム名
     * @return このシームが有効で、いま障害を発生させるべきなら true
     */
    public static boolean shouldFire(String name) {
        if (ARMED.remove(name)) {
            FIRED.add(name);
            return true;
        }
        return false;
    }

    /**
     * 発火を消費しない状態照会（有効化の機械記録用）。
     *
     * @param name シーム名
     * @return 有効化されたまま未発火なら true
     */
    public static boolean isArmed(String name) {
        return ARMED.contains(name);
    }

    /**
     * このケース中に発火済みかを返す（{@code E2eVerify#faultFired} の機械検証用）。
     *
     * @param name シーム名
     * @return 発火済みなら true
     */
    public static boolean wasFired(String name) {
        return FIRED.contains(name);
    }

    /** ベースラインリセット時の全解除（{@code E2eBaseTest} が毎ケース前に呼ぶ）。 */
    public static void reset() {
        ARMED.clear();
        FIRED.clear();
    }
}
