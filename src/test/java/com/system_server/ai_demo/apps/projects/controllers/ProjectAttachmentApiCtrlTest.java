package com.system_server.ai_demo.apps.projects.controllers;

import static org.hamcrest.Matchers.containsString;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.system_server.ai_demo.apps.projects.services.ProjectUpdateService;
import com.system_server.ai_demo.database.entity.ProjectAttachmentsEntity;
import com.system_server.ai_demo.database.entity.ProjectAttachmentsSummaryEntity;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.HttpHeaders;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(ProjectAttachmentApiCtrl.class)
@WithMockUser
class ProjectAttachmentApiCtrlTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private ProjectUpdateService projectUpdateService;

    @Test
    void listAttachments_returnsItemsWithoutFileData() throws Exception {
        ProjectAttachmentsSummaryEntity entity = new ProjectAttachmentsSummaryEntity();
        entity.setFileId(3);
        entity.setManagementCode(1);
        entity.setFileName("設計書.txt");
        entity.setFileSize(18);
        entity.setCreatedBy("営業 太郎");
        entity.setCreatedDepartment("営業部");
        entity.setCreatedAt(OffsetDateTime.of(2026, 1, 5, 9, 30, 0, 0, ZoneOffset.ofHours(9)));
        when(projectUpdateService.getAttachments(1)).thenReturn(List.of(entity));

        mockMvc.perform(get("/api/attachments").param("managementCode", "1"))
                .andExpect(status().isOk()).andExpect(jsonPath("$[0].fileId").value(3))
                .andExpect(jsonPath("$[0].fileName").value("設計書.txt"))
                .andExpect(jsonPath("$[0].fileSize").value(18))
                .andExpect(jsonPath("$[0].createdBy").value("営業 太郎"))
                .andExpect(jsonPath("$[0].createdDepartment").value("営業部"))
                .andExpect(jsonPath("$[0].createdAt", containsString("2026-01-05T09:30:00")))
                // 実ファイル（バイナリ）は一覧に含めない。
                .andExpect(jsonPath("$[0].fileData").doesNotExist());
    }

    @Test
    void listAttachments_returnsEmpty_whenNoAttachments() throws Exception {
        when(projectUpdateService.getAttachments(1)).thenReturn(List.of());
        mockMvc.perform(get("/api/attachments").param("managementCode", "1"))
                .andExpect(status().isOk()).andExpect(jsonPath("$").isEmpty());
    }

    @Test
    void listAttachments_returns400_whenManagementCodeMissing() throws Exception {
        mockMvc.perform(get("/api/attachments")).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("managementCode"))
                .andExpect(jsonPath("$.errors[0].message").value("この項目は入力が必要です。"));
    }

    @Test
    void listAttachments_returns400_whenManagementCodeNotInteger() throws Exception {
        mockMvc.perform(get("/api/attachments").param("managementCode", "abc"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("managementCode"))
                .andExpect(jsonPath("$.errors[0].message").value("整数を入力してください。"));
    }

    @Test
    void downloadAttachment_returnsFile() throws Exception {
        ProjectAttachmentsEntity entity = new ProjectAttachmentsEntity();
        entity.setFileName("spec.txt");
        entity.setFileData("hello".getBytes());
        when(projectUpdateService.getAttachment(5)).thenReturn(entity);

        mockMvc.perform(get("/api/attachments/5")).andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.CONTENT_DISPOSITION,
                        containsString("attachment")))
                .andExpect(content().bytes("hello".getBytes()));
    }

    @Test
    void downloadAttachment_returns404_whenAbsent() throws Exception {
        when(projectUpdateService.getAttachment(9)).thenReturn(null);
        mockMvc.perform(get("/api/attachments/9")).andExpect(status().isNotFound());
    }

    @Test
    void deleteAttachment_returnsDeletedCount() throws Exception {
        when(projectUpdateService.deleteAttachment(5)).thenReturn(1);
        mockMvc.perform(delete("/api/attachments/5").with(csrf())).andExpect(status().isOk())
                .andExpect(jsonPath("$.result").value(1));
    }
}
