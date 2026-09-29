package com.system_server.ai_demo.database.entity;

import java.time.OffsetDateTime;
import lombok.Data;

/**
 * 案件情報添付ファイル（project_attachments テーブル）の、実ファイルを除く読取用エンティティ。
 *
 * <p>
 * 一覧表示（添付一覧API）と登録済み件数の判定は実ファイル（BLOB）を必要としないため、本エンティティで読み出して 無駄な読み取りを避ける。実ファイルを扱う登録・ダウンロードは
 * {@link ProjectAttachmentsEntity} を用いる。
 */
@Data
public class ProjectAttachmentsSummaryEntity {

    private Integer fileId;
    private Integer managementCode;
    private String fileName;
    private Integer fileSize;
    private String createdBy;
    private String createdDepartment;
    private OffsetDateTime createdAt;
}
