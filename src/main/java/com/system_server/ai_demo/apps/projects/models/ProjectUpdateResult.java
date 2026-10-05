package com.system_server.ai_demo.apps.projects.models;

/**
 * 案件更新の結果。
 */
public enum ProjectUpdateResult {

    /** 更新成功。 */
    SUCCESS,

    /** 楽観排他失敗（読込〜更新の間に他ユーザが更新した）。 */
    CONFLICT,

    /** 添付ファイルの登録上限超過（登録済みが既に5件で追加できない）。 */
    ATTACHMENT_LIMIT
}
