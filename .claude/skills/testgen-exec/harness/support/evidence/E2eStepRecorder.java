package testgen.e2e.support.evidence;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.core.util.DefaultIndenter;
import com.fasterxml.jackson.core.util.DefaultPrettyPrinter;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.ObjectWriter;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * ケース単位の操作・検証ステップを {@code steps.json} へ記録する（証跡V2）。
 *
 * <p>
 * 1 テストメソッド（＝1 ケース）につき 1 インスタンスを生成し、検証部品（{@link E2eVerify}）が操作・検証の たびにステップを積む。テスト終了時（基底クラスの
 * {@code @AfterEach}）に、機能の証跡フォルダ直下の {@code steps.json} へケース単位でマージ書込する（同一ケースの再実行は置き換え）。フレーム画像との対応・
 * 機械検証の実測値を持ち、受け入れ用ビューア（e2e_evidence_index.py の V2 レイアウト）と 受け入れ確認（フレーム監査）の共通の正本になる。
 */
public final class E2eStepRecorder {

    /** steps.json の 1 ステップ。null のフィールドは JSON に出さない。 */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static final class Step {
        public int seq;
        public String kind; // op / verify / machine / whole
        public String verify; // 検証の種別（visible/hidden/text/url/... kind=verify|machine のとき）
        public String desc; // 何をした・何を確認したか（ビューアのキャプション）
        public String expected;
        public String actual;
        public String status; // pass / fail（kind=verify|machine のとき）
        public String frame; // フレーム画像ファイル名（撮影したステップのみ）
        public String attachFrame; // 要素単体ショット（見切れフォールバック）のファイル名
        public String artifact; // 操作の結果出力されたファイル（DL実物）の証跡ファイル名
        public String url; // 撮影時のパス（location.pathname + search）
        public String ts; // 記録時刻（HH:mm:ss.SSS。アプリログとの突き合わせ用）
        public String note; // 代理証跡ラベル・補足
        public List<Map<String, Object>> highlight; // ハイライト矩形（ビューア再描画用）
    }

    private static final DateTimeFormatter TS_FORMAT = DateTimeFormatter.ofPattern("HH:mm:ss.SSS");

    /** @return 現在時刻の記録用表現（{@code HH:mm:ss.SSS}） */
    public static String nowTs() {
        return LocalTime.now().format(TS_FORMAT);
    }

    /** 1 ケースに記録するログの上限（超過分は打ち切り、上限到達を示す行を残す）。 */
    private static final int MAX_LOGS = 300;

    private static final Pattern CASE_NO = Pattern.compile("No\\.(\\d+(?:-\\d+)?)\\s*(.*)");
    private static final ObjectMapper MAPPER = new ObjectMapper();
    // 改行は LF 固定（実行 OS に依存させない。書式は既定の整形のまま、行区切りだけを固定する）。
    private static final ObjectWriter WRITER = MAPPER
            .writer(new DefaultPrettyPrinter().withObjectIndenter(new DefaultIndenter("  ", "\n")));

    private final Path evidenceDir;
    private final String feature;
    private final String target;
    private final String caseNo;
    private final String caseName;
    private final List<Step> steps = new ArrayList<>();
    private final List<Map<String, Object>> logs = new ArrayList<>();
    private int seq = 0;

    /**
     * ケースの記録を開始する。
     *
     * @param evidenceDir 証跡フォルダ
     * @param feature 機能名
     * @param target テスト実施単位名
     * @param displayName テスト表示名（仕様書のケース No を含む）
     */
    public E2eStepRecorder(Path evidenceDir, String feature, String target, String displayName) {
        this.evidenceDir = evidenceDir;
        this.feature = feature;
        this.target = target;
        Matcher matcher = CASE_NO.matcher(displayName == null ? "" : displayName);
        if (matcher.find()) {
            this.caseNo = matcher.group(1);
            this.caseName = matcher.group(2).trim();
        } else {
            this.caseNo = "";
            this.caseName = displayName == null ? "" : displayName;
        }
    }

    /** @return 次のステップ連番（1 始まり） */
    public int nextSeq() {
        return ++seq;
    }

    /**
     * 指定連番のフレーム画像ファイル名を返す（{@code No.<ケースNo>_<連番2桁>.jpg}）。
     *
     * @param stepSeq ステップ連番
     * @return フレーム画像ファイル名
     */
    public String frameName(int stepSeq) {
        return String.format("No.%s_%02d.jpg", caseNo.isEmpty() ? "x" : caseNo, stepSeq);
    }

    /**
     * ステップを積む。
     *
     * @param step 記録するステップ
     */
    public void add(Step step) {
        steps.add(step);
    }

    /** @return 1 件以上のステップが記録されていれば真（＝V2 方式のケース） */
    public boolean hasSteps() {
        return !steps.isEmpty();
    }

