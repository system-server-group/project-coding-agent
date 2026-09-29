package com.system_server.ai_demo.apps.projects.controllers;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.system_server.ai_demo.apps.projects.models.ProjectListDto;
import com.system_server.ai_demo.apps.projects.services.ProjectSearchService;
import com.system_server.ai_demo.enums.Status;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(ProjectSearchApiCtrl.class)
@WithMockUser
class ProjectSearchApiCtrlTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private ProjectSearchService projectSearchService;

    private static ProjectListDto dto() {
        return new ProjectListDto(5, "案件", Status.OPEN, null, null, null, null, null, null, null,
                null, null, null, null, null, null, null, null, null, null, null, null, null, null);
    }

    @Test
    void search_returns200WithProjects_whenValid() throws Exception {
        when(projectSearchService.search(any())).thenReturn(List.of(dto()));

        mockMvc.perform(
                post("/api/projects/search").with(csrf()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"keyword\":\"Java\",\"keywordTargets\":[\"title\"],"
                                + "\"statuses\":[\"OPEN\"],\"bpOnly\":false}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.projects[0].managementCode").value(5))
                .andExpect(jsonPath("$.projects[0].status.code").value("OPEN"))
                .andExpect(jsonPath("$.projects[0].status.label").value("オープン"));
    }

    @Test
    void search_returns400_whenKeywordTooLong() throws Exception {
        String keyword = "a".repeat(101);
        mockMvc.perform(
                post("/api/projects/search").with(csrf()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"keyword\":\"" + keyword + "\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("keyword"));
    }

    @Test
    void search_returns400_whenDateOutOfBounds() throws Exception {
        mockMvc.perform(
                post("/api/projects/search").with(csrf()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"startDateFrom\":\"1899-12-31\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("startDateFrom"));
    }

    @Test
    void search_returns400_whenDateRangeViolated() throws Exception {
        mockMvc.perform(post("/api/projects/search").with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"startDateFrom\":\"2026-06-10\",\"startDateTo\":\"2026-06-01\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("startDateTo"));
    }

    @Test
    void search_returns400WithDateFormatError_whenDateUnparseable() throws Exception {
        // 日付項目の形式不正はボディ変換（LocalDate パース）で失敗する。「日付形式」チェックの違反として
        // 項目名と「無効な日付です。」を返す（入力値チェック システム共通仕様書「日付形式」）。
        mockMvc.perform(
                post("/api/projects/search").with(csrf()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"startDateFrom\":\"2026/13/99\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("startDateFrom"))
                .andExpect(jsonPath("$.errors[0].message").value("無効な日付です。"));
    }

    @Test
    void search_returns400WithEmptyErrors_whenBodyMalformed() throws Exception {
        // 項目に紐づけられない変換失敗（不正な JSON）は空の違反一覧の 400 とする。
        mockMvc.perform(post("/api/projects/search").with(csrf())
                .contentType(MediaType.APPLICATION_JSON).content("{not-json"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.errors").isEmpty());
    }

    @Test
    void search_returns400_whenRegistrantTooLong() throws Exception {
        String registrant = "a".repeat(51);
        mockMvc.perform(
                post("/api/projects/search").with(csrf()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"registrant\":\"" + registrant + "\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("registrant"));
    }
}
