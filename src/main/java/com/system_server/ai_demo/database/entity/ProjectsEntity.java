package com.system_server.ai_demo.database.entity;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import lombok.Data;

/**
 * 案件情報（projects テーブル）のエンティティ。
 *
 * <p>
 * {@code status}・{@code bpRecruitment}・{@code contractType} は区分コード（文字列）を保持する。列挙型への変換は サービス層で行う。
 */
@Data
public class ProjectsEntity {

    private Integer managementCode;
    private String title;
    private String status;
    private String clientName;
    private String commercialFlow;
    private String summary;
    private String process;
    private LocalDate startDate;
    private LocalDate endDate;
    private String skill;
    private String headcount;
    private String remainingCount;
    private String bpRecruitment;
    private String bpRecruitmentDetail;
    private String workLocation;
    private String estimatedUnitPrice;
    private String contractType;
    private String note;
    private String createdBy;
    private String createdDepartment;
    private OffsetDateTime createdAt;
    private String updatedBy;
    private String updatedDepartment;
    private OffsetDateTime updatedAt;
}
