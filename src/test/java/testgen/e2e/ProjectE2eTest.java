package testgen.e2e;

import com.system_server.ai_demo.AiDemoApplication;
import com.system_server.ai_demo.TestcontainersConfiguration;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import testgen.e2e.support.E2eBaseTest;
import testgen.e2e.support.E2eSeedSupport;
import testgen.e2e.support.E2eSpecLayout;

/**
 * 本プロジェクト向けの糊クラス（testgen-exec ハーネスキットとアプリの接合）。
 *
 * <p>
 * ハーネスキット（{@link E2eBaseTest}）はアプリに依存しないため、アプリ起動の注釈・ルートパッケージ・ ベースライン投入（シーダー）を本クラスが供給する。E2E
 * テストクラスは本クラスを継承する。
 *
 * <p>
 * ベースラインはテスト実施単位の {@code _baseline/data/} を正本とし、各ケースの前に全表を初期化して
 * 再投入する（{@link #resetBaseline()}）。案件情報・添付ファイルは自動採番のため、採番開始位置を シード帯域の次へ固定して UI 採番の管理コードを決定的にする。
 */
@SpringBootTest(classes = AiDemoApplication.class,
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import(TestcontainersConfiguration.class)
@ActiveProfiles("e2e")
public abstract class ProjectE2eTest extends E2eBaseTest {

    /** 会社マスタの列（テーブル定義書「会社マスタレコード」に一致させる）。 */
    private static final String[] COMPANY_COLUMNS = {"company_id", "company_name"};

    /** 部署マスタの列（テーブル定義書「部署マスタレコード」に一致させる）。 */
    private static final String[] DEPARTMENT_COLUMNS = {"department_id", "department_name"};

    /** ユーザマスタの列（テーブル定義書「ユーザマスタレコード」に一致させる）。 */
    private static final String[] USER_COLUMNS =
            {"user_id", "user_name", "email", "department_id", "role", "password"};

    /** 案件情報の列（テーブル定義書「案件情報」に一致させる。管理コードは自動採番）。 */
    private static final String[] PROJECT_COLUMNS = {"management_code", "title", "status",
            "client_name", "commercial_flow", "summary", "process", "start_date", "end_date",
            "skill", "headcount", "remaining_count", "bp_recruitment", "bp_recruitment_detail",
            "work_location", "estimated_unit_price", "contract_type", "note", "created_by",
            "created_department", "created_at", "updated_by", "updated_department", "updated_at"};

    /**
     * 案件情報の列のうち、日付・日時として投入する列（{@code ?::date}／{@code ?::timestamptz} で キャストし、空文字は NULL として扱う）。
     */
    private static final String[] PROJECT_COLUMN_CASTS =
            {"", "", "", "", "", "", "", "::date", "::date", "", "", "", "", "", "", "", "", "", "",
                    "", "::timestamptz", "", "", "::timestamptz"};

    /**
     * 案件情報添付ファイルのシード CSV の列。ファイル実体は {@code file_data} を直接持たず、 素材ファイル名（{@code file_source}。CSV
     * と同じディレクトリ）から読み込む。
     */
    private static final String[] ATTACHMENT_COLUMNS = {"file_id", "management_code", "file_name",
            "file_size", "file_source", "created_by", "created_department", "created_at"};

    /**
     * ベースライン初期化で全消去する表。子から親の順に並べる（外部キー参照の順序）。 案件情報・添付ファイルはベースラインに正本を持たず、各ケースが必要に応じて投入する。
     */
    private static final String TRUNCATE_TABLES =
            "project_attachments, projects, users, departments, companies";

    @Autowired
    private JdbcTemplate jdbc;

    @Override
    protected String appRootPackage() {
        return "com.system_server.ai_demo";
    }

    @Override
    protected void resetBaseline() {
        jdbc.execute("TRUNCATE TABLE " + TRUNCATE_TABLES + " RESTART IDENTITY CASCADE");
        Path baseline = E2eSpecLayout.baselineDataDir();
        replaceCompanies(baseline.resolve("companies.csv"));
        replaceDepartments(baseline.resolve("departments.csv"));
        replaceUsers(baseline.resolve("users.csv"));
        E2eSeedSupport.moveSequencePastSeedBand(jdbc, "projects", "management_code");
        E2eSeedSupport.moveSequencePastSeedBand(jdbc, "project_attachments", "file_id");
    }

    /**
     * 会社マスタを CSV 正本の内容で置き換える（ベースライン投入・ケース固有データの差し替えに使う）。
     *
     * @param csv 会社マスタのシード CSV（列はテーブル定義に一致）
     */
    protected void replaceCompanies(Path csv) {
        List<Object[]> batch = new ArrayList<>();
        Set<Integer> seen = new HashSet<>();
        for (List<String> row : readSeed(csv, COMPANY_COLUMNS)) {
            batch.add(new Object[] {E2eSeedSupport.parseSeedId("company_id", row.get(0), seen, csv),
                    row.get(1)});
        }
        jdbc.update("DELETE FROM companies");
        jdbc.batchUpdate("INSERT INTO companies (company_id, company_name) VALUES (?, ?)", batch);
    }

    /**
     * 部署マスタを CSV 正本の内容で置き換える。
     *
     * @param csv 部署マスタのシード CSV（列はテーブル定義に一致）
     */
    protected void replaceDepartments(Path csv) {
        List<Object[]> batch = new ArrayList<>();
        Set<Integer> seen = new HashSet<>();
        for (List<String> row : readSeed(csv, DEPARTMENT_COLUMNS)) {
            batch.add(new Object[] {
                    E2eSeedSupport.parseSeedId("department_id", row.get(0), seen, csv),
                    row.get(1)});
        }
        jdbc.update("DELETE FROM departments");
        jdbc.batchUpdate("INSERT INTO departments (department_id, department_name) VALUES (?, ?)",
                batch);
    }

    /**
     * ユーザマスタを CSV 正本の内容で置き換える。
     *
     * @param csv ユーザマスタのシード CSV（列はテーブル定義に一致。主キーは文字列のため帯域検査の対象外）
     */
    protected void replaceUsers(Path csv) {
        jdbc.update("DELETE FROM users");
        addUsers(csv);
    }

    /**
     * ユーザマスタへ CSV 正本の内容を追加する（ベースラインのユーザを残したままケース固有のユーザを 足す場合に使う）。
     *
     * @param csv ユーザマスタのシード CSV（列はテーブル定義に一致）
     */
    protected void addUsers(Path csv) {
        List<Object[]> batch = new ArrayList<>();
        for (List<String> row : readSeed(csv, USER_COLUMNS)) {
            batch.add(new Object[] {row.get(0), row.get(1), row.get(2),
                    requireInt("department_id", row.get(3), csv), row.get(4), row.get(5)});
        }
        String sql = "INSERT INTO users (user_id, user_name, email, department_id, role, password)"
                + " VALUES (?, ?, ?, ?, ?, ?)";
        jdbc.batchUpdate(sql, batch);
    }

    /**
     * 案件情報を CSV 正本の内容で投入する（管理コードは CSV の明示値。自動採番へ上書き投入する）。
     *
     * @param csv 案件情報のシード CSV（列はテーブル定義に一致）
     */
    protected void insertProjects(Path csv) {
        List<Object[]> batch = new ArrayList<>();
        Set<Integer> seen = new HashSet<>();
        for (List<String> row : readSeed(csv, PROJECT_COLUMNS)) {
            Object[] values = new Object[PROJECT_COLUMNS.length];
            values[0] = E2eSeedSupport.parseSeedId("management_code", row.get(0), seen, csv);
            for (int i = 1; i < PROJECT_COLUMNS.length; i++) {
                values[i] = row.get(i).isEmpty() ? null : row.get(i);
            }
            batch.add(values);
        }
        jdbc.batchUpdate("INSERT INTO projects (" + String.join(", ", PROJECT_COLUMNS)
                + ") OVERRIDING SYSTEM VALUE VALUES (" + placeholders(PROJECT_COLUMN_CASTS) + ")",
                batch);
    }

    /**
     * 案件情報添付ファイルを CSV 正本の内容で投入する。ファイル実体は CSV と同じディレクトリの 素材ファイル（{@code file_source} 列）から読み込む。
     *
     * @param csv 添付ファイルのシード CSV
     */
    protected void insertAttachments(Path csv) {
        List<Object[]> batch = new ArrayList<>();
        Set<Integer> seen = new HashSet<>();
        for (List<String> row : readSeed(csv, ATTACHMENT_COLUMNS)) {
            byte[] data = readFileSource(csv, row.get(4));
            int declaredSize = requireInt("file_size", row.get(3), csv);
            if (declaredSize != data.length) {
                throw new IllegalStateException("file_size が素材ファイルの実測と不一致: 宣言=" + declaredSize
                        + " 実測=" + data.length + " (" + csv + ")");
            }
            batch.add(new Object[] {E2eSeedSupport.parseSeedId("file_id", row.get(0), seen, csv),
                    requireInt("management_code", row.get(1), csv), row.get(2), data, declaredSize,
                    row.get(5), row.get(6), row.get(7)});
        }
        jdbc.batchUpdate(
                "INSERT INTO project_attachments (file_id, management_code, file_name,"
                        + " file_data, file_size, created_by, created_department, created_at)"
                        + " OVERRIDING SYSTEM VALUE VALUES (?, ?, ?, ?, ?, ?, ?, ?::timestamptz)",
                batch);
    }

    /**
     * 実施単位からの相対パスでデータ正本を解決する（仕様書の「参照テストデータ」欄の表記に合わせる）。
     *
     * @param relativePath 実施単位からの相対パス（例 {@code 01_共通レイアウト/data/部署未登録ユーザ.csv}）
     * @return データ正本の絶対パス
     */
    protected Path unitData(String relativePath) {
        return E2eSpecLayout.targetDir().resolve(relativePath);
    }

    /** シード CSV と同じディレクトリの素材ファイルを読み込む。 */
    private static byte[] readFileSource(Path csv, String fileSource) {
        Path source = csv.resolveSibling(fileSource);
        try {
            return Files.readAllBytes(source);
        } catch (IOException e) {
            throw new UncheckedIOException("添付ファイルの素材を読み込めません: " + source, e);
        }
    }

    /** 列ごとのキャスト指定（{@code ::date} 等）を付けたプレースホルダ列を組み立てる。 */
    private static String placeholders(String[] casts) {
        StringBuilder sql = new StringBuilder();
        for (int i = 0; i < casts.length; i++) {
            sql.append(i == 0 ? "" : ", ").append('?').append(casts[i]);
        }
        return sql.toString();
    }

    /**
     * シード CSV を読み、ヘッダーと列数を検査してデータ行を返す。列の取り違え・欠けを投入前に失敗させる。
     *
     * @param csv シード CSV のパス
     * @param columns 期待する列名の並び
     * @return データ行（ヘッダーを除く）
     */
    private static List<List<String>> readSeed(Path csv, String[] columns) {
        List<List<String>> records = E2eSeedSupport.parseCsv(E2eSeedSupport.readContent(csv));
        if (records.isEmpty()) {
            throw new IllegalStateException("シード CSV が空です: " + csv);
        }
        E2eSeedSupport.requireHeader(records.get(0), columns, csv);
        List<List<String>> rows = records.subList(1, records.size());
        for (int i = 0; i < rows.size(); i++) {
            if (rows.get(i).size() != columns.length) {
                throw new IllegalStateException("列数が想定と不一致です: " + (i + 2) + "行目 実測="
                        + rows.get(i).size() + " 期待=" + columns.length + " (" + csv + ")");
            }
        }
        return rows;
    }

    /**
     * 整数列のセル値を検証して整数化する。
     *
     * @param columnName 列名（エラーメッセージ用）
     * @param value セル値
     * @param csv エラーメッセージ用のパス
     * @return 整数値
     */
    private static int requireInt(String columnName, String value, Path csv) {
        try {
            return Integer.parseInt(value == null ? "" : value.trim());
        } catch (NumberFormatException e) {
            throw new IllegalStateException(columnName + " が整数ではありません: " + value + " (" + csv + ")",
                    e);
        }
    }
}
