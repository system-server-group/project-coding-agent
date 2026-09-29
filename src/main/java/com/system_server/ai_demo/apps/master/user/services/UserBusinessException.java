package com.system_server.ai_demo.apps.master.user.services;

/**
 * ユーザマスタ編集中の業務エラーを表す例外。発生したデータ行番号（ヘッダー行を除く）と表示用メッセージを保持する。
 *
 * <p>
 * {@link UserMasterService#edit} 内でのみ送出・捕捉し、捕捉時にトランザクションをロールバックして編集前の状態に戻す。
 */
final class UserBusinessException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    private final int dataLineNumber;

    /**
     * データ行番号と表示用メッセージを指定して生成する。
     *
     * @param dataLineNumber 業務エラーが発生したデータ行番号（ヘッダー行を除く）
     * @param message 表示用のエラーメッセージ
     */
    UserBusinessException(int dataLineNumber, String message) {
        super(message);
        this.dataLineNumber = dataLineNumber;
    }

    /**
     * 業務エラーが発生したデータ行番号を返す。
     *
     * @return データ行番号（ヘッダー行を除く）
     */
    int getDataLineNumber() {
        return dataLineNumber;
    }
}
