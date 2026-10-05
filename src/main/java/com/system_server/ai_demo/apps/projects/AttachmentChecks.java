package com.system_server.ai_demo.apps.projects;

import java.util.ArrayList;
import java.util.List;
import org.springframework.web.multipart.MultipartFile;

/**
 * 添付ファイルの形式チェック（容量・ファイル名長）を集約する共通部品。案件登録・更新の両画面で再利用する。
 *
 * <p>
 * 形式チェックはクライアント側でも実施されるが、防御的にサーバ側でも検証する（容量6MB以下・ファイル名200字以下）。登録済み件数の
 * 上限（最大5件）は対象の案件情報に依存するため本部品では扱わない（呼び出し側で判定する）。
 */
public final class AttachmentChecks {

    /** 添付ファイルの最大容量（6MB）。 */
    public static final long MAX_SIZE = 6L * 1024 * 1024;

    /** ファイル名（拡張子を含む）の最大文字数。 */
    public static final int MAX_NAME_LENGTH = 200;

    private static final String MSG_TOO_LARGE = "添付ファイルの容量が大きすぎます(6MBまで)";
    private static final String MSG_NAME_TOO_LONG = "ファイル名は200字までです。";

    private AttachmentChecks() {}

    /**
     * 添付ファイルの容量・ファイル名長を検証し、違反メッセージの一覧を返す。未指定（{@code null}・空）は対象外。
     *
     * @param file 添付ファイル
     * @return 違反メッセージの一覧（違反が無ければ空）
     */
    public static List<String> validate(MultipartFile file) {
        List<String> errors = new ArrayList<>();
        if (file == null || file.isEmpty()) {
            return errors;
        }
        if (file.getSize() > MAX_SIZE) {
            errors.add(MSG_TOO_LARGE);
        }
        String name = file.getOriginalFilename();
        if (name != null && name.length() > MAX_NAME_LENGTH) {
            errors.add(MSG_NAME_TOO_LONG);
        }
        return errors;
    }
}
