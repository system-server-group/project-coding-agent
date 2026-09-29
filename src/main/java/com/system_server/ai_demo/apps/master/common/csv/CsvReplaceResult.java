package com.system_server.ai_demo.apps.master.common.csv;

import java.util.List;

/**
 * CSV 洗い替えの結果。成功または失敗（失敗種別＋表示用エラー明細）を表す。
 *
 * @param success 洗い替えが成功したか
 * @param failureType 失敗種別（成功時は {@code null}）
 * @param errors 表示用のエラーメッセージ一覧（成功時は空）
 */
public record CsvReplaceResult(boolean success, CsvFailureType failureType, List<String> errors) {

    /**
     * 成功結果を生成する。
     *
     * @return 成功結果
     */
    public static CsvReplaceResult ofSuccess() {
        return new CsvReplaceResult(true, null, List.of());
    }

    /**
     * 失敗結果を生成する。
     *
     * @param failureType 失敗種別
     * @param errors エラーメッセージ一覧
     * @return 失敗結果
     */
    public static CsvReplaceResult ofFailure(CsvFailureType failureType, List<String> errors) {
        return new CsvReplaceResult(false, failureType, errors);
    }
}
