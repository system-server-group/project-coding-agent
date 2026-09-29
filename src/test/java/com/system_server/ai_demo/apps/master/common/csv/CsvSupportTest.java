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

    // ---- ファイル形式チェック（容量①→様式②→件数③ の順） ----

    @Test
    void parse_throwsTooLarge_whenOverMaxSize() {
        // 5MB 超過を容量チェック（①）で検出する。実バイト列を確保せず getSize のみをスタブする。
        MultipartFile file = mock(MultipartFile.class);
        when(file.getSize()).thenReturn(5L * 1024 * 1024 + 1);

        CsvFormatException ex =
                assertThrows(CsvFormatException.class, () -> CsvSupport.parse(file, 2));
        assertEquals("ファイルの容量が大きすぎます(5MBまで)。", ex.getMessage());
    }

    @Test
    void parse_throwsNoData_whenEmptyFile() {
        // 空ファイルは件数チェック（③）で MSG_NO_DATA となる（順序変更後も結果は不変）。
        CsvFormatException ex = assertThrows(CsvFormatException.class,
                () -> CsvSupport.parse(new MockMultipartFile("csvFile", new byte[0]), 2));
        assertEquals("CSVファイルにデータがありませんでした。", ex.getMessage());
    }

    @Test
    void parse_stripsBom_andReturnsHeaderAndBodyRows() {
        byte[] bom = {(byte) 0xEF, (byte) 0xBB, (byte) 0xBF};
        byte[] body = "会社ID,会社名\r\n1,会社あ\r\n".getBytes(StandardCharsets.UTF_8);
        byte[] content = new byte[bom.length + body.length];
        System.arraycopy(bom, 0, content, 0, bom.length);
        System.arraycopy(body, 0, content, bom.length, body.length);

        List<String[]> rows =
                CsvSupport.parse(new MockMultipartFile("csvFile", "x.csv", "text/csv", content), 2);

        assertEquals(2, rows.size());
        assertEquals("会社ID", rows.get(0)[0]);
        assertEquals("1", rows.get(1)[0]);
        assertEquals("会社あ", rows.get(1)[1]);
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
