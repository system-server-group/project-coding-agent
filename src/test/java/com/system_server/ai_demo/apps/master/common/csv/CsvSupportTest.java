package com.system_server.ai_demo.apps.master.common.csv;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.nio.charset.StandardCharsets;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.multipart.MultipartFile;

class CsvSupportTest {

    private static final String[] HEADER = {"部署ID", "部署名"};

    /** 既定の様式（UTF-8 BOM 付き）の CSV を生成する。改行は呼び出し側の {@code body} に含める。 */
    private static MultipartFile csv(String body) {
        return new MockMultipartFile("csvFile", "x.csv", "text/csv", withBom(body));
    }

    /** UTF-8 BOM を前置したバイト列を返す。 */
    private static byte[] withBom(String body) {
        byte[] bom = {(byte) 0xEF, (byte) 0xBB, (byte) 0xBF};
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        byte[] content = new byte[bom.length + bytes.length];
        System.arraycopy(bom, 0, content, 0, bom.length);
        System.arraycopy(bytes, 0, content, bom.length, bytes.length);
        return content;
    }

    // ---- ファイル形式チェック（容量①→様式②→件数③ の順） ----

    @Test
    void parse_throwsTooLarge_whenOverMaxSize() {
        // 5MB 超過を容量チェック（①）で検出する。実バイト列を確保せず getSize のみをスタブする。
        MultipartFile file = mock(MultipartFile.class);
        when(file.getSize()).thenReturn(5L * 1024 * 1024 + 1);

        CsvFormatException ex =
                assertThrows(CsvFormatException.class, () -> CsvSupport.parse(file, HEADER));
        assertEquals("ファイルの容量が大きすぎます(5MBまで)。", ex.getMessage());
    }

    @Test
    void parse_throwsNoData_whenEmptyFile() {
        // 空ファイルは件数チェック（③）で MSG_NO_DATA となる（順序変更後も結果は不変）。
        CsvFormatException ex = assertThrows(CsvFormatException.class,
                () -> CsvSupport.parse(new MockMultipartFile("csvFile", new byte[0]), HEADER));
        assertEquals("CSVファイルにデータがありませんでした。", ex.getMessage());
    }

    @Test
    void parse_throwsNoData_whenBomOnly() {
        // BOM のみ（内容が空）のファイルは様式②を満たし、件数チェック（③）で MSG_NO_DATA となる。
        CsvFormatException ex =
                assertThrows(CsvFormatException.class, () -> CsvSupport.parse(csv(""), HEADER));
        assertEquals("CSVファイルにデータがありませんでした。", ex.getMessage());
    }

    @Test
    void parse_throwsUnreadable_whenBomMissing() {
        // 文字コードは UTF-8 BOM ありのみ受け付ける（CSV共通仕様 2.1）。BOM なしは既定の様式ではない。
        MultipartFile file = new MockMultipartFile("csvFile", "x.csv", "text/csv",
                "部署ID,部署名\r\n1,部署あ\r\n".getBytes(StandardCharsets.UTF_8));

        CsvFormatException ex =
                assertThrows(CsvFormatException.class, () -> CsvSupport.parse(file, HEADER));
        assertEquals("CSVの読み取りに失敗しました。フォーマットが正しいかファイルを確認してください。", ex.getMessage());
    }

    @Test
    void parse_stripsBom_andReturnsHeaderAndBodyRows() {
        byte[] bom = {(byte) 0xEF, (byte) 0xBB, (byte) 0xBF};
        byte[] body = "会社ID,会社名\r\n1,会社あ\r\n".getBytes(StandardCharsets.UTF_8);
        byte[] content = new byte[bom.length + body.length];
        System.arraycopy(bom, 0, content, 0, bom.length);
        System.arraycopy(body, 0, content, bom.length, body.length);

        List<String[]> rows =
                CsvSupport.parse(new MockMultipartFile("csvFile", "x.csv", "text/csv", content),
                        new String[] {"会社ID", "会社名"});

        assertEquals(2, rows.size());
        assertEquals("会社ID", rows.get(0)[0]);
        assertEquals("1", rows.get(1)[0]);
        assertEquals("会社あ", rows.get(1)[1]);
    }

    // ---- 読み取りの規則（CSV共通仕様 3.1）と読み取りに失敗する場合（3.2） ----

