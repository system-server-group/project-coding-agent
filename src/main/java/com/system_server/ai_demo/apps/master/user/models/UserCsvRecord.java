package com.system_server.ai_demo.apps.master.user.models;

/**
 * ユーザマスタ管理のCSVアップロード（編集）で読み込むCSVファイルの1行を表すモデル型。
 *
 * <p>
 * 入力値チェック前の生値を表すため、各項目は CSV のセル値（文字列）をそのまま保持する。編集属性・ロールは区分コード文字列、
 * 部署IDは整数文字列であり、列挙型・数値への変換は入力値チェック通過後にサービス層で行う。パスワードは平文。
 *
 * @param editAttribute 編集属性（区分コード文字列。0:何もしない/1:追加/2:更新/3:削除）
 * @param userId ユーザID
 * @param userName ユーザ名
 * @param email メールアドレス
 * @param departmentId 部署ID（整数文字列）
 * @param role ロール（区分コード文字列。0:一般ユーザ/9:システム管理者）
 * @param password パスワード（平文。保存時にBCryptでハッシュ化）
 */
public record UserCsvRecord(String editAttribute, String userId, String userName, String email,
        String departmentId, String role, String password) {

    /** CSV の列数（編集属性＋ユーザ6項目）。 */
    public static final int COLUMN_COUNT = 7;

    /**
     * CSV の1行（列値の配列）からモデルを生成する。列順は編集属性・ユーザID・ユーザ名・メールアドレス・部署ID・ロール・パスワード。
     *
     * @param fields 列値の配列（長さ {@link #COLUMN_COUNT}）
     * @return ユーザマスタCSVレコード
     */
    public static UserCsvRecord of(String[] fields) {
        return new UserCsvRecord(fields[0], fields[1], fields[2], fields[3], fields[4], fields[5],
                fields[6]);
    }
}
