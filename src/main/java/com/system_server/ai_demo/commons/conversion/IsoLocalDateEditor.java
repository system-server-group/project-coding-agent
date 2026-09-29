package com.system_server.ai_demo.commons.conversion;

import java.beans.PropertyEditorSupport;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;

/**
 * フォーム入力の {@link LocalDate} 変換を担う {@link java.beans.PropertyEditor}。
 *
 * <p>
 * 画面の日付入力部品（{@code <input type="date">}）が送出する ISO 形式（{@code yyyy-MM-dd}）を {@link LocalDate} に
 * 変換する。未入力（空文字）は {@code null} とし、任意項目の未入力を型変換エラーにしない。表示時は ISO 形式で出力する。
 *
 * <p>
 * ISO 形式として解釈できない値は {@link IllegalArgumentException} を送出する（{@link java.beans.PropertyEditor} の
 * コントラクトに従う）。これにより Spring 標準の型変換エラー（{@code typeMismatch}）として扱われ、入力値チェック仕様の
 * 「日付形式」メッセージへの読み替え（{@code FormErrorMessages}）が機能する。
 */
public class IsoLocalDateEditor extends PropertyEditorSupport {

    @Override
    public void setAsText(String text) {
        String value = text == null ? null : text.trim();
        if (value == null || value.isEmpty()) {
            setValue(null);
            return;
        }
        try {
            setValue(LocalDate.parse(value));
        } catch (DateTimeParseException e) {
            throw new IllegalArgumentException(e.getMessage(), e);
        }
    }

    @Override
    public String getAsText() {
        Object value = getValue();
        return value == null ? "" : value.toString();
    }
}
