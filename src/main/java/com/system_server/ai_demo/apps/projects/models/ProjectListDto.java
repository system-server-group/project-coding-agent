package com.system_server.ai_demo.apps.projects.models;

import com.system_server.ai_demo.enums.BpRecruitment;
import com.system_server.ai_demo.enums.ContractType;
import com.system_server.ai_demo.enums.Status;
import java.time.LocalDate;
import java.time.OffsetDateTime;

/**
 * 案件情報一覧の1件分の応答データ。案件検索APIが返す案件情報テーブルの表示項目。
 *
 * <p>
 * 区分項目（{@code status}・{@code bpRecruitment}・{@code contractType}）は列挙型で保持し、
 * {@code CodeEnumSerializer} により {@code {code, label}} で出力される（表示はラベル、判定はコード）。日付・日時は ISO
 * 文字列で出力し、表示形式（YYYY/MM/DD・YYYY/MM/DD hh:mm:ss）への整形・改行の半角スペース置換は画面側で行う。 値の無い項目は {@code null}。
 *
 * @param managementCode 管理コード
 * @param title 件名
 * @param status ステータス（区分）
 * @param clientName 取引先
 * @param commercialFlow 商流
 * @param summary 案件概要
 * @param process 工程
 * @param startDate 開始日
 * @param endDate 終了日
 * @param skill スキル
 * @param headcount 人数
 * @param remainingCount 残数
 * @param bpRecruitment BP募集(要否)（区分）
 * @param bpRecruitmentDetail BP募集(詳細)
 * @param workLocation 作業場所
 * @param estimatedUnitPrice 見込単価
 * @param contractType 契約種別（区分）
 * @param note 備考
 * @param createdBy 登録者
 * @param createdDepartment 登録部署
 * @param createdAt 登録日時
 * @param updatedBy 更新者
 * @param updatedDepartment 更新部署
 * @param updatedAt 更新日時
 */
public record ProjectListDto(Integer managementCode, String title, Status status, String clientName,
        String commercialFlow, String summary, String process, LocalDate startDate,
        LocalDate endDate, String skill, String headcount, String remainingCount,
        BpRecruitment bpRecruitment, String bpRecruitmentDetail, String workLocation,
        String estimatedUnitPrice, ContractType contractType, String note, String createdBy,
        String createdDepartment, OffsetDateTime createdAt, String updatedBy,
        String updatedDepartment, OffsetDateTime updatedAt) {
}
