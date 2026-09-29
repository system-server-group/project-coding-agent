package com.system_server.ai_demo.apps.master.department.controllers;

import static org.hamcrest.Matchers.containsString;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.system_server.ai_demo.apps.master.common.csv.CsvFailureType;
import com.system_server.ai_demo.apps.master.common.csv.CsvReplaceResult;
import com.system_server.ai_demo.apps.master.department.services.DepartmentMasterService;
import com.system_server.ai_demo.apps.master.reference.services.DepartmentReferenceService;
import com.system_server.ai_demo.apps.master.reference.services.UserReferenceService;
import com.system_server.ai_demo.config.SecurityConfig;
import com.system_server.ai_demo.database.entity.DepartmentsEntity;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(DepartmentMasterCtrl.class)
@Import(SecurityConfig.class)
@WithMockUser(username = "SM9", roles = "ADMIN")
class DepartmentMasterCtrlTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private DepartmentMasterService departmentMasterService;

    @MockitoBean
    private UserReferenceService userReferenceService;

    @MockitoBean
    private DepartmentReferenceService departmentReferenceService;

    private static DepartmentsEntity department(int id, String name) {
        DepartmentsEntity entity = new DepartmentsEntity();
        entity.setDepartmentId(id);
        entity.setDepartmentName(name);
        return entity;
    }

    @BeforeEach
    void stubHeader() {
        when(userReferenceService.getUserName(any())).thenReturn("管理者");
        when(userReferenceService.getUserDepartmentId(any())).thenReturn(10);
        when(departmentReferenceService.getDepartmentName(10)).thenReturn("情報システム部");
    }

    @Test
    void showDepartments_returnsListView() throws Exception {
        when(departmentMasterService.getAllDepartments()).thenReturn(List.of(department(1, "部署あ")));
        mockMvc.perform(get("/master/departments")).andExpect(status().isOk())
                .andExpect(content().string(containsString("部署マスタ管理画面")));
    }

    @Test
    void downloadCsv_returnsCsvAttachment() throws Exception {
        when(departmentMasterService.getAllDepartments()).thenReturn(List.of(department(1, "部署あ")));
        byte[] body = {(byte) 0xEF, (byte) 0xBB, (byte) 0xBF, 'I', 'D'};
        when(departmentMasterService.toCsv(any())).thenReturn(body);

        mockMvc.perform(get("/master/departments/csv")).andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.CONTENT_TYPE, containsString("text/csv")))
                .andExpect(header().string(HttpHeaders.CONTENT_DISPOSITION,
                        containsString("attachment")))
                .andExpect(content().bytes(body));
    }

    @Test
    void uploadCsv_reShowsList_whenSuccess() throws Exception {
        when(departmentMasterService.replaceAll(any())).thenReturn(CsvReplaceResult.ofSuccess());
        when(departmentMasterService.getAllDepartments()).thenReturn(List.of(department(1, "部署あ")));

        mockMvc.perform(multipart("/master/departments/csv")
                .file("csvFile", "部署ID,部署名\r\n1,部署あ\r\n".getBytes()).with(csrf()))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("部署マスタ管理画面")));
    }

    @Test
    void uploadCsv_showsValidationErrors_whenValidationFailure() throws Exception {
        when(departmentMasterService.replaceAll(any())).thenReturn(CsvReplaceResult
                .ofFailure(CsvFailureType.VALIDATION, List.of("0002 - データ1行目 部署ID: エラー")));
        when(departmentMasterService.getAllDepartments()).thenReturn(List.of());

        mockMvc.perform(
                multipart("/master/departments/csv").file("csvFile", "bad".getBytes()).with(csrf()))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("CSVの内容にエラーがあります。詳細は以下の通りです。")))
                .andExpect(content().string(containsString("0002 - データ1行目 部署ID: エラー")));
    }

    @SuppressWarnings("unchecked")
    @Test
    void downloadCsv_appliesSortAndOrder() throws Exception {
        when(departmentMasterService.getAllDepartments())
                .thenReturn(List.of(department(1, "部署あ"), department(2, "部署い")));
        when(departmentMasterService.toCsv(any())).thenReturn(new byte[0]);

        mockMvc.perform(
                get("/master/departments/csv").param("sort", "departmentId").param("order", "desc"))
                .andExpect(status().isOk());

        ArgumentCaptor<List<DepartmentsEntity>> captor = ArgumentCaptor.forClass(List.class);
        verify(departmentMasterService).toCsv(captor.capture());
        assertEquals(2, captor.getValue().get(0).getDepartmentId());
        assertEquals(1, captor.getValue().get(1).getDepartmentId());
    }

    @Test
    void uploadCsv_showsFormatTitle_whenFormatFailure() throws Exception {
        when(departmentMasterService.replaceAll(any()))
                .thenReturn(CsvReplaceResult.ofFailure(CsvFailureType.FORMAT, List.of("形式エラー")));
        when(departmentMasterService.getAllDepartments()).thenReturn(List.of());

        mockMvc.perform(
                multipart("/master/departments/csv").file("csvFile", "bad".getBytes()).with(csrf()))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("CSVアップロードに失敗しました")));
    }

    @Test
    void uploadCsv_showsRequiredError_whenFileMissing() throws Exception {
        when(departmentMasterService.getAllDepartments()).thenReturn(List.of());

        mockMvc.perform(multipart("/master/departments/csv").with(csrf()))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("CSVアップロードに失敗しました")))
                .andExpect(content().string(containsString("この項目は入力が必要です。")));
    }

    @Test
    @WithMockUser(username = "U1", roles = "USER")
    void showDepartments_isForbidden_whenNotAdmin() throws Exception {
        mockMvc.perform(get("/master/departments")).andExpect(status().isForbidden());
    }
}
