package com.system_server.ai_demo.enums;

import com.system_server.ai_demo.commons.code.CodeEnum;

/**
 * 編集属性（ユーザマスタの CSV 編集において、レコードごとに実施する編集の種類を表す区分）。
 *
 * <p>
 * 区分コードが数字文字列のため、Java 定数名（識別子）と区分コードは一致しない。{@link #getCode()} が保持コードを返す。
 */
public enum EditAttribute implements CodeEnum {

    NONE("0", "何もしない"), ADD("1", "ユーザの追加"), UPDATE("2", "ユーザの更新"), DELETE("3", "ユーザの削除");

    private final String code;
    private final String label;

    EditAttribute(String code, String label) {
        this.code = code;
        this.label = label;
    }

    @Override
    public String getCode() {
        return code;
    }

    @Override
    public String getLabel() {
        return label;
    }
}
