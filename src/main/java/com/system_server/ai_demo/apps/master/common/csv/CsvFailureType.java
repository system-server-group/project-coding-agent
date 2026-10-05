package com.system_server.ai_demo.apps.master.common.csv;

/**
 * CSV 洗い替えの失敗種別。
 */
public enum CsvFailureType {

    /** ファイル形式チェック違反。 */
    FORMAT,

    /** 入力値チェック違反。 */
    VALIDATION
}
