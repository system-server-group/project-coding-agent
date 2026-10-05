package com.system_server.ai_demo.apps.projects.models;

import com.system_server.ai_demo.commons.validation.AllowedCharacters;
import com.system_server.ai_demo.commons.validation.CheckMessages;
import com.system_server.ai_demo.commons.validation.DateInBounds;
import com.system_server.ai_demo.commons.validation.DateRange;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;
import java.util.List;
import lombok.Data;

/**
 * 案件情報一覧の検索フォームで入力される検索条件。案件検索API（POST /api/projects/search）のリクエストボディ。
 *
 * <p>
 * 日付項目は日付入力部品が送出する ISO 形式（{@code yyyy-MM-dd}）を {@link LocalDate} として受け取り、値域
 * （1900/01/01〜9999/12/31）と項目間の前後関係を共通バリデーションで検証する。入力のある条件のみ適用し、複数条件はAND結合、
 * 無入力なら全件。キーワードは選択した対象列のいずれかに部分一致（対象列間はOR）、ステータスは指定値のいずれかに一致（OR）。 {@link DateRange} を用いるため record
 * ではなく getter を持つクラスとする。
 */
@Data
@DateRange(from = "startDateFrom", to = "startDateTo", boundLabel = "開始日(自)")
@DateRange(from = "endDateFrom", to = "endDateTo", boundLabel = "終了日(自)")
@DateRange(from = "registeredDateFrom", to = "registeredDateTo", boundLabel = "登録日(自)")
public class ProjectSearchCondition {

    /** 検索キーワード。 */
    @Size(max = 100, message = CheckMessages.MAX_LENGTH)
    @AllowedCharacters
    private String keyword;

    /** キーワード検索対象列（title／skill／commercialFlow／workLocation／summary／note のうち選択。対象列間はOR）。 */
    private List<String> keywordTargets;

    /** ステータス（区分コードの複数指定。指定値のいずれかに一致＝OR）。 */
    private List<String> statuses;

    /** 開始日(自)。 */
    @DateInBounds
    private LocalDate startDateFrom;

    /** 開始日(至)。 */
    @DateInBounds
    private LocalDate startDateTo;

    /** 終了日(自)。 */
    @DateInBounds
    private LocalDate endDateFrom;

    /** 終了日(至)。 */
    @DateInBounds
    private LocalDate endDateTo;

    /** 登録日(自)。 */
    @DateInBounds
    private LocalDate registeredDateFrom;

    /** 登録日(至)。 */
    @DateInBounds
    private LocalDate registeredDateTo;

    /** 登録者（部分一致）。 */
    @Size(max = 50, message = CheckMessages.MAX_LENGTH)
    @AllowedCharacters
    private String registrant;

    /** 登録部署（部分一致）。 */
    @Size(max = 50, message = CheckMessages.MAX_LENGTH)
    @AllowedCharacters
    private String registrantDepartment;

    /** 取引先（部分一致）。 */
    @Size(max = 200, message = CheckMessages.MAX_LENGTH)
    @AllowedCharacters
    private String client;

    /** BP募集限定（指定時はBP募集(要否)が「必要」の案件に限定）。 */
    private boolean bpOnly;
}
