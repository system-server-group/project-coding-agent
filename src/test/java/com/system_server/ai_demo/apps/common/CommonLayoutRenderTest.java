package com.system_server.ai_demo.apps.common;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.system_server.ai_demo.apps.master.reference.services.DepartmentReferenceService;
import com.system_server.ai_demo.apps.master.reference.services.UserReferenceService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.stereotype.Controller;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.bind.annotation.GetMapping;

@WebMvcTest(controllers = CommonLayoutRenderTest.LayoutProbeController.class)
@Import({CommonLayoutRenderTest.LayoutProbeController.class, CommonLayoutMvcConfig.class})
class CommonLayoutRenderTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private UserReferenceService userReferenceService;

    @MockitoBean
    private DepartmentReferenceService departmentReferenceService;

    @BeforeEach
    void stubReferences() {
        when(userReferenceService.getUserName(any())).thenReturn("山田太郎");
        when(userReferenceService.getUserDepartmentId(any())).thenReturn(10);
        when(departmentReferenceService.getDepartmentName(10)).thenReturn("営業部");
    }

    @Test
    @WithMockUser(username = "SM0", roles = "USER")
    void generalUser_seesUserInfoButNoMasterNav() throws Exception {
        mockMvc.perform(get("/test/common-layout")).andExpect(status().isOk())
                .andExpect(content().string(containsString("山田太郎")))
                .andExpect(content().string(containsString("営業部")))
                .andExpect(content().string(containsString("一覧・検索")))
                .andExpect(content().string(not(containsString("会社マスタ"))));
    }

    @Test
    @WithMockUser(username = "SM9", roles = "ADMIN")
    void admin_seesMasterNav() throws Exception {
        mockMvc.perform(get("/test/common-layout")).andExpect(status().isOk())
                .andExpect(content().string(containsString("会社マスタ")))
                .andExpect(content().string(containsString("ユーザマスタ")))
                .andExpect(content().string(containsString("部署マスタ")));
    }

    @Controller
    @CommonLayout
    public static class LayoutProbeController {

        @GetMapping("/test/common-layout")
        public String probe() {
            return "test/layout-probe";
        }
    }
}
