package com.system_server.ai_demo.enums;

import com.system_server.ai_demo.commons.code.CodeEnum;

/**
 * ロール（システムに対するユーザの権限を表す区分）。
 *
 * <p>
 * 区分コードが数字文字列のため、Java 定数名（識別子）と区分コードは一致しない。{@link #getCode()} が保持コードを返す。
 */
public enum Role implements CodeEnum {

    GENERAL_USER("0", "一般ユーザ"), SYSTEM_ADMIN("9", "システム管理者");

    private final String code;
    private final String label;

    Role(String code, String label) {
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
