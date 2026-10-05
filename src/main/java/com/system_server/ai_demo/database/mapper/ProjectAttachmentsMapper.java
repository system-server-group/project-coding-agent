package com.system_server.ai_demo.database.mapper;

import com.system_server.ai_demo.database.entity.ProjectAttachmentsEntity;
import com.system_server.ai_demo.database.entity.ProjectAttachmentsSummaryEntity;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/**
 * 案件情報添付ファイル（project_attachments）の 1 表専用データアクセス。
 */
@Mapper
public interface ProjectAttachmentsMapper {

    /**
     * 指定した案件情報に紐づく添付ファイルをファイルID昇順で取得する（実ファイルを含まない）。
     *
     * @param managementCode 管理コード
     * @return 添付ファイルの一覧
     */
    List<ProjectAttachmentsSummaryEntity> findByManagementCode(
            @Param("managementCode") Integer managementCode);

    /**
     * ファイルIDを指定して添付ファイルを 1 件取得する（実ファイルを含む。ダウンロード用）。
     *
     * @param fileId ファイルID
     * @return 添付ファイル、存在しない場合は {@code null}
     */
    ProjectAttachmentsEntity findByFileId(@Param("fileId") Integer fileId);

    /**
     * 添付ファイルを 1 件登録する（ファイルIDはテーブルの自動採番。採番値は引数の {@code fileId} に設定される）。
     *
     * @param attachment 登録する添付ファイル
     * @return 登録件数
     */
    int insert(ProjectAttachmentsEntity attachment);

    /**
     * ファイルIDを指定して添付ファイルを 1 件削除する。
     *
     * @param fileId ファイルID
     * @return 削除件数
     */
    int deleteByFileId(@Param("fileId") Integer fileId);
}
