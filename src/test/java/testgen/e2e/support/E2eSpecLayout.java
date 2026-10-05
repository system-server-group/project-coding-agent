package testgen.e2e.support;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * テストケース仕様（正本）とエビデンスの配置解決。
 *
 * <p>
 * 配置は {@code docs/tests/テストケース仕様/<テスト実施単位>/<機能>/} であり、テスト実施単位（リリースターゲット・再テスト）が 機能の 1
 * 階層上に入る。実施単位ごとに仕様書・データ・ベースライン・エビデンスが一式で閉じるため、初回テストの証跡を残したまま 再テストを別の実施単位として実施できる。
 *
 * <p>
 * 実施単位は システムプロパティ {@code e2e.target} で必ず指定する（{@code -Pe2e.target=<実施単位>} から Gradle が渡す。 IDE
 * から直接実行するときは {@code -De2e.target=<実施単位>}）。実施単位の取り違えを防ぐため既定値は設けず、 未指定なら停止する。仕様ルートは
 * {@code e2e.spec-root} で上書きできる。
 *
 * <p>
 * 実施単位の中で対象機能を絞るときは {@code e2e.features}（機能フォルダ名のカンマ区切り／{@code -Pe2e.features} から Gradle
 * が渡す）を指定する。未指定のときは実施単位の全機能を対象にする。選択は実施単位の内側だけで働くため、 1 回の実行が実施単位を跨ぐことはない。
 */
public final class E2eSpecLayout {

    private static final String DEFAULT_ROOT = "docs/tests/テストケース仕様";
    private static final String EVIDENCE = "エビデンス";
    private static final String DATA = "data";
    private static final String BASELINE = "_baseline";

    private static final Path ROOT = Path.of(System.getProperty("e2e.spec-root", DEFAULT_ROOT));
    private static final Path TARGET = resolveTarget();
    private static final Set<String> SELECTED_FEATURES = resolveSelectedFeatures();

    private E2eSpecLayout() {}

    /**
     * テスト実施単位のディレクトリを返す。
     *
     * @return {@code <仕様ルート>/<実施単位>}
     */
    public static Path targetDir() {
        return TARGET;
    }

    /**
     * 機能フォルダを返す。
     *
     * @param feature 機能名（機能フォルダ名）
     * @return {@code <実施単位>/<機能>}
     */
    public static Path featureDir(String feature) {
        return TARGET.resolve(feature);
    }

    /**
     * 機能固有のテストデータ正本ディレクトリを返す。
     *
     * @param feature 機能名（機能フォルダ名）
     * @return {@code <実施単位>/<機能>/data}
     */
    public static Path dataDir(String feature) {
        return featureDir(feature).resolve(DATA);
    }

    /**
     * 証跡（エビデンス）の出力先ディレクトリを返す。
     *
     * @param feature 機能名（機能フォルダ名）
     * @return {@code <実施単位>/<機能>/エビデンス}
     */
    public static Path evidenceDir(String feature) {
        return featureDir(feature).resolve(EVIDENCE);
    }

    /**
     * ベースライン正本（マスタ／参照系 csv）のディレクトリを返す。
     *
     * @return {@code <実施単位>/_baseline/data}
     */
    public static Path baselineDataDir() {
        return TARGET.resolve(BASELINE).resolve(DATA);
    }

    /**
     * その機能が今回の実行対象かどうかを返す。
     *
     * <p>
     * {@code e2e.features} が未指定なら実施単位の全機能が対象（常に真）。指定があるときは列挙された機能だけを対象とし、 選択外のテストクラスは実行前に自身をスキップする。
     *
     * @param feature 機能名（機能フォルダ名）
     * @return 今回の実行対象なら真
     */
    public static boolean isSelected(String feature) {
        return SELECTED_FEATURES.isEmpty() || SELECTED_FEATURES.contains(feature);
    }

    /**
     * 機能フォルダが実施単位配下に存在することを確かめる。
     *
     * <p>
     * 存在しない機能名で証跡を出力すると、規約外の場所に証跡フォルダを作ってしまい受け入れの正本が壊れるため、 出力前に立ち止まる。
     *
     * @param feature 機能名（機能フォルダ名）
     * @throws IllegalStateException 機能フォルダが無いとき
     */
    public static void requireFeature(String feature) {
        if (!Files.isDirectory(featureDir(feature))) {
            throw new IllegalStateException("機能フォルダがテスト実施単位に存在しません: " + featureDir(feature)
                    + "（実施単位は -Pe2e.target で指定します）");
        }
    }

    private static Path resolveTarget() {
        String specified = System.getProperty("e2e.target", "").trim();
        if (specified.isEmpty()) {
            throw new IllegalStateException("テスト実施単位が指定されていません"
                    + "（Gradle からは -Pe2e.target=<実施単位>、IDE から直接実行するときは -De2e.target=<実施単位>）。");
        }
        Path dir = ROOT.resolve(specified);
        if (!Files.isDirectory(dir)) {
            throw new IllegalStateException("テスト実施単位が見つかりません: " + dir);
        }
        return dir;
    }

    /**
     * 対象機能の選択（{@code e2e.features}）を解決する。
     *
     * <p>
     * 実施単位に無い機能名（誤字・別実施単位の機能）を黙って無視すると、対象 0 件のまま「成功」して終わるため、 解決できない名前があれば実行前に停止する。
     */
    private static Set<String> resolveSelectedFeatures() {
        String specified = System.getProperty("e2e.features", "").trim();
        if (specified.isEmpty()) {
            return Set.of();
        }
        Set<String> features = new LinkedHashSet<>();
        List<String> missing = new ArrayList<>();
        for (String name : specified.split(",")) {
            String feature = name.trim();
            if (feature.isEmpty()) {
                continue;
            }
            features.add(feature);
            if (!Files.isDirectory(TARGET.resolve(feature))) {
                missing.add(feature);
            }
        }
        if (features.isEmpty()) {
            throw new IllegalStateException("e2e.features に機能名が指定されていません: " + specified);
        }
        if (!missing.isEmpty()) {
            throw new IllegalStateException("指定された機能フォルダがテスト実施単位にありません: "
                    + String.join("／", missing) + "（実施単位 " + TARGET + "）");
        }
        return features;
    }
}
