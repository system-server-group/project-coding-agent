package com.system_server.ai_demo.apps.projects.controllers;

import static org.hamcrest.Matchers.containsString;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.system_server.ai_demo.apps.master.reference.services.CompanyReferenceService;
import com.system_server.ai_demo.apps.master.reference.services.DepartmentReferenceService;
import com.system_server.ai_demo.apps.master.reference.services.UserReferenceService;
import com.system_server.ai_demo.apps.projects.models.ProjectRegisterForm;
import com.system_server.ai_demo.apps.projects.services.ProjectRegisterService;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(ProjectRegisterCtrl.class)
@WithMockUser(username = "SM1")
class ProjectRegisterCtrlTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private ProjectRegisterService projectRegisterService;

    @MockitoBean
    private CompanyReferenceService companyReferenceService;

    @MockitoBean
    private UserReferenceService userReferenceService;

    @MockitoBean
    private DepartmentReferenceService departmentReferenceService;

    @BeforeEach
    void stub() {
        when(userReferenceService.getUserName(any())).thenReturn("営業太郎");
        when(userReferenceService.getUserDepartmentId(any())).thenReturn(10);
        when(departmentReferenceService.getDepartmentName(10)).thenReturn("SSV営業部");
        when(companyReferenceService.getCompanyNames()).thenReturn(List.of("富士通"));
    }

    @Test
    void showRegisterForm_rendersViewWithOptions() throws Exception {
        mockMvc.perform(get("/projects/new")).andExpect(status().isOk())
                .andExpect(content().string(containsString("案件情報登録画面")))
                .andExpect(content().string(containsString("オープン")))
                .andExpect(content().string(containsString("富士通")))
                // 登録の送信先は表示URLと同一。未認証で登録画面（GET）を要求した場合に、ログイン後の復帰先
                // として登録画面が再表示されるための前提（パーマリンク システム共通仕様書「未ログイン時の画面遷移の保留」）。
                .andExpect(content().string(containsString("action=\"/projects/new\"")));
    }

    @Test
    void register_redirectsToEdit_whenValid() throws Exception {
        when(projectRegisterService.register(any(), eq("SM1"))).thenReturn(100);

        mockMvc.perform(multipart("/projects/new").param("subject", "案件件名").param("status", "OPEN")
                .with(csrf())).andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/projects/detail/100"));
    }

    @Test
    void register_bindsIsoDate_andTreatsEmptyAsNull() throws Exception {
        when(projectRegisterService.register(any(), eq("SM1"))).thenReturn(100);

        mockMvc.perform(multipart("/projects/new").param("subject", "案件件名").param("status", "OPEN")
                .param("startDate", "2026-06-01").param("endDate", "").with(csrf()))
                .andExpect(status().is3xxRedirection());

        ArgumentCaptor<ProjectRegisterForm> captor =
                ArgumentCaptor.forClass(ProjectRegisterForm.class);
        verify(projectRegisterService).register(captor.capture(), eq("SM1"));
        assertEquals(LocalDate.of(2026, 6, 1), captor.getValue().getStartDate());
        assertNull(captor.getValue().getEndDate());
    }

    @Test
    void register_reRendersWithErrors_whenSubjectBlank() throws Exception {
        mockMvc.perform(multipart("/projects/new").param("subject", "").param("status", "OPEN")
                .with(csrf())).andExpect(status().isOk())
                .andExpect(content().string(containsString("この項目は入力が必要です。")));
    }

    @Test
    void register_reRendersWithErrors_whenStatusBlank() throws Exception {
        // 空値は区分値の確認（サーバ防御・500）ではなく必須入力の入力値チェックとして扱う
        mockMvc.perform(multipart("/projects/new").param("subject", "案件件名").param("status", "")
                .with(csrf())).andExpect(status().isOk())
                .andExpect(content().string(containsString("この項目は入力が必要です。")));
    }

    @Test
    void register_reRendersWithInvalidDateMessage_whenDateNotParsable() throws Exception {
        mockMvc.perform(multipart("/projects/new").param("subject", "案件件名").param("status", "OPEN")
                .param("startDate", "not-a-date").with(csrf())).andExpect(status().isOk())
                .andExpect(content().string(containsString("無効な日付です。")));
    }
}
