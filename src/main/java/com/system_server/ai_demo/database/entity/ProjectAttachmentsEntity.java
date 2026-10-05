package com.system_server.ai_demo.database.entity;

import java.time.OffsetDateTime;
import lombok.Data;

/**
 * 案件情報添付ファイル（project_attachments テーブル）のエンティティ。
 */
@Data
public class ProjectAttachmentsEntity {

    private Integer fileId;
    private Integer managementCode;
    private String fileName;
    private byte[] fileData;
    private Integer fileSize;
    private String createdBy;
    private String createdDepartment;
    private OffsetDateTime createdAt;
}
