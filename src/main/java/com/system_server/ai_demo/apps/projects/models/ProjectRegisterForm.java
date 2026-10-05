package com.system_server.ai_demo.apps.projects.models;

import com.system_server.ai_demo.commons.validation.AllowedCharacters;
import com.system_server.ai_demo.commons.validation.CheckMessages;
import com.system_server.ai_demo.commons.validation.DateInBounds;
import com.system_server.ai_demo.commons.validation.DateRange;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;
import lombok.Data;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.multipart.MultipartFile;

/**
 * 案件情報登録画面で入力される登録内容。案件情報の入力項目に加え、営業情報メールの送信可否・本文、添付ファイル（任意・1件）を保持する。
 *
 * <p>
 * 日付は日付入力部品（{@code <input type="date">}）が送出する ISO 形式（{@code yyyy-MM-dd}）を {@link LocalDate} として
 * 受け取り、値域（1900/01/01〜9999/12/31）と項目間の前後関係を共通バリデーションで検証する。ステータス・契約種別は区分コード
 * 文字列、BP募集(要否)はチェックボックス（真＝必要／偽＝不要）。{@link DateRange} を用いるため getter を持つクラスとする。
 */
@Data
@DateRange(from = "startDate", to = "endDate", boundLabel = "開始日")
public class ProjectRegisterForm {

    /** 件名。 */
    @NotBlank(message = CheckMessages.REQUIRED_INPUT)
    @Size(max = 200, message = CheckMessages.MAX_LENGTH)
    @AllowedCharacters
    private String subject;

    /** ステータス（区分コード・必須）。 */
    @NotBlank(message = CheckMessages.REQUIRED_INPUT)
    private String status;

    /** 取引先。 */
    @Size(max = 200, message = CheckMessages.MAX_LENGTH)
    @AllowedCharacters
    private String client;

    /** 商流。 */
    @Size(max = 200, message = CheckMessages.MAX_LENGTH)
    @AllowedCharacters
    private String distribution;

    /** 案件概要（改行可）。 */
    @Size(max = 2000, message = CheckMessages.MAX_LENGTH)
    @AllowedCharacters(allowNewline = true)
    private String overview;

    /** 工程。 */
    @Size(max = 200, message = CheckMessages.MAX_LENGTH)
    @AllowedCharacters
    private String process;

    /** 開始日。 */
    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
    @DateInBounds
    private LocalDate startDate;

    /** 終了日。 */
    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
    @DateInBounds
    private LocalDate endDate;

    /** スキル。 */
    @Size(max = 200, message = CheckMessages.MAX_LENGTH)
    @AllowedCharacters
    private String skill;

    /** 人数。 */
    @Size(max = 200, message = CheckMessages.MAX_LENGTH)
    @AllowedCharacters
    private String headcount;

    /** 残数。 */
    @Size(max = 200, message = CheckMessages.MAX_LENGTH)
    @AllowedCharacters
    private String remainingCount;

    /** BP募集(要否)（チェックあり＝必要）。 */
    private boolean bpRequired;

    /** BP募集(詳細)。 */
    @Size(max = 200, message = CheckMessages.MAX_LENGTH)
    @AllowedCharacters
    private String bpDetail;

    /** 作業場所。 */
    @Size(max = 200, message = CheckMessages.MAX_LENGTH)
    @AllowedCharacters
    private String workplace;

    /** 見込単価。 */
    @Size(max = 200, message = CheckMessages.MAX_LENGTH)
    @AllowedCharacters
    private String estimatedPrice;

    /** 契約種別（区分コード。未選択は空文字）。 */
    private String contractType;

    /** 備考（改行可）。 */
    @Size(max = 4000, message = CheckMessages.MAX_LENGTH)
    @AllowedCharacters(allowNewline = true)
    private String note;

    /** メール送信フラグ。 */
    private boolean sendMail;

    /** メール本文（改行可）。 */
    @Size(max = 4000, message = CheckMessages.MAX_LENGTH)
    @AllowedCharacters(allowNewline = true)
    private String mailBody;

    /** 添付ファイル（任意・1件）。 */
    private MultipartFile attachment;
}
