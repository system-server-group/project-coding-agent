package com.system_server.ai_demo.apps.projects.controllers;

import com.system_server.ai_demo.apps.common.CommonLayout;
import com.system_server.ai_demo.apps.master.reference.services.CompanyReferenceService;
import com.system_server.ai_demo.apps.projects.AttachmentChecks;
import com.system_server.ai_demo.apps.projects.models.ProjectRegisterForm;
import com.system_server.ai_demo.apps.projects.services.ProjectRegisterService;
import com.system_server.ai_demo.commons.code.CodeEnums;
import com.system_server.ai_demo.commons.error.FormErrorMessages;
import com.system_server.ai_demo.enums.ContractType;
import com.system_server.ai_demo.enums.Status;
import jakarta.validation.Valid;
import java.security.Principal;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;

/**
 * 案件情報登録画面コントローラー。登録画面の表示と案件情報の新規登録を行う。一般ユーザ・システム管理者ともにアクセス可能。共通レイアウトを使用する。
 */
@Controller
@CommonLayout
@RequestMapping("/projects")
public class ProjectRegisterCtrl {

    private static final String VIEW = "projects/register";

    private final ProjectRegisterService projectRegisterService;
    private final CompanyReferenceService companyReferenceService;

    public ProjectRegisterCtrl(ProjectRegisterService projectRegisterService,
            CompanyReferenceService companyReferenceService) {
        this.projectRegisterService = projectRegisterService;
        this.companyReferenceService = companyReferenceService;
    }

    /**
     * 案件情報登録画面を表示する（初期値・取引先候補を設定）。
     *
     * @param model モデル
     * @return 案件情報登録画面テンプレート
     */
    @GetMapping("/new")
    public String showRegisterForm(Model model) {
        ProjectRegisterForm form = new ProjectRegisterForm();
        form.setStatus(Status.OPEN.getCode());
        form.setContractType("");
        form.setBpRequired(false);
        form.setSendMail(true);
        model.addAttribute("form", form);
        addFormOptions(model);
        return VIEW;
    }

    /**
     * 入力された案件情報を登録し、成功時は案件情報更新画面へリダイレクトする。
     *
     * <p>
     * 登録画面の表示（{@code GET /projects/new}）と同一URLで受ける。セッション失効時、復帰先として保存されるのは画面の GET のみで 本 POST
     * は保存されないため（{@code SecurityConfig}）、機能仕様書「ログインの有効期限が切れている場合」のとおり
     * ログイン成功後は案件情報一覧画面へ遷移し、入力した値は破棄され登録は行われない。
     *
     * @param form 案件登録Form
     * @param bindingResult 入力値チェック結果
     * @param principal ログイン中ユーザ
     * @param model モデル
     * @return 成功時は更新画面へのリダイレクト、違反時は登録画面の再表示
     */
    @PostMapping("/new")
    public String register(
            @Valid @ModelAttribute("form") ProjectRegisterForm form,
            BindingResult bindingResult,
            Principal principal,
            Model model) {
        // ステータスの空値は必須入力の入力値チェック（Form の @NotBlank）として扱う。選択肢に無い
        // 非空の値のみ、通常起こりえない改ざん等への防御として異常終了させ、エラー画面（500）へ委ねる
        // （コントローラー定義書「区分値の確認」）。
        if (form.getStatus() != null && !form.getStatus().isBlank()) {
            CodeEnums.fromCode(Status.class, form.getStatus());
        }

        Map<String, String> fieldErrors =
                new LinkedHashMap<>(FormErrorMessages.reduceByField(bindingResult));
        List<String> attachmentErrors = AttachmentChecks.validate(form.getAttachment());
        if (!attachmentErrors.isEmpty()) {
            fieldErrors.putIfAbsent("attachment", attachmentErrors.get(0));
        }
        if (!fieldErrors.isEmpty()) {
            model.addAttribute("fieldErrors", fieldErrors);
            addFormOptions(model);
            return VIEW;
        }
        Integer managementCode = projectRegisterService.register(form, principal.getName());
        return "redirect:/projects/detail/" + managementCode;
    }

    private void addFormOptions(Model model) {
        model.addAttribute("clientOptions", companyReferenceService.getCompanyNames());
        model.addAttribute("statusOptions", Status.values());
        model.addAttribute("contractTypeOptions", ContractType.values());
    }
}
