package com.system_server.ai_demo.apps.projects.controllers;

import com.system_server.ai_demo.apps.common.CommonLayout;
import com.system_server.ai_demo.apps.master.reference.services.CompanyReferenceService;
import com.system_server.ai_demo.apps.master.reference.services.DepartmentReferenceService;
import com.system_server.ai_demo.apps.master.reference.services.UserReferenceService;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;

/**
 * 案件情報一覧画面コントローラー。一覧画面を表示し、検索フォームのオートコンプリート候補（登録者・登録部署・取引先）を供給する。
 *
 * <p>
 * 案件情報テーブルの初期データは初期表示イベントが案件検索APIで取得する。一般ユーザ・システム管理者ともにアクセス可能。共通レイアウトを使用する。
 */
@Controller
@CommonLayout
@RequestMapping("/projects")
public class ProjectListCtrl {

    private static final String VIEW = "projects/list";

    private final UserReferenceService userReferenceService;
    private final DepartmentReferenceService departmentReferenceService;
    private final CompanyReferenceService companyReferenceService;

    /**
     * 依存を注入して生成する。
     *
     * @param userReferenceService ユーザ参照サービス（登録者候補）
     * @param departmentReferenceService 部署参照サービス（登録部署候補）
     * @param companyReferenceService 会社名参照サービス（取引先候補）
     */
    public ProjectListCtrl(UserReferenceService userReferenceService,
            DepartmentReferenceService departmentReferenceService,
            CompanyReferenceService companyReferenceService) {
        this.userReferenceService = userReferenceService;
        this.departmentReferenceService = departmentReferenceService;
        this.companyReferenceService = companyReferenceService;
    }

    /**
     * 案件情報一覧画面を表示する。検索フォームのオートコンプリート候補を設定する。
     *
     * @param model モデル
     * @return 案件情報一覧画面テンプレート
     */
    @GetMapping
    public String showProjectList(Model model) {
        model.addAttribute("registrantOptions", userReferenceService.getUserNames());
        model.addAttribute("departmentOptions", departmentReferenceService.getDepartmentNames());
        model.addAttribute("clientOptions", companyReferenceService.getCompanyNames());
        return VIEW;
    }
}
