package testgen.e2e.data;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;

/**
 * 仕様書が「テスト実行時に生成する」と定めるファイルを、指定どおりの名前・内容で用意する。
 *
 * <p>
 * サイズ境界・ファイル名長境界の素材は、リポジトリのデータ正本には置かず（巨大ファイル・Windows の
 * パス長制限のため）、実行時に一時ディレクトリへ生成する（テストデータ規範）。生成規則は各機能の
 * データ正本（{@code 添付ファイル境界.csv}・{@code 添付ファイル名長_生成仕様.csv} 等）に一致させる。
 */
public final class E2eTestFiles {

    /** 生成規則で用いる埋め文字（{@code 0x41} = {@code 'A'}）。 */
    private static final byte FILL = 0x41;

    /** CSV の生成規則が定める UTF-8 BOM。 */
    private static final byte[] BOM = {(byte) 0xEF, (byte) 0xBB, (byte) 0xBF};

    /** CSV の生成規則が定める改行コード。 */
    private static final String CRLF = "\r\n";

    /**
     * 生成先の一時ディレクトリ。
     *
     * <p>
     * ファイル名長の境界（200文字・201文字）を扱うため、ディレクトリ名を短く固定してフルパスが Windows
     * のパス長制限（260文字）に収まるようにする（{@code createTempDirectory} の既定は
     * ランダムな長い名前になり、200文字のファイル名と併せると上限を超えて内容が渡らない）。
     */
    private static final Path DIR = createDir();

    private E2eTestFiles() {}

    private static Path createDir() {
        try {
            Path dir = Path.of(System.getProperty("java.io.tmpdir"), "tge2e");
            Files.createDirectories(dir);
            return dir;
        } catch (IOException e) {
            throw new UncheckedIOException("テスト用ファイルの生成先を作成できません", e);
        }
    }

    /**
     * 指定した名前・バイト数のファイルを生成する（内容は {@code 0x41} の繰り返し）。
     *
     * @param fileName ファイル名
     * @param size バイト数
     * @return 生成したファイルのパス
     */
    public static Path ofSize(String fileName, int size) {
        Path file = DIR.resolve(fileName);
        if (isUpToDate(file, size)) {
            return file;
        }
        byte[] content = new byte[size];
        Arrays.fill(content, FILL);
        return write(file, content);
    }

    /**
     * ファイル名長が指定文字数ちょうどのファイルを生成する（半角英字 {@code a} の繰り返し＋拡張子 {@code .bin}。内容は {@code 0x41} を 1 バイト）。
     *
     * @param nameLength 拡張子を含むファイル名の文字数（5 以上）
     * @return 生成したファイルのパス
     */
    public static Path ofNameLength(int nameLength) {
        String extension = ".bin";
        if (nameLength <= extension.length()) {
            throw new IllegalArgumentException("ファイル名長が短すぎます: " + nameLength);
        }
        String fileName = "a".repeat(nameLength - extension.length()) + extension;
        Path file = DIR.resolve(fileName);
        if (isUpToDate(file, 1)) {
            return file;
        }
        return write(file, new byte[] {FILL});
    }

