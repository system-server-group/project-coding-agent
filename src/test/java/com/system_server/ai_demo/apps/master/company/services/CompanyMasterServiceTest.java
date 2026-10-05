package com.system_server.ai_demo.apps.master.company.services;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import com.system_server.ai_demo.apps.master.common.csv.CsvFailureType;
import com.system_server.ai_demo.apps.master.common.csv.CsvReplaceResult;
import com.system_server.ai_demo.database.entity.CompaniesEntity;
import com.system_server.ai_demo.database.mapper.CompaniesMapper;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.multipart.MultipartFile;

class CompanyMasterServiceTest {

    private final CompaniesMapper companiesMapper = mock(CompaniesMapper.class);
    private final CompanyMasterService service = new CompanyMasterService(companiesMapper);

    /** ファイル容量の上限（5MB）。 */
    private static final int MAX_FILE_SIZE = 5 * 1024 * 1024;

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

    /**
     * 全体が指定バイト数ちょうどになる CSV を生成する。
     *
     * <p>
     * 構成は UTF-8 BOM + ヘッダー + CRLF + 「1,」 + 半角 a の繰り返し + CRLF とする。
     */
    private static MultipartFile csvOfSize(int totalBytes) {
        byte[] bom = {(byte) 0xEF, (byte) 0xBB, (byte) 0xBF};
        byte[] prefix = "会社ID,会社名\r\n1,".getBytes(StandardCharsets.UTF_8);
        byte[] suffix = "\r\n".getBytes(StandardCharsets.UTF_8);
        byte[] content = new byte[totalBytes];
        System.arraycopy(bom, 0, content, 0, bom.length);
        System.arraycopy(prefix, 0, content, bom.length, prefix.length);
        Arrays.fill(content, bom.length + prefix.length, totalBytes - suffix.length, (byte) 'a');
        System.arraycopy(suffix, 0, content, totalBytes - suffix.length, suffix.length);
        return new MockMultipartFile("csvFile", "x.csv", "text/csv", content);
    }

    private static CompaniesEntity company(int id, String name) {
        CompaniesEntity entity = new CompaniesEntity();
        entity.setCompanyId(id);
        entity.setCompanyName(name);
        return entity;
    }

    private static CsvReplaceResult formatFailure(String message) {
        return CsvReplaceResult.ofFailure(CsvFailureType.FORMAT, List.of(message));
    }

    private static CsvReplaceResult validationFailure(String... messages) {
        return CsvReplaceResult.ofFailure(CsvFailureType.VALIDATION, List.of(messages));
    }

    /** 先頭の UTF-8 BOM を検証し、BOM を除いた CSV の内容を返す。 */
    private static String decodeWithBom(byte[] csv) {
        assertEquals((byte) 0xEF, csv[0]);
        assertEquals((byte) 0xBB, csv[1]);
        assertEquals((byte) 0xBF, csv[2]);
        return new String(csv, 3, csv.length - 3, StandardCharsets.UTF_8);
    }

    @Test
    void getAllCompanies_returnsRecordsFromMapper() {
        List<CompaniesEntity> companies = List.of(company(1, "エー社"), company(2, "ビー社"));
        when(companiesMapper.findAll()).thenReturn(companies);

        assertEquals(companies, service.getAllCompanies());
    }

    @Test
    void getAllCompanies_returnsEmptyList_whenNoRecords() {
        when(companiesMapper.findAll()).thenReturn(List.of());

        assertTrue(service.getAllCompanies().isEmpty());
    }

    @Test
    void replaceAll_deletesAllThenInsertsInCsvOrder_whenValid() {
        byte[] bom = {(byte) 0xEF, (byte) 0xBB, (byte) 0xBF};
        byte[] body = "会社ID,会社名\r\n2,ビー社\r\n1,エー社\r\n".getBytes(StandardCharsets.UTF_8);
        byte[] content = new byte[bom.length + body.length];
        System.arraycopy(bom, 0, content, 0, bom.length);
        System.arraycopy(body, 0, content, bom.length, body.length);
        MultipartFile file = new MockMultipartFile("csvFile", "x.csv", "text/csv", content);

        CsvReplaceResult result = service.replaceAll(file);

        assertEquals(CsvReplaceResult.ofSuccess(), result);
        InOrder order = inOrder(companiesMapper);
        order.verify(companiesMapper).deleteAll();
        ArgumentCaptor<CompaniesEntity> captor = ArgumentCaptor.forClass(CompaniesEntity.class);
        order.verify(companiesMapper, times(2)).insert(captor.capture());
        verifyNoMoreInteractions(companiesMapper);
        assertEquals(List.of(company(2, "ビー社"), company(1, "エー社")), captor.getAllValues());
    }

