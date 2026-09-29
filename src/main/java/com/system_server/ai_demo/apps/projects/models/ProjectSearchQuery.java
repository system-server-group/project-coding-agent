package com.system_server.ai_demo.apps.projects.models;

import java.time.LocalDate;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Data;

/**
 * 案件情報マッパーの条件検索（{@code ProjectsMapper.search}）に渡す検索クエリ。
 *
 * <p>
 * {@link ProjectSearchCondition} を正規化したもの。日付文字列は {@code LocalDate} に解釈済み、空文字列は {@code null} に
 * 正規化済み（動的SQLの「入力のある条件のみ適用」を {@code null} 判定で行うため）。MyBatis から getter で参照するためクラスとする。
 */
@Data
@AllArgsConstructor
public class ProjectSearchQuery {

    /** 検索キーワード（未指定は {@code null}）。 */
    private String keyword;

    /** キーワード検索対象列のキー（title／skill／commercialFlow／workLocation／summary／note）。 */
    private List<String> keywordTargets;

    /** ステータス区分コードの一覧（指定値のいずれかに一致＝OR）。 */
    private List<String> statuses;

    /** 開始日(自)。 */
    private LocalDate startDateFrom;

    /** 開始日(至)。 */
    private LocalDate startDateTo;

    /** 終了日(自)。 */
    private LocalDate endDateFrom;

    /** 終了日(至)。 */
    private LocalDate endDateTo;

    /** 登録日(自)。 */
    private LocalDate registeredDateFrom;

    /** 登録日(至)。 */
    private LocalDate registeredDateTo;

    /** 登録者（部分一致。未指定は {@code null}）。 */
    private String registrant;

    /** 登録部署（部分一致。未指定は {@code null}）。 */
    private String registrantDepartment;

    /** 取引先（部分一致。未指定は {@code null}）。 */
    private String client;

    /** BP募集限定。 */
    private boolean bpOnly;

    /** BP募集限定時に一致させるBP募集(要否)の区分コード（{@code BpRecruitment.REQUIRED} のコード）。 */
    private String bpRequiredCode;
}
