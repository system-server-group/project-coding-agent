package com.system_server.ai_demo.apps.master.company.services;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.system_server.ai_demo.apps.master.common.csv.CsvFailureType;
import com.system_server.ai_demo.apps.master.common.csv.CsvReplaceResult;
import com.system_server.ai_demo.database.entity.CompaniesEntity;
import com.system_server.ai_demo.database.mapper.CompaniesMapper;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.multipart.MultipartFile;

class CompanyMasterServiceTest {

    private final CompaniesMapper companiesMapper = mock(CompaniesMapper.class);
    private final CompanyMasterService service = new CompanyMasterService(companiesMapper);

    private static MultipartFile csv(String body) {
        return new MockMultipartFile("csvFile", "x.csv", "text/csv",
                body.getBytes(StandardCharsets.UTF_8));
    }

    private static CompaniesEntity company(int id, String name) {
        CompaniesEntity entity = new CompaniesEntity();
        entity.setCompanyId(id);
        entity.setCompanyName(name);
        return entity;
    }

    @Test
    void getAllCompanies_delegatesToMapper() {
        when(companiesMapper.findAll()).thenReturn(List.of(company(1, "会社あ")));
        assertEquals(1, service.getAllCompanies().size());
    }

    @Test
    void replaceAll_replacesAllRecords_whenValid() {
        byte[] bom = {(byte) 0xEF, (byte) 0xBB, (byte) 0xBF};
        byte[] body = "会社ID,会社名\r\n1,会社あ\r\n2,会社い\r\n".getBytes(StandardCharsets.UTF_8);
        byte[] content = new byte[bom.length + body.length];
        System.arraycopy(bom, 0, content, 0, bom.length);
        System.arraycopy(body, 0, content, bom.length, body.length);
        MultipartFile file = new MockMultipartFile("csvFile", "x.csv", "text/csv", content);

        CsvReplaceResult result = service.replaceAll(file);

        assertTrue(result.success());
        verify(companiesMapper).deleteAll();
        ArgumentCaptor<CompaniesEntity> captor = ArgumentCaptor.forClass(CompaniesEntity.class);
        verify(companiesMapper, times(2)).insert(captor.capture());
        assertEquals(1, captor.getAllValues().get(0).getCompanyId());
        assertEquals("会社あ", captor.getAllValues().get(0).getCompanyName());
        assertEquals(2, captor.getAllValues().get(1).getCompanyId());
    }

    @Test
    void replaceAll_failsFormat_whenEmptyFile() {
        CsvReplaceResult result = service.replaceAll(new MockMultipartFile("csvFile", new byte[0]));
        assertFalse(result.success());
        assertEquals(CsvFailureType.FORMAT, result.failureType());
        assertEquals(List.of("CSVファイルにデータがありませんでした。"), result.errors());
        verifyNoInteractions(companiesMapper);
    }

    @Test
    void replaceAll_failsFormat_whenHeaderOnly() {
        CsvReplaceResult result = service.replaceAll(csv("会社ID,会社名\r\n"));
        assertEquals(CsvFailureType.FORMAT, result.failureType());
        assertEquals(List.of("CSVファイルにデータがありませんでした。"), result.errors());
    }

    @Test
    void replaceAll_failsFormat_whenWrongColumnCount() {
        CsvReplaceResult result = service.replaceAll(csv("会社ID,会社名\r\n1,会社,余分\r\n"));
        assertEquals(CsvFailureType.FORMAT, result.failureType());
        assertEquals(List.of("CSVの読み取りに失敗しました。フォーマットが正しいかファイルを確認してください。"), result.errors());
    }

    @Test
    void replaceAll_failsValidation_whenCompanyIdRequired() {
        CsvReplaceResult result = service.replaceAll(csv("会社ID,会社名\r\n,会社あ\r\n"));
        assertEquals(CsvFailureType.VALIDATION, result.failureType());
        assertEquals(List.of("0002 - データ1行目 会社ID: この項目は入力が必要です。"), result.errors());
        verifyNoInteractions(companiesMapper);
    }

    @Test
    void replaceAll_failsValidation_whenCompanyIdNotInteger() {
        CsvReplaceResult result = service.replaceAll(csv("会社ID,会社名\r\nabc,会社あ\r\n"));
        assertEquals(List.of("0002 - データ1行目 会社ID: 整数を入力してください。"), result.errors());
    }

    @Test
    void replaceAll_failsValidation_whenCompanyIdBelowMin() {
        CsvReplaceResult result = service.replaceAll(csv("会社ID,会社名\r\n0,会社あ\r\n"));
        assertEquals(List.of("0002 - データ1行目 会社ID: 1以上の数値を入力してください。"), result.errors());
    }

    @Test
    void replaceAll_failsValidation_whenCompanyIdAboveMax() {
        CsvReplaceResult result = service.replaceAll(csv("会社ID,会社名\r\n2147483648,会社あ\r\n"));
        assertEquals(List.of("0002 - データ1行目 会社ID: 2147483647以下の数値を入力してください。"), result.errors());
    }

    @Test
    void replaceAll_failsValidation_whenCompanyIdDuplicated() {
        CsvReplaceResult result = service.replaceAll(csv("会社ID,会社名\r\n1,会社あ\r\n1,会社い\r\n"));
        assertEquals(List.of("0003 - データ2行目 会社ID: 重複して設定しないでください(1)"), result.errors());
    }

    @Test
    void replaceAll_failsValidation_whenCompanyNameRequired() {
        CsvReplaceResult result = service.replaceAll(csv("会社ID,会社名\r\n1,\r\n"));
        assertEquals(List.of("0002 - データ1行目 会社名: この項目は入力が必要です。"), result.errors());
    }

    @Test
    void replaceAll_failsValidation_whenCompanyNameTooLong() {
        String longName = "あ".repeat(51);
        CsvReplaceResult result = service.replaceAll(csv("会社ID,会社名\r\n1," + longName + "\r\n"));
        assertEquals(List.of("0002 - データ1行目 会社名: 50文字以内で入力してください。"), result.errors());
    }

    @Test
    void replaceAll_failsValidation_whenCompanyNameNotFullWidth() {
        CsvReplaceResult result = service.replaceAll(csv("会社ID,会社名\r\n1,ABC\r\n"));
        assertEquals(List.of("0002 - データ1行目 会社名: 使用不可能な文字が含まれています。"), result.errors());
    }

    @Test
    void replaceAll_reportsErrorsByColumnOrder_whenSameRow() {
        CsvReplaceResult result = service.replaceAll(csv("会社ID,会社名\r\n,ABC\r\n"));
        assertEquals(List.of("0002 - データ1行目 会社ID: この項目は入力が必要です。",
                "0002 - データ1行目 会社名: 使用不可能な文字が含まれています。"), result.errors());
    }

    @Test
    void toCsv_writesBomHeaderAndBody() {
        byte[] csv = service.toCsv(List.of(company(1, "会社あ"), company(2, "会社い")));
        assertEquals((byte) 0xEF, csv[0]);
        assertEquals((byte) 0xBB, csv[1]);
        assertEquals((byte) 0xBF, csv[2]);
        String text = new String(csv, 3, csv.length - 3, StandardCharsets.UTF_8);
        assertEquals("会社ID,会社名\r\n1,会社あ\r\n2,会社い\r\n", text);
    }

    @Test
    void toCsv_writesHeaderOnly_whenNoRecords() {
        byte[] csv = service.toCsv(List.of());
        String text = new String(csv, 3, csv.length - 3, StandardCharsets.UTF_8);
        assertEquals("会社ID,会社名\r\n", text);
    }
}
