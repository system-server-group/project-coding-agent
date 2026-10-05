package com.system_server.ai_demo.apps.projects.services;

import com.system_server.ai_demo.apps.projects.models.ProjectListDto;
import com.system_server.ai_demo.apps.projects.models.ProjectSearchCondition;
import com.system_server.ai_demo.apps.projects.models.ProjectSearchQuery;
import com.system_server.ai_demo.commons.code.CodeEnums;
import com.system_server.ai_demo.database.entity.ProjectsEntity;
import com.system_server.ai_demo.database.mapper.ProjectsMapper;
import com.system_server.ai_demo.enums.BpRecruitment;
import com.system_server.ai_demo.enums.ContractType;
import com.system_server.ai_demo.enums.Status;
import java.util.List;
import org.springframework.stereotype.Service;

/**
 * 案件検索サービス。検索条件に合致する案件情報を取得する。案件情報一覧画面の初期表示・検索で用いられる。
 */
@Service
public class ProjectSearchService {

    private final ProjectsMapper projectsMapper;

    public ProjectSearchService(ProjectsMapper projectsMapper) {
        this.projectsMapper = projectsMapper;
    }

    /**
     * 検索条件に合致する案件情報を全件取得する（管理コード降順）。
     *
     * <p>
     * 検索条件を正規化（空文字列の {@code null} 化）した上で {@link ProjectsMapper#search} に委譲し、取得した エンティティを表示用の
     * {@link ProjectListDto} に変換する。区分コードは列挙型に解決して {@code {code, label}} 出力に備える。
     *
     * @param condition 検索条件
     * @return 条件に合致する案件情報の一覧（0件の場合は空の一覧）
     */
    public List<ProjectListDto> search(ProjectSearchCondition condition) {
        ProjectSearchQuery query = new ProjectSearchQuery(blankToNull(condition.getKeyword()),
                condition.getKeywordTargets(), condition.getStatuses(),
                condition.getStartDateFrom(), condition.getStartDateTo(),
                condition.getEndDateFrom(), condition.getEndDateTo(),
                condition.getRegisteredDateFrom(), condition.getRegisteredDateTo(),
                blankToNull(condition.getRegistrant()),
                blankToNull(condition.getRegistrantDepartment()),
                blankToNull(condition.getClient()), condition.isBpOnly(),
                BpRecruitment.REQUIRED.getCode());

        return projectsMapper.search(query).stream().map(ProjectSearchService::toDto).toList();
    }

    private static ProjectListDto toDto(ProjectsEntity entity) {
        return new ProjectListDto(entity.getManagementCode(), entity.getTitle(),
                CodeEnums.fromCodeOrNull(Status.class, entity.getStatus()), entity.getClientName(),
                entity.getCommercialFlow(), entity.getSummary(), entity.getProcess(),
                entity.getStartDate(), entity.getEndDate(), entity.getSkill(),
                entity.getHeadcount(), entity.getRemainingCount(),
                CodeEnums.fromCodeOrNull(BpRecruitment.class, entity.getBpRecruitment()),
                entity.getBpRecruitmentDetail(), entity.getWorkLocation(),
                entity.getEstimatedUnitPrice(),
                CodeEnums.fromCodeOrNull(ContractType.class, entity.getContractType()),
                entity.getNote(), entity.getCreatedBy(), entity.getCreatedDepartment(),
                entity.getCreatedAt(), entity.getUpdatedBy(), entity.getUpdatedDepartment(),
                entity.getUpdatedAt());
    }

    private static String blankToNull(String value) {
        return value == null || value.isEmpty() ? null : value;
    }
}
