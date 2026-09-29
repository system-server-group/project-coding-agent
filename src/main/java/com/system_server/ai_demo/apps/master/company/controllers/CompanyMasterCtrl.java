package com.system_server.ai_demo.apps.master.company.controllers;

import com.system_server.ai_demo.apps.common.CommonLayout;
import com.system_server.ai_demo.apps.master.common.csv.CsvFailureType;
import com.system_server.ai_demo.apps.master.common.csv.CsvReplaceResult;
import com.system_server.ai_demo.apps.master.company.services.CompanyMasterService;
import com.system_server.ai_demo.commons.validation.CheckMessages;
import com.system_server.ai_demo.database.entity.CompaniesEntity;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.multipart.MultipartFile;

/**
 * 会社マスタ管理画面コントローラー。一覧表示・CSV ダウンロード・CSV アップロード（洗い替え）を行う。システム管理者のみ アクセス可能（認可は SecurityConfig
 * が強制）。共通レイアウトを使用する。
 */
@Controller
@CommonLayout
@RequestMapping("/master/companies")
public class CompanyMasterCtrl {

    private static final String VIEW = "master/companies";

    private final CompanyMasterService companyMasterService;

    public CompanyMasterCtrl(CompanyMasterService companyMasterService) {
        this.companyMasterService = companyMasterService;
    }

    /**
     * 会社マスタ管理画面を表示する（全件・会社ID昇順）。
     *
     * @param model モデル
     * @return 会社マスタ管理画面テンプレート
     */
    @GetMapping
    public String showCompanies(Model model) {
        addCompanies(model);
        return VIEW;
    }

    /**
     * 会社マスタの全件を CSV でダウンロードする。
     *
     * @param sort 並び替え対象列（companyId／companyName。既定 companyId）
     * @param order 並び順（asc／desc。既定 asc）
     * @return CSV ファイルのダウンロード応答
     */
    @GetMapping("/csv")
    public ResponseEntity<byte[]> downloadCsv(
            @RequestParam(defaultValue = "companyId") String sort,
            @RequestParam(defaultValue = "asc") String order) {
        List<CompaniesEntity> companies = new ArrayList<>(companyMasterService.getAllCompanies());
        companies.sort(comparator(sort, order));
        byte[] csv = companyMasterService.toCsv(companies);

        ContentDisposition disposition = ContentDisposition.attachment()
                .filename("会社マスタ.csv", StandardCharsets.UTF_8).build();
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(new MediaType("text", "csv", StandardCharsets.UTF_8));
        headers.setContentDisposition(disposition);
        return new ResponseEntity<>(csv, headers, HttpStatus.OK);
    }

    /**
     * アップロードされた CSV で会社マスタを洗い替え、結果に応じて一覧再表示またはエラー表示を行う。
     *
     * @param csvFile アップロードされた CSV ファイル
     * @param model モデル
     * @return 会社マスタ管理画面テンプレート
     */
    @PostMapping("/csv")
    public String uploadCsv(
            @RequestParam(value = "csvFile", required = false) MultipartFile csvFile,
            Model model) {
        if (csvFile == null || csvFile.isEmpty()) {
            model.addAttribute("errorTitle", "CSVアップロードに失敗しました");
            model.addAttribute("errorMessages", List.of(CheckMessages.REQUIRED_INPUT));
            addCompanies(model);
            return VIEW;
        }
        CsvReplaceResult result = companyMasterService.replaceAll(csvFile);
        if (!result.success()) {
            if (result.failureType() == CsvFailureType.FORMAT) {
                model.addAttribute("errorTitle", "CSVアップロードに失敗しました");
            } else {
                model.addAttribute("errorTitle", "CSVの内容にエラーがあります。詳細は以下の通りです。");
            }
            model.addAttribute("errorMessages", result.errors());
        }
        addCompanies(model);
        return VIEW;
    }

    private void addCompanies(Model model) {
        List<CompaniesEntity> companies = companyMasterService.getAllCompanies();
        model.addAttribute("companies", companies);
        model.addAttribute("companyCount", companies.size());
    }

    private static Comparator<CompaniesEntity> comparator(String sort, String order) {
        Comparator<CompaniesEntity> comparator =
                "companyName".equals(sort) ? Comparator.comparing(c -> c.getCompanyName())
                        : Comparator.comparing(c -> c.getCompanyId());
        return "desc".equalsIgnoreCase(order) ? comparator.reversed() : comparator;
    }
}
