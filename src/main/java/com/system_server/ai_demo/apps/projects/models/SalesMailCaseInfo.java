package com.system_server.ai_demo.apps.projects.models;

import java.time.LocalDate;
import java.time.OffsetDateTime;

/**
 * 営業情報メールに記載する案件情報の表示データ。
 *
 * <p>
 * 各項目はメール記載用の表示値で、区分項目（BP募集(要否)・契約種別・ステータス）は表示名（ラベル）を保持する。日付・日時は型付き
 * 値（{@link LocalDate}／{@link OffsetDateTime}）で保持し、共通出力様式（YYYY年MM月DD日 等）への整形は
 * {@code SalesMailService} が行う。値が無い項目は {@code null}（本文では空欄になる）。
 *
 * @param managementCode 管理コード
 * @param title 件名
 * @param clientName 取引先
 * @param commercialFlow 商流
 * @param summary 案件概要（改行を含みうる）
 * @param process 工程
 * @param startDate 開始日
 * @param endDate 終了日
 * @param skill スキル
 * @param headcount 人数
 * @param remainingCount 残数
 * @param bpRecruitment BP募集(要否)（区分の表示名）
 * @param bpRecruitmentDetail BP募集(詳細)
 * @param workLocation 作業場所
 * @param estimatedUnitPrice 見込単価
 * @param contractType 契約種別（区分の表示名）
 * @param status ステータス（区分の表示名）
 * @param note 備考（改行を含みうる）
 * @param createdAt 登録日時
 * @param createdDepartment 登録部署（部署名）
 * @param createdBy 登録者（ユーザ名）
 */
public record SalesMailCaseInfo(String managementCode, String title, String clientName,
        String commercialFlow, String summary, String process, LocalDate startDate,
        LocalDate endDate, String skill, String headcount, String remainingCount,
        String bpRecruitment, String bpRecruitmentDetail, String workLocation,
        String estimatedUnitPrice, String contractType, String status, String note,
        OffsetDateTime createdAt, String createdDepartment, String createdBy) {
}
