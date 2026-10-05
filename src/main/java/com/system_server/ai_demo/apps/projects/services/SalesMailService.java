package com.system_server.ai_demo.apps.projects.services;

import com.system_server.ai_demo.apps.projects.models.SalesMailCaseInfo;
import com.system_server.ai_demo.apps.projects.models.SalesMailOperation;
import com.system_server.ai_demo.config.AiDemoProperties;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.mail.MailException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;

/**
 * 営業情報メール送信サービス。案件情報の登録・更新時に、営業情報メール共通仕様のレイアウトに従う通知メールを生成して送信する。
 *
 * <p>
 * 送信は任意であり、失敗しても案件情報の登録・更新は取り消さず、ユーザにも通知しない（送信の成否を呼び出し元に影響させない）。 登録・更新の両方から共通利用される。
 */
@Service
public class SalesMailService {

    private static final Logger log = LoggerFactory.getLogger(SalesMailService.class);

    private static final String SUBJECT_PREFIX = "【営業情報】";
    private static final String ATTACHMENT_NOTE = "(添付ファイルの登録あり)";
    private static final String DIVIDER = "---------------------------------------------";

    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("uuuu年MM月dd日");
    private static final DateTimeFormatter DATE_TIME_FORMAT =
            DateTimeFormatter.ofPattern("uuuu年MM月dd日 HH時mm分ss秒");

    private final JavaMailSender mailSender;
    private final AiDemoProperties properties;

    /**
     * 依存を注入して生成する。
     *
     * @param mailSender メール送信器（spring.mail 設定から自動構成）
     * @param properties アプリ固有プロパティ（宛先リスト）
     */
    public SalesMailService(JavaMailSender mailSender, AiDemoProperties properties) {
        this.mailSender = mailSender;
        this.properties = properties;
    }

    /**
     * 操作区分に応じたタイトル・本文の営業情報メールを生成し、設定された宛先へ送信する。
     *
     * <p>
     * 宛先は設定の宛先リスト（本番系の営業部メールリスト）。リストが空の場合は送信元と同一アドレスとする（開発系・検証系）。
     * 送信に失敗した場合は例外を送出せず、警告ログのみを残す（宛先・本文・認証情報はログに含めない）。
     *
     * @param operation 操作区分（登録／更新）
     * @param caseInfo メール記載用の案件情報
     * @param mailBody 入力されたメール本文
     * @param fromAddress 送信元メールアドレス（登録・更新したユーザのメールアドレス）
     * @param hasAttachment 添付ファイル有無
     * @param updateScreenUrl 更新画面URL
     */
    public void send(
            SalesMailOperation operation,
            SalesMailCaseInfo caseInfo,
            String mailBody,
            String fromAddress,
            boolean hasAttachment,
            String updateScreenUrl) {
        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom(fromAddress);
        message.setTo(resolveRecipients(fromAddress));
        message.setSubject(buildSubject(operation, caseInfo));
        message.setText(buildBody(operation, caseInfo, mailBody, hasAttachment, updateScreenUrl));

        try {
            mailSender.send(message);
        } catch (MailException ex) {
            // 送信失敗は呼び出し元の処理結果に影響させない。宛先・本文等の PII を残さないため種別のみ記録する。
            log.warn("営業情報メールの送信に失敗しました。operation={}, cause={}", operation,
                    ex.getClass().getSimpleName());
        }
    }

    private String[] resolveRecipients(String fromAddress) {
        List<String> recipients = properties.getMail().getRecipients();
        if (recipients == null || recipients.isEmpty()) {
            return new String[] {fromAddress};
        }
        return recipients.toArray(new String[0]);
    }

    private static String buildSubject(SalesMailOperation operation, SalesMailCaseInfo caseInfo) {
        return SUBJECT_PREFIX + operation.getSubjectMark() + " " + value(caseInfo.title());
    }

    private static String buildBody(
            SalesMailOperation operation,
            SalesMailCaseInfo caseInfo,
            String mailBody,
            boolean hasAttachment,
            String updateScreenUrl) {
        String attachmentNote = hasAttachment ? ATTACHMENT_NOTE : "";
        List<String> lines = List.of(operation.getHeaderText(),
                value(updateScreenUrl) + " " + attachmentNote, "", value(mailBody), DIVIDER,
                "● 案件情報", "", "【管理コード】  " + value(caseInfo.managementCode()),
                "【件名】    " + value(caseInfo.title()), "【取引先】  " + value(caseInfo.clientName()),
                "【商流】    " + value(caseInfo.commercialFlow()),
                "【案件概要】    " + value(caseInfo.summary()), "【工程】    " + value(caseInfo.process()),
                "【開始日】  " + date(caseInfo.startDate()), "【終了日】  " + date(caseInfo.endDate()),
                "【スキル】  " + value(caseInfo.skill()), "【人数】    " + value(caseInfo.headcount()),
                "【残数】    " + value(caseInfo.remainingCount()),
                "【ＢＰ募集(要否)】  " + value(caseInfo.bpRecruitment()),
                "【ＢＰ募集(詳細)】  " + value(caseInfo.bpRecruitmentDetail()),
                "【作業場所】    " + value(caseInfo.workLocation()),
                "【見込単価】    " + value(caseInfo.estimatedUnitPrice()),
                "【契約種別】    " + value(caseInfo.contractType()),
                "【ステータス】  " + value(caseInfo.status()), "【備考】    " + value(caseInfo.note()),
                "【登録日時】    " + dateTime(caseInfo.createdAt()),
                "【登録部署】    " + value(caseInfo.createdDepartment()),
                "【登録者】  " + value(caseInfo.createdBy()), DIVIDER);
        return String.join("\n", lines);
    }

    private static String value(String raw) {
        return raw == null ? "" : raw;
    }

    private static String date(LocalDate date) {
        return date == null ? "" : date.format(DATE_FORMAT);
    }

    private static String dateTime(OffsetDateTime dateTime) {
        return dateTime == null ? "" : dateTime.format(DATE_TIME_FORMAT);
    }
}
