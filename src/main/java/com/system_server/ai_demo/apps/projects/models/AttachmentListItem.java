package com.system_server.ai_demo.apps.projects.models;

import com.system_server.ai_demo.database.entity.ProjectAttachmentsSummaryEntity;
import java.time.OffsetDateTime;

/**
 * 添付一覧APIの応答1件分。案件情報更新画面の【ファイル一覧】が表示する項目を保持する。
 *
 * <p>
 * 実ファイル（バイナリ）は一覧表示に不要なため含めない。日時は ISO 文字列で出力し、表示形式（YYYY/MM/DD hh:mm:ss）・ファイルサイズの単位（B/KB/MB）・表示順の序数は
 * 画面側で整形する（案件検索APIと同じ方針）。
 *
 * @param fileId ファイルID
 * @param fileName ファイル名
 * @param fileSize ファイル容量（バイト）
 * @param createdBy 登録者
 * @param createdDepartment 登録部署
 * @param createdAt 登録日時
 */
public record AttachmentListItem(Integer fileId, String fileName, Integer fileSize,
        String createdBy, String createdDepartment, OffsetDateTime createdAt) {

    /**
     * 案件情報添付ファイル（実ファイルを除く読取用）から応答データを生成する。
     *
     * @param entity 案件情報添付ファイル
     * @return 添付一覧APIの応答1件分
     */
    public static AttachmentListItem from(ProjectAttachmentsSummaryEntity entity) {
        return new AttachmentListItem(entity.getFileId(), entity.getFileName(),
                entity.getFileSize(), entity.getCreatedBy(), entity.getCreatedDepartment(),
                entity.getCreatedAt());
    }
}
