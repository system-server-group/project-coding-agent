package com.system_server.ai_demo.apps.projects.controllers;

import static org.hamcrest.Matchers.containsString;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.system_server.ai_demo.apps.master.reference.services.CompanyReferenceService;
import com.system_server.ai_demo.apps.master.reference.services.DepartmentReferenceService;
import com.system_server.ai_demo.apps.master.reference.services.UserReferenceService;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(ProjectListCtrl.class)
@WithMockUser(username = "SM1")
class ProjectListCtrlTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private UserReferenceService userReferenceService;

    @MockitoBean
    private DepartmentReferenceService departmentReferenceService;

    @MockitoBean
    private CompanyReferenceService companyReferenceService;

    @BeforeEach
    void stub() {
        when(userReferenceService.getUserName(any())).thenReturn("営業太郎");
        when(userReferenceService.getUserDepartmentId(any())).thenReturn(10);
        when(departmentReferenceService.getDepartmentName(10)).thenReturn("SSV営業部");
        when(userReferenceService.getUserNames()).thenReturn(List.of("営業太郎", "営業次郎"));
        when(departmentReferenceService.getDepartmentNames()).thenReturn(List.of("SSV営業部"));
        when(companyReferenceService.getCompanyNames()).thenReturn(List.of("富士通"));
    }

    @Test
    void showProjectList_rendersViewWithOptions() throws Exception {
        mockMvc.perform(get("/projects")).andExpect(status().isOk())
                .andExpect(content().string(containsString("案件情報一覧画面")))
                .andExpect(content().string(containsString("案件検索")))
                .andExpect(content().string(containsString("富士通")));
    }
}
