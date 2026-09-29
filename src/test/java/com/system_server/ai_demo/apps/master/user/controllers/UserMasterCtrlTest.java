package com.system_server.ai_demo.apps.master.user.controllers;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.system_server.ai_demo.apps.master.reference.services.DepartmentReferenceService;
import com.system_server.ai_demo.apps.master.reference.services.UserReferenceService;
import com.system_server.ai_demo.apps.master.user.models.UserEditResult;
import com.system_server.ai_demo.apps.master.user.services.UserMasterService;
import com.system_server.ai_demo.config.SecurityConfig;
import com.system_server.ai_demo.database.entity.UsersEntity;
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

@WebMvcTest(UserMasterCtrl.class)
@Import(SecurityConfig.class)
@WithMockUser(username = "SM9", roles = "ADMIN")
class UserMasterCtrlTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private UserMasterService userMasterService;

    @MockitoBean
    private UserReferenceService userReferenceService;

    @MockitoBean
    private DepartmentReferenceService departmentReferenceService;

    private static UsersEntity user(String userId) {
        UsersEntity entity = new UsersEntity();
        entity.setUserId(userId);
        entity.setUserName("山田太郎");
        entity.setEmail(userId + "@example.com");
        entity.setDepartmentId(1);
        entity.setRole("0");
        entity.setPassword("h");
        return entity;
    }

    @BeforeEach
    void stubHeader() {
        when(userReferenceService.getUserName(any())).thenReturn("管理者");
        when(userReferenceService.getUserDepartmentId(any())).thenReturn(10);
        when(departmentReferenceService.getDepartmentName(10)).thenReturn("情報システム部");
    }

    @Test
    void showUsers_returnsListView() throws Exception {
        when(userMasterService.getAllUsers()).thenReturn(List.of(user("SM0231")));
        mockMvc.perform(get("/master/users")).andExpect(status().isOk())
                .andExpect(content().string(containsString("ユーザマスタ管理画面")));
    }

    @Test
    void downloadCsv_returnsCsvAttachment() throws Exception {
        when(userMasterService.getAllUsers()).thenReturn(List.of(user("SM0231")));
        byte[] body = {(byte) 0xEF, (byte) 0xBB, (byte) 0xBF, 'I', 'D'};
        when(userMasterService.toCsv(any())).thenReturn(body);

        mockMvc.perform(get("/master/users/csv")).andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.CONTENT_TYPE, containsString("text/csv")))
                .andExpect(header().string(HttpHeaders.CONTENT_DISPOSITION,
                        containsString("attachment")))
                .andExpect(content().bytes(body));
    }

    @Test
    void uploadCsv_reShowsList_whenSuccess() throws Exception {
        when(userMasterService.edit(any(), eq("SM9"))).thenReturn(UserEditResult.ofSuccess());
        when(userMasterService.getAllUsers()).thenReturn(List.of(user("SM0231")));

        mockMvc.perform(multipart("/master/users/csv")
                .file("csvFile", "編集属性,ユーザID\r\n0,SM0231\r\n".getBytes()).with(csrf()))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("ユーザマスタ管理画面")));
    }

    @Test
    void uploadCsv_showsUploadFailedTitle_whenFormatFailure() throws Exception {
        when(userMasterService.edit(any(), eq("SM9")))
                .thenReturn(UserEditResult.ofFormatFailure(List.of("CSVファイルにデータがありませんでした。")));
        when(userMasterService.getAllUsers()).thenReturn(List.of());

        mockMvc.perform(
                multipart("/master/users/csv").file("csvFile", "bad".getBytes()).with(csrf()))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("CSVアップロードに失敗しました")))
                .andExpect(content().string(containsString("CSVファイルにデータがありませんでした。")))
                .andExpect(content().string(not(containsString("詳細は以下の通りです。"))));
    }

    @Test
    void uploadCsv_showsValidationTitle_whenValidationFailure() throws Exception {
        when(userMasterService.edit(any(), eq("SM9"))).thenReturn(
                UserEditResult.ofValidationFailure(List.of("0002 - データ1行目 ユーザID: この項目は入力が必要です。")));
        when(userMasterService.getAllUsers()).thenReturn(List.of());

        mockMvc.perform(
                multipart("/master/users/csv").file("csvFile", "bad".getBytes()).with(csrf()))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("CSVの内容にエラーがあります。詳細は以下の通りです。")))
                .andExpect(content().string(containsString("0002 - データ1行目 ユーザID: この項目は入力が必要です。")));
    }

    @Test
    void uploadCsv_showsBusinessTitle_whenBusinessFailure() throws Exception {
        when(userMasterService.edit(any(), eq("SM9")))
                .thenReturn(UserEditResult.ofBusinessFailure("1行目で異常終了しました。", "ユーザIDU01は既に存在します。"));
        when(userMasterService.getAllUsers()).thenReturn(List.of());

        mockMvc.perform(
                multipart("/master/users/csv").file("csvFile", "bad".getBytes()).with(csrf()))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("1行目で異常終了しました。")))
                .andExpect(content().string(containsString("ユーザIDU01は既に存在します。")));
    }

    @SuppressWarnings("unchecked")
    @Test
    void downloadCsv_appliesSortOrder_andSetsFileName() throws Exception {
        when(userMasterService.getAllUsers()).thenReturn(List.of(user("SM0100"), user("SM0231")));
        when(userMasterService.toCsv(any())).thenReturn(new byte[0]);

        mockMvc.perform(get("/master/users/csv").param("sort", "userId").param("order", "desc"))
                .andExpect(status().isOk()).andExpect(
                        header().string(HttpHeaders.CONTENT_DISPOSITION, containsString(".csv")));

        ArgumentCaptor<List<UsersEntity>> captor = ArgumentCaptor.forClass(List.class);
        verify(userMasterService).toCsv(captor.capture());
        assertEquals("SM0231", captor.getValue().get(0).getUserId());
        assertEquals("SM0100", captor.getValue().get(1).getUserId());
    }

    @Test
    void uploadCsv_showsRequiredMessage_whenFileMissing() throws Exception {
        when(userMasterService.getAllUsers()).thenReturn(List.of());

        mockMvc.perform(multipart("/master/users/csv").with(csrf())).andExpect(status().isOk())
                .andExpect(content().string(containsString("CSVアップロードに失敗しました")))
                .andExpect(content().string(containsString("この項目は入力が必要です。")));
    }

    @Test
    @WithMockUser(username = "U1", roles = "USER")
    void showUsers_isForbidden_whenNotAdmin() throws Exception {
        mockMvc.perform(get("/master/users")).andExpect(status().isForbidden());
    }
}
