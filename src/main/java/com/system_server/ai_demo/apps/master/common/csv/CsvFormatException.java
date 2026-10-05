package com.system_server.ai_demo.apps.master.common.csv;

/**
 * CSV のファイル形式（様式）違反を表す例外。表示用メッセージを保持する。
 */
public final class CsvFormatException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    /**
     * 表示用メッセージを指定して生成する。
     *
     * @param message 形式違反の表示用メッセージ
     */
    public CsvFormatException(String message) {
        super(message);
    }
}
