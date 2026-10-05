package com.system_server.ai_demo.apps.master.company.controllers;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.model;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

import com.system_server.ai_demo.apps.master.common.csv.CsvFailureType;
import com.system_server.ai_demo.apps.master.common.csv.CsvReplaceResult;
import com.system_server.ai_demo.apps.master.company.services.CompanyMasterService;
import com.system_server.ai_demo.apps.master.reference.services.DepartmentReferenceService;
import com.system_server.ai_demo.apps.master.reference.services.UserReferenceService;
import com.system_server.ai_demo.config.SecurityConfig;
import com.system_server.ai_demo.database.entity.CompaniesEntity;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.core.NestedExceptionUtils;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.test.context.support.WithAnonymousUser;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

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

    /** 会社IDの順にも会社名の順にも並んでいない一覧（会社名の昇順は 2→1→3）。 */
    private static List<CompaniesEntity> unsortedCompanies() {
        return List.of(company(2, "ア社"), company(3, "ウ社"), company(1, "イ社"));
    }

    private static MockMultipartFile csvFile(String body) {
        return new MockMultipartFile("csvFile", "会社マスタ.csv", "text/csv",
                body.getBytes(StandardCharsets.UTF_8));
    }

    /** CSV ダウンロードを要求し、CSV 変換に渡された一覧の会社IDを並び順どおりに返す。 */
    @SuppressWarnings("unchecked")
    private List<Integer> downloadAndCaptureCompanyIds(MockHttpServletRequestBuilder request)
            throws Exception {
        when(companyMasterService.getAllCompanies()).thenReturn(unsortedCompanies());
        when(companyMasterService.toCsv(any())).thenReturn(new byte[0]);

        mockMvc.perform(request).andExpect(status().isOk());

        ArgumentCaptor<List<CompaniesEntity>> captor = ArgumentCaptor.forClass(List.class);
        verify(companyMasterService).toCsv(captor.capture());
        return captor.getValue().stream().map(CompaniesEntity::getCompanyId).toList();
    }

    @BeforeEach
    void stubHeader() {
        when(userReferenceService.getUserName(any())).thenReturn("管理者");
        when(userReferenceService.getUserDepartmentId(any())).thenReturn(10);
        when(departmentReferenceService.getDepartmentName(10)).thenReturn("情報システム部");
    }

    @Test
    void showCompanies_rendersListViewWithCompaniesAndCount() throws Exception {
        List<CompaniesEntity> companies = List.of(company(1, "エー社"), company(2, "ビー社"));
        when(companyMasterService.getAllCompanies()).thenReturn(companies);

        mockMvc.perform(get("/master/companies")).andExpect(status().isOk())
                .andExpect(view().name("master/companies"))
                .andExpect(model().attribute("companies", companies))
                .andExpect(model().attribute("companyCount", 2))
                .andExpect(content().string(containsString("会社マスタ管理画面")))
                .andExpect(content().string(containsString("取得データ件数: ")))
                .andExpect(content().string(containsString(">2</span>件")))
                .andExpect(content().string(containsString("window.COMPANY_ROWS = [{")));
    }

    @Test
    void showCompanies_rendersZeroCountAndEmptyRows_whenNoCompanies() throws Exception {
        when(companyMasterService.getAllCompanies()).thenReturn(List.of());

        mockMvc.perform(get("/master/companies")).andExpect(status().isOk())
                .andExpect(model().attribute("companies", List.of()))
                .andExpect(model().attribute("companyCount", 0))
                .andExpect(content().string(containsString(">0</span>件")))
                .andExpect(content().string(containsString("window.COMPANY_ROWS = [];")));
    }

    @Test
    void showCompanies_rendersGridAndCountAtTopAndBottom_withoutErrorArea() throws Exception {
        when(companyMasterService.getAllCompanies())
                .thenReturn(List.of(company(1, "エー社"), company(2, "ビー社")));

        String html = mockMvc.perform(get("/master/companies")).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);

        // 取得データ件数はテーブル上部・下部の 2 箇所に表示し、初期表示ではエラー表示を出さない。
        assertEquals(2, StringUtils.countOccurrencesOf(html, ">2</span>件"));
        assertFalse(html.contains("id=\"errorArea\""));
        assertTrue(html.contains("id=\"companyGrid\""));
        assertTrue(html.contains("/js/company-master.js"));
    }

    @Test
    void showCompanies_highlightsCompanyMasterNav() throws Exception {
        when(companyMasterService.getAllCompanies()).thenReturn(List.of());

        // 共通ヘッダーのナビのうち、表示中の会社マスタ管理画面に対応する「会社マスタ」を強調表示する。
        mockMvc.perform(get("/master/companies")).andExpect(status().isOk()).andExpect(content()
                .string(containsString("class=\"nav-btn active\" href=\"/master/companies\"")));
    }

    @Test
    void showCompanies_rendersCsvDownloadButton() throws Exception {
        when(companyMasterService.getAllCompanies()).thenReturn(List.of());

        mockMvc.perform(get("/master/companies")).andExpect(status().isOk())
                .andExpect(content().string(containsString("id=\"csvDownloadButton\"")))
                .andExpect(content().string(containsString("CSVダウンロード")));
    }

    @Test
    void showCompanies_rendersCsvUploadForm() throws Exception {
        when(companyMasterService.getAllCompanies()).thenReturn(List.of());

        String html = mockMvc.perform(get("/master/companies")).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);

        // アップロードは POST・multipart で /master/companies/csv へ CSRF トークンを伴って送信する。
        // ファイル入力は csvFile（許容するファイル形式は CSV のみ）。
        int start = html.indexOf("<form id=\"csvUploadForm\"");
        assertTrue(start >= 0);
        String form = html.substring(start, html.indexOf("</form>", start));
        assertTrue(form.contains("action=\"/master/companies/csv\""));
        assertTrue(form.contains("method=\"post\""));
        assertTrue(form.contains("enctype=\"multipart/form-data\""));
        assertTrue(form.contains("name=\"_csrf\""));
        assertTrue(form.contains("type=\"file\""));
        assertTrue(form.contains("name=\"csvFile\""));
        assertTrue(form.contains("accept=\".csv\""));
        assertTrue(form.contains("id=\"csvUploadButton\""));
    }

    @Test
    void downloadCsv_returnsCsvAttachmentNamedCompanyMaster() throws Exception {
        List<CompaniesEntity> companies = List.of(company(1, "エー社"));
        when(companyMasterService.getAllCompanies()).thenReturn(companies);
        byte[] body = {(byte) 0xEF, (byte) 0xBB, (byte) 0xBF, 'I', 'D'};
        when(companyMasterService.toCsv(companies)).thenReturn(body);

        MvcResult result = mockMvc.perform(get("/master/companies/csv")).andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.CONTENT_TYPE, containsString("text/csv")))
                .andExpect(content().bytes(body)).andReturn();

        ContentDisposition disposition = ContentDisposition
                .parse(result.getResponse().getHeader(HttpHeaders.CONTENT_DISPOSITION));
        assertTrue(disposition.isAttachment());
        assertEquals("会社マスタ.csv", disposition.getFilename());
    }

    @Test
    void downloadCsv_sortsByCompanyIdAscending_whenSortAndOrderAreOmitted() throws Exception {
        assertEquals(List.of(1, 2, 3), downloadAndCaptureCompanyIds(get("/master/companies/csv")));
    }

    @Test
    void downloadCsv_sortsByCompanyIdDescending() throws Exception {
        assertEquals(List.of(3, 2, 1), downloadAndCaptureCompanyIds(
                get("/master/companies/csv").param("sort", "companyId").param("order", "desc")));
    }

    @Test
    void downloadCsv_sortsByCompanyNameAscending() throws Exception {
        assertEquals(List.of(2, 1, 3), downloadAndCaptureCompanyIds(
                get("/master/companies/csv").param("sort", "companyName").param("order", "asc")));
    }

    @Test
    void downloadCsv_sortsByCompanyNameDescending() throws Exception {
        assertEquals(List.of(3, 1, 2), downloadAndCaptureCompanyIds(
                get("/master/companies/csv").param("sort", "companyName").param("order", "desc")));
    }

    @Test
    void downloadCsv_sortsByCompanyId_whenSortIsUnknown() throws Exception {
        assertEquals(List.of(3, 2, 1), downloadAndCaptureCompanyIds(
                get("/master/companies/csv").param("sort", "unknown").param("order", "desc")));
    }

    @Test
    void uploadCsv_showsRequiredError_withoutReplacing_whenFilePartIsMissing() throws Exception {
        List<CompaniesEntity> companies = List.of(company(1, "エー社"));
        when(companyMasterService.getAllCompanies()).thenReturn(companies);

        mockMvc.perform(multipart("/master/companies/csv").with(csrf())).andExpect(status().isOk())
                .andExpect(view().name("master/companies"))
                .andExpect(model().attribute("errorTitle", "CSVアップロードに失敗しました"))
                .andExpect(model().attribute("errorMessages", List.of("この項目は入力が必要です。")))
                .andExpect(model().attribute("companies", companies))
                .andExpect(model().attribute("companyCount", 1))
                .andExpect(content().string(containsString("CSVアップロードに失敗しました")))
                .andExpect(content().string(containsString("この項目は入力が必要です。")));

        verify(companyMasterService, never()).replaceAll(any());
    }

    @Test
    void uploadCsv_showsRequiredError_withoutReplacing_whenFileIsEmpty() throws Exception {
        when(companyMasterService.getAllCompanies()).thenReturn(List.of());

        mockMvc.perform(multipart("/master/companies/csv")
                .file(new MockMultipartFile("csvFile", "empty.csv", "text/csv", new byte[0]))
                .with(csrf())).andExpect(status().isOk())
                .andExpect(model().attribute("errorTitle", "CSVアップロードに失敗しました"))
                .andExpect(model().attribute("errorMessages", List.of("この項目は入力が必要です。")));

        verify(companyMasterService, never()).replaceAll(any());
    }

    @Test
    void uploadCsv_reShowsLatestList_withoutError_whenReplaceSucceeds() throws Exception {
        byte[] uploaded = "会社ID,会社名\r\n1,エー社\r\n2,ビー社\r\n".getBytes(StandardCharsets.UTF_8);
        List<CompaniesEntity> replaced = List.of(company(1, "エー社"), company(2, "ビー社"));
        when(companyMasterService.replaceAll(any())).thenReturn(CsvReplaceResult.ofSuccess());
        when(companyMasterService.getAllCompanies()).thenReturn(replaced);

        mockMvc.perform(multipart("/master/companies/csv")
                .file(new MockMultipartFile("csvFile", "会社マスタ.csv", "text/csv", uploaded))
                .with(csrf())).andExpect(status().isOk()).andExpect(view().name("master/companies"))
                .andExpect(model().attributeDoesNotExist("errorTitle", "errorMessages"))
                .andExpect(model().attribute("companies", replaced))
                .andExpect(model().attribute("companyCount", 2))
                .andExpect(content().string(not(containsString("id=\"errorArea\""))));

        // 受け取った CSV ファイルで洗い替えを実行し、その後に全件を取得し直して一覧を再表示する。
        InOrder order = inOrder(companyMasterService);
        ArgumentCaptor<MultipartFile> captor = ArgumentCaptor.forClass(MultipartFile.class);
        order.verify(companyMasterService).replaceAll(captor.capture());
        order.verify(companyMasterService).getAllCompanies();
        assertArrayEquals(uploaded, captor.getValue().getBytes());
    }

    @Test
    void uploadCsv_showsFormatError_whenFormatCheckFails() throws Exception {
        when(companyMasterService.replaceAll(any())).thenReturn(CsvReplaceResult
                .ofFailure(CsvFailureType.FORMAT, List.of("CSVファイルにデータがありませんでした。")));
        when(companyMasterService.getAllCompanies()).thenReturn(List.of(company(1, "エー社")));

        mockMvc.perform(
                multipart("/master/companies/csv").file(csvFile("会社ID,会社名\r\n")).with(csrf()))
                .andExpect(status().isOk())
                .andExpect(model().attribute("errorTitle", "CSVアップロードに失敗しました"))
                .andExpect(model().attribute("errorMessages", List.of("CSVファイルにデータがありませんでした。")))
                .andExpect(model().attribute("companyCount", 1))
                .andExpect(content().string(containsString("CSVアップロードに失敗しました")))
                .andExpect(content().string(containsString("CSVファイルにデータがありませんでした。")));
    }

    @Test
    void uploadCsv_showsAllValidationErrors_whenValidationFails() throws Exception {
        List<String> errors = List.of("0002 - データ1行目 会社ID: この項目は入力が必要です。",
                "0003 - データ2行目 会社名: 使用不可能な文字が含まれています。");
        when(companyMasterService.replaceAll(any()))
                .thenReturn(CsvReplaceResult.ofFailure(CsvFailureType.VALIDATION, errors));
        when(companyMasterService.getAllCompanies()).thenReturn(List.of());

        mockMvc.perform(multipart("/master/companies/csv")
                .file(csvFile("会社ID,会社名\r\n,エー社\r\n2,ABC\r\n")).with(csrf()))
                .andExpect(status().isOk())
                .andExpect(model().attribute("errorTitle", "CSVの内容にエラーがあります。詳細は以下の通りです。"))
                .andExpect(model().attribute("errorMessages", errors))
                .andExpect(content().string(containsString("CSVの内容にエラーがあります。詳細は以下の通りです。")))
                .andExpect(content().string(containsString(errors.get(0))))
                .andExpect(content().string(containsString(errors.get(1))));
    }

    @Test
    void uploadCsv_propagatesException_whenReplaceFails() {
        // DB 例外等は握りつぶさずに送出する（ロールバックはサービスの @Transactional、エラー画面【サーバー内部エラー】
        // への遷移は共通のエラー処理が担う）。
        DataAccessException failure = new DataAccessResourceFailureException("replace failed");
        when(companyMasterService.replaceAll(any())).thenThrow(failure);

        Exception thrown = assertThrows(Exception.class,
                () -> mockMvc.perform(multipart("/master/companies/csv")
                        .file(csvFile("会社ID,会社名\r\n1,エー社\r\n")).with(csrf())));

        assertSame(failure, NestedExceptionUtils.getMostSpecificCause(thrown));
        verify(companyMasterService, never()).getAllCompanies();
    }

    @Test
    @WithMockUser(username = "U1", roles = "USER")
    void showCompanies_isForbidden_whenNotAdmin() throws Exception {
        mockMvc.perform(get("/master/companies")).andExpect(status().isForbidden());

        verifyNoInteractions(companyMasterService);
    }

    @Test
    @WithMockUser(username = "U1", roles = "USER")
    void downloadCsv_isForbidden_whenNotAdmin() throws Exception {
        mockMvc.perform(get("/master/companies/csv")).andExpect(status().isForbidden());

        verifyNoInteractions(companyMasterService);
    }

    @Test
    @WithMockUser(username = "U1", roles = "USER")
    void uploadCsv_isForbidden_whenNotAdmin() throws Exception {
        mockMvc.perform(multipart("/master/companies/csv").file(csvFile("会社ID,会社名\r\n1,エー社\r\n"))
                .with(csrf())).andExpect(status().isForbidden());

        verifyNoInteractions(companyMasterService);
    }

    @Test
    @WithAnonymousUser
    void showCompanies_redirectsToLogin_whenUnauthenticated() throws Exception {
        mockMvc.perform(get("/master/companies")).andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/"));

        verifyNoInteractions(companyMasterService);
    }

    @Test
    @WithAnonymousUser
    void downloadCsv_redirectsToLogin_withoutDownloading_whenUnauthenticated() throws Exception {
        mockMvc.perform(get("/master/companies/csv")).andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/"));

        verifyNoInteractions(companyMasterService);
    }

    @Test
    @WithAnonymousUser
    void uploadCsv_redirectsToLogin_withoutReplacing_whenSessionExpired() throws Exception {
        // セッション失効（＝セッション内の CSRF トークンが失われた状態）での POST は、権限不足ではなく未認証として
        // 扱われログイン画面へ誘導される。洗い替えは実施されない。
        mockMvc.perform(multipart("/master/companies/csv").file(csvFile("会社ID,会社名\r\n1,エー社\r\n")))
                .andExpect(status().is3xxRedirection()).andExpect(redirectedUrl("/"));

        verifyNoInteractions(companyMasterService);
    }
}
