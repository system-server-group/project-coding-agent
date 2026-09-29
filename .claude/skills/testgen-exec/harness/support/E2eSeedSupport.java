package testgen.e2e.support;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * シード CSV（明示 ID）の投入を支援する汎用部品。プロジェクトのシーダー（テーブル・機能ごとに 作成する。キットには含まれない）から利用する。
 *
 * <p>
 * テスト規約のデータ規律を機械強制する: シード CSV は全行に主キーを明示し（自動採番の列も {@code OVERRIDING SYSTEM VALUE}
 * で投入）、主キーはシード帯域（1〜帯域上限）で一意。 自動採番の開始位置は {@link #moveSequencePastSeedBand}
 * で帯域の次へ固定し、テスト中の UI 操作で 採番される ID をシード件数に依存させない（明示 ID と自動採番の衝突回避＋UI 採番 ID の決定性）。
 *
 * <p>
 * 帯域上限は既定値 {@link #SEED_ID_MAX} を持つ可変の設定で、プロジェクトの要求（必要なシード行数・
 * 採番開始値）に合わせて変えられる。変える場合はプロジェクト側（糊クラス等）に有効値を定数として 1 つ定義し、
 * {@link #parseSeedId} と {@link #moveSequencePastSeedBand} の両方へ同じ値を渡す（キット本体は書き換えない）。
 */
public final class E2eSeedSupport {

    /**
     * シード用 ID 帯域上限の既定値（シード CSV の主キーは 1〜帯域上限）。テスト中に UI 操作で
     * 自動採番される ID は帯域上限の次（帯域上限 + 1）から始まる。プロジェクトの要求に合わせて
     * 有効値を変える場合は、糊クラス等に定数を定義して {@link #parseSeedId} と
     * {@link #moveSequencePastSeedBand} の両方へ渡す（残る上限は主キー列の型限界のみで、ハーネス側の仮定は無い）。
     */
    public static final int SEED_ID_MAX = 1_000;

    private E2eSeedSupport() {}

    /**
     * CSV 正本の全文を読み込む（UTF-8。先頭の BOM は除去する）。
     *
     * @param csvPath 正本 CSV のパス
     * @return CSV 全文
     */
    public static String readContent(Path csvPath) {
        try {
            String content = Files.readString(csvPath, StandardCharsets.UTF_8);
            if (!content.isEmpty() && content.charAt(0) == '﻿') {
                content = content.substring(1);
            }
            return content;
        } catch (IOException e) {
            throw new UncheckedIOException("CSV の読み込みに失敗しました: " + csvPath, e);
        }
    }

    /**
     * RFC4180 の CSV 全文を走査してレコード（各セルの並び）へ分解する。引用符内のカンマ・改行・ エスケープ二重引用符（{@code ""}）を正しく扱う。行終端は
     * CRLF／LF／CR に対応する。
     *
     * @param content CSV 全文
     * @return レコードのリスト（先頭はヘッダー行）
     */
    public static List<List<String>> parseCsv(String content) {
        List<List<String>> records = new ArrayList<>();
        List<String> record = new ArrayList<>();
        StringBuilder field = new StringBuilder();
        boolean inQuotes = false;
        int index = 0;
        int length = content.length();
        while (index < length) {
            char current = content.charAt(index);
            if (inQuotes) {
                if (current == '"') {
                    if (index + 1 < length && content.charAt(index + 1) == '"') {
                        field.append('"');
                        index += 2;
                    } else {
                        inQuotes = false;
                        index++;
                    }
                } else {
                    field.append(current);
                    index++;
                }
            } else if (current == '"') {
                inQuotes = true;
                index++;
            } else if (current == ',') {
                record.add(field.toString());
                field.setLength(0);
                index++;
            } else if (current == '\r' || current == '\n') {
                record.add(field.toString());
                field.setLength(0);
                records.add(record);
                record = new ArrayList<>();
                index += current == '\r' && index + 1 < length && content.charAt(index + 1) == '\n'
                        ? 2
                        : 1;
            } else {
                field.append(current);
                index++;
            }
        }
        if (field.length() > 0 || !record.isEmpty()) {
            record.add(field.toString());
            records.add(record);
        }
        return records;
    }

    /**
     * ヘッダー行が期待した列（名前・順序）と完全一致することを検査する。件数だけの検査では列順の 取り違えが黙って通るため、名前まで照合して投入前に失敗させる。
     *
     * @param header CSV のヘッダー行
     * @param expected 期待する列名の並び（テーブル定義に一致させる）
     * @param csvPath エラーメッセージ用のパス
     */
    public static void requireHeader(List<String> header, String[] expected, Path csvPath) {
        if (!header.equals(List.of(expected))) {
            throw new IllegalStateException("CSV のヘッダーが想定と不一致: " + header + " 期待="
                    + List.of(expected) + " (" + csvPath + ")");
        }
    }

    /**
     * 主キー値を検証して整数化する（帯域上限は既定値 {@link #SEED_ID_MAX}）。
     *
     * @param columnName 主キーの列名（エラーメッセージ用）
     * @param value CSV のセル値
     * @param seen 既出 ID の集合（重複検出用。呼び出し側が CSV 単位で保持する）
     * @param csvPath エラーメッセージ用のパス
     * @return 検証済みの ID
     */
    public static int parseSeedId(
            String columnName,
            String value,
            Set<Integer> seen,
            Path csvPath) {
        return parseSeedId(columnName, value, seen, csvPath, SEED_ID_MAX);
    }

    /**
     * 主キー値を検証して整数化する。テストデータが一意に定まることを投入前に機械強制する—— 空・非整数・シード帯域外・重複はここで失敗させる。
     *
     * @param columnName 主キーの列名（エラーメッセージ用）
     * @param value CSV のセル値
     * @param seen 既出 ID の集合（重複検出用。呼び出し側が CSV 単位で保持する）
     * @param csvPath エラーメッセージ用のパス
     * @param seedIdMax シード帯域の上限（プロジェクトの有効値。{@link #moveSequencePastSeedBand} と同じ値を渡す）
     * @return 検証済みの ID
     */
    public static int parseSeedId(
            String columnName,
            String value,
            Set<Integer> seen,
            Path csvPath,
            int seedIdMax) {
        String trimmed = value == null ? "" : value.trim();
        if (trimmed.isEmpty()) {
            throw new IllegalStateException(columnName + " の無い行があります: " + csvPath);
        }
        final int id;
        try {
            id = Integer.parseInt(trimmed);
        } catch (NumberFormatException e) {
            throw new IllegalStateException(
                    columnName + " が整数ではありません: " + trimmed + " (" + csvPath + ")", e);
        }
        if (id < 1 || id > seedIdMax) {
            throw new IllegalStateException(
                    columnName + " がシード帯域（1〜" + seedIdMax + "）の外です: " + id + " (" + csvPath
                            + ")。帯域上限は可変（既定は E2eSeedSupport.SEED_ID_MAX。"
                            + "変える場合はプロジェクト側の有効値を parseSeedId と moveSequencePastSeedBand の両方へ渡す）");
        }
        if (!seen.add(id)) {
            throw new IllegalStateException(columnName + " が重複しています: " + id + " (" + csvPath + ")");
        }
        return id;
    }

    /**
     * 自動採番の開始位置をシード帯域の次へ固定する（帯域上限は既定値 {@link #SEED_ID_MAX}）。
     *
     * @param jdbc DB アクセス
     * @param table 対象テーブル名
     * @param column 自動採番の主キー列名
     */
    public static void moveSequencePastSeedBand(JdbcTemplate jdbc, String table, String column) {
        moveSequencePastSeedBand(jdbc, table, column, SEED_ID_MAX);
    }

    /**
     * 自動採番の開始位置をシード帯域の次（帯域上限 + 1）へ固定する。ベースライン初期化の 最後に、自動採番列を持つ各テーブルへ呼ぶ。
     *
     * @param jdbc DB アクセス
     * @param table 対象テーブル名
     * @param column 自動採番の主キー列名
     * @param seedIdMax シード帯域の上限（プロジェクトの有効値。{@link #parseSeedId} と同じ値を渡す）
     */
    public static void moveSequencePastSeedBand(
            JdbcTemplate jdbc, String table, String column, int seedIdMax) {
        jdbc.execute("SELECT setval(pg_get_serial_sequence('" + table + "', '" + column + "'), "
                + (seedIdMax + 1) + ", false)");
    }
}
