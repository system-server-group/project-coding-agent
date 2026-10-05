package com.system_server.ai_demo.enums;

import com.system_server.ai_demo.commons.code.CodeEnum;

/**
 * 契約種別（案件の労働形態を表す契約種別の区分）。
 */
public enum ContractType implements CodeEnum {

    CONTRACT("CONTRACT", "請負"), QUASI_MANDATE("QUASI_MANDATE", "準委任"), DISPATCH("DISPATCH", "派遣");

    private final String code;
    private final String label;

    ContractType(String code, String label) {
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
