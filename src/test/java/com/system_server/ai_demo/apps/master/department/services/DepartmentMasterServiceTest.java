package com.system_server.ai_demo.apps.master.department.services;

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
import com.system_server.ai_demo.database.entity.DepartmentsEntity;
import com.system_server.ai_demo.database.mapper.DepartmentsMapper;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.multipart.MultipartFile;

class DepartmentMasterServiceTest {

    private final DepartmentsMapper departmentsMapper = mock(DepartmentsMapper.class);
    private final DepartmentMasterService service = new DepartmentMasterService(departmentsMapper);

    private static MultipartFile csv(String body) {
        return new MockMultipartFile("csvFile", "x.csv", "text/csv",
                body.getBytes(StandardCharsets.UTF_8));
    }

    private static DepartmentsEntity department(int id, String name) {
        DepartmentsEntity entity = new DepartmentsEntity();
        entity.setDepartmentId(id);
        entity.setDepartmentName(name);
        return entity;
    }

    @Test
    void getAllDepartments_delegatesToMapper() {
        when(departmentsMapper.findAll()).thenReturn(List.of(department(1, "部署あ")));
        assertEquals(1, service.getAllDepartments().size());
    }

    @Test
    void replaceAll_replacesAllRecords_whenValid() {
        byte[] bom = {(byte) 0xEF, (byte) 0xBB, (byte) 0xBF};
        byte[] body = "部署ID,部署名\r\n1,部署あ\r\n2,部署い\r\n".getBytes(StandardCharsets.UTF_8);
        byte[] content = new byte[bom.length + body.length];
        System.arraycopy(bom, 0, content, 0, bom.length);
        System.arraycopy(body, 0, content, bom.length, body.length);
        MultipartFile file = new MockMultipartFile("csvFile", "x.csv", "text/csv", content);

        CsvReplaceResult result = service.replaceAll(file);

        assertTrue(result.success());
        verify(departmentsMapper).deleteAll();
        ArgumentCaptor<DepartmentsEntity> captor = ArgumentCaptor.forClass(DepartmentsEntity.class);
        verify(departmentsMapper, times(2)).insert(captor.capture());
        assertEquals(1, captor.getAllValues().get(0).getDepartmentId());
        assertEquals("部署あ", captor.getAllValues().get(0).getDepartmentName());
        assertEquals(2, captor.getAllValues().get(1).getDepartmentId());
    }

    @Test
    void replaceAll_failsFormat_whenEmptyFile() {
        CsvReplaceResult result = service.replaceAll(new MockMultipartFile("csvFile", new byte[0]));
        assertFalse(result.success());
        assertEquals(CsvFailureType.FORMAT, result.failureType());
        assertEquals(List.of("CSVファイルにデータがありませんでした。"), result.errors());
        verifyNoInteractions(departmentsMapper);
    }

    @Test
    void replaceAll_failsFormat_whenHeaderOnly() {
        CsvReplaceResult result = service.replaceAll(csv("部署ID,部署名\r\n"));
        assertEquals(CsvFailureType.FORMAT, result.failureType());
        assertEquals(List.of("CSVファイルにデータがありませんでした。"), result.errors());
    }

    @Test
    void replaceAll_failsFormat_whenWrongColumnCount() {
        CsvReplaceResult result = service.replaceAll(csv("部署ID,部署名\r\n1,部署,余分\r\n"));
        assertEquals(CsvFailureType.FORMAT, result.failureType());
        assertEquals(List.of("CSVの読み取りに失敗しました。フォーマットが正しいかファイルを確認してください。"), result.errors());
    }

    @Test
    void replaceAll_failsValidation_whenDepartmentIdRequired() {
        CsvReplaceResult result = service.replaceAll(csv("部署ID,部署名\r\n,部署あ\r\n"));
        assertEquals(CsvFailureType.VALIDATION, result.failureType());
        assertEquals(List.of("0002 - データ1行目 部署ID: この項目は入力が必要です。"), result.errors());
        verifyNoInteractions(departmentsMapper);
    }

    @Test
    void replaceAll_failsValidation_whenDepartmentIdNotInteger() {
        CsvReplaceResult result = service.replaceAll(csv("部署ID,部署名\r\nabc,部署あ\r\n"));
        assertEquals(List.of("0002 - データ1行目 部署ID: 整数を入力してください。"), result.errors());
    }

    @Test
    void replaceAll_failsValidation_whenDepartmentIdBelowMin() {
        CsvReplaceResult result = service.replaceAll(csv("部署ID,部署名\r\n0,部署あ\r\n"));
        assertEquals(List.of("0002 - データ1行目 部署ID: 1以上の数値を入力してください。"), result.errors());
    }

    @Test
    void replaceAll_failsValidation_whenDepartmentIdAboveMax() {
        CsvReplaceResult result = service.replaceAll(csv("部署ID,部署名\r\n2147483648,部署あ\r\n"));
        assertEquals(List.of("0002 - データ1行目 部署ID: 2147483647以下の数値を入力してください。"), result.errors());
    }

    @Test
    void replaceAll_failsValidation_whenDepartmentIdDuplicated() {
        CsvReplaceResult result = service.replaceAll(csv("部署ID,部署名\r\n1,部署あ\r\n1,部署い\r\n"));
        assertEquals(List.of("0003 - データ2行目 部署ID: 重複して設定しないでください(1)"), result.errors());
    }

    @Test
    void replaceAll_failsValidation_whenDepartmentNameRequired() {
        CsvReplaceResult result = service.replaceAll(csv("部署ID,部署名\r\n1,\r\n"));
        assertEquals(List.of("0002 - データ1行目 部署名: この項目は入力が必要です。"), result.errors());
    }

    @Test
    void replaceAll_failsValidation_whenDepartmentNameTooLong() {
        String longName = "あ".repeat(51);
        CsvReplaceResult result = service.replaceAll(csv("部署ID,部署名\r\n1," + longName + "\r\n"));
        assertEquals(List.of("0002 - データ1行目 部署名: 50文字以内で入力してください。"), result.errors());
    }

    @Test
    void replaceAll_failsValidation_whenDepartmentNameNotFullWidth() {
        CsvReplaceResult result = service.replaceAll(csv("部署ID,部署名\r\n1,ABC\r\n"));
        assertEquals(List.of("0002 - データ1行目 部署名: 使用不可能な文字が含まれています。"), result.errors());
    }

    @Test
    void replaceAll_reportsErrorsByColumnOrder_whenSameRow() {
        CsvReplaceResult result = service.replaceAll(csv("部署ID,部署名\r\n,ABC\r\n"));
        assertEquals(List.of("0002 - データ1行目 部署ID: この項目は入力が必要です。",
                "0002 - データ1行目 部署名: 使用不可能な文字が含まれています。"), result.errors());
    }

    @Test
    void toCsv_writesBomHeaderAndBody() {
        byte[] csv = service.toCsv(List.of(department(1, "部署あ"), department(2, "部署い")));
        assertEquals((byte) 0xEF, csv[0]);
        assertEquals((byte) 0xBB, csv[1]);
        assertEquals((byte) 0xBF, csv[2]);
        String text = new String(csv, 3, csv.length - 3, StandardCharsets.UTF_8);
        assertEquals("部署ID,部署名\r\n1,部署あ\r\n2,部署い\r\n", text);
    }

    @Test
    void toCsv_writesHeaderOnly_whenNoRecords() {
        byte[] csv = service.toCsv(List.of());
        String text = new String(csv, 3, csv.length - 3, StandardCharsets.UTF_8);
        assertEquals("部署ID,部署名\r\n", text);
    }
}