    @Test
    void parse_keepsLineBreakInsideQuotes_asPartOfValue() {
        // 囲み文字の中の改行は行の終わりではなく値の一部（形式チェックでは弾かない）。
        List<String[]> rows = CsvSupport.parse(csv("部署ID,部署名\r\n1,\"部署\r\nあ\"\r\n"), HEADER);

        assertEquals(2, rows.size());
        assertEquals("部署\r\nあ", rows.get(1)[1]);
    }

    @Test
    void parse_keepsCommaInsideQuotes_andRestoresDoubledQuotes() {
        List<String[]> rows = CsvSupport.parse(csv("部署ID,部署名\r\n1,\"部署,\"\"あ\"\"\"\r\n"), HEADER);

        assertEquals("部署,\"あ\"", rows.get(1)[1]);
    }

    @Test
    void parse_treatsQuoteInsideUnquotedField_asPartOfValue() {
        // 先頭が囲み文字でないフィールドは、次の区切り文字または行の終わりまでをそのまま値とする。
        List<String[]> rows = CsvSupport.parse(csv("部署ID,部署名\r\n1,部\"署\r\n"), HEADER);

        assertEquals("部\"署", rows.get(1)[1]);
    }

    @Test
    void parse_keepsLfInsideQuotes_asPartOfValue() {
        // 囲み文字の中の改行は種類を問わず値の一部（LF 単独でも形式チェックでは弾かない）。
        List<String[]> rows = CsvSupport.parse(csv("部署ID,部署名\r\n1,\"部署\nあ\"\r\n"), HEADER);

        assertEquals(2, rows.size());
        assertEquals("部署\nあ", rows.get(1)[1]);
    }

    @Test
    void parse_acceptsLastLineWithoutLineBreak() {
        // 行の終わりの省略は最終行に限り受け付ける。
        List<String[]> rows = CsvSupport.parse(csv("部署ID,部署名\r\n1,部署あ\r\n2,部署い"), HEADER);

        assertEquals(3, rows.size());
        assertEquals("部署い", rows.get(2)[1]);
    }

    @Test
    void parse_throwsUnreadable_whenLineBreakIsLfOnly() {
        // 改行コードは CR+LF のみ受け付ける（CSV共通仕様 2.1）。囲み文字の外の LF 単独は既定の様式ではない。
        CsvFormatException ex = assertThrows(CsvFormatException.class,
                () -> CsvSupport.parse(csv("部署ID,部署名\n1,部署あ\r\n"), HEADER));
        assertEquals("CSVの読み取りに失敗しました。フォーマットが正しいかファイルを確認してください。", ex.getMessage());
    }

    @Test
    void parse_throwsUnreadable_whenLineBreakIsCrOnly() {
        CsvFormatException ex = assertThrows(CsvFormatException.class,
                () -> CsvSupport.parse(csv("部署ID,部署名\r1,部署あ\r\n"), HEADER));
        assertEquals("CSVの読み取りに失敗しました。フォーマットが正しいかファイルを確認してください。", ex.getMessage());
    }

    @Test
    void parse_throwsUnreadable_whenLastLineEndsWithLfOnly() {
        // 最終行でも、行の終わりを置く場合は CR+LF であること。
        CsvFormatException ex = assertThrows(CsvFormatException.class,
                () -> CsvSupport.parse(csv("部署ID,部署名\r\n1,部署あ\n"), HEADER));
        assertEquals("CSVの読み取りに失敗しました。フォーマットが正しいかファイルを確認してください。", ex.getMessage());
    }

    @Test
    void parse_throwsUnreadable_whenCharactersFollowClosingQuote() {
        // 囲み文字で括られた値の直後に区切り文字・行の終わり以外の文字が続く場合は既定の様式ではない。
        CsvFormatException ex = assertThrows(CsvFormatException.class,
                () -> CsvSupport.parse(csv("部署ID,部署名\r\n1,\"部署\"あ\r\n"), HEADER));
        assertEquals("CSVの読み取りに失敗しました。フォーマットが正しいかファイルを確認してください。", ex.getMessage());
    }

