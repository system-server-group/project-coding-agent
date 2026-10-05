package com.system_server.ai_demo.apps.master.department.controllers;

import com.system_server.ai_demo.apps.common.CommonLayout;
import com.system_server.ai_demo.apps.master.common.csv.CsvFailureType;
import com.system_server.ai_demo.apps.master.common.csv.CsvReplaceResult;
import com.system_server.ai_demo.apps.master.department.services.DepartmentMasterService;
import com.system_server.ai_demo.commons.validation.CheckMessages;
import com.system_server.ai_demo.database.entity.DepartmentsEntity;
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
 * 部署マスタ管理画面コントローラー。一覧表示・CSV ダウンロード・CSV アップロード（洗い替え）を行う。システム管理者のみ アクセス可能（認可は SecurityConfig
 * が強制）。共通レイアウトを使用する。
 */
@Controller
@CommonLayout
@RequestMapping("/master/departments")
public class DepartmentMasterCtrl {

    private static final String VIEW = "master/departments";

    private final DepartmentMasterService departmentMasterService;

    public DepartmentMasterCtrl(DepartmentMasterService departmentMasterService) {
        this.departmentMasterService = departmentMasterService;
    }

    /**
     * 部署マスタ管理画面を表示する（全件・部署ID昇順）。
     *
     * @param model モデル
     * @return 部署マスタ管理画面テンプレート
     */
    @GetMapping
    public String showDepartments(Model model) {
        addDepartments(model);
        return VIEW;
    }

    /**
     * 部署マスタの全件を CSV でダウンロードする。
     *
     * @param sort 並び替え対象列（departmentId／departmentName。既定 departmentId）
     * @param order 並び順（asc／desc。既定 asc）
     * @return CSV ファイルのダウンロード応答
     */
    @GetMapping("/csv")
    public ResponseEntity<byte[]> downloadCsv(
            @RequestParam(defaultValue = "departmentId") String sort,
            @RequestParam(defaultValue = "asc") String order) {
        List<DepartmentsEntity> departments =
                new ArrayList<>(departmentMasterService.getAllDepartments());
        departments.sort(comparator(sort, order));
        byte[] csv = departmentMasterService.toCsv(departments);

        ContentDisposition disposition = ContentDisposition.attachment()
                .filename("部署マスタ.csv", StandardCharsets.UTF_8).build();
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(new MediaType("text", "csv", StandardCharsets.UTF_8));
        headers.setContentDisposition(disposition);
        return new ResponseEntity<>(csv, headers, HttpStatus.OK);
    }

    /**
     * アップロードされた CSV で部署マスタを洗い替え、結果に応じて一覧再表示またはエラー表示を行う。
     *
     * @param csvFile アップロードされた CSV ファイル
     * @param model モデル
     * @return 部署マスタ管理画面テンプレート
     */
    @PostMapping("/csv")
    public String uploadCsv(
            @RequestParam(value = "csvFile", required = false) MultipartFile csvFile,
            Model model) {
        if (csvFile == null || csvFile.isEmpty()) {
            model.addAttribute("errorTitle", "CSVアップロードに失敗しました");
            model.addAttribute("errorMessages", List.of(CheckMessages.REQUIRED_INPUT));
            addDepartments(model);
            return VIEW;
        }
        CsvReplaceResult result = departmentMasterService.replaceAll(csvFile);
        if (!result.success()) {
            if (result.failureType() == CsvFailureType.FORMAT) {
                model.addAttribute("errorTitle", "CSVアップロードに失敗しました");
            } else {
                model.addAttribute("errorTitle", "CSVの内容にエラーがあります。詳細は以下の通りです。");
            }
            model.addAttribute("errorMessages", result.errors());
        }
        addDepartments(model);
        return VIEW;
    }

    private void addDepartments(Model model) {
        List<DepartmentsEntity> departments = departmentMasterService.getAllDepartments();
        model.addAttribute("departments", departments);
        model.addAttribute("departmentCount", departments.size());
    }

    private static Comparator<DepartmentsEntity> comparator(String sort, String order) {
        Comparator<DepartmentsEntity> comparator =
                "departmentName".equals(sort) ? Comparator.comparing(d -> d.getDepartmentName())
                        : Comparator.comparing(d -> d.getDepartmentId());
        return "desc".equalsIgnoreCase(order) ? comparator.reversed() : comparator;
    }
}
