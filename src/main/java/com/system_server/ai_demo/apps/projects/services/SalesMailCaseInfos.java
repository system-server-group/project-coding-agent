package com.system_server.ai_demo.apps.projects.services;

import com.system_server.ai_demo.apps.projects.models.SalesMailCaseInfo;
import com.system_server.ai_demo.commons.code.CodeEnum;
import com.system_server.ai_demo.commons.code.CodeEnums;
import com.system_server.ai_demo.database.entity.ProjectsEntity;
import com.system_server.ai_demo.enums.BpRecruitment;
import com.system_server.ai_demo.enums.ContractType;
import com.system_server.ai_demo.enums.Status;

/**
 * 案件情報エンティティから営業情報メール記載用の表示データ（{@link SalesMailCaseInfo}）を生成する共通部品。案件登録・更新で再利用する。
 *
 * <p>
 * 区分項目（ステータス・BP募集(要否)・契約種別）はコードを列挙型に解決して表示名（ラベル）に変換する。値の無い項目は {@code null}。
 */
final class SalesMailCaseInfos {

    private SalesMailCaseInfos() {}

    /**
     * 案件情報エンティティから表示データを生成する。
     *
     * @param project 案件情報エンティティ（管理コード採番済み）
     * @return メール記載用の表示データ
     */
    static SalesMailCaseInfo of(ProjectsEntity project) {
        return new SalesMailCaseInfo(String.valueOf(project.getManagementCode()),
                project.getTitle(), project.getClientName(), project.getCommercialFlow(),
                project.getSummary(), project.getProcess(), project.getStartDate(),
                project.getEndDate(), project.getSkill(), project.getHeadcount(),
                project.getRemainingCount(), label(BpRecruitment.class, project.getBpRecruitment()),
                project.getBpRecruitmentDetail(), project.getWorkLocation(),
                project.getEstimatedUnitPrice(),
                label(ContractType.class, project.getContractType()),
                label(Status.class, project.getStatus()), project.getNote(), project.getCreatedAt(),
                project.getCreatedDepartment(), project.getCreatedBy());
    }

    private static <E extends Enum<E> & CodeEnum> String label(Class<E> enumType, String code) {
        E resolved = CodeEnums.fromCodeOrNull(enumType, blankToNull(code));
        return resolved == null ? null : resolved.getLabel();
    }

    private static String blankToNull(String value) {
        return value == null || value.isEmpty() ? null : value;
    }
}