    @Test
    void parse_throwsUnreadable_whenQuoteNotClosed() {
        CsvFormatException ex = assertThrows(CsvFormatException.class,
                () -> CsvSupport.parse(csv("部署ID,部署名\r\n1,\"部署\r\n2,部署い\r\n"), HEADER));
        assertEquals("CSVの読み取りに失敗しました。フォーマットが正しいかファイルを確認してください。", ex.getMessage());
    }

    @Test
    void parse_throwsUnreadable_whenColumnCountDiffers() {
        CsvFormatException ex = assertThrows(CsvFormatException.class,
                () -> CsvSupport.parse(csv("部署ID,部署名\r\n1,部署あ,余分\r\n"), HEADER));
        assertEquals("CSVの読み取りに失敗しました。フォーマットが正しいかファイルを確認してください。", ex.getMessage());
    }

    // ---- 列ヘッダー（②既定の様式。1行目が列名・列順ともに一致すること） ----

    @Test
    void parse_throwsUnreadable_whenHeaderNameDiffers() {
        // 列ヘッダーの「ID」は半角。全角「ＩＤ」は既定の様式ではない。
        CsvFormatException ex = assertThrows(CsvFormatException.class,
                () -> CsvSupport.parse(csv("部署ＩＤ,部署名\r\n1,部署あ\r\n"), HEADER));
        assertEquals("CSVの読み取りに失敗しました。フォーマットが正しいかファイルを確認してください。", ex.getMessage());
    }

    @Test
    void parse_throwsUnreadable_whenHeaderOrderDiffers() {
        CsvFormatException ex = assertThrows(CsvFormatException.class,
                () -> CsvSupport.parse(csv("部署名,部署ID\r\n部署あ,1\r\n"), HEADER));
        assertEquals("CSVの読み取りに失敗しました。フォーマットが正しいかファイルを確認してください。", ex.getMessage());
    }

    @Test
    void parse_throwsUnreadable_whenHeaderHasSurroundingSpace() {
        // 列名は完全一致で判定する（前後の空白も差異とみなす）。
        CsvFormatException ex = assertThrows(CsvFormatException.class,
                () -> CsvSupport.parse(csv("部署ID ,部署名\r\n1,部署あ\r\n"), HEADER));
        assertEquals("CSVの読み取りに失敗しました。フォーマットが正しいかファイルを確認してください。", ex.getMessage());
    }

    @Test
    void parse_throwsUnreadable_whenHeaderDiffers_evenIfNoBodyRows() {
        // ②列ヘッダーの検査は③ボディ件数より先に行う（ヘッダーのみのファイルでも列名違いは様式違反）。
        CsvFormatException ex = assertThrows(CsvFormatException.class,
                () -> CsvSupport.parse(csv("部署名,部署ID\r\n"), HEADER));
        assertEquals("CSVの読み取りに失敗しました。フォーマットが正しいかファイルを確認してください。", ex.getMessage());
    }

    @Test
    void parse_acceptsQuotedHeader() {
        // 列ヘッダーの一致は、読み取り規則（CSV共通仕様 3.1）で復元した値で判定する。
        List<String[]> rows = CsvSupport.parse(csv("\"部署ID\",\"部署名\"\r\n1,部署あ\r\n"), HEADER);

        assertEquals(2, rows.size());
        assertEquals("部署ID", rows.get(0)[0]);
    }

    // ---- エラー整形（CSV行番号は4桁0埋め、5桁以上はそのまま） ----

    @Test
    void formatError_padsCsvLineNumberToFourDigits() {
        assertEquals("0002 - データ1行目 会社ID: この項目は入力が必要です。",
                CsvSupport.formatError(2, 1, "会社ID", "この項目は入力が必要です。"));
    }

    @Test
    void formatError_keepsDigits_whenFiveOrMore() {
        assertEquals("10000 - データ9999行目 会社ID: エラー",
                CsvSupport.formatError(10000, 9999, "会社ID", "エラー"));
    }

    // ---- 全角のみ判定 ----

    @Test
    void isFullWidthOnly_trueForFullWidth_falseForHalfWidth() {
        assertTrue(CsvSupport.isFullWidthOnly("会社あ"));
        assertFalse(CsvSupport.isFullWidthOnly("ABC"));
        assertFalse(CsvSupport.isFullWidthOnly("ﾊﾝｶｸ"));
    }
}