    @Test
    void replaceAll_insertsCompanyIdsAtBounds() {
        CsvReplaceResult result =
                service.replaceAll(csv("会社ID,会社名\r\n1,エー社\r\n2147483647,ビー社\r\n"));

        assertEquals(CsvReplaceResult.ofSuccess(), result);
        ArgumentCaptor<CompaniesEntity> captor = ArgumentCaptor.forClass(CompaniesEntity.class);
        verify(companiesMapper, times(2)).insert(captor.capture());
        assertEquals(List.of(company(1, "エー社"), company(2147483647, "ビー社")), captor.getAllValues());
    }

    @Test
    void replaceAll_insertsCompanyNameOfFiftyCharacters() {
        String name = "あ".repeat(50);

        CsvReplaceResult result = service.replaceAll(csv("会社ID,会社名\r\n1," + name + "\r\n"));

        assertEquals(CsvReplaceResult.ofSuccess(), result);
        verify(companiesMapper).insert(company(1, name));
    }

    @Test
    void replaceAll_failsFormat_whenEmptyFile() {
        CsvReplaceResult result = service.replaceAll(new MockMultipartFile("csvFile", new byte[0]));

        assertEquals(formatFailure("CSVファイルにデータがありませんでした。"), result);
        verifyNoInteractions(companiesMapper);
    }

    @Test
    void replaceAll_failsFormat_whenHeaderOnly() {
        CsvReplaceResult result = service.replaceAll(csv("会社ID,会社名\r\n"));

        assertEquals(formatFailure("CSVファイルにデータがありませんでした。"), result);
        verifyNoInteractions(companiesMapper);
    }

    @Test
    void replaceAll_failsFormat_whenWrongColumnCount() {
        CsvReplaceResult result = service.replaceAll(csv("会社ID,会社名\r\n1,エー社,余分\r\n"));

        assertEquals(formatFailure("CSVの読み取りに失敗しました。フォーマットが正しいかファイルを確認してください。"), result);
        verifyNoInteractions(companiesMapper);
    }

    @Test
    void replaceAll_failsFormat_whenHeaderDiffers() {
        // 列ヘッダーの「ID」は半角。全角「ＩＤ」は既定の様式ではない。
        CsvReplaceResult result = service.replaceAll(csv("会社ＩＤ,会社名\r\n1,エー社\r\n"));

        assertEquals(formatFailure("CSVの読み取りに失敗しました。フォーマットが正しいかファイルを確認してください。"), result);
        verifyNoInteractions(companiesMapper);
    }

    @Test
    void replaceAll_failsFormat_whenHeaderColumnOrderDiffers() {
        CsvReplaceResult result = service.replaceAll(csv("会社名,会社ID\r\nエー社,1\r\n"));

        assertEquals(formatFailure("CSVの読み取りに失敗しました。フォーマットが正しいかファイルを確認してください。"), result);
        verifyNoInteractions(companiesMapper);
    }

    @Test
    void replaceAll_failsFormat_whenCharactersFollowClosingQuote() {
        CsvReplaceResult result = service.replaceAll(csv("会社ID,会社名\r\n1,\"エー社\"ビー\r\n"));

        assertEquals(formatFailure("CSVの読み取りに失敗しました。フォーマットが正しいかファイルを確認してください。"), result);
        verifyNoInteractions(companiesMapper);
    }

    @Test
    void replaceAll_failsFormat_whenQuoteNotClosed() {
        CsvReplaceResult result = service.replaceAll(csv("会社ID,会社名\r\n1,\"エー社\r\n"));

        assertEquals(formatFailure("CSVの読み取りに失敗しました。フォーマットが正しいかファイルを確認してください。"), result);
        verifyNoInteractions(companiesMapper);
    }

    @Test
    void replaceAll_passesSizeCheck_whenExactlyMaxSize() {
        // 5MB ちょうどは容量チェックを通過する。会社名（半角 a の繰り返し）は入力値チェックの最大文字数違反となる。
        MultipartFile file = csvOfSize(MAX_FILE_SIZE);
        assertEquals(MAX_FILE_SIZE, file.getSize());

        CsvReplaceResult result = service.replaceAll(file);

        assertEquals(validationFailure("0002 - データ1行目 会社名: 50文字以内で入力してください。"), result);
        verifyNoInteractions(companiesMapper);
    }

    @Test
    void replaceAll_failsFormat_whenOneByteOverMaxSize() {
        MultipartFile file = csvOfSize(MAX_FILE_SIZE + 1);
        assertEquals(MAX_FILE_SIZE + 1, file.getSize());

        CsvReplaceResult result = service.replaceAll(file);

        assertEquals(formatFailure("ファイルの容量が大きすぎます(5MBまで)。"), result);
        verifyNoInteractions(companiesMapper);
    }

