package com.system_server.ai_demo.apps.master.user.models;

/**
 * ユーザマスタ編集（CSVアップロード）の失敗種別。
 */
public enum UserEditFailureType {

    /** ファイル形式チェック違反。 */
    FORMAT,

    /** 入力値チェック違反。 */
    VALIDATION,

    /** 業務エラー（追加・更新・削除の実行中に発生した異常）。 */
    BUSINESS
}
