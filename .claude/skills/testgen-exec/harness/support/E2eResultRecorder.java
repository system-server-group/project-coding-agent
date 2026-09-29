package testgen.e2e.support;

import com.fasterxml.jackson.core.util.DefaultIndenter;
import com.fasterxml.jackson.core.util.DefaultPrettyPrinter;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.ObjectWriter;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.junit.jupiter.api.extension.TestWatcher;

/**
 * E2E 各ケースの機械判定（合否）を証跡フォルダの {@code results.json} に記録する JUnit 拡張。
 *
 * <p>
 * 証跡モード（{@code e2e.evidence=true}）でのみ動作する。テストクラスと証跡ディレクトリの対応は基底クラスが
 * {@link #registerEvidenceDir(String, Path)} で登録し、本 Watcher は表示名（ケース No を含む）と合否を証跡 ディレクトリごとの
 * {@code results.json} に書き出す。受け入れ用 index.html を生成する testgen-exec スキル同梱の
 * {@code e2e_evidence_index.py} がこれを読み、各ケースの合否として反映する。
 */
public class E2eResultRecorder implements TestWatcher {

    private static final Pattern CASE_NO = Pattern.compile("No\\.(\\d+(?:-\\d+)?)");
    private static final Map<String, Path> EVIDENCE_DIR_BY_CLASS = new ConcurrentHashMap<>();
    private static final Map<Path, List<Map<String, String>>> RESULTS = new ConcurrentHashMap<>();
    private static final ObjectMapper MAPPER = new ObjectMapper();
    // 改行は LF 固定（実行 OS に依存させない。書式は既定の整形のまま、行区切りだけを固定する）。
    private static final ObjectWriter WRITER = MAPPER
            .writer(new DefaultPrettyPrinter().withObjectIndenter(new DefaultIndenter("  ", "\n")));

    /**
     * テストクラスと証跡ディレクトリの対応を登録する（基底クラスの {@code @BeforeEach} から呼ぶ）。
     *
     * <p>
     * 証跡ディレクトリはテスト実施単位を含む（{@code <実施単位>/<機能>/エビデンス}）ため、同じ機能でも実施単位が異なれば 別の {@code results.json}
     * に記録される。
     *
     * @param className テストクラスの完全修飾名
     * @param evidenceDir 証跡ディレクトリ
     */
    public static void registerEvidenceDir(String className, Path evidenceDir) {
        EVIDENCE_DIR_BY_CLASS.put(className, evidenceDir);
    }

    @Override
    public void testSuccessful(ExtensionContext context) {
        record(context, "pass", null);
    }

    @Override
    public void testFailed(ExtensionContext context, Throwable cause) {
        record(context, "fail", cause);
    }

    @Override
    public void testAborted(ExtensionContext context, Throwable cause) {
        record(context, "aborted", cause);
    }

    private void record(ExtensionContext context, String status, Throwable cause) {
        if (!E2eEvidence.enabled()) {
            return;
        }
        String className = context.getTestClass().map(r -> r.getName()).orElse(null);
        Path evidenceDir = className == null ? null : EVIDENCE_DIR_BY_CLASS.get(className);
        if (evidenceDir == null) {
            return;
        }
        Matcher matcher = CASE_NO.matcher(context.getDisplayName());
        Map<String, String> entry = new LinkedHashMap<>();
        entry.put("no", matcher.find() ? matcher.group(1) : "");
        entry.put("name", context.getDisplayName());
        entry.put("status", status);
        entry.put("message", message(cause));
        List<Map<String, String>> list =
                RESULTS.computeIfAbsent(evidenceDir, key -> new ArrayList<>());
        synchronized (list) {
            list.add(entry);
            write(evidenceDir, list);
        }
    }

    private static String message(Throwable cause) {
        if (cause == null) {
            return "";
        }
        String text =
                cause.getMessage() != null ? cause.getMessage() : cause.getClass().getSimpleName();
        text = text.strip();
        int limit = 300;
        return text.length() > limit ? text.substring(0, limit) + "…" : text;
    }

    private void write(Path evidenceDir, List<Map<String, String>> list) {
        try {
            Files.createDirectories(evidenceDir);
            WRITER.writeValue(evidenceDir.resolve("results.json").toFile(), list);
        } catch (IOException e) {
            throw new UncheckedIOException("results.json の書き込みに失敗しました: " + evidenceDir, e);
        }
    }
}
