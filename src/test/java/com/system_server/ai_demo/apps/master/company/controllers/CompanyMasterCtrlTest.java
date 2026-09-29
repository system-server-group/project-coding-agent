package com.system_server.ai_demo.apps.master.company.controllers;

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
import com.system_server.ai_demo.apps.master.company.services.CompanyMasterService;
import com.system_server.ai_demo.apps.master.reference.services.DepartmentReferenceService;
import com.system_server.ai_demo.apps.master.reference.services.UserReferenceService;
import com.system_server.ai_demo.config.SecurityConfig;
import com.system_server.ai_demo.database.entity.CompaniesEntity;
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

@WebMvcTest(CompanyMasterCtrl.class)
@Import(SecurityConfig.class)
@WithMockUser(username = "SM9", roles = "ADMIN")
class CompanyMasterCtrlTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private CompanyMasterService companyMasterService;

    @MockitoBean
    private UserReferenceService userReferenceService;

    @MockitoBean
    private DepartmentReferenceService departmentReferenceService;

    private static CompaniesEntity company(int id, String name) {
        CompaniesEntity entity = new CompaniesEntity();
        entity.setCompanyId(id);
        entity.setCompanyName(name);
        return entity;
    }

    @BeforeEach
    void stubHeader() {
        when(userReferenceService.getUserName(any())).thenReturn("管理者");
        when(userReferenceService.getUserDepartmentId(any())).thenReturn(10);
        when(departmentReferenceService.getDepartmentName(10)).thenReturn("情報システム部");
    }

    @Test
    void showCompanies_returnsListView() throws Exception {
        when(companyMasterService.getAllCompanies()).thenReturn(List.of(company(1, "会社あ")));
        mockMvc.perform(get("/master/companies")).andExpect(status().isOk())
                .andExpect(content().string(containsString("会社マスタ管理画面")));
    }

    @Test
    void downloadCsv_returnsCsvAttachment() throws Exception {
        when(companyMasterService.getAllCompanies()).thenReturn(List.of(company(1, "会社あ")));
        byte[] body = {(byte) 0xEF, (byte) 0xBB, (byte) 0xBF, 'I', 'D'};
        when(companyMasterService.toCsv(any())).thenReturn(body);

        mockMvc.perform(get("/master/companies/csv")).andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.CONTENT_TYPE, containsString("text/csv")))
                .andExpect(header().string(HttpHeaders.CONTENT_DISPOSITION,
                        containsString("attachment")))
                .andExpect(content().bytes(body));
    }

    @Test
    void uploadCsv_reShowsList_whenSuccess() throws Exception {
        when(companyMasterService.replaceAll(any())).thenReturn(CsvReplaceResult.ofSuccess());
        when(companyMasterService.getAllCompanies()).thenReturn(List.of(company(1, "会社あ")));

        mockMvc.perform(multipart("/master/companies/csv")
                .file("csvFile", "会社ID,会社名\r\n1,会社あ\r\n".getBytes()).with(csrf()))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("会社マスタ管理画面")));
    }

    @Test
    void uploadCsv_showsValidationErrors_whenValidationFailure() throws Exception {
        when(companyMasterService.replaceAll(any())).thenReturn(CsvReplaceResult
                .ofFailure(CsvFailureType.VALIDATION, List.of("0002 - データ1行目 会社ID: エラー")));
        when(companyMasterService.getAllCompanies()).thenReturn(List.of());

        mockMvc.perform(
                multipart("/master/companies/csv").file("csvFile", "bad".getBytes()).with(csrf()))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("CSVの内容にエラーがあります。詳細は以下の通りです。")))
                .andExpect(content().string(containsString("0002 - データ1行目 会社ID: エラー")));
    }

    @SuppressWarnings("unchecked")
    @Test
    void downloadCsv_appliesSortAndOrder() throws Exception {
        when(companyMasterService.getAllCompanies())
                .thenReturn(List.of(company(1, "会社あ"), company(2, "会社い")));
        when(companyMasterService.toCsv(any())).thenReturn(new byte[0]);

        mockMvc.perform(
                get("/master/companies/csv").param("sort", "companyId").param("order", "desc"))
                .andExpect(status().isOk());

        ArgumentCaptor<List<CompaniesEntity>> captor = ArgumentCaptor.forClass(List.class);
        verify(companyMasterService).toCsv(captor.capture());
        assertEquals(2, captor.getValue().get(0).getCompanyId());
        assertEquals(1, captor.getValue().get(1).getCompanyId());
    }

    @Test
    void uploadCsv_showsFormatTitle_whenFormatFailure() throws Exception {
        when(companyMasterService.replaceAll(any()))
                .thenReturn(CsvReplaceResult.ofFailure(CsvFailureType.FORMAT, List.of("形式エラー")));
        when(companyMasterService.getAllCompanies()).thenReturn(List.of());

        mockMvc.perform(
                multipart("/master/companies/csv").file("csvFile", "bad".getBytes()).with(csrf()))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("CSVアップロードに失敗しました")));
    }

    @Test
    void uploadCsv_showsRequiredError_whenFileMissing() throws Exception {
        when(companyMasterService.getAllCompanies()).thenReturn(List.of());

        mockMvc.perform(multipart("/master/companies/csv").with(csrf())).andExpect(status().isOk())
                .andExpect(content().string(containsString("CSVアップロードに失敗しました")))
                .andExpect(content().string(containsString("この項目は入力が必要です。")));
    }

    @Test
    @WithMockUser(username = "U1", roles = "USER")
    void showCompanies_isForbidden_whenNotAdmin() throws Exception {
        mockMvc.perform(get("/master/companies")).andExpect(status().isForbidden());
    }
}
