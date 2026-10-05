package com.system_server.ai_demo.apps.projects.controllers;

import com.system_server.ai_demo.apps.common.CommonLayout;
import com.system_server.ai_demo.apps.master.reference.services.CompanyReferenceService;
import com.system_server.ai_demo.apps.projects.AttachmentChecks;
import com.system_server.ai_demo.apps.projects.models.ProjectUpdateForm;
import com.system_server.ai_demo.apps.projects.models.ProjectUpdateResult;
import com.system_server.ai_demo.apps.projects.services.ProjectUpdateService;
import com.system_server.ai_demo.commons.code.CodeEnums;
import com.system_server.ai_demo.commons.error.FormErrorMessages;
import com.system_server.ai_demo.database.entity.ProjectsEntity;
import com.system_server.ai_demo.enums.BpRecruitment;
import com.system_server.ai_demo.enums.ContractType;
import com.system_server.ai_demo.enums.Status;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import java.io.IOException;
import java.security.Principal;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

/**
 * 案件情報更新画面コントローラー。管理コードで指定された案件情報の表示と、楽観排他を伴う更新を行う。一般ユーザ・システム管理者
 * ともにアクセス可能。共通レイアウトを使用する。添付ファイルのダウンロード・削除は添付ファイルAPIが担う。
 */
@Controller
@CommonLayout
@RequestMapping("/projects")
public class ProjectEditCtrl {

    private static final String VIEW = "projects/edit";

    private static final String NOT_FOUND_TITLE = "存在しない案件情報です";
    private static final String NOT_FOUND_MESSAGE = "IDが不正、もしくは削除された案件情報です。";
    private static final String CONFLICT_TITLE = "案件情報の更新に失敗しました。";
    private static final String CONFLICT_MESSAGE = "他のユーザが案件情報を更新しました。再度更新内容を入力してください。";
    private static final String ATTACHMENT_LIMIT_MESSAGE = "登録できる添付ファイルは5件までです。";

    private final ProjectUpdateService projectUpdateService;
    private final CompanyReferenceService companyReferenceService;

    public ProjectEditCtrl(ProjectUpdateService projectUpdateService,
            CompanyReferenceService companyReferenceService) {
        this.projectUpdateService = projectUpdateService;
        this.companyReferenceService = companyReferenceService;
    }

    /**
     * 管理コードが整数に変換できない不正なパスでアクセスされた場合は、ページ未検出エラー（404）としてエラー画面へ遷移させる。 画面コントローラーのため、API 共通の JSON
     * エラー応答（{@code ApiExceptionHandler}）ではなく、エラー画面ディスパッチ（{@code sendError}）を用いる。
     *
     * @param response HTTP レスポンス
     * @throws IOException エラーディスパッチに失敗した場合
     */
    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    void handleInvalidManagementCode(HttpServletResponse response) throws IOException {
        response.sendError(HttpStatus.NOT_FOUND.value());
    }

    /**
     * 管理コードで指定された案件情報を表示する。【ファイル一覧】は画面側が添付ファイルAPIから取得する。
     *
     * @param managementCode 管理コード（パス変数）
     * @param model モデル
     * @return 案件情報更新画面テンプレート
     */
    @GetMapping("/detail/{managementCode}")
    public String showEditForm(@PathVariable Integer managementCode, Model model) {
        ProjectsEntity project = projectUpdateService.getProject(managementCode);
        if (project == null) {
            model.addAttribute("errorTitle", NOT_FOUND_TITLE);
            model.addAttribute("errorMessages", List.of(NOT_FOUND_MESSAGE));
            return VIEW;
        }
        addEditModel(model, project, toForm(project));
        return VIEW;
    }

    /**
     * 入力された案件情報を楽観排他のもとで更新し、結果に応じて再表示またはエラー表示を行う。
     *
     * @param managementCode 管理コード（パス変数）
     * @param form 案件更新Form
     * @param bindingResult 入力値チェック結果
     * @param principal ログイン中ユーザ
     * @param model モデル
     * @return 案件情報更新画面テンプレート
     */
    @PostMapping("/detail/{managementCode}")
    public String update(
            @PathVariable Integer managementCode,
            @Valid @ModelAttribute("form") ProjectUpdateForm form,
            BindingResult bindingResult,
            Principal principal,
            Model model) {
        form.setManagementCode(managementCode);

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
            return reShowWithInput(model, managementCode, form, fieldErrors);
        }

        ProjectUpdateResult result = projectUpdateService.update(form, principal.getName());
        if (result == ProjectUpdateResult.ATTACHMENT_LIMIT) {
            return reShowWithInput(model, managementCode, form,
                    Map.of("attachment", ATTACHMENT_LIMIT_MESSAGE));
        }
        if (result == ProjectUpdateResult.CONFLICT) {
            ProjectsEntity latest = projectUpdateService.getProject(managementCode);
            model.addAttribute("errorTitle", CONFLICT_TITLE);
            model.addAttribute("errorMessages", List.of(CONFLICT_MESSAGE));
            addEditModel(model, latest, toForm(latest));
            return VIEW;
        }
        ProjectsEntity updated = projectUpdateService.getProject(managementCode);
        addEditModel(model, updated, toForm(updated));
        return VIEW;
    }

    private String reShowWithInput(
            Model model,
            Integer managementCode,
            ProjectUpdateForm form,
            Map<String, String> fieldErrors) {
        model.addAttribute("fieldErrors", fieldErrors);
        addEditModel(model, projectUpdateService.getProject(managementCode), form);
        return VIEW;
    }

    private void addEditModel(Model model, ProjectsEntity project, ProjectUpdateForm form) {
        model.addAttribute("form", form);
        model.addAttribute("project", project);
        model.addAttribute("titleBadge", "ID: " + project.getManagementCode());
        // 【ファイル一覧】は添付ファイルAPI（添付一覧）から画面側で取得・描画するため、モデルには載せない。
        model.addAttribute("clientOptions", companyReferenceService.getCompanyNames());
        model.addAttribute("statusOptions", Status.values());
        model.addAttribute("contractTypeOptions", ContractType.values());
    }

    private static ProjectUpdateForm toForm(ProjectsEntity project) {
        ProjectUpdateForm form = new ProjectUpdateForm();
        form.setManagementCode(project.getManagementCode());
        form.setSubject(project.getTitle());
        form.setStatus(project.getStatus());
        form.setClient(project.getClientName());
        form.setDistribution(project.getCommercialFlow());
        form.setOverview(project.getSummary());
        form.setProcess(project.getProcess());
        form.setStartDate(project.getStartDate());
        form.setEndDate(project.getEndDate());
        form.setSkill(project.getSkill());
        form.setHeadcount(project.getHeadcount());
        form.setRemainingCount(project.getRemainingCount());
        form.setBpRequired(BpRecruitment.REQUIRED.getCode().equals(project.getBpRecruitment()));
        form.setBpDetail(project.getBpRecruitmentDetail());
        form.setWorkplace(project.getWorkLocation());
        form.setEstimatedPrice(project.getEstimatedUnitPrice());
        form.setContractType(project.getContractType() == null ? "" : project.getContractType());
        form.setNote(project.getNote());
        form.setSendMail(true);
        form.setLoadedUpdatedAt(project.getUpdatedAt());
        return form;
    }
}
