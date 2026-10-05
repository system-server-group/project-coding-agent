package com.system_server.ai_demo.commons.error;

/**
 * 入力チェック違反 1 件分。
 *
 * @param field 違反した入力項目の物理名。特定の項目に紐づかない違反の場合は {@code null}
 * @param message エラーメッセージ
 */
public record ErrorItem(String field, String message) {
}
