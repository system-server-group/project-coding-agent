package testgen.e2e.support;

import java.util.regex.Pattern;

/**
 * E2E 実行証跡（エビデンス）の生成補助。
 *
 * <p>
 * 証跡モード（システムプロパティ {@code e2e.evidence=true}／{@code e2eEvidence} タスクが設定）でのみ有効化する。 通常の
 * {@code e2eTest} は証跡を取らず高速に実行する。証跡はケースごとにスクリーンショット（{@code .png}）・
 * 動画（{@code .webm}）・トレース（{@code .trace.zip}）を出力する。ファイル名は仕様書のケース No を含む表示名から 生成する。受け入れ用
 * {@code index.html}（仕様書本文＋証跡を 1ケース＝1画面で表示）は、{@code e2eEvidence} の 後段タスク
 * {@code e2eEvidenceIndex}（testgen-exec スキル同梱の {@code e2e_evidence_index.py}）が xlsx 正本から生成する。
 *
 * <p>
 * index 生成の対象は、実行時に指定した実施単位（{@code e2e.target}）と機能（{@code e2e.features}）から決まる。 テストと index
 * 生成が同じ指定を見るため、実施単位を跨いで別の {@code index.html} を作り直すことはない。
 */
public final class E2eEvidence {

    private static final Pattern ILLEGAL = Pattern.compile("[\\\\/:*?\"<>|]");

    private E2eEvidence() {}

    /**
     * 証跡モードか否かを返す。
     *
     * @return {@code e2e.evidence=true} のとき真
     */
    public static boolean enabled() {
        return "true".equals(System.getProperty("e2e.evidence"));
    }

    /**
     * 表示名からファイル名スラグ（拡張子なし）を生成する。ケース No を先頭に保った可読名とする。
     *
     * @param displayName テストの表示名（＝仕様書のケース No を含む）
     * @return ファイル名に使えるスラグ
     */
    public static String slug(String displayName) {
        String source = displayName == null ? "case" : displayName.trim();
        String replaced = ILLEGAL.matcher(source).replaceAll("_").replace(' ', '_');
        return replaced.isBlank() ? "case" : replaced;
    }
}
