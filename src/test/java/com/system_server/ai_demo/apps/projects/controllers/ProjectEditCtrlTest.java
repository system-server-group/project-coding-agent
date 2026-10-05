package com.system_server.ai_demo.apps.projects.controllers;

import static org.hamcrest.Matchers.containsString;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.system_server.ai_demo.apps.master.reference.services.CompanyReferenceService;
import com.system_server.ai_demo.apps.master.reference.services.DepartmentReferenceService;
import com.system_server.ai_demo.apps.master.reference.services.UserReferenceService;
import com.system_server.ai_demo.apps.projects.models.ProjectUpdateResult;
import com.system_server.ai_demo.apps.projects.services.ProjectUpdateService;
import com.system_server.ai_demo.database.entity.ProjectsEntity;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(ProjectEditCtrl.class)
@WithMockUser(username = "SM1")
class ProjectEditCtrlTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private ProjectUpdateService projectUpdateService;

    @MockitoBean
    private CompanyReferenceService companyReferenceService;

    @MockitoBean
    private UserReferenceService userReferenceService;

    @MockitoBean
    private DepartmentReferenceService departmentReferenceService;

    private static ProjectsEntity project() {
        OffsetDateTime at = OffsetDateTime.of(2026, 6, 10, 12, 0, 0, 0, ZoneOffset.ofHours(9));
        ProjectsEntity entity = new ProjectsEntity();
        entity.setManagementCode(100);
        entity.setTitle("案件件名");
        entity.setStatus("OPEN");
        entity.setStartDate(LocalDate.of(2019, 5, 13));
        entity.setBpRecruitment("NOT_REQUIRED");
        entity.setCreatedBy("登録太郎");
        entity.setCreatedDepartment("登録部署");
        entity.setCreatedAt(at);
        entity.setUpdatedBy("更新太郎");
        entity.setUpdatedDepartment("更新部署");
        entity.setUpdatedAt(at);
        return entity;
    }

    @BeforeEach
    void stub() {
        when(userReferenceService.getUserName(any())).thenReturn("営業太郎");
        when(userReferenceService.getUserDepartmentId(any())).thenReturn(10);
        when(departmentReferenceService.getDepartmentName(10)).thenReturn("SSV営業部");
        when(companyReferenceService.getCompanyNames()).thenReturn(List.of("富士通"));
    }

    @Test
    void showEditForm_rendersProject() throws Exception {
        when(projectUpdateService.getProject(100)).thenReturn(project());
        mockMvc.perform(get("/projects/detail/100")).andExpect(status().isOk())
                .andExpect(content().string(containsString("案件情報更新画面")))
                .andExpect(content().string(containsString("ID: 100")))
                .andExpect(content().string(containsString("value=\"2019-05-13\"")));
    }

    @Test
    void showEditForm_showsNotFound_whenAbsent() throws Exception {
        when(projectUpdateService.getProject(999)).thenReturn(null);
        mockMvc.perform(get("/projects/detail/999")).andExpect(status().isOk())
                .andExpect(content().string(containsString("存在しない案件情報です")));
    }

    @Test
    void showEditForm_returns404_whenManagementCodeNotInteger() throws Exception {
        // 整数に変換できない管理コードはページ未検出エラー（404）とし、JSON 応答は返さない
        mockMvc.perform(get("/projects/detail/abc")).andExpect(status().isNotFound());
    }

    @Test
    void update_reShows_whenSuccess() throws Exception {
        when(projectUpdateService.update(any(), eq("SM1"))).thenReturn(ProjectUpdateResult.SUCCESS);
        when(projectUpdateService.getProject(100)).thenReturn(project());

        mockMvc.perform(multipart("/projects/detail/100").param("subject", "案件件名")
                .param("status", "OPEN").with(csrf())).andExpect(status().isOk())
                .andExpect(content().string(containsString("案件情報更新画面")));
    }

    @Test
    void update_showsConflict_whenConflict() throws Exception {
        when(projectUpdateService.update(any(), eq("SM1")))
                .thenReturn(ProjectUpdateResult.CONFLICT);
        when(projectUpdateService.getProject(100)).thenReturn(project());

        mockMvc.perform(multipart("/projects/detail/100").param("subject", "案件件名")
                .param("status", "OPEN").with(csrf())).andExpect(status().isOk())
                .andExpect(content().string(containsString("案件情報の更新に失敗しました。")));
    }

    @Test
    void update_reShowsWithErrors_whenSubjectBlank() throws Exception {
        when(projectUpdateService.getProject(100)).thenReturn(project());

        mockMvc.perform(multipart("/projects/detail/100").param("subject", "")
                .param("status", "OPEN").with(csrf())).andExpect(status().isOk())
                .andExpect(content().string(containsString("この項目は入力が必要です。")));
    }

    @Test
    void update_reShowsWithErrors_whenStatusBlank() throws Exception {
        // 空値は区分値の確認（サーバ防御・500）ではなく必須入力の入力値チェックとして扱う
        when(projectUpdateService.getProject(100)).thenReturn(project());

        mockMvc.perform(multipart("/projects/detail/100").param("subject", "案件件名")
                .param("status", "").with(csrf())).andExpect(status().isOk())
                .andExpect(content().string(containsString("この項目は入力が必要です。")));
    }
}
