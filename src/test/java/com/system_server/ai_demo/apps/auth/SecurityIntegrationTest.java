package com.system_server.ai_demo.apps.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrlPattern;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.system_server.ai_demo.TestcontainersConfiguration;
import com.system_server.ai_demo.database.entity.UsersEntity;
import com.system_server.ai_demo.database.mapper.UsersMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
@Transactional
@ActiveProfiles("test")
class SecurityIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UsersMapper usersMapper;

    @Autowired
    private PasswordEncoder passwordEncoder;

    private void insertUser(String userId, String roleCode, String rawPassword) {
        UsersEntity entity = new UsersEntity();
        entity.setUserId(userId);
        entity.setUserName("テスト");
        entity.setEmail(userId + "@example.com");
        entity.setDepartmentId(1);
        entity.setRole(roleCode);
        entity.setPassword(passwordEncoder.encode(rawPassword));
        usersMapper.insert(entity);
    }

    @Test
    void loginSuccess_redirectsToProjectHome() throws Exception {
        insertUser("SM001", "0", "secret123");
        mockMvc.perform(
                post("/login").param("userId", "SM001").param("password", "secret123").with(csrf()))
                .andExpect(status().is3xxRedirection()).andExpect(redirectedUrl("/projects"));
    }

    @Test
    void loginFailure_redirectsToLoginWithError() throws Exception {
        insertUser("SM001", "0", "secret123");
        mockMvc.perform(
                post("/login").param("userId", "SM001").param("password", "wrong").with(csrf()))
                .andExpect(status().is3xxRedirection()).andExpect(redirectedUrl("/?error"));
    }

    @Test
    void unauthenticatedPage_redirectsToLogin() throws Exception {
        mockMvc.perform(get("/projects")).andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/"));
    }

    @Test
    void unauthenticatedApi_returns401() throws Exception {
        // /api/** は未認証で 401（パスパターンで判定するためハンドラ未実装パスでも成立）
        mockMvc.perform(get("/api/__authz_probe__")).andExpect(status().isUnauthorized());
    }

    @Test
    void sessionExpiredRegisterPost_movesToProjectListAfterLogin() throws Exception {
        insertUser("SM001", "0", "secret123");

        // セッション失効（＝セッション内の CSRF トークンが失われた状態）での登録 POST は、権限不足ではなく
        // 未認証として扱われログイン画面へ誘導される。案件情報の登録は行われない。
        MvcResult denied = mockMvc.perform(post("/projects/new"))
                .andExpect(status().is3xxRedirection()).andExpect(redirectedUrl("/")).andReturn();

        // 更新系（POST）は復帰先に保存しないため、ログイン成功後は既定の案件情報一覧画面へ遷移する
        // （案件情報登録機能 機能仕様書「登録の失敗」）。
        mockMvc.perform(post("/login").param("userId", "SM001").param("password", "secret123")
                .cookie(denied.getResponse().getCookies()).with(csrf()))
                .andExpect(status().is3xxRedirection()).andExpect(redirectedUrl("/projects"));
    }

    @Test
    void sessionExpiredCsvUploadPost_movesToProjectListAfterLogin() throws Exception {
        insertUser("SM009", "9", "secret123");

        // セッション失効での CSV アップロード POST も登録・更新と同じく未認証として扱われ、ログイン画面へ
        // 誘導される。洗い替えは実施されない。
        MvcResult denied = mockMvc.perform(post("/master/companies/csv"))
                .andExpect(status().is3xxRedirection()).andExpect(redirectedUrl("/")).andReturn();

        // 更新系（POST）は復帰先に保存しないため、ログイン成功後は既定の案件情報一覧画面へ遷移する
        // （会社マスタ管理機能 機能仕様書「洗い替えの失敗」）。
        mockMvc.perform(post("/login").param("userId", "SM009").param("password", "secret123")
                .cookie(denied.getResponse().getCookies()).with(csrf()))
                .andExpect(status().is3xxRedirection()).andExpect(redirectedUrl("/projects"));
    }

    @Test
    void unauthenticatedScreenGet_returnsToRequestedScreenAfterLogin() throws Exception {
        insertUser("SM009", "9", "secret123");

        // 画面（GET）は復帰先として保存される（パーマリンク システム共通仕様書「未ログイン時の画面遷移の保留」）。
        MvcResult denied = mockMvc.perform(get("/master/companies"))
                .andExpect(status().is3xxRedirection()).andExpect(redirectedUrl("/")).andReturn();

        mockMvc.perform(post("/login").param("userId", "SM009").param("password", "secret123")
                .cookie(denied.getResponse().getCookies()).with(csrf()))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrlPattern("**/master/companies*"));
    }

    @Test
    void unauthenticatedCsvDownload_isNotSavedAsLoginRedirectTarget() throws Exception {
        insertUser("SM009", "9", "secret123");

        // CSV ダウンロードは GET だが画面ではないため復帰先にしない。未認証の要求はログイン画面へ誘導される。
        MvcResult denied = mockMvc.perform(get("/master/companies/csv"))
                .andExpect(status().is3xxRedirection()).andExpect(redirectedUrl("/")).andReturn();

        // 復帰先を保存しないためセッションも作られない（保存する場合はセッション Cookie が返る）。
        assertThat(denied.getResponse().getCookies()).isEmpty();

        // ログイン成功後はダウンロード URL へ復帰せず、既定の案件情報一覧画面へ遷移する
        // （会社マスタ管理機能 機能仕様書「ログインの有効期限が切れている場合」）。
        mockMvc.perform(
                post("/login").param("userId", "SM009").param("password", "secret123").with(csrf()))
                .andExpect(status().is3xxRedirection()).andExpect(redirectedUrl("/projects"));
    }

    @Test
    void unauthenticatedUnknownUrl_returnsNotFound() throws Exception {
        // 未認証でも存在しない URL はログイン画面へ誘導せず 404 とする
        // （パーマリンク システム共通仕様書「遷移を保留しない例外条件」）。
        mockMvc.perform(get("/no-such-page")).andExpect(status().isNotFound());
    }

    @Test
    void unauthenticatedUnknownUrl_isNotSavedAsLoginRedirectTarget() throws Exception {
        insertUser("SM001", "0", "secret123");

        // 存在しない URL は復帰先として保存しない（保存する場合はセッション Cookie が返る）。
        MvcResult denied =
                mockMvc.perform(get("/no-such-page")).andExpect(status().isNotFound()).andReturn();
        assertThat(denied.getResponse().getCookies()).isEmpty();

        // ログイン成功後は存在しない URL へ復帰せず、既定の案件情報一覧画面へ遷移する。
        mockMvc.perform(
                post("/login").param("userId", "SM001").param("password", "secret123").with(csrf()))
                .andExpect(status().is3xxRedirection()).andExpect(redirectedUrl("/projects"));
    }

    @Test
    @WithMockUser(roles = "USER")
    void generalUserAccessingMaster_isForbidden() throws Exception {
        mockMvc.perform(get("/master/users")).andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void adminAccessingMaster_passesAuthorization() throws Exception {
        // 認可は通過する（ハンドラ未実装の /master 配下パスのため 404。403 ではないことが認可通過の証左）
        mockMvc.perform(get("/master/__authz_probe__")).andExpect(status().isNotFound());
    }

    @Test
    @WithMockUser(roles = "USER")
    void authenticatedApiAuthorization_passes() throws Exception {
        // 認可は通過する（ハンドラ未実装の /api 配下パスのため 404。401/403 でないことが認可通過の証左）
        mockMvc.perform(get("/api/__authz_probe__")).andExpect(status().isNotFound());
    }
}
