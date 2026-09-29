package com.system_server.ai_demo.enums;

import com.system_server.ai_demo.commons.code.CodeEnum;

/**
 * BP募集(要否)（案件の BP（ビジネスパートナー）募集の要否を表す区分）。
 */
public enum BpRecruitment implements CodeEnum {

    REQUIRED("REQUIRED", "必要"), NOT_REQUIRED("NOT_REQUIRED", "不要");

    private final String code;
    private final String label;

    BpRecruitment(String code, String label) {
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
