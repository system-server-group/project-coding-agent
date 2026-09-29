package com.system_server.ai_demo.apps.master.common.csv;

import com.system_server.ai_demo.commons.validation.CheckMessages;
import java.io.IOException;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import org.springframework.web.multipart.MultipartFile;

/**
 * マスタの CSV 洗い替えで共有する汎用処理。
 *
 * <p>
 * ファイル形式チェック（容量・読取可・列数・ボディ件数）と読み取り（BOM 除去・引用符対応パース）、CSV 生成（UTF-8 BOM 付き・CRLF・クォート）、
 * エラー整形、共通の列入力値チェック（整数IDの必須/整数/値域/一意、全角名称の必須/最大文字数/全角のみ）を提供する。 形式違反は {@link CsvFormatException}
 * を送出する。入力値チェックは違反メッセージ（問題なければ {@code null}）を返す。
 */
public final class CsvSupport {

    private static final long MAX_FILE_SIZE = 5L * 1024 * 1024;
    private static final byte[] BOM = {(byte) 0xEF, (byte) 0xBB, (byte) 0xBF};

    private static final String MSG_NO_DATA = "CSVファイルにデータがありませんでした。";
    private static final String MSG_TOO_LARGE = "ファイルの容量が大きすぎます(5MBまで)。";
    private static final String MSG_UNREADABLE = "CSVの読み取りに失敗しました。フォーマットが正しいかファイルを確認してください。";

    private CsvSupport() {}

    /**
     * アップロードされた CSV をファイル形式チェックの上で読み取り、ヘッダー行を含む全行を返す。
     *
     * <p>
     * チェックは①容量(5MB以下)②読取可・各行が指定列数③ボディ行1件以上 の順で行い、違反時は {@link CsvFormatException} を送出する。
     *
     * @param file アップロードされた CSV ファイル
     * @param expectedColumns 1 行あたりの列数
     * @return ヘッダー行を含む全行（各行は列値の配列）
     */
    public static List<String[]> parse(MultipartFile file, int expectedColumns) {
        // 防御的な null ガード（ファイル未指定はコントローラーの必須チェックで弾く想定）。
        if (file == null) {
            throw new CsvFormatException(MSG_NO_DATA);
        }
        // ① 容量（5MB以下であること）
        if (file.getSize() > MAX_FILE_SIZE) {
            throw new CsvFormatException(MSG_TOO_LARGE);
        }
        // ② 既定の様式（読取可・各行が指定列数であること）
        List<String[]> rows = parseRows(readContent(file), expectedColumns);
        // ③ ボディ行が1件以上であること（空・ヘッダーのみは MSG_NO_DATA）
        if (rows.size() <= 1) {
            throw new CsvFormatException(MSG_NO_DATA);
        }
        return rows;
    }

    /**
     * ヘッダーとボディ行を CSV（UTF-8 BOM 付き・CRLF）に変換する。各値は必要に応じてクォートする。
     *
     * @param header ヘッダー行（カンマ区切り済みの文字列）
     * @param rows ボディ行（各行は列値の配列）
     * @return CSV のバイト列
     */
    public static byte[] toCsv(String header, List<String[]> rows) {
        StringBuilder builder = new StringBuilder();
        builder.append(header).append("\r\n");
        for (String[] row : rows) {
            for (int i = 0; i < row.length; i++) {
                if (i > 0) {
                    builder.append(',');
                }
                builder.append(quote(row[i]));
            }
            builder.append("\r\n");
        }
        byte[] body = builder.toString().getBytes(StandardCharsets.UTF_8);
        byte[] result = new byte[BOM.length + body.length];
        System.arraycopy(BOM, 0, result, 0, BOM.length);
        System.arraycopy(body, 0, result, BOM.length, body.length);
        return result;
    }

    /**
     * 入力値チェックのエラーメッセージを整形する。
     *
     * @param csvLineNumber CSV 行番号（ヘッダー行を含む。4桁0埋め、5桁以上はそのまま）
     * @param dataLineNumber データ行番号（ヘッダー行を除く）
     * @param column 列ヘッダー名
     * @param message エラーメッセージ
     * @return 整形済みのエラーメッセージ
     */
    public static String formatError(
            int csvLineNumber,
            int dataLineNumber,
            String column,
            String message) {
        return String.format("%04d - データ%d行目 %s: %s", csvLineNumber, dataLineNumber, column,
                message);
    }

