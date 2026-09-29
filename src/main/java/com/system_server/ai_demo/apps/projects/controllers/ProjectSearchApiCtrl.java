package com.system_server.ai_demo.apps.projects.controllers;

import com.system_server.ai_demo.apps.projects.models.ProjectSearchCondition;
import com.system_server.ai_demo.apps.projects.models.ProjectSearchResponse;
import com.system_server.ai_demo.apps.projects.services.ProjectSearchService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 案件検索API。検索条件に合致する案件情報を返す。案件情報一覧画面の初期表示・検索から呼び出される。
 *
 * <p>
 * 入力値チェック違反は共通の {@code ApiExceptionHandler} が 400（{@code errors[]}）へ整形する。一般ユーザ・システム管理者
 * ともにアクセス可能（認可は SecurityConfig が強制）。
 */
@RestController
@RequestMapping("/api/projects")
public class ProjectSearchApiCtrl {

    private final ProjectSearchService projectSearchService;

    public ProjectSearchApiCtrl(ProjectSearchService projectSearchService) {
        this.projectSearchService = projectSearchService;
    }

    /**
     * 検索条件に合致する案件情報を全件返す。
     *
     * @param condition 検索条件
     * @return 案件情報の一覧
     */
    @PostMapping("/search")
    public ProjectSearchResponse search(@Valid @RequestBody ProjectSearchCondition condition) {
        return new ProjectSearchResponse(projectSearchService.search(condition));
    }
}