    @Test
    void replaceAll_failsValidation_whenCompanyIdRequired() {
        CsvReplaceResult result = service.replaceAll(csv("会社ID,会社名\r\n,エー社\r\n"));

        assertEquals(validationFailure("0002 - データ1行目 会社ID: この項目は入力が必要です。"), result);
        verifyNoInteractions(companiesMapper);
    }

    @Test
    void replaceAll_failsValidation_whenCompanyIdNotInteger() {
        // 英字・小数・全角数字は整数ではない。
        CsvReplaceResult result =
                service.replaceAll(csv("会社ID,会社名\r\nabc,エー社\r\n1.5,ビー社\r\n１,シー社\r\n"));

        assertEquals(
                validationFailure("0002 - データ1行目 会社ID: 整数を入力してください。",
                        "0003 - データ2行目 会社ID: 整数を入力してください。", "0004 - データ3行目 会社ID: 整数を入力してください。"),
                result);
        verifyNoInteractions(companiesMapper);
    }

    @Test
    void replaceAll_failsValidation_whenCompanyIdBelowMin() {
        CsvReplaceResult result = service.replaceAll(csv("会社ID,会社名\r\n0,エー社\r\n"));

        assertEquals(validationFailure("0002 - データ1行目 会社ID: 1以上の数値を入力してください。"), result);
        verifyNoInteractions(companiesMapper);
    }

    @Test
    void replaceAll_failsValidation_whenCompanyIdAboveMax() {
        CsvReplaceResult result = service.replaceAll(csv("会社ID,会社名\r\n2147483648,エー社\r\n"));

        assertEquals(validationFailure("0002 - データ1行目 会社ID: 2147483647以下の数値を入力してください。"), result);
        verifyNoInteractions(companiesMapper);
    }

    @Test
    void replaceAll_failsValidation_whenCompanyIdDuplicated() {
        // 一意違反は 2 回目以降に同じ会社IDを設定した行のみを対象とする（1 回目の行はエラーにしない）。
        CsvReplaceResult result =
                service.replaceAll(csv("会社ID,会社名\r\n1,エー社\r\n1,ビー社\r\n2,シー社\r\n1,ディー社\r\n"));

        assertEquals(validationFailure("0003 - データ2行目 会社ID: 重複して設定しないでください(1)",
                "0005 - データ4行目 会社ID: 重複して設定しないでください(1)"), result);
        verifyNoInteractions(companiesMapper);
    }

    @Test
    void replaceAll_failsValidation_whenCompanyNameRequired() {
        CsvReplaceResult result = service.replaceAll(csv("会社ID,会社名\r\n1,\r\n"));

        assertEquals(validationFailure("0002 - データ1行目 会社名: この項目は入力が必要です。"), result);
        verifyNoInteractions(companiesMapper);
    }

    @Test
    void replaceAll_failsValidation_whenCompanyNameTooLong() {
        String longName = "あ".repeat(51);

        CsvReplaceResult result = service.replaceAll(csv("会社ID,会社名\r\n1," + longName + "\r\n"));

        assertEquals(validationFailure("0002 - データ1行目 会社名: 50文字以内で入力してください。"), result);
        verifyNoInteractions(companiesMapper);
    }

    @Test
    void replaceAll_failsValidation_whenCompanyNameContainsHalfWidthAlphanumerics() {
        CsvReplaceResult result = service.replaceAll(csv("会社ID,会社名\r\n1,株式会社ABC1\r\n"));

        assertEquals(validationFailure("0002 - データ1行目 会社名: 使用不可能な文字が含まれています。"), result);
        verifyNoInteractions(companiesMapper);
    }

    @Test
    void replaceAll_failsValidation_whenCompanyNameContainsHalfWidthKatakana() {
        CsvReplaceResult result = service.replaceAll(csv("会社ID,会社名\r\n1,ｴｰ社\r\n"));

        assertEquals(validationFailure("0002 - データ1行目 会社名: 使用不可能な文字が含まれています。"), result);
        verifyNoInteractions(companiesMapper);
    }

    @Test
    void replaceAll_failsValidation_whenCompanyNameContainsLineBreak() {
        // 囲み文字内の改行は形式チェックを通過し、入力値チェックの使用文字違反となる。
        CsvReplaceResult result = service.replaceAll(csv("会社ID,会社名\r\n1,\"エー\r\n社\"\r\n"));

        assertEquals(validationFailure("0002 - データ1行目 会社名: 使用不可能な文字が含まれています。"), result);
        verifyNoInteractions(companiesMapper);
    }