    /**
     * 文字列が全角文字のみで構成されているか判定する（半角英数字・半角記号・制御文字・半角カタカナを不可）。
     *
     * @param value 判定対象
     * @return 全角文字のみなら {@code true}
     */
    public static boolean isFullWidthOnly(String value) {
        return value.codePoints().allMatch(
                codePoint -> codePoint >= 0xA0 && !(codePoint >= 0xFF61 && codePoint <= 0xFF9F));
    }

    /**
     * 整数 ID 列の入力値チェック（必須／整数／最小値／最大値／一意）を行う。
     *
     * @param raw CSV から読み取った生値
     * @param seenIds 既出 ID の集合（呼び出し側で行間共有。チェック通過時に追加される）
     * @param min 最小許容値
     * @param max 最大許容値
     * @return 違反メッセージ、問題なければ {@code null}
     */
    public static String validateIntegerId(String raw, Set<Integer> seenIds, int min, int max) {
        if (raw == null || raw.isEmpty()) {
            return CheckMessages.REQUIRED_INPUT;
        }
        if (!raw.matches("-?\\d+")) {
            return CheckMessages.INTEGER_VALUE;
        }
        BigInteger value = new BigInteger(raw);
        if (value.compareTo(BigInteger.valueOf(min)) < 0) {
            return CheckMessages.MIN_VALUE.replace("{value}", String.valueOf(min));
        }
        if (value.compareTo(BigInteger.valueOf(max)) > 0) {
            return CheckMessages.MAX_VALUE.replace("{value}", String.valueOf(max));
        }
        int id = value.intValueExact();
        if (!seenIds.add(id)) {
            return "重複して設定しないでください(" + id + ")";
        }
        return null;
    }

    /**
     * 全角名称列の入力値チェック（必須／最大文字数／全角のみ）を行う。
     *
     * @param raw CSV から読み取った生値
     * @param maxLength 最大文字数
     * @return 違反メッセージ、問題なければ {@code null}
     */
    public static String validateFullWidthName(String raw, int maxLength) {
        if (raw == null || raw.isEmpty()) {
            return CheckMessages.REQUIRED_INPUT;
        }
        if (raw.codePointCount(0, raw.length()) > maxLength) {
            return CheckMessages.MAX_LENGTH.replace("{max}", String.valueOf(maxLength));
        }
        if (!isFullWidthOnly(raw)) {
            return CheckMessages.ALLOWED_CHARACTERS;
        }
        return null;
    }

    private static String readContent(MultipartFile file) {
        byte[] bytes;
        try {
            bytes = file.getBytes();
        } catch (IOException ex) {
            throw new CsvFormatException(MSG_UNREADABLE);
        }
        int offset = 0;
        if (bytes.length >= 3 && (bytes[0] & 0xFF) == 0xEF && (bytes[1] & 0xFF) == 0xBB
                && (bytes[2] & 0xFF) == 0xBF) {
            offset = 3;
        }
        return new String(bytes, offset, bytes.length - offset, StandardCharsets.UTF_8);
    }

    private static List<String[]> parseRows(String content, int expectedColumns) {
        List<String[]> rows = new ArrayList<>();
        for (String line : content.split("\r\n|\n|\r", -1)) {
            rows.add(parseLine(line));
        }
        if (!rows.isEmpty()) {
            String[] last = rows.get(rows.size() - 1);
            if (last.length == 1 && last[0].isEmpty()) {
                rows.remove(rows.size() - 1);
            }
        }
        for (String[] row : rows) {
            if (row.length != expectedColumns) {
                throw new CsvFormatException(MSG_UNREADABLE);
            }
        }
        return rows;
    }

    private static String[] parseLine(String line) {
        List<String> fields = new ArrayList<>();
        StringBuilder field = new StringBuilder();
        boolean inQuotes = false;
        int index = 0;
        while (index < line.length()) {
            char current = line.charAt(index);
            if (inQuotes) {
                if (current == '"') {
                    if (index + 1 < line.length() && line.charAt(index + 1) == '"') {
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
                fields.add(field.toString());
                field.setLength(0);
                index++;
            } else {
                field.append(current);
                index++;
            }
        }
        if (inQuotes) {
            throw new CsvFormatException(MSG_UNREADABLE);
        }
        fields.add(field.toString());
        return fields.toArray(new String[0]);
    }

    private static String quote(String value) {
        if (value.contains(",") || value.contains("\"") || value.contains("\r")
                || value.contains("\n")) {
            return "\"" + value.replace("\"", "\"\"") + "\"";
        }
        return value;
    }
}
