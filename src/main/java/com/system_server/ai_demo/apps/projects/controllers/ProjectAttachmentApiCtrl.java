package com.system_server.ai_demo.apps.projects.controllers;

import com.system_server.ai_demo.apps.projects.models.AttachmentDeleteResponse;
import com.system_server.ai_demo.apps.projects.models.AttachmentListItem;
import com.system_server.ai_demo.apps.projects.services.ProjectUpdateService;
import com.system_server.ai_demo.database.entity.ProjectAttachmentsEntity;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 添付ファイルAPI。案件情報添付ファイルの一覧取得・ダウンロード・削除を提供する。案件情報更新画面から呼び出される。一般ユーザ・システム管理者 ともにアクセス可能（認可は
 * SecurityConfig が強制）。
 */
@RestController
@RequestMapping("/api/attachments")
public class ProjectAttachmentApiCtrl {

    private final ProjectUpdateService projectUpdateService;

    public ProjectAttachmentApiCtrl(ProjectUpdateService projectUpdateService) {
        this.projectUpdateService = projectUpdateService;
    }

    /**
     * 管理コードで指定された案件情報の添付ファイル一覧（登録日時昇順）を返す。実ファイルは含まない。
     *
     * <p>
     * 案件情報更新画面の【ファイル一覧】は初期表示・再取得ともに本APIの応答を用いる（表示の整形は画面側で一元化する）。
     *
     * @param managementCode 管理コード
     * @return 添付ファイル一覧（該当が無い場合は空）
     */
    @GetMapping
    public List<AttachmentListItem> listAttachments(@RequestParam Integer managementCode) {
        return projectUpdateService.getAttachments(managementCode).stream()
                .map(AttachmentListItem::from).toList();
    }

    /**
     * ファイルIDで指定された添付ファイルをダウンロード応答として返す。該当が無い場合は404。
     *
     * @param fileId ファイルID
     * @return 添付ファイルのダウンロード応答
     */
    @GetMapping("/{fileId}")
    public ResponseEntity<byte[]> downloadAttachment(@PathVariable Integer fileId) {
        ProjectAttachmentsEntity attachment = projectUpdateService.getAttachment(fileId);
        if (attachment == null) {
            return ResponseEntity.notFound().build();
        }
        ContentDisposition disposition = ContentDisposition.attachment()
                .filename(attachment.getFileName(), StandardCharsets.UTF_8).build();
        HttpHeaders headers = new HttpHeaders();
        headers.setContentDisposition(disposition);
        headers.setContentType(MediaType.APPLICATION_OCTET_STREAM);
        return new ResponseEntity<>(attachment.getFileData(), headers, HttpStatus.OK);
    }

    /**
     * ファイルIDで指定された添付ファイルを削除し、削除件数を返す。
     *
     * @param fileId ファイルID
     * @return 削除件数
     */
    @DeleteMapping("/{fileId}")
    public AttachmentDeleteResponse deleteAttachment(@PathVariable Integer fileId) {
        return new AttachmentDeleteResponse(projectUpdateService.deleteAttachment(fileId));
    }
}