    /** 本ケースの既存フレーム・DL実物（前回実行分）を消し、再実行で古い証跡が残らないようにする。 */
    public void deleteExistingFrames() {
        if (caseNo.isEmpty() || !Files.isDirectory(evidenceDir)) {
            return;
        }
        String prefix = "No." + caseNo + "_";
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(evidenceDir, path -> {
            String name = path.getFileName().toString();
            if (!name.startsWith(prefix)) {
                return false;
            }
            String rest = name.substring(prefix.length());
            // フレーム（jpg）と DL 実物（<連番2桁>d_<元ファイル名>）のみ対象。webm/trace は消さない。
            return name.endsWith(".jpg") || rest.matches("\\d{2}d_.+");
        })) {
            for (Path path : stream) {
                Files.delete(path);
            }
        } catch (IOException e) {
            throw new UncheckedIOException("既存フレームの削除に失敗しました: " + evidenceDir, e);
        }
    }

    /**
     * 操作の結果出力されたファイル（ダウンロード実物）を証跡として保存する。
     *
     * <p>
     * 機械検証（バイト一致等）に使った実物そのものを受け入れ者が開けるようにする。ファイル名は
     * {@code No.<ケースNo>_<連番2桁>d_<元ファイル名>}（ケース開始時の洗い替え対象）。
     *
     * @param stepSeq 対応するステップ連番
     * @param originalName 出力されたファイルの元ファイル名
     * @param content ファイル内容
     * @return 保存した証跡ファイル名（steps.json の {@code artifact} に記録する）
     */
    public String saveArtifact(int stepSeq, String originalName, byte[] content) {
        String safeName = originalName.replaceAll("[\\\\/:*?\"<>|]", "_");
        String fileName =
                String.format("No.%s_%02dd_%s", caseNo.isEmpty() ? "x" : caseNo, stepSeq, safeName);
        try {
            Files.createDirectories(evidenceDir);
            Files.write(evidenceDir.resolve(fileName), content);
        } catch (IOException e) {
            throw new UncheckedIOException("DL実物の保存に失敗しました: " + fileName, e);
        }
        return fileName;
    }

    /** @return ケース No（表示名から抽出。無ければ空文字） */
    public String caseNo() {
        return caseNo;
    }

    /**
     * ケース実行中に観測したログ（アプリケーション・ブラウザコンソール）を記録する。
     *
     * @param ts 記録時刻（{@code HH:mm:ss.SSS}）
     * @param src 出所（{@code app}／{@code browser}）
     * @param level ログレベル
     * @param logger ロガー名（ブラウザは空でよい）
     * @param message メッセージ（開発用ダミーのみ。秘匿情報は元より出力しない方針）
     */
    public void addLog(String ts, String src, String level, String logger, String message) {
        if (logs.size() > MAX_LOGS) {
            return;
        }
        if (logs.size() == MAX_LOGS) {
            Map<String, Object> marker = new LinkedHashMap<>();
            marker.put("ts", ts);
            marker.put("src", "system");
            marker.put("level", "WARN");
            marker.put("logger", "");
            marker.put("message", "（以降のログは件数上限 " + MAX_LOGS + " により省略）");
            logs.add(marker);
            return;
        }
        Map<String, Object> entry = new LinkedHashMap<>();
        entry.put("ts", ts);
        entry.put("src", src);
        entry.put("level", level);
        entry.put("logger", logger);
        entry.put("message", message);
        logs.add(entry);
    }

    /**
     * ここまでのステップを {@code steps.json} へケース単位でマージ書込する。
     *
     * <p>
     * 失敗テストでも中断時点までのステップを残すため、{@code @AfterEach} から成功・失敗を問わず呼ぶ。
     */
    public void write() {
        try {
            Files.createDirectories(evidenceDir);
            Path file = evidenceDir.resolve("steps.json");
            ObjectNode root;
            if (Files.exists(file)) {
                root = (ObjectNode) MAPPER.readTree(file.toFile());
            } else {
                root = MAPPER.createObjectNode();
            }
            root.put("version", 1);
            root.put("feature", feature);
            root.put("target", target);
            ObjectNode entry = MAPPER.createObjectNode();
            entry.put("no", caseNo);
            entry.put("name", caseName);
            entry.set("steps", MAPPER.valueToTree(steps));
            if (!logs.isEmpty()) {
                // ハーネスイベント（実行中に逐次追記）とアプリログ（テスト終了時にまとめて追記）で
                // 記録順が揃わないため、時系列（ts）で安定ソートして書き出す。ts は HH:mm:ss.SSS の
                // 辞書順＝時刻順（ケース実行が日付をまたがない前提。またいだ場合は順序保証外）。
                logs.sort(Comparator.comparing(log -> (String) log.get("ts")));
                entry.set("logs", MAPPER.valueToTree(logs));
            }
            ArrayNode cases = root.withArray("cases");
            for (int i = 0; i < cases.size(); i++) {
                if (caseNo.equals(cases.get(i).path("no").asText())) {
                    cases.set(i, entry);
                    entry = null;
                    break;
                }
            }
            if (entry != null) {
                cases.add(entry);
            }
            WRITER.writeValue(file.toFile(), root);
        } catch (IOException e) {
            throw new UncheckedIOException("steps.json の書き込みに失敗しました: " + evidenceDir, e);
        }
    }
}
