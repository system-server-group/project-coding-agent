package com.system_server.ai_demo.apps.master.user.models;

import java.util.List;

/**
 * ユーザマスタ編集（CSVアップロード）の結果。成功または失敗（失敗種別＋表示用エラー明細）を表す。
 *
 * <p>
 * 業務エラー時はエラータイトル「{データ行番号}行目で異常終了しました。」を {@code errorTitle} に保持する。形式チェック・入力値 チェック違反時の
 * {@code errorTitle} は {@code null}（タイトルの設定はコントローラー側の責務）。
 *
 * @param success 編集が成功したか
 * @param failureType 失敗種別（成功時は {@code null}）
 * @param errorTitle 業務エラー時のエラータイトル（業務エラー以外は {@code null}）
 * @param errors 表示用のエラーメッセージ一覧（成功時は空）
 */
public record UserEditResult(boolean success, UserEditFailureType failureType, String errorTitle,
        List<String> errors) {

    /**
     * 成功結果を生成する。
     *
     * @return 成功結果
     */
    public static UserEditResult ofSuccess() {
        return new UserEditResult(true, null, null, List.of());
    }

    /**
     * 形式チェック失敗結果を生成する。
     *
     * @param errors エラーメッセージ一覧
     * @return 失敗結果
     */
    public static UserEditResult ofFormatFailure(List<String> errors) {
        return new UserEditResult(false, UserEditFailureType.FORMAT, null, errors);
    }

    /**
     * 入力値チェック失敗結果を生成する。
     *
     * @param errors エラーメッセージ一覧
     * @return 失敗結果
     */
    public static UserEditResult ofValidationFailure(List<String> errors) {
        return new UserEditResult(false, UserEditFailureType.VALIDATION, null, errors);
    }

    /**
     * 業務エラー失敗結果を生成する。
     *
     * @param errorTitle エラータイトル（{データ行番号}行目で異常終了しました。）
     * @param message 該当エラー内容
     * @return 失敗結果
     */
    public static UserEditResult ofBusinessFailure(String errorTitle, String message) {
        return new UserEditResult(false, UserEditFailureType.BUSINESS, errorTitle,
                List.of(message));
    }
}
