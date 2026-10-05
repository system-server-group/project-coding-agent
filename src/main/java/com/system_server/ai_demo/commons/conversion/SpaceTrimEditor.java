package com.system_server.ai_demo.commons.conversion;

import java.beans.PropertyEditorSupport;

/**
 * フォーム入力の文字列トリムを担う {@link java.beans.PropertyEditor}。
 *
 * <p>
 * 入力された文字列の前後から半角スペース・全角スペースを（連続分すべて）除去し、文字列内部の スペースは保持する（画面部品仕様 システム共通仕様書「トリム」。テキスト・テキストエリア・
 * オートコンプリートに共通の入力仕様）。
 *
 * <p>
 * 除去対象は半角スペース（U+0020）・全角スペース（U+3000）の2種のみとする。タブ・改行は仕様の
 * 除去対象に含まれないため除去しない（テキストエリアの改行を保持する）。スペースのみの入力は 空文字になる（{@code null} へは変換せず、必須チェック等の判定は変えない）。
 */
public class SpaceTrimEditor extends PropertyEditorSupport {

    @Override
    public void setAsText(String text) {
        setValue(text == null ? null : trim(text));
    }

    private static String trim(String value) {
        int start = 0;
        int end = value.length();
        while (start < end && isSpace(value.charAt(start))) {
            start++;
        }
        while (end > start && isSpace(value.charAt(end - 1))) {
            end--;
        }
        return value.substring(start, end);
    }

    private static boolean isSpace(char c) {
        return c == ' ' || c == '　';
    }
}
