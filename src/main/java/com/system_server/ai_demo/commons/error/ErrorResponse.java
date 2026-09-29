package com.system_server.ai_demo.commons.error;

import java.util.List;

/**
 * API 共通のエラーレスポンス（HTTP 400）。入力チェック違反の一覧を返す。
 *
 * @param errors 入力チェック違反の一覧
 */
public record ErrorResponse(List<ErrorItem> errors) {
}