    /**
     * 指定した総バイト数ちょうどの CSV を生成する（UTF-8 BOM ＋ ヘッダー行 ＋ CRLF ＋ 1 データ行 ＋ CRLF。 データ行は前置文字列と後置文字列の間を半角英字
     * {@code a} で埋め、全体が総バイト数になるよう個数を調整する）。
     *
     * <p>
     * マスタ管理のファイル容量境界（5MB ちょうど／5MB 超過）の素材に用いる。生成規則は各機能のデータ正本 （{@code ファイル容量境界.csv}）に一致させる。
     *
     * @param fileName ファイル名
     * @param headerLine ヘッダー行（改行を含まない）
     * @param rowPrefix データ行の前置文字列（例 {@code 1,}）
     * @param rowSuffix データ行の後置文字列（無い場合は空文字列）
     * @param totalBytes ファイル全体のバイト数
     * @return 生成したファイルのパス
     */
    public static Path csvOfSize(
            String fileName,
            String headerLine,
            String rowPrefix,
            String rowSuffix,
            int totalBytes) {
        Path file = DIR.resolve(fileName);
        if (isUpToDate(file, totalBytes)) {
            return file;
        }
        int fixed = BOM.length + utf8Length(headerLine) + CRLF.length() + utf8Length(rowPrefix)
                + utf8Length(rowSuffix) + CRLF.length();
        int fill = totalBytes - fixed;
        if (fill < 0) {
            throw new IllegalArgumentException("指定バイト数が固定部分より小さい: " + totalBytes);
        }
        String body = headerLine + CRLF + rowPrefix + "a".repeat(fill) + rowSuffix + CRLF;
        return write(file, withBom(body));
    }

    /**
     * ヘッダー行と本文行から CSV を生成する（UTF-8 BOM ＋ 各行 ＋ CRLF）。
     *
     * <p>
     * CSV 行番号が 5 桁になる大量行の素材（{@code 行番号5桁_生成仕様.csv}）等に用いる。
     *
     * @param fileName ファイル名
     * @param headerLine ヘッダー行（改行を含まない）
     * @param bodyLines 本文行（改行を含まない）
     * @return 生成したファイルのパス
     */
    public static Path csvOfLines(String fileName, String headerLine, List<String> bodyLines) {
        StringBuilder content = new StringBuilder(headerLine).append(CRLF);
        for (String line : bodyLines) {
            content.append(line).append(CRLF);
        }
        byte[] bytes = withBom(content.toString());
        Path file = DIR.resolve(fileName);
        if (isUpToDate(file, bytes.length)) {
            return file;
        }
        return write(file, bytes);
    }

    /**
     * 数字を全角（{@code ０〜９}）に変換する（マスタ名の生成規則「全角数字」に用いる）。
     *
     * @param value 変換する値
     * @param digits 0 埋めする桁数
     * @return 全角数字の文字列
     */
    public static String fullWidthDigits(int value, int digits) {
        String half = String.format("%0" + digits + "d", value);
        StringBuilder full = new StringBuilder(half.length());
        for (int i = 0; i < half.length(); i++) {
            full.append((char) ('０' + (half.charAt(i) - '0')));
        }
        return full.toString();
    }

    /**
     * ダウンロードした実物を保存する先のパスを返す（ラウンドトリップ検証で、取得したファイルを そのままアップロードするために用いる）。
     *
     * @param fileName 保存するファイル名
     * @return 保存先のパス（ファイルはまだ存在しない）
     */
    public static Path downloadTarget(String fileName) {
        Path file = DIR.resolve(fileName);
        file.toFile().deleteOnExit();
        return file;
    }

    /** UTF-8 BOM を前置したバイト列。 */
    private static byte[] withBom(String content) {
        byte[] body = content.getBytes(StandardCharsets.UTF_8);
        byte[] bytes = new byte[BOM.length + body.length];
        System.arraycopy(BOM, 0, bytes, 0, BOM.length);
        System.arraycopy(body, 0, bytes, BOM.length, body.length);
        return bytes;
    }

    /** UTF-8 でのバイト数。 */
    private static int utf8Length(String value) {
        return value.getBytes(StandardCharsets.UTF_8).length;
    }

    private static boolean isUpToDate(Path file, int size) {
        try {
            return Files.isRegularFile(file) && Files.size(file) == size;
        } catch (IOException e) {
            return false;
        }
    }

    private static Path write(Path file, byte[] content) {
        try {
            Files.write(file, content);
            file.toFile().deleteOnExit();
            return file;
        } catch (IOException e) {
            throw new UncheckedIOException("テスト用ファイルを生成できません: " + file, e);
        }
    }
}