    @Test
    void replaceAll_reportsOnlyFirstViolation_whenSameColumnHasMultipleViolations() {
        // 半角 51 文字は最大文字数と使用文字の両方に違反するが、番号の小さい最大文字数のみを対象とする。
        String longName = "a".repeat(51);

        CsvReplaceResult result = service.replaceAll(csv("会社ID,会社名\r\n1," + longName + "\r\n"));

        assertEquals(validationFailure("0002 - データ1行目 会社名: 50文字以内で入力してください。"), result);
        verifyNoInteractions(companiesMapper);
    }

    @Test
    void replaceAll_reportsErrorsByColumnOrder_whenSameRow() {
        CsvReplaceResult result = service.replaceAll(csv("会社ID,会社名\r\n,ABC\r\n"));

        assertEquals(validationFailure("0002 - データ1行目 会社ID: この項目は入力が必要です。",
                "0002 - データ1行目 会社名: 使用不可能な文字が含まれています。"), result);
        verifyNoInteractions(companiesMapper);
    }

    @Test
    void replaceAll_reportsErrorsOfAllRows() {
        CsvReplaceResult result =
                service.replaceAll(csv("会社ID,会社名\r\n0,エー社\r\n2,ビー社\r\n3,ABC\r\n"));

        assertEquals(validationFailure("0002 - データ1行目 会社ID: 1以上の数値を入力してください。",
                "0004 - データ3行目 会社名: 使用不可能な文字が含まれています。"), result);
        verifyNoInteractions(companiesMapper);
    }

    @Test
    void replaceAll_formatsCsvLineNumber_withoutPadding_whenFiveDigitsOrMore() {
        // CSV 行番号は 4 桁で 0 埋めし、5 桁以上はそのまま表示する。
        // データ 9998 行目は CSV 9999 行目、データ 9999 行目は CSV 10000 行目となる。
        StringBuilder body = new StringBuilder("会社ID,会社名\r\n");
        for (int id = 1; id <= 9997; id++) {
            body.append(id).append(",会社\r\n");
        }
        body.append("9998,\r\n").append("9999,\r\n");

        CsvReplaceResult result = service.replaceAll(csv(body.toString()));

        assertEquals(validationFailure("9999 - データ9998行目 会社名: この項目は入力が必要です。",
                "10000 - データ9999行目 会社名: この項目は入力が必要です。"), result);
        verifyNoInteractions(companiesMapper);
    }

    @Test
    void replaceAll_propagatesException_andStopsInserting_whenInsertFails() {
        // 2 件目の登録で DB 例外が発生した場合、結果を返さず例外をそのまま送出し、後続行の登録は行わない
        // （送出された例外により @Transactional がロールバックし、呼び出し側でサーバー内部エラーとなる）。
        DataAccessException failure = new DataAccessResourceFailureException("insert failed");
        when(companiesMapper.insert(any())).thenReturn(1).thenThrow(failure);

        DataAccessException thrown = assertThrows(DataAccessException.class,
                () -> service.replaceAll(csv("会社ID,会社名\r\n1,エー社\r\n2,ビー社\r\n3,シー社\r\n")));

        assertSame(failure, thrown);
        verify(companiesMapper).deleteAll();
        verify(companiesMapper, times(2)).insert(any());
    }

    @Test
    void replaceAll_propagatesException_andSkipsInsert_whenDeleteAllFails() {
        DataAccessException failure = new DataAccessResourceFailureException("delete failed");
        when(companiesMapper.deleteAll()).thenThrow(failure);

        DataAccessException thrown = assertThrows(DataAccessException.class,
                () -> service.replaceAll(csv("会社ID,会社名\r\n1,エー社\r\n")));

        assertSame(failure, thrown);
        verify(companiesMapper, never()).insert(any());
    }

    @Test
    void toCsv_writesBomHeaderAndBody_inInputOrder() {
        byte[] csv = service.toCsv(List.of(company(2, "ビー社"), company(1, "エー社")));

        assertEquals("会社ID,会社名\r\n2,ビー社\r\n1,エー社\r\n", decodeWithBom(csv));
    }

    @Test
    void toCsv_writesHeaderOnly_whenNoRecords() {
        byte[] csv = service.toCsv(List.of());

        assertEquals("会社ID,会社名\r\n", decodeWithBom(csv));
    }

    @Test
    void toCsv_quotesValues_whenContainingCommaQuoteOrLineBreak() {
        byte[] csv = service
                .toCsv(List.of(company(1, "エー,社"), company(2, "ビー\"社\""), company(3, "シー\r\n社")));

        assertEquals("会社ID,会社名\r\n1,\"エー,社\"\r\n2,\"ビー\"\"社\"\"\"\r\n3,\"シー\r\n社\"\r\n",
                decodeWithBom(csv));
    }
}
