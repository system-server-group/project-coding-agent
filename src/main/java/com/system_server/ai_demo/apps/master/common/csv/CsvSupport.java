package com.system_server.ai_demo.apps.master.common.csv;

import com.system_server.ai_demo.commons.validation.CheckMessages;
import java.io.IOException;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import org.springframework.web.multipart.MultipartFile;

/**
 * マスタの CSV 洗い替えで共有する汎用処理。
 *
 * <p>
 * ファイル形式チェック（容量・読取可・列数・列ヘッダー・ボディ件数）と読み取り（UTF-8 BOM 付き・CRLF のみ受理・引用符対応パース）、CSV 生成（UTF-8 BOM
 * 付き・CRLF・クォート）、 エラー整形、共通の列入力値チェック（整数IDの必須/整数/値域/一意、全角名称の必須/最大文字数/全角のみ）を提供する。 形式違反は
 * {@link CsvFormatException} を送出する。入力値チェックは違反メッセージ（問題なければ {@code null}）を返す。
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
     * チェックは①容量(5MB以下)②既定の様式（UTF-8 BOM 付き・行の終わりが CR+LF・読取可・各行が指定列数・1行目が指定の列ヘッダーと一致）③ボディ行1件以上
     * の順で行い、違反時は {@link CsvFormatException} を送出する。列ヘッダーの一致は、読み取り規則で復元した値と列名・列順の完全一致で判定する。
     *
     * @param file アップロードされた CSV ファイル
     * @param expectedHeader 期待する列ヘッダー（列順どおりの列名。長さが 1 行あたりの列数）
     * @return ヘッダー行を含む全行（各行は列値の配列）
     */
    public static List<String[]> parse(MultipartFile file, String[] expectedHeader) {
        // 防御的な null ガード（ファイル未指定はコントローラーの必須チェックで弾く想定）。
        if (file == null) {
            throw new CsvFormatException(MSG_NO_DATA);
        }
        // ① 容量（5MB以下であること）
        if (file.getSize() > MAX_FILE_SIZE) {
            throw new CsvFormatException(MSG_TOO_LARGE);
        }
        // ② 既定の様式（UTF-8 BOM 付き・行の終わりが CR+LF・読取可・各行が指定列数・1行目が指定の列ヘッダーであること）
        List<String[]> rows = parseRows(readContent(file), expectedHeader.length);
        if (!rows.isEmpty() && !Arrays.equals(rows.get(0), expectedHeader)) {
            throw new CsvFormatException(MSG_UNREADABLE);
        }
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

    /**
     * アップロードされた CSV を UTF-8 BOM 付きの UTF-8 として読み取り、BOM を除いた内容を返す。
     *
     * <p>
     * 空ファイルは空文字列を返す（ボディ件数のチェックで {@code MSG_NO_DATA} とする）。空でなく先頭が BOM でない場合は、既定の様式ではないとして
     * {@link CsvFormatException} を送出する。
     *
     * @param file アップロードされた CSV ファイル
     * @return BOM 除去済みの CSV の内容
     */
    private static String readContent(MultipartFile file) {
        byte[] bytes;
        try {
            bytes = file.getBytes();
        } catch (IOException ex) {
            throw new CsvFormatException(MSG_UNREADABLE);
        }
        if (bytes.length == 0) {
            return "";
        }
        if (bytes.length < BOM.length || !Arrays.equals(bytes, 0, BOM.length, BOM, 0, BOM.length)) {
            throw new CsvFormatException(MSG_UNREADABLE);
        }
        return new String(bytes, BOM.length, bytes.length - BOM.length, StandardCharsets.UTF_8);
    }

    /**
     * CSV の内容を CSV共通仕様の読み取り規則に従って行・列に分解する。
     *
     * <ul>
     * <li>先頭が囲み文字（"）のフィールドは、対応する閉じの囲み文字までを値とし、値の中の「""」は「"」に復元する。中のカンマ・改行は値の一部。</li>
     * <li>先頭が囲み文字でないフィールドは、次の区切り文字または行の終わりまでをそのまま値とする（途中の「"」も値の一部）。</li>
     * <li>閉じられていない囲み文字、または閉じの囲み文字の直後に区切り文字・行の終わり・ファイル末尾以外の文字が続く場合は、既定の様式ではないとして
     * {@link CsvFormatException} を送出する。</li>
     * <li>囲み文字の外の行の終わりは CR+LF のみ受け付ける（最終行に限り行の終わりを省略できる）。CR・LF 単独は既定の様式ではないとして
     * {@link CsvFormatException} を送出する。囲み文字の中の改行は種類を問わず値の一部とする。</li>
     * </ul>
     *
     * @param content BOM 除去済みの CSV の内容
     * @param expectedColumns 1 行あたりの列数
     * @return 全行（各行は列値の配列）
     */
    private static List<String[]> parseRows(String content, int expectedColumns) {
        List<String[]> rows = new ArrayList<>();
        int index = 0;
        while (index < content.length()) {
            List<String> fields = new ArrayList<>();
            StringBuilder field = new StringBuilder();
            while (true) {
                index = readField(content, index, field);
                fields.add(field.toString());
                field.setLength(0);
                if (index >= content.length() || content.charAt(index) != ',') {
                    break;
                }
                index++;
            }
            index = skipLineBreak(content, index);
            rows.add(fields.toArray(new String[0]));
        }
        for (String[] row : rows) {
            if (row.length != expectedColumns) {
                throw new CsvFormatException(MSG_UNREADABLE);
            }
        }
        return rows;
    }

    /**
     * 1 フィールドを読み取って {@code field} に追記し、フィールド直後（区切り文字・行の終わり・ファイル末尾）の位置を返す。
     *
     * @param content CSV の内容
     * @param index フィールドの先頭位置
     * @param field 読み取った値の追記先
     * @return フィールド直後の位置
     */
    private static int readField(String content, int index, StringBuilder field) {
        if (index < content.length() && content.charAt(index) == '"') {
            return readQuotedField(content, index + 1, field);
        }
        int position = index;
        while (position < content.length() && !isFieldEnd(content.charAt(position))) {
            field.append(content.charAt(position));
            position++;
        }
        return position;
    }

    /**
     * 囲み文字で始まるフィールドを、開きの囲み文字の次の位置から閉じの囲み文字まで読み取り、閉じの囲み文字の直後の位置を返す。
     *
     * @param content CSV の内容
     * @param index 開きの囲み文字の次の位置
     * @param field 読み取った値の追記先
     * @return 閉じの囲み文字の直後の位置
     */
    private static int readQuotedField(String content, int index, StringBuilder field) {
        int position = index;
        while (position < content.length()) {
            char current = content.charAt(position);
            if (current != '"') {
                // カンマ・改行も値の一部として保持する
                field.append(current);
                position++;
            } else if (position + 1 < content.length() && content.charAt(position + 1) == '"') {
                // 「""」は「"」に復元する
                field.append('"');
                position += 2;
            } else {
                // 閉じの囲み文字。直後は区切り文字・行の終わり・ファイル末尾のみ許容する
                position++;
                if (position < content.length() && !isFieldEnd(content.charAt(position))) {
                    throw new CsvFormatException(MSG_UNREADABLE);
                }
                return position;
            }
        }
        // 閉じられていない囲み文字
        throw new CsvFormatException(MSG_UNREADABLE);
    }

    private static boolean isFieldEnd(char character) {
        return character == ',' || character == '\r' || character == '\n';
    }

    /**
     * 行の終わり（CR+LF）を 1 つ読み飛ばした位置を返す。ファイル末尾（最終行の行の終わりの省略）ならそのまま返す。
     *
     * <p>
     * CR・LF 単独の行の終わりは既定の様式ではないとして {@link CsvFormatException} を送出する。
     *
     * @param content CSV の内容
     * @param index 行の終わりの位置
     * @return 次の行の先頭位置
     */
    private static int skipLineBreak(String content, int index) {
        if (index >= content.length()) {
            return index;
        }
        if (content.startsWith("\r\n", index)) {
            return index + 2;
        }
        throw new CsvFormatException(MSG_UNREADABLE);
    }

    private static String quote(String value) {
        if (value.contains(",") || value.contains("\"") || value.contains("\r")
                || value.contains("\n")) {
            return "\"" + value.replace("\"", "\"\"") + "\"";
        }
        return value;
    }
}
